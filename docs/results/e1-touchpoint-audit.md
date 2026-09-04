# E1 touchpoint audit: does a turn resolve without a renderer?

Story 1.1, [#14](https://github.com/watchthelight/shatterfish/issues/14). Upstream tag `v3.3.8`.
Measured on Windows 11, JDK 21 (Zulu), Gradle 9.4, libGDX 1.14.0 headless backend.

## The question

Turn resolution in Shattered Pixel Dungeon runs through sprite animation callbacks. A hero melee
attack calls `sprite.attack(cell)` and returns `false`; the damage, the time spent and the next
turn all happen in `onAttackComplete`, which only the animation's own update fires. If that path
cannot complete without a renderer, the headless harness of ADR-0015 is impossible and E1 needs a
different plan.

## The answer

**It resolves.** A Warrior on a generated sewer level, with no window and no graphics context,
attacked a sleeping marsupial rat: the damage landed, the hero became ready again, and actor time
advanced by one turn. The evidence is `HeadlessTurnSpikeTest`.

The shape that worked is the game's own: the actor loop on its own thread through
`Actor.process()`, and a second thread playing the render thread's part, updating sprites,
draining the posted-runnable queue and notifying the actor thread.

### What five runs actually produced

The test asserts three things, and all three held on every run. The *outcome* of the fight did
not, because combat rolls come from an unseeded generator.

| Run | Damage dealt | Rat | Damage landed on frame | Hero ready again | Actor time |
|---|---|---|---|---|---|
| 1 | 5 of 8 | alive | 4 | yes | 1.0 |
| 2 | 8 of 8 | dead | 4 | yes | 1.0 |
| 3 | 7 of 8 | alive | 4 | yes | 1.0 |
| 4 | 3 of 8 | alive | 4 | yes | 1.0 |
| 5 | 8 of 8 | dead | 4 | yes | 1.0 |

The rat dies in some runs and not others. Nothing in the test depends on which, and no claim here
should either.

## Hooks needed: one row, three sites

The budget is ten rows. This story needed **one row**, filed as row 5 under the corrected ledger
in [ADR-0016](../adr/0016-hook-ledger-corrected-by-story-1-1.md), whose reason is "let the actor
loop run with no `GameScene`". ADR-0008's original table partitioned row 5 by file rather than by
reason and had no home for the third site; that table is superseded rather than edited.

| Site | Reached from | `path:line` at `v3.3.8` |
|---|---|---|
| `GameScene.selectCell` | `Hero.ready()` on every Input wait | `core/.../scenes/GameScene.java:1557`, via `GameScene.ready()` at `:1656` and `Hero.ready()` at `core/.../actors/hero/Hero.java:945` |
| `GameScene.resetKeyHold` | `Hero.interrupt()` when an enemy becomes visible | `core/.../scenes/GameScene.java:1671`, via `Hero.interrupt()` at `core/.../actors/hero/Hero.java:955` |
| `GameScene.add(EmoIcon)` | `CharSprite.update()` for a sleeping mob | `core/.../scenes/GameScene.java:1164`, via `CharSprite.showSleep()` at `core/.../sprites/CharSprite.java:657` and `update()` at `:635-639` |

**Why none of the three could be avoided by constructing the object**, which is how the two
health indicators below were handled: `cellSelector` is `private static` and assigned only inside
`create()` (`core/.../scenes/GameScene.java:178`, `:368`), and `scene` is package-private
(`:159`), so no Shatterfish class can install either.

**Vanilla is unaffected.** `cellSelector` is never set back to null once assigned, and `scene` is
null only between `destroy()` and `create()`, when no sprite is updating. The real game launches
and plays with the guards in place (`./gradlew :desktop:debug`). That is an argument plus a manual
check, not a test; the vanilla-equivalence test is owed by story 1.2, which builds the registry
and the marker-counting test.

**The emote guard could have been a reproducibility bug and is not.** `EmoIcon`'s constructor
calls `GameScene.add(this)` at `core/.../effects/EmoIcon.java:46`, and `EmoIcon.Sleep` draws
`Random.Float` at `:89`, *after* the super constructor returns. Guarding the add therefore skips
no random draw, so a headless Run and an Overlay Run still consume the same stream. Had the draw
come first, this one-line guard would have silently desynchronised the two drivers and broken
ADR-0011's Replay.

## What the driver must do that is not a hook

Twelve setup steps, none of which touch upstream. They are the content of stories 1.3 and 1.4.

| What | Why, with the line that forces it |
|---|---|
| `HeadlessApplication` with `updatesPerSecond = -1` | Gives `Gdx.app` and `Gdx.files` while the backend's own loop body never runs, so the driver owns the loop |
| **Drain `Gdx.app.postRunnable` every frame** | With that loop disabled nobody else does. `Game.runOnRenderThread` posts there (`SPD-classes/.../noosa/Game.java:306-313`) and the game uses it to show windows from the actor thread; 52 sites in `core` use it |
| A no-op `GL20`/`GL30` before any texture is constructed | `Texture`'s constructor calls `glGenTexture` |
| `Game.instance`, via a subclass | `Game.scene()` and `Game.switchingScene()` dereference it with no null check (`SPD-classes/.../noosa/Game.java:222-228`); `HeroSprite.place` calls `Game.scene()` (`core/.../sprites/HeroSprite.java:107`) |
| `Game.requestedReset = false` | See the silent stall below (`SPD-classes/.../noosa/Game.java:64`, `:232-233`) |
| `Game.versionCode`, `version`, `density`, `width`, `height` | `versionCode` is stored as the save version by `Dungeon.init()`; `width` and `height` are read by `Camera.reset` (`SPD-classes/.../noosa/Camera.java:71-72`) |
| `Camera.main`, after those | `CharSprite.worldToCamera` reads `Camera.main` directly rather than the gizmo's own camera (`core/.../sprites/CharSprite.java:183-184`) |
| `new TargetHealthIndicator()` | `Hero.act()` → `checkVisibleMobs()` → `QuickSlotButton.target()` dereferences the static (`core/.../ui/QuickSlotButton.java:398`); the indicator sets it in its own constructor (`core/.../ui/TargetHealthIndicator.java:33-36`) |
| `new AttackIndicator()` | `Hero.ready()` calls `AttackIndicator.updateState()` (`core/.../ui/AttackIndicator.java:206`); same self-registering pattern (`:54-58`) |
| `FileUtils.setDefaultFileProperties` at a per-Run directory | This is the Profile of ADR-0007 (`SPD-classes/.../utils/FileUtils.java:42`) |
| `Messages.setup(ENGLISH)` | Otherwise the bundle follows the host locale and every display string in the Observation is locale-dependent |
| `PathFinder.setMapSize` and `Actor.clear()` | Before adding actors to a freshly built level |

The game's assets also have to reach the classpath. `core/src/main/assets` is not a resources
directory, so Gradle does not put it there; the desktop module adds it to its own run task and the
harness now does the same in its own build file, for both source sets. No upstream change.

## Three traps worth the whole exercise

**A silent stall, not an error.** `Game.requestedReset` starts `true` and is cleared only by
`Game.step()` when it creates the first scene. `Actor.process()` refuses to pick any actor while
`Game.switchingScene()` is true (`core/.../actors/Actor.java:251`). A driver that never calls
`step()` therefore gets an actor loop that parks forever, with no exception, no log line and no
clue. The field is `protected`, so the driver clears it by subclassing `Game`, not by editing
upstream. This cost more time than anything else in the story.

**Failures on the actor thread are invisible.** An exception there kills the thread and the Run
simply stops advancing. The driver must install an uncaught-exception handler and treat a dead
actor thread as a fatal Run error, or every such bug looks like a hang.

**`Actor.processing()` is not an idle signal.** It is `current != null`, and `current` stays set
while the thread is parked (`core/.../actors/Actor.java:234`, `:244-322`). A driver that waits for
it to go false waits forever.

## Against ADR-0015

| Assumption | Verdict |
|---|---|
| A harness-owned scene lets real sprite subclasses behave as in the game, with no edits to actor or item code | **Confirmed.** No actor or item file was touched |
| The driver thread can own the loop, with the headless application providing only the statics | **Confirmed, with a condition the ADR already stated**: the driver must drain the posted-runnable queue itself, which this spike does |
| A no-op graphics binding is enough for construction | **Confirmed**, and cheaper than budgeted: a dynamic proxy over the interface works, so the "about two hundred lines of stubs" is optional |
| Fast-forwarding with a large fixed step completes animations | **Confirmed.** The damage landed on the fourth update at 0.2 s per step, in every run |
| The scene must create sprites and emote icons as the game does, so draw counts match | **Not tested here.** This spike creates no scene at all: sprites live in a bare `Group`, and the guarded emote icon is constructed and then dropped unparented. The draw-count question is real and is story 1.3's to settle |

Nothing in ADR-0015 was refuted. One decision was **not exercised**: Input-wait detection. The
spike polls the hero's readiness, which is that ADR's rejected option 9, and it does so across
threads on non-volatile fields. Story 1.5 builds the decided option, a notification at the observe
site inside `Hero.act()`. **Stories 1.3 and 1.4 must not inherit the polling.**

## An owned risk handed to story 1.13

The `selectCell` guard means that headlessly, every targeted item use becomes a no-op: about
twenty-eight callers reach `GameScene.selectCell` (`core/.../items/Item.java:154`,
`core/.../items/wands/Wand.java:124`, `core/.../items/spells/TargetedSpell.java:46`, and others),
and with no cell selector installed none of them can open one. Story 1.13's criterion is that a
targeted item use "drives the game's own selector within the same Input wait". That story must
either install a real `CellSelector` through the accessor row of the ledger, or change its
criterion. It cannot be discovered late: it is written down here and in the story file.

## Incidental confirmation of ADR-0007

The five runs above used the same dungeon seed and produced five different combat results. That is
the unseeded base generator ADR-0007 describes, observed rather than argued, and it is why story
1.15 exists.

## What stories 1.3 and 1.4 inherit

The boot sequence, the no-op graphics binding, the update pump with its runnable drain, and the
actor-thread handshake. They must **not** inherit the hand-built level, the hand-placed mob, or
the readiness polling. The spike test is deleted when story 1.4 lands, and its value is this page.

## Also found

`./gradlew :desktop:run` has never worked: the `run` task does not set the version system
properties the launcher parses, so it dies in `DesktopLauncher.main`. The working task is
`:desktop:debug`. `CLAUDE.md` and `docs/tooling.md` said otherwise and are corrected.
