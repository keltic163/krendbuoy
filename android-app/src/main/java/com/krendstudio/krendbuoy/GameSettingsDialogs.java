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
        String[] items = {"Controller Settings", "Display Settings", "Audio Preset"};
        new AlertDialog.Builder(activity)
                .setTitle("Settings")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) host.showControllerSettingsDialog();
                    else if (which == 1) host.showDisplaySettingsDialog();
                    else if (which == 2) host.showAudioPresetDialog();
                })
                .setNegativeButton("Cancel", (dialog, which) -> host.resumeEmulationFromMenu())
                .setOnCancelListener(dialog -> host.resumeEmulationFromMenu())
                .show();
    }

    static void showControllerSettings(Activity activity, Host host) {
        host.pauseEmulationForMenu();
        AppSettingsManager sm = host.getSettingsManager();
        boolean skinned = sm.isControllerSkinEnabled();
        int skinId = sm.getControllerSkinId();
        
        String[] items = {
                "Controller Skin: " + (skinned ? "ON" : "OFF"),
                "Select Skin: Style 0" + skinId,
                "Button Size: Default",
                "Button Opacity: Default"
        };
        new AlertDialog.Builder(activity)
                .setTitle("Controller Settings")
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
                .setNegativeButton("Cancel", (dialog, which) -> host.resumeEmulationFromMenu())
                .setOnCancelListener(dialog -> host.resumeEmulationFromMenu())
                .show();
    }

    private static void showSkinPicker(Activity activity, Host host) {
        String[] skins = {"Style 01 (Original)", "Style 02", "Style 03"};
        AppSettingsManager sm = host.getSettingsManager();
        new AlertDialog.Builder(activity)
                .setTitle("Select Skin Style")
                .setSingleChoiceItems(skins, sm.getControllerSkinId() - 1, (dialog, which) -> {
                    sm.setControllerSkinId(which + 1);
                    activity.recreate();
                })
                .show();
    }

    private static void showControllerPending(Activity activity) {
        new AlertDialog.Builder(activity)
                .setTitle("Controller Settings")
                .setMessage("Controller customization will be added in a later build.")
                .setPositiveButton("OK", null)
                .show();
    }
}
