package com.ccs.coffee_n_crops.editor;

import com.ccs.coffee_n_crops.lang.ast.Node;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.TryStmt;

/** Structural edits that preserve a try statement's required catch or finally arm. */
final class TryArmEdits {
    private TryArmEdits() {
    }

    /** Handles Backspace at the start of a direct try, catch or finally body. */
    static boolean stepOutOrRemove(EditorCursor cursor) {
        if (cursor.index != 0) return false;
        Block body = cursor.block;
        Node owner = body.parent;
        TryStmt stmt = owner instanceof TryStmt ? (TryStmt) owner
                : owner instanceof TryStmt.Catch ? (TryStmt) owner.parent : null;
        if (stmt == null || !(stmt.parent instanceof Block)) return false;
        Block outer = (Block) stmt.parent;
        int at = outer.indexOf(stmt);

        if (body.size() == 0 && owner instanceof TryStmt.Catch
                && (stmt.catches().size() > 1 || stmt.finallyBlock() != null)) {
            int catchIndex = stmt.catches().indexOf(owner);
            Block previous = catchIndex == 0 ? stmt.tryBlock : stmt.catches().get(catchIndex - 1).body;
            stmt.removeCatch((TryStmt.Catch) owner);
            cursor.moveTo(previous, previous.size());
            return true;
        }

        if (body.size() == 0 && body == stmt.finallyBlock() && !stmt.catches().isEmpty()) {
            Block previous = stmt.catches().get(stmt.catches().size() - 1).body;
            stmt.removeFinally();
            cursor.moveTo(previous, previous.size());
            return true;
        }

        boolean allEmpty = true;
        for (Block child : stmt.childBlocks()) allEmpty &= child.size() == 0;
        if (allEmpty) {
            outer.remove(at);
            cursor.moveTo(outer, at);
            return true;
        }

        // Removing the only handler would leave invalid Java and discard the player's try body.
        // Step above the whole statement, as Backspace already does for nonempty compound blocks.
        cursor.moveTo(outer, at);
        return true;
    }
}
