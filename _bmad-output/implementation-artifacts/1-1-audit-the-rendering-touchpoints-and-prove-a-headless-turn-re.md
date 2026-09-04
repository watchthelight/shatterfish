---
story: 1.1
key: 1-1-audit-the-rendering-touchpoints-and-prove-a-headless-turn-re
title: Audit the rendering touchpoints and prove a headless turn resolves
epic: 1
issue: 14
status: review
created: '2026-09-04'
updated: '2026-09-04'
---

# Story 1.1: Audit the rendering touchpoints and prove a headless turn resolves

As the engineer,
I want a spike that boots the game headlessly and completes one hero turn,
So that the headless-scene strategy is proven against the real tree before anything rests on it.

## Acceptance criteria and how each was met

| Criterion | Outcome |
|---|---|
| One hero melee attack resolves end to end with no graphics context, meaning damage is applied and the hero becomes ready again | **Met.** `HeadlessTurnSpikeTest` boots a Warrior on a generated sewer level, orders an attack at the hero's first Input wait, and asserts three things: the rat took damage, the hero became ready again, and actor time advanced by one turn. Five runs: all three held every time; the damage varied from 3 to 8 and the rat died in two of the five, because combat rolls are unseeded |
| The spike lives on the story branch under `shatterfish/harness/src/test/java` as a throwaway test, and says what stories 1.3 and 1.4 inherit | **Met.** `spike/HeadlessTurnSpikeTest.java` and `spike/NoOpGL.java`; the class comment and the report name what carries forward (boot sequence, graphics binding, update pump, actor handshake) and what must not (hand-built level, hand-placed mob) |
| The report lists every static dereference that had to be guarded, with a `path:line` | **Met.** `docs/results/e1-touchpoint-audit.md` carries the citations in the page itself, in the hook table and the driver-setup table. An earlier draft left them only in the test's console output, which would have died with the test |
| It states how many hook rows the full implementation needs | **Met.** One row, three sites, against a budget of ten |
| The story fails if that number exceeds ten | **Not triggered.** One row |
| Findings written to `docs/results/e1-touchpoint-audit.md`, with each ADR-0015 assumption confirmed or refuted | **Met.** Four confirmed, none refuted, one recorded as untested (the scene's draw parity, which this spike could not test because it builds no scene) |

## What was built

- `shatterfish/harness/src/test/java/org/shatterfish/harness/spike/NoOpGL.java`: a graphics
  binding that accepts everything and returns each method's zero value, as a dynamic proxy.
- `shatterfish/harness/src/test/java/org/shatterfish/harness/spike/HeadlessTurnSpikeTest.java`:
  the spike. It runs the actor loop on its own thread through `Actor.process()` and plays the
  render thread's part from the test thread, which is the game's own shape.
- `shatterfish/harness/build.gradle`: the game's assets added to the main and test runtime classpaths.
- Hook row 5, three one-line null guards in `GameScene`, with markers and an `UPSTREAM.md` row.
- ADR-0016, superseding ADR-0008's
  expected-ledger table. The original partitioned row 5 by file and left the emote guard with no
  home; the corrected table names each row by its reason and keeps the budget at ten.

## Decisions taken inside the story

- **A dynamic proxy for the graphics binding rather than a hand-written stub.** ADR-0015 budgeted
  about two hundred lines; the proxy is thirty and behaves identically. Reflection is barred in
  `brain`, not in `harness`. If story 1.3 prefers no reflection at all, the translation is
  mechanical.
- **The spike drives the real actor thread rather than calling `act()` directly.** `Actor.act()`
  is protected, so a driver cannot call it; more importantly, calling it directly would not have
  tested the wait and notify handshake, which is the part most likely to break.
- **The rat is left asleep.** Waking it would have avoided the emote path and hidden the third
  hook site. A real Run has sleeping mobs, so the spike keeps one.
- **The driver drains the posted-runnable queue itself.** With the headless backend's own loop
  body disabled, nothing else does, and every path that defers to the render thread would hang.

## Deviations

- The story's criteria describe the spike as reporting the hook count; landing the guards it
  counted is a small widening, taken because the count is not credible unless the path actually
  completes with them in place. The guards are one line each, marked, listed in `UPSTREAM.md`, and
  the pull request carries `touches-upstream`.
- `CLAUDE.md` and `docs/tooling.md` are corrected: `./gradlew :desktop:run` has never worked, and
  the working task is `:desktop:debug`. Found by running it to check the guards were harmless.

## Known limitations, handed forward

- The spike **polls the hero's readiness** to detect an Input wait. That is ADR-0015's rejected
  option 9, and it reads fields the actor thread writes. Story 1.5 builds the decided option;
  stories 1.3 and 1.4 must not inherit the polling.
- The `selectCell` guard makes every targeted item use a no-op headlessly, which story 1.13's
  criterion ("drives the game's own selector within the same Input wait") depends on. That story
  must install a real cell selector through the accessor hook row or change its criterion. Written
  into the audit report so it cannot be discovered late.
- The spike creates no scene, so ADR-0015's assumption that a scene must create sprites and emote
  icons exactly as the game does is **untested**, not confirmed. Story 1.3 owns it.
- Vanilla equivalence for the three guards is argued and manually checked, not tested. Story 1.2
  owes the test.

## Evidence

- `./gradlew :harness:test --tests '*HeadlessTurnSpikeTest*'`: green on five consecutive clean
  reruns; the asserted properties held every time, the fight's outcome varied.
- `./gradlew build`: green.
- `./gradlew :desktop:debug`: the unmodified game launches and runs with the guards in place.
- Rig numbers: not applicable, no Brain exists until E4.

## Follow-ups for later stories

- Story 1.2 creates the `Hooks` registry and the marker-counting test; row 5's three markers are
  its first real input, and its parser must tolerate a prose line after the id.
- Stories 1.3 and 1.4 inherit the boot sequence and the pump, and delete this spike.
- Story 1.15 is where the unseeded combat generator observed here is brought under control.
