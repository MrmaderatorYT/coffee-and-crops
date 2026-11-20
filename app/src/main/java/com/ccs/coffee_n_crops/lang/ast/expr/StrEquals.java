package com.ccs.coffee_n_crops.lang.ast.expr;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;

/**
 * String comparison the Java way: {@code left.equals(right)}. There is deliberately no
 * {@code ==} for strings in the game, because in Java that compares references, not text.
 */
public final class StrEquals extends Expr {
    public final ExprSlot left;
    public final ExprSlot right;

    public StrEquals(Expr left, Expr right) {
        this.left = new ExprSlot(this, JType.STRING, left);
        this.right = new ExprSlot(this, JType.STRING, right);
    }

    @Override
    public Value eval(ExecutionContext ctx) throws InterruptedException {
        return Value.ofBoolean(left.eval(ctx).asString().equals(right.eval(ctx).asString()));
    }
}
