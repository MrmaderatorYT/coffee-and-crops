package com.ccs.coffee_n_crops.lang.runtime;

import com.ccs.coffee_n_crops.farm.Direction;
import com.ccs.coffee_n_crops.farm.Farmhand;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.MethodDecl;
import com.ccs.coffee_n_crops.lang.interp.ScriptError;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;

import java.util.List;

/** The Object methods exposed to player code, including a type token for interpreted classes. */
public final class ObjectMethods {
    private ObjectMethods() { }

    public static MethodDecl signature(String name) {
        return switch (name) {
            case "equals" -> new MethodDecl(name, JType.BOOLEAN, Ast.declare(JType.OBJECT, "other"));
            case "hashCode" -> new MethodDecl(name, JType.INT);
            case "toString" -> new MethodDecl(name, JType.STRING);
            case "getClass" -> new MethodDecl(name, JType.CLASS_TOKEN);
            default -> null;
        };
    }

    public static boolean contains(String name) {
        return name.equals("equals") || name.equals("hashCode") || name.equals("toString") || name.equals("getClass");
    }

    public static JType runtimeType(Value value) {
        Object raw = value.raw;
        if (raw instanceof ObjectInstance object) return object.declaration.type();
        if (raw instanceof ObjectArray array) return JType.objectArray(array.componentType());
        if (raw instanceof String) return JType.STRING;
        if (raw instanceof List<?>) return JType.INT_LIST;
        if (raw instanceof java.util.Map<?, ?>) return JType.STRING_INT_MAP;
        if (raw instanceof com.ccs.coffee_n_crops.farm.Crop) return JType.CROP;
        if (raw instanceof com.ccs.coffee_n_crops.farm.Crop[]) return JType.CROP_ARRAY;
        if (raw instanceof int[]) return JType.INT_ARRAY;
        if (raw instanceof int[][]) return JType.INT_GRID;
        if (raw instanceof Farmhand) return JType.FARMHAND;
        if (raw instanceof Direction) return JType.DIRECTION;
        if (raw instanceof JType || raw instanceof Class<?>) return JType.CLASS_TOKEN;
        return value.type;
    }

    public static Value invoke(Value receiver, String name, Value[] arguments) {
        Object raw = receiver.raw;
        if (raw == null) throw new ScriptError("NullPointerException");
        if (arguments.length != (name.equals("equals") ? 1 : 0)) {
            throw new ScriptError("method " + name + " cannot be applied to given types");
        }
        return switch (name) {
            case "equals" -> Value.ofBoolean(raw.equals(arguments[0].raw));
            case "hashCode" -> Value.ofInt(raw.hashCode());
            case "toString" -> Value.ofString(raw instanceof JType type ? "class " + type.javaName : raw.toString());
            case "getClass" -> new Value(JType.CLASS_TOKEN, raw instanceof ObjectInstance || raw instanceof ObjectArray
                    ? runtimeType(receiver) : raw instanceof JType ? Class.class : raw.getClass());
            default -> throw new ScriptError("cannot find symbol: method " + name + "()");
        };
    }
}
