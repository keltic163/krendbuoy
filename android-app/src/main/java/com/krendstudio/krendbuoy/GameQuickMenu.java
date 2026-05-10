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
        void showControllerSettingsDialog();
    }

    private GameQuickMenu() {
    }

    static void show(Activity activity, Host host) {
        host.pauseEmulationForMenu();
        host.releaseAllButtons();

        boolean isLandscape = activity.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        
        String[] items;
        if (isLandscape) {
            items = new String[]{
                    activity.getString(R.string.quick_menu_resume),
                    activity.getString(R.string.quick_menu_restart_game),
                    activity.getString(R.string.settings_edit_controller_layout),
                    activity.getString(R.string.quick_menu_return_main_menu)
            };
        } else {
            items = new String[]{
                    activity.getString(R.string.quick_menu_resume),
                    activity.getString(R.string.quick_menu_restart_game),
                    activity.getString(R.string.quick_menu_return_main_menu)
            };
        }

        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.quick_menu_title))
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        host.resumeEmulationFromMenu();
                        dialog.dismiss();
                    } else if (which == 1) {
                        host.restartGame();
                    } else if (isLandscape && which == 2) {
                        host.showControllerSettingsDialog();
                    } else if ((isLandscape && which == 3) || (!isLandscape && which == 2)) {
                        host.leaveGame();
                    }
                })
                .setOnCancelListener(dialog -> host.resumeEmulationFromMenu())
                .show();
    }
}
