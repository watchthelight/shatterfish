---
story: 1.14
key: 1-14-a-random-action-warrior-run-to-death
title: "A random-action Warrior Run to death"
epic: 1
issue: 27
type: 'feature'
status: 'ready-for-dev' # draft | ready-for-dev | in-progress | in-review | done
created: '2026-09-11'
updated: '2026-09-11'
review_loop_iteration: 0
baseline_commit: '8249c3113e3583df5fd88cc0f662d6225e3c818d'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Every part of the loop exists — the driver finds an Input wait, the Observer reads the
screen, `ValidActions` says what may be pressed and `ActionExecutor` presses it — and nothing has
ever driven all four to the end of a Run. Until something does, the parts are known to work one wait
at a time and unknown to work for ten thousand, and no number Shatterfish publishes later has a
floor under it.

**Approach:** An agent in `harness` that takes a uniformly random valid Action at every Input wait
until the hero dies, wins or the turn cap is reached, and reports how the Run ended, how deep it got
and how many turns it took. The Run loop serves the scene changes the driver reports rather than
stopping at them, so a Run can leave floor one (issue #68).

## Boundaries & Constraints

**Always:** The agent chooses uniformly from `Observation.actions()` and nothing else, so it is held
to the same set the Brain will be. It lives in `org.shatterfish.harness.agent`, and `brain` stays
empty, which is epic 1's own rule. One process hosts one Run, so a thousand Runs are a thousand
start-and-close cycles, each leaving nothing behind for the next. A Run ends for exactly one reason
and says which. Every claim about the game is a `path:line` at `v4.0.0`.

**Ask First:** Anything that would spend a hook row. ADR-0016 gives this epic five and it has spent
five. If serving a scene change cannot be done with public calls, stop and say so rather than taking
a row assigned to E5.

**Never:** No Action outside the valid set, even to get past something awkward, and no branch in the
agent that treats one kind differently from another — a bias here becomes a bias in every number
measured later. No writing of game state to arrange a test: a test that needs the hero somewhere
lets it walk there, because writing `hero.pos` from the driver thread is what made issue #68 look
like a harness bug when it was a probe bug. No turn cap other than the one the story names, and no
silent extension of it.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| The hero dies | A wait whose Action kills the hero | The Run ends with cause `death`, carrying the depth reached and the turns passed | N/A |
| The turn cap | A Run still alive at 20,000 turns | The Run ends with cause `turn cap`, and the loop stops rather than running on | N/A |
| A descent | A wait where `Descend` is taken and activates | The driver reports the scene change, the loop serves it, and the next wait is on the next floor | A mode the loop does not serve ends the Run with cause `unserved scene`, naming it |
| A Prompt in front | A wait under a window the Prompt table names | The set offers what the window offers and the agent answers it like any other Action | N/A |
| A window nobody named | A wait under a window that is not a Prompt | The Run ends with cause `unknown window`, naming the class, rather than hanging | The driver's `Stalled` is caught and reported as this |
| A refused Action | The executor answers `Rejected` | The Run records it and serves the next wait, since a refusal changes nothing | Refusals beyond a bound end the Run with cause `refused`, naming the last reason |

</frozen-after-approval>

## Code Map

Read at `v4.0.0`. The two hooked files (`Hero.java`, `GameScene.java`) run about nine lines longer in
the working tree than at the tag; the numbers below are the tag's.

- `shatterfish/harness/.../driver/HeadlessDriver.java` — `start`, `close`, `stepToInputWait`,
  `run(WaitSequence, maxWaits)` and `Halt`. `Halt.reason()` already distinguishes `INPUT_WAIT`,
  `HERO_DEAD` and `SCENE_SWITCH`, and `requestedScene()` names the scene asked for, so the loop
  reads an ending rather than inferring one. `Stalled` is what a wait that never arrives throws.
- `HeadlessDriver.newGame` is the precedent for serving a scene change: it reproduces
  `InterlevelScene.descend()`'s no-hero branch with citations rather than running the scene.
- `core/.../scenes/InterlevelScene.java:622-671` — `descend()`. The with-hero branch holds allies,
  calls `Dungeon.saveAll()`, takes depth and branch from `curTransition`, loads the level if it has
  been generated and generates it otherwise, then `Dungeon.switchLevel(level, destTransition.cell())`.
  `ascend()` is `:693-716` and `fall()` `:676-692`; a chasm jump is a `FALL`, so a random agent meets
  it.
- `shatterfish/api/.../ValidActions.java` and `Observation.actions()` — the set to draw from.
- `shatterfish/harness/.../executor/ActionExecutor.java` — `execute` answers `Applied` or `Rejected`
  and never throws.
- `core/.../Statistics.java:33` (`deepestFloor`) and `core/.../actors/Actor.java:196`
  (`Statistics.duration += min`) — the depth reached and the turns passed, both the game's own
  counters; the game reads the turn count as `Statistics.duration + Actor.now()`
  (`core/.../ui/QuickSlotButton.java:278`).
- `shatterfish/harness/.../executor/ActionKindCoverageTest.java` — the nearest thing to this agent
  that exists, written in story 1.13's review; its walk is the shape to generalise.

## Tasks & Acceptance

**Execution:**
- [ ] `shatterfish/harness/src/main/java/org/shatterfish/harness/agent/RandomAgent.java` — the
  chooser: given an Observation and a `Random`, draw uniformly from its set. No state and no
  preferences, so a Run's shape is the game's and not the agent's.
- [ ] `shatterfish/harness/src/main/java/org/shatterfish/harness/agent/RunOutcome.java` — what a Run
  ended as: the cause, the depth reached, the turns passed, the waits served, and the Actions
  applied and refused.
- [ ] `shatterfish/harness/src/main/java/org/shatterfish/harness/agent/RunLoop.java` — start a Run,
  serve waits until an ending, and serve the scene changes the driver reports by reproducing the
  interlevel work with public calls, each cited. A scene it does not serve ends the Run by name
  rather than by exception.
- [ ] `shatterfish/harness/src/test/java/org/shatterfish/harness/agent/RandomAgentRunTest.java` —
  one Run in full, the turn cap, a Run that descends, and the thousand.
- [ ] `docs/adr/0015-headless-scene-and-input-wait-detection.md` — an amendment: who serves a
  reported scene change, and the rule that nothing writes the hero's position from the driver
  thread.

**Acceptance Criteria:**
- Given the Observation, the valid set and the executor, when the agent takes a uniformly random
  valid Action at every Input wait, then the Run ends in death, a Win or the turn cap and reports
  the depth reached and the turn count — `RandomAgentRunTest.a_run_ends_and_says_how`.
- Given a Run still alive at 20,000 turns, when the cap is reached, then it ends with cause
  `turn cap` rather than running on — `RandomAgentRunTest.the_cap_ends_a_run`.
- Given a Run whose agent takes `Descend` while standing on the stairs, when the driver reports the
  scene change, then the loop serves it and the next wait is on the next floor —
  `RandomAgentRunTest.a_run_can_leave_the_first_floor`.
- Given a thousand seeded Runs, when they are run unattended in one process, then every one reaches
  an ending with no exception and no hang, and the causes are reported as a tally —
  `RandomAgentRunTest.a_thousand_runs`.
- Given the agent, when the module graph is checked, then `brain` is still empty and the agent is in
  `harness` — the existing ArchUnit rules.

## Spec Change Log

## Design Notes

**Why the loop serves the scene change and not the driver.** ADR-0015 gives the driver one job:
report. A driver that crossed a floor by itself would decide what a Run is, and a Replay would have
no say in it. The loop above the driver is where a Run's shape belongs, and it is where the rig will
put its own loop later.

**The turn cap is the game's own clock**, `Statistics.duration + Actor.now()`, and not a count of
waits: a wait is one input, and resting through a hundred turns is one input. Counting waits would
let a resting Run run a very long time under a cap that looked small.

## Verification

**Commands:**
- `./gradlew build -Pshatterfish.mobile=off` — expected: green.
- `./gradlew :harness:test --tests "org.shatterfish.harness.agent.*"` — expected: green, the
  thousand Runs included, with the tally printed.
- `uv run --no-project --with-requirements docs/requirements.txt mkdocs build --strict` — clean.
- A mutation battery over the agent and the loop — expected: every mutation caught, or a survivor
  explained in the code beside the branch it removes.
