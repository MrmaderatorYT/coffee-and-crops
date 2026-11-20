package com.ccs.coffee_n_crops.lang.ast.stmt;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.types.JType;

/** Gives an existing variable a new value, e.g. {@code steps = steps + 1;}. */
public final class Assign extends Stmt {
    public final String name;
    public final ExprSlot value;

    /** @param type the declared type of the variable; the value slot accepts only this type */
    public Assign(JType type, String name, Expr value) {
        this.name = name;
        this.value = new ExprSlot(this, type, value);
    }

    @Override
    public void exec(ExecutionContext ctx) throws InterruptedException {
        ctx.checkpoint(this);
        ctx.clock.awaitTicks(VarDecl.THINK_TICKS);
        ctx.assignVariable(name, value.eval(ctx));
    }
}
