package com.ccs.coffee_n_crops.lang.interp;

import com.ccs.coffee_n_crops.lang.types.Value;

/** Internal control flow for Java's return; never reported as a runtime error to the player. */
public final class ReturnSignal extends RuntimeException {
    public final Value value;

    public ReturnSignal(Value value) {
        super(null, null, false, false);
        this.value = value;
    }
}
