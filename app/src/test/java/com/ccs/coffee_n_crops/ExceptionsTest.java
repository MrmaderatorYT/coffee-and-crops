package com.ccs.coffee_n_crops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ccs.coffee_n_crops.farm.Direction;
import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.farm.WorldClock;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.AstWalk;
import com.ccs.coffee_n_crops.lang.ast.MethodDecl;
import com.ccs.coffee_n_crops.lang.ast.Node;
import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.ast.expr.Binary;
import com.ccs.coffee_n_crops.lang.ast.expr.UserCall;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.ExprStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.IfStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ReturnStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.TryStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.check.ScopeResolver;
import com.ccs.coffee_n_crops.lang.check.Validator;
import com.ccs.coffee_n_crops.lang.interp.ExecListener;
import com.ccs.coffee_n_crops.lang.interp.Interpreter;
import com.ccs.coffee_n_crops.lang.print.JavaPrinter;
import com.ccs.coffee_n_crops.lang.runtime.ExceptionTypes;
import com.ccs.coffee_n_crops.lang.serial.AstJson;
import com.ccs.coffee_n_crops.lang.types.JType;

import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Java exception propagation, finally precedence, scoped catch variables, and safe cancellation. */
public class ExceptionsTest {
    private static final int TIMEOUT_SECONDS = 5;
    private static final double TANK_CAPACITY = 1.0;

    private static final class Result implements ExecListener {
        final CountDownLatch done = new CountDownLatch(1);
        final List<Integer> entered = new ArrayList<>();
        String error;
        int errorNode = -1;

        @Override public void onNodeEnter(int farmerId, int nodeId) { entered.add(nodeId); }
        @Override public void onFinished(int farmerId) { done.countDown(); }
        @Override public void onError(int farmerId, int nodeId, String message) {
            error = message;
            errorNode = nodeId;
            done.countDown();
        }
    }

    private static Block program(Stmt... statements) {
        Block program = new Block();
        for (Stmt statement : statements) program.add(statement);
        return program;
    }

    private static Result run(Block program, FarmWorld world) throws InterruptedException {
        Result result = new Result();
        Interpreter interpreter = new Interpreter(new WorldClock(0));
        try {
            interpreter.start(program, world, result, false);
            assertTrue("program did not finish", result.done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            return result;
        } finally {
            interpreter.stop();
        }
    }

    private static FarmWorld emptyTank(String layout) {
        return new FarmWorld(new String[]{layout}, 0, 0, 0, TANK_CAPACITY);
    }

    private static void assertValid(Block program) {
        Validator.Problem problem = Validator.check(program);
        assertNull(problem == null ? null : problem.message, problem);
    }

    private static ExprStmt call(MethodDecl method) {
        return new ExprStmt(new UserCall(method));
    }

    private static IfStmt harvestIfResult(MethodDecl method, int expected) {
        IfStmt correct = Ast.ifStmt(Ast.eq(new UserCall(method), expected), false);
        correct.thenBlock.add(Ast.harvest());
        return correct;
    }

    @Test
    public void matchingCatchRefillsAndExplicitlyRetriesTheFailedWatering() throws Exception {
        TryStmt attempt = new TryStmt();
        attempt.tryBlock.add(Ast.water());
        TryStmt.Catch empty = attempt.addCatch(ExceptionTypes.EMPTY_TANK, "e");
        empty.body.add(Ast.refill());
        empty.body.add(Ast.water());
        Block program = program(attempt, Ast.move(Direction.EAST));
        FarmWorld world = emptyTank("g.");

        assertValid(program);
        assertNull(run(program, world).error);
        assertEquals(1, world.watered());
        assertEquals(1, world.refills());
        assertEquals(1, world.farmer.pose().x);
    }

    @Test
    public void catchDoesNotAutomaticallyRetryOrResumeTheRestOfTheTryBlock() throws Exception {
        TryStmt attempt = new TryStmt();
        ExprStmt skipped = Ast.move(Direction.EAST);
        attempt.tryBlock.add(Ast.water());
        attempt.tryBlock.add(skipped);
        attempt.addCatch(ExceptionTypes.EMPTY_TANK, "e").body.add(Ast.refill());
        FarmWorld world = emptyTank("g.");

        Result result = run(program(attempt), world);
        assertNull(result.error);
        assertEquals(0, world.watered());
        assertEquals(1, world.refills());
        assertEquals(0, world.farmer.pose().x);
        assertFalse(result.entered.contains(skipped.id));
    }

    @Test
    public void onlyTheFirstMatchingCatchRunsAndTheBaseTypeCoversBothFarmErrors() throws Exception {
        TryStmt specific = new TryStmt();
        specific.tryBlock.add(Ast.water());
        specific.addCatch(ExceptionTypes.EMPTY_TANK, "e").body.add(Ast.refill());
        TryStmt.Catch broad = specific.addCatch(ExceptionTypes.FARM, "e");
        broad.body.add(Ast.move(Direction.EAST));
        FarmWorld world = emptyTank("g.");
        assertValid(program(specific));
        assertNull(run(program(specific), world).error);
        assertEquals(1, world.refills());
        assertEquals(0, world.farmer.pose().x);

        for (boolean tankFailure : new boolean[]{true, false}) {
            TryStmt base = new TryStmt();
            base.tryBlock.add(tankFailure ? Ast.water() : Ast.move(Direction.WEST));
            base.addCatch(ExceptionTypes.FARM, "e").body.add(Ast.refill());
            FarmWorld baseWorld = emptyTank("g");
            assertNull(run(program(base), baseWorld).error);
            assertEquals(1, baseWorld.refills());
        }
    }

    @Test
    public void unmatchedErrorsRunFinallyAndKeepTheOriginalFailureHighlighted() throws Exception {
        TryStmt attempt = new TryStmt();
        ExprStmt outside = Ast.move(Direction.WEST);
        attempt.tryBlock.add(outside);
        attempt.addCatch(ExceptionTypes.EMPTY_TANK, "e").body.add(Ast.harvest());
        attempt.addFinally().add(Ast.refill());
        FarmWorld world = emptyTank("W");

        Result result = run(program(attempt), world);
        assertEquals("OutOfBoundsFarmException: the farmer tried to leave the farm at (-1, 0)", result.error);
        assertEquals(outside.id, result.errorNode);
        assertEquals(1, world.refills());
        assertEquals(0, world.wheatHarvested());
    }

    @Test
    public void anErrorFromACatchSkipsSiblingCatchesButStillRunsFinally() throws Exception {
        TryStmt attempt = new TryStmt();
        attempt.tryBlock.add(Ast.water());
        ExprStmt outside = Ast.move(Direction.WEST);
        attempt.addCatch(ExceptionTypes.EMPTY_TANK, "e").body.add(outside);
        attempt.addCatch(ExceptionTypes.FARM, "e").body.add(Ast.harvest());
        attempt.addFinally().add(Ast.refill());
        FarmWorld world = emptyTank("W");
        assertValid(program(attempt));

        Result result = run(program(attempt), world);
        assertEquals("OutOfBoundsFarmException: the farmer tried to leave the farm at (-1, 0)", result.error);
        assertEquals(outside.id, result.errorNode);
        assertEquals(0, world.wheatHarvested());
        assertEquals(1, world.refills());
    }

    @Test
    public void finallyRunsAfterNormalCompletionAndAfterAHandledFailure() throws Exception {
        for (boolean throwFirst : new boolean[]{false, true}) {
            TryStmt attempt = new TryStmt();
            if (throwFirst) attempt.tryBlock.add(Ast.move(Direction.WEST));
            else attempt.tryBlock.add(Ast.refill());
            attempt.addCatch(ExceptionTypes.OUT_OF_BOUNDS, "e").body.add(Ast.refill());
            attempt.addFinally().add(Ast.water());
            FarmWorld world = emptyTank("g");

            assertNull(run(program(attempt), world).error);
            assertEquals(1, world.refills());
            assertEquals(1, world.watered());
        }
    }

    @Test
    public void aReturnUnwindsTheTryAndRunsFinallyBeforeReturningToTheCaller() throws Exception {
        MethodDecl method = new MethodDecl("first", JType.VOID);
        TryStmt attempt = new TryStmt();
        attempt.tryBlock.add(new ReturnStmt());
        attempt.addCatch(ExceptionTypes.FARM, "e").body.add(Ast.move(Direction.WEST));
        attempt.addFinally().add(Ast.harvest());
        method.body.add(attempt);
        Block program = program(call(method), Ast.move(Direction.EAST));
        program.addMethod(method);
        FarmWorld world = new FarmWorld(new String[]{"W."}, 0, 0);

        assertValid(program);
        assertNull(run(program, world).error);
        assertEquals(1, world.wheatHarvested());
        assertEquals(1, world.farmer.pose().x);
    }

    @Test
    public void returnExpressionIsEvaluatedBeforeFinallyChangesItsLocalVariable() throws Exception {
        MethodDecl method = new MethodDecl("value", JType.INT);
        VarDecl count = new VarDecl(JType.INT, "count", Ast.lit(1));
        method.body.add(count);
        TryStmt attempt = new TryStmt();
        attempt.tryBlock.add(new ReturnStmt(JType.INT, new VarRef("count")));
        attempt.addFinally().add(Ast.assign(count, Ast.lit(2)));
        method.body.add(attempt);
        Block program = program(harvestIfResult(method, 1));
        program.addMethod(method);
        FarmWorld world = new FarmWorld(new String[]{"W"}, 0, 0);

        assertValid(program);
        assertNull(run(program, world).error);
        assertEquals(1, world.wheatHarvested());
        assertCompiles(JavaPrinter.toSource(program));
    }

    @Test
    public void aFinallyReturnOverridesBothAPendingReturnAndAnUncaughtError() throws Exception {
        for (boolean throwFirst : new boolean[]{false, true}) {
            MethodDecl method = new MethodDecl("value", JType.INT);
            TryStmt attempt = new TryStmt();
            attempt.tryBlock.add(throwFirst ? Ast.move(Direction.WEST) : new ReturnStmt(JType.INT, Ast.lit(1)));
            attempt.addFinally().add(new ReturnStmt(JType.INT, Ast.lit(2)));
            method.body.add(attempt);
            Block program = program(harvestIfResult(method, 2));
            program.addMethod(method);
            FarmWorld world = new FarmWorld(new String[]{"W"}, 0, 0);

            assertValid(program);
            assertNull(run(program, world).error);
            assertEquals(1, world.wheatHarvested());
            assertCompiles(JavaPrinter.toSource(program));
        }
    }

    @Test
    public void anErrorInFinallyOverridesTheOriginalErrorAndHighlightsTheCleanupFailure() throws Exception {
        TryStmt attempt = new TryStmt();
        attempt.tryBlock.add(Ast.move(Direction.WEST));
        ExprStmt failedCleanup = Ast.water();
        attempt.addFinally().add(failedCleanup);
        FarmWorld world = emptyTank("g");

        Result result = run(program(attempt), world);
        assertEquals("EmptyTankException: the tank holds 0.0 L but watering needs 0.5 L", result.error);
        assertEquals(failedCleanup.id, result.errorNode);
        assertEquals(0, world.watered());
    }

    @Test
    public void nestedFinallyFinishesBeforeTheOuterCatchAndOuterCleanup() throws Exception {
        TryStmt inner = new TryStmt();
        inner.tryBlock.add(Ast.move(Direction.WEST));
        ExprStmt innerCleanup = Ast.move(Direction.EAST);
        inner.addFinally().add(innerCleanup);
        TryStmt outer = new TryStmt();
        outer.tryBlock.add(inner);
        ExprStmt recover = Ast.water();
        outer.addCatch(ExceptionTypes.OUT_OF_BOUNDS, "e").body.add(recover);
        ExprStmt outerCleanup = Ast.move(Direction.EAST);
        outer.addFinally().add(outerCleanup);
        Block program = program(outer, Ast.harvest());
        FarmWorld world = new FarmWorld(new String[]{".gW"}, 0, 0);

        assertValid(program);
        Result result = run(program, world);
        assertNull(result.error);
        assertEquals(1, world.watered());
        assertEquals(1, world.wheatHarvested());
        assertEquals(2, world.farmer.pose().x);
        assertTrue(result.entered.indexOf(innerCleanup.id) < result.entered.indexOf(recover.id));
        assertTrue(result.entered.indexOf(recover.id) < result.entered.indexOf(outerCleanup.id));
    }

    @Test
    public void callersCanCatchFarmErrorsThrownDeepInsideHelperCalls() throws Exception {
        MethodDecl inner = new MethodDecl("inner", JType.VOID);
        inner.body.add(Ast.move(Direction.WEST));
        MethodDecl outer = new MethodDecl("outer", JType.VOID);
        outer.body.add(call(inner));
        TryStmt attempt = new TryStmt();
        attempt.tryBlock.add(call(outer));
        attempt.addCatch(ExceptionTypes.OUT_OF_BOUNDS, "e").body.add(Ast.harvest());
        Block program = program(attempt);
        program.addMethod(inner);
        program.addMethod(outer);
        FarmWorld world = new FarmWorld(new String[]{"W"}, 0, 0);

        assertValid(program);
        assertNull(run(program, world).error);
        assertEquals(1, world.wheatHarvested());
    }

    @Test
    public void farmCatchesDoNotSwallowArithmeticErrorsButTheirFinallyStillRuns() throws Exception {
        TryStmt attempt = new TryStmt();
        VarDecl divide = new VarDecl(JType.INT, "count", new Binary(Binary.Op.DIV, Ast.lit(1), Ast.lit(0)));
        attempt.tryBlock.add(divide);
        attempt.addCatch(ExceptionTypes.FARM, "e").body.add(Ast.harvest());
        attempt.addFinally().add(Ast.refill());
        FarmWorld world = emptyTank("W");

        Result result = run(program(attempt), world);
        assertEquals("ArithmeticException: / by zero", result.error);
        assertEquals(divide.id, result.errorNode);
        assertEquals(1, world.refills());
        assertEquals(0, world.wheatHarvested());
    }

    @Test
    public void catchParametersAndTryLocalsStayInsideTheirOwnArms() {
        TryStmt attempt = new TryStmt();
        attempt.tryBlock.add(Ast.declare(JType.INT, "insideTry"));
        TryStmt.Catch arm = attempt.addCatch(ExceptionTypes.EMPTY_TANK, "e");
        arm.body.add(Ast.declare(JType.INT, "insideCatch"));
        Block cleanup = attempt.addFinally();
        Block program = program(Ast.declare(JType.INT, "outside"), attempt);

        assertValid(program);
        assertTrue(ScopeResolver.visibleAt(arm.body, 0).contains(arm.variable));
        assertFalse(ScopeResolver.visibleAt(attempt.tryBlock, 0).contains(arm.variable));
        assertFalse(ScopeResolver.visibleAt(cleanup, 0).contains(arm.variable));
        assertFalse(ScopeResolver.visibleAt(program, program.size()).contains(arm.variable));
        assertFalse(hasName(ScopeResolver.visibleAt(arm.body, 0), "insideTry"));
        assertFalse(hasName(ScopeResolver.visibleAt(cleanup, 0), "insideTry"));
        assertTrue(hasName(ScopeResolver.visibleAt(arm.body, 0), "outside"));

        cleanup.add(new VarDecl(JType.INT, "escaped", new VarRef("insideCatch")));
        assertEquals("cannot find symbol: variable insideCatch", Validator.check(program).message);
    }

    @Test
    public void catchNamesCannotRedeclareOuterLocalsButMayBeReusedAcrossSiblingArms() throws Exception {
        TryStmt attempt = new TryStmt();
        attempt.addCatch(ExceptionTypes.EMPTY_TANK, "e");
        attempt.addCatch(ExceptionTypes.OUT_OF_BOUNDS, "e");
        Block valid = program(attempt, Ast.declare(JType.INT, "e"));
        assertValid(valid);
        assertCompiles(JavaPrinter.toSource(valid));

        Block collision = program(Ast.declare(JType.INT, "e"), attempt);
        Validator.Problem problem = Validator.check(collision);
        assertNotNull(problem);
        assertTrue(problem.message.contains("variable e is already defined"));
    }

    @Test
    public void validatorRejectsUnknownDuplicateAndAlreadyCoveredCatchTypes() {
        TryStmt unknown = new TryStmt();
        unknown.addCatch("MissingException", "e");
        Validator.Problem missing = Validator.check(program(unknown));
        assertNotNull(missing);
        assertTrue(missing.message.contains("cannot find symbol"));
        assertTrue(missing.message.contains("MissingException"));

        TryStmt duplicate = new TryStmt();
        duplicate.addCatch(ExceptionTypes.EMPTY_TANK, "e");
        duplicate.addCatch(ExceptionTypes.EMPTY_TANK, "other");
        assertEquals("exception EmptyTankException has already been caught", Validator.check(program(duplicate)).message);

        TryStmt covered = new TryStmt();
        covered.addCatch(ExceptionTypes.FARM, "e");
        covered.addCatch(ExceptionTypes.OUT_OF_BOUNDS, "other");
        assertEquals("exception OutOfBoundsFarmException has already been caught", Validator.check(program(covered)).message);

        TryStmt valid = new TryStmt();
        valid.addCatch(ExceptionTypes.EMPTY_TANK, "e");
        valid.addCatch(ExceptionTypes.OUT_OF_BOUNDS, "e");
        valid.addCatch(ExceptionTypes.FARM, "e");
        assertValid(program(valid));
    }

    @Test
    public void validatorChecksTryStructureAndReturnPathsThroughFinally() throws Exception {
        Validator.Problem bare = Validator.check(program(new TryStmt()));
        assertNotNull(bare);
        assertTrue(bare.message.contains("catch") && bare.message.contains("finally"));

        MethodDecl method = new MethodDecl("value", JType.INT);
        TryStmt attempt = new TryStmt();
        attempt.tryBlock.add(new ReturnStmt(JType.INT, Ast.lit(1)));
        TryStmt.Catch arm = attempt.addCatch(ExceptionTypes.FARM, "e");
        method.body.add(attempt);
        Block program = new Block();
        program.addMethod(method);
        assertEquals("missing return statement", Validator.check(program).message);

        arm.body.add(new ReturnStmt(JType.INT, Ast.lit(2)));
        assertValid(program);
        assertCompiles(JavaPrinter.toSource(program));
        ExprStmt unreachable = Ast.harvest();
        method.body.add(unreachable);
        assertEquals("unreachable statement", Validator.check(program).message);
        assertEquals(unreachable, Validator.check(program).statement);
        method.body.remove(method.body.size() - 1);

        attempt.addFinally().add(new ReturnStmt(JType.INT, Ast.lit(3)));
        assertValid(program);
        assertCompiles(JavaPrinter.toSource(program));
    }

    @Test
    public void nestedArmsPrintAsJavaAndSurviveJsonWhileOldProgramsRemainLoadable() throws Exception {
        TryStmt attempt = new TryStmt();
        attempt.tryBlock.add(Ast.water());
        TryStmt.Catch empty = attempt.addCatch(ExceptionTypes.EMPTY_TANK, "e");
        empty.body.add(Ast.refill());
        TryStmt retry = new TryStmt();
        retry.tryBlock.add(Ast.water());
        retry.addCatch(ExceptionTypes.FARM, "retryError");
        empty.body.add(retry);
        attempt.addCatch(ExceptionTypes.OUT_OF_BOUNDS, "e");
        attempt.addFinally().add(Ast.walkTo(Ast.lit(0), Ast.lit(0)));
        Block original = program(attempt);
        String source = JavaPrinter.toSource(original);
        assertTrue(source.contains("} catch (EmptyTankException e) {"));
        assertTrue(source.contains("} finally {"));
        assertCompiles(source);
        Block loaded = AstJson.blockFrom(new JSONObject(AstJson.toJson(original).toString()));
        assertEquals(source, JavaPrinter.toSource(loaded));
        assertValid(loaded);
        FarmWorld world = emptyTank("g");
        assertNull(run(loaded, world).error);
        assertEquals(1, world.watered());
        assertEquals(1, world.refills());

        List<Node> visited = new ArrayList<>();
        AstWalk.walk(original, visited::add);
        assertTrue(visited.contains(empty));
        assertTrue(visited.contains(empty.variable));
        assertTrue(visited.contains(retry));
        assertTrue(visited.contains(attempt.finallyBlock().get(0)));

        Block legacy = program(Ast.harvest());
        JSONObject saved = AstJson.toJson(legacy);
        saved.remove("methods");
        assertEquals(JavaPrinter.toSource(legacy), JavaPrinter.toSource(AstJson.blockFrom(saved)));
    }

    @Test
    public void steppingShowsTheChosenCatchAndFinallyInExecutionOrder() throws Exception {
        TryStmt attempt = new TryStmt();
        ExprStmt water = Ast.water();
        attempt.tryBlock.add(water);
        TryStmt.Catch empty = attempt.addCatch(ExceptionTypes.EMPTY_TANK, "e");
        ExprStmt refill = Ast.refill();
        empty.body.add(refill);
        ExprStmt home = Ast.walkTo(Ast.lit(0), Ast.lit(0));
        attempt.addFinally().add(home);
        FarmWorld world = emptyTank("g");
        BlockingQueue<Integer> entered = new LinkedBlockingQueue<>();
        Result result = new Result();
        Interpreter interpreter = new Interpreter(new WorldClock(0));
        try {
            interpreter.start(program(attempt), world, new ExecListener() {
                @Override public void onNodeEnter(int farmerId, int nodeId) { entered.add(nodeId); }
                @Override public void onFinished(int farmerId) { result.onFinished(farmerId); }
                @Override public void onError(int farmerId, int nodeId, String message) {
                    result.onError(farmerId, nodeId, message);
                }
            }, true);
            assertEquals(Integer.valueOf(attempt.id), entered.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            interpreter.debug.step();
            assertEquals(Integer.valueOf(water.id), entered.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            interpreter.debug.step();
            assertEquals(Integer.valueOf(empty.id), entered.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertEquals(0, world.refills());
            interpreter.debug.step();
            assertEquals(Integer.valueOf(refill.id), entered.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            interpreter.debug.step();
            assertEquals(Integer.valueOf(attempt.finallyBlock().id), entered.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertEquals(1, world.refills());
            interpreter.debug.step();
            assertEquals(Integer.valueOf(home.id), entered.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            interpreter.debug.step();
            assertTrue(result.done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertNull(result.error);
        } finally {
            interpreter.stop();
        }
    }

    @Test
    public void cancelingAProtectedRunDoesNotExecuteItsCatchOrFinallyOnTheResetFarm() throws Exception {
        TryStmt attempt = new TryStmt();
        ExprStmt inside = Ast.water();
        attempt.tryBlock.add(inside);
        attempt.addCatch(ExceptionTypes.FARM, "e").body.add(Ast.refill());
        ExprStmt cleanup = Ast.harvest();
        attempt.addFinally().add(cleanup);
        FarmWorld world = emptyTank("W");
        CountDownLatch blocked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread[] worker = new Thread[1];
        AtomicInteger staleCompletions = new AtomicInteger();
        AtomicInteger cleanupVisits = new AtomicInteger();
        Interpreter interpreter = new Interpreter(new WorldClock(0));
        try {
            interpreter.start(program(attempt), world, new ExecListener() {
                @Override public void onNodeEnter(int farmerId, int nodeId) {
                    if (nodeId == cleanup.id) cleanupVisits.incrementAndGet();
                    if (nodeId != inside.id) return;
                    worker[0] = Thread.currentThread();
                    blocked.countDown();
                    boolean released = false;
                    while (!released) {
                        try {
                            release.await();
                            released = true;
                        } catch (InterruptedException ignored) {
                            // A slow listener may clear the interrupt; cancellation must still win.
                        }
                    }
                }
                @Override public void onFinished(int farmerId) { staleCompletions.incrementAndGet(); }
                @Override public void onError(int farmerId, int nodeId, String message) {
                    staleCompletions.incrementAndGet();
                }
            }, false);
            assertTrue(blocked.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            interpreter.stop();
            world.reset();
            release.countDown();
            worker[0].join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
            assertFalse("canceled worker did not unwind", worker[0].isAlive());
            assertEquals(0, world.wheatHarvested());
            assertEquals(0, world.refills());
            assertEquals(0, cleanupVisits.get());
            assertEquals(0, staleCompletions.get());
            assertNull(run(program(Ast.harvest()), world).error);
            assertEquals(1, world.wheatHarvested());
        } finally {
            release.countDown();
            interpreter.stop();
        }
    }

    private static boolean hasName(List<VarDecl> declarations, String name) {
        for (VarDecl declaration : declarations) if (declaration.name.equals(name)) return true;
        return false;
    }

    /** Only the development test invokes javac; the Android app interprets its AST offline. */
    private static void assertCompiles(String methods) throws IOException, InterruptedException {
        String source = "class PrintedExceptions {\n"
                + "enum Direction { NORTH, EAST, SOUTH, WEST }\n"
                + "static class FarmException extends RuntimeException {}\n"
                + "static class EmptyTankException extends FarmException {}\n"
                + "static class OutOfBoundsFarmException extends FarmException {}\n"
                + "static class Farmhand { void harvest() {} void water() {} void refill() {} "
                + "void move(Direction d) {} void walkTo(int x, int y) {} }\n"
                + methods + "}\n";
        Path directory = Files.createTempDirectory("coffee-crops-exceptions-");
        Process compiler = null;
        try {
            Path input = directory.resolve("PrintedExceptions.java");
            Files.write(input, source.getBytes(StandardCharsets.UTF_8));
            String javac = new File(System.getProperty("java.home"), "bin/javac").getAbsolutePath();
            compiler = new ProcessBuilder(javac, "-proc:none", "--release", "17", input.toString())
                    .redirectErrorStream(true).start();
            assertTrue("javac did not finish", compiler.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            String diagnostics = new String(compiler.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(diagnostics + "\n" + source, 0, compiler.exitValue());
        } finally {
            if (compiler != null) compiler.destroyForcibly();
            File[] generated = directory.toFile().listFiles();
            if (generated != null) {
                for (File file : generated) Files.deleteIfExists(file.toPath());
            }
            Files.deleteIfExists(directory);
        }
    }
}
