---
story: 1.14
key: 1-14-a-random-action-warrior-run-to-death
title: "A random-action Warrior Run to death"
epic: 1
issue: 27
type: 'feature'
status: 'in-review'
created: '2026-09-11'
updated: '2026-09-11'
review_loop_iteration: 1
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
- [x] `shatterfish/harness/src/main/java/org/shatterfish/harness/agent/RandomAgent.java` — the
  chooser: given an Observation and a `Random`, draw uniformly from its set. No state and no
  preferences, so a Run's shape is the game's and not the agent's.
- [x] `shatterfish/harness/src/main/java/org/shatterfish/harness/agent/RunOutcome.java` — what a Run
  ended as: the cause, the depth reached, the turns passed, the waits served, and the Actions
  applied and refused.
- [x] `shatterfish/harness/src/main/java/org/shatterfish/harness/agent/RunLoop.java` — start a Run,
  serve waits until an ending, and serve the scene changes the driver reports by reproducing the
  interlevel work with public calls, each cited. A scene it does not serve ends the Run by name
  rather than by exception.
- [x] `shatterfish/harness/src/test/java/org/shatterfish/harness/agent/RandomAgentRunTest.java` —
  one Run in full, the turn cap, a Run that descends, and the thousand.
- [x] `docs/adr/0015-headless-scene-and-input-wait-detection.md` — an amendment: who serves a
  reported scene change, and the rule that nothing writes the hero's position from the driver
  thread.
- [x] Added under review, and not in the plan: `shatterfish/api/.../Decider.java`, the seam moved
  where a Brain can see it; `shatterfish/harness/.../hooks/MirroredUpstreamTest.java` with step 4a
  in `docs/UPSTREAM.md` and in the sync skill, holding the copied upstream bodies to the tag; and
  `RandomAgentTest`, which the mutation battery asked for.

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

## Dev notes

As the engineer,
I want an agent that plays a seeded Run by choosing randomly among the legal Actions,
So that the whole loop is exercised before any Brain exists.

Paths abbreviate `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/` as `…/`, and every
line number is at the pinned tag `v4.0.0`.

## Acceptance criteria and how each was met

| Criterion | Outcome |
|---|---|
| Given the Observation, the valid set and the executor, when the agent takes a uniformly random valid Action at every Input wait, then the Run ends in death, a Win or the turn cap and reports the depth reached and the turn count | **Met.** `RandomAgent` draws uniformly from `Observation.actions()`; `RunLoop` serves waits until an ending and answers with a `RunOutcome` carrying the cause, the depth, the turns, the waits and the tallies. `RandomAgentRunTest.a_run_ends_and_says_how` |
| And `RandomAgentRunTest` completes 1,000 such Runs unattended without an exception or a hang | **Met.** A thousand Runs in one process: `{DEATH=1000}`, deepest floor 2, 1,434,005 turns and 72,684 waits, in four and a half minutes. `RandomAgentRunTest.a_thousand_runs` |
| And a Run that reaches the 20,000-turn cap ends with cause turn cap rather than running forever | **Met.** The cap is checked at each wait against the game's own clock. The rule is tested at a cap a test can reach and the story's number is held beside it. `RandomAgentRunTest.the_cap_ends_a_run` |
| And the agent lives in `harness` under an agent package, since it is a harness tool and not a Brain, and `brain` remains empty at this point | **Met.** `org.shatterfish.harness.agent`; `brain` is still empty and the existing ArchUnit rules still hold it so |

## What was built

- `shatterfish/harness/.../agent/RandomAgent.java`, `RunOutcome.java`, `RunLoop.java`.
- `HeadlessDriver.serveSceneSwitch(Runnable)`, which lets a Run cross a floor.
- `RandomAgentRunTest`: one Run in full, the cap, a Run that leaves floor one, and the thousand;
  `RandomAgentTest`, the chooser away from a Run, which the battery asked for.
- ADR-0015's story 1.14 amendment.

## What the story found

A thousand Runs are about seventy thousand inputs, and everything below appears only at that length.

**An item action that opens the bag is only half an input.** Pressing the broken seal's affix draws
a window asking which item to affix it to (`…/items/BrokenSeal.java:113-119`), and a person who
stops there has done nothing. The valid set offered that half as a bare `UseItem`, and a Run that
took it left the game waiting for a choice no Action carries: a bag is not a Prompt the table names,
so no wait followed and the Run stopped. The set now offers the whole input, `UseItemOn`, and not
its first half.

**The full interface has no window to answer.** `GameScene.selectItem` hands the selector to the
inventory pane when the pane exists and shows a `WndBag` when it does not
(`…/scenes/GameScene.java:1668-1684`); the pane exists on the full interface
(`…/scenes/GameScene.java:399`, `:547-551`), which is the desktop default and what a headless Run
was inheriting. The pane draws nowhere headless and no Action can name a slot in it, so the game
waited for a tap that could never come. Every Run now declares the compact interface, which is the
same game seen the way a phone player sees it, and where the selector is a window the Observer reads
and an Action answers.

**A refusal that follows a change has to announce itself.** Story 1.13 made a refusal announce
nothing, which is right when nothing happened. The bag paths are not that: the item was executed,
a window opened, and sending it away is a change. Announcing nothing left the driver waiting for a
wait that had already been served. `touchedAndRefused` is the other half of `actionHandedOver`, and
the mutation that removes it stops a Run.

**A target the selector would not accept is not a tap a person could make.** The window draws a
button only for an item the selector accepts, so the executor asks `itemSelectable` first and, when
the answer is no, sends the window away with the back press — which is what tells the selector it
was cancelled and returns the hero to ready (`…/windows/WndBag.java:376-381`). Hiding the window
instead leaves the game waiting, which is how this was found.

## Decisions taken inside the story

**The loop serves the scene change, not the driver.** Alternatives: (a) the driver crosses a floor
itself; (b) the loop does the whole thing including the thread and the scene; (c) the driver owns
the thread and the scene and the caller says what the change means. Chosen (c): ADR-0015 gives the
driver one job, to report, and a driver that decided what a transition was would decide what a Run
is — a Replay would have no say. `serveSceneSwitch(Runnable)` is that split.

**The interlevel work is reproduced, not run.** Alternatives: (a) run `InterlevelScene` headlessly;
(b) spend a hook row to call its private methods; (c) reproduce the body with public calls and cite
it. Chosen (c), which is what `HeadlessDriver.newGame` already does for the scene that starts a
game: the scene is a fade, a thread and a progress bar, none of which a headless Run has, and
ADR-0016 has no row to spend. Pre-mortem: the risk is upstream changing the body and this copy
drifting, which the upgrade procedure's hook re-verification does not cover — so the mutation that
generates a floor that was already generated is in the battery, and `docs/UPSTREAM.md`'s "what moves
with the pin" table names it.

**The cap is the game's own clock**, `Statistics.duration + Actor.now()`
(`…/actors/Actor.java:196`; `…/ui/QuickSlotButton.java:278`), and not a count of waits: a wait is
one input, and resting through a hundred turns is one input, so a cap counted in waits would let a
resting Run run a very long time under a cap that looked small.

**No test pins a seed to an outcome.** A Run is not yet reproducible from its tuple (issue #70), so
a test that expected a seed to reach floor two would be asserting today's noise. The descent test
plays Runs until one descends instead, and says why.

## Evidence

`./gradlew clean build -Pshatterfish.mobile=off`: green, 502 tests across 47 suites.
`mkdocs build --strict`: clean.

**A thousand Runs**, unattended, in one process: `{DEATH=1000}`, deepest floor 2, 1,434,005 turns and
72,684 waits, in four and a half minutes — about 5,300 turns a second, which is the first throughput
number this program has and is not a published one (story 1.21 owns those, on a named machine). The
counts differ slightly from one run of the suite to the next, which is issue #70 and not noise in
the measurement.

**Mutation battery**, sixteen mutations of the agent, the loop, the driver, the executor, the set
and the boot, each applied to a committed clean tree and run against the tests that
could catch it — the chooser's own test, the agent's short Run tests, the executor's two suites and
`ValidActionsTest`:

| # | Mutation | Caught by | What failed |
|---|---|---|---|
| N1 | the agent always takes the first Action offered | `RandomAgentTest` | org.opentest4j.AssertionFailedError: a different seed is a different stream ==> expected: <true> but was: <fal |
| N2 | the turn cap never ends a Run | `RandomAgentRunTest` | org.opentest4j.AssertionFailedError: DEATH on floor 1 after 1668 turns and 114 waits (112 applied, 2 refused) |
| N3 | a death is recorded as the turn cap | `RandomAgentRunTest` | org.opentest4j.AssertionFailedError: TURN_CAP on floor 1 after 1340 turns and 105 waits (101 applied, 4 refuse |
| N4 | a scene change is never served | `RandomAgentRunTest` | org.opentest4j.AssertionFailedError: seed 51 ended badly: UNSERVED_SCENE on floor 1 after 1309 turns and 121 w |
| N5 | a floor is generated fresh even when it was generated before | **survives** | the battery says why beside the mutation |
| N6 | a descent arrives at no particular cell | **survives** | the battery says why beside the mutation |
| N7 | the driver keeps the floor it left behind | **survives** | the battery says why beside the mutation |
| N8 | the old floor keeps its actor thread | `RandomAgentRunTest` | java.lang.IllegalStateException: destroy() with the actor thread alive; call SceneStepper.endActorThread() fir |
| N9 | a refusal after a change announces nothing | `ActionExecutorTest`, `RandomAgentRunTest` | org.shatterfish.harness.driver.HeadlessDriver$Stalled: no Input wait within 10000 frames of 0.2 s. The last ac |
| N10 | an item the selector refuses is answered with anyway | `ActionExecutorTest` | org.opentest4j.AssertionFailedError: a target the window draws no button for is not a tap a person could make |
| N11 | the window left open is hidden rather than cancelled | **survives** | the battery says why beside the mutation |
| N12 | the set offers only the targeted shape | `ActionExecutorTest`, `ValidActionsTest` | java.lang.AssertionError: the armour offers its seal |
| N13 | a Run plays on the full interface | `ActionExecutorTest`, `ActionValidityPropertyTest`, `RandomAgentRunTest` | java.lang.IllegalStateException: a Run plays on the compact interface, and this process has interface size 2; |
| N14 | an action that opened no bag is called a refusal | `ActionExecutorTest` | org.opentest4j.AssertionFailedError: the scroll was read, which is what pressing READ does, so the Action was |
| N15 | the mirrored body holds allies into the city quest area | **survives** | the battery says why beside the mutation |
| N16 | a mirrored body is not held to the tag | `MirroredUpstreamTest` | org.opentest4j.AssertionFailedError: an upstream body Shatterfish reproduces has changed. Read it, decide what |

Eleven of sixteen are caught; the battery grew by three during the review. Five survive and the
battery says why beside each. N5 shows only when a Run returns to a floor it has left, which no Run
here does. N6 is hidden by `Dungeon.switchLevel` falling back to the level's own first transition
when the cell it is given will not hold a hero (`…/Dungeon.java:472-476`), which on a floor with one
way in is the same cell. N7 leaves the driver reading the window in front from a scene it has
destroyed, which nothing observes until something shows a window on a floor a Run descended to, and
is the one worth remembering. N11 is the cancel in a branch the set reaches only when it is stale.
N15 removes a guard about floors sixteen to twenty, which no random Warrior reaches;
`MirroredUpstreamTest` is what defends that one, by failing when the upstream body changes so that
someone reads it.

Two mutations were rewritten rather than reported. One made the agent take the first Action at every
wait, which does not fail a test — it hangs, because a Run that only ever steps neither dies nor
reaches the cap in any time worth spending, and the test timeout did not stop it; the chooser is now
mutated against the chooser's own test, `RandomAgentTest`, which is seconds and which exists because
of this. The other renamed a switch label and did not compile, and the runner called it a survivor,
because the api module's tests still write results while the harness module fails to build. The
runner now reports a mutation javac refused as one that was never tried.

## The fairness review

The review returned **CHANGES**: no blocking finding, and one should-fix that was as serious as a
block.

**Narrowing the valid set took reading most of the scroll table away from the bot, and the executor
called a successful read a refusal.** `READ` is in `ON_AN_ITEM` because it *can* open the bag, and
it does for five scrolls and for no other. Offering only the targeted shape therefore left every
other scroll reachable only as `UseItemOn`, and that path ran `Item.execute` — the scroll was read,
consumed, its effect landed — then found no window to answer and reported `Rejected`. A Run counted
each of those as a refusal, and sixty-four in a row would have ended it as one, so the first
Run-level numbers this program produces were wrong in the direction that flatters them. Both shapes
are offered again, as story 1.12 wrote them; an action that opens no bag is applied, because the
press happened and it was the press a person makes; and the bug the narrowing was written for is
the executor's, which cancels a window no Action can answer. The two branches now have tests, which
is the gap that let this through — the review found that neither was driven.

The other findings, each taken:

- **The mirrored interlevel body dropped a guard the game carries.** Upstream does not hold allies
  into the city's quest area (`…/scenes/InterlevelScene.java:650-655`, `:694-699`); the copy held
  them unconditionally, which is a different game on floors sixteen to twenty.
- **An arrival the game does not have was being invented.** Upstream dereferences the destination
  transition without asking; where it would throw, the copy dropped the hero wherever the
  no-position path puts them, turning a loud failure into a quiet wrong placement. It fails now,
  naming the transition type.
- **Nothing held a mirrored body to the tag.** The hook ledger counts edits to upstream files; a
  body copied out of a private method leaves no mark, so an upgrade could rewrite `InterlevelScene`
  and leave the build green with the harness playing a different game. `MirroredUpstreamTest`
  digests the three bodies and fails on drift, `docs/UPSTREAM.md` gains step 4a, and the sync skill
  carries it. `HeadlessDriver.newGame` had this gap before this story and is covered by the same
  test now.
- **The interface a Run plays on was a silent input to what a Run can do.** Every targeted item
  action depends on it, and the desktop default is the other one, so `HeadlessDriver.start` asserts
  the compact interface rather than assuming it.
- **The decider's seam was declared in the module that imports the game.** It is `Decider` in `api`
  now, where a Brain can see it; the comment promised something the module graph forbids.
- **Two citations did not carry their claim.** The executor's fairness argument rests on the window
  greying a slot with the very predicate it asks (`…/windows/WndBag.java:355-357`), not on the
  selector's declaration, and the back press is `:377-382`.
- **`ValidActions`'s javadoc contradicted its code.** It records the narrowing and its reversal.

What the review looked for and did not find is worth as much as what it found: no path by which
hidden state reaches an Observation, an Action, or anything a Brain could hold; `itemSelectable` is
parity-clean because the window greys the slot with the same predicate, so the executor learns only
what the screen draws; the compact interface shows *less* than the full one and nothing the Observer
reads; and the diff adds no RNG, seed, snapshot, oracle or reflection path.

## Deviations

- No upstream file is touched and no hook row is spent.
- Four files outside the story's own package changed: `ActionExecutor` and `ValidActions`, because
  the thousand Runs found the bag paths wrong; `HeadlessDriver`, which gained the scene crossing and
  the interface assertion; and `HeadlessBoot`, which declares the interface. All four are in the
  battery, and the diff carries the `fairness` label for them.
- `api` gained one type, `Decider`, at the review's asking, and it is named in
  `JsonRenderingTest`'s allowlist with the reason.
- A test the story did not plan: `MirroredUpstreamTest`, which holds the upstream bodies this story
  reproduces to a digest. It belongs to the upgrade procedure as much as to this story.

## Known limitations, handed forward

- **A Run is not reproducible from its tuple** (#70): the same seed and chooser give different Runs
  in one process, and different ones again across processes. Stories 1.15 and 1.16 own this.
- **A random Warrior reaches floor two at best**, which is what a uniform chooser does; it is the
  baseline every Brain is measured against, not a target.
- **The scenes served are the descent, the ascent and the fall.** Any other mode ends the Run with
  `UNSERVED_SCENE` naming it, rather than a guess.
- **A window the Prompt table does not name still ends a Run**, now as `UNKNOWN_WINDOW` naming the
  class rather than as a hang.

## Follow-ups for later stories

- Story 1.15 (#28): the salt, the mix function and the Profile — the first half of #70.
- Story 1.16 (#29): identity order and the two-JVM determinism test — the second half.
- Story 1.21 (#34): the published throughput number, on a named machine.
