package com.ccs.coffee_n_crops.lang.ast;

import com.ccs.coffee_n_crops.lang.ast.expr.AtomicCounter;
import com.ccs.coffee_n_crops.lang.ast.stmt.SynchronizedStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.LockStmt;

import com.ccs.coffee_n_crops.lang.ast.expr.Access;
import com.ccs.coffee_n_crops.lang.ast.expr.ListContains;
import com.ccs.coffee_n_crops.lang.ast.expr.CollectionCall;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamCall;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamLambda;
import com.ccs.coffee_n_crops.lang.ast.expr.FunctionValue;
import com.ccs.coffee_n_crops.lang.ast.expr.FunctionCall;
import com.ccs.coffee_n_crops.lang.ast.expr.NewIntArray;
import com.ccs.coffee_n_crops.lang.ast.expr.NewIntGrid;
import com.ccs.coffee_n_crops.lang.ast.expr.IntArrayLiteral;
import com.ccs.coffee_n_crops.lang.ast.expr.NewObjectArray;
import com.ccs.coffee_n_crops.lang.ast.expr.ObjectArrayGet;
import com.ccs.coffee_n_crops.lang.ast.stmt.ArrayAssign;
import com.ccs.coffee_n_crops.lang.ast.stmt.ObjectArrayAssign;
import com.ccs.coffee_n_crops.lang.ast.expr.Binary;
import com.ccs.coffee_n_crops.lang.ast.expr.MethodCall;
import com.ccs.coffee_n_crops.lang.ast.expr.Not;
import com.ccs.coffee_n_crops.lang.ast.expr.StrEquals;
import com.ccs.coffee_n_crops.lang.ast.expr.CropEquals;
import com.ccs.coffee_n_crops.lang.ast.expr.StringConcat;
import com.ccs.coffee_n_crops.lang.ast.expr.StringLength;
import com.ccs.coffee_n_crops.lang.ast.expr.StringIntMapCall;
import com.ccs.coffee_n_crops.lang.ast.expr.UserCall;
import com.ccs.coffee_n_crops.lang.ast.expr.NewObject;
import com.ccs.coffee_n_crops.lang.ast.expr.FieldAccess;
import com.ccs.coffee_n_crops.lang.ast.expr.InstanceCall;
import com.ccs.coffee_n_crops.lang.ast.expr.InstanceOf;
import com.ccs.coffee_n_crops.lang.ast.stmt.FieldAssign;
import com.ccs.coffee_n_crops.lang.ast.stmt.SuperCall;
import com.ccs.coffee_n_crops.lang.ast.stmt.Assign;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.ExprStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForEachStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.IfStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.AssertStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ListAdd;
import com.ccs.coffee_n_crops.lang.ast.stmt.ListRemove;
import com.ccs.coffee_n_crops.lang.ast.stmt.ListSet;
import com.ccs.coffee_n_crops.lang.ast.stmt.ReturnStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.SwitchStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.StreamForEachStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.TeamStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.TryStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.ast.stmt.WhileStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.CompoundAssign;
import com.ccs.coffee_n_crops.lang.ast.stmt.DoWhileStmt;

/** Visits every statement and expression of a program, parents before children. */
public final class AstWalk {

    public interface Visitor {
        void visit(Node node);
    }

    private AstWalk() {
    }

    /** Visits any subtree, useful when a lesson inspects a method body or call argument only. */
    public static void walk(Node node, Visitor v) {
        if (node instanceof Block) {
            walk((Block) node, v);
        } else if (node instanceof Stmt) {
            stmt((Stmt) node, v);
        } else if (node instanceof Expr) {
            expr((Expr) node, v);
        } else if (node instanceof StreamLambda lambda) {
            v.visit(lambda);
            v.visit(lambda.parameter);
            if (lambda.body != null) walk(lambda.body, v);
            else expr(lambda.expression.get(), v);
        } else if (node instanceof MethodDecl) {
            MethodDecl method = (MethodDecl) node;
            v.visit(method);
            for (VarDecl parameter : method.parameters) v.visit(parameter);
            walk(method.body, v);
        } else if (node instanceof TryStmt.Catch) {
            TryStmt.Catch arm = (TryStmt.Catch) node;
            v.visit(arm);
            v.visit(arm.variable);
            walk(arm.body, v);
        } else if (node instanceof ClassDecl) {
            ClassDecl declaration = (ClassDecl) node;
            v.visit(declaration);
            for (VarDecl field : declaration.fields()) stmt(field, v);
            if (declaration.constructor() != null) walk(declaration.constructor(), v);
            for (MethodDecl method : declaration.methods()) walk(method, v);
        } else if (node instanceof ConstructorDecl) {
            ConstructorDecl declaration = (ConstructorDecl) node;
            v.visit(declaration);
            for (VarDecl parameter : declaration.parameters) v.visit(parameter);
            walk(declaration.body, v);
        } else {
            v.visit(node);
        }
    }

    public static void walk(Block block, Visitor v) {
        v.visit(block);
        for (Stmt s : block.statements()) stmt(s, v);
        for (MethodDecl method : block.methods()) {
            v.visit(method);
            for (VarDecl parameter : method.parameters) v.visit(parameter);
            walk(method.body, v);
        }
        for (ClassDecl declaration : block.classes()) walk(declaration, v);
    }

    private static void stmt(Stmt s, Visitor v) {
        v.visit(s);
        if (s instanceof StreamForEachStmt each) {
            // Its child block belongs to the lambda and is visited through the call.
            expr(each.call, v);
            return;
        }
        if (s instanceof TryStmt) {
            TryStmt guarded = (TryStmt) s;
            walk(guarded.tryBlock, v);
            for (TryStmt.Catch arm : guarded.catches()) walk(arm, v);
            if (guarded.finallyBlock() != null) walk(guarded.finallyBlock(), v);
            return;
        }
        if (s instanceof TeamStmt team) {
            v.visit(team.firstFarmer);
            v.visit(team.secondFarmer);
        } else if (s instanceof SynchronizedStmt guarded) {
            expr(guarded.monitor.get(), v);
        } else if (s instanceof LockStmt guarded) {
            expr(guarded.lock.get(), v);
        } else if (s instanceof FieldAssign) {
            FieldAssign assignment = (FieldAssign) s;
            expr(assignment.target, v);
            expr(assignment.value.get(), v);
        } else if (s instanceof SuperCall) {
            for (ExprSlot argument : ((SuperCall) s).args) expr(argument.get(), v);
        } else if (s instanceof ForStmt) {
            ForStmt f = (ForStmt) s;
            stmt(f.init, v);
            expr(f.cond.get(), v);
            expr(f.update, v);
        } else if (s instanceof IfStmt) {
            for (IfStmt.Branch b : ((IfStmt) s).branches()) expr(b.cond.get(), v);
        } else if (s instanceof AssertStmt assertion) {
            expr(assertion.condition.get(), v);
            if (assertion.detail != null) expr(assertion.detail.get(), v);
        } else if (s instanceof WhileStmt) {
            expr(((WhileStmt) s).cond.get(), v);
        } else if (s instanceof DoWhileStmt) {
            DoWhileStmt loop = (DoWhileStmt) s;
            walk(loop.body, v);
            v.visit(loop.conditionLine);
            expr(loop.cond.get(), v);
            return;
        } else if (s instanceof ForEachStmt) {
            v.visit(((ForEachStmt) s).variable);
        } else if (s instanceof SwitchStmt selection) {
            expr(selection.subject.get(), v);
            for (SwitchStmt.Case arm : selection.cases()) {
                v.visit(arm);
                expr(arm.label.get(), v);
                walk(arm.body, v);
            }
            if (selection.defaultBlock() != null) walk(selection.defaultBlock(), v);
            return;
        } else if (s instanceof VarDecl) {
            expr(((VarDecl) s).init.get(), v);
        } else if (s instanceof Assign) {
            expr(((Assign) s).value.get(), v);
        } else if (s instanceof CompoundAssign) {
            expr(((CompoundAssign) s).value.get(), v);
        } else if (s instanceof ListAdd) {
            expr(((ListAdd) s).value.get(), v);
        } else if (s instanceof ArrayAssign) {
            ArrayAssign assignment = (ArrayAssign) s;
            expr(assignment.index.get(), v);
            expr(assignment.value.get(), v);
            if (assignment.column != null) expr(assignment.column.get(), v);
        } else if (s instanceof ObjectArrayAssign) {
            ObjectArrayAssign assignment = (ObjectArrayAssign) s;
            expr(assignment.index.get(), v);
            expr(assignment.value.get(), v);
        } else if (s instanceof ListSet) {
            ListSet set = (ListSet) s;
            expr(set.index.get(), v);
            expr(set.value.get(), v);
        } else if (s instanceof ListRemove) {
            expr(((ListRemove) s).index.get(), v);
        } else if (s instanceof ReturnStmt) {
            ExprSlot value = ((ReturnStmt) s).value;
            if (value != null) expr(value.get(), v);
        } else if (s instanceof ExprStmt) {
            expr(((ExprStmt) s).expr, v);
        }
        for (Block child : s.childBlocks()) walk(child, v);
    }

    private static void expr(Expr e, Visitor v) {
        v.visit(e);
        if (e instanceof AtomicCounter counter) expr(counter.operand.get(), v);
        if (e instanceof InstanceOf check) {
            expr(check.operand.get(), v);
        } else if (e instanceof FieldAccess) {
            expr(((FieldAccess) e).receiver.get(), v);
        } else if (e instanceof InstanceCall) {
            InstanceCall call = (InstanceCall) e;
            expr(call.receiver.get(), v);
            for (ExprSlot argument : call.args) expr(argument.get(), v);
        } else if (e instanceof NewObject) {
            for (ExprSlot argument : ((NewObject) e).args) expr(argument.get(), v);
        } else if (e instanceof Binary) {
            expr(((Binary) e).left.get(), v);
            expr(((Binary) e).right.get(), v);
        } else if (e instanceof StringConcat concat) {
            expr(concat.left.get(), v);
            expr(concat.right.get(), v);
        } else if (e instanceof StringIntMapCall call) {
            for (ExprSlot argument : call.arguments) expr(argument.get(), v);
        } else if (e instanceof StringLength length) {
            expr(length.text.get(), v);
        } else if (e instanceof CropEquals comparison) {
            expr(comparison.left.get(), v);
            expr(comparison.right.get(), v);
        } else if (e instanceof StrEquals) {
            expr(((StrEquals) e).left.get(), v);
            expr(((StrEquals) e).right.get(), v);
        } else if (e instanceof Not) {
            expr(((Not) e).operand.get(), v);
        } else if (e instanceof Access) {
            for (ExprSlot arg : ((Access) e).args) expr(arg.get(), v);
        } else if (e instanceof NewIntGrid grid) {
            expr(grid.rows.get(), v);
            expr(grid.columns.get(), v);
        } else if (e instanceof IntArrayLiteral array) {
            for (ExprSlot element : array.elements()) expr(element.get(), v);
        } else if (e instanceof NewIntArray) {
            expr(((NewIntArray) e).length.get(), v);
        } else if (e instanceof NewObjectArray) {
            expr(((NewObjectArray) e).length.get(), v);
        } else if (e instanceof ObjectArrayGet) {
            expr(((ObjectArrayGet) e).index.get(), v);
        } else if (e instanceof ListContains) {
            expr(((ListContains) e).value.get(), v);
        } else if (e instanceof CollectionCall) {
            ExprSlot argument = ((CollectionCall) e).argument;
            if (argument != null) expr(argument.get(), v);
        } else if (e instanceof FunctionValue function) {
            walk(function.callback, v);
        } else if (e instanceof FunctionCall function) {
            expr(function.argument.get(), v);
        } else if (e instanceof StreamCall stream) {
            for (StreamCall.Stage stage : stream.stages) walk(stage.callback(), v);
            if (stream.callback != null) walk(stream.callback, v);
        } else if (e instanceof UserCall) {
            for (ExprSlot arg : ((UserCall) e).args) expr(arg.get(), v);
        } else if (e instanceof MethodCall) {
            for (ExprSlot arg : ((MethodCall) e).args) expr(arg.get(), v);
        }
    }
}
