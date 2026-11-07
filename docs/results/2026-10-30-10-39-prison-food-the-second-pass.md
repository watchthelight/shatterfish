# Story 10.39: Prison food, second pass

Issue #423. The 10.7 diagnosis found 440 of 960 heroes STARVING on their last Decision (45.8%), 214 of 294 on depths 6 to 9, and 91 of 126
Goo deaths hungry or starving. This page follows where the food goes, measures five levers aimed at it, and keeps one. The main result is
a negative one: **rest is near a local optimum on this Brain, and the starving share is the price of surviving, not the cause of dying.
The Prison deaths are about fighting strength, not food.** An early guess in this story, that more rest before a descent would add reach,
was wrong (lever E).

Every number below comes from the Rig's `:rig:direction` on the `standard` set, `--jobs 3`, with the debug record on. Slice 1 is
`--class ALL --first 40 --salt-base 1000` (240 Runs), slice 2 is `--class WARRIOR --first 80 --salt-base 3000`. Nothing from `goo`, `holdout`
or `tengu`. The `direction.md` of each side is committed (as `<name>-direction.txt`) in `docs/results/logs/2026-10-30-10-39-prison-food/`; the full logs are 1.8 MB a Run
and are not.

## How a lever is kept

Pooled across classes, on both slices: reach depth 5 and 6 and Goo kills must not fall. A single class moves by 5 either way on noise at 40
Runs, so a per-class veto would reject every lever; one class falling by more than 8 is the exception. Per-class rows are reported. The
starving share is reported too, but it does not decide: A and B show why.

Levers A, B, C and E were measured against `base40`, the head of `main` before #427 (`8c783944e`, 240 Runs). Main then moved (#427 removed the
depth 1 search sweep), so C was measured again, and D was measured for the first time, on the merged tree: C against `main` at `b51a2cd31`,
D against that merge with C in.

## Where the food goes

Income is one ration-class food per floor, about 330 turns of energy (a floor deals four rations and a pasty over five floors; the boss
floors pause the clock). Turns per floor in `base40`, with the share by what the Policy was doing:

| Depth | Runs | mean turns | explore | rest | pick-up | fight | search | descend |
|---|---|---|---|---|---|---|---|---|
| 1 | 240 | 259 | 35% | 9% | 17% | 13% | 12% | 6% |
| 2 | 232 | 380 | 27% | 22% | 16% | 15% | 9% | 6% |
| 3 | 213 | 411 | 27% | 22% | 14% | 20% | 6% | 5% |
| 4 | 173 | 532 | 26% | 23% | 12% | 22% | 7% | 4% |
| 5 | 126 | 237 | 33% | 31% | 3% | 26% | 2% | 4% |
| 6 | 88 | 377 | 30% | 9% | 12% | 38% | 5% | 2% |

From depth 2 on a floor costs more turns than its food buys (380, 411 and 532 against about 330), and the gap is paid out of the pack: the mean
food in the pack at arrival is 367 turns on depth 2, 243 on depth 4, 107 on depth 6. By arrival on depth 5, 77 of 126 heroes are hungry or starving, 36 of them
starving; on depth 6, 70 of 88 and 43. Food left behind on the floor is small: 29, 16, 13 and 25 heaps of about 240 seen on depths 1 to 4, about
10%. So the loss is not food missed. It is turns spent, and of those the discretionary ones (rest 9 to 31%, search 2 to 12%, pick-up 3 to 17%)
add up to about 28% of all turns.

One failure is plain: 3.75% of Runs stall on a floor whose exit is never seen (after the 36 search spots), and the fallback then draws a
uniformly random Action, including a `Rest(full)` of up to 800 turns while hungry or starving. A starving hero regenerates nothing
(Regeneration.java:56) and loses hit points, so that rest is pure waste. That is lever C.

## The levers

Reach and Goo are pooled over slice 1 unless the row says otherwise. The starving share is the share of Runs STARVING on their last wait.

| Lever | Runs | reach d5 | reach d6 | reach d8 | Goo kills | starving on the last Decision | turns per floor |
|---|---|---|---|---|---|---|---|
| A | 240 | 126 to 125 (-1) | 88 to 81 (-7) | 5 to 10 (+5) | 90 to 83 (-7) | 52.1% to 40.4% | 355 to 335 |
| B | 240 | 126 to 122 (-4) | 88 to 77 (-11) | 5 to 8 (+3) | 90 to 78 (-12) | 52.1% to 37.1% | 355 to 326 |
| C | 240 | 126 to 130 (+4) | 88 to 85 (-3) | 5 to 5 (0) | 90 to 89 (-1) | 52.1% to 50.8% | 355 to 349 |
| E | 240 | 126 to 114 (-12) | 88 to 81 (-7) | 5 to 3 (-2) | 90 to 82 (-8) | 52.1% to 49.6% | 355 to 351 |
| C on main | 240 | 123 to 129 (+6) | 88 to 87 (-1) | 9 to 9 (0) | 93 to 93 (0) | 47.9% to 48.3% | 349 to 344 |
| D on main + C | 240 | 129 to 132 (+3) | 87 to 86 (-1) | 9 to 3 (-6) | 93 to 88 (-5) | 48.3% to 40.8% | 344 to 339 |
| C, Warrior 80 (slice 2) | 80 | 54 to 55 (+1) | 48 to 49 (+1) | 3 to 4 (+1) | 48 to 49 (+1) | 56.2% to 55.0% | 362 to 359 |

Per class, reach depth 5 and Goo kills (baseline to lever):

| Lever | measure | WARRIOR | MAGE | ROGUE | HUNTRESS | DUELIST | CLERIC |
|---|---|---|---|---|---|---|---|
| A | reach d5 | 31 to 30 | 20 to 20 | 15 to 20 | 25 to 26 | 19 to 14 | 16 to 15 |
| A | Goo kills | 27 to 27 | 11 to 11 | 10 to 10 | 18 to 16 | 12 to 9 | 12 to 10 |
| B | reach d5 | 31 to 29 | 20 to 20 | 15 to 18 | 25 to 27 | 19 to 17 | 16 to 11 |
| B | Goo kills | 27 to 24 | 11 to 12 | 10 to 11 | 18 to 12 | 12 to 11 | 12 to 8 |
| C | reach d5 | 31 to 31 | 20 to 21 | 15 to 16 | 25 to 25 | 19 to 20 | 16 to 17 |
| C | Goo kills | 27 to 27 | 11 to 11 | 10 to 10 | 18 to 17 | 12 to 12 | 12 to 12 |
| E | reach d5 | 31 to 32 | 20 to 17 | 15 to 13 | 25 to 24 | 19 to 17 | 16 to 11 |
| E | Goo kills | 27 to 29 | 11 to 9 | 10 to 8 | 18 to 16 | 12 to 12 | 12 to 8 |
| C on main | reach d5 | 33 to 33 | 21 to 23 | 15 to 16 | 26 to 28 | 13 to 14 | 15 to 15 |
| C on main | Goo kills | 27 to 25 | 15 to 15 | 9 to 9 | 23 to 24 | 8 to 9 | 11 to 11 |
| D on main + C | reach d5 | 33 to 33 | 23 to 21 | 16 to 17 | 28 to 28 | 14 to 16 | 15 to 17 |
| D on main + C | Goo kills | 25 to 32 | 15 to 15 | 9 to 5 | 24 to 22 | 9 to 3 | 11 to 11 |

- **A, rest less before a descent when the larder is tight** (`descend.rest_tight_per_mille=500`). Dropped. The starving share falls by 12
  points; pooled reach d6 falls by 7 and Goo kills by 7, and the Duelist loses 5 on reach d5. It cut starving without adding reach.
- **B, rest less before every descent** (`descend.rest_per_mille=600`). Dropped. The share falls by 15 points; reach d6 falls by 11 and Goo kills
  by 12 (Huntress 18 to 12).
- **C, no rest in the fallback while hungry with no food** (`larder.fallback_rests=0`, now the default). **Kept.** Slice 1 on the older main:
  reach d5 +4, d6 -3, Goo -1. Slice 1 on the merged main: d5 +6, d6 -1, Goo 0. Slice 2 (Warrior 80): d5 +1, d6 +1, Goo +1. No class moves by
  more than 2 on reach d5. It is small, because the stuck-floor case is 3.75% of Runs, and it is the one lever that removes pure waste rather
  than trading rest for food.
- **D, a smaller search budget** (`explore.searches=6`, `explore.frugal_searches=2`), measured on the merged main with C in. Dropped. Reach
  d5 +3 and d6 -1 but reach d8 falls 9 to 3 and Goo kills fall 93 to 88 (Duelist 9 to 3, Rogue 9 to 5 against the Warrior's 25 to 32). The share
  falls 7.5 points. Not run on slice 2.
- **E, rest more before exploring on** (`explore.rest_below_per_mille=500` while fed and holding a ration; code removed after the measurement).
  Dropped. Reach d5 falls 12, d6 7, Goo kills 8; the Cleric loses 5 on reach d5. The starving share barely moves (52.1% to 49.6%).

## What the pattern says

A and B rest less and cost reach and Goo kills while the starving share falls by 12 and 15 points. E rests more and costs reach and Goo
kills while the share stays put. D searches less and costs the deep reach. In both directions the change loses. The simple reading is that the
current rest and search settings sit near a local optimum, and that the starving share is a symptom of heroes that survive long enough to
run low on food, not the thing that kills them: the levers that cut it made the hero die sooner. It is a reading of five tables of 240 Runs
(noise of about 5 on a pooled count), not a proof. The Prison deaths are about fighting strength: the level, strength and gear a hero arrives
with against the killers of the depth. The next work is in `docs/ideas/2026-10-30-prison-deaths-are-about-fighting-strength.md`
(story 10.17, the power curve by depth, and combat).

## What the tables cannot show

Forty Runs a class is one measurement: a lever that changes who arrives where changes every later fight, so single-class rows move by 5 either way
on noise. C's effect is inside that noise on every row and is kept because the Policy it changes was spending hundreds of turns on food that was not
there, and because it does not fall on either slice. A, B, D and E were not repeated on a second seed band; one of them could pass there.
