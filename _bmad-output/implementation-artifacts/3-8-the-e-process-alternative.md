---
title: 'Story 3.8: The e-process alternative'
type: 'feature'
created: '2026-09-23'
status: 'in-review'
baseline_commit: '2c81c1cb3f263e8dc8026a524602e1b6d49ae169'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** ADR-0012 chose the GSPRT and kept an e-process as the calibrated alternative: the
choice of statistic is itself to be evidence-based. Story 3.7 measured the GSPRT and declared the
margin (0.010); nothing yet measures the alternative or applies the rule that picks between them.

**Approach:** Add `EProcess`, a one-sided betting e-process (Waudby-Smith and Ramdas, aGRAPA bets)
that accepts when its wealth against H0 (pair-score mean ≤ `p0`) reaches 1/α and needs no
alternative to do so, with a second betting process against mean ≥ `p1` for futility at 1/β. Both
tests implement one `SequentialTest` interface. The calibration runs both on the same fresh
sequences it validates the GSPRT on, publishes realized error rates and stopping times side by
side, and applies the rule: the e-process becomes the gate only if the GSPRT's realized error exceeds
nominal by more than the margin. `SequentialTest.GATE` names the gate; the other stays in the tree.

## Boundaries & Constraints

**Always:** Same sequences, same missing-pair VOID rule, same α, β, `nmax` for both. The rule is the
one ADR-0012 and story 3.7 stated, applied in code, with its outcome asserted by a test. Every
number published is on the generated calibration page.

**Ask First:** Changing the margin, or the GSPRT, to change the outcome of the rule.

**Never:** A statistics library. An e-process whose validity depends on the burn-in or on `p1`
for acceptance. Removing the loser.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Strong candidate | every pair a win | ACCEPT once log wealth ≥ log(1/α) | N/A |
| Hopeless candidate | every pair a loss | REJECT once futility log wealth ≥ log(1/β) | N/A |
| All ties at ½ | mean exactly p0 | never ACCEPT; wealth does not grow | N/A |
| Missing past cap | as Gsprt | VOID | N/A |
| Bad parameters | p0 ≥ p1, α+β ≥ 1, nmax < 1 | refused | IllegalArgumentException |
| GSPRT within margin | 3.7's validation rates | GATE stays GSPRT | N/A |

</frozen-after-approval>

## Code Map

- `shatterfish/rig/.../Gsprt.java` -- `run(pairs, missing)` VOID rule, `Result`, accessors; becomes a `SequentialTest`.
- `shatterfish/rig/.../Comparison.java:60,84,161` -- takes a `Gsprt`; writes its parameters to `comparison.json`.
- `shatterfish/rig/.../Runner.java:405` -- `Gsprt.of(registration)`; becomes `SequentialTest.of`.
- `shatterfish/rig/.../Calibration.java` -- `pass`, `tally(Gsprt, ...)`, `simulate`, `page`; validation seed.
- `docs/methodology.md` "Calibrating the bounds" -- where the comparison and the gate are stated.

## Tasks & Acceptance

**Execution:**
- [x] `shatterfish/rig/.../SequentialTest.java` -- interface (run, parameters, bounds), `Statistic` enum, `GATE`, `of(Registration)`.
- [x] `shatterfish/rig/.../EProcess.java` -- aGRAPA acceptance and futility processes, VOID rule shared with Gsprt.
- [x] `shatterfish/rig/.../Gsprt.java`, `Comparison.java`, `Runner.java` -- use the interface; `comparison.json` names the statistic.
- [x] `shatterfish/rig/.../Calibration.java` -- both tests on the validation sequences for each `p1` at the chosen `n0` and cap; the gate rule; the page's side-by-side section.
- [x] `shatterfish/rig/src/test/.../EProcessTest.java` and `CalibrationTest` -- the matrix; the gate.
- [x] `docs/methodology.md` -- which statistic, why, with what numbers; the e-process described.

**Acceptance Criteria:**
- Given the committed table, when the calibration runs, then the page shows both tests' realized false-accept, false-reject, undecided, void and mean pairs on the same sequences (`CalibrationTest`).
- Given the GSPRT's validated rates, when the rule is applied, then `Calibration.gate` equals `SequentialTest.GATE` (`CalibrationTest`).
- Given H0 exactly, when the e-process runs 10,000 sequences, then its realized false-accept is at most α (`CalibrationTest`, from the page's numbers).
- Rig numbers in Evidence: the calibration's wall time with both tests.

## Design Notes

aGRAPA bet for testing mean ≤ m on scores in [0, 1]: λ_t = (μ̂ − m)/(σ̂² + (μ̂ − m)²) from the
estimates before pair t (μ̂ from ½ + Σx over t, σ̂² from ¼ + Σ(x − μ̂)² over t), clipped to
[0, ½/m], so wealth `Π(1 + λ(x − m))` stays positive. Ville's inequality bounds the chance it ever
reaches 1/α under H0 by α, at every stopping time, with no burn-in and no alternative.

## Verification

**Commands:**
- `./gradlew :rig:calibrate` -- rewrites the page; a second run leaves `git diff` empty.
- `./gradlew build` -- green.
