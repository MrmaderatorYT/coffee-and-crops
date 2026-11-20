package com.ccs.coffee_n_crops.lang.ast.expr;

import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.interp.ScriptError;
import com.ccs.coffee_n_crops.lang.types.JType;
import com.ccs.coffee_n_crops.lang.types.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.stream.Stream;

/** A sequential pipeline over an integer list with an ordered series of intermediate stages. */
public final class StreamCall extends Expr {
    public enum StageKind {
        FILTER("filter", JType.BOOLEAN), MAP("map", JType.INT);

        public final String method;
        public final JType resultType;

        StageKind(String method, JType resultType) {
            this.method = method;
            this.resultType = resultType;
        }
    }

    public enum Terminal {
        FOR_EACH("forEach", JType.VOID, JType.VOID), COUNT("count", JType.LONG, null),
        ANY_MATCH("anyMatch", JType.BOOLEAN, JType.BOOLEAN), FIND_FIRST("findFirst", JType.INT_OPTIONAL, null);

        public final String method;
        public final JType resultType;
        /** Expected callback result, or null when the operation takes no callback. */
        public final JType callbackResult;

        Terminal(String method, JType resultType, JType callbackResult) {
            this.method = method;
            this.resultType = resultType;
            this.callbackResult = callbackResult;
        }
    }

    public record Stage(StageKind kind, StreamCallback callback) { }

    public final String source;
    public final Terminal terminal;
    public final List<Stage> stages;
    public final StreamCallback callback;

    public StreamCall(String source, Terminal terminal, List<Stage> stages, StreamCallback callback) {
        this.source = source;
        this.terminal = terminal;
        this.stages = Collections.unmodifiableList(new ArrayList<>(stages));
        this.callback = callback;
        for (Stage stage : this.stages) stage.callback().parent = this;
        if (callback != null) callback.parent = this;
    }

    @Override public Value eval(ExecutionContext ctx) throws InterruptedException {
        ctx.checkActive();
        List<Integer> list = ctx.readVariable(source).asIntList();
        if (list == null) throw new ScriptError("NullPointerException");
        Stream<Integer> stream = list.stream();
        for (Stage stage : stages) {
            StreamCallback.Bound operation = stage.callback().bind(ctx);
            stream = stage.kind() == StageKind.FILTER
                    ? stream.filter(item -> apply(ctx, operation, item).asBoolean())
                    : stream.map(item -> apply(ctx, operation, item).asInt());
        }

        StreamCallback.Bound operation = callback == null ? null : callback.bind(ctx);
        Value result;
        try {
            result = switch (terminal) {
                case FOR_EACH -> {
                    stream.forEach(item -> apply(ctx, operation, item));
                    yield Value.VOID;
                }
                // A sized stream may omit intermediate map evaluations on common JDKs.
                case COUNT -> Value.ofLong(stream.count());
                case ANY_MATCH -> Value.ofBoolean(stream.anyMatch(item -> apply(ctx, operation, item).asBoolean()));
                case FIND_FIRST -> new Value(JType.INT_OPTIONAL, stream.findFirst());
            };
        } catch (InterruptedCallback interrupted) {
            throw (InterruptedException) interrupted.getCause();
        } catch (ConcurrentModificationException modified) {
            throw new ScriptError("ConcurrentModificationException");
        }
        ctx.recordLessonUse(this);
        return result;
    }

    private static Value apply(ExecutionContext ctx, StreamCallback.Bound callback, int element) {
        try {
            ctx.checkActive();
            return callback.apply(ctx, element);
        } catch (InterruptedException interrupted) {
            throw new InterruptedCallback(interrupted);
        }
    }

    private static final class InterruptedCallback extends RuntimeException {
        InterruptedCallback(InterruptedException cause) { super(cause); }
    }
}
