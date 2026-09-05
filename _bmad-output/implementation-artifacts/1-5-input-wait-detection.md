---
story: 1.5
key: 1-5-input-wait-detection
title: Input-wait detection
epic: 1
issue: 18
status: done
created: '2026-09-05'
updated: '2026-09-05'
---

# Story 1.5: Input-wait detection

As the engineer,
I want exactly one detection per hero turn,
So that the wait index is a reliable key for everything else.

Paths abbreviate `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/` as `…/`, and every
line number is at the pinned tag `v3.3.8` (commit `7b8b845a`), not in the hooked working tree.

## Acceptance criteria and how each was met

| Criterion | Outcome |
|---|---|
| Given ADR-0015's decision that detection happens at the observe site inside the hero's act method, in the branch guarded by the ready flag, when the hook sets a flag and the driver consumes it | **Met.** The fourth site of hook row 5: nine added lines (a marker, five comment lines, two statements and an import) at the top of the branch of `Hero.act()` that runs when the hero begins an act unready (`…/actors/hero/Hero.java:840-848`), reading `Hooks.inputWait` into a local and calling it. The listener is the driver's, one volatile write on the actor thread; the driver reads the count between frames, with the thread parked |
| `InputWaitCountTest` asserts that sixty actor-thread wake-ups with the hero parked produce exactly one wait, not sixty | **Met, with a finding about the premise.** A parked hero is never woken by the scene, which wakes the thread only while no actor is current; what wakes it is the UI ending the hero's turn with `next()` after handling a click, as a click the hero cannot act on does. The test does that sixty times: the thread parks exactly sixty more times, the site notifies nothing, the index stays 1, and asking for a wait then stalls with "nothing has happened since wait 1". The mutation that moves the call into `ready()`, the earlier design, notifies sixty-one times |
| The driver confirms the wait condition, being the hero ready with no window or a recognised Prompt window, before acting on the flag, and drops the flag otherwise | **Met.** The condition is AD-5's, with three things the game does (below); a notification that finds the hero mid-action is dropped and counted (`droppedNotifications`); a window that is not a Prompt stalls the Run with the window named |
| The per-wait sequence runs in order: increment the index, reseed, observe, decide, execute, record, with the reseed and record stubbed until their own stories | **Met.** `WaitSequence<O, D>` with five no-op defaults; `HeadlessDriver.run(sequence, maxWaits)` confirms a wait, which increments `k`, then calls the five in order with that `k`. The test's sequence records the calls and moves the hero; three waits give fifteen calls in order |
| An interruption or a free search that produces a wait with no preceding Action is counted correctly | **Met.** A hero interrupted mid-move (`Hero.interrupt()` between frames, what damage or an enemy in view does) becomes ready on its next act, which begins unready and notifies; the driver confirms wait 2 with no Action between. Every path to `ready()` goes through the branch in the same act, so every transition to ready is announced |

## What was built

- `core/.../actors/hero/Hero.java`: hook row 5's fourth site (`touches-upstream`); `Hooks.java`: the point's doc says what the site is. Ledger row 5, site index and diff budget updated.
- `shatterfish/harness/src/main/java/org/shatterfish/harness/driver/HeadlessDriver.java`: registers the listener at start and clears it at close; `stepToInputWait` confirms a new wait from a notification, a changed window in front, or an Action handed to the game since the last confirmed wait, checks the condition, drops and counts the rest, and owns the index `k` (`Halt.waitIndex`, `waitIndex()`, a `long`); `run(WaitSequence, maxWaits)`; `hookNotifications()`, `droppedNotifications()`; the stall diagnostic says what has and has not happened since the last wait.
- `…/driver/Prompts.java`: the windows that are Prompts, the ones among ADR-0006's kinds the game opens on its own, until story 1.10 owns the kinds.
- `…/driver/WaitSequence.java`: ADR-0013's per-wait sequence as an interface with no-op defaults.
- `…/boot/HeadlessBoot.java`: the support prompt is marked as already shown, so the first boss's key opens no window.
- Tests: `driver/InputWaitCountTest` (seven tests); `HeadlessBootTest` asserts the index across the chasm prompt and its answer, and takes the resurrection warning.
- Docs: ADR-0015 amendment for story 1.5 (the correction below, what makes a new wait, the index); five rows in `docs/rules/game-loop.md`, one of them tier F; `docs/UPSTREAM.md` row 5.

## What the story found

**ADR-0015's site runs more than once per Input wait.** The decision said the observe branch of
`Hero.act()` "is reached exactly once per Input wait because the branch is guarded by `ready`
becoming true at its end", and that a multi-cell move never reaches it between cells. At the tag
the branch runs at the start of every act that begins unready (`…/actors/hero/Hero.java:840-848`),
and an act that carries out an Action sets `ready` false (`:887`), so each step of a move, each
resting turn and each attack reaches it again on the next act: the game observes between the
cells of a walk, which is how the fog lifts as the hero walks. What is true, and enough: `ready()`
is reached only later in the same act (`:862-870`, `:935-946`), so the branch runs once before
every transition to ready, and a hero already waiting skips it on every wake-up. So the
notification means "confirm now", and the driver drops what is not a wait. ADR-0014's one step per
Action still pairs with this, for a different reason: it makes one Action one act, so one
notification.

**An act can begin ready and end ready in one go, announcing nothing.** A move the hero cannot
path to, or the first floor's entrance refused without the amulet, which posts a message
(`…/levels/SewerLevel.java:146-155`, `:1391-1395`), runs `getCloser` or `actTransition` and
`ready()` inside an act that skipped the branch. The notification and the window's change both
miss the wait that follows, and AD-5 gives every executed Action its own wait, whatever the game
made of it, or two Actions would share an index in the Run log. So an Action handed to the game,
which the driver sees as the hero holding it when stepping resumes, is the third source of a new
wait. The review found the ascent; the refused move followed from it.

**A parked hero is never woken by the scene.** The scene notifies the thread only while no actor
is current (`…/scenes/GameScene.java:865`), and a waiting hero stays current after its `act()`
returned false (`…/actors/Actor.java:293-321`). The "sixty wake-ups" of the acceptance criterion
come from the UI ending the hero's turn with `next()` after handling a click
(`…/scenes/GameScene.java:1750-1756`) or a held key (`…/scenes/CellSelector.java:415-416`),
whether or not the handling gave the hero anything to do. The test wakes the thread that way.

**Answering a Prompt can close it without the hero acting**, so a notification alone would miss
the wait that follows: after the chasm prompt's "no" the hero is ready with no window and has
not acted. A change of the window in front since the last confirmed wait is therefore a new wait
too. A window that appears and goes away between confirmations is not: the game is back where
the last wait left it, and nothing is new for the brain until an Action changes something; the
Run stalls, with a message saying so, rather than counting a wait nothing decided.

**A Prompt is not answerable on the frame it appears.** The chasm prompt ignores its buttons
until it has been updated for more than 0.2 s of frame time (`…/levels/features/Chasm.java:77-92`),
a guard against a click meant for the map; a driver confirming the wait on the frame the window
appeared would have its answer refused and the Run would stall. A window is a wait from its
second frame in front, which is one frame later than story 1.4 confirmed it and one frame
earlier than any human. The review found this; story 1.4's test had stepped the extra frame by
hand.

**The resurrection window asks twice when a kept-item slot is empty**, stacking a warning
`WndOptions` over itself (`…/windows/WndResurrect.java:98-118`); with the hero at zero health and
not ready, the warning must count as the Prompt it is. The review found this too. And the
inventory pane can be selecting an item with no window at all while the map refuses clicks
(`…/scenes/GameScene.java:1386-1395`); that is not a wait, and the diagnostic now says so.

**The worktree's upstream files are CRLF on disk with LF in the index.** A script that rewrites
an upstream file and keeps what it read makes git see every line changed (2646 insertions, 2637
deletions for one hook); the ledger's digest strips carriage returns, but the wrap rule and the
line counts do not. The site was written with LF, and the diff is nine added lines.

## Decisions taken inside the story

**Where the site goes.** Alternatives: (a) beside the `Dungeon.observe()` call, inside the
not-resting arm; (b) the first statement of the ready-guarded branch, covering both arms; (c) in
`ready()`, the design the reviewer gate rejected. Chosen (b): a resting hero that stops resting
becomes ready through the same branch, and the notification must precede every transition to
ready. Pre-mortem: an upgrade that moves the branch; the ledger's digest names the file and the
wrap rule holds nothing was removed.

**What a new wait is.** Alternatives: (a) a notification alone, which misses a Prompt answered
without the hero acting and an Action the game refused; (b) a notification or an executed Action,
which misses a Prompt appearing over a waiting hero; (c) a notification, a change of the window in
front, or an Action handed to the game, each since the last confirmed wait. Chosen (c). An Action
that the game refuses still consumes its wait, because AD-5 is one Action per index and the log
must say which; a Run left with nothing handed to the game and nothing changed stalls with a
message. Pre-mortem: a game-initiated window that is not a Prompt appearing while the hero waits;
it stalls the Run with the window named, which is the assertion ADR-0006 asks for.

**The recognised Prompts.** Alternatives: (a) any window; (b) `WndOptions` only; (c) the windows
among ADR-0006's kinds that the game opens on its own, as a list in `Prompts`, owned by story 1.10
from there. Chosen (c). Pre-mortem: a Prompt the list lacks stalls a Run by name, which is the
failure that tells 1.10 what to add.

**When a window is a wait.** Alternatives: (a) the frame it appears, with the executor stepping a
frame before answering the one prompt that refuses early answers; (b) from its second frame in
front, for every window. Chosen (b): the driver, not the executor, should know when the game will
take an answer, and one frame per prompt is below any human's floor.

**The sequence as an interface with defaults.** Alternatives: (a) five listener fields on the
driver; (b) the sequence inside `stepToInputWait`; (c) `WaitSequence<O, D>` with defaults and a
`run` loop. Chosen (c): the order is in one place, the types are placeholders until `api` has the
Observation and the Decision, and a test can supply all five.

**The index.** `k` counts confirmed waits from 1 and is incremented before the sequence, as
ADR-0013 has it; the spine's "0-based" is the value before the first wait. It is a `long`, as the
spine says, since the Run log and the reseed carry it.

## Evidence

`./gradlew build -Pshatterfish.mobile=off`: green, 70 tests across 13 suites. `mkdocs build --strict`:
clean. The harness suite run six times in a row across the two commits: green each time.

**Mutation battery.** Run on the committed tree, each mutation applied, its tests run with results
cleared first and Gradle's exit code checked, then restored with `git checkout` and the tree
verified clean. Fourteen mutations, thirteen caught.

| Mutation | Result |
|---|---|
| M1 the site calls nothing (marker kept) | eighteen failures: every Run stalls at its first wait, and `HooksLedgerTest` finds the digest changed |
| M2 the earlier design: the call moves into `ready()` | fails four times: sixty-one notifications where one is right, one per resting turn where each is wanted, and a refused move that notified |
| M3 every window is a Prompt | fails twice: the message window is confirmed as a wait |
| M4 the notification is never consumed | fails twice: the parked hero is confirmed again and again |
| M5 a changed window is not a new wait | fails twice: the chasm prompt and the resurrection warning are never confirmed |
| M6 dropped notifications are not counted | fails twice |
| M7 the sequence executes before it decides | fails: execute gets nothing to execute |
| M8 the listener outlives the Run | fails eight times: the next Run refuses to start |
| M9 the index is not incremented | fails twelve times |
| M10 the resurrection window is not a wait | fails twice |
| M11 a window is a wait from the frame it appears | fails four times: confirmed a frame early, and the "yes" the chasm prompt refused leaves the Run stalled |
| M12 an Action handed to the game is not a source of a new wait | fails twice: the refused move and the refused ascent never get their wait |
| M13 the resurrection is confirmed only through its own window | fails: the warning over it is never confirmed |
| M14 the inventory pane selecting is a wait | **not caught.** No test drives the pane's item selection, which needs an item action; story 1.13 owes that test with the executor |

## The fairness review

One round, by the `fairness-reviewer` subagent on `git diff main...HEAD` after the first commit.
**PASS on information parity, no blocking finding** (confidence high for parity, medium for the
completeness of the wait rule); the hook was found minimal, listed and exact, the listener a
volatile write, the claim against ADR-0015 verified at the tag. Eight findings, every one taken:

1. **The resurrection warning** stacked over `WndResurrect` when a kept-item slot is empty was not
   a wait: the confirmation would stall a Run answering "continue" with an empty slot. Fixed; the
   armor-less ankh test; M13.
2. **The chasm prompt confirmed on the frame it appeared** refuses an answer for 0.2 s of frame
   time; story 1.4's test had stepped the extra frame by hand. A window is a wait from its second
   frame in front; the tests no longer step by hand; M11.
3. **A notification consumed under a non-Prompt window was lost**, and the reviewer's example, the
   first floor's entrance refused without the amulet, turned out to announce nothing at all: the
   act begins and ends ready. Fixed by the third source of a new wait, an Action handed to the
   game; the refused ascent and the refused move are tests; M12. The support prompt on the first
   boss's key is switched off in the boot; the alchemy scene and talents are noted as not windows.
4. **`GameScene.interfaceBlockingHero()` was ignored**: the inventory pane selecting an item is not
   a wait. Added to the condition and the diagnostic; untested (M14), owed with the executor.
5. **The `Prompts` comment overstated its list**; rewritten to say what is and is not there.
6. **`k`'s convention** differed between the spine (0-based, long) and ADR-0013 (`k++` first);
   settled as a `long` counting from 1, recorded in the amendment.
7. **Citations**: the click path's `next()` is the default cell listener
   (`GameScene.java:1750-1756`), not the selector; the ledger and the amendment undercounted the
   hook's added lines. Corrected.
8. **Tests**: the wake-up test now asserts exactly sixty parks; the dropped count is a delta; a
   full rest and the resurrection warning are tested; the resurrection test asserts the index.

## Deviations

- The story text expects sixty wake-ups of a parked hero; the game never wakes one on its own. The test wakes the thread the way the UI does and says why.
- The manual `:desktop:debug` check was not run; the hook calls nothing when nothing is registered, and every scene test without the driver exercises that branch.

## Known limitations, handed forward

- **The resurrection window** is recognised by the window and the pending mark, not by the hook, since the hero is not `ready` under it; the confirmation must keep doing that.
- **`Prompts` is a list**, not the Observer's model of a prompt; story 1.10 owns the kinds and their options, and the blacksmith's later windows are not in it.
- **The inventory pane's item selection** is excluded from the wait condition without a test; story 1.13's executor, which will drive item actions, owes it.
- **The sequence's types are placeholders** until stories 1.6, 1.7 and 1.12 give `api` the Observation, the Decision and the Action.
- **An Action the game refuses consumes its wait.** The valid-Action set of story 1.12 should keep such Actions rare, and the Rig will need a cap on waits as well as turns, since a refused Action spends no turn.

## Follow-ups for later stories

- Story 1.8 onward: `WaitSequence.observe` is the Observer; the confirmation's window is the Observation's prompt.
- Story 1.10: the recognised Prompt kinds and their options replace `Prompts`.
- Story 1.12: the valid-Action set excludes what the game refuses where it can be known.
- Story 1.13: `WaitSequence.execute` is the `ActionExecutor`; a test drives the inventory pane's selection.
- Story 1.14: `HeadlessDriver.run` with a random agent as the sequence, across floors, with a cap on waits.
- Story 1.15: `WaitSequence.reseed` is `RngControl`.
