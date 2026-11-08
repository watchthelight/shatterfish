---
title: 'Story 10.37: The first tuning run'
type: 'feature'
created: '2026-10-30'
status: 'review'
baseline_commit: '1e5ddb015'
review_loop_iteration: 0
context: []
---

## Intent

**Problem:** `:rig:tune` (story 10.36) has never been run for real. A number it moves is a hypothesis; it is kept only
if it wins on dungeons it was not fitted on.

**Approach:** one SPSA run over every registered number (all 41, including the seven dormant levers of issue #412 that
ship at 0, so this run is their way back) for all six classes on the tuning salts 5000 to 6999; then the candidate
against the current values on the two confirmation slices (salt bases 1000 and 3000) with the E3 Sequential test,
pooled and per class. The verdict and the negative or positive result are published as a results page.

## Budget reasoning (written before the run)

- Measured on the 10.36 demo: 120 Runs in 22 minutes at `--jobs 4`, and the first iteration of this run (8 per class,
  96 Runs) took about 9.5 minutes at `--jobs 3`, so about 6 seconds of wall clock per Run, with other jobs on the laptop
  pushing it to 7 or 8.
- The target is about 8 hours at `--jobs 3`: about 4000 Runs.
- The salt window is the other limit: 5000 to 6999 is 2000 salts, and one iteration uses `classes x --first`, so
  `iterations x 6 x first <= 2000`. 4000 Runs is 2000 pairs, which is exactly that limit.
- Split of the 2000 pairs: noise per iteration against the number of iterations. SPSA's total movement is bounded by the
  schedule (`a_k`, `c_k`, the move cap), and a number moves only when its iteration's difference is real, so more
  iterations help more than more pairs in one. Eight Runs per class (48 pairs, a paired standard error of about 0.55
  score points) and 41 iterations use 1968 salts (5000 to 6967) and about 3940 Runs. Alternatives weighed: 20
  iterations of 16 per class (half the iterations, steadier steps), 16 of 20 (the first sketch, 1920 pairs, which
  would have finished in 3 hours and so under-used the budget), 100 of 3 (too noisy per step to read). 41 x 8 was
  chosen. A first attempt at 20 x 8 was stopped after one iteration because the measured speed showed the budget was
  twice that, and its folder was deleted; the salts it used are replayed by the real run and nothing was kept from it.
- `--params all`: the registry allows the #412 levers, so they are in. The README of the method warns that more numbers
  add more directions for noise to push; the second slice is the judge.
- Resumable: the folder is the state (`tune.json`, `trajectory.jsonl`); a killed run continues with `--resume`.
- Command: `:rig:tune --set standard --class ALL --first 8 --salt-base 5000 --iterations 41 --jobs 3 --params all`.

## Confirmation, pre-registered here before the candidate exists

- Slices: `standard`, all six classes, the first 20 of each, salt bases 1000 and 3000 (entry `i` of a class under
  `base + i`). The candidate and the current values play the same triples under the same salts. The current values are
  played into a folder (no git-ref baseline), then the candidate is played with `--tunables` against that folder.
- Test: the E3 Sequential test as it stands (`EProcess`), H0 0.50, H1 0.55, alpha = beta = 0.05, up to 25% of pairs
  missing, undecided after the pairs it has. Run pooled (pairs in salt order with the classes interleaved) and per class
  by `TuneConfirm`. The hypotheses are constants of the class, not flags.
- Keep rule: the pooled test accepts on both slices, and on each slice no class kills fewer bosses than the current
  values. Anything else is a negative result. 120 pairs per slice may well be too few for an accept at H1 0.55; that is
  stated on the page and is not a reason to change H1 after the fact.
- `holdout`, `goo`, `tengu` are not played; 7000 and up are not touched.

## Acceptance criteria and where they are held

1. A run on the tuning salts for all six classes: the trajectory in `docs/results/logs/2026-10-30-10-37-first-tuning-run/`.
2. The candidate against the current values on the two slices, pooled and per class: `TuneConfirm` (`:rig:confirm`),
   the page.
3. The page with the trajectory, both slices, the PR table for all six classes with the think-time column, and the
   verdict; `TuneRunPageTest` reads the logs it names and checks the figures.
4. A rejected candidate is a negative result and the story is done; a kept one changes only Playbook numbers.
5. No goo, holdout or tengu seeds.

## Amendments made while the story ran

- The coordinator changed the confirmation twice, and both are what was run: the candidate is judged on `main` at
  `1e5ddb015` against the default values at that same commit (the tuning run played on `b51a2cd31`), on `ALL` x 40
  at salts 1000 and 3000 (not 20 per class), and a per-class veto was added (8 or more worse pairs than better). The
  bosses-killed rule above stays. `TuneConfirm` holds all of it.
- A first confirmation pass at `bafa72185` was discarded when `main` moved; nothing from it is on the page.
- The tune run's `tune.json` was lost to a scratch cleanup; the page and `TuneRunPageTest` state the configuration.

## Outcome

Rejected, published as a negative result: the page `docs/results/2026-11-07-10-37-the-first-tuning-run.md`. The
pooled test is undecided on both slices (111 better to 129 worse on 1000, 120 to 112 on 3000), four classes kill fewer
bosses on slice 1000 and the Huntress is vetoed. No Playbook number changes, so no `fairness` label, and no #412 lever
is turned on (nothing to report on #412 beyond the page: the dormant levers moved a little in the candidate and the
candidate did not win).

## Verification

`TuneRunPageTest` (trajectory, candidate table, both slices recomputed from `runs.tsv`, verdict, named logs),
`TuneConfirmTest` (a clear win is kept, identical sides are not, a class veto, a missing pair), `TuneRunnerTest`,
`TuneRefusalTest`, `RigOracleGateTest`, `IdeasIndexTest`.
