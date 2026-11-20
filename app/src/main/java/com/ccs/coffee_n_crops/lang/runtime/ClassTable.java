package com.ccs.coffee_n_crops.lang.runtime;

import com.ccs.coffee_n_crops.lang.ast.ClassDecl;
import com.ccs.coffee_n_crops.lang.ast.MethodDecl;
import com.ccs.coffee_n_crops.lang.ast.Node;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.types.JType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolves named types and inherited members without reflection. */
public final class ClassTable {
    private final Map<String, ClassDecl> declarations = new LinkedHashMap<>();
    public ClassTable(Block program) {
        for (ClassDecl declaration : program.classes()) declarations.put(declaration.name, declaration);
    }
    public ClassDecl findClass(String name) { return declarations.get(name); }
    public ClassDecl findClass(JType type) { return type == null ? null : findClass(type.className); }
    public VarDecl field(JType type, String name) {
        for (ClassDecl declaration : lineage(type)) {
            for (VarDecl field : declaration.fields()) if (field.name.equals(name)) return field;
        }
        return null;
    }
    public MethodDecl method(JType type, String name) {
        for (ClassDecl declaration : lineage(type)) {
            for (MethodDecl method : declaration.methods()) if (method.name.equals(name)) return method;
        }
        return null;
    }
    /** Visible fields, nearest declaration first; inherited hidden names are omitted. */
    public List<VarDecl> fields(JType type) {
        List<VarDecl> result = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (ClassDecl declaration : lineage(type)) {
            for (VarDecl field : declaration.fields()) if (names.add(field.name)) result.add(field);
        }
        return result;
    }
    public List<MethodDecl> methods(JType type) {
        List<MethodDecl> result = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (ClassDecl declaration : lineage(type)) {
            for (MethodDecl method : declaration.methods()) if (names.add(method.name)) result.add(method);
        }
        return result;
    }
    /** Includes hidden fields, because each declaring class owns a distinct storage slot. */
    public List<VarDecl> allFields(JType type) {
        List<VarDecl> result = new ArrayList<>();
        List<ClassDecl> classes = lineage(type);
        for (int i = classes.size() - 1; i >= 0; i--) {
            for (VarDecl field : classes.get(i).fields()) if (!field.isStatic) result.add(field);
        }
        return result;
    }
    public List<ClassDecl> lineage(JType type) {
        return lineage(type, declarations);
    }

    private static List<ClassDecl> lineage(JType type, Map<String, ClassDecl> declarations) {
        List<ClassDecl> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        ClassDecl declaration = type == null ? null : declarations.get(type.className);
        while (declaration != null && visited.add(declaration.name)) {
            result.add(declaration);
            declaration = declarations.get(declaration.superName);
        }
        return result;
    }

    public boolean assignable(JType actual, JType expected) {
        return assignable(actual, expected, declarations);
    }

    public boolean instanceOfConvertible(JType source, JType target) {
        return instanceOfConvertible(source, target, declarations);
    }

    /** A non-final class and an interface can meet in a later subclass. */
    public static boolean instanceOfConvertible(JType source, JType target, Map<String, ClassDecl> declarations) {
        if (assignable(source, target, declarations) || assignable(target, source, declarations)) return true;
        ClassDecl left = declarations.get(source.javaName);
        ClassDecl right = declarations.get(target.javaName);
        return left != null && right != null && (left.isInterface || right.isInterface);
    }

    /** Shared by runtime conversions and the pre-run type checker. */
    public static boolean assignable(JType actual, JType expected, Map<String, ClassDecl> declarations) {
        if (actual == expected) return true;
        if (actual == JType.NULL) return expected.isReference();
        if (expected == JType.OBJECT) return actual.isReference();
        if (actual == JType.INT && (expected == JType.LONG || expected == JType.DOUBLE)) return true;
        if (actual == JType.LONG && expected == JType.DOUBLE) return true;
        if (ExceptionTypes.assignable(actual, expected)) return true;
        if (actual.elementType != null && expected.elementType != null) {
            return assignable(actual.elementType, expected.elementType, declarations);
        }
        if (actual.isClass() && expected.isClass()) {
            for (ClassDecl declaration : lineage(actual, declarations)) {
                if (declaration.type() == expected) return true;
                ClassDecl contract = declarations.get(declaration.interfaceName);
                if (contract != null && contract.isInterface && contract.type() == expected) return true;
            }
        }
        return false;
    }
    public static ClassDecl classOf(Node node) {
        for (Node current = node; current != null; current = current.parent) {
            if (current instanceof ClassDecl) return (ClassDecl) current;
        }
        return null;
    }
    public static Block programOf(Node node) {
        Node current = node;
        while (current != null && current.parent != null) current = current.parent;
        return current instanceof Block ? (Block) current : null;
    }
}
