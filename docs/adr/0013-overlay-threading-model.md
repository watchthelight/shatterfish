---
status: accepted
date: 2026-09-04
deciders: watchthelight (product owner), Claude (engineer)
---

# ADR-0013: Threading model for the Overlay and the drivers

## Context and problem statement

The Overlay runs the Brain inside the real desktop game and must never block the render thread or
touch game objects off their thread (PRD FR-12, FR-37 to FR-40, NFR-4). Session 10 read the
threads: game logic runs on a dedicated "SHPD Actor Thread" created by `GameScene.update()` and
notified from the render thread at most sixty times a second (`…/scenes/GameScene.java:826-828`,
`:865-888`); the hero parks that thread by returning `false` from `act()` after `ready()`
(`…/actors/hero/Hero.java:863-881`; `…/actors/Actor.java:304-322`); a click is delivered on the render
thread by `CellSelector.select`, which calls `Hero.handle(cell)` then `hero.next()`
(`…/scenes/CellSelector.java:152-171`; `…/scenes/GameScene.java:1750-1756`); turn resolution ends in
animation callbacks fired from the render thread (`…/actors/Actor.java:274-286`;
`…/sprites/CharSprite.java:824-862`); windows are shown through `Game.runOnRenderThread` after the
hero is ready (`…/actors/hero/Hero.java:1019-1035`); and the project's own 2020 deadlock came from
the two threads sharing monitors on scene-graph objects.

The session 12 reviewer gate found that the first draft's mechanisms did not hold: Input-wait
detection was placed in `Hero.ready()`, which the game calls on every wake-up, and PAUSED input
blocking was placed in a `CellSelector.Listener`, which `GameScene.ready()` reinstalls sixty times
a second and which `CellSelector.processKeyHold` bypasses entirely. Detection moves to ADR-0015;
input blocking becomes a hook; the rest is decided here.

Non-negotiables touched: #4 (in-process), #6 (native UI), #1 (the Brain holds no game object).

## Decision drivers

- The game keeps its frame rate while the Brain thinks (NFR-4).
- No new lock is taken on a game object; no thread waits on another thread's future on the render
  or actor thread.
- One per-wait sequence for headless and embedded, or a Replay of an Overlay Run is not the same
  code as a Rig Run.
- Human takeover at any Input wait without desync (FR-40), and every human input recorded or
  marked unsupported (FR-4).

## Considered options

1. Brain on the render thread. Rejected: a Decision longer than a frame drops frames (NFR-4).
2. Brain on the actor thread. Rejected: that thread holds the sprite waits and the scene lock; a
   slow Decision stalls animations and invites the 2020 deadlock.
3. **Three roles: the game's actor thread, untouched; the UI-role thread (the render thread in the
   Overlay, the driver thread headless), the only thread that observes, executes Actions and
   writes the Panel; a single-thread brain worker that receives an immutable Observation and
   returns a Decision through a future the UI-role thread polls once per frame.** Chosen.
4. A dedicated Shatterfish driver thread in the Overlay marshalling with `runOnRenderThread`.
   Rejected: an extra hop for every read, and the render thread is already the game's UI thread.
5. The brain worker posts its Action to the render thread itself. Rejected: the worker would hold
   a reference into the scene; it returns a value and nothing else.
6. Blocking the render thread on the worker with a timeout. Rejected: an overrun would freeze the
   frame; the budget is a label, never a wait.

**Blocking hero input while PAUSED**

7. The Panel installs its own `CellSelector.Listener`. **Rejected by the gate**: `Hero.ready()`
   calls `GameScene.ready()`, which calls `selectCell(defaultCellListener)`
   (`…/actors/hero/Hero.java:945`; `…/scenes/GameScene.java:1642-1643`, `:1552-1555`), so the game
   reinstalls its own listener on every wake-up and a paused click would run
   `hero.handle(cell); hero.next()` unlogged.
8. Deactivating the toolbar, inventory pane and quickslot buttons. Rejected as sufficient: each
   `Button` tests its own `active` (`…/ui/Button.java:117-125`), which a parent group does not set,
   and `CellSelector.processKeyHold` moves the hero without consulting any listener, forcing
   `enabled` and `Dungeon.hero.ready` true itself (`…/scenes/CellSelector.java:415-417`, `:464-480`).
9. **A hook: `CellSelector.select` and `CellSelector.processKeyHold` consult `Hooks.inputGate`
   before acting, and the Overlay's gate returns false while PAUSED.** Chosen (hook row 9 of
   ADR-0008). One gate, at the two places every hero-directed input funnels through.

## Decision outcome

**Roles**

| Thread | Owner | Does | Never |
|---|---|---|---|
| Game actor thread | upstream | `Actor.process`, hero and mob turns | runs Shatterfish code except the notification hooks, which are non-blocking volatile writes |
| UI-role thread | render thread (Overlay) or driver thread (headless, replay) | consumes the Input-wait flag, reseeds, observes, hands the Observation to the worker, polls the future, executes the Action, writes the Panel and the Run-log record | blocks on the worker; holds a game object across a frame |
| Brain worker | `harness.driver`, one per driver | `Brain.decide` and `Brain.update` | touches a game object, the scene, or the log file |

**Per Input wait**, on the UI-role thread, after ADR-0015's detection confirms the wait:
`k++`; `RngControl.reseed(salt, k)`; `obs = Observer.observe()`; snapshot (Overlay);
submit to the worker; poll; execute; write the record. In HUMAN mode the worker runs
`Brain.update` to keep the Belief current **and** a shadow `Brain.decide` whose Decision is shown
greyed on the card and logged as `shadow`, never executed; this is also what v2 coach mode needs.

**Decisions are tagged with their `k`.** A Decision that arrives for a `k` that is no longer
current (the human took over, or a level changed) is logged as skipped and never executed.

**Executing.** `ActionExecutor.execute(action)` on the UI-role thread does what the UI does, per
ADR-0014, and is the only Shatterfish caller of `Hero.handle` and `hero.next()`.

**Speed modes** (EXPERIENCE.md): the future is polled each frame; `Next Step` shows the Decision
and waits for the key; `Run N` and `Human play speed` schedule execution `interval` seconds later
on the render thread's clock; `Fast as it can` executes on the frame the future completes, with
hook row 7 shortening the sprite motion interval, and the Panel refreshes at most a few times a
second so it stays readable. A budget overrun sets `THINKING` and nothing else.

**Modes.** `PAUSED` drops hero input through the input-gate hook (option 9) and dims the controls;
`HUMAN` opens the gate, records each human Action from the executor's own notification sites, and
runs `update` plus the shadow `decide`; `Take over` and `Hand back` apply at the next Input wait.

**Recording human Actions.** `Hero.curAction` after `handle` covers movement, attack, interact,
pick up, open, buy, unlock and transitions; the `Item.execute` notification covers item use; and
the notification hook also fires for `Hero.rest`, `Hero.search`, talent and ability use, and a
window's own button (trade, chasm jump, subclass). Anything else a human does is written as the
`unsupported` record of ADR-0011 and ends Replay-verifiability from that `k` (FR-4).

**Run over.** Death is not a scene switch: `Hero.die` shows `WndResurrect` or reaches
`GameScene.gameOver()` (`…/actors/hero/Hero.java:2169-2176`, `:2256`). The Overlay treats the
game-over notification as the end of the Run, reads the cause from the game's own ranking record,
writes the `end` record, and disables every control but the Panel toggle. `WndResurrect` is a
recognised Prompt kind, so an Ankh choice is an `AnswerPrompt` like any other.

**Level changes** destroy and recreate the scene; the driver re-attaches through the scene-seam
hook, re-registers the Observer's log listener, and keeps `k`, the salt, the Belief and the Run
log across the boundary (ADR-0015).

**Save and resume.** On the game's save-and-quit the driver writes a `boundary` record carrying
`k`, the salt and the chain value; on resume through the launcher it re-attaches, continues `k`
and the chain from that record, keeps the same salt, re-plans from the current Observation, and
starts in PAUSED with speed mode Next Step. A save opened without the launcher is not an Overlay
Run and its log ends at the boundary.

**Headless.** The driver thread is the UI-role thread and owns the loop (ADR-0015); the worker may
be the driver thread itself, since no frame rate is at stake.

**Deadlock rule.** Shatterfish code never takes the scene monitor or any game object's monitor;
the only cross-thread primitives are volatile flags, an immutable Observation handed to the
worker, and a future polled without blocking.

### Consequences

- Good: the render thread is never blocked; a slow Brain shows `THINKING` and the game keeps
  drawing.
- Good: one per-wait sequence for both drivers, so an Overlay Run replays under the Rig.
- Good: the input gate is one place, so "PAUSED ignores hero input" is a check rather than a hope.
- Bad: polling per frame adds up to one frame of latency per hand-off, about 17 ms at 60 fps,
  which is below the shortest Human play speed interval.
- Bad: the shadow `decide` in HUMAN mode doubles the Brain's work during a takeover; it is off the
  render thread and the human is slower than the Brain.

## Pre-mortem

*If this is wrong in six months, why?*

- A game system mutates hero state on the render thread outside the actor thread's park.
  Mitigation: the Observer asserts the Input-wait condition and re-observes if a Prompt closed in
  between.
- The input gate is bypassed by a path neither `select` nor `processKeyHold` covers. Mitigation:
  the E5 story enumerates every caller of `Hero.handle` and of the hero-affecting buttons, and the
  fairness reviewer checks the list; a PAUSED Run whose log gains an unexplained Action fails the
  Replay test.
- The shadow Decision confuses the human because it is stale by the time they read it.
  Mitigation: it is tagged with its `k` like any Decision and greyed as advisory.
- `Fast as it can` starves the render loop. Mitigation: at most one Action per frame; the Rig is
  the place for speed.

## Amendment: story 1.19 (2026-09-16)

The roles above are asserted, and the deadlock rule is a test. Paths abbreviate
`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/` as `…/`, at `v4.0.0`.

**The UI-role thread is a claimed identity.** `UiRole`, in the driver package, holds the thread
that owns the role: `HeadlessDriver` claims it for the thread that starts a Run, in its
constructor, and releases it in `close()`; the Overlay's driver will claim the render thread the
same way (E5). A second claim while a Run holds the role is refused by name. The identity is the
thread object, never its name, so a foreign call cannot pass by being called the same thing.

**The ports ask on entry, before they read a game field.** `Observer`'s every read begins with
`UiRole.require("Observer.observe()")` and `ActionExecutor.execute` with its own; on any thread but
the owner they throw an `IllegalStateException` naming the port, the role, the owning thread and
the calling thread. The executor throws rather than refuses: a refusal is for a Brain's choice, a
wrong thread is a programming error. `ThreadConfinementTest` calls each port from a foreign thread
at a wait and holds the failure and its names, holds that the foreign calls changed nothing, that
the driver thread is the role from start to close, and that the role is released and not claimable
twice.

**No Shatterfish code takes a monitor on a game type, as an ArchUnit rule.** `MonitorConfinementTest`
reads each Shatterfish class's bytecode through ASM and holds that no `monitorenter` has an operand
whose static type is a game type, a class under `com.shatteredpixel` or `com.watabou` or a subclass
of one, and that no synchronized method sits on a class that is a game type. The operand's type is
what produced it, a field's declared type, a parameter's, a local's from the debug table, a call's
return type or the class of a `new`, followed back through javac's `dup; astore; monitorenter`; a
producer the analysis cannot type is a violation, never a pass. A Shatterfish object that
implements a game interface, the log listener on the game's signal, is Shatterfish's own object
and may be locked; a game object behind a variable declared `Object` passes by static type, which
is the rule's limit and is written beside it. The rule is shown to bite on fixtures that lock a
game field, a game parameter, a game local, a call's result and a game subclass's own method, and
to pass an own field, `this` and the listener.

**Two exemptions, each load-bearing.** `SceneStepper` holds the actor thread's monitor and every
moving sprite's across a frame: the fence of story 1.3, on purpose, so the frame and the actor
thread cannot interleave, and `FenceInvariantTest` holds that design; this record's deadlock rule
was written before that fence and this amendment reconciles them. `HeadlessScene` is the scene,
and the game locks its scene on the render thread, `GameScene.update` being synchronized
(`…/scenes/GameScene.java:867`) as are `erase` (`:967`) and `addMobSprite` (`:1087`), with the
actor thread taking `synchronized (scene)` (`:1098`); the override of `update()` keeps the game's
lock and `openWindow()` reads the member list under the lock `erase` writes it under. That is the
game's rule for its scene, not a monitor Shatterfish invented. The test holds that each exemption
would violate the bare rule, so neither can go stale unnoticed.

**The Brain holds no game object** by `BrainBoundaryTest`'s allowlist and its denial of
`org.shatterfish.harness..`, which this story names and does not repeat.
