package com.ccs.coffee_n_crops.lang.ast.expr;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.interp.ScriptError;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;

/** A rectangular, zero-filled array allocated by new int[rows][columns]. */
public final class NewIntGrid extends Expr {
    private static final int MAX_DIMENSION = 16_384;
    private static final int MAX_CELLS = 65_536;

    public final ExprSlot rows;
    public final ExprSlot columns;

    public NewIntGrid(Expr rows, Expr columns) {
        this.rows = new ExprSlot(this, JType.INT, rows);
        this.columns = new ExprSlot(this, JType.INT, columns);
    }

    @Override
    public Value eval(ExecutionContext ctx) throws InterruptedException {
        int height = rows.eval(ctx).asInt();
        int width = columns.eval(ctx).asInt();
        if (height < 0 || width < 0) {
            throw new ScriptError("NegativeArraySizeException: " + (height < 0 ? height : width));
        }
        if (height > MAX_DIMENSION || width > MAX_DIMENSION || (long) height * width > MAX_CELLS) {
            throw new ScriptError("OutOfMemoryError: Requested array size exceeds VM limit");
        }
        ctx.recordLessonUse(this);
        return new Value(JType.INT_GRID, new int[height][width]);
    }
}
