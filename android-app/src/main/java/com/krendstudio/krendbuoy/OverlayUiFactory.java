package com.krendstudio.krendbuoy;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Small shared UI helpers for the in-game overlay.
 */
final class OverlayUiFactory {
    private OverlayUiFactory() {}

    static TextView makeSystemButton(Activity activity, String label, Runnable action) {
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

    static TextView addSystemControl(Activity activity, FrameLayout parent, String label, int width, int height, int gravity, int horizontalMargin, int verticalMargin, Runnable action) {
        TextView view = makeSystemButton(activity, label, action);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, height, gravity);
        if ((gravity & Gravity.RIGHT) == Gravity.RIGHT) lp.rightMargin = horizontalMargin;
        else if ((gravity & Gravity.LEFT) == Gravity.LEFT) lp.leftMargin = horizontalMargin;
        if ((gravity & Gravity.TOP) == Gravity.TOP) lp.topMargin = verticalMargin;
        else lp.bottomMargin = verticalMargin;
        parent.addView(view, lp);
        return view;
    }

    static void placeByCenter(ViewGroup parent, View view, int width, int height, float centerX, float centerY) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, height, Gravity.TOP | Gravity.LEFT);
        lp.leftMargin = Math.round(centerX - width / 2f);
        lp.topMargin = Math.round(centerY - height / 2f);
        parent.addView(view, lp);
    }
}
