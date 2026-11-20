package com.ccs.coffee_n_crops.lang.ast.expr;

import com.ccs.coffee_n_crops.lang.ast.ClassDecl;
import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.ast.ConstructorDecl;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Allocates a user object and invokes its interpreted constructor. */
public final class NewObject extends Expr {
    public final String className;
    public final List<ExprSlot> args;
    public NewObject(ClassDecl declaration, Expr... args) {
        this(declaration.name, parameterTypes(declaration.constructor()), args);
    }
    public NewObject(String className, JType[] parameterTypes, Expr... args) {
        this.className = className;
        List<ExprSlot> slots = new ArrayList<>();
        for (int i = 0; i < args.length; i++) slots.add(new ExprSlot(this,
                i < parameterTypes.length ? parameterTypes[i] : JType.INT, args[i]));
        this.args = Collections.unmodifiableList(slots);
    }
    public static JType[] parameterTypes(ConstructorDecl constructor) {
        if (constructor == null) return new JType[0];
        JType[] types = new JType[constructor.parameters.size()];
        for (int i = 0; i < types.length; i++) types[i] = constructor.parameters.get(i).type;
        return types;
    }
    @Override public Value eval(ExecutionContext ctx) throws InterruptedException {
        Value[] values = new Value[args.size()];
        for (int i = 0; i < values.length; i++) values[i] = args.get(i).eval(ctx);
        return ctx.newObject(className, values);
    }
}
