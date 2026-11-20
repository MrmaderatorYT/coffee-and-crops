package com.ccs.coffee_n_crops.editor;

import com.ccs.coffee_n_crops.editor.CodeRow.Kind;
import com.ccs.coffee_n_crops.lang.ast.MethodDecl;
import com.ccs.coffee_n_crops.lang.ast.ClassDecl;
import com.ccs.coffee_n_crops.lang.ast.ConstructorDecl;
import com.ccs.coffee_n_crops.lang.ast.ControlFlow;
import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.DoWhileStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.IfStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.LockStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.StreamForEachStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.TeamStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.SwitchStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.TryStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.TogetherStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.print.JavaPrinter;
import com.ccs.coffee_n_crops.lang.print.Token;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Projects the nested AST onto a flat list of indented code lines. */
public final class RowFlattener {
    private RowFlattener() {
    }

    /**
     * @param cursor insertion point to mark with a caret row, or null to show none
     */
    public static List<CodeRow> flatten(Block program, EditorCursor cursor) {
        List<CodeRow> out = new ArrayList<>();
        out.add(new CodeRow(Kind.FRAME_OPEN, 0, null, JavaPrinter.frameOpen(), program, 0));
        walk(program, 1, cursor, out);
        out.add(new CodeRow(Kind.FRAME_CLOSE, 0, null, JavaPrinter.closeBrace(), program, program.size()));
        for (MethodDecl method : program.methods()) {
            out.add(new CodeRow(Kind.FRAME_OPEN, 0, method, JavaPrinter.methodOpen(method), method.body, 0));
            walk(method.body, 1, cursor, out);
            out.add(new CodeRow(Kind.FRAME_CLOSE, 0, null, JavaPrinter.closeBrace(),
                    method.body, method.body.size()));
        }
        return out;
    }

    /** One selected class, retaining the same insertion rows as the introductory editor. */
    public static List<CodeRow> flattenClass(Block program, ClassDecl selected, EditorCursor cursor,
                                              Set<Integer> folded) {
        List<CodeRow> out = new ArrayList<>();
        if (selected == null) {
            List<Token> header = new ArrayList<>();
            header.add(new Token("class", Token.Kind.KEYWORD, null));
            header.add(new Token(" Program {", Token.Kind.TYPE, null));
            out.add(new CodeRow(Kind.FRAME_OPEN, 0, null, header, program, 0));
            out.add(new CodeRow(Kind.FRAME_OPEN, 1, program, JavaPrinter.frameOpen(), program, 0));
            walk(program, 2, cursor, out);
            out.add(new CodeRow(Kind.FRAME_CLOSE, 1, null, JavaPrinter.closeBrace(), program, program.size()));
            for (MethodDecl method : program.methods()) member(method, method.body, JavaPrinter.methodOpen(method), 1, cursor, folded, out);
            out.add(new CodeRow(Kind.FRAME_CLOSE, 0, null, JavaPrinter.closeBrace(), program, program.size()));
            return out;
        }
        out.add(new CodeRow(Kind.FRAME_OPEN, 0, selected, JavaPrinter.classOpen(selected), program, 0));
        for (VarDecl field : selected.fields()) {
            out.add(new CodeRow(Kind.STATEMENT, 1, field, JavaPrinter.fieldLine(field), program, 0));
        }
        ConstructorDecl constructor = selected.constructor();
        if (constructor != null) member(constructor, constructor.body, JavaPrinter.constructorOpen(constructor), 1, cursor, folded, out);
        for (MethodDecl method : selected.methods()) {
            if (selected.isInterface) {
                out.add(new CodeRow(Kind.STATEMENT, 1, method, JavaPrinter.methodOpen(method), program, 0));
                continue;
            }
            if (method.override) out.add(new CodeRow(Kind.MID, 1, null, JavaPrinter.overrideLine(), method.body, 0));
            member(method, method.body, JavaPrinter.methodOpen(method), 1, cursor, folded, out);
        }
        out.add(new CodeRow(Kind.FRAME_CLOSE, 0, selected, JavaPrinter.closeBrace(), program, 0));
        return out;
    }

    private static void member(com.ccs.coffee_n_crops.lang.ast.Node node, Block body, List<Token> tokens,
                               int depth, EditorCursor cursor, Set<Integer> folded, List<CodeRow> out) {
        boolean collapsed = folded.contains(node.id);
        out.add(new CodeRow(Kind.FRAME_OPEN, depth, node, tokens, body, 0).withFold(node, collapsed));
        if (!collapsed) walk(body, depth + 1, cursor, out);
        out.add(new CodeRow(Kind.FRAME_CLOSE, depth, null, JavaPrinter.closeBrace(), body, body.size()));
    }

    private static void walk(Block block, int depth, EditorCursor cursor, List<CodeRow> out) {
        for (int i = 0; i < block.size(); i++) {
            caretIfHere(block, i, depth, cursor, out);
            Stmt stmt = block.get(i);
            List<Block> children = stmt.childBlocks();
            if (children.isEmpty()) {
                out.add(new CodeRow(Kind.STATEMENT, depth, stmt, JavaPrinter.line(stmt), block, i + 1));
                continue;
            }

            // A compound statement. Tapping its header puts the cursor at the top of its first
            // block; tapping its closing brace puts the cursor after the whole statement.
            out.add(new CodeRow(Kind.HEADER, depth, stmt, JavaPrinter.line(stmt), children.get(0), 0));
            if (stmt instanceof IfStmt) {
                ifArms((IfStmt) stmt, depth, cursor, out);
            } else if (stmt instanceof SwitchStmt) {
                switchArms((SwitchStmt) stmt, depth, cursor, out);
            } else if (stmt instanceof LockStmt guard) {
                out.add(new CodeRow(Kind.MID, depth, null, JavaPrinter.lockTryLine(), guard.body, 0));
                walk(guard.body, depth + 1, cursor, out);
                out.add(new CodeRow(Kind.MID, depth, null, JavaPrinter.lockFinallyLine(), block, i + 1));
                out.add(new CodeRow(Kind.MID, depth + 1, null, JavaPrinter.lockUnlockLine(guard), block, i + 1));
            } else if (stmt instanceof TeamStmt team) {
                walk(team.first, depth + 1, cursor, out);
                out.add(new CodeRow(Kind.MID, depth, team.secondFarmer, JavaPrinter.teamNext(team), team.second, 0));
                walk(team.second, depth + 1, cursor, out);
            } else if (stmt instanceof TogetherStmt together) {
                walk(together.first, depth + 1, cursor, out);
                out.add(new CodeRow(Kind.MID, depth, together, JavaPrinter.togetherNext(), together.second, 0));
                walk(together.second, depth + 1, cursor, out);
            } else if (stmt instanceof TryStmt) {
                tryArms((TryStmt) stmt, depth, cursor, out);
            } else {
                walk(children.get(0), depth + 1, cursor, out); // a loop body
            }
            if (stmt instanceof DoWhileStmt) {
                DoWhileStmt loop = (DoWhileStmt) stmt;
                // The trailing condition has its own debugger stop and an editable boolean chip.
                out.add(new CodeRow(Kind.MID, depth, loop.conditionLine, JavaPrinter.doWhileEnd(loop), block, i + 1));
            } else if (stmt instanceof TogetherStmt || stmt instanceof TeamStmt) {
                out.add(new CodeRow(Kind.FOOTER, depth, stmt, JavaPrinter.togetherEnd(), block, i + 1));
            } else if (stmt instanceof StreamForEachStmt) {
                out.add(new CodeRow(Kind.FOOTER, depth, stmt, JavaPrinter.streamLambdaEnd(), block, i + 1));
            } else {
                out.add(new CodeRow(Kind.FOOTER, depth, stmt, JavaPrinter.closeBrace(), block, i + 1));
            }
        }
        caretIfHere(block, block.size(), depth, cursor, out);
    }

    private static void ifArms(IfStmt stmt, int depth, EditorCursor cursor, List<CodeRow> out) {
        List<IfStmt.Branch> branches = stmt.branches();
        walk(branches.get(0).block, depth + 1, cursor, out);
        for (IfStmt.Branch branch : branches.subList(1, branches.size())) {
            // The branch is the row's node so the debugger can highlight the condition being tested.
            out.add(new CodeRow(Kind.MID, depth, branch, JavaPrinter.elseIfLine(branch), branch.block, 0));
            walk(branch.block, depth + 1, cursor, out);
        }
        if (stmt.elseBlock() != null) {
            out.add(new CodeRow(Kind.MID, depth, null, JavaPrinter.elseLine(), stmt.elseBlock(), 0));
            walk(stmt.elseBlock(), depth + 1, cursor, out);
        }
    }

    private static void switchArms(SwitchStmt stmt, int depth, EditorCursor cursor, List<CodeRow> out) {
        boolean scoped = stmt.needsScopedArms();
        for (SwitchStmt.Case c : stmt.cases()) {
            out.add(new CodeRow(Kind.MID, depth + 1, null, JavaPrinter.caseLine(c, scoped), c.body, 0));
            caseBody(c.body, !c.fallsThrough(), scoped, depth + 2, cursor, out);
        }
        if (stmt.defaultBlock() != null) {
            out.add(new CodeRow(Kind.MID, depth + 1, null, JavaPrinter.defaultLine(scoped),
                    stmt.defaultBlock(), 0));
            caseBody(stmt.defaultBlock(), true, scoped, depth + 2, cursor, out);
        }
    }

    private static void tryArms(TryStmt stmt, int depth, EditorCursor cursor, List<CodeRow> out) {
        walk(stmt.tryBlock, depth + 1, cursor, out);
        for (TryStmt.Catch arm : stmt.catches()) {
            out.add(new CodeRow(Kind.MID, depth, arm, JavaPrinter.catchLine(arm), arm.body, 0));
            walk(arm.body, depth + 1, cursor, out);
        }
        if (stmt.finallyBlock() != null) {
            out.add(new CodeRow(Kind.MID, depth, stmt.finallyBlock(), JavaPrinter.finallyLine(),
                    stmt.finallyBlock(), 0));
            walk(stmt.finallyBlock(), depth + 1, cursor, out);
        }
    }

    private static void caseBody(Block body, boolean hasBreak, boolean scoped, int depth,
                                 EditorCursor cursor, List<CodeRow> out) {
        walk(body, depth, cursor, out);
        if (hasBreak && ControlFlow.completesNormally(body)) {
            out.add(new CodeRow(Kind.MID, depth, null, JavaPrinter.breakLine(), body, body.size()));
        }
        if (scoped) {
            out.add(new CodeRow(Kind.MID, depth - 1, null, JavaPrinter.closeBrace(), body, body.size()));
        }
    }

    private static void caretIfHere(Block block, int index, int depth, EditorCursor cursor, List<CodeRow> out) {
        if (cursor != null && cursor.isAt(block, index)) {
            out.add(new CodeRow(Kind.CARET, depth, null, null, block, index));
        }
    }
}
