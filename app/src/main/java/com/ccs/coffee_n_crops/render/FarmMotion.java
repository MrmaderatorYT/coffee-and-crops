package com.ccs.coffee_n_crops.render;

import com.ccs.coffee_n_crops.farm.Direction;
import com.ccs.coffee_n_crops.farm.Farmhand;

/** Turns a worker's action snapshot into the visual pose for the current frame. */
final class FarmMotion {
    private static final float STRIDE_RADIANS_PER_TILE = (float) (2 * Math.PI * 1.7);
    private Farmhand.Pose lastPose;
    private long lastFrame;
    private double actionMillis;
    float x;
    float y;
    Direction facing = Direction.EAST;
    float phase;
    float stride;
    float movement;

    void update(Farmhand.Pose pose, long now, long millisPerTick, int speed) {
        long previousTime = pose == lastPose ? lastFrame : pose.startedNanos;
        if (pose != lastPose) actionMillis = 0;
        actionMillis += Math.max(0, now - previousTime) * speed / 1_000_000.0;
        lastFrame = now;
        lastPose = pose;
        double duration = pose.durationTicks * millisPerTick;
        phase = duration <= 0 ? 1 : (float) Math.min(1, actionMillis / duration);
        x = pose.x + .5f;
        y = pose.y + .5f;
        facing = pose.facing;
        stride = 0;
        movement = 0;
        if (pose.action != Farmhand.Action.WALK) return;

        int dx = pose.x - pose.fromX, dy = pose.y - pose.fromY;
        int eastWest = Math.abs(dx);
        float distance = phase * (eastWest + Math.abs(dy));
        x = pose.fromX + .5f + Integer.signum(dx) * Math.min(eastWest, distance);
        y = pose.fromY + .5f + Integer.signum(dy) * Math.max(0, distance - eastWest);
        if (distance < eastWest || dy == 0) facing = dx < 0 ? Direction.WEST : Direction.EAST;
        else facing = dy < 0 ? Direction.NORTH : Direction.SOUTH;
        stride = distance * STRIDE_RADIANS_PER_TILE;
        movement = phase >= 1 ? 0 : Math.min(1, Math.min(phase, 1 - phase) * 12);
    }
}
