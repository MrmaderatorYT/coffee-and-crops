package com.ccs.coffee_n_crops.lang.ast.expr;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;

/** Java string concatenation with a String left operand. */
public final class StringConcat extends Expr {
    public final ExprSlot left;
    public final ExprSlot right;

    public StringConcat(Expr left, JType rightType, Expr right) {
        this.left = new ExprSlot(this, JType.STRING, left);
        this.right = new ExprSlot(this, rightType, right);
    }

    @Override
    public Value eval(ExecutionContext ctx) throws InterruptedException {
        String prefix = String.valueOf(left.eval(ctx).raw);
        String suffix = String.valueOf(right.eval(ctx).raw);
        ctx.recordLessonUse(this);
        return Value.ofString(prefix + suffix);
    }
}
