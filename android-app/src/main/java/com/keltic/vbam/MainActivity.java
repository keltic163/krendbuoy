package com.keltic.vbam;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String message;
        try {
            System.loadLibrary("vbam_libretro");
            message = "VBA-M Android core loaded successfully.\nStandalone gameplay UI will be added next.";
        } catch (Throwable t) {
            message = "Core library load failed: " + t.getMessage();
        }

        TextView view = new TextView(this);
        view.setPadding(48, 96, 48, 48);
        view.setText(message);
        view.setTextSize(18f);
        setContentView(view);
    }
}
