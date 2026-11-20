package com.ccs.coffee_n_crops;

import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.farm.Farmhand;
import com.ccs.coffee_n_crops.farm.WorldClock;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.expr.MethodCall;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.ExprStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.TeamStmt;
import com.ccs.coffee_n_crops.lang.interp.ExecListener;
import com.ccs.coffee_n_crops.lang.interp.Interpreter;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TeamRuntimeTest {
    @Test public void steppingSecondFarmerLeavesFirstFarmerPaused() throws Exception {
        FarmWorld world = new FarmWorld(new String[]{".W", ".W"}, 0, 0, 0, 0, 0, 1);
        Block program = new Block();
        TeamStmt team = bothRows();
        program.add(team);
        Interpreter interpreter = new Interpreter(new WorldClock(0));
        AtomicReference<Thread> firstThread = new AtomicReference<>();
        AtomicReference<Thread> secondThread = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>();
        CountDownLatch actorsWaiting = new CountDownLatch(2);
        CountDownLatch secondMoved = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        try {
            interpreter.start(program, world, new ExecListener() {
                @Override public void onNodeEnter(int farmerId, int nodeId) {
                    if (farmerId == 1 && nodeId == team.first.get(0).id) {
                        firstThread.set(Thread.currentThread());
                        actorsWaiting.countDown();
                    } else if (farmerId == 2 && nodeId == team.second.get(0).id) {
                        secondThread.set(Thread.currentThread());
                        actorsWaiting.countDown();
                    } else if (farmerId == 2 && nodeId == team.second.get(1).id) {
                        secondMoved.countDown();
                    }
                }
                @Override public void onFinished(int farmerId) { finished.countDown(); }
                @Override public void onError(int farmerId, int nodeId, String message) {
                    error.set(message);
                    finished.countDown();
                }
            }, true);
            interpreter.debug.step();
            assertTrue(actorsWaiting.await(3, TimeUnit.SECONDS));
            assertNotSame(firstThread.get(), secondThread.get());
            interpreter.debug.step(2);
            assertTrue(secondMoved.await(3, TimeUnit.SECONDS));
            assertEquals(0, world.farmer.pose().x);
            assertEquals(1, world.farmers().get(1).pose().x);
            assertEquals(0, world.wheatHarvested());

            interpreter.debug.play();
            assertTrue(finished.await(3, TimeUnit.SECONDS));
            assertNull(error.get());
            assertEquals(1, world.wheatHarvested(world.farmer));
            Farmhand partner = world.farmers().get(1);
            assertEquals(1, world.wheatHarvested(partner));
            world.reset();
            assertEquals(0, world.farmer.pose().x);
            assertEquals(0, partner.pose().x);
            assertEquals(1, partner.pose().y);
            assertEquals(0, world.wheatHarvested(partner));
        } finally {
            interpreter.stop();
        }
    }

    @Test public void resetInterruptsBothPausedFarmhands() throws Exception {
        FarmWorld world = new FarmWorld(new String[]{".W", ".W"}, 0, 0, 0, 0, 0, 1);
        Block program = new Block();
        TeamStmt team = bothRows();
        program.add(team);
        Interpreter interpreter = new Interpreter(new WorldClock(0));
        CountDownLatch paused = new CountDownLatch(2);
        CountDownLatch stopped = new CountDownLatch(2);
        try {
            interpreter.start(program, world, new ExecListener() {
                @Override public void onNodeEnter(int farmerId, int nodeId) {
                    if (nodeId == team.first.get(0).id || nodeId == team.second.get(0).id) paused.countDown();
                }
                @Override public void onTeamFarmerFinished(int farmerId) { stopped.countDown(); }
                @Override public void onFinished(int farmerId) { }
                @Override public void onError(int farmerId, int nodeId, String message) { }
            }, true);
            interpreter.debug.step();
            assertTrue(paused.await(3, TimeUnit.SECONDS));
            interpreter.stop();
            world.reset();
            assertTrue(stopped.await(3, TimeUnit.SECONDS));
            assertEquals(0, world.farmer.pose().x);
            assertEquals(0, world.farmers().get(1).pose().x);
            assertEquals(0, world.wheatHarvested());
        } finally {
            interpreter.stop();
        }
    }

    private static TeamStmt bothRows() {
        TeamStmt team = new TeamStmt();
        team.first.add(new ExprStmt(new MethodCall(team.firstFarmer.name, "walkTo", Ast.lit(1), Ast.lit(0))));
        team.first.add(new ExprStmt(new MethodCall(team.firstFarmer.name, "harvest")));
        team.second.add(new ExprStmt(new MethodCall(team.secondFarmer.name, "walkTo", Ast.lit(1), Ast.lit(1))));
        team.second.add(new ExprStmt(new MethodCall(team.secondFarmer.name, "harvest")));
        return team;
    }
}
