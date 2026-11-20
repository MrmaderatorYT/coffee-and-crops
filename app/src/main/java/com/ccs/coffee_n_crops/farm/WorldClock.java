package com.ccs.coffee_n_crops.farm;

/**
 * Game time. Farm actions cost ticks; the speed multiplier shortens the real time a tick takes.
 * Waiting is done in short slices so a speed change takes effect mid-action.
 */
public final class WorldClock {
    private static final long SLICE_MS = 10;

    private final long baseTickMs;
    private volatile int speed = 1;

    /** @param baseTickMs real milliseconds per tick at speed x1; 0 disables waiting (unit tests) */
    public WorldClock(long baseTickMs) {
        this.baseTickMs = baseTickMs;
    }

    public void setSpeed(int speed) {
        this.speed = Math.max(1, speed);
    }

    public int speed() {
        return speed;
    }

    /** Duration used by the renderer to follow an action's timing. */
    public long millisPerTick() {
        return baseTickMs;
    }

    /** Blocks the calling farmhand thread for the given number of ticks. */
    public void awaitTicks(double ticks) throws InterruptedException {
        if (Thread.interrupted()) throw new InterruptedException();
        double remainingMs = ticks * baseTickMs;
        while (remainingMs > 0) {
            Thread.sleep(SLICE_MS);
            remainingMs -= SLICE_MS * speed;
        }
    }
}
