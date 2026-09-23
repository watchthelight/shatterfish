# Epic 3 Context: Rig

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Make every claim about a Brain measurable, publishable, and reproducible by a stranger who has
only the repository and a machine. The Rig turns the Harness into an instrument: thousands of
seeded Runs across parallel processes, paired comparison of two Brains under a sequential test
whose error rates were calibrated on this project's own outcome distribution rather than assumed,
hash-chained Run logs anyone can re-run and verify, and Results pages that carry the tag, the
commits, the seed set version, the statistical trace, and the command that reproduces them.
Registration precedes measurement, so a result cannot be chosen after the fact. From this epic
onward no Brain change merges without Rig numbers, which makes E3 the gate every later epic
depends on: E4's Goo rung, E6's search accept-or-close decision, and E7's win rate are all
verdicts this machinery issues.

## Stories

- Story 3.1: Seed sets as committed, versioned files
- Story 3.2: Run logs with a hash chain
- Story 3.3: The parallel runner
- Story 3.4: Replay with verification
- Story 3.5: Registration and the salt discipline
- Story 3.6: The per-pair statistic and the sequential test
- Story 3.7: Calibrate the bounds
- Story 3.8: The e-process alternative
- Story 3.9: The baseline and the deliberately worse Brain
- Story 3.10: Results pages and the methodology page
- Story 3.11: The nightly job and the results pull request
- Story 3.12: The death gallery

## Requirements & Constraints

- **A published number is a Registration plus a Run log.** A comparison runs only under a
  Registration committed before its first Run, carrying the hypothesis id, both Brains with
  commits and configuration hashes, the seed set and its version, the bounds, burn-in, maximum,
  budget and machine class. The runner refuses to start without one.
- **Salts are not pre-registered.** The runner draws each pair's salt at execution and writes it
  to both Run logs, so a Brain's author cannot precompute the random stream. A Run's identity is
  (tag, class, challenges, seed, salt, action list).
- **Holdout discipline.** `holdout` is never run during development; only a release-level or
  headline claim may use it, at most once per Brain version, and every use is recorded. One
  comparison ledger records every Registration, its outcome, and every holdout use, so the number
  of prior attempts behind a published claim is public.
- **Oracle mode cannot reach a ranked Run.** The runner refuses any Run whose log header carries
  the oracle flag; oracle is not reachable through the Rig command line at all.
- **Everything published is reproducible and readable.** Run logs, Results pages and strategy logs
  are plain text (JSONL or Markdown) legible without tooling. A Results page names the tag, both
  commits, the seed set version, the outcome with its log-likelihood trace, per-Run distributions,
  survival curve, measured pair correlation, fairness-suite status, and the reproducing command.
- **Negatives and undecided results publish on the same terms as positives.**
- **Throughput is a budget, not a promise.** No rate is claimed before it is measured; the Rig
  meets its cost target by choosing parallelism and seed-set sizes, not by assuming an engine
  rate. The `smoke` direction check must fit a working session; the `standard` acceptance run
  must fit overnight on the development laptop.
- **Smoke is sized for direction, never acceptance,** and every Results page says which it was.
- **Cross-platform reproducibility** is verified by a nightly job that replays a published Run on
  Windows and Linux and compares hash chains. No runtime network calls beyond GitHub's own API.
- **Epic is done when** a random-agent Baseline is published and a deliberately worse Brain is
  rejected by the sequential test.

## Technical Decisions

- **Module placement.** All of this lives in `rig`, which may depend on `harness` and `brain` and
  nothing else of Shatterfish's. The `brain` wall is unaffected by anything here.
- **One process hosts one Run**, each with its own Profile directory and working directory. A
  crashed or hung Run is recorded as incomplete with its partial log kept, never silently lost.
- **Run id** is `<tag>-<class>-<challenges>-<seedcode>-<salt>-<brain>`, so the two Runs of a pair
  never collide in one log file.
- **Ownership.** The Rig owns the Registration, the salts and the Results; the driver owns the
  wait index, the reseed, the Profile and the Run log file. Nothing else writes those.
- **Everything crossing a module edge is an `api` record with a canonical codec and a schema
  version** — the Run-log records, the `Registration`, the seed set. No floats in hashed data;
  timestamps appear only in Results metadata, never in the hash chain. The chain excludes the
  timing field so it recomputes from the file alone.
- **Statistics are hand-ported into `rig`, no library.** The sequential test is a per-pair GSPRT
  with an e-process kept as the calibrated alternative; both stay in the tree behind a flag so
  the comparison can be re-run on a later tag. Bounds, burn-in and maximum are deliberately left
  unset by the architecture and are decided by this epic's calibration story.
- **Seed-set sizes are provisional.** `smoke` 25, `standard` 500, `holdout` 500, `bosses` 100,
  `goo` 400 Warrior triples; revisable by ADR once throughput is measured. Files are versioned by
  content and a Results page names the version.
- **Pairing** is on the (seed, class, flags) triple with a shared salt; the composite outcome
  ranks Win, Score and bosses killed above depth, and a missing Run scores as a tie counted
  separately.
- **CI shape** is a per-PR gate plus a nightly `smoke` run that updates a single results pull
  request; where `standard` runs (laptop vs Actions) is an open question this epic resolves by
  measurement.

## Cross-Story Dependencies

- Story 3.2 (Run logs) underpins 3.4, 3.9, 3.10 and 3.12; story 3.1 (seed sets) underpins every
  comparison story.
- Story 3.7's calibration declares the acceptable margin between realized and nominal error;
  story 3.8 tests against that declared number and may replace the statistic on the strength of it.
  Neither 3.9 nor 3.10 can publish an acceptance before the statistic is settled.
- Story 3.5's standing Registration is what story 3.11's nightly job runs under.
- **From E1:** the Observation, the executor, the random agent, the salt and mix function, and the
  Profile all pre-exist; story 3.4 inherits the cross-platform comparison that E1's determinism
  story explicitly deferred here. Story 3.9 restates E1's throughput numbers beside the Rig's own
  cost per comparison.
- **From E2:** the Codex version is recorded in the Run-log header and the Registration.
- **To E4:** every E4 story's pull request carries Rig numbers, so the runner, the pair statistic
  and the smoke direction check must all work before E4 opens. Story 3.3 owns the Rig half of
  oracle gating that E1 named. Story 3.12's per-Brain comparison view is explicitly deferred to E4.
