# E10, story 407: early food economy

Issue #407 asked for three levers on the food economy of depths 1 to 4: walk back for food that was seen and left, a per-floor turn budget tied to the food on hand, and a correct count of every food item. All three were built, measured on the same 120 Runs, and none beat the baseline, so none changes play. This page keeps the numbers and the reasons. Unranked direction checks on the `standard` set; the baseline is the #406 branch (after-test rest only under 30 percent health).

## Where the food goes

The counts below come from the debug record, which holds ground truth the bot never reads; they are offline observations that guided the design, and every lever acted only on what the screen drew. The feeding-at-will figure is from the unranked, flagged oracle run on the prison food page (`2026-10-29-e10-w2-prison-food.md`).

Read from the `--debug-record` files of 240 baseline Runs (40 of each class, salts 1000..1039, `--jobs 4`).

- 89 of 240 Runs (37 percent) are out of food with the starving icon showing by depth 4; 115 of 240 are starving on their last Decision.
- Food is not mostly left behind. Over the old baseline of departures from depths 1 to 4, the floor's food was taken in 776, never seen in 116 (12 percent) and seen but left in 41. In the current baseline 46 of 240 Runs leave a food they saw, 19 of those 46 with no hunger icon showing and food still in the pack. Only 11 left it while starving with an empty pack.
- Median turns per floor on depths 1 to 4 are 269, 397, 500 and 512 against about 330 turns of food income a floor. The sinks are fights, exploring, searching and resting, not walking past food.
- Mechanics, all at the pinned tag: hunger is 300 for the hungry icon and 450 for starving (`Hunger.java:40-41`), one turn a turn (`Hunger.java:89-95`), eating clamps at zero (`Hunger.java:146-148`); one food a regular floor and none on boss floors (`Level.java:224-226`, `Dungeon.java:441-443`); a ration is 300, a pasty 450, mystery meat 150.

## The three levers

| lever | what it did | result |
|---|---|---|
| 1. walk back | Raise the pull of a remembered food heap on the floor (up to 200 energy-weighted), and when hungry with no food, climb one floor back for a food heap that floor's screen drew | worse |
| 2. floor budget | Descend after a floor allowance shortened by the food held, when the pack is nearly empty, no food heap is on the map and the next floor is not a boss floor | no gain |
| 3. count and eat | Audit of the food count and the eat thresholds | already right, test added |

The target set of lever 1 was a predicate over the remembered heaps (food only for now), so a later story can widen it to every worthwhile item. It is not kept, so the code is not in this change; the experiment diff lives in the story notes only.

## Direction checks (120 Runs, first 20 of each class, salts 1000..1019)

Reach of a depth is the number of Runs that arrived there.

| variant | d4 | d5 | d6 | d10 | Goo kills | starving at death | out of food by d4 |
|---|---|---|---|---|---|---|---|
| baseline (#406) | 86 | 64 | 43 | 0 | 44 | 64 of 120 | 48 |
| fetch food within 200 | 81 | 63 | 39 | 0 | 41 | 57 of 119 | 37 |
| fetch plus walk back up (60 steps) | 81 | 61 | 39 | 0 | 41 | 56 of 119 | 38 |
| descend at 250 turns with food under 1 | 85 | 63 | 40 | 0 | 42 | 63 of 120 | 48 |
| descend at 200 turns plus the food held | 84 | 62 | 39 | 0 | 42 | 65 of 120 | 52 |

Deaths by depth (1 to 9):

| variant | deaths |
|---|---|
| baseline | 4 10 20 22 21 26 13 4 0 |
| fetch food | 4 12 22 18 24 25 10 4 0 |
| fetch plus walk back | 4 12 22 20 22 24 11 4 0 |
| descend 250 | 4 10 21 22 23 25 12 3 0 |
| descend 200 plus food | 4 10 22 22 23 23 13 3 0 |

Fetching more eagerly does cut the Runs out of food by depth 4 (48 to 37) and the starving share at death (64 of 120 to 57 of 119), but it costs depth 4 and depth 6 reach and three Goo kills, concentrated in Cleric and Duelist (d5 7 to 5 each). The walks cost health and turns that the food does not repay. The descend rules do nothing: the floor's food mostly lies in its unexplored part, so leaving sooner gives up the food it was meant to save, and the out-of-food count does not fall (48 and 52).

## Why the rule "not worse on both slices" stopped here

No variant was at least as good as the baseline on the first slice, so the second slice (Warrior, 80 Runs, salts 3000..3079) and the full 240-Run table were not run for them. A slice of 20 per class has a noise of a few Runs on reach; the fetch variants are down on d4, d5, d6 and Goo kills together, which is the same direction four times, and the descend variants are flat to down. Not enough to say they hurt, enough to say they do not help, and a change that does not help is not kept.

## Lever 3, the audit

`LarderTest.every_food_counted_and_eaten_without_waste` pins the claim:

- `Larder.food` counts every food stack of every food in the eat table at its energy times its quantity, and counts nothing that is not food.
- At the hungry icon the hero eats a food only when its energy is at most 300 (the hunger there is 300 to 449, so more than that is partly lost), mystery meat is never eaten there, and a lone pasty or meat pie waits for the starving icon, where the existing least-waste order applies (`EatPolicyTest`). So there is no wasted food to recover, and no behaviour changes.

## What this says about the economy

The ceiling is real (feeding at will lifted reach of depth 5 from 52 to 71 of 120 in the earlier measurement), but it comes from more food, not from a better use of the food found: the audit shows no waste, and walking for the little left behind costs more than it brings. The remaining sources are fewer turns spent per floor on fights, searches and rests, and finding the floor's food sooner, which needs exploration that knows where food is likely to be. That is a Policy question for a later story, and the lever-1 predicate design is worth reusing for it.
