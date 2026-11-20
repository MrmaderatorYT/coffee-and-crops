package com.ccs.coffee_n_crops.lang.ast.stmt;

import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.interp.LoopJump;

/** Exits the nearest loop or switch, including from a nested conditional. */
public final class BreakStmt extends Stmt {
    @Override
    public void exec(ExecutionContext ctx) throws InterruptedException {
        LoopJump.execute(this, false, ctx);
    }
}
