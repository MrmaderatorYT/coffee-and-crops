package com.ccs.coffee_n_crops.lang.ast.expr;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.types.Value;

/** The current instance; unavailable in Program's entry point and legacy helpers. */
public final class ThisRef extends Expr {
    @Override public Value eval(ExecutionContext ctx) { return ctx.thisValue(); }
}
