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
| `Hooks.java` is added under `core` with one nullable listener field per hook point | **Met, with one point rather than ten.** The registry, its convention and its `clear()` are landed; the only listener point declared is `inputWait`, the one ADR-0015 has already decided. The other nine ledger rows are not all listener rows, and the ones that are belong to stories that have not run. See *Decisions taken inside the story* |
| `HooksLedgerTest` greps the tree for hook markers and fails if the set of ids differs from the rows in `docs/UPSTREAM.md`, or if the row count exceeds ten | **Met.** Four checks: ids in the tree equal rows in the document, rows are at most ten, id 2 appears in `Hooks.java` and nowhere else, and no upstream file differs from the pinned tag without carrying a marker. It found a real defect on its first run: hook row 1 in `settings.gradle` was marked `//shatterfish hook #1`, which no parser matches |
| `HooksVanillaTest` boots with no listener registered and asserts the vanilla branch runs at every site | **Met in the half that is reachable, and the other half is proved a different way.** The guard-firing branch is exercised at runtime with every static null. The vanilla branch cannot be reached from a test at all, for a reason recorded below, so it is proved against the pinned upstream tag by the rule that *a hook wraps vanilla code and never deletes it*. Story 1.3 owns the runtime half |
| `./gradlew :desktop:run` still launches the unmodified game | **Not run, and the criterion is wrong.** That task has never worked (story 1.1 finding); the working task is `:desktop:debug`, and story 1.1 ran it against these same three guards. This story adds no site and no call: `Hooks.java` is a new file with no caller, so it cannot change vanilla behaviour, and `:desktop:jar` compiles it |
| `BrainBoundaryTest` asserts that `brain` depends on no game package, no other Shatterfish module but `api`, and none of `java.io`, `java.nio.file`, `java.net` or `java.lang.reflect` | **Met**, five rules, including `java.lang.invoke.MethodHandles` alongside reflection and a positive rule that `brain` sees only `api` and the JDK |
| `ApiBoundaryTest` asserts `api` depends only on the JDK | **Met**, plus an explicit ban on game packages so the failure says which rule broke |
| The ArchUnit bump to 1.5.0 lands here with every boundary rule green | **Met.** 1.3.0 to 1.5.0, seven boundary rules green |

## What was built

- `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/shatterfish/Hooks.java`: the registry,
  hook row 2. The only Shatterfish-authored source file outside `shatterfish/`.
- `shatterfish/harness/src/test/java/org/shatterfish/harness/hooks/`: `Ledger` (reads markers, rows
  and the diff against the pinned tag), `HooksLedgerTest`, `HooksVanillaTest`.
- `shatterfish/brain/src/test/java/org/shatterfish/brain/BrainBoundaryTest.java`, replacing
  `BrainImportsNoGameCodeTest`.
- `shatterfish/api/src/test/java/org/shatterfish/api/ApiBoundaryTest.java`, replacing
  `ApiDependsOnNothingTest`.
- `settings.gradle`: hook row 1's marker written in the form the parser reads.
- `shatterfish/java-module.gradle`: ArchUnit 1.5.0.
- `.github/workflows/build.yml`: `fetch-depth: 0`, because two of the new checks compare the tree
  against the pinned tag and a shallow checkout does not have it.
- `docs/UPSTREAM.md`: row 2; row 1's and row 5's guard columns rewritten to say what is now tested
  rather than argued; the documentation exceptions named as exceptions the test knows about.

## Decisions taken inside the story

**One listener point, not ten.** The criterion says "one nullable listener field per hook point",
and the obvious reading is to declare all ten now. Rejected: four of the ten rows have no listener
at all (a build file, read-only accessors, the identity-order edits, the draw routing), and the
remaining five belong to stories that have not run, so their signatures would be guesses. ADR-0016's
pre-mortem is precisely about a ledger row written before the work that fills it. What landed is the
mechanism plus `inputWait`, whose site and meaning ADR-0015 already decided, so the registry has a
real contract to test rather than being an empty shell. Adding a point later edits this file only
and consumes no ledger row; the class comment says so, and `clear_nulls_every_declared_point`
enforces the one rule that would otherwise be forgotten.

**Why the vanilla branch is proved against the tag rather than at runtime.** The intended shape was a
test that installs a real `GameScene` and `CellSelector`, calls each site, and watches the vanilla
statement execute. It cannot be written. All three of row 5's statics are assigned only inside
`GameScene.create()` (`core/.../scenes/GameScene.java:178`, `:368`), `emoicons` likewise (`:305`),
and `CellSelector` dereferences its `DungeonTilemap` in its own constructor
(`core/.../scenes/CellSelector.java:55-56`), so there is no way to install one without building the
scene ADR-0015 deliberately does not build. Three alternatives were weighed: allocate the objects
without their constructors (rejected: needs `Unsafe`, and a `CellSelector` in an impossible state
proves nothing about the real one); pull story 1.1's spike boot into this story to construct a real
tilemap (rejected: the spike is throwaway and story 1.3 owns that machinery, so this would build it
twice); or prove the property against the source. The third is not a weaker substitute — it is
stronger in the dimension that matters. A runtime test covers the sites that exist; the source rule
covers every hook this repository will ever have, including the ones not yet written, and it catches
exactly the failure ADR-0008's own pre-mortem named: a vanilla statement lost because an `else`
branch was mis-copied.

**A hook wraps vanilla code; it never deletes it.** Every line an upstream file loses relative to the
pinned tag must still appear inside some line the file gained. Comments and blank lines are exempt.
One hook breaks the rule honestly: row 1 moves the two mobile `include` lines out of
`settings.gradle` and into `shatterfish/settings.gradle`, which restores them under
`-Pshatterfish.mobile=on`. That is a named relocation in the test with the target file checked, not a
silent exception, and any new entry has to argue for itself in its `docs/UPSTREAM.md` row.

**The registry is not a hiding place.** ADR-0008 anticipated the counting test being gamed by moving
a hook into `Hooks.java`, where many sites would become one marker. The test asserts that
`Hooks.java` carries id 2 and nothing else, and that id 2 marks no site anywhere else.

**Directory names are matched at the root, not at any depth.** The first version of the marker scan
skipped any directory named `shatterfish`, which skipped
`core/.../shatteredpixeldungeon/shatterfish/Hooks.java` — the one file the ledger most needs to see.
The test caught it: the registry was invisible and its row looked orphaned.

## Evidence

`./gradlew build -Pshatterfish.mobile=off`: green, all 17 tests (`api` 2, `brain` 5, `harness` 10).

A test that cannot fail is not evidence, so each new check was run against a deliberate break. Every
mutation was applied to a clean tree, run, and reverted.

| Mutation | Failed |
|---|---|
| An upstream file gains `// shatterfish-hook:9`, with no row for it | `HooksLedgerTest > every hook id in the tree has a row, and every row has a site` |
| An upstream file is edited with no marker at all | `HooksLedgerTest > no upstream file is modified without a hook row` |
| A guard replaces the vanilla statement instead of wrapping it (`if (cellSelector != null) return;`) | `HooksVanillaTest > a hook wraps vanilla code; it never deletes it` |
| The `selectCell` guard is deleted, as an upstream merge could silently do | `HooksVanillaTest > row 5: the guarded sites do nothing when no scene exists`, and `HeadlessTurnSpikeTest` |
| A listener point is added to `Hooks` without a line in `clear()` | `HooksVanillaTest > clear() nulls every point, including ones added later` |

The fourth is worth noting: story 1.1's spike fails too, which is the pair working as intended — the
unit check says which guard, the end-to-end check says that a turn no longer resolves.

Rig numbers: not applicable, no Brain exists until E4.

## Deviations

- The criterion naming `./gradlew :desktop:run` is not met and is not met-able; see the table. Nothing
  in this story changes a hook site, and story 1.1 ran `:desktop:debug` against these same guards.
- One listener point rather than ten, argued above.
- The two existing boundary tests were renamed rather than extended in place, to the names the
  criteria use.

## Known limitations, handed forward

- **The vanilla branch of row 5 is still not exercised at runtime.** Story 1.3 owns it: once a
  harness-owned scene makes the three statics non-null, `HooksVanillaTest` gains the other half.
- **`HooksVanillaTest` and `HooksLedgerTest` require git and the pinned tag.** They fail loudly rather
  than skipping, which is deliberate: a check that quietly skips is a check that quietly rots. CI now
  checks out with full history. Anyone building from a source archive rather than a clone will see
  these two tests fail with a message saying why.
- **The marker scan reads `.java`, `.gradle`, `.properties` and `.xml`.** A hook in any other kind of
  file would be invisible to the id comparison, though *"no upstream file is modified without a hook
  row"* would still catch the edit. Widen the suffix list when a row needs it.
- **`brain` has one trivial class**, so its five rules currently pass over almost nothing. They are
  a gate on what comes later, not a measurement of what is there now.

## Follow-ups for later stories

- Story 1.3 lands the harness-owned scene and the runtime half of row 5's vanilla equivalence, and
  should delete nothing from these tests when it does.
- Story 1.5 lands the `Hooks.inputWait` call site inside `Hero.act()`, which is the first real use of
  the registry and stays inside row 5.
- The `epics.md` criterion for this story says `./gradlew :desktop:run`; later stories that quote it
  should read `:desktop:debug`.
