package com.ccs.coffee_n_crops.lang.ast;

import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;

import java.util.Collections;
import java.util.List;

/** A statement: something that is executed for its effect. */
public abstract class Stmt extends Node {
    /**
     * Runs on the farmer's own thread. May block (debugger pause, tick delays);
     * {@link InterruptedException} means the run was stopped and must unwind.
     */
    public abstract void exec(ExecutionContext ctx) throws InterruptedException;

    /**
     * The blocks nested directly inside this statement, in source order: a loop's body, the arms
     * of an if or switch. Empty for one-line statements.
     */
    public List<Block> childBlocks() {
        return Collections.emptyList();
    }
}
