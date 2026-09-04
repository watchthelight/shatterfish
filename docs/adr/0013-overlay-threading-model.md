---
status: proposed
date: 2026-09-03
deciders: watchthelight (product owner), Claude (engineer)
---

# ADR-0013: Threading model for the Overlay and the drivers

## Context and problem statement

The Overlay runs the Brain inside the real desktop game and must never block the render thread
or touch game objects off their thread (PRD FR-12, FR-37 to FR-40, NFR-4). Session 10 read the
threads: game logic runs on a dedicated "SHPD Actor Thread" created by `GameScene.update()` and
notified from the render thread at most 60 times per second (`…/scenes/GameScene.java:826-828`,
`:865-888`); the hero parks that thread by returning `false` from `act()` after `ready()`
(`…/actors/hero/Hero.java:863-881`; `…/actors/Actor.java:304-322`), and `ready()` is re-run on every
wake-up while waiting; a click is delivered on the render thread by `CellSelector.select`, which
calls `Hero.handle(cell)` then `hero.next()` to wake the actor thread
(`…/scenes/CellSelector.java:152-171`; `…/scenes/GameScene.java:1750-1756`); `Actor.process()` waits
on moving sprites and turn resolution ends in animation callbacks fired from the render thread
(`…/actors/Actor.java:274-286`; `…/sprites/CharSprite.java:824-862`); windows are shown through
`Game.runOnRenderThread` after the hero is ready (`…/actors/hero/Hero.java:1019-1035`); `GameScene.update`
is `synchronized` on the scene and the actor thread takes the same lock to add sprites
(`…/scenes/GameScene.java:838`, `:1054-1066`); the project's own 2020 deadlock came from the two threads
sharing monitors (research §7). Decide which thread does what, and how the three roles hand off.

Non-negotiables touched: #4 (in-process), #6 (native UI), #1 (the Brain holds no game object).

## Decision drivers

- The game keeps its frame rate while the Brain thinks (NFR-4).
- No new lock is taken on a game object; no thread waits on another thread's future on the
  render or actor thread.
- One code path for headless and embedded: the driver contract of the spine.
- Human takeover at any Input wait without desync (FR-40).

## Considered options

1. Brain on the render thread. Rejected: a Decision that takes longer than a frame drops frames;
   NFR-4.
2. Brain on the actor thread (hook into `Hero.act`). Rejected: the actor thread holds the sprite
   waits and the scene lock; a slow Decision stalls animations and invites the 2020 deadlock.
3. **Three roles: the game's actor thread, untouched; the UI-role thread (the render thread in the
   Overlay, the driver thread headless), the only thread that observes, executes Actions and
   writes the Panel; a single-thread brain worker that receives an immutable Observation and
   returns a Decision through a future the UI-role thread polls once per frame.** Chosen.
4. A dedicated Shatterfish driver thread in the Overlay that marshals to the render thread with
   `runOnRenderThread`. Rejected: an extra hop for every read, and the render thread is already
   the game's own UI-role thread (input handling and window display run there).
5. The brain worker posts its Action to the render thread itself. Rejected: the worker would
   hold a reference into the scene; the worker returns a value and nothing else.
6. Blocking the render thread on the worker with a timeout (the thinking budget). Rejected: a
   budget overrun would freeze the frame; the budget is a label (`THINKING`), never a wait.

## Decision outcome

**Roles**

| Thread | Owner | Does | Never |
|---|---|---|---|
| Game actor thread | upstream | `Actor.process`, hero and mob turns | runs Shatterfish code except the notification hooks (non-blocking volatile writes) |
| UI-role thread | render thread (Overlay) or the driver thread (headless, replay) | detects Input waits, `RngControl.reseed(salt, k)`, `Observer.observe()`, hands the Observation to the worker, polls the future, `ActionExecutor.execute`, writes the Panel and the Run log | blocks on the worker; holds a game object across a frame |
| Brain worker | `harness.driver` (one per driver) | `Brain.decide(Observation, Belief)` or `Brain.update(Observation, Belief)` for human turns | touches a game object, the scene, or the log file |

**Input-wait detection**: the hook of ADR-0008 row 3 fires from `Hero.ready()` on the actor
thread the first time the hero becomes ready (the branch that calls `Dungeon.observe()`,
`…/actors/hero/Hero.java:935-946`), setting a volatile `waitPending` flag; the UI-role thread
consumes it at its next frame (Overlay) or loop iteration (headless) and confirms the condition
of AD-5 (hero ready; no window or a Prompt window) before observing. While the hero is parked
the actor thread only re-runs `ready()`, whose writes are idempotent, so the reads are as safe
as the game's own HUD reads.

**Per Input wait on the UI-role thread**: `k++`; `RngControl.reseed(salt, k)`;
`obs = Observer.observe()`; `snapshot` (Overlay, for Take over and Pause); submit
`decide(obs, belief)` (bot turn) or `update(obs, belief)` (human turn) to the worker; write the
`wait` record when the Action is known.

**Executing**: `ActionExecutor.execute(action)` on the UI-role thread does what the UI does:
`Hero.handle(cell)` then `hero.next()`, `Item.execute(hero, action)`, `Hero.rest`, `Hero.search`,
or a Prompt window's button; it asserts the thread and validity first (AD-4).

**Speed modes** (EXPERIENCE.md, Stepping model): the Decision future is polled each frame; when it
completes, `Next Step` shows it and waits for the key; `Run N` and `Human play speed` schedule the
execution `interval` seconds later on the render thread's clock; `Fast as it can` executes on
the frame the future completes, and hook row 7 shortens the sprite motion interval so the actor
thread is not the ceiling. A budget overrun sets `THINKING` and nothing else; the computation is
never cancelled (AD-7).

**Modes**: `PAUSED` holds the Decision and drops hero input (the Panel installs its own
`CellSelector.Listener` that ignores cells while paused, and sets the toolbar and inventory pane
inactive through the accessor row); `HUMAN` restores the game's default listener and the panes,
records each human Action from `Hero.curAction` after `handle` and from the `Item.execute`
notification (hook row 3), and calls `update` at every Input wait; `Take over` and `Hand back`
apply at the next Input wait ("Hero busy" in EXPERIENCE.md). `RUN OVER` and level changes are
seen as the scene being destroyed and recreated (`InterlevelScene`), so the driver re-attaches
through the scene seam hook each time and keeps `k` across floors.

**Headless**: the driver thread is the UI-role thread; it drives `scene.update(dt)` with a large
`dt` until the wait flag is set, then runs the same per-wait sequence; there is no render thread,
so the worker may be the driver thread itself (synchronous `decide`) without changing the
contract.

**Deadlock rule**: Shatterfish code never takes the scene monitor or any game object's monitor;
the only cross-thread primitives are volatile flags, an immutable Observation handed to the
worker, and a future polled without blocking.

### Consequences

- Good: the render thread is never blocked; a slow Brain shows `THINKING` and the game keeps
  drawing.
- Good: headless and embedded share one per-wait sequence, so a Replay of an Overlay Run is the
  same code as a Rig Run.
- Bad: polling per frame adds up to one frame of latency per hand-off (about 17 ms at 60 fps),
  which is below the Human play speed's shortest interval.
- Bad: recording human Actions needs the `Item.execute` notification (hook row 3's second site).

## Pre-mortem

*If this is wrong in six months, why?*

- A game system mutates state on the render thread outside the actor thread's park (a window's
  button acting on the hero, an ankh resurrection). Mitigation: the Observer asserts the AD-5
  condition and observes again if a Prompt closed between detection and observation; such
  windows are Prompts by definition.
- `waitPending` fires for a wake-up that is not a new Input wait (the hero re-runs `ready()` 60
  times a second). Mitigation: the hook fires only on the `!ready` branch; the UI-role thread
  also checks that `k`'s Observation hash changed or an Action was executed since the last wait.
- The worker's Decision arrives after the human has taken over. Mitigation: a Decision is tagged
  with its `k`; a stale Decision is logged as skipped and never executed.
- `Fast as it can` starves the render loop. Mitigation: at most one Action per frame; the frame
  rate is the ceiling by design, and the Rig is the place for speed.
