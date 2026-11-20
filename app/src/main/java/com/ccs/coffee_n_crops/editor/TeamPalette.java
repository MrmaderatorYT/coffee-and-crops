package com.ccs.coffee_n_crops.editor;

import com.ccs.coffee_n_crops.farm.Direction;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.ast.Node;
import com.ccs.coffee_n_crops.lang.ast.expr.Literal;
import com.ccs.coffee_n_crops.lang.ast.expr.MethodCall;
import com.ccs.coffee_n_crops.lang.ast.expr.NewReentrantLock;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.ExprStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.LockStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.TeamStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.check.ScopeResolver;
import com.ccs.coffee_n_crops.lang.print.JavaPrinter;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.progress.Unlock;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/** Farm actions target the farmhand bound to the current workTogether lambda. */
final class TeamPalette {
    private static final int FIRST_ROW = 0;
    private static final int SECOND_ROW = 1;
    private static final String[] LOCK_NAMES = {"gate", "pumpA", "pumpB"};

    private TeamPalette() { }

    static VarDecl farmhandAt(Node node) {
        Node child = node;
        for (Node parent = node.parent; parent != null; parent = parent.parent) {
            if (parent instanceof TeamStmt team) {
                if (child == team.first) return team.firstFarmer;
                if (child == team.second) return team.secondFarmer;
                return null;
            }
            child = parent;
        }
        return null;
    }

    static void statements(EditorCursor cursor, Set<String> unlocks, Block program, List<BlockSpec> out) {
        if (cursor.block != program) return;
        if (unlocks.contains(Unlock.TEAM)) {
            out.add(threadCode("farmer.workTogether(first -> { … }, second -> { … });",
                    editor -> editor.insert(new TeamStmt())));
        }
        if (unlocks.contains(Unlock.REENTRANT_LOCK)) {
            String name = lockName(PaletteProvider.namesInMethod(cursor.block, program));
            String type = JType.REENTRANT_LOCK.javaName;
            out.add(threadCode(type + " " + name + " = new " + type + "();", editor ->
                    editor.insert(new VarDecl(JType.REENTRANT_LOCK, name, new NewReentrantLock()))));
        }
    }

    static void jobStatements(EditorCursor cursor, Set<String> unlocks, Block program, List<BlockSpec> out) {
        VarDecl farmhand = farmhandAt(cursor.block);
        List<VarDecl> visible = ScopeResolver.visibleAt(cursor.block, cursor.index);
        if (unlocks.contains(Unlock.HARVEST)) farmAction(() -> new MethodCall(farmhand.name, "harvest"), out);
        if (unlocks.contains(Unlock.WATER)) farmAction(() -> new MethodCall(farmhand.name, "water"), out);
        if (unlocks.contains(Unlock.FERTILIZE)) farmAction(() -> new MethodCall(farmhand.name, "fertilize"), out);
        if (unlocks.contains(Unlock.WAIT)) farmAction(() -> new MethodCall(farmhand.name, "waitOneTick"), out);
        if (unlocks.contains(Unlock.MOVE)) farmAction(() -> new MethodCall(farmhand.name, "move",
                new Literal(JType.DIRECTION, Direction.EAST)), out);
        if (unlocks.contains(Unlock.WALK_TO)) {
            List<String> coordinates = PaletteProvider.intNames(visible);
            int row = farmhand == ((TeamStmt) farmhand.parent).firstFarmer ? FIRST_ROW : SECOND_ROW;
            farmAction(() -> new MethodCall(farmhand.name, "walkTo", coordinate(coordinates, 0, 0),
                    coordinate(coordinates, 1, row)), out);
        }
        ParallelPalette.jobStatements(cursor, unlocks, program, visible, out);
        if (unlocks.contains(Unlock.REENTRANT_LOCK)) {
            for (VarDecl variable : visible) {
                if (variable.type != JType.REENTRANT_LOCK) continue;
                String name = variable.name;
                out.add(threadCode(name + ".lock(); try { } finally { " + name + ".unlock(); }", editor ->
                        editor.insert(new LockStmt(new VarRef(name)))));
            }
        }
    }

    static void slots(ExprSlot slot, Set<String> unlocks, List<BlockSpec> out) {
        if (slot.type != JType.REENTRANT_LOCK || slot.constantOnly || !unlocks.contains(Unlock.REENTRANT_LOCK)) return;
        lockChoices(slot, out);
        if (!(slot.owner instanceof LockStmt)) {
            out.add(new BlockSpec("new " + JType.REENTRANT_LOCK.javaName + "()", false,
                    editor -> editor.mutate(() -> slot.set(new NewReentrantLock()))));
        }
    }

    static void jobSlots(ExprSlot slot, Set<String> unlocks, Block program, List<BlockSpec> out) {
        if (slot.owner instanceof LockStmt) {
            lockChoices(slot, out);
            return;
        }
        if (slot.type == JType.DIRECTION) {
            for (Direction direction : Direction.values()) {
                valueChoice(slot, "Direction." + direction, () -> new Literal(JType.DIRECTION, direction), out);
            }
        }
        ParallelPalette.jobSlots(slot, program, out);
        if (slot.type != JType.BOOLEAN || slot.constantOnly) return;
        String receiver = farmhandAt(slot.owner).name;
        if (unlocks.contains(Unlock.IS_RIPE)) valueChoice(slot, receiver + ".isRipe()",
                () -> new MethodCall(receiver, "isRipe"), out);
        if (unlocks.contains(Unlock.IS_DRY)) valueChoice(slot, receiver + ".isDry()",
                () -> new MethodCall(receiver, "isDry"), out);
    }

    private static void lockChoices(ExprSlot slot, List<BlockSpec> out) {
        for (VarDecl variable : ScopeResolver.visibleAt(slot)) {
            if (variable.type == JType.REENTRANT_LOCK) {
                valueChoice(slot, variable.name, () -> new VarRef(variable.name), out);
            }
        }
    }

    private static Expr coordinate(List<String> names, int index, int fallback) {
        return index < names.size() ? new VarRef(names.get(index)) : Ast.lit(fallback);
    }

    private static String lockName(Set<String> used) {
        for (String name : LOCK_NAMES) if (!used.contains(name)) return name;
        for (int suffix = 2; ; suffix++) if (!used.contains(LOCK_NAMES[0] + suffix)) return LOCK_NAMES[0] + suffix;
    }

    private static void farmAction(Supplier<MethodCall> call, List<BlockSpec> out) {
        String preview = JavaPrinter.text(JavaPrinter.line(new ExprStmt(call.get())));
        out.add(new BlockSpec(preview, false, editor -> editor.insert(new ExprStmt(call.get())))
                .inTab(BlockSpec.TAB_FARM));
    }

    private static void valueChoice(ExprSlot slot, String label, Supplier<Expr> expression, List<BlockSpec> out) {
        out.add(new BlockSpec(label, false, editor -> editor.mutate(() -> slot.set(expression.get()))));
    }

    private static BlockSpec threadCode(String label, BlockSpec.Action action) {
        return new BlockSpec(label, false, action).inTab(BlockSpec.TAB_THREADS);
    }
}
