package com.ccs.coffee_n_crops.lang.interp;

import com.ccs.coffee_n_crops.farm.Farmhand;
import com.ccs.coffee_n_crops.lang.runtime.ObjectArray;
import com.ccs.coffee_n_crops.lang.runtime.ObjectInstance;
import com.ccs.coffee_n_crops.lang.types.Value;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One level of local variables; scopes chain outward like Java's nested braces. */
public final class Scope {
    private static final int MAX_BINDINGS = 64;
    private static final int MAX_TEXT = 80;
    public final Scope parent;
    private final Map<String, Value> vars = new LinkedHashMap<>();

    public Scope(Scope parent) {
        this.parent = parent;
    }

    public void declare(String name, Value value) {
        if (lookup(name) != null) {
            throw new ScriptError("Variable '" + name + "' is already defined");
        }
        vars.put(name, value);
    }

    public Value get(String name) {
        Scope owner = lookup(name);
        if (owner == null) throw new ScriptError("Cannot find variable '" + name + "'");
        return owner.vars.get(name);
    }

    public void set(String name, Value value) {
        Scope owner = lookup(name);
        if (owner == null) throw new ScriptError("Cannot find variable '" + name + "'");
        owner.vars.put(name, value);
    }

    public boolean contains(String name) { return lookup(name) != null; }

    /** Captures variable bindings by value, retaining references to their shared objects. */
    Scope capture() {
        Scope captured = new Scope(null);
        for (Scope current = this; current != null; current = current.parent) {
            for (Map.Entry<String, Value> variable : current.vars.entrySet()) {
                captured.vars.putIfAbsent(variable.getKey(), variable.getValue());
            }
        }
        return captured;
    }

    String snapshot() {
        StringBuilder result = new StringBuilder();
        int remaining = MAX_BINDINGS;
        for (Scope current = this; current != null; current = current.parent) {
            if (current.vars.isEmpty()) continue;
            if (result.length() > 0) result.append('\n');
            for (var entry : current.vars.entrySet()) {
                if (remaining-- == 0) return result.append("…").toString();
                Value value = entry.getValue();
                result.append(value.type.javaName).append(' ').append(entry.getKey()).append(" = ")
                        .append(displayValue(value.raw)).append('\n');
            }
        }
        return result.toString().trim();
    }

    private static String displayValue(Object value) {
        if (value == null || value instanceof Number || value instanceof Boolean || value instanceof Enum<?>) {
            return String.valueOf(value);
        }
        if (value instanceof String text) {
            String preview = text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) + "…" : text;
            return "\"" + preview.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t") + "\"";
        }
        if (value instanceof Farmhand hand) return "#" + hand.id;
        if (value instanceof int[] array) return "length " + array.length;
        if (value instanceof int[][] grid) return "length " + grid.length;
        if (value instanceof ObjectArray array) return "length " + array.length();
        if (value instanceof List<?> list) return "size " + list.size();
        if (value instanceof Map<?, ?> map) return "size " + map.size();
        if (value instanceof ObjectInstance object) return object.toString();
        return "…";
    }

    private Scope lookup(String name) {
        for (Scope s = this; s != null; s = s.parent) {
            if (s.vars.containsKey(name)) return s;
        }
        return null;
    }
}
