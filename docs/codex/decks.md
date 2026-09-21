# Decks

!!! info "Generated"

    From `codex/v4.0.0/decks.json` at upstream tag `v4.0.0`, Codex version 8.
    Never hand edited: run `./gradlew :codex:generate` and commit what it writes.

The item generator's categories with their two deck weights and their classes' weights, the
three appearance-label pools, and the exotic swap with its chance (story 2.3).

[`codex/v4.0.0/decks.json`](https://github.com/watchthelight/shatterfish/blob/main/codex/v4.0.0/decks.json) holds 26 entries in 38.8 KB. The entries are not repeated here: they are committed, diffable and cited in that file.

## What the table states of itself

The values the table carries beside its entries, by the path each is reached at.

| Field | Type | Value |
|---|---|---|
| `exotic.chanceExpression` | string | -- |
| `exotic.chanceWithoutTrinketPerMille` | number | `0` |
| `exotic.citation` | citation | `ExoticCrystals.java:52` |
| `exotic.pairs` | list of object | -- |

## How a row is shaped

### `categories`

23 entries. Every field any entry of this list holds, by the path it is reached at
(`[]` is a list's element):

| Field | Type |
|---|---|
| `citation` | object |
| `citation.line` | number |
| `citation.path` | string |
| `classes` | list |
| `classes[]` | object |
| `classes[].className` | string |
| `classes[].firstDeck` | number |
| `classes[].secondDeck` | number |
| `classes[].total` | number |
| `classesCitation` | object |
| `classesCitation.line` | number |
| `classesCitation.path` | string |
| `decks` | number |
| `firstProb` | number |
| `name` | string |
| `secondProb` | number |
| `superClass` | string |
| `weightsCitation` | object |
| `weightsCitation.line` | number |
| `weightsCitation.path` | string |
| `weights2Citation` | object |
| `weights2Citation.line` | number |
| `weights2Citation.path` | string |

### `labelPools`

3 entries. Every field any entry of this list holds, by the path it is reached at
(`[]` is a list's element):

| Field | Type |
|---|---|
| `citation` | object |
| `citation.line` | number |
| `citation.path` | string |
| `family` | string |
| `labels` | list |
| `labels[]` | object |
| `labels[].citation` | object |
| `labels[].citation.line` | number |
| `labels[].citation.path` | string |
| `labels[].exoticCitation` | object |
| `labels[].exoticCitation.line` | number |
| `labels[].exoticCitation.path` | string |
| `labels[].exoticName` | string |
| `labels[].key` | string |
| `labels[].name` | string |
| `labels[].nameCitation` | object |
| `labels[].nameCitation.line` | number |
| `labels[].nameCitation.path` | string |

## Citations

Every value above was read from the pinned tree at the line the entry carries. The reader
recorded 167 citations in 6 files:

| Source | Citations | Lines |
|---|---:|---|
| [`core/src/main/assets/messages/items/items.properties`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/assets/messages/items/items.properties) | 60 | 725-1235 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/Generator.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/Generator.java) | 67 | 222-599 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/potions/Potion.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/potions/Potion.java) | 13 | 91-104 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/rings/Ring.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/rings/Ring.java) | 13 | 55-68 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/scrolls/Scroll.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/scrolls/Scroll.java) | 13 | 73-86 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/trinkets/ExoticCrystals.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/trinkets/ExoticCrystals.java) | 1 | 52 |

## Judgments

The reader named no reason in this table: every entry is a fact the pinned tree states, and
nothing was left out or decided.
