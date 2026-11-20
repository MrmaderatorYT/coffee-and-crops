package com.ccs.coffee_n_crops.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.TryStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.print.JavaPrinter;
import com.ccs.coffee_n_crops.lang.runtime.ExceptionTypes;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.progress.Unlock;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Exception block projection, scoped choices and structural Delete edits without Android UI. */
public class EditorExceptionsTest {
    @Test
    public void everyArmHasItsOwnHeaderCursorAndHighlight() {
        Block program = new Block();
        TryStmt stmt = new TryStmt();
        stmt.tryBlock.add(Ast.water());
        TryStmt.Catch tank = stmt.addCatch(ExceptionTypes.EMPTY_TANK, "e");
        tank.body.add(Ast.refill());
        TryStmt.Catch edge = stmt.addCatch(ExceptionTypes.OUT_OF_BOUNDS, "e");
        Block cleanup = stmt.addFinally();
        cleanup.add(Ast.walkTo(Ast.lit(0), Ast.lit(0)));
        program.add(stmt);
        EditorCursor cursor = cursor(edge.body);

        List<CodeRow> rows = RowFlattener.flatten(program, cursor);
        CodeRow tankHeader = row(rows, "} catch (EmptyTankException e) {");
        assertSame(tank, tankHeader.node);
        assertSame(tank.body, tankHeader.cursorBlock);
        assertTrue(tankHeader.showsHighlight(tank.id));
        assertTrue(tankHeader.showsHighlight(tank.body.id));
        assertEquals(0, tankHeader.cursorIndex);
        CodeRow edgeHeader = row(rows, "} catch (OutOfBoundsFarmException e) {");
        assertSame(edge.body, edgeHeader.cursorBlock);
        assertTrue(rows.stream().anyMatch(r -> r.kind == CodeRow.Kind.CARET && r.cursorBlock == edge.body));
        CodeRow finallyHeader = row(rows, "} finally {");
        assertSame(cleanup, finallyHeader.node);
        assertTrue(finallyHeader.showsHighlight(cleanup.id));
        assertSame(cleanup, finallyHeader.cursorBlock);
        assertEquals(tankHeader.depth, finallyHeader.depth);
        assertTrue(rows.stream().anyMatch(r -> r.kind == CodeRow.Kind.FOOTER && r.node == stmt
                && r.cursorBlock == program && r.cursorIndex == 1));
    }

    @Test
    public void startersRespectUnlocksAndRemainInTheExceptionsTab() {
        Block program = new Block();
        EditorCursor cursor = cursor(program);
        List<BlockSpec> choices = PaletteProvider.query(cursor, keys(Unlock.TRY, Unlock.CATCH_EMPTY_TANK), program);
        assertTrue(choices.stream().anyMatch(c -> c.label.equals("try { } catch (EmptyTankException e) { }")
                && BlockSpec.TAB_EXCEPTIONS.equals(c.tab)));
        Set<String> labels = labels(choices);
        assertFalse(labels.contains("try { } catch (OutOfBoundsFarmException e) { }"));
        assertFalse(labels.contains("try { } finally { }"));
        assertFalse(labels(PaletteProvider.query(cursor, keys(Unlock.HARVEST), program)).stream()
                .anyMatch(label -> label.startsWith("try")));
        assertTrue(labels(PaletteProvider.query(cursor, keys(Unlock.TRY, Unlock.FINALLY), program))
                .contains("try { } finally { }"));
    }

    @Test
    public void armCommandsReachNestedBodiesAndHideCoveredCatches() {
        Block program = new Block();
        TryStmt stmt = new TryStmt();
        TryStmt.Catch tank = stmt.addCatch(ExceptionTypes.EMPTY_TANK, "e");
        ForStmt loop = Ast.forCount("i", 3);
        tank.body.add(loop);
        program.add(stmt);
        List<BlockSpec> choices = PaletteProvider.query(cursor(loop.body), allKeys(), program);
        assertTrue(choices.stream().anyMatch(c -> c.label.equals("+ finally { }") && c.tab == null));
        Set<String> labels = labels(choices);
        assertFalse(labels.contains("+ catch (EmptyTankException e) { }"));
        assertTrue(labels.contains("+ catch (OutOfBoundsFarmException e) { }"));
        assertTrue(labels.contains("+ catch (FarmException e) { }"));

        stmt.addCatch(ExceptionTypes.FARM, "e");
        Set<String> covered = labels(PaletteProvider.query(cursor(loop.body), allKeys(), program));
        assertFalse(covered.stream().anyMatch(label -> label.startsWith("+ catch")));
        stmt.addFinally();
        assertFalse(labels(PaletteProvider.query(cursor(loop.body), allKeys(), program)).contains("+ finally { }"));
    }

    @Test
    public void nestedCatchesAvoidOuterNamesAndMetadataDoesNotBecomeEditableData() {
        Block program = new Block();
        TryStmt outer = new TryStmt();
        TryStmt.Catch error = outer.addCatch(ExceptionTypes.FARM, "e");
        TryStmt inner = new TryStmt();
        inner.addCatch(ExceptionTypes.EMPTY_TANK, "failure");
        error.body.add(inner);
        VarDecl steps = Ast.declare(JType.INT, "steps");
        error.body.add(steps);
        program.add(outer);
        Set<String> labels = labels(PaletteProvider.query(cursor(inner.tryBlock), allKeys(), program));
        assertTrue(labels.contains("+ catch (OutOfBoundsFarmException error) { }"));
        assertTrue(labels.contains("try { } catch (EmptyTankException error) { }"));
        assertFalse(labels.stream().anyMatch(label -> label.startsWith("e =")));
        EditorCursor slot = cursor(error.body);
        slot.slot = steps.init;
        assertFalse(labels(PaletteProvider.query(slot, allKeys(), program)).contains("e"));
    }

    @Test
    public void deletingOptionalArmsKeepsTheRemainingBodiesAndCursors() {
        Block program = new Block();
        TryStmt stmt = new TryStmt();
        stmt.tryBlock.add(Ast.water());
        TryStmt.Catch tank = stmt.addCatch(ExceptionTypes.EMPTY_TANK, "e");
        TryStmt.Catch edge = stmt.addCatch(ExceptionTypes.OUT_OF_BOUNDS, "e");
        Block cleanup = stmt.addFinally();
        program.add(stmt);
        EditorCursor cursor = cursor(edge.body);
        assertTrue(TryArmEdits.stepOutOrRemove(cursor));
        assertEquals(1, stmt.catches().size());
        assertSame(tank.body, cursor.block);
        cursor.moveTo(tank.body, 0);
        assertTrue(TryArmEdits.stepOutOrRemove(cursor));
        assertTrue(stmt.catches().isEmpty());
        assertSame(stmt.tryBlock, cursor.block);
        assertEquals(1, cursor.index);
        assertSame(cleanup, stmt.finallyBlock());

        TryStmt.Catch replacement = stmt.addCatch(ExceptionTypes.FARM, "e");
        cursor.moveTo(cleanup, 0);
        assertTrue(TryArmEdits.stepOutOrRemove(cursor));
        assertSame(replacement.body, cursor.block);
        assertNull(stmt.finallyBlock());
        assertSame(stmt, program.get(0));
    }

    @Test
    public void deletingTheOnlyHandlerPreservesNonemptyCodeAndRemovesAnEmptyTry() {
        Block program = new Block();
        TryStmt stmt = new TryStmt();
        stmt.tryBlock.add(Ast.water());
        TryStmt.Catch tank = stmt.addCatch(ExceptionTypes.EMPTY_TANK, "e");
        program.add(stmt);
        EditorCursor cursor = cursor(tank.body);
        assertTrue(TryArmEdits.stepOutOrRemove(cursor));
        assertSame(program, cursor.block);
        assertEquals(1, stmt.catches().size());
        assertSame(stmt, program.get(0));
        stmt.tryBlock.remove(0);
        cursor.moveTo(tank.body, 0);
        assertTrue(TryArmEdits.stepOutOrRemove(cursor));
        assertEquals(0, program.size());
        assertSame(program, cursor.block);

        TryStmt cleanupOnly = new TryStmt();
        cleanupOnly.tryBlock.add(Ast.water());
        Block cleanup = cleanupOnly.addFinally();
        program.add(cleanupOnly);
        cursor.moveTo(cleanup, 0);
        assertTrue(TryArmEdits.stepOutOrRemove(cursor));
        assertSame(cleanup, cleanupOnly.finallyBlock());
        assertSame(program, cursor.block);
        cleanupOnly.tryBlock.remove(0);
        cursor.moveTo(cleanup, 0);
        assertTrue(TryArmEdits.stepOutOrRemove(cursor));
        assertEquals(0, program.size());
    }

    @Test
    public void emptyOptionalArmsAreRemovedBeforeTheWholeStatement() {
        Block program = new Block();
        TryStmt stmt = new TryStmt();
        TryStmt.Catch tank = stmt.addCatch(ExceptionTypes.EMPTY_TANK, "e");
        TryStmt.Catch edge = stmt.addCatch(ExceptionTypes.OUT_OF_BOUNDS, "e");
        Block cleanup = stmt.addFinally();
        program.add(stmt);
        EditorCursor cursor = cursor(edge.body);
        assertTrue(TryArmEdits.stepOutOrRemove(cursor));
        assertSame(stmt, program.get(0));
        assertEquals(1, stmt.catches().size());
        assertSame(tank.body, cursor.block);
        cursor.moveTo(cleanup, 0);
        assertTrue(TryArmEdits.stepOutOrRemove(cursor));
        assertSame(stmt, program.get(0));
        assertNull(stmt.finallyBlock());
        assertSame(tank.body, cursor.block);
    }

    private static CodeRow row(List<CodeRow> rows, String text) {
        CodeRow result = rows.stream().filter(r -> r.tokens != null && text.equals(JavaPrinter.text(r.tokens)))
                .findFirst().orElse(null);
        assertNotNull(text, result);
        return result;
    }

    private static EditorCursor cursor(Block block) {
        EditorCursor cursor = new EditorCursor();
        cursor.moveTo(block, 0);
        return cursor;
    }

    private static Set<String> keys(String... keys) {
        return new HashSet<>(Arrays.asList(keys));
    }

    private static Set<String> allKeys() {
        return keys(Unlock.TRY, Unlock.CATCH_EMPTY_TANK, Unlock.CATCH_OUT_OF_BOUNDS, Unlock.CATCH_FARM,
                Unlock.FINALLY, Unlock.MOVE, Unlock.WATER, Unlock.HARVEST, Unlock.TANK, Unlock.INT,
                Unlock.DOUBLE, Unlock.BOOLEAN, Unlock.STRING, Unlock.ASSIGN, Unlock.MATH, Unlock.LOGIC,
                Unlock.FOR, Unlock.METHODS, Unlock.RETURN, Unlock.PARAMETERS, Unlock.RECURSION);
    }

    private static Set<String> labels(List<BlockSpec> choices) {
        Set<String> labels = new HashSet<>();
        for (BlockSpec choice : choices) labels.add(choice.label);
        return labels;
    }
}
