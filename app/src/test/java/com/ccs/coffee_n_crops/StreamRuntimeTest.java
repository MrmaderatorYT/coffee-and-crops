package com.ccs.coffee_n_crops;

import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.farm.WorldClock;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.AstWalk;
import com.ccs.coffee_n_crops.lang.ast.expr.Binary;
import com.ccs.coffee_n_crops.lang.ast.expr.Literal;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamCall;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamLambda;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamMethodRef;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.ExprStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ListAdd;
import com.ccs.coffee_n_crops.lang.ast.stmt.ReturnStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.StreamForEachStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.interp.DebugController;
import com.ccs.coffee_n_crops.lang.interp.ExecListener;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.interp.ScriptError;
import com.ccs.coffee_n_crops.lang.print.JavaPrinter;
import com.ccs.coffee_n_crops.lang.runtime.NativeRegistry;
import com.ccs.coffee_n_crops.lang.serial.AstJson;
import com.ccs.coffee_n_crops.lang.types.JType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** Checks stream runtime behavior that differs from evaluating an ordinary list operation. */
public final class StreamRuntimeTest {
    private static final String SOURCE = "stops";
    private static final String OUTPUT = "route";
    private static final String CELL = "cell";

    @Test public void anyMatchShortCircuitsAndDoesNotInvokeAnEmptyStream() throws Exception {
        Block program = stops(1, 0);
        StreamLambda condition = new StreamLambda(CELL, JType.BOOLEAN, new Binary(Binary.Op.GT,
                new Binary(Binary.Op.DIV, Ast.lit(1), new VarRef(CELL)), Ast.lit(0)));
        StreamCall some = new StreamCall(SOURCE, StreamCall.Terminal.ANY_MATCH, Collections.emptyList(), condition);
        program.add(new VarDecl(JType.BOOLEAN, "found", some));

        program.add(Ast.newList("empty"));
        StreamLambda never = new StreamLambda(CELL, JType.BOOLEAN, Ast.lit(true));
        program.add(new VarDecl(JType.BOOLEAN, "absent", new StreamCall("empty", StreamCall.Terminal.ANY_MATCH,
                Collections.emptyList(), never)));
        ExecutionContext context = run(program);

        assertTrue(context.readVariable("found").asBoolean());
        assertFalse(context.readVariable("absent").asBoolean());
        assertTrue(context.world.usedLessonNode(condition.id));
        assertFalse(context.world.usedLessonNode(never.id));
    }

    @Test public void lambdaReturnIsLocalAndItsStatementsAreSavedVisitedAndSteppedOnce() throws Exception {
        Block program = stops(1, 2);
        program.add(Ast.newList(OUTPUT));
        StreamLambda lambda = new StreamLambda(CELL);
        var guard = Ast.ifStmt(new Binary(Binary.Op.EQ, new VarRef(CELL), Ast.lit(1)), false);
        guard.thenBlock.add(new ReturnStmt());
        lambda.body.add(guard);
        lambda.body.add(new ListAdd(OUTPUT, new VarRef(CELL)));
        program.add(new StreamForEachStmt(new StreamCall(SOURCE, StreamCall.Terminal.FOR_EACH,
                Collections.emptyList(), lambda)));
        program.add(new ListAdd(OUTPUT, Ast.lit(3)));
        Block loaded = AstJson.blockFrom(new JSONObject(AstJson.toJson(program).toString()));
        assertEquals(JavaPrinter.toSource(program), JavaPrinter.toSource(loaded));

        StreamForEachStmt each = (StreamForEachStmt) loaded.get(4);
        int addInside = each.childBlocks().get(0).get(1).id;
        List<Integer> visited = new ArrayList<>();
        AstWalk.walk(loaded, node -> visited.add(node.id));
        assertEquals(1, Collections.frequency(visited, addInside));

        List<Integer> steps = new ArrayList<>();
        ExecutionContext context = run(loaded, steps);
        assertEquals(List.of(2, 3), context.readVariable(OUTPUT).asIntList());
        assertEquals(1, Collections.frequency(steps, addInside));
        assertFalse(context.scope().contains(CELL));
    }

    @Test public void boundReferencesFailAtBindingAndSourceMutationFailsFast() {
        Block empty = stops();
        empty.add(new VarDecl(JType.INT_LIST, OUTPUT, new Literal(JType.INT_LIST, null)));
        empty.add(new ExprStmt(appendTo(OUTPUT)));
        assertEquals("NullPointerException", assertThrows(ScriptError.class, () -> run(empty)).getMessage());

        Block aliases = stops(1, 2);
        aliases.add(new VarDecl(JType.INT_LIST, OUTPUT, new VarRef(SOURCE)));
        aliases.add(new ExprStmt(appendTo(OUTPUT)));
        assertEquals("ConcurrentModificationException",
                assertThrows(ScriptError.class, () -> run(aliases)).getMessage());
    }

    private static StreamCall appendTo(String destination) {
        return new StreamCall(SOURCE, StreamCall.Terminal.FOR_EACH, Collections.emptyList(),
                new StreamMethodRef(destination, "add"));
    }

    private static Block stops(int... values) {
        Block program = new Block();
        program.add(Ast.newList(SOURCE));
        for (int value : values) program.add(new ListAdd(SOURCE, Ast.lit(value)));
        return program;
    }

    private static ExecutionContext run(Block program) throws InterruptedException {
        return run(program, new ArrayList<>());
    }

    private static ExecutionContext run(Block program, List<Integer> steps) throws InterruptedException {
        FarmWorld world = new FarmWorld(new String[]{"W"}, 0, 0);
        ExecListener listener = new ExecListener() {
            @Override public void onNodeEnter(int farmerId, int nodeId) { steps.add(nodeId); }
            @Override public void onFinished(int farmerId) { }
            @Override public void onError(int farmerId, int nodeId, String message) { throw new AssertionError(message); }
        };
        ExecutionContext context = new ExecutionContext(world.farmer, world, new DebugController(),
                new WorldClock(0), NativeRegistry.STANDARD, listener);
        program.exec(context);
        return context;
    }
}
