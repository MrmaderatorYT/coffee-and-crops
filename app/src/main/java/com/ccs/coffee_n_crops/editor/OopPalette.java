package com.ccs.coffee_n_crops.editor;

import com.ccs.coffee_n_crops.editor.BlockSpec.ClassMemberKind;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.ClassDecl;
import com.ccs.coffee_n_crops.lang.ast.ConstructorDecl;
import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.ast.MethodDecl;
import com.ccs.coffee_n_crops.lang.ast.Node;
import com.ccs.coffee_n_crops.lang.ast.expr.Access;
import com.ccs.coffee_n_crops.lang.ast.expr.FarmhandRef;
import com.ccs.coffee_n_crops.lang.ast.expr.FieldAccess;
import com.ccs.coffee_n_crops.lang.ast.expr.InstanceCall;
import com.ccs.coffee_n_crops.lang.ast.expr.InstanceOf;
import com.ccs.coffee_n_crops.lang.ast.expr.StaticConstant;
import com.ccs.coffee_n_crops.lang.ast.expr.NewObject;
import com.ccs.coffee_n_crops.lang.ast.expr.NewObjectArray;
import com.ccs.coffee_n_crops.lang.ast.expr.ObjectArrayGet;
import com.ccs.coffee_n_crops.lang.ast.expr.ThisRef;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.expr.Binary;
import com.ccs.coffee_n_crops.lang.ast.expr.Literal;
import com.ccs.coffee_n_crops.lang.ast.expr.MethodCall;
import com.ccs.coffee_n_crops.lang.ast.expr.StrEquals;
import com.ccs.coffee_n_crops.lang.ast.expr.Not;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.ExprStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.FieldAssign;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ObjectArrayAssign;
import com.ccs.coffee_n_crops.lang.ast.stmt.ReturnStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.SuperCall;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.check.ScopeResolver;
import com.ccs.coffee_n_crops.lang.check.Validator;
import com.ccs.coffee_n_crops.lang.print.JavaPrinter;
import com.ccs.coffee_n_crops.lang.runtime.ClassTable;
import com.ccs.coffee_n_crops.lang.runtime.NativeRegistry;
import com.ccs.coffee_n_crops.lang.runtime.ObjectMethods;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.progress.Unlock;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Supplier;

/** Class and object choices kept separate from the introductory statement palette. */
final class OopPalette {
    private static final String[] WORK_NAMES = {"work", "harvestRow", "tend", "increment"};
    private static final int DEFAULT_OBJECT_ARRAY_LENGTH = 2;
    private static final int DEFAULT_STOPS = 3;
    private static final String[] OBJECT_METHOD_NAMES = {"equals", "hashCode", "toString", "getClass"};

    private OopPalette() { }

    /** Defaults in instance bodies must name an actual Farmhand reference in Java source. */
    static Expr contextualize(Expr expression, ClassDecl cls, List<VarDecl> visible) {
        if (cls == null) return expression;
        if (expression instanceof MethodCall && ((MethodCall) expression).receiver.equals("farmer")) {
            MethodCall call = (MethodCall) expression;
            List<Target> farmers = farmhandTargets(cls, visible);
            if (farmers.isEmpty()) {
                if (call.returnType == JType.BOOLEAN) return Ast.lit(true);
                return argument(call.returnType, new ArrayList<>(), cls);
            }
            Expr[] args = new Expr[call.args.size()];
            for (int i = 0; i < args.length; i++) args[i] = call.args.get(i).get();
            return nativeCall(farmers.get(0), call.name, args);
        }
        if (expression instanceof Binary) {
            Binary binary = (Binary) expression;
            binary.left.set(contextualize(binary.left.get(), cls, visible));
            binary.right.set(contextualize(binary.right.get(), cls, visible));
        } else if (expression instanceof StrEquals) {
            StrEquals equals = (StrEquals) expression;
            equals.left.set(contextualize(equals.left.get(), cls, visible));
            equals.right.set(contextualize(equals.right.get(), cls, visible));
        } else if (expression instanceof Not) {
            Not not = (Not) expression;
            not.operand.set(contextualize(not.operand.get(), cls, visible));
        }
        return expression;
    }

    static boolean structural(EditorCursor cursor) {
        return cursor.declaration instanceof ClassDecl
                || cursor.declaration instanceof VarDecl && cursor.declaration.parent instanceof ClassDecl
                || cursor.declaration instanceof MethodDecl && ((MethodDecl) cursor.declaration).isInterfaceSignature();
    }

    static void members(EditorCursor cursor, Set<String> unlocks, Block program, List<BlockSpec> out) {
        members(cursor.classDecl, unlocks, program, out);
    }

    /** Class declarations are available independently of the current statement or expression. */
    static List<BlockSpec> creationChoices(ClassDecl cls, Set<String> unlocks, Block program) {
        List<BlockSpec> choices = new ArrayList<>();
        members(cls, unlocks, program, choices);
        choices.removeIf(choice -> choice.classMemberKind == null);
        return choices;
    }

    private static void members(ClassDecl cls, Set<String> unlocks, Block program, List<BlockSpec> out) {
        if (cls == null || !unlocks.contains(Unlock.CLASSES)) return;
        Set<String> methodNames = new HashSet<>();
        for (MethodDecl method : cls.methods()) methodNames.add(method.name);
        if (cls.isInterface) {
            if (unlocks.contains(Unlock.INTERFACES)) interfaceSignatures(out, methodNames, unlocks);
            return;
        }
        List<MethodDecl> overrideCandidates = new ArrayList<>();
        if (unlocks.contains(Unlock.INTERFACES)) interfaceImplementations(cls, program, overrideCandidates, out);
        Set<String> names = new HashSet<>();
        for (VarDecl field : cls.fields()) names.add(field.name);
        String[] pool = cls.name.equals("Counter") ? new String[]{"value", "count", "total"}
                : cls.name.equals("RowPlan") ? new String[]{"row", "width", "count"}
                : new String[]{"row", "width", "count", "value"};
        String name = free(pool, names);
        out.add(command("+ int " + name + " = 0;", e -> e.addField(Ast.declare(JType.INT, name)))
                .asClassCreation(ClassMemberKind.FIELD));
        if (unlocks.contains(Unlock.CONSTANTS)) {
            String constant = free(new String[]{"STOPS", "LIMIT", "WIDTH"}, names);
            out.add(command("+ static final int " + constant + " = " + DEFAULT_STOPS + ";", e ->
                    e.addField(new VarDecl(JType.INT, constant, Ast.lit(DEFAULT_STOPS), true, true)))
                    .asClassCreation(ClassMemberKind.FIELD));
        }
        if (unlocks.contains(Unlock.INSTANCE_METHODS) && !names.contains("farmer")) {
            out.add(command("+ Farmhand farmer;", e -> e.addField(new VarDecl(JType.FARMHAND, "farmer",
                    new Literal(JType.FARMHAND, null)))).asClassCreation(ClassMemberKind.FIELD));
        }
        if (unlocks.contains(Unlock.CONSTRUCTORS) && cls.constructor() == null) {
            out.add(command("+ " + cls.name + "() { }", e -> e.addConstructor(new ConstructorDecl()))
                    .asClassCreation(ClassMemberKind.CONSTRUCTOR));
        }
        if (cls.superName != null && unlocks.contains(Unlock.INHERITANCE)
                && unlocks.contains(Unlock.INSTANCE_METHODS)) {
            ClassTable table = new ClassTable(program);
            overrideCandidates.addAll(table.methods(JType.reference(cls.superName)));
        }
        if (unlocks.contains(Unlock.OBJECT_METHODS) && unlocks.contains(Unlock.RETURN)) {
            for (String objectMethod : OBJECT_METHOD_NAMES) {
                if (!objectMethod.equals("getClass")) overrideCandidates.add(ObjectMethods.signature(objectMethod));
            }
        }
        List<MethodDecl> offeredOverrides = new ArrayList<>();
        for (MethodDecl signature : overrideCandidates) {
            if (methodNames.contains(signature.name) || offeredOverrides.stream().anyMatch(signature::sameSignature)) continue;
            offeredOverrides.add(signature);
            out.add(overrideCreation(signature));
        }
        if (unlocks.contains(Unlock.INSTANCE_METHODS)) {
            String methodName = free(WORK_NAMES, methodNames);
            out.add(command("+ void " + methodName + "() { }",
                    e -> e.addInstanceMethod(new MethodDecl(methodName, JType.VOID)))
                    .asMethodCreation().asClassCreation(ClassMemberKind.METHOD));
            if (unlocks.contains(Unlock.RETURN)) {
                String getter = free(new String[]{"get", "count", "measure"}, methodNames);
                out.add(command("+ int " + getter + "() { }",
                        e -> e.addInstanceMethod(new MethodDecl(getter, JType.INT)))
                        .asMethodCreation().asClassCreation(ClassMemberKind.METHOD));
            }
        }
    }

    private static void interfaceSignatures(List<BlockSpec> out, Set<String> methodNames, Set<String> unlocks) {
        String name = free(WORK_NAMES, methodNames);
        out.add(command("+ void " + name + "();",
                e -> e.addInstanceMethod(new MethodDecl(name, JType.VOID)))
                .asMethodCreation().asClassCreation(ClassMemberKind.METHOD));
        out.add(command("+ void " + name + "(Farmhand farmer);", e -> e.addInstanceMethod(new MethodDecl(name,
                JType.VOID, new VarDecl(JType.FARMHAND, "farmer", Ast.defaultValue(JType.FARMHAND)))))
                .asMethodCreation().asClassCreation(ClassMemberKind.METHOD));
        if (unlocks.contains(Unlock.RETURN)) {
            String getter = free(new String[]{"get", "count", "measure"}, methodNames);
            out.add(command("+ int " + getter + "();",
                    e -> e.addInstanceMethod(new MethodDecl(getter, JType.INT)))
                    .asMethodCreation().asClassCreation(ClassMemberKind.METHOD));
        }
    }

    private static BlockSpec overrideCreation(MethodDecl signature) {
        StringJoiner label = new StringJoiner(", ", "+ @Override " + signature.name + "(", ")");
        for (VarDecl parameter : signature.parameters) label.add(parameter.type.javaName + " " + parameter.name);
        return command(label.toString(), e -> e.addOverride(signature))
                .asMethodCreation().asClassCreation(ClassMemberKind.METHOD);
    }

    private static void interfaceImplementations(ClassDecl cls, Block program, List<MethodDecl> overrideCandidates,
                                                 List<BlockSpec> out) {
        if (cls.interfaceName != null) {
            ClassDecl contract = new ClassTable(program).findClass(cls.interfaceName);
            if (contract != null && contract.isInterface) overrideCandidates.addAll(contract.methods());
            out.add(command("− implements " + cls.interfaceName, e -> e.mutate(() -> cls.interfaceName = null)));
            return;
        }
        for (ClassDecl contract : program.classes()) {
            if (contract.isInterface) {
                out.add(command("+ implements " + contract.name, e -> e.mutate(() -> cls.interfaceName = contract.name)));
            }
        }
    }

    static void statements(EditorCursor cursor, Set<String> unlocks, Block program, List<BlockSpec> out) {
        if (!unlocks.contains(Unlock.CLASSES)) return;
        List<VarDecl> visible = ScopeResolver.visibleAt(cursor.block, cursor.index);
        List<BlockSpec> choices = new ArrayList<>();
        members(cursor, unlocks, program, choices);
        ClassTable table = new ClassTable(program);
        Set<String> locals = PaletteProvider.namesInMethod(cursor.block, program);
        boolean objectMethods = unlocks.contains(Unlock.OBJECT_METHODS);
        boolean objectReferences = objectMethods || unlocks.contains(Unlock.INSTANCE_OF);
        for (ClassDecl cls : program.classes()) {
            if (cls.isInterface) continue;
            String variable = variableName(cls.type(), locals);
            choices.add(code(cls.name + " " + variable + " = " + newText(cls, visible, cursor.classDecl) + ";",
                    e -> e.insert(new VarDecl(JType.reference(cls.name), variable, construct(cls, visible, cursor.classDecl)))));
            if (objectReferences) {
                String object = variableName(JType.OBJECT, locals);
                choices.add(code("Object " + object + " = " + newText(cls, visible, cursor.classDecl) + ";",
                        e -> e.insert(new VarDecl(JType.OBJECT, object, construct(cls, visible, cursor.classDecl)))));
            }
            for (ClassDecl base : program.classes()) {
                String key = base.isInterface ? Unlock.INTERFACES : Unlock.INHERITANCE;
                if (!unlocks.contains(key) || base == cls || !table.assignable(cls.type(), base.type())) continue;
                String baseVariable = variableName(base.type(), locals);
                choices.add(code(base.name + " " + baseVariable + " = " + newText(cls, visible, cursor.classDecl) + ";",
                        e -> e.insert(new VarDecl(base.type(), baseVariable, construct(cls, visible, cursor.classDecl)))));
            }
        }
        if (objectReferences) {
            String name = variableName(JType.OBJECT, locals);
            for (VarDecl existing : visible) {
                if (existing.type != JType.OBJECT && !existing.type.isClass()) continue;
                if (existing.parent instanceof ClassDecl) continue;
                choices.add(code("Object " + name + " = " + existing.name + ";",
                        e -> e.insert(new VarDecl(JType.OBJECT, name, new VarRef(existing.name)))));
            }
            choices.add(code("Object " + name + " = new Object();", e -> e.insert(new VarDecl(JType.OBJECT,
                    name, new NewObject(JType.OBJECT.javaName, new JType[0])))));
        }
        if (unlocks.contains(Unlock.CONSTANTS)) {
            String name = free(new String[]{"stops", "limit", "width"}, locals);
            for (ClassDecl owner : program.classes()) {
                for (VarDecl field : owner.fields()) {
                    if (!field.isStatic) continue;
                    choices.add(code("final int " + name + " = " + owner.name + "." + field.name + ";", e ->
                            e.insert(new VarDecl(JType.INT, name, new StaticConstant(owner.name, field.name), true, false))));
                }
            }
            choices.add(code("final int " + name + " = " + DEFAULT_STOPS + ";", e -> e.insert(new VarDecl(
                    JType.INT, name, Ast.lit(DEFAULT_STOPS), true, false))));
        }
        if (unlocks.contains(Unlock.OBJECT_ARRAYS)) arrayStatements(cursor, program, visible, locals, table, choices);
        for (Target target : targets(cursor.classDecl, visible)) {
            for (VarDecl field : table.fields(target.type)) {
                if (field.isFinal || field.isStatic) continue;
                if (field.type == JType.INT) {
                    choices.add(code(target.label + "." + field.name + " = " + target.label + "." + field.name + " + 1;",
                            e -> e.insert(new FieldAssign(access(target, field), new Binary(Binary.Op.ADD, access(target, field), Ast.lit(1))))));
                }
                Expr initial = argument(field.type, visible, cursor.classDecl);
                choices.add(code(target.label + "." + field.name + " = " + exprText(field.type, initial) + ";",
                        e -> e.insert(new FieldAssign(access(target, field), argument(field.type, visible, cursor.classDecl)))));
            }
            if (unlocks.contains(Unlock.INSTANCE_METHODS)) {
                for (MethodDecl method : table.methods(target.type)) {
                    InstanceCall call = call(target, method, visible, cursor.classDecl);
                    choices.add(code(exprText(method.returnType, call) + ";",
                            e -> e.insert(new ExprStmt(call(target, method, visible, cursor.classDecl)))).asMethodCall());
                }
            }
            if (objectMethods && target.type.isReference()) {
                for (String name : OBJECT_METHOD_NAMES) {
                    if (table.method(target.type, name) != null) continue;
                    MethodDecl method = ObjectMethods.signature(name);
                    String result = free(new String[]{name.equals("equals") ? "same" : name.equals("hashCode") ? "hash"
                            : name.equals("toString") ? "description" : "type"}, locals);
                    Expr preview = objectCall(target, method, visible);
                    choices.add(code(method.returnType.javaName + " " + result + " = " + exprText(method.returnType, preview) + ";",
                            e -> e.insert(new VarDecl(method.returnType, result, objectCall(target, method, visible))))
                            .asMethodCall());
                }
            }
        }
        ClassDecl cls = cursor.classDecl;
        ConstructorDecl constructor = enclosingConstructor(cursor.block);
        if (constructor != null) {
            Set<String> parameters = new HashSet<>();
            for (VarDecl p : constructor.parameters) parameters.add(p.name);
            String name = free(cls != null && cls.name.equals("Counter") ? new String[]{"start", "value"}
                    : new String[]{"row", "width", "start"}, parameters);
            choices.add(command("+ int " + name + BlockSpec.PARAMETER_SUFFIX,
                    e -> e.addConstructorParameter(Ast.declare(JType.INT, name))));
            if (unlocks.contains(Unlock.INSTANCE_METHODS) && !parameters.contains("farmer")) {
                choices.add(command("+ Farmhand farmer" + BlockSpec.PARAMETER_SUFFIX,
                        e -> e.addConstructorParameter(new VarDecl(JType.FARMHAND, "farmer", new Literal(JType.FARMHAND, null)))));
            }
            for (VarDecl parameter : constructor.parameters) {
                choices.add(command("− " + parameter.type.javaName + " " + parameter.name + BlockSpec.PARAMETER_SUFFIX,
                        e -> e.mutate(() -> constructor.removeParameter(parameter))));
            }
            if (cls != null && cls.superName != null && unlocks.contains(Unlock.INHERITANCE)
                    && cursor.index == 0 && (constructor.body.size() == 0 || !(constructor.body.get(0) instanceof SuperCall))) {
                ClassDecl base = table.findClass(cls.superName);
                if (base != null) {
                    ConstructorDecl superConstructor = base.constructor();
                    JType[] types = types(superConstructor == null ? new ArrayList<>() : superConstructor.parameters);
                    choices.add(code("super(" + argumentText(types, visible, cls) + ");", e ->
                            e.insert(new SuperCall(types, arguments(types, visible, cls)))));
                }
            }
        }
        MethodDecl currentMethod = enclosingMethod(cursor.block);
        if (cls != null && unlocks.contains(Unlock.INSTANCE_METHODS)) {
            if (currentMethod != null && (currentMethod.override || supportsOverride(cls, currentMethod, table, unlocks))) {
                choices.add(command(currentMethod.override ? "− @Override" : "+ @Override",
                        e -> e.mutate(() -> currentMethod.override = !currentMethod.override)));
            }
            if (currentMethod != null && currentMethod.returnType != JType.VOID) {
                JType type = currentMethod.returnType;
                choices.add(code("return " + exprText(type, Ast.defaultValue(type)) + ";",
                        e -> e.insert(new ReturnStmt(type, Ast.defaultValue(type)))));
            } else choices.add(code("return;", e -> e.insert(new ReturnStmt())));
        }
        for (BlockSpec choice : choices) out.add(choice.inTab(BlockSpec.TAB_OBJECTS));
        if (cls != null) nativeStatements(cursor, unlocks, visible, program, out);
    }

    private static boolean supportsOverride(ClassDecl cls, MethodDecl method, ClassTable table, Set<String> unlocks) {
        if (cls.superName != null && unlocks.contains(Unlock.INHERITANCE)
                && matches(table.method(JType.reference(cls.superName), method.name), method)) return true;
        if (!unlocks.contains(Unlock.INTERFACES)) return false;
        for (ClassDecl base : table.lineage(cls.type())) {
            if (base.interfaceName != null && matches(table.method(JType.reference(base.interfaceName), method.name), method)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(MethodDecl signature, MethodDecl method) {
        return signature != null && signature.returnType == method.returnType && signature.sameSignature(method);
    }

    private static void arrayStatements(EditorCursor cursor, Block program, List<VarDecl> visible,
                                        Set<String> locals, ClassTable table, List<BlockSpec> out) {
        for (ClassDecl declaration : program.classes()) {
            JType element = declaration.type();
            JType array = JType.objectArray(element);
            String name = free(new String[]{decapitalize(element.javaName) + "s"}, locals);
            out.add(code(array.javaName + " " + name + " = " + newArrayText(element) + ";",
                    e -> e.insert(new VarDecl(array, name, newArray(element)))));
        }
        for (VarDecl variable : visible) {
            JType element = variable.type.elementType;
            if (element == null) continue;
            String name = variableName(element, locals);
            String index = arrayIndex(visible);
            String target = variable.name + "[" + (index == null ? "0" : index) + "]";
            out.add(code(element.javaName + " " + name + " = " + target + ";",
                    e -> e.insert(new VarDecl(element, name, arrayGet(variable, index)))));
            boolean hasConcreteChoice = false;
            for (ClassDecl declaration : program.classes()) {
                if (declaration.isInterface || !table.assignable(declaration.type(), element)) continue;
                hasConcreteChoice = true;
                String value = newText(declaration, visible, cursor.classDecl);
                out.add(code(target + " = " + value + ";", e -> e.insert(new ObjectArrayAssign(variable.name,
                        element, indexExpr(index), construct(declaration, visible, cursor.classDecl)))));
            }
            if (!hasConcreteChoice) {
                out.add(code(target + " = null;", e -> e.insert(new ObjectArrayAssign(variable.name, element,
                        indexExpr(index), Ast.defaultValue(element)))));
            }
        }
    }

    private static String newArrayText(JType element) {
        return "new " + element.javaName + "[" + DEFAULT_OBJECT_ARRAY_LENGTH + "]";
    }

    private static NewObjectArray newArray(JType element) {
        return new NewObjectArray(element, Ast.lit(DEFAULT_OBJECT_ARRAY_LENGTH));
    }

    private static String arrayIndex(List<VarDecl> visible) {
        for (VarDecl variable : visible) {
            if (variable.type == JType.INT && variable.parent instanceof ForStmt) return variable.name;
        }
        return null;
    }

    private static Expr indexExpr(String index) { return index == null ? Ast.lit(0) : new VarRef(index); }

    private static ObjectArrayGet arrayGet(VarDecl variable, String index) {
        return new ObjectArrayGet(variable.name, variable.type.elementType, indexExpr(index));
    }

    static void slots(ExprSlot slot, Set<String> unlocks, Block program, List<BlockSpec> out) {
        if (!unlocks.contains(Unlock.CLASSES) || slot.constantOnly) return;
        ClassDecl cls = ClassTable.classOf(slot.owner);
        List<VarDecl> visible = ScopeResolver.visibleAt(slot);
        ClassTable table = new ClassTable(program);
        for (Target target : targets(cls, visible)) {
            for (VarDecl field : table.fields(target.type)) {
                if (!field.isStatic && fits(slot, field.type, table)) {
                    out.add(code(target.label + "." + field.name,
                            e -> e.mutate(() -> slot.set(access(target, field)))));
                }
            }
            if (unlocks.contains(Unlock.INSTANCE_METHODS)) {
                for (MethodDecl method : table.methods(target.type)) {
                    if (method.returnType != JType.VOID && fits(slot, method.returnType, table)) {
                        out.add(code(exprText(method.returnType, call(target, method, visible, cls)), e ->
                                e.mutate(() -> slot.set(call(target, method, visible, cls)))).asMethodCall());
                    }
                }
            }
            if (unlocks.contains(Unlock.OBJECT_METHODS) && target.type.isReference()) {
                for (String name : OBJECT_METHOD_NAMES) {
                    if (table.method(target.type, name) != null) continue;
                    MethodDecl method = ObjectMethods.signature(name);
                    if (!fits(slot, method.returnType, table)) continue;
                    Expr preview = objectCall(target, method, visible);
                    out.add(code(exprText(method.returnType, preview), e ->
                            e.mutate(() -> slot.set(objectCall(target, method, visible)))).asMethodCall());
                }
            }
            if (unlocks.contains(Unlock.INSTANCE_OF) && slot.type == JType.BOOLEAN && target.type.isReference()) {
                for (ClassDecl test : program.classes()) {
                    if (!table.instanceOfConvertible(target.type, test.type())) continue;
                    out.add(code(target.label + " instanceof " + test.name, e ->
                            e.mutate(() -> slot.set(new InstanceOf(target.expression.get(), test.type())))));
                }
            }
        }
        if (slot.type == JType.INT && unlocks.contains(Unlock.CONSTANTS)) {
            for (ClassDecl owner : program.classes()) {
                for (VarDecl field : owner.fields()) {
                    if (field.isStatic) out.add(code(owner.name + "." + field.name, e ->
                            e.mutate(() -> slot.set(new StaticConstant(owner.name, field.name)))));
                }
            }
        }
        for (VarDecl variable : visible) {
            boolean namedReference = slot.type.isClass() || slot.type.elementType != null
                    || slot.type == JType.OBJECT || slot.type == JType.CLASS_TOKEN;
            if (fits(slot, variable.type, table) && (namedReference || variable.type != slot.type)) {
                out.add(code(variable.name, e -> e.mutate(() -> slot.set(new VarRef(variable.name)))));
            }
        }
        if (cls != null && fits(slot, cls.type(), table)) {
            out.add(code("this", e -> e.mutate(() -> slot.set(new ThisRef()))));
        }
        if (slot.type == JType.FARMHAND && cls == null) {
            out.add(code("farmer", e -> e.mutate(() -> slot.set(new FarmhandRef()))));
        }
        for (ClassDecl candidate : program.classes()) {
            if (!candidate.isInterface && fits(slot, candidate.type(), table)) {
                out.add(code(newText(candidate, visible, cls), e -> e.mutate(() -> slot.set(construct(candidate, visible, cls)))));
            }
        }
        if ((slot.type.isClass() && unlocks.contains(Unlock.INTERFACES))
                || slot.type.elementType != null && unlocks.contains(Unlock.OBJECT_ARRAYS) || slot.type == JType.OBJECT) {
            out.add(code("null", e -> e.mutate(() -> slot.set(Ast.defaultValue(slot.type)))));
        }
        if (slot.type == JType.OBJECT && unlocks.contains(Unlock.OBJECT_METHODS)) {
            out.add(code("new Object()", e -> e.mutate(() -> slot.set(new NewObject(JType.OBJECT.javaName, new JType[0])))));
        }
        if (unlocks.contains(Unlock.OBJECT_ARRAYS)) arraySlots(slot, visible, table, out);
        if (slot.type == JType.BOOLEAN && cls != null) {
            for (Target farmer : farmhandTargets(cls, visible)) {
                if (unlocks.contains(Unlock.IS_RIPE)) nativeSlot(slot, farmer, "isRipe", out);
                if (unlocks.contains(Unlock.IS_DRY)) nativeSlot(slot, farmer, "isDry", out);
                if (unlocks.contains(Unlock.CAN_MOVE)) {
                    out.add(code(farmer.label + ".canMove(Direction.EAST)", e -> e.mutate(() -> {
                        slot.set(nativeCall(farmer, "canMove", new Literal(JType.DIRECTION,
                                com.ccs.coffee_n_crops.farm.Direction.EAST)));
                    })));
                }
            }
        }
        if (cls != null) for (Target farmer : farmhandTargets(cls, visible)) {
            if (slot.type == JType.DOUBLE && unlocks.contains(Unlock.TANK)) nativeSlot(slot, farmer, "getWater", out);
            if (slot.type == JType.STRING && unlocks.contains(Unlock.GET_CROP)) nativeSlot(slot, farmer, "getCrop", out);
            if (slot.type == JType.INT_ARRAY && unlocks.contains(Unlock.ARRAY)) nativeSlot(slot, farmer, "scanRow", out);
            if (slot.type == JType.INT_GRID && unlocks.contains(Unlock.GRID)) nativeSlot(slot, farmer, "scanField", out);
        }
    }

    private static void arraySlots(ExprSlot slot, List<VarDecl> visible, ClassTable table, List<BlockSpec> out) {
        if (slot.type.elementType != null) {
            JType element = slot.type.elementType;
            out.add(code(newArrayText(element), e -> e.mutate(() -> slot.set(newArray(element)))));
        }
        String index = arrayIndex(visible);
        for (VarDecl variable : visible) {
            JType element = variable.type.elementType;
            if (element == null) continue;
            if (slot.type == JType.INT) {
                out.add(code(variable.name + ".length", e -> e.mutate(() -> slot.set(new Access(Access.Kind.LENGTH, variable.name)))));
            } else if (fits(slot, element, table)) {
                out.add(code(variable.name + "[" + (index == null ? "0" : index) + "]", e ->
                        e.mutate(() -> slot.set(arrayGet(variable, index)))));
            }
        }
    }

    private static boolean fits(ExprSlot slot, JType type, ClassTable table) {
        if (!table.assignable(type, slot.type)) return false;
        return !(slot.owner instanceof InstanceOf check) || table.instanceOfConvertible(type, check.target);
    }

    private static void nativeSlot(ExprSlot slot, Target target, String name, List<BlockSpec> out) {
        out.add(code(target.label + "." + name + "()", e -> e.mutate(() -> slot.set(nativeCall(target, name)))));
    }

    private static void nativeStatements(EditorCursor cursor, Set<String> unlocks, List<VarDecl> visible,
                                         Block program, List<BlockSpec> out) {
        for (Target farmer : farmhandTargets(cursor.classDecl, visible)) {
            if (unlocks.contains(Unlock.HARVEST)) out.add(code(farmer.label + ".harvest();", e -> e.insert(new ExprStmt(nativeCall(farmer, "harvest")))).inTab(BlockSpec.TAB_FARM));
            if (unlocks.contains(Unlock.WATER)) out.add(code(farmer.label + ".water();", e -> e.insert(new ExprStmt(nativeCall(farmer, "water")))).inTab(BlockSpec.TAB_FARM));
            if (unlocks.contains(Unlock.FERTILIZE)) out.add(code(farmer.label + ".fertilize();", e -> e.insert(new ExprStmt(nativeCall(farmer, "fertilize")))).inTab(BlockSpec.TAB_FARM));
            if (unlocks.contains(Unlock.WAIT)) out.add(code(farmer.label + ".waitOneTick();", e -> e.insert(new ExprStmt(nativeCall(farmer, "waitOneTick")))).inTab(BlockSpec.TAB_FARM));
            if (unlocks.contains(Unlock.MOVE)) out.add(code(farmer.label + ".move(Direction.EAST);", e -> e.insert(new ExprStmt(nativeCall(farmer, "move", new Literal(JType.DIRECTION, com.ccs.coffee_n_crops.farm.Direction.EAST))))).inTab(BlockSpec.TAB_FARM));
            if (unlocks.contains(Unlock.WALK_TO)) {
                String x = "0";
                for (VarDecl variable : visible) if (variable.type == JType.INT && !(variable.parent instanceof ClassDecl)) { x = variable.name; break; }
                String column = x;
                VarDecl row = new ClassTable(program).field(JType.reference(cursor.classDecl.name), "row");
                String y = row == null ? "0" : "this.row";
                out.add(code(farmer.label + ".walkTo(" + column + ", " + y + ");", e -> e.insert(new ExprStmt(nativeCall(farmer, "walkTo",
                        column.equals("0") ? Ast.lit(0) : new VarRef(column), row == null ? Ast.lit(0)
                                : new FieldAccess(new ThisRef(), JType.reference(cursor.classDecl.name), "row", JType.INT))))).inTab(BlockSpec.TAB_FARM));
            }
        }
    }

    private static InstanceCall nativeCall(Target target, String name, Expr... args) {
        NativeRegistry.Entry entry = NativeRegistry.STANDARD.lookup("farmer", name);
        return new InstanceCall(target.expression.get(), JType.FARMHAND, name, entry.returnType, entry.params, args);
    }

    private static InstanceCall call(Target target, MethodDecl method, List<VarDecl> visible, ClassDecl cls) {
        return new InstanceCall(target.expression.get(), target.type, method, arguments(types(method.parameters), visible, cls));
    }

    private static InstanceCall objectCall(Target target, MethodDecl method, List<VarDecl> visible) {
        if (!method.name.equals("equals")) return new InstanceCall(target.expression.get(), target.type, method);
        for (VarDecl other : visible) {
            if (!other.type.isReference() || other.name.equals(target.label) || other.parent instanceof ClassDecl) continue;
            return new InstanceCall(target.expression.get(), target.type, method, new VarRef(other.name));
        }
        return new InstanceCall(target.expression.get(), target.type, method, Ast.defaultValue(JType.OBJECT));
    }

    private static NewObject construct(ClassDecl cls, List<VarDecl> visible, ClassDecl enclosing) {
        ConstructorDecl constructor = cls.constructor();
        return new NewObject(cls, arguments(types(constructor == null ? new ArrayList<>() : constructor.parameters), visible, enclosing));
    }

    private static String newText(ClassDecl cls, List<VarDecl> visible, ClassDecl enclosing) {
        return exprText(JType.reference(cls.name), construct(cls, visible, enclosing));
    }

    private static String exprText(JType type, Expr expression) {
        if (type == JType.VOID) {
            String line = JavaPrinter.text(JavaPrinter.line(new ExprStmt(expression)));
            return line.substring(0, line.length() - 1);
        }
        String line = JavaPrinter.text(JavaPrinter.line(new VarDecl(type, "result", expression)));
        return line.substring(line.indexOf(" = ") + 3, line.length() - 1);
    }

    private static JType[] types(List<VarDecl> parameters) {
        JType[] types = new JType[parameters.size()];
        for (int i = 0; i < types.length; i++) types[i] = parameters.get(i).type;
        return types;
    }

    private static Expr[] arguments(JType[] types, List<VarDecl> visible, ClassDecl cls) {
        Expr[] args = new Expr[types.length];
        for (int i = 0; i < args.length; i++) args[i] = argument(types[i], visible, cls);
        return args;
    }

    private static String argumentText(JType[] types, List<VarDecl> visible, ClassDecl cls) {
        StringBuilder text = new StringBuilder();
        for (JType type : types) {
            if (text.length() > 0) text.append(", ");
            text.append(exprText(type, argument(type, visible, cls)));
        }
        return text.toString();
    }

    private static Expr argument(JType type, List<VarDecl> visible, ClassDecl cls) {
        for (VarDecl variable : visible) if (variable.type == type && !(variable.parent instanceof ClassDecl)) return new VarRef(variable.name);
        if (type == JType.FARMHAND && cls == null) return new FarmhandRef();
        if (type == JType.INT) return Ast.lit(0);
        if (type == JType.BOOLEAN) return Ast.lit(false);
        if (type == JType.DOUBLE) return Ast.lit(0.0);
        return new Literal(type, null);
    }

    private static List<Target> targets(ClassDecl cls, List<VarDecl> visible) {
        List<Target> out = new ArrayList<>();
        if (cls != null) out.add(new Target("this", JType.reference(cls.name), ThisRef::new));
        for (VarDecl variable : visible) {
            if (variable.parent instanceof ClassDecl) continue;
            out.add(new Target(variable.name, variable.type, () -> new VarRef(variable.name)));
        }
        return out;
    }

    private static List<Target> farmhandTargets(ClassDecl cls, List<VarDecl> visible) {
        List<Target> out = new ArrayList<>();
        Set<String> labels = new HashSet<>();
        for (VarDecl variable : visible) {
            if (variable.type != JType.FARMHAND) continue;
            if (variable.parent instanceof ClassDecl && cls != null) {
                if (labels.add("this." + variable.name)) out.add(new Target("this." + variable.name, JType.FARMHAND,
                        () -> new FieldAccess(new ThisRef(), JType.reference(cls.name), variable.name, JType.FARMHAND)));
            } else if (labels.add(variable.name)) out.add(new Target(variable.name, JType.FARMHAND, () -> new VarRef(variable.name)));
        }
        return out;
    }

    private static FieldAccess access(Target target, VarDecl field) {
        return new FieldAccess(target.expression.get(), target.type, field.name, field.type);
    }

    private static ConstructorDecl enclosingConstructor(Node node) {
        for (Node current = node; current != null; current = current.parent) if (current instanceof ConstructorDecl) return (ConstructorDecl) current;
        return null;
    }

    private static MethodDecl enclosingMethod(Node node) {
        for (Node current = node; current != null; current = current.parent) if (current instanceof MethodDecl) return (MethodDecl) current;
        return null;
    }

    private static String decapitalize(String text) { return Character.toLowerCase(text.charAt(0)) + text.substring(1); }

    static String variableName(JType type, Set<String> taken) {
        String name = decapitalize(type.javaName);
        return free(new String[]{Validator.validIdentifier(name) ? name : name + "Object"}, taken);
    }

    private static String free(String[] pool, Set<String> taken) {
        for (String candidate : pool) if (!taken.contains(candidate)) return candidate;
        for (int i = 2; ; i++) if (!taken.contains(pool[0] + i)) return pool[0] + i;
    }

    private static BlockSpec code(String label, BlockSpec.Action action) { return new BlockSpec(label, false, action); }
    private static BlockSpec command(String label, BlockSpec.Action action) { return new BlockSpec(label, true, action); }

    private static final class Target {
        final String label;
        final JType type;
        final Supplier<Expr> expression;
        Target(String label, JType type, Supplier<Expr> expression) { this.label = label; this.type = type; this.expression = expression; }
    }
}
