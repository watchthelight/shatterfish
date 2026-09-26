---
title: 'Story 5.1: The launcher, the Profile and the embedded driver'
type: 'feature'
created: '2026-09-26'
status: 'review'
baseline_commit: 'd53e33b36'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Everything so far plays headlessly: the driver owns the loop and steps its own frames.
The Overlay (epic 5) puts the bot inside the real desktop game, where the render thread owns the loop
and a Run must never hold a frame up (NFR-4), and nothing yet starts that game with a Run attached,
keeps the Run across the game's own floor changes, or proves that a Run played inside the game is the
Run the Rig would play (FR-37, ADR-0013, ADR-0015).

**Approach:** A launcher starts upstream's desktop game in a Run Profile it owns, with a Run attached
at scene creation. An embedded Run, the headless Run loop turned inside out, is handed one frame at a
time by the render thread: it confirms Input waits through the same gate the headless driver uses,
hands the Observation to the Brain's own worker thread, and executes and records the answer at a
later frame through the same executor and record writer. The game serves its own floor changes; the
Run re-attaches at each new play scene through the existing scene seam, keeping the wait index, the
salt, the Belief and the log.

## Boundaries & Constraints

**Always:**
- Non-negotiable #1: the Observer is the only door; the Brain imports only `api`; the oracle is made
  only by the launcher, off by default, and marked in the window title and the log header.
- Non-negotiable #3: no upstream edit. The existing hooks (row 3's scene seam, row 5's Input-wait
  notification) are enough; the launcher extends upstream's game class rather than editing it.
- Non-negotiable #5: one per-wait sequence for both drivers, so an embedded Run is the headless Run
  of the same tuple where the frames are the same.
- ADR-0013's deadlock rule: no monitor on a game object; the render thread never waits on the worker.

**Ask First:** an upstream edit; a new hook row; running the Rig's Brain in the Overlay before the
Codex reader moves out of the rig.

**Never:** an oracle flag on the Rig; a Brain call on the render thread; a Run's Profile shared with
another Run or with the player's own directories.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Launch | `--seed`, `--class` | the desktop game in a fresh Profile, a new game of that tuple, a Run attached | an unknown flag, a used `--profile` directory: refused by name |
| Scene creation | the play scene's `create()` | the Run attaches through hook row 3; the Observer's log listener is re-added | N/A |
| Floor change | the game serves `InterlevelScene` | the Run re-attaches at the new play scene; k, salt, Belief and log continue | N/A |
| Input wait | notified by hook row 5 | confirmed by `WaitGate`, reseeded, observed, handed to the worker | nothing confirmed while the render queue holds anything |
| Brain thinking | worker busy | every frame returns at once (`THINKING`); frames go on | N/A |
| Answer in | worker done | executed and recorded at the next frame | `CannotDecide`: `BRAIN_ERROR`; any other failure: thrown |
| Death, win, cap, stall, refusals | as the headless loop | the same ending and end record | N/A |
| `--oracle` | launcher only | `OracleObserver`, header `oracle:true`, window title `[ORACLE]` | the Rig refuses `--oracle` by name |

</frozen-after-approval>

## Code Map

- `shatterfish/harness/.../driver/WaitGate.java` (new): the Input-wait confirmation and numbering,
  extracted from `HeadlessDriver.stepToInputWait`; both drivers ask it once per frame.
- `shatterfish/harness/.../driver/HeadlessDriver.java`: uses `WaitGate`; `newGame` begins the game
  through `NewGame.begin`; `rngControl()` for a host that drives its scene frame by frame.
- `shatterfish/harness/.../driver/NewGame.java` (new): how a Run's game begins, for both drivers.
- `shatterfish/harness/.../agent/EmbeddedRun.java` (new): the embedded Run; `Host` is what only the
  loop's owner knows (the render queue, the scene asked for).
- `shatterfish/harness/.../agent/RunLoop.java`: `openLog`, `record`, `ending`, `outcome` and
  `crossFloor` shared with the embedded Run.
- `shatterfish/harness/.../boot/Profile.java`: `Target` and `owned(MemoryPreferences)` for a game the
  harness did not boot; the empty history now clears the game's cached remains (`Bones`).
- `shatterfish/harness/.../boot/HeadlessBoot.java`: `pinnedVersionName/Code` without booting.
- `shatterfish/overlay/.../ShatterfishLauncher.java`, `LaunchOptions.java`, `OverlayGame.java`,
  `OverlayApplication.java` (new): the launcher, its flags, the desktop game with the Run attached,
  and the desktop backend with its render queue counted.
- `shatterfish/overlay/build.gradle`: `desktop` and the lwjgl3 backend; the `:overlay:launch` task.
- Second review: `OverlayAgents.java` and `InputLock.java` (overlay); `RunLog.Header.driver` (api) and
  its reader; `OverlayLogs.java` (rig), applied in `Comparison`, `LogHeader` and `Gallery`;
  `SceneStepper.actorThreadParked` and `WaitGate.reconfirm` (harness). Tests `EmbeddedEndingsTest`,
  `OracleToggleTest`, `InputLockTest`, `OverlayAgentsTest`, `OverlayLogsRefusedTest`.
- Tests: `EmbeddedAttachTest`, `EmbeddedThreadingTest`, `EmbeddedDeterminismTest`,
  `EmbeddedRunRulesTest`, `EmbeddedHost` (harness `agent`); `InProcessRunsTest` (harness
  `determinism`); `LaunchOptionsTest`, `FreshProfileTest`, `OverlayOracleGateTest`, `RenderQueueTest` (overlay);
  `RigOracleGateTest.the_launchers_flag_is_refused_here` (rig).
- Docs: ADR-0003 and ADR-0013 amendments, `docs/architecture.md`, `docs/fairness.md`,
  `docs/ideas.md`, `shatterfish/settings.gradle`'s edge comment.

## Tasks & Acceptance

**Execution:**
- [x] `WaitGate` extracted; `HeadlessDriver` on it; `NewGame` extracted.
- [x] `EmbeddedRun` with its host interface; `RunLoop` helpers shared.
- [x] The launcher, its options, the overlay game and backend; `:overlay:launch`.
- [x] Tests: attach, threading, determinism, rules, in-process Runs, options, fresh Profile, oracle gates.
- [x] Docs and ADR amendments.
- [x] Mutation battery.
- [x] Manual launch of the real desktop game.

**Acceptance Criteria:**
- Given FR-37 and ADR-0013, when the launcher starts the desktop game with a Run Profile it owns
  (`FreshProfileTest`, `LaunchOptionsTest`), the driver attaches at scene creation and re-attaches
  after every level change, keeping the wait index, the salt, the Belief and the Run log across the
  boundary (`EmbeddedAttachTest.attaches_across_two_floor_changes`,
  `EmbeddedAttachTest.the_log_is_heard_on_every_floor`).
- The render thread is the UI-role thread, the Brain runs on its own worker, and the game's frame
  rate is unaffected while the Brain thinks (`EmbeddedThreadingTest`, `EmbeddedRunRulesTest`).
- The oracle flag exists only on the launcher, never on the Rig (`LaunchOptionsTest.the_oracle_and_the_salt`,
  `OverlayOracleGateTest`, `RigOracleGateTest.the_launchers_flag_is_refused_here`).
- `EmbeddedAttachTest` asserts attachment and re-attachment across two level changes.
- Determinism: an embedded Run's waits equal the headless Run's for the same tuple, field by field,
  Decisions, Beliefs and floor changes included, and the two headers differ only in `driver`
  (`EmbeddedDeterminismTest`); the one condition, the frames the desktop adds while the Brain thinks,
  is named in Design Notes and owned by story 5.13, and until then the Rig refuses an Overlay log
  (`OverlayLogsRefusedTest`).
- No Rig numbers: the Overlay does not change what the Brain decides (epic 5's intro).

## Design Notes

**Constraints restated:** the render thread must never wait (NFR-4); the per-wait sequence must be
the headless one (ADR-0013's third driver); no upstream edit unless nothing else works
(non-negotiable 3); the oracle only on the launcher (non-negotiable 1, FR-11).

**Where the Run attaches:**
1. A new hook in `GameScene` for creation and destruction (ADR-0016's row 3 "second site"). Rejected:
   creation is already notified by row 3's scene seam, right where the log listener must be re-added,
   and nothing in 5.1 needs destruction: the Run keeps no scene object, so a destroyed scene leaves
   nothing to detach.
2. A `Scene` subclass the launcher substitutes for `GameScene`, as the harness substitutes
   `HeadlessScene`. Rejected: the real game creates its play scene by class from `InterlevelScene`
   (`Game.switchScene(GameScene.class)`), so a substitute would need an edit to every place that names it.
3. **Row 3's existing seam, `Hooks.logReplaced`, chained by the Run: it re-adds the Observer's listener
   and counts the attachment.** Chosen: zero upstream edits, and the seam fires exactly once per play
   scene, before the floor's first lines.

**Where the frame comes from:**
1. `Gdx.app.postRunnable` re-posting itself each frame. Rejected: it runs at the start of the next
   frame, before the game's update, when the frame's own posted windows have not run yet.
2. A delegating `ApplicationListener` around upstream's game. Rejected in favour of 3: the delegate
   cannot read the game's protected `sceneClass`.
3. **`OverlayGame extends ShatteredPixelDungeon`, overriding `create`, `render` and `dispose` and
   calling upstream's first or last.** Chosen: the Run's frame is after the game's whole frame, and
   the subclass can answer the host's question about the scene asked for.

**How the render queue is known:**
1. A one-frame deferral of every notification. Rejected: it confirms waits a frame later than the
   headless driver, so the two drivers would disagree on frames and therefore on draws.
2. Reading `Lwjgl3Application`'s queue. Impossible: it is private at libGDX 1.14.0.
3. **Counting at the door: `OverlayApplication.postRunnable` counts up, the wrapped runnable counts
   down.** Chosen: `Game.runOnRenderThread` is `Gdx.app.postRunnable`, the one door the game posts
   through. The count is static because libGDX loops inside the constructor.

**How the wait is confirmed:**
1. A copy of the headless driver's confirmation in the embedded Run. Rejected: two copies drift, and
   the drift is exactly the Replay divergence ADR-0013 forbids.
2. The embedded Run driving `HeadlessDriver` itself. Rejected: the headless driver owns its loop.
3. **`WaitGate`, the confirmation state extracted from `HeadlessDriver`, asked once per frame by both
   drivers.** Chosen; the whole harness suite is the regression test for the extraction.

**Determinism, stated exactly.** The embedded Run is the headless Run of the same tuple whenever the
frames between two waits are the same frames: the gate confirms the same waits, and every draw
between them comes from the same generator. `EmbeddedDeterminismTest` holds this for the random
agent and for the Brain, with and without floor changes, by comparing whole log chains. The desktop
game adds frames the headless one does not have: frames drawn while the Brain thinks, and frames
paced by the wall clock rather than a fixed step. What the render thread draws in them comes from
the Run's generator today, so an Overlay Run is **not reproducible from its tuple or its Action
list** until story 5.13: a Replay of its log parts from it at the first roll the extra frames moved.
That is a named exception to non-negotiable 5 (ADR-0013's story 5.1 amendment); the Overlay log's
header says `driver: embedded` (chained), and the Rig refuses such a log wherever it reads logs.
Routing the render thread's draws away from the Run's generator is story 5.13's hook ("the speed
ceiling and the draw-routing hook", which carries Rig numbers for exactly this reason).

**Found on the way: in-process remains.** The first determinism test failed at wait 11 because the
headless loop is not reproducible across two Runs in one process when the first dies: `Bones` caches
the dead hero's remains in statics and reads its file only while it has none cached
(`core/.../Bones.java:50-54`, `:154-160`), so the second Run's first floor had a grave. The Rig plays
each Run in its own process and never saw it. The Profile's empty history now clears the cache
through `Bones.leave()`'s daily branch, the one public door that resets it and writes nothing
(`:62-68`); `InProcessRunsTest` holds it. The Profile version is unchanged: no Run in a fresh process
is affected.

**Found by the real launch: the backend's own runnables.** The first launch of the real desktop game
confirmed no wait at all. The render-queue count never reached zero because libGDX's controller
monitor (`JamepadControllerMonitor`) re-posts itself on every frame. The wait rule is about what the
game posts (a window queued by the hero's act), which is all the headless backend ever holds, so
`OverlayApplication` now counts only runnables whose class is the game's (`RenderQueueTest`).

**Found by the real launch: a scene rebuilt on the same floor.** The second launch confirmed no wait
either: the desktop game rebuilds its play scene whenever the window changes size
(`SPD-classes/…/noosa/Game.java:136-141`), once at start-up, and re-arming the gate at that rebuild
discarded the hero's first announcement, which he does not repeat. The gate is now re-armed only on a
new floor (`Dungeon.level` another object); `EmbeddedAttachTest.a_rebuilt_scene_keeps_the_wait`.

**The Brain the Overlay plays.** The Rig's Brain needs the Codex, which the rig reads
(`CodexKnowledge`); the Overlay does not depend on the rig. Story 5.1 attaches the random agent; the
Brain arrives when the reader moves somewhere both can reach (docs/ideas.md), at the latest with
story 5.16.

**Pre-mortem:**
- *The real game mutates hero state on the render thread outside the actor thread's park.* The gate
  confirms only when AD-5's condition holds and the render queue is empty; the Observer asserts the
  condition on entry.
- *A decision arrives for a wait that is no longer current.* Impossible in 5.1: no wait is confirmed
  while a decision is pending; the invariant is checked and throws. Takeover (5.8) makes it real.
- *The game serves a scene mode the headless loop does not (a resurrection, a return).* The embedded
  Run ends as the headless loop would (`UNSERVED_SCENE`), so its log stays one a Replay can follow.
- *The desktop frame count differs from the headless one.* Named above; 5.13.

## Review

**Implementation notes:**
- **No upstream edit.** The Run attaches through hook row 3's scene seam and hears waits through row
  5's notification, both already in the ledger; the launcher extends upstream's game class.
- **`WaitGate`** is the headless driver's own confirmation, moved: the whole harness suite (71 classes,
  321 tests) passes on it, and `HeadlessDriver` keeps its public surface.
- **The oracle** is built only in `ShatterfishLauncher.observer(true)`; the embedded Run is handed its
  observer, so the harness's `OracleGateTest` covers it unchanged.
- **The Brain** in the Overlay is the random agent by default; `--agent brain` attaches `shatterfish`
  on the committed weights and an empty Codex until the Codex reader leaves the rig (docs/ideas.md).
- **A test helper deadlock** in `Ledger` (hook-ledger tests): it read the subprocess's output pipe to its
  end before its error pipe, and this worktree's line-ending warnings filled the error pipe; the first
  full harness run sat 45 minutes in `HooksLedgerTest`. Standard error now goes to a file. `Brains`,
  `Registrations`, `Results` (rig) and `DocsCitations` (codex) use the same shape on small outputs and
  are left alone here.

**The manual launch of the real desktop game** (`:overlay:launch`, seed 12345, the Warrior, salt
`5a175a17`, the random agent, a 200-turn cap, `--exit-when-over`):
1. First launch: the game began, the play scene was created and the log header written, and no wait
   was confirmed. The render-queue count never left 1: libGDX's controller monitor re-posts itself
   every frame. Fixed: only the game's own runnables are counted (`RenderQueueTest`).
2. Second launch: still no wait. The desktop rebuilds its play scene when the window changes size, once
   at start-up, and the rebuild re-armed the gate over the hero's first announcement. Fixed: the gate
   is re-armed only on a new floor (`EmbeddedAttachTest.a_rebuilt_scene_keeps_the_wait`).
3. Third launch: the Run played to its end, a death on floor 1 after 80 turns and 73 waits, the window
   closing itself; the log verifies as a complete chain with a verifiable end record. Against the
   headless Run of the same tuple and agent, the first 14 waits are identical; they part at wait 15,
   the first roll decided differently: the desktop's extra frames draw from the Run's generator, which
   story 5.13 routes away.

**Mutation battery** (a scratch script, not committed; a control run first, each mutant against its
named tests):

| # | Mutant | Killed by |
|---|---|---|
| M1 | the gate confirms with the render queue full | `HeadlessBootTest` |
| M2 | the gate takes a window on its first frame | `HeadlessBootTest` |
| M3 | the executor's announcement reaches no gate | `ActionExecutorTest` |
| M4 | the embedded Run skips the reseed | `EmbeddedDeterminismTest` |
| M5 | a scene creation not counted | `EmbeddedAttachTest` |
| M6 | the log listener not re-added at a new scene | `EmbeddedAttachTest` |
| M7 | the answer taken before it is in | `EmbeddedThreadingTest` |
| M8 | the Brain decides on the render thread | `EmbeddedThreadingTest` |
| M9 | no end record | `EmbeddedDeterminismTest` |
| M10 | remains left for the next Run | `InProcessRunsTest` |
| M11 | the oracle on by default | `LaunchOptionsTest` |
| M12 | a used Profile directory taken | `FreshProfileTest` |
| M13 | the wait index restarted at a scene | `EmbeddedAttachTest` |
| M14 | hooks left registered at close | `EmbeddedAttachTest` |
| M15 | a scene rebuilt on the same floor re-arms the gate | `EmbeddedAttachTest` |
| M16 | the backend's own runnables counted | `RenderQueueTest` |

16 of 16 killed. M14 survived at first (the host's teardown cleared the hooks anyway); the test now
asserts right after the Run's own close. An earlier battery run reported false kills because the
wrapper was not found; the script now calls it by full path and runs a control first.

### Second review (fairness and lens, on 7bb09a0f7)

**Fairness.**
1. *Reproducibility was overclaimed.* An Overlay Run is now stated, in `EmbeddedRun`, ADR-0013 and
   this file, as not reproducible from its tuple or its Action list until story 5.13, a named
   exception to non-negotiable #5 in ADR-0013. Its log header carries `driver: embedded` (Run log
   schema v2, an optional chained field; ADR-0011 amendment; a headless log's bytes are unchanged),
   and the Rig refuses such a log by name wherever it scores, calibrates, reads back or shows logs
   (`OverlayLogs`, applied in `Comparison.outcome`, `LogHeader` and `Gallery`;
   `OverlayLogsRefusedTest`).
2. *The oracle toggle was untested end to end.* `OracleToggleTest` holds that
   `ShatterfishLauncher.observer(false/true)` yields `header.oracle()` false/true and that the window
   title and `Logging.oracle` follow; `EmbeddedEndingsTest.an_unannounced_oracle_is_refused` holds
   that the Run refuses an oracle Observation its log does not announce.
3. *The player could act while a Run played.* `InputLock` is placed first in the game's input
   multiplexer and swallows keys, touches, drags and scrolls until the Run ends (`InputLockTest`).
   A gamepad writes the key queue directly and is story 5.5's hook (docs/ideas.md).

**Lens.**
- F1: a requested play scene means the Run keeps playing; the test host now requests it from inside
  the frame, as the loading scene does.
- F2: the Run decides by the scene in front: the surface is a win, the loading scene in a mode other
  than descend, ascend or fall is unserved, any other scene is unserved by its name
  (`EmbeddedEndingsTest`: the surface, a resurrection, a descent and a stranger).
- F3: a wait is confirmed only with the actor thread parked (`SceneStepper.actorThreadParked`;
  `EmbeddedEndingsTest.no_wait_before_the_actor_thread_parks`).
- F4: a frame budget (the headless loop's, 20,000 frames without a wait) ends the Run as an unknown
  window naming the scene in front (`no_wait_within_the_budget`). The region intro on a first descent
  to depths 6, 11, 16 and 21 is not clicked: a Run reaching it ends there, documented in ADR-0013 and
  docs/ideas.md.
- F5: at serve time the Run re-checks that the game is quiet, nothing has acted, the scene and window
  are the ones it confirmed, and the hero still waits; otherwise it drops the answer and confirms the
  wait again (`WaitGate.reconfirm`; `a_stale_answer_is_dropped`).
- F6: the Profile is claimed atomically by creating `.shatterfish-owner`; a temporary Profile is
  deleted when the game closes (`FreshProfileTest`).
- F7: the render-queue count excludes `com.badlogic.` rather than listing the game's packages
  (`RenderQueueTest`).
- F8: `EmbeddedAttachTest` goes down two floors through the real loading-scene path (the hero is
  planted beside the exit and the Brain takes it) and asserts exactly: waits 1..n contiguous, one
  decision per wait on the Brain's thread, depths 1, 2, 3 matching the log, two loading scenes, three
  attachments, and the embedded header.

**Test runs.** `:api:test` (16 classes, 382 tests), `:harness:test` (72 classes, 329 tests),
`:overlay:test` (7 classes, 16 tests) and the Rig's log-reading classes (11 classes, 105 tests), all
green.

**The desktop launch across a floor** (`:overlay:launch`, the Warrior, salt `5a175a17`, `--agent
brain`, a 3,000-turn cap, `--exit-when-over`):

| Seed | Waits | Floors (first wait on each) | Ending | Stale answers |
|---|---|---|---|---|
| 12345 | 1,625 | 1 | death on floor 1 after 1,648 turns | 0 |
| 1000 | 1,061 | 1, 2 (797), 1 (848) | death on floor 1, deepest 2 | 0 |
| 2000 | 1,265 | 1, 2 (549), 1 (587), 2 (791), 1, 2 | death on floor 2 | 0 |
| 3000 | 1,537 | 1, 2 (705), 1 (735), 2 (1,049), 1, 2 | death on floor 2 | 0 |
| 4000 | 1,809 | 1, 2 (606), 1 (622), 2 (770), 1, 2 | death on floor 2 | 0 |

Every log's waits run 1..n without a gap, its header says `driver: embedded` and `oracle: false`, and
the window closed itself at the end. The real loading scene served each descent and ascent (up to
seven play scenes a Run) and the Run followed the hero across every one. Seed 12345 never left floor 1:
from wait 501 the descend and explore Policies held the hero between two cells until it starved, a
Brain matter recorded in docs/ideas.md. The end record's depth is the deepest floor reached, as in the
headless Run; the Overlay's closing line now says so.
