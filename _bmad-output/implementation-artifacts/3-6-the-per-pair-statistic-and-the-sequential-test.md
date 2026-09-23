---
title: 'Story 3.6: The Per-pair statistic and the sequential test'
type: 'feature'
created: '2026-09-23'
status: 'done'
review_loop_iteration: 1
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
- [x] `api/Registration.java` -- `p0`/`p1` per mil; comparison needs 0 < p0 < p1 < 1000, baseline needs both 0 and omits them.
- [x] `rig/PairScore.java` -- the Composite comparison and the pair's score.
- [x] `rig/Gsprt.java` -- counts, LLR, clamp, verdict, trace.
- [x] `rig/Comparison.java` -- pairs two folders' logs by triple and salt, in Seed-set order, and runs the test.
- [x] `rig/Runner.java` -- `--against`; one salt per triple for both children; `comparison.json` beside the summary.
- [x] `rig/src/test/resources/gsprt-reference.json` + `tools/gsprt_reference.py` -- values from the pinned Fishtest.
- [x] Tests: `PairScoreTest`, `GsprtTest`, `GsprtReferenceTest`, `ComparisonTest`, and a random-vs-random Runner case.
- [x] `docs/methodology.md` -- the statistic, the test, the licence decision, how to read `comparison.json`.

**Acceptance Criteria:**
- Given two Composite outcomes, when scored, then the order is Win; Score for two wins; bosses; depth; turns survived (`PairScoreTest`).
- Given counts, when the LLR is computed, then it equals the pinned Fishtest's `sprt.set_state` to 1e-9 on every committed vector (`GsprtReferenceTest`).
- Given a bound crossed before `n0`, then the test continues; at `nmax` it is undecided (`GsprtTest`).
- Given `--against random`, then every pair shares a salt and scores ½ (`RunnerComparisonTest`).
- Rig numbers: the cost of a paired `smoke` comparison against a single-Brain one, in Evidence.

## Evidence

**Rig numbers.** The `smoke` set, 24 processes, default cap: one Brain plays its 25 Runs in
**7,538 ms**; `--brain random --against random` plays 50 Runs in **9,996 ms** (5.0 Runs/s). Twice
the Runs for a third more time, because the pool is fuller. On the methodology page beside the
command.

**The Fishtest reference.** `tools/gsprt_reference.py` (ours) imports a Fishtest checkout at
`2e540196ed8a72283a17f40793defd0f4a45d9c9` and writes 435 cases -- five hypotheses, three error-rate
pairs, twenty-nine count sets including tie-heavy small samples -- with bounds, LLR, raw LLR and the
clamp flag. `GsprtReferenceTest` checks every column to 1e-9 and pins the fixture's SHA-256
(`17c78aab...`). Fishtest has no licence, so none of its code is in the repository.

**Deviations from the task list, argued.** The fixture is `gsprt-reference.txt`, whitespace
columns, not JSON: the rig reads it with a split rather than a parser it would otherwise not need.
`random` against `random` does not "score 1/2 on every pair" for the reason the spec imagined: the
two halves of a pair are the same Run (same salt, same chain, checked from each child's own log),
so every pair that reaches an ending ties, and the Runs stopped by the turn cap count as missing,
which also score a half. The Registration gained `missing_per_mil` beside `p0`/`p1`, which the spec
did not name; see the reviews.

**Four reviews.** The blocking finding, from the fairness review with a worked example: a missing
pair scored a tie, and ties both raise the mean and cut the variance, so a Brain that crashed on
exactly the seeds it would lose bought an ACCEPT. A comparison now states the share of missing
pairs it tolerates, and past it -- or when every consumed pair is missing -- the verdict is `VOID`.
A Run stopped at the turn cap is the Rig's stop, not the game's ending, and counts as missing. Also
found and fixed: Fishtest's server stops on the exact GLR and not on eq. 2.1, and the page claimed
more than the reference proves; the baseline's configuration and release-level version were never
checked; a comparison could register H1 at or below one half, or error rates summing past one;
`smoke` could accept although ADR-0012 lets only `standard` and `bosses`; `comparison.json` counted
every pair played rather than those consumed, so its counts did not reproduce its own LLR; one wait
counter was shared by both sides; a Brain compared with itself collided in the alive map;
`--against` with `--verify` or `--replay` was silently ignored; `--verify` on a comparison folder
failed; the ledger's FINISHED lines did not say what was concluded.

**Mutation battery: 32 mutations, 31 killed, 1 deliberate survivor.** Nine survived the first run:
the all-missing void, `Gsprt`'s alpha-plus-beta check, the baseline's configuration and version
checks, per-side waits, the one-header rule, `micros` refusing to saturate, the Registration's
missing fraction below one, and the alive-map key. Seven of the nine were review-patch guards --
the pattern of every E3 story so far. Each now has a test that kills it, except M20: the alive map
is only read when children must be destroyed after a failure, and a collision there leaks a child
process rather than changing any number; a test would have to kill a comparison mid-run and count
processes. Kept, and said here.

## Design Notes

A pair is two Runs of one triple under one salt. Scoring from the candidate's side keeps "accept"
meaning "the candidate is better", as Fishtest's pass does. Consuming pairs in Seed-set order is what
makes stopping legitimate: completion order depends on which Runs are slow, which depends on the
outcomes.

## Verification

**Commands:**
- `./gradlew build` -- green.
- `./gradlew :rig:run --args="--brain random --against random --seeds smoke --out <a>"` -- 25 pairs, all ½.
