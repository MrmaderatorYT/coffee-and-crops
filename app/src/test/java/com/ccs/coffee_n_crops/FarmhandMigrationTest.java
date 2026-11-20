package com.ccs.coffee_n_crops;

import com.ccs.coffee_n_crops.farm.Direction;
import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.farm.Farmhand;
import com.ccs.coffee_n_crops.farm.WorldClock;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.ClassDecl;
import com.ccs.coffee_n_crops.lang.ast.expr.NewObject;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Old saves must keep their farm behavior while displaying the person-based Java API. */
public class FarmhandMigrationTest {
    @Test
    public void upgradesLegacyFixedCallsAndImplicitParameters() throws Exception {
        JSONObject legacy = new JSONObject("""
            {"t":"block","stmts":[
              {"t":"expr","e":{"t":"call","recv":"drone","name":"flyTo","args":[
                {"t":"lit","type":"INT","v":1},{"t":"lit","type":"INT","v":0}]}},
              {"t":"expr","e":{"t":"usercall","name":"cut","returnType":"VOID","types":[],"args":[]}}
            ],"methods":[{"name":"cut","returnType":"VOID","parameters":[],"body":{"t":"block","stmts":[
              {"t":"expr","e":{"t":"call","recv":"drone","name":"harvest","args":[]}}
            ]}}]}
            """);
        Block upgraded = AstJson.blockFrom(legacy);
        String java = JavaPrinter.toSource(upgraded);
        assertTrue(java.contains("void run(Farmhand farmer)"));
        assertTrue(java.contains("farmer.walkTo(1, 0);"));
        assertTrue(java.contains("void cut(Farmhand farmer)"));
        assertTrue(java.contains("cut(farmer);"));
        assertTrue(legacy.toString().contains("flyTo")); // Loading does not rewrite the stored source.
        FarmWorld world = new FarmWorld(new String[]{".W"}, 0, 0);
        run(upgraded, world);
        assertEquals(1, world.wheatHarvested());
        assertEquals(Farmhand.Action.IDLE, world.farmer.pose().action);
    }

    @Test
    public void upgradesNativeObjectCallsAndRetainsUserIdentifiers() throws Exception {
        JSONObject legacy = new JSONObject("""
            {"t":"block","stmts":[
              {"t":"var","type":"CLASS:Worker","name":"worker","init":{"t":"newobject","class":"Worker",
                "types":["DRONE"],"args":[{"t":"drone"}]}},
              {"t":"expr","e":{"t":"instancecall","recv":{"t":"ref","name":"worker"},"recvType":"CLASS:Worker",
                "name":"flyTo","returnType":"VOID","types":[],"args":[]}}
            ],"classes":[{"name":"Worker","fields":[{"t":"var","type":"DRONE","name":"drone",
              "init":{"t":"lit","type":"DRONE","v":null}}],
              "constructor":{"parameters":[{"t":"var","type":"DRONE","name":"drone",
                "init":{"t":"lit","type":"DRONE","v":null}}],"body":{"t":"block","stmts":[
                  {"t":"fieldassign","target":{"t":"field","recv":{"t":"this"},"recvType":"CLASS:Worker",
                    "name":"drone","type":"DRONE"},"value":{"t":"ref","name":"drone"}}
              ]}},
              "methods":[{"name":"flyTo","returnType":"VOID","parameters":[],"body":{"t":"block","stmts":[
                {"t":"expr","e":{"t":"instancecall","recv":{"t":"field","recv":{"t":"this"},"recvType":"CLASS:Worker",
                  "name":"drone","type":"DRONE"},"recvType":"DRONE","name":"flyTo","returnType":"VOID",
                  "types":["INT","INT"],"args":[{"t":"lit","type":"INT","v":1},{"t":"lit","type":"INT","v":0}]}},
                {"t":"expr","e":{"t":"call","recv":"drone","name":"harvest","args":[]}}
              ]}}]
            }]}
            """);
        Block upgraded = AstJson.blockFrom(legacy);
        String java = JavaPrinter.toSource(upgraded);
        assertTrue(java.contains("new Worker(farmer)"));
        assertTrue(java.contains("Worker(Farmhand drone)"));
        assertTrue(java.contains("this.drone.walkTo(1, 0);"));
        assertTrue(java.contains("worker.flyTo();"));
        FarmWorld world = new FarmWorld(new String[]{".W"}, 0, 0);
        run(upgraded, world);
        assertEquals(1, world.wheatHarvested());
        assertEquals(java, JavaPrinter.toSource(AstJson.blockFrom(AstJson.toJson(upgraded))));
        assertSame(JType.FARMHAND, JType.valueOf("DRONE"));
    }

    @Test
    public void displacesLegacyNamesThatWouldShadowTheNewApi() throws Exception {
        Block oldProgram = new Block();
        ClassDecl userClass = new ClassDecl("Farmhand");
        oldProgram.addClass(userClass);
        oldProgram.add(new VarDecl(userClass.type(), "farmer", new NewObject(userClass)));
        oldProgram.add(Ast.harvest());
        JSONObject legacy = AstJson.toJson(oldProgram);
        legacy.remove("actorApi");
        legacy.getJSONArray("stmts").getJSONObject(1).getJSONObject("e").put("recv", "drone");

        Block upgraded = AstJson.blockFrom(legacy);
        assertEquals("Farmhand2", upgraded.classes().get(0).name);
        assertTrue(JavaPrinter.toSource(upgraded).contains("Farmhand2 previousFarmer = new Farmhand2();"));
        FarmWorld world = new FarmWorld(new String[]{"W"}, 0, 0);
        run(upgraded, world);
        assertEquals(1, world.wheatHarvested());
    }

    @Test
    public void rendersAConsistentPoseAndIgnoresCleanupOfAnOldAction() {
        FarmWorld world = new FarmWorld(new String[]{"WW."}, 0, 0);
        Farmhand hand = world.farmer;
        world.move(hand, Direction.EAST);
        Farmhand.Pose step = hand.pose();
        assertEquals(Farmhand.Action.WALK, step.action);
        assertEquals(0, step.fromX);
        assertEquals(1, step.x);
        Farmhand.Pose water = hand.beginAction(Farmhand.Action.WATER, 0.6);
        hand.finishAction(step);
        assertSame(water, hand.pose());
        world.reset();
        hand.finishAction(water);
        assertEquals(Farmhand.Action.IDLE, hand.pose().action);
        assertEquals(0, hand.pose().x);
    }

    private static void run(Block program, FarmWorld world) throws InterruptedException {
        Validator.Problem problem = Validator.check(program);
        assertNull(problem == null ? null : problem.message, problem);
        ExecutionContext context = new ExecutionContext(world.farmer, world, new DebugController(),
                new WorldClock(0), NativeRegistry.STANDARD, new ExecListener() {
                    @Override public void onNodeEnter(int workerId, int nodeId) { }
                    @Override public void onFinished(int workerId) { }
                    @Override public void onError(int workerId, int nodeId, String message) { }
                });
        program.exec(context);
    }
}
