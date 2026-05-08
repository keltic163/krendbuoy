package com.krendstudio.krendbuoy;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;

import java.util.Locale;

final class LocaleHelper {
    private LocaleHelper() {
    }

    static ContextWrapper wrap(Context context) {
        AppSettingsManager settingsManager = new AppSettingsManager(context);
        Locale locale = localeForMode(settingsManager.getLanguageMode());
        if (locale == null) {
            return new ContextWrapper(context);
        }

        Locale.setDefault(locale);
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(locale);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(new LocaleList(locale));
        }
        return new ContextWrapper(context.createConfigurationContext(config));
    }

    private static Locale localeForMode(int mode) {
        if (mode == AppSettingsManager.LANGUAGE_EN) {
            return Locale.ENGLISH;
        }
        if (mode == AppSettingsManager.LANGUAGE_ZH_TW) {
            return Locale.TAIWAN;
        }
        return null;
    }
}
