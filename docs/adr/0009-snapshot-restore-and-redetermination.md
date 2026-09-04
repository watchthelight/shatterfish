---
status: proposed
date: 2026-09-04
deciders: watchthelight (product owner), Claude (engineer)
---

# ADR-0009: Snapshot, restore, and redetermination

## Context and problem statement

The Harness must be able to snapshot a Run, restore it, and produce a *redetermined* copy in
which every hidden element is re-sampled from a Belief (PRD FR-6, deferred to E6 with the
interface reserved in E1). Redetermination is what makes engine rollouts fair (non-negotiable #1;
`docs/fairness.md`, "Search"): a rollout on the raw saved game would see unidentified item
classes, unseen mob positions, unexplored layout, hidden traps and the generator state.

Session 10 read the save path: a save is a gzip `Bundle` of `org.json` objects written by
`Dungeon.saveGame` and `saveLevel` (`…/Dungeon.java:624-704`), written through the static
`Bundle.write(Bundle, OutputStream)` (`SPD-classes/…/utils/Bundle.java:535`); loading is `loadGame`
plus `loadLevel` plus `switchLevel` (`…/Dungeon.java:723-840`; `…/scenes/InterlevelScene.java:733-747`);
no random state is saved; `saveAll` runs `Actor.fixTime` first (`…/actors/Actor.java:170-192`); and
`switchLevel` clears `hero.curAction` (`…/Dungeon.java:508`). Game state is static singletons, so
one process holds one world at a time.

The session 12 reviewer gate found three defects in the first draft of this decision, all
fairness-blocking, and they shape the outcome below: a `Snapshot` of the game's own bundle bytes
placed in `api` is readable by anything holding it, including the Brain, with nothing but the JDK;
the scrub table omitted `Dungeon.seed` and assumed a rollout could not generate a floor; and it
scrubbed only mobs *outside* the field of view, leaving the true AI state and exact hit points of
every visible mob inside a rollout.

Non-negotiables touched: #1 (parity of rollouts), #3 (hooks), #4 (in-process), #5 (a restored
snapshot must replay identically).

## Decision drivers

- Nothing a Brain can hold may be inflatable into hidden state.
- Restore-and-replay must reproduce the original Observation hashes from the snapshot's Input
  wait onward.
- The scrubbed snapshot's Observation must be byte-identical to the original's (the differential
  test, FR-9), which is the definition of "hidden": anything the scrub may change.
- Zero or one hook; the game's own save and load paths already exist.
- Cheap enough to take at every Input wait in the Overlay and to restore many times per Decision
  in E6.

## Considered options

**Where a Snapshot lives**

1. A `Snapshot` record in `api` carrying the bundle bytes. **Rejected by the red team**: the bytes
   are a gzip `Bundle`, so any holder can inflate them with `java.util.zip` and read every
   `__className`, every unseen mob's `pos`, `state` and `target`, the secret-door map and
   `Dungeon.seed`, passing the ArchUnit rules (no game import, no reflection, no file access).
   This would be a total parity break with every guard still green.
2. **`Snapshot` is a `harness` type that never leaves the module. `api` declares an opaque
   `SnapshotHandle` (an id and a `scrubbed` flag, no payload) and a `Simulator` interface with
   which the Brain asks `harness` to roll out from a handle.** Chosen. The Brain can request a
   simulation and read its *result*; it can never read the state.
3. Encrypt the bytes in `api`. Rejected: the key would live in the same JVM; obfuscation, not a
   boundary.

**Snapshot mechanism**

4. **The game's own bundles: `Dungeon.saveGame` and `saveLevel` into byte arrays, plus the Harness
   state (`salt`, `k`, Profile version, tag, Codex version).** Chosen. Restore writes the bytes
   into the Run's Profile slot and calls the game's own `loadGame`, `loadLevel` and `switchLevel`,
   so no second serialization of game state exists.
5. Deep-copy the object graph. Rejected: static singletons, sprites, listeners and the actor
   thread's monitors make a copy neither complete nor safe.
6. Re-derive the state by replaying the Action list from the Run start. Kept as the *verification*
   of option 4 and as the fallback for a bundle that will not load; rejected as the mechanism
   because it is O(k) per restore.
7. Process checkpointing (CRIU, JVM snapshots). Rejected: platform-specific and outside Java.

**Redetermination mechanism**

8. **Rewrite the snapshot's bundle before loading: a `SnapshotScrubber` in `harness` walks the
   bundle and replaces every hidden element by the Belief sample's value.** Chosen as the E6
   default.
9. Build a fresh `Level` from the Observation plus the Belief sample. Kept as the E6 alternative
   if the scrub cannot reach some hidden element; rejected as the default because unknown regions
   would need synthetic layout and connectivity, which the scrub avoids by making them solid.
10. Redetermine in memory after loading. Rejected: the same fields as 8 but with sprites and actor
    registrations to keep consistent.

**What "hidden" means for the scrub** (each row is a differential-test case)

| Hidden element | Scrub | Why the Observation is unchanged |
|---|---|---|
| Unidentified potion, scroll and ring classes | Permute the class-to-label map among unknown classes per the sample and rewrite the `__className` of every instance carrying an unknown label | the Observation shows labels only (ADR-0006) |
| Unknown item level and curse | Re-roll per the sample within the game's own ranges | `levelKnown` / `cursedKnown` gate what is shown |
| Mobs outside the field of view | Remove them; re-add the sample's remembered mobs at sampled positions on VISITED cells outside the field of view | mobs are present iff drawn |
| **A stealthy passive mimic on a VISITED cell** | Keep it, because the game keeps drawing it (`…/scenes/GameScene.java:1443-1445`) and the Observation emits it as a `CHEST` heap (ADR-0006) | it is on screen |
| **AI state of every mob, visible or not** | Re-sample `state`, `enemySeen`, `target` and `enemy` from the sample; a visible mob keeps only what the screen shows (its emote and its buff icons) | the Observation carries the emote, never `Mob.state` or `target` |
| **Exact hit points of every mob** | Re-sample within the health-bar bucket the Observation reports | the screen shows a bar, not a number (ADR-0005) |
| Unknown cells (`Fog.UNKNOWN`) | Set to `WALL`, remove their heaps, traps, blobs and transitions | an UNKNOWN cell carries `Tile.NONE` whatever is there |
| Hidden traps and secret doors on known cells | `SECRET_TRAP` becomes `EMPTY` with the trap removed; `SECRET_DOOR` becomes `WALL` | drawn as floor and wall |
| Container contents | Replace by the sample's draw from the Codex tables | containers show only the container |
| **`Dungeon.seed`** | Replace with the sample's seed (`…/Dungeon.java:630`, restored at `:730`) | never observed; without this a rollout that descends would generate the *real* next floor through `seedForDepth` and `Level.create` (`…/Dungeon.java:414-430`; `…/levels/Level.java:217`), because descending is a single Action (`…/actors/hero/Hero.java:1955`) |
| Generator decks, `LimitedDrops`, `SpecialRoom` and `SecretRoom` queues, quest state, `Notes` entries not yet shown, `Statistics` | Reset to the tag's defaults or to the sample | never observed |
| Random state | The rollout's own salt comes from the sample; `k` continues | never observed |

Everything the scrub may touch is by construction outside the Observation; the differential test
proves it per row by comparing `Observation.sectionHashes()` before and after.

**Rollout execution**

11. Swap in place: snapshot the live Run, load the scrubbed copy into the same process, roll out,
    restore the live Run. Chosen as the E6 baseline; its cost is what ADR-0010's simulator-speed
    measurement reports.
12. Classloader-isolated engine instances in the same JVM, with libGDX and its natives in a shared
    parent loader and only game classes per child, because the JNI specification forbids loading
    the same native library into more than one class loader (Java 21 JNI specification, "Library
    and Version Management"). Kept as the alternative if the E1 isolation spike succeeds.
13. A pool of engine processes fed by the bot. Rejected: non-negotiable #4 forbids a process
    boundary between bot and game.

## Decision outcome

- `api` (E1): `SnapshotHandle` (opaque id, `scrubbed` flag, the `k` it was taken at), `BeliefSample`
  (label-to-kind assignment, remembered mobs, container draws, mob AI states, seed) and the
  `Simulator` interface (`rollout(SnapshotHandle, List<Action>) → RolloutResult`), where
  `RolloutResult` carries only Observations and outcomes, never state.
- `harness` (E1): `Snapshot` (module-private bytes), `SnapshotStore.take()` and
  `restore(SnapshotHandle)` over the game's bundles; the restore-and-replay test in CI from E1 on.
- `harness` (E6): `SnapshotScrubber` implementing `Redeterminer` per the table, the redetermination
  differential test, and the swap-in-place rollout host.
- **Restore contract.** `loadGame` calls `Generator.restoreFromBundle`, which calls `fullReset()`
  and consumes draws from the top generator (`…/Dungeon.java:822`; `…/items/Generator.java:625-636`,
  `:926`), so the Harness reseeds *after* the load completes, at the first Input wait of the
  restored Run, which is where ADR-0007 reseeds anyway. Restore also clears `hero.lastAction`
  (`…/Dungeon.java:508`) and resets `Actor.now` to zero (`…/actors/Actor.java:162`), which can
  re-trigger a free Foresight search; the restore-and-replay test asserts equality of Observation
  hashes from the restored wait onward and therefore catches any of these that matter.
- Rule: no Search may run on a handle whose `scrubbed` flag is false; the rollout host asserts it
  (FR-6, FR-13).
- Snapshots are written to disk only on request (the death gallery, FR-26); in the Overlay one
  snapshot per Input wait lives in memory for Take over and the v2 Replay scrubber.

### Consequences

- Good: the Brain cannot hold anything inflatable; the parity boundary is a module boundary, not
  a promise.
- Good: no second serialization; the game's own load path validates every snapshot.
- Good: "hidden" has a table, and each row is a differential-test case.
- Bad: the scrubber must know the bundle keys, which an upgrade can rename; the citation checker
  and the differential test catch it.
- Bad: swap-in-place serializes the live Run for every Decision that searches; if the measurement
  shows the cost dominates, ADR-0010's criteria route search to the abstract model or to
  isolation.
- Bad: making unknown cells solid changes mob pathing inside rollouts, since `PathFinder` walks
  `Level.passable`. That is the fair error, because the hero does not know the corridor either,
  and the horizon is short.

## Pre-mortem

*If this is wrong in six months, why?*

- The bundle carries hidden state the table missed. Mitigation: the differential test permutes
  every row, and the fairness reviewer audits the scrubber's key list against `Dungeon.saveGame`
  line by line before E6's first search story.
- `loadGame` has a side effect beyond state that the restore-and-replay test does not surface.
  Mitigation: the Profile is per Run and disposable; the test compares hashes, not fields.
- Snapshot cost makes per-wait snapshots in the Overlay stutter. Mitigation: take them from the
  bytes the game already writes on `saveAll`, or only on Take over and Pause.
- A rollout mutates static state that survives the restore. Mitigation: the restore path is the
  game's own level switch, which reassigns those; the restore-and-replay test with a rollout in
  between is the check.
