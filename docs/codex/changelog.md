# Changelog

!!! info "Generated"

    From `codex/v4.0.0/changelog.json` at upstream tag `v4.0.0`, Codex version 8.
    Never hand edited: run `./gradlew :codex:generate` and commit what it writes.

Every entry of the game's changelist with the headings under it, the date where an entry or a
heading states one, and the version the tree builds as beside the save codes it still reads
(story 2.7).

[`codex/v4.0.0/changelog.json`](https://github.com/watchthelight/shatterfish/blob/main/codex/v4.0.0/changelog.json) holds 199 entries in 196.7 KB. The entries are not repeated here: they are committed, diffable and cited in that file.
An [entry](index.md#the-tables) is an object in one of the table's own lists; a number the
table states beside them is a value, and the values are below.

## What the table states of itself

The values the table carries beside its entries, by the path each is reached at.

| Field | Type | Value |
|---|---|---|
| `versionCitation` | citation | `build.gradle:19` |
| `versionCode` | number | `912` |
| `versionName` | string | `4.0.0` |

## How a row is shaped

### `entries`

193 entries. Every field any entry of this list holds, by the path it is reached at
(`[]` is a list's element):

| Field | Type |
|---|---|
| `citation` | object |
| `citation.line` | number |
| `citation.path` | string |
| `className` | string |
| `conditionExpression` | string |
| `dates` | list |
| `headings` | list |
| `headings[]` | object |
| `headings[].citation` | object |
| `headings[].citation.line` | number |
| `headings[].citation.path` | string |
| `headings[].conditionExpression` | string |
| `headings[].dates` | list |
| `headings[].title` | string |
| `headings[].titleExpression` | string |
| `headings[].titleKey` | string |
| `major` | boolean |
| `tab` | number |
| `text` | string |
| `title` | string |
| `titleExpression` | string |
| `titleKey` | string |
| `headings[].dates[]` | string |

### `saveCodes`

6 entries. Every field any entry of this list holds, by the path it is reached at
(`[]` is a list's element):

| Field | Type |
|---|---|
| `citation` | object |
| `citation.line` | number |
| `citation.path` | string |
| `expression` | string |
| `what` | string |

## Citations

Every value above is cited to the pinned tree: the line it was read from, or, where a table
is measured rather than transcribed, the method that was run. The reader
recorded 788 citations in 16 files:

| Source | Citations | Line span |
|---|---:|---|
| [`build.gradle`](https://github.com/watchthelight/shatterfish/blob/main/build.gradle) | 1 | 19 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/ShatteredPixelDungeon.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/ShatteredPixelDungeon.java) | 6 | 37-46 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/ui/changelist/Pixel_Dungeon_Changes.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/ui/changelist/Pixel_Dungeon_Changes.java) | 9 | 36-571 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/ui/changelist/v0_1_X_Changes.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/ui/changelist/v0_1_X_Changes.java) | 11 | 32-108 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/ui/changelist/v0_2_X_Changes.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/ui/changelist/v0_2_X_Changes.java) | 27 | 34-248 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/ui/changelist/v0_3_X_Changes.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/ui/changelist/v0_3_X_Changes.java) | 29 | 34-315 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/ui/changelist/v0_4_X_Changes.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/ui/changelist/v0_4_X_Changes.java) | 19 | 34-224 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/ui/changelist/v0_5_X_Changes.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/ui/changelist/v0_5_X_Changes.java) | 6 | 38-68 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/ui/changelist/v0_6_X_Changes.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/ui/changelist/v0_6_X_Changes.java) | 116 | 43-872 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/ui/changelist/v0_7_X_Changes.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/ui/changelist/v0_7_X_Changes.java) | 126 | 43-897 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/ui/changelist/v0_8_X_Changes.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/ui/changelist/v0_8_X_Changes.java) | 71 | 40-672 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/ui/changelist/v0_9_X_Changes.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/ui/changelist/v0_9_X_Changes.java) | 64 | 41-610 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/ui/changelist/v1_X_Changes.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/ui/changelist/v1_X_Changes.java) | 106 | 42-963 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/ui/changelist/v2_X_Changes.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/ui/changelist/v2_X_Changes.java) | 103 | 45-1447 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/ui/changelist/v3_X_Changes.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/ui/changelist/v3_X_Changes.java) | 68 | 42-894 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/ui/changelist/v4_X_Changes.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/ui/changelist/v4_X_Changes.java) | 26 | 45-286 |

## Judgments

The reader named no reason in this table: every entry is a fact the pinned tree states, and
nothing was left out or decided.
