package com.ccs.coffee_n_crops.lang.ast;

import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A constructor's exact source parameters and body, without implicit arguments. */
public final class ConstructorDecl extends Node {
    private final List<VarDecl> parameterList = new ArrayList<>();
    public final List<VarDecl> parameters = Collections.unmodifiableList(parameterList);
    public final Block body = new Block();
    public ConstructorDecl(VarDecl... parameters) {
        for (VarDecl parameter : parameters) addParameter(parameter);
        body.parent = this;
    }
    public void addParameter(VarDecl parameter) { parameter.parent = this; parameterList.add(parameter); }
    public void removeParameter(VarDecl parameter) { if (parameterList.remove(parameter)) parameter.parent = null; }
}
