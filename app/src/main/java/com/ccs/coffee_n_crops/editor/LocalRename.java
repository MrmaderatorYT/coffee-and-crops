package com.ccs.coffee_n_crops.editor;

import com.ccs.coffee_n_crops.lang.ast.AstWalk;
import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.check.ScopeResolver;
import com.ccs.coffee_n_crops.lang.check.Validator;
import com.ccs.coffee_n_crops.lang.serial.AstJson;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** Renames an ordinary local and the references in its lexical scope. */
final class LocalRename {
    private LocalRename() { }

    static boolean validName(String name) {
        // Both run and Program helpers have an implicit Farmhand parameter in this AST.
        return !name.equals("farmer") && Validator.validIdentifier(name);
    }

    static boolean available(Block block, int position, String oldName, String newName) {
        Set<String> unavailable = new HashSet<>();
        for (VarDecl local : ScopeResolver.visibleAt(block, position)) unavailable.add(local.name);
        for (int i = position + 1; i < block.size(); i++) {
            AstWalk.walk(block.get(i), node -> {
                if (node instanceof VarDecl declaration) unavailable.add(declaration.name);
            });
        }
        return !unavailable.contains(newName) && !unavailable.contains(oldName);
    }

    static boolean apply(Block program, Block block, int position, String newName) throws JSONException {
        VarDecl declaration = (VarDecl) block.get(position);
        String oldName = declaration.name;
        if (!validName(newName) || !available(block, position, oldName, newName)) return false;
        if (oldName.equals(newName)) return true;

        JSONObject saved = AstJson.toJson(block);
        JSONArray statements = saved.getJSONArray("stmts");
        statements.getJSONObject(position).put("name", newName);
        // A Java local starts at its declaration and is not visible outside this block.
        for (int i = position + 1; i < statements.length(); i++) {
            if (!rewrite(statements.getJSONObject(i), oldName, newName)) return false;
        }
        Block renamed = AstJson.blockFrom(saved);
        Validator.Problem before = Validator.check(program);
        List<Stmt> original = new ArrayList<>();
        int count = block.size();
        for (int i = position; i < count; i++) original.add(block.remove(position));
        while (renamed.size() > position) block.add(renamed.remove(position));
        Validator.Problem after = Validator.check(program);
        if (after == null || before != null && before.node == after.node && before.message.equals(after.message)) {
            return true;
        }
        while (block.size() > position) block.remove(position);
        for (Stmt statement : original) block.add(statement);
        return false;
    }

    private static boolean rewrite(JSONObject node, String oldName, String newName) throws JSONException {
        // Only fields that spell local references can change. Method names, field names and text
        // literals may have the same spelling but do not refer to this declaration.
        String key = switch (node.optString("t")) {
            case "ref", "inc", "assign", "intaddassign", "intsubassign", "decrement", "access",
                 "listadd", "listset", "listremove", "listcontains", "arrayassign", "objectarrayassign",
                 "objectarrayget", "stringintmapcall", "functioncall", "functionvariable" -> "name";
            case "foreach" -> "src";
            case "stream" -> "source";
            case "call" -> "recv";
            case "collectioncall", "methodref" -> "receiver";
            default -> null;
        };
        if (key != null && newName.equals(node.optString(key))) return false;
        if (key != null && oldName.equals(node.optString(key))) node.put(key, newName);
        for (Iterator<String> keys = node.keys(); keys.hasNext(); ) {
            Object value = node.get(keys.next());
            if (value instanceof JSONObject child && !rewrite(child, oldName, newName)) return false;
            if (value instanceof JSONArray array) {
                for (int i = 0; i < array.length(); i++) {
                    if (array.get(i) instanceof JSONObject child && !rewrite(child, oldName, newName)) return false;
                }
            }
        }
        return true;
    }
}
