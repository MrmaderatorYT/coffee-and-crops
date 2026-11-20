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
    STUBBLE;

    public boolean isGrowing() {
        return this == WHEAT_GROWING || this == WHEAT_GROWING_WATERED;
    }
}
