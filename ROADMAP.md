# ROADMAP — Coffee & Crops

What is done and what to build next, in recommended order. Read [AGENTS.md](AGENTS.md) first for
the rules and the "adding a language feature" recipe. Work in small slices: one construct, one or
two levels, tests, then verify on a device.

## Done

| Chapter | Topic | Levels | Constructs |
|---|---|---|---|
| 1 | Variables & types | 1, 2, 10, 11 | `int`, `boolean`, `double`, `String`, declarations, assignment |
| 2 | Operators & conditionals | 3, 4, 6, 11 | arithmetic, comparison, `&&` `\|\|` `!`, `if / else if / else`, `switch / case / default`, `.equals()` |
| 3 | Loops | 1, 4, 5 | `for`, `while`, for-each |
| 4 | Arrays & collections | 7, 8, 9 | `int[]`, `int[][]`, `ArrayList<Integer>` |
| 5 | Methods & recursion | 12, 13, 14, 15 | helper methods, parameters, `return`, recursive calls |
| 6 | OOP basics | 19, 20, 21, 22 | classes, fields, constructors, `new`, `this`, instance methods, inheritance, `super`, overrides and dynamic dispatch |
| 7 | Exceptions | 16, 17, 18 | `try`, typed `catch` arms, `finally`, recovery and cleanup |
| Practice | Loops and collections | 23–32 | `+=`, `--`, `do-while`, `break`, `continue`, `new int[n]`, indexed assignment, `contains`, `set`, `remove(int)` |
| 6 | OOP contracts and polymorphism | 33–35 | `interface`, `implements`, one-dimensional object arrays, mixed implementations in for-each |
| 6 | Object and class state | 36–39 | `Object` methods, `instanceof`, constructors, `final` locals and `static final` fields |
| 8 | Iteration and optional results | 40–42 | `Iterator<Integer>`, `Optional<Integer>`, `stream().filter(...).findFirst()` |
| 7 | Assertions | 43 | `assert` with optional detail, evaluated with assertions enabled |
| 9 | Shared state | 44–46 | paired worker threads, `synchronized`, `volatile`, `AtomicInteger` |
| 8 | Stream pipelines | 47–52 | expression and block lambdas, bound method references, `map`, `forEach`, `count`, `anyMatch` |
| 9 | Farmhand teams | 53–56 | two working farmhands, shared harvest tallies, `ReentrantLock`, lock ordering and deadlock detection |
| Farm | Coffee and navigation | 57–58 | coffee harvest, watering, fertilizer, timed growth and rocks |
| Practice | Strings, maps and arrays | 59–62 | text entry, `+`, `length()`, `HashMap`, array literals and 2D grid writes |
| Practice | Flow and typed scanning | 63–65 | `-=`, switch fall-through, and `Crop[]` |
| 8 | Function values | 66–67 | functional-interface variables, `Math::abs`, and `int[]::new` |

Also done: tree-walking interpreter with one thread per farmhand; step debugger (Play, Pause, Step,
x1/x5/x10) with line highlighting; pre-run validator with javac-style errors; block editor with
typed slots, soft-wrapped lines and scrollable palette tabs; JSON save per level (including helper
methods, backward compatible with old saves); level select with progress; original 3D garden and human farmhand.
The 0.2.0 JVM suite has 228 passing tests covering saved reference solutions, source
compilation with the build JDK, stepping, structural editor edits, object behavior, save migration,
viewport fitting and action interpolation. Debug APK assembly and Android Lint pass.

Chapter 5 adds separate method frames and a toolbar for `run()` and helper methods. Helpers explicitly take `Farmhand farmer`
as their first argument, so their printed source is valid Java without capturing run's locals.
Each call has isolated parameters and locals; recursion is capped at 64 calls and reports
`StackOverflowError`. Reset interrupts workers without waiting on the UI thread, and cancelled
workers cannot mutate a freshly reset farm.

Chapter 7 adds an `exceptions` palette tab, typed catch arms and optional finally cleanup.
Catch parameters have Java scope; earlier catches cannot shadow a later subtype. `finally` runs
on normal completion, a caught or uncaught error, and return; a return/error in finally overrides
the pending result. Reset cancels the entire run and skips cleanup to preserve the reset farm.
Goals check actual handler/finally execution, not only the presence of an unused block.

The owner approved class tabs and levels 19–22 before implementation. OOP levels add a Program
tab and separate class tabs, foldable constructor/method bodies, a contextual objects palette,
class-aware diagnostics and debugger navigation, and immutable object-state snapshots. Ordinary
objects share the existing farmhand. Constructing a Worker never starts another farmhand thread.
The interpreter models named reference types and per-instance field storage, including inherited
fields and overridden dispatch. Old programs and JSON files keep their existing behavior.

All 11 levels were completed by hand on a real phone (before palette tabs were added).
Levels 12–22 have automated reference solutions. Level 12 was assembled and completed on a Pixel
7a using the method toolbar. The device check also covered restoring the insertion point inside a
loop, choosing a call and filling a typed expression in level 13, inserting an instance call in
level 21, and using the Ukrainian controls.
The class member strip was verified on a Pixel 7a. Level 19 was built and completed in a test
world. Levels 20, 22 and 33 covered the Ukrainian controls, folded bodies, constructor signature
updates, restoring an insertion point inside a loop, and opening an interface signature.
The add buttons were also checked on the Pixel 7a in levels 19, 21, 33 and 39, covering direct
creation, field and interface choosers, constructor deletion and recreation, and editor lock.
Levels 23–32 have automated solutions verified after a save round trip and by the build JDK.
The Android palette for `do-while`, array assignment and list removal was verified on a Pixel 7a.
Levels 33–35 were assembled with the editor controls on a Pixel 7a and completed in test worlds,
including creation of an interface, editing array indices and dispatch through the interface.
Levels 36, 42, 43, 45 and 46 were assembled and completed on a Pixel 7a in test worlds without
changing saved programs or progress.
Levels 47–52 were assembled and completed on a Pixel 7a with the Android editor. The device
check covered lambda body insertion, editing pipeline operands, long comparisons and `route::add`.
Levels 53–56 were built and completed on a Pixel 7a. The device check covered choosing a farmhand
for Step, detecting the pump deadlock, and completing the corrected lock order in a test world.
The English and Ukrainian buttons, persisted choice, localized lesson screens, and all six farm
environments were verified on a Pixel 7a.
The 3D redesign was installed on a Pixel 7a and verified by replaying the pre-update level 8
program. The original save loaded with the Farmhand API and completed with 7 wheat and 4
watered plants. Adding and deleting a command in level 1 also worked on the device.

## 0. Release verification

For 0.2.0, Pixel 7a smoke checks covered undo/redo, reordering, copying, variable renaming,
typed text and cursor restoration, expression tabs, level 19 completion, switch case creation,
default creation and deletion, duplicate case validation, debugger Step/Resume/Reset, paused
variables, hints opening, and the side-by-side landscape layout. All 67 levels have bundled
worked solutions and star targets measured from successful reference runs.

Additional manual regression scenarios for future editor changes:

- [ ] Palette tabs: the scrollable strip (up to six tabs + fixed `⌫ Delete`) fits the width; tabs switch; small levels
      still show a single row; arm buttons appear in every tab.
- [ ] Delete removes an empty `else`, `else if`, `case`, `default`.
- [x] `+ case` and `+ default` buttons, and the duplicate-case-label error on Pixel 7a.
- [ ] Pause and Resume in the middle of a run.
- [x] Level 11 solved with `.equals()` as well as with `switch` in the JVM suite.
- [ ] Levels 12–15: build the solutions by tapping; create and delete an empty helper; edit a
      parameter argument and return value; Step into a helper and back; Reset during recursion.
- [ ] Levels 16–18: build the solutions by tapping; add catch/finally from a nested body; delete
      optional arms and check the last required handler is preserved; Step through catch, return
      and finally; Reset while paused in cleanup.
- [ ] Levels 19–22: build each solution through the class tabs; edit fields and constructor
      parameters; fold/unfold members; Step across constructors and overrides; inspect each
      object's fields; Reset restores the editing location; class errors open the right tab.

## 1. Chapter 5 — Methods & recursion — implemented

Later chapters need methods, so this comes first.

- [x] AST: `MethodDecl` (name, return type, parameters, body), `ReturnStmt`, `UserCall` expressions /
      call statements. Root `Block` retains run's statements and owns a list of helper methods.
- [x] Editor: jump between `run()` and helper methods from the top strip, create methods from
      a separate button, and insert existing calls from a shortcut beside the palette.
- [x] Interpreter: call stack with a frame per call; a depth limit that raises `StackOverflowError`
      with Java's wording instead of crashing the app.
- [x] Validator: `missing return statement`, `incompatible types` on return, wrong argument
      count/types, `unreachable statement` after `return`.
- [x] Debugger: stepping follows the call into the method and back (JVM verified; device check above).
- [x] Levels 12–15: extract `harvestRow(farmer)` and call it per row; `int countRipe(farmer)` with
      `return`; a parameter in `tendRow(farmer, y)`; recursion with `walkToEdge(farmer)`.

## 2. Chapter 7 — Exceptions — implemented

- [x] AST: `TryStmt` with ordered `catch (Type e)` arms and optional `finally`.
- [x] Interpreter: `EmptyTankException`, `OutOfBoundsFarmException` and their `FarmException`
      base are catchable; unmatched errors propagate; finally preserves Java return/error priority.
- [x] Validator: `exception X has already been caught`, unknown exception type, duplicate/local
      catch names, bare try, and return/reachability checks through try/catch/finally.
- [x] Editor: exceptions tab, level-restricted starters, nested-body arm commands, scoped names,
      catch/finally stepping highlights, and Delete that retains valid Java.
- [x] Levels 16–18: catch an empty tank and explicitly retry watering; find the field edge by
      catching a failed move; return from catch while finally takes the farmhand home.
- [x] Backward-compatible JSON, printed-source compilation and automated reference solutions.
- `throw`, multi-catch and try-with-resources are deferred; only the farm exception hierarchy is
  exposed. Device verification remains above.

## 3. Chapter 6 — OOP — implemented

- [x] Classes with per-object fields, constructors and instance methods; `new`; `this`.
- [x] User-class inheritance, `super(...)`, `@Override` and dispatch through a base-typed reference.
- [x] Class-tab editor design approved before implementation; contextual member creation,
      typed reference slots, folding, automatic debugger navigation and object state display.
- [x] A fixed strip in each type tab opens fields, the constructor, methods and interface
      signatures directly. It unfolds the selected body and restores its last insertion point.
- [x] Class tabs have fixed add buttons for fields, constructors and methods. The editor opens the
      new member immediately and shows a chooser when the lesson offers several declarations.
- [x] Level 19: Counter field controls the harvest loop.
- [x] Level 20: two RowPlan objects initialized with different constructor arguments.
- [x] Level 21: Worker instances use their own row and Farmhand fields in instance methods.
- [x] Level 22: `Harvester extends Worker`, base initialization and overridden `work()` dispatch.
- [x] Backward-compatible saves and actual Java source compilation for every reference solution.
- [x] Level 33: declare `Tool`, implement it with `Harvester`, and call `work()` through `Tool`.
- [x] Level 34: allocate `RowPlan[]`, write and read both route objects by index, and run their plans.
- [x] Level 35: put `Harvester` and `Waterer` in `Tool[]` and tend both crops with one polymorphic call.
- [x] Type tabs, signature and implementation commands, typed array blocks, and saved reference
      solutions checked by the build JDK and interpreter.
- [x] Levels 36–39: base `Object` methods, runtime type checks with `instanceof`, objects constructed
      with separate field state, immutable locals and a class constant.

Current limits: one constructor and one method per name, one direct interface per class, interface
method signatures and one-dimensional object arrays. The runtime limits call depth to 64 and
allocations to 512 objects, reporting Java-style errors safely.

## 4. Chapter 8 — Streams & lambdas

- [x] Level 40: follow a route with `iterator()`, `hasNext()` and `next()`.
- [x] Level 41: recover from `Optional.empty()` using `orElse()`.
- [x] Level 42: filter integer stops with a predicate and use `findFirst()` to get an optional stop.
- [x] Level 47: visit each integer stop through a block lambda passed to `forEach()`.
- [x] Level 48: transform the stops using a `map()` expression lambda.
- [x] Level 49: filter and `count()` stops, then compare the `long` result.
- [x] Level 50: use the boolean result of a short-circuiting `anyMatch()`.
- [x] Level 51: copy elements into a route using the bound method reference `route::add`.
- [x] Level 52: compose `map()`, `filter()` and `forEach()` to harvest while protecting sprouts.
- [x] Standalone functional-interface variables, static and unbound method references, and array
      constructor references.

## 5. Chapter 9 — Concurrency

The farmhand can launch two short worker jobs and wait for both. The jobs share captured objects
and keep their own local bindings. The main farmhand performs the resulting farm actions.

- [x] Two visible farmhands on one grid, with separate editable jobs and Java threads under
      `workTogether`. The jobs share captured references.
- [x] Levels 44–46: protect a shared counter with `synchronized`, publish a signal with `volatile`,
      and use `AtomicInteger` for atomic increments.
- [x] Level 53: split the field and let both farmhands harvest their own row.
- [x] Level 54: use one `AtomicInteger` to tally both rows.
- [x] Level 55: protect the read and write of a shared tally with `ReentrantLock`.
- [x] Level 56: reproduce a pump-lock deadlock and resolve it with consistent lock ordering.
- [x] Debugger: per-farmhand highlight colours and a Step target selector during team jobs.

## Gaps inside finished chapters

- [x] `HashMap<String, Integer>` for counts by crop name.
- [x] One-dimensional arrays: `new int[n]` and element writes (`row[i] = 1`).
- [x] Int array literals and allocation and writes for two-dimensional int arrays.
- [x] Lists: index-based `remove`, `contains`, `set`, and Java iterator behavior in for-each.
- [x] Strings: concatenation with `+`, `length()`, and player-typed text.
- [x] Typed `Crop` and `Crop[]` scanners. Earlier lessons retain their integer scanner.
- [x] `switch` fall-through in a dedicated lesson with optional case breaks.
- [x] Integer `+=`, standalone `--`, `do … while`, and unlabelled `break` / `continue`.
- [x] Subtraction assignment `-=` in a countdown lesson.
- [x] Scope-aware renaming of ordinary local variables.

## Editor & UX

- [x] Keep the chosen palette category on returning from an expression slot. Keep Done visible in
      slot editing and restore the previous insertion point when returning to a method.
- [x] Prefer wrapping long lines after `;` or `,`.
- [x] Tabs for typed slots with many variable choices.
- [x] Session undo and redo, moving statements, and copying blocks.
- [x] Stepped hints with a read-only worked solution after three failures.
- [x] Paused debugger variables grouped by scope and farmhand.

## Game & content

- [x] Original low-poly 3D garden, human farmhand, and synchronized walking, harvest, watering
      and refill animations. New Android styling and launcher mark. See [ASSETS.md](ASSETS.md).
- [x] Coffee and carrots, timed coffee growth, fertilizer, and obstacles.
- [x] Stars for meeting reference program line and action tick targets. Personal bests persist.
- [x] Bundled offline sounds with a mute control, and fertilizer animation.
- [x] English and Ukrainian interface, editor controls and all 67 lessons. The level list has an
      offline language picker. Ukrainian lesson translations are bundled with stable level IDs.
- [x] Six rotating 3D environments with distinct ground, horizons, and scenery meshes.
- [x] A separate two-farmhand sandbox with all palette features available.

## Technical debt

- JSON and SharedPreferences are the supported offline persistence for 0.2.0. Room remains an
  optional migration if there is a separate requirement for it.
- [x] Java-only instrumented smoke tests for the editor and debugger.
- [x] Landscape and tablet layout with the farm and code side by side in wide windows.
- [x] Release signing via environment configuration, R8, and an adaptive launcher icon.
