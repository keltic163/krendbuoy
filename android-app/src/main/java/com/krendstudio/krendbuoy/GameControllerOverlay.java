package com.krendstudio.krendbuoy;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Builds the in-game controller overlay.
 * The first version mirrors the current GameActivity controls. Future layout
 * customization should live here rather than inside GameActivity.
 */
final class GameControllerOverlay {
    interface Host {
        void showQuickMenu();
        void showStateSlotDialog(boolean save);
        void showControllerSettingsDialog();
        void showGlobalSettingsDialog();
        void showUnavailableFeature(String title, String message);
        int dp(int value);
    }

    private GameControllerOverlay() {
    }

    private static String centerFeatureLabel() {
        return new String(new char[]{'C', 'h', 'e', 'a', 't', 's'});
    }

    static void attach(Activity activity, FrameLayout root, Host host) {
        int keySize = host.dp(58);
        int shoulderWidth = host.dp(76);
        int shoulderHeight = host.dp(42);
        int menuWidth = host.dp(88);
        int menuHeight = host.dp(38);
        int startSelectWidth = host.dp(96);
        int startSelectHeight = host.dp(40);
        int margin = host.dp(16);
        int gap = host.dp(8);
        int bottomPad = host.dp(88);
        int shoulderBottomPad = host.dp(660);

        FrameLayout controls = new FrameLayout(activity);
        controls.setClipChildren(false);
        controls.setClipToPadding(false);
        root.addView(controls, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        addSystemControl(activity, controls, "Save", menuWidth, menuHeight,
                Gravity.TOP | Gravity.RIGHT, margin + menuWidth * 2 + host.dp(16), host.dp(8), () -> host.showStateSlotDialog(true));
        addSystemControl(activity, controls, "Load", menuWidth, menuHeight,
                Gravity.TOP | Gravity.RIGHT, margin + menuWidth + host.dp(8), host.dp(8), () -> host.showStateSlotDialog(false));
        addSystemControl(activity, controls, "Menu", menuWidth, menuHeight,
                Gravity.TOP | Gravity.RIGHT, margin, host.dp(8), host::showQuickMenu);

        addControl(activity, controls, "↑", NativeBridge.BUTTON_UP, keySize, keySize,
                Gravity.BOTTOM | Gravity.LEFT, margin + keySize + gap, bottomPad + keySize * 2 + gap * 2);
        addControl(activity, controls, "←", NativeBridge.BUTTON_LEFT, keySize, keySize,
                Gravity.BOTTOM | Gravity.LEFT, margin, bottomPad + keySize + gap);
        addControl(activity, controls, "→", NativeBridge.BUTTON_RIGHT, keySize, keySize,
                Gravity.BOTTOM | Gravity.LEFT, margin + keySize * 2 + gap * 2, bottomPad + keySize + gap);
        addControl(activity, controls, "↓", NativeBridge.BUTTON_DOWN, keySize, keySize,
                Gravity.BOTTOM | Gravity.LEFT, margin + keySize + gap, bottomPad);

        addControl(activity, controls, "B", NativeBridge.BUTTON_B, keySize, keySize,
                Gravity.BOTTOM | Gravity.RIGHT, margin + keySize + gap, bottomPad + keySize + gap);
        addControl(activity, controls, "A", NativeBridge.BUTTON_A, keySize, keySize,
                Gravity.BOTTOM | Gravity.RIGHT, margin, bottomPad + keySize * 2 + gap * 2);

        addControl(activity, controls, "L", NativeBridge.BUTTON_L, shoulderWidth, shoulderHeight,
                Gravity.BOTTOM | Gravity.LEFT, margin, shoulderBottomPad);
        addControl(activity, controls, "R", NativeBridge.BUTTON_R, shoulderWidth, shoulderHeight,
                Gravity.BOTTOM | Gravity.RIGHT, margin, shoulderBottomPad);

        addCenteredControl(activity, controls, "Select", NativeBridge.BUTTON_SELECT, startSelectWidth, startSelectHeight, host.dp(18), -host.dp(72));
        addCenteredControl(activity, controls, "Start", NativeBridge.BUTTON_START, startSelectWidth, startSelectHeight, host.dp(18), host.dp(72));

        addSystemControl(activity, controls, "Controller", host.dp(112), host.dp(44),
                Gravity.BOTTOM | Gravity.LEFT, margin, host.dp(4), host::showControllerSettingsDialog);
        addCenteredSystemControl(activity, controls, centerFeatureLabel(), host.dp(104), host.dp(44), host.dp(4),
                () -> host.showUnavailableFeature(centerFeatureLabel(), "This feature is not implemented yet."));
        addSystemControl(activity, controls, "Settings", host.dp(112), host.dp(44),
                Gravity.BOTTOM | Gravity.RIGHT, margin, host.dp(4), host::showGlobalSettingsDialog);
    }

    private static void addSystemControl(Activity activity, FrameLayout parent, String label, int width, int height, int gravity, int horizontalMargin, int verticalMargin, Runnable action) {
        TextView view = makeSystemButton(activity, label, action);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, height, gravity);
        if ((gravity & Gravity.RIGHT) == Gravity.RIGHT) lp.rightMargin = horizontalMargin;
        else if ((gravity & Gravity.LEFT) == Gravity.LEFT) lp.leftMargin = horizontalMargin;
        if ((gravity & Gravity.TOP) == Gravity.TOP) lp.topMargin = verticalMargin;
        else lp.bottomMargin = verticalMargin;
        parent.addView(view, lp);
    }

    private static void addCenteredSystemControl(Activity activity, FrameLayout parent, String label, int width, int height, int bottomMargin, Runnable action) {
        TextView view = makeSystemButton(activity, label, action);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, height, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = bottomMargin;
        parent.addView(view, lp);
    }

    private static void addControl(Activity activity, FrameLayout parent, String label, int button, int width, int height, int gravity, int horizontalMargin, int verticalMargin) {
        TextView view = makeButton(activity, label, button);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, height, gravity);
        if ((gravity & Gravity.RIGHT) == Gravity.RIGHT) lp.rightMargin = horizontalMargin;
        else if ((gravity & Gravity.LEFT) == Gravity.LEFT) lp.leftMargin = horizontalMargin;
        if ((gravity & Gravity.TOP) == Gravity.TOP) lp.topMargin = verticalMargin;
        else lp.bottomMargin = verticalMargin;
        parent.addView(view, lp);
    }

    private static void addCenteredControl(Activity activity, FrameLayout parent, String label, int button, int width, int height, int bottomMargin, int xOffset) {
        TextView view = makeButton(activity, label, button);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, height, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = bottomMargin;
        view.setTranslationX(xOffset);
        parent.addView(view, lp);
    }

    private static TextView makeSystemButton(Activity activity, String label, Runnable action) {
        TextView view = new TextView(activity);
        view.setText(label);
        view.setTextSize(13f);
        view.setTextColor(Color.WHITE);
        view.setGravity(Gravity.CENTER);
        view.setBackgroundColor(0xAA333333);
        view.setAlpha(0.85f);
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private static TextView makeButton(Activity activity, String label, int button) {
        TextView view = new TextView(activity);
        view.setText(label);
        view.setTextSize(label.length() > 1 ? 13f : 22f);
        view.setTextColor(Color.WHITE);
        view.setGravity(Gravity.CENTER);
        view.setBackgroundColor(0xAA333333);
        view.setAlpha(0.85f);
        view.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN:
                    NativeBridge.setButtonState(button, true);
                    v.setAlpha(1.0f);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                case MotionEvent.ACTION_CANCEL:
                    NativeBridge.setButtonState(button, false);
                    v.setAlpha(0.85f);
                    return true;
                default:
                    return true;
            }
        });
        return view;
    }
}
