package com.ccs.coffee_n_crops.lang.ast.expr;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;

/** Reads an element through an object array's declared component type. */
public final class ObjectArrayGet extends Expr {
    public final String name;
    public final JType elementType;
    public final ExprSlot index;

    public ObjectArrayGet(String name, JType elementType, Expr index) {
        this.name = name;
        this.elementType = elementType;
        this.index = new ExprSlot(this, JType.INT, index);
    }

    @Override public Value eval(ExecutionContext ctx) throws InterruptedException {
        Value target = ctx.readVariable(name);
        int offset = index.eval(ctx).asInt();
        Value element = target.asObjectArray().get(offset, target.type.elementType);
        ctx.recordLessonUse(this);
        return element;
    }
}
