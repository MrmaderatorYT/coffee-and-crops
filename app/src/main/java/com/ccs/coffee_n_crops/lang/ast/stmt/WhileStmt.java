package com.ccs.coffee_n_crops.lang.ast.stmt;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.interp.LoopJump;
import com.ccs.coffee_n_crops.lang.types.JType;

import java.util.Collections;
import java.util.List;

/** {@code while (cond) { body }}: repeats for as long as the condition holds. */
public final class WhileStmt extends Stmt {
    public final ExprSlot cond;
    public final Block body = new Block();

    public WhileStmt(Expr cond) {
        this.cond = new ExprSlot(this, JType.BOOLEAN, cond);
        this.body.parent = this;
    }

    @Override
    public List<Block> childBlocks() {
        return Collections.singletonList(body);
    }

    @Override
    public void exec(ExecutionContext ctx) throws InterruptedException {
        while (true) {
            // The wait on every pass keeps while (true) { } from spinning a CPU core:
            // an endless loop just keeps running until the player presses Reset.
            ctx.checkpoint(this);
            ctx.clock.awaitTicks(VarDecl.THINK_TICKS);
            if (!cond.eval(ctx).asBoolean()) return;

            ctx.pushScope();
            try {
                if (!LoopJump.runIteration(body, this, ctx)) return;
            } finally {
                ctx.popScope();
            }
        }
    }
}
