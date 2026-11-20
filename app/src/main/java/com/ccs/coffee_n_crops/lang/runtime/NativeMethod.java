package com.ccs.coffee_n_crops.lang.runtime;

import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.types.Value;

/** A built-in method implemented by the game engine, e.g. {@code farmer.harvest()}. */
public interface NativeMethod {
    Value call(ExecutionContext ctx, Value[] args) throws InterruptedException;
}
