package com.ccs.coffee_n_crops.app;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.util.SparseBooleanArray;

import com.ccs.coffee_n_crops.R;
import com.ccs.coffee_n_crops.farm.Farmhand;

/** Short offline feedback sounds. SoundPool decodes the bundled samples asynchronously. */
final class FarmSounds {
    private static final String PREFS = "display_settings";
    private static final String SOUND_SETTING = "farm_sounds";
    private static final float GAIN = .7f;

    private final SoundPool pool;
    private final int harvest;
    private final int water;
    private final int complete;
    private final SparseBooleanArray ready = new SparseBooleanArray();
    private boolean active;
    private boolean closed;

    FarmSounds(Context context) {
        pool = new SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        pool.setOnLoadCompleteListener((sounds, sample, status) -> {
            if (!closed && status == 0) ready.put(sample, true);
        });
        harvest = pool.load(context, R.raw.farm_harvest, 1);
        water = pool.load(context, R.raw.farm_water, 1);
        complete = pool.load(context, R.raw.farm_complete, 1);
    }

    static boolean enabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(SOUND_SETTING, true);
    }

    static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(SOUND_SETTING, enabled).apply();
    }

    void setActive(boolean active) {
        this.active = active;
        if (!active && !closed) pool.autoPause();
    }

    void play(Farmhand.Action action) {
        switch (action) {
            case HARVEST -> play(harvest, 1f);
            case FERTILIZE -> play(harvest, .75f);
            case WATER -> play(water, 1f);
            case REFILL -> play(water, .8f);
            default -> { }
        }
    }

    void complete() {
        play(complete, 1f);
    }

    void close() {
        closed = true;
        pool.release();
    }

    private void play(int sample, float speed) {
        if (active && !closed && ready.get(sample)) pool.play(sample, GAIN, GAIN, 1, 0, speed);
    }
}
