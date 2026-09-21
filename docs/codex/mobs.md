# Mobs

!!! info "Generated"

    From `codex/v4.0.0/mobs.json` at upstream tag `v4.0.0`, Codex version 8.
    Never hand edited: run `./gradlew :codex:generate` and commit what it writes.

Every concrete mob class of the game with its hit points, defense skill, experience, maximum
level, alignment, properties, loot and the three rolls as cited expressions, and its variants by
depth and by challenge -- the depth-scaled mobs and the Stronger Bosses variants (story 2.2).

[`codex/v4.0.0/mobs.json`](https://github.com/watchthelight/shatterfish/blob/main/codex/v4.0.0/mobs.json) holds 129 entries in 172.8 KB. The entries are not repeated here: they are committed, diffable and cited in that file.
An [entry](index.md#the-tables) is an object in one of the table's own lists; a number the
table states beside them is a value, and the values are below.

## How a row is shaped

129 entries. Every field any entry of this list holds, by the path it is reached at
(`[]` is a list's element):

| Field | Type |
|---|---|
| `alignment` | string |
| `attack` | object |
| `attack.citation` | object |
| `attack.citation.line` | number |
| `attack.citation.path` | string |
| `attack.expression` | string |
| `attack.kind` | string |
| `attack.max` | number |
| `attack.min` | number |
| `citation` | object |
| `citation.line` | number |
| `citation.path` | string |
| `className` | string |
| `customDefense` | boolean |
| `damage` | object |
| `damage.citation` | object |
| `damage.citation.line` | number |
| `damage.citation.path` | string |
| `damage.expression` | string |
| `damage.kind` | string |
| `damage.max` | number |
| `damage.min` | number |
| `defenseSkill` | number |
| `dr` | object |
| `dr.citation` | object |
| `dr.citation.line` | number |
| `dr.citation.path` | string |
| `dr.expression` | string |
| `dr.kind` | string |
| `dr.max` | number |
| `dr.min` | number |
| `draws` | list |
| `exp` | number |
| `ht` | number |
| `loot` | object |
| `loot.chanceExpression` | string |
| `loot.chanceThousandths` | number |
| `loot.citation` | object |
| `loot.citation.line` | number |
| `loot.citation.path` | string |
| `loot.customChance` | boolean |
| `loot.customLoot` | boolean |
| `loot.declaration` | string |
| `loot.kind` | string |
| `loot.name` | string |
| `loot.random` | boolean |
| `maxLvl` | number |
| `properties` | list |
| `properties[]` | string |
| `propertiesRandom` | boolean |
| `runDependent` | list |
| `statsSetLater` | boolean |
| `variants` | list |
| `variants[]` | object |
| `variants[].challenge` | string |
| `variants[].depth` | number |
| `variants[].fields` | object |
| `variants[].fields.defenseSkill` | string |
| `variants[].fields.ht` | string |
| `draws[]` | object |
| `draws[].line` | number |
| `draws[].path` | string |
| `runDependent[]` | string |

## Citations

Every value above is cited to the pinned tree: the line it was read from, or, where a table
is measured rather than transcribed, the method that was run. The reader
recorded 671 citations in 107 files:

| Source | Citations | Line span |
|---|---:|---|
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/Char.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/Char.java) | 80 | 689-709 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/hero/abilities/Ratmogrify.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/hero/abilities/Ratmogrify.java) | 4 | 191-279 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/hero/abilities/cleric/PowerOfMany.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/hero/abilities/cleric/PowerOfMany.java) | 5 | 244-312 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/hero/abilities/duelist/Feint.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/hero/abilities/duelist/Feint.java) | 1 | 160 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/hero/abilities/huntress/SpiritHawk.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/hero/abilities/huntress/SpiritHawk.java) | 3 | 143-184 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/hero/abilities/rogue/ShadowClone.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/hero/abilities/rogue/ShadowClone.java) | 4 | 144-229 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/hero/abilities/rogue/SmokeBomb.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/hero/abilities/rogue/SmokeBomb.java) | 2 | 165-181 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Acidic.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Acidic.java) | 2 | 32-39 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Albino.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Albino.java) | 2 | 31-39 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/ArmoredBrute.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/ArmoredBrute.java) | 4 | 37-55 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/ArmoredStatue.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/ArmoredStatue.java) | 2 | 35-74 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Bandit.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Bandit.java) | 1 | 34 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Bat.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Bat.java) | 5 | 33-62 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Bee.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Bee.java) | 3 | 37-118 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Brute.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Brute.java) | 6 | 40-70 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/CausticSlime.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/CausticSlime.java) | 1 | 33 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Crab.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Crab.java) | 11 | 29-56 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/CrystalGuardian.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/CrystalGuardian.java) | 5 | 46-186 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/CrystalMimic.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/CrystalMimic.java) | 2 | 50-97 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/CrystalSpire.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/CrystalSpire.java) | 2 | 60-475 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/CrystalWisp.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/CrystalWisp.java) | 5 | 37-87 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/DM100.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/DM100.java) | 6 | 40-71 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/DM200.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/DM200.java) | 12 | 38-69 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/DM201.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/DM201.java) | 2 | 35-48 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/DM300.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/DM300.java) | 5 | 79-116 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/DemonSpawner.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/DemonSpawner.java) | 3 | 41-64 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/DwarfKing.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/DwarfKing.java) | 8 | 80-639 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/EbonyMimic.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/EbonyMimic.java) | 2 | 44-89 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Elemental.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Elemental.java) | 39 | 83-563 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Eye.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Eye.java) | 9 | 48-81 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/FetidRat.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/FetidRat.java) | 3 | 37-60 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/FungalCore.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/FungalCore.java) | 1 | 27 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/FungalSentry.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/FungalSentry.java) | 3 | 32-90 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/FungalSpinner.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/FungalSpinner.java) | 1 | 33 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Ghoul.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Ghoul.java) | 12 | 43-75 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Gnoll.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Gnoll.java) | 8 | 29-55 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/GnollExile.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/GnollExile.java) | 4 | 41-70 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/GnollGeomancer.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/GnollGeomancer.java) | 5 | 66-161 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/GnollGuard.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/GnollGuard.java) | 5 | 35-106 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/GnollSapper.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/GnollSapper.java) | 5 | 37-115 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/GnollTrickster.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/GnollTrickster.java) | 3 | 43-66 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/GoldenMimic.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/GoldenMimic.java) | 1 | 44 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Golem.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Golem.java) | 14 | 40-72 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Goo.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Goo.java) | 4 | 51-97 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/GreatCrab.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/GreatCrab.java) | 2 | 40-54 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Guard.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Guard.java) | 5 | 43-143 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/HermitCrab.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/HermitCrab.java) | 2 | 28-50 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Mimic.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Mimic.java) | 11 | 51-251 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Mob.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Mob.java) | 66 | 1098 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Monk.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Monk.java) | 11 | 37-70 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Necromancer.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Necromancer.java) | 6 | 50-409 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/PhantomPiranha.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/PhantomPiranha.java) | 2 | 40-45 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Piranha.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Piranha.java) | 8 | 42-94 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Pylon.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Pylon.java) | 2 | 49-69 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Rat.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Rat.java) | 10 | 31-65 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/RipperDemon.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/RipperDemon.java) | 7 | 46-87 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/RotHeart.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/RotHeart.java) | 4 | 37-132 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/RotLasher.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/RotLasher.java) | 5 | 37-117 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Scorpio.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Scorpio.java) | 12 | 39-68 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Senior.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Senior.java) | 3 | 28-46 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Shaman.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Shaman.java) | 15 | 53-173 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Skeleton.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Skeleton.java) | 10 | 47-162 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Slime.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Slime.java) | 5 | 33-53 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Snake.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Snake.java) | 4 | 33-54 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/SpectralNecromancer.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/SpectralNecromancer.java) | 1 | 40 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Spinner.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Spinner.java) | 9 | 41-70 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Statue.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Statue.java) | 9 | 41-113 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Succubus.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Succubus.java) | 5 | 51-168 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Swarm.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Swarm.java) | 4 | 40-123 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Tengu.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Tengu.java) | 4 | 85-116 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Thief.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Thief.java) | 11 | 39-121 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/TormentedSpirit.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/TormentedSpirit.java) | 3 | 38-52 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Warlock.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Warlock.java) | 9 | 43-73 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Wraith.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Wraith.java) | 5 | 40-81 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/YogDzewa.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/YogDzewa.java) | 17 | 67-687 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/YogFist.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/YogFist.java) | 24 | 179-569 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/npcs/Blacksmith.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/npcs/Blacksmith.java) | 1 | 53 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/npcs/DirectableAlly.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/npcs/DirectableAlly.java) | 1 | 30 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/npcs/Ghost.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/npcs/Ghost.java) | 1 | 58 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/npcs/Imp.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/npcs/Imp.java) | 1 | 55 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/npcs/ImpShopkeeper.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/npcs/ImpShopkeeper.java) | 1 | 29 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/npcs/MirrorImage.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/npcs/MirrorImage.java) | 4 | 50-156 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/npcs/PrismaticImage.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/npcs/PrismaticImage.java) | 4 | 47-188 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/npcs/RatKing.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/npcs/RatKing.java) | 1 | 41 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/npcs/Sheep.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/npcs/Sheep.java) | 1 | 36 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/npcs/Shopkeeper.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/npcs/Shopkeeper.java) | 1 | 62 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/npcs/VaultLaser.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/npcs/VaultLaser.java) | 1 | 46 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/npcs/VaultMirror.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/npcs/VaultMirror.java) | 1 | 50 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/npcs/VaultSentry.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/npcs/VaultSentry.java) | 1 | 48 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/npcs/VaultTokenDoor.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/npcs/VaultTokenDoor.java) | 1 | 45 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/npcs/Wandmaker.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/npcs/Wandmaker.java) | 1 | 61 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/quest/vault/VaultBossElemental.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/quest/vault/VaultBossElemental.java) | 7 | 81-147 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/quest/vault/VaultDM100.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/quest/vault/VaultDM100.java) | 4 | 29-49 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/quest/vault/VaultDM200.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/quest/vault/VaultDM200.java) | 3 | 30-45 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/quest/vault/VaultElemental.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/quest/vault/VaultElemental.java) | 6 | 41-80 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/quest/vault/VaultGhoul.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/quest/vault/VaultGhoul.java) | 2 | 31-41 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/quest/vault/VaultGolem.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/quest/vault/VaultGolem.java) | 2 | 29-38 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/quest/vault/VaultRat.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/quest/vault/VaultRat.java) | 3 | 32-46 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/quest/vault/VaultShaman.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/quest/vault/VaultShaman.java) | 6 | 36-89 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/quest/vault/VaultSkeleton.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/quest/vault/VaultSkeleton.java) | 4 | 30-60 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/artifacts/DriedRose.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/artifacts/DriedRose.java) | 4 | 542-735 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/quest/CorpseDust.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/quest/CorpseDust.java) | 1 | 185 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/wands/WandOfLivingEarth.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/wands/WandOfLivingEarth.java) | 4 | 365-417 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/wands/WandOfRegrowth.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/wands/WandOfRegrowth.java) | 1 | 417 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/wands/WandOfWarding.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/wands/WandOfWarding.java) | 4 | 229-345 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/SentryRoom.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/SentryRoom.java) | 2 | 224-313 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/GuardianTrap.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/traps/GuardianTrap.java) | 1 | 72 |

## Judgments

The reader named no reason in this table: every entry is a fact the pinned tree states, and
nothing was left out or decided.
