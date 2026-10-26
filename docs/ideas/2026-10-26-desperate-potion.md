# Drink the likeliest unidentified potion when death is near

Issue #219 (the Goo rate). The idea: a desperation rule. When the hero is at the low-health warning, the
enemies are expected to take more than it has left, no retreat is open and no potion of healing is held,
drink the unidentified potion the Beliefs rate most likely to be healing. Built, measured and reverted; this
page records the numbers. The rule never moved a Goo kill.

## What the baseline logs say

The 40 tuning Runs of main (standard WARRIOR, salt 1000, `--debug-record`), last recorded pack of each Run:

- 35 of 40 died holding at least one unidentified potion; 31 of the 36 deaths that ended under a third of
  the hit points did. So the premise (potions in the pack at death) is common, not rare.
- 4 died holding a known potion of healing: two piranha deaths (32 to 26 to 18 to 4 hit points over four
  turns, three piranhas adjacent, the danger estimate not reaching the hero's hit points until the last
  turn), one poisoned at 5 hit points with no enemy in view (Heal only enters with an enemy in view), and one
  bomb at 23 hit points. These are a different lever (the danger estimate for piranhas, and a poison rule);
  they are not what this page tried.
- No unidentified scroll was held at death: the Brain reads scrolls at full health as soon as it can, so
  the scroll step of the plan has nothing to act on. Throwables were held in about a third of the deaths but
  none of them saves a hero at 1 to 6 hit points.

## What was built

In `Heal.choose`, after the known-potion loop found nothing: if the hero is under a third of its hit points
(the status pane's warning), `hp <= Heal.danger`, and `Fight.retreat` is null, drink the offered unidentified
potion with the highest healing odds from `Beliefs.identities` (never the true identity). The odds floor was
0.10, then 0.02. Healing is 3 of 17 in the potion table (`Generator.java:341`), so a single unknown potion is
healing about one time in six before the Beliefs narrow it. The test (`HealPolicyTest`, cornered by a rat at
3 of 20 with a crimson potion) failed on main with `expected: <heal> but was: <fight>` and passed with the
change. `./gradlew :brain:test` was green.

## Rig numbers

`--baseline origin/main --jobs 2`, standard WARRIOR:

| slice | reach depth 5 | Goo kills | deaths by depth 1..8 |
|---|---|---|---|
| tuning 40 (salt 1000), main | 28/40 | 25/40 | 0/1/7/4/3/19/4/2 |
| tuning 40, with the rule (floor 0.10 and 0.02 gave the same table) | 28/40 | 25/40 | 0/1/7/4/3/17/6/2 |
| 80 slice (salt 3000), main | 44/80 | 41/80 | 5/9/10/12/7/19/14/4 |
| 80 slice, with the rule | 44/80 | 41/80 | identical |

Goo kills summed over 120 Runs: 66 before, 66 after. The only visible effect was two Runs of the tuning slice
dying a floor deeper (depth 6 to 7), which did not change reach or the Goo count, and the 80 slice was
identical Run for Run. The rule fires rarely at best.

## Why it does not pay

- The Goo gate is decided by who reaches depth 5 and with what health; the 12 tuning Runs that never reach it
  die on depths 2 to 4 to crabs, piranhas, hunger, a bomb, slime and gas. The ones that matter are killed
  in two or three turns from a health that is not yet under a third, so the low-health condition is met on the
  last turn only, when a 1 in 6 gamble has nothing left to buy.
- Of the three Goo deaths in the baseline, two held unidentified potions, but Goo is met with the
  dodge and the pump rules first; the hero is rarely both under a third and cornered.
- What would raise the odds is earlier: the unknown potions are not tested before death because Story 4.10
  tests only at half health on a calm screen. That is the lever the earlier "drinking an unknown potion" note
  warned about, and it is a test-timing question, not a desperation one.

## If this is reopened

Count first how often the rule fires (a Decision reason `desperate ...` in the strategy log) on a slice;
neither table above can show it, because the Run logs of the measured branch were not kept past the table.
The poison and piranha healing gaps in the second bullet above are worth their own pages, since a potion of
healing already known and held at death is a bigger, surer loss than a gamble on an unknown one.
