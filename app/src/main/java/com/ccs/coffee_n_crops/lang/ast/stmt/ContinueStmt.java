package com.ccs.coffee_n_crops.lang.ast.stmt;

import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.interp.LoopJump;

/** Advances the nearest loop, even when a switch encloses the statement. */
public final class ContinueStmt extends Stmt {
    @Override
    public void exec(ExecutionContext ctx) throws InterruptedException {
        LoopJump.execute(this, true, ctx);
    }
}
