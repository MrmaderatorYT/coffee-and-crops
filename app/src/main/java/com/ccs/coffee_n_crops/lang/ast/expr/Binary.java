package com.ccs.coffee_n_crops.lang.ast.expr;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.interp.ScriptError;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;

/** A two-operand expression: arithmetic, comparison, or boolean logic. */
public final class Binary extends Expr {

    /** Operators in one family take the same operand type and give the same result type. */
    public enum Family {
        ARITHMETIC(JType.INT, JType.INT),
        COMPARISON(JType.INT, JType.BOOLEAN),
        LOGIC(JType.BOOLEAN, JType.BOOLEAN);

        public final JType operand;
        public final JType result;

        Family(JType operand, JType result) {
            this.operand = operand;
            this.result = result;
        }
    }

    public enum Op {
        MUL("*", Family.ARITHMETIC, 6), DIV("/", Family.ARITHMETIC, 6), MOD("%", Family.ARITHMETIC, 6),
        ADD("+", Family.ARITHMETIC, 5), SUB("-", Family.ARITHMETIC, 5),
        LT("<", Family.COMPARISON, 4), LE("<=", Family.COMPARISON, 4),
        GT(">", Family.COMPARISON, 4), GE(">=", Family.COMPARISON, 4),
        EQ("==", Family.COMPARISON, 3), NE("!=", Family.COMPARISON, 3),
        AND("&&", Family.LOGIC, 2),
        OR("||", Family.LOGIC, 1);

        public final String symbol;
        public final Family family;
        /** Java operator precedence; higher binds tighter. Used to place parentheses when printing. */
        public final int precedence;

        Op(String symbol, Family family, int precedence) {
            this.symbol = symbol;
            this.family = family;
            this.precedence = precedence;
        }
    }

    private volatile Op op;
    /** The widest operand type the editor offers, or boolean for logic. */
    public final JType operandType;
    public final ExprSlot left;
    public final ExprSlot right;

    /** Logic on booleans, or arithmetic and comparison on ints. */
    public Binary(Op op, Expr left, Expr right) {
        this(op, op.family.operand, left, right);
    }

    /**
     * @param operandType the widest numeric type allowed in either slot
     */
    public Binary(Op op, JType operandType, Expr left, Expr right) {
        boolean numeric = operandType == JType.INT || operandType == JType.LONG || operandType == JType.DOUBLE;
        if (op.family == Family.LOGIC ? operandType != JType.BOOLEAN : !numeric) {
            throw new IllegalArgumentException(op.symbol + " does not apply to " + operandType.javaName);
        }
        this.op = op;
        this.operandType = operandType;
        this.left = new ExprSlot(this, operandType, left);
        this.right = new ExprSlot(this, operandType, right);
    }

    public Op op() {
        return op;
    }

    /** Swaps the operator for another of the same family, so operand and result types stay valid. */
    public void setOp(Op next) {
        if (next.family != op.family) {
            throw new IllegalArgumentException(next.symbol + " is not interchangeable with " + op.symbol);
        }
        op = next;
    }

    @Override
    public Value eval(ExecutionContext ctx) throws InterruptedException {
        Op op = this.op;
        if (op.family == Family.LOGIC) {
            // Short-circuit, exactly like Java: the right side may never run.
            boolean a = left.eval(ctx).asBoolean();
            if (op == Op.AND) return Value.ofBoolean(a && right.eval(ctx).asBoolean());
            return Value.ofBoolean(a || right.eval(ctx).asBoolean());
        }
        Value leftValue = left.eval(ctx);
        Value rightValue = right.eval(ctx);
        if (leftValue.type == JType.DOUBLE || rightValue.type == JType.DOUBLE) {
            return evalDouble(op, leftValue.asDouble(), rightValue.asDouble());
        }
        boolean wide = leftValue.type == JType.LONG || rightValue.type == JType.LONG;
        long a = wide ? leftValue.asLong() : leftValue.asInt();
        long b = wide ? rightValue.asLong() : rightValue.asInt();
        return switch (op) {
            case LT -> Value.ofBoolean(a < b);
            case LE -> Value.ofBoolean(a <= b);
            case GT -> Value.ofBoolean(a > b);
            case GE -> Value.ofBoolean(a >= b);
            case EQ -> Value.ofBoolean(a == b);
            case NE -> Value.ofBoolean(a != b);
            case ADD -> integral(a + b, wide);
            case SUB -> integral(a - b, wide);
            case MUL -> integral(a * b, wide);
            case DIV, MOD -> {
                if (b == 0) throw new ScriptError("ArithmeticException: / by zero");
                yield integral(op == Op.DIV ? a / b : a % b, wide);
            }
            default -> throw new ScriptError("Unknown operator " + op.symbol);
        };
    }

    private static Value integral(long result, boolean wide) {
        return wide ? Value.ofLong(result) : Value.ofInt((int) result);
    }

    /** Double arithmetic follows Java: dividing by zero gives Infinity or NaN, not an exception. */
    private static Value evalDouble(Op op, double a, double b) {
        switch (op) {
            case LT: return Value.ofBoolean(a < b);
            case LE: return Value.ofBoolean(a <= b);
            case GT: return Value.ofBoolean(a > b);
            case GE: return Value.ofBoolean(a >= b);
            case EQ: return Value.ofBoolean(a == b);
            case NE: return Value.ofBoolean(a != b);
            case ADD: return Value.ofDouble(a + b);
            case SUB: return Value.ofDouble(a - b);
            case MUL: return Value.ofDouble(a * b);
            case DIV: return Value.ofDouble(a / b);
            case MOD: return Value.ofDouble(a % b);
            default: throw new ScriptError("Unknown operator " + op.symbol);
        }
    }
}
