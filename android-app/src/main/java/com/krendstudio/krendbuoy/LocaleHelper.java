package com.krendstudio.krendbuoy;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;

import java.util.Locale;

final class LocaleHelper {
    private LocaleHelper() {
    }

    static ContextWrapper wrap(Context context) {
        Locale locale = currentLocale(context);
        if (locale == null) {
            return new ContextWrapper(context);
        }

        Locale.setDefault(locale);
        Configuration config = new Configuration(context.getResources().getConfiguration());
        applyLocaleToConfiguration(config, locale);
        return new ContextWrapper(context.createConfigurationContext(config));
    }

    static void applyTo(Context context) {
        Locale locale = currentLocale(context);
        if (locale == null) {
            return;
        }

        Locale.setDefault(locale);
        Resources resources = context.getResources();
        Configuration config = new Configuration(resources.getConfiguration());
        applyLocaleToConfiguration(config, locale);
        resources.updateConfiguration(config, resources.getDisplayMetrics());
    }

    private static Locale currentLocale(Context context) {
        AppSettingsManager settingsManager = new AppSettingsManager(context);
        return localeForMode(settingsManager.getLanguageMode());
    }

    private static void applyLocaleToConfiguration(Configuration config, Locale locale) {
        config.setLocale(locale);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(new LocaleList(locale));
        }
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
