package com.ccs.coffee_n_crops.progress;

import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.AstWalk;
import com.ccs.coffee_n_crops.lang.ast.ClassDecl;
import com.ccs.coffee_n_crops.lang.ast.ConstructorDecl;
import com.ccs.coffee_n_crops.lang.ast.Node;
import com.ccs.coffee_n_crops.lang.ast.expr.Binary;
import com.ccs.coffee_n_crops.lang.ast.expr.InstanceCall;
import com.ccs.coffee_n_crops.lang.ast.expr.InstanceOf;
import com.ccs.coffee_n_crops.lang.ast.expr.StaticConstant;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.check.ScopeResolver;
import com.ccs.coffee_n_crops.lang.types.JType;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/** Lessons on Object, runtime type checks, constructor state and constants. */
final class AdvancedOopLevels {
    private AdvancedOopLevels() { }

    static List<LevelDef> all() {
        return Arrays.asList(
                new LevelDef("level_36", "Level 36 · Object methods", "Use Object.equals() to recognize an alias",
                        "Add Object object = new Parcel(); and Object object2 = object;. "
                                + "Both variables refer to the same parcel. Set the if condition to object.equals(object2) "
                                + "and harvest inside it. The same Object palette offers hashCode(), toString() and getClass().",
                        new String[]{"W."}, 0, 0, (world, program) -> verify(world, program,
                                node -> node instanceof InstanceCall call && call.name.equals("equals")
                                        && call.receiver.type == JType.OBJECT && comparesDifferentNames(call),
                                "Call Object.equals() with the two names for the same parcel."),
                        Unlock.CLASSES, Unlock.OBJECT_METHODS, Unlock.IF, Unlock.HARVEST)
                        .withStarter(() -> types(new ClassDecl("Parcel"))),
                new LevelDef("level_37", "Level 37 · instanceof", "Check what an Object actually contains",
                        "Create Object object = new Harvester();. Change the if condition to "
                                + "object instanceof Harvester and harvest in that branch. The check uses the "
                                + "actual class. Try creating a Waterer to see the branch stay inactive.",
                        new String[]{"Wg"}, 0, 0, (world, program) -> verify(world, program,
                                node -> node instanceof InstanceOf check && check.target == JType.reference("Harvester"),
                                "Test the Object reference with instanceof Harvester."),
                        Unlock.CLASSES, Unlock.INSTANCE_OF, Unlock.IF, Unlock.HARVEST)
                        .withStarter(() -> types(new ClassDecl("Harvester"), new ClassDecl("Waterer"))),
                new LevelDef("level_38", "Level 38 · Independent objects", "Initialize and update two Route objects independently",
                        "Route has a column field and a constructor. In the Route tab, set this.column = column; "
                                + "inside the constructor. In Program, create two objects with new Route(1), then "
                                + "increment only route2.column. Walk to each object's column on row 0 and harvest.",
                        new String[]{".WW."}, 0, 0, AdvancedOopLevels::checkRoutes,
                        Unlock.CLASSES, Unlock.CONSTRUCTORS, Unlock.INT, Unlock.WALK_TO, Unlock.HARVEST)
                        .withStarter(AdvancedOopLevels::routes),
                new LevelDef("level_39", "Level 39 · final and static", "Use a class constant as an immutable loop bound",
                        "On the Route tab, add static final int STOPS = 3;. In Program, add "
                                + "final int stops = Route.STOPS;. Use stops as the for loop's upper bound. "
                                + "At each step, harvest and move east toward the flag.",
                        new String[]{"WWW."}, 0, 0, AdvancedOopLevels::checkConstants,
                        Unlock.CLASSES, Unlock.CONSTANTS, Unlock.FOR, Unlock.INT, Unlock.HARVEST, Unlock.MOVE)
                        .withStarter(() -> types(new ClassDecl("Route"))).withFinish(3, 0));
    }

    private static Block types(ClassDecl... declarations) {
        Block program = new Block();
        for (ClassDecl declaration : declarations) program.addClass(declaration);
        return program;
    }

    private static Block routes() {
        ClassDecl type = new ClassDecl("Route");
        type.addField(Ast.declare(JType.INT, "column"));
        type.setConstructor(new ConstructorDecl(Ast.declare(JType.INT, "column")));
        return types(type);
    }

    private static boolean comparesDifferentNames(InstanceCall call) {
        return call.receiver.get() instanceof VarRef first && call.args.get(0).get() instanceof VarRef second
                && !first.name.equals(second.name);
    }

    private static String verify(FarmWorld world, Block program, Predicate<Node> expected, String hint) {
        String crops = remaining(world);
        if (crops != null) return crops;
        boolean[] executed = {false};
        AstWalk.walk(program, node -> {
            if (world.usedLessonNode(node.id) && expected.test(node)) executed[0] = true;
        });
        return executed[0] ? null : hint;
    }

    private static String checkRoutes(FarmWorld world, Block program) {
        String crops = remaining(world);
        if (crops != null) return crops;
        return world.objectCreations("Route") >= 2 && world.fieldWrites("Route", "column") >= 3
                ? null : "Initialize both Route objects with the constructor, then update just one column field.";
    }

    private static String checkConstants(FarmWorld world, Block program) {
        String crops = remaining(world);
        if (crops != null) return crops;
        boolean[] boundUsed = {false};
        AstWalk.walk(program, node -> {
            if (!(node instanceof ForStmt loop) || !(loop.cond.get() instanceof Binary comparison)
                    || !(comparison.right.get() instanceof VarRef bound)) return;
            for (VarDecl var : ScopeResolver.visibleAt(comparison.right)) {
                if (!bound.name.equals(var.name) || !var.isFinal || !world.usedLessonNode(var.id)) continue;
                if (var.init.get() instanceof StaticConstant constant && world.usedLessonNode(constant.id)) {
                    boundUsed[0] = true;
                }
            }
        });
        return boundUsed[0] ? null : "Use a final local initialized from Route.STOPS as the loop bound.";
    }

    private static String remaining(FarmWorld world) {
        if (world.ruined() != 0) return "Leave the young crop untouched.";
        return world.ripeRemaining() == 0 ? null : "Harvest the remaining wheat.";
    }
}
