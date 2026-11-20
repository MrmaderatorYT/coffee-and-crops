package com.ccs.coffee_n_crops.lang.ast.expr;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;

import java.util.concurrent.locks.ReentrantLock;

/** Allocates a fresh Java lock to be shared by the farmhands. */
public final class NewReentrantLock extends Expr {
    @Override public Value eval(ExecutionContext ctx) {
        return new Value(JType.REENTRANT_LOCK, new ReentrantLock());
    }
}
