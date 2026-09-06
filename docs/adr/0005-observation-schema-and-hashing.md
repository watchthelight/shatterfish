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

**`Tile` is the tile sheet's visuals, not the terrain table.** Option 12 said an enum with no
secret members; the review of the story found that raw terrain has more than the two secret
constants that the screen does not tell apart. A trap's floor, active or not, and custom
decoration floor are drawn as the empty floor (`…/tiles/DungeonTileSheet.java:427-431`), the trap
itself on a layer of its own only while revealed (`…/tiles/TerrainFeaturesTilemap.java:57-62`),
and a door the hero locked as a locked door by visual and by name (`:446-447`;
`…/levels/Level.java:1584-1586`). So `Tile` has one member per visual the sheet distinguishes
(`DungeonTileSheet.java:414-465`), thirty-two of them, and a trap is a `TrapView` and nothing in
the tile. A `Tile.TRAP` would have been a copy of the trap bit read from raw terrain, which is
what option 11 rejected, and the terrain is not even what the trap layer draws: the layer keys on
the trap's own `visible` flag (`TerrainFeaturesTilemap.java:57-62`), which `Trap.reveal()` and
`Trap.hide()` set without touching the terrain (`…/levels/traps/Trap.java:76-90`). The corpus pin
moved with the tiles under version 1: nothing outside this story had encoded an Observation, so
there was no reader to break; from the merge on, a pin move is a version bump.

**The records refuse what the fog would not draw.** An unknown cell carries `Tile.NONE` and
nothing else does; traps, heaps and transitions stand on cells the player has seen; a blob stands
in view, since the emitter draws one only where the hero sees (`…/effects/BlobEmitter.java:62-64`),
and the two blobs the game marks always visible, drawn under the fog of a remembered cell
(`…/actors/mobs/Tengu.java:850`, `:1045`; `…/items/artifacts/SkeletonKey.java:475`), have no
representation in this version; an actor stands in view; a container shows no item, a price
belongs to a for-sale heap only and a category to a crystal chest only. ADR-0006's whitelist is a
whitelist by construction, which `SchemaRulesTest` holds. What the records cannot check is the
free text: a trap's kind, a heap's item, a blob's kind, an actor's or a buff's name are strings,
and the Observer stories' leak tests must pin each to the name the screen shows.

**Health is quantised to a convention the screen exceeds.** The bar over a sprite is the sprite's
width times four sixths (`…/ui/CharHealthIndicator.java:50-51`), drawn to the pixel of the camera
zoom with the lit part rounded up (`…/ui/HealthBar.java:66-69`), so its resolution depends on the
sprite, ten to twenty-seven units wide at the tag, and on the zoom, from 1
(`…/scenes/PixelScene.java:144`): eight pixels for a twelve-unit sprite at zoom 1, eleven for a
sixteen-unit one. No single constant is "the bar". The codec states `W = 32/3`, the sixteen-unit
bar at zoom 1, and `healthPips` is `ceil(hp / max * W)` in integer arithmetic, 0 to 11. That is
fair by a different window: examining a character shows a bar about a hundred UI units wide at a
UI zoom of at least two (`…/windows/WndInfoMob.java:58-59`, `:72`, `:77`;
`…/windows/WndTitledMessage.java:32`; `PixelScene.java:133-137`, `:150`), which resolves every
point of health of every character that is not a boss, and a boss's bar prints its health as a
number (`…/ui/BossHealthBar.java:205-206`). So the screen offers exact health; ADR-0006's "never
exact HP" is stricter than the screen, and the schema keeps its quantisation as a loss to the
brain, never a leak. Two drawn bits are dropped the same way: the bar hidden at full health
(`CharHealthIndicator.java:55`) and the shield segment (`HealthBar.java:68`). Story 1.9 may loosen
this with a version bump.

**The hero is not an actor.** The actors section carries every visible character but the hero,
whose cell and health belong to the hero section of story 1.7.

**`Feeling.SECRETS` stays.** The floor feeling's text is logged on arrival
(`…/scenes/GameScene.java:663-685`) and its title, "secrets floor", heads the window the menu pane
opens for it (`…/ui/MenuPane.java:112-115`; `levels.properties:260`), so the member names an
announcement, not the secrets; the enum test names it as the one exception to "no member contains
SECRET".

**Buff turns are hundredths.** The description prints turns with `#.##`
(`…/actors/buffs/Buff.java:136-138`), so a buff carries hundredths of a turn and whether turns are
shown at all.

**The version is 1 and pinned.** `ObservationHashTest` holds the corpus Observation's hash to a
constant, so any change to the encoding is a change to the version, recorded here, before the pin
moves. `HeaderSection.version` is the schema version, which the codec refuses to encode unless it
is its own.

## Amendment: story 1.7 (2026-09-05)

The hero, the inventory, the journal, the log, the valid Actions and the Prompt are records,
`Observation` is the nine sections in the table's order, the readable form is `ObservationJson`
over a `JsonWriter` the Run log shares, and the Belief is `Belief`. The schema version is 2.
These are the choices the decision left open or did not foresee.

**The hero section carries what five views draw.** The cell; the name the hero window titles
(`…/windows/WndHero.java:162`; `…/actors/hero/Hero.java:417`); the subclass, which names the hero
once chosen (`Hero.java:412-414`); the armour ability, drawn as the fourth talent tier and the
action indicator (`Hero.java:209`, `:390`, `:402`); the level (`WndHero.java:160`); the experience
and what the next level needs, the exp bar's fill and its text on either pane
(`…/ui/StatusPane.java:334-345`; `WndHero.java:195`); the health, its maximum and the shielding exactly as the status pane
prints them (`StatusPane.java:322-327`; `WndHero.java:193-194`), so the hero's health is exact
where an actor's is quantised, because the pane prints the number; the strength and the bonus or
penalty printed after it (`WndHero.java:190-192`); the gold and the alchemical energy the bag
window prints (`…/windows/WndBag.java:186`, `:179`, `:219`), which the table did not list;
hunger as the icon's three states (`…/actors/buffs/Hunger.java:179-187`); every buff with an icon,
ordered by name, then timed, then turns, as an actor's are (`WndHero.java:301-314`;
`…/ui/BuffIndicator.java:192-196`); every talent of every tier the pane shows with its points, and
the unspent points per tier drawn as open stars (`…/ui/TalentsPane.java:75-84`, `:183`, `:259`;
`Hero.java:210`, `:387-396`), where the pane shows a tier from one level below its threshold, the
third only with a subclass and the fourth only with an ability, while the hero holds the first two
tiers from creation (`…/actors/hero/Talent.java:968-970`), so the section carries what is drawn;
and the six quickslots with the placeholder flag
(`…/QuickSlot.java:36-41`; `…/ui/QuickSlotButton.java:306`). The danger count of ADR-0006 is not a
field: it is the enemies among the actors, which that section lists with the invisible flag.

**The inventory is positional, in the belongings' order.** The equipped items in slot order, then
the backpack (`…/actors/hero/Belongings.java:428-429`, `:446-453`), which the record enforces,
because an `ItemRef` (ADR-0014, option 11) is a position in it. An item carries the family its
sprite and bag show (`ItemKind`, one member per item package of the tag), the display name, the
quantity, the level and curse flags with their visible values, the status text, the slot, the
actions the item window offers and the default action (`…/items/Item.java:110-115`, `:179-181`,
`:433-451`, `:483-499`, `:538`, `:570-572`; `…/windows/WndUseItem.java:54-76`). The sprite index
the ADR-0006 row whitelists is not carried: the name says what the sprite draws.

**The journal is notes and known appearances.** The notes tab's three records, landmark, keys and
a written note, by depth then kind, title, text and count (`…/journal/Notes.java:73-100`,
`:115-143`, `:145-151`, `:296-306`, `:375-420`); and the potions, scrolls and rings identified this
Run by their true names (`…/items/potions/Potion.java:402-404`). The guide's pages and the
bestiary are not here: static text the Codex carries, and cross-Run state.

**The log is the messages as emitted, capped at sixty-four.** The tone comes from the prefix
(`…/utils/GLog.java:32-39`; `…/ui/GameLog.java:72-87`) and the text is what follows it; the
new-line marker is dropped, and the merging of same-colour messages is the pane's. The source is
the raw signal every message goes through (`GLog.java:39`), which is the game log non-negotiable 1
names beside the renderer, and the reproducible choice, since frame timing is not part of the Run
tuple. The pane is a view of it: it takes a frame's messages in one batch, merges, and trims the
oldest entries beyond three or five lines of text before the frame is drawn (`GameLog.java:55-131`,
`:59`, `:89`, `:107-122`), so a burst that exceeds the lines in one frame loses its oldest
messages before they are ever drawn, and the Observation may carry a message the pane never
showed. That is the trade-off recorded here: the signal over the pane, for reproducibility, at
the cost of a line a human may not have read in a burst. The cap is a bound on the Observation's
size, not a claim about the pane. For story 1.10: the pane replaces the signal's listener and
buffers statically (`GameLog.java:47`, `:52`), so the Observer's capture must equal what the pane
receives, no more. Story 1.10 captures it through hook row 3, a site right after the pane's
construction, so the listener hears what the pane hears, the floor's own lines included, and
joins the signal again at the start of every Run, before the first floor is built. One thing the
pane does that the signal does not carry: `GameLog.wipe()` empties the pane with no message on
the signal (`…/items/journal/Guidebook.java:57` on picking up the guidebook;
`…/windows/WndSettings.java:1093`), and the section keeps its lines after it; those lines were
on the screen before the wipe, so this is memory the human also has, not hidden information
(ADR-0006's amendment for that story).

**The valid Actions are a section, and the Observation is built in two steps.** `Action` is the
sealed interface of ADR-0014 with one record per kind, amended there: item use is three kinds by
the shape of its target, a window of options an item opens being a Prompt at its own Input wait,
and `MoveTo` records a human's click. `ActionsSection` sorts by kind and then by the action's own
bytes, refuses repeats and refuses `MoveTo`, so a set enumerated in any order is one section with
one hash and a human's click is never a valid Action. The table's circularity, a valid set computed from the Observation and
part of it, is resolved by `ActionsSection.NONE` and `Observation.withActions`: the Observer builds
the record without Actions, story 1.12's `validActions` reads that, and the record with the set is
what is hashed. The Observation refuses an Action naming what it does not carry: a cell off the
map, an item reference whose index, name or quantity the inventory does not list, an item action
the item does not offer, an answer past the prompt's options, a talent the hero section does not
list, an ability the hero does not have. That is ADR-0014's rule that every parameter is a value
the Observation carries, as a constructor check rather than an executor's.

**The prompt section is the kind, the title, the text and the button labels in drawing order**
(`…/windows/WndOptions.java:57`, `:92`). With no Prompt open it is empty, and the header's kind
must equal the section's, which the Observation holds. The richer windows, a trade or a subclass
choice, are flattened into labels by story 1.10: the first text block is the title when a window
draws two, the rest the text, and the styled buttons' labels the options, in drawing order
(ADR-0006's amendment for that story).

**The readable form is canonical JSON.** `JsonWriter` sorts an object's keys by their UTF-16 code
units whatever order they were given in, writes no whitespace, writes integers only, and escapes
only what JSON requires; `ObservationJson` renders the hash, the section hashes and the nine
sections, each record an object keyed by its component names, each enum its name, each Action
with a `kind` key. It is derived from the records and never hashed, and nothing in `api` reads it
back: `JsonRenderingTest` scans the module for a method that turns text or bytes into a record of
the schema and finds none, and reads the rendering with a strict reader that accepts only the
canonical shape. ADR-0011's Run log uses the same writer.

**The Belief is a class, not a record.** A record over a byte array would compare by identity, so
`Belief(version, bytes)` copies its bytes in and out, is equal by content, and hashes SHA-256 over
the version and the bytes; the harness logs a Belief it cannot read (AD-14). No record of the
schema has a component of that type, which the reflection test holds.

**The version is 2 and pinned.** Every section's bytes are new relative to version 1, and the pin
moved with them. Story 1.6's rule stands: from here, a pin move is a version bump.
