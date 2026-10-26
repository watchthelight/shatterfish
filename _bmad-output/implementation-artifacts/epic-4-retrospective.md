---
title: 'Epic 4 retrospective: the baseline Brain'
type: 'retrospective'
created: '2026-10-26'
status: 'done'
---

# Epic 4 retrospective: the baseline Brain

Written at the first half of story 4.14, from the story files 4.1 to 4.13, the merged pull requests #130 to #189 and
the issues they closed. Story 4.14 itself is not finished: the Goo gate (SM-3, 75% on the `goo` set) is far off and
stays open, and so do the gate Run with a committed Registration, its Results page and the throughput benchmark with
the Brain attached (see the story file). This retrospective covers what the epic delivered and learned so far; 4.14's
last lessons are added here when it closes.

## What the epic delivered

A symbolic Brain that depends on `api` alone: the skeleton with arbitration and re-planning (4.1), Beliefs about
unknown items and the floor (4.2), `safeTest` (4.3), the Decision output and the strategy log (4.4), the Evaluation
with weights as data (4.5), and the Policies for exploring (4.6), fighting in corridors (4.7), picking up and
equipping (4.8), eating and healing (4.9), testing unknown items (4.10), answering prompts (4.11) and descending
(4.12). Story 4.13 added the death gallery's per-Brain comparison view and measured tuning steps. After it the tuning
set kills Goo in 7 of 40 Warrior Runs against SM-3's 75%, and 30 of 40 die before depth 5. 4.14's testable half adds
the behavioural differential test on real standard-set Runs (`PermutedIdentitiesDifferentialTest`), the E4 half of
FR-9.

## What went well

- **The boundary held.** The Brain never saw game code, and the fairness reviewer passed every Policy story; what it
  found were test gaps and wording (a non-enemy actor and a chest's contents in 4.2, three should-fixes in 4.3, a
  mis-cited line in 4.10), never a leak. Information parity was cheap because it was architecture, not discipline.
- **Weights as data and the strategy log.** Because every Decision carries its reason and its weights come from a
  committed file, each tuning step in 4.13 could be kept or reverted on a recorded number, and a reader can see why a
  step lost (F1, S2, S7, P1 and the three Goo tactics were reverted with their numbers kept).
- **Regression tests with a real failure behind them.** `StarvationRegressionTest` failed on a real smoke Warrior
  before `Eat` was fixed (4.9), and the oscillation fix (#178) cut wasted waits from 25% to about 1% once the
  strategy log made the pattern visible.
- **The death gallery.** Asking "why do 30 of 40 die before depth 5" had no answer until the comparison view and the
  last-screens probe existed; the assessment in 4.13 (food, then strength and gear, then fights chosen badly) came
  from them.

## What went badly, and what it cost

1. **A gate we cannot reach by tuning.** SM-3 was written before any Brain existed. After 4.13 the honest estimate is
   several stories away, and E6's tactical search is likely a prerequisite. Story 4.14 therefore cannot close in E4 as
   written, and the epic stays in progress with it.
2. **Local wins that were not wins.** Goo-specific tactics (G1 to G4) measured neutral or worse; the one lever that
   moved Goo kills was general strength. Brain changes that looked good in one 40-Run table lost reach in the next
   (the E5 retrospective records #207 reverted in #211). A single table is a direction check, never evidence.
3. **Defects found long after the story that owned them.** The two-cell oscillation (#174) wasted a quarter of all
   waits across several finished stories; the boss floor's locked exit (#163) left Runs starving after Goo died;
   avoided zones blocked frontiers and heaps (#183); the talent offered at its cap ended Runs `UNKNOWN_WINDOW` (#162).
   Each was invisible to unit tests on hand-built screens and visible at once in the strategy log of a real Run.
4. **Batched brain PRs.** #186 and #189 each merged cleanly and together broke compilation (also in the E5
   retrospective).
5. **Test seeds near the gate.** Tuning on `standard` and `smoke` is safe, but each story that said "the Warrior kills
   Goo" needed the reader to know which set the number came from. Some pages named it late.

## Lessons that change how we work

The first four are in `CLAUDE.md` under "Lessons from E4"; the fifth is already there as the failing-first-test rule.

1. A Policy story is not done on hand-built screens alone: it ends with a real-Run check (a `smoke` or `standard`
   subset, strategy log read for repeated cells and wasted waits), because the defects that cost the most (#174, #163,
   #183) only showed in real Runs.
2. A tuning table is one measurement on one set. Keep or revert on the recorded numbers of the tuning set, re-run a
   kept step against `main` on a second slice before merging, and say in the PR what the table cannot show.
3. Write down the gate arithmetic early: for a rate-of-reach gate, split the target into reach times conditional
   success (here 30 of 40 reaching Goo at 70% conditional) and report how far each factor is, so a gate that needs
   something an epic away is a correct-course item when it is found, not when its story starts.
4. A differential test compares Decisions only while the Observations are equal and then stops; test the stop (a
   control where the permuted state does come to show), or the test holds for any Brain.
5. The Brain's regression tests for a bug are written first, fail on `main`, and use the real Run that showed the bug
   (ADR-0019), with the seed from the `standard` set.

## Open items carried out of the epic

- Story 4.14: the gate Run on the `goo` set with a committed Registration, its Results page and the throughput
  benchmark with the Brain attached (needs an idle machine); the gate itself stays open (issue #115, plan in #219).
