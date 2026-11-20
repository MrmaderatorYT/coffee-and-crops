package com.ccs.coffee_n_crops.lang.ast.stmt;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.interp.ScriptError;
import com.ccs.coffee_n_crops.lang.types.JType;

/** A Java assertion. The game runs player code with assertions enabled, like Java's {@code -ea}. */
public final class AssertStmt extends Stmt {
    public final ExprSlot condition;
    public final ExprSlot detail;

    public AssertStmt(Expr condition, Expr detail) {
        this.condition = new ExprSlot(this, JType.BOOLEAN, condition);
        this.detail = detail == null ? null : new ExprSlot(this, JType.STRING, detail);
    }

    @Override public void exec(ExecutionContext ctx) throws InterruptedException {
        ctx.checkpoint(this);
        ctx.clock.awaitTicks(VarDecl.THINK_TICKS);
        if (!condition.eval(ctx).asBoolean()) {
            throw new ScriptError(detail == null ? "AssertionError"
                    : "AssertionError: " + String.valueOf(detail.eval(ctx).raw));
        }
        ctx.recordLessonUse(this);
    }
}
