package com.ccs.coffee_n_crops.lang.interp;

import com.ccs.coffee_n_crops.lang.ast.ControlFlow;
import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;

/** Carries a break or continue through inner blocks and any intervening finally clauses. */
public final class LoopJump extends RuntimeException {
    public final Stmt target;
    public final boolean continuing;

    private LoopJump(Stmt target, boolean continuing) {
        super(null, null, false, false);
        this.target = target;
        this.continuing = continuing;
    }

    public static void execute(Stmt statement, boolean continuing, ExecutionContext ctx) throws InterruptedException {
        Stmt target = ControlFlow.jumpTarget(statement, continuing);
        if (target == null) throw new ScriptError(continuing ? "continue outside of loop" : "break outside switch or loop");
        ctx.checkpoint(statement);
        ctx.recordLessonUse(statement);
        throw new LoopJump(target, continuing);
    }

    /** A matching continue proceeds to the loop update or condition; a matching break skips both. */
    public static boolean runIteration(Block body, Stmt loop, ExecutionContext ctx) throws InterruptedException {
        try {
            body.exec(ctx);
            return true;
        } catch (LoopJump jump) {
            if (jump.target != loop) throw jump;
            return jump.continuing;
        }
    }
}
