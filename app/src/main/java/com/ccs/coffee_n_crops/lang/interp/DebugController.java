package com.ccs.coffee_n_crops.lang.interp;

import java.util.function.BooleanSupplier;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The debugger gate shared by every farmer thread. Each statement asks permission before it runs;
 * while paused the asking thread simply blocks here, so "pause" needs no interpreter state machine.
 */
public final class DebugController {
    private static final int ANY_FARMER = 0;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition resumed = lock.newCondition();
    private boolean paused;
    private int stepPermits;
    private int stepFarmer = ANY_FARMER;

    /** Called on a farmer thread before each statement. Blocks while paused with no step permit. */
    public void awaitPermission() throws InterruptedException {
        awaitPermission(() -> true, ANY_FARMER);
    }

    /** A canceled worker must not consume a step belonging to a newly started run. */
    void awaitPermission(BooleanSupplier active, int farmerId) throws InterruptedException {
        awaitPermission(active, farmerId, () -> { });
    }

    void awaitPermission(BooleanSupplier active, int farmerId, Runnable onWait) throws InterruptedException {
        lock.lockInterruptibly();
        try {
            boolean reported = false;
            while (paused && (stepPermits == 0 || stepFarmer != ANY_FARMER && stepFarmer != farmerId)
                    && active.getAsBoolean()) {
                if (!reported) {
                    onWait.run();
                    reported = true;
                }
                resumed.await();
            }
            if (!active.getAsBoolean()) throw new InterruptedException();
            if (paused) stepPermits--; // a step lets exactly one statement through
        } finally {
            lock.unlock();
        }
    }

    public void play() {
        set(false, 0, ANY_FARMER);
    }

    public void pause() {
        set(true, 0, ANY_FARMER);
    }

    /** Run one statement, then pause again at the next one. */
    public void step() {
        step(ANY_FARMER);
    }

    /** Allow the selected actor's next statement to execute while all others stay paused. */
    public void step(int farmerId) {
        set(true, 1, farmerId);
    }

    public boolean isPaused() {
        lock.lock();
        try {
            return paused;
        } finally {
            lock.unlock();
        }
    }

    private void set(boolean paused, int permits, int farmerId) {
        lock.lock();
        try {
            this.paused = paused;
            this.stepPermits = permits;
            this.stepFarmer = farmerId;
            resumed.signalAll();
        } finally {
            lock.unlock();
        }
    }
}
