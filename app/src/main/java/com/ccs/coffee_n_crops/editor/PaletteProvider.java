package com.ccs.coffee_n_crops.editor;

import com.ccs.coffee_n_crops.farm.Direction;
import com.ccs.coffee_n_crops.farm.Crop;
import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.AstWalk;
import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.ast.Node;
import com.ccs.coffee_n_crops.lang.ast.MethodDecl;
import com.ccs.coffee_n_crops.lang.ast.ConstructorDecl;
import com.ccs.coffee_n_crops.lang.ast.ClassDecl;
import com.ccs.coffee_n_crops.lang.ast.expr.Access;
import com.ccs.coffee_n_crops.lang.ast.expr.Binary;
import com.ccs.coffee_n_crops.lang.ast.expr.Literal;
import com.ccs.coffee_n_crops.lang.ast.expr.MethodCall;
import com.ccs.coffee_n_crops.lang.ast.expr.ListContains;
import com.ccs.coffee_n_crops.lang.ast.expr.NewIntArray;
import com.ccs.coffee_n_crops.lang.ast.expr.NewIntGrid;
import com.ccs.coffee_n_crops.lang.ast.expr.IntArrayLiteral;
import com.ccs.coffee_n_crops.lang.ast.expr.NewList;
import com.ccs.coffee_n_crops.lang.ast.expr.Not;
import com.ccs.coffee_n_crops.lang.ast.expr.StrEquals;
import com.ccs.coffee_n_crops.lang.ast.expr.CropEquals;
import com.ccs.coffee_n_crops.lang.ast.expr.StringConcat;
import com.ccs.coffee_n_crops.lang.ast.expr.StringLength;
import com.ccs.coffee_n_crops.lang.ast.expr.NewStringIntMap;
import com.ccs.coffee_n_crops.lang.ast.expr.StringIntMapCall;
import com.ccs.coffee_n_crops.lang.ast.expr.VarRef;
import com.ccs.coffee_n_crops.lang.ast.expr.UserCall;
import com.ccs.coffee_n_crops.lang.ast.stmt.ArrayAssign;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.BreakStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.CompoundAssign;
import com.ccs.coffee_n_crops.lang.ast.stmt.ContinueStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.DecrementStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.DoWhileStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ExprStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForEachStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.IfStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.AssertStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ListAdd;
import com.ccs.coffee_n_crops.lang.ast.stmt.ListRemove;
import com.ccs.coffee_n_crops.lang.ast.stmt.ListSet;
import com.ccs.coffee_n_crops.lang.ast.stmt.SwitchStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.TryStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.ast.stmt.WhileStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.ReturnStmt;
import com.ccs.coffee_n_crops.lang.check.ScopeResolver;
import com.ccs.coffee_n_crops.lang.print.JavaPrinter;
import com.ccs.coffee_n_crops.lang.runtime.ExceptionTypes;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.progress.Unlock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Decides which blocks the bottom bar offers. The answer depends on where the cursor is
 * (statement position or a typed expression slot), on the variables in scope there, and on what
 * the player has unlocked, so only choices that keep the program valid are ever shown.
 */
public final class PaletteProvider {
    private static final int[] QUICK_INTS = {0, 1, 2, 3, 4, 5, 6, 8, 10};
    private static final String[] LOOP_VARS = {"i", "j", "k"};
    private static final String[] INT_NAMES = {"steps", "count", "width", "total", "rows"};
    private static final String[] BOOLEAN_NAMES = {"ripe", "done", "ready", "found"};
    private static final String[] DIRECTION_NAMES = {"d", "dir", "way"};
    private static final String[] ARRAY_NAMES = {"row", "cells", "line"};
    private static final String[] GRID_NAMES = {"field", "grid", "map"};
    private static final String[] LIST_NAMES = {"list", "todo", "found"};
    private static final String[] ELEMENT_NAMES = {"cell", "item", "value"};
    private static final String[] DOUBLE_NAMES = {"amount", "level", "liters"};
    private static final String[] STRING_NAMES = {"crop", "name", "kind"};
    private static final int DEFAULT_GRID_ROWS = 2;
    private static final int DEFAULT_GRID_COLUMNS = 3;
    private static final String GRID_CREATION = "new int[" + DEFAULT_GRID_ROWS + "][" + DEFAULT_GRID_COLUMNS + "]";
    private static final String ARRAY_LITERAL = "new int[] {0, 1}";
    private static final String[] MAP_NAMES = {"counts", "harvests", "inventory"};
    private static final String[] VOID_METHOD_NAMES = {"harvestRow", "work", "tend"};
    private static final String[] RECURSIVE_METHOD_NAMES = {"walkToEdge", "harvestRow", "work"};
    private static final String[] VALUE_METHOD_NAMES = {"countRipe", "countTiles", "measure"};
    private static final String[] PARAMETER_METHOD_NAMES = {"tendRow", "visitRow", "workRow"};
    private static final String[] CATCH_NAMES = {"e", "error", "failure"};
    private static final double[] QUICK_DOUBLES = {0.0, 0.5, 1.0, 1.5, 2.0, 3.0};
    private static final double DOUBLE_STEP = 0.5;
    private static final int DEFAULT_LOOP_COUNT = 3;
    private static final int DEFAULT_ARRAY_LENGTH = 3;

    private PaletteProvider() {
    }

    public static List<BlockSpec> query(EditorCursor cursor, Set<String> unlocks, Block program) {
        List<BlockSpec> out = new ArrayList<>();
        if (cursor.slot != null) {
            if (TeamPalette.farmhandAt(cursor.slot.owner) != null) {
                TeamPalette.jobSlots(cursor.slot, unlocks, program, out);
            } else if (ParallelPalette.inJob(cursor.slot.owner)) {
                ParallelPalette.jobSlots(cursor.slot, program, out);
            } else {
                slotChoices(cursor.slot, unlocks, program, out);
                ParallelPalette.slots(cursor.slot, unlocks, out);
                TeamPalette.slots(cursor.slot, unlocks, out);
            }
            out.add(new BlockSpec(BlockSpec.DONE, true, BlockEditorView::closeSlot));
        } else if (OopPalette.structural(cursor)) {
            OopPalette.members(cursor, unlocks, program, out);
            ParallelPalette.members(cursor, unlocks, out);
            out.add(new BlockSpec(BlockSpec.DELETE, true, BlockEditorView::backspace).pinned());
        } else {
            if (TeamPalette.farmhandAt(cursor.block) != null) {
                TeamPalette.jobStatements(cursor, unlocks, program, out);
            } else if (!ParallelPalette.inJob(cursor.block)) {
                armChoices(cursor, unlocks, out);
                statementChoices(cursor, unlocks, program, out);
                TeamPalette.statements(cursor, unlocks, program, out);
            }
            ParallelPalette.statements(cursor, unlocks, program, out);
            out.add(new BlockSpec(BlockSpec.DELETE, true, BlockEditorView::backspace).pinned());
        }
        return out;
    }

    // ---- arms of the enclosing if / switch ---------------------------------------------------

    /** When the cursor is inside an if or a switch, offers to grow that statement by another arm. */
    private static void armChoices(EditorCursor cursor, Set<String> unlocks, List<BlockSpec> out) {
        Node owner = cursor.block.parent;

        IfStmt enclosingIf = owner instanceof IfStmt.Branch ? (IfStmt) owner.parent
                : owner instanceof IfStmt ? (IfStmt) owner : null;
        if (enclosingIf != null) {
            if (unlocks.contains(Unlock.ELSE_IF)) {
                out.add(command("+ else if (…) { }", e -> e.mutate(() -> {
                    List<VarDecl> visible = ScopeResolver.visibleAt(cursor.block, cursor.index);
                    IfStmt.Branch added = enclosingIf.addElseIf(OopPalette.contextualize(
                            elseIfCondition(unlocks, visible), cursor.classDecl, visible));
                    e.placeCursor(added.block, 0);
                })));
            }
            if (enclosingIf.elseBlock() == null) {
                out.add(command("+ else { }", e -> e.mutate(() -> e.placeCursor(enclosingIf.addElse(), 0))));
            }
        }

        SwitchStmt enclosingSwitch = owner instanceof SwitchStmt.Case ? (SwitchStmt) owner.parent
                : owner instanceof SwitchStmt ? (SwitchStmt) owner : null;
        if (enclosingSwitch != null) {
            if (unlocks.contains(Unlock.SWITCH_FALL_THROUGH) && owner instanceof SwitchStmt.Case arm) {
                String toggle = arm.fallsThrough() ? BlockSpec.DISABLE_FALL_THROUGH : BlockSpec.ENABLE_FALL_THROUGH;
                out.add(command(toggle, e -> e.mutate(() -> arm.setFallThrough(!arm.fallsThrough()))));
            }
            Object label = enclosingSwitch.nextLabel();
            String text = JavaPrinter.literalText(enclosingSwitch.subject.type, label);
            out.add(command("+ case " + text + ":", e -> e.mutate(() ->
                    e.placeCursor(enclosingSwitch.addCase(label).body, 0))));
            if (enclosingSwitch.defaultBlock() == null) {
                out.add(command("+ default:", e -> e.mutate(() ->
                        e.placeCursor(enclosingSwitch.addDefault(), 0))));
            }
        }

        TryStmt enclosingTry = enclosingTry(cursor.block);
        if (enclosingTry != null && unlocks.contains(Unlock.TRY)) {
            List<VarDecl> visible = variablesOutside(enclosingTry);
            String name = catchName(visible);
            for (String type : catchTypes(unlocks)) {
                boolean covered = false;
                for (TryStmt.Catch arm : enclosingTry.catches()) {
                    if (ExceptionTypes.covers(arm.typeName, type)) covered = true;
                }
                if (covered) continue;
                out.add(command("+ catch (" + type + " " + name + ") { }", e -> e.mutate(() -> {
                    TryStmt.Catch added = enclosingTry.addCatch(type, name);
                    e.placeCursor(added.body, 0);
                })));
            }
            if (unlocks.contains(Unlock.FINALLY) && enclosingTry.finallyBlock() == null) {
                out.add(command("+ finally { }", e -> e.mutate(() ->
                        e.placeCursor(enclosingTry.addFinally(), 0))));
            }
        }
    }

    // ---- statements --------------------------------------------------------------------------

    private static void statementChoices(EditorCursor cursor, Set<String> unlocks, Block program,
                                         List<BlockSpec> out) {
        List<VarDecl> visible = ScopeResolver.visibleAt(cursor.block, cursor.index);
        Set<String> taken = namesInMethod(cursor.block, program);
        List<BlockSpec> farmer = new ArrayList<>();
        List<BlockSpec> loops = new ArrayList<>();
        List<BlockSpec> logic = new ArrayList<>();
        List<BlockSpec> data = new ArrayList<>();

        if (cursor.classDecl == null && unlocks.contains(Unlock.HARVEST)) {
            farmer.add(code("farmer.harvest();", e -> e.insert(Ast.harvest())));
        }
        if (cursor.classDecl == null && unlocks.contains(Unlock.MOVE)) {
            farmer.add(code("farmer.move(Direction.EAST);", e -> e.insert(Ast.move(Direction.EAST))));
        }
        if (cursor.classDecl == null && unlocks.contains(Unlock.WATER)) {
            farmer.add(code("farmer.water();", e -> e.insert(Ast.water())));
        }
        if (cursor.classDecl == null && unlocks.contains(Unlock.FERTILIZE)) {
            farmer.add(code("farmer.fertilize();", e -> e.insert(Ast.fertilize())));
        }
        if (cursor.classDecl == null && unlocks.contains(Unlock.WAIT)) {
            farmer.add(code("farmer.waitOneTick();", e -> e.insert(Ast.waitOneTick())));
        }
        if (cursor.classDecl == null && unlocks.contains(Unlock.TANK)) {
            farmer.add(code("farmer.refill();", e -> e.insert(Ast.refill())));
        }
        if (cursor.classDecl == null && unlocks.contains(Unlock.WALK_TO)) {
            // Default to the loop counters in scope: inner one as x, the one outside it as y.
            List<String> ints = intNames(visible);
            String x = ints.size() > 0 ? ints.get(0) : "0";
            String y = ints.size() > 1 ? ints.get(1) : "0";
            farmer.add(code("farmer.walkTo(" + x + ", " + y + ");",
                    e -> e.insert(Ast.walkTo(intOrZero(ints, 0), intOrZero(ints, 1)))));
        }
        if (unlocks.contains(Unlock.FOR)) {
            String v = freeName(new String[]{loopVar(cursor.loopDepth())}, taken);
            // With an array in scope, the natural loop runs over its whole length; with a grid,
            // the outer loop runs over the rows and a loop nested in it over the columns.
            String array = firstOfType(visible, JType.INT_ARRAY);
            if (array == null && unlocks.contains(Unlock.OBJECT_ARRAYS)) {
                for (VarDecl variable : visible) {
                    if (variable.type.elementType == null) continue;
                    array = variable.name;
                    break;
                }
            }
            String grid = firstOfType(visible, JType.INT_GRID);
            Access.Kind kind = array != null ? Access.Kind.LENGTH
                    : grid == null ? null
                    : cursor.loopDepth() == 0 ? Access.Kind.GRID_ROWS : Access.Kind.GRID_COLS;
            String collection = array != null ? array : grid;
            String bound = kind == null ? String.valueOf(DEFAULT_LOOP_COUNT)
                    : JavaPrinter.accessText(kind, collection);
            loops.add(code("for (int " + v + " = 0; " + v + " < " + bound + "; " + v + "++) { }", e -> {
                ForStmt loop = Ast.forCount(v, DEFAULT_LOOP_COUNT);
                if (kind != null) ((Binary) loop.cond.get()).right.set(new Access(kind, collection));
                e.insert(loop);
            }));
        }
        if (unlocks.contains(Unlock.WHILE)) {
            WhileStmt preview = new WhileStmt(whileCondition(cursor, unlocks, visible));
            loops.add(code(JavaPrinter.text(JavaPrinter.line(preview)) + " }",
                    e -> e.insert(new WhileStmt(whileCondition(cursor, unlocks, visible)))));
        }
        if (unlocks.contains(Unlock.DO_WHILE)) {
            DoWhileStmt preview = new DoWhileStmt(doCondition(cursor, unlocks, visible));
            loops.add(code("do { " + JavaPrinter.text(JavaPrinter.doWhileEnd(preview)),
                    e -> e.insert(new DoWhileStmt(doCondition(cursor, unlocks, visible)))));
        }
        addLoopJumps(cursor, unlocks, loops);
        if (unlocks.contains(Unlock.FOR_EACH)) {
            String name = freeName(DIRECTION_NAMES, taken);
            loops.add(code("for (Direction " + name + " : Direction.values()) { }",
                    e -> e.insert(new ForEachStmt(name))));
        }
        boolean enhancedFor = !StreamPalette.enabled(unlocks) || unlocks.contains(Unlock.FOR_EACH);
        for (VarDecl v : visible) {
            // One for-each block per array or list that can be iterated here.
            if (enhancedFor && v.type == JType.CROP_ARRAY && unlocks.contains(Unlock.CROP_ENUM)) {
                String crop = freeName(STRING_NAMES, taken);
                loops.add(code("for (Crop " + crop + " : " + v.name + ") { }",
                        e -> e.insert(new ForEachStmt(JType.CROP, crop, v.name))));
            }
            if (enhancedFor && (v.type == JType.INT_ARRAY || v.type == JType.INT_LIST)) {
                String name = freeName(ELEMENT_NAMES, taken);
                loops.add(code("for (int " + name + " : " + v.name + ") { }",
                        e -> e.insert(new ForEachStmt(JType.INT, name, v.name))));
            } else if (v.type.elementType != null && unlocks.contains(Unlock.OBJECT_ARRAYS)) {
                JType element = v.type.elementType;
                String name = OopPalette.variableName(element, taken);
                loops.add(code("for (" + element.javaName + " " + name + " : " + v.name + ") { }",
                        e -> e.insert(new ForEachStmt(element, name, v.name))));
            }
        }
        if (unlocks.contains(Unlock.IF)) {
            String cond = JavaPrinter.text(JavaPrinter.line(
                    Ast.ifStmt(OopPalette.contextualize(condition(unlocks, visible, FarmWorld.SCAN_RIPE), cursor.classDecl, visible), false)));
            String head = cond.substring(0, cond.length() - 1); // without the opening brace
            logic.add(code(head + "{ }", e -> e.insert(
                    Ast.ifStmt(OopPalette.contextualize(condition(unlocks, visible, FarmWorld.SCAN_RIPE), cursor.classDecl, visible), false))));
            logic.add(code(head + "{ } else { }", e -> e.insert(
                    Ast.ifStmt(OopPalette.contextualize(condition(unlocks, visible, FarmWorld.SCAN_RIPE), cursor.classDecl, visible), true))));
        }
        if (unlocks.contains(Unlock.SWITCH)) {
            // Switch on the nearest int variable if there is one: usually the loop counter.
            String subject = firstIntName(visible);
            boolean onCrop = unlocks.contains(Unlock.GET_CROP);
            if (onCrop) {
                logic.add(code("switch (farmer.getCrop()) { case \"wheat\": … }",
                        e -> e.insert(Ast.switchOnCrop())));
            }
            if (!onCrop || subject != null) {
                logic.add(code("switch (" + (subject == null ? "0" : subject) + ") { case 0: … }",
                        e -> e.insert(Ast.switchStmt(subject == null ? Ast.lit(0) : new VarRef(subject)))));
            }
        }

        if (unlocks.contains(Unlock.ASSERT)) {
            addAssertChoice(cursor, unlocks, visible, logic, null);
            addAssertChoice(cursor, unlocks, visible, logic, "Expected ripe wheat");
        }

        if (unlocks.contains(Unlock.ARRAY)) {
            String name = freeName(ARRAY_NAMES, taken);
            data.add(code("int[] " + name + " = farmer.scanRow();", e -> e.insert(Ast.scanRow(name))));
        }
        if (unlocks.contains(Unlock.NEW_ARRAY)) {
            String name = freeName(ARRAY_NAMES, taken);
            data.add(code("int[] " + name + " = new int[" + DEFAULT_ARRAY_LENGTH + "];",
                    e -> e.insert(new VarDecl(JType.INT_ARRAY, name, newArray()))));
        }
        if (unlocks.contains(Unlock.ARRAY_LITERAL)) {
            String name = freeName(ARRAY_NAMES, taken);
            data.add(code("int[] " + name + " = " + ARRAY_LITERAL + ";",
                    e -> e.insert(new VarDecl(JType.INT_ARRAY, name, defaultArrayLiteral()))));
        }
        if (unlocks.contains(Unlock.GRID_ALLOCATION)) {
            String name = freeName(GRID_NAMES, taken);
            data.add(code("int[][] " + name + " = " + GRID_CREATION + ";",
                    e -> e.insert(new VarDecl(JType.INT_GRID, name, defaultGrid()))));
        }
        if (unlocks.contains(Unlock.GRID)) {
            String name = freeName(GRID_NAMES, taken);
            data.add(code("int[][] " + name + " = farmer.scanField();", e -> e.insert(Ast.scanField(name))));
        }
        if (unlocks.contains(Unlock.LIST)) {
            String name = freeName(LIST_NAMES, taken);
            data.add(code("ArrayList<Integer> " + name + " = new ArrayList<>();",
                    e -> e.insert(Ast.newList(name))));
        }
        addCollectionEdits(unlocks, visible, data);
        if (unlocks.contains(Unlock.HASH_MAP)) addMapStatements(visible, taken, data);
        ModernCollectionPalette.statements(unlocks, visible, taken, data, loops, logic);
        if (unlocks.contains(Unlock.INT)) {
            String name = freeName(INT_NAMES, taken);
            data.add(code("int " + name + " = 0;", e -> e.insert(Ast.declare(JType.INT, name))));
        }
        if (unlocks.contains(Unlock.DOUBLE)) {
            String name = freeName(DOUBLE_NAMES, taken);
            data.add(code("double " + name + " = 0.0;", e -> e.insert(Ast.declare(JType.DOUBLE, name))));
        }
        if (unlocks.contains(Unlock.CROP_ENUM) && cursor.classDecl == null) {
            String crop = freeName(STRING_NAMES, taken);
            data.add(code("Crop " + crop + " = farmer.scanCrop();",
                    e -> e.insert(new VarDecl(JType.CROP, crop, Ast.scanCrop()))));
            String crops = freeName(new String[]{"crops", "cropRow"}, taken);
            data.add(code("Crop[] " + crops + " = farmer.scanCrops();",
                    e -> e.insert(new VarDecl(JType.CROP_ARRAY, crops, Ast.scanCrops()))));
        }
        if (unlocks.contains(Unlock.STRING)) {
            String name = freeName(STRING_NAMES, taken);
            boolean sensed = unlocks.contains(Unlock.GET_CROP);
            String init = sensed ? "farmer.getCrop()" : "\"" + FarmWorld.CROP_NAMES[0] + "\"";
            data.add(code("String " + name + " = " + init + ";", e -> e.insert(new VarDecl(JType.STRING, name,
                    sensed ? Ast.getCrop() : Ast.lit(FarmWorld.CROP_NAMES[0])))));
        }
        if (unlocks.contains(Unlock.BOOLEAN)) {
            String name = freeName(BOOLEAN_NAMES, taken);
            data.add(code("boolean " + name + " = false;", e -> e.insert(Ast.declare(JType.BOOLEAN, name))));
        }
        if (unlocks.contains(Unlock.ASSIGN)) {
            // One assignment block per variable that is in scope at the cursor.
            for (VarDecl v : visible) {
                if (v.isFinal || v.isStatic || v.parent instanceof ForEachStmt) continue;
                if (v.type == JType.INT) {
                    data.add(code(v.name + " = " + v.name + " + 1;", e -> e.insert(Ast.assign(v,
                            new Binary(Binary.Op.ADD, new VarRef(v.name), Ast.lit(1))))));
                } else if (v.type == JType.BOOLEAN) {
                    data.add(code(v.name + " = true;", e -> e.insert(Ast.assign(v, Ast.lit(true)))));
                } else if (v.type == JType.DOUBLE) {
                    data.add(code(v.name + " = " + v.name + " + " + DOUBLE_STEP + ";",
                            e -> e.insert(Ast.assign(v, new Binary(Binary.Op.ADD, JType.DOUBLE,
                                    new VarRef(v.name), Ast.lit(DOUBLE_STEP))))));
                }
            }
        }
        for (VarDecl variable : visible) {
            if (variable.type != JType.INT || variable.isFinal || variable.isStatic) continue;
            String name = variable.name;
            if (unlocks.contains(Unlock.COMPOUND_ASSIGN)) {
                data.add(code(name + " += 1;", e -> e.insert(new CompoundAssign(name, Ast.lit(1)))));
            }
            if (unlocks.contains(Unlock.SUBTRACT_ASSIGN)) {
                data.add(code(name + " -= 1;", e -> e.insert(
                        new CompoundAssign(name, CompoundAssign.Operation.SUBTRACT, Ast.lit(1)))));
            }
            if (unlocks.contains(Unlock.DECREMENT)) {
                data.add(code(name + "--;", e -> e.insert(new DecrementStmt(name))));
            }
        }

        file(farmer, BlockSpec.TAB_FARM, out);
        file(loops, BlockSpec.TAB_LOOPS, out);
        file(logic, BlockSpec.TAB_LOGIC, out);
        file(data, BlockSpec.TAB_DATA, out);
        StreamPalette.statements(unlocks, visible, taken, out);
        if (unlocks.contains(Unlock.FUNCTION_VALUES)) FunctionPalette.statements(visible, taken, out);
        methodChoices(cursor, unlocks, program, visible, taken, out);
        exceptionChoices(unlocks, visible, out);
        OopPalette.statements(cursor, unlocks, program, out);
    }

    private static void addAssertChoice(EditorCursor cursor, Set<String> unlocks, List<VarDecl> visible,
                                        List<BlockSpec> choices, String detail) {
        AssertStmt preview = assertion(cursor, unlocks, visible, detail);
        choices.add(code(JavaPrinter.text(JavaPrinter.line(preview)),
                e -> e.insert(assertion(cursor, unlocks, visible, detail))));
    }

    private static AssertStmt assertion(EditorCursor cursor, Set<String> unlocks, List<VarDecl> visible,
                                        String detail) {
        Expr condition = unlocks.contains(Unlock.IS_RIPE) ? Ast.isRipe() : Ast.lit(true);
        return new AssertStmt(OopPalette.contextualize(condition, cursor.classDecl, visible),
                detail == null ? null : Ast.lit(detail));
    }

    private static Expr whileCondition(EditorCursor cursor, Set<String> unlocks, List<VarDecl> visible) {
        // The break lesson deliberately puts the boundary check inside the loop.
        if (unlocks.contains(Unlock.BREAK)) return Ast.lit(true);
        String counter = firstIntName(visible);
        if (counter != null && unlocks.contains(Unlock.DECREMENT)) {
            return new Binary(Binary.Op.GE, new VarRef(counter), Ast.lit(0));
        }
        if (counter != null && unlocks.contains(Unlock.COMPOUND_ASSIGN)) {
            return new Binary(Binary.Op.LT, new VarRef(counter), Ast.lit(DEFAULT_LOOP_COUNT));
        }
        return doCondition(cursor, unlocks, visible);
    }

    private static Expr doCondition(EditorCursor cursor, Set<String> unlocks, List<VarDecl> visible) {
        Expr choice = unlocks.contains(Unlock.CAN_MOVE) ? Ast.canMove(Direction.EAST) : Ast.lit(true);
        return OopPalette.contextualize(choice, cursor.classDecl, visible);
    }

    private static void addLoopJumps(EditorCursor cursor, Set<String> unlocks, List<BlockSpec> loops) {
        if (!unlocks.contains(Unlock.BREAK) && !unlocks.contains(Unlock.CONTINUE)) return;
        boolean canBreak = false;
        boolean canContinue = false;
        for (Node node = cursor.block.parent; node != null; node = node.parent) {
            if (node instanceof MethodDecl || node instanceof ConstructorDecl) break;
            if (node instanceof SwitchStmt) canBreak = true;
            if (node instanceof WhileStmt || node instanceof DoWhileStmt
                    || node instanceof ForStmt || node instanceof ForEachStmt) {
                canBreak = true;
                canContinue = true;
                break;
            }
        }
        if (canBreak && unlocks.contains(Unlock.BREAK)) {
            loops.add(code("break;", e -> e.insert(new BreakStmt())));
        }
        if (canContinue && unlocks.contains(Unlock.CONTINUE)) {
            loops.add(code("continue;", e -> e.insert(new ContinueStmt())));
        }
    }

    private static void addMapReads(ExprSlot slot, List<VarDecl> visible, List<BlockSpec> out) {
        for (VarDecl variable : visible) {
            if (variable.type != JType.STRING_INT_MAP) continue;
            String map = variable.name;
            if (slot.type == JType.INT) {
                out.add(code(map + ".getOrDefault(\"wheat\", 0)", e -> e.mutate(() -> slot.set(
                        new StringIntMapCall(map, StringIntMapCall.Op.GET_OR_DEFAULT,
                                Ast.lit(FarmWorld.CROP_NAMES[0]), Ast.lit(0))))));
                out.add(code(map + ".size()", e -> e.mutate(() -> slot.set(
                        new StringIntMapCall(map, StringIntMapCall.Op.SIZE)))));
            } else if (slot.type == JType.BOOLEAN) {
                out.add(code(map + ".containsKey(\"wheat\")", e -> e.mutate(() -> slot.set(
                        new StringIntMapCall(map, StringIntMapCall.Op.CONTAINS_KEY, Ast.lit(FarmWorld.CROP_NAMES[0]))))));
            }
        }
    }

    private static void addMapStatements(List<VarDecl> visible, Set<String> taken, List<BlockSpec> out) {
        String name = freeName(MAP_NAMES, taken);
        out.add(code(JType.STRING_INT_MAP.javaName + " " + name + " = new java.util.HashMap<>();",
                e -> e.insert(new VarDecl(JType.STRING_INT_MAP, name, new NewStringIntMap()))));
        for (VarDecl variable : visible) {
            if (variable.type != JType.STRING_INT_MAP) continue;
            String map = variable.name;
            out.add(code(map + ".put(\"wheat\", 1);", e -> e.insert(new ExprStmt(
                    new StringIntMapCall(map, StringIntMapCall.Op.PUT, Ast.lit(FarmWorld.CROP_NAMES[0]), Ast.lit(1))))));
        }
    }

    private static NewIntArray newArray() {
        return new NewIntArray(Ast.lit(DEFAULT_ARRAY_LENGTH));
    }

    private static IntArrayLiteral defaultArrayLiteral() {
        return new IntArrayLiteral(Ast.lit(0), Ast.lit(1));
    }

    private static NewIntGrid defaultGrid() {
        return new NewIntGrid(Ast.lit(DEFAULT_GRID_ROWS), Ast.lit(DEFAULT_GRID_COLUMNS));
    }

    private static void addCollectionEdits(Set<String> unlocks, List<VarDecl> visible, List<BlockSpec> data) {
        List<String> ints = intNames(visible);
        String index = ints.isEmpty() ? "0" : ints.get(0);
        for (VarDecl variable : visible) {
            String name = variable.name;
            if (variable.type == JType.INT_ARRAY && unlocks.contains(Unlock.ARRAY_ASSIGN)) {
                data.add(code(name + "[" + index + "] = 0;",
                        e -> e.insert(new ArrayAssign(name, intOrZero(ints, 0), Ast.lit(0)))));
            }
            if (variable.type == JType.INT_GRID && unlocks.contains(Unlock.GRID_ASSIGN)) {
                data.add(code(name + "[0][" + index + "] = 0;",
                        e -> e.insert(new ArrayAssign(name, Ast.lit(0), intOrZero(ints, 0), Ast.lit(0)))));
            }
            if (variable.type != JType.INT_LIST) continue;
            data.add(code(name + ".add(" + index + ");",
                    e -> e.insert(new ListAdd(name, intOrZero(ints, 0)))));
            if (unlocks.contains(Unlock.LIST_SET)) {
                data.add(code(name + ".set(" + index + ", 0);",
                        e -> e.insert(new ListSet(name, intOrZero(ints, 0), Ast.lit(0)))));
            }
            if (unlocks.contains(Unlock.LIST_REMOVE)) {
                data.add(code(name + ".remove(" + index + ");",
                        e -> e.insert(new ListRemove(name, intOrZero(ints, 0)))));
            }
        }
    }

    private static void exceptionChoices(Set<String> unlocks, List<VarDecl> visible, List<BlockSpec> out) {
        if (!unlocks.contains(Unlock.TRY)) return;
        List<BlockSpec> exceptions = new ArrayList<>();
        String name = catchName(visible);
        for (String type : catchTypes(unlocks)) {
            exceptions.add(code("try { } catch (" + type + " " + name + ") { }", e -> {
                TryStmt stmt = new TryStmt();
                stmt.addCatch(type, name);
                e.insert(stmt);
            }));
        }
        if (unlocks.contains(Unlock.FINALLY)) {
            exceptions.add(code("try { } finally { }", e -> {
                TryStmt stmt = new TryStmt();
                stmt.addFinally();
                e.insert(stmt);
            }));
        }
        file(exceptions, BlockSpec.TAB_EXCEPTIONS, out);
    }

    private static List<String> catchTypes(Set<String> unlocks) {
        List<String> types = new ArrayList<>();
        if (unlocks.contains(Unlock.CATCH_EMPTY_TANK)) types.add(ExceptionTypes.EMPTY_TANK);
        if (unlocks.contains(Unlock.CATCH_OUT_OF_BOUNDS)) types.add(ExceptionTypes.OUT_OF_BOUNDS);
        if (unlocks.contains(Unlock.CATCH_FARM)) types.add(ExceptionTypes.FARM);
        return types;
    }

    private static String catchName(List<VarDecl> visible) {
        Set<String> taken = new HashSet<>();
        taken.add("farmer");
        for (VarDecl variable : visible) taken.add(variable.name);
        return freeName(CATCH_NAMES, taken);
    }

    /** A catch can see the enclosing locals, but cannot see locals declared inside its try. */
    private static List<VarDecl> variablesOutside(TryStmt stmt) {
        if (!(stmt.parent instanceof Block)) return Collections.emptyList();
        Block parent = (Block) stmt.parent;
        return ScopeResolver.visibleAt(parent, parent.indexOf(stmt));
    }

    /** Arm commands stay available while building a nested loop inside a try or catch body. */
    private static TryStmt enclosingTry(Node node) {
        for (Node current = node; current != null; current = current.parent) {
            if (current instanceof TryStmt) return (TryStmt) current;
        }
        return null;
    }

    /** Helpers are siblings of run, so each body has its own parameter and local-variable scope. */
    private static void methodChoices(EditorCursor cursor, Set<String> unlocks, Block program,
                                      List<VarDecl> visible, Set<String> taken, List<BlockSpec> out) {
        if (!unlocks.contains(Unlock.METHODS) || cursor.classDecl != null) return;
        List<BlockSpec> methods = new ArrayList<>();
        MethodDecl enclosing = enclosingMethod(cursor.block);
        for (MethodDecl method : program.methods()) {
            if (method == enclosing && !unlocks.contains(Unlock.RECURSION)) continue;
            String call = callText(method, visible);
            methods.add(code(call + ";", e -> e.insert(new ExprStmt(defaultCall(method, visible)))).asMethodCall());
            if (method.returnType == JType.INT && unlocks.contains(Unlock.RETURN)) {
                String name = freeName(INT_NAMES, taken);
                methods.add(code("int " + name + " = " + call + ";", e ->
                        e.insert(new VarDecl(JType.INT, name, defaultCall(method, visible)))).asMethodCall());
            }
        }
        Set<String> names = new HashSet<>();
        names.add("run");
        for (MethodDecl method : program.methods()) names.add(method.name);
        String voidName = freeName(unlocks.contains(Unlock.RECURSION)
                ? RECURSIVE_METHOD_NAMES : VOID_METHOD_NAMES, names);
        methods.add(command("+ void " + voidName + "(Farmhand farmer) { }",
                e -> e.addMethod(new MethodDecl(voidName, JType.VOID))).asMethodCreation());
        if (unlocks.contains(Unlock.RETURN)) {
            String name = freeName(VALUE_METHOD_NAMES, names);
            methods.add(command("+ int " + name + "(Farmhand farmer) { }",
                    e -> e.addMethod(new MethodDecl(name, JType.INT))).asMethodCreation());
        }
        if (unlocks.contains(Unlock.PARAMETERS)) {
            String name = freeName(PARAMETER_METHOD_NAMES, names);
            methods.add(command("+ void " + name + "(Farmhand farmer, int y) { }",
                    e -> e.addMethod(new MethodDecl(name, JType.VOID, Ast.declare(JType.INT, "y"))))
                    .asMethodCreation());
        }
        JType result = enclosing == null ? JType.VOID : enclosing.returnType;
        if (result == JType.VOID) {
            methods.add(code("return;", e -> e.insert(new ReturnStmt())));
        } else if (unlocks.contains(Unlock.RETURN)) {
            Expr value = defaultArgument(result, visible);
            String text = JavaPrinter.text(JavaPrinter.line(new ReturnStmt(result, value)));
            methods.add(code(text, e -> e.insert(new ReturnStmt(result, defaultArgument(result, visible)))));
        }
        file(methods, BlockSpec.TAB_METHODS, out);
    }

    private static UserCall defaultCall(MethodDecl method, List<VarDecl> visible) {
        Expr[] args = new Expr[method.parameters.size()];
        for (int i = 0; i < args.length; i++) {
            args[i] = defaultArgument(method.parameters.get(i).type, visible);
        }
        return new UserCall(method, args);
    }

    private static Expr defaultArgument(JType type, List<VarDecl> visible) {
        String variable = firstOfType(visible, type);
        if (variable != null) return new VarRef(variable);
        if (type == JType.INT_ARRAY) return Ast.scanRow("").init.get();
        if (type == JType.INT_GRID) return Ast.scanField("").init.get();
        if (type == JType.INT_LIST) return new NewList();
        return Ast.defaultValue(type);
    }

    private static String callText(MethodDecl method, List<VarDecl> visible) {
        String statement = JavaPrinter.text(JavaPrinter.line(new ExprStmt(defaultCall(method, visible))));
        return statement.substring(0, statement.length() - 1);
    }

    private static MethodDecl enclosingMethod(Node node) {
        for (Node current = node; current != null; current = current.parent) {
            if (current instanceof MethodDecl) return (MethodDecl) current;
        }
        return null;
    }

    /** A name used in another method does not prevent the player using it in this one. */
    static Set<String> namesInMethod(Node context, Block program) {
        Node enclosing = enclosingExecutable(context);
        VarDecl farmhand = TeamPalette.farmhandAt(context);
        Set<String> names = new HashSet<>();
        names.add("farmer");
        AstWalk.walk(program, node -> {
            if (node instanceof VarDecl variable && !(node.parent instanceof ClassDecl)
                    && enclosingExecutable(node) == enclosing) {
                VarDecl actor = TeamPalette.farmhandAt(node);
                if (farmhand == null || actor == null || actor == farmhand) names.add(variable.name);
            }
        });
        return names;
    }

    private static Node enclosingExecutable(Node node) {
        for (Node current = node; current != null; current = current.parent) {
            if (current instanceof MethodDecl || current instanceof ConstructorDecl) return current;
        }
        return null;
    }

    /**
     * The question a new if most likely wants to ask here: the live sensor if the level has one,
     * otherwise the scanned value for the tile the loops in scope are pointing at.
     *
     * @param scanCode the scanner code to compare against when reading from an array
     */
    private static Expr condition(Set<String> unlocks, List<VarDecl> visible, int scanCode) {
        if (unlocks.contains(Unlock.BREAK) && unlocks.contains(Unlock.CAN_MOVE)) {
            return new Not(Ast.canMove(Direction.EAST));
        }
        if (unlocks.contains(Unlock.IS_RIPE)) {
            return unlocks.contains(Unlock.CONTINUE) ? new Not(Ast.isRipe()) : Ast.isRipe();
        }
        if (unlocks.contains(Unlock.GET_CROP)) {
            return Ast.cropIs(scanCode == FarmWorld.SCAN_RIPE ? FarmWorld.CROP_WHEAT : FarmWorld.CROP_SPROUT);
        }
        if (unlocks.contains(Unlock.TANK)) return Ast.waterBelow(FarmWorld.WATER_PER_USE);
        List<String> ints = intNames(visible);
        String list = firstOfType(visible, JType.INT_LIST);
        if (list != null && unlocks.contains(Unlock.LIST_CONTAINS)) {
            return new Not(new ListContains(list, intOrZero(ints, 0)));
        }
        for (VarDecl v : visible) {
            if (v.type == JType.INT_ARRAY) {
                return Ast.eq(new Access(Access.Kind.INDEX, v.name, intOrZero(ints, 0)), scanCode);
            }
            if (v.type == JType.INT_GRID) {
                // Grids are indexed [y][x]: the outer loop counter first, the inner one second.
                return Ast.eq(new Access(Access.Kind.GRID_INDEX, v.name,
                        intOrZero(ints, 1), intOrZero(ints, 0)), scanCode);
            }
        }
        return Ast.lit(true);
    }

    /** A second question to ask after the first: is it dry / is it young wheat? */
    private static Expr elseIfCondition(Set<String> unlocks, List<VarDecl> visible) {
        if (unlocks.contains(Unlock.IS_DRY)) return Ast.isDry();
        return condition(unlocks, visible, FarmWorld.SCAN_YOUNG);
    }

    // ---- expression slots --------------------------------------------------------------------

    private static void slotChoices(ExprSlot slot, Set<String> unlocks, Block program, List<BlockSpec> out) {
        Expr current = slot.get();
        // Level 1 keeps its number slots to plain numbers; once any block that works with
        // variables is unlocked, every variable in scope (loop counters included) is offered.
        boolean variables = !slot.constantOnly && (unlocks.contains(Unlock.INT)
                || unlocks.contains(Unlock.BOOLEAN) || unlocks.contains(Unlock.FOR_EACH)
                || unlocks.contains(Unlock.SWITCH) || unlocks.contains(Unlock.MATH)
                || unlocks.contains(Unlock.ARRAY) || unlocks.contains(Unlock.GRID)
                || unlocks.contains(Unlock.LIST) || unlocks.contains(Unlock.DOUBLE)
                || unlocks.contains(Unlock.STRING) || unlocks.contains(Unlock.STRING_OPERATIONS)
                || unlocks.contains(Unlock.HASH_MAP) || unlocks.contains(Unlock.CROP_ENUM)
                || unlocks.contains(Unlock.FUNCTION_VALUES) || unlocks.contains(Unlock.METHODS)
                || unlocks.contains(Unlock.CLASSES) || unlocks.contains(Unlock.NEW_ARRAY) || unlocks.contains(Unlock.ARRAY_LITERAL)
                || unlocks.contains(Unlock.GRID_ALLOCATION) || unlocks.contains(Unlock.GRID_ASSIGN)
                || unlocks.contains(Unlock.ARRAY_ASSIGN) || unlocks.contains(Unlock.LIST_CONTAINS)
                || unlocks.contains(Unlock.LIST_SET) || unlocks.contains(Unlock.LIST_REMOVE)
                || unlocks.contains(Unlock.COMPOUND_ASSIGN) || unlocks.contains(Unlock.DECREMENT)
                || StreamPalette.enabled(unlocks));
        List<VarDecl> visible = variables ? ScopeResolver.visibleAt(slot) : Collections.<VarDecl>emptyList();
        boolean globalFarmer = com.ccs.coffee_n_crops.lang.runtime.ClassTable.classOf(slot.owner) == null;

        // If the slot holds an operator expression, first offer the operators it can be swapped for.
        if (current instanceof Binary) {
            Binary binary = (Binary) current;
            for (Binary.Op op : Binary.Op.values()) {
                if (op.family == binary.op().family && op != binary.op()) {
                    out.add(command(op.symbol, e -> e.mutate(() -> binary.setOp(op))));
                }
            }
        }

        switch (slot.type.name()) {
            case "INT":
                if (current instanceof Literal) {
                    out.add(command("− 1", e -> e.nudgeSlot(-1)));
                    out.add(command("+ 1", e -> e.nudgeSlot(1)));
                }
                for (int n : QUICK_INTS) {
                    out.add(code(String.valueOf(n), e -> e.mutate(() -> slot.set(Ast.lit(n)))));
                }
                if (slot.constantOnly) break; // a case label must be a constant: no variables, no operators
                variableChoices(slot, visible, out);
                collectionReads(slot, visible, out);
                if (unlocks.contains(Unlock.STRING_OPERATIONS)) {
                    String name = firstOfType(visible, JType.STRING);
                    String preview = name == null ? "\"wheat\"" : name;
                    out.add(code(preview + ".length()", e -> e.mutate(() -> slot.set(
                            new StringLength(name == null ? Ast.lit(FarmWorld.CROP_NAMES[0]) : new VarRef(name))))));
                }
                if (unlocks.contains(Unlock.MATH)) {
                    out.add(command("▢ + 1", e -> e.mutate(() ->
                            slot.set(new Binary(Binary.Op.ADD, slot.get(), Ast.lit(1))))));
                    out.add(command("▢ % 2", e -> e.mutate(() ->
                            slot.set(new Binary(Binary.Op.MOD, slot.get(), Ast.lit(2))))));
                }
                break;

            case "LONG":
                for (long n : QUICK_INTS) {
                    out.add(code(n + "L", e -> e.mutate(() -> slot.set(new Literal(JType.LONG, n)))));
                }
                if (!slot.constantOnly) variableChoices(slot, visible, out);
                break;

            case "BOOLEAN":
                out.add(code("true", e -> e.mutate(() -> slot.set(Ast.lit(true)))));
                out.add(code("false", e -> e.mutate(() -> slot.set(Ast.lit(false)))));
                if (globalFarmer && unlocks.contains(Unlock.IS_RIPE)) {
                    out.add(code("farmer.isRipe()", e -> e.mutate(() -> slot.set(Ast.isRipe()))));
                }
                if (globalFarmer && unlocks.contains(Unlock.IS_DRY)) {
                    out.add(code("farmer.isDry()", e -> e.mutate(() -> slot.set(Ast.isDry()))));
                }
                if (globalFarmer && unlocks.contains(Unlock.CAN_MOVE)) {
                    out.add(code("farmer.canMove(Direction.EAST)",
                            e -> e.mutate(() -> slot.set(Ast.canMove(Direction.EAST)))));
                }
                variableChoices(slot, visible, out);
                if (unlocks.contains(Unlock.CROP_ENUM)) {
                    String name = firstOfType(visible, JType.CROP);
                    if (name != null || globalFarmer) {
                        String preview = name == null ? "farmer.scanCrop()" : name;
                        out.add(code(preview + " == Crop.WHEAT", e -> e.mutate(() -> slot.set(
                                new CropEquals(name == null ? Ast.scanCrop() : new VarRef(name), Ast.lit(Crop.WHEAT))))));
                    }
                }
                if (unlocks.contains(Unlock.LIST_CONTAINS)) addListContainsChoices(slot, visible, out);
                if (unlocks.contains(Unlock.LOGIC)) {
                    out.add(command("▢ == ▢", e -> e.mutate(() ->
                            slot.set(new Binary(Binary.Op.EQ, firstInt(visible), Ast.lit(0))))));
                    out.add(command("▢ < ▢", e -> e.mutate(() ->
                            slot.set(new Binary(Binary.Op.LT, firstInt(visible), Ast.lit(0))))));
                    if (unlocks.contains(Unlock.DOUBLE)) {
                        // A comparison on doubles: its operands accept 0.5 as well as whole numbers.
                        out.add(command("▢ < " + DOUBLE_STEP, e -> e.mutate(() -> slot.set(new Binary(
                                Binary.Op.LT, JType.DOUBLE, firstDouble(visible, unlocks), Ast.lit(DOUBLE_STEP))))));
                    }
                    if (unlocks.contains(Unlock.STRING)) {
                        out.add(command("▢.equals(▢)", e -> e.mutate(() -> slot.set(new StrEquals(
                                firstString(visible, unlocks), Ast.lit(FarmWorld.CROP_NAMES[0]))))));
                    }
                    if (current instanceof Not) {
                        out.add(command(BlockSpec.REMOVE_NOT, e -> e.mutate(() ->
                                slot.set(((Not) slot.get()).operand.get()))));
                    } else {
                        out.add(command("! ▢", e -> e.mutate(() -> slot.set(new Not(slot.get())))));
                    }
                    out.add(command("▢ && ▢", e -> e.mutate(() ->
                            slot.set(new Binary(Binary.Op.AND, slot.get(), Ast.lit(true))))));
                }
                break;

            case "DIRECTION":
                for (Direction d : Direction.values()) {
                    out.add(code("Direction." + d.name(), e -> e.mutate(() ->
                            slot.set(new Literal(JType.DIRECTION, d)))));
                }
                variableChoices(slot, visible, out);
                break;

            case "DOUBLE":
                if (current instanceof Literal) {
                    Literal literal = (Literal) current;
                    out.add(command("− " + DOUBLE_STEP, e -> e.mutate(() ->
                            literal.value = Math.max(0.0, (Double) literal.value - DOUBLE_STEP))));
                    out.add(command("+ " + DOUBLE_STEP, e -> e.mutate(() ->
                            literal.value = (Double) literal.value + DOUBLE_STEP)));
                }
                for (double d : QUICK_DOUBLES) {
                    out.add(code(String.valueOf(d), e -> e.mutate(() -> slot.set(Ast.lit(d)))));
                }
                if (globalFarmer && unlocks.contains(Unlock.TANK)) {
                    out.add(code("farmer.getWater()", e -> e.mutate(() -> slot.set(Ast.getWater()))));
                }
                for (VarDecl v : visible) {
                    // An int variable fits too: Java widens int to double.
                    if (v.type == JType.DOUBLE || v.type == JType.INT) {
                        out.add(code(v.name, e -> e.mutate(() -> slot.set(new VarRef(v.name)))));
                    }
                }
                if (unlocks.contains(Unlock.MATH)) {
                    out.add(command("▢ + " + DOUBLE_STEP, e -> e.mutate(() -> slot.set(new Binary(
                            Binary.Op.ADD, JType.DOUBLE, slot.get(), Ast.lit(DOUBLE_STEP))))));
                }
                break;

            case "STRING":
                for (String name : FarmWorld.CROP_NAMES) {
                    out.add(code("\"" + name + "\"", e -> e.mutate(() -> slot.set(Ast.lit(name)))));
                }
                if (unlocks.contains(Unlock.TEXT_INPUT)) {
                    out.add(command(BlockSpec.EDIT_TEXT, BlockEditorView::editStringSlot));
                }
                if (slot.constantOnly) break; // a case label must be a constant
                if (unlocks.contains(Unlock.STRING_OPERATIONS)) {
                    out.add(command("▢ + \"\"", e -> e.mutate(() -> slot.set(
                            new StringConcat(slot.get(), JType.STRING, Ast.lit(""))))));
                    out.add(command("▢ + 1", e -> e.mutate(() -> slot.set(
                            new StringConcat(slot.get(), JType.INT, Ast.lit(1))))));
                }
                if (globalFarmer && unlocks.contains(Unlock.GET_CROP)) {
                    out.add(code("farmer.getCrop()", e -> e.mutate(() -> slot.set(Ast.getCrop()))));
                }
                variableChoices(slot, visible, out);
                break;

            case "INT_ARRAY":
                if (globalFarmer && unlocks.contains(Unlock.ARRAY)) out.add(code("farmer.scanRow()", e -> e.mutate(() ->
                        slot.set(Ast.scanRow("").init.get()))));
                if (unlocks.contains(Unlock.NEW_ARRAY)) {
                    out.add(code("new int[" + DEFAULT_ARRAY_LENGTH + "]", e -> e.mutate(() -> slot.set(newArray()))));
                }
                if (unlocks.contains(Unlock.ARRAY_LITERAL)) {
                    if (current instanceof IntArrayLiteral array) {
                        if (array.canAppend()) out.add(command(BlockSpec.ADD_ARRAY_ELEMENT,
                                e -> e.mutate(() -> array.append(Ast.lit(0)))));
                        if (!array.elements().isEmpty()) out.add(command(BlockSpec.REMOVE_ARRAY_ELEMENT,
                                e -> e.mutate(array::removeLast)));
                    } else {
                        out.add(code(ARRAY_LITERAL, e -> e.mutate(() -> slot.set(defaultArrayLiteral()))));
                    }
                }
                variableChoices(slot, visible, out);
                break;

            case "INT_GRID":
                if (globalFarmer && unlocks.contains(Unlock.GRID)) out.add(code("farmer.scanField()", e -> e.mutate(() ->
                        slot.set(Ast.scanField("").init.get()))));
                if (unlocks.contains(Unlock.GRID_ALLOCATION)) {
                    out.add(code(GRID_CREATION, e -> e.mutate(() -> slot.set(defaultGrid()))));
                }
                variableChoices(slot, visible, out);
                break;

            case "CROP":
                if (unlocks.contains(Unlock.CROP_ENUM)) {
                    for (Crop crop : Crop.values()) {
                        out.add(code(JavaPrinter.literalText(JType.CROP, crop), e -> e.mutate(() -> slot.set(Ast.lit(crop)))));
                    }
                    if (globalFarmer) out.add(code("farmer.scanCrop()", e -> e.mutate(() -> slot.set(Ast.scanCrop()))));
                    collectionReads(slot, visible, out);
                    variableChoices(slot, visible, out);
                }
                break;

            case "CROP_ARRAY":
                if (globalFarmer && unlocks.contains(Unlock.CROP_ENUM)) {
                    out.add(code("farmer.scanCrops()", e -> e.mutate(() -> slot.set(Ast.scanCrops()))));
                }
                variableChoices(slot, visible, out);
                break;

            case "STRING_INT_MAP":
                if (unlocks.contains(Unlock.HASH_MAP)) {
                    out.add(code("new java.util.HashMap<>()", e -> e.mutate(() -> slot.set(new NewStringIntMap()))));
                }
                variableChoices(slot, visible, out);
                break;

            case "INT_LIST":
                out.add(code("new ArrayList<>()", e -> e.mutate(() -> slot.set(new NewList()))));
                variableChoices(slot, visible, out);
                break;

            default:
                break;
        }
        if (!slot.constantOnly && unlocks.contains(Unlock.HASH_MAP)) addMapReads(slot, visible, out);
        if (!slot.constantOnly && unlocks.contains(Unlock.FUNCTION_VALUES)) {
            FunctionPalette.slots(slot, visible, namesInMethod(slot.owner, program), out);
        }
        OopPalette.slots(slot, unlocks, program, out);
        ModernCollectionPalette.slots(slot, unlocks, visible, out);
        if (StreamPalette.enabled(unlocks) && (slot.type == JType.BOOLEAN || slot.type == JType.LONG)) {
            StreamPalette.slots(slot, unlocks, visible, namesInMethod(slot.owner, program), out);
        }
        if (!slot.constantOnly && unlocks.contains(Unlock.METHODS)
                && com.ccs.coffee_n_crops.lang.runtime.ClassTable.classOf(slot.owner) == null) {
            MethodDecl enclosing = enclosingMethod(slot.owner);
            for (MethodDecl method : program.methods()) {
                if (method == enclosing && !unlocks.contains(Unlock.RECURSION)) continue;
                if (method.returnType == JType.VOID) continue;
                if (method.returnType == slot.type || slot.type == JType.DOUBLE && method.returnType == JType.INT) {
                    out.add(code(callText(method, visible), e -> e.mutate(() ->
                            slot.set(defaultCall(method, visible)))).asMethodCall());
                }
            }
        }
    }

    private static void addListContainsChoices(ExprSlot slot, List<VarDecl> visible, List<BlockSpec> out) {
        List<String> ints = intNames(visible);
        String argument = ints.isEmpty() ? "0" : ints.get(0);
        for (VarDecl variable : visible) {
            if (variable.type != JType.INT_LIST) continue;
            String list = variable.name;
            out.add(code(list + ".contains(" + argument + ")",
                    e -> e.mutate(() -> slot.set(new ListContains(list, intOrZero(ints, 0))))));
        }
    }

    /** For an int slot: every way to read an int out of the arrays and lists in scope. */
    private static void collectionReads(ExprSlot slot, List<VarDecl> visible, List<BlockSpec> out) {
        List<String> ints = intNames(visible);
        for (VarDecl v : visible) {
            for (Access.Kind kind : Access.Kind.values()) {
                if (kind.target != v.type || kind.resultType != slot.type) continue;
                out.add(code(JavaPrinter.accessText(kind, v.name), e -> e.mutate(() -> {
                    // Index with the loop counters in scope; a grid takes [outer][inner].
                    Expr[] args = new Expr[kind.argCount];
                    for (int i = 0; i < args.length; i++) {
                        args[i] = intOrZero(ints, args.length - 1 - i);
                    }
                    slot.set(new Access(kind, v.name, args));
                })));
            }
        }
    }

    /** Left side for a new double comparison: a double variable, the tank level, or 0.0. */
    private static Expr firstDouble(List<VarDecl> visible, Set<String> unlocks) {
        String name = firstOfType(visible, JType.DOUBLE);
        if (name != null) return new VarRef(name);
        return unlocks.contains(Unlock.TANK) ? Ast.getWater() : Ast.lit(0.0);
    }

    /** Left side for a new equals(): a String variable, what the farmer sees, or a constant. */
    private static Expr firstString(List<VarDecl> visible, Set<String> unlocks) {
        String name = firstOfType(visible, JType.STRING);
        if (name != null) return new VarRef(name);
        return unlocks.contains(Unlock.GET_CROP) ? Ast.getCrop() : Ast.lit(FarmWorld.CROP_NAMES[0]);
    }

    /** Names of the int variables in scope, nearest first. */
    static List<String> intNames(List<VarDecl> visible) {
        List<String> names = new ArrayList<>();
        for (VarDecl v : visible) {
            if (v.type == JType.INT) names.add(v.name);
        }
        return names;
    }

    /** A reference to the n-th nearest int variable, or the literal 0 if there are not that many. */
    private static Expr intOrZero(List<String> ints, int n) {
        return n < ints.size() ? new VarRef(ints.get(n)) : Ast.lit(0);
    }

    /** The nearest variable of the type in scope, or null. */
    private static String firstOfType(List<VarDecl> visible, JType type) {
        for (VarDecl v : visible) {
            if (v.type == type) return v.name;
        }
        return null;
    }

    /** Offers every in-scope variable whose type fits the slot. */
    private static void variableChoices(ExprSlot slot, List<VarDecl> visible, List<BlockSpec> out) {
        for (VarDecl v : visible) {
            if (v.type == slot.type) {
                out.add(code(v.name, e -> e.mutate(() -> slot.set(new VarRef(v.name)))));
            }
        }
    }

    /** Left operand for a new comparison: an int variable if one is in scope, otherwise 0. */
    private static Expr firstInt(List<VarDecl> visible) {
        String name = firstIntName(visible);
        return name == null ? Ast.lit(0) : new VarRef(name);
    }

    /** The nearest int variable in scope, or null. */
    private static String firstIntName(List<VarDecl> visible) {
        for (VarDecl v : visible) {
            if (v.type == JType.INT) return v.name;
        }
        return null;
    }

    /** Appends the blocks to the palette, filed under one tab. */
    private static void file(List<BlockSpec> blocks, String tab, List<BlockSpec> out) {
        for (BlockSpec spec : blocks) out.add(spec.inTab(tab));
    }

    // ---- naming ------------------------------------------------------------------------------

    /** A nested loop needs a name its enclosing loops are not already using. */
    private static String loopVar(int depth) {
        return depth < LOOP_VARS.length ? LOOP_VARS[depth] : "n" + depth;
    }

    /** Java does not allow two locals with one name in a method, so each new variable gets a fresh one. */
    static String freeName(String[] pool, Set<String> taken) {
        for (String name : pool) {
            if (!taken.contains(name)) return name;
        }
        for (int n = 2; ; n++) {
            String name = pool[0] + n;
            if (!taken.contains(name)) return name;
        }
    }

    private static BlockSpec code(String label, BlockSpec.Action action) {
        return new BlockSpec(label, false, action);
    }

    private static BlockSpec command(String label, BlockSpec.Action action) {
        return new BlockSpec(label, true, action);
    }
}
