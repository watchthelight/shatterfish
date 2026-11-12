# Story 10.45: food is worth the turns it buys

Story 10.45, epic E10, merged as PR #457 on 2026-11-09 (the PR says `Closes #448`, the issue of story 10.44; 10.45 had no issue of its own). The story file is
`_bmad-output/implementation-artifacts/10-45-food-worth-the-walk.md`. This is an unregistered direction check (ADR-0012); `goo`, `holdout` and `tengu` were not played.

**Provenance.** This page was written after the merge, from the PR body and the story file. The Run folders and the `direction.md` of both sides were not
committed, so the figures below are copied from those two records and were not re-derived from logs for this page. Treat them as the story's reported numbers,
not as verified ones.

## The cause

A replay of a starving Warrior (seed 3975733004048, salt 1036) dumped the Observation at the Decision where the hero said "descend: spent". The depth 1 ration lay
in an explored room 25 steps away, reachable over walkable cells. The pick-up Policy weighs an item at `item` 2000 against `turn` -150 per Step
(`weights/shatterfish.json`), so nothing farther than about 12 Steps is fetched, and food had no worth of its own.

## The change

`Pickup.worth` values a heap of food at the turns it buys (`Eat.ENERGY`, a ration 300 turns, `Food.java:51`) times the stack size, at the committed turn weight.
There is no new free parameter, so there is no heuristic register entry. The first test, `PickupThresholdTest.food_worth_the_walk`, fails on `main` (a ration 25
Steps away is not taken) and passes with the change. Two older assertions had used a ration as the "ordinary item" and now use a throwing stone. The
fairness review found no violation: only the heap's visible title is used, and there is no `Observer` change.

## The measurement

`:rig:direction --set standard --class ALL --first 40 --jobs 1`, with the baseline pinned to `29469bd1b` (the merge base), two slices of 240 Runs. The salt-3000
slice ran at `--jobs 4`; the job count does not change a Run.

| | baseline | current | delta |
|---|---|---|---|
| reach depth 5, salt 1000 | 122 | 134 | +12 |
| reach depth 5, salt 3000 | 123 | 129 | +6 |
| reach depth 5, pooled (480 Runs) | 245 | 263 | +18 |
| bosses killed, pooled | 189 | 213 | +24 |

Per class, reach depth 5 pooled: Cleric +6, Duelist +1, Huntress +3, Mage +9, Rogue -1, Warrior 0. The worst class is -1, inside the keep rule's veto of 8.

## Verdict

Kept on the keep rule of this epic (pooled reach depth 5 not worse, no class 8 or more worse). No sequential test was run on this change, and the page does not
claim one.

## What the table cannot show

- Goo kills are not reported in the PR or the story file, so this page gives no Goo column for the change.
- The figures were not recomputed from logs for this page (see Provenance), and each slice is a single measurement of 240 Runs.
- Both slices are drawn from the `standard` set (the first 40 triples per class), the same set the epic's other keep decisions were measured on.
