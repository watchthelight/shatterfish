---
story: 1.3
key: 1-3-the-headless-graphics-stub-and-the-scene
title: The headless graphics stub and the scene
epic: 1
issue: 16
status: review
created: '2026-09-04'
updated: '2026-09-04'
---

# Story 1.3: The headless graphics stub and the scene

As the engineer,
I want a scene that behaves like the game's own without a graphics context,
So that sprite callbacks fire and turns resolve.

Paths abbreviate `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/` as `…/`, and every
line number is at the pinned tag `v3.3.8` (commit `7b8b845a`), not in the hooked working tree.

## Acceptance criteria and how each was met

| Criterion | Outcome |
|---|---|
| The binding is installed before any texture class initializes, and atlases load through the image path that needs no graphics context | **Met.** `HeadlessBoot` installs the no-op binding as its second step, before any game class that builds a texture is touched, and `HeadlessScene`'s constructor refuses to exist unless `NoOpGL.isNoOp(Gdx.gl)` holds, so the order is a property of the code rather than of which test ran first. `HeadlessSceneTest` asserts both, and asserts the level's tile atlas was read as a `Pixmap` with a size. The `Texture` class's own statics turned out to be compile-time constants (`SPD-classes/…/glwrap/Texture.java:34-39`), so class initialization never touches GL; every constructor does (`:47`) |
| The scene creates the same groups, sprites and emote icons the real scene creates, so that actor-thread random draws match an Overlay Run | **Met by identity, and the criterion turned out to be too weak.** `HeadlessScene` is a `GameScene` whose `create()` is `GameScene.create()`. The story found that the groups are not what matters: the statics game code calls are gated on the package-private `GameScene.scene`, and several carry game logic (a mob spawned during play is registered as an actor only when a scene exists). See *What the scene decision missed* |
| `SceneDrawParityTest` asserts the draw counts: a scripted sequence consumes the same number of random draws headlessly as the same sequence consumes with the real scene, without a graphics context in the test | **Met, live rather than against a recorded number.** Both scenes are created and stepped headlessly in the same test, and the comparison is a fingerprint rather than a count: draws during creation and during the script, every Input wait, every frame, the hero, every mob, the heaps, the whole game log, and the generator's next value. A second test runs the same scene twice and asks for the same fingerprint, which is the determinism the fenced stepper promises, and `FenceInvariantTest` observes the fence itself |
| No story after this one adds a guard to actor or item code | **Met.** This story adds no hook and no upstream edit at all; the diff budget in `docs/UPSTREAM.md` is unchanged. Everything runs through the real scene, so there is nothing left to guard |

Also delivered, owed from story 1.2: the runtime vanilla branches of the two `cellSelector` sites of hook row 5 (`selectCell`, `resetKeyHold`), exercised by `HeadlessSceneTest` against the real `CellSelector`.

## What was built

- `shatterfish/harness/src/main/java/org/shatterfish/harness/boot/`: `HeadlessBoot` (the backend with its loop disabled, the no-op binding, settings in memory with the intro off, the version numbers stamped from the root build script, the `Game` instance, a profile directory, the language, a camera; one per process), `HeadlessGame` (the `Game` whose pending scene switch is cleared and which switches to a scene the driver constructed, through the game's own `switchScene()`), `HeadlessPlatformSupport` (no display, no network, FreeType text exactly as the desktop build renders it), `MemoryPreferences` (the game's settings, never written anywhere).
- `shatterfish/harness/src/main/java/org/shatterfish/harness/scene/`: `NoOpGL` (moved from the spike, now public, with `isNoOp`), `HeadlessScene`, `SceneStepper`.
- `shatterfish/harness/src/main/resources/org/shatterfish/harness/boot/upstream.properties`, filled by `processResources` from `appVersionName` and `appVersionCode`.
- `shatterfish/harness/src/test/java/org/shatterfish/harness/`: `HarnessReflectionTest` (reflection into upstream confined to `SceneStepper`, one lookup and one opening, its reach checked against `docs/UPSTREAM.md`, with a bite test), `HarnessPackageAnchorTest` (every class compiled into harness lives under `org.shatterfish.harness`, so none can share an upstream package and its package-private members); under `scene/`, `SceneDrawParityTest`, `HeadlessSceneTest`, `FenceInvariantTest`, and two fixtures, `FreshRun` (the game's own start and continue paths) and `DrawCounter` (a counting generator on the game's stack).
- `shatterfish/harness/build.gradle`: `gdx-freetype` and its natives; the desktop module's assets on the runtime classpath (the fallback font lives there); the version stamp; `-XX:hashCode=2` for the test JVM.
- `HeadlessDriver` boots through `HeadlessBoot` and no longer exits; story 1.4 puts the loop in it. `HeadlessGame` refuses to switch or destroy a scene whose actor thread is alive, because `GameScene.destroy()` interrupts such a thread and waits for it but never asks it to finish (`…/scenes/GameScene.java:768-777`, `:796-806`), leaving an orphan the next scene finds alive.
- `HooksVanillaTest`'s inert-sites check now nulls the `CellSelector` it is about and restores it, so it checks the guard it names even after a scene test ran in the same JVM.
- The story 1.1 spike (`HeadlessTurnSpikeTest`, the spike `NoOpGL`) is deleted, as stories 1.3 and 1.4 were told to. Its boot recipe is `HeadlessBoot`; its claim that a turn resolves without a renderer is `HeadlessSceneTest.a_turn_resolves_without_a_renderer`, now against the real scene.
- Docs: ADR-0015 amended; `docs/UPSTREAM.md` row 5, and a paragraph on the harness's reflective reach and the package anchor; four rows in `docs/rules/game-loop.md` and three in `docs/rules/rng.md`; `docs/architecture.md`.
- `Ledger.git()` passes `--no-textconv`, from the second fairness review of story 1.2, which arrived during this story.

## What the scene decision missed

ADR-0015 chose "a `Scene` subclass that creates the Groups `GameScene` creates", on the reasoning that sprite and emote constructors draw random numbers. Reading `GameScene`'s statics before writing a line showed that the groups are the smaller half of the problem. `GameScene.scene` is package-private and assigned only inside `GameScene.create()` (`…/scenes/GameScene.java:159`, `:242`); every static game code calls is gated on it; and these are not all drawing:

- `add(Mob, float)` registers the mob as an actor and links its sprite only when a scene exists (`:1153-1161`). Under any other scene a spawned mob is added to `level.mobs` and never acts.
- `add(Heap)` marks a heap as not counting for the exploration bonus only then (`:1131-1136`).
- `show(Window)` shows a window only then (`:1352`), which is the Prompt mechanism story 1.4 needs.
- `effectOverFog` and `addSprite` dereference the scene with no guard at all (`:1185-1186`, `:1149`).

And `create()` itself is level-entry logic: items dropped down the stairs land here, journal landmarks are noted here, the first log lines are written here. A scene that mirrored the groups would have had to mirror this too, which non-negotiable 4 forbids, and would still not have been `GameScene.scene`.

So the question became whether the real scene constructs headlessly, which the ADR never asked. It does: the whole of `GameScene.create()` and `update()` run under the no-op binding, with three things supplied, a `PlatformSupport` (the desktop one is behind LWJGL), FreeType and its natives for text, and the desktop module's fallback font, which the scene's font setup opens unconditionally and which is not in `core`. The parity test then compares the harness scene to the real one live, both headless. Today they are the same code. The test is what keeps them so when someone is tempted to make the headless scene cheaper.

The decision is recorded as an amendment to ADR-0015 rather than a new ADR, because the ADR's choice (a harness-owned scene, real sprites, no-op GL, Pixmap atlases, zero edits to actor or item code) stands; only the shape of the scene changed.

## The loop, and what determinism cost

ADR-0015 said the driver drives `scene.update(dt)` "in a tight loop". The story found that this reproduces the game's own concurrency: the scene starts the actor thread and notifies it from `update()`, after which the thread runs concurrently with the next frame, and any draw made on the driver thread lands at a wall-clock-dependent point in the actor thread's sequence. In the game that is a known problem for the Overlay (ADR-0007, option 15). Headlessly it would have made the same seed and the same actions replay differently, which no Rig can be built on.

At the tag the actor thread parks in exactly three places: on its own monitor between turns (`…/actors/Actor.java:318`); on the sprite of the character it is about to process, while that sprite's movement animation runs (`:274-282`); and, under the gravity-chaos curse, on each moving sprite in `Actor.chars()` order from the buff's own `act()`, which is not a character's (`…/actors/buffs/GravityChaosTracker.java:76-86`). `grep '\.wait('` under `core` and `SPD-classes` finds those three and one more, `GameScene.waitForActorThread` (`…/scenes/GameScene.java:796-806`), which is the render thread waiting on the actor thread from `destroy()` and `onPause()`; the stepper must never reach it inside a frame, because `Object.wait` releases a reentrant hold in full. `SceneStepper` fences every frame against all three. Before the frame it requires the thread to be parked, on its own monitor or on a moving sprite it is about to hold, predicting the sprite from the sites above and comparing class and identity with the object the JVM reports the thread waiting on, so that a fourth site in some upgrade fails by name rather than running unfenced. It holds the actor thread's monitor across the frame, so the scene's own notify cannot let the thread run before the frame ends, and every moving sprite's monitor, so a movement ending inside the frame cannot either, whichever sprite the thread waits on. At the end of the frame, with the monitors still held, the thread's state says whether anything woke it: a thread the scene notified or a movement released is blocked on a monitor the stepper holds and reads as `BLOCKED`; one that was not is still `WAITING`. That is the scene's own wake rule and the game's own movement release, read rather than repeated, throttle and all. If it was woken the stepper releases the monitors and polls the JVM's count of the thread's waits (`ThreadInfo.getWaitedCount`) until the thread has entered its next wait, the one signal that distinguishes "still parked" from "notified and not yet running". Then it acquires and releases the monitors the thread released when it parked, its own and every character's sprite, so that what the thread wrote is visible to whoever reads game state between frames by the language's rules and not by the hardware's. Two checks keep the promise honest on a JVM that does not report states the way HotSpot does: a thread that parks between two frames without the stepper having waited for it fails the next step, and so does a thread that is anything but parked or blocked at the end of a frame. The thread is also started by the stepper before the first frame, where the scene would otherwise start it in the middle of the first `update()`.

Four of those pieces were found by failure:

1. Without the sprite fence, movement releases let the thread run mid-frame. Every parity run diverged.
2. With the wake decision made after the sprite monitors were released, the reading could be of a thread mid-turn, and a notify the scene never sends could wake it a second time; the hero then acted during the next frame and its trample rolls interleaved with the frame's emitter draws. One run in about twenty produced a dewdrop the others did not.
3. With the thread created by the scene mid-frame, one run in about a hundred and eighty reached its second Input wait two frames late. The stepper now starts the thread itself, mirroring `…/scenes/GameScene.java:866-882`.
4. With the fenced sprite derived from `Actor.current` alone, the gravity-chaos buff's wait site was unfenced: its `current` is the buff, not a character. The first fairness review found and demonstrated this (below).

A fifth was found by the second review without a failure: the wake rule, repeated from `GameScene.update()`, had no copy of the scene's `notifyDelay` throttle, so at frame times below 1/60 s the stepper woke the thread when the game would not. Reading the thread's state at the end of the frame replaced the copy; the reviewer measured that reading against the copy over 340 frames at 0.2 s with no disagreement, and so did a scratch probe here.

## The fairness reviews

The first review returned BLOCK on one finding and demonstrated it: a `GameScene` subclass sampling the actor thread's wait count, state and `Actor.current` across `super.update()`, stepped by the stepper, showed no violation in a control run of 124 frames and 52 violations in 267 frames once `GravityChaosTracker` was appended to the hero, the first at frame 4 with the thread `RUNNABLE` at the end of `update()`. The fix is the one described above, and the reviewer's probe is now `FenceInvariantTest`, with the curse and without.

The reviewer's proposed fix, hold every moving sprite and treat any of them stopping as the release, was taken for the hold and not for the release. A sprite nobody waits on can stop moving, a mob's while the hero attacks for instance, and waiting for the thread to park after that would spin until the timeout. So the stepper still predicts the one sprite the thread waits on, now for the buff's site too, and verifies the prediction against the JVM's lock information rather than trusting it.

The review's other findings, each taken:

- Every `path:line` had been cited from the hooked working tree, where row 5's hook shifts everything after `GameScene.java:1165`. All citations in code, docs and this file now name the tag; the anchors in `docs/rules/game-loop.md` were wrong by the same shift.
- `docs/UPSTREAM.md` had said the harness's reflection "changes nothing" and "reads only", while the stepper writes `GameScene.actorThread`. The paragraph now describes the write as the behaviour-preserving reordering it is, and lists what tests reach as well.
- The precedent was bounded by prose only. `HarnessReflectionTest` now confines reflection in harness main code to `SceneStepper`, asserts the fields it reaches are exactly the two the ledger names, and bites.
- `awaitPark` returned on wait entry, before the monitor was released, so reads between frames had no happens-before with the actor thread's writes; the stepper now round-trips the monitors.
- `HooksVanillaTest`'s inert-sites check ran against a real `CellSelector` after any scene test; it now nulls and restores it.
- The parity script consulted `Actor.findChar` on cells out of view and broke ties by actor id, neither of which a player sees; it now consults occupancy only in view and breaks ties by position.
- `Music.playTracks` draws once per track on every scene creation headlessly, because the mock player never reports playing, while the desktop build skips the draws for an unchanged list; recorded in `docs/rules/rng.md` for E5.
- The identity-hash pin's wording claimed insertion order; it now claims determinism, which is what holds.

The second review passed the fence, with its own probe of 340 fenced frames finding no frame where the thread was woken and not waited out or waited out without being woken, and asked for six things, all taken:

- The repeated wake rule, above, replaced by reading the thread's state; the rule is no longer a second implementation that can drift.
- The wait-site prediction checked by identity as well as class, which is exact outside the test JVM's identity-hash pin, where every identity hash is the same value and the class remains the only discriminator. The review also showed the prediction can be wrong in a Rig process, because `Pushing`'s render-thread callback under this very curse can spawn a mob and so change `Actor.chars()` between the buff's copy of it and the stepper's; with the state read deciding the release, the prediction is no longer load-bearing, which is the right role for it.
- `isMoving` read under the sprite's monitor and the round trip after each frame covering every character's sprite, so the happens-before argument holds by the language rather than by HotSpot's fences.
- `trySetAccessible` added to the reflection rule, and the stepper bounded to one field lookup and one opening.
- The route that needs no reflection closed: a harness class declared in an upstream package would share its package-private members. `HarnessPackageAnchorTest` keeps every class compiled into harness under `org.shatterfish.harness`.
- The fourth `Object.wait` site named, in `docs/rules/game-loop.md` and in the stepper, and `HeadlessGame` made to refuse a scene switch or teardown while the actor thread is alive, which would orphan it mid-turn. The list of what tests reach reflectively completed.

## Things the game does that a Run must control, found on the way

Each of these is in `docs/rules/` with a citation, and each cost a diagnosis:

- **Identity hash order.** The game orders actors, mobs and weighted choices by `HashSet` iteration; HotSpot assigns identity hashes from a per-thread random state; two runs of one seed in two JVMs are two different games. This is ledger row 6, story 1.16's. Until it lands, the harness test JVM runs with `-XX:hashCode=2`, which pins every identity hash and makes hash order deterministic. `SceneDrawParityTest` asserts the pin is in effect, so a dropped flag fails rather than flakes. Story 1.16 deletes both. It is the test JVM only; `HeadlessDriver` and a Rig process do not carry it.
- **A static initializer draws.** `WindParticle`'s wind angle is drawn when the class is first used, once per process (`…/effects/particles/WindParticle.java:41`). The first Run in a process draws one more than every later one. A Rig Run is its own process and pays it every time; in one process it shifts every comparison by one, so the parity fixture throws its first Run away. It is the only static initializer under `core` that draws.
- **The guidebook is placed from an unseeded generator.** `EntranceRoom.placeEarlyGuidePages` pushes a generator with no seed, on purpose, so that meta-progression does not perturb the rest of the layout (`…/levels/rooms/standard/entrance/EntranceRoom.java:102-118`). Generating a floor twice from one seed does not give the same floor. `docs/rules/rng.md` already had the row; this story met it. The parity fixture generates once and every compared Run resumes the saved game through the game's own continue path (`…/scenes/InterlevelScene.java:733-745`). The determinism story owns the real answer.
- **The journal is loaded once per process, and level generation reads it.** Which guide pages are found decides what the first floors drop (`…/levels/RegularLevel.java:561-575`); `Journal.loadGlobal()` returns early after its first call, and so does `Badges.loadGlobal()`. The fixture resets both and reloads them from a fresh profile, which is what the hero-select screen does before every game (`…/scenes/HeroSelectScene.java:106-107`). Story 1.15 owns the Profile.
- **Save slots are remembered per process.** `GamesInProgress` caches which slots it has seen occupied, so the slot a fresh profile's game lands in depends on what ran before in the JVM. The snapshot records its slot.
- **A click is accepted on the frame the hero becomes ready** because `Hero.ready()` reaches `selectCell`, which sets `cellSelector.enabled` on the actor thread (`…/scenes/GameScene.java:1556`). The script relies on it.
- **Music draws differ headless and rendered** (`SPD-classes/…/noosa/audio/Music.java:95-129`), found by the review; E5's routing row is where it goes.

## Decisions taken inside the story

**The scene is the real one.** Alternatives: (a) a `Scene` subclass mirroring the groups, as the ADR wrote, plus reflection to make it `GameScene.scene` and to fill the private fields its statics use (about twenty of them, some needing text, so fonts anyway); (b) a hook making `scene` settable or adding a listener for the guarded statics (a row, and still a mirror of `create()`); (c) `HeadlessScene extends GameScene` with `create()` inherited. Chosen (c): zero upstream edits, no second implementation, and the parity test becomes a live comparison. Pre-mortem: the real scene may be too slow for the throughput target. Not measured here (story 1.21); the parity suite runs five Runs of sixty waits in under a second on this machine, which is not a benchmark but is not a warning either.

**Determinism by fencing, not by hooks.** Alternatives: (a) leave the thread free and accept wall-clock interleaving headlessly, as the game does; (b) a hook in `GameScene.update()` handing the actor thread to the driver; (c) fence each frame from outside with the thread's own monitors and the JVM's wait count. Chosen (c). It costs two reflective touches of private statics (`GameScene.actorThread`, written once per scene; `Actor.current`, read per frame) and no row; `HarnessReflectionTest` keeps it at two. Pre-mortem: an upstream rename breaks it; the failure is immediate and names the field, and row 4 is where it moves. An upstream that adds a fourth wait site is the other risk; the JVM lock check turns it into a named failure rather than a silent race.

**Identity hashes pinned in the test JVM.** Alternatives: (a) shorten the parity comparison to the window before mob turns begin, where hash order cannot matter; (b) implement row 6 now; (c) pin the hashes and assert the pin. Chosen (c): (a) would make the test thin and (b) is a story of its own with a hook. The pin is named in `build.gradle`, in the test, in ADR-0015 and here, with story 1.16 as the owner of its removal.

**The parity fixture resumes a saved floor.** Alternatives: (a) generate per Run and accept that the guidebook moves, which makes the comparison meaningless; (b) neutralize the unseeded generator by reflection; (c) generate once, snapshot the profile, and resume through the game's continue path. Chosen (c): it is the game's own path and it is robust against every level-generation nondeterminism at once.

## Evidence

`./gradlew build -Pshatterfish.mobile=off`: green, 50 tests across 12 suites. `mkdocs build --strict`: clean.

The reference fingerprint for the committed seed and script, from the real `GameScene` and matched exactly by `HeadlessScene` three times over: 72 draws to create, 4065 draws during 60 Input waits over 352 frames, and every listed field equal. The absolute numbers belong to the floor a JVM generated, because the guidebook cell is unseeded and the parity fixture generates once per JVM; another JVM's numbers differ while its runs agree with each other, which is the property the test asserts. Eighty further runs of the same script across four JVMs, in a scratch probe that compared per-wait state, all matched their reference. `FenceInvariantTest`: no violation across every frame of a 60-wait run, with the gravity-chaos curse (its wait site reached) and without.

**Mutation battery.** Run on the committed tree, each mutation applied, its tests run with results cleared first and Gradle's exit code checked, then restored with `git checkout` and the tree verified clean. Repeats are for mutations whose effect could be intermittent.

| Mutation | Result |
|---|---|
| M1 `HeadlessScene.create()` draws one extra number | parity fails: real 72, headless 73 |
| M2 `HeadlessScene.update()` skips every third frame | parity fails; `HeadlessSceneTest` passes (it counts calls, which still happen) |
| M3 `-XX:hashCode=2` removed from the test task | `SceneDrawParityTest` fails at class setup with the pin message |
| M4 no sprite monitors held | the stepper itself fails, two of two: "the actor thread was RUNNABLE at the end of frame 3, so it ran during it" |
| M5 the thread's state read after the fence is released | the stepper itself fails, three of three, the same way. This was the race the earlier battery caught one time in three; deciding from the state inside the fence made it a deterministic failure |
| M6 the first frame runs before the actor thread first parks | fails, two of two: "not parked at the start of a frame" |
| M7 the wait-site check trusts any sprite | passes. **Not demonstrated, and expected**: the check is a tripwire for a wait site the stepper does not know, and every site at the tag is known; the release decision no longer depends on it |
| M8 no warm-up Run | replay test fails: the first Run has 73 creation draws |
| M9 `NoOpGL.isNoOp` accepts any binding | `HeadlessSceneTest` fails: a scene constructs without the binding |
| M10 `selectCell` guard swallows the listener (an upstream edit) | `HeadlessSceneTest` fails twice, and `HooksLedgerTest` fails on the digest |
| M11 `HeadlessScene` is not `GameScene.scene` | `HeadlessSceneTest` fails three times (no actor registered, no click accepted, no turn) and parity fails |
| M12 no wait for the park after a wake | the stepper itself fails, two of two, at the next frame |
| M13 a second harness class reflects into upstream (`trySetAccessible`) | `HarnessReflectionTest`'s rule fails, naming the class |
| M14 the stepper reaches a third field the ledger does not name | `HarnessReflectionTest` fails: the reach and the declared set differ |
| M15 the stepper looks a field up in a second place | `HarnessReflectionTest` fails: two lookups |
| M16 a harness class declared in `com.shatteredpixel.shatteredpixeldungeon.scenes` reads `GameScene.scene` | `HarnessPackageAnchorTest` fails, naming the class |

## Deviations

- The epics text says the parity test "does not require a graphics context in the test", which read as comparing against a recorded number. Both scenes run in the test instead. That is stronger and is what the ADR amendment records.
- The story was told to inherit story 1.1's boot recipe and not its readiness polling. `SceneDrawParityTest`'s script reads `hero.ready` between frames to decide when to click. It is a test script standing in for story 1.5's Input-wait detection, and it reads the flag only while the actor thread is parked and after the stepper has published its writes, which is not the race ADR-0015 rejected; but it is polling, and story 1.5 replaces it.
- The manual `:desktop:debug` check was not run. This story edits no upstream file.

## Known limitations, handed forward

- **The pin.** `-XX:hashCode=2` makes the harness's tests exact and the game's own iteration order deterministic. It is not a property of a Rig Run, which will need row 6.
- **Reflection into upstream.** Two private statics are reached, from one class, and a rule holds it there; a second rule keeps every harness class out of upstream's packages. The ledger still cannot see either route; `docs/UPSTREAM.md` says so and names both fields, and the tests check the names and the packages.
- **The first turn runs before the first frame** rather than during it. The only visible difference is when the hero's first `observe()` happens relative to the first frame's draws, and it draws nothing itself. An Overlay Run will have the game's order; story 1.5 and E5 should know.
- **The wait-site prediction is knowledge of the tag.** Three sites are known and checked. An upgrade that adds one fails a step by name; the fix is to teach the stepper the new site, which is a reading of upstream, not a hook. The prediction can be wrong in a Rig process in one constructed case (a spawn from a render-thread callback under the gravity-chaos curse reordering `Actor.chars()`); it decides nothing, and the identity comparison then names the mismatch.
- **The fence reads HotSpot's reporting.** That a notified thread reads `BLOCKED` on the notifier's side is HotSpot behaviour, not a language guarantee. The two self-checks in the stepper turn a JVM that reports differently into a failure at the next frame rather than a silent race.
- **The fixture, not the game, makes floors repeatable.** A Rig Run generates its own floor and will meet the unseeded guidebook generator; the determinism story decides whether to seed it (a hook) or to accept that floors 1 and 2 vary by one heap.
- **Throughput is unmeasured.** The real scene updates its whole UI every frame.

## Follow-ups for later stories

- Story 1.4 lifts `FreshRun.start` into the driver and builds the loop on `SceneStepper.step()`; its Prompt window criterion now has `GameScene.show` reachable.
- Story 1.5 replaces the script's `hero.ready` polling with the observe-site notification.
- Story 1.15 owns what `FreshRun.forgetTheLastProfile` does by hand.
- Story 1.16 removes `-XX:hashCode=2` and the assertion that guards it; that removal is its proof.
- The determinism story meets `EntranceRoom.placeEarlyGuidePages` and `WindParticle`; E5's routing row meets `Music.playTracks`.
