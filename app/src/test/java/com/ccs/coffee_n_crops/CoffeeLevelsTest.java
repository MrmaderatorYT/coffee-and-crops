package com.ccs.coffee_n_crops;

import com.ccs.coffee_n_crops.farm.Direction;
import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.farm.TileState;
import com.ccs.coffee_n_crops.farm.WorldClock;
import com.ccs.coffee_n_crops.farm.error.FarmException;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.expr.Not;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.IfStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.WhileStmt;
import com.ccs.coffee_n_crops.lang.check.Validator;
import com.ccs.coffee_n_crops.lang.interp.ExecListener;
import com.ccs.coffee_n_crops.lang.interp.Interpreter;
import com.ccs.coffee_n_crops.lang.print.JavaPrinter;
import com.ccs.coffee_n_crops.lang.serial.AstJson;
import com.ccs.coffee_n_crops.progress.LevelCatalog;
import com.ccs.coffee_n_crops.progress.LevelDef;
import com.ccs.coffee_n_crops.progress.LevelScore;
import com.ccs.coffee_n_crops.progress.Unlock;

import org.json.JSONObject;
import org.junit.Test;

import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/** Verifies the coffee crop's life cycle and both lessons through saved Java blocks. */
public final class CoffeeLevelsTest {
    @Test public void savedCoffeeProgramsCompileAndComplete() throws Exception {
        StringBuilder source = new StringBuilder("enum Direction { EAST }\n"
                + "class Farmhand { void move(Direction d) {} void walkTo(int x, int y) {} "
                + "void harvest() {} void water() {} void fertilize() {} void waitOneTick() {} "
                + "boolean isRipe() { return true; } String getCrop() { return \"coffee\"; } }\n");
        for (int number : new int[]{57, 58}) {
            LevelDef level = LevelCatalog.get(number - 1);
            Block original = solution(number);
            Block program = AstJson.blockFrom(new JSONObject(AstJson.toJson(original).toString()));
            assertNull(Validator.check(program));
            String java = JavaPrinter.toSource(program);
            assertEquals(JavaPrinter.toSource(original), java);
            source.append("class Lesson").append(number).append(" {\n").append(java).append("}\n");

            FarmWorld world = level.createWorld();
            assertNull(run(program, world));
            assertNull(level.check(world, program));
            assertEquals(2, world.coffeeHarvested());
            assertEquals(0, world.wheatHarvested());
            assertEquals(number == 58 ? 1 : 0, world.fertilized());
        }
        Path temp = Files.createTempDirectory("coffee-level-java");
        Path java = temp.resolve("CoffeeLessons.java");
        Files.write(java, source.toString().getBytes(StandardCharsets.UTF_8));
        Path javac = Path.of(System.getProperty("java.home"), "bin", "javac");
        Process compiler = new ProcessBuilder(javac.toString(), "-d", temp.toString(), java.toString())
                .redirectErrorStream(true).start();
        assertTrue("javac timed out", compiler.waitFor(15, TimeUnit.SECONDS));
        assertEquals(new String(compiler.getInputStream().readAllBytes(), StandardCharsets.UTF_8),
                0, compiler.exitValue());
    }

    @Test public void growthUsesElapsedTicksAndFertilizerOnlyAppliesOnce() {
        FarmWorld world = new FarmWorld(new String[]{"cW"}, 0, 0);
        world.advanceTicks(FarmWorld.COFFEE_GROWTH_TICKS * 2);
        assertTrue(world.isDry(world.farmer));
        assertEquals(FarmWorld.CROP_COFFEE, world.cropName(world.farmer));
        world.water(world.farmer);
        world.fertilize(world.farmer);
        world.fertilize(world.farmer);
        assertEquals(1, world.fertilized());
        world.advanceTicks(2);
        assertFalse(world.isRipe(world.farmer));
        world.advanceTicks(.5);
        assertTrue(world.isRipe(world.farmer));
        world.harvest(world.farmer);
        assertEquals(1, world.coffeeHarvested());
        assertEquals(0, world.wheatHarvested());
        world.reset();
        assertEquals(TileState.COFFEE_GROWING, world.tile(0, 0).state);
        assertEquals(0, world.coffeeHarvested());
        assertEquals(0, world.fertilized());
        assertEquals(0, world.actionTicks(), .00001);
    }

    @Test public void rockBlocksSingleMovesAndPreflightOfWholeRoute() {
        FarmWorld world = LevelCatalog.get(57).createWorld();
        assertFalse(world.canMove(world.farmer, Direction.EAST));
        assertThrows(FarmException.class, () -> world.move(world.farmer, Direction.EAST));
        assertThrows(FarmException.class, () -> world.walkTo(world.farmer, 2, 0));
        assertEquals(0, world.farmer.pose().x);
        assertEquals(0, world.farmer.pose().y);
        assertEquals(1, world.walkTo(world.farmer, 0, 1));
        assertEquals(3, world.walkTo(world.farmer, 2, 0));
    }

    @Test public void sandboxHasEveryUnlockAndDoesNotConsumeACampaignIndex() throws Exception {
        LevelDef sandbox = LevelCatalog.get(LevelCatalog.SANDBOX_INDEX);
        assertTrue(sandbox.isSandbox());
        assertFalse(LevelCatalog.all().contains(sandbox));
        for (var field : Unlock.class.getFields()) {
            if (field.getType() == String.class && Modifier.isStatic(field.getModifiers())) {
                assertTrue(field.getName(), sandbox.unlocks.contains((String) field.get(null)));
            }
        }
        FarmWorld world = sandbox.createWorld();
        assertEquals(2, world.farmers().size());
        assertEquals(2, world.totalCarrots());
        world.walkTo(world.farmer, 5, 1);
        assertEquals(FarmWorld.CROP_CARROT, world.cropName(world.farmer));
        assertTrue(world.isRipe(world.farmer));
        world.harvest(world.farmer);
        assertEquals(1, world.carrotsHarvested());
        assertEquals(0, world.wheatHarvested());
    }

    @Test public void efficiencyStarsKeepSeparateRecordsForLinesAndActionTicks() {
        LevelScore score = LevelScore.firstRun(12, 7.2);
        assertEquals(1, score.stars(null));
        score = score.withRun(11, 7.3);
        assertEquals(2, score.stars(null));
        score = score.withRun(13, 7.1);
        assertEquals(3, score.stars(null));
        score = score.withRun(15, 9);
        assertEquals(3, score.stars(null));
        assertEquals(11, score.bestLines());
        assertEquals(7.1, score.bestTicks(), .00001);
        assertEquals(3, LevelScore.firstRun(10, 7.2).stars(new LevelScore.Target(10, 72)));
        assertEquals(2, LevelScore.firstRun(10, 7.3).stars(new LevelScore.Target(10, 72)));
    }

    static Block solution(int number) {
        Block program = new Block();
        if (number == 57) {
            ForStmt row = Ast.forCount("i", 4);
            IfStmt coffee = Ast.ifStmt(Ast.cropIs(FarmWorld.CROP_COFFEE), false);
            coffee.thenBlock.add(Ast.harvest());
            row.body.add(coffee);
            row.body.add(Ast.move(Direction.EAST));
            program.add(row);
            return program;
        }
        program.add(Ast.walkTo(Ast.lit(0), Ast.lit(1)));
        program.add(Ast.walkTo(Ast.lit(2), Ast.lit(0)));
        program.add(Ast.water());
        program.add(Ast.fertilize());
        WhileStmt wait = new WhileStmt(new Not(Ast.isRipe()));
        wait.body.add(Ast.waitOneTick());
        program.add(wait);
        program.add(Ast.harvest());
        program.add(Ast.walkTo(Ast.lit(3), Ast.lit(1)));
        program.add(Ast.harvest());
        return program;
    }

    private static String run(Block program, FarmWorld world) throws InterruptedException {
        Interpreter interpreter = new Interpreter(new WorldClock(0));
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> failure = new AtomicReference<>();
        interpreter.start(program, world, new ExecListener() {
            @Override public void onNodeEnter(int farmerId, int nodeId) { }
            @Override public void onFinished(int farmerId) { done.countDown(); }
            @Override public void onError(int farmerId, int nodeId, String message) {
                failure.set(message);
                done.countDown();
            }
        }, false);
        try {
            assertTrue("Interpreter timed out", done.await(3, TimeUnit.SECONDS));
            return failure.get();
        } finally {
            interpreter.stop();
        }
    }
}
