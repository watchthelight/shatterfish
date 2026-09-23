---
title: 'Story 3.7: Calibrate the bounds'
type: 'feature'
created: '2026-09-23'
status: 'in-progress'
baseline_commit: 'f1b070c0c2e3b671b9d6786ee7e06e7224e71960'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Story 3.6's GSPRT states nominal error rates, but its guarantees are asymptotic and the
pair score is three-valued, often tied, and sometimes missing. FR-21 and ADR-0012 require the
realized false-accept and false-reject rates to be measured on this project's own outcomes before a
bound is trusted, and story 3.8 needs a declared margin to test the e-process against.

**Approach:** Extract the Composite outcomes of a random-Brain run of `standard` into a committed,
provenance-stamped table. A deterministic Monte-Carlo (`Calibration`) bootstraps pairs from that
table under H0 (two independent draws, mean ½) and under H1 (the same stream with a fraction of pairs
made wins so the mean is exactly `p1`), runs `Gsprt` over each simulated sequence, and reports
realized accept, reject, undecided and void rates, stopping times, tie and missing fractions over a
small grid of `p1` and `n0`. The chosen cell and the margin are constants in code; a generated
results page and the methodology page publish them.

## Boundaries & Constraints

**Always:** The simulation is a pure function of (table, grid, simulation count, seed): the same
inputs write the same page byte for byte, and CI fails on drift. A missing Run is scored as `Gsprt`
and `PairScore` score it, and the Registration's missing cap applies in the simulation as in a real
comparison. The table records where it came from: tag, Brain and its version, Seed set and version,
cap, the command, and each Run's chain.

**Ask First:** Changing `Gsprt` or `PairScore` to make the numbers look better. Choosing a margin
after seeing the e-process's numbers.

**Never:** Commit the 37 MB of Run logs. Use a statistics library. Draw the simulation's randomness
from the game's RNG or from a salt. Use `holdout`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| H0 simulation | table, p0 = ½ | realized false-accept = share of ACCEPT | N/A |
| H1 simulation | table, tilt q = 2(p1 − ½) | realized false-reject = share of REJECT; undecided reported apart | N/A |
| Missing Runs | table rows with a cause other than DEATH/WIN | pair scores ½, counted missing, VOID past the cap | N/A |
| Same inputs twice | same seed and count | identical page | N/A |
| Empty or all-missing table | no reached outcome | refused with a message | IllegalArgumentException |
| Tilt impossible | p1 ≤ ½ or p1 ≥ 1 | refused | IllegalArgumentException |

</frozen-after-approval>

## Code Map

- `shatterfish/rig/.../Gsprt.java` -- `run(pairs, missing)`, `allowingMissing`; the test being calibrated. Read-only.
- `shatterfish/rig/.../PairScore.java` -- `of`, `reached`; the pair score. Read-only.
- `shatterfish/harness/.../log/RunLogReader.java` -- reads each log's end outcome for the extraction.
- `shatterfish/rig/.../Brains.java` -- `version(root, name)` for the table's provenance.
- `shatterfish/rig/build.gradle` -- JavaExec tasks `seeds`/`reference` are the pattern; test `inputs.dir` for committed folders.
- `docs/adr/0012-rig-statistics.md` -- calibration is FR-21's; the e-process replaces the GSPRT past the margin.
- Measured input: random on `standard`, cap 20,000: 500 Runs in 180,379 ms; 458 `DEATH`, 42 `UNKNOWN_WINDOW`; 491 on depth 1; no bosses.

## Tasks & Acceptance

**Execution:**
- [x] `shatterfish/rig/.../Calibration.java` -- `extract(runsFolder, root)` writes the outcome table; `simulate(table, grid, sims, seed)` returns per-cell rates; `page(...)` renders Markdown; `CHOSEN` and `MARGIN_PER_MIL` constants; `main` for both modes.
- [x] `calibration/v4.0.0-random-standard.jsonl` -- the committed table: a provenance header line, then one line per Run (run id, chain, cause, win, score, bosses, depth, turns).
- [x] `shatterfish/rig/build.gradle` -- `:rig:calibrate` JavaExec task; `calibration/` as a test input.
- [x] `docs/results/calibration-v4.0.0.md` -- generated; linked from `docs/results/index.md`.
- [x] `shatterfish/rig/src/test/.../CalibrationTest.java` -- tilt gives mean p1; determinism; refusals; the chosen cell's realized rates within nominal + margin; the committed page matches a fresh render.
- [x] `docs/methodology.md` -- "Calibrating the bounds": method, chosen `p0`, `p1`, α, β, `n0`, `nmax`, missing cap, margin, tie fraction, and the link.

**Acceptance Criteria:**
- Given the committed table, when the calibration runs, then realized false-accept and false-reject at the chosen `n0` and `nmax` are each at most nominal + `MARGIN_PER_MIL`/1000 (`CalibrationTest`).
- Given the same table, seed and count, when rendered twice, then the pages are equal, and the committed page equals a fresh render (`CalibrationTest`).
- Given the grid, then the page reports the tie fraction and the missing fraction of the pair statistic under H0.
- Rig numbers in Evidence: the extraction run (Runs, ms) and the simulation's wall time.

## Design Notes

H1 by tilting rather than by a second Brain: there is no better Brain yet, and a tilt that turns a
share q of pairs into wins keeps every other property of the real stream (ties, missing Runs, their
rate). With H0's mean ½, the tilted mean is ½ + q/2, so q = 2(p1 − ½).

Independent draws are the pessimistic pairing: two different Brains diverge at their first differing
Decision (ADR-0012's pre-mortem), and correlation only shrinks variance.

## Verification

**Commands:**
- `./gradlew :rig:calibrate` -- rewrites the page; `git diff` empty on a second run.
- `./gradlew build` -- green.
