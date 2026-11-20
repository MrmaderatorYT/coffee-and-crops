package com.ccs.coffee_n_crops.editor;

import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.ast.Node;
import com.ccs.coffee_n_crops.lang.ast.expr.AtomicCounter;
import com.ccs.coffee_n_crops.lang.ast.expr.Binary;
import com.ccs.coffee_n_crops.lang.ast.expr.FieldAccess;
import com.ccs.coffee_n_crops.lang.ast.expr.Not;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.ExprStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.FieldAssign;
import com.ccs.coffee_n_crops.lang.ast.stmt.SynchronizedStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.TogetherStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.ast.stmt.WhileStmt;
import com.ccs.coffee_n_crops.lang.check.ScopeResolver;
import com.ccs.coffee_n_crops.lang.runtime.ClassTable;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.progress.Unlock;

import java.util.List;
import java.util.Set;

/** Thread lesson choices and the restricted palettes inside the two worker bodies. */
final class ParallelPalette {
    private static final String COUNTER = "counter";
    private static final int[] QUICK_INTS = {0, 1, 2, 3, 4, 5, 6, 8};

    private ParallelPalette() { }

    static boolean inJob(Node node) {
        for (Node at = node; at != null; at = at.parent) if (at instanceof TogetherStmt) return true;
        return false;
    }

    static void members(EditorCursor cursor, Set<String> unlocks, List<BlockSpec> out) {
        if (!unlocks.contains(Unlock.VOLATILE) || cursor.classDecl == null || cursor.classDecl.isInterface) return;
        for (VarDecl field : cursor.classDecl.fields()) {
            if (field.isFinal || field.isStatic || (cursor.declaration instanceof VarDecl && cursor.declaration != field)) {
                continue;
            }
            String modifier = field.volatileField ? "− volatile " : "+ volatile ";
            out.add(new BlockSpec(modifier + field.type.javaName + " " + field.name, true,
                    e -> e.mutate(() -> field.volatileField = !field.volatileField)).inTab(BlockSpec.TAB_THREADS));
        }
    }

    static void statements(EditorCursor cursor, Set<String> unlocks, Block program, List<BlockSpec> out) {
        boolean backgroundJobs = unlocks.contains(Unlock.THREADS);
        if (!backgroundJobs && !unlocks.contains(Unlock.TEAM)) return;
        List<VarDecl> visible = ScopeResolver.visibleAt(cursor.block, cursor.index);
        if (inJob(cursor.block)) {
            jobStatements(cursor, unlocks, program, visible, out);
            return;
        }
        if (backgroundJobs && cursor.block == program) {
            out.add(code("farmer.runTogether(() -> { … }, () -> { … });", e -> e.insert(new TogetherStmt())));
        }
        if (unlocks.contains(Unlock.ATOMIC)) {
            String name = free(COUNTER, PaletteProvider.namesInMethod(cursor.block, program));
            String type = JType.ATOMIC_INTEGER.javaName;
            out.add(code(type + " " + name + " = new " + type + "(0);", editor -> {
                AtomicCounter initial = new AtomicCounter(AtomicCounter.Op.NEW, Ast.lit(0));
                editor.insert(new VarDecl(JType.ATOMIC_INTEGER, name, initial));
            }));
            atomicStatements(visible, out);
        }
    }

    static void jobStatements(EditorCursor cursor, Set<String> unlocks, Block program,
                              List<VarDecl> visible, List<BlockSpec> out) {
        ClassTable types = new ClassTable(program);
        boolean monitorsAvailable = unlocks.contains(Unlock.SYNCHRONIZED)
                && TeamPalette.farmhandAt(cursor.block) == null;
        boolean heldMonitor = false;
        for (Node at = cursor.block; at != null; at = at.parent) {
            if (at instanceof SynchronizedStmt) { heldMonitor = true; break; }
        }
        for (VarDecl variable : visible) {
            if (monitorsAvailable && !heldMonitor && variable.type.isReference()) {
                out.add(code("synchronized (" + variable.name + ") { }",
                        e -> e.insert(new SynchronizedStmt(variable.type, new VarRef(variable.name)))));
            }
            for (VarDecl field : types.fields(variable.type)) {
                if (field.isStatic || field.isFinal) continue;
                String qualified = variable.name + "." + field.name;
                if (field.type == JType.INT) {
                    out.add(code(qualified + " = " + qualified + " + 1;", e -> e.insert(new FieldAssign(
                            field(variable, field), new Binary(Binary.Op.ADD, field(variable, field), Ast.lit(1))))));
                    for (VarDecl source : types.fields(variable.type)) {
                        if (source.type != JType.INT || source == field || source.isStatic) continue;
                        out.add(code(qualified + " = " + variable.name + "." + source.name + ";",
                                e -> e.insert(new FieldAssign(field(variable, field), field(variable, source)))));
                    }
                } else if (field.type == JType.BOOLEAN) {
                    out.add(code(qualified + " = true;", e -> e.insert(new FieldAssign(field(variable, field), Ast.lit(true)))));
                    if (unlocks.contains(Unlock.WHILE)) {
                        out.add(code("while (!" + qualified + ") { }",
                                e -> e.insert(new WhileStmt(new Not(field(variable, field))))));
                    }
                }
            }
        }
        if (unlocks.contains(Unlock.ATOMIC)) atomicStatements(visible, out);
        if (unlocks.contains(Unlock.FOR)) {
            String counter = free("i", PaletteProvider.namesInMethod(cursor.block, program));
            out.add(code("for (int " + counter + " = 0; " + counter + " < 3; " + counter + "++) { }",
                    e -> e.insert(Ast.forCount(counter, 3))));
        }
    }

    static void slots(ExprSlot slot, Set<String> unlocks, List<BlockSpec> out) {
        if (!unlocks.contains(Unlock.ATOMIC) || slot.constantOnly) return;
        if (slot.type == JType.ATOMIC_INTEGER) {
            out.add(code("new " + JType.ATOMIC_INTEGER.javaName + "(0)",
                    e -> e.mutate(() -> slot.set(new AtomicCounter(AtomicCounter.Op.NEW, Ast.lit(0))))));
        }
        atomicReads(slot, ScopeResolver.visibleAt(slot), out);
    }

    static void jobSlots(ExprSlot slot, Block program, List<BlockSpec> out) {
        if (slot.type == JType.INT) {
            for (int value : QUICK_INTS) out.add(code(String.valueOf(value), e -> e.mutate(() -> slot.set(Ast.lit(value)))));
        } else if (slot.type == JType.BOOLEAN) {
            out.add(code("true", e -> e.mutate(() -> slot.set(Ast.lit(true)))));
            out.add(code("false", e -> e.mutate(() -> slot.set(Ast.lit(false)))));
        }
        if (slot.constantOnly) return;
        List<VarDecl> visible = ScopeResolver.visibleAt(slot);
        ClassTable types = new ClassTable(program);
        for (VarDecl variable : visible) {
            if (types.assignable(variable.type, slot.type)) {
                out.add(code(variable.name, e -> e.mutate(() -> slot.set(new VarRef(variable.name)))));
            }
            for (VarDecl field : types.fields(variable.type)) {
                if (field.isStatic || !types.assignable(field.type, slot.type)) continue;
                String qualified = variable.name + "." + field.name;
                out.add(code(qualified, e -> e.mutate(() -> slot.set(field(variable, field)))));
                if (slot.type == JType.BOOLEAN) {
                    out.add(code("!" + qualified, e -> e.mutate(() -> slot.set(new Not(field(variable, field))))));
                }
            }
        }
        atomicReads(slot, visible, out);
    }

    private static void atomicStatements(List<VarDecl> visible, List<BlockSpec> out) {
        for (VarDecl variable : visible) {
            if (variable.type != JType.ATOMIC_INTEGER) continue;
            for (AtomicCounter.Op op : new AtomicCounter.Op[]{AtomicCounter.Op.INCREMENT, AtomicCounter.Op.GET_AND_INCREMENT}) {
                out.add(code(variable.name + "." + op.method + "();",
                        e -> e.insert(new ExprStmt(atomic(variable, op)))));
            }
        }
    }

    private static void atomicReads(ExprSlot slot, List<VarDecl> visible, List<BlockSpec> out) {
        if (slot.type != JType.INT) return;
        for (VarDecl variable : visible) {
            if (variable.type == JType.ATOMIC_INTEGER) out.add(code(variable.name + ".get()",
                    e -> e.mutate(() -> slot.set(atomic(variable, AtomicCounter.Op.GET)))));
        }
    }

    private static AtomicCounter atomic(VarDecl variable, AtomicCounter.Op op) {
        return new AtomicCounter(op, new VarRef(variable.name));
    }

    private static FieldAccess field(VarDecl variable, VarDecl field) {
        return new FieldAccess(new VarRef(variable.name), variable.type, field.name, field.type);
    }

    private static String free(String name, Set<String> taken) {
        if (!taken.contains(name)) return name;
        for (int i = 2; ; i++) if (!taken.contains(name + i)) return name + i;
    }

    private static BlockSpec code(String label, BlockSpec.Action action) {
        return new BlockSpec(label, false, action).inTab(BlockSpec.TAB_THREADS);
    }
}
