# Hero classes

!!! info "Generated"

    From `codex/v4.0.0/hero-classes.json` at upstream tag `v4.0.0`, Codex version 8.
    Never hand edited: run `./gradlew :codex:generate` and commit what it writes.

The six hero classes in the game's declaration order, each with its subclasses in the order
the constructor lists them (story 2.1).

[`codex/v4.0.0/hero-classes.json`](https://github.com/watchthelight/shatterfish/blob/main/codex/v4.0.0/hero-classes.json) holds 6 entries in 1.1 KB. The entries are not repeated here: they are committed, diffable and cited in that file.
An [entry](index.md#the-tables) is an object in one of the table's own lists; a number the
table states beside them is a value, and the values are below.

## How a row is shaped

6 entries. Every field any entry of this list holds, by the path it is reached at
(`[]` is a list's element):

| Field | Type |
|---|---|
| `citation` | object |
| `citation.line` | number |
| `citation.path` | string |
| `heroClass` | string |
| `subclasses` | list |
| `subclasses[]` | string |

## Citations

Every value above is cited to the pinned tree: the line it was read from, or, where a table
is measured rather than transcribed, the method that was run. The reader
recorded 6 citations in 1 file:

| Source | Citations | Line span |
|---|---:|---|
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/hero/HeroClass.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/hero/HeroClass.java) | 6 | 87-92 |

## Judgments

The reader named no reason in this table: every entry is a fact the pinned tree states, and
nothing was left out or decided.
