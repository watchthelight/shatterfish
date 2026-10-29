---
title: 'Story 10.15: Prison food budgeting'
type: 'feature'
created: '2026-10-29'
status: 'review'
baseline_commit: 'efbc02222'
review_loop_iteration: 0
context: []
---

## Intent

**Problem:** 26 of 30 Skeleton deaths and 23 of 31 Warrior deaths on depths 6 and 7 were starving on the last Decision.

**Approach:** Find why from the `--debug-record` files, then move only what the evidence supports. The hero is out of
food by depth 4 in 93 of 240 Runs; feeding it at will lifts reach of depth 5 from 52 to 71 of 120 and all of that comes
from depths 1 to 4. The largest spend that was not a wound was the rest after a test. The kept change is the Playbook
field `identify.rest_hp_per_mille` (300 in `shatterfish.json`, 1000 elsewhere, the old rest): that rest begins only at or
under that share of health. Ten variants and the dead ends (frugal mode, floor allowance, rest while hungry, hungry-leave)
are in `docs/results/2026-10-29-e10-w2-prison-food.md`.

## Acceptance criteria and where they are held

1. A screen where the old Policy rests off a scratch and the budget keeps exploring, and one where it still rests:
   `PrisonFoodBudgetTest`.
2. Numbers read from the Codex rules page, with a test reading the file: `PrisonFoodBudgetTest.numbers_come_from_the_codex`.
3. Information parity: only the hero's hp and max hp; `fairness-reviewer` found no violation.
4. No new Policy, so the `test-item/rest` CoachLine sentence is unchanged.
5. Rig numbers against `origin/main`, all six classes plus Warrior 80: the results page. Pooled reach of depth 5 104 to
   118 of 240; Warrior 80 neutral; the share of deaths starving on the last Decision did not fall (51 to 50 percent).

## Not done

Fetching food seen and left behind, and diving when the larder is empty, are the next levers.
