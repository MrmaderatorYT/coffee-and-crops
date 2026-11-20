package com.ccs.coffee_n_crops.lang.ast.stmt;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;

/** Integer compound assignment, such as {@code count += 1;} or {@code count -= 1;}. */
public final class CompoundAssign extends Stmt {
    public enum Operation {
        ADD("+="), SUBTRACT("-=");

        public final String symbol;

        Operation(String symbol) {
            this.symbol = symbol;
        }
    }

    public final String name;
    public final Operation operation;
    public final ExprSlot value;

    public CompoundAssign(String name, Expr value) {
        this(name, Operation.ADD, value);
    }

    public CompoundAssign(String name, Operation operation, Expr value) {
        this.name = name;
        this.operation = operation;
        this.value = new ExprSlot(this, JType.INT, value);
    }

    @Override
    public void exec(ExecutionContext ctx) throws InterruptedException {
        ctx.checkpoint(this);
        ctx.clock.awaitTicks(VarDecl.THINK_TICKS);
        int previous = ctx.readVariable(name).asInt();
        int operand = value.eval(ctx).asInt();
        int result = operation == Operation.ADD ? previous + operand : previous - operand;
        ctx.assignVariable(name, Value.ofInt(result));
        ctx.recordLessonUse(this);
    }
}
