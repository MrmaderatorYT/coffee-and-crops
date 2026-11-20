package com.ccs.coffee_n_crops.lang.ast.expr;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;

/**
 * A constant written in the code: {@code 5}, {@code true}, {@code Direction.EAST}.
 * Literals are the editor's tappable "slots", so the value is mutable (edited only while no run is active).
 */
public final class Literal extends Expr {
    public final JType type;
    public volatile Object value;

    public Literal(JType type, Object value) {
        this.type = type;
        this.value = value;
    }

    @Override
    public Value eval(ExecutionContext ctx) {
        return new Value(type, value);
    }
}
