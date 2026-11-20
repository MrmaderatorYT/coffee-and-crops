package com.ccs.coffee_n_crops.lang.ast.stmt;

import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamCall;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamLambda;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;

import java.util.Collections;
import java.util.List;

/** A forEach call with a multi-line consumer lambda. */
public final class StreamForEachStmt extends Stmt {
    public final StreamCall call;

    public StreamForEachStmt(StreamCall call) {
        if (call.terminal != StreamCall.Terminal.FOR_EACH || !(call.callback instanceof StreamLambda lambda)
                || lambda.body == null) {
            throw new IllegalArgumentException("Expected a forEach block lambda");
        }
        this.call = call;
        call.parent = this;
    }

    @Override public List<Block> childBlocks() {
        return Collections.singletonList(((StreamLambda) call.callback).body);
    }

    @Override public void exec(ExecutionContext ctx) throws InterruptedException {
        ctx.checkpoint(this);
        call.eval(ctx);
    }
}
