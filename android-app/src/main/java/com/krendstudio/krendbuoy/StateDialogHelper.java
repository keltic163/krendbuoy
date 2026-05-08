package com.krendstudio.krendbuoy;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class StateDialogHelper {
    public interface Callback {
        void onSlotSelected(int slot);
        void onDismiss();
    }

    private static final ExecutorService diskExecutor = Executors.newSingleThreadExecutor();

    static void show(Activity activity, String title, SaveStateManager saveStateManager, 
                     Map<Integer, Bitmap> cache, Callback callback) {
        
        saveStateManager.refreshDirectoryCache();
        
        ListView listView = new ListView(activity);
        int dp8 = dp(activity, 8);
        listView.setPadding(dp8, dp8, dp8, dp8);
        listView.setDivider(null);
        listView.setDividerHeight(dp(activity, 4));

        BaseAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() { return SaveStateManager.SLOT_COUNT + 1; }
            @Override
            public Object getItem(int position) { return position; }
            @Override
            public long getItemId(int position) { return position; }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                int slot = position; // Now 0 is Auto-Save, 1-10 are manual slots
                LinearLayout layout;
                if (convertView instanceof LinearLayout) {
                    layout = (LinearLayout) convertView;
                } else {
                    layout = new LinearLayout(activity);
                    layout.setOrientation(LinearLayout.HORIZONTAL);
                    layout.setGravity(Gravity.CENTER_VERTICAL);
                    layout.setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 12), dp(activity, 8));
                    layout.setBackground(makeRoundRect(Color.rgb(25, 36, 58), dp(activity, 8)));
                    
                    ImageView thumb = new ImageView(activity);
                    thumb.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    thumb.setBackgroundColor(Color.BLACK);
                    layout.addView(thumb, new LinearLayout.LayoutParams(dp(activity, 80), dp(activity, 54)));
                    
                    TextView text = new TextView(activity);
                    text.setTextColor(Color.WHITE);
                    text.setPadding(dp(activity, 12), 0, 0, 0);
                    layout.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                }

                ImageView thumb = (ImageView) layout.getChildAt(0);
                TextView text = (TextView) layout.getChildAt(1);

                boolean isAutoSave = (slot == SaveStateManager.AUTO_SAVE_SLOT);
                
                // Disable clicking on Auto-Save if we are in "Save" mode
                boolean canSelect = !(title.toLowerCase().contains("save") && !title.toLowerCase().contains("load") && isAutoSave);
                layout.setAlpha(canSelect ? 1.0f : 0.4f);
                layout.setClickable(!canSelect); // Parent ListView handles clicks, but this helps visual
                
                String loadingText = isAutoSave
                        ? activity.getString(R.string.auto_save_loading)
                        : activity.getString(R.string.slot_loading_format, slot);
                text.setText(loadingText);
                
                Bitmap cached = cache != null ? cache.get(slot) : null;
                if (cached != null) {
                    thumb.setImageBitmap(cached);
                    thumb.setVisibility(View.VISIBLE);
                    text.setText(saveStateManager.slotLabel(slot));
                } else {
                    thumb.setVisibility(View.INVISIBLE);
                    diskExecutor.execute(() -> {
                        String fullLabel = saveStateManager.slotLabel(slot);
                        Bitmap bmp = saveStateManager.getThumbnail(slot);
                        activity.runOnUiThread(() -> {
                            if (bmp != null && cache != null) cache.put(slot, bmp);
                            text.setText(fullLabel);
                            if (bmp != null) {
                                thumb.setImageBitmap(bmp);
                                thumb.setVisibility(View.VISIBLE);
                            }
                        });
                    });
                }
                return layout;
            }
        };
        listView.setAdapter(adapter);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(listView)
                .setNegativeButton(android.R.string.cancel, null)
                .setOnDismissListener(d -> callback.onDismiss())
                .show();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            int slot = position;
            // Prevent selecting Auto-Save in "Save" mode
            if (title.toLowerCase().contains("save") && !title.toLowerCase().contains("load") && slot == SaveStateManager.AUTO_SAVE_SLOT) {
                return;
            }
            callback.onSlotSelected(slot);
            dialog.dismiss();
        });
    }

    private static GradientDrawable makeRoundRect(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
