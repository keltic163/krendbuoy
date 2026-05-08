package com.krendstudio.krendbuoy;

import android.app.Activity;
import android.app.AlertDialog;

/**
 * Shared settings dialogs used from the in-game screen.
 * MainActivity and GameActivity should read and write the same preferences,
 * while the in-game Activity also pauses and resumes the running game around dialogs.
 */
final class GameSettingsDialogs {
    interface Host {
        void pauseEmulationForMenu();
        void resumeEmulationFromMenu();
        void showDisplaySettingsDialog();
        void showAudioPresetDialog();
        void showControllerSettingsDialog();
        AppSettingsManager getSettingsManager();
    }

    private GameSettingsDialogs() {
    }

    static void showGlobalSettings(Activity activity, Host host) {
        host.pauseEmulationForMenu();
        String[] items = {
                activity.getString(R.string.settings_controller_settings),
                activity.getString(R.string.settings_display_settings),
                activity.getString(R.string.settings_audio_preset)
        };
        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.settings_title))
                .setItems(items, (dialog, which) -> {
                    if (which == 0) host.showControllerSettingsDialog();
                    else if (which == 1) host.showDisplaySettingsDialog();
                    else if (which == 2) host.showAudioPresetDialog();
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> host.resumeEmulationFromMenu())
                .setOnCancelListener(dialog -> host.resumeEmulationFromMenu())
                .show();
    }

    static void showControllerSettings(Activity activity, Host host) {
        host.pauseEmulationForMenu();
        AppSettingsManager sm = host.getSettingsManager();
        boolean skinned = sm.isControllerSkinEnabled();
        int skinId = sm.getControllerSkinId();
        
        String[] items = {
                activity.getString(R.string.settings_controller_skin_format, skinned ? activity.getString(R.string.common_on) : activity.getString(R.string.common_off)),
                activity.getString(R.string.settings_select_skin_format, skinId),
                activity.getString(R.string.settings_button_size_default),
                activity.getString(R.string.settings_button_opacity_default)
        };
        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.settings_controller_settings))
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        sm.setControllerSkinEnabled(!skinned);
                        activity.recreate();
                    } else if (which == 1) {
                        showSkinPicker(activity, host);
                    } else {
                        showControllerPending(activity);
                    }
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> host.resumeEmulationFromMenu())
                .setOnCancelListener(dialog -> host.resumeEmulationFromMenu())
                .show();
    }

    private static void showSkinPicker(Activity activity, Host host) {
        String[] skins = {
                activity.getString(R.string.skin_style_01),
                activity.getString(R.string.skin_style_02),
                activity.getString(R.string.skin_style_03)
        };
        AppSettingsManager sm = host.getSettingsManager();
        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.settings_select_skin_style))
                .setSingleChoiceItems(skins, sm.getControllerSkinId() - 1, (dialog, which) -> {
                    sm.setControllerSkinId(which + 1);
                    activity.recreate();
                })
                .show();
    }

    private static void showControllerPending(Activity activity) {
        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.settings_controller_settings))
                .setMessage(activity.getString(R.string.settings_controller_pending))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
