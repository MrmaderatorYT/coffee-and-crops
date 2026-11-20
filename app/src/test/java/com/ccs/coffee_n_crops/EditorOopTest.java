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
import com.ccs.coffee_n_crops.lang.ast.ClassDecl;
import com.ccs.coffee_n_crops.lang.ast.ConstructorDecl;
import com.ccs.coffee_n_crops.lang.ast.MethodDecl;
import com.ccs.coffee_n_crops.lang.ast.expr.Binary;
import com.ccs.coffee_n_crops.lang.ast.expr.FieldAccess;
import com.ccs.coffee_n_crops.lang.ast.expr.Literal;
import com.ccs.coffee_n_crops.lang.ast.expr.NewObject;
import com.ccs.coffee_n_crops.lang.ast.expr.ThisRef;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.FieldAssign;
import com.ccs.coffee_n_crops.lang.ast.stmt.SuperCall;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.ast.stmt.WhileStmt;
import com.ccs.coffee_n_crops.lang.check.ScopeResolver;
import com.ccs.coffee_n_crops.lang.print.JavaPrinter;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.progress.Unlock;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Class projection and context-sensitive choices without requiring Android widgets. */
public class EditorOopTest {
    private static Set<String> keys(String... values) { return new HashSet<>(Arrays.asList(values)); }

    private static Set<String> labels(EditorCursor cursor, Set<String> unlocks, Block program) {
        return PaletteProvider.query(cursor, unlocks, program).stream().map(b -> b.label).collect(Collectors.toSet());
    }

    private static EditorCursor cursor(Block block, ClassDecl cls) {
        EditorCursor cursor = new EditorCursor();
        cursor.moveTo(block, block.size());
        cursor.classDecl = cls;
        return cursor;
    }

    private static String text(CodeRow row) { return row.tokens == null ? "" : JavaPrinter.text(row.tokens); }

    @Test
    public void selectedClassShowsItsFieldsConstructorAndExactInstanceSignatures() {
        Block program = new Block();
        ClassDecl counter = new ClassDecl("Counter");
        counter.addField(Ast.declare(JType.INT, "value"));
        ConstructorDecl constructor = new ConstructorDecl(Ast.declare(JType.INT, "start"));
        constructor.body.add(new FieldAssign(new FieldAccess(new ThisRef(), counter.type(), "value", JType.INT), new VarRef("start")));
        counter.setConstructor(constructor);
        MethodDecl method = new MethodDecl("increment", JType.VOID);
        counter.addMethod(method);
        program.addClass(counter);
        List<CodeRow> rows = RowFlattener.flattenClass(program, counter, cursor(constructor.body, counter), Collections.emptySet());
        assertEquals("class Counter {", text(rows.get(0)));
        assertTrue(rows.stream().anyMatch(row -> text(row).equals("int value = 0;") && row.node == counter.fields().get(0)));
        CodeRow header = rows.stream().filter(row -> row.node == constructor).findFirst().get();
        assertEquals("Counter(int start) {", text(header));
        assertSame(constructor.body, header.cursorBlock);
        assertTrue(header.showsHighlight(constructor.body.id));
        assertTrue(header.showsHighlight(constructor.id));
        assertTrue(rows.stream().anyMatch(row -> text(row).equals("void increment() {")));
        assertFalse(rows.stream().anyMatch(row -> text(row).contains("increment(Farmhand")));
        assertTrue(rows.stream().anyMatch(row -> row.kind == CodeRow.Kind.CARET && row.cursorBlock == constructor.body));
        CodeRow footer = rows.get(rows.size() - 1);
        assertSame(counter, footer.node);
        assertFalse(footer.showsHighlight(counter.id));
    }

    @Test
    public void foldingHidesOnlyTheMemberBodyAndNeverPrintsFakeJava() {
        Block program = new Block();
        ClassDecl worker = new ClassDecl("Worker");
        MethodDecl work = new MethodDecl("work", JType.VOID);
        work.body.add(Ast.declare(JType.INT, "steps"));
        worker.addMethod(work);
        program.addClass(worker);
        List<CodeRow> rows = RowFlattener.flattenClass(program, worker, null, Collections.singleton(work.id));
        CodeRow header = rows.stream().filter(row -> row.node == work).findFirst().get();
        assertSame(work, header.foldMember);
        assertTrue(header.folded);
        assertFalse(rows.stream().anyMatch(row -> row.node == work.body.get(0)));
        assertFalse(rows.stream().anyMatch(row -> text(row).contains("…") || text(row).contains("...")));
        assertEquals(2, rows.stream().filter(row -> text(row).equals("}")).count());
    }

    @Test
    public void programKeepsItsDroneHelperButClassMethodsDoNotReceiveHiddenArguments() {
        Block program = new Block();
        MethodDecl helper = new MethodDecl("harvestRow", JType.VOID);
        program.addMethod(helper);
        ClassDecl worker = new ClassDecl("Worker");
        worker.addMethod(new MethodDecl("work", JType.VOID));
        program.addClass(worker);
        List<CodeRow> main = RowFlattener.flattenClass(program, null, cursor(program, null), Collections.emptySet());
        assertEquals("class Program {", text(main.get(0)));
        assertTrue(main.stream().anyMatch(row -> text(row).equals("void run(Farmhand farmer) {")));
        assertTrue(main.stream().anyMatch(row -> text(row).equals("void harvestRow(Farmhand farmer) {")));
        assertFalse(main.stream().anyMatch(row -> row.node == worker));
    }

    @Test
    public void classStructureChoicesDoNotInsertStatementsIntoRun() {
        Block program = new Block();
        ClassDecl rowPlan = new ClassDecl("RowPlan");
        program.addClass(rowPlan);
        EditorCursor cursor = cursor(program, rowPlan);
        cursor.declaration = rowPlan;
        Set<String> choices = labels(cursor, keys(Unlock.CLASSES, Unlock.CONSTRUCTORS, Unlock.HARVEST), program);
        assertTrue(choices.contains("+ int row = 0;"));
        assertTrue(choices.contains("+ RowPlan() { }"));
        assertFalse(choices.contains("farmer.harvest();"));
        rowPlan.addField(Ast.declare(JType.INT, "row"));
        assertTrue(labels(cursor, keys(Unlock.CLASSES), program).contains("+ int width = 0;"));
    }

    @Test
    public void objectFieldReadsAndWritesAreAvailableInTheRunLoop() {
        Block program = new Block();
        ClassDecl counter = new ClassDecl("Counter");
        counter.addField(Ast.declare(JType.INT, "value"));
        program.addClass(counter);
        program.add(new VarDecl(counter.type(), "counter", new NewObject(counter)));
        WhileStmt loop = new WhileStmt(new Binary(Binary.Op.LT, Ast.lit(0), Ast.lit(5)));
        program.add(loop);
        Set<String> unlocks = keys(Unlock.CLASSES, Unlock.INT, Unlock.ASSIGN, Unlock.MATH);
        EditorCursor cursor = cursor(loop.body, null);
        assertTrue(labels(cursor, unlocks, program).contains("counter.value = counter.value + 1;"));
        cursor.slot = ((Binary) loop.cond.get()).left;
        assertTrue(labels(cursor, unlocks, program).contains("counter.value"));
    }

    @Test
    public void instanceDroneActionsRequireARealFieldOrParameter() {
        Block program = new Block();
        ClassDecl worker = new ClassDecl("Worker");
        worker.addField(new VarDecl(JType.FARMHAND, "farmer", new Literal(JType.FARMHAND, null)));
        MethodDecl work = new MethodDecl("work", JType.VOID);
        worker.addMethod(work);
        program.addClass(worker);
        Set<String> unlocks = keys(Unlock.CLASSES, Unlock.INSTANCE_METHODS, Unlock.HARVEST, Unlock.IF, Unlock.IS_RIPE);
        Set<String> actions = labels(cursor(work.body, worker), unlocks, program);
        assertTrue(actions.contains("this.farmer.harvest();"));
        assertFalse(actions.contains("farmer.harvest();"));
        assertTrue(actions.stream().anyMatch(s -> s.startsWith("if (this.farmer.isRipe())")));

        ClassDecl counter = new ClassDecl("Counter");
        MethodDecl count = new MethodDecl("count", JType.VOID);
        counter.addMethod(count);
        program.addClass(counter);
        Set<String> unavailable = labels(cursor(count.body, counter), unlocks, program);
        assertFalse(unavailable.stream().anyMatch(s -> s.contains(".harvest()")));
        assertFalse(unavailable.stream().anyMatch(s -> s.contains("farmer.isRipe()")));
    }

    @Test
    public void quickCallsRespectTheVisibleReceiverAndSlotType() {
        Block program = new Block();
        ClassDecl worker = new ClassDecl("Worker");
        worker.addMethod(new MethodDecl("work", JType.VOID));
        worker.addMethod(new MethodDecl("count", JType.INT));
        program.addClass(worker);
        VarDecl variable = new VarDecl(worker.type(), "worker", new NewObject(worker));
        VarDecl count = Ast.declare(JType.INT, "amount");
        program.add(variable);
        program.add(count);
        EditorCursor cursor = cursor(program, null);
        Set<String> unlocks = keys(Unlock.CLASSES, Unlock.INSTANCE_METHODS);

        assertTrue(PaletteProvider.query(cursor, unlocks, program).stream().anyMatch(choice ->
                choice.label.equals("worker.work();") && choice.methodRole == BlockSpec.MethodRole.CALL));
        cursor.slot = count.init;
        assertEquals(Collections.singleton("worker.count()"), PaletteProvider.query(cursor, unlocks, program).stream()
                .filter(choice -> choice.methodRole == BlockSpec.MethodRole.CALL)
                .map(choice -> choice.label).collect(Collectors.toSet()));
        cursor.selectClassStructure(program, worker);
        List<BlockSpec> members = PaletteProvider.query(cursor, unlocks, program);
        assertTrue(members.stream().anyMatch(choice -> choice.label.startsWith("+ void ")
                && choice.methodRole == BlockSpec.MethodRole.CREATE));
        assertFalse(members.stream().anyMatch(choice -> choice.methodRole == BlockSpec.MethodRole.CALL));
    }

    @Test
    public void referenceSlotsAcceptSubclassesButKeepUnrelatedClassesOut() {
        Block program = new Block();
        ClassDecl worker = new ClassDecl("Worker");
        ClassDecl harvester = new ClassDecl("Harvester", "Worker");
        ClassDecl counter = new ClassDecl("Counter");
        program.addClass(worker); program.addClass(harvester); program.addClass(counter);
        VarDecl variable = new VarDecl(worker.type(), "worker", new NewObject(worker));
        program.add(variable);
        EditorCursor cursor = cursor(program, null);
        cursor.slot = variable.init;
        Set<String> choices = labels(cursor, keys(Unlock.CLASSES, Unlock.INHERITANCE), program);
        assertTrue(choices.contains("new Worker()"));
        assertTrue(choices.contains("new Harvester()"));
        assertFalse(choices.contains("new Counter()"));
    }

    @Test
    public void constructorParametersAndSuperCallsHaveTypedContextualChoices() {
        Block program = new Block();
        ClassDecl worker = new ClassDecl("Worker");
        ConstructorDecl base = new ConstructorDecl(new VarDecl(JType.FARMHAND, "farmer", new Literal(JType.FARMHAND, null)));
        worker.setConstructor(base);
        ClassDecl harvester = new ClassDecl("Harvester", "Worker");
        ConstructorDecl constructor = new ConstructorDecl(new VarDecl(JType.FARMHAND, "farmer", new Literal(JType.FARMHAND, null)));
        harvester.setConstructor(constructor);
        program.addClass(worker); program.addClass(harvester);
        EditorCursor cursor = cursor(constructor.body, harvester);
        Set<String> unlocks = keys(Unlock.CLASSES, Unlock.CONSTRUCTORS, Unlock.INSTANCE_METHODS, Unlock.INHERITANCE);
        Set<String> choices = labels(cursor, unlocks, program);
        assertTrue(choices.contains("super(farmer);"));
        assertTrue(choices.contains("+ int row parameter"));
        assertFalse(choices.contains("+ Farmhand farmer parameter"));
        constructor.addParameter(Ast.declare(JType.INT, "row"));
        assertTrue(labels(cursor, unlocks, program).contains("+ int width parameter"));
        constructor.body.add(new SuperCall(base, new VarRef("farmer")));
        cursor.moveTo(constructor.body, 0); cursor.classDecl = harvester;
        assertFalse(labels(cursor, unlocks, program).contains("super(farmer);"));
        assertTrue(ScopeResolver.visibleAt(constructor.body, 0).stream().anyMatch(v -> v.name.equals("row")));
    }

    @Test
    public void classStructureAndMemberCursorAlwaysAgreeOnTheVisibleClass() {
        Block program = new Block();
        ClassDecl worker = new ClassDecl("Worker");
        MethodDecl work = new MethodDecl("work", JType.VOID);
        worker.addMethod(work);
        program.addClass(worker);
        EditorCursor cursor = cursor(program, null);
        cursor.selectClassStructure(program, worker);
        assertSame(worker, cursor.classDecl);
        assertSame(worker, cursor.declaration);
        cursor.moveTo(work.body, 0);
        assertSame(worker, cursor.classDecl);
        assertEquals(null, cursor.declaration);
        cursor.selectClassStructure(program, null);
        assertSame(program, cursor.block);
        assertEquals(null, cursor.classDecl);
    }

    @Test
    public void resetAfterDeletingTheSavedLoopOrClassCannotRestoreAnInvisibleBody() {
        Block program = new Block();
        com.ccs.coffee_n_crops.lang.ast.stmt.ForStmt loop = Ast.forCount("i", 3);
        program.add(loop);
        EditorCursor cursor = cursor(loop.body, null);
        program.remove(0);
        cursor.restoreEditingLocation(program, null, loop.body, 0);
        assertSame(program, cursor.block);
        assertEquals(0, cursor.index);

        ClassDecl worker = new ClassDecl("Worker");
        ConstructorDecl constructor = new ConstructorDecl();
        worker.setConstructor(constructor);
        program.addClass(worker);
        cursor.moveTo(constructor.body, 0);
        program.removeClass(worker);
        cursor.restoreEditingLocation(program, worker, constructor.body, 0);
        assertSame(program, cursor.block);
        assertEquals(null, cursor.classDecl);
        cursor.restoreEditingLocation(program, null, null, 0);
        assertSame(program, cursor.block);
    }
}
