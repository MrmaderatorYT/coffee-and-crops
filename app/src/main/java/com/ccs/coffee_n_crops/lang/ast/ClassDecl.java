package com.ccs.coffee_n_crops.lang.ast;

import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.types.JType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A user class or interface, represented without loading Java classes. */
public final class ClassDecl extends Node {
    public final String name;
    public final boolean isInterface;
    public String superName;
    /** The interface explicitly implemented here, independent of any inherited interfaces. */
    public String interfaceName;
    private final List<VarDecl> fields = new ArrayList<>();
    private final List<MethodDecl> methods = new ArrayList<>();
    private ConstructorDecl constructor;

    public ClassDecl(String name) { this(name, false); }
    public ClassDecl(String name, String superName) { this(name); this.superName = superName; }
    public ClassDecl(String name, boolean isInterface) {
        this.name = name;
        this.isInterface = isInterface;
    }
    public JType type() { return JType.reference(name); }
    public List<VarDecl> fields() { return Collections.unmodifiableList(fields); }
    public List<MethodDecl> methods() { return Collections.unmodifiableList(methods); }
    public ConstructorDecl constructor() { return constructor; }
    public void addField(VarDecl field) { field.parent = this; fields.add(field); }
    public void removeField(VarDecl field) { if (fields.remove(field)) field.parent = null; }
    public void addMethod(MethodDecl method) { method.parent = this; methods.add(method); }
    public void removeMethod(MethodDecl method) { if (methods.remove(method)) method.parent = null; }
    public void setConstructor(ConstructorDecl constructor) {
        if (this.constructor != null) this.constructor.parent = null;
        this.constructor = constructor;
        if (constructor != null) constructor.parent = this;
    }
}
