package com.ccs.coffee_n_crops.lang.ast.expr;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.types.Value;

/** Reads a class's compile-time constant through its type name. */
public final class StaticConstant extends Expr {
    public final String className;
    public final String fieldName;

    public StaticConstant(String className, String fieldName) {
        this.className = className;
        this.fieldName = fieldName;
    }

    @Override public Value eval(ExecutionContext ctx) throws InterruptedException {
        Value result = ctx.readStaticConstant(className, fieldName);
        ctx.recordLessonUse(this);
        return result;
    }
}
