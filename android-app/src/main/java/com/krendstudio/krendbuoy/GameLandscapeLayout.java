package com.krendstudio.krendbuoy;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Builds the landscape layout for GameActivityV2.
 * Design: Left Controls | Center Screen | Right Controls.
 */
final class GameLandscapeLayout {

    static GamePortraitLayout.Result build(Activity activity, GameActivityV2 host) {
        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(Color.rgb(18, 22, 26));

        // 1. Center Screen Area
        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
        
        // Use about 60% of width for the screen in the center
        int centerWidth = Math.round(screenWidth * 0.60f);
        int sideWidth = (screenWidth - centerWidth) / 2;

        FrameLayout screenBox = new FrameLayout(activity);
        screenBox.setBackgroundColor(Color.BLACK);
        FrameLayout.LayoutParams screenBoxLp = new FrameLayout.LayoutParams(centerWidth, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER);
        root.addView(screenBox, screenBoxLp);

        ImageView screen = new ImageView(activity);
        screen.setAdjustViewBounds(false);
        screen.setScaleType(ImageView.ScaleType.FIT_CENTER);
        screenBox.addView(screen, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        View screenBorder = new View(activity);
        screenBorder.setBackground(new GameActivityV2.BorderDrawable(host.dp(2), Color.DKGRAY));
        screenBox.addView(screenBorder, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 2. Left Overlay (Title, FPS, D-pad, Select)
        TextView title = new TextView(activity);
        title.setText(activity.getString(R.string.app_name));
        title.setTextSize(16f); title.setTextColor(Color.WHITE); title.setTypeface(null, Typeface.BOLD);
        FrameLayout.LayoutParams titleLp = new FrameLayout.LayoutParams(sideWidth, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT);
        titleLp.leftMargin = host.dp(12); titleLp.topMargin = host.dp(12);
        root.addView(title, titleLp);

        TextView info = new TextView(activity);
        info.setText("00:00:00 / 0 FPS"); info.setTextSize(10f); info.setTextColor(Color.GRAY);
        FrameLayout.LayoutParams infoLp = new FrameLayout.LayoutParams(sideWidth, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT);
        infoLp.leftMargin = host.dp(12); infoLp.topMargin = host.dp(34);
        root.addView(info, infoLp);

        // 3. Right Overlay (System Buttons, Buttons)
        // We'll let GameControllerOverlay handle placing buttons in these side areas.
        
        GameControllerOverlay.attachLandscape(activity, root, host, sideWidth);

        return new GamePortraitLayout.Result(root, info, screen, screenBox, screenBorder);
    }
}
