package com.ccs.coffee_n_crops.farm;

/** One grid cell. The state is volatile because the render thread reads it while farmer threads write it. */
public final class Tile {
    public volatile TileState state;
    double readyAt;
    boolean fertilized;

    public Tile(TileState state) {
        this.state = state;
    }
}
