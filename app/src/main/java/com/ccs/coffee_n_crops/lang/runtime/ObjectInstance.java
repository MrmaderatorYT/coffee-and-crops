package com.ccs.coffee_n_crops.lang.runtime;

import com.ccs.coffee_n_crops.lang.ast.ClassDecl;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;
import java.util.LinkedHashMap;
import java.util.Map;

/** Per-object field storage; two new expressions never share their field values. */
public final class ObjectInstance {
    private static final int MAX_SNAPSHOT_FIELDS = 8;
    public final ClassDecl declaration;
    public final int id;
    private final Map<VarDecl, FieldValue> fields = new LinkedHashMap<>();

    private static final class FieldValue {
        final boolean publish;
        Value plain;
        volatile Value shared;

        FieldValue(VarDecl field) {
            publish = field.volatileField;
            set(defaultValue(field.type));
        }

        void set(Value value) {
            if (publish) shared = value;
            else plain = value;
        }

        Value get() { return publish ? shared : plain; }
    }
    public ObjectInstance(ClassDecl declaration, int id, ClassTable types) {
        this.declaration = declaration;
        this.id = id;
        for (VarDecl field : types.allFields(declaration.type())) fields.put(field, new FieldValue(field));
    }
    public Value get(VarDecl field) { return fields.get(field).get(); }
    public void set(VarDecl field, Value value) { fields.get(field).set(value); }
    public String snapshot() {
        StringBuilder text = new StringBuilder(declaration.name).append('#').append(id).append(" { ");
        boolean first = true;
        int shown = 0;
        for (Map.Entry<VarDecl, FieldValue> entry : fields.entrySet()) {
            if (shown++ == MAX_SNAPSHOT_FIELDS) { text.append(", …"); break; }
            if (!first) text.append(", ");
            first = false;
            Value value = entry.getValue().get();
            text.append(entry.getKey().name).append(" = ");
            if (value.raw instanceof ObjectInstance) {
                ObjectInstance object = (ObjectInstance) value.raw;
                text.append(object.declaration.name).append('#').append(object.id);
            } else if (value.type == JType.FARMHAND && value.raw != null) text.append("farmer");
            else text.append(value.raw);
        }
        return text.append(" }").toString();
    }
    public static Value defaultValue(JType type) {
        if (type == JType.INT) return Value.ofInt(0);
        if (type == JType.LONG) return Value.ofLong(0);
        if (type == JType.DOUBLE) return Value.ofDouble(0);
        if (type == JType.BOOLEAN) return Value.ofBoolean(false);
        return new Value(type, null);
    }
    @Override public String toString() { return declaration.name + "@" + Integer.toHexString(id); }
}
