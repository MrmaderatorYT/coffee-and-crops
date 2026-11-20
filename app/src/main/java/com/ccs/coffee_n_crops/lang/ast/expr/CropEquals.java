package com.ccs.coffee_n_crops.lang.ast.expr;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;

/** Compares two values of the Crop enum with Java's reference equality operator. */
public final class CropEquals extends Expr {
    public final ExprSlot left;
    public final ExprSlot right;

    public CropEquals(Expr left, Expr right) {
        this.left = new ExprSlot(this, JType.CROP, left);
        this.right = new ExprSlot(this, JType.CROP, right);
    }

    @Override
    public Value eval(ExecutionContext ctx) throws InterruptedException {
        Object a = left.eval(ctx).raw;
        Object b = right.eval(ctx).raw;
        ctx.recordLessonUse(this);
        return Value.ofBoolean(a == b);
    }
}
