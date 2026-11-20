package com.ccs.coffee_n_crops.lang.ast.stmt;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.interp.ReturnSignal;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;

/** Returns from the current method, unwinding every loop and block in that call. */
public final class ReturnStmt extends Stmt {
    public final ExprSlot value;

    public ReturnStmt() {
        value = null;
    }

    public ReturnStmt(JType returnType, Expr expression) {
        value = new ExprSlot(this, returnType, expression);
    }

    @Override
    public void exec(ExecutionContext ctx) throws InterruptedException {
        ctx.checkpoint(this);
        throw new ReturnSignal(value == null ? Value.VOID : value.eval(ctx));
    }
}
