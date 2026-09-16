---
story: 1.19
key: 1-19-thread-confinement
title: "Thread confinement"
epic: 1
issue: 32
type: 'feature'
status: 'in-review'
created: '2026-09-16'
updated: '2026-09-16'
review_loop_iteration: 0
baseline_commit: '7d9f60428'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** ADR-0013 gives every thread a role and says the Observer and the executor run on the
UI-role thread only, and that Shatterfish code never takes a monitor on a game object. Nothing
asserts either today: a call from the wrong thread would read torn state or deadlock the way the
game once did, quietly (FR-12).

**Approach:** A `UiRole` holder in the driver package that the driver claims for the thread that
starts a Run and releases on close; the Observer and the executor ask it on entry and fail loudly,
naming the role, the thread that owns it and the thread that called. `ThreadConfinementTest`
calls each port from a foreign thread and holds the failure. A bytecode rule over every
Shatterfish class, written as an ArchUnit condition, holds that no code takes a monitor on a game
type, with the two places that must, the stepper's fence and the headless scene's own methods,
exempted by name and by reason. The Brain holding no game object is already `BrainBoundaryTest`'s.

## Boundaries & Constraints

**Always:** The role is a thread identity the driver claims, never a thread name or a guess. The
fair path's behaviour on the right thread is unchanged: every existing test passes. Exemptions to
the monitor rule are named classes with the reason written where the rule lives, as
`HarnessReflectionTest` confines reflection. Every claim about the game cites `path:line` at
`v4.0.0`. ADR-0013 and the fairness page change in this pull request.

**Ask First:** Any monitor added to Shatterfish code; any change to the stepper's fence; any hook.

**Never:** No global lock as the confinement; no `Thread.getName()` comparison; no thread check
inside the game's own actor thread path. The executor throws on the wrong thread rather than
returning a refusal: a refusal is for a Brain's choice, a wrong thread is a programming error.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| The right thread | `Observer.observe()`, `ActionExecutor.execute` on the thread that started the Run | As today | N/A |
| A foreign thread | The same calls from a new thread at a wait | `IllegalStateException` naming the UI-role thread, the owning thread and the calling thread, before any game state is read | N/A |
| No Run | A call with no driver live | The Observer's own "no Run is in progress" | N/A |
| The Overlay's claim | `UiRole.claim(thread)` from a later driver | The render thread becomes the role's owner for that Run | A second claim while one is live is refused |
| The monitor rule | Every Shatterfish class on the classpath | No `monitorenter` on a value of a game type and no synchronized method on a class that is a game type, outside `SceneStepper` and `HeadlessScene` | The failure names the class, the method and the type |
| The rule bites | A fixture synchronizing on a game object; one extending a game type with a synchronized method; one synchronizing on its own field | The first two violate, the third passes | N/A |

</frozen-after-approval>

## Code Map

- `docs/adr/0013-overlay-threading-model.md:73-133` — the roles, the per-wait sequence and the
  deadlock rule this story enforces.
- `shatterfish/harness/.../driver/HeadlessDriver.java:163-169` — the constructor, where `live` is
  set: the claim goes here; `close()` releases.
- `shatterfish/harness/.../observer/Observer.java:930-948` — `atInputWait()`, every port's entry;
  the role check goes first.
- `shatterfish/harness/.../executor/ActionExecutor.java:71-80` — `execute`, whose first act is the
  wait check; the role check goes before it.
- `shatterfish/harness/.../scene/SceneStepper.java:178`, `:336`, `:354`, `:472-478` — the fence's
  monitors on the actor thread and the sprites, story 1.3's design, held by `FenceInvariantTest`.
- `shatterfish/harness/.../scene/HeadlessScene.java:60`, `:72` — `update()` and `openWindow()`
  synchronized on the scene, which is the game's own rule for its scene:
  `…/scenes/GameScene.java:867` (`update` synchronized), `:967` (`erase`), `:1087` (`addMobSprite`),
  `:1098` (`synchronized (scene)`).
- `shatterfish/harness/.../boot/HeadlessBoot.java:231`, `…/observer/GameLogListener.java:97` —
  monitors on Shatterfish objects, which the rule allows.
- `shatterfish/java-module.gradle:20-25` — the shared test dependencies; ArchUnit 1.5.0 shades
  ASM's core but not its analysis package, and the Gradle cache holds `org.ow2.asm` 9.9 with it.
- `shatterfish/harness/src/test/.../HarnessReflectionTest.java:55-77`, `:157-200` — the shape:
  a rule with named exemptions and fixtures it is shown to bite on.
- `shatterfish/brain/src/test/.../BrainBoundaryTest.java:138-178` — the brain holds no game object.

## Tasks & Acceptance

**Execution:**
- [x] `shatterfish/harness/src/main/java/org/shatterfish/harness/driver/UiRole.java` -- the role's
  owner, claimed and released by a driver, asked by a port -- the identity the assertion uses.
- [x] `…/driver/HeadlessDriver.java` -- claim in the constructor, release in `close()` -- the
  headless driver thread is the UI-role thread (ADR-0013).
- [x] `…/observer/Observer.java`, `…/executor/ActionExecutor.java` -- `UiRole.require` on entry --
  the assertion the story is for.
- [x] `shatterfish/java-module.gradle` -- `org.ow2.asm:asm`, `asm-tree`, `asm-analysis` 9.9 as test
  dependencies -- the reader the monitor rule needs.
- [x] `shatterfish/harness/src/test/java/org/shatterfish/harness/driver/ThreadConfinementTest.java` --
  the foreign-thread calls and the claim's rules -- FR-12's test.
- [x] `shatterfish/harness/src/test/java/org/shatterfish/harness/MonitorConfinementTest.java` -- the
  ArchUnit condition over every Shatterfish class, the two exemptions, and the fixtures -- the
  deadlock rule as a test.
- [x] `docs/adr/0013-overlay-threading-model.md` -- amendment for story 1.19; `docs/fairness.md` --
  the thread-confinement row; `docs/architecture.md` -- `UiRole` in the harness inventory.

**Acceptance Criteria:**
- Given a Run at a wait, when the Observer or the executor is called from a foreign thread, then
  it fails naming the UI-role thread, its owner and the caller, before any state is read —
  `ThreadConfinementTest.a_foreign_thread_is_refused_by_name`.
- Given the same wait, when they are called on the driver thread, then they work as before —
  `ThreadConfinementTest.the_driver_thread_is_the_role`.
- Given every Shatterfish class, when the monitor rule runs, then none outside the two named
  exemptions takes a monitor on a game type, and each exemption is shown to need its exemption —
  `MonitorConfinementTest.no_shatterfish_code_takes_a_monitor_on_a_game_type`, `the_exemptions_are_load_bearing`.
- Given the fixtures, when the rule evaluates them, then the game-object ones violate and the own-field one passes — `MonitorConfinementTest.the_rule_bites`.
- Given `BrainBoundaryTest`, the Brain holds no game object — named, already green.

## Spec Change Log

- **Review, 2026-09-16 (patches, no loopback).** The matrix's "No Run" row names "the
  Observer's own 'no Run is in progress'"; with the role asked first, the refusal is the role's
  and carries the same words plus the role's name, which is what makes it the ordering evidence
  the test now uses. The matrix's "second claim refused" is refined: a claim by the owner is
  idempotent, by another live thread refused, by a dead thread refused. The reviews' other
  findings were patches: the claim moved to the top of `start()` with an unwind on failure, the
  release last in `close()` and refused to a stranger, thread ids in every message, the oracle
  observer and the driver's stepping asking too, ASM confined to the harness module with debug
  info pinned, a static synchronized method and an interface-typed variable not counted, an
  unloadable type reported, the violation's message asserted, the exemptions narrowed to the two
  classes, a hang guard on the foreign calls, and a second thread's Run shown to take the role.
  KEEP: the claimed identity; the throw rather than the refusal; the two named exemptions.

## Design Notes

**Why an owner thread, not a name.** The Overlay's UI-role thread is the render thread and the
headless one is the driver thread; a name would tie the ports to one driver. A claimed identity is
what the driver knows and what a foreign call cannot fake.

**Why the scene's methods are exempt.** `HeadlessScene` is the scene; `GameScene.update` is
synchronized upstream and the override keeps that lock, and `openWindow` reads the member list
under the lock the group's own writers take (`Group.java:49`, `:99`, `:124`, `:201`). That is the game's rule for its scene, not a
monitor Shatterfish invented; ADR-0013's amendment says so.

**Pre-mortem.** A test that starts a Run on one thread and reads on another would now fail by
design; the suite is checked for that before the assertion lands. The analysis must find the
monitor's operand through javac's `dup; astore; monitorenter` shape and through fields, locals,
parameters and call results, or it reports a violation with an unknown type, never silence.

## Verification

**Commands:**
- `./gradlew :harness:test -Pshatterfish.mobile=off` -- expected: green, the two new suites included.
- `./gradlew build -Pshatterfish.mobile=off` -- expected: green; `mkdocs build --strict` green.

## Dev notes

Implemented on `story/1-19-thread-confinement` from `7d9f60428`. One main class (`UiRole`), two
one-line entries in the ports, the claim and release in the driver, two suites, and three test
dependencies (ASM 9.9, from the Gradle cache) shared by every module. No hook spent, no upstream
file touched. The fairness review follows below.

## Acceptance criteria and how each was met

- **A foreign thread is refused by name before any state is read.**
  `ThreadConfinementTest.a_foreign_thread_is_refused_by_name`: the Observer and the executor
  from two named foreign threads, the failure naming the port, the role, the owner and the caller,
  and the wait reading the same afterwards.
- **The driver thread is the role.** `ThreadConfinementTest.the_driver_thread_is_the_role`:
  claimed at start, not claimable by another thread, released at close, and the ports work on it.
- **No monitor on a game type, as an ArchUnit rule.**
  `MonitorConfinementTest.no_shatterfish_code_takes_a_monitor_on_a_game_type`, over every
  Shatterfish class on the classpath, with `SceneStepper` and `HeadlessScene` exempt by name;
  `the_exemptions_are_load_bearing` holds that each would violate the bare rule.
- **The rule bites.** `MonitorConfinementTest.the_rule_bites`: five fixtures violate, three pass,
  and the static-type limit is shown.
- **The Brain holds no game object.** `BrainBoundaryTest`, named in ADR-0013's amendment.

## What was built

- `UiRole` — the role's owner: `claim`, `release`, `owner`, `require`.
- `HeadlessDriver` — claims in the constructor, releases in `close()`.
- `Observer.atInputWait()` and `ActionExecutor.execute()` — `UiRole.require` first.
- `ThreadConfinementTest`, `MonitorConfinementTest`; ASM as a shared test dependency.
- ADR-0013's story 1.19 amendment; the fairness page's thread-confinement row; the architecture
  inventory.

## What the story found

- **The rule's first run caught the log listener.** `GameLogListener` implements the game's
  `Signal.Listener` and synchronizes on itself; the first type test counted a game interface as a
  game type. A Shatterfish object behind a game interface is still Shatterfish's own, and a monitor
  on it is one the game never takes; the test now follows superclasses only, and a fixture shows
  the listener's shape passing.
- **The headless scene's monitors are the game's own rule.** `GameScene.update` is synchronized
  upstream and the override must be; `openWindow` reads under the lock `erase` writes under. The
  deadlock rule as ADR-0013 wrote it predates the fence of story 1.3 and the scene of the same
  story; the amendment reconciles them with two named, load-bearing exemptions.
- **The no-Run message had to be the Observer's, and became the ordering evidence.** The role
  check runs before the Observer's own "no Run is in progress", so its message says the same words
  and names the role; the test holds that the refusal naming the role is the one that fires, and
  that the executor throws where it would have refused after reading the hero.
- **The claim's first home leaked.** In the driver's constructor, a refused claim left the scene,
  the actor thread and the hook registered; it now comes first in `start()` and unwinds.

## Decisions taken inside the story

- **A claimed thread identity, not a name.** Headless the role is the driver thread; in the
  Overlay it will be the render thread. The driver knows which thread started the Run; a name
  would tie the ports to one driver and could be faked.
- **Throw, do not refuse, on the wrong thread.** The executor's refusals are for a Brain's
  choices; a wrong thread is a bug, and a bug should not read as a rejected Action.
- **Static types, reported loud.** The operand's type is what produced it; a producer the
  analysis cannot type is a violation rather than a pass, and the limit, a game object behind a
  variable declared `Object`, is named beside the rule with the fixture that shows it.
- **ASM as a test dependency.** ArchUnit shades ASM's core but not its analysis package; the
  cache holds 9.9, and three test-scoped lines in the shared module script bring it in.

## Evidence

- `:harness:test`, the whole suite with the assertion in place: green, 197 tests in 43 suites,
  no failures; `ThreadConfinementTest` and `MonitorConfinementTest` green, 2 and 3 tests.
- `mkdocs build --strict` green.
- The battery, `mutations119.py`, on the committed tree, restored clean after each break:

```
=== M1 the Observer does not ask: UiRole.require dropped from atInputWait
  -> caught by: ThreadConfinementTest (both tests)
=== M2 the executor does not ask: UiRole.require dropped from execute
  -> caught by: ThreadConfinementTest (the render-thread call accepted)
=== M3 the driver does not claim: no owner is ever set
  -> caught by: ThreadConfinementTest (both tests)
=== M4 identity by name: a thread called the same passes
  -> caught by: ThreadConfinementTest (the impostor accepted)
=== M5 the driver never releases: the role outlives the Run
  -> caught by: ThreadConfinementTest (no Run, no role)
=== M6 a monitor on a game object in the Observer
  -> caught by: MonitorConfinementTest (the ArchUnit rule)
tree restored and clean
```

  The battery script lives in the session scratchpad, as every story's has; committing the
  batteries is `deferred-work.md`'s entry.

## Deviations

- None from the spec's tasks.

## Known limitations, handed forward

- **The monitor rule sees static types.** A game object held behind `Object` passes; the fixture
  `HidesAGameObjectBehindObject` shows it. Shatterfish code has no such variable today.
- **The rule's "cannot type" branches have no fixture.** javac always emits a typed producer for
  a source-level monitor, so an untyped operand cannot be written in Java; those branches are
  read, not run.
- **The rule sees the harness's classpath**, `api` and `harness` main; `codex` (E2) and the
  overlay (E5) carry the same rule when they arrive, since each depends on the game.
- **The Overlay's claim** of the render thread is E5's, through the same `UiRole.claim`.

## Follow-ups for later stories

- Story 1.20 (#33): snapshot, restore and the reserved interfaces.
- E5: the embedded driver claims the render thread; the same two suites apply to the overlay.
