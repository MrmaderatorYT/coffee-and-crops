package com.ccs.coffee_n_crops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.farm.WorldClock;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.ast.expr.Access;
import com.ccs.coffee_n_crops.lang.ast.expr.Binary;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForEachStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.IfStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ListAdd;
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

/** int[], int[][] and ArrayList: the intended solutions of levels 7-9 and their edge cases. */
public class CollectionsTest {

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

    /** {@code for (int var = 0; var < bound; var++)} */
    private static ForStmt loop(String var, Expr bound) {
        ForStmt loop = Ast.forCount(var, 0);
        ((Binary) loop.cond.get()).right.set(bound);
        return loop;
    }

    /** {@code if (read == 2) { harvest } else if (read == 1) { water }}, each arm walking there first. */
    private static IfStmt tend(Expr ripe, Expr young, String x, String y, boolean flyInside) {
        IfStmt check = Ast.ifStmt(Ast.eq(ripe, FarmWorld.SCAN_RIPE), false);
        IfStmt.Branch water = check.addElseIf(Ast.eq(young, FarmWorld.SCAN_YOUNG));
        if (flyInside) {
            check.thenBlock.add(Ast.walkTo(new VarRef(x), new VarRef(y)));
            water.block.add(Ast.walkTo(new VarRef(x), new VarRef(y)));
        }
        check.thenBlock.add(Ast.harvest());
        water.block.add(Ast.water());
        return check;
    }

    /** Level 7. */
    private static Block tendScannedRow(Expr bound) {
        ForStmt loop = loop("i", bound);
        loop.body.add(Ast.walkTo(new VarRef("i"), Ast.lit(0)));
        loop.body.add(tend(
                new Access(Access.Kind.INDEX, "row", new VarRef("i")),
                new Access(Access.Kind.INDEX, "row", new VarRef("i")), null, null, false));
        return program(Ast.scanRow("row"), loop);
    }

    /** Level 8. */
    private static Block tendScannedField() {
        ForStmt rows = loop("i", new Access(Access.Kind.GRID_ROWS, "field"));
        ForStmt cols = loop("j", new Access(Access.Kind.GRID_COLS, "field"));
        cols.body.add(tend(
                new Access(Access.Kind.GRID_INDEX, "field", new VarRef("i"), new VarRef("j")),
                new Access(Access.Kind.GRID_INDEX, "field", new VarRef("i"), new VarRef("j")),
                "j", "i", true));
        rows.body.add(cols);
        return program(Ast.scanField("field"), rows);
    }

    /** Level 9. */
    private static Block collectThenVisit() {
        ForStmt scan = loop("i", new Access(Access.Kind.LENGTH, "row"));
        IfStmt ripe = Ast.ifStmt(Ast.eq(new Access(Access.Kind.INDEX, "row", new VarRef("i")),
                FarmWorld.SCAN_RIPE), false);
        ripe.thenBlock.add(new ListAdd("list", new VarRef("i")));
        scan.body.add(ripe);

        ForEachStmt visit = new ForEachStmt(JType.INT, "cell", "list");
        visit.body.add(Ast.walkTo(new VarRef("cell"), Ast.lit(0)));
        visit.body.add(Ast.harvest());
        return program(Ast.scanRow("row"), Ast.newList("list"), scan, visit);
    }

    @Test
    public void level7ReadsTheScanByIndex() throws Exception {
        LevelDef level = LevelCatalog.get(6);
        FarmWorld world = level.createWorld();
        Block solution = tendScannedRow(new Access(Access.Kind.LENGTH, "row"));
        assertNull(Validator.check(solution));
        assertNull(run(solution, world));
        assertEquals(4, world.wheatHarvested());
        assertEquals(3, world.watered());
        assertEquals(0, world.ruined());
        assertNull(level.check(world, solution));
    }

    @Test
    public void readingPastTheEndFailsLikeJava() throws Exception {
        FarmWorld world = LevelCatalog.get(6).createWorld();
        // i < 10 on a 9-tile row: walkTo(9, 0) leaves the farm before row[9] is ever read.
        assertEquals("OutOfBoundsFarmException: the farmer tried to leave the farm at (9, 0)",
                run(tendScannedRow(Ast.lit(10)), world));

        // Reading alone: int[] row; if (row[9] == 2) ...
        IfStmt read = Ast.ifStmt(Ast.eq(new Access(Access.Kind.INDEX, "row", Ast.lit(9)), 2), false);
        world.reset();
        assertEquals("ArrayIndexOutOfBoundsException: Index 9 out of bounds for length 9",
                run(program(Ast.scanRow("row"), read), world));

        // list.get on an empty list
        IfStmt get = Ast.ifStmt(Ast.eq(new Access(Access.Kind.LIST_GET, "list", Ast.lit(0)), 2), false);
        world.reset();
        assertEquals("IndexOutOfBoundsException: Index 0 out of bounds for length 0",
                run(program(Ast.newList("list"), get), world));
    }

    @Test
    public void level8WalksAGridRowByRow() throws Exception {
        LevelDef level = LevelCatalog.get(7);
        FarmWorld world = level.createWorld();
        Block solution = tendScannedField();
        assertNull(Validator.check(solution));
        assertNull(run(solution, world));
        assertEquals(7, world.wheatHarvested());
        assertEquals(4, world.watered());
        assertEquals(0, world.ruined());
        assertNull(level.check(world, solution));

        int[][] scan = world.scanField();
        assertEquals("rows", 4, scan.length);
        assertEquals("columns", 5, scan[0].length);
    }

    @Test
    public void level9CollectsIntoAListThenIteratesIt() throws Exception {
        LevelDef level = LevelCatalog.get(8);
        FarmWorld world = level.createWorld();
        Block solution = collectThenVisit();
        assertNull(Validator.check(solution));
        assertNull(run(solution, world));
        assertEquals(5, world.wheatHarvested());
        assertEquals("the farmer ends on the last ripe tile", 12, world.farmer.pose().x);
        assertNull(level.check(world, solution));

        // Harvesting straight from the array gets the wheat in but skips the lesson.
        ForStmt direct = loop("i", new Access(Access.Kind.LENGTH, "row"));
        IfStmt ripe = Ast.ifStmt(Ast.eq(new Access(Access.Kind.INDEX, "row", new VarRef("i")), 2), false);
        ripe.thenBlock.add(Ast.walkTo(new VarRef("i"), Ast.lit(0)));
        ripe.thenBlock.add(Ast.harvest());
        direct.body.add(ripe);
        Block noList = program(Ast.scanRow("row"), direct);
        FarmWorld again = level.createWorld();
        assertNull(run(noList, again));
        assertEquals(5, again.wheatHarvested());
        assertNotNull(level.check(again, noList));
    }

    @Test
    public void validatorChecksCollectionVariables() {
        // row[i] with no row declared
        Block missing = tendScannedRow(Ast.lit(3));
        missing.remove(0);
        assertEquals("cannot find symbol: variable row", Validator.check(missing).message);

        // list.add on something that is an int[]
        Block wrongType = program(Ast.scanRow("row"), new ListAdd("row", Ast.lit(1)));
        assertEquals("incompatible types: int[] cannot be converted to ArrayList<Integer>",
                Validator.check(wrongType).message);

        // for-each over a variable that is not there any more
        Block gone = collectThenVisit();
        gone.remove(1);
        assertEquals("cannot find symbol: variable list", Validator.check(gone).message);
    }

    @Test
    public void collectionsPrintAsJavaAndSurviveSaving() throws Exception {
        String expected = ""
                + "void run(Farmhand farmer) {\n"
                + "    int[][] field = farmer.scanField();\n"
                + "    for (int i = 0; i < field.length; i++) {\n"
                + "        for (int j = 0; j < field[0].length; j++) {\n"
                + "            if (field[i][j] == 2) {\n"
                + "                farmer.walkTo(j, i);\n"
                + "                farmer.harvest();\n"
                + "            } else if (field[i][j] == 1) {\n"
                + "                farmer.walkTo(j, i);\n"
                + "                farmer.water();\n"
                + "            }\n"
                + "        }\n"
                + "    }\n"
                + "}\n";
        assertEquals(expected, JavaPrinter.toSource(tendScannedField()));

        expected = ""
                + "void run(Farmhand farmer) {\n"
                + "    int[] row = farmer.scanRow();\n"
                + "    ArrayList<Integer> list = new ArrayList<>();\n"
                + "    for (int i = 0; i < row.length; i++) {\n"
                + "        if (row[i] == 2) {\n"
                + "            list.add(i);\n"
                + "        }\n"
                + "    }\n"
                + "    for (int cell : list) {\n"
                + "        farmer.walkTo(cell, 0);\n"
                + "        farmer.harvest();\n"
                + "    }\n"
                + "}\n";
        assertEquals(expected, JavaPrinter.toSource(collectThenVisit()));

        Block[] programs = {tendScannedRow(new Access(Access.Kind.LENGTH, "row")),
                tendScannedField(), collectThenVisit()};
        for (Block program : programs) {
            Block loaded = AstJson.blockFrom(new JSONObject(AstJson.toJson(program).toString()));
            assertEquals(JavaPrinter.toSource(program), JavaPrinter.toSource(loaded));
        }
    }
}
