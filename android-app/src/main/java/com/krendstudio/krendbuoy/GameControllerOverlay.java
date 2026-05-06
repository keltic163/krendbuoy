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

        View cheatsView = buildCheatsView(activity, host); cheatsViews.add(cheatsView); placeByCenter(panel, cheatsView, w, h - navHeight, w / 2f, (h - navHeight) / 2f);
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

    private static View buildCheatsView(Activity activity, Host host) {
        FrameLayout wrapper = new FrameLayout(activity);
        final Runnable[] refreshRef = new Runnable[1];
        refreshRef[0] = () -> {
            wrapper.removeAllViews(); LinearLayout root = new LinearLayout(activity); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(host.dp(16), host.dp(16), host.dp(16), host.dp(16));
            buildCheatsContent(activity, host, root, refreshRef[0]); wrapper.addView(root);
        };
        refreshRef[0].run(); return wrapper;
    }

    private static View buildPokemonView(Activity activity, Host host) {
        FrameLayout wrapper = new FrameLayout(activity);
        final Runnable[] refreshRef = new Runnable[1];
        refreshRef[0] = () -> {
            wrapper.removeAllViews(); ScrollView scroll = new ScrollView(activity);
            LinearLayout root = new LinearLayout(activity); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(host.dp(16), host.dp(16), host.dp(16), host.dp(16));
            buildPokemonContent(activity, host, root, refreshRef[0]); scroll.addView(root); wrapper.addView(scroll);
        };
        refreshRef[0].run(); return wrapper;
    }

    private static PokemonConstants.Pocket sCurrentPocket = PokemonConstants.Pocket.ITEMS;

    private static void buildPokemonContent(Activity activity, Host host, LinearLayout root, Runnable refreshAll) {
        PokemonManager pm = host.getPokemonManager(); if (pm == null) return;
        AppSettingsManager sm = host.getSettingsManager();
        
        // 自動嘗試恢復之前的掃描狀態
        if (pm.getMoneyAddress() == 0) {
            String lastId = sm.getLastTrainerId();
            if (!lastId.isEmpty()) {
                try {
                    if (lastId.matches("\\d+")) pm.scanForTrainerID(Integer.parseInt(lastId));
                    else pm.scanForTrainerName(lastId);
                } catch (Exception ignored) {}
            } else {
                pm.autoLocateByPointers(); // 至少嘗試自動定位
            }
        }

        buildPokemonToolsSection(activity, host, root, refreshAll);
        root.addView(new View(activity), new LinearLayout.LayoutParams(1, host.dp(12)));
        
        if (pm.getMoneyAddress() != 0) {
            // 口袋切換標籤
            HorizontalScrollView hsv = new HorizontalScrollView(activity);
            hsv.setHorizontalScrollBarEnabled(false);
            LinearLayout tabs = new LinearLayout(activity); tabs.setOrientation(LinearLayout.HORIZONTAL);
            tabs.setBackgroundColor(0x33000000); tabs.setPadding(host.dp(4), host.dp(4), host.dp(4), host.dp(4));
            
            String[] pocketNames = {"Items", "Balls", "Key", "TM", "Berry"};
            PokemonConstants.Pocket[] pocketTypes = {
                PokemonConstants.Pocket.ITEMS, 
                PokemonConstants.Pocket.BALLS, 
                PokemonConstants.Pocket.KEY_ITEMS, 
                PokemonConstants.Pocket.TM_HM, 
                PokemonConstants.Pocket.BERRIES
            };
            
            for (int i = 0; i < pocketNames.length; i++) {
                final int idx = i; TextView t = new TextView(activity); t.setText(pocketNames[i]);
                t.setGravity(Gravity.CENTER); t.setPadding(host.dp(16), host.dp(6), host.dp(16), host.dp(6));
                t.setTextSize(12f); t.setTypeface(null, sCurrentPocket == pocketTypes[i] ? Typeface.BOLD : Typeface.NORMAL);
                t.setTextColor(sCurrentPocket == pocketTypes[i] ? Color.CYAN : Color.GRAY);
                t.setOnClickListener(v -> { sCurrentPocket = pocketTypes[idx]; refreshAll.run(); });
                tabs.addView(t);
            }
            hsv.addView(tabs);
            root.addView(hsv);

            TextView bt = new TextView(activity); bt.setText("Pocket: " + sCurrentPocket.name()); bt.setTextColor(Color.WHITE); bt.setPadding(0, host.dp(8), 0, host.dp(4)); bt.setTextSize(13f); root.addView(bt);
            
            List<PokemonConstants.ItemSlot> items = pm.getBagItems(sCurrentPocket);
            if (items.isEmpty()) {
                TextView empty = new TextView(activity); empty.setText("Empty or not loaded."); empty.setTextColor(Color.GRAY); empty.setPadding(0, host.dp(8), 0, host.dp(8)); root.addView(empty);
            } else {
                LinearLayout list = new LinearLayout(activity); list.setOrientation(LinearLayout.VERTICAL);
                for (PokemonConstants.ItemSlot item : items) {
                    LinearLayout row = new LinearLayout(activity); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(0, host.dp(4), 0, host.dp(4));
                    TextView name = new TextView(activity); name.setText(pm.getItemName(item.id)); name.setTextColor(Color.LTGRAY); name.setTextSize(14f); row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                    TextView count = new TextView(activity); count.setText("x" + item.count); count.setTextColor(Color.YELLOW); count.setTextSize(14f); row.addView(count);
                    row.setOnClickListener(v -> {
                        android.widget.EditText in = new android.widget.EditText(activity); in.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); in.setText(String.valueOf(item.count));
                        new AlertDialog.Builder(activity).setTitle("Edit: " + pm.getItemName(item.id)).setView(in).setPositiveButton("Set", (d, w) -> {
                            try { pm.setBagItem(item.index, item.id, Integer.parseInt(in.getText().toString()), item.pocket); refreshAll.run(); } catch (Exception ignored) {}
                        }).setNegativeButton("Cancel", null).show();
                    });
                    list.addView(row);
                }
                root.addView(list);
            }
            root.addView(makeSystemButton(activity, "Add to " + sCurrentPocket.name(), () -> showAddItemDialog(activity, pm, refreshAll)), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.dp(40)));
        } else {
            TextView info = new TextView(activity); info.setText("Locate Trainer first to edit Bag."); info.setTextColor(Color.GRAY); root.addView(info);
        }
    }

    private static void showAddItemDialog(Activity a, PokemonManager pm, Runnable refresh) {
        List<PokemonConstants.ItemInfo> common = pm.getCommonItems(sCurrentPocket);
        String[] names = new String[common.size() + 1];
        for (int i = 0; i < common.size(); i++) names[i] = common.get(i).name;
        names[common.size()] = "Custom ID...";

        new AlertDialog.Builder(a).setTitle("Add to " + sCurrentPocket.name()).setItems(names, (d, which) -> {
            if (which < common.size()) {
                PokemonConstants.ItemInfo info = common.get(which);
                showQuantityDialog(a, pm, info.id, info.name, info.pocket, refresh);
            } else {
                android.widget.EditText in = new android.widget.EditText(a); in.setHint("Item ID");
                new AlertDialog.Builder(a).setTitle("Enter ID").setView(in).setPositiveButton("Next", (d2, w2) -> {
                    try {
                        String s = in.getText().toString().trim();
                        int id = s.startsWith("0x") ? Integer.parseInt(s.substring(2), 16) : Integer.parseInt(s);
                        showQuantityDialog(a, pm, id, "Item #" + id, sCurrentPocket, refresh);
                    } catch (Exception ignored) {}
                }).show();
            }
        }).show();
    }

    private static void showQuantityDialog(Activity a, PokemonManager pm, int id, String name, PokemonConstants.Pocket p, Runnable refresh) {
        android.widget.EditText in = new android.widget.EditText(a); in.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); in.setText("10");
        new AlertDialog.Builder(a).setTitle("Quantity for " + name).setView(in).setPositiveButton("Add", (d, w) -> {
            try {
                int count = Integer.parseInt(in.getText().toString());
                if (pm.addBagItem(id, count, p)) {
                    Toast.makeText(a, "Added " + name, Toast.LENGTH_SHORT).show();
                    refresh.run();
                } else {
                    Toast.makeText(a, "Pocket is full!", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception ignored) {}
        }).setNegativeButton("Cancel", null).show();
    }

    private static void buildPokemonToolsSection(Activity activity, Host host, LinearLayout root, Runnable refreshAll) {
        PokemonManager pm = host.getPokemonManager(); if (pm == null) return;
        TextView pt = new TextView(activity); pt.setText("Pokemon Tools & Debugger"); pt.setTextColor(Color.rgb(61, 155, 235)); pt.setTypeface(null, Typeface.BOLD); pt.setTextSize(14f); root.addView(pt);
        TextView versionInfo = new TextView(activity);
        PokemonConstants.GameVersion autoVersion = pm.detectVersion();
        PokemonConstants.GameVersion effectiveVersion = pm.getEffectiveVersion();
        String versionText = pm.isManualVersionSelected()
                ? "Version: Manual " + pm.getManualVersion() + " | Auto: " + autoVersion
                : "Version: Auto " + effectiveVersion;

        if (effectiveVersion == PokemonConstants.GameVersion.UNKNOWN) {
            versionText += "\nUnknown ROM. Please set version manually.";
        }

        versionInfo.setText(versionText);
        versionInfo.setTextColor(effectiveVersion == PokemonConstants.GameVersion.UNKNOWN ? Color.YELLOW : Color.LTGRAY);
        versionInfo.setTextSize(11f);
        versionInfo.setPadding(0, host.dp(4), 0, host.dp(8));
        root.addView(versionInfo);
        LinearLayout mr = new LinearLayout(activity); mr.setOrientation(LinearLayout.HORIZONTAL); mr.setGravity(Gravity.CENTER_VERTICAL); mr.setPadding(0, host.dp(8), 0, host.dp(12));
        TextView mv = new TextView(activity); int m = pm.getMoney(); mv.setText("Money: $" + (m == -1 ? "???" : m)); mv.setTextColor(pm.isMoneyLocked() ? Color.CYAN : Color.YELLOW); mv.setTextSize(16f); mr.addView(mv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (pm.isMoneyLocked()) {
            mr.addView(makeSystemButton(activity, "Unlock", () -> { pm.unlockMoney(); refreshAll.run(); }), new LinearLayout.LayoutParams(host.dp(60), host.dp(34)));
            mr.addView(new View(activity), new LinearLayout.LayoutParams(host.dp(6), 1));
        }
        mr.addView(makeSystemButton(activity, "Edit", () -> {
            LinearLayout l = new LinearLayout(activity); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(host.dp(20), host.dp(10), host.dp(20), 0);
            android.widget.EditText in = new android.widget.EditText(activity); in.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); in.setText(String.valueOf(pm.getMoney())); l.addView(in);
            android.widget.CheckBox cb = new android.widget.CheckBox(activity); cb.setText("Lock Money (Infinite)"); cb.setChecked(true); l.addView(cb);
            new AlertDialog.Builder(activity).setTitle("Edit Money").setView(l).setPositiveButton("Set", (d, w) -> { 
                try { 
                    int val = Integer.parseInt(in.getText().toString()); 
                    pm.setMoney(val); 
                    if (cb.isChecked()) pm.lockMoney(val); else pm.unlockMoney();
                    refreshAll.run(); 
                } catch (Exception ignored) {} 
            }).setNegativeButton("Cancel", null).show();
        }), new LinearLayout.LayoutParams(host.dp(60), host.dp(34))); root.addView(mr);
        TextView dt = new TextView(activity); byte[] raw = pm.readRawMoney(); int rv = (raw != null && raw.length >= 4) ? ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).getInt() : 0;
        dt.setText("Addr: " + String.format("0x%08X", pm.getMoneyAddress()) + " | Key: 0x" + Integer.toHexString(pm.findSecurityKey()) + "\nRaw: " + rv + " | Hex: " + bytesToHex(raw)); dt.setTextColor(Color.GREEN); dt.setTextSize(10f); dt.setTypeface(Typeface.MONOSPACE); root.addView(dt);
        LinearLayout dc = new LinearLayout(activity); dc.setOrientation(LinearLayout.VERTICAL); dc.setPadding(0, host.dp(4), 0, host.dp(8));
        LinearLayout r1 = new LinearLayout(activity); r1.setOrientation(LinearLayout.HORIZONTAL); r1.setPadding(0, 0, 0, host.dp(8));
        r1.addView(makeSystemButton(activity, "Scan Trainer", () -> {
            android.widget.EditText in = new android.widget.EditText(activity); in.setHint("Trainer ID (5 digits)");
            in.setText(host.getSettingsManager().getLastTrainerId());
            new AlertDialog.Builder(activity).setTitle("Locate by Trainer ID").setMessage("Enter your 5-digit Trainer ID from the Trainer Card.").setView(in).setPositiveButton("Scan ID", (d, w) -> {
                String n = in.getText().toString().trim();
                host.getSettingsManager().setLastTrainerId(n);
                if (n.matches("\\d+")) {
                    int f = pm.scanForTrainerID(Integer.parseInt(n));
                    if (f != 0) { Toast.makeText(activity, "Found Trainer at 0x" + Integer.toHexString(f), Toast.LENGTH_SHORT).show(); refreshAll.run(); }
                    else Toast.makeText(activity, "ID not found in RAM", Toast.LENGTH_SHORT).show();
                }
            }).setNeutralButton("Scan Name", (d, w) -> {
                String n = in.getText().toString().trim();
                int f = pm.scanForTrainerName(n);
                if (f != 0) { Toast.makeText(activity, "Found Name at 0x" + Integer.toHexString(f), Toast.LENGTH_SHORT).show(); refreshAll.run(); }
                else Toast.makeText(activity, "Name not found", Toast.LENGTH_SHORT).show();
            }).show();
        }), new LinearLayout.LayoutParams(0, host.dp(32), 1f)); dc.addView(r1);
        LinearLayout r2 = new LinearLayout(activity); r2.setOrientation(LinearLayout.HORIZONTAL);
        r2.addView(makeSystemButton(activity, "Fix Security Key", () -> {
            android.widget.EditText in = new android.widget.EditText(activity); in.setHint("Current Money Amount");
            in.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            new AlertDialog.Builder(activity).setTitle("Fix Decryption Key").setMessage("If money/items look wrong, enter your EXACT current money amount to recalculate the Security Key.").setView(in).setPositiveButton("Fix Key", (d, w) -> {
                try {
                    int amount = Integer.parseInt(in.getText().toString().trim());
                    pm.scanByExactMoney(amount);
                    refreshAll.run();
                } catch (Exception ignored) {}
            }).setNegativeButton("Cancel", null).show();
        }), new LinearLayout.LayoutParams(0, host.dp(32), 1f)); r2.addView(new View(activity), new LinearLayout.LayoutParams(host.dp(6), 1));
        r2.addView(makeSystemButton(activity, "Set Version", () -> showPokemonVersionDialog(activity, pm, refreshAll)), new LinearLayout.LayoutParams(0, host.dp(32), 1f)); r2.addView(new View(activity), new LinearLayout.LayoutParams(host.dp(6), 1));
        r2.addView(makeSystemButton(activity, "Auto Locate", () -> { pm.autoLocateByPointers(); refreshAll.run(); }), new LinearLayout.LayoutParams(0, host.dp(32), 1f)); dc.addView(r2); root.addView(dc);
        
        MemoryScanner ms = host.getMemoryScanner();
        android.widget.CheckBox xorCb = new android.widget.CheckBox(activity); xorCb.setText("Memory Scanner XOR Mode (for encrypted values)"); xorCb.setTextColor(Color.LTGRAY); xorCb.setTextSize(12f);
        xorCb.setChecked(ms.isXorMode());
        xorCb.setOnCheckedChangeListener((v, c) -> ms.setXorMode(c, pm.findSecurityKey())); root.addView(xorCb);
    }

    private static void buildCheatsContent(Activity activity, Host host, LinearLayout root, Runnable refreshAll) {
        MemoryScanner ms = host.getMemoryScanner();
        TextView scanTitle = new TextView(activity); scanTitle.setText("Memory Scanner (Cheat Engine)"); scanTitle.setTextColor(Color.CYAN); scanTitle.setTypeface(null, Typeface.BOLD); scanTitle.setTextSize(14f); root.addView(scanTitle);
        LinearLayout scanRow = new LinearLayout(activity); scanRow.setOrientation(LinearLayout.HORIZONTAL); scanRow.setPadding(0, host.dp(8), 0, host.dp(8));
        scanRow.addView(makeSystemButton(activity, "New Scan", () -> showValueInputDialog(activity, "First Scan", val -> { int count = ms.firstScan(val); Toast.makeText(activity, "Found " + count, Toast.LENGTH_SHORT).show(); refreshAll.run(); })), new LinearLayout.LayoutParams(0, host.dp(34), 1f));
        scanRow.addView(new View(activity), new LinearLayout.LayoutParams(host.dp(8), 1));
        scanRow.addView(makeSystemButton(activity, "Next Scan", () -> showValueInputDialog(activity, "Next Scan", val -> { int count = ms.nextScan(val); Toast.makeText(activity, "Filtered to " + count, Toast.LENGTH_SHORT).show(); refreshAll.run(); })), new LinearLayout.LayoutParams(0, host.dp(34), 1f));
        root.addView(scanRow);
        List<Integer> res = ms.getResults();
        if (!res.isEmpty()) {
            TextView rh = new TextView(activity); rh.setText("Results (" + res.size() + "): Click to Copy"); rh.setTextColor(Color.YELLOW); rh.setTextSize(11f); root.addView(rh);
            LinearLayout rl = new LinearLayout(activity); rl.setOrientation(LinearLayout.VERTICAL);
            for (int i = 0; i < Math.min(res.size(), 5); i++) {
                final int addr = res.get(i); TextView ri = new TextView(activity); ri.setText(String.format("0x%08X", addr)); ri.setTextColor(Color.GREEN); ri.setPadding(host.dp(8), host.dp(4), host.dp(8), host.dp(4));
                ri.setOnClickListener(v -> {
                    android.content.ClipboardManager cb = (android.content.ClipboardManager) activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                    cb.setPrimaryClip(android.content.ClipData.newPlainText("Address", String.format("0x%08X", addr)));
                    Toast.makeText(activity, "Copied: 0x" + Integer.toHexString(addr), Toast.LENGTH_SHORT).show();
                }); rl.addView(ri);
            }
            root.addView(rl);
        }
        root.addView(new View(activity), new LinearLayout.LayoutParams(1, host.dp(12)));
        TextView t = new TextView(activity); t.setText("Standard Cheats"); t.setTextColor(Color.WHITE); t.setTypeface(null, Typeface.BOLD); t.setTextSize(14f); t.setPadding(0, host.dp(12), 0, host.dp(8)); root.addView(t);
        ScrollView s = new ScrollView(activity); LinearLayout l = new LinearLayout(activity); l.setOrientation(LinearLayout.VERTICAL); s.addView(l); root.addView(s, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        final Runnable rlr[] = new Runnable[1];
        Runnable rl = () -> {
            l.removeAllViews(); CheatManager cm = host.getCheatManager(); if (cm == null) return; List<CheatEntry> chs = cm.getCheats();
            if (chs.isEmpty()) { TextView e = new TextView(activity); e.setText("No cheats yet."); e.setTextColor(Color.GRAY); e.setGravity(Gravity.CENTER); l.addView(e); }
            else for (int i = 0; i < chs.size(); i++) {
                final int idx = i; CheatEntry en = chs.get(idx);
                LinearLayout row = new LinearLayout(activity); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(0, host.dp(8), 0, host.dp(8));
                android.widget.CheckBox cb = new android.widget.CheckBox(activity); cb.setChecked(en.enabled); cb.setOnCheckedChangeListener((v, c) -> cm.toggleCheat(idx)); row.addView(cb);
                LinearLayout tc = new LinearLayout(activity); tc.setOrientation(LinearLayout.VERTICAL);
                TextView n = new TextView(activity); n.setText(en.name); n.setTextColor(Color.WHITE); tc.addView(n);
                TextView c = new TextView(activity); c.setText(en.code); c.setTextColor(Color.LTGRAY); c.setTextSize(11f); tc.addView(c);
                row.addView(tc, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                tc.setOnClickListener(v -> showEditCheatDialog(activity, host, idx, en, rlr[0]));
                TextView del = new TextView(activity); del.setText("\u2715"); del.setTextColor(Color.RED); del.setPadding(host.dp(12), 0, host.dp(12), 0);
                del.setOnClickListener(v -> { cm.removeCheat(idx); rlr[0].run(); }); row.addView(del); l.addView(row);
            }
        };
        rlr[0] = rl; rl.run();
        TextView ab = makeSystemButton(activity, "Add New Cheat", () -> showAddCheatDialog(activity, host, rl)); root.addView(ab, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.dp(44)));
    }

    private interface ValueCallback { void onValue(int val); }
    private static void showValueInputDialog(Activity a, String title, ValueCallback cb) {
        android.widget.EditText in = new android.widget.EditText(a); in.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        new AlertDialog.Builder(a).setTitle(title).setView(in).setPositiveButton("OK", (d, w) -> { try { cb.onValue(Integer.parseInt(in.getText().toString())); } catch (Exception ignored) {} }).setNegativeButton("Cancel", null).show();
    }

    private static void showPokemonVersionDialog(Activity activity, PokemonManager pm, Runnable refreshAll) {
    String[] labels = {
            "Auto Detect",
            "FireRed",
            "LeafGreen",
            "Ruby",
            "Sapphire",
            "Emerald / Glazed"
    };

    PokemonConstants.GameVersion[] versions = {
            PokemonConstants.GameVersion.UNKNOWN,
            PokemonConstants.GameVersion.FIRE_RED,
            PokemonConstants.GameVersion.LEAF_GREEN,
            PokemonConstants.GameVersion.RUBY,
            PokemonConstants.GameVersion.SAPPHIRE,
            PokemonConstants.GameVersion.EMERALD
    };

    new AlertDialog.Builder(activity)
            .setTitle("Set Pokémon Version")
            .setItems(labels, (dialog, which) -> {
                PokemonConstants.GameVersion selected = versions[which];

                if (selected == PokemonConstants.GameVersion.UNKNOWN) {
                    pm.clearManualVersion();
                    Toast.makeText(activity, "Version set to Auto Detect", Toast.LENGTH_SHORT).show();
                } else {
                    pm.setVersion(selected);
                    Toast.makeText(activity, "Version set to " + selected, Toast.LENGTH_SHORT).show();
                }

                refreshAll.run();
            })
            .setNegativeButton("Cancel", null)
            .show();
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

    private static void showAddCheatDialog(Activity a, Host h, Runnable o) { showCheatEditorDialog(a, h, -1, null, o); }
    private static void showEditCheatDialog(Activity a, Host h, int i, CheatEntry e, Runnable o) { showCheatEditorDialog(a, h, i, e, o); }
    private static void showCheatEditorDialog(Activity a, Host h, int idx, CheatEntry e, Runnable o) {
        boolean isE = (e != null); LinearLayout l = new LinearLayout(a); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(h.dp(20), h.dp(20), h.dp(20), h.dp(20));
        android.widget.EditText ni = new android.widget.EditText(a); ni.setHint("Name"); if (isE) ni.setText(e.name); l.addView(ni);
        android.widget.EditText ci = new android.widget.EditText(a); ci.setHint("Code"); if (isE) ci.setText(e.code); l.addView(ci);
        new AlertDialog.Builder(a).setTitle(isE ? "Edit Cheat" : "Add Cheat").setView(l).setPositiveButton(isE ? "Save" : "Add", (d, w) -> {
            String n = ni.getText().toString().trim(), c = ci.getText().toString().trim();
            if (!c.isEmpty()) { if (n.isEmpty()) n = "Unnamed"; if (isE) h.getCheatManager().updateCheat(idx, n, c); else h.getCheatManager().addCheat(n, c); o.run(); }
        }).setNegativeButton("Cancel", null).show();
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
