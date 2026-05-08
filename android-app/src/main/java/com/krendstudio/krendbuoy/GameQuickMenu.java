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
    }

    private GameQuickMenu() {
    }

    static void show(Activity activity, Host host) {
        host.pauseEmulationForMenu();
        host.releaseAllButtons();

        // Removed "Display Settings" and "Audio Preset" as they are in global settings.
        // Moved "Return to Main Menu" to the last option.
        String[] items = {
                activity.getString(R.string.quick_menu_resume),
                activity.getString(R.string.quick_menu_restart_game),
                activity.getString(R.string.quick_menu_return_main_menu)
        };

        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.quick_menu_title))
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        host.resumeEmulationFromMenu();
                        dialog.dismiss();
                    } else if (which == 1) {
                        host.restartGame();
                    } else if (which == 2) {
                        host.leaveGame();
                    }
                })
                .setOnCancelListener(dialog -> host.resumeEmulationFromMenu())
                .show();
    }
}
