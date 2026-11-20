package com.ccs.coffee_n_crops.lang.ast.stmt;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.interp.ScriptError;
import com.ccs.coffee_n_crops.lang.types.JType;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/** A Java lock/try/finally/unlock idiom kept together as one editable block. */
public final class LockStmt extends Stmt {
    public final ExprSlot lock;
    public final Block body = new Block();

    public LockStmt(Expr lock) {
        this.lock = new ExprSlot(this, JType.REENTRANT_LOCK, lock);
        body.parent = this;
    }

    @Override public List<Block> childBlocks() {
        return Collections.singletonList(body);
    }

    @Override public void exec(ExecutionContext ctx) throws InterruptedException {
        ctx.checkpoint(this);
        Object target = lock.eval(ctx).raw;
        if (target == null) throw new ScriptError("NullPointerException");
        if (!(target instanceof ReentrantLock mutex)) throw new ScriptError("incompatible types: expected ReentrantLock");
        String name = lock.get() instanceof VarRef variable ? variable.name : "lock";
        ctx.locks().acquire(mutex, name, ctx.farmer.id);
        try {
            ctx.recordLessonUse(this);
            ctx.clock.awaitTicks(VarDecl.THINK_TICKS);
            ctx.checkActive();
            ctx.pushScope();
            try {
                body.exec(ctx);
            } finally {
                ctx.popScope();
            }
        } finally {
            ctx.locks().release(mutex);
        }
    }
}
