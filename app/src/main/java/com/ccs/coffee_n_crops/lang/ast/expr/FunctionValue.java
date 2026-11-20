package com.ccs.coffee_n_crops.lang.ast.expr;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.interp.ScriptError;
import com.ccs.coffee_n_crops.lang.types.Value;

/** Stores an expression lambda or method reference in a functional interface variable. */
public final class FunctionValue extends Expr {
    public final FunctionType function;
    public final StreamCallback callback;

    public FunctionValue(FunctionType function, StreamCallback callback) {
        this.function = function;
        this.callback = callback;
        callback.parent = this;
    }

    @Override
    public Value eval(ExecutionContext ctx) throws InterruptedException {
        StreamCallback.Bound bound = callback.bind(ctx);
        ctx.recordLessonUse(this);
        return new Value(function.type, bound);
    }

    static StreamCallback.Bound bound(Value value, FunctionType expected) {
        if (value.raw == null) throw new ScriptError("NullPointerException");
        if (value.type != expected.type || !(value.raw instanceof StreamCallback.Bound bound)) {
            throw new ScriptError("incompatible types: expected " + expected.type.javaName);
        }
        return bound;
    }
}
