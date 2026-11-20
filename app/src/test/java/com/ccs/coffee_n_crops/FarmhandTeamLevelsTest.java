package com.ccs.coffee_n_crops;

import com.ccs.coffee_n_crops.farm.Direction;
import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.farm.WorldClock;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.expr.AtomicCounter;
import com.ccs.coffee_n_crops.lang.ast.expr.Binary;
import com.ccs.coffee_n_crops.lang.ast.expr.FieldAccess;
import com.ccs.coffee_n_crops.lang.ast.expr.Literal;
import com.ccs.coffee_n_crops.lang.ast.expr.MethodCall;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.ExprStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.FieldAssign;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.LockStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.TeamStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.check.ScopeResolver;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Saved reference solutions and a deterministic reproduction of the pump-lock cycle. */
public final class FarmhandTeamLevelsTest {
    private static final int FIRST = 53;
    private static final int LAST = 56;
    private static final int ROW_LENGTH = 3;

    @Test public void referenceSolutionsSurviveSavingCompileAndCompleteAllFourLessons() throws Exception {
        for (int number = FIRST; number <= LAST; number++) {
            LevelDef level = level(number);
            Block solution = level.createProgram();
            solve(solution, number);
            Block saved = AstJson.blockFrom(new JSONObject(AstJson.toJson(solution).toString()));
            String source = JavaPrinter.toSource(saved);
            assertEquals(JavaPrinter.toSource(solution), source);
            assertCompiles(source);
            assertValid(saved);
            FarmWorld world = level.createWorld();
            assertEquals(2, world.farmers().size());
            assertNull(level.id, run(saved, world, ignored -> { }));
            assertNull(level.id, level.check(world, saved));
        }
    }

    @Test public void oppositePumpOrderReportsTheCycleAndTheTeamCanRunAgainAfterReset() throws Exception {
        LevelDef level = level(LAST);
        Block program = level.createProgram();
        TeamStmt team = team(program);
        LockStmt firstWait = nestedLock(team.first);
        LockStmt secondWait = nestedLock(team.second);
        CountDownLatch firstLocksHeld = new CountDownLatch(2);
        FarmWorld world = level.createWorld();
        assertValid(program);
        String error = run(program, world, nodeId -> {
            if (nodeId != firstWait.id && nodeId != secondWait.id) return;
            // Start both inner acquisitions only once both threads hold different outer locks.
            firstLocksHeld.countDown();
            try {
                assertTrue("workers did not reach the two-lock cycle", firstLocksHeld.await(2, TimeUnit.SECONDS));
            } catch (InterruptedException cancelled) {
                Thread.currentThread().interrupt();
            }
        });
        assertNotNull(error);
        assertTrue(error, error.contains("Deadlock") && error.contains("pumpA") && error.contains("pumpB"));
        assertEquals(0, world.watered());

        world.reset();
        solve(program, LAST);
        assertNull(run(program, world, ignored -> { }));
        assertNull(level.check(world, program));
    }

    @Test public void harvestingBothRowsWithOnlyOneActorDoesNotCompleteTheTeamLesson() throws Exception {
        LevelDef level = level(FIRST);
        Block program = level.createProgram();
        TeamStmt team = team(program);
        for (int row = 0; row < 2; row++) {
            for (int x = 0; x < ROW_LENGTH; x++) {
                team.first.add(action(team.firstFarmer.name, "walkTo", Ast.lit(x), Ast.lit(row)));
                team.first.add(action(team.firstFarmer.name, "harvest"));
            }
        }
        FarmWorld world = level.createWorld();
        assertValid(program);
        assertNull(run(program, world, ignored -> { }));
        assertEquals(0, world.ripeRemaining());
        assertNotNull(level.check(world, program));
    }

    @Test public void eachLambdaBindsItsOwnActorAndCapturesTheSharedVariables() {
        Block program = level(54).createProgram();
        TeamStmt team = team(program);
        assertTrue(ScopeResolver.visibleAt(team.first, 0).contains(team.firstFarmer));
        assertFalse(ScopeResolver.visibleAt(team.first, 0).contains(team.secondFarmer));
        assertTrue(ScopeResolver.visibleAt(team.second, 0).contains((VarDecl) program.get(0)));
        team.second.add(action(team.firstFarmer.name, "harvest"));
        assertEquals("Use second for farm actions in this job.", Validator.check(program).message);
    }

    private static void solve(Block program, int number) {
        TeamStmt team = team(program);
        if (number == LAST) {
            ((LockStmt) team.second.get(0)).lock.set(new VarRef("pumpA"));
            nestedLock(team.second).lock.set(new VarRef("pumpB"));
            return;
        }
        solveRow(team.first, team.firstFarmer.name, number);
        solveRow(team.second, team.secondFarmer.name, number);
    }

    private static void solveRow(Block job, String actor, int number) {
        if (job.size() == 0) job.add(Ast.forCount("i", ROW_LENGTH));
        Block body = ((ForStmt) job.get(0)).body;
        body.add(action(actor, "harvest"));
        if (number == 54) {
            body.add(new ExprStmt(new AtomicCounter(AtomicCounter.Op.INCREMENT, new VarRef("tally"))));
        } else if (number == 55) {
            LockStmt guard = new LockStmt(new VarRef("gate"));
            guard.body.add(new FieldAssign(total(), new Binary(Binary.Op.ADD, total(), Ast.lit(1))));
            body.add(guard);
        }
        body.add(action(actor, "move", new Literal(JType.DIRECTION, Direction.EAST)));
    }

    private static FieldAccess total() {
        return new FieldAccess(new VarRef("counter"), JType.reference("Counter"), "total", JType.INT);
    }

    private static ExprStmt action(String actor, String name, Expr... args) {
        return new ExprStmt(new MethodCall(actor, name, args));
    }

    private static TeamStmt team(Block program) {
        return program.statements().stream().filter(TeamStmt.class::isInstance)
                .map(TeamStmt.class::cast).findFirst().orElseThrow(AssertionError::new);
    }

    private static LockStmt nestedLock(Block job) { return (LockStmt) ((LockStmt) job.get(0)).body.get(0); }

    private static LevelDef level(int number) { return LevelCatalog.get(number - 1); }

    private static void assertValid(Block program) {
        Validator.Problem problem = Validator.check(program);
        assertNull(problem == null ? null : problem.message, problem);
    }

    private static String run(Block program, FarmWorld world, IntConsumer entry) throws InterruptedException {
        Interpreter interpreter = new Interpreter(new WorldClock(0));
        CountDownLatch completed = new CountDownLatch(1);
        String[] error = {null};
        try {
            interpreter.start(program, world, new ExecListener() {
                @Override public void onNodeEnter(int farmerId, int nodeId) { entry.accept(nodeId); }
                @Override public void onFinished(int farmerId) { completed.countDown(); }
                @Override public void onError(int farmerId, int nodeId, String message) {
                    error[0] = message;
                    completed.countDown();
                }
            }, false);
            assertTrue("team did not finish", completed.await(5, TimeUnit.SECONDS));
            return error[0];
        } finally {
            interpreter.stop();
        }
    }

    private static void assertCompiles(String source) throws Exception {
        String api = "enum Direction { EAST }\n"
                + "class Farmhand { void workTogether(java.util.function.Consumer<Farmhand> a, "
                + "java.util.function.Consumer<Farmhand> b) {} void move(Direction d) {} "
                + "void harvest() {} void water() {} }\n";
        String compilationUnit = source.startsWith("class Program") ? source : "class Program {\n" + source + "}\n";
        Path directory = Files.createTempDirectory("coffee-farmhand-team-");
        Process compiler = null;
        try {
            Path input = directory.resolve("Program.java");
            Files.write(input, (api + compilationUnit).getBytes(StandardCharsets.UTF_8));
            String javac = new File(System.getProperty("java.home"), "bin/javac").getPath();
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
