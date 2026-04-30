package com.krendstudio.krendbuoy;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class GameActivityV2 extends Activity implements GameControllerOverlay.Host, GameQuickMenu.Host, GameSettingsDialogs.Host, FrameLoopManager.Host, PortableSaveManager.Host {
    private TextView info;
    private ImageView screen;
    private Uri currentRomUri;
    private volatile boolean finishingFromMenu;
    private volatile boolean menuPaused;
    private volatile boolean restarting;
    private FrameLoopManager frameLoopManager;
    private final AudioPlaybackManager audioPlaybackManager = new AudioPlaybackManager();
    private RomSessionManager romSessionManager;
    private PortableSaveManager portableSaveManager;
    private int audioBacklogSamples = -1;
    private Uri portableSaveFolderUri;
    private String romBaseName = "selected";
    private int displayMode = 0;
    private boolean debugTextVisible = false;
    private int startupLoadStateSlot = 0;
    private SaveStateManager saveStateManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        currentRomUri = getIntent().getData();
        romSessionManager = new RomSessionManager(this);
        audioBacklogSamples = normalizeAudioPreset(getIntent().getIntExtra("audio_backlog_samples", -1));
        startupLoadStateSlot = getIntent().getIntExtra("load_state_slot", 0);
        if (startupLoadStateSlot < 1 || startupLoadStateSlot > 5) startupLoadStateSlot = 0;
        String saveFolder = getIntent().getStringExtra("save_folder_uri");
        if (saveFolder != null && !saveFolder.isEmpty()) portableSaveFolderUri = Uri.parse(saveFolder);
        portableSaveManager = new PortableSaveManager(this, portableSaveFolderUri, this);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(18, 22, 26));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(8), dp(62), dp(8), 0);
        root.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        int screenWidth = getResources().getDisplayMetrics().widthPixels - dp(16);
        int screenHeight = Math.round(screenWidth * 2f / 3f);
        screenHeight = Math.max(dp(220), Math.min(screenHeight, dp(360)));

        FrameLayout screenBox = new FrameLayout(this);
        screenBox.setBackgroundColor(Color.BLACK);
        content.addView(screenBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, screenHeight));

        screen = new ImageView(this);
        screen.setAdjustViewBounds(false);
        screen.setScaleType(ImageView.ScaleType.FIT_CENTER);
        screenBox.addView(screen, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        frameLoopManager = new FrameLoopManager(this, screen);

        info = new TextView(this);
        info.setText("Preparing ROM...");
        info.setTextSize(12f);
        info.setTextColor(Color.LTGRAY);
        info.setGravity(Gravity.CENTER);
        info.setVisibility(debugTextVisible ? View.VISIBLE : View.GONE);
        content.addView(info, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        GameControllerOverlay.attach(this, root, this);
        setContentView(root);
        new Thread(() -> prepareAndStart(currentRomUri), "KrendBuoy-prepare-v2").start();
    }

    @Override
    protected void onPause() {
        NativeBridge.saveSram();
        exportPortableSramIfEnabled();
        releaseAllButtons();
        stopAudioPlayback();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (frameLoopManager != null) frameLoopManager.stop();
        NativeBridge.saveSram();
        exportPortableSramIfEnabled();
        stopAudioPlayback();
        releaseAllButtons();
        NativeBridge.unloadRom();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        showQuickMenu();
    }

    private void prepareAndStart(Uri romUri) {
        if (romSessionManager == null) romSessionManager = new RomSessionManager(this);
        RomSessionManager.Result result = romSessionManager.load(romUri);
        if (!result.loaded) {
            updateInfo(result.errorMessage);
            return;
        }
        romBaseName = result.romBaseName;
        saveStateManager = new SaveStateManager(this, portableSaveFolderUri, romSessionManager.ensureDirectory("states"), romBaseName);
        NativeBridge.setAudioMaxBufferedSamples(audioBacklogSamples);
        importPortableSramIfAvailable();
        loadStartupStateIfRequested();
        updateInfo("Running... audio preset " + audioPresetLabel(audioBacklogSamples) + "\n" + NativeBridge.getLastError());
        menuPaused = false;
        restarting = false;
        startAudioPlayback();
        startFrameLoop();
    }

    private void startFrameLoop() {
        if (frameLoopManager == null) frameLoopManager = new FrameLoopManager(this, screen);
        frameLoopManager.start();
    }

    private void startAudioPlayback() {
        audioPlaybackManager.start();
    }

    private void stopAudioPlayback() {
        audioPlaybackManager.stop();
    }

    @Override
    public void showQuickMenu() {
        GameQuickMenu.show(this, this);
    }

    @Override
    public void pauseEmulationForMenu() {
        if (finishingFromMenu || restarting) return;
        menuPaused = true;
        releaseAllButtons();
        stopAudioPlayback();
    }

    @Override
    public void resumeEmulationFromMenu() {
        if (finishingFromMenu || restarting || frameLoopManager == null || !frameLoopManager.isRunning() || !menuPaused) return;
        menuPaused = false;
        startAudioPlayback();
    }

    @Override
    public boolean isFrameLoopPaused() {
        return menuPaused;
    }

    @Override
    public void updateFrameInfo(String text) {
        updateInfo(text);
    }

    @Override
    public String audioPresetLabelForFrameLoop() {
        return audioPresetLabel(audioBacklogSamples);
    }

    @Override
    public void updatePortableSaveInfo(String text) {
        updateInfo(text);
    }

    @Override
    public void releaseAllButtons() {
        NativeBridge.setButtonState(NativeBridge.BUTTON_A, false);
        NativeBridge.setButtonState(NativeBridge.BUTTON_B, false);
        NativeBridge.setButtonState(NativeBridge.BUTTON_L, false);
        NativeBridge.setButtonState(NativeBridge.BUTTON_R, false);
        NativeBridge.setButtonState(NativeBridge.BUTTON_START, false);
        NativeBridge.setButtonState(NativeBridge.BUTTON_SELECT, false);
        NativeBridge.setButtonState(NativeBridge.BUTTON_UP, false);
        NativeBridge.setButtonState(NativeBridge.BUTTON_DOWN, false);
        NativeBridge.setButtonState(NativeBridge.BUTTON_LEFT, false);
        NativeBridge.setButtonState(NativeBridge.BUTTON_RIGHT, false);
    }

    @Override
    public void restartGame() {
        if (restarting || currentRomUri == null) return;
        restarting = true;
        updateInfo("Restarting game...");
        new Thread(() -> {
            menuPaused = true;
            NativeBridge.saveSram();
            exportPortableSramIfEnabled();
            releaseAllButtons();
            stopAudioPlayback();
            if (frameLoopManager != null) frameLoopManager.stop();
            NativeBridge.unloadRom();
            saveStateManager = null;
            startupLoadStateSlot = 0;
            frameLoopManager = new FrameLoopManager(this, screen);
            prepareAndStart(currentRomUri);
        }, "KrendBuoy-restart-v2").start();
    }

    @Override
    public void leaveGame() {
        if (finishingFromMenu) return;
        finishingFromMenu = true;
        NativeBridge.saveSram();
        exportPortableSramIfEnabled();
        releaseAllButtons();
        finish();
    }

    @Override
    public void showStateSlotDialog(boolean save) {
        pauseEmulationForMenu();
        if (saveStateManager == null) saveStateManager = new SaveStateManager(this, portableSaveFolderUri, romSessionManager.ensureDirectory("states"), romBaseName);
        String[] labels = new String[5];
        for (int i = 0; i < labels.length; i++) labels[i] = saveStateManager.slotLabel(i + 1);
        new AlertDialog.Builder(this)
                .setTitle(save ? "Save State" : "Load State")
                .setItems(labels, (dialog, which) -> {
                    if (save) confirmAndSaveState(which + 1);
                    else loadStateNow(which + 1);
                })
                .setNegativeButton("Cancel", (dialog, which) -> resumeEmulationFromMenu())
                .setOnCancelListener(dialog -> resumeEmulationFromMenu())
                .show();
    }

    private void confirmAndSaveState(int slot) {
        pauseEmulationForMenu();
        if (saveStateManager.getModifiedTime(slot) <= 0) {
            saveStateNow(slot);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Overwrite Save State")
                .setMessage("Slot " + slot + " already has a save state.\n" + saveStateManager.slotLabel(slot) + "\n\nOverwrite it?")
                .setPositiveButton("Overwrite", (dialog, which) -> saveStateNow(slot))
                .setNegativeButton("Cancel", (dialog, which) -> resumeEmulationFromMenu())
                .setOnCancelListener(dialog -> resumeEmulationFromMenu())
                .show();
    }

    private void saveStateNow(int slot) {
        try {
            byte[] data = NativeBridge.exportState();
            if (saveStateManager.write(data, slot)) showQuickNotice("Save State", "Saved to Slot " + slot + "\n" + saveStateManager.stateFileName(slot));
            else showQuickNotice("Save State", "Save state write failed.");
        } catch (Throwable t) {
            showQuickNotice("Save State", "Save state failed:\n" + t.getMessage());
        }
    }

    private void loadStartupStateIfRequested() {
        if (startupLoadStateSlot <= 0) return;
        int slot = startupLoadStateSlot;
        startupLoadStateSlot = 0;
        try {
            byte[] data = saveStateManager.read(slot);
            if (data == null || data.length == 0) {
                updateInfo("Startup save state not found: Slot " + slot);
                return;
            }
            boolean ok = NativeBridge.importState(data);
            updateInfo(ok ? "Startup save state loaded: Slot " + slot : "Startup load state failed:\n" + NativeBridge.getLastError());
        } catch (Throwable t) {
            updateInfo("Startup load state failed:\n" + t.getMessage());
        }
    }

    private void loadStateNow(int slot) {
        try {
            byte[] data = saveStateManager.read(slot);
            if (data == null || data.length == 0) {
                showQuickNotice("Load State", "No save state found in Slot " + slot + ".");
                return;
            }
            boolean ok = NativeBridge.importState(data);
            showQuickNotice("Load State", ok ? "Loaded Slot " + slot + "\n" + saveStateManager.stateFileName(slot) : "Load state failed:\n" + NativeBridge.getLastError());
        } catch (Throwable t) {
            showQuickNotice("Load State", "Load state failed:\n" + t.getMessage());
        }
    }

    @Override
    public void showDisplaySettingsDialog() {
        pauseEmulationForMenu();
        String[] labels = {"Fit Screen", "Original Ratio", "Stretch", debugTextVisible ? "Hide Debug Text" : "Show Debug Text"};
        new AlertDialog.Builder(this)
                .setTitle("Display Settings")
                .setItems(labels, (dialog, which) -> {
                    if (which <= 2) {
                        displayMode = which;
                        applyDisplayMode();
                    } else {
                        debugTextVisible = !debugTextVisible;
                        info.setVisibility(debugTextVisible ? View.VISIBLE : View.GONE);
                    }
                    resumeEmulationFromMenu();
                })
                .setNegativeButton("Cancel", (dialog, which) -> resumeEmulationFromMenu())
                .setOnCancelListener(dialog -> resumeEmulationFromMenu())
                .show();
    }

    @Override
    public void showAudioPresetDialog() {
        pauseEmulationForMenu();
        String[] labels = {"Dynamic - recommended", "1024 - ultra low latency, may crackle", "2048 - low latency", "4096 - balanced"};
        int[] values = {-1, 1024, 2048, 4096};
        int checked = 0;
        for (int i = 0; i < values.length; i++) if (values[i] == audioBacklogSamples) checked = i;
        new AlertDialog.Builder(this)
                .setTitle("Audio Preset")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    audioBacklogSamples = values[which];
                    NativeBridge.setAudioMaxBufferedSamples(audioBacklogSamples);
                    updateInfo("audio preset " + audioPresetLabel(audioBacklogSamples) + "\n" + NativeBridge.getLastError());
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .setOnDismissListener(dialog -> resumeEmulationFromMenu())
                .show();
    }

    @Override
    public void showControllerSettingsDialog() {
        GameSettingsDialogs.showControllerSettings(this, this);
    }

    @Override
    public void showGlobalSettingsDialog() {
        GameSettingsDialogs.showGlobalSettings(this, this);
    }

    @Override
    public void showUnavailableFeature(String title, String message) {
        pauseEmulationForMenu();
        showQuickNotice(title, message);
    }

    private void applyDisplayMode() {
        screen.setAdjustViewBounds(displayMode != 2);
        screen.setScaleType(displayMode == 2 ? ImageView.ScaleType.FIT_XY : ImageView.ScaleType.FIT_CENTER);
        updateInfo("Display mode: " + (displayMode == 1 ? "Original Ratio" : displayMode == 2 ? "Stretch" : "Fit Screen"));
    }

    private void showQuickNotice(String title, String message) {
        pauseEmulationForMenu();
        updateInfo(message);
        runOnUiThread(() -> new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .setOnDismissListener(dialog -> resumeEmulationFromMenu())
                .show());
    }

    private int normalizeAudioPreset(int value) {
        return value == 1024 || value == 2048 || value == 4096 ? value : -1;
    }

    private String audioPresetLabel(int value) {
        return value == 1024 ? "1024" : value == 2048 ? "2048" : value == 4096 ? "4096" : "Dynamic";
    }

    private void importPortableSramIfAvailable() {
        if (portableSaveManager != null) portableSaveManager.importIfAvailable(romBaseName);
    }

    private void exportPortableSramIfEnabled() {
        if (portableSaveManager != null) portableSaveManager.exportIfEnabled(romBaseName);
    }

    private void updateInfo(String text) {
        runOnUiThread(() -> {
            if (info != null) info.setText(text);
        });
    }

    @Override
    public int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
