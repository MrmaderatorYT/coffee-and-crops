package com.ccs.coffee_n_crops.lang.ast.expr;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.interp.ScriptError;
import com.ccs.coffee_n_crops.lang.runtime.NativeRegistry;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A call on a built-in object, e.g. {@code farmer.move(Direction.EAST)} or {@code farmer.isRipe()}. */
public final class MethodCall extends Expr {
    public final String receiver;
    public final String name;
    public final JType returnType;
    /** One slot per parameter, typed from the method's signature. */
    public final List<ExprSlot> args;

    public MethodCall(String receiver, String name, Expr... args) {
        NativeRegistry.Entry entry = NativeRegistry.STANDARD.lookup("farmer", name);
        if (entry.params.length != args.length) {
            throw new ScriptError(receiver + "." + name + "() takes " + entry.params.length
                    + " argument(s), not " + args.length);
        }
        this.receiver = receiver;
        this.name = name;
        this.returnType = entry.returnType;
        List<ExprSlot> slots = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            slots.add(new ExprSlot(this, entry.params[i], args[i]));
        }
        this.args = Collections.unmodifiableList(slots);
    }

    @Override
    public Value eval(ExecutionContext ctx) throws InterruptedException {
        Value target = ctx.readVariable(receiver);
        Value[] values = new Value[args.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = args.get(i).eval(ctx);
        }
        return ctx.callInstance(target, name, values, id);
    }
}
