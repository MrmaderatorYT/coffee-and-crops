package com.ccs.coffee_n_crops.app;

import android.content.Context;
import android.content.res.Configuration;
import android.os.LocaleList;
import android.util.Log;

import com.ccs.coffee_n_crops.progress.LevelTranslations;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Applies the locally saved language before Android inflates any view resources. */
final class AppLanguage {
    static final String ENGLISH = "en";
    static final String UKRAINIAN = "uk";
    private static final String PREFERENCES = "display_settings";
    private static final String LANGUAGE_KEY = "language";
    private static final String EARLY_LESSONS = "i18n/uk_levels_01_28.json";
    private static final String LATER_LESSONS = "i18n/uk_levels_29_56.json";
    private static LevelTranslations ukrainian;

    private AppLanguage() { }

    static Context apply(Context base) {
        String system = base.getResources().getConfiguration().getLocales().get(0).getLanguage();
        String stored = base.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(LANGUAGE_KEY, system);
        Locale locale = Locale.forLanguageTag(UKRAINIAN.equals(stored) ? UKRAINIAN : ENGLISH);
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.setLocales(new LocaleList(locale));
        return base.createConfigurationContext(configuration);
    }

    static boolean isUkrainian(Context context) {
        return UKRAINIAN.equals(context.getResources().getConfiguration().getLocales().get(0).getLanguage());
    }

    static void choose(Context context, String language) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putString(LANGUAGE_KEY, language).apply();
    }

    static LevelTranslations lessons(Context context) {
        return isUkrainian(context) ? ukrainianLessons(context) : LevelTranslations.ENGLISH;
    }

    private static synchronized LevelTranslations ukrainianLessons(Context context) {
        if (ukrainian == null) {
            try {
                ukrainian = LevelTranslations.parse(read(context, EARLY_LESSONS), read(context, LATER_LESSONS));
            } catch (IOException | JSONException | IllegalArgumentException error) {
                Log.e("AppLanguage", "Cannot load bundled lesson translations", error);
                ukrainian = LevelTranslations.ENGLISH;
            }
        }
        return ukrainian;
    }

    private static String read(Context context, String asset) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open(asset),
                StandardCharsets.UTF_8))) {
            StringBuilder text = new StringBuilder();
            char[] buffer = new char[4096];
            for (int count; (count = reader.read(buffer)) != -1; ) text.append(buffer, 0, count);
            return text.toString();
        }
    }
}
