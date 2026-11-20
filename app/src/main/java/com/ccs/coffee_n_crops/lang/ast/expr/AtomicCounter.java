package com.ccs.coffee_n_crops.lang.ast.expr;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.interp.ScriptError;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;

import java.util.concurrent.atomic.AtomicInteger;

/** Creates an AtomicInteger or invokes an integer operation on one. */
public final class AtomicCounter extends Expr {
    public enum Op {
        NEW("", JType.ATOMIC_INTEGER), GET("get", JType.INT),
        INCREMENT("incrementAndGet", JType.INT), GET_AND_INCREMENT("getAndIncrement", JType.INT);

        public final String method;
        public final JType type;

        Op(String method, JType type) { this.method = method; this.type = type; }
    }

    public final Op op;
    public final ExprSlot operand;

    public AtomicCounter(Op op, Expr operand) {
        this.op = op;
        this.operand = new ExprSlot(this, op == Op.NEW ? JType.INT : JType.ATOMIC_INTEGER, operand);
    }

    @Override public Value eval(ExecutionContext ctx) throws InterruptedException {
        ctx.checkActive();
        Value value = operand.eval(ctx);
        if (op == Op.NEW) return new Value(JType.ATOMIC_INTEGER, new AtomicInteger(value.asInt()));
        if (value.raw == null) throw new ScriptError("NullPointerException");
        AtomicInteger counter = (AtomicInteger) value.raw;
        if (op == Op.GET) return Value.ofInt(counter.get());
        int result = op == Op.INCREMENT ? counter.incrementAndGet() : counter.getAndIncrement();
        ctx.recordLessonUse(this);
        return Value.ofInt(result);
    }
}
