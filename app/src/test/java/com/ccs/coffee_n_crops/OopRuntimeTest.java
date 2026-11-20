package com.ccs.coffee_n_crops;

import static org.junit.Assert.*;

import com.ccs.coffee_n_crops.farm.Direction;
import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.farm.WorldClock;
import com.ccs.coffee_n_crops.lang.ast.*;
import com.ccs.coffee_n_crops.lang.ast.expr.*;
import com.ccs.coffee_n_crops.lang.ast.stmt.*;
import com.ccs.coffee_n_crops.lang.check.Validator;
import com.ccs.coffee_n_crops.lang.interp.*;
import com.ccs.coffee_n_crops.lang.runtime.ExceptionTypes;
import com.ccs.coffee_n_crops.lang.types.JType;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Java ordering, aliasing, field hiding and cancellation across interpreted object frames. */
public class OopRuntimeTest {
    private static VarDecl parameter(JType type, String name) { return new VarDecl(type, name, Ast.defaultValue(type)); }
    private static FieldAccess own(ClassDecl cls, String name, JType type) { return new FieldAccess(new ThisRef(), cls.type(), name, type); }
    private static FieldAccess field(String variable, ClassDecl cls, String name, JType type) { return new FieldAccess(new VarRef(variable), cls.type(), name, type); }
    private static ExprStmt invoke(Expr receiver, ClassDecl cls, MethodDecl method, Expr... args) { return new ExprStmt(new InstanceCall(receiver, cls.type(), method, args)); }
    private static void harvestWhen(Block program, Expr condition) { IfStmt test=Ast.ifStmt(condition,false);test.thenBlock.add(Ast.harvest());program.add(test); }
    private static Expr both(Expr first, Expr second) { return new Binary(Binary.Op.AND,first,second); }
    private static String run(Block program, FarmWorld world, List<String> snapshots) throws InterruptedException {
        Validator.Problem problem=Validator.check(program);
        assertNull(problem==null?null:problem.message,problem);
        CountDownLatch done=new CountDownLatch(1);String[] error=new String[1];
        Interpreter interpreter=new Interpreter(new WorldClock(0));
        try {
            interpreter.start(program,world,new ExecListener(){
                @Override public void onNodeEnter(int farmerId,int nodeId) { }
                @Override public void onFinished(int farmerId){done.countDown();}
                @Override public void onError(int farmerId,int nodeId,String message){error[0]=message;done.countDown();}
                @Override public void onObjectState(int farmerId,String snapshot){snapshots.add(snapshot);}
            },false);
            assertTrue(done.await(5,TimeUnit.SECONDS));return error[0];
        } finally {interpreter.stop();}
    }
    private static String run(Block program,FarmWorld world)throws InterruptedException{return run(program,world,new ArrayList<>());}

    @Test public void superclassBodyRunsBeforeDerivedInitializersAndDerivedBody() throws Exception {
        ClassDecl base=new ClassDecl("Base");base.addField(Ast.declare(JType.INT,"value"));
        ConstructorDecl parent=new ConstructorDecl(parameter(JType.INT,"start"));
        parent.body.add(new FieldAssign(own(base,"value",JType.INT),new VarRef("start")));base.setConstructor(parent);
        ClassDecl child=new ClassDecl("Child","Base");child.addField(new VarDecl(JType.INT,"copy",own(child,"value",JType.INT)));
        ConstructorDecl ctor=new ConstructorDecl();ctor.body.add(new SuperCall(parent,Ast.lit(2)));
        ctor.body.add(new FieldAssign(own(child,"value",JType.INT),Ast.lit(3)));child.setConstructor(ctor);
        Block program=new Block();program.addClass(base);program.addClass(child);
        program.add(new VarDecl(child.type(),"child",new NewObject(child)));
        harvestWhen(program,both(Ast.eq(field("child",child,"copy",JType.INT),2),Ast.eq(field("child",child,"value",JType.INT),3)));
        FarmWorld world=new FarmWorld(new String[]{"W"},0,0);assertNull(run(program,world));assertEquals(1,world.wheatHarvested());
    }

    @Test public void fieldHidingUsesDeclaredReceiverAndInheritedMethodUsesItsDeclaringClass() throws Exception {
        ClassDecl base=new ClassDecl("Base");base.addField(new VarDecl(JType.INT,"value",Ast.lit(1)));
        MethodDecl get=new MethodDecl("get",JType.INT);get.body.add(new ReturnStmt(JType.INT,own(base,"value",JType.INT)));base.addMethod(get);
        ClassDecl child=new ClassDecl("Child","Base");child.addField(new VarDecl(JType.INT,"value",Ast.lit(2)));
        Block program=new Block();program.addClass(base);program.addClass(child);
        program.add(new VarDecl(child.type(),"child",new NewObject(child)));program.add(new VarDecl(base.type(),"base",new VarRef("child")));
        // The receiver slot accepts Base, but child.value is Java's Child field.
        Expr childRead=new FieldAccess(new VarRef("child"),base.type(),"value",JType.INT);
        Expr inherited=new InstanceCall(new VarRef("child"),child.type(),get);
        harvestWhen(program,both(Ast.eq(childRead,2),both(Ast.eq(field("base",base,"value",JType.INT),1),Ast.eq(inherited,1))));
        FarmWorld world=new FarmWorld(new String[]{"W"},0,0);assertNull(run(program,world));assertEquals(1,world.wheatHarvested());
    }

    @Test public void constructorArgumentsEvaluateInCallerBeforeParameterFrameExists() throws Exception {
        ClassDecl counter=new ClassDecl("Counter");counter.addField(Ast.declare(JType.INT,"value"));
        ConstructorDecl constructor=new ConstructorDecl(parameter(JType.INT,"start"));
        constructor.body.add(new FieldAssign(own(counter,"value",JType.INT),new VarRef("start")));counter.setConstructor(constructor);
        Block program=new Block();program.addClass(counter);program.add(new VarDecl(JType.INT,"start",Ast.lit(3)));
        program.add(new VarDecl(counter.type(),"counter",new NewObject(counter,new PostIncrement("start"))));
        harvestWhen(program,both(Ast.eq(field("counter",counter,"value",JType.INT),3),Ast.eq(new VarRef("start"),4)));
        FarmWorld world=new FarmWorld(new String[]{"W"},0,0);assertNull(run(program,world));assertEquals(1,world.wheatHarvested());
    }

    @Test public void nullMethodReceiverStillEvaluatesArgumentsBeforeThrowing() throws Exception {
        ClassDecl counter=new ClassDecl("Counter");MethodDecl accept=new MethodDecl("accept",JType.VOID,parameter(JType.INT,"x"));counter.addMethod(accept);
        MethodDecl side=new MethodDecl("side",JType.INT);side.body.add(Ast.harvest());side.body.add(new ReturnStmt(JType.INT,Ast.lit(1)));
        Block program=new Block();program.addClass(counter);program.addMethod(side);
        program.add(new VarDecl(counter.type(),"counter",new Literal(JType.NULL,null)));
        program.add(invoke(new VarRef("counter"),counter,accept,new UserCall(side)));
        FarmWorld world=new FarmWorld(new String[]{"W"},0,0);assertEquals("NullPointerException",run(program,world));assertEquals(1,world.wheatHarvested());
    }

    @Test public void assignmentCapturesObjectReceiverBeforeRightHandSideChangesItsAlias() throws Exception {
        ClassDecl counter=new ClassDecl("Counter");counter.addField(Ast.declare(JType.INT,"value"));
        ClassDecl box=new ClassDecl("Box");box.addField(new VarDecl(counter.type(),"target",new Literal(JType.NULL,null)));
        MethodDecl redirect=new MethodDecl("redirect",JType.INT,parameter(box.type(),"box"),parameter(counter.type(),"next"));
        redirect.body.add(new FieldAssign(field("box",box,"target",counter.type()),new VarRef("next")));redirect.body.add(new ReturnStmt(JType.INT,Ast.lit(9)));
        Block program=new Block();program.addClass(counter);program.addClass(box);program.addMethod(redirect);
        program.add(new VarDecl(counter.type(),"first",new NewObject(counter)));program.add(new VarDecl(counter.type(),"second",new NewObject(counter)));program.add(new VarDecl(box.type(),"box",new NewObject(box)));
        program.add(new FieldAssign(field("box",box,"target",counter.type()),new VarRef("first")));
        FieldAccess target=new FieldAccess(field("box",box,"target",counter.type()),counter.type(),"value",JType.INT);
        program.add(new FieldAssign(target,new UserCall(redirect,new VarRef("box"),new VarRef("second"))));
        harvestWhen(program,both(Ast.eq(field("first",counter,"value",JType.INT),9),Ast.eq(field("second",counter,"value",JType.INT),0)));
        FarmWorld world=new FarmWorld(new String[]{"W"},0,0);assertNull(run(program,world));assertEquals(1,world.wheatHarvested());
    }

    @Test public void covariantReturnDispatchDoesNotChangeStaticFieldSelection() throws Exception {
        ClassDecl base=new ClassDecl("Base");base.addField(new VarDecl(JType.INT,"value",Ast.lit(1)));
        ClassDecl child=new ClassDecl("Child","Base");child.addField(new VarDecl(JType.INT,"value",Ast.lit(2)));
        ClassDecl maker=new ClassDecl("Maker");MethodDecl make=new MethodDecl("make",base.type());make.body.add(new ReturnStmt(base.type(),new NewObject(base)));maker.addMethod(make);
        ClassDecl better=new ClassDecl("BetterMaker","Maker");MethodDecl override=new MethodDecl("make",child.type());override.override=true;override.body.add(new ReturnStmt(child.type(),new NewObject(child)));better.addMethod(override);
        Block program=new Block();program.addClass(base);program.addClass(child);program.addClass(maker);program.addClass(better);
        program.add(new VarDecl(maker.type(),"maker",new NewObject(better)));
        Expr made=new InstanceCall(new VarRef("maker"),maker.type(),make);
        harvestWhen(program,Ast.eq(new FieldAccess(made,base.type(),"value",JType.INT),1));
        FarmWorld world=new FarmWorld(new String[]{"W"},0,0);assertNull(run(program,world));assertEquals(1,world.wheatHarvested());
    }

    @Test public void objectMethodFailureUnwindsToOutsideCatchAndFinally() throws Exception {
        ClassDecl worker=OopPrograms.worker(false);MethodDecl work=new MethodDecl("work",JType.VOID);
        work.body.add(new ExprStmt(OopPrograms.farmer(own(worker,"farmer",JType.FARMHAND),"move",JType.VOID,new JType[]{JType.DIRECTION},new Literal(JType.DIRECTION,Direction.EAST))));worker.addMethod(work);
        Block program=new Block();program.addClass(worker);program.add(new VarDecl(worker.type(),"worker",new NewObject(worker,new FarmhandRef())));
        TryStmt attempt=new TryStmt();attempt.tryBlock.add(invoke(new VarRef("worker"),worker,work));attempt.addCatch(ExceptionTypes.OUT_OF_BOUNDS,"e").body.add(Ast.harvest());attempt.addFinally().add(Ast.refill());program.add(attempt);
        FarmWorld world=new FarmWorld(new String[]{"W"},0,0);assertNull(run(program,world));assertEquals(1,world.wheatHarvested());assertEquals(1,world.finallyRuns());assertEquals(1,world.refills());
    }

    @Test public void objectSnapshotTerminatesForSelfReference() throws Exception {
        ClassDecl link=new ClassDecl("Link");link.addField(new VarDecl(link.type(),"next",new Literal(JType.NULL,null)));
        Block program=new Block();program.addClass(link);program.add(new VarDecl(link.type(),"link",new NewObject(link)));
        program.add(new FieldAssign(field("link",link,"next",link.type()),new VarRef("link")));
        List<String> snapshots=new ArrayList<>();assertNull(run(program,new FarmWorld(new String[]{"."},0,0),snapshots));
        assertTrue(snapshots.stream().anyMatch(s->s.contains("next = Link#1")));assertTrue(snapshots.stream().allMatch(s->s.length()<200));
    }

    @Test public void resetDuringPausedConstructorUnwindsBeforeAnyDroneAction() throws Exception {
        ClassDecl builder=new ClassDecl("Builder");ConstructorDecl constructor=new ConstructorDecl(parameter(JType.FARMHAND,"farmer"));constructor.body.add(Ast.move(Direction.EAST));builder.setConstructor(constructor);
        Block program=new Block();program.addClass(builder);program.add(new VarDecl(builder.type(),"builder",new NewObject(builder,new FarmhandRef())));
        assertNull(Validator.check(program));FarmWorld world=new FarmWorld(new String[]{"W."},0,0);Interpreter interpreter=new Interpreter(new WorldClock(0));CountDownLatch entering=new CountDownLatch(1);Thread[] old=new Thread[1];
        try {
            interpreter.start(program,world,new ExecListener(){
                @Override public void onNodeEnter(int farmerId,int nodeId){if(nodeId==constructor.id){old[0]=Thread.currentThread();entering.countDown();}}
                @Override public void onFinished(int farmerId){fail("canceled constructor finished");}
                @Override public void onError(int farmerId,int nodeId,String message){fail(message);}
            },true);
            interpreter.debug.step();assertTrue(entering.await(5,TimeUnit.SECONDS));interpreter.stop();world.reset();
            old[0].join(1000);assertFalse(old[0].isAlive());Block fresh=new Block();fresh.add(Ast.harvest());assertNull(run(fresh,world));assertEquals(0,world.farmer.pose().x);assertEquals(1,world.wheatHarvested());assertEquals(0,world.objectCreations("Builder"));
        } finally {interpreter.stop();}
    }
}
