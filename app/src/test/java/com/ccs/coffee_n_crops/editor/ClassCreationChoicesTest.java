package com.ccs.coffee_n_crops.editor;

import static com.ccs.coffee_n_crops.editor.BlockSpec.ClassMemberKind.CONSTRUCTOR;
import static com.ccs.coffee_n_crops.editor.BlockSpec.ClassMemberKind.FIELD;
import static com.ccs.coffee_n_crops.editor.BlockSpec.ClassMemberKind.METHOD;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ccs.coffee_n_crops.editor.BlockSpec.ClassMemberKind;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.ClassDecl;
import com.ccs.coffee_n_crops.lang.ast.ConstructorDecl;
import com.ccs.coffee_n_crops.lang.ast.MethodDecl;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.progress.Unlock;

import org.junit.Test;

import java.util.List;
import java.util.Set;

public class ClassCreationChoicesTest {
    @Test
    public void classCreationFollowsUnlocksAndDoesNotReplaceExistingConstructor() {
        Block program = new Block();
        ClassDecl counter = new ClassDecl("Counter");
        program.addClass(counter);
        Set<String> basic = Set.of(Unlock.CLASSES);
        Set<String> withConstructors = Set.of(Unlock.CLASSES, Unlock.CONSTRUCTORS);

        assertTrue(OopPalette.creationChoices(counter, Set.of(), program).isEmpty());
        assertTrue(OopPalette.creationChoices(null, withConstructors, program).isEmpty());
        assertEquals(List.of("+ int value = 0;"), labels(counter, basic, program, FIELD));
        assertTrue(labels(counter, basic, program, CONSTRUCTOR).isEmpty());
        assertTrue(labels(counter, basic, program, METHOD).isEmpty());
        assertEquals(List.of("+ Counter() { }"), labels(counter, withConstructors, program, CONSTRUCTOR));

        counter.setConstructor(new ConstructorDecl());
        assertTrue(labels(counter, withConstructors, program, CONSTRUCTOR).isEmpty());

        counter.setConstructor(null);
        assertEquals(List.of("+ Counter() { }"), labels(counter, withConstructors, program, CONSTRUCTOR));
    }

    @Test
    public void fieldAlternativesRespectMembersAlreadyDeclared() {
        Block program = new Block();
        ClassDecl worker = new ClassDecl("Worker");
        program.addClass(worker);
        Set<String> unlocks = Set.of(Unlock.CLASSES, Unlock.CONSTANTS, Unlock.INSTANCE_METHODS);

        assertEquals(List.of("+ int row = 0;", "+ static final int STOPS = 3;", "+ Farmhand farmer;"),
                labels(worker, unlocks, program, FIELD));
        worker.addField(Ast.declare(JType.INT, "row"));
        worker.addField(Ast.declare(JType.FARMHAND, "farmer"));

        assertEquals(List.of("+ int width = 0;", "+ static final int STOPS = 3;"),
                labels(worker, unlocks, program, FIELD));
    }

    @Test
    public void interfaceChoicesSeparateSignaturesFromRelationshipCommands() {
        Block program = new Block();
        ClassDecl tool = new ClassDecl("Tool", true);
        ClassDecl base = new ClassDecl("BaseWorker");
        ClassDecl worker = new ClassDecl("Worker", base.name);
        program.addClass(tool);
        program.addClass(base);
        program.addClass(worker);
        Set<String> unlocks = Set.of(Unlock.CLASSES, Unlock.CONSTRUCTORS, Unlock.INTERFACES,
                Unlock.INHERITANCE, Unlock.INSTANCE_METHODS, Unlock.OBJECT_METHODS, Unlock.RETURN);

        assertTrue(labels(tool, unlocks, program, FIELD).isEmpty());
        assertTrue(labels(tool, unlocks, program, CONSTRUCTOR).isEmpty());
        assertEquals(List.of("+ void work();", "+ void work(Farmhand farmer);", "+ int get();"),
                labels(tool, unlocks, program, METHOD));
        assertFalse(OopPalette.creationChoices(worker, unlocks, program).stream()
                .anyMatch(choice -> choice.label.contains("implements")));

        tool.addMethod(new MethodDecl("work", JType.VOID, Ast.declare(JType.FARMHAND, "farmer")));
        tool.addMethod(new MethodDecl("equals", JType.BOOLEAN, Ast.declare(JType.OBJECT, "candidate")));
        base.addMethod(new MethodDecl("work", JType.VOID, Ast.declare(JType.FARMHAND, "assistant")));
        base.addMethod(new MethodDecl("equals", JType.BOOLEAN, Ast.declare(JType.OBJECT, "peer")));
        worker.interfaceName = tool.name;
        List<String> choices = labels(worker, unlocks, program, METHOD);
        assertEquals(List.of("+ @Override work(Farmhand farmer)"), choices.stream()
                .filter(label -> label.startsWith("+ @Override work(")).toList());
        assertEquals(List.of("+ @Override equals(Object candidate)"), choices.stream()
                .filter(label -> label.startsWith("+ @Override equals(")).toList());
    }

    @Test
    public void methodCreationIncludesUnlockedOverridesAndKeepsItsMetadataInTabs() {
        Block program = new Block();
        ClassDecl base = new ClassDecl("Worker");
        MethodDecl tend = new MethodDecl("tend", JType.VOID, Ast.declare(JType.INT, "row"));
        base.addMethod(tend);
        ClassDecl child = new ClassDecl("Harvester", base.name);
        program.addClass(base);
        program.addClass(child);
        Set<String> unlocks = Set.of(Unlock.CLASSES, Unlock.INHERITANCE, Unlock.INSTANCE_METHODS,
                Unlock.OBJECT_METHODS, Unlock.RETURN);

        String inherited = "+ @Override tend(int row)";
        BlockSpec choice = OopPalette.creationChoices(child, unlocks, program).stream()
                .filter(spec -> spec.label.equals(inherited)).findFirst().orElseThrow();
        BlockSpec filed = choice.inTab(BlockSpec.TAB_OBJECTS).pinned();
        assertEquals(METHOD, filed.classMemberKind);
        assertEquals(BlockSpec.MethodRole.CREATE, filed.methodRole);
        assertTrue(labels(child, unlocks, program, METHOD).contains("+ @Override equals(Object other)"));

        child.addMethod(new MethodDecl(tend.name, tend.returnType, Ast.declare(JType.INT, "row")));
        assertFalse(labels(child, unlocks, program, METHOD).contains(inherited));
    }

    private static List<String> labels(ClassDecl declaration, Set<String> unlocks, Block program,
                                       ClassMemberKind kind) {
        return OopPalette.creationChoices(declaration, unlocks, program).stream()
                .filter(choice -> choice.classMemberKind == kind).map(choice -> choice.label).toList();
    }
}
