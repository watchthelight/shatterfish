# E10 W2, story 10.15: prison food budgeting

Twenty-six of 30 Skeleton deaths and 23 of 31 Warrior deaths on depths 6 and 7 were starving on the last Decision. This page says why the hero is hungry by then, what was tried, and the one change kept. Unranked direction checks on the `standard` set; the baseline is `origin/main`.

## Why the hero starves

Read from the `--debug-record` files of 240 baseline Runs (40 of each class, salts 1000..1039).

- Food is one item a regular floor and none on the boss floors (`Level.java:224-226`): about 330 turns of food income a floor. A hit point rested costs ten turns of it and a search costs six (`docs/rules/buffs.md`). The hunger icon shows only none, hungry at 300 and starving at 450, and a starving hero does not regenerate.
- 93 of 240 Runs were out of food with the starving icon showing by depth 4. They spent 834 hunger turns on depths 1 and 2 against 689 for the Runs that were not (explore 325 against 308, resting 175 against 85).
- The ceiling is real. Feeding the hero 1000 hunger at will (a debug hook, not kept) lifts reach of depth 5 from 52 to 71 of 120. All of that comes from depths 1 to 4; feeding only from depth 5 changes nothing. So starving on depth 6 is mostly the result of a floor-1-to-4 economy, and a rule on depth 6 cannot fix it.
- Resting after a test was the largest single spend that was not a wound: 97 turns a Run on depths 1 to 4 for 9.4 hit points, most of it at 50 to 99 percent health.

## What was tried (120 Runs, first 20 of each class, salts 1000..1019, pooled reach of depths 5 / 6 / 7)

| variant | d5 | d6 | d7 | deepest | hunger kills |
|---|---|---|---|---|---|
| baseline | 52 | 37 | 15 | 4.36 | 14 |
| no frugal mode | 47 | 29 | 9 | 4.13 | 14 |
| frugal under 900 | 48 | 32 | 16 | 4.21 | 17 |
| shorter floor allowance when the larder is low | 52 | 37 | 12 | 4.33 | 13 |
| rest while hungry | 48 | 33 | 10 | 4.26 | 17 |
| no hungry-leave | no change | | | | |
| after-test rest only under 60 percent health | 57 | 39 | 20 | 4.55 | 13 |
| both rests (after a test, before descent) only under 30 percent health | 59 | 39 | 20 | 4.59 | 8 |

Cutting searches and rests across the board cut starvation but not reach: health matters as much as food. Resting only where a wound is deep was the one variant that moved reach.

## What was kept

`identify.rest_hp_per_mille` in the Playbook: the rest after a test begins only at or under that share of the hero's health. `shatterfish.json` states 300, `oneply.json` and the reference keep 1000 (the old rest, whenever hurt). Only the after-test rest is gated; the before-descent rest needs the Playbook threaded into `Explore` and the 30-percent-both variant above gained only 2 on reach of depth 5 over the 60-percent after-test one, so it is left. The value 300 was picked between the two bracketing variants and measured only in the tables below. No new Policy, so the CoachLine sentence for `test-item/rest` is unchanged.

## Direction checks against `origin/main`

`./gradlew :rig:direction --args="--set standard --class ALL --first 40 --salt-base 1000 --baseline origin/main --jobs 12"`

| 240 Runs | main | change | delta |
|---|---|---|---|
| reach depth 5 | 104 | 118 | +14 |
| reach depth 10 | 0 | 0 | 0 |
| Goo kills | 74 | 76 | +2 |
| deepest mean | 4.342 | 4.558 | +0.22 |
| deaths by depth 1..8 | 9/17/52/58/36/45/23/0 | 8/19/38/57/43/40/29/6 | |

By class, reach of depth 5 and Goo kills of 40: Warrior 35 and 33 against 35 and 27; Mage 18 and 7 against 24 and 11; Rogue 10 and 5 against 14 and 8; Huntress 21 and 16 against 23 and 16; Duelist 13 and 7 against 13 and 8; Cleric 7 and 6 against 9 and 6. Cleric and Mage did not use food differently enough to read apart.

Warrior confirmation, `--class WARRIOR --first 80 --salt-base 3000`: reach of depth 5 55 against 55, Goo kills 48 against 49, deepest mean 5.188 against 5.175. Neutral. On the 40-Warrior slice Goo kills fell by 6 while reach of depth 5 and deepest mean held; the 80-Warrior slice does not repeat it, so it is read as noise, and said here because the instruction was not to be worse on both slices.

## What it does not do

The share of deaths that are starving on the last Decision did not fall (51 percent before, 50 percent after on the 120-Run slice); hunger as the named killer fell from 14 to 8. The remaining starvation is the depth 1 to 4 economy: fetching food seen and left behind, and diving when the larder is empty and the floor's food is taken, are the next levers and are not built here.
