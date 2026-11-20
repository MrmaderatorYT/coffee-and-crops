package com.ccs.coffee_n_crops.lang.runtime;

import com.ccs.coffee_n_crops.lang.interp.ScriptError;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;

/** Array storage keeps its allocation-time component type across covariant aliases. */
public final class ObjectArray {
    private final JType component;
    private final ObjectInstance[] items;

    public ObjectArray(JType component, int length) {
        this.component = component;
        items = new ObjectInstance[length];
    }

    public int length() { return items.length; }

    public JType componentType() { return component; }

    public Value get(int index, JType staticComponent) {
        return new Value(staticComponent, items[checkedIndex(index)]);
    }

    public void set(int index, Value value, ClassTable classes) {
        int target = checkedIndex(index);
        if (value.raw == null) {
            items[target] = null;
            return;
        }
        if (!(value.raw instanceof ObjectInstance)) throw new ScriptError("ArrayStoreException: " + value.type.javaName);
        ObjectInstance object = (ObjectInstance) value.raw;
        if (!classes.assignable(object.declaration.type(), component)) {
            throw new ScriptError("ArrayStoreException: " + object.declaration.name);
        }
        items[target] = object;
    }

    private int checkedIndex(int index) {
        if (index < 0 || index >= items.length) {
            throw new ScriptError("ArrayIndexOutOfBoundsException: Index " + index
                    + " out of bounds for length " + items.length);
        }
        return index;
    }

    @Override public String toString() { return component.javaName + "[" + items.length + "]"; }
}
