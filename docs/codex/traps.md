# Traps

!!! info "Generated"

    From `codex/v4.0.0/traps.json` at upstream tag `v4.0.0`, Codex version 8.
    Never hand edited: run `./gradlew :codex:generate` and commit what it writes.

Every concrete trap class with the two flags a player can act on, whether the game leaves it
active, the text of its own effect and whose effect that is, what else the placing class puts on
the cell, and the pool each level draws from with the class that declares it, the condition that
chooses it and how many traps the floor lays (story 2.6).

[`codex/v4.0.0/traps.json`](https://github.com/watchthelight/shatterfish/blob/main/codex/v4.0.0/traps.json) holds 44 entries in 60.9 KB. The entries are not repeated here: they are committed, diffable and cited in that file.

## How a row is shaped

### `pools`

9 entries. Every field any entry of this list holds, by the path it is reached at
(`[]` is a list's element):

| Field | Type |
|---|---|
| `citation` | object |
| `citation.line` | number |
| `citation.path` | string |
| `condition` | string |
| `declaredBy` | string |
| `levelClass` | string |
| `nTrapsCitation` | object |
| `nTrapsCitation.line` | number |
| `nTrapsCitation.path` | string |
| `nTrapsExpression` | string |
| `traps` | list |
| `traps[]` | object |
| `traps[].className` | string |
| `traps[].weightPerMille` | number |

### `traps`

35 entries. Every field any entry of this list holds, by the path it is reached at
(`[]` is a list's element):

| Field | Type |
|---|---|
| `activateCitation` | object |
| `activateCitation.line` | number |
| `activateCitation.path` | string |
| `activateExpression` | string |
| `activateFrom` | string |
| `active` | boolean |
| `alsoOnTheCell` | list |
| `canBeHidden` | boolean |
| `canBeSearched` | boolean |
| `citation` | object |
| `citation.line` | number |
| `citation.path` | string |
| `className` | string |
| `name` | string |
| `nameCitation` | object |
| `nameCitation.line` | number |
| `nameCitation.path` | string |
| `nameFrom` | string |
| `alsoOnTheCell[]` | object |
| `alsoOnTheCell[].citation` | object |
| `alsoOnTheCell[].citation.line` | number |
| `alsoOnTheCell[].citation.path` | string |
| `alsoOnTheCell[].expression` | string |
| `alsoOnTheCell[].what` | string |

## Citations

Every value above was read from the pinned tree at the line the entry carries. The reader
recorded 129 citations in 43 files:

| Source | Citations | Lines |
|---|---:|---|
| [`core/src/main/assets/messages/levels/levels.properties`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/assets/messages/levels/levels.properties) | 35 | 23-305 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/CavesLevel.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/CavesLevel.java) | 1 | 178 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/CityLevel.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/CityLevel.java) | 2 | 125 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/HallsLevel.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/HallsLevel.java) | 1 | 136 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/PrisonLevel.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/PrisonLevel.java) | 1 | 127 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/RegularLevel.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/RegularLevel.java) | 6 | 193 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/SewerBossLevel.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/SewerBossLevel.java) | 2 | 128 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/SewerLevel.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/SewerLevel.java) | 4 | 120 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/VaultLevel.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/VaultLevel.java) | 4 | 201-680 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/ToxicGasRoom.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/ToxicGasRoom.java) | 6 | 60-173 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/AlarmTrap.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/AlarmTrap.java) | 2 | 33-41 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/BlazingTrap.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/BlazingTrap.java) | 2 | 38-47 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/BurningTrap.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/BurningTrap.java) | 2 | 37-45 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/ChillingTrap.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/ChillingTrap.java) | 2 | 36-44 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/ConfusionTrap.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/ConfusionTrap.java) | 2 | 34-42 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/CorrosionTrap.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/CorrosionTrap.java) | 2 | 34-42 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/CursingTrap.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/CursingTrap.java) | 2 | 44-52 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/DisarmingTrap.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/DisarmingTrap.java) | 2 | 41-49 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/DisintegrationTrap.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/DisintegrationTrap.java) | 2 | 42-53 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/DistortionTrap.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/DistortionTrap.java) | 2 | 53-63 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/ExplosiveTrap.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/ExplosiveTrap.java) | 2 | 32-40 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/FlashingTrap.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/FlashingTrap.java) | 2 | 36-47 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/FlockTrap.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/FlockTrap.java) | 2 | 40-49 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/FrostTrap.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/FrostTrap.java) | 2 | 37-45 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/GatewayTrap.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/GatewayTrap.java) | 2 | 43-56 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/GeyserTrap.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/GeyserTrap.java) | 2 | 44-55 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/GnollRockfallTrap.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/GnollRockfallTrap.java) | 2 | 50-53 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/GrimTrap.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/GrimTrap.java) | 2 | 42-53 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/GrippingTrap.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/GrippingTrap.java) | 2 | 32-43 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/GuardianTrap.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/GuardianTrap.java) | 2 | 38-46 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/OozeTrap.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/OozeTrap.java) | 2 | 33-41 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/PitfallTrap.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/PitfallTrap.java) | 2 | 43-51 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/PoisonDartTrap.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/PoisonDartTrap.java) | 3 | 44-63 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/RockfallTrap.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/RockfallTrap.java) | 2 | 47-58 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/ShockingTrap.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/ShockingTrap.java) | 2 | 35-43 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/StormTrap.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/StormTrap.java) | 2 | 36-44 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/SummoningTrap.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/SummoningTrap.java) | 2 | 36-46 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/TeleportationTrap.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/TeleportationTrap.java) | 3 | 39-47 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/TenguDartTrap.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/TenguDartTrap.java) | 1 | 29 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/ToxicTrap.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/ToxicTrap.java) | 2 | 34-42 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/WarpingTrap.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/WarpingTrap.java) | 2 | 28-36 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/WeakeningTrap.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/WeakeningTrap.java) | 2 | 33-41 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/WornDartTrap.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/WornDartTrap.java) | 2 | 42-53 |

## Judgments

The reader named no reason in this table: every entry is a fact the pinned tree states, and
nothing was left out or decided.
