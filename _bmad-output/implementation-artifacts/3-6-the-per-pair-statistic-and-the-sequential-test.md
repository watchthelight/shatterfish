---
title: 'Story 3.6: The Per-pair statistic and the sequential test'
type: 'feature'
created: '2026-09-23'
status: 'ready-for-dev'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The Rig can run one Brain over a Seed set and cannot say whether a change is better.
FR-21 asks for an early-stopping comparison with stated error rates; ADR-0012 chooses a Per-pair
statistic over the Composite outcome and Fishtest's GSPRT. Story 3.5 also shipped a Registration
without `p0` and `p1`, which ADR-0012 lists and the test cannot run without.

**Approach:** `PairScore` compares two Composite outcomes lexicographically (PRD glossary) and scores
1, ½ or 0; `Gsprt` is the trinomial GSPRT with Van den Bergh's approximation 2.1, regularized and
clamped, bounds `log(β/(1-α))` and `log((1-β)/α)`, burn-in `n0`, maximum `nmax`. The Runner gains
`--against <brain>`: both Brains play each triple under one shared salt, and the test consumes the
pairs in Seed-set order. A Registration for a comparison states `p0`, `p1` in thousandths.

## Boundaries & Constraints

**Always:** Pairs are consumed in the Seed set's order, never in completion order — the order is
fixed before any outcome is seen. A pair with a missing or unreadable Run scores ½ and is counted
separately. The test never stops before `n0` and is undecided at `nmax`. Every verdict carries its
log-likelihood trace.

**Ask First:** Changing the Composite outcome's order. Using the test to cancel in-flight Runs.

**Never:** Copy Fishtest's code: it carries no licence, so it is all-rights-reserved. Implement the
published formulas (Van den Bergh, *GSPRT approximation*, eq. 2.1) and use a pinned Fishtest checkout
only as a black-box oracle to generate the reference values; commit the values and the generator,
not the code. Do not edit a committed Registration: `p0`/`p1` are absent from a baseline's canonical
text, so `H-0001-nightly-smoke` keeps its hash.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Win vs loss | one Run won, the other did not | 1 to the winner's side | N/A |
| Two wins | both won | higher Score wins; equal Score falls through | N/A |
| Two losses | neither won | bosses, then depth, then turns survived | N/A |
| Identical | same outcome | ½ | N/A |
| Missing Run | one log absent or has no end | ½, counted as missing | N/A |
| Early accept | LLR ≥ upper bound at pair k ≥ n0 | ACCEPT at k | N/A |
| Before burn-in | bound crossed at k < n0 | continues | N/A |
| Max reached | no bound by nmax | UNDECIDED at nmax | N/A |
| random vs random | `--against random` | every pair ½ | N/A |

</frozen-after-approval>

## Code Map

- `shatterfish/api/.../Registration.java` -- add `p0PerMil`, `p1PerMil`; absent from a baseline.
- `shatterfish/api/.../RunLog.java:432` -- `Outcome(win, ascended, score, depth, turns, cause, bosses)`.
- `shatterfish/harness/.../log/RunLogReader.java` -- `Log.end().outcome()`, how a pair's outcomes are read.
- `shatterfish/rig/.../Runner.java` -- `run`, `one`, `child`, the salt drawn per triple; `KNOWN`.
- `shatterfish/rig/.../RunIndex.java` -- `summary(...)`, atomic writes.
- `docs/adr/0012-rig-statistics.md:34-55,70-95` -- the statistic, the test, the Registration fields.
- PRD glossary "Composite outcome" -- the order, with turns *survived* (more is better).
- Fishtest `2e540196ed8a72283a17f40793defd0f4a45d9c9`, `server/fishtest/stats/{sprt,LLRcalc}.py` -- the oracle.

## Tasks & Acceptance

**Execution:**
- [ ] `api/Registration.java` -- `p0`/`p1` per mil; comparison needs 0 < p0 < p1 < 1000, baseline needs both 0 and omits them.
- [ ] `rig/PairScore.java` -- the Composite comparison and the pair's score.
- [ ] `rig/Gsprt.java` -- counts, LLR, clamp, verdict, trace.
- [ ] `rig/Comparison.java` -- pairs two folders' logs by triple and salt, in Seed-set order, and runs the test.
- [ ] `rig/Runner.java` -- `--against`; one salt per triple for both children; `comparison.json` beside the summary.
- [ ] `rig/src/test/resources/gsprt-reference.json` + `tools/gsprt_reference.py` -- values from the pinned Fishtest.
- [ ] Tests: `PairScoreTest`, `GsprtTest`, `GsprtReferenceTest`, `ComparisonTest`, and a random-vs-random Runner case.
- [ ] `docs/methodology.md` -- the statistic, the test, the licence decision, how to read `comparison.json`.

**Acceptance Criteria:**
- Given two Composite outcomes, when scored, then the order is Win; Score for two wins; bosses; depth; turns survived (`PairScoreTest`).
- Given counts, when the LLR is computed, then it equals the pinned Fishtest's `sprt.set_state` to 1e-9 on every committed vector (`GsprtReferenceTest`).
- Given a bound crossed before `n0`, then the test continues; at `nmax` it is undecided (`GsprtTest`).
- Given `--against random`, then every pair shares a salt and scores ½ (`RunnerComparisonTest`).
- Rig numbers: the cost of a paired `smoke` comparison against a single-Brain one, in Evidence.

## Design Notes

A pair is two Runs of one triple under one salt. Scoring from the candidate's side keeps "accept"
meaning "the candidate is better", as Fishtest's pass does. Consuming pairs in Seed-set order is what
makes stopping legitimate: completion order depends on which Runs are slow, which depends on the
outcomes.

## Verification

**Commands:**
- `./gradlew build` -- green.
- `./gradlew :rig:run --args="--brain random --against random --seeds smoke --out <a>"` -- 25 pairs, all ½.
