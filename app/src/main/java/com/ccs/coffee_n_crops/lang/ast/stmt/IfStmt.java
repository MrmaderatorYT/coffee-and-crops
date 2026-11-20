package com.ccs.coffee_n_crops.lang.ast.stmt;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.ast.Node;
import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.types.JType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** {@code if (c) { } else if (c2) { } else { }}: one or more conditional branches and an optional else. */
public final class IfStmt extends Stmt {

    /** One {@code if (...) { }} or {@code else if (...) { }} arm. */
    public static final class Branch extends Node {
        public final ExprSlot cond;
        public final Block block = new Block();

        Branch(IfStmt owner, Expr cond) {
            this.parent = owner;
            this.cond = new ExprSlot(this, JType.BOOLEAN, cond);
            this.block.parent = this;
        }
    }

    private final List<Branch> branches = new ArrayList<>();
    private Block elseBlock;

    /** The {@code if} arm itself: always present, always first. */
    public final ExprSlot cond;
    public final Block thenBlock;

    public IfStmt(Expr cond, boolean withElse) {
        Branch first = new Branch(this, cond);
        branches.add(first);
        this.cond = first.cond;
        this.thenBlock = first.block;
        if (withElse) addElse();
    }

    /** The {@code if} arm followed by any {@code else if} arms, in source order. */
    public List<Branch> branches() {
        return Collections.unmodifiableList(branches);
    }

    public Branch addElseIf(Expr cond) {
        Branch branch = new Branch(this, cond);
        branches.add(branch);
        return branch;
    }

    /** Removes an {@code else if} arm. The leading {@code if} arm cannot be removed. */
    public void removeElseIf(Branch branch) {
        if (branch == branches.get(0)) throw new IllegalArgumentException("cannot remove the if arm");
        branches.remove(branch);
    }

    /** @return the else block, or null when the statement has none */
    public Block elseBlock() {
        return elseBlock;
    }

    public Block addElse() {
        if (elseBlock == null) {
            elseBlock = new Block();
            elseBlock.parent = this;
        }
        return elseBlock;
    }

    public void removeElse() {
        elseBlock = null;
    }

    @Override
    public List<Block> childBlocks() {
        List<Block> out = new ArrayList<>();
        for (Branch b : branches) out.add(b.block);
        if (elseBlock != null) out.add(elseBlock);
        return out;
    }

    @Override
    public void exec(ExecutionContext ctx) throws InterruptedException {
        for (int i = 0; i < branches.size(); i++) {
            Branch branch = branches.get(i);
            // Each condition that is actually tested gets its own debugger stop, so stepping
            // shows the chain being walked top to bottom until one arm matches.
            ctx.checkpoint(i == 0 ? this : branch);
            ctx.clock.awaitTicks(VarDecl.THINK_TICKS);
            if (branch.cond.eval(ctx).asBoolean()) {
                run(branch.block, ctx);
                return;
            }
        }
        if (elseBlock != null) run(elseBlock, ctx);
    }

    private static void run(Block block, ExecutionContext ctx) throws InterruptedException {
        ctx.pushScope();
        try {
            block.exec(ctx);
        } finally {
            ctx.popScope();
        }
    }
}
