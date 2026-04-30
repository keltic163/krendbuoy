package com.krendstudio.krendbuoy;

import android.content.Context;
import android.content.SharedPreferences;

final class AppSettingsManager {
    static final String PREFS = "krendbuoy_prefs";

    static final int AUDIO_DYNAMIC = -1;
    static final int AUDIO_1024 = 1024;
    static final int AUDIO_2048 = 2048;
    static final int AUDIO_4096 = 4096;

    static final int DISPLAY_FIT = 0;
    static final int DISPLAY_ORIGINAL_RATIO = 1;
    static final int DISPLAY_STRETCH = 2;

    static final int THEME_DEFAULT = 0;
    static final int LANGUAGE_SYSTEM = 0;

    private static final String KEY_AUDIO_PRESET = "audio_preset";
    private static final String KEY_DISPLAY_MODE = "display_mode";
    private static final String KEY_DEBUG_TEXT_VISIBLE = "debug_text_visible";
    private static final String KEY_THEME = "interface_theme";
    private static final String KEY_LANGUAGE = "language_mode";
    private static final String KEY_CONTROLLER_LAYOUT = "controller_layout";
    private static final String KEY_BUTTON_SIZE = "button_size";
    private static final String KEY_BUTTON_OPACITY = "button_opacity";

    private final SharedPreferences prefs;

    AppSettingsManager(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    int getAudioPreset() {
        return normalizeAudioPreset(prefs.getInt(KEY_AUDIO_PRESET, AUDIO_DYNAMIC));
    }

    void setAudioPreset(int value) {
        prefs.edit().putInt(KEY_AUDIO_PRESET, normalizeAudioPreset(value)).apply();
    }

    int getDisplayMode() {
        int value = prefs.getInt(KEY_DISPLAY_MODE, DISPLAY_FIT);
        return value == DISPLAY_ORIGINAL_RATIO || value == DISPLAY_STRETCH ? value : DISPLAY_FIT;
    }

    void setDisplayMode(int value) {
        prefs.edit().putInt(KEY_DISPLAY_MODE, value == DISPLAY_ORIGINAL_RATIO || value == DISPLAY_STRETCH ? value : DISPLAY_FIT).apply();
    }

    boolean isDebugTextVisible() {
        return prefs.getBoolean(KEY_DEBUG_TEXT_VISIBLE, false);
    }

    void setDebugTextVisible(boolean visible) {
        prefs.edit().putBoolean(KEY_DEBUG_TEXT_VISIBLE, visible).apply();
    }

    int getTheme() {
        return prefs.getInt(KEY_THEME, THEME_DEFAULT);
    }

    void setTheme(int value) {
        prefs.edit().putInt(KEY_THEME, value).apply();
    }

    int getLanguageMode() {
        return prefs.getInt(KEY_LANGUAGE, LANGUAGE_SYSTEM);
    }

    void setLanguageMode(int value) {
        prefs.edit().putInt(KEY_LANGUAGE, value).apply();
    }

    int getControllerLayout() {
        return prefs.getInt(KEY_CONTROLLER_LAYOUT, 0);
    }

    void setControllerLayout(int value) {
        prefs.edit().putInt(KEY_CONTROLLER_LAYOUT, value).apply();
    }

    int getButtonSize() {
        return prefs.getInt(KEY_BUTTON_SIZE, 0);
    }

    void setButtonSize(int value) {
        prefs.edit().putInt(KEY_BUTTON_SIZE, value).apply();
    }

    int getButtonOpacity() {
        return prefs.getInt(KEY_BUTTON_OPACITY, 0);
    }

    void setButtonOpacity(int value) {
        prefs.edit().putInt(KEY_BUTTON_OPACITY, value).apply();
    }

    static int normalizeAudioPreset(int value) {
        return value == AUDIO_1024 || value == AUDIO_2048 || value == AUDIO_4096 ? value : AUDIO_DYNAMIC;
    }

    static String audioPresetLabel(int value) {
        value = normalizeAudioPreset(value);
        if (value == AUDIO_1024) return "1024";
        if (value == AUDIO_2048) return "2048";
        if (value == AUDIO_4096) return "4096";
        return "Dynamic";
    }

    static String displayModeLabel(int value) {
        if (value == DISPLAY_ORIGINAL_RATIO) return "Original Ratio";
        if (value == DISPLAY_STRETCH) return "Stretch";
        return "Fit Screen";
    }

    static String themeLabel(int value) {
        return "Default";
    }

    static String languageLabel(int value) {
        return "System Default";
    }

    static String controllerLayoutLabel(int value) {
        return "Default";
    }

    static String buttonSizeLabel(int value) {
        return "Default";
    }

    static String buttonOpacityLabel(int value) {
        return "Default";
    }
}
