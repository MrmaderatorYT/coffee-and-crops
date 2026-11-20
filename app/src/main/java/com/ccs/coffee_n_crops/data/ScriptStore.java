package com.ccs.coffee_n_crops.data;

import android.content.Context;
import android.util.Log;

import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.serial.AstJson;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Offline persistence for the player's program: one JSON file per level in app-private storage.
 * Room replaces this once there is progress and farm state worth querying.
 */
public final class ScriptStore {
    private static final String TAG = "ScriptStore";

    private ScriptStore() {
    }

    public static void save(Context context, String levelId, Block program) {
        try {
            byte[] bytes = AstJson.toJson(program).toString().getBytes(StandardCharsets.UTF_8);
            Files.write(file(context, levelId).toPath(), bytes);
        } catch (IOException | JSONException e) {
            Log.w(TAG, "Could not save script for " + levelId, e);
        }
    }

    /** @return the saved program, or null if there is none or it cannot be read */
    public static Block load(Context context, String levelId) {
        File file = file(context, levelId);
        if (!file.exists()) return null;
        try {
            String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            return AstJson.blockFrom(new JSONObject(json));
        } catch (IOException | JSONException | RuntimeException e) {
            Log.w(TAG, "Could not load script for " + levelId, e);
            return null;
        }
    }

    private static File file(Context context, String levelId) {
        return new File(context.getFilesDir(), "script_" + levelId + ".json");
    }
}
