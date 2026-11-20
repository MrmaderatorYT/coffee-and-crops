package com.ccs.coffee_n_crops.farm;

/** Compass directions on the farm grid. North is up the screen (y decreases). */
public enum Direction {
    NORTH(0, -1),
    EAST(1, 0),
    SOUTH(0, 1),
    WEST(-1, 0);

    public final int dx;
    public final int dy;

    Direction(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }
}
