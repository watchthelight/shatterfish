---
title: 'Story 8.2: The Explain expansion'
type: 'feature'
created: '2026-10-27'
status: 'review'
baseline_commit: '06a7153cc'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent, do not modify unless human renegotiates">

## Intent

**Problem:** Since story 8.1 a Decision carries the Evaluation terms behind each score and, when a Search ran, its
outcomes. The Panel's Explain shows only the Policy and the Safety flags, so a person cannot see why a score is what it is.

**Approach:** Explain expands the Decision card inside the Panel into a scrollable region that shows the Policy, the
chosen Action's term table, each alternative with its score, full reason and term table, the flags, and the Search
block only when the Decision has one. The text is a pure function; the layout is the game's own toolkit.

## Acceptance Criteria (from epics.md Story 8.2, with the tests that hold them)

1. Explain pressed in PAUSED expands the card to show, in order: the Policy, the chosen Action's terms, each
   alternative with score, `why` and terms, the Safety flags, and the Search block only when there is one.
   `ExplainContentTest.with_terms`, `with_search`, `reasons_in_full`; `ExplainPanelTest.follows_the_decision`.
2. The text comes from the pure `ExplainContent`, laid out with `PixelScene.renderTextBlock` in a scrollable Panel
   region (`ExplainView`, the game's `ScrollPane`), no new window, no new asset.
   `ExplainPanelTest.terms_are_a_column` (the numbers end at one edge), `takes_no_tap_when_it_should_not`.
3. A Decision with no terms shows what it has and says nothing of terms, never an empty table.
   `ExplainContentTest.without_terms`, `ExplainPanelTest.terms_are_a_column` (second half).
4. While open, the expansion follows the new Decision at the next Input wait and stays expanded; it collapses on
   Explain again, on the Panel's collapse, and on Take over.
   `ExplainPanelTest.follows_the_decision`, `closes_on_explain_and_on_collapse`, `closes_on_take_over`,
   `closes_when_the_run_is_over`.
5. In the collapsed Mode strip state Explain is disabled with the reason shown.
   `ExplainPanelTest.disabled_when_collapsed`, `ControlEnablementTest.explain_rows`.
6. `ExplainContentTest` asserts order and text for a Decision with terms, one without, one with a Search block.
   `ControlEnablementTest` gains the expansion's rows (`explain_rows`, `explain_reasons_are_words`).
7. A `ScriptedHuman` test on `EmbeddedHost` presses Explain at a real Decision of the `standard` set, reads the
   Panel's content model, and asserts the terms sum to the displayed score.
   `ExplainRealRunTest.terms_sum_to_the_displayed_score` (standard entry 0, WARRIOR).
8. Live-session checklist for the product owner (below).
9. Fairness: the PR is labelled `fairness` and carries the reviewer's verdict.

</frozen-after-approval>

## Design notes

**Alternatives weighed for where the expansion lives.** (a) Keep it inside the Decision card, growing the card: the
card would then need its own scroll and the Belief and log sections would have to shrink around it. (b) A new window:
refused by the story ("no new window") and by ADR-0013's input-lock reasoning. (c) A region of the Panel that takes
the place of the flags, belief and log sections while it is open, between the card and the Controls row. Chosen (c):
the Panel already arbitrates its sections by their heights, and the region simply takes the room the log had.

**The conflict with story 5.6.** 5.6 says a collapsed Panel changes no enablement (the hotkeys act through the same
matrix), while this story says Explain is disabled in the collapsed strip with the reason shown. Resolved without
touching the matrix: `ControlEnablement.explainReason(mode, over, panelFull, hasDecision)` is the Panel's own reason
and is null when Explain acts. The matrix still says what the Run permits. A key press that finds a reason shows it
on the Mode strip for three seconds (`Panel.notice`) and does nothing else.

**What closes it.** Explain again, the Panel's collapse (so it does not return with the Panel), Take over (closed as
the press is made, not when the hand-over lands) and a Run that is over. It stays open across Decisions, scrolled
back to the top when the lines change.

**Term tables.** Rows are `feature` left and the contribution right in the card's own score format
(`Columns.score`), every number ending at one edge. Words wrap by the width left of the widest number, so a long
`why` never pushes a number off. The fallback Policy's closing `tier` term (story 8.1) makes the rows sum to the
score; a Policy that records no terms gets no table and no header.

**Input lock.** The scroll area's hot area is inactive when a window is in front or when the view is closed, the same
rule story 5.3 and 5.4 set for Explain and the log (`ExplainPanelTest.takes_no_tap_when_it_should_not`).

**Test seam.** `EmbeddedHost` and `ScriptedHuman` are package-private in the harness's tests, so the Overlay's test
sources hold a small bridge, `org.shatterfish.harness.agent.ExplainSession`, and depend on the harness's test output.
Nothing in main code sees it.

## Results

- `:overlay:test`: ExplainContentTest 7, ExplainPanelTest 7, ControlEnablementTest 8, ExplainRealRunTest 1, all green.
- Real Decision: the first Decision of standard entry 0 that carries terms is explained through the Panel; the
  content model equals `ExplainContent.of` and the term rows under the chosen row sum to its printed score.
- No play changes: the story touches only the Overlay's display. The Rig's Runs are unchanged by construction (no
  `brain`, `harness` main or `api` file is edited); `:rig:direction` is not run.

## Live-session checklist (product owner, headed game)

Run `./gradlew :overlay:launch --args="--agent brain"` and Pause.

1280x720 window:
1. Press Explain (button or key): the card grows into a region below it listing the Policy, then the chosen Action
   with its terms, then each alternative with score, reason and terms, then the flags. Numbers sit in one right column.
2. Scroll the region with the mouse wheel and by dragging. A long list reaches its end; nothing is clipped half a row.
3. Press Next Step: the region shows the new Decision, still open, back at the top.
4. Press Explain again: it closes. Open it, press Take over: it closes.
5. Open it, collapse the Panel with its toggle: the strip shows. Press the Explain key: the strip says Explain is off
   and why for about three seconds, then the Mode line returns. Restore the Panel: the expansion is closed.
6. A Decision with no terms (for example a Wait forced by a trouble notice): no table, no word about terms.

Wider window (1920x1080 or more): repeat 1 to 3; the region keeps the Panel's 200 UI pixel width and gains height.

Also: with a window open (an item prompt), clicking where the region is does nothing to it.

## Dev Agent Record

Alternatives, pre-mortem and test notes are above. Open question for the product owner: none. The live checklist is
the only unverified part; the Panel was only ever drawn headless.
