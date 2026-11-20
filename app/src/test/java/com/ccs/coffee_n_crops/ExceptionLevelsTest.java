package com.ccs.coffee_n_crops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ccs.coffee_n_crops.farm.Direction;
import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.farm.WorldClock;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.MethodDecl;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ReturnStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.TryStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.WhileStmt;
import com.ccs.coffee_n_crops.lang.check.Validator;
import com.ccs.coffee_n_crops.lang.interp.ExecListener;
import com.ccs.coffee_n_crops.lang.interp.Interpreter;
import com.ccs.coffee_n_crops.lang.print.JavaPrinter;
import com.ccs.coffee_n_crops.lang.runtime.ExceptionTypes;
import com.ccs.coffee_n_crops.lang.serial.AstJson;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.progress.LevelCatalog;
import com.ccs.coffee_n_crops.progress.LevelDef;

import org.json.JSONObject;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Reference solutions and lesson evidence for the exceptions chapter. */
public class ExceptionLevelsTest {
    private static final int FIRST_EXCEPTION_LEVEL = 15;
    private static final int EXCEPTION_LEVEL_COUNT = 3;
    private static final int SPROUT_COUNT = 7;
    private static final int EXPECTED_REFILLS = 4;

    private static Block recoverTank(boolean retry) {
        Block program = new Block();
        ForStmt loop = Ast.forCount("i", SPROUT_COUNT);
        TryStmt attempt = new TryStmt();
        attempt.tryBlock.add(Ast.water());
        TryStmt.Catch recovery = attempt.addCatch(ExceptionTypes.EMPTY_TANK, "e");
        recovery.body.add(Ast.refill());
        if (retry) recovery.body.add(Ast.water());
        loop.body.add(attempt);
        loop.body.add(Ast.move(Direction.EAST));
        program.add(loop);
        return program;
    }

    private static Block edge(boolean cleanup) {
        Block program = new Block();
        TryStmt attempt = new TryStmt();
        WhileStmt loop = new WhileStmt(Ast.lit(true));
        loop.body.add(Ast.harvest());
        loop.body.add(Ast.move(Direction.EAST));
        attempt.tryBlock.add(loop);
        TryStmt.Catch caught = attempt.addCatch(ExceptionTypes.OUT_OF_BOUNDS, "e");
        if (cleanup) {
            caught.body.add(new ReturnStmt());
            attempt.addFinally().add(Ast.walkTo(Ast.lit(0), Ast.lit(0)));
        }
        program.add(attempt);
        return program;
    }

    private static String run(Block program, FarmWorld world) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        String[] error = new String[1];
        Interpreter interpreter = new Interpreter(new WorldClock(0));
        interpreter.start(program, world, new ExecListener() {
            @Override public void onNodeEnter(int farmerId, int nodeId) { }
            @Override public void onFinished(int farmerId) { done.countDown(); }
            @Override public void onError(int farmerId, int nodeId, String message) {
                error[0] = message;
                done.countDown();
            }
        }, false);
        try {
            assertTrue("program did not finish", done.await(5, TimeUnit.SECONDS));
            return error[0];
        } finally {
            interpreter.stop();
        }
    }

    @Test
    public void savedReferenceSolutionsCompleteAllThreeLessons() throws Exception {
        Block[] solutions = {recoverTank(true), edge(false), edge(true)};
        for (int i = 0; i < solutions.length; i++) {
            LevelDef level = LevelCatalog.get(FIRST_EXCEPTION_LEVEL + i);
            Block loaded = AstJson.blockFrom(new JSONObject(AstJson.toJson(solutions[i]).toString()));
            assertEquals(JavaPrinter.toSource(solutions[i]), JavaPrinter.toSource(loaded));
            Validator.Problem problem = Validator.check(loaded);
            assertNull(problem == null ? null : problem.message, problem);
            FarmWorld world = level.createWorld();
            assertNull(run(loaded, world));
            assertEquals(level.id, 0, world.ripeRemaining());
            assertEquals(level.id, 0, world.dryRemaining());
            assertEquals(level.id, 0, world.ruined());
            assertNull(level.id, level.check(world, loaded));
            if (i == 0) {
                assertEquals(EXPECTED_REFILLS, world.refills());
                assertEquals(EXPECTED_REFILLS, world.caughtExceptions(ExceptionTypes.EMPTY_TANK));
            } else {
                assertEquals(1, world.caughtExceptions(ExceptionTypes.OUT_OF_BOUNDS));
                assertEquals(i == 2 ? 0 : world.width - 1, world.farmer.pose().x);
                assertEquals(i == 2 ? 1 : 0, world.finallyRuns());
            }
        }
    }

    @Test
    public void forgettingToRetryWateringLeavesDrySprouts() throws Exception {
        LevelDef level = LevelCatalog.get(FIRST_EXCEPTION_LEVEL);
        Block program = recoverTank(false);
        FarmWorld world = level.createWorld();
        assertNull(Validator.check(program));
        assertNull(run(program, world));
        assertTrue(world.caughtExceptions(ExceptionTypes.EMPTY_TANK) > 0);
        assertTrue(world.dryRemaining() > 0);
        assertNotNull(level.check(world, program));
    }

    @Test
    public void refillingBeforeTryCannotReplaceLearningToCatch() throws Exception {
        LevelDef level = LevelCatalog.get(FIRST_EXCEPTION_LEVEL);
        Block program = recoverTank(true);
        ((ForStmt) program.get(0)).body.add(0, Ast.refill());
        FarmWorld world = level.createWorld();
        assertNull(run(program, world));
        assertEquals(0, world.dryRemaining());
        assertEquals(0, world.caughtExceptions(ExceptionTypes.EMPTY_TANK));
        assertNotNull(level.check(world, program));
    }

    @Test
    public void straightHarvestingAndAnUnusedExampleCannotPassTheEdgeLesson() throws Exception {
        LevelDef level = LevelCatalog.get(FIRST_EXCEPTION_LEVEL + 1);
        FarmWorld world = level.createWorld();
        Block program = new Block();
        ForStmt rows = Ast.forCount("i", world.width);
        rows.body.add(Ast.walkTo(new VarRef("i"), Ast.lit(0)));
        rows.body.add(Ast.harvest());
        program.add(rows);
        MethodDecl unused = new MethodDecl("unused", JType.VOID);
        unused.body.add(edge(false).remove(0));
        program.addMethod(unused);
        assertNull(run(program, world));
        assertEquals(0, world.ripeRemaining());
        assertEquals(world.width - 1, world.farmer.pose().x);
        assertEquals(0, world.caughtExceptions(ExceptionTypes.OUT_OF_BOUNDS));
        assertNotNull(level.check(world, program));
    }

    @Test
    public void returnFromCatchStillNeedsCleanupToBringTheDroneHome() throws Exception {
        LevelDef level = LevelCatalog.get(FIRST_EXCEPTION_LEVEL + 2);
        Block program = edge(true);
        ((TryStmt) program.get(0)).removeFinally();
        FarmWorld world = level.createWorld();
        assertNull(run(program, world));
        assertEquals(0, world.ripeRemaining());
        assertEquals(world.width - 1, world.farmer.pose().x);
        assertEquals(0, world.finallyRuns());
        assertNotNull(level.check(world, program));
    }

    @Test
    public void baseFarmExceptionCanRecoverAndRunTheFinallyLesson() throws Exception {
        LevelDef level = LevelCatalog.get(FIRST_EXCEPTION_LEVEL + 2);
        Block program = edge(true);
        TryStmt attempt = (TryStmt) program.get(0);
        attempt.removeCatch(attempt.catches().get(0));
        attempt.addCatch(ExceptionTypes.FARM, "e").body.add(new ReturnStmt());
        assertNull(Validator.check(program));
        FarmWorld world = level.createWorld();
        assertNull(run(program, world));
        assertEquals(1, world.caughtExceptions(ExceptionTypes.FARM));
        assertNull(level.check(world, program));
    }

    @Test
    public void resetClearsExceptionEvidenceAndAllowsTheSameSavedProgramAgain() throws Exception {
        LevelDef level = LevelCatalog.get(FIRST_EXCEPTION_LEVEL + 2);
        Block program = edge(true);
        FarmWorld world = level.createWorld();
        assertNull(run(program, world));
        assertEquals(1, world.caughtExceptions(ExceptionTypes.OUT_OF_BOUNDS));
        assertEquals(1, world.finallyRuns());
        world.reset();
        assertEquals(0, world.caughtExceptions(ExceptionTypes.OUT_OF_BOUNDS));
        assertEquals(0, world.finallyRuns());
        assertNotNull(level.check(world, program));
        assertNull(run(program, world));
        assertEquals(1, world.caughtExceptions(ExceptionTypes.OUT_OF_BOUNDS));
        assertEquals(1, world.finallyRuns());
        assertNull(level.check(world, program));
    }
}
