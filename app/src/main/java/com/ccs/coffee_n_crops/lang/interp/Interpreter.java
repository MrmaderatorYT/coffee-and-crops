package com.ccs.coffee_n_crops.lang.interp;

import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.farm.WorldClock;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.runtime.FarmerRunnable;
import com.ccs.coffee_n_crops.lang.runtime.NativeRegistry;

/**
 * Starts and stops program runs. Each farmer executes its AST on its own Java thread;
 * the MVP has one farmer, the concurrency chapter starts several against the same world.
 */
public final class Interpreter {
    public final DebugController debug = new DebugController();
    public final WorldClock clock;
    private final NativeRegistry natives = NativeRegistry.STANDARD;
    private Thread thread;
    private ExecutionContext context;

    public Interpreter(WorldClock clock) {
        this.clock = clock;
    }

    public synchronized void start(Block program, FarmWorld world, ExecListener listener, boolean startPaused) {
        stop();
        if (startPaused) debug.pause(); else debug.play();
        ExecutionContext ctx = new ExecutionContext(world.farmer, world, debug, clock, natives, listener);
        context = ctx;
        thread = new Thread(new FarmerRunnable(program, ctx), "farmer-" + world.farmer.id);
        thread.setDaemon(true);
        thread.start();
    }

    /** Invalidates and interrupts the run without blocking the calling UI thread. */
    public synchronized void stop() {
        ExecutionContext stopped = context;
        context = null;
        if (stopped != null) stopped.cancel();
        Thread t = thread;
        thread = null;
        if (t != null) t.interrupt();
    }

    public synchronized boolean isRunning() {
        return thread != null && thread.isAlive();
    }
}
