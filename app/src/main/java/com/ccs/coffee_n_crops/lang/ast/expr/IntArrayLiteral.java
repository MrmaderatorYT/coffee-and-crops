package com.ccs.coffee_n_crops.lang.ast.expr;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** An inline array initializer. Each use constructs a fresh int array. */
public final class IntArrayLiteral extends Expr {
    private static final int MAX_ELEMENTS = 128;
    private final List<ExprSlot> elements = new ArrayList<>();

    public IntArrayLiteral(Expr... items) {
        if (items.length > MAX_ELEMENTS) throw new IllegalArgumentException("Too many array elements");
        for (Expr item : items) append(item);
    }

    public List<ExprSlot> elements() {
        return Collections.unmodifiableList(elements);
    }

    public boolean canAppend() {
        return elements.size() < MAX_ELEMENTS;
    }

    public void append(Expr item) {
        if (canAppend()) elements.add(new ExprSlot(this, JType.INT, item));
    }

    public void removeLast() {
        if (!elements.isEmpty()) elements.remove(elements.size() - 1);
    }

    @Override
    public Value eval(ExecutionContext ctx) throws InterruptedException {
        int[] array = new int[elements.size()];
        for (int i = 0; i < array.length; i++) array[i] = elements.get(i).eval(ctx).asInt();
        ctx.recordLessonUse(this);
        return new Value(JType.INT_ARRAY, array);
    }
}
