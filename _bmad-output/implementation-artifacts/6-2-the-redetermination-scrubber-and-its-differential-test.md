---
title: 'Story 6.2: The redetermination scrubber and its differential test'
type: 'feature'
created: '2026-10-26'
status: 'review'
baseline_commit: '770642583'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent, do not modify unless human renegotiates">

## Intent

**Problem:** E6's search needs to roll the engine forward from a snapshot, and a snapshot is the Run's whole hidden
state. ADR-0009 settled that a search never holds the bytes and that a *scrubbed* copy replaces everything the player
cannot know before any rollout. Nothing implements the scrubber or a host that rolls out from it.

**Approach:** `SnapshotScrubber` (a `Redeterminer`) and `RolloutHost` (a `Simulator`) in `harness`, with a differential
test that holds the scrub to its definition: a scrubbed copy reads, to `Observation.sectionHashes()`, exactly as the live
snapshot does, and differs from it in the hidden state.

## Acceptance Criteria (from epics.md Story 6.2, with the tests that hold them)

1. `SnapshotScrubber` implements every row of the ADR-0009 scrub table, each named in code (`ScrubRow`).
   `RedeterminationDifferentialTest`.
2. Before any code, the fairness reviewer audits the key list below against `Dungeon.saveGame`; the audit is in this file.
3. `Redeterminer.scrub` rejects an unscrubbed result, the live snapshot relabelled and another wait
   (`ReservedInterfacesTest`); the store refuses a handle that claims to be scrubbed and is not one it made
   (`SnapshotScrubStoreTest`).
4. `RedeterminationDifferentialTest` over the `smoke` set's mid-fight snapshots: per row and for all rows, equal section
   hashes; at least one hidden element changed per row the snapshot holds; two samples, two copies, equal Observations;
   a scrubber with one row removed fails naming the row.
5. `RolloutHost` (a `Simulator`): snapshot the live Run, load the scrubbed copy, roll Actions out through the executor,
   return `RolloutResult`, restore the live Run. `RestoreReplayAfterRolloutTest`; a descent inside a rollout builds its
   floor from the sample's seed, not the Run's; a handle the host did not make rolls out nothing.

</frozen-after-approval>

## Constraints that bind this design

- **Information parity.** The scrubber's inputs are the Observation taken at the snapshot's wait, the Codex tables and
  the sample. The true state is the thing being replaced, so it may be read only to be overwritten or to be kept where
  the Observation already shows it. Every replacement is drawn from a stream named by the sample and by a key the
  Observation also carries (a cell, an inventory index, a class name), never by position in the bundle, iteration order
  or the live generator. Two worlds with equal Observations therefore scrub, under one sample, to equal content.
- **No second implementation of the game's rules.** Fresh hidden values come from the game's own constructors and
  `random()` methods run inside `Random.pushGenerator(sampleStream)` / `popGenerator()`, so the live stream is untouched
  and the distribution is the game's own. Statics those calls touch (`Generator`, `SpecialRoom`, `SecretRoom`) are
  saved through the game's own bundle methods and put back.
- **The live Run is not disturbed.** A scrub reads one stored snapshot and writes a new one. It draws nothing from the
  live generator and allocates no actor id.

## Decisions

### Where the pieces live

- `org.shatterfish.harness.redetermine` (public, no snapshot types): `ScrubSample` (the sample's version-1 bytes),
  `ScrubRow` (the named rows), `Streams`, `Scrub` (the row logic over parsed bundles), `FogModel`, `Priors`.
- `org.shatterfish.harness.driver`: `SnapshotScrubber` and the store's registry of scrubbed snapshots, because
  `Snapshot` is package-private there (`SnapshotBoundaryTest`).
- `org.shatterfish.harness.agent`: `RolloutHost`, beside `RunLoop.crossFloor`, which a rollout that descends needs.
- A `Snapshot` now also carries the Observation read at its wait and the field of view, because "what the screen shows"
  must be the screen at the snapshot's wait, not the live Run's later one.
- The sample is `BeliefSample(version 1, bytes)`: the replacement dungeon seed, the rollout salt, the stream seed for
  every other draw, and a list of remembered mobs (class, cell). Nothing else, so a Brain's belief module can write it
  without knowing the bundle.

### Alternatives considered

1. **Scrub in place on the live objects, then reload.** Rejected: it writes through live statics (generator decks, room
   queues, actor ids) and the live Run would no longer be the twin ADR-0009 requires.
2. **A scrub driven by the true state with a "hide" mask** (copy the true value where the Observation does not show it
   and re-roll the rest). Rejected: it is the leak the story exists to close; which value was re-rolled is itself
   information, and any row that reads the truth to decide has a covert channel through its choice.
3. **Rebuild the whole level from the Observation** (synthesise terrain, mobs, items). Rejected: far outside the game's
   own save and load paths (ADR-0009, option 4), a second implementation of level rules, and it would invent a layout a
   rollout can then explore.
4. **Scrub bundle JSON (chosen).** The game saves JSON; each row is a function from parsed bundles, the Observation and
   a named stream to new bundles. Rows are separate classes of work so a test can remove one.
5. **Unknown cells.** (a) make them all `WALL`: wrong, because `Observer.fog` reads the stitchability of the cell below
   and beside a wall and `Level.cleanWalls` reads whether any of nine neighbours is not a wall, so the fog of a *known*
   wall changes. (b) copy the true class of each unknown neighbour: leaks the layout. (c) **chosen:** a small
   constraint search over the unknown cells that touch a known wall, three terrains (`WALL`, `BARRICADE`, `SECRET_DOOR`)
   differing only in those two properties, solved in an order set by the sample, accepting the first assignment under
   which the fog of every known wall is what the Observation says. The result depends on the Observation and the sample.
6. **A hidden mimic.** The ADR says keep it because the screen draws a chest there. Kept here as the AC requires, with a
   known residual: a mimic stays a mimic and a chest stays a chest, so a rollout learns which of the drawn chests is
   which. Closing it needs a mob bundle for a mimic made from a chest, which needs an actor id and a game object
   built in the live process; recorded as a deferral.

### Refusals instead of an incomplete scrub

The scrubber refuses (`IllegalArgumentException` naming the reason) where it cannot make a complete scrub:

- a boss floor or a floor of a branch (`depth % 5 == 0`, `branch != 0`): level subclasses carry state the table does not
  cover (Tengu, Imp shop, stored items);
- a floor holding a quest NPC in view: the quest state holds the reward;
- a floor with `locked` set.

### Pre-mortem

It is a year on and the scrub leaked. The likely causes, and what stops each:

| Cause | Guard |
|---|---|
| A row's choice read the true value (which unknown cell is floor) | rows draw from named streams; the solver orders by the sample; the differential test's two-worlds case compares the scrubbed *content* of worlds with equal Observations |
| A key nobody listed | the key audit below; the scrubber lists the keys it passes through, and a test fails on a key it has not classified |
| Iteration order carried truth (mob order, `HashMap` order) | kept mobs and blobs are sorted by cell before writing; JSON keys are read by name |
| A shared cache or generator (`Random` stack, `Actor.nextID`) | scrub runs inside `pushGenerator`, constructs no actor, sets `nextid` itself |
| The sample's salt left the Run's stream predictable | the rollout swaps the salt in `RngControl` and swaps it back on the next live restore |
| A rollout wrote to a process-wide place (Rankings, Bones) | `RolloutHost` restores them; `RestoreReplayAfterRolloutTest` compares the twin |
| The scrub was applied once and trusted | the store registers only what `SnapshotScrubber` made; `restore` refuses scrubbed ids, `restoreScrubbed` refuses live ones |

## The key list audited against `Dungeon.saveGame`

Each key the game writes, and what the scrubber does with it. KEEP means the key is history the player holds or a public
fact the Observation shows; every other class names the row.

| Key (file `game.dat`) | Row | Treatment |
|---|---|---|
| `<Class>_label`, `<Class>_known` for every potion, scroll and ring | POTION_SCROLL_RING_CLASSES | `known` kept; `label` permuted among classes with `known` false |
| `item_types_discovered` | POTION_SCROLL_RING_CLASSES | class strings mapped through the permutation |
| `placeholders` (quickslot) | POTION_SCROLL_RING_CLASSES | class strings mapped |
| `seed`, `custom_seed` | DUNGEON_SEED | replaced by the sample's seed and its code (kept non-empty so seeded-run semantics stay) |
| `generated_levels`, `dropped%d`, `depth%d.dat` of other floors | DUNGEON_SEED | trimmed to the current floor; other floors' files removed |
| `first_deck`, `general_probs`, `<category>_probs`, `_seed`, `_dropped`, `_using_probs2` | GENERATOR_AND_QUEUES | the game's `Generator.fullReset()` under the sample's stream |
| `limited_drops` | GENERATOR_AND_QUEUES | emptied (all counts zero) |
| `special_rooms`, `secret_rooms`, `pit_needed`, `region_secrets`, `placements` | GENERATOR_AND_QUEUES | `SpecialRoom.initForRun()` and `SecretRoom.initForRun()` under the sample's stream |
| `quests.*` | GENERATOR_AND_QUEUES | reset to unspawned; refused if the NPC is in view |
| `mobs_to_champion` | GENERATOR_AND_QUEUES | the game's initial value |
| `spawnersAlive` | GENERATOR_AND_QUEUES | the count of spawners kept in view |
| `nextid` | MOBS_OUT_OF_VIEW | one above the greatest id left in the bundles |
| `hero.*` | KEEP, except items | position, stats, talents, buffs, hunger: the status pane and hero window show them. `hero.belongings` and `inventory` items go through the item rows |
| `badges`, `boss_scores`, `quest_scores`, `chapters`, `challenges`, `amuletObtained`, `ankhsUsed`, `ascended`, `won`, `win_mult`, `chal_mult`, `daily*`, `version`, `init_ver`, `last_played` | KEEP | history or identity |
| `depth`, `branch`, `maxDepth`, `maxAscent`, `gold`, `energy` | KEEP | shown |
| `duration`, `enemiesSlain`, `foodEaten`, `hazard_assists`, `potionsCooked`, `priranhas`, `sneakAttacks`, `thrownAssists`, `upgradesUsed`, `flr_expl_*`, `score`, `prog_score`, `expl_score`, `tot_boss`, `tot_quest`, `tres_score`, `total_score`, `item_val`, `qualifiedFor*` | KEEP | the player's own history (`Statistics`); `duration` is the turn count the journal shows |
| `records`, `next_custom_id` | KEEP | `Notes`: every record is one the player has been shown |

| Key (level file) | Row | Treatment |
|---|---|---|
| `map` | UNKNOWN_CELLS, SECRET_TRAPS_AND_DOORS | unknown cells replaced by the constraint search; `SECRET_DOOR` to `WALL`, `SECRET_TRAP` to `EMPTY` on known cells |
| `visited`, `mapped` | KEEP; cleared on unknown cells | |
| `heaps` | UNKNOWN_CELLS, UNSEEN_ITEMS, CONTAINER_CONTENTS | heaps on unknown cells and heaps never seen removed; chests redrawn; item fields through the item rows |
| `mobs` | MOBS_OUT_OF_VIEW, STEALTHY_MIMIC_KEPT, MOB_AI_AND_HP | out-of-view mobs removed, remembered mobs added, hidden mimic on a drawn chest kept, kept mobs' state, target and hit points re-sampled |
| `traps` | SECRET_TRAPS_AND_DOORS, UNKNOWN_CELLS | hidden traps removed; traps on unknown cells removed |
| `blobs` | UNKNOWN_CELLS | volume zeroed on cells not in view; empty blobs removed |
| `plants` | UNKNOWN_CELLS | plants on unknown cells removed |
| `transitions` | UNKNOWN_CELLS | those whose designated cell is unknown removed |
| `rooms` | UNKNOWN_CELLS | rooms not wholly known removed |
| `customTiles`, `customWalls`, `customTerrain` | UNKNOWN_CELLS | emptied if any lies on an unknown cell |
| `targeted_cells` | UNKNOWN_CELLS | emptied |
| `mobs_to_spawn` | GENERATOR_AND_QUEUES | emptied |
| `respawner` | KEEP | the respawn clock; a residual (see below) |
| `width`, `height`, `version`, `locked`, `feeling` | KEEP | shown |

Mob and item keys: `HP`, `HT`, `state`, `seen`, `target`, `enemy_id`, `swarm_time`, `using_stealth`, `invest_turns`,
`wander_positions`, `wander_pos_idx` are re-sampled for an enemy in view; `pos`, `prev_pos`, `buffs`, `max_lvl`, `time`,
`id` are kept; item `level`, `cursed`, `enchantment`, `glyph`, wand charges and `__className` are rewritten by the item
rows when the item's level or curse is not known, and kept otherwise.

## Residuals recorded, not closed

- A hidden mimic stays on its chest (above).
- Buffs without an icon on a mob in view, its `time` and its `max_lvl` are kept.
- Gas volume on cells in view is kept (the Observation shows the kind and not the volume).
- Upgrades the hero applied to an item of unknown level are not tracked by the game and are not restored.
- The respawn clock, and the set of quest NPCs not in view, are kept or reset without a draw.
- Journal and process-wide badge persistence across a rollout follows the ADR-0009 amendment.

## Deferred

- Boss floors, branches and quest NPCs in view are refused, not scrubbed.
- The belief sampler that writes a `BeliefSample` is E6.3 or later; the tests write samples by hand.

## Fairness audit of the key list

Audited by the `fairness-reviewer` subagent against `Dungeon.saveGame` (`Dungeon.java:624-697`) and everything it
writes through, with the real depth-1 dump, before any scrubber code existed. Verdict: BLOCK, two items. Every
finding and its disposition follows; the dispositions amend the key tables above where they differ.

### Blockers

| # | Finding | Disposition |
|---|---|---|
| B1 | `hero.buffs` carries `MissileWeapon$UpgradedSetTracker` (`MissileWeapon.java:816-903`, enchant path `:490-504`); its `set_enchants` names the enchant of an unseen set member. Every hero buff without an icon is hidden state that the Observer never shows. | Fixed in the design. The `hero` buffs are rebuilt by an allowlist: a buff with an icon is kept (the Observation lists it), a buff without one is kept only if its class is in `Scrub.NO_ICON_KEPT` (mechanical state a player can derive, such as hunger and regeneration), and any other class is dropped (fail closed). `UpgradedSetTracker` has its `set_enchants` emptied. A test enumerates every `Buff` subclass on the classpath and fails if one without an icon is in neither list. |
| B2 | A hidden mimic kept as truth, with the true chest heaps: a rollout that opens chest A is ambushed and chest B is not, so search learns which chest is the mimic with no in-game cue. "Kept, documented as residual" is not enough. | Option (b), the refusal: a snapshot whose Observation shows a chest-family heap (`CHEST`, `LOCKED_CHEST`, `CRYSTAL_CHEST`) or whose bundle holds a hidden mimic is refused with a reason. A hidden mimic and a chest are one picture, so no accepted snapshot has a mimic to keep. This deviates from the acceptance text for `STEALTHY_MIMIC_KEPT` (the row exists as the gate that makes keeping unnecessary); the PR says so. Re-sampling mimic versus chest in both directions needs a mimic bundle (actor id, statics) and waits for a later story. |

### Fixes taken into the design

| Finding | Disposition |
|---|---|
| `seed`, `custom_seed`, `daily`, `daily_replay` | Replaced from the sample (`DUNGEON_SEED`); `daily` is set so Bones is inert; `generated_levels` trimmed to the current floor. |
| Sample keys: never draw sequentially in bundle order | All draws come from `Streams.of(sample, role, key)`, key from Observation-visible values (cell, inventory slot, class name). No sequential stream is shared across entities. |
| `Generator`, `SpecialRoom`, `SecretRoom` statics, `Random` stack, `Actor.nextID` | Generator and queue rows run under a pushed generator; static state is saved and restored through the game's own bundle methods; a test asserts the live Run's streams are unchanged by a scrub. `nextid` is recomputed as the largest kept id plus one. |
| `MissileWeapon.setID` uses `SecureRandom` (`MissileWeapon.java:78`) | The scrubber never copies `set_id` from a fresh item. Items a rollout creates are upstream's; recorded as a residual with ledger row 6's neighbours, not hooked here. |
| `DimensionalSundial` reads the wall-clock hour (`DimensionalSundial.java:59-67`) | A snapshot whose hero holds the trinket is refused. |
| `mobs_to_champion`, `limited_drops`, `chapters`, `dropped%d`, generator queues | Reset to defaults or the sample's (`GENERATOR_AND_QUEUES`). |
| `Statistics`: `flr_expl_N`, `exploreScore`, `spawnersAlive` | `flr_expl_N` of other floors dropped with their levels; `spawnersAlive` recomputed from the kept mobs; the others are screen-visible and kept. |
| Quest bundles (rewards hidden until the quest completes) | Reset to `{spawned:false}`; a snapshot where any quest is `spawned` is refused. |
| `Statue.weapon` | A snapshot with a Statue in view is refused (a coverage limit). |
| Unseen heaps: position and content | Unseen heaps removed (`UNSEEN_ITEMS`); a seen heap keeps its top item only; `hidden` and `haunted` cleared. |
| Item identity across pack, heaps and mob-carried items | One permutation per sample, applied to every class-valued string in every bundle; the hero's own labels are untouched because the permutation is over classes, and labels follow. A Thief's carried `item` is removed. |
| `transitions` of unexplored cells | Removed with their U cells. A rollout cannot reach an exit the screen has not shown; descent happens only through a shown transition. Recorded as a coverage limit. |
| `RegularLevel.rooms` | Kept only when every cell of the room is known. |
| `UNKNOWN_CELLS` neighbour equivalence | The CSP checks the Observer's own fog function over the candidate assignment (not a class table), and the differential test compares `sectionHashes()`. If no assignment matches, the snapshot is refused. |
| Traps with `visible=false` | Removed (`SECRET_TRAPS_AND_DOORS`). |
| Out-of-view mob removal | Removes the whole record; `target` and `enemy_id` of kept mobs that name a removed mob are cleared. |
| In-view state and HP | As designed; the HP bucket test includes shielding. |
| Blob volume out of view | Zeroed off `VISIBLE` cells; always-visible blobs kept; volume in view is a residual. |
| Trinkets with hidden rolls | Fail closed: a snapshot whose hero holds a trinket outside the known-safe list is refused. |
| Scrub iterates `HashMap` / `HashSet` | Every iteration in the scrubber sorts first. |
| Badges / rollouts must not write to disk | The driver copies and restores the profile files around a rollout (Part 2). |

### Residuals

Hidden item counts re-sampled from priors; gas volume in view; upgrades on unknown items are not restored; the respawn clock and `Statistics.duration` are kept (visible); `Notes` and Journal entries are kept as the player's; a mob's iconless buffs, `time` and `max_lvl` are kept; FLEEING and non-mimic PASSIVE states are kept.

### Not reviewable until code exists

Import edges, ArchUnit, leak tests per row, the toggle test and the determinism test. The final diff goes back to the reviewer.

## As built (amendments to the design above)

- **Stealthy mimic.** The row is a gate: a chest-family heap in view, or a hidden mimic drawn, makes `Scrub` refuse
  the snapshot (audit B2). The AC text says "keep"; the audit found keeping unsafe. ADR-0009's amendment records it.
- **Added rows.** `UNSEEN_ITEMS` and `HERO_HIDDEN_BUFFS` (blocker B1: buffs without an icon are dropped unless
  `GameRows.NO_ICON_KEPT` keeps them; `RedeterminationBuffsTest` holds the allowlist file
  `iconless-buffs-dropped.txt`). The copy sets `daily: true`, which keeps bones out of it and drops the other floors.
- **`FogModel`.** The fog function was lifted out of `Observer` so the scrubber and the Observer share it. `Observer`
  delegates; `FogParityTest` holds the equality. This is why the diff touches `Observer` and carries the `fairness` label.
- **Rollout salt.** `RngControl` gained an active-salt swap; the live restore unswaps; `Snapshot` carries the
  observation and field of view it was taken with, and the activation salt.
- **Rollout host.** `RolloutHost extends Simulator` in the agent package: checks `SnapshotStore.madeScrubbed`, takes the
  live snapshot, captures `RunStatics.Held` and a `ProfileGuard`, plays the Actions through `ActionExecutor` serving floor
  changes with `RunLoop.crossFloor`, and in a `finally` restores the live Run, steps it to its wait and puts the statics
  and the profile back. A refused Action ends the rollout `REFUSED`; death `DEATH`; the surface `WIN`; the end of the
  Actions, or a scene it cannot serve, `HORIZON`.
- **Profile containment.** A rollout in the live process would otherwise write the rankings (a death submits a daily
  record), the badges, the journal and its guide pages (which steer floor generation), and the statics. `ProfileGuard`
  and `RunStatics.Held` put them back. `Profile.emptyTheCatalogs` became public for the guard. One `Field` read was
  added to `RunStatics` (the private `read` behind `Held`); `HarnessReflectionTest` and `docs/UPSTREAM.md` say so. No new
  hook row: `RunStatics` is already listed.
- **Tests.** `RedeterminationDifferentialTest` (8 tests: all rows and each row alone show the same screen; the copy
  differs in each row held; two samples give two copies with one screen; an incomplete scrubber names its row; scrubbing
  leaves the live Run alone; container and unseen rows on synthetic heaps; the store refuses a claimed handle),
  `RedeterminationBuffsTest`, and `RestoreReplayAfterRolloutTest` (4 tests: the twin's hashes from the restored wait
  onward, with the profile files equal; a foreign, claimed or other-store handle rolls out nothing; a descent inside a
  rollout builds the sample's floor, repeatable per sample, different per sample and from the live seed's; the profile
  guard; a rollout to the hero's death leaves the files, rankings and live Run as found).
- **Fairness review of the final diff** (verdict BLOCK before the fixes): unidentified artifacts kept their true curse,
  grave heaps kept their contents, an awake mimic in view kept its loot, and two rolls failed open. Fixed in the same
  PR: artifacts and wands with unknown charges are rolled afresh; a mimic in view, a TOMB or REMAINS heap, an item with no
  fitting roll, and a health bar no hit points fit are refused. `hidden_contents_are_refused_or_rolled` holds the
  grave, the mimic and the artifact; `a_death_inside_a_rollout_is_contained` plays a rollout to the hero's death. The
  snapshot id carries the sample's whole hash. `ItemRows.classOf` resolves names inside the game's package only, by
  `loadClass`, and `OracleGateTest`'s name rule now bars `loadClass` too, with that one door excepted. Left open from the
  review: the kept visible mob's iconless buffs and non-sampled states (allowlisting as for the hero), and a
  two-worlds-one-screen leak test per row.
- **Residuals added.** See ADR-0009's amendment: re-created buff icons, true ids kept, `limited_drops` zeroed, `heroFOV`
  as an input, container and unseen rows covered by synthetic heaps only.
- **Fairness re-review of PR #262 (verdict BLOCK, fixed on the same branch).** Held by
  `RedeterminationReReviewTest` and `MobRowsTest`; all but the wand-charge, REMAINS and secret-door-equivalence tests fail
  on the code before the fix (those fill the gap the review named; the equivalence test passes before the fix too,
  because the solver resets an unneeded door to a wall either way, so it guards the property rather than proving the
  change).
  1. *Items outside the pack and the heaps.* `ItemRows.levels` now walks the whole `game` and `level` bundles, so the
     chasm-dropped `dropped%d` lists, a Thief's stolen item, a pin cushion's stuck missiles and a shopkeeper's buy-back are
     rolled afresh like the pack's. They are re-rolled rather than trimmed (the audit table said "trim or scrub"); a trim
     would change what a descent inside a rollout finds.
  2. *Salt in ids.* A live snapshot's id is `snapshot-<counter>`; a scrubbed copy's id is the source id, the sample's hash
     and the row mask. No id, handle or refusal message prints a salt (the driver's salt-mismatch message too).
  3. *Walls and secret doors.* Every known cell the screen draws as a wall is a variable in `CellRows` with the domain
     {WALL, SECRET_DOOR}, so a true secret door is no longer the only cell the solver may turn.
  4. *Order and wording.* Unseen heaps and out-of-view mobs are removed before any re-roll that can refuse, and
     refusal messages name no class.
  5. *Mobs in view.* `MobRows.hidden` drops their iconless buffs and resets cooldowns rolled from `Random` (any numeric
     key containing `cooldown` or ending `_cd`) to ready.
  6. *Partial copies.* `Snapshot` carries `complete`, true only when the scrubber ran with every row; `madeScrubbed`
     requires it, so `RolloutHost` refuses a copy made with a subset. The subset constructor stays public for the
     differential test, which restores such copies through the store.
  7. `OracleGateTest` excepts `ItemRows` from the `loadClass` rule only; `forName` stays barred everywhere.
  8. Tests added for wand charges, REMAINS, the health bar and a no-fitting-roll refusal.

  Residuals from the re-review: the scrubbed copy is written to the real save slot while a rollout runs (the live Run is
  restored from memory afterwards, but a crash mid-rollout leaves the copy in the slot); mob cooldowns reset to ready
  rather than sampled; `dropped%d` are re-rolled, not trimmed. The differential test's world reader sees only unseen
  heaps on known cells, so heaps on never-visited cells are removed but not read back.
