# Bestiary: Anywhere: special rooms, traps and summons

One card per enemy. Mechanics are Tier 1: read from the pinned code at `v4.0.0` and cited.
Tactics are derived from the mechanics and name the lines they rest on. Community notes are
forum and wiki claims graded against the code: Tier 1 confirmed, Tier F contradicted (struck),
Tier 3 not settled. The [bestiary index](index.md) has the cross-cutting rules, the one-line
tactic for every enemy and the tag vocabulary of `tactics/bestiary.json`.

## Enemies

- [Swarm of flies](#swarm) (3-4 and 6 (rotation); splits on hit): Back into a corridor so the swarm and its clones queue up; hit it there, preferring magic (no splits) or big melee hits; burn it if you have fire.
- [Animated statue](#statue) (any (StatueRoom vault; Distortion trap)): Examine it to learn the weapon, enchantment and reach. Rest to full, stand at the far end of the vault, and wake it with a thrown weapon or wand zap.
- [Armored statue](#armoredstatue) (any (10% StatueRoom variant, more with Rat Skull; Distortion trap)): Treat it as optional. It drops both its weapon and its armor (identified, uncursed), which is a big reward.
- [Summoned guardian](#guardian) (caves, city, halls (Guardian trap); count (scalingDepth-5)/5): After triggering, move to a chokepoint away from the trap cell and watch the approaches.
- [Wraith](#wraith) (any (tombs, haunted remains, Distortion trap, Spectral Necromancer)): Before opening a tomb, stand so few cardinal cells are open and have a wand charged.
- [Tormented spirit](#tormentedspirit) (any (1/100 of unspecified wraith spawns, more with Rat Skull)): If you carry a Scroll of Remove Curse (even unidentified, if you're willing to test it), step next to the spirit and read it.
- [Giant piranha](#piranha) (any (PoolRoom x3, AquariumRoom 1-3, Distortion trap); 1 in 50 is a Phantom Piranha): Never enter its water. Stand on land at least 2 tiles from any water it can reach and kill it with thrown weapons, wands or knockback onto land.
- [Phantom piranha](#phantompiranha) (any (1/50 of piranha spawns, more with Rat Skull)): Optional (the prize is Phantom Meat). Stand on land next to a single water cell and let it come to you, or tag it once with a ranged hit to teleport it next to...
- [Golden bee](#bee) (any (shattered honeypot; secret honeypot room)): Treat a honeypot as a weapon: throw it into enemies you'd rather not melee.
- [Rot heart](#rotheart) (quest (Wandmaker rot garden, 7-9)): Follow the lasher-safe path the generator guarantees to reach the heart (it is 7+ steps from the door).
- [Rot lasher](#rotlasher) (quest (Wandmaker rot garden, 7-9)): Route around lashers. Step only on cells not adjacent to them, and if one tile must be adjacent, move through without stopping.
- [Ratmogrified %s](#transmograt) (special (hero's Ratmogrify armor ability; helper, not a spawn)): Only relevant if the hero has Ratmogrify (armor ability from giving the King's Crown to the Rat King).
- [Mimic](#mimic) (any, depth 2+ (1 in 20 random floor-item drops becomes a mimic; treasury rooms; suspicious-chest room 1/3; Distortion trap); Mimic Tooth raises the rates): Examine chests; any with the hint, or any chest in a treasury or suspicious-chest room, gets a thrown or zapped test hit from range first.
- [Golden mimic](#goldenmimic) (any, depth 2+ (in place of a locked golden chest holding an upgradable item or artifact; CursedWand)): As Mimic, but expect roughly one depth-tier-and-a-third stronger stats.
- [Crystal mimic](#crystalmimic) (any (CrystalVaultRoom: the second crystal chest is a mimic with chance 1/10, Rat Skull half as effective, Mimic Tooth fully)): In a crystal vault, examine both chests. Stand in the only doorway with a ranged option ready, reveal the mimic with a ranged surprise hit, and keep it in view...
- [Ebony mimic](#ebonymimic) (any, only with the Mimic Tooth trinket (12.5% + 12.5%/level per floor)): Only exists if you carry a Mimic Tooth. Treat every faint outline on a door, the exit or an item pile as a mimic: hit it from range, then melee it down for the...

## Rules these cards rely on

Tier 1, cited. Written for this region by the reader who verified its cards; the
[index](index.md#cross-cutting-rules) states the ones every region shares.

- **Speed and time.** Every Char starts at baseSpeed 1; speed() multiplies by modifiers (Cripple /2, Stamina x1.5, Adrenaline x2, Haste x3, Dread x2, armor glyphs Swiftness/Flow/Bulk). Mobs also get the Ascension modifier. A mob's move costs 1/speed() time, so a speed-2 mob takes two steps per hero step. A mob attack costs attackDelay(), 1 by default (Adrenaline /1.5); the hero's attack costs the weapon's delayFactor. <small>`core/…/actors/Char.java:175`, `core/…/actors/Char.java:770-783`, `core/…/mobs/Mob.java:864-867`, `core/…/mobs/Mob.java:1302`, `core/…/mobs/Mob.java:1355`, `core/…/mobs/Mob.java:1460`, `core/…/mobs/Mob.java:753-757`, `core/…/hero/Hero.java:782-792`</small>
- **Accuracy vs evasion.** hit(): acuRoll = Random.Float(attackSkill), defRoll = Random.Float(defenseSkill), hit if acuRoll &gt;= defRoll, after Bless x1.25 / Hex x0.8 / Daze x0.5 and champion factors. An invisible attacker that canSurpriseAttack gets infinite accuracy; the 'magic' overload doubles accuracy. Infinite evasion beats infinite accuracy. DERIVED: with accuracy A &lt;= evasion D the hit chance is about A/(2D); a defender at defenseSkill 0 is always hit. <small>`core/…/actors/Char.java:615-685`</small>
- **Rule.** Hero accuracy is attackSkill (10 at start, +1 per level) x ring/talent multipliers x weapon accuracyFactor. Thrown weapons get x1.5 accuracy when the target is not adjacent and x0.5 (Point Blank talent raises it) when adjacent. <small>`core/…/hero/Hero.java:219`, `core/…/hero/Hero.java:2075`, `core/…/hero/Hero.java:511-565`, `core/…/missiles/MissileWeapon.java:215-233`</small>
- **Surprise attacks.** Mob.defenseSkill returns 0 when surprisedBy(hero) or the mob is paralysed. surprisedBy is true when the attacker is the hero, the hero canSurpriseAttack (meets the weapon's STR requirement, not a flail), and the hero is invisible, or the mob's enemySeen is false, or the hero is outside the mob's field of view. enemySeen is recomputed on every mob act: false while SLEEPING or WANDERING, = enemyInFOV while HUNTING or PASSIVE; enemyInFOV needs the hero visible and not invisible. So sleeping mobs, wandering mobs that have not noticed you, and a hunting mob whose last act did not see you (it rounded a corner or came through a door) take guaranteed hits. The attack then dispels the hero's invisibility. Dagger, Dirk, Assassin's Blade, Kunai and Throwing Knife roll extra damage on a surprised target. <small>`core/…/mobs/Mob.java:782-803`, `core/…/mobs/Mob.java:869-877`, `core/…/hero/Hero.java:744-752`, `core/…/mobs/Mob.java:290`, `core/…/mobs/Mob.java:1237`, `core/…/mobs/Mob.java:1298`, `core/…/mobs/Mob.java:1327`, `core/…/mobs/Mob.java:1495`, `core/…/actors/Char.java:198-202`, `core/…/actors/Char.java:627-630`, `core/…/hero/Hero.java:480-481`, `core/…/hero/Hero.java:2366-2369`, `core/…/melee/Dagger.java:66-71`</small>
- **Detection.** A sleeping mob with a hostile in view wakes with chance 1/(distance + stealth) per turn (a flying hostile 2+ tiles away never wakes it); any negative buff wakes it. A wandering mob notices a visible hero with chance 1/(distance/2 + stealth) per turn. Any damage wakes a sleeper to WANDERING and alerts it; wand, cleric-spell and armor-ability damage aggro it straight onto the hero. Level-generated mobs start SLEEPING (Mob default); respawned mobs start WANDERING. <small>`core/…/mobs/Mob.java:1189-1246`, `core/…/mobs/Mob.java:1269-1295`, `core/…/mobs/Mob.java:902-925`, `core/…/mobs/Mob.java:124`, `core/…/levels/Level.java:774-776`, `core/…/actors/Char.java:1280-1286`</small>
- **Melee reach.** A Mob can attack only an adjacent (8-neighbour) target unless it overrides canAttack or a champion buff grants reach. <small>`core/…/mobs/Mob.java:558-568`</small>
- **Losing pursuit.** A HUNTING mob that cannot see its enemy keeps its target on the last seen cell and walks there; when it cannot get closer (arrived or blocked) and still sees nothing, it shows the lost icon and drops to WANDERING toward a random destination. Breaking line of sight and then moving away from where you were last seen sheds pursuers. <small>`core/…/mobs/Mob.java:1342-1361`, `core/…/mobs/Mob.java:1387-1407`</small>
- **Doors.** A closed DOOR is passable, LOS-blocking and SOLID (so nothing can be spawned or split onto it, and projectiles and bolts collide at it); OPEN_DOOR is passable and not LOS-blocking. Any character (flying ones included) opens a door by entering it; the door shuts again when the last character leaves it unless a heap lies on it. Doors therefore cut line of sight behind you but never stop a mob's path. <small>`core/…/levels/Terrain.java:90-91`, `core/…/levels/Level.java:1214-1216`, `core/…/levels/Level.java:1257-1258`, `core/…/features/Door.java:35-59`, `core/…/actors/Char.java:1311-1313`</small>
- **Stairs.** Enemies never change floors with the hero: on a level change only allies are held and restored; a mob standing on the hero's arrival cell is displaced. With visible enemies, tapping the stairs only walks there; the transition fires when you tap the stairs while standing on them. <small>`core/…/mobs/Mob.java:1686-1705`, `core/…/shatteredpixeldungeon/Dungeon.java:487-503`, `core/…/hero/Hero.java:1991-1998`</small>
- **Line of fire.** Thrown weapons use Ballistica.PROJECTILE (stop at target, first character, or solid terrain); bolts use MAGIC_BOLT (stop at first character or solid). A character in the line takes the shot instead of the target. <small>`core/…/mechanics/Ballistica.java:42-49`, `core/…/mechanics/Ballistica.java:117-131`</small>
- **Magic and direct damage.** Wand zaps and burning call Char.damage() directly: no accuracy roll, no armor DR roll (DR is applied only inside Char.attack) and no defenseProc (so no on-hit reactions such as splitting or gas). Bombs also skip the hit roll and defenseProc, but Bomb.explode subtracts the target's drRoll() before calling damage(), so armor still reduces bomb damage. Cleric spells Guiding Light, Holy Lance and Sunray also call damage() directly. Physical attacks (melee, thrown, spirit arrows) go through Char.attack: hit roll, then defenseProc, then DR. <small>`core/…/wands/WandOfMagicMissile.java:62`, `core/…/bombs/Bomb.java:196-200`, `core/…/buffs/Burning.java:149`, `core/…/spells/GuidingLight.java:86`, `core/…/spells/HolyLance.java:123`, `core/…/spells/Sunray.java:102-110`, `core/…/actors/Char.java:388-512`</small>
- **Paralysis.** A paralysed mob skips its turn and has defenseSkill 0. <small>`core/…/mobs/Mob.java:277-282`, `core/…/mobs/Mob.java:797`</small>
- **Property immunities.** INORGANIC: immune to Bleeding, ToxicGas, Poison. MINIBOSS: immune to AllyBuff (corruption, charm-to-ally), Dread. IMMOVABLE: immune to Vertigo. STATIC: immune to AllyBuff, Dread, Terror, Amok, Charm, Sleep, Paralysis, Frost, Chill, Slow, Speed. UNDEAD and DEMONIC carry no immunities but take bonus damage from holy/light effects (Wand of Prismatic Light x1.333, Holy Dart, Smite max roll, Holy Bomb, Sunray, Holy Lance). <small>`core/…/actors/Char.java:1413-1441`, `core/…/wands/WandOfPrismaticLight.java:96-100`, `core/…/darts/HolyDart.java:64-67`, `core/…/spells/Smite.java:120-127`, `core/…/bombs/HolyBomb.java:72`, `core/…/spells/Sunray.java:100`, `core/…/spells/HolyLance.java:120`</small>
- **Fleeing.** A FLEEING mob steps away from its enemy; out of sight it may trigger escaped() on a roll of 1+Random.Int(distance) &gt;= 6; cornered and not terrified it turns to fight. <small>`core/…/mobs/Mob.java:1437-1487`</small>
- **Rule.** Class starting kits used in classNotes: Warrior worn shortsword + throwing stones + Broken Seal (Healing and Rage identified); Mage staff with Wand of Magic Missile (Upgrade and Liquid Flame identified); Rogue dagger, Cloak of Shadows (invisibility), throwing knives (Magic Mapping and Invisibility identified); Huntress gloves + Spirit Bow (Mind Vision and Lullaby identified); Duelist rapier + 2 throwing spikes (Strength and Mirror Image identified); Cleric cudgel + Holy Tome with Guiding Light available (Purity and Remove Curse identified). <small>`core/…/hero/HeroClass.java:174-188`, `core/…/hero/HeroClass.java:190-201`, `core/…/hero/HeroClass.java:204-218`, `core/…/hero/HeroClass.java:221-230`, `core/…/hero/HeroClass.java:233-245`, `core/…/hero/HeroClass.java:248-260`, `core/…/artifacts/CloakOfShadows.java:306`, `core/…/spells/ClericSpell.java:109`</small>

**Open questions.**

- Hero.stealth() was not traced beyond Char.stealth() (Obfuscation glyph only); class/talent stealth sources are not covered here.

??? note "Rules corrected during verification (3)"

    - Magic/direct-damage rule: bombs are NOT DR-free; Bomb.java:197 subtracts ch.drRoll() before damage() (cite widened to 196-200). Added Cleric spells as direct damage (GuidingLight.java:86, HolyLance.java:123, Sunray.java:102-110).
    - Door rule: Terrain.java:90 flags DOOR as SOLID as well as passable and LOS-blocking; added the consequence.
    - Class-kit rule: completed the identified consumables per HeroClass.java:174-260; Cleric Guiding Light from ClericSpell.java:109.

## Swarm of flies {#swarm}

`actors.mobs.Swarm` · depths 3-4 and 6 (rotation); splits on hit · **Tactic:** Back into a corridor so the swarm and its clones queue up; hit it there, preferring magic (no splits) or big melee hits; burn it if you have fire.

**Stats** (from the [Codex](../codex/mobs.md)): HT 50 · accuracy 10 · evasion 5 · damage 1-4 · armour 0 (no override: only Barkskin adds to it) · EXP 3 · max level 9 · properties none · loot PotionOfHealing (16.7% base). <small>`core/…/mobs/Swarm.java:40`, `core/…/mobs/Swarm.java:123`, `core/…/mobs/Swarm.java:83`, `core/…/actors/Char.java:701`</small>

Depths corrected by hand: the boss floors 5, 10 and 15 create no rotation mobs and run no respawner, so this enemy never spawns there. <small>`core/…/mobs/MobSpawner.java:70-155`, `core/…/levels/SewerBossLevel.java:132-138`, `core/…/levels/PrisonBossLevel.java:597-604`, `core/…/levels/CavesBossLevel.java:207-219`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** No baseSpeed override: 1 move per turn like the hero. Speed `normal`; flies: yes; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/mobs/Swarm.java:51`, `core/…/actors/Char.java:175`, `core/…/levels/Level.java:1174-1217`, `core/…/mobs/Mob.java:1686-1705`</small>
- **Attack.** `melee`, reach 1. Adjacent only (8 neighbours). Attack time: 1 (Mob default). <small>`core/…/mobs/Mob.java:558-568`, `core/…/mobs/Mob.java:753-757`, `core/…/mobs/Swarm.java:83-85`, `core/…/mobs/Swarm.java:122-125`</small>
- **split.** Whenever it is struck through Char.attack (any attacker: hero melee, thrown weapon, spirit arrow, ally, other mob) and HP &gt;= damage+2 (damage measured before armor, as passed to defenseProc) and a free non-solid cardinal neighbour exists, a clone with (HP-damage)/2 HP appears in a random free cardinal cell, already HUNTING, acting after a 1-turn delay; the original loses that HP. Clones give 0 EXP and copy Burning (reignited), Poison (2 turns) and revive-persistent buffs. *Telegraph:* none in the log; the examine text says every non-magical attack splits it (core/…/assets/messages/actors/actors.properties:1817) <small>`core/…/mobs/Swarm.java:57`, `core/…/mobs/Swarm.java:88-120`, `core/…/mobs/Swarm.java:127-143`, `core/…/actors/actors.properties:1817`, `core/…/actors/Char.java:483`</small>
- **AI.** Starts `sleeping`; flees: never. Level-generated swarms sleep (Mob default); respawns arrive wandering; clones hunt immediately. <small>`core/…/mobs/Mob.java:124`, `core/…/levels/Level.java:774-776`, `core/…/mobs/Swarm.java:107`</small>
- **Evasion.** defenseSkill 5 (live formula `5`); evasive: no. Low evasion and accuracy 10; its danger is numbers, not stats. <small>`core/…/mobs/Swarm.java:46`, `core/…/mobs/Swarm.java:123-125`</small>
- **Surprise.** Can be surprised: yes. Standard rules; a surprise hit still splits it (split is in defenseProc, which runs on every hit). <small>`core/…/mobs/Mob.java:869-877`, `core/…/mobs/Swarm.java:88-120`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `chokepoint`; ranged attacks first: no.
- **Plan.** Back into a corridor so the swarm and its clones queue up; hit it there, preferring magic (no splits) or big melee hits; burn it if you have fire. Don't open with thrown weapons in the open, since each hit adds a clone that flanks you. If overwhelmed, retreat through a door or take stairs. <small>`core/…/mobs/Swarm.java:88-120`, `core/…/wands/WandOfMagicMissile.java:62`, `core/…/hero/HeroClass.java:190-201`, `core/…/hero/HeroClass.java:221-230`, `core/…/spells/GuidingLight.java:86`</small>
- **Kill or nullify: Kill it with wand zaps or other direct damage.** Wand/bomb/burn damage calls damage() without defenseProc, so the swarm never splits. (derived) <small>`core/…/wands/WandOfMagicMissile.java:62`, `core/…/actors/Char.java:483`, `core/…/mobs/Swarm.java:88`</small>
- **Kill or nullify: Set it on fire.** Every clone inherits Burning, so one ignition burns the whole family; Burning damages directly. (derived) <small>`core/…/mobs/Swarm.java:131-133`, `core/…/buffs/Burning.java:149`</small>
- **Kill or nullify: Fight it inside a 1-wide corridor.** Clones need a free cardinal neighbour of the swarm; walls and your own cell remove candidates, and only the fly in front of you can reach you. (derived) <small>`core/…/mobs/Swarm.java:93-101`, `core/…/mobs/Mob.java:558-561`</small>
- **Kill or nullify: Prefer big hits.** A hit with damage &gt; HP-2 does not split; heavy weapons make fewer, smaller swarms than fast weak ones. (derived) <small>`core/…/mobs/Swarm.java:90`</small>
- **Kill or nullify: Armor matters more than killing speed.** Accuracy 10 and damage 1-4 per fly; armor cuts both the hit chance and the damage. (derived) <small>`core/…/mobs/Swarm.java:83-85`, `core/…/mobs/Swarm.java:123-125`, `core/…/actors/Char.java:615-685`</small>
- **Kill or nullify: Don't farm it for healing potions.** Each clone's drop chance is 1/(6\*(generation+1)), further scaled by (5 - drops so far)/5 through LimitedDrops.SWARM_HP, so splitting yields sharply diminishing returns and none after five drops. (derived) <small>`core/…/mobs/Swarm.java:145-155`</small>
- **Escape.** Outrunnable: no; contact breaks at: door, out-of-sight, stairs. Same speed as the hero and flying (chasms and water don't stop it). Doors break sight; stairs end it. <small>`core/…/mobs/Swarm.java:51`, `core/…/levels/Terrain.java:90-91`, `core/…/mobs/Mob.java:1686-1705`</small>

| Class | Note |
|---|---|
| Warrior | Corridor melee; the shortsword is fine. Throwing stones only make more flies. |
| Mage | Magic Missile zaps never split it; zap it down, and Liquid Flame burns every clone. |
| Rogue | Surprise hits on a sleeping swarm are guaranteed but still split it. Use them in a corridor. |
| Huntress | Spirit arrows are physical and split it. Shoot only down a corridor where clones have nowhere to go, or switch to melee in the chokepoint. |
| Duelist | Rapier melee in a corridor; keep thrown spikes for other targets. |
| Cleric | Guiding Light (and later Holy Lance / Sunray) calls damage() directly, so it never splits the swarm; use it, then finish in a corridor with the cudgel. |

### Community notes

- **Tier 1.** “Lure a Swarm into a hallway before fighting so it can't surround you, and rely on armor rather than your weapon — melee/physical damage causes it to split in two (each half HP) if there is empty cardinal space, so alternate hitting and retreating, and mop up flies that can no longer split.” [source](https://pixeldungeon.fandom.com/wiki/Swarm_of_flies) (version: old wiki page (flagged oldVersion)). Split needs HP &gt;= damage+2 and a free non-solid cardinal cell; the clone gets (HP-damage)/2 and the original loses the same, so each half is about half the remaining HP. Accuracy 10 and damage 1-4 make armor the key stat. A fly with HP &lt; damage+2 cannot split. <small>`core/…/mobs/Swarm.java:88-120`, `core/…/mobs/Swarm.java:83-85`, `core/…/mobs/Swarm.java:122-125`</small>
- **Tier 1.** “Avoid non-physical damage sources (debuffs, magic, wands) on a Swarm if you want it to keep splitting for extra Potion of Healing drops — those damage types don't trigger the split.” [source](https://pixeldungeon.fandom.com/wiki/Swarm_of_flies) (version: old wiki page (flagged oldVersion)). Confirmed that wand/debuff damage bypasses defenseProc and so never splits. Farming caveat: each clone's drop chance is 1/(6\*(generation+1)) times (5 - SWARM_HP drops)/5, so returns shrink fast and stop after 5 drops. <small>`core/…/mobs/Swarm.java:88`, `core/…/wands/WandOfMagicMissile.java:62`, `core/…/mobs/Swarm.java:145-155`</small>

??? note "Corrected during verification (4)"

    - Resolved the Cleric open question: GuidingLight.java:86, HolyLance.java:123 and Sunray.java:102-110 call damage() directly, so they never split it.
    - Split threshold uses the pre-armor damage passed to defenseProc (Char.java:483); stated explicitly.
    - Added the loot-cap counter (Swarm.java:145-155) needed to grade the community farming claim.
    - Hand check: depths '3-6 (rotation), splits on hit' corrected to '3-4 and 6 (rotation); splits on hit'; the sewer, prison and caves boss floors create no rotation mobs and add no respawner (SewerBossLevel.java:132-138, PrisonBossLevel.java:597-604, CavesBossLevel.java:207-219).

## Animated statue {#statue}

`actors.mobs.Statue` · depths any (StatueRoom vault; Distortion trap) · **Tactic:** Examine it to learn the weapon, enchantment and reach. Rest to full, stand at the far end of the vault, and wake it with a thrown weapon or wand zap.

**Stats** (from the [Codex](../codex/mobs.md)): HT 20 · accuracy `(int)((9 + Dungeon.depth) \* weapon.accuracyFactor( this, target ))` · evasion 5 · damage `weapon.damageRoll(this)` · armour `Random.NormalIntRange(0, Dungeon.depth + weapon.defenseFactor(this))` · EXP 0 · max level 29 · properties INORGANIC. <small>`core/…/mobs/Statue.java:41`, `core/…/mobs/Statue.java:98`, `core/…/mobs/Statue.java:93`, `core/…/mobs/Statue.java:113`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** baseSpeed 1; moves only once HUNTING. Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/actors/Char.java:175`, `core/…/levels/Level.java:1257-1258`, `core/…/mobs/Statue.java:47`</small>
- **Attack.** `melee`, reach 1. Adjacent, or up to its weapon's reach along a path not blocked by walls or characters (Spear/Glaive 2, Whip 3, Projecting adds reach). Attack time: 1 x weapon delayFactor. <small>`core/…/mobs/Statue.java:102-110`, `core/…/items/KindOfWeapon.java:265-277`, `core/…/weapon/Weapon.java:334-346`</small>
- **enchanted weapon.** Wields a random uncursed melee weapon with a random enchantment; the enchantment procs on its hits. *Telegraph:* examine text: 'the &lt;enchanted weapon&gt; it's wielding looks real' <small>`core/…/mobs/Statue.java:63-72`, `core/…/mobs/Statue.java:139-147`, `core/…/actors/actors.properties:1810`</small>
- **dormant until provoked.** Starts PASSIVE and does nothing until it takes damage or gains a negative buff; it ignores beckoning (alarm traps) while passive. Walking up to it or next to it never wakes it. *Telegraph:* none; it looks like a statue with red eyes (core/…/assets/messages/actors/actors.properties:1809) <small>`core/…/mobs/Statue.java:47`, `core/…/mobs/Statue.java:117-136`, `core/…/mobs/Statue.java:149-154`, `core/…/mobs/Mob.java:1489-1498`</small>
- **Inflicts weapon enchantment.** Whatever its weapon's enchantment applies (for example Blazing, Chilling, Shocking). <small>`core/…/mobs/Statue.java:71`, `core/…/mobs/Statue.java:141`</small>
- **Immune.** Bleeding, ToxicGas, Poison (INORGANIC) <small>`core/…/mobs/Statue.java:49`, `core/…/actors/Char.java:1421-1422`</small>
- **Resists.** Grim enchantment <small>`core/…/mobs/Statue.java:198-200`</small>
- **AI.** Starts `passive`; flees: never. PASSIVE -&gt; HUNTING on any damage or negative buff; then normal hunting. <small>`core/…/mobs/Statue.java:117-136`</small>
- **Evasion.** defenseSkill 5 (live formula `4+depth`); evasive: no. 4 + depth (the listed 5 is the depth-1 value); mid-range, scaling with depth. <small>`core/…/mobs/Statue.java:59-60`</small>
- **Surprise.** Can be surprised: yes. While passive it records the hero as seen whenever the hero is in its view (Passive sets enemySeen = enemyInFOV), so the practical surprise openers are invisibility or striking from outside its view. <small>`core/…/mobs/Mob.java:1494-1497`, `core/…/mobs/Mob.java:869-877`, `core/…/actors/Char.java:627-630`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `keep-away`; ranged attacks first: yes.
- **Plan.** Examine it to learn the weapon, enchantment and reach. Rest to full, stand at the far end of the vault, and wake it with a thrown weapon or wand zap. Keep throwing while it walks over, then fight in melee, or kite with paralysis, frost or roots if you have them. Gas is useless on it. It drops its weapon on death. <small>`core/…/mobs/Mob.java:782-794`, `core/…/mobs/Mob.java:912-915`, `core/…/hero/HeroClass.java:204-218`, `core/…/melee/Dagger.java:66-71`</small>
- **Kill or nullify: Skip it if you don't want the weapon.** It never acts while passive; the StatueRoom vault holds nothing else of value. (derived) <small>`core/…/mobs/Statue.java:47`, `core/…/special/StatueRoom.java:43-73`</small>
- **Kill or nullify: Examine it before engaging.** The description names the weapon and its enchantment (the enchantment is never a curse), so you know its reach (Spear/Glaive 2, Whip 3, Projecting +1), its speed and its on-hit proc. (derived) <small>`core/…/mobs/Statue.java:63-72`, `core/…/mobs/Statue.java:189-196`, `core/…/weapon/Weapon.java:416`, `core/…/items/KindOfWeapon.java:265-277`, `core/…/melee/Spear.java:49`, `core/…/melee/Glaive.java:38`, `core/…/melee/Whip.java:45`, `core/…/weapon/Weapon.java:345-346`</small>
- **Kill or nullify: Don't use toxic gas, poison or bleed.** INORGANIC: immune to ToxicGas, Poison and Bleeding. (derived) <small>`core/…/mobs/Statue.java:49`, `core/…/actors/Char.java:1421-1422`</small>
- **Kill or nullify: Paralysis, frost, slow and roots work.** It has no STATIC or MINIBOSS property, and a paralysed mob has 0 defense and skips turns. (derived) <small>`core/…/mobs/Statue.java:49`, `core/…/actors/Char.java:1439-1441`, `core/…/mobs/Mob.java:277-282`, `core/…/mobs/Mob.java:797`</small>
- **Kill or nullify: Soften it with thrown weapons as it closes.** Same speed as the hero; thrown weapons are x1.5 accurate at range and x0.5 adjacent, so use them before contact. (derived) <small>`core/…/missiles/MissileWeapon.java:223-232`, `core/…/actors/Char.java:175`</small>
- **Kill or nullify: Open from invisibility.** An invisible hero's first hit is a guaranteed surprise hit. (derived) <small>`core/…/actors/Char.java:627-630`, `core/…/mobs/Mob.java:873-877`</small>
- **Kill or nullify: Reward check.** On death it drops its weapon, identified and uncursed with that enchantment. (derived) <small>`core/…/mobs/Statue.java:156-164`, `core/…/mobs/Statue.java:70`</small>
- **Escape.** Outrunnable: no; contact breaks at: door, out-of-sight, stairs. Never provoke it if escape matters; once hunting it keeps pace. <small>`core/…/actors/Char.java:175`, `core/…/mobs/Mob.java:1342-1361`, `core/…/mobs/Mob.java:1686-1705`</small>

| Class | Note |
|---|---|
| Warrior | Melee duel, using the Broken Seal armor's shielding. Throw stones as it approaches. |
| Mage | Zap it from across the room (no hit roll, and the zap wakes it), then melee with the staff. |
| Rogue | Cloak or Invisibility for a guaranteed surprise opener with the dagger's surprise damage bonus. |
| Huntress | Arrows from range as it closes, and keep firing once adjacent if the bow outdamages your melee. |
| Duelist | Rapier plus thrown spikes on approach. Weapon abilities are not analysed here. |
| Cleric | Melee; Guiding Light (Illuminated) zeroes the target's defense against the cleric's weapon. |

**Open questions.**

- Which enchantment pool Enchantment.random() draws from (rarity weights) was not read.
- The distribution of weapon tiers from Generator.random(WEAPON) at each depth was not read.

### Community notes

- **Tier F.** “<del>A statue stays inert and won't attack until you first deal damage to it or apply a debuff to it (or trip the Guardian Trap that spawns it); this generally can't happen until you actually try to take the loot behind the locked door, unless you proactively snipe it with a Wand of Disintegration or a Projecting-enchanted thrown weapon from outside its detection.</del>” [source](https://pixeldungeon.fandom.com/wiki/Animated_statue) (version: old wiki page (flagged oldVersion)). Inert-until-damaged-or-debuffed is correct. But in v4.0.0 the statue vault holds no loot besides the statue itself (and nothing wakes it when you walk in), and Guardian-trap guardians are a different subclass that spawn already WANDERING toward you, not inert. <small>`core/…/mobs/Statue.java:117-136`, `core/…/special/StatueRoom.java:43-73`, `core/…/traps/GuardianTrap.java:59-67`</small>
- **Tier 1.** “Animated Statues in statue-vault rooms carry enchanted (and sometimes armored) weapons and are more dangerous than the plain, unenchanted-weapon Summoned Guardians spawned by Guardian Traps elsewhere — size up gear accordingly before opening a statue vault.” [source](https://pixeldungeon.fandom.com/wiki/Animated_statue) (version: old wiki page (flagged oldVersion)). Vault statues get a random enchantment (never a curse) and 10% are Armored Statues with glyphed armor; Guardians get a +0 unenchanted weapon. HP formula (15+5\*depth) is the same for both. <small>`core/…/mobs/Statue.java:63-72`, `core/…/mobs/Statue.java:206-217`, `core/…/traps/GuardianTrap.java:83-89`</small>

??? note "Corrected during verification (1)"

    - evasion.defenseSkill 5 is only the depth-1 value; added defenseSkillFormula 4+depth (Statue.java:60).

## Armored statue {#armoredstatue}

`actors.mobs.ArmoredStatue` · depths any (10% StatueRoom variant, more with Rat Skull; Distortion trap) · **Tactic:** Treat it as optional. It drops both its weapon and its armor (identified, uncursed), which is a big reward.

**Stats** (from the [Codex](../codex/mobs.md)): HT 40 · accuracy `(int)((9 + Dungeon.depth) \* weapon.accuracyFactor( this, target ))` · evasion 5 · damage `weapon.damageRoll(this)` · armour `super.drRoll() + Random.NormalIntRange( armor.DRMin(), armor.DRMax())` · EXP 0 · max level 29 · properties INORGANIC. <small>`core/…/mobs/ArmoredStatue.java:35`, `core/…/mobs/Statue.java:98`, `core/…/mobs/Statue.java:93`, `core/…/mobs/ArmoredStatue.java:74`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** baseSpeed 1. Its armor glyph feeds speed(): Flow speeds it up in water. Swiftness looks inert because the statue itself counts as a nearby ENEMY (DERIVED). Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/mobs/ArmoredStatue.java:89-96`, `core/…/actors/Char.java:778-780`, `core/…/glyphs/Flow.java:41-50`, `core/…/glyphs/Swiftness.java:48-58`</small>
- **Attack.** `melee`, reach 1. As Statue: adjacent or weapon reach. Attack time: 1 x weapon delayFactor. <small>`core/…/mobs/Statue.java:102-110`</small>
- **enchanted weapon + glyphed armor.** Statue weapon plus random uncursed armor with a random glyph; armor adds DR, its glyph procs when the statue is hit by a physical attack (defenseProc), and glyph effects that check glyphLevel also apply (AntiMagic reduces magic damage, Brimstone grants Burning immunity, Stone zeroes its evasion, Flow speeds it in water). Armor weight does not reduce its evasion (the STR penalty is hero-only). *Telegraph:* examine text names weapon and armor including enchant/glyph (core/…/assets/messages/actors/actors.properties:1508) <small>`core/…/mobs/ArmoredStatue.java:50-57`, `core/…/mobs/ArmoredStatue.java:73-76`, `core/…/mobs/ArmoredStatue.java:83-96`, `core/…/mobs/ArmoredStatue.java:109-112`, `core/…/actors/Char.java:942`, `core/…/actors/Char.java:1384`, `core/…/armor/Armor.java:578`, `core/…/actors/actors.properties:1508`, `core/…/armor/Armor.java:413-431`</small>
- **dormant until provoked.** Inherits Statue's PASSIVE start and wake rules. *Telegraph:* none <small>`core/…/mobs/Statue.java:47`, `core/…/mobs/Statue.java:117-136`</small>
- **Inflicts weapon enchantment.** As Statue. <small>`core/…/mobs/Statue.java:141`</small>
- **Immune.** Bleeding, ToxicGas, Poison (INORGANIC) <small>`core/…/mobs/Statue.java:49`, `core/…/actors/Char.java:1421-1422`</small>
- **Resists.** Grim; plus whatever its glyph grants <small>`core/…/mobs/Statue.java:198-200`, `core/…/mobs/ArmoredStatue.java:89-96`</small>
- **AI.** Starts `passive`; flees: never. As Statue. <small>`core/…/mobs/Statue.java:117-136`</small>
- **Evasion.** defenseSkill 5 (live formula `4+depth (0 with a Stone glyph)`); evasive: no. Mob defense 4+depth passed through Armor.evasionFactor. The STR-encumbrance penalty applies only to heroes, so heavy armor does NOT lower a statue's evasion; a Stone glyph sets it to 0; an augment would shift it (randomArmor leaves augment NONE). <small>`core/…/mobs/ArmoredStatue.java:109-112`, `core/…/armor/Armor.java:413-431`, `core/…/mobs/Statue.java:59-60`</small>
- **Surprise.** Can be surprised: yes. As Statue. <small>`core/…/mobs/Mob.java:869-877`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `avoid`; ranged attacks first: yes.
- **Plan.** Treat it as optional. It drops both its weapon and its armor (identified, uncursed), which is a big reward. Fight it only with a real plan: direct-damage wands (bombs still lose to its armor DR), disables, or a clear gear edge. Wake it from range and never with gas. <small>`core/…/mobs/ArmoredStatue.java:73-76`, `core/…/mobs/ArmoredStatue.java:114-122`, `core/…/mobs/Statue.java:156-164`</small>
- **Kill or nullify: Use wands instead of weapons.** Wand damage skips the armor DR roll and the glyph's defenseProc; only an AntiMagic glyph still reduces it. Bombs are worse here: Bomb.explode subtracts drRoll(), which includes the armor's DR. (derived) <small>`core/…/actors/Char.java:388-512`, `core/…/wands/WandOfMagicMissile.java:62`, `core/…/actors/Char.java:941-947`, `core/…/bombs/Bomb.java:196-200`, `core/…/mobs/ArmoredStatue.java:73-87`</small>
- **Kill or nullify: Read the glyph first.** Examine shows it; a glyph that punishes attackers (it procs in defenseProc) makes melee costly, while AntiMagic means stick to weapons. (derived) <small>`core/…/mobs/ArmoredStatue.java:83-87`, `core/…/armor/Armor.java:578`</small>
- **Kill or nullify: Leave it if under-geared.** Double HP plus armor DR; passive until touched. (derived) <small>`core/…/mobs/ArmoredStatue.java:46-47`, `core/…/mobs/Statue.java:47`</small>
- **Kill or nullify: Examine it before engaging.** The description names the weapon and its enchantment (the enchantment is never a curse), so you know its reach (Spear/Glaive 2, Whip 3, Projecting +1), its speed and its on-hit proc. (derived) <small>`core/…/mobs/Statue.java:63-72`, `core/…/mobs/Statue.java:189-196`, `core/…/weapon/Weapon.java:416`, `core/…/items/KindOfWeapon.java:265-277`, `core/…/melee/Spear.java:49`, `core/…/melee/Glaive.java:38`, `core/…/melee/Whip.java:45`, `core/…/weapon/Weapon.java:345-346`</small>
- **Kill or nullify: Don't use toxic gas, poison or bleed.** INORGANIC: immune to ToxicGas, Poison and Bleeding. (derived) <small>`core/…/mobs/Statue.java:49`, `core/…/actors/Char.java:1421-1422`</small>
- **Kill or nullify: Paralysis, frost, slow and roots work.** It has no STATIC or MINIBOSS property, and a paralysed mob has 0 defense and skips turns. (derived) <small>`core/…/mobs/Statue.java:49`, `core/…/actors/Char.java:1439-1441`, `core/…/mobs/Mob.java:277-282`, `core/…/mobs/Mob.java:797`</small>
- **Kill or nullify: Soften it with thrown weapons as it closes.** Same speed as the hero; thrown weapons are x1.5 accurate at range and x0.5 adjacent, so use them before contact. (derived) <small>`core/…/missiles/MissileWeapon.java:223-232`, `core/…/actors/Char.java:175`</small>
- **Escape.** Outrunnable: no; contact breaks at: door, out-of-sight, stairs. As Statue; a Flow glyph makes it faster in water. <small>`core/…/glyphs/Flow.java:41-50`</small>

| Class | Note |
|---|---|
| Warrior | A long melee grind against DR. Pick the fight only if your weapon beats its armor. |
| Mage | Best-placed class: staff zaps ignore its DR. Check it has no AntiMagic glyph. |
| Rogue | A surprise opener helps, but the dagger's low damage struggles against its DR. Usually skip. |
| Huntress | Arrows lose a lot to its DR. Paralysis or roots plus arrows, or skip it. |
| Duelist | Only with a strong weapon; otherwise skip. |
| Cleric | Guiding Light zeroes its defense for the cleric's hits, but DR still applies. |

**Open questions.**

- Swiftness on a statue: the loop in Swiftness.speedBoost appears to count the owner itself as a nearby ENEMY, so the glyph never boosts it; this should be confirmed by a test.

### Community notes

- **Tier 1.** “Animated Statues in statue-vault rooms carry enchanted (and sometimes armored) weapons and are more dangerous than the plain, unenchanted-weapon Summoned Guardians spawned by Guardian Traps elsewhere — size up gear accordingly before opening a statue vault.” [source](https://pixeldungeon.fandom.com/wiki/Animated_statue) (version: old wiki page (flagged oldVersion)). The 'sometimes armored' part: 1 in 10 vault statues (more with Rat Skull) is an Armored Statue with double HP and random glyphed armor. <small>`core/…/mobs/Statue.java:206-217`, `core/…/mobs/ArmoredStatue.java:46-57`</small>

??? note "Corrected during verification (3)"

    - Evasion note was wrong: Armor.evasionFactor applies the STR penalty only when owner is a Hero (Armor.java:420-428); heavy armor does not lower a statue's evasion. Stone glyph zeroes it (Armor.java:416-418).
    - Counter 'wands and bombs skip DR' was wrong for bombs: Bomb.java:197 subtracts drRoll(), and ArmoredStatue.drRoll includes armor DR (ArmoredStatue.java:73-76).
    - defenseSkill 5 is the depth-1 codex value; added formula.

## Summoned guardian {#guardian}

`levels.traps.GuardianTrap.Guardian` · depths caves, city, halls (Guardian trap); count (scalingDepth-5)/5 · **Tactic:** After triggering, move to a chokepoint away from the trap cell and watch the approaches.

**Stats** (from the [Codex](../codex/mobs.md)): HT 20 · accuracy `(int)((9 + Dungeon.depth) \* weapon.accuracyFactor( this, target ))` · evasion 5 · damage `weapon.damageRoll(this)` · armour `Random.NormalIntRange(0, Dungeon.depth + weapon.defenseFactor(this))` · EXP 0 · max level 29 · properties INORGANIC. Some numbers are set when it spawns; the evasion formula below is the live one. <small>`core/…/traps/GuardianTrap.java:72`, `core/…/mobs/Statue.java:98`, `core/…/mobs/Statue.java:93`, `core/…/mobs/Statue.java:113`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** Statue baseSpeed 1. Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/actors/Char.java:175`, `core/…/levels/Level.java:1257-1258`</small>
- **Attack.** `melee`, reach 1. Adjacent, or weapon reach (random +0 weapon, may be a reach weapon). Attack time: 1 x weapon delayFactor. <small>`core/…/mobs/Statue.java:102-110`, `core/…/traps/GuardianTrap.java:83-89`</small>
- **alarm summon.** When the trap fires, every mob on the floor is beckoned to the trap cell, then (scalingDepth-5)/5 guardians (scalingDepth = the floor's depth for a level-generated trap) spawn in random standard rooms other than the entrance room, out of the hero's view, WANDERING toward the hero's position at that moment. In practice 1 guardian on depths 11-14, 2 on 16-19, 3 on 21-24 (the trap is only in the Caves, City and Halls trap lists). *Telegraph:* log warning (alarm) with a scream effect if the trap is in view; the trap itself is a red star (RED/STARS) once revealed <small>`core/…/traps/GuardianTrap.java:40-70`, `core/…/levels/RegularLevel.java:319-341`, `core/…/levels/CavesLevel.java:181`, `core/…/levels/CityLevel.java:128`, `core/…/levels/HallsLevel.java:139`, `core/…/traps/Trap.java:116-118`</small>
- **plain weapon.** A +0 unenchanted default-table melee weapon, dropped on death. *Telegraph:* blue-tinted statue sprite <small>`core/…/traps/GuardianTrap.java:83-89`, `core/…/traps/GuardianTrap.java:104-116`, `core/…/mobs/Statue.java:156-164`</small>
- **Immune.** Bleeding, ToxicGas, Poison (INORGANIC) <small>`core/…/mobs/Statue.java:49`, `core/…/actors/Char.java:1421-1422`</small>
- **Resists.** Grim <small>`core/…/mobs/Statue.java:198-200`</small>
- **AI.** Starts `wandering`; flees: never. Spawns WANDERING toward the hero's position; unlike Statue it answers every beckon (goes WANDERING to the new cell unless already hunting). <small>`core/…/traps/GuardianTrap.java:62-66`, `core/…/traps/GuardianTrap.java:91-100`</small>
- **Evasion.** defenseSkill 5 (live formula `4+depth`); evasive: no. 4 + depth (5 is the depth-1 value). <small>`core/…/mobs/Statue.java:59-60`</small>
- **Surprise.** Can be surprised: yes. It arrives WANDERING, so until it notices you (chance 1/(dist/2+stealth) per turn in view) its enemySeen is false and your hits are surprise hits. <small>`core/…/mobs/Mob.java:1269-1298`, `core/…/mobs/Mob.java:869-877`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `surprise`; ranged attacks first: no.
- **Plan.** After triggering, move to a chokepoint away from the trap cell and watch the approaches. Hit each guardian before it notices you for guaranteed damage, then melee it down. It drops only a +0 weapon, so avoiding it is also fine. Gas does nothing. <small>`core/…/mobs/Mob.java:912-915`, `core/…/melee/Dagger.java:66-71`</small>
- **Kill or nullify: Ambush it at a corner or door.** It walks to where you were while still wandering; hits before it notices you are guaranteed surprise hits. (derived) <small>`core/…/traps/GuardianTrap.java:66`, `core/…/mobs/Mob.java:1298`, `core/…/mobs/Mob.java:782-803`</small>
- **Kill or nullify: Or leave the trigger spot.** Its target is fixed at the hero's position when the trap fired; with no sight of you it reaches that cell and wanders. (derived) <small>`core/…/traps/GuardianTrap.java:91-100`, `core/…/mobs/Mob.java:1297-1310`</small>
- **Kill or nullify: Expect the rest of the floor too.** The trap beckons every mob to the trap cell; fight from a chokepoint rather than at the trap. (derived) <small>`core/…/traps/GuardianTrap.java:48-50`</small>
- **Kill or nullify: No gas or poison; disables work.** INORGANIC, not STATIC. (derived) <small>`core/…/mobs/Statue.java:49`, `core/…/actors/Char.java:1421-1441`</small>
- **Escape.** Outrunnable: no; contact breaks at: door, out-of-sight, stairs. It is only wandering toward a fixed cell, so moving away out of sight usually avoids it entirely. <small>`core/…/mobs/Mob.java:1269-1310`</small>

| Class | Note |
|---|---|
| Warrior | Chokepoint melee. |
| Mage | Zaps; a wand hit aggros it onto you, so zap only when ready to finish it. |
| Rogue | Ideal ambush target; the dagger's surprise damage bonus applies. |
| Huntress | Arrows land as surprise hits while it wanders in unaware. |
| Duelist | Chokepoint melee. |
| Cleric | Chokepoint melee. |

**Open questions.**

- How visible the Guardian trap is before triggering depends on trap discovery (search), which is out of scope here.

### Community notes

- **Tier F.** “<del>A statue stays inert and won't attack until you first deal damage to it or apply a debuff to it (or trip the Guardian Trap that spawns it); this generally can't happen until you actually try to take the loot behind the locked door, unless you proactively snipe it with a Wand of Disintegration or a Projecting-enchanted thrown weapon from outside its detection.</del>” [source](https://pixeldungeon.fandom.com/wiki/Animated_statue) (version: old wiki page (flagged oldVersion)). Guardians are never inert: they spawn WANDERING and are beckoned to the hero's position when the trap fires. <small>`core/…/traps/GuardianTrap.java:59-67`, `core/…/traps/GuardianTrap.java:74-79`</small>
- **Tier 1.** “Animated Statues in statue-vault rooms carry enchanted (and sometimes armored) weapons and are more dangerous than the plain, unenchanted-weapon Summoned Guardians spawned by Guardian Traps elsewhere — size up gear accordingly before opening a statue vault.” [source](https://pixeldungeon.fandom.com/wiki/Animated_statue) (version: old wiki page (flagged oldVersion)). Guardian weapons are +0 and unenchanted (enchant(null), level(0)). <small>`core/…/traps/GuardianTrap.java:83-89`</small>

??? note "Corrected during verification (2)"

    - Guardian count by depth tightened to the floors that can hold the trap (11-14 / 16-19 / 21-24); Trap.scalingDepth cited (Trap.java:116-118). Spawn is in standard rooms excluding the entrance room (RegularLevel.java:329-341).
    - defenseSkill formula added (4+depth).

## Wraith {#wraith}

`actors.mobs.Wraith` · depths any (tombs, haunted remains, Distortion trap, Spectral Necromancer) · **Tactic:** Before opening a tomb, stand so few cardinal cells are open and have a wand charged.

**Stats** (from the [Codex](../codex/mobs.md)): HT 1 · accuracy `10 + level` · evasion 0 · damage `Random.NormalIntRange( 1 + level/2, 2 + level )` · armour 0 (no override: only Barkskin adds to it) · EXP 0 · max level -2 · properties INORGANIC, UNDEAD. Some numbers are set when it spawns; the evasion formula below is the live one. <small>`core/…/mobs/Wraith.java:40`, `core/…/mobs/Wraith.java:81`, `core/…/mobs/Wraith.java:76`, `core/…/actors/Char.java:701`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** baseSpeed 1. Speed `normal`; flies: yes; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/mobs/Wraith.java:54`, `core/…/actors/Char.java:175`, `core/…/levels/Level.java:1214-1216`</small>
- **Attack.** `melee`, reach 1. Adjacent. Attack time: 1. <small>`core/…/mobs/Mob.java:558-568`, `core/…/mobs/Mob.java:753-757`, `core/…/mobs/Wraith.java:75-83`</small>
- **extreme evasion.** defenseSkill = 5 x attackSkill = 5\*(10+L). *Telegraph:* examine: 'very hard to hit with a regular weapon' (core/…/assets/messages/actors/actors.properties:1887) <small>`core/…/mobs/Wraith.java:85-88`, `core/…/actors/actors.properties:1887`</small>
- **ambush spawn.** Spawns already HUNTING with enemySeen=true after a 2-turn delay, fading in with shadow particles. Sources: opening a tomb (one on each free, non-solid cardinal neighbour of the hero, never elsewhere); opening a haunted skeleton or remains heap (one holding a cursed item) spawns 1 at the heap, or on a free neighbour of it, and if no cell is free the hero loses half their current HP instead; Distortion trap; Spectral Necromancer. Soul Mark (Necromancer's Minions talent) wraiths are corrupted allies, not threats. Unspecified-type spawns (tombs, haunted heaps, Distortion trap) are a Tormented Spirit 1% of the time. *Telegraph:* tomb heaps are visible; the haunting is not announced before opening <small>`core/…/mobs/Wraith.java:102-173`, `core/…/items/Heap.java:84-106`, `core/…/items/Heap.java:118-127`, `core/…/levels/RegularLevel.java:443-444`, `core/…/special/CryptRoom.java:71`, `core/…/standard/GrassyGraveRoom.java:67`, `core/…/traps/DistortionTrap.java:113`, `core/…/mobs/SpectralNecromancer.java:149`, `core/…/mobs/Mob.java:1024-1034`, `core/…/mobs/Wraith.java:143-150`</small>
- **Immune.** Bleeding, ToxicGas, Poison (INORGANIC) <small>`core/…/mobs/Wraith.java:57`, `core/…/actors/Char.java:1421-1422`</small>
- **AI.** Starts `hunting`; flees: never. Spawns HUNTING; on level reset it wanders. <small>`core/…/mobs/Wraith.java:156`, `core/…/mobs/Wraith.java:96-100`</small>
- **Evasion.** defenseSkill 0 (live formula `5*(10+scalingDepth)`); evasive: yes. Codex field is 0 until adjustStats sets 5\*(10+L), e.g. 100 at depth 10 and 150 at depth 20. <small>`core/…/mobs/Wraith.java:85-88`, `core/…/mobs/Wraith.java:154`</small>
- **Surprise.** Can be surprised: yes. Not on arrival (enemySeen=true and no FOV array yet), but it can be surprised by an invisible hero or after an act in which it lost sight of you. <small>`core/…/mobs/Wraith.java:88`, `core/…/mobs/Mob.java:873-877`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `keep-away`; ranged attacks first: yes.
- **Plan.** Before opening a tomb, stand so few cardinal cells are open and have a wand charged. In the 2-turn spawn delay, zap or throw at range; each hit kills. Once adjacent, prefer wand zaps over melee swings, or break line of sight at a door and strike it as it comes through (a surprise hit). Gas does nothing. <small>`core/…/mobs/Wraith.java:49`, `core/…/missiles/MissileWeapon.java:223-232`, `core/…/actors/Char.java:627-630`, `core/…/hero/HeroClass.java:190-201`, `core/…/hero/HeroClass.java:204-218`</small>
- **Kill or nullify: Kill it with any wand zap or direct damage.** HP 1 and no DR; wand, bomb and burn damage have no hit roll, so any of them kills. (derived) <small>`core/…/mobs/Wraith.java:49`, `core/…/actors/Char.java:701-707`, `core/…/wands/WandOfMagicMissile.java:62`, `core/…/bombs/Bomb.java:200`, `core/…/buffs/Burning.java:149`</small>
- **Kill or nullify: Don't count on melee accuracy.** Evasion is 5x its accuracy; at hero accuracy A vs evasion D the hit chance is about A/(2D), roughly 10% for a level-10 hero against a depth-10 wraith (evasion 100). (derived) <small>`core/…/mobs/Wraith.java:85-88`, `core/…/actors/Char.java:646-684`</small>
- **Kill or nullify: Make it a surprise hit.** Surprise sets its defense to 0: attack while invisible, or when it steps adjacent after acting without seeing you (around a corner or door). (derived) <small>`core/…/mobs/Mob.java:782-803`, `core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:1327`</small>
- **Kill or nullify: Throw at range, not adjacent.** Thrown accuracy is x1.5 at range and x0.5 adjacent; one hit kills. (derived) <small>`core/…/missiles/MissileWeapon.java:223-232`, `core/…/mobs/Wraith.java:49`</small>
- **Kill or nullify: Use holy/light effects.** UNDEAD: Prismatic Light x1.333, Holy Dart, Smite, Sunray, Holy Bomb target it (any damage kills anyway). (derived) <small>`core/…/mobs/Wraith.java:56`, `core/…/wands/WandOfPrismaticLight.java:96-100`</small>
- **Kill or nullify: Gas and poison do nothing.** INORGANIC. (derived) <small>`core/…/mobs/Wraith.java:57`, `core/…/actors/Char.java:1421-1422`</small>
- **Kill or nullify: Open tombs with walls on your cardinal sides.** A tomb spawns one wraith on each free, non-solid cardinal neighbour of the hero (never elsewhere); a tomb can be opened from an adjacent or diagonal cell. (derived) <small>`core/…/mobs/Wraith.java:106-110`, `core/…/mobs/Wraith.java:120-138`, `core/…/items/Heap.java:86-88`, `core/…/hero/Hero.java:1195-1197`</small>
- **Kill or nullify: Use the 2-turn spawn delay.** Spawned wraiths first act 2 turns later: time for one or two zaps, or to step to a corridor. (derived) <small>`core/…/mobs/Wraith.java:42`, `core/…/mobs/Wraith.java:157`</small>
- **Escape.** Outrunnable: no; contact breaks at: door, out-of-sight, stairs. Flying, normal speed; chasms and water don't stop it. <small>`core/…/mobs/Wraith.java:54`</small>

| Class | Note |
|---|---|
| Warrior | Poor accuracy against it. Throw stones at range before contact, fight at doors for surprise hits, or avoid tombs without a wand. |
| Mage | Best counter: every Magic Missile zap kills one. |
| Rogue | Cloak of Shadows: attacks from invisibility always hit, and one hit kills. |
| Huntress | Spirit arrows at range get x1.5 accuracy and one hit kills. Shoot before it closes. |
| Duelist | Throwing spikes at range; melee only with surprise. |
| Cleric | Undead: holy spells work, and any hit kills. Avoid long melee exchanges. |

**Open questions.**

- Whether a human can tell a haunted skeleton/remains heap before opening it: setHauntedIfCursed marks the cursed item cursedKnown, but what the heap sprite or info shows before opening was not traced.
- Soul Mark / Spirit Form / Dried Rose wraiths are allies and are not threats; Dust wraiths (CorpseDust.DustWraith) are a separate class.

### Community notes

- **Tier 1.** “Before disturbing a Wraith-spawning tomb, stand with your back to a wall, statue, or room corner — wraiths spawn only in the four cardinal tiles around you, so blocking some of those tiles with terrain caps the number that can appear at once to 3 or fewer.” [source](https://pixeldungeon.fandom.com/wiki/Wraith) (version: old wiki page (flagged oldVersion)). Tomb wraiths spawn only on the hero's 4 cardinal neighbours that are not solid and unoccupied (allowAdjacent=false), so walls cap the count. Applies to tombs only; haunted skeletons spawn one wraith at the heap. <small>`core/…/items/Heap.java:86-88`, `core/…/mobs/Wraith.java:106-110`, `core/…/mobs/Wraith.java:120-138`</small>
- **Tier F.** “<del>Wraiths have very high evasion, so melee attacks miss often; they're described as weak to magic-type damage (including fire and poison), and this source claims a Wraith only has 1 HP so any connecting hit — including a guaranteed surprise attack performed by retreating behind a door — kills it outright. Treat the '1 HP' figure with caution since it's stated without a version and may not reflect the current build's stats.</del>” [source](https://pixeldungeon.fandom.com/wiki/User_blog:108.184.82.95/How_to_kill_Wraiths) (version: old wiki page (flagged oldVersion)). 1 HP, very high evasion (5x accuracy), and a guaranteed surprise hit all confirmed in v4.0.0; fire works (no Burning immunity). Poison is wrong: wraiths are INORGANIC and immune to Poison and ToxicGas. <small>`core/…/mobs/Wraith.java:49`, `core/…/mobs/Wraith.java:85-88`, `core/…/mobs/Wraith.java:57`, `core/…/actors/Char.java:1421-1422`</small>

??? note "Corrected during verification (2)"

    - Haunted-heap spawn: Wraith.spawnAt(pos) allows an adjacent cell when the heap cell is occupied (Wraith.java:120-138); only if every cell is blocked does the hero lose HP/2 (Heap.java:96-104).
    - Marked Soul Mark wraiths as corrupted allies (Mob.java:1024-1034); added defenseSkillFormula.

## Tormented spirit {#tormentedspirit}

`actors.mobs.TormentedSpirit` · depths any (1/100 of unspecified wraith spawns, more with Rat Skull) · **Tactic:** If you carry a Scroll of Remove Curse (even unidentified, if you're willing to test it), step next to the spirit and read it.

**Stats** (from the [Codex](../codex/mobs.md)): HT 1 · accuracy `10 + Math.round(1.5f\*level)` · evasion 0 · damage `Random.NormalIntRange( 1 + Math.round(1.5f\*level)/2, 2 + Math.round(1.5f\*level) )` · armour 0 (no override: only Barkskin adds to it) · EXP 0 · max level -2 · properties INORGANIC, UNDEAD. Some numbers are set when it spawns; the evasion formula below is the live one. <small>`core/…/mobs/TormentedSpirit.java:38`, `core/…/mobs/TormentedSpirit.java:52`, `core/…/mobs/TormentedSpirit.java:46`, `core/…/actors/Char.java:701`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** Inherits Wraith. Speed `normal`; flies: yes; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/mobs/Wraith.java:54`</small>
- **Attack.** `melee`, reach 1. Adjacent. Attack time: 1. <small>`core/…/mobs/TormentedSpirit.java:44-54`, `core/…/mobs/Mob.java:558-568`</small>
- **cleansable curse.** Reading a Scroll of Remove Curse while it is adjacent (any of the 8 neighbours) consumes the scroll and frees the spirit: it drops a random enchanted weapon or glyphed armor, uncursed and known-uncursed, 50% chance of +1 if +0. Killing it gives nothing (EXP 0, no loot). *Telegraph:* spawns with a challenge-particle burst instead of shadow particles; its description hints at the cure (core/…/assets/messages/actors/actors.properties:1846) <small>`core/…/mobs/TormentedSpirit.java:56-84`, `core/…/scrolls/ScrollOfRemoveCurse.java:52-78`, `core/…/mobs/Wraith.java:163-167`, `core/…/actors/actors.properties:1846`, `core/…/mobs/Wraith.java:49-50`</small>
- **stronger wraith.** 50% more accuracy (and therefore evasion) and damage scaling than a wraith. *Telegraph:* distinct sprite <small>`core/…/mobs/TormentedSpirit.java:44-54`, `core/…/mobs/Wraith.java:85-88`</small>
- **Immune.** Bleeding, ToxicGas, Poison (INORGANIC) <small>`core/…/mobs/Wraith.java:57`, `core/…/actors/Char.java:1421-1422`</small>
- **AI.** Starts `hunting`; flees: never. Spawned through Wraith.spawnAt: HUNTING after a 2-turn delay. <small>`core/…/mobs/Wraith.java:143-157`</small>
- **Evasion.** defenseSkill 0 (live formula `5*(10+round(1.5*scalingDepth))`); evasive: yes. 5\*(10+round(1.5L)), e.g. 125 at depth 10. <small>`core/…/mobs/TormentedSpirit.java:50-54`, `core/…/mobs/Wraith.java:87`</small>
- **Surprise.** Can be surprised: yes. As Wraith. <small>`core/…/mobs/Mob.java:873-877`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `close`; ranged attacks first: no.
- **Plan.** If you carry a Scroll of Remove Curse (even unidentified, if you're willing to test it), step next to the spirit and read it. That is the best outcome. Otherwise handle it like a wraith that hits harder and dodges more: wand zap it (1 HP) or use a surprise hit. <small>`core/…/hero/HeroClass.java:248-260`, `core/…/scrolls/ScrollOfRemoveCurse.java:55-74`</small>
- **Kill or nullify: Cure it instead of killing it.** Remove Curse read next to it yields a free enchanted, uncursed item; killing yields nothing. (derived) <small>`core/…/mobs/TormentedSpirit.java:56-84`, `core/…/scrolls/ScrollOfRemoveCurse.java:55-74`</small>
- **Kill or nullify: Kill it with any wand zap or direct damage.** HP 1 and no DR; wand, bomb and burn damage have no hit roll, so any of them kills. (derived) <small>`core/…/mobs/Wraith.java:49`, `core/…/actors/Char.java:701-707`, `core/…/wands/WandOfMagicMissile.java:62`, `core/…/bombs/Bomb.java:200`, `core/…/buffs/Burning.java:149`</small>
- **Kill or nullify: Don't count on melee accuracy.** Evasion is 5x its accuracy, 5\*(10+round(1.5L)) = 125 at depth 10; at hero accuracy A vs evasion D the hit chance is about A/(2D), roughly 8% for a level-10 hero (accuracy 19) at depth 10. (derived) <small>`core/…/mobs/TormentedSpirit.java:50-54`, `core/…/mobs/Wraith.java:85-88`, `core/…/actors/Char.java:646-684`</small>
- **Kill or nullify: Make it a surprise hit.** Surprise sets its defense to 0: attack while invisible, or when it steps adjacent after acting without seeing you (around a corner or door). (derived) <small>`core/…/mobs/Mob.java:782-803`, `core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:1327`</small>
- **Kill or nullify: Throw at range, not adjacent.** Thrown accuracy is x1.5 at range and x0.5 adjacent; one hit kills. (derived) <small>`core/…/missiles/MissileWeapon.java:223-232`, `core/…/mobs/Wraith.java:49`</small>
- **Kill or nullify: Use holy/light effects.** UNDEAD: Prismatic Light x1.333, Holy Dart, Smite, Sunray, Holy Bomb target it (any damage kills anyway). (derived) <small>`core/…/mobs/Wraith.java:56`, `core/…/wands/WandOfPrismaticLight.java:96-100`</small>
- **Kill or nullify: Gas and poison do nothing.** INORGANIC. (derived) <small>`core/…/mobs/Wraith.java:57`, `core/…/actors/Char.java:1421-1422`</small>
- **Kill or nullify: Use the 2-turn spawn delay.** It arrives through Wraith.spawnAt and first acts 2 turns later: time for one or two zaps, or to step next to it with a Remove Curse scroll ready. (derived) <small>`core/…/mobs/Wraith.java:42`, `core/…/mobs/Wraith.java:157`</small>
- **Escape.** Outrunnable: no; contact breaks at: door, out-of-sight, stairs. As Wraith. <small>`core/…/mobs/Wraith.java:54`</small>

| Class | Note |
|---|---|
| Warrior | No reliable hit without surprise. Use a Remove Curse scroll if you have one, or retreat. |
| Mage | One zap kills it, but curing pays better if a scroll is on hand. |
| Rogue | An invisible attack kills it, but curing pays better. |
| Huntress | Arrows at range if you can't cure it. |
| Duelist | Cure it, or throw spikes. |
| Cleric | Starts with Remove Curse identified: walk up and cure it. |

**Open questions.**

- The ScrollOfRemoveCurse path identifies the scroll and skips its normal effect; whether an unidentified Remove Curse can be read deliberately for this purpose is a player-knowledge question, not a code one.

### Community notes

- **Tier 1.** “Before disturbing a Wraith-spawning tomb, stand with your back to a wall, statue, or room corner — wraiths spawn only in the four cardinal tiles around you, so blocking some of those tiles with terrain caps the number that can appear at once to 3 or fewer.” [source](https://pixeldungeon.fandom.com/wiki/Wraith) (version: old wiki page (flagged oldVersion)). Applies to tormented spirits too: tomb spawns use the same spawnAround and 1% of them are Tormented Spirits. <small>`core/…/mobs/Wraith.java:143-150`</small>

??? note "Corrected during verification (2)"

    - Melee-accuracy counter was copied from Wraith (evasion 100); recomputed for the spirit (125 at depth 10, ~8% for a level-10 hero).
    - Adjacency for cleansing is NEIGHBOURS8 (ScrollOfRemoveCurse.java:55-60); added defenseSkillFormula.

## Giant piranha {#piranha}

`actors.mobs.Piranha` · depths any (PoolRoom x3, AquariumRoom 1-3, Distortion trap); 1 in 50 is a Phantom Piranha · **Tactic:** Never enter its water. Stand on land at least 2 tiles from any water it can reach and kill it with thrown weapons, wands or knockback onto land.

**Stats** (from the [Codex](../codex/mobs.md)): HT 15 · accuracy `20 + Dungeon.depth \* 2` · evasion 12 · damage `Random.NormalIntRange( Dungeon.depth, 4 + Dungeon.depth \* 2 )` · armour `Random.NormalIntRange(0, Dungeon.depth)` · EXP 0 · max level 29 · properties none · loot MysteryMeat (100% base). <small>`core/…/mobs/Piranha.java:42`, `core/…/mobs/Piranha.java:89`, `core/…/mobs/Piranha.java:84`, `core/…/mobs/Piranha.java:94`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** baseSpeed 2: two water steps per hero turn. Pathing is restricted to water cells. Speed `fast`; flies: no; amphibious: no; water-bound: yes; opens doors: no; follows you on the stairs: no. <small>`core/…/mobs/Piranha.java:47`, `core/…/mobs/Piranha.java:132-157`, `core/…/mobs/Piranha.java:69-81`, `core/…/levels/Level.java:1219-1221`</small>
- **Attack.** `melee`, reach 1. Adjacent from its water cell, so it can bite a hero standing on the shore next to water. Attack time: 1. <small>`core/…/mobs/Mob.java:558-568`, `core/…/mobs/Piranha.java:84-91`</small>
- **water-only hunter.** It treats the hero as unseen unless a water path of length &lt;= viewDistance connects them (the path may end at the hero's cell even on land). It dies instantly if it ends up on a non-water cell or flying. *Telegraph:* none; sleeps in water until woken <small>`core/…/mobs/Piranha.java:168-204`, `core/…/mobs/Piranha.java:69-81`, `core/…/levels/Level.java:1219-1221`, `SPD-classes/…/utils/PathFinder.java:248-282`</small>
- **surprise rule override.** The hero surprises it if it is asleep, the hero is outside its raw field of view, or the hero is invisible, whether or not it saw the hero before. *Telegraph:* none <small>`core/…/mobs/Piranha.java:98-108`</small>
- **Immune.** Every blob in BlobImmunity except Electricity and Freezing (toxic, paralytic, confusion, corrosive gas, fire, web, etc.), plus Burning <small>`core/…/mobs/Piranha.java:159-166`, `core/…/buffs/BlobImmunity.java:63-77`</small>
- **AI.** Starts `sleeping`; flees: never. Starts SLEEPING; its sleep/wander/hunt states all use the water-path sight rule. <small>`core/…/mobs/Piranha.java:54-58`, `core/…/mobs/Piranha.java:168-204`</small>
- **Evasion.** defenseSkill 12 (live formula `10+2*depth`); evasive: yes. 10+2\*depth (12 is the depth-1 value), and accuracy 20+2\*depth: high for its depth. <small>`core/…/mobs/Piranha.java:65-66`, `core/…/mobs/Piranha.java:88-91`</small>
- **Surprise.** Can be surprised: yes. Sleeping, or hero invisible or outside its FOV. <small>`core/…/mobs/Piranha.java:98-108`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `keep-away`; ranged attacks first: yes.
- **Plan.** Never enter its water. Stand on land at least 2 tiles from any water it can reach and kill it with thrown weapons, wands or knockback onto land. To loot a pool room, use the Potion of Invisibility that comes with it, or levitation. Each kill drops mystery meat. <small>`core/…/mobs/Piranha.java:132-146`, `core/…/special/PoolRoom.java:91`, `core/…/hero/HeroClass.java:221-230`</small>
- **Kill or nullify: Stay 2+ tiles from its water and use ranged attacks.** It can only reach you through water and bite adjacent. Two tiles from water it can neither reach nor 'see' you, so it cannot retaliate. (derived) <small>`core/…/mobs/Piranha.java:132-146`, `core/…/mobs/Piranha.java:193-203`, `core/…/mobs/Mob.java:558-561`</small>
- **Kill or nullify: Push it onto land.** Any non-water cell kills it instantly (Blast Wave knockback, for example). (derived) <small>`core/…/levels/Level.java:1219-1221`, `core/…/mobs/Piranha.java:69-81`</small>
- **Kill or nullify: Cross pools invisible.** The PoolRoom level also spawns a Potion of Invisibility; an invisible hero is not an enemy in view and is always surprise-hit material. (derived) <small>`core/…/special/PoolRoom.java:91`, `core/…/mobs/Mob.java:290`, `core/…/mobs/Piranha.java:105`</small>
- **Kill or nullify: Freeze or shock it.** Of the blobs, only Freezing and Electricity get through its immunities; gas and fire do nothing. (derived) <small>`core/…/mobs/Piranha.java:159-166`</small>
- **Kill or nullify: Snipe it while it sleeps.** A sleeping piranha is surprised: 0 defense, so thrown hits land. (derived) <small>`core/…/mobs/Piranha.java:105`, `core/…/mobs/Mob.java:782-803`</small>
- **Kill or nullify: Never fight it from water or swim past it.** Speed 2 in water, high accuracy and damage for its depth. (derived) <small>`core/…/mobs/Piranha.java:47`, `core/…/mobs/Piranha.java:84-91`</small>
- **Kill or nullify: Don't levitate over its water next to it.** Its sight test builds the water path from the hero's own cell whatever the terrain, and canAttack only checks adjacency, so a levitating hero beside or above its water can still be bitten. (derived) <small>`core/…/mobs/Piranha.java:193-203`, `core/…/mobs/Mob.java:558-568`</small>
- **Kill or nullify: Fishing spears and lightning are the specialist tools.** A Fishing Spear hit deals at least half the piranha's current HP; Wand of Lightning arcs 2 tiles from a target in water and deals full damage to every arc target when the main target is in water. (derived) <small>`core/…/missiles/FishingSpear.java:39-45`, `core/…/wands/WandOfLightning.java:81-84`, `core/…/wands/WandOfLightning.java:143-160`</small>
- **Escape.** Outrunnable: yes; contact breaks at: leave-water, out-of-sight. It cannot leave water: step onto land away from the water's edge and it can't follow. In water it is twice your speed. <small>`core/…/mobs/Piranha.java:132-157`, `core/…/mobs/Piranha.java:47`</small>

| Class | Note |
|---|---|
| Warrior | Throwing stones from 2+ tiles inland, or simply ignore it. |
| Mage | Zap from inland. Its fire/gas immunities make Liquid Flame useless here. |
| Rogue | Cloak of Shadows lets you wade past, and invisible attacks always hit. |
| Huntress | Spirit Bow from 2+ tiles inland: free kills. |
| Duelist | Throwing spikes from inland. |
| Cleric | Ranged spells from inland; don't melee from the shore. |

### Community notes

- **Tier F.** “<del>Avoid melee entirely — Giant Piranhas hit hard in the water. Ranged weapons work but their high HP/evasion make it slow; Fishing Spears specifically get greatly increased accuracy and damage against piranhas. The best plan is often to just avoid the fight: every flooded-vault floor spawns a Potion of Invisibility elsewhere on the level specifically so you can slip past them to the treasure.</del>” [source](https://pixeldungeon.fandom.com/wiki/Giant_piranha) (version: old wiki page (flagged oldVersion)). Invisibility potion per pool-room floor and high HP/evasion confirmed. Fishing Spear gets only a damage bonus (at least half the piranha's current HP); there is no accuracy bonus in v4.0.0. <small>`core/…/missiles/FishingSpear.java:39-45`, `core/…/special/PoolRoom.java:91`, `core/…/mobs/Piranha.java:65-66`</small>
- **Tier 1.** “As Huntress, prop the piranha-room door open and kill them with a thrown boomerang from outside the water — this avoids spending an Invisibility potion; lightning-type damage hits multiple grouped piranhas at once if you can lure them together.” [source](https://spd-huntress-guide.pages.dev/guide) (version: unspecified (current-era source)). Attacking from a cell with no water path to the piranha leaves it unable to see or reach you. Lightning arcs 2 tiles from targets in water and deals full damage to all when the main target is in water. The Huntress's current kit is the Spirit Bow (the boomerang is a generic thrown weapon, not her starter); propping the door is irrelevant because piranhas never leave water. <small>`core/…/mobs/Piranha.java:193-203`, `core/…/wands/WandOfLightning.java:81-84`, `core/…/wands/WandOfLightning.java:143-160`</small>
- **Tier F.** “<del>The Flooded Vault special room is guarded by 3 Giant Piranhas. Piranhas die instantly if kept out of water for a single turn, so luring them onto dry land (or draining the pool) kills them outright; every floor containing a flooded vault also generates a Potion of Invisibility elsewhere on that floor, letting you sneak past the piranhas entirely to reach the vault's chest instead of fighting them in the water where they have the advantage.</del>” [source](https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Special_Rooms) (version: unspecified (current-era source)). Three piranhas and the Invisibility potion are confirmed, and a piranha on land dies at once. But you cannot lure one onto land: its pathing only uses water cells. Only knockback, teleport or terrain change puts it on land, and 1 in 50 is a Phantom Piranha that teleports back to water instead of dying. <small>`core/…/special/PoolRoom.java:39`, `core/…/special/PoolRoom.java:91`, `core/…/mobs/Piranha.java:132-146`, `core/…/levels/Level.java:1219-1221`</small>

??? note "Corrected during verification (3)"

    - Resolved the levitation open question as DERIVED from Piranha.java:193-203 and Mob.java:558-568 (bitten while adjacent).
    - Added Fishing Spear and Wand of Lightning counters from code, needed to grade community claims.
    - defenseSkill 12 is the depth-1 value; added formula.

## Phantom piranha {#phantompiranha}

`actors.mobs.PhantomPiranha` · depths any (1/50 of piranha spawns, more with Rat Skull) · **Tactic:** Optional (the prize is Phantom Meat). Stand on land next to a single water cell and let it come to you, or tag it once with a ranged hit to teleport it next to you, then melee it at full damage.

**Stats** (from the [Codex](../codex/mobs.md)): HT 15 · accuracy `20 + Dungeon.depth \* 2` · evasion 12 · damage `Random.NormalIntRange( Dungeon.depth, 4 + Dungeon.depth \* 2 )` · armour `Random.NormalIntRange(0, Dungeon.depth)` · EXP 0 · max level 29 · properties none · loot PhantomMeat (100% base). <small>`core/…/mobs/PhantomPiranha.java:40`, `core/…/mobs/Piranha.java:89`, `core/…/mobs/Piranha.java:84`, `core/…/mobs/Piranha.java:94`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** baseSpeed 2: two water steps per hero turn. Pathing is restricted to water cells. Speed `fast`; flies: no; amphibious: no; water-bound: yes; opens doors: no; follows you on the stairs: no. <small>`core/…/mobs/Piranha.java:47`, `core/…/mobs/Piranha.java:132-157`, `core/…/mobs/Piranha.java:69-81`, `core/…/levels/Level.java:1219-1221`</small>
- **Attack.** `melee`, reach 1. Adjacent from water. Attack time: 1. <small>`core/…/mobs/Mob.java:558-568`</small>
- **teleport on hit.** Damage from a source not adjacent to it (or with no source) is halved. If it survives, it teleports into a free water cell next to the attacker and aggroes (wands and cleric spells count the hero as the source); if there is no such water, it teleports away to a random water cell, preferring one out of the hero's view. *Telegraph:* 'The phantom piranha teleports away...' in the log when it vanishes (core/…/assets/messages/actors/actors.properties:1745) <small>`core/…/mobs/PhantomPiranha.java:49-80`, `core/…/mobs/PhantomPiranha.java:94-123`, `core/…/actors/actors.properties:1745`</small>
- **does not die on land.** Put on land (or found out of water when it acts), it teleports to a random free water cell instead of dying. It dies only if it is flying (levitating) or there is no free water cell on the floor. *Telegraph:* none <small>`core/…/mobs/PhantomPiranha.java:87-92`</small>
- **Immune.** As Piranha (blobs except Electricity/Freezing, plus Burning) <small>`core/…/mobs/Piranha.java:159-166`</small>
- **Resists.** Half damage from non-adjacent sources <small>`core/…/mobs/PhantomPiranha.java:55-57`</small>
- **AI.** Starts `sleeping`; flees: never. As Piranha. <small>`core/…/mobs/Piranha.java:58`</small>
- **Evasion.** defenseSkill 12 (live formula `10+2*depth`); evasive: yes. As Piranha. <small>`core/…/mobs/Piranha.java:65-66`</small>
- **Surprise.** Can be surprised: yes. As Piranha. <small>`core/…/mobs/Piranha.java:98-108`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `close`; ranged attacks first: no.
- **Plan.** Optional (the prize is Phantom Meat). Stand on land next to a single water cell and let it come to you, or tag it once with a ranged hit to teleport it next to you, then melee it at full damage. Otherwise ignore it and stay 2+ tiles from water. <small>`core/…/mobs/PhantomPiranha.java:49-80`</small>
- **Kill or nullify: Fight it adjacent, from land beside the water.** Adjacent hits deal full damage and don't teleport it. (derived) <small>`core/…/mobs/PhantomPiranha.java:55-62`</small>
- **Kill or nullify: Pull it to you with one ranged hit.** Stand on land next to water: a ranged hit (halved) teleports it into a free water cell next to you and aggroes it; then melee it at full damage. (derived) <small>`core/…/mobs/PhantomPiranha.java:61-71`</small>
- **Kill or nullify: Don't bother sniping from inland.** With no water beside you it halves the damage and teleports away, often out of sight. (derived) <small>`core/…/mobs/PhantomPiranha.java:72-78`, `core/…/mobs/PhantomPiranha.java:94-123`</small>
- **Kill or nullify: Knockback onto land doesn't kill it.** dieOnLand is overridden to teleport it. (derived) <small>`core/…/mobs/PhantomPiranha.java:87-92`</small>
- **Escape.** Outrunnable: yes; contact breaks at: leave-water, out-of-sight. Water-bound like any piranha; it only reaches you by teleporting after you damage it. <small>`core/…/mobs/Piranha.java:132-157`, `core/…/mobs/PhantomPiranha.java:61-71`</small>

| Class | Note |
|---|---|
| Warrior | Shore melee. |
| Mage | Zaps from a distance are halved. Zap from the shore, then melee with the staff. |
| Rogue | Shore melee with the dagger. |
| Huntress | A bow shot from the shore brings it next to you; finish it with the bow (adjacent shots count as adjacent, so full damage) or with gloves. |
| Duelist | Shore melee. |
| Cleric | Shore melee. |

### Community notes

- **Tier F.** “<del>The Flooded Vault special room is guarded by 3 Giant Piranhas. Piranhas die instantly if kept out of water for a single turn, so luring them onto dry land (or draining the pool) kills them outright; every floor containing a flooded vault also generates a Potion of Invisibility elsewhere on that floor, letting you sneak past the piranhas entirely to reach the vault's chest instead of fighting them in the water where they have the advantage.</del>” [source](https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Special_Rooms) (version: unspecified (current-era source)). Any flooded-vault piranha can be a Phantom Piranha (1/50); it does not die on land but teleports to water. <small>`core/…/mobs/PhantomPiranha.java:87-92`, `core/…/mobs/Piranha.java:206-213`</small>

??? note "Corrected during verification (1)"

    - 'does not die on land' also fails when no free water cell exists anywhere (PhantomPiranha.java:94-121).

## Golden bee {#bee}

`actors.mobs.Bee` · depths any (shattered honeypot; secret honeypot room) · **Tactic:** Treat a honeypot as a weapon: throw it into enemies you'd rather not melee.

**Stats** (from the [Codex](../codex/mobs.md)): HT 0 · accuracy `defenseSkill` · evasion 0 · damage `Random.NormalIntRange( HT / 10, HT / 4 )` · armour 0 (no override: only Barkskin adds to it) · EXP 0 · max level 29 · properties none. Some numbers are set when it spawns; the evasion formula below is the live one. <small>`core/…/mobs/Bee.java:37`, `core/…/mobs/Bee.java:113`, `core/…/mobs/Bee.java:118`, `core/…/actors/Char.java:701`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** baseSpeed 1; leashed to its pot (returns toward it when its target is more than 3 from the pot or while wandering). Speed `normal`; flies: yes; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/mobs/Bee.java:46`, `core/…/mobs/Bee.java:212-226`, `core/…/actors/Char.java:175`</small>
- **Attack.** `melee`, reach 1. Adjacent. Attack time: 1. <small>`core/…/mobs/Mob.java:558-568`, `core/…/mobs/Bee.java:112-120`</small>
- **pot guard.** Targets whoever holds its shattered pot (hero or thief/crystal mimic that stole a honeypot). If the pot is on the ground, it targets the closest non-neutral mob within 3 tiles of the pot, and the hero only if no such mob exists and the hero is within 3 of the pot. There is no field-of-view condition on that closest-mob pick. Its hits make the victim mob aggro onto the bee. viewDistance 4. *Telegraph:* none; description says keep your distance <small>`core/…/mobs/Bee.java:42`, `core/…/mobs/Bee.java:122-129`, `core/…/mobs/Bee.java:144-210`, `core/…/mobs/Thief.java:146-168`, `core/…/mobs/CrystalMimic.java:164-171`</small>
- **spawned by a honeypot.** Throwing a honeypot (not into a chasm) or using its Shatter action spawns a full-HP bee at depth scaling. Shattering it in hand makes the hero the pot holder (the bee targets the hero) unless the shattered pot can't be picked up. *Telegraph:* shatter sound and yellow splash <small>`core/…/items/Honeypot.java:62-136`, `core/…/items/Honeypot.java:161-193`</small>
- **AI.** Starts `wandering`; flees: never. WANDERING around its pot; an Elixir of Honeyed Healing thrown on it makes it an ally (no longer tied to the pot). <small>`core/…/mobs/Bee.java:47`, `core/…/elixirs/ElixirOfHoneyedHealing.java:63-66`</small>
- **Evasion.** defenseSkill 0 (live formula `9+scalingDepth (9+depth in a secret honeypot room)`); evasive: no. Set by spawn(): 9+L (19 at depth 10); accuracy equals it. <small>`core/…/mobs/Bee.java:89-94`, `core/…/mobs/Bee.java:112-115`</small>
- **Surprise.** Can be surprised: yes. While wandering and not yet engaged, standard rules apply. <small>`core/…/mobs/Mob.java:869-877`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `avoid`; ranged attacks first: no.
- **Plan.** Treat a honeypot as a weapon: throw it into enemies you'd rather not melee. In a secret honeypot room, grab the loot and leave the pot's 3-tile radius quickly, or fight the bee at the edge if it engages. Never carry a shattered pot. <small>`core/…/mobs/Bee.java:174-195`</small>
- **Kill or nullify: Use the honeypot as a grenade.** Throw it into a group of enemies: the bee attacks the nearest mob within 3 of the pot, and the victims fight the bee. (derived) <small>`core/…/items/Honeypot.java:88-95`, `core/…/mobs/Bee.java:174-195`, `core/…/mobs/Bee.java:122-129`</small>
- **Kill or nullify: Never Shatter it in hand, and don't pick up the shattered pot.** Holding the pot makes you the bee's target wherever you go. (derived) <small>`core/…/items/Honeypot.java:74-79`, `core/…/items/Honeypot.java:161-169`, `core/…/mobs/Bee.java:150-152`</small>
- **Kill or nullify: Walk more than 3 tiles from the pot.** It drops any target more than 3 from the pot and returns to it. (derived) <small>`core/…/mobs/Bee.java:175-178`, `core/…/mobs/Bee.java:212-224`</small>
- **Kill or nullify: Tame it.** Throwing Elixir of Honeyed Healing on it turns it into an ally. (derived) <small>`core/…/elixirs/ElixirOfHoneyedHealing.java:59-66`</small>
- **Escape.** Outrunnable: no; contact breaks at: distance-from-pot&gt;3, door, stairs. Same speed, but leashed: leave the pot's 3-tile radius and it stops chasing, unless you carry the pot. <small>`core/…/mobs/Bee.java:144-226`</small>

| Class | Note |
|---|---|
| Warrior | Throw honeypots into packs. |
| Mage | Honeypot as a distraction. |
| Rogue | Honeypot as a distraction while you slip past. |
| Huntress | Throw honeypots into packs and shoot what the bee leaves. |
| Duelist | Throw honeypots into packs. |
| Cleric | Throw honeypots into packs. |

### Community notes

- **Tier 1.** “Shattering/throwing a Honeypot spawns a Golden Bee that attacks the first thing in its field of view — including you, if no other enemy is nearby — so only pop a honeypot when an enemy (ideally a boss) is already in the bee's sight, not preemptively.” [source](https://pixeldungeon.fandom.com/wiki/Honeypot) (version: unspecified (current-era source)). Gist confirmed: with no other non-neutral mob within 3 tiles of the pot, it targets the hero if the hero is within 3 of the pot. Correction: the choice is by distance to the pot (3 tiles), not 'first thing in its field of view'. <small>`core/…/mobs/Bee.java:174-202`</small>
- **Tier 1.** “If a Golden Bee turns hostile toward you by mistake, throwing a Potion of Honeyed Healing at it placates it into a friendly, following ally instead of an enemy.” [source](https://pixeldungeon.fandom.com/wiki/Honeypot) (version: unspecified (current-era source)). Confirmed, but the item is the Elixir of Honeyed Healing, not a potion; the bee becomes an ALLY and forgets its pot. <small>`core/…/elixirs/ElixirOfHoneyedHealing.java:59-66`</small>
- **Tier F.** “<del>Crazy Bandits can steal and shatter a Honeypot from your inventory, which releases a hostile Golden Bee, so don't assume a Bandit encounter is contained to just the Bandit if you're carrying honeypots.</del>” [source](https://steamcommunity.com/sharedfiles/filedetails/?id=2786737188) (version: unspecified (current-era source)). Bandits (Thief subclass) do shatter a stolen honeypot, but they shatter it with themselves as the pot holder, so the bee targets the bandit, not the hero. It only threatens the hero later, around the dropped pot, if the bandit dies. Claim filed under Crazy Bandit; graded here for the bee behaviour. <small>`core/…/mobs/Thief.java:146-168`, `core/…/mobs/Bee.java:150-152`</small>

??? note "Corrected during verification (2)"

    - Resolved the crystal-mimic open question: CrystalMimic.java:164-166 shatters a stolen honeypot with the mimic as holder; Thief.java:159-160 does the same for thieves and bandits, so the bee targets the thief, not the hero.
    - evasion 0 is the pre-spawn codex value; added formula (Bee.java:89-94).

## Rot heart {#rotheart}

`actors.mobs.RotHeart` · depths quest (Wandmaker rot garden, 7-9) · **Tactic:** Follow the lasher-safe path the generator guarantees to reach the heart (it is 7+ steps from the door).

**Stats** (from the [Codex](../codex/mobs.md)): HT 80 · accuracy 0 · evasion 0 · damage 0 · armour 0-5 · EXP 4 · max level 29 · properties IMMOVABLE, MINIBOSS, STATIC. <small>`core/…/mobs/RotHeart.java:37`, `core/…/mobs/RotHeart.java:127`, `core/…/mobs/RotHeart.java:122`, `core/…/mobs/RotHeart.java:132`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** Immobile: IMMOVABLE, getCloser always false. Speed `immobile`; flies: no; amphibious: no; water-bound: no; opens doors: no; follows you on the stairs: no. <small>`core/…/mobs/RotHeart.java:49-51`, `core/…/mobs/RotHeart.java:91-94`</small>
- **Attack.** `none`, reach 0. Never attacks (accuracy 0, damage 0, PASSIVE). Attack time: n/a. <small>`core/…/mobs/RotHeart.java:47`, `core/…/mobs/RotHeart.java:121-129`</small>
- **toxic retaliation.** Every physical hit on it (defenseProc) seeds ToxicGas on its cell, volume 5 + 3 x open neighbours. *Telegraph:* none before; gas cloud appears on hit <small>`core/…/mobs/RotHeart.java:71-84`</small>
- **linked lashers.** When it is destroyed, every Rot Lasher on the floor dies. *Telegraph:* none <small>`core/…/mobs/RotHeart.java:96-106`</small>
- **quest drop.** Dying drops the Rotberry Seed that the Wandmaker quest wants; burning destroys it without dying, so no seed drops. *Telegraph:* none <small>`core/…/mobs/RotHeart.java:60-69`, `core/…/mobs/RotHeart.java:108-114`, `core/…/npcs/Wandmaker.java:122-123`</small>
- **Inflicts ToxicGas (area).** Gas spawned on hit; the heart and lashers are immune, the hero is not. <small>`core/…/mobs/RotHeart.java:81`, `core/…/mobs/RotHeart.java:136-138`</small>
- **Immune.** ToxicGas; AllyBuff, Dread (MINIBOSS); Vertigo (IMMOVABLE); AllyBuff, Dread, Terror, Amok, Charm, Sleep, Paralysis, Frost, Chill, Slow, Speed (STATIC) <small>`core/…/mobs/RotHeart.java:136-138`, `core/…/actors/Char.java:1416-1441`</small>
- **AI.** Starts `passive`; flees: never. Always PASSIVE; ignores beckon. <small>`core/…/mobs/RotHeart.java:47`, `core/…/mobs/RotHeart.java:86-89`</small>
- **Evasion.** defenseSkill 0; evasive: no. Always hit. <small>`core/…/mobs/RotHeart.java:43`</small>
- **Surprise.** Can be surprised: yes. Irrelevant: defense is already 0. <small>`core/…/mobs/RotHeart.java:43`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `keep-away`; ranged attacks first: yes.
- **Plan.** Follow the lasher-safe path the generator guarantees to reach the heart (it is 7+ steps from the door). Kill it with wand zaps (no gas) or thrown weapons from range; if you must melee it, drink Purity or step out of the gas between hits. No fire. Pick up the Rotberry Seed. <small>`core/…/mobs/RotHeart.java:71-84`, `core/…/quest/RotGardenRoom.java:79-127`, `core/…/hero/HeroClass.java:248-260`</small>
- **Kill or nullify: Use wands, not weapons.** Wand damage skips defenseProc, so no gas is released. (derived) <small>`core/…/wands/WandOfMagicMissile.java:62`, `core/…/actors/Char.java:483`, `core/…/mobs/RotHeart.java:71-84`</small>
- **Kill or nullify: Or throw weapons from several tiles away.** Gas starts on the heart's own cell; distance buys time before it reaches you. (derived) <small>`core/…/mobs/RotHeart.java:81`</small>
- **Kill or nullify: If you melee it, drink Purity first.** Otherwise every hit bathes you in toxic gas while you chew through 80 HP. (derived) <small>`core/…/mobs/RotHeart.java:42`, `core/…/mobs/RotHeart.java:71-84`, `core/…/buffs/BlobImmunity.java:76`</small>
- **Kill or nullify: Never use fire here.** Burning calls destroy() instead of die(), so the Rotberry Seed never drops (die() is where the seed is dropped); the garden is high grass, so fire spreads. (derived) <small>`core/…/mobs/RotHeart.java:60-69`, `core/…/quest/RotGardenRoom.java:63`</small>
- **Kill or nullify: Kill the heart, not the lashers.** The heart's death kills every lasher. (derived) <small>`core/…/mobs/RotHeart.java:96-106`</small>
- **Escape.** Outrunnable: yes; contact breaks at: door, any. It never moves or attacks; the only danger is the gas. <small>`core/…/mobs/RotHeart.java:91-94`, `core/…/mobs/RotHeart.java:121-129`</small>

| Class | Note |
|---|---|
| Warrior | Melee plus stepping back out of the gas; 80 HP at DR 0-5 is slow. Throwing stones from range help. |
| Mage | Zap it: no gas at all. Don't use the Liquid Flame. |
| Rogue | Throwing knives from range, or melee with a retreat. |
| Huntress | Shoot from range; no gas reaches you. |
| Duelist | Throwing spikes plus melee with retreats. |
| Cleric | Starts with Purity identified: drink it and melee freely. |

**Open questions.**

- Toxic gas damage per turn was not read here (see buffs/ToxicGas).

### Community notes

No community claim about this enemy was found.

??? note "Corrected during verification (1)"

    - movement.speed 'slow' -&gt; 'immobile' (getCloser always false, IMMOVABLE).

## Rot lasher {#rotlasher}

`actors.mobs.RotLasher` · depths quest (Wandmaker rot garden, 7-9) · **Tactic:** Route around lashers. Step only on cells not adjacent to them, and if one tile must be adjacent, move through without stopping.

**Stats** (from the [Codex](../codex/mobs.md)): HT 80 · accuracy 25 · evasion 0 · damage 10-20 · armour 0-8 · EXP 1 · max level 29 · properties IMMOVABLE, MINIBOSS · loot SEED (75% base). <small>`core/…/mobs/RotLasher.java:37`, `core/…/mobs/RotLasher.java:112`, `core/…/mobs/RotLasher.java:107`, `core/…/mobs/RotLasher.java:117`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** Immobile: IMMOVABLE, getCloser/getFurther always false. Speed `immobile`; flies: no; amphibious: no; water-bound: no; opens doors: no; follows you on the stairs: no. <small>`core/…/mobs/RotLasher.java:53-54`, `core/…/mobs/RotLasher.java:96-104`</small>
- **Attack.** `melee`, reach 1. Adjacent only; viewDistance 1, so it only perceives adjacent cells. Attack time: 1. <small>`core/…/mobs/RotLasher.java:50-51`, `core/…/mobs/Mob.java:558-568`</small>
- **hidden sentinel.** Waits in a WANDERING-type state; noticing an adjacent enemy costs it an extra turn, then it attacks. *Telegraph:* sits on a short-grass tile inside high grass; notice shows the alert icon <small>`core/…/mobs/RotLasher.java:50`, `core/…/mobs/RotLasher.java:125-132`, `core/…/quest/RotGardenRoom.java:176-181`</small>
- **regeneration.** Heals 5 HP per act while its enemy is not adjacent. *Telegraph:* green healing number <small>`core/…/mobs/RotLasher.java:58-64`</small>
- **Inflicts Cripple.** 2 turns on each hit (halves speed). <small>`core/…/mobs/RotLasher.java:84-89`, `core/…/actors/Char.java:772`</small>
- **Immune.** ToxicGas; AllyBuff, Dread (MINIBOSS); Vertigo (IMMOVABLE) <small>`core/…/mobs/RotLasher.java:121-123`, `core/…/actors/Char.java:1416-1434`</small>
- **AI.** Starts `wandering`; flees: never. Waiting state (Wandering subclass). Burning destroys it outright. <small>`core/…/mobs/RotLasher.java:50`, `core/…/mobs/RotLasher.java:66-74`</small>
- **Evasion.** defenseSkill 0; evasive: no. Always hit. <small>`core/…/mobs/RotLasher.java:43`</small>
- **Surprise.** Can be surprised: yes. It can't see beyond adjacency, but defense is 0 anyway. <small>`core/…/mobs/RotLasher.java:43`, `core/…/mobs/RotLasher.java:51`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `avoid`; ranged attacks first: no.
- **Plan.** Route around lashers. Step only on cells not adjacent to them, and if one tile must be adjacent, move through without stopping. Go straight for the heart; lashers die with it. If crippled, step back out of reach before resting. <small>`core/…/mobs/RotHeart.java:96-106`</small>
- **Kill or nullify: Don't fight lashers; kill the heart.** The heart's death kills all lashers; lashers hit for 10-20, cripple, and regenerate. (derived) <small>`core/…/mobs/RotHeart.java:96-106`, `core/…/mobs/RotLasher.java:58-64`, `core/…/mobs/RotLasher.java:106-109`</small>
- **Kill or nullify: Follow the generated safe path.** The generator keeps an entrance-to-heart path that avoids every lasher's cardinal neighbours (all 8 neighbours for lashers within 2 of the heart). Diagonal adjacency to far lashers is not excluded, but a diagonal step is adjacent for only one move. The garden is high grass, which blocks sight, so the lashers are not all visible in advance. (derived) <small>`core/…/quest/RotGardenRoom.java:113-127`, `core/…/quest/RotGardenRoom.java:139-174`</small>
- **Kill or nullify: If you pass one, don't linger.** Its notice costs it a turn, so one turn of adjacency usually passes without a hit. (derived) <small>`core/…/mobs/RotLasher.java:125-132`</small>
- **Kill or nullify: Ranged chip damage is mostly wasted.** It regenerates 5 HP each act while you are not adjacent, and DR 0-8 cuts every physical hit; only burst damage beats that. (derived) <small>`core/…/mobs/RotLasher.java:58-64`, `core/…/mobs/RotLasher.java:116-119`</small>
- **Kill or nullify: Don't burn it (quest).** Fire would also destroy the heart without its seed. (derived) <small>`core/…/mobs/RotLasher.java:66-74`, `core/…/mobs/RotHeart.java:60-69`</small>
- **Escape.** Outrunnable: yes; contact breaks at: step-away. It never moves: stepping out of adjacency ends the threat. <small>`core/…/mobs/RotLasher.java:96-104`</small>

| Class | Note |
|---|---|
| Warrior | Avoid them. |
| Mage | Avoid them. |
| Rogue | Avoid them. |
| Huntress | Avoid them; shooting one is wasted because of its regeneration. |
| Duelist | Avoid them. |
| Cleric | Avoid them. |

**Open questions.**

- The exact actor order when the hero steps adjacent and then away was not simulated; the one-free-turn claim is DERIVED from noticeEnemy spending a tick and should be tested.

### Community notes

No community claim about this enemy was found.

??? note "Corrected during verification (2)"

    - movement.speed 'slow' -&gt; 'immobile'.
    - 'Chip damage is futile' overstated; rewritten as mostly wasted, with DR 0-8 (RotLasher.java:116-119) added to the basis.

## Ratmogrified %s {#transmograt}

`actors.hero.abilities.Ratmogrify.TransmogRat` · depths special (hero's Ratmogrify armor ability; helper, not a spawn) · **Tactic:** Only relevant if the hero has Ratmogrify (armor ability from giving the King's Crown to the Rat King).

**Stats** (from the [Codex](../codex/mobs.md)): HT 0 · accuracy `original.attackSkill(target)` · evasion 0 · damage `damage` · armour `original.drRoll()` · EXP 1 · max level 29 · properties none. Some numbers are set when it spawns; the evasion formula below is the live one. <small>`core/…/abilities/Ratmogrify.java:191`, `core/…/abilities/Ratmogrify.java:270`, `core/…/abilities/Ratmogrify.java:279`, `core/…/abilities/Ratmogrify.java:274`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** Plain Mob, baseSpeed 1: the original's speed and flying are not copied. Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/abilities/Ratmogrify.java:191-223`, `core/…/actors/Char.java:175`</small>
- **Attack.** `melee`, reach 1. Adjacent only (the original's ranged or reach attacks are lost). Attack time: the original's attackDelay(). <small>`core/…/mobs/Mob.java:558-568`, `core/…/abilities/Ratmogrify.java:270-290`</small>
- **temporary rat.** Replaces a non-boss, non-miniboss enemy Mob (not a Rat) for 6 units of its own time, then reverts to the original with the rat's current HP. Killing the rat grants the original's EXP and rolls the original's loot, but the original's own die() never runs, so its on-death effects do not fire. Ratsistance multiplies its damage by 0.9 per point; Ratlomacy can make it a permanent ally. *Telegraph:* wool puff; name 'ratmogrified &lt;name&gt;' <small>`core/…/abilities/Ratmogrify.java:121-172`, `core/…/abilities/Ratmogrify.java:203-268`, `core/…/abilities/Ratmogrify.java:278-296`, `core/…/abilities/Ratmogrify.java:292-296`</small>
- **Immune.** AllyBuff (corruption/charm-to-ally), except via Ratlomacy <small>`core/…/abilities/Ratmogrify.java:315-317`</small>
- **AI.** Starts `hunting`; flees: never. Copies the original's state: sleeping stays sleeping, hunting stays hunting, anything else wanders. <small>`core/…/abilities/Ratmogrify.java:215-221`</small>
- **Evasion.** defenseSkill 0 (live formula `copied from the original mob`); evasive: no. The original's defenseSkill field (codex shows 0 because it is copied at runtime). <small>`core/…/abilities/Ratmogrify.java:210`</small>
- **Surprise.** Can be surprised: yes. Standard Mob rules; a sleeping original gives a sleeping rat. <small>`core/…/abilities/Ratmogrify.java:215-216`, `core/…/mobs/Mob.java:869-877`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `close`; ranged attacks first: no.
- **Plan.** Only relevant if the hero has Ratmogrify (armor ability from giving the King's Crown to the Rat King). Rat the most dangerous non-boss enemy, then kill it in melee within 6 turns. <small>`core/…/npcs/RatKing.java:148`</small>
- **Kill or nullify: Kill it within 6 turns.** It reverts with its remaining HP; a rat has no special abilities, so this is the safe window. (derived) <small>`core/…/abilities/Ratmogrify.java:233-257`</small>
- **Kill or nullify: Target enemies whose danger is their abilities.** Casters, reach and ranged enemies and special movers become adjacent-only normal-speed rats. (derived) <small>`core/…/abilities/Ratmogrify.java:191-223`, `core/…/mobs/Mob.java:558-568`</small>
- **Kill or nullify: Minibosses and bosses can't be transformed.** Refused with 'too strong'. (derived) <small>`core/…/abilities/Ratmogrify.java:136-138`</small>
- **Escape.** Outrunnable: no; contact breaks at: door, out-of-sight, stairs. Normal-speed rat, so an escape window against fast originals. <small>`core/…/actors/Char.java:175`</small>

| Class | Note |
|---|---|
| Warrior | Same for every class once the ability is chosen. |
| Mage | Same for every class once the ability is chosen. |
| Rogue | Same for every class once the ability is chosen. |
| Huntress | Same for every class once the ability is chosen. |
| Duelist | Same for every class once the ability is chosen. |
| Cleric | Same for every class once the ability is chosen. |

**Open questions.**

- The original's resistances, immunities and properties are not copied (TransmogRat declares only AllyBuff immunity); consequences per enemy not enumerated.

### Community notes

No community claim about this enemy was found.

??? note "Corrected during verification (1)"

    - Clarified that the original's die() never runs (only rollToDropLoot is delegated, Ratmogrify.java:292-296), so 'killing the rat kills the original' does not include its death effects.

## Mimic {#mimic}

`actors.mobs.Mimic` · depths any, depth 2+ (1 in 20 random floor-item drops becomes a mimic; treasury rooms; suspicious-chest room 1/3; Distortion trap); Mimic Tooth raises the rates · **Tactic:** Examine chests; any with the hint, or any chest in a treasury or suspicious-chest room, gets a thrown or zapped test hit from range first.

**Stats** (from the [Codex](../codex/mobs.md)): HT 0 · accuracy `INFINITE_ACCURACY; \| 6 + level` · evasion 0 · damage `Random.NormalIntRange( 2 + 2\*level, 2 + 2\*level); \| Random.NormalIntRange( 1 + level, 2 + 2\*level)` · armour `Random.NormalIntRange(0, 1 + level/2)` · EXP 0 · max level 29 · properties DEMONIC. Some numbers are set when it spawns; the evasion formula below is the live one. <small>`core/…/mobs/Mimic.java:51`, `core/…/mobs/Mimic.java:251`, `core/…/mobs/Mimic.java:230`, `core/…/mobs/Mimic.java:239`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** baseSpeed 1; does not move while hidden (PASSIVE). Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/actors/Char.java:175`, `core/…/mobs/Mimic.java:63-64`</small>
- **Attack.** `melee`, reach 1. Adjacent. Attack time: 1. <small>`core/…/mobs/Mob.java:558-568`, `core/…/mobs/Mob.java:753-757`</small>
- **hidden chest.** NEUTRAL and PASSIVE while hidden. Tapping it counts as interacting (not attacking), and interacting makes it bite with infinite accuracy for fixed max damage 2+2L (unless the hero is invisible or time is frozen). Any damage, any negative buff, being hit, or turning hostile reveals it (HUNTING). *Telegraph:* Examining it shows the chest description plus 'Something about this chest feels off...' unless the hero has a Mimic Tooth; the non-stealthy hidden sprite also cycles a tell frame (frames 1,1,1,1,1,2 at 1 fps). Reveal logs 'That chest is a mimic!' <small>`core/…/mobs/Mimic.java:62-64`, `core/…/mobs/Mimic.java:98-131`, `core/…/mobs/Mimic.java:133-145`, `core/…/mobs/Mimic.java:154-222`, `core/…/mobs/Mimic.java:229-257`, `core/…/hero/Hero.java:1947-1953`, `core/…/sprites/MimicSprite.java:55-59`, `core/…/sprites/MimicSprite.java:84-91`, `core/…/actors/actors.properties:1722-1723`</small>
- **AI.** Starts `passive`; flees: never. PASSIVE/NEUTRAL until revealed, then HUNTING the hero. <small>`core/…/mobs/Mimic.java:63-64`, `core/…/mobs/Mimic.java:212-222`</small>
- **Evasion.** defenseSkill 0 (live formula `2+L/2, L=scalingDepth`); evasive: no. 2+L/2 once adjustStats runs (codex shows 0). <small>`core/…/mobs/Mimic.java:264-269`</small>
- **Surprise.** Can be surprised: yes. While hidden it is NEUTRAL, chooses no enemy, and its Passive act sets enemySeen false, so the first hero hit is a surprise hit (defense 0). adjustStats sets enemySeen true at spawn, but the first Passive act clears it. <small>`core/…/mobs/Mob.java:477-480`, `core/…/mobs/Mob.java:1494-1497`, `core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mimic.java:264-269`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `keep-away`; ranged attacks first: yes.
- **Plan.** Examine chests; any with the hint, or any chest in a treasury or suspicious-chest room, gets a thrown or zapped test hit from range first. Kill the revealed mimic in melee and loot its hoard. Never open a suspected mimic by hand unless invisible. <small>`core/…/mobs/Mimic.java:154-172`, `core/…/levels/RegularLevel.java:398-419`, `core/…/special/TreasuryRoom.java:59`, `core/…/standard/SuspiciousChestRoom.java:67`</small>
- **Kill or nullify: Examine every chest before opening.** Without a Mimic Tooth, a mimic's info text carries the 'feels off' hint. (derived) <small>`core/…/mobs/Mimic.java:120-131`, `core/…/trinkets/MimicTooth.java:64-66`</small>
- **Kill or nullify: Open with a ranged surprise hit, never by opening it.** A hidden mimic chooses no enemy, so its enemySeen is false and your first thrown or zapped hit lands at 0 defense. Opening it instead takes a guaranteed max-damage bite. (derived) <small>`core/…/mobs/Mob.java:477-480`, `core/…/mobs/Mob.java:1494-1497`, `core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mimic.java:251-257`, `core/…/mobs/Mimic.java:229-236`</small>
- **Kill or nullify: Or open it while invisible.** An invisible hero's interaction skips the bite and just reveals it. (derived) <small>`core/…/mobs/Mimic.java:163-172`</small>
- **Kill or nullify: Fight it like a weak brute.** Revealed: HP (1+L)\*6, def 2+L/2, acc 6+L, dmg 1+L..2+2L; normal speed, no gas/poison immunity. (derived) <small>`core/…/mobs/Mimic.java:229-269`</small>
- **Kill or nullify: Kill it for its hoard.** It drops the chest's items plus an extra prize. (derived) <small>`core/…/mobs/Mimic.java:271-281`, `core/…/mobs/Mimic.java:332-359`</small>
- **Kill or nullify: Holy and light effects.** DEMONIC takes bonus damage from Prismatic Light, Holy Dart and similar. (derived) <small>`core/…/mobs/Mimic.java:58`, `core/…/wands/WandOfPrismaticLight.java:96-100`</small>
- **Escape.** Outrunnable: no; contact breaks at: door, out-of-sight, stairs. Same speed as the hero; an unrevealed mimic never follows. <small>`core/…/actors/Char.java:175`, `core/…/mobs/Mimic.java:63-64`</small>

| Class | Note |
|---|---|
| Warrior | Throw a stone at a suspect chest, then melee the revealed mimic. |
| Mage | Zap a suspect chest from range, then zap and melee. |
| Rogue | Throwing knife at the suspect chest (surprise damage bonus), or open it from the cloak's invisibility to skip the bite. |
| Huntress | Arrow any suspect chest from range; the first shot is a surprise hit. |
| Duelist | Throwing spike at the suspect chest, then melee. |
| Cleric | Test-hit suspect chests from range; holy damage bonus against the DEMONIC mimic. |

**Open questions.**

- Whether the Observer presents hidden mimics as chests (heaps) or as mobs; a human sees a chest sprite that can be examined as a character. This is a fairness question for Observer.
- Whether mobs path over or around visible traps (for the community 'lure it over traps' claim) was not traced.

### Community notes

- **Tier 1.** “Examining a suspicious chest will explicitly tell you if something is off — the safest ID method. If you'd rather not examine, hit the chest first with a ranged weapon, wand or thrown item from a distance; a Mimic will reveal itself and take damage, while a real chest is unaffected.” [source](https://steamcommunity.com/sharedfiles/filedetails/?id=3225596609) (version: unspecified (current-era source)). Examine shows the 'feels off' hint for every chest-shaped mimic unless you carry a Mimic Tooth (then no hint). A ranged or wand hit reveals it (damage calls stopHiding); on a real chest a thrown item just lands. <small>`core/…/mobs/Mimic.java:120-131`, `core/…/trinkets/MimicTooth.java:64-66`, `core/…/mobs/Mimic.java:194-201`</small>
- **Tier 1.** “A Mimic's opening/first hit reportedly deals unusually high damage, so keep distance and fight it with ranged weapons once identified rather than walking up to melee it blind; luring it over your own traps also works. Mimics (like Animated Statues) are optional encounters — if unprepared, you can just leave the chest and move on.” [source](https://steamcommunity.com/sharedfiles/filedetails/?id=3225596609) (version: unspecified (current-era source)). Hidden bite: infinite accuracy and fixed maximum damage 2+2L (crystal mimics steal an item instead of dealing bonus damage). Mimics stay PASSIVE and never move until disturbed, so they are optional. The 'lure it over traps' part was not checked. <small>`core/…/mobs/Mimic.java:229-236`, `core/…/mobs/Mimic.java:251-257`, `core/…/mobs/Mimic.java:63-64`</small>

??? note "Corrected during verification (2)"

    - Depth/frequency text fixed: the 1/20 roll is over random floor drops, not over chests (RegularLevel.java:398-421); suspicious-chest room is 1/3 (SuspiciousChestRoom.java:65-67).
    - defenseSkill 0 is the codex value before adjustStats; formula added. Noted enemySeen=true at spawn is cleared by the first Passive act.

## Golden mimic {#goldenmimic}

`actors.mobs.GoldenMimic` · depths any, depth 2+ (in place of a locked golden chest holding an upgradable item or artifact; CursedWand) · **Tactic:** As Mimic, but expect roughly one depth-tier-and-a-third stronger stats.

**Stats** (from the [Codex](../codex/mobs.md)): HT 0 · accuracy `INFINITE_ACCURACY; \| 6 + level` · evasion 0 · damage `Random.NormalIntRange( 2 + 2\*level, 2 + 2\*level); \| Random.NormalIntRange( 1 + level, 2 + 2\*level)` · armour `Random.NormalIntRange(0, 1 + level/2)` · EXP 0 · max level 29 · properties DEMONIC. Some numbers are set when it spawns; the evasion formula below is the live one. <small>`core/…/mobs/GoldenMimic.java:44`, `core/…/mobs/Mimic.java:251`, `core/…/mobs/Mimic.java:230`, `core/…/mobs/Mimic.java:239`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** baseSpeed 1; does not move while hidden (PASSIVE). Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/actors/Char.java:175`, `core/…/mobs/Mimic.java:63-64`</small>
- **Attack.** `melee`, reach 1. Adjacent. Attack time: 1. <small>`core/…/mobs/Mob.java:558-568`, `core/…/mobs/Mob.java:753-757`</small>
- **hidden locked chest.** NEUTRAL and PASSIVE while hidden. Tapping it counts as interacting (not attacking), and interacting makes it bite with infinite accuracy for fixed max damage 2+2L (unless the hero is invisible or time is frozen). Any damage, any negative buff, being hit, or turning hostile reveals it (HUNTING). Disguised as a locked (golden) chest; its level is 1.33x scalingDepth, so everything is stronger. *Telegraph:* Examining it shows the chest description plus 'Something about this chest feels off...' unless the hero has a Mimic Tooth; the non-stealthy hidden sprite also cycles a tell frame (frames 1,1,1,1,1,2 at 1 fps). Reveal logs 'That chest is a mimic!' <small>`core/…/mobs/Mimic.java:62-64`, `core/…/mobs/Mimic.java:98-131`, `core/…/mobs/Mimic.java:133-145`, `core/…/mobs/Mimic.java:154-222`, `core/…/mobs/Mimic.java:229-257`, `core/…/hero/Hero.java:1947-1953`, `core/…/sprites/MimicSprite.java:55-59`, `core/…/sprites/MimicSprite.java:84-91`, `core/…/actors/actors.properties:1722-1723`</small>
- **better loot.** All equipment prizes uncursed and known-uncursed, curse enchants/glyphs removed, and +0 items have a 50% chance to become +1. *Telegraph:* none <small>`core/…/mobs/GoldenMimic.java:89-108`</small>
- **AI.** Starts `passive`; flees: never. As Mimic. <small>`core/…/mobs/GoldenMimic.java:72-82`</small>
- **Evasion.** defenseSkill 0 (live formula `2+L/2, L=round(1.33*scalingDepth)`); evasive: no. 2+L/2 with L = round(1.33\*scalingDepth). <small>`core/…/mobs/GoldenMimic.java:84-87`, `core/…/mobs/Mimic.java:264-269`</small>
- **Surprise.** Can be surprised: yes. As Mimic. <small>`core/…/mobs/Mob.java:873-877`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `keep-away`; ranged attacks first: yes.
- **Plan.** As Mimic, but expect roughly one depth-tier-and-a-third stronger stats. Test-hit every golden chest from range, rest before popping it, and fight it at a chokepoint. The loot is guaranteed uncursed. <small>`core/…/mobs/GoldenMimic.java:84-108`</small>
- **Kill or nullify: Suspect every locked golden chest.** Golden mimics replace a locked chest holding an artifact or upgradable item with chance 1/10 (x Mimic Tooth) at depth 2+; the examine hint still applies without a Mimic Tooth. (derived) <small>`core/…/levels/RegularLevel.java:427-432`, `core/…/mobs/GoldenMimic.java:59-70`</small>
- **Kill or nullify: Examine every chest before opening.** Without a Mimic Tooth, a mimic's info text carries the 'feels off' hint. (derived) <small>`core/…/mobs/Mimic.java:120-131`, `core/…/trinkets/MimicTooth.java:64-66`</small>
- **Kill or nullify: Open with a ranged surprise hit, never by opening it.** A hidden mimic chooses no enemy, so its enemySeen is false and your first thrown or zapped hit lands at 0 defense. Opening it instead takes a guaranteed max-damage bite. (derived) <small>`core/…/mobs/Mob.java:477-480`, `core/…/mobs/Mob.java:1494-1497`, `core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mimic.java:251-257`, `core/…/mobs/Mimic.java:229-236`</small>
- **Kill or nullify: Or open it while invisible.** An invisible hero's interaction skips the bite and just reveals it. (derived) <small>`core/…/mobs/Mimic.java:163-172`</small>
- **Kill or nullify: Fight it like a weak brute.** Revealed: HP (1+L)\*6, def 2+L/2, acc 6+L, dmg 1+L..2+2L; normal speed, no gas/poison immunity. (derived) <small>`core/…/mobs/Mimic.java:229-269`</small>
- **Kill or nullify: Kill it for its hoard.** It drops the chest's items plus an extra prize. (derived) <small>`core/…/mobs/Mimic.java:271-281`, `core/…/mobs/Mimic.java:332-359`</small>
- **Kill or nullify: Holy and light effects.** DEMONIC takes bonus damage from Prismatic Light, Holy Dart and similar. (derived) <small>`core/…/mobs/Mimic.java:58`, `core/…/wands/WandOfPrismaticLight.java:96-100`</small>
- **Escape.** Outrunnable: no; contact breaks at: door, out-of-sight, stairs. As Mimic. <small>`core/…/actors/Char.java:175`</small>

| Class | Note |
|---|---|
| Warrior | Throw a stone at a suspect chest, then melee the revealed mimic. |
| Mage | Zap a suspect chest from range, then zap and melee. |
| Rogue | Throwing knife at the suspect chest (surprise damage bonus), or open it from the cloak's invisibility to skip the bite. |
| Huntress | Arrow any suspect chest from range; the first shot is a surprise hit. |
| Duelist | Throwing spike at the suspect chest, then melee. |
| Cleric | Test-hit suspect chests from range; holy damage bonus against the DEMONIC mimic. |

### Community notes

- **Tier 1.** “Examining a suspicious chest will explicitly tell you if something is off — the safest ID method. If you'd rather not examine, hit the chest first with a ranged weapon, wand or thrown item from a distance; a Mimic will reveal itself and take damage, while a real chest is unaffected.” [source](https://steamcommunity.com/sharedfiles/filedetails/?id=3225596609) (version: unspecified (current-era source)). Examine shows the 'feels off' hint for every chest-shaped mimic unless you carry a Mimic Tooth (then no hint). A ranged or wand hit reveals it (damage calls stopHiding); on a real chest a thrown item just lands. <small>`core/…/mobs/Mimic.java:120-131`, `core/…/trinkets/MimicTooth.java:64-66`, `core/…/mobs/Mimic.java:194-201`</small>
- **Tier 1.** “A Mimic's opening/first hit reportedly deals unusually high damage, so keep distance and fight it with ranged weapons once identified rather than walking up to melee it blind; luring it over your own traps also works. Mimics (like Animated Statues) are optional encounters — if unprepared, you can just leave the chest and move on.” [source](https://steamcommunity.com/sharedfiles/filedetails/?id=3225596609) (version: unspecified (current-era source)). Hidden bite: infinite accuracy and fixed maximum damage 2+2L (crystal mimics steal an item instead of dealing bonus damage). Mimics stay PASSIVE and never move until disturbed, so they are optional. The 'lure it over traps' part was not checked. <small>`core/…/mobs/Mimic.java:229-236`, `core/…/mobs/Mimic.java:251-257`, `core/…/mobs/Mimic.java:63-64`</small>

??? note "Corrected during verification (1)"

    - defenseSkill formula added; golden-chest replacement rate stated from RegularLevel.java:430-431.

## Crystal mimic {#crystalmimic}

`actors.mobs.CrystalMimic` · depths any (CrystalVaultRoom: the second crystal chest is a mimic with chance 1/10, Rat Skull half as effective, Mimic Tooth fully) · **Tactic:** In a crystal vault, examine both chests. Stand in the only doorway with a ranged option ready, reveal the mimic with a ranged surprise hit, and keep it in view while you finish it with ranged attacks, or melee it when...

**Stats** (from the [Codex](../codex/mobs.md)): HT 0 · accuracy `INFINITE_ACCURACY; \| 6 + level` · evasion 0 · damage `dmg; \| super.damageRoll()` · armour `Random.NormalIntRange(0, 1 + level/2)` · EXP 0 · max level 29 · properties DEMONIC. Some numbers are set when it spawns; the evasion formula below is the live one. <small>`core/…/mobs/CrystalMimic.java:50`, `core/…/mobs/Mimic.java:251`, `core/…/mobs/CrystalMimic.java:97`, `core/…/mobs/Mimic.java:239`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** baseSpeed 1, but on reveal gets Haste (x3 speed) for 2 turns if it revealed by biting, else 1 turn; when cornered while fleeing it acts again immediately. Speed `fast`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/mobs/CrystalMimic.java:108-124`, `core/…/actors/Char.java:775`, `core/…/mobs/CrystalMimic.java:197-203`</small>
- **Attack.** `melee`, reach 1. Adjacent. Attack time: 1. <small>`core/…/mobs/Mob.java:558-568`, `core/…/mobs/Mob.java:753-757`</small>
- **hidden crystal chest.** Disguised as a crystal chest, named with its item category (artifact/ring/wand). A hidden bite steals a random unequipped, non-unique +0 item instead of dealing bonus damage. *Telegraph:* Examining it shows the chest description plus 'Something about this chest feels off...' unless the hero has a Mimic Tooth; the non-stealthy hidden sprite also cycles a tell frame (frames 1,1,1,1,1,2 at 1 fps). Reveal logs 'That chest is a mimic!' <small>`core/…/mobs/Mimic.java:62-64`, `core/…/mobs/Mimic.java:98-131`, `core/…/mobs/Mimic.java:133-145`, `core/…/mobs/Mimic.java:154-222`, `core/…/mobs/Mimic.java:229-257`, `core/…/hero/Hero.java:1947-1953`, `core/…/sprites/MimicSprite.java:55-59`, `core/…/sprites/MimicSprite.java:84-91`, `core/…/actors/actors.properties:1722-1723`</small>
- **thief's escape.** Revealed, it FLEES with Haste (x3 speed): 2 turns if revealed by its own hidden bite, 1 turn if revealed by damage or a debuff. Each hit it lands while revealed moves its victim to a random free passable cell next to the mimic, then it flees again. When a flee roll triggers escaped() and it is out of the hero's view and 6+ tiles away, it is removed from the level with all its items; otherwise it drops to WANDERING. Cornered, it turns to fight and acts again at once (spend(-TICK)). *Telegraph:* 'The crystal mimic has escaped!' in the log when gone <small>`core/…/mobs/CrystalMimic.java:108-124`, `core/…/mobs/CrystalMimic.java:126-146`, `core/…/mobs/CrystalMimic.java:185-203`, `core/…/mobs/Mob.java:1442-1450`, `core/…/actors/Char.java:775`</small>
- **Inflicts displacement.** Its hits teleport the target to an adjacent free cell. <small>`core/…/mobs/CrystalMimic.java:131-141`</small>
- **AI.** Starts `passive`; flees: always once revealed. stopHiding sets FLEEING; it turns to fight only when cornered. <small>`core/…/mobs/CrystalMimic.java:108-110`, `core/…/mobs/CrystalMimic.java:197-203`, `core/…/mobs/Mob.java:1476-1486`</small>
- **Evasion.** defenseSkill 0 (live formula `2+L/2, L=scalingDepth`); evasive: no. 2+L/2 (low). <small>`core/…/mobs/Mimic.java:264-269`</small>
- **Surprise.** Can be surprised: yes. Hidden: first hit is a surprise hit, as for Mimic. Fleeing in view: no surprise. <small>`core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:1443`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `chokepoint`; ranged attacks first: yes.
- **Plan.** In a crystal vault, examine both chests. Stand in the only doorway with a ranged option ready, reveal the mimic with a ranged surprise hit, and keep it in view while you finish it with ranged attacks, or melee it when it's cornered. Its bite may bump you off the door; step back in. <small>`core/…/mobs/CrystalMimic.java:185-195`</small>
- **Kill or nullify: Examine both crystal chests.** The mimic's text carries the 'feels off' hint unless you have a Mimic Tooth. (derived) <small>`core/…/mobs/CrystalMimic.java:67-93`</small>
- **Kill or nullify: Block the vault's single door before revealing it.** The vault has one locked entrance and escape needs you to lose sight of it; standing in the doorway keeps it in view and corners it (a cornered fleer fights). (derived) <small>`core/…/special/CrystalVaultRoom.java:67-88`, `core/…/mobs/CrystalMimic.java:185-195`, `core/…/mobs/Mob.java:1476-1486`</small>
- **Kill or nullify: Keep it in sight and hit it at range.** It can't escape while in the hero's view; its evasion is low. (derived) <small>`core/…/mobs/CrystalMimic.java:187-194`, `core/…/mobs/Mimic.java:264-269`</small>
- **Kill or nullify: Root or paralyse it.** A rooted mob can't getFurther; a paralysed one skips turns with 0 defense. (derived) <small>`core/…/mobs/Mob.java:714-726`, `core/…/mobs/Mob.java:277-282`</small>
- **Kill or nullify: Don't open it by hand.** The hidden bite steals an item, and fleeing with it risks losing that item forever. (derived) <small>`core/…/mobs/CrystalMimic.java:126-174`, `core/…/mobs/CrystalMimic.java:187-191`</small>
- **Kill or nullify: Reveal it with a ranged hit, not by opening it.** Damage reveals it with alignment already ENEMY, so it gets only 1 turn of Haste; opening it (even invisibly) calls stopHiding while NEUTRAL, giving 2 turns of Haste, and a visible opener also loses an item to the steal bite. (derived) <small>`core/…/mobs/Mimic.java:194-201`, `core/…/mobs/Mimic.java:154-172`, `core/…/mobs/CrystalMimic.java:108-116`, `core/…/mobs/CrystalMimic.java:126-130`</small>
- **Escape.** Outrunnable: yes; contact breaks at: n/a: it runs from you. It is the one trying to escape. <small>`core/…/mobs/CrystalMimic.java:185-195`</small>

| Class | Note |
|---|---|
| Warrior | Hold the doorway with throwing stones and melee it when cornered. |
| Mage | Zaps from the doorway, no hit roll. |
| Rogue | Throwing knives from the doorway. Opening it from invisibility skips the steal but gives it 2 turns of Haste; a knife opener gives only 1. |
| Huntress | Best case: arrows from the doorway. |
| Duelist | Spikes from the doorway. |
| Cleric | Ranged spells from the doorway. |

**Open questions.**

- Whether its displacement hit can move the hero out of the doorway far enough for it to slip past in practice was not simulated.

### Community notes

- **Tier 1.** “Examining a suspicious chest will explicitly tell you if something is off — the safest ID method. If you'd rather not examine, hit the chest first with a ranged weapon, wand or thrown item from a distance; a Mimic will reveal itself and take damage, while a real chest is unaffected.” [source](https://steamcommunity.com/sharedfiles/filedetails/?id=3225596609) (version: unspecified (current-era source)). Examine shows the 'feels off' hint for every chest-shaped mimic unless you carry a Mimic Tooth (then no hint). A ranged or wand hit reveals it (damage calls stopHiding); on a real chest a thrown item just lands. <small>`core/…/mobs/Mimic.java:120-131`, `core/…/trinkets/MimicTooth.java:64-66`, `core/…/mobs/Mimic.java:194-201`</small>
- **Tier 1.** “A Mimic's opening/first hit reportedly deals unusually high damage, so keep distance and fight it with ranged weapons once identified rather than walking up to melee it blind; luring it over your own traps also works. Mimics (like Animated Statues) are optional encounters — if unprepared, you can just leave the chest and move on.” [source](https://steamcommunity.com/sharedfiles/filedetails/?id=3225596609) (version: unspecified (current-era source)). Hidden bite: infinite accuracy and fixed maximum damage 2+2L (crystal mimics steal an item instead of dealing bonus damage). Mimics stay PASSIVE and never move until disturbed, so they are optional. The 'lure it over traps' part was not checked. <small>`core/…/mobs/Mimic.java:229-236`, `core/…/mobs/Mimic.java:251-257`, `core/…/mobs/Mimic.java:63-64`</small>

??? note "Corrected during verification (3)"

    - Thief's-escape text: the victim is moved next to the MIMIC (CrystalMimic.java:132-141), escape also requires the Fleeing escape roll, and when escape fails it goes WANDERING; Haste on reveal and the cornered extra action added to the ability.
    - Rogue class note ('don't let it reach you while the cloak is up') had no code basis; replaced with the Haste difference between opening and ranged reveal.
    - defenseSkill formula added.

## Ebony mimic {#ebonymimic}

`actors.mobs.EbonyMimic` · depths any, only with the Mimic Tooth trinket (12.5% + 12.5%/level per floor) · **Tactic:** Only exists if you carry a Mimic Tooth. Treat every faint outline on a door, the exit or an item pile as a mimic: hit it from range, then melee it down for the +1 loot.

**Stats** (from the [Codex](../codex/mobs.md)): HT 0 · accuracy `INFINITE_ACCURACY; \| 6 + level` · evasion 0 · damage `Math.round(super.damageRoll()\*2f); \| super.damageRoll()` · armour `Random.NormalIntRange(0, 1 + level/2)` · EXP 0 · max level 29 · properties DEMONIC. Some numbers are set when it spawns; the evasion formula below is the live one. <small>`core/…/mobs/EbonyMimic.java:44`, `core/…/mobs/Mimic.java:251`, `core/…/mobs/EbonyMimic.java:89`, `core/…/mobs/Mimic.java:239`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** baseSpeed 1; does not move while hidden (PASSIVE). Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/actors/Char.java:175`, `core/…/mobs/Mimic.java:63-64`</small>
- **Attack.** `melee`, reach 1. Adjacent. Attack time: 1. <small>`core/…/mobs/Mob.java:558-568`, `core/…/mobs/Mob.java:753-757`</small>
- **near-invisible lurker.** Always stealthy. Hidden, it is named 'suspicious outline' and drawn at 20% alpha, placed (with 50% chance of trying heaps first) on a plain heap outside special rooms, otherwise on the exit (1 in 5) or a random door. Its hidden bite deals (2+2L)x2 = 4+4L with infinite accuracy. Revealed on a door, it opens the door. *Telegraph:* a faint outline you can examine: 'There seems to be something here, but it's almost totally transparent.' <small>`core/…/mobs/EbonyMimic.java:50-95`, `core/…/sprites/MimicSprite.java:115-141`, `core/…/levels/RegularLevel.java:650-675`, `core/…/trinkets/MimicTooth.java:68-78`, `core/…/actors/actors.properties:1608-1609`</small>
- **rich loot.** An extra random item; all equipment prizes uncursed and at least +1. *Telegraph:* none <small>`core/…/mobs/EbonyMimic.java:97-119`</small>
- **AI.** Starts `passive`; flees: never. As Mimic once revealed. <small>`core/…/mobs/EbonyMimic.java:73-86`</small>
- **Evasion.** defenseSkill 0 (live formula `2+L/2, L=scalingDepth`); evasive: no. 2+L/2. <small>`core/…/mobs/Mimic.java:264-269`</small>
- **Surprise.** Can be surprised: yes. As Mimic while hidden. <small>`core/…/mobs/Mob.java:873-877`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `keep-away`; ranged attacks first: yes.
- **Plan.** Only exists if you carry a Mimic Tooth. Treat every faint outline on a door, the exit or an item pile as a mimic: hit it from range, then melee it down for the +1 loot. Never walk into an outlined cell. <small>`core/…/mobs/EbonyMimic.java:88-95`</small>
- **Kill or nullify: With a Mimic Tooth, scan doors, the exit and loose items for faint outlines.** Those are its only placements. (derived) <small>`core/…/levels/RegularLevel.java:650-675`</small>
- **Kill or nullify: Hit any outline from range first.** The hidden bite is double damage with infinite accuracy; a ranged opener is a surprise hit instead. (derived) <small>`core/…/mobs/EbonyMimic.java:88-95`, `core/…/mobs/Mimic.java:251-257`, `core/…/mobs/Mob.java:873-877`</small>
- **Kill or nullify: Fight it like a weak brute.** Revealed: HP (1+L)\*6, def 2+L/2, acc 6+L, dmg 1+L..2+2L; normal speed, no gas/poison immunity. (derived) <small>`core/…/mobs/Mimic.java:229-269`</small>
- **Kill or nullify: Kill it for its hoard.** It carries no chest items: it drops the Mimic prize, the Mimic Tooth extra random item, and one more random item; equipment among them is uncursed and at least +1. (derived) <small>`core/…/mobs/Mimic.java:332-359`, `core/…/mobs/EbonyMimic.java:97-119`, `core/…/levels/RegularLevel.java:675`</small>
- **Kill or nullify: Holy and light effects.** DEMONIC takes bonus damage from Prismatic Light, Holy Dart and similar. (derived) <small>`core/…/mobs/Mimic.java:58`, `core/…/wands/WandOfPrismaticLight.java:96-100`</small>
- **Escape.** Outrunnable: no; contact breaks at: door, out-of-sight, stairs. As Mimic. <small>`core/…/actors/Char.java:175`</small>

| Class | Note |
|---|---|
| Warrior | Throw a stone at a suspicious outline, then melee the revealed mimic. |
| Mage | Zap a suspicious outline from range, then zap and melee. |
| Rogue | Throwing knife at the suspicious outline (surprise damage bonus), or open it from the cloak's invisibility to skip the bite. |
| Huntress | Arrow any suspicious outline from range; the first shot is a surprise hit. |
| Duelist | Throwing spike at the suspicious outline, then melee. |
| Cleric | Test-hit suspicious outlines from range; holy damage bonus against the DEMONIC mimic. |

**Open questions.**

- Whether walking along a path through an unseen ebony mimic's door cell triggers interact (bite) or an ordinary blocked move depends on the hero's field of view of the cell (Hero.handle needs fieldOfView); not simulated.

### Community notes

- **Tier F.** “<del>Examining a suspicious chest will explicitly tell you if something is off — the safest ID method. If you'd rather not examine, hit the chest first with a ranged weapon, wand or thrown item from a distance; a Mimic will reveal itself and take damage, while a real chest is unaffected.</del>” [source](https://steamcommunity.com/sharedfiles/filedetails/?id=3225596609) (version: unspecified (current-era source)). Does not apply to ebony mimics: they look like a faint outline rather than a chest, have no 'feels off' hint, and exist only when you carry a Mimic Tooth. A ranged hit still reveals them. <small>`core/…/mobs/EbonyMimic.java:59-66`, `core/…/levels/RegularLevel.java:650-675`</small>

??? note "Corrected during verification (3)"

    - Hidden-bite text was garbled ('2x (2+2L)\*2'); it is (2+2L)\*2 = 4+4L (EbonyMimic.java:88-95 over Mimic.java:231-232).
    - 'Kill it for its hoard' claimed chest items; ebony mimics spawn with none (RegularLevel.java:675 passes no items). Rewritten from EbonyMimic.generatePrize.
    - Class notes referred to chests; changed to outlines. defenseSkill formula added.
