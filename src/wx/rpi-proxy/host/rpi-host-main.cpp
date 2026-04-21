// RPI Filter Host - 32-bit process that loads and executes RPI filter plugins
// This is embedded as a resource in the 64-bit VBA-M build and extracted at runtime
// to enable loading 32-bit filter plugins from the 64-bit application.

#if defined(_WIN32)

#include <windows.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>

#include "wx/rpi-proxy/SharedFilterBuffer.h"
#include "wx/rpi.h"

using namespace rpi_proxy;

// Global state
static HANDLE g_sharedMemHandle = nullptr;
static SharedFilterBuffer* g_sharedMem = nullptr;
static HANDLE g_requestEvent = nullptr;
static HANDLE g_responseEvent = nullptr;

// Client process ID and instance ID (passed via command line, used for unique IPC names)
static uint32_t g_clientPid = 0;
static uint32_t g_instanceId = 0;

// Plugin state for the main/loading instance
static HMODULE g_pluginHandle = nullptr;
static RENDER_PLUGIN_INFO* g_pluginInfo = nullptr;
static wchar_t g_pluginPath[MAX_PATH] = {0};  // Store path for thread instances

// Per-thread state for multi-threaded filtering
struct FilterThreadState {
    // Plugin instance
    HMODULE pluginHandle = nullptr;
    RENDER_PLUGIN_INFO* pluginInfo = nullptr;

    // Per-thread IPC
    HANDLE sharedMemHandle = nullptr;
    SharedFilterBuffer* sharedMem = nullptr;
    HANDLE requestEvent = nullptr;
    HANDLE responseEvent = nullptr;

    // Thread management
    HANDLE threadHandle = nullptr;
    uint32_t threadId = 0;
    volatile bool running = false;
    volatile bool active = false;
};
static FilterThreadState g_threadStates[kMaxFilterThreads];

// Critical section to serialize plugin Output calls
// Many RPI plugins have global state and are not thread-safe, so we need
// to ensure only one thread calls Output at a time.
static CRITICAL_SECTION g_pluginOutputCs;
static bool g_pluginOutputCsInitialized = false;

// Forward declarations
static DWORD WINAPI FilterThreadProc(LPVOID param);
static void CleanupThreadIPC(FilterThreadState* state);

static bool InitializeIPC() {
    // Generate IPC names using client PID and instance ID
    wchar_t memName[128], reqName[128], respName[128];
    GetSharedMemoryName(g_clientPid, g_instanceId, memName, 128);
    GetRequestEventName(g_clientPid, g_instanceId, reqName, 128);
    GetResponseEventName(g_clientPid, g_instanceId, respName, 128);

    // Open the shared memory created by the parent process
    g_sharedMemHandle = OpenFileMappingW(
        FILE_MAP_ALL_ACCESS,
        FALSE,
        memName);

    if (!g_sharedMemHandle) {
        return false;
    }

    g_sharedMem = static_cast<SharedFilterBuffer*>(
        MapViewOfFile(g_sharedMemHandle, FILE_MAP_ALL_ACCESS, 0, 0, kSharedMemorySize));

    if (!g_sharedMem) {
        CloseHandle(g_sharedMemHandle);
        g_sharedMemHandle = nullptr;
        return false;
    }

    // Open synchronization events
    g_requestEvent = OpenEventW(EVENT_ALL_ACCESS, FALSE, reqName);
    if (!g_requestEvent) {
        return false;
    }

    g_responseEvent = OpenEventW(EVENT_ALL_ACCESS, FALSE, respName);
    if (!g_responseEvent) {
        return false;
    }

    return true;
}

static void CleanupIPC() {
    if (g_sharedMem) {
        UnmapViewOfFile(g_sharedMem);
        g_sharedMem = nullptr;
    }
    if (g_sharedMemHandle) {
        CloseHandle(g_sharedMemHandle);
        g_sharedMemHandle = nullptr;
    }
    if (g_requestEvent) {
        CloseHandle(g_requestEvent);
        g_requestEvent = nullptr;
    }
    if (g_responseEvent) {
        CloseHandle(g_responseEvent);
        g_responseEvent = nullptr;
    }
}

static void UnloadCurrentPlugin() {
    if (g_pluginHandle) {
        FreeLibrary(g_pluginHandle);
        g_pluginHandle = nullptr;
        g_pluginInfo = nullptr;
    }
}

// Load a plugin into a specific handle/info pair (for thread instances)
static bool LoadPluginInstance(const wchar_t* path, HMODULE* outHandle, RENDER_PLUGIN_INFO** outInfo) {
    *outHandle = LoadLibraryW(path);
    if (!*outHandle) {
        return false;
    }

    RENDPLUG_GetInfo getInfo = reinterpret_cast<RENDPLUG_GetInfo>(
        reinterpret_cast<void*>(GetProcAddress(*outHandle, "RenderPluginGetInfo")));

    if (!getInfo) {
        FreeLibrary(*outHandle);
        *outHandle = nullptr;
        return false;
    }

    *outInfo = getInfo();
    if (!*outInfo) {
        FreeLibrary(*outHandle);
        *outHandle = nullptr;
        return false;
    }

    // Validate version - accept both version 1 and 2
    unsigned int pluginVersion = (*outInfo)->Flags & 0xff;
    if (pluginVersion != 1 && pluginVersion != 2) {
        FreeLibrary(*outHandle);
        *outHandle = nullptr;
        *outInfo = nullptr;
        return false;
    }

    // Validate color format support (accept 555, 565, or 888)
    // Version 1 plugins may not set color format flags - assume 565 support for compatibility
    if (((*outInfo)->Flags & (RPI_555_SUPP | RPI_565_SUPP | RPI_888_SUPP)) == 0) {
        if (pluginVersion == 1) {
            // Version 1 plugins without color flags - assume 565 support (common for GBA)
            (*outInfo)->Flags |= RPI_565_SUPP;
        } else {
            FreeLibrary(*outHandle);
            *outHandle = nullptr;
            *outInfo = nullptr;
            return false;
        }
    }

    // Get Output function if not set
    if (!(*outInfo)->Output) {
        (*outInfo)->Output = reinterpret_cast<RENDPLUG_Output>(
            reinterpret_cast<void*>(GetProcAddress(*outHandle, "RenderPluginOutput")));
    }

    if (!(*outInfo)->Output) {
        FreeLibrary(*outHandle);
        *outHandle = nullptr;
        *outInfo = nullptr;
        return false;
    }

    return true;
}

static bool LoadPlugin(const wchar_t* path) {
    UnloadCurrentPlugin();

    // Store the path for later thread instance creation
    wcsncpy_s(g_pluginPath, MAX_PATH, path, _TRUNCATE);

    // Use the common loading function
    if (!LoadPluginInstance(path, &g_pluginHandle, &g_pluginInfo)) {
        g_pluginPath[0] = L'\0';
        return false;
    }

    return true;
}

static void HandleLoadPlugin() {
    if (LoadPlugin(g_sharedMem->pluginPath)) {
        // Copy plugin info to shared memory
        strncpy_s(g_sharedMem->pluginInfo.name, sizeof(g_sharedMem->pluginInfo.name),
            g_pluginInfo->Name, _TRUNCATE);
        g_sharedMem->pluginInfo.flags = g_pluginInfo->Flags;
        g_sharedMem->pluginInfo.scale = (g_pluginInfo->Flags & RPI_OUT_SCLMSK) >> RPI_OUT_SCLSH;
        g_sharedMem->result = FilterResult::Success;
    } else {
        g_sharedMem->result = FilterResult::InvalidPlugin;
    }
}

static void CleanupThreadIPC(FilterThreadState* state) {
    if (state->sharedMem) {
        UnmapViewOfFile(state->sharedMem);
        state->sharedMem = nullptr;
    }
    if (state->sharedMemHandle) {
        CloseHandle(state->sharedMemHandle);
        state->sharedMemHandle = nullptr;
    }
    if (state->requestEvent) {
        CloseHandle(state->requestEvent);
        state->requestEvent = nullptr;
    }
    if (state->responseEvent) {
        CloseHandle(state->responseEvent);
        state->responseEvent = nullptr;
    }
}

static bool InitializeThreadIPC(FilterThreadState* state, uint32_t threadId) {
    wchar_t memName[128], reqName[128], respName[128];
    GetThreadSharedMemoryName(g_clientPid, g_instanceId, threadId, memName, 128);
    GetThreadRequestEventName(g_clientPid, g_instanceId, threadId, reqName, 128);
    GetThreadResponseEventName(g_clientPid, g_instanceId, threadId, respName, 128);

    // Open shared memory created by the client
    state->sharedMemHandle = OpenFileMappingW(FILE_MAP_ALL_ACCESS, FALSE, memName);
    if (!state->sharedMemHandle) {
        return false;
    }

    state->sharedMem = static_cast<SharedFilterBuffer*>(
        MapViewOfFile(state->sharedMemHandle, FILE_MAP_ALL_ACCESS, 0, 0, kSharedMemorySize));
    if (!state->sharedMem) {
        CloseHandle(state->sharedMemHandle);
        state->sharedMemHandle = nullptr;
        return false;
    }

    // Open events created by the client
    state->requestEvent = OpenEventW(EVENT_ALL_ACCESS, FALSE, reqName);
    if (!state->requestEvent) {
        CleanupThreadIPC(state);
        return false;
    }

    state->responseEvent = OpenEventW(EVENT_ALL_ACCESS, FALSE, respName);
    if (!state->responseEvent) {
        CleanupThreadIPC(state);
        return false;
    }

    return true;
}

// Thread procedure for per-thread filter processing
static DWORD WINAPI FilterThreadProc(LPVOID param) {
    FilterThreadState* state = static_cast<FilterThreadState*>(param);

    while (state->running) {
        // Wait for a command
        DWORD waitResult = WaitForSingleObject(state->requestEvent, 1000);

        if (!state->running) {
            break;
        }

        if (waitResult == WAIT_TIMEOUT) {
            continue;
        }

        if (waitResult != WAIT_OBJECT_0) {
            break;
        }

        // Memory barrier to ensure we see the latest writes from the client
        MemoryBarrier();

        // Process the command
        FilterCommand cmd = state->sharedMem->command;

        if (cmd == FilterCommand::ApplyFilter) {
            if (state->pluginInfo && state->pluginInfo->Output) {
                // Build RENDER_PLUGIN_OUTP from shared memory
                RENDER_PLUGIN_OUTP outp;
                outp.Size = sizeof(outp);
                outp.Flags = state->sharedMem->filterParams.flags;
                outp.SrcPtr = state->sharedMem->GetSrcBuffer();
                outp.SrcPitch = state->sharedMem->filterParams.srcPitch;
                outp.SrcW = state->sharedMem->filterParams.srcW;
                outp.SrcH = state->sharedMem->filterParams.srcH;
                outp.DstPtr = state->sharedMem->GetDstBuffer();
                outp.DstPitch = state->sharedMem->filterParams.dstPitch;
                outp.DstW = state->sharedMem->filterParams.dstW;
                outp.DstH = state->sharedMem->filterParams.dstH;
                outp.OutW = state->sharedMem->filterParams.outW;
                outp.OutH = state->sharedMem->filterParams.outH;

                // Execute the filter with serialization
                // Many RPI plugins have global state and are not thread-safe,
                // so we serialize all Output calls through a critical section.
                EnterCriticalSection(&g_pluginOutputCs);
                state->pluginInfo->Output(&outp);
                LeaveCriticalSection(&g_pluginOutputCs);

                state->sharedMem->result = FilterResult::Success;
            } else {
                state->sharedMem->result = FilterResult::PluginNotLoaded;
            }
        } else {
            state->sharedMem->result = FilterResult::InvalidParameters;
        }

        // Reset command and signal completion
        state->sharedMem->command = FilterCommand::None;
        state->sharedMem->response_seq = state->sharedMem->request_seq;
        MemoryBarrier();
        SetEvent(state->responseEvent);
    }

    return 0;
}

static void HandleStartThread() {
    uint32_t threadId = g_sharedMem->filterParams.thread_id;

    if (threadId >= kMaxFilterThreads) {
        g_sharedMem->result = FilterResult::InvalidParameters;
        return;
    }

    if (g_pluginPath[0] == L'\0') {
        g_sharedMem->result = FilterResult::PluginNotLoaded;
        return;
    }

    FilterThreadState* state = &g_threadStates[threadId];

    // Stop existing thread if running
    if (state->active) {
        state->running = false;
        if (state->threadHandle) {
            TerminateThread(state->threadHandle, 0);
            CloseHandle(state->threadHandle);
            state->threadHandle = nullptr;
        }
        if (state->pluginHandle) {
            FreeLibrary(state->pluginHandle);
        }
        state->pluginHandle = nullptr;
        state->pluginInfo = nullptr;
        CleanupThreadIPC(state);
        state->active = false;
    }

    // Initialize per-thread IPC
    if (!InitializeThreadIPC(state, threadId)) {
        g_sharedMem->result = FilterResult::Failed;
        return;
    }

    // Load a new plugin instance for this thread
    if (!LoadPluginInstance(g_pluginPath, &state->pluginHandle, &state->pluginInfo)) {
        CleanupThreadIPC(state);
        g_sharedMem->result = FilterResult::InvalidPlugin;
        return;
    }

    // Start the thread
    state->threadId = threadId;
    state->running = true;
    state->active = true;
    state->threadHandle = CreateThread(nullptr, 0, FilterThreadProc, state, 0, nullptr);

    if (!state->threadHandle) {
        FreeLibrary(state->pluginHandle);
        state->pluginHandle = nullptr;
        state->pluginInfo = nullptr;
        CleanupThreadIPC(state);
        state->active = false;
        state->running = false;
        g_sharedMem->result = FilterResult::Failed;
        return;
    }

    g_sharedMem->result = FilterResult::Success;
}

static void HandleStopThread() {
    uint32_t threadId = g_sharedMem->filterParams.thread_id;

    if (threadId >= kMaxFilterThreads) {
        g_sharedMem->result = FilterResult::InvalidParameters;
        return;
    }

    FilterThreadState* state = &g_threadStates[threadId];

    if (state->active) {
        // Signal thread to stop
        state->running = false;

        // Forcefully terminate thread for instant shutdown
        if (state->threadHandle) {
            TerminateThread(state->threadHandle, 0);
            CloseHandle(state->threadHandle);
            state->threadHandle = nullptr;
        }

        // Cleanup plugin
        if (state->pluginHandle) {
            FreeLibrary(state->pluginHandle);
        }
        state->pluginHandle = nullptr;
        state->pluginInfo = nullptr;

        // Cleanup IPC
        CleanupThreadIPC(state);

        state->active = false;
    }

    g_sharedMem->result = FilterResult::Success;
}

static void HandleApplyFilter() {
    uint32_t threadId = g_sharedMem->filterParams.thread_id;

    // Determine which plugin instance to use
    RENDER_PLUGIN_INFO* pluginInfo = nullptr;

    if (threadId < kMaxFilterThreads && g_threadStates[threadId].active) {
        // Use thread-specific instance
        pluginInfo = g_threadStates[threadId].pluginInfo;
    } else {
        // Fall back to main instance
        pluginInfo = g_pluginInfo;
    }

    if (!pluginInfo || !pluginInfo->Output) {
        g_sharedMem->result = FilterResult::PluginNotLoaded;
        return;
    }

    // Build RENDER_PLUGIN_OUTP from shared memory
    RENDER_PLUGIN_OUTP outp;
    outp.Size = sizeof(outp);
    outp.Flags = g_sharedMem->filterParams.flags;
    outp.SrcPtr = g_sharedMem->GetSrcBuffer();
    outp.SrcPitch = g_sharedMem->filterParams.srcPitch;
    outp.SrcW = g_sharedMem->filterParams.srcW;
    outp.SrcH = g_sharedMem->filterParams.srcH;
    outp.DstPtr = g_sharedMem->GetDstBuffer();
    outp.DstPitch = g_sharedMem->filterParams.dstPitch;
    outp.DstW = g_sharedMem->filterParams.dstW;
    outp.DstH = g_sharedMem->filterParams.dstH;
    outp.OutW = g_sharedMem->filterParams.outW;
    outp.OutH = g_sharedMem->filterParams.outH;

    // Execute the filter
    pluginInfo->Output(&outp);

    g_sharedMem->result = FilterResult::Success;
}

static void UnloadAllThreadInstances() {
    for (uint32_t i = 0; i < kMaxFilterThreads; i++) {
        FilterThreadState* state = &g_threadStates[i];
        if (state->active) {
            // Signal thread to stop
            state->running = false;

            // Forcefully terminate thread for instant shutdown
            if (state->threadHandle) {
                TerminateThread(state->threadHandle, 0);
                CloseHandle(state->threadHandle);
                state->threadHandle = nullptr;
            }

            // Cleanup plugin
            if (state->pluginHandle) {
                FreeLibrary(state->pluginHandle);
            }
            state->pluginHandle = nullptr;
            state->pluginInfo = nullptr;

            // Cleanup IPC
            CleanupThreadIPC(state);

            state->active = false;
        }
    }
}

static void HandleUnloadPlugin() {
    UnloadAllThreadInstances();
    UnloadCurrentPlugin();
    g_pluginPath[0] = L'\0';
    memset(&g_sharedMem->pluginInfo, 0, sizeof(g_sharedMem->pluginInfo));
    g_sharedMem->result = FilterResult::Success;
}

static void HandleEnumeratePlugins() {
    uint32_t count = g_sharedMem->numEnumPlugins;
    if (count > kMaxEnumPlugins) {
        count = kMaxEnumPlugins;
    }

    PluginEnumEntry* entries = g_sharedMem->GetEnumEntries();

    for (uint32_t i = 0; i < count; i++) {
        HMODULE handle = nullptr;
        RENDER_PLUGIN_INFO* info = nullptr;

        if (LoadPluginInstance(entries[i].path, &handle, &info)) {
            strncpy_s(entries[i].name, sizeof(entries[i].name),
                info->Name, _TRUNCATE);
            entries[i].flags = info->Flags;
            entries[i].valid = 1;
            FreeLibrary(handle);
        } else {
            entries[i].name[0] = '\0';
            entries[i].flags = 0;
            entries[i].valid = 0;
        }
    }

    g_sharedMem->result = FilterResult::Success;
}

static void HandleGetInfo() {
    if (!g_pluginInfo) {
        g_sharedMem->result = FilterResult::PluginNotLoaded;
        return;
    }

    strncpy_s(g_sharedMem->pluginInfo.name, sizeof(g_sharedMem->pluginInfo.name),
        g_pluginInfo->Name, _TRUNCATE);
    g_sharedMem->pluginInfo.flags = g_pluginInfo->Flags;
    g_sharedMem->pluginInfo.scale = (g_pluginInfo->Flags & RPI_OUT_SCLMSK) >> RPI_OUT_SCLSH;
    g_sharedMem->result = FilterResult::Success;
}

static bool ProcessCommand(FilterCommand cmd) {
    switch (cmd) {
        case FilterCommand::LoadPlugin:
            HandleLoadPlugin();
            break;

        case FilterCommand::ApplyFilter:
            HandleApplyFilter();
            break;

        case FilterCommand::UnloadPlugin:
            HandleUnloadPlugin();
            break;

        case FilterCommand::GetInfo:
            HandleGetInfo();
            break;

        case FilterCommand::Shutdown:
            g_sharedMem->result = FilterResult::Success;
            return false;  // Signal to exit

        case FilterCommand::StartThread:
            HandleStartThread();
            break;

        case FilterCommand::StopThread:
            HandleStopThread();
            break;

        case FilterCommand::EnumeratePlugins:
            HandleEnumeratePlugins();
            break;

        case FilterCommand::None:
            // Should never happen since we check before calling, but handle gracefully
            break;

        default:
            g_sharedMem->result = FilterResult::InvalidParameters;
            break;
    }

    return true;  // Continue running
}

int main(int argc, char* argv[]) {
    // Parse client PID and instance ID from command line first
    if (argc < 3) {
        return 1;
    }
    g_clientPid = static_cast<uint32_t>(atoi(argv[1]));
    g_instanceId = static_cast<uint32_t>(atoi(argv[2]));

    // Pre-load msvcrt.dll to help with MinGW-compiled plugins that depend on it
    // This ensures the CRT is initialized before any plugin tries to use it
    LoadLibraryA("msvcrt.dll");

    if (g_clientPid == 0) {
        return 1;
    }

    if (!InitializeIPC()) {
        return 1;
    }

    // Initialize critical section for serializing plugin Output calls
    InitializeCriticalSection(&g_pluginOutputCs);
    g_pluginOutputCsInitialized = true;

    // Signal that we're ready (version check + ready event)
    g_sharedMem->version = SharedFilterBuffer::kCurrentVersion;
    g_sharedMem->result = FilterResult::Success;
    MemoryBarrier();  // Ensure version is visible before signaling

    // Signal the ready event so the client doesn't have to poll
    wchar_t readyName[128];
    GetReadyEventName(g_clientPid, g_instanceId, readyName, 128);
    HANDLE readyEvent = OpenEventW(EVENT_MODIFY_STATE, FALSE, readyName);
    if (readyEvent) {
        SetEvent(readyEvent);
        CloseHandle(readyEvent);
    }

    // Main command processing loop
    bool running = true;
    while (running) {
        // Wait for a command from the parent process
        DWORD waitResult = WaitForSingleObject(g_requestEvent, 30000);  // 30 second timeout

        if (waitResult == WAIT_TIMEOUT) {
            // No signal received, just continue waiting
            continue;
        } else if (waitResult != WAIT_OBJECT_0) {
            break;
        }

        // Memory barrier to ensure we see the latest writes from the client
        // This is critical because the client writes to shared memory then signals,
        // but without a barrier we might see stale cached values
        MemoryBarrier();

        // Read the command and sequence number ONCE into local variables to avoid TOCTOU races
        // The client might modify the shared memory between our reads (especially if it times out
        // and starts a new command), so we snapshot these values immediately after waking up
        // Note: Client writes command FIRST, then seq. If we see a new seq, command should be visible.
        uint32_t req_seq = g_sharedMem->request_seq;
        FilterCommand cmd = g_sharedMem->command;

        // If command is None but we got a valid seq, there may be a timing issue where
        // the host woke up before the client's writes became fully visible. Wait briefly and retry.
        if (cmd == FilterCommand::None && req_seq != 0) {
            // Small spin-wait to allow client writes to become visible
            for (int retry = 0; retry < 20 && cmd == FilterCommand::None; retry++) {
                Sleep(1);  // 1ms delay
                MemoryBarrier();
                cmd = g_sharedMem->command;
            }
        }

        // If command is still None after retries but we have a valid seq,
        // send a negative acknowledgment so the client can retry immediately
        // instead of waiting for the full timeout
        if (cmd == FilterCommand::None) {
            if (req_seq != 0) {
                g_sharedMem->result = FilterResult::Failed;
                g_sharedMem->response_seq = req_seq;
                MemoryBarrier();
                SetEvent(g_responseEvent);
            }
            continue;
        }

        // Process the command using the snapshotted value
        running = ProcessCommand(cmd);

        // Reset command
        g_sharedMem->command = FilterCommand::None;

        // Copy the SNAPSHOTTED request sequence number to response
        // This is critical: we must use the seq we captured at the start, not the current value
        // in shared memory (which the client may have already updated for the next command)
        g_sharedMem->response_seq = req_seq;
        MemoryBarrier();  // Ensure response_seq is visible before signaling

        // Signal completion
        SetEvent(g_responseEvent);
    }

    // Cleanup
    UnloadAllThreadInstances();
    UnloadCurrentPlugin();
    CleanupIPC();
    if (g_pluginOutputCsInitialized) {
        DeleteCriticalSection(&g_pluginOutputCs);
        g_pluginOutputCsInitialized = false;
    }

    return 0;
}

#else  // !_WIN32

int main() {
    // This executable is Windows-only
    return 1;
}

#endif  // _WIN32
