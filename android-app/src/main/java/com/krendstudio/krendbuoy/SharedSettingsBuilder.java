package com.krendstudio.krendbuoy;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Shared logic to build the settings UI for both MainActivity and GameActivity.
 * Ensures options remain consistent across the app.
 */
final class SharedSettingsBuilder {

    interface Host {
        AppSettingsManager getSettingsManager();
        void onSettingChanged(); // Optional callback to refresh UI if needed
        default void onEditLayout() {} // Callback for layout editing
    }

    static View buildSettingsView(Activity activity, Host host) {
        AppSettingsManager sm = host.getSettingsManager();
        
        ScrollView scroll = new ScrollView(activity);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(activity, 16), dp(activity, 16), dp(activity, 16), dp(activity, 16));
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(sectionTitle(activity, activity.getString(R.string.settings_language)));
        root.addView(makeSettingButton(activity, activity.getString(R.string.settings_language), () -> {
            String[] labels = {
                    activity.getString(R.string.language_system),
                    activity.getString(R.string.language_en),
                    activity.getString(R.string.language_zh_tw)
            };
            int[] values = {
                    AppSettingsManager.LANGUAGE_SYSTEM,
                    AppSettingsManager.LANGUAGE_EN,
                    AppSettingsManager.LANGUAGE_ZH_TW
            };
            int checked = 0;
            int current = sm.getLanguageMode();
            for (int i = 0; i < values.length; i++) if (values[i] == current) checked = i;
            new AlertDialog.Builder(activity)
                    .setTitle(activity.getString(R.string.settings_language))
                    .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                        sm.setLanguageMode(values[which]);
                        dialog.dismiss();
                        activity.recreate();
                    })
                    .setMessage(activity.getString(R.string.language_restart_note))
                    .show();
        }), blockParams(activity, 0, 8, 0, 24));

        // Display Settings Section
        root.addView(sectionTitle(activity, activity.getString(R.string.settings_display)));
        
        root.addView(makeSettingButton(activity, activity.getString(R.string.settings_screen_scaling), () -> {
            String[] labels = {
                    activity.getString(R.string.display_fit_screen),
                    activity.getString(R.string.display_original_ratio),
                    activity.getString(R.string.display_stretch),
                    activity.getString(R.string.display_pixel_perfect)
            };
            new AlertDialog.Builder(activity)
                    .setTitle(activity.getString(R.string.settings_screen_scaling))
                    .setSingleChoiceItems(labels, sm.getDisplayMode(), (dialog, which) -> {
                        sm.setDisplayMode(which);
                        host.onSettingChanged();
                        dialog.dismiss();
                    }).show();
        }), blockParams(activity, 0, 8, 0, 12));

        root.addView(makeSettingButton(activity, activity.getString(R.string.settings_screen_brightness), () -> {
            String[] labels = {
                    activity.getString(R.string.brightness_brightest),
                    activity.getString(R.string.brightness_bright),
                    activity.getString(R.string.brightness_medium),
                    activity.getString(R.string.brightness_dim)
            };
            new AlertDialog.Builder(activity)
                    .setTitle(activity.getString(R.string.settings_screen_brightness))
                    .setSingleChoiceItems(labels, sm.getBgDimmingLevel(), (dialog, which) -> {
                        sm.setBgDimmingLevel(which);
                        host.onSettingChanged();
                        dialog.dismiss();
                    }).show();
        }), blockParams(activity, 0, 0, 0, 12));

        root.addView(makeSettingToggle(activity, activity.getString(R.string.settings_color_correction), sm.isColorCorrectionEnabled(), enabled -> {
            sm.setColorCorrectionEnabled(enabled);
            host.onSettingChanged();
        }), blockParams(activity, 0, 0, 0, 12));
        
        root.addView(makeSettingToggle(activity, activity.getString(R.string.settings_screen_border), sm.isScreenBorderEnabled(), enabled -> {
            sm.setScreenBorderEnabled(enabled);
            host.onSettingChanged();
        }), blockParams(activity, 0, 0, 0, 12));

        root.addView(makeSettingToggle(activity, activity.getString(R.string.settings_show_debug_info), sm.isDebugTextVisible(), enabled -> {
            sm.setDebugTextVisible(enabled);
            host.onSettingChanged();
        }), blockParams(activity, 0, 0, 0, 24));

        // Controller Settings Section
        root.addView(sectionTitle(activity, activity.getString(R.string.settings_controller)));
        
        root.addView(makeSettingToggle(activity, activity.getString(R.string.settings_use_controller_skin), sm.isControllerSkinEnabled(), enabled -> {
            sm.setControllerSkinEnabled(enabled);
            host.onSettingChanged();
        }), blockParams(activity, 0, 8, 0, 12));

        root.addView(makeSettingButton(activity, activity.getString(R.string.settings_select_skin_style), () -> {
            String[] skins = {
                    activity.getString(R.string.skin_style_01),
                    activity.getString(R.string.skin_style_02),
                    activity.getString(R.string.skin_style_03)
            };
            new AlertDialog.Builder(activity)
                    .setTitle(activity.getString(R.string.settings_select_skin_style))
                    .setSingleChoiceItems(skins, sm.getControllerSkinId() - 1, (dialog, which) -> {
                        sm.setControllerSkinId(which + 1);
                        host.onSettingChanged();
                        dialog.dismiss();
                    }).show();
        }), blockParams(activity, 0, 0, 0, 24));

        // Layout Editor - Only show if the host provides a valid edit action
        if (activity instanceof GameActivityV2) {
            root.addView(sectionTitle(activity, activity.getString(R.string.settings_layout)));
            root.addView(makeSettingButton(activity, activity.getString(R.string.settings_edit_controller_layout), () -> {
                host.onEditLayout();
            }), blockParams(activity, 0, 8, 0, 24));
        }

        // Audio Settings Section
        root.addView(sectionTitle(activity, activity.getString(R.string.settings_audio_preset)));
        RadioGroup audioGroup = new RadioGroup(activity);
        audioGroup.setOrientation(RadioGroup.VERTICAL);
        audioGroup.setBackground(makeRoundRect(Color.rgb(28, 39, 58), dp(activity, 10)));
        audioGroup.setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 12), dp(activity, 8));
        
        addAudioOption(activity, audioGroup, 100000, activity.getString(R.string.audio_dynamic));
        addAudioOption(activity, audioGroup, 1024, activity.getString(R.string.audio_1024));
        addAudioOption(activity, audioGroup, 2048, activity.getString(R.string.audio_2048));
        addAudioOption(activity, audioGroup, 4096, activity.getString(R.string.audio_4096));
        
        int currentAudio = sm.getAudioPreset();
        audioGroup.check(currentAudio == AppSettingsManager.AUDIO_DYNAMIC ? 100000 : currentAudio);
        audioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int val = checkedId == 100000 ? AppSettingsManager.AUDIO_DYNAMIC : checkedId;
            sm.setAudioPreset(val);
            host.onSettingChanged();
        });
        root.addView(audioGroup, blockParams(activity, 0, 8, 0, 24));

        return scroll;
    }

    private static View makeSettingButton(Activity activity, String title, Runnable action) {
        Button btn = new Button(activity);
        btn.setText(title);
        btn.setAllCaps(false);
        btn.setOnClickListener(v -> action.run());
        return btn;
    }

    private static View makeSettingToggle(Activity activity, String title, boolean initial, java.util.function.Consumer<Boolean> onToggle) {
        CheckBox cb = new CheckBox(activity);
        cb.setText(title);
        cb.setChecked(initial);
        cb.setTextColor(Color.WHITE);
        cb.setOnCheckedChangeListener((v, checked) -> onToggle.accept(checked));
        return cb;
    }

    private static void addAudioOption(Activity activity, RadioGroup group, int id, String label) {
        RadioButton button = new RadioButton(activity);
        button.setId(id);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15f);
        group.addView(button);
    }

    private static TextView sectionTitle(Activity activity, String text) {
        TextView title = new TextView(activity);
        title.setText(text);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(18f);
        return title;
    }

    private static LinearLayout.LayoutParams blockParams(Activity activity, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(activity, left), dp(activity, top), dp(activity, right), dp(activity, bottom));
        return params;
    }

    private static GradientDrawable makeRoundRect(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
