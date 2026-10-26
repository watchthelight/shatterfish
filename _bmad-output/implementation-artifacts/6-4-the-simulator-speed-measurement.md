---
title: 'Story 6.4: The simulator-speed measurement'
type: 'feature'
created: '2026-10-26'
status: 'review'
baseline_commit: 'a8aa3589d8ac3a9b857ead0360e955c2e686b832'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent, do not modify unless human renegotiates">

## Intent

**Problem:** ADR-0010 defers the choice of a tactical search to three measurements. The first asks how many redetermined
rollouts a second the swap-in-place host of ADR-0009 can run. The host (story 6.2) exists and has never been timed.

**Approach:** a measurement that times the five costs of a rollout apart on the `smoke` set's mid-fight snapshots, three runs
of it, and a Results page that states the rate, the dominant cost and what fits a per-Decision budget, without comparing
anything to a target chosen in advance (NFR-3).

## Acceptance Criteria (from epics.md Story 6.4, with the tests that hold them)

1. The measurement reports, separately, the cost of taking a snapshot, of scrubbing it, of loading the scrubbed copy, of
   rolling out 2, 3 and 4 Actions and of restoring the live Run, each as a median and a 95th percentile, and the redetermined
   rollouts per second per process they imply. `SimulatorSpeedSmokeTest` (the five rows, the counts, the percentile
   definitions).
2. The page states the machine, the JDK, the commit, the command and the number of snapshots; the command is run three times;
   the load on the machine during each run is stated. The reports are attached under `docs/results/logs/`.
3. If the classloader-isolation spike cannot be completed in this story, the page says why in one sentence citing ADR-0009's
   JNI note.
4. The page says which of the five costs dominates and the rate if it were zero; it states how many determinizations times
   rollouts fit the per-Decision budgets, and that the answer is none where it is; the numbers are not compared to a target.
5. `SimulatorSpeedSmokeTest` runs a tiny configuration and pins structure, not timings; goo and holdout are refused
   (`SimulatorSpeedSmokeTest`, `goo_and_holdout_are_refused`). The page is indexed, the nav lists it, and the docs build
   passes. No Brain changes, so the pull request carries no Rig comparison.

</frozen-after-approval>

## Code Map

- `shatterfish/harness/src/main/java/org/shatterfish/harness/agent/RolloutHost.java`: the only production change. A
  `Phases` listener (`timedBy`) hears four durations: snapshot (the live take, statics and profile held), load (restore of
  the copy and the step to its wait), rollout (the Actions) and restore. Null by default; with no listener the host does one
  extra branch per phase and nothing else. The first `stepToInputWait` of the old `play` moved to the caller so that the load
  cost ends where the first wait is reached; the order of every call is unchanged.
- `shatterfish/harness/src/test/java/org/shatterfish/harness/agent/SimulatorSpeed.java`: the measurement and its Markdown.
  Test source because it plays the real Brain (`RealBrain`), a test dependency of the module.
- `shatterfish/harness/build.gradle`: `:harness:simulatorSpeed` (`-Psimulator.args`), on the test runtime classpath.
- `docs/results/2026-10-26-simulator-speed.md`, `docs/results/logs/2026-10-26-simulator-speed-run{1,2,3}.txt`,
  `docs/results/index.md`, `mkdocs.yml`, ADR-0010's story 6.4 amendment.

## Decisions

- **The line of play.** A rollout needs Actions. They are the real Brain's: its choice at the snapshot's wait, then at each
  wait the copy reaches, planned untimed on the same copy (a rollout from one copy is deterministic, since the load reseeds
  from the sample's salt), so the timed rollout plays the line the planner chose. A planner is a fresh `BrainDecider`, kept
  apart from the Brain that plays the live Run, so planning never touches the Run's Brain memory. Alternatives rejected: a
  fixed list of waits (cheap, not a fight), and the live Run's next Actions (valid in the live world, often refused in a
  scrubbed one).
- **Horizons are Actions, not game turns**, 2, 3 and 4, FR-34's range. An Action may take several turns.
- **Five costs, not seven.** The scrub's own input snapshot is the same operation as the host's snapshot, so the snapshot row is
  the host's. The scrub is timed alone, once per sample, and shared by the three horizons; the rate is given with a scrub per
  rollout (the worst case), a scrub per eight rollouts, at the 95th percentile, and measured end to end.
- **Medians and nearest-rank percentile**, because timings of a shared machine are skewed. The rate from medians is the
  headline, and the end-to-end measured rate is printed beside it as a check.
- **Load is reported, not controlled.** The machine is shared, so every run states the Gradle and Java process counts, the
  processor load sampled while it ran and the system load the process itself read at each snapshot. Run 3 happened to overlap
  other agents' builds and is kept and labelled, not replaced.
- **Isolation is not attempted.** ADR-0009's JNI note (the JNI specification forbids loading one native library into more
  than one class loader) means an isolated instance needs the shared-parent-loader split first; that is the E1 spike and is
  its own story. The page says so in a sentence.
- **Budgets.** All six committed Registrations carry `budget_ms` 0 and the Overlay has no budget written down, so the page
  gives a grid from 10 ms to 5 s and picks none.
- **Fairness.** The host's change adds timing only and hands the listener durations. The measurement reads what the scrub
  hands a search (the Observation of the live wait for the Brain's choice, the scrubbed copy's Observations for planning). It
  uses `smoke`, and `goo` and `holdout` are refused by name.

## Pre-mortem

| Cause | Guard |
|---|---|
| The timing hook changed the host's behaviour | the old `RolloutHost` tests (`RestoreReplayAfterRolloutTest`, `RedeterminationReReviewTest`) run unchanged and must stay green |
| A page of numbers from one lucky run | three runs, load stated for each, the busy run kept |
| Warm-up in the first samples | four discarded warm-up samples |
| A mean hides a long tail | median and 95th percentile only |
| The page implies a pass or a fail | no target; the budget grid picks none |

## Verification

`./gradlew :harness:test :codex:test :rig:test` and the strict docs build; `fairness-reviewer` before the pull request.
