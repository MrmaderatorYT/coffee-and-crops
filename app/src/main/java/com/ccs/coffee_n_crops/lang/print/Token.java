package com.ccs.coffee_n_crops.lang.print;

import com.ccs.coffee_n_crops.lang.ast.ExprSlot;

/** One syntax-coloured piece of a line of Java. A token backed by an {@link ExprSlot} is tappable. */
public final class Token {

    public enum Kind { KEYWORD, TYPE, IDENT, NUMBER, STRING, CONSTANT, METHOD, OPERATOR, PUNCT }

    public final String text;
    public final Kind kind;
    /** The slot this token selects when tapped, or null for fixed syntax. */
    public final ExprSlot slot;

    public Token(String text, Kind kind, ExprSlot slot) {
        this.text = text;
        this.kind = kind;
        this.slot = slot;
    }
}
