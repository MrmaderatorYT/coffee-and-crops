package com.ccs.coffee_n_crops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ccs.coffee_n_crops.farm.Direction;
import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.farm.TileState;
import com.ccs.coffee_n_crops.farm.WorldClock;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.expr.Binary;
import com.ccs.coffee_n_crops.lang.ast.expr.Not;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.IfStmt;
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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Variables, conditionals, the validator and the save format, on the plain JVM. */
public class LanguageTest {

    /** Runs a program to the end with no delays and returns the uncaught error, or null. */
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

    /** A loop whose bound is the variable {@code steps} instead of a number. */
    private static ForStmt loopOver(String var, String bound) {
        ForStmt loop = Ast.forCount(var, 0);
        ((Binary) loop.cond.get()).right.set(new VarRef(bound));
        return loop;
    }

    /** The intended Level 2 solution: one variable drives both rows. */
    private static Block twoRows() {
        Block program = new Block();
        VarDecl steps = Ast.declare(JType.INT, "steps");
        steps.init.set(Ast.lit(5));
        program.add(steps);

        ForStmt east = loopOver("i", "steps");
        east.body.add(Ast.harvest());
        east.body.add(Ast.move(Direction.EAST));
        program.add(east);
        program.add(Ast.move(Direction.SOUTH));

        ForStmt west = loopOver("i", "steps");
        west.body.add(Ast.move(Direction.WEST));
        west.body.add(Ast.harvest());
        program.add(west);
        return program;
    }

    /** The intended Level 3 solution: harvest if ripe, otherwise water. */
    private static Block ripeOrWater() {
        ForStmt loop = Ast.forCount("i", 6);
        IfStmt check = Ast.ifStmt(Ast.isRipe(), true);
        check.thenBlock.add(Ast.harvest());
        check.elseBlock().add(Ast.water());
        loop.body.add(check);
        loop.body.add(Ast.move(Direction.EAST));
        Block program = new Block();
        program.add(loop);
        return program;
    }

    @Test
    public void level2IsSolvedWithAVariableAndRejectedWithoutOne() throws Exception {
        LevelDef level = LevelCatalog.get(1);

        FarmWorld world = level.createWorld();
        Block withVariable = twoRows();
        assertNull(Validator.check(withVariable));
        assertNull(run(withVariable, world));
        assertEquals(10, world.wheatHarvested());
        assertNull(level.check(world, withVariable));

        // Same moves with plain numbers: the wheat is in, but the level's point is missed.
        Block numbersOnly = twoRows();
        numbersOnly.remove(0);
        ((Binary) ((ForStmt) numbersOnly.get(0)).cond.get()).right.set(Ast.lit(5));
        ((Binary) ((ForStmt) numbersOnly.get(2)).cond.get()).right.set(Ast.lit(5));
        FarmWorld again = level.createWorld();
        assertNull(run(numbersOnly, again));
        assertEquals(10, again.wheatHarvested());
        assertNotNull(level.check(again, numbersOnly));
    }

    @Test
    public void level3NeedsTheIfElse() throws Exception {
        LevelDef level = LevelCatalog.get(2);

        FarmWorld world = level.createWorld();
        Block solution = ripeOrWater();
        assertNull(run(solution, world));
        assertEquals(4, world.wheatHarvested());
        assertEquals(2, world.watered());
        assertEquals(0, world.ruined());
        assertNull(level.check(world, solution));

        // Harvesting everything blindly ruins the two young plants.
        ForStmt blind = Ast.forCount("i", 6);
        blind.body.add(Ast.harvest());
        blind.body.add(Ast.move(Direction.EAST));
        Block program = new Block();
        program.add(blind);
        FarmWorld ruined = level.createWorld();
        assertNull(run(program, ruined));
        assertEquals(2, ruined.ruined());
        assertNotNull(level.check(ruined, program));
    }

    @Test
    public void assignmentAndLogicEvaluateLikeJava() throws Exception {
        // int count = 0; for (i < 4) { if (!(count == 2) && true) { harvest } count = count + 1; move }
        Block program = new Block();
        VarDecl count = Ast.declare(JType.INT, "count");
        program.add(count);
        ForStmt loop = Ast.forCount("i", 4);
        Binary isTwo = new Binary(Binary.Op.EQ, new VarRef("count"), Ast.lit(2));
        IfStmt skipThird = Ast.ifStmt(new Binary(Binary.Op.AND, new Not(isTwo), Ast.lit(true)), false);
        skipThird.thenBlock.add(Ast.harvest());
        loop.body.add(skipThird);
        loop.body.add(Ast.assign(count, new Binary(Binary.Op.ADD, new VarRef("count"), Ast.lit(1))));
        loop.body.add(Ast.move(Direction.EAST));
        program.add(loop);

        FarmWorld world = new FarmWorld(new String[]{"WWWW."}, 0, 0);
        assertNull(Validator.check(program));
        assertNull(run(program, world));
        assertEquals("the third tile is skipped", 3, world.wheatHarvested());
        assertEquals(TileState.WHEAT_RIPE, world.tile(2, 0).state);
    }

    @Test
    public void printerAddsParenthesesOnlyWhereJavaNeedsThem() {
        // (count + 1) * 2 < 10 && !(count == 2 || done)
        Binary sum = new Binary(Binary.Op.ADD, new VarRef("count"), Ast.lit(1));
        Binary product = new Binary(Binary.Op.MUL, sum, Ast.lit(2));
        Binary small = new Binary(Binary.Op.LT, product, Ast.lit(10));
        Binary either = new Binary(Binary.Op.OR,
                new Binary(Binary.Op.EQ, new VarRef("count"), Ast.lit(2)), new VarRef("done"));
        IfStmt statement = Ast.ifStmt(new Binary(Binary.Op.AND, small, new Not(either)), true);

        assertEquals("if ((count + 1) * 2 < 10 && !(count == 2 || done)) {",
                JavaPrinter.text(JavaPrinter.line(statement)));

        // a - (b - c) keeps its parentheses; (a - b) - c does not need them.
        Binary right = new Binary(Binary.Op.SUB, Ast.lit(9),
                new Binary(Binary.Op.SUB, Ast.lit(4), Ast.lit(1)));
        Binary left = new Binary(Binary.Op.SUB,
                new Binary(Binary.Op.SUB, Ast.lit(9), Ast.lit(4)), Ast.lit(1));
        assertEquals("int a = 9 - (4 - 1);",
                JavaPrinter.text(JavaPrinter.line(new VarDecl(JType.INT, "a", right))));
        assertEquals("int b = 9 - 4 - 1;",
                JavaPrinter.text(JavaPrinter.line(new VarDecl(JType.INT, "b", left))));
    }

    @Test
    public void validatorReportsCompileErrorsLikeJavac() {
        // The declaration was deleted but the loop still reads the variable.
        Block missing = twoRows();
        missing.remove(0);
        Validator.Problem problem = Validator.check(missing);
        assertNotNull(problem);
        assertEquals("cannot find symbol: variable steps", problem.message);
        assertEquals(missing.get(0), problem.statement);

        // A variable used above its declaration is just as undefined.
        Block tooLate = twoRows();
        tooLate.add(tooLate.remove(0));
        assertEquals("cannot find symbol: variable steps", Validator.check(tooLate).message);

        Block twice = new Block();
        twice.add(Ast.declare(JType.INT, "steps"));
        twice.add(Ast.declare(JType.INT, "steps"));
        assertEquals("variable steps is already defined in method run(Farmhand)",
                Validator.check(twice).message);

        // A variable declared inside a loop body does not exist after the loop.
        Block escaped = new Block();
        ForStmt loop = Ast.forCount("i", 2);
        VarDecl inner = Ast.declare(JType.INT, "count");
        loop.body.add(inner);
        escaped.add(loop);
        escaped.add(Ast.assign(inner, Ast.lit(1)));
        assertEquals("cannot find symbol: variable count", Validator.check(escaped).message);
    }

    @Test
    public void scopeResolverFollowsJavaScoping() {
        Block program = twoRows();
        ForStmt east = (ForStmt) program.get(1);

        assertTrue("nothing is declared before the first line",
                ScopeResolver.visibleAt(program, 0).isEmpty());
        assertEquals(names("steps", "i"), names(ScopeResolver.visibleAt(east.body, 0)));
        assertEquals("the loop variable is gone after the loop",
                names("steps"), names(ScopeResolver.visibleAt(program, 2)));
        assertEquals("the condition sees the loop variable",
                names("i", "steps"), names(ScopeResolver.visibleAt(east.cond)));
        assertEquals("an initializer cannot see its own variable",
                names("steps"), names(ScopeResolver.visibleAt(east.init.init)));
    }

    @Test
    public void programsSurviveASaveAndLoad() throws Exception {
        for (Block program : new Block[]{twoRows(), ripeOrWater()}) {
            String json = AstJson.toJson(program).toString();
            Block loaded = AstJson.blockFrom(new JSONObject(json));
            assertEquals(JavaPrinter.toSource(program), JavaPrinter.toSource(loaded));
        }
    }

    private static Set<String> names(String... names) {
        return new HashSet<>(Arrays.asList(names));
    }

    private static Set<String> names(List<VarDecl> decls) {
        Set<String> out = new HashSet<>();
        for (VarDecl d : decls) out.add(d.name);
        return out;
    }
}
