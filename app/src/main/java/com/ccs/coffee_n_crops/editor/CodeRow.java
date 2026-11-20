package com.ccs.coffee_n_crops.editor;

import com.ccs.coffee_n_crops.lang.ast.Node;
import com.ccs.coffee_n_crops.lang.ast.MethodDecl;
import com.ccs.coffee_n_crops.lang.ast.ConstructorDecl;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.lang.ast.stmt.TryStmt;
import com.ccs.coffee_n_crops.lang.print.Token;

import java.util.List;

/** One line of code in the editor list. The AST is flattened into these for the RecyclerView. */
public final class CodeRow {

    public enum Kind {
        /** A method declaration line (run or a helper) and its closing brace. */
        FRAME_OPEN, FRAME_CLOSE,
        /** A complete one-line statement. */
        STATEMENT,
        /** The opening line and closing brace of a compound statement such as {@code for}. */
        HEADER, FOOTER,
        /** A fixed line inside a compound statement: {@code } else {}, {@code case 1:}, {@code break;}. */
        MID,
        /** The insertion point marker. */
        CARET
    }

    public final Kind kind;
    public final int depth;
    /** The node the debugger highlights on this row, or null if the row is never highlighted. */
    public final Node node;
    public final List<Token> tokens;
    /** Where the cursor goes when this row is tapped. */
    public final Block cursorBlock;
    public final int cursorIndex;
    /** A disclosure control is editor chrome, separate from the Java tokens. */
    public Node foldMember;
    public boolean folded;

    public CodeRow(Kind kind, int depth, Node node, List<Token> tokens, Block cursorBlock, int cursorIndex) {
        this.kind = kind;
        this.depth = depth;
        this.node = node;
        this.tokens = tokens;
        this.cursorBlock = cursorBlock;
        this.cursorIndex = cursorIndex;
    }

    public CodeRow withFold(Node member, boolean folded) {
        this.foldMember = member;
        this.folded = folded;
        return this;
    }

    /** A method-body compiler problem belongs on its declaration line. */
    public boolean showsHighlight(int nodeId) {
        if (node == null || kind == Kind.FOOTER || kind == Kind.FRAME_CLOSE) return false;
        if (node.id == nodeId) return true;
        if (kind == Kind.MID && node instanceof TryStmt.Catch) {
            return ((TryStmt.Catch) node).body.id == nodeId;
        }
        if (kind == Kind.FRAME_OPEN && node instanceof ConstructorDecl) {
            return ((ConstructorDecl) node).body.id == nodeId;
        }
        return kind == Kind.FRAME_OPEN && node instanceof MethodDecl
                && ((MethodDecl) node).body.id == nodeId;
    }
}
