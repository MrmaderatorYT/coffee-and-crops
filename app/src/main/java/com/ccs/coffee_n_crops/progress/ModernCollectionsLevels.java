package com.ccs.coffee_n_crops.progress;

import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.AstWalk;
import com.ccs.coffee_n_crops.lang.ast.expr.CollectionCall;
import com.ccs.coffee_n_crops.lang.ast.expr.CollectionCall.Op;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.ListAdd;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

/** Farm routes that teach external iteration, optional values and finding the first stream match. */
final class ModernCollectionsLevels {
    private static final String STOPS = "stops";

    private ModernCollectionsLevels() { }

    static List<LevelDef> all() {
        return Arrays.asList(
                new LevelDef("level_40", "Level 40 · Iterator", "Follow three stops with an explicit iterator",
                        "The stops list has columns 1, 4 and 6. Create Iterator<Integer> route = stops.iterator(). "
                                + "Inside while (route.hasNext()), read int column = route.next(), "
                                + "walk to (column, 0) and harvest. hasNext() checks the cursor without moving it.",
                        new String[]{".W..W.W"}, 0, 0,
                        (world, program) -> check(world, program, "Use iterator(), hasNext() and next() on the route.",
                                Op.ITERATOR, Op.HAS_NEXT, Op.NEXT),
                        Unlock.ITERATOR, Unlock.WHILE, Unlock.WALK_TO, Unlock.HARVEST)
                        .withStarter(() -> stops(1, 4, 6)),
                new LevelDef("level_41", "Level 41 · Optional", "Use a fallback when a route has no stop",
                        "Create Optional<Integer> stop = Optional.empty(). Get a safe column with "
                                + "int column = stop.orElse(3). Walk to (column, 0) and harvest. "
                                + "orElse keeps the stored value when the Optional is present.",
                        new String[]{"...W."}, 0, 0,
                        (world, program) -> check(world, program, "Use Optional.empty() and orElse(3) for the missing stop.",
                                Op.EMPTY, Op.OR_ELSE),
                        Unlock.OPTIONAL, Unlock.IF, Unlock.WALK_TO, Unlock.HARVEST),
                new LevelDef("level_42", "Level 42 · Streams", "Find the first stop at or beyond column 4",
                        "The stops are 1, 3 and 5. Add stops.stream().filter(cell -> cell >= 0).findFirst(). "
                                + "Tap the cutoff 0 in the filter and change it to 4. "
                                + "Use orElse(0) to read the result, then walk to that column and harvest. "
                                + "findFirst() returns an Optional and can stop evaluating as soon as one item matches.",
                        new String[]{".g.g.W."}, 0, 0,
                        (world, program) -> check(world, program, "Filter the stops and read the result of findFirst() with orElse().",
                                Op.FIND_FIRST, Op.OR_ELSE),
                        Unlock.STREAMS, Unlock.OPTIONAL, Unlock.WALK_TO, Unlock.HARVEST)
                        .withStarter(() -> stops(1, 3, 5)));
    }

    private static Block stops(int... columns) {
        Block program = new Block();
        program.add(Ast.newList(STOPS));
        for (int column : columns) program.add(new ListAdd(STOPS, Ast.lit(column)));
        return program;
    }

    private static String check(FarmWorld world, Block program, String hint, Op first, Op... rest) {
        if (world.ruined() != 0) return "Leave the young plants intact.";
        if (world.ripeRemaining() != 0) return "There is still ripe wheat to harvest.";
        EnumSet<Op> remaining = EnumSet.of(first, rest);
        AstWalk.walk(program, node -> {
            if (node instanceof CollectionCall call && world.usedLessonNode(call.id)) remaining.remove(call.op);
        });
        return remaining.isEmpty() ? null : hint;
    }
}
