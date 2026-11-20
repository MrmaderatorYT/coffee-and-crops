package com.ccs.coffee_n_crops.lang.ast.expr;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.ast.MethodDecl;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A call to a player's helper, with the fixed {@code farmer} argument supplied automatically. */
public final class UserCall extends Expr {
    public final String name;
    public final JType returnType;
    public final List<ExprSlot> args;

    public UserCall(MethodDecl method, Expr... args) {
        this(method.name, method.returnType, parameterTypes(method), args);
    }

    public UserCall(String name, JType returnType, JType[] parameterTypes, Expr... args) {
        this.name = name;
        this.returnType = returnType;
        List<ExprSlot> slots = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            // Keep malformed calls loadable so the validator can report a Java compiler error.
            JType type = i < parameterTypes.length ? parameterTypes[i] : JType.INT;
            slots.add(new ExprSlot(this, type, args[i]));
        }
        this.args = Collections.unmodifiableList(slots);
    }

    private static JType[] parameterTypes(MethodDecl method) {
        JType[] types = new JType[method.parameters.size()];
        for (int i = 0; i < types.length; i++) types[i] = method.parameters.get(i).type;
        return types;
    }

    @Override
    public Value eval(ExecutionContext ctx) throws InterruptedException {
        Value[] values = new Value[args.size()];
        for (int i = 0; i < values.length; i++) values[i] = args.get(i).eval(ctx);
        return ctx.callMethod(name, values);
    }
}
