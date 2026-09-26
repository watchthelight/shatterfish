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
that owns the role: `HeadlessDriver.start` claims it for the calling thread before anything is
built, so a refusal leaves nothing behind, releases it again if the start fails, and releases it
last in `close()`, after the scene, the hooks and the listener are gone; the Overlay's driver will
claim the render thread the same way (E5). A claim by the thread that already holds the role is
idempotent, a claim while another thread holds it is refused by name, a thread that is not alive
cannot hold it, and only the owner may release it, so a stray release cannot make the owner's next
call fail as if no Run were live. The identity is the thread object, never its name; the messages
carry each thread's id beside its name, so two threads called `main` are told apart.

**The ports ask on entry, before they read a game field.** `Observer`'s every read,
`OracleObserver.observe()`, `ActionExecutor.execute` and the driver's own stepping,
`stepToInputWait` and `step`, begin with `UiRole.require`; on any thread but the owner they throw
an `IllegalStateException` naming the port, the role, the owning thread and the calling thread.
The executor throws rather than refuses: a refusal is for a Brain's choice, a wrong thread is a
programming error. `ThreadConfinementTest` calls each port from a foreign thread at a wait and
holds the failure and its names, holds a foreign thread wearing the owner's name refused and told
apart by id, holds that the foreign calls changed nothing, holds the order of the check by the
no-Run case, where only the role's own refusal names the role and the executor throws where it
would have refused after a read, and holds the role from start to close: claimed, not claimable
by a dead thread or another live one, not releasable by a stranger, released on close, and free
for another thread's Run, on which the ports then work.

**No Shatterfish code takes a monitor on a game type, as an ArchUnit rule.** `MonitorConfinementTest`
reads each Shatterfish class's bytecode through ASM and holds that no `monitorenter` has an operand
whose static type is a game type, a class under `com.shatteredpixel` or `com.watabou` or a subclass
of one, and that no synchronized method sits on a class that is a game type. The operand's type is
what produced it, a field's declared type, a parameter's, a local's from the debug table, a call's
return type or the class of a `new`, followed back through javac's `dup; astore; monitorenter`; a
producer the analysis cannot type is a violation, never a pass. A Shatterfish object that
implements a game interface, the log listener on the game's signal, is Shatterfish's own object
and may be locked, and a variable declared as a game interface is not a game object either; a
static synchronized method locks the Class object, which is the class's own; a game object behind
a variable declared `Object` passes by static type, which is the rule's limit and is written
beside it; a type the rule cannot load to look at is a violation, not a pass. The rule reads
local variable tables, which the shared module script now pins with `-g:source,lines,vars`
rather than leaving to Gradle's default, and its ASM is the harness module's own test dependency.
The two exemptions are the two classes themselves, not their nested classes. A second limit,
found by the fairness review: the rule sees the monitors Shatterfish code declares, not the ones a
synchronized game method takes on its behalf; every read of the window in front goes through
`Group.shatterfishMembers()`, synchronized on the scene (`SPD-classes/…/noosa/Group.java:49`), so
the fair path holds the scene monitor for the length of that call, held and released inside the
game's own method with nothing of Shatterfish's nested in it. The deadlock rule is about monitors
held across Shatterfish code, and the record's "never takes the scene monitor" reads that way. The rule is shown to bite on fixtures that lock a
game field, a game parameter, a game local, a call's result and a game subclass's own method, and
to pass an own field, `this` and the listener.

**Two exemptions, each load-bearing.** `SceneStepper` holds the actor thread's monitor and every
moving sprite's across a frame: the fence of story 1.3, on purpose, so the frame and the actor
thread cannot interleave, and `FenceInvariantTest` holds that design; this record's deadlock rule
was written before that fence and this amendment reconciles them. `HeadlessScene` is the scene,
and the game locks its scene on the render thread, `GameScene.update` being synchronized
(`…/scenes/GameScene.java:867`) as are `erase` (`:967`) and `addMobSprite` (`:1087`), with the
actor thread taking `synchronized (scene)` (`:1098`) and every member-list method of `Group`
synchronized on the group (`SPD-classes/…/noosa/Group.java:49`, `:99`, `:124`, `:201`); the
override of `update()` keeps the game's lock and `openWindow()` reads the member list under the
lock the group's own writers take. That is the
game's rule for its scene, not a monitor Shatterfish invented. The test holds that each exemption
would violate the bare rule, so neither can go stale unnoticed.

**The Brain holds no game object** by `BrainBoundaryTest`'s allowlist and its denial of
`org.shatterfish.harness..`, which this story names and does not repeat.

## Amendment: story 5.1 (2026-09-26)

The Overlay's side of the roles above is built. Paths abbreviate
`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/` as `…/`, at `v4.0.0`.

**The render thread claims the UI role** when the launcher's game attaches a Run
(`EmbeddedRun.attach`), as the headless driver's thread claims it at `start`; the Observer, the
executor and the Run's own `frame()` then refuse every other thread by name.

**One confirmation for both drivers.** The per-wait sequence is shared by construction rather than by
care: the state that decides whether a frame ends at a new Input wait (the hook row 5 count, the
announcement of an Action handed over, the window in front and its frames shown, the render queue)
moved out of `HeadlessDriver` into `WaitGate`, which the headless driver asks after each frame it
steps and the embedded Run asks after each frame the render thread gives it. The executor's
announcement reaches whichever gate is installed.

**The Brain's worker** is one daemon thread per Run, `shatterfish-brain`. The render thread submits
the Observation, asks `Future.isDone` once per frame, and takes the answer with `resultNow`, which
throws rather than waits; `EmbeddedRunRulesTest` holds that the class calls nothing that waits, and
`EmbeddedThreadingTest` that three hundred frames go by, each at once, while a Brain is held. No wait
is confirmed while a decision is pending, so the "decision for a `k` no longer current" case cannot
arise before takeover (story 5.8), and is checked as an invariant until then.

**The render queue.** A wait is confirmed only with nothing the game posted queued for the render
thread. libGDX's desktop backend keeps its queue private, so `OverlayApplication` counts at the one
door the game posts through, `Gdx.app.postRunnable` (`SPD-classes/…/noosa/Game.java:306-313`), and
counts only the game's own runnables: the backend's controller monitor re-posts itself on every frame,
so a count of everything never reaches zero, which the first real launch showed (no wait was ever
confirmed). The headless backend queues only what the game posts, so the rule is the same on both.

**Scene lifetime.** The Run re-attaches through hook row 3's existing seam, which it chains: the
Observer's log listener is re-added and the attachment counted. No destruction site was needed; the
Run holds no scene object. The gate is re-armed only when the floor changed (`Dungeon.level` is
another object): the desktop game also rebuilds its play scene on the same floor whenever the window
changes size (`SPD-classes/…/noosa/Game.java:136-141`), once at start-up, and a hero who became ready
in the scene before announced his wait there and does not announce it again. The second real launch
lost the first wait that way; `EmbeddedAttachTest.a_rebuilt_scene_keeps_the_wait` holds the rule.

**What equality with a Rig Run means.** An embedded Run's waits and ending equal the headless Run's
for the same tuple whenever the frames between two waits are the same (`EmbeddedDeterminismTest`,
which compares every wait's Observation hash, sections, Action, Decision, Belief, turn and depth, and
the end record). The desktop adds frames, drawn while the Brain thinks and paced by the wall clock,
and the render thread's draws in them come from the Run's generator until story 5.13's draw-routing
hook. Measured on the real desktop game (seed 12345, the Warrior, salt `5a175a17`, the random agent,
73 waits to its death): the Overlay Run's log verifies as a complete chain, which says only that it
was not altered, and its first 14 waits are the headless Run's, wait for wait; they part at wait 15,
the first turn a roll decided differently.

**A named exception to non-negotiable 5.** Until story 5.13, an Overlay Run is **not reproducible from
its tuple or its Action list**: a Replay of its log under the headless driver parts from it at the
first roll the desktop's extra frames moved. So an Overlay log's header carries `driver: embedded`
(chained, written only for the Overlay, so every headless log keeps its bytes; ADR-0011's story 5.1
amendment), and the Rig refuses such a log on every path that reads logs to count, score, calibrate
or replay them, by its header even when a later line is unreadable (`OverlayLogs` and
`Replay.refusal`, held by `OverlayLogsRefusedTest`). The field is a label against an honest mistake,
not tamper protection: the chain rules are published, and a whole log can be rewritten without it.
The debug views, the death gallery's snapshots and the strategy log, may show an Overlay log; they
count nothing. An Overlay Run is a thing to watch and a log to read, never a number. Story 5.13 closes the exception, and its Rig numbers say so.

**The player's input.** Until take-over (story 5.8), the game's own input is closed while a Run is
attached (`InputLock`, first in the game's input multiplexer, and put first again at the end of every
frame, because a text-input window inserts its own stage at the head of the same multiplexer), so a
click or key cannot change the game outside the log. A gamepad writes the game's key queue past the multiplexer and is not closed;
story 5.5's input-gate hook (option 9 above) closes every path.

**Answers that went stale.** A decision takes frames; if the play scene, the window in front, the
hero's state or the actor thread's rest changed meanwhile, or anything was announced or handed over,
the answer is dropped unrecorded and the same wait index is confirmed again from what is in front
(`WaitGate.reconfirm`), so a log holds one wait per index. The decider is put back to where it stood
before it was asked (`Rewindable`: the Brain's Belief and last Decision, the random agent's stream),
so the wait asked again is answered from one Observation, as the headless Run's is, and it is not
counted as a second wait on the same turn. A decider that cannot be put back keeps what the dropped
question changed, and the Run counts it; the Overlay's two agents both can. The actor thread must be
parked (`SceneStepper.actorThreadParked`: waiting on its own monitor in `Actor.process`, not on a
moving sprite, and then fenced by taking that monitor) before a wait is confirmed or acted on: the
hero is `ready` a little before his act has finished writing.

**Scenes in front.** The desktop game serves the scene the actor thread asks for in the same frame's
`step()`, before the Run looks, and the request flag is not volatile; so the Run decides by the scene
in front: the surface is the win; the loading scene of a descent, an ascent or a fall, and the play
scene it asks for, go on; any other loading mode, and any other scene, end the Run as unserved, as
the headless loop ends it. A Run that reaches no wait, while not thinking, within the headless loop's
budget at sixty frames a second (about 333 seconds) ends as an unknown window. The budget is counted
in game time, the sum of `Game.elapsed`, not in frames, which the desktop draws at the monitor's
refresh rate: a frame count would end the same Run sooner on a faster screen; the region intro on a first descent to depths 6, 11, 16 and 21 does, because the
Overlay does not click through it (the headless game never shows it).

## Amendment: story 5.2 (2026-09-26)

The Panel, the first thing the Overlay draws, lives on the same render thread and adds no thread.

- **Where it is placed, and when.** A frame is drawn with each camera's matrix, which only
  `Camera.update` rebuilds (`SPD-classes/.../noosa/Camera.java:225`, `:300-313`), inside
  `Camera.updateAll()` at the end of the game's update (`Game.java:269-283`); `Game.render` draws before
  it steps (`:150-171`). The scene's update is where `GameScene.layoutTags` resets the world camera's
  offset to `(0, y)` (`core/.../scenes/GameScene.java:931-958`, `:993-999`), whenever the tags change or
  the hero becomes ready (`:1745`), and on every new scene and resize. So `OverlayGame.update()` is the
  game's update written out in the game's order, with `PanelDock.step` doing the scene's update, then
  the Panel (added to each new play scene, placed by `PanelLayout`, a pure function of the screen) and
  its horizontal offset, then `Camera.updateAll()`. Placing the Panel after the whole update, the first
  way this story tried, drew the map shifted by the offset for one frame every time the tags were laid
  out; the matrix test in `PanelHudTest` holds the order. No upstream file is edited: a hook in
  `layoutTags` was rejected as an edit for something our own subclass reaches, and a child gizmo's
  `update()` as too early (the scene updates its members before `layoutTags`).
- **What is drawn over it, and what it is drawn over.** The Panel is added in front of the HUD the scene
  built and behind the scene's fade from black (`GameScene.java:784`, `PixelScene.java:366-376`), which
  it puts back in front, so the Panel does not show on black while a floor fades in. Windows are added
  in front of it later (`GameScene.java:1420-1441`). The cell-selection prompt (a `Toast` near the bottom
  centre, `GameScene.java:1126-1149`) and the badge banners (`PixelScene.java:378-395`) are drawn over it
  and take precedence: while either shows, the Panel dims.
- **The interface it plays on, and a second exception to non-negotiable 5.** The Run Profile declares
  the compact interface, the phone's, where UX-DR2 collapses the Panel for good, so the Overlay declares
  the mixed interface (size 1) after the Profile is prepared: the desktop layout, with no inventory
  pane, so an item selector is still a `WndBag` the executor answers (`GameScene.java:547-556`,
  `:1673-1674`; `ItemSelectorTest`). The full interface (2), the desktop default that `DESIGN.md` draws,
  would hand selectors to the inventory pane, which no Action names (`docs/ideas.md`). The Rig and the
  headless driver keep interface 0. The game falls back to the compact interface itself when the window
  is below its full-UI minimum (`SPDSettings.java:140-146`), where the Panel is the Mode strip.
  The interface size is not only layout. It changes the log text the Observation carries, all Run long:
  - the guidebook pickup line (`core/.../items/journal/Guidebook.java:59-63`), which every Run meets,
    since the cleared Profile journal puts a guidebook on floor 1
    (`core/.../levels/rooms/standard/entrance/EntranceRoom.java:119-130`);
  - the guide-page hint on every guide page found (`GameScene.java:1317-1320`), which is reached from
    `DocumentPage.java:55`, `Hunger.java:109`, `EquipableItem.java:59`, `Snake.java:67`,
    `Hero.java:1751`, `TrinketCatalyst.java:77` and `GameScene.java:1833`;
  - the movement and interface tutorial lines (`GameScene.java:760-765`, `:1353-1358`).

  The Brain's `Goo` pump memory hashes the log's text into its Belief (`Memory.tail`), so an Overlay
  Run's logged Belief hash differs from a headless Run's at the same waits, though the Decisions do not,
  today. This is a **second, independent exception** to non-negotiable 5, beside story 5.1's (the render
  thread's draws): story 5.13's draw routing does not close it, because the headless driver refuses any
  interface size but 0 ([`shatterfish/harness/src/main/java/org/shatterfish/harness/driver/HeadlessDriver.java:248-252`](https://github.com/watchthelight/shatterfish/blob/main/shatterfish/harness/src/main/java/org/shatterfish/harness/driver/HeadlessDriver.java#L248-L252)), so a Replay cannot be run at
  the size an Overlay Run was played on. Its closing plan is issue #169: the headless driver accepts
  interface size 1 when the log it replays states it. Meanwhile the log header states it: an Overlay log
  carries `interface` beside `driver: embedded`. The header states the size the Overlay declares: the log is opened in
  `create()`, before the game knows its window, when the setting reads 0; on a window below the
  game's full-UI minimum the game plays the compact interface instead, which the Panel's placement line
  in the console records and the header does not.
- **The controller.** The desktop strings name a key, or a controller's button when one is connected
  (`Guidebook.java:62`, `GameScene.java:1319`, `:1356`, by `ControllerHandler.isControllerConnected()`),
  so a gamepad plugged in changes the Observation's log text. The header states whether a controller was
  connected when the Run began (`controller`, 0 or 1); a controller connected mid-Run is not recorded, and
  is left with #169.
- **The oracle.** An oracle Run always opens windowed, since the desktop game is fullscreen by default
  (`SPDSettings.java:66-68`) and fullscreen hides the title bar, which was the oracle's only on-screen
  marker; and the Mode strip carries an ORACLE label in the oracle colour (`DESIGN.md`, `#FF2020`), full or
  collapsed. The border around the game view remains story 5.12's (non-negotiable 1: visibly flagged).
- **The launcher's view of it.** `--window WxH` opens the game windowed at that size, and
  `--screenshot <file>` writes one frame from the game's own framebuffer about ten seconds in (a window
  OpenGL draws cannot be captured from outside the process on this platform, and a launch nobody watches
  still leaves a picture); a screenshot that cannot be written is logged and the Run plays on. Each change
  of the Panel's placement is logged. `-Plaunch.args` splits on whitespace and keeps a single- or
  double-quoted value whole, so a path with a space can be passed.

## Amendment: story 5.3 (2026-09-26)

The Panel's content -- the Mode strip's line, the Goal line and the Decision card -- reads the Brain's
own Decision without adding a thread or a lock.

**The hand-off is same-thread, not cross-thread.** `EmbeddedRun.frame()` and `EmbeddedRun.serve()`
already run on the render thread (the UI-role thread, story 5.1); `serve()` now also stores the last
served wait's Decision (from `Deliberator.lastDecision()`, read the same way `RunLoop.record` already
does, after the worker's `Future` is done, which is the happens-before edge over whatever `decide()`
set on its own thread), its turn, its floor and the Observation it was decided from -- the same
Observation the Observer already built and the Brain already saw, carried out unchanged and naming
nothing new (ADR-0014), so the Panel can turn an Action into words (`ActionText`, added in this
story's review: a Step's compass direction, an Attack's target, an AnswerPrompt's option text)
without a second read of the game. `EmbeddedRun.snapshot()`, guarded by `UiRole.require`
like every other port, reads those fields and the Run's live `state()` -- so `Snapshot.state()`
can be `THINKING` while `Snapshot.decision()` still shows the *previous* wait's Decision, which is
exactly the "Panel shows the previous Decision until the new one lands" rule (`EXPERIENCE.md`,
Thinking indicator). Both the write (inside `serve()`) and the read (`PanelDock.frame`, from
`OverlayGame.update()`) happen on the render thread, so this is one thread reading its own state, not
a second cross-thread hand-off beside the worker's `Future`; no new lock, volatile field or queue was
added. `EmbeddedSnapshotTest` holds the THINKING/previous-Decision case and that `snapshot()` refuses a
foreign thread by name, as every other port does.

**Modes and speed modes are not real yet.** `ModeState.of` reads a placeholder: Mode is always
`RUNNING` and the speed mode always `normal` with a documented placeholder interval, since nothing
before story 5.5 (PAUSED), 5.6 (the controls row) or 5.7 (the speed selector and its interval) changes
either. Only the turn, the floor and whether the Brain is `THINKING` are read from the Run. This is
short of FR-38's full state table on purpose (`docs/ideas.md`, "Real Mode, speed mode and THINKING");
the point of doing it now is that `ModeStripContent` and `DecisionCardContent` already handle every
real value those stories will produce, so they add a caller rather than a format change.

**The Explain control and the input lock.** The Decision card's Explain is a native `RedButton`,
which -- like every click -- reaches the game only through `InputHandler`'s multiplexer, where
`InputLock` sits first and swallows every touch and key while a Run plays (story 5.1's review). Three
options were weighed: (a) carve an exemption for the Panel's own rectangle out of `InputLock`, rejected
because it duplicates `PanelLayout`'s geometry inside the lock and reopens exactly the door the lock
was built to close, ahead of story 5.5's own input-gate hook; (b) give Explain a key binding through
the game's own `SPDAction`/`KeyBindings` path, rejected because that path is reached through the same
multiplexer `InputLock` sits in front of, so it is blocked the same way; (c) **let Explain work only
when the lock does not hold**, chosen. Concretely: nothing routes around `InputLock`, so Explain is
unreachable while a Run plays and reachable once it has ended (`OverlayGame.render()` already calls
`lock.unlock()` there) -- a real, useful case (reading the final Decision), not a stub. Story 5.5's
input-gate hook is for hero-directed input (`CellSelector`), not Panel buttons, so closing this fully
is left to whichever story gives PAUSED a real click (`docs/ideas.md`).
