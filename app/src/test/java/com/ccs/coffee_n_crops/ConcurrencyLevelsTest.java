package com.ccs.coffee_n_crops;

import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.farm.Farmhand;
import com.ccs.coffee_n_crops.farm.WorldClock;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.expr.AtomicCounter;
import com.ccs.coffee_n_crops.lang.ast.expr.Binary;
import com.ccs.coffee_n_crops.lang.ast.expr.FieldAccess;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.ExprStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.FieldAssign;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.SynchronizedStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.TogetherStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.ast.stmt.WhileStmt;
import com.ccs.coffee_n_crops.lang.check.Validator;
import com.ccs.coffee_n_crops.lang.interp.ExecListener;
import com.ccs.coffee_n_crops.lang.interp.Interpreter;
import com.ccs.coffee_n_crops.lang.print.JavaPrinter;
import com.ccs.coffee_n_crops.lang.serial.AstJson;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.progress.LevelCatalog;
import com.ccs.coffee_n_crops.progress.LevelDef;

import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** These lessons execute on two genuine Java workers and retain their edits after a save round trip. */
public final class ConcurrencyLevelsTest {
    private static final int SYNCHRONIZED = 43;
    private static final int VOLATILE = 44;
    private static final int ATOMIC = 45;

    @Test public void allThreeReferenceSolutionsCompileAndCompleteAfterLoading() throws Exception {
        for (int index = SYNCHRONIZED; index <= ATOMIC; index++) {
            LevelDef level = LevelCatalog.get(index);
            Block answer = level.createProgram();
            TogetherStmt jobs = (TogetherStmt) answer.get(1);
            if (index == SYNCHRONIZED) protect(answer, jobs);
            else if (index == VOLATILE) ready(answer).volatileField = true;
            else for (Block body : jobs.childBlocks()) {
                body.add(new ExprStmt(new AtomicCounter(AtomicCounter.Op.INCREMENT, new VarRef("counter"))));
            }
            Block loaded = AstJson.blockFrom(new JSONObject(AstJson.toJson(answer).toString()));
            String source = JavaPrinter.toSource(loaded);
            assertEquals(JavaPrinter.toSource(answer), source);
            assertCompiles(source, loaded.classes().isEmpty());
            Validator.Problem problem = Validator.check(loaded);
            assertNull(problem == null ? null : problem.message, problem);
            FarmWorld world = level.createWorld();
            assertNull(level.id, run(loaded, world));
            assertNull(level.id, level.check(world, loaded));
        }
    }

    @Test public void merelyEnteringTwoEmptyMonitorsDoesNotSolveTheTally() throws Exception {
        LevelDef level = LevelCatalog.get(SYNCHRONIZED);
        Block program = level.createProgram();
        TogetherStmt jobs = (TogetherStmt) program.get(1);
        JType counterType = program.classes().get(0).type();
        for (Block job : jobs.childBlocks()) job.add(new SynchronizedStmt(counterType, new VarRef("counter")));
        program.remove(2);
        program.add(2, Ast.walkTo(Ast.lit(6), Ast.lit(0)));
        FarmWorld world = level.createWorld();
        assertNull(run(program, world));
        assertEquals(0, world.ripeRemaining());
        assertNotNull(level.check(world, program));
    }

    @Test public void poolStartsBothJobsOnDifferentThreadsBeforeJoining() {
        Farmhand farmer = new Farmhand(1, 0, 0);
        CountDownLatch bothStarted = new CountDownLatch(2);
        Set<Thread> workers = ConcurrentHashMap.newKeySet();
        Thread caller = Thread.currentThread();
        Runnable report = () -> {
            workers.add(Thread.currentThread());
            bothStarted.countDown();
            try {
                assertTrue("jobs must run concurrently", bothStarted.await(3, TimeUnit.SECONDS));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
        };
        farmer.runTogether(report, report);
        assertEquals(2, workers.size());
        assertTrue(!workers.contains(caller));
    }

    @Test public void aWorkerCannotMutateTheFarmOrWaitForever() throws Exception {
        Block program = new Block();
        TogetherStmt jobs = new TogetherStmt();
        program.add(jobs);
        jobs.first.add(Ast.harvest());
        assertNotNull(Validator.check(program));
        jobs.first.remove(0);
        jobs.first.add(new WhileStmt(Ast.lit(true)));
        assertNull(Validator.check(program));
        FarmWorld world = new FarmWorld(new String[]{"W"}, 0, 0);
        assertEquals("Background job exceeded its step limit.", run(program, world));
        assertEquals(1, world.ripeRemaining());
    }

    private static void protect(Block program, TogetherStmt jobs) {
        JType type = program.classes().get(0).type();
        for (Block job : jobs.childBlocks()) {
            SynchronizedStmt guard = new SynchronizedStmt(type, new VarRef("counter"));
            FieldAccess write = new FieldAccess(new VarRef("counter"), type, "total", JType.INT);
            FieldAccess read = new FieldAccess(new VarRef("counter"), type, "total", JType.INT);
            guard.body.add(new FieldAssign(write, new Binary(Binary.Op.ADD, read, Ast.lit(1))));
            ((ForStmt) job.get(0)).body.add(guard);
        }
    }

    private static VarDecl ready(Block program) {
        return program.classes().get(0).fields().stream().filter(field -> field.name.equals("ready")).findFirst().get();
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
            assertTrue("program did not finish", done.await(5, TimeUnit.SECONDS));
            return error[0];
        } finally {
            interpreter.stop();
        }
    }

    private static void assertCompiles(String source, boolean wrapMethod) throws Exception {
        String farmApi = "class Farmhand { void walkTo(int x, int y) {} void harvest() {} "
                + "void runTogether(Runnable first, Runnable second) {} }\n";
        Path directory = Files.createTempDirectory("coffee-crops-concurrency-");
        Process compiler = null;
        try {
            Path input = directory.resolve("Program.java");
            String program = farmApi + (wrapMethod ? "class Program {\n" + source + "}\n" : source);
            Files.write(input, program.getBytes(StandardCharsets.UTF_8));
            String javac = new File(System.getProperty("java.home"), "bin/javac").getAbsolutePath();
            compiler = new ProcessBuilder(javac, "-proc:none", "--release", "17", input.toString())
                    .redirectErrorStream(true).start();
            assertTrue("javac did not finish", compiler.waitFor(5, TimeUnit.SECONDS));
            String diagnostics = new String(compiler.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(diagnostics + "\n" + source, 0, compiler.exitValue());
        } finally {
            if (compiler != null) compiler.destroyForcibly();
            File[] files = directory.toFile().listFiles();
            if (files != null) for (File file : files) Files.deleteIfExists(file.toPath());
            Files.deleteIfExists(directory);
        }
    }
}
