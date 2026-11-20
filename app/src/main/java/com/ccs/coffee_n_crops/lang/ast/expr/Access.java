package com.ccs.coffee_n_crops.lang.ast.expr;

import com.ccs.coffee_n_crops.farm.Crop;
import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.interp.ScriptError;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads from an array or list variable: {@code row[i]}, {@code row.length}, {@code field[y][x]},
 * {@code list.get(i)}, {@code list.size()}, or {@code crops[i]}.
 */
public final class Access extends Expr {

    public enum Kind {
        /** {@code name[i]} */
        INDEX(JType.INT_ARRAY, 1),
        /** {@code name.length}, also available for user object arrays. */
        LENGTH(JType.INT_ARRAY, 0),
        /** {@code name[y][x]} */
        GRID_INDEX(JType.INT_GRID, 2),
        /** {@code name.length}: the number of rows */
        GRID_ROWS(JType.INT_GRID, 0),
        /** {@code name[0].length}: the number of columns */
        GRID_COLS(JType.INT_GRID, 0),
        /** {@code name.get(i)} */
        LIST_GET(JType.INT_LIST, 1),
        /** {@code name.size()} */
        LIST_SIZE(JType.INT_LIST, 0),
        CROP_INDEX(JType.CROP_ARRAY, JType.CROP, 1),
        CROP_LENGTH(JType.CROP_ARRAY, 0);

        /** The type the variable must have. */
        public final JType target;
        public final int argCount;
        public final JType resultType;

        Kind(JType target, int argCount) {
            this(target, JType.INT, argCount);
        }

        Kind(JType target, JType resultType, int argCount) {
            this.target = target;
            this.resultType = resultType;
            this.argCount = argCount;
        }
    }

    public final Kind kind;
    /** The array or list variable being read. */
    public final String name;
    /** The index expressions, if the kind takes any. */
    public final List<ExprSlot> args;

    public Access(Kind kind, String name, Expr... args) {
        if (args.length != kind.argCount) {
            throw new IllegalArgumentException(kind + " takes " + kind.argCount + " index expression(s)");
        }
        this.kind = kind;
        this.name = name;
        List<ExprSlot> slots = new ArrayList<>();
        for (Expr arg : args) slots.add(new ExprSlot(this, JType.INT, arg));
        this.args = Collections.unmodifiableList(slots);
    }

    @Override
    public Value eval(ExecutionContext ctx) throws InterruptedException {
        Value target = ctx.readVariable(name);
        switch (kind) {
            case INDEX: {
                int[] array = target.asIntArray();
                return Value.ofInt(array[arrayIndex(args.get(0).eval(ctx).asInt(), array.length)]);
            }
            case LENGTH:
                if (target.raw == null) throw new ScriptError("NullPointerException");
                if (target.type.elementType != null) return Value.ofInt(target.asObjectArray().length());
                return Value.ofInt(target.asIntArray().length);
            case GRID_INDEX: {
                int[][] grid = target.asIntGrid();
                int[] row = grid[arrayIndex(args.get(0).eval(ctx).asInt(), grid.length)];
                return Value.ofInt(row[arrayIndex(args.get(1).eval(ctx).asInt(), row.length)]);
            }
            case GRID_ROWS:
                return Value.ofInt(target.asIntGrid().length);
            case GRID_COLS: {
                int[][] grid = target.asIntGrid();
                // Same failure real Java has on an empty grid.
                return Value.ofInt(grid[arrayIndex(0, grid.length)].length);
            }
            case LIST_GET: {
                List<Integer> list = target.asIntList();
                int index = args.get(0).eval(ctx).asInt();
                if (index < 0 || index >= list.size()) {
                    throw new ScriptError("IndexOutOfBoundsException: Index " + index
                            + " out of bounds for length " + list.size());
                }
                return Value.ofInt(list.get(index));
            }
            case LIST_SIZE:
                return Value.ofInt(target.asIntList().size());
            case CROP_INDEX: {
                int index = args.get(0).eval(ctx).asInt();
                Crop[] crops = target.asCropArray();
                return new Value(JType.CROP, crops[arrayIndex(index, crops.length)]);
            }
            case CROP_LENGTH:
                return Value.ofInt(target.asCropArray().length);
            default:
                throw new ScriptError("Unknown access " + kind);
        }
    }

    /** Bounds check with the message real Java prints. */
    private static int arrayIndex(int index, int length) {
        if (index < 0 || index >= length) {
            throw new ScriptError("ArrayIndexOutOfBoundsException: Index " + index
                    + " out of bounds for length " + length);
        }
        return index;
    }
}
