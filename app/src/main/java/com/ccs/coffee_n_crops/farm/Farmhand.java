package com.ccs.coffee_n_crops.farm;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** The person working the farm. The renderer reads a single immutable pose per frame. */
public final class Farmhand {
    public enum Action { IDLE, WALK, HARVEST, WATER, REFILL, FERTILIZE }

    /** Position, facing and action published together so a frame cannot mix separate updates. */
    public static final class Pose {
        public final int x;
        public final int y;
        public final int fromX;
        public final int fromY;
        public final Direction facing;
        public final Action action;
        public final long startedNanos;
        public final double durationTicks;

        private Pose(int x, int y, int fromX, int fromY, Direction facing, Action action, double durationTicks) {
            this.x = x;
            this.y = y;
            this.fromX = fromX;
            this.fromY = fromY;
            this.facing = facing;
            this.action = action;
            this.durationTicks = durationTicks;
            startedNanos = System.nanoTime();
        }
    }

    public final int id;
    private final Farmhand partner;
    private volatile Pose pose;

    public Farmhand(int id, int x, int y) {
        this(id, x, y, null);
    }

    Farmhand(int id, int x, int y, Farmhand partner) {
        this.id = id;
        this.partner = partner;
        reset(x, y);
    }

    public Pose pose() {
        return pose;
    }

    synchronized void reset(int x, int y) {
        pose = new Pose(x, y, x, y, Direction.EAST, Action.IDLE, 0);
    }

    synchronized void startWalking(int x, int y, Direction facing, double ticks) {
        pose = new Pose(x, y, pose.x, pose.y, facing, Action.WALK, ticks);
    }

    public synchronized Pose beginAction(Action action, double ticks) {
        pose = new Pose(pose.x, pose.y, pose.x, pose.y, pose.facing, action, ticks);
        return pose;
    }

    public synchronized void finishAction(Pose started) {
        // A canceled worker must not clear the pose after Reset or another run.
        if (pose == started) beginAction(Action.IDLE, 0);
    }

    /** Runs two background jobs and waits for their results on the farmhand's own thread. */
    public void runTogether(Runnable first, Runnable second) {
        runPair(first, "farm-job-1", second, "farm-job-2");
    }

    /** Runs each working farmer's actions on a separate thread and waits for both to finish. */
    public void workTogether(Consumer<Farmhand> first, Consumer<Farmhand> second) {
        if (partner == null) throw new IllegalStateException("This level needs a second farmer.");
        runPair(() -> first.accept(this), "farmer-" + id + "-team",
                () -> second.accept(partner), "farmer-" + partner.id + "-team");
    }

    private static void runPair(Runnable first, String firstName, Runnable second, String secondName) {
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread[] workers = new Thread[2];
        workers[0] = new Thread(() -> runJob(first, start, workers[1], failure), firstName);
        workers[1] = new Thread(() -> runJob(second, start, workers[0], failure), secondName);
        for (Thread worker : workers) worker.setDaemon(true);
        try {
            for (Thread worker : workers) worker.start();
            start.countDown();
            for (Thread worker : workers) worker.join();
        } catch (InterruptedException cancelled) {
            for (Thread worker : workers) worker.interrupt();
            Thread.currentThread().interrupt();
            return;
        }
        Throwable problem = failure.get();
        if (problem instanceof RuntimeException) throw (RuntimeException) problem;
        if (problem instanceof Error) throw (Error) problem;
    }

    private static void runJob(Runnable job, CountDownLatch start, Thread partner,
                               AtomicReference<Throwable> failure) {
        try {
            start.await();
            job.run();
        } catch (InterruptedException cancelled) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException | Error problem) {
            if (failure.compareAndSet(null, problem)) partner.interrupt();
        }
    }
}
