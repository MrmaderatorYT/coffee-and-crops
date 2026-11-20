package com.ccs.coffee_n_crops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ccs.coffee_n_crops.farm.Direction;
import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.farm.WorldClock;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.MethodDecl;
import com.ccs.coffee_n_crops.lang.ast.expr.Access;
import com.ccs.coffee_n_crops.lang.ast.expr.Binary;
import com.ccs.coffee_n_crops.lang.ast.expr.UserCall;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.ExprStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.IfStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ReturnStmt;
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

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Reference solutions and construct requirements for every methods lesson. */
public class MethodLevelsTest {
    private static final int FIRST_METHOD_LEVEL = 11;
    private static final int METHOD_LEVEL_COUNT = 4;
    private static final int ROW_LENGTH = 5;
    private static final int ROW_COUNT = 3;
    private static final int TENDING_WIDTH = 6;

    private static ForStmt loop(String name, Expr bound) {
        ForStmt loop = Ast.forCount(name, 0);
        ((Binary) loop.cond.get()).right.set(bound);
        return loop;
    }

    private static Block reusableRows() {
        Block program = new Block();
        MethodDecl harvestRow = new MethodDecl("harvestRow", JType.VOID);
        ForStmt harvest = Ast.forCount("i", ROW_LENGTH);
        harvest.body.add(Ast.harvest());
        harvest.body.add(Ast.move(Direction.EAST));
        harvestRow.body.add(harvest);
        program.addMethod(harvestRow);
        ForStmt rows = Ast.forCount("i", ROW_COUNT);
        rows.body.add(Ast.walkTo(Ast.lit(0), new VarRef("i")));
        rows.body.add(new ExprStmt(new UserCall(harvestRow)));
        program.add(rows);
        return program;
    }

    private static Block returnedCount() {
        Block program = new Block();
        MethodDecl countRipe = new MethodDecl("countRipe", JType.INT);
        countRipe.body.add(Ast.scanRow("row"));
        VarDecl count = Ast.declare(JType.INT, "count");
        countRipe.body.add(count);
        ForStmt scan = loop("i", new Access(Access.Kind.LENGTH, "row"));
        IfStmt ripe = Ast.ifStmt(Ast.eq(new Access(Access.Kind.INDEX, "row", new VarRef("i")),
                FarmWorld.SCAN_RIPE), false);
        ripe.thenBlock.add(Ast.assign(count, new Binary(Binary.Op.ADD, new VarRef("count"), Ast.lit(1))));
        scan.body.add(ripe);
        countRipe.body.add(scan);
        countRipe.body.add(new ReturnStmt(JType.INT, new VarRef("count")));
        program.addMethod(countRipe);
        program.add(new VarDecl(JType.INT, "count", new UserCall(countRipe)));
        ForStmt harvest = loop("i", new VarRef("count"));
        harvest.body.add(Ast.harvest());
        harvest.body.add(Ast.move(Direction.EAST));
        program.add(harvest);
        return program;
    }

    private static Block parameterRows() {
        Block program = new Block();
        MethodDecl tendRow = new MethodDecl("tendRow", JType.VOID,
                new VarDecl(JType.INT, "y", Ast.lit(0)));
        ForStmt columns = Ast.forCount("i", TENDING_WIDTH);
        columns.body.add(Ast.walkTo(new VarRef("i"), new VarRef("y")));
        IfStmt tend = Ast.ifStmt(Ast.isRipe(), false);
        tend.thenBlock.add(Ast.harvest());
        tend.addElseIf(Ast.isDry()).block.add(Ast.water());
        columns.body.add(tend);
        tendRow.body.add(columns);
        program.addMethod(tendRow);
        ForStmt rows = Ast.forCount("i", ROW_COUNT);
        rows.body.add(new ExprStmt(new UserCall(tendRow, new VarRef("i"))));
        program.add(rows);
        return program;
    }

    private static Block recursiveWalk() {
        Block program = new Block();
        MethodDecl walk = new MethodDecl("walkToEdge", JType.VOID);
        IfStmt ripe = Ast.ifStmt(Ast.isRipe(), false);
        ripe.thenBlock.add(Ast.harvest());
        walk.body.add(ripe);
        IfStmt more = Ast.ifStmt(Ast.canMove(Direction.EAST), false);
        more.thenBlock.add(Ast.move(Direction.EAST));
        more.thenBlock.add(new ExprStmt(new UserCall(walk)));
        walk.body.add(more);
        program.addMethod(walk);
        program.add(new ExprStmt(new UserCall(walk)));
        return program;
    }

    private static void run(Block program, FarmWorld world) throws InterruptedException {
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
            assertNull(error[0]);
        } finally {
            interpreter.stop();
        }
    }

    @Test
    public void allFourReferenceSolutionsCompleteAfterSaving() throws Exception {
        Block[] solutions = {reusableRows(), returnedCount(), parameterRows(), recursiveWalk()};
        assertEquals(METHOD_LEVEL_COUNT, solutions.length);
        for (int i = 0; i < solutions.length; i++) {
            LevelDef level = LevelCatalog.get(FIRST_METHOD_LEVEL + i);
            Block loaded = AstJson.blockFrom(new JSONObject(AstJson.toJson(solutions[i]).toString()));
            assertEquals(JavaPrinter.toSource(solutions[i]), JavaPrinter.toSource(loaded));
            Validator.Problem problem = Validator.check(loaded);
            assertNull(problem == null ? null : problem.message, problem);
            FarmWorld world = level.createWorld();
            run(loaded, world);
            assertEquals(level.id, 0, world.ripeRemaining());
            assertEquals(level.id, 0, world.dryRemaining());
            assertEquals(level.id, 0, world.ruined());
            assertNull(level.id, level.check(world, loaded));
        }
    }

    @Test
    public void harvestingWithoutTheTaughtConstructDoesNotPass() {
        for (int i = 0; i < METHOD_LEVEL_COUNT; i++) {
            LevelDef level = LevelCatalog.get(FIRST_METHOD_LEVEL + i);
            FarmWorld world = level.createWorld();
            // Complete the farm independently of any program, then check the lesson requirement.
            for (int y = 0; y < world.height; y++) {
                for (int x = 0; x < world.width; x++) {
                    world.walkTo(world.farmer, x, y);
                    if (world.isRipe(world.farmer)) world.harvest(world.farmer);
                    else if (world.isDry(world.farmer)) world.water(world.farmer);
                }
            }
            assertNotNull(level.id, level.check(world, new Block()));
        }
    }

    @Test
    public void unusedHelpersCannotSatisfyTheLessons() {
        Block[] solutions = {reusableRows(), returnedCount(), parameterRows(), recursiveWalk()};
        for (int i = 0; i < solutions.length; i++) {
            LevelDef level = LevelCatalog.get(FIRST_METHOD_LEVEL + i);
            FarmWorld world = level.createWorld();
            for (int y = 0; y < world.height; y++) {
                for (int x = 0; x < world.width; x++) {
                    world.walkTo(world.farmer, x, y);
                    if (world.isRipe(world.farmer)) world.harvest(world.farmer);
                    else if (world.isDry(world.farmer)) world.water(world.farmer);
                }
            }
            Block unused = solutions[i];
            while (unused.size() > 0) unused.remove(0);
            assertNotNull(level.id, level.check(world, unused));
        }
    }

    @Test
    public void catalogKeepsOldSaveIdsAndAddsUniqueLevels() {
        Set<String> ids = new HashSet<>();
        assertEquals(56, LevelCatalog.all().size());
        for (int i = 0; i < LevelCatalog.all().size(); i++) {
            LevelDef level = LevelCatalog.get(i);
            assertEquals(String.format(java.util.Locale.ROOT, "level_%02d", i + 1), level.id);
            assertTrue(ids.add(level.id));
        }
    }
}
