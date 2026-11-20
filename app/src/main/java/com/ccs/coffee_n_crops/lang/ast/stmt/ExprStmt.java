package com.ccs.coffee_n_crops.lang.ast.stmt;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;

/** An expression used as a statement, e.g. {@code farmer.harvest();}. */
public final class ExprStmt extends Stmt {
    public final Expr expr;

    public ExprStmt(Expr expr) {
        this.expr = expr;
        expr.parent = this;
    }

    @Override
    public void exec(ExecutionContext ctx) throws InterruptedException {
        ctx.checkpoint(this);
        expr.eval(ctx);
    }
}
