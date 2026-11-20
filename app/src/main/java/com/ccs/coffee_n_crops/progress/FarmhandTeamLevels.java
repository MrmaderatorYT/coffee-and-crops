package com.ccs.coffee_n_crops.progress;

import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.AstWalk;
import com.ccs.coffee_n_crops.lang.ast.ClassDecl;
import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.Node;
import com.ccs.coffee_n_crops.lang.ast.expr.AtomicCounter;
import com.ccs.coffee_n_crops.lang.ast.expr.Binary;
import com.ccs.coffee_n_crops.lang.ast.expr.FieldAccess;
import com.ccs.coffee_n_crops.lang.ast.expr.Literal;
import com.ccs.coffee_n_crops.lang.ast.expr.MethodCall;
import com.ccs.coffee_n_crops.lang.ast.expr.NewObject;
import com.ccs.coffee_n_crops.lang.ast.expr.NewReentrantLock;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.AssertStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.ExprStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.FieldAssign;
import com.ccs.coffee_n_crops.lang.ast.stmt.LockStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.TeamStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.types.JType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/** Team lessons share a farm and Java objects between two visible farmhands. */
final class FarmhandTeamLevels {
    private static final int CROPS_PER_FARMHAND = 3;
    private static final int TOTAL_CROPS = 2 * CROPS_PER_FARMHAND;
    private static final String TALLY = "tally";
    private static final String COUNTER = "counter";
    private static final String TOTAL = "total";
    private static final String GATE = "gate";
    private static final String PUMP_A = "pumpA";
    private static final String PUMP_B = "pumpB";

    private FarmhandTeamLevels() { }

    static List<LevelDef> all() {
        return Arrays.asList(
                new LevelDef("level_53", "Level 53 · Two farmhands", "Harvest three crops with each farmhand",
                        "The mint farmhand starts on the first row and the blue farmhand on the second. "
                                + "Tap each job header in workTogether. In each job, repeat three times: "
                                + "harvest and move EAST using that job's farmhand.",
                        wheatRows(), 0, 0, FarmhandTeamLevels::harvestComplete,
                        Unlock.TEAM, Unlock.FOR, Unlock.HARVEST, Unlock.MOVE, Unlock.WALK_TO)
                        .withStarter(FarmhandTeamLevels::splitField).withSecondFarmhand(0, 1),
                new LevelDef("level_54", "Level 54 · Shared harvest tally", "Count both rows using one shared AtomicInteger",
                        "Both jobs can see tally, which was created before workTogether. In each loop, "
                                + "harvest, call tally.incrementAndGet(), then move EAST. "
                                + "After both jobs finish, the assertion checks that the shared total is 6.",
                        wheatRows(), 0, 0, FarmhandTeamLevels::checkAtomicTally,
                        Unlock.TEAM, Unlock.ATOMIC, Unlock.FOR, Unlock.HARVEST, Unlock.MOVE, Unlock.WALK_TO)
                        .withStarter(FarmhandTeamLevels::atomicTally).withSecondFarmhand(0, 1),
                new LevelDef("level_55", "Level 55 · ReentrantLock", "Protect a shared read and write with one lock",
                        "In both loops, add a gate.lock() block. Put counter.total = counter.total + 1; "
                                + "inside its try section. The generated finally releases the same lock. "
                                + "Harvest and move EAST in each iteration. Both jobs must share gate.",
                        wheatRows(), 0, 0, FarmhandTeamLevels::checkLockedTally,
                        Unlock.TEAM, Unlock.REENTRANT_LOCK, Unlock.CLASSES, Unlock.FOR,
                        Unlock.HARVEST, Unlock.MOVE, Unlock.WALK_TO)
                        .withStarter(FarmhandTeamLevels::lockedTally).withSecondFarmhand(0, 1),
                new LevelDef("level_56", "Level 56 · Deadlock", "Order the pump locks so both crops get water",
                        "Run the starter. Each job takes one pump lock and then asks for the other. "
                                + "If they wait on each other, the run reports a deadlock. Tap the two lock names "
                                + "in the second job and make its order match the first: pumpA, then pumpB. "
                                + "Both jobs still need both pumps.",
                        new String[]{"g..", "g.."}, 0, 0, FarmhandTeamLevels::checkPumpOrder,
                        Unlock.TEAM, Unlock.REENTRANT_LOCK, Unlock.WATER)
                        .withStarter(FarmhandTeamLevels::twoPumps).withSecondFarmhand(0, 1));
    }

    private static String[] wheatRows() { return new String[]{"WWW.", "WWW."}; }

    private static Block splitField() {
        Block program = new Block();
        program.add(new TeamStmt());
        return program;
    }

    private static Block atomicTally() {
        Block program = new Block();
        program.add(new VarDecl(JType.ATOMIC_INTEGER, TALLY,
                new AtomicCounter(AtomicCounter.Op.NEW, Ast.lit(0)), true, false));
        program.add(rowJobs());
        program.add(tallyAssertion(new AtomicCounter(AtomicCounter.Op.GET, new VarRef(TALLY))));
        return program;
    }

    private static Block lockedTally() {
        Block program = new Block();
        ClassDecl counter = new ClassDecl("Counter");
        counter.addField(Ast.declare(JType.INT, TOTAL));
        program.addClass(counter);
        program.add(new VarDecl(counter.type(), COUNTER, new NewObject(counter), true, false));
        program.add(lock(GATE));
        program.add(rowJobs());
        program.add(tallyAssertion(new FieldAccess(new VarRef(COUNTER), counter.type(), TOTAL, JType.INT)));
        return program;
    }

    private static AssertStmt tallyAssertion(Expr tally) {
        return new AssertStmt(Ast.eq(tally, TOTAL_CROPS), Ast.lit("Expected one count for each crop"));
    }

    private static TeamStmt rowJobs() {
        TeamStmt team = new TeamStmt();
        team.first.add(Ast.forCount("i", CROPS_PER_FARMHAND));
        team.second.add(Ast.forCount("i", CROPS_PER_FARMHAND));
        return team;
    }

    private static Block twoPumps() {
        Block program = new Block();
        program.add(lock(PUMP_A));
        program.add(lock(PUMP_B));
        TeamStmt team = new TeamStmt();
        team.first.add(wateringJob(team.firstFarmer.name, PUMP_A, PUMP_B));
        team.second.add(wateringJob(team.secondFarmer.name, PUMP_B, PUMP_A));
        program.add(team);
        return program;
    }

    private static VarDecl lock(String name) {
        return new VarDecl(JType.REENTRANT_LOCK, name, new NewReentrantLock(), true, false);
    }

    private static LockStmt wateringJob(String actor, String firstPump, String secondPump) {
        LockStmt first = new LockStmt(new VarRef(firstPump));
        LockStmt second = new LockStmt(new VarRef(secondPump));
        second.body.add(new ExprStmt(new MethodCall(actor, "water")));
        first.body.add(second);
        return first;
    }

    private static String harvestComplete(FarmWorld world, Block program) {
        if (world.ruined() != 0) return "Keep the young crops intact.";
        if (world.ripeRemaining() != 0) return "Harvest the two rows before finishing.";
        return world.farmers().stream().allMatch(hand -> world.wheatHarvested(hand) == CROPS_PER_FARMHAND)
                ? null : "Both farmhands must harvest three crops in workTogether.";
    }

    private static String checkAtomicTally(FarmWorld world, Block program) {
        String crop = harvestComplete(world, program);
        if (crop != null) return crop;
        return bothJobs(world, program, FarmhandTeamLevels::atomicIncrement) && checkedTotal(world, program,
                expr -> expr instanceof AtomicCounter get && get.op == AtomicCounter.Op.GET
                        && named(get.operand.get(), TALLY))
                ? null : "Increment the same tally in both jobs and keep the check for all six crops.";
    }

    private static boolean atomicIncrement(Node node) {
        return node instanceof AtomicCounter update && named(update.operand.get(), TALLY)
                && (update.op == AtomicCounter.Op.INCREMENT || update.op == AtomicCounter.Op.GET_AND_INCREMENT);
    }

    private static String checkLockedTally(FarmWorld world, Block program) {
        String crop = harvestComplete(world, program);
        if (crop != null) return crop;
        return bothJobs(world, program, node -> node instanceof LockStmt guard && named(guard.lock.get(), GATE)
                && executed(world, guard.body, inner -> inner instanceof FieldAssign update
                        && counterTotal(update.target) && update.value.get() instanceof Binary value
                        && value.op() == Binary.Op.ADD && counterTotal(value.left.get())
                        && value.right.get() instanceof Literal amount && Integer.valueOf(1).equals(amount.value)))
                && checkedTotal(world, program, FarmhandTeamLevels::counterTotal)
                ? null : "Protect each counter increment with gate and keep the check for all six crops.";
    }

    private static boolean checkedTotal(FarmWorld world, Block program, Predicate<Expr> total) {
        for (var statement : program.statements()) {
            if (statement instanceof AssertStmt check && world.usedLessonNode(check.id)
                    && check.condition.get() instanceof Binary comparison && comparison.op() == Binary.Op.EQ
                    && total.test(comparison.left.get()) && comparison.right.get() instanceof Literal expected
                    && Integer.valueOf(TOTAL_CROPS).equals(expected.value)) return true;
        }
        return false;
    }

    private static boolean counterTotal(Expr expression) {
        return expression instanceof FieldAccess field && named(field.receiver.get(), COUNTER) && field.name.equals(TOTAL);
    }

    private static boolean named(Expr expression, String name) {
        return expression instanceof VarRef variable && variable.name.equals(name);
    }

    private static String checkPumpOrder(FarmWorld world, Block program) {
        if (world.dryRemaining() != 0 || world.ruined() != 0
                || !world.farmers().stream().allMatch(hand -> world.watered(hand) == 1)) {
            return "Both farmhands must water their own sprout exactly once.";
        }
        for (var statement : program.statements()) {
            if (statement instanceof TeamStmt team && world.usedLessonNode(team.id)) {
                List<String> first = pumpOrder(world, team.first);
                if (!first.isEmpty() && first.equals(pumpOrder(world, team.second))) return null;
            }
        }
        return "Acquire both pump locks in the same order in both jobs.";
    }

    private static List<String> pumpOrder(FarmWorld world, Block body) {
        for (var statement : body.statements()) {
            if (!(statement instanceof LockStmt outer) || !world.usedLessonNode(outer.id)
                    || !(outer.lock.get() instanceof VarRef first)) continue;
            for (var inside : outer.body.statements()) {
                if (!(inside instanceof LockStmt inner) || !world.usedLessonNode(inner.id)) continue;
                if (first.name.equals(PUMP_A) && named(inner.lock.get(), PUMP_B)) return Arrays.asList(PUMP_A, PUMP_B);
                if (first.name.equals(PUMP_B) && named(inner.lock.get(), PUMP_A)) return Arrays.asList(PUMP_B, PUMP_A);
            }
        }
        return Collections.emptyList();
    }

    private static boolean bothJobs(FarmWorld world, Block program, Predicate<Node> operation) {
        for (var statement : program.statements()) {
            if (statement instanceof TeamStmt team && world.usedLessonNode(team.id)
                    && executed(world, team.first, operation) && executed(world, team.second, operation)) return true;
        }
        return false;
    }

    private static boolean executed(FarmWorld world, Block body, Predicate<Node> operation) {
        boolean[] found = {false};
        AstWalk.walk(body, node -> {
            if (!found[0] && world.usedLessonNode(node.id) && operation.test(node)) found[0] = true;
        });
        return found[0];
    }
}
