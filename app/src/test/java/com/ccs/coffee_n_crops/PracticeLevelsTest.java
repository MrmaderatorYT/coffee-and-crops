package com.ccs.coffee_n_crops;

import com.ccs.coffee_n_crops.farm.Direction;
import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.farm.WorldClock;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.ast.expr.Access;
import com.ccs.coffee_n_crops.lang.ast.expr.Binary;
import com.ccs.coffee_n_crops.lang.ast.expr.ListContains;
import com.ccs.coffee_n_crops.lang.ast.expr.NewIntArray;
import com.ccs.coffee_n_crops.lang.ast.expr.Not;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.ArrayAssign;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.BreakStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.CompoundAssign;
import com.ccs.coffee_n_crops.lang.ast.stmt.ContinueStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.DecrementStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.DoWhileStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForEachStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.IfStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ListAdd;
import com.ccs.coffee_n_crops.lang.ast.stmt.ListRemove;
import com.ccs.coffee_n_crops.lang.ast.stmt.ListSet;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Builds, saves, compiles and runs the reference solution for each practice level. */
public class PracticeLevelsTest {
    private static final int FIRST = 23;
    private static final int LAST = 32;
    private static final String COUNTER = "steps";
    private static final String COLUMN = "i";
    private static final String ARRAY = "row";
    private static final String LIST = "list";

    private static Block solution(int number) {
        return switch (number) {
            case 23 -> count(false);
            case 24 -> count(true);
            case 25 -> doThenCheck();
            case 26 -> breakAtEdge();
            case 27 -> skipSprouts();
            case 28 -> useLength();
            case 29 -> writeRoute();
            case 30 -> blockedColumns();
            case 31 -> repairRoute();
            case 32 -> shortenRoute();
            default -> throw new AssertionError(number);
        };
    }

    private static Block count(boolean down) {
        VarDecl steps = new VarDecl(JType.INT, COUNTER, Ast.lit(down ? 5 : 0));
        Binary condition = new Binary(down ? Binary.Op.GE : Binary.Op.LT,
                new VarRef(COUNTER), Ast.lit(down ? 0 : 7));
        WhileStmt loop = new WhileStmt(condition);
        add(loop.body, visit(new VarRef(COUNTER)), Ast.harvest(),
                down ? new DecrementStmt(COUNTER) : new CompoundAssign(COUNTER, Ast.lit(2)));
        return program(steps, loop);
    }

    private static Block doThenCheck() {
        DoWhileStmt loop = new DoWhileStmt(Ast.canMove(Direction.EAST));
        add(loop.body, Ast.harvest(), Ast.move(Direction.EAST));
        return program(loop);
    }

    private static Block breakAtEdge() {
        WhileStmt loop = new WhileStmt(Ast.lit(true));
        IfStmt edge = Ast.ifStmt(new Not(Ast.canMove(Direction.EAST)), false);
        edge.thenBlock.add(new BreakStmt());
        add(loop.body, Ast.harvest(), edge, Ast.move(Direction.EAST));
        return program(loop);
    }

    private static Block skipSprouts() {
        ForStmt loop = Ast.forCount(COLUMN, 8);
        IfStmt notRipe = Ast.ifStmt(new Not(Ast.isRipe()), false);
        notRipe.thenBlock.add(new ContinueStmt());
        add(loop.body, visit(new VarRef(COLUMN)), notRipe, Ast.harvest());
        return program(loop);
    }

    private static Block useLength() {
        ForStmt loop = Ast.forCount(COLUMN, 0);
        ((Binary) loop.cond.get()).right.set(new Access(Access.Kind.LENGTH, ARRAY));
        add(loop.body, visit(new VarRef(COLUMN)), Ast.harvest());
        return program(newArray(5), loop);
    }

    private static Block writeRoute() {
        return program(newArray(3),
                new ArrayAssign(ARRAY, Ast.lit(0), Ast.lit(1)),
                new ArrayAssign(ARRAY, Ast.lit(1), Ast.lit(4)),
                new ArrayAssign(ARRAY, Ast.lit(2), Ast.lit(6)), harvestRoute(ARRAY));
    }

    private static Block blockedColumns() {
        Block program = list(1, 4, 6);
        ForStmt loop = Ast.forCount(COLUMN, 8);
        IfStmt allowed = Ast.ifStmt(new Not(new ListContains(LIST, new VarRef(COLUMN))), false);
        add(allowed.thenBlock, visit(new VarRef(COLUMN)), Ast.harvest());
        loop.body.add(allowed);
        program.add(loop);
        return program;
    }

    private static Block repairRoute() {
        Block program = list(1, 2, 5);
        add(program, new ListSet(LIST, Ast.lit(1), Ast.lit(3)), harvestRoute(LIST));
        return program;
    }

    private static Block shortenRoute() {
        Block program = list(0, 1, 3, 5);
        add(program, new ListRemove(LIST, Ast.lit(1)), harvestRoute(LIST));
        return program;
    }

    private static Block list(int... columns) {
        Block program = program(Ast.newList(LIST));
        for (int column : columns) program.add(new ListAdd(LIST, Ast.lit(column)));
        return program;
    }

    private static VarDecl newArray(int size) {
        return new VarDecl(JType.INT_ARRAY, ARRAY, new NewIntArray(Ast.lit(size)));
    }

    private static ForEachStmt harvestRoute(String source) {
        ForEachStmt loop = new ForEachStmt(JType.INT, "cell", source);
        add(loop.body, visit(new VarRef("cell")), Ast.harvest());
        return loop;
    }

    private static Stmt visit(Expr x) {
        return Ast.walkTo(x, Ast.lit(0));
    }

    private static Block program(Stmt... statements) {
        Block program = new Block();
        add(program, statements);
        return program;
    }

    private static void add(Block block, Stmt... statements) {
        for (Stmt statement : statements) block.add(statement);
    }

    @Test public void allTenSolutionsCompleteAfterSavingAndLoading() throws Exception {
        StringBuilder sources = new StringBuilder("import java.util.ArrayList;\n"
                + "enum Direction { EAST }\n"
                + "class Farmhand { void harvest() {} void move(Direction direction) {} "
                + "void walkTo(int x, int y) {} boolean isRipe() { return true; } "
                + "boolean canMove(Direction direction) { return true; } }\n");
        for (int number = FIRST; number <= LAST; number++) {
            LevelDef level = LevelCatalog.get(number - 1);
            Block original = solution(number);
            Block loaded = AstJson.blockFrom(new JSONObject(AstJson.toJson(original).toString()));
            String source = JavaPrinter.toSource(loaded);
            assertEquals(level.id, JavaPrinter.toSource(original), source);
            sources.append("class Level").append(number).append(" {\n").append(source).append("}\n");
            Validator.Problem problem = Validator.check(loaded);
            assertNull(level.id + (problem == null ? "" : ": " + problem.message), problem);
            FarmWorld world = level.createWorld();
            run(loaded, world, level.id);
            assertEquals(level.id, 0, world.ruined());
            assertEquals(level.id, 0, world.ripeRemaining());
            assertNull(level.id, level.check(world, loaded));
        }
        assertCompiles(sources.toString());
    }

    @Test public void anUnrunExampleDoesNotCountEvenIfTheCropsAreAlreadyHarvested() {
        for (int number = FIRST; number <= LAST; number++) {
            LevelDef level = LevelCatalog.get(number - 1);
            FarmWorld world = level.createWorld();
            for (int x = 0; x < world.width; x++) {
                world.walkTo(world.farmer, x, 0);
                if (world.isRipe(world.farmer)) world.harvest(world.farmer);
            }
            if (level.finish() != null) world.walkTo(world.farmer, level.finish().x(), level.finish().y());
            assertEquals(level.id, 0, world.ripeRemaining());
            assertNotNull(level.id, level.check(world, solution(number)));
        }
    }

    private static void run(Block program, FarmWorld world, String name) throws InterruptedException {
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
            assertTrue(name + " did not finish", done.await(5, TimeUnit.SECONDS));
            assertNull(name, error[0]);
        } finally {
            interpreter.stop();
        }
    }

    private static void assertCompiles(String source) throws Exception {
        Path directory = Files.createTempDirectory("farm-practice-source-");
        Process compiler = null;
        try {
            Path input = directory.resolve("Practice.java");
            Files.write(input, source.getBytes(StandardCharsets.UTF_8));
            String javac = new File(System.getProperty("java.home"), "bin/javac").getAbsolutePath();
            compiler = new ProcessBuilder(javac, "-proc:none", "--release", "17", input.toString())
                    .redirectErrorStream(true).start();
            assertTrue("javac did not finish", compiler.waitFor(8, TimeUnit.SECONDS));
            assertEquals(new String(compiler.getInputStream().readAllBytes()) + "\n" + source,
                    0, compiler.exitValue());
        } finally {
            if (compiler != null) compiler.destroyForcibly();
            File[] files = directory.toFile().listFiles();
            if (files != null) for (File file : files) Files.deleteIfExists(file.toPath());
            Files.deleteIfExists(directory);
        }
    }
}
