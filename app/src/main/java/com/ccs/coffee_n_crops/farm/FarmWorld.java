package com.ccs.coffee_n_crops.farm;

import com.ccs.coffee_n_crops.farm.error.EmptyTankException;
import com.ccs.coffee_n_crops.farm.error.OutOfBoundsFarmException;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The farm grid and everything on it. Mutating methods are synchronized so the engine's own state
 * can never be corrupted, whatever the player's code does; game-level races (two workers, one tile)
 * are introduced deliberately in the concurrency chapter, above this layer.
 */
public final class FarmWorld {
    /** Layout characters. Anything else is grass. */
    public static final char RIPE = 'W';
    public static final char GROWING = 'g';
    public static final char GROWING_WATERED = 'w';

    /** Scanner codes: what {@code farmer.scanRow()} and {@code farmer.scanField()} report per tile. */
    public static final int SCAN_NOTHING = 0;
    /** Young wheat that needs water. */
    public static final int SCAN_YOUNG = 1;
    public static final int SCAN_RIPE = 2;

    /** What {@code farmer.getCrop()} answers. */
    public static final String CROP_WHEAT = "wheat";
    public static final String CROP_SPROUT = "sprout";
    public static final String CROP_WATERED = "watered";
    public static final String CROP_GRASS = "grass";
    /** Every answer, in the order the editor offers them as String constants. */
    public static final String[] CROP_NAMES = {CROP_WHEAT, CROP_SPROUT, CROP_WATERED, CROP_GRASS};

    /** Litres one watering takes from the tank. */
    public static final double WATER_PER_USE = 0.5;
    private static final double STEP_TICKS = 1.0;
    /** Continuous walking skips the pause between separate steps. */
    public static final double WALK_TICKS_PER_TILE = 0.4;

    public final int width;
    public final int height;
    public final Farmhand farmer;

    private record Start(int x, int y) { }

    private final String[] layout;
    private final int startX;
    private final int startY;
    private final Start partnerStart;
    private final List<Farmhand> farmers;
    private final Tile[][] tiles; // [y][x]
    private final int totalWheat;
    private final int totalGrowing;
    private volatile int wheatHarvested;
    private volatile int watered;
    private volatile int ruined;
    private final Map<Farmhand, Integer> farmerHarvests = new HashMap<>();
    private final Map<Farmhand, Integer> farmerWaterings = new HashMap<>();
    private volatile int resetCount;
    /** Tank size in litres; 0 means the level does not limit water. */
    private final double tankCapacity;
    private final double tankStart;
    private volatile double tank;
    private volatile int refills;
    /** Per-run lesson evidence: an unused catch/finally block must not satisfy a goal. */
    private final Map<String, Integer> caughtExceptions = new HashMap<>();
    private int finallyRuns;
    private final Set<Integer> usedLessonNodes = new HashSet<>();
    private final Map<String, Integer> objectCreations = new HashMap<>();
    private final Map<String, Integer> fieldWrites = new HashMap<>();
    private final Map<String, Integer> instanceCalls = new HashMap<>();
    private final Map<Integer, Set<String>> instanceCallClasses = new HashMap<>();
    private final Map<String, Integer> superCalls = new HashMap<>();

    /**
     * @param layout one string per row, using {@link #RIPE} and {@link #GROWING}
     */
    public FarmWorld(String[] layout, int startX, int startY) {
        this(layout, startX, startY, 0, 0);
    }

    /**
     * @param tankStart    litres in the tank at the start of every run
     * @param tankCapacity litres a refill brings the tank to; 0 for unlimited water
     */
    public FarmWorld(String[] layout, int startX, int startY, double tankStart, double tankCapacity) {
        this(layout, startX, startY, tankStart, tankCapacity, null);
    }

    /** Creates a level with a second worker on the same grid. */
    public FarmWorld(String[] layout, int startX, int startY, double tankStart, double tankCapacity,
                     int secondX, int secondY) {
        this(layout, startX, startY, tankStart, tankCapacity, new Start(secondX, secondY));
    }

    private FarmWorld(String[] layout, int startX, int startY, double tankStart, double tankCapacity,
                      Start partnerStart) {
        this.tankStart = tankStart;
        this.tankCapacity = tankCapacity;
        this.tank = tankStart;
        this.layout = layout.clone();
        this.height = layout.length;
        this.width = layout[0].length();
        this.startX = startX;
        this.startY = startY;
        this.partnerStart = partnerStart;
        this.tiles = new Tile[height][width];
        Farmhand partner = partnerStart == null ? null : new Farmhand(2, partnerStart.x(), partnerStart.y());
        this.farmer = new Farmhand(1, startX, startY, partner);
        this.farmers = partner == null ? Collections.singletonList(farmer)
                : Collections.unmodifiableList(Arrays.asList(farmer, partner));
        int wheat = 0;
        int growing = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                TileState state = initialState(x, y);
                tiles[y][x] = new Tile(state);
                if (state == TileState.WHEAT_RIPE) wheat++;
                if (state == TileState.WHEAT_GROWING) growing++;
            }
        }
        this.totalWheat = wheat;
        this.totalGrowing = growing;
    }

    /** Workers in stable program and rendering order. */
    public List<Farmhand> farmers() { return farmers; }

    /** Puts every tile and worker back to the level's starting state. */
    public synchronized void reset() {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                tiles[y][x].state = initialState(x, y);
            }
        }
        farmer.reset(startX, startY);
        if (partnerStart != null) farmers.get(1).reset(partnerStart.x(), partnerStart.y());
        wheatHarvested = 0;
        watered = 0;
        farmerHarvests.clear();
        farmerWaterings.clear();
        ruined = 0;
        tank = tankStart;
        refills = 0;
        caughtExceptions.clear();
        finallyRuns = 0;
        usedLessonNodes.clear();
        objectCreations.clear();
        fieldWrites.clear();
        instanceCalls.clear();
        instanceCallClasses.clear();
        superCalls.clear();
        resetCount++;
    }

    /** Records entry into an interpreter catch arm, using its declared Java exception type. */
    public synchronized void recordCaughtException(String typeName) {
        caughtExceptions.put(typeName, caughtExceptions.getOrDefault(typeName, 0) + 1);
    }

    /** Number of handlers of this declared type entered during this run. */
    public synchronized int caughtExceptions(String typeName) {
        return caughtExceptions.getOrDefault(typeName, 0);
    }

    /** Records entry into a finally block; Reset clears this with the rest of the farm. */
    public synchronized void recordFinally() {
        finallyRuns++;
    }

    public synchronized int finallyRuns() {
        return finallyRuns;
    }

    /** Records an AST node that performed a lesson's featured operation in this run. */
    public synchronized void recordLessonUse(int nodeId) {
        usedLessonNodes.add(nodeId);
    }

    public synchronized boolean usedLessonNode(int nodeId) {
        return usedLessonNodes.contains(nodeId);
    }

    /** Records an object created by player code, rather than merely declared in an unused class. */
    public synchronized void recordObjectCreated(String className) {
        objectCreations.put(className, objectCreations.getOrDefault(className, 0) + 1);
    }

    public synchronized int objectCreations(String className) {
        return objectCreations.getOrDefault(className, 0);
    }

    /** Explicit field assignment evidence; default field initialization is not a player write. */
    public synchronized void recordFieldWrite(String declaringClass, String fieldName) {
        String key = declaringClass + "." + fieldName;
        fieldWrites.put(key, fieldWrites.getOrDefault(key, 0) + 1);
    }

    public synchronized int fieldWrites(String declaringClass, String fieldName) {
        return fieldWrites.getOrDefault(declaringClass + "." + fieldName, 0);
    }

    /** Records dispatch using the concrete object's class, including calls through a base reference. */
    public synchronized void recordInstanceCall(String runtimeClass, String methodName, int callId) {
        String key = runtimeClass + "." + methodName;
        instanceCalls.put(key, instanceCalls.getOrDefault(key, 0) + 1);
        instanceCallClasses.computeIfAbsent(callId, id -> new HashSet<>()).add(runtimeClass);
    }

    public synchronized int instanceCalls(String runtimeClass, String methodName) {
        return instanceCalls.getOrDefault(runtimeClass + "." + methodName, 0);
    }

    /** Number of different concrete classes invoked from this particular expression. */
    public synchronized int instanceCallClassCount(int callId) {
        Set<String> classes = instanceCallClasses.get(callId);
        return classes == null ? 0 : classes.size();
    }

    public synchronized void recordSuperCall(String className) {
        superCalls.put(className, superCalls.getOrDefault(className, 0) + 1);
    }

    public synchronized int superCalls(String className) {
        return superCalls.getOrDefault(className, 0);
    }

    public Tile tile(int x, int y) {
        return tiles[y][x];
    }

    public synchronized void move(Farmhand hand, Direction direction) {
        Farmhand.Pose current = hand.pose();
        int nx = current.x + direction.dx;
        int ny = current.y + direction.dy;
        if (nx < 0 || ny < 0 || nx >= width || ny >= height) {
            throw new OutOfBoundsFarmException(nx, ny);
        }
        hand.startWalking(nx, ny, direction, STEP_TICKS);
    }

    /**
     * Cuts whatever crop is under the farmer. Ripe wheat is collected; young wheat is destroyed
     * and counted as ruined, which is why programs should check {@link #isRipe} first.
     */
    public synchronized void harvest(Farmhand hand) {
        Tile tile = tileUnder(hand);
        if (tile.state == TileState.WHEAT_RIPE) {
            tile.state = TileState.STUBBLE;
            wheatHarvested++;
            farmerHarvests.merge(hand, 1, Integer::sum);
        } else if (tile.state.isGrowing()) {
            tile.state = TileState.STUBBLE;
            ruined++;
        }
    }

    /**
     * Waters the tile under the farmer. Dry young wheat becomes watered; young wheat that is
     * already watered drowns and is counted as ruined. Anything else is unaffected.
     */
    public synchronized void water(Farmhand hand) {
        if (tankCapacity > 0) {
            // The water is spent whatever is under the farmer.
            if (tank < WATER_PER_USE) throw new EmptyTankException(tank, WATER_PER_USE);
            tank -= WATER_PER_USE;
        }
        Tile tile = tileUnder(hand);
        if (tile.state == TileState.WHEAT_GROWING) {
            tile.state = TileState.WHEAT_GROWING_WATERED;
            watered++;
            farmerWaterings.merge(hand, 1, Integer::sum);
        } else if (tile.state == TileState.WHEAT_GROWING_WATERED) {
            tile.state = TileState.STUBBLE;
            ruined++;
        }
    }

    /** Fills the tank to capacity. */
    public synchronized void refill() {
        tank = tankCapacity;
        refills++;
    }

    /** Litres in the tank right now. */
    public double tankLevel() {
        return tank;
    }

    /** Tank size in litres; 0 if this level does not limit water. */
    public double tankCapacity() {
        return tankCapacity;
    }

    public int refills() {
        return refills;
    }

    /** The name of what is under the farmer, one of {@link #CROP_NAMES}. */
    public synchronized String cropName(Farmhand hand) {
        switch (tileUnder(hand).state) {
            case WHEAT_RIPE: return CROP_WHEAT;
            case WHEAT_GROWING: return CROP_SPROUT;
            case WHEAT_GROWING_WATERED: return CROP_WATERED;
            default: return CROP_GRASS;
        }
    }

    /** True if the tile under the farmer holds young wheat that has not been watered. */
    public synchronized boolean isDry(Farmhand hand) {
        return tileUnder(hand).state == TileState.WHEAT_GROWING;
    }

    /** True if one step in the direction stays on the farm. */
    public synchronized boolean canMove(Farmhand hand, Direction direction) {
        Farmhand.Pose current = hand.pose();
        int nx = current.x + direction.dx;
        int ny = current.y + direction.dy;
        return nx >= 0 && ny >= 0 && nx < width && ny < height;
    }

    /**
     * Walks the farmer to a tile, following columns first and rows second.
     *
     * @return how many tiles it travelled, counting sideways and up/down steps
     */
    public synchronized int walkTo(Farmhand hand, int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            throw new OutOfBoundsFarmException(x, y);
        }
        Farmhand.Pose current = hand.pose();
        int distance = Math.abs(x - current.x) + Math.abs(y - current.y);
        if (distance > 0) {
            // The renderer uses the facing of each leg. The final pose faces the last leg.
            Direction facing = y != current.y
                    ? (y > current.y ? Direction.SOUTH : Direction.NORTH)
                    : (x > current.x ? Direction.EAST : Direction.WEST);
            hand.startWalking(x, y, facing, distance * WALK_TICKS_PER_TILE);
        }
        return distance;
    }

    /** What the scanner reports for each tile of the farmer's row, west to east. */
    public synchronized int[] scanRow(Farmhand hand) {
        int[] codes = new int[width];
        int y = hand.pose().y;
        for (int x = 0; x < width; x++) codes[x] = code(tiles[y][x].state);
        return codes;
    }

    /** The scanner's report for the whole farm, indexed {@code [y][x]}. */
    public synchronized int[][] scanField() {
        int[][] codes = new int[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) codes[y][x] = code(tiles[y][x].state);
        }
        return codes;
    }

    private static int code(TileState state) {
        if (state == TileState.WHEAT_RIPE) return SCAN_RIPE;
        if (state == TileState.WHEAT_GROWING) return SCAN_YOUNG;
        return SCAN_NOTHING;
    }

    /** Bumped on every reset, so the renderer can tell a restart from a walk. */
    public int resetCount() {
        return resetCount;
    }

    public synchronized boolean isRipe(Farmhand hand) {
        return tileUnder(hand).state == TileState.WHEAT_RIPE;
    }

    private Tile tileUnder(Farmhand hand) {
        Farmhand.Pose current = hand.pose();
        return tiles[current.y][current.x];
    }

    public int wheatHarvested() {
        return wheatHarvested;
    }

    /** Ripe crops cut by this worker during the current run. */
    public synchronized int wheatHarvested(Farmhand hand) {
        return farmerHarvests.getOrDefault(hand, 0);
    }

    public int totalWheat() {
        return totalWheat;
    }

    public int ripeRemaining() {
        return totalWheat - wheatHarvested;
    }

    /** Young wheat tiles the level started with. */
    public int totalGrowing() {
        return totalGrowing;
    }

    public int watered() {
        return watered;
    }

    /** Sprouts watered by this worker during the current run. */
    public synchronized int watered(Farmhand hand) {
        return farmerWaterings.getOrDefault(hand, 0);
    }

    /** Young wheat lost to a mistake: cut before it was ripe, or watered twice. */
    public int ruined() {
        return ruined;
    }

    /** Young wheat still standing dry. */
    public synchronized int dryRemaining() {
        int dry = 0;
        for (Tile[] row : tiles) {
            for (Tile tile : row) {
                if (tile.state == TileState.WHEAT_GROWING) dry++;
            }
        }
        return dry;
    }

    private TileState initialState(int x, int y) {
        char c = layout[y].charAt(x);
        if (c == RIPE) return TileState.WHEAT_RIPE;
        if (c == GROWING) return TileState.WHEAT_GROWING;
        if (c == GROWING_WATERED) return TileState.WHEAT_GROWING_WATERED;
        return TileState.GRASS;
    }
}
