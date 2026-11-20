package com.ccs.coffee_n_crops.lang.ast.expr;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.interp.ScriptError;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** The supported calls on a HashMap<String, Integer> variable. */
public final class StringIntMapCall extends Expr {
    private static final int MAX_ENTRIES = 8_192;

    public enum Op {
        PUT("put", JType.VOID, JType.STRING, JType.INT),
        GET_OR_DEFAULT("getOrDefault", JType.INT, JType.STRING, JType.INT),
        CONTAINS_KEY("containsKey", JType.BOOLEAN, JType.STRING),
        SIZE("size", JType.INT);

        public final String method;
        public final JType resultType;
        private final JType[] parameterTypes;

        Op(String method, JType resultType, JType... parameterTypes) {
            this.method = method;
            this.resultType = resultType;
            this.parameterTypes = parameterTypes;
        }
    }

    public final String name;
    public final Op op;
    public final List<ExprSlot> arguments;

    public StringIntMapCall(String name, Op op, Expr... arguments) {
        if (arguments.length != op.parameterTypes.length) {
            throw new IllegalArgumentException("Wrong argument count for " + op.method);
        }
        this.name = name;
        this.op = op;
        List<ExprSlot> slots = new ArrayList<>();
        for (int i = 0; i < arguments.length; i++) {
            slots.add(new ExprSlot(this, op.parameterTypes[i], arguments[i]));
        }
        this.arguments = Collections.unmodifiableList(slots);
    }

    @Override
    public Value eval(ExecutionContext ctx) throws InterruptedException {
        Value receiver = ctx.readVariable(name);
        String key = arguments.isEmpty() ? null : arguments.get(0).eval(ctx).asString();
        int number = arguments.size() == 2 ? arguments.get(1).eval(ctx).asInt() : 0;
        Map<String, Integer> map = receiver.asStringIntMap();
        Value result = switch (op) {
            case GET_OR_DEFAULT -> Value.ofInt(map.getOrDefault(key, number));
            case CONTAINS_KEY -> Value.ofBoolean(map.containsKey(key));
            case SIZE -> Value.ofInt(map.size());
            case PUT -> {
                if (map.size() >= MAX_ENTRIES && !map.containsKey(key)) {
                    throw new ScriptError("OutOfMemoryError: Requested map size exceeds VM limit");
                }
                map.put(key, number);
                yield Value.VOID;
            }
        };
        ctx.recordLessonUse(this);
        return result;
    }
}
