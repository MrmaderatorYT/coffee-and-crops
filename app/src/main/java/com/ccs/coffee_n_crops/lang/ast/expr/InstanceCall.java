package com.ccs.coffee_n_crops.lang.ast.expr;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.ast.MethodDecl;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.runtime.ObjectMethods;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Calls an instance method, using the runtime class for overridden method dispatch. */
public final class InstanceCall extends Expr {
    public final ExprSlot receiver;
    public final String name;
    public final JType returnType;
    public final List<ExprSlot> args;
    public InstanceCall(Expr receiver, JType receiverType, MethodDecl method, Expr... args) {
        this(receiver, receiverType, method.name, method.returnType, parameterTypes(method), args);
    }
    public InstanceCall(Expr receiver, JType receiverType, String name, JType returnType,
                        JType[] parameterTypes, Expr... args) {
        this.receiver = new ExprSlot(this, receiverType, receiver);
        this.name = name;
        this.returnType = returnType;
        List<ExprSlot> slots = new ArrayList<>();
        for (int i = 0; i < args.length; i++) slots.add(new ExprSlot(this,
                i < parameterTypes.length ? parameterTypes[i] : JType.INT, args[i]));
        this.args = Collections.unmodifiableList(slots);
    }
    private static JType[] parameterTypes(MethodDecl method) {
        JType[] types = new JType[method.parameters.size()];
        for (int i = 0; i < types.length; i++) types[i] = method.parameters.get(i).type;
        return types;
    }
    @Override public Value eval(ExecutionContext ctx) throws InterruptedException {
        Value target = receiver.eval(ctx);
        Value[] values = new Value[args.size()];
        for (int i = 0; i < values.length; i++) values[i] = args.get(i).eval(ctx);
        Value result = ctx.callInstance(target, name, values, id);
        if (ObjectMethods.contains(name)) ctx.recordLessonUse(this);
        return result;
    }
}
