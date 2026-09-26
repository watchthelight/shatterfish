# Bestiary

Every enemy and boss of Shattered Pixel Dungeon `v4.0.0`, read from the pinned code: how it moves,
attacks and notices you, what it inflicts and shrugs off, and from that, how to approach it, kill it,
nullify it and get away from it. It is the knowledge base for the Brain's fight decisions and for
anyone reading a Run.

- **Mechanics are Tier 1**: each statement was read from the code at the pin and carries its
  `path:line` citations, which `DocsCitationTest` resolves at the tag on every build.
- **Tactics are derived**: they follow from the mechanics and cite the lines they rest on. They are
  hypotheses about play, not measurements; a Brain change built on one still needs rig numbers.
- **Community notes are lore**: wiki and forum claims, each graded against the code (Tier 1
  confirmed, Tier F contradicted and struck through, Tier 3 unsettled). Their provenance lives in
  `lore/bestiary/`, one file per region with frontmatter.
- **Tags** for code live in `tactics/bestiary.json`, one entry per enemy class, in the closed
  vocabulary [below](#tag-vocabulary). `BestiaryTest` holds the file to the Codex and the vocabulary.

Pages: [Sewers](sewers.md) · [Prison](prison.md) · [Caves](caves.md) · [Dwarven City](city.md) ·
[Demon Halls](halls.md) · [Bosses](bosses.md) · [Anywhere](specials.md) · [Quest branches](quest-branch.md).

## Cross-cutting rules {#cross-cutting-rules}

The rules every card leans on. Tier 1, read at the pin; the ones the Brain is most likely to rely on
are also rows of [Rules: combat](../rules/combat.md).

- **Hitting is a roll of accuracy against evasion.** The attacker rolls a uniform number up to its accuracy and the defender one up to its evasion, each scaled by Bless x1.25, Hex x0.8 and Daze x0.5; the hit lands when the first is at least the second. For accuracy A and evasion D that is A/(2D) when A is at most D, else 1 - D/(2A). A magic attack, such as a gnoll shaman bolt, doubles the accuracy. An invisible attacker that can surprise attack has infinite accuracy; infinite evasion (a focused monk) still beats it. <small>`core/…/actors/Char.java:612-613`, `core/…/actors/Char.java:615-617`, `core/…/actors/Char.java:619-685`</small>
- **Surprise attacks always hit.** A mob is surprised by the hero when the hero is invisible, when the mob has not seen the hero (asleep, wandering, or hunting without the hero in the view it took at the start of its act), or when the hero is outside its field of view, and the hero can surprise attack (not with a flail or an under-strength weapon). A surprised or paralysed mob has evasion 0. The dagger and the throwing knife (and their relatives) roll from 75% of their range to the maximum on a surprise hit. <small>`core/…/actors/mobs/Mob.java:873-877`, `core/…/actors/mobs/Mob.java:796-802`, `core/…/actors/hero/Hero.java:744-752`, `core/…/weapon/melee/Dagger.java:62-77`, `core/…/weapon/missiles/ThrowingKnife.java:50-65`</small>
- **A sleeper wakes when you walk up; a sniper does not wake it first.** A sleeping mob with the hero in view wakes with chance 1/(distance + stealth) each of its turns, and a wandering one notices with 1/(distance/2 + stealth). Hero stealth is 0 unless an Obfuscation glyph raises it, so a sleeper always wakes on its first turn with the hero adjacent, and a wanderer always notices within 2 cells. The reliable surprise on a sleeper is a throw, an arrow or a zap from 2 or more cells on the hero's turn. <small>`core/…/actors/mobs/Mob.java:1244-1246`, `core/…/actors/mobs/Mob.java:1281-1284`, `core/…/actors/Char.java:1280-1286`</small>
- **The door ambush.** A mob computes its field of view at the start of its own act and a hunting mob sets `enemySeen` from it, so a mob that steps through a door or round a corner into view is still unaware until it acts again, and the hero's attack in between is a surprise attack. On equal times the hero acts first. Against a mob faster than the hero (a crab, a bat) the ambush holds only when the step fell on a whole turn, which the player cannot see. <small>`core/…/actors/Char.java:198-202`, `core/…/actors/mobs/Mob.java:290`, `core/…/actors/mobs/Mob.java:1327`, `core/…/actors/Actor.java:50-52`, `core/…/actors/Actor.java:266-267`</small>
- **Invisibility buys one surprise attack.** Every hero melee attack and every throw dispels Invisibility and ends the Cloak of Shadows' stealth right after the attack. <small>`core/…/actors/hero/Hero.java:478-481`, `core/…/actors/hero/Hero.java:2366-2368`, `core/…/actors/buffs/Invisibility.java:90-98`</small>
- **Throw from range, never point blank.** Missile weapons, the Spirit Bow's arrows included, get x1.5 accuracy against a target that is not adjacent and x0.5 (plus 0.25 per Point Blank point) against an adjacent one. <small>`core/…/weapon/missiles/MissileWeapon.java:215-233`</small>
- **Armour only stops weapon hits.** The defender's damage-reduction roll is subtracted only inside `Char.attack`: melee, thrown and bow hits. Wand bolts, gas, rockfalls, shaman and warlock bolts call `damage()` directly, with no hit roll for wands and no armour. Bombs are the exception that looks like one: the explosion subtracts the target's armour roll itself. <small>`core/…/actors/Char.java:388-390`, `core/…/actors/Char.java:483-486`, `core/…/wands/WandOfMagicMissile.java:56-62`, `core/…/items/bombs/Bomb.java:196-197`</small>
- **Speed decides whether you can run.** A mob's step costs 1/speed and its attack 1 (Adrenaline /1.5). Speed is the base speed times Cripple /2, Stamina x1.5, Adrenaline x2, Haste x3, Dread x2 and armour glyphs. A speed-2 mob (crab, bat, piranha) closes two cells per hero step but still attacks once per turn: you cannot outrun it, and ending your turn 3 or more cells away makes it spend its turn closing. <small>`core/…/actors/mobs/Mob.java:1353-1355`, `core/…/actors/mobs/Mob.java:753-757`, `core/…/actors/Char.java:770-783`</small>
- **Adjacency is eight cells; a corridor is the chokepoint.** Distance is Chebyshev and a mob attacks any of its eight neighbours, with no corner-cutting rule. A hero standing in a doorway can be hit from the three room-side cells; the one-at-a-time spot is a 1-wide corridor cell, one step back from the door. <small>`core/…/levels/Level.java:1533-1543`, `SPD-classes/…/utils/PathFinder.java:69-70`, `core/…/actors/mobs/Mob.java:558-568`</small>
- **Lines of fire.** A thrown item and a projectile stop at the target, the first character or a solid cell; a magic bolt stops at the first character or a solid cell. A closed door is solid and blocks sight; an open one does neither. Anything solid or any body in the line, ally or enemy, blocks a caster; so do corners. <small>`core/…/mechanics/Ballistica.java:42-49`, `core/…/levels/Terrain.java:90-91`</small>
- **Breaking contact.** A hunting mob that loses sight of the hero walks to the last cell it saw the hero on; when it can get no closer and still cannot see the hero it shows the lost mark and turns wandering. Only allies go through the stairs with the hero, so the stairs always end a chase, unless a boss fight has sealed the floor. <small>`core/…/actors/mobs/Mob.java:1342-1360`, `core/…/actors/mobs/Mob.java:1400-1405`, `core/…/actors/mobs/Mob.java:1686-1706`</small>
- **Water washes off Ooze and fire.** A character standing in water, not flying, loses Ooze and Burning once the debuff has acted once. <small>`core/…/actors/buffs/Ooze.java:93-94`, `core/…/actors/buffs/Burning.java:99-100`</small>
- **Large mobs stay out of doorways.** A LARGE mob (the DM-200s, DM-300, golems, the rusted fist) may only step onto an open-space cell, and a doorway or a 1-wide corridor is not one. <small>`core/…/actors/mobs/Mob.java:580-582`</small>
- **Properties carry immunities.** INORGANIC: bleeding, toxic gas and poison. FIERY: burning. ICY: frost and chill. ACIDIC: ooze. BOSS and MINIBOSS: charm-to-ally and dread. IMMOVABLE: vertigo. STATIC: every AI-state debuff, stun and slow. <small>`core/…/actors/Char.java:1413-1441`</small>

## Tag vocabulary {#tag-vocabulary}

`tactics/bestiary.json` is `{"entries": [...], "tag": "v4.0.0", "version": 1}` in the layout of the Codex
tables: canonical JSON with sorted keys and no whitespace, one entry per line, sorted by class name.
It covers every `ENEMY` class of the Codex table `mobs.json` and six `NEUTRAL` ones
that fight you all the same (the four mimics, the crystal spire and the power pylon). Each tag is
derived from the card of the same class; `citations` lists every `path:line` behind the tags, at the pin.

| Tag | Values | Meaning |
|---|---|---|
| `className` | Codex class name | The key: `actors.mobs.Snake`, `levels.traps.GuardianTrap.Guardian`. |
| `displayName` | text | The name the game shows, from the Codex strings (inherited from the superclass when the class has none). Fourteen names belong to more than one class, such as the three gnoll shamans and the boss and vault copies of city mobs, so a reader tells them apart by depth and floor. The bestiary pages head those cards with a qualifier. |
| `speed` | slow, normal, fast, immobile | Movement against the hero's speed 1: slower, equal, faster (speed 2), or never walks. `immobile` extends the vocabulary first proposed, because rot hearts, spawners, pylons and the like are neither slow nor normal. |
| `attack` | melee, ranged, bolt, none | `ranged` is a physical projectile or a reach attack from a distance; `bolt` is a magic bolt along a `MAGIC_BOLT` line; `none` never attacks directly. |
| `reach` | integer | Cells from which it can hurt you: 1 is adjacent only, 2 a spear, and a bolt caster's view distance. 99 means limited only by its sight; 0 means it has no attack of its own. |
| `flying` | true, false | Crosses chasms and water, sets off no traps. |
| `amphibious` | true, false | Moves on land and in water alike. No enemy at this tag is; the piranhas are water-bound instead, which the card says. |
| `immune` | buff and blob class names | `AllyBuff`, `Amok`, `Bleeding`, `Blazing`, `Blindness`, `Blizzard`, `Burning`, `Charm`, `Chill`, `ConfusionGas`, `CorrosiveGas`, `Dread`, `Fire`, `Freezing`, `Frost`, `Inferno`, `Ooze`, `Paralysis`, `ParalyticGas`, `Poison`, `Regrowth`, `Roots`, `Sleep`, `Slow`, `SmokeScreen`, `Speed`, `StenchGas`, `StormCloud`, `Terror`, `ToxicGas`, `Vertigo`, `Web`. From its properties and its own immunity list; a phase-dependent invulnerability is on the card, not here. |
| `inflicts` | buff class names and effects | `Bleeding`, `Blindness`, `Burning`, `Charm`, `Chill`, `Corrosion`, `Cripple`, `Degrade`, `Electricity`, `Frost`, `Hex`, `Ooze`, `Paralysis`, `Poison`, `Roots`, `ToxicGas`, `Vulnerable`, `Weakness`, `cursed-wand`, `darkness`, `displace`, `enchantment`, `knockback`, `pull`, `shock`, `steal`. Effects: `pull` drags you next to it, `displace` moves you, `knockback` pushes you, `steal` takes an item, `cursed-wand` is a random cursed-wand effect, `enchantment` is its weapon's, `darkness` shortens your light, `shock` arcs to nearby characters. |
| `ai` | sleeping, wandering, hunting, passive | The state it is created in. |
| `evasive` | true, false | Plain melee from a hero of the level expected at its depth lands less than about half the time, or an ability dodges outright (parry, claw block, teleport). |
| `splits` | true, false | A hit makes more of it (the swarm). |
| `telegraph` | true, false | At least one dangerous ability is announced on screen at least one hero action before it lands (a charge, marked cells, a countdown, a summoning glow), or it can be spotted before it strikes (a mimic's tell). |
| `approach` | close, keep-away, chokepoint, surprise, avoid | The derived default: get adjacent; stay out of its reach; fight it from a 1-wide cell; open with a surprise hit; do not engage. |
| `rangedFirst` | true, false | Spend throws, arrows or zaps before it closes. |
| `outrunnable` | true, false | The hero can walk away from it. |
| `breakContact` | door, stairs, out-of-sight, corridor, invisibility, distance, leave-water, escape-crystal, none | What ends its pursuit: a closed door, the stairs, breaking its sight, a corridor it cannot enter, going invisible, plain distance (immobile or leashed foes), leaving water (piranhas), the vault's escape crystal, or nothing (a sealed boss floor). |
| `citations` | `path:line` list | Every line the tags rest on, at `v4.0.0`, full path. |

Approach counts over the 103 entries: close 52, keep-away 12, chokepoint 12, surprise 19, avoid 8.

## Every enemy, one line each

### Sewers (depths 1-4)

- [Marsupial rat](sewers.md#rat) (1-3): Low threat. Walk in and melee; if several, back into a 1-wide corridor (one step past the door, not the door cell) so they come one at a time.
- [Albino rat](sewers.md#albino) (1-3 (1/50 alternate of Rat)): Easy to hit (def 2) but 12 HP and a bleeding bite. Soften it with a throw as it approaches, then melee it down; worth killing for the guaranteed meat.
- [Sewer snake](sewers.md#snake) (1-3): Never slug it out in the open (20% hit rate). If it's asleep, throw/shoot from 2+ tiles for a guaranteed hit.
- [Gnoll scout](sewers.md#gnoll) (2-4): Throw or shoot once as it closes, then melee. No reason to keep distance: the gnoll scout has no ranged or magic attack.
- [Gnoll exile](sewers.md#gnollexile) (2-4 (1/50 alternate of Gnoll)): Decide first: it's the toughest sewer regular (24 HP, 1-10 dmg, acc 15, reach 2) but drops 2-3 items.
- [Sewer crab](sewers.md#crab) (3-4): The sewer's real damage dealer (1-7, acc 12, fast). Take one ranged shot as it appears, then stand and melee in a corridor/doorway.
- [Hermit crab](sewers.md#hermitcrab) (3-4 (1/50 alternate of Crab)): Worth killing for the guaranteed armor. Don't plink it with weak hits (DR 2-6).
- [Slime](sewers.md#slime) (4): A 20 HP sponge that punishes heavy weapons. Use your fastest weapon, DOTs, or thrown weapons; don't spend surprise-damage openers or upgraded heavy hits here.
- [Caustic slime](sewers.md#causticslime) (4 (1/50 alternate of Slime)): Slime plan plus ooze management: soften with ranged hits, finish in melee standing near water, and step into water after the fight to clear Ooze.
- [Fetid rat](sewers.md#fetidrat) (quest (Sad Ghost, depth 2)): Engage from range, ideally with wands (no gas). If you must melee, hit once and step back out of the cloud rather than standing in it; stand near water to...
- [Gnoll trickster](sewers.md#gnolltrickster) (quest (Sad Ghost, depth 3)): Opposite of instinct: don't shoot it from afar, run it down. Approach using cover (doors, corners, other mobs) to cut its line; once adjacent it cannot hurt...
- [Great crab](sewers.md#greatcrab) (quest (Sad Ghost, depth 4)): Never trade in the open: every hit and zap is blocked while it watches you.

### Prison (depths 6-9)

- [Skeleton](prison.md#skeleton) (6-9): Open with a surprise hit if it is asleep. Soften it with ranged attacks as it walks in, since its low evasion (9) makes throws land.
- [Crazy thief](prison.md#thief) (6-9; rare 2.5% on 4): Treat it as an item threat rather than an HP threat. Hit it asleep or at range before it reaches you (HP 20).
- [Crazy bandit](prison.md#bandit) (6-9 (2% alternate of Thief)): Play it like a thief but stricter: open at range or with a surprise hit and burst it down (HP 20) before it touches you.
- [DM-100](prison.md#dm100) (7-9): Like a gnoll shaman: get next to it. Approach from outside its line of fire (around corners and through doors) or wait behind a corner until it walks up to...
- [Prison guard](prison.md#guard) (7-9): Either meet it in melee deliberately (walk into adjacency so the chain never fires) or snipe it from 5+ tiles and retreat around a corner as it closes.
- [Necromancer](prison.md#necromancer) (8-9): Shoot it from 5+ tiles as it walks in. Once it summons, ignore or body-block the skeleton and push through to the necromancer (it has no attack).
- [Spectral necromancer](prison.md#spectralnecromancer) (8-9 (2% alternate of Necromancer)): Top priority target. Burst it from range before it reaches 4 tiles if you can; otherwise charge straight at it, ignoring wraiths except those blocking the...
- [Necromancer's skeleton](prison.md#necromancer-necroskeleton) (8-9 (summoned by Necromancer)): Treat it as a distraction: body-block or walk past it to the necromancer.

### Caves (depths 11-14)

- [Vampire bat](caves.md#bat) (11-14; rare 2.5% on 9): Surprise it asleep if possible. Otherwise back into a corridor or doorway and melee it as it arrives.
- [Gnoll brute](caves.md#brute) (11-14): Sneak-hit it if asleep, then melee it in a corridor. As it hits 0 HP and the rage icon appears, step back and keep moving away along a clear path for about 6...
- [Armored brute](caves.md#armoredbrute) (11-14 (2% alternate of Brute)): Treat it as a damage sponge. Soften it with wands, gas or fire first (bombs are reduced by its DR), then melee only with a high-damage weapon.
- [Gnoll shaman (red)](caves.md#shaman-redshaman) (11-14 (40% of Shaman family)): Close the distance under cover: use corners and doors so it never has a long clear line, or step in while another monster blocks its line.
- [Gnoll shaman (blue)](caves.md#shaman-blueshaman) (11-14 (40% of Shaman family)): Close the distance under cover: use corners and doors so it never has a long clear line, or step in while another monster blocks its line.
- [Gnoll shaman (purple)](caves.md#shaman-purpleshaman) (11-14 (20% of Shaman family)): Close the distance under cover: use corners and doors so it never has a long clear line, or step in while another monster blocks its line.
- [Cave spinner](caves.md#spinner) (12-14): Sneak-hit it asleep if possible. Otherwise wait for it at a corridor mouth and fight standing still.
- [DM-200](caves.md#dm200) (13-14): Meet it in a room and stay adjacent: every turn you are adjacent is a turn it cannot vent.
- [DM-201](caves.md#dm201) (13-14 (2% alternate of DM200)): Either avoid it entirely (it cannot move), or walk adjacent while it is awake and targeting you and melee it down with no ranged damage at all.
- [Gnoll guard](caves.md#gnollguard) (quest (Blacksmith gnoll mine, branch of 12-14)): Take out the linked sapper first (the guard takes quarter damage until then), keeping the guard adjacent or out of line so it never gets its 16-22 reach hit.
- [Gnoll sapper](caves.md#gnollsapper) (quest (Blacksmith gnoll mine, branch of 12-14)): Sneak up while it sleeps and kill it before it acts; its guard then loses its armor.
- [Gnoll geomancer](caves.md#gnollgeomancer) (quest boss (Blacksmith gnoll mine, branch of 12-14)): Clear the mine's sapper/guard pairs first where you can, since each living sapper is a future invulnerability shield.

### Dwarven City (depths 16-19)

- [Dwarven ghoul](city.md#ghoul) (16-18; rare 2.5% on 14): Ghouls come in pairs; if asleep, open with a surprise hit on one. Kill: focus one ghoul, then immediately kill the other before the first revives (5 turns);...
- [Fire elemental](city.md#elemental-fireelemental) (16-19 (about 39% of Elemental rolls); depth 20 is the boss floor with no regular spawns): Fight next to or in water when possible. Kill: close to melee; its zap only ignites, and in melee it has a 50% ignite chance, so stand in water.
- [Frost elemental](city.md#elemental-frostelemental) (16-19 (about 39% of Elemental rolls); depth 20 is the boss floor with no regular spawns): Stay out of water. Kill: close in and burst it down; set it on fire for 30-36 damage.
- [Shock elemental](city.md#elemental-shockelemental) (16-19 (about 20% of Elemental rolls); depth 20 is the boss floor with no regular spawns): Stay out of water and keep allies more than 2 tiles from whoever it hits.
- [Chaos elemental](city.md#elemental-chaoselemental) (16-19 (2% of Elemental rolls, the rare alt); depth 20 is the boss floor with no regular spawns): Its zap never misses, so do not trade at range; close quickly or ambush it asleep.
- [Dwarf warlock](city.md#warlock) (16-19 (depth 20 is the boss floor with no regular spawns)): Never cross open ground in its line of fire; use doors, corners or other mobs as cover, ambush it while it sleeps, or wait beside a door for it to walk through.
- [Dwarf monk](city.md#monk) (17-19): Ambush it asleep for a guaranteed hit; if it is hunting, expect the first attack to be parried.
- [Senior monk](city.md#senior) (17-19 (2% rare alternate of Monk)): Ambush it asleep for a guaranteed hit; if it is hunting, expect the first attack to be parried.
- [Golem](city.md#golem) (18-19 (depth 20 is the boss floor with no regular spawns)): Stay in a corridor or doorway; it cannot enter. Kill: shoot or zap it from the corridor; when it teleports you next to it, step back into the corridor and...

### Demon Halls (depths 21-24)

- [Succubus](halls.md#succubus) (21-24 (2 of 3 rotation slots on 21, 1/2 on 22, 1/4 on 23, 1/6 on 24); on 19 a 2.5% chance per rotation to add one slot): Shoot it while it closes: it only blinks when you are more than 2 tiles away, so the blink doesn't bypass all ranged damage, and surprise shots on a sleeper...
- [Evil eye](halls.md#eye) (21-24 (1 of 3 rotation slots on 21, 1/2 on 22, 2/4 on 23, 2/6 on 24)): Treat the charge animation as a 2-turn warning, but don't just step sideways: while you stay in its view it re-aims at your new cell.
- [Scorpio](halls.md#scorpio) (23-24 (1 of 4 rotation slots on 23, 3 of 6 on 24; each slot has a 2% chance, x Rat Skull, to be an Acidic scorpio instead)): Close the distance using cover (corners, doors, other monsters between you) and fight it adjacent, where it can't attack.
- [Acidic scorpio](halls.md#acidic) (23-24 (each Scorpio rotation slot has a 1/50 chance, x Rat Skull multiplier, to become Acidic)): Close the distance using cover (corners, doors, other monsters between you) and fight it adjacent, where it can't attack.
- [Demon spawner](halls.md#demonspawner) (21-24 (one per floor via DemonSpawnerRoom in HallsLevel)): Enter the spawner room ready to fight a Ripper (one spawns on the spawner's first act, that is, as soon as you arrive on the floor, and again every 40-60...
- [Ripper demon](halls.md#ripperdemon) (21-24 (spawned by Demon Spawner)): Don't run in open ground. Take it at a corner or doorway, or within 2 tiles, so it can't leap, and kill it fast (60 HP, DR 0-4).

### Anywhere: special rooms, traps and summons

- [Swarm of flies](specials.md#swarm) (3-4 and 6 (rotation); splits on hit): Back into a corridor so the swarm and its clones queue up; hit it there, preferring magic (no splits) or big melee hits; burn it if you have fire.
- [Animated statue](specials.md#statue) (any (StatueRoom vault; Distortion trap)): Examine it to learn the weapon, enchantment and reach. Rest to full, stand at the far end of the vault, and wake it with a thrown weapon or wand zap.
- [Armored statue](specials.md#armoredstatue) (any (10% StatueRoom variant, more with Rat Skull; Distortion trap)): Treat it as optional. It drops both its weapon and its armor (identified, uncursed), which is a big reward.
- [Summoned guardian](specials.md#guardian) (caves, city, halls (Guardian trap); count (scalingDepth-5)/5): After triggering, move to a chokepoint away from the trap cell and watch the approaches.
- [Wraith](specials.md#wraith) (any (tombs, haunted remains, Distortion trap, Spectral Necromancer)): Before opening a tomb, stand so few cardinal cells are open and have a wand charged.
- [Tormented spirit](specials.md#tormentedspirit) (any (1/100 of unspecified wraith spawns, more with Rat Skull)): If you carry a Scroll of Remove Curse (even unidentified, if you're willing to test it), step next to the spirit and read it.
- [Giant piranha](specials.md#piranha) (any (PoolRoom x3, AquariumRoom 1-3, Distortion trap); 1 in 50 is a Phantom Piranha): Never enter its water. Stand on land at least 2 tiles from any water it can reach and kill it with thrown weapons, wands or knockback onto land.
- [Phantom piranha](specials.md#phantompiranha) (any (1/50 of piranha spawns, more with Rat Skull)): Optional (the prize is Phantom Meat). Stand on land next to a single water cell and let it come to you, or tag it once with a ranged hit to teleport it next to...
- [Golden bee](specials.md#bee) (any (shattered honeypot; secret honeypot room)): Treat a honeypot as a weapon: throw it into enemies you'd rather not melee.
- [Rot heart](specials.md#rotheart) (quest (Wandmaker rot garden, 7-9)): Follow the lasher-safe path the generator guarantees to reach the heart (it is 7+ steps from the door).
- [Rot lasher](specials.md#rotlasher) (quest (Wandmaker rot garden, 7-9)): Route around lashers. Step only on cells not adjacent to them, and if one tile must be adjacent, move through without stopping.
- [Ratmogrified %s](specials.md#transmograt) (special (hero's Ratmogrify armor ability; helper, not a spawn)): Only relevant if the hero has Ratmogrify (armor ability from giving the King's Crown to the Rat King).
- [Mimic](specials.md#mimic) (any, depth 2+ (1 in 20 random floor-item drops becomes a mimic; treasury rooms; suspicious-chest room 1/3; Distortion trap); Mimic Tooth raises the rates): Examine chests; any with the hint, or any chest in a treasury or suspicious-chest room, gets a thrown or zapped test hit from range first.
- [Golden mimic](specials.md#goldenmimic) (any, depth 2+ (in place of a locked golden chest holding an upgradable item or artifact; CursedWand)): As Mimic, but expect roughly one depth-tier-and-a-third stronger stats.
- [Crystal mimic](specials.md#crystalmimic) (any (CrystalVaultRoom: the second crystal chest is a mimic with chance 1/10, Rat Skull half as effective, Mimic Tooth fully)): In a crystal vault, examine both chests. Stand in the only doorway with a ranged option ready, reveal the mimic with a ranged surprise hit, and keep it in view...
- [Ebony mimic](specials.md#ebonymimic) (any, only with the Mimic Tooth trinket (12.5% + 12.5%/level per floor)): Only exists if you carry a Mimic Tooth. Treat every faint outline on a door, the exit or an item pile as a mimic: hit it from range, then melee it down for the...

### Quest branches and quest foes

- [Crystal guardian](quest-branch.md#crystalguardian) (quest: Blacksmith crystal mine (branch 1 of depth 12-14); one per large mine room): Guardians are an obstacle, not a kill target: they cannot die and give nothing.
- [Crystal wisp](quest-branch.md#crystalwisp) (quest: Blacksmith crystal mine (branch 1 of depth 12-14); the mine's regular spawn): Treat it as a turret you have to break line with. Step behind a crystal or let a guardian/ally stand in the line so it must drift adjacent, then melee it (30...
- [Crystal spire](quest-branch.md#crystalspire) (quest boss: Blacksmith crystal mine (branch 1 of depth 12-14), centre of the giant mine room): Prepare first: find the guardians, note where they sleep, and mine a narrow tunnel to the spire (guardians crawl in enclosed cells).
- [Fungal core](quest-branch.md#fungalcore) (quest boss: Blacksmith fungi mine - NOT reachable in v4.0.0 (quest type rolls only 1-2; fungi = 3 is unimplemented)): Unreachable in normal v4.0.0 play. If it ever appears: clear or avoid adjacent Fungal Sentries' lines, then beat on the core; it does not fight back.
- [Fungal sentry](quest-branch.md#fungalsentry) (quest: Blacksmith fungi mine (centre of each large mine room when the quest type is FUNGI) - NOT reachable in v4.0.0 (fungi quest unimplemented)): Unreachable in normal v4.0.0 play. If present: route around its lines of fire; if it must die, approach along a path with no line until adjacent or attack...
- [Fungal spinner](quest-branch.md#fungalspinner) (quest: Blacksmith fungi mine regular spawn - NOT reachable in v4.0.0 (fungi quest unimplemented)): Unreachable in normal v4.0.0 play. If present: pull it onto bare floor before trading blows, avoid stepping into its regrowth patches, and use ranged attacks...
- [Newborn fire elemental](quest-branch.md#elemental-newbornfireelemental) (quest miniboss: Wandmaker ritual site (depth 7-9), summoned when all 4 ceremonial candles are placed): Light the candles standing next to the ritual centre so you can open with a surprise hit, ideally on or next to water.
- [Newborn fire elemental (ally)](quest-branch.md#elemental-allynewbornelemental) (summoned ally from the Summon Elemental spell (default, un-imbued summon); not a naturally hostile spawn): Friendly summon: keep it between you and enemies; don't fight next to it on grass since its hits can ignite.
- [Wraith (corpse dust)](quest-branch.md#dustwraith) (quest: Wandmaker corpse-dust quest (depth 7-9); spawns around the hero while the dust is carried, on any level): The moment you pick up the dust, walk straight back to the Wandmaker along explored corridors.
- [Greater elemental](quest-branch.md#vaultbosselemental) (quest: Ambitious Imp's dwarven vault (branch 1, entered from depth 17-19); boss of the vault's final room): Bring both a stack of thrown weapons and a melee weapon (and wands if you have them).
- [Modified DM-100](quest-branch.md#vaultdm100) (quest: Ambitious Imp's dwarven vault (branch 1, entered from depth 17-19)): Watch its patrol, come in from behind or the side, and open with a surprise hit; finish it before it gets a clear line.
- [Modified DM-200](quest-branch.md#vaultdm200) (quest: Ambitious Imp's dwarven vault (branch 1, entered from depth 17-19)): Open with a surprise hit if you can, then fall back into a doorway or 1-wide corridor.
- [Fire elemental (vault)](quest-branch.md#vaultelemental-fire) (quest: Ambitious Imp's dwarven vault (branch 1, entered from depth 17-19)): Surprise it from behind its patrol, ideally while you stand in or next to water; finish with frost/chill effects if you have them (each ~half its HP).
- [Frost elemental (vault)](quest-branch.md#vaultelemental-frost) (quest: Ambitious Imp's dwarven vault (branch 1, entered from depth 17-19)): Surprise it on patrol and never fight it on water. A fire source (liquid flame, fire wand, burning grass) deals ~half its HP per application.
- [Shock elemental (vault)](quest-branch.md#vaultelemental-shock) (quest: Ambitious Imp's dwarven vault (branch 1, entered from depth 17-19)): Surprise it on patrol; fight on dry floor with allies spread out. If blinded, back off out of its line until you can see again.
- [Dwarven ghoul (vault)](quest-branch.md#vaultghoul) (quest: Ambitious Imp's dwarven vault (branch 1, entered from depth 17-19)): Surprise it on patrol and finish it before it trades many 16-22 hits. A solo vault ghoul dies for good; if you see two together, separate them or burst both...
- [Golem (vault)](quest-branch.md#vaultgolem) (quest: Ambitious Imp's dwarven vault (branch 1, entered from depth 17-19)): Stealth around it using its patrol facing. If you must fight: surprise hit, then fight from a corridor with ranged attacks, and step back each time it pulls...
- [Marsupial rat (vault)](quest-branch.md#vaultrat) (quest: Ambitious Imp's dwarven vault (branch 1, entered from depth 17-19) - but no v4.0.0 code spawns it (only an unused import in VaultLongRoom)): Not spawned in v4.0.0. If present, one surprise hit or one normal hit kills it.
- [Captured shaman](quest-branch.md#vaultshaman) (quest: Ambitious Imp's dwarven vault (branch 1, entered from depth 17-19)): Sneak up from behind its patrol and open with a surprise hit; kill it in the next turn or two.
- [Dwarven skeleton](quest-branch.md#vaultskeleton) (quest: Ambitious Imp's dwarven vault (branch 1, entered from depth 17-19)): Surprise it on patrol, then either finish with a thrown weapon/wand from 2+ tiles or take the 6-12 burst (double DR) if finishing in melee.

### Bosses and their parts

- [Goo](bosses.md#goo) (boss 5): Arrive healthy with healing. Open with a guaranteed ranged hit on the sleeping Goo.
- [Tengu](bosses.md#tengu) (boss 10): Phase 1: after each jump, note the flashed traps, walk the trap-free path to Tengu and fight him adjacent, where his accuracy halves, until he hits 100 HP.
- [DM-300](bosses.md#dm300) (boss 15): Engage on plain floor and stay adjacent, reacting to each ability: leave the gas cloud, or step to an unmarked cell before rocks land.
- [Power pylon](bosses.md#pylon) (boss 15 (DM-300 arena part; NEUTRAL until activated)): When DM-300 supercharges, find the pylon that turned active (not the one nearest you).
- [King of Dwarves](bosses.md#dwarfking) (boss 20): Enter at full HP and charges; once sealed there is no leaving. Phase 1: hit the King hard (250 HP to the threshold); kill life-linked minions when damage is...
- [Dwarven ghoul (DK summon)](bosses.md#dwarfking-dkghoul) (boss 20 (summoned)): DK ghouls arrive hunting from pedestals and still revive if another ghoul is within view or 3 tiles.
- [Dwarf monk (DK summon)](bosses.md#dwarfking-dkmonk) (boss 20 (summoned)): DK monk: arrives hunting and gains Focus on its first action (cooldown starts at 0).
- [Dwarf warlock (DK summon)](bosses.md#dwarfking-dkwarlock) (boss 20 (summoned)): Kill DK warlocks early in each wave, since their armor-ignoring bolts stack with the other summons.
- [Golem (DK summon)](bosses.md#dwarfking-dkgolem) (boss 20 (summoned, Stronger Bosses only)): DK golems (Stronger Bosses only) fight in the open arena; kill them with heavy hits or wands, and expect to be pulled next to them whenever you stand out of...
- [Yog-Dzewa](bosses.md#yogdzewa) (boss 25): Before 25: kill the Demon Spawners and carry Torches, healing, and root/escape tools.
- [God's larva](bosses.md#yogdzewa-larva) (boss 25 (summoned)): Low-HP chaff: shoot or zap it as it comes out of Yog, or melee it off a beam line.
- [Ripper demon (Yog summon)](bosses.md#yogdzewa-yogripper) (boss 25 (summoned)): Don't run in open ground. Take it at a corner or doorway, or within 2 tiles, so it can't leap, and kill it fast (60 HP, DR 0-4).
- [Evil eye (Yog summon)](bosses.md#yogdzewa-yogeye) (boss 25 (summoned; Stronger Bosses challenge only)): Treat the charge animation as a 2-turn warning, but don't just step sideways: while you stay in its view it re-aims at your new cell.
- [Scorpio (Yog summon)](bosses.md#yogdzewa-yogscorpio) (boss 25 (summoned; Stronger Bosses challenge only)): Close the distance using cover (corners, doors, other monsters between you) and fight it adjacent, where it can't attack.
- [Burning fist](bosses.md#yogfist-burningfist) (boss 25 (Yog fist)): Pull it away from Yog. Stay out of its 3x3 fire aura and break its MAGIC_BOLT line (wall, closed door or a body) to deny zaps.
- [Soiled fist](bosses.md#yogfist-soiledfist) (boss 25 (Yog fist)): Pull it off Yog and check its 3x3: the more furrowed or high grass, the less damage you do (zero at 6 or more).
- [Rotting fist](bosses.md#yogfist-rottingfist) (boss 25 (Yog fist)): Drag it away from Yog and off water. Hit it with large single hits spaced out so each new bleed is bigger than the remaining one.
- [Rusted fist](bosses.md#yogfist-rustedfist) (boss 25 (Yog fist)): Pull it away from Yog and pile on damage. It drains at 10% of stored damage per act, so keep adding and don't expect instant results.
- [Bright fist](bosses.md#yogfist-brightfist) (boss 25 (Yog fist)): Drag it from Yog and fight it adjacent so it can only melee. Expect a 15-turn blind at half HP when it teleports away: stop and stay off Yog's marked lines...
- [Dark fist](bosses.md#yogfist-darkfist) (boss 25 (Yog fist)): Drag it from Yog and fight it adjacent so it can only melee. At half HP it removes your Light and teleports away: relight a Torch and hold position off beam...

## How it was made and checked

Seven readers each took one region, wrote a card per enemy from the code, and a second pass
re-opened every citation at the pin and corrected what did not hold; each card lists its
corrections. The cards cited the working tree, where Shatterfish's hooks shift some lines of
`Hero.java`, `Level.java`, `Actor.java` and a few others, so every citation was then mapped to the
line it occupies at `v4.0.0` (498 of 11,374 moved; none pointed into hook-only lines). Community
claims from the wiki were attached to the cards they are about and graded: 87 confirmed,
50 contradicted, 7 unsettled.

Ten cards were then checked by hand against the code: sewer snake, sewer crab, gnoll shaman, swarm
of flies, Goo, wraith, vampire bat, crazy thief, DM-300 and succubus. Their numbers, abilities and
tactics held. The check found one error the cards shared: a rotation that lists a boss depth is not a
spawn there. The sewer, prison and caves boss floors create no rotation mobs and run no respawner, so
the regular mobs of depths 5, 10 and 15 never appear on them, and 24 cards had their depths corrected. <small>`core/…/actors/mobs/MobSpawner.java:70-155`, `core/…/levels/SewerBossLevel.java:132-138`, `core/…/levels/PrisonBossLevel.java:597-604`, `core/…/levels/CavesBossLevel.java:207-219`</small>
Building the tags fixed a few more inconsistencies, each recorded on its card: eight classes that
never walk are tagged `immobile`, six bolt casters had a reach of 1 (their bolt reaches as far as
they see, 8 cells), four enemies judged evasive only because their evasion led their cohort are not,
and four boss or summon cards with no way out are tagged `none` or `stairs`.

