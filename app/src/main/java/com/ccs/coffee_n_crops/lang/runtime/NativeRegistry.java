package com.ccs.coffee_n_crops.lang.runtime;

import com.ccs.coffee_n_crops.farm.Farmhand;
import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.interp.ScriptError;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;

import java.util.HashMap;
import java.util.Map;

/**
 * The table of built-in methods the player's code can call, with their Java signatures.
 * New farmer abilities are registered here.
 */
public final class NativeRegistry {
    /** Ticks a farmer spends cutting, before and after the crop actually changes. */
    private static final double HARVEST_BEFORE_TICKS = 0.5;
    private static final double HARVEST_AFTER_TICKS = 0.3;
    private static final double WATER_TICKS = 0.6;
    private static final double FERTILIZE_TICKS = 0.6;
    private static final double WAIT_TICKS = 1.0;
    /** Ticks a farmer spends looking at the tile under it. */
    private static final double SENSE_TICKS = 0.2;
    /** Ticks a full scan takes. */
    private static final double SCAN_TICKS = 1.0;
    /** Refilling is slow on purpose, so doing it more often than needed is felt. */
    private static final double REFILL_TICKS = 2.0;

    /** A built-in method: its signature and its implementation. */
    public static final class Entry {
        public final JType returnType;
        public final JType[] params;
        public final NativeMethod impl;

        Entry(JType returnType, JType[] params, NativeMethod impl) {
            this.returnType = returnType;
            this.params = params;
            this.impl = impl;
        }
    }

    /** The farmer API every level uses. Immutable after class initialisation. */
    public static final NativeRegistry STANDARD = standard();

    private final Map<String, Entry> methods = new HashMap<>();

    public void register(String receiver, String name, JType returnType, JType[] params, NativeMethod impl) {
        methods.put(receiver + "." + name, new Entry(returnType, params, impl));
    }

    public Entry lookup(String receiver, String name) {
        Entry entry = methods.get(receiver + "." + name);
        if (entry == null) throw new ScriptError("cannot find symbol: method " + receiver + "." + name + "()");
        return entry;
    }

    private static NativeRegistry standard() {
        NativeRegistry r = new NativeRegistry();
        JType[] none = {};

        r.register("farmer", "move", JType.VOID, new JType[]{JType.DIRECTION}, (ctx, args) -> {
            Farmhand.Pose pose;
            synchronized (ctx.world) {
                ctx.checkActive();
                ctx.world.move(ctx.farmer, args[0].asDirection());
                pose = ctx.farmer.pose();
            }
            finishWalk(ctx, pose, pose.durationTicks);
            return Value.VOID;
        });

        r.register("farmer", "harvest", JType.VOID, none, (ctx, args) -> {
            performAction(ctx, Farmhand.Action.HARVEST, HARVEST_BEFORE_TICKS,
                    () -> ctx.world.harvest(ctx.farmer), HARVEST_AFTER_TICKS);
            return Value.VOID;
        });

        r.register("farmer", "water", JType.VOID, none, (ctx, args) -> {
            performAction(ctx, Farmhand.Action.WATER, WATER_TICKS, () -> ctx.world.water(ctx.farmer), 0);
            return Value.VOID;
        });

        r.register("farmer", "fertilize", JType.VOID, none, (ctx, args) -> {
            performAction(ctx, Farmhand.Action.FERTILIZE, FERTILIZE_TICKS, () -> ctx.world.fertilize(ctx.farmer), 0);
            return Value.VOID;
        });

        r.register("farmer", "waitOneTick", JType.VOID, none, (ctx, args) -> {
            advanceTime(ctx, WAIT_TICKS);
            return Value.VOID;
        });

        r.register("farmer", "isRipe", JType.BOOLEAN, none, (ctx, args) -> {
            advanceTime(ctx, SENSE_TICKS);
            return Value.ofBoolean(ctx.world.isRipe(ctx.farmer));
        });

        r.register("farmer", "isDry", JType.BOOLEAN, none, (ctx, args) -> {
            advanceTime(ctx, SENSE_TICKS);
            return Value.ofBoolean(ctx.world.isDry(ctx.farmer));
        });

        r.register("farmer", "canMove", JType.BOOLEAN, new JType[]{JType.DIRECTION}, (ctx, args) -> {
            advanceTime(ctx, SENSE_TICKS);
            return Value.ofBoolean(ctx.world.canMove(ctx.farmer, args[0].asDirection()));
        });

        r.register("farmer", "refill", JType.VOID, none, (ctx, args) -> {
            performAction(ctx, Farmhand.Action.REFILL, REFILL_TICKS, ctx.world::refill, 0);
            return Value.VOID;
        });

        r.register("farmer", "getWater", JType.DOUBLE, none, (ctx, args) -> {
            advanceTime(ctx, SENSE_TICKS);
            return Value.ofDouble(ctx.world.tankLevel());
        });

        r.register("farmer", "getCrop", JType.STRING, none, (ctx, args) -> {
            advanceTime(ctx, SENSE_TICKS);
            return Value.ofString(ctx.world.cropName(ctx.farmer));
        });

        r.register("farmer", "walkTo", JType.VOID, new JType[]{JType.INT, JType.INT}, (ctx, args) -> {
            int distance;
            Farmhand.Pose pose;
            synchronized (ctx.world) {
                ctx.checkActive();
                distance = ctx.world.walkTo(ctx.farmer, args[0].asInt(), args[1].asInt());
                pose = distance > 0 ? ctx.farmer.pose() : null;
            }
            finishWalk(ctx, pose, pose == null ? FarmWorld.WALK_TICKS_PER_TILE : pose.durationTicks);
            return Value.VOID;
        });

        r.register("farmer", "scanCrop", JType.CROP, none, (ctx, args) -> {
            advanceTime(ctx, SENSE_TICKS);
            return new Value(JType.CROP, ctx.world.scanCrop(ctx.farmer));
        });

        r.register("farmer", "scanCrops", JType.CROP_ARRAY, none, (ctx, args) -> {
            advanceTime(ctx, SCAN_TICKS);
            return new Value(JType.CROP_ARRAY, ctx.world.scanCrops(ctx.farmer));
        });

        r.register("farmer", "scanRow", JType.INT_ARRAY, none, (ctx, args) -> {
            advanceTime(ctx, SCAN_TICKS);
            return new Value(JType.INT_ARRAY, ctx.world.scanRow(ctx.farmer));
        });

        r.register("farmer", "scanField", JType.INT_GRID, none, (ctx, args) -> {
            advanceTime(ctx, SCAN_TICKS);
            return new Value(JType.INT_GRID, ctx.world.scanField());
        });

        return r;
    }

    private static void advanceTime(ExecutionContext ctx, double ticks) throws InterruptedException {
        if (ticks <= 0) return;
        ctx.clock.awaitTicks(ticks);
        synchronized (ctx.world) {
            ctx.checkActive();
            ctx.world.advanceTicks(ticks);
        }
    }

    private static void finishWalk(ExecutionContext ctx, Farmhand.Pose pose, double ticks)
            throws InterruptedException {
        try {
            advanceTime(ctx, ticks);
        } finally {
            if (pose != null) ctx.farmer.finishAction(pose);
        }
    }

    private static void performAction(ExecutionContext ctx, Farmhand.Action action, double windup,
                                      Runnable change, double followThrough) throws InterruptedException {
        Farmhand.Pose pose;
        synchronized (ctx.world) {
            ctx.checkActive();
            pose = ctx.farmer.beginAction(action, windup + followThrough);
        }
        try {
            advanceTime(ctx, windup);
            synchronized (ctx.world) {
                ctx.checkActive();
                change.run();
            }
            ctx.listener.onFarmAction(action);
            advanceTime(ctx, followThrough);
        } finally {
            ctx.farmer.finishAction(pose);
        }
    }
}
