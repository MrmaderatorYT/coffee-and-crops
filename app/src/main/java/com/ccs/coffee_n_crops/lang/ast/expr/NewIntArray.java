package com.ccs.coffee_n_crops.lang.ast.expr;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.interp.ScriptError;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;

/** Creates a zero-filled array with the length in `new int[length]`. */
public final class NewIntArray extends Expr {
    // Bound individual allocations from player code before allocating on the Android heap.
    private static final int MAX_LENGTH = 16_384;

    public final ExprSlot length;

    public NewIntArray(Expr length) {
        this.length = new ExprSlot(this, JType.INT, length);
    }

    @Override
    public Value eval(ExecutionContext ctx) throws InterruptedException {
        int size = length.eval(ctx).asInt();
        if (size < 0) throw new ScriptError("NegativeArraySizeException: " + size);
        if (size > MAX_LENGTH) throw new ScriptError("OutOfMemoryError: Requested array size exceeds VM limit");
        int[] array = new int[size];
        ctx.recordLessonUse(this);
        return new Value(JType.INT_ARRAY, array);
    }
}
