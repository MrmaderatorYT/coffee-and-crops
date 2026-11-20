package com.ccs.coffee_n_crops.lang.runtime;

import com.ccs.coffee_n_crops.lang.interp.ScriptError;

import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.locks.ReentrantLock;

/** Tracks the wait graph of one interpreted run's real, interruptible ReentrantLocks. */
public final class LockRegistry {
    private record Wait(ReentrantLock lock, String name, int farmerId) { }

    private final Map<ReentrantLock, Thread> owners = new HashMap<>();
    private final Map<Thread, Wait> waiting = new HashMap<>();
    private String deadlock;

    public void acquire(ReentrantLock lock, String name, int farmerId) throws InterruptedException {
        Thread current = Thread.currentThread();
        synchronized (this) {
            if (current.isInterrupted()) throw new InterruptedException();
            if (deadlock != null) throw new ScriptError(deadlock);
            if (lock.tryLock()) {
                owners.put(lock, current);
                return;
            }
            waiting.put(current, new Wait(lock, name, farmerId));
            String cycle = cycleFrom(current);
            if (cycle != null) {
                waiting.remove(current);
                deadlock = "Deadlock: " + cycle + ". Take the locks in the same order.";
                throw new ScriptError(deadlock);
            }
        }

        try {
            // Waiting outside the registry lets the actual owner run and release its lock.
            lock.lockInterruptibly();
        } catch (InterruptedException cancelled) {
            synchronized (this) {
                waiting.remove(current);
            }
            throw cancelled;
        }
        synchronized (this) {
            waiting.remove(current);
            if (deadlock != null) {
                // The cycle breaker released this lock while unwinding; skip the peer's body too.
                lock.unlock();
                throw new ScriptError(deadlock);
            }
            owners.put(lock, current);
        }
    }

    /** Releases one hold. Reentering a lock keeps its owner registered until the last release. */
    public synchronized void release(ReentrantLock lock) {
        lock.unlock();
        if (!lock.isHeldByCurrentThread()) owners.remove(lock);
    }

    private String cycleFrom(Thread start) {
        Thread current = start;
        StringJoiner cycle = new StringJoiner("; ");
        for (int i = 0; i < waiting.size(); i++) {
            Wait request = waiting.get(current);
            if (request == null) return null;
            Thread owner = owners.get(request.lock());
            Wait ownerRequest = waiting.get(owner);
            if (ownerRequest == null) return null;
            cycle.add("farmer " + request.farmerId() + " waits for " + request.name()
                    + " held by farmer " + ownerRequest.farmerId());
            if (owner == start) return cycle.toString();
            current = owner;
        }
        return null;
    }
}
