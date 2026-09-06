---
story: 1.8
key: 1-8-the-observer-part-one-map-fog-traps-and-heaps
title: "The Observer, part one: map, fog, traps and heaps"
epic: 1
issue: 21
status: in-progress
created: '2026-09-06'
updated: '2026-09-06'
---

# Story 1.8: The Observer, part one: map, fog, traps and heaps

As the bot,
I want the terrain, fog, visible traps and seen heaps exactly as the screen draws them,
So that I can navigate without seeing what the player cannot.

Paths abbreviate `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/` as `…/`, and every
line number is at the pinned tag `v3.3.8` (commit `7b8b845a`).

## Acceptance criteria and how each was met

| Criterion | Outcome |
|---|---|
| Given the rows of ADR-0006 for cell visibility, terrain, traps and heaps, when the Observer builds the map section from the game's own drawing predicates | **Met.** `Observer.map()` in `org.shatterfish.harness.observer` reads `heroFOV`, `visited`, `mapped` and `discoverable` as the fog of war does, maps `map[]` through the tile sheet's own tables, and takes traps by `visible` and heaps by `seen` |
| A secret door reads as a wall and a secret trap as floor, and neither is identifiable | **Met.** The terrain reaches its tile through `DungeonTileSheet.directVisuals` and `directFlatVisuals`, where the secrets carry their cover's visual; `TerrainTableTest` and `MapLeakTest` hold it, and the serialized Observation carries no "SECRET" and not the hidden trap's name |
| A trap that is visible but sits on a cell whose fog is unknown does not appear | **Met.** A trap appears only when `visible` and its cell's fog is not unknown; `MapLeakTest` reveals a trap on an unvisited cell, finds it absent, maps the cell and finds it present |
| A container heap exposes only its container type, except a crystal chest, which names its category, and a plain heap exposes its current top item | **Met.** The item is the top item's title for a plain or for-sale heap only, the category is the words the crystal chest's description prints, the price is the single for-sale item's as the heap's title prints it; `MapLeakTest` holds a locked chest's potion of strength and a crystal chest's wand absent from the serialization |
| `MapLeakTest` constructs a world with a secret door, a hidden trap, a pre-revealed trap on an unvisited cell and a locked chest, and asserts none is identifiable in the serialized Observation | **Met.** `MapLeakTest` builds each on the first floor of a seeded Run and searches the JSON and the canonical bytes for every hidden name |
| Each row implemented is added to `docs/rules/visibility.md` with this test named in its Test column | **Met.** Seven rows gain the test names and four rows are added for what the story read |

## What was built

- `shatterfish/harness/src/main/java/org/shatterfish/harness/observer/Observer.java`: the one door, with `header()` and `map()`, the Input-wait assertion, the visual-to-tile table, the fog rule and the trap and heap rules.
- Tests in `shatterfish/harness/src/test/java/org/shatterfish/harness/observer/`: `MapLeakTest` (four scenes on a seeded floor), `TerrainTableTest` (every constant of `Terrain`), `ObserverGateTest` (the header and the gate), `Skeleton` (a test-side Observation around the two sections).
- Docs: ADR-0006 amendment for story 1.8; `docs/rules/visibility.md`.

## What the story found

**The sheet decides what a secret looks like, not the Observer.** The tilemap takes a terrain's
visual from the sheet's direct table, then stitches water and chasm, then reads the flat table
(`…/tiles/DungeonTerrainTilemap.java:42-56`). The Observer keeps one table from the sheet's visual
constants to `Tile` and lets the game's tables route each terrain to it, so a later tag that
draws a terrain differently changes the Observation the same way, and a terrain in no table fails.

**The mine's crystal and boulder are one sprite.** `FLAT_MINE_CRYSTAL` and `FLAT_MINE_BOULDER` are
the same index, and so are the raised and overhang sets (`…/tiles/DungeonTileSheet.java:211-216`,
`:311-316`, `:397-402`); the mining level names them apart (`…/levels/MiningLevel.java:227-235`).
They are the one pair mapped by terrain rather than by visual, since the cell's name is on the
screen too.

**The fog has a gate before its four levels.** A cell that cannot be discovered is painted opaque
whatever `visited` or `mapped` say (`…/tiles/FogOfWar.java:200-205`), and the four levels follow
(`:288-299`).

**A for-sale heap of one entry prints its price in its title, and a stacked item is one entry.**
`Heap.title()` prints the price before the top item's title when the heap holds one entry, and
only the title when it holds several items (`…/items/Heap.java:368-376`; `items.properties:2354`);
the entry count is `items.size()`, not quantity, so a stack of three potions is one entry whose
price, `value()` scaling with quantity, is the stack's. ADR-0006's row said "a stack of several
shows none"; the first draft of the test believed it and failed; the amendment corrects the row.

**An empty heap is blank.** The sprite shows nothing for a heap with no items
(`…/sprites/ItemSprite.java:213-215`), so the Observer skips one.

## Decisions taken inside the story

**Where the Observer lives and what it exposes.** Alternatives: (a) `observe()` from this story,
with stand-in sections; (b) one method per section as its story lands, `observe()` when all do;
(c) `observe()` returning a partial record type. Chosen (b): the Observer never emits a section it
cannot build, and a test wraps the built sections in its own skeleton to serialize them.
Pre-mortem: a caller relying on `observe()` before 1.11; none exists.

**The mapping's shape.** Alternatives: (a) a switch from terrain to tile written by hand;
(b) the game's tables to a visual, then one table from visual to tile. Chosen (b): the secrets'
covers come from the game, the table is a function (the constructor refuses a visual named twice),
and `TerrainTableTest` proves every terrain and every tile is covered.

**The header now.** Alternatives: (a) leave the header to 1.11; (b) build it, including the boss
lock. Chosen (b): every field is one drawn value, and the gate test holds them.

## Evidence

Pending.

## The fairness review

Pending.

## Deviations

- `sealed` is read from `Level.locked` in this story rather than 1.11.
- No upstream file is touched; no manual `:desktop:debug` check applies.

## Known limitations, handed forward

- **No `observe()` yet**: the actors and the hero (1.9), the inventory, journal, log and Prompt (1.10), and blobs, feeling, transitions and the danger count (1.11) come first.
- **A passive mimic is not yet a chest heap**: story 1.9's mobs row and `MimicDifferentialTest`.
- **Custom tilemap visuals** (`…/windows/WndInfoCell.java:50-63`, `:78-97`) are drawn from a custom layer the Observer does not read; the terrain beneath is what is emitted.
- **A window of any kind fails the Observer** until story 1.10 recognises the Prompt kinds.

## Follow-ups for later stories

- Story 1.9: actors and the hero; the mimic as a chest heap.
- Story 1.10: the Prompt gate; the log listener.
- Story 1.11: blobs, feeling, transitions; `observe()`; the checklist over ADR-0006's rows.
