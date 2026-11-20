package com.ccs.coffee_n_crops.app;

import android.content.Context;
import android.util.Log;

import com.ccs.coffee_n_crops.progress.LevelScore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Loads offline medal targets keyed by the same stable lesson IDs as saved programs. */
final class LevelBenchmarks {
    private static Map<String, LevelScore.Target> targets;

    private LevelBenchmarks() { }

    static LevelScore.Target forLevel(Context context, String id) {
        if (targets == null) targets = load(context);
        return targets.get(id);
    }

    private static Map<String, LevelScore.Target> load(Context context) {
        Map<String, LevelScore.Target> result = new HashMap<>();
        try (BufferedReader input = new BufferedReader(new InputStreamReader(
                context.getAssets().open("solutions/benchmarks.csv"), StandardCharsets.UTF_8))) {
            for (String line; (line = input.readLine()) != null; ) {
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                String[] parts = line.split(",", -1);
                if (parts.length == 3) {
                    result.put(parts[0], new LevelScore.Target(Integer.parseInt(parts[1]), Long.parseLong(parts[2])));
                }
            }
        } catch (IOException | NumberFormatException failure) {
            Log.e("LevelBenchmarks", "Could not load medal targets", failure);
        }
        return result;
    }
}
