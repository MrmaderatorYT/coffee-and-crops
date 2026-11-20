package com.ccs.coffee_n_crops.editor;

import android.content.Context;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.text.method.ScrollingMovementMethod;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.ast.Node;
import com.ccs.coffee_n_crops.lang.ast.MethodDecl;
import com.ccs.coffee_n_crops.lang.ast.ClassDecl;
import com.ccs.coffee_n_crops.lang.ast.ConstructorDecl;
import com.ccs.coffee_n_crops.lang.ast.AstWalk;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.expr.Literal;
import com.ccs.coffee_n_crops.lang.ast.expr.StreamLambda;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.IfStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.SwitchStmt;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;
import com.ccs.coffee_n_crops.lang.runtime.ClassTable;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.progress.Unlock;
import com.ccs.coffee_n_crops.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;

/**
 * The block code editor: a list of real Java lines on top, a context-sensitive block palette below.
 * It edits the AST directly; the rows are re-derived from the AST after every change.
 */
public final class BlockEditorView extends LinearLayout implements BlockAdapter.Listener {

    public interface OnChangeListener {
        void onProgramChanged();
    }

    private static final int MIN_INT = 0;
    private static final int MAX_INT = 99;
    /** Up to this many code blocks are shown as one row, without tabs. */
    private static final int MAX_UNTABBED = 5;
    private static final int OBJECT_STATE_LINES = 4;

    private final EditorCursor cursor = new EditorCursor();
    private final BlockAdapter adapter = new BlockAdapter(this);
    private final RecyclerView list;
    private final LinearLayout palette;
    private final HorizontalScrollView paletteScroller;
    private final LinearLayout callActions;
    private final LinearLayout paletteActions;
    private final MethodNavigationView methodNavigation;
    private final ClassMemberNavigationView classMemberNavigation;
    private final LinearLayout classBar;
    private final LinearLayout classTabs;
    private final TextView newInterface;
    private final TextView objectState;
    private final Set<Integer> foldedMembers = new HashSet<>();
    private ClassDecl selectedClass;
    private ClassDecl editingClass;
    private Block editingBlock;
    private int editingIndex;
    private final Map<Block, BodyPosition> bodyPositions = new IdentityHashMap<>();
    private ExprSlot paletteSlot;
    private int statementPaletteScroll;
    private String statementPaletteTab;
    /** The row of tabs above the palette; hidden when the choices are few enough for one row. */
    private final LinearLayout tabStrip;
    private final LinearLayout tabBar;
    private final LinearLayout pinnedCommands;
    private List<String> renderedTabs = Collections.emptyList();
    private String renderedActiveTab;
    private List<BlockSpec> methodCreations = Collections.emptyList();
    private List<BlockSpec> classCreations = Collections.emptyList();
    private int paletteRevision;
    /** The chosen tab survives temporary contexts that do not offer it. */
    private String selectedTab = BlockSpec.TAB_FARM;

    private record BodyPosition(Block block, int index, String tab) { }

    private Block program = new Block();
    private Set<String> unlocks = Collections.emptySet();
    private OnChangeListener onChange;
    private boolean locked;

    public BlockEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        setBackgroundColor(SyntaxColors.EDITOR_BG);

        classBar = new LinearLayout(context);
        classBar.setOrientation(HORIZONTAL);
        classBar.setBackgroundColor(SyntaxColors.PALETTE_BG);
        int classSpacing = Ui.dp(context, 6);
        classBar.setPadding(classSpacing, classSpacing, classSpacing, classSpacing);
        classTabs = new LinearLayout(context);
        classTabs.setOrientation(HORIZONTAL);
        HorizontalScrollView classScroller = new HorizontalScrollView(context);
        classScroller.setHorizontalScrollBarEnabled(false);
        classScroller.addView(classTabs);
        classBar.addView(classScroller, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        TextView newClass = navigationChip(context.getString(R.string.editor_add_class), false);
        newClass.setOnClickListener(v -> { if (!locked) showNewTypeDialog(false); });
        LayoutParams newClassParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        newClassParams.setMarginStart(classSpacing);
        classBar.addView(newClass, newClassParams);
        newInterface = navigationChip(context.getString(R.string.editor_add_interface), false);
        newInterface.setOnClickListener(v -> { if (!locked) showNewTypeDialog(true); });
        LayoutParams newInterfaceParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        newInterfaceParams.setMarginStart(classSpacing);
        classBar.addView(newInterface, newInterfaceParams);
        addView(classBar, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        methodNavigation = new MethodNavigationView(context, this::jumpToMethod, this::openMethodCreation);
        addView(methodNavigation, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        classMemberNavigation = new ClassMemberNavigationView(context, this::jumpToClassMember,
                () -> openClassCreation(BlockSpec.ClassMemberKind.FIELD),
                () -> openClassCreation(BlockSpec.ClassMemberKind.CONSTRUCTOR),
                () -> openClassCreation(BlockSpec.ClassMemberKind.METHOD));
        addView(classMemberNavigation, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        list = new RecyclerView(context);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setItemAnimator(null); // highlights change many times a second at x10
        list.setAdapter(adapter);
        int pad = Ui.dp(context, 6);
        list.setPadding(0, pad, 0, pad);
        list.setClipToPadding(false);
        addView(list, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        objectState = new TextView(context);
        objectState.setTypeface(Typeface.MONOSPACE);
        objectState.setTextSize(12f);
        objectState.setMaxLines(OBJECT_STATE_LINES);
        objectState.setVerticalScrollBarEnabled(true);
        objectState.setMovementMethod(new ScrollingMovementMethod());
        objectState.setTextColor(SyntaxColors.ACCENT);
        objectState.setBackgroundColor(SyntaxColors.PALETTE_BG);
        objectState.setPadding(pad, pad, pad, pad);
        objectState.setVisibility(GONE);
        addView(objectState, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        tabBar = new LinearLayout(context);
        tabBar.setOrientation(HORIZONTAL);
        tabBar.setBackgroundColor(SyntaxColors.PALETTE_BG);
        tabBar.setPadding(pad, pad, 0, 0);
        tabStrip = new LinearLayout(context);
        tabStrip.setOrientation(HORIZONTAL);
        HorizontalScrollView tabsScroller = new HorizontalScrollView(context);
        tabsScroller.setHorizontalScrollBarEnabled(false);
        tabsScroller.addView(tabStrip);
        tabBar.addView(tabsScroller, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        pinnedCommands = new LinearLayout(context);
        pinnedCommands.setOrientation(HORIZONTAL);
        tabBar.addView(pinnedCommands);
        addView(tabBar, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout paletteBar = new LinearLayout(context);
        paletteBar.setOrientation(HORIZONTAL);
        paletteBar.setGravity(Gravity.CENTER_VERTICAL);
        paletteBar.setPadding(pad, pad, 0, pad);
        paletteBar.setBackgroundColor(SyntaxColors.PALETTE_BG);

        callActions = new LinearLayout(context);
        paletteBar.addView(callActions);

        palette = new LinearLayout(context);
        palette.setOrientation(HORIZONTAL);
        paletteScroller = new HorizontalScrollView(context);
        paletteScroller.setHorizontalScrollBarEnabled(false);
        paletteScroller.addView(palette);
        paletteBar.addView(paletteScroller, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        paletteActions = new LinearLayout(context);
        paletteBar.addView(paletteActions);
        addView(paletteBar, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        cursor.moveTo(program, 0);
        refresh();
    }

    // ---- wiring ------------------------------------------------------------------------------

    public void setProgram(Block program) {
        this.program = program;
        editingClass = null;
        editingBlock = null;
        selectedClass = null;
        cursor.classDecl = null;
        foldedMembers.clear();
        bodyPositions.clear();
        selectedTab = BlockSpec.TAB_FARM;
        paletteSlot = null;
        statementPaletteTab = null;
        statementPaletteScroll = 0;
        paletteScroller.scrollTo(0, 0);
        cursor.moveTo(program, program.size());
        refresh();
    }

    public Block getProgram() {
        return program;
    }

    public void setUnlocks(Set<String> unlocks) {
        this.unlocks = unlocks;
        refresh();
    }

    public void setOnChangeListener(OnChangeListener listener) {
        this.onChange = listener;
    }

    /** While a program runs the code is read-only, so the AST is never edited under the interpreter. */
    public void setLocked(boolean locked) {
        if (locked && !this.locked) {
            editingClass = selectedClass;
            editingBlock = cursor.block;
            editingIndex = cursor.index;
        }
        boolean wasLocked = this.locked;
        this.locked = locked;
        if (wasLocked && !locked && unlocks.contains(Unlock.CLASSES)) cursor.selectClassStructure(program, selectedClass);
        if (locked) cursor.slot = null;
        palette.setAlpha(locked ? 0.35f : 1f);
        tabBar.setAlpha(locked ? 0.35f : 1f);
        callActions.setAlpha(locked ? 0.35f : 1f);
        paletteActions.setAlpha(locked ? 0.35f : 1f);
        refresh();
    }

    public void highlight(int nodeId, boolean error) {
        if (unlocks.contains(Unlock.CLASSES)) {
            Node[] target = new Node[1];
            AstWalk.walk(program, node -> { if (node.id == nodeId) target[0] = node; });
            if (target[0] != null) {
                selectedClass = ClassTable.classOf(target[0]);
                if (!locked) cursor.selectClassStructure(program, selectedClass);
                for (Node ancestor = target[0]; ancestor != null; ancestor = ancestor.parent) {
                    if (ancestor instanceof ConstructorDecl || ancestor instanceof MethodDecl) foldedMembers.remove(ancestor.id);
                }
                refresh();
            }
        }
        int position = adapter.setHighlight(nodeId, error);
        if (position >= 0) list.scrollToPosition(position);
    }

    public void clearHighlight() {
        adapter.clearHighlight();
    }

    /** Marks both active jobs independently and scrolls only for the selected farmhand. */
    public void highlightTeamNode(boolean teammate, int nodeId, boolean follow) {
        if (!teammate && follow && nodeId >= 0) {
            highlight(nodeId, false);
            return;
        }
        int position = teammate ? adapter.setTeammateHighlight(nodeId) : adapter.setHighlight(nodeId, false);
        if (follow && position >= 0) list.scrollToPosition(position);
    }

    /** Called on the UI thread with a snapshot copied from the interpreter thread. */
    public void setObjectState(String snapshot) {
        boolean show = unlocks.contains(Unlock.CLASSES) && snapshot != null && !snapshot.isEmpty();
        objectState.setVisibility(show ? VISIBLE : GONE);
        if (show) objectState.setText(getContext().getString(R.string.editor_object_state, snapshot));
    }

    /** Reset restores the place the player was editing before debugger navigation began. */
    public void restoreEditingLocation() {
        if (editingBlock != null) {
            cursor.classDecl = selectedClass;
            cursor.restoreEditingLocation(program, editingClass, editingBlock, editingIndex);
            selectedClass = cursor.classDecl;
        }
        setObjectState(null);
        refresh();
    }

    // ---- edits (called by palette actions) ---------------------------------------------------

    public void insert(Stmt stmt) {
        if (locked || cursor.slot != null) return;
        cursor.block.add(cursor.index, stmt);
        List<Block> children = stmt.childBlocks();
        if (children.isEmpty()) {
            cursor.index++;
        } else {
            cursor.moveTo(children.get(0), 0); // keep building inside the new loop or branch
        }
        changed();
    }

    /** Adds a helper alongside run and opens its empty body for editing. */
    public void addMethod(MethodDecl method) {
        if (locked || cursor.slot != null) return;
        rememberBodyPosition();
        program.addMethod(method);
        cursor.moveTo(method.body, 0);
        selectedTab = BlockSpec.TAB_FARM;
        changed();
        scrollToCaret();
    }

    private MethodDecl helperAt(Node node) {
        for (Node ancestor = node; ancestor != null; ancestor = ancestor.parent) {
            if (ancestor instanceof MethodDecl method && method.parent == program) return method;
        }
        return null;
    }

    private Node classMemberAt(Node node) {
        if (selectedClass == null) return null;
        for (Node ancestor = node; ancestor != null; ancestor = ancestor.parent) {
            if (ancestor.parent == selectedClass) return ancestor;
        }
        return null;
    }

    private static Block memberBody(Node member) {
        if (member instanceof ConstructorDecl constructor) return constructor.body;
        if (member instanceof MethodDecl method && !method.isInterfaceSignature()) return method.body;
        return null;
    }

    private void rememberBodyPosition() {
        if (cursor.declaration != null || ClassTable.programOf(cursor.block) != program) return;
        if (ClassTable.classOf(cursor.block) != selectedClass) return;
        Block body = selectedClass == null ? memberBody(helperAt(cursor.block)) : memberBody(classMemberAt(cursor.block));
        if (body == null && selectedClass == null) body = program;
        if (body != null) bodyPositions.put(body, new BodyPosition(cursor.block, cursor.index, selectedTab));
    }

    private void jumpToMethod(MethodDecl method) {
        if (locked || method != null && method.parent != program) return;
        rememberBodyPosition();
        Block body = method == null ? program : method.body;
        moveToBody(body, method == null ? body.size() : 0, BlockSpec.TAB_FARM);
        refresh();
        scrollToCaret();
    }

    private void jumpToClassMember(Node member) {
        if (locked || selectedClass == null || member.parent != selectedClass) return;
        focusClassMember(member);
        refresh();
        scrollToDeclaration(member);
    }

    private void focusClassMember(Node member) {
        rememberBodyPosition();
        Block body = memberBody(member);
        if (body == null) {
            cursor.selectClassStructure(program, selectedClass);
            cursor.declaration = member;
            if (member instanceof VarDecl field && (field.type == JType.INT || field.type == JType.BOOLEAN)) {
                cursor.slot = field.init;
            }
        } else {
            foldedMembers.remove(member.id);
            String tab = member instanceof ConstructorDecl ? BlockSpec.TAB_OBJECTS : BlockSpec.TAB_FARM;
            moveToBody(body, 0, tab);
        }
    }

    private void moveToBody(Block body, int defaultIndex, String defaultTab) {
        BodyPosition saved = bodyPositions.get(body);
        if (saved != null && ClassTable.programOf(saved.block()) == program) {
            cursor.moveTo(saved.block(), Math.min(saved.index(), saved.block().size()));
            selectedTab = saved.tab();
        } else {
            cursor.moveTo(body, defaultIndex);
            selectedTab = defaultTab;
        }
    }

    private void scrollToDeclaration(Node member) {
        int position = adapter.positionOf(member.id);
        if (position < 0) return;
        int caret = adapter.caretPosition();
        if (caret > position + 1) {
            list.scrollToPosition(caret);
        } else {
            ((LinearLayoutManager) list.getLayoutManager()).scrollToPositionWithOffset(position, 0);
        }
    }

    private void scrollToCaret() {
        int position = adapter.caretPosition();
        if (position >= 0) list.scrollToPosition(position);
    }

    /** Adds a declaration in the class structure rather than in a method body. */
    public void addField(VarDecl field) {
        if (locked || selectedClass == null || selectedClass.isInterface) return;
        selectedClass.addField(field);
        memberAdded(field);
    }

    public void addConstructor(ConstructorDecl constructor) {
        if (locked || selectedClass == null || selectedClass.isInterface || selectedClass.constructor() != null) return;
        selectedClass.setConstructor(constructor);
        memberAdded(constructor);
    }

    public void addConstructorParameter(VarDecl parameter) {
        if (locked) return;
        for (Node node = cursor.block; node != null; node = node.parent) {
            if (node instanceof ConstructorDecl) {
                ((ConstructorDecl) node).addParameter(parameter);
                changed();
                return;
            }
        }
    }

    public void addInstanceMethod(MethodDecl method) {
        if (locked || selectedClass == null) return;
        selectedClass.addMethod(method);
        memberAdded(method);
    }

    private void memberAdded(Node member) {
        focusClassMember(member);
        changed();
        scrollToDeclaration(member);
    }

    public void addOverride(MethodDecl inherited) {
        VarDecl[] parameters = new VarDecl[inherited.parameters.size()];
        for (int i = 0; i < parameters.length; i++) {
            VarDecl parameter = inherited.parameters.get(i);
            parameters[i] = new VarDecl(parameter.type, parameter.name, Ast.defaultValue(parameter.type));
        }
        MethodDecl method = new MethodDecl(inherited.name, inherited.returnType, parameters);
        method.override = true;
        addInstanceMethod(method);
    }

    /** Removes the statement before the cursor, like Backspace in a text editor. */
    public void backspace() {
        if (locked) return;
        if (cursor.declaration instanceof VarDecl && cursor.declaration.parent instanceof ClassDecl) {
            ((ClassDecl) cursor.declaration.parent).removeField((VarDecl) cursor.declaration);
            cursor.slot = null;
            cursor.declaration = selectedClass;
            changed();
            return;
        }
        if (cursor.declaration instanceof MethodDecl && selectedClass != null && selectedClass.isInterface) {
            selectedClass.removeMethod((MethodDecl) cursor.declaration);
            cursor.selectClassStructure(program, selectedClass);
            changed();
            return;
        }
        if (cursor.declaration instanceof ClassDecl) {
            ClassDecl cls = (ClassDecl) cursor.declaration;
            if (cls.fields().isEmpty() && cls.constructor() == null && cls.methods().isEmpty()) program.removeClass(cls);
            selectClass(null);
            changed();
            return;
        }
        if (cursor.index > 0) {
            cursor.block.remove(cursor.index - 1);
            cursor.index--;
        } else if (cursor.block.parent != null) {
            stepOutOrRemove();
        } else {
            return;
        }
        changed();
    }

    /** Applies a palette edit to the program (e.g. filling the selected slot) and redraws. */
    public void mutate(Runnable edit) {
        if (locked) return;
        edit.run();
        changed();
    }

    /** Adds to the number in the selected slot. Does nothing unless the slot holds an int literal. */
    public void nudgeSlot(int delta) {
        if (locked || cursor.slot == null || !(cursor.slot.get() instanceof Literal)) return;
        Literal literal = (Literal) cursor.slot.get();
        if (literal.type != JType.INT) return;
        int next = (Integer) literal.value + delta;
        literal.value = Math.max(MIN_INT, Math.min(MAX_INT, next));
        changed();
    }

    public void closeSlot() {
        cursor.slot = null;
        refresh();
    }

    // ---- taps on the code --------------------------------------------------------------------

    @Override
    public void onRowTapped(CodeRow row) {
        if (locked) return;
        if (selectedClass != null) rememberBodyPosition();
        if (row.node instanceof ClassDecl || row.node instanceof VarDecl && row.node.parent instanceof ClassDecl
                || row.node instanceof MethodDecl && ((MethodDecl) row.node).isInterfaceSignature()) {
            selectedClass = ClassTable.classOf(row.node);
            cursor.selectClassStructure(program, selectedClass);
            cursor.declaration = row.node;
            cursor.slot = null;
            refresh();
            return;
        }
        Node owner = row.cursorBlock.parent;
        if (owner instanceof MethodDecl || owner instanceof ConstructorDecl) foldedMembers.remove(owner.id);
        cursor.moveTo(row.cursorBlock, row.cursorIndex);
        cursor.classDecl = selectedClass;
        refresh();
    }

    @Override
    public void onFoldTapped(CodeRow row) {
        if (row.foldMember == null) return;
        boolean collapse = foldedMembers.add(row.foldMember.id);
        if (!collapse) foldedMembers.remove(row.foldMember.id);
        if (collapse && !locked) {
            for (Node ancestor = cursor.block; ancestor != null; ancestor = ancestor.parent) {
                if (ancestor == row.foldMember) {
                    rememberBodyPosition();
                    cursor.slot = null;
                    cursor.declaration = selectedClass;
                    break;
                }
            }
        }
        refresh();
    }

    @Override
    public void onSlotTapped(ExprSlot slot) {
        if (locked) return;
        cursor.slot = slot;
        refresh();
    }

    // ---- internals ---------------------------------------------------------------------------

    /**
     * Backspace at the top of a nested block. An empty optional arm (else, else if, a case, default)
     * is removed; an entirely empty compound statement is removed; otherwise the cursor just
     * steps out to above the statement, leaving its contents alone.
     */
    private void stepOutOrRemove() {
        Block block = cursor.block;
        Node owner = block.parent;
        boolean empty = block.size() == 0;

        if (owner instanceof MethodDecl) {
            // Backspace at a helper's opening brace must not cast the declaration to a statement.
            // Only an empty helper is removed; a nonempty one is left intact.
            if (owner.parent instanceof ClassDecl) {
                if (empty) ((ClassDecl) owner.parent).removeMethod((MethodDecl) owner);
                cursor.moveTo(program, 0);
                cursor.declaration = selectedClass;
            } else {
                if (empty) program.removeMethod((MethodDecl) owner);
                cursor.moveTo(program, program.size());
            }
            return;
        }
        if (owner instanceof ConstructorDecl) {
            if (empty) ((ClassDecl) owner.parent).setConstructor(null);
            cursor.moveTo(program, 0);
            cursor.declaration = selectedClass;
            return;
        }

        if (TryArmEdits.stepOutOrRemove(cursor)) return;

        if (empty && owner instanceof IfStmt) { // the else block
            IfStmt stmt = (IfStmt) owner;
            stmt.removeElse();
            moveToEndOf(last(stmt.childBlocks()));
            return;
        }
        if (empty && owner instanceof IfStmt.Branch) {
            IfStmt stmt = (IfStmt) owner.parent;
            int at = stmt.branches().indexOf(owner);
            if (at > 0) {
                stmt.removeElseIf((IfStmt.Branch) owner);
                moveToEndOf(stmt.branches().get(at - 1).block);
                return;
            }
        }
        if (empty && owner instanceof SwitchStmt) { // the default block
            SwitchStmt stmt = (SwitchStmt) owner;
            stmt.removeDefault();
            if (!stmt.cases().isEmpty()) {
                moveToEndOf(last(stmt.childBlocks()));
                return;
            }
        }
        if (empty && owner instanceof SwitchStmt.Case) {
            SwitchStmt stmt = (SwitchStmt) owner.parent;
            int at = stmt.cases().indexOf(owner);
            stmt.removeCase((SwitchStmt.Case) owner);
            if (!stmt.childBlocks().isEmpty()) {
                moveToEndOf(stmt.childBlocks().get(Math.max(0, at - 1)));
                return;
            }
        }

        // A stream callback block belongs to the lambda, which is inside the call expression.
        if (owner instanceof StreamLambda) owner = owner.parent.parent;
        Stmt compound = owner instanceof Stmt ? (Stmt) owner : (Stmt) owner.parent;
        Block outer = (Block) compound.parent;
        int at = outer.indexOf(compound);
        boolean allEmpty = true;
        for (Block child : compound.childBlocks()) allEmpty &= child.size() == 0;
        if (allEmpty) outer.remove(at);
        cursor.moveTo(outer, at);
    }

    private void moveToEndOf(Block block) {
        cursor.moveTo(block, block.size());
    }

    private static Block last(List<Block> blocks) {
        return blocks.get(blocks.size() - 1);
    }

    /** Moves the insertion point. For palette actions, inside {@link #mutate}, which redraws. */
    public void placeCursor(Block block, int index) {
        cursor.moveTo(block, index);
    }

    private void changed() {
        adapter.clearHighlight();
        refresh();
        if (onChange != null) onChange.onProgramChanged();
    }

    private void refresh() {
        // While editing a slot the insertion caret is hidden; the selected chip is the cursor.
        boolean showCaret = !locked && cursor.slot == null;
        boolean classes = unlocks.contains(Unlock.CLASSES);
        classBar.setVisibility(classes ? VISIBLE : GONE);
        newInterface.setVisibility(unlocks.contains(Unlock.INTERFACES) ? VISIBLE : GONE);
        if (classes) rebuildClassTabs();
        refreshClassPanel(classes ? selectedClass : null);
        adapter.submit(classes ? RowFlattener.flattenClass(program, selectedClass, showCaret ? cursor : null, foldedMembers)
                : RowFlattener.flatten(program, showCaret ? cursor : null), cursor.slot);
        rebuildPalette();
    }

    private void refreshClassPanel(ClassDecl cls) {
        classCreations = cls == null ? Collections.emptyList() : OopPalette.creationChoices(cls, unlocks, program);
        Node location = cursor.slot != null ? cursor.slot.owner : cursor.declaration;
        if (location == null) location = cursor.block;
        boolean showConstructor = cls != null && !cls.isInterface && unlocks.contains(Unlock.CONSTRUCTORS);
        classMemberNavigation.showMembers(cls, locked ? null : classMemberAt(location), locked,
                hasClassCreation(BlockSpec.ClassMemberKind.FIELD), showConstructor,
                hasClassCreation(BlockSpec.ClassMemberKind.CONSTRUCTOR),
                hasClassCreation(BlockSpec.ClassMemberKind.METHOD));
    }

    private boolean hasClassCreation(BlockSpec.ClassMemberKind kind) {
        return classCreations.stream().anyMatch(spec -> spec.classMemberKind == kind);
    }

    private void selectClass(ClassDecl cls) {
        if (!locked) rememberBodyPosition();
        selectedClass = cls;
        if (!locked) {
            cursor.selectClassStructure(program, cls);
        }
        refresh();
        ((LinearLayoutManager) list.getLayoutManager()).scrollToPositionWithOffset(0, 0);
    }

    private void rebuildClassTabs() {
        classTabs.removeAllViews();
        TextView main = navigationChip(getContext().getString(R.string.editor_program), selectedClass == null);
        main.setOnClickListener(v -> selectClass(null));
        classTabs.addView(main, chipParams(getContext()));
        for (ClassDecl cls : program.classes()) {
            TextView tab = navigationChip(cls.name, selectedClass == cls);
            tab.setOnClickListener(v -> selectClass(cls));
            classTabs.addView(tab, chipParams(getContext()));
        }
    }

    private TextView navigationChip(String label, boolean selected) {
        Context context = getContext();
        TextView tab = new TextView(context);
        tab.setText(label);
        tab.setTextSize(13f);
        tab.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tab.setGravity(Gravity.CENTER);
        tab.setSingleLine(true);
        tab.setMinHeight(Ui.dp(context, 44));
        tab.setPadding(Ui.dp(context, 12), Ui.dp(context, 8), Ui.dp(context, 12), Ui.dp(context, 8));
        tab.setTextColor(selected ? SyntaxColors.EDITOR_BG : SyntaxColors.TEXT);
        tab.setBackground(Ui.pressable(Ui.rounded(
                selected ? SyntaxColors.ACCENT : SyntaxColors.CHIP_BG, Ui.dp(context, 8))));
        tab.setFocusable(true);
        tab.setSelected(selected);
        return tab;
    }

    private void showNewTypeDialog(boolean interfaceType) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        Context context = builder.getContext();
        LinearLayout form = new LinearLayout(context);
        form.setOrientation(VERTICAL);
        int pad = Ui.dp(context, 24);
        form.setPadding(pad, Ui.dp(context, 12), pad, Ui.dp(context, 8));
        EditText name = new EditText(context);
        name.setSingleLine(true);
        name.setHint(interfaceType ? R.string.editor_interface_name : R.string.editor_class_name);
        Set<String> names = new HashSet<>();
        Collections.addAll(names, "Program", "Farmhand", "Drone", "Direction", "String", "ArrayList",
                "FarmException", "EmptyTankException", "OutOfBoundsFarmException", "Object");
        for (ClassDecl cls : program.classes()) names.add(cls.name);
        String[] candidates = interfaceType ? new String[]{"Tool", "Job"}
                : unlocks.contains(Unlock.INSTANCE_METHODS) ? new String[]{"Worker", "Harvester", "Waterer"}
                : unlocks.contains(Unlock.CONSTRUCTORS) ? new String[]{"RowPlan", "Counter", "Plan"}
                : new String[]{"Counter", "Plan", "Counter2"};
        String next = null;
        for (String candidate : candidates) if (!names.contains(candidate)) { next = candidate; break; }
        String prefix = interfaceType ? "Tool" : "FarmObject";
        if (next == null) for (int i = 2; next == null; i++) if (!names.contains(prefix + i)) next = prefix + i;
        name.setText(next);
        form.addView(name);
        List<String> bases = new ArrayList<>();
        bases.add(context.getString(R.string.editor_no_base));
        Spinner base = new Spinner(context);
        boolean canExtend = !interfaceType && unlocks.contains(Unlock.INHERITANCE);
        if (canExtend) {
            TextView label = new TextView(context);
            label.setText(R.string.editor_base_class);
            form.addView(label);
            for (ClassDecl cls : program.classes()) if (!cls.isInterface) bases.add(cls.name);
            ArrayAdapter<String> items = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, bases);
            items.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            base.setAdapter(items);
            form.addView(base);
        }
        AlertDialog dialog = builder
                .setTitle(interfaceType ? R.string.editor_new_interface : R.string.editor_new_class).setView(form)
                .setNegativeButton(R.string.editor_cancel, null).setPositiveButton(R.string.editor_create, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = name.getText().toString().trim();
            if (!value.matches("[A-Z][A-Za-z0-9_]*") || names.contains(value)) {
                name.setError(context.getString(interfaceType ? R.string.editor_invalid_interface : R.string.editor_invalid_class));
                return;
            }
            String parent = canExtend && base.getSelectedItemPosition() > 0
                    ? bases.get(base.getSelectedItemPosition()) : null;
            ClassDecl cls = interfaceType ? new ClassDecl(value, true) : new ClassDecl(value, parent);
            program.addClass(cls);
            selectClass(cls);
            selectedTab = BlockSpec.TAB_OBJECTS;
            changed();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void rebuildPalette() {
        int revision = ++paletteRevision;
        int currentScroll = paletteScroller.getScrollX();
        ExprSlot previousSlot = paletteSlot;
        ExprSlot activeSlot = cursor.slot;
        paletteSlot = activeSlot;
        if (previousSlot == null && activeSlot != null) statementPaletteScroll = currentScroll;

        palette.removeAllViews();
        callActions.removeAllViews();
        paletteActions.removeAllViews();
        Node hiddenMember = null;
        for (Node ancestor = cursor.block; ancestor != null; ancestor = ancestor.parent) {
            if (foldedMembers.contains(ancestor.id)) { hiddenMember = ancestor; break; }
        }
        final Node folded = hiddenMember;
        List<BlockSpec> specs = folded == null ? PaletteProvider.query(cursor, unlocks, program)
                : Collections.singletonList(new BlockSpec(getContext().getString(R.string.editor_unfold), true,
                editor -> { foldedMembers.remove(folded.id); cursor.declaration = null; refresh(); }));

        boolean showMethodNavigation = unlocks.contains(Unlock.METHODS) && selectedClass == null;
        List<BlockSpec> calls = new ArrayList<>();
        List<BlockSpec> creations = new ArrayList<>();
        List<BlockSpec> standard = new ArrayList<>();
        for (BlockSpec spec : specs) {
            if (spec.classMemberKind != null) continue;
            if (spec.methodRole == BlockSpec.MethodRole.CALL) calls.add(spec);
            else if (showMethodNavigation && spec.methodRole == BlockSpec.MethodRole.CREATE) creations.add(spec);
            else if (showMethodNavigation && BlockSpec.TAB_METHODS.equals(spec.tab)) standard.add(spec.inTab(null));
            else standard.add(spec);
        }
        methodCreations = creations;
        methodNavigation.setVisibility(showMethodNavigation ? VISIBLE : GONE);
        if (showMethodNavigation) {
            Node location = activeSlot == null ? cursor.block : activeSlot.owner;
            methodNavigation.showMethods(program.methods(), helperAt(location), !creations.isEmpty(), locked);
        }
        if (!calls.isEmpty()) showQuickCalls(calls);

        // Tabs are worth their row only when there is more than one group and too much for one row.
        List<String> tabs = new ArrayList<>();
        int filed = 0;
        for (BlockSpec spec : standard) {
            if (spec.tab == null) continue;
            filed++;
            if (!tabs.contains(spec.tab)) tabs.add(spec.tab);
        }
        boolean tabbed = tabs.size() > 1 && filed > MAX_UNTABBED;
        pinnedCommands.removeAllViews();
        tabBar.setVisibility(tabbed ? VISIBLE : GONE);
        String activeTab = tabbed ? (tabs.contains(selectedTab) ? selectedTab : tabs.get(0)) : null;
        renderTabs(tabbed ? tabs : Collections.emptyList(), activeTab);

        Context context = getContext();
        for (BlockSpec spec : standard) {
            if (tabbed && spec.pinned) {
                pinnedCommands.addView(chip(context, spec), chipParams(context));
            } else if (spec.pinned || BlockSpec.DONE.equals(spec.label)) {
                paletteActions.addView(chip(context, spec), chipParams(context));
            } else if (!tabbed || spec.tab == null || spec.tab.equals(activeTab)) {
                palette.addView(chip(context, spec), chipParams(context));
            }
        }

        int restore;
        if (activeSlot == null) {
            restore = Objects.equals(statementPaletteTab, activeTab)
                    ? (previousSlot == null ? currentScroll : statementPaletteScroll) : 0;
            statementPaletteTab = activeTab;
        } else {
            restore = previousSlot == activeSlot ? currentScroll : 0;
        }
        if (currentScroll != restore) {
            paletteScroller.post(() -> {
                if (paletteRevision == revision) paletteScroller.scrollTo(restore, 0);
            });
        }
    }

    private void renderTabs(List<String> tabs, String activeTab) {
        if (renderedTabs.equals(tabs) && Objects.equals(renderedActiveTab, activeTab)) return;
        renderedTabs = tabs;
        renderedActiveTab = activeTab;
        tabStrip.removeAllViews();
        for (String tab : tabs) tabStrip.addView(tabView(tab, tab.equals(activeTab)), chipParams(getContext()));
    }

    private void showQuickCalls(List<BlockSpec> calls) {
        Context context = getContext();
        TextView shortcut;
        if (calls.size() == 1) {
            BlockSpec call = calls.get(0);
            shortcut = chip(context, call);
            shortcut.setContentDescription(getResources().getString(R.string.editor_method_call_description, call.label));
            shortcut.setEllipsize(TextUtils.TruncateAt.END);
            int width = getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels;
            shortcut.setMaxWidth(Math.min(Ui.dp(context, 170), width / 2));
        } else {
            shortcut = navigationChip(context.getString(R.string.editor_call_method), false);
            shortcut.setOnClickListener(view -> choosePaletteAction(R.string.editor_call_method_title, calls));
        }
        shortcut.setTextColor(SyntaxColors.ACCENT);
        shortcut.setEnabled(!locked);
        callActions.addView(shortcut, chipParams(context));
    }

    private void openMethodCreation() {
        choosePaletteAction(R.string.editor_create_method_title, methodCreations);
    }

    private void openClassCreation(BlockSpec.ClassMemberKind kind) {
        List<BlockSpec> choices = new ArrayList<>();
        for (BlockSpec spec : classCreations) {
            if (spec.classMemberKind == kind) choices.add(spec);
        }
        int title = switch (kind) {
            case FIELD -> R.string.editor_create_field_title;
            case CONSTRUCTOR -> R.string.editor_create_constructor_title;
            case METHOD -> R.string.editor_create_method_title;
        };
        choosePaletteAction(title, choices);
    }

    private void choosePaletteAction(int title, List<BlockSpec> choices) {
        if (locked || choices.isEmpty()) return;
        if (choices.size() == 1) {
            choices.get(0).action.apply(this);
            return;
        }
        Block ownerProgram = program;
        ClassDecl ownerClass = selectedClass;
        CharSequence[] labels = new CharSequence[choices.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = chipLabel(choices.get(i));
        new AlertDialog.Builder(getContext()).setTitle(title)
                .setItems(labels, (dialog, index) -> {
                    if (!locked && program == ownerProgram && selectedClass == ownerClass) {
                        choices.get(index).action.apply(this);
                    }
                })
                .setNegativeButton(R.string.editor_cancel, null)
                .show();
    }

    private TextView tabView(String tab, boolean open) {
        TextView view = navigationChip(tabLabel(tab), open);
        view.setOnClickListener(v -> {
            if (locked || tab.equals(selectedTab)) return;
            selectedTab = tab;
            if (!open) paletteScroller.scrollTo(0, 0);
            rebuildPalette();
        });
        return view;
    }

    private String tabLabel(String tab) {
        int label = switch (tab) {
            case BlockSpec.TAB_FARM -> R.string.editor_tab_farm;
            case BlockSpec.TAB_LOOPS -> R.string.editor_tab_loops;
            case BlockSpec.TAB_LOGIC -> R.string.editor_tab_conditions;
            case BlockSpec.TAB_DATA -> R.string.editor_tab_variables;
            case BlockSpec.TAB_METHODS -> R.string.editor_tab_methods;
            case BlockSpec.TAB_EXCEPTIONS -> R.string.editor_tab_exceptions;
            case BlockSpec.TAB_OBJECTS -> R.string.editor_tab_objects;
            case BlockSpec.TAB_THREADS -> R.string.editor_tab_threads;
            case BlockSpec.TAB_STREAMS -> R.string.editor_tab_streams;
            default -> 0;
        };
        return label == 0 ? tab : getContext().getString(label);
    }

    private TextView chip(Context context, BlockSpec spec) {
        TextView chip = new TextView(context);
        chip.setText(chipLabel(spec));
        boolean wrapPreview = BlockSpec.TAB_STREAMS.equals(spec.tab) || BlockSpec.TAB_THREADS.equals(spec.tab);
        chip.setSingleLine(!wrapPreview);
        if (wrapPreview) {
            int editorWidth = getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels;
            chip.setMaxWidth(editorWidth - Ui.dp(context, 16));
        }
        chip.setTypeface(spec.command ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : Typeface.MONOSPACE);
        chip.setTextSize(14f);
        chip.setTextColor(spec.command ? SyntaxColors.AMBER : SyntaxColors.TEXT);
        chip.setBackground(Ui.pressable(Ui.rounded(SyntaxColors.CHIP_BG, Ui.dp(context, 8))));
        chip.setGravity(Gravity.CENTER);
        chip.setFocusable(true);
        chip.setMinHeight(Ui.dp(context, 44));
        chip.setPadding(Ui.dp(context, 12), Ui.dp(context, 7), Ui.dp(context, 12), Ui.dp(context, 7));
        chip.setOnClickListener(v -> {
            if (!locked) spec.action.apply(this);
        });
        return chip;
    }

    private String chipLabel(BlockSpec spec) {
        if (!spec.command) return spec.label;
        return switch (spec.label) {
            case BlockSpec.DONE -> getContext().getString(R.string.editor_done);
            case BlockSpec.DELETE -> getContext().getString(R.string.editor_delete);
            case BlockSpec.REMOVE_NOT -> getContext().getString(R.string.editor_remove_not);
            default -> spec.label.endsWith(BlockSpec.PARAMETER_SUFFIX)
                    ? getContext().getString(R.string.editor_parameter, spec.label.substring(0,
                            spec.label.length() - BlockSpec.PARAMETER_SUFFIX.length()))
                    : spec.label;
        };
    }

    private static LayoutParams chipParams(Context context) {
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(Ui.dp(context, 6));
        return lp;
    }
}
