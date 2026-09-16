---
story: 1.21
key: 1-21-publish-the-e1-numbers
title: "Publish the E1 numbers"
epic: 1
issue: 34
type: 'feature'
status: 'review'
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
- [x] `shatterfish/harness/.../Launcher.java` -- `--benchmark`, `--runs`, `--samples`,
  `--siblings`, `--horizon` parsed; a nested `Benchmark` that plays, times, snapshots, plays out
  and prints the report as Markdown -- the measurement.
- [x] `shatterfish/harness/build.gradle` -- a `benchmark` `JavaExec` task with the harness's
  runtime classpath and the desktop assets -- the reproducing command.
- [x] `shatterfish/harness/src/test/.../BenchmarkSmokeTest.java` -- a tiny configuration runs and
  every field is present and finite; the flags parse -- the test.
- [x] `docs/results/e1-throughput.md`, `docs/results/index.md`, `mkdocs.yml` -- the page, its row,
  its navigation entry.
- [x] `docs/adr/0010-tactical-search-deferral-criteria.md` -- amendment: the two properties are
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

## Dev notes

Implemented on `story/1-21-publish-the-e1-numbers` from `1682a62cf`. The benchmark is a mode of
the launcher, nested there because the tactics half reads the oracle sidecar and only the
launcher may construct the oracle (`OracleGateTest`, story 1.18); a `benchmark` Gradle task on the
harness's main runtime classpath is the reproducing command. The recorded invocation ran at
`c976188c6`, after the font fix below. No hook, no upstream file, no change to the fair path.

## Acceptance criteria and how each was met

- **The page reports the rate, Runs per minute, the median Run length, and the codec's and the
  writer's costs separately**, from one recorded invocation: `docs/results/e1-throughput.md`.
- **Leaf correlation and the disambiguation factor with their definitions, and no bias**: the
  page's Tactics and Definitions sections.
- **The benchmark with a Brain named as E4's; the machine, the tag, the commit and the command
  stated; the determinism test cited**: the page's head and its SM-4 section.
- **A tiny configuration reports every field**: `BenchmarkSmokeTest`, 2 tests.

## What was built

- `Launcher`: `--benchmark`, `--runs`, `--samples`, `--siblings`, `--horizon`; the nested
  `Benchmark` with its `Report` and its Markdown.
- `shatterfish/harness/build.gradle`: the `benchmark` task.
- `BenchmarkSmokeTest`; `docs/results/e1-throughput.md`; the results index row; the navigation;
  ADR-0010's amendment.
- `HeadlessGraphics` and its line in `HeadlessBoot`, `HeadlessTextTest`, ADR-0015's amendment:
  the fix for what the first benchmark run found (below).

## What the story found

- **Text never rendered headlessly, and every render logged a stack trace.** The first run's
  report carried thirty thousand FreeType refusals: libGDX's mock graphics report a zero-wide
  back buffer, the game scales every text block's font by it (`DeviceCompat.java:64-71`,
  `PixelScene.java:337-339`), so every block asked for size zero. Silent since the headless scene,
  under the test output. `HeadlessGraphics` reports the boot's display; the rate with the traces
  was 371.2 waits per second, and the published rate is from the fixed commit.
- **The tactics half had to live in the launcher.** The disambiguation factor counts hidden facts,
  which are the oracle's; the gate of story 1.18 admits no other constructor, and a nested class
  belongs to the launcher under ArchUnit's rule, so the gate holds unchanged.
- **The JSON is not larger than the canonical bytes**, contrary to the first smoke assertion; the
  page reports both sizes rather than assuming their order.
- **Mobs cannot be hidden facts of a disambiguation factor.** A mob's position is hidden and shown
  again as it moves, so its "revelation" would never settle; the factor counts the static facts
  and the page reports mobs first drawn per wait beside it.
- **The random agent reveals nothing in twenty waits, and its siblings agree by chance.** The
  factor read 0.000 and the leaf correlation 0.877 against a chance agreement of 0.876
  (kappa 0.010.); the page says why neither is on Long et al.'s scale, and ADR-0010's amendment
  records the two definitional changes E6 makes before its readings are compared.
- **The oracle had a second switch.** The tactics half read the sidecar without `--oracle` and
  printed no mark; the review made it the same switch, marked every line, and named the two
  classes in the gate (ADR-0006's amendment).
- **Four reviews, forty findings.** The fairness reviewer (six), the adversarial lens (twenty-six),
  the edge-case hunter (twenty-one) and the verification-gap lens (ten) overlapped on the median,
  the early-ended playouts, the chance agreement, the unmarked oracle and the uncaught stall; the
  patch is one commit and the Review section lists what was and was not taken.

## Decisions taken inside the story

- **Alive at the horizon as the payoff.** A tactical search scores a short horizon; a binary
  payoff there is what leaf correlation compares.
- **Tuples as a function of the seed base.** The benchmark's salts are the seed's, so the command
  replays the same Runs; the rates are the machine's, the properties the game's.
- **Playouts never descend.** The floor below would read the process's journal and the tactical
  horizon is short.
- **The font fix is this story's.** The alternative was publishing a rate with thirty thousand
  stack traces in it and an issue; the fix is one harness class, no hook, and the page is the
  deliverable.
- **A refusal re-serves the wait, as in the Run loop.** On the approach to a sample and inside a
  playout, a refused Action is retried at the same wait up to twenty times; twenty refusals cut
  the playout, which is dropped rather than scored.
- **Three invocations, the first published.** The report on the page is the first run's; the
  three wait rates are given beside it as the spread, since one number of unknown variance is a
  poor foundation for E3's sizes.

## Evidence

- `BenchmarkSmokeTest` green, 4 tests, with the tiny configuration's outcomes pinned and played
  twice; `HeadlessTextTest` green, 2 tests, the application logger captured through a Run's first
  waits; `OracleGateTest` green, 6 tests, naming the two classes; `ThreadConfinementTest` and
  `MonitorConfinementTest` green with the nested benchmark in the launcher; `HeadlessBootTest`,
  `SceneDrawParityTest`, `HeadlessSceneTest`, `FenceInvariantTest`, `ProfileTest` green with the
  display reported, and no `[GAME]` error in any of their outputs.
- The recorded invocation: `./gradlew :harness:benchmark -Pshatterfish.mobile=off` at
  `c976188c6`, three times; the first report is the page, the three rates are on it. The same command before the font fix, at `20ee7cc30`,
  played the same 13337 waits and the same 23 samples with the same payoffs, at 371.2 waits per
  second: text rendering is not in the game, and the traces cost an eighth of every wait.
- Mutation battery, five mutations of the launcher and the graphics, each run against
  `BenchmarkSmokeTest` and `HeadlessTextTest`:
    - M1 the report drops its tactics section: caught by `BenchmarkSmokeTest`.
    - M2 a skipped sample is not counted: **survived** before the review. The tiny configuration's
      two samples land on windowless waits, so nothing is skipped; the review's smoke test pins the
      sample lines and the skip counts by reason, and the battery was rerun after the patch (below).
    - M3 a count of zero is accepted: caught by `BenchmarkSmokeTest`.
    - M4 the cost Run never steps: caught by `BenchmarkSmokeTest`.
    - M5 the back buffer is zero wide again: caught by `HeadlessTextTest`, both tests.

## Deviations

- The font fix, `HeadlessGraphics` and `HeadlessTextTest`, was not in the spec's tasks; the
  benchmark found it and the page would otherwise have measured it.

## Known limitations, handed forward

- **The rates are one machine's, one process's**; the cross-platform reading is story 3.4's.
- **The horizon is twenty waits**, and the properties are taken at it rather than per move; E6
  reruns the method from mid-fight snapshots with the simulator it builds.
- **A sample under a window is skipped**; the page counts them.
- **Both properties are the random agent's.** A Brain moves the disambiguation factor off its
  floor; E6 reads it again, with the denominator and the death count changed as ADR-0010's
  amendment says.
- **The journal is a process's.** The 225 Runs of one invocation share the journal the process
  loaded first (story 1.20's limit); the pins and the determinism test hold the outcomes within
  a process, and the per-process reading is story 3.4's.

## Follow-ups for later stories

- E3: the seed-set sizes from the rate; the paired-seed variance (SM-5).
- E4: the benchmark with a Brain attached.
- E6: simulator speed, the search leak test, and the two properties from mid-fight snapshots.
