package com.krendstudio.krendbuoy;

import android.app.AlertDialog;

/**
 * Shared settings dialogs used from the in-game screen.
 * MainActivity and GameActivity should read and write the same preferences,
 * while GameActivity also pauses and resumes the running game around dialogs.
 */
final class GameSettingsDialogs {
    interface Host {
        void pauseEmulationForMenu();
        void resumeEmulationFromMenu();
        void showDisplaySettingsDialog();
        void showAudioPresetDialog();
        void showControllerSettingsDialog();
    }

    private GameSettingsDialogs() {
    }

    static void showGlobalSettings(GameActivity activity, Host host) {
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

    static void showControllerSettings(GameActivity activity, Host host) {
        host.pauseEmulationForMenu();
        String[] items = {
                "Controller Layout: GBA SP Style",
                "Button Size: Default",
                "Button Opacity: Default"
        };
        new AlertDialog.Builder(activity)
                .setTitle("Controller Settings")
                .setItems(items, (dialog, which) -> showControllerPending(activity))
                .setNegativeButton("Cancel", (dialog, which) -> host.resumeEmulationFromMenu())
                .setOnCancelListener(dialog -> host.resumeEmulationFromMenu())
                .show();
    }

    private static void showControllerPending(GameActivity activity) {
        new AlertDialog.Builder(activity)
                .setTitle("Controller Settings")
                .setMessage("Controller customization will be added in a later build.")
                .setPositiveButton("OK", null)
                .show();
    }
}
