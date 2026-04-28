package com.keltic.vbam;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int REQUEST_OPEN_ROM = 1001;
    private static final int AUDIO_DYNAMIC_VALUE = -1;
    private static final int AUDIO_DYNAMIC_VIEW_ID = 100000;

    private TextView statusView;
    private TextView romView;
    private Uri selectedRomUri;
    private int selectedAudioBacklogSamples = AUDIO_DYNAMIC_VALUE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean coreLoaded = false;
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
        title.setText("VBA-M Android");
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

        Button openButton = new Button(this);
        openButton.setText("Choose ROM file");
        openButton.setEnabled(coreLoaded);
        openButton.setOnClickListener(v -> openRomPicker());
        root.addView(openButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        romView = new TextView(this);
        romView.setText("No ROM selected. Supported files: .gba, .gb, .gbc");
        romView.setTextSize(15f);
        romView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams romParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        romParams.setMargins(0, 32, 0, 32);
        root.addView(romView, romParams);

        Button startButton = new Button(this);
        startButton.setText("Start Game");
        startButton.setEnabled(false);
        startButton.setOnClickListener(v -> {
            if (selectedRomUri != null) {
                Intent intent = new Intent(this, GameActivity.class);
                intent.setData(selectedRomUri);
                intent.putExtra("audio_backlog_samples", selectedAudioBacklogSamples);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(intent);
            }
        });
        root.addView(startButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        romView.setTag(startButton);
        setContentView(root);
    }

    private void openRomPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimeTypes = {
                "application/octet-stream",
                "application/x-gba-rom",
                "application/x-gameboy-rom",
                "application/x-gameboy-color-rom"
        };
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        startActivityForResult(intent, REQUEST_OPEN_ROM);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OPEN_ROM && resultCode == RESULT_OK && data != null && data.getData() != null) {
            selectedRomUri = data.getData();
            getContentResolver().takePersistableUriPermission(
                    selectedRomUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
            String name = getDisplayName(selectedRomUri);
            romView.setText("Selected ROM:\n" + name);
            Button startButton = (Button) romView.getTag();
            startButton.setEnabled(true);
        }
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
