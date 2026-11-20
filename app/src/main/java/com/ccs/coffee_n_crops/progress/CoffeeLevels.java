package com.ccs.coffee_n_crops.progress;

import java.util.Arrays;
import java.util.List;

/** A coffee harvest lesson followed by a timed harvest with an obstructed route. */
final class CoffeeLevels {
    private CoffeeLevels() { }

    static List<LevelDef> all() {
        return Arrays.asList(
                new LevelDef("level_57", "Level 57 · Coffee harvest", "Pick the coffee and leave the wheat standing",
                        "Walk along the row. Use farmer.getCrop().equals(\"coffee\") to recognise a coffee bush. "
                                + "Only call farmer.harvest() for coffee and finish at the flag.",
                        new String[]{"CWCW."}, 0, 0,
                        (world, program) -> {
                            if (world.wheatHarvested() != 0) return "Leave the wheat standing.";
                            if (world.coffeeHarvested() < world.totalCoffee()) return "Pick both coffee bushes.";
                            return null;
                        },
                        Unlock.MOVE, Unlock.WALK_TO, Unlock.HARVEST, Unlock.FOR, Unlock.IF,
                        Unlock.GET_CROP, Unlock.STRING, Unlock.INT, Unlock.LOGIC).withFinish(4, 0),
                new LevelDef("level_58", "Level 58 · Growing coffee", "Go around the rock and help the coffee ripen",
                        "Walk along the lower row to avoid the rock. Water the young coffee, then fertilize it "
                                + "to halve its remaining growth time. Repeat farmer.waitOneTick() while "
                                + "!farmer.isRipe(), harvest both bushes, and reach the flag.",
                        new String[]{".#c.", "...C"}, 0, 0,
                        (world, program) -> {
                            if (world.ruined() > 0) return "Let the young coffee ripen before harvesting.";
                            if (world.fertilized() == 0) return "Fertilize the watered coffee before it ripens.";
                            if (world.coffeeHarvested() < world.totalCoffee()) return "Pick both coffee bushes.";
                            return null;
                        },
                        Unlock.MOVE, Unlock.WALK_TO, Unlock.HARVEST, Unlock.WATER, Unlock.IS_RIPE,
                        Unlock.IS_DRY, Unlock.CAN_MOVE, Unlock.FERTILIZE, Unlock.WAIT, Unlock.WHILE,
                        Unlock.LOGIC, Unlock.INT).withFinish(3, 1));
    }
}
