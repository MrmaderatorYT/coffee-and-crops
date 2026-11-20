package com.ccs.coffee_n_crops.lang.types;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Interned Java types, including named references and arrays of those references. */
public final class JType {
    private static final Map<String, JType> BUILTINS = new LinkedHashMap<>();
    private static final Map<String, JType> REFERENCES = new ConcurrentHashMap<>();
    private static final Map<JType, JType> OBJECT_ARRAYS = new ConcurrentHashMap<>();
    public static final JType INT = builtin("INT", "int");
    public static final JType LONG = builtin("LONG", "long");
    public static final JType DOUBLE = builtin("DOUBLE", "double");
    public static final JType BOOLEAN = builtin("BOOLEAN", "boolean");
    public static final JType STRING = builtin("STRING", "String");
    public static final JType DIRECTION = builtin("DIRECTION", "Direction");
    public static final JType INT_ARRAY = builtin("INT_ARRAY", "int[]");
    public static final JType INT_GRID = builtin("INT_GRID", "int[][]");
    public static final JType INT_LIST = builtin("INT_LIST", "ArrayList<Integer>");
    public static final JType ATOMIC_INTEGER = builtin("ATOMIC_INTEGER", "java.util.concurrent.atomic.AtomicInteger");
    public static final JType REENTRANT_LOCK = builtin("REENTRANT_LOCK", "java.util.concurrent.locks.ReentrantLock");
    public static final JType INT_ITERATOR = builtin("INT_ITERATOR", "java.util.Iterator<Integer>");
    public static final JType INT_OPTIONAL = builtin("INT_OPTIONAL", "java.util.Optional<Integer>");
    public static final JType OBJECT = builtin("OBJECT", "Object");
    public static final JType CLASS_TOKEN = builtin("CLASS_TOKEN", "java.lang.Class<?>");
    public static final JType FARM_EXCEPTION = builtin("FARM_EXCEPTION", "FarmException");
    public static final JType EMPTY_TANK_EXCEPTION = builtin("EMPTY_TANK_EXCEPTION", "EmptyTankException");
    public static final JType OUT_OF_BOUNDS_FARM_EXCEPTION = builtin("OUT_OF_BOUNDS_FARM_EXCEPTION", "OutOfBoundsFarmException");
    /** Keep the previous key so saved signatures and typed slots can still be read. */
    public static final JType FARMHAND = builtin("DRONE", "Farmhand");
    public static final JType NULL = builtin("NULL", "<null>");
    public static final JType VOID = builtin("VOID", "void");
    public final String javaName;
    /** User class or interface name, or null for other types. */
    public final String className;
    /** Component type for a user object array, otherwise null. */
    public final JType elementType;
    private final String savedName;

    private JType(String savedName, String javaName, String className, JType elementType) {
        this.savedName = savedName;
        this.javaName = javaName;
        this.className = className;
        this.elementType = elementType;
    }
    private static JType builtin(String name, String javaName) {
        JType type = new JType(name, javaName, null, null);
        BUILTINS.put(name, type);
        return type;
    }
    public static JType reference(String className) {
        if (className == null || className.isEmpty()) throw new IllegalArgumentException("empty class name");
        return REFERENCES.computeIfAbsent(className, name -> new JType("CLASS:" + name, name, name, null));
    }
    public static JType objectArray(JType elementType) {
        if (elementType == null || !elementType.isUserClass()) {
            throw new IllegalArgumentException("Expected a user class or interface component type");
        }
        return OBJECT_ARRAYS.computeIfAbsent(elementType,
                type -> new JType("ARRAY:" + type.name(), type.javaName + "[]", null, type));
    }
    public boolean isClass() { return className != null; }
    public boolean isUserClass() { return isClass(); }
    public boolean isReference() {
        return this != INT && this != LONG && this != DOUBLE && this != BOOLEAN && this != VOID;
    }
    /** Stable save spelling; pre-OOP built-in type names remain unchanged. */
    public String name() { return savedName; }
    public static JType valueOf(String name) {
        if (name.startsWith("CLASS:")) return reference(name.substring("CLASS:".length()));
        if (name.startsWith("ARRAY:")) return objectArray(valueOf(name.substring("ARRAY:".length())));
        JType type = BUILTINS.get(name);
        if (type == null) throw new IllegalArgumentException("Unknown type " + name);
        return type;
    }
    public static JType[] values() { return BUILTINS.values().toArray(new JType[0]); }
    @Override public String toString() { return savedName; }
}
