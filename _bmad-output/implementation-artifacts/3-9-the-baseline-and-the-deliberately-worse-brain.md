---
title: 'Story 3.9: The baseline and the deliberately worse Brain'
type: 'feature'
created: '2026-09-23'
status: 'in-progress'
baseline_commit: '34f8f032ee27eb5f37f91c060a9c4ecab221bc5e'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** E3 is done when a random-agent Baseline is published and a deliberately worse Brain is
rejected by the sequential test (SM-5). Nothing has been published under a Registration yet, no
worse Brain exists, pairing's benefit is unmeasured, and two gaps deferred from stories 3.7 and 3.8
let a ranked comparison run under uncalibrated bounds or a statistic it was not registered with.

**Approach:** Add the Brain `random-nodescend` — the random agent with `Descend` removed from what it
may choose. Commit two Registrations before any Run: `H-0002` fixes the random Baseline on
`standard`; `H-0003` registers `random-nodescend` against `random` on `standard` at the calibrated
bounds with the GSPRT. A comparison Registration now states its `statistic`, and the Runner refuses
a ranked comparison on an accepting set whose bounds are not `Calibration.CHOSEN` or whose statistic
is not the gate. `comparison.json` reports the within-pair correlation of turns survived. Run both
ranked invocations and publish two Results pages, with E1's throughput restated beside the Rig's
cost per comparison.

## Boundaries & Constraints

**Always:** Registrations are committed before the Runs they govern; the ledger records both
invocations. Results pages carry the tag, commits, Seed set and version, the Registration stamp,
the verdict with its trace, and the reproducing command. A negative or surprising result is
published as it came out.

**Ask First:** Changing the definition of the worse Brain; running `holdout`.

**Never:** Edit a committed Registration (a new one instead). Hand-edit `comparison.json` or a log.
Change the calibrated bounds to get a verdict.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Worse Brain | offered set holds `Descend` | chooses uniformly among the rest | N/A |
| Only `Descend` offered | nothing else to choose | returns null; the Run ends by name, counted missing | N/A |
| Ranked comparison, uncalibrated bounds, `standard` | p1, n0, cap, α, β or nmax ≠ CHOSEN | refused, recorded FORBIDDEN | IllegalArgumentException |
| Statistic mismatch | Registration says EPROCESS, gate is GSPRT | refused | IllegalArgumentException |
| Smoke comparison | any bounds | allowed (direction check) | N/A |
| Correlation | pairs with both Runs reached | Pearson r of turns; absent if fewer than 3 | N/A |

</frozen-after-approval>

## Code Map

- `shatterfish/harness/.../agent/RandomAgent.java` -- the uniform chooser the worse Brain filters.
- `shatterfish/rig/.../Brains.java` -- `names`, `sourceOf`, `of`, `configHash`: where a Brain is registered.
- `shatterfish/api/.../Registration.java` -- 16 components; comparisons carry p0/p1/missing; add `statistic`.
- `shatterfish/rig/.../Registrations.java:~230` -- reads the optional comparison keys.
- `shatterfish/rig/.../Runner.java:~540` -- `registration(...)` refusal chain for comparisons.
- `shatterfish/rig/.../Calibration.java` -- `CHOSEN`, `ALPHA_PER_MIL`, `BETA_PER_MIL`, `MAXIMUM`.
- `shatterfish/rig/.../Comparison.java` -- `write`: add the correlation.
- `docs/results/e1-throughput.md` -- 399.5 waits/s per process, 362.3 Runs/min.

## Tasks & Acceptance

**Execution:**
- [ ] `harness/.../agent/NoDescendAgent.java` + `Brains` -- the worse Brain, its sources and config.
- [ ] `api/Registration`, `rig/Registrations` -- `statistic` for comparisons, absent for baselines.
- [ ] `rig/Runner` -- refuse uncalibrated bounds on accepting sets, and a statistic other than the gate.
- [ ] `rig/Comparison` -- within-pair correlation of turns in `comparison.json`.
- [ ] `registrations/H-0002-*.json`, `H-0003-*.json` -- committed before the Runs.
- [ ] Tests: `NoDescendAgentTest`, `RegistrationTest`, `RunnerRegistrationTest`, `ComparisonTest`.
- [ ] The two ranked runs; `docs/results/` pages; `docs/methodology.md` and `docs/results/index.md`.

**Acceptance Criteria:**
- Given H-0002, when the random Baseline runs on `standard`, then its Results page exists with the command that reproduces it.
- Given H-0003, when `random-nodescend` is compared with `random`, then the verdict is REJECT and the page publishes it with its trace.
- Given the comparison, then the within-pair correlation is reported and what it says about pairing is stated.
- Rig numbers in Evidence: both runs' Runs, ms and Runs/s, beside E1's throughput.

## Verification

**Commands:**
- `./gradlew build` -- green.
- `./gradlew :rig:run --args="--brain random --seeds standard --out <a> --registration H-0002-random-standard"` -- ranked Baseline.
- `./gradlew :rig:run --args="--brain random-nodescend --against random --seeds standard --out <b> --registration H-0003-nodescend-worse"` -- REJECT.

## Spec Change Log

- **The worse Brain named in SM-5 is the random agent itself** (found by the first ranked run,
  H-0004). `random_nodescend` played the same Run as `random` on all 458 pairs of `standard` that
  reached an ending: the random agent never takes the stairs, so withholding `Descend` withholds
  nothing, and a one-sided test rejects two identical Brains as readily as a worse one. That REJECT
  is published as the null result it is. Amended: the deliberately worse Brain is the random agent
  with `Rest` withheld (`random_norest`), chosen on the development set `smoke` (withholding
  `Attack` changed 1 pair of 25, withholding `Rest` lost 21 of 25) and registered on `standard`
  as H-0005, with the reverse comparison as H-0006. KEEP: the H-0004 run and its result.
- **H-0005's first invocation was VOID** at pair 27: 7 of the first 27 pairs had a Run missing,
  over the calibrated cap of 0.25, although over all 500 pairs the candidate scored 0.239. Written
  here before it runs: H-0005 is invoked **once more**, and that invocation is published whatever
  it concludes, beside the first. There is no third. The ledger carries both.
