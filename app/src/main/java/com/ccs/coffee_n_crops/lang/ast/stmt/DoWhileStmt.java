package com.ccs.coffee_n_crops.lang.ast.stmt;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.ast.Node;
import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.interp.LoopJump;
import com.ccs.coffee_n_crops.lang.types.JType;

import java.util.Collections;
import java.util.List;

/** A post-test loop: its body runs once before the condition is evaluated. */
public final class DoWhileStmt extends Stmt {
    public final Block body = new Block();
    /** Debugger anchor for the trailing {@code while (...);} condition. */
    public final Node conditionLine = new Node() {};
    public final ExprSlot cond;

    public DoWhileStmt(Expr condition) {
        body.parent = this;
        conditionLine.parent = this;
        cond = new ExprSlot(conditionLine, JType.BOOLEAN, condition);
    }

    @Override
    public List<Block> childBlocks() {
        return Collections.singletonList(body);
    }

    @Override
    public void exec(ExecutionContext ctx) throws InterruptedException {
        ctx.checkpoint(this);
        ctx.recordLessonUse(this);
        do {
            ctx.pushScope();
            try {
                if (!LoopJump.runIteration(body, this, ctx)) return;
            } finally {
                ctx.popScope();
            }
            ctx.checkpoint(conditionLine);
            ctx.clock.awaitTicks(VarDecl.THINK_TICKS);
        } while (cond.eval(ctx).asBoolean());
    }
}
