package com.ccs.coffee_n_crops.lang.ast.stmt;

import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.types.Value;

/** Standalone integer post-decrement: {@code remaining--;}. */
public final class DecrementStmt extends Stmt {
    public final String name;

    public DecrementStmt(String name) {
        this.name = name;
    }

    @Override
    public void exec(ExecutionContext ctx) throws InterruptedException {
        ctx.checkpoint(this);
        ctx.clock.awaitTicks(VarDecl.THINK_TICKS);
        ctx.assignVariable(name, Value.ofInt(ctx.readVariable(name).asInt() - 1));
        ctx.recordLessonUse(this);
    }
}
