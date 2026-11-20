package com.ccs.coffee_n_crops.lang.interp;

import com.ccs.coffee_n_crops.lang.types.Value;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class DebugVariablesTest {
    @Test
    public void aPauseReportsLiveBindingsGroupedByScope() throws Exception {
        var globals = new Scope(null);
        globals.declare("count", Value.ofInt(2));
        var locals = new Scope(globals);
        locals.declare("label", Value.ofString("row\n1"));
        var paused = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        var debugger = new DebugController();
        debugger.pause();
        String[] state = {null};
        Thread worker = new Thread(() -> {
            try {
                debugger.awaitPermission(() -> true, 1, () -> {
                    state[0] = locals.snapshot();
                    paused.countDown();
                });
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                finished.countDown();
            }
        });
        worker.setDaemon(true);
        worker.start();
        try {
            assertTrue(paused.await(1, TimeUnit.SECONDS));
            assertEquals("String label = \"row\\n1\"\n\nint count = 2", state[0]);
            assertFalse(finished.await(10, TimeUnit.MILLISECONDS));
        } finally {
            debugger.play();
        }
        assertTrue(finished.await(1, TimeUnit.SECONDS));
    }
}
