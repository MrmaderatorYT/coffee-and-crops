package com.ccs.coffee_n_crops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.farm.WorldClock;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.ClassDecl;
import com.ccs.coffee_n_crops.lang.ast.MethodDecl;
import com.ccs.coffee_n_crops.lang.ast.expr.InstanceCall;
import com.ccs.coffee_n_crops.lang.ast.expr.NewObject;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.ExprStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ReturnStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.check.Validator;
import com.ccs.coffee_n_crops.lang.interp.DebugController;
import com.ccs.coffee_n_crops.lang.interp.ExecListener;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.print.JavaPrinter;
import com.ccs.coffee_n_crops.lang.runtime.NativeRegistry;
import com.ccs.coffee_n_crops.lang.serial.AstJson;
import com.ccs.coffee_n_crops.lang.types.JType;

import org.json.JSONObject;
import org.junit.Test;

/** Interface contracts, static member lookup, and dispatch through the runtime receiver. */
public final class InterfaceLanguageTest {
    @Test(timeout = 2000)
    public void inheritedImplementationAndOverrideDispatchThroughSavedInterfaceReference() throws Exception {
        Block program = new Block();
        ClassDecl tool = new ClassDecl("Tool", true);
        MethodDecl signature = new MethodDecl("work", JType.INT, Ast.declare(JType.INT, "amount"));
        tool.addMethod(signature);
        program.addClass(tool);

        ClassDecl base = new ClassDecl("Base");
        MethodDecl method = new MethodDecl("work", JType.INT, Ast.declare(JType.INT, "amount"));
        method.body.add(new ReturnStmt(JType.INT, new VarRef("amount")));
        base.addMethod(method);
        program.addClass(base);

        ClassDecl worker = new ClassDecl("Worker", base.name);
        worker.interfaceName = tool.name;
        program.addClass(worker);
        ClassDecl specialist = new ClassDecl("Specialist", worker.name);
        MethodDecl override = new MethodDecl("work", JType.INT, Ast.declare(JType.INT, "amount"));
        override.override = true;
        override.body.add(new ReturnStmt(JType.INT, Ast.lit(8)));
        specialist.addMethod(override);
        program.addClass(specialist);

        program.add(new VarDecl(tool.type(), "tool", new NewObject(worker)));
        program.add(new VarDecl(JType.INT, "first", new InstanceCall(new VarRef("tool"), tool.type(),
                signature, Ast.lit(3))));
        program.add(new VarDecl(tool.type(), "another", new NewObject(specialist)));
        program.add(new VarDecl(JType.INT, "second", new InstanceCall(new VarRef("another"), tool.type(),
                signature, Ast.lit(3))));

        Block restored = AstJson.blockFrom(new JSONObject(AstJson.toJson(program).toString()));
        String source = JavaPrinter.toSource(restored);
        assertEquals(JavaPrinter.toSource(program), source);
        assertTrue(source.contains("interface Tool {\n    int work(int amount);\n}"));
        assertTrue(source.contains("class Worker extends Base implements Tool {"));
        assertTrue(source.contains("class Base {\n    public int work(int amount) {"));
        assertNull(Validator.check(restored));
        ExecutionContext context = run(restored);
        assertEquals(3, context.readVariable("first").asInt());
        assertEquals(8, context.readVariable("second").asInt());
    }

    @Test
    public void rejectsInstantiationOfAnInterface() {
        Block program = new Block();
        ClassDecl tool = new ClassDecl("Tool", true);
        program.addClass(tool);
        program.add(new VarDecl(tool.type(), "tool", new NewObject(tool)));
        assertEquals("Tool is abstract; cannot be instantiated", Validator.check(program).message);
    }

    @Test
    public void enforcesImplementationsAndCompatibleReturnTypes() {
        Block program = new Block();
        ClassDecl tool = new ClassDecl("Tool", true);
        tool.addMethod(new MethodDecl("work", JType.VOID));
        program.addClass(tool);
        ClassDecl worker = new ClassDecl("Worker");
        worker.interfaceName = tool.name;
        program.addClass(worker);
        assertEquals("Worker is not abstract and does not override abstract method work() in Tool",
                Validator.check(program).message);

        MethodDecl wrongResult = new MethodDecl("work", JType.INT);
        wrongResult.body.add(new ReturnStmt(JType.INT, Ast.lit(0)));
        worker.addMethod(wrongResult);
        assertEquals("return type int is not compatible with void", Validator.check(program).message);
    }

    @Test
    public void interfaceVariableCannotSeeAdditionalConcreteMethods() {
        Block program = new Block();
        ClassDecl tool = new ClassDecl("Tool", true);
        ClassDecl worker = new ClassDecl("Worker");
        worker.interfaceName = tool.name;
        MethodDecl extra = new MethodDecl("extra", JType.VOID);
        worker.addMethod(extra);
        program.addClass(tool);
        program.addClass(worker);
        program.add(new VarDecl(tool.type(), "tool", new NewObject(worker)));
        program.add(new ExprStmt(new InstanceCall(new VarRef("tool"), tool.type(), extra)));
        assertEquals("cannot find symbol: method extra()", Validator.check(program).message);
    }

    private static ExecutionContext run(Block program) throws InterruptedException {
        FarmWorld world = new FarmWorld(new String[]{"."}, 0, 0);
        ExecListener listener = new ExecListener() {
            @Override public void onNodeEnter(int farmerId, int nodeId) { }
            @Override public void onFinished(int farmerId) { }
            @Override public void onError(int farmerId, int nodeId, String message) {
                throw new AssertionError(message);
            }
        };
        ExecutionContext context = new ExecutionContext(world.farmer, world, new DebugController(),
                new WorldClock(0), NativeRegistry.STANDARD, listener);
        program.exec(context);
        return context;
    }
}
