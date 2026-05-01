package com.krendstudio.krendbuoy;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

final class GameControllerOverlay {
    interface Host {
        void showQuickMenu();
        void showStateSlotDialog(boolean save);
        void showControllerSettingsDialog();
        void cycleEmulationSpeed();
        void releaseAllButtons();
        String emulationSpeedLabel();
        int dp(int value);
        void showDisplaySettingsDialog();
        void showAudioPresetDialog();
    }

    private static final int PAGE_CONTROLLER = 0;
    private static final int PAGE_CHEATS = 1;
    private static final int PAGE_SETTINGS = 2;
    private static int sCurrentPage = PAGE_CONTROLLER;

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

    static void attach(Activity activity, FrameLayout root, Host host) {
        sCurrentPage = PAGE_CONTROLLER;
        int menuIconSize = host.dp(40);
        int margin = host.dp(16);

        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels - host.dp(16);
        int screenHeight = Math.round(screenWidth * 2f / 3f);
        screenHeight = Math.max(host.dp(220), Math.min(screenHeight, host.dp(360)));
        int panelTop = host.dp(62) + screenHeight;

        // Top Bar - Title
        TextView title = new TextView(activity);
        title.setText("KrendBuoy");
        title.setTextSize(18f);
        title.setTextColor(Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        FrameLayout.LayoutParams titleLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.LEFT);
        titleLp.leftMargin = margin;
        titleLp.topMargin = host.dp(12);
        root.addView(title, titleLp);

        // Top Bar - Controls
        // Unicode Icons: Save (Floppy), Load (Folder/Tray), Menu (Hamburger)
        addSystemControl(activity, root, "\uD83D\uDCBE", menuIconSize, menuIconSize,
                Gravity.TOP | Gravity.RIGHT, margin + menuIconSize * 2 + host.dp(12), host.dp(8), () -> host.showStateSlotDialog(true));
        addSystemControl(activity, root, "\uD83D\uDCE5", menuIconSize, menuIconSize,
                Gravity.TOP | Gravity.RIGHT, margin + menuIconSize + host.dp(6), host.dp(8), () -> host.showStateSlotDialog(false));
        addSystemControl(activity, root, "\u2630", menuIconSize, menuIconSize,
                Gravity.TOP | Gravity.RIGHT, margin, host.dp(8), host::showQuickMenu);

        // Speed Button - Moved to be above the D-pad area
        TextView speed = addSystemControl(activity, root, host.emulationSpeedLabel(), host.dp(50), host.dp(32),
                Gravity.TOP | Gravity.LEFT, margin, panelTop - host.dp(40), () -> {
                    host.cycleEmulationSpeed();
                });
        speed.setOnClickListener(v -> {
            host.cycleEmulationSpeed();
            speed.setText(host.emulationSpeedLabel());
        });

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

        int dpadSize = Math.max(host.dp(140), Math.round(w * 0.35f));
        int actionSize = Math.max(host.dp(62), Math.round(w * 0.14f));
        int shoulderWidth = host.dp(84);
        int shoulderHeight = host.dp(44);
        int startSelectWidth = host.dp(72);
        int startSelectHeight = host.dp(34);
        
        int navHeight = host.dp(60);

        List<View> controllerViews = new ArrayList<>();
        List<View> cheatsViews = new ArrayList<>();
        List<View> settingsViews = new ArrayList<>();
        List<View> navButtons = new ArrayList<>();

        // Controller Page
        // D-pad on the left
        FrameLayout dpadPad = makeDpadPad(activity, host, dpadSize);
        placeByCenter(panel, dpadPad, dpadSize, dpadSize, w * 0.25f, h * 0.55f);
        controllerViews.add(dpadPad);

        // L and R above A/B
        controllerViews.add(addActionButton(activity, panel, actionButtons, "L", NativeBridge.BUTTON_L, shoulderWidth, shoulderHeight, w * 0.65f, h * 0.38f, 15));
        controllerViews.add(addActionButton(activity, panel, actionButtons, "R", NativeBridge.BUTTON_R, shoulderWidth, shoulderHeight, w * 0.85f, h * 0.38f, 15));

        // A and B on the right, B is lower-left of A
        controllerViews.add(addActionButton(activity, panel, actionButtons, "B", NativeBridge.BUTTON_B, actionSize, actionSize, (float)(w * 0.70), (float)(h * 0.68), actionSize / 2));
        controllerViews.add(addActionButton(activity, panel, actionButtons, "A", NativeBridge.BUTTON_A, actionSize, actionSize, (float)(w * 0.86), (float)(h * 0.52), actionSize / 2));

        // Select and Start at bottom center - adjusted spacing and size to avoid overlap
        controllerViews.add(addActionButton(activity, panel, actionButtons, "SELECT", NativeBridge.BUTTON_SELECT, startSelectWidth, startSelectHeight, w * 0.38f, h * 0.82f, 8));
        controllerViews.add(addActionButton(activity, panel, actionButtons, "START", NativeBridge.BUTTON_START, startSelectWidth, startSelectHeight, w * 0.62f, h * 0.82f, 8));

        // Cheats Page
        TextView cheatsInfo = new TextView(activity);
        cheatsInfo.setText("Cheat codes and memory modification\nwill be available in a future update.");
        cheatsInfo.setTextColor(Color.GRAY);
        cheatsInfo.setGravity(Gravity.CENTER);
        placeByCenter(panel, cheatsInfo, w, host.dp(100), w / 2f, h / 2f);
        cheatsViews.add(cheatsInfo);

        // Settings Page
        int settingsBtnW = Math.round(w * 0.7f);
        int settingsBtnH = host.dp(52);
        settingsViews.add(addSystemButtonByCenter(activity, panel, "Display Settings", settingsBtnW, settingsBtnH, w / 2f, h * 0.35f, host::showDisplaySettingsDialog));
        settingsViews.add(addSystemButtonByCenter(activity, panel, "Audio Preset", settingsBtnW, settingsBtnH, w / 2f, h * 0.50f, host::showAudioPresetDialog));
        settingsViews.add(addSystemButtonByCenter(activity, panel, "Controller Settings", settingsBtnW, settingsBtnH, w / 2f, h * 0.65f, host::showControllerSettingsDialog));

        // Navigation Bar
        int navBtnW = w / 3;
        navButtons.add(addNavButton(activity, panel, "CONTROLLER", "\uD83C\uDFAE", navBtnW, navHeight, w * 1f / 6f, h - navHeight / 2f, () -> switchPage(PAGE_CONTROLLER, controllerViews, cheatsViews, settingsViews, navButtons, host)));
        navButtons.add(addNavButton(activity, panel, "CHEATS", "\u2328", navBtnW, navHeight, w * 3f / 6f, h - navHeight / 2f, () -> switchPage(PAGE_CHEATS, controllerViews, cheatsViews, settingsViews, navButtons, host)));
        navButtons.add(addNavButton(activity, panel, "SETTINGS", "\u2699\uFE0F", navBtnW, navHeight, w * 5f / 6f, h - navHeight / 2f, () -> switchPage(PAGE_SETTINGS, controllerViews, cheatsViews, settingsViews, navButtons, host)));

        // Initial state
        switchPage(PAGE_CONTROLLER, controllerViews, cheatsViews, settingsViews, navButtons, host);

        panel.setOnTouchListener((view, event) -> {
            if (sCurrentPage == PAGE_CONTROLLER) {
                updateDpad(dpadPad, dpadState, event);
                updateVirtualButtons(actionButtons, event);
            }
            return true;
        });
    }

    private static void switchPage(int page, List<View> controllerViews, List<View> cheatsViews, List<View> settingsViews, List<View> navButtons, Host host) {
        sCurrentPage = page;
        if (page != PAGE_CONTROLLER) {
            host.releaseAllButtons();
        }
        for (View v : controllerViews) v.setVisibility(page == PAGE_CONTROLLER ? View.VISIBLE : View.GONE);
        for (View v : cheatsViews) v.setVisibility(page == PAGE_CHEATS ? View.VISIBLE : View.GONE);
        for (View v : settingsViews) v.setVisibility(page == PAGE_SETTINGS ? View.VISIBLE : View.GONE);

        for (int i = 0; i < navButtons.size(); i++) {
            ViewGroup navGroup = (ViewGroup) navButtons.get(i);
            int color = (i == page) ? Color.rgb(61, 155, 235) : Color.GRAY;
            for (int j = 0; j < navGroup.getChildCount(); j++) {
                View child = navGroup.getChildAt(j);
                if (child instanceof TextView) {
                    ((TextView) child).setTextColor(color);
                }
            }
        }
    }

    private static View addNavButton(Activity activity, FrameLayout parent, String text, String icon, int width, int height, float centerX, float centerY, Runnable action) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        
        TextView iconView = new TextView(activity);
        iconView.setText(icon);
        iconView.setTextSize(20f);
        iconView.setGravity(Gravity.CENTER);
        layout.addView(iconView);
        
        TextView textView = new TextView(activity);
        textView.setText(text);
        textView.setTextSize(10f);
        textView.setGravity(Gravity.CENTER);
        layout.addView(textView);
        
        placeByCenter(parent, layout, width, height, centerX, centerY);
        layout.setOnClickListener(v -> action.run());
        
        return layout;
    }

    private static FrameLayout makeDpadPad(Activity activity, Host host, int dpadSize) {
        FrameLayout pad = new FrameLayout(activity);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(0x33666666);
        gd.setCornerRadius(dpadSize / 2f);
        pad.setBackground(gd);
        pad.setAlpha(0.85f);
        
        int arrowSize = Math.max(host.dp(42), Math.round(dpadSize * 0.32f));
        int offset = Math.round(dpadSize * 0.32f);
        addDpadArrow(activity, pad, "\u25B2", arrowSize, dpadSize / 2f, dpadSize / 2f - offset);
        addDpadArrow(activity, pad, "\u25BC", arrowSize, dpadSize / 2f, dpadSize / 2f + offset);
        addDpadArrow(activity, pad, "\u25C0", arrowSize, dpadSize / 2f - offset, dpadSize / 2f);
        addDpadArrow(activity, pad, "\u25B6", arrowSize, dpadSize / 2f + offset, dpadSize / 2f);
        
        // Center circle
        View center = new View(activity);
        GradientDrawable cgd = new GradientDrawable();
        cgd.setColor(0x44888888);
        cgd.setCornerRadius(host.dp(10));
        center.setBackground(cgd);
        placeByCenter(pad, center, host.dp(20), host.dp(20), dpadSize / 2f, dpadSize / 2f);
        
        return pad;
    }

    private static void addDpadArrow(Activity activity, FrameLayout parent, String label, int size, float centerX, float centerY) {
        TextView arrow = new TextView(activity);
        arrow.setText(label);
        arrow.setTextSize(20f);
        arrow.setTextColor(Color.WHITE);
        arrow.setGravity(Gravity.CENTER);
        placeByCenter(parent, arrow, size, size, centerX, centerY);
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

        float dpadLeft = dpad.getLeft() + dpad.getTranslationX();
        float dpadTop = dpad.getTop() + dpad.getTranslationY();
        float cx = dpadLeft + dpad.getWidth() / 2f;
        float cy = dpadTop + dpad.getHeight() / 2f;
        float dx = chosenX - cx;
        float dy = chosenY - cy;
        float deadZone = Math.min(dpad.getWidth(), dpad.getHeight()) * 0.15f;

        if (Math.hypot(dx, dy) < deadZone) {
            setDpadState(state, false, false, false, false);
            dpad.setAlpha(0.85f);
            return;
        }

        double degrees = Math.toDegrees(Math.atan2(dy, dx));
        boolean pressRight = degrees >= -67.5 && degrees <= 67.5;
        boolean pressDown = degrees >= 22.5 && degrees <= 157.5;
        boolean pressLeft = degrees >= 112.5 || degrees <= -112.5;
        boolean pressUp = degrees >= -157.5 && degrees <= -22.5;
        setDpadState(state, pressUp, pressDown, pressLeft, pressRight);
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
        if (button.view.getBackground() instanceof GradientDrawable) {
            ((GradientDrawable) button.view.getBackground()).setColor(pressed ? 0xAA666666 : 0xAA444444);
        }
    }

    private static TextView addActionButton(Activity activity, FrameLayout parent, List<VirtualButton> buttons, String label, int button, int width, int height, float centerX, float centerY, int radius) {
        TextView view = makeButton(activity, label, radius);
        placeByCenter(parent, view, width, height, centerX, centerY);
        buttons.add(new VirtualButton(view, button));
        return view;
    }

    private static TextView addSystemControl(Activity activity, FrameLayout parent, String label, int width, int height, int gravity, int horizontalMargin, int verticalMargin, Runnable action) {
        TextView view = makeSystemButton(activity, label, action);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, height, gravity);
        if ((gravity & Gravity.RIGHT) == Gravity.RIGHT) lp.rightMargin = horizontalMargin;
        else if ((gravity & Gravity.LEFT) == Gravity.LEFT) lp.leftMargin = horizontalMargin;
        if ((gravity & Gravity.TOP) == Gravity.TOP) lp.topMargin = verticalMargin;
        else lp.bottomMargin = verticalMargin;
        parent.addView(view, lp);
        return view;
    }

    private static TextView addSystemButtonByCenter(Activity activity, FrameLayout parent, String label, int width, int height, float centerX, float centerY, Runnable action) {
        TextView view = makeSystemButton(activity, label, action);
        placeByCenter(parent, view, width, height, centerX, centerY);
        return view;
    }

    private static void placeByCenter(ViewGroup parent, View view, int width, int height, float centerX, float centerY) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, height, Gravity.TOP | Gravity.LEFT);
        lp.leftMargin = Math.round(centerX - width / 2f);
        lp.topMargin = Math.round(centerY - height / 2f);
        parent.addView(view, lp);
    }

    private static TextView makeSystemButton(Activity activity, String label, Runnable action) {
        TextView view = new TextView(activity);
        view.setText(label);
        view.setTextSize(14f);
        view.setTextColor(Color.WHITE);
        view.setGravity(Gravity.CENTER);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(0xAA333333);
        gd.setCornerRadius(12);
        view.setBackground(gd);
        view.setAlpha(0.9f);
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private static TextView makeButton(Activity activity, String label, int radius) {
        TextView view = new TextView(activity);
        view.setText(label);
        // Smaller font for longer labels like SELECT/START
        float textSize = label.length() > 5 ? 10f : (label.length() > 1 ? 12f : 24f);
        view.setTextSize(textSize);
        view.setTextColor(Color.WHITE);
        view.setGravity(Gravity.CENTER);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(0xAA444444);
        gd.setCornerRadius(radius);
        view.setBackground(gd);
        view.setAlpha(0.85f);
        return view;
    }
}
