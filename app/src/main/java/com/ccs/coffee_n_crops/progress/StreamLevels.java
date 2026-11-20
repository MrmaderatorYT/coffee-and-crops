package com.ccs.coffee_n_crops.progress;

import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.AstWalk;
import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.expr.Binary;
import com.ccs.coffee_n_crops.lang.ast.expr.Literal;
import com.ccs.coffee_n_crops.lang.ast.expr.MethodCall;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamCall;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamCall.StageKind;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamCall.Terminal;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamLambda;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamMethodRef;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForEachStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.IfStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ListAdd;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.check.ScopeResolver;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/** Stream exercises that apply a route, measure it, and compose operations. */
final class StreamLevels {
    private static final String STOPS = "stops";
    private static final String ROUTE = "route";
    private static final int EXPECTED_COUNT = 3;
    private static final int TARGET_STOP = 4;

    private StreamLevels() { }

    static List<LevelDef> all() {
        return Arrays.asList(
                new LevelDef("level_47", "Level 47 · A lambda for each stop", "Harvest the stops with a block lambda",
                        "The list holds columns 1, 3 and 5. Add stops.stream().forEach(cell -> { }). "
                                + "Inside the braces, walk to (cell, 0) and harvest. The lambda runs once per stop.",
                        new String[]{".W.W.W."}, 0, 0,
                        (world, program) -> lambdaRoute(world, program,
                                "Visit and harvest each stop inside the forEach lambda."),
                        Unlock.STREAM_FOR_EACH, Unlock.WALK_TO, Unlock.HARVEST)
                        .withStarter(() -> stops(1, 3, 5)),
                new LevelDef("level_48", "Level 48 · Transform with map", "Shift the route two columns east",
                        "The list holds offsets 0, 2 and 4. Add a map stage and edit its lambda "
                                + "to cell -> cell + 2. Then walk to (cell, 0) and harvest inside forEach. "
                                + "The last lambda receives the transformed columns 2, 4 and 6.",
                        new String[]{"g.W.W.W"}, 0, 0,
                        (world, program) -> lambdaRoute(world, program,
                                "Map the offsets to real columns before the forEach lambda.", StageKind.MAP),
                        Unlock.STREAM_MAP, Unlock.STREAM_FOR_EACH, Unlock.WALK_TO, Unlock.HARVEST)
                        .withStarter(() -> stops(0, 2, 4)),
                new LevelDef("level_49", "Level 49 · Count matches", "Confirm that three stops pass the filter",
                        "Keep stops at column 2 or beyond with filter(cell -> cell >= 2). Call count() "
                                + "and store its long result. Harvest the crop under the farmer only inside "
                                + "if (count == 3L). The L marks a long literal.",
                        new String[]{"W......"}, 0, 0, StreamLevels::countRoute,
                        Unlock.STREAM_FILTER, Unlock.STREAM_COUNT, Unlock.IF, Unlock.HARVEST)
                        .withStarter(() -> stops(0, 1, 2, 4, 6)),
                new LevelDef("level_50", "Level 50 · Check anyMatch", "Harvest if the route contains column 4",
                        "Set the anyMatch lambda to cell -> cell == 4 and store the boolean result. "
                                + "Harvest the crop under the farmer inside if (found). anyMatch stops "
                                + "as soon as a stop satisfies the predicate.",
                        new String[]{"W...."}, 0, 0, StreamLevels::matchRoute,
                        Unlock.STREAM_ANY_MATCH, Unlock.IF, Unlock.HARVEST)
                        .withStarter(() -> stops(0, 2, 4)),
                new LevelDef("level_51", "Level 51 · Method references", "Copy the stops into a second route",
                        "Use stops.stream().forEach(route::add) to copy each stop into the empty route. "
                                + "The method reference calls add on route. Use a for-each over route "
                                + "to walk to (cell, 0) and harvest each copied stop.",
                        new String[]{".W.W.W."}, 0, 0, StreamLevels::copyRoute,
                        Unlock.STREAM_METHOD_REF, Unlock.FOR_EACH, Unlock.WALK_TO, Unlock.HARVEST)
                        .withStarter(StreamLevels::twoLists),
                new LevelDef("level_52", "Level 52 · Compose a pipeline", "Shift the route and keep only the ripe stops",
                        "The offsets are 0 through 4. Use map(cell -> cell + 1) to get actual columns, "
                                + "then filter(cell -> cell >= 4) to skip young crops. Use forEach to "
                                + "walk to each remaining column on row 0 and harvest it.",
                        new String[]{".gggWW."}, 0, 0,
                        (world, program) -> lambdaRoute(world, program,
                                "Use map, then filter, then harvest in the forEach lambda.",
                                StageKind.MAP, StageKind.FILTER),
                        Unlock.STREAM_MAP, Unlock.STREAM_FILTER, Unlock.STREAM_FOR_EACH, Unlock.WALK_TO, Unlock.HARVEST)
                        .withStarter(() -> stops(0, 1, 2, 3, 4)));
    }

    private static Block stops(int... columns) {
        Block program = new Block();
        program.add(Ast.newList(STOPS));
        for (int column : columns) program.add(new ListAdd(STOPS, Ast.lit(column)));
        return program;
    }

    private static Block twoLists() {
        Block program = stops(1, 3, 5);
        program.add(Ast.newList(ROUTE));
        return program;
    }

    private static String lambdaRoute(FarmWorld world, Block program, String hint, StageKind... stages) {
        String remaining = unfinished(world);
        if (remaining != null) return remaining;
        return executedPipeline(world, program, stream -> stream.terminal == Terminal.FOR_EACH
                && hasStages(stream, stages) && stream.callback instanceof StreamLambda lambda
                && world.usedLessonNode(lambda.id)
                && lambda.body != null && harvestsColumn(lambda.body, lambda.parameter.name)) ? null : hint;
    }

    private static String countRoute(FarmWorld world, Block program) {
        String remaining = unfinished(world);
        if (remaining != null) return remaining;
        boolean[] usedResult = {false};
        AstWalk.walk(program, node -> {
            if (!(node instanceof IfStmt guard) || !(guard.cond.get() instanceof Binary check)
                    || check.op() != Binary.Op.EQ || !containsHarvest(guard.thenBlock)) return;
            for (VarDecl variable : ScopeResolver.visibleAt(guard.cond)) {
                if (variable.init.get() instanceof StreamCall stream && world.usedLessonNode(stream.id)
                        && stream.terminal == Terminal.COUNT && hasStages(stream, StageKind.FILTER)
                        && world.usedLessonNode(stream.stages.get(0).callback().id)
                        && testsStop(check, variable.name, EXPECTED_COUNT)) usedResult[0] = true;
            }
        });
        return usedResult[0] ? null : "Filter the stops, count them, and harvest only when the count is 3L.";
    }

    private static String matchRoute(FarmWorld world, Block program) {
        String remaining = unfinished(world);
        if (remaining != null) return remaining;
        boolean[] usedResult = {false};
        AstWalk.walk(program, node -> {
            if (!(node instanceof IfStmt guard) || !(guard.cond.get() instanceof VarRef found)
                    || !containsHarvest(guard.thenBlock)) return;
            for (VarDecl variable : ScopeResolver.visibleAt(guard.cond)) {
                if (variable.name.equals(found.name) && variable.init.get() instanceof StreamCall stream
                        && world.usedLessonNode(stream.id) && stream.terminal == Terminal.ANY_MATCH
                        && stream.callback instanceof StreamLambda predicate && predicate.expression != null
                        && world.usedLessonNode(predicate.id)
                        && predicate.expression.get() instanceof Binary check && check.op() == Binary.Op.EQ
                        && testsStop(check, predicate.parameter.name, TARGET_STOP)) usedResult[0] = true;
            }
        });
        return usedResult[0] ? null : "Use anyMatch to look for column 4 and use its boolean as the if condition.";
    }

    private static String copyRoute(FarmWorld world, Block program) {
        String remaining = unfinished(world);
        if (remaining != null) return remaining;
        boolean copied = executedPipeline(world, program, stream -> stream.terminal == Terminal.FOR_EACH
                && stream.callback instanceof StreamMethodRef reference && reference.receiver.equals(ROUTE)
                && world.usedLessonNode(reference.id)
                && reference.method.equals("add") && !stream.source.equals(ROUTE));
        boolean[] visits = {false};
        AstWalk.walk(program, node -> {
            if (node instanceof ForEachStmt loop && ROUTE.equals(loop.source)
                    && harvestsColumn(loop.body, loop.variable.name)) visits[0] = true;
        });
        return copied && visits[0] ? null : "Copy the stops with route::add and harvest by looping over route.";
    }

    private static boolean hasStages(StreamCall stream, StageKind... stages) {
        if (stream.stages.size() != stages.length) return false;
        for (int index = 0; index < stages.length; index++) {
            if (stream.stages.get(index).kind() != stages[index]) return false;
        }
        return true;
    }

    private static boolean testsStop(Binary check, String name, int value) {
        return comparesValue(check.left.get(), check.right.get(), name, value)
                || comparesValue(check.right.get(), check.left.get(), name, value);
    }

    private static boolean comparesValue(Expr input, Expr constant, String name, int expected) {
        return input instanceof VarRef ref && ref.name.equals(name) && constant instanceof Literal literal
                && literal.value instanceof Number number && number.doubleValue() == expected;
    }

    private static boolean executedPipeline(FarmWorld world, Block program, Predicate<StreamCall> matches) {
        boolean[] found = {false};
        AstWalk.walk(program, node -> {
            if (node instanceof StreamCall stream && world.usedLessonNode(stream.id) && matches.test(stream)) found[0] = true;
        });
        return found[0];
    }

    private static boolean harvestsColumn(Block body, String column) {
        boolean[] walks = {false};
        AstWalk.walk(body, node -> {
            if (node instanceof MethodCall call && call.receiver.equals("farmer") && call.name.equals("walkTo")
                    && call.args.get(0).get() instanceof VarRef ref && ref.name.equals(column)) walks[0] = true;
        });
        return walks[0] && containsHarvest(body);
    }

    private static boolean containsHarvest(Block body) {
        boolean[] found = {false};
        AstWalk.walk(body, node -> {
            if (node instanceof MethodCall call && call.receiver.equals("farmer") && call.name.equals("harvest")) found[0] = true;
        });
        return found[0];
    }

    private static String unfinished(FarmWorld world) {
        if (world.ruined() != 0) return "Leave the young crops untouched.";
        return world.ripeRemaining() == 0 ? null : "Harvest the remaining wheat.";
    }
}
