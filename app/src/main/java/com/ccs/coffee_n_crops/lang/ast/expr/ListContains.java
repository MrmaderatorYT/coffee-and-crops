package com.ccs.coffee_n_crops.lang.ast.expr;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;

import java.util.List;

/** Tests whether an integer appears anywhere in a list. */
public final class ListContains extends Expr {
    public final String name;
    public final ExprSlot value;

    public ListContains(String name, Expr value) {
        this.name = name;
        this.value = new ExprSlot(this, JType.INT, value);
    }

    @Override
    public Value eval(ExecutionContext ctx) throws InterruptedException {
        List<Integer> list = ctx.readVariable(name).asIntList();
        boolean found = list.contains(value.eval(ctx).asInt());
        ctx.recordLessonUse(this);
        return Value.ofBoolean(found);
    }
}
