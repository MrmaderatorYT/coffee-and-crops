package com.ccs.coffee_n_crops.editor;

import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.ast.expr.Binary;
import com.ccs.coffee_n_crops.lang.ast.expr.Literal;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamCall;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamCall.Stage;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamCall.StageKind;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamCall.Terminal;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamLambda;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamMethodRef;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.ExprStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.StreamForEachStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.print.JavaPrinter;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.progress.Unlock;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/** Builds editable stream pipelines from the lists and result variables visible at the cursor. */
final class StreamPalette {
    private static final long DEFAULT_EXPECTED_COUNT = 3L;

    private StreamPalette() { }

    static boolean enabled(Set<String> unlocks) {
        return unlocks.contains(Unlock.STREAM_FOR_EACH) || unlocks.contains(Unlock.STREAM_COUNT)
                || unlocks.contains(Unlock.STREAM_ANY_MATCH) || unlocks.contains(Unlock.STREAM_METHOD_REF);
    }

    static void statements(Set<String> unlocks, List<VarDecl> visible, Set<String> taken, List<BlockSpec> out) {
        if (!enabled(unlocks)) return;
        String cell = fresh("cell", taken);
        for (VarDecl variable : visible) {
            if (variable.type == JType.INT_LIST) {
                pipelines(unlocks, visible, taken, cell, variable.name, out);
            } else if (unlocks.contains(Unlock.IF)) {
                if (variable.type == JType.LONG && unlocks.contains(Unlock.STREAM_COUNT)) {
                    out.add(code("if (" + variable.name + " == " + DEFAULT_EXPECTED_COUNT + "L) { }", editor -> {
                        Binary condition = new Binary(Binary.Op.EQ, JType.LONG,
                                new VarRef(variable.name), new Literal(JType.LONG, DEFAULT_EXPECTED_COUNT));
                        editor.insert(Ast.ifStmt(condition, false));
                    }));
                } else if (variable.type == JType.BOOLEAN && unlocks.contains(Unlock.STREAM_ANY_MATCH)) {
                    out.add(code("if (" + variable.name + ") { }", editor ->
                            editor.insert(Ast.ifStmt(new VarRef(variable.name), false))));
                }
            }
        }
    }

    private static void pipelines(Set<String> unlocks, List<VarDecl> visible, Set<String> taken,
                                  String cell, String source, List<BlockSpec> out) {
        boolean filter = unlocks.contains(Unlock.STREAM_FILTER);
        if (unlocks.contains(Unlock.STREAM_FOR_EACH)) {
            if (unlocks.contains(Unlock.STREAM_MAP)) {
                if (filter) consumer(() -> forEach(source, cell, map(cell), filter(cell)), out);
                consumer(() -> forEach(source, cell, map(cell)), out);
            }
            if (filter) consumer(() -> forEach(source, cell, filter(cell)), out);
            consumer(() -> forEach(source, cell), out);
        }
        if (unlocks.contains(Unlock.STREAM_COUNT)) {
            String count = fresh("count", taken);
            if (filter) declaration(count, () -> count(source, filter(cell)), out);
            declaration(count, () -> count(source), out);
        }
        if (unlocks.contains(Unlock.STREAM_ANY_MATCH)) {
            declaration(fresh("found", taken), () -> anyMatch(source, cell), out);
        }
        if (unlocks.contains(Unlock.STREAM_METHOD_REF)) {
            for (VarDecl destination : visible) {
                if (destination.type != JType.INT_LIST || destination.name.equals(source)) continue;
                Supplier<StreamCall> reference = () -> new StreamCall(source, Terminal.FOR_EACH,
                        Collections.emptyList(), new StreamMethodRef(destination.name, "add"));
                out.add(code(JavaPrinter.streamText(reference.get()) + ";", editor ->
                        editor.insert(new ExprStmt(reference.get()))));
            }
        }
    }

    static void slots(ExprSlot slot, Set<String> unlocks, List<VarDecl> visible, Set<String> taken,
                      List<BlockSpec> out) {
        if (slot.constantOnly || !enabled(unlocks)) return;
        String cell = fresh("cell", taken);
        for (VarDecl variable : visible) {
            if (variable.type != JType.INT_LIST) continue;
            String source = variable.name;
            if (slot.type == JType.LONG && unlocks.contains(Unlock.STREAM_COUNT)) {
                if (unlocks.contains(Unlock.STREAM_FILTER)) choice(slot, () -> count(source, filter(cell)), out);
                choice(slot, () -> count(source), out);
            } else if (slot.type == JType.BOOLEAN && unlocks.contains(Unlock.STREAM_ANY_MATCH)) {
                choice(slot, () -> anyMatch(source, cell), out);
            }
        }
    }

    private static Stage map(String cell) {
        return new Stage(StageKind.MAP, new StreamLambda(cell, JType.INT,
                new Binary(Binary.Op.ADD, new VarRef(cell), Ast.lit(0))));
    }

    private static Stage filter(String cell) {
        return new Stage(StageKind.FILTER, predicate(cell, Binary.Op.GE));
    }

    private static StreamCall forEach(String source, String cell, Stage... stages) {
        return new StreamCall(source, Terminal.FOR_EACH, Arrays.asList(stages), new StreamLambda(cell));
    }

    private static StreamCall count(String source, Stage... stages) {
        return new StreamCall(source, Terminal.COUNT, Arrays.asList(stages), null);
    }

    private static StreamCall anyMatch(String source, String cell) {
        return new StreamCall(source, Terminal.ANY_MATCH, Collections.emptyList(), predicate(cell, Binary.Op.EQ));
    }

    private static StreamLambda predicate(String cell, Binary.Op op) {
        return new StreamLambda(cell, JType.BOOLEAN, new Binary(op, new VarRef(cell), Ast.lit(0)));
    }

    private static void consumer(Supplier<StreamCall> factory, List<BlockSpec> out) {
        out.add(code(JavaPrinter.streamText(factory.get()) + " });", editor ->
                editor.insert(new StreamForEachStmt(factory.get()))));
    }

    private static void declaration(String name, Supplier<StreamCall> factory, List<BlockSpec> out) {
        StreamCall call = factory.get();
        JType type = call.terminal.resultType;
        String label = type.javaName + " " + name + " = " + JavaPrinter.streamText(call) + ";";
        out.add(code(label, editor -> editor.insert(new VarDecl(type, name, factory.get()))));
    }

    private static void choice(ExprSlot slot, Supplier<StreamCall> factory, List<BlockSpec> out) {
        out.add(new BlockSpec(JavaPrinter.streamText(factory.get()), false,
                editor -> editor.mutate(() -> slot.set(factory.get()))));
    }

    private static String fresh(String base, Set<String> taken) {
        if (!taken.contains(base)) return base;
        for (int i = 2; ; i++) if (!taken.contains(base + i)) return base + i;
    }

    private static BlockSpec code(String label, BlockSpec.Action action) {
        return new BlockSpec(label, false, action).inTab(BlockSpec.TAB_STREAMS);
    }
}
