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
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int REQUEST_OPEN_SAVE_FOLDER = 1002;
    private static final int AUDIO_DYNAMIC_VIEW_ID = 100000;
    private static final String PREFS = "krendbuoy_prefs";
    private static final String KEY_FOLDER_URI = "folder_uri";
    private static final String KEY_SORT_MODE = "sort_mode";
    private static final String KEY_FAVORITES = "favorite_roms";
    private static final String KEY_LAST_PLAYED_PREFIX = "last_played_";
    private static final String STATE_FOLDER_NAME = "KrendBuoy States";

    private static final int SORT_LAST_PLAYED = 0;
    private static final int SORT_NAME_ASC = 1;
    private static final int SORT_NAME_DESC = 2;
    private static final int SORT_STATE_COUNT = 3;
    private static final int SORT_RECENTLY_ADDED = 4;

    private SharedPreferences prefs;
    private AppSettingsManager settingsManager;
    private LinearLayout root;
    private LinearLayout topBar;
    private FrameLayout pageContainer;
    private LinearLayout gamesPage;
    private LinearLayout settingsPage;
    private LinearLayout romList;
    private TextView folderPathView;
    private TextView emptyView;
    private View gamesTab;
    private View settingsTab;
    private Uri selectedSaveFolderUri;
    private int sortMode = SORT_LAST_PLAYED;
    private boolean coreLoaded;
    private String coreStatus;

    private static class RomEntry {
        String name;
        String baseName;
        Uri uri;
        boolean hasSav;
        int stateCount;
        long lastPlayed;
        long lastModified;
        boolean favorite;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        settingsManager = new AppSettingsManager(this);
        sortMode = prefs.getInt(KEY_SORT_MODE, SORT_LAST_PLAYED);
        String savedFolder = prefs.getString(KEY_FOLDER_URI, null);
        if (savedFolder != null && !savedFolder.isEmpty()) selectedSaveFolderUri = Uri.parse(savedFolder);
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
        root.setBackgroundColor(Color.rgb(20, 24, 28)); // Match Game Panel background
        root.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        buildTopBar();
        root.addView(topBar);
        pageContainer = new FrameLayout(this);
        root.addView(pageContainer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        buildGamesPage();
        buildSettingsPage();
        buildBottomTabs();
        setContentView(root);
    }

    private void buildTopBar() {
        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(16), dp(16), dp(16), dp(12));
        topBar.setBackgroundColor(Color.rgb(20, 24, 28));
        
        TextView title = new TextView(this);
        title.setText("KrendBuoy");
        title.setTextColor(Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextSize(20f);
        topBar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        
        addTopBarIcon("\u21BB", this::refreshRomList); // Refresh icon
        addTopBarIcon("\u21C5", this::showSortDialog); // Sort/Arrows icon
    }

    private void addTopBarIcon(String icon, Runnable action) {
        TextView view = new TextView(this);
        view.setText(icon);
        view.setTextSize(22f);
        view.setTextColor(Color.WHITE);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(8), 0, dp(8), 0);
        view.setOnClickListener(v -> action.run());
        topBar.addView(view, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void buildGamesPage() {
        ScrollView scroll = new ScrollView(this);
        gamesPage = new LinearLayout(this);
        gamesPage.setOrientation(LinearLayout.VERTICAL);
        gamesPage.setPadding(dp(12), dp(12), dp(12), dp(12));
        scroll.addView(gamesPage, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
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
        gamesPage.addView(romList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        pageContainer.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void buildSettingsPage() {
        ScrollView scroll = new ScrollView(this);
        settingsPage = new LinearLayout(this);
        settingsPage.setOrientation(LinearLayout.VERTICAL);
        settingsPage.setPadding(dp(16), dp(16), dp(16), dp(16));
        scroll.addView(settingsPage, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Display Settings
        settingsPage.addView(sectionTitle("Display"));
        settingsPage.addView(makeSettingButton("Screen Scaling", () -> {
            String[] labels = {"Fit Screen", "Original Ratio", "Stretch", "Pixel Perfect (2x)"};
            new AlertDialog.Builder(this)
                    .setTitle("Screen Scaling")
                    .setSingleChoiceItems(labels, settingsManager.getDisplayMode(), (dialog, which) -> {
                        settingsManager.setDisplayMode(which);
                        dialog.dismiss();
                    }).show();
        }), blockParams(0, 8, 0, 12));

        settingsPage.addView(makeSettingButton("Screen Brightness", () -> {
            String[] labels = {"Brightest (100%)", "Bright (80%)", "Medium (60%)", "Dim (40%)"};
            new AlertDialog.Builder(this)
                    .setTitle("Screen Brightness")
                    .setSingleChoiceItems(labels, settingsManager.getBgDimmingLevel(), (dialog, which) -> {
                        settingsManager.setBgDimmingLevel(which);
                        dialog.dismiss();
                    }).show();
        }), blockParams(0, 0, 0, 12));

        settingsPage.addView(makeSettingToggle("Color Correction", settingsManager.isColorCorrectionEnabled(), settingsManager::setColorCorrectionEnabled), blockParams(0, 0, 0, 12));
        settingsPage.addView(makeSettingToggle("Screen Border", settingsManager.isScreenBorderEnabled(), settingsManager::setScreenBorderEnabled), blockParams(0, 0, 0, 12));
        settingsPage.addView(makeSettingToggle("Show Debug Info (FPS)", settingsManager.isDebugTextVisible(), settingsManager::setDebugTextVisible), blockParams(0, 0, 0, 24));

        // Audio Settings
        settingsPage.addView(sectionTitle("Audio Preset"));
        RadioGroup audioGroup = new RadioGroup(this);
        audioGroup.setOrientation(RadioGroup.VERTICAL);
        audioGroup.setBackground(makeRoundRect(Color.rgb(28, 39, 58), dp(10)));
        audioGroup.setPadding(dp(12), dp(8), dp(12), dp(8));
        addAudioOption(audioGroup, AUDIO_DYNAMIC_VIEW_ID, "Dynamic - recommended");
        addAudioOption(audioGroup, 1024, "1024 - ultra low latency, may crackle");
        addAudioOption(audioGroup, 2048, "2048 - low latency");
        addAudioOption(audioGroup, 4096, "4096 - balanced");
        
        int currentAudio = settingsManager.getAudioPreset();
        audioGroup.check(currentAudio == AppSettingsManager.AUDIO_DYNAMIC ? AUDIO_DYNAMIC_VIEW_ID : currentAudio);
        audioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int val = checkedId == AUDIO_DYNAMIC_VIEW_ID ? AppSettingsManager.AUDIO_DYNAMIC : checkedId;
            settingsManager.setAudioPreset(val);
        });
        settingsPage.addView(audioGroup, blockParams(0, 8, 0, 24));

        settingsPage.addView(sectionTitle("Core Status"));
        settingsPage.addView(bodyCard(coreStatus), blockParams(0, 8, 0, 24));
        settingsPage.addView(sectionTitle("About"));
        settingsPage.addView(bodyCard("KrendBuoy Android test build\nPortable .sav and multi-slot save states are stored in the selected folder."), blockParams(0, 8, 0, 24));
        scroll.setVisibility(View.GONE);
        pageContainer.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        settingsPage.setTag(scroll);
    }

    private View makeSettingButton(String title, Runnable action) {
        Button btn = new Button(this);
        btn.setText(title);
        btn.setAllCaps(false);
        btn.setOnClickListener(v -> action.run());
        return btn;
    }

    private View makeSettingToggle(String title, boolean initial, java.util.function.Consumer<Boolean> onToggle) {
        CheckBox cb = new CheckBox(this);
        cb.setText(title);
        cb.setChecked(initial);
        cb.setTextColor(Color.WHITE);
        cb.setOnCheckedChangeListener((v, checked) -> onToggle.accept(checked));
        return cb;
    }

    private void addAudioOption(RadioGroup group, int id, String label) {
        RadioButton button = new RadioButton(this);
        button.setId(id);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15f);
        group.addView(button);
    }

    private void buildBottomTabs() {
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(0, dp(8), 0, dp(8));
        tabs.setBackgroundColor(Color.rgb(20, 24, 28));
        
        int btnW = getResources().getDisplayMetrics().widthPixels / 2;
        
        gamesTab = addNavButton(tabs, "GAMES", "\uD83C\uDFAE", btnW, this::showGamesPage);
        settingsTab = addNavButton(tabs, "SETTINGS", "\u2699\uFE0F", btnW, this::showSettingsPage);
        
        root.addView(tabs);
    }

    private View addNavButton(LinearLayout parent, String text, String icon, int width, Runnable action) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        
        TextView iconView = new TextView(this);
        iconView.setText(icon);
        iconView.setTextSize(20f);
        iconView.setGravity(Gravity.CENTER);
        layout.addView(iconView);
        
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextSize(10f);
        textView.setGravity(Gravity.CENTER);
        layout.addView(textView);
        
        parent.addView(layout, new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT));
        layout.setOnClickListener(v -> action.run());
        
        // Tag the layout to easily find it for coloring
        layout.setTag(textView);
        return (View) layout;
    }

    private void showGamesPage() {
        if (pageContainer == null) return;
        pageContainer.getChildAt(0).setVisibility(View.VISIBLE);
        pageContainer.getChildAt(1).setVisibility(View.GONE);
        updateNavColor(0);
    }

    private void showSettingsPage() {
        pageContainer.getChildAt(0).setVisibility(View.GONE);
        pageContainer.getChildAt(1).setVisibility(View.VISIBLE);
        updateNavColor(1);
    }

    private void updateNavColor(int index) {
        int blue = Color.rgb(61, 155, 235);
        int gray = Color.GRAY;
        
        setNavGroupColor((ViewGroup) gamesTab, index == 0 ? blue : gray);
        setNavGroupColor((ViewGroup) settingsTab, index == 1 ? blue : gray);
    }

    private void setNavGroupColor(ViewGroup group, int color) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View v = group.getChildAt(i);
            if (v instanceof TextView) ((TextView) v).setTextColor(color);
        }
    }

    private void showSortDialog() {
        String[] labels = {"Last Played", "Name A → Z", "Name Z → A", "Save State Count", "Recently Added"};
        new AlertDialog.Builder(this).setTitle("Sort ROM List").setSingleChoiceItems(labels, sortMode, (dialog, which) -> {
            sortMode = which;
            prefs.edit().putInt(KEY_SORT_MODE, sortMode).apply();
            dialog.dismiss();
            refreshRomList();
        }).setNegativeButton("Cancel", null).show();
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
        for (RomEntry entry : entries) romList.addView(makeRomCard(entry), blockParams(0, 0, 0, 10));
    }

    private ArrayList<RomEntry> scanRomEntries() {
        ArrayList<RomEntry> entries = new ArrayList<>();
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(selectedSaveFolderUri, DocumentsContract.getTreeDocumentId(selectedSaveFolderUri));
        try (Cursor cursor = getContentResolver().query(childrenUri, new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_LAST_MODIFIED}, null, null, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String name = cursor.getString(0);
                    String documentId = cursor.getString(1);
                    long modified = 0;
                    try { modified = cursor.getLong(2); } catch (Throwable ignored) {}
                    if (isSupportedRomName(name)) {
                        RomEntry entry = new RomEntry();
                        entry.name = name;
                        entry.baseName = removeKnownRomExtension(name);
                        entry.uri = DocumentsContract.buildDocumentUriUsingTree(selectedSaveFolderUri, documentId);
                        entry.hasSav = hasRootSaveFile(entry.baseName);
                        entry.stateCount = countStateFiles(entry.baseName);
                        entry.lastPlayed = prefs.getLong(lastPlayedKey(entry.baseName), 0);
                        entry.lastModified = modified;
                        entry.favorite = isFavorite(entry.baseName);
                        entries.add(entry);
                    }
                }
            }
        } catch (Throwable t) {
            showNotice("ROM List Failed", t.getMessage() == null ? "Could not list selected folder." : t.getMessage());
        }
        sortRomEntries(entries);
        return entries;
    }

    private void sortRomEntries(ArrayList<RomEntry> entries) {
        Collections.sort(entries, (a, b) -> {
            if (a.favorite != b.favorite) return a.favorite ? -1 : 1;
            int result;
            if (sortMode == SORT_NAME_ASC) result = a.name.compareToIgnoreCase(b.name);
            else if (sortMode == SORT_NAME_DESC) result = b.name.compareToIgnoreCase(a.name);
            else if (sortMode == SORT_STATE_COUNT) {
                result = Integer.compare(b.stateCount, a.stateCount);
                if (result == 0) result = a.name.compareToIgnoreCase(b.name);
            } else if (sortMode == SORT_RECENTLY_ADDED) {
                result = Long.compare(b.lastModified, a.lastModified);
                if (result == 0) result = a.name.compareToIgnoreCase(b.name);
            } else {
                result = Long.compare(b.lastPlayed, a.lastPlayed);
                if (result == 0) result = a.name.compareToIgnoreCase(b.name);
            }
            return result;
        });
    }

    private View makeRomCard(RomEntry entry) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(makeRoundRect(entry.favorite ? Color.rgb(35, 50, 74) : Color.rgb(25, 36, 58), dp(12)));
        card.setOnClickListener(v -> startGame(entry.uri, entry.baseName, 0));
        card.setOnLongClickListener(v -> { showRomActions(entry); return true; });
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(header);
        TextView name = new TextView(this);
        name.setText((entry.favorite ? "★ " : "") + entry.name);
        name.setTextColor(Color.WHITE);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextSize(17f);
        header.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView more = new TextView(this);
        more.setText("⋮");
        more.setTextColor(Color.rgb(205, 213, 225));
        more.setTextSize(24f);
        more.setGravity(Gravity.CENTER);
        more.setOnClickListener(v -> showRomActions(entry));
        header.addView(more, new LinearLayout.LayoutParams(dp(36), dp(36)));
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.LEFT);
        chips.setPadding(0, dp(8), 0, 0);
        chips.addView(chip(entry.hasSav ? "SAV FOUND" : "NO SAV", entry.hasSav));
        chips.addView(chip(entry.stateCount > 0 ? "STATE x" + entry.stateCount : "NO STATE", entry.stateCount > 0));
        if (entry.favorite) chips.addView(chip("PINNED", true));
        card.addView(chips);
        TextView meta = new TextView(this);
        meta.setText("Last played: " + (entry.lastPlayed > 0 ? formatTimestamp(entry.lastPlayed) : "Never"));
        meta.setTextColor(Color.rgb(176, 187, 204));
        meta.setTextSize(13f);
        meta.setPadding(0, dp(8), 0, 0);
        card.addView(meta);
        return card;
    }

    private void showRomActions(RomEntry entry) {
        String favoriteLabel = entry.favorite ? "Unpin Favorite" : "Pin Favorite";
        String[] items = {"Launch Game", "Load State Slot", favoriteLabel, "Show File Info", "Refresh Status"};
        new AlertDialog.Builder(this).setTitle(entry.name).setItems(items, (dialog, which) -> {
            if (which == 0) startGame(entry.uri, entry.baseName, 0);
            else if (which == 1) showLoadStateSlotDialog(entry);
            else if (which == 2) { toggleFavorite(entry.baseName); refreshRomList(); }
            else if (which == 3) showRomFileInfo(entry);
            else if (which == 4) refreshRomList();
        }).show();
    }

    private void showLoadStateSlotDialog(RomEntry entry) {
        String[] labels = new String[SaveStateManager.SLOT_COUNT];
        for (int slot = 1; slot <= SaveStateManager.SLOT_COUNT; slot++) labels[slot - 1] = stateSlotLabel(entry.baseName, slot);
        new AlertDialog.Builder(this).setTitle("Load State Slot").setItems(labels, (dialog, which) -> {
            int slot = which + 1;
            if (!stateSlotExists(entry.baseName, slot)) {
                showNotice("Load State", "No save state found in Slot " + slot + ".");
                return;
            }
            startGame(entry.uri, entry.baseName, slot);
        }).setNegativeButton("Cancel", null).show();
    }

    private String stateSlotLabel(String baseName, int slot) {
        long modified = getStateModifiedTime(baseName, slot);
        if (modified <= 0) return "Slot " + slot + " - Empty";
        return "Slot " + slot + " - " + formatTimestamp(modified);
    }

    private long getStateModifiedTime(String baseName, int slot) {
        Uri stateDir = findRootChild(STATE_FOLDER_NAME);
        if (stateDir == null) return 0;
        String fileName = sanitizeFileName(baseName) + ".slot" + slot + ".state";
        return findChildModifiedTimeIn(stateDir, fileName);
    }

    private String formatTimestamp(long timestamp) {
        return new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(new Date(timestamp));
    }

    private void showRomFileInfo(RomEntry entry) {
        String info = entry.name + "\n\nFavorite: " + (entry.favorite ? "YES" : "NO") + "\nSAV: " + (entry.hasSav ? "FOUND" : "MISSING") + "\nSTATE: " + entry.stateCount + "\nLast played: " + (entry.lastPlayed > 0 ? formatTimestamp(entry.lastPlayed) : "Never") + "\nFolder: " + displayFolderPath(selectedSaveFolderUri);
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
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(8), 0);
        chip.setLayoutParams(lp);
        return chip;
    }

    private boolean hasRootSaveFile(String baseName) { return findRootChild(baseName + ".sav") != null || findRootChild(baseName + ".srm") != null; }
    private int countStateFiles(String baseName) { int count = 0; for (int slot = 1; slot <= SaveStateManager.SLOT_COUNT; slot++) if (stateSlotExists(baseName, slot)) count++; return count; }
    private boolean stateSlotExists(String baseName, int slot) { return getStateModifiedTime(baseName, slot) > 0; }
    private boolean isFavorite(String baseName) { return prefs.getStringSet(KEY_FAVORITES, Collections.emptySet()).contains(sanitizeFileName(baseName)); }

    private void toggleFavorite(String baseName) {
        String key = sanitizeFileName(baseName);
        Set<String> current = prefs.getStringSet(KEY_FAVORITES, Collections.emptySet());
        Set<String> next = new HashSet<>(current);
        if (next.contains(key)) next.remove(key); else next.add(key);
        prefs.edit().putStringSet(KEY_FAVORITES, next).apply();
    }

    private String lastPlayedKey(String baseName) { return KEY_LAST_PLAYED_PREFIX + sanitizeFileName(baseName); }
    private void markLastPlayed(String baseName) { prefs.edit().putLong(lastPlayedKey(baseName), System.currentTimeMillis()).apply(); }

    private Uri findRootChild(String fileName) {
        if (selectedSaveFolderUri == null) return null;
        Uri root = DocumentsContract.buildDocumentUriUsingTree(selectedSaveFolderUri, DocumentsContract.getTreeDocumentId(selectedSaveFolderUri));
        return findChildIn(root, fileName);
    }

    private Uri findChildIn(Uri parentDocumentUri, String fileName) {
        if (selectedSaveFolderUri == null || parentDocumentUri == null) return null;
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(selectedSaveFolderUri, DocumentsContract.getDocumentId(parentDocumentUri));
        try (Cursor cursor = getContentResolver().query(childrenUri, new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID}, null, null, null)) {
            if (cursor == null) return null;
            while (cursor.moveToNext()) {
                String name = cursor.getString(0);
                String documentId = cursor.getString(1);
                if (fileName.equals(name)) return DocumentsContract.buildDocumentUriUsingTree(selectedSaveFolderUri, documentId);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private long findChildModifiedTimeIn(Uri parentDocumentUri, String fileName) {
        if (selectedSaveFolderUri == null || parentDocumentUri == null) return 0;
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(selectedSaveFolderUri, DocumentsContract.getDocumentId(parentDocumentUri));
        try (Cursor cursor = getContentResolver().query(childrenUri, new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_LAST_MODIFIED}, null, null, null)) {
            if (cursor == null) return 0;
            while (cursor.moveToNext()) if (fileName.equals(cursor.getString(0))) return cursor.getLong(1);
        } catch (Throwable ignored) {}
        return 0;
    }

    private void startGame(Uri romUri, String baseName, int loadStateSlot) {
        if (!coreLoaded) { showNotice("Core Unavailable", coreStatus); return; }
        if (selectedSaveFolderUri == null) { showNotice("Folder Required", "Choose a Save / ROM folder before starting a game."); return; }
        markLastPlayed(baseName);
        Intent intent = new Intent(this, GameActivityV2.class);
        intent.setData(romUri);
        intent.putExtra("save_folder_uri", selectedSaveFolderUri.toString());
        intent.putExtra("load_state_slot", loadStateSlot);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivity(intent);
    }

    @Override protected void onResume() { super.onResume(); refreshRomList(); }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == REQUEST_OPEN_SAVE_FOLDER) {
            selectedSaveFolderUri = data.getData();
            int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            getContentResolver().takePersistableUriPermission(selectedSaveFolderUri, flags);
            prefs.edit().putString(KEY_FOLDER_URI, selectedSaveFolderUri.toString()).apply();
            showGamesPage();
            refreshRomList();
        }
    }

    private boolean isSupportedRomName(String name) { if (name == null) return false; String lower = name.toLowerCase(); return lower.endsWith(".gba") || lower.endsWith(".gbc") || lower.endsWith(".gb"); }
    private String removeKnownRomExtension(String name) { if (name == null || name.isEmpty()) return "selected"; String lower = name.toLowerCase(); if (lower.endsWith(".gba") || lower.endsWith(".gbc")) return name.substring(0, name.length() - 4); if (lower.endsWith(".gb")) return name.substring(0, name.length() - 3); return name; }
    private String sanitizeFileName(String input) { if (input == null) return ""; return input.replaceAll("[^a-zA-Z0-9._-]", "_"); }
    private String displayFolderPath(Uri uri) { try { String treeId = DocumentsContract.getTreeDocumentId(uri); if (treeId.startsWith("primary:")) return "/storage/emulated/0/" + treeId.substring("primary:".length()); return treeId; } catch (Throwable ignored) { return uri.toString(); } }
    private TextView sectionTitle(String text) { TextView title = new TextView(this); title.setText(text); title.setTextColor(Color.WHITE); title.setTypeface(Typeface.DEFAULT_BOLD); title.setTextSize(18f); return title; }
    private TextView bodyCard(String text) { TextView card = new TextView(this); card.setText(text); card.setTextColor(Color.rgb(205, 213, 225)); card.setTextSize(14f); card.setPadding(dp(12), dp(12), dp(12), dp(12)); card.setBackground(makeRoundRect(Color.rgb(28, 39, 58), dp(10))); return card; }
    private LinearLayout.LayoutParams blockParams(int left, int top, int right, int bottom) { LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); params.setMargins(dp(left), dp(top), dp(right), dp(bottom)); return params; }
    private GradientDrawable makeRoundRect(int color, int radius) { GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(radius); return drawable; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void showNotice(String title, String message) { new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show(); }
}
