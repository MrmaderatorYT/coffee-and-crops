package com.ccs.coffee_n_crops;

import com.ccs.coffee_n_crops.editor.BlockSpec;
import com.ccs.coffee_n_crops.editor.CodeRow;
import com.ccs.coffee_n_crops.editor.EditorCursor;
import com.ccs.coffee_n_crops.editor.PaletteProvider;
import com.ccs.coffee_n_crops.editor.RowFlattener;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.expr.NewReentrantLock;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.LockStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.TeamStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.print.JavaPrinter;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.progress.Unlock;

import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Editor checks for independently addressed farmhands and editable lock guards. */
public final class EditorTeamTest {
    @Test public void workerPaletteUsesItsOwnFarmhandAndAllowsSameLoopName() {
        Block program = new Block();
        TeamStmt team = new TeamStmt();
        team.first.add(Ast.forCount("i", 3));
        program.add(team);
        Set<String> unlocks = Set.of(Unlock.TEAM, Unlock.FOR, Unlock.MOVE, Unlock.HARVEST, Unlock.WALK_TO,
                Unlock.SYNCHRONIZED);
        List<String> first = labels(cursor(team.first, 0), unlocks, program);
        assertTrue(first.containsAll(List.of("first.harvest();", "first.move(Direction.EAST);", "first.walkTo(0, 0);")));
        assertFalse(first.contains("farmer.harvest();"));
        assertFalse(first.contains("second.harvest();"));
        assertFalse(first.contains("synchronized (first) { }"));

        List<String> second = labels(cursor(team.second, 0), unlocks, program);
        assertTrue(second.containsAll(List.of("second.harvest();", "second.move(Direction.EAST);", "second.walkTo(0, 1);",
                "for (int i = 0; i < 3; i++) { }")));
        assertFalse(second.contains("first.harvest();"));
    }

    @Test public void lockSelectorCanOnlyChooseNamedLocksAndReleaseFollowsSelection() {
        Block program = new Block();
        program.add(new VarDecl(JType.REENTRANT_LOCK, "pumpA", new NewReentrantLock()));
        program.add(new VarDecl(JType.REENTRANT_LOCK, "pumpB", new NewReentrantLock()));
        program.add(Ast.declare(JType.INT, "width"));
        TeamStmt team = new TeamStmt();
        LockStmt guard = new LockStmt(new VarRef("pumpB"));
        team.second.add(guard);
        program.add(team);

        Set<String> unlocks = Set.of(Unlock.TEAM, Unlock.REENTRANT_LOCK);
        assertTrue(labels(cursor(team.second, 0), unlocks, program)
                .contains("pumpA.lock(); try { } finally { pumpA.unlock(); }"));
        EditorCursor edit = cursor(team.second, 0);
        edit.slot = guard.lock;
        assertEquals(List.of("pumpA", "pumpB", "✓ Done"), labels(edit, unlocks, program));

        guard.lock.set(new VarRef("pumpA"));
        List<CodeRow> rows = RowFlattener.flatten(program, null);
        CodeRow acquisition = row(rows, "pumpA.lock();");
        CodeRow entry = row(rows, "try {");
        CodeRow cleanup = row(rows, "pumpA.unlock();");
        assertSame(guard.lock, acquisition.tokens.stream().filter(token -> token.slot != null)
                .findFirst().orElseThrow().slot);
        assertSame(guard.body, entry.cursorBlock);
        assertSame(team.second, cleanup.cursorBlock);
        assertEquals(1, cleanup.cursorIndex);
        assertTrue(cleanup.tokens.stream().allMatch(token -> token.slot == null));
    }

    private static CodeRow row(List<CodeRow> rows, String text) {
        return rows.stream().filter(row -> JavaPrinter.text(row.tokens).equals(text)).findFirst().orElseThrow();
    }

    private static EditorCursor cursor(Block block, int index) {
        EditorCursor cursor = new EditorCursor();
        cursor.moveTo(block, index);
        return cursor;
    }

    private static List<String> labels(EditorCursor cursor, Set<String> unlocks, Block program) {
        return PaletteProvider.query(cursor, unlocks, program).stream().map((BlockSpec block) -> block.label)
                .collect(Collectors.toList());
    }
}
