package com.ccs.coffee_n_crops.lang.ast.stmt;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.interp.ScriptError;
import com.ccs.coffee_n_crops.lang.types.JType;

import java.util.List;

/** Replaces one list element without changing the list size. */
public final class ListSet extends Stmt {
    public final String name;
    public final ExprSlot index;
    public final ExprSlot value;

    public ListSet(String name, Expr index, Expr value) {
        this.name = name;
        this.index = new ExprSlot(this, JType.INT, index);
        this.value = new ExprSlot(this, JType.INT, value);
    }

    @Override
    public void exec(ExecutionContext ctx) throws InterruptedException {
        ctx.checkpoint(this);
        ctx.clock.awaitTicks(VarDecl.THINK_TICKS);
        List<Integer> list = ctx.readVariable(name).asIntList();
        int target = index.eval(ctx).asInt();
        int replacement = value.eval(ctx).asInt();
        if (target < 0 || target >= list.size()) {
            throw new ScriptError("IndexOutOfBoundsException: Index " + target
                    + " out of bounds for length " + list.size());
        }
        list.set(target, replacement);
        ctx.recordLessonUse(this);
    }
}
