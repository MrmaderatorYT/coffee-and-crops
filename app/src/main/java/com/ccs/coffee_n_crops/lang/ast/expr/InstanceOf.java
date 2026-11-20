package com.ccs.coffee_n_crops.lang.ast.expr;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.runtime.ObjectMethods;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;

/** Tests a non-null object's actual type, including inherited interfaces. */
public final class InstanceOf extends Expr {
    public final ExprSlot operand;
    public final JType target;

    public InstanceOf(Expr operand, JType target) {
        this.operand = new ExprSlot(this, JType.OBJECT, operand);
        this.target = target;
    }

    @Override public Value eval(ExecutionContext ctx) throws InterruptedException {
        Value value = operand.eval(ctx);
        ctx.recordLessonUse(this);
        return Value.ofBoolean(value.raw != null && ctx.classes().assignable(ObjectMethods.runtimeType(value), target));
    }
}
