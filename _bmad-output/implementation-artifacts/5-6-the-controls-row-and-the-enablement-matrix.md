---
title: 'Story 5.6: The controls row and the enablement matrix'
type: 'feature'
created: '2026-10-25'
status: 'review'
baseline_commit: '06b98ad0f'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The Panel shows what the bot decided but gives the human no hand on the Run: it plays at
once, as fast as the Brain answers, and nothing on the Panel can be pressed while it does (the input
lock swallows every press, so even Explain worked only after the Run was over).

**Approach:** The Controls row (UX-DR7, FR-39) as the Panel's last section: Pause and Resume, Next Step,
Run N with its count stepper, the speed selector with its interval stepper, Take over and Hand back, and
Explain, wrapping to two rows. A pure enablement matrix says which act in which state; a dim control takes
no press. The Run opens held, PAUSED with Next Step, and the row turns the hold.

## Acceptance Criteria (from epics.md Story 5.6, with the tests that hold them)

1. **The row holds the eight controls and wraps to two rows.** All as the game's own `RedButton`s.
   Tests: `ControlsRowTest.the_row_wraps_to_two_rows`, `.narrow_wraps_further`.
2. **A control whose action is impossible in the current state is dim and non-interactive.**
   Tests: `ControlsRowTest.every_state_draws_its_matrix`, `.a_window_in_front_dims_everything`,
   `.run_over_is_dim`, `.taps_reach_the_controls` (a tap on a dim button does nothing),
   `ControlStateTest.enabled_acts_and_dim_does_not`.
3. **The Run starts in PAUSED with Next Step.**
   Tests: `ControlStateTest.starts_paused_with_next_step`, `ControlsRowTest.opens_paused`,
   `EmbeddedHoldTest` (the held Run asks for no wait, and is not ended by idling).
4. **`ControlEnablementTest` asserts the full enablement matrix of UX-DR7 for every state, so no state
   leaves a control enabled that would do nothing.**
   Tests: `ControlEnablementTest.the_matrix_of_each_state`, `.thinking_is_not_a_row_of_its_own`,
   `.run_n_only_when_paused`, `.human_disables_everything_but_hand_back_and_explain`,
   `.unbuilt_controls_are_dim`; and the "does something" half, `ControlStateTest.enabled_acts_and_dim_does_not`.

</frozen-after-approval>

## Decisions

The owner is away and delegated; each is recorded in the PR body too.

- **Reach through the lock.** `InputLock` passes a press on the Panel while it holds; the Panel's hot areas
  are off while a window is in front. Without this no control could be pressed during a Run. ADR-0013
  amended (the story 5.3 option (a) it had rejected, with the synthetic-tap reasoning answered).
- **The seam is `EmbeddedRun.hold` and `advance`.** A credit per Input wait confirmed, spent before the
  Brain is asked. The Decision is therefore made at the press, not ahead of it; showing the pending
  Decision on the card (and the "next press executes" headline) is story 5.7's, so the card never shows
  that headline now (it would label the last executed Decision as the next).
- **Take over and Hand back, and the interval stepper, are dim until 5.8 and 5.7.** `Capabilities.NOW`.
  The matrix test holds UX-DR7 whole (`Capabilities.ALL`) and the withheld pair separately.
- **Human play speed is skipped by the selector** until 5.7 can pace it; Resume from Next Step therefore
  plays at Fast. Run N plays as fast as the Brain answers until then.
- **The speed selector is one cycling `RedButton`**, not three tabs: it is what F7 does, and one toolkit part.
- **Explain moved from the Decision card to the row**; it is dim with no Decision to explain.
- **Rows of UX-DR7 not built here:** Brain error, no valid action, hero busy, no Run (story 5.15); the
  Run over strip text and path (5.14), though its controls are dim already; hotkeys (5.11).
- **`MIN_PANEL_HEIGHT` grew by the row** (two rows and a gap), so a short window collapses a little sooner.

## Review

Fairness review of the harness seam and the input-lock change: see the PR.
