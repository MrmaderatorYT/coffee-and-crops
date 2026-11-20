package com.ccs.coffee_n_crops.editor;

import com.ccs.coffee_n_crops.lang.ast.Node;
import com.ccs.coffee_n_crops.lang.ast.ClassDecl;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.ForStmt;
import com.ccs.coffee_n_crops.lang.runtime.ClassTable;

/**
 * Where the next edit lands. Either an insertion point between statements ({@link #block},
 * {@link #index}) or, when {@link #slot} is set, a value inside a statement being edited.
 */
public final class EditorCursor {
    public Block block;
    public int index;
    /** The expression slot being edited, or null when the cursor is an insertion point. */
    public ExprSlot slot;
    /** Class or interface open in the type selector; null means Program. */
    public ClassDecl classDecl;
    /** Selected class member or class header, outside a statement insertion position. */
    public Node declaration;

    public void moveTo(Block block, int index) {
        this.block = block;
        this.index = index;
        this.slot = null;
        this.declaration = null;
        this.classDecl = ClassTable.classOf(block);
    }

    /** A class header has member commands, never an insertion point in the hidden run body. */
    public void selectClassStructure(Block program, ClassDecl selected) {
        moveTo(program, selected == null ? program.size() : 0);
        classDecl = selected;
        declaration = selected;
    }

    /** A post-run edit may have removed the old loop or class; never restore an orphaned body. */
    public void restoreEditingLocation(Block program, ClassDecl savedClass, Block savedBlock, int savedIndex) {
        if (savedBlock == null) return;
        boolean classExists = savedClass == null || program.classes().contains(savedClass);
        if (classExists && ClassTable.programOf(savedBlock) == program) {
            moveTo(savedBlock, Math.min(savedIndex, savedBlock.size()));
            classDecl = savedClass;
            if (savedClass != null && savedBlock == program) declaration = savedClass;
        } else {
            ClassDecl visible = classDecl != null && program.classes().contains(classDecl) ? classDecl : null;
            selectClassStructure(program, visible);
        }
    }

    public boolean isAt(Block block, int index) {
        return this.block == block && this.index == index;
    }

    /** How many counting {@code for} loops enclose the insertion point; used to pick i, j, k. */
    public int loopDepth() {
        int depth = 0;
        for (Node n = block; n != null; n = n.parent) {
            if (n instanceof ForStmt) depth++;
        }
        return depth;
    }
}
