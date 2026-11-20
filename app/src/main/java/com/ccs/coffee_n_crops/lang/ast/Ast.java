package com.ccs.coffee_n_crops.lang.ast;

import com.ccs.coffee_n_crops.farm.Direction;
import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.lang.ast.expr.Binary;
import com.ccs.coffee_n_crops.lang.ast.expr.Literal;
import com.ccs.coffee_n_crops.lang.ast.expr.MethodCall;
import com.ccs.coffee_n_crops.lang.ast.expr.NewList;
import com.ccs.coffee_n_crops.lang.ast.expr.PostIncrement;
import com.ccs.coffee_n_crops.lang.ast.expr.StrEquals;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.Assign;
import com.ccs.coffee_n_crops.lang.ast.stmt.ExprStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.IfStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.SwitchStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.types.JType;

/** Small factory for the node shapes the block palette inserts. */
public final class Ast {
    private Ast() {
    }

    public static Literal lit(int value) {
        return new Literal(JType.INT, value);
    }

    public static Literal lit(long value) {
        return new Literal(JType.LONG, value);
    }

    public static Literal lit(double value) {
        return new Literal(JType.DOUBLE, value);
    }

    public static Literal lit(String value) {
        return new Literal(JType.STRING, value);
    }

    public static Literal lit(boolean value) {
        return new Literal(JType.BOOLEAN, value);
    }

    /** {@code farmer.move(Direction.X);} */
    public static ExprStmt move(Direction direction) {
        return new ExprStmt(new MethodCall("farmer", "move", new Literal(JType.DIRECTION, direction)));
    }

    /** {@code farmer.harvest();} */
    public static ExprStmt harvest() {
        return new ExprStmt(new MethodCall("farmer", "harvest"));
    }

    /** {@code farmer.water();} */
    public static ExprStmt water() {
        return new ExprStmt(new MethodCall("farmer", "water"));
    }

    /** {@code farmer.isRipe()} */
    public static MethodCall isRipe() {
        return new MethodCall("farmer", "isRipe");
    }

    /** {@code for (int v = 0; v < count; v++) { }} */
    public static ForStmt forCount(String var, int count) {
        return new ForStmt(
                new VarDecl(JType.INT, var, lit(0)),
                new Binary(Binary.Op.LT, new VarRef(var), lit(count)),
                new PostIncrement(var));
    }

    /** {@code int name = 0;} or {@code boolean name = false;} */
    public static VarDecl declare(JType type, String name) {
        return new VarDecl(type, name, defaultValue(type));
    }

    public static Assign assign(VarDecl variable, Expr value) {
        return new Assign(variable.type, variable.name, value);
    }

    public static IfStmt ifStmt(Expr cond, boolean withElse) {
        return new IfStmt(cond, withElse);
    }

    /** {@code farmer.isDry()} */
    public static MethodCall isDry() {
        return new MethodCall("farmer", "isDry");
    }

    /** {@code farmer.canMove(Direction.X)} */
    public static MethodCall canMove(Direction direction) {
        return new MethodCall("farmer", "canMove", new Literal(JType.DIRECTION, direction));
    }

    /** {@code switch (subject) { case 0: break; case 1: break; default: break; }} */
    public static SwitchStmt switchStmt(Expr subject) {
        SwitchStmt s = new SwitchStmt(subject);
        s.addCase(0);
        s.addCase(1);
        s.addDefault();
        return s;
    }

    /** {@code farmer.walkTo(x, y);} */
    public static ExprStmt walkTo(Expr x, Expr y) {
        return new ExprStmt(new MethodCall("farmer", "walkTo", x, y));
    }

    /** {@code int[] name = farmer.scanRow();} */
    public static VarDecl scanRow(String name) {
        return new VarDecl(JType.INT_ARRAY, name, new MethodCall("farmer", "scanRow"));
    }

    /** {@code int[][] name = farmer.scanField();} */
    public static VarDecl scanField(String name) {
        return new VarDecl(JType.INT_GRID, name, new MethodCall("farmer", "scanField"));
    }

    /** {@code ArrayList<Integer> name = new ArrayList<>();} */
    public static VarDecl newList(String name) {
        return new VarDecl(JType.INT_LIST, name, new NewList());
    }

    /** {@code farmer.refill();} */
    public static ExprStmt refill() {
        return new ExprStmt(new MethodCall("farmer", "refill"));
    }

    /** {@code farmer.getWater()} */
    public static MethodCall getWater() {
        return new MethodCall("farmer", "getWater");
    }

    /** {@code farmer.getCrop()} */
    public static MethodCall getCrop() {
        return new MethodCall("farmer", "getCrop");
    }

    /** {@code farmer.getWater() < liters} */
    public static Binary waterBelow(double liters) {
        return new Binary(Binary.Op.LT, JType.DOUBLE, getWater(), lit(liters));
    }

    /** {@code farmer.getCrop().equals(name)} */
    public static StrEquals cropIs(String name) {
        return new StrEquals(getCrop(), lit(name));
    }

    /** {@code switch (farmer.getCrop()) { case "wheat": break; case "sprout": break; default: break; }} */
    public static SwitchStmt switchOnCrop() {
        SwitchStmt s = new SwitchStmt(JType.STRING, getCrop());
        s.addCase(FarmWorld.CROP_WHEAT);
        s.addCase(FarmWorld.CROP_SPROUT);
        s.addDefault();
        return s;
    }

    /** {@code left == right} */
    public static Binary eq(Expr left, int right) {
        return new Binary(Binary.Op.EQ, left, lit(right));
    }

    public static Literal defaultValue(JType type) {
        if (type == JType.INT) return lit(0);
        if (type == JType.LONG) return lit(0L);
        if (type == JType.BOOLEAN) return lit(false);
        if (type == JType.DOUBLE) return lit(0.0);
        if (type == JType.STRING) return lit(FarmWorld.CROP_NAMES[0]);
        if (type == JType.DIRECTION) return new Literal(JType.DIRECTION, Direction.EAST);
        if (type.isReference()) return new Literal(JType.NULL, null);
        throw new IllegalArgumentException("No default value for " + type.javaName);
    }
}
