---
title: 'Story 8.6: The Beliefs view'
type: 'feature'
created: '2026-10-27'
status: 'review'
baseline_commit: 'ef7b427bb'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent, do not modify unless human renegotiates">

## Intent

**Problem:** The Belief summary shows the three most ambiguous items. A person who wants to check a belief against
what they know, or catch a bad one before it costs a Run, needs everything the Brain believes at an Input wait (FR-46).

**Approach:** An `api` value `BeliefView` that the Brain builds on its worker from its own `Beliefs`, carrying the hash
of the Belief the Wait record stores. The Panel gains a scrollable Beliefs section, opened from a Controls-row button
with its own hotkey (F12, row 10), sorted most ambiguous first, shown in PAUSED and HUMAN (REPLAY joins when story 8.5
adds the mode).

## Acceptance Criteria (from epics.md Story 8.6, with the tests that hold them)

1. `BeliefView` (api) carries, for every unidentified appearance the Brain tracks, its candidates and probabilities in
   the Brain's order; the floor facts with their reason; the unseen-monster memory with the wait each was last seen;
   every guaranteed drop's counter; each already formatted as the summary's lines are. The brain builds it from its own
   `Beliefs`; its `hash` is the Belief hash the Wait record stores.
   `BeliefViewTest`: contents of a constructed `Beliefs`, only unseen monsters listed, no true identity (candidates are
   the appearance's own possible identities), the hash of `BrainDecider.belief()` equals `beliefView().hash()`.
2. The Panel's Beliefs section is opened by a Controls-row control and the hotkey F12 (row 10, not a new hook), sorted
   most ambiguous first, scrollable, available in PAUSED and HUMAN and closed in RUNNING.
   `BeliefViewContentTest`: the order, the scrolling, the availability by Mode. `ControlEnablementTest`/`HotkeyTest`
   and `KeyBindingConflictTest` take in the new control and key.
3. With the Oracle flag on, and only then, a true identity appears beside the Brain's belief with the `oracle` prefix.
   `BeliefViewContentTest` (gating on constructed views), `OracleMarkingTest.the_beliefs_view_rows` (dock level, oracle
   Run and fair Run), and `OverlayOracleGateTest` gains the case that without the flag the same view has no such line.
4. The Brain builds the view on its worker and the Panel gets it as an immutable value in the Snapshot.
   `BeliefViewRunTest` on `EmbeddedHost`: a view at three waits of a `standard`-set Run, each hash equal to the Belief
   hash the Run log wrote at that wait.
5. No play changes: the Rig's `smoke` columns are unchanged by this story (the view is read-only; `PauseOnRunTest`-style
   equal Action lists are held by `BeliefViewRunTest.the_view_does_not_change_play`).
6. Fairness: the PR is labelled `fairness` and carries the `fairness-reviewer` verdict. Hook row 10 gains one string.

</frozen-after-approval>

## Design notes

Where the view lives in the Snapshot: (1) inside `BeliefSummary` (hash timing is wrong, the summary is built before the
Belief is handed on); (2) a new Snapshot component with a delegating old constructor. (2) chosen. The view is built after
`handed()` so its hash is the one `RunLoop` logs for that wait, from the `Beliefs` read before it, as the summary is.
Alternatives for the control: a Panel-header button, a Controls-row button. The Controls row chosen: it already wraps,
has the disabled-by-matrix rule and the hotkey path. Pre-mortem: a view for the wrong wait (hash check in the Run test),
a hot area stealing a synthetic tap (the section's pane is gated on `inputLocked` like the Decision log), the oracle row
leaking into the Snapshot (it is made in the dock, from the sidecar, as the summary's rows are).

## Live-session checklist (the product owner's acceptance; no agent has opened a window)

- [ ] Pause the bot, press Beliefs or F12: the section replaces the Decision log, most ambiguous appearance first.
- [ ] Scroll with the mouse wheel or drag; the list keeps its place across a Next Step unless the Belief changed.
- [ ] Resume: the section closes; Take over: it can be opened in HUMAN.
- [ ] With `--oracle`, `oracle:` lines appear under their appearance, and not without it.

## Results

`BeliefViewRunTest` over the `standard` Warrior seed of `SewersRunSmokeTest` to a 600-turn cap: 313 views taken, one
per wait, each with the Belief hash the Run log wrote at that wait (zero mismatches), and the Action list equal to the
same Run with no view taken. Rig numbers: this story changes nothing the Brain decides (`BrainDecider` builds one more
value after `handed()`); `:rig:direction` was not run, since the columns cannot move, and `BeliefViewRunTest` is the
equal-Action-list check.

## Decisions and caveats

- The Beliefs view is a Controls-row button (`Control.BELIEFS`) with the hotkey F12 (`Hotkey.BELIEFS`, hook row 10, one
  string added to the existing windows.properties site, ledger digest updated). It is enabled in PAUSED and HUMAN.
  REPLAY is a Mode story 8.5 adds; it joins the matrix there (one line in `ControlEnablement.of`). EXPERIENCE.md's
  state-patterns table is the UX spine's and is not edited here.
- It replaces the Belief summary and the Decision log in the Panel while open, and closes when the Run goes RUNNING.
- Only monsters out of view are listed as the unseen-monster memory; monsters in view are on the screen.
- F12 is the last free default key (F6 to F11 are taken). Stories 8.4, 8.5, 8.7 and 8.9 that want a hotkey register
  one without a default (the registration declines a taken key and the control stays rebindable in the game's screen).
