package com.ccs.coffee_n_crops.editor;

/** One button in the bottom palette: what it shows and what tapping it does to the program. */
public final class BlockSpec {

    public interface Action {
        void apply(BlockEditorView editor);
    }

    /** Separates using a method at the cursor from declaring a new method. */
    public enum MethodRole {
        NONE, CALL, CREATE
    }

    /** Which class creation button can present this action. */
    public enum ClassMemberKind {
        FIELD, CONSTRUCTOR, METHOD
    }

    /** Palette tabs, in the order they are shown. */
    public static final String TAB_FARM = "Farm";
    public static final String TAB_LOOPS = "Loops";
    public static final String TAB_LOGIC = "Conditions";
    public static final String TAB_DATA = "Variables";
    public static final String TAB_METHODS = "Methods";
    public static final String TAB_EXCEPTIONS = "Exceptions";
    public static final String TAB_OBJECTS = "Objects";
    public static final String TAB_THREADS = "Threads";
    public static final String TAB_STREAMS = "Streams";
    public static final String DONE = "✓ Done";
    public static final String DELETE = "⌫ Delete";
    public static final String REMOVE_NOT = "remove !";
    public static final String PARAMETER_SUFFIX = " parameter";

    /** Shown on the chip. For code blocks this is the real Java that will be inserted. */
    public final String label;
    /** True for editor commands (delete, done) as opposed to code. */
    public final boolean command;
    public final Action action;
    /** The palette tab this block belongs to, or null for a block shown whichever tab is open. */
    public final String tab;
    /** True for a block that stays on screen beside the tabs instead of scrolling with the others. */
    public final boolean pinned;
    public final MethodRole methodRole;
    /** Null for actions that do not declare a class or interface member. */
    public final ClassMemberKind classMemberKind;

    public BlockSpec(String label, boolean command, Action action) {
        this(label, command, action, null, false, MethodRole.NONE, null);
    }

    private BlockSpec(String label, boolean command, Action action, String tab, boolean pinned,
                      MethodRole methodRole, ClassMemberKind classMemberKind) {
        this.label = label;
        this.command = command;
        this.action = action;
        this.tab = tab;
        this.pinned = pinned;
        this.methodRole = methodRole;
        this.classMemberKind = classMemberKind;
    }

    /** The same block, filed under a palette tab. */
    public BlockSpec inTab(String tab) {
        return new BlockSpec(label, command, action, tab, pinned, methodRole, classMemberKind);
    }

    /** The same block, kept beside the tabs. */
    public BlockSpec pinned() {
        return new BlockSpec(label, command, action, tab, true, methodRole, classMemberKind);
    }

    public BlockSpec asMethodCall() {
        return new BlockSpec(label, command, action, tab, pinned, MethodRole.CALL, classMemberKind);
    }

    public BlockSpec asMethodCreation() {
        return new BlockSpec(label, command, action, tab, pinned, MethodRole.CREATE, classMemberKind);
    }

    public BlockSpec asClassCreation(ClassMemberKind kind) {
        return new BlockSpec(label, command, action, tab, pinned, methodRole, kind);
    }
}
