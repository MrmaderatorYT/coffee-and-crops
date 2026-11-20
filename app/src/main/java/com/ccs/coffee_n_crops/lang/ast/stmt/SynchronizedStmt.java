package com.ccs.coffee_n_crops.lang.ast.stmt;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.interp.ScriptError;
import com.ccs.coffee_n_crops.lang.types.JType;

import java.util.Collections;
import java.util.List;

/** A Java synchronized block on a shared reference. */
public final class SynchronizedStmt extends Stmt {
    public final ExprSlot monitor;
    public final Block body = new Block();

    public SynchronizedStmt(JType monitorType, Expr monitor) {
        this.monitor = new ExprSlot(this, monitorType, monitor);
        body.parent = this;
    }

    @Override public List<Block> childBlocks() { return Collections.singletonList(body); }

    @Override public void exec(ExecutionContext ctx) throws InterruptedException {
        ctx.checkpoint(this);
        Object lock = monitor.eval(ctx).raw;
        if (lock == null) throw new ScriptError("NullPointerException");
        synchronized (lock) {
            ctx.checkActive();
            ctx.pushScope();
            try {
                body.exec(ctx);
                ctx.recordLessonUse(this);
            } finally {
                ctx.popScope();
            }
        }
    }
}
