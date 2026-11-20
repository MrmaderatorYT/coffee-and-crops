package com.ccs.coffee_n_crops.lang.ast.stmt;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.interp.ScriptError;
import com.ccs.coffee_n_crops.lang.types.JType;

/** Replaces one element of an int array: {@code row[index] = value;}. */
public final class ArrayAssign extends Stmt {
    public final String name;
    public final ExprSlot index;
    public final ExprSlot value;

    public ArrayAssign(String name, Expr index, Expr value) {
        this.name = name;
        this.index = new ExprSlot(this, JType.INT, index);
        this.value = new ExprSlot(this, JType.INT, value);
    }

    @Override
    public void exec(ExecutionContext ctx) throws InterruptedException {
        ctx.checkpoint(this);
        ctx.clock.awaitTicks(VarDecl.THINK_TICKS);
        int[] array = ctx.readVariable(name).asIntArray();
        int target = index.eval(ctx).asInt();
        // Java evaluates both operands before checking the destination of a simple array store.
        int replacement = value.eval(ctx).asInt();
        if (target < 0 || target >= array.length) {
            throw new ScriptError("ArrayIndexOutOfBoundsException: Index " + target
                    + " out of bounds for length " + array.length);
        }
        array[target] = replacement;
        ctx.recordLessonUse(this);
    }
}
