package com.ccs.coffee_n_crops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.farm.Direction;
import com.ccs.coffee_n_crops.farm.WorldClock;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.ClassDecl;
import com.ccs.coffee_n_crops.lang.ast.ConstructorDecl;
import com.ccs.coffee_n_crops.lang.ast.MethodDecl;
import com.ccs.coffee_n_crops.lang.ast.expr.Access;
import com.ccs.coffee_n_crops.lang.ast.expr.Binary;
import com.ccs.coffee_n_crops.lang.ast.expr.FarmhandRef;
import com.ccs.coffee_n_crops.lang.ast.expr.FieldAccess;
import com.ccs.coffee_n_crops.lang.ast.expr.InstanceCall;
import com.ccs.coffee_n_crops.lang.ast.expr.InstanceOf;
import com.ccs.coffee_n_crops.lang.ast.expr.Literal;
import com.ccs.coffee_n_crops.lang.ast.expr.NewObject;
import com.ccs.coffee_n_crops.lang.ast.expr.NewObjectArray;
import com.ccs.coffee_n_crops.lang.ast.expr.ObjectArrayGet;
import com.ccs.coffee_n_crops.lang.ast.expr.ThisRef;
import com.ccs.coffee_n_crops.lang.ast.expr.StaticConstant;
import com.ccs.coffee_n_crops.lang.ast.expr.StrEquals;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.Assign;
import com.ccs.coffee_n_crops.lang.ast.stmt.ExprStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.FieldAssign;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForEachStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.IfStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ObjectArrayAssign;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.check.Validator;
import com.ccs.coffee_n_crops.lang.interp.ExecListener;
import com.ccs.coffee_n_crops.lang.interp.Interpreter;
import com.ccs.coffee_n_crops.lang.print.JavaPrinter;
import com.ccs.coffee_n_crops.lang.serial.AstJson;
import com.ccs.coffee_n_crops.lang.runtime.ObjectMethods;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.progress.LevelCatalog;
import com.ccs.coffee_n_crops.progress.LevelDef;

import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Actual object behavior and saved reference programs for both OOP sequences. */
public class OopTest {
    private static final int FIRST_OOP_LEVEL = 18;
    private static final int OOP_LEVEL_COUNT = 4;
    private static final int INTERFACES = 32;
    private static final int OBJECT_ARRAYS = 33;
    private static final int POLYMORPHISM = 34;
    private static final String TOOL_VAR = "tool";

    private static String run(Block program, FarmWorld world, List<String> snapshots) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        String[] error = new String[1];
        Interpreter interpreter = new Interpreter(new WorldClock(0));
        try {
            interpreter.start(program, world, new ExecListener() {
                @Override public void onNodeEnter(int farmerId, int nodeId) { }
                @Override public void onFinished(int farmerId) { done.countDown(); }
                @Override public void onError(int farmerId, int nodeId, String message) {
                    error[0] = message;
                    done.countDown();
                }
                @Override public void onObjectState(int farmerId, String state) {
                    snapshots.add(state);
                }
            }, false);
            assertTrue("program did not finish", done.await(5, TimeUnit.SECONDS));
            return error[0];
        } finally {
            interpreter.stop();
        }
    }

    private static String run(Block program, FarmWorld world) throws InterruptedException {
        return run(program, world, new ArrayList<>());
    }

    @Test
    public void allFourLessonsCompleteAfterSavingAndLoading() throws Exception {
        Block[] references = {OopPrograms.fields(), OopPrograms.constructors(), OopPrograms.methods(),
                OopPrograms.inheritance()};
        assertEquals(OOP_LEVEL_COUNT, references.length);
        for (int i = 0; i < references.length; i++) {
            Block loaded = AstJson.blockFrom(new JSONObject(AstJson.toJson(references[i]).toString()));
            assertEquals(JavaPrinter.toSource(references[i]), JavaPrinter.toSource(loaded));
            assertCompiles(JavaPrinter.toSource(loaded));
            Validator.Problem problem = Validator.check(loaded);
            assertNull(problem == null ? null : problem.message, problem);
            LevelDef level = LevelCatalog.get(FIRST_OOP_LEVEL + i);
            FarmWorld world = level.createWorld();
            assertNull(level.id, run(loaded, world));
            assertEquals(level.id, 0, world.ripeRemaining());
            assertEquals(level.id, 0, world.dryRemaining());
            assertEquals(level.id, 0, world.ruined());
            assertNull(level.id, level.check(world, loaded));
        }
    }

    @Test
    public void interfaceAndObjectArrayLessonsCompleteAfterSavingAndLoading() throws Exception {
        for (int index = INTERFACES; index <= POLYMORPHISM; index++) {
            LevelDef level = LevelCatalog.get(index);
            Block solution = level.createProgram();
            if (index == INTERFACES) solveInterface(solution);
            if (index == OBJECT_ARRAYS) solveObjectArray(solution);
            if (index == POLYMORPHISM) solvePolymorphism(solution);
            Block loaded = AstJson.blockFrom(new JSONObject(AstJson.toJson(solution).toString()));
            String source = JavaPrinter.toSource(loaded);
            assertEquals(JavaPrinter.toSource(solution), source);
            assertCompiles(source);
            Validator.Problem problem = Validator.check(loaded);
            assertNull(problem == null ? null : problem.message, problem);
            FarmWorld world = level.createWorld();
            assertNull(level.id, run(loaded, world));
            assertNull(level.id, level.check(world, loaded));
        }
    }

    @Test
    public void twoSeparateConcreteCallsDoNotSatisfyThePolymorphismLesson() throws Exception {
        LevelDef level = LevelCatalog.get(POLYMORPHISM);
        Block program = level.createProgram();
        for (ClassDecl worker : program.classes()) {
            if (worker.isInterface) continue;
            String name = worker.name.toLowerCase(Locale.ROOT);
            program.add(new VarDecl(worker.type(), name, new NewObject(worker, new FarmhandRef())));
            program.add(callWork(name, worker));
        }
        assertNull(Validator.check(program));
        FarmWorld world = level.createWorld();
        assertNull(run(program, world));
        assertEquals(0, world.ripeRemaining());
        assertEquals(0, world.dryRemaining());
        assertNotNull(level.check(world, program));
    }

    @Test
    public void eachNewLevelVisitGetsAnIndependentStarter() {
        for (int index = INTERFACES; index <= POLYMORPHISM; index++) {
            LevelDef level = LevelCatalog.get(index);
            Block starter = level.createProgram();
            String expected = JavaPrinter.toSource(starter);
            starter.removeClass(starter.classes().get(0));
            assertEquals(expected, JavaPrinter.toSource(level.createProgram()));
        }
    }

    private static void solveInterface(Block program) {
        ClassDecl harvester = program.classes().get(0);
        ClassDecl tool = new ClassDecl("Tool", true);
        tool.addMethod(new MethodDecl("work", JType.VOID));
        program.addClass(tool);
        harvester.interfaceName = tool.name;
        program.add(new VarDecl(tool.type(), TOOL_VAR, new NewObject(harvester, new FarmhandRef())));
        program.add(callWork(TOOL_VAR, tool));
    }

    private static void solveObjectArray(Block program) {
        ClassDecl plan = program.classes().get(0);
        String array = "rowPlans";
        program.add(new VarDecl(JType.objectArray(plan.type()), array, new NewObjectArray(plan.type(), Ast.lit(2))));
        program.add(new ObjectArrayAssign(array, plan.type(), Ast.lit(0), new NewObject(plan, Ast.lit(1))));
        program.add(new ObjectArrayAssign(array, plan.type(), Ast.lit(1), new NewObject(plan, Ast.lit(4))));
        var loop = Ast.forCount("i", 0);
        ((Binary) loop.cond.get()).right.set(new Access(Access.Kind.LENGTH, array));
        loop.body.add(new VarDecl(plan.type(), "rowPlan", new ObjectArrayGet(array, plan.type(), new VarRef("i"))));
        loop.body.add(new ExprStmt(new InstanceCall(new VarRef("rowPlan"), plan.type(),
                plan.methods().get(0), new FarmhandRef())));
        program.add(loop);
    }

    private static void solvePolymorphism(Block program) {
        ClassDecl tool = program.classes().get(0);
        String array = "tools";
        program.add(new VarDecl(JType.objectArray(tool.type()), array, new NewObjectArray(tool.type(), Ast.lit(2))));
        for (int i = 1; i < program.classes().size(); i++) {
            program.add(new ObjectArrayAssign(array, tool.type(), Ast.lit(i - 1),
                    new NewObject(program.classes().get(i), new FarmhandRef())));
        }
        ForEachStmt loop = new ForEachStmt(tool.type(), TOOL_VAR, array);
        loop.body.add(callWork(TOOL_VAR, tool));
        program.add(loop);
    }

    private static ExprStmt callWork(String variable, ClassDecl type) {
        return new ExprStmt(new InstanceCall(new VarRef(variable), type.type(), type.methods().get(0)));
    }

    @Test
    public void advancedObjectLessonsRoundTripCompileAndComplete() throws Exception {
        for (int number = 36; number <= 39; number++) {
            LevelDef level = LevelCatalog.get(number - 1);
            Block original = level.createProgram();
            ClassDecl type = original.classes().get(0);
            switch (number) {
                case 36 -> {
                    original.add(new VarDecl(JType.OBJECT, "object", new NewObject(type)));
                    original.add(new VarDecl(JType.OBJECT, "object2", new VarRef("object")));
                    IfStmt check = Ast.ifStmt(objectMethod("object", JType.OBJECT, "equals", new VarRef("object2")), false);
                    check.thenBlock.add(Ast.harvest());
                    original.add(check);
                    original.add(new VarDecl(JType.INT, "hash", objectMethod("object", JType.OBJECT, "hashCode")));
                    original.add(new VarDecl(JType.STRING, "description", objectMethod("object", JType.OBJECT, "toString")));
                    original.add(new VarDecl(JType.CLASS_TOKEN, "type", objectMethod("object", JType.OBJECT, "getClass")));
                }
                case 37 -> {
                    original.add(new VarDecl(JType.OBJECT, "object", new NewObject(type)));
                    IfStmt check = Ast.ifStmt(new InstanceOf(new VarRef("object"), type.type()), false);
                    check.thenBlock.add(Ast.harvest());
                    original.add(check);
                }
                case 38 -> {
                    type.constructor().body.add(new FieldAssign(new FieldAccess(new ThisRef(), type.type(), "column", JType.INT),
                            new VarRef("column")));
                    original.add(new VarDecl(type.type(), "route", new NewObject(type, Ast.lit(1))));
                    original.add(new VarDecl(type.type(), "route2", new NewObject(type, Ast.lit(1))));
                    original.add(new FieldAssign(OopPrograms.field("route2", type.name, "column", JType.INT), Ast.lit(2)));
                    for (String name : new String[]{"route", "route2"}) {
                        original.add(Ast.walkTo(OopPrograms.field(name, type.name, "column", JType.INT), Ast.lit(0)));
                        original.add(Ast.harvest());
                    }
                }
                case 39 -> {
                    type.addField(new VarDecl(JType.INT, "STOPS", Ast.lit(3), true, true));
                    original.add(new VarDecl(JType.INT, "stops", new StaticConstant(type.name, "STOPS"), true, false));
                    var loop = Ast.forCount("i", 3);
                    ((Binary) loop.cond.get()).right.set(new VarRef("stops"));
                    loop.body.add(Ast.harvest());
                    loop.body.add(Ast.move(Direction.EAST));
                    original.add(loop);
                }
                default -> throw new AssertionError(number);
            }
            Block loaded = AstJson.blockFrom(new JSONObject(AstJson.toJson(original).toString()));
            String source = JavaPrinter.toSource(loaded);
            assertEquals(JavaPrinter.toSource(original), source);
            assertCompiles(source);
            Validator.Problem problem = Validator.check(loaded);
            assertNull(problem == null ? null : problem.message, problem);
            FarmWorld world = level.createWorld();
            assertNull(level.id, run(loaded, world));
            assertNull(level.id, level.check(world, loaded));
        }
    }

    @Test
    public void objectCallsUseTheRuntimeClassAndNullDoesNotPassInstanceof() throws Exception {
        Block program = new Block();
        ClassDecl base = new ClassDecl("Base");
        ClassDecl derived = new ClassDecl("Derived", base.name);
        MethodDecl hash = new MethodDecl("hashCode", JType.INT);
        hash.override = true;
        hash.body.add(new com.ccs.coffee_n_crops.lang.ast.stmt.ReturnStmt(JType.INT, Ast.lit(17)));
        derived.addMethod(hash);
        program.addClass(base);
        program.addClass(derived);
        program.add(new VarDecl(JType.OBJECT, "item", new NewObject(derived)));
        program.add(new VarDecl(JType.OBJECT, "nil", Ast.defaultValue(JType.OBJECT)));
        program.add(new VarDecl(JType.CLASS_TOKEN, "itemType", objectMethod("item", JType.OBJECT, "getClass")));
        program.add(new VarDecl(JType.CLASS_TOKEN, "otherType", new InstanceCall(new NewObject(derived),
                derived.type(), ObjectMethods.signature("getClass"))));
        IfStmt check = Ast.ifStmt(new Binary(Binary.Op.AND, new InstanceOf(new VarRef("item"), base.type()),
                new Binary(Binary.Op.AND, Ast.eq(objectMethod("item", JType.OBJECT, "hashCode"), 17),
                        new Binary(Binary.Op.AND, objectMethod("itemType", JType.CLASS_TOKEN, "equals", new VarRef("otherType")),
                                new StrEquals(objectMethod("item", JType.OBJECT, "toString"), Ast.lit("Derived@11"))))), false);
        check.thenBlock.add(Ast.harvest());
        program.add(check);
        IfStmt nullCheck = Ast.ifStmt(new InstanceOf(new VarRef("nil"), base.type()), false);
        nullCheck.thenBlock.add(Ast.move(Direction.EAST));
        nullCheck.thenBlock.add(Ast.harvest());
        program.add(nullCheck);
        assertCompiles(JavaPrinter.toSource(program));
        assertNull(Validator.check(program));
        FarmWorld world = new FarmWorld(new String[]{"WW"}, 0, 0);
        assertNull(run(program, world));
        assertEquals(1, world.wheatHarvested());
    }

    @Test
    public void checkerRejectsReassignmentOfFinalAndUnrelatedInstanceofOperands() {
        Block program = new Block();
        program.add(new VarDecl(JType.INT, "stops", Ast.lit(3), true, false));
        program.add(new Assign(JType.INT, "stops", Ast.lit(4)));
        assertEquals("cannot assign a value to final variable stops", Validator.check(program).message);

        program = new Block();
        ClassDecl a = new ClassDecl("Parcel"), b = new ClassDecl("Route");
        program.addClass(a);
        program.addClass(b);
        program.add(new VarDecl(a.type(), "parcel", new NewObject(a)));
        program.add(new VarDecl(JType.BOOLEAN, "test", new InstanceOf(new VarRef("parcel"), b.type())));
        assertEquals("incompatible types: Parcel cannot be converted to Route", Validator.check(program).message);
    }

    private static InstanceCall objectMethod(String receiver, JType receiverType, String name,
                                             com.ccs.coffee_n_crops.lang.ast.Expr... args) {
        return new InstanceCall(new VarRef(receiver), receiverType, ObjectMethods.signature(name), args);
    }

    @Test
    public void aCompletedFarmAndUnusedClassesCannotSatisfyTheLesson() {
        Block[] unused = {OopPrograms.fields(), OopPrograms.constructors(), OopPrograms.methods(),
                OopPrograms.inheritance()};
        for (int i = 0; i < OOP_LEVEL_COUNT; i++) {
            LevelDef level = LevelCatalog.get(FIRST_OOP_LEVEL + i);
            FarmWorld world = level.createWorld();
            for (int y = 0; y < world.height; y++) {
                for (int x = 0; x < world.width; x++) {
                    world.walkTo(world.farmer, x, y);
                    if (world.isRipe(world.farmer)) world.harvest(world.farmer);
                    if (world.isDry(world.farmer)) world.water(world.farmer);
                }
            }
            while (unused[i].size() > 0) unused[i].remove(0);
            assertNotNull(level.id, level.check(world, unused[i]));
        }
    }

    @Test
    public void twoObjectsHaveIndependentFieldsButAnAliasSharesItsObject() throws Exception {
        ClassDecl counter = OopPrograms.counter();
        JType type = JType.reference(counter.name);
        Block program = new Block();
        program.addClass(counter);
        program.add(new VarDecl(type, "first", new NewObject(counter)));
        program.add(new VarDecl(type, "second", new NewObject(counter)));
        program.add(new VarDecl(type, "alias", new VarRef("first")));
        program.add(new FieldAssign(OopPrograms.field("alias", counter.name, "value", JType.INT), Ast.lit(3)));
        IfStmt check = Ast.ifStmt(new Binary(Binary.Op.AND,
                Ast.eq(OopPrograms.field("first", counter.name, "value", JType.INT), 3),
                Ast.eq(OopPrograms.field("second", counter.name, "value", JType.INT), 0)), false);
        check.thenBlock.add(Ast.harvest());
        program.add(check);
        assertNull(Validator.check(program));
        FarmWorld world = new FarmWorld(new String[]{"W"}, 0, 0);
        assertNull(run(program, world));
        assertEquals(1, world.wheatHarvested());
        assertEquals(2, world.objectCreations(counter.name));
        assertEquals(1, world.fieldWrites(counter.name, "value"));
    }

    @Test
    public void dispatchThroughBaseTypeExecutesTheSubclassAndInitializesItsInheritedField() throws Exception {
        Block program = OopPrograms.inheritance();
        LevelDef level = LevelCatalog.get(FIRST_OOP_LEVEL + OOP_LEVEL_COUNT - 1);
        FarmWorld world = level.createWorld();
        assertNull(run(program, world));
        assertEquals(1, world.objectCreations("Harvester"));
        assertEquals(1, world.superCalls("Harvester"));
        assertEquals(1, world.instanceCalls("Harvester", "work"));
        assertEquals(1, world.fieldWrites("Worker", "farmer"));
        assertEquals(world.totalWheat(), world.wheatHarvested());
    }

    @Test
    public void inheritanceLessonRequiresTheExplicitSuperCallItTeaches() throws Exception {
        Block program = OopPrograms.inheritance();
        ClassDecl worker = program.classes().get(0);
        ClassDecl harvester = program.classes().get(1);
        worker.setConstructor(new ConstructorDecl());
        harvester.constructor().body.remove(0);
        harvester.constructor().body.add(new FieldAssign(OopPrograms.own(harvester.name, "farmer", JType.FARMHAND),
                new VarRef("farmer")));
        assertNull(Validator.check(program));
        LevelDef level = LevelCatalog.get(FIRST_OOP_LEVEL + OOP_LEVEL_COUNT - 1);
        FarmWorld world = level.createWorld();
        assertNull(run(program, world));
        assertEquals(0, world.ripeRemaining());
        assertTrue("implicit Java base initialization still ran", world.superCalls(harvester.name) > 0);
        assertNotNull(level.check(world, program));
    }

    @Test
    public void recursiveInstanceCallsRespectTheSameDepthLimitAsHelpers() throws Exception {
        ClassDecl cls = new ClassDecl("Repeater");
        MethodDecl again = new MethodDecl("again", JType.VOID);
        again.body.add(new ExprStmt(new InstanceCall(new ThisRef(), JType.reference(cls.name), again)));
        cls.addMethod(again);
        Block program = new Block();
        program.addClass(cls);
        program.add(new VarDecl(JType.reference(cls.name), "object", new NewObject(cls)));
        program.add(new ExprStmt(new InstanceCall(new VarRef("object"), JType.reference(cls.name), again)));
        assertNull(Validator.check(program));
        FarmWorld world = new FarmWorld(new String[]{"W"}, 0, 0);
        assertEquals("StackOverflowError", run(program, world));
        Block next = new Block();
        next.add(Ast.harvest());
        assertNull(run(next, world));
        assertEquals(1, world.wheatHarvested());
    }

    @Test
    public void nullFieldReceiverReportsAPlayerErrorInsteadOfCrashingTheApp() throws Exception {
        ClassDecl counter = OopPrograms.counter();
        Block program = new Block();
        program.addClass(counter);
        program.add(new VarDecl(JType.reference(counter.name), "counter",
                new Literal(JType.reference(counter.name), null)));
        program.add(new FieldAssign(OopPrograms.field("counter", counter.name, "value", JType.INT), Ast.lit(1)));
        assertNull(Validator.check(program));
        String error = run(program, new FarmWorld(new String[]{"."}, 0, 0));
        assertNotNull(error);
        assertTrue(error, error.startsWith("NullPointerException"));
    }

    @Test
    public void debuggerReceivesImmutableObjectSnapshotsAndResetClearsLessonEvidence() throws Exception {
        Block program = OopPrograms.fields();
        List<String> snapshots = new ArrayList<>();
        FarmWorld world = LevelCatalog.get(FIRST_OOP_LEVEL).createWorld();
        assertNull(run(program, world, snapshots));
        assertTrue(snapshots.stream().anyMatch(s -> s.contains("Counter") && s.contains("value")));
        List<String> before = new ArrayList<>(snapshots);
        assertTrue(world.objectCreations("Counter") > 0);
        assertTrue(world.fieldWrites("Counter", "value") > 0);
        world.reset();
        assertEquals(0, world.objectCreations("Counter"));
        assertEquals(0, world.fieldWrites("Counter", "value"));
        assertEquals(before, snapshots);
    }

    @Test
    public void namedReferencesAreInternedAndDistinctFromEachOtherAndTheNativeDrone() {
        assertTrue(JType.reference("Counter") == JType.reference("Counter"));
        assertFalse(JType.reference("Counter") == JType.reference("RowPlan"));
        assertFalse(JType.reference("Counter") == JType.FARMHAND);
        assertTrue(JType.reference("Counter") == JType.valueOf(JType.reference("Counter").name()));
    }

    /** Build-time verification only: the Android game interprets its AST and never invokes javac. */
    private static void assertCompiles(String source) throws Exception {
        String api = "enum Direction { NORTH, EAST, SOUTH, WEST }\n"
                + "class Farmhand { void harvest() {} void water() {} void move(Direction d) {} "
                + "void walkTo(int x, int y) {} boolean isRipe() { return true; } "
                + "boolean isDry() { return true; } }\n";
        Path directory = Files.createTempDirectory("coffee-crops-oop-");
        Process compiler = null;
        try {
            Path input = directory.resolve("Program.java");
            Files.write(input, (api + source).getBytes(StandardCharsets.UTF_8));
            String javac = new File(System.getProperty("java.home"), "bin/javac").getAbsolutePath();
            compiler = new ProcessBuilder(javac, "-proc:none", "--release", "17", input.toString())
                    .redirectErrorStream(true).start();
            assertTrue("javac did not finish", compiler.waitFor(5, TimeUnit.SECONDS));
            String diagnostics = new String(compiler.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(diagnostics + "\n" + source, 0, compiler.exitValue());
        } finally {
            if (compiler != null) compiler.destroyForcibly();
            File[] files = directory.toFile().listFiles();
            if (files != null) for (File file : files) Files.deleteIfExists(file.toPath());
            Files.deleteIfExists(directory);
        }
    }
}
