package com.ccs.coffee_n_crops;

import com.ccs.coffee_n_crops.editor.EditorCursor;
import com.ccs.coffee_n_crops.editor.PaletteProvider;
import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.farm.WorldClock;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.expr.Binary;
import com.ccs.coffee_n_crops.lang.ast.expr.CollectionCall;
import com.ccs.coffee_n_crops.lang.ast.expr.CollectionCall.Op;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.ExprStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ListAdd;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.ast.stmt.WhileStmt;
import com.ccs.coffee_n_crops.lang.check.Validator;
import com.ccs.coffee_n_crops.lang.interp.ExecListener;
import com.ccs.coffee_n_crops.lang.interp.Interpreter;
import com.ccs.coffee_n_crops.lang.print.JavaPrinter;
import com.ccs.coffee_n_crops.lang.serial.AstJson;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.progress.LevelCatalog;
import com.ccs.coffee_n_crops.progress.LevelDef;

import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Verifies the saved source, interpreter semantics and offered blocks for levels 40–42. */
public class ModernCollectionsTest {
    private static final int FIRST = 40;
    private static final int LAST = 42;
    private static final String STOPS = "stops";
    private static final String ROUTE = "route";
    private static final String STOP = "stop";
    private static final String COLUMN = "column";

    @Test public void savedSolutionsCompileAndCompleteTheirLevels() throws Exception {
        StringBuilder sources = new StringBuilder("import java.util.ArrayList;\n"
                + "class Farmhand { void harvest() {} void walkTo(int x, int y) {} }\n");
        for (int number = FIRST; number <= LAST; number++) {
            LevelDef level = LevelCatalog.get(number - 1);
            Block program = solution(number, level);
            Block loaded = AstJson.blockFrom(new JSONObject(AstJson.toJson(program).toString()));
            String source = JavaPrinter.toSource(loaded);
            assertEquals(level.id, JavaPrinter.toSource(program), source);
            sources.append("class Level").append(number).append(" {\n").append(source).append("}\n");
            assertNull(level.id, Validator.check(loaded));
            FarmWorld world = level.createWorld();
            assertNull(level.id, run(loaded, world));
            assertNull(level.id, level.check(world, loaded));
            world.reset();
            assertNotNull(level.id, level.check(world, loaded));
        }
        assertCompiles(sources.toString());
    }

    @Test public void iteratorRemoveUpdatesTheListAndIndependentChangesFailFast() throws Exception {
        Block program = new Block();
        program.add(Ast.newList(STOPS));
        program.add(new ListAdd(STOPS, Ast.lit(1)));
        program.add(new ListAdd(STOPS, Ast.lit(0)));
        program.add(iterator());
        program.add(new ExprStmt(call(Op.NEXT, ROUTE)));
        program.add(new ExprStmt(call(Op.REMOVE, ROUTE)));
        var emptyFirstColumn = Ast.ifStmt(new Binary(Binary.Op.EQ, call(Op.NEXT, ROUTE), Ast.lit(0)), false);
        emptyFirstColumn.thenBlock.add(Ast.harvest());
        program.add(emptyFirstColumn);
        FarmWorld world = new FarmWorld(new String[]{"W"}, 0, 0);
        assertNull(run(program, world));
        assertEquals(1, world.wheatHarvested());

        program.add(new ListAdd(STOPS, Ast.lit(8)));
        program.add(new ExprStmt(call(Op.NEXT, ROUTE)));
        assertEquals("ConcurrentModificationException", run(program, world));
    }

    @Test public void theFirstMatchingValueWinsAndNoMatchUsesTheFallback() throws Exception {
        Block program = new Block();
        program.add(Ast.newList(STOPS));
        program.add(new ListAdd(STOPS, Ast.lit(3)));
        program.add(new ListAdd(STOPS, Ast.lit(5)));
        program.add(optional(findFirst(3)));
        harvestAt(program, new CollectionCall(Op.OR_ELSE, STOP, Ast.lit(1)));
        FarmWorld world = new FarmWorld(new String[]{"...W.W"}, 0, 0);
        assertNull(run(program, world));
        assertEquals(1, world.wheatHarvested());
        assertEquals(3, world.farmer.pose().x);

        program.remove(3);
        program.add(3, optional(findFirst(8)));
        FarmWorld missing = new FarmWorld(new String[]{".W.W.W"}, 0, 0);
        assertNull(run(program, missing));
        assertEquals(1, missing.wheatHarvested());
        assertEquals(1, missing.farmer.pose().x);
        program.add(new ExprStmt(call(Op.GET, STOP)));
        assertEquals("NoSuchElementException: No value present", run(program, missing));
    }

    @Test public void paletteUsesReceiversAndMakesTheStreamCutoffEditable() {
        LevelDef level = LevelCatalog.get(LAST - 1);
        Block starter = level.createProgram();
        EditorCursor cursor = new EditorCursor();
        cursor.moveTo(starter, starter.size());
        assertTrue(PaletteProvider.query(cursor, level.unlocks, starter).stream().anyMatch(spec ->
                spec.label.contains("stops.stream().filter(cell -> cell >= 0).findFirst()")));
        CollectionCall filter = findFirst(0);
        starter.add(optional(filter));
        cursor.slot = filter.argument;
        assertTrue(PaletteProvider.query(cursor, level.unlocks, starter).stream().anyMatch(spec ->
                spec.label.equals("4")));
    }

    private static Block solution(int number, LevelDef level) {
        Block program = level.createProgram();
        if (number == FIRST) {
            program.add(iterator());
            WhileStmt loop = new WhileStmt(call(Op.HAS_NEXT, ROUTE));
            harvestAt(loop.body, call(Op.NEXT, ROUTE));
            program.add(loop);
        } else {
            boolean stream = number == LAST;
            program.add(optional(stream ? findFirst(4) : call(Op.EMPTY, null)));
            harvestAt(program, new CollectionCall(Op.OR_ELSE, STOP, Ast.lit(stream ? 0 : 3)));
        }
        return program;
    }

    private static CollectionCall call(Op op, String receiver) {
        return new CollectionCall(op, receiver);
    }

    private static VarDecl iterator() {
        return new VarDecl(JType.INT_ITERATOR, ROUTE, call(Op.ITERATOR, STOPS));
    }

    private static VarDecl optional(CollectionCall initial) {
        return new VarDecl(JType.INT_OPTIONAL, STOP, initial);
    }

    private static CollectionCall findFirst(int cutoff) {
        return new CollectionCall(Op.FIND_FIRST, STOPS, "cell", Ast.lit(cutoff));
    }

    private static void harvestAt(Block body, CollectionCall column) {
        body.add(new VarDecl(JType.INT, COLUMN, column));
        body.add(Ast.walkTo(new VarRef(COLUMN), Ast.lit(0)));
        body.add(Ast.harvest());
    }

    private static String run(Block program, FarmWorld world) throws InterruptedException {
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
            }, false);
            assertTrue("program did not finish", done.await(5, TimeUnit.SECONDS));
            return error[0];
        } finally {
            interpreter.stop();
        }
    }

    private static void assertCompiles(String source) throws Exception {
        Path directory = Files.createTempDirectory("farm-modern-collections-source-");
        Process compiler = null;
        try {
            Path input = directory.resolve("Solutions.java");
            Files.write(input, source.getBytes(StandardCharsets.UTF_8));
            String javac = new File(System.getProperty("java.home"), "bin/javac").getAbsolutePath();
            compiler = new ProcessBuilder(javac, "-proc:none", "--release", "17", input.toString())
                    .redirectErrorStream(true).start();
            assertTrue("javac did not finish", compiler.waitFor(8, TimeUnit.SECONDS));
            assertEquals(new String(compiler.getInputStream().readAllBytes(), StandardCharsets.UTF_8), 0,
                    compiler.exitValue());
        } finally {
            if (compiler != null) compiler.destroyForcibly();
            File[] files = directory.toFile().listFiles();
            if (files != null) for (File file : files) Files.deleteIfExists(file.toPath());
            Files.deleteIfExists(directory);
        }
    }
}
