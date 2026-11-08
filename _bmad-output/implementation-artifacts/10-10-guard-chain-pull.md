---
title: 'Story 10.10: Guard chain pull'
type: 'feature'
created: '2026-11-08'
status: 'review'
baseline_commit: '1e5ddb015'
review_loop_iteration: 0
context: []
---

## Intent

**Problem:** Prison Guards killed 24 of the 480 Runs in the two standard slices (all at depth 7 and 8). A Guard that
is awake pulls the hero one step toward it with a chain when the hero is 2 to 4 tiles away in a clear line, and
cripples the hero; the Policy had no notion of the line.

**Approach:** One fight rule, `Guard.engage`, that steps out of the line when a safe step exists and other enemies are
awake, so the pull would drag the hero toward company.

## Acceptance criteria and where they are held

1. Stats, the chain pull and the drops confirmed at path:line by `upstream-reader`; cited in `docs/rules/combat.md`
   (Guard row, `Guard.java:51-55, 71-135, 179-187`, `Mob.java:561-571`).
2. Rule row `guard-chain` in `docs/brain-rules.md`; `Guard.REACH` cites `Guard.java:183` and sits in the
   `HeuristicLinkTest` EXEMPT list with that reason.
3. `GuardChainTest`: `steps_out_of_the_line` fails on `main` (the old Policy stays in the line), 
   `no_dodge_where_the_pull_cannot_happen` and `a_sleeping_guard_is_left_alone` hold.
4. PR table, six classes, two slices: `docs/results/2026-11-08-10-10-guard-chain-pull.md`.
5. CoachLine template `fight/guard` ("to keep out of the guard's chain"), `docs/coach-line.md`, the closed word list.
6. `fairness-reviewer` run: no parity violation. Open suggestions, not blocking: a cap on consecutive dodges with a
   termination test; `company` counting far enemies; whether `offered` can include trap cells.

## Result

Identical to the baseline in every column on both slices (pooled reach d5 129/240 and 118/240, Goo kills 109/240 and
82/240, 24 Guard deaths before and after). The rule never fired in the 480 Runs. Kept because it is not worse and a
unit screen pins the behaviour; no gain is claimed.
