# Levels

!!! info "Generated"

    From `codex/v4.0.0/levels.json` at upstream tag `v4.0.0`, Codex version 8.
    Never hand edited: run `./gradlew :codex:generate` and commit what it writes.

What every depth of every branch builds and what an unnamed depth builds, which floors place a
shop and where that was decided, which are boss floors, which seal behind the hero and how, and
the level feelings with their chances, their arms and every place the game reads them
(story 2.6).

[`codex/v4.0.0/levels.json`](https://github.com/watchthelight/shatterfish/blob/main/codex/v4.0.0/levels.json) holds 44 entries in 21.8 KB. The entries are not repeated here: they are committed, diffable and cited in that file.

## What the table states of itself

The values the table carries beside its entries, by the path each is reached at.

| Field | Type | Value |
|---|---|---|
| `bossCitation` | citation | `Dungeon.java:441` |
| `bossExpression` | string | -- |
| `feelingCitation` | citation | `Level.java:262` |
| `feelingGate` | string | -- |
| `otherwiseCitation` | citation | `Dungeon.java:354` |
| `otherwiseClass` | string | `levels.DeadEndLevel` |
| `roomsCitation` | citation | `RegularLevel.java:124` |
| `sealedCitation` | citation | `Level.java:181` |
| `shopCitation` | citation | `Dungeon.java:433` |
| `shopExpression` | string | -- |

## How a row is shaped

### `feelings`

8 entries. Every field any entry of this list holds, by the path it is reached at
(`[]` is a list's element):

| Field | Type |
|---|---|
| `chancePerMille` | number |
| `citation` | object |
| `citation.line` | number |
| `citation.path` | string |
| `effects` | list |
| `expression` | string |
| `what` | string |
| `effects[]` | object |
| `effects[].citation` | object |
| `effects[].citation.line` | number |
| `effects[].citation.path` | string |
| `effects[].expression` | string |
| `effects[].what` | string |

### `levels`

34 entries. Every field any entry of this list holds, by the path it is reached at
(`[]` is a list's element):

| Field | Type |
|---|---|
| `boss` | boolean |
| `branch` | number |
| `citation` | object |
| `citation.line` | number |
| `citation.path` | string |
| `depth` | number |
| `levelClass` | string |
| `sealed` | boolean |
| `sealedBy` | string |
| `shop` | boolean |
| `shopCitation` | object |
| `shopCitation.line` | number |
| `shopCitation.path` | string |
| `sealCitation` | object |
| `sealCitation.line` | number |
| `sealCitation.path` | string |

### `otherFeelingSources`

2 entries. Every field any entry of this list holds, by the path it is reached at
(`[]` is a list's element):

| Field | Type |
|---|---|
| `citation` | object |
| `citation.line` | number |
| `citation.path` | string |
| `expression` | string |
| `what` | string |

## Citations

Every value above was read from the pinned tree at the line the entry carries. The reader
recorded 121 citations in 18 files:

| Source | Citations | Lines |
|---|---:|---|
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/Dungeon.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/Dungeon.java) | 37 | 309-441 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/CavesBossLevel.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/CavesBossLevel.java) | 2 | 73-288 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/CavesLevel.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/CavesLevel.java) | 2 | 111-112 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/CityBossLevel.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/CityBossLevel.java) | 2 | 59-319 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/CityLevel.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/CityLevel.java) | 2 | 119-120 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/HallsBossLevel.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/HallsBossLevel.java) | 2 | 62-246 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/HallsLevel.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/HallsLevel.java) | 2 | 113-114 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/LastLevel.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/LastLevel.java) | 1 | 45 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/Level.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/Level.java) | 16 | 181-774 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/MiningLevel.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/MiningLevel.java) | 4 | 91 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/PrisonBossLevel.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/PrisonBossLevel.java) | 2 | 71-431 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/PrisonLevel.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/PrisonLevel.java) | 2 | 111-112 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/RegularLevel.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/RegularLevel.java) | 29 | 124-481 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/SewerBossLevel.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/SewerBossLevel.java) | 2 | 85-177 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/SewerLevel.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/SewerLevel.java) | 2 | 104-105 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/VaultLevel.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/VaultLevel.java) | 8 | 149-630 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/features/HighGrass.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/features/HighGrass.java) | 1 | 151 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/painters/RegularPainter.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/painters/RegularPainter.java) | 5 | 80-474 |

## Judgments

The reader named no reason in this table: every entry is a fact the pinned tree states, and
nothing was left out or decided.
