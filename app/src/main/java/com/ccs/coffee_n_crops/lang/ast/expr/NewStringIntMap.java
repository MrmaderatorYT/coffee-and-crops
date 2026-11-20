package com.ccs.coffee_n_crops.lang.ast.expr;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;

import java.util.HashMap;

/** Creates an empty map of string keys to integer counts. */
public final class NewStringIntMap extends Expr {
    @Override
    public Value eval(ExecutionContext ctx) throws InterruptedException {
        ctx.recordLessonUse(this);
        return new Value(JType.STRING_INT_MAP, new HashMap<String, Integer>());
    }
}
