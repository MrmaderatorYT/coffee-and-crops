package com.ccs.coffee_n_crops.farm.error;

/** Thrown when a farmer tries to walk off the edge of the farm. */
public class OutOfBoundsFarmException extends FarmException {
    public OutOfBoundsFarmException(int x, int y) {
        super("the farmer tried to leave the farm at (" + x + ", " + y + ")");
    }
}
