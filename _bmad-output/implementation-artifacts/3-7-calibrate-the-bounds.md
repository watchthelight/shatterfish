---
title: 'Story 3.7: Calibrate the bounds'
type: 'feature'
created: '2026-09-23'
status: 'done'
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

## Evidence

**Rig numbers.** The bootstrap source: `./gradlew :rig:run --args="--brain random --seeds standard
--parallel 24 --out <dir>"`, **500 Runs in 180,379 ms** (2.77 Runs/s, 24 processes), 0 incomplete;
458 `DEATH`, 42 `UNKNOWN_WINDOW`; 491 on depth 1; no bosses. The extraction of the table from those
logs takes **2,867 ms**; the calibration itself -- 27 cells, 2 hypotheses, 10,000 sequences each,
plus the validation -- takes **3,054 ms** (both timed by the task's own clock, JVM start excluded).
No Brain changed, so there is no strength result.

**The result.** p0 0.500, p1 0.600, alpha = beta = 0.050, n0 20, nmax 500, missing cap 0.250.
On 10,000 fresh sequences: realized false-accept 5.73%, false-reject 3.99%, power 92.6%, 108 pairs
under H0 and 102 under H1 on average. The declared margin for story 3.8 is **0.010**. Tie fraction
under H0 18.2%, of which 16.1 points are missing pairs.

**Deviations, argued.** `CHOSEN` is written down and the test holds the rule's choice to it, rather
than being the rule's output alone: the Registrations of story 3.9 will cite it. The grid gained a
missing cap of 0.20 in review, after the first numbers were seen; it did not change the choice (it
voids one result in six), and the rule itself is the one written before the first run. The table's
file name is derived from its own tag, Brain and Seed set, so an extraction of another folder
cannot overwrite this one.

**Three reviews.** Findings fixed: H1's tilt turned missing pairs into wins, so the alternative
voided less often than a real better Brain would, contradicting the design note (now missing pairs
stay missing and the tilt is q = 2(p1 - 1/2)/(1 - m), with each cell's simulated H1 mean printed
beside its p1); the chosen cell's rates were quoted from the sequences it was chosen on (now from
fresh ones, and the false-accept rate above nominal is stated); "calibrated" read "yes" for cells
that void almost everything (renamed "errors within margin", with the page saying why power is also
required); pair counts included void and undecided sequences without saying so; H0 stopping times
were not published; the table's reader and the extraction trusted their input (every key, the
stated count, strict booleans, one Brain, tag and cap per folder, the Seed set covered, each chain
the log's own, logs inside the folder); the extraction's provenance was untested; `:rig:test` did
not take the methodology page as an input. Deferred to story 3.9: nothing yet holds a comparison
Registration to calibrated bounds.

**Mutation battery: 28 mutations, 28 killed.** Four survived the first run: the provenance keys,
strict booleans, a summary with no Seed set, and an empty chain in the index. The first survived
because a missing `runs` fails to parse, and a `NumberFormatException` is an
`IllegalArgumentException`, so the refusal test passed for the wrong reason; the refusals are now
checked by what they say.

## Suggested Review Order

**The simulation**

- Entry point: bootstrap, choose by rule, validate on fresh sequences.
  `shatterfish/rig/src/main/java/org/shatterfish/rig/Calibration.java:227`

- H1 tilts reached pairs only; missing pairs stay missing.
  `shatterfish/rig/src/main/java/org/shatterfish/rig/Calibration.java:318`

- The share that puts the mean at p1, and what it refuses.
  `shatterfish/rig/src/main/java/org/shatterfish/rig/Calibration.java:289`

- The rule fixed before the numbers: margin and power, then p1, cap, pairs.
  `shatterfish/rig/src/main/java/org/shatterfish/rig/Calibration.java:361`

- The margin story 3.8 is judged by, and the chosen bounds.
  `shatterfish/rig/src/main/java/org/shatterfish/rig/Calibration.java:76`

**The table**

- Extraction: every row held to one provenance.
  `shatterfish/rig/src/main/java/org/shatterfish/rig/Calibration.java:454`

- Reading: every key, the stated count.
  `shatterfish/rig/src/main/java/org/shatterfish/rig/Calibration.java:391`

**Publication**

- The generated page.
  `shatterfish/rig/src/main/java/org/shatterfish/rig/Calibration.java:570`

- The method and the chosen bounds, for a reader.
  `docs/methodology.md:468`

**Tests and config**

- A fresh render is the committed page.
  `shatterfish/rig/src/test/java/org/shatterfish/rig/CalibrationTest.java:66`

- The chosen bounds hold on fresh sequences.
  `shatterfish/rig/src/test/java/org/shatterfish/rig/CalibrationTest.java:75`

- The extraction's provenance, key by key.
  `shatterfish/rig/src/test/java/org/shatterfish/rig/CalibrationTest.java:302`

- The task, and the inputs that keep the checks running.
  `shatterfish/rig/build.gradle:38`
