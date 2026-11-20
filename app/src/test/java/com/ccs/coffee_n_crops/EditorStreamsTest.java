package com.ccs.coffee_n_crops;

import com.ccs.coffee_n_crops.editor.BlockSpec;
import com.ccs.coffee_n_crops.editor.CodeRow;
import com.ccs.coffee_n_crops.editor.EditorCursor;
import com.ccs.coffee_n_crops.editor.PaletteProvider;
import com.ccs.coffee_n_crops.editor.RowFlattener;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.expr.Binary;
import com.ccs.coffee_n_crops.lang.ast.expr.Literal;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamCall;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamLambda;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.StreamForEachStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.print.JavaPrinter;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.progress.Unlock;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Editor checks for stream palette gating, editable arguments and block lambda rows. */
public final class EditorStreamsTest {
    @Test
    public void combinedPipelineUsesOneHeaderWithEditableCallbacks() {
        Block program = new Block();
        program.add(Ast.newList("stops"));
        Set<String> unlocks = Set.of(Unlock.STREAM_MAP, Unlock.STREAM_FILTER, Unlock.STREAM_FOR_EACH);
        String preview = "stops.stream().map(cell -> cell + 0).filter(cell -> cell >= 0).forEach(cell -> { });";
        BlockSpec offered = PaletteProvider.query(cursor(program, 1), unlocks, program).stream()
                .filter(block -> block.label.equals(preview)).findFirst().orElseThrow();
        assertEquals(BlockSpec.TAB_STREAMS, offered.tab);
        assertFalse(labels(cursor(program, 1), Set.of(Unlock.STREAM_FOR_EACH), program).contains(preview));
        assertFalse(labels(cursor(program, 1), unlocks, program).contains("for (int cell : stops) { }"));

        StreamLambda map = new StreamLambda("cell", JType.INT, new Binary(Binary.Op.ADD, new VarRef("cell"), Ast.lit(0)));
        StreamLambda filter = new StreamLambda("cell", JType.BOOLEAN, new Binary(Binary.Op.GE, new VarRef("cell"), Ast.lit(0)));
        StreamLambda consumer = new StreamLambda("cell");
        StreamCall call = new StreamCall("stops", StreamCall.Terminal.FOR_EACH,
                List.of(new StreamCall.Stage(StreamCall.StageKind.MAP, map),
                        new StreamCall.Stage(StreamCall.StageKind.FILTER, filter)), consumer);
        StreamForEachStmt each = new StreamForEachStmt(call);
        program.add(each);
        List<CodeRow> rows = RowFlattener.flatten(program, cursor(consumer.body, 0));
        CodeRow header = rows.stream().filter(row -> row.node == each && row.kind == CodeRow.Kind.HEADER)
                .findFirst().orElseThrow();
        CodeRow footer = rows.stream().filter(row -> row.node == each && row.kind == CodeRow.Kind.FOOTER)
                .findFirst().orElseThrow();
        assertEquals(preview.substring(0, preview.length() - 4), JavaPrinter.text(header.tokens));
        assertSame(consumer.body, header.cursorBlock);
        assertEquals("});", JavaPrinter.text(footer.tokens));
        assertSame(program, footer.cursorBlock);
        assertEquals(2, footer.cursorIndex);
        assertTrue(header.tokens.stream().anyMatch(token -> token.slot == ((Binary) map.expression.get()).right));
        assertTrue(header.tokens.stream().anyMatch(token -> token.slot == ((Binary) filter.expression.get()).right));
        assertTrue(labels(cursor(consumer.body, 0), Set.of(Unlock.WALK_TO), program)
                .contains("farmer.walkTo(cell, 0);"));
        assertTrue(labels(cursor(program, 2), Set.of(Unlock.WALK_TO), program)
                .contains("farmer.walkTo(0, 0);"));
    }

    @Test
    public void terminalChoicesKeepCountLongAndAnyMatchBoolean() {
        Block program = new Block();
        program.add(Ast.newList("stops"));
        Set<String> unlocks = Set.of(Unlock.STREAM_COUNT, Unlock.STREAM_FILTER, Unlock.STREAM_ANY_MATCH, Unlock.IF);
        assertTrue(labels(cursor(program, 1), unlocks, program).containsAll(Set.of(
                "long count = stops.stream().filter(cell -> cell >= 0).count();",
                "boolean found = stops.stream().anyMatch(cell -> cell == 0);")));
        VarDecl count = new VarDecl(JType.LONG, "count", new Literal(JType.LONG, 0L));
        program.add(count);
        program.add(Ast.declare(JType.BOOLEAN, "found"));
        assertTrue(labels(cursor(program, 3), unlocks, program).containsAll(Set.of(
                "if (count == 3L) { }", "if (found) { }")));

        EditorCursor slot = cursor(program, 1);
        slot.slot = count.init;
        Set<String> choices = labels(slot, unlocks, program);
        assertTrue(choices.contains("3L"));
        assertTrue(choices.contains("stops.stream().filter(cell -> cell >= 0).count()"));
        assertFalse(choices.contains("stops.stream().anyMatch(cell -> cell == 0)"));
    }

    @Test
    public void methodReferenceNeedsAnotherListInScope() {
        Block program = new Block();
        program.add(Ast.newList("stops"));
        program.add(Ast.newList("route"));
        Set<String> unlocks = Set.of(Unlock.STREAM_METHOD_REF, Unlock.FOR_EACH);
        String reference = "stops.stream().forEach(route::add);";
        assertFalse(labels(cursor(program, 1), unlocks, program).contains(reference));
        Set<String> visible = labels(cursor(program, 2), unlocks, program);
        assertTrue(visible.contains(reference));
        assertFalse(visible.contains("stops.stream().forEach(stops::add);"));
        assertTrue(visible.contains("for (int cell : route) { }"));
    }

    private static EditorCursor cursor(Block block, int at) {
        EditorCursor cursor = new EditorCursor();
        cursor.moveTo(block, at);
        return cursor;
    }

    private static Set<String> labels(EditorCursor cursor, Set<String> unlocks, Block program) {
        Set<String> labels = new HashSet<>();
        for (BlockSpec spec : PaletteProvider.query(cursor, unlocks, program)) labels.add(spec.label);
        return labels;
    }
}
