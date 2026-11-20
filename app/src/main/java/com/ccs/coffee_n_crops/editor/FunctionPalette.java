package com.ccs.coffee_n_crops.editor;

import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.ast.expr.Binary;
import com.ccs.coffee_n_crops.lang.ast.expr.FunctionCall;
import com.ccs.coffee_n_crops.lang.ast.expr.FunctionType;
import com.ccs.coffee_n_crops.lang.ast.expr.FunctionValue;
import com.ccs.coffee_n_crops.lang.ast.expr.IntegerMethodRef;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamCallback;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamLambda;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamMethodRef;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.ExprStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.print.JavaPrinter;
import com.ccs.coffee_n_crops.lang.types.JType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/** Builds standalone functional values and typed invocations. */
final class FunctionPalette {
    private static final int DEFAULT_INPUT = 2;
    private static final String[] TRANSFORM_NAMES = {"transform", "mapper"};
    private static final String[] PREDICATE_NAMES = {"matches", "predicate"};
    private static final String[] CONSUMER_NAMES = {"store", "consumer"};
    private static final String[] FACTORY_NAMES = {"allocate", "arrayFactory"};
    private static final String[] PARAMETER_NAMES = {"value", "item", "element"};

    private FunctionPalette() { }

    static void statements(List<VarDecl> visible, Set<String> taken, List<BlockSpec> out) {
        String parameter = PaletteProvider.freeName(PARAMETER_NAMES, taken);
        for (FunctionType type : FunctionType.values()) {
            String name = PaletteProvider.freeName(names(type), taken);
            for (Supplier<StreamCallback> factory : initializers(type, parameter, visible)) {
                String label = type.type.javaName + " " + name + " = " + JavaPrinter.callbackText(factory.get()) + ";";
                out.add(code(label, editor -> editor.insert(new VarDecl(type.type, name,
                        new FunctionValue(type, factory.get())))));
            }
        }

        for (VarDecl variable : visible) {
            FunctionType type = FunctionType.of(variable.type);
            if (type != FunctionType.CONSUMER) continue;
            out.add(code(callText(variable.name, type) + ";", editor -> editor.insert(new ExprStmt(
                    new FunctionCall(variable.name, type, Ast.lit(DEFAULT_INPUT))))));
        }
    }

    static void slots(ExprSlot slot, List<VarDecl> visible, Set<String> taken, List<BlockSpec> out) {
        FunctionType initializer = FunctionType.of(slot.type);
        if (initializer != null) {
            String parameter = PaletteProvider.freeName(PARAMETER_NAMES, taken);
            for (Supplier<StreamCallback> factory : initializers(initializer, parameter, visible)) {
                out.add(code(JavaPrinter.callbackText(factory.get()), editor -> editor.mutate(() ->
                        slot.set(new FunctionValue(initializer, factory.get())))));
            }
            for (VarDecl variable : visible) {
                if (variable.type == slot.type) {
                    out.add(code(variable.name, editor -> editor.mutate(() -> slot.set(new VarRef(variable.name)))));
                }
            }
        }
        for (VarDecl variable : visible) {
            FunctionType type = FunctionType.of(variable.type);
            if (type != null && type.result == slot.type) {
                out.add(code(callText(variable.name, type), editor -> editor.mutate(() ->
                        slot.set(new FunctionCall(variable.name, type, Ast.lit(DEFAULT_INPUT))))));
            }
        }
    }

    private static String[] names(FunctionType type) {
        return switch (type) {
            case TRANSFORM -> TRANSFORM_NAMES;
            case PREDICATE -> PREDICATE_NAMES;
            case CONSUMER -> CONSUMER_NAMES;
            case ARRAY_FACTORY -> FACTORY_NAMES;
        };
    }

    private static List<Supplier<StreamCallback>> initializers(FunctionType type, String parameter,
                                                                 List<VarDecl> visible) {
        return switch (type) {
            case TRANSFORM -> Arrays.asList(
                    () -> new StreamLambda(parameter, JType.INT,
                            new Binary(Binary.Op.ADD, new VarRef(parameter), Ast.lit(1))),
                    () -> new IntegerMethodRef(IntegerMethodRef.Kind.ABS),
                    () -> new IntegerMethodRef(IntegerMethodRef.Kind.INT_VALUE));
            case PREDICATE -> Collections.singletonList(() -> new StreamLambda(parameter, JType.BOOLEAN,
                    new Binary(Binary.Op.GE, new VarRef(parameter), Ast.lit(0))));
            case ARRAY_FACTORY -> Collections.singletonList(() -> new IntegerMethodRef(IntegerMethodRef.Kind.ARRAY));
            case CONSUMER -> consumers(visible);
        };
    }

    private static List<Supplier<StreamCallback>> consumers(List<VarDecl> visible) {
        List<Supplier<StreamCallback>> choices = new ArrayList<>();
        for (VarDecl variable : visible) {
            if (variable.type == JType.INT_LIST) choices.add(() -> new StreamMethodRef(variable.name, "add"));
        }
        return choices;
    }

    private static String callText(String name, FunctionType type) {
        return name + "." + type.invocation + "(" + DEFAULT_INPUT + ")";
    }

    private static BlockSpec code(String label, BlockSpec.Action action) {
        return new BlockSpec(label, false, action).inTab(BlockSpec.TAB_STREAMS);
    }
}
