package com.keltic.vbam;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

public class GameActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Uri romUri = getIntent().getData();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(48, 48, 48, 48);

        TextView title = new TextView(this);
        title.setText("Game Screen");
        title.setTextSize(26f);
        root.addView(title);

        TextView info = new TextView(this);
        info.setText(
                "ROM loaded:\n\n" +
                (romUri != null ? romUri.toString() : "No ROM") +
                "\n\nNext step:\nNative rendering + controls + emulator runtime"
        );
        info.setTextSize(16f);
        info.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 32, 0, 0);
        root.addView(info, params);

        setContentView(root);
    }
}
