package com.ccs.coffee_n_crops.farm;

import com.ccs.coffee_n_crops.farm.error.EmptyTankException;
import com.ccs.coffee_n_crops.farm.error.FarmException;
import com.ccs.coffee_n_crops.farm.error.OutOfBoundsFarmException;

import java.util.ArrayList;
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
    public static final char COFFEE_RIPE = 'C';
    public static final char COFFEE_GROWING = 'c';
    public static final char CARROT_RIPE = 'R';
    public static final char ROCK = '#';

    /** Scanner codes: what {@code farmer.scanRow()} and {@code farmer.scanField()} report per tile. */
    public static final int SCAN_NOTHING = 0;
    /** Young crop that needs water. */
    public static final int SCAN_YOUNG = 1;
    public static final int SCAN_RIPE = 2;

    /** What {@code farmer.getCrop()} answers. */
    public static final String CROP_WHEAT = "wheat";
    public static final String CROP_SPROUT = "sprout";
    public static final String CROP_WATERED = "watered";
    public static final String CROP_GRASS = "grass";
    public static final String CROP_COFFEE = "coffee";
    public static final String CROP_CARROT = "carrot";
    /** Every answer, in the order the editor offers them as String constants. */
    public static final String[] CROP_NAMES = {
            CROP_WHEAT, CROP_SPROUT, CROP_WATERED, CROP_GRASS, CROP_COFFEE, CROP_CARROT
    };

    /** Litres one watering takes from the tank. */
    public static final double WATER_PER_USE = 0.5;
    private static final double STEP_TICKS = 1.0;
    /** Continuous walking skips the pause between separate steps. */
    public static final double WALK_TICKS_PER_TILE = 0.4;
    public static final double COFFEE_GROWTH_TICKS = 5;
    private static final double FERTILIZER_GROWTH_FACTOR = .5;
    private record Position(int x, int y) { }

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
    private final int totalCoffee;
    private final int totalCarrots;
    private final int totalGrowing;
    private final List<Position> coffeeBeds = new ArrayList<>();
    private volatile int wheatHarvested;
    private volatile int coffeeHarvested;
    private volatile int carrotsHarvested;
    private volatile int watered;
    private volatile int ruined;
    private volatile int fertilized;
    private volatile double actionTicks;
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
        int coffee = 0;
        int carrots = 0;
        int growing = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                TileState state = initialState(x, y);
                tiles[y][x] = new Tile(state);
                if (state == TileState.WHEAT_RIPE) wheat++;
                if (state.isCoffee()) coffee++;
                if (state == TileState.CARROT_RIPE) carrots++;
                if (state == TileState.COFFEE_GROWING) coffeeBeds.add(new Position(x, y));
                if (state.isDry()) growing++;
            }
        }
        this.totalWheat = wheat;
        this.totalCoffee = coffee;
        this.totalCarrots = carrots;
        this.totalGrowing = growing;
    }

    /** Workers in stable program and rendering order. */
    public List<Farmhand> farmers() { return farmers; }

    /** Puts every tile and worker back to the level's starting state. */
    public synchronized void reset() {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Tile tile = tiles[y][x];
                tile.state = initialState(x, y);
                tile.readyAt = 0;
                tile.fertilized = false;
            }
        }
        farmer.reset(startX, startY);
        if (partnerStart != null) farmers.get(1).reset(partnerStart.x(), partnerStart.y());
        wheatHarvested = 0;
        coffeeHarvested = 0;
        carrotsHarvested = 0;
        watered = 0;
        farmerHarvests.clear();
        farmerWaterings.clear();
        ruined = 0;
        fertilized = 0;
        actionTicks = 0;
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
        requireWalkable(nx, ny);
        hand.startWalking(nx, ny, direction, STEP_TICKS);
    }

    /**
     * Cuts whatever crop is under the farmer. Ripe crops are collected; young crops are destroyed
     * and counted as ruined, which is why programs should check {@link #isRipe} first.
     */
    public synchronized void harvest(Farmhand hand) {
        Tile tile = tileUnder(hand);
        if (tile.state == TileState.WHEAT_RIPE) {
            tile.state = TileState.STUBBLE;
            wheatHarvested++;
            farmerHarvests.merge(hand, 1, Integer::sum);
        } else if (tile.state == TileState.COFFEE_RIPE) {
            tile.state = TileState.STUBBLE;
            coffeeHarvested++;
        } else if (tile.state == TileState.CARROT_RIPE) {
            tile.state = TileState.STUBBLE;
            carrotsHarvested++;
        } else if (tile.state.isGrowing()) {
            tile.state = TileState.STUBBLE;
            ruined++;
        }
    }

    /**
     * Waters the tile under the farmer. A dry crop becomes watered; a young crop that is
     * already watered drowns and is counted as ruined. Anything else is unaffected.
     */
    public synchronized void water(Farmhand hand) {
        if (tankCapacity > 0) {
            // The water is spent whatever is under the farmer.
            if (tank < WATER_PER_USE) throw new EmptyTankException(tank, WATER_PER_USE);
            tank -= WATER_PER_USE;
        }
        Tile tile = tileUnder(hand);
        if (tile.state.isDry()) {
            if (tile.state == TileState.COFFEE_GROWING) {
                tile.readyAt = actionTicks + COFFEE_GROWTH_TICKS;
                tile.state = TileState.COFFEE_WATERED;
            } else {
                tile.state = TileState.WHEAT_GROWING_WATERED;
            }
            watered++;
            farmerWaterings.merge(hand, 1, Integer::sum);
        } else if (tile.state.isWatered()) {
            tile.state = TileState.STUBBLE;
            ruined++;
        }
    }

    /** The first fertilizing of a watered coffee plant halves its remaining growth time. */
    public synchronized void fertilize(Farmhand hand) {
        Tile tile = tileUnder(hand);
        if (tile.state != TileState.COFFEE_WATERED || tile.fertilized) return;
        tile.fertilized = true;
        tile.readyAt = actionTicks + (tile.readyAt - actionTicks) * FERTILIZER_GROWTH_FACTOR;
        fertilized++;
    }

    /** Advances crop growth using simulation ticks, unaffected by the run speed or debugger. */
    public synchronized void advanceTicks(double ticks) {
        if (ticks <= 0) return;
        actionTicks += ticks;
        for (Position bed : coffeeBeds) {
            Tile tile = tile(bed.x(), bed.y());
            if (tile.state == TileState.COFFEE_WATERED && tile.readyAt <= actionTicks) {
                tile.state = TileState.COFFEE_RIPE;
            }
        }
    }

    public double actionTicks() { return actionTicks; }
    public int fertilized() { return fertilized; }
    public int coffeeHarvested() { return coffeeHarvested; }
    public int totalCoffee() { return totalCoffee; }
    public int carrotsHarvested() { return carrotsHarvested; }
    public int totalCarrots() { return totalCarrots; }

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
            case COFFEE_GROWING, COFFEE_WATERED, COFFEE_RIPE: return CROP_COFFEE;
            case CARROT_RIPE: return CROP_CARROT;
            default: return CROP_GRASS;
        }
    }

    /** True if the tile under the farmer holds a young crop that has not been watered. */
    public synchronized boolean isDry(Farmhand hand) {
        return tileUnder(hand).state.isDry();
    }

    /** True if the neighboring tile is on the farm and clear of obstacles. */
    public synchronized boolean canMove(Farmhand hand, Direction direction) {
        Farmhand.Pose current = hand.pose();
        int nx = current.x + direction.dx;
        int ny = current.y + direction.dy;
        return inBounds(nx, ny) && tile(nx, ny).state != TileState.ROCK;
    }

    /**
     * Walks the farmer to a tile, following columns first and rows second.
     *
     * @return how many tiles it travelled, counting sideways and up/down steps
     */
    public synchronized int walkTo(Farmhand hand, int x, int y) {
        requireWalkable(x, y);
        Farmhand.Pose current = hand.pose();
        // Validate the whole route before publishing a new pose.
        int routeX = current.x;
        int routeY = current.y;
        while (routeX != x) {
            routeX += Integer.signum(x - routeX);
            requireWalkable(routeX, routeY);
        }
        while (routeY != y) {
            routeY += Integer.signum(y - routeY);
            requireWalkable(routeX, routeY);
        }
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

    private boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    private void requireWalkable(int x, int y) {
        if (!inBounds(x, y)) throw new OutOfBoundsFarmException(x, y);
        if (tile(x, y).state == TileState.ROCK) {
            throw new FarmException("the path is blocked at (" + x + ", " + y + ")");
        }
    }

    /** What the scanner reports for each tile of the farmer's row, west to east. */
    public synchronized int[] scanRow(Farmhand hand) {
        int[] codes = new int[width];
        int y = hand.pose().y;
        for (int x = 0; x < width; x++) codes[x] = code(tiles[y][x].state);
        return codes;
    }

    /** The crop under the farmhand, including its growth stage. */
    public synchronized Crop scanCrop(Farmhand hand) {
        return Crop.fromTile(tileUnder(hand).state);
    }

    /** The row's crops from west to east, with no numeric codes to decode. */
    public synchronized Crop[] scanCrops(Farmhand hand) {
        Crop[] crops = new Crop[width];
        int y = hand.pose().y;
        for (int x = 0; x < width; x++) crops[x] = Crop.fromTile(tiles[y][x].state);
        return crops;
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
        if (state.isRipe()) return SCAN_RIPE;
        if (state.isDry()) return SCAN_YOUNG;
        return SCAN_NOTHING;
    }

    /** Bumped on every reset, so the renderer can tell a restart from a walk. */
    public int resetCount() {
        return resetCount;
    }

    public synchronized boolean isRipe(Farmhand hand) {
        return tileUnder(hand).state.isRipe();
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

    /** Dry young crops the level started with. */
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

    /** Young crops lost to a mistake: cut before they were ripe, or watered twice. */
    public int ruined() {
        return ruined;
    }

    /** Young crops still standing dry. */
    public synchronized int dryRemaining() {
        int dry = 0;
        for (Tile[] row : tiles) {
            for (Tile tile : row) {
                if (tile.state.isDry()) dry++;
            }
        }
        return dry;
    }

    private TileState initialState(int x, int y) {
        char c = layout[y].charAt(x);
        if (c == RIPE) return TileState.WHEAT_RIPE;
        if (c == GROWING) return TileState.WHEAT_GROWING;
        if (c == GROWING_WATERED) return TileState.WHEAT_GROWING_WATERED;
        if (c == COFFEE_RIPE) return TileState.COFFEE_RIPE;
        if (c == COFFEE_GROWING) return TileState.COFFEE_GROWING;
        if (c == CARROT_RIPE) return TileState.CARROT_RIPE;
        if (c == ROCK) return TileState.ROCK;
        return TileState.GRASS;
    }
}
