package com.krendstudio.krendbuoy;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
        AppSettingsManager getSettingsManager();
        CheatManager getCheatManager();
        PokemonManager getPokemonManager();
        MemoryScanner getMemoryScanner();
    }

    private static final int PAGE_CONTROLLER = 0;
    private static final int PAGE_CHEATS = 1;
    private static final int PAGE_POKEMON = 2;
    private static final int PAGE_SETTINGS = 3;
    private static int sCurrentPage = PAGE_CONTROLLER;
    private static boolean sIsEditing = false;
    private static boolean sUseSkin = true;
    private static View sDpadView;
    private static final List<VirtualButton> sActionButtons = new ArrayList<>();
    private static View sEditControlsOverlay;

    private static final class VirtualButton {
        final View view;
        final int button;
        boolean pressed;
        VirtualButton(View view, int button) { this.view = view; this.button = button; }
    }

    private static final class DpadState { boolean up, down, left, right; }

    private GameControllerOverlay() {}

    static void attach(Activity activity, FrameLayout root, Host host) {
        sCurrentPage = PAGE_CONTROLLER;
        sIsEditing = false;
        sUseSkin = host.getSettingsManager().isControllerSkinEnabled();
        int menuIconSize = host.dp(40);
        int margin = host.dp(16);
        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels - host.dp(16);
        int screenHeight = Math.round(screenWidth * 2f / 3f);
        screenHeight = Math.max(host.dp(220), Math.min(screenHeight, host.dp(360)));
        int panelTop = host.dp(62) + screenHeight;

        TextView title = new TextView(activity);
        title.setText("KrendBuoy"); title.setTextSize(18f); title.setTextColor(Color.WHITE); title.setTypeface(null, Typeface.BOLD);
        FrameLayout.LayoutParams titleLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT);
        titleLp.leftMargin = margin; titleLp.topMargin = host.dp(12); root.addView(title, titleLp);

        addSystemControl(activity, root, "\u2630", menuIconSize, menuIconSize, Gravity.TOP | Gravity.RIGHT, margin, host.dp(8), host::showQuickMenu);
        addSystemControl(activity, root, "\uD83D\uDCE5", menuIconSize, menuIconSize, Gravity.TOP | Gravity.RIGHT, margin + menuIconSize + host.dp(6), host.dp(8), () -> host.showStateSlotDialog(false));
        addSystemControl(activity, root, "\uD83D\uDCBE", menuIconSize, menuIconSize, Gravity.TOP | Gravity.RIGHT, margin + menuIconSize * 2 + host.dp(12), host.dp(8), () -> host.showStateSlotDialog(true));
        TextView speed = addSystemControl(activity, root, host.emulationSpeedLabel(), host.dp(44), menuIconSize, Gravity.TOP | Gravity.RIGHT, margin + menuIconSize * 3 + host.dp(18), host.dp(8), host::cycleEmulationSpeed);
        speed.setOnClickListener(v -> { host.cycleEmulationSpeed(); speed.setText(host.emulationSpeedLabel()); }); speed.setTextSize(12f);

        FrameLayout panel = new FrameLayout(activity);
        panel.setClipChildren(false); panel.setClipToPadding(false); panel.setBackgroundColor(Color.rgb(20, 24, 28));
        android.widget.ImageView controllerBg = new android.widget.ImageView(activity);
        int skinId = host.getSettingsManager().getControllerSkinId();
        int resId = activity.getResources().getIdentifier("controller_bg_0" + skinId, "drawable", activity.getPackageName());
        if (resId != 0) controllerBg.setImageResource(resId);
        controllerBg.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        controllerBg.setVisibility(sUseSkin ? View.VISIBLE : View.GONE); 
        panel.addView(controllerBg, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        panelLp.topMargin = panelTop; root.addView(panel, panelLp);
        List<VirtualButton> actionButtons = new ArrayList<>();
        DpadState dpadState = new DpadState();
        panel.post(() -> buildMeasuredPanel(activity, panel, host, actionButtons, dpadState));
    }

    private static void buildMeasuredPanel(Activity activity, FrameLayout panel, Host host, List<VirtualButton> actionButtons, DpadState dpadState) {
        int w = panel.getWidth(), h = panel.getHeight(); if (w <= 0 || h <= 0) return;
        sActionButtons.clear(); AppSettingsManager sm = host.getSettingsManager();
        int dpadSize = Math.round(w * 0.42f), actionSize = Math.round(w * 0.18f);
        int shoulderWidth = Math.round(w * 0.22f), shoulderHeight = host.dp(48);
        int startSelectWidth = Math.round(w * 0.15f), startSelectHeight = host.dp(30), navHeight = host.dp(60);
        List<View> controllerViews = new ArrayList<>(), cheatsViews = new ArrayList<>(), pokemonViews = new ArrayList<>(), settingsViews = new ArrayList<>(), navButtons = new ArrayList<>();

        float dpadX = sm.getButtonPosX(NativeBridge.BUTTON_UP, 0.255f), dpadY = sm.getButtonPosY(NativeBridge.BUTTON_UP, 0.52f);
        sDpadView = makeDpadPad(activity, host, dpadSize); placeByCenter(panel, sDpadView, dpadSize, dpadSize, w * dpadX, h * dpadY); controllerViews.add(sDpadView);
        controllerViews.add(addActionButton(activity, panel, actionButtons, sUseSkin ? "" : "L", NativeBridge.BUTTON_L, shoulderWidth, shoulderHeight, w * sm.getButtonPosX(NativeBridge.BUTTON_L, 0.62f), h * sm.getButtonPosY(NativeBridge.BUTTON_L, 0.30f), 10));
        controllerViews.add(addActionButton(activity, panel, actionButtons, sUseSkin ? "" : "R", NativeBridge.BUTTON_R, shoulderWidth, shoulderHeight, w * sm.getButtonPosX(NativeBridge.BUTTON_R, 0.86f), h * sm.getButtonPosY(NativeBridge.BUTTON_R, 0.30f), 10));
        controllerViews.add(addActionButton(activity, panel, actionButtons, sUseSkin ? "" : "B", NativeBridge.BUTTON_B, actionSize, actionSize, w * sm.getButtonPosX(NativeBridge.BUTTON_B, 0.657f), h * sm.getButtonPosY(NativeBridge.BUTTON_B, 0.595f), actionSize / 2));
        controllerViews.add(addActionButton(activity, panel, actionButtons, sUseSkin ? "" : "A", NativeBridge.BUTTON_A, actionSize, actionSize, w * sm.getButtonPosX(NativeBridge.BUTTON_A, 0.857f), h * sm.getButtonPosY(NativeBridge.BUTTON_A, 0.485f), actionSize / 2));
        controllerViews.add(addActionButton(activity, panel, actionButtons, sUseSkin ? "" : "SELECT", NativeBridge.BUTTON_SELECT, startSelectWidth, startSelectHeight, w * sm.getButtonPosX(NativeBridge.BUTTON_SELECT, 0.415f), h * sm.getButtonPosY(NativeBridge.BUTTON_SELECT, 0.77f), 10));
        controllerViews.add(addActionButton(activity, panel, actionButtons, sUseSkin ? "" : "START", NativeBridge.BUTTON_START, startSelectWidth, startSelectHeight, w * sm.getButtonPosX(NativeBridge.BUTTON_START, 0.585f), h * sm.getButtonPosY(NativeBridge.BUTTON_START, 0.77f), 10));
        sActionButtons.addAll(actionButtons);

        View cheatsView = CheatsToolsViewBuilder.build(activity, new CheatsToolsViewBuilder.Host() {
            @Override public int dp(int value) { return host.dp(value); }
            @Override public CheatManager getCheatManager() { return host.getCheatManager(); }
            @Override public MemoryScanner getMemoryScanner() { return host.getMemoryScanner(); }
        }); cheatsViews.add(cheatsView); placeByCenter(panel, cheatsView, w, h - navHeight, w / 2f, (h - navHeight) / 2f);
        View pokemonView = PokemonToolsViewBuilder.build(activity, new PokemonToolsViewBuilder.Host() {
            @Override public int dp(int value) { return host.dp(value); }
            @Override public AppSettingsManager getSettingsManager() { return host.getSettingsManager(); }
            @Override public PokemonManager getPokemonManager() { return host.getPokemonManager(); }
            @Override public MemoryScanner getMemoryScanner() { return host.getMemoryScanner(); }
        });
        pokemonViews.add(pokemonView); placeByCenter(panel, pokemonView, w, h - navHeight, w / 2f, (h - navHeight) / 2f);
        View settingsView = SharedSettingsBuilder.buildSettingsView(activity, new SharedSettingsBuilder.Host() {
            @Override public AppSettingsManager getSettingsManager() { return host.getSettingsManager(); }
            @Override public void onSettingChanged() { activity.recreate(); }
            @Override public void onEditLayout() { startEditing(panel, controllerViews, cheatsViews, pokemonViews, settingsViews, navButtons, host); }
        });
        settingsViews.add(settingsView); placeByCenter(panel, settingsView, w, h - navHeight, w / 2f, (h - navHeight) / 2f);

        int navBtnW = w / 4;
        navButtons.add(addNavButton(panel, "CONTROLLER", "🎮", navBtnW, navHeight, w * 1f / 8f, h - navHeight / 2f, () -> switchPage(PAGE_CONTROLLER, controllerViews, cheatsViews, pokemonViews, settingsViews, navButtons, host)));
        navButtons.add(addNavButton(panel, "CHEATS", "⌨", navBtnW, navHeight, w * 3f / 8f, h - navHeight / 2f, () -> switchPage(PAGE_CHEATS, controllerViews, cheatsViews, pokemonViews, settingsViews, navButtons, host)));
        navButtons.add(addNavButton(panel, "POKEMON", "◎", navBtnW, navHeight, w * 5f / 8f, h - navHeight / 2f, () -> switchPage(PAGE_POKEMON, controllerViews, cheatsViews, pokemonViews, settingsViews, navButtons, host)));
        navButtons.add(addNavButton(panel, "SETTINGS", "⚙", navBtnW, navHeight, w * 7f / 8f, h - navHeight / 2f, () -> switchPage(PAGE_SETTINGS, controllerViews, cheatsViews, pokemonViews, settingsViews, navButtons, host)));
        switchPage(PAGE_CONTROLLER, controllerViews, cheatsViews, pokemonViews, settingsViews, navButtons, host);

        panel.setOnTouchListener((view, event) -> {
            if (sIsEditing) { handleEditTouch(event); return true; }
            if (sCurrentPage == PAGE_CONTROLLER) { updateDpad(sDpadView, dpadState, event); updateVirtualButtons(actionButtons, event); }
            return true;
        });
    }

    private static void switchPage(int page, List<View> cv, List<View> chv, List<View> pv, List<View> sv, List<View> nb, Host host) {
        sCurrentPage = page; if (page != PAGE_CONTROLLER) host.releaseAllButtons();
        ViewGroup panel = (ViewGroup) cv.get(0).getParent();
        if (panel != null) { View bg = panel.getChildAt(0); if (bg instanceof android.widget.ImageView) bg.setVisibility((sUseSkin && page == PAGE_CONTROLLER) ? View.VISIBLE : View.GONE); }
        for (View v : cv) v.setVisibility(page == PAGE_CONTROLLER ? View.VISIBLE : View.GONE);
        for (View v : chv) v.setVisibility(page == PAGE_CHEATS ? View.VISIBLE : View.GONE);
        for (View v : pv) v.setVisibility(page == PAGE_POKEMON ? View.VISIBLE : View.GONE);
        for (View v : sv) v.setVisibility(page == PAGE_SETTINGS ? View.VISIBLE : View.GONE);
        for (int i = 0; i < nb.size(); i++) {
            ViewGroup g = (ViewGroup) nb.get(i); int c = (i == page) ? Color.rgb(61, 155, 235) : Color.GRAY;
            for (int j = 0; j < g.getChildCount(); j++) { View ch = g.getChildAt(j); if (ch instanceof TextView) ((TextView) ch).setTextColor(c); }
        }
    }

    private static View addNavButton(ViewGroup parent, String text, String icon, int width, int height, float centerX, float centerY, Runnable action) {
        LinearLayout layout = new LinearLayout(parent.getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        TextView iv = new TextView(parent.getContext()); iv.setText(icon); iv.setTextSize(20f); iv.setGravity(Gravity.CENTER); layout.addView(iv);
        TextView tv = new TextView(parent.getContext()); tv.setText(text); tv.setTextSize(10f); tv.setGravity(Gravity.CENTER); layout.addView(tv);
        placeByCenter(parent, layout, width, height, centerX, centerY);
        layout.setOnClickListener(v -> action.run());
        return layout;
    }
    private static void handleEditTouch(MotionEvent event) {
        int action = event.getActionMasked(); float x = event.getX(), y = event.getY();
        if (action == MotionEvent.ACTION_DOWN) {
            sDraggingView = null; if (isInside(x, y, sDpadView)) sDraggingView = sDpadView; else for (VirtualButton b : sActionButtons) if (isInside(x, y, b.view)) { sDraggingView = b.view; break; }
            if (sDraggingView != null) { sDragOffsetX = x - (sDraggingView.getLeft() + sDraggingView.getWidth() / 2f); sDragOffsetY = y - (sDraggingView.getTop() + sDraggingView.getHeight() / 2f); sDraggingView.setAlpha(0.5f); }
        } else if (action == MotionEvent.ACTION_MOVE && sDraggingView != null) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) sDraggingView.getLayoutParams();
            lp.leftMargin = Math.round(x - sDragOffsetX - sDraggingView.getWidth() / 2f); lp.topMargin = Math.round(y - sDragOffsetY - sDraggingView.getHeight() / 2f); sDraggingView.setLayoutParams(lp);
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) { if (sDraggingView != null) { sDraggingView.setAlpha(1.0f); sDraggingView = null; } }
    }

    private static View sDraggingView = null;
    private static float sDragOffsetX = 0, sDragOffsetY = 0;

    private static void startEditing(FrameLayout p, List<View> cv, List<View> chv, List<View> pv, List<View> sv, List<View> nb, Host host) {
        sIsEditing = true; switchPage(PAGE_CONTROLLER, cv, chv, pv, sv, nb, host); sDpadView.setBackgroundColor(0x66888888);
        for (VirtualButton b : sActionButtons) { b.view.setAlpha(1.0f); if (b.view.getBackground() instanceof GradientDrawable) ((GradientDrawable) b.view.getBackground()).setColor(0xAA444444); }
        LinearLayout o = new LinearLayout(p.getContext()); o.setOrientation(LinearLayout.HORIZONTAL); o.setGravity(Gravity.CENTER); o.setBackgroundColor(0xCC000000);
        int bw = host.dp(80), bh = host.dp(40);
        o.addView(makeSystemButton((Activity)p.getContext(), "Save", () -> { saveLayout(p, host); stopEditing(p, cv, chv, pv, sv, nb, host); }), new LinearLayout.LayoutParams(bw, bh));
        o.addView(new View(p.getContext()), new LinearLayout.LayoutParams(host.dp(12), 1));
        o.addView(makeSystemButton((Activity)p.getContext(), "Reset", () -> { host.getSettingsManager().resetAllButtonPos(); ((Activity)p.getContext()).recreate(); }), new LinearLayout.LayoutParams(bw, bh));
        o.addView(new View(p.getContext()), new LinearLayout.LayoutParams(host.dp(12), 1));
        o.addView(makeSystemButton((Activity)p.getContext(), "Cancel", () -> stopEditing(p, cv, chv, pv, sv, nb, host)), new LinearLayout.LayoutParams(bw, bh));
        p.addView(o, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.dp(60), Gravity.BOTTOM)); sEditControlsOverlay = o;
    }

    private static void stopEditing(FrameLayout p, List<View> cv, List<View> chv, List<View> pv, List<View> sv, List<View> nb, Host host) {
        sIsEditing = false; if (sEditControlsOverlay != null) { p.removeView(sEditControlsOverlay); sEditControlsOverlay = null; }
        if (sUseSkin) { sDpadView.setBackgroundColor(Color.TRANSPARENT); for (VirtualButton b : sActionButtons) { b.view.setAlpha(0.0f); if (b.view.getBackground() instanceof GradientDrawable) ((GradientDrawable) b.view.getBackground()).setColor(Color.WHITE); } }
        switchPage(PAGE_SETTINGS, cv, chv, pv, sv, nb, host);
    }

    private static void saveLayout(FrameLayout p, Host host) {
        float w = p.getWidth(), h = p.getHeight(); if (w <= 0 || h <= 0) return; AppSettingsManager sm = host.getSettingsManager();
        sm.setButtonPos(NativeBridge.BUTTON_UP, (sDpadView.getLeft() + sDpadView.getWidth() / 2f) / w, (sDpadView.getTop() + sDpadView.getHeight() / 2f) / h);
        for (VirtualButton b : sActionButtons) sm.setButtonPos(b.button, (b.view.getLeft() + b.view.getWidth() / 2f) / w, (b.view.getTop() + b.view.getHeight() / 2f) / h);
    }
    private static FrameLayout makeDpadPad(Activity a, Host h, int s) {
        FrameLayout p = new FrameLayout(a); if (sUseSkin) p.setBackgroundColor(Color.TRANSPARENT); else { GradientDrawable g = new GradientDrawable(); g.setColor(0x66333333); g.setCornerRadius(s / 2f); p.setBackground(g); }
        p.setAlpha(1.0f); int as = Math.max(h.dp(42), Math.round(s * 0.32f)), o = Math.round(s * 0.32f);
        addDpadArrow(a, p, sUseSkin ? "" : "↑", as, s / 2f, s / 2f - o); addDpadArrow(a, p, sUseSkin ? "" : "↓", as, s / 2f, s / 2f + o);
        addDpadArrow(a, p, sUseSkin ? "" : "←", as, s / 2f - o, s / 2f); addDpadArrow(a, p, sUseSkin ? "" : "→", as, s / 2f + o, s / 2f); return p;
    }

    private static void addDpadArrow(Activity a, FrameLayout p, String l, int s, float cx, float cy) {
        TextView ar = new TextView(a); ar.setText(l); ar.setTextSize(20f); ar.setTextColor(Color.WHITE); ar.setGravity(Gravity.CENTER); placeByCenter(p, ar, s, s, cx, cy);
    }

    private static void updateDpad(View d, DpadState s, MotionEvent e) {
        int ac = e.getActionMasked(); if (ac == MotionEvent.ACTION_UP || ac == MotionEvent.ACTION_CANCEL) { setDpadState(s, false, false, false, false); d.setAlpha(0.85f); return; }
        int ai = e.getActionIndex(); boolean pu = ac == MotionEvent.ACTION_POINTER_UP; boolean f = false; float cx = 0, cy = 0;
        for (int i = 0; i < e.getPointerCount(); i++) { if (pu && i == ai) continue; if (isInside(e.getX(i), e.getY(i), d)) { cx = e.getX(i); cy = e.getY(i); f = true; break; } }
        if (!f) { setDpadState(s, false, false, false, false); d.setAlpha(0.85f); return; }
        float dx = cx - (d.getLeft() + d.getTranslationX() + d.getWidth() / 2f), dy = cy - (d.getTop() + d.getTranslationY() + d.getHeight() / 2f);
        if (Math.hypot(dx, dy) < Math.min(d.getWidth(), d.getHeight()) * 0.15f) { setDpadState(s, false, false, false, false); d.setAlpha(0.85f); return; }
        double deg = Math.toDegrees(Math.atan2(dy, dx));
        setDpadState(s, deg >= -157.5 && deg <= -22.5, deg >= 22.5 && deg <= 157.5, deg >= 112.5 || deg <= -112.5, deg >= -67.5 && deg <= 67.5); d.setAlpha(1.0f);
    }

    private static void setDpadState(DpadState s, boolean u, boolean d, boolean l, boolean r) {
        if (s.up != u) { s.up = u; NativeBridge.setButtonState(NativeBridge.BUTTON_UP, u); }
        if (s.down != d) { s.down = d; NativeBridge.setButtonState(NativeBridge.BUTTON_DOWN, d); }
        if (s.left != l) { s.left = l; NativeBridge.setButtonState(NativeBridge.BUTTON_LEFT, l); }
        if (s.right != r) { s.right = r; NativeBridge.setButtonState(NativeBridge.BUTTON_RIGHT, r); }
    }

    private static void updateVirtualButtons(List<VirtualButton> bs, MotionEvent e) {
        int ac = e.getActionMasked(); if (ac == MotionEvent.ACTION_UP || ac == MotionEvent.ACTION_CANCEL) { for (VirtualButton b : bs) setPressed(b, false); return; }
        int ai = e.getActionIndex(); boolean pu = ac == MotionEvent.ACTION_POINTER_UP;
        for (VirtualButton b : bs) { boolean in = false; for (int i = 0; i < e.getPointerCount(); i++) { if (pu && i == ai) continue; if (isInside(e.getX(i), e.getY(i), b.view)) { in = true; break; } } setPressed(b, in); }
    }

    private static boolean isInside(float x, float y, View v) {
        float l = v.getLeft() + v.getTranslationX(), t = v.getTop() + v.getTranslationY();
        return x >= l && x <= l + v.getWidth() && y >= t && y <= t + v.getHeight();
    }

    private static void setPressed(VirtualButton b, boolean p) {
        if (b.pressed == p) return; b.pressed = p; NativeBridge.setButtonState(b.button, p);
        if (sUseSkin) b.view.setAlpha(p ? 0.3f : 0.0f); else b.view.setAlpha(p ? 1.0f : 0.85f);
        if (b.view.getBackground() instanceof GradientDrawable) ((GradientDrawable) b.view.getBackground()).setColor(Color.WHITE);
    }

    private static TextView addActionButton(Activity a, FrameLayout p, List<VirtualButton> bs, String l, int b, int w, int h, float cx, float cy, int r) {
        TextView v = makeButton(a, l, r); placeByCenter(p, v, w, h, cx, cy); bs.add(new VirtualButton(v, b)); return v;
    }

    private static TextView addSystemControl(Activity a, FrameLayout p, String l, int w, int h, int g, int hm, int vm, Runnable ac) {
        TextView v = makeSystemButton(a, l, ac); FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h, g);
        if ((g & Gravity.RIGHT) == Gravity.RIGHT) lp.rightMargin = hm; else if ((g & Gravity.LEFT) == Gravity.LEFT) lp.leftMargin = hm;
        if ((g & Gravity.TOP) == Gravity.TOP) lp.topMargin = vm; else lp.bottomMargin = vm; p.addView(v, lp); return v;
    }

    private static void placeByCenter(ViewGroup p, View v, int w, int h, float cx, float cy) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h, Gravity.TOP | Gravity.LEFT);
        lp.leftMargin = Math.round(cx - w / 2f); lp.topMargin = Math.round(cy - h / 2f); p.addView(v, lp);
    }

    private static TextView makeSystemButton(Activity a, String l, Runnable ac) {
        TextView v = new TextView(a); v.setText(l); v.setTextSize(14f); v.setTextColor(Color.WHITE); v.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable(); g.setColor(0xAA333333); g.setCornerRadius(12); v.setBackground(g); v.setAlpha(0.9f); v.setOnClickListener(view -> ac.run()); return v;
    }

    private static TextView makeButton(Activity a, String l, int r) {
        TextView v = new TextView(a); v.setText(l); float ts = l.length() > 5 ? 10f : (l.length() > 1 ? 12f : 24f); v.setTextSize(ts); v.setTextColor(Color.WHITE); v.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable(); if (sUseSkin) { g.setColor(Color.WHITE); v.setAlpha(0.0f); } else { g.setColor(0xAA444444); v.setAlpha(0.85f); }
        g.setCornerRadius(r); v.setBackground(g); return v;
    }

    private static String bytesToHex(byte[] b) {
        if (b == null) return "NULL"; StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format("%02X ", x)); return s.toString();
    }
}
