package com.ccs.coffee_n_crops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ccs.coffee_n_crops.farm.Direction;
import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.farm.WorldClock;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.ast.expr.Binary;
import com.ccs.coffee_n_crops.lang.ast.expr.StrEquals;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.IfStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.SwitchStmt;
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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** double, String and switch on a String: the intended solutions of levels 10-11 and their edge cases. */
public class DoubleAndStringTest {

    /** Runs a program to the end with no delays; returns the uncaught error, or null. */
    private static String run(Block program, FarmWorld world) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        String[] error = new String[1];
        new Interpreter(new WorldClock(0)).start(program, world, new ExecListener() {
            @Override
            public void onNodeEnter(int farmerId, int nodeId) {
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

    /** Level 10: water along the row; refill when told to by the flags. */
    private static Block waterRow(boolean checkTank, boolean alwaysRefill) {
        WhileStmt loop = new WhileStmt(Ast.canMove(Direction.EAST));
        if (checkTank) {
            IfStmt low = Ast.ifStmt(Ast.waterBelow(0.5), false);
            low.thenBlock.add(Ast.refill());
            loop.body.add(low);
        }
        if (alwaysRefill) loop.body.add(Ast.refill());
        loop.body.add(Ast.water());
        loop.body.add(Ast.move(Direction.EAST));
        return program(loop);
    }

    /** Level 11 with a switch on the crop name. */
    private static Block tendBySwitch() {
        WhileStmt loop = new WhileStmt(Ast.canMove(Direction.EAST));
        SwitchStmt sw = Ast.switchOnCrop();
        sw.cases().get(0).body.add(Ast.harvest());
        sw.cases().get(1).body.add(Ast.water());
        loop.body.add(sw);
        loop.body.add(Ast.move(Direction.EAST));
        return program(loop);
    }

    /** Level 11 with a String variable and equals(). */
    private static Block tendByEquals() {
        WhileStmt loop = new WhileStmt(Ast.canMove(Direction.EAST));
        VarDecl crop = new VarDecl(JType.STRING, "crop", Ast.getCrop());
        IfStmt check = Ast.ifStmt(new StrEquals(
                new VarRef("crop"), Ast.lit("wheat")), false);
        check.thenBlock.add(Ast.harvest());
        check.addElseIf(new StrEquals(
                new VarRef("crop"), Ast.lit("sprout"))).block.add(Ast.water());
        loop.body.add(crop);
        loop.body.add(check);
        loop.body.add(Ast.move(Direction.EAST));
        return program(loop);
    }

    @Test
    public void level10NeedsTheTankCheck() throws Exception {
        LevelDef level = LevelCatalog.get(9);

        FarmWorld world = level.createWorld();
        Block solution = waterRow(true, false);
        assertNull(Validator.check(solution));
        assertNull(run(solution, world));
        assertEquals(8, world.watered());
        assertEquals(2, world.refills());
        assertEquals(0.5, world.tankLevel(), 0.0);
        assertNull(level.check(world, solution));

        // Never refilling: the fourth watering finds the tank empty.
        FarmWorld dry = level.createWorld();
        assertEquals("EmptyTankException: the tank holds 0.0 L but watering needs 0.5 L",
                run(waterRow(false, false), dry));
        assertEquals(3, dry.watered());

        // Refilling before every watering works, but wastes six refills.
        Block wasteful = waterRow(false, true);
        FarmWorld wasted = level.createWorld();
        assertNull(run(wasteful, wasted));
        assertEquals(8, wasted.watered());
        assertNotNull(level.check(wasted, wasteful));
    }

    @Test
    public void doubleArithmeticWidensIntsAndFollowsJava() throws Exception {
        // double amount = 1; amount = amount + 0.5; if (amount * 2 == 3.0) harvest
        VarDecl amount = new VarDecl(JType.DOUBLE, "amount", Ast.lit(1));
        Stmt add = Ast.assign(amount, new Binary(Binary.Op.ADD, JType.DOUBLE, new VarRef("amount"), Ast.lit(0.5)));
        Binary doubled = new Binary(Binary.Op.MUL, JType.DOUBLE, new VarRef("amount"), Ast.lit(2));
        IfStmt check = Ast.ifStmt(new Binary(Binary.Op.EQ, JType.DOUBLE, doubled, Ast.lit(3.0)), false);
        check.thenBlock.add(Ast.harvest());
        Block program = program(amount, add, check);

        FarmWorld world = new FarmWorld(new String[]{"W"}, 0, 0);
        assertNull(Validator.check(program));
        assertNull(run(program, world));
        assertEquals(1, world.wheatHarvested());
        assertEquals("if (amount * 2 == 3.0) {", JavaPrinter.text(JavaPrinter.line(check)));

        // An int variable may be read where a double is wanted, but not the other way round.
        VarDecl steps = Ast.declare(JType.INT, "steps");
        VarDecl half = new VarDecl(JType.DOUBLE, "half", new VarRef("steps"));
        assertNull(Validator.check(program(steps, half)));
        VarDecl level = Ast.declare(JType.DOUBLE, "level");
        VarDecl whole = new VarDecl(JType.INT, "whole", new VarRef("level"));
        assertEquals("incompatible types: double cannot be converted to int",
                Validator.check(program(level, whole)).message);
    }

    @Test
    public void level11IsSolvedBySwitchOrByEquals() throws Exception {
        LevelDef level = LevelCatalog.get(10);
        for (Block solution : new Block[]{tendBySwitch(), tendByEquals()}) {
            FarmWorld world = level.createWorld();
            assertNull(Validator.check(solution));
            assertNull(run(solution, world));
            assertEquals(4, world.wheatHarvested());
            assertEquals(3, world.watered());
            assertEquals(0, world.ruined());
            assertNull(level.check(world, solution));
        }
    }

    @Test
    public void stringSwitchUsesDefaultAndRejectsDuplicateLabels() throws Exception {
        // On grass neither case matches, so default runs.
        SwitchStmt sw = Ast.switchOnCrop();
        sw.cases().get(0).body.add(Ast.harvest());
        sw.defaultBlock().add(Ast.move(Direction.EAST));
        FarmWorld world = new FarmWorld(new String[]{".W"}, 0, 0);
        assertNull(run(program(sw), world));
        assertEquals(1, world.farmer.pose().x);
        assertEquals(0, world.wheatHarvested());

        assertEquals("the next unused crop name", "watered", sw.nextLabel());
        sw.addCase("wheat");
        assertEquals("duplicate case label: \"wheat\"", Validator.check(program(sw)).message);
    }

    @Test
    public void doublesAndStringsPrintAsJavaAndSurviveSaving() throws Exception {
        String expected = ""
                + "void run(Farmhand farmer) {\n"
                + "    while (farmer.canMove(Direction.EAST)) {\n"
                + "        if (farmer.getWater() < 0.5) {\n"
                + "            farmer.refill();\n"
                + "        }\n"
                + "        farmer.water();\n"
                + "        farmer.move(Direction.EAST);\n"
                + "    }\n"
                + "}\n";
        assertEquals(expected, JavaPrinter.toSource(waterRow(true, false)));

        expected = ""
                + "void run(Farmhand farmer) {\n"
                + "    while (farmer.canMove(Direction.EAST)) {\n"
                + "        switch (farmer.getCrop()) {\n"
                + "            case \"wheat\":\n"
                + "                farmer.harvest();\n"
                + "                break;\n"
                + "            case \"sprout\":\n"
                + "                farmer.water();\n"
                + "                break;\n"
                + "            default:\n"
                + "                break;\n"
                + "        }\n"
                + "        farmer.move(Direction.EAST);\n"
                + "    }\n"
                + "}\n";
        assertEquals(expected, JavaPrinter.toSource(tendBySwitch()));

        String equalsLine = JavaPrinter.toSource(tendByEquals());
        assertTrue(equalsLine, equalsLine.contains("String crop = farmer.getCrop();"));
        assertTrue(equalsLine, equalsLine.contains("if (crop.equals(\"wheat\")) {"));
        assertTrue(equalsLine, equalsLine.contains("} else if (crop.equals(\"sprout\")) {"));

        for (Block program : new Block[]{waterRow(true, false), tendBySwitch(), tendByEquals()}) {
            Block loaded = AstJson.blockFrom(new JSONObject(AstJson.toJson(program).toString()));
            assertEquals(JavaPrinter.toSource(program), JavaPrinter.toSource(loaded));
        }
    }
}
