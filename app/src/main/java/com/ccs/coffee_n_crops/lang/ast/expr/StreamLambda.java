package com.ccs.coffee_n_crops.lang.ast.expr;

import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.interp.ReturnSignal;
import com.ccs.coffee_n_crops.lang.interp.ScriptError;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;

/** A lambda over a single integer element, with an expression or a consumer body. */
public final class StreamLambda extends StreamCallback {
    public final VarDecl parameter;
    public final ExprSlot expression;
    public final Block body;

    public StreamLambda(String parameter, JType resultType, Expr expression) {
        super(resultType);
        this.parameter = Ast.declare(JType.INT, parameter);
        this.parameter.parent = this;
        this.expression = new ExprSlot(this, resultType, expression);
        this.body = null;
    }

    public StreamLambda(String parameter) {
        super(JType.VOID);
        this.parameter = Ast.declare(JType.INT, parameter);
        this.parameter.parent = this;
        this.expression = null;
        this.body = new Block();
        this.body.parent = this;
    }

    @Override Bound bind(ExecutionContext ctx) {
        return element -> {
            ctx.pushScope();
            try {
                ctx.scope().declare(parameter.name, Value.ofInt(element));
                Value result = expression == null ? runBody(ctx) : expression.eval(ctx);
                ctx.recordLessonUse(this);
                return result;
            } finally {
                ctx.popScope();
            }
        };
    }

    private Value runBody(ExecutionContext ctx) throws InterruptedException {
        try {
            body.exec(ctx);
        } catch (ReturnSignal returned) {
            if (returned.value.type != JType.VOID) throw new ScriptError("unexpected return value");
        }
        return Value.VOID;
    }
}
