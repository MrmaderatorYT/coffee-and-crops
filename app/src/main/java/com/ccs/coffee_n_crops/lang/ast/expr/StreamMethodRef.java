package com.ccs.coffee_n_crops.lang.ast.expr;

import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.interp.ScriptError;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;

import java.util.List;

/** A bound reference to the one-argument {@code ArrayList<Integer>.add} as a consumer. */
public final class StreamMethodRef extends StreamCallback {
    public final String receiver;
    public final String method;

    public StreamMethodRef(String receiver, String method) {
        super(JType.VOID);
        this.receiver = receiver;
        this.method = method;
    }

    @Override Bound bind(ExecutionContext ctx) {
        List<Integer> target = ctx.readVariable(receiver).asIntList();
        if (target == null) throw new ScriptError("NullPointerException");
        if (!method.equals("add")) throw new ScriptError("invalid method reference");
        return (call, element) -> {
            target.add(element);
            call.recordLessonUse(this);
            return Value.VOID;
        };
    }
}
