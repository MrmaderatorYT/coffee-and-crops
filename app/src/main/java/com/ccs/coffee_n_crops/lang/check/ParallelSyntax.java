package com.ccs.coffee_n_crops.lang.check;

import com.ccs.coffee_n_crops.lang.ast.AstWalk;
import com.ccs.coffee_n_crops.lang.ast.Node;
import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.ast.expr.AtomicCounter;
import com.ccs.coffee_n_crops.lang.ast.expr.Binary;
import com.ccs.coffee_n_crops.lang.ast.expr.FieldAccess;
import com.ccs.coffee_n_crops.lang.ast.expr.Literal;
import com.ccs.coffee_n_crops.lang.ast.expr.Not;
import com.ccs.coffee_n_crops.lang.ast.expr.PostIncrement;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.Assign;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.CompoundAssign;
import com.ccs.coffee_n_crops.lang.ast.stmt.DecrementStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ExprStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.FieldAssign;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.IfStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.SynchronizedStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.TogetherStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.ast.stmt.WhileStmt;

import java.util.HashSet;
import java.util.Set;

/** Checks capture rules and limits background jobs to shared state and calculations. */
final class ParallelSyntax {
    private ParallelSyntax() { }

    static Validator.Problem check(TogetherStmt together) {
        if (!(together.parent instanceof Block run) || run.parent != null) {
            return new Validator.Problem(together, "Place runTogether directly inside run().");
        }
        Set<String> outer = new HashSet<>();
        for (int i = 0; i < run.indexOf(together); i++) {
            if (run.get(i) instanceof VarDecl variable) outer.add(variable.name);
        }
        Set<String> captures = new HashSet<>();
        Validator.Problem[] failure = new Validator.Problem[1];
        AstWalk.walk(together, node -> {
            if (node == together || failure[0] != null) return;
            if (!allowed(node)) {
                failure[0] = new Validator.Problem(node, "Use only calculations, loops and shared fields in background jobs.");
            } else if (node instanceof SynchronizedStmt && insideMonitor(node)) {
                failure[0] = new Validator.Problem(node, "Use one synchronized block at a time in a background job.");
            } else if (node instanceof VarRef ref && outer.contains(ref.name)) {
                captures.add(ref.name);
            }
        });
        if (failure[0] != null) return failure[0];
        for (Stmt statement : run.statements()) {
            AstWalk.walk(statement, node -> {
                if (failure[0] == null && captures.contains(assignedName(node))) {
                    failure[0] = new Validator.Problem(together,
                            "local variables referenced from a lambda expression must be final or effectively final");
                }
            });
        }
        return failure[0];
    }

    private static boolean allowed(Node node) {
        if (node instanceof PostIncrement increment) {
            return increment.parent instanceof ForStmt loop && loop.update == increment
                    && loop.init.name.equals(increment.name);
        }
        if (node instanceof AtomicCounter counter) return counter.op != AtomicCounter.Op.NEW;
        return node instanceof Block || node instanceof SynchronizedStmt || node instanceof WhileStmt
                || node instanceof ForStmt || node instanceof IfStmt || node instanceof FieldAssign
                || node instanceof ExprStmt || node instanceof VarDecl || node instanceof VarRef
                || node instanceof FieldAccess || node instanceof Literal || node instanceof Binary || node instanceof Not;
    }

    private static boolean insideMonitor(Node node) {
        for (Node at = node.parent; at != null; at = at.parent) {
            if (at instanceof SynchronizedStmt) return true;
        }
        return false;
    }

    private static String assignedName(Node node) {
        if (node instanceof Assign a) return a.name;
        if (node instanceof CompoundAssign a) return a.name;
        if (node instanceof DecrementStmt a) return a.name;
        return node instanceof PostIncrement a ? a.name : null;
    }
}
