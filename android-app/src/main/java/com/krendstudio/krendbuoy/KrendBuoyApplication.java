package com.krendstudio.krendbuoy;

import android.app.Application;
import android.content.Context;

public class KrendBuoyApplication extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.wrap(base));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        LocaleHelper.applyTo(this);
    }
}
