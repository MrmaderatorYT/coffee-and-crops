package com.ccs.coffee_n_crops;

import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.farm.WorldClock;
import com.ccs.coffee_n_crops.lang.ast.expr.NewReentrantLock;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.Assign;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.LockStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ReturnStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.check.Validator;
import com.ccs.coffee_n_crops.lang.interp.DebugController;
import com.ccs.coffee_n_crops.lang.interp.ExecListener;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.interp.ScriptError;
import com.ccs.coffee_n_crops.lang.print.JavaPrinter;
import com.ccs.coffee_n_crops.lang.runtime.LockRegistry;
import com.ccs.coffee_n_crops.lang.runtime.NativeRegistry;
import com.ccs.coffee_n_crops.lang.serial.AstJson;
import com.ccs.coffee_n_crops.lang.types.JType;

import org.json.JSONObject;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class LockLanguageTest {
    private static final long WAIT_SECONDS = 3;

    @Test public void nestedReentrantBlocksReleaseBothHoldsDuringReturn() throws Exception {
        Block program = pumpProgram();
        LockStmt outer = (LockStmt) program.get(1);
        LockStmt inner = new LockStmt(new VarRef("pump"));
        outer.body.add(inner);
        inner.body.add(new ReturnStmt());
        Block loaded = AstJson.blockFrom(new JSONObject(AstJson.toJson(program).toString()));
        assertNull(Validator.check(loaded));
        String source = JavaPrinter.toSource(loaded);
        assertEquals(JavaPrinter.toSource(program), source);
        assertTrue(source.contains("new java.util.concurrent.locks.ReentrantLock()"));
        assertTrue(source.contains("pump.lock();\n    try {"));
        assertTrue(source.contains("} finally {\n        pump.unlock();"));

        FarmWorld world = new FarmWorld(new String[]{"W"}, 0, 0);
        ExecutionContext context = new ExecutionContext(world.farmer, world, new DebugController(),
                new WorldClock(0), NativeRegistry.STANDARD, new ExecListener() {
                    @Override public void onNodeEnter(int farmerId, int nodeId) { }
                    @Override public void onFinished(int farmerId) { }
                    @Override public void onError(int farmerId, int nodeId, String message) {
                        throw new AssertionError(message);
                    }
                });
        loaded.exec(context);
        ReentrantLock stored = (ReentrantLock) context.readVariable("pump").raw;
        assertFalse("finally must release the lock during return", stored.isLocked());
    }

    @Test public void lockTargetMustStayTheSameUntilFinallyReleasesIt() {
        Block program = pumpProgram();
        LockStmt guarded = (LockStmt) program.get(1);
        Assign changed = new Assign(JType.REENTRANT_LOCK, "pump", new NewReentrantLock());
        guarded.body.add(changed);
        Validator.Problem problem = Validator.check(program);
        assertNotNull(problem);
        assertEquals(changed, problem.node);
        assertTrue(problem.message.contains("unlock()"));
        guarded.lock.set(new NewReentrantLock());
        assertEquals(guarded, Validator.check(program).node);
    }

    @Test public void circularWaitReportsBothLocksAndAbortsBothCriticalSections() throws Exception {
        LockRegistry locks = new LockRegistry();
        ReentrantLock pumpA = new ReentrantLock();
        ReentrantLock pumpB = new ReentrantLock();
        CountDownLatch held = new CountDownLatch(2);
        CountDownLatch done = new CountDownLatch(2);
        AtomicReference<Throwable> report = new AtomicReference<>();
        AtomicInteger completed = new AtomicInteger();
        Thread first = circularWorker(locks, pumpA, "pumpA", pumpB, "pumpB", 1, held, done, report, completed);
        Thread second = circularWorker(locks, pumpB, "pumpB", pumpA, "pumpA", 2, held, done, report, completed);
        first.start();
        second.start();
        try {
            assertTrue("lock cycle must not hang the workers", done.await(WAIT_SECONDS, TimeUnit.SECONDS));
            Throwable error = report.get();
            assertTrue(String.valueOf(error), error instanceof ScriptError);
            assertTrue(error.getMessage(), error.getMessage().startsWith("Deadlock:"));
            assertTrue(error.getMessage(), error.getMessage().contains("pumpA") && error.getMessage().contains("pumpB"));
            assertEquals("neither critical section may run after the deadlock", 0, completed.get());
            assertFalse(pumpA.isLocked());
            assertFalse(pumpB.isLocked());
        } finally {
            first.interrupt();
            second.interrupt();
        }
    }

    @Test public void cancellingANormalWaitOnlyInterruptsTheWaiter() throws Exception {
        LockRegistry locks = new LockRegistry();
        ReentrantLock pump = new ReentrantLock();
        AtomicReference<Throwable> outcome = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Thread waiter = daemon(() -> {
            try {
                locks.acquire(pump, "pump", 2);
                locks.release(pump);
                outcome.set(new AssertionError("unexpectedly acquired the lock"));
            } catch (Throwable problem) {
                outcome.set(problem);
            } finally {
                done.countDown();
            }
        });
        locks.acquire(pump, "pump", 1);
        waiter.start();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
            while (!pump.hasQueuedThread(waiter) && done.getCount() > 0 && System.nanoTime() < deadline) {
                Thread.sleep(1);
            }
            assertTrue("peer must wait on the real ReentrantLock", pump.hasQueuedThread(waiter));
            waiter.interrupt();
            assertTrue(done.await(WAIT_SECONDS, TimeUnit.SECONDS));
            assertTrue(String.valueOf(outcome.get()), outcome.get() instanceof InterruptedException);
            assertTrue(pump.isHeldByCurrentThread());
        } finally {
            waiter.interrupt();
            locks.release(pump);
        }
        assertFalse(pump.isLocked());
    }

    private static Block pumpProgram() {
        Block program = new Block();
        program.add(new VarDecl(JType.REENTRANT_LOCK, "pump", new NewReentrantLock()));
        program.add(new LockStmt(new VarRef("pump")));
        return program;
    }

    private static Thread circularWorker(LockRegistry locks, ReentrantLock owned, String ownName,
                                         ReentrantLock other, String otherName, int farmerId,
                                         CountDownLatch held, CountDownLatch done,
                                         AtomicReference<Throwable> report, AtomicInteger completed) {
        return daemon(() -> {
            try {
                locks.acquire(owned, ownName, farmerId);
                try {
                    held.countDown();
                    if (!held.await(WAIT_SECONDS, TimeUnit.SECONDS)) throw new AssertionError("peer did not acquire its lock");
                    locks.acquire(other, otherName, farmerId);
                    locks.release(other);
                    completed.incrementAndGet();
                } finally {
                    locks.release(owned);
                }
            } catch (Throwable problem) {
                report.compareAndSet(null, problem);
            } finally {
                done.countDown();
            }
        });
    }

    private static Thread daemon(Runnable task) {
        Thread worker = new Thread(task);
        worker.setDaemon(true);
        return worker;
    }
}
