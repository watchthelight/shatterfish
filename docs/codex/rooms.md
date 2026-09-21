# Rooms

!!! info "Generated"

    From `codex/v4.0.0/rooms.json` at upstream tag `v4.0.0`, Codex version 8.
    Never hand edited: run `./gradlew :codex:generate` and commit what it writes.

The special and secret rooms with the game's lists, what each puts on the floor -- keys,
solution potions, a honeypot at a coin -- and what it draws (story 2.4).

[`codex/v4.0.0/rooms.json`](https://github.com/watchthelight/shatterfish/blob/main/codex/v4.0.0/rooms.json) holds 39 entries in 26.2 KB. The entries are not repeated here: they are committed, diffable and cited in that file.
An [entry](index.md#the-tables) is an object in one of the table's own lists; a number the
table states beside them is a value, and the values are below.

## What the table states of itself

The values the table carries beside its entries, by the path each is reached at.

| Field | Type | Value |
|---|---|---|
| `baseSecretsPerRegionThousandths` | list of number | `2000, 2250, 2500, 2750, 3000` |
| `queue.citation` | citation | `SpecialRoom.java:177` |
| `queue.expression` | string | `int index = Random.chances(new float[]{6, 3, 1});` |
| `queue.what` | string | `queue` |
| `secretsCitation` | citation | `SecretRoom.java:47` |

## How a row is shaped

### `lists`

6 entries. Every field any entry of this list holds, by the path it is reached at
(`[]` is a list's element):

| Field | Type |
|---|---|
| `citation` | object |
| `citation.line` | number |
| `citation.path` | string |
| `members` | list |
| `members[]` | string |
| `name` | string |

### `secrets`

12 entries. Every field any entry of this list holds, by the path it is reached at
(`[]` is a list's element):

| Field | Type |
|---|---|
| `citation` | object |
| `citation.line` | number |
| `citation.path` | string |
| `className` | string |
| `draws` | list |
| `draws[]` | object |
| `draws[].citation` | object |
| `draws[].citation.line` | number |
| `draws[].citation.path` | string |
| `draws[].expression` | string |
| `secret` | boolean |
| `spawns` | list |
| `spawns[]` | object |
| `spawns[].citation` | object |
| `spawns[].citation.line` | number |
| `spawns[].citation.path` | string |
| `spawns[].className` | string |
| `spawns[].conditional` | boolean |
| `spawns[].count` | number |
| `spawns[].floorDrop` | boolean |

### `specials`

21 entries. Every field any entry of this list holds, by the path it is reached at
(`[]` is a list's element):

| Field | Type |
|---|---|
| `citation` | object |
| `citation.line` | number |
| `citation.path` | string |
| `className` | string |
| `draws` | list |
| `draws[]` | object |
| `draws[].citation` | object |
| `draws[].citation.line` | number |
| `draws[].citation.path` | string |
| `draws[].expression` | string |
| `secret` | boolean |
| `spawns` | list |
| `spawns[]` | object |
| `spawns[].citation` | object |
| `spawns[].citation.line` | number |
| `spawns[].citation.path` | string |
| `spawns[].className` | string |
| `spawns[].conditional` | boolean |
| `spawns[].count` | number |
| `spawns[].floorDrop` | boolean |

## Citations

Every value above is cited to the pinned tree: the line it was read from, or, where a table
is measured rather than transcribed, the method that was run. The reader
recorded 115 citations in 35 files:

| Source | Citations | Line span |
|---|---:|---|
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/secret/SecretArtilleryRoom.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/secret/SecretArtilleryRoom.java) | 3 | 30-49 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/secret/SecretChestChasmRoom.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/secret/SecretChestChasmRoom.java) | 7 | 34-113 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/secret/SecretGardenRoom.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/secret/SecretGardenRoom.java) | 1 | 33 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/secret/SecretHoardRoom.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/secret/SecretHoardRoom.java) | 2 | 38-71 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/secret/SecretHoneypotRoom.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/secret/SecretHoneypotRoom.java) | 1 | 34 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/secret/SecretLaboratoryRoom.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/secret/SecretLaboratoryRoom.java) | 4 | 50-106 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/secret/SecretLarderRoom.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/secret/SecretLarderRoom.java) | 1 | 35 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/secret/SecretLibraryRoom.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/secret/SecretLibraryRoom.java) | 3 | 46-105 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/secret/SecretMazeRoom.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/secret/SecretMazeRoom.java) | 3 | 39-107 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/secret/SecretRoom.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/secret/SecretRoom.java) | 2 | 37-47 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/secret/SecretRunestoneRoom.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/secret/SecretRunestoneRoom.java) | 5 | 32-81 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/secret/SecretSummoningRoom.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/secret/SecretSummoningRoom.java) | 2 | 33-53 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/secret/SecretWellRoom.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/secret/SecretWellRoom.java) | 1 | 33 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/ArmoryRoom.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/ArmoryRoom.java) | 6 | 36-94 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/CryptRoom.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/CryptRoom.java) | 3 | 37-77 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/CrystalChoiceRoom.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/CrystalChoiceRoom.java) | 5 | 38-135 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/CrystalPathRoom.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/CrystalPathRoom.java) | 3 | 46-257 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/CrystalVaultRoom.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/CrystalVaultRoom.java) | 4 | 44-97 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/GardenRoom.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/GardenRoom.java) | 2 | 34-43 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/LaboratoryRoom.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/LaboratoryRoom.java) | 4 | 44-131 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/LibraryRoom.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/LibraryRoom.java) | 3 | 37-74 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/MagicWellRoom.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/MagicWellRoom.java) | 2 | 35-60 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/MagicalFireRoom.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/MagicalFireRoom.java) | 4 | 50-124 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/PitRoom.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/PitRoom.java) | 6 | 36-98 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/PoolRoom.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/PoolRoom.java) | 5 | 37-128 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/RunestoneRoom.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/RunestoneRoom.java) | 3 | 35-73 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/SacrificeRoom.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/SacrificeRoom.java) | 2 | 38-87 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/SentryRoom.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/SentryRoom.java) | 5 | 50-192 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/SpecialRoom.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/SpecialRoom.java) | 6 | 83-177 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/StatueRoom.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/StatueRoom.java) | 2 | 32-46 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/StorageRoom.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/StorageRoom.java) | 4 | 33-68 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/ToxicGasRoom.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/ToxicGasRoom.java) | 2 | 42-107 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/TrapsRoom.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/TrapsRoom.java) | 5 | 48-143 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/TreasuryRoom.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/TreasuryRoom.java) | 3 | 37-76 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/WeakFloorRoom.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/WeakFloorRoom.java) | 1 | 37 |

## Judgments

The reader named no reason in this table: every entry is a fact the pinned tree states, and
nothing was left out or decided.
