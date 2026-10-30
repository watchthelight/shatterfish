# Story 10.8: the Skeleton deaths, read one by one (no Brain change)

Issue #354. The W1 killer tables (PR #388) name the Skeleton as the top killer on depths 6 and 7. This page reads the
30 Runs the game itself credits to a Skeleton, in the `--debug-record` files of a direction check, and says why a bone
burst rule is not built. Nothing in `brain/` changes.

## The Run set

`./gradlew :rig:direction --args="--set standard --class ALL --first 40 --salt-base 1000 --debug-record --jobs 12"`,
unranked, Brain at `efbc02222` (the head of `e10/instruments`). 240 Runs, 239 with an ending record.

| killer | WARRIOR | MAGE | ROGUE | HUNTRESS | DUELIST | CLERIC | pooled |
|---|---|---|---|---|---|---|---|
| Skeleton deaths | 15 | 1 | 2 | 9 | 1 | 2 | 30 |
| reach depth 5 | 32/40 | 18/40 | 10/40 | 21/40 | 13/40 | 7/40 | 101/240 |
| reach depth 10 | 0/40 | 0/40 | 0/40 | 0/40 | 0/40 | 0/40 | 0/240 |

## What the game does (upstream-reader, pinned `v4.0.2`)

- Skeleton: 25 HP, defense 9, accuracy 12, damage `NormalIntRange(2, 10)`, armour roll `NormalIntRange(0, 5)`
  (`Skeleton.java:52-68, 156-164`).
- On any death except a chasm fall (`Skeleton.java:73-75`: the cause is only compared with `Chasm.class`, so a
  kill from range does not stop it) each living character on the 8 neighbouring cells takes
  `NormalIntRange(6, 12)` less two armour rolls (`Skeleton.java:78-126`). The hit has no accuracy roll and no
  `defenseProc`. A hero it kills gets "You were killed by the explosion of bones..." and the recorded cause
  is the Skeleton (`Skeleton.java:127-140`, `Rankings.java:110`).
- So a kill from two or more cells away avoids the burst, and a kill adjacent costs 6 to 12 less the armour, for
  certain.

## What the 30 deaths show

| | Runs of 30 |
|---|---|
| hero STARVING on its last Decision | 26 |
| hungry, not starving | 1 |
| not hungry | 3 |
| hero HP on the last Decision 1 to 7 | 30 |
| last Decision `Attack` on the Skeleton | 30 |
| last Decision's why starts "cornered" | 30 |
| Skeleton at 3 pips or fewer (a blow could kill it, so the burst is possible) | 14 |
| Skeleton at 4 pips or more (it was not about to die, so the hero died to its blows) | 16 |
| a known healing potion in the pack | 0 |

The hero has been starving for 14 to 369 Decisions when it dies (median 93.5). A starving hero regenerates
nothing (`Regeneration.java:56`, as the [no-stairs flight page](../ideas/2026-10-27-no-stairs-flight-while-starving.md)
records), so it enters every Skeleton fight at whatever HP it had and loses the fight at the first bad streak.
In 16 of the 30 the Skeleton was still above 3 pips when the hero died, so the burst did not decide those at all;
in the other 14 it could have, and the log cannot say which blow ended the hero (the game names the Skeleton for both).

Over the whole set, 33 Runs landed a possibly-killing blow (hero HP 12 or less, Skeleton at 3 pips or fewer, adjacent);
20 of them died to the Skeleton and the other 13 died to something else a little later (hunger 4, Thief 3, DM-100 3,
Guard, Bleeding, Poison). A hero in that state is at the end of its health, not one that a different final blow would
have saved.

## Why the rule is not built

The rule from the story: do not land the killing blow adjacent at HP below the burst's maximum; prefer a ranged finish,
a step back, or a heal first. Against this code:

- **A heal first** is `Heal`'s job and runs ahead of `Fight`. In the 30 deaths the pack held a known healing potion
  in none of them (the potion lists on the last Decisions show no healing).
- **A step back** is already what `Fight.ranked` does below a third of the hero's HP: the fight is not favourable
  (`Fight.favourable`, the `hp * 3 < ht` clause), so `retreat` ranks above `attack`. A third of the Prison hero's
  45 to 60 HP is 15 to 20, above the burst's maximum of 12, so the rule would switch on later than the existing one
  and never earlier. It differs from `main` only where `retreat` returns nothing, and there the hero is cornered:
  all 30 final Decisions say so. A hero with no cell to step to has no alternative to the blow.
- **A ranged finish** needs distance 2, and a Skeleton walks at the hero's pace (no speed override,
  `Char.java:175`): once it is adjacent a step back gives distance 2 for one turn and the Skeleton closes again before
  the hero can act. A ranged kill is available only while it approaches, and `Shoot`, `Zap` and `Ranged` already take
  that window when the kit has one (the Huntress, 9 of the 30, is the class with a bow).
- **Not landing the blow** while cornered at HP 1 to 7 and starving costs HP every turn from the Skeleton (2 to 10 less
  armour, accuracy 12) and from hunger, with nothing regenerating. The burst is a certain 6 to 12 less two armour rolls;
  the Skeleton's blows are likely and repeated. Neither saves a hero at 4 HP.

A rule with these inputs would never fire where `main` acts differently and win, so it was not built: there is no
case for `SkeletonBurstTest` that fails on `main` (ADR-0019 asks for a first test that does).

## What the evidence points at

1. **Hunger on depth 6.** 26 of the 30 Skeleton deaths are starving heroes. This is story 10.15 (Prison food budgeting)
   and it is the lever with a measured reason behind it; the Skeleton is where an already doomed hero finishes.
   Hunger itself is credited to the Skeleton in the killer table only when the Skeleton lands the last point, so the
   table understates hunger's share on depths 6 and 7 (2 hunger deaths on the Warrior table for depths 6 and 7, yet
   the hero was starving on its last Decision in 23 of the 31 Warrior deaths there).
2. **Cornered fights** (30 of 30). The hero reaches the fight with no cell to step to. Doors and chokepoints and the
   retreat upstairs are story 10.14.
3. Kept for later, if hunger is solved and Skeleton deaths remain: the burst rule above, with a first test on a
   screen where `retreat` returns a cell and the Skeleton is at one pip while the hero is in the 13 to 15 HP band
   above the one-third line. This set was not searched for such a case.

## What this page cannot show

- Which blow killed the hero in the 14 deaths where the burst was possible. The game log of the final turn is not in the
  debug record (the hero is dead before it is read); a harness replay that reads the last log line would settle it,
  and nothing here depends on it.
- Whether the same holds with hunger fixed. After 10.15 lands the table needs reading again.
- The killer table credits one cause per death, so "Skeleton 30" is a count of last blows, not of fights lost to
  Skeletons; the 33 Runs with a possibly-killing blow include fights the hero won only to die to a later enemy.
