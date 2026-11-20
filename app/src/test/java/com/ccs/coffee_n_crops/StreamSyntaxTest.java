package com.ccs.coffee_n_crops;

import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.farm.WorldClock;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.expr.Binary;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamCall;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamLambda;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.Assign;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.BreakStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ExprStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.StreamForEachStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.check.ScopeResolver;
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

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Java scope and numeric rules used by the stream lessons. */
public final class StreamSyntaxTest {
    @Test public void capturedLocalMustRemainEffectivelyFinalThroughoutItsScope() {
        Block program = new Block();
        program.add(Ast.newList("stops"));
        VarDecl offset = new VarDecl(JType.INT, "offset", Ast.lit(1));
        program.add(offset);
        StreamLambda mapper = new StreamLambda("cell", JType.INT,
                new Binary(Binary.Op.ADD, new VarRef("cell"), new VarRef("offset")));
        StreamCall stream = new StreamCall("stops", StreamCall.Terminal.FIND_FIRST,
                Collections.singletonList(new StreamCall.Stage(StreamCall.StageKind.MAP, mapper)), null);
        program.add(new ExprStmt(stream));
        assertTrue(ScopeResolver.visibleAt(mapper.expression).contains(mapper.parameter));
        assertTrue(ScopeResolver.visibleAt(mapper.expression).contains(offset));
        assertNull(Validator.check(program));

        Assign reassign = Ast.assign(offset, Ast.lit(2));
        program.add(reassign);
        assertCaptureError(program);
        program.remove(program.size() - 1);
        program.add(2, reassign);
        assertCaptureError(program);
    }

    @Test public void aBreakInsideAConsumerCannotExitAnEnclosingLoop() {
        Block program = new Block();
        program.add(Ast.newList("stops"));
        ForStmt loop = Ast.forCount("i", 3);
        StreamLambda consumer = new StreamLambda("cell");
        consumer.body.add(new BreakStmt());
        loop.body.add(new StreamForEachStmt(new StreamCall("stops", StreamCall.Terminal.FOR_EACH,
                Collections.emptyList(), consumer)));
        program.add(loop);

        assertEquals("break outside switch or loop", Validator.check(program).message);
    }

    @Test public void longLiteralsAndArithmeticRoundTripWithoutNarrowingTheResult() throws Exception {
        Block program = new Block();
        program.add(new VarDecl(JType.LONG, "count", new Binary(Binary.Op.ADD, JType.LONG,
                Ast.lit(4_000_000_000L), Ast.lit(3))));
        program.add(new VarDecl(JType.LONG, "widened", new Binary(Binary.Op.ADD, JType.LONG,
                Ast.lit(Integer.MAX_VALUE), Ast.lit(1))));
        Block loaded = AstJson.blockFrom(new JSONObject(AstJson.toJson(program).toString()));
        assertTrue(JavaPrinter.toSource(loaded).contains("long count = 4000000000L + 3;"));
        assertNull(Validator.check(loaded));
        FarmWorld world = new FarmWorld(new String[]{"."}, 0, 0);
        ExecutionContext context = new ExecutionContext(world.farmer, world, new DebugController(),
                new WorldClock(0), NativeRegistry.STANDARD, new ExecListener() {
                    @Override public void onNodeEnter(int farmerId, int nodeId) { }
                    @Override public void onFinished(int farmerId) { }
                    @Override public void onError(int farmerId, int nodeId, String message) {
                        throw new AssertionError(message);
                    }
                });
        loaded.exec(context);
        assertEquals(4_000_000_003L, context.readVariable("count").asLong());
        assertEquals(Integer.MIN_VALUE, context.readVariable("widened").asLong());
    }

    private static void assertCaptureError(Block program) {
        assertEquals("local variables referenced from a lambda expression must be final or effectively final",
                Validator.check(program).message);
    }
}
