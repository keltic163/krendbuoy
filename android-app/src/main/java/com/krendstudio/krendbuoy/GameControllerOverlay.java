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

    private static final class DpadState {
        boolean up;
        boolean down;
        boolean left;
        boolean right;
    }

    private GameControllerOverlay() {
    }

    private static String centerFeatureLabel() {
        return "Cheats";
    }

    static void attach(Activity activity, FrameLayout root, Host host) {
        int menuWidth = host.dp(88);
        int menuHeight = host.dp(38);
        int margin = host.dp(20);

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

        List<VirtualButton> actionButtons = new ArrayList<>();
        DpadState dpadState = new DpadState();

        panel.post(() -> buildMeasuredPanel(activity, panel, host, actionButtons, dpadState));
    }

    private static void buildMeasuredPanel(Activity activity, FrameLayout panel, Host host, List<VirtualButton> actionButtons, DpadState dpadState) {
        int w = panel.getWidth();
        int h = panel.getHeight();
        if (w <= 0 || h <= 0) return;

        int unit = Math.max(host.dp(52), Math.min(host.dp(72), Math.round(w * 0.115f)));
        int dpadSize = unit * 3;
        int actionSize = Math.max(host.dp(58), Math.min(host.dp(76), Math.round(w * 0.125f)));
        int shoulderWidth = Math.max(host.dp(78), Math.min(host.dp(104), Math.round(w * 0.18f)));
        int shoulderHeight = host.dp(42);
        int startSelectWidth = Math.max(host.dp(62), Math.min(host.dp(84), Math.round(w * 0.14f)));
        int startSelectHeight = host.dp(32);
        int bottomButtonWidth = Math.max(host.dp(104), Math.min(host.dp(136), Math.round(w * 0.26f)));
        int bottomButtonHeight = host.dp(44);

        TextView dpadPad = makeButton(activity, "");
        dpadPad.setText("↑\n←  →\n↓");
        dpadPad.setTextSize(24f);
        dpadPad.setBackgroundColor(0x88333333);
        placeByCenter(panel, dpadPad, dpadSize, dpadSize, w * 0.27f, h * 0.55f);

        addActionButton(activity, panel, actionButtons, "B", NativeBridge.BUTTON_B, actionSize, w * 0.67f, h * 0.62f);
        addActionButton(activity, panel, actionButtons, "A", NativeBridge.BUTTON_A, actionSize, w * 0.82f, h * 0.48f);

        addActionButton(activity, panel, actionButtons, "L", NativeBridge.BUTTON_L, shoulderWidth, shoulderHeight, w * 0.42f, h * 0.17f);
        addActionButton(activity, panel, actionButtons, "R", NativeBridge.BUTTON_R, shoulderWidth, shoulderHeight, w * 0.58f, h * 0.17f);

        addActionButton(activity, panel, actionButtons, "Select", NativeBridge.BUTTON_SELECT, startSelectWidth, startSelectHeight, w * 0.43f, h * 0.79f);
        addActionButton(activity, panel, actionButtons, "Start", NativeBridge.BUTTON_START, startSelectWidth, startSelectHeight, w * 0.57f, h * 0.79f);

        addSystemButtonByCenter(activity, panel, "Controller", bottomButtonWidth, bottomButtonHeight, w * 0.20f, h - bottomButtonHeight / 2f - host.dp(6), host::showControllerSettingsDialog);
        addSystemButtonByCenter(activity, panel, centerFeatureLabel(), bottomButtonWidth, bottomButtonHeight, w * 0.50f, h - bottomButtonHeight / 2f - host.dp(6),
                () -> host.showUnavailableFeature(centerFeatureLabel(), "This feature is not implemented yet."));
        addSystemButtonByCenter(activity, panel, "Settings", bottomButtonWidth, bottomButtonHeight, w * 0.80f, h - bottomButtonHeight / 2f - host.dp(6), host::showGlobalSettingsDialog);

        panel.setOnTouchListener((view, event) -> {
            updateDpad(dpadPad, dpadState, event);
            updateVirtualButtons(actionButtons, event);
            return true;
        });
    }

    private static void updateDpad(View dpad, DpadState state, MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            setDpadState(state, false, false, false, false);
            dpad.setAlpha(0.85f);
            return;
        }

        int actionIndex = event.getActionIndex();
        boolean pointerUp = action == MotionEvent.ACTION_POINTER_UP;
        boolean found = false;
        float chosenX = 0;
        float chosenY = 0;

        for (int i = 0; i < event.getPointerCount(); i++) {
            if (pointerUp && i == actionIndex) continue;
            if (isInside(event.getX(i), event.getY(i), dpad)) {
                chosenX = event.getX(i);
                chosenY = event.getY(i);
                found = true;
                break;
            }
        }

        if (!found) {
            setDpadState(state, false, false, false, false);
            dpad.setAlpha(0.85f);
            return;
        }

        float left = dpad.getLeft() + dpad.getTranslationX();
        float top = dpad.getTop() + dpad.getTranslationY();
        float cx = left + dpad.getWidth() / 2f;
        float cy = top + dpad.getHeight() / 2f;
        float dx = chosenX - cx;
        float dy = chosenY - cy;
        float deadZone = Math.min(dpad.getWidth(), dpad.getHeight()) * 0.16f;

        if (Math.hypot(dx, dy) < deadZone) {
            setDpadState(state, false, false, false, false);
            dpad.setAlpha(0.85f);
            return;
        }

        double degrees = Math.toDegrees(Math.atan2(dy, dx));
        boolean right = degrees >= -67.5 && degrees <= 67.5;
        boolean down = degrees >= 22.5 && degrees <= 157.5;
        boolean left = degrees >= 112.5 || degrees <= -112.5;
        boolean up = degrees >= -157.5 && degrees <= -22.5;
        setDpadState(state, up, down, left, right);
        dpad.setAlpha(1.0f);
    }

    private static void setDpadState(DpadState state, boolean up, boolean down, boolean left, boolean right) {
        if (state.up != up) {
            state.up = up;
            NativeBridge.setButtonState(NativeBridge.BUTTON_UP, up);
        }
        if (state.down != down) {
            state.down = down;
            NativeBridge.setButtonState(NativeBridge.BUTTON_DOWN, down);
        }
        if (state.left != left) {
            state.left = left;
            NativeBridge.setButtonState(NativeBridge.BUTTON_LEFT, left);
        }
        if (state.right != right) {
            state.right = right;
            NativeBridge.setButtonState(NativeBridge.BUTTON_RIGHT, right);
        }
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

    private static void addActionButton(Activity activity, FrameLayout parent, List<VirtualButton> buttons, String label, int button, int size, float centerX, float centerY) {
        addActionButton(activity, parent, buttons, label, button, size, size, centerX, centerY);
    }

    private static void addActionButton(Activity activity, FrameLayout parent, List<VirtualButton> buttons, String label, int button, int width, int height, float centerX, float centerY) {
        TextView view = makeButton(activity, label);
        placeByCenter(parent, view, width, height, centerX, centerY);
        buttons.add(new VirtualButton(view, button));
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

    private static void addSystemButtonByCenter(Activity activity, FrameLayout parent, String label, int width, int height, float centerX, float centerY, Runnable action) {
        TextView view = makeSystemButton(activity, label, action);
        placeByCenter(parent, view, width, height, centerX, centerY);
    }

    private static void placeByCenter(FrameLayout parent, View view, int width, int height, float centerX, float centerY) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, height, Gravity.TOP | Gravity.LEFT);
        lp.leftMargin = Math.round(centerX - width / 2f);
        lp.topMargin = Math.round(centerY - height / 2f);
        parent.addView(view, lp);
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
