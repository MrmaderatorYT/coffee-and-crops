package com.ccs.coffee_n_crops.lang.ast;

import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;

/**
 * A typed hole in a node that holds one expression: a loop bound, an {@code if} condition,
 * a method argument. The editor fills and refills slots; the type says which expressions fit,
 * which is how the palette only ever offers code that compiles.
 */
public final class ExprSlot {
    /** The node this slot belongs to; becomes the parent of whatever expression is placed here. */
    public final Node owner;
    public final JType type;
    /** True where Java demands a compile-time constant, such as a case label: only literals fit. */
    public final boolean constantOnly;
    private volatile Expr expr;

    public ExprSlot(Node owner, JType type, Expr initial) {
        this(owner, type, initial, false);
    }

    private ExprSlot(Node owner, JType type, Expr initial, boolean constantOnly) {
        this.owner = owner;
        this.type = type;
        this.constantOnly = constantOnly;
        set(initial);
    }

    /** A slot that accepts only literals. */
    public static ExprSlot constant(Node owner, JType type, Expr initial) {
        return new ExprSlot(owner, type, initial, true);
    }

    public Expr get() {
        return expr;
    }

    public void set(Expr expr) {
        expr.parent = owner;
        this.expr = expr;
    }

    public Value eval(ExecutionContext ctx) throws InterruptedException {
        return expr.eval(ctx);
    }
}
