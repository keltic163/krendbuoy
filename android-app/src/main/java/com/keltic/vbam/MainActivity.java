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
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int REQUEST_OPEN_ROM = 1001;

    private TextView statusView;
    private TextView romView;
    private Uri selectedRomUri;

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
        statusParams.setMargins(0, 32, 0, 32);
        root.addView(statusView, statusParams);

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
