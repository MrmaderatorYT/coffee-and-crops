package com.ccs.coffee_n_crops.progress;

import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.lang.ast.AstWalk;
import com.ccs.coffee_n_crops.lang.ast.Node;
import com.ccs.coffee_n_crops.lang.ast.expr.ListContains;
import com.ccs.coffee_n_crops.lang.ast.expr.NewIntArray;
import com.ccs.coffee_n_crops.lang.ast.stmt.ArrayAssign;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.BreakStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.CompoundAssign;
import com.ccs.coffee_n_crops.lang.ast.stmt.ContinueStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.DecrementStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.DoWhileStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ListRemove;
import com.ccs.coffee_n_crops.lang.ast.stmt.ListSet;

import java.util.Arrays;
import java.util.List;

/** Ten practice levels, each introducing one operation in a farm task. */
final class PracticeLevels {
    private PracticeLevels() { }

    static List<LevelDef> all() {
        return Arrays.asList(
                lesson(23, "Add and assign", "Harvest the tiles two steps apart",
                        "Start int steps = 0. While steps is less than 7, walk to (steps, 0), harvest, "
                                + "then use steps += 2 to reach the next crop.",
                        "W.W.W.W.", 0, CompoundAssign.class, "Use += to advance the counter.",
                        Unlock.INT, Unlock.WHILE, Unlock.LOGIC, Unlock.MATH, Unlock.WALK_TO,
                        Unlock.HARVEST, Unlock.COMPOUND_ASSIGN),
                lesson(24, "Count down", "Harvest from east to west using --",
                        "Start int steps = 5. While steps is greater than or equal to 0, "
                                + "walk to (steps, 0), harvest, then use steps--. Finish on the western flag.",
                        "WWWWWW", 5, DecrementStmt.class, "Use -- to count down to the first tile.",
                        Unlock.INT, Unlock.WHILE, Unlock.LOGIC, Unlock.MATH, Unlock.WALK_TO,
                        Unlock.HARVEST, Unlock.DECREMENT).withFinish(0, 0),
                lesson(25, "Do then check", "Harvest the row with a do-while loop",
                        "Put harvest and an eastward move inside do { ... }. Tap the condition at the "
                                + "bottom and check farmer.canMove(Direction.EAST). The body runs before the first check.",
                        "WWWWW.", 0, DoWhileStmt.class, "Use do-while to tend the current tile before testing the next move.",
                        Unlock.HARVEST, Unlock.MOVE, Unlock.CAN_MOVE, Unlock.DO_WHILE).withFinish(5, 0),
                lesson(26, "Break out", "Leave an open-ended loop at the field edge",
                        "Start with while (true). Harvest, then use if (!farmer.canMove(Direction.EAST)) "
                                + "with break inside. Move east after that if. The break exits the nearest loop.",
                        "WWWWWWW", 0, BreakStmt.class, "Use break at the edge to exit the loop.",
                        Unlock.HARVEST, Unlock.MOVE, Unlock.CAN_MOVE, Unlock.WHILE, Unlock.IF,
                        Unlock.LOGIC, Unlock.BREAK).withFinish(6, 0),
                lesson(27, "Skip with continue", "Skip sprouts and empty tiles without cutting them",
                        "Loop i from 0 to 7 and walk to (i, 0). If the farmer is not on ripe wheat, "
                                + "continue to the next iteration. Put harvest after the if.",
                        "Wg.WgWW.", 0, ContinueStmt.class, "Use continue to skip a tile before harvesting.",
                        Unlock.WALK_TO, Unlock.HARVEST, Unlock.IS_RIPE, Unlock.FOR, Unlock.IF,
                        Unlock.INT, Unlock.LOGIC, Unlock.MATH, Unlock.CONTINUE),
                lesson(28, "Allocate an array", "Use a new array's length to mark a five-tile work area",
                        "Create int[] row = new int[5]. Add a for loop with i < row.length. "
                                + "Walk to (i, 0) and harvest. New int array elements start at zero.",
                        "WWWWW..", 0, NewIntArray.class, "Allocate the work array with new int[5].",
                        Unlock.NEW_ARRAY, Unlock.INT, Unlock.FOR, Unlock.WALK_TO, Unlock.HARVEST),
                lesson(29, "Write to an array", "Store three harvest stops and visit them",
                        "Create an int array of length 3. Store 1, 4 and 6 in row[0], row[1] and row[2]. "
                                + "Use a for-each loop to visit the stored columns and harvest.",
                        ".W..W.W.", 0, ArrayAssign.class, "Write the crop positions into the array before visiting them.",
                        Unlock.NEW_ARRAY, Unlock.ARRAY_ASSIGN, Unlock.FOR, Unlock.FOR_EACH,
                        Unlock.INT, Unlock.WALK_TO, Unlock.HARVEST),
                lesson(30, "Check list membership", "Protect the sprouts with a list of blocked columns",
                        "Add 1, 4 and 6 to a list. Loop over the eight columns, and walk to and harvest "
                                + "a tile only if !list.contains(i). The blocked columns are sprouts.",
                        "WgWWgWgW", 0, ListContains.class, "Use contains to check whether a column should be skipped.",
                        Unlock.LIST, Unlock.LIST_CONTAINS, Unlock.INT, Unlock.FOR, Unlock.IF,
                        Unlock.LOGIC, Unlock.WALK_TO, Unlock.HARVEST),
                lesson(31, "Replace a list item", "Correct a harvest route without adding another stop",
                        "Make a list with the columns 1, 2 and 5 in that order. Index 1 points to a sprout. "
                                + "Change it to column 3 using list.set(1, 3), then visit and harvest each entry.",
                        ".WgW.W.", 0, ListSet.class, "Use set to replace the route entry for the sprout.",
                        Unlock.LIST, Unlock.LIST_SET, Unlock.INT, Unlock.WALK_TO, Unlock.HARVEST),
                lesson(32, "Remove a list item", "Take a sprout off the harvest route",
                        "Make a list with the columns 0, 1, 3 and 5 in that order. Remove the entry at "
                                + "index 1 with list.remove(1) before the for-each. Later entries shift left.",
                        "Wg.W.W.", 0, ListRemove.class, "Use remove(index) before iterating the route.",
                        Unlock.LIST, Unlock.LIST_REMOVE, Unlock.INT, Unlock.WALK_TO, Unlock.HARVEST));
    }

    private static LevelDef lesson(int number, String title, String goal, String hint, String row,
                                   int start, Class<? extends Node> topic, String useHint, String... unlocks) {
        return new LevelDef("level_" + number, "Level " + number + " · " + title, goal, hint,
                new String[]{row}, start, 0, (world, program) -> check(world, program, topic, useHint), unlocks);
    }

    private static String check(FarmWorld world, Block program, Class<? extends Node> topic, String useHint) {
        if (world.ruined() != 0) return "A sprout was ruined. Leave the young plants intact.";
        if (world.ripeRemaining() != 0) return "There is still ripe wheat to harvest. " + useHint;
        boolean[] used = {false};
        AstWalk.walk(program, node -> used[0] |= topic.isInstance(node) && world.usedLessonNode(node.id));
        return used[0] ? null : useHint;
    }
}
