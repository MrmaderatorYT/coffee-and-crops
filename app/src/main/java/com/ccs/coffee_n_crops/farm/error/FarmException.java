package com.ccs.coffee_n_crops.farm.error;

import com.ccs.coffee_n_crops.lang.interp.ScriptError;

/** Base of the runtime errors a farmer can cause on the farm and catch in a player's try block. */
public class FarmException extends ScriptError {
    public FarmException(String message) {
        super(message);
    }
}
