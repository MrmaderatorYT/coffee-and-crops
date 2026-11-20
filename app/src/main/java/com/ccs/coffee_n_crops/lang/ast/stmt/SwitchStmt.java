package com.ccs.coffee_n_crops.lang.ast.stmt;

import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.lang.ast.Expr;
import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.ast.Node;
import com.ccs.coffee_n_crops.lang.ast.Stmt;
import com.ccs.coffee_n_crops.lang.ast.expr.Literal;
import com.ccs.coffee_n_crops.lang.interp.ExecutionContext;
import com.ccs.coffee_n_crops.lang.interp.LoopJump;
import com.ccs.coffee_n_crops.lang.types.JType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@code switch (subject) { case 0: ... break; default: ... break; }} on an int or a String.
 * The editor supplies a final {@code break;} where needed, so cases cannot fall through.
 */
public final class SwitchStmt extends Stmt {

    /** One {@code case X:} arm. */
    public static final class Case extends Node {
        /** The label: a slot that accepts only a constant of the subject's type, as Java requires. */
        public final ExprSlot label;
        public final Block body = new Block();

        Case(SwitchStmt owner, Object value) {
            this.parent = owner;
            this.label = ExprSlot.constant(this, owner.subject.type, new Literal(owner.subject.type, value));
            this.body.parent = this;
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

    /** @param value an Integer or a String, matching the subject's type */
    public Case addCase(Object value) {
        Case c = new Case(this, value);
        cases.add(c);
        return c;
    }

    public void removeCase(Case c) {
        cases.remove(c);
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
            return FarmWorld.CROP_NAMES[0];
        }
        int next = 0;
        for (Case c : cases) next = Math.max(next, (Integer) c.value() + 1);
        return next;
    }

    private boolean hasLabel(Object value) {
        for (Case c : cases) {
            if (c.value().equals(value)) return true;
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
        Block chosen = defaultBlock;
        for (Case c : cases) {
            if (c.value().equals(value)) {
                chosen = c.body;
                break;
            }
        }
        if (chosen == null) return;
        ctx.pushScope();
        try {
            chosen.exec(ctx);
        } catch (LoopJump jump) {
            if (jump.target != this) throw jump;
        } finally {
            ctx.popScope();
        }
    }
}
