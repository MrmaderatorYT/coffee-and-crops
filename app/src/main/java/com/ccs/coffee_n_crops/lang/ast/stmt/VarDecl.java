package com.ccs.coffee_n_crops.lang.ast.stmt;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.types.JType;

/** A local variable declaration with initializer, e.g. {@code int steps = 5;}. */
public final class VarDecl extends Stmt {
    /** How long a pure-computation statement stays highlighted, in ticks. */
    public static final double THINK_TICKS = 0.4;

    public final JType type;
    public final String name;
    public final ExprSlot init;
    /** Instance fields can use JVM volatile storage for publication between workers. */
    public boolean volatileField;
    public final boolean isFinal;
    public final boolean isStatic;

    public VarDecl(JType type, String name, Expr init) {
        this(type, name, init, false, false);
    }

    public VarDecl(JType type, String name, Expr init, boolean isFinal, boolean isStatic) {
        this.type = type;
        this.name = name;
        this.isFinal = isFinal;
        this.isStatic = isStatic;
        this.init = isStatic ? ExprSlot.constant(this, type, init) : new ExprSlot(this, type, init);
    }

    @Override
    public void exec(ExecutionContext ctx) throws InterruptedException {
        ctx.checkpoint(this);
        ctx.clock.awaitTicks(THINK_TICKS);
        declare(ctx);
    }

    /** Declares the variable without a debugger checkpoint; used by the {@code for} header. */
    public void declare(ExecutionContext ctx) throws InterruptedException {
        ctx.scope().declare(name, ctx.convertValue(init.eval(ctx), type));
        if (isFinal) ctx.recordLessonUse(this);
    }
}
