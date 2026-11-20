package com.ccs.coffee_n_crops.lang.print;

import com.ccs.coffee_n_crops.lang.ast.expr.AtomicCounter;
import com.ccs.coffee_n_crops.lang.ast.stmt.SynchronizedStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.LockStmt;
import com.ccs.coffee_n_crops.lang.ast.expr.NewReentrantLock;
import com.ccs.coffee_n_crops.lang.ast.stmt.TogetherStmt;

import com.ccs.coffee_n_crops.lang.ast.ControlFlow;
import com.ccs.coffee_n_crops.lang.ast.ClassDecl;
import com.ccs.coffee_n_crops.lang.ast.ConstructorDecl;
import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.ast.MethodDecl;
import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.ast.expr.Access;
import com.ccs.coffee_n_crops.lang.ast.expr.NewObject;
import com.ccs.coffee_n_crops.lang.ast.expr.FieldAccess;
import com.ccs.coffee_n_crops.lang.ast.expr.InstanceCall;
import com.ccs.coffee_n_crops.lang.ast.expr.InstanceOf;
import com.ccs.coffee_n_crops.lang.ast.expr.StaticConstant;
import com.ccs.coffee_n_crops.lang.ast.expr.ThisRef;
import com.ccs.coffee_n_crops.lang.ast.expr.FarmhandRef;
import com.ccs.coffee_n_crops.lang.ast.expr.Binary;
import com.ccs.coffee_n_crops.lang.ast.expr.Literal;
import com.ccs.coffee_n_crops.lang.ast.expr.MethodCall;
import com.ccs.coffee_n_crops.lang.ast.expr.NewList;
import com.ccs.coffee_n_crops.lang.ast.expr.ListContains;
import com.ccs.coffee_n_crops.lang.ast.expr.CollectionCall;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamCall;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamCallback;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamLambda;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamMethodRef;
import com.ccs.coffee_n_crops.lang.ast.expr.NewIntArray;
import com.ccs.coffee_n_crops.lang.ast.expr.NewObjectArray;
import com.ccs.coffee_n_crops.lang.ast.expr.ObjectArrayGet;
import com.ccs.coffee_n_crops.lang.ast.stmt.ArrayAssign;
import com.ccs.coffee_n_crops.lang.ast.stmt.ObjectArrayAssign;
import com.ccs.coffee_n_crops.lang.ast.expr.Not;
import com.ccs.coffee_n_crops.lang.ast.expr.PostIncrement;
import com.ccs.coffee_n_crops.lang.ast.expr.StrEquals;
import com.ccs.coffee_n_crops.lang.ast.expr.UserCall;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.Assign;
import com.ccs.coffee_n_crops.lang.ast.stmt.FieldAssign;
import com.ccs.coffee_n_crops.lang.ast.stmt.SuperCall;
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
import com.ccs.coffee_n_crops.lang.ast.stmt.BreakStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.CompoundAssign;
import com.ccs.coffee_n_crops.lang.ast.stmt.ContinueStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.DecrementStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.DoWhileStmt;
import com.ccs.coffee_n_crops.lang.print.Token.Kind;
import com.ccs.coffee_n_crops.lang.runtime.ObjectMethods;
import com.ccs.coffee_n_crops.lang.types.JType;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns AST nodes into real Java source. The block editor renders these tokens,
 * so what the player sees is always exactly the code that runs.
 */
public final class JavaPrinter {
    /** Precedence of {@code !}; binds tighter than every binary operator. */
    private static final int PREC_UNARY = 7;
    /** Literals, names and calls never need parentheses. */
    private static final int PREC_PRIMARY = 9;
    private static final int PREC_NONE = 0;

    private JavaPrinter() {
    }

    /** The fixed method signature that wraps the player's program. */
    public static List<Token> frameOpen() {
        List<Token> out = new ArrayList<>();
        add(out, "void", Kind.KEYWORD);
        add(out, " ", Kind.PUNCT);
        add(out, "run", Kind.METHOD);
        add(out, "(", Kind.PUNCT);
        add(out, "Farmhand", Kind.TYPE);
        add(out, " ", Kind.PUNCT);
        add(out, "farmer", Kind.IDENT);
        add(out, ") {", Kind.PUNCT);
        return out;
    }

    /** Instance methods show exactly their parameters; legacy helpers retain explicit farmer. */
    public static List<Token> methodOpen(MethodDecl method) {
        List<Token> out = new ArrayList<>();
        boolean signature = method.isInterfaceSignature();
        if (method.isInstance() && !signature && requiresPublicMethod(method)) {
            add(out, "public ", Kind.KEYWORD);
        }
        add(out, method.returnType.javaName, method.returnType == JType.VOID ? Kind.KEYWORD : Kind.TYPE);
        add(out, " ", Kind.PUNCT);
        add(out, method.name, Kind.METHOD);
        add(out, "(", Kind.PUNCT);
        parameters(method.parameters, !method.isInstance(), out);
        add(out, signature ? ");" : ") {", Kind.PUNCT);
        return out;
    }

    private static boolean requiresPublicMethod(MethodDecl method) {
        if (method.sameSignature(ObjectMethods.signature(method.name))) return true;
        ClassDecl owner = (ClassDecl) method.parent;
        if (!(owner.parent instanceof Block)) return false;
        // A subclass can implement an interface by inheriting a public method from a base class.
        for (ClassDecl declaration : ((Block) owner.parent).classes()) {
            if (!declaration.isInterface) continue;
            for (MethodDecl contract : declaration.methods()) {
                if (contract.sameSignature(method)) return true;
            }
        }
        return false;
    }

    public static List<Token> classOpen(ClassDecl declaration) {
        List<Token> out = new ArrayList<>();
        add(out, declaration.isInterface ? "interface " : "class ", Kind.KEYWORD);
        add(out, declaration.name, Kind.TYPE);
        if (declaration.superName != null && !declaration.superName.isEmpty()) {
            add(out, " extends ", Kind.KEYWORD);
            add(out, declaration.superName, Kind.TYPE);
        }
        if (declaration.interfaceName != null) {
            add(out, " implements ", Kind.KEYWORD);
            add(out, declaration.interfaceName, Kind.TYPE);
        }
        add(out, " {", Kind.PUNCT);
        return out;
    }

    public static List<Token> constructorOpen(ConstructorDecl constructor) {
        List<Token> out = new ArrayList<>();
        String name = constructor.parent instanceof ClassDecl ? ((ClassDecl) constructor.parent).name : "Constructor";
        add(out, name, Kind.METHOD);
        add(out, "(", Kind.PUNCT);
        parameters(constructor.parameters, false, out);
        add(out, ") {", Kind.PUNCT);
        return out;
    }

    public static List<Token> fieldLine(VarDecl field) {
        List<Token> out = new ArrayList<>();
        if (field.volatileField) add(out, "volatile ", Kind.KEYWORD);
        out.addAll(line(field));
        return out;
    }

    public static List<Token> togetherNext() {
        return java.util.Collections.singletonList(new Token("}, () -> {", Kind.PUNCT, null));
    }

    /** Opens the second farmhand's lambda. */
    public static List<Token> teamNext(TeamStmt team) {
        List<Token> out = new ArrayList<>();
        add(out, "}, ", Kind.PUNCT);
        add(out, team.secondFarmer.name, Kind.IDENT);
        add(out, " -> {", Kind.PUNCT);
        return out;
    }

    public static List<Token> togetherEnd() {
        return java.util.Collections.singletonList(new Token("});", Kind.PUNCT, null));
    }

    /** Closing line of a consumer call with a block lambda. */
    public static List<Token> streamLambdaEnd() {
        return java.util.Collections.singletonList(new Token("});", Kind.PUNCT, null));
    }

    /** Opens the protected body after the lock has been acquired. */
    public static List<Token> lockTryLine() {
        List<Token> out = new ArrayList<>();
        add(out, "try", Kind.KEYWORD);
        add(out, " {", Kind.PUNCT);
        return out;
    }

    /** Opens the generated cleanup. */
    public static List<Token> lockFinallyLine() { return finallyLine(); }

    /** Cleanup displays the same operand. Only acquisition is editable. */
    public static List<Token> lockUnlockLine(LockStmt guarded) {
        List<Token> operand = new ArrayList<>();
        slot(guarded.lock, operand, PREC_PRIMARY, false);
        List<Token> out = new ArrayList<>();
        add(out, text(operand), Kind.IDENT);
        add(out, ".unlock", Kind.METHOD);
        add(out, "();", Kind.PUNCT);
        return out;
    }

    public static List<Token> overrideLine() {
        List<Token> out = new ArrayList<>();
        add(out, "@Override", Kind.KEYWORD);
        return out;
    }

    private static void parameters(List<VarDecl> parameters, boolean farmer, List<Token> out) {
        boolean comma = farmer;
        if (farmer) {
            add(out, "Farmhand", Kind.TYPE);
            add(out, " farmer", Kind.IDENT);
        }
        for (VarDecl parameter : parameters) {
            if (comma) add(out, ", ", Kind.PUNCT);
            if (parameter.isFinal) add(out, "final ", Kind.KEYWORD);
            add(out, parameter.type.javaName, Kind.TYPE);
            add(out, " ", Kind.PUNCT);
            add(out, parameter.name, Kind.IDENT);
            comma = true;
        }
    }

    public static List<Token> closeBrace() {
        List<Token> out = new ArrayList<>();
        add(out, "}", Kind.PUNCT);
        return out;
    }

    /** The line between the two branches of an if statement. */
    public static List<Token> elseLine() {
        List<Token> out = new ArrayList<>();
        add(out, "} ", Kind.PUNCT);
        add(out, "else", Kind.KEYWORD);
        add(out, " {", Kind.PUNCT);
        return out;
    }

    /** The line that opens an else-if arm. */
    public static List<Token> elseIfLine(IfStmt.Branch branch) {
        List<Token> out = new ArrayList<>();
        add(out, "} ", Kind.PUNCT);
        add(out, "else if", Kind.KEYWORD);
        add(out, " (", Kind.PUNCT);
        slot(branch.cond, out, PREC_NONE, false);
        add(out, ") {", Kind.PUNCT);
        return out;
    }

    /** The line that starts an exception handler. */
    public static List<Token> catchLine(TryStmt.Catch arm) {
        List<Token> out = new ArrayList<>();
        add(out, "} ", Kind.PUNCT);
        add(out, "catch", Kind.KEYWORD);
        add(out, " (", Kind.PUNCT);
        add(out, arm.typeName, Kind.TYPE);
        add(out, " ", Kind.PUNCT);
        add(out, arm.name, Kind.IDENT);
        add(out, ") {", Kind.PUNCT);
        return out;
    }

    /** The cleanup arm shares Java's closing/opening brace layout with catch and else. */
    public static List<Token> finallyLine() {
        List<Token> out = new ArrayList<>();
        add(out, "} ", Kind.PUNCT);
        add(out, "finally", Kind.KEYWORD);
        add(out, " {", Kind.PUNCT);
        return out;
    }

    /** {@code case N:} */
    public static List<Token> caseLine(SwitchStmt.Case c) {
        List<Token> out = new ArrayList<>();
        add(out, "case", Kind.KEYWORD);
        add(out, " ", Kind.PUNCT);
        slot(c.label, out, PREC_NONE, false);
        add(out, ":", Kind.PUNCT);
        return out;
    }

    /** {@code default:} */
    public static List<Token> defaultLine() {
        List<Token> out = new ArrayList<>();
        add(out, "default", Kind.KEYWORD);
        add(out, ":", Kind.PUNCT);
        return out;
    }

    /** The {@code break;} that closes every case. */
    public static List<Token> breakLine() {
        List<Token> out = new ArrayList<>();
        add(out, "break", Kind.KEYWORD);
        add(out, ";", Kind.PUNCT);
        return out;
    }

    /** The condition on the closing line of a do-while loop. */
    public static List<Token> doWhileEnd(DoWhileStmt loop) {
        List<Token> out = new ArrayList<>();
        add(out, "} ", Kind.PUNCT);
        add(out, "while", Kind.KEYWORD);
        add(out, " (", Kind.PUNCT);
        slot(loop.cond, out, PREC_NONE, false);
        add(out, ");", Kind.PUNCT);
        return out;
    }

    /** The first source line of a statement: the whole statement, or the header of a compound one. */
    public static List<Token> line(Stmt stmt) {
        List<Token> out = new ArrayList<>();
        if (stmt instanceof StreamForEachStmt each) {
            streamCall(each.call, null, out);
        } else if (stmt instanceof TeamStmt team) {
            add(out, "farmer", Kind.IDENT);
            add(out, ".workTogether", Kind.METHOD);
            add(out, "(", Kind.PUNCT);
            add(out, team.firstFarmer.name, Kind.IDENT);
            add(out, " -> {", Kind.PUNCT);
        } else if (stmt instanceof TogetherStmt) {
            add(out, "farmer", Kind.IDENT);
            add(out, ".runTogether", Kind.METHOD);
            add(out, "(() -> {", Kind.PUNCT);
        } else if (stmt instanceof SynchronizedStmt guarded) {
            add(out, "synchronized", Kind.KEYWORD);
            add(out, " (", Kind.PUNCT);
            slot(guarded.monitor, out, PREC_NONE, false);
            add(out, ") {", Kind.PUNCT);
        } else if (stmt instanceof LockStmt guarded) {
            slot(guarded.lock, out, PREC_PRIMARY, false);
            add(out, ".lock", Kind.METHOD);
            add(out, "();", Kind.PUNCT);
        } else if (stmt instanceof FieldAssign) {
            FieldAssign assignment = (FieldAssign) stmt;
            fieldAccess(assignment.target, null, out);
            add(out, " = ", Kind.PUNCT);
            slot(assignment.value, out, PREC_NONE, false);
            add(out, ";", Kind.PUNCT);
        } else if (stmt instanceof SuperCall) {
            add(out, "super", Kind.KEYWORD);
            arguments(((SuperCall) stmt).args, out);
            add(out, ";", Kind.PUNCT);
        } else if (stmt instanceof TryStmt) {
            add(out, "try", Kind.KEYWORD);
            add(out, " {", Kind.PUNCT);
        } else if (stmt instanceof ForStmt) {
            ForStmt f = (ForStmt) stmt;
            add(out, "for", Kind.KEYWORD);
            add(out, " (", Kind.PUNCT);
            varDecl(f.init, out);
            add(out, "; ", Kind.PUNCT);
            slot(f.cond, out, PREC_NONE, false);
            add(out, "; ", Kind.PUNCT);
            fixed(f.update, out);
            add(out, ") {", Kind.PUNCT);
        } else if (stmt instanceof WhileStmt) {
            add(out, "while", Kind.KEYWORD);
            add(out, " (", Kind.PUNCT);
            slot(((WhileStmt) stmt).cond, out, PREC_NONE, false);
            add(out, ") {", Kind.PUNCT);
        } else if (stmt instanceof DoWhileStmt) {
            add(out, "do", Kind.KEYWORD);
            add(out, " {", Kind.PUNCT);
        } else if (stmt instanceof ForEachStmt) {
            VarDecl v = ((ForEachStmt) stmt).variable;
            add(out, "for", Kind.KEYWORD);
            add(out, " (", Kind.PUNCT);
            add(out, v.type.javaName, Kind.TYPE);
            add(out, " ", Kind.PUNCT);
            add(out, v.name, Kind.IDENT);
            add(out, " : ", Kind.PUNCT);
            String source = ((ForEachStmt) stmt).source;
            if (source == null) {
                add(out, ForEachStmt.VALUES_TYPE, Kind.TYPE);
                add(out, ".", Kind.PUNCT);
                add(out, ForEachStmt.VALUES_METHOD, Kind.METHOD);
                add(out, "()", Kind.PUNCT);
            } else {
                add(out, source, Kind.IDENT);
            }
            add(out, ") {", Kind.PUNCT);
        } else if (stmt instanceof SwitchStmt) {
            add(out, "switch", Kind.KEYWORD);
            add(out, " (", Kind.PUNCT);
            slot(((SwitchStmt) stmt).subject, out, PREC_NONE, false);
            add(out, ") {", Kind.PUNCT);
        } else if (stmt instanceof IfStmt) {
            add(out, "if", Kind.KEYWORD);
            add(out, " (", Kind.PUNCT);
            slot(((IfStmt) stmt).cond, out, PREC_NONE, false);
            add(out, ") {", Kind.PUNCT);
        } else if (stmt instanceof VarDecl) {
            varDecl((VarDecl) stmt, out);
            add(out, ";", Kind.PUNCT);
        } else if (stmt instanceof Assign) {
            Assign a = (Assign) stmt;
            add(out, a.name, Kind.IDENT);
            add(out, " = ", Kind.PUNCT);
            slot(a.value, out, PREC_NONE, false);
            add(out, ";", Kind.PUNCT);
        } else if (stmt instanceof ArrayAssign) {
            ArrayAssign assignment = (ArrayAssign) stmt;
            arrayAssignment(assignment.name, assignment.index, assignment.value, out);
        } else if (stmt instanceof ObjectArrayAssign) {
            ObjectArrayAssign assignment = (ObjectArrayAssign) stmt;
            arrayAssignment(assignment.name, assignment.index, assignment.value, out);
        } else if (stmt instanceof ListAdd) {
            ListAdd add = (ListAdd) stmt;
            listMutation(add.name, "add", out, add.value);
        } else if (stmt instanceof ListSet) {
            ListSet set = (ListSet) stmt;
            listMutation(set.name, "set", out, set.index, set.value);
        } else if (stmt instanceof ListRemove) {
            ListRemove remove = (ListRemove) stmt;
            listMutation(remove.name, "remove", out, remove.index);
        } else if (stmt instanceof AssertStmt assertion) {
            add(out, "assert ", Kind.KEYWORD);
            slot(assertion.condition, out, PREC_NONE, false);
            if (assertion.detail != null) {
                add(out, " : ", Kind.PUNCT);
                slot(assertion.detail, out, PREC_NONE, false);
            }
            add(out, ";", Kind.PUNCT);
        } else if (stmt instanceof CompoundAssign) {
            CompoundAssign assignment = (CompoundAssign) stmt;
            add(out, assignment.name, Kind.IDENT);
            add(out, " += ", Kind.OPERATOR);
            slot(assignment.value, out, PREC_NONE, false);
            add(out, ";", Kind.PUNCT);
        } else if (stmt instanceof DecrementStmt) {
            add(out, ((DecrementStmt) stmt).name, Kind.IDENT);
            add(out, "--", Kind.OPERATOR);
            add(out, ";", Kind.PUNCT);
        } else if (stmt instanceof BreakStmt) {
            return breakLine();
        } else if (stmt instanceof ContinueStmt) {
            add(out, "continue", Kind.KEYWORD);
            add(out, ";", Kind.PUNCT);
        } else if (stmt instanceof ReturnStmt) {
            add(out, "return", Kind.KEYWORD);
            ExprSlot value = ((ReturnStmt) stmt).value;
            if (value != null) {
                add(out, " ", Kind.PUNCT);
                slot(value, out, PREC_NONE, false);
            }
            add(out, ";", Kind.PUNCT);
        } else if (stmt instanceof ExprStmt) {
            fixed(((ExprStmt) stmt).expr, out);
            add(out, ";", Kind.PUNCT);
        } else {
            throw new IllegalArgumentException("No printer for " + stmt.getClass().getSimpleName());
        }
        return out;
    }

    /** Full, copy-pasteable source; old single-method programs keep their original format. */
    public static String toSource(Block program) {
        StringBuilder sb = new StringBuilder();
        boolean classes = !program.classes().isEmpty();
        int depth = classes ? 1 : 0;
        if (classes) sb.append("class Program {\n");
        indent(sb, depth).append(text(frameOpen())).append('\n');
        appendBlock(program, depth + 1, sb);
        indent(sb, depth).append("}\n");
        for (MethodDecl method : program.methods()) {
            sb.append('\n');
            indent(sb, depth).append(text(methodOpen(method))).append('\n');
            appendBlock(method.body, depth + 1, sb);
            indent(sb, depth).append("}\n");
        }
        if (classes) sb.append("}\n");
        for (ClassDecl declaration : program.classes()) {
            sb.append('\n').append(text(classOpen(declaration))).append('\n');
            for (VarDecl field : declaration.fields()) {
                indent(sb, 1).append(text(fieldLine(field))).append('\n');
            }
            ConstructorDecl constructor = declaration.constructor();
            if (constructor != null) {
                indent(sb, 1).append(text(constructorOpen(constructor))).append('\n');
                appendBlock(constructor.body, 2, sb);
                indent(sb, 1).append("}\n");
            }
            for (MethodDecl method : declaration.methods()) {
                if (method.override) indent(sb, 1).append(text(overrideLine())).append('\n');
                indent(sb, 1).append(text(methodOpen(method))).append('\n');
                if (!method.isInterfaceSignature()) {
                    appendBlock(method.body, 2, sb);
                    indent(sb, 1).append("}\n");
                }
            }
            sb.append("}\n");
        }
        return sb.toString();
    }

    public static String text(List<Token> tokens) {
        StringBuilder sb = new StringBuilder();
        for (Token t : tokens) sb.append(t.text);
        return sb.toString();
    }

    private static void appendBlock(Block block, int depth, StringBuilder sb) {
        for (Stmt stmt : block.statements()) {
            indent(sb, depth).append(text(line(stmt))).append('\n');
            if (stmt instanceof TeamStmt team) {
                appendBlock(team.first, depth + 1, sb);
                indent(sb, depth).append(text(teamNext(team))).append('\n');
                appendBlock(team.second, depth + 1, sb);
                indent(sb, depth).append(text(togetherEnd())).append('\n');
            } else if (stmt instanceof TogetherStmt together) {
                appendBlock(together.first, depth + 1, sb);
                indent(sb, depth).append(text(togetherNext())).append('\n');
                appendBlock(together.second, depth + 1, sb);
                indent(sb, depth).append(text(togetherEnd())).append('\n');
            } else if (stmt instanceof StreamForEachStmt each) {
                appendBlock(each.childBlocks().get(0), depth + 1, sb);
                indent(sb, depth).append(text(streamLambdaEnd())).append('\n');
            } else if (stmt instanceof LockStmt guarded) {
                indent(sb, depth).append(text(lockTryLine())).append('\n');
                appendBlock(guarded.body, depth + 1, sb);
                indent(sb, depth).append(text(lockFinallyLine())).append('\n');
                indent(sb, depth + 1).append(text(lockUnlockLine(guarded))).append('\n');
                indent(sb, depth).append("}\n");
            } else if (stmt instanceof TryStmt) {
                TryStmt guarded = (TryStmt) stmt;
                appendBlock(guarded.tryBlock, depth + 1, sb);
                for (TryStmt.Catch arm : guarded.catches()) {
                    indent(sb, depth).append(text(catchLine(arm))).append('\n');
                    appendBlock(arm.body, depth + 1, sb);
                }
                if (guarded.finallyBlock() != null) {
                    indent(sb, depth).append(text(finallyLine())).append('\n');
                    appendBlock(guarded.finallyBlock(), depth + 1, sb);
                }
                indent(sb, depth).append("}\n");
            } else if (stmt instanceof IfStmt) {
                IfStmt i = (IfStmt) stmt;
                appendBlock(i.thenBlock, depth + 1, sb);
                for (IfStmt.Branch b : i.branches().subList(1, i.branches().size())) {
                    indent(sb, depth).append(text(elseIfLine(b))).append('\n');
                    appendBlock(b.block, depth + 1, sb);
                }
                if (i.elseBlock() != null) {
                    indent(sb, depth).append(text(elseLine())).append('\n');
                    appendBlock(i.elseBlock(), depth + 1, sb);
                }
                indent(sb, depth).append("}\n");
            } else if (stmt instanceof SwitchStmt) {
                SwitchStmt s = (SwitchStmt) stmt;
                for (SwitchStmt.Case c : s.cases()) {
                    indent(sb, depth + 1).append(text(caseLine(c))).append('\n');
                    appendCaseBody(c.body, depth + 2, sb);
                }
                if (s.defaultBlock() != null) {
                    indent(sb, depth + 1).append(text(defaultLine())).append('\n');
                    appendCaseBody(s.defaultBlock(), depth + 2, sb);
                }
                indent(sb, depth).append("}\n");
            } else if (stmt instanceof DoWhileStmt) {
                DoWhileStmt loop = (DoWhileStmt) stmt;
                appendBlock(loop.body, depth + 1, sb);
                indent(sb, depth).append(text(doWhileEnd(loop))).append('\n');
            } else if (!stmt.childBlocks().isEmpty()) {
                appendBlock(stmt.childBlocks().get(0), depth + 1, sb); // a loop body
                indent(sb, depth).append("}\n");
            }
        }
    }

    private static void appendCaseBody(Block body, int depth, StringBuilder sb) {
        appendBlock(body, depth, sb);
        if (ControlFlow.completesNormally(body)) {
            indent(sb, depth).append(text(breakLine())).append('\n');
        }
    }

    private static StringBuilder indent(StringBuilder sb, int depth) {
        for (int i = 0; i < depth; i++) sb.append("    ");
        return sb;
    }

    private static void varDecl(VarDecl d, List<Token> out) {
        if (d.isStatic) add(out, "static ", Kind.KEYWORD);
        if (d.isFinal) add(out, "final ", Kind.KEYWORD);
        add(out, d.type.javaName, Kind.TYPE);
        add(out, " ", Kind.PUNCT);
        add(out, d.name, Kind.IDENT);
        add(out, " = ", Kind.PUNCT);
        slot(d.init, out, PREC_NONE, false);
    }

    /**
     * Prints the expression in a slot. Tokens that stand for the slot's whole expression carry the slot,
     * so tapping them selects it: a literal or name, or the operator of a compound expression.
     *
     * @param parentPrec precedence of the enclosing operator, to decide on parentheses
     * @param rightSide  true for the right operand of a binary operator (left-associativity)
     */
    private static void slot(ExprSlot s, List<Token> out, int parentPrec, boolean rightSide) {
        Expr e = s.get();
        int prec = precedence(e);
        boolean parens = prec < parentPrec || (rightSide && prec == parentPrec);
        if (parens) add(out, "(", Kind.PUNCT);

        if (e instanceof AtomicCounter counter) {
            atomicCounter(counter, s, out);
        } else if (e instanceof NewReentrantLock) {
            newReentrantLock(s, out);
        } else if (e instanceof InstanceOf check) {
            slot(check.operand, out, Binary.Op.LT.precedence, false);
            out.add(new Token(" instanceof ", Kind.KEYWORD, s));
            add(out, check.target.javaName, Kind.TYPE);
        } else if (e instanceof StaticConstant constant) {
            out.add(new Token(constant.className + "." + constant.fieldName, Kind.CONSTANT, s));
        } else if (e instanceof NewObject || e instanceof FieldAccess || e instanceof InstanceCall
                || e instanceof ThisRef || e instanceof FarmhandRef) {
            objectExpression(e, s, out);
        } else if (e instanceof Literal) {
            Literal lit = (Literal) e;
            Kind kind = lit.type == JType.DIRECTION ? Kind.CONSTANT
                    : lit.type == JType.BOOLEAN ? Kind.KEYWORD
                    : lit.type == JType.STRING ? Kind.STRING : Kind.NUMBER;
            out.add(new Token(literalText(lit.type, lit.value), kind, s));
        } else if (e instanceof StrEquals) {
            // left.equals(right): the method name selects the whole comparison.
            StrEquals eq = (StrEquals) e;
            slot(eq.left, out, PREC_PRIMARY, false);
            out.add(new Token(".equals", Kind.METHOD, s));
            add(out, "(", Kind.PUNCT);
            slot(eq.right, out, PREC_NONE, false);
            add(out, ")", Kind.PUNCT);
        } else if (e instanceof VarRef) {
            out.add(new Token(((VarRef) e).name, Kind.IDENT, s));
        } else if (e instanceof Not) {
            out.add(new Token("!", Kind.OPERATOR, s));
            slot(((Not) e).operand, out, PREC_UNARY, false);
        } else if (e instanceof Binary) {
            Binary b = (Binary) e;
            slot(b.left, out, prec, false);
            add(out, " ", Kind.PUNCT);
            out.add(new Token(b.op().symbol, Kind.OPERATOR, s));
            add(out, " ", Kind.PUNCT);
            slot(b.right, out, prec, true);
        } else if (e instanceof NewList) {
            out.add(new Token("new ArrayList<>()", Kind.TYPE, s));
        } else if (e instanceof NewIntArray) {
            arrayCreation(JType.INT, ((NewIntArray) e).length, s, out);
        } else if (e instanceof NewObjectArray) {
            NewObjectArray array = (NewObjectArray) e;
            arrayCreation(array.elementType, array.length, s, out);
        } else if (e instanceof ObjectArrayGet) {
            ObjectArrayGet array = (ObjectArrayGet) e;
            out.add(new Token(array.name, Kind.IDENT, s));
            add(out, "[", Kind.PUNCT);
            slot(array.index, out, PREC_NONE, false);
            add(out, "]", Kind.PUNCT);
        } else if (e instanceof ListContains) {
            listContains((ListContains) e, s, out);
        } else if (e instanceof CollectionCall call) {
            collectionCall(call, s, out);
        } else if (e instanceof StreamCall stream) {
            streamCall(stream, s, out);
        } else if (e instanceof Access) {
            access((Access) e, s, out);
        } else if (e instanceof UserCall) {
            userCall((UserCall) e, s, out);
        } else if (e instanceof MethodCall) {
            // The method name selects the slot; any arguments are slots of their own.
            MethodCall c = (MethodCall) e;
            if (c.args.isEmpty()) {
                out.add(new Token(c.receiver + "." + c.name + "()", Kind.METHOD, s));
            } else {
                out.add(new Token(c.receiver + "." + c.name, Kind.METHOD, s));
                add(out, "(", Kind.PUNCT);
                for (int i = 0; i < c.args.size(); i++) {
                    if (i > 0) add(out, ", ", Kind.PUNCT);
                    slot(c.args.get(i), out, PREC_NONE, false);
                }
                add(out, ")", Kind.PUNCT);
            }
        } else {
            fixed(e, out);
        }

        if (parens) add(out, ")", Kind.PUNCT);
    }

    /** A constant exactly as Java source spells it: {@code 5}, {@code 0.5}, {@code "wheat"}, {@code Direction.EAST}. */
    public static String literalText(JType type, Object value) {
        if (value == null) return "null";
        if (type == JType.LONG) return value + "L";
        if (type == JType.STRING) return "\"" + value + "\"";
        if (type == JType.DIRECTION) return "Direction." + value;
        return String.valueOf(value);
    }

    /** An array or list read. The variable name selects the whole expression; indexes are slots. */
    private static void access(Access a, ExprSlot s, List<Token> out) {
        switch (a.kind) {
            case INDEX:
                out.add(new Token(a.name, Kind.IDENT, s));
                add(out, "[", Kind.PUNCT);
                slot(a.args.get(0), out, PREC_NONE, false);
                add(out, "]", Kind.PUNCT);
                break;
            case GRID_INDEX:
                out.add(new Token(a.name, Kind.IDENT, s));
                add(out, "[", Kind.PUNCT);
                slot(a.args.get(0), out, PREC_NONE, false);
                add(out, "][", Kind.PUNCT);
                slot(a.args.get(1), out, PREC_NONE, false);
                add(out, "]", Kind.PUNCT);
                break;
            case LIST_GET:
                out.add(new Token(a.name + ".get", Kind.METHOD, s));
                add(out, "(", Kind.PUNCT);
                slot(a.args.get(0), out, PREC_NONE, false);
                add(out, ")", Kind.PUNCT);
                break;
            default:
                out.add(new Token(accessText(a.kind, a.name), Kind.IDENT, s));
                break;
        }
    }

    /** Source text of an access, with {@code ▢} standing for each index. Also labels palette chips. */
    public static String accessText(Access.Kind kind, String name) {
        switch (kind) {
            case INDEX: return name + "[▢]";
            case LENGTH: return name + ".length";
            case GRID_INDEX: return name + "[▢][▢]";
            case GRID_ROWS: return name + ".length";
            case GRID_COLS: return name + "[0].length";
            case LIST_GET: return name + ".get(▢)";
            case LIST_SIZE: return name + ".size()";
            default: throw new IllegalArgumentException(kind.name());
        }
    }

    /** Prints an expression that is part of the statement's fixed shape; only its arguments are slots. */
    private static void fixed(Expr e, List<Token> out) {
        if (e instanceof AtomicCounter counter) {
            atomicCounter(counter, null, out);
            return;
        }
        if (e instanceof NewReentrantLock) {
            newReentrantLock(null, out);
            return;
        }
        if (e instanceof CollectionCall call) {
            collectionCall(call, null, out);
            return;
        }
        if (e instanceof StreamCall stream) {
            streamCall(stream, null, out);
            return;
        }
        if (e instanceof ListContains) {
            listContains((ListContains) e, null, out);
            return;
        }
        if (e instanceof NewObject || e instanceof InstanceCall || e instanceof FieldAccess
                || e instanceof ThisRef || e instanceof FarmhandRef) {
            objectExpression(e, null, out);
        } else if (e instanceof PostIncrement) {
            add(out, ((PostIncrement) e).name, Kind.IDENT);
            add(out, "++", Kind.PUNCT);
        } else if (e instanceof UserCall) {
            userCall((UserCall) e, null, out);
        } else if (e instanceof MethodCall) {
            MethodCall c = (MethodCall) e;
            add(out, c.receiver, Kind.IDENT);
            add(out, ".", Kind.PUNCT);
            add(out, c.name, Kind.METHOD);
            add(out, "(", Kind.PUNCT);
            for (int i = 0; i < c.args.size(); i++) {
                if (i > 0) add(out, ", ", Kind.PUNCT);
                slot(c.args.get(i), out, PREC_NONE, false);
            }
            add(out, ")", Kind.PUNCT);
        } else {
            throw new IllegalArgumentException("No printer for " + e.getClass().getSimpleName());
        }
    }

    private static void objectExpression(Expr expression, ExprSlot selected, List<Token> out) {
        if (expression instanceof NewObject) {
            NewObject creation = (NewObject) expression;
            add(out, "new ", Kind.KEYWORD);
            out.add(new Token(creation.className, Kind.TYPE, selected));
            arguments(creation.args, out);
        } else if (expression instanceof FieldAccess) {
            fieldAccess((FieldAccess) expression, selected, out);
        } else if (expression instanceof InstanceCall) {
            InstanceCall call = (InstanceCall) expression;
            slot(call.receiver, out, PREC_PRIMARY, false);
            add(out, ".", Kind.PUNCT);
            out.add(new Token(call.name, Kind.METHOD, selected));
            arguments(call.args, out);
        } else {
            out.add(new Token(expression instanceof ThisRef ? "this" : "farmer",
                    expression instanceof ThisRef ? Kind.KEYWORD : Kind.IDENT, selected));
        }
    }

    private static void fieldAccess(FieldAccess access, ExprSlot selected, List<Token> out) {
        slot(access.receiver, out, PREC_PRIMARY, false);
        add(out, ".", Kind.PUNCT);
        out.add(new Token(access.name, Kind.IDENT, selected));
    }

    private static void arguments(List<ExprSlot> arguments, List<Token> out) {
        add(out, "(", Kind.PUNCT);
        for (int i = 0; i < arguments.size(); i++) {
            if (i > 0) add(out, ", ", Kind.PUNCT);
            slot(arguments.get(i), out, PREC_NONE, false);
        }
        add(out, ")", Kind.PUNCT);
    }

    private static void listMutation(String name, String method, List<Token> out, ExprSlot... args) {
        add(out, name, Kind.IDENT);
        add(out, ".", Kind.PUNCT);
        add(out, method, Kind.METHOD);
        arguments(java.util.Arrays.asList(args), out);
        add(out, ";", Kind.PUNCT);
    }

    private static void arrayAssignment(String name, ExprSlot index, ExprSlot value, List<Token> out) {
        add(out, name, Kind.IDENT);
        add(out, "[", Kind.PUNCT);
        slot(index, out, PREC_NONE, false);
        add(out, "] = ", Kind.PUNCT);
        slot(value, out, PREC_NONE, false);
        add(out, ";", Kind.PUNCT);
    }

    private static void arrayCreation(JType component, ExprSlot length, ExprSlot selected, List<Token> out) {
        out.add(new Token("new", Kind.KEYWORD, selected));
        add(out, " ", Kind.PUNCT);
        out.add(new Token(component.javaName, Kind.TYPE, selected));
        add(out, "[", Kind.PUNCT);
        slot(length, out, PREC_NONE, false);
        add(out, "]", Kind.PUNCT);
    }

    private static void listContains(ListContains contains, ExprSlot selected, List<Token> out) {
        out.add(new Token(contains.name + ".contains", Kind.METHOD, selected));
        arguments(java.util.Collections.singletonList(contains.value), out);
    }

    private static void collectionCall(CollectionCall call, ExprSlot selected, List<Token> out) {
        if (call.op == CollectionCall.Op.FIND_FIRST) {
            out.add(new Token(call.receiver + ".stream().filter", Kind.METHOD, selected));
            add(out, "(", Kind.PUNCT);
            add(out, call.parameter, Kind.IDENT);
            add(out, " -> ", Kind.OPERATOR);
            add(out, call.parameter, Kind.IDENT);
            add(out, " >= ", Kind.OPERATOR);
            slot(call.argument, out, PREC_NONE, false);
            add(out, ").findFirst()", Kind.METHOD);
            return;
        }
        String receiver = call.receiver == null ? "java.util.Optional" : call.receiver;
        out.add(new Token(receiver + "." + call.op.method, Kind.METHOD, selected));
        arguments(call.argument == null ? java.util.Collections.emptyList()
                : java.util.Collections.singletonList(call.argument), out);
    }

    /** The same call syntax used when previewing an expression in the editor palette. */
    public static String collectionCallText(CollectionCall call) {
        List<Token> out = new ArrayList<>();
        collectionCall(call, null, out);
        return text(out);
    }

    private static void streamCall(StreamCall call, ExprSlot whole, List<Token> out) {
        out.add(new Token(call.source + ".stream()", Kind.METHOD, whole));
        for (StreamCall.Stage stage : call.stages) {
            streamOperation(stage.kind().method, stage.callback(), out);
        }
        streamOperation(call.terminal.method, call.callback, out);
    }

    private static void streamOperation(String name, StreamCallback callback, List<Token> out) {
        add(out, "." + name, Kind.METHOD);
        add(out, "(", Kind.PUNCT);
        if (callback instanceof StreamLambda lambda) {
            add(out, lambda.parameter.name, Kind.IDENT);
            add(out, " -> ", Kind.OPERATOR);
            if (lambda.body != null) {
                add(out, "{", Kind.PUNCT);
                return; // The lambda's statements and closing line use their own editor rows.
            }
            slot(lambda.expression, out, PREC_NONE, false);
        } else if (callback instanceof StreamMethodRef reference) {
            add(out, reference.receiver, Kind.IDENT);
            add(out, "::", Kind.OPERATOR);
            add(out, reference.method, Kind.METHOD);
        }
        add(out, ")", Kind.PUNCT);
    }

    /** The call expression, or the opening line when the callback has a block body. */
    public static String streamText(StreamCall call) {
        List<Token> out = new ArrayList<>();
        streamCall(call, null, out);
        return text(out);
    }

    private static void userCall(UserCall call, ExprSlot selected, List<Token> out) {
        out.add(new Token(call.name, Kind.METHOD, selected));
        add(out, "(", Kind.PUNCT);
        add(out, "farmer", Kind.IDENT);
        for (ExprSlot argument : call.args) {
            add(out, ", ", Kind.PUNCT);
            slot(argument, out, PREC_NONE, false);
        }
        add(out, ")", Kind.PUNCT);
    }

    private static int precedence(Expr e) {
        if (e instanceof Binary) return ((Binary) e).op().precedence;
        if (e instanceof InstanceOf) return Binary.Op.LT.precedence;
        if (e instanceof Not) return PREC_UNARY;
        return PREC_PRIMARY;
    }

    private static void atomicCounter(AtomicCounter counter, ExprSlot whole, List<Token> out) {
        if (counter.op == AtomicCounter.Op.NEW) {
            out.add(new Token("new " + JType.ATOMIC_INTEGER.javaName, Kind.TYPE, whole));
            add(out, "(", Kind.PUNCT);
            slot(counter.operand, out, PREC_NONE, false);
            add(out, ")", Kind.PUNCT);
        } else {
            slot(counter.operand, out, PREC_PRIMARY, false);
            out.add(new Token("." + counter.op.method + "()", Kind.METHOD, whole));
        }
    }

    private static void newReentrantLock(ExprSlot whole, List<Token> out) {
        add(out, "new ", Kind.KEYWORD);
        out.add(new Token(JType.REENTRANT_LOCK.javaName, Kind.TYPE, whole));
        add(out, "()", Kind.PUNCT);
    }

    private static void add(List<Token> out, String text, Kind kind) {
        out.add(new Token(text, kind, null));
    }
}
