---
status: accepted
date: 2026-09-04
deciders: watchthelight (product owner), Claude (engineer)
---

# ADR-0015: The headless scene, the main loop, and Input-wait detection

## Context and problem statement

Two things every E1 story depends on had no decision. First, who owns the main loop headlessly:
libGDX's `HeadlessApplication` runs its own loop thread, while the architecture says the UI-role
thread drives everything (AD-8); whichever an E1 story picked first would decide whether Prompt
windows exist at all and whether the thread invariant holds. Second, how an Input wait is
detected: ADR-0013 said a hook in `Hero.ready()`, and the reviewer gate showed `ready()` is called
on every actor-thread wake-up, roughly sixty times a second, so the hook would fire constantly.

The research report's recommendation 1 settled the strategy (a harness-owned headless *scene*
rather than null sprites or a stub, because turn resolution runs through sprite callbacks), and
the PRD's addendum lists it as an E1 mechanism decision; it was adopted in the spine's structural
seed without ever being decided. This ADR decides all three.

Non-negotiables touched: #3 (hooks), #4 (in-process), #5 (reproducible).

Paths abbreviate `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/` as `…/`, at `v3.3.8`.

## Decision drivers

- Turn resolution ends in animation callbacks fired from `scene.update()`
  (`…/actors/Actor.java:274-286`; `…/sprites/CharSprite.java:824-862`), so something must drive the
  scene or the hero never gets another turn.
- The signal must fire exactly once per Input wait, on a state the game itself computes.
- Headless and embedded must share the per-wait sequence, or a Replay of an Overlay Run is not
  the same code as a Rig Run.
- Hooks are scarce (ADR-0008).

## Considered options

**The main loop**

1. `HeadlessApplication` with its own loop thread calling a `Game` instance. Rejected: it
   introduces a thread neither AD-8 nor the driver controls, its loop is wall-clock paced (the
   opposite of fast-forwarding), and stopping it deterministically at an Input wait means racing
   it.
2. **Construct a `HeadlessApplication` only to install the `Gdx.app`, `Gdx.files` and
   `Gdx.audio` statics the game dereferences, configured so its loop does no work, and let the
   driver thread own the real loop: `scene.update(dt)` with a large fixed `dt` in a tight loop,
   draining `Gdx.app.postRunnable` queues itself, until the Input-wait flag is set.** Chosen. The
   driver thread is then the UI-role thread of AD-8, exactly as the render thread is in the
   Overlay.
3. No libGDX application at all; stub every `Gdx.*` static. Rejected: `Gdx.files` is what loads
   the message bundles and atlases through `Pixmap`, and stubbing the file API is a larger
   surface than one headless application.
4. Run the real `ShatteredPixelDungeon` game class headlessly. Rejected: it drives scene switching
   and settings for a player, which the driver must own.

**The scene**

5. **A harness-owned `HeadlessScene`**: a `Scene` subclass that creates the Groups `GameScene`
   creates and that the game's code expects to exist, with a no-op `GL20`/`GL30` installed before
   any `Texture` class initializes and atlases loaded through `Pixmap`. Chosen (research
   recommendation 1); real sprite subclasses then behave as they do in the game, so animation
   callbacks fire and turn resolution completes with zero edits to actor or item code.
6. `HeadlessCharSprite` overrides. Rejected: about twenty overrides plus routing for the
   `sprite()` factory overrides, and mob-specific sprites would diverge from the game's.
7. Null sprites. Rejected: hundreds of upstream lines would need guards, and `Actor.process`
   would stop waiting on motions that the Overlay does wait on, so the two drivers would resolve
   turns differently.

**Input-wait detection**

8. A hook in `Hero.ready()`. Rejected by the gate: called on every wake-up.
9. Polling from the driver thread: read `Dungeon.hero.ready` and the window stack each iteration.
   Rejected alone: it races the actor thread's writes and cannot tell a fresh wait from the
   sixtieth observation of the same one.
10. **A notification at the `Dungeon.observe()` call site inside `Hero.act()`'s `!ready` branch
    (`…/actors/hero/Hero.java:840-848`)**, which the game reaches exactly once per Input wait
    because the branch is guarded by `ready` becoming true at its end. The hook sets a volatile
    `waitPending`; the UI-role thread consumes it, confirms the AD-5 condition, and clears it.
    Chosen. This is the site the earlier draft meant and mis-cited as `ready()` (935-946).
11. Counting Actions instead (a wait follows every executed Action). Rejected: interruptions, free
    Foresight searches and Prompts produce waits with no preceding Action.

## Decision outcome

**Loop ownership.** The driver thread owns the loop headlessly; the render thread owns it in the
Overlay. Both are "the UI-role thread" of AD-8. The headless driver constructs a
`HeadlessApplication` for the `Gdx.*` statics only, drives `scene.update(dt)` itself with a fixed
fast-forward `dt`, and drains posted runnables in the same loop so that code paths using
`Game.runOnRenderThread` complete deterministically rather than never.

**The scene.** `HeadlessScene` in `org.shatterfish.harness.scene` creates the same Groups the game
expects, installs a no-op `GL20`/`GL30` before any `Texture` initializes, and loads atlases as
`Pixmap`s. The first E1 story is the touchpoint audit, whose exit criterion is that a Run
completes with no upstream edit outside the hook ledger. The scene must create sprites and
emote icons exactly as `GameScene` does, because those constructors draw random numbers on the
actor thread (ADR-0007 option 15); a headless scene that skipped them would consume a different
number of draws than an Overlay Run and break cross-driver reproducibility.

**Input-wait detection.** Hook site: the `Dungeon.observe()` call inside `Hero.act()`'s `!ready`
branch. The hook sets `waitPending` and records nothing else. The UI-role thread, at its next
iteration or frame:

1. reads and clears `waitPending`;
2. confirms the AD-5 condition (hero ready; no window, or a window of a recognised Prompt kind);
3. increments `k`, reseeds from `mix(salt, k)`, observes, and runs the per-wait sequence of
   ADR-0013.

Because the branch is guarded, the flag is set once per wait and no de-duplication by hash is
needed; the "hash changed" guard of the earlier draft is withdrawn (it was vacuous anyway, since
`k` is no longer in the hashed Observation).

**Consequences for movement.** A multi-cell move never reaches the `!ready` branch between cells,
which is why ADR-0014 makes one step one Action. The two decisions are a pair: neither works
without the other.

**Scene lifetime.** Every level change destroys and recreates the scene (`InterlevelScene`), and a
new `GameLog` replaces the `GLog.update` listener when it is constructed
(`…/ui/GameLog.java:47`). The scene-seam hook therefore fires on scene creation as well, and the
driver re-registers the Observer's log listener and re-attaches the Panel there. `k`, the salt,
the Belief and the Run log survive the scene; the driver does not.

**Prompt windows headlessly.** Windows are shown through `Game.runOnRenderThread` after the hero
is ready (`…/actors/hero/Hero.java:1019-1035`); because the driver drains that queue in its own
loop, a Prompt window exists headlessly exactly as in the Overlay, and `AnswerPrompt` closes it
through the window's own button.

### Consequences

- Good: one per-wait sequence for both drivers, so an Overlay Run replays under the Rig.
- Good: the detection is a guarded branch the game already computes; no polling, no races.
- Bad: the driver must drain the runnable queue, so any game code that assumes a real frame
  cadence (tween durations in wall-clock seconds) is fast-forwarded rather than skipped; the
  touchpoint audit is the story that finds the exceptions.
- Bad: a no-op `GL20` is about two hundred lines of stubs with no upstream precedent found by the
  research; it is written once and never changes.

## Pre-mortem

*If this is wrong in six months, why?*

- The `!ready` branch is reached in a state that is not an Input wait (during an interruption, or
  while a window is open). Mitigation: the UI-role thread confirms the AD-5 condition before
  acting on the flag, and drops the flag otherwise.
- Fast-forwarding `dt` breaks an animation that gates a turn (a tween that clamps its progress).
  Mitigation: the touchpoint audit measures a Run to completion; `Actor.processing()` going false
  with the hero alive and no wait pending is the failure signal.
- `HeadlessApplication` starts a thread that touches `Gdx.graphics` and dies. Mitigation: the E1
  spike is the first story; if it does, option 3's narrower stub is the fallback and the decision
  is superseded.
- The no-op `GL20` is not enough because something calls `frame()` on a `Texture`. Mitigation:
  the research verified the constructor chain needs no GL; the audit finds the rest, and each is
  a `Pixmap` path or a hook.

## Amendment: story 1.3 (2026-09-04)

The scene decision above is kept, with one change of shape that the story found necessary and
one addition it found necessary for the loop decision.

**`HeadlessScene` is a `GameScene`, and its `create()` is `GameScene.create()`.** Option 5 said
"a `Scene` subclass that creates the Groups `GameScene` creates". The story found that the
groups are not the point. The statics game code calls are gated on `GameScene.scene`, which is
package-private and assigned only inside `GameScene.create()` (`…/scenes/GameScene.java:159`,
`:242`), and several of them carry game logic rather than drawing: a mob spawned during play is
added to the actor list only when a scene exists (`:1153-1161`), a heap dropped during play
counts for the exploration bonus only then (`:1131-1136`), a window opened by game code is shown
only then (`:1352`), and `effectOverFog` and `addSprite` dereference the scene unguarded
(`:1149`, `:1185-1186`). A scene that is not `GameScene.scene` therefore plays a different game, not
a quieter one, and `create()` itself carries level-entry logic (dropped items landing, journal
landmarks, the first log lines) that a mirror would have to re-implement, which non-negotiable 4
forbids. The real scene constructs and updates under the no-op binding with FreeType for text
and the desktop module's fallback font on the classpath, so the harness uses it. The parity test
now compares the harness scene to the real one live, both headless, rather than to a number
recorded from a desktop run; today they are the same code, and the test is what keeps them so.

**Every frame is fenced.** Option 2 said the driver drives `scene.update(dt)` "in a tight loop".
Left at that, the actor thread runs concurrently with the driver's next frame exactly as it runs
concurrently with the render thread in the game, and any draw on the driver thread lands at a
wall-clock-dependent point in the actor thread's sequence. At the tag the actor thread parks in
three places: on its own monitor between turns (`…/actors/Actor.java:318`), on the sprite of
the character it is about to process while that sprite moves (`:274-282`), and, under the
gravity-chaos curse, on each moving sprite in turn from the buff's own act
(`…/actors/buffs/GravityChaosTracker.java:76-86`). `SceneStepper` checks before each frame that the
thread is parked on its own monitor or on a moving sprite it is about to hold, comparing what
the JVM reports the thread waiting on with those objects, and that the frame which called
`wait` is one of the three sites, so that a fourth site in some upgrade fails by name instead
of running unfenced; holds the actor thread's monitor and every moving sprite's monitor across
the frame; reads the thread's state at the end of the frame while the monitors are still held,
where a thread that the scene's notify or a movement's `notifyAll` reached is blocked on one of
them, which is the scene's own wake rule read rather than repeated; if it was woken, polls the
JVM's count of the thread's waits until it has parked again at one of the three sites, since
HotSpot counts a park on any lock as a wait and a turn that logs can block briefly on the
console's lock on its way to its monitor; checks that count against what it must be during,
across and between frames, so that a JVM reporting states differently fails a step rather than
opening the fence; and then acquires and releases the monitors the thread
released when it parked, so that what it wrote is visible between frames by the language's
rules. It also starts the thread itself before the first frame, where the scene
would otherwise start it mid-update, which moves the hero's first turn ahead of the first frame
and changes nothing else. The actor thread thus runs only between frames, which
`FenceInvariantTest` observes directly with and without the curse, and two Runs of one seed and
one action list replay to the frame, which `SceneDrawParityTest` asserts. One private static is
reached by reflection for this, `GameScene.actorThread`, written once per scene; a hook was not
spent because that changes nothing the game computes, `HarnessReflectionTest` confines
reflection in `harness` to that one class and checks the name against `docs/UPSTREAM.md`,
`HarnessPackageAnchorTest` keeps every harness class file out of upstream's packages, and row 4
is where the field moves if an upgrade renames it.

**What the story could not make deterministic, recorded for stories 1.15 and 1.16.** The game
orders actors, mobs and weighted choices by `HashSet` iteration, which follows identity hash
codes; the harness test task pins them (`-XX:hashCode=2`) until row 6 lands. The first Run in a
process draws once more than later ones, because `WindParticle`'s static initializer draws
(`…/effects/particles/WindParticle.java:41`); a Rig Run is its own process and pays it every
time, so it is a fixture concern, not a reproducibility one. And generating a floor twice from
one seed does not give the same floor, because the entrance room places the guidebook from a
generator pushed without a seed (`…/levels/rooms/standard/entrance/EntranceRoom.java:103-118`);
that is `docs/rules/rng.md`'s known row, and the determinism story's to settle.
