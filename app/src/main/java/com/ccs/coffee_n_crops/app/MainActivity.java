package com.ccs.coffee_n_crops.app;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.ccs.coffee_n_crops.R;
import com.ccs.coffee_n_crops.data.ProgressStore;
import com.ccs.coffee_n_crops.progress.LevelCatalog;
import com.ccs.coffee_n_crops.progress.LevelDef;
import com.ccs.coffee_n_crops.progress.LevelScore;
import com.ccs.coffee_n_crops.progress.LevelTranslations;

import java.util.List;

/** Level select: one button per level; a level opens once the one before it is complete. */
public final class MainActivity extends Activity {
    private LinearLayout list;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppLanguage.apply(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        list = findViewById(R.id.level_list);
        boolean ukrainian = AppLanguage.isUkrainian(this);
        bindLanguage(findViewById(R.id.language_uk), AppLanguage.UKRAINIAN, ukrainian);
        bindLanguage(findViewById(R.id.language_en), AppLanguage.ENGLISH, !ukrainian);
        Button sound = findViewById(R.id.toggle_sound);
        showSoundSetting(sound);
        sound.setOnClickListener(view -> {
            FarmSounds.setEnabled(this, !FarmSounds.enabled(this));
            showSoundSetting(sound);
        });
    }

    private void showSoundSetting(Button button) {
        boolean enabled = FarmSounds.enabled(this);
        button.setSelected(enabled);
        button.setText(enabled ? R.string.farm_sound_on : R.string.farm_sound_off);
    }

    private void bindLanguage(Button button, String language, boolean selected) {
        button.setSelected(selected);
        button.setOnClickListener(view -> {
            AppLanguage.choose(this, language);
            if (!selected) recreate();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        rebuild(); // progress may have changed while a level was open
    }

    private void rebuild() {
        list.removeAllViews();
        int completed = ProgressStore.completedCount(this);
        List<LevelDef> levels = LevelCatalog.all();
        LevelTranslations translations = AppLanguage.lessons(this);
        ProgressBar progress = findViewById(R.id.level_progress);
        progress.setMax(levels.size());
        progress.setProgress(completed);
        TextView progressLabel = findViewById(R.id.level_progress_label);
        progressLabel.setText(getString(R.string.level_progress, completed, levels.size()));

        LevelTranslations.Text sandbox = translations.text(LevelCatalog.get(LevelCatalog.SANDBOX_INDEX));
        addCard(sandbox.title(), sandbox.goal(), LevelCatalog.SANDBOX_INDEX, true, false);
        for (int i = 0; i < levels.size(); i++) {
            LevelDef level = levels.get(i);
            LevelTranslations.Text text = translations.text(level);
            boolean unlocked = ProgressStore.isUnlocked(this, i);
            String title = text.title();
            if (i < completed) {
                LevelScore score = ProgressStore.score(this, level.id);
                int stars = score == null ? 1 : score.stars(LevelBenchmarks.forLevel(this, level.id));
                title = getString(R.string.level_scored_title, title, "★★★".substring(0, stars),
                        "☆☆☆".substring(stars));
            } else if (!unlocked) {
                title = getString(R.string.level_locked_title, title);
            }
            addCard(title, text.goal(), i, unlocked, i == completed);
        }
    }

    private void addCard(String title, String goal, int index, boolean enabled, boolean selected) {
        Button button = (Button) getLayoutInflater().inflate(R.layout.level_card, list, false);
        SpannableString label = new SpannableString(getString(R.string.level_card_label, title, goal));
        label.setSpan(new StyleSpan(Typeface.BOLD), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        label.setSpan(new ForegroundColorSpan(getColor(R.color.text_dim)), title.length(), label.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        button.setText(label);
        button.setEnabled(enabled);
        button.setSelected(enabled && selected);
        button.setOnClickListener(v -> startActivity(LevelActivity.intent(this, index)));
        list.addView(button);
    }
}
