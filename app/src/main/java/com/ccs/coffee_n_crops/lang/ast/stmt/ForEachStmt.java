package com.ccs.coffee_n_crops.lang.ast.stmt;

import com.ccs.coffee_n_crops.farm.Direction;
import com.ccs.coffee_n_crops.farm.Crop;
import com.ccs.coffee_n_crops.lang.ast.Ast;
import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.interp.LoopJump;
import com.ccs.coffee_n_crops.lang.interp.ScriptError;
import com.ccs.coffee_n_crops.lang.runtime.ObjectArray;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;

import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;

/**
 * The enhanced for loop. It iterates either the directions,
 * {@code for (Direction d : Direction.values()) { }}, or an array or list variable,
 * {@code for (int cell : row) { }}.
 */
public final class ForEachStmt extends Stmt {
    /** How the built-in direction source is written. */
    public static final String VALUES_TYPE = "Direction";
    public static final String VALUES_METHOD = "values";

    /**
     * The loop variable. Held as a declaration so scoping and name checks treat it like any
     * other local; its initializer is a placeholder that is never printed or evaluated.
     */
    public final VarDecl variable;
    /** The array or list variable iterated, or null for {@code Direction.values()}. */
    public final String source;
    public final Block body = new Block();

    /** {@code for (Direction name : Direction.values())} */
    public ForEachStmt(String name) {
        this(JType.DIRECTION, name, null);
    }

    /** Iterates an array or an {@code ArrayList<Integer>} variable. */
    public ForEachStmt(JType elementType, String name, String source) {
        this.variable = new VarDecl(elementType, name, Ast.defaultValue(elementType));
        this.variable.parent = this;
        this.source = source;
        this.body.parent = this;
    }

    @Override
    public List<Block> childBlocks() {
        return Collections.singletonList(body);
    }

    @Override
    public void exec(ExecutionContext ctx) throws InterruptedException {
        if (source == null) {
            for (Direction direction : Direction.values()) {
                if (!execItem(ctx, new Value(JType.DIRECTION, direction))) return;
            }
            return;
        }
        Value collection = ctx.readVariable(source);
        if (collection.raw == null) throw new ScriptError("NullPointerException");
        if (collection.type == JType.INT_ARRAY) {
            // An enhanced for loop keeps the array reference and reads each element as it goes.
            for (int element : collection.asIntArray()) {
                if (!execItem(ctx, Value.ofInt(element))) return;
            }
            return;
        }
        if (collection.type == JType.CROP_ARRAY) {
            for (Crop crop : collection.asCropArray()) {
                if (!execItem(ctx, new Value(JType.CROP, crop))) return;
            }
            return;
        }
        if (collection.type.elementType != null) {
            ObjectArray array = collection.asObjectArray();
            ctx.recordLessonUse(this);
            for (int i = 0; i < array.length(); i++) {
                if (!execItem(ctx, array.get(i, collection.type.elementType))) return;
            }
            return;
        }
        Iterator<Integer> iterator = collection.asIntList().iterator();
        while (iterator.hasNext()) {
            int element;
            try {
                element = iterator.next();
            } catch (ConcurrentModificationException error) {
                throw new ScriptError("ConcurrentModificationException");
            }
            if (!execItem(ctx, Value.ofInt(element))) return;
        }
    }

    private boolean execItem(ExecutionContext ctx, Value item) throws InterruptedException {
        ctx.checkpoint(this);
        ctx.clock.awaitTicks(VarDecl.THINK_TICKS);
        ctx.pushScope();
        try {
            ctx.scope().declare(variable.name, ctx.convertValue(item, variable.type));
            return LoopJump.runIteration(body, this, ctx);
        } finally {
            ctx.popScope();
        }
    }
}
