---
status: accepted
date: 2026-09-04
deciders: watchthelight (product owner), Claude (engineer)
---

# ADR-0005: Observation schema and hashing

## Context and problem statement

The Observation is the only thing the Brain ever sees (non-negotiable #1) and the unit of
reproducibility (non-negotiable #5): the determinism test compares Observation hashes at every
Input wait (PRD FR-2), the leak and differential tests compare serialized Observations (FR-8,
FR-9), and the Run log records one Observation hash per Input wait (FR-23). The `api` module that
holds it may depend on nothing but the JDK (ADR-0003). Decide the shape of the Observation, how
it is serialized, and how it is hashed so that equal Observations have equal bytes and equal
hashes on every JVM and platform.

Non-negotiables touched: #1 (parity: the schema must be able to carry only what is drawn),
#4 (Java, in-process), #5 (measured and reproducible).

Facts from the code (`docs/codebase-map.md`, `docs/rules/`): the game draws from `heroFOV`,
`visited`, `mapped`, `discoverable` (`…/tiles/FogOfWar.java:288-299`), draws secret terrain with
the visual of its cover (`…/tiles/DungeonTileSheet.java:427`, `:464`), names an unknown item by its
appearance (`…/items/potions/Potion.java:377-379`), shows a mob iff `heroFOV[mob.pos]`
(`…/scenes/GameScene.java:1441-1448`), and shows the hero's buffs only when they have an icon
(`…/ui/BuffIndicator.java:192-196`). Paths abbreviate
`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/` as `…/`.

## Decision drivers

- Byte-identical serialization across JVMs and operating systems (the nightly cross-platform
  Replay check, NFR-2).
- No third-party dependency in `api`.
- The differential test must be able to say *which section* differs, not only that the hash
  differs.
- The schema must evolve (E5 adds the Prompt kinds the Overlay answers, E6 adds nothing but
  must not break E1 logs) without silently changing old hashes.
- Human-readable form for the Run log and the Panel's Explain view.

## Considered options

**Shape**

1. A single `Map<String, Object>` blob. Rejected: no compile-time contract; the Brain would
   reconstruct types by hand and the leak tests could not enumerate fields.
2. **Java records in `org.shatterfish.api`, immutable, nested by section.** Chosen.
3. A screenshot (pixels) as the Observation. Rejected: parity-perfect but useless to a symbolic
   Brain and far too slow; the Rules pages already give the exact drawing predicates, which is
   the same information at the semantic level.
4. Reuse upstream's `Bundle` JSON of the hero and level with hidden fields stripped. Rejected:
   the stripping would be a blacklist over a schema upstream owns; a new field in a later tag
   would leak by default. The Observation is a whitelist by construction.

**Serialization and hashing**

5. Java serialization plus `Arrays.hashCode`. Rejected: not canonical across JVM versions;
   `hashCode` is 32 bits.
6. JSON through Jackson or Gson, hashed after canonicalization. Rejected for the hash: adds a
   dependency to `api`; float formatting and key ordering are library-dependent.
7. Protocol Buffers or FlatBuffers. Rejected: code generation and a dependency in `api` for a
   one-JVM product; deterministic serialization is not guaranteed by protobuf.
8. **A hand-written canonical binary encoding in `api` (`ObservationCodec`): fixed field order
   from the record declaration, big-endian fixed-width integers, UTF-8 strings with a 4-byte
   length prefix, booleans as one byte, lists with a 4-byte count, no floats anywhere in the
   schema (probabilities and ratios are stored as integers over a stated denominator), and a
   schema version integer first. SHA-256 over those bytes is the Observation hash.** Chosen.
9. **Section hashes (Merkle): each top-level section is encoded and hashed on its own and the
   Observation hash is SHA-256 over the concatenated section hashes plus the version.** Chosen
   in addition to 8, so a differential-test failure names the section.
10. A hand-written JSON writer in `api` (about 150 lines, no dependency) for the Run log and the
    Explain view, never for hashing. Chosen in addition; the JSON is derived from the same
    records and is not canonical.

**Numeric representation of the map**

11. Cells as the game's `Terrain` integers. Rejected: raw `Terrain` includes `SECRET_DOOR` and
    `SECRET_TRAP`, so the encoder itself would be a leak surface.
12. **Cells as a `Tile` enum defined in `api` that has no secret members: the encoder maps the
    drawn visual (wall for a secret door, floor for a secret trap) and a `Fog` enum
    (VISIBLE, VISITED, MAPPED, UNKNOWN) per cell.** Chosen; the Observer (ADR-0006) does the
    mapping, the schema makes the leak unrepresentable.

## Decision outcome

The Observation is a record tree in `org.shatterfish.api`:

| Section | Contents (all as the screen shows them) |
|---|---|
| `header` | schema version, Upstream tag, Codex version, hero class, challenge flags, depth, branch, `sealed` (the floor is locked by a boss fight, so no transition is possible: `Level.locked`, which the screen shows through the locked stairs and the boss bar), `oracle` boolean (true only for an `OracleObserver` Run), kind of the open Prompt if any. Not the Input wait index `k` (the Brain counts waits itself; putting `k` in the hash would make every Observation unequal and every hash-change guard vacuous), not the seed, not the salt. Not the seed (drawn by `WndHero` but excluded so a Brain cannot fingerprint published seeds, FR-9) and not a turn counter (the game draws none; the Run log carries `Statistics.duration + Actor.now()` as fixed-point thousandths outside the hash) |
| `map` | width, height, per cell `Tile` and `Fog`; traps that are `visible` *and* on a cell whose `Fog` is not `UNKNOWN` (cell, trap kind, active); seen heaps (cell, container type, `hidden` flag, current top item's display name for plain and for-sale heaps, price for a single for-sale item, the category word for a crystal chest); per visible cell the set of blob kinds present (never a volume); level feeling if announced; transitions the player has seen |
| `actors` | visible chars: display name, cell, health quantised to the health bar's pixel resolution (`ceil(fraction * W) / W` with `W` stated in the codec; full HP when the bar is hidden), alignment, `invisible` flag (drawn at alpha 0.4), the emote shown (`NONE`, `SLEEP`, `ALERT`, `LOST`, `INVESTIGATE`), every buff with an icon and the turns its description would show. A neutral passive mimic is not an actor: it is emitted as a `CHEST` heap (ADR-0006) |
| `hero` | HP, HT, level, experience, strength, gold, hunger state (none, hungry, starving), every buff with an icon and the turns its description shows (no cap: the hero window lists them all), subclass, talents as the talents pane shows them, quickslots |
| `inventory` | items with display name, category, appearance label when unidentified, `levelKnown`, `cursedKnown`, `visiblyUpgraded`, `visiblyCursed`, `status()` text, quantity, equipped slot, and the valid `actions()` list |
| `journal` | this Run's Notes (landmarks, keys), the per-Run known appearances (`Potion/Scroll/Ring.getKnown()`), the Catalog is excluded (cross-Run state) |
| `log` | the last N `GLog` messages as raw (text, color prefix) pairs captured from the `GLog.update` signal, never `GameLog`'s rendered entries (which merge and wrap by UI size on the render thread) |
| `actions` | the valid-Action set (FR-3), one entry per `Action` (ADR-0014) the ActionExecutor would accept now, computed from the Observation alone |
| `prompt` | the open Prompt, if any: kind, text, options |

Rules:

- No floats. Health, probabilities and fixed-point turns are integers or integer pairs.
- Every list has a canonical order fixed by the codec, never the order a `HashSet` or `HashMap` produced: actors, heaps, traps and blob cells by cell index; buffs, known appearances and blob kinds by class name; inventory in `Belongings` iteration order (weapon, armor, artifact, misc, ring, second weapon, backpack). A codec test shuffles every input list and expects equal section hashes.
- The `Belief` is not part of the Observation: it is a separate opaque `api` value produced by the Brain and carried by the caller (AD-14), so that `harness` can hash and log it without depending on `brain`.
- Oracle data is never a field of `Observation`. `OracleObserver` returns an `OracleView` sidecar for debugging and labelling tools, and sets `header.oracle`, so an Oracle Run's hashes differ from a fair Run's and a Brain cannot read what the record does not hold.
- No secret members in any enum. `Tile` has no `SECRET_*`; `Fog.UNKNOWN` cells carry `Tile.NONE`.
- The `header.version` is bumped whenever the encoding of any section changes; the Run log
  records it; a Replay refuses to compare across versions.
- `ObservationCodec.encode(Observation)` is the only encoder; `Observation.hash()` is SHA-256 of
  the section hashes; `Observation.sectionHashes()` is exposed for the differential test.
- Equality of records is structural (Java records), so `equals` and `hash` agree by
  construction; a test asserts `a.equals(b)` iff `a.hash().equals(b.hash())` over a corpus.
- The JSON rendering carries the hash as a field and is never re-parsed into an Observation.

### Consequences

- Good: the leak tests enumerate record fields, so "hidden state cannot appear" is a check over a
  finite list.
- Good: a section-level hash makes the differential test's failure message actionable.
- Good: no dependency in `api`; the codec is about 400 lines and fully unit-tested.
- Bad: a hand-written codec must be kept in step with the records; mitigated by a reflection
  test that fails when a record component is not encoded.
- Bad: the health bar's reference width `W` is read from `CharHealthIndicator` and `HealthBar`
  (`…/ui/CharHealthIndicator.java:49-55`, `…/ui/HealthBar.java:65-88`) in the E1 Observer story; the
  codec states it as a constant so the quantisation is reproducible.

## Pre-mortem

*If this is wrong in six months, why?*

- The Brain needs something the screen shows that the schema lacks (an animation, a sound).
  Mitigation: the schema is versioned and additive; sounds are already excluded by the parity
  rule since the log carries their text.
- Section hashes are too coarse to explain a differential failure inside `map`. Mitigation:
  the codec can hash per-cell chunks on demand in the test; the production hash stays as decided.
- The canonical encoding differs between JVMs after all (string normalization, surrogate
  pairs). Mitigation: strings are encoded as UTF-8 code units of the exact Java string, no
  normalization; the nightly cross-platform Replay is the test.
- Performance: encoding a 32x32 map plus inventory each Input wait costs more than the Brain's
  decision. Mitigation: the E1 benchmark (FR-5) reports codec time separately; the map section
  can carry a dirty-region delta later without changing the hash definition.

## Amendment: story 1.6 (2026-09-05)

The header, the map and the actors, `ObservationCodec` and the section hashes are implemented as
decided, with these choices the decision left open or did not foresee.

**SHA-256 and UTF-8 are written in `api`.** The module may reach only `java.lang` and `java.util`
(`ApiBoundaryTest`, kept as strict as the brain's allowlist since story 1.2), which puts
`MessageDigest` and the character sets out of reach. The digest is a hundred lines held to the
standard's vectors and the JDK's answer over random input; the encoder mirrors `String.getBytes`
on the exact code units, an unpaired surrogate included.

**Canonical order is the records', not only the codec's.** The rule that `equals` and the hash
agree needs the order fixed before equality is computed, so every set-like list is sorted and
checked for repeats in the record's constructor: challenges by name; traps, heaps, blobs,
transitions and actors by cell, each cell at most once; blob kinds and buffs by name. Tiles and
fog are positional, one per cell. Enums are encoded by name, so a constant added later changes no
existing bytes.

**The records refuse what the fog would not draw.** An unknown cell carries `Tile.NONE` and
nothing else does; traps, heaps, blobs and transitions stand on cells the player has seen; an
actor stands in view; a container shows no item, a price belongs to a for-sale heap only and a
category to a crystal chest only. ADR-0006's whitelist is a whitelist by construction, which
`SchemaRulesTest` holds.

**Health is quantised at the coarsest view any player has.** The bar over a sprite is the sprite's
width times four sixths (`…/ui/CharHealthIndicator.java:50-51`), drawn to the pixel with the lit
part rounded up (`…/ui/HealthBar.java:65-68`), so its resolution depends on the camera zoom, which
the player can set as low as 1 (`…/scenes/PixelScene.java:144`). The codec states `W = 32/3`, a
sixteen-unit sprite at zoom 1, and `healthPips` is `ceil(hp / max * W)` in integer arithmetic,
0 to 11. That is at most what any player sees, which is what parity asks; a player at the default
zoom of 2 or more sees a finer bar, and the Observation deliberately does not.

**The hero is not an actor.** The actors section carries every visible character but the hero,
whose cell and health belong to the hero section of story 1.7.

**`Feeling.SECRETS` stays.** The floor feeling is announced as a title on arrival ("secrets
floor", `core/src/main/assets/messages/levels/levels.properties:260`), so the member names an
announcement, not the secrets; the enum test names it as the one exception to "no member contains
SECRET".

**Buff turns are hundredths.** The description prints turns with `#.##`
(`…/actors/buffs/Buff.java:136-138`), so a buff carries hundredths of a turn and whether turns are
shown at all.

**The version is 1 and pinned.** `ObservationHashTest` holds the corpus Observation's hash to a
constant, so any change to the encoding is a change to the version, recorded here, before the pin
moves. `HeaderSection.version` is the schema version, which the codec refuses to encode unless it
is its own.
