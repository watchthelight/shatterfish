---
story: 1.4
key: 1-4-the-driver-loop-and-the-first-input-wait
title: The driver loop and the first Input wait
epic: 1
issue: 17
status: review
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
| `HeadlessBootTest` starts a seeded Warrior game and reaches the hero's first Input wait | **Met.** `HeadlessDriver.start(seed, HeroClass)` takes the player's path (seed window, start button, the loading scene's `descend()`), lifted from the test fixture `FreshRun.start` into `newGame`; `stepToInputWait()` returns `INPUT_WAIT` after one frame with the hero `ready`, no action, no window, and `Dungeon.seed` equal to the seed typed |
| A Prompt window opened by game code appears headlessly and can be closed through its own button | **Met.** The test makes a cell beside the hero a chasm and clicks it; `Hero.getCloser` reaches `Chasm.heroJump`, which posts a `WndOptions` to the render thread (`…/levels/features/Chasm.java:57-96`); the driver's next frame shows it and confirms the wait under it. The test presses the window's "no" button the way a mouse does, two pointer events at the button's screen centre, delivered by the game's own `PointerEvent` dispatch inside the next frame; the window closes and the hero has not moved. A second test presses "yes" and the driver reports the game's request for `InterlevelScene` in `FALL` mode; a third takes the resurrection prompt the same way |
| No library-owned loop thread drives the scene, asserted by the test that the driver's own step count matches the number of scene updates | **Met.** After six waits, `driver.frames()` equals `HeadlessScene.updates()` and `SceneStepper.frames()`; the backend's loop thread is dead and its frame id is still -1 |
| A Run that never reaches a wait fails with a diagnostic naming the last actor processed, rather than hanging | **Met.** A test actor whose `act()` returns false without `next()` is added; the driver's `stepToInputWait(300)` throws `HeadlessDriver.Stalled` after exactly 300 frames: `The last actor processed was Stuck#15: its act() returned false and nothing has called next() since (Actor.java:293-321 at the pinned tag), so the actor thread is parked between turns waiting for a callback that has not come`, followed by the hero's flags and the actor thread's stack. A second test stalls the thread on a sprite that never stops moving and gets the other sentence. The stall test runs under a separate-thread timeout, and the mutation that ignores the budget fails by that timeout rather than hanging the build |

Also delivered: the driver stops on the hero's death (`HERO_DEAD`) and on a requested scene change (`SCENE_SWITCH`); a `Halt` says why the loop stopped and how many frames it took; the resurrection prompt is an Input wait; a Run's leftovers in the render thread's queue and the input queues never reach the next Run.

## What was built

- `shatterfish/harness/src/main/java/org/shatterfish/harness/driver/HeadlessDriver.java` (moved from `org.shatterfish.harness`): `boot()`, `newGame(seed, heroClass)`, `start(seed, heroClass)`, `stepToInputWait()` with a frame budget, `step()`, `frames()`, `close()`, the `Halt` record with its `Reason`, the `Stalled` exception, and a `main` that boots, starts a Run and reports its first wait.
- `…/boot/HeadlessBoot.java`: the backend is subclassed (`Backend`) to expose how many runnables are queued and whether its loop thread is alive; the boot joins that thread and fails if it does not end; `profile(Path)` invalidates the game's save-slot cache.
- `…/boot/HeadlessGame.java`: `destroy()` and `switchTo()` deliver the render thread's queue and the input event queues before the scene goes.
- `…/scene/HeadlessScene.java`: `openWindow()`, the window in front, read from the scene's own member list.
- `…/scene/SceneStepper.java`: the frame delivers queued pointer, key and scroll events where `Game.update()` does; `currentActor()` reads `Actor.current` by reflection (the second and last declared reach); `parkedOnItsOwnMonitor()`, `describeActorThread()` and `name(Actor)` for diagnostics; `endActorThread()` interrupts a parked thread until it has left.
- Tests: `driver/HeadlessBootTest` (eleven tests, replacing `HeadlessDriverTest`); `scene/FreshRun` now delegates its start to the driver and keeps only the two reflective resets (`forget()`); `HeadlessSceneTest` starts its Runs through the driver; `HarnessReflectionTest` declares the second field, reads the ledger's paragraph on harness main code rather than the whole file, and counts reflective writes; `HarnessPackageAnchorTest` imports the moved class.
- Docs: ADR-0015 amendment for story 1.4; eight rows in `docs/rules/game-loop.md`; `docs/UPSTREAM.md`'s reflection paragraph names both fields and the tests' new reach (`Group.members`); `docs/architecture.md`'s harness row.

## What the story found

**The wait the flags show is one frame early when the hero's own act posts a window.** The chasm
prompt is posted from inside the hero's act and the hero is `ready()` in the same act
(`…/actors/hero/Hero.java:1838-1850`, `:989-992`). Read between frames, the flags say "Input wait,
no window"; the window is in the backend's queue and appears at the start of the next frame. A
click accepted in that gap is one the game would refuse a frame later
(`GameScene.interfaceBlockingHero()`). The driver therefore confirms a wait only when nothing is
queued, and steps one more frame otherwise. Story 1.3's parity script, which clicks between frames
on the flags alone, is exposed to this in exactly one situation (a prompt pending), and its
committed seed and script never reach one; story 1.5's confirmation inherits the rule.

**A hero at zero health with an unblessed ankh is not dead to the game.** `Hero.die` marks a
resurrection as pending, posts `WndResurrect`, and returns without dying
(`…/actors/hero/Hero.java:2141-2190`, `:2171`); `Dungeon.saveAll` treats the game as still on
while the mark exists (`…/Dungeon.java:707`), and the window's confirm button detaches the ankh and
requests `InterlevelScene` in `RESURRECT` mode (`…/windows/WndResurrect.java:125-141`). The first
driver reported `HERO_DEAD` there and would have closed a live game; the review found it. Dead now
means what the game means, the resurrection window is an Input wait, the one Prompt a hero answers
without being `ready`, and a requested scene change is checked before death, because the confirm
button clears the mark and asks for the loading scene in one click.

**The render thread's queue and the input queues belong to the process, and a Run's leftovers ran
in the next Run.** The mutation battery found the first: with the empty-queue rule removed, the
chasm prompt one test left queued appeared in the next test's scene, blocking its clicks. The
unmutated driver had the same leak on every death, because the dying hero posts the game-over
banner (`…/actors/hero/Hero.java:2256`) and the driver stops before a frame runs it; the statics the
runnables call only check that some scene exists (`…/scenes/GameScene.java:1352-1353`,
`:1482-1483`). The review found the second: the pointer, key and scroll queues are process statics
too (`SPD-classes/.../input/PointerEvent.java:131-132`), with no way to clear them but delivery, and
a click queued for one Run and delivered by no frame moved the next Run's hero. `HeadlessGame` now
delivers both against a scene before it is destroyed or replaced, and `newGame` refuses to start
with runnables queued or a resurrection mark alive.

**Every start occupies a save slot for the life of the process, and story 1.3 ran its later Runs
in slot -1.** `Dungeon.switchLevel` saves the game (`…/Dungeon.java:511-512`), which marks the
slot occupied in a cache keyed by slot alone (`…/GamesInProgress.java:40-41`, `:98-136`);
`firstEmpty()` returns -1 after six saves, one per hero class. Nothing validates the slot, so
story 1.3's seventh and later Runs saved into `game-1/` and worked. The driver checks the slot, and
the boot invalidates the cache whenever the profile directory changes, which is the moment the
cache stops describing reality.

**A click can be delivered through the game's own input pipeline with no access to anything
private.** `PointerEvent.addPointerEvent` queues a DOWN and an UP at a screen point;
`processPointerEvents` dispatches to every `PointerArea`, most recently registered first
(`SPD-classes/.../utils/Signal.java:40-45`, `:67-80`), and a window's buttons register after its
blocker (`…/ui/Window.java:68-80`, `…/ui/Button.java:50-115`). The button's screen rectangle comes
from its public geometry and its camera; a map cell's from `DungeonTilemap.tileCenterToWorld` and
the main camera. What is not public is the window's member list (`Group.members` is protected), so
finding the button is the test's reflective reach for now.

**`Actor.current` between turns names what the game is waiting on.** After `act()` returns false
the loop parks with `current` still set unless `next()` cleared it (`…/actors/Actor.java:229-231`,
`:293-321`). So at a park it is the actor waiting for an animation callback, or the hero waiting
for input, and null when turns are ending normally. The classic headless failure, an animation
callback that never fires, therefore names its actor; a hero that never becomes ready for another
reason (paralysis, resting) leaves `current` null, and the diagnostic then says so and names the
actor due next. The text names cells and health the player may not see, which is what parity
forbids the brain; it is marked in code as a diagnostic for a person that must never reach an
Observation or a log the brain reads except under the oracle flag.

**One interrupt does not end a thread parked on a sprite.** It catches the interrupt in the sprite
wait (`…/actors/Actor.java:283-285`) and parks again on its own monitor before it re-reads
`keepActorThreadAlive` (`:304-324`), so `endActorThread()` failed its join and the next Run refused
to start. It now interrupts a parked thread until it has left, and a Run stalled on a sprite still
closes.

**Death posts the game-over banner and requests no scene change.** `Hero.die` posts
`GameScene.gameOver()`, deletes the save and submits the ranking; the banner's buttons are what
switch scenes (`…/scenes/GameScene.java:1482-1494`). The actor loop stops picking actors
(`Actor.java:294-297`) and the scene stops notifying, so the driver stops on the game's own test of
death.

**A buff at the hero's time never acts while the hero waits.** Poison attached at a wait acts only
after the hero spends a turn: at equal time the hero's priority wins every wake-up, and a waiting
hero returns false without spending. The death tests hand the hero a move first. And the Warrior's
seal shields, so poison sized to the hero's health does not kill; the tests use far more.

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

**What dead means.** Alternatives: (a) `hero.isAlive()`; (b) the game's own test,
`isAlive() || WndResurrect.instance != null`, with the resurrection window as an Input wait; (c) a
fourth stop reason for the prompt. Chosen (b): the window is a Prompt the Overlay's player answers
like any other, and ADR-0013 already says an ankh choice is an `AnswerPrompt`. Pre-mortem: the hero
is not `ready` under it, so story 1.5's flag from the observe site will not fire for it; the
confirmation must keep recognising the window.

**How the diagnostic names the actor.** Alternatives: (a) sample `Actor.current` while the thread
runs, which is probabilistic; (b) a hook in `Actor.process` recording every actor processed, which
is a row for a diagnostic; (c) read `Actor.current` between frames by reflection in the stepper,
where the one existing reach lives, and say what null means. Chosen (c). Pre-mortem: an upgrade
renames the field; the failure is immediate and by name, and row 4 is where it moves.

**How the button is pressed.** Alternatives: (a) call `onClick()`, which is protected; (b)
`Window.onBackPressed()`, which is not the button; (c) real pointer events through the game's
dispatch. Chosen (c), with the events delivered inside the frame where `Game.update()` delivers
them. Pre-mortem: `KeyEvent.processKeyEvents` reaches `Game.inputHandler` for keys bound to clicks,
and the headless game has no handler; the bindings are empty headlessly and no key is ever queued,
and the executor story decides whether keys are an input at all.

**Which stop reasons.** Alternatives: (a) only the wait, with death and scene changes as stalls;
(b) wait, death, scene change, each named. Chosen (b): the actor loop and the scene behave
differently in each, and the Run stories need to tell them apart. The scene change is reported and
refused, not served; serving it is story 1.14's road to a Run that crosses floors.

**Where leftovers are drained.** Alternatives: (a) discard the queues when a Run closes; (b) run
them after the scene is destroyed, where the statics no-op; (c) run them before the scene is
destroyed or replaced, in `HeadlessGame`, which every teardown path goes through, and refuse to
start a Run with anything queued that can be counted. Chosen (c): it is what the render thread
would have done at its next frame, and it covers tests that end a Run without the driver.
Pre-mortem: a leftover runnable that switches scenes or writes files; none does at the tag, and the
guard in `newGame` makes any surviving runnable a named failure rather than a leak. The input
queues cannot be counted without reflection, so their guard is the test.

**Ending a stalled thread.** Alternatives: (a) one interrupt and a join, as before; (b) interrupt
a parked thread until it has left, within the timeout; (c) `Thread.stop`, which does not exist.
Chosen (b): the game's own `endActorThread` sends one interrupt because the game's thread is
never parked on a sprite when the game shuts down.

## Evidence

`./gradlew build -Pshatterfish.mobile=off`: green, 61 tests across 12 suites. `mkdocs build --strict`:
clean. The harness suite run eleven times in a row across the three commits: green each time.

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

**Mutation battery.** Run on the committed tree, each mutation applied, its tests run with results
cleared first and Gradle's exit code checked, then restored with `git checkout` and the tree
verified clean. Nineteen mutations, nineteen caught.

| Mutation | Result |
|---|---|
| M1 the driver ignores pending runnables when confirming a wait | `HeadlessBootTest` fails twice: the chasm prompt is not there at the wait. On the first battery it also failed the death test, which is how the leftover-queue leak was found |
| M2 the frame budget is off by one | fails: 301 frames spent, not 300 |
| M3 the frame budget is ignored | fails after 5 minutes by the separate-thread timeout: `a_run_that_never_reaches_a_wait_fails_naming_the_last_actor() timed out`. The hang is a failure, not a hang |
| M4 the diagnostic does not name the actor | fails on the message |
| M5 the driver counts two frames per step | fails four times, including the step-count-equals-updates assertion |
| M6 input events are not delivered inside the frame | fails three times: the window does not close, and neither "yes" nor the resurrection is ever pressed |
| M7 a requested scene change is not noticed | fails twice: `INPUT_WAIT` where `SCENE_SWITCH` was expected, and `HERO_DEAD` for a taken resurrection |
| M8 the hero dying is not noticed | fails: the driver spends its whole budget on a dead hero and reports "No actor is mid-turn" |
| M9 the current actor is not read | fails on the message: "No actor is mid-turn" for a stuck actor |
| M10 the stepper reaches a field the ledger does not name | `HarnessReflectionTest` fails: the reach and the declared set differ |
| M11 `openWindow()` never finds the window | fails twice |
| M12 the slot cache is not invalidated when the profile changes | `HeadlessSceneTest` fails three times: "no free save slot", the seventh Run of the process |
| M13 the backend loop runs and the boot does not wait for it to end | fails twice: the loop thread is alive |
| M14 the render queue is not drained when a scene is destroyed or replaced | fails four times: four runnables left by the death, and the next Runs refuse to start |
| M15 the input event queues are not delivered when a scene goes | fails: the next Run's first wait takes two frames and its hero has moved |
| M16 the resurrection mark is ignored, so zero health is death | fails: `HERO_DEAD` at the resurrection prompt |
| M17 ending the actor thread interrupts it only once | fails eight times: the sprite-stalled Run cannot close, and every Run after it refuses to start, which is the wedge the review described |
| M18 death is checked before a requested scene change | fails: `HERO_DEAD` for a taken resurrection |
| M19 a second reflective write in the stepper | `HarnessReflectionTest` fails: two writes where the ledger says one field is only read |

## The fairness review

One round, by the `fairness-reviewer` subagent on `git diff main...HEAD` after the first two
commits, interrupted once by a network failure and resumed. **PASS on information parity, no
blocking finding** (confidence high for parity, medium for determinism), with nine findings, every
one taken:

1. **Queued input events crossed Runs** (demonstrated: a click queued in one Run moved the next
   Run's hero). Fixed in `HeadlessGame`; `queued_input_does_not_cross_runs` is the test; M15.
2. **`HERO_DEAD` fired at the resurrection prompt** (demonstrated), and closing there would have
   destroyed a live game. Fixed: dead is the game's own test; the window is an Input wait; the
   scene change is checked first. `an_ankh_makes_death_a_prompt` is the test; M16, M18.
3. **A Run's start is not determined by the seed within a process**: the journal loads once per
   process and level generation reads it; `Chasm.jumpConfirmed` is a process static too. The
   driver resets the latter and validates the seed before it touches anything; the journal is
   story 1.15's, as the story already said, and is now a named limitation.
4. **The diagnostics expose hidden state through public harness API.** Marked in code and in the
   ADR as for a person, never for an Observation or a log the brain reads except under oracle.
5. **`KeyEvent.processKeyEvents` can reach a null `Game.inputHandler`** for keys bound to clicks.
   Safe today (empty bindings, no key queued); recorded in the frame's comment and here.
6. **Four citations wrong**: `Hero.java:870-876` (is 865-870), `Hero.java:1836-1845` (is
   1838-1850), "sixth Run" (seventh), and the pre-existing `GameScene.java:796-806` (793-806).
   All corrected.
7. **Weak tests**: the ledger check could pass on the tests' sentence alone, and nothing counted
   reflective writes. The reflection test now reads the paragraph on harness main code and
   counts `Field.set` (M19); the out-of-range seed is tested; the resurrection and input-queue
   tests are the ones above.
8. **`start()` with an unclosed scene switched the profile before destroying it**, so the old
   scene's badges and journal would be written into the new Run's directory. Fixed: a leftover
   scene is destroyed first, in its own profile.
9. **`close()` after a sprite-parked stall wedged the process** (one interrupt is not enough).
   Fixed in `endActorThread()`; `a_run_stuck_on_a_sprite_names_the_actor_and_still_closes` is the
   test; M17 shows the wedge's blast radius.

The reviewer also verified sixty-odd citations at the tag, could not construct a false wait, and
noted that `openWindow()` reaches `Group.members` by inheritance, a route the ledger's reflection
paragraph does not cover; that is recorded under limitations.

## Deviations

- The story text names `HeadlessBootTest`; it replaces `HeadlessDriverTest`, whose one assertion it keeps.
- `FreshRun.start` was lifted into the driver except for its two reflective resets of once-per-process loaders, which stay in the test fixture because harness main code confines reflection to the stepper; story 1.15 owns the Profile that makes them unnecessary.
- The manual `:desktop:debug` check was not run. This story edits no upstream file.

## Known limitations, handed forward

- **The wait is still read from the flags.** Between frames, with the thread parked and its writes published, and with the queue empty; story 1.5 replaces the flags with the observe-site notification and keeps the confirmation, which must also keep recognising the resurrection window, under which the hero is not `ready`.
- **Same seed twice in one process is not the same floor** unless the journal is reset first, which only the test fixture does; a Rig Run is a process of its own. Story 1.15 owns the Profile.
- **Finding a button needs the window's member list**, which is protected in `Group`. The test reads it by reflection; the Observer's prompt story (1.10) and the executor (1.13) need it in main code, by a row-4 accessor or a declared reflective read. `HeadlessScene` reaches the scene's own list by inheritance, a route the ledger paragraph does not name.
- **The diagnostics are oracle-grade.** `Stalled`'s message, `describeActorThread()` and `name(Actor)` print cells and health behind the fog. They are for a person; the Observation stories must never route them.
- **A requested scene change is reported, not served.** The driver refuses to step past it. A Run that crosses floors, and one that resurrects, is story 1.14's.
- **Key events bound to clicks would reach `Game.inputHandler`, which is null headlessly.** No key is queued today.
- **The driver's `main` starts one Run and stops at its first wait.** The Rig's process-per-Run runner is E3's.

## Follow-ups for later stories

- Story 1.5: the observe-site hook sets the flag; the driver consumes it in `stepToInputWait` and confirms with the same conditions plus the empty queue, and recognises the resurrection window by itself.
- Story 1.10 and 1.13: a way to enumerate a window's options and buttons from main code.
- Story 1.14: serve `SCENE_SWITCH` (destroy the scene, run `InterlevelScene`'s work, create the next scene, re-register listeners at the scene seam), including `RESURRECT`, and end a Run at death.
- Story 1.15: the Profile replaces `FreshRun.forget()` and the boot's slot-cache invalidation, and a same-seed-twice test goes through the driver.
