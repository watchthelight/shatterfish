---
status: accepted
date: 2026-10-28
deciders: watchthelight (product owner), Claude (engineer)
---

# ADR-0024: A practice Run starts on the boss floor with a profile kit, is stamped in its header, and is never ranked

## Context and problem statement

Epic E10 sets the next rung: Tengu (depth 10) killed in 95% or more of Runs. A real Run reaches depth
10 in a minority of Runs, so measuring a change to the Tengu fight on real Runs spends most of its
time on floors that are not the question, and the sample that reaches Tengu is small and survivor
biased. The fight needs a harness that starts a Run on the boss floor with a hero fit for it, so that
a Tengu change is measured on fights.

That harness plays a Run that no player could have had. Three things bind it:

- **Non-negotiable 1 (information parity).** The setup is scaffolding outside the Brain. The Brain
  is handed its Observation and nothing of how the hero came to be where it is. The kit must be
  something a player could hold (no item or level the game cannot produce), and nothing about the
  setup may reach the Observation by a side door.
- **Non-negotiable 3 (upstream edits are hooks).** The game must be driven with its own classes
  and no upstream file edited.
- **Non-negotiable 5 (everything is measured and reproducible).** A practice Run is fully determined
  by (tag, seed, salt, kit profile, action list), and its log must say so and be replayable.
- A practice number must never be mistaken for a ranked one: a Registration, a direction table,
  a published Results page and a baseline must refuse it.

## Decision drivers

- The floor is the floor a real Run of that seed reaches, so a practice result is about the real
  fight. `Level.create` skips its item block on a boss floor (`core/.../levels/Level.java:224`) and a
  floor's seed is a function of the Run's seed and its depth alone (`core/.../Dungeon.java:414-431`),
  so a boss floor does not depend on the floors above it.
- The kit is a realistic arrival, derived from what the game gives out and from what real Runs
  arrive with, and a profile can later be sampled from real arrivals.
- No upstream edit, no new hook row in `docs/UPSTREAM.md`.
- A refusal that is enforced where logs are read, not only promised in prose.

## Considered options

1. **Edit `Dungeon` to take a start depth and a kit.** The shortest path and one number to turn.
   Rejected on non-negotiable 3: an upstream edit for a measurement tool, a hook row and a merge
   cost at every upstream tag, for something the harness can do from outside.
2. **Descend for real, with a bot that skips nothing, then record only the boss fight.** The kit is
   exactly what a real descent gives. Rejected on the driver of cost: it is the current
   `:rig:direction` and spends 90% or more of the time on floors above, and a Run that dies at
   depth 4 contributes nothing to the question.
3. **A save-game snapshot at the boss's stairs** replayed per seed. Rejected: the game's save format is
   upstream's, versioned by upstream, and a snapshot of a Run carries the Run's RNG state and
   history, which breaks "a function of (tag, seed, salt, profile)" and ties the practice to one
   Brain's descent.
4. **A debug-menu "teleport and give items" through the game's own item spawner, played with the
   Brain.** Needs the scene layer, which the harness does not run, and the items it spawns are
   random draws from the Generator, so the kit is neither a profile nor a function of its name.
5. **Harness-side start: after `Dungeon.init`, raise the hero with the game's own classes to a
   `KitSpec`, set the depth, and build the floor with `Dungeon.newLevel` as the descent does;
   stamp the profile in the header.** Chosen.
6. **A separate process that drives the game over a socket.** Rejected on non-negotiable 4 (one JVM,
   in process).

## Decision outcome

Option 5.

### The start

`NewGame.begin` calls `Dungeon.init`, then `PracticeStart.arrive(boss, kit)`, then reseeds the Run's
generator and builds the boss floor. `arrive` runs the hero's level through `Hero.earnExp` (so
health, accuracy and evasion follow the game's own level-up), sets strength as the field a potion of
strength raises, upgrades each piece one scroll at a time with `Item.upgrade`, builds the pack from a
fixed allowlist of item classes, identifies those items, and sets the `LimitedDrops` counts to what
the floors above are taken to have given out. It draws from a generator seeded from the Run's seed
and the kit, so what it draws is a function of the tuple and the profile and not of any earlier floor.
`Dungeon.depth` is the boss's depth before the floor is built, so the key door of the prison boss
(`PrisonBossLevel`, `Notes.keyCount(new IronKey(Dungeon.depth))`) matches the key it spawns.

### The profile

A profile is a name for a function from (boss, hero class) to a game-free `KitSpec` in `harness`:
level, strength, equipment with upgrades, pack, and the drop counts. `standard` is written by hand from
what the code states: two potions of strength and three scrolls of upgrade per five-floor set
(`Dungeon.java:529-562`), the experience curve (`Hero.java:2135-2137`), and the Brain's own
upgrade allocation. The `.arrivals.tsv` file that `:rig:direction` writes beside each Run records
what real Runs arrive with at each depth, so a sampled profile has the same shape. The numbers behind
the first level choice are in `docs/results/e10-practice-kit.md`.

### The stamp, determinism and replay

The header gains a chained field `practice`, written only when set, so every existing log keeps its
bytes: `<boss>/<profile>@<first 16 hex of SHA-256 of the kit's canonical text>`. A Replay re-resolves
the stamp and refuses the log if the profile now resolves to a different kit, so a profile cannot be
edited under logs that name it. Same tag, seed, salt, class, profile and action list give the same
log chain. A new end cause, `BOSS_DOWN`, stops a practice Run when the boss it was started for is
killed, so a Run does not go on to play the floors below.

### The refusal

A practice Run is never ranked, enforced at the points that read logs and not only in prose:

- the header validator refuses a practice stamp together with a registration, and the practice
  runner and `RunOne` refuse a Registration;
- `RunLoop.Logging` refuses a practice start with oracle mode;
- `OverlayLogs.refuse`, the door every counting path already uses against Overlay logs, now refuses a
  header that carries the stamp, so `LogHeader`, `Comparison`, `GalleryComparison` and the Results
  extract throw on one; `DirectionMetrics.read` calls it too, so a practice folder is not a
  baseline and not a candidate;
- `PracticeRuns` reads only `standard` and `smoke` triples, so `goo`, `holdout` and the sets kept for
  registered use are refused by name;
- `SeedSets.publish` takes a Registration's id and no log, and a practice Run has no Registration, so it
  has no door to publish through.

The debug views (the gallery of a folder, the strategy log) read a practice folder: they count nothing.

## Pre-mortem

If this is wrong in six months:

- **The kit is not a real arrival, and a Tengu fix that works in practice does not work in a real
  Run.** Mitigation: the profile is checked against the arrival records of real Runs
  (`docs/results/e10-practice-kit.md`), the practice Goo conversion is reported against the real Goo
  conversion on the same class, and a practice number is never the acceptance: E10's rungs are
  measured on real Runs and the registered `tengu` set.
- **A profile is edited and old logs silently mean something else.** Mitigation: the stamp carries the
  kit hash and Replay refuses a mismatch.
- **A practice number leaks into a table that ranks.** Mitigation: the refusal is in the readers, and
  a test per reader reads a practice log and expects `Refused`.
- **A parity leak: the Brain learns the Run is a practice Run.** Mitigation: the stamp is in the log
  header, not the Observation, and `RunLoop` hands the Brain only what the Observer builds; the kit
  is visible to it only as the hero's level, items and strength on screen, which a real hero has.
  The fairness review covers the diff.
- **The boss floor is not history independent after an upstream change.** Mitigation: a test builds
  the floor with and without the practice start and compares its map hash, and fails on a tag whose
  boss floor reads earlier state.
- **Unspent talent points at a high start level strand the hero.** Mitigation: the first Run of each
  class is checked for the Brain spending them, and the profile's level is the one a real arrival has.

## Review notes

The fairness review (2026-10-28) found no violation. Two facts it asked to state: `arrive` identifies the pack items it builds, which marks the potion of healing kind as known for the Run, as holding one would for a player and revealing no other item; and it sets the `LimitedDrops` counts, so floors below the boss differ from a real descent, which `BOSS_DOWN` ends before it matters. The gallery shows practice Runs (a debug view that counts nothing).
