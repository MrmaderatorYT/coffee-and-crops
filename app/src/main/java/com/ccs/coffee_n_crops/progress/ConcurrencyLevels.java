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
import com.ccs.coffee_n_crops.lang.ast.expr.NewObject;
import com.ccs.coffee_n_crops.lang.ast.expr.Not;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.FieldAssign;
import com.ccs.coffee_n_crops.lang.ast.stmt.SynchronizedStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.TogetherStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.ast.stmt.WhileStmt;
import com.ccs.coffee_n_crops.lang.types.JType;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/** The first shared-state jobs use a real monitor, a publication flag, and a JVM atomic value. */
final class ConcurrencyLevels {
    private static final String COUNTER = "counter";
    private static final String TOTAL = "total";
    private static final String SIGNAL = "signal";
    private static final String READY = "ready";
    private static final String COLUMN = "column";
    private static final String OBSERVED = "observed";
    private static final int INCREMENTS_PER_JOB = 3;
    private static final int PUBLISHED_COLUMN = 4;

    private ConcurrencyLevels() { }

    static List<LevelDef> all() {
        return Arrays.asList(
                new LevelDef("level_44", "Level 44 · Synchronized", "Protect a shared tally with one monitor",
                        "The two background jobs each repeat three times. In both loops, add synchronized (counter) "
                                + "and put counter.total = counter.total + 1; inside it. The whole read and write then "
                                + "happen under the same lock. The farmer walks to the combined tally after both jobs finish.",
                        new String[]{"......W"}, 0, 0, (world, program) -> bothJobsUsed(world, program,
                                node -> locksCounter(world, node), "Increment the tally inside synchronized (counter) in both jobs."),
                        Unlock.THREADS, Unlock.SYNCHRONIZED, Unlock.CLASSES, Unlock.FOR, Unlock.INT,
                        Unlock.WALK_TO, Unlock.HARVEST).withStarter(ConcurrencyLevels::lockedTally),
                new LevelDef("level_45", "Level 45 · Volatile", "Publish a column from one job to the other",
                        "The first job writes a column, then sets ready. The second waits for ready before reading "
                                + "the column. On the Signal tab, make ready volatile so that reading true also "
                                + "makes the earlier column write visible to the second job.",
                        new String[]{"....W"}, 0, 0, ConcurrencyLevels::checkPublication,
                        Unlock.THREADS, Unlock.VOLATILE, Unlock.CLASSES, Unlock.WHILE, Unlock.INT,
                        Unlock.WALK_TO, Unlock.HARVEST).withStarter(ConcurrencyLevels::publishSignal),
                new LevelDef("level_46", "Level 46 · Atomic integers", "Increment one AtomicInteger from two jobs",
                        "Add counter.incrementAndGet(); to each of the two background jobs. Each increment "
                                + "updates the shared value as one operation. The farmer uses counter.get() to find the crop.",
                        new String[]{"..W"}, 0, 0, (world, program) -> bothJobsUsed(world, program,
                                ConcurrencyLevels::incrementsCounter, "Increment the same AtomicInteger in both jobs."),
                        Unlock.THREADS, Unlock.ATOMIC, Unlock.WALK_TO, Unlock.HARVEST, Unlock.INT)
                        .withStarter(ConcurrencyLevels::atomicTally));
    }

    private static Block lockedTally() {
        Block program = new Block();
        ClassDecl counter = new ClassDecl("Counter");
        counter.addField(Ast.declare(JType.INT, TOTAL));
        program.addClass(counter);
        program.add(new VarDecl(counter.type(), COUNTER, new NewObject(counter)));
        TogetherStmt jobs = new TogetherStmt();
        jobs.first.add(Ast.forCount("i", INCREMENTS_PER_JOB));
        jobs.second.add(Ast.forCount("i", INCREMENTS_PER_JOB));
        program.add(jobs);
        finishAt(program, field(counter, COUNTER, TOTAL, JType.INT));
        return program;
    }

    private static Block publishSignal() {
        Block program = new Block();
        ClassDecl signal = new ClassDecl("Signal");
        signal.addField(Ast.declare(JType.INT, COLUMN));
        signal.addField(Ast.declare(JType.BOOLEAN, READY));
        signal.addField(Ast.declare(JType.INT, OBSERVED));
        program.addClass(signal);
        program.add(new VarDecl(signal.type(), SIGNAL, new NewObject(signal)));
        TogetherStmt jobs = new TogetherStmt();
        jobs.first.add(new FieldAssign(field(signal, SIGNAL, COLUMN, JType.INT), Ast.lit(PUBLISHED_COLUMN)));
        jobs.first.add(new FieldAssign(field(signal, SIGNAL, READY, JType.BOOLEAN), Ast.lit(true)));
        jobs.second.add(new WhileStmt(new Not(field(signal, SIGNAL, READY, JType.BOOLEAN))));
        jobs.second.add(new FieldAssign(field(signal, SIGNAL, OBSERVED, JType.INT), field(signal, SIGNAL, COLUMN, JType.INT)));
        program.add(jobs);
        finishAt(program, field(signal, SIGNAL, OBSERVED, JType.INT));
        return program;
    }

    private static Block atomicTally() {
        Block program = new Block();
        program.add(new VarDecl(JType.ATOMIC_INTEGER, COUNTER, new AtomicCounter(AtomicCounter.Op.NEW, Ast.lit(0))));
        program.add(new TogetherStmt());
        finishAt(program, new AtomicCounter(AtomicCounter.Op.GET, new VarRef(COUNTER)));
        return program;
    }

    private static void finishAt(Block program, Expr column) {
        program.add(Ast.walkTo(column, Ast.lit(0)));
        program.add(Ast.harvest());
    }

    private static FieldAccess field(ClassDecl type, String variable, String name, JType fieldType) {
        return new FieldAccess(new VarRef(variable), type.type(), name, fieldType);
    }

    private static String checkPublication(FarmWorld world, Block program) {
        for (ClassDecl type : program.classes()) {
            if (!type.name.equals("Signal")) continue;
            for (VarDecl field : type.fields()) {
                if (field.name.equals(READY) && field.volatileField && world.usedLessonNode(field.id)) {
                    return world.ripeRemaining() == 0 ? null : "Use the published column to reach and harvest the crop.";
                }
            }
        }
        return "Make ready volatile and read it from the receiving job before reading the column.";
    }

    private static String bothJobsUsed(FarmWorld world, Block program, Predicate<Node> operation, String hint) {
        for (var statement : program.statements()) {
            if (statement instanceof TogetherStmt jobs && world.usedLessonNode(jobs.id)
                    && used(world, jobs.first, operation) && used(world, jobs.second, operation)) {
                return world.ripeRemaining() == 0 ? null : "Use the combined tally to reach and harvest the crop.";
            }
        }
        return hint;
    }

    private static boolean used(FarmWorld world, Block body, Predicate<Node> operation) {
        boolean[] found = new boolean[1];
        AstWalk.walk(body, node -> found[0] |= operation.test(node) && world.usedLessonNode(node.id));
        return found[0];
    }

    private static boolean locksCounter(FarmWorld world, Node node) {
        if (!(node instanceof SynchronizedStmt lock) || !(lock.monitor.get() instanceof VarRef ref)
                || !ref.name.equals(COUNTER)) return false;
        return used(world, lock.body, inner -> inner instanceof FieldAssign update
                && update.target.name.equals(TOTAL) && update.target.receiver.get() instanceof VarRef target
                && target.name.equals(COUNTER) && update.value.get() instanceof Binary increment
                && increment.op() == Binary.Op.ADD);
    }

    private static boolean incrementsCounter(Node node) {
        return node instanceof AtomicCounter counter
                && (counter.op == AtomicCounter.Op.INCREMENT || counter.op == AtomicCounter.Op.GET_AND_INCREMENT)
                && counter.operand.get() instanceof VarRef ref && ref.name.equals(COUNTER);
    }
}
