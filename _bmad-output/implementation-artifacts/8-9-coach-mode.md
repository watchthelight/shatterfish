---
title: 'Story 8.9: Coach mode'
type: 'feature'
created: '2026-10-27'
status: 'review'
baseline_commit: 'f34d2f8df864c56dfcb5f7dcca63ece69b7bfad9'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent, do not modify unless human renegotiates">

## Intent

**Problem:** A learner wants the bot's advice before each of their own moves without the bot playing for them
(FR-47, UJ-4). Story 8.8 made the sentence; nothing yet lets a person ask for it.

**Approach:** Coach mode is a switch inside HUMAN. The Brain already shadows every wait of a HUMAN Run (story 5.9);
with the switch on, the Decision card shows the sentence of the shadow Decision for the wait in front, and
nothing else about the Run changes. The switch is a Controls button and a hotkey, and writing it is one `mode`
record of the existing kind.

## Acceptance Criteria (from epics.md Story 8.9, with the tests that hold them)

1. With the coach on in HUMAN, at each wait the person is to move the Brain has decided as it does for the shadow
   Decision, the card shows the sentence on its own line over the table, and the bot executes nothing.
   `CoachPanelTest.the_sentence_follows_the_switch`, `CoachRunTest.the_learner_disagrees`; the shadow and its
   non-execution are story 5.9's `ShadowDecisionTest`.
2. The coach is a toggle in the Controls with its own hotkey (no default key, because 8.6 takes F12; bind it in the key-binding screen), off by default, and a setting of HUMAN and not a
   fourth Mode: dim in RUNNING and PAUSED, and once the Run is over, with the reason shown. (REPLAY is not a Mode of
   the Overlay; the Replay session is story 8.5, unbuilt, and a log read in the Rig has no Panel.)
   `CoachStateTest.refused_outside_human`, `CoachPanelTest.disabled_with_the_reason`, `HotkeyTest`.
3. Switching writes a `mode` record of the existing kind, so no log format changes, and a log with coach advice
   replays as an ordinary log. `CoachRunTest.coach_changes_nothing`, `a_coached_log_replays`.
4. The advice is computed on the Brain's worker while the person thinks; a move made before it arrives is taken at
   once and the late advice is dropped from the card and never shown against the wrong wait; the test waits on a
   condition with a deadline. `CoachRunTest.late_advice_is_dropped`, `CoachPanelTest.late_advice_is_not_shown`.
5. A coached Run's game state after the same human Actions equals an uncoached Run's: the same scripted moves are
   played with and without the coach and the Observation hash is compared at every wait, with a control (one
   changed move) that shows the comparison can tell. `CoachRunTest.coach_changes_nothing`.
6. The Brain's updates of its Beliefs and Memory happen exactly as in HUMAN: the shadow is asked at every wait
   whether the coach is on or not, and the shadow Decisions of the two Runs are equal wait by wait. Same test.
7. UJ-4 headless: the scripted learner takes a move the coach disagreed with; the log holds the human Action, the
   shadow Decision and the coach toggle; the Decision log shows the mode line, the greyed shadow line and the human
   line. `CoachRunTest.the_learner_disagrees`. The Replay scrubber (story 8.7) is unbuilt, so what it would read is
   checked in the log itself.
8. The story carries the live-session checklist below. The PR is labelled `fairness`, with the reviewer's verdict.

</frozen-after-approval>

## Design notes

Alternatives: (1) a fourth Mode (refused by the story and by ADR-0013's three); (2) a new `Control` in the
enablement matrix (every matrix test would then say something about a control that is not a Mode's); (3) a button
and state beside the matrix, enabled by one function of Mode and over, `ControlEnablement.coachReason`. (3) chosen:
the matrix keeps meaning "what a Mode allows", and the reason strings come out of the same function that enables
the button, so a dim button and its reason cannot disagree. For the record: (a) a new `RunLog` kind, refused by the
story; (b) a `mode` record whose speed word says "player, coach", chosen, since nothing reads the word and a
reader of the log still sees `HUMAN`; (c) no record at all, refused: the log would not say when the learner was
being advised, which UJ-4's journey needs.

Pre-mortem: the coach left on across a hand back (it is turned off the frame the Mode is not HUMAN, so a later
takeover starts without it); advice shown against the wrong wait (the card shows the sentence only when the
snapshot's `shadowCurrent` holds, and the test checks every frame, not the end state); the hotkey adding an upstream
hook (it needs one string in `windows.properties` for the key-binding screen's label; that is one line inside hook
row 10, which already owns that file, so the ledger gains no row and its digest line is updated); the row growing a
third line at the Panel's narrowest width (`ControlsRowTest` still holds two to four rows).

The sentence stays where story 8.8 put it, on its own line over the table, inside the card, under the headline. In a
person's Run that line used to be shown greyed with the rest of the shadow; it is now shown only with the coach on, in
the card's chosen colour, because advice nobody asked for is not what a learner who left the coach off wants.

The Run's only change is `EmbeddedRun.coach(boolean)`, which writes the record at the wait in front and does nothing
for a Run the person does not play. No `brain`, `Observer`, executor or Rig code changed.

## Results

Four real-Run tests over the first `standard` Warrior seed: a coached and an uncoached Run of thirty scripted moves have
equal Observation hashes at every wait, equal shadow Decisions, and differ in the log by exactly two `mode` records; the
control run with one move changed parts from the others; the coached log verifies and replays with every hash matching;
a gated Brain's late advice is never shown against another wait in any frame; the learner who disagreed with four
sentences is in the log and on the Decision log.

## Live-session checklist (the product owner's acceptance; no agent has opened a window)

- [ ] The Coach button (or the hotkey, once bound) in a HUMAN Run puts one line over the Decision card's table, and pressing it again removes it.
- [ ] The advice arrives before you have decided, reads as one sentence, and never covers the map.
- [ ] Moving before the advice arrives is not slowed and the old advice does not flash against the new screen.
- [ ] In a Run the bot plays, the button is dim and the hotkey says in the Mode strip why; Take over makes it live.
- [ ] After Hand back and a second Take over the coach starts off.
- [ ] The Controls row still fits at the narrowest Panel width.
- [ ] The log of the session verifies, and the Decision log shows `mode: HUMAN player, coach`.
