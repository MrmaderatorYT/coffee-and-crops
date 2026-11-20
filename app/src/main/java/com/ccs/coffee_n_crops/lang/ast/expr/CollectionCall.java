package com.ccs.coffee_n_crops.lang.ast.expr;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.interp.ScriptError;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CancellationException;

/** The fixed library signatures used by the iterator, Optional and stream lessons. */
public final class CollectionCall extends Expr {
    public enum Op {
        ITERATOR("iterator", JType.INT_LIST, JType.INT_ITERATOR, false),
        HAS_NEXT("hasNext", JType.INT_ITERATOR, JType.BOOLEAN, false),
        NEXT("next", JType.INT_ITERATOR, JType.INT, false),
        REMOVE("remove", JType.INT_ITERATOR, JType.VOID, false),
        OF("of", null, JType.INT_OPTIONAL, true),
        EMPTY("empty", null, JType.INT_OPTIONAL, false),
        IS_PRESENT("isPresent", JType.INT_OPTIONAL, JType.BOOLEAN, false),
        GET("get", JType.INT_OPTIONAL, JType.INT, false),
        OR_ELSE("orElse", JType.INT_OPTIONAL, JType.INT, true),
        FIND_FIRST("findFirst", JType.INT_LIST, JType.INT_OPTIONAL, true);

        public final String method;
        public final JType receiverType;
        public final JType resultType;
        public final boolean withArgument;

        Op(String method, JType receiverType, JType resultType, boolean withArgument) {
            this.method = method;
            this.receiverType = receiverType;
            this.resultType = resultType;
            this.withArgument = withArgument;
        }
    }

    public final Op op;
    /** A named list, iterator or optional. Null denotes a static Optional factory. */
    public final String receiver;
    /** The stream's lambda parameter, or null on other operations. */
    public final String parameter;
    public final ExprSlot argument;

    public CollectionCall(Op op, String receiver) {
        this(op, receiver, null, null);
    }

    public CollectionCall(Op op, String receiver, Expr argument) {
        this(op, receiver, null, argument);
    }

    public CollectionCall(Op op, String receiver, String parameter, Expr argument) {
        if ((receiver != null) != (op.receiverType != null)
                || (argument != null) != op.withArgument || (parameter != null) != (op == Op.FIND_FIRST)) {
            throw new IllegalArgumentException("Invalid signature for " + op.method);
        }
        this.op = op;
        this.receiver = receiver;
        this.parameter = parameter;
        // The lesson edits the cutoff, leaving the lambda independent of mutable outer variables.
        this.argument = argument == null ? null : op == Op.FIND_FIRST
                ? ExprSlot.constant(this, JType.INT, argument) : new ExprSlot(this, JType.INT, argument);
    }

    @Override public Value eval(ExecutionContext ctx) throws InterruptedException {
        ctx.checkActive();
        Value target = receiver == null ? null : ctx.readVariable(receiver);
        int input = argument == null ? 0 : argument.eval(ctx).asInt();
        if (target != null && target.raw == null) throw new ScriptError("NullPointerException");
        Value result;
        try {
            result = evaluate(target, input);
        } catch (CancellationException cancelled) {
            throw new InterruptedException();
        } catch (NoSuchElementException | ConcurrentModificationException | IllegalStateException failure) {
            String message = failure.getMessage();
            throw new ScriptError(failure.getClass().getSimpleName() + (message == null ? "" : ": " + message));
        }
        ctx.recordLessonUse(this);
        return result;
    }

    private Value evaluate(Value target, int input) {
        return switch (op) {
            case ITERATOR -> new Value(op.resultType, target.asIntList().iterator());
            case HAS_NEXT -> Value.ofBoolean(iterator(target).hasNext());
            case NEXT -> Value.ofInt(iterator(target).next());
            case REMOVE -> {
                iterator(target).remove();
                yield Value.VOID;
            }
            case OF -> new Value(op.resultType, Optional.of(input));
            case EMPTY -> new Value(op.resultType, Optional.empty());
            case IS_PRESENT -> Value.ofBoolean(optional(target).isPresent());
            case GET -> Value.ofInt(optional(target).get());
            case OR_ELSE -> Value.ofInt(optional(target).orElse(input));
            case FIND_FIRST -> new Value(op.resultType, target.asIntList().stream().filter(cell -> {
                if (Thread.currentThread().isInterrupted()) throw new CancellationException();
                return cell >= input;
            }).findFirst());
        };
    }

    @SuppressWarnings("unchecked")
    private static Iterator<Integer> iterator(Value value) {
        return (Iterator<Integer>) value.raw;
    }

    @SuppressWarnings("unchecked")
    private static Optional<Integer> optional(Value value) {
        return (Optional<Integer>) value.raw;
    }
}
