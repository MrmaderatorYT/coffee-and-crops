package com.ccs.coffee_n_crops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.ClassDecl;
import com.ccs.coffee_n_crops.lang.ast.ConstructorDecl;
import com.ccs.coffee_n_crops.lang.ast.MethodDecl;
import com.ccs.coffee_n_crops.lang.ast.expr.FieldAccess;
import com.ccs.coffee_n_crops.lang.ast.expr.InstanceCall;
import com.ccs.coffee_n_crops.lang.ast.expr.Literal;
import com.ccs.coffee_n_crops.lang.ast.expr.NewObject;
import com.ccs.coffee_n_crops.lang.ast.expr.ThisRef;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.ExprStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.FieldAssign;
import com.ccs.coffee_n_crops.lang.ast.stmt.ReturnStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.SuperCall;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.check.Validator;
import com.ccs.coffee_n_crops.lang.print.JavaPrinter;
import com.ccs.coffee_n_crops.lang.serial.AstJson;
import com.ccs.coffee_n_crops.lang.types.JType;

import org.json.JSONObject;
import org.junit.Test;

/** Named reference types, class diagnostics, and backwards-compatible source/save behavior. */
public class OopLanguageTest {
    private static FieldAccess field(ClassDecl owner, String name, JType type) {
        return new FieldAccess(new ThisRef(), owner.type(), name, type);
    }

    private static ClassDecl counter() {
        ClassDecl counter = new ClassDecl("Counter");
        counter.addField(Ast.declare(JType.INT, "value"));
        ConstructorDecl constructor = new ConstructorDecl(Ast.declare(JType.INT, "start"));
        constructor.body.add(new FieldAssign(field(counter, "value", JType.INT), new VarRef("start")));
        counter.setConstructor(constructor);
        MethodDecl get = new MethodDecl("get", JType.INT);
        get.body.add(new ReturnStmt(JType.INT, field(counter, "value", JType.INT)));
        counter.addMethod(get);
        return counter;
    }

    private static Block program(ClassDecl... classes) {
        Block program = new Block();
        for (ClassDecl declaration : classes) program.addClass(declaration);
        return program;
    }

    private static String problem(Block program) {
        Validator.Problem problem = Validator.check(program);
        assertNotNull("expected a compiler diagnostic", problem);
        return problem.message;
    }

    @Test
    public void unrelatedNamedClassesCannotFitTheSameSlot() {
        ClassDecl counter = new ClassDecl("Counter");
        ClassDecl plan = new ClassDecl("RowPlan");
        Block program = program(counter, plan);
        program.add(new VarDecl(counter.type(), "counter", new NewObject(plan)));
        assertEquals("incompatible types: RowPlan cannot be converted to Counter", problem(program));
        assertSame(counter.type(), JType.valueOf(counter.type().name()));
    }

    @Test
    public void constructorParametersCanShadowFieldsIncludingDrone() {
        ClassDecl worker = new ClassDecl("Worker");
        worker.addField(new VarDecl(JType.FARMHAND, "farmer", new Literal(JType.FARMHAND, null)));
        worker.addField(Ast.declare(JType.INT, "row"));
        ConstructorDecl constructor = new ConstructorDecl(
                new VarDecl(JType.FARMHAND, "farmer", new Literal(JType.FARMHAND, null)), Ast.declare(JType.INT, "row"));
        constructor.body.add(new FieldAssign(field(worker, "farmer", JType.FARMHAND), new VarRef("farmer")));
        constructor.body.add(new FieldAssign(field(worker, "row", JType.INT), new VarRef("row")));
        worker.setConstructor(constructor);
        assertNull(Validator.check(program(worker)));
    }

    @Test
    public void classMembersCannotCaptureRunLocals() {
        ClassDecl counter = counter();
        counter.constructor().body.add(new VarDecl(JType.INT, "copy", new VarRef("runLocal")));
        Block program = program(counter);
        program.add(new VarDecl(JType.INT, "runLocal", Ast.lit(3)));
        assertEquals("cannot find symbol: variable runLocal", problem(program));
    }

    @Test
    public void missingClassesFieldsAndMethodsAreDiagnosedBeforeRunning() {
        Block missing = new Block();
        missing.add(new VarDecl(JType.reference("Missing"), "item", new NewObject("Missing", new JType[0])));
        assertEquals("cannot find symbol: class Missing", problem(missing));

        ClassDecl counter = counter();
        Block badField = program(counter);
        badField.add(new VarDecl(counter.type(), "counter", new NewObject(counter, Ast.lit(0))));
        badField.add(new VarDecl(JType.INT, "value", new FieldAccess(new VarRef("counter"), counter.type(), "gone", JType.INT)));
        assertEquals("cannot find symbol: variable gone", problem(badField));
        badField.remove(1);
        badField.add(new ExprStmt(new InstanceCall(new VarRef("counter"), counter.type(), "gone", JType.VOID, new JType[0])));
        assertEquals("cannot find symbol: method gone()", problem(badField));
    }

    @Test
    public void constructorArgumentsAndImplicitSuperAreChecked() {
        ClassDecl counter = counter();
        Block missingArgument = program(counter);
        missingArgument.add(new VarDecl(counter.type(), "counter", new NewObject(counter)));
        assertTrue(problem(missingArgument).startsWith("constructor Counter cannot be applied to given types"));

        ClassDecl child = new ClassDecl("Child", "Counter");
        Block missingSuper = program(counter, child);
        assertTrue(problem(missingSuper).startsWith("constructor Counter cannot be applied to given types"));
        ConstructorDecl constructor = new ConstructorDecl();
        constructor.body.add(new SuperCall(counter.constructor(), Ast.lit(0)));
        child.setConstructor(constructor);
        assertNull(Validator.check(missingSuper));
    }

    @Test
    public void explicitSuperMustBeFirstAndCannotReadThis() {
        ClassDecl counter = counter();
        ClassDecl child = new ClassDecl("Child", "Counter");
        ConstructorDecl constructor = new ConstructorDecl();
        constructor.body.add(new VarDecl(JType.INT, "temporary", Ast.lit(0)));
        SuperCall call = new SuperCall(counter.constructor(), Ast.lit(0));
        constructor.body.add(call);
        child.setConstructor(constructor);
        Block program = program(counter, child);
        assertEquals("call to super must be first statement in constructor", problem(program));

        constructor.body.remove(0);
        call.args.get(0).set(field(child, "value", JType.INT));
        assertEquals("cannot reference this before supertype constructor has been called", problem(program));
    }

    @Test
    public void duplicateClassesAndInheritanceCyclesHighlightTheClassDeclaration() {
        ClassDecl first = new ClassDecl("Counter");
        ClassDecl second = new ClassDecl("Counter");
        Validator.Problem duplicate = Validator.check(program(first, second));
        assertSame(second, duplicate.node);
        assertEquals("duplicate class: Counter", duplicate.message);

        ClassDecl left = new ClassDecl("Left", "Right");
        ClassDecl right = new ClassDecl("Right", "Left");
        assertEquals("cyclic inheritance involving Left", problem(program(left, right)));
        assertEquals("duplicate class: Farmhand", problem(program(new ClassDecl("Farmhand"))));
    }

    @Test
    public void overrideMustMatchAnInheritedSignatureAndReturnType() {
        ClassDecl parent = counter();
        ClassDecl child = new ClassDecl("Child", "Counter");
        ConstructorDecl constructor = new ConstructorDecl();
        constructor.body.add(new SuperCall(parent.constructor(), Ast.lit(0)));
        child.setConstructor(constructor);
        MethodDecl wrong = new MethodDecl("get", JType.BOOLEAN);
        wrong.override = true;
        wrong.body.add(new ReturnStmt(JType.BOOLEAN, Ast.lit(true)));
        child.addMethod(wrong);
        Block program = program(parent, child);
        assertEquals("return type boolean is not compatible with int", problem(program));
        child.removeMethod(wrong);
        MethodDecl absent = new MethodDecl("missing", JType.VOID);
        absent.override = true;
        child.addMethod(absent);
        assertEquals("method does not override or implement a method from a supertype", problem(program));
    }

    @Test
    public void objectSavesKeepNullFieldsExactParametersAndOverrideFlags() throws Exception {
        ClassDecl counter = counter();
        ClassDecl child = new ClassDecl("Child", "Counter");
        ConstructorDecl constructor = new ConstructorDecl(Ast.declare(JType.INT, "start"));
        constructor.body.add(new SuperCall(counter.constructor(), new VarRef("start")));
        child.setConstructor(constructor);
        MethodDecl get = new MethodDecl("get", JType.INT);
        get.override = true;
        get.body.add(new ReturnStmt(JType.INT, field(child, "value", JType.INT)));
        child.addMethod(get);
        child.addField(new VarDecl(counter.type(), "other", new Literal(counter.type(), null)));
        Block program = program(counter, child);
        program.add(new VarDecl(counter.type(), "counter", new NewObject(child, Ast.lit(2))));
        program.add(new VarDecl(JType.INT, "result", new InstanceCall(new VarRef("counter"), counter.type(), "get", JType.INT, new JType[0])));
        assertNull(Validator.check(program));
        Block loaded = AstJson.blockFrom(new JSONObject(AstJson.toJson(program).toString()));
        assertNull(Validator.check(loaded));
        assertEquals(JavaPrinter.toSource(program), JavaPrinter.toSource(loaded));
        String source = JavaPrinter.toSource(loaded);
        assertTrue(source.startsWith("class Program {\n"));
        assertTrue(source.contains("Counter(int start) {"));
        assertTrue(source.contains("int get() {"));
        assertTrue(source.contains("@Override\n"));
        assertTrue(source.contains("super(start);"));
        assertTrue(source.contains("Counter other = null;"));
        assertTrue(source.contains("counter.get();"));
        assertTrue(!source.contains("int get(Farmhand"));
    }

    @Test
    public void fieldInitializersFollowJavaForwardReferenceRules() {
        ClassDecl forward = new ClassDecl("Forward");
        forward.addField(new VarDecl(JType.INT, "first", new VarRef("second")));
        forward.addField(new VarDecl(JType.INT, "second", Ast.lit(2)));
        Block program = program(forward);
        assertEquals("illegal forward reference", problem(program));
        forward.fields().get(0).init.set(field(forward, "second", JType.INT));
        assertNull(Validator.check(program));
    }
}
