package com.ccs.coffee_n_crops.lang.ast.stmt;

import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;

import java.util.Arrays;
import java.util.List;

/** Two background jobs joined by the farmhand before the program continues. */
public final class TogetherStmt extends Stmt {
    public final Block first = new Block();
    public final Block second = new Block();

    public TogetherStmt() {
        first.parent = this;
        second.parent = this;
    }

    @Override public List<Block> childBlocks() {
        return Arrays.asList(first, second);
    }

    @Override public void exec(ExecutionContext ctx) throws InterruptedException {
        ctx.checkpoint(this);
        ctx.runTogether(first, second);
        ctx.recordLessonUse(this);
    }
}
