package com.ccs.coffee_n_crops.lang.ast.expr;

import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;

/** Supplies an already assigned functional interface variable to a stream operation. */
public final class FunctionVariable extends StreamCallback {
    public final String name;
    public final FunctionType function;

    public FunctionVariable(String name, FunctionType function) {
        super(function.result);
        this.name = name;
        this.function = function;
    }

    @Override Bound bind(ExecutionContext ctx) {
        return FunctionValue.bound(ctx.readVariable(name), function);
    }
}
