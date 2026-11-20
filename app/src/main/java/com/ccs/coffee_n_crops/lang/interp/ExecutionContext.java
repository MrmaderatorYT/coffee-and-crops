package com.ccs.coffee_n_crops.lang.interp;

import com.ccs.coffee_n_crops.farm.Farmhand;
import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.farm.WorldClock;
import com.ccs.coffee_n_crops.lang.ast.MethodDecl;
import com.ccs.coffee_n_crops.lang.ast.ClassDecl;
import com.ccs.coffee_n_crops.lang.ast.ConstructorDecl;
import com.ccs.coffee_n_crops.lang.ast.Node;
import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.expr.ThisRef;
import com.ccs.coffee_n_crops.lang.ast.expr.FarmhandRef;
import com.ccs.coffee_n_crops.lang.ast.expr.NewObject;
import com.ccs.coffee_n_crops.lang.ast.expr.NewReentrantLock;
import com.ccs.coffee_n_crops.lang.ast.expr.ObjectArrayGet;
import com.ccs.coffee_n_crops.lang.ast.expr.StaticConstant;
import com.ccs.coffee_n_crops.lang.ast.expr.FieldAccess;
import com.ccs.coffee_n_crops.lang.ast.expr.InstanceCall;
import com.ccs.coffee_n_crops.lang.ast.expr.UserCall;
import com.ccs.coffee_n_crops.lang.ast.expr.MethodCall;
import com.ccs.coffee_n_crops.lang.ast.expr.Literal;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.TeamStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.ast.stmt.SuperCall;
import com.ccs.coffee_n_crops.lang.runtime.ClassTable;
import com.ccs.coffee_n_crops.lang.runtime.LockRegistry;
import com.ccs.coffee_n_crops.lang.runtime.ObjectInstance;
import com.ccs.coffee_n_crops.lang.runtime.ObjectArray;
import com.ccs.coffee_n_crops.lang.runtime.ObjectMethods;
import com.ccs.coffee_n_crops.lang.runtime.NativeRegistry;
import com.ccs.coffee_n_crops.lang.runtime.ExceptionTypes;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything one farmer thread needs to run its program. The world, clock and debugger are shared
 * among workers; the scope chain and current node belong to this thread alone.
 */
public final class ExecutionContext {
    public final Farmhand farmer;
    public final FarmWorld world;
    public final DebugController debug;
    public final WorldClock clock;
    public final NativeRegistry natives;
    public final ExecListener listener;

    /** Bound recursion before the host JVM stack is at risk. */
    public static final int MAX_CALL_DEPTH = 64;
    private static final int MAX_OBJECTS = 512;
    private static final int MAX_SNAPSHOT_OBJECTS = 8;
    private static final int MAX_OBJECT_ARRAY_LENGTH = 16_384;
    private static final int MAX_OBJECT_ARRAY_CELLS = 65_536;
    private static final int MAX_JOB_CHECKPOINTS = 128;
    private static final long JOB_TICK_MS = 1;
    private Scope scope = new Scope(null);
    private final Map<String, MethodDecl> methods = new HashMap<>();
    private int callDepth;
    private volatile Node currentNode;
    private int currentFarmerId;
    private volatile boolean active = true;
    private ClassTable classes = new ClassTable(new Block());
    private LockRegistry lockRegistry = new LockRegistry();
    private ObjectInstance receiver;
    private ClassDecl declaringClass;
    private ConstructorState constructor;
    private final List<ObjectInstance> objects = new ArrayList<>();
    private int allocatedArrayCells;
    private int plainObjects;
    private ExecutionContext jobOwner;
    private boolean teamWorker;
    private Set<Integer> jobUses;
    private int jobCheckpoints;

    private static final class ConstructorState {
        final ClassDecl declaration;
        final ObjectInstance object;
        boolean initialized;
        ConstructorState(ClassDecl declaration, ObjectInstance object) {
            this.declaration = declaration;
            this.object = object;
        }
    }

    private static final class Frame {
        Scope scope;
        ObjectInstance receiver;
        ClassDecl declaringClass;
        ConstructorState constructor;
        Node node;
    }

    public ExecutionContext(Farmhand farmer, FarmWorld world, DebugController debug, WorldClock clock,
                            NativeRegistry natives, ExecListener listener) {
        this.farmer = farmer;
        currentFarmerId = farmer.id;
        this.world = world;
        this.debug = debug;
        this.clock = clock;
        this.natives = natives;
        this.listener = listener;
        scope.declare("farmer", new Value(JType.FARMHAND, farmer));
    }

    /** Starts a fresh run without sharing local variables with a previous execution. */
    public void beginProgram(Block program) {
        methods.clear();
        for (MethodDecl method : program.methods()) methods.put(method.name, method);
        scope = new Scope(null);
        scope.declare("farmer", new Value(JType.FARMHAND, farmer));
        classes = new ClassTable(program);
        lockRegistry = new LockRegistry();
        receiver = null;
        declaringClass = null;
        constructor = null;
        objects.clear();
        plainObjects = 0;
        allocatedArrayCells = 0;
        callDepth = 0;
    }

    /** A helper sees its parameters and locals, never the caller's locals. */
    public Value callMethod(String name, Value[] arguments) throws InterruptedException {
        checkActive();
        MethodDecl method = methods.get(name);
        if (method == null) throw new ScriptError("cannot find symbol: method " + name);
        if (arguments.length != method.parameters.size()) {
            throw new ScriptError("method " + name + " cannot be applied to given types");
        }
        Frame caller = enterFrame(null, null);
        boolean completed = false;
        try {
            scope.declare("farmer", new Value(JType.FARMHAND, farmer));
            for (int i = 0; i < arguments.length; i++) {
                VarDecl parameter = method.parameters.get(i);
                scope.declare(parameter.name, convertValue(arguments[i], parameter.type));
            }
            try {
                method.body.exec(this);
            } catch (ReturnSignal returned) {
                Value result = convertValue(returned.value, method.returnType);
                completed = true;
                return result;
            }
            if (method.returnType != JType.VOID) throw new ScriptError("missing return statement");
            completed = true;
            return Value.VOID;
        } finally {
            leaveFrame(caller, completed);
        }
    }

    public Value convertValue(Value value, JType target) {
        if (target == JType.LONG && value.type == JType.INT) return Value.ofLong(value.asInt());
        if (target == JType.DOUBLE && (value.type == JType.INT || value.type == JType.LONG)) {
            return Value.ofDouble(value.asDouble());
        }
        if (!classes.assignable(value.type, target)) {
            throw new ScriptError("incompatible types: " + value.type.javaName
                    + " cannot be converted to " + target.javaName);
        }
        return value.type == target ? value : new Value(target, value.raw);
    }

    public ClassTable classes() { return classes; }

    public LockRegistry locks() { return lockRegistry; }

    /** Runs an interpreted lambda per worker with separate local bindings and shared references. */
    public void runTogether(Block first, Block second) throws InterruptedException {
        checkActive();
        // Yield briefly between interpreted statements even in a zero-tick test run.
        ExecutionContext left = jobContext(farmer, new WorldClock(JOB_TICK_MS), false);
        ExecutionContext right = jobContext(farmer, new WorldClock(JOB_TICK_MS), false);
        awaitJobs(left, right, () -> farmer.runTogether(() -> runJob(first, left), () -> runJob(second, right)));
    }

    /** Starts both farmhands with their own actor binding and the same captured object references. */
    public void workTogether(TeamStmt team) throws InterruptedException {
        checkActive();
        if (world.farmers().size() != 2 || farmer != world.farmer || jobOwner != null) {
            throw new ScriptError("workTogether() needs a level with two farmhands.");
        }
        ExecutionContext left = jobContext(farmer, clock, true);
        ExecutionContext right = jobContext(world.farmers().get(1), clock, true);
        listener.onTeamStarted();
        try {
            awaitJobs(left, right, () -> farmer.workTogether(
                    actor -> runFarmhand(team.first, team.firstFarmer, actor, left),
                    actor -> runFarmhand(team.second, team.secondFarmer, actor, right)));
        } finally {
            listener.onTeamFinished();
        }
    }

    private void awaitJobs(ExecutionContext left, ExecutionContext right, Runnable jobs) throws InterruptedException {
        try {
            jobs.run();
        } catch (JobFailure failure) {
            restoreCurrentNode(failure.at);
            currentFarmerId = failure.farmerId;
            throw failure.problem;
        }
        synchronized (world) {
            checkActive();
            for (int id : left.jobUses) world.recordLessonUse(id);
            for (int id : right.jobUses) world.recordLessonUse(id);
        }
        emitObjectState();
    }

    private ExecutionContext jobContext(Farmhand actor, WorldClock workerClock, boolean farmhandJob) {
        ExecutionContext job = new ExecutionContext(actor, world, debug, workerClock, natives, listener);
        job.scope = scope.capture();
        job.classes = classes;
        job.lockRegistry = lockRegistry;
        job.jobOwner = this;
        job.teamWorker = farmhandJob;
        job.jobUses = new HashSet<>();
        return job;
    }

    private static void runFarmhand(Block body, VarDecl parameter, Farmhand actor, ExecutionContext worker) {
        try {
            worker.scope.declare(parameter.name, new Value(JType.FARMHAND, actor));
            runJob(body, worker);
        } finally {
            worker.listener.onTeamFarmerFinished(actor.id);
        }
    }

    private static void runJob(Block body, ExecutionContext worker) {
        try {
            body.exec(worker);
        } catch (InterruptedException cancelled) {
            Thread.currentThread().interrupt();
        } catch (ReturnSignal returned) {
            if (!worker.teamWorker || returned.value.type != JType.VOID) {
                throw new JobFailure(worker, new ScriptError("incompatible types: unexpected return value"));
            }
        } catch (RuntimeException problem) {
            throw new JobFailure(worker, problem);
        }
    }

    private static final class JobFailure extends RuntimeException {
        final Node at;
        final int farmerId;
        final RuntimeException problem;

        JobFailure(ExecutionContext worker, RuntimeException problem) {
            super(problem);
            at = worker.currentNode();
            farmerId = worker.farmer.id;
            this.problem = problem;
        }
    }

    public Value newObjectArray(JType component, int length) throws InterruptedException {
        checkActive();
        if (length < 0) throw new ScriptError("NegativeArraySizeException: " + length);
        if (length > MAX_OBJECT_ARRAY_LENGTH) {
            throw new ScriptError("OutOfMemoryError: Requested array size exceeds VM limit");
        }
        // A class can retain every array in a route. Bound the whole run as well as each allocation.
        int cells = Math.max(1, length);
        if (cells > MAX_OBJECT_ARRAY_CELLS - allocatedArrayCells) {
            throw new ScriptError("OutOfMemoryError: Java heap space");
        }
        allocatedArrayCells += cells;
        return new Value(JType.objectArray(component), new ObjectArray(component, length));
    }

    private Frame enterFrame(ObjectInstance object, ClassDecl owner) throws InterruptedException {
        checkActive();
        if (callDepth >= MAX_CALL_DEPTH) throw new ScriptError("StackOverflowError");
        Frame previous = new Frame();
        previous.scope = scope;
        previous.receiver = receiver;
        previous.declaringClass = declaringClass;
        previous.constructor = constructor;
        previous.node = currentNode;
        scope = new Scope(null);
        receiver = object;
        declaringClass = owner;
        constructor = null;
        callDepth++;
        return previous;
    }

    private void leaveFrame(Frame previous, boolean completed) {
        scope = previous.scope;
        receiver = previous.receiver;
        declaringClass = previous.declaringClass;
        constructor = previous.constructor;
        callDepth--;
        if (completed) currentNode = previous.node;
    }

    /** Evaluates new without creating any additional farm actor or Java thread. */
    public Value newObject(String name, Value[] arguments) throws InterruptedException {
        checkActive();
        if (objects.size() + plainObjects >= MAX_OBJECTS) throw new ScriptError("OutOfMemoryError: Java heap space");
        if (name.equals(JType.OBJECT.javaName)) {
            if (arguments.length != 0) throw new ScriptError("constructor Object cannot be applied to given types");
            plainObjects++;
            return new Value(JType.OBJECT, new Object());
        }
        ClassDecl declaration = classes.findClass(name);
        if (declaration == null) throw new ScriptError("cannot find symbol: class " + name);
        if (declaration.isInterface) throw new ScriptError(name + " is abstract; cannot be instantiated");
        ObjectInstance object = new ObjectInstance(declaration, objects.size() + 1, classes);
        objects.add(object);
        synchronized (world) {
            checkActive();
            world.recordObjectCreated(name);
        }
        construct(object, declaration, arguments);
        emitObjectState();
        return new Value(declaration.type(), object);
    }

    private void construct(ObjectInstance object, ClassDecl declaration, Value[] arguments)
            throws InterruptedException {
        ConstructorDecl method = declaration.constructor();
        int expected = method == null ? 0 : method.parameters.size();
        if (arguments.length != expected) throw new ScriptError("constructor " + declaration.name
                + " cannot be applied to given types");
        Frame previous = enterFrame(object, declaration);
        boolean completed = false;
        try {
            constructor = new ConstructorState(declaration, object);
            if (method != null) {
                for (int i = 0; i < arguments.length; i++) {
                    VarDecl parameter = method.parameters.get(i);
                    scope.declare(parameter.name, convertValue(arguments[i], parameter.type));
                }
                checkpoint(method);
                clock.awaitTicks(VarDecl.THINK_TICKS);
            } else checkpoint(declaration);
            boolean explicitSuper = method != null && method.body.size() > 0
                    && method.body.get(0) instanceof SuperCall;
            if (!explicitSuper) callSuper(new Value[0]);
            try {
                if (method != null) method.body.exec(this);
            } catch (ReturnSignal returned) {
                if (returned.value.type != JType.VOID) throw new ScriptError("incompatible types: unexpected return value");
            }
            completed = true;
        } finally {
            leaveFrame(previous, completed);
        }
    }

    /** Invoked only by the first constructor statement, or its implicit no-argument equivalent. */
    public void callSuper(Value[] arguments) throws InterruptedException {
        checkActive();
        ConstructorState state = constructor;
        if (state == null || state.initialized) throw new ScriptError("call to super must be first statement in constructor");
        state.initialized = true;
        ClassDecl base = classes.findClass(state.declaration.superName);
        if (state.declaration.superName != null && base == null) {
            throw new ScriptError("cannot find symbol: class " + state.declaration.superName);
        }
        if (base != null) {
            synchronized (world) {
                checkActive();
                world.recordSuperCall(state.declaration.name);
            }
            construct(state.object, base, arguments);
        } else if (arguments.length != 0) throw new ScriptError("constructor Object cannot be applied to given types");
        initializeFields(state.object, state.declaration);
    }

    private void initializeFields(ObjectInstance object, ClassDecl declaration) throws InterruptedException {
        Scope parameters = scope;
        scope = new Scope(null); // Field initializers cannot read a constructor's parameters.
        try {
            for (VarDecl field : declaration.fields()) {
                if (field.isStatic) continue;
                checkpoint(field);
                clock.awaitTicks(VarDecl.THINK_TICKS);
                object.set(field, convertValue(field.init.eval(this), field.type));
            }
        } finally {
            scope = parameters;
        }
    }

    public Value callInstance(Value target, String name, Value[] arguments, int callId) throws InterruptedException {
        checkActive();
        if (target.raw == null) throw new ScriptError("NullPointerException");
        if (!(target.raw instanceof ObjectInstance) && ObjectMethods.contains(name)) {
            return ObjectMethods.invoke(target, name, arguments);
        }
        if (target.type == JType.FARMHAND) {
            if (target.raw != farmer) throw new ScriptError("IllegalArgumentException: unknown farmer");
            return natives.lookup("farmer", name).impl.call(this, arguments);
        }
        ObjectInstance object = object(target);
        MethodDecl method = classes.method(object.declaration.type(), name);
        if (method == null && name.equals("toString") && arguments.length == 0) {
            // Java's default toString uses the object's virtual hashCode().
            int hash = callInstance(target, "hashCode", arguments, callId).asInt();
            return Value.ofString(object.declaration.name + "@" + Integer.toHexString(hash));
        }
        if (method == null) return ObjectMethods.invoke(target, name, arguments);
        if (arguments.length != method.parameters.size()) throw new ScriptError("method " + name + " cannot be applied to given types");
        Frame previous = enterFrame(object, (ClassDecl) method.parent);
        boolean completed = false;
        try {
            for (int i = 0; i < arguments.length; i++) {
                VarDecl parameter = method.parameters.get(i);
                scope.declare(parameter.name, convertValue(arguments[i], parameter.type));
            }
            synchronized (world) {
                checkActive();
                world.recordInstanceCall(object.declaration.name, name, callId);
            }
            checkpoint(method);
            clock.awaitTicks(VarDecl.THINK_TICKS);
            try {
                method.body.exec(this);
            } catch (ReturnSignal returned) {
                Value result = convertValue(returned.value, method.returnType);
                completed = true;
                emitObjectState();
                return result;
            }
            if (method.returnType != JType.VOID) throw new ScriptError("missing return statement");
            completed = true;
            emitObjectState();
            return Value.VOID;
        } finally {
            leaveFrame(previous, completed);
        }
    }

    private static ObjectInstance object(Value value) {
        if (value.raw == null) throw new ScriptError("NullPointerException");
        if (!(value.raw instanceof ObjectInstance)) throw new ScriptError("incompatible types: " + value.type.javaName);
        return (ObjectInstance) value.raw;
    }

    public Value thisValue() {
        if (receiver == null) throw new ScriptError("non-static variable this cannot be referenced from a static context");
        return new Value(declaringClass.type(), receiver);
    }

    public Value readField(Value target, JType staticType, String name) {
        VarDecl field = classes.field(staticType, name);
        if (field == null) throw new ScriptError("cannot find symbol: variable " + name);
        if (field.isStatic) return staticConstant(field);
        if (jobOwner != null && field.volatileField) jobUses.add(field.id);
        return object(target).get(field);
    }

    public Value readStaticConstant(String className, String name) {
        VarDecl field = classes.field(JType.reference(className), name);
        if (field == null || !field.isStatic) throw new ScriptError("cannot find symbol: static variable " + name);
        return staticConstant(field);
    }

    private static Value staticConstant(VarDecl field) {
        if (field.type == JType.INT && field.init.get() instanceof Literal literal && literal.value instanceof Integer) {
            return Value.ofInt((Integer) literal.value);
        }
        throw new ScriptError("constant expression required");
    }

    /** Static expression type decides field hiding; the object's runtime class decides dispatch. */
    public JType expressionType(Expr expression) {
        if (expression instanceof NewReentrantLock) return JType.REENTRANT_LOCK;
        if (expression instanceof VarRef) return readVariable(((VarRef) expression).name).type;
        if (expression instanceof ThisRef) return declaringClass == null ? JType.NULL : declaringClass.type();
        if (expression instanceof FarmhandRef) return JType.FARMHAND;
        if (expression instanceof NewObject creation) return creation.className.equals(JType.OBJECT.javaName)
                ? JType.OBJECT : JType.reference(creation.className);
        if (expression instanceof ObjectArrayGet) return readVariable(((ObjectArrayGet) expression).name).type.elementType;
        if (expression instanceof StaticConstant) return JType.INT;
        if (expression instanceof Literal) return ((Literal) expression).type;
        if (expression instanceof FieldAccess) {
            FieldAccess access = (FieldAccess) expression;
            VarDecl field = classes.field(expressionType(access.receiver.get()), access.name);
            return field == null ? access.type : field.type;
        }
        if (expression instanceof InstanceCall) {
            InstanceCall call = (InstanceCall) expression;
            JType target = expressionType(call.receiver.get());
            if (target == JType.FARMHAND && !ObjectMethods.contains(call.name)) return natives.lookup("farmer", call.name).returnType;
            MethodDecl method = classes.method(target, call.name);
            return method == null ? call.returnType : method.returnType;
        }
        if (expression instanceof MethodCall) return ((MethodCall) expression).returnType;
        if (expression instanceof UserCall) {
            MethodDecl method = methods.get(((UserCall) expression).name);
            return method == null ? ((UserCall) expression).returnType : method.returnType;
        }
        throw new ScriptError("incompatible types: expression is not an object reference");
    }

    public void writeField(Value target, JType staticType, String name, Value value) throws InterruptedException {
        checkActive();
        ObjectInstance object = object(target);
        VarDecl field = classes.field(staticType, name);
        if (field == null) throw new ScriptError("cannot find symbol: variable " + name);
        if (field.isFinal) throw new ScriptError("cannot assign a value to final variable " + name);
        object.set(field, convertValue(value, field.type));
        if (jobOwner != null) return;
        synchronized (world) {
            checkActive();
            world.recordFieldWrite(((ClassDecl) field.parent).name, name);
        }
        emitObjectState();
    }

    public Value readVariable(String name) {
        if (scope.contains(name)) return scope.get(name);
        if (receiver != null && classes.field(declaringClass.type(), name) != null) return readField(thisValue(), declaringClass.type(), name);
        return scope.get(name);
    }

    public void assignVariable(String name, Value value) throws InterruptedException {
        if (scope.contains(name)) scope.set(name, convertValue(value, scope.get(name).type));
        else if (receiver != null) writeField(thisValue(), declaringClass.type(), name, value);
        else scope.set(name, value);
    }

    public void emitObjectState() {
        if (!active || objects.isEmpty()) return;
        StringBuilder snapshot = new StringBuilder();
        int first = Math.max(0, objects.size() - MAX_SNAPSHOT_OBJECTS);
        for (int i = first; i < objects.size(); i++) {
            ObjectInstance object = objects.get(i);
            if (snapshot.length() > 0) snapshot.append('\n');
            snapshot.append(object.snapshot());
        }
        listener.onObjectState(farmer.id, snapshot.toString());
    }

    /** Called by every statement before it runs: reports the highlight, then waits at the debugger gate. */
    public void checkpoint(Node node) throws InterruptedException {
        checkActive();
        currentNode = node;
        currentFarmerId = farmer.id;
        if (jobOwner != null && !teamWorker) {
            // Treat the join as one debugger step. Pausing inside a held monitor could stall its peer.
            if (++jobCheckpoints > MAX_JOB_CHECKPOINTS) throw new ScriptError("Background job exceeded its step limit.");
            return;
        }
        emitObjectState();
        listener.onNodeEnter(farmer.id, node.id);
        debug.awaitPermission(this::isActive, farmer.id);
        checkActive();
    }

    /** Keeps lesson evidence tied to the currently active run, including after Reset. */
    public void recordLessonUse(Node node) throws InterruptedException {
        if (jobOwner != null) {
            checkActive();
            jobUses.add(node.id);
            return;
        }
        synchronized (world) {
            checkActive();
            world.recordLessonUse(node.id);
        }
    }

    /** Invalidates this run immediately; the UI never waits for its worker to finish. */
    public void cancel() {
        active = false;
    }

    public boolean isActive() {
        return active && (jobOwner == null || jobOwner.isActive());
    }

    /** Also checked under the world lock before mutations, making Reset safe during cancellation. */
    public void checkActive() throws InterruptedException {
        if (!isActive() || Thread.currentThread().isInterrupted()) throw new InterruptedException();
    }

    public Node currentNode() {
        return currentNode;
    }

    public int currentFarmerId() { return currentFarmerId; }

    /** Restores the origin of a pending exception after a normally completed finally block. */
    public void restoreCurrentNode(Node node) {
        currentNode = node;
    }

    public Scope scope() {
        return scope;
    }

    public void pushScope() {
        scope = new Scope(scope);
    }

    public void popScope() {
        scope = scope.parent;
    }
}
