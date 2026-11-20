package com.ccs.coffee_n_crops.farm.error;

/** Thrown when a farmer tries to water with less water in its tank than one watering needs. */
public class EmptyTankException extends FarmException {
    public EmptyTankException(double left, double needed) {
        super("the tank holds " + left + " L but watering needs " + needed + " L");
    }
}
