package com.ccs.coffee_n_crops.lang.types;

import com.ccs.coffee_n_crops.farm.Direction;
import com.ccs.coffee_n_crops.farm.Crop;
import com.ccs.coffee_n_crops.lang.interp.ScriptError;
import com.ccs.coffee_n_crops.lang.runtime.ObjectArray;

import java.util.List;
import java.util.Map;

/**
 * A runtime value produced by evaluating an expression. Arrays and lists are held by reference,
 * as in Java: two variables can refer to the same list.
 */
public final class Value {
    public static final Value VOID = new Value(JType.VOID, null);

    public final JType type;
    public final Object raw;

    public Value(JType type, Object raw) {
        this.type = type;
        this.raw = raw;
    }

    public static Value ofInt(int v) {
        return new Value(JType.INT, v);
    }

    public static Value ofLong(long v) {
        return new Value(JType.LONG, v);
    }

    public static Value ofBoolean(boolean v) {
        return new Value(JType.BOOLEAN, v);
    }

    public int asInt() {
        if (type != JType.INT) throw new ScriptError("Expected int but found " + type.javaName);
        return (Integer) raw;
    }

    /** A long, or an int widened to long. */
    public long asLong() {
        if (type == JType.INT) return asInt();
        if (type != JType.LONG) throw new ScriptError("Expected long but found " + type.javaName);
        return (Long) raw;
    }

    public static Value ofDouble(double v) {
        return new Value(JType.DOUBLE, v);
    }

    public static Value ofString(String v) {
        return new Value(JType.STRING, v);
    }

    /** A double, or an integer widened to double as Java does implicitly. */
    public double asDouble() {
        if (type == JType.INT) return (Integer) raw;
        if (type == JType.LONG) return asLong();
        if (type != JType.DOUBLE) throw new ScriptError("Expected double but found " + type.javaName);
        return (Double) raw;
    }

    public String asString() {
        if (type != JType.STRING) throw new ScriptError("Expected String but found " + type.javaName);
        return (String) raw;
    }

    public boolean asBoolean() {
        if (type != JType.BOOLEAN) throw new ScriptError("Expected boolean but found " + type.javaName);
        return (Boolean) raw;
    }

    public Direction asDirection() {
        if (type != JType.DIRECTION) throw new ScriptError("Expected Direction but found " + type.javaName);
        return (Direction) raw;
    }

    public Crop[] asCropArray() {
        if (type != JType.CROP_ARRAY) throw new ScriptError("array required, but " + type.javaName + " found");
        if (raw == null) throw new ScriptError("NullPointerException");
        return (Crop[]) raw;
    }

    public int[] asIntArray() {
        if (type != JType.INT_ARRAY) throw new ScriptError("Expected int[] but found " + type.javaName);
        return (int[]) raw;
    }

    public int[][] asIntGrid() {
        if (type != JType.INT_GRID) throw new ScriptError("Expected int[][] but found " + type.javaName);
        return (int[][]) raw;
    }

    public ObjectArray asObjectArray() {
        if (type.elementType == null) throw new ScriptError("array required, but " + type.javaName + " found");
        if (raw == null) throw new ScriptError("NullPointerException");
        return (ObjectArray) raw;
    }

    @SuppressWarnings("unchecked")
    public List<Integer> asIntList() {
        if (type != JType.INT_LIST) throw new ScriptError("Expected ArrayList but found " + type.javaName);
        return (List<Integer>) raw;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Integer> asStringIntMap() {
        if (type != JType.STRING_INT_MAP) throw new ScriptError("Expected HashMap but found " + type.javaName);
        if (raw == null) throw new ScriptError("NullPointerException");
        return (Map<String, Integer>) raw;
    }

    @Override
    public String toString() {
        return String.valueOf(raw);
    }
}
