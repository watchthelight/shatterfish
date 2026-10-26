# Walk to the stairs ahead of a crab when the hero gets there first

Issue #219 (the Goo rate), crab deaths on depths 2 to 4. On main fa5e15cd4 (standard WARRIOR, tuning slice
salt 1000, 40 Runs) 12 Runs die before depth 5, and crabs kill four of them (a fifth is a starving hero a crab
finished). The strategy logs show one shape in all four: the hero is under half health after an earlier fight
(a gnoll scout or a snake costs 5 to 10 points), explores on, and a crab turns up at 5 to 8 cells. The fight
Policy finds the fight unfavourable, has no retreat that pays, and logs `hold: no-way` for the two or three
waits the crab needs, then `cornered: sewer crab`. The crab steps adjacent and attacks in the same hero turn
(it moves at 0.5 and attacks at 1, `Mob.java:1365-1393`, `Crab.java:34-36`), so the hero starts the melee a
blow down, and a crab costs about 15 points (15 health, damage 1 to 7, `Crab.java:34-58`) against hero health of
2 to 18.

## What was ruled out from the logs

- Throwing stones during the waits: the pack held a throwing stone in one of the four Runs. Ammunition is spent
  on earlier gnolls and not picked up again.
- A doorway or corridor: a crab came alone in two of the four deaths, and a chokepoint gains nothing against one enemy.

## What was tried

`Fight.retreat` allowed a walk to the stairs with an awake faster enemy within six cells only when the stairs
were at most two steps off (`OUTPACED_STAIRS`, `docs/brain-rules.md#faster-enemy-no-outrunning`). The change
also allowed a longer walk when the hero would arrive with two cells to spare: steps at most the crab's
distance less two (the crab gains one cell a step on a hero walking straight away), and the stairs farther
from the crab than the hero is. No hostile follows across the stairs (`Mob.java:1719-1743`), so the stairs end
the contact. A unit test for it failed on main and passed with the change.

## Numbers

Tuning slice (`--set standard --class WARRIOR --first 40 --salt-base 1000`) against origin/main fa5e15cd4:
reach depth 5 28/40 against 28/40, Goo kills 25/40 against 25/40, median turns 2130.5 against 2130.5, deaths by
depth identical. Every column matched: the rule never changed a Decision in 40 Runs. So the situation it covers
(crab inside six cells, the stairs inside the crab's distance less two, the hero not already cornered) is rare
or does not occur on this slice. The logs do not say where the stairs lay in the four deaths (the crab was
seen at 5, 6 and 8 cells, once with gnolls in the way). Not kept; the 80 slice was not run
because a zero-change table cannot move.

## What the table cannot show

The deaths are decided earlier: the hero meets the crab at under half health. Resting after fights was tried
(branch fix/219-reach, not merged; a gain of 2 in reach and Goo on the tuning slice, flat on the second)
and food runs short. Untried: reading unknown scrolls as an emergency measure when cornered by a crab at low
health (teleportation among the scroll odds), which is allowed by information parity but is a gamble, and a
cheaper first blow by waiting out of the crab's line of sight so it loses the hero (`Mob.java:1381-1387`).
