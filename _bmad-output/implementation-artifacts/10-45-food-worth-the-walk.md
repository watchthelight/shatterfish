# Story 10.45: food is worth the turns it buys

Status: review. Issue #448 (Closes). Follows story 10.44 (PR #450, the fallback rest).

## Diagnosis

Hungry Runs died on depths 1-4 with a ration seen on an earlier floor and never taken. A replay of a starving Warrior
(seed 3975733004048, salt 1036) dumped the Observation at the Decision where the hero said "descend: spent": the
depth-1 ration (cell 1685) lay in an explored room 25 steps from the hero, reachable over walkable cells. The pick-up
Policy skipped it because an item is weighed as `item` (2000) against `turn` (-150) per Step
(weights/shatterfish.json), so anything past about 12 Steps is not worth fetching, and food had no worth of its own.

## Change

`Pickup.worth` values a food heap at the turns it buys (`Eat.ENERGY`, a ration 300 turns, Food.java:51) times the
stack size, at the committed turn weight. No new free parameter, so no heuristic register entry.

First test: `PickupThresholdTest.food_worth_the_walk` fails on main (a ration 25 Steps away is not taken) and passes.
Two older assertions used a ration as the "ordinary item" and now use a throwing stone.

## Measure

`:rig:direction --set standard --class ALL --first 40 --jobs 1`, baseline pinned to 29469bd1b (the merge base),
both slices pooled (240 Runs each):

| | baseline | current | delta |
|---|---|---|---|
| reach depth 5, salt 1000 | 122 | 134 | +12 |
| reach depth 5, salt 3000 | 123 | 129 | +6 |
| reach depth 5, pooled | 245 | 263 | +18 |
| bosses killed, pooled | 189 | 213 | +24 |

Per class, reach depth 5 pooled: Cleric +6, Duelist +1, Huntress +3, Mage +9, Rogue -1, Warrior 0. The worst class
is -1, inside the -8 veto. The salt-3000 slice ran at `--jobs 4` (the results do not depend on the job count).
