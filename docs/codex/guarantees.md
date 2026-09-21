# Guarantees

!!! info "Generated"

    From `codex/v4.0.0/guarantees.json` at upstream tag `v4.0.0`, Codex version 8.
    Never hand edited: run `./gradlew :codex:generate` and commit what it writes.

Every limited drop the level's creation decides -- the strength potions, the upgrade scrolls,
the styli, the two stones, the trinket catalyst, the laboratory -- as the game's method text and
as a schedule: the exact chance, for every depth and counter state, that the drop is needed and
placed, what level class each floor of the main branch is and whether it places the floor's
spawn list at all, and the Forbidden Runes rule (story 2.4).

[`codex/v4.0.0/guarantees.json`](https://github.com/watchthelight/shatterfish/blob/main/codex/v4.0.0/guarantees.json) holds 33 entries in 130.3 KB. The entries are not repeated here: they are committed, diffable and cited in that file.
An [entry](index.md#the-tables) is an object in one of the table's own lists; a number the
table states beside them is a value, and the values are below.

## What the table states of itself

The values the table carries beside its entries, by the path each is reached at.

| Field | Type | Value |
|---|---|---|
| `bossCitation` | citation | `Dungeon.java:441` |
| `bossDepths` | list of number | `5, 10, 15, 20, 25` |
| `counters` | list of string | [below](#counters) |
| `countersCitation` | citation | `Dungeon.java:104` |
| `gateExpression` | string | `if (!Dungeon.bossLevel() && Dungeon.branch == 0) {` |
| `noScrollsCitation` | citation | `Level.java:238` |
| `noScrollsExpression` | string | [below](#noscrollsexpression) |
| `placementCitation` | citation | `Level.java:224` |

#### `counters`

```
STRENGTH_POTIONS, UPGRADE_SCROLLS, ARCANE_STYLI, ENCH_STONE, INT_STONE, TRINKET_CATA, LAB_ROOM, SWARM_HP, NECRO_HP, BAT_HP, WARLOCK_HP, COOKING_HP, BLANDFRUIT_SEED, SLIME_WEP, SKELE_WEP, THEIF_MISC, GUARD_ARM, SHAMAN_WAND, DM200_EQUIP, GOLEM_EQUIP, VELVET_POUCH, SCROLL_HOLDER, POTION_BANDOLIER, MAGICAL_HOLSTER, LORE_SEWERS, LORE_PRISON, LORE_CAVES, LORE_CITY, LORE_HALLS
```

#### `noScrollsExpression`

```
if (!Dungeon.isChallenged(Challenges.NO_SCROLLS) || Dungeon.LimitedDrops.UPGRADE_SCROLLS.count%2 != 0){
```

## How a row is shaped

### `drops`

7 entries. Every field any entry of this list holds, by the path it is reached at
(`[]` is a list's element):

| Field | Type |
|---|---|
| `citation` | object |
| `citation.line` | number |
| `citation.path` | string |
| `entries` | list |
| `entries[]` | object |
| `entries[].count` | number |
| `entries[].depth` | number |
| `entries[].neededPerMille` | number |
| `entries[].placedNoScrollsPerMille` | number |
| `entries[].placedPerMille` | number |
| `expression` | string |
| `item` | string |
| `method` | string |
| `name` | string |
| `once` | boolean |
| `perSet` | number |
| `placementCitation` | object |
| `placementCitation.line` | number |
| `placementCitation.path` | string |

### `placements`

26 entries. Every field any entry of this list holds, by the path it is reached at
(`[]` is a list's element):

| Field | Type |
|---|---|
| `citation` | object |
| `citation.line` | number |
| `citation.path` | string |
| `depth` | number |
| `levelClass` | string |
| `placesSpawnList` | boolean |

## Citations

Every value above is cited to the pinned tree: the line it was read from, or, where a table
is measured rather than transcribed, the method that was run. The reader
recorded 44 citations in 3 files:

| Source | Citations | Line span |
|---|---:|---|
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/Dungeon.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/Dungeon.java) | 35 | 104-589 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/Level.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/Level.java) | 8 | 224-254 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/SpecialRoom.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/SpecialRoom.java) | 1 | 135 |

## Judgments

The reader named no reason in this table: every entry is a fact the pinned tree states, and
nothing was left out or decided.
