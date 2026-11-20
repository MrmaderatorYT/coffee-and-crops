package com.ccs.coffee_n_crops.lang.ast.stmt;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;

/** Integer compound addition: {@code count += 1;}. */
public final class CompoundAssign extends Stmt {
    public final String name;
    public final ExprSlot value;

    public CompoundAssign(String name, Expr value) {
        this.name = name;
        this.value = new ExprSlot(this, JType.INT, value);
    }

    @Override
    public void exec(ExecutionContext ctx) throws InterruptedException {
        ctx.checkpoint(this);
        ctx.clock.awaitTicks(VarDecl.THINK_TICKS);
        int previous = ctx.readVariable(name).asInt();
        ctx.assignVariable(name, Value.ofInt(previous + value.eval(ctx).asInt()));
        ctx.recordLessonUse(this);
    }
}
