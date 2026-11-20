package com.ccs.coffee_n_crops.lang.ast.stmt;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.types.JType;

/** Appends to a list variable: {@code list.add(value);}. */
public final class ListAdd extends Stmt {
    public final String name;
    public final ExprSlot value;

    public ListAdd(String name, Expr value) {
        this.name = name;
        this.value = new ExprSlot(this, JType.INT, value);
    }

    @Override
    public void exec(ExecutionContext ctx) throws InterruptedException {
        ctx.checkpoint(this);
        ctx.clock.awaitTicks(VarDecl.THINK_TICKS);
        ctx.readVariable(name).asIntList().add(value.eval(ctx).asInt());
    }
}
