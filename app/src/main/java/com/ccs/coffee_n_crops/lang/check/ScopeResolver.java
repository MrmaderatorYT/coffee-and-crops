package com.ccs.coffee_n_crops.lang.check;

import com.ccs.coffee_n_crops.lang.ast.AstWalk;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.ast.Node;
import com.ccs.coffee_n_crops.lang.ast.MethodDecl;
import com.ccs.coffee_n_crops.lang.ast.ClassDecl;
import com.ccs.coffee_n_crops.lang.ast.ConstructorDecl;
import com.ccs.coffee_n_crops.lang.ast.expr.Literal;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamLambda;
import com.ccs.coffee_n_crops.lang.runtime.ClassTable;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForEachStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.ast.stmt.TryStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.TeamStmt;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Answers "which variables can be used here?" by Java's scoping rules: a variable is visible
 * after its declaration, inside the block that declares it and any block nested in that one.
 */
public final class ScopeResolver {
    private ScopeResolver() {
    }

    /** Variables visible to a statement inserted at {@code block[index]}. */
    public static List<VarDecl> visibleAt(Block block, int index) {
        List<VarDecl> out = new ArrayList<>();
        declaredBefore(block, index, out);
        climb(block, block.parent, out);
        return out;
    }

    /** Variables visible to an expression placed in the slot. */
    public static List<VarDecl> visibleAt(ExprSlot slot) {
        List<VarDecl> out = new ArrayList<>();
        climb(slot.get(), slot.owner, out);
        return out;
    }

    /** Every variable name declared anywhere in the program, loop variables included. */
    public static Set<String> allNames(Block program) {
        Set<String> names = new HashSet<>();
        AstWalk.walk(program, node -> {
            if (node instanceof VarDecl) names.add(((VarDecl) node).name);
        });
        return names;
    }

    /** Walks outward from {@code child} through its ancestors, collecting what each one brings into scope. */
    private static void climb(Node child, Node ancestor, List<VarDecl> out) {
        while (ancestor != null) {
            if (ancestor instanceof TryStmt.Catch) out.add(((TryStmt.Catch) ancestor).variable);
            if (ancestor instanceof MethodDecl) {
                MethodDecl method = (MethodDecl) ancestor;
                out.addAll(method.parameters);
                if (method.isInstance()) addFields((ClassDecl) method.parent, out);
                return;
            }
            if (ancestor instanceof ConstructorDecl) {
                out.addAll(((ConstructorDecl) ancestor).parameters);
                if (ancestor.parent instanceof ClassDecl) addFields((ClassDecl) ancestor.parent, out);
                return;
            }
            if (ancestor instanceof ClassDecl) {
                addFields((ClassDecl) ancestor, out);
                return;
            }
            if (ancestor instanceof ForStmt) {
                // The loop variable is visible in the condition, update and body, not in its own initializer.
                VarDecl loopVar = ((ForStmt) ancestor).init;
                if (child != loopVar) out.add(loopVar);
            }
            if (ancestor instanceof ForEachStmt) {
                out.add(((ForEachStmt) ancestor).variable);
            }
            if (ancestor instanceof StreamLambda lambda && child != lambda.parameter) {
                out.add(lambda.parameter);
            }
            if (ancestor instanceof TeamStmt team) {
                if (child == team.first) out.add(team.firstFarmer);
                if (child == team.second) out.add(team.secondFarmer);
            }
            if (ancestor instanceof Block && child instanceof Stmt) {
                Block block = (Block) ancestor;
                declaredBefore(block, block.indexOf((Stmt) child), out);
            }
            child = ancestor;
            ancestor = ancestor.parent;
        }
    }

    private static void addFields(ClassDecl declaration, List<VarDecl> out) {
        Block program = ClassTable.programOf(declaration);
        if (program == null) return;
        Set<String> visible = new HashSet<>();
        for (VarDecl variable : out) visible.add(variable.name);
        for (VarDecl field : new ClassTable(program).fields(declaration.type())) {
            if (visible.add(field.name)) out.add(field);
        }
    }

    private static void declaredBefore(Block block, int index, List<VarDecl> out) {
        for (int i = 0; i < index; i++) {
            if (block.get(i) instanceof VarDecl) out.add((VarDecl) block.get(i));
        }
    }
}
