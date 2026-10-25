---
title: 'Story 5.8: Take over and hand back'
type: 'feature'
created: '2026-10-25'
status: 'review'
baseline_commit: 'c1fd5d855'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Take over and Hand back were dim. A person could play a whole Run (story 5.9's slice) but could not
rescue a Brain's Run or give it back.

**Approach:** A change of hands is a thing the Run does at an Input wait. Take over opens the hero to the person at
the next Input wait and the Brain only shadows from there; Hand back lands in PAUSED with a Decision computed from
the Observation after the person's last turn.

## Acceptance Criteria (from epics.md Story 5.8, with the tests that hold them)

1. **Take over opens the gate at the next Input wait; Hand back lands in PAUSED with a fresh Decision computed from
   the current Observation.**
   Tests: `TakeoverHandbackTest.a_takeover_and_a_hand_back` (Mode records HUMAN at k=2, PAUSED at k=4; the Decision
   is the Brain's call 4, made from wait 4's Observation, not asked twice),
   `.a_takeover_waits_for_a_thinking_brain`, `.a_takeover_with_no_wait_in_front`, `.a_plain_brain_is_asked_afresh`.
2. **A control pressed mid-animation stays dim until the hero is ready, then applies.**
   Tests: `ControlStateTest.take_over_queued_while_the_hero_is_busy`, `.take_over_gives_no_credit_while_it_waits`,
   `ControlsRowTest` (the pressed control and the rest are dim until the Run has changed hands),
   `TakeoverHandbackTest.a_hand_back_waits_for_an_open_wait`.
3. **A takeover and a hand back in a scripted Run assert the Mode transitions and that Hand back lands in PAUSED
   with a Decision computed after the human's last turn.**
   `TakeoverHandbackTest` (above) with `ControlStateTest.take_over_and_hand_back` for the Mode transitions.

Handed over from 5.9 and closed here: the log says who played which turn (`actor`), each wait the person had has a
shadow Decision, a replay of the mixed log verifies every Observation hash
(`TakeoverHandbackTest.a_takeover_and_a_hand_back`), and a controller at the takeover marks the Run unverifiable
from that wait (`.a_controller_marks_from_the_takeover`). A key held across a hand back is released into the game
(`InputLockTest.a_hand_back_leaves_no_key_held`). Rig numbers: this story changes no Brain decision, so there is
no direction table.

</frozen-after-approval>

## Decisions

The owner is away and delegated; each is recorded in the PR body too. The architectural ones are in ADR-0013's
story 5.8 amendment.

- **Changes of hands apply at an Input wait, on the UI-role thread, in `EmbeddedRun.frame()`.** The request is
  queued; every control but Explain is dim until the Run has changed hands, and the Mode follows the Run.
- **Take over takes the wait the Brain decided.** The Decision becomes that wait's shadow; nothing is asked twice.
- **Hand back reuses the shadow of the wait it lands on** when the Brain gave one (it was shown the Observation
  after the person's last turn); a Brain without Decisions is asked afresh.
- **Hand back lands in PAUSED, never RUNNING**, with the Decision withheld until a credit.
- **A Take over clears Run N and withdraws the credit**, so the Decision about to execute is not executed.
- **HUMAN Mode shows the player speed**; no speed mode paces a person.
- **The input lock releases keys and presses the person still holds at a hand back.**

## Not here

Hotkeys (5.11); the Brain error and "hero busy" rows (5.15); routing render-thread draws (5.13). A Search followed
at once by a tap is cleared by the game's search animation and so does not replay across drivers; that belongs to
5.13's non-reproducibility of an Overlay Run's frames.
