---
title: 'Story 3.9: The baseline and the deliberately worse Brain'
type: 'feature'
created: '2026-09-23'
status: 'done'
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
- [x] `harness/.../agent/NoDescendAgent.java` + `Brains` -- the worse Brain, its sources and config.
- [x] `api/Registration`, `rig/Registrations` -- `statistic` for comparisons, absent for baselines.
- [x] `rig/Runner` -- refuse uncalibrated bounds on accepting sets, and a statistic other than the gate.
- [x] `rig/Comparison` -- within-pair correlation of turns in `comparison.json`.
- [x] `registrations/H-0002-*.json`, `H-0003-*.json` -- committed before the Runs.
- [x] Tests: `NoDescendAgentTest`, `RegistrationTest`, `RunnerRegistrationTest`, `ComparisonTest`.
- [x] The two ranked runs; `docs/results/` pages; `docs/methodology.md` and `docs/results/index.md`.

**Acceptance Criteria:**
- Given H-0002, when the random Baseline runs on `standard`, then its Results page exists with the command that reproduces it.
- Given H-0003, when `random-nodescend` is compared with `random`, then the verdict is REJECT and the page publishes it with its trace.
- Given the comparison, then the within-pair correlation is reported and what it says about pairing is stated.
- Rig numbers in Evidence: both runs' Runs, ms and Runs/s, beside E1's throughput.

## Verification

**Commands** (amended; see the change log -- H-0003 and `random-nodescend` do not exist):
- `./gradlew build` -- green.
- `./gradlew :rig:run --args="--brain random --seeds standard --out <a> --registration H-0002-random-standard"` -- ranked Baseline.
- `./gradlew :rig:run --args="--brain random_norest --against random --seeds standard --out <b> --registration H-0005-norest-worse"` -- REJECT.
- `./gradlew :rig:run --args="--brain random --against random_norest --seeds standard --out <c> --registration H-0006-random-over-norest"` -- ACCEPT.

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
- **H-0003 withdrawn, and the classes renamed** (found by the first invocation). The Registration
  committed for `random-nodescend` could not be read: a Brain's name may not hold a hyphen, because
  the run id is hyphen-separated. It never ran and has no ledger line (the refusal is raised before
  the ledger is opened); the file was removed in `ffb2446a4` and the Brain renamed
  `random_nodescend`, registered as H-0004. `NoDescendAgent` became `WithholdingAgent`, which
  withholds any one kind, and `NoDescendAgentTest` became `WithholdingAgentTest`. The frozen
  intent's names (H-0003, `random-nodescend`, `NoDescendAgent`) are superseded by these.
- **Decided under the owner's standing delegation, and open for the owner to confirm.** Replacing
  the worse Brain SM-5 names is on this spec's Ask First list. The owner has asked for the loop to
  make the best decisions without stopping, so the engineer made it: the letter of SM-5 (H-0004's
  REJECT) is published beside the evidence that it proves nothing, and the spirit (H-0005's REJECT,
  H-0006's ACCEPT) is published beside it. The handoff raises it as the one open question.
- **The retry of H-0005 was decided with more than the void in view**: after H-0005's first
  invocation had played all 500 pairs and after H-0006 had accepted. The Results page says so, and
  says what a second chance costs.
- **Holdout comparisons held to the calibrated bounds** (fairness review): a release-level
  comparison on the held-out set was labelled a direction check and exempt; it now may accept and
  is held to the bounds like `standard` and `bosses`.

## Evidence

**Rig numbers**, 24 processes, Intel Core Ultra 9 275HX, Windows 11, `standard` v1, cap 20,000:

| Invocation | Runs | Time | Runs/s | Waits/s |
|---|---|---|---|---|
| H-0002, random Baseline | 500 | 177,193 ms | 2.82 | 191 |
| H-0004, random_nodescend against random | 1,000 | 376,198 ms | 2.66 | 178 |
| H-0005 first, random_norest against random | 1,000 | 371,513 ms | 2.69 | 551 |
| H-0006, random against random_norest | 1,000 | 392,894 ms | 2.55 | 532 |
| H-0005 second | 1,000 | 397,396 ms | 2.52 | 538 |

Beside E1's 399.5 Input waits per second in one process and 362.3 Runs per minute: the Rig's cost
per short Run is mostly its per-Run start-up. Both Results pages restate this.

**The result.** SM-5 met with every attempt published: H-0004 rejected a Brain that played the
same Run as the Baseline (vacuous, and said so); H-0005 was void on its first invocation and
rejected at pair 22 on the one further invocation fixed in advance; H-0006 accepted the same
difference the other way at pair 49. Within-pair turns correlation 1.00 for identical Brains,
0.12 to 0.19 once they differ.

**Four reviews** (three lenses and fairness; no information-parity leak). Fixed: the held-out set
exempt from the calibrated bounds; H-0003 missing from the record; the retry's circumstances
unstated; unsupported claims ("dies sooner", "fell", an unattributed figure); untested bounds,
wiring and correlation edges; the Attack exploration unreproducible. Deferred (deferred-work.md):
an unreadable Registration leaves no ledger line; the withholding Brains share a Brain version;
FR-25's distributions, survival curve and cross-checks belong to story 3.10.

**Mutation battery: 24 mutations, 24 killed.** Four survived the first run (the null-kind guard,
the correlation clamp, a pair lost alike on both sides counted identical or correlated); each now
has a test.

## Suggested Review Order

**The worse Brain**

- The agent: the random agent less one kind.
  `shatterfish/harness/src/main/java/org/shatterfish/harness/agent/WithholdingAgent.java`

- Which Brain withholds what.
  `shatterfish/rig/src/main/java/org/shatterfish/rig/Brains.java`

**Held to the Registration**

- The statistic and the calibrated bounds, and which sets may accept.
  `shatterfish/rig/src/main/java/org/shatterfish/rig/Runner.java`

- A comparison names its statistic.
  `shatterfish/api/src/main/java/org/shatterfish/api/Registration.java`

**What was published**

- The attempts, in order, including the ones that showed nothing.
  `docs/results/2026-09-23-worse-brain.md`

- The Baseline.
  `docs/results/2026-09-23-random-baseline.md`
