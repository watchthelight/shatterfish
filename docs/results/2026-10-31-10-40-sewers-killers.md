# Story 10.40: the Sewers killers, read one by one (no Brain change)

Issue #424. The 10.7 diagnosis puts 259 of 960 deaths (27.0%) on the Sewers' Crab, Gnoll and Snake, all on depths 1 to 4,
and the Swarm adds 69 more. This page reads those deaths in the `--debug-record` files of a direction check, one at a time,
and says why no family rule is built. Nothing in `brain/` changes. It also answers a question that came up beside it:
how many of these heroes died holding potions they never tried.

## The Run sets

Brain at `eb38f1c4d` (main after #420).

- Slice 1: `./gradlew :rig:direction --args="--set standard --class ALL --first 40 --salt-base 1000 --debug-record --jobs 3"`,
  240 Runs, every one with an ending record.
- Slice 2: `./gradlew :rig:direction --args="--set standard --class WARRIOR --first 80 --salt-base 3000 --debug-record --jobs 3"`,
  results in the last section.

Slice 1, by class:

| | WARRIOR | MAGE | ROGUE | HUNTRESS | DUELIST | CLERIC | pooled |
|---|---|---|---|---|---|---|---|
| Runs | 40 | 40 | 40 | 40 | 40 | 40 | 240 |
| reach depth 5 | 33/40 | 19/40 | 18/40 | 25/40 | 15/40 | 13/40 | 123/240 |
| reach depth 10 | 0/40 | 0/40 | 0/40 | 0/40 | 0/40 | 0/40 | 0/240 |
| Goo kills | 29/40 | 16/40 | 8/40 | 23/40 | 8/40 | 11/40 | 95/240 |
| deaths to Crab | 1 | 5 | 5 | 4 | 7 | 8 | 30 |
| deaths to Gnoll | 2 | 2 | 4 | 0 | 5 | 7 | 20 |
| deaths to Snake | 0 | 0 | 1 | 1 | 2 | 1 | 5 |
| deaths to Swarm | 3 | 5 | 3 | 2 | 3 | 5 | 21 |
| deaths by depth 1..8 | 0/1/2/4/4/12/11/6 | 0/2/10/9/3/8/8/0 | 2/5/3/12/10/4/4/0 | 1/4/2/8/2/18/3/2 | 3/3/7/12/8/3/2/2 | 1/7/7/12/2/4/5/2 | 7/22/31/57/29/49/33/12 |

The four families kill 76 of the 240 Runs (31.7%); depths 1 to 4 hold 117 of the 240 deaths. Six more are
Hermit Crab (3), Great Crab (1) and Gnoll Exile (2), left out of the counts. Where the four die: Crab on depths 3 (14) and
4 (16); Gnoll on 2 (13), 3 (6) and 4 (1); Snake on depth 1 (5); Swarm on 3 (16), 4 (4) and 6 (1).

## What the game does (pinned `v4.0.2`)

- Sewer crab (`Crab.java`): 15 HP, defense 5, accuracy 12, damage 1 to 7, armour roll 0 to 4, `baseSpeed = 2` so it closes
  two cells a turn (`actors/mobs/Crab.java:34-57`). The Brain already has the rule for that speed
  ([`faster-enemy-no-outrunning`](../brain-rules.md#faster-enemy-no-outrunning)); it is why the stairs retreat is the one tried at range.
- Gnoll scout (the game's `Gnoll`, shown as "gnoll scout"): 12 HP, defense 4, accuracy 10, damage 1 to 6, armour roll 0 to 2
  (`actors/mobs/Gnoll.java:34-56`). There is no separate scout class in the pinned tree; the Brute and Shaman are other classes
  and do not spawn on depths 1 to 4.
- Sewer snake (`Snake.java`): 4 HP, defense 25, accuracy 10, damage 1 to 4 (`actors/mobs/Snake.java:38-55`). A hero's swing must beat
  a defense of 25, so a snake with only 4 HP still holds a hero in melee for many swings.
- Swarm of flies (`Swarm.java`): 50 HP, defense 5, accuracy 10, damage 1 to 4, and every hit that leaves it at least 2 HP
  splits off a clone with half the remaining HP (`actors/mobs/Swarm.java:45-123`). Story 10.13 reads the Swarm; its deaths
  are counted here only to size the Sewers.

## What the 76 deaths show

| first contact with the killer's family, slice 1 | Crab 30 | Gnoll 20 | Snake 5 | Swarm 21 | all 76 |
|---|---|---|---|---|---|
| starving, no food in the pack | 9 | 1 | 0 | 7 | 17 |
| not starving, under half health at first contact | 11 | 9 | 0 | 10 | 30 |
| half health or better at first contact | 10 | 10 | 5 | 4 | 29 |
| more than one kind of enemy in view during the fight | 18 | 16 | 5 | 14 | 53 |

The hero is at 50% of its maximum at the first contact on average (1116 of 2375 summed hit points). On its last Decision it
is not hungry in 43, hungry in 12 and starving in 21.

Three things repeat.

1. **A hurt hero walks into the fight.** In 47 of the 71 Crab, Gnoll and Swarm deaths the hero first met the killer
   under half health. In the 60 Decisions before contact, with no enemy in view, it spent 210 Decisions under half health
   (starving ones included, which cannot regenerate): 82 exploring steps, 60 item steps, 23 searches, 17 on the way down,
   and 10 resting (the other 18 ate or used items). The rest levers are already measured and not kept: rest when hurt and fed
   ([`2026-10-26-rest-when-hurt-and-fed.md`](../ideas/2026-10-26-rest-when-hurt-and-fed.md)), and rest by need priced in
   food ([`2026-10-26-rest-by-need.md`](../ideas/2026-10-26-rest-by-need.md): 120 Runs, 72 to 73 on reach, 66 to 64 on
   Goo kills). A rule here would repeat them.
2. **The fight is not alone.** 53 of 76 had a second kind of enemy in view (a rat or a gnoll beside the crab, flies beside
   a gnoll). The last Decisions are `fight: cornered` in 29 of the 30 Crab deaths and in 18 of 20 Gnoll deaths, and
   `fight: retreat: stairs` appears in the last 18 Decisions of 11 Crab, 10 Gnoll and 9 Swarm deaths: the retreat is tried,
   then the hero is back among the enemies. Doors, chokepoints and the retreat upstairs are story 10.14, which found them
   already built ([`2026-10-30-starving-stairs-bounce.md`](../ideas/2026-10-30-starving-stairs-bounce.md)).
3. **The clock.** 21 of 76 are starving on the last Decision and 17 had no food at first contact. A starving hero regenerates
   nothing (`Regeneration.java:56`), the same finding as the Skeleton page. That is story 10.39.

The Snake deaths are a different thing and the smallest. All five are on depth 1, all five met the snake at full health
(20 of 20), and all five fought it beside a rat. The hero's swing has to beat a defense of 25, and the snake
holds it in melee for many swings while a rat bites beside it. A snake does not flee, so there is no step to take.

## Dying with potions untried

The question from the Goo agent's finding (42 of 50 lost Goo fights never drank, with about 5 mostly unknown potions held):
is it the same here? It is.

| the 76 Sewers deaths, slice 1 | count |
|---|---|
| a potion of any kind in the pack at the last Decision | 53 of 76 |
| at least one unknown potion (a colour, not a name) | 45 of 76 (59%) |
| unknown potions held, summed | 130 (2.9 each among the 45) |
| a known potion of healing in the pack | 0 |
| any potion drunk in the last 20 Decisions | 0 |
| unknown potion held and nothing drunk in the last 20 Decisions | 45 |
| holders by family | Crab 23 of 30, Swarm 13 of 21, Gnoll 9 of 20, Snake 0 of 5 |

So the "drink the likeliest-healing unknown potion when dying" lever is not Goo-specific. Three-fifths of the Sewers
deaths had a potion to try, none tried one at the end, and the hero was at about half its hit points when the fatal fight
began. The `unknown-potion-heal-half` rule only drinks an unknown potion at half health or
below when healing is likely enough ([brain rules](../brain-rules.md#unknown-potion-heal-half)); in these 45 deaths it did not
fire. Why it did not (the odds term, a danger threshold, or the cornered branch ranking first) is not read here; the
last-20-Decisions window cannot say. The lever belongs in the fight Policy for every enemy, as the coordinator proposed.

Also in the same files: 107 of the 240 Runs die holding a known potion of strength they never drank. `known_gains` is 0 in
the committed Playbook because story 7.3 measured it as no gain (reach 30 and Goo kills 26 against 30 and 27 on 40 Runs;
reach 44 and Goo kills 37 against 46 and 41 on the 80 slice). That was on an older Brain; it is worth one more look once the fight lever is in. And 13 of
the 76 died with a loose wand in the pack, which nothing zaps (see the idea page below).

## Why no family rule is built

The story asks for a rule only where it fires differently from `main` and wins, with a first test that fails on `main`
(ADR-0019). For each family:

- **Crab.** The speed rule exists and is why the retreat is not tried at range. What is left is a hurt hero meeting a crab,
  which is the rest question above (measured three times, inside the noise), and cornered fights that no step changes.
- **Gnoll.** Plain melee stats (12 HP, 1 to 6); the Gnoll deaths are the multi-enemy fights and the hurt walk again.
  Target order does not separate them: a crab (15 HP, 1 to 7, accuracy 12) and a gnoll (12 HP, 1 to 6, accuracy 10) have
  nearly the same damage per hit point of their own, so kill-the-faster-first moves the order by one swing at most.
- **Snake.** Nothing to do in melee; thrown knives, spikes and the Huntress bow were already used in 3 of the 5 and the
  snake dodges them at the same defense 25.
- **Swarm.** Story 10.13.

None would fire where `main` acts differently and win, so no `SewersKillersTest` exists; there is no failing-first case to
write. The two things that would fire, the potion lever and the wand lever, are below.

## What this page cannot show

- Which blow ended the hero, for the same reason as the Skeleton page: the game log of the final turn is not in the record.
- Whether a lever that lowers the rate of the first contact (resting, taking another way round) would have changed these
  Runs: the same seeds would play differently after the change, which is what a direction check is for. Nothing here is
  measured against a change.
- The identity of the unknown potions the hero held: the record's potion table does not match the colours in the pack for
  every Run, so the count of how many were truly healing is not given. A real lever should measure that on its own run.
- Slice 2 is the Warrior only and is a smaller check on the same readings, not a second table of all six classes.

## Slice 2

80 Warriors, salts 3000 to 3079, Brain at `eb38f1c4d`, 80 of 80 with an ending record.

| | slice 2 (Warrior, 80) | slice 1 Warrior (40) for scale |
|---|---|---|
| reach depth 5 | 55/80 | 33/40 |
| Goo kills | 48/80 | 29/40 |
| deaths by depth 1..8 | 4/4/13/4/7/29/15/4 | 0/1/2/4/4/12/11/6 |
| deaths to Crab | 1 | 1 |
| deaths to Gnoll | 2 | 2 |
| deaths to Snake | 5 | 0 |
| deaths to Swarm | 5 | 3 |
| the four families together | 13 of 80 (16%) | 6 of 40 (15%) |

The Warrior is the class that suffers least in the Sewers, and the share holds on the second slice. The Snake is the difference:
five Snake deaths here (depth 1 three times, depths 2 and 3 once each) against none on slice 1; four of the five met the snake at
half health or better, and four of the five fought it with another kind of enemy in view. It is the same fight as on slice 1 and
no more actionable. The family deaths in this slice are 11 at half health or better at first contact, 1 hurt and 1 starving.

The potion question on slice 2: 9 of the 13 held unknown potions (17 in all), none held a known potion of healing, none drank in
the last 20 Decisions, and the fatal fight began at 71% of maximum on average (249 of 365 hit points summed).

Pooled over both slices (320 Runs, 89 family deaths): 54 of 89 (61%) died holding unknown potions, 147 potions in all, and
none drank in the last 20 Decisions.
