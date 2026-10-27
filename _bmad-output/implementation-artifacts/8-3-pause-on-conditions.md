---
title: 'Story 8.3: Pause-on conditions'
type: 'feature'
created: '2026-10-27'
status: 'review'
baseline_commit: '06a7153cc'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent, do not modify unless human renegotiates">

## Intent

**Problem:** A person wants to let the bot run and still be stopped at the moments they care about (FR-45).

**Approach:** A pure `PauseOn` function over the enabled conditions, the Observation and the Decision about to run;
`ControlState` looks at each wait once, when its Decision has landed and before a credit executes it, and moves to
PAUSED with the Decision still pending. Settings are an `OverlaySettings` value (all off, HP threshold 30%) that story
8.4's screen edits.

## Acceptance Criteria (from epics.md Story 8.3, with the tests that hold them)

1. A pure `PauseOn.first` returns the first condition that holds, or none, from the Observation and the Decision
   only, in a fixed order (item use, unknown item, stairs, boss floor, HP, new enemy).
   `PauseOnTest`: a firing case and a quiet case for each of the six, `two_at_once_report_the_first`, and the
   defaults (`settings_defaults`).
2. In RUNNING a holding condition moves the Overlay to PAUSED at that Input wait before the Action executes; the
   Mode strip reads "PAUSED: before stairs"; Resume or Next Step executes the pending Decision once.
   `PauseOnControlsTest.pauses_before_executing_and_resume_executes`, `next_step_executes`,
   `a_later_wait_pauses_again`.
3. A condition that holds in PAUSED, HUMAN or replay does nothing, and does not fire again for the same wait after
   Resume. `PauseOnControlsTest.nothing_in_paused` and the Resume half of the first test. HUMAN and replay never
   reach the evaluator: `ControlState` exists only for a Brain's Run and evaluates only in RUNNING.
4. The pause is a `mode` record of the existing kind; no log format changes.
   `PauseOnRunTest` reads the Mode records of the logged Run.
5. A Run with every condition enabled and resumed each time has the same Action list as with none enabled, over a
   `standard`-set seed, on `EmbeddedHost`. `PauseOnRunTest.the_feature_asks_and_does_not_change_play`.
6. Settings come from `OverlaySettings` with defaults all off and 30%. `PauseOnTest.settings_defaults`.
7. Fairness: the PR is labelled `fairness` and carries the `fairness-reviewer` verdict.

</frozen-after-approval>

## Design notes

Alternatives for where to look: (1) in `EmbeddedRun`, before `serve()`; (2) in the Panel's draw; (3) in
`ControlState.frame`, which already decides when a credit is given. (3) chosen: it is the one place that owns the
Mode, and it needs only a read of the pending wait. Alternatives for Fast: (1) leave it unheld and miss Decisions;
(2) hold the Run whenever a condition is enabled. (2) chosen; with none enabled Fast is unchanged.
Pre-mortem: a pause that ate a wait (it does not, the Decision stays pending), a condition that fires at every wait
of a stretch (boss floor and HP fire on entry), a second path for the first wait after a settings change (the
baseline is forgotten, so nothing is "new").

The conditions: an unknown item is a potion, scroll or ring whose name is not in the journal's identified list, or a
weapon or armour whose level is not shown. A new enemy is a hostile actor of a name more of which are in view than at
the previous wait. A Run whose agent gives no Decision (the random agent) has nothing pending, so only the Brain's
Runs are stopped; the story is about the Brain.

## Results

`PauseOnRunTest` over the `standard` Warrior seed of `SewersRunSmokeTest` to a 1500-turn cap: 966 waits, 81 pauses
(HP below 30%, before item use, before stairs, new enemy), Action lists equal. No boss floor is reached in that
stretch; the boss condition is held by `PauseOnTest.on_boss_floors`.

## Live-session checklist (the product owner's acceptance; no agent has opened a window)

There is no way to set a condition from the game until story 8.4 ships the settings screen, so this story is accepted
headless; the live pass for the Mode strip wording belongs with 8.4. When it does:

- [ ] With "before stairs" on, press Resume: the bot stops with the strip reading "PAUSED: before stairs" and the
      Decision card showing the descend about to run.
- [ ] Resume again: the bot descends and does not stop for the same stairs twice.
- [ ] With "HP below" on at 30%: the bot stops once when HP crosses below, not at every wait under it.
- [ ] At Fast as it can with a condition on, the Run still plays at speed between stops.
