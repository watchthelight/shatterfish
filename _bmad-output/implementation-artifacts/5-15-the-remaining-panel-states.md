---
title: 'Story 5.15: The remaining Panel states'
type: 'feature'
created: '2026-10-25'
status: 'review'
baseline_commit: '9657c7b49'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent, do not modify unless human renegotiates">

## Intent

**Problem:** A Brain that throws or offers no Action ends the Overlay's Run (or, for an exception other than
`CannotDecide`, crashes the render loop), and the Panel has no words for either, so the human cannot trust what it
shows.

**Approach:** Answer such a Brain with a wait the Run chooses itself, say so on the Decision card, log it, and
pause the Overlay; hold the two rows that were already true (hero busy, no Run) and every other state of UX-DR7
in one `PanelStateTest`.

## Acceptance Criteria (from epics.md Story 5.15, with the tests that hold them)

1. **A Brain that throws produces a Decision of wait, a card reading brain error with the exception class, a log
   line, and PAUSED; the game never crashes.** Tests: `BrainTroubleTest.a_throwing_brain_waits_and_pauses`,
   `PanelStateTest.brain_error`.
2. **No valid action shows its own card text, waits once, and after three in a row enters PAUSED and logs it.**
   Tests: `BrainTroubleTest.no_valid_action_pauses_on_the_third`, `.a_decision_resets_the_count`,
   `PanelStateTest.no_valid_action`.
3. **Hero busy dims the pressed control until the hero is ready, then applies the change.**
   Test: `PanelStateTest.hero_busy`.
4. **No Run hides the Panel; the Overlay attaches when a scene appears with a living hero.**
   Test: `PanelStateTest.no_run`.
5. **`PanelStateTest` reaches each state of UX-DR7 and asserts content and enablement.** Test: `PanelStateTest`
   (twelve states).

</frozen-after-approval>

## Decisions

The owner is away and delegated; each is recorded in the PR body too. The ADR-0013 amendment carries the design.

- **Only the Overlay recovers.** `Host.recoversFromBrainTrouble()` (default false): the Rig and the headless loop
  still count a Brain error as an ending (ADR-0012), since a Rig number must not hide a broken Brain.
- **The wait is the Run's, through the ordinary path.** Credit, executor, record and history are the same; the
  record carries a Decision of the Overlay's own words and no Belief.
- **Forced pause lands in PAUSED with Next Step** and writes a `mode` record with speed `Next Step`.
- **The Overlay's wait is not a Brain refusal**, so it never counts toward the refusals that end a Run. A wait
  rejected at a Prompt leaves the Run paused at the same screen; the person decides.
- **Hero busy and No Run needed no new behaviour**; they are named in code (`PanelDock.attaches`) and held by test.

## Not here

- A Brain whose thinking never lands (a hung worker) is the watchdog's (story 5.1), not this story's.
- The surface scene still hosts no Panel (story 5.14's deferral).
