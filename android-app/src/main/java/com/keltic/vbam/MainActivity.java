package com.krendstudio.krendbuoy;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final int REQUEST_OPEN_SAVE_FOLDER = 1002;
    private static final int AUDIO_DYNAMIC_VALUE = -1;
    private static final int AUDIO_DYNAMIC_VIEW_ID = 100000;

    private TextView statusView;
    private TextView romView;
    private TextView saveFolderView;
    private Button openRomButton;
    private Button startButton;
    private Uri selectedRomUri;
    private Uri selectedSaveFolderUri;
    private int selectedAudioBacklogSamples = AUDIO_DYNAMIC_VALUE;
    private boolean coreLoaded;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        coreLoaded = false;
        String status;
        try {
            System.loadLibrary("vbam_libretro");
            coreLoaded = true;
            status = "Core loaded successfully";
        } catch (Throwable t) {
            status = "Core library load failed: " + t.getMessage();
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(48, 96, 48, 48);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        TextView title = new TextView(this);
        title.setText("KrendBuoy");
        title.setTextSize(28f);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        statusView = new TextView(this);
        statusView.setText(status);
        statusView.setTextSize(16f);
        statusView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        statusParams.setMargins(0, 32, 0, 24);
        root.addView(statusView, statusParams);

        TextView audioTitle = new TextView(this);
        audioTitle.setText("Audio latency preset");
        audioTitle.setTextSize(16f);
        audioTitle.setGravity(Gravity.CENTER);
        root.addView(audioTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        RadioGroup audioGroup = new RadioGroup(this);
        audioGroup.setOrientation(RadioGroup.VERTICAL);

        RadioButton presetDynamic = new RadioButton(this);
        presetDynamic.setText("Dynamic - recommended");
        presetDynamic.setId(AUDIO_DYNAMIC_VIEW_ID);
        audioGroup.addView(presetDynamic);

        RadioButton preset1024 = new RadioButton(this);
        preset1024.setText("1024 - ultra low latency, may crackle");
        preset1024.setId(1024);
        audioGroup.addView(preset1024);

        RadioButton preset2048 = new RadioButton(this);
        preset2048.setText("2048 - low latency");
        preset2048.setId(2048);
        audioGroup.addView(preset2048);

        RadioButton preset4096 = new RadioButton(this);
        preset4096.setText("4096 - balanced");
        preset4096.setId(4096);
        audioGroup.addView(preset4096);

        audioGroup.check(AUDIO_DYNAMIC_VIEW_ID);
        audioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            selectedAudioBacklogSamples = checkedId == AUDIO_DYNAMIC_VIEW_ID ? AUDIO_DYNAMIC_VALUE : checkedId;
        });
        LinearLayout.LayoutParams audioParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        audioParams.setMargins(0, 8, 0, 24);
        root.addView(audioGroup, audioParams);

        Button saveFolderButton = new Button(this);
        saveFolderButton.setText("Choose Save / ROM Folder");
        saveFolderButton.setOnClickListener(v -> openSaveFolderPicker());
        root.addView(saveFolderButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        saveFolderView = new TextView(this);
        saveFolderView.setText("No folder selected. Choose a folder before selecting ROM.");
        saveFolderView.setTextSize(14f);
        saveFolderView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        saveParams.setMargins(0, 12, 0, 24);
        root.addView(saveFolderView, saveParams);

        openRomButton = new Button(this);
        openRomButton.setText("Choose ROM from selected folder");
        openRomButton.setEnabled(false);
        openRomButton.setOnClickListener(v -> showRomPickerFromSaveFolder());
        root.addView(openRomButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        romView = new TextView(this);
        romView.setText("No ROM selected. Supported files in selected folder: .gba, .gb, .gbc");
        romView.setTextSize(15f);
        romView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams romParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        romParams.setMargins(0, 32, 0, 32);
        root.addView(romView, romParams);

        startButton = new Button(this);
        startButton.setText("Start Game");
        startButton.setEnabled(false);
        startButton.setOnClickListener(v -> startSelectedGame());
        root.addView(startButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        setContentView(root);
    }

    private void openSaveFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_OPEN_SAVE_FOLDER);
    }

    private void showRomPickerFromSaveFolder() {
        if (selectedSaveFolderUri == null) {
            showNotice("Folder Required", "Choose a Save / ROM folder before selecting a ROM.");
            return;
        }

        ArrayList<String> names = new ArrayList<>();
        ArrayList<Uri> uris = new ArrayList<>();
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
                        names.add(name);
                        uris.add(DocumentsContract.buildDocumentUriUsingTree(selectedSaveFolderUri, documentId));
                    }
                }
            }
        } catch (Throwable t) {
            showNotice("ROM List Failed", t.getMessage() == null ? "Could not list selected folder." : t.getMessage());
            return;
        }

        if (names.isEmpty()) {
            showNotice("No ROM Found", "No .gba, .gbc, or .gb files were found in the selected folder.");
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Choose ROM")
                .setItems(names.toArray(new String[0]), (dialog, which) -> {
                    selectedRomUri = uris.get(which);
                    romView.setText("Selected ROM:\n" + names.get(which));
                    startButton.setEnabled(true);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startSelectedGame() {
        if (selectedSaveFolderUri == null) {
            showNotice("Folder Required", "Choose a Save / ROM folder before starting a game.");
            return;
        }
        if (selectedRomUri == null) {
            showNotice("ROM Required", "Choose a ROM from the selected folder first.");
            return;
        }
        Intent intent = new Intent(this, GameActivity.class);
        intent.setData(selectedRomUri);
        intent.putExtra("audio_backlog_samples", selectedAudioBacklogSamples);
        intent.putExtra("save_folder_uri", selectedSaveFolderUri.toString());
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivity(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        if (requestCode == REQUEST_OPEN_SAVE_FOLDER) {
            selectedSaveFolderUri = data.getData();
            getContentResolver().takePersistableUriPermission(
                    selectedSaveFolderUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            );
            selectedRomUri = null;
            saveFolderView.setText("Folder selected:\n" + selectedSaveFolderUri + "\nROM must be selected from this folder.");
            romView.setText("No ROM selected. Supported files in selected folder: .gba, .gb, .gbc");
            openRomButton.setEnabled(coreLoaded);
            startButton.setEnabled(false);
        }
    }

    private boolean isSupportedRomName(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        return lower.endsWith(".gba") || lower.endsWith(".gbc") || lower.endsWith(".gb");
    }

    private void showNotice(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private String getDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        } catch (Throwable ignored) {
        }
        return uri.toString();
    }
}
