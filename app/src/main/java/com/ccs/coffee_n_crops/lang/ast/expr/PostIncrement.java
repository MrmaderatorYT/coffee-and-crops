package com.ccs.coffee_n_crops.lang.ast.expr;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.types.Value;

/** {@code name++}: adds one to an int variable and evaluates to its old value. */
public final class PostIncrement extends Expr {
    public final String name;

    public PostIncrement(String name) {
        this.name = name;
    }

    @Override
    public Value eval(ExecutionContext ctx) throws InterruptedException {
        Value old = ctx.readVariable(name);
        ctx.assignVariable(name, Value.ofInt(old.asInt() + 1));
        return old;
    }
}
