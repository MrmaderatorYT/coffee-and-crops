package com.ccs.coffee_n_crops.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.TextView;

import com.ccs.coffee_n_crops.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Reveals one instruction at a time before offering a read-only worked solution. */
final class LessonHelp {
    private static final int FAILURES_TO_SHOW_SOLUTION = 3;
    private static final String STATE_FAILURES = "lesson_help_failures";
    private static final String STATE_STEP = "lesson_help_step";

    private final Activity activity;
    private final String levelId;
    private final String[] steps;
    private int failures;
    private int step;

    LessonHelp(Activity activity, String levelId, String hint, Bundle savedState) {
        this.activity = activity;
        this.levelId = levelId;
        steps = hint.split("(?<=[.!?])\\s+(?=[\\p{Lu}])");
        if (savedState != null) {
            failures = savedState.getInt(STATE_FAILURES);
            step = Math.min(savedState.getInt(STATE_STEP), steps.length - 1);
        }
    }

    String firstHint() { return steps[0]; }

    void recordFailure() {
        if (failures < FAILURES_TO_SHOW_SOLUTION) failures++;
    }

    void recordSuccess() { failures = 0; }

    void saveState(Bundle state) {
        state.putInt(STATE_FAILURES, failures);
        state.putInt(STATE_STEP, step);
    }

    void show() {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(R.string.lesson_help)
                .setMessage(steps[step])
                .setNegativeButton(android.R.string.ok, null);
        if (steps.length > 1) {
            builder.setPositiveButton(R.string.lesson_hint_next, null);
        }
        String source = failures >= FAILURES_TO_SHOW_SOLUTION ? readSolution() : null;
        if (source != null) builder.setNeutralButton(R.string.lesson_solution, (dialog, which) -> showSolution(source));
        AlertDialog dialog = builder.show();
        if (steps.length > 1) {
            updateStep(dialog);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                step = (step + 1) % steps.length;
                updateStep(dialog);
            });
        }
    }

    private void updateStep(AlertDialog dialog) {
        dialog.setTitle(activity.getString(R.string.lesson_hint_step, step + 1, steps.length));
        dialog.setMessage(steps[step]);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(step + 1 < steps.length
                ? R.string.lesson_hint_next : R.string.lesson_hint_restart);
    }

    private String readSolution() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                activity.getAssets().open("solutions/" + levelId + ".java.txt"), StandardCharsets.UTF_8))) {
            StringBuilder source = new StringBuilder();
            for (String line; (line = reader.readLine()) != null; ) source.append(line).append('\n');
            return source.toString();
        } catch (IOException unavailable) {
            return null;
        }
    }

    private void showSolution(String source) {
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(R.string.lesson_solution)
                .setMessage(source).setPositiveButton(android.R.string.ok, null).show();
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) message.setTypeface(Typeface.MONOSPACE);
    }
}
