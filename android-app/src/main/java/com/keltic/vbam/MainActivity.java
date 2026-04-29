package com.krendstudio.krendbuoy;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class MainActivity extends Activity {
    private static final int REQUEST_OPEN_SAVE_FOLDER = 1002;
    private static final int AUDIO_DYNAMIC_VALUE = -1;
    private static final int AUDIO_DYNAMIC_VIEW_ID = 100000;
    private static final String PREFS = "krendbuoy_prefs";
    private static final String KEY_FOLDER_URI = "folder_uri";
    private static final String KEY_AUDIO_PRESET = "audio_preset";
    private static final String STATE_FOLDER_NAME = "KrendBuoy States";

    private SharedPreferences prefs;
    private LinearLayout root;
    private LinearLayout topBar;
    private FrameLayout pageContainer;
    private LinearLayout gamesPage;
    private LinearLayout settingsPage;
    private LinearLayout romList;
    private TextView folderPathView;
    private TextView emptyView;
    private Button gamesTab;
    private Button settingsTab;
    private Uri selectedSaveFolderUri;
    private int selectedAudioBacklogSamples = AUDIO_DYNAMIC_VALUE;
    private boolean coreLoaded;
    private String coreStatus;

    private static class RomEntry {
        String name;
        String baseName;
        Uri uri;
        boolean hasSav;
        int stateCount;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        selectedAudioBacklogSamples = prefs.getInt(KEY_AUDIO_PRESET, AUDIO_DYNAMIC_VALUE);
        String savedFolder = prefs.getString(KEY_FOLDER_URI, null);
        if (savedFolder != null && !savedFolder.isEmpty()) {
            selectedSaveFolderUri = Uri.parse(savedFolder);
        }

        checkCoreStatus();
        buildRootUi();
        showGamesPage();
        refreshRomList();
    }

    private void checkCoreStatus() {
        coreLoaded = false;
        try {
            System.loadLibrary("vbam_libretro");
            coreLoaded = true;
            coreStatus = "Core loaded successfully";
        } catch (Throwable t) {
            coreStatus = "Core library load failed: " + t.getMessage();
        }
    }

    private void buildRootUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(18, 24, 34));
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        buildTopBar();
        root.addView(topBar);

        pageContainer = new FrameLayout(this);
        root.addView(pageContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        buildGamesPage();
        buildSettingsPage();

        buildBottomTabs();
        setContentView(root);
    }

    private void buildTopBar() {
        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(16), dp(12), dp(16), dp(10));
        topBar.setBackgroundColor(Color.rgb(22, 31, 46));

        TextView title = new TextView(this);
        title.setText("KrendBuoy");
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(22f);
        topBar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button refresh = new Button(this);
        refresh.setText("Refresh");
        refresh.setOnClickListener(v -> refreshRomList());
        topBar.addView(refresh, new LinearLayout.LayoutParams(dp(104), ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void buildGamesPage() {
        ScrollView scroll = new ScrollView(this);
        gamesPage = new LinearLayout(this);
        gamesPage.setOrientation(LinearLayout.VERTICAL);
        gamesPage.setPadding(dp(12), dp(12), dp(12), dp(12));
        scroll.addView(gamesPage, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout folderCard = new LinearLayout(this);
        folderCard.setOrientation(LinearLayout.VERTICAL);
        folderCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        folderCard.setBackground(makeRoundRect(Color.rgb(28, 39, 58), dp(10)));
        gamesPage.addView(folderCard, blockParams(0, 0, 0, 12));

        LinearLayout folderHeader = new LinearLayout(this);
        folderHeader.setOrientation(LinearLayout.HORIZONTAL);
        folderHeader.setGravity(Gravity.CENTER_VERTICAL);
        folderCard.addView(folderHeader);

        LinearLayout folderTextBox = new LinearLayout(this);
        folderTextBox.setOrientation(LinearLayout.VERTICAL);
        folderHeader.addView(folderTextBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView folderTitle = new TextView(this);
        folderTitle.setText("Folder Control");
        folderTitle.setTextColor(Color.WHITE);
        folderTitle.setTypeface(Typeface.DEFAULT_BOLD);
        folderTitle.setTextSize(18f);
        folderTextBox.addView(folderTitle);

        folderPathView = new TextView(this);
        folderPathView.setTextColor(Color.rgb(205, 213, 225));
        folderPathView.setTextSize(14f);
        folderTextBox.addView(folderPathView);

        Button changeFolder = new Button(this);
        changeFolder.setText("Change Folder");
        changeFolder.setOnClickListener(v -> openSaveFolderPicker());
        folderHeader.addView(changeFolder, new LinearLayout.LayoutParams(dp(144), ViewGroup.LayoutParams.WRAP_CONTENT));

        emptyView = new TextView(this);
        emptyView.setTextColor(Color.rgb(205, 213, 225));
        emptyView.setTextSize(15f);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(dp(12), dp(32), dp(12), dp(32));
        gamesPage.addView(emptyView, blockParams(0, 0, 0, 12));

        romList = new LinearLayout(this);
        romList.setOrientation(LinearLayout.VERTICAL);
        gamesPage.addView(romList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        pageContainer.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    private void buildSettingsPage() {
        ScrollView scroll = new ScrollView(this);
        settingsPage = new LinearLayout(this);
        settingsPage.setOrientation(LinearLayout.VERTICAL);
        settingsPage.setPadding(dp(16), dp(16), dp(16), dp(16));
        scroll.addView(settingsPage, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView audioTitle = sectionTitle("Audio Preset");
        settingsPage.addView(audioTitle);

        RadioGroup audioGroup = new RadioGroup(this);
        audioGroup.setOrientation(RadioGroup.VERTICAL);
        audioGroup.setBackground(makeRoundRect(Color.rgb(28, 39, 58), dp(10)));
        audioGroup.setPadding(dp(12), dp(8), dp(12), dp(8));

        addAudioOption(audioGroup, AUDIO_DYNAMIC_VIEW_ID, "Dynamic - recommended");
        addAudioOption(audioGroup, 1024, "1024 - ultra low latency, may crackle");
        addAudioOption(audioGroup, 2048, "2048 - low latency");
        addAudioOption(audioGroup, 4096, "4096 - balanced");
        audioGroup.check(selectedAudioBacklogSamples == AUDIO_DYNAMIC_VALUE ? AUDIO_DYNAMIC_VIEW_ID : selectedAudioBacklogSamples);
        audioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            selectedAudioBacklogSamples = checkedId == AUDIO_DYNAMIC_VIEW_ID ? AUDIO_DYNAMIC_VALUE : checkedId;
            prefs.edit().putInt(KEY_AUDIO_PRESET, selectedAudioBacklogSamples).apply();
        });
        settingsPage.addView(audioGroup, blockParams(0, 8, 0, 24));

        TextView statusTitle = sectionTitle("Core Status");
        settingsPage.addView(statusTitle);
        TextView core = bodyCard(coreStatus);
        settingsPage.addView(core, blockParams(0, 8, 0, 24));

        TextView aboutTitle = sectionTitle("About");
        settingsPage.addView(aboutTitle);
        TextView about = bodyCard("KrendBuoy Android test build\nPortable .sav and multi-slot save states are stored in the selected folder.");
        settingsPage.addView(about, blockParams(0, 8, 0, 24));

        scroll.setVisibility(View.GONE);
        pageContainer.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        settingsPage.setTag(scroll);
    }

    private void buildBottomTabs() {
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(12), dp(8), dp(12), dp(8));
        tabs.setBackgroundColor(Color.rgb(14, 20, 30));

        gamesTab = new Button(this);
        gamesTab.setText("Games");
        gamesTab.setOnClickListener(v -> showGamesPage());
        tabs.addView(gamesTab, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        settingsTab = new Button(this);
        settingsTab.setText("Settings");
        settingsTab.setOnClickListener(v -> showSettingsPage());
        tabs.addView(settingsTab, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        root.addView(tabs);
    }

    private void addAudioOption(RadioGroup group, int id, String label) {
        RadioButton button = new RadioButton(this);
        button.setId(id);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15f);
        group.addView(button);
    }

    private void showGamesPage() {
        if (pageContainer == null) return;
        for (int i = 0; i < pageContainer.getChildCount(); i++) {
            pageContainer.getChildAt(i).setVisibility(i == 0 ? View.VISIBLE : View.GONE);
        }
        if (gamesTab != null) gamesTab.setEnabled(false);
        if (settingsTab != null) settingsTab.setEnabled(true);
    }

    private void showSettingsPage() {
        for (int i = 0; i < pageContainer.getChildCount(); i++) {
            pageContainer.getChildAt(i).setVisibility(i == 1 ? View.VISIBLE : View.GONE);
        }
        gamesTab.setEnabled(true);
        settingsTab.setEnabled(false);
    }

    private void openSaveFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_OPEN_SAVE_FOLDER);
    }

    private void refreshRomList() {
        if (folderPathView == null || romList == null || emptyView == null) return;
        romList.removeAllViews();
        if (selectedSaveFolderUri == null) {
            folderPathView.setText("No folder selected");
            emptyView.setVisibility(View.VISIBLE);
            emptyView.setText("Choose a Save / ROM folder first. ROM files must be inside that folder.");
            return;
        }

        folderPathView.setText(displayFolderPath(selectedSaveFolderUri));
        ArrayList<RomEntry> entries = scanRomEntries();
        if (entries.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            emptyView.setText("No .gba, .gbc, or .gb files found in this folder.");
            return;
        }

        emptyView.setVisibility(View.GONE);
        for (RomEntry entry : entries) {
            romList.addView(makeRomCard(entry), blockParams(0, 0, 0, 10));
        }
    }

    private ArrayList<RomEntry> scanRomEntries() {
        ArrayList<RomEntry> entries = new ArrayList<>();
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                selectedSaveFolderUri,
                DocumentsContract.getTreeDocumentId(selectedSaveFolderUri)
        );

        try (Cursor cursor = getContentResolver().query(
                childrenUri,
                new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID},
                null,
                null,
                null
        )) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String name = cursor.getString(0);
                    String documentId = cursor.getString(1);
                    if (isSupportedRomName(name)) {
                        RomEntry entry = new RomEntry();
                        entry.name = name;
                        entry.baseName = removeKnownRomExtension(name);
                        entry.uri = DocumentsContract.buildDocumentUriUsingTree(selectedSaveFolderUri, documentId);
                        entry.hasSav = hasRootSaveFile(entry.baseName);
                        entry.stateCount = countStateFiles(entry.baseName);
                        entries.add(entry);
                    }
                }
            }
        } catch (Throwable t) {
            showNotice("ROM List Failed", t.getMessage() == null ? "Could not list selected folder." : t.getMessage());
        }

        Collections.sort(entries, Comparator.comparing(a -> a.name.toLowerCase()));
        return entries;
    }

    private View makeRomCard(RomEntry entry) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(makeRoundRect(Color.rgb(25, 36, 58), dp(10)));
        card.setOnClickListener(v -> startGame(entry.uri, 0));
        card.setOnLongClickListener(v -> {
            showRomActions(entry);
            return true;
        });

        TextView name = new TextView(this);
        name.setText(entry.name);
        name.setTextColor(Color.WHITE);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextSize(18f);
        card.addView(name);

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.LEFT);
        chips.setPadding(0, dp(8), 0, 0);
        chips.addView(chip(entry.hasSav ? "SAV FOUND" : "NO SAV", entry.hasSav));
        chips.addView(chip(entry.stateCount > 0 ? "STATE x" + entry.stateCount : "NO STATE", entry.stateCount > 0));
        card.addView(chips);

        return card;
    }

    private void showRomActions(RomEntry entry) {
        String[] items = {"Launch Game", "Load State Slot", "Show File Info", "Refresh Status"};
        new AlertDialog.Builder(this)
                .setTitle(entry.name)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        startGame(entry.uri, 0);
                    } else if (which == 1) {
                        showLoadStateSlotDialog(entry);
                    } else if (which == 2) {
                        showRomFileInfo(entry);
                    } else if (which == 3) {
                        refreshRomList();
                    }
                })
                .show();
    }

    private void showLoadStateSlotDialog(RomEntry entry) {
        String[] labels = new String[5];
        for (int slot = 1; slot <= 5; slot++) {
            labels[slot - 1] = stateSlotExists(entry.baseName, slot) ? "Slot " + slot + " - Available" : "Slot " + slot + " - Empty";
        }
        new AlertDialog.Builder(this)
                .setTitle("Load State Slot")
                .setItems(labels, (dialog, which) -> {
                    int slot = which + 1;
                    if (!stateSlotExists(entry.baseName, slot)) {
                        showNotice("Load State", "No save state found in Slot " + slot + ".");
                        return;
                    }
                    startGame(entry.uri, slot);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showRomFileInfo(RomEntry entry) {
        String info = entry.name
                + "\n\nSAV: " + (entry.hasSav ? "FOUND" : "MISSING")
                + "\nSTATE: " + entry.stateCount
                + "\nFolder: " + displayFolderPath(selectedSaveFolderUri);
        showNotice("File Info", info);
    }

    private TextView chip(String text, boolean positive) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextColor(Color.WHITE);
        chip.setTextSize(12f);
        chip.setTypeface(Typeface.DEFAULT_BOLD);
        chip.setPadding(dp(8), dp(3), dp(8), dp(3));
        chip.setBackground(makeRoundRect(positive ? Color.rgb(46, 125, 50) : Color.rgb(95, 101, 112), dp(6)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, 0, dp(8), 0);
        chip.setLayoutParams(lp);
        return chip;
    }

    private boolean hasRootSaveFile(String baseName) {
        return findRootChild(baseName + ".sav") != null || findRootChild(baseName + ".srm") != null;
    }

    private int countStateFiles(String baseName) {
        int count = 0;
        for (int slot = 1; slot <= 5; slot++) {
            if (stateSlotExists(baseName, slot)) count++;
        }
        return count;
    }

    private boolean stateSlotExists(String baseName, int slot) {
        Uri stateDir = findRootChild(STATE_FOLDER_NAME);
        if (stateDir == null) return false;
        String fileName = sanitizeFileName(baseName) + ".slot" + slot + ".state";
        return findChildIn(stateDir, fileName) != null;
    }

    private Uri findRootChild(String fileName) {
        if (selectedSaveFolderUri == null) return null;
        Uri root = DocumentsContract.buildDocumentUriUsingTree(
                selectedSaveFolderUri,
                DocumentsContract.getTreeDocumentId(selectedSaveFolderUri)
        );
        return findChildIn(root, fileName);
    }

    private Uri findChildIn(Uri parentDocumentUri, String fileName) {
        if (selectedSaveFolderUri == null || parentDocumentUri == null) return null;
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                selectedSaveFolderUri,
                DocumentsContract.getDocumentId(parentDocumentUri)
        );
        try (Cursor cursor = getContentResolver().query(
                childrenUri,
                new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID},
                null,
                null,
                null
        )) {
            if (cursor == null) return null;
            while (cursor.moveToNext()) {
                String name = cursor.getString(0);
                String documentId = cursor.getString(1);
                if (fileName.equals(name)) {
                    return DocumentsContract.buildDocumentUriUsingTree(selectedSaveFolderUri, documentId);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void startGame(Uri romUri, int loadStateSlot) {
        if (!coreLoaded) {
            showNotice("Core Unavailable", coreStatus);
            return;
        }
        if (selectedSaveFolderUri == null) {
            showNotice("Folder Required", "Choose a Save / ROM folder before starting a game.");
            return;
        }
        Intent intent = new Intent(this, GameActivity.class);
        intent.setData(romUri);
        intent.putExtra("audio_backlog_samples", selectedAudioBacklogSamples);
        intent.putExtra("save_folder_uri", selectedSaveFolderUri.toString());
        intent.putExtra("load_state_slot", loadStateSlot);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshRomList();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        if (requestCode == REQUEST_OPEN_SAVE_FOLDER) {
            selectedSaveFolderUri = data.getData();
            int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            getContentResolver().takePersistableUriPermission(selectedSaveFolderUri, flags);
            prefs.edit().putString(KEY_FOLDER_URI, selectedSaveFolderUri.toString()).apply();
            showGamesPage();
            refreshRomList();
        }
    }

    private boolean isSupportedRomName(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        return lower.endsWith(".gba") || lower.endsWith(".gbc") || lower.endsWith(".gb");
    }

    private String removeKnownRomExtension(String name) {
        if (name == null || name.isEmpty()) return "selected";
        String lower = name.toLowerCase();
        if (lower.endsWith(".gba")) return name.substring(0, name.length() - 4);
        if (lower.endsWith(".gbc")) return name.substring(0, name.length() - 4);
        if (lower.endsWith(".gb")) return name.substring(0, name.length() - 3);
        return name;
    }

    private String sanitizeFileName(String input) {
        if (input == null) return "";
        return input.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String displayFolderPath(Uri uri) {
        try {
            String treeId = DocumentsContract.getTreeDocumentId(uri);
            if (treeId.startsWith("primary:")) {
                return "/storage/emulated/0/" + treeId.substring("primary:".length());
            }
            return treeId;
        } catch (Throwable ignored) {
            return uri.toString();
        }
    }

    private TextView sectionTitle(String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(18f);
        return title;
    }

    private TextView bodyCard(String text) {
        TextView card = new TextView(this);
        card.setText(text);
        card.setTextColor(Color.rgb(205, 213, 225));
        card.setTextSize(14f);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackground(makeRoundRect(Color.rgb(28, 39, 58), dp(10)));
        return card;
    }

    private LinearLayout.LayoutParams blockParams(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private GradientDrawable makeRoundRect(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showNotice(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }
}
