package com.ccs.coffee_n_crops.editor;

import com.ccs.coffee_n_crops.lang.ast.ControlFlow;
import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.SuperCall;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.check.ScopeResolver;
import com.ccs.coffee_n_crops.lang.check.Validator;
import com.ccs.coffee_n_crops.lang.serial.AstJson;

import org.json.JSONException;

import java.util.Set;

/** Edits siblings within one block, retaining their Java scope. */
final class StatementEdits {
    private StatementEdits() { }

    /** Returns the compiler message when a move would invalidate the surrounding code. */
    static String move(Block program, Block block, int from, int to) {
        Stmt statement = block.get(from);
        Stmt neighbor = block.get(to);
        Validator.Problem before = Validator.check(program);
        if (before != null && (statement instanceof VarDecl || neighbor instanceof VarDecl
                || statement instanceof SuperCall || neighbor instanceof SuperCall
                || !ControlFlow.completesNormally(statement) || !ControlFlow.completesNormally(neighbor))) {
            return before.message;
        }

        block.add(to, block.remove(from));
        Validator.Problem after = Validator.check(program);
        if (unchangedProblem(before, after)) return null;
        block.add(from, block.remove(to));
        return after.message;
    }

    /** Copies a statement with fresh node IDs and, for a declaration, a fresh variable name. */
    static String copy(Block program, Block block, int at) throws JSONException {
        Stmt original = block.get(at);
        Validator.Problem before = Validator.check(program);
        if (before != null && (original instanceof SuperCall || !ControlFlow.completesNormally(original))) {
            return before.message;
        }

        Stmt duplicate = AstJson.copyStatement(original);
        if (duplicate instanceof VarDecl declaration) {
            Set<String> names = ScopeResolver.allNames(program);
            String name;
            int suffix = 2;
            do {
                name = declaration.name + suffix++;
            } while (names.contains(name));
            duplicate = new VarDecl(declaration.type, name, declaration.init.get(),
                    declaration.isFinal, declaration.isStatic);
        }
        block.add(at + 1, duplicate);
        Validator.Problem after = Validator.check(program);
        if (unchangedProblem(before, after)) return null;
        block.remove(at + 1);
        return after.message;
    }

    private static boolean unchangedProblem(Validator.Problem before, Validator.Problem after) {
        return after == null || before != null && before.node == after.node && before.message.equals(after.message);
    }
}
