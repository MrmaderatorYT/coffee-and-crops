package com.ccs.coffee_n_crops.lang.ast;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base of every AST node. The editor shows nodes as blocks, the interpreter executes them,
 * and the debugger highlights them by {@link #id}.
 */
public abstract class Node {
    private static final AtomicInteger NEXT_ID = new AtomicInteger();

    /** Unique within the process. Used to map an executing node back to its editor row. */
    public final int id = NEXT_ID.incrementAndGet();

    /** Enclosing node, or null for the program root. Maintained by {@code Block} and compound statements. */
    public Node parent;
}
