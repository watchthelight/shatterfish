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

## What the table states of itself

The values the table carries beside its entries, by the path each is reached at.

| Field | Type | Value |
|---|---|---|
| `bossCitation` | citation | `Dungeon.java:441` |
| `bossDepths` | list of number | `5, 10, 15, 20, 25` |
| `counters` | list of string | -- |
| `countersCitation` | citation | `Dungeon.java:104` |
| `gateExpression` | string | `if (!Dungeon.bossLevel() && Dungeon.branch == 0) {` |
| `noScrollsCitation` | citation | `Level.java:238` |
| `noScrollsExpression` | string | -- |
| `placementCitation` | citation | `Level.java:224` |

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

Every value above was read from the pinned tree at the line the entry carries. The reader
recorded 44 citations in 3 files:

| Source | Citations | Lines |
|---|---:|---|
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/Dungeon.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/Dungeon.java) | 35 | 104-589 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/Level.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/Level.java) | 8 | 224-254 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/SpecialRoom.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/SpecialRoom.java) | 1 | 135 |

## Judgments

The reader named no reason in this table: every entry is a fact the pinned tree states, and
nothing was left out or decided.
