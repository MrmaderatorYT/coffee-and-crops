package com.ccs.coffee_n_crops;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ccs.coffee_n_crops.editor.CodeRow;
import com.ccs.coffee_n_crops.editor.EditorCursor;
import com.ccs.coffee_n_crops.editor.PaletteProvider;
import com.ccs.coffee_n_crops.editor.RowFlattener;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.ClassDecl;
import com.ccs.coffee_n_crops.lang.ast.MethodDecl;
import com.ccs.coffee_n_crops.lang.ast.expr.NewObject;
import com.ccs.coffee_n_crops.lang.ast.expr.NewObjectArray;
import com.ccs.coffee_n_crops.lang.ast.expr.ObjectArrayGet;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForEachStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.print.JavaPrinter;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.progress.Unlock;

import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class EditorInterfacesTest {
    private static final Set<String> UNLOCKS = Set.of(Unlock.CLASSES, Unlock.INTERFACES,
            Unlock.INSTANCE_METHODS, Unlock.OBJECT_ARRAYS, Unlock.FOR);

    @Test
    public void interfaceTabHasSignaturesAndClassOffersTheImplementation() {
        Block program = new Block();
        ClassDecl tool = new ClassDecl("Tool", true);
        ClassDecl harvester = new ClassDecl("Harvester");
        program.addClass(tool);
        program.addClass(harvester);
        EditorCursor cursor = new EditorCursor();
        cursor.selectClassStructure(program, tool);
        Set<String> signatureChoices = labels(cursor, program);
        assertTrue(signatureChoices.contains("+ void work();"));
        assertFalse(signatureChoices.stream().anyMatch(label -> label.startsWith("+ int row")));
        MethodDecl work = new MethodDecl("work", JType.VOID);
        tool.addMethod(work);
        List<CodeRow> rows = RowFlattener.flattenClass(program, tool, cursor, Collections.emptySet());
        assertTrue(rows.stream().anyMatch(row -> JavaPrinter.text(row.tokens).equals("interface Tool {")));
        CodeRow signature = rows.stream().filter(row -> row.node == work).findFirst().get();
        assertTrue(JavaPrinter.text(signature.tokens).equals("void work();"));
        assertNull(signature.foldMember);
        assertFalse(rows.stream().anyMatch(row -> row.kind == CodeRow.Kind.CARET));

        cursor.selectClassStructure(program, harvester);
        assertTrue(labels(cursor, program).contains("+ implements Tool"));
        harvester.interfaceName = tool.name;
        assertTrue(labels(cursor, program).contains("+ @Override work()"));
        harvester.addMethod(new MethodDecl(work.name, work.returnType));
        cursor.selectClassStructure(program, null);
        Set<String> programChoices = labels(cursor, program);
        assertTrue(programChoices.contains("Tool tool = new Harvester();"));
        assertFalse(programChoices.stream().anyMatch(label -> label.contains("new Tool()")));
    }

    @Test
    public void arrayPaletteOffersCompatibleWritesReadsAndIteration() {
        Block program = new Block();
        ClassDecl tool = new ClassDecl("Tool", true);
        tool.addMethod(new MethodDecl("work", JType.VOID));
        program.addClass(tool);
        for (String name : List.of("Harvester", "Waterer")) {
            ClassDecl implementation = new ClassDecl(name);
            implementation.interfaceName = tool.name;
            implementation.addMethod(new MethodDecl("work", JType.VOID));
            program.addClass(implementation);
        }
        program.addClass(new ClassDecl("Marker"));
        VarDecl tools = new VarDecl(JType.objectArray(tool.type()), "tools", new NewObjectArray(tool.type(), Ast.lit(2)));
        program.add(tools);

        EditorCursor cursor = new EditorCursor();
        cursor.moveTo(program, program.size());
        Set<String> outer = labels(cursor, program);
        assertTrue(outer.contains("tools[0] = new Harvester();"));
        assertTrue(outer.contains("tools[0] = new Waterer();"));
        assertFalse(outer.contains("tools[0] = new Marker();"));
        assertTrue(outer.contains("Tool tool = tools[0];"));
        assertTrue(outer.contains("for (int i = 0; i < tools.length; i++) { }"));
        assertTrue(outer.contains("for (Tool tool : tools) { }"));

        ForEachStmt each = new ForEachStmt(tool.type(), "tool", tools.name);
        program.add(each);
        cursor.moveTo(each.body, 0);
        assertTrue(labels(cursor, program).contains("tool.work();"));

        VarDecl element = new VarDecl(tool.type(), "chosen", new ObjectArrayGet(tools.name, tool.type(), Ast.lit(0)));
        program.add(element);
        cursor.slot = element.init;
        assertTrue(labels(cursor, program).contains("tools[0]"));
        assertFalse(labels(cursor, program).contains("new Marker()"));
        cursor.slot = ((ObjectArrayGet) element.init.get()).index;
        assertTrue(labels(cursor, program).contains("tools.length"));
    }

    @Test
    public void automaticNamesStayValidForClassesNamedLikeJavaKeywords() {
        Block program = new Block();
        ClassDecl declaration = new ClassDecl("Class");
        program.addClass(declaration);
        program.add(new VarDecl(declaration.type(), "classObject", new NewObject(declaration)));
        EditorCursor cursor = new EditorCursor();
        cursor.moveTo(program, program.size());
        assertTrue(labels(cursor, program).contains("Class classObject2 = new Class();"));
    }

    private static Set<String> labels(EditorCursor cursor, Block program) {
        return PaletteProvider.query(cursor, UNLOCKS, program).stream()
                .map(choice -> choice.label).collect(Collectors.toSet());
    }
}
