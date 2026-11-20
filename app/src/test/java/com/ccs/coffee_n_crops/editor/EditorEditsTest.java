package com.ccs.coffee_n_crops.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.Node;
import com.ccs.coffee_n_crops.lang.ast.expr.Literal;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.Assign;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.check.Validator;
import com.ccs.coffee_n_crops.lang.types.JType;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class EditorEditsTest {
    @Test public void undoAndRedoRetainNestedCursorAndValue() throws Exception {
        EditorHistory history = new EditorHistory();
        EditorCursor cursor = new EditorCursor();
        Block program = new Block();
        ForStmt loop = Ast.forCount("i", 3);
        program.add(loop);
        VarDecl steps = new VarDecl(JType.INT, "steps", Ast.lit(1));
        loop.body.add(steps);
        cursor.moveTo(loop.body, 1);
        cursor.slot = steps.init;
        EditorHistory.Snapshot before = EditorHistory.capture(program, null, cursor, "Farm", Collections.emptySet());
        history.record(before, program);
        assertFalse(history.canUndo());

        steps.init.set(Ast.lit(2));
        history.record(before, program);
        EditorHistory.Snapshot current = EditorHistory.capture(program, null, cursor, "Farm", Collections.emptySet());
        EditorHistory.Snapshot previous = history.take(false, current);
        Block restored = previous.program();
        Block inner = ((ForStmt) restored.get(0)).body;
        List<Node> nodes = EditorHistory.nodes(restored);
        assertSame(inner, EditorHistory.nodeAt(nodes, previous.block()));
        assertEquals(1, previous.index());
        assertSame(((VarDecl) inner.get(0)).init.get(), EditorHistory.nodeAt(nodes, previous.expression()));
        assertEquals(1, ((Literal) EditorHistory.nodeAt(nodes, previous.expression())).value);

        Block repeated = history.take(true, previous).program();
        assertEquals(2, ((Literal) ((VarDecl) ((ForStmt) repeated.get(0)).body.get(0)).init.get()).value);
    }

    @Test public void copyCreatesIndependentBlocksAndUniqueLocalNames() throws Exception {
        Block program = new Block();
        VarDecl steps = new VarDecl(JType.INT, "steps", Ast.lit(1));
        program.add(steps);
        ForStmt loop = Ast.forCount("i", 3);
        loop.body.add(Ast.harvest());
        program.add(loop);
        assertNull(StatementEdits.copy(program, program, 0));
        VarDecl copy = (VarDecl) program.get(1);
        assertEquals("steps2", copy.name);
        assertNotSame(steps.init.get(), copy.init.get());
        assertNull(StatementEdits.copy(program, program, 2));
        ForStmt copiedLoop = (ForStmt) program.get(3);
        assertNotSame(loop.body, copiedLoop.body);
        assertSame(copiedLoop.body, copiedLoop.body.get(0).parent);
        assertNull(Validator.check(program));
    }

    @Test public void moveKeepsOrderWhenItWouldHideALocal() {
        Block program = new Block();
        VarDecl steps = Ast.declare(JType.INT, "steps");
        Assign use = Ast.assign(steps, Ast.lit(2));
        program.add(steps);
        program.add(use);
        program.add(Ast.harvest());
        assertTrue(StatementEdits.move(program, program, 1, 0).contains("cannot find symbol"));
        assertSame(steps, program.get(0));
        assertSame(use, program.get(1));
        assertNull(StatementEdits.move(program, program, 1, 2));
        assertSame(use, program.get(2));
    }

    @Test public void renameStaysInScopeAndDoesNotChangeQuotedText() throws Exception {
        Block program = new Block();
        VarDecl steps = Ast.declare(JType.INT, "steps");
        program.add(steps);
        ForStmt loop = Ast.forCount("i", 3);
        loop.body.add(new VarDecl(JType.INT, "count", new VarRef(steps.name)));
        loop.body.add(Ast.assign(steps, new VarRef("count")));
        program.add(loop);
        program.add(new VarDecl(JType.STRING, "label", Ast.lit("steps")));
        assertFalse(LocalRename.apply(program, program, 0, "count"));
        assertSame(steps, program.get(0));
        assertTrue(LocalRename.apply(program, program, 0, "rowLength"));
        assertEquals("rowLength", ((VarDecl) program.get(0)).name);
        Block body = ((ForStmt) program.get(1)).body;
        assertEquals("rowLength", ((VarRef) ((VarDecl) body.get(0)).init.get()).name);
        assertEquals("rowLength", ((Assign) body.get(1)).name);
        assertEquals("steps", ((Literal) ((VarDecl) program.get(2)).init.get()).value);
        assertNull(Validator.check(program));
    }
}
