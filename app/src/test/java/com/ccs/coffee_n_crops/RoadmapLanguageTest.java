package com.ccs.coffee_n_crops;

import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.farm.WorldClock;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.expr.Binary;
import com.ccs.coffee_n_crops.lang.ast.expr.FunctionCall;
import com.ccs.coffee_n_crops.lang.ast.expr.FunctionType;
import com.ccs.coffee_n_crops.lang.ast.expr.FunctionValue;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamLambda;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.interp.DebugController;
import com.ccs.coffee_n_crops.lang.interp.ExecListener;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.print.JavaPrinter;
import com.ccs.coffee_n_crops.lang.runtime.NativeRegistry;
import com.ccs.coffee_n_crops.lang.serial.AstJson;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class RoadmapLanguageTest {
    @Rule public TemporaryFolder files = new TemporaryFolder();

    @Test
    public void newLessonSolutionsAndEscapedTextCompileWithJava17() throws Exception {
        StringBuilder source = new StringBuilder("""
                enum Direction { NORTH, SOUTH, EAST, WEST }
                enum Crop { WHEAT }
                class Farmhand {
                    void harvest() { }
                    void water() { }
                    void move(Direction direction) { }
                    void walkTo(int x, int y) { }
                    String getCrop() { return "wheat"; }
                    Crop[] scanCrops() { return new Crop[0]; }
                }
                """);
        for (int level = 59; level <= 67; level++) {
            Block restored = AstJson.blockFrom(AstJson.toJson(LanguagePracticeLevelsTest.solution(level)));
            source.append("class Lesson").append(level).append(" {\n")
                    .append(JavaPrinter.toSource(restored)).append("}\n");
        }
        Block text = new Block();
        String value = "Зерно \"A\" \\u000a\r\n\t\b\f\0🙂";
        text.add(new VarDecl(JType.STRING, "label", Ast.lit(value)));
        Block restored = AstJson.blockFrom(AstJson.toJson(text));
        assertEquals(value, ((com.ccs.coffee_n_crops.lang.ast.expr.Literal)
                ((VarDecl) restored.get(0)).init.get()).value);
        source.append("class TextEntry {\n").append(JavaPrinter.toSource(restored)).append("}\n");

        Path directory = files.newFolder().toPath();
        Path input = directory.resolve("Lessons.java");
        Files.write(input, source.toString().getBytes(StandardCharsets.UTF_8));
        Path javac = Path.of(System.getProperty("java.home"), "bin", "javac");
        Process compiler = new ProcessBuilder(javac.toString(), "-proc:none", "--release", "17",
                "-encoding", "UTF-8", "-d", directory.toString(), input.toString())
                .redirectErrorStream(true).start();
        try {
            assertTrue("javac did not finish", compiler.waitFor(10, TimeUnit.SECONDS));
            assertEquals(new String(compiler.getInputStream().readAllBytes(), StandardCharsets.UTF_8),
                    0, compiler.exitValue());
        } finally {
            compiler.destroyForcibly();
        }
    }

    @Test
    public void standaloneLambdaRetainsBindingFromItsCreationScope() throws Exception {
        FarmWorld world = new FarmWorld(new String[]{"."}, 0, 0);
        ExecutionContext ctx = new ExecutionContext(world.farmer, world, new DebugController(),
                new WorldClock(0), NativeRegistry.STANDARD, new ExecListener() {
                    @Override public void onNodeEnter(int actor, int node) { }
                    @Override public void onFinished(int actor) { }
                    @Override public void onError(int actor, int node, String message) { }
                });
        ctx.pushScope();
        ctx.scope().declare("offset", Value.ofInt(3));
        FunctionValue expression = new FunctionValue(FunctionType.TRANSFORM,
                new StreamLambda("input", JType.INT, new Binary(Binary.Op.ADD,
                        new VarRef("input"), new VarRef("offset"))));
        Value function = expression.eval(ctx);
        ctx.popScope();

        ctx.scope().declare("offset", Value.ofInt(99));
        ctx.scope().declare("transform", function);
        assertEquals(5, new FunctionCall("transform", FunctionType.TRANSFORM, Ast.lit(2)).eval(ctx).asInt());
        assertEquals(99, ctx.readVariable("offset").asInt());
    }
}
