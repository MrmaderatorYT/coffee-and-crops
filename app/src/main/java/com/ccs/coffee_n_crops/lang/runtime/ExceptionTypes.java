package com.ccs.coffee_n_crops.lang.runtime;

import com.ccs.coffee_n_crops.farm.error.EmptyTankException;
import com.ccs.coffee_n_crops.farm.error.FarmException;
import com.ccs.coffee_n_crops.farm.error.OutOfBoundsFarmException;
import com.ccs.coffee_n_crops.lang.types.JType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The farm exception hierarchy exposed by the block editor, without reflection or class loading. */
public final class ExceptionTypes {
    public static final String EMPTY_TANK = "EmptyTankException";
    public static final String OUT_OF_BOUNDS = "OutOfBoundsFarmException";
    public static final String FARM = "FarmException";
    public static final List<String> NAMES = Collections.unmodifiableList(
            Arrays.asList(EMPTY_TANK, OUT_OF_BOUNDS, FARM));

    private ExceptionTypes() {
    }

    public static boolean isKnown(String name) {
        return NAMES.contains(name);
    }

    /** Whether a preceding catch for superType makes a later catch for subType unreachable. */
    public static boolean covers(String superType, String subType) {
        return isKnown(superType) && isKnown(subType)
                && (superType.equals(subType) || superType.equals(FARM));
    }

    public static boolean matches(String name, FarmException failure) {
        if (EMPTY_TANK.equals(name)) return failure instanceof EmptyTankException;
        if (OUT_OF_BOUNDS.equals(name)) return failure instanceof OutOfBoundsFarmException;
        return FARM.equals(name);
    }

    /** Unknown saved type names remain loadable, and are rejected by the pre-run validator. */
    public static JType typeOf(String name) {
        if (EMPTY_TANK.equals(name)) return JType.EMPTY_TANK_EXCEPTION;
        if (OUT_OF_BOUNDS.equals(name)) return JType.OUT_OF_BOUNDS_FARM_EXCEPTION;
        return JType.FARM_EXCEPTION;
    }

    public static boolean assignable(JType actual, JType expected) {
        return expected == JType.FARM_EXCEPTION
                && (actual == JType.EMPTY_TANK_EXCEPTION || actual == JType.OUT_OF_BOUNDS_FARM_EXCEPTION);
    }
}
