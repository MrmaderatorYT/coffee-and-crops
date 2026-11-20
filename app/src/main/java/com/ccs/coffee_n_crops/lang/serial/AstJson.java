package com.ccs.coffee_n_crops.lang.serial;

import com.ccs.coffee_n_crops.lang.ast.expr.AtomicCounter;
import com.ccs.coffee_n_crops.lang.ast.expr.NewReentrantLock;
import com.ccs.coffee_n_crops.lang.ast.stmt.LockStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.SynchronizedStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.TogetherStmt;

import com.ccs.coffee_n_crops.farm.Direction;
import com.ccs.coffee_n_crops.farm.Crop;
import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.ast.MethodDecl;
import com.ccs.coffee_n_crops.lang.ast.ClassDecl;
import com.ccs.coffee_n_crops.lang.ast.ConstructorDecl;
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
import com.ccs.coffee_n_crops.lang.ast.expr.StreamCall;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamCallback;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamLambda;
import com.ccs.coffee_n_crops.lang.ast.expr.FunctionValue;
import com.ccs.coffee_n_crops.lang.ast.expr.FunctionCall;
import com.ccs.coffee_n_crops.lang.ast.expr.FunctionVariable;
import com.ccs.coffee_n_crops.lang.ast.expr.FunctionType;
import com.ccs.coffee_n_crops.lang.ast.expr.IntegerMethodRef;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamMethodRef;
import com.ccs.coffee_n_crops.lang.ast.expr.NewIntArray;
import com.ccs.coffee_n_crops.lang.ast.expr.NewIntGrid;
import com.ccs.coffee_n_crops.lang.ast.expr.IntArrayLiteral;
import com.ccs.coffee_n_crops.lang.ast.expr.NewObjectArray;
import com.ccs.coffee_n_crops.lang.ast.expr.ObjectArrayGet;
import com.ccs.coffee_n_crops.lang.ast.stmt.ArrayAssign;
import com.ccs.coffee_n_crops.lang.ast.stmt.ObjectArrayAssign;
import com.ccs.coffee_n_crops.lang.ast.expr.Not;
import com.ccs.coffee_n_crops.lang.ast.expr.PostIncrement;
import com.ccs.coffee_n_crops.lang.ast.expr.StrEquals;
import com.ccs.coffee_n_crops.lang.ast.expr.CropEquals;
import com.ccs.coffee_n_crops.lang.ast.expr.StringConcat;
import com.ccs.coffee_n_crops.lang.ast.expr.StringLength;
import com.ccs.coffee_n_crops.lang.ast.expr.NewStringIntMap;
import com.ccs.coffee_n_crops.lang.ast.expr.StringIntMapCall;
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
import com.ccs.coffee_n_crops.lang.ast.stmt.ListAdd;
import com.ccs.coffee_n_crops.lang.ast.stmt.ListRemove;
import com.ccs.coffee_n_crops.lang.ast.stmt.ListSet;
import com.ccs.coffee_n_crops.lang.ast.stmt.ReturnStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.SwitchStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.StreamForEachStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.TeamStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.TryStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.ast.stmt.WhileStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.BreakStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.CompoundAssign;
import com.ccs.coffee_n_crops.lang.ast.stmt.ContinueStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.DecrementStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.DoWhileStmt;
import com.ccs.coffee_n_crops.lang.types.JType;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Saves and loads a program as JSON. Each node becomes an object tagged with {@code "t"}. */
public final class AstJson {
    private AstJson() {
    }

    public static JSONObject toJson(Block block) throws JSONException {
        return writeBlock(block).put("actorApi", LegacyFarmApi.FARMHAND_VERSION);
    }

    /** Deep-copies a statement with independent node IDs and editable slots. */
    public static Stmt copyStatement(Stmt statement) throws JSONException {
        return stmtFrom(stmt(statement));
    }

    private static JSONObject writeBlock(Block block) throws JSONException {
        JSONArray stmts = new JSONArray();
        for (Stmt s : block.statements()) stmts.put(stmt(s));
        JSONObject out = new JSONObject().put("t", "block").put("stmts", stmts);
        if (!block.methods().isEmpty()) {
            JSONArray methods = new JSONArray();
            for (MethodDecl method : block.methods()) methods.put(methodJson(method));
            out.put("methods", methods);
        }
        if (!block.classes().isEmpty()) {
            JSONArray classes = new JSONArray();
            for (ClassDecl declaration : block.classes()) {
                JSONObject saved = new JSONObject().put("name", declaration.name)
                        .put("fields", parametersJson(declaration.fields()));
                if (declaration.superName != null) saved.put("super", declaration.superName);
                if (declaration.isInterface) saved.put("interface", true);
                if (declaration.interfaceName != null) saved.put("implements", declaration.interfaceName);
                ConstructorDecl constructor = declaration.constructor();
                if (constructor != null) {
                    saved.put("constructor", new JSONObject().put("parameters", parametersJson(constructor.parameters))
                            .put("body", writeBlock(constructor.body)));
                }
                JSONArray methods = new JSONArray();
                for (MethodDecl method : declaration.methods()) methods.put(methodJson(method));
                saved.put("methods", methods);
                classes.put(saved);
            }
            out.put("classes", classes);
        }
        return out;
    }

    public static Block blockFrom(JSONObject json) throws JSONException {
        Block block = new Block();
        fill(block, LegacyFarmApi.upgrade(json));
        return block;
    }

    private static void fill(Block target, JSONObject json) throws JSONException {
        JSONArray stmts = json.getJSONArray("stmts");
        for (int i = 0; i < stmts.length(); i++) target.add(stmtFrom(stmts.getJSONObject(i)));
        JSONArray methods = json.optJSONArray("methods");
        for (int i = 0; methods != null && i < methods.length(); i++) {
            target.addMethod(methodFrom(methods.getJSONObject(i)));
        }
        JSONArray classes = json.optJSONArray("classes");
        for (int i = 0; classes != null && i < classes.length(); i++) {
            JSONObject saved = classes.getJSONObject(i);
            ClassDecl declaration = new ClassDecl(saved.getString("name"), saved.optBoolean("interface", false));
            declaration.superName = saved.optString("super", null);
            declaration.interfaceName = saved.optString("implements", null);
            for (VarDecl field : parametersFrom(saved.getJSONArray("fields"))) declaration.addField(field);
            if (saved.has("constructor")) {
                JSONObject object = saved.getJSONObject("constructor");
                ConstructorDecl constructor = new ConstructorDecl(parametersFrom(object.getJSONArray("parameters")));
                fill(constructor.body, object.getJSONObject("body"));
                declaration.setConstructor(constructor);
            }
            JSONArray members = saved.getJSONArray("methods");
            for (int n = 0; n < members.length(); n++) declaration.addMethod(methodFrom(members.getJSONObject(n)));
            target.addClass(declaration);
        }
    }

    private static JSONArray parametersJson(java.util.List<VarDecl> parameters) throws JSONException {
        JSONArray out = new JSONArray();
        for (VarDecl parameter : parameters) out.put(stmt(parameter));
        return out;
    }

    private static VarDecl[] parametersFrom(JSONArray parameters) throws JSONException {
        VarDecl[] out = new VarDecl[parameters.length()];
        for (int i = 0; i < out.length; i++) out[i] = (VarDecl) stmtFrom(parameters.getJSONObject(i));
        return out;
    }

    private static JSONObject methodJson(MethodDecl method) throws JSONException {
        JSONObject out = new JSONObject().put("name", method.name).put("returnType", method.returnType.name())
                .put("parameters", parametersJson(method.parameters)).put("body", writeBlock(method.body));
        if (method.override) out.put("override", true);
        return out;
    }

    private static MethodDecl methodFrom(JSONObject saved) throws JSONException {
        MethodDecl method = new MethodDecl(saved.getString("name"), JType.valueOf(saved.getString("returnType")),
                parametersFrom(saved.getJSONArray("parameters")));
        method.override = saved.optBoolean("override", false);
        fill(method.body, saved.getJSONObject("body"));
        return method;
    }

    private static JSONObject stmt(Stmt s) throws JSONException {
        if (s instanceof StreamForEachStmt each) {
            return new JSONObject().put("t", "streamforeach").put("call", streamJson(each.call));
        }
        if (s instanceof TeamStmt team) {
            return new JSONObject().put("t", "team").put("first", writeBlock(team.first))
                    .put("second", writeBlock(team.second));
        }
        if (s instanceof TogetherStmt together) {
            return new JSONObject().put("t", "together").put("first", writeBlock(together.first))
                    .put("second", writeBlock(together.second));
        }
        if (s instanceof SynchronizedStmt guarded) {
            return new JSONObject().put("t", "synchronized").put("monitorType", guarded.monitor.type.name())
                    .put("monitor", expr(guarded.monitor)).put("body", writeBlock(guarded.body));
        }
        if (s instanceof LockStmt guarded) {
            return new JSONObject().put("t", "lockblock").put("lock", expr(guarded.lock))
                    .put("body", writeBlock(guarded.body));
        }
        if (s instanceof FieldAssign) {
            FieldAssign assignment = (FieldAssign) s;
            return new JSONObject().put("t", "fieldassign").put("target", expr(assignment.target))
                    .put("value", expr(assignment.value));
        }
        if (s instanceof SuperCall) {
            return callJson("super", ((SuperCall) s).args);
        }
        if (s instanceof TryStmt) {
            TryStmt guarded = (TryStmt) s;
            JSONArray handlers = new JSONArray();
            for (TryStmt.Catch arm : guarded.catches()) {
                handlers.put(new JSONObject().put("type", arm.typeName).put("name", arm.name)
                        .put("body", writeBlock(arm.body)));
            }
            JSONObject out = new JSONObject().put("t", "try").put("body", writeBlock(guarded.tryBlock))
                    .put("catches", handlers);
            if (guarded.finallyBlock() != null) out.put("finally", writeBlock(guarded.finallyBlock()));
            return out;
        }
        if (s instanceof ForStmt) {
            ForStmt f = (ForStmt) s;
            return new JSONObject().put("t", "for")
                    .put("init", stmt(f.init))
                    .put("cond", expr(f.cond))
                    .put("update", expr(f.update))
                    .put("body", writeBlock(f.body));
        }
        if (s instanceof AssertStmt assertion) {
            JSONObject saved = new JSONObject().put("t", "assert").put("cond", expr(assertion.condition.get()));
            if (assertion.detail != null) saved.put("detail", expr(assertion.detail.get()));
            return saved;
        }
        if (s instanceof IfStmt) {
            IfStmt i = (IfStmt) s;
            JSONObject o = new JSONObject().put("t", "if")
                    .put("cond", expr(i.cond))
                    .put("then", writeBlock(i.thenBlock));
            JSONArray elifs = new JSONArray();
            for (IfStmt.Branch b : i.branches().subList(1, i.branches().size())) {
                elifs.put(new JSONObject().put("cond", expr(b.cond)).put("body", writeBlock(b.block)));
            }
            if (elifs.length() > 0) o.put("elifs", elifs);
            if (i.elseBlock() != null) o.put("else", writeBlock(i.elseBlock()));
            return o;
        }
        if (s instanceof WhileStmt) {
            WhileStmt w = (WhileStmt) s;
            return new JSONObject().put("t", "while").put("cond", expr(w.cond)).put("body", writeBlock(w.body));
        }
        if (s instanceof DoWhileStmt) {
            DoWhileStmt loop = (DoWhileStmt) s;
            return new JSONObject().put("t", "dowhile").put("cond", expr(loop.cond))
                    .put("body", writeBlock(loop.body));
        }
        if (s instanceof ForEachStmt) {
            ForEachStmt f = (ForEachStmt) s;
            JSONObject o = new JSONObject().put("t", "foreach").put("var", f.variable.name)
                    .put("body", writeBlock(f.body));
            if (f.source != null) o.put("elem", f.variable.type.name()).put("src", f.source);
            return o;
        }
        if (s instanceof SwitchStmt) {
            SwitchStmt sw = (SwitchStmt) s;
            JSONArray cases = new JSONArray();
            for (SwitchStmt.Case c : sw.cases()) {
                JSONObject arm = new JSONObject().put("label", c.value()).put("body", writeBlock(c.body));
                if (c.fallsThrough()) arm.put("fallThrough", true);
                cases.put(arm);
            }
            JSONObject o = new JSONObject().put("t", "switch").put("on", sw.subject.type.name())
                    .put("subject", expr(sw.subject)).put("cases", cases);
            if (sw.defaultBlock() != null) o.put("default", writeBlock(sw.defaultBlock()));
            return o;
        }
        if (s instanceof VarDecl) {
            VarDecl d = (VarDecl) s;
            JSONObject saved = new JSONObject().put("t", "var")
                    .put("type", d.type.name())
                    .put("name", d.name)
                    .put("init", expr(d.init));
            if (d.isFinal) saved.put("final", true);
            if (d.isStatic) saved.put("static", true);
            if (d.volatileField) saved.put("volatile", true);
            return saved;
        }
        if (s instanceof Assign) {
            Assign a = (Assign) s;
            return new JSONObject().put("t", "assign")
                    .put("type", a.value.type.name())
                    .put("name", a.name)
                    .put("value", expr(a.value));
        }
        if (s instanceof ListAdd) {
            ListAdd a = (ListAdd) s;
            return new JSONObject().put("t", "listadd").put("name", a.name).put("value", expr(a.value));
        }
        if (s instanceof ArrayAssign) {
            ArrayAssign assignment = (ArrayAssign) s;
            JSONObject saved = new JSONObject().put("t", "arrayassign").put("name", assignment.name)
                    .put("index", expr(assignment.index)).put("value", expr(assignment.value));
            if (assignment.column != null) saved.put("column", expr(assignment.column));
            return saved;
        }
        if (s instanceof ObjectArrayAssign) {
            ObjectArrayAssign assignment = (ObjectArrayAssign) s;
            return new JSONObject().put("t", "objectarrayassign").put("name", assignment.name)
                    .put("element", assignment.elementType.name())
                    .put("index", expr(assignment.index)).put("value", expr(assignment.value));
        }
        if (s instanceof ListSet) {
            ListSet set = (ListSet) s;
            return new JSONObject().put("t", "listset").put("name", set.name)
                    .put("index", expr(set.index)).put("value", expr(set.value));
        }
        if (s instanceof ListRemove) {
            ListRemove remove = (ListRemove) s;
            return new JSONObject().put("t", "listremove").put("name", remove.name)
                    .put("index", expr(remove.index));
        }
        if (s instanceof CompoundAssign) {
            CompoundAssign assignment = (CompoundAssign) s;
            return new JSONObject().put("t", assignment.operation == CompoundAssign.Operation.ADD
                    ? "intaddassign" : "intsubassign").put("name", assignment.name)
                    .put("value", expr(assignment.value));
        }
        if (s instanceof DecrementStmt) {
            return new JSONObject().put("t", "decrement").put("name", ((DecrementStmt) s).name);
        }
        if (s instanceof BreakStmt) return new JSONObject().put("t", "break");
        if (s instanceof ContinueStmt) return new JSONObject().put("t", "continue");
        if (s instanceof ReturnStmt) {
            ReturnStmt returned = (ReturnStmt) s;
            JSONObject out = new JSONObject().put("t", "return");
            if (returned.value != null) {
                out.put("type", returned.value.type.name()).put("value", expr(returned.value));
            }
            return out;
        }
        if (s instanceof ExprStmt) {
            return new JSONObject().put("t", "expr").put("e", expr(((ExprStmt) s).expr));
        }
        throw new JSONException("Cannot save " + s.getClass().getSimpleName());
    }

    private static Stmt stmtFrom(JSONObject o) throws JSONException {
        String t = o.getString("t");
        switch (t) {
            case "streamforeach":
                return new StreamForEachStmt(streamFrom(o.getJSONObject("call")));
            case "team": {
                TeamStmt team = new TeamStmt();
                fill(team.first, o.getJSONObject("first"));
                fill(team.second, o.getJSONObject("second"));
                return team;
            }
            case "together": {
                TogetherStmt together = new TogetherStmt();
                fill(together.first, o.getJSONObject("first"));
                fill(together.second, o.getJSONObject("second"));
                return together;
            }
            case "synchronized": {
                SynchronizedStmt guarded = new SynchronizedStmt(JType.valueOf(o.getString("monitorType")),
                        exprFrom(o.getJSONObject("monitor")));
                fill(guarded.body, o.getJSONObject("body"));
                return guarded;
            }
            case "lockblock": {
                LockStmt guarded = new LockStmt(exprFrom(o.getJSONObject("lock")));
                fill(guarded.body, o.getJSONObject("body"));
                return guarded;
            }
            case "fieldassign":
                return new FieldAssign((FieldAccess) exprFrom(o.getJSONObject("target")),
                        exprFrom(o.getJSONObject("value")));
            case "super":
                return new SuperCall(argumentTypes(o), argumentsFrom(o));
            case "try": {
                TryStmt guarded = new TryStmt();
                fill(guarded.tryBlock, o.getJSONObject("body"));
                JSONArray handlers = o.optJSONArray("catches");
                for (int n = 0; handlers != null && n < handlers.length(); n++) {
                    JSONObject arm = handlers.getJSONObject(n);
                    fill(guarded.addCatch(arm.getString("type"), arm.getString("name")).body,
                            arm.getJSONObject("body"));
                }
                if (o.has("finally")) fill(guarded.addFinally(), o.getJSONObject("finally"));
                return guarded;
            }
            case "for": {
                ForStmt f = new ForStmt(
                        (VarDecl) stmtFrom(o.getJSONObject("init")),
                        exprFrom(o.getJSONObject("cond")),
                        exprFrom(o.getJSONObject("update")));
                fill(f.body, o.getJSONObject("body"));
                return f;
            }
            case "assert":
                return new AssertStmt(exprFrom(o.getJSONObject("cond")),
                        o.has("detail") ? exprFrom(o.getJSONObject("detail")) : null);
            case "if": {
                IfStmt i = new IfStmt(exprFrom(o.getJSONObject("cond")), o.has("else"));
                fill(i.thenBlock, o.getJSONObject("then"));
                JSONArray elifs = o.optJSONArray("elifs");
                for (int n = 0; elifs != null && n < elifs.length(); n++) {
                    JSONObject e = elifs.getJSONObject(n);
                    fill(i.addElseIf(exprFrom(e.getJSONObject("cond"))).block, e.getJSONObject("body"));
                }
                if (i.elseBlock() != null) fill(i.elseBlock(), o.getJSONObject("else"));
                return i;
            }
            case "while": {
                WhileStmt w = new WhileStmt(exprFrom(o.getJSONObject("cond")));
                fill(w.body, o.getJSONObject("body"));
                return w;
            }
            case "dowhile": {
                DoWhileStmt loop = new DoWhileStmt(exprFrom(o.getJSONObject("cond")));
                fill(loop.body, o.getJSONObject("body"));
                return loop;
            }
            case "foreach": {
                ForEachStmt f = o.has("src")
                        ? new ForEachStmt(JType.valueOf(o.getString("elem")), o.getString("var"), o.getString("src"))
                        : new ForEachStmt(o.getString("var"));
                fill(f.body, o.getJSONObject("body"));
                return f;
            }
            case "switch": {
                JType on = JType.valueOf(o.optString("on", JType.INT.name()));
                SwitchStmt sw = new SwitchStmt(on, exprFrom(o.getJSONObject("subject")));
                JSONArray cases = o.getJSONArray("cases");
                for (int n = 0; n < cases.length(); n++) {
                    JSONObject c = cases.getJSONObject(n);
                    Object label = on == JType.STRING ? (Object) c.getString("label") : (Object) c.getInt("label");
                    SwitchStmt.Case arm = sw.addCase(label);
                    arm.setFallThrough(c.optBoolean("fallThrough", false));
                    fill(arm.body, c.getJSONObject("body"));
                }
                if (o.has("default")) fill(sw.addDefault(), o.getJSONObject("default"));
                return sw;
            }
            case "var": {
                VarDecl declaration = new VarDecl(JType.valueOf(o.getString("type")), o.getString("name"),
                        exprFrom(o.getJSONObject("init")), o.optBoolean("final"), o.optBoolean("static"));
                declaration.volatileField = o.optBoolean("volatile");
                return declaration;
            }
            case "assign":
                return new Assign(JType.valueOf(o.getString("type")), o.getString("name"),
                        exprFrom(o.getJSONObject("value")));
            case "listadd":
                return new ListAdd(o.getString("name"), exprFrom(o.getJSONObject("value")));
            case "arrayassign":
                return new ArrayAssign(o.getString("name"), exprFrom(o.getJSONObject("index")),
                        o.has("column") ? exprFrom(o.getJSONObject("column")) : null,
                        exprFrom(o.getJSONObject("value")));
            case "objectarrayassign":
                return new ObjectArrayAssign(o.getString("name"), JType.valueOf(o.getString("element")),
                        exprFrom(o.getJSONObject("index")), exprFrom(o.getJSONObject("value")));
            case "listset":
                return new ListSet(o.getString("name"), exprFrom(o.getJSONObject("index")),
                        exprFrom(o.getJSONObject("value")));
            case "listremove":
                return new ListRemove(o.getString("name"), exprFrom(o.getJSONObject("index")));
            case "intsubassign":
                return new CompoundAssign(o.getString("name"), CompoundAssign.Operation.SUBTRACT,
                        exprFrom(o.getJSONObject("value")));
            case "intaddassign":
                return new CompoundAssign(o.getString("name"), exprFrom(o.getJSONObject("value")));
            case "decrement":
                return new DecrementStmt(o.getString("name"));
            case "break":
                return new BreakStmt();
            case "continue":
                return new ContinueStmt();
            case "return":
                return o.has("value") ? new ReturnStmt(JType.valueOf(o.getString("type")),
                        exprFrom(o.getJSONObject("value"))) : new ReturnStmt();
            case "expr":
                return new ExprStmt(exprFrom(o.getJSONObject("e")));
            default:
                throw new JSONException("Unknown statement type " + t);
        }
    }

    private static JSONObject expr(ExprSlot slot) throws JSONException {
        return expr(slot.get());
    }

    private static JSONObject expr(Expr e) throws JSONException {
        if (e instanceof NewReentrantLock) return new JSONObject().put("t", "newlock");
        if (e instanceof AtomicCounter counter) {
            return new JSONObject().put("t", "atomic").put("op", counter.op.name())
                    .put("operand", expr(counter.operand));
        }
        if (e instanceof NewObject) {
            NewObject creation = (NewObject) e;
            return callJson("newobject", creation.args).put("class", creation.className);
        }
        if (e instanceof InstanceOf check) {
            return new JSONObject().put("t", "instanceof").put("operand", expr(check.operand))
                    .put("type", check.target.name());
        }
        if (e instanceof StaticConstant constant) {
            return new JSONObject().put("t", "staticfield").put("class", constant.className)
                    .put("field", constant.fieldName);
        }
        if (e instanceof FieldAccess) {
            FieldAccess access = (FieldAccess) e;
            return new JSONObject().put("t", "field").put("recv", expr(access.receiver))
                    .put("recvType", access.receiver.type.name()).put("name", access.name)
                    .put("type", access.type.name());
        }
        if (e instanceof InstanceCall) {
            InstanceCall call = (InstanceCall) e;
            return callJson("instancecall", call.args).put("recv", expr(call.receiver))
                    .put("recvType", call.receiver.type.name()).put("name", call.name)
                    .put("returnType", call.returnType.name());
        }
        if (e instanceof ThisRef) return new JSONObject().put("t", "this");
        if (e instanceof FarmhandRef) return new JSONObject().put("t", "farmer");
        if (e instanceof Literal) {
            Literal lit = (Literal) e;
            Object v = lit.value instanceof Enum<?> constant ? constant.name() : lit.value;
            return new JSONObject().put("t", "lit").put("type", lit.type.name()).put("v", v == null ? JSONObject.NULL : v);
        }
        if (e instanceof VarRef) {
            return new JSONObject().put("t", "ref").put("name", ((VarRef) e).name);
        }
        if (e instanceof PostIncrement) {
            return new JSONObject().put("t", "inc").put("name", ((PostIncrement) e).name);
        }
        if (e instanceof Not) {
            return new JSONObject().put("t", "not").put("e", expr(((Not) e).operand));
        }
        if (e instanceof Binary) {
            Binary b = (Binary) e;
            JSONObject o = new JSONObject().put("t", "bin").put("op", b.op().name())
                    .put("l", expr(b.left)).put("r", expr(b.right));
            if (b.operandType != b.op().family.operand) o.put("num", b.operandType.name());
            return o;
        }
        if (e instanceof StringConcat concat) {
            return new JSONObject().put("t", "strconcat").put("l", expr(concat.left))
                    .put("rightType", concat.right.type.name()).put("r", expr(concat.right));
        }
        if (e instanceof NewStringIntMap) return new JSONObject().put("t", "newstringintmap");
        if (e instanceof StringIntMapCall call) {
            return callJson("stringintmapcall", call.arguments).put("name", call.name).put("op", call.op.name());
        }
        if (e instanceof StringLength length) {
            return new JSONObject().put("t", "strlen").put("e", expr(length.text));
        }
        if (e instanceof CropEquals comparison) {
            return new JSONObject().put("t", "cropeq").put("l", expr(comparison.left)).put("r", expr(comparison.right));
        }
        if (e instanceof StrEquals) {
            StrEquals eq = (StrEquals) e;
            return new JSONObject().put("t", "streq").put("l", expr(eq.left)).put("r", expr(eq.right));
        }
        if (e instanceof NewList) {
            return new JSONObject().put("t", "newlist");
        }
        if (e instanceof NewIntGrid grid) {
            return new JSONObject().put("t", "newintgrid")
                    .put("rows", expr(grid.rows)).put("columns", expr(grid.columns));
        }
        if (e instanceof IntArrayLiteral array) {
            return callJson("intarrayliteral", array.elements());
        }
        if (e instanceof NewIntArray) {
            return new JSONObject().put("t", "newintarray").put("length", expr(((NewIntArray) e).length));
        }
        if (e instanceof NewObjectArray) {
            NewObjectArray array = (NewObjectArray) e;
            return new JSONObject().put("t", "newobjectarray")
                    .put("element", array.elementType.name()).put("length", expr(array.length));
        }
        if (e instanceof ObjectArrayGet) {
            ObjectArrayGet access = (ObjectArrayGet) e;
            return new JSONObject().put("t", "objectarrayget").put("name", access.name)
                    .put("element", access.elementType.name()).put("index", expr(access.index));
        }
        if (e instanceof ListContains) {
            ListContains contains = (ListContains) e;
            return new JSONObject().put("t", "listcontains").put("name", contains.name)
                    .put("value", expr(contains.value));
        }
        if (e instanceof CollectionCall call) {
            JSONObject o = new JSONObject().put("t", "collectioncall").put("op", call.op.name());
            if (call.receiver != null) o.put("receiver", call.receiver);
            if (call.parameter != null) o.put("parameter", call.parameter);
            if (call.argument != null) o.put("argument", expr(call.argument));
            return o;
        }
        if (e instanceof FunctionValue function) {
            return new JSONObject().put("t", "functionvalue").put("type", function.function.name())
                    .put("callback", callbackJson(function.callback));
        }
        if (e instanceof FunctionCall function) {
            return new JSONObject().put("t", "functioncall").put("type", function.function.name())
                    .put("name", function.name).put("argument", expr(function.argument));
        }
        if (e instanceof StreamCall stream) {
            return streamJson(stream);
        }
        if (e instanceof Access) {
            Access a = (Access) e;
            JSONArray args = new JSONArray();
            for (ExprSlot arg : a.args) args.put(expr(arg));
            return new JSONObject().put("t", "access").put("kind", a.kind.name())
                    .put("name", a.name).put("args", args);
        }
        if (e instanceof UserCall) {
            UserCall call = (UserCall) e;
            JSONArray args = new JSONArray();
            JSONArray types = new JSONArray();
            for (ExprSlot argument : call.args) {
                args.put(expr(argument));
                types.put(argument.type.name());
            }
            return new JSONObject().put("t", "usercall").put("name", call.name)
                    .put("returnType", call.returnType.name()).put("types", types).put("args", args);
        }
        if (e instanceof MethodCall) {
            MethodCall c = (MethodCall) e;
            JSONArray args = new JSONArray();
            for (ExprSlot a : c.args) args.put(expr(a));
            return new JSONObject().put("t", "call").put("recv", c.receiver)
                    .put("name", c.name).put("args", args);
        }
        throw new JSONException("Cannot save " + e.getClass().getSimpleName());
    }

    private static JSONObject streamJson(StreamCall stream) throws JSONException {
        JSONArray stages = new JSONArray();
        for (StreamCall.Stage stage : stream.stages) {
            stages.put(new JSONObject().put("kind", stage.kind().name())
                    .put("callback", callbackJson(stage.callback())));
        }
        JSONObject out = new JSONObject().put("t", "stream").put("source", stream.source)
                .put("terminal", stream.terminal.name()).put("stages", stages);
        if (stream.callback != null) out.put("callback", callbackJson(stream.callback));
        return out;
    }

    private static JSONObject callbackJson(StreamCallback callback) throws JSONException {
        if (callback instanceof FunctionVariable stored) {
            return new JSONObject().put("t", "functionvariable").put("name", stored.name)
                    .put("type", stored.function.name());
        }
        if (callback instanceof IntegerMethodRef reference) {
            return new JSONObject().put("t", "integerref").put("kind", reference.kind.name());
        }
        if (callback instanceof StreamMethodRef ref) {
            return new JSONObject().put("t", "methodref").put("receiver", ref.receiver).put("method", ref.method);
        }
        if (callback instanceof StreamLambda lambda) {
            JSONObject out = new JSONObject().put("t", "lambda").put("parameter", lambda.parameter.name);
            if (lambda.body != null) return out.put("body", writeBlock(lambda.body));
            return out.put("result", lambda.resultType.name()).put("expression", expr(lambda.expression));
        }
        throw new JSONException("Cannot save stream callback " + callback.getClass().getSimpleName());
    }

    private static StreamCall streamFrom(JSONObject saved) throws JSONException {
        JSONArray array = saved.getJSONArray("stages");
        List<StreamCall.Stage> stages = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject stage = array.getJSONObject(i);
            stages.add(new StreamCall.Stage(StreamCall.StageKind.valueOf(stage.getString("kind")),
                    callbackFrom(stage.getJSONObject("callback"))));
        }
        return new StreamCall(saved.getString("source"), StreamCall.Terminal.valueOf(saved.getString("terminal")),
                stages, saved.has("callback") ? callbackFrom(saved.getJSONObject("callback")) : null);
    }

    private static StreamCallback callbackFrom(JSONObject saved) throws JSONException {
        String tag = saved.getString("t");
        if (tag.equals("functionvariable")) {
            return new FunctionVariable(saved.getString("name"), FunctionType.valueOf(saved.getString("type")));
        }
        if (tag.equals("integerref")) {
            return new IntegerMethodRef(IntegerMethodRef.Kind.valueOf(saved.getString("kind")));
        }
        if (tag.equals("methodref")) {
            return new StreamMethodRef(saved.getString("receiver"), saved.getString("method"));
        }
        if (!tag.equals("lambda")) throw new JSONException("Unknown stream callback " + tag);
        String parameter = saved.getString("parameter");
        if (!saved.has("body")) {
            return new StreamLambda(parameter, JType.valueOf(saved.getString("result")),
                    exprFrom(saved.getJSONObject("expression")));
        }
        StreamLambda lambda = new StreamLambda(parameter);
        fill(lambda.body, saved.getJSONObject("body"));
        return lambda;
    }

    private static Expr exprFrom(JSONObject o) throws JSONException {
        String t = o.getString("t");
        switch (t) {
            case "newlock":
                return new NewReentrantLock();
            case "atomic":
                return new AtomicCounter(AtomicCounter.Op.valueOf(o.getString("op")),
                        exprFrom(o.getJSONObject("operand")));
            case "lit": {
                JType type = JType.valueOf(o.getString("type"));
                if (o.isNull("v")) return new Literal(type, null);
                if (type == JType.INT) return new Literal(type, o.getInt("v"));
                if (type == JType.LONG) return new Literal(type, o.getLong("v"));
                if (type == JType.BOOLEAN) return new Literal(type, o.getBoolean("v"));
                if (type == JType.DOUBLE) return new Literal(type, o.getDouble("v"));
                if (type == JType.STRING) return new Literal(type, o.getString("v"));
                if (type == JType.DIRECTION) return new Literal(type, Direction.valueOf(o.getString("v")));
                if (type == JType.CROP) return new Literal(type, Crop.valueOf(o.getString("v")));
                throw new JSONException("Unsupported literal type " + type);
            }
            case "this": return new ThisRef();
            case "instanceof": return new InstanceOf(exprFrom(o.getJSONObject("operand")), JType.valueOf(o.getString("type")));
            case "staticfield": return new StaticConstant(o.getString("class"), o.getString("field"));
            case "farmer": return new FarmhandRef();
            case "newobject": return new NewObject(o.getString("class"), argumentTypes(o), argumentsFrom(o));
            case "field": return new FieldAccess(exprFrom(o.getJSONObject("recv")),
                    JType.valueOf(o.getString("recvType")), o.getString("name"), JType.valueOf(o.getString("type")));
            case "instancecall": return new InstanceCall(exprFrom(o.getJSONObject("recv")),
                    JType.valueOf(o.getString("recvType")), o.getString("name"),
                    JType.valueOf(o.getString("returnType")), argumentTypes(o), argumentsFrom(o));
            case "ref":
                return new VarRef(o.getString("name"));
            case "inc":
                return new PostIncrement(o.getString("name"));
            case "not":
                return new Not(exprFrom(o.getJSONObject("e")));
            case "bin": {
                Binary.Op op = Binary.Op.valueOf(o.getString("op"));
                JType operands = o.has("num") ? JType.valueOf(o.getString("num")) : op.family.operand;
                return new Binary(op, operands,
                        exprFrom(o.getJSONObject("l")), exprFrom(o.getJSONObject("r")));
            }
            case "strconcat":
                return new StringConcat(exprFrom(o.getJSONObject("l")), JType.valueOf(o.getString("rightType")),
                        exprFrom(o.getJSONObject("r")));
            case "newstringintmap":
                return new NewStringIntMap();
            case "stringintmapcall":
                return new StringIntMapCall(o.getString("name"), StringIntMapCall.Op.valueOf(o.getString("op")),
                        argumentsFrom(o));
            case "strlen":
                return new StringLength(exprFrom(o.getJSONObject("e")));
            case "cropeq":
                return new CropEquals(exprFrom(o.getJSONObject("l")), exprFrom(o.getJSONObject("r")));
            case "streq":
                return new StrEquals(exprFrom(o.getJSONObject("l")), exprFrom(o.getJSONObject("r")));
            case "newlist":
                return new NewList();
            case "newintgrid":
                return new NewIntGrid(exprFrom(o.getJSONObject("rows")), exprFrom(o.getJSONObject("columns")));
            case "intarrayliteral":
                return new IntArrayLiteral(argumentsFrom(o));
            case "newintarray":
                return new NewIntArray(exprFrom(o.getJSONObject("length")));
            case "newobjectarray":
                return new NewObjectArray(JType.valueOf(o.getString("element")), exprFrom(o.getJSONObject("length")));
            case "objectarrayget":
                return new ObjectArrayGet(o.getString("name"), JType.valueOf(o.getString("element")),
                        exprFrom(o.getJSONObject("index")));
            case "listcontains":
                return new ListContains(o.getString("name"), exprFrom(o.getJSONObject("value")));
            case "collectioncall":
                return new CollectionCall(CollectionCall.Op.valueOf(o.getString("op")),
                        o.optString("receiver", null), o.optString("parameter", null),
                        o.has("argument") ? exprFrom(o.getJSONObject("argument")) : null);
            case "functionvalue":
                return new FunctionValue(FunctionType.valueOf(o.getString("type")),
                        callbackFrom(o.getJSONObject("callback")));
            case "functioncall":
                return new FunctionCall(o.getString("name"), FunctionType.valueOf(o.getString("type")),
                        exprFrom(o.getJSONObject("argument")));
            case "stream":
                return streamFrom(o);
            case "access": {
                JSONArray arr = o.getJSONArray("args");
                Expr[] args = new Expr[arr.length()];
                for (int i = 0; i < args.length; i++) args[i] = exprFrom(arr.getJSONObject(i));
                return new Access(Access.Kind.valueOf(o.getString("kind")), o.getString("name"), args);
            }
            case "usercall": {
                JSONArray arr = o.getJSONArray("args");
                JSONArray savedTypes = o.getJSONArray("types");
                Expr[] args = new Expr[arr.length()];
                JType[] types = new JType[arr.length()];
                for (int i = 0; i < args.length; i++) {
                    args[i] = exprFrom(arr.getJSONObject(i));
                    types[i] = JType.valueOf(savedTypes.getString(i));
                }
                return new UserCall(o.getString("name"), JType.valueOf(o.getString("returnType")), types, args);
            }
            case "call": {
                JSONArray arr = o.getJSONArray("args");
                Expr[] args = new Expr[arr.length()];
                for (int i = 0; i < args.length; i++) args[i] = exprFrom(arr.getJSONObject(i));
                return new MethodCall(o.getString("recv"), o.getString("name"), args);
            }
            default:
                throw new JSONException("Unknown expression type " + t);
        }
    }
    private static JSONObject callJson(String tag, java.util.List<ExprSlot> arguments) throws JSONException {
        JSONArray args = new JSONArray();
        JSONArray types = new JSONArray();
        for (ExprSlot argument : arguments) {
            args.put(expr(argument));
            types.put(argument.type.name());
        }
        return new JSONObject().put("t", tag).put("types", types).put("args", args);
    }

    private static Expr[] argumentsFrom(JSONObject saved) throws JSONException {
        JSONArray array = saved.getJSONArray("args");
        Expr[] out = new Expr[array.length()];
        for (int i = 0; i < out.length; i++) out[i] = exprFrom(array.getJSONObject(i));
        return out;
    }

    private static JType[] argumentTypes(JSONObject saved) throws JSONException {
        JSONArray array = saved.getJSONArray("types");
        JType[] out = new JType[array.length()];
        for (int i = 0; i < out.length; i++) out[i] = JType.valueOf(array.getString(i));
        return out;
    }

}
