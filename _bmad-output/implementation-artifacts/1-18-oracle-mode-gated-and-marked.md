---
story: 1.18
key: 1-18-oracle-mode-gated-and-marked
title: "Oracle mode, gated and marked"
epic: 1
issue: 31
type: 'feature'
status: 'in-progress'
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
- [ ] `shatterfish/harness/src/main/java/org/shatterfish/harness/observer/OracleView.java` -- a record
  of what the screen hides, built from public game state -- the sidecar ADR-0005 names.
- [ ] `…/observer/OracleObserver.java` -- wraps the fair Observer; returns the Observation re-headed
  with `oracle == true` and the view beside it -- the marked read.
- [ ] `…/driver/HeadlessDriver.java` -- `main` parses `--oracle` through a small `Launch` record and
  takes the oracle branch only then -- the launcher flag.
- [ ] `shatterfish/harness/src/test/java/org/shatterfish/harness/observer/OracleGateTest.java` -- the
  matrix's gate and reads -- FR-11's test.
- [ ] `docs/adr/0006-observer-visibility-rules.md` -- amendment for story 1.18; `docs/fairness.md` --
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
