package com.ccs.coffee_n_crops.lang.ast.expr;

import com.ccs.coffee_n_crops.lang.types.JType;

/** The supported one-argument Java functional interfaces and their invocation methods. */
public enum FunctionType {
    TRANSFORM(JType.INTEGER_FUNCTION, JType.INT, "apply"),
    PREDICATE(JType.INTEGER_PREDICATE, JType.BOOLEAN, "test"),
    CONSUMER(JType.INTEGER_CONSUMER, JType.VOID, "accept"),
    ARRAY_FACTORY(JType.INT_ARRAY_FACTORY, JType.INT_ARRAY, "apply");

    public final JType type;
    public final JType result;
    public final String invocation;

    FunctionType(JType type, JType result, String invocation) {
        this.type = type;
        this.result = result;
        this.invocation = invocation;
    }

    public static FunctionType of(JType type) {
        for (FunctionType function : values()) if (function.type == type) return function;
        return null;
    }
}
