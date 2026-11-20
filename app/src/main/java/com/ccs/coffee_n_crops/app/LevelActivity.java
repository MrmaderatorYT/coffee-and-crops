package com.ccs.coffee_n_crops.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.ccs.coffee_n_crops.R;
import com.ccs.coffee_n_crops.data.ProgressStore;
import com.ccs.coffee_n_crops.data.ScriptStore;
import com.ccs.coffee_n_crops.editor.BlockEditorView;
import com.ccs.coffee_n_crops.editor.SyntaxColors;
import com.ccs.coffee_n_crops.farm.Farmhand;
import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.farm.WorldClock;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.check.Validator;
import com.ccs.coffee_n_crops.lang.interp.ExecListener;
import com.ccs.coffee_n_crops.lang.interp.Interpreter;
import com.ccs.coffee_n_crops.lang.print.JavaPrinter;
import com.ccs.coffee_n_crops.progress.LevelCatalog;
import com.ccs.coffee_n_crops.progress.LevelDef;
import com.ccs.coffee_n_crops.progress.LevelScore;
import com.ccs.coffee_n_crops.progress.LevelTranslations;
import com.ccs.coffee_n_crops.render.FarmSurfaceView;
import com.ccs.coffee_n_crops.render.FarmTheme;

import java.util.HashMap;
import java.util.Map;

/** The play screen: farm on top, code editor in the middle, debugger controls at the bottom. */
public final class LevelActivity extends Activity {

    private enum RunState { IDLE, RUNNING, PAUSED, DONE }

    private static final String EXTRA_LEVEL_INDEX = "level_index";
    private static final long TICK_MS = 450;
    private static final int[] SPEEDS = {1, 5, 10};
    private static final int ANY_FARMHAND = 0;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Interpreter interpreter = new Interpreter(new WorldClock(TICK_MS));

    private int levelIndex;
    private LevelDef level;
    private FarmSounds sounds;
    private LevelTranslations translations;
    private LevelTranslations.Text lessonText;
    private LessonHelp help;
    private FarmWorld world;
    private BlockEditorView editor;
    private TextView status;
    private Button playButton;
    private Button speedButton;
    private Button nextButton;
    private Button stepTargetButton;
    private View variablesButton;
    private final Map<Integer, String> variableSnapshots = new HashMap<>();
    private final Map<Integer, Integer> teamNodes = new HashMap<>();
    private boolean teamActive;
    private int stepTarget = ANY_FARMHAND;

    private RunState state = RunState.IDLE;
    private int speedIndex;
    /** Incremented for every run and reset; events from an older run are ignored. */
    private int runGeneration;

    public static Intent intent(Context context, int levelIndex) {
        return new Intent(context, LevelActivity.class).putExtra(EXTRA_LEVEL_INDEX, levelIndex);
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppLanguage.apply(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_level);
        if (FarmSounds.enabled(this)) sounds = new FarmSounds(this);
        ResponsiveLevelLayout.apply(this);

        levelIndex = getIntent().getIntExtra(EXTRA_LEVEL_INDEX, 0);
        level = LevelCatalog.get(levelIndex);
        translations = AppLanguage.lessons(this);
        lessonText = translations.text(level);
        help = new LessonHelp(this, level.id, lessonText.hint(), savedInstanceState);
        world = level.createWorld();

        ((TextView) findViewById(R.id.level_title)).setText(lessonText.title());
        ((TextView) findViewById(R.id.level_goal)).setText(lessonText.goal());
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        ((FarmSurfaceView) findViewById(R.id.farm_view)).bind(world, interpreter.clock, level.finish(),
                FarmTheme.forLevel(levelIndex));
        status = findViewById(R.id.status);
        status.setOnClickListener(v -> showFullNote());
        playButton = findViewById(R.id.btn_play);
        speedButton = findViewById(R.id.btn_speed);
        nextButton = findViewById(R.id.btn_next);
        stepTargetButton = findViewById(R.id.btn_step_target);
        variablesButton = findViewById(R.id.btn_variables);
        variablesButton.setOnClickListener(view -> showVariables());
        findViewById(R.id.btn_hint).setOnClickListener(view -> help.show());

        editor = findViewById(R.id.editor);
        editor.setUnlocks(level.unlocks);
        Block saved = ScriptStore.load(this, level.id);
        editor.setProgram(saved != null ? saved : level.createProgram());
        editor.setOnChangeListener(() -> {
            ScriptStore.save(this, level.id, editor.getProgram());
            if (state == RunState.DONE) reset(); // the farm shows the old run's result; start clean
        });

        playButton.setOnClickListener(v -> onPlayPause());
        findViewById(R.id.btn_step).setOnClickListener(v -> onStep());
        stepTargetButton.setOnClickListener(v -> cycleStepTarget());
        findViewById(R.id.btn_reset).setOnClickListener(v -> reset());
        speedButton.setOnClickListener(v -> {
            speedIndex = (speedIndex + 1) % SPEEDS.length;
            applySpeed();
        });
        boolean last = level.isSandbox() || levelIndex + 1 >= LevelCatalog.all().size();
        nextButton.setText(last ? R.string.btn_all_levels : R.string.btn_next_level);
        nextButton.setOnClickListener(v -> {
            if (!last) startActivity(intent(this, levelIndex + 1));
            finish();
        });

        applySpeed();
        setState(RunState.IDLE);
        showStatus(help.firstHint(), false);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        help.saveState(state);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sounds != null) sounds.setActive(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sounds != null) sounds.setActive(false);
        if (state == RunState.RUNNING) {
            interpreter.debug.pause();
            setState(RunState.PAUSED);
            showStatus(getText(R.string.run_paused), false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        runGeneration++;
        interpreter.stop();
        if (sounds != null) sounds.close();
    }

    // ---- debugger controls -------------------------------------------------------------------

    private void onPlayPause() {
        switch (state) {
            case IDLE:
            case DONE:
                start(false);
                break;
            case RUNNING:
                interpreter.debug.pause();
                setState(RunState.PAUSED);
                showStatus(getText(R.string.run_paused), false);
                break;
            case PAUSED:
                interpreter.debug.play();
                setState(RunState.RUNNING);
                showStatus(getText(R.string.run_running), false);
                break;
        }
    }

    private void onStep() {
        if (state == RunState.IDLE || state == RunState.DONE) {
            start(true); // stops on the first statement, highlighted and not yet executed
        } else {
            interpreter.debug.step(stepTarget);
            setState(RunState.PAUSED);
            showStatus(getText(R.string.run_step_ready), false);
        }
    }

    private void start(boolean paused) {
        Block program = editor.getProgram();
        if (program.size() == 0) {
            showStatus(getText(R.string.run_empty), false);
            return;
        }
        // The "compile" step: a program with an error never starts.
        Validator.Problem problem = Validator.check(program);
        if (problem != null) {
            help.recordFailure();
            editor.highlight(problem.node.id, true);
            showStatus(getString(R.string.run_error, translations.message(problem.message)), true);
            return;
        }

        interpreter.stop();
        variableSnapshots.clear();
        clearTeamState();
        world.reset();
        editor.clearHighlight();
        editor.setLocked(true);
        editor.setObjectState("");
        nextButton.setVisibility(View.GONE);
        int generation = ++runGeneration;
        interpreter.start(program, world, new UiListener(generation), paused);
        setState(paused ? RunState.PAUSED : RunState.RUNNING);
        showStatus(getText(paused ? R.string.run_step_ready : R.string.run_running), false);
    }

    private void reset() {
        runGeneration++;
        interpreter.stop();
        variableSnapshots.clear();
        clearTeamState();
        world.reset();
        editor.clearHighlight();
        editor.setLocked(false);
        editor.setObjectState("");
        editor.restoreEditingLocation();
        nextButton.setVisibility(View.GONE);
        setState(RunState.IDLE);
        showStatus(help.firstHint(), false);
    }

    private void applySpeed() {
        int speed = SPEEDS[speedIndex];
        interpreter.clock.setSpeed(speed);
        speedButton.setText(getString(R.string.run_speed, speed));
        speedButton.setContentDescription(getString(R.string.run_speed_description, speed));
    }

    private void setState(RunState next) {
        state = next;
        if (next == RunState.RUNNING) variableSnapshots.clear();
        playButton.setText(next == RunState.RUNNING ? R.string.btn_pause
                : next == RunState.PAUSED ? R.string.btn_resume
                : R.string.btn_run);
        updateStepTarget();
        updateVariablesButton();
    }

    private void cycleStepTarget() {
        int next = ANY_FARMHAND;
        for (Farmhand hand : world.farmers()) {
            if (hand.id > stepTarget && teamNodes.containsKey(hand.id)) {
                next = hand.id;
                break;
            }
        }
        stepTarget = next;
        updateStepTarget();
        Integer node = teamNodes.get(stepTarget);
        if (node != null) editor.highlightTeamNode(stepTarget != world.farmer.id, node, true);
    }

    private void updateStepTarget() {
        stepTargetButton.setVisibility(teamActive && state == RunState.PAUSED && !teamNodes.isEmpty()
                ? View.VISIBLE : View.GONE);
        boolean any = stepTarget == ANY_FARMHAND;
        stepTargetButton.setText(any ? getText(R.string.step_target_any)
                : getString(R.string.step_target_farmer, stepTarget));
        stepTargetButton.setContentDescription(any ? getText(R.string.step_target_any_description)
                : getString(R.string.step_target_farmer_description, stepTarget));
        stepTargetButton.setTextColor(any ? getColor(R.color.text_primary)
                : stepTarget == world.farmer.id ? SyntaxColors.ACCENT : SyntaxColors.TEAMMATE);
    }

    private void clearTeamState() {
        teamActive = false;
        stepTarget = ANY_FARMHAND;
        teamNodes.clear();
        updateStepTarget();
    }

    private void updateVariablesButton() {
        variablesButton.setVisibility(state == RunState.PAUSED && !variableSnapshots.isEmpty()
                ? View.VISIBLE : View.GONE);
    }

    private void showVariables() {
        StringBuilder text = new StringBuilder();
        for (Farmhand hand : world.farmers()) {
            String bindings = variableSnapshots.get(hand.id);
            if (bindings == null || stepTarget != ANY_FARMHAND && hand.id != stepTarget) continue;
            if (text.length() > 0) text.append("\n\n");
            if (teamActive) text.append(getString(R.string.debug_farmhand, hand.id)).append('\n');
            text.append(bindings.isEmpty() ? getText(R.string.debug_no_variables) : bindings);
        }
        if (text.length() == 0) return;
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.debug_variables)
                .setMessage(text).setPositiveButton(android.R.string.ok, null).show();
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) message.setTypeface(Typeface.MONOSPACE);
    }

    private void showStatus(CharSequence message, boolean error) {
        status.setTextColor(getColor(error ? R.color.error : R.color.text_dim));
        status.setText(message);
    }

    /** Let a long hint or Java error be read without taking height away from the farm and editor. */
    private void showFullNote() {
        Layout layout = status.getLayout();
        if (layout == null || layout.getLineCount() == 0
                || layout.getEllipsisCount(layout.getLineCount() - 1) == 0) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.run_information)
                .setMessage(status.getText())
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    // ---- results -----------------------------------------------------------------------------

    private void onRunFinished() {
        editor.clearHighlight();
        editor.setLocked(false);
        setState(RunState.DONE);
        if (level.isSandbox()) {
            showStatus(getText(R.string.sandbox_run_finished), false);
            return;
        }
        String missing = level.check(world, editor.getProgram());
        if (missing == null) {
            help.recordSuccess();
            if (sounds != null) sounds.complete();
            int lines = JavaPrinter.toSource(editor.getProgram()).split("\\n").length;
            LevelScore score = ProgressStore.markCompleted(this, levelIndex, level.id, lines, world.actionTicks());
            LevelScore.Target target = LevelBenchmarks.forLevel(this, level.id);
            int stars = score.stars(target);
            showStatus(target == null
                    ? getString(R.string.run_success_score, stars, score.bestLines(), score.bestTicks())
                    : getString(R.string.run_success_target, stars, score.bestLines(), score.bestTicks(),
                            target.lines(), target.ticks()), false);
            nextButton.setVisibility(View.VISIBLE);
        } else {
            help.recordFailure();
            showStatus(translations.message(missing), false);
        }
    }

    private void onRunFailed(int nodeId, String message) {
        help.recordFailure();
        editor.setLocked(false);
        editor.highlight(nodeId, true);
        setState(RunState.DONE);
        showStatus(getString(R.string.run_error, translations.message(message)), true);
    }

    /** Receives interpreter events on the farmhand thread and forwards them to the UI thread. */
    private final class UiListener implements ExecListener {
        private final int generation;

        UiListener(int generation) {
            this.generation = generation;
        }

        private void post(Runnable action) {
            main.post(() -> {
                if (generation == runGeneration) action.run();
            });
        }

        @Override
        public void onNodeEnter(int farmerId, int nodeId) {
            post(() -> {
                if (!teamActive) {
                    editor.highlight(nodeId, false);
                    return;
                }
                boolean teammate = farmerId != world.farmer.id;
                boolean firstNode = teamNodes.put(farmerId, nodeId) == null;
                boolean follow = farmerId == stepTarget || stepTarget == ANY_FARMHAND
                        && (!teammate || !teamNodes.containsKey(world.farmer.id));
                editor.highlightTeamNode(teammate, nodeId, follow);
                if (firstNode) updateStepTarget();
            });
        }

        @Override
        public void onTeamStarted() {
            post(() -> {
                teamActive = true;
                editor.clearHighlight();
                updateStepTarget();
            });
        }

        @Override
        public void onTeamFarmerFinished(int farmerId) {
            post(() -> {
                variableSnapshots.remove(farmerId);
                updateVariablesButton();
                teamNodes.remove(farmerId);
                if (stepTarget == farmerId) stepTarget = ANY_FARMHAND;
                editor.highlightTeamNode(farmerId != world.farmer.id, -1, false);
                updateStepTarget();
            });
        }

        @Override
        public void onTeamFinished() {
            post(LevelActivity.this::clearTeamState);
        }

        @Override
        public void onObjectState(int farmerId, String snapshot) {
            post(() -> editor.setObjectState(snapshot));
        }

        @Override
        public void onVariables(int farmerId, String snapshot) {
            post(() -> {
                variableSnapshots.put(farmerId, snapshot);
                updateVariablesButton();
            });
        }

        @Override
        public void onFarmAction(Farmhand.Action action) {
            if (sounds != null) post(() -> sounds.play(action));
        }

        @Override
        public void onFinished(int farmerId) {
            post(LevelActivity.this::onRunFinished);
        }

        @Override
        public void onError(int farmerId, int nodeId, String message) {
            post(() -> onRunFailed(nodeId, message));
        }
    }
}
