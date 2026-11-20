package com.ccs.coffee_n_crops.lang.ast.expr;

import com.ccs.coffee_n_crops.lang.ast.Node;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;

/** A function of one stream element, passed to a Java stream operation. */
public abstract class StreamCallback extends Node {
    public final JType resultType;

    protected StreamCallback(JType resultType) {
        this.resultType = resultType;
    }

    /** Binds the callback before traversal, as Java does when evaluating method references. */
    abstract Bound bind(ExecutionContext ctx);

    interface Bound {
        Value apply(ExecutionContext ctx, int element) throws InterruptedException;
    }
}
