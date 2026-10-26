---
title: 'Story 4.14: Pass the Goo gate on the registered set'
type: 'feature'
created: '2026-10-26'
status: 'in-progress'
baseline_commit: '1dfc784b2'
context: []
---

# Story 4.14: Pass the Goo gate on the registered set

The story is split at a clean boundary (issue #115). The first part does not depend on the gate and is done here;
the rest waits.

## Done

- [x] The behavioural differential test on real standard-set seeds with hidden identities permuted
  (`shatterfish/harness/src/test/java/org/shatterfish/harness/agent/PermutedIdentitiesDifferentialTest.java`), the E4
  half of FR-9. Three Warriors of the `standard` set, each played twice under one salt with the real Brain; in the
  second world two unidentified classes per family (potion, scroll, ring) trade their labels and the floor items of
  those classes trade classes, so the screen is unchanged and the game's truth is not. While the two Observations are
  equal, the Action, Decision and Belief must be equal; the walk stops at the first divergence. Controls: the common
  prefix must be at least 25 waits (the permutation must not show at once) and at least one seed must diverge later
  (the permutation must come to show). About 20 seconds.
- [x] The E4 retrospective (`epic-4-retrospective.md`); its lessons are in `CLAUDE.md`, "Lessons from E4".

## Left for later (needs an idle machine, or the gate)

- [ ] The gate Run: the Brain on the `goo` set of 400 Warrior triples with a Registration committed before the Run,
  and the Results page (survival curve, boss staircase, reproducing command). The gate itself (SM-3, 75% kills with
  a lower bound of 70%) is far off: the tuning set kills Goo in 17 of 40 Runs and 44 of 120 (36.7%) on Profile 6, which is not the
  75% that the gate asks for (see the Profile 6 baselines below). Plan: issue #219.
  Never `goo` or `holdout` for tuning.
- [x] The throughput benchmark re-run with the Brain attached, completing the measurement story 1.21 deferred 
  (`docs/results/2026-10-26-throughput-7b1212eea.md`): about 95 Input waits per
  second per process (random agent: 399.5), 3.94 Runs per minute, 10.5 ms a wait. Taken on a shared machine, not an
  idle one; the page says so and asks for a repeat once the machine is quiet.


The story stays in progress in `sprint-status.yaml` until those are done or the owner renegotiates the gate.

## Profile 6 baselines (2026-10-26, `7b1212eea`)

Published as unregistered baseline pages (nothing here is the gate Run): the 
tuning tables (`docs/results/2026-10-26-profile6-tuning-7b1212eea.md`) (24/40 reach and 17/40 Goo kills; 35/80 and
27/80), the 120-Run Goo baseline (`docs/results/2026-10-26-goo-baseline-7b1212eea.md`) (reach 59/120, Goo kills
44/120 = 36.7%, conversion 44/59 = 74.6%) and the throughput page above. They replace the voided Profile 5 numbers
(#240, #258). The story stays in progress: the registered `goo` Run and its Registration are still open.
