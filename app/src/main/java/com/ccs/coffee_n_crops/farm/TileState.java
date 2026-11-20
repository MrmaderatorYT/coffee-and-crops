package com.ccs.coffee_n_crops.farm;

/** What is on a farm tile right now. */
public enum TileState {
    /** Plain grass: nothing to harvest. */
    GRASS,
    /** Young wheat on dry soil: needs water, and is ruined if harvested. */
    WHEAT_GROWING,
    /** Young wheat that has been watered. Still not ready to harvest. */
    WHEAT_GROWING_WATERED,
    /** Soil with ripe wheat, ready to harvest. */
    WHEAT_RIPE,
    /** Soil with cut stubble, left after a harvest. */
    STUBBLE,
    COFFEE_GROWING,
    COFFEE_WATERED,
    COFFEE_RIPE,
    CARROT_RIPE,
    ROCK;

    public boolean isGrowing() {
        return isDry() || isWatered();
    }

    public boolean isDry() {
        return this == WHEAT_GROWING || this == COFFEE_GROWING;
    }

    public boolean isWatered() {
        return this == WHEAT_GROWING_WATERED || this == COFFEE_WATERED;
    }

    public boolean isRipe() {
        return this == WHEAT_RIPE || this == COFFEE_RIPE || this == CARROT_RIPE;
    }

    public boolean isCoffee() {
        return this == COFFEE_GROWING || this == COFFEE_WATERED || this == COFFEE_RIPE;
    }
}
