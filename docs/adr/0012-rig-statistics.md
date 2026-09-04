---
status: accepted
date: 2026-09-04
deciders: watchthelight (product owner), Claude (engineer)
---

# ADR-0012: The Rig's statistics: the Per-pair GSPRT

## Context and problem statement

The Rig compares two Brains with a Sequential test over Per-pair statistics and reports accept,
reject or undecided (PRD FR-21), with bounds pre-registered under a Hypothesis id (FR-22), never
gating on win rate while it is near zero, and validating realized error rates by simulation before
a bound is trusted. The research report recommends porting Fishtest's GSPRT and warns that
pairing may buy little (chess sees about 15%), that GSPRT's guarantees are asymptotic, that a
depth-gated metric invites diving, and that e-process designs are the alternative to evaluate
(research §3, contrary evidence B, recommendation 3). Decide the statistic, the test, what is
pre-registered, what is calibrated, and what is reported beside the verdict.

Non-negotiables touched: #5 (measured and reproducible), #1 indirectly (Oracle refused).

## Decision drivers

- Early stopping with stated error rates; an honest "undecided".
- Robust to a metric that is mostly ties early (both Brains die on the same floor).
- The gated statistic must not reward diving (SM-C2) or Score without Wins (SM-C3).
- Every number reproducible from the Run logs and the Registration (AD-11).
- No statistics library; the port is small and reviewable.

## Considered options

**Statistic**

1. **Per-pair statistic: for each seed `s` in the Seed set, both Brains run the same (seed, salt)
   pair; the two Composite outcomes are compared lexicographically (Win; then Score for two
   wins; then bosses killed; then Floor depth; then turns) and the pair scores 1 (A better),
   0.5 (equal), 0 (A worse).** Chosen. Pairing on (seed, salt) is the strongest common-random-
   numbers design available; its benefit is measured (below), not assumed.
2. Mean Score difference. Rejected: Score without a Win is gameable (SM-C3), and its variance is
   dominated by rare wins.
3. Win-rate difference. Rejected while wins are near zero (FR-21); reported with a confidence
   interval instead.
4. Depth difference. Rejected: rewards diving (SM-C2); depth stays inside the Composite order
   below bosses.
5. Unpaired Composite comparison (Mann-Whitney over all Runs). Kept as the reported cross-check
   when the measured pair correlation is near zero; not the gate.

**Test**

6. **GSPRT ported from Fishtest (`sprt.py` for the driver, `LLRcalc.py` for approximation 2.1
   and the regularization; the port is two files): the log-likelihood ratio uses the normal
   approximation with the sample variance of the pair scores (approximation 2.1 in Van den
   Bergh's note), regularized, with the per-pair increment clamped; H0 and H1 are pair-score
   means `p0` and `p1`; bounds `log(β/(1-α))` and `log((1-β)/α)`; a burn-in of `n0` pairs before
   any stop; a maximum of `nmax` pairs after which the result is undecided.** Chosen.
7. Fixed-sample test (Wilcoxon signed-rank on the pairs at `nmax`). Rejected as the gate: no
   early stop; reported as the fixed-sample cross-check at `nmax`.
8. E-process (test supermartingale, mixture or betting form) that needs no pre-registered
   alternative and allows continued testing. **Evaluated in E3 as the alternative**: the
   calibration story runs both on the same simulated distribution; the e-process replaces 6 if
   the GSPRT's realized error rate exceeds its nominal rate by more than the margin the
   Registration states.
9. Bayesian sequential test with a prior on the effect. Rejected: the prior is a second argument
   to have with every skeptic.
10. Fishtest's pentanomial model over game pairs. Rejected: our pair is one comparison, not two
    games with sides; the trinomial pair score is the analogue.

## Decision outcome

- `rig` implements `PairScore` (the lexicographic Composite comparison), `Gsprt` (option 6) and,
  in E3's calibration story, `EProcess` (option 8) side by side.
- A Registration fixes: Hypothesis id, both Brains (name, commit, config hash), Seed set and
  version, `p0`, `p1`,
  `α`, `β`, `n0`, `nmax`, the per-Decision budget for the Overlay-relevant comparisons, and the
  machine class. Nothing here has a default in this ADR; the first values are set by the E3
  calibration story and published on the methodology page with the simulation that produced
  them.
- Calibration (FR-21): the E3 story simulates both tests on the Rig's own outcome distribution
  (bootstrapped from the random-agent Baseline's Runs, then from the first scripted Brain's) to
  report realized α and β at the registered `n0` and `nmax`; bounds are recalibrated per Upstream
  tag and per Composite-outcome change (a new Registration, never an edit).
- Reported beside every verdict (FR-21, FR-25): the log-likelihood trace, the number of pairs,
  the pair-score mean with a confidence interval, the measured within-pair correlation (the
  answer to research open question 2), win rate with a Wilson interval, the survival curve and
  median death depth, boss staircase, and the fixed-sample cross-check at stop.
- `smoke` runs the same statistic and prints the trace but is labeled a direction check; only
  `standard` (and `bosses` for fight-specific comparisons) can accept.
- Pairing is per (seed, hero class, challenge flags) triple, which is what a Seed set holds: the
  two Brains run the same triple with the same salt, drawn by the runner when the pair executes
  from a per-invocation secret and written to both Run logs (ADR-0007). Salts are never published
  before a comparison completes.
- A pair with a missing Run (a crash, a hang, a Run that hit the turn cap) is scored as a tie and
  counted separately on the Results page; a Registration whose missing-pair fraction exceeds the
  fraction it declares is void, so a Brain cannot win by crashing on seeds it would lose.
- The Rig refuses a `holdout` Registration for a development comparison; `holdout` may be used
  only for a registered release-level claim or the SM-1 Win claim, at most once per Brain
  version, and every use is recorded in the Results ledger (FR-20). Any Run with `oracle` true is
  refused outright.

### Consequences

- Good: one small, reviewable statistic; a skeptic can recompute the trace from `runs.jsonl`.
- Good: ties count as half and shrink the variance, so a mostly-tied early Run does not stop the
  test falsely; the burn-in guards the asymptotic approximation.
- Bad: pairing's benefit is unknown until measured; the design does not depend on it being large.
- Bad: two tests in E3 is more work than one; it is the price of trusting the bounds.

## Pre-mortem

*If this is wrong in six months, why?*

- The Composite outcome's lexicographic order makes almost every pair a tie or a coin flip on
  turns survived, and the test never resolves. Mitigation: the trace and the tie fraction are
  reported; the `bosses` set and a per-floor survival comparison are the finer instruments, and a
  Composite order change is a new Registration.
- The normal approximation is poor for a three-valued score at `n0`. Mitigation: the calibration
  simulation sets `n0`; the e-process is the fallback.
- The salts committed with a Registration let a Brain author tune to them. Mitigation: the Brain
  never sees a salt (AD-1, AD-2); a Results page for a public claim uses `holdout` or a fresh
  draw (SM-1).
- Two Brains diverge at their first differing Decision and the pair is then two independent Runs,
  so "paired" is a name only. Mitigation: that is the expected outcome per the research; the
  measured correlation is published and the unpaired cross-check is reported.
