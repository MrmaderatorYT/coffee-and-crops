package com.ccs.coffee_n_crops.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.ccs.coffee_n_crops.progress.LevelScore;

/**
 * Offline player progress: the highest completed lesson and each lesson's personal records.
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

    public static LevelScore score(Context context, String levelId) {
        SharedPreferences preferences = prefs(context);
        String prefix = scorePrefix(levelId);
        if (!preferences.contains(prefix + "first_lines")) return null;
        return new LevelScore(preferences.getInt(prefix + "first_lines", 0),
                preferences.getLong(prefix + "first_ticks", 0L),
                preferences.getInt(prefix + "best_lines", 0),
                preferences.getLong(prefix + "best_ticks", 0L));
    }

    public static LevelScore markCompleted(Context context, int levelIndex, String levelId, int lines, double ticks) {
        LevelScore previous = score(context, levelId);
        LevelScore score = previous == null ? LevelScore.firstRun(lines, ticks) : previous.withRun(lines, ticks);
        String prefix = scorePrefix(levelId);
        prefs(context).edit()
                .putInt(KEY_COMPLETED, Math.max(completedCount(context), levelIndex + 1))
                .putInt(prefix + "first_lines", score.firstLines())
                .putLong(prefix + "first_ticks", score.firstTickTenths())
                .putInt(prefix + "best_lines", score.bestLines())
                .putLong(prefix + "best_ticks", score.bestTickTenths())
                .apply();
        return score;
    }

    private static String scorePrefix(String levelId) {
        return "score_" + levelId + "_";
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
