package com.ccs.coffee_n_crops.lang.ast.expr;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.interp.ScriptError;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;

/** The Java String.length() method, counting UTF-16 code units. */
public final class StringLength extends Expr {
    public final ExprSlot text;

    public StringLength(Expr text) {
        this.text = new ExprSlot(this, JType.STRING, text);
    }

    @Override
    public Value eval(ExecutionContext ctx) throws InterruptedException {
        String value = text.eval(ctx).asString();
        if (value == null) throw new ScriptError("NullPointerException");
        ctx.recordLessonUse(this);
        return Value.ofInt(value.length());
    }
}
