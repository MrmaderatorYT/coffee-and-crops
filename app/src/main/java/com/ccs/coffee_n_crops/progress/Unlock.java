package com.ccs.coffee_n_crops.progress;

/** Keys for the code blocks a level makes available. Later the skill tree grants these. */
public final class Unlock {
    public static final String MOVE = "farmer.move";
    public static final String HARVEST = "farmer.harvest";
    public static final String WATER = "farmer.water";
    public static final String FERTILIZE = "farmer.fertilize";
    public static final String WAIT = "farmer.waitOneTick";
    public static final String IS_RIPE = "farmer.isRipe";
    public static final String FOR = "for";
    public static final String IS_DRY = "farmer.isDry";
    public static final String CAN_MOVE = "farmer.canMove";
    public static final String WHILE = "while";
    /** The enhanced for loop over {@code Direction.values()}. */
    public static final String FOR_EACH = "for-each";
    public static final String IF = "if";
    /** Adding {@code else if} arms to an if statement. */
    public static final String ELSE_IF = "else if";
    public static final String SWITCH = "switch";
    public static final String SWITCH_FALL_THROUGH = "switch-fall-through";
    /** {@code int} declarations and int variables in expressions. */
    public static final String INT = "int";
    /** {@code boolean} declarations and boolean variables in expressions. */
    public static final String BOOLEAN = "boolean";
    public static final String WALK_TO = "farmer.walkTo";
    /** The water tank: {@code farmer.getWater()} and {@code farmer.refill()}. */
    public static final String TANK = "farmer.getWater";
    public static final String GET_CROP = "farmer.getCrop";
    public static final String CROP_ENUM = "crop-enum";
    /** {@code double} declarations, literals and arithmetic. */
    public static final String DOUBLE = "double";
    /** {@code String} declarations, constants and {@code equals}. */
    public static final String STRING = "String";
    public static final String STRING_OPERATIONS = "string-operations";
    /** Counts indexed by text keys. */
    public static final String HASH_MAP = "hash-map";
    public static final String TEXT_INPUT = "text-input";
    /** {@code int[]} from {@code farmer.scanRow()}: indexing, {@code .length}, for-each over it. */
    public static final String ARRAY = "int[]";
    /** {@code int[][]} from {@code farmer.scanField()}. */
    public static final String GRID = "int[][]";
    /** {@code ArrayList<Integer>}: {@code add}, {@code get}, {@code size}, for-each over it. */
    public static final String LIST = "ArrayList";
    /** Assignment statements: {@code name = value;}. */
    public static final String ASSIGN = "assign";
    /** Arithmetic operators. */
    public static final String MATH = "math";
    /** Comparison and logical operators. */
    public static final String LOGIC = "logic";
    /** Declaring and calling helper methods with their own local scope. */
    public static final String METHODS = "methods";
    /** Methods that return an int, and their return statements. */
    public static final String RETURN = "return";
    /** Helper methods with an additional int parameter. */
    public static final String PARAMETERS = "parameters";
    /** Calling a helper from its own body. */
    public static final String RECURSION = "recursion";
    /** A try block with catch arms or a finally block. */
    public static final String TRY = "try";
    /** Catching an empty tank and recovering with a refill. */
    public static final String CATCH_EMPTY_TANK = "catch.EmptyTankException";
    /** Catching an attempted move beyond the farm edge. */
    public static final String CATCH_OUT_OF_BOUNDS = "catch.OutOfBoundsFarmException";
    /** Catching the common superclass of the farm exceptions. */
    public static final String CATCH_FARM = "catch.FarmException";
    /** Cleanup that runs after the try and catch arms. */
    public static final String FINALLY = "finally";
    /** Named classes, per-object fields and object creation. */
    public static final String CLASSES = "classes";
    /** Explicit constructors, parameters and this.field initialization. */
    public static final String CONSTRUCTORS = "constructors";
    /** Instance method declarations and calls through an object. */
    public static final String INSTANCE_METHODS = "instance-methods";
    /** A user class extending another user class, super and overrides. */
    public static final String INHERITANCE = "inheritance";
    /** Interface signatures and a class's implements clause. */
    public static final String INTERFACES = "interfaces";
    /** Object arrays, including arrays whose component type is an interface. */
    public static final String OBJECT_ARRAYS = "object-arrays";
    /** Object references and common methods inherited from Object. */
    public static final String OBJECT_METHODS = "object-methods";
    /** Runtime type checks against a class or interface. */
    public static final String INSTANCE_OF = "instanceof";
    /** Initialized final locals and class constants. */
    public static final String CONSTANTS = "constants";
    public static final String COMPOUND_ASSIGN = "compound-assign";
    public static final String SUBTRACT_ASSIGN = "subtract-assign";
    public static final String DECREMENT = "decrement";
    public static final String DO_WHILE = "do-while";
    public static final String BREAK = "break";
    public static final String CONTINUE = "continue";
    public static final String NEW_ARRAY = "new-array";
    public static final String ARRAY_LITERAL = "array-literal";
    public static final String GRID_ALLOCATION = "grid-allocation";
    public static final String GRID_ASSIGN = "grid-assign";
    public static final String ARRAY_ASSIGN = "array-assign";
    public static final String LIST_CONTAINS = "list-contains";
    public static final String LIST_SET = "list-set";
    public static final String LIST_REMOVE = "list-remove";
    /** Explicit cursor over an ArrayList, including iterator removal. */
    public static final String ITERATOR = "iterator";
    /** Present and absent results with an explicit fallback. */
    public static final String OPTIONAL = "optional";
    /** A lazy filter followed by findFirst from an integer list. */
    public static final String STREAMS = "streams";
    public static final String STREAM_FOR_EACH = "stream.forEach";
    public static final String STREAM_MAP = "stream.map";
    public static final String STREAM_FILTER = "stream.filter";
    public static final String STREAM_COUNT = "stream.count";
    public static final String STREAM_ANY_MATCH = "stream.anyMatch";
    public static final String STREAM_METHOD_REF = "stream.methodReference";
    public static final String FUNCTION_VALUES = "function-values";

    /** Assertions with a Boolean condition and an optional message. */
    public static final String ASSERT = "assert";
    /** Run two background jobs on real threads, then join both. */
    public static final String THREADS = "threads";
    public static final String SYNCHRONIZED = "synchronized";
    public static final String VOLATILE = "volatile";
    public static final String ATOMIC = "atomic";
    /** Two farmhands with separate lambda bodies and one shared world. */
    public static final String TEAM = "team";
    /** Named {@code ReentrantLock} declarations and lock guards with finally cleanup. */
    public static final String REENTRANT_LOCK = "reentrant-lock";

    private Unlock() {
    }
}
