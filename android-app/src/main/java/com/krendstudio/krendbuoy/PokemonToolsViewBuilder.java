package com.krendstudio.krendbuoy;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * Builds the Pokemon tools page used by GameControllerOverlay.
 *
 * This class is intentionally self-contained so GameControllerOverlay can be
 * reduced to page composition and navigation only.
 */
final class PokemonToolsViewBuilder {
    private static PokemonConstants.Pocket sCurrentPocket = PokemonConstants.Pocket.ITEMS;

    interface Host {
        int dp(int value);
        AppSettingsManager getSettingsManager();
        PokemonManager getPokemonManager();
        MemoryScanner getMemoryScanner();
    }

    private PokemonToolsViewBuilder() {}

    static View build(Activity activity, Host host) {
        FrameLayout wrapper = new FrameLayout(activity);
        final Runnable[] refreshRef = new Runnable[1];
        refreshRef[0] = () -> {
            wrapper.removeAllViews();
            ScrollView scroll = new ScrollView(activity);
            LinearLayout root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(host.dp(16), host.dp(16), host.dp(16), host.dp(16));
            buildPokemonContent(activity, host, root, refreshRef[0]);
            scroll.addView(root);
            wrapper.addView(scroll);
        };
        refreshRef[0].run();
        return wrapper;
    }

    private static void buildPokemonContent(Activity activity, Host host, LinearLayout root, Runnable refreshAll) {
        PokemonManager pm = host.getPokemonManager();
        if (pm == null) return;

        AppSettingsManager sm = host.getSettingsManager();
        if (pm.getMoneyAddress() == 0) {
            String lastId = sm.getLastTrainerId();
            if (!lastId.isEmpty()) {
                try {
                    if (lastId.matches("\\d+")) pm.scanForTrainerID(Integer.parseInt(lastId));
                    else pm.scanForTrainerName(lastId);
                } catch (Exception ignored) {}
            } else {
                pm.autoLocateByPointers();
            }
        }

        buildPokemonToolsSection(activity, host, root, refreshAll);
        root.addView(new View(activity), new LinearLayout.LayoutParams(1, host.dp(12)));

        if (pm.getMoneyAddress() != 0) {
            HorizontalScrollView hsv = new HorizontalScrollView(activity);
            hsv.setHorizontalScrollBarEnabled(false);
            LinearLayout tabs = new LinearLayout(activity);
            tabs.setOrientation(LinearLayout.HORIZONTAL);
            tabs.setBackgroundColor(0x33000000);
            tabs.setPadding(host.dp(4), host.dp(4), host.dp(4), host.dp(4));

            String[] pocketNames = {
                    activity.getString(R.string.pokemon_pocket_items),
                    activity.getString(R.string.pokemon_pocket_balls),
                    activity.getString(R.string.pokemon_pocket_key),
                    activity.getString(R.string.pokemon_pocket_tm),
                    activity.getString(R.string.pokemon_pocket_berry)
            };
            PokemonConstants.Pocket[] pocketTypes = {
                    PokemonConstants.Pocket.ITEMS,
                    PokemonConstants.Pocket.BALLS,
                    PokemonConstants.Pocket.KEY_ITEMS,
                    PokemonConstants.Pocket.TM_HM,
                    PokemonConstants.Pocket.BERRIES
            };

            for (int i = 0; i < pocketNames.length; i++) {
                final int idx = i;
                TextView tab = new TextView(activity);
                tab.setText(pocketNames[i]);
                tab.setGravity(Gravity.CENTER);
                tab.setPadding(host.dp(16), host.dp(6), host.dp(16), host.dp(6));
                tab.setTextSize(12f);
                tab.setTypeface(null, sCurrentPocket == pocketTypes[i] ? Typeface.BOLD : Typeface.NORMAL);
                tab.setTextColor(sCurrentPocket == pocketTypes[i] ? Color.CYAN : Color.GRAY);
                tab.setOnClickListener(v -> {
                    sCurrentPocket = pocketTypes[idx];
                    refreshAll.run();
                });
                tabs.addView(tab);
            }
            hsv.addView(tabs);
            root.addView(hsv);

            TextView pocketTitle = new TextView(activity);
            pocketTitle.setText(activity.getString(R.string.pokemon_pocket_format, sCurrentPocket.name()));
            pocketTitle.setTextColor(Color.WHITE);
            pocketTitle.setPadding(0, host.dp(8), 0, host.dp(4));
            pocketTitle.setTextSize(13f);
            root.addView(pocketTitle);

            List<PokemonConstants.ItemSlot> items = pm.getBagItems(sCurrentPocket);
            if (items.isEmpty()) {
                TextView empty = new TextView(activity);
                empty.setText(activity.getString(R.string.pokemon_bag_empty));
                empty.setTextColor(Color.GRAY);
                empty.setPadding(0, host.dp(8), 0, host.dp(8));
                root.addView(empty);
            } else {
                LinearLayout list = new LinearLayout(activity);
                list.setOrientation(LinearLayout.VERTICAL);
                for (PokemonConstants.ItemSlot item : items) {
                    LinearLayout row = new LinearLayout(activity);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(0, host.dp(4), 0, host.dp(4));

                    TextView name = new TextView(activity);
                    name.setText(pm.getItemName(item.id));
                    name.setTextColor(Color.LTGRAY);
                    name.setTextSize(14f);
                    row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                    TextView count = new TextView(activity);
                    count.setText("x" + item.count);
                    count.setTextColor(Color.YELLOW);
                    count.setTextSize(14f);
                    row.addView(count);

                    row.setOnClickListener(v -> {
                        android.widget.EditText in = new android.widget.EditText(activity);
                        in.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
                        in.setText(String.valueOf(item.count));
                        new AlertDialog.Builder(activity)
                                .setTitle(activity.getString(R.string.pokemon_bag_edit_item_format, pm.getItemName(item.id)))
                                .setView(in)
                                .setPositiveButton(activity.getString(R.string.pokemon_bag_set), (d, w) -> {
                                    try {
                                        pm.setBagItem(item.index, item.id, Integer.parseInt(in.getText().toString()), item.pocket);
                                        refreshAll.run();
                                    } catch (Exception ignored) {}
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    });
                    list.addView(row);
                }
                root.addView(list);
            }

            root.addView(makeSystemButton(activity, activity.getString(R.string.pokemon_bag_add_to_pocket_format, sCurrentPocket.name()), () -> showAddItemDialog(activity, pm, refreshAll)),
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.dp(40)));
        } else {
            TextView info = new TextView(activity);
            info.setText(pm.getEffectiveVersion() == PokemonConstants.GameVersion.UNKNOWN
                    ? activity.getString(R.string.pokemon_trainer_locate_locate_first)
                    : activity.getString(R.string.pokemon_trainer_locate_trainer_first));
            info.setTextColor(Color.GRAY);
            root.addView(info);
        }
    }

    private static void buildPokemonToolsSection(Activity activity, Host host, LinearLayout root, Runnable refreshAll) {
        PokemonManager pm = host.getPokemonManager();
        if (pm == null) return;

        TextView title = new TextView(activity);
        title.setText(activity.getString(R.string.pokemon_tools_debugger));
        title.setTextColor(Color.rgb(61, 155, 235));
        title.setTypeface(null, Typeface.BOLD);
        title.setTextSize(14f);
        root.addView(title);

        TextView versionInfo = new TextView(activity);
        PokemonConstants.GameVersion autoVersion = pm.detectVersion();
        PokemonConstants.GameVersion effectiveVersion = pm.getEffectiveVersion();
        String versionText = pm.isManualVersionSelected()
                ? activity.getString(R.string.pokemon_version_manual_format, pm.getManualVersion(), autoVersion)
                : activity.getString(R.string.pokemon_version_auto_format, effectiveVersion);
        if (effectiveVersion == PokemonConstants.GameVersion.UNKNOWN) {
            versionText += "\n" + activity.getString(R.string.pokemon_version_unknown_note);
        }
        versionInfo.setText(versionText);
        versionInfo.setTextColor(effectiveVersion == PokemonConstants.GameVersion.UNKNOWN ? Color.YELLOW : Color.LTGRAY);
        versionInfo.setTextSize(11f);
        versionInfo.setPadding(0, host.dp(4), 0, host.dp(8));
        root.addView(versionInfo);

        LinearLayout moneyRow = new LinearLayout(activity);
        moneyRow.setOrientation(LinearLayout.HORIZONTAL);
        moneyRow.setGravity(Gravity.CENTER_VERTICAL);
        moneyRow.setPadding(0, host.dp(8), 0, host.dp(12));

        TextView moneyView = new TextView(activity);
        int money = pm.getMoney();
        String moneyLabel = pm.isMoneyLocked() 
                ? activity.getString(R.string.pokemon_money_locked_format, money)
                : activity.getString(R.string.pokemon_money_format, (money == -1 ? 0 : money));
        moneyView.setText(moneyLabel);
        moneyView.setTextColor(pm.isMoneyLocked() ? Color.CYAN : Color.YELLOW);
        moneyView.setTextSize(16f);
        moneyRow.addView(moneyView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (pm.isMoneyLocked()) {
            moneyRow.addView(makeSystemButton(activity, activity.getString(R.string.pokemon_money_unlock), () -> {
                pm.unlockMoney();
                refreshAll.run();
            }), new LinearLayout.LayoutParams(host.dp(60), host.dp(34)));
            moneyRow.addView(new View(activity), new LinearLayout.LayoutParams(host.dp(6), 1));
        }

        moneyRow.addView(makeSystemButton(activity, activity.getString(R.string.pokemon_money_edit), () -> showEditMoneyDialog(activity, host, pm, refreshAll)),
                new LinearLayout.LayoutParams(host.dp(60), host.dp(34)));
        root.addView(moneyRow);

        TextView debugText = new TextView(activity);
        byte[] raw = pm.readRawMoney();
        int rawValue = (raw != null && raw.length >= 4) ? ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).getInt() : 0;
        debugText.setText(activity.getString(R.string.pokemon_debug_addr_key_format, 
                pm.getMoneyAddress(), pm.findSecurityKey(), rawValue, bytesToHex(raw)));
        debugText.setTextColor(Color.GREEN);
        debugText.setTextSize(10f);
        debugText.setTypeface(Typeface.MONOSPACE);
        root.addView(debugText);

        LinearLayout controls = new LinearLayout(activity);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(0, host.dp(4), 0, host.dp(8));

        LinearLayout row1 = new LinearLayout(activity);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setPadding(0, 0, 0, host.dp(8));
        row1.addView(makeSystemButton(activity, activity.getString(R.string.pokemon_tool_trainer_id_scan), () -> showScanTrainerDialog(activity, host, pm, refreshAll)),
                new LinearLayout.LayoutParams(0, host.dp(32), 1f));
        controls.addView(row1);

        LinearLayout row2 = new LinearLayout(activity);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(makeSystemButton(activity, activity.getString(R.string.pokemon_tool_fix_security_key), () -> showFixSecurityKeyDialog(activity, pm, refreshAll)),
                new LinearLayout.LayoutParams(0, host.dp(32), 1f));
        row2.addView(new View(activity), new LinearLayout.LayoutParams(host.dp(6), 1));
        row2.addView(makeSystemButton(activity, activity.getString(R.string.pokemon_tool_set_version), () -> showPokemonVersionDialog(activity, pm, refreshAll)),
                new LinearLayout.LayoutParams(0, host.dp(32), 1f));
        row2.addView(new View(activity), new LinearLayout.LayoutParams(host.dp(6), 1));
        row2.addView(makeSystemButton(activity, activity.getString(R.string.pokemon_tool_auto_locate), () -> {
            pm.autoLocateByPointers();
            refreshAll.run();
        }), new LinearLayout.LayoutParams(0, host.dp(32), 1f));
        controls.addView(row2);
        root.addView(controls);

        MemoryScanner ms = host.getMemoryScanner();
        CheckBox xorCb = new CheckBox(activity);
        xorCb.setText(activity.getString(R.string.pokemon_xor_mode));
        xorCb.setTextColor(Color.LTGRAY);
        xorCb.setTextSize(12f);
        xorCb.setChecked(ms != null && ms.isXorMode());
        xorCb.setOnCheckedChangeListener((v, checked) -> {
            if (ms != null) ms.setXorMode(checked, pm.findSecurityKey());
        });
        root.addView(xorCb);
    }

    private static void showEditMoneyDialog(Activity activity, Host host, PokemonManager pm, Runnable refreshAll) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(host.dp(20), host.dp(10), host.dp(20), 0);

        android.widget.EditText input = new android.widget.EditText(activity);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(pm.getMoney()));
        layout.addView(input);

        CheckBox lock = new CheckBox(activity);
        lock.setText(activity.getString(R.string.pokemon_lock_money_infinite));
        lock.setChecked(true);
        layout.addView(lock);

        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.pokemon_dialog_edit_money))
                .setView(layout)
                .setPositiveButton(activity.getString(R.string.pokemon_bag_set), (d, w) -> {
                    try {
                        int value = Integer.parseInt(input.getText().toString());
                        pm.setMoney(value);
                        if (lock.isChecked()) pm.lockMoney(value);
                        else pm.unlockMoney();
                        refreshAll.run();
                    } catch (Exception ignored) {}
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void showScanTrainerDialog(Activity activity, Host host, PokemonManager pm, Runnable refreshAll) {
        android.widget.EditText input = new android.widget.EditText(activity);
        input.setHint(activity.getString(R.string.pokemon_dialog_enter_trainer_id));
        input.setText(host.getSettingsManager().getLastTrainerId());
        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.pokemon_dialog_locate_by_id))
                .setMessage(activity.getString(R.string.pokemon_dialog_trainer_id_msg))
                .setView(input)
                .setPositiveButton(activity.getString(R.string.pokemon_dialog_scan_id), (d, w) -> {
                    String value = input.getText().toString().trim();
                    host.getSettingsManager().setLastTrainerId(value);
                    if (value.matches("\\d+")) {
                        int found = pm.scanForTrainerID(Integer.parseInt(value));
                        if (found != 0) {
                            Toast.makeText(activity, activity.getString(R.string.cheat_copied_format, Integer.toHexString(found).toUpperCase()), Toast.LENGTH_SHORT).show();
                            refreshAll.run();
                        } else {
                            Toast.makeText(activity, activity.getString(R.string.pokemon_dialog_id_not_found), Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNeutralButton(activity.getString(R.string.pokemon_dialog_scan_name), (d, w) -> {
                    String value = input.getText().toString().trim();
                    int found = pm.scanForTrainerName(value);
                    if (found != 0) {
                        Toast.makeText(activity, activity.getString(R.string.cheat_copied_format, Integer.toHexString(found).toUpperCase()), Toast.LENGTH_SHORT).show();
                        refreshAll.run();
                    } else {
                        Toast.makeText(activity, activity.getString(R.string.pokemon_dialog_name_not_found), Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private static void showFixSecurityKeyDialog(Activity activity, PokemonManager pm, Runnable refreshAll) {
        android.widget.EditText input = new android.widget.EditText(activity);
        input.setHint(activity.getString(R.string.pokemon_dialog_enter_exact_money));
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.pokemon_dialog_fix_key_title))
                .setMessage(activity.getString(R.string.pokemon_dialog_fix_key_msg))
                .setView(input)
                .setPositiveButton(activity.getString(R.string.pokemon_bag_set), (d, w) -> {
                    try {
                        int amount = Integer.parseInt(input.getText().toString().trim());
                        pm.scanByExactMoney(amount);
                        refreshAll.run();
                    } catch (Exception ignored) {}
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void showAddItemDialog(Activity activity, PokemonManager pm, Runnable refresh) {
        List<PokemonConstants.ItemInfo> common = pm.getCommonItems(sCurrentPocket);
        String[] names = new String[common.size() + 1];
        for (int i = 0; i < common.size(); i++) names[i] = common.get(i).name;
        names[common.size()] = activity.getString(R.string.pokemon_dialog_custom_id);

        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.pokemon_bag_add_to_pocket_format, sCurrentPocket.name()))
                .setItems(names, (d, which) -> {
                    if (which < common.size()) {
                        PokemonConstants.ItemInfo info = common.get(which);
                        showQuantityDialog(activity, pm, info.id, info.name, info.pocket, refresh);
                    } else {
                        android.widget.EditText input = new android.widget.EditText(activity);
                        input.setHint(activity.getString(R.string.pokemon_dialog_item_id_hint));
                        new AlertDialog.Builder(activity)
                                .setTitle(activity.getString(R.string.pokemon_dialog_enter_id))
                                .setView(input)
                                .setPositiveButton(activity.getString(R.string.common_ok), (d2, w2) -> {
                                    try {
                                        String value = input.getText().toString().trim();
                                        int id = value.startsWith("0x") ? Integer.parseInt(value.substring(2), 16) : Integer.parseInt(value);
                                        showQuantityDialog(activity, pm, id, activity.getString(R.string.pokemon_item_name_fallback_format, id), sCurrentPocket, refresh);
                                    } catch (Exception ignored) {}
                                })
                                .show();
                    }
                })
                .show();
    }

    private static void showQuantityDialog(Activity activity, PokemonManager pm, int id, String name, PokemonConstants.Pocket pocket, Runnable refresh) {
        android.widget.EditText input = new android.widget.EditText(activity);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setText("10");
        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.pokemon_dialog_quantity_for_format, name))
                .setView(input)
                .setPositiveButton(activity.getString(R.string.pokemon_dialog_add), (d, w) -> {
                    try {
                        int count = Integer.parseInt(input.getText().toString());
                        if (pm.addBagItem(id, count, pocket)) {
                            Toast.makeText(activity, activity.getString(R.string.pokemon_dialog_added_format, name), Toast.LENGTH_SHORT).show();
                            refresh.run();
                        } else {
                            Toast.makeText(activity, activity.getString(R.string.pokemon_dialog_pocket_full), Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception ignored) {}
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void showPokemonVersionDialog(Activity activity, PokemonManager pm, Runnable refreshAll) {
        String[] labels = {
                activity.getString(R.string.pokemon_version_auto),
                activity.getString(R.string.pokemon_version_firered),
                activity.getString(R.string.pokemon_version_leafgreen),
                activity.getString(R.string.pokemon_version_ruby),
                activity.getString(R.string.pokemon_version_sapphire),
                activity.getString(R.string.pokemon_version_emerald)
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
                .setTitle(activity.getString(R.string.pokemon_dialog_set_version_title))
                .setItems(labels, (dialog, which) -> {
                    PokemonConstants.GameVersion selected = versions[which];
                    if (selected == PokemonConstants.GameVersion.UNKNOWN) {
                        pm.clearManualVersion();
                        Toast.makeText(activity, activity.getString(R.string.pokemon_dialog_version_set_auto), Toast.LENGTH_SHORT).show();
                    } else {
                        pm.setVersion(selected);
                        Toast.makeText(activity, activity.getString(R.string.pokemon_dialog_version_set_format, labels[which]), Toast.LENGTH_SHORT).show();
                    }
                    refreshAll.run();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static TextView makeSystemButton(Activity activity, String label, Runnable action) {
        TextView button = new TextView(activity);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(12f);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(null, Typeface.BOLD);
        button.setBackground(makeRoundRect(Color.rgb(45, 56, 72), 12));
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private static GradientDrawable makeRoundRect(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02X ", b));
        return sb.toString().trim();
    }
}
