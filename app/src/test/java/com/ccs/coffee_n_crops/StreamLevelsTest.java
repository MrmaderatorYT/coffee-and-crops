package com.ccs.coffee_n_crops;

import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.farm.WorldClock;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.expr.Binary;
import com.ccs.coffee_n_crops.lang.ast.expr.Literal;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamCall;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamCall.Stage;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamCall.StageKind;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamCall.Terminal;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamCallback;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamLambda;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamMethodRef;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.ExprStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForEachStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.IfStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ListAdd;
import com.ccs.coffee_n_crops.lang.ast.stmt.StreamForEachStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
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
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Compile, save, and play the six stream lessons through the production interpreter. */
public final class StreamLevelsTest {
    private static final int FIRST = 47;
    private static final int LAST = 52;
    private static final String STOPS = "stops";
    private static final String ROUTE = "route";
    private static final String CELL = "cell";
    private static final String COUNT = "count";
    private static final String FOUND = "found";

    @Test public void savedSolutionsCompileAndCompleteEveryLesson() throws Exception {
        StringBuilder source = new StringBuilder("import java.util.ArrayList;\n"
                + "class Farmhand { void harvest() {} void walkTo(int x, int y) {} }\n");
        for (int number = FIRST; number <= LAST; number++) {
            LevelDef level = LevelCatalog.get(number - 1);
            Block original = solution(level, number);
            Block restored = AstJson.blockFrom(new JSONObject(AstJson.toJson(original).toString()));
            assertEquals(level.id, JavaPrinter.toSource(original), JavaPrinter.toSource(restored));
            source.append("class Level").append(number).append(" {\n")
                    .append(JavaPrinter.toSource(restored)).append("}\n");
            Validator.Problem problem = Validator.check(restored);
            assertNull(level.id + (problem == null ? "" : ": " + problem.message), problem);
            FarmWorld world = level.createWorld();
            assertNull(level.id, run(restored, world));
            assertNull(level.id, level.check(world, restored));
        }
        assertCompiles(source.toString());
    }

    @Test public void anyMatchStopsAtTheFirstTrueElement() throws Exception {
        Block program = new Block();
        program.add(Ast.newList(STOPS));
        program.add(new ListAdd(STOPS, Ast.lit(2)));
        program.add(new ListAdd(STOPS, Ast.lit(0)));
        Expr division = new Binary(Binary.Op.DIV, Ast.lit(4), new VarRef(CELL));
        StreamLambda predicate = new StreamLambda(CELL, JType.BOOLEAN, Ast.eq(division, 2));
        program.add(new VarDecl(JType.BOOLEAN, FOUND, pipeline(Terminal.ANY_MATCH, predicate)));
        harvestWhen(program, new VarRef(FOUND));

        assertNull(Validator.check(program));
        FarmWorld world = new FarmWorld(new String[]{"W"}, 0, 0);
        assertNull(run(program, world));
        assertEquals(1, world.wheatHarvested());
        program.remove(1);
        assertEquals("ArithmeticException: / by zero", run(program, world));
    }

    @Test public void harvestedCropsAloneCannotSatisfyTheLambdaLesson() throws Exception {
        LevelDef level = LevelCatalog.get(FIRST - 1);
        Block program = level.createProgram();
        IfStmt skipped = Ast.ifStmt(Ast.lit(false), false);
        skipped.thenBlock.add(new StreamForEachStmt(pipeline(Terminal.FOR_EACH, harvestEach())));
        program.add(skipped);
        for (int column : new int[]{1, 3, 5}) {
            program.add(Ast.walkTo(Ast.lit(column), Ast.lit(0)));
            program.add(Ast.harvest());
        }
        FarmWorld world = level.createWorld();
        assertNull(Validator.check(program));
        assertNull(run(program, world));
        assertEquals(0, world.ripeRemaining());
        assertNotNull(level.check(world, program));
    }

    @Test public void wrongStopIsRejectedEvenIfAnyMatchReturnsTrue() throws Exception {
        LevelDef level = LevelCatalog.get(50 - 1);
        Block program = level.createProgram();
        program.add(new VarDecl(JType.BOOLEAN, FOUND,
                pipeline(Terminal.ANY_MATCH, predicate(Binary.Op.EQ, 2))));
        harvestWhen(program, new VarRef(FOUND));
        FarmWorld world = level.createWorld();
        assertNull(Validator.check(program));
        assertNull(run(program, world));
        assertEquals(1, world.wheatHarvested());
        assertNotNull(level.check(world, program));
    }

    private static Block solution(LevelDef level, int number) {
        Block program = level.createProgram();
        switch (number) {
            case 47 -> program.add(new StreamForEachStmt(pipeline(Terminal.FOR_EACH, harvestEach())));
            case 48 -> program.add(new StreamForEachStmt(pipeline(Terminal.FOR_EACH, harvestEach(), shift(2))));
            case 49 -> {
                program.add(new VarDecl(JType.LONG, COUNT, pipeline(Terminal.COUNT, null, cutoff(2))));
                harvestWhen(program, new Binary(Binary.Op.EQ, JType.LONG,
                        new VarRef(COUNT), new Literal(JType.LONG, 3L)));
            }
            case 50 -> {
                program.add(new VarDecl(JType.BOOLEAN, FOUND,
                        pipeline(Terminal.ANY_MATCH, predicate(Binary.Op.EQ, 4))));
                harvestWhen(program, new VarRef(FOUND));
            }
            case 51 -> {
                program.add(new ExprStmt(pipeline(Terminal.FOR_EACH, new StreamMethodRef(ROUTE, "add"))));
                ForEachStmt loop = new ForEachStmt(JType.INT, CELL, ROUTE);
                harvestAtCell(loop.body, CELL);
                program.add(loop);
            }
            case 52 -> program.add(new StreamForEachStmt(pipeline(Terminal.FOR_EACH, harvestEach(), shift(1), cutoff(4))));
            default -> throw new IllegalArgumentException("Unexpected stream level " + number);
        }
        return program;
    }

    private static StreamCall pipeline(Terminal terminal, StreamCallback callback, Stage... stages) {
        return new StreamCall(STOPS, terminal, Arrays.asList(stages), callback);
    }

    private static Stage shift(int columns) {
        return new Stage(StageKind.MAP, new StreamLambda(CELL, JType.INT,
                new Binary(Binary.Op.ADD, new VarRef(CELL), Ast.lit(columns))));
    }

    private static Stage cutoff(int column) {
        return new Stage(StageKind.FILTER, predicate(Binary.Op.GE, column));
    }

    private static StreamLambda predicate(Binary.Op operator, int column) {
        return new StreamLambda(CELL, JType.BOOLEAN, new Binary(operator, new VarRef(CELL), Ast.lit(column)));
    }

    private static StreamLambda harvestEach() {
        StreamLambda lambda = new StreamLambda(CELL);
        harvestAtCell(lambda.body, CELL);
        return lambda;
    }

    private static void harvestAtCell(Block body, String column) {
        body.add(Ast.walkTo(new VarRef(column), Ast.lit(0)));
        body.add(Ast.harvest());
    }

    private static void harvestWhen(Block program, Expr condition) {
        IfStmt guard = Ast.ifStmt(condition, false);
        guard.thenBlock.add(Ast.harvest());
        program.add(guard);
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

    private static void assertCompiles(String source) throws Exception {
        Path directory = Files.createTempDirectory("farm-stream-source-");
        Process compiler = null;
        try {
            Path input = directory.resolve("Solutions.java");
            Files.write(input, source.getBytes(StandardCharsets.UTF_8));
            String javac = new File(System.getProperty("java.home"), "bin/javac").getAbsolutePath();
            compiler = new ProcessBuilder(javac, "-proc:none", "--release", "17", input.toString())
                    .redirectErrorStream(true).start();
            assertTrue("javac did not finish", compiler.waitFor(8, TimeUnit.SECONDS));
            assertEquals(new String(compiler.getInputStream().readAllBytes(), StandardCharsets.UTF_8), 0,
                    compiler.exitValue());
        } finally {
            if (compiler != null) compiler.destroyForcibly();
            File[] files = directory.toFile().listFiles();
            if (files != null) for (File file : files) Files.deleteIfExists(file.toPath());
            Files.deleteIfExists(directory);
        }
    }
}
