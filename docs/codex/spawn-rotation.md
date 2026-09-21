# Spawn rotation

!!! info "Generated"

    From `codex/v4.0.0/spawn-rotation.json` at upstream tag `v4.0.0`, Codex version 8.
    Never hand edited: run `./gradlew :codex:generate` and commit what it writes.

The standard spawn rotation per depth, the random families with their odds, the rare
additions, the alternates and the champion rule (story 2.2).

[`codex/v4.0.0/spawn-rotation.json`](https://github.com/watchthelight/shatterfish/blob/main/codex/v4.0.0/spawn-rotation.json) holds 43 entries in 14.0 KB. The entries are not repeated here: they are committed, diffable and cited in that file.

## What the table states of itself

The values the table carries beside its entries, by the path each is reached at.

| Field | Type | Value |
|---|---|---|
| `alternateChanceExpression` | string | `1 / 50f * RatSkull.exoticChanceMultiplier()` |
| `alternateChancePerMille` | number | `20` |
| `champion.buffs` | list of string | `Blazing, Projecting, AntiMagic, Giant, Blessed, Growing` |
| `champion.challenge` | string | `CHAMPION_ENEMIES` |
| `champion.citation` | citation | `ChampionEnemy.java:94` |
| `champion.counterExpression` | string | -- |
| `champion.exclusions` | list of object | -- |
| `defaultDepth` | number | `1` |

## How a row is shaped

### `alternates`

11 entries. Every field any entry of this list holds, by the path it is reached at
(`[]` is a list's element):

| Field | Type |
|---|---|
| `alternate` | string |
| `citation` | object |
| `citation.line` | number |
| `citation.path` | string |
| `className` | string |
| `reachable` | boolean |

### `depths`

26 entries. Every field any entry of this list holds, by the path it is reached at
(`[]` is a list's element):

| Field | Type |
|---|---|
| `citation` | object |
| `citation.line` | number |
| `citation.path` | string |
| `depth` | number |
| `entries` | list |
| `entries[]` | object |
| `entries[].className` | string |
| `entries[].count` | number |
| `entries[].family` | boolean |

### `families`

2 entries. Every field any entry of this list holds, by the path it is reached at
(`[]` is a list's element):

| Field | Type |
|---|---|
| `citation` | object |
| `citation.line` | number |
| `citation.path` | string |
| `className` | string |
| `odds` | list |
| `odds[]` | object |
| `odds[].className` | string |
| `odds[].expression` | string |
| `odds[].perMille` | number |

### `rareMobs`

4 entries. Every field any entry of this list holds, by the path it is reached at
(`[]` is a list's element):

| Field | Type |
|---|---|
| `citation` | object |
| `citation.line` | number |
| `citation.path` | string |
| `className` | string |
| `depth` | number |
| `perMille` | number |

## Citations

Every value above was read from the pinned tree at the line the entry carries. The reader
recorded 44 citations in 4 files:

| Source | Citations | Lines |
|---|---:|---|
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/buffs/ChampionEnemy.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/buffs/ChampionEnemy.java) | 1 | 94 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Elemental.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Elemental.java) | 1 | 604 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/MobSpawner.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/MobSpawner.java) | 41 | 75-274 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Shaman.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Shaman.java) | 1 | 184 |

## Judgments

The reader named no reason in this table: every entry is a fact the pinned tree states, and
nothing was left out or decided.
