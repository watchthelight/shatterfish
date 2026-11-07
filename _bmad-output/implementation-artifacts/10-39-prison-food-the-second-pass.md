---
title: 'Story 10.39: Prison food, second pass'
type: 'feature'
created: '2026-10-30'
status: 'review'
baseline_commit: '8c783944e'
review_loop_iteration: 0
context: []
---

## Intent

**Problem:** The 10.7 diagnosis found 45.8% of heroes STARVING on their last Decision (72.8% on depths 6 to 9), 102 of 141 Skeleton deaths and
91 of 126 Goo deaths starving or hungry, and no Run reaching depth 9.

**Approach:** Follow where the food goes, measure each lever alone on two slices, keep only a lever that does not fall pooled on reach depth
5 and 6 and Goo kills (a class falling by more than 8 is the exception). Result on `docs/results/2026-10-30-10-39-prison-food-the-second-pass.md`.

## Result

One lever kept: the fallback draws no rest while the hero is hungry with no food held (`larder.fallback_rests`, default 0). Four dropped:
less rest before a descent (A and B), a smaller search budget (D), and more rest before exploring on (E, code removed). Rest is near a local
optimum; the starving share is the price of surviving, not the cause of dying; the Prison deaths are about fighting strength. The next work
is `docs/ideas/2026-10-30-prison-deaths-are-about-fighting-strength.md` (10.17, combat).

## Acceptance criteria and where they are held

1. Where the food goes, with numbers: the page, "Where the food goes".
2. Each lever measured alone on `ALL --first 40 --salt-base 1000` and confirmed on `WARRIOR --first 80 --salt-base 3000`: the page; the
   `direction.md` of every side is, as `.txt`, in `docs/results/logs/2026-10-30-10-39-prison-food/`. D and the dropped levers were not repeated on slice 2,
   they had already failed slice 1.
3. A first test that fails on `main`: `PrisonLarderTest` calls `Policies.fallback(evaluation, vector)`, which `main` does not have, so it does not
   compile there; the old behaviour (a fed hero rests, and so does a hungry one at `larder.fallback_rests=1`) is held in the same class.
4. New numbers in the tunables registry, each with a `@Heuristic` link: `descend.rest_per_mille`, `descend.rest_tight_per_mille`,
   `explore.searches`, `explore.frugal_searches`, `larder.frugal`, `larder.fallback_rests` (`Tunables`, `TunableRegistryTest`).
5. No new Decision reason, so no new `CoachLine` sentence.
