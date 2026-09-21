# Combat

!!! info "Generated"

    From `codex/v4.0.0/combat.json` at upstream tag `v4.0.0`, Codex version 8.
    Never hand edited: run `./gradlew :codex:generate` and commit what it writes.

Combat measured rather than transcribed (story 2.5): the spread of every weapon's own damage
roll by level, of the engine's own absorption roll with each armour worn, and of every mob's
own damage reduction, each naming the method that was run, citing it, and carrying the samples
behind it. The table that decides whether an attack lands names its method and says plainly
that a generator which may not boot cannot run it.

[`codex/v4.0.0/combat.json`](https://github.com/watchthelight/shatterfish/blob/main/codex/v4.0.0/combat.json) holds 548 entries in 150.1 KB. The entries are not repeated here: they are committed, diffable and cited in that file.

## What the table states of itself

The values the table carries beside its entries, by the path each is reached at.

| Field | Type | Value |
|---|---|---|
| `hit.accuracy.from` | number | `0` |
| `hit.accuracy.step` | number | `2` |
| `hit.accuracy.to` | number | `40` |
| `hit.accuracy.what` | string | `accuracy` |
| `hit.citation` | citation | `Char.java:619` |
| `hit.evasion.from` | number | `0` |
| `hit.evasion.step` | number | `2` |
| `hit.evasion.to` | number | `40` |
| `hit.evasion.what` | string | `evasion` |
| `hit.measured` | boolean | `false` |
| `hit.method` | string | `Char.hit` |
| `hit.reason` | string | -- |
| `hitCells` | empty list | -- |
| `seed` | number | `12648430` |

## How a row is shaped

### `armours`

66 entries. Every field any entry of this list holds, by the path it is reached at
(`[]` is a list's element):

| Field | Type |
|---|---|
| `citation` | object |
| `citation.line` | number |
| `citation.path` | string |
| `className` | string |
| `level` | number |
| `method` | string |
| `spread` | object |
| `spread.max` | number |
| `spread.meanPerMille` | number |
| `spread.min` | number |
| `spread.samples` | number |
| `tier` | number |

### `mobs`

110 entries. Every field any entry of this list holds, by the path it is reached at
(`[]` is a list's element):

| Field | Type |
|---|---|
| `citation` | object |
| `citation.line` | number |
| `citation.path` | string |
| `className` | string |
| `level` | number |
| `method` | string |
| `spread` | object |
| `spread.max` | number |
| `spread.meanPerMille` | number |
| `spread.min` | number |
| `spread.samples` | number |
| `tier` | number |

### `weapons`

372 entries. Every field any entry of this list holds, by the path it is reached at
(`[]` is a list's element):

| Field | Type |
|---|---|
| `citation` | object |
| `citation.line` | number |
| `citation.path` | string |
| `className` | string |
| `level` | number |
| `method` | string |
| `spread` | object |
| `spread.max` | number |
| `spread.meanPerMille` | number |
| `spread.min` | number |
| `spread.samples` | number |
| `tier` | number |

## Citations

Every value above was read from the pinned tree at the line the entry carries. The reader
recorded 549 citations in 64 files:

| Source | Citations | Lines |
|---|---:|---|
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/Char.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/Char.java) | 25 | 619-701 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/hero/Hero.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/hero/Hero.java) | 66 | 644 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/hero/abilities/cleric/PowerOfMany.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/hero/abilities/cleric/PowerOfMany.java) | 1 | 312 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/hero/abilities/rogue/SmokeBomb.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/hero/abilities/rogue/SmokeBomb.java) | 1 | 181 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/ArmoredBrute.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/ArmoredBrute.java) | 1 | 55 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Bat.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Bat.java) | 1 | 62 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Brute.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Brute.java) | 1 | 70 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Crab.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Crab.java) | 2 | 56 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/CrystalGuardian.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/CrystalGuardian.java) | 1 | 115 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/CrystalWisp.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/CrystalWisp.java) | 1 | 87 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/DM100.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/DM100.java) | 1 | 71 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/DM200.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/DM200.java) | 3 | 69 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/DM300.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/DM300.java) | 1 | 104 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/DemonSpawner.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/DemonSpawner.java) | 1 | 64 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/DwarfKing.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/DwarfKing.java) | 1 | 104 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Elemental.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Elemental.java) | 9 | 111 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Eye.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Eye.java) | 2 | 81 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/FetidRat.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/FetidRat.java) | 1 | 60 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Ghoul.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Ghoul.java) | 3 | 75 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Gnoll.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Gnoll.java) | 2 | 55 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/GnollExile.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/GnollExile.java) | 1 | 70 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/GnollGeomancer.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/GnollGeomancer.java) | 1 | 161 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/GnollGuard.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/GnollGuard.java) | 1 | 106 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/GnollSapper.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/GnollSapper.java) | 1 | 115 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Golem.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Golem.java) | 3 | 72 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Goo.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Goo.java) | 1 | 97 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Guard.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Guard.java) | 1 | 143 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/HermitCrab.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/HermitCrab.java) | 1 | 50 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Monk.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Monk.java) | 3 | 70 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Necromancer.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Necromancer.java) | 2 | 97 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Piranha.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Piranha.java) | 2 | 94 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Rat.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Rat.java) | 3 | 65 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/RipperDemon.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/RipperDemon.java) | 2 | 87 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/RotHeart.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/RotHeart.java) | 1 | 132 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/RotLasher.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/RotLasher.java) | 1 | 117 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Scorpio.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Scorpio.java) | 3 | 68 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Shaman.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Shaman.java) | 3 | 68 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Skeleton.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Skeleton.java) | 2 | 162 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Spinner.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Spinner.java) | 2 | 70 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Succubus.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Succubus.java) | 1 | 168 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Tengu.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Tengu.java) | 1 | 116 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Thief.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Thief.java) | 2 | 121 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Warlock.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Warlock.java) | 2 | 73 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/YogDzewa.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/YogDzewa.java) | 1 | 668 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/YogFist.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/YogFist.java) | 6 | 189 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/npcs/PrismaticImage.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/npcs/PrismaticImage.java) | 1 | 188 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/quest/vault/VaultBossElemental.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/quest/vault/VaultBossElemental.java) | 1 | 115 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/quest/vault/VaultDM100.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/quest/vault/VaultDM100.java) | 1 | 49 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/quest/vault/VaultShaman.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/quest/vault/VaultShaman.java) | 1 | 89 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/quest/vault/VaultSkeleton.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/quest/vault/VaultSkeleton.java) | 1 | 60 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/wands/WandOfWarding.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/wands/WandOfWarding.java) | 1 | 345 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/weapon/SpiritBow.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/weapon/SpiritBow.java) | 6 | 212 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/weapon/melee/AssassinsBlade.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/weapon/melee/AssassinsBlade.java) | 6 | 48 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/weapon/melee/Dagger.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/weapon/melee/Dagger.java) | 6 | 62 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/weapon/melee/Dirk.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/weapon/melee/Dirk.java) | 6 | 48 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/weapon/melee/Flail.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/weapon/melee/Flail.java) | 6 | 60 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/weapon/melee/MeleeWeapon.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/weapon/melee/MeleeWeapon.java) | 174 | 294 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/weapon/missiles/Kunai.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/weapon/missiles/Kunai.java) | 6 | 48 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/weapon/missiles/MissileWeapon.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/weapon/missiles/MissileWeapon.java) | 132 | 535 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/weapon/missiles/ThrowingKnife.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/weapon/missiles/ThrowingKnife.java) | 6 | 50 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/weapon/missiles/darts/AdrenalineDart.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/weapon/missiles/darts/AdrenalineDart.java) | 6 | 38 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/weapon/missiles/darts/CleansingDart.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/weapon/missiles/darts/CleansingDart.java) | 6 | 42 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/weapon/missiles/darts/HealingDart.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/weapon/missiles/darts/HealingDart.java) | 6 | 39 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/weapon/missiles/darts/HolyDart.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/weapon/missiles/darts/HolyDart.java) | 6 | 42 |

## Judgments

Where the reader could not simply read a value it recorded why, and every such entry is here
in full -- these are the judgments a human audits, and the list is short on purpose. 1 of the
table's 548 entries name a reason:

| Entry | Reason |
|---|---|
| `Char.hit` | Char.hit writes the icon of the reason an attack landed, which initialises FloatingText, whose initialiser builds a texture film; a generator that may not boot cannot run it |
