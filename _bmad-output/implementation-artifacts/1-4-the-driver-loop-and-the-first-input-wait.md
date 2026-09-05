---
story: 1.4
key: 1-4-the-driver-loop-and-the-first-input-wait
title: The driver loop and the first Input wait
epic: 1
issue: 17
status: in-progress
created: '2026-09-05'
updated: '2026-09-05'
---

# Story 1.4: The driver loop and the first Input wait

As the engineer,
I want the driver thread to own the loop and reach a state where the hero waits for input,
So that everything else has a place to happen.

Paths abbreviate `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/` as `…/`, and every
line number is at the pinned tag `v3.3.8` (commit `7b8b845a`), not in the hooked working tree.

## Acceptance criteria and how each was met

| Criterion | Outcome |
|---|---|
| Given ADR-0015's decision that the driver thread owns the loop, when the driver drives the scene with a fixed fast-forward step and drains the posted-runnable queue itself | **Met.** `HeadlessDriver` (`org.shatterfish.harness.driver`, where the architecture spine puts it) owns a Run: it steps `SceneStepper` on the calling thread, one 0.2 s frame at a time, and every frame drains the backend's runnable queue and delivers queued input before the scene updates, as `Game.update()` does. No other thread advances the game: the backend's own loop thread ends during the boot, which the boot now waits for and `HeadlessBootTest` asserts |
| `HeadlessBootTest` starts a seeded Warrior game and reaches the hero's first Input wait | **Met.** `HeadlessDriver.start(seed, HeroClass)` takes the player's path (seed window, start button, the loading scene's `descend()`), lifted from the test fixture `FreshRun.start` into `newGame`; `stepToInputWait()` returns `INPUT_WAIT` after one frame with the hero `ready`, no action, no window, `Dungeon.seed` equal to the seed typed |
| A Prompt window opened by game code appears headlessly and can be closed through its own button | **Met.** The test makes a cell beside the hero a chasm and clicks it; `Hero.getCloser` reaches `Chasm.heroJump`, which posts a `WndOptions` to the render thread (`…/levels/features/Chasm.java:57-96`); the driver's next frame shows it and confirms the wait under it. The test presses the window's "no" button the way a mouse does, two pointer events at the button's screen centre, delivered by the game's own `PointerEvent` dispatch inside the next frame; the window closes and the hero has not moved. A second test presses "yes" and the driver reports the game's request for `InterlevelScene` in `FALL` mode |
| No library-owned loop thread drives the scene, asserted by the test that the driver's own step count matches the number of scene updates | **Met.** After six waits, `driver.frames()` equals `HeadlessScene.updates()` and `SceneStepper.frames()`; the backend's loop thread is dead and its frame id is still -1 |
| A Run that never reaches a wait fails with a diagnostic naming the last actor processed, rather than hanging | **Met.** A test actor whose `act()` returns false without `next()` is added; the driver's `stepToInputWait(300)` throws `HeadlessDriver.Stalled` after exactly 300 frames: `The last actor processed was Stuck#15: its act() returned false and nothing has called next() since (Actor.java:293-321 at the pinned tag), so the actor thread is parked between turns waiting for a callback that has not come`, followed by the hero's flags and the actor thread's stack. The test runs under a separate-thread timeout so that a hang is a failure |

Also delivered: the driver stops on the hero's death (`HERO_DEAD`), and a `Halt` says why the loop stopped and how many frames it took.

## What was built

- `shatterfish/harness/src/main/java/org/shatterfish/harness/driver/HeadlessDriver.java` (moved from `org.shatterfish.harness`): `boot()`, `newGame(seed, heroClass)`, `start(seed, heroClass)`, `stepToInputWait()` with a frame budget, `step()`, `frames()`, `close()`, the `Halt` record with its `Reason`, the `Stalled` exception, and a `main` that boots, starts a Run and reports its first wait.
- `…/boot/HeadlessBoot.java`: the backend is subclassed (`Backend`) to expose how many runnables are queued and whether its loop thread is alive; the boot joins that thread and fails if it does not end; `profile(Path)` invalidates the game's save-slot cache.
- `…/scene/HeadlessScene.java`: `openWindow()`, the window in front, read from the scene's own member list.
- `…/scene/SceneStepper.java`: the frame delivers queued pointer, key and scroll events where `Game.update()` does; `currentActor()` reads `Actor.current` by reflection (the second and last declared reach); `parkedOnItsOwnMonitor()`, `describeActorThread()` and `name(Actor)` for diagnostics.
- Tests: `driver/HeadlessBootTest` (seven tests, replacing `HeadlessDriverTest`); `scene/FreshRun` now delegates its start to the driver and keeps only the two reflective resets (`forget()`); `HeadlessSceneTest` starts its Runs through the driver; `HarnessReflectionTest` declares the second field; `HarnessPackageAnchorTest` imports the moved class.
- Docs: ADR-0015 amendment for story 1.4; six rows in `docs/rules/game-loop.md`; `docs/UPSTREAM.md`'s reflection paragraph names both fields and the tests' new reach (`Group.members`); `docs/architecture.md`'s harness row.

## What the story found

**The wait the flags show is one frame early when the hero's own act posts a window.** The chasm
prompt is posted from inside the hero's act and the hero is `ready()` in the same act
(`…/actors/hero/Hero.java:1836-1845`, `:989-992`). Read between frames, the flags say "Input wait,
no window"; the window is in the backend's queue and appears at the start of the next frame. A
click accepted in that gap is one the game would refuse a frame later
(`GameScene.interfaceBlockingHero()`). The driver therefore confirms a wait only when nothing is
queued, and steps one more frame otherwise. Story 1.3's parity script, which clicks between frames
on the flags alone, is exposed to this in exactly one situation (a prompt pending), and its
committed seed and script never reach one; story 1.5's confirmation inherits the rule.

**Every start occupies a save slot for the life of the process, and story 1.3 ran its later Runs
in slot -1.** `Dungeon.switchLevel` saves the game (`…/Dungeon.java:511-512`), which marks the
slot occupied in a cache keyed by slot alone (`…/GamesInProgress.java:40-41`, `:98-136`);
`firstEmpty()` returns -1 after six saves. Nothing validates the slot, so story 1.3's seventh and
later Runs saved into `game-1/` and worked. The driver checks the slot, and the boot invalidates
the cache whenever the profile directory changes, which is the moment the cache stops describing
reality.

**A click can be delivered through the game's own input pipeline with no access to anything
private.** `PointerEvent.addPointerEvent` queues a DOWN and an UP at a screen point;
`processPointerEvents` dispatches to every `PointerArea`, most recently registered first
(`SPD-classes/.../utils/Signal.java:40-45`, `:67-80`), and a window's buttons register after its
blocker (`…/ui/Window.java:68-80`, `…/ui/Button.java:50-115`). The button's screen rectangle comes
from its public geometry and its camera. What is not public is the window's member list
(`Group.members` is protected), so finding the button is the test's reflective reach for now.

**`Actor.current` between turns names what the game is waiting on.** After `act()` returns false
the loop parks with `current` still set unless `next()` cleared it (`…/actors/Actor.java:229-231`,
`:293-321`). So at a park it is the actor waiting for an animation callback, or the hero waiting
for input, and null when turns are ending normally. The classic headless failure, an animation
callback that never fires, therefore names its actor; a hero that never becomes ready for another
reason (paralysis, resting) leaves `current` null, and the diagnostic then says so and names the
actor due next.

**Death posts the game-over banner and requests no scene change.** `Hero.die` posts
`GameScene.gameOver()` (`…/actors/hero/Hero.java:2256`), deletes the save and submits the ranking;
the banner's buttons are what switch scenes (`…/scenes/GameScene.java:1482-1494`). The actor loop
stops picking actors (`Actor.java:294-297`) and the scene stops notifying, so the driver stops on
`hero.isAlive()`.

**The Warrior's seal shields.** Poison sized to the hero's health did not kill: `WarriorShield`
absorbs damage first. A test that must kill the hero needs more than its health.

## Decisions taken inside the story

**Where the driver lives and what it owns.** Alternatives: (a) keep `HeadlessDriver` in
`org.shatterfish.harness` beside the future `Observer`; (b) `org.shatterfish.harness.driver` as the
spine's structural seed says; (c) fold the loop into `HeadlessScene`. Chosen (b). The scene stays
the game's scene, the stepper stays a frame, and the driver owns the Run: the profile directory
(AD-14), the start, the loop, the stop reasons. Pre-mortem: story 1.5 wants the per-wait sequence
somewhere; it goes in the driver's loop, which is why the loop returns at every wait rather than
running to an end.

**How the wait is confirmed.** Alternatives: (a) the hero's flags, read between frames, as story
1.3's tests do; (b) the flags plus an empty runnable queue; (c) drain the queue outside the frame
when the flags say wait. Chosen (b): (a) accepts a click the game would refuse, and (c) drains
where the game does not, which a determinism story would have to explain. Pre-mortem: a runnable
that is not a window (a level-up flash, say) also costs the extra frame; that is the game's order
too, and it costs nothing else.

**How the diagnostic names the actor.** Alternatives: (a) sample `Actor.current` while the thread
runs, which is probabilistic; (b) a hook in `Actor.process` recording every actor processed, which
is a row for a diagnostic; (c) read `Actor.current` between frames by reflection in the stepper,
where the one existing reach lives, and say what null means. Chosen (c). Pre-mortem: an upgrade
renames the field; the failure is immediate and by name, and row 4 is where it moves.

**How the button is pressed.** Alternatives: (a) call `onClick()`, which is protected; (b)
`Window.onBackPressed()`, which is not the button; (c) real pointer events through the game's
dispatch. Chosen (c), with the events delivered inside the frame where `Game.update()` delivers
them. Pre-mortem: `KeyEvent.processKeyEvents` reaches `Game.inputHandler` for keys bound to clicks,
and the headless game has no handler; no key is ever queued headlessly today, and the executor
story decides whether keys are an input at all.

**Which stop reasons.** Alternatives: (a) only the wait, with death and scene changes as stalls;
(b) wait, death, scene change, each named. Chosen (b): the actor loop and the scene behave
differently in each, and the Run stories need to tell them apart. The scene change is reported and
refused, not served; serving it is story 1.14's road to a Run that crosses floors.

## Evidence

`./gradlew build -Pshatterfish.mobile=off`: green, 57 tests across 12 suites. `mkdocs build --strict`:
clean. The harness suite run five times in a row: green each time.

The stall diagnostic as printed by `HeadlessBootTest`:

```text
no Input wait within 300 frames of 0.2 s. The last actor processed was Stuck#15: its act()
returned false and nothing has called next() since (Actor.java:293-321 at the pinned tag), so the
actor thread is parked between turns waiting for a callback that has not come. Hero: Hero#1
(WARRIOR) at 178, HP 20/20, ready=false, curAction=Move to 178, resting=false, paralysed=0,
buffs=[Regeneration, Hunger, WarriorShield]. Actor thread: state=WAITING, waiting on=java.lang.Thread@1,
Actor.current=Stuck#15, Actor.now=0.0, hero.ready=false, ... actor thread stack:
    at java.base/java.lang.Object.wait(Object.java:339)
    at com.shatteredpixel.shatteredpixeldungeon.actors.Actor.process(Actor.java:318)
```

**Mutation battery.** Pending; see the section below once run.

## The fairness review

Pending.

## Deviations

- The story text names `HeadlessBootTest`; it replaces `HeadlessDriverTest`, whose one assertion it keeps.
- `FreshRun.start` was lifted into the driver except for its two reflective resets of once-per-process loaders, which stay in the test fixture because harness main code confines reflection to the stepper; story 1.15 owns the Profile that makes them unnecessary.
- The manual `:desktop:debug` check was not run. This story edits no upstream file.

## Known limitations, handed forward

- **The wait is still read from the flags.** Between frames, with the thread parked and its writes published, and with the queue empty; story 1.5 replaces the flags with the observe-site notification and keeps the confirmation.
- **Finding a button needs the window's member list**, which is protected in `Group`. The test reads it by reflection; the Observer's prompt story (1.10) and the executor (1.13) need it in main code, by a row-4 accessor or a declared reflective read.
- **A requested scene change is reported, not served.** The driver refuses to step past it. A Run that crosses floors is story 1.14's.
- **Key events bound to clicks would reach `Game.inputHandler`, which is null headlessly.** No key is queued today.
- **The driver's `main` starts one Run and stops at its first wait.** The Rig's process-per-Run runner is E3's.

## Follow-ups for later stories

- Story 1.5: the observe-site hook sets the flag; the driver consumes it in `stepToInputWait` and confirms with the same three conditions plus the empty queue.
- Story 1.10 and 1.13: a way to enumerate a window's options and buttons from main code.
- Story 1.14: serve `SCENE_SWITCH` (destroy the scene, run `InterlevelScene`'s work, create the next scene, re-register listeners at the scene seam) and end a Run at death.
- Story 1.15: the Profile replaces `FreshRun.forget()` and the boot's slot-cache invalidation.
