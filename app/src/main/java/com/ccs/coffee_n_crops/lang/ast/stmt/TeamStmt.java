package com.ccs.coffee_n_crops.lang.ast.stmt;

import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.types.JType;

import java.util.Arrays;
import java.util.List;

/** Two farmhands running separate blocks on one grid and joining at the end. */
public final class TeamStmt extends Stmt {
    public final Block first = new Block();
    public final Block second = new Block();
    public final VarDecl firstFarmer = Ast.declare(JType.FARMHAND, "first");
    public final VarDecl secondFarmer = Ast.declare(JType.FARMHAND, "second");

    public TeamStmt() {
        first.parent = this;
        second.parent = this;
        firstFarmer.parent = this;
        secondFarmer.parent = this;
    }

    @Override public List<Block> childBlocks() {
        return Arrays.asList(first, second);
    }

    @Override public void exec(ExecutionContext ctx) throws InterruptedException {
        ctx.checkpoint(this);
        ctx.workTogether(this);
        ctx.recordLessonUse(this);
    }
}
