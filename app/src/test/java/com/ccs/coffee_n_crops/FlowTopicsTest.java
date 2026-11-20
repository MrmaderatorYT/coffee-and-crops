package com.ccs.coffee_n_crops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.farm.WorldClock;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.BreakStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.CompoundAssign;
import com.ccs.coffee_n_crops.lang.ast.stmt.ContinueStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.DecrementStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.DoWhileStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ReturnStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.SwitchStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.TryStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.ast.stmt.WhileStmt;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.check.Validator;
import com.ccs.coffee_n_crops.lang.interp.DebugController;
import com.ccs.coffee_n_crops.lang.interp.ExecListener;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.print.JavaPrinter;
import com.ccs.coffee_n_crops.lang.runtime.NativeRegistry;
import com.ccs.coffee_n_crops.lang.serial.AstJson;
import com.ccs.coffee_n_crops.lang.types.JType;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Runtime, scope and save regressions for the loop and assignment lessons. */
public final class FlowTopicsTest {
    @Test(timeout = 2000)
    public void doWhileRunsOnceEvenWhenFalseAndHighlightsConditionAfterContinue() throws Exception {
        Block program = new Block();
        program.add(new VarDecl(JType.INT, "count", Ast.lit(2)));
        DoWhileStmt loop = new DoWhileStmt(Ast.lit(false));
        loop.body.add(new DecrementStmt("count"));
        loop.body.add(new CompoundAssign("count", Ast.lit(5)));
        loop.body.add(new ContinueStmt());
        program.add(loop);
        WhileStmt enclosing = new WhileStmt(Ast.lit(true));
        enclosing.body.add(new BreakStmt());
        program.add(enclosing);

        Block restored = AstJson.blockFrom(new JSONObject(AstJson.toJson(program).toString()));
        assertEquals(JavaPrinter.toSource(program), JavaPrinter.toSource(restored));
        assertTrue(JavaPrinter.toSource(restored).contains("} while (false);"));
        assertNull(Validator.check(restored));
        List<Integer> entered = new ArrayList<>();
        assertEquals(6, run(restored, entered).readVariable("count").asInt());
        DoWhileStmt reloadedLoop = (DoWhileStmt) restored.get(1);
        int condition = entered.indexOf(reloadedLoop.conditionLine.id);
        assertTrue(condition > entered.indexOf(reloadedLoop.body.get(2).id));
    }

    @Test(timeout = 2000)
    public void continueCrossesSwitchAndRunsFinallyAndForUpdate() throws Exception {
        Block program = new Block();
        program.add(new VarDecl(JType.INT, "count", Ast.lit(0)));
        ForStmt loop = Ast.forCount("i", 4);
        TryStmt guarded = new TryStmt();
        SwitchStmt branch = new SwitchStmt(new VarRef("i"));
        branch.addCase(0).body.add(new ContinueStmt());
        branch.addDefault().add(new BreakStmt());
        guarded.tryBlock.add(branch);
        guarded.addFinally().add(new CompoundAssign("count", Ast.lit(10)));
        loop.body.add(guarded);
        loop.body.add(new CompoundAssign("count", Ast.lit(1)));
        program.add(loop);

        assertNull(Validator.check(program));
        assertEquals(43, run(program, new ArrayList<>()).readVariable("count").asInt());
    }

    @Test
    public void aFinallyReturnCancelsBreakWhenDetectingUnreachableCode() {
        Block program = new Block();
        WhileStmt loop = new WhileStmt(Ast.lit(true));
        TryStmt guarded = new TryStmt();
        guarded.tryBlock.add(new BreakStmt());
        guarded.addFinally().add(new ReturnStmt());
        loop.body.add(guarded);
        program.add(loop);
        program.add(Ast.harvest());

        assertEquals("unreachable statement", Validator.check(program).message);
    }

    @Test
    public void invalidJumpAndLocalInDoConditionGetJavaDiagnostics() {
        Block program = new Block();
        program.add(new BreakStmt());
        assertEquals("break outside switch or loop", Validator.check(program).message);
        program.remove(0);
        program.add(new ContinueStmt());
        assertEquals("continue outside of loop", Validator.check(program).message);
        program.remove(0);
        DoWhileStmt loop = new DoWhileStmt(new VarRef("again"));
        loop.body.add(new VarDecl(JType.BOOLEAN, "again", Ast.lit(false)));
        program.add(loop);
        assertEquals("cannot find symbol: variable again", Validator.check(program).message);
        assertEquals(loop.conditionLine, Validator.check(program).node);
    }

    private static ExecutionContext run(Block program, List<Integer> entered) throws InterruptedException {
        FarmWorld world = new FarmWorld(new String[]{"."}, 0, 0);
        ExecListener listener = new ExecListener() {
            @Override public void onNodeEnter(int farmerId, int nodeId) { entered.add(nodeId); }
            @Override public void onFinished(int farmerId) { }
            @Override public void onError(int farmerId, int nodeId, String message) {
                throw new AssertionError(message);
            }
        };
        ExecutionContext context = new ExecutionContext(world.farmer, world, new DebugController(),
                new WorldClock(0), NativeRegistry.STANDARD, listener);
        program.exec(context);
        return context;
    }
}
