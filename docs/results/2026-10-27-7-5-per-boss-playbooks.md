# Per-boss playbooks

Story 7.5. The Playbook's `bosses` section holds the Goo's four numbers (`descent_rest_per_mille`, `drink_per_mille`,
`meet_range`, `wash_range`) and a `descent_rest_per_mille` and a `status` for Tengu and DM-300. This page holds the check
that moving the constants into the Playbook changed no play, the variants of the Goo entry that were played, the `bosses`
set read beside them, and what was kept. These are direction checks (ADR-0012), not a published rate and not the Goo gate
(story 4.14): nothing here is ranked, and `goo` and `holdout` were not run.

The numbers the fields replace are the heuristics `goo-position-and-wash` ([Goo fight position and
wash](2026-10-26-219-goo-fight-position-and-wash.md)) and `rest-before-descend`.

Baseline for every table: `origin/main` at `3d0a4f663` (story 7.4 merged). Current is this branch, `9b02a3aae` plus the
variant's Playbook file. `:rig:direction` plays the baseline's brain under the current api, harness and rig.

## The reference Playbook plays as main

The reference entry (`descent_rest_per_mille` 1000, `drink_per_mille` 400, `meet_range` 6, `wash_range` 4) is the old
constants, so every column of the direction table should be equal.

| | tuning, 40 Runs, salts 1000.. | confirmation, 80 Runs, salts 3000.. |
|---|---|---|
| reach depth 5, main / this branch | 33/40 / 33/40 | 54/80 / 54/80 |
| Goo kills | 32/40 / 32/40 | 47/80 / 47/80 |
| median turns | 2240.5 / 2240.5 | 2171.5 / 2171.5 |
| deepest mean / max | 5.875 / 7 both | 5.200 / 8 both |
| deaths by depth | equal | 5/8/8/5/8/16/25/5 both |

On the 80-Run slice the Run logs are equal one by one (80 of 80, ignoring `think_ms`, the chain and the configuration
hash).

## Variants of the Goo entry

Tuning slice (`--set standard --class WARRIOR --first 40 --salt-base 1000`), each against main. Reach is Runs that reached
depth 5; conversion is Goo kills over reach.

| Goo entry | reach depth 5 | Goo kills | median turns | deaths by depth 1..8 |
|---|---:|---:|---:|---|
| main / reference (1000, 400, 6, 4) | 33/40 | 32/40 | 2240.5 | 0/1/3/3/2/15/16/0 |
| drink 600 | 33/40 | 32/40 | 2243.0 | 0/1/3/3/2/22/8/1 |
| drink 250 | 33/40 | 32/40 | 2306.0 | 0/1/3/3/3/15/14/1 |
| meet 9, wash 6 | 33/40 | 32/40 | 2240.5 | 0/1/3/3/2/15/16/0 |

Every variant reaches the same 33 Runs and wins the same 32 fights, so the first slice cannot tell them apart. The
conversion is already 32 of 33. The difference between rows is where the Runs die after the Goo (depths 6 to 8), which is
the divergence of the Run and not the fight. The two variants that moved anything (drink 600 and 250) were not kept; meet 9
with wash 6 changed no Run at all on this slice.

Confirmation slice (`--first 80 --salt-base 3000`) for the variants that were worth a second look:

| Goo entry | reach depth 5 | Goo kills | median turns | deepest mean | deaths by depth 1..8 |
|---|---:|---:|---:|---:|---|
| main / reference | 54/80 | 47/80 | 2171.5 | 5.200 | 5/8/8/5/8/16/25/5 |
| drink 600 | 54/80 | 44/80 (-3) | 2101.0 | 5.050 | 5/8/8/5/10/20/23/1 |
| meet 9, wash 6 | 54/80 | 47/80 | 2171.5 | 5.200 | 5/8/8/5/7/18/24/5 |

Drink 600 loses three Goo kills. Meet 9 with wash 6 ties on the Goo and changes only the depths after it. Neither wins on
both slices, so neither is kept.

## Kills over fights, before and after

A fight is a Run that reached depth 5; a kill is a Run that killed the Goo. Wilson 95% intervals.

| | before (main) | after (this branch, reference entry) |
|---|---|---|
| tuning, 40 Runs | 32/33 = 97.0% (84.7 to 99.5) | 32/33 = 97.0% (84.7 to 99.5) |
| confirmation, 80 Runs | 47/54 = 87.0% (75.6 to 93.6) | 47/54 = 87.0% (75.6 to 93.6) |
| both slices, 120 Runs | 79/87 = 90.8% (82.9 to 95.3) | 79/87 = 90.8% (82.9 to 95.3) |
| drink 600, both slices | | 76/87 = 87.4% (78.8 to 92.8) |

The intervals are wide and overlap everywhere; the table shows that no Goo entry played here moved the conversion and
that the refactor moved nothing. The Goo gate is a rate over a larger set and is not claimed here.

## The `bosses` set, reported only

`--set bosses --first 100 --salt-base 5000`, no baseline (a set that is reported, not tuned on), this branch with the
reference Playbook.

| | |
|---|---|
| Runs | 100 |
| reach depth 5 | 29/100 (29.0%, 21.0 to 38.5) |
| Goo kills | 17/100; 17/29 over fights = 58.6% (40.7 to 74.5) |
| deepest mean / max | 3.650 / 8 |
| deaths by depth 1..8 | 9/16/30/16/13/9/5/2 |
| Runs that reached depth 10 (Tengu) | 0 of 100 |
| Runs that reached depth 15 (DM-300) | 0 of 100 |

Fewer than 10 of the 100 Runs reach Tengu or DM-300, so both entries are untested and not tuned (`status` `UNTESTED`). The
Goo conversion on this set is lower than on `standard`; it is a different set and the page reads it as a report.

## What was kept

Nothing changed in play. The section and the readers ship, the constants are fields, and the reference values stay. The
format is the deliverable: the Goo's numbers now live in one place that the validator checks, the Equivalence test pins and
`HeuristicLinkTest` links to a Rule or a measured page. No Goo change ships, so no Sequential test was registered.

What the tables cannot show: a fight value that the first 33 fights did not reach. The tuning slice has 33 fights and the
conversion is 32 of 33, so a lever that costs a kill in one fight in 33 cannot be seen on it; the second slice (54 fights)
is the check, and it is itself wide. A better test of the Goo is the gate's larger set.
