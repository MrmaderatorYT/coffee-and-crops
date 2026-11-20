package com.ccs.coffee_n_crops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.ccs.coffee_n_crops.editor.BlockSpec;
import com.ccs.coffee_n_crops.editor.CodeRow;
import com.ccs.coffee_n_crops.editor.EditorCursor;
import com.ccs.coffee_n_crops.editor.PaletteProvider;
import com.ccs.coffee_n_crops.editor.RowFlattener;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.MethodDecl;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.ReturnStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.SwitchStmt;
import com.ccs.coffee_n_crops.lang.print.JavaPrinter;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.progress.Unlock;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** The method editor's projection and choices can be checked without a device. */
public class EditorMethodsTest {
    @Test
    public void helpersHaveSeparateFramesAndEmptyBodyInsertionPoints() {
        Block program = new Block();
        MethodDecl helper = new MethodDecl("tendRow", JType.VOID, Ast.declare(JType.INT, "y"));
        program.addMethod(helper);
        EditorCursor cursor = new EditorCursor();
        cursor.moveTo(helper.body, 0);

        List<CodeRow> rows = RowFlattener.flatten(program, cursor);
        assertEquals(5, rows.size());
        assertEquals("void run(Farmhand farmer) {", JavaPrinter.text(rows.get(0).tokens));
        assertEquals("void tendRow(Farmhand farmer, int y) {", JavaPrinter.text(rows.get(2).tokens));
        assertSame(helper, rows.get(2).node);
        assertTrue(rows.get(2).showsHighlight(helper.id));
        assertTrue(rows.get(2).showsHighlight(helper.body.id));
        assertSame(helper.body, rows.get(2).cursorBlock);
        assertEquals(CodeRow.Kind.CARET, rows.get(3).kind);
        assertSame(helper.body, rows.get(3).cursorBlock);
        assertEquals(CodeRow.Kind.FRAME_CLOSE, rows.get(4).kind);
        assertSame(helper.body, rows.get(4).cursorBlock);
        assertFalse(rows.get(4).showsHighlight(helper.body.id));
    }

    @Test
    public void parameterSlotsSeeTheirOwnMethodAndNotRunLocals() {
        Block program = new Block();
        program.add(Ast.declare(JType.INT, "steps"));
        MethodDecl helper = new MethodDecl("countRow", JType.INT, Ast.declare(JType.INT, "y"));
        ReturnStmt result = new ReturnStmt(JType.INT, new VarRef("y"));
        helper.body.add(result);
        program.addMethod(helper);
        EditorCursor cursor = new EditorCursor();
        cursor.moveTo(helper.body, 0);
        cursor.slot = result.value;

        Set<String> labels = labels(PaletteProvider.query(cursor, keys(Unlock.METHODS), program));
        assertTrue(labels.contains("y"));
        assertFalse(labels.contains("steps"));
        assertFalse(labels.contains("countRow(farmer, y)"));
        assertTrue(labels(PaletteProvider.query(cursor,
                keys(Unlock.METHODS, Unlock.RECURSION), program)).contains("countRow(farmer, y)"));
    }

    @Test
    public void methodsHaveIndependentNamesAndTypedCallChoices() {
        Block program = new Block();
        program.add(Ast.declare(JType.INT, "steps"));
        MethodDecl work = new MethodDecl("harvestRow", JType.VOID);
        program.addMethod(work);
        MethodDecl count = new MethodDecl("countRipe", JType.INT);
        count.body.add(new ReturnStmt(JType.INT, Ast.lit(0)));
        program.addMethod(count);
        EditorCursor cursor = new EditorCursor();
        cursor.moveTo(work.body, 0);

        List<BlockSpec> choices = PaletteProvider.query(cursor,
                keys(Unlock.INT, Unlock.METHODS, Unlock.RETURN), program);
        Set<String> labels = labels(choices);
        assertTrue(labels.contains("int steps = 0;"));
        assertTrue(labels.contains("int steps = countRipe(farmer);"));
        assertTrue(labels.contains("return;"));
        assertFalse(labels.contains("harvestRow(farmer);"));
        assertTrue(choices.stream().anyMatch(choice -> "countRipe(farmer);".equals(choice.label)
                && BlockSpec.TAB_METHODS.equals(choice.tab)));
    }

    @Test
    public void quickCallsContainExistingMethodsAndCanBeSeparatedFromNewDeclarations() {
        Block program = new Block();
        program.addMethod(new MethodDecl("harvestRow", JType.VOID));
        program.addMethod(new MethodDecl("countRipe", JType.INT));
        EditorCursor cursor = new EditorCursor();
        cursor.moveTo(program, 0);
        Set<String> unlocks = keys(Unlock.METHODS, Unlock.RETURN);

        List<BlockSpec> choices = PaletteProvider.query(cursor, unlocks, program);
        List<String> calls = choices.stream().filter(choice -> choice.methodRole == BlockSpec.MethodRole.CALL)
                .map(choice -> choice.label).collect(Collectors.toList());
        assertEquals(Arrays.asList("harvestRow(farmer);", "countRipe(farmer);", "int steps = countRipe(farmer);"), calls);
        assertTrue(choices.stream().anyMatch(choice -> choice.label.startsWith("+ void work(")
                && choice.methodRole == BlockSpec.MethodRole.CREATE));
        assertTrue(choices.stream().anyMatch(choice -> choice.label.equals("return;")
                && choice.methodRole == BlockSpec.MethodRole.NONE));

        MethodDecl helper = new MethodDecl("measure", JType.INT);
        ReturnStmt result = new ReturnStmt(JType.INT, Ast.lit(0));
        helper.body.add(result);
        program.addMethod(helper);
        cursor.moveTo(helper.body, 0);
        cursor.slot = result.value;
        List<BlockSpec> slotChoices = PaletteProvider.query(cursor, unlocks, program);
        assertEquals(Arrays.asList("countRipe(farmer)"), slotChoices.stream()
                .filter(choice -> choice.methodRole == BlockSpec.MethodRole.CALL)
                .map(choice -> choice.label).collect(Collectors.toList()));
        assertFalse(slotChoices.stream().anyMatch(choice -> choice.methodRole == BlockSpec.MethodRole.CREATE));
    }

    @Test
    public void caseLabelsRemainConstantAndReturningArmsHaveNoUnreachableBreak() {
        Block program = new Block();
        MethodDecl count = new MethodDecl("countRipe", JType.INT);
        SwitchStmt selection = new SwitchStmt(Ast.lit(0));
        SwitchStmt.Case arm = selection.addCase(0);
        arm.body.add(new ReturnStmt(JType.INT, Ast.lit(1)));
        selection.addDefault().add(new ReturnStmt(JType.INT, Ast.lit(0)));
        count.body.add(selection);
        program.addMethod(count);
        EditorCursor cursor = new EditorCursor();
        cursor.moveTo(arm.body, 0);
        cursor.slot = arm.label;

        Set<String> labels = labels(PaletteProvider.query(cursor,
                keys(Unlock.METHODS, Unlock.RECURSION), program));
        assertFalse(labels.contains("countRipe(farmer)"));
        List<CodeRow> rows = RowFlattener.flatten(program, null);
        assertFalse(rows.stream().anyMatch(row -> row.tokens != null
                && "break;".equals(JavaPrinter.text(row.tokens))));
    }

    private static Set<String> keys(String... keys) {
        return new HashSet<>(Arrays.asList(keys));
    }

    private static Set<String> labels(List<BlockSpec> choices) {
        Set<String> labels = new HashSet<>();
        for (BlockSpec choice : choices) labels.add(choice.label);
        return labels;
    }
}
