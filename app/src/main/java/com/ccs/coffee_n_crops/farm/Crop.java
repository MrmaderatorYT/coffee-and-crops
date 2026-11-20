package com.ccs.coffee_n_crops.farm;

/** The crop and growth stage reported by the typed scanner. */
public enum Crop {
    NONE, WHEAT_SEEDLING, WATERED_WHEAT, WHEAT, COFFEE_SEEDLING, WATERED_COFFEE, COFFEE, CARROT;

    static Crop fromTile(TileState state) {
        return switch (state) {
            case WHEAT_GROWING -> WHEAT_SEEDLING;
            case WHEAT_GROWING_WATERED -> WATERED_WHEAT;
            case WHEAT_RIPE -> WHEAT;
            case COFFEE_GROWING -> COFFEE_SEEDLING;
            case COFFEE_WATERED -> WATERED_COFFEE;
            case COFFEE_RIPE -> COFFEE;
            case CARROT_RIPE -> CARROT;
            case GRASS, STUBBLE, ROCK -> NONE;
        };
    }
}
