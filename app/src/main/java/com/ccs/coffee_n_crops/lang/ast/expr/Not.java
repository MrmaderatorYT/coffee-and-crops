package com.ccs.coffee_n_crops.lang.ast.expr;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;

/** Logical negation: {@code !operand}. */
public final class Not extends Expr {
    public final ExprSlot operand;

    public Not(Expr operand) {
        this.operand = new ExprSlot(this, JType.BOOLEAN, operand);
    }

    @Override
    public Value eval(ExecutionContext ctx) throws InterruptedException {
        return Value.ofBoolean(!operand.eval(ctx).asBoolean());
    }
}
