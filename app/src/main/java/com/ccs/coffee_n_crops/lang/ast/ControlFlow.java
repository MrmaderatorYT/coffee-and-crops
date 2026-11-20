package com.ccs.coffee_n_crops.lang.ast;

import com.ccs.coffee_n_crops.lang.ast.expr.Literal;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamLambda;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.BreakStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ContinueStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.DoWhileStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForEachStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.IfStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ReturnStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.SwitchStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.TryStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.WhileStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.SynchronizedStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.LockStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.TeamStmt;

/** Source reachability and the lexical targets of unlabelled jumps. */
public final class ControlFlow {
    private ControlFlow() {
    }

    public static boolean completesNormally(Block block) {
        for (Stmt statement : block.statements()) {
            if (!completesNormally(statement)) return false;
        }
        return true;
    }

    public static boolean completesNormally(Stmt statement) {
        if (statement instanceof ReturnStmt || statement instanceof BreakStmt || statement instanceof ContinueStmt) {
            return false;
        }
        if (statement instanceof TryStmt) {
            TryStmt guarded = (TryStmt) statement;
            if (guarded.finallyBlock() != null && !completesNormally(guarded.finallyBlock())) return false;
            if (completesNormally(guarded.tryBlock)) return true;
            for (TryStmt.Catch arm : guarded.catches()) {
                if (completesNormally(arm.body)) return true;
            }
            return false;
        }
        if (statement instanceof IfStmt) {
            IfStmt condition = (IfStmt) statement;
            if (condition.elseBlock() == null || completesNormally(condition.elseBlock())) return true;
            for (IfStmt.Branch branch : condition.branches()) {
                if (completesNormally(branch.block)) return true;
            }
            return false;
        }
        if (statement instanceof SwitchStmt) {
            SwitchStmt selection = (SwitchStmt) statement;
            if (selection.defaultBlock() == null) return true;
            for (Block arm : selection.childBlocks()) {
                if (completesNormally(arm) || hasExitingJump(arm, selection, false)) return true;
            }
            return false;
        }
        if (statement instanceof WhileStmt) {
            WhileStmt loop = (WhileStmt) statement;
            return !alwaysTrue(loop.cond.get()) || hasExitingJump(loop.body, loop, false);
        }
        if (statement instanceof ForStmt) {
            ForStmt loop = (ForStmt) statement;
            return !alwaysTrue(loop.cond.get()) || hasExitingJump(loop.body, loop, false);
        }
        if (statement instanceof DoWhileStmt) {
            DoWhileStmt loop = (DoWhileStmt) statement;
            return hasExitingJump(loop.body, loop, false)
                    || !alwaysTrue(loop.cond.get())
                    && (completesNormally(loop.body) || hasExitingJump(loop.body, loop, true));
        }
        if (statement instanceof SynchronizedStmt guarded) return completesNormally(guarded.body);
        if (statement instanceof LockStmt guarded) return completesNormally(guarded.body);
        if (statement instanceof Block) return completesNormally((Block) statement);
        return true;
    }

    private static boolean alwaysTrue(Expr expression) {
        return expression instanceof Literal && Boolean.TRUE.equals(((Literal) expression).value);
    }

    /** A jump cannot escape its method, constructor, or lambda body. */
    public static Stmt jumpTarget(Node node, boolean continuing) {
        for (Node parent = node.parent; parent != null; parent = parent.parent) {
            if (parent instanceof MethodDecl || parent instanceof ConstructorDecl || parent instanceof ClassDecl
                    || parent instanceof StreamLambda || parent instanceof TeamStmt) {
                break;
            }
            if (parent instanceof ForStmt || parent instanceof ForEachStmt || parent instanceof WhileStmt
                    || parent instanceof DoWhileStmt || (!continuing && parent instanceof SwitchStmt)) {
                return (Stmt) parent;
            }
        }
        return null;
    }

    private static boolean hasExitingJump(Block block, Stmt target, boolean continuing) {
        for (Stmt statement : block.statements()) {
            boolean matchingJump = continuing ? statement instanceof ContinueStmt : statement instanceof BreakStmt;
            if (matchingJump && jumpTarget(statement, continuing) == target && !blockedByFinally(statement, target)) {
                return true;
            }
            for (Block child : statement.childBlocks()) {
                if (hasExitingJump(child, target, continuing)) return true;
            }
            // Validator checks every child block first. Rechecking each nested loop here would
            // repeat its entire subtree for every enclosing loop.
        }
        return false;
    }

    /** An abruptly completing finally overrides a jump from its try or catch body. */
    private static boolean blockedByFinally(Node jump, Stmt target) {
        Node child = jump;
        for (Node parent = jump.parent; parent != target; parent = parent.parent) {
            if (parent instanceof TryStmt) {
                Block cleanup = ((TryStmt) parent).finallyBlock();
                if (cleanup != null && child != cleanup && !completesNormally(cleanup)) return true;
            }
            child = parent;
        }
        return false;
    }
}
