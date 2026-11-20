package com.ccs.coffee_n_crops.progress;

import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.lang.ast.AstWalk;
import com.ccs.coffee_n_crops.lang.ast.ClassDecl;
import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.MethodDecl;
import com.ccs.coffee_n_crops.lang.ast.Node;
import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.ast.expr.Access;
import com.ccs.coffee_n_crops.lang.ast.expr.FieldAccess;
import com.ccs.coffee_n_crops.lang.ast.expr.InstanceCall;
import com.ccs.coffee_n_crops.lang.ast.expr.NewObject;
import com.ccs.coffee_n_crops.lang.ast.expr.ThisRef;
import com.ccs.coffee_n_crops.lang.ast.expr.MethodCall;
import com.ccs.coffee_n_crops.lang.ast.expr.UserCall;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.AssertStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForEachStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ListAdd;
import com.ccs.coffee_n_crops.lang.ast.stmt.ReturnStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.SuperCall;
import com.ccs.coffee_n_crops.lang.ast.stmt.TryStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.ast.stmt.WhileStmt;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.runtime.ExceptionTypes;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.lang.reflect.Modifier;

/** All levels in curriculum order. */
public final class LevelCatalog {
    public static final int SANDBOX_INDEX = -1;
    private static final List<LevelDef> LEVELS = createLevels();
    private static final LevelDef SANDBOX = createSandbox();

    private static List<LevelDef> createLevels() {
        List<LevelDef> levels = new ArrayList<>(Arrays.asList(
                loops(), variables(), conditionals(), whileAndElseIf(), forEach(), switchOnPattern(),
                arrays(), grids(), lists(), doubles(), strings(), reusableMethods(), returnValues(),
                methodParameters(), recursion(), recoverEmptyTank(), findEdgeWithException(), finallyHome(),
                objectFields(), constructors(), instanceWorkers(), inheritance()));
        levels.addAll(PracticeLevels.all());
        levels.addAll(PolymorphismLevels.all());
        levels.addAll(AdvancedOopLevels.all());
        levels.addAll(ModernCollectionsLevels.all());
        levels.add(assertions());
        levels.addAll(ConcurrencyLevels.all());
        levels.addAll(StreamLevels.all());
        levels.addAll(FarmhandTeamLevels.all());
        levels.addAll(CoffeeLevels.all());
        levels.addAll(LanguagePracticeLevels.all());
        return Collections.unmodifiableList(levels);
    }

    private LevelCatalog() {
    }

    public static List<LevelDef> all() {
        return LEVELS;
    }

    public static LevelDef get(int index) {
        return index == SANDBOX_INDEX ? SANDBOX : LEVELS.get(index);
    }

    private static LevelDef createSandbox() {
        // Palette additions also belong in the sandbox before they get a dedicated lesson.
        List<String> keys = new ArrayList<>();
        for (var field : Unlock.class.getFields()) {
            if (field.getType() != String.class || !Modifier.isStatic(field.getModifiers())) continue;
            try {
                keys.add((String) field.get(null));
            } catch (IllegalAccessException failure) {
                throw new IllegalStateException("Cannot read an unlock key", failure);
            }
        }
        return new LevelDef("sandbox", "Sandbox farm", "Experiment with every available Java block",
                "Write any program for this farm. Reset to restore the crops and both farmhands. "
                        + "This garden is separate from the lesson sequence.",
                new String[]{"WW.Cc.", ".#..#R", "cg..C.", "..#..R", ".Wg.w."},
                0, 0, (world, program) -> null, keys.toArray(new String[0]))
                .withTank(2, 3).withSecondFarmhand(0, 4).asSandbox();
    }

    private static LevelDef loops() {
        return new LevelDef(
                "level_01", "Level 1", "Harvest all 5 wheat and reach the flag",
                "Tap a block below to add it to run(). A for loop repeats everything between its braces. Finish on the grass tile with the orange flag.",
                new String[]{"WWWWW."}, 0, 0,
                (world, program) -> wheatLeft(world),
                Unlock.MOVE, Unlock.HARVEST, Unlock.FOR).withFinish(5, 0);
    }

    private static LevelDef variables() {
        return new LevelDef(
                "level_02", "Level 2", "Harvest both rows using an int variable",
                "Both rows are the same length. Store it once: add int steps = 0; then tap a number "
                        + "in your code to swap it for steps.",
                new String[]{"WWWWW.", "WWWWW."}, 0, 0,
                (world, program) -> {
                    String left = wheatLeft(world);
                    if (left != null) return left;
                    return usesOwnVariable(program) ? null
                            : "All the wheat is in, but this level asks for a variable. Declare an int "
                            + "and use it in place of a number.";
                },
                Unlock.MOVE, Unlock.HARVEST, Unlock.FOR, Unlock.INT, Unlock.ASSIGN, Unlock.MATH);
    }

    private static LevelDef conditionals() {
        return new LevelDef(
                "level_03", "Level 3", "Harvest the ripe wheat, water the young wheat",
                "Green sprouts are not ripe: harvesting one ruins it. Ask farmer.isRipe() in an if, "
                        + "and water the sprouts in the else branch.",
                new String[]{"WgWWgW."}, 0, 0,
                (world, program) -> tended(world,
                        "Check farmer.isRipe() before harvesting.",
                        "Call farmer.water() in the else branch."),
                Unlock.MOVE, Unlock.HARVEST, Unlock.WATER, Unlock.IS_RIPE, Unlock.FOR, Unlock.IF,
                Unlock.INT, Unlock.BOOLEAN, Unlock.ASSIGN, Unlock.MATH, Unlock.LOGIC);
    }

    private static LevelDef whileAndElseIf() {
        return new LevelDef(
                "level_04", "Level 4", "Tend the whole row with a while loop",
                "No for loop here: repeat while farmer.canMove(Direction.EAST). Dark soil is already "
                        + "watered, and watering it again drowns the plant, so ask farmer.isDry() in an else if.",
                new String[]{"WgwWgWwW."}, 0, 0,
                (world, program) -> tended(world,
                        "Harvest only if farmer.isRipe(), and water only if farmer.isDry().",
                        "Water it in an else if (farmer.isDry()) arm."),
                Unlock.MOVE, Unlock.HARVEST, Unlock.WATER, Unlock.IS_RIPE, Unlock.IS_DRY, Unlock.CAN_MOVE,
                Unlock.WHILE, Unlock.IF, Unlock.ELSE_IF, Unlock.LOGIC);
    }

    private static LevelDef forEach() {
        return new LevelDef(
                "level_05", "Level 5", "Harvest the ring with a for-each loop",
                "Direction.values() is NORTH, EAST, SOUTH, WEST: exactly one lap around the pond. "
                        + "Loop over it, and tap a direction in your code to swap it for the loop variable.",
                new String[]{"WWW", "W.W", "WWW"}, 0, 2,
                (world, program) -> {
                    String left = wheatLeft(world);
                    if (left != null) return left;
                    return contains(program, ForEachStmt.class) ? null
                            : "All the wheat is in, but this level asks for a for-each loop over "
                            + "Direction.values().";
                },
                Unlock.MOVE, Unlock.HARVEST, Unlock.FOR, Unlock.FOR_EACH);
    }

    private static LevelDef switchOnPattern() {
        return new LevelDef(
                "level_06", "Level 6", "The sensors are down: use the pattern",
                "No isRipe() today. The row repeats every 3 tiles: ripe, young, grass. "
                        + "i % 3 tells you which tile the farmer is on, so switch on it.",
                new String[]{"Wg.Wg.Wg.."}, 0, 0,
                (world, program) -> tended(world,
                        "Harvest only where i % 3 is 0.",
                        "Water where i % 3 is 1."),
                Unlock.MOVE, Unlock.HARVEST, Unlock.WATER, Unlock.FOR, Unlock.SWITCH, Unlock.MATH);
    }

    private static LevelDef arrays() {
        return new LevelDef(
                "level_07", "Level 7", "Scan the row into an int[] and tend it",
                "farmer.scanRow() returns one number per tile: 2 is ripe, 1 is young, 0 is nothing. "
                        + "Loop i from 0 to row.length, walk to tile i and act on row[i].",
                new String[]{"gW.WgWW.g"}, 0, 0,
                (world, program) -> {
                    String missing = tended(world,
                            "Harvest only where row[i] == 2.", "Water where row[i] == 1.");
                    if (missing != null) return missing;
                    return reads(program, Access.Kind.INDEX) ? null
                            : "The row is tended, but this level asks you to read the scan with row[i].";
                },
                Unlock.WALK_TO, Unlock.HARVEST, Unlock.WATER, Unlock.FOR, Unlock.IF, Unlock.ELSE_IF,
                Unlock.LOGIC, Unlock.MATH, Unlock.ARRAY);
    }

    private static LevelDef grids() {
        return new LevelDef(
                "level_08", "Level 8", "Scan the whole field into an int[][]",
                "A 2D array is rows of columns: field[y][x]. field.length is the number of rows, "
                        + "field[0].length the number of columns. Nest two loops and walk only where there is work.",
                new String[]{"W.gW.", ".WW.g", "g..W.", "W.g.W"}, 0, 0,
                (world, program) -> {
                    String missing = tended(world,
                            "Harvest only where field[y][x] == 2.", "Water where field[y][x] == 1.");
                    if (missing != null) return missing;
                    return reads(program, Access.Kind.GRID_INDEX) ? null
                            : "The field is tended, but this level asks you to read the scan with field[y][x].";
                },
                Unlock.WALK_TO, Unlock.HARVEST, Unlock.WATER, Unlock.FOR, Unlock.IF, Unlock.ELSE_IF,
                Unlock.LOGIC, Unlock.MATH, Unlock.GRID);
    }

    private static LevelDef lists() {
        return new LevelDef(
                "level_09", "Level 9", "Collect the ripe tiles in an ArrayList, then visit them",
                "First pass: for every i where row[i] == 2, call list.add(i). "
                        + "Second pass: a for-each over the list walks to each saved tile and harvests it.",
                new String[]{"..W..W...WW.W"}, 0, 0,
                (world, program) -> {
                    String left = wheatLeft(world);
                    if (left != null) return left;
                    boolean adds = contains(program, ListAdd.class);
                    boolean[] iterates = new boolean[1];
                    AstWalk.walk(program, node -> iterates[0] |= node instanceof ForEachStmt
                            && ((ForEachStmt) node).source != null);
                    return adds && (iterates[0] || reads(program, Access.Kind.LIST_GET)) ? null
                            : "All the wheat is in, but this level asks you to add the ripe tiles to an "
                            + "ArrayList and then loop over that list.";
                },
                Unlock.WALK_TO, Unlock.HARVEST, Unlock.FOR, Unlock.IF, Unlock.LOGIC, Unlock.MATH,
                Unlock.ARRAY, Unlock.LIST);
    }

    private static LevelDef doubles() {
        // 8 sprouts at 0.5 L each from a 1.5 L tank: three waterings, then a refill, twice.
        int enoughRefills = 2;
        return new LevelDef(
                "level_10", "Level 10", "Water every sprout from a 1.5 L tank",
                "Each watering takes 0.5 L, and watering on an empty tank stops the farmer. "
                        + "farmer.getWater() is a double: refill only when it is below 0.5.",
                new String[]{"gggggggg."}, 0, 0,
                (world, program) -> {
                    String missing = tended(world,
                            "Water each sprout once.", "Keep going to the end of the row.");
                    if (missing != null) return missing;
                    return world.refills() <= enoughRefills ? null
                            : "Every sprout is watered, but the farmer refilled " + world.refills()
                            + " times and " + enoughRefills + " are enough. Refill only if "
                            + "farmer.getWater() < 0.5.";
                },
                Unlock.MOVE, Unlock.WATER, Unlock.CAN_MOVE, Unlock.WHILE, Unlock.IF, Unlock.LOGIC,
                Unlock.MATH, Unlock.DOUBLE, Unlock.TANK)
                .withTank(1.5, 1.5);
    }

    private static LevelDef strings() {
        return new LevelDef(
                "level_11", "Level 11", "Ask the farmer what's on this tile",
                "farmer.getCrop() answers with a String: \"wheat\", \"sprout\", \"watered\" or \"grass\". "
                        + "Switch on it, or compare with .equals(): never == for text.",
                new String[]{"WgW.gWWg."}, 0, 0,
                (world, program) -> tended(world,
                        "Harvest only when the crop is \"wheat\", and water only a \"sprout\".",
                        "Water it in the \"sprout\" case."),
                Unlock.MOVE, Unlock.HARVEST, Unlock.WATER, Unlock.CAN_MOVE, Unlock.WHILE, Unlock.IF,
                Unlock.ELSE_IF, Unlock.LOGIC, Unlock.SWITCH, Unlock.STRING, Unlock.GET_CROP);
    }

    private static LevelDef reusableMethods() {
        return new LevelDef(
                "level_12", "Level 12 · Methods", "Harvest three rows with one reusable method",
                "Use + Method to add void harvestRow(Farmhand farmer). Build the five-tile harvest "
                        + "loop inside it. Tap run() above the code to go back. Walk to the start of each row "
                        + "and insert harvestRow(farmer) from the palette. A method has its own local variables.",
                new String[]{"WWWWW.", "WWWWW.", "WWWWW."}, 0, 0,
                (world, program) -> {
                    String missing = wheatLeft(world);
                    if (missing != null) return missing;
                    for (MethodDecl method : program.methods()) {
                        if (method.returnType == JType.VOID && method.parameters.isEmpty()
                                && hasFarmerCall(method.body, "harvest") && isCalled(program, method.name)
                                && (calledInLoop(program, method.name)
                                || callCount(program, method.name) >= world.height)) {
                            return null;
                        }
                    }
                    return "The wheat is in. Put the row's harvest code in one void method, "
                            + "then call that method for all three rows from run().";
                },
                Unlock.MOVE, Unlock.WALK_TO, Unlock.HARVEST, Unlock.FOR, Unlock.INT,
                Unlock.MATH, Unlock.METHODS);
    }

    private static LevelDef returnValues() {
        return new LevelDef(
                "level_13", "Level 13 · Return values", "Count the ripe wheat in a method, then harvest it",
                "Use + Method to add int countRipe(Farmhand farmer). Scan the row, count entries equal to 2 and "
                        + "return the count. In run(), open Call… to store countRipe(farmer) in an int and use "
                        + "it as the harvest loop's limit. return ends the method and sends back a value.",
                new String[]{"WWWWW."}, 0, 0,
                (world, program) -> {
                    String missing = wheatLeft(world);
                    if (missing != null) return missing;
                    for (MethodDecl method : program.methods()) {
                        if (method.returnType == JType.INT && contains(method.body, ReturnStmt.class)
                                && reads(method.body, Access.Kind.INDEX)
                                && returnControlsLoop(program, method.name)) return null;
                    }
                    return "Count the scan's ripe entries in an int method, return that count, "
                            + "and use the returned value to control the harvest loop in run().";
                },
                Unlock.MOVE, Unlock.HARVEST, Unlock.FOR, Unlock.IF, Unlock.INT, Unlock.ASSIGN,
                Unlock.MATH, Unlock.LOGIC, Unlock.ARRAY, Unlock.METHODS, Unlock.RETURN);
    }

    private static LevelDef methodParameters() {
        return new LevelDef(
                "level_14", "Level 14 · Parameters", "Tend three different rows with tendRow(farmer, y)",
                "Add void tendRow(Farmhand farmer, int y). Loop across the six columns, walk to (i, y), "
                        + "harvest ripe wheat and water dry sprouts. In run(), loop over the rows "
                        + "and pass the loop variable to tendRow(farmer, y). Each call gets its own y.",
                new String[]{"Wg.Wg.", "g.WW.g", ".gW.gW"}, 0, 0,
                (world, program) -> {
                    String missing = tended(world, "Check isRipe() inside your method.",
                            "Water only when isDry() is true.");
                    if (missing != null) return missing;
                    for (MethodDecl method : program.methods()) {
                        if (method.returnType != JType.VOID || method.parameters.isEmpty()
                                || !calledInLoop(program, method.name)) continue;
                        for (VarDecl parameter : method.parameters) {
                            if (parameter.type == JType.INT && readsName(method.body, parameter.name)
                                    && hasFarmerCall(method.body, "walkTo")) {
                                return null;
                            }
                        }
                    }
                    return "Use one method with an int parameter for the row. Read that parameter "
                            + "inside the method and call it from run()'s row loop.";
                },
                Unlock.WALK_TO, Unlock.HARVEST, Unlock.WATER, Unlock.IS_RIPE, Unlock.IS_DRY,
                Unlock.FOR, Unlock.IF, Unlock.ELSE_IF, Unlock.INT, Unlock.MATH, Unlock.LOGIC,
                Unlock.METHODS, Unlock.PARAMETERS);
    }

    private static LevelDef recursion() {
        return new LevelDef(
                "level_15", "Level 15 · Recursion", "Harvest to the edge by calling the method again",
                "Add void walkToEdge(Farmhand farmer). Harvest if the tile is ripe. If the farmer can "
                        + "move EAST, move once and call walkToEdge(farmer) again. At the edge, stop "
                        + "calling. Call the method once from run(). Step follows every call and return.",
                new String[]{"W.W.WW.W"}, 0, 0,
                (world, program) -> {
                    String missing = wheatLeft(world);
                    if (missing != null) return missing;
                    if (world.farmer.pose().x != world.width - 1) return "Keep going until the eastern edge.";
                    for (MethodDecl method : program.methods()) {
                        if (isCalled(program, method.name) && callCount(method.body, method.name) > 0
                                && hasFarmerCall(method.body, "harvest")) return null;
                    }
                    return "Put the harvest and movement in a method that calls itself, "
                            + "and start it with one call from run().";
                },
                Unlock.MOVE, Unlock.HARVEST, Unlock.IS_RIPE, Unlock.CAN_MOVE, Unlock.IF,
                Unlock.LOGIC, Unlock.METHODS, Unlock.RECURSION).withFinish(7, 0);
    }

    private static LevelDef recoverEmptyTank() {
        return new LevelDef(
                "level_16", "Level 16 · Catch and recover", "Water seven sprouts, recovering from an empty tank",
                "The 1 L tank starts empty. Put farmer.water() in try inside a seven-step for loop. "
                        + "In catch (EmptyTankException e), refill and water again, then move EAST "
                        + "after the catch. Java skips the rest of try after a failure: it does not retry the failed line.",
                new String[]{"ggggggg."}, 0, 0,
                (world, program) -> {
                    String missing = tended(world, "Water each sprout exactly once.",
                            "After refilling in catch, call farmer.water() again before moving.");
                    if (missing != null) return missing;
                    if (world.caughtExceptions(ExceptionTypes.EMPTY_TANK) == 0
                            || !hasRecovery(program, ExceptionTypes.EMPTY_TANK, "water", "refill", "water")) {
                        return "Recover from EmptyTankException in catch: refill, then retry watering "
                                + "that same tile. Refilling before try skips this lesson.";
                    }
                    int enoughRefills = (int) Math.ceil(world.totalGrowing() * FarmWorld.WATER_PER_USE
                            / world.tankCapacity());
                    return world.refills() <= enoughRefills ? null
                            : "All the sprouts are watered, but " + enoughRefills + " refills are enough. "
                            + "Refill only in the EmptyTankException catch arm.";
                },
                Unlock.MOVE, Unlock.WATER, Unlock.FOR, Unlock.TANK,
                Unlock.TRY, Unlock.CATCH_EMPTY_TANK)
                .withTank(0, 1.0);
    }

    private static LevelDef findEdgeWithException() {
        return new LevelDef(
                "level_17", "Level 17 · Find the edge", "Harvest until movement throws an exception",
                "No edge sensor today. Put while (true) inside try: harvest, then move EAST. "
                        + "The last move throws OutOfBoundsFarmException and exits the loop. "
                        + "Catch it after the loop; an empty catch is enough here. The farmer stays on the last tile.",
                new String[]{"WWWWWWW"}, 0, 0,
                (world, program) -> {
                    String missing = wheatLeft(world);
                    if (missing != null) return missing;
                    if (world.farmer.pose().x != world.width - 1) return "Finish on the eastern edge of the field.";
                    return world.caughtExceptions(ExceptionTypes.OUT_OF_BOUNDS) > 0
                            && hasRecovery(program, ExceptionTypes.OUT_OF_BOUNDS, "move") ? null
                            : "Find the edge by catching OutOfBoundsFarmException. Put try around "
                            + "the whole loop, so catching the failed move ends the loop.";
                },
                Unlock.MOVE, Unlock.HARVEST, Unlock.WHILE,
                Unlock.TRY, Unlock.CATCH_OUT_OF_BOUNDS).withFinish(6, 0);
    }

    private static LevelDef finallyHome() {
        return new LevelDef(
                "level_18", "Level 18 · Always come home", "Harvest to the edge, then return home in finally",
                "Wrap the harvest-and-move loop in try. Catch OutOfBoundsFarmException and add "
                        + "return; there. Add finally with farmer.walkTo(0, 0). Even return must run "
                        + "finally before leaving the method. Step through the catch, then the cleanup.",
                new String[]{"WWWWWW"}, 0, 0,
                (world, program) -> {
                    String missing = wheatLeft(world);
                    if (missing != null) return missing;
                    if (world.farmer.pose().x != 0 || world.farmer.pose().y != 0) {
                        return "Put farmer.walkTo(0, 0) in finally so the farmer comes home even after return.";
                    }
                    if (world.finallyRuns() == 0 || !hasReturningCleanup(program)) {
                        return "Use a finally block to walk home, and return from the catch arm. "
                                + "A line after try is skipped by return; finally is still executed.";
                    }
                    return world.caughtExceptions(ExceptionTypes.OUT_OF_BOUNDS) > 0
                            || world.caughtExceptions(ExceptionTypes.FARM) > 0 ? null
                            : "Reach the edge and handle its exception before returning through finally.";
                },
                Unlock.MOVE, Unlock.WALK_TO, Unlock.HARVEST, Unlock.WHILE, Unlock.METHODS,
                Unlock.TRY, Unlock.CATCH_EMPTY_TANK, Unlock.CATCH_OUT_OF_BOUNDS,
                Unlock.CATCH_FARM, Unlock.FINALLY).withFinish(0, 0);
    }

    private static LevelDef objectFields() {
        return new LevelDef(
                "level_19", "Level 19 · Classes and fields", "Let a Counter object's field control the harvest",
                "Use + Class to create Counter, then + Field for int value = 0. In Program, "
                        + "create Counter counter = new Counter(). Repeat while counter.value < 5: "
                        + "harvest, add 1 to counter.value and move EAST. Fields belong to an object, "
                        + "not to run()'s local scope.",
                new String[]{"WWWWW."}, 0, 0,
                (world, program) -> {
                    String missing = wheatLeft(world);
                    if (missing != null) return missing;
                    for (ClassDecl cls : program.classes()) {
                        if (world.objectCreations(cls.name) == 0) continue;
                        for (VarDecl field : cls.fields()) {
                            if (field.type == JType.INT && world.fieldWrites(cls.name, field.name) > 0
                                    && fieldControlsLoop(program, cls.name, field.name)) return null;
                        }
                    }
                    return "Create a Counter object, update its int field during the run, and read "
                            + "that field in the harvest loop's condition.";
                },
                Unlock.MOVE, Unlock.HARVEST, Unlock.WHILE, Unlock.INT, Unlock.MATH,
                Unlock.LOGIC, Unlock.ASSIGN, Unlock.CLASSES);
    }

    private static LevelDef constructors() {
        return new LevelDef(
                "level_20", "Level 20 · Constructors", "Give two RowPlan objects different rows and widths",
                "In RowPlan, use + Field for int row and int width. Use + Constructor, then add two int "
                        + "parameters and assign this.row = row; this.width = width;. In Program, "
                        + "make plans (0, 3) and (1, 5). For each plan, harvest columns 0 to plan.width "
                        + "on plan.row. Each object keeps its own field values.",
                new String[]{"WWW...", "WWWWW."}, 0, 0,
                (world, program) -> {
                    String missing = wheatLeft(world);
                    if (missing != null) return missing;
                    for (ClassDecl cls : program.classes()) {
                        if (cls.constructor() == null || cls.constructor().parameters.size() < world.height
                                || world.objectCreations(cls.name) < world.height) continue;
                        int initialized = 0;
                        for (VarDecl field : cls.fields()) {
                            if (field.type == JType.INT && world.fieldWrites(cls.name, field.name) >= world.height
                                    && readsObjectField(program, cls.name, field.name)) initialized++;
                        }
                        if (initialized >= world.height) return null;
                    }
                    return "Construct two RowPlan objects with separate row and width values. "
                            + "Initialize both fields with this in the constructor and read them in Program.";
                },
                Unlock.WALK_TO, Unlock.HARVEST, Unlock.FOR, Unlock.INT, Unlock.MATH, Unlock.LOGIC,
                Unlock.ASSIGN, Unlock.CLASSES, Unlock.CONSTRUCTORS);
    }

    private static LevelDef instanceWorkers() {
        return new LevelDef(
                "level_21", "Level 21 · Instance methods", "Give each Worker its own row to tend",
                "In Worker, use + Field for Farmhand farmer and int row. Its constructor receives both "
                        + "and assigns this.farmer and this.row. Use + Method to add void work() and loop over six columns, "
                        + "walk with this.farmer to (i, this.row), then harvest ripe wheat or water dry "
                        + "sprouts. In Program, construct one Worker per row and call worker.work().",
                new String[]{"Wg.Wg.", "g.WW.g"}, 0, 0,
                (world, program) -> {
                    String missing = tended(world, "Check isRipe() inside Worker.work().",
                            "Water only when isDry() is true.");
                    if (missing != null) return missing;
                    for (ClassDecl cls : program.classes()) {
                        if (world.objectCreations(cls.name) < world.height) continue;
                        for (MethodDecl method : cls.methods()) {
                            if (world.instanceCalls(cls.name, method.name) >= world.height
                                    && usesThis(method.body) && hasFarmhandField(cls)
                                    && readsInitializedRow(world, cls, method.body)) return null;
                        }
                    }
                    return "Use two Worker instances and call an instance method on each. "
                            + "That method should use this and the object's Farmhand field to tend its own row.";
                },
                Unlock.WALK_TO, Unlock.HARVEST, Unlock.WATER, Unlock.IS_RIPE, Unlock.IS_DRY,
                Unlock.FOR, Unlock.IF, Unlock.ELSE_IF, Unlock.INT, Unlock.MATH, Unlock.LOGIC,
                Unlock.ASSIGN, Unlock.CLASSES, Unlock.CONSTRUCTORS, Unlock.INSTANCE_METHODS);
    }

    private static LevelDef inheritance() {
        return new LevelDef(
                "level_22", "Level 22 · Inheritance", "Run Harvester.work() through a Worker reference",
                "Create Worker with a Farmhand field, a constructor and void work(). Create Harvester "
                        + "extending Worker. Its constructor starts with super(farmer). Use @Override "
                        + "to give work() a six-tile harvest loop using the inherited this.farmer. "
                        + "In Program, declare Worker worker = new Harvester(farmer); then call worker.work(). "
                        + "The actual object's class chooses which work() runs.",
                new String[]{"WWWWWW."}, 0, 0,
                (world, program) -> {
                    String missing = wheatLeft(world);
                    if (missing != null) return missing;
                    for (ClassDecl cls : program.classes()) {
                        if (cls.superName == null || cls.constructor() == null
                                || cls.constructor().body.size() == 0
                                || !(cls.constructor().body.get(0) instanceof SuperCall)
                                || world.objectCreations(cls.name) == 0
                                || world.superCalls(cls.name) == 0 || !usesBaseReference(program, cls)) continue;
                        for (MethodDecl method : cls.methods()) {
                            if (method.override && world.instanceCalls(cls.name, method.name) > 0) return null;
                        }
                    }
                    return "Construct a subclass with super(...), override its work() method, "
                            + "and invoke it through a variable declared with the base class's type.";
                },
                Unlock.MOVE, Unlock.HARVEST, Unlock.FOR, Unlock.INT, Unlock.MATH, Unlock.LOGIC,
                Unlock.ASSIGN, Unlock.CLASSES, Unlock.CONSTRUCTORS, Unlock.INSTANCE_METHODS,
                Unlock.INHERITANCE);
    }

    private static LevelDef assertions() {
        return new LevelDef(
                "level_43", "Level 43 · Assertions", "Check that the wheat is ripe before harvesting",
                "Repeat three times: add assert farmer.isRipe();, harvest, then move EAST. "
                        + "Try moving the assertion below harvest to see the error. The game enables "
                        + "assertions, like java -ea. You can add a message after the colon.",
                new String[]{"WWW."}, 0, 0,
                (world, program) -> {
                    String missing = wheatLeft(world);
                    if (missing != null) return missing;
                    boolean[] checked = {false};
                    AstWalk.walk(program, node -> {
                        if (node instanceof AssertStmt check && world.usedLessonNode(check.id)
                                && isFarmerCall(check.condition.get(), "isRipe")) checked[0] = true;
                    });
                    return checked[0] ? null : "Use assert farmer.isRipe(); before cutting wheat.";
                }, Unlock.FOR, Unlock.IS_RIPE, Unlock.HARVEST, Unlock.MOVE, Unlock.ASSERT, Unlock.STRING)
                .withFinish(3, 0);
    }

    private static boolean isFarmerCall(Node expression, String name) {
        return expression instanceof MethodCall call && call.receiver.equals("farmer") && call.name.equals(name);
    }

    private static boolean fieldControlsLoop(Block program, String cls, String name) {
        boolean[] found = new boolean[1];
        for (Stmt statement : program.statements()) {
            AstWalk.walk(statement, node -> {
                Expr condition = node instanceof WhileStmt ? ((WhileStmt) node).cond.get()
                        : node instanceof ForStmt ? ((ForStmt) node).cond.get() : null;
                if (condition != null) AstWalk.walk(condition, value -> found[0] |= isField(value, cls, name));
            });
        }
        return found[0];
    }

    private static boolean readsObjectField(Block program, String cls, String name) {
        boolean[] found = new boolean[1];
        for (Stmt statement : program.statements()) {
            AstWalk.walk(statement, node -> found[0] |= isField(node, cls, name));
        }
        return found[0];
    }

    private static boolean isField(Node node, String cls, String name) {
        return node instanceof FieldAccess && ((FieldAccess) node).name.equals(name)
                && ((FieldAccess) node).receiver.type == JType.reference(cls);
    }

    private static boolean usesThis(Block body) {
        boolean[] found = new boolean[1];
        AstWalk.walk(body, node -> found[0] |= node instanceof ThisRef);
        return found[0];
    }

    private static boolean hasFarmhandField(ClassDecl cls) {
        for (VarDecl field : cls.fields()) if (field.type == JType.FARMHAND) return true;
        return false;
    }

    private static boolean readsInitializedRow(FarmWorld world, ClassDecl cls, Block body) {
        for (VarDecl field : cls.fields()) {
            if (field.type != JType.INT || world.fieldWrites(cls.name, field.name) < world.height) continue;
            boolean[] read = new boolean[1];
            AstWalk.walk(body, node -> read[0] |= isField(node, cls.name, field.name));
            if (read[0]) return true;
        }
        return false;
    }

    private static boolean usesBaseReference(Block program, ClassDecl cls) {
        Set<String> references = new HashSet<>();
        for (Stmt statement : program.statements()) {
            AstWalk.walk(statement, node -> {
                if (!(node instanceof VarDecl)) return;
                VarDecl variable = (VarDecl) node;
                if (variable.type == JType.reference(cls.superName) && variable.init.get() instanceof NewObject
                        && ((NewObject) variable.init.get()).className.equals(cls.name)) references.add(variable.name);
            });
        }
        boolean[] used = new boolean[1];
        for (Stmt statement : program.statements()) {
            AstWalk.walk(statement, node -> {
                if (!(node instanceof InstanceCall)) return;
                Expr receiver = ((InstanceCall) node).receiver.get();
                if (receiver instanceof VarRef && references.contains(((VarRef) receiver).name)) used[0] = true;
            });
        }
        return used[0];
    }

    /** Restrict lesson inspection to run and helpers it can call, excluding unused demonstrations. */
    private static List<TryStmt> reachableTries(Block program) {
        List<TryStmt> tries = new ArrayList<>();
        List<Block> pending = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        pending.add(program);
        for (int i = 0; i < pending.size(); i++) {
            for (Stmt statement : pending.get(i).statements()) {
                AstWalk.walk(statement, node -> {
                    if (node instanceof TryStmt) tries.add((TryStmt) node);
                    if (node instanceof UserCall) {
                        String name = ((UserCall) node).name;
                        if (!visited.add(name)) return;
                        for (MethodDecl method : program.methods()) {
                            if (method.name.equals(name)) pending.add(method.body);
                        }
                    }
                });
            }
        }
        return tries;
    }

    private static boolean hasRecovery(Block program, String type, String protectedAction,
                                       String... recoveryActions) {
        for (TryStmt attempt : reachableTries(program)) {
            if (!hasFarmerCall(attempt.tryBlock, protectedAction)) continue;
            for (TryStmt.Catch arm : attempt.catches()) {
                if (!arm.typeName.equals(type)) continue;
                boolean complete = true;
                for (String action : recoveryActions) complete &= hasFarmerCall(arm.body, action);
                if (complete) return true;
            }
        }
        return false;
    }

    private static boolean hasReturningCleanup(Block program) {
        for (TryStmt attempt : reachableTries(program)) {
            if (attempt.finallyBlock() == null || !hasFarmerCall(attempt.finallyBlock(), "walkTo")) continue;
            for (TryStmt.Catch arm : attempt.catches()) {
                if (ExceptionTypes.covers(arm.typeName, ExceptionTypes.OUT_OF_BOUNDS)
                        && contains(arm.body, ReturnStmt.class)) return true;
            }
        }
        return false;
    }

    private static boolean hasFarmerCall(Block body, String name) {
        boolean[] found = new boolean[1];
        AstWalk.walk(body, node -> found[0] |= isFarmerCall(node, name));
        return found[0];
    }

    private static boolean readsName(Block body, String name) {
        boolean[] found = new boolean[1];
        AstWalk.walk(body, node -> found[0] |= node instanceof VarRef && ((VarRef) node).name.equals(name));
        return found[0];
    }

    /** Only walk run(), so an unused helper cannot satisfy a method lesson's goal. */
    private static int callCount(Block body, String name) {
        int[] count = new int[1];
        for (Stmt statement : body.statements()) {
            AstWalk.walk(statement, node -> {
                if (node instanceof UserCall && ((UserCall) node).name.equals(name)) count[0]++;
            });
        }
        return count[0];
    }

    private static boolean isCalled(Block program, String name) {
        return callCount(program, name) > 0;
    }

    private static boolean calledInLoop(Block program, String name) {
        boolean[] found = new boolean[1];
        for (Stmt statement : program.statements()) {
            AstWalk.walk(statement, node -> {
                if (node instanceof UserCall && ((UserCall) node).name.equals(name)) {
                    for (Node p = node.parent; p != program && p != null; p = p.parent) {
                        if (p instanceof ForStmt || p instanceof WhileStmt || p instanceof ForEachStmt) {
                            found[0] = true;
                        }
                    }
                }
            });
        }
        return found[0];
    }

    /** Accept either a direct call in a condition or a local holding the returned value. */
    private static boolean returnControlsLoop(Block program, String method) {
        Set<String> results = new HashSet<>();
        for (Stmt statement : program.statements()) {
            AstWalk.walk(statement, node -> {
                if (node instanceof VarDecl) {
                    VarDecl variable = (VarDecl) node;
                    AstWalk.walk(variable.init.get(), value -> {
                        if (value instanceof UserCall && ((UserCall) value).name.equals(method)) results.add(variable.name);
                    });
                }
            });
        }
        boolean[] used = new boolean[1];
        for (Stmt statement : program.statements()) {
            AstWalk.walk(statement, node -> {
                Expr condition = node instanceof ForStmt ? ((ForStmt) node).cond.get()
                        : node instanceof WhileStmt ? ((WhileStmt) node).cond.get() : null;
                if (condition == null) return;
                AstWalk.walk(condition, value -> {
                    if (value instanceof UserCall && ((UserCall) value).name.equals(method)
                            || value instanceof VarRef && results.contains(((VarRef) value).name)) used[0] = true;
                });
            });
        }
        return used[0];
    }

    private static boolean reads(Block program, Access.Kind kind) {
        boolean[] found = new boolean[1];
        AstWalk.walk(program, node -> found[0] |= node instanceof Access && ((Access) node).kind == kind);
        return found[0];
    }

    /**
     * The shared goal of the tending levels: nothing ruined, all ripe wheat in, all young wheat watered.
     *
     * @param ruinedTip  what to do differently if a plant was ruined
     * @param wateringTip how to water what is still dry
     */
    private static String tended(FarmWorld world, String ruinedTip, String wateringTip) {
        if (world.ruined() > 0) {
            return world.ruined() + " young wheat was ruined: cut before it was ripe, or watered twice. "
                    + ruinedTip;
        }
        String left = wheatLeft(world);
        if (left != null) return left;
        int dry = world.dryRemaining();
        return dry == 0 ? null : dry + " young wheat still needs water. " + wateringTip;
    }

    private static boolean contains(Block program, Class<? extends Stmt> kind) {
        boolean[] found = new boolean[1];
        AstWalk.walk(program, node -> found[0] |= kind.isInstance(node));
        return found[0];
    }

    private static String wheatLeft(FarmWorld world) {
        int left = world.ripeRemaining();
        return left == 0 ? null
                : "The program finished, but " + left + " ripe wheat is still in the field.";
    }

    /** True if the program declares a variable as its own statement and reads it somewhere. */
    private static boolean usesOwnVariable(Block program) {
        Set<String> declared = new HashSet<>();
        Set<String> read = new HashSet<>();
        AstWalk.walk(program, node -> {
            // A loop's "int i" is part of the for statement, not a declaration the player wrote.
            if (node instanceof VarDecl && node.parent instanceof Block) declared.add(((VarDecl) node).name);
            if (node instanceof VarRef) read.add(((VarRef) node).name);
        });
        declared.retainAll(read);
        return !declared.isEmpty();
    }
}
