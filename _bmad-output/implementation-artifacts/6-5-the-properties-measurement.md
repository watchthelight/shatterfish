---
title: 'Story 6.5: The properties measurement'
type: 'feature'
created: '2026-10-26'
status: 'review'
baseline_commit: '099003a40'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent, do not modify unless human renegotiates">

## Intent

**Problem:** ADR-0010's choice rule 4 says Long et al.'s two transferable properties are reported beside every search
candidate. Story 1.21 measured them once, with the random agent from floor-one waits, and the readings could not be placed on
the scale (kappa 0.010, disambiguation 0.000): survival there was the rate of a random agent at a random wait, and the
denominator was the Run's whole set of unknown appearances. The amendment named two changes and a method for the rerun.

**Approach:** a measurement that plays random playouts from the `smoke` set's mid-fight snapshots, drawn as story 6.4 draws
them, driven in turn by the random agent, the baseline Brain and the one-ply model, with the amendment's two changes, and
publishes both numbers with confidence intervals for each driving Brain, an explanatory prediction in the sense of Long et al.,
and no choice.

## Acceptance Criteria (from epics.md Story 6.5, with the tests that hold them)

1. The method is the amendment's: random playouts from snapshots, the disambiguation denominator the facts reachable on the
   floor of the sample, and the count taken at a death as well as at the horizon; the three drivers; the snapshots the `smoke`
   set's mid-fight snapshots drawn with 6.4's code. `PropertiesDefinitionTest.the_denominator_is_the_floor`,
   `PropertiesDefinitionTest.a_death_counts_at_the_death`, `OracleFloorIdentitiesTest` (what "reachable on the floor" means for an
   item), `PropertiesSmokeTest` (three drivers, the same snapshot and siblings, both horizons, the live Run put back).
2. `PropertiesDefinitionTest` pins the two definitions on hand-built cases with known answers: a toy snapshot where every
   sibling agrees (`every_sibling_agrees`, `agreement_counts_pairs`), one where the revealed share is known
   (`the_revealed_share_is_known`), and the intervals (`the_interval_is_a_function_of_the_samples`).
3. Bias is not measured and the page says why in one sentence, citing ADR-0010. The hidden facts are counted through the
   oracle sidecar from the launcher under `--oracle`: `Launcher.hiddenFacts` is the one door, it refuses without the flag, the
   page shows the flag, and no oracle code runs in a ranked Rig run or in any search (`PropertiesOracleGateTest`,
   `RigOracleGateTest.the_rig_cannot_reach_an_oracle`, `OverlayOracleGateTest.not_the_harness_launcher`,
   `OracleGateTest`).
4. The page reports the two numbers with 95% intervals for each driving Brain, says what each predicts for sampled search in
   the sense of Long et al., labels that prediction explanatory, and links the speed page. ADR-0010's amendment records what was
   measured and that no choice was made from it. The page is indexed and in the nav, and the strict docs build passes.

</frozen-after-approval>

## Code Map

- `shatterfish/harness/src/main/java/org/shatterfish/harness/Launcher.java`: `hiddenFacts(Launch)` (the door; refuses without
  `--oracle`) and `facts(OracleView)` (one key per fact: `identity` for an unknown appearance an item of this floor shows,
  `elsewhere` for the Run's other appearances, `door`, `trap`, `mimic`). Strings out; the caller holds no sidecar type.
- `shatterfish/harness/src/main/java/org/shatterfish/harness/observer/OracleObserver.java`, `OracleView.java`: `view()` reads the
  sidecar without the fair read (needed after a death, where the fair Observer has nothing to observe), and `floorIdentities`:
  the unknown appearances shown by an unidentified potion, scroll or ring in the pack or in a heap of the floor
  (`Belongings.java:73`, `Level.java:184`, `Heap.java:52`). The old six-argument `OracleView` constructor stays.
- `shatterfish/harness/src/test/java/org/shatterfish/harness/agent/Properties.java`: the definitions, the cluster bootstrap and the
  tables. No game.
- `.../agent/PropertiesMeasurement.java`: the snapshots, the playouts, the page. Test source because it plays the real Brain.
  `SimulatorSpeed` supplies the seed entries, the "enemy drawn" rule and (by copying the loop) the gap and salts.
- `shatterfish/harness/build.gradle`: `:harness:propertiesMeasurement` (`-Pproperties.args`), which passes `--oracle`.
- Tests: `PropertiesDefinitionTest`, `PropertiesSmokeTest`, `PropertiesOracleGateTest`, `observer/OracleFloorIdentitiesTest`;
  one rule each added to `RigOracleGateTest` and `OverlayOracleGateTest`.
- `docs/results/2026-10-26-6-5-the-properties-measurement.md`, `docs/results/logs/`, `docs/results/index.md`, `mkdocs.yml`,
  ADR-0010's story 6.5 amendment.

## Decisions

- **Where the code lives.** A Brain-driven playout needs the Brain, which only test sources can see (`BrainBoundaryTest`: harness
  main does not depend on `brain`). The oracle door must be the launcher's. So the measurement is a test-source tool that asks
  `Launcher.hiddenFacts`, a plain-string door that refuses without `--oracle`; the task passes the flag, the measurement refuses
  to start without it, and the page prints it. Rejected: building the oracle in the measurement (breaks "the launcher is the one
  place that constructs it"), and moving the Brain into harness main (a boundary change far past this story).
- **Facts reachable on the floor.** The identities of the Run are every unknown potion, scroll and ring class (about 30). Only
  those an unidentified item of this floor shows, in the pack or in a heap, can be revealed by the hero in a playout, so those
  are the denominator's identities; with the floor's secret doors, hidden traps and hidden mimics they are the floor's facts. Not
  counted: items carried by mobs, and contents of chests not yet opened by the mimic rule (heap contents are counted). The Run's
  other appearances are kept apart as `elsewhere`, so the page can show story 1.21's denominator and the two changes one at a time.
- **A death counts where it happens.** The hidden set is read at the wait the hero dies at (`OracleObserver.view()` needs no fair
  Observation) and every leaf counts; the old survivors-only count stays as a column.
- **Payoff, horizons, siblings.** The payoff is alive or dead, as in story 1.21 and the amendment. Each leaf is read at 4 waits (the
  tactical horizon of ADR-0010, FR-34) and 20 waits (story 1.21's), from one playout. Up to four distinct valid Actions, never a
  descent, are played from each snapshot and the same four for each driver, so the drivers are paired. A second payoff, alive with at
  least half the hero's health, is reported beside the first: a Brain that rarely dies leaves the first with nothing to disagree
  about (the first full run showed 14 deaths in 526 baseline leaves), and the page says that it is an addition to the amendment.
- **A playout ends alive when the driver chooses the stairs or the game switches scene (a chasm).** The floor's events are over and
  the hero lives; the hidden set is read just before. Rejected: dropping those (it would drop exactly the Brain's flight, and bias
  the survival of the drivers that flee).
- **Drivers.** The random agent per playout from a derived seed; the baseline Brain and the one-ply model each a fresh
  `BrainDecider` rewound to the Belief the live baseline Brain held at the snapshot (`Rewindable`), so they are not cold. A driver
  is handed an Observation and nothing else. A playout that cannot be read to an end (a refused sibling, a stalled Action, a driver that
  cannot decide, refusals that stay) is dropped, counted by reason, and the page says how many.
- **Intervals.** 95% percentile intervals of a cluster bootstrap over seeds (the snapshots of one seed share a Run), 2000
  resamples, fixed seed; a statistic undefined in a resample (kappa when everyone lives) is skipped and the page says when none is
  defined.
- **Repeat and a second slice.** The tuple fixes the measurement, so a rerun must reproduce the table byte for byte (kept under
  `docs/results/logs/`); the first 25 seeds of the `standard` set are a second slice, as E4's lesson asks. Neither is gated on.
- **A restore that differs.** On one `standard` seed (648322377831, wait 164) the restored Run came back with a different map
  Observation after the random-agent playouts; the cause is unknown. A playout whose restore differs is dropped ("restore
  differs"), and if the live Run does not return to the snapshot's wait the seed ends there with its recorded snapshots kept
  (`Unrestored`). `smoke` never hits it. Rejected: hiding it (the page states it) and chasing it here (a harness question).
- **Results.** The page, the logs (`smoke` twice, byte-identical; `standard` once) and ADR-0010's story 6.5 amendment hold them.
- **No choice.** Nothing is compared to a threshold and nothing is chosen (ADR-0010 rule 4).

## Pre-mortem

| Cause | Guard |
|---|---|
| The oracle reaches a Run or a search | one door, refuses without the flag; ArchUnit over the harness (main and test), the Rig and the Overlay; the measurement refuses to start without `--oracle` |
| A driver sees a hidden fact | a driver is given an Observation only; facts are strings read after the fact and compared by the measurement |
| The page reads a kappa the survival makes meaningless | the second payoff, the chance column, and "n/a" printed where kappa is 0/0 |
| The live Run is left in a different state | the restore is checked against the snapshot's Observation hash after every snapshot, and the measurement throws if it differs |
| A reading presented as a verdict | the page labels the prediction explanatory and picks nothing |

## Verification

`./gradlew :harness:test :codex:test :rig:test` and the strict docs build; `fairness-reviewer` before the pull request.
