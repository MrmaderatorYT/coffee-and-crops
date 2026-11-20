package com.ccs.coffee_n_crops.progress;

import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.lang.ast.AstWalk;
import com.ccs.coffee_n_crops.lang.ast.Node;
import com.ccs.coffee_n_crops.lang.ast.expr.CropEquals;
import com.ccs.coffee_n_crops.lang.ast.expr.FunctionCall;
import com.ccs.coffee_n_crops.lang.ast.expr.FunctionType;
import com.ccs.coffee_n_crops.lang.ast.expr.FunctionValue;
import com.ccs.coffee_n_crops.lang.ast.expr.IntArrayLiteral;
import com.ccs.coffee_n_crops.lang.ast.expr.IntegerMethodRef;
import com.ccs.coffee_n_crops.lang.ast.expr.NewIntGrid;
import com.ccs.coffee_n_crops.lang.ast.expr.StringConcat;
import com.ccs.coffee_n_crops.lang.ast.expr.StringIntMapCall;
import com.ccs.coffee_n_crops.lang.ast.expr.StringLength;
import com.ccs.coffee_n_crops.lang.ast.stmt.ArrayAssign;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.CompoundAssign;
import com.ccs.coffee_n_crops.lang.ast.stmt.SwitchStmt;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/** Follow-up puzzles for the small language features used in the sandbox. */
final class LanguagePracticeLevels {
    private LanguagePracticeLevels() { }

    static List<LevelDef> all() {
        return Arrays.asList(
                new LevelDef("level_59", "Level 59 · Build a string", "Open the harvest with a five-letter label",
                        "Build a String from two pieces using +. Tap a string value and use Enter text to type your own pieces. "
                                + "Check label.length() == 5 before harvesting.",
                        new String[]{"W"}, 0, 0, (world, program) -> finished(world, program,
                                "Build a String with + and measure it with length().",
                                node -> node instanceof StringConcat, node -> node instanceof StringLength),
                        Unlock.HARVEST, Unlock.STRING, Unlock.STRING_OPERATIONS, Unlock.TEXT_INPUT, Unlock.IF, Unlock.LOGIC),
                new LevelDef("level_60", "Level 60 · Crop counts", "Count the two wheat tiles by name and reach the flag",
                        "Create a HashMap<String, Integer>. For each of the first two tiles, use the crop name as the key "
                                + "and put the old count from getOrDefault(key, 0) plus 1. Harvest each tile. "
                                + "Walk to the flag at (3, 0) when the wheat count is 2.",
                        new String[]{"WW.."}, 0, 0, (world, program) -> finished(world, program,
                                "Count crops by name using both put() and getOrDefault().",
                                node -> mapCall(node, StringIntMapCall.Op.PUT),
                                node -> mapCall(node, StringIntMapCall.Op.GET_OR_DEFAULT)),
                        Unlock.MOVE, Unlock.WALK_TO, Unlock.HARVEST, Unlock.FOR, Unlock.IF, Unlock.STRING,
                        Unlock.GET_CROP, Unlock.INT, Unlock.HASH_MAP, Unlock.MATH, Unlock.LOGIC).withFinish(3, 0),
                new LevelDef("level_61", "Level 61 · Array literals", "Harvest both stops from an array literal",
                        "Write an int array literal containing 1 and 3. Use a for-each loop to walk to each saved column "
                                + "and harvest it.",
                        new String[]{".W.W"}, 0, 0, (world, program) -> finished(world, program,
                                "Use an array literal while tending the stops.", node -> node instanceof IntArrayLiteral),
                        Unlock.ARRAY_LITERAL, Unlock.FOR_EACH, Unlock.WALK_TO, Unlock.HARVEST, Unlock.INT),
                new LevelDef("level_62", "Level 62 · Write a grid", "Store a harvest column for each row in an int[][]",
                        "Allocate a grid with two rows and one column. Store 1 in field[0][0] and 2 in field[1][0]. "
                                + "Loop across the rows and use the recorded column to reach each crop.",
                        new String[]{".W.", "..W"}, 0, 0, (world, program) -> finished(world, program,
                                "Allocate an int[][] and write a cell before harvesting.",
                                node -> node instanceof NewIntGrid,
                                node -> node instanceof ArrayAssign write && write.column != null),
                        Unlock.GRID_ALLOCATION, Unlock.GRID_ASSIGN, Unlock.FOR, Unlock.WALK_TO,
                        Unlock.HARVEST, Unlock.INT, Unlock.MATH),
                new LevelDef("level_63", "Level 63 · Subtract and assign", "Harvest westward with a countdown",
                        "Set an int counter to 3. While it is above 0, harvest, move WEST and subtract 1 with -=.",
                        new String[]{".WWW"}, 3, 0, (world, program) -> finished(world, program,
                                "Use -= while counting down the harvest.",
                                node -> node instanceof CompoundAssign assignment
                                        && assignment.operation == CompoundAssign.Operation.SUBTRACT),
                        Unlock.INT, Unlock.SUBTRACT_ASSIGN, Unlock.WHILE, Unlock.HARVEST,
                        Unlock.MOVE, Unlock.MATH, Unlock.LOGIC).withFinish(0, 0),
                new LevelDef("level_64", "Level 64 · Switch fall-through", "Share the same move between two cases",
                        "Loop through 0, 1 and 2. In case 0, water the sprout and enable fall-through. "
                                + "In case 1, move EAST and keep its break. In default, harvest the wheat.",
                        new String[]{"g.W"}, 0, 0, (world, program) -> {
                            if (world.watered() < 1) return "Water the sprout before moving east.";
                            return finished(world, program, "Let case 0 fall through into case 1 during the run.",
                                    node -> node instanceof SwitchStmt.Case);
                        }, Unlock.FOR, Unlock.SWITCH, Unlock.SWITCH_FALL_THROUGH,
                        Unlock.MOVE, Unlock.WATER, Unlock.HARVEST, Unlock.INT, Unlock.MATH),
                new LevelDef("level_65", "Level 65 · Crop enum", "Read named crop states from the scanner",
                        "Save farmer.scanCrops() in a Crop[] variable. Loop over its indices and compare each "
                                + "entry with Crop.WHEAT. Walk to and harvest the matching tiles.",
                        new String[]{"W.gW."}, 0, 0, (world, program) -> finished(world, program,
                                "Use named Crop values to tell ripe wheat from the other tiles.",
                                node -> node instanceof CropEquals),
                        Unlock.CROP_ENUM, Unlock.FOR, Unlock.IF, Unlock.INT, Unlock.LOGIC,
                        Unlock.WALK_TO, Unlock.HARVEST),
                new LevelDef("level_66", "Level 66 · Function values", "Turn a negative offset into a harvest column",
                        "Store Math::abs in a Function<Integer, Integer>. Start an int at 0 and subtract 3. "
                                + "Use apply() on the offset to get the column, then walk there and harvest.",
                        new String[]{"...W"}, 0, 0, (world, program) -> finished(world, program,
                                "Store Math::abs in a function variable and call apply().",
                                node -> node instanceof IntegerMethodRef ref && ref.kind == IntegerMethodRef.Kind.ABS,
                                node -> node instanceof FunctionCall call && call.function == FunctionType.TRANSFORM),
                        Unlock.FUNCTION_VALUES, Unlock.INT, Unlock.SUBTRACT_ASSIGN, Unlock.WALK_TO, Unlock.HARVEST),
                new LevelDef("level_67", "Level 67 · Constructor reference", "Create a harvest route with int[]::new",
                        "Store int[]::new in an IntFunction<int[]>. Call apply(2) to create a route array. "
                                + "Write 1 and 4 to its two entries, then walk to each column and harvest.",
                        new String[]{".W..W"}, 0, 0, (world, program) -> finished(world, program,
                                "Create the route array from an int[]::new reference and write to it.",
                                node -> node instanceof FunctionValue value && value.function == FunctionType.ARRAY_FACTORY,
                                node -> node instanceof IntegerMethodRef ref && ref.kind == IntegerMethodRef.Kind.ARRAY,
                                node -> node instanceof ArrayAssign write && write.column == null),
                        Unlock.FUNCTION_VALUES, Unlock.ARRAY_ASSIGN, Unlock.NEW_ARRAY, Unlock.FOR_EACH,
                        Unlock.INT, Unlock.WALK_TO, Unlock.HARVEST));
    }

    private static boolean mapCall(Node node, StringIntMapCall.Op method) {
        return node instanceof StringIntMapCall call && call.op == method;
    }

    @SafeVarargs
    private static String finished(FarmWorld world, Block program, String prompt, Predicate<Node>... expected) {
        if (world.ripeRemaining() > 0) return "Harvest all ripe wheat.";
        if (world.ruined() > 0) return "Keep the crops healthy.";
        for (Predicate<Node> requirement : expected) {
            boolean[] used = {false};
            AstWalk.walk(program, node -> used[0] |= world.usedLessonNode(node.id) && requirement.test(node));
            if (!used[0]) return prompt;
        }
        return null;
    }
}
