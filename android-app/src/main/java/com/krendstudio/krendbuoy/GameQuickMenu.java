package com.krendstudio.krendbuoy;

import android.app.Activity;
import android.app.AlertDialog;

/**
 * In-game, game-specific quick menu helper.
 * This is intentionally separate from global settings. It should only contain
 * actions that affect the currently running game session.
 */
final class GameQuickMenu {
    interface Host {
        void pauseEmulationForMenu();
        void resumeEmulationFromMenu();
        void releaseAllButtons();
        void restartGame();
        void leaveGame();
        void showDisplaySettingsDialog();
        void showAudioPresetDialog();
    }

    private GameQuickMenu() {
    }

    static void show(Activity activity, Host host) {
        host.pauseEmulationForMenu();
        host.releaseAllButtons();

        String[] items = {
                "Resume",
                "Restart Game",
                "Return to Main Menu",
                "Display Settings",
                "Audio Preset",
                "Exit Game"
        };

        new AlertDialog.Builder(activity)
                .setTitle("KrendBuoy Menu")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        host.resumeEmulationFromMenu();
                        dialog.dismiss();
                    } else if (which == 1) {
                        host.restartGame();
                    } else if (which == 2) {
                        host.leaveGame();
                    } else if (which == 3) {
                        host.showDisplaySettingsDialog();
                    } else if (which == 4) {
                        host.showAudioPresetDialog();
                    } else if (which == 5) {
                        host.leaveGame();
                    }
                })
                .setOnCancelListener(dialog -> host.resumeEmulationFromMenu())
                .show();
    }
}
