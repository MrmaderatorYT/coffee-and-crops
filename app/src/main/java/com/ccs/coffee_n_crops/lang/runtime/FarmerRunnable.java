package com.ccs.coffee_n_crops.lang.runtime;

import com.ccs.coffee_n_crops.lang.ast.Node;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.interp.ScriptError;

/** The body of one farmer's thread: walk the program's AST from top to bottom. */
public final class FarmerRunnable implements Runnable {
    private final Block program;
    private final ExecutionContext ctx;

    public FarmerRunnable(Block program, ExecutionContext ctx) {
        this.program = program;
        this.ctx = ctx;
    }

    @Override
    public void run() {
        try {
            program.exec(ctx);
            ctx.emitObjectState();
            if (ctx.isActive()) ctx.listener.onFinished(ctx.farmer.id);
        } catch (InterruptedException e) {
            // The run was stopped from outside: unwind quietly.
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            // An uncaught error in the player's program: the farmer stops, the app keeps running.
            if (!ctx.isActive()) return;
            Node at = ctx.currentNode();
            // Farm exceptions are named after their class; plain script errors already carry
            // the Java exception name in their message (e.g. ArrayIndexOutOfBoundsException).
            String message = e.getClass() == ScriptError.class ? e.getMessage()
                    : e.getClass().getSimpleName() + ": " + e.getMessage();
            ctx.listener.onError(ctx.currentFarmerId(), at == null ? -1 : at.id, message);
        }
    }
}
