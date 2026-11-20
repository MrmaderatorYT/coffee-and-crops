package com.ccs.coffee_n_crops.lang.ast.stmt;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.interp.LoopJump;
import com.ccs.coffee_n_crops.lang.types.JType;

import java.util.Collections;
import java.util.List;

/** The classic three-part loop: {@code for (init; cond; update) { body }}. */
public final class ForStmt extends Stmt {
    public final VarDecl init;
    public final ExprSlot cond;
    public final Expr update;
    public final Block body = new Block();

    public ForStmt(VarDecl init, Expr cond, Expr update) {
        this.init = init;
        this.cond = new ExprSlot(this, JType.BOOLEAN, cond);
        this.update = update;
        init.parent = this;
        update.parent = this;
        body.parent = this;
    }

    @Override
    public List<Block> childBlocks() {
        return Collections.singletonList(body);
    }

    @Override
    public void exec(ExecutionContext ctx) throws InterruptedException {
        ctx.pushScope(); // the loop variable lives only for the duration of the loop
        try {
            init.declare(ctx);
            while (true) {
                // The header is highlighted on every pass, including the final failing check,
                // so the player sees the condition being re-tested. The wait also keeps a loop
                // that never ends from spinning a CPU core until the player presses Reset.
                ctx.checkpoint(this);
                ctx.clock.awaitTicks(VarDecl.THINK_TICKS);
                if (!cond.eval(ctx).asBoolean()) break;

                ctx.pushScope();
                try {
                    if (!LoopJump.runIteration(body, this, ctx)) break;
                } finally {
                    ctx.popScope();
                }
                update.eval(ctx);
            }
        } finally {
            ctx.popScope();
        }
    }
}
