---
story: 1.5
key: 1-5-input-wait-detection
title: Input-wait detection
epic: 1
issue: 18
status: in-progress
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
| Given ADR-0015's decision that detection happens at the observe site inside the hero's act method, in the branch guarded by the ready flag, when the hook sets a flag and the driver consumes it | **Met.** The fourth site of hook row 5: two lines and an import at the top of the branch of `Hero.act()` that runs when the hero begins an act unready (`…/actors/hero/Hero.java:840-848`), reading `Hooks.inputWait` into a local and calling it. The listener is the driver's, one volatile write on the actor thread; the driver reads the count between frames, with the thread parked |
| `InputWaitCountTest` asserts that sixty actor-thread wake-ups with the hero parked produce exactly one wait, not sixty | **Met, with a finding about the premise.** A parked hero is never woken by the scene, which wakes the thread only while no actor is current; what wakes it is the UI ending the hero's turn with `next()` after handling input, as a click the hero cannot act on does. The test does that sixty times: the thread parks sixty more times, the site notifies nothing, the index stays 1, and asking for a wait then stalls with "nothing has happened since wait 1". The mutation that moves the call into `ready()`, the earlier design, notifies sixty-one times |
| The driver confirms the wait condition, being the hero ready with no window or a recognised Prompt window, before acting on the flag, and drops the flag otherwise | **Met.** `isInputWait`: the hero `ready` with no action and not resting, under no window or one `Prompts` recognises, or the resurrection window; a notification that fails it is dropped and counted (`droppedNotifications`). A window that is not a Prompt stalls the Run with the window named |
| The per-wait sequence runs in order: increment the index, reseed, observe, decide, execute, record, with the reseed and record stubbed until their own stories | **Met.** `WaitSequence<O, D>` with five no-op defaults; `HeadlessDriver.run(sequence, maxWaits)` confirms a wait, which increments `k`, then calls the five in order with that `k`. The test's sequence records the calls and moves the hero; three waits give fifteen calls in order |
| An interruption or a free search that produces a wait with no preceding Action is counted correctly | **Met.** A hero interrupted mid-move (`Hero.interrupt()` between frames, what damage or an enemy in view does) becomes ready on its next act, which begins unready and notifies; the driver confirms wait 2 with no Action between. The same mechanism covers every path to `ready()`, since it is reached only after the branch in the same act |

## What was built

- `core/.../actors/hero/Hero.java`: hook row 5's fourth site (`touches-upstream`); `Hooks.java`: the point's doc says what the site is. Ledger row 5, site index and diff budget updated.
- `shatterfish/harness/src/main/java/org/shatterfish/harness/driver/HeadlessDriver.java`: registers the listener at start and clears it at close; `stepToInputWait` confirms a new wait from a notification or a changed window in front, drops and counts the rest, and owns the index `k` (`Halt.waitIndex`, `waitIndex()`); `run(WaitSequence, maxWaits)`; `hookNotifications()`, `droppedNotifications()`; the stall diagnostic says what has and has not happened since the last wait.
- `…/driver/Prompts.java`: the windows that are Prompts, ADR-0006's kinds, until story 1.10 owns them.
- `…/driver/WaitSequence.java`: ADR-0013's per-wait sequence as an interface with no-op defaults.
- Tests: `driver/InputWaitCountTest` (five tests); `HeadlessBootTest` asserts the index across the chasm prompt and its answer.
- Docs: ADR-0015 amendment for story 1.5 (the correction below); two rows in `docs/rules/game-loop.md`, one of them tier F; `docs/UPSTREAM.md` row 5.

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

**A parked hero is never woken by the scene.** The scene notifies the thread only while no actor
is current (`…/scenes/GameScene.java:865`), and a waiting hero stays current after its `act()`
returned false (`…/actors/Actor.java:293-321`). The "sixty wake-ups" of the acceptance criterion
come from the UI ending the hero's turn with `next()` after handling input
(`…/scenes/CellSelector.java:152-166`, `:415-416`), whether or not the handling gave the hero
anything to do. The test wakes the thread that way.

**Answering a Prompt can close it without the hero acting**, so a notification alone would miss
the wait that follows: after the chasm prompt's "no" the hero is ready with no window and has
not acted. A change of the window in front since the last confirmed wait is therefore a new wait
too. A window that appears and goes away between confirmations is not: the game is back where
the last wait left it, and nothing is new for the brain until an Action changes something; the
Run stalls, with a message saying so, rather than counting a wait nothing decided.

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
without the hero acting; (b) a notification or an executed Action, which counts a wait for an
Action that changed nothing and would let a do-nothing agent burn the index forever; (c) a
notification or a change of the window in front since the last confirmed wait. Chosen (c): an
Action that changes nothing observable is a stall with a message, not a wait. Pre-mortem: a
game-initiated window that is not a Prompt appearing while the hero waits; it stalls the Run with
the window named, which is the assertion ADR-0006 asks for.

**The recognised Prompts.** Alternatives: (a) any window; (b) `WndOptions` only; (c) ADR-0006's
kinds as a list in `Prompts`, owned by story 1.10 from there. Chosen (c). Pre-mortem: a Prompt the
list lacks stalls a Run by name, which is the failure that tells 1.10 what to add.

**The sequence as an interface with defaults.** Alternatives: (a) five listener fields on the
driver; (b) the sequence inside `stepToInputWait`; (c) `WaitSequence<O, D>` with defaults and a
`run` loop. Chosen (c): the order is in one place, the types are placeholders until `api` has the
Observation and the Decision, and a test can supply all five.

## Evidence

Pending: build, repeats, the mutation battery and the review are recorded below once run.

## The fairness review

Pending.

## Deviations

- The story text expects sixty wake-ups of a parked hero; the game never wakes one on its own. The test wakes the thread the way the UI does and says why.
- The manual `:desktop:debug` check was not run; the hook is two lines that call nothing when nothing is registered.

## Known limitations, handed forward

- **The resurrection window** is recognised by the window, not by the hook, since the hero is not `ready` under it; the confirmation must keep doing that.
- **`Prompts` is a list**, not the Observer's model of a prompt; story 1.10 owns the kinds and their options.
- **The sequence's types are placeholders** until stories 1.6, 1.7 and 1.12 give `api` the Observation, the Decision and the Action.
- **A do-nothing Action stalls the Run** rather than advancing the index; the valid-Action set of story 1.12 must exclude Actions that change nothing.

## Follow-ups for later stories

- Story 1.8 onward: `WaitSequence.observe` is the Observer; the confirmation's window is the Observation's prompt.
- Story 1.10: the recognised Prompt kinds and their options replace `Prompts`.
- Story 1.13: `WaitSequence.execute` is the `ActionExecutor`; an Action that changes nothing is its error.
- Story 1.14: `HeadlessDriver.run` with a random agent as the sequence, across floors.
- Story 1.15: `WaitSequence.reseed` is `RngControl`.
