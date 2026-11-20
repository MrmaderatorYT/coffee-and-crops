package com.ccs.coffee_n_crops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.farm.WorldClock;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.ast.expr.ListContains;
import com.ccs.coffee_n_crops.lang.ast.expr.NewIntArray;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.ArrayAssign;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForEachStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.IfStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ListAdd;
import com.ccs.coffee_n_crops.lang.ast.stmt.ListRemove;
import com.ccs.coffee_n_crops.lang.ast.stmt.ListSet;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.check.Validator;
import com.ccs.coffee_n_crops.lang.interp.ExecListener;
import com.ccs.coffee_n_crops.lang.interp.Interpreter;
import com.ccs.coffee_n_crops.lang.types.JType;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Java semantics at the edges of array and list editing. */
public class CollectionTopicsTest {
    @Test
    public void arrayStoresAreVisibleThroughAliasesAndOnLaterEnhancedForPasses() throws Exception {
        ForEachStmt loop = new ForEachStmt(JType.INT, "cell", "row");
        IfStmt ripe = Ast.ifStmt(Ast.eq(new VarRef("cell"), FarmWorld.SCAN_RIPE), false);
        ripe.thenBlock.add(Ast.harvest());
        loop.body.add(ripe);
        loop.body.add(new ArrayAssign("alias", Ast.lit(1), Ast.lit(FarmWorld.SCAN_RIPE)));

        Block program = program(array(2), new VarDecl(JType.INT_ARRAY, "alias", new VarRef("row")), loop);
        FarmWorld world = world();
        assertNull(Validator.check(program));
        assertNull(run(program, world));
        assertEquals(1, world.wheatHarvested());
    }

    @Test
    public void changingAnExistingListElementDoesNotInvalidateAnEnhancedForIterator() throws Exception {
        ForEachStmt loop = new ForEachStmt(JType.INT, "cell", "list");
        IfStmt ripe = Ast.ifStmt(Ast.eq(new VarRef("cell"), FarmWorld.SCAN_RIPE), false);
        ripe.thenBlock.add(Ast.harvest());
        loop.body.add(ripe);
        loop.body.add(new ListSet("list", Ast.lit(1), Ast.lit(FarmWorld.SCAN_RIPE)));

        Block program = program(Ast.newList("list"), listCell(0), listCell(0), loop);
        FarmWorld world = world();
        assertNull(Validator.check(program));
        assertNull(run(program, world));
        assertEquals(1, world.wheatHarvested());
    }

    @Test
    public void removingAnElementFromAnEnhancedForListFailsOnTheNextElement() throws Exception {
        ForEachStmt loop = new ForEachStmt(JType.INT, "cell", "list");
        loop.body.add(new ListRemove("list", Ast.lit(0)));
        Block program = program(Ast.newList("list"), listCell(1), listCell(2), listCell(3), loop);
        assertNull(Validator.check(program));
        String error = run(program, world());
        assertTrue(String.valueOf(error), error != null && error.startsWith("ConcurrentModificationException"));
    }

    @Test
    public void negativeLengthsAndInvalidElementIndexesReportJavaErrors() throws Exception {
        assertEquals("NegativeArraySizeException: -1", run(program(array(-1)), world()));
        assertEquals("ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 1",
                run(program(array(1), new ArrayAssign("row", Ast.lit(1), Ast.lit(8))), world()));
        assertEquals("IndexOutOfBoundsException: Index 0 out of bounds for length 0",
                run(program(Ast.newList("list"), new ListRemove("list", Ast.lit(0))), world()));
    }

    @Test
    public void validatorRejectsAnArrayWriteWithABooleanIndexAndContainsOnANonList() {
        Block invalidIndex = program(array(1), new ArrayAssign("row", Ast.lit(false), Ast.lit(0)));
        assertEquals("incompatible types: boolean cannot be converted to int",
                Validator.check(invalidIndex).message);
        Block invalidList = program(array(1), Ast.ifStmt(new ListContains("row", Ast.lit(1)), false));
        assertEquals("incompatible types: int[] cannot be converted to ArrayList<Integer>",
                Validator.check(invalidList).message);
    }

    private static VarDecl array(int length) {
        return new VarDecl(JType.INT_ARRAY, "row", new NewIntArray(Ast.lit(length)));
    }

    private static ListAdd listCell(int value) {
        return new ListAdd("list", Ast.lit(value));
    }

    private static FarmWorld world() {
        return new FarmWorld(new String[]{"W"}, 0, 0);
    }

    private static Block program(Stmt... statements) {
        Block program = new Block();
        for (Stmt statement : statements) program.add(statement);
        return program;
    }

    private static String run(Block program, FarmWorld world) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        String[] error = new String[1];
        new Interpreter(new WorldClock(0)).start(program, world, new ExecListener() {
            @Override public void onNodeEnter(int farmerId, int nodeId) { }
            @Override public void onFinished(int farmerId) { done.countDown(); }
            @Override public void onError(int farmerId, int nodeId, String message) {
                error[0] = message;
                done.countDown();
            }
        }, false);
        assertTrue("program did not finish", done.await(5, TimeUnit.SECONDS));
        return error[0];
    }
}
