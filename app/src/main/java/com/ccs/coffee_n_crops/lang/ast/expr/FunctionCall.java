package com.ccs.coffee_n_crops.lang.ast.expr;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;

/** Invokes a standalone functional interface on an integer input. */
public final class FunctionCall extends Expr {
    public final String name;
    public final FunctionType function;
    public final ExprSlot argument;

    public FunctionCall(String name, FunctionType function, Expr argument) {
        this.name = name;
        this.function = function;
        this.argument = new ExprSlot(this, JType.INT, argument);
    }

    @Override
    public Value eval(ExecutionContext ctx) throws InterruptedException {
        Value receiver = ctx.readVariable(name);
        int input = argument.eval(ctx).asInt();
        Value result = FunctionValue.bound(receiver, function).apply(ctx, input);
        ctx.recordLessonUse(this);
        return result;
    }
}
