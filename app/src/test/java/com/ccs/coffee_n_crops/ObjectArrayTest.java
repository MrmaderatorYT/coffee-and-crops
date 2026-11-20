package com.ccs.coffee_n_crops;

import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.farm.WorldClock;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.ClassDecl;
import com.ccs.coffee_n_crops.lang.ast.MethodDecl;
import com.ccs.coffee_n_crops.lang.ast.expr.Access;
import com.ccs.coffee_n_crops.lang.ast.expr.InstanceCall;
import com.ccs.coffee_n_crops.lang.ast.expr.NewObject;
import com.ccs.coffee_n_crops.lang.ast.expr.NewObjectArray;
import com.ccs.coffee_n_crops.lang.ast.expr.ObjectArrayGet;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.ExprStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForEachStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ObjectArrayAssign;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.check.Validator;
import com.ccs.coffee_n_crops.lang.interp.ExecListener;
import com.ccs.coffee_n_crops.lang.interp.Interpreter;
import com.ccs.coffee_n_crops.lang.print.JavaPrinter;
import com.ccs.coffee_n_crops.lang.serial.AstJson;
import com.ccs.coffee_n_crops.lang.types.JType;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Covers typed array operations and Java's live, covariant reference semantics. */
public class ObjectArrayTest {
    private static final JType TOOL = JType.reference("Tool");
    private static final JType TOOLS = JType.objectArray(TOOL);
    private static final MethodDecl SIGNATURE = new MethodDecl("work", JType.VOID);

    @Test
    public void roundTripKeepsLiveAliasedElementsAndStaticIterationType() throws Exception {
        Block program = tools();
        ClassDecl first = program.classes().get(1);
        ClassDecl second = program.classes().get(2);
        program.add(array(TOOL, 2));
        program.add(new VarDecl(TOOLS, "alias", new VarRef("tools")));
        program.add(new ObjectArrayAssign("tools", TOOL, Ast.lit(0), new NewObject(first)));
        program.add(new VarDecl(TOOL, "first", new ObjectArrayGet("alias", TOOL, Ast.lit(0))));
        program.add(new VarDecl(JType.INT, "count", new Access(Access.Kind.LENGTH, "tools")));
        ForEachStmt loop = new ForEachStmt(TOOL, "tool", "tools");
        loop.body.add(new ExprStmt(new InstanceCall(new VarRef("tool"), TOOL, SIGNATURE)));
        // A later iteration reads the element written through the alias on the first iteration.
        loop.body.add(new ObjectArrayAssign("alias", TOOL, Ast.lit(1), new NewObject(second)));
        program.add(loop);

        Block loaded = AstJson.blockFrom(AstJson.toJson(program));
        assertSame(TOOLS, ((VarDecl) loaded.get(0)).type);
        String source = JavaPrinter.toSource(loaded);
        assertTrue(source, source.contains("Tool[] tools = new Tool[2];"));
        assertTrue(source, source.contains("Tool first = alias[0];"));
        assertTrue(source, source.contains("for (Tool tool : tools)"));
        assertNull(Validator.check(loaded));
        FarmWorld world = world();
        assertNull(run(loaded, world));
        assertEquals(1, world.instanceCalls("Harvester", "work"));
        assertEquals(1, world.instanceCalls("Waterer", "work"));
        assertTrue(world.usedLessonNode(loaded.get(5).id));
    }

    @Test
    public void covarianceChecksTheRuntimeComponentWhenStoring() throws Exception {
        Block program = tools();
        ClassDecl first = program.classes().get(1);
        ClassDecl second = program.classes().get(2);
        program.add(array(first.type(), 1));
        program.add(new ObjectArrayAssign("tools", TOOL, Ast.lit(0), new NewObject(first)));
        program.add(new ObjectArrayAssign("tools", TOOL, Ast.lit(0), new NewObject(second)));
        assertNull(Validator.check(program));
        assertEquals("ArrayStoreException: Waterer", run(program, world()));
    }

    @Test
    public void defaultNullElementsAndBadArrayIndexesReportJavaErrors() throws Exception {
        Block program = tools();
        program.add(array(TOOL, 1));
        program.add(new ExprStmt(new InstanceCall(new ObjectArrayGet("tools", TOOL, Ast.lit(0)), TOOL, SIGNATURE)));
        assertNull(Validator.check(program));
        assertEquals("NullPointerException", run(program, world()));

        program.remove(1);
        program.add(new VarDecl(TOOL, "missing", new ObjectArrayGet("tools", TOOL, Ast.lit(1))));
        assertEquals("ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 1", run(program, world()));
        program.remove(0);
        program.add(0, array(TOOL, -1));
        assertEquals("NegativeArraySizeException: -1", run(program, world()));
    }

    @Test
    public void validatorChecksNamedComponentsStoresAndLoopItemTypes() {
        Block program = tools();
        program.add(array(JType.reference("Missing"), 1));
        assertEquals("cannot find symbol: class Missing", Validator.check(program).message);
        program.remove(0);
        program.add(array(TOOL, 1));
        program.add(new ObjectArrayAssign("tools", TOOL, Ast.lit(0), Ast.lit(7)));
        assertEquals("incompatible types: int cannot be converted to Tool", Validator.check(program).message);
        program.remove(1);
        program.add(new ForEachStmt(program.classes().get(1).type(), "tool", "tools"));
        assertEquals("incompatible types: Tool cannot be converted to Harvester", Validator.check(program).message);
    }

    private static VarDecl array(JType allocatedType, int length) {
        return new VarDecl(TOOLS, "tools", new NewObjectArray(allocatedType, Ast.lit(length)));
    }

    private static Block tools() {
        Block program = new Block();
        ClassDecl contract = new ClassDecl(TOOL.javaName, true);
        contract.addMethod(new MethodDecl(SIGNATURE.name, SIGNATURE.returnType));
        program.addClass(contract);
        for (String name : new String[]{"Harvester", "Waterer"}) {
            ClassDecl tool = new ClassDecl(name);
            tool.interfaceName = contract.name;
            tool.addMethod(new MethodDecl(SIGNATURE.name, SIGNATURE.returnType));
            program.addClass(tool);
        }
        return program;
    }

    private static FarmWorld world() {
        return new FarmWorld(new String[]{"W"}, 0, 0);
    }

    private static String run(Block program, FarmWorld world) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        String[] error = new String[1];
        new Interpreter(new WorldClock(0)).start(program, world, new ExecListener() {
            @Override public void onNodeEnter(int farmerId, int nodeId) { }
            @Override public void onFinished(int farmerId) { done.countDown(); }
            @Override public void onError(int farmerId, int nodeId, String message) {
                error[0] = message;
                done.countDown();
            }
        }, false);
        assertTrue("program did not finish", done.await(5, TimeUnit.SECONDS));
        return error[0];
    }
}
