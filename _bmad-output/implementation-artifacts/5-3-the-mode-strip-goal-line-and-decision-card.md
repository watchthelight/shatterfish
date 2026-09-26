---
title: 'Story 5.3: The Mode strip, Goal line and Decision card'
type: 'feature'
created: '2026-09-26'
status: 'review'
baseline_commit: '1d98e35be'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Story 5.2 gave the Panel its place and its frame; it draws nothing yet. The human cannot
see what the bot is about to do, or why, so stepping it one Input wait at a time (the rest of epic 5)
would be watching a black box.

**Approach:** Read the Brain's own Decision (already produced by story 4.4, carried by `RunLog.Decision`
and `RunLog.Choice`) onto the render thread through a same-thread snapshot the render thread both
writes and reads; turn it into text with pure functions a test can hold against a constructed Decision
without booting the game; and draw that text with the Panel's own `RenderedTextBlock`s and one native
`RedButton`, the Explain control. Pause, takeover and the speed controls are not built yet (stories 5.5
to 5.7), so the Mode and speed mode shown today are a documented placeholder; the turn, the floor and
whether the Brain is `THINKING` are real.

## Acceptance Criteria (from epics.md Story 5.3, with the tests that hold them)

1. **The Mode strip.** Shows the mode word in its colour, the speed mode with its interval, the turn
   and the floor, and doubles as the collapsed Panel.
   Tests: `ModeStripContentTest` (every Mode word and colour; a speed mode with an interval shows it,
   one without does not; `THINKING` only while thinking; turn and floor columns fixed-width and
   right-aligned at any digit count; `ModeState.of` reads a live `EmbeddedRun.Snapshot`),
   `PanelContentTest` (the strip's text on a real Panel, full or collapsed).
2. **The Decision card.** Shows the chosen Action with its score and up to three alternatives with
   scores and one-line reasons; in Next Step mode the card is labelled as what the next press will
   execute.
   Tests: `DecisionCardContentTest` (chosen and alternatives against a constructed Decision; the Next
   Step headline; no Decision yet is a stated word, not a blank card), `PanelContentTest` (the rows on
   a real card).
3. **Numbers are right-aligned in fixed-width columns (UX-DR5).**
   Tests: `ModeStripContentTest.turn_and_floor_are_fixed_width_columns`,
   `DecisionCardContentTest.scores_are_fixed_width_columns` (`Columns`, exact `BigDecimal` decimals,
   never a float).
4. **Every state is stated in words (UX-DR14).**
   Tests: `ModeStripContentTest.every_mode_is_a_word` (colour is never the only signal),
   `DecisionCardContentTest.no_decision_yet` and `.no_flags_says_so` (absence is a word too).
5. **The Explain control.** Expands the Decision card in place to the Policy that fired, the
   alternatives' reasons in full and the Safety flags that applied; a second press collapses it. FR-39's
   v1 control, not the v2 Explain view.
   Tests: `DecisionCardContentTest.explain_expansion`, `PanelContentTest.explain_toggles_in_place`.

</frozen-after-approval>

## Design notes

### Getting the Decision to the render thread

- **A. A volatile field EmbeddedRun writes and the Panel polls, guarded as a second cross-thread
  hand-off beside the Brain's `Future`.** Rejected: `EmbeddedRun.frame()` and `.serve()` already run on
  the render thread (ADR-0013, story 5.1); a volatile would be solving a race that is not there and
  would read as one more thread to reason about when there is only one.
- **B. Reopen the Run log and read the last Wait record back.** Rejected: the log may be null (no
  logging), and a Decision the render thread already saw once should not cost a file read every frame.
- **C. `serve()` stores the Decision it already computes for `RunLoop.record` (the same
  `Deliberator.lastDecision()` read, at the same point, after the same `Future.isDone()`), and
  `EmbeddedRun.snapshot()` (a new port, guarded by `UiRole.require` like every other) hands it and the
  turn, the floor and the live `state()` to whoever asks, on that same thread.** Chosen: no new thread,
  lock or queue; `Snapshot` is an immutable record, so what is handed out cannot be mutated out from
  under the caller; `state()` is read live at the call, not stored, so `THINKING` while the *next*
  Decision is pending shows the *previous* one, which is `EXPERIENCE.md`'s own rule for it.

Pre-mortem: *`serve()`'s read of `lastDecision()` races the worker's next `decide()`.* It cannot: the
worker is single-threaded and `EmbeddedRun` submits its next question only after `serve()` has run for
the last one (`pending` is set back to null first), so there is exactly one `decide()` in flight and one
`lastDecision()` to read, and the read happens after the `Future` that carries the happens-before edge
over it is done. `EmbeddedSnapshotTest.thinking_keeps_the_previous_decision` and
`.snapshot_is_thread_confined` hold the two ways this could have been wrong.

### The Mode and speed mode: a placeholder, not a guess

- **A. Build the real state machine now (PAUSED, HUMAN, Next Step, Run N, Human play speed, Fast,
  their intervals) ahead of the stories that control them.** Rejected: the setup for this story
  explicitly scopes pause and the speed controls to 5.5 through 5.7; building the machine without a way
  to enter any state but RUNNING would be untested guesswork wearing a green checkmark.
- **B. Show nothing until a later story wires it (blank Mode strip fields).** Rejected: FR-38 asks for
  the Mode strip now, and a blank field is not "stated in words" (UX-DR14) -- it is the absence of one.
- **C. A minimal, honest placeholder: Mode is always `RUNNING`, the speed mode is always `normal` with
  a documented placeholder interval, and the turn, the floor and `THINKING` are real, read from the
  Run.** Chosen. `ModeState.of` is the one place this is true, and it is named for what it is in its own
  Javadoc so nobody downstream mistakes it for the real thing; `ModeStripContent` and
  `DecisionCardContent` are written against the full `Mode`/`SpeedMode` enums, so stories 5.5 to 5.7 add
  a caller, not a rewrite (`docs/ideas.md`, "Real Mode, speed mode and THINKING").

Pre-mortem: *A reviewer reads "RUNNING" and believes pause exists.* The Mode strip's own placeholder
Javadoc, this section, and `docs/ideas.md` all say otherwise in the same words; `ModeStripContentTest`
constructs every `Mode` value directly (not only through `.of`), so the enum and its colours are held
even though only one is reachable live today.

### The Explain control's input, under `InputLock`

- **A. Carve an exemption for the Panel's rectangle out of `InputLock`.** Rejected: it would duplicate
  `PanelLayout`'s geometry inside the lock, and the lock exists precisely so that no click reaches the
  game outside the log (story 5.1's fairness review); a second, parallel notion of "this click is fine"
  is the kind of thing a leak test exists to catch later instead of a design note catching it now.
- **B. Route Explain through the game's key-binding path (`SPDAction`/`KeyBindings`) instead of a
  click.** Rejected: that path is reached through the same `InputHandler` multiplexer `InputLock` sits
  first in (`InputLock.keyDown` returns `locked` before `KeyBindings` is ever asked), so it is blocked
  the same way; it would look like a fix and not be one.
- **C. Let Explain work only when the lock does not hold.** Chosen, and stated in the setup: "It may be
  fine to make it work only when the lock allows." Nothing routes around `InputLock`; while a Run plays,
  a click on Explain is swallowed like any other, and once the Run ends `OverlayGame.render()` already
  calls `lock.unlock()`, so Explain becomes clickable to read the final Decision -- a real case, not a
  stub. Story 5.5's input-gate hook is for hero-directed input (`CellSelector`), not Panel buttons;
  whichever story gives PAUSED a real click should route one to the Panel too (`docs/ideas.md`).

Pre-mortem: *Explain looks broken during a live Run because nothing says why.* `DecisionCard.toggleExplain()`
is a plain method, called by the button and callable directly, so `PanelContentTest` and
`DecisionCardContentTest` hold the expansion itself regardless of the lock; the lock's effect on the
button is a known, written-down limitation (`docs/ideas.md`), not a silent one, and no acceptance
criterion of this story promises a click during a live Run.

### One `RenderedTextBlock` per row, not one per column

- **A. A separate `RenderedTextBlock` per field (action, score, reason), each positioned at a fixed x, on
  every row.** Rejected for this story: `DESIGN.md` prefers it ("alignment is by column position, never
  by padding with characters"), but building it for the Mode strip's line and every Decision-card row
  today, only to redo it again for story 5.4's Belief rows and Decision log, is machinery ahead of the
  content it would carry.
- **B. Space-pad the numeric field to a fixed character width inside one string (`Columns`).** Chosen.
  It gives every number the same column *start* character, which is what `ModeStripContentTest` and
  `DecisionCardContentTest` hold (UX-DR5's testable property), at the cost of true pixel alignment,
  since the pixel font is not monospace. Recorded as a simplification in `docs/ideas.md` ("Per-column
  text, not a padded string") for story 5.4 to fix once, for every row kind at once.

## Tasks

- [x] `Columns`: score and number formatting, right-aligned, fixed width, no floats.
- [x] `ModeState`: the Mode and speed mode enums, the placeholder `of(Snapshot)`.
- [x] `ModeStripContent`: the strip's line and the Mode's colour, pure functions of a `ModeState`.
- [x] `DecisionCardContent`: the chosen row, the alternatives, the Next Step headline, the Explain
  expansion, pure functions of a `RunLog.Decision`.
- [x] `EmbeddedRun.Snapshot` and `.snapshot()`: the render thread's read of the last served Decision,
  turn, floor and live state.
- [x] `GoalLine`, `DecisionCard`: the components, added to `Panel` below the Mode strip; `Panel.content`
  wires the strip's text and both sections' width and visibility to the Panel's own form.
- [x] `PanelDock.frame`/`.step` take the `Snapshot` and hand it to `Panel.content` every frame.
- [x] Tests: `ModeStripContentTest`, `DecisionCardContentTest`, `PanelContentTest`, `EmbeddedSnapshotTest`.
- [x] A real launch with `--agent brain` and a screenshot.
- [x] Docs: `docs/architecture.md`, ADR-0013's story 5.3 amendment, `docs/ideas.md`.
- [x] Mutation battery.

## Review

### What was built

- `org.shatterfish.overlay.Columns`: `score` (a ten-thousandths `long` as its exact `BigDecimal`
  decimal, the same no-float technique `StrategyLog.choice` uses), `number` and `rightAlign` (fixed
  width, right-aligned with spaces), `seconds` (an interval to one decimal place).
- `org.shatterfish.overlay.ModeState`: `Mode` (RUNNING, PAUSED, HUMAN) and `SpeedMode` (NORMAL, the
  placeholder; NEXT_STEP; HUMAN_PLAY; FAST, each knowing whether it shows an interval); `of(Snapshot)`
  is the one place the placeholder is built.
- `org.shatterfish.overlay.ModeStripContent`: `text(ModeState)` (the strip's whole line) and
  `color(Mode)` (DESIGN.md's running/paused/human).
- `org.shatterfish.overlay.DecisionCardContent`: `Content`, `Row` and `Explain` records; `of(Decision,
  nextStep, explain)` is the one pure function everything else (the tests, `DecisionCard`) reads.
- `org.shatterfish.overlay.GoalLine`, `org.shatterfish.overlay.DecisionCard`: components added to
  `Panel`, drawing `ModeStripContent`'s and `DecisionCardContent`'s output with the game's own
  `RenderedTextBlock`s and, on the card, a `RedButton` ("Explain") whose click calls
  `DecisionCard.toggleExplain()`, the same method a test calls directly.
- `Panel.content(ModeState, RunLog.Decision)`: sets the strip's text and colour every frame regardless
  of form, and the Goal line's and the card's content only when full (both are given `null` when
  collapsed, which is what hides them); `Panel.layout()` now also positions the Goal line and the card
  below the strip, sections 6 apart (`DESIGN.md` spacing tokens), and forces the card invisible when
  collapsed (the Goal line already hides itself with no goal to show).
- `org.shatterfish.harness.agent.EmbeddedRun`: `Snapshot` (`decision`, `turn`, `floor`, `state`) and
  `snapshot()`, guarded by `UiRole.require`; `serve()` now also stores the served wait's Decision
  (`Deliberator.lastDecision()`, read exactly where `RunLoop.record` reads it), turn and floor.
- `PanelDock.step`/`.frame` take an `EmbeddedRun.Snapshot` (nullable, before a Run is attached) and pass
  `ModeState.of(snapshot)` and its Decision to `Panel.content`; `OverlayGame.update()` supplies
  `run == null ? null : run.snapshot()`.
- No upstream file edited; no hook row.

### Tests

`:overlay:test` in full: 18 classes, all passing (11 new: `ModeStripContentTest` 5,
`DecisionCardContentTest` 6, `PanelContentTest` 4, plus the existing 14 classes' 49 tests unchanged).
`:harness:test`'s `EmbeddedSnapshotTest` (new, 4) and the classes touched
(`EmbeddedEndingsTest`, `EmbeddedAttachTest`, `EmbeddedThreadingTest`, `EmbeddedDeterminismTest`,
`EmbeddedRunRulesTest`) all passing.

| Test | Holds |
|---|---|
| `ModeStripContentTest` | every Mode word and its own colour; a speed mode without an interval shows only its word, one with shows it to one decimal; `THINKING` only while thinking; the turn and floor columns are a fixed width and right-aligned at any digit count; `ModeState.of` reads the placeholder Mode/speed and the Run's real turn, floor and thinking flag from a constructed `Snapshot` |
| `DecisionCardContentTest` | no Decision yet is `"no decision yet"`, not a blank card; the chosen row and up to three alternatives, each a score and a one-line reason; Next Step's headline, present only in that mode, changes nothing else; Explain adds the Policy and the flags without changing the rows; no flags is `"none"`, never blank; scores are a fixed-width column, exact decimals (`0.1667`, not `0.17`) |
| `PanelContentTest` | on a real play scene: full, the strip's text matches `ModeStripContent`, the Goal line shows the goal, the card shows the chosen row and its one alternative; collapsed, the strip still says the Mode but the Goal line and the card are hidden; no Decision yet on a full Panel hides the Goal line and shows the card's placeholder; Explain toggles the Policy and flags rows in place and a second press collapses them |
| `EmbeddedSnapshotTest` | before the first wait, the snapshot has no Decision and turn/floor 0; a served wait's Decision, turn (equal to `RunLoop.turns()`, and moving between two waits) and floor reach it; while the next Decision is pending the snapshot is `THINKING` and still shows the *previous* Decision; `snapshot()` refuses a thread that is not the UI-role thread, by name, and the UI-role thread's own calls still work afterward |

### Mutation battery

A control run of `:overlay:test` and the touched `:harness:test` classes passed first; then 13 mutants,
each planted, run against the tests above, killed, and reverted.

| Mutant | Killed by |
|---|---|
| M1: `Columns.rightAlign` pads on the wrong side | `ModeStripContentTest`, `DecisionCardContentTest` (the fixed-width column tests) |
| M2: `Columns.score` uses three decimals, not four | `DecisionCardContentTest.scores_are_fixed_width_columns`, `PanelContentTest` |
| M3: `ModeStripContent.color` swaps RUNNING and PAUSED | `ModeStripContentTest.every_mode_is_a_word` |
| M4: `THINKING` shown unconditionally | `ModeStripContentTest.thinking_marker` |
| M5: `SpeedMode.NEXT_STEP` shows an interval | `ModeStripContentTest.speed_mode_and_interval` |
| M6: `DecisionCardContent.of` ignores `nextStep` | `DecisionCardContentTest.next_step_headline` |
| M7: an empty flags list is not said as `"none"` | `DecisionCardContentTest.no_flags_says_so` |
| M8: a `Row`'s reason is always dropped | `DecisionCardContentTest.chosen_and_alternatives`, `PanelContentTest` |
| M9: `EmbeddedRun.serve()` records the branch, not the depth, as the floor | `EmbeddedSnapshotTest.a_served_wait` |
| M10: `EmbeddedRun.snapshot()` drops its `UiRole.require` guard | `EmbeddedSnapshotTest.snapshot_is_thread_confined` |
| M11: `Panel.content` stops gating the Goal line and the card by `full` | `PanelContentTest.collapsed_hides_the_goal_and_the_card` |
| M12: `PanelDock.frame` never calls `Panel.content` | `PanelContentTest` (all four) |
| M13: `EmbeddedRun.frame()` drops `pendingTurn = turn` | `EmbeddedSnapshotTest.a_served_wait` |

### Real launch

`:overlay:launch --agent brain --seed 2000 --class WARRIOR --turn-cap 300 --exit-when-over --window
1600x900 --screenshot <path>` (see the handoff for the screenshot's absolute path). The window and
interface size match story 5.2's FULL case, so the Panel shows the Mode strip's line (`RUNNING  normal
0.0s  turn <n>  floor <n>`), the Goal line and the Decision card with the Brain's chosen Action and its
alternatives, scores right-aligned.

### Deferred

To `docs/ideas.md`: real per-column text (one `RenderedTextBlock` per field, not a padded string) for
story 5.4 to build once for every row kind; the real Mode, speed mode and their intervals (stories 5.5
to 5.7); Explain's click while a Run plays (whichever story gives PAUSED a real one); the Decision
card's height arbitrating room with the sections story 5.4 adds below it; the Goal line's two-line cap,
unenforced since no Brain goal today is long enough to need it.
