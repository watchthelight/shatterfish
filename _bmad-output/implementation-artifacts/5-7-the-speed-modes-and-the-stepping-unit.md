---
title: 'Story 5.7: The speed modes and the stepping unit'
type: 'feature'
created: '2026-10-25'
status: 'review'
baseline_commit: 'b50f9c790'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Story 5.6 gave the human a hand on the Run but not its pace: Resume played at Fast, Run N as fast as
the Brain answered, the Brain was asked only at the press so its Decision could not be seen before it was
executed, and the interval stepper was dim.

**Approach:** The unit is one Input wait. A held Run decides the wait in front ahead of the press and shows that
Decision; a credit executes it. Next Step gives one credit (queued if the Brain still thinks); Run N gives N one
at a time and lands in PAUSED; Human play speed gives one per interval (default 1 s, 0.5 s to 5 s); Fast as it
can lets the Run go.

## Acceptance Criteria (from epics.md Story 5.7, with the tests that hold them)

1. **Next Step advances exactly one Input wait; Run N advances N and lands in PAUSED.**
   Tests: `SpeedModeTest.next_step_advances_one_wait`, `.run_n_advances_n_and_pauses`;
   `EmbeddedHoldTest.advance_spends_one_credit_per_wait`.
2. **Human play speed advances one Input wait per configurable interval, default one second, range half a
   second to five; Fast as it can advances as soon as the Brain returns.**
   Tests: `SpeedModeTest.human_play_speed_paces_by_the_interval`, `.fast_advances_when_the_brain_returns`;
   `ControlStateTest.steppers` (range and default).
3. **Switching speed mode never loses a turn, taking effect at the next Input wait.**
   Tests: `SpeedModeTest.switching_never_loses_a_turn`, `ControlStateTest.next_step_speed_pauses`,
   `EmbeddedHoldTest.hold_clears_and_release_frees`.
4. **A Next Step pressed while the Brain is over budget is queued and fires when the Decision lands, with the Mode
   strip showing THINKING.** Tests: `SpeedModeTest.queued_next_step_fires_once`,
   `EmbeddedHoldTest.a_queued_credit_fires_once`.
5. **The steppers hold their values for the session only, with the documented defaults and ranges.**
   Tests: `ControlStateTest.starts_paused_with_next_step`, `.steppers`.
6. **`SpeedModeTest` asserts the advance count for each mode and that a queued Next Step fires exactly once.**
   `SpeedModeTest` (above).

Handed over from 5.6 and closed here: the pending Decision is shown (`ControlsRowTest.the_pending_decision_is_headed`,
`EmbeddedHoldTest.held_run_decides_and_waits`) and a re-asked stale wait spends no credit
(`EmbeddedHoldTest.a_stale_answer_spends_no_credit`).

</frozen-after-approval>

## Decisions

The owner is away and delegated; each is recorded in the PR body too. The architectural one is in ADR-0013's
story 5.7 amendment.

- **Decide ahead of the press.** `EmbeddedRun` confirms and asks the Brain while held; the landed Decision is
  withheld until a credit; the credit is spent at execution, after the stale check.
- **One Next Step queues.** A press while a credit is outstanding is ignored.
- **Human play speed's first credit is at once**, then one per interval measured from the last credit.
- **A wait with a window in front costs no credit** (a Prompt, whoever opened it), so no control being off can stall a held Run, and Run N counts hero turns.
- **Resume from Next Step goes to Human play speed**, the readable pace.
- **The headline "next press executes:" shows only in PAUSED**; while RUNNING at Human pace the pending Decision
  is shown without a headline, since it executes on its own.
- **Pause withdraws an unspent credit but not a withheld Decision**, which executes on the next press.

## Not here

Take over and Hand back (5.8); hotkeys (5.11); Brain error and "hero busy" rows (5.15); routing render-thread
draws (5.13).
