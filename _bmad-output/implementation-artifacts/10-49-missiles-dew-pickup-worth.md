---
title: 'Story 10.49: The missile, dew and pick-up worth levers at full size'
type: 'feature'
created: '2026-11-09'
status: 'done'
baseline_commit: '28f4fe38d'
review_loop_iteration: 0
context: []
---

## Intent

**Problem:** issue #412 merged nine levers dormant (PR #426) and closed on a re-measure of 80 Runs where a difference of
one or two Runs is noise. Which of them lift the Goo kill rate (47% on main, target 90 to 100%)?

**Approach:** play each lever alone on the two ALL-40 slices (salt bases 1000 and 3000, 240 Runs each, `--jobs 4`),
baseline pinned to the merge-base SHA, and keep what passes the keep rule (pooled over both slices not worse in reach
depth 5 and Goo kills, no class 8 or more worse). Food worth went in separately (#457) and is not redone. A lever that
passes is played again on current main before its pull request.

## Alternatives weighed (before building)

1. Re-run the old 40-Run slices with more salts: same noise, rejected.
2. One lever at a time at full size, a control first to show the off state is a no-op. Chosen.
3. Tune the numbers with SPSA first (#384 already moved them a step or two and found nothing): rejected, the question is
   whether any lever helps at its measured value.

## Pre-mortem

- A dropped lever may be dropped at the wrong value; the results page says so and the lever stays in the registry at 0.
- Baseline and current must be the same game when the lever is off: the control matched on both slices before any lever ran.

## Outcome

- `ranged.fetch_steps` kept at 2: on main 9cd3af90e reach depth 5 280 to 285, Goo kills 229 to 241 of 480, worst class -2.
- Played again as the committed default on main c6b3b9dc5: reach depth 5 293 to 299, Goo kills 241 to 253 of 480.
- Note for later measurements: `:rig:direction` plays a baseline ref with the current api module, so a changed tunable
  default moves the baseline too; play the old default with `--tunables` instead.
- Dropped: `ranged.min_damage_per_mille`, `pickup.dew_skin_below`, `pickup.missile_reach`, `explore.grass_skin_below`,
  `descend.trample_reach`, and the weights `heal`, `upgrade`, `useful`.
- Results: `docs/results/2026-11-09-10-49-missiles-dew-pickup-worth.md`.
