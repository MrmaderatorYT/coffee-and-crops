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
import com.ccs.coffee_n_crops.lang.ast.stmt.ForStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.IfStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ReturnStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.SwitchStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.ast.stmt.WhileStmt;
import com.ccs.coffee_n_crops.lang.check.ScopeResolver;
import com.ccs.coffee_n_crops.lang.check.Validator;
import com.ccs.coffee_n_crops.lang.interp.ExecListener;
import com.ccs.coffee_n_crops.lang.interp.Interpreter;
import com.ccs.coffee_n_crops.lang.print.JavaPrinter;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** User methods, independent call frames, recursion, and debugger steps across calls. */
public class MethodsTest {
    private static final int TIMEOUT_SECONDS = 5;

    private static Block program(Stmt... statements) {
        Block program = new Block();
        for (Stmt statement : statements) program.add(statement);
        return program;
    }

    private static ExprStmt call(MethodDecl method, com.ccs.coffee_n_crops.lang.ast.Expr... args) {
        return new ExprStmt(new UserCall(method, args));
    }

    private static String run(Block program, FarmWorld world) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        String[] error = new String[1];
        Interpreter interpreter = new Interpreter(new WorldClock(0));
        try {
            interpreter.start(program, world, new ExecListener() {
                @Override public void onNodeEnter(int farmerId, int nodeId) { }
                @Override public void onFinished(int farmerId) { done.countDown(); }
                @Override public void onError(int farmerId, int nodeId, String message) {
                    error[0] = message;
                    done.countDown();
                }
            }, false);
            assertTrue("program did not finish", done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            return error[0];
        } finally {
            interpreter.stop();
        }
    }

    private static MethodDecl sumTo() {
        MethodDecl sum = new MethodDecl("sumTo", JType.INT, Ast.declare(JType.INT, "n"));
        IfStmt base = Ast.ifStmt(Ast.eq(new VarRef("n"), 0), false);
        base.thenBlock.add(new ReturnStmt(JType.INT, Ast.lit(0)));
        sum.body.add(base);
        sum.body.add(new ReturnStmt(JType.INT, new Binary(Binary.Op.ADD, new VarRef("n"),
                new UserCall(sum, new Binary(Binary.Op.SUB, new VarRef("n"), Ast.lit(1))))));
        return sum;
    }

    @Test
    public void recursiveReturnsKeepEachParameterAndCallerVariableSeparate() throws Exception {
        MethodDecl sum = sumTo();
        VarDecl n = new VarDecl(JType.INT, "n", Ast.lit(7));
        VarDecl result = new VarDecl(JType.INT, "result", new UserCall(sum, Ast.lit(4)));
        IfStmt correct = Ast.ifStmt(new Binary(Binary.Op.AND,
                Ast.eq(new VarRef("result"), 10), Ast.eq(new VarRef("n"), 7)), false);
        correct.thenBlock.add(Ast.harvest());
        Block program = program(n, result, correct);
        program.addMethod(sum);
        FarmWorld world = new FarmWorld(new String[]{"W"}, 0, 0);

        assertNull(Validator.check(program));
        assertNull(run(program, world));
        assertEquals(1, world.wheatHarvested());
    }

    @Test
    public void argumentsAreEvaluatedInTheCallerBeforeCreatingTheNewFrame() throws Exception {
        VarDecl parameter = Ast.declare(JType.INT, "count");
        MethodDecl next = new MethodDecl("next", JType.INT, parameter);
        next.body.add(Ast.assign(parameter,
                new Binary(Binary.Op.ADD, new VarRef("count"), Ast.lit(1))));
        next.body.add(new ReturnStmt(JType.INT, new VarRef("count")));
        VarDecl count = new VarDecl(JType.INT, "count", Ast.lit(3));
        VarDecl first = new VarDecl(JType.INT, "first", new UserCall(next, new VarRef("count")));
        VarDecl second = new VarDecl(JType.INT, "second", new UserCall(next, new VarRef("first")));
        IfStmt correct = Ast.ifStmt(new Binary(Binary.Op.AND,
                Ast.eq(new VarRef("count"), 3), Ast.eq(new VarRef("second"), 5)), false);
        correct.thenBlock.add(Ast.harvest());
        Block program = program(count, first, second, correct);
        program.addMethod(next);
        FarmWorld world = new FarmWorld(new String[]{"W"}, 0, 0);

        assertNull(Validator.check(program));
        assertNull(run(program, world));
        assertEquals(1, world.wheatHarvested());
    }

    @Test
    public void intArgumentsAndReturnValuesWidenToDoubleLikeJava() throws Exception {
        MethodDecl identity = new MethodDecl("identity", JType.DOUBLE, Ast.declare(JType.DOUBLE, "value"));
        identity.body.add(new ReturnStmt(JType.DOUBLE, new VarRef("value")));
        MethodDecl integerResult = new MethodDecl("integerResult", JType.DOUBLE);
        integerResult.body.add(new ReturnStmt(JType.DOUBLE, Ast.lit(2)));
        VarDecl result = new VarDecl(JType.DOUBLE, "result", new UserCall(identity, Ast.lit(2)));
        IfStmt correct = Ast.ifStmt(new Binary(Binary.Op.EQ, JType.DOUBLE,
                new VarRef("result"), new UserCall(integerResult)), false);
        correct.thenBlock.add(Ast.harvest());
        Block program = program(result, correct);
        program.addMethod(identity);
        program.addMethod(integerResult);
        FarmWorld world = new FarmWorld(new String[]{"W"}, 0, 0);

        assertNull(Validator.check(program));
        assertNull(run(program, world));
        assertEquals(1, world.wheatHarvested());
        assertCompiles(JavaPrinter.toSource(program));
    }

    @Test
    public void helpersUseTheSameDroneAndCanBeCalledRepeatedly() throws Exception {
        MethodDecl harvestStep = new MethodDecl("harvestStep", JType.VOID);
        harvestStep.body.add(Ast.harvest());
        harvestStep.body.add(Ast.move(Direction.EAST));
        Block program = program(call(harvestStep), call(harvestStep));
        program.addMethod(harvestStep);
        FarmWorld world = new FarmWorld(new String[]{"WW."}, 0, 0);

        assertNull(Validator.check(program));
        assertNull(run(program, world));
        assertEquals(2, world.wheatHarvested());
        assertEquals(2, world.farmer.pose().x);
    }

    @Test
    public void returnInsideALoopUnwindsOnlyTheCurrentMethod() throws Exception {
        MethodDecl firstOnly = new MethodDecl("firstOnly", JType.VOID);
        ForStmt loop = Ast.forCount("i", 3);
        IfStmt stop = Ast.ifStmt(Ast.eq(new VarRef("i"), 1), false);
        stop.thenBlock.add(new ReturnStmt());
        loop.body.add(stop);
        loop.body.add(Ast.harvest());
        loop.body.add(Ast.move(Direction.EAST));
        firstOnly.body.add(loop);
        Block program = program(call(firstOnly), Ast.harvest());
        program.addMethod(firstOnly);
        FarmWorld world = new FarmWorld(new String[]{"WWW."}, 0, 0);

        assertNull(Validator.check(program));
        assertNull(run(program, world));
        assertEquals("execution continues in run() after the helper returns", 2, world.wheatHarvested());
        assertEquals(1, world.farmer.pose().x);
    }

    @Test
    public void recursionDepthLimitReportsAPlayerErrorAndTheNextRunStillWorks() throws Exception {
        MethodDecl forever = new MethodDecl("forever", JType.VOID);
        forever.body.add(call(forever));
        Block recursive = program(call(forever));
        recursive.addMethod(forever);
        FarmWorld world = new FarmWorld(new String[]{"W"}, 0, 0);

        assertNull(Validator.check(recursive));
        assertEquals("StackOverflowError", run(recursive, world));
        assertNull(run(program(Ast.harvest()), world));
        assertEquals(1, world.wheatHarvested());
    }

    @Test
    public void helperScopesSeeParametersButNeverRunLocals() {
        MethodDecl helper = new MethodDecl("helper", JType.INT, Ast.declare(JType.INT, "y"));
        VarDecl local = new VarDecl(JType.INT, "count", new VarRef("y"));
        helper.body.add(local);
        helper.body.add(new ReturnStmt(JType.INT, new VarRef("count")));
        Block program = program(Ast.declare(JType.INT, "steps"), call(helper, Ast.lit(1)));
        program.addMethod(helper);

        assertEquals(names("y"), names(ScopeResolver.visibleAt(helper.body, 0)));
        assertEquals(names("y", "count"), names(ScopeResolver.visibleAt(helper.body, 1)));
        assertEquals(names("steps"), names(ScopeResolver.visibleAt(program, 1)));
        assertNull(Validator.check(program));

        local.init.set(new VarRef("steps"));
        assertEquals("cannot find symbol: variable steps", Validator.check(program).message);
        local.init.set(new VarRef("y"));
        program.add(new VarDecl(JType.INT, "escaped", new VarRef("y")));
        assertEquals("cannot find symbol: variable y", Validator.check(program).message);
    }

    @Test
    public void validatorChecksReturnTypesAndEveryPossiblePath() {
        MethodDecl method = new MethodDecl("value", JType.INT);
        Block program = new Block();
        program.addMethod(method);
        assertEquals("missing return statement", Validator.check(program).message);

        IfStmt conditional = Ast.ifStmt(Ast.lit(true), false);
        conditional.thenBlock.add(new ReturnStmt(JType.INT, Ast.lit(1)));
        method.body.add(conditional);
        assertEquals("missing return statement", Validator.check(program).message);

        conditional.addElse().add(new ReturnStmt(JType.INT, Ast.lit(2)));
        assertNull(Validator.check(program));

        conditional.elseBlock().remove(0);
        conditional.elseBlock().add(new ReturnStmt(JType.INT, Ast.lit(false)));
        assertEquals("incompatible types: boolean cannot be converted to int", Validator.check(program).message);
    }

    @Test
    public void validatorRejectsValuesFromVoidMethodsAndMissingValuesFromIntMethods() {
        MethodDecl noValue = new MethodDecl("noValue", JType.VOID);
        noValue.body.add(new ReturnStmt(JType.INT, Ast.lit(1)));
        Block program = new Block();
        program.addMethod(noValue);
        assertNotNull(Validator.check(program));
        assertTrue(Validator.check(program).message.contains("return"));

        program.removeMethod(noValue);
        MethodDecl needsValue = new MethodDecl("needsValue", JType.INT);
        needsValue.body.add(new ReturnStmt());
        program.addMethod(needsValue);
        assertNotNull(Validator.check(program));
        assertTrue(Validator.check(program).message.contains("return"));
    }

    @Test
    public void validatorRejectsWrongArgumentsAndCallsToDeletedMethods() {
        MethodDecl helper = new MethodDecl("helper", JType.VOID, Ast.declare(JType.INT, "y"));
        Block wrongType = program(call(helper, Ast.lit(true)));
        wrongType.addMethod(helper);
        assertEquals("incompatible types: boolean cannot be converted to int", Validator.check(wrongType).message);

        Block wrongCount = program(new ExprStmt(new UserCall("helper", JType.VOID, new JType[0])));
        wrongCount.addMethod(helper);
        assertNotNull(Validator.check(wrongCount));
        assertTrue(Validator.check(wrongCount).message.contains("cannot be applied"));

        Block deleted = program(call(helper, Ast.lit(0)));
        deleted.addMethod(helper);
        deleted.removeMethod(helper);
        assertNotNull(Validator.check(deleted));
        assertTrue(Validator.check(deleted).message.startsWith("cannot find symbol: method helper"));
    }

    @Test
    public void validatorRejectsUnreachableStatementsAfterConditionalReturns() {
        MethodDecl helper = new MethodDecl("helper", JType.INT);
        IfStmt bothReturn = Ast.ifStmt(Ast.lit(true), true);
        bothReturn.thenBlock.add(new ReturnStmt(JType.INT, Ast.lit(1)));
        bothReturn.elseBlock().add(new ReturnStmt(JType.INT, Ast.lit(2)));
        helper.body.add(bothReturn);
        ExprStmt unreachable = Ast.harvest();
        helper.body.add(unreachable);
        Block program = new Block();
        program.addMethod(helper);

        assertEquals("unreachable statement", Validator.check(program).message);
        assertEquals(unreachable, Validator.check(program).statement);
    }

    @Test
    public void constantFalseLoopsAreUnreachableEvenWhenTheirBodiesAreEmpty() {
        WhileStmt whileLoop = new WhileStmt(Ast.lit(false));
        Validator.Problem whileProblem = Validator.check(program(whileLoop));
        assertEquals("unreachable statement", whileProblem.message);
        assertEquals(whileLoop, whileProblem.statement);

        ForStmt forLoop = Ast.forCount("i", 1);
        forLoop.cond.set(Ast.lit(false));
        Validator.Problem forProblem = Validator.check(program(forLoop));
        assertEquals("unreachable statement", forProblem.message);
        assertEquals(forLoop, forProblem.statement);
    }

    @Test
    public void methodsAndNestedReturnsSurviveSavingAndPrintCompilableJava() throws Exception {
        MethodDecl sum = sumTo();
        IfStmt correct = Ast.ifStmt(Ast.eq(new UserCall(sum, Ast.lit(4)), 10), false);
        correct.thenBlock.add(Ast.harvest());
        Block program = program(correct);
        program.addMethod(sum);
        String source = JavaPrinter.toSource(program);
        assertTrue(source.contains("int sumTo(Farmhand farmer, int n) {"));
        assertTrue(source.contains("sumTo(farmer, n - 1)"));
        assertCompiles(source);

        Block loaded = AstJson.blockFrom(new JSONObject(AstJson.toJson(program).toString()));
        assertEquals(source, JavaPrinter.toSource(loaded));
        assertEquals(1, loaded.methods().size());
        assertNull(Validator.check(loaded));
        FarmWorld world = new FarmWorld(new String[]{"W"}, 0, 0);
        assertNull(run(loaded, world));
        assertEquals(1, world.wheatHarvested());

        List<Node> visited = new ArrayList<>();
        AstWalk.walk(loaded, visited::add);
        assertTrue("method declarations are part of the AST walk", visited.contains(loaded.methods().get(0)));
        assertTrue("the return expression is visited", visited.stream().anyMatch(n -> n instanceof UserCall));
    }

    @Test
    public void returningSwitchArmsDoNotPrintUnreachableBreaks() throws Exception {
        MethodDecl helper = new MethodDecl("value", JType.INT);
        SwitchStmt choice = new SwitchStmt(Ast.lit(7));
        choice.addCase(0).body.add(new ReturnStmt(JType.INT, Ast.lit(0)));
        choice.addDefault().add(new ReturnStmt(JType.INT, Ast.lit(1)));
        helper.body.add(choice);
        IfStmt correct = Ast.ifStmt(Ast.eq(new UserCall(helper), 1), false);
        correct.thenBlock.add(Ast.harvest());
        Block program = program(correct);
        program.addMethod(helper);

        assertNull(Validator.check(program));
        String source = JavaPrinter.toSource(program);
        assertFalse(source.contains("break;"));
        assertCompiles(source);
        FarmWorld world = new FarmWorld(new String[]{"W"}, 0, 0);
        assertNull(run(program, world));
        assertEquals(1, world.wheatHarvested());
    }

    @Test
    public void legacyProgramsWithoutAMethodsFieldRemainLoadable() throws Exception {
        Block original = program(Ast.harvest());
        JSONObject legacy = AstJson.toJson(original);
        legacy.remove("methods");
        Block loaded = AstJson.blockFrom(new JSONObject(legacy.toString()));
        assertTrue(loaded.methods().isEmpty());
        assertEquals(JavaPrinter.toSource(original), JavaPrinter.toSource(loaded));
        FarmWorld world = new FarmWorld(new String[]{"W"}, 0, 0);
        assertNull(run(loaded, world));
        assertEquals(1, world.wheatHarvested());
    }

    @Test
    public void steppingEntersTheHelperThenResumesAtTheCaller() throws Exception {
        MethodDecl helper = new MethodDecl("helper", JType.VOID);
        ExprStmt harvest = Ast.harvest();
        ReturnStmt returnStatement = new ReturnStmt();
        helper.body.add(harvest);
        helper.body.add(returnStatement);
        ExprStmt call = call(helper);
        ExprStmt afterCall = Ast.move(Direction.EAST);
        Block program = program(call, afterCall);
        program.addMethod(helper);
        FarmWorld world = new FarmWorld(new String[]{"W."}, 0, 0);
        BlockingQueue<Integer> entered = new LinkedBlockingQueue<>();
        BlockingQueue<String> errors = new LinkedBlockingQueue<>();
        CountDownLatch done = new CountDownLatch(1);
        Interpreter interpreter = new Interpreter(new WorldClock(0));
        try {
            interpreter.start(program, world, new ExecListener() {
                @Override public void onNodeEnter(int farmerId, int nodeId) { entered.add(nodeId); }
                @Override public void onFinished(int farmerId) { done.countDown(); }
                @Override public void onError(int farmerId, int nodeId, String message) {
                    errors.add(message);
                    done.countDown();
                }
            }, true);
            assertEquals(Integer.valueOf(call.id), entered.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertEquals(0, world.wheatHarvested());
            interpreter.debug.step();
            assertEquals(Integer.valueOf(harvest.id), entered.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertEquals("a paused checkpoint has not run its statement", 0, world.wheatHarvested());
            interpreter.debug.step();
            assertEquals(Integer.valueOf(returnStatement.id), entered.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertEquals(1, world.wheatHarvested());
            interpreter.debug.step();
            assertEquals(Integer.valueOf(afterCall.id), entered.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertEquals(0, world.farmer.pose().x);
            assertFalse("run() is paused after the helper returns", done.await(50, TimeUnit.MILLISECONDS));
            interpreter.debug.play();
            assertTrue(done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertTrue(errors.toString(), errors.isEmpty());
            assertEquals(1, world.farmer.pose().x);
        } finally {
            interpreter.stop();
        }
    }

    @Test
    public void anUncaughtHelperErrorHighlightsTheFailingHelperStatement() throws Exception {
        MethodDecl helper = new MethodDecl("helper", JType.VOID);
        ExprStmt outside = Ast.move(Direction.EAST);
        helper.body.add(outside);
        Block program = program(call(helper));
        program.addMethod(helper);
        CountDownLatch done = new CountDownLatch(1);
        int[] errorNode = {-1};
        String[] error = new String[1];
        Interpreter interpreter = new Interpreter(new WorldClock(0));
        try {
            interpreter.start(program, new FarmWorld(new String[]{"."}, 0, 0), new ExecListener() {
                @Override public void onNodeEnter(int farmerId, int nodeId) { }
                @Override public void onFinished(int farmerId) { done.countDown(); }
                @Override public void onError(int farmerId, int nodeId, String message) {
                    errorNode[0] = nodeId;
                    error[0] = message;
                    done.countDown();
                }
            }, false);
            assertTrue(done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertNotNull(error[0]);
            assertTrue(error[0], error[0].startsWith("OutOfBoundsFarmException"));
            assertEquals(outside.id, errorNode[0]);
        } finally {
            interpreter.stop();
        }
    }

    @Test
    public void stoppingInsidePausedRecursionAllowsAQuietFreshRun() throws Exception {
        MethodDecl recursive = new MethodDecl("recursive", JType.VOID);
        ExprStmt harvest = Ast.harvest();
        ExprStmt recurse = call(recursive);
        recursive.body.add(harvest);
        recursive.body.add(recurse);
        ExprStmt enter = call(recursive);
        Block oldProgram = program(enter);
        oldProgram.addMethod(recursive);
        FarmWorld world = new FarmWorld(new String[]{"W"}, 0, 0);
        BlockingQueue<Integer> entered = new LinkedBlockingQueue<>();
        AtomicInteger staleFinished = new AtomicInteger();
        AtomicInteger staleErrors = new AtomicInteger();
        Thread[] oldWorker = new Thread[1];
        CountDownLatch restarted = new CountDownLatch(1);
        String[] restartError = new String[1];
        Interpreter interpreter = new Interpreter(new WorldClock(0));
        try {
            interpreter.start(oldProgram, world, new ExecListener() {
                @Override public void onNodeEnter(int farmerId, int nodeId) {
                    oldWorker[0] = Thread.currentThread();
                    entered.add(nodeId);
                }
                @Override public void onFinished(int farmerId) { staleFinished.incrementAndGet(); }
                @Override public void onError(int farmerId, int nodeId, String message) { staleErrors.incrementAndGet(); }
            }, true);
            assertEquals(Integer.valueOf(enter.id), entered.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            interpreter.debug.step();
            assertEquals(Integer.valueOf(harvest.id), entered.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            interpreter.debug.step();
            assertEquals(Integer.valueOf(recurse.id), entered.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            interpreter.debug.step();
            assertEquals(Integer.valueOf(harvest.id), entered.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS));

            interpreter.stop();
            world.reset();
            interpreter.start(program(Ast.harvest()), world, completion(restarted, restartError), false);
            assertTrue(restarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            oldWorker[0].join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
            assertFalse("the canceled recursive stack unwinds", oldWorker[0].isAlive());
            assertNull(restartError[0]);
            assertEquals(1, world.wheatHarvested());
            assertEquals(0, staleFinished.get());
            assertEquals(0, staleErrors.get());
        } finally {
            interpreter.stop();
        }
    }

    @Test
    public void aCanceledActionWaitingForTheFarmCannotMutateTheResetWorld() throws Exception {
        FarmWorld world = new FarmWorld(new String[]{"W."}, 0, 0);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch restarted = new CountDownLatch(1);
        AtomicInteger staleCallbacks = new AtomicInteger();
        Thread[] oldWorker = new Thread[1];
        String[] restartError = new String[1];
        Interpreter interpreter = new Interpreter(new WorldClock(0));
        try {
            synchronized (world) {
                interpreter.start(program(Ast.move(Direction.EAST)), world, new ExecListener() {
                    @Override public void onNodeEnter(int farmerId, int nodeId) {
                        oldWorker[0] = Thread.currentThread();
                        entered.countDown();
                    }
                    @Override public void onFinished(int farmerId) { staleCallbacks.incrementAndGet(); }
                    @Override public void onError(int farmerId, int nodeId, String message) { staleCallbacks.incrementAndGet(); }
                }, false);
                assertTrue(entered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
                // The old statement has passed its checkpoint but cannot acquire the farm monitor.
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
                while (oldWorker[0].getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) {
                    Thread.yield();
                }
                assertEquals(Thread.State.BLOCKED, oldWorker[0].getState());
                interpreter.stop();
                world.reset();
                interpreter.start(program(Ast.harvest()), world, completion(restarted, restartError), false);
            }
            assertTrue(restarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            oldWorker[0].join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
            assertFalse(oldWorker[0].isAlive());
            assertNull(restartError[0]);
            assertEquals("the old move cannot run after reset", 0, world.farmer.pose().x);
            assertEquals(1, world.wheatHarvested());
            assertEquals(0, staleCallbacks.get());
        } finally {
            interpreter.stop();
        }
    }

    @Test
    public void aCanceledCallbackCannotWaitForOrConsumeTheRestartedRunsStep() throws Exception {
        FarmWorld world = new FarmWorld(new String[]{"W"}, 0, 0);
        CountDownLatch oldEntered = new CountDownLatch(1);
        CountDownLatch releaseOldCallback = new CountDownLatch(1);
        CountDownLatch newEntered = new CountDownLatch(1);
        CountDownLatch restarted = new CountDownLatch(1);
        AtomicInteger staleCallbacks = new AtomicInteger();
        Thread[] oldWorker = new Thread[1];
        String[] restartError = new String[1];
        Interpreter interpreter = new Interpreter(new WorldClock(0));
        try {
            interpreter.start(program(Ast.harvest()), world, new ExecListener() {
                @Override public void onNodeEnter(int farmerId, int nodeId) {
                    oldWorker[0] = Thread.currentThread();
                    oldEntered.countDown();
                    boolean released = false;
                    while (!released) {
                        try {
                            releaseOldCallback.await();
                            released = true;
                        } catch (InterruptedException ignored) {
                            // Model a listener that clears the interrupt while its UI callback is pending.
                        }
                    }
                }
                @Override public void onFinished(int farmerId) { staleCallbacks.incrementAndGet(); }
                @Override public void onError(int farmerId, int nodeId, String message) { staleCallbacks.incrementAndGet(); }
            }, true);
            assertTrue(oldEntered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            interpreter.stop();
            world.reset();
            interpreter.start(program(Ast.harvest()), world, new ExecListener() {
                @Override public void onNodeEnter(int farmerId, int nodeId) { newEntered.countDown(); }
                @Override public void onFinished(int farmerId) { restarted.countDown(); }
                @Override public void onError(int farmerId, int nodeId, String message) {
                    restartError[0] = message;
                    restarted.countDown();
                }
            }, true);
            assertTrue(newEntered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            releaseOldCallback.countDown();
            oldWorker[0].join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
            assertFalse("canceling does not require a permit from the new paused run", oldWorker[0].isAlive());
            assertEquals(0, world.wheatHarvested());
            interpreter.debug.step();
            assertTrue(restarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertNull(restartError[0]);
            assertEquals(1, world.wheatHarvested());
            assertEquals(0, staleCallbacks.get());
        } finally {
            releaseOldCallback.countDown();
            interpreter.debug.play();
            interpreter.stop();
        }
    }

    private static ExecListener completion(CountDownLatch done, String[] error) {
        return new ExecListener() {
            @Override public void onNodeEnter(int farmerId, int nodeId) { }
            @Override public void onFinished(int farmerId) { done.countDown(); }
            @Override public void onError(int farmerId, int nodeId, String message) {
                error[0] = message;
                done.countDown();
            }
        };
    }

    private static Set<String> names(String... names) {
        return new HashSet<>(Arrays.asList(names));
    }

    private static Set<String> names(List<VarDecl> declarations) {
        Set<String> names = new HashSet<>();
        for (VarDecl declaration : declarations) names.add(declaration.name);
        return names;
    }

    /** Compile displayed methods with the build JDK; the app never compiles player code. */
    private static void assertCompiles(String methods) throws IOException, InterruptedException {
        String source = "class PrintedProgram {\n"
                + "static class Farmhand { void harvest() {} }\n" + methods + "}\n";
        Path directory = Files.createTempDirectory("coffee-crops-source-");
        Process compiler = null;
        try {
            Path input = directory.resolve("PrintedProgram.java");
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
