# Spend the talent points the hero has earned

Issue #219 (the Goo gate, story 4.14). Reach and Goo kills on `main` `a303dcb79`: tuning slice (standard, WARRIOR,
salts 1000 to 1039) reaches depth 5 in 28 of 40 Runs and kills Goo in 25; the 80 slice (salts 3000 to 3079) 44 and 41.
This page records a turn budget of the baseline Runs, the one change that came out of it, and why it is not kept.
The code is on branch `fix/219-talents-code` (commit `184aaaaa7`), not merged.

## Turn budget, depths 1 to 4, 40 baseline Runs (`build/direction/main40`, debug records)

Turns are the gap between one recorded wait and the next, charged to the Decision at the first. The records are a
subset of all waits, so the kinds are approximate; the per-Run total (about 1,760 turns on depths 1 to 4) matches
the Run logs. Turns per Run:

| kind | d1 | d2 | d3 | d4 | total |
|---|---|---|---|---|---|
| explore frontier | 97 | 122 | 126 | 129 | 474 |
| heap walks (`item:`) | 52 | 65 | 57 | 48 | 222 |
| rest: after-test | 43 | 59 | 24 | 8 | 134 |
| rest: before-descent and descent | 6 | 22 | 53 | 8 | 89 |
| exit walk (`exit: spent`, `exit: hungry`) | 3 | 34 | 58 | 90 | 185 |
| fight (attack, approach, retreat, chase) | 36 | 57 | 95 | 134 | 322 |
| search ahead of a shown trap | 14 | 23 | 16 | 23 | 76 |
| search spots and searches on a spent floor | 24 | 17 | 16 | 32 | 89 |
| random fallback (`act: uniform`) | 14 | 1 | 19 | 2 | 36 |

Findings that matter for the next person:

- No kind is large and safely cuttable. The biggest idle items are the after-test rests (134), searching (165
  between the two search rows) and the fallback (36, three Runs: a 330 turn `Rest` at full health on depth 1, a 208
  turn one, and one Run that never found the depth 3 exit and starved after 1,700 turns on the floor).
- Heap walks by item: throwing stones 43 turns a Run (re-collecting thrown stones), iron, crystal and golden keys 56
  (they open vaults, so worth it), dewdrops 16, gold 14, torn guidebook pages 7. Nothing here is more than 3% of a Run.
- Earlier pages say a starving hero loses 1 HP a turn. It does not: `Hunger.java:78-85` adds `HT / 1000` a turn
  (about 1 HP per 25 turns at 40 HP). Starving costs the regeneration (`Regeneration.java:56`), not a bleed.
- Wandering mobs spawn on a fixed 50 turn timer while the floor holds fewer than 6 to 9 mobs (`Level.java:149`,
  `RegularLevel.java:206-217`), none on depth 1, so time on a floor buys fights and experience in about equal parts.
- Food is a flat one per floor with no catch-up for a slow hero (`Level.java:224-226`); a ration is 300 turns.

## The change tried: spend the talent points

The one waste in the logs that costs no turn: the Brain has no talent Policy. The only talents ever spent were 19 in
40 Runs, by the random fallback. A Warrior earns a first-tier point at each of levels 2 to 6 (five) and a second-tier
one at each of 7 to 12 (`Talent.java:436`, `Hero.java:398-407`); spending one takes no game time
(`Hero.java:377-388`). The new `talents` Policy sat above `heal` and spent each point in this order: Iron Will (the
seal shield grows by a point, `BrokenSeal.java:97`), Provoked Anger (+3 then +5 on the hit after the shield breaks,
`Talent.java:870-873`), Hearty Meal, Veteran's Intuition, then Lethal Momentum, Liquid Willpower, Iron Stomach and the
rest. It read only the talents pane. Expected size: about two more shield points per seal trigger and a few extra
damage, so a few percent of the damage a Run takes, and nothing else.

The test (`TalentsPolicyTest`, five cases) failed with the Policy left out of the list (which is `main`'s behaviour:
`expected: <talents> but was: <explore>` and `<fight>`) and passed with it.

## Numbers

Against origin/main `a303dcb79`, `--baseline origin/main --jobs 2`, standard WARRIOR:

| slice | reach depth 5 | Goo kills | median turns | deaths by depth |
|---|---|---|---|---|
| tuning 40, salt 1000 | 28 to 26 | 25 to 24 | 2130.5 to 2149.5 | 0/1/7/4/3/17/6/2 to 0/2/3/9/2/11/10/3 |
| 80, salt 3000 | 44 to 44 | 41 to 36 | 1974.0 to 1967.5 | 5/9/10/12/7/19/14/4/0 to 5/6/9/16/8/18/14/3/1 |

Over 120 Runs: reach 72 to 70, Goo kills 66 to 60. Not kept; the code is not in this pull request.

## What the tables cannot show

- Both slices lie inside the noise (about 3 on 40 Runs, 4 on 80). The Goo drop on the 80 slice (-5) is not evidence
  that spending points hurts: no mechanism for that is known, and reach was unchanged. It is evidence that the effect,
  if any, is smaller than this measurement can resolve.
- A talent point changes the Run from level 2 on, so every Run diverges early; the comparison is of two samples, not
  of paired Runs.
- The chosen order is a guess from the code. Provoked Anger and Iron Will only act when the seal shield triggers
  (hit under half health), and the shield's cooldown does not tick while starving (`BrokenSeal.java:319-321`), which
  13 of 28 surviving Runs are by the depth 4 exit.

## Where this leaves #219

Eight levers (this one, rest by need, rest when hurt, stairs ahead of a crab, kiting, a search cap, no search ahead,
dew rest) have each moved reach by one or two Runs in 40. The turn budget shows no single cut worth more than 3% of a
Run. The 12 Runs that never reach depth 5 die to five different killers (crab 4, piranha 3, hunger-after-hurt 2, one
each bomb, slime, toxic gas); a lever has to be aimed at one of those, not at the average Run.
