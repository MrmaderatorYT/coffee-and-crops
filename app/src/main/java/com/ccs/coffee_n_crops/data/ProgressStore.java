package com.ccs.coffee_n_crops.data;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Offline player progress: how many levels, counted from the first, have been completed.
 * Levels unlock in order, so one number is the whole state for now.
 */
public final class ProgressStore {
    private static final String PREFS = "progress";
    private static final String KEY_COMPLETED = "levels_completed";

    private ProgressStore() {
    }

    public static int completedCount(Context context) {
        return prefs(context).getInt(KEY_COMPLETED, 0);
    }

    public static boolean isUnlocked(Context context, int levelIndex) {
        return levelIndex <= completedCount(context);
    }

    public static void markCompleted(Context context, int levelIndex) {
        if (levelIndex + 1 > completedCount(context)) {
            prefs(context).edit().putInt(KEY_COMPLETED, levelIndex + 1).apply();
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
