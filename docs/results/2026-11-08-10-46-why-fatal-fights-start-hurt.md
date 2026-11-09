# Story 10.46: why fatal fights start hurt (no Brain change)

Issue #453. Story 10.42 (the deaths before Goo) found that 100 of 256 deaths on depths 1 to 4 begin the
fatal fight under a third of health, and that three rest levers were measured and rejected for the food clock. This story asks
what comes before those fights, to find a pattern narrower than "rest more" that a rule could target. It finds that most of
them are the food clock again, and that what is left is too small to measure. `brain/` is unchanged.

## The Runs read

Two sets of Runs with a debug record, both all classes, 40 Runs a class: salts 1000 to 1039 (the folder `m1042-1000`, current side)
and salts 3000 to 3039 (the folder `L1S3`, baseline side). The folders hold no `7ea59b6fa` records: the
rig runs of the thief story were made without `--debug-record`, and a debug record is what holds hit points, hunger and the
pack. Their Brains differ by a few merged stories, which move the counts by a few Runs, not the pattern.

The method is `docs/results/logs/2026-11-08-10-46-hurt-fights.py`: a fight is a run of recorded Decisions with an enemy in
view, at most 3 Decisions apart; the fatal fight is the last, and it has to reach the end of the Run. The record keeps only
the wait screens, so a quiet walk between two fights is thin in it. Whether a rest was interrupted cannot be read from it: a
`rest:` Decision is rare and appears only a few times among the last Decisions before a fight.

## The numbers

| | Runs read |
|---|---|
| deaths on depths 1 to 4 | 247 |
| no fight at the end (hunger, poison, a fall) | 11 |
| fatal fight found | 236 |
| begins under a third of health | 92 (39%) |

Of the 92 hurt starts:

| the hero at the first Decision of the fatal fight | hurt starts |
|---|---|
| hungry or starving, no food in the pack | 55 (60%) |
| of those, starving | 42 |
| hungry or starving, food in the pack | 8 |
| not hungry | 29 (32%) |
| the fight came within 30 Decisions after another fight, which ended under half health | 82 (89%) |
| nearest enemy 1, 2, 3, 4 or 5+ cells off | 22, 19, 18, 10, 23 |

The killers of the 92 are the Crab (30), the Swarm (15), Hunger (14), the Gnoll (8), the Slime (7) and the Snake (6). Of the 29
that were not hungry, 10 are Crabs, 5 Swarms and 5 Gnolls; the hero was exploring the floor in 16 of the 29 and picking up an
item in 7 when the enemy came into view.

## Reading it

- **The food clock.** 55 of 92 hurt starts have no food and 42 are already starving. A starving hero does not regenerate
  (`Regeneration.java:56`), so no rest rule helps them; this is story 10.39's food and the reason the three rest levers
  stopped. It is the largest pattern, and it is not a fight pattern.
- **A fight after a fight.** 82 of 92 follow another fight within 30 Decisions, which ended under half health. The hero
  finishes a fight hurt and meets the next enemy before regaining anything; most are a second mob on the same floor, and about
  two times in five it is another kind (35 hurt starts of the first slice: 20 the same kind, 15 another). This is the same fact as "hurt", not a new lever: what would change it is resting between
  fights, which is the rejected rest rule when fed and the unavailable one when starving.
- **Surprise.** 59 of 92 first see the enemy at 3 cells or fewer, against 81 of 144 of the fights that start healthy: a little
  more often, not a pattern a door rule could cut, and a door is not named in the record.
- **What is left that a rule could target:** the 29 not-hungry hurt starts, of 236 fatal fights (12%), with 20 of them holding
  food. A rule that saved every one of them would save 29 of 480 Runs, about 6 percentage points of reach depth 5 at the very
  most, and no rule saves all. Two slices of one Brain differ by 6 Runs in reach depth 5 (135 and 129), and the mechanism
  (rest between fights while fed) was measured three times before inside that spread, so a rule with this ceiling does not show
  on the table and none is built.

## Result

No rule. Nothing in the record separates a hurt fight the hero could have avoided from one it could not, apart from food.
What stays: the script, the counts above, and one pointer for the next story that reads this: start the hurt-start analysis
from the Runs that are starving with no food, with the depth and turn at which the last ration was eaten, because that is
where 60% of these fights come from.
