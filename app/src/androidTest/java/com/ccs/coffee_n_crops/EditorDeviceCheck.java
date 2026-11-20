package com.ccs.coffee_n_crops;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.LinearLayout;

import com.ccs.coffee_n_crops.app.LevelActivity;
import com.ccs.coffee_n_crops.editor.BlockEditorView;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.SwitchStmt;
import com.ccs.coffee_n_crops.lang.check.Validator;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.progress.Unlock;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Java-only on-device smoke tests, using a disposable in-memory editor program. */
public final class EditorDeviceCheck extends Instrumentation {
    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override
    public void onStart() {
        Bundle status = new Bundle();
        status.putString("id", "Coffee & Crops");
        status.putString("class", getClass().getName());
        status.putString("test", "editorAndDebugger");
        status.putInt("numtests", 1);
        status.putInt("current", 1);
        sendStatus(1, status);
        Activity activity = null;
        try {
            Intent request = LevelActivity.intent(getTargetContext(), 0)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity = startActivitySync(request);
            verify(activity);
            activity = verifyLandscape(activity);
            sendStatus(0, status);
            finish(Activity.RESULT_OK, new Bundle());
        } catch (Throwable failure) {
            status.putString("stack", android.util.Log.getStackTraceString(failure));
            sendStatus(-2, status);
            finish(Activity.RESULT_CANCELED, status);
        } finally {
            if (activity != null) runOnMainSync(activity::finish);
        }
    }

    private Activity verifyLandscape(Activity activity) throws InterruptedException {
        AtomicReference<Activity> current = new AtomicReference<>();
        CountDownLatch rotated = new CountDownLatch(1);
        Application app = activity.getApplication();
        Application.ActivityLifecycleCallbacks watcher = new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity next, Bundle state) { }
            @Override public void onActivityStarted(Activity next) { }
            @Override public void onActivityPaused(Activity next) { }
            @Override public void onActivityStopped(Activity next) { }
            @Override public void onActivitySaveInstanceState(Activity next, Bundle state) { }
            @Override public void onActivityDestroyed(Activity next) { }

            @Override public void onActivityResumed(Activity next) {
                if (next instanceof LevelActivity && next.getResources().getConfiguration().orientation
                        == Configuration.ORIENTATION_LANDSCAPE) {
                    current.set(next);
                    rotated.countDown();
                }
            }
        };
        runOnMainSync(() -> {
            app.registerActivityLifecycleCallbacks(watcher);
            watcher.onActivityResumed(activity);
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        });
        try {
            require(rotated.await(8, TimeUnit.SECONDS), "Level did not reopen in landscape");
            Activity landscape = current.get();
            waitFor(() -> {
                LinearLayout body = landscape.findViewById(R.id.level_body);
                View farm = landscape.findViewById(R.id.farm_view);
                View editor = landscape.findViewById(R.id.editor);
                return body.getOrientation() == LinearLayout.HORIZONTAL && farm.getWidth() > 0
                        && editor.getWidth() > 0 && editor.getHeight() > 0;
            }, "Landscape did not show both the farm and editor");
            return landscape;
        } finally {
            runOnMainSync(() -> app.unregisterActivityLifecycleCallbacks(watcher));
        }
    }

    private void verify(Activity activity) {
        BlockEditorView editor = activity.findViewById(R.id.editor);
        Block program = new Block();
        SwitchStmt choice = new SwitchStmt(Ast.lit(0));
        choice.addCase(0);
        program.add(choice);
        runOnMainSync(() -> {
            editor.setOnChangeListener(null);
            editor.setUnlocks(Set.of(Unlock.SWITCH, Unlock.INT, Unlock.IF, Unlock.ELSE_IF,
                    Unlock.FOR, Unlock.HARVEST, Unlock.WATER, Unlock.MOVE, Unlock.LOGIC));
            editor.setProgram(program);
        });
        waitFor(() -> findText(editor, "case") != null, "Switch case row did not render");
        runOnMainSync(() -> {
            clickText(editor, "case");
            clickText(editor, "+ case 1:");
            require(choice.cases().size() == 2, "Case was not added");
            clickText(editor, "+ default:");
            require(choice.defaultBlock() != null, "Default was not added");
            clickText(editor, activity.getString(R.string.editor_delete));
            require(choice.defaultBlock() == null, "Empty default was not deleted");
            choice.addCase(0);
            Validator.Problem duplicate = Validator.check(program);
            require(duplicate != null && duplicate.message.contains("duplicate case label"),
                    "Duplicate case was accepted");
        });

        Block debuggerProgram = new Block();
        debuggerProgram.add(Ast.declare(JType.INT, "steps"));
        debuggerProgram.add(Ast.forCount("i", 1));
        runOnMainSync(() -> {
            editor.setProgram(debuggerProgram);
            activity.findViewById(R.id.btn_step).performClick();
        });
        waitFor(() -> visible(activity, R.id.btn_variables), "Paused variables did not become available");
        runOnMainSync(() -> activity.findViewById(R.id.btn_step).performClick());
        waitFor(() -> visible(activity, R.id.btn_variables), "Variables disappeared after stepping");
        runOnMainSync(() -> activity.findViewById(R.id.btn_play).performClick());
        waitFor(() -> !visible(activity, R.id.btn_variables), "Resume did not hide the paused variables");
        runOnMainSync(() -> {
            activity.findViewById(R.id.btn_reset).performClick();
            require(!visible(activity, R.id.btn_variables), "Reset left stale variable data visible");
            activity.findViewById(R.id.btn_hint).performClick();
        });
    }

    private void waitFor(BooleanSupplier condition, String failure) {
        long timeout = SystemClock.uptimeMillis() + 4_000;
        do {
            boolean[] match = {false};
            runOnMainSync(() -> match[0] = condition.getAsBoolean());
            if (match[0]) return;
            SystemClock.sleep(30);
        } while (SystemClock.uptimeMillis() < timeout);
        throw new AssertionError(failure);
    }

    private static boolean visible(Activity activity, int id) {
        return activity.findViewById(id).getVisibility() == View.VISIBLE;
    }

    private static void clickText(View root, String label) {
        View button = findText(root, label);
        if (button == null) throw new AssertionError("Missing editor control " + label);
        require(button.performClick(), "Cannot activate " + label);
    }

    private static View findText(View view, String label) {
        if (view.getVisibility() != View.VISIBLE) return null;
        if (view instanceof TextView text && label.contentEquals(text.getText())) {
            return view.hasOnClickListeners() ? view : (View) view.getParent();
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findText(group.getChildAt(i), label);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void require(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }
}
