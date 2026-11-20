package com.ccs.coffee_n_crops.editor;

import com.ccs.coffee_n_crops.lang.ast.AstWalk;
import com.ccs.coffee_n_crops.lang.ast.ClassDecl;
import com.ccs.coffee_n_crops.lang.ast.Node;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.serial.AstJson;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/** Bounded, in-memory snapshots of edits and their active cursor. */
final class EditorHistory {
    private static final int MAX_EDITS = 32;

    private final Deque<Snapshot> undo = new ArrayDeque<>();
    private final Deque<Snapshot> redo = new ArrayDeque<>();

    // The save codec rebuilds node IDs. Traversal positions match equivalent trees instead.
    record Snapshot(String json, int cls, int block, int index, int declaration, int expression,
                    String tab, List<Integer> folded) {
        Block program() throws JSONException {
            return AstJson.blockFrom(new JSONObject(json));
        }
    }

    static Snapshot capture(Block program, ClassDecl selectedClass, EditorCursor cursor,
                            String tab, Set<Integer> folded) throws JSONException {
        List<Node> nodes = nodes(program);
        List<Integer> foldedPositions = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            if (folded.contains(nodes.get(i).id)) foldedPositions.add(i);
        }
        return new Snapshot(AstJson.toJson(program).toString(),
                nodes.indexOf(selectedClass), nodes.indexOf(cursor.block), cursor.index,
                nodes.indexOf(cursor.declaration), nodes.indexOf(cursor.slot == null ? null : cursor.slot.get()),
                tab, foldedPositions);
    }

    static List<Node> nodes(Block program) {
        List<Node> nodes = new ArrayList<>();
        AstWalk.walk(program, nodes::add);
        return nodes;
    }

    static Node nodeAt(List<Node> nodes, int index) {
        return index >= 0 && index < nodes.size() ? nodes.get(index) : null;
    }

    void record(Snapshot before, Block after) throws JSONException {
        if (before == null || before.json().equals(AstJson.toJson(after).toString())) return;
        if (undo.size() == MAX_EDITS) undo.removeLast();
        undo.push(before);
        redo.clear();
    }

    Snapshot take(boolean redoEdit, Snapshot current) {
        Deque<Snapshot> from = redoEdit ? redo : undo;
        if (from.isEmpty()) return null;
        (redoEdit ? undo : redo).push(current);
        return from.pop();
    }

    boolean canUndo() { return !undo.isEmpty(); }
    boolean canRedo() { return !redo.isEmpty(); }

    void clear() {
        undo.clear();
        redo.clear();
    }
}
