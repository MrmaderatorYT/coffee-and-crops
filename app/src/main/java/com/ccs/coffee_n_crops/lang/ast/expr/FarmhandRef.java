package com.ccs.coffee_n_crops.lang.ast.expr;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.types.Value;

/** The Farmhand parameter in run, a helper or an explicitly declared instance member. */
public final class FarmhandRef extends Expr {
    @Override public Value eval(ExecutionContext ctx) { return ctx.readVariable("farmer"); }
}
