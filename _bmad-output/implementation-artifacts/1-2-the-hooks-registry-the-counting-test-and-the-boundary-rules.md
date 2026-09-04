---
story: 1.2
key: 1-2-the-hooks-registry-the-counting-test-and-the-boundary-rules
title: The Hooks registry, the counting test, and the boundary rules
epic: 1
issue: 15
status: done
created: '2026-09-04'
updated: '2026-09-04'
---

# Story 1.2: The Hooks registry, the counting test, and the boundary rules

As the engineer,
I want one registry class for every upstream edit and the full set of boundary rules,
So that neither an unexplained hook nor a forbidden import can enter the tree.

## Acceptance criteria and how each was met

| Criterion | Outcome |
|---|---|
| `Hooks.java` is added under `core` with one nullable listener field per hook point | **Met, with one point rather than ten.** The registry, its convention and its `clear()` are landed; the only listener point declared is `inputWait`, the one ADR-0015 has already decided. The other nine ledger rows are not all listener rows, and the ones that are belong to stories that have not run |
| `HooksLedgerTest` greps the tree for hook markers and fails if the set of ids differs from the rows in `docs/UPSTREAM.md`, or if the row count exceeds ten | **Met, and then some.** Seven checks: ids in the tree equal rows in the document; the machine-readable site index equals the markers file by file; nothing looks like a marker without being one; at most ten rows with no id used twice; id 2 confined to the registry; every upstream file changed, added, deleted or renamed relative to the pinned tag carries a marker; and every top-level directory in the pinned tag is classified as upstream's or ours. It found a real defect on its first run: hook row 1 in `settings.gradle` was marked `//shatterfish hook #1`, which no parser matches |
| `HooksVanillaTest` boots with no listener registered and asserts the vanilla branch runs at every site | **Met for the site where the vanilla branch is reachable, which is one of three.** Both branches of `add(EmoIcon)` are exercised at runtime. The guard branch of all three is exercised. The vanilla branch of the two `cellSelector` sites needs a `CellSelector`, which needs a level and a texture; story 1.3 owns them. Beyond the runtime checks, the property is held against the pinned tag |
| `./gradlew :desktop:run` still launches the unmodified game | **Not run, and the criterion is wrong.** That task has never worked (story 1.1 finding); the working task is `:desktop:debug`, and story 1.1 ran it against these same three guards. This story adds no site and no call: `Hooks.java` is a new file with no caller, so it cannot change vanilla behaviour, and `:desktop:jar` compiles it |
| `BrainBoundaryTest` asserts that `brain` depends on no game package, no other Shatterfish module but `api`, and none of `java.io`, `java.nio.file`, `java.net` or `java.lang.reflect` | **Met, and the criterion as written is not sufficient.** See *The rules that did not bind* |
| `ApiBoundaryTest` asserts `api` depends only on the JDK | **Met**, plus an explicit ban on game packages so the failure says which rule broke |
| The ArchUnit bump to 1.5.0 lands here with every boundary rule green | **Met.** 1.3.0 to 1.5.0 |

## What was built

- `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/shatterfish/Hooks.java`: the registry,
  hook row 2. The only Shatterfish-authored source file outside `shatterfish/`.
- `shatterfish/harness/src/test/java/org/shatterfish/harness/hooks/`: `Ledger` (markers, ledger rows,
  the site index, and the difference from the pinned tag), `HooksLedgerTest`, `HooksVanillaTest`.
- `shatterfish/brain/src/test/java/org/shatterfish/brain/`: `BrainBoundaryTest` replacing
  `BrainImportsNoGameCodeTest`, and `BrainBoundaryRulesBiteTest`, which checks each rule against a
  class that breaks it.
- `shatterfish/api/src/test/java/org/shatterfish/api/ApiBoundaryTest.java`, replacing
  `ApiDependsOnNothingTest`.
- `settings.gradle`: hook row 1's marker written in the form the parser reads.
- `docs/UPSTREAM.md`: row 2; a machine-readable site index; a table of what each check catches; row
  1's and row 5's guard columns rewritten to say what is tested rather than argued.
- `shatterfish/java-module.gradle`: ArchUnit 1.5.0.
- `shatterfish/harness/build.gradle`: `docs/UPSTREAM.md` and the upstream tree declared as inputs of
  `:harness:test`.
- `.github/workflows/build.yml`: `fetch-depth: 0`, because the checks compare the tree against the
  pinned tag and a shallow checkout does not have it.

## The rules that did not bind

The first draft of this story passed its own tests and was wrong. An adversarial fairness review
demonstrated, by writing the class and running the build, that this compiles inside `brain` with all
five boundary rules green:

```java
Class<?> dungeon = Class.forName("com.shatteredpixel.shatteredpixeldungeon.Dungeon");
MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(dungeon, MethodHandles.lookup());
MethodHandle getter = lookup.findStaticGetter(dungeon, "level", Object.class);
return getter.invoke();
```

That is the whole dungeon, hidden state included, from inside the module whose entire purpose is not
to have it. Three separate reasons it passed, each worth remembering:

1. **A package rule sees dependencies, and a string is not a dependency.** `Class.forName` names the
   game in a `String` constant, so `brain_never_depends_on_game_code` has nothing to look at. The
   brain shares a JVM with the game by non-negotiable #4, so at Overlay and rig runtime the game's
   classes are on the same classpath whatever `brain` compiles against. The Gradle resolution check
   in `brain/build.gradle` constrains `brain`'s own classpath, not the application's.
2. **The reflection ban named a class where a package was needed.** It banned
   `java.lang.invoke.MethodHandles..`, which matches nothing at all: `MethodHandles` reports package
   `java.lang.invoke`, and `resideInAnyPackage` matches package names. The exact API the exploit
   turns on was nominally banned and actually unbanned, and the story file published the opposite.
3. **The positive rule allows all of `java..`**, which is where `Class`, `ClassLoader` and
   `java.lang.invoke` live.

The same review found the rest of the surface open: `System.getenv` and `System.getProperty` (a
one-line channel from the harness in the same process), `ServiceLoader` (the harness registers a
provider of an `api` interface and hands the brain an oracle-backed object, without the brain ever
naming a game type), `ProcessBuilder`, `Scanner` over standard input, `Instant.now()` and an
unseeded `Random`.

The rules now ban `java.lang.invoke`, `sun` and `jdk` as packages, and `ClassLoader`, `Module`,
`ModuleLayer`, `System`, `Runtime`, `ProcessBuilder`, `Process`, `ServiceLoader`, `Scanner` and
`Class.forName` by name. Two further bans are about reproducibility rather than parity: `java.time`,
and generators the brain seeds itself (`Random`, `SplittableRandom`, `ThreadLocalRandom`,
`Math.random`). A Run is (tag, seed, action list) and nothing else; randomness the brain legitimately
needs arrives through `api`, seeded from the Run. The exploit above was re-run against the new rules
and is rejected by two of them.

**`BrainBoundaryRulesBiteTest` is the answer to why this was not caught.** `brain` holds one trivial
class, so every rule passed over almost nothing and none of them had ever rejected anything. Each
rule is now checked against a class that breaks it, and — as importantly — ordinary Java (lambdas,
string concatenation, streams, records, enums, `getClass`) is checked to pass every rule, because a
ban wide enough to catch ordinary code is a ban that gets removed the first time it is inconvenient.
Two rules cannot be exercised from inside `brain`, because game code and the other Shatterfish
modules are absent from its classpath; that absence is the guarantee, and putting either there to
test the test would be the hole itself.

## Decisions taken inside the story

**One listener point, not ten.** The criterion says "one nullable listener field per hook point", and
the obvious reading is to declare all ten now. Rejected: four of the ten rows have no listener at all
(a build file, read-only accessors, the identity-order edits, the draw routing), and the remaining
five belong to stories that have not run, so their signatures would be guesses — which is exactly
what ADR-0016's pre-mortem is about. What landed is the mechanism plus `inputWait`, whose site and
meaning ADR-0015 already decided, so the registry has a real contract to test rather than being an
empty shell. Adding a point later edits this file only and consumes no ledger row.

**The site index.** Comparing sets of ids does not catch a fourth site added to `GameScene` under id
5, for an unrelated reason, with no change to the ledger — and `GameScene` is precisely where an
Observer-adjacent leak would be added. The prose table abbreviates paths for a reader, so
`docs/UPSTREAM.md` now also carries the same information for a machine: `<id> <markers> <path>`, one
line per row per file, checked for equality against the tree. A mistyped marker is caught separately,
because a marker that does not parse is a comment that reads like a declaration.

**Every status, not only modifications.** The first draft read only `M` from `git diff --name-status`,
so deleting an upstream file, renaming one, or adding a new class inside `core` was invisible to every
check — including to the rule whose message claimed to catch it. Untracked files were invisible too,
which is the ordinary state of a new file mid-story. All four are now failures.

**An allowlist of upstream roots, with a check on the allowlist.** Classifying paths by listing *our*
directories broke the moment a tool added one (`.bmad-loop`), turning an ordinary addition into a
build failure. The rule now names upstream's code modules, and a separate check fails when the pinned
tag has a top-level directory this project has not classified — so the allowlist cannot quietly go
stale across an upgrade.

**Why the vanilla branch is proved two ways, and what was wrong with the first answer.** The first
draft published that the vanilla branch "cannot be reached from a test at all", and used that as the
sole justification for not writing one. It is false: `new GameScene()` constructs headlessly, and
`emoicons` and `scene` are reachable by the same reflection the tests already use, so
`add(EmoIcon)`'s vanilla branch is a short test that now exists. The other two sites really do need a
`CellSelector`, whose constructor dereferences a `DungeonTilemap` (`CellSelector.java:55-56`) and
whose only concrete subclass reads `Dungeon.level` and a texture — verified by probe, not assumed.
Reaching them means booting a graphics binding and generating a level, which is story 1.3's driver.
The source-level rule stays alongside the runtime tests rather than instead of them, because it
covers hooks that do not exist yet.

**A hook encloses vanilla code; it does not delete it.** Every line an upstream file loses relative to
the pinned tag must be enclosed by an added line that begins a condition and ends with that line.
Comments and blank lines are exempt. The first draft accepted the removed text appearing *anywhere*
in the added text, which a commented-out statement satisfies. One hook breaks the rule honestly: row
1 moves the two mobile `include` lines into `shatterfish/settings.gradle`, which restores them under
`-Pshatterfish.mobile=on`. That is a named relocation with the target checked, and a new entry needs
its own argument in its `docs/UPSTREAM.md` row.

**Declared task inputs.** `Ledger` reads `docs/UPSTREAM.md` and the upstream tree, neither of which
Gradle could know about, so an unmarked edit left `:harness:test` up to date and the check simply did
not run — the build stayed green because nothing looked. Both are now declared inputs.

**The registry's documented idiom reads the point into a local.** Testing the volatile field and then
reading it again can be interrupted by `clear()` on the thread that ends a Run, and the second read
returns null on the game's actor thread. Volatility makes a registration visible; it does not make
two reads one.

**Directory names are matched at the root, not at any depth.** The first marker scan skipped any
directory named `shatterfish`, which skipped
`core/.../shatteredpixeldungeon/shatterfish/Hooks.java` — the one file the ledger most needs to see.
The test caught it: the registry was invisible and its row looked orphaned.

## Evidence

`./gradlew build -Pshatterfish.mobile=off`: green. `mkdocs build --strict`: clean.

A test that cannot fail is not evidence. Each check was run against a deliberate break, on a clean
tree, **without** `--rerun-tasks`, so that a mutation which leaves the task up to date would show as a
pass rather than hide.

| Mutation | Failed |
|---|---|
| An upstream file gains `// shatterfish-hook:9`, with no row for it | `every hook id in the tree has a row, and every row has a site`, and the site index |
| An upstream file is edited with no marker at all | `no upstream file is changed, added, deleted or renamed without a hook row` |
| A fourth site in `GameScene` under id 5, which already has a row | `the site index names every marker, and only the markers that exist` |
| A marker mistyped as `// shatterfish-hook: 9` | `nothing in the upstream tree looks like a marker without being one` |
| A new, unmarked, untracked class added inside `core/` | `no upstream file is changed, added, deleted or renamed without a hook row` |
| An upstream file deleted | the same check |
| A row deleted from `docs/UPSTREAM.md`, nothing else touched | `every hook id in the tree has a row, and every row has a site` |
| A guard replaces the vanilla statement (`if (cellSelector != null) return;`) | `a hook wraps vanilla code; it never deletes it` |
| A guard comments the vanilla statement out | the same check |
| The `selectCell` guard deleted, as an upgrade merge could do | `row 5: the guarded sites do nothing when no scene exists`, and `HeadlessTurnSpikeTest` |
| A listener point added to `Hooks` without a line in `clear()` | `clear() nulls every point, including ones added later` |
| The reviewer's `Class.forName` plus `MethodHandles` exploit, placed in `brain` | `brain_uses_no_reflection` and `brain_reaches_nothing_by_name_at_runtime` |

Two notes on that table. The guard-deletion mutation fails the unit check *and* story 1.1's spike,
which is the pair working as intended: one says which guard, the other says that a turn no longer
resolves. And an earlier run of this battery was invalid — its restore step used `git checkout`,
which silently reverted two edits that were not yet committed, so every result in it was
contaminated. The table above is from a clean rerun after committing.

Rig numbers: not applicable, no Brain exists until E4.

## Deviations

- The criterion naming `./gradlew :desktop:run` is not met and is not met-able; see the table.
  Nothing in this story changes a hook site, and story 1.1 ran `:desktop:debug` against these guards.
- One listener point rather than ten, argued above.
- The boundary rules go beyond the four packages the criterion names. The criterion's list is not
  sufficient, as the exploit above shows.
- The two existing boundary tests were renamed rather than extended in place, to the names the
  criteria use.

## Known limitations, handed forward

- **The vanilla branch of the two `cellSelector` sites is still not exercised at runtime.** Story 1.3
  owns it: once its driver can build a level and a tilemap, a real `CellSelector` can be installed.
- **`brain_never_depends_on_game_code` and `brain_depends_on_no_other_shatterfish_module` have no
  bite test**, because the classes they forbid are not on the module's classpath. That absence is a
  stronger guarantee than a test, but it does mean these two rules are the only ones taken on trust.
- **The "encloses, does not delete" rule is a syntactic check over one diff format.** It will fire on
  a legitimate hook that changes a condition rather than wrapping a statement — row 9's input gate is
  the known example — which will need a relocation-style exception with a stated reason. It also
  cannot see a vanilla statement moved into a private method that nothing calls.
- **The checks require git and the pinned tag.** They fail loudly rather than skipping, which is
  deliberate: a check that quietly skips is a check that quietly rots. CI checks out with full
  history.
- **`brain` has one trivial class**, so the rules still pass over almost nothing in the module itself.
  `BrainBoundaryRulesBiteTest` is what makes them meaningful before there is a brain to constrain.

## Follow-ups for later stories

- Story 1.3 lands the harness-owned scene and the runtime vanilla branch of the two remaining row 5
  sites, and should delete nothing from these tests when it does.
- Story 1.5 lands the `Hooks.inputWait` call site inside `Hero.act()`, using the local-copy idiom, and
  stays inside row 5. Its site changes the site index.
- Story 1.13's targeted-item work inherits story 1.1's named risk about `selectCell`.
- The `epics.md` criterion for this story says `./gradlew :desktop:run`; later stories that quote it
  should read `:desktop:debug`.
