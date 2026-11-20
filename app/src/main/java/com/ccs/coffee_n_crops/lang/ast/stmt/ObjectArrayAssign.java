package com.ccs.coffee_n_crops.lang.ast.stmt;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;

/** Writes a reference into an object array at the given index. */
public final class ObjectArrayAssign extends Stmt {
    public final String name;
    public final JType elementType;
    public final ExprSlot index;
    public final ExprSlot value;

    public ObjectArrayAssign(String name, JType elementType, Expr index, Expr value) {
        this.name = name;
        this.elementType = elementType;
        this.index = new ExprSlot(this, JType.INT, index);
        this.value = new ExprSlot(this, elementType, value);
    }

    @Override public void exec(ExecutionContext ctx) throws InterruptedException {
        ctx.checkpoint(this);
        ctx.clock.awaitTicks(VarDecl.THINK_TICKS);
        Value target = ctx.readVariable(name);
        int offset = index.eval(ctx).asInt();
        // A simple Java array store evaluates the right side before checking the destination.
        Value replacement = value.eval(ctx);
        target.asObjectArray().set(offset, replacement, ctx.classes());
        ctx.recordLessonUse(this);
    }
}
