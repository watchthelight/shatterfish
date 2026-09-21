# Vocabulary

!!! info "Generated"

    From `codex/v4.0.0/vocabulary.json` at upstream tag `v4.0.0`, Codex version 8.
    Never hand edited: run `./gradlew :codex:generate` and commit what it writes.

The one table read from two pinned games: every display name either this game or vanilla Pixel
Dungeon gives a mob or an item, which of them has it, the classes that carry it on each side
with their citations, and the mechanics the two state differently. Nothing reads it; it is the
input the variant classifier of epic 7 will use (story 2.8).

[`codex/v4.0.0/vocabulary.json`](https://github.com/watchthelight/shatterfish/blob/main/codex/v4.0.0/vocabulary.json) holds 449 entries in 268.8 KB. The entries are not repeated here: they are committed, diffable and cited in that file.

## What the table states of itself

The values the table carries beside its entries, by the path each is reached at.

| Field | Type | Value |
|---|---|---|
| `consumer` | string | -- |
| `tag` | string | `v4.0.0` |
| `vanillaTag` | string | `archive` |

## How a row is shaped

### `entries`

449 entries. Every field any entry of this list holds, by the path it is reached at
(`[]` is a list's element):

| Field | Type |
|---|---|
| `differences` | list |
| `here` | object |
| `here.citations` | list |
| `here.citations[]` | object |
| `here.citations[].line` | number |
| `here.citations[].path` | string |
| `here.classNames` | list |
| `here.classNames[]` | string |
| `here.facts` | list |
| `here.name` | string |
| `kind` | string |
| `name` | string |
| `shared` | boolean |
| `there` | object |
| `there.citations` | list |
| `there.citations[]` | object |
| `there.citations[].line` | number |
| `there.citations[].path` | string |
| `there.classNames` | list |
| `there.classNames[]` | string |
| `there.facts` | list |
| `there.name` | string |
| `differences[]` | object |
| `differences[].comparable` | boolean |
| `differences[].here` | string |
| `differences[].there` | string |
| `differences[].what` | string |
| `here.facts[]` | object |
| `here.facts[].citation` | object |
| `here.facts[].citation.line` | number |
| `here.facts[].citation.path` | string |
| `here.facts[].expression` | string |
| `here.facts[].what` | string |
| `there.facts[]` | object |
| `there.facts[].citation` | object |
| `there.facts[].citation.line` | number |
| `there.facts[].citation.path` | string |
| `there.facts[].expression` | string |
| `there.facts[].what` | string |

## Citations

Every value above was read from the pinned tree at the line the entry carries. The reader
recorded 1259 citations in 227 files:

| Source | Citations | Lines |
|---|---:|---|
| [`core/src/main/assets/messages/actors/actors.properties`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/assets/messages/actors/actors.properties) | 98 | 1314-1919 |
| [`core/src/main/assets/messages/items/items.properties`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/assets/messages/items/items.properties) | 294 | 117-2506 |
| [`core/src/main/assets/messages/plants/plants.properties`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/assets/messages/plants/plants.properties) | 13 | 4-78 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/Char.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/Char.java) | 58 | 689-709 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Albino.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Albino.java) | 1 | 36 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/ArmoredBrute.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/ArmoredBrute.java) | 2 | 48-55 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/ArmoredStatue.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/ArmoredStatue.java) | 2 | 47-74 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Bat.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Bat.java) | 5 | 38-62 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Bee.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Bee.java) | 2 | 113-118 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Brute.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Brute.java) | 8 | 45-70 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Crab.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Crab.java) | 11 | 34-56 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/CrystalGuardian.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/CrystalGuardian.java) | 5 | 51-115 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/CrystalMimic.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/CrystalMimic.java) | 1 | 97 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/CrystalSpire.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/CrystalSpire.java) | 1 | 64 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/CrystalWisp.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/CrystalWisp.java) | 5 | 42-87 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/DM100.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/DM100.java) | 7 | 47-71 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/DM200.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/DM200.java) | 11 | 43-69 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/DM201.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/DM201.java) | 2 | 40-48 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/DM300.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/DM300.java) | 5 | 84-104 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/DemonSpawner.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/DemonSpawner.java) | 3 | 46-64 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/DwarfKing.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/DwarfKing.java) | 5 | 85-104 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/EbonyMimic.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/EbonyMimic.java) | 1 | 89 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Elemental.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Elemental.java) | 23 | 71-397 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Eye.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Eye.java) | 5 | 53-81 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/FetidRat.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/FetidRat.java) | 4 | 42-60 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/FungalCore.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/FungalCore.java) | 1 | 30 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/FungalSentry.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/FungalSentry.java) | 4 | 37-90 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/FungalSpinner.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/FungalSpinner.java) | 2 | 38-39 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Ghoul.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Ghoul.java) | 5 | 48-75 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Gnoll.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Gnoll.java) | 7 | 34-55 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/GnollExile.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/GnollExile.java) | 5 | 53-70 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/GnollGeomancer.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/GnollGeomancer.java) | 4 | 69-161 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/GnollGuard.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/GnollGuard.java) | 5 | 40-106 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/GnollSapper.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/GnollSapper.java) | 5 | 45-115 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/GnollTrickster.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/GnollTrickster.java) | 3 | 48-66 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Golem.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Golem.java) | 5 | 45-72 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Goo.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Goo.java) | 5 | 54-97 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/GreatCrab.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/GreatCrab.java) | 2 | 45-46 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Guard.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Guard.java) | 5 | 51-143 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/HermitCrab.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/HermitCrab.java) | 2 | 33-50 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Mimic.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Mimic.java) | 10 | 230-251 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Monk.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Monk.java) | 9 | 42-70 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Necromancer.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Necromancer.java) | 6 | 55-97 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Piranha.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Piranha.java) | 10 | 65-94 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Pylon.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Pylon.java) | 1 | 54 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Rat.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Rat.java) | 10 | 36-65 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/RipperDemon.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/RipperDemon.java) | 5 | 51-87 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/RotHeart.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/RotHeart.java) | 5 | 42-132 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/RotLasher.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/RotLasher.java) | 5 | 42-117 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Scorpio.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Scorpio.java) | 10 | 44-68 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Senior.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Senior.java) | 1 | 46 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Shaman.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Shaman.java) | 6 | 47-68 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Skeleton.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Skeleton.java) | 12 | 52-162 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Slime.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Slime.java) | 8 | 38-53 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Snake.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Snake.java) | 4 | 38-54 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Spinner.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Spinner.java) | 8 | 46-70 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Statue.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Statue.java) | 8 | 59-113 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Succubus.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Succubus.java) | 5 | 58-168 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Swarm.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Swarm.java) | 4 | 45-123 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Tengu.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Tengu.java) | 5 | 90-116 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Thief.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Thief.java) | 10 | 46-121 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/TormentedSpirit.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/TormentedSpirit.java) | 2 | 46-52 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Warlock.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Warlock.java) | 5 | 50-73 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Wraith.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Wraith.java) | 2 | 76-81 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/YogDzewa.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/YogDzewa.java) | 7 | 72-668 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/YogFist.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/YogFist.java) | 30 | 68-476 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/npcs/MirrorImage.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/npcs/MirrorImage.java) | 3 | 110-156 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/npcs/NPC.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/npcs/NPC.java) | 11 | 31 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/npcs/PrismaticImage.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/npcs/PrismaticImage.java) | 5 | 52-188 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/quest/vault/VaultBossElemental.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/quest/vault/VaultBossElemental.java) | 5 | 84-115 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/quest/vault/VaultDM100.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/quest/vault/VaultDM100.java) | 3 | 35-49 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/quest/vault/VaultDM200.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/quest/vault/VaultDM200.java) | 2 | 36-45 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/quest/vault/VaultShaman.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/quest/vault/VaultShaman.java) | 4 | 42-89 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/quest/vault/VaultSkeleton.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/quest/vault/VaultSkeleton.java) | 3 | 36-60 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/Char.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/Char.java) | 33 | 219-235 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Acidic.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Acidic.java) | 1 | 28 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Albino.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Albino.java) | 2 | 30-33 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Bandit.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Bandit.java) | 1 | 34 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Bat.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Bat.java) | 6 | 32-59 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Brute.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Brute.java) | 9 | 37-71 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Crab.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Crab.java) | 6 | 29-54 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/CursePersonification.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/CursePersonification.java) | 6 | 38-63 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/DM300.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/DM300.java) | 12 | 51-73 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Elemental.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Elemental.java) | 6 | 37-63 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Eye.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Eye.java) | 5 | 46-83 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/FetidRat.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/FetidRat.java) | 6 | 35-58 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Gnoll.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Gnoll.java) | 6 | 29-53 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Golem.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Golem.java) | 6 | 34-60 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Goo.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Goo.java) | 12 | 49-78 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/King.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/King.java) | 18 | 55-302 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Mimic.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Mimic.java) | 3 | 47-77 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Monk.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Monk.java) | 10 | 40-69 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Piranha.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Piranha.java) | 6 | 39-75 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Rat.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Rat.java) | 10 | 28-48 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Scorpio.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Scorpio.java) | 11 | 39-64 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Senior.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Senior.java) | 2 | 30-35 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Shaman.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Shaman.java) | 6 | 45-69 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Shielded.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Shielded.java) | 3 | 26-33 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Skeleton.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Skeleton.java) | 6 | 41-103 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Spinner.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Spinner.java) | 6 | 37-63 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Statue.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Statue.java) | 6 | 40-101 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Succubus.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Succubus.java) | 6 | 46-112 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Swarm.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Swarm.java) | 5 | 41-111 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Tengu.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Tengu.java) | 12 | 52-73 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Thief.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Thief.java) | 11 | 42-97 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Warlock.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Warlock.java) | 6 | 45-69 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Wraith.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Wraith.java) | 4 | 42-72 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Yog.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Yog.java) | 22 | 60-422 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/npcs/Bee.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/npcs/Bee.java) | 3 | 35-75 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/npcs/Blacksmith.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/npcs/Blacksmith.java) | 1 | 71 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/npcs/Ghost.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/npcs/Ghost.java) | 1 | 59 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/npcs/Imp.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/npcs/Imp.java) | 1 | 44 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/npcs/ImpShopkeeper.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/npcs/ImpShopkeeper.java) | 1 | 33 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/npcs/MirrorImage.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/npcs/MirrorImage.java) | 3 | 37-80 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/npcs/NPC.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/npcs/NPC.java) | 9 | 29 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/npcs/RatKing.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/npcs/RatKing.java) | 1 | 29 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/npcs/Shopkeeper.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/npcs/Shopkeeper.java) | 1 | 34 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/npcs/Wandmaker.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/actors/mobs/npcs/Wandmaker.java) | 1 | 58 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/Amulet.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/Amulet.java) | 1 | 36 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/Ankh.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/Ankh.java) | 1 | 26 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/ArmorKit.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/ArmorKit.java) | 1 | 44 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/Bomb.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/Bomb.java) | 1 | 41 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/DewVial.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/DewVial.java) | 1 | 51 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/Dewdrop.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/Dewdrop.java) | 1 | 34 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/Gold.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/Gold.java) | 1 | 43 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/Honeypot.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/Honeypot.java) | 1 | 41 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/LloydsBeacon.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/LloydsBeacon.java) | 1 | 63 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/TomeOfMastery.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/TomeOfMastery.java) | 2 | 49 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/Torch.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/Torch.java) | 1 | 36 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/Weightstone.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/Weightstone.java) | 1 | 48 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/armor/ClothArmor.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/armor/ClothArmor.java) | 1 | 26 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/armor/HuntressArmor.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/armor/HuntressArmor.java) | 1 | 42 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/armor/LeatherArmor.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/armor/LeatherArmor.java) | 1 | 26 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/armor/MageArmor.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/armor/MageArmor.java) | 1 | 42 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/armor/MailArmor.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/armor/MailArmor.java) | 1 | 26 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/armor/PlateArmor.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/armor/PlateArmor.java) | 1 | 26 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/armor/RogueArmor.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/armor/RogueArmor.java) | 1 | 46 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/armor/ScaleArmor.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/armor/ScaleArmor.java) | 1 | 26 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/armor/WarriorArmor.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/armor/WarriorArmor.java) | 1 | 51 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/bags/Keyring.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/bags/Keyring.java) | 1 | 27 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/bags/ScrollHolder.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/bags/ScrollHolder.java) | 1 | 27 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/bags/SeedPouch.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/bags/SeedPouch.java) | 1 | 27 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/bags/WandHolster.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/bags/WandHolster.java) | 1 | 27 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/food/ChargrilledMeat.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/food/ChargrilledMeat.java) | 1 | 26 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/food/Food.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/food/Food.java) | 1 | 46 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/food/FrozenCarpaccio.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/food/FrozenCarpaccio.java) | 1 | 37 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/food/MysteryMeat.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/food/MysteryMeat.java) | 1 | 35 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/food/OverpricedRation.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/food/OverpricedRation.java) | 1 | 26 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/food/Pasty.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/food/Pasty.java) | 1 | 26 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/keys/GoldenKey.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/keys/GoldenKey.java) | 1 | 25 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/keys/IronKey.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/keys/IronKey.java) | 1 | 32 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/keys/SkeletonKey.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/keys/SkeletonKey.java) | 1 | 25 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/potions/PotionOfExperience.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/potions/PotionOfExperience.java) | 1 | 25 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/potions/PotionOfFrost.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/potions/PotionOfFrost.java) | 1 | 34 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/potions/PotionOfHealing.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/potions/PotionOfHealing.java) | 1 | 33 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/potions/PotionOfInvisibility.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/potions/PotionOfInvisibility.java) | 1 | 34 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/potions/PotionOfLevitation.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/potions/PotionOfLevitation.java) | 1 | 28 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/potions/PotionOfLiquidFlame.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/potions/PotionOfLiquidFlame.java) | 1 | 30 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/potions/PotionOfMight.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/potions/PotionOfMight.java) | 1 | 28 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/potions/PotionOfMindVision.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/potions/PotionOfMindVision.java) | 1 | 29 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/potions/PotionOfParalyticGas.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/potions/PotionOfParalyticGas.java) | 1 | 30 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/potions/PotionOfPurity.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/potions/PotionOfPurity.java) | 1 | 44 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/potions/PotionOfStrength.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/potions/PotionOfStrength.java) | 1 | 28 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/potions/PotionOfToxicGas.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/potions/PotionOfToxicGas.java) | 1 | 30 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/quest/CorpseDust.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/quest/CorpseDust.java) | 1 | 26 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/quest/DarkGold.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/quest/DarkGold.java) | 1 | 26 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/quest/DriedRose.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/quest/DriedRose.java) | 1 | 26 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/quest/DwarfToken.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/quest/DwarfToken.java) | 1 | 26 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/quest/PhantomFish.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/quest/PhantomFish.java) | 1 | 38 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/quest/Pickaxe.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/quest/Pickaxe.java) | 1 | 53 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/quest/RatSkull.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/quest/RatSkull.java) | 1 | 26 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/rings/RingOfAccuracy.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/rings/RingOfAccuracy.java) | 1 | 23 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/rings/RingOfDetection.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/rings/RingOfDetection.java) | 1 | 26 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/rings/RingOfElements.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/rings/RingOfElements.java) | 1 | 34 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/rings/RingOfEvasion.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/rings/RingOfEvasion.java) | 1 | 23 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/rings/RingOfHaggler.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/rings/RingOfHaggler.java) | 1 | 27 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/rings/RingOfHaste.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/rings/RingOfHaste.java) | 1 | 23 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/rings/RingOfHerbalism.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/rings/RingOfHerbalism.java) | 1 | 23 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/rings/RingOfMending.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/rings/RingOfMending.java) | 1 | 23 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/rings/RingOfPower.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/rings/RingOfPower.java) | 1 | 23 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/rings/RingOfSatiety.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/rings/RingOfSatiety.java) | 1 | 23 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/rings/RingOfShadows.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/rings/RingOfShadows.java) | 1 | 23 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/rings/RingOfThorns.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/rings/RingOfThorns.java) | 1 | 27 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/scrolls/ScrollOfChallenge.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/scrolls/ScrollOfChallenge.java) | 1 | 36 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/scrolls/ScrollOfEnchantment.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/scrolls/ScrollOfEnchantment.java) | 1 | 34 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/scrolls/ScrollOfIdentify.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/scrolls/ScrollOfIdentify.java) | 1 | 29 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/scrolls/ScrollOfLullaby.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/scrolls/ScrollOfLullaby.java) | 1 | 34 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/scrolls/ScrollOfMagicMapping.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/scrolls/ScrollOfMagicMapping.java) | 1 | 37 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/scrolls/ScrollOfMirrorImage.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/scrolls/ScrollOfMirrorImage.java) | 1 | 37 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/scrolls/ScrollOfPsionicBlast.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/scrolls/ScrollOfPsionicBlast.java) | 1 | 34 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/scrolls/ScrollOfRecharging.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/scrolls/ScrollOfRecharging.java) | 1 | 31 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/scrolls/ScrollOfRemoveCurse.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/scrolls/ScrollOfRemoveCurse.java) | 1 | 38 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/scrolls/ScrollOfTeleportation.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/scrolls/ScrollOfTeleportation.java) | 1 | 37 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/scrolls/ScrollOfTerror.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/scrolls/ScrollOfTerror.java) | 1 | 34 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/scrolls/ScrollOfUpgrade.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/scrolls/ScrollOfUpgrade.java) | 1 | 33 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/scrolls/ScrollOfWipeOut.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/scrolls/ScrollOfWipeOut.java) | 1 | 50 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/wands/WandOfAmok.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/wands/WandOfAmok.java) | 1 | 35 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/wands/WandOfAvalanche.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/wands/WandOfAvalanche.java) | 1 | 46 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/wands/WandOfBlink.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/wands/WandOfBlink.java) | 1 | 34 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/wands/WandOfDisintegration.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/wands/WandOfDisintegration.java) | 1 | 39 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/wands/WandOfFirebolt.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/wands/WandOfFirebolt.java) | 1 | 43 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/wands/WandOfFlock.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/wands/WandOfFlock.java) | 2 | 41-112 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/wands/WandOfLightning.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/wands/WandOfLightning.java) | 1 | 41 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/wands/WandOfMagicMissile.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/wands/WandOfMagicMissile.java) | 1 | 53 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/wands/WandOfPoison.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/wands/WandOfPoison.java) | 1 | 33 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/wands/WandOfReach.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/wands/WandOfReach.java) | 1 | 48 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/wands/WandOfRegrowth.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/wands/WandOfRegrowth.java) | 1 | 36 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/wands/WandOfSlowness.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/wands/WandOfSlowness.java) | 1 | 33 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/wands/WandOfTeleportation.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/wands/WandOfTeleportation.java) | 1 | 33 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/weapon/melee/BattleAxe.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/weapon/melee/BattleAxe.java) | 1 | 25 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/weapon/melee/Dagger.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/weapon/melee/Dagger.java) | 1 | 25 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/weapon/melee/Glaive.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/weapon/melee/Glaive.java) | 1 | 25 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/weapon/melee/Knuckles.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/weapon/melee/Knuckles.java) | 1 | 25 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/weapon/melee/Longsword.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/weapon/melee/Longsword.java) | 1 | 25 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/weapon/melee/Mace.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/weapon/melee/Mace.java) | 1 | 25 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/weapon/melee/Quarterstaff.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/weapon/melee/Quarterstaff.java) | 1 | 25 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/weapon/melee/ShortSword.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/weapon/melee/ShortSword.java) | 1 | 50 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/weapon/melee/Spear.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/weapon/melee/Spear.java) | 1 | 25 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/weapon/melee/Sword.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/weapon/melee/Sword.java) | 1 | 25 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/weapon/melee/WarHammer.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/weapon/melee/WarHammer.java) | 1 | 25 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/weapon/missiles/Boomerang.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/weapon/missiles/Boomerang.java) | 1 | 30 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/weapon/missiles/CurareDart.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/weapon/missiles/CurareDart.java) | 1 | 32 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/weapon/missiles/Dart.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/weapon/missiles/Dart.java) | 1 | 27 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/weapon/missiles/IncendiaryDart.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/weapon/missiles/IncendiaryDart.java) | 1 | 36 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/weapon/missiles/Javelin.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/weapon/missiles/Javelin.java) | 1 | 30 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/weapon/missiles/Shuriken.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/weapon/missiles/Shuriken.java) | 1 | 27 |
| [`vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/weapon/missiles/Tamahawk.java`](https://github.com/watchthelight/shatterfish/blob/main/vanilla-src/core/src/main/java/com/watabou/pixeldungeon/items/weapon/missiles/Tamahawk.java) | 1 | 30 |

## Judgments

The reader named no reason in this table: every entry is a fact the pinned tree states, and
nothing was left out or decided.
