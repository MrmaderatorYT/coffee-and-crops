# AGENTS.md — Coffee & Crops

Guide for an AI agent (or a new developer) continuing this project. Read this first, then
[ROADMAP.md](ROADMAP.md) for what to build next.

## What this is

An offline Android game that teaches Java. The player guides a human farmhand with a block editor
whose blocks are **real, readable Java lines** (`for (int i = 0; i < 5; i++) {`), runs the program
with a step debugger, and watches the farmhand work an original low-poly 3D garden.

- Package / applicationId: `com.ccs.coffee_n_crops`
- State: 56 playable levels. Levels 33–39 cover interfaces, arrays of objects, polymorphism and
  advanced OOP. Levels 40–46 cover iterators, optional results, streams, assertions and shared
  state. Levels 47–52 cover lambdas, method references and stream pipelines. Levels 53–56 use
  two working farmhands, a shared tally, `ReentrantLock` and lock ordering. Levels 12–56 have
  automated reference solutions.

## Hard rules (set by the project owner — do not break)

1. **100% Java. No Kotlin anywhere.** No `.kt` or `.kts` files, Groovy Gradle scripts only, and no
   dependency that pulls in `kotlin-stdlib`. This is why `androidx.recyclerview` is pinned to
   `1.2.1` (newer versions bring Kotlin) and why activities extend plain `android.app.Activity`
   (no AppCompat). Before adding any dependency, check `./gradlew :app:dependencies` for `kotlin`.
2. **100% offline.** No network permission, no network calls, no analytics.
3. **No runtime bytecode compilation.** Player code is an AST run by our own tree-walking
   interpreter. Never generate or load classes/dex at runtime.
4. **Each farmhand runs on its own Java `Thread`; the UI thread must never block.** Chapter 9 depends
   on these being real threads with real races.
5. **The editor shows real Java.** Every line on screen must be Java that `javac` would accept in
   the context of `void run(Farmhand farmer)`. Error messages copy javac / JVM wording.
6. Native Android, minSdk 26, Java 17 source level. Rendering is `SurfaceView` + `Canvas`.

## Build, test, install

Run from the project root. JDK 17 is required; the Gradle wrapper (8.13) and AGP 8.11.1 are set up.

```bash
./gradlew testDebugUnitTest      # 215 JVM tests, no device needed
./gradlew assembleDebug          # -> app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -S --activity-clear-task -n com.ccs.coffee_n_crops/.app.MainActivity
```

Notes:
- The project folder name contains `&` — always quote the path in shell commands.
- `am start -S --activity-clear-task` matters: without it Android may restore a stale activity.
- The interpreter, validator, printer and serializer have no Android dependencies, so they are
  tested on the plain JVM. `org.json` is added as a test dependency for that reason.

## Architecture

Source root: `app/src/main/java/com/ccs/coffee_n_crops/`

| Package | Role |
|---|---|
| `lang/types` | Interned `JType` built-ins, named references, arrays and supported Java library types; runtime `Value`. |
| `lang/ast` | `Node`, `Stmt` (`exec`), `Expr` (`eval`), `ExprSlot`, `AstWalk`, and `Ast` (factory helpers). `expr/` and `stmt/` hold the node classes. |
| `lang/interp` | `Interpreter`, `ExecutionContext`, `Scope`, `DebugController` (play / pause / step gate), `ExecListener`, `ScriptError`. |
| `lang/runtime` | `FarmerRunnable` (one per farmhand thread), `NativeRegistry` (the `farmer.*` methods and their tick costs). |
| `lang/check` | `ScopeResolver` (Java scoping) and `Validator` (the pre-run "compiler" with javac-style messages). |
| `lang/print` | `JavaPrinter` turns AST into `Token`s / source text with correct parentheses. |
| `lang/serial` | `AstJson` saves and loads programs. |
| `farm` | The simulation: `FarmWorld`, `Tile`, `TileState`, `Farmhand`, `Direction`, `WorldClock`, and `error/` (`FarmException`, `EmptyTankException`, `OutOfBoundsFarmException`). |
| `render` | `FarmSurfaceView` (render thread), `Scene3d` (projected meshes), `FarmDiorama` (garden), `FarmhandModel` and `FarmMotion` (person and animation). |
| `editor` | The block editor: `BlockEditorView`, `BlockAdapter`, `RowFlattener`, `CodeRow`, `EditorCursor`, `PaletteProvider`, `BlockSpec`, `SyntaxColors`. |
| `progress` | `Unlock` (palette keys), `LevelDef`, `LevelCatalog` (all levels). |
| `data` | `ScriptStore` (one JSON file per level), `ProgressStore` (SharedPreferences). |
| `app` | `MainActivity` (level select), `LevelActivity` (one level: farm, editor, debugger bar). |

### Key ideas you must understand before editing

- **`ExprSlot`** is a typed, replaceable hole for an expression. Every editable value in a line is
  a slot. A `Token` carries the slot it selects, so tapping a token selects that slot and the
  palette then offers only expressions of the slot's type. `constantOnly` slots (case labels)
  accept literals only.
- **`Stmt.childBlocks()`** is how the editor, flattener, walker and serializer treat compound
  statements uniformly. A new compound statement must implement it.
- **`RowFlattener`** turns the AST into a flat list of `CodeRow`s (statement, header, mid such as
  `} else {`, footer `}`, caret) for a `RecyclerView`. The AST is the only source of truth; rows
  are rebuilt after every edit.
- **Execution:** `Stmt.exec` calls `ctx.checkpoint(this)` (the debugger gate and the highlight
  event) and `ctx.clock.awaitTicks(...)` (simulated time, scaled by x1/x5/x10). Listener events are
  posted to the main thread with a run-generation guard so a stale run cannot touch the UI.
- **The editor is locked while a program runs**, so the AST is never mutated under the interpreter.
- **Methods:** the root `Block` contains run's statements plus `methods()`. Each `MethodDecl` has
  a separate body, return type and extra parameters. The printer supplies `Farmhand farmer` first in
  every helper signature and `farmer` first in every `UserCall`; parameter initializers are only
  typed placeholders. Calls isolate locals and parameters and stop at a depth of 64.
- **OOP:** the root also owns `classes()`. `ClassDecl` has fields, one optional constructor and
  instance methods. `JType.reference(name)` interns named references; built-in `name()` save keys
  stay compatible. `ClassTable` resolves inheritance and members; `ObjectInstance` stores separate
  field slots per declaring class. User methods have exactly their displayed parameters; only
  legacy Program helpers receive an implicit Farmhand argument. User classes print beside Program.
  Creating objects never creates farmhands or Java threads. Snapshots are immutable strings;
  allocation and display bounds keep player loops from exhausting the app's memory.
  Interfaces share the type tabs and declare method signatures. Classes can implement a contract
  and dispatch calls through its type. Named object arrays can hold classes or interfaces.
- **Background jobs:** `farmer.runTogether` launches two Java threads within the farmhand worker.
  Each job gets its own local scope and shares captured references. The job pair is one debugger
  step. Reset cancels the run and interrupts its jobs. Jobs handle shared memory; the farmhand
  performs actions after joining them.
- **Farmhand teams:** `farmer.workTogether` starts one Java thread for each actor at its own
  starting tile. Each lambda receives its own farmhand and shares captured references with
  the other lambda. Each actor has its own highlight and can be selected for Step. `LockStmt`
  prints `lock()` and `try/finally`, uses an interruptible JVM lock in the interpreter,
  and reports wait cycles without leaving locks held after the run.
- **Save compatibility:** `LegacyFarmApi` migrates old implicit `drone` references and native
  `flyTo` calls to `farmer` and `walkTo`. The built-in type retains the old `DRONE` save key.
- **Render poses:** `Farmhand.Pose` publishes action, route and duration together. `FarmMotion`
  interpolates the route using the game clock. Meshes are projected in Java and drawn on Canvas.
- **Reset never joins the worker on the UI thread.** It invalidates the execution context and
  interrupts its thread. Native mutations check cancellation under the world's monitor, so a
  cancelled worker cannot change the farm after reset.
- **Exceptions:** `TryStmt` owns `tryBlock`, ordered `Catch` nodes and optional `finallyBlock()`.
  `ExceptionTypes` exposes only the two farm subclasses and `FarmException`; internal return
  signals and cancellation are never caught by player code. Catch parameters have typed metadata
  and independent scope. Finally follows Java return/error priority, but Reset skips cleanup.
  `FarmWorld` records actual catch/finally entries for lesson goals and clears them on reset.
- **`Validator` runs before every run.** Anything the editor can construct that Java would reject
  must be caught here with javac's wording.
- **Levels teach by restriction.** A `LevelDef` lists `Unlock` keys (what the palette offers) and a
  `Goal` that returns `null` when complete or a player-facing "what is missing" sentence. Goals
  also inspect the program to require the construct being taught.
- **Palette tabs:** `BlockSpec.tab` files a statement block under `Farm`, `loops`, `if / switch`,
  `variables`, `methods`, `exceptions` or `objects`; `pinned` keeps Delete beside the scrollable tabs. Tabs
  appear only with more than five code blocks. Slot choices are a single untabbed row.

## Recipe: adding a language feature

Touch these in order; skipping one usually shows up as a crash on load or a missing block.

1. **AST node** in `lang/ast/expr` or `lang/ast/stmt` (`eval` / `exec`, `childBlocks()` if compound).
   Add a factory to `Ast` if it helps.
2. **`AstWalk`** — make sure the new node's slots and blocks are visited.
3. **`JavaPrinter`** — tokens for the line(s); add an operator precedence if it is an expression.
4. **`RowFlattener`** — only if the statement has extra fixed lines (like `} else {`, `break;`).
5. **`ScopeResolver` / `Validator`** — scoping and type rules, javac-style messages.
6. **`AstJson`** — write and read; keep old saves loadable (format is backward compatible).
7. **`Unlock`** key, then **`PaletteProvider`** — offer it, with a sensible default, in the right tab.
8. **`BlockEditorView.stepOutOrRemove`** — only if it has optional arms that Delete should remove.
9. **`LevelCatalog`** — a level that forces the feature, with a goal message for each way to fail.
10. **Unit tests** — interpreter behaviour, validator messages, print/serialize round trip, and the
    reference solution of the new level completing it.

## Conventions

- Comments explain *why*, in English; Javadoc on public types. Match the existing style.
- No magic numbers: name constants (`THINK_TICKS`, `WATER_PER_USE`, `MAX_UNTABBED`).
- Runtime errors shown to the player use real Java wording
  (`ArithmeticException: / by zero`, `Index 5 out of bounds for length 5`).
- Variable names are auto-assigned from name pools in `PaletteProvider`; Java forbids two locals
  with the same name in one method, so `freeName` always picks an unused one.
- UI strings live in `res/values/strings.xml` and `res/values-uk/strings.xml`. English lesson
  text lives in the level catalog and Ukrainian is bundled under `assets/i18n`, keyed by save ID.
- Art: see [ASSETS.md](ASSETS.md) for the original 3D models, palette and viewport checks.

## Verifying on a device

Unit tests do not cover the editor UI. After editor or rendering changes, install on a phone (or
emulator) and build at least one affected level by tapping. Useful `adb` calls:
`adb shell input tap X Y`, `adb shell input swipe X1 Y1 X2 Y2 900`, `adb exec-out screencap -p > s.png`.
Slow swipes (about 900 ms) avoid a fling swallowing the next tap.

**Currently unverified on a device** (see ROADMAP "Verify first"): palette tabs, Delete of
if/switch arms, the `+ case` / `+ default` buttons, Pause/Resume mid-run, and the methods editor
in levels 12–15 and exception editor in levels 16–18.
Class tabs, member folding, constructor parameters and cross-class debugger navigation in levels
19–22 are also pending a physical device check.

## Known limitations (intentional gaps, not bugs)

- `switch` has no fall-through; every case that can reach its end has a fixed `break;`.
  An arm that always returns omits its otherwise unreachable `break;`.
- Method names are auto-assigned; the palette creates void helpers, int-returning helpers and
  void helpers with one int parameter. Every helper also explicitly receives `Farmhand farmer`.
- OOP supports one constructor and one method per name. Each class can directly implement one
  interface, and contracts contain method signatures. Overloads, interface inheritance, default
  methods, static members and `super.method()` are future extensions. The native Farmhand remains
  a single shared actor.
- Exceptions support the farm hierarchy with single-type catch arms and optional finally;
  no `throw`, multi-catch, try-with-resources or exception inspection methods yet. Deleting an
  empty optional catch/finally arm retains the last required handler when try contains code.
- One-dimensional `int[]` arrays can be allocated and written by index. The 2D scanner still
  supplies the only `int[][]` values, and array literals are not available.
- `ArrayList<Integer>` supports `add`, `get`, `size`, `contains`, `set`, index-based `remove`,
  and for-each. Its sequential streams support integer `map`, boolean `filter`, block-lambda
  `forEach`, bound `list::add`, `count` (which returns `long`), `anyMatch`, and `findFirst`.
  Stream parameters are scoped to their lambdas. Captured locals must be effectively final.
- Strings are the fixed crop names; no concatenation or typed text.
- The scanner returns int codes (0 nothing, 1 young, 2 ripe), not an enum.
- Persistence is JSON files + SharedPreferences, not Room.
- Team levels use two farmhands on a shared grid, each with an editable job in the same program.
