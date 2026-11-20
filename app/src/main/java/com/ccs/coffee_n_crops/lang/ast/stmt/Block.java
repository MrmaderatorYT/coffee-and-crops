package com.ccs.coffee_n_crops.lang.ast.stmt;

import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.ast.MethodDecl;
import com.ccs.coffee_n_crops.lang.ast.ClassDecl;
import com.ccs.coffee_n_crops.lang.interp.ReturnSignal;
import com.ccs.coffee_n_crops.lang.interp.ScriptError;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** An ordered list of statements: a method body or the body between a loop's braces. */
public final class Block extends Stmt {
    private final List<Stmt> statements = new ArrayList<>();
    private final List<MethodDecl> methods = new ArrayList<>();
    private final List<ClassDecl> classes = new ArrayList<>();
    public List<ClassDecl> classes() { return Collections.unmodifiableList(classes); }
    public void addClass(ClassDecl declaration) { declaration.parent = this; classes.add(declaration); }
    public void removeClass(ClassDecl declaration) { if (classes.remove(declaration)) declaration.parent = null; }

    /** Helpers belong to the program root and execute only when called. */
    public List<MethodDecl> methods() {
        return Collections.unmodifiableList(methods);
    }

    public void addMethod(MethodDecl method) {
        method.parent = this;
        methods.add(method);
    }

    public void removeMethod(MethodDecl method) {
        if (methods.remove(method)) method.parent = null;
    }

    public int size() {
        return statements.size();
    }

    public Stmt get(int index) {
        return statements.get(index);
    }

    public List<Stmt> statements() {
        return Collections.unmodifiableList(statements);
    }

    public void add(Stmt stmt) {
        add(statements.size(), stmt);
    }

    public void add(int index, Stmt stmt) {
        stmt.parent = this;
        statements.add(index, stmt);
    }

    public Stmt remove(int index) {
        Stmt removed = statements.remove(index);
        removed.parent = null;
        return removed;
    }

    public int indexOf(Stmt stmt) {
        return statements.indexOf(stmt);
    }

    @Override
    public void exec(ExecutionContext ctx) throws InterruptedException {
        boolean entry = parent == null;
        if (entry) ctx.beginProgram(this);
        try {
            for (Stmt stmt : statements) stmt.exec(ctx);
        } catch (ReturnSignal returned) {
            if (!entry) throw returned;
            if (returned.value.type != JType.VOID) {
                throw new ScriptError("incompatible types: unexpected return value");
            }
        }
    }
}
