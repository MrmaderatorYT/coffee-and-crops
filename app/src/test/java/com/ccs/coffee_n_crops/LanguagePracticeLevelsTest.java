package com.ccs.coffee_n_crops;

import com.ccs.coffee_n_crops.farm.Crop;
import com.ccs.coffee_n_crops.farm.Direction;
import com.ccs.coffee_n_crops.farm.WorldClock;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.ast.expr.Access;
import com.ccs.coffee_n_crops.lang.ast.expr.Binary;
import com.ccs.coffee_n_crops.lang.ast.expr.CropEquals;
import com.ccs.coffee_n_crops.lang.ast.expr.FunctionCall;
import com.ccs.coffee_n_crops.lang.ast.expr.FunctionType;
import com.ccs.coffee_n_crops.lang.ast.expr.FunctionValue;
import com.ccs.coffee_n_crops.lang.ast.expr.IntArrayLiteral;
import com.ccs.coffee_n_crops.lang.ast.expr.IntegerMethodRef;
import com.ccs.coffee_n_crops.lang.ast.expr.NewIntGrid;
import com.ccs.coffee_n_crops.lang.ast.expr.NewStringIntMap;
import com.ccs.coffee_n_crops.lang.ast.expr.StringConcat;
import com.ccs.coffee_n_crops.lang.ast.expr.StringIntMapCall;
import com.ccs.coffee_n_crops.lang.ast.expr.StringLength;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.ArrayAssign;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.CompoundAssign;
import com.ccs.coffee_n_crops.lang.ast.stmt.ExprStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForEachStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.SwitchStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.ast.stmt.WhileStmt;
import com.ccs.coffee_n_crops.lang.check.Validator;
import com.ccs.coffee_n_crops.lang.interp.ExecListener;
import com.ccs.coffee_n_crops.lang.interp.Interpreter;
import com.ccs.coffee_n_crops.lang.print.JavaPrinter;
import com.ccs.coffee_n_crops.lang.serial.AstJson;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.progress.LevelCatalog;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class LanguagePracticeLevelsTest {
    @Test
    public void referenceProgramsCompleteAfterSaving() throws Exception {
        for (int number = 59; number <= 67; number++) {
            var level = LevelCatalog.get(number - 1);
            var world = level.createWorld();
            Block original = solution(number);
            Block program = AstJson.blockFrom(AstJson.toJson(original));
            assertEquals(JavaPrinter.toSource(original), JavaPrinter.toSource(program));
            Validator.Problem problem = Validator.check(program);
            assertNull(problem == null ? "" : problem.message, problem);
            CountDownLatch completed = new CountDownLatch(1);
            String[] failure = {null};
            WorldClock clock = new WorldClock(1);
            clock.setSpeed(10);
            Interpreter interpreter = new Interpreter(clock);
            try {
                interpreter.start(program, world, new ExecListener() {
                    @Override public void onNodeEnter(int actor, int node) { }
                    @Override public void onFinished(int actor) { completed.countDown(); }
                    @Override public void onError(int actor, int node, String message) {
                        failure[0] = message;
                        completed.countDown();
                    }
                }, false);
                assertTrue(level.id, completed.await(3, TimeUnit.SECONDS));
                assertNull(level.id, failure[0]);
                assertNull(level.id, level.check(world, program));
            } finally {
                interpreter.stop();
            }
        }
    }

    static Block solution(int number) {
        return switch (number) {
            case 59 -> label();
            case 60 -> countCrops();
            case 61 -> array();
            case 62 -> grid();
            case 63 -> countdown();
            case 64 -> fallThrough();
            case 65 -> crops();
            case 66 -> applyFunction();
            case 67 -> createArrayFromReference();
            default -> throw new IllegalArgumentException("No solution for level " + number);
        };
    }

    private static Block label() {
        var label = new VarDecl(JType.STRING, "label", new StringConcat(Ast.lit("whe"), JType.STRING, Ast.lit("at")));
        var check = Ast.ifStmt(Ast.eq(new StringLength(new VarRef("label")), 5), false);
        check.thenBlock.add(Ast.harvest());
        return program(label, check);
    }

    private static Block countCrops() {
        var counts = new VarDecl(JType.STRING_INT_MAP, "counts", new NewStringIntMap());
        ForStmt loop = Ast.forCount("i", 2);
        loop.body.add(new VarDecl(JType.STRING, "crop", Ast.getCrop()));
        var prior = new StringIntMapCall("counts", StringIntMapCall.Op.GET_OR_DEFAULT, new VarRef("crop"), Ast.lit(0));
        loop.body.add(new ExprStmt(new StringIntMapCall("counts", StringIntMapCall.Op.PUT, new VarRef("crop"),
                new Binary(Binary.Op.ADD, prior, Ast.lit(1)))));
        loop.body.add(Ast.harvest());
        loop.body.add(Ast.move(Direction.EAST));
        var check = Ast.ifStmt(Ast.eq(new StringIntMapCall("counts", StringIntMapCall.Op.GET_OR_DEFAULT,
                Ast.lit("wheat"), Ast.lit(0)), 2), false);
        check.thenBlock.add(Ast.walkTo(Ast.lit(3), Ast.lit(0)));
        return program(counts, loop, check);
    }

    private static Block array() {
        var route = new VarDecl(JType.INT_ARRAY, "route", new IntArrayLiteral(Ast.lit(1), Ast.lit(3)));
        var visit = new ForEachStmt(JType.INT, "column", "route");
        visit.body.add(Ast.walkTo(new VarRef("column"), Ast.lit(0)));
        visit.body.add(Ast.harvest());
        return program(route, visit);
    }

    private static Block grid() {
        var field = new VarDecl(JType.INT_GRID, "field", new NewIntGrid(Ast.lit(2), Ast.lit(1)));
        var first = new ArrayAssign("field", Ast.lit(0), Ast.lit(0), Ast.lit(1));
        var second = new ArrayAssign("field", Ast.lit(1), Ast.lit(0), Ast.lit(2));
        var route = Ast.forCount("i", 0);
        ((Binary) route.cond.get()).right.set(new Access(Access.Kind.GRID_ROWS, "field"));
        route.body.add(Ast.walkTo(new Access(Access.Kind.GRID_INDEX, "field", new VarRef("i"), Ast.lit(0)),
                new VarRef("i")));
        route.body.add(Ast.harvest());
        return program(field, first, second, route);
    }

    private static Block countdown() {
        var steps = new VarDecl(JType.INT, "steps", Ast.lit(3));
        var loop = new WhileStmt(new Binary(Binary.Op.GT, new VarRef("steps"), Ast.lit(0)));
        loop.body.add(Ast.harvest());
        loop.body.add(Ast.move(Direction.WEST));
        loop.body.add(new CompoundAssign("steps", CompoundAssign.Operation.SUBTRACT, Ast.lit(1)));
        return program(steps, loop);
    }

    private static Block fallThrough() {
        var loop = Ast.forCount("i", 3);
        var choice = new SwitchStmt(new VarRef("i"));
        var first = choice.addCase(0);
        first.body.add(Ast.water());
        first.setFallThrough(true);
        choice.addCase(1).body.add(Ast.move(Direction.EAST));
        choice.addDefault().add(Ast.harvest());
        loop.body.add(choice);
        return program(loop);
    }

    private static Block crops() {
        var scan = new VarDecl(JType.CROP_ARRAY, "crops", Ast.scanCrops());
        var loop = Ast.forCount("i", 0);
        ((Binary) loop.cond.get()).right.set(new Access(Access.Kind.CROP_LENGTH, "crops"));
        var check = Ast.ifStmt(new CropEquals(new Access(Access.Kind.CROP_INDEX, "crops", new VarRef("i")),
                Ast.lit(Crop.WHEAT)), false);
        check.thenBlock.add(Ast.walkTo(new VarRef("i"), Ast.lit(0)));
        check.thenBlock.add(Ast.harvest());
        loop.body.add(check);
        return program(scan, loop);
    }

    private static Block program(Stmt... statements) {
        Block root = new Block();
        for (Stmt statement : statements) root.add(statement);
        return root;
    }

    private static Block applyFunction() {
        var absolute = new VarDecl(FunctionType.TRANSFORM.type, "absolute",
                new FunctionValue(FunctionType.TRANSFORM, new IntegerMethodRef(IntegerMethodRef.Kind.ABS)));
        var offset = new VarDecl(JType.INT, "offset", Ast.lit(0));
        var subtract = new CompoundAssign("offset", CompoundAssign.Operation.SUBTRACT, Ast.lit(3));
        return program(absolute, offset, subtract,
                Ast.walkTo(new FunctionCall("absolute", FunctionType.TRANSFORM, new VarRef("offset")), Ast.lit(0)),
                Ast.harvest());
    }

    private static Block createArrayFromReference() {
        var factory = new VarDecl(FunctionType.ARRAY_FACTORY.type, "factory",
                new FunctionValue(FunctionType.ARRAY_FACTORY, new IntegerMethodRef(IntegerMethodRef.Kind.ARRAY)));
        var route = new VarDecl(JType.INT_ARRAY, "route", new FunctionCall("factory", FunctionType.ARRAY_FACTORY,
                Ast.lit(2)));
        var visit = new ForEachStmt(JType.INT, "column", "route");
        visit.body.add(Ast.walkTo(new VarRef("column"), Ast.lit(0)));
        visit.body.add(Ast.harvest());
        return program(factory, route, new ArrayAssign("route", Ast.lit(0), Ast.lit(1)),
                new ArrayAssign("route", Ast.lit(1), Ast.lit(4)), visit);
    }
}
