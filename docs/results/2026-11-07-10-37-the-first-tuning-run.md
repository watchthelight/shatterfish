# Story 10.37: the first tuning run (a negative result)

Date: 2026-11-07. Code: `main` at `1e5ddb015` for the confirmation, the tuning run played on `b51a2cd31` (see "What was run"). Upstream tag as pinned. Nothing on this page is ranked: every Run is a development Run on the `standard` set; `goo`, `holdout` and `tengu` were not played and salts 7000 and up were not touched.

## Verdict

Verdict: REJECTED.

The candidate is not kept and no Playbook number changes. On neither confirmation slice did the pooled Sequential test accept (both undecided after all 240 pairs), and on slice 1000 four classes killed fewer bosses than the current numbers (Warrior 29 against 32, Mage 16 against 19, Rogue 10 against 12, Huntress 20 against 25), with the Huntress vetoed (24 worse pairs against 16 better). On slice 3000 the candidate killed more bosses pooled (98 against 82) and Mage and Rogue killed fewer; Warrior and Huntress went from fewer bosses on slice 1000 to more on slice 3000, so the two slices disagree in sign for two classes, which is what a candidate fitted to noise looks like. The run is a negative result and the story is done.

## What was run

- Tuning: `:rig:tune --set standard --class ALL --first 8 --salt-base 5000 --iterations 41 --jobs 3 --params all`, all 41 registered numbers including the seven dormant levers of issue #412 (they ship at 0 and the run could move them). It played salts 5000 to 6967 (41 iterations x 6 classes x 8 triples), 3936 Runs, 6 h 20 min of wall clock. The run began on `b51a2cd31` (after #427); `main` moved on while it ran, and the confirmation below is on `1e5ddb015`, tuned vector against the default values at the same commit, so the Brain the candidate is judged on is not the one it was fitted on. That is a handicap for the candidate that the owner asked for, and it is part of why the result is negative evidence about this vector and not about SPSA.
- The run's `tune.json` was lost in a scratch cleanup; its configuration is the line above, which is what the page test uses. The per-pair scores (`pairs/`) are not committed; the trajectory carries both scores and the standard error of every iteration.
- Confirmation: `:rig:direction --set standard --class ALL --first 40 --salt-base 1000` and `3000`, once with the default values and once with `--tunables` set to the candidate, `--jobs 3`, the same triples under the same salts; then `:rig:confirm` pairs the two folders and runs the E3 Sequential test (the e-process, H0 0.50, H1 0.55, alpha = beta = 0.05, 25% missing allowed), pooled and per class. The hypotheses were fixed in the story file before the candidate existed.
- Keep rule (pre-registered in the story file, with the per-class veto added by the product owner): on each slice the pooled test accepts, no class kills fewer bosses than the current numbers, and no class has 8 or more worse pairs than better.
- Budget: about 6 seconds a Run at `--jobs 3`; the salt window 5000 to 6999 holds 2000 pairs, so 41 iterations of 8 per class use it. A first attempt at 20 iterations was stopped after one iteration (the speed showed twice the budget) and a second was killed by a session loss after two of 41; both folders were deleted and the run restarted whole.

## The trajectory

Logs: `docs/results/logs/2026-11-07-10-37-first-tuning-run/trajectory.jsonl`, `docs/results/logs/2026-11-07-10-37-first-tuning-run/candidate.diff.txt`.

Score of a Run is 5 per boss killed plus the deepest depth. Each row is the mean score of the 48 paired Runs of one iteration with the plus vector and with the minus vector, the difference and its standard error.

Mean paired difference -0.100 over 41 iterations; 20 positive.

The mean difference is inside one standard error of zero (a standard error of an iteration is about 0.6 points), and positive and negative differences are about as many as a coin would give. Most single iterations are within two standard errors of zero, so what moved the numbers was largely noise bounded by the schedule: the largest move of any number is about two steps (the schedule's own bound, ADR-0026).

| iteration | plus score | minus score | difference | standard error |
|---|---|---|---|---|
| 0 | 6.042 | 7.250 | -1.208 | 0.657 |
| 1 | 5.771 | 4.396 | +1.375 | 0.599 |
| 2 | 6.542 | 5.375 | +1.167 | 0.599 |
| 3 | 4.521 | 5.750 | -1.229 | 0.608 |
| 4 | 6.062 | 6.000 | +0.062 | 0.647 |
| 5 | 5.271 | 6.583 | -1.312 | 0.604 |
| 6 | 5.146 | 6.083 | -0.938 | 0.634 |
| 7 | 4.979 | 6.125 | -1.146 | 0.590 |
| 8 | 4.750 | 4.500 | +0.250 | 0.554 |
| 9 | 5.250 | 5.812 | -0.562 | 0.720 |
| 10 | 5.000 | 6.354 | -1.354 | 0.768 |
| 11 | 6.229 | 5.792 | +0.438 | 0.682 |
| 12 | 6.333 | 5.625 | +0.708 | 0.679 |
| 13 | 5.729 | 5.958 | -0.229 | 0.647 |
| 14 | 6.062 | 6.708 | -0.646 | 0.815 |
| 15 | 6.000 | 4.958 | +1.042 | 0.502 |
| 16 | 5.042 | 5.500 | -0.458 | 0.432 |
| 17 | 6.458 | 5.292 | +1.167 | 0.584 |
| 18 | 5.583 | 4.771 | +0.812 | 0.528 |
| 19 | 5.917 | 4.875 | +1.042 | 0.542 |
| 20 | 6.875 | 6.000 | +0.875 | 0.681 |
| 21 | 5.417 | 6.646 | -1.229 | 0.667 |
| 22 | 5.146 | 5.688 | -0.542 | 0.619 |
| 23 | 6.188 | 6.625 | -0.438 | 0.789 |
| 24 | 5.896 | 6.979 | -1.083 | 0.589 |
| 25 | 4.729 | 6.250 | -1.521 | 0.602 |
| 26 | 6.167 | 5.708 | +0.458 | 0.572 |
| 27 | 5.458 | 6.646 | -1.188 | 0.619 |
| 28 | 6.354 | 6.188 | +0.167 | 0.739 |
| 29 | 5.750 | 4.979 | +0.771 | 0.529 |
| 30 | 5.938 | 6.125 | -0.188 | 0.627 |
| 31 | 6.542 | 6.208 | +0.333 | 0.717 |
| 32 | 5.833 | 5.771 | +0.062 | 0.646 |
| 33 | 5.625 | 5.604 | +0.021 | 0.600 |
| 34 | 6.333 | 6.188 | +0.146 | 0.704 |
| 35 | 6.333 | 5.354 | +0.979 | 0.717 |
| 36 | 6.250 | 6.292 | -0.042 | 0.702 |
| 37 | 5.604 | 6.333 | -0.729 | 0.687 |
| 38 | 6.896 | 5.833 | +1.062 | 0.689 |
| 39 | 5.229 | 5.604 | -0.375 | 0.729 |
| 40 | 4.917 | 5.542 | -0.625 | 0.566 |

### The candidate

34 of 41 numbers moved.

Largest moves in steps: `identify.scroll_hp_per_mille` -2.22 (1000 to 889: unknown scrolls read at lower health), `chests.mimic_margin_per_mille` +2.09, `descend.per_drop` +2.08, `fight.chase_waits` -2.00 (10 to 2: chase an enemy that dropped out of view for fewer waits), `identify.potion_hp_per_mille` +1.60, `bosses.goo.descent_rest_per_mille` -1.34 (rest to 87% of health before the Goo stairs instead of full), `bosses.goo.drink_per_mille` +1.28. The dormant levers moved a little and not far: `pickup.missile_reach` 0 to 2, `pickup.dew_skin_below` 0 to 2, `descend.trample_reach` 0 to 1, `explore.grass_skin_below` 0 to 2, `ranged.fetch_steps` 0 to 1, `ranged.min_damage_per_mille` 0 to 189; none is a reading on #412, because nothing here was kept.

| number | default | candidate | steps moved |
|---|---|---|---|
| `bosses.goo.descent_rest_per_mille` | 1000 | 866 | -1.34 |
| `bosses.goo.drink_per_mille` | 400 | 464 | +1.28 |
| `bosses.goo.meet_range` | 6 | 6 | 0.00 |
| `bosses.goo.wash_range` | 4 | 4 | 0.00 |
| `bosses.tengu.adjacent_cost` | 6 | 5 | -0.50 |
| `bosses.tengu.bomb_cost` | 22 | 21 | -0.25 |
| `bosses.tengu.fire_cost` | 18 | 17 | -0.25 |
| `bosses.tengu.shock_cost` | 12 | 12 | 0.00 |
| `brain.avoid_radius` | 5 | 6 | +1.00 |
| `brain.avoid_waits` | 40 | 30 | -1.00 |
| `chests.mimic_margin_per_mille` | 1250 | 1564 | +2.09 |
| `descend.allowance` | 500 | 404 | -0.96 |
| `descend.per_drop` | 250 | 354 | +2.08 |
| `descend.rests` | 20 | 24 | +0.80 |
| `descend.trample_reach` | 0 | 1 | +0.50 |
| `explore.grass_skin_below` | 0 | 2 | +0.50 |
| `explore.rests` | 50 | 48 | -0.20 |
| `fight.chase_waits` | 10 | 2 | -2.00 |
| `fight.flights` | 2 | 2 | 0.00 |
| `fight.margin_per_mille` | 500 | 528 | +0.56 |
| `fight.outpaced_range` | 6 | 7 | +1.00 |
| `fight.outpaced_stairs` | 2 | 1 | -1.00 |
| `fight.recall_waits` | 20 | 20 | 0.00 |
| `heal.horizon` | 10 | 8 | -1.00 |
| `heal.redrink_share_per_mille` | 250 | 244 | -0.12 |
| `identify.escape_waits` | 12 | 9 | -0.75 |
| `identify.gain_odds_per_mille` | 200 | 173 | -0.54 |
| `identify.healing_odds_per_mille` | 200 | 172 | -0.56 |
| `identify.inventory_odds_per_mille` | 250 | 305 | +1.10 |
| `identify.potion_hp_per_mille` | 500 | 580 | +1.60 |
| `identify.reach` | 8 | 7 | -0.25 |
| `identify.rest_waits` | 50 | 45 | -0.20 |
| `identify.scroll_hp_per_mille` | 1000 | 889 | -2.22 |
| `pickup.dew_skin_below` | 0 | 2 | +0.50 |
| `pickup.missile_reach` | 0 | 2 | +0.50 |
| `plunge.fit_percent` | 80 | 86 | +1.20 |
| `plunge.safe_hp` | 15 | 14 | -0.33 |
| `ranged.fetch_steps` | 0 | 1 | +1.00 |
| `ranged.min_damage_per_mille` | 0 | 189 | +0.76 |
| `upgrade.hold_until_depth` | 0 | 0 | 0.00 |
| `upgrade.weapon_lead` | 0 | 0 | 0.00 |

## The candidate against the current values

Logs: `docs/results/logs/2026-11-07-10-37-first-tuning-run/slice-1000/runs.tsv`, `docs/results/logs/2026-11-07-10-37-first-tuning-run/slice-3000/runs.tsv` (one line per Run, both sides, from the Run logs, which are too large to commit) and the full direction tables `docs/results/logs/2026-11-07-10-37-first-tuning-run/slice-1000/direction.txt`, `docs/results/logs/2026-11-07-10-37-first-tuning-run/slice-3000/direction.txt`.

The Sequential test is the e-process, which is the E3 gate (`SequentialTest.GATE`). Pairs are in salt order with the classes interleaved in the pooled row. "Bosses" is bosses killed by the candidate and by the current values.

| slice | group | pairs | missing | worse | equal | better | verdict | consumed | bosses candidate | bosses current | class veto |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 1000 | POOLED | 240 | 0 | 129 | 0 | 111 | UNDECIDED | 240 | 106 | 109 | no |
| 1000 | WARRIOR | 40 | 0 | 22 | 0 | 18 | UNDECIDED | 40 | 29 | 32 | no |
| 1000 | MAGE | 40 | 0 | 23 | 0 | 17 | UNDECIDED | 40 | 16 | 19 | no |
| 1000 | ROGUE | 40 | 0 | 23 | 0 | 17 | UNDECIDED | 40 | 10 | 12 | no |
| 1000 | HUNTRESS | 40 | 0 | 24 | 0 | 16 | UNDECIDED | 40 | 20 | 25 | veto |
| 1000 | DUELIST | 40 | 0 | 17 | 0 | 23 | UNDECIDED | 40 | 19 | 13 | no |
| 1000 | CLERIC | 40 | 0 | 20 | 0 | 20 | UNDECIDED | 40 | 12 | 8 | no |
| 3000 | POOLED | 240 | 1 | 112 | 8 | 120 | UNDECIDED | 240 | 98 | 82 | no |
| 3000 | WARRIOR | 40 | 0 | 14 | 1 | 25 | UNDECIDED | 40 | 27 | 21 | no |
| 3000 | MAGE | 40 | 1 | 20 | 1 | 19 | UNDECIDED | 40 | 13 | 14 | no |
| 3000 | ROGUE | 40 | 0 | 20 | 4 | 16 | UNDECIDED | 40 | 6 | 8 | no |
| 3000 | HUNTRESS | 40 | 0 | 18 | 0 | 22 | UNDECIDED | 40 | 24 | 20 | no |
| 3000 | DUELIST | 40 | 0 | 19 | 1 | 20 | UNDECIDED | 40 | 16 | 12 | no |
| 3000 | CLERIC | 40 | 0 | 21 | 1 | 18 | UNDECIDED | 40 | 12 | 7 | no |

Neither slice accepts. The pooled pair scores are 111 better to 129 worse on slice 1000 and 120 better to 112 worse on slice 3000: 231 better to 241 worse over both, which is a tie. Per class, seven of twelve class-slices have more worse pairs than better, four the reverse and one is level.

## The PR table for all six classes

The full tables (every metric, all six classes, pooled, and the killers by depth) are the two `direction.txt` files named above. These rows are the ones the keep rule and the story ask for, current values then candidate, and the think-time column (mean / 95th percentile milliseconds per Decision from the logs' `think_ms`).

| slice | class | reach depth 5 | bosses killed | Goo kills | think ms per Decision, mean / p95 |
|---|---|---|---|---|---|
| 1000 | WARRIOR | 32/40 to 32/40 | 32/40 to 29/40 | 32/40 to 29/40 | 18.4 / 61 to 6.0 / 18 |
| 1000 | MAGE | 22/40 to 21/40 | 19/40 to 16/40 | 19/40 to 16/40 | 12.7 / 43 to 5.1 / 17 |
| 1000 | ROGUE | 18/40 to 14/40 | 12/40 to 10/40 | 12/40 to 10/40 | 7.6 / 27 to 5.7 / 20 |
| 1000 | HUNTRESS | 27/40 to 26/40 | 25/40 to 20/40 | 25/40 to 20/40 | 6.7 / 23 to 6.1 / 22 |
| 1000 | DUELIST | 18/40 to 24/40 | 13/40 to 19/40 | 13/40 to 19/40 | 6.9 / 19 to 6.6 / 19 |
| 1000 | CLERIC | 12/40 to 17/40 | 8/40 to 12/40 | 8/40 to 12/40 | 4.5 / 15 to 3.8 / 13 |
| 1000 | pooled | 129/240 to 134/240 | 109/240 to 106/240 | 109/240 to 106/240 | 10.0 / 36 to 5.6 / 18 |
| 3000 | WARRIOR | 27/40 to 29/40 | 21/40 to 27/40 | 21/40 to 27/40 | 5.6 / 17 to 4.8 / 15 |
| 3000 | MAGE | 19/40 to 18/40 | 14/40 to 13/40 | 14/40 to 13/40 | 4.3 / 14 to 4.3 / 14 |
| 3000 | ROGUE | 17/40 to 11/40 | 8/40 to 6/40 | 8/40 to 6/40 | 4.7 / 16 to 4.3 / 14 |
| 3000 | HUNTRESS | 24/40 to 25/40 | 20/40 to 24/40 | 20/40 to 24/40 | 5.0 / 15 to 4.5 / 15 |
| 3000 | DUELIST | 17/40 to 23/40 | 12/40 to 16/40 | 12/40 to 16/40 | 6.6 / 18 to 6.2 / 18 |
| 3000 | CLERIC | 14/40 to 15/40 | 7/40 to 12/40 | 7/40 to 12/40 | 3.9 / 13 to 4.3 / 15 |
| 3000 | pooled | 118/240 to 121/240 | 82/240 to 98/240 | 82/240 to 98/240 | 5.1 / 16 to 4.8 / 15 |

The think-time column most likely reflects the load the machine was under (cores pinned, other jobs running) and not the numbers: the current-values pass of slice 1000 shows 18.4 ms a Decision for the Warrior and the candidate pass 6.0, and the numbers tuned here do not touch the search budget. It should not be read as an effect of the candidate. Mean milliseconds per Decision recomputed from `runs.tsv` (Decisions and their total time are in it):

| slice | class | current | candidate |
|---|---|---|---|
| 1000 | WARRIOR | 18.4 | 6.0 |
| 1000 | MAGE | 12.7 | 5.1 |
| 1000 | ROGUE | 7.6 | 5.7 |
| 1000 | HUNTRESS | 6.7 | 6.1 |
| 1000 | DUELIST | 6.9 | 6.6 |
| 1000 | CLERIC | 4.5 | 3.8 |
| 1000 | POOLED | 10.0 | 5.6 |
| 3000 | WARRIOR | 5.6 | 4.8 |
| 3000 | MAGE | 4.3 | 4.3 |
| 3000 | ROGUE | 4.7 | 4.3 |
| 3000 | HUNTRESS | 5.0 | 4.5 |
| 3000 | DUELIST | 6.6 | 6.2 |
| 3000 | CLERIC | 3.9 | 4.3 |
| 3000 | POOLED | 5.1 | 4.8 |

## What this does and does not show

- It shows that this vector, from 3936 tuning Runs on a Brain that then changed, does not beat the defaults on 480 confirmation pairs per slice-pair. With H1 at 0.55 and 240 pairs a slice, a real gain of 0.55 would be accepted only about half the time, so "undecided" is weaker than "no gain"; but the difference is also nowhere near what the test looks for, and the slices disagree in sign for two classes.
- It does not show that SPSA cannot work here. The trajectory's standard error is about 0.6 points per iteration against a gain the schedule asks to be about 0.2 steps at the end, and 41 numbers share each difference. A smaller subset (one family at a time: the Goo numbers, the identify numbers) or a much longer run is the next step, and the objective (5 per boss plus depth) cannot see changes that matter only on floors the Brain does not reach, which is where most of the Tengu and Prison numbers live.
- The confirmation Brain (`1e5ddb015`) is later than the tuning Brain (`b51a2cd31`); numbers tuned against one Brain are not guaranteed to carry to the next, and this run cannot say how much of the negative result that is. A rerun at one commit would separate them.

Reproduce: `:rig:tune` with the arguments above into an empty folder, then for each slice play the current values and the candidate (`--tunables` text in `candidate.diff.txt`) with `:rig:direction ... --baseline <folder>`, then `:rig:confirm`.
