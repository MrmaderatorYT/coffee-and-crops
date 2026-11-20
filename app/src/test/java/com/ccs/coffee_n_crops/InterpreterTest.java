package com.ccs.coffee_n_crops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ccs.coffee_n_crops.farm.Direction;
import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.farm.WorldClock;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForStmt;
import com.ccs.coffee_n_crops.lang.interp.ExecListener;
import com.ccs.coffee_n_crops.lang.interp.Interpreter;
import com.ccs.coffee_n_crops.lang.print.JavaPrinter;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Runs the interpreter on the plain JVM: no Android, no delays. */
public class InterpreterTest {
    private static final String[] LEVEL_1 = {"WWWWW."};

    /** Records how a run ended. */
    private static final class Recorder implements ExecListener {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicInteger entered = new AtomicInteger();
        volatile String error;

        @Override
        public void onNodeEnter(int farmerId, int nodeId) {
            entered.incrementAndGet();
        }

        @Override
        public void onFinished(int farmerId) {
            done.countDown();
        }

        @Override
        public void onError(int farmerId, int nodeId, String message) {
            error = message;
            done.countDown();
        }
    }

    private static Block harvestRow(int count) {
        ForStmt loop = Ast.forCount("i", count);
        loop.body.add(Ast.harvest());
        loop.body.add(Ast.move(Direction.EAST));
        Block program = new Block();
        program.add(loop);
        return program;
    }

    @Test
    public void forLoopHarvestsTheWholeRow() throws Exception {
        FarmWorld world = new FarmWorld(LEVEL_1, 0, 0);
        Recorder recorder = new Recorder();
        new Interpreter(new WorldClock(0)).start(harvestRow(5), world, recorder, false);

        assertTrue(recorder.done.await(5, TimeUnit.SECONDS));
        assertNull(recorder.error);
        assertEquals(5, world.wheatHarvested());
        assertEquals(0, world.ripeRemaining());
        assertEquals(5, world.farmer.pose().x);
    }

    @Test
    public void flyingOffTheFarmStopsTheDroneWithAnError() throws Exception {
        FarmWorld world = new FarmWorld(LEVEL_1, 0, 0);
        Recorder recorder = new Recorder();
        new Interpreter(new WorldClock(0)).start(harvestRow(6), world, recorder, false);

        assertTrue(recorder.done.await(5, TimeUnit.SECONDS));
        assertNotNull(recorder.error);
        assertTrue(recorder.error, recorder.error.startsWith("OutOfBoundsFarmException"));
        assertEquals(5, world.farmer.pose().x);
    }

    @Test
    public void stepRunsExactlyOneStatementAtATime() throws Exception {
        FarmWorld world = new FarmWorld(LEVEL_1, 0, 0);
        Block program = new Block();
        program.add(Ast.harvest());
        program.add(Ast.move(Direction.EAST));
        Recorder recorder = new Recorder();
        Interpreter interpreter = new Interpreter(new WorldClock(0));

        interpreter.start(program, world, recorder, true);
        waitUntil(() -> recorder.entered.get() == 1);
        assertEquals("paused before the first statement", 0, world.wheatHarvested());

        interpreter.debug.step();
        waitUntil(() -> recorder.entered.get() == 2);
        assertEquals(1, world.wheatHarvested());
        assertEquals("paused before move()", 0, world.farmer.pose().x);

        interpreter.debug.step();
        assertTrue(recorder.done.await(5, TimeUnit.SECONDS));
        assertEquals(1, world.farmer.pose().x);
    }

    @Test
    public void printerProducesRealJava() {
        String expected = ""
                + "void run(Farmhand farmer) {\n"
                + "    for (int i = 0; i < 5; i++) {\n"
                + "        farmer.harvest();\n"
                + "        farmer.move(Direction.EAST);\n"
                + "    }\n"
                + "}\n";
        assertEquals(expected, JavaPrinter.toSource(harvestRow(5)));
    }

    private interface Condition {
        boolean holds();
    }

    private static void waitUntil(Condition condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (!condition.holds()) {
            if (System.currentTimeMillis() > deadline) throw new AssertionError("timed out");
            Thread.sleep(5);
        }
        Thread.sleep(50); // let the farmer thread settle at the gate
    }
}
