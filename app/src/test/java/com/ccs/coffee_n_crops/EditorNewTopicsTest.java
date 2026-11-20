package com.ccs.coffee_n_crops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.ccs.coffee_n_crops.editor.BlockSpec;
import com.ccs.coffee_n_crops.editor.CodeRow;
import com.ccs.coffee_n_crops.editor.EditorCursor;
import com.ccs.coffee_n_crops.editor.PaletteProvider;
import com.ccs.coffee_n_crops.editor.RowFlattener;
import com.ccs.coffee_n_crops.farm.Direction;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.MethodDecl;
import com.ccs.coffee_n_crops.lang.ast.expr.Binary;
import com.ccs.coffee_n_crops.lang.ast.expr.NewIntArray;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.DoWhileStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.IfStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.SwitchStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.print.JavaPrinter;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.progress.LevelCatalog;
import com.ccs.coffee_n_crops.progress.Unlock;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Editor coverage for the new topics, including the actual lesson unlocks. */
public class EditorNewTopicsTest {
    @Test
    public void doWhileFooterEditsConditionAndHasASeparateDebuggerStop() {
        Block program = new Block();
        assertTrue(labels(cursor(program, 0), levelUnlocks(25), program)
                .contains("do { } while (farmer.canMove(Direction.EAST));"));
        DoWhileStmt loop = new DoWhileStmt(Ast.canMove(Direction.EAST));
        loop.body.add(Ast.declare(JType.BOOLEAN, "bodyOnly"));
        program.add(loop);
        program.add(Ast.harvest());
        List<CodeRow> rows = RowFlattener.flatten(program, null);
        CodeRow header = rows.stream().filter(r -> r.node == loop).findFirst().orElseThrow();
        CodeRow tail = rows.stream().filter(r -> r.node == loop.conditionLine).findFirst().orElseThrow();
        assertEquals("do {", JavaPrinter.text(header.tokens));
        assertEquals("} while (farmer.canMove(Direction.EAST));", JavaPrinter.text(tail.tokens));
        assertSame(loop.body, header.cursorBlock);
        assertSame(program, tail.cursorBlock);
        assertEquals(1, tail.cursorIndex);
        assertTrue(tail.tokens.stream().anyMatch(t -> t.slot == loop.cond));
        assertTrue(tail.showsHighlight(loop.conditionLine.id));
        assertFalse(header.showsHighlight(loop.conditionLine.id));

        EditorCursor cursor = cursor(program, program.size());
        cursor.slot = loop.cond;
        assertFalse(labels(cursor, Set.of(Unlock.DO_WHILE, Unlock.BOOLEAN), program).contains("bodyOnly"));
    }

    @Test
    public void loopJumpChoicesFollowLexicalTargets() {
        Set<String> unlocks = Set.of(Unlock.BREAK, Unlock.CONTINUE);
        Block program = new Block();
        ForStmt loop = Ast.forCount("i", 3);
        IfStmt condition = Ast.ifStmt(Ast.lit(true), false);
        loop.body.add(condition);
        program.add(loop);
        assertTrue(labels(cursor(condition.thenBlock, 0), unlocks, program).containsAll(Set.of("break;", "continue;")));
        assertFalse(labels(cursor(program, program.size()), unlocks, program).contains("break;"));
        assertFalse(labels(cursor(program, program.size()), unlocks, program).contains("continue;"));

        SwitchStmt selection = Ast.switchStmt(Ast.lit(0));
        program.add(selection);
        Block arm = selection.cases().get(0).body;
        assertTrue(labels(cursor(arm, 0), unlocks, program).contains("break;"));
        assertFalse(labels(cursor(arm, 0), unlocks, program).contains("continue;"));

        MethodDecl helper = new MethodDecl("helper", JType.VOID);
        program.addMethod(helper);
        assertFalse(labels(cursor(helper.body, 0), unlocks, program).contains("break;"));
    }

    @Test
    public void jumpLessonsOfferTheMatchingGuards() {
        Block program = new Block();
        Set<String> breaks = labels(cursor(program, 0), levelUnlocks(26), program);
        assertTrue(breaks.contains("while (true) { }"));
        assertTrue(breaks.contains("if (!farmer.canMove(Direction.EAST)) { }"));
        Set<String> continues = labels(cursor(program, 0), levelUnlocks(27), program);
        assertTrue(continues.contains("if (!farmer.isRipe()) { }"));
    }

    @Test
    public void numericUpdatesRequireTheUnlockedOperatorAndAnInScopeInt() {
        Block program = new Block();
        program.add(Ast.declare(JType.INT, "steps"));
        program.add(Ast.declare(JType.BOOLEAN, "ready"));
        Set<String> increment = labels(cursor(program, 2), levelUnlocks(23), program);
        Set<String> decrement = labels(cursor(program, 2), levelUnlocks(24), program);
        assertTrue(increment.contains("steps += 1;"));
        assertFalse(increment.contains("ready += 1;"));
        assertFalse(increment.contains("steps--;"));
        assertTrue(decrement.contains("steps--;"));
        assertFalse(decrement.contains("ready--;"));
        assertFalse(labels(cursor(program, 0), levelUnlocks(23), program).contains("steps += 1;"));
    }

    @Test
    public void arrayLessonsOfferAllocationLengthIterationAndAnIntWrite() {
        Block program = new Block();
        EditorCursor cursor = cursor(program, 0);
        assertTrue(labels(cursor, levelUnlocks(28), program).contains("int[] row = new int[3];"));
        assertFalse(labels(cursor, levelUnlocks(28), program).contains("int[] row = farmer.scanRow();"));
        VarDecl row = new VarDecl(JType.INT_ARRAY, "row", new NewIntArray(Ast.lit(5)));
        program.add(row);
        cursor.moveTo(program, 1);
        assertTrue(labels(cursor, levelUnlocks(28), program).contains("for (int i = 0; i < row.length; i++) { }"));
        Set<String> writes = labels(cursor, levelUnlocks(29), program);
        assertTrue(writes.contains("row[0] = 0;"));
        assertTrue(writes.contains("for (int cell : row) { }"));
        assertFalse(labels(cursor, levelUnlocks(28), program).contains("row[0] = 0;"));

        cursor.slot = row.init;
        assertTrue(labels(cursor, levelUnlocks(28), program).contains("new int[3]"));
        assertFalse(labels(cursor, levelUnlocks(28), program).contains("farmer.scanRow()"));
    }

    @Test
    public void listLessonChoicesKeepMembershipBooleanAndMutationsScoped() {
        Block program = new Block();
        program.add(Ast.newList("list"));
        ForStmt loop = Ast.forCount("i", 8);
        IfStmt condition = Ast.ifStmt(Ast.lit(true), false);
        loop.body.add(condition);
        program.add(loop);

        EditorCursor cursor = cursor(loop.body, 0);
        assertTrue(labels(cursor, levelUnlocks(30), program).contains("if (!list.contains(i)) { }"));
        cursor.slot = condition.cond;
        assertTrue(labels(cursor, levelUnlocks(30), program).contains("list.contains(i)"));
        assertFalse(labels(cursor, levelUnlocks(31), program).contains("list.contains(i)"));
        cursor.slot = ((Binary) loop.cond.get()).right;
        assertFalse(labels(cursor, levelUnlocks(30), program).contains("list.contains(i)"));

        cursor.moveTo(program, 1);
        assertTrue(labels(cursor, levelUnlocks(31), program).contains("list.set(0, 0);"));
        assertTrue(labels(cursor, levelUnlocks(32), program).contains("list.remove(0);"));
        assertFalse(labels(cursor(program, 0), levelUnlocks(32), program).contains("list.remove(0);"));
    }

    private static Set<String> levelUnlocks(int levelNumber) {
        return LevelCatalog.get(levelNumber - 1).unlocks;
    }

    private static EditorCursor cursor(Block block, int at) {
        EditorCursor cursor = new EditorCursor();
        cursor.moveTo(block, at);
        return cursor;
    }

    private static Set<String> labels(EditorCursor cursor, Set<String> unlocks, Block program) {
        Set<String> labels = new HashSet<>();
        for (BlockSpec spec : PaletteProvider.query(cursor, unlocks, program)) labels.add(spec.label);
        return labels;
    }
}
