package com.ccs.coffee_n_crops.progress;

import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/** A level: its farm, its goal, and which code blocks the player may use. */
public final class LevelDef {

    /** Decides whether a finished run completed the level. */
    public interface Goal {
        /** @return null if the level is complete, otherwise what is still missing, for the player */
        String check(FarmWorld world, Block program);
    }

    public final String id;
    public final String title;
    /** One line shown next to the title: what the player has to achieve. */
    public final String goalText;
    public final String hint;
    /** Palette unlock keys from {@link Unlock}. */
    public final Set<String> unlocks;

    private final String[] layout;
    private final int startX;
    private final int startY;
    private final Goal goal;
    /** The tile where the farmer must stand when the program finishes. */
    public record Finish(int x, int y) { }

    private Finish finish;
    private double tankStart;
    private double tankCapacity;
    private Supplier<Block> starter = Block::new;
    private int secondX = -1;
    private int secondY = -1;
    private boolean sandbox;

    public LevelDef(String id, String title, String goalText, String hint,
                    String[] layout, int startX, int startY, Goal goal, String... unlocks) {
        this.id = id;
        this.title = title;
        this.goalText = goalText;
        this.hint = hint;
        this.layout = layout;
        this.startX = startX;
        this.startY = startY;
        this.goal = goal;
        this.unlocks = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(unlocks)));
    }

    /**
     * Gives the level's farmer a limited water tank.
     *
     * @param start    litres in the tank when a run begins
     * @param capacity litres after {@code farmer.refill()}
     */
    public LevelDef withTank(double start, double capacity) {
        this.tankStart = start;
        this.tankCapacity = capacity;
        return this;
    }

    public LevelDef withFinish(int x, int y) {
        if (y < 0 || y >= layout.length || x < 0 || x >= layout[y].length()) {
            throw new IllegalArgumentException("Finish must be on the farm");
        }
        finish = new Finish(x, y);
        return this;
    }

    public Finish finish() {
        return finish;
    }

    public FarmWorld createWorld() {
        if (secondX >= 0) return new FarmWorld(layout, startX, startY, tankStart, tankCapacity, secondX, secondY);
        return new FarmWorld(layout, startX, startY, tankStart, tankCapacity);
    }

    LevelDef withSecondFarmhand(int x, int y) {
        if (y < 0 || y >= layout.length || x < 0 || x >= layout[y].length()) {
            throw new IllegalArgumentException("Second farmer must start on the farm");
        }
        secondX = x;
        secondY = y;
        return this;
    }

    LevelDef withStarter(Supplier<Block> starter) {
        this.starter = starter;
        return this;
    }

    LevelDef asSandbox() {
        sandbox = true;
        return this;
    }

    public boolean isSandbox() {
        return sandbox;
    }

    /** Returns a fresh, independently editable program for a level without a saved script. */
    public Block createProgram() {
        return starter.get();
    }

    /** @return null if the run completed the level, otherwise what is still missing */
    public String check(FarmWorld world, Block program) {
        String missing = goal.check(world, program);
        if (missing != null || finish == null) return missing;
        var position = world.farmer.pose();
        return position.x == finish.x() && position.y == finish.y()
                ? null : "Finish on the tile marked with the orange flag.";
    }
}
