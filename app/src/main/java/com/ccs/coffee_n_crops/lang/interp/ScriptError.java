package com.ccs.coffee_n_crops.lang.interp;

/** A runtime failure inside the player's program (not a bug in the app). Shown to the player. */
public class ScriptError extends RuntimeException {
    public ScriptError(String message) {
        super(message);
    }
}
