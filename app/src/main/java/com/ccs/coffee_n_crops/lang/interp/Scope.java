package com.ccs.coffee_n_crops.lang.interp;

import com.ccs.coffee_n_crops.lang.types.Value;

import java.util.HashMap;
import java.util.Map;

/** One level of local variables; scopes chain outward like Java's nested braces. */
public final class Scope {
    public final Scope parent;
    private final Map<String, Value> vars = new HashMap<>();

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

    private Scope lookup(String name) {
        for (Scope s = this; s != null; s = s.parent) {
            if (s.vars.containsKey(name)) return s;
        }
        return null;
    }
}
