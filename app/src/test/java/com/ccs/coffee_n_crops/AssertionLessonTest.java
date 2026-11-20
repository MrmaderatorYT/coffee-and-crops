package com.ccs.coffee_n_crops;

import com.ccs.coffee_n_crops.farm.Direction;
import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.farm.WorldClock;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.MethodDecl;
import com.ccs.coffee_n_crops.lang.ast.expr.UserCall;
import com.ccs.coffee_n_crops.lang.ast.stmt.AssertStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ReturnStmt;
import com.ccs.coffee_n_crops.lang.check.Validator;
import com.ccs.coffee_n_crops.lang.interp.DebugController;
import com.ccs.coffee_n_crops.lang.interp.ExecListener;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.interp.ScriptError;
import com.ccs.coffee_n_crops.lang.print.JavaPrinter;
import com.ccs.coffee_n_crops.lang.runtime.NativeRegistry;
import com.ccs.coffee_n_crops.lang.serial.AstJson;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.progress.LevelCatalog;
import com.ccs.coffee_n_crops.progress.LevelDef;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** Runtime behavior and saved solution for the assertions lesson. */
public final class AssertionLessonTest {
    private static final int ASSERTION_LEVEL_INDEX = 42;

    @Test public void savedAssertionRunsAndCompletesTheLesson() throws Exception {
        LevelDef level = LevelCatalog.get(ASSERTION_LEVEL_INDEX);
        Block program = level.createProgram();
        ForStmt loop = Ast.forCount("i", 3);
        loop.body.add(new AssertStmt(Ast.isRipe(), Ast.lit("Expected ripe wheat")));
        loop.body.add(Ast.harvest());
        loop.body.add(Ast.move(Direction.EAST));
        program.add(loop);

        Block saved = AstJson.blockFrom(new JSONObject(AstJson.toJson(program).toString()));
        assertEquals(JavaPrinter.toSource(program), JavaPrinter.toSource(saved));
        assertTrue(JavaPrinter.toSource(saved).contains("assert farmer.isRipe() : \"Expected ripe wheat\";"));
        assertNull(Validator.check(saved));
        FarmWorld world = level.createWorld();
        run(saved, world);
        assertNull(level.check(world, saved));
        assertNotNull(level.check(world, program));
    }

    @Test public void failureEvaluatesTheMessageAndStopsBeforeTheNextStatement() throws Exception {
        Block program = new Block();
        MethodDecl detail = new MethodDecl("diagnose", JType.STRING);
        detail.body.add(Ast.harvest());
        detail.body.add(new ReturnStmt(JType.STRING, Ast.lit("Check failed")));
        program.addMethod(detail);
        program.add(new AssertStmt(Ast.lit(true), new UserCall(detail)));
        program.add(new AssertStmt(Ast.isRipe(), null));
        program.add(new AssertStmt(Ast.lit(false), new UserCall(detail)));
        program.add(Ast.move(Direction.EAST));
        assertNull(Validator.check(program));

        FarmWorld world = new FarmWorld(new String[]{"W."}, 0, 0);
        ScriptError error = assertThrows(ScriptError.class, () -> run(program, world));
        assertEquals("AssertionError: Check failed", error.getMessage());
        assertEquals(1, world.wheatHarvested());
        assertEquals(0, world.farmer.pose().x);
    }

    @Test public void conditionRequiresBooleanAndFailureCanOmitTheMessage() throws Exception {
        Block program = new Block();
        AssertStmt assertion = new AssertStmt(Ast.lit(1), null);
        program.add(assertion);
        assertEquals("incompatible types: int cannot be converted to boolean", Validator.check(program).message);
        assertion.condition.set(Ast.lit(false));
        assertNull(Validator.check(program));
        FarmWorld world = new FarmWorld(new String[]{"W"}, 0, 0);
        assertEquals("AssertionError", assertThrows(ScriptError.class, () -> run(program, world)).getMessage());
    }

    private static void run(Block program, FarmWorld world) throws InterruptedException {
        ExecListener listener = new ExecListener() {
            @Override public void onNodeEnter(int farmerId, int nodeId) { }
            @Override public void onFinished(int farmerId) { }
            @Override public void onError(int farmerId, int nodeId, String message) {
                throw new AssertionError(message);
            }
        };
        ExecutionContext context = new ExecutionContext(world.farmer, world, new DebugController(),
                new WorldClock(0), NativeRegistry.STANDARD, listener);
        program.exec(context);
    }
}
