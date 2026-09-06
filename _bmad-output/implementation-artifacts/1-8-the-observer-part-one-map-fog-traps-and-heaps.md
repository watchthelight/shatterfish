---
story: 1.8
key: 1-8-the-observer-part-one-map-fog-traps-and-heaps
title: "The Observer, part one: map, fog, traps and heaps"
epic: 1
issue: 21
status: review
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
| Given the rows of ADR-0006 for cell visibility, terrain, traps and heaps, when the Observer builds the map section from the game's own drawing predicates | **Met.** `Observer.map()` in `org.shatterfish.harness.observer` paints the fog as the fog of war does, wall rule included, and `FogParityTest` holds every cell to the painted texture; it maps `map[]` through the tile sheet's own tables, and takes traps by `visible` and heaps by `seen` |
| A secret door reads as a wall and a secret trap as floor, and neither is identifiable | **Met.** The terrain reaches its tile through `DungeonTileSheet.directVisuals` and `directFlatVisuals`, where the secrets carry their cover's visual; `TerrainTableTest` and `MapLeakTest` hold it, and the serialized Observation carries no "SECRET" and not the hidden trap's name |
| A trap that is visible but sits on a cell whose fog is unknown does not appear | **Met.** A trap appears only when `visible` and its cell's fog is not unknown; `MapLeakTest` reveals a trap on an unvisited cell, finds it absent, maps the cell and finds it present |
| A container heap exposes only its container type, except a crystal chest, which names its category, and a plain heap exposes its current top item | **Met.** The item is the top item's title for a plain or for-sale heap only, the category is the words the crystal chest's description prints, the price is the single for-sale item's as the heap's title prints it; `MapLeakTest` holds a locked chest's potion of strength and a crystal chest's wand absent from the serialization |
| `MapLeakTest` constructs a world with a secret door, a hidden trap, a pre-revealed trap on an unvisited cell and a locked chest, and asserts none is identifiable in the serialized Observation | **Met.** `MapLeakTest` builds each on the first floor of a seeded Run and searches the JSON and the canonical bytes for every hidden name |
| Each row implemented is added to `docs/rules/visibility.md` with this test named in its Test column | **Met.** Seven rows gain the test names and four rows are added for what the story read |

## What was built

- `shatterfish/harness/src/main/java/org/shatterfish/harness/observer/Observer.java`: the one door, with `header()` and `map()`, the Input-wait assertion, the visual-to-tile table, the fog rule and the trap and heap rules.
- Tests in `shatterfish/harness/src/test/java/org/shatterfish/harness/observer/`: `MapLeakTest` (four scenes on a seeded floor), `FogParityTest` (every cell against the painted fog texture in five scenes, and two readings of one wait byte-equal), `TerrainTableTest` (every constant of `Terrain`), `ObserverGateTest` (the header and the gate), `Skeleton` (a test-side Observation around the two sections).
- `HeadlessDriver.heroWaits(Hero)`: the driver's Input-wait condition on the hero, now shared with the Observer's gate.
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

**The fog has a gate before its four levels, and paints walls by their neighbours.** A cell that
cannot be discovered is painted opaque whatever `visited` or `mapped` say
(`…/tiles/FogOfWar.java:200-205`), the four levels follow for any cell that is not a wall
(`:288-299`), and a wall cell is painted by the cells its face belongs to (`:210-267`): the
darkest of itself and the cell below for a wall over floor, two halves from the side neighbours
for a wall over a wall. So a room's far wall is opaque until the corridor beyond is seen, even
while the wall is in the hero's field of view. The first draft emitted such a wall as in view;
the review found it, and the fog now mirrors the painting. One step past the paint stays: a
discoverable wall painted opaque that is visited or mapped is emitted at that level, since the
examine window opens on any visited or mapped cell and draws its tile
(`…/scenes/GameScene.java:1661-1667`; `…/windows/WndInfoCell.java:42-74`), behind the fog's own
gate, since rock is never visited or mapped in play.

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
lock. Chosen (b): every field is one drawn value, and the gate test holds them. The lock's
evidence is the `LockedFloor` buff's icon (`…/levels/Level.java:617-630`;
`…/actors/buffs/LockedFloor.java:76-78`), not the locked stairs and the boss bar ADR-0005's table
names, which are each boss level's own.

**A wall painted opaque but examinable.** Alternatives: (a) emit the painted level, unknown, and
lose the wall the player can examine; (b) emit the wall's own level, in view, more than the
player sees; (c) the examine level, visited or mapped. Chosen (c): it is exactly what the player
can learn of the cell, and the tile is what the examine window draws.

**How the fog is tested.** Alternatives: (a) recompute the rule from the arrays, which cannot see
the arrays disagree with the paint; (b) read the fog of war's texture back. Chosen (b):
`FogParityTest` reads the painted pixels through the scene and compares every cell, through the
game's own effects for blindness, mind vision and a read scroll.

## Evidence

`./gradlew build -Pshatterfish.mobile=off`: green, 397 tests across 26 suites, fourteen of them
the four observer suites. `mkdocs build --strict`: clean.

**Mutation battery**, twenty mutations of `Observer.java` on the committed tree at `8a7c2b10d`
(seventeen first run at `530d8cab8`, three added for the review's fog rule), each applied to a
clean tree, run against the four observer suites without `--rerun-tasks`, restored with
`git checkout`, and the tree verified clean after each:

| # | Mutation | Caught by |
|---|---|---|
| M1 | the fog ignores the discoverable gate | `FogParityTest` (rock stays opaque) |
| M2 | visited is read before in view | `FogParityTest` (every cell against the paint; the Observation refuses a hero on a visited cell) |
| M3 | a trap is emitted whether or not it is visible | `MapLeakTest` (secrets: the hidden trap gains a tile) |
| M4 | a trap is emitted on an unknown cell | `MapLeakTest` (the revealed trap; the map record refuses it) |
| M5 | a heap is emitted whether or not it was seen | `MapLeakTest` (fog and unseen heaps; the map record refuses it) |
| M6 | a container exposes its top item | `MapLeakTest` (containers: the locked chest's potion, the chest's torch) |
| M7 | a crystal chest names its item | `MapLeakTest` (containers: the wand's title) |
| M8 | a heap of several items shows a price | `MapLeakTest` (containers: the two-item heap) |
| M9 | a secret door is mapped by a rule of its own | `TerrainTableTest` (secrets share their cover), `MapLeakTest` (secrets) |
| M10 | the hidden flag is dropped | `MapLeakTest` (containers: the hidden heap) |
| M11 | the header never says sealed | `ObserverGateTest` (the header) |
| M12 | the Observer runs while the hero acts | `ObserverGateTest` (only at an Input wait) |
| M13 | the Observer runs under a window | `ObserverGateTest` (only at an Input wait) |
| M14 | the challenges are read inverted | `ObserverGateTest` (the header) |
| M15 | an empty heap is emitted | `MapLeakTest` (the emptied heap has no top item to title, and the test errors) |
| M16 | a trap is named by its class | `MapLeakTest` (secrets; the revealed trap) |
| M17 | a disarmed trap reads active | `MapLeakTest` (the revealed trap, disarmed) |
| M18 | the wall rule is dropped: a wall is painted its own level | `FogParityTest` (the far wall in view reads as painted) |
| M19 | the examine level is dropped: a visited wall painted opaque reads unknown | `FogParityTest` (the first wait) |
| M20 | an internal wall takes the darker half | `FogParityTest` (every cell against the paint) |

All twenty caught.

## The fairness review

Run as an isolated `fairness-reviewer` on commit `530d8cab8`. Verdict: FINDINGS, none blocking:
every read is a field the renderer reads, nothing behind an appearance, a container or the fog
reaches the section, and the RNG, the seed and the mobs are untouched. Six should-fix findings,
all taken in the review commit:

1. **The fog painted walls differently from the Observer.** The fog of war paints a wall cell by
   the cells its face belongs to (`…/tiles/FogOfWar.java:210-267`), and the wall tilemap hides the
   visual by the same rule (`…/tiles/WallBlockingTilemap.java:194-202`), so a room's far wall is
   opaque until the corridor beyond is seen while the Observer said in view. The fog now mirrors
   the painting, taking the lighter of a wall's two halves, and emits a wall painted opaque that is
   visited or mapped at that level, since the examine window opens on it and draws its tile
   (`…/scenes/GameScene.java:1661-1667`; `…/windows/WndInfoCell.java:42-74`).
2. **The fog test recomputed the Observer's formula from the same arrays**, so it could not see
   the arrays disagree with the paint, and toggles were set by hand. `FogParityTest` reads the fog
   of war's texture back through the scene and holds every cell to it at the first wait, blinded
   and with mind vision through `Buff.affect` and `Dungeon.observe()`, after a scroll of magic
   mapping read through `Scroll.execute`, and with rock marked visited by hand; and holds two
   readings of one wait byte-equal. Two Runs of one seed are story 1.16's, since the first floor
   differs between processes (story 1.3).
3. **A passive mimic drawn as a chest has no heap**, so `map()` reveals one by the absence of a
   heap under a chest sprite. Story 1.9 owns the mobs row and the differential test; until then
   `map()` is marked not for play (#22) in its Javadoc, the amendment and the limitations, and no
   brain reads it.
4. **The lock's evidence was wrong.** `Level.locked` is set by `seal()` together with the
   `LockedFloor` buff whose icon the HUD shows (`…/levels/Level.java:617-630`;
   `…/actors/buffs/LockedFloor.java:76-78`); the locked stairs and the boss bar ADR-0005's table
   names are each boss level's own. The Observer's and `HeaderSection`'s Javadocs and the
   amendment cite the buff.
5. **Two definitions of an Input wait.** The gate now uses the driver's own predicate,
   `HeadlessDriver.heroWaits`, and `GameScene.interfaceBlockingHero()`
   (`…/scenes/GameScene.java:1386-1396`), which covers a window and the inventory pane selecting.
6. **IDE output under the module.** `shatterfish/harness/bin/` is deleted and
   `shatterfish/*/bin/` ignored in the root ignore file's Shatterfish section, which the ledger
   exempts by name.

## Deviations

- `sealed` is read from `Level.locked` in this story rather than 1.11.
- No upstream file is touched; no manual `:desktop:debug` check applies.

## Known limitations, handed forward

- **No `observe()` yet**: the actors and the hero (1.9), the inventory, journal, log and Prompt (1.10), and blobs, feeling, transitions and the danger count (1.11) come first.
- **`map()` is not for play until story 1.9 (#22)**: a passive mimic has no heap and is drawn as a chest, so the absence of a heap under a chest sprite would say what the screen does not until 1.9 emits it; the Javadoc says so, and no brain reads the section yet.
- **Two Runs of one seed are not compared**: the first floor differs between processes (the unseeded guidebook page, story 1.3), so determinism across Runs is story 1.16's; two readings of one wait are held byte-equal.
- **Custom tilemap visuals** (`…/windows/WndInfoCell.java:50-63`, `:78-97`) are drawn from a custom layer the Observer does not read; the terrain beneath is what is emitted.
- **A window of any kind fails the Observer** until story 1.10 recognises the Prompt kinds.

## Follow-ups for later stories

- Story 1.9: actors and the hero; the mimic as a chest heap.
- Story 1.10: the Prompt gate; the log listener.
- Story 1.11: blobs, feeling, transitions; `observe()`; the checklist over ADR-0006's rows.
