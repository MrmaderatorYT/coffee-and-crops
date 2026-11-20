package com.ccs.coffee_n_crops.lang.ast.stmt;

import com.ccs.coffee_n_crops.farm.error.FarmException;
import com.ccs.coffee_n_crops.lang.ast.Node;
import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.ast.expr.Literal;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.runtime.ExceptionTypes;
import com.ccs.coffee_n_crops.lang.types.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A Java try with ordered farm exception handlers and an optional finally block. */
public final class TryStmt extends Stmt {
    /** A catch parameter and its independently scoped body. */
    public static final class Catch extends Node {
        public final String typeName;
        public final String name;
        /** Typed declaration metadata; its null initializer is never executed. */
        public final VarDecl variable;
        public final Block body = new Block();

        private Catch(TryStmt owner, String typeName, String name) {
            parent = owner;
            this.typeName = typeName;
            this.name = name;
            variable = new VarDecl(ExceptionTypes.typeOf(typeName), name,
                    new Literal(ExceptionTypes.typeOf(typeName), null));
            variable.parent = this;
            body.parent = this;
        }
    }

    public final Block tryBlock = new Block();
    private final List<Catch> catches = new ArrayList<>();
    private Block finallyBlock;

    public TryStmt() {
        tryBlock.parent = this;
    }

    public List<Catch> catches() {
        return Collections.unmodifiableList(catches);
    }

    public Catch addCatch(String typeName, String variableName) {
        Catch arm = new Catch(this, typeName, variableName);
        catches.add(arm);
        return arm;
    }

    public void removeCatch(Catch arm) {
        if (catches.remove(arm)) arm.parent = null;
    }

    public Block finallyBlock() {
        return finallyBlock;
    }

    public Block addFinally() {
        if (finallyBlock == null) {
            finallyBlock = new Block();
            finallyBlock.parent = this;
        }
        return finallyBlock;
    }

    public void removeFinally() {
        if (finallyBlock != null) finallyBlock.parent = null;
        finallyBlock = null;
    }

    @Override
    public List<Block> childBlocks() {
        List<Block> blocks = new ArrayList<>();
        blocks.add(tryBlock);
        for (Catch arm : catches) blocks.add(arm.body);
        if (finallyBlock != null) blocks.add(finallyBlock);
        return blocks;
    }

    @Override
    public void exec(ExecutionContext ctx) throws InterruptedException {
        ctx.checkpoint(this);
        ctx.clock.awaitTicks(VarDecl.THINK_TICKS);
        boolean interrupted = false;
        try {
            try {
                runScoped(tryBlock, ctx);
            } catch (FarmException failure) {
                Catch handler = null;
                for (Catch arm : catches) {
                    if (ExceptionTypes.matches(arm.typeName, failure)) {
                        handler = arm;
                        break;
                    }
                }
                if (handler == null) throw failure;
                ctx.checkpoint(handler);
                ctx.clock.awaitTicks(VarDecl.THINK_TICKS);
                synchronized (ctx.world) {
                    ctx.checkActive();
                    ctx.world.recordCaughtException(handler.typeName);
                }
                ctx.pushScope();
                try {
                    ctx.scope().declare(handler.name, new Value(handler.variable.type, failure));
                    handler.body.exec(ctx);
                } finally {
                    ctx.popScope();
                }
            }
        } catch (InterruptedException canceled) {
            interrupted = true;
            throw canceled;
        } finally {
            // Reset cancels the whole program, including cleanup; it is not a Java farm exception.
            if (finallyBlock != null && !interrupted) {
                ctx.checkActive();
                Node pendingNode = ctx.currentNode();
                boolean completed = false;
                try {
                    ctx.checkpoint(finallyBlock);
                    ctx.clock.awaitTicks(VarDecl.THINK_TICKS);
                    synchronized (ctx.world) {
                        ctx.checkActive();
                        ctx.world.recordFinally();
                    }
                    runScoped(finallyBlock, ctx);
                    completed = true;
                } finally {
                    // Successful cleanup must not hide the original source of an uncaught failure.
                    if (completed) ctx.restoreCurrentNode(pendingNode);
                }
            }
        }
    }

    private static void runScoped(Block block, ExecutionContext ctx) throws InterruptedException {
        ctx.pushScope();
        try {
            block.exec(ctx);
        } finally {
            ctx.popScope();
        }
    }
}
