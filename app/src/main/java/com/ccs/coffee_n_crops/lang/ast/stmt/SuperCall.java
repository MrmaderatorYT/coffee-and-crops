package com.ccs.coffee_n_crops.lang.ast.stmt;

import com.ccs.coffee_n_crops.lang.ast.ConstructorDecl;
import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.ast.expr.NewObject;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The first statement of a constructor, invoking its immediate superclass constructor. */
public final class SuperCall extends Stmt {
    public final List<ExprSlot> args;
    public SuperCall(ConstructorDecl constructor, Expr... args) { this(NewObject.parameterTypes(constructor), args); }
    public SuperCall(JType[] parameterTypes, Expr... args) {
        List<ExprSlot> slots = new ArrayList<>();
        for (int i = 0; i < args.length; i++) slots.add(new ExprSlot(this,
                i < parameterTypes.length ? parameterTypes[i] : JType.INT, args[i]));
        this.args = Collections.unmodifiableList(slots);
    }
    @Override public void exec(ExecutionContext ctx) throws InterruptedException {
        ctx.checkpoint(this);
        ctx.clock.awaitTicks(VarDecl.THINK_TICKS);
        Value[] values = new Value[args.size()];
        for (int i = 0; i < values.length; i++) values[i] = args.get(i).eval(ctx);
        ctx.callSuper(values);
    }
}
