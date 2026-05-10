package com.krendstudio.krendbuoy;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Builds the portrait layout for GameActivityV2.
 * This is the classic layout with screen on top and controls/tools below.
 */
final class GamePortraitLayout {

    static final class Result {
        final View root;
        final TextView info;
        final ImageView screen;
        final FrameLayout screenBox;
        final View screenBorder;

        Result(View root, TextView info, ImageView screen, FrameLayout screenBox, View screenBorder) {
            this.root = root;
            this.info = info;
            this.screen = screen;
            this.screenBox = screenBox;
            this.screenBorder = screenBorder;
        }
    }

    static Result build(Activity activity, GameActivityV2 host) {
        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(Color.rgb(18, 22, 26));

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        content.setPadding(host.dp(8), host.dp(62), host.dp(8), 0);
        root.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels - host.dp(16);
        int screenHeight = Math.round(screenWidth * 2f / 3f);
        screenHeight = Math.max(host.dp(220), Math.min(screenHeight, host.dp(360)));

        TextView info = new TextView(activity);
        info.setText("00:00:00 / 0 FPS");
        info.setTextSize(11f);
        info.setTextColor(Color.GRAY);
        info.setGravity(Gravity.LEFT);
        info.setPadding(host.dp(8), 0, 0, host.dp(4));
        content.addView(info, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout screenBox = new FrameLayout(activity);
        screenBox.setBackgroundColor(Color.BLACK);
        content.addView(screenBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, screenHeight));

        ImageView screen = new ImageView(activity);
        screen.setAdjustViewBounds(false);
        screen.setScaleType(ImageView.ScaleType.FIT_CENTER);
        screenBox.addView(screen, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        View screenBorder = new View(activity);
        screenBorder.setBackground(new GameActivityV2.BorderDrawable(host.dp(4), Color.GRAY));
        screenBox.addView(screenBorder, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Attach controller overlay (portrait logic is inside attach)
        GameControllerOverlay.attach(activity, root, host);

        return new Result(root, info, screen, screenBox, screenBorder);
    }
}
