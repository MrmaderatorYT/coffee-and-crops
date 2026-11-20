package com.ccs.coffee_n_crops.lang.ast.stmt;

import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.ast.Node;
import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.ast.expr.Literal;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.interp.LoopJump;
import com.ccs.coffee_n_crops.lang.interp.ScriptError;
import com.ccs.coffee_n_crops.lang.types.JType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@code switch (subject) { case 0: ... break; default: ... break; }} on an int or a String.
 * Cases have a final {@code break;} by default; later lessons can remove it to fall through.
 */
public final class SwitchStmt extends Stmt {

    /** One {@code case X:} arm. */
    public static final class Case extends Node {
        /** The label: a slot that accepts only a constant of the subject's type, as Java requires. */
        public final ExprSlot label;
        public final Block body = new Block();
        private boolean fallThrough;

        Case(SwitchStmt owner, Object value) {
            this.parent = owner;
            this.label = ExprSlot.constant(this, owner.subject.type, new Literal(owner.subject.type, value));
            this.body.parent = this;
        }

        public boolean fallsThrough() {
            return fallThrough;
        }

        public void setFallThrough(boolean fallThrough) {
            this.fallThrough = fallThrough;
        }

        /** The label's constant: an Integer or a String. */
        public Object value() {
            return ((Literal) label.get()).value;
        }
    }

    /** What is switched on. Its type, int or String, is fixed when the statement is created. */
    public final ExprSlot subject;
    private final List<Case> cases = new ArrayList<>();
    private Block defaultBlock;

    /** A switch on an int. */
    public SwitchStmt(Expr subject) {
        this(JType.INT, subject);
    }

    /** @param subjectType {@link JType#INT} or {@link JType#STRING} */
    public SwitchStmt(JType subjectType, Expr subject) {
        if (subjectType != JType.INT && subjectType != JType.STRING) {
            throw new IllegalArgumentException("cannot switch on " + subjectType.javaName);
        }
        this.subject = new ExprSlot(this, subjectType, subject);
    }

    public List<Case> cases() {
        return Collections.unmodifiableList(cases);
    }

    /** Explicit blocks preserve the editor's separate local scopes in the printed Java. */
    public boolean needsScopedArms() {
        for (Case arm : cases) {
            if (arm.fallsThrough() || declaresLocal(arm.body)) return true;
        }
        return defaultBlock != null && declaresLocal(defaultBlock);
    }

    private static boolean declaresLocal(Block body) {
        for (Stmt statement : body.statements()) {
            if (statement instanceof VarDecl) return true;
        }
        return false;
    }

    /** @param value an Integer or a String, matching the subject's type */
    public Case addCase(Object value) {
        Case c = new Case(this, value);
        cases.add(c);
        return c;
    }

    public void removeCase(Case c) {
        if (cases.remove(c)) c.parent = null;
    }

    /**
     * A label no existing case uses: for ints one more than the largest so far, for Strings the
     * first crop name not taken yet.
     */
    public Object nextLabel() {
        if (subject.type == JType.STRING) {
            for (String name : FarmWorld.CROP_NAMES) {
                if (!hasLabel(name)) return name;
            }
            int next = 1;
            while (hasLabel("crop" + next)) next++;
            return "crop" + next;
        }
        int next = 0;
        for (Case c : cases) {
            int value = (Integer) c.value();
            if (value != Integer.MAX_VALUE) next = Math.max(next, value + 1);
        }
        if (hasLabel(next)) {
            next = 0;
            while (hasLabel(next)) next++;
        }
        return next;
    }

    private boolean hasLabel(Object value) {
        for (Case c : cases) {
            if (java.util.Objects.equals(c.value(), value)) return true;
        }
        return false;
    }

    /** @return the default block, or null when the switch has none */
    public Block defaultBlock() {
        return defaultBlock;
    }

    public Block addDefault() {
        if (defaultBlock == null) {
            defaultBlock = new Block();
            defaultBlock.parent = this;
        }
        return defaultBlock;
    }

    public void removeDefault() {
        if (defaultBlock != null) defaultBlock.parent = null;
        defaultBlock = null;
    }

    @Override
    public List<Block> childBlocks() {
        List<Block> out = new ArrayList<>();
        for (Case c : cases) out.add(c.body);
        if (defaultBlock != null) out.add(defaultBlock);
        return out;
    }

    @Override
    public void exec(ExecutionContext ctx) throws InterruptedException {
        ctx.checkpoint(this);
        ctx.clock.awaitTicks(VarDecl.THINK_TICKS);
        Object value = subject.eval(ctx).raw; // Integer and String both compare with equals()
        if (value == null) throw new ScriptError("NullPointerException");
        int start = 0;
        while (start < cases.size() && !value.equals(cases.get(start).value())) start++;
        try {
            for (int index = start; index < cases.size(); index++) {
                Case arm = cases.get(index);
                executeArm(ctx, arm.body);
                if (!arm.fallsThrough()) return;
                if (index + 1 < cases.size() || defaultBlock != null) ctx.recordLessonUse(arm);
            }
            if (defaultBlock != null) executeArm(ctx, defaultBlock);
        } catch (LoopJump jump) {
            if (jump.target != this) throw jump;
        }
    }

    private static void executeArm(ExecutionContext ctx, Block block) throws InterruptedException {
        ctx.pushScope();
        try {
            block.exec(ctx);
        } finally {
            ctx.popScope();
        }
    }
}
