---
title: 'Story 10.44: Starvation before Goo'
type: 'bugfix'
created: '2026-11-08'
status: 'in-review'
baseline_commit: 'edc544ade'
review_loop_iteration: 0
context: []
---

## Intent

**Problem:** story 10.42 found 68 of 256 early deaths starving on the last Decision. Which part of that is waste the hero
can avoid, and is not food policy that story 10.39 already tuned?

**Approach:** diagnose from Run logs that exist (the issue #412 baselines at `edc544ade`, 80 Runs on salt bases 1000 and
3000, all classes), build the largest fix that is a plain bug, and measure it on both ALL-40 slices.

## Diagnosis (80 Runs, `edc544ade`)

Hunger clock: `Hunger.HUNGRY` 300 and `Hunger.STARVING` 450 (`Hunger.java:40-41`); a ration restores 300 (`Food.java:51`,
`energy = Hunger.HUNGRY`) and every regular floor places one food item (`Level.java:224-226`). So the budget is about one
floor per ration, 300 turns. The median floor takes 187 turns, depth 4 takes 295 on average, and 22 of 616 floors take
over 600 turns and carry 14% of all turns.

- 7 of 80 Runs die of Hunger. Replaying the clock from the logs (turns, and each meal's energy), 6 of 38 early deaths end
  starving; the 10.42 count (68 of 256) used the hunger icon on the last Decision.
- Starving early heroes spend 650 turns a floor against 277 for the other early deaths.
- Where the turns go in the starving early deaths: Rest 27% (13% for the others), fallback draws 10.5%.
- **The fallback draws Rest(true) beside an enemy.** 16 draws in 80 Runs, 1233 turns (134, 59, 333, ...). One DUELIST spent
  862 turns on one depth-3 floor in `fallback:uniform` and died of Hunger; a WARRIOR lost 169 on depth 6 and died of Hunger.
  The only rest the screen offers is `Rest(true)` (`ValidActions`), which runs until the hit points are full or something
  interrupts it, and an enemy that is not coming does not.
- Food left behind: 32 of 334 food heaps seen were never taken, 19 of them on a floor the hero left. At least four rations
  or pasties on depths 1, 3 and 4 are never named by a pick-up decision before the hero leaves ("descend: spent"), so the
  heap was out of the walkable set (`Pickup.best` skips a heap with no walk). The logs hold no tiles, so the cause (water,
  chasm, a closed room) is not read here; nothing is built for it.
- Rest before descending is 24% of the turns a hungry hero spends. Story 10.39 owns it (`descend.rest_share`,
  `descend.rest_tight`); not touched.

## Change

`Policies.fallback` draws no `Action.Rest` while an enemy is in view, unless the rest is all the screen offers. `Wait`
stays (one turn). The first test, `FallbackRestTest`, fails on `edc544ade` (the rest is drawn with a crab in view) and
has two controls (calm still rests; a lone rest is still drawn). `WaterBoundTest.known_exit_leaves_a_shore_frontier`
asserted a property of the explore Policy over decisions that were fallback draws; the draw changed, so it now skips
the fallback's decisions.

## Measure

Both ALL-40 slices, `--jobs 1`, baseline the folders played at `edc544ade`:

| | slice 1000 | slice 3000 | pooled |
|---|---|---|---|
| reach depth 5 | 17 to 16 | 25 to 25 | 42 to 41 |
| Goo kills | 13 to 13 | 20 to 21 | 33 to 34 |
| deepest mean | 4.350 to 4.250 | 5.000 to 5.050 | 4.675 to 4.650 |

The fallback fires with an enemy in view in 16 draws of 80 Runs, so a change in play is rare and the table is inside the
noise by design; this is a bug fix, kept because it is not worse and a full rest beside an enemy is never right.

## Not built

- The food left behind (needs the map; a debug-record Run of the seed would show it).
- A food budget or any rest lever (10.39, and the rest levers measured three times).
