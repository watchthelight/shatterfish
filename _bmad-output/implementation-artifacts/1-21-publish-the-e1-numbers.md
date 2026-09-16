---
story: 1.21
key: 1-21-publish-the-e1-numbers
title: "Publish the E1 numbers"
epic: 1
issue: 34
type: 'feature'
status: 'in-progress'
created: '2026-09-16'
updated: '2026-09-16'
review_loop_iteration: 0
baseline_commit: '1682a62cf'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The roadmap rests on a guess about how fast the harness plays and on an unmeasured
inference about the game's tactics (research §4, low confidence). NFR-3 says no rate is promised
before it is measured, and SM-4 says E1 ends when the rate and the two tactical properties are
published beside a green determinism test.

**Approach:** A benchmark mode of the harness launcher that plays the random agent for a batch
of Runs and reports Input waits per second per process, Runs per minute and the median Run
length; measures the codec's and the JSON writer's cost per Observation separately; and, from
snapshots taken along random Runs, measures Long et al.'s two transferable properties with random
playouts to a horizon: leaf correlation as sibling agreement on the payoff, and the disambiguation
factor as the share of the oracle's hidden facts revealed by the horizon. The page
`docs/results/e1-throughput.md` states the machine, the tag, the commit and the reproducing
command, does not report bias, names the benchmark with a Brain as E4's, and cites the
determinism test.

## Boundaries & Constraints

**Always:** The measurement of hidden facts goes through `OracleObserver`, which only the launcher
may construct, so the benchmark's tactics half lives in the launcher's own class; the fair path,
the ports and `api` are untouched. Every number on the page comes from one recorded invocation
whose command, commit and tag the page states. Every claim about the game cites `path:line` at
`v4.0.0`. The results index, the navigation and ADR-0010 change in this pull request.

**Ask First:** Any change to the random agent's choices, the run loop's caps or the ports; any
number on the page not produced by the recorded invocation.

**Never:** No bias figure (a two-player quantity); no rate promised beyond what was measured on
the described machine; no Brain (E4); no rollout host (E6); no hook.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Throughput | `--benchmark` with `--runs N` | N Runs to their end in one process; waits per second, Runs per minute, median turns and waits, the cause tally | A Run that ends by a refusal or an unserved scene is counted by its cause |
| Costs | The same invocation | Microseconds per Observation for `observe()`, for the codec (encode and hash) and for the JSON writer, measured separately over one Run's waits | N/A |
| Tactics | `--samples S --siblings K --horizon H` | From S snapshots along random Runs: leaf correlation as the mean pairwise agreement of K siblings' payoffs (alive at the horizon) and the disambiguation factor as the mean share of hidden facts revealed by H | A sample under a window is skipped and counted |
| The page | The printed report | `docs/results/e1-throughput.md` with the machine, tag, commit, command, the numbers, the definitions, the deferrals and the determinism test | N/A |
| The smoke | A tiny configuration in a test | The report carries every field and every number is finite | N/A |

</frozen-after-approval>

## Code Map

- `shatterfish/harness/.../Launcher.java` — the command line and the one class allowed to
  construct `OracleObserver` (`OracleGateTest`, story 1.18); `Launch.parse`.
- `shatterfish/harness/.../agent/RunLoop.java:82-160` — `play(seed, class, salt, agent, cap)`,
  `RunOutcome` (cause, depth, turns, waits, applied, refused); `RandomAgent(seed)`.
- `shatterfish/harness/.../driver/SnapshotStore.java`, `HeadlessDriver.snapshot()/restore()` —
  the playouts' branching point (story 1.20); a snapshot needs a windowless wait.
- `shatterfish/harness/.../observer/OracleObserver.java` — `OracleView`: identities, mobs with
  `seen`, hidden mimics, secret doors, hidden traps: the hidden facts the disambiguation counts.
- `shatterfish/api/.../ObservationCodec.java` — `encode`; `Observation.hash()`, `json()`.
- `shatterfish/harness/build.gradle` — where the desktop assets join a classpath; the benchmark
  task mirrors it.
- `docs/results/index.md`, `docs/results/e1-touchpoint-audit.md`, `mkdocs.yml:143-144` — the
  results pages' shape and the navigation.
- `docs/adr/0010-tactical-search-deferral-criteria.md:50-78` — the measurements and what they
  decide; research §4 — Long et al.'s definitions and calibration points.
- `_bmad-output/planning-artifacts/prds/.../prd.md:523`, `:541` — SM-4 and NFR-3.

## Tasks & Acceptance

**Execution:**
- [ ] `shatterfish/harness/.../Launcher.java` -- `--benchmark`, `--runs`, `--samples`,
  `--siblings`, `--horizon` parsed; a nested `Benchmark` that plays, times, snapshots, plays out
  and prints the report as Markdown -- the measurement.
- [ ] `shatterfish/harness/build.gradle` -- a `benchmark` `JavaExec` task with the harness's
  runtime classpath and the desktop assets -- the reproducing command.
- [ ] `shatterfish/harness/src/test/.../BenchmarkSmokeTest.java` -- a tiny configuration runs and
  every field is present and finite; the flags parse -- the test.
- [ ] `docs/results/e1-throughput.md`, `docs/results/index.md`, `mkdocs.yml` -- the page, its row,
  its navigation entry.
- [ ] `docs/adr/0010-tactical-search-deferral-criteria.md` -- amendment: the two properties are
  measured, where, and what they say; the simulator speed and the search leak test stay E6's.

**Acceptance Criteria:**
- Given the benchmark, when it runs on the described machine, then the page reports waits per
  second per process, Runs per minute, the median Run length, and the codec's and the writer's
  costs separately, from one recorded invocation.
- Given the samples, when the playouts run, then the page reports leaf correlation and the
  disambiguation factor with their definitions, and no bias.
- Given the page, then it names the benchmark with a Brain as E4's, states the machine, the tag,
  the commit and the command, and cites `DeterminismTwoJvmTest` as green — SM-4.
- Given a tiny configuration, when `BenchmarkSmokeTest` runs, then the report carries every field
  and every number is finite.

## Spec Change Log

## Design Notes

**Why the launcher.** The hidden facts a disambiguation factor counts are the oracle's, and the
gate of story 1.18 lets only the launcher construct an oracle; a benchmark that measured them
elsewhere would need the gate widened. The throughput half could live anywhere; it lives beside
the other so one command produces one page.

**Why alive-at-horizon as the payoff.** Long et al. use terminal payoffs; a roguelike Run is long
and a tactical horizon is short (research §4, two to four hero turns for search). A binary payoff
at a horizon of H waits is what a tactical search would score, and it is the same for every
sibling of a sample, which is what leaf correlation compares.

**Pre-mortem.** Playouts within one process share the journal, so a page found in one playout is
found in the next; on floor one the effect is nil and the page says so. A sample under a window
cannot be snapshotted and is skipped, counted, and reported.

## Verification

**Commands:**
- `./gradlew :harness:benchmark -Pshatterfish.mobile=off` -- expected: a Markdown report on stdout.
- `./gradlew :harness:test -Pshatterfish.mobile=off --tests "org.shatterfish.harness.BenchmarkSmokeTest"` -- expected: green.
- `./gradlew build -Pshatterfish.mobile=off` -- expected: green; `mkdocs build --strict` green.
