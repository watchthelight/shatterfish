---
story: 1.20
key: 1-20-snapshot-restore-and-the-reserved-interfaces
title: "Snapshot, restore, and the reserved interfaces"
epic: 1
issue: 33
type: 'feature'
status: 'in-review'
created: '2026-09-16'
updated: '2026-09-16'
review_loop_iteration: 0
baseline_commit: '904cab886'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Later epics roll out from a Run's exact state (E6) and take over from it (E5), and
nothing today can save that state and put it back. ADR-0009 decided the shape: a snapshot over
the game's own save bundles, module-private to `harness`, with `api` holding only an opaque handle
and the simulator interface, so that nothing a Brain can hold is inflatable into hidden state.

**Approach:** A `SnapshotStore` in the driver package that takes a snapshot at a wait, the game's
own save written and its files held in memory with the wait index, the salt and the log's lines,
and restores it through the game's own load into the same driver, which then reseeds at the first
wait as ADR-0007 already does. `api` gains `SnapshotHandle`, `Simulator`, `RolloutResult`,
`BeliefSample`, `BeliefSampler` and `Redeterminer`, the last three with no implementation, and
the simulator's contract refuses a handle that is not scrubbed. `RestoreReplayTest` replays the
recorded Actions from the restore and holds every Observation hash to the original; a boundary
test holds the snapshot type private and the handle empty of bytes. The rollout host and the
scrubber are E6's, named here.

## Boundaries & Constraints

**Always:** The snapshot is the game's own bundles, written by `Dungeon.saveAll` and read by
`Dungeon.loadGame` and `loadLevel`, never a second serialization. The snapshot type and its
bytes never leave `harness`; `api` carries an id, a wait index and a flag. Every claim about the
game cites `path:line` at `v4.0.0`. ADR-0009 and the architecture inventory change in this pull
request. Taking a snapshot changes nothing about the Run it is taken from.

**Ask First:** Any bundle key read by name (that is the scrubber, E6); any hook; any reflection
into upstream from main code.

**Never:** No rollout host, no scrubber, no redetermination table (E6). No search. No snapshot
written to disk except through the game's own save in the Run's profile. No `api` type that holds
game state or bytes a caller could inflate.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Take | `SnapshotStore.take(driver)` at wait `k` | A `SnapshotHandle(id, k, scrubbed=false)`; the Run continues unchanged | Outside a wait or off the UI-role thread: refused by the driver's own rules |
| Restore and replay | `restore(handle)` on the live driver, then the recorded Actions from `k` | The first wait reads as the original wait `k`, hash for hash, and every wait after to the end of the record | An unknown handle is refused by name |
| Nothing changes | The same tuple played with and without a snapshot at `k` | The same hashes at every wait | N/A |
| The contract | `Simulator.simulate(handle, actions)` with `scrubbed == false` | Refused before any rollout runs | `IllegalArgumentException` naming the handle |
| The redeterminer's contract | A `Redeterminer` whose result is not scrubbed | Refused by the interface's own wrapper | `IllegalStateException` |
| The boundary | The harness and api classes | `Snapshot` is not public and nothing outside its package depends on it; `SnapshotHandle` and `RolloutResult` carry no byte array | The rule names the class |

</frozen-after-approval>

## Code Map

Read at `v4.0.0`. Paths abbreviate `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/`
as `…/`.

- `docs/adr/0009-snapshot-restore-and-redetermination.md`, decision outcome — the split between
  `api` and `harness`, the restore contract (the load consumes draws; reseed at the first wait),
  the scrubbed rule.
- `…/Dungeon.java:624-697` — `saveGame`; `:699-704` — `saveLevel`; `:706-717` — `saveAll` (with
  `Actor.fixTime`, and nothing for a dead hero at `:707`); `:719-760` — `loadGame`: `Actor.clear`,
  the item handlers restored, `Generator.restoreFromBundle` (draws, `…/items/Generator.java:625-636`,
  `:926`); `:826-840` — `loadLevel`.
- `…/scenes/InterlevelScene.java:733-747` — `restore()`: clear held allies, wipe the log, load the
  game, load the level, `switchLevel(level, hero.pos)`; `…/Dungeon.java:464-505` — `switchLevel`.
- `…/GamesInProgress.java:57-71` — the game folder and its files, relative to the profile;
  `shatterfish/harness/.../boot/HeadlessBoot.java:170-183` — the profile directory the files live under.
- `…/journal/Journal.java:34-36` — `loadGlobal` runs once per process behind a private flag; the
  journal is not part of a snapshot, which is the limit to record.
- `shatterfish/harness/src/test/.../scene/FreshRun.java:56-100` — the parity fixture's snapshot and
  resume of a profile, the path this story moves into main code without its reflection.
- `shatterfish/harness/.../driver/HeadlessDriver.java:376-403` — `serveSceneSwitch`: end the actor
  thread, destroy the scene, do the level work, a new scene; `:463-470` — the reseed at the wait
  and `waitIndex`; `:140-160` — the fields a restore resets.
- `shatterfish/harness/.../observer/GameLogListener.java:66-107` — `reset()` and `lines()`; the
  restore needs the snapshot's lines put back after the load's own lines.
- `shatterfish/api/.../Belief.java` — the shape of an opaque value; `Decider.java` — an interface
  in `api`; `shatterfish/api/src/test/.../JsonRenderingTest.java:139-146` — `HELPERS`, which every
  new `api` class joins by name after review.
- `shatterfish/harness/.../agent/RandomAgent.java` — a seeded decider for the replay's record.

## Tasks & Acceptance

**Execution:**
- [x] `shatterfish/api/.../SnapshotHandle.java`, `RolloutResult.java`, `RolloutEnd.java`,
  `Simulator.java`, `BeliefSample.java`, `BeliefSampler.java`, `Redeterminer.java` -- the reserved
  types: an opaque handle, an abstract simulator whose `simulate` refuses an unscrubbed handle
  before its `rollout`, a result of Observations only, an opaque sample, and two interfaces with
  no implementation -- ADR-0009's `api` half.
- [x] `shatterfish/api/src/test/.../JsonRenderingTest.java` -- the new names in `HELPERS`;
  `shatterfish/api/src/test/.../ReservedInterfacesTest.java` -- the two contracts.
- [x] `shatterfish/harness/.../driver/Snapshot.java` -- package-private: the game folder's files
  in memory, the wait index, the salt, the slot, the log's lines -- the bytes that never leave.
- [x] `…/driver/SnapshotStore.java` -- `take(driver)` and `restore(handle)`; `…/driver/HeadlessDriver.java`
  -- `snapshot()` and `restore(Snapshot)`, the latter the scene switch's shape over the game's
  own load, with the wait index, the windows and the log's lines put back -- the restore.
- [x] `…/observer/GameLogListener.java` -- `restore(List<LogLine>)` -- the log put back.
- [x] `shatterfish/harness/src/test/.../driver/RestoreReplayTest.java` -- the replay, the
  unchanged Run, the unknown handle -- FR-6's test.
- [x] `shatterfish/harness/src/test/.../SnapshotBoundaryTest.java` -- the ArchUnit rule and the
  handle's components.
- [x] `docs/adr/0009-snapshot-restore-and-redetermination.md` -- amendment for story 1.20;
  `docs/architecture.md` -- the store in the inventory.

**Acceptance Criteria:**
- Given a Run snapshotted at wait `k` and played on with recorded Actions, when the snapshot is
  restored and the Actions replayed, then every Observation hash from `k` to the end matches —
  `RestoreReplayTest.a_restored_run_replays_hash_for_hash`.
- Given one tuple, when it is played with and without a snapshot, then the hashes are the same —
  `RestoreReplayTest.taking_a_snapshot_changes_nothing`.
- Given the harness and api classes, when the boundary rule runs, then the snapshot type is not
  public and nothing outside its package depends on it, and the handle carries no bytes —
  `SnapshotBoundaryTest`.
- Given an unscrubbed handle, when a simulator is asked, then it refuses before any rollout —
  `ReservedInterfacesTest.a_simulator_refuses_an_unscrubbed_handle`; likewise a redeterminer's
  unscrubbed result.
- Given ADR-0009, the rollout host and the scrubber are named as E6's in the amendment.

## Spec Change Log

- **Review, 2026-09-16 (patches, no loopback).** The matrix's "Take" row said a snapshot is
  refused "outside a wait or off the UI-role thread"; it is also refused under a window, since the
  game's save carries no window, and after an Action was handed to the game, since the save would
  stand at the wrong wait. The reviews' other findings were patches: the snapshot carries the seed
  and the hero class and a restore of another tuple is refused; the store's ids are unique across
  the process and it refuses a scrubbed claim and a wrong wait, and can drop a snapshot; the
  restore reseeds before the load, resets the chasm's jump and the action indicator, checks the
  loaded hero, and closes the Run if it fails; the log's lines are put back once the new scene has
  run, whatever halt follows; the save is shown fresh; `Redeterminer` is an abstract class with a
  final `scrub` that also refuses the live id relabelled and a moved wait; `RolloutResult` carries
  the applied count; `BeliefSample` hashes; nulls are refused by name; the tests hold a second
  restore, an earlier snapshot over a later one, the Run intact after every refusal, the chasm's
  prompt, an Action handed over, and a broken snapshot. KEEP: the files, not the bundles; restore
  into the same driver; the snapshot's lines put back.

## Design Notes

**Why the files, not the bundles.** The game writes a bundle per file and reads them back by
name; holding the folder's files in memory is one copy of exactly what the load reads, with no
second serialization and no bundle key named. The parity fixture already restores a Run this way,
on disk.

**Why restore into the same driver.** The driver owns the scene, the hooks and the generator
control; a floor change already ends the actor thread, destroys the scene and builds a new one
around loaded state. A restore is that, with the wait index set back and the log's lines put back
after the load has said its own.

**Pre-mortem.** The load's own log lines would make the first restored wait differ from the
original; they are dropped and the snapshot's lines restored at that wait. The journal is
process-global and outside the snapshot; a page found between the snapshot and the restore stays
found, which the test avoids by staying on one floor and the story records. `Actor.fixTime` at the
save shifts every time; the same shift happens in the original Run at the same wait, so both
sides agree, and the unchanged-Run test holds the save itself harmless.

## Verification

**Commands:**
- `./gradlew :harness:test -Pshatterfish.mobile=off --tests "org.shatterfish.harness.driver.RestoreReplayTest" --tests "org.shatterfish.harness.SnapshotBoundaryTest"` and `./gradlew :api:test` -- expected: green.
- `./gradlew build -Pshatterfish.mobile=off` -- expected: green; `mkdocs build --strict` green.

## Dev notes

Implemented on `story/1-20-snapshot-restore-and-the-reserved-interfaces` from `904cab886`. Seven
`api` types, two harness classes, two driver methods, one log-listener method, and three suites;
no hook spent, no upstream file touched, no reflection into upstream. The replay held on the first
run. The fairness review follows below.

## Acceptance criteria and how each was met

- **A restored Run replays hash for hash.** `RestoreReplayTest.a_restored_run_replays_hash_for_hash`:
  wait 9 restored and waits 9 to 24 replayed to the record.
- **A snapshot changes nothing.** `RestoreReplayTest.taking_a_snapshot_changes_nothing`.
- **The boundary.** `SnapshotBoundaryTest`: `Snapshot` package-private and unreachable, the store
  handing out handles only, the api half free of bytes.
- **The contracts.** `ReservedInterfacesTest`: an unscrubbed handle refused before any rollout, a
  redeterminer's unscrubbed result refused.
- **The rollout host and the scrubber are E6's.** Named in ADR-0009's amendment.

## What was built

- `api`: `SnapshotHandle`, `Simulator`, `RolloutResult`, `RolloutEnd`, `BeliefSample`,
  `BeliefSampler`, `Redeterminer`; `ReservedInterfacesTest`; the helpers list.
- `harness.driver`: `Snapshot` (package-private), `SnapshotStore`, `HeadlessDriver.snapshot()` and
  `restore(Snapshot)`; `GameLogListener.restore(lines)`.
- `RestoreReplayTest`, `SnapshotBoundaryTest`; ADR-0009's amendment; the architecture inventory;
  an idea for the journal.

## What the story found

- **The journal cannot be snapshotted.** `Journal.loadGlobal` runs once per process behind a
  private flag and nothing public resets it; the parity fixture resets it by reflection, which
  main code may not. A restore within one floor is exact; across a guide page found in between it
  is not. Recorded in the amendment and `docs/ideas.md`.
- **The load's own log lines would have broken the first restored wait.** The pane's greeting on
  Continue is a screen difference a human would see and a replay must not; the snapshot carries
  the listener's lines and the driver puts them back at the first wait after the restore.
- **`Simulator` and `Redeterminer` are abstract classes, not interfaces.** A default method on
  an interface can be overridden; a final method over a protected abstract one cannot. The
  verification-gap review found the redeterminer still an interface after the simulator's reason
  had been given, and it followed.
- **The scrubbed flag is a claim.** Any caller can build a handle that says scrubbed; the blind
  review said so. The harness that holds the bytes verifies the claim by id: the live store
  refuses a handle that claims it, and E6's host will roll out nothing for a handle it did not
  make. Written into the handle's, the simulator's and the record's text.
- **A snapshot needs a windowless wait.** The game's save carries no window, so a wait under a
  Prompt cannot be restored to; `snapshot()` refuses it by name. E5's one-snapshot-per-wait skips
  Prompt waits, a limit recorded below.

## Decisions taken inside the story

- **The files, not the bundles.** One copy of exactly what the load reads, no bundle key named,
  no second serialization; the parity fixture already restores a Run this way on disk.
- **Restore into the same driver.** A floor change already ends the actor thread, destroys the
  scene and builds a new one around loaded state; a restore is that with the wait index set back.
- **A foreign salt is refused.** A snapshot's waits draw from its Run's stream; restoring it into a
  Run with another salt would replay under another stream and could not match its record.

## Evidence

- `:api:test` green (ApiBoundaryTest, ReservedInterfacesTest, JsonRenderingTest and the rest);
  `RestoreReplayTest` 3 of 3 and `SnapshotBoundaryTest` 3 of 3 green on the first run;
  `mkdocs build --strict` green.
- The battery, `mutations120.py`, on the committed tree, restored clean after each break:

```
=== M1 the restore does not set the wait back
  -> caught by: RestoreReplayTest (the first restored wait is not the snapshot's)
=== M2 the restore drops the log: the load's own lines stand
  -> caught by: RestoreReplayTest (the restored wait's hash differs)
=== M3 the snapshot saves nothing
  -> caught by: RestoreReplayTest (the restored wait's hash differs)
=== M4 the restore loads the game but not the floor
  -> caught by: RestoreReplayTest (the restore fails)
=== M5 the snapshot type is public
  -> caught by: SnapshotBoundaryTest
=== M6 the simulator does not check the flag
  -> caught by: ReservedInterfacesTest
=== M7 the redeterminer does not check its result
  -> caught by: ReservedInterfacesTest
tree restored and clean
```

  The battery's own report also listed `SnapshotBoundaryTest` under M6 and M7: the api breaks ran
  the api module alone, and the harness results directory still held M5's failure. A reporting
  artifact of the script, not a finding; the api suite caught each. The script lives in the
  session scratchpad, as every story's has (`deferred-work.md`).

## Deviations

- None from the spec's tasks.

## Known limitations, handed forward

- **The journal**, as above; **the badges and the rankings** likewise, which nothing on the screen
  reads.
- **Snapshots live in memory** in the store that took them, and a store can drop one; the death
  gallery's write to disk is FR-26's (E5).
- **A snapshot is taken at a windowless wait only.** The game's save carries no window; a Run at
  a Prompt cannot be snapshotted, which E5's take-over at a Prompt inherits.
- **A restore after the hero's death and across a floor change are not tested here**; the first is
  E5's take-over case, the second the journal's limit above.
- **The rollout host, the scrubber and the redetermination table** are E6's (ADR-0009).

## Follow-ups for later stories

- Story 1.21 (#34): publish the E1 numbers.
- E5: one snapshot per wait for take-over; E6: the scrubber and the swap-in-place host.
