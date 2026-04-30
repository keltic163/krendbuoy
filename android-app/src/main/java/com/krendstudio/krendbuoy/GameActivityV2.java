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
    private TextView toastView;
    private ImageView screen;
    private Uri currentRomUri;
    private volatile boolean finishingFromMenu;
    private volatile boolean menuPaused;
    private volatile boolean restarting;
    private volatile int emulationSpeedMultiplier = 1;
    private FrameLoopManager frameLoopManager;
    private final AudioPlaybackManager audioPlaybackManager = new AudioPlaybackManager();
    private AppSettingsManager settingsManager;
    private RomSessionManager romSessionManager;
    private PortableSaveManager portableSaveManager;
    private int audioBacklogSamples = AppSettingsManager.AUDIO_DYNAMIC;
    private Uri portableSaveFolderUri;
    private String romBaseName = "selected";
    private int displayMode = AppSettingsManager.DISPLAY_FIT;
    private boolean debugTextVisible = false;
    private int startupLoadStateSlot = 0;
    private SaveStateManager saveStateManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settingsManager = new AppSettingsManager(this);

        FrameLayout root = new FrameLayout(this);

        toastView = new TextView(this);
        toastView.setTextColor(Color.WHITE);
        toastView.setBackgroundColor(0xCC000000);
        toastView.setPadding(20,10,20,10);
        toastView.setAlpha(0f);

        FrameLayout.LayoutParams toastLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        toastLp.topMargin = 80;
        root.addView(toastView, toastLp);

        currentRomUri = getIntent().getData();
        romSessionManager = new RomSessionManager(this);
        audioBacklogSamples = settingsManager.getAudioPreset();
        displayMode = settingsManager.getDisplayMode();
        debugTextVisible = settingsManager.isDebugTextVisible();

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content);

        screen = new ImageView(this);
        content.addView(screen);

        info = new TextView(this);
        content.addView(info);

        GameControllerOverlay.attach(this, root, this);
        setContentView(root);

        new Thread(() -> prepareAndStart(currentRomUri)).start();
    }

    private void showToast(String text) {
        runOnUiThread(() -> {
            toastView.setText(text);
            toastView.setAlpha(1f);
            toastView.animate().alpha(0f).setStartDelay(1000).setDuration(400).start();
        });
    }

    @Override
    public void showStateSlotDialog(boolean save) {
        pauseEmulationForMenu();

        if (saveStateManager == null) {
            saveStateManager = new SaveStateManager(this, portableSaveFolderUri, romSessionManager.ensureDirectory("states"), romBaseName);
        }

        String[] labels = new String[SaveStateManager.SLOT_COUNT];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = saveStateManager.slotLabel(i + 1);
        }

        new AlertDialog.Builder(this)
                .setItems(labels, (d, which) -> {
                    if (save) {
                        saveStateNow(which + 1);
                    } else {
                        loadStateNow(which + 1);
                    }
                })
                .setOnDismissListener(d -> resumeEmulationFromMenu())
                .show();
    }

    private void saveStateNow(int slot) {
        try {
            byte[] data = NativeBridge.exportState();
            if (saveStateManager.write(data, slot)) {
                showToast("Saved Slot " + slot);
            }
        } catch (Throwable ignored) {}
    }

    private void loadStateNow(int slot) {
        try {
            byte[] data = saveStateManager.read(slot);
            if (data != null) {
                NativeBridge.importState(data);
                showToast("Loaded Slot " + slot);
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void cycleEmulationSpeed() {
        emulationSpeedMultiplier = emulationSpeedMultiplier >= 3 ? 1 : emulationSpeedMultiplier + 1;
        showToast("Speed " + emulationSpeedMultiplier + "x");
    }

    @Override
    public String emulationSpeedLabel() {
        return emulationSpeedMultiplier + "x";
    }

    @Override public void showQuickMenu() {}
    @Override public void pauseEmulationForMenu() {}
    @Override public void resumeEmulationFromMenu() {}
    @Override public boolean isFrameLoopPaused() { return false; }
    @Override public int emulationSpeedMultiplierForFrameLoop() { return emulationSpeedMultiplier; }
    @Override public void updateFrameInfo(String text) {}
    @Override public String audioPresetLabelForFrameLoop() { return ""; }
    @Override public void updatePortableSaveInfo(String text) {}
    @Override public void releaseAllButtons() {}
    @Override public void restartGame() {}
    @Override public void leaveGame() {}
    @Override public void showDisplaySettingsDialog() {}
    @Override public void showAudioPresetDialog() {}
    @Override public void showControllerSettingsDialog() {}
    @Override public void showGlobalSettingsDialog() {}
    @Override public void showUnavailableFeature(String title, String message) {}
    @Override public int dp(int value) { return value; }

    private void prepareAndStart(Uri uri) {}
}
