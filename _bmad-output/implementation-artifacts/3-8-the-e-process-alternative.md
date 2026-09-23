---
title: 'Story 3.8: The e-process alternative'
type: 'feature'
created: '2026-09-23'
status: 'done'
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

## Evidence

**Rig numbers.** `./gradlew :rig:calibrate` now runs 27 GSPRT cells on the choosing sequences and 7
tests on the fresh ones (the validation, and both designs at three values of `p1`), 10,000
sequences per hypothesis each: **4,130 ms** by the task's own clock, against 3,054 ms with the
GSPRT alone. No Brain changed, so there is no strength result.

**The result.** At the chosen bounds (p0 0.50, p1 0.60, alpha = beta = 0.05, cap 0.25), on the same
fresh sequences: GSPRT false-accept 5.73%, false-reject 3.99%, power 92.6%, 108 / 102 pairs under
H0 / H1; e-process 2.21%, 2.29%, 93.6%, 163 / 164 pairs. At p1 0.55 the e-process's power is 37%
against the GSPRT's 66%. The GSPRT is within its margin, so the gate stays the GSPRT
(`SequentialTest.GATE`), and ADR-0012 records the outcome.

**Deviations, argued.** "Behind a flag" is `SequentialTest.GATE`, a constant `CalibrationTest`
holds to the calibration's own conclusion, not a command-line flag: an unranked comparison runs no
test at all, and a ranked one must run the gate, so a flag would have had nothing to switch that a
Registration should allow. The e-process's futility side uses `p1`; acceptance does not, which is
the property the ADR asked for. The rule was extended in review: the e-process takes the gate only if
it is itself within the margin and powerful, and with no chosen GSPRT cell the rule picks neither.

**Three reviews.** Fixed: the gate went to the e-process on any GSPRT failure, and on no choice at
all, without looking at the e-process; an e-process REJECT was a crossing of a wealth not in the
trace (the reported statistic is now the leading wealth, the futility one negated); the duel's
`p1` values were hard-coded; `EProcess.of`, `SequentialTest.of` and a comparison under the
e-process were untested; the docs gave the e-process's cost under H1 only and hid its weakness at
p1 0.55; the bet's clip and citation were imprecise; the ADR's decision did not point at its
outcome. Deferred to story 3.9: a Registration does not yet fix which statistic it was registered
under.

**Mutation battery: 25 mutations, 24 killed, 1 removed.** Two survived the first run. M12 (the
refusal of a baseline in `EProcess.of`) survived because the constructor refuses a baseline's zero
hypotheses anyway; the test now checks the refusal is the baseline one. M22 (adding the chosen `p1`
to the duel) could not be killed because the chosen cell always comes from the grid; the redundant
line was removed.

## Suggested Review Order

**The alternative**

- Entry point: the betting e-process, acceptance and futility, reporting the leading wealth.
  `shatterfish/rig/src/main/java/org/shatterfish/rig/EProcess.java:140`

- The aGRAPA bet and its clip.
  `shatterfish/rig/src/main/java/org/shatterfish/rig/EProcess.java:133`

**One interface, one gate**

- The gate constant, and the VOID rule both designs share.
  `shatterfish/rig/src/main/java/org/shatterfish/rig/SequentialTest.java:29`
  `shatterfish/rig/src/main/java/org/shatterfish/rig/SequentialTest.java:71`

- The rule: keep a calibrated GSPRT; else a qualified e-process; else neither.
  `shatterfish/rig/src/main/java/org/shatterfish/rig/Calibration.java:287`

- Both designs on the same fresh sequences.
  `shatterfish/rig/src/main/java/org/shatterfish/rig/Calibration.java:265`

**Publication**

- Which test gates, and why, with the numbers.
  `docs/methodology.md:532`

**Tests**

- The gate follows the calibration, and the duel's rows.
  `shatterfish/rig/src/test/java/org/shatterfish/rig/CalibrationTest.java:99`

- The rule going the other way, and refusing an unqualified challenger.
  `shatterfish/rig/src/test/java/org/shatterfish/rig/CalibrationTest.java:135`
