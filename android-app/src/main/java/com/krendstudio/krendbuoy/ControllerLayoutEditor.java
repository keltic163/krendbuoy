package com.krendstudio.krendbuoy;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.List;

/**
 * Handles edit-mode drag and save/reset/cancel controls for the in-game
 * controller overlay layout.
 */
final class ControllerLayoutEditor {
    interface Host {
        int dp(int value);
        AppSettingsManager getSettingsManager();
    }

    interface ButtonBinding {
        View getView();
        int getButton();
    }

    private static boolean sEditing = false;
    private static View sDraggingView = null;
    private static float sDragOffsetX = 0;
    private static float sDragOffsetY = 0;
    private static View sEditControlsOverlay = null;

    private ControllerLayoutEditor() {}

    static void reset() {
        sEditing = false;
        sDraggingView = null;
        sDragOffsetX = 0;
        sDragOffsetY = 0;
        sEditControlsOverlay = null;
    }

    static boolean isEditing() {
        return sEditing;
    }

    static void start(
            FrameLayout panel,
            View dpadView,
            List<? extends ButtonBinding> actionButtons,
            boolean useSkin,
            boolean landscape,
            Host host,
            Runnable switchToController,
            Runnable switchToSettings
    ) {
        sEditing = true;
        switchToController.run();
        dpadView.setBackgroundColor(0x66888888);

        for (ButtonBinding button : actionButtons) {
            View view = button.getView();
            view.setAlpha(1.0f);
            if (view.getBackground() instanceof GradientDrawable) {
                ((GradientDrawable) view.getBackground()).setColor(0xAA444444);
            }
        }

        LinearLayout controls = new LinearLayout(panel.getContext());
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.setBackgroundColor(0xCC000000);

        int buttonWidth = host.dp(80);
        int buttonHeight = host.dp(40);
        Activity activity = (Activity) panel.getContext();

        controls.addView(OverlayUiFactory.makeSystemButton(activity, "Save", () -> {
            saveLayout(panel, dpadView, actionButtons, landscape, host);
            stop(panel, dpadView, actionButtons, useSkin, switchToSettings);
        }), new LinearLayout.LayoutParams(buttonWidth, buttonHeight));
        controls.addView(new View(panel.getContext()), new LinearLayout.LayoutParams(host.dp(12), 1));
        controls.addView(OverlayUiFactory.makeSystemButton(activity, "Reset", () -> {
            host.getSettingsManager().resetAllButtonPos();
            activity.recreate();
        }), new LinearLayout.LayoutParams(buttonWidth, buttonHeight));
        controls.addView(new View(panel.getContext()), new LinearLayout.LayoutParams(host.dp(12), 1));
        controls.addView(OverlayUiFactory.makeSystemButton(activity, "Cancel", () -> stop(panel, dpadView, actionButtons, useSkin, switchToSettings)),
                new LinearLayout.LayoutParams(buttonWidth, buttonHeight));

        panel.addView(controls, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.dp(60), Gravity.BOTTOM));
        sEditControlsOverlay = controls;
    }

    static void handleTouch(MotionEvent event, View dpadView, List<? extends ButtonBinding> actionButtons) {
        int action = event.getActionMasked();
        float x = event.getX();
        float y = event.getY();

        if (action == MotionEvent.ACTION_DOWN) {
            sDraggingView = null;
            if (isInside(x, y, dpadView)) {
                sDraggingView = dpadView;
            } else {
                for (ButtonBinding button : actionButtons) {
                    View view = button.getView();
                    if (isInside(x, y, view)) {
                        sDraggingView = view;
                        break;
                    }
                }
            }

            if (sDraggingView != null) {
                sDragOffsetX = x - (sDraggingView.getLeft() + sDraggingView.getWidth() / 2f);
                sDragOffsetY = y - (sDraggingView.getTop() + sDraggingView.getHeight() / 2f);
                sDraggingView.setAlpha(0.5f);
            }
        } else if (action == MotionEvent.ACTION_MOVE && sDraggingView != null) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) sDraggingView.getLayoutParams();
            lp.leftMargin = Math.round(x - sDragOffsetX - sDraggingView.getWidth() / 2f);
            lp.topMargin = Math.round(y - sDragOffsetY - sDraggingView.getHeight() / 2f);
            sDraggingView.setLayoutParams(lp);
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (sDraggingView != null) {
                sDraggingView.setAlpha(1.0f);
                sDraggingView = null;
            }
        }
    }

    private static void stop(
            FrameLayout panel,
            View dpadView,
            List<? extends ButtonBinding> actionButtons,
            boolean useSkin,
            Runnable switchToSettings
    ) {
        sEditing = false;
        if (sEditControlsOverlay != null) {
            panel.removeView(sEditControlsOverlay);
            sEditControlsOverlay = null;
        }

        if (useSkin) {
            dpadView.setBackgroundColor(Color.TRANSPARENT);
            for (ButtonBinding button : actionButtons) {
                View view = button.getView();
                view.setAlpha(0.0f);
                if (view.getBackground() instanceof GradientDrawable) {
                    ((GradientDrawable) view.getBackground()).setColor(Color.WHITE);
                }
            }
        }

        switchToSettings.run();
    }

    private static void saveLayout(
            FrameLayout panel,
            View dpadView,
            List<? extends ButtonBinding> actionButtons,
            boolean landscape,
            Host host
    ) {
        float width = panel.getWidth();
        float height = panel.getHeight();
        if (width <= 0 || height <= 0) return;

        AppSettingsManager settings = host.getSettingsManager();
        settings.setButtonPos(NativeBridge.BUTTON_UP, landscape,
                (dpadView.getLeft() + dpadView.getWidth() / 2f) / width,
                (dpadView.getTop() + dpadView.getHeight() / 2f) / height);

        for (ButtonBinding button : actionButtons) {
            View view = button.getView();
            settings.setButtonPos(button.getButton(), landscape,
                    (view.getLeft() + view.getWidth() / 2f) / width,
                    (view.getTop() + view.getHeight() / 2f) / height);
        }
    }

    private static boolean isInside(float x, float y, View view) {
        float left = view.getLeft() + view.getTranslationX();
        float top = view.getTop() + view.getTranslationY();
        return x >= left && x <= left + view.getWidth() && y >= top && y <= top + view.getHeight();
    }
}
