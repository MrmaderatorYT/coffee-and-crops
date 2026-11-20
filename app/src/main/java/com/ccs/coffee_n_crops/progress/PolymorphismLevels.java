package com.ccs.coffee_n_crops.progress;

import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.AstWalk;
import com.ccs.coffee_n_crops.lang.ast.ClassDecl;
import com.ccs.coffee_n_crops.lang.ast.ConstructorDecl;
import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.ast.MethodDecl;
import com.ccs.coffee_n_crops.lang.ast.expr.FieldAccess;
import com.ccs.coffee_n_crops.lang.ast.expr.InstanceCall;
import com.ccs.coffee_n_crops.lang.ast.expr.NewObjectArray;
import com.ccs.coffee_n_crops.lang.ast.expr.ObjectArrayGet;
import com.ccs.coffee_n_crops.lang.ast.expr.ThisRef;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.ExprStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.FieldAssign;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForEachStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ObjectArrayAssign;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.check.ScopeResolver;
import com.ccs.coffee_n_crops.lang.runtime.ClassTable;
import com.ccs.coffee_n_crops.lang.runtime.NativeRegistry;
import com.ccs.coffee_n_crops.lang.types.JType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The second OOP sequence builds from a contract to a mixed team that shares one loop. */
final class PolymorphismLevels {
    private static final String TOOL = "Tool";
    private static final String WORK = "work";
    private static final String FARMER = "farmer";
    private static final String COLUMN = "column";

    private PolymorphismLevels() { }

    static List<LevelDef> all() {
        return Arrays.asList(
                new LevelDef("level_33", "Level 33 · Interfaces", "Harvest through a Tool reference",
                        "Harvester already knows how to work. Add an interface named Tool and use + Method for void work();. "
                                + "On the Harvester tab, add implements Tool. In Program, declare "
                                + "Tool tool = new Harvester(farmer); and call tool.work().",
                        new String[]{"WWW."}, 0, 0, PolymorphismLevels::checkInterface,
                        Unlock.CLASSES, Unlock.INTERFACES, Unlock.INSTANCE_METHODS)
                        .withStarter(PolymorphismLevels::singleWorker).withFinish(3, 0),
                new LevelDef("level_34", "Level 34 · Object arrays", "Store two RowPlan objects and harvest their stops",
                        "The RowPlan class is ready. Create RowPlan[] rowPlans = new RowPlan[2];. "
                                + "Store new RowPlan(1) and new RowPlan(4) at indices 0 and 1. "
                                + "Loop to rowPlans.length, load RowPlan rowPlan = rowPlans[i];, "
                                + "then call rowPlan.work(farmer). New array slots start at null.",
                        new String[]{".W..W."}, 0, 0, PolymorphismLevels::checkArray,
                        Unlock.CLASSES, Unlock.INSTANCE_METHODS, Unlock.OBJECT_ARRAYS,
                        Unlock.FOR, Unlock.INT, Unlock.LOGIC)
                        .withStarter(PolymorphismLevels::rowPlanner),
                new LevelDef("level_35", "Level 35 · Polymorphism", "Harvest and water from the same loop",
                        "Tool, Harvester and Waterer are ready. Create Tool[] tools = new Tool[2];. "
                                + "Put a Harvester and a Waterer in the two slots. Use for (Tool tool : tools) "
                                + "with one tool.work() call in its body. Each object runs its own implementation.",
                        new String[]{".W..g."}, 0, 0, PolymorphismLevels::checkPolymorphism,
                        Unlock.CLASSES, Unlock.INTERFACES, Unlock.INSTANCE_METHODS, Unlock.OBJECT_ARRAYS,
                        Unlock.FOR_EACH, Unlock.INT)
                        .withStarter(PolymorphismLevels::mixedTeam));
    }

    private static Block singleWorker() {
        Block program = new Block();
        ClassDecl harvester = worker("Harvester", null);
        var loop = Ast.forCount("i", 3);
        loop.body.add(workAction(harvester, "walkTo", new VarRef("i"), Ast.lit(0)));
        loop.body.add(workAction(harvester, "harvest"));
        harvester.methods().get(0).body.add(loop);
        harvester.methods().get(0).body.add(workAction(harvester, "walkTo", Ast.lit(3), Ast.lit(0)));
        program.addClass(harvester);
        return program;
    }

    private static Block rowPlanner() {
        Block program = new Block();
        ClassDecl plan = new ClassDecl("RowPlan");
        plan.addField(Ast.declare(JType.INT, COLUMN));
        ConstructorDecl constructor = new ConstructorDecl(Ast.declare(JType.INT, COLUMN));
        constructor.body.add(new FieldAssign(field(plan, COLUMN, JType.INT), new VarRef(COLUMN)));
        plan.setConstructor(constructor);
        MethodDecl work = new MethodDecl(WORK, JType.VOID, Ast.declare(JType.FARMHAND, FARMER));
        work.body.add(Ast.walkTo(field(plan, COLUMN, JType.INT), Ast.lit(0)));
        work.body.add(Ast.harvest());
        plan.addMethod(work);
        program.addClass(plan);
        return program;
    }

    private static Block mixedTeam() {
        Block program = new Block();
        ClassDecl tool = new ClassDecl(TOOL, true);
        tool.addMethod(new MethodDecl(WORK, JType.VOID));
        program.addClass(tool);

        ClassDecl harvester = worker("Harvester", tool.name);
        harvester.methods().get(0).body.add(workAction(harvester, "walkTo", Ast.lit(1), Ast.lit(0)));
        harvester.methods().get(0).body.add(workAction(harvester, "harvest"));
        program.addClass(harvester);

        ClassDecl waterer = worker("Waterer", tool.name);
        waterer.methods().get(0).body.add(workAction(waterer, "walkTo", Ast.lit(4), Ast.lit(0)));
        waterer.methods().get(0).body.add(workAction(waterer, "water"));
        program.addClass(waterer);
        return program;
    }

    private static ClassDecl worker(String name, String contract) {
        ClassDecl worker = new ClassDecl(name);
        worker.interfaceName = contract;
        worker.addField(Ast.declare(JType.FARMHAND, FARMER));
        ConstructorDecl constructor = new ConstructorDecl(Ast.declare(JType.FARMHAND, FARMER));
        constructor.body.add(new FieldAssign(field(worker, FARMER, JType.FARMHAND), new VarRef(FARMER)));
        worker.setConstructor(constructor);
        MethodDecl work = new MethodDecl(WORK, JType.VOID);
        work.override = contract != null;
        worker.addMethod(work);
        return worker;
    }

    private static FieldAccess field(ClassDecl owner, String name, JType type) {
        return new FieldAccess(new ThisRef(), owner.type(), name, type);
    }

    private static ExprStmt workAction(ClassDecl owner, String name, Expr... args) {
        FieldAccess receiver = field(owner, FARMER, JType.FARMHAND);
        NativeRegistry.Entry method = NativeRegistry.STANDARD.lookup(FARMER, name);
        return new ExprStmt(new InstanceCall(receiver, JType.FARMHAND, name, method.returnType, method.params, args));
    }

    private static String checkInterface(FarmWorld world, Block program) {
        String remaining = cropsRemaining(world);
        if (remaining != null) return remaining;
        return usedInterfaceCall(world, program, false) ? null
                : "Call work() through a local variable declared with the interface type.";
    }

    private static String checkArray(FarmWorld world, Block program) {
        String remaining = cropsRemaining(world);
        if (remaining != null) return remaining;
        Set<VarDecl> filledArrays = new HashSet<>();
        AstWalk.walk(program, node -> {
            if (!(node instanceof ObjectArrayAssign write) || !world.usedLessonNode(write.id)) return;
            VarDecl array = local(write.index, write.name);
            if (array != null && array.init.get() instanceof NewObjectArray created
                    && world.usedLessonNode(created.id)) filledArrays.add(array);
        });
        boolean[] called = {false};
        AstWalk.walk(program, node -> {
            if (called[0] || !(node instanceof InstanceCall call) || !call.name.equals(WORK)
                    || world.instanceCallClassCount(call.id) == 0) return;
            VarDecl plan = receiverLocal(call);
            if (plan != null && plan.init.get() instanceof ObjectArrayGet read && world.usedLessonNode(read.id)) {
                called[0] = filledArrays.contains(local(read.index, read.name));
            }
        });
        return called[0] ? null : "Store the row plans in an object array, read them back by index, "
                + "and call work(farmer) on the plan you read.";
    }

    private static String checkPolymorphism(FarmWorld world, Block program) {
        String remaining = cropsRemaining(world);
        if (remaining != null) return remaining;
        return usedInterfaceCall(world, program, true) ? null
                : "Call work() once in the for-each body and let that call reach two different Tool implementations.";
    }

    private static String cropsRemaining(FarmWorld world) {
        if (world.ruined() != 0) return "Leave the young plants intact.";
        if (world.ripeRemaining() != 0) return "There is still ripe wheat to harvest.";
        return world.dryRemaining() != 0 ? "There is still a dry sprout to water." : null;
    }

    private static boolean usedInterfaceCall(FarmWorld world, Block program, boolean requireMixedLoop) {
        ClassTable table = new ClassTable(program);
        boolean[] found = {false};
        AstWalk.walk(program, node -> {
            if (found[0] || !(node instanceof InstanceCall call) || !call.name.equals(WORK)
                    || world.instanceCallClassCount(call.id) < (requireMixedLoop ? 2 : 1)) return;
            VarDecl variable = receiverLocal(call);
            if (variable == null) return;
            ClassDecl contract = table.findClass(variable.type);
            found[0] = contract != null && contract.isInterface
                    && (!requireMixedLoop || iteratesContractArray(variable));
        });
        return found[0];
    }

    private static VarDecl receiverLocal(InstanceCall call) {
        return call.receiver.get() instanceof VarRef ref ? local(call.receiver, ref.name) : null;
    }

    private static VarDecl local(ExprSlot slot, String name) {
        for (VarDecl variable : ScopeResolver.visibleAt(slot)) {
            if (variable.name.equals(name) && !(variable.parent instanceof ClassDecl)) return variable;
        }
        return null;
    }

    private static boolean iteratesContractArray(VarDecl variable) {
        if (!(variable.parent instanceof ForEachStmt loop) || loop.source == null) return false;
        for (VarDecl source : ScopeResolver.visibleAt(loop.body, 0)) {
            if (source.name.equals(loop.source)) return source.type.elementType == variable.type;
        }
        return false;
    }
}
