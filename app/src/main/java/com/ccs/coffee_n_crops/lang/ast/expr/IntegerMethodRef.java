package com.ccs.coffee_n_crops.lang.ast.expr;

import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;

/** Static, unbound instance, and array constructor references with one integer input. */
public final class IntegerMethodRef extends StreamCallback {
    public enum Kind {
        ABS("Math", "abs", JType.INT),
        INT_VALUE("Integer", "intValue", JType.INT),
        ARRAY("int[]", "new", JType.INT_ARRAY);

        public final String owner;
        public final String method;
        private final JType result;

        Kind(String owner, String method, JType result) {
            this.owner = owner;
            this.method = method;
            this.result = result;
        }
    }

    public final Kind kind;

    public IntegerMethodRef(Kind kind) {
        super(kind.result);
        this.kind = kind;
    }

    @Override Bound bind(ExecutionContext ctx) {
        return (call, input) -> {
            Value value = switch (kind) {
                case ABS -> Value.ofInt(Math.abs(input));
                case INT_VALUE -> Value.ofInt(input);
                case ARRAY -> new Value(JType.INT_ARRAY, NewIntArray.allocate(input));
            };
            call.recordLessonUse(this);
            return value;
        };
    }
}
