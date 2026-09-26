---
title: 'Story 5.3: The Mode strip, Goal line and Decision card'
type: 'feature'
created: '2026-09-26'
status: 'done'
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
   a real card), `ActionTextTest` (the Action read as words a person can read, added in the review
   round below after `Action.toString()` reached the screen).
3. **Numbers are right-aligned in fixed-width columns (UX-DR5).**
   Tests: `ModeStripContentTest.turn_and_floor_are_fixed_width_columns` (the Mode strip's one line);
   `DecisionCardContentTest.scores_are_exact_decimals` (`Columns`, exact `BigDecimal` decimals, never a
   float); `PanelContentTest.scores_and_reasons_are_real_pixel_columns` (the Decision card's rows,
   real pixel positions measured with the game's font, not a padded string -- corrected in the review
   round below, which the first pass's citation here named as string padding until it was found wrong
   on screen).
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

### Real columns for the Decision card's rows (revised in review; see "Review round" below)

The first pass chose a padded string over a separate `RenderedTextBlock` per field, on the reasoning
that pixel columns for one story's rows would be redone for story 5.4's Belief rows and Decision log
regardless. The review (`s53-1600x900.png`) found that reasoning did not survive contact with the
screen: the pixel font is proportional, so a score space-padded inside the same text block as the
action name does not actually land in one column -- `1.0000` and `0.1111` started at different x
positions from row to row, which is UX-DR5's own acceptance criterion for this story, not a
simplification to defer. Re-brainstormed with the same three options:

- **A. A separate `RenderedTextBlock` per field (action, score, reason), each row's column x computed
  from the widest cell in that column, measured with the game's font.** Chosen this round. Three
  `RenderedTextBlock`s per table row (`DecisionCard.actionBlocks`, `.scoreBlocks`, `.reasonBlocks`);
  `refresh()` sets each block's text (natural, unwrapped width for the action and the score, so the
  measurement is real), then takes the widest action block and the widest score block across the rows
  shown to fix the score column's right edge (`maxActionWidth + COLUMN_GAP + maxScoreWidth`) and the
  reason column's left edge (that plus another `COLUMN_GAP`); `layout()` right-aligns each score block
  to that edge (`x = columnRight − block.width()`) and left-aligns every action and reason block.
  `PanelContentTest.scores_and_reasons_are_real_pixel_columns` measures the drawn positions, not
  strings: every row's score block ends at one right edge and every reason block starts at one left x,
  against rows deliberately built to differ in every measured width (a long action name with a short
  score, a short action name with a long score, and an empty reason), so the test cannot pass by
  coincidence of equal-length text.
- **B. Space-pad the numeric field to a fixed character width inside one string (`Columns`, the first
  pass's choice).** Reverted: it gives every number the same column *start character*, which
  `ModeStripContentTest` and the old `DecisionCardContentTest.scores_are_fixed_width_columns` held, but
  a character position is not a pixel position in a proportional font, which is exactly what the
  screenshot showed wrong. `Columns.score` keeps the exact-decimal formatting (`BigDecimal.valueOf(v,
  4)`, UX-DR5's "no floats") and drops the padding, since a pixel column needs none of its own.
- **C. Wait for story 5.4 and fix every row kind (Decision card, Belief rows, Decision log) in one
  pass.** Rejected: the reviewer named this an AC failure of *this* story, not a deferral; UX-DR5 is
  this story's own acceptance criterion, and shipping it wrong now to fix it later is not what "done"
  means. Story 5.4's Belief rows and Decision log still get their own columns in their own story --
  this only builds `DecisionCard`'s.

The Mode strip's turn and floor stay a padded string (`Columns.number`, unchanged): it is one line, so
there is no second row to misalign against, which is the property UX-DR5 is protecting.

Pre-mortem: *A row with no reason (`Action.Wait`'s empty one) still needs to occupy the reason column
so the next row's reason lines up under it, not so its own empty text lines up under nothing.* The
reason block is positioned at the column for every visible row regardless of whether its text is
empty; `PanelContentTest`'s uneven-rows case includes exactly this row and holds the column position,
not the text, for it.

### A human-readable Action label (added in review)

The review also found `Action.toString()` on screen (`"Step[cell=659]"`, `"Search[]"`): fine for
`StrategyLog`, wrong for a person reading the card (UX-DR13).

- **A. Add a `describe()`/`toString()`-like method to `Action` itself, in `api`.** Rejected: `api` is
  shared by `brain`, the harness and the Overlay, and `StrategyLog`'s plain-text log wants the exact
  machine form it already has; a second, prettier `toString()` on the same sealed type serves one
  reader (the Overlay) by changing what every reader sees.
- **B. Teach `DecisionCardContent` to format the label, since it already builds the card's rows.**
  Rejected: labelling a Step's direction, an Attack's target and an AnswerPrompt's option text needs
  the Observation the Decision was made on, which is exactly what `DecisionCardContent`'s own doc
  disclaims ("the Decision's shape, not its rendering"); folding it in would mean every content test
  needs an Observation to hold a Row at all.
- **C. A new class, `ActionText`, an exhaustive switch over every kind `Action`'s sealed interface
  permits (the same discipline `brain`'s `Highlights` holds itself to), taking the Action and the
  Observation the Decision was made on.** Chosen. `EmbeddedRun.Snapshot` gains an `observation` field
  (the same Observation `serve()` already reads, carried out unchanged -- ADR-0014: it names nothing
  the Decision could not already see); null before the first wait and whenever a caller has none.
  `ActionText.of` covers all 21 kinds with no default, so a kind added to `Action` does not compile
  here until someone has said what it reads as; a null Observation falls back to a plainer label (the
  raw cell, "attack" alone, the option's index) rather than throwing. `ActionTextTest` holds the
  exhaustive fallback list, the eight compass points from a real hero cell and map width, an Attack
  naming a real actor found at the target cell (and falling back when none is there), and an
  AnswerPrompt reading a real Prompt's button label (and falling back to the index outside it).

Pre-mortem: *`UseItemOn`'s label names the wrong item.* `UseItemOn`'s `item` is what pressed the
button (the scroll, say); its `target` is what the bag then chose (the weapon it upgrades). The label
names the target (`"upgrade wand of magic missile"`), since the action word already says what the
item itself does, and naming the item again would read as `"upgrade scroll of upgrade"`. Covered by
`ActionTextTest.every_kind_without_an_observation` and killed by mutant N10 below when reverted.

### The placeholder speed shows an em dash, not a number (fixed in review)

- **A. Leave `SpeedMode.NORMAL`'s interval at the placeholder `0.0` and format it like any other
  (the first pass's choice: `"normal 0.0s"`).** Reverted: the reviewer read `0.0s` as a measurement
  nobody made, not as "nothing to show" -- a number always looks like data, however placeholder its
  source.
- **B. Hide the interval field entirely for `NORMAL` (just `"normal"`).** Rejected: it collapses
  `NORMAL` into the same shape as `NEXT_STEP` and `FAST` (which truly never have an interval), erasing
  the distinction that `NORMAL` is a placeholder for a speed mode that *will* have one (5.7's Human
  play speed does), not a speed mode that never will.
- **C. Show the word with an em dash where the number would go (`"normal —"`).** Chosen.
  `ModeStripContent.NO_INTERVAL_YET` is the one place this is decided; `SpeedMode.NORMAL` still
  reports `showsInterval = true` (there is a slot for one), and `ModeStripContent.speed` substitutes
  the em dash only for `NORMAL`, so `HUMAN_PLAY`'s interval (constructed directly in tests today, and
  real from story 5.7) still formats as a number.

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
- [x] *Review round:* real per-column pixel positioning in `DecisionCard`, an `ActionText` label for
  every `Action` kind, and the em dash for the placeholder speed's interval (below).
- [x] *Fairness review round:* Explain's hot area gated by `inputLocked`, an Observation-identity
  pin in `EmbeddedSnapshotTest`, and a generator-purity test for `Panel.content` (below).

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

### Real launch (first pass)

`:overlay:launch --agent brain --seed 2000 --class WARRIOR --turn-cap 300 --exit-when-over --window
1600x900 --screenshot s53-1600x900.png`. The window and interface size match story 5.2's FULL case, so
the Panel showed the Mode strip's line, the Goal line and the Decision card with the Brain's chosen
Action and its alternatives -- this launch is what the review round below found wrong (`Action.toString()`
on screen, `"normal 0.0s"`, scores not actually landing in one column), so its screenshot is superseded
by the review round's.

### Deferred

To `docs/ideas.md`, after the review round's fixes below: the real Mode, speed mode and their
intervals (stories 5.5 to 5.7); Explain's click while a Run plays (whichever story gives PAUSED a real
one); the Decision card's height arbitrating room with the sections story 5.4 adds below it; the Goal
line's two-line cap, unenforced since no Brain goal today is long enough to need it.

## Review round (real pixel columns, a human-readable Action label, and the placeholder's em dash)

The reviewer read `s53-1600x900.png` and found three things to fix before this story is done, the
first an acceptance-criterion failure rather than a polish item:

1. **UX-DR5 was not met on screen.** One padded `RenderedTextBlock` per row does not align in a
   proportional font; scores and reasons started at different x positions row to row. Fixed by
   building real columns in `DecisionCard` (see "Design notes" above, the revised "Real columns"
   section) and holding the drawn positions, not strings, with
   `PanelContentTest.scores_and_reasons_are_real_pixel_columns`.
2. **`Action.toString()` was on screen** (`"Step[cell=659]"`, `"Search[]"`), unreadable to a person.
   Fixed with a new `ActionText` class, an exhaustive switch over every kind covering the whole of
   `Action`'s sealed interface, using the Observation the Decision was made on (now carried by
   `EmbeddedRun.Snapshot`) for a Step's compass direction, an Attack's target name and an
   AnswerPrompt's option text. Held by `ActionTextTest`.
3. **`"normal 0.0s"` read as a real measurement.** Fixed: the placeholder speed mode shows an em dash
   where the interval would go (`ModeStripContent.NO_INTERVAL_YET`), so a placeholder cannot be
   mistaken for data; `HUMAN_PLAY`'s (constructed, and real from 5.7) interval still formats as a
   number. Held by `ModeStripContentTest.speed_mode_and_interval`.

### What changed

- `Columns.score` no longer pads (real columns replace it); `Columns.rightAlign`/`.number` are
  unchanged and still carry the Mode strip's single-line turn and floor.
- `DecisionCardContent.Row` carries the `Action` itself, not a string: content stays "the Decision's
  shape," and `DecisionCard` (or a test) decides how to show it.
- New `org.shatterfish.overlay.ActionText`: `of(Action, Observation)`, covering all 21 kinds.
- `EmbeddedRun.Snapshot` gains `observation` (the Observation the Decision was made on, or null);
  `serve()` stores it alongside the Decision, turn and floor it already stored.
- `DecisionCard` rebuilt around `actionBlocks`/`scoreBlocks`/`reasonBlocks` (one `RenderedTextBlock`
  triad per table row): `refresh()` measures each block's natural width and fixes the shared score and
  reason column edges; `layout()` positions every row against those edges. `Panel.content` and
  `PanelDock` thread the Observation through alongside the Decision.
- `ModeStripContent.NO_INTERVAL_YET` (an em dash), substituted for `SpeedMode.NORMAL`'s interval only.

### Tests

`:overlay:test` in full: 20 classes, all passing (`ActionTextTest` new, 4 tests;
`ModeStripContentTest`, `DecisionCardContentTest` and `PanelContentTest` updated for the Action-object
`Row`, the em dash, and the new column test). `:harness:test`'s `EmbeddedSnapshotTest` (updated for
`observation()`) and the other touched classes all still passing.

| Test | Holds |
|---|---|
| `ActionTextTest` | every one of the 21 `Action` kinds reads as a word with no Observation to add detail (the exhaustive fallback list); a Step or MoveTo reads the real compass direction from a real hero cell and map width, all eight points, including the degenerate "here"; an Attack names a real actor found at the target cell and falls back to "attack" when there is none; an AnswerPrompt reads a real Prompt's button label and falls back to the index outside it |
| `DecisionCardContentTest` (updated) | `Row.action()` is the `Action` object itself, not a string; everything else as before |
| `PanelContentTest` (updated) | the card's rows read through `ActionText` on a real Panel (`"attack"`, `"step 12"` -- this snapshot carries no Observation, so `ActionTextTest` is where the compass direction and the named target are held); `scores_and_reasons_are_real_pixel_columns`: three rows built to differ in every measured width (a long action name with a short score, a short action name with a long score, an empty reason) still share one score-column right edge and one reason-column left edge, each computed independently of `DecisionCard`'s own private fields (the card's public `left()`, its public `COLUMN_GAP`, and each block's own measured `width()`) |
| `ModeStripContentTest` (updated) | the placeholder speed shows `"normal —"`, never a number |
| `EmbeddedSnapshotTest` (updated) | a served wait's snapshot carries the Observation the Decision was made on (its header names the same floor); no Decision yet carries no Observation either |

### Mutation battery

A control run of `:overlay:test` and the touched `:harness:test` classes passed first; then 11 more
mutants (24 in total with the first pass's 13), each planted, run, killed, and reverted.

| Mutant | Killed by |
|---|---|
| N1: `Columns.score` uses three decimals, not four | `DecisionCardContentTest.scores_are_exact_decimals` |
| N2: the placeholder speed formats a number again, not the em dash | `ModeStripContentTest.speed_mode_and_interval` |
| N3: `DecisionCard`'s score column drops its `COLUMN_GAP` | `PanelContentTest.scores_and_reasons_are_real_pixel_columns` |
| N4: the score block is left-aligned (no width subtracted) | `PanelContentTest.scores_and_reasons_are_real_pixel_columns` |
| N5: the reason column drops its own `COLUMN_GAP` | `PanelContentTest.scores_and_reasons_are_real_pixel_columns` |
| N6: the action column is offset by `COLUMN_GAP` from the card's own left edge | `PanelContentTest.scores_and_reasons_are_real_pixel_columns` |
| N7: `ActionText`'s compass north and south are swapped | `ActionTextTest.step_direction` |
| N8: `ActionText`'s Attack never looks for a target | `ActionTextTest.attack_names_the_target_when_found` |
| N9: `ActionText`'s AnswerPrompt drops its bounds check (throws instead of falling back) | `ActionTextTest.answer_prompt_reads_the_option_text` |
| N10: `ActionText`'s UseItemOn names the item pressed, not the target chosen | `ActionTextTest.every_kind_without_an_observation` |
| N11: `EmbeddedRun.serve()` never stores the Observation on the snapshot | `EmbeddedSnapshotTest.a_served_wait` |

### Real launch (review round)

Same command, a fresh seed's Run, at `s53-1600x900-review.png`. The Mode strip reads `RUNNING  normal
—  turn    5  floor 1`; the Goal line reads `explore: floor`; the Decision card shows `step NW  1.0000
frontier 1`, `step NE`, `step N` and `search`, each `0.1111`, with every score ending at the same
pixel column regardless of the action words' length, and the Explain button below.

### Deferred (after the review round)

To `docs/ideas.md`: the real Mode, speed mode and their intervals (stories 5.5 to 5.7); Explain's
click while a Run plays; the Decision card's height arbitrating room with story 5.4's sections; the
Goal line's two-line cap; `Interact`, `PickUp`, `OpenChest`, `Buy`, `Unlock` and `DismissPrompt`'s
labels are plain fallback words (`"interact"`, `"open"`, ...) since the epic named only some kinds --
a later story that wants richer text for these has a single exhaustive switch to extend, not a search
for one.

## Fairness review round (Explain's hot area, an identity pin, and a generator-purity test)

The fairness reviewer found no leak (nothing here reads what a player at the screen could not), and
three should-fixes.

### #5: Explain's `PointerArea` could swallow the executor's own synthetic taps

`ActionExecutor.press` (`ActionExecutor.java:420-428`) presses a window's button by queuing a
`PointerEvent` DOWN and UP directly (`PointerEvent.addPointerEvent`) -- the door on the far side of
`InputHandler`'s multiplexer, which is where `InputLock` sits and which the executor never passes
through. `PointerEvent`'s dispatch (`Signal`, stack mode) tries every registered `PointerArea` newest
first and stops at the one that consumes the tap. The Panel is rebuilt on every new `GameScene`
(ADR-0013, "Scene lifetime"), which can happen after a window already opened and registered its own
button's `PointerArea` -- so Explain's listener can end up in front of one it should never compete
with. `Button.java:160` sets `hotArea.active` from the button's own `visible`, not its `active`, so a
visible-but-should-not-be-clickable Explain (a Run playing) was never actually made unable to consume
a tap: only `.visible` was gated, never `.active`.

- **A. Give the executor a way to mark its own taps so a `PointerArea` can tell a real click from a
  synthetic one.** Rejected: it would mean touching `ActionExecutor` and `PointerArea` itself (an
  SPD-classes type) to carry a new flag through every `PointerEvent`, for a distinction that already
  has a simpler answer -- Explain should not be clickable by anyone right then, real click or not.
- **B. Move the Panel to the back of the dispatch stack, or re-register it last every frame.**
  Rejected: it does not fix the actual defect (Explain visible-and-clickable during a Run), only
  reduces how often the ordering happens to matter, and depends on assumptions about how the game
  orders its own windows' `PointerArea`s that this module does not own.
- **C. `explainButton.active = content.present() && !inputLocked`, threaded from `InputLock.locked()`
  through `OverlayGame` → `PanelDock` → `Panel` → `DecisionCard.content`.** Chosen. `Gizmo.isActive()`
  walks the parent chain (`active && parent.isActive()`) fresh on every call, so setting the button's
  own `active` (not the hot area's) is enough regardless of whether `Button.update()` has run that
  frame; `PointerArea.onSignal` returns `false` for any tap while inactive
  (`PointerArea.java:61-63`), so it never consumes and the dispatch falls through to whatever is
  really under it. This is the same design already chosen for Explain (the story's "Explain control's
  input, under `InputLock`" design note): active only when input is not locked, now enforced against
  every tap, not only a human one.

Pre-mortem: *A future Panel control forgets this and reintroduces the bug.* Recorded in
`docs/ideas.md` ("A second, general instance of the bug the fairness review found in Explain") as
something 5.6's controls row should give every button, not re-derive per button.

Also fixed: `Panel`'s stale Javadoc ("It has no pointer area"), which stopped being true the moment
Explain existed; it now says which parts of the Panel have none and what gates the one that does.

### #2 and #3: two more tests

- `EmbeddedSnapshotTest.a_served_wait` now has `Counting.decide` capture the Observation it was
  handed, and asserts the snapshot's is the *same object*, not merely an equal one -- pinning that
  the Panel labels an Action from the Brain's own Observation, not a copy that could quietly drift
  from it.
- `PanelContentTest.content_does_not_draw_from_the_generator`: rendering is read-only. `RngControl`
  itself keeps no draw count to assert against (its own Javadoc says so), so this seeds
  `com.watabou.utils.Random` directly, draws one reference number, reseeds identically, calls
  `Panel.content` fifty times (alternating Decisions, `inputLocked`, and toggling Explain a hundred
  times in between), and holds that the next number drawn is the same reference -- nothing in the
  whole render path touched the Run's own generator.

### What changed

- `InputLock.locked()` is threaded from `OverlayGame.update()` through `PanelDock.step`/`.frame`,
  `Panel.content` and into `DecisionCard.content` as `inputLocked`.
- `DecisionCard.refresh()` sets `explainButton.active = content.present() && !inputLocked` alongside
  `.visible`.
- `Panel`'s class Javadoc corrected.
- `EmbeddedSnapshotTest.Counting` captures the Observation it decided from; `a_served_wait` asserts
  identity against `snapshot.observation()`.
- New `PanelContentTest.explain_does_not_steal_a_synthetic_tap_while_locked` and
  `.content_does_not_draw_from_the_generator`; `full_panel_shows_everything` and
  `no_decision_yet_on_a_full_panel` gained `explainButton().active` assertions for the unlocked and
  the no-Decision cases.

### Tests

`:overlay:test` in full: 20 classes, all passing (`PanelContentTest` now 7, two new).
`:harness:test`'s `EmbeddedSnapshotTest` and the other touched classes all still passing.

| Test | Holds |
|---|---|
| `PanelContentTest.explain_does_not_steal_a_synthetic_tap_while_locked` | a window's own button placed exactly where Explain is, registered before the Panel is rebuilt (the vulnerable order); a synthetic DOWN/UP at that point, reproduced the way `ActionExecutor.press` makes one, reaches the window's button, not Explain, and `explaining()` stays false |
| `PanelContentTest.content_does_not_draw_from_the_generator` | fifty calls to `Panel.content` and a hundred Explain toggles, between two draws from an identically reseeded generator, draw nothing: the next number is the same reference one |
| `PanelContentTest` (updated) | Explain is `active` when a Decision is present and input is not locked; not `active` with no Decision, even when input is not locked |
| `EmbeddedSnapshotTest.a_served_wait` (updated) | the snapshot's Observation is the same object the Brain's `decide()` was handed, not merely an equal one |

### Mutation battery

A control run of `:overlay:test` and the touched `:harness:test` classes passed first; then 4 more
mutants targeting the active-flag logic (28 in total across all three rounds), each planted, run,
killed, and reverted.

| Mutant | Killed by |
|---|---|
| P1: `explainButton.active` drops the `!inputLocked` term (active whenever a Decision is present, locked or not) | `PanelContentTest.explain_does_not_steal_a_synthetic_tap_while_locked` |
| P2: `explainButton.active` drops the `content.present()` term (active whenever input is not locked, even with no Decision) | `PanelContentTest.no_decision_yet_on_a_full_panel` |
| P3: `DecisionCard.content` never stores its `inputLocked` parameter (the field stays `false`) | `PanelContentTest.explain_does_not_steal_a_synthetic_tap_while_locked` |
| P4: `Panel.content` stops passing `inputLocked` through to `card.content` (hardcodes `false`) | `PanelContentTest.explain_does_not_steal_a_synthetic_tap_while_locked` |

### Deferred (after the fairness review round)

To `docs/ideas.md`: every future Panel button (5.6's controls row, the speed selector, the steppers)
needs the same active-while-locked discipline Explain now has, since `ActionExecutor.press` bypasses
`InputLock` for any window button, not only ones that happen to overlap the Panel today; a shared base
that gates `active` from one flag `PanelDock` sets once a frame would make it a property of the class
rather than something each control has to remember on its own.
