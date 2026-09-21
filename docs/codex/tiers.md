# Tiers

!!! info "Generated"

    From `codex/v4.0.0/tiers.json` at upstream tag `v4.0.0`, Codex version 8.
    Never hand edited: run `./gradlew :codex:generate` and commit what it writes.

The floor-set tier table with the armor, weapon and missile rules that draw by it
(story 2.4).

[`codex/v4.0.0/tiers.json`](https://github.com/watchthelight/shatterfish/blob/main/codex/v4.0.0/tiers.json) holds 7 entries in 2.4 KB. The entries are not repeated here: they are committed, diffable and cited in that file.

## What the table states of itself

The values the table carries beside its entries, by the path each is reached at.

| Field | Type | Value |
|---|---|---|
| `armor.citation` | citation | `Generator.java:780` |
| `armor.expression` | string | -- |
| `armor.what` | string | `armor` |
| `citation` | citation | `Generator.java:613` |
| `gate.citation` | citation | `Generator.java:782` |
| `gate.expression` | string | -- |
| `gate.what` | string | `gate` |
| `missile.citation` | citation | `Generator.java:842` |
| `missile.expression` | string | -- |
| `missile.what` | string | `missile` |
| `weapon.citation` | citation | `Generator.java:809` |
| `weapon.expression` | string | -- |
| `weapon.what` | string | `weapon` |

## How a row is shaped

### `arrays`

2 entries. Every field any entry of this list holds, by the path it is reached at
(`[]` is a list's element):

| Field | Type |
|---|---|
| `citation` | object |
| `citation.line` | number |
| `citation.path` | string |
| `expression` | string |
| `what` | string |

### `rows`

5 entries. Every field any entry of this list holds, by the path it is reached at
(`[]` is a list's element):

| Field | Type |
|---|---|
| `depthFrom` | number |
| `depthTo` | number |
| `floorSet` | number |
| `weights` | list |
| `weights[]` | number |

## Citations

Every value above was read from the pinned tree at the line the entry carries. The reader
recorded 7 citations in 1 file:

| Source | Citations | Lines |
|---|---:|---|
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/Generator.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/Generator.java) | 7 | 613-842 |

## Judgments

The reader named no reason in this table: every entry is a fact the pinned tree states, and
nothing was left out or decided.
