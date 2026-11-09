---
title: 'Story 10.42: The deaths before Goo'
type: 'feature'
created: '2026-11-08'
status: 'done'
baseline_commit: '396dc470a'
review_loop_iteration: 0
context: []
---

## Intent

**Problem:** the hero kills Goo in most of the Runs that reach depth 5, but only about half of them reach it. 256 of 520
Runs read for this story die on depths 1 to 4. Which of those deaths could the hero have avoided, and what is the
largest one that is not already measured and rejected?

**Approach:** diagnose from Run logs that already exist (no new Runs), publish the page with a test that recomputes its
tables, then build the fix for the largest new cause and measure it on the two ALL-40 slices (salt bases 1000 and 3000)
with the E3 Sequential test, pooled, and a per-class veto of 8, the baseline pinned to the merge-base SHA `396dc470a`.

## Scope fences

- The unknown-potion gamble is story #435 (agent a2c881): not touched. Thieves are agent a5a829's: not touched.
- Rest levers are not rebuilt: three measurements (`docs/ideas/2026-10-26-rest-when-hurt-and-fed.md`,
  `docs/ideas/2026-10-26-rest-by-need.md`, the 10.40 page) were inside the noise, stopped by the food clock.

## Alternatives weighed (before building)

1. Rest to a higher health before exploring on (the largest cause by count, 100 of 256 fatal fights begin under a third of
   health). Rejected: measured three times and not kept; no new lever.
2. A food budget for the Sewers (68 of 256 are starving at the last Decision). Rejected here: story 10.39 owns food.
3. Throw a missile in the waits before a fight the hero cannot walk away from (101 of 256 wait; 28 of those with a
   throwing weapon in the pack and none thrown). Chosen: new, small, local, and the gap is stated by the two Policies' own
   comments.
4. Wear the leather or mail armour the hero carries (50 of 233 early deaths on the first look carried a spare over a worn
   cloth armour). Rejected here: the armour is issue #443's story (E10.41), and a plain leather armour only beats a
   cloth armour that was never upgraded (`Armor.java:379-390`: leather 0 to 4, cloth+0 0 to 2, cloth+1 1 to 3).

## Pre-mortem

- The throw wastes missiles that the pick-up Policy must collect while the fight is on: a missile that lands near a
  crab is not fetched with an enemy in view (`Ranged.FETCH_STEPS` is 0). Accepted: the throws are free turns and what
  lies on the floor is collected after the fight.
- A thrown dart against armour 0 to 4 does nothing (a dart rolls 1 to 2). Accepted: it costs nothing either, since the
  enemy was going to arrive in that turn anyway; the measure is the judge.
- The change makes a hero throw at an enemy that is not coming (a wanderer that has not noticed it), and wakes it.
  Mitigation: only an awake enemy two to six cells off in a clear line, as now, and only where there is no way away.
- A sealed boss floor must keep the old rule: Goo is excluded already and the Tengu fight is the Tengu code's.

## Outcome

KEPT on the epic's keep rule (pooled over both slices not worse, no class 8 or more worse; E3 acceptance of H1 is not
required). The throw rule (commit `ee12dea53`) was measured on the two ALL-40 slices against `396dc470a`: better in 52
pairs, worse in 39, equal in 389 of 480; depth 5 245 to 249 and Goo kills 189 to 195 pooled; the largest worse-minus-better
in any class is 3. E3 left it undecided (pooled REJECT of H1 on both slices, five classes UNDECIDED). The diagnosis (256
early deaths, the Crab a third of them, 101 waiting at `hold: no-way`) and the figures are on
`docs/results/2026-11-08-10-42-the-deaths-before-goo.md`, recomputed by `DeathsBeforeGooPageTest` and
`DeathsBeforeGooMeasureTest`. The two other causes belong to open work: food (story 10.39) and unknown potions (#435).
