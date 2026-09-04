---
status: proposed
date: 2026-09-03
deciders: watchthelight (product owner), Claude (engineer)
---

# ADR-0009: Snapshot, restore, and redetermination

## Context and problem statement

The Harness must be able to snapshot a Run, restore it, and produce a *redetermined* copy in
which every hidden element is re-sampled from a Belief (PRD FR-6, deferred to E6 with the
interface reserved in E1). Redetermination is what makes engine rollouts fair (non-negotiable #1;
`docs/fairness.md`, "Search"): a rollout on the raw saved game would see unidentified item
classes, unseen mob positions, unexplored layout, hidden traps and the generator state. Session 10
read the save path: a save is a gzip `Bundle` of `org.json` objects written by `Dungeon.saveGame`
and `saveLevel` (`…/Dungeon.java:624-704`; `SPD-classes/…/utils/Bundle.java:365-376`, `:483-502`),
loading is `loadGame` plus `loadLevel` plus `switchLevel` (`…/Dungeon.java:723-840`,
`…/scenes/InterlevelScene.java:733-747`), no random state is saved, `saveAll` runs `Actor.fixTime`
first (`…/actors/Actor.java:170-192`), and `switchLevel` clears `hero.curAction`
(`…/Dungeon.java:508`). Game state is static singletons, so one process holds one world at a time.

Non-negotiables touched: #1 (parity of rollouts), #3 (hooks), #4 (in-process), #5 (a restored
snapshot must replay identically).

## Decision drivers

- Restore-and-replay must reproduce the original Observation hashes from the snapshot's Input
  wait onward; ADR-0007's per-wait reseeding makes the random state at wait `k` just `(salt, k)`.
- The scrubbed snapshot's Observation must be byte-identical to the original's (the differential
  test, FR-9), which is the definition of "hidden": anything the scrub may change.
- Zero or one hook; the game's own save and load paths already exist.
- The snapshot must be cheap enough to take at every Input wait in the Overlay (for Take over
  and for the E8 Replay scrubber) and to restore many times per Decision in E6.

## Considered options

**Snapshot mechanism**

1. **The game's own bundles: `Dungeon.saveGame` and `saveLevel` written to byte arrays (through
   `Bundle.write(OutputStream)`), plus the Harness state (`salt`, `k`, Profile version, tag,
   Input-wait definition).** Chosen. Restore writes the bytes into the Run's Profile slot and
   calls the game's `loadGame`/`loadLevel`/`switchLevel`, so no second serialization of game
   state exists.
2. Deep-copy the object graph. Rejected: static singletons, sprites, listeners and the actor
   thread's monitors make a copy neither complete nor safe.
3. Re-derive the state by replaying the Action list from the Run start. Kept as the *verification*
   of option 1 (the restore-and-replay test) and as the fallback for a Run whose bundle will not
   load; rejected as the mechanism because it is O(k) per restore.
4. Process checkpointing (CRIU, JVM snapshots). Rejected: platform-specific and outside Java.
5. A Harness-owned parallel world model. Rejected: a second implementation of the rules
   (non-negotiable #4).

**Redetermination mechanism**

6. **Rewrite the snapshot's bundle JSON before loading: a `SnapshotScrubber` in `harness` walks
   the level and game objects and replaces every hidden element by the Belief sample's value.**
   Chosen as the E6 default.
7. Build a fresh `Level` from the Observation plus the Belief sample (the "world built from
   Observation" design of bootstrap §4). Kept as the E6 alternative if the scrub cannot reach
   some hidden element; rejected as the default because unknown regions would need synthetic
   layout and connectivity, which the scrub avoids by making them solid.
8. Redetermine in memory after loading (mutate live objects). Rejected: the same fields as 6
   but with sprites and actor registrations to keep consistent; the bundle is the cleaner seam.

**What "hidden" means for the scrub** (each is an element the differential test permutes)

| Hidden element | Scrub | Why the Observation is unchanged |
|---|---|---|
| Unidentified potion, scroll and ring classes | Permute the class-to-label map among unknown classes per the sample, and rewrite the `__className` of every instance carrying an unknown label (hero belongings, heaps, shop stock) | the Observation shows labels only (ADR-0006) |
| Unknown item level and curse | Re-roll per the sample within the game's own ranges | `levelKnown`/`cursedKnown` gate what is shown |
| Unseen mobs | Remove every mob outside `heroFOV`; re-add the sample's remembered mobs at sampled positions on VISITED cells outside FOV | mobs are present iff in FOV |
| Unknown cells (`Fog.UNKNOWN`) | Set to `WALL`, remove their heaps, traps, blobs and transitions | an UNKNOWN cell carries `Tile.NONE` whatever is there |
| Hidden traps, secret doors on known cells | `SECRET_TRAP` → `EMPTY` with the trap removed; `SECRET_DOOR` → `WALL` | drawn as floor and wall |
| Container contents | Replace by the sample's draw from the Codex tables | containers show only the container |
| Generator decks, `LimitedDrops`, quest state not yet shown | Reset to the tag's defaults | never observed; rollouts inside a Decision horizon never generate a floor |
| Random state | `salt' = sample.seed`, `k` unchanged | never observed |

Everything the scrub may touch is by construction outside the Observation; the differential test
proves it per element by comparing `Observation.sectionHashes()` before and after.

**Rollout execution (where a redetermined copy runs)**

9. Swap in place: snapshot the live Run, load the scrubbed copy into the same process, roll out,
   restore the live Run. Chosen as the E6 baseline; its cost is what the E1 spike measures
   (ADR-0010's simulator-speed criterion).
10. Classloader-isolated engine instances in the same JVM (libGDX and natives in a shared parent
    loader, game classes per child). Kept as the alternative if the E1 isolation spike succeeds.
11. A pool of engine processes fed by the bot. Rejected: non-negotiable #4 forbids a process
    boundary between bot and game.

## Decision outcome

- `api` (E1): `Snapshot` (opaque bytes plus `salt`, `k`, `profileVersion`, `tag`, `schemaVersion`),
  `BeliefSample` (label-to-kind assignment, remembered mobs with positions, container draws,
  `seed`), and the `Redeterminer` interface.
- `harness` (E1): `SnapshotStore.take()` and `restore(Snapshot)` over the game's bundles; the
  restore-and-replay test (restore at wait `k`, replay the remaining Actions, expect the original
  hashes) runs in CI from E1 on.
- `harness` (E6): `SnapshotScrubber` implementing `Redeterminer` per the table; the
  redetermination differential test; the swap-in-place rollout host, with classloader isolation
  as the alternative decided by the E1 spike.
- Rule: no Search may run on a `Snapshot` that has not passed through `Redeterminer`; the
  rollout host asserts a `scrubbed` flag set only by the scrubber (FR-6, FR-13).
- Snapshots are never written to disk by the Rig except on request (the death gallery, FR-25);
  in the Overlay one snapshot per Input wait lives in memory for Take over and the v2 scrubber.

### Consequences

- Good: no second serialization; the game's own load path validates every snapshot.
- Good: "hidden" has a table, and each row is a differential-test case.
- Bad: rewriting `__className` for unknown items touches every bundle that holds items; the
  scrubber must know the bundle keys (`Belongings`, `Heap`, `Shopkeeper` stock), which an
  upgrade can rename; the citation checker and the differential test catch it.
- Bad: swap-in-place serializes the live Run every Decision that searches; if the E1 spike shows
  the cost dominates, ADR-0010's criteria route search to the abstract model or to isolation.
- Bad: making unknown cells solid changes mob pathing inside rollouts (a mob cannot arrive through
  a corridor the hero has not seen); this is the fair error, since the hero does not know the
  corridor either, and the horizon is short (ADR-0010).

## Pre-mortem

*If this is wrong in six months, why?*

- The bundle carries hidden state the table missed (a quest's target item, a boss phase).
  Mitigation: the differential test permutes every element in the table; the fairness-reviewer
  subagent reviews the scrubber's key list against `Dungeon.saveGame` line by line before E6's
  first search story.
- `loadGame` has side effects beyond state (badges, journal, `GamesInProgress` slot info).
  Mitigation: the Profile is per Run and disposable; the restore path uses the same fresh
  Profile.
- Snapshot cost makes per-wait snapshots in the Overlay stutter. Mitigation: take them off the
  render thread from a copy of the bytes the game already wrote on `saveAll`, or only on
  Take over and Pause.
- A rollout mutates static state that survives the restore (`Item.curUser`, `Dungeon.hero`).
  Mitigation: the restore path is the game's own level switch, which reassigns those; the
  restore-and-replay test with a rollout in between is the check.
