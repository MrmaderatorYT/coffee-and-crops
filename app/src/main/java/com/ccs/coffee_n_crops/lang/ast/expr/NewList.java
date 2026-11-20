package com.ccs.coffee_n_crops.lang.ast.expr;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;

import java.util.ArrayList;

/** {@code new ArrayList<>()}: a fresh, empty list of Integer. */
public final class NewList extends Expr {
    @Override
    public Value eval(ExecutionContext ctx) {
        return new Value(JType.INT_LIST, new ArrayList<Integer>());
    }
}
