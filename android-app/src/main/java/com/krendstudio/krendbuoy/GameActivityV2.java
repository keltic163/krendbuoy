package com.krendstudio.krendbuoy;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
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
    private int displayMode = AppSettingsManager.SCALE_AUTO_FIT;
    private boolean debugTextVisible = false;
    private boolean screenBorderEnabled = false;
    private int startupLoadStateSlot = 0;
    private SaveStateManager saveStateManager;
    private CheatManager cheatManager;
    private PokemonManager pokemonManager;
    private MemoryScanner memoryScanner;
    private FrameLayout screenBox;
    private View screenBorder;
    
    // Performance optimization and Seamless Resumption
    private static byte[] sInstantStateCache = null;
    private final Map<Integer, Bitmap> thumbnailCache = new HashMap<>();
    private final ExecutorService diskExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settingsManager = new AppSettingsManager(this);
        currentRomUri = getIntent().getData();
        romSessionManager = new RomSessionManager(this);
        audioBacklogSamples = settingsManager.getAudioPreset();
        displayMode = settingsManager.getDisplayMode();
        debugTextVisible = settingsManager.isDebugTextVisible();
        screenBorderEnabled = settingsManager.isScreenBorderEnabled();
        NativeBridge.setColorMode(settingsManager.getColorMode());
        pokemonManager = new PokemonManager();
        memoryScanner = new MemoryScanner();
        startupLoadStateSlot = getIntent().getIntExtra("load_state_slot", 0);
        if (startupLoadStateSlot < 1 || startupLoadStateSlot > SaveStateManager.SLOT_COUNT) startupLoadStateSlot = 0;
        getIntent().removeExtra("load_state_slot"); // One-time use
        String saveFolder = getIntent().getStringExtra("save_folder_uri");
        if (saveFolder != null && !saveFolder.isEmpty()) portableSaveFolderUri = Uri.parse(saveFolder);
        portableSaveManager = new PortableSaveManager(this, portableSaveFolderUri, this);

        rebuildUi();
        new Thread(() -> prepareAndStart(currentRomUri), "KrendBuoy-prepare-v2").start();
    }

    private void rebuildUi() {
        boolean landscape = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        updateSystemUi(landscape);
        
        GamePortraitLayout.Result res = landscape 
                ? GameLandscapeLayout.build(this, this)
                : GamePortraitLayout.build(this, this);
        
        this.info = res.info;
        this.screen = res.screen;
        this.screenBox = res.screenBox;
        this.screenBorder = res.screenBorder;
        
        info.setVisibility(debugTextVisible ? View.VISIBLE : View.GONE);
        screenBorder.setVisibility(screenBorderEnabled ? View.VISIBLE : View.GONE);
        applyDisplayMode();
        setContentView(res.root);
        
        if (frameLoopManager != null) {
            frameLoopManager.stop();
            frameLoopManager = new FrameLoopManager(this, screen);
            if (!menuPaused && !restarting) frameLoopManager.start();
        }
    }

    private void updateSystemUi(boolean landscape) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                android.view.Window window = getWindow();
                if (window == null) return;
                android.view.WindowInsetsController controller = window.getInsetsController();
                if (controller != null) {
                    if (landscape) {
                        controller.hide(android.view.WindowInsets.Type.statusBars());
                        controller.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                    } else {
                        controller.show(android.view.WindowInsets.Type.statusBars());
                    }
                }
            } else {
                // Legacy implementation
                android.view.Window window = getWindow();
                if (window == null) return;
                View decorView = window.getDecorView();
                if (decorView == null) return;
                int flags = decorView.getSystemUiVisibility();
                if (landscape) {
                    flags |= View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
                } else {
                    flags &= ~View.SYSTEM_UI_FLAG_FULLSCREEN;
                    flags &= ~View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
                }
                decorView.setSystemUiVisibility(flags);
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        rebuildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // Ensure System UI is correct (especially when starting in landscape)
        boolean isLandscape = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        updateSystemUi(isLandscape);

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
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        try {
            sInstantStateCache = NativeBridge.exportState();
        } catch (Throwable ignored) {}
    }

    @Override
    protected void onDestroy() {
        if (frameLoopManager != null) frameLoopManager.stop();
        NativeBridge.saveSram();
        exportPortableSramIfEnabled();
        stopAudioPlayback();
        releaseAllButtons();
        GameControllerOverlay.cleanup();
        ControllerLayoutEditor.reset();
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
        cheatManager = new CheatManager(this, portableSaveFolderUri, romSessionManager.ensureDirectory("states"), romBaseName);
        NativeBridge.setAudioMaxBufferedSamples(audioBacklogSamples);
        importPortableSramIfAvailable();
        cheatManager.applyToCore();
        if (pokemonManager != null) pokemonManager.detectVersion();
        
        if (sInstantStateCache != null) {
            NativeBridge.importState(sInstantStateCache);
            sInstantStateCache = null;
            updateInfo("Resumed from memory cache");
        } else {
            loadStartupStateIfRequested();
        }

        updateInfo("Running... speed " + emulationSpeedLabel() + " audio preset " + AppSettingsManager.audioPresetLabel(this, audioBacklogSamples) + "\n" + NativeBridge.getLastError());
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
    public void applyFrameHooks() {
        if (pokemonManager != null) {
            pokemonManager.applyLocks();
        }
    }

    @Override
    public void updateFrameInfo(String text) {
        updateInfo(text);
    }

    @Override
    public String audioPresetLabelForFrameLoop() {
        return getString(R.string.settings_audio_preset) + " " + AppSettingsManager.audioPresetLabel(this, audioBacklogSamples);
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
    public AppSettingsManager getSettingsManager() {
        return settingsManager;
    }

    @Override
    public CheatManager getCheatManager() {
        return cheatManager;
    }

    @Override
    public PokemonManager getPokemonManager() {
        return pokemonManager;
    }

    @Override
    public MemoryScanner getMemoryScanner() {
        return memoryScanner;
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
        
        // Auto-save to Slot 0 before exiting
        try {
            if (saveStateManager == null) saveStateManager = new SaveStateManager(this, portableSaveFolderUri, romSessionManager.ensureDirectory("states"), romBaseName);
            byte[] data = NativeBridge.exportState();
            Bitmap thumb = frameLoopManager.captureLastFrame();
            saveStateManager.write(data, SaveStateManager.AUTO_SAVE_SLOT, thumb);
        } catch (Throwable ignored) {}

        NativeBridge.saveSram();
        exportPortableSramIfEnabled();
        releaseAllButtons();
        finish();
    }

    @Override
    public void showStateSlotDialog(boolean save) {
        pauseEmulationForMenu();
        if (saveStateManager == null) saveStateManager = new SaveStateManager(this, portableSaveFolderUri, romSessionManager.ensureDirectory("states"), romBaseName);
        
        StateDialogHelper.show(this, save ? getString(R.string.save_state_title) : getString(R.string.load_state_title), saveStateManager, thumbnailCache, new StateDialogHelper.Callback() {
            @Override
            public void onSlotSelected(int slot) {
                if (save) confirmAndSaveState(slot);
                else loadStateNow(slot);
            }

            @Override
            public void onDismiss() {
                resumeEmulationFromMenu();
            }
        });
    }

    private void confirmAndSaveState(int slot) {
        pauseEmulationForMenu();
        if (saveStateManager.getModifiedTime(slot) <= 0) {
            saveStateNow(slot);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.overwrite_save_state_title))
                .setMessage(getString(R.string.overwrite_save_state_message_format, slot, saveStateManager.slotLabel(slot)))
                .setPositiveButton(getString(R.string.overwrite), (dialog, which) -> saveStateNow(slot))
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> resumeEmulationFromMenu())
                .setOnCancelListener(dialog -> resumeEmulationFromMenu())
                .show();
    }

    private void saveStateNow(int slot) {
        try {
            byte[] data = NativeBridge.exportState();
            Bitmap thumb = frameLoopManager.captureLastFrame();
            boolean ok = saveStateManager.write(data, slot, thumb);
            if (ok) {
                if (thumb != null) thumbnailCache.put(slot, thumb);
                showToast(getString(R.string.save_state_saved_format, slot));
            } else {
                showToast(getString(R.string.save_state_write_failed));
            }
        } catch (Throwable t) {
            showToast(getString(R.string.save_state_failed_format, safeMessage(t)));
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
                showToast(getString(R.string.slot_empty_format, slot));
                resumeEmulationFromMenu();
                return;
            }
            boolean ok = NativeBridge.importState(data);
            showToast(ok ? getString(R.string.load_state_loaded_format, slot) : getString(R.string.load_state_failed));
        } catch (Throwable t) {
            showToast(getString(R.string.load_state_failed_format, safeMessage(t)));
        }
        resumeEmulationFromMenu();
    }

    @Override
    public void showDisplaySettingsDialog() {
        pauseEmulationForMenu();
        String[] sections = {
                getString(R.string.settings_screen_scaling),
                getString(R.string.settings_color_mode),
                getString(R.string.settings_screen_border),
                getString(R.string.settings_show_debug_info)
        };
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.settings_display_settings))
                .setItems(sections, (dialog, which) -> {
                    if (which == 0) showScalingDialog();
                    else if (which == 1) showColorModeDialog();
                    else if (which == 2) toggleScreenBorder();
                    else if (which == 3) toggleDebugText();
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> resumeEmulationFromMenu())
                .setOnCancelListener(dialog -> resumeEmulationFromMenu())
                .show();
    }

    private void showScalingDialog() {
        String[] labels = {
                getString(R.string.display_auto_fit),
                getString(R.string.display_integer_scale),
                getString(R.string.display_stretch)
        };
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.settings_screen_scaling))
                .setSingleChoiceItems(labels, displayMode, (dialog, which) -> {
                    displayMode = which;
                    settingsManager.setDisplayMode(displayMode);
                    applyDisplayMode();
                    dialog.dismiss();
                    resumeEmulationFromMenu();
                })
                .show();
    }

    private void showColorModeDialog() {
        String[] labels = {
                getString(R.string.color_mode_standard),
                getString(R.string.color_mode_gba),
                getString(R.string.color_mode_grayscale)
        };
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.settings_color_mode))
                .setSingleChoiceItems(labels, settingsManager.getColorMode(), (dialog, which) -> {
                    settingsManager.setColorMode(which);
                    NativeBridge.setColorMode(which);
                    dialog.dismiss();
                    resumeEmulationFromMenu();
                })
                .show();
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

    @Override
    public void showAudioPresetDialog() {
        pauseEmulationForMenu();
        String[] labels = {
                getString(R.string.audio_dynamic),
                getString(R.string.audio_1024),
                getString(R.string.audio_2048),
                getString(R.string.audio_4096)
        };
        int[] values = {AppSettingsManager.AUDIO_DYNAMIC, AppSettingsManager.AUDIO_1024, AppSettingsManager.AUDIO_2048, AppSettingsManager.AUDIO_4096};
        int checked = 0;
        for (int i = 0; i < values.length; i++) if (values[i] == audioBacklogSamples) checked = i;
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.settings_audio_preset))
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    audioBacklogSamples = values[which];
                    settingsManager.setAudioPreset(audioBacklogSamples);
                    NativeBridge.setAudioMaxBufferedSamples(audioBacklogSamples);
                    updateInfo("audio preset " + AppSettingsManager.audioPresetLabel(this, audioBacklogSamples) + "\n" + NativeBridge.getLastError());
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
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
        if (displayMode == AppSettingsManager.SCALE_INTEGER) {
            int boxW = screenBox.getWidth();
            int boxH = screenBox.getHeight();

            // Fallback if layout hasn't happened yet
            if (boxW <= 0 || boxH <= 0) {
                boxW = getResources().getDisplayMetrics().widthPixels - dp(16);
                boxH = Math.max(dp(220), Math.min(Math.round(boxW * 2f / 3f), dp(360)));
            }

            int scaleW = boxW / 240;
            int scaleH = boxH / 160;
            int finalScale = Math.max(1, Math.min(scaleW, scaleH));

            screen.setAdjustViewBounds(false); // We set exact size
            screen.setScaleType(ImageView.ScaleType.FIT_CENTER);

            ViewGroup.LayoutParams lp = screen.getLayoutParams();
            lp.width = 240 * finalScale;
            lp.height = 160 * finalScale;
            screen.setLayoutParams(lp);
        } else {
            screen.setAdjustViewBounds(displayMode != AppSettingsManager.SCALE_STRETCH);
            screen.setScaleType(displayMode == AppSettingsManager.SCALE_STRETCH ? ImageView.ScaleType.FIT_XY : ImageView.ScaleType.FIT_CENTER);
            ViewGroup.LayoutParams lp = screen.getLayoutParams();
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            screen.setLayoutParams(lp);
        }
        updateInfo(getString(R.string.settings_screen_scaling) + ": " + AppSettingsManager.displayModeLabel(this, displayMode));
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

    private GradientDrawable makeRoundRect(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    @Override
    public int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    public static class BorderDrawable extends Drawable {
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
