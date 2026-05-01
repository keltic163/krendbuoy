package com.krendstudio.krendbuoy;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class GameActivityV2 extends Activity implements GameControllerOverlay.Host, GameQuickMenu.Host, GameSettingsDialogs.Host, FrameLoopManager.Host, PortableSaveManager.Host {
    private TextView info;
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
    private boolean colorCorrectionEnabled = true;
    private int bgDimmingLevel = 0;
    private boolean screenBorderEnabled = false;
    private int startupLoadStateSlot = 0;
    private SaveStateManager saveStateManager;
    private FrameLayout dimOverlay;
    private View screenBorder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settingsManager = new AppSettingsManager(this);
        currentRomUri = getIntent().getData();
        romSessionManager = new RomSessionManager(this);
        audioBacklogSamples = settingsManager.getAudioPreset();
        displayMode = settingsManager.getDisplayMode();
        debugTextVisible = settingsManager.isDebugTextVisible();
        colorCorrectionEnabled = settingsManager.isColorCorrectionEnabled();
        bgDimmingLevel = settingsManager.getBgDimmingLevel();
        screenBorderEnabled = settingsManager.isScreenBorderEnabled();
        startupLoadStateSlot = getIntent().getIntExtra("load_state_slot", 0);
        if (startupLoadStateSlot < 1 || startupLoadStateSlot > SaveStateManager.SLOT_COUNT) startupLoadStateSlot = 0;
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

        info = new TextView(this);
        info.setText("00:00:00 / 0 FPS");
        info.setTextSize(11f);
        info.setTextColor(Color.GRAY);
        info.setGravity(Gravity.LEFT);
        info.setPadding(dp(8), 0, 0, dp(4));
        info.setVisibility(debugTextVisible ? View.VISIBLE : View.GONE);
        content.addView(info, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout screenBox = new FrameLayout(this);
        screenBox.setBackgroundColor(Color.BLACK);
        content.addView(screenBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, screenHeight));

        screen = new ImageView(this);
        screen.setAdjustViewBounds(false);
        screen.setScaleType(ImageView.ScaleType.FIT_CENTER);
        screenBox.addView(screen, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        frameLoopManager = new FrameLoopManager(this, screen);

        screenBorder = new View(this);
        screenBorder.setBackground(new BorderDrawable(dp(4), Color.GRAY));
        screenBorder.setVisibility(screenBorderEnabled ? View.VISIBLE : View.GONE);
        screenBox.addView(screenBorder, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        dimOverlay = new FrameLayout(this);
        dimOverlay.setBackgroundColor(Color.TRANSPARENT);
        // dimOverlay will be added later to control Z-order
        
        applyDisplayMode();

        // Important: dimOverlay should be ABOVE content but BELOW GameControllerOverlay
        root.addView(dimOverlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        updateDimOverlay();

        GameControllerOverlay.attach(this, root, this);
        setContentView(root);
        new Thread(() -> prepareAndStart(currentRomUri), "KrendBuoy-prepare-v2").start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!finishingFromMenu && !restarting && !menuPaused && frameLoopManager != null && frameLoopManager.isRunning()) {
            startAudioPlayback();
        }
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
        updateInfo("Running... speed " + emulationSpeedLabel() + " audio preset " + AppSettingsManager.audioPresetLabel(audioBacklogSamples) + "\n" + NativeBridge.getLastError());
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
    public int emulationSpeedMultiplierForFrameLoop() {
        return emulationSpeedMultiplier;
    }

    @Override
    public void updateFrameInfo(String text) {
        updateInfo(text);
    }

    @Override
    public String audioPresetLabelForFrameLoop() {
        return AppSettingsManager.audioPresetLabel(audioBacklogSamples);
    }

    @Override
    public void cycleEmulationSpeed() {
        emulationSpeedMultiplier = emulationSpeedMultiplier >= 3 ? 1 : emulationSpeedMultiplier + 1;
        updateInfo("Speed " + emulationSpeedLabel());
    }

    @Override
    public String emulationSpeedLabel() {
        return emulationSpeedMultiplier + "x";
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
        String[] labels = new String[SaveStateManager.SLOT_COUNT];
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
            if (saveStateManager.write(data, slot)) showToast("Saved Slot " + slot);
            else showToast("Save state write failed");
        } catch (Throwable t) {
            showToast("Save state failed: " + safeMessage(t));
        }
        resumeEmulationFromMenu();
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
                showToast("Slot " + slot + " is empty");
                resumeEmulationFromMenu();
                return;
            }
            boolean ok = NativeBridge.importState(data);
            showToast(ok ? "Loaded Slot " + slot : "Load state failed");
        } catch (Throwable t) {
            showToast("Load state failed: " + safeMessage(t));
        }
        resumeEmulationFromMenu();
    }

    @Override
    public void showDisplaySettingsDialog() {
        pauseEmulationForMenu();
        String[] sections = {"Screen Scaling", "Color Correction", "Background Dimming", "Screen Border", "Debug Text"};
        new AlertDialog.Builder(this)
                .setTitle("Display Settings")
                .setItems(sections, (dialog, which) -> {
                    if (which == 0) showScalingDialog();
                    else if (which == 1) toggleColorCorrection();
                    else if (which == 2) showDimmingDialog();
                    else if (which == 3) toggleScreenBorder();
                    else if (which == 4) toggleDebugText();
                })
                .setNegativeButton("Back", (dialog, which) -> resumeEmulationFromMenu())
                .setOnCancelListener(dialog -> resumeEmulationFromMenu())
                .show();
    }

    private void showScalingDialog() {
        String[] labels = {"Fit Screen", "Original Ratio", "Stretch", "Pixel Perfect (2x)"};
        new AlertDialog.Builder(this)
                .setTitle("Screen Scaling")
                .setSingleChoiceItems(labels, displayMode, (dialog, which) -> {
                    displayMode = which;
                    settingsManager.setDisplayMode(displayMode);
                    applyDisplayMode();
                    dialog.dismiss();
                    resumeEmulationFromMenu();
                })
                .show();
    }

    private void toggleColorCorrection() {
        colorCorrectionEnabled = !colorCorrectionEnabled;
        settingsManager.setColorCorrectionEnabled(colorCorrectionEnabled);
        showToast("Color Correction: " + (colorCorrectionEnabled ? "On" : "Off"));
        resumeEmulationFromMenu();
    }

    private void showDimmingDialog() {
        String[] labels = {"Brightest (100%)", "Bright (80%)", "Medium (60%)", "Dim (40%)"};
        new AlertDialog.Builder(this)
                .setTitle("Screen Brightness")
                .setSingleChoiceItems(labels, bgDimmingLevel, (dialog, which) -> {
                    bgDimmingLevel = which;
                    settingsManager.setBgDimmingLevel(bgDimmingLevel);
                    updateDimOverlay();
                    dialog.dismiss();
                    resumeEmulationFromMenu();
                })
                .show();
    }

    private void updateDimOverlay() {
        if (dimOverlay == null) return;
        dimOverlay.setVisibility(bgDimmingLevel > 0 ? View.VISIBLE : View.GONE);
        dimOverlay.removeAllViews();
        if (bgDimmingLevel > 0) {
            View dimView = new View(this);
            dimView.setBackgroundColor(Color.BLACK);
            dimView.setAlpha(getDimAlpha(bgDimmingLevel));
            dimOverlay.addView(dimView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            
            // Allow clicks to pass through dimOverlay to the content/controller below
            // Note: dimOverlay is a FrameLayout, setting clickable(false) on dimView
            dimView.setClickable(false);
            dimOverlay.setClickable(false);
        }
    }

    private void toggleScreenBorder() {
        screenBorderEnabled = !screenBorderEnabled;
        settingsManager.setScreenBorderEnabled(screenBorderEnabled);
        if (screenBorder != null) screenBorder.setVisibility(screenBorderEnabled ? View.VISIBLE : View.GONE);
        resumeEmulationFromMenu();
    }

    private void toggleDebugText() {
        debugTextVisible = !debugTextVisible;
        settingsManager.setDebugTextVisible(debugTextVisible);
        info.setVisibility(debugTextVisible ? View.VISIBLE : View.GONE);
        resumeEmulationFromMenu();
    }

    private float getDimAlpha(int level) {
        if (level == 1) return 0.2f;
        if (level == 2) return 0.4f;
        if (level == 3) return 0.6f;
        return 0.0f;
    }

    @Override
    public void showAudioPresetDialog() {
        pauseEmulationForMenu();
        String[] labels = {"Dynamic - recommended", "1024 - ultra low latency, may crackle", "2048 - low latency", "4096 - balanced"};
        int[] values = {AppSettingsManager.AUDIO_DYNAMIC, AppSettingsManager.AUDIO_1024, AppSettingsManager.AUDIO_2048, AppSettingsManager.AUDIO_4096};
        int checked = 0;
        for (int i = 0; i < values.length; i++) if (values[i] == audioBacklogSamples) checked = i;
        new AlertDialog.Builder(this)
                .setTitle("Audio Preset")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    audioBacklogSamples = values[which];
                    settingsManager.setAudioPreset(audioBacklogSamples);
                    NativeBridge.setAudioMaxBufferedSamples(audioBacklogSamples);
                    updateInfo("audio preset " + AppSettingsManager.audioPresetLabel(audioBacklogSamples) + "\n" + NativeBridge.getLastError());
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

    public void showGlobalSettingsDialog() {
        GameSettingsDialogs.showGlobalSettings(this, this);
    }

    public void showUnavailableFeature(String title, String message) {
        pauseEmulationForMenu();
        showQuickNotice(title, message);
    }

    private void applyDisplayMode() {
        if (displayMode == AppSettingsManager.DISPLAY_PIXEL_PERFECT) {
            screen.setAdjustViewBounds(true);
            screen.setScaleType(ImageView.ScaleType.CENTER);
            // GBA is 240x160. 2x is 480x320.
            ViewGroup.LayoutParams lp = screen.getLayoutParams();
            lp.width = dp(480);
            lp.height = dp(320);
            screen.setLayoutParams(lp);
        } else {
            screen.setAdjustViewBounds(displayMode != AppSettingsManager.DISPLAY_STRETCH);
            screen.setScaleType(displayMode == AppSettingsManager.DISPLAY_STRETCH ? ImageView.ScaleType.FIT_XY : ImageView.ScaleType.FIT_CENTER);
            ViewGroup.LayoutParams lp = screen.getLayoutParams();
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            screen.setLayoutParams(lp);
        }
        updateInfo("Display mode: " + AppSettingsManager.displayModeLabel(displayMode));
    }

    private void showToast(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
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

    private String safeMessage(Throwable t) {
        String message = t == null ? null : t.getMessage();
        return message == null || message.isEmpty() ? "Unknown error" : message;
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

    private static class BorderDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int strokeWidth;

        BorderDrawable(int strokeWidth, int color) {
            this.strokeWidth = strokeWidth;
            this.paint.setColor(color);
            this.paint.setStyle(Paint.Style.STROKE);
            this.paint.setStrokeWidth(strokeWidth);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            float half = strokeWidth / 2f;
            canvas.drawRect(bounds.left + half, bounds.top + half, bounds.right - half, bounds.bottom - half, paint);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter colorFilter) { paint.setColorFilter(colorFilter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
