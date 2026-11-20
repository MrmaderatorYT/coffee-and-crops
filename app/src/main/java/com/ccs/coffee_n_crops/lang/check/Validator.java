package com.ccs.coffee_n_crops.lang.check;

import com.ccs.coffee_n_crops.lang.ast.expr.AtomicCounter;
import com.ccs.coffee_n_crops.lang.ast.expr.NewReentrantLock;
import com.ccs.coffee_n_crops.lang.ast.stmt.LockStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.SynchronizedStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.TogetherStmt;

import com.ccs.coffee_n_crops.lang.ast.ControlFlow;
import com.ccs.coffee_n_crops.lang.ast.AstWalk;
import com.ccs.coffee_n_crops.lang.ast.ClassDecl;
import com.ccs.coffee_n_crops.lang.ast.ConstructorDecl;
import com.ccs.coffee_n_crops.lang.ast.Node;
import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.ast.MethodDecl;
import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.ast.expr.Access;
import com.ccs.coffee_n_crops.lang.ast.expr.NewObject;
import com.ccs.coffee_n_crops.lang.ast.expr.FieldAccess;
import com.ccs.coffee_n_crops.lang.ast.expr.InstanceCall;
import com.ccs.coffee_n_crops.lang.ast.expr.InstanceOf;
import com.ccs.coffee_n_crops.lang.ast.expr.StaticConstant;
import com.ccs.coffee_n_crops.lang.ast.expr.ThisRef;
import com.ccs.coffee_n_crops.lang.ast.expr.FarmhandRef;
import com.ccs.coffee_n_crops.lang.ast.expr.Binary;
import com.ccs.coffee_n_crops.lang.ast.expr.Literal;
import com.ccs.coffee_n_crops.lang.ast.expr.MethodCall;
import com.ccs.coffee_n_crops.lang.ast.expr.NewList;
import com.ccs.coffee_n_crops.lang.ast.expr.ListContains;
import com.ccs.coffee_n_crops.lang.ast.expr.CollectionCall;
import com.ccs.coffee_n_crops.lang.ast.expr.NewIntArray;
import com.ccs.coffee_n_crops.lang.ast.expr.NewObjectArray;
import com.ccs.coffee_n_crops.lang.ast.expr.ObjectArrayGet;
import com.ccs.coffee_n_crops.lang.ast.stmt.ArrayAssign;
import com.ccs.coffee_n_crops.lang.ast.stmt.ObjectArrayAssign;
import com.ccs.coffee_n_crops.lang.ast.expr.Not;
import com.ccs.coffee_n_crops.lang.ast.expr.PostIncrement;
import com.ccs.coffee_n_crops.lang.ast.expr.StrEquals;
import com.ccs.coffee_n_crops.lang.ast.expr.UserCall;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.Assign;
import com.ccs.coffee_n_crops.lang.ast.stmt.FieldAssign;
import com.ccs.coffee_n_crops.lang.ast.stmt.SuperCall;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.ExprStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForEachStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.IfStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.AssertStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.StreamForEachStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.TeamStmt;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamCall;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamCallback;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamLambda;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamMethodRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.ListAdd;
import com.ccs.coffee_n_crops.lang.ast.stmt.ListRemove;
import com.ccs.coffee_n_crops.lang.ast.stmt.ListSet;
import com.ccs.coffee_n_crops.lang.ast.stmt.ReturnStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.SwitchStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.TryStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.ast.stmt.WhileStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.BreakStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.CompoundAssign;
import com.ccs.coffee_n_crops.lang.ast.stmt.ContinueStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.DecrementStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.DoWhileStmt;
import com.ccs.coffee_n_crops.lang.print.JavaPrinter;
import com.ccs.coffee_n_crops.lang.runtime.ClassTable;
import com.ccs.coffee_n_crops.lang.runtime.ObjectMethods;
import com.ccs.coffee_n_crops.lang.runtime.ExceptionTypes;
import com.ccs.coffee_n_crops.lang.runtime.NativeRegistry;
import com.ccs.coffee_n_crops.lang.interp.ScriptError;
import com.ccs.coffee_n_crops.lang.types.JType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Collections;
import java.util.Set;
import java.util.StringJoiner;

/**
 * The "compiler" check run before a program starts. The palette only inserts well-typed code, but
 * deleting or moving a declaration can leave a variable used where it no longer exists; this
 * reports that with the wording javac uses.
 */
public final class Validator {

    /** A compile error and the source node that should be opened and highlighted. */
    public static final class Problem {
        /** Legacy statement anchor retained for existing callers and saved tests. */
        public final Stmt statement;
        public final Node node;
        public final String message;

        Problem(Node node, String message) {
            this.node = node;
            Node parent = node;
            while (parent != null && !(parent instanceof Stmt)) parent = parent.parent;
            this.statement = parent instanceof Stmt ? (Stmt) parent : null;
            this.message = message;
        }
    }

    private static final String CAPTURE_ERROR = "local variables referenced from a lambda expression must be final or effectively final";

    private static final class Local {
        final JType type;
        final boolean isFinal;
        boolean assigned;
        Node capture;
        int lockDepth;

        Local(JType type, boolean isFinal) {
            this.type = type;
            this.isFinal = isFinal;
        }
    }

    /** Lexical locals plus the class context; methods never capture run's local variables. */
    private static final class Env {
        final Env parent;
        final Map<String, Local> vars = new HashMap<>();
        final Map<String, MethodDecl> methods;
        final Map<String, ClassDecl> classes;
        final JType returnType;
        final String methodName;
        final ClassDecl owner;
        final ConstructorDecl constructor;
        final boolean lambdaBoundary;
        String teamActor;
        boolean beforeSuper;
        VarDecl initializingField;
        Set<String> initializedFields;

        Env(Env parent) { this(parent, false); }

        Env(Env parent, boolean lambdaBoundary) {
            this.parent = parent;
            methods = parent.methods;
            classes = parent.classes;
            returnType = lambdaBoundary ? JType.VOID : parent.returnType;
            methodName = parent.methodName;
            owner = parent.owner;
            constructor = lambdaBoundary ? null : parent.constructor;
            this.lambdaBoundary = lambdaBoundary;
            teamActor = parent.teamActor;
            beforeSuper = parent.beforeSuper;
            initializingField = parent.initializingField;
            initializedFields = parent.initializedFields;
        }

        Env(Map<String, MethodDecl> methods, Map<String, ClassDecl> classes, JType returnType,
            String methodName, ClassDecl owner, ConstructorDecl constructor) {
            parent = null;
            this.methods = methods;
            this.classes = classes;
            this.returnType = returnType;
            this.methodName = methodName;
            this.owner = owner;
            this.constructor = constructor;
            lambdaBoundary = false;
        }

        JType find(String name) {
            Local local = local(name);
            return local == null ? null : local.type;
        }

        private Local local(String name) {
            for (Env env = this; env != null; env = env.parent) {
                Local local = env.vars.get(name);
                if (local != null) return local;
            }
            return null;
        }

        void put(String name, JType type, boolean isFinal) {
            vars.put(name, new Local(type, isFinal));
        }

        /** Walk from the use toward its binding to see whether it crosses a lambda. */
        Problem capture(String name, Node at) {
            boolean crossesLambda = false;
            for (Env env = this; env != null; env = env.parent) {
                Local local = env.vars.get(name);
                if (local != null) {
                    if (!crossesLambda) return null;
                    if (local.assigned) return new Problem(at, CAPTURE_ERROR);
                    if (local.capture == null) local.capture = at;
                    return null;
                }
                crossesLambda |= env.lambdaBoundary;
            }
            return null;
        }

        Problem assign(String name, Node at) {
            Local local = local(name);
            if (local == null) return null;
            if (local.capture != null) return new Problem(local.capture, CAPTURE_ERROR);
            if (local.lockDepth > 0) return new Problem(at, "Keep " + name + " unchanged until unlock().");
            local.assigned = true;
            return capture(name, at);
        }

        JType valueType(String name) {
            JType local = find(name);
            if (local != null) return local;
            VarDecl field = owner == null ? null : field(owner, name, classes);
            return field == null ? null : field.type;
        }

        boolean isFinal(String name) {
            Local local = local(name);
            if (local != null) return local.isFinal;
            VarDecl declaration = field(owner, name, classes);
            return declaration != null && declaration.isFinal;
        }
    }

    private Validator() {
    }

    /** @return the first problem in source order, or null if the program is valid */
    public static Problem check(Block program) {
        Map<String, ClassDecl> classes = new HashMap<>();
        for (ClassDecl declaration : program.classes()) {
            if (!validIdentifier(declaration.name) || declaration.name.equals("Program")
                    || builtinName(declaration.name)) {
                return new Problem(declaration, "duplicate class: " + declaration.name);
            }
            if (classes.put(declaration.name, declaration) != null) {
                return new Problem(declaration, "duplicate class: " + declaration.name);
            }
        }
        for (ClassDecl declaration : program.classes()) {
            if (declaration.isInterface && (hasBase(declaration) || declaration.interfaceName != null)) {
                return new Problem(declaration, "'{' expected");
            }
            if (hasBase(declaration) && !classes.containsKey(declaration.superName)) {
                return new Problem(declaration, declaration.superName.equals("Farmhand")
                        ? "cannot inherit from final Farmhand" : "cannot find symbol: class " + declaration.superName);
            }
            if (hasBase(declaration) && base(declaration, classes).isInterface) {
                return new Problem(declaration, "no interface expected here");
            }
            if (declaration.interfaceName != null) {
                ClassDecl contract = classes.get(declaration.interfaceName);
                if (contract == null) {
                    return new Problem(declaration, "cannot find symbol: class " + declaration.interfaceName);
                }
                if (!contract.isInterface) return new Problem(declaration, "interface expected here");
            }
            Set<String> ancestors = new HashSet<>();
            for (ClassDecl at = declaration; at != null; at = base(at, classes)) {
                if (!ancestors.add(at.name)) return new Problem(declaration,
                        "cyclic inheritance involving " + declaration.name);
            }
        }
        Map<String, MethodDecl> methods = new HashMap<>();
        for (MethodDecl method : program.methods()) {
            if (methods.put(method.name, method) != null) {
                return new Problem(method.body, "method " + method.name + " is already defined");
            }
            if (!validIdentifier(method.name) || method.name.equals("run")) {
                return new Problem(method.body, "invalid method declaration; return type required");
            }
        }
        Env run = new Env(methods, classes, JType.VOID, "run(Farmhand)", null, null);
        run.put("farmer", JType.FARMHAND, false);
        Problem problem = block(program, run);
        if (problem != null) return problem;
        for (MethodDecl method : program.methods()) {
            problem = checkMethod(method, null, methods, classes);
            if (problem != null) return problem;
        }
        for (ClassDecl declaration : program.classes()) {
            problem = checkClass(declaration, classes);
            if (problem != null) return problem;
        }
        return null;
    }

    private static Problem checkClass(ClassDecl declaration, Map<String, ClassDecl> classes) {
        if (declaration.isInterface) return checkInterface(declaration, classes);
        Map<String, MethodDecl> noHelpers = Collections.emptyMap();
        Env fieldEnv = new Env(noHelpers, classes, JType.VOID, declaration.name, declaration, null);
        fieldEnv.initializedFields = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (VarDecl field : declaration.fields()) {
            if (!validIdentifier(field.name)) return new Problem(field, "<identifier> expected");
            if (!names.add(field.name)) return new Problem(field,
                    "variable " + field.name + " is already defined in class " + declaration.name);
            if (field.volatileField && (field.isFinal || field.isStatic)) {
                return new Problem(field, "illegal combination of modifiers: volatile and "
                        + (field.isFinal ? "final" : "static"));
            }
            if (field.isStatic && (!field.isFinal || field.type != JType.INT
                    || !(field.init.get() instanceof Literal))) {
                return new Problem(field, "constant expression required");
            }
            Problem problem = typeProblem(field.type, field, fieldEnv, false);
            if (problem != null) return problem;
            fieldEnv.initializingField = field;
            problem = expr(field.init.get(), field.type, field, fieldEnv);
            if (problem != null) return problem;
            fieldEnv.initializedFields.add(field.name);
        }
        ConstructorDecl constructor = declaration.constructor();
        if (constructor != null) {
            Env env = new Env(noHelpers, classes, JType.VOID, declaration.name, declaration, constructor);
            Problem problem = parameters(constructor.parameters, constructor, env);
            if (problem != null) return problem;
            Problem[] misplacedSuper = new Problem[1];
            AstWalk.walk(constructor.body, node -> {
                if (node instanceof SuperCall && (node.parent != constructor.body
                        || constructor.body.indexOf((SuperCall) node) != 0) && misplacedSuper[0] == null) {
                    misplacedSuper[0] = new Problem(node, "call to super must be first statement in constructor");
                }
            });
            if (misplacedSuper[0] != null) return misplacedSuper[0];
            boolean explicitSuper = constructor.body.size() > 0 && constructor.body.get(0) instanceof SuperCall;
            if (!explicitSuper) {
                problem = superArguments(Collections.emptyList(), declaration, constructor, env);
                if (problem != null) return problem;
            }
            problem = block(constructor.body, env);
            if (problem != null) return problem;
        } else {
            Problem problem = superArguments(Collections.emptyList(), declaration, declaration, fieldEnv);
            if (problem != null) return problem;
        }
        Set<String> signatures = new HashSet<>();
        for (MethodDecl method : declaration.methods()) {
            if (!signatures.add(method.name)) return new Problem(method,
                    "method " + method.name + " is already defined in class " + declaration.name);
            MethodDecl inherited = method(base(declaration, classes), method.name, classes);
            if (inherited == null) inherited = ObjectMethods.signature(method.name);
            if (method.name.equals("getClass")) return new Problem(method, "getClass() in " + declaration.name
                    + " cannot override getClass() in Object; overridden method is final");
            if (method.override && !method.sameSignature(inherited)
                    && !overridesInterface(method, declaration, classes)) {
                return new Problem(method, "method does not override or implement a method from a supertype");
            }
            if (inherited != null) {
                if (!method.sameSignature(inherited)) {
                    return new Problem(method, "method " + method.name + " cannot be applied to given types");
                }
                Problem problem = checkOverrideReturn(method, inherited, classes);
                if (problem != null) return problem;
            }
            Problem problem = checkMethod(method, declaration, noHelpers, classes);
            if (problem != null) return problem;
        }
        ClassDecl contract = classes.get(declaration.interfaceName);
        if (contract == null) return null;
        for (MethodDecl signature : contract.methods()) {
            MethodDecl implementation = method(declaration, signature.name, classes);
            if (!signature.sameSignature(implementation)) {
                StringJoiner parameters = new StringJoiner(",", "(", ")");
                for (VarDecl parameter : signature.parameters) parameters.add(parameter.type.javaName);
                return new Problem(declaration, declaration.name + " is not abstract and does not override abstract method "
                        + signature.name + parameters + " in " + contract.name);
            }
            Problem problem = checkOverrideReturn(implementation, signature, classes);
            if (problem != null) return problem;
        }
        return null;
    }

    private static Problem checkInterface(ClassDecl declaration, Map<String, ClassDecl> classes) {
        if (!declaration.fields().isEmpty()) {
            return new Problem(declaration.fields().get(0), "interface members must be method signatures");
        }
        if (declaration.constructor() != null) {
            return new Problem(declaration.constructor(), "<identifier> expected");
        }
        Set<String> signatures = new HashSet<>();
        for (MethodDecl method : declaration.methods()) {
            if (!signatures.add(method.name)) return new Problem(method,
                    "method " + method.name + " is already defined in interface " + declaration.name);
            if (method.override) {
                return new Problem(method, "method does not override or implement a method from a supertype");
            }
            Problem problem = checkMethod(method, declaration, Collections.emptyMap(), classes);
            if (problem != null) return problem;
        }
        return null;
    }

    private static boolean overridesInterface(MethodDecl method, ClassDecl owner, Map<String, ClassDecl> classes) {
        for (ClassDecl at = owner; at != null; at = base(at, classes)) {
            ClassDecl contract = classes.get(at.interfaceName);
            if (method.sameSignature(method(contract, method.name, classes))) return true;
        }
        return false;
    }

    private static Problem checkOverrideReturn(MethodDecl method, MethodDecl inherited, Map<String, ClassDecl> classes) {
        if (method.returnType == inherited.returnType || method.returnType.isReference()
                && inherited.returnType.isReference() && assignable(method.returnType, inherited.returnType, classes)) {
            return null;
        }
        return new Problem(method, "return type " + method.returnType.javaName
                + " is not compatible with " + inherited.returnType.javaName);
    }

    private static Problem checkMethod(MethodDecl method, ClassDecl owner, Map<String, MethodDecl> helpers,
                                       Map<String, ClassDecl> classes) {
        Env env = new Env(helpers, classes, method.returnType,
                method.name + (owner == null ? "(Farmhand)" : "()"), owner, null);
        if (!validIdentifier(method.name)) return new Problem(method, "<identifier> expected");
        Problem problem = typeProblem(method.returnType, method, env, true);
        if (problem != null) return problem;
        if (owner == null) env.put("farmer", JType.FARMHAND, false);
        problem = parameters(method.parameters, method.isInterfaceSignature() ? method : method.body, env);
        if (problem != null) return problem;
        if (method.isInterfaceSignature()) {
            return method.body.size() == 0 ? null : new Problem(method, "interface abstract methods cannot have body");
        }
        problem = block(method.body, env);
        if (problem != null) return problem;
        if (method.returnType != JType.VOID && ControlFlow.completesNormally(method.body)) {
            return new Problem(method.body, "missing return statement");
        }
        return null;
    }

    private static Problem parameters(List<VarDecl> parameters, Node at, Env env) {
        for (VarDecl parameter : parameters) {
            if (parameter.isStatic) return new Problem(at, "modifier static not allowed here");
            if (!validIdentifier(parameter.name) || env.find(parameter.name) != null) {
                return new Problem(at, "variable " + parameter.name
                        + " is already defined in method " + env.methodName);
            }
            Problem problem = typeProblem(parameter.type, at, env, false);
            if (problem != null) return problem;
            env.put(parameter.name, parameter.type, parameter.isFinal);
        }
        return null;
    }

    private static Problem block(Block block, Env outer) {
        Env env = new Env(outer);
        boolean reachable = true;
        for (Stmt s : block.statements()) {
            if (!reachable) return new Problem(s, "unreachable statement");
            Problem p = stmt(s, env);
            if (p != null) return p;
            reachable = ControlFlow.completesNormally(s);
        }
        return null;
    }

    private static Problem stmt(Stmt s, Env env) {
        if (s instanceof StreamForEachStmt each) return stream(each.call, s, env);
        if (s instanceof TeamStmt team) {
            if (!(team.parent instanceof Block run) || run.parent != null) {
                return new Problem(team, "Place workTogether directly inside run().");
            }
            Problem receiver = use("farmer", JType.FARMHAND, team, env);
            if (receiver != null) return receiver;
            Problem first = teamBranch(team.first, team.firstFarmer, env);
            return first == null ? teamBranch(team.second, team.secondFarmer, env) : first;
        }
        if (s instanceof TogetherStmt together) {
            Problem problem = ParallelSyntax.check(together);
            if (problem == null) problem = block(together.first, env);
            return problem == null ? block(together.second, env) : problem;
        }
        if (s instanceof SynchronizedStmt guarded) {
            if (env.teamActor != null) return new Problem(s, "Use a ReentrantLock in a farmhand's job.");
            Problem problem = expr(guarded.monitor.get(), null, s, env);
            if (problem != null) return problem;
            JType monitorType = typeOf(guarded.monitor.get(), env);
            if (monitorType == JType.NULL || !monitorType.isReference()) {
                return new Problem(s, "unexpected type: required reference");
            }
            return block(guarded.body, env);
        }
        if (s instanceof LockStmt guarded) {
            if (!(guarded.lock.get() instanceof VarRef variable)) {
                return new Problem(s, "Select a local ReentrantLock variable.");
            }
            Problem problem = slot(guarded.lock, s, env);
            if (problem != null) return problem;
            Local local = env.local(variable.name);
            if (local == null) return new Problem(s, "Select a local ReentrantLock variable.");
            local.lockDepth++;
            try {
                return block(guarded.body, env);
            } finally {
                local.lockDepth--;
            }
        }
        if (s instanceof FieldAssign) {
            FieldAssign assignment = (FieldAssign) s;
            Problem problem = expr(assignment.target, null, s, env);
            if (problem != null) return problem;
            JType owner = typeOf(assignment.target.receiver.get(), env);
            VarDecl declaration = field(env.classes.get(owner.javaName), assignment.target.name, env.classes);
            if (declaration.isFinal) return finalAssignment(declaration.name, s);
            return expr(assignment.value.get(), declaration.type, s, env);
        }
        if (s instanceof SuperCall) {
            if (env.constructor == null || s.parent != env.constructor.body
                    || env.constructor.body.indexOf(s) != 0) {
                return new Problem(s, "call to super must be first statement in constructor");
            }
            return superArguments(((SuperCall) s).args, env.owner, s, env);
        }
        if (s instanceof TryStmt) {
            TryStmt guarded = (TryStmt) s;
            if (guarded.catches().isEmpty() && guarded.finallyBlock() == null) {
                return new Problem(s, "'try' without 'catch', 'finally' or resource declarations");
            }
            Problem problem = block(guarded.tryBlock, env);
            if (problem != null) return problem;
            Set<String> handled = new HashSet<>();
            for (TryStmt.Catch arm : guarded.catches()) {
                if (!ExceptionTypes.isKnown(arm.typeName)) {
                    return new Problem(arm.body, "cannot find symbol: class " + arm.typeName);
                }
                for (String previous : handled) {
                    if (ExceptionTypes.covers(previous, arm.typeName)) {
                        return new Problem(arm.body, "exception " + arm.typeName + " has already been caught");
                    }
                }
                handled.add(arm.typeName);
                if (!validIdentifier(arm.name) || env.find(arm.name) != null) {
                    return new Problem(arm.body, "variable " + arm.name
                            + " is already defined in method " + env.methodName);
                }
                Env handler = new Env(env);
                handler.put(arm.name, arm.variable.type, false);
                problem = block(arm.body, handler);
                if (problem != null) return problem;
            }
            return guarded.finallyBlock() == null ? null : block(guarded.finallyBlock(), env);
        }
        if (s instanceof VarDecl) {
            return declare((VarDecl) s, s, env);
        }
        if (s instanceof Assign) {
            Assign a = (Assign) s;
            if (env.isFinal(a.name)) return finalAssignment(a.name, s);
            Problem p = use(a.name, null, s, env);
            if (p == null) p = expr(a.value.get(), env.valueType(a.name), s, env);
            return p != null ? p : env.assign(a.name, s);
        }
        if (s instanceof ListAdd) {
            ListAdd a = (ListAdd) s;
            Problem p = use(a.name, JType.INT_LIST, s, env);
            return p != null ? p : slot(a.value, s, env);
        }
        if (s instanceof ArrayAssign) {
            ArrayAssign assignment = (ArrayAssign) s;
            Problem p = use(assignment.name, JType.INT_ARRAY, s, env);
            if (p == null) p = slot(assignment.index, s, env);
            return p != null ? p : slot(assignment.value, s, env);
        }
        if (s instanceof ObjectArrayAssign) {
            ObjectArrayAssign assignment = (ObjectArrayAssign) s;
            Problem problem = useObjectArray(assignment.name, assignment.index, s, env);
            return problem != null ? problem : expr(assignment.value.get(),
                    env.valueType(assignment.name).elementType, s, env);
        }
        if (s instanceof ListSet) {
            ListSet set = (ListSet) s;
            Problem p = use(set.name, JType.INT_LIST, s, env);
            if (p == null) p = slot(set.index, s, env);
            return p != null ? p : slot(set.value, s, env);
        }
        if (s instanceof ListRemove) {
            ListRemove remove = (ListRemove) s;
            Problem p = use(remove.name, JType.INT_LIST, s, env);
            return p != null ? p : slot(remove.index, s, env);
        }
        if (s instanceof CompoundAssign) {
            CompoundAssign assignment = (CompoundAssign) s;
            if (env.isFinal(assignment.name)) return finalAssignment(assignment.name, s);
            Problem problem = use(assignment.name, JType.INT, s, env);
            if (problem == null) problem = slot(assignment.value, s, env);
            return problem != null ? problem : env.assign(assignment.name, s);
        }
        if (s instanceof DecrementStmt) {
            DecrementStmt decrement = (DecrementStmt) s;
            if (env.isFinal(decrement.name)) return finalAssignment(decrement.name, s);
            Problem problem = use(decrement.name, null, s, env);
            if (problem != null) return problem;
            JType type = env.valueType(decrement.name);
            return type == JType.INT ? env.assign(decrement.name, s) : new Problem(s,
                    "bad operand type " + type.javaName + " for unary operator '--'");
        }
        if (s instanceof BreakStmt || s instanceof ContinueStmt) {
            boolean continuing = s instanceof ContinueStmt;
            return ControlFlow.jumpTarget(s, continuing) != null ? null : new Problem(s,
                    continuing ? "continue outside of loop" : "break outside switch or loop");
        }
        if (s instanceof ReturnStmt) {
            ReturnStmt returned = (ReturnStmt) s;
            if (returned.value == null) {
                return env.returnType == JType.VOID ? null
                        : new Problem(s, "incompatible types: missing return value");
            }
            if (env.returnType == JType.VOID) return new Problem(s, "incompatible types: unexpected return value");
            return expr(returned.value.get(), env.returnType, s, env);
        }
        if (s instanceof ExprStmt) {
            Expr expression = ((ExprStmt) s).expr;
            if (!(expression instanceof MethodCall) && !(expression instanceof UserCall)
                    && !(expression instanceof InstanceCall) && !(expression instanceof NewObject)
                    && !(expression instanceof PostIncrement)
                    && !(expression instanceof ListContains) && !(expression instanceof CollectionCall)
                    && !(expression instanceof AtomicCounter) && !(expression instanceof StreamCall)
                    && !(expression instanceof NewReentrantLock)) {
                return new Problem(s, "not a statement");
            }
            return expr(expression, null, s, env);
        }
        if (s instanceof ForStmt) {
            ForStmt f = (ForStmt) s;
            Env loop = new Env(env);
            Problem p = declare(f.init, s, loop);
            if (p == null) p = slot(f.cond, s, loop);
            if (p == null) p = expr(f.update, null, s, loop);
            if (p != null) return p;
            if (isFalse(f.cond.get())) return new Problem(s, "unreachable statement");
            return block(f.body, loop);
        }
        if (s instanceof AssertStmt assertion) {
            Problem condition = slot(assertion.condition, s, env);
            return condition != null || assertion.detail == null ? condition : slot(assertion.detail, s, env);
        }
        if (s instanceof IfStmt) {
            IfStmt i = (IfStmt) s;
            for (IfStmt.Branch b : i.branches()) {
                Problem p = slot(b.cond, s, env);
                if (p == null) p = block(b.block, env);
                if (p != null) return p;
            }
            return i.elseBlock() == null ? null : block(i.elseBlock(), env);
        }
        if (s instanceof WhileStmt) {
            WhileStmt w = (WhileStmt) s;
            Problem p = slot(w.cond, s, env);
            if (p != null) return p;
            if (isFalse(w.cond.get())) return new Problem(s, "unreachable statement");
            return block(w.body, env);
        }
        if (s instanceof DoWhileStmt) {
            DoWhileStmt loop = (DoWhileStmt) s;
            Problem problem = block(loop.body, env);
            if (problem != null) return problem;
            problem = slot(loop.cond, s, env);
            return problem == null ? null : new Problem(loop.conditionLine, problem.message);
        }
        if (s instanceof ForEachStmt) {
            ForEachStmt f = (ForEachStmt) s;
            JType itemType = JType.DIRECTION;
            if (f.source != null) {
                Problem problem = use(f.source, null, s, env);
                if (problem != null) return problem;
                JType collection = env.valueType(f.source);
                itemType = collection == JType.INT_ARRAY || collection == JType.INT_LIST
                        ? JType.INT : collection.elementType;
                if (itemType == null) {
                    return new Problem(s, "for-each not applicable to expression type "
                            + collection.javaName);
                }
            }
            Problem problem = compatible(itemType, f.variable.type, s, env);
            if (problem != null) return problem;
            Env loop = new Env(env);
            Problem p = declare(f.variable, s, loop);
            return p != null ? p : block(f.body, loop);
        }
        if (s instanceof SwitchStmt) {
            SwitchStmt sw = (SwitchStmt) s;
            Problem p = slot(sw.subject, s, env);
            if (p != null) return p;
            Set<Object> labels = new HashSet<>();
            for (SwitchStmt.Case c : sw.cases()) {
                if (!labels.add(c.value())) {
                    return new Problem(s, "duplicate case label: "
                            + JavaPrinter.literalText(sw.subject.type, c.value()));
                }
                p = block(c.body, env);
                if (p != null) return p;
            }
            return sw.defaultBlock() == null ? null : block(sw.defaultBlock(), env);
        }
        return null;
    }

    private static Problem declare(VarDecl d, Stmt at, Env env) {
        if (d.isStatic) return new Problem(at, "modifier static not allowed here");
        if (d.volatileField) return new Problem(at, "modifier volatile not allowed here");
        Problem type = typeProblem(d.type, at, env, false);
        if (type != null) return type;
        if (!validIdentifier(d.name)) {
            return new Problem(at, "variable " + d.name + " is already defined in method " + env.methodName);
        }
        Problem p = slot(d.init, at, env); // the initializer cannot see the variable it initializes
        if (p != null) return p;
        if (env.find(d.name) != null) {
            return new Problem(at, "variable " + d.name + " is already defined in method " + env.methodName);
        }
        env.put(d.name, d.type, d.isFinal);
        return null;
    }

    private static Problem teamBranch(Block body, VarDecl actor, Env parent) {
        if (parent.find(actor.name) != null) {
            return new Problem(actor, "variable " + actor.name + " is already defined in method " + parent.methodName);
        }
        Env worker = new Env(parent, true);
        worker.teamActor = actor.name;
        worker.put(actor.name, JType.FARMHAND, true);
        return block(body, worker);
    }

    private static Problem finalAssignment(String name, Stmt at) {
        return new Problem(at, "cannot assign a value to final variable " + name);
    }

    private static Problem slot(ExprSlot slot, Stmt at, Env env) {
        return expr(slot.get(), slot.type, at, env);
    }

    private static Problem useObjectArray(String name, ExprSlot index, Stmt at, Env env) {
        Problem problem = use(name, null, at, env);
        if (problem != null) return problem;
        JType type = env.valueType(name);
        if (type.elementType == null) return new Problem(at, "array required, but " + type.javaName + " found");
        return slot(index, at, env);
    }

    /** @param expected the type required here, or null if any type is accepted */
    private static Problem expr(Expr e, JType expected, Stmt at, Env env) {
        JType actual;
        if (e instanceof ThisRef) {
            if (env.owner == null) return new Problem(at, "non-static variable this cannot be referenced from a static context");
            if (env.beforeSuper) return new Problem(at, "cannot reference this before supertype constructor has been called");
            actual = JType.reference(env.owner.name);
        } else if (e instanceof FarmhandRef) {
            if (env.teamActor != null) return actorProblem(at, env);
            return use("farmer", JType.FARMHAND, at, env) == null
                    ? compatible(JType.FARMHAND, expected, at, env) : use("farmer", JType.FARMHAND, at, env);
        } else if (e instanceof NewObject) {
            NewObject creation = (NewObject) e;
            if (creation.className.equals(JType.OBJECT.javaName)) {
                if (!creation.args.isEmpty()) return argumentCount("constructor Object", at);
                return compatible(JType.OBJECT, expected, at, env);
            }
            ClassDecl declaration = env.classes.get(creation.className);
            if (declaration == null) return new Problem(at, "cannot find symbol: class " + creation.className);
            if (declaration.isInterface) return new Problem(at, creation.className + " is abstract; cannot be instantiated");
            ConstructorDecl constructor = declaration.constructor();
            Problem problem = arguments(creation.args, constructor == null ? Collections.emptyList() : constructor.parameters,
                    "constructor " + creation.className, at, env);
            if (problem != null) return problem;
            actual = JType.reference(creation.className);
        } else if (e instanceof FieldAccess) {
            FieldAccess access = (FieldAccess) e;
            Problem problem = expr(access.receiver.get(), access.receiver.type, at, env);
            if (problem != null) return problem;
            JType receiver = typeOf(access.receiver.get(), env);
            VarDecl declaration = field(env.classes.get(receiver.javaName), access.name, env.classes);
            if (declaration == null) return new Problem(at, "cannot find symbol: variable " + access.name);
            if (access.type != declaration.type) return new Problem(at, "incompatible types: "
                    + declaration.type.javaName + " cannot be converted to " + access.type.javaName);
            actual = declaration.type;
        } else if (e instanceof StaticConstant) {
            StaticConstant constant = (StaticConstant) e;
            ClassDecl owner = env.classes.get(constant.className);
            if (owner == null) return new Problem(at, "cannot find symbol: class " + constant.className);
            VarDecl variable = field(owner, constant.fieldName, env.classes);
            if (variable == null) return new Problem(at, "cannot find symbol: variable " + constant.fieldName);
            if (!variable.isStatic) return new Problem(at, "non-static variable " + constant.fieldName
                    + " cannot be referenced from a static context");
            actual = variable.type;
        } else if (e instanceof InstanceOf) {
            InstanceOf check = (InstanceOf) e;
            Problem problem = expr(check.operand.get(), JType.OBJECT, at, env);
            if (problem != null) return problem;
            if (!(check.target.isClass() || check.target == JType.OBJECT)) return new Problem(at, "unexpected type");
            problem = typeProblem(check.target, at, env, false);
            if (problem != null) return problem;
            JType source = typeOf(check.operand.get(), env);
            if (!ClassTable.instanceOfConvertible(source, check.target, env.classes)) {
                return new Problem(at, "incompatible types: " + source.javaName + " cannot be converted to " + check.target.javaName);
            }
            actual = JType.BOOLEAN;
        } else if (e instanceof InstanceCall) {
            InstanceCall call = (InstanceCall) e;
            Problem problem = expr(call.receiver.get(), call.receiver.type, at, env);
            if (problem != null) return problem;
            JType receiver = typeOf(call.receiver.get(), env);
            if (receiver == JType.FARMHAND && !ObjectMethods.contains(call.name)) {
                if (env.teamActor != null && (!(call.receiver.get() instanceof VarRef ref)
                        || !ref.name.equals(env.teamActor))) return actorProblem(at, env);
                NativeRegistry.Entry entry;
                try { entry = NativeRegistry.STANDARD.lookup("farmer", call.name); }
                catch (ScriptError missing) { return new Problem(at, missing.getMessage()); }
                if (entry.params.length != call.args.size()) return argumentCount("method " + call.name, at);
                for (int i = 0; i < call.args.size(); i++) {
                    problem = expr(call.args.get(i).get(), entry.params[i], at, env);
                    if (problem != null) return problem;
                }
                actual = entry.returnType;
            } else {
                MethodDecl target = method(env.classes.get(receiver.javaName), call.name, env.classes);
                if (target == null && receiver.isReference() && receiver != JType.NULL) {
                    target = ObjectMethods.signature(call.name);
                }
                if (target == null) return new Problem(at, "cannot find symbol: method " + call.name + "()");
                problem = arguments(call.args, target.parameters, "method " + call.name, at, env);
                if (problem != null) return problem;
                actual = target.returnType;
            }
        } else if (e instanceof VarRef) {
            return use(((VarRef) e).name, expected, at, env);
        } else if (e instanceof Literal) {
            Literal literal = (Literal) e;
            actual = literal.value == null ? JType.NULL : literal.type;
        } else if (e instanceof PostIncrement) {
            if (env.isFinal(((PostIncrement) e).name)) return finalAssignment(((PostIncrement) e).name, at);
            Problem problem = use(((PostIncrement) e).name, JType.INT, at, env);
            if (problem == null) problem = env.assign(((PostIncrement) e).name, at);
            if (problem != null) return problem;
            actual = JType.INT;
        } else if (e instanceof Binary) {
            Binary binary = (Binary) e;
            Problem problem = slot(binary.left, at, env);
            if (problem == null) problem = slot(binary.right, at, env);
            if (problem != null) return problem;
            actual = binaryResult(binary, env);
        } else if (e instanceof Not) {
            Problem problem = slot(((Not) e).operand, at, env);
            if (problem != null) return problem;
            actual = JType.BOOLEAN;
        } else if (e instanceof StrEquals) {
            Problem problem = slot(((StrEquals) e).left, at, env);
            if (problem == null) problem = slot(((StrEquals) e).right, at, env);
            if (problem != null) return problem;
            actual = JType.BOOLEAN;
        } else if (e instanceof Access) {
            Access access = (Access) e;
            JType receiver = env.valueType(access.name);
            JType target = access.kind == Access.Kind.LENGTH && receiver != null && receiver.elementType != null
                    ? receiver : access.kind.target;
            Problem problem = use(access.name, target, at, env);
            for (ExprSlot argument : access.args) {
                if (problem == null) problem = slot(argument, at, env);
            }
            if (problem != null) return problem;
            actual = JType.INT;
        } else if (e instanceof UserCall) {
            UserCall call = (UserCall) e;
            if (env.teamActor != null) return actorProblem(at, env);
            MethodDecl method = env.methods.get(call.name);
            if (method == null) return new Problem(at, "cannot find symbol: method " + call.name + "(Farmhand)");
            Problem farmer = use("farmer", JType.FARMHAND, at, env);
            if (farmer != null) return farmer;
            if (call.args.size() != method.parameters.size()) {
                return new Problem(at, "method " + call.name + " cannot be applied to given types; "
                        + "actual and formal argument lists differ in length");
            }
            for (int i = 0; i < call.args.size(); i++) {
                Problem problem = expr(call.args.get(i).get(), method.parameters.get(i).type, at, env);
                if (problem != null) return problem;
            }
            actual = method.returnType;
        } else if (e instanceof MethodCall) {
            MethodCall call = (MethodCall) e;
            if (env.teamActor != null && !call.receiver.equals(env.teamActor)) return actorProblem(at, env);
            Problem receiver = use(call.receiver, JType.FARMHAND, at, env);
            if (receiver != null) return receiver;
            for (ExprSlot argument : call.args) {
                Problem problem = slot(argument, at, env);
                if (problem != null) return problem;
            }
            actual = call.returnType;
        } else if (e instanceof NewList) {
            actual = JType.INT_LIST;
        } else if (e instanceof NewIntArray) {
            Problem problem = slot(((NewIntArray) e).length, at, env);
            if (problem != null) return problem;
            actual = JType.INT_ARRAY;
        } else if (e instanceof NewObjectArray) {
            NewObjectArray creation = (NewObjectArray) e;
            if (!creation.elementType.isUserClass()) return new Problem(at, "illegal start of type");
            Problem problem = typeProblem(creation.elementType, at, env, false);
            if (problem == null) problem = slot(creation.length, at, env);
            if (problem != null) return problem;
            actual = JType.objectArray(creation.elementType);
        } else if (e instanceof ObjectArrayGet) {
            ObjectArrayGet access = (ObjectArrayGet) e;
            Problem problem = useObjectArray(access.name, access.index, at, env);
            if (problem != null) return problem;
            actual = env.valueType(access.name).elementType;
        } else if (e instanceof ListContains) {
            ListContains contains = (ListContains) e;
            Problem problem = use(contains.name, JType.INT_LIST, at, env);
            if (problem == null) problem = slot(contains.value, at, env);
            if (problem != null) return problem;
            actual = JType.BOOLEAN;
        } else if (e instanceof AtomicCounter counter) {
            Problem problem = slot(counter.operand, at, env);
            if (problem != null) return problem;
            actual = counter.op.type;
        } else if (e instanceof NewReentrantLock) {
            actual = JType.REENTRANT_LOCK;
        } else if (e instanceof StreamCall call) {
            if (call.callback instanceof StreamLambda lambda && lambda.body != null) {
                return new Problem(at, "a forEach block lambda requires its own statement");
            }
            Problem problem = stream(call, at, env);
            if (problem != null) return problem;
            actual = call.terminal.resultType;
        } else if (e instanceof CollectionCall call) {
            Problem problem = call.receiver == null ? null : use(call.receiver, call.op.receiverType, at, env);
            if (problem == null && call.argument != null) problem = slot(call.argument, at, env);
            if (problem != null) return problem;
            if (call.op == CollectionCall.Op.FIND_FIRST) {
                if (!validIdentifier(call.parameter)) return new Problem(at, "<identifier> expected");
                if (env.find(call.parameter) != null) return new Problem(at, "variable " + call.parameter
                        + " is already defined in method " + env.methodName);
                if (!(call.argument.get() instanceof Literal literal) || literal.type != JType.INT) {
                    return new Problem(at, "expected an int literal in the stream filter");
                }
            }
            actual = call.op.resultType;
        } else {
            return new Problem(at, "unsupported expression");
        }
        return compatible(actual, expected, at, env);
    }

    private static Problem stream(StreamCall call, Stmt at, Env env) {
        Problem source = use(call.source, JType.INT_LIST, at, env);
        if (source != null) return source;
        for (StreamCall.Stage stage : call.stages) {
            Problem problem = streamCallback(stage.callback(), stage.kind().resultType, at, env);
            if (problem != null) return problem;
        }
        if (call.terminal.callbackResult == null) {
            return call.callback == null ? null : argumentCount("method " + call.terminal.method, at);
        }
        return streamCallback(call.callback, call.terminal.callbackResult, at, env);
    }

    private static Problem actorProblem(Stmt at, Env env) {
        return new Problem(at, "Use " + env.teamActor + " for farm actions in this job.");
    }

    private static Problem streamCallback(StreamCallback callback, JType expected, Stmt at, Env env) {
        if (callback == null || callback.resultType != expected) {
            return new Problem(at, "incompatible types: bad return type in lambda expression");
        }
        if (callback instanceof StreamMethodRef method) {
            if (!"add".equals(method.method)) return new Problem(at, "invalid method reference");
            return use(method.receiver, JType.INT_LIST, at, env);
        }
        if (!(callback instanceof StreamLambda lambda)) return new Problem(at, "unsupported lambda expression");
        String name = lambda.parameter.name;
        if (!validIdentifier(name)) return new Problem(at, "<identifier> expected");
        if (env.find(name) != null) return new Problem(at, "variable " + name + " is already defined in method " + env.methodName);
        Env body = new Env(env, true);
        body.put(name, JType.INT, false);
        if (expected == JType.VOID) {
            return lambda.body == null ? new Problem(at, "incompatible types: unexpected return value")
                    : block(lambda.body, body);
        }
        return lambda.expression == null ? new Problem(at, "incompatible types: missing return value")
                : slot(lambda.expression, at, body);
    }

    private static Problem compatible(JType actual, JType expected, Stmt at, Env env) {
        if (expected != null && !assignable(actual, expected, env.classes)) {
            return new Problem(at, "incompatible types: " + actual.javaName
                    + " cannot be converted to " + expected.javaName);
        }
        return null;
    }

    private static boolean assignable(JType actual, JType expected, Map<String, ClassDecl> classes) {
        return ClassTable.assignable(actual, expected, classes);
    }


    private static Problem typeProblem(JType type, Node at, Env env, boolean allowVoid) {
        if (type == JType.VOID && !allowVoid) return new Problem(at, "'void' type not allowed here");
        if (type == JType.NULL) return new Problem(at, "illegal start of type");
        JType named = type.elementType == null ? type : type.elementType;
        if (named.isUserClass() && !env.classes.containsKey(named.javaName)) {
            return new Problem(at, "cannot find symbol: class " + named.javaName);
        }
        return null;
    }

    private static boolean builtinName(String name) {
        if (name.equals("ArrayList") || name.equals("Integer") || name.equals("Object")) return true;
        for (JType type : JType.values()) if (type.javaName.equals(name)) return true;
        return false;
    }

    private static boolean hasBase(ClassDecl declaration) {
        return declaration.superName != null && !declaration.superName.isEmpty();
    }

    private static ClassDecl base(ClassDecl declaration, Map<String, ClassDecl> classes) {
        return declaration == null || !hasBase(declaration) ? null : classes.get(declaration.superName);
    }

    private static VarDecl field(ClassDecl declaration, String name, Map<String, ClassDecl> classes) {
        Set<String> visited = new HashSet<>();
        for (ClassDecl at = declaration; at != null && visited.add(at.name); at = base(at, classes)) {
            for (VarDecl field : at.fields()) if (field.name.equals(name)) return field;
        }
        return null;
    }

    private static MethodDecl method(ClassDecl declaration, String name, Map<String, ClassDecl> classes) {
        Set<String> visited = new HashSet<>();
        for (ClassDecl at = declaration; at != null && visited.add(at.name); at = base(at, classes)) {
            for (MethodDecl method : at.methods()) if (method.name.equals(name)) return method;
        }
        return null;
    }

    private static Problem argumentCount(String target, Node at) {
        return new Problem(at, target + " cannot be applied to given types; actual and formal argument lists differ in length");
    }

    private static Problem arguments(List<ExprSlot> actual, List<VarDecl> parameters, String target, Stmt at, Env env) {
        if (actual.size() != parameters.size()) return argumentCount(target, at);
        for (int i = 0; i < actual.size(); i++) {
            Problem problem = expr(actual.get(i).get(), parameters.get(i).type, at, env);
            if (problem != null) return problem;
        }
        return null;
    }

    private static Problem superArguments(List<ExprSlot> args, ClassDecl owner, Node at, Env env) {
        ClassDecl parent = base(owner, env.classes);
        List<VarDecl> parameters = parent == null || parent.constructor() == null
                ? Collections.emptyList() : parent.constructor().parameters;
        if (args.size() != parameters.size()) return argumentCount("constructor "
                + (parent == null ? "Object" : parent.name), at);
        Env before = new Env(env);
        before.beforeSuper = true;
        Stmt statement = at instanceof Stmt ? (Stmt) at
                : env.constructor != null ? env.constructor.body : owner.fields().isEmpty() ? new Block() : owner.fields().get(0);
        for (int i = 0; i < args.size(); i++) {
            Problem problem = expr(args.get(i).get(), parameters.get(i).type, statement, before);
            if (problem != null) return problem;
        }
        return null;
    }

    private static JType typeOf(Expr expression, Env env) {
        if (expression instanceof AtomicCounter counter) return counter.op.type;
        if (expression instanceof NewReentrantLock) return JType.REENTRANT_LOCK;
        if (expression instanceof InstanceOf) return JType.BOOLEAN;
        if (expression instanceof StaticConstant) return JType.INT;
        if (expression instanceof VarRef) return env.valueType(((VarRef) expression).name);
        if (expression instanceof ThisRef) return env.owner == null ? JType.NULL : JType.reference(env.owner.name);
        if (expression instanceof FarmhandRef) return JType.FARMHAND;
        if (expression instanceof NewObject creation) return creation.className.equals(JType.OBJECT.javaName)
                ? JType.OBJECT : JType.reference(creation.className);
        if (expression instanceof FieldAccess) {
            FieldAccess access = (FieldAccess) expression;
            JType receiver = typeOf(access.receiver.get(), env);
            VarDecl target = receiver == null ? null : field(env.classes.get(receiver.javaName), access.name, env.classes);
            return target == null ? access.type : target.type;
        }
        if (expression instanceof InstanceCall) {
            InstanceCall call = (InstanceCall) expression;
            JType receiver = typeOf(call.receiver.get(), env);
            MethodDecl target = receiver == null ? null : method(env.classes.get(receiver.javaName), call.name, env.classes);
            if (target == null && receiver != null && receiver.isReference()) target = ObjectMethods.signature(call.name);
            return target == null ? call.returnType : target.returnType;
        }
        if (expression instanceof Literal) return ((Literal) expression).value == null ? JType.NULL : ((Literal) expression).type;
        if (expression instanceof UserCall) {
            MethodDecl target = env.methods.get(((UserCall) expression).name);
            return target == null ? ((UserCall) expression).returnType : target.returnType;
        }
        if (expression instanceof MethodCall) return ((MethodCall) expression).returnType;
        if (expression instanceof Binary binary) return binaryResult(binary, env);
        if (expression instanceof Not || expression instanceof StrEquals) return JType.BOOLEAN;
        if (expression instanceof NewList) return JType.INT_LIST;
        if (expression instanceof NewIntArray) return JType.INT_ARRAY;
        if (expression instanceof NewObjectArray) return JType.objectArray(((NewObjectArray) expression).elementType);
        if (expression instanceof ObjectArrayGet) return env.valueType(((ObjectArrayGet) expression).name).elementType;
        if (expression instanceof ListContains) return JType.BOOLEAN;
        if (expression instanceof CollectionCall call) return call.op.resultType;
        if (expression instanceof StreamCall call) return call.terminal.resultType;
        return JType.INT;
    }

    private static JType binaryResult(Binary binary, Env env) {
        if (binary.op().family != Binary.Family.ARITHMETIC) return JType.BOOLEAN;
        JType left = typeOf(binary.left.get(), env);
        JType right = typeOf(binary.right.get(), env);
        if (left == JType.DOUBLE || right == JType.DOUBLE) return JType.DOUBLE;
        if (left == JType.LONG || right == JType.LONG) return JType.LONG;
        return JType.INT;
    }

    private static boolean isFalse(Expr expression) {
        return expression instanceof Literal && Boolean.FALSE.equals(((Literal) expression).value);
    }

    /** Checks identifier syntax before the editor proposes a generated Java name. */
    public static boolean validIdentifier(String name) {
        if (name == null || name.isEmpty() || !Character.isJavaIdentifierStart(name.charAt(0))) return false;
        for (int i = 1; i < name.length(); i++) {
            if (!Character.isJavaIdentifierPart(name.charAt(i))) return false;
        }
        return !JAVA_KEYWORDS.contains(name);
    }

    private static final Set<String> JAVA_KEYWORDS = new HashSet<>(java.util.Arrays.asList(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
            "volatile", "while", "true", "false", "null", "_"));

    private static Problem use(String name, JType expected, Stmt at, Env env) {
        JType actual = env.valueType(name);
        if (actual == null) return new Problem(at, "cannot find symbol: variable " + name);
        if (env.find(name) == null && env.owner != null) {
            if (env.beforeSuper) return new Problem(at,
                    "cannot reference " + name + " before supertype constructor has been called");
            if (env.initializingField != null && env.initializedFields != null) {
                if (env.initializingField.name.equals(name)) return new Problem(at, "self-reference in initializer");
                boolean own = false;
                for (VarDecl field : env.owner.fields()) own |= field.name.equals(name);
                if (own && !env.initializedFields.contains(name)) return new Problem(at, "illegal forward reference");
            }
        }
        Problem problem = compatible(actual, expected, at, env);
        return problem != null ? problem : env.capture(name, at);
    }
}
