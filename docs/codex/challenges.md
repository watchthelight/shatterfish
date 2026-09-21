# Challenges

!!! info "Generated"

    From `codex/v4.0.0/challenges.json` at upstream tag `v4.0.0`, Codex version 8.
    Never hand edited: run `./gradlew :codex:generate` and commit what it writes.

The nine challenge flags in the api's order, which is the declaration order of the game's
`Challenges`, each with the bit the game stores it under. The count, the union and the set are
checked against the game's own `MAX_CHALS`, `MAX_VALUE` and `MASKS`, so a flag the game adds
stops the generation rather than going missing (story 2.1).

[`codex/v4.0.0/challenges.json`](https://github.com/watchthelight/shatterfish/blob/main/codex/v4.0.0/challenges.json) holds 9 entries in 1.3 KB. The entries are not repeated here: they are committed, diffable and cited in that file.
An [entry](index.md#the-tables) is an object in one of the table's own lists; a number the
table states beside them is a value, and the values are below.

## How a row is shaped

9 entries. Every field any entry of this list holds, by the path it is reached at
(`[]` is a list's element):

| Field | Type |
|---|---|
| `challenge` | string |
| `citation` | object |
| `citation.line` | number |
| `citation.path` | string |
| `mask` | number |

## Citations

Every value above is cited to the pinned tree: the line it was read from, or, where a table
is measured rather than transcribed, the method that was run. The reader
recorded 9 citations in 1 file:

| Source | Citations | Line span |
|---|---:|---|
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/Challenges.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/Challenges.java) | 9 | 30-38 |

## Judgments

The reader named no reason in this table: every entry is a fact the pinned tree states, and
nothing was left out or decided.
