package com.ccs.coffee_n_crops.lang.ast.expr;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.types.Value;

/** Reads a variable by name, e.g. the {@code i} in {@code i < 5}. */
public final class VarRef extends Expr {
    public final String name;

    public VarRef(String name) {
        this.name = name;
    }

    @Override
    public Value eval(ExecutionContext ctx) {
        return ctx.readVariable(name);
    }
}
