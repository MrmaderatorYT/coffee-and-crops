package com.ccs.coffee_n_crops.lang.ast.expr;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;

/** An instance field read with a statically typed receiver, such as this.value or plan.width. */
public final class FieldAccess extends Expr {
    public final ExprSlot receiver;
    public final String name;
    public final JType type;
    public FieldAccess(Expr receiver, JType receiverType, String name, JType type) {
        this.receiver = new ExprSlot(this, receiverType, receiver);
        this.name = name;
        this.type = type;
    }
    @Override public Value eval(ExecutionContext ctx) throws InterruptedException {
        return ctx.readField(receiver.eval(ctx), ctx.expressionType(receiver.get()), name);
    }
}
