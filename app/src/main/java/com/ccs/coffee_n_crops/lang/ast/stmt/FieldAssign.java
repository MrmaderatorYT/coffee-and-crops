package com.ccs.coffee_n_crops.lang.ast.stmt;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.ast.expr.FieldAccess;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.types.Value;

/** Writes an object's field without affecting another object's state. */
public final class FieldAssign extends Stmt {
    public final FieldAccess target;
    public final ExprSlot value;
    public FieldAssign(FieldAccess target, Expr value) {
        this.target = target;
        target.parent = this;
        this.value = new ExprSlot(this, target.type, value);
    }
    @Override public void exec(ExecutionContext ctx) throws InterruptedException {
        ctx.checkpoint(this);
        ctx.clock.awaitTicks(VarDecl.THINK_TICKS);
        // Java evaluates the receiver before the right-hand expression.
        Value object = target.receiver.eval(ctx);
        com.ccs.coffee_n_crops.lang.types.JType staticType = ctx.expressionType(target.receiver.get());
        Value result = value.eval(ctx);
        ctx.writeField(object, staticType, target.name, result);
        ctx.recordLessonUse(this);
    }
}
