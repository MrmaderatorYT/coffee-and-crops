package com.ccs.coffee_n_crops.lang.ast.stmt;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.interp.ScriptError;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;

/** Replaces one element of a one- or two-dimensional int array. */
public final class ArrayAssign extends Stmt {
    public final String name;
    public final ExprSlot index;
    public final ExprSlot column;
    public final ExprSlot value;

    public ArrayAssign(String name, Expr index, Expr value) {
        this(name, index, null, value);
    }

    /** For a grid, index selects a row and column selects an element of that row. */
    public ArrayAssign(String name, Expr index, Expr column, Expr value) {
        this.name = name;
        this.index = new ExprSlot(this, JType.INT, index);
        this.column = column == null ? null : new ExprSlot(this, JType.INT, column);
        this.value = new ExprSlot(this, JType.INT, value);
    }

    @Override
    public void exec(ExecutionContext ctx) throws InterruptedException {
        ctx.checkpoint(this);
        ctx.clock.awaitTicks(VarDecl.THINK_TICKS);
        Value receiver = ctx.readVariable(name);
        int target = index.eval(ctx).asInt();
        int[] array;
        if (column == null) {
            array = receiver.asIntArray();
        } else {
            int[][] grid = receiver.asIntGrid();
            if (grid == null) throw new ScriptError("NullPointerException");
            array = grid[checkedIndex(target, grid.length)];
            target = column.eval(ctx).asInt();
        }
        // Java evaluates the right operand before checking the destination of the final store.
        int replacement = value.eval(ctx).asInt();
        if (array == null) throw new ScriptError("NullPointerException");
        array[checkedIndex(target, array.length)] = replacement;
        ctx.recordLessonUse(this);
    }

    private static int checkedIndex(int index, int length) {
        if (index < 0 || index >= length) {
            throw new ScriptError("ArrayIndexOutOfBoundsException: Index " + index
                    + " out of bounds for length " + length);
        }
        return index;
    }
}
