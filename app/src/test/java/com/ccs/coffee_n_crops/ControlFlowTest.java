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
import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.ast.expr.Binary;
import com.ccs.coffee_n_crops.lang.ast.expr.MethodCall;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.ExprStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForEachStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.IfStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.SwitchStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.WhileStmt;
import com.ccs.coffee_n_crops.lang.check.ScopeResolver;
import com.ccs.coffee_n_crops.lang.check.Validator;
import com.ccs.coffee_n_crops.lang.interp.ExecListener;
import com.ccs.coffee_n_crops.lang.interp.Interpreter;
import com.ccs.coffee_n_crops.lang.print.JavaPrinter;
import com.ccs.coffee_n_crops.lang.serial.AstJson;
import com.ccs.coffee_n_crops.progress.LevelCatalog;
import com.ccs.coffee_n_crops.progress.LevelDef;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** while, for-each, else if and switch: the intended solutions of levels 4-6 and their edge cases. */
public class ControlFlowTest {

    /** Runs a program to the end with no delays; returns the uncaught error, or null. */
    private static String run(Block program, FarmWorld world, List<Integer> entered) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        String[] error = new String[1];
        new Interpreter(new WorldClock(0)).start(program, world, new ExecListener() {
            @Override
            public void onNodeEnter(int farmerId, int nodeId) {
                if (entered != null) entered.add(nodeId);
            }

            @Override
            public void onFinished(int farmerId) {
                done.countDown();
            }

            @Override
            public void onError(int farmerId, int nodeId, String message) {
                error[0] = message;
                done.countDown();
            }
        }, false);
        assertTrue("program did not finish", done.await(5, TimeUnit.SECONDS));
        return error[0];
    }

    private static Block program(Stmt... statements) {
        Block program = new Block();
        for (Stmt s : statements) program.add(s);
        return program;
    }

    /** Level 4: while there is farm ahead, harvest if ripe, else water if dry, then move on. */
    private static Block tendRow(boolean guardWatering) {
        WhileStmt loop = new WhileStmt(Ast.canMove(Direction.EAST));
        IfStmt check = Ast.ifStmt(Ast.isRipe(), !guardWatering);
        check.thenBlock.add(Ast.harvest());
        if (guardWatering) {
            check.addElseIf(Ast.isDry()).block.add(Ast.water());
        } else {
            check.elseBlock().add(Ast.water());
        }
        loop.body.add(check);
        loop.body.add(Ast.move(Direction.EAST));
        return program(loop);
    }

    /** Level 5: one lap, two tiles per side. */
    private static Block lap() {
        ForEachStmt each = new ForEachStmt("d");
        ForStmt side = Ast.forCount("i", 2);
        side.body.add(Ast.harvest());
        ExprStmt move = Ast.move(Direction.EAST);
        ((MethodCall) move.expr).args.get(0).set(new VarRef("d"));
        side.body.add(move);
        each.body.add(side);
        return program(each);
    }

    /** Level 6: act on the position in the repeating pattern. */
    private static Block byPattern() {
        ForStmt loop = Ast.forCount("i", 9);
        SwitchStmt sw = Ast.switchStmt(new Binary(Binary.Op.MOD, new VarRef("i"), Ast.lit(3)));
        sw.cases().get(0).body.add(Ast.harvest());
        sw.cases().get(1).body.add(Ast.water());
        loop.body.add(sw);
        loop.body.add(Ast.move(Direction.EAST));
        return program(loop);
    }

    @Test
    public void level4NeedsTheElseIf() throws Exception {
        LevelDef level = LevelCatalog.get(3);

        FarmWorld world = level.createWorld();
        Block solution = tendRow(true);
        assertNull(Validator.check(solution));
        assertNull(run(solution, world, null));
        assertEquals(4, world.wheatHarvested());
        assertEquals(2, world.watered());
        assertEquals(0, world.ruined());
        assertEquals("the loop stops on the last tile", 8, world.farmer.pose().x);
        assertNull(level.check(world, solution));

        // A plain else waters the already-watered plants too, and drowns them.
        Block careless = tendRow(false);
        FarmWorld drowned = level.createWorld();
        assertNull(run(careless, drowned, null));
        assertEquals(2, drowned.ruined());
        assertNotNull(level.check(drowned, careless));
    }

    @Test
    public void elseIfConditionsAreTestedInOrderUntilOneMatches() throws Exception {
        // On a dry young plant: isRipe() is false, so the else-if arm is tested and taken.
        IfStmt check = Ast.ifStmt(Ast.isRipe(), true);
        check.thenBlock.add(Ast.harvest());
        IfStmt.Branch dry = check.addElseIf(Ast.isDry());
        dry.block.add(Ast.water());
        check.elseBlock().add(Ast.move(Direction.EAST));

        FarmWorld world = new FarmWorld(new String[]{"g."}, 0, 0);
        List<Integer> entered = new ArrayList<>();
        assertNull(run(program(check), world, entered));
        assertEquals(1, world.watered());
        assertEquals("the else arm is skipped", 0, world.farmer.pose().x);
        assertEquals("debugger stops: the if, then the else-if",
                Arrays.asList(check.id, dry.id), entered.subList(0, 2));

        // On ripe wheat the first arm wins and the else-if is never tested.
        FarmWorld ripe = new FarmWorld(new String[]{"W."}, 0, 0);
        entered.clear();
        assertNull(run(program(check), ripe, entered));
        assertEquals(1, ripe.wheatHarvested());
        assertFalse(entered.contains(dry.id));
    }

    @Test
    public void level5IsALapWithForEach() throws Exception {
        LevelDef level = LevelCatalog.get(4);
        FarmWorld world = level.createWorld();
        Block solution = lap();
        assertNull(Validator.check(solution));
        assertNull(run(solution, world, null));
        assertEquals(8, world.wheatHarvested());
        assertEquals("back at the start", 0, world.farmer.pose().x);
        assertEquals(2, world.farmer.pose().y);
        assertNull(level.check(world, solution));

        ForEachStmt each = (ForEachStmt) solution.get(0);
        assertEquals("d is visible inside the loop", 1, ScopeResolver.visibleAt(each.body, 0).size());
        assertTrue("and gone after it", ScopeResolver.visibleAt(solution, 1).isEmpty());
    }

    @Test
    public void level6IsSolvedBySwitchingOnThePattern() throws Exception {
        LevelDef level = LevelCatalog.get(5);
        FarmWorld world = level.createWorld();
        Block solution = byPattern();
        assertNull(Validator.check(solution));
        assertNull(run(solution, world, null));
        assertEquals(3, world.wheatHarvested());
        assertEquals(3, world.watered());
        assertEquals(0, world.ruined());
        assertNull(level.check(world, solution));
    }

    @Test
    public void switchFallsToDefaultAndRejectsDuplicateLabels() throws Exception {
        // switch (7) { case 0: harvest; case 1: water; default: move }
        SwitchStmt sw = Ast.switchStmt(Ast.lit(7));
        sw.cases().get(0).body.add(Ast.harvest());
        sw.cases().get(1).body.add(Ast.water());
        sw.defaultBlock().add(Ast.move(Direction.EAST));
        FarmWorld world = new FarmWorld(new String[]{"W."}, 0, 0);
        assertNull(run(program(sw), world, null));
        assertEquals(0, world.wheatHarvested());
        assertEquals(1, world.farmer.pose().x);

        // With no default and no matching case, nothing runs.
        sw.removeDefault();
        world.reset();
        assertNull(run(program(sw), world, null));
        assertEquals(0, world.farmer.pose().x);

        assertEquals(2, sw.nextLabel());
        sw.addCase(1);
        assertEquals("duplicate case label: 1", Validator.check(program(sw)).message);
    }

    @Test
    public void newStatementsPrintAsJavaAndSurviveSaving() throws Exception {
        String expected = ""
                + "void run(Farmhand farmer) {\n"
                + "    while (farmer.canMove(Direction.EAST)) {\n"
                + "        if (farmer.isRipe()) {\n"
                + "            farmer.harvest();\n"
                + "        } else if (farmer.isDry()) {\n"
                + "            farmer.water();\n"
                + "        }\n"
                + "        farmer.move(Direction.EAST);\n"
                + "    }\n"
                + "}\n";
        assertEquals(expected, JavaPrinter.toSource(tendRow(true)));

        expected = ""
                + "void run(Farmhand farmer) {\n"
                + "    for (Direction d : Direction.values()) {\n"
                + "        for (int i = 0; i < 2; i++) {\n"
                + "            farmer.harvest();\n"
                + "            farmer.move(d);\n"
                + "        }\n"
                + "    }\n"
                + "}\n";
        assertEquals(expected, JavaPrinter.toSource(lap()));

        expected = ""
                + "void run(Farmhand farmer) {\n"
                + "    for (int i = 0; i < 9; i++) {\n"
                + "        switch (i % 3) {\n"
                + "            case 0:\n"
                + "                farmer.harvest();\n"
                + "                break;\n"
                + "            case 1:\n"
                + "                farmer.water();\n"
                + "                break;\n"
                + "            default:\n"
                + "                break;\n"
                + "        }\n"
                + "        farmer.move(Direction.EAST);\n"
                + "    }\n"
                + "}\n";
        assertEquals(expected, JavaPrinter.toSource(byPattern()));

        for (Block program : new Block[]{tendRow(true), tendRow(false), lap(), byPattern()}) {
            String json = AstJson.toJson(program).toString();
            Block loaded = AstJson.blockFrom(new JSONObject(json));
            assertEquals(JavaPrinter.toSource(program), JavaPrinter.toSource(loaded));
        }
    }
}
