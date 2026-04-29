package com.krendstudio.krendbuoy;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class GameActivityV2 extends Activity implements GameControllerOverlay.Host, GameQuickMenu.Host, GameSettingsDialogs.Host {
    private TextView info;
    private ImageView screen;
    private volatile boolean running;
    private volatile boolean audioRunning;
    private volatile boolean finishingFromMenu;
    private volatile boolean menuPaused;
    private Thread frameThread;
    private Thread audioThread;
    private AudioTrack audioTrack;
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
        Uri romUri = getIntent().getData();
        audioBacklogSamples = normalizeAudioPreset(getIntent().getIntExtra("audio_backlog_samples", -1));
        startupLoadStateSlot = getIntent().getIntExtra("load_state_slot", 0);
        if (startupLoadStateSlot < 1 || startupLoadStateSlot > 5) startupLoadStateSlot = 0;
        String saveFolder = getIntent().getStringExtra("save_folder_uri");
        if (saveFolder != null && !saveFolder.isEmpty()) portableSaveFolderUri = Uri.parse(saveFolder);

        FrameLayout root = new FrameLayout(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(8), dp(4), dp(8), dp(176));
        root.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        screen = new ImageView(this);
        screen.setAdjustViewBounds(true);
        screen.setScaleType(ImageView.ScaleType.FIT_CENTER);
        content.addView(screen, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        info = new TextView(this);
        info.setText("Preparing ROM...");
        info.setTextSize(12f);
        info.setTextColor(Color.DKGRAY);
        info.setGravity(Gravity.CENTER);
        info.setVisibility(debugTextVisible ? View.VISIBLE : View.GONE);
        content.addView(info, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        GameControllerOverlay.attach(this, root, this);
        setContentView(root);
        new Thread(() -> prepareAndStart(romUri), "KrendBuoy-prepare-v2").start();
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
        running = false;
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
        if (romUri == null) { updateInfo("No ROM URI received."); return; }
        try {
            String romDisplayName = getDisplayName(romUri);
            romBaseName = removeKnownRomExtension(romDisplayName);
            saveStateManager = new SaveStateManager(this, portableSaveFolderUri, ensureDirectory("states"), romBaseName);

            File localRom = copyRomToLocalFile(romUri);
            NativeBridge.setDirectories(ensureDirectory("system").getAbsolutePath(), ensureDirectory("save").getAbsolutePath());
            if (!NativeBridge.loadRom(localRom.getAbsolutePath())) {
                updateInfo("loadRom failed:\n" + NativeBridge.getLastError());
                return;
            }
            NativeBridge.setAudioMaxBufferedSamples(audioBacklogSamples);
            importPortableSramIfAvailable();
            loadStartupStateIfRequested();
            updateInfo("Running... audio preset " + audioPresetLabel(audioBacklogSamples) + "\n" + NativeBridge.getLastError());
            startAudioPlayback();
            startFrameLoop();
        } catch (Throwable t) {
            updateInfo("ROM prepare/load failed:\n" + t.getMessage());
        }
    }

    private void startFrameLoop() {
        if (running) return;
        running = true;
        frameThread = new Thread(() -> {
            long frame = 0;
            while (running) {
                if (menuPaused) { sleepQuietly(16); continue; }
                long start = System.currentTimeMillis();
                if (NativeBridge.runFrame()) {
                    int width = NativeBridge.getFrameWidth();
                    int height = NativeBridge.getFrameHeight();
                    int[] pixels = NativeBridge.copyFramePixels();
                    if (width > 0 && height > 0 && pixels != null && pixels.length == width * height) {
                        Bitmap bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
                        runOnUiThread(() -> screen.setImageBitmap(bitmap));
                    }
                    if (++frame % 30 == 0) updateInfo("audio preset " + audioPresetLabel(audioBacklogSamples) + "\n" + NativeBridge.getLastError());
                } else {
                    updateInfo("runFrame failed:\n" + NativeBridge.getLastError());
                    running = false;
                }
                long sleep = 16L - (System.currentTimeMillis() - start);
                if (sleep > 0) sleepQuietly(sleep);
            }
        }, "VBAM-frame-loop-v2");
        frameThread.start();
    }

    private void startAudioPlayback() {
        stopAudioPlayback();
        int sampleRate = NativeBridge.getAudioSampleRate();
        int channelConfig = AudioFormat.CHANNEL_OUT_STEREO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int bufferBytes = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat);
        if (bufferBytes <= 0) bufferBytes = Math.max(4096, sampleRate / 4);
        audioTrack = new AudioTrack(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).setFlags(AudioAttributes.FLAG_LOW_LATENCY).build(), new AudioFormat.Builder().setSampleRate(sampleRate).setEncoding(audioFormat).setChannelMask(channelConfig).build(), bufferBytes, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
        audioTrack.play();
        audioRunning = true;
        int shortBufferSize = Math.max(512, bufferBytes / 4);
        audioThread = new Thread(() -> {
            short[] buffer = new short[shortBufferSize];
            while (audioRunning) {
                int count = NativeBridge.readAudioSamples(buffer, buffer.length);
                if (count > 0 && audioTrack != null) audioTrack.write(buffer, 0, count);
                else sleepQuietly(1);
            }
        }, "VBAM-audio-loop-v2");
        audioThread.start();
    }

    private void stopAudioPlayback() {
        audioRunning = false;
        if (audioThread != null) { audioThread.interrupt(); audioThread = null; }
        if (audioTrack != null) {
            try { audioTrack.pause(); audioTrack.flush(); audioTrack.release(); } catch (Throwable ignored) {}
            audioTrack = null;
        }
    }

    @Override
    public void showQuickMenu() { GameQuickMenu.show(this, this); }

    @Override
    public void pauseEmulationForMenu() {
        if (finishingFromMenu) return;
        menuPaused = true;
        releaseAllButtons();
        stopAudioPlayback();
    }

    @Override
    public void resumeEmulationFromMenu() {
        if (finishingFromMenu || !running || !menuPaused) return;
        menuPaused = false;
        startAudioPlayback();
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
    public void restartGame() { showUnavailableFeature("Restart Game", "Restart will be added after GameActivityV2 is verified."); }

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
        if (saveStateManager == null) saveStateManager = new SaveStateManager(this, portableSaveFolderUri, ensureDirectory("states"), romBaseName);
        String[] labels = new String[5];
        for (int i = 0; i < labels.length; i++) labels[i] = saveStateManager.slotLabel(i + 1);
        new AlertDialog.Builder(this).setTitle(save ? "Save State" : "Load State").setItems(labels, (dialog, which) -> { if (save) confirmAndSaveState(which + 1); else loadStateNow(which + 1); }).setNegativeButton("Cancel", (dialog, which) -> resumeEmulationFromMenu()).setOnCancelListener(dialog -> resumeEmulationFromMenu()).show();
    }

    private void confirmAndSaveState(int slot) {
        if (saveStateManager.getModifiedTime(slot) <= 0) { saveStateNow(slot); return; }
        new AlertDialog.Builder(this).setTitle("Overwrite Save State").setMessage("Slot " + slot + " already has a save state.\n" + saveStateManager.slotLabel(slot) + "\n\nOverwrite it?").setPositiveButton("Overwrite", (dialog, which) -> saveStateNow(slot)).setNegativeButton("Cancel", (dialog, which) -> resumeEmulationFromMenu()).setOnCancelListener(dialog -> resumeEmulationFromMenu()).show();
    }

    private void saveStateNow(int slot) {
        try {
            byte[] data = NativeBridge.exportState();
            if (saveStateManager.write(data, slot)) showQuickNotice("Save State", "Saved to Slot " + slot + "\n" + saveStateManager.stateFileName(slot));
            else showQuickNotice("Save State", "Save state write failed.");
        } catch (Throwable t) { showQuickNotice("Save State", "Save state failed:\n" + t.getMessage()); }
    }

    private void loadStartupStateIfRequested() {
        if (startupLoadStateSlot <= 0) return;
        int slot = startupLoadStateSlot;
        startupLoadStateSlot = 0;
        try {
            byte[] data = saveStateManager.read(slot);
            if (data == null || data.length == 0) { updateInfo("Startup save state not found: Slot " + slot); return; }
            boolean ok = NativeBridge.importState(data);
            updateInfo(ok ? "Startup save state loaded: Slot " + slot : "Startup load state failed:\n" + NativeBridge.getLastError());
        } catch (Throwable t) { updateInfo("Startup load state failed:\n" + t.getMessage()); }
    }

    private void loadStateNow(int slot) {
        try {
            byte[] data = saveStateManager.read(slot);
            if (data == null || data.length == 0) { showQuickNotice("Load State", "No save state found in Slot " + slot + "."); return; }
            boolean ok = NativeBridge.importState(data);
            showQuickNotice("Load State", ok ? "Loaded Slot " + slot + "\n" + saveStateManager.stateFileName(slot) : "Load state failed:\n" + NativeBridge.getLastError());
        } catch (Throwable t) { showQuickNotice("Load State", "Load state failed:\n" + t.getMessage()); }
    }

    @Override
    public void showDisplaySettingsDialog() {
        String[] labels = {"Fit Screen", "Original Ratio", "Stretch", debugTextVisible ? "Hide Debug Text" : "Show Debug Text"};
        new AlertDialog.Builder(this).setTitle("Display Settings").setItems(labels, (dialog, which) -> { if (which <= 2) { displayMode = which; applyDisplayMode(); } else { debugTextVisible = !debugTextVisible; info.setVisibility(debugTextVisible ? View.VISIBLE : View.GONE); } resumeEmulationFromMenu(); }).setNegativeButton("Cancel", (dialog, which) -> resumeEmulationFromMenu()).setOnCancelListener(dialog -> resumeEmulationFromMenu()).show();
    }

    @Override
    public void showAudioPresetDialog() {
        String[] labels = {"Dynamic - recommended", "1024 - ultra low latency, may crackle", "2048 - low latency", "4096 - balanced"};
        int[] values = {-1, 1024, 2048, 4096};
        int checked = 0;
        for (int i = 0; i < values.length; i++) if (values[i] == audioBacklogSamples) checked = i;
        new AlertDialog.Builder(this).setTitle("Audio Preset").setSingleChoiceItems(labels, checked, (dialog, which) -> { audioBacklogSamples = values[which]; NativeBridge.setAudioMaxBufferedSamples(audioBacklogSamples); updateInfo("audio preset " + audioPresetLabel(audioBacklogSamples) + "\n" + NativeBridge.getLastError()); dialog.dismiss(); }).setNegativeButton("Cancel", null).setOnDismissListener(dialog -> resumeEmulationFromMenu()).show();
    }

    @Override
    public void showControllerSettingsDialog() { GameSettingsDialogs.showControllerSettings(this, this); }

    @Override
    public void showGlobalSettingsDialog() { GameSettingsDialogs.showGlobalSettings(this, this); }

    @Override
    public void showUnavailableFeature(String title, String message) { pauseEmulationForMenu(); showQuickNotice(title, message); }

    private void applyDisplayMode() {
        screen.setAdjustViewBounds(displayMode != 2);
        screen.setScaleType(displayMode == 2 ? ImageView.ScaleType.FIT_XY : ImageView.ScaleType.FIT_CENTER);
        updateInfo("Display mode: " + (displayMode == 1 ? "Original Ratio" : displayMode == 2 ? "Stretch" : "Fit Screen"));
    }

    private void showQuickNotice(String title, String message) { updateInfo(message); runOnUiThread(() -> new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).setOnDismissListener(dialog -> resumeEmulationFromMenu()).show()); }
    private int normalizeAudioPreset(int value) { return value == 1024 || value == 2048 || value == 4096 ? value : -1; }
    private String audioPresetLabel(int value) { return value == 1024 ? "1024" : value == 2048 ? "2048" : value == 4096 ? "4096" : "Dynamic"; }

    private void importPortableSramIfAvailable() {
        if (portableSaveFolderUri == null) return;
        try {
            Uri sav = findChildDocument(romBaseName + ".sav");
            Uri srm = sav != null ? sav : findChildDocument(romBaseName + ".srm");
            if (srm == null) return;
            byte[] data = readAllBytes(srm);
            if (data != null && data.length > 0) NativeBridge.importSram(data);
        } catch (Throwable t) { updateInfo("Portable save load failed:\n" + t.getMessage()); }
    }

    private void exportPortableSramIfEnabled() {
        if (portableSaveFolderUri == null) return;
        try {
            byte[] data = NativeBridge.exportSram();
            if (data == null || data.length == 0) return;
            Uri target = findChildDocument(romBaseName + ".sav");
            if (target == null) target = createChildDocument(romBaseName + ".sav");
            if (target == null) return;
            try (OutputStream output = getContentResolver().openOutputStream(target, "wt")) { if (output != null) { output.write(data); output.flush(); } }
        } catch (Throwable t) { updateInfo("Portable save write failed:\n" + t.getMessage()); }
    }

    private Uri findChildDocument(String fileName) {
        if (portableSaveFolderUri == null) return null;
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(portableSaveFolderUri, DocumentsContract.getTreeDocumentId(portableSaveFolderUri));
        try (Cursor cursor = getContentResolver().query(childrenUri, new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID}, null, null, null)) {
            if (cursor == null) return null;
            while (cursor.moveToNext()) if (fileName.equals(cursor.getString(0))) return DocumentsContract.buildDocumentUriUsingTree(portableSaveFolderUri, cursor.getString(1));
        }
        return null;
    }

    private Uri createChildDocument(String fileName) throws Exception {
        Uri parent = DocumentsContract.buildDocumentUriUsingTree(portableSaveFolderUri, DocumentsContract.getTreeDocumentId(portableSaveFolderUri));
        return DocumentsContract.createDocument(getContentResolver(), parent, "application/octet-stream", fileName);
    }

    private byte[] readAllBytes(Uri uri) throws Exception {
        try (InputStream input = getContentResolver().openInputStream(uri); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) return null;
            byte[] buffer = new byte[16384];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return output.toByteArray();
        }
    }

    private File copyRomToLocalFile(Uri uri) throws Exception {
        File target = new File(getCacheDir(), "selected-rom-" + System.currentTimeMillis());
        try (InputStream input = getContentResolver().openInputStream(uri); FileOutputStream output = new FileOutputStream(target)) {
            if (input == null) throw new IllegalStateException("Unable to open ROM input stream");
            byte[] buffer = new byte[16384];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        }
        return target;
    }

    private String getDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (Throwable ignored) {}
        String path = uri.getLastPathSegment();
        return path == null || path.isEmpty() ? "selected" : path;
    }

    private String removeKnownRomExtension(String name) {
        if (name == null || name.isEmpty()) return "selected";
        String lower = name.toLowerCase();
        if (lower.endsWith(".gba") || lower.endsWith(".gbc")) return name.substring(0, name.length() - 4);
        if (lower.endsWith(".gb")) return name.substring(0, name.length() - 3);
        return name;
    }

    private File ensureDirectory(String name) {
        File dir = new File(getFilesDir(), name);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private void updateInfo(String text) { runOnUiThread(() -> { if (info != null) info.setText(text); }); }
    private void sleepQuietly(long millis) { try { Thread.sleep(millis); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); } }
    @Override public int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
