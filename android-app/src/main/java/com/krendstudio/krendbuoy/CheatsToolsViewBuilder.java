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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * Builds the Cheats page used by GameControllerOverlay.
 *
 * Keeps memory scanner and standard cheat UI outside the main controller
 * overlay class.
 */
final class CheatsToolsViewBuilder {
    interface Host {
        int dp(int value);
        CheatManager getCheatManager();
        MemoryScanner getMemoryScanner();
    }

    private interface ValueCallback { void onValue(int value); }

    private CheatsToolsViewBuilder() {}

    static View build(Activity activity, Host host) {
        FrameLayout wrapper = new FrameLayout(activity);
        final Runnable[] refreshRef = new Runnable[1];
        refreshRef[0] = () -> {
            wrapper.removeAllViews();
            LinearLayout root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(host.dp(16), host.dp(16), host.dp(16), host.dp(16));
            buildCheatsContent(activity, host, root, refreshRef[0]);
            wrapper.addView(root);
        };
        refreshRef[0].run();
        return wrapper;
    }

    private static void buildCheatsContent(Activity activity, Host host, LinearLayout root, Runnable refreshAll) {
        MemoryScanner scanner = host.getMemoryScanner();

        TextView scanTitle = new TextView(activity);
        scanTitle.setText(activity.getString(R.string.cheat_memory_scanner));
        scanTitle.setTextColor(Color.CYAN);
        scanTitle.setTypeface(null, Typeface.BOLD);
        scanTitle.setTextSize(14f);
        root.addView(scanTitle);

        LinearLayout scanRow = new LinearLayout(activity);
        scanRow.setOrientation(LinearLayout.HORIZONTAL);
        scanRow.setPadding(0, host.dp(8), 0, host.dp(8));
        scanRow.addView(makeSystemButton(activity, activity.getString(R.string.cheat_new_scan), () -> showValueInputDialog(activity, activity.getString(R.string.cheat_first_scan_title), value -> {
            int count = scanner == null ? 0 : scanner.firstScan(value);
            Toast.makeText(activity, activity.getString(R.string.cheat_found_format, count), Toast.LENGTH_SHORT).show();
            refreshAll.run();
        })), new LinearLayout.LayoutParams(0, host.dp(34), 1f));
        scanRow.addView(new View(activity), new LinearLayout.LayoutParams(host.dp(8), 1));
        scanRow.addView(makeSystemButton(activity, activity.getString(R.string.cheat_next_scan), () -> showValueInputDialog(activity, activity.getString(R.string.cheat_next_scan), value -> {
            int count = scanner == null ? 0 : scanner.nextScan(value);
            Toast.makeText(activity, activity.getString(R.string.cheat_filtered_format, count), Toast.LENGTH_SHORT).show();
            refreshAll.run();
        })), new LinearLayout.LayoutParams(0, host.dp(34), 1f));
        root.addView(scanRow);

        List<Integer> results = scanner == null ? java.util.Collections.emptyList() : scanner.getResults();
        if (!results.isEmpty()) {
            TextView resultsHeader = new TextView(activity);
            resultsHeader.setText(activity.getString(R.string.cheat_results_header_format, results.size()));
            resultsHeader.setTextColor(Color.YELLOW);
            resultsHeader.setTextSize(11f);
            root.addView(resultsHeader);

            LinearLayout resultsList = new LinearLayout(activity);
            resultsList.setOrientation(LinearLayout.VERTICAL);
            for (int i = 0; i < Math.min(results.size(), 5); i++) {
                final int addr = results.get(i);
                TextView resultItem = new TextView(activity);
                resultItem.setText(String.format("0x%08X", addr));
                resultItem.setTextColor(Color.GREEN);
                resultItem.setPadding(host.dp(8), host.dp(4), host.dp(8), host.dp(4));
                resultItem.setOnClickListener(v -> {
                    android.content.ClipboardManager cb = (android.content.ClipboardManager) activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                    cb.setPrimaryClip(android.content.ClipData.newPlainText("Address", String.format("0x%08X", addr)));
                    Toast.makeText(activity, activity.getString(R.string.cheat_copied_format, Integer.toHexString(addr).toUpperCase()), Toast.LENGTH_SHORT).show();
                });
                resultsList.addView(resultItem);
            }
            root.addView(resultsList);
        }

        root.addView(new View(activity), new LinearLayout.LayoutParams(1, host.dp(12)));

        TextView title = new TextView(activity);
        title.setText(activity.getString(R.string.cheat_standard_cheats));
        title.setTextColor(Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextSize(14f);
        title.setPadding(0, host.dp(12), 0, host.dp(8));
        root.addView(title);

        ScrollView scroll = new ScrollView(activity);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        final Runnable[] reloadRef = new Runnable[1];
        Runnable reload = () -> {
            list.removeAllViews();
            CheatManager cheatManager = host.getCheatManager();
            if (cheatManager == null) return;
            List<CheatEntry> cheats = cheatManager.getCheats();
            if (cheats.isEmpty()) {
                TextView empty = new TextView(activity);
                empty.setText(activity.getString(R.string.cheat_no_cheats));
                empty.setTextColor(Color.GRAY);
                empty.setGravity(Gravity.CENTER);
                list.addView(empty);
            } else {
                for (int i = 0; i < cheats.size(); i++) {
                    final int idx = i;
                    CheatEntry entry = cheats.get(idx);
                    LinearLayout row = new LinearLayout(activity);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(0, host.dp(8), 0, host.dp(8));

                    CheckBox enabled = new CheckBox(activity);
                    enabled.setChecked(entry.enabled);
                    enabled.setOnCheckedChangeListener((v, checked) -> cheatManager.toggleCheat(idx));
                    row.addView(enabled);

                    LinearLayout textColumn = new LinearLayout(activity);
                    textColumn.setOrientation(LinearLayout.VERTICAL);
                    TextView name = new TextView(activity);
                    name.setText(entry.name);
                    name.setTextColor(Color.WHITE);
                    textColumn.addView(name);
                    TextView code = new TextView(activity);
                    code.setText(entry.code);
                    code.setTextColor(Color.LTGRAY);
                    code.setTextSize(11f);
                    textColumn.addView(code);
                    row.addView(textColumn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                    textColumn.setOnClickListener(v -> showEditCheatDialog(activity, host, idx, entry, reloadRef[0]));

                    TextView delete = new TextView(activity);
                    delete.setText("\u2715");
                    delete.setTextColor(Color.RED);
                    delete.setPadding(host.dp(12), 0, host.dp(12), 0);
                    delete.setOnClickListener(v -> {
                        cheatManager.removeCheat(idx);
                        reloadRef[0].run();
                    });
                    row.addView(delete);
                    list.addView(row);
                }
            }
        };
        reloadRef[0] = reload;
        reload.run();

        TextView addButton = makeSystemButton(activity, activity.getString(R.string.cheat_add_new), () -> showAddCheatDialog(activity, host, reload));
        root.addView(addButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.dp(44)));
    }

    private static void showValueInputDialog(Activity activity, String title, ValueCallback callback) {
        android.widget.EditText input = new android.widget.EditText(activity);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(input)
                .setPositiveButton("OK", (d, w) -> {
                    try {
                        callback.onValue(Integer.parseInt(input.getText().toString()));
                    } catch (Exception ignored) {}
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void showAddCheatDialog(Activity activity, Host host, Runnable onChanged) {
        showCheatEditorDialog(activity, host, -1, null, onChanged);
    }

    private static void showEditCheatDialog(Activity activity, Host host, int index, CheatEntry entry, Runnable onChanged) {
        showCheatEditorDialog(activity, host, index, entry, onChanged);
    }

    private static void showCheatEditorDialog(Activity activity, Host host, int index, CheatEntry entry, Runnable onChanged) {
        boolean isEdit = entry != null;
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(host.dp(20), host.dp(20), host.dp(20), host.dp(20));

        android.widget.EditText nameInput = new android.widget.EditText(activity);
        nameInput.setHint("Name");
        if (isEdit) nameInput.setText(entry.name);
        layout.addView(nameInput);

        android.widget.EditText codeInput = new android.widget.EditText(activity);
        codeInput.setHint("Code");
        if (isEdit) codeInput.setText(entry.code);
        layout.addView(codeInput);

        new AlertDialog.Builder(activity)
                .setTitle(isEdit ? "Edit Cheat" : "Add Cheat")
                .setView(layout)
                .setPositiveButton(isEdit ? "Save" : "Add", (d, w) -> {
                    String name = nameInput.getText().toString().trim();
                    String code = codeInput.getText().toString().trim();
                    CheatManager cheatManager = host.getCheatManager();
                    if (!code.isEmpty() && cheatManager != null) {
                        if (name.isEmpty()) name = "Unnamed";
                        if (isEdit) cheatManager.updateCheat(index, name, code);
                        else cheatManager.addCheat(name, code);
                        onChanged.run();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static TextView makeSystemButton(Activity activity, String label, Runnable action) {
        TextView view = new TextView(activity);
        view.setText(label);
        view.setTextSize(14f);
        view.setTextColor(Color.WHITE);
        view.setGravity(Gravity.CENTER);
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(0xAA333333);
        drawable.setCornerRadius(12);
        view.setBackground(drawable);
        view.setAlpha(0.9f);
        view.setOnClickListener(v -> action.run());
        return view;
    }
}
