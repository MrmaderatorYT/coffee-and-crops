package com.ccs.coffee_n_crops.lang.ast;

import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.types.Value;

/** An expression: something that is evaluated to a {@link Value}. */
public abstract class Expr extends Node {
    public abstract Value eval(ExecutionContext ctx) throws InterruptedException;
}
