package com.ccs.coffee_n_crops.lang.ast.expr;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;

/** Creates a null-filled array of class or interface references. */
public final class NewObjectArray extends Expr {
    public final JType elementType;
    public final ExprSlot length;

    public NewObjectArray(JType elementType, Expr length) {
        this.elementType = elementType;
        this.length = new ExprSlot(this, JType.INT, length);
    }

    @Override public Value eval(ExecutionContext ctx) throws InterruptedException {
        Value array = ctx.newObjectArray(elementType, length.eval(ctx).asInt());
        ctx.recordLessonUse(this);
        return array;
    }
}
