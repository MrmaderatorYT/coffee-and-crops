package com.ccs.coffee_n_crops.editor;

import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.ast.expr.CollectionCall;
import com.ccs.coffee_n_crops.lang.ast.expr.CollectionCall.Op;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.ExprStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.ast.stmt.WhileStmt;
import com.ccs.coffee_n_crops.lang.print.JavaPrinter;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.progress.Unlock;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/** Scope-aware library blocks used by the explicit iterator, Optional and stream lessons. */
final class ModernCollectionPalette {
    private ModernCollectionPalette() { }

    static void statements(Set<String> unlocks, List<VarDecl> visible, Set<String> taken,
                           List<BlockSpec> data, List<BlockSpec> loops, List<BlockSpec> logic) {
        if (unlocks.contains(Unlock.OPTIONAL)) {
            String stop = fresh("stop", taken);
            declaration(stop, () -> new CollectionCall(Op.EMPTY, null), data);
            declaration(stop, () -> new CollectionCall(Op.OF, null, Ast.lit(0)), data);
        }
        for (VarDecl receiver : visible) {
            if (receiver.type == JType.INT_LIST) {
                if (unlocks.contains(Unlock.ITERATOR)) declaration(fresh("route", taken),
                        () -> new CollectionCall(Op.ITERATOR, receiver.name), data);
                if (unlocks.contains(Unlock.STREAMS)) {
                    String cell = fresh("cell", taken);
                    declaration(fresh("stop", taken), () -> stream(receiver.name, cell), data);
                }
            } else if (receiver.type == JType.INT_ITERATOR && unlocks.contains(Unlock.ITERATOR)) {
                declaration(fresh("column", taken), () -> new CollectionCall(Op.NEXT, receiver.name), data);
                data.add(code(receiver.name + ".remove();",
                        editor -> editor.insert(new ExprStmt(new CollectionCall(Op.REMOVE, receiver.name)))));
                if (unlocks.contains(Unlock.WHILE)) {
                    loops.add(code("while (" + receiver.name + ".hasNext()) { }", editor ->
                            editor.insert(new WhileStmt(new CollectionCall(Op.HAS_NEXT, receiver.name)))));
                }
            } else if (receiver.type == JType.INT_OPTIONAL && unlocks.contains(Unlock.OPTIONAL)) {
                declaration(fresh("column", taken), () -> new CollectionCall(Op.OR_ELSE, receiver.name, Ast.lit(0)), data);
                if (unlocks.contains(Unlock.IF)) {
                    logic.add(code("if (" + receiver.name + ".isPresent()) { }", editor ->
                            editor.insert(Ast.ifStmt(new CollectionCall(Op.IS_PRESENT, receiver.name), false))));
                }
            }
        }
    }

    static void slots(ExprSlot slot, Set<String> unlocks, List<VarDecl> visible, List<BlockSpec> out) {
        if (slot.constantOnly) return;
        boolean optional = unlocks.contains(Unlock.OPTIONAL);
        boolean iterator = unlocks.contains(Unlock.ITERATOR);
        boolean streams = unlocks.contains(Unlock.STREAMS);
        if (!(optional || iterator || streams)) return;
        if (slot.type == JType.INT_OPTIONAL && optional) {
            choice(slot, () -> new CollectionCall(Op.EMPTY, null), out);
            choice(slot, () -> new CollectionCall(Op.OF, null, Ast.lit(0)), out);
        }
        for (VarDecl receiver : visible) {
            if (receiver.type == slot.type && (slot.type == JType.INT_OPTIONAL || slot.type == JType.INT_ITERATOR)) {
                out.add(code(receiver.name, editor -> editor.mutate(() -> slot.set(new VarRef(receiver.name)))));
            }
            if (receiver.type == JType.INT_ITERATOR && iterator) {
                if (slot.type == JType.INT) choice(slot, () -> new CollectionCall(Op.NEXT, receiver.name), out);
                if (slot.type == JType.BOOLEAN) choice(slot, () -> new CollectionCall(Op.HAS_NEXT, receiver.name), out);
            }
            if (receiver.type == JType.INT_OPTIONAL && optional) {
                if (slot.type == JType.BOOLEAN) choice(slot, () -> new CollectionCall(Op.IS_PRESENT, receiver.name), out);
                if (slot.type == JType.INT) {
                    choice(slot, () -> new CollectionCall(Op.GET, receiver.name), out);
                    choice(slot, () -> new CollectionCall(Op.OR_ELSE, receiver.name, Ast.lit(0)), out);
                }
            }
            if (receiver.type == JType.INT_LIST && slot.type == JType.INT_ITERATOR && iterator) {
                choice(slot, () -> new CollectionCall(Op.ITERATOR, receiver.name), out);
            }
            if (receiver.type == JType.INT_LIST && slot.type == JType.INT_OPTIONAL && streams) {
                Set<String> names = new HashSet<>();
                for (VarDecl variable : visible) names.add(variable.name);
                String cell = fresh("cell", names);
                choice(slot, () -> stream(receiver.name, cell), out);
            }
        }
    }

    private static CollectionCall stream(String name, String cell) {
        return new CollectionCall(Op.FIND_FIRST, name, cell, Ast.lit(0));
    }

    private static void declaration(String name, Supplier<CollectionCall> initial, List<BlockSpec> out) {
        CollectionCall call = initial.get();
        String label = call.op.resultType.javaName + " " + name + " = " + JavaPrinter.collectionCallText(call) + ";";
        out.add(code(label, editor -> editor.insert(new VarDecl(call.op.resultType, name, initial.get()))));
    }

    private static void choice(ExprSlot slot, Supplier<CollectionCall> call, List<BlockSpec> out) {
        out.add(code(JavaPrinter.collectionCallText(call.get()), editor -> editor.mutate(() -> slot.set(call.get()))));
    }

    private static String fresh(String base, Set<String> taken) {
        if (!taken.contains(base)) return base;
        for (int n = 2; ; n++) {
            if (!taken.contains(base + n)) return base + n;
        }
    }

    private static BlockSpec code(String label, BlockSpec.Action action) {
        return new BlockSpec(label, false, action);
    }
}
