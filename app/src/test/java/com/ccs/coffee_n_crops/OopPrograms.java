package com.ccs.coffee_n_crops;

import com.ccs.coffee_n_crops.farm.Direction;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.ClassDecl;
import com.ccs.coffee_n_crops.lang.ast.ConstructorDecl;
import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.MethodDecl;
import com.ccs.coffee_n_crops.lang.ast.expr.Binary;
import com.ccs.coffee_n_crops.lang.ast.expr.FarmhandRef;
import com.ccs.coffee_n_crops.lang.ast.expr.FieldAccess;
import com.ccs.coffee_n_crops.lang.ast.expr.InstanceCall;
import com.ccs.coffee_n_crops.lang.ast.expr.Literal;
import com.ccs.coffee_n_crops.lang.ast.expr.NewObject;
import com.ccs.coffee_n_crops.lang.ast.expr.ThisRef;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.ExprStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.FieldAssign;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.IfStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.SuperCall;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.ast.stmt.WhileStmt;
import com.ccs.coffee_n_crops.lang.types.JType;

/** Complete player-built programs used to verify the four OOP lessons. */
final class OopPrograms {
    private static final int COUNTER_WHEAT = 5;
    private static final int WORKER_COLUMNS = 6;
    private static final int WORKER_ROWS = 2;

    private OopPrograms() { }

    static FieldAccess field(String receiver, String cls, String field, JType type) {
        return new FieldAccess(new VarRef(receiver), JType.reference(cls), field, type);
    }

    static FieldAccess own(String cls, String field, JType type) {
        return new FieldAccess(new ThisRef(), JType.reference(cls), field, type);
    }

    static InstanceCall farmer(Expr receiver, String name, JType result, JType[] parameters, Expr... args) {
        return new InstanceCall(receiver, JType.FARMHAND, name, result, parameters, args);
    }

    static ClassDecl counter() {
        ClassDecl counter = new ClassDecl("Counter");
        counter.addField(Ast.declare(JType.INT, "value"));
        return counter;
    }

    static Block fields() {
        Block program = new Block();
        ClassDecl counter = counter();
        program.addClass(counter);
        program.add(new VarDecl(JType.reference(counter.name), "counter", new NewObject(counter)));
        WhileStmt loop = new WhileStmt(new Binary(Binary.Op.LT,
                field("counter", "Counter", "value", JType.INT), Ast.lit(COUNTER_WHEAT)));
        loop.body.add(Ast.harvest());
        loop.body.add(new FieldAssign(field("counter", "Counter", "value", JType.INT),
                new Binary(Binary.Op.ADD, field("counter", "Counter", "value", JType.INT), Ast.lit(1))));
        loop.body.add(Ast.move(Direction.EAST));
        program.add(loop);
        return program;
    }

    static Block constructors() {
        Block program = new Block();
        ClassDecl plan = new ClassDecl("RowPlan");
        plan.addField(Ast.declare(JType.INT, "row"));
        plan.addField(Ast.declare(JType.INT, "width"));
        ConstructorDecl constructor = new ConstructorDecl(Ast.declare(JType.INT, "row"),
                Ast.declare(JType.INT, "width"));
        constructor.body.add(new FieldAssign(own(plan.name, "row", JType.INT), new VarRef("row")));
        constructor.body.add(new FieldAssign(own(plan.name, "width", JType.INT), new VarRef("width")));
        plan.setConstructor(constructor);
        program.addClass(plan);
        program.add(new VarDecl(JType.reference(plan.name), "first", new NewObject(plan, Ast.lit(0), Ast.lit(3))));
        program.add(new VarDecl(JType.reference(plan.name), "second", new NewObject(plan, Ast.lit(1), Ast.lit(5))));
        addPlanLoop(program, "first", "i");
        addPlanLoop(program, "second", "j");
        return program;
    }

    private static void addPlanLoop(Block program, String name, String variable) {
        ForStmt loop = Ast.forCount(variable, 0);
        ((Binary) loop.cond.get()).right.set(field(name, "RowPlan", "width", JType.INT));
        loop.body.add(Ast.walkTo(new VarRef(variable), field(name, "RowPlan", "row", JType.INT)));
        loop.body.add(Ast.harvest());
        program.add(loop);
    }

    static ClassDecl worker(boolean withRow) {
        ClassDecl worker = new ClassDecl("Worker");
        worker.addField(new VarDecl(JType.FARMHAND, "farmer", new Literal(JType.FARMHAND, null)));
        if (withRow) worker.addField(Ast.declare(JType.INT, "row"));
        VarDecl farmer = new VarDecl(JType.FARMHAND, "farmer", new Literal(JType.FARMHAND, null));
        ConstructorDecl constructor = withRow
                ? new ConstructorDecl(farmer, Ast.declare(JType.INT, "row")) : new ConstructorDecl(farmer);
        constructor.body.add(new FieldAssign(own(worker.name, "farmer", JType.FARMHAND), new VarRef("farmer")));
        if (withRow) constructor.body.add(new FieldAssign(own(worker.name, "row", JType.INT), new VarRef("row")));
        worker.setConstructor(constructor);
        return worker;
    }

    static Block methods() {
        Block program = new Block();
        ClassDecl worker = worker(true);
        MethodDecl work = new MethodDecl("work", JType.VOID);
        ForStmt columns = Ast.forCount("i", WORKER_COLUMNS);
        columns.body.add(new ExprStmt(farmer(own(worker.name, "farmer", JType.FARMHAND), "walkTo", JType.VOID,
                new JType[]{JType.INT, JType.INT}, new VarRef("i"), own(worker.name, "row", JType.INT))));
        IfStmt tend = Ast.ifStmt(farmer(own(worker.name, "farmer", JType.FARMHAND), "isRipe", JType.BOOLEAN,
                new JType[0]), false);
        tend.thenBlock.add(new ExprStmt(farmer(own(worker.name, "farmer", JType.FARMHAND), "harvest", JType.VOID,
                new JType[0])));
        tend.addElseIf(farmer(own(worker.name, "farmer", JType.FARMHAND), "isDry", JType.BOOLEAN,
                new JType[0])).block.add(new ExprStmt(farmer(own(worker.name, "farmer", JType.FARMHAND),
                "water", JType.VOID, new JType[0])));
        columns.body.add(tend);
        work.body.add(columns);
        worker.addMethod(work);
        program.addClass(worker);
        ForStmt rows = Ast.forCount("i", WORKER_ROWS);
        rows.body.add(new VarDecl(JType.reference(worker.name), "worker",
                new NewObject(worker, new FarmhandRef(), new VarRef("i"))));
        rows.body.add(new ExprStmt(new InstanceCall(new VarRef("worker"), JType.reference(worker.name), work)));
        program.add(rows);
        return program;
    }

    static Block inheritance() {
        Block program = new Block();
        ClassDecl worker = worker(false);
        MethodDecl baseWork = new MethodDecl("work", JType.VOID);
        worker.addMethod(baseWork);
        ClassDecl harvester = new ClassDecl("Harvester", worker.name);
        ConstructorDecl constructor = new ConstructorDecl(new VarDecl(JType.FARMHAND, "farmer",
                new Literal(JType.FARMHAND, null)));
        constructor.body.add(new SuperCall(worker.constructor(), new VarRef("farmer")));
        harvester.setConstructor(constructor);
        MethodDecl work = new MethodDecl("work", JType.VOID);
        work.override = true;
        ForStmt loop = Ast.forCount("i", WORKER_COLUMNS);
        loop.body.add(new ExprStmt(farmer(own(harvester.name, "farmer", JType.FARMHAND), "harvest", JType.VOID,
                new JType[0])));
        loop.body.add(new ExprStmt(farmer(own(harvester.name, "farmer", JType.FARMHAND), "move", JType.VOID,
                new JType[]{JType.DIRECTION}, new Literal(JType.DIRECTION, Direction.EAST))));
        work.body.add(loop);
        harvester.addMethod(work);
        program.addClass(worker);
        program.addClass(harvester);
        program.add(new VarDecl(JType.reference(worker.name), "worker", new NewObject(harvester, new FarmhandRef())));
        program.add(new ExprStmt(new InstanceCall(new VarRef("worker"), JType.reference(worker.name), baseWork)));
        return program;
    }
}
