package com.ccs.coffee_n_crops.editor;

import com.ccs.coffee_n_crops.lang.print.Token;

/** Pine surfaces and warm syntax colors shared by editor rows and controls. */
public final class SyntaxColors {
    public static final int EDITOR_BG = 0xFF102622;
    public static final int PALETTE_BG = 0xFF162E28;
    public static final int ROW_RUNNING = 0xFF344C3C;
    public static final int ROW_TEAMMATE = 0xFF233D50;
    public static final int TEAMMATE = 0xFFA9D5FF;
    public static final int ROW_ERROR = 0xFF603931;
    public static final int GUIDE = 0xFF31483F;
    public static final int CHIP_BG = 0xFF244039;
    public static final int ACCENT = 0xFFB6D5A0;
    public static final int AMBER = 0xFFE4A760;
    public static final int ERROR = 0xFFF9ADA0;
    public static final int TEXT = 0xFFF4E8D2;
    public static final int TEXT_DIM = 0xFFB9C6B2;

    private SyntaxColors() {
    }

    public static int of(Token.Kind kind) {
        switch (kind) {
            case KEYWORD: return 0xFFFFB3A0;
            case TYPE: return 0xFFADD9D0;
            case NUMBER: return 0xFFF2D28A;
            case STRING: return 0xFFECC09B;
            case CONSTANT: return 0xFFD9C9EF;
            case METHOD: return ACCENT;
            case OPERATOR: return AMBER;
            case PUNCT: return TEXT_DIM;
            case IDENT:
            default: return TEXT;
        }
    }
}
