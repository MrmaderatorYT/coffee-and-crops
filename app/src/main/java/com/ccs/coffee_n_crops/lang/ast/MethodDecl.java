package com.ccs.coffee_n_crops.lang.ast;

import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.types.JType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A Program helper, class method, or interface signature. */
public final class MethodDecl extends Node {
    public final String name;
    public final JType returnType;
    public boolean override;
    /** Extra parameters; their initializer slots are placeholders and are never executed. */
    public final List<VarDecl> parameters;
    private final List<VarDecl> parameterList = new ArrayList<>();
    public final Block body = new Block();

    public MethodDecl(String name, JType returnType, VarDecl... parameters) {
        this.name = name;
        this.returnType = returnType;
        this.parameters = Collections.unmodifiableList(parameterList);
        for (VarDecl parameter : parameters) addParameter(parameter);
        body.parent = this;
    }
    public void addParameter(VarDecl parameter) { parameter.parent = this; parameterList.add(parameter); }
    public void removeParameter(VarDecl parameter) { if (parameterList.remove(parameter)) parameter.parent = null; }
    public boolean isInstance() { return parent instanceof ClassDecl; }
    public boolean isInterfaceSignature() { return isInstance() && ((ClassDecl) parent).isInterface; }

    /** Match Java's method signature, which excludes the return type. */
    public boolean sameSignature(MethodDecl other) {
        if (other == null || !name.equals(other.name) || parameters.size() != other.parameters.size()) return false;
        for (int i = 0; i < parameters.size(); i++) {
            if (parameters.get(i).type != other.parameters.get(i).type) return false;
        }
        return true;
    }
}
