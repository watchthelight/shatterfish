---
story: 1.18
key: 1-18-oracle-mode-gated-and-marked
title: "Oracle mode, gated and marked"
epic: 1
issue: 31
type: 'feature'
status: 'in-review'
created: '2026-09-16'
updated: '2026-09-16'
review_loop_iteration: 0
baseline_commit: '13d4ee25a'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Diagnosing a belief bug needs a view that sees everything, and the fairness claim
needs that view to be unreachable from any fair path. The Observation's header has carried an
`oracle` bit since story 1.6 that nothing sets, and ADR-0005 and ADR-0006 promise an
`OracleObserver` with an `OracleView` sidecar that no Brain-facing type can hold (FR-11).

**Approach:** An `OracleObserver` in `harness` that wraps the fair Observer, returns the ordinary
Observation with the header's oracle bit set, so its hashes differ, and returns beside it an
`OracleView` record, a harness type never in `api`, carrying what the screen hides. The only code
allowed to construct it is the launcher's `--oracle` branch. `OracleGateTest` holds the gate three
ways: by ArchUnit over the harness, by reflection over every type the Observation can reach, and
by the fair Observation carrying none of the sidecar's secrets. The Rig's refusal of oracle Runs
is story 3.3's and is named, not built.

## Boundaries & Constraints

**Always:** `Observer` stays final and unchanged; the oracle is a wrapper beside it, never a
switch inside it. `OracleView` lives in `org.shatterfish.harness.observer` and is referenced only
by `OracleObserver` and the launcher. Everything the view reads is a public game field or method,
so no reflection into upstream and no hook. Every claim cites `path:line` at `v4.0.0`. ADR-0006
and the fairness page change in this pull request.

**Ask First:** Any field added to `api`, any change to the header's meaning, any global flag.

**Never:** No oracle switch in `RunLoop`, `RandomAgent` or the executor: a measured Run has no
path to the oracle at all in E1, and the Rig's refusal (story 3.3) sits on top of that, not in
place of it. No oracle data in a log the Brain could read. No behaviour change to a fair Run.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| The oracle read | `OracleObserver.observe()` at a wait | `Observation` equal to the fair read but for `header.oracle == true`; `hash()` differs; the Actions equal | Outside a wait, the fair Observer's own refusal |
| The sidecar | The same call | `OracleView`: the seed, every unknown appearance with its true name, every mob with cell, name, health and state whether seen or not, hidden mimics, secret doors and hidden traps by cell | N/A |
| The fair read | `new Observer().observe()` at the same wait | Its bytes and JSON contain none of the sidecar's secrets; `header.oracle == false` | N/A |
| The launcher | `HeadlessDriver.main` with `--oracle` | An oracle read at the first wait, the sidecar printed for a person; without the flag, the fair read | An unknown argument names itself |
| The gate | The harness's main classes | No class but `OracleObserver` and `HeadlessDriver` references `OracleView` or constructs `OracleObserver`; no type reachable from `Observation` or `Decider` lives outside `api`, `java.lang` and `java.util` | The rule's failure names the class |

</frozen-after-approval>

## Code Map

Read at `v4.0.0`. Paths abbreviate `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/`
as `…/`.

- `shatterfish/api/.../HeaderSection.java:27-31` — the `oracle` component, hashed by
  `ObservationCodec.java:215` and rendered by `ObservationJson.java:139`; `Observer.java:229`
  passes `false`.
- `shatterfish/api/.../Decider.java` — the Brain-facing seam: `decide(Observation)` and nothing else.
- `shatterfish/harness/.../observer/Observer.java` — final; `observe()` at `:203`, `header()` at `:219`.
- `shatterfish/harness/.../driver/HeadlessDriver.java:703-720` — `main`: the launcher; takes a seed.
- `shatterfish/harness/.../agent/RunLoop.java:129` — the measured Run's one observe site; stays.
- `shatterfish/brain/src/test/.../BrainBoundaryTest.java:138-178` — the brain reaches `api` and
  the JDK only, never `org.shatterfish.harness..`; the sidecar's home is outside its reach.
- `shatterfish/harness/src/test/.../HarnessReflectionTest.java:55-64` — ArchUnit over harness
  main with `DoNotIncludeTests`; the shape `OracleGateTest` follows.
- `shatterfish/harness/src/test/.../observer/Skeleton.java` — `Serialized.assertAbsent`.
- What the screen hides, all public: `Item.trueName()`, `Potion/Scroll/Ring.getUnknown()` and each
  instance's `name()`; `Level.mobs`, `Mob.pos/HP/state`; `Level.secret[]`, `Level.map[]`;
  `Level.traps` with `Trap.visible`; `Observer.hiddenMimic`; `Dungeon.seed`.
- `docs/adr/0005-observation-schema-and-hashing.md:105`, `docs/adr/0006-observer-visibility-rules.md:84-88`,
  `docs/adr/0012-rig-statistics.md:97-98` — the promises this story keeps, and the refusal it names.

## Tasks & Acceptance

**Execution:**
- [x] `shatterfish/harness/src/main/java/org/shatterfish/harness/observer/OracleView.java` -- a record
  of what the screen hides, built from public game state -- the sidecar ADR-0005 names.
- [x] `…/observer/OracleObserver.java` -- wraps the fair Observer; returns the Observation re-headed
  with `oracle == true` and the view beside it -- the marked read.
- [x] `…/driver/HeadlessDriver.java` -- `main` parses `--oracle` through a small `Launch` record and
  takes the oracle branch only then -- the launcher flag.
- [x] `shatterfish/harness/src/test/java/org/shatterfish/harness/observer/OracleGateTest.java` -- the
  matrix's gate and reads -- FR-11's test.
- [x] `docs/adr/0006-observer-visibility-rules.md` -- amendment for story 1.18; `docs/fairness.md` --
  the oracle row of its table.

**Acceptance Criteria:**
- Given a wait, when the oracle observer reads, then the Observation equals the fair one but for
  the header's oracle bit and the hashes differ — `OracleGateTest.the_oracle_read_is_marked`.
- Given the same wait, when both observers read, then the sidecar carries the true identities and
  the unseen positions and the fair bytes carry none of them — `OracleGateTest.the_sidecar_holds_what_the_screen_hides`.
- Given the harness's main classes, when ArchUnit runs, then only the oracle observer and the
  launcher reach the sidecar, and no type reachable from `Observation` or `Decider` lives outside
  `api` and the JDK — `OracleGateTest.no_fair_path_reaches_the_sidecar`.
- Given `--oracle` on the launcher, when it parses, then the oracle branch is chosen, and not
  otherwise — `OracleGateTest.the_flag_is_the_launchers`.
- Given ADR-0012, when the refusal is looked for, then it is named as story 3.3's in ADR-0006's
  amendment and the story file.

## Spec Change Log

- **Review, 2026-09-16 (patches, no loopback).** The frozen matrix names `HeadlessDriver.main`
  as the launcher; the launcher is `org.shatterfish.harness.Launcher`, since the driver package
  must not depend on the observer package, which depends on it, and `HeadlessDriver.main` is
  removed rather than kept as a second command line. The matrix's meaning, one flag on the
  harness command line and one exempted class, is unchanged. The reviews' other findings were
  patches: the launcher's branch made testable (`Launcher.read`) and tested; the sidecar's
  identities, a mob's fields and a hidden mimic asserted; class names beside the localised
  strings; a hidden trap's armed flag; the sort's tie-break; seed range and duplicate-seed
  refusals; the exit in a finally; the reflective walk through sealed, wildcard and array types,
  failing on an open interface; a by-name rule and fixtures the rules bite on; the oracle read
  shown to leave the game and the generator untouched; citations at the tag for every read.
  KEEP: the wrapper beside a final Observer; the sidecar as a harness type; the three-way gate.

## Design Notes

**Why a wrapper, not a switch.** A flag inside `Observer` is a door in the door; a global any code
can flip is worse. A wrapper beside a final Observer changes nothing on the fair path, and the
gate is then a question about who may construct one class, which ArchUnit can answer.

**Why the sidecar is a harness type.** ADR-0005: oracle data is never a field of `Observation`. A
type in `api` could be held by a Brain; one in `harness` cannot be reached by the brain module at
all (`BrainBoundaryTest`), and `Decider` takes an Observation alone.

**Pre-mortem.** The re-headed Observation must recompute nothing but the bit, or a difference in the
Actions would make the oracle read a different screen; the test holds the Actions equal. The view
reads private-looking things through public fields only, or `HarnessReflectionTest` fails.

## Verification

**Commands:**
- `./gradlew :harness:test -Pshatterfish.mobile=off --tests "org.shatterfish.harness.observer.OracleGateTest"` -- expected: green.
- `./gradlew build -Pshatterfish.mobile=off` -- expected: green; `mkdocs build --strict` green.

## Dev notes

Implemented on `story/1-18-oracle-mode-gated-and-marked` from `13d4ee25a`. Three main classes in
`harness` (`OracleView`, `OracleObserver`, `Launcher`) and one suite; the fair Observer, the
driver, the loop and `api` untouched; no hook spent. The launcher is its own class at the harness
root rather than a branch of `HeadlessDriver.main`, so the driver package gains no dependency on
the observer package. The fairness review follows below.

## Acceptance criteria and how each was met

- **The oracle read equals the fair read but for the header's oracle bit, and the hashes
  differ.** `OracleGateTest.the_oracle_read_is_marked`: every other section's hash equal, the
  Actions equal and self-implied.
- **The sidecar carries the true identities and unseen positions and the fair bytes carry none.**
  `OracleGateTest.the_sidecar_holds_what_the_screen_hides`, with a secret door and a hidden trap
  planted in view.
- **No fair path reaches the sidecar.** `OracleGateTest.no_fair_path_reaches_the_sidecar`:
  ArchUnit over the harness's main classes and a reflective walk of every type an Observation is
  made of; a Decider takes an Observation alone.
- **The flag is the launcher's.** `OracleGateTest.the_flag_is_the_launchers`.
- **The Rig's refusal is story 3.3's.** Named in ADR-0006's amendment, the fairness page and here.

## What was built

- `OracleView` — the sidecar record: seed, identities, every mob, hidden mimics, secret doors,
  hidden traps; a harness type by design.
- `OracleObserver` — the fair read re-headed with the oracle bit, and the view beside it.
- `Launcher` — the harness command line: a seed and `--oracle`, parsed by `Launch.parse`; the
  only constructor of the oracle.
- `OracleGateTest`; ADR-0006's story 1.18 amendment; the fairness page's oracle section and the
  glossary entry.

## Decisions taken inside the story

- **A wrapper, not a switch.** A flag inside the Observer is a door in the door, and a global any
  code can flip is worse; a wrapper beside a final Observer leaves the fair path untouched and
  turns the gate into a question ArchUnit can answer.
- **The launcher is its own class.** `HeadlessDriver.main` stays a boot smoke; the driver package
  must not depend on the observer package, which depends on it.
- **The Actions are carried, not recomputed.** The marked read keeps the fair read's Actions and
  the test holds them equal to what the marked read implies, so the oracle bit changes no screen.

## Evidence

- `:harness:test` for `OracleGateTest`, `HarnessReflectionTest` and `HarnessPackageAnchorTest`:
  green, 4, 5 and 1 tests.
- The battery, `mutations118.py`, on the committed tree, restored clean after each break:

```
=== M1 the fair read is marked: Observer.header() sets the oracle bit
  -> caught by: OracleGateTest (the_oracle_read_is_marked)
=== M2 the oracle read is not marked: OracleObserver passes the fair header through
  -> caught by: OracleGateTest (the_oracle_read_is_marked)
=== M3 the measured loop reads through the oracle: RunLoop constructs an OracleObserver
  -> caught by: OracleGateTest (no_fair_path_reaches_the_sidecar, the ArchUnit rule)
=== M4 a fair type holds the sidecar: RunOutcome gains a static OracleView field
  -> caught by: OracleGateTest (no_fair_path_reaches_the_sidecar, the ArchUnit rule)
=== M5 the flag is ignored: Launch.parse never sets oracle
  -> caught by: OracleGateTest (the_flag_is_the_launchers)
tree restored and clean
```

  M4's first form added a record component, which broke every constructor call and so tested
  nothing; the battery scored it "did not compile" and the break was rewritten as a static field.

## The fairness review

The `fairness-reviewer` subagent read `git diff main...HEAD` and returned **CHANGES**: no path by
which the sidecar or the marked Observation reaches a Brain, a run log or the measured loop, the
fair Observer and `api` untouched, and six should-fixes, every one taken. The citations the
sidecar's reads lacked are now on `OracleObserver`, `OracleView` and the ADR amendment, at the
tag; the claim that `hiddenTraps` were "the traps the feature layer does not draw" was wrong,
since the layer also does not draw a visible trap under opaque fog, and now reads "the traps
hidden by `Trap.hide()`"; "no reflection into upstream" became "no reflection into a private
upstream member", since the item instances are made through the game's own factory; the
construction gate gained a by-name rule against `forName` and fixtures each rule is shown to bite
on; the two seed-brittle assertions, a hidden mimic in view and the trap's name, are held by the
fair read's rule and by cell; and the frozen matrix's naming of `HeadlessDriver` is logged.

Three more reviews ran under the build workflow. The verification-gap reviewer found the
launcher's branch parsed but never exercised, and the sidecar's identities, mob fields and hidden
mimics never asserted: `Launcher.read` now takes a launch and a stream and the test holds the
branch to the flag and the sidecar's every field to the game. The blind reviewer's patches taken:
the reflective walk through sealed types, class names beside localised strings, a failure rather
than an omission when the factory cannot make an item, the oracle read shown to leave the game
and the generator untouched, the duplicate `HeadlessDriver.main` removed, the architecture
inventory, the gap the hero's unidentified gear leaves, and the scope of the gate across modules
written down. The edge-case reviewer's taken: seed overflow, range and duplicates refused by
name, `Gdx.app.exit()` in a finally, the mob sort's tie-break, the hidden trap's armed flag, and
wildcard, array and type-variable handling in the walk. Rejected: widening the ArchUnit import
to every module, which the harness's test classpath cannot see; handled instead by scoping the
claim and naming the rule each later module carries.

## Deviations

- The spec's launcher task named `HeadlessDriver.main`; the launcher is `org.shatterfish.harness.Launcher`
  instead, for the package reason above, and `HeadlessDriver.main`, which duplicated it line for
  line, is removed. Logged in the Spec Change Log.

## Known limitations, handed forward

- **The sidecar is not exhaustive.** It carries what FR-11 names, identities and unseen positions,
  and the secrets the map hides; the hero's own unidentified gear (a weapon's, armour's, wand's
  or artifact's level, enchantment and curse while unknown), a heap's contents out of view, a
  mob's buffs and the RNG state are not in it. A later consumer (E5's overlay, E9's labels)
  widens it under the same gate.
- **The gate sees the harness module.** The rig and the overlay compile against the harness and
  are empty today; each carries its own construction rule when it arrives (story 3.3, E5).
- **The Run log's `oracle` field** (ADR-0011) and the Rig's refusal (story 3.3) are E3's.
- **The battery script** lives in the session scratchpad, as every story's has; the story
  carries its output, and committing the batteries is `deferred-work.md`'s entry.

## Follow-ups for later stories

- Story 1.19 (#32): thread confinement.
- Story 3.3: the Rig refuses any Run whose header says oracle.
- E5: the overlay's red border and label, driven by the header bit.
