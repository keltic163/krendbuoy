package com.krendstudio.krendbuoy;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

final class GameControllerOverlay {
    interface Host {
        void showQuickMenu();
        void showStateSlotDialog(boolean save);
        void showControllerSettingsDialog();
        void showGlobalSettingsDialog();
        void showUnavailableFeature(String title, String message);
        int dp(int value);
    }

    private static final class VirtualButton {
        final View view;
        final int button;
        boolean pressed;

        VirtualButton(View view, int button) {
            this.view = view;
            this.button = button;
        }
    }

    private GameControllerOverlay() {
    }

    private static String centerFeatureLabel() {
        return "Cheats";
    }

    static void attach(Activity activity, FrameLayout root, Host host) {
        int keySize = host.dp(62);
        int shoulderWidth = host.dp(82);
        int shoulderHeight = host.dp(42);
        int menuWidth = host.dp(88);
        int menuHeight = host.dp(38);
        int startSelectWidth = host.dp(70);
        int startSelectHeight = host.dp(32);
        int margin = host.dp(20);
        int gap = host.dp(10);

        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels - host.dp(16);
        int screenHeight = Math.round(screenWidth * 2f / 3f);
        screenHeight = Math.max(host.dp(220), Math.min(screenHeight, host.dp(360)));
        int panelTop = host.dp(62) + screenHeight;

        addSystemControl(activity, root, "Save", menuWidth, menuHeight,
                Gravity.TOP | Gravity.RIGHT, margin + menuWidth * 2 + host.dp(16), host.dp(8), () -> host.showStateSlotDialog(true));
        addSystemControl(activity, root, "Load", menuWidth, menuHeight,
                Gravity.TOP | Gravity.RIGHT, margin + menuWidth + host.dp(8), host.dp(8), () -> host.showStateSlotDialog(false));
        addSystemControl(activity, root, "Menu", menuWidth, menuHeight,
                Gravity.TOP | Gravity.RIGHT, margin, host.dp(8), host::showQuickMenu);

        FrameLayout panel = new FrameLayout(activity);
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        panel.setBackgroundColor(Color.rgb(20, 24, 28));
        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        panelLp.topMargin = panelTop;
        root.addView(panel, panelLp);

        List<VirtualButton> virtualButtons = new ArrayList<>();

        addControl(activity, panel, virtualButtons, "L", NativeBridge.BUTTON_L, shoulderWidth, shoulderHeight,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL, host.dp(0), host.dp(30), -host.dp(58));
        addControl(activity, panel, virtualButtons, "R", NativeBridge.BUTTON_R, shoulderWidth, shoulderHeight,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL, host.dp(0), host.dp(30), host.dp(58));

        int dpadBottom = host.dp(108);
        addControl(activity, panel, virtualButtons, "↑", NativeBridge.BUTTON_UP, keySize, keySize,
                Gravity.BOTTOM | Gravity.LEFT, margin + keySize + gap, dpadBottom + keySize + gap, 0);
        addControl(activity, panel, virtualButtons, "←", NativeBridge.BUTTON_LEFT, keySize, keySize,
                Gravity.BOTTOM | Gravity.LEFT, margin, dpadBottom, 0);
        addControl(activity, panel, virtualButtons, "→", NativeBridge.BUTTON_RIGHT, keySize, keySize,
                Gravity.BOTTOM | Gravity.LEFT, margin + keySize * 2 + gap * 2, dpadBottom, 0);
        addControl(activity, panel, virtualButtons, "↓", NativeBridge.BUTTON_DOWN, keySize, keySize,
                Gravity.BOTTOM | Gravity.LEFT, margin + keySize + gap, dpadBottom - keySize - gap, 0);

        addControl(activity, panel, virtualButtons, "B", NativeBridge.BUTTON_B, keySize, keySize,
                Gravity.BOTTOM | Gravity.RIGHT, margin + keySize + gap + host.dp(28), dpadBottom - host.dp(2), 0);
        addControl(activity, panel, virtualButtons, "A", NativeBridge.BUTTON_A, keySize, keySize,
                Gravity.BOTTOM | Gravity.RIGHT, margin, dpadBottom + keySize - host.dp(4), 0);

        addControl(activity, panel, virtualButtons, "Select", NativeBridge.BUTTON_SELECT, startSelectWidth, startSelectHeight,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, host.dp(0), host.dp(68), -host.dp(46));
        addControl(activity, panel, virtualButtons, "Start", NativeBridge.BUTTON_START, startSelectWidth, startSelectHeight,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, host.dp(0), host.dp(68), host.dp(46));

        addSystemControl(activity, panel, "Controller", host.dp(112), host.dp(44),
                Gravity.BOTTOM | Gravity.LEFT, margin, host.dp(4), host::showControllerSettingsDialog);
        addCenteredSystemControl(activity, panel, centerFeatureLabel(), host.dp(104), host.dp(44), host.dp(4),
                () -> host.showUnavailableFeature(centerFeatureLabel(), "This feature is not implemented yet."));
        addSystemControl(activity, panel, "Settings", host.dp(112), host.dp(44),
                Gravity.BOTTOM | Gravity.RIGHT, margin, host.dp(4), host::showGlobalSettingsDialog);

        panel.setOnTouchListener((view, event) -> {
            updateVirtualButtons(virtualButtons, event);
            return true;
        });
    }

    private static void updateVirtualButtons(List<VirtualButton> buttons, MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            setAllReleased(buttons);
            return;
        }

        int actionIndex = event.getActionIndex();
        boolean pointerUp = action == MotionEvent.ACTION_POINTER_UP;

        for (VirtualButton button : buttons) {
            boolean insideAnyPointer = false;
            for (int i = 0; i < event.getPointerCount(); i++) {
                if (pointerUp && i == actionIndex) continue;
                if (isInside(event.getX(i), event.getY(i), button.view)) {
                    insideAnyPointer = true;
                    break;
                }
            }
            setPressed(button, insideAnyPointer);
        }
    }

    private static boolean isInside(float x, float y, View view) {
        float left = view.getLeft() + view.getTranslationX();
        float top = view.getTop() + view.getTranslationY();
        float right = left + view.getWidth();
        float bottom = top + view.getHeight();
        return x >= left && x <= right && y >= top && y <= bottom;
    }

    private static void setAllReleased(List<VirtualButton> buttons) {
        for (VirtualButton button : buttons) setPressed(button, false);
    }

    private static void setPressed(VirtualButton button, boolean pressed) {
        if (button.pressed == pressed) return;
        button.pressed = pressed;
        NativeBridge.setButtonState(button.button, pressed);
        button.view.setAlpha(pressed ? 1.0f : 0.85f);
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

    private static void addControl(Activity activity, FrameLayout parent, List<VirtualButton> buttons, String label, int button, int width, int height, int gravity, int horizontalMargin, int verticalMargin, int xOffset) {
        TextView view = makeButton(activity, label);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, height, gravity);
        if ((gravity & Gravity.RIGHT) == Gravity.RIGHT) lp.rightMargin = horizontalMargin;
        else if ((gravity & Gravity.LEFT) == Gravity.LEFT) lp.leftMargin = horizontalMargin;
        if ((gravity & Gravity.TOP) == Gravity.TOP) lp.topMargin = verticalMargin;
        else lp.bottomMargin = verticalMargin;
        view.setTranslationX(xOffset);
        parent.addView(view, lp);
        buttons.add(new VirtualButton(view, button));
    }

    private static TextView makeSystemButton(Activity activity, String label, Runnable action) {
        TextView view = new TextView(activity);
        view.setText(label);
        view.setTextSize(13f);
        view.setTextColor(Color.WHITE);
        view.setGravity(Gravity.CENTER);
        view.setBackgroundColor(0xAA333333);
        view.setAlpha(0.9f);
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private static TextView makeButton(Activity activity, String label) {
        TextView view = new TextView(activity);
        view.setText(label);
        view.setTextSize(label.length() > 1 ? 12f : 22f);
        view.setTextColor(Color.WHITE);
        view.setGravity(Gravity.CENTER);
        view.setBackgroundColor(0xAA333333);
        view.setAlpha(0.85f);
        return view;
    }
}
