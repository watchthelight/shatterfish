# Bestiary: Caves (depths 11-14)

One card per enemy. Mechanics are Tier 1: read from the pinned code at `v4.0.0` and cited.
Tactics are derived from the mechanics and name the lines they rest on. Community notes are
forum and wiki claims graded against the code: Tier 1 confirmed, Tier F contradicted (struck),
Tier 3 not settled. The [bestiary index](index.md) has the cross-cutting rules, the one-line
tactic for every enemy and the tag vocabulary of `tactics/bestiary.json`.

This region's boss is on the [bosses page](bosses.md): [DM-300](bosses.md#dm300), [power pylon](bosses.md#pylon).

## Enemies

- [Vampire bat](#bat) (11-14; rare 2.5% on 9): Surprise it asleep if possible. Otherwise back into a corridor or doorway and melee it as it arrives.
- [Gnoll brute](#brute) (11-14): Sneak-hit it if asleep, then melee it in a corridor. As it hits 0 HP and the rage icon appears, step back and keep moving away along a clear path for about 6...
- [Armored brute](#armoredbrute) (11-14 (2% alternate of Brute)): Treat it as a damage sponge. Soften it with wands, gas or fire first (bombs are reduced by its DR), then melee only with a high-damage weapon.
- [Gnoll shaman (red)](#shaman-redshaman) (11-14 (40% of Shaman family)): Close the distance under cover: use corners and doors so it never has a long clear line, or step in while another monster blocks its line.
- [Gnoll shaman (blue)](#shaman-blueshaman) (11-14 (40% of Shaman family)): Close the distance under cover: use corners and doors so it never has a long clear line, or step in while another monster blocks its line.
- [Gnoll shaman (purple)](#shaman-purpleshaman) (11-14 (20% of Shaman family)): Close the distance under cover: use corners and doors so it never has a long clear line, or step in while another monster blocks its line.
- [Cave spinner](#spinner) (12-14): Sneak-hit it asleep if possible. Otherwise wait for it at a corridor mouth and fight standing still.
- [DM-200](#dm200) (13-14): Meet it in a room and stay adjacent: every turn you are adjacent is a turn it cannot vent.
- [DM-201](#dm201) (13-14 (2% alternate of DM200)): Either avoid it entirely (it cannot move), or walk adjacent while it is awake and targeting you and melee it down with no ranged damage at all.
- [Gnoll guard](#gnollguard) (quest (Blacksmith gnoll mine, branch of 12-14)): Take out the linked sapper first (the guard takes quarter damage until then), keeping the guard adjacent or out of line so it never gets its 16-22 reach hit.
- [Gnoll sapper](#gnollsapper) (quest (Blacksmith gnoll mine, branch of 12-14)): Sneak up while it sleeps and kill it before it acts; its guard then loses its armor.
- [Gnoll geomancer](#gnollgeomancer) (quest boss (Blacksmith gnoll mine, branch of 12-14)): Clear the mine's sapper/guard pairs first where you can, since each living sapper is a future invulnerability shield.
- [DM-300](bosses.md#dm300) (boss 15): Engage on plain floor and stay adjacent, reacting to each ability: leave the gas cloud, or step to an unmarked cell before rocks land.
- [Power pylon](bosses.md#pylon) (boss 15 (DM-300 arena part; NEUTRAL until activated)): When DM-300 supercharges, find the pylon that turned active (not the one nearest you).

## Rules these cards rely on

Tier 1, cited. Written for this region by the reader who verified its cards; the
[index](index.md#cross-cutting-rules) states the ones every region shares.

- **Surprise attacks.** A mob is surprised by the hero when the hero is invisible, or the mob has not seen the hero (enemySeen false, which is always the case while SLEEPING), or the hero stands outside the mob's field of view; for an attack the hero must also be able to surprise-attack (not wielding a Flail and meeting the weapon's STR requirement). A surprised (or paralysed) mob has defenseSkill 0, so the hero's accuracy roll always wins: a surprise attack never misses. Invisible attackers are given infinite accuracy outright. Dagger, Dirk, Assassin's Blade, Throwing Knife and Kunai roll 75%-to-max damage on a surprised target. Any damage wakes a sleeper (SLEEPING -&gt; WANDERING, alerted) and aggroes it on the attacker. <small>`core/…/mobs/Mob.java:873-877`, `core/…/hero/Hero.java:744-752`, `core/…/mobs/Mob.java:796-802`, `core/…/actors/Char.java:627-630`, `core/…/actors/Char.java:646-684`, `core/…/mobs/Mob.java:1237`, `core/…/melee/Dagger.java:62-75`, `core/…/missiles/ThrowingKnife.java:50-64`, `core/…/mobs/Mob.java:902-911`, `core/…/mobs/Mob.java:834-841`, `core/…/melee/Dirk.java:52`, `core/…/melee/AssassinsBlade.java:52`, `core/…/missiles/Kunai.java:52`</small>
- **Accuracy vs evasion.** hit() rolls acuRoll = U(0, attackSkill) and defRoll = U(0, defenseSkill) with multipliers (Bless x1.25, Hex x0.8, Daze x0.5 on either side); the attack lands when acuRoll &gt;= defRoll. Magic attacks (hit(..., magic=true)) double the accuracy multiplier. Derived: with accuracy A and evasion D, P(hit) = 1 - D/(2A) when A &gt;= D, else A/(2D). The hero starts at attackSkill 10 / defenseSkill 5 and gains +1 of each per level. Missile weapons (thrown weapons and Spirit Bow arrows) get x1.5 accuracy at range and x0.5 when adjacent (+0.25 per point of the Point Blank talent). <small>`core/…/actors/Char.java:615-685`, `core/…/hero/Hero.java:219-220`, `core/…/hero/Hero.java:2075-2076`, `core/…/hero/Hero.java:511-565`, `core/…/missiles/MissileWeapon.java:215-233`, `core/…/weapon/SpiritBow.java:293`</small>
- **Armor (DR) applies only to Char.attack.** The defender's drRoll is subtracted only inside Char.attack (melee and thrown/bow hits). Effects that call damage() directly - wand zaps, gas, rockfalls, shaman bolts, pylon shocks - skip the target's armor. Weakness on the attacker multiplies damage by 0.67; Vulnerable on the defender multiplies post-armor damage by 1.33. Exception: bomb explosions subtract the target's drRoll themselves, so bombs are NOT armor-bypassing. <small>`core/…/actors/Char.java:388-390`, `core/…/actors/Char.java:479-496`, `core/…/actors/Char.java:826-835`, `core/…/wands/WandOfMagicMissile.java:62`, `core/…/bombs/Bomb.java:197`</small>
- **Resistances and immunities.** Each matching resistance halves the effect's damage; an immunity nullifies it. Properties carry sets: INORGANIC is immune to Bleeding, ToxicGas and Poison; BOSS/MINIBOSS are immune to AllyBuff and Dread (BOSS also resists Grim, Retribution, Psionic Blast); ELECTRIC resists lightning sources; IMMOVABLE is immune to Vertigo; STATIC is immune to AI-state debuffs, stuns and slows. <small>`core/…/actors/Char.java:1356-1394`, `core/…/actors/Char.java:926-930`, `core/…/actors/Char.java:1413-1441`</small>
- **Speed and attack delay.** A mob's move costs 1/speed() time; speed = baseSpeed (default 1) modified by Cripple /2, Haste x3, Adrenaline x2 etc. A mob's attack costs attackDelay() = 1 (Adrenaline /1.5). Derived: a same-speed pursuer cannot catch a hero who keeps moving along an open path (each step it takes only re-establishes adjacency and uses its turn), while a speed-2 mob closes two cells per hero turn and cannot be outrun. <small>`core/…/actors/Char.java:175`, `core/…/actors/Char.java:770-783`, `core/…/mobs/Mob.java:1353-1356`, `core/…/mobs/Mob.java:753-757`, `core/…/mobs/Mob.java:759-779`</small>
- **Ballistica line of fire.** Flags: PROJECTILE = STOP_TARGET\|STOP_CHARS\|STOP_SOLID, MAGIC_BOLT = STOP_CHARS\|STOP_SOLID. A path stops on the first character when STOP_CHARS is set (so a body in between blocks bolts and thrown weapons), stops before impassable terrain, and stops ON passable-but-solid cells such as closed doors and spider webs unless IGNORE_SOFT_SOLID is set. <small>`core/…/mechanics/Ballistica.java:42-51`, `core/…/mechanics/Ballistica.java:115-142`, `core/…/levels/Terrain.java:90`, `core/…/blobs/Web.java:101-108`</small>
- **Doors.** A closed DOOR is PASSABLE, LOS_BLOCKING and SOLID. Any character stepping on it opens it (walkers via pressCell, fliers directly); it closes again when vacated unless an item heap or another character is on it. Because a closed door is solid it is never openSpace, so LARGE mobs (DM-200, DM-201, DM-300) cannot pass doors or 1-wide gaps. <small>`core/…/levels/Terrain.java:90-91`, `core/…/levels/Level.java:1174`, `core/…/levels/Level.java:1214-1215`, `core/…/levels/Level.java:1257-1258`, `core/…/features/Door.java:35-57`, `core/…/actors/Char.java:1311-1313`, `core/…/mobs/Mob.java:580-582`, `core/…/levels/Level.java:889-905`</small>
- **Breaking pursuit.** A hunting mob only refreshes its target cell while the hero is in its FOV; otherwise it walks to the last seen cell, and when it cannot get closer and cannot see the hero it shows the 'lost' mark and drops to WANDERING. Wandering mobs re-notice with chance 1/(distance/2 + stealth) per turn, sleeping ones 1/(distance + stealth). Stairs: only allies are carried between floors, enemies never follow. Default FOV is 8 cells. <small>`core/…/mobs/Mob.java:1326-1362`, `core/…/mobs/Mob.java:1387-1407`, `core/…/mobs/Mob.java:1282-1284`, `core/…/mobs/Mob.java:1244-1246`, `core/…/mobs/Mob.java:1686-1705`, `core/…/actors/Char.java:191`</small>
- **Starting AI state.** Mobs placed at level generation start SLEEPING; mobs spawned later during play start WANDERING. <small>`core/…/mobs/Mob.java:124`, `core/…/levels/Level.java:770-776`</small>
- **Fleeing and terror.** Terror or Dread forces FLEEING. A fleeing mob that has nowhere to run turns and fights if it can see the enemy (unless still terrified). <small>`core/…/mobs/Mob.java:284-286`, `core/…/mobs/Mob.java:1437-1487`</small>
- **Paralysis breaks on damage.** Each hit on a paralysed character adds to a ParalysisResist pool; paralysis ends when NormalIntRange(0, pooled damage) &gt;= NormalIntRange(0, current HP). So hitting a paralysed target, or getting hit while paralysed, may end it early. Paralysed defenders also have defenseSkill 0 (mob) or half evasion (hero). <small>`core/…/buffs/Paralysis.java:51-63`, `core/…/buffs/Paralysis.java:89-104`, `core/…/mobs/Mob.java:796-802`, `core/…/hero/Hero.java:597-599`</small>
- **Telegraphed ground attacks.** Rockfalls (DM-300, gnoll sapper, gnoll geomancer) and gnoll boulder throws mark their cells with GameScene.targetedCell and land after a delay of the target's own action time clamped to 1-3 turns, so the hero always gets one action to leave the marked cells. Rockfall damage is applied through damage() (no armor) and paralyses survivors. <small>`core/…/mobs/DM300.java:464-469`, `core/…/mobs/GnollGeomancer.java:817-823`, `core/…/mobs/GnollSapper.java:200-209`, `core/…/mobs/DelayedRockFall.java:56-73`, `core/…/scenes/GameScene.java:1236-1248`</small>
- **Max-level -2 mobs.** A mob grants EXP only if hero level &lt;= maxLvl, and rolls loot only if hero level &lt;= maxLvl + 2. Gnoll guards, gnoll sappers and pylons have maxLvl -2, so killing them never gives EXP or loot, whatever the codex EXP/loot fields say. <small>`core/…/mobs/Mob.java:951`, `core/…/mobs/Mob.java:1059-1060`, `core/…/mobs/GnollGuard.java:44`, `core/…/mobs/GnollSapper.java:49`, `core/…/mobs/Pylon.java:56`</small>
- **Potion of Purity immunity.** Drinking a Potion of Purity gives BlobImmunity for 20 turns. Its immunity list includes ToxicGas, CorrosiveGas, ParalyticGas, Web and the Electricity damage source, and Char.isImmune reads buff immunities, so the drinker takes no damage from toxic or corrosive gas, is not rooted by webs, and takes 0 from pylon shocks and DM-300's energized floor (both deal damage with an Electricity source). <small>`core/…/potions/PotionOfPurity.java:95`, `core/…/buffs/BlobImmunity.java:49`, `core/…/buffs/BlobImmunity.java:63-77`, `core/…/actors/Char.java:925-927`, `core/…/actors/Char.java:1381-1383`, `core/…/levels/Level.java:1162`, `core/…/mobs/Pylon.java:138`, `core/…/levels/CavesBossLevel.java:914`</small>
- **Integer ability cooldowns shrink per hit.** Gnoll sappers and the gnoll geomancer do 'abilityCooldown -= dmg/10f' on an int field; Java truncates toward zero, so while the cooldown is positive every damaging hit removes ceil(dmg/10) turns (a 1-damage hit still removes a full turn). Many small hits therefore speed up their abilities more than a few big ones. <small>`core/…/mobs/GnollSapper.java:108-112`, `core/…/mobs/GnollGeomancer.java:279`</small>

**Open questions.**

- Codex mobs.json extraction gaps for this cohort: Brute and ArmoredBrute damage show 0-0 (source is a ternary on BruteRage, Brute.java:59-61 / ArmoredBrute.java:49-51), ArmoredBrute DR shows 0-0 (source is Brute's 0-8 plus 4, ArmoredBrute.java:55-57), and GnollGuard damage shows 0-0 (source is conditional on range, GnollGuard.java:83-89). The cards use the source values.
- PathFinder.CIRCLE8 is ordered top-left, top, top-right, right, bottom-right, bottom, bottom-left, left (PathFinder.java:74): index +1 is one step clockwise on screen. Resolved for the Pylon card.

??? note "Rules corrected during verification (7)"

    - Surprise rule: added citations for Dirk, Assassin's Blade and Kunai (the card named them but cited only Dagger and Throwing Knife).
    - Accuracy rule: Point Blank adjacent multiplier restated as 0.5 + 0.25 per talent point (MissileWeapon.java:226).
    - Armor rule: added that Bomb.explode subtracts drRoll (Bomb.java:197), so bombs do not bypass DR.
    - Telegraph rule: GameScene citation moved from the 3-arg delegating overload (1245-1246) to the method that sets the marker expiry (1249-1261).
    - Added rule: maxLvl -2 mobs (gnoll guard, sapper, pylon) give no EXP or loot (Mob.java:951, 1060).
    - Added rule: drunk Potion of Purity (BlobImmunity, 20 turns) nullifies toxic/corrosive gas, webs, pylon shocks and the energized floor.
    - Added rule: int-truncated ability cooldowns (sapper, geomancer) lose ceil(dmg/10) per hit.

## Vampire bat {#bat}

`actors.mobs.Bat` · depths 11-14; rare 2.5% on 9 · **Tactic:** Surprise it asleep if possible. Otherwise back into a corridor or doorway and melee it as it arrives.

**Stats** (from the [Codex](../codex/mobs.md)): HT 30 · accuracy 16 · evasion 15 · damage 5-18 · armour 0-4 · EXP 7 · max level 15 · properties none · loot PotionOfHealing (16.7% base). <small>`core/…/mobs/Bat.java:33`, `core/…/mobs/Bat.java:57`, `core/…/mobs/Bat.java:52`, `core/…/mobs/Bat.java:62`</small>

Depths corrected by hand: the boss floors 5, 10 and 15 create no rotation mobs and run no respawner, so this enemy never spawns there. <small>`core/…/mobs/MobSpawner.java:70-155`, `core/…/levels/SewerBossLevel.java:132-138`, `core/…/levels/PrisonBossLevel.java:597-604`, `core/…/levels/CavesBossLevel.java:207-219`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** baseSpeed 2: two moves per hero turn. Speed `fast`; flies: yes; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/mobs/Bat.java:40`, `core/…/mobs/Bat.java:45`, `core/…/mobs/Mob.java:1355`, `core/…/mobs/Mob.java:571-575`, `core/…/levels/Level.java:1174`, `core/…/levels/Level.java:1213-1216`, `core/…/mobs/Mob.java:1686-1705`</small>
- **Attack.** `melee`, reach 1. Adjacent only (default Mob.canAttack). Attack time: 1 turn (default).. <small>`core/…/mobs/Mob.java:558-568`, `core/…/mobs/Mob.java:753-757`</small>
- **Life drain.** After armor (and after Vulnerable), heals itself by (damage dealt - 4), capped at missing HP. *Telegraph:* Green healing number over the bat. <small>`core/…/mobs/Bat.java:72-83`, `core/…/actors/Char.java:486-498`</small>
- **Flight.** Paths over chasms and other avoid-cells; never presses traps. Falls when it dies over a chasm. *Telegraph:* none <small>`core/…/mobs/Mob.java:571-575`, `core/…/levels/Level.java:1174`, `core/…/mobs/Bat.java:66-70`, `core/…/actors/Char.java:1127-1129`</small>
- **Immune.** Floor traps (flying chars skip pressCell). <small>`core/…/levels/Level.java:1174`, `core/…/levels/Level.java:1212`</small>
- **AI.** Starts `sleeping`; flees: never (no custom fleeing; only Terror/Dread). Default Mob AI: sleeps at generation, wanders if respawned, hunts once noticed. <small>`core/…/mobs/Mob.java:124`, `core/…/levels/Level.java:774-776`, `core/…/mobs/Mob.java:284-286`</small>
- **Evasion.** defenseSkill 15; evasive: no. Average cave evasion; hero at level ~12 (attack 21) lands ~64% of plain melee swings (derived from hit formula). <small>`core/…/mobs/Bat.java:39`, `core/…/actors/Char.java:646-684`</small>
- **Surprise.** Can be surprised: yes. Standard rule. Sleeping bats take guaranteed hits. <small>`core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/actors/Char.java:646-684`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `chokepoint`; ranged attacks first: no.
- **Plan.** Surprise it asleep if possible. Otherwise back into a corridor or doorway and melee it as it arrives. Do not try to kite or flee. Kill it quickly, before its drain outheals your damage; strong armor cuts both its damage and its healing. <small>`core/…/mobs/Bat.java:72-83`, `core/…/actors/Char.java:953-961`, `core/…/actors/Char.java:392-398`, `core/…/mobs/Mob.java:783-794`, `core/…/missiles/MissileWeapon.java:223-233`, `core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/actors/Char.java:646-684`, `core/…/actors/Char.java:390`, `core/…/actors/Char.java:483-486`</small>
- **Kill or nullify: Hit hard and fast; armor also starves its healing..** It heals only (post-armor damage - 4): with high DR most bites heal little or nothing. (derived) <small>`core/…/mobs/Bat.java:75-80`, `core/…/actors/Char.java:486-498`</small>
- **Kill or nullify: Open with a surprise hit if it is asleep..** Surprised mobs have 0 evasion; 30 HP means one strong sneak hit takes a large chunk. (derived) <small>`core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/actors/Char.java:646-684`</small>
- **Kill or nullify: Fight it from a corridor or doorway when more than one bat is around..** Melee reach 1 means only cells adjacent to you can attack; a 1-wide corridor lets at most one or two reach you. (derived) <small>`core/…/mobs/Mob.java:558-568`</small>
- **Kill or nullify: At most one ranged shot as it closes, then melee..** Speed 2 closes two cells per turn; thrown weapons get x1.5 accuracy at range but x0.5 adjacent. (derived) <small>`core/…/mobs/Bat.java:40`, `core/…/missiles/MissileWeapon.java:223-233`</small>
- **Escape.** Outrunnable: no; contact breaks at: out-of-sight, door, stairs. Speed 2 and flight make running pointless on open ground; closing a door breaks sight but it reaches your last seen cell quickly. Taking the stairs leaves it behind. <small>`core/…/mobs/Bat.java:40`, `core/…/mobs/Mob.java:1342-1350`, `core/…/mobs/Mob.java:1400-1405`, `core/…/levels/Terrain.java:90`, `core/…/mobs/Mob.java:1686-1705`</small>

| Class | Note |
|---|---|
| Warrior | Tank it in melee: armor directly reduces its drain, and the seal shield triggers at half HP. |
| Mage | Wand damage bypasses its 0-4 DR; zap as it closes and finish in melee. |
| Rogue | Sleeping bats are ideal sneak-attack targets for Dagger/Dirk bonus damage. |
| Huntress | At most one bow shot while it closes, then melee or point-blank arrows (x0.5 accuracy adjacent unless Point Blank). |
| Duelist | A weapon ability's burst helps kill it before the drain matters. |
| Cleric | Guiding Light makes its evasion 0 against you for the next strike. |

### Community notes

No community claim about this enemy was found.

??? note "Corrected during verification (3)"

    - Hand check: depths '11-15; rare 2.5% on 9' corrected to '11-14; rare 2.5% on 9'; the sewer, prison and caves boss floors create no rotation mobs and add no respawner (SewerBossLevel.java:132-138, PrisonBossLevel.java:597-604, CavesBossLevel.java:207-219).
    - Tactics basis: added the direct-damage/DR citations that support the Mage note (wand damage skips DR).
    - Life drain: clarified that the heal is computed from post-armor, post-Vulnerable damage (Char.java:486-498).

## Gnoll brute {#brute}

`actors.mobs.Brute` · depths 11-14 · **Tactic:** Sneak-hit it if asleep, then melee it in a corridor. As it hits 0 HP and the rage icon appears, step back and keep moving away along a clear path for about 6 turns so the rage shield drains and it dies without swinging.

**Stats** (from the [Codex](../codex/mobs.md)): HT 40 · accuracy 20 · evasion 15 · damage `buff(BruteRage.class) != null ? Random.NormalIntRange( 15, 40 ) : Random.NormalIntRange( 5, 25 )` · armour 0-8 · EXP 8 · max level 16 · properties none · loot Gold (50% base). <small>`core/…/mobs/Brute.java:40`, `core/…/mobs/Brute.java:65`, `core/…/mobs/Brute.java:58`, `core/…/mobs/Brute.java:70`</small>

Depths corrected by hand: the boss floors 5, 10 and 15 create no rotation mobs and run no respawner, so this enemy never spawns there. <small>`core/…/mobs/MobSpawner.java:70-155`, `core/…/levels/SewerBossLevel.java:132-138`, `core/…/levels/PrisonBossLevel.java:597-604`, `core/…/levels/CavesBossLevel.java:207-219`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** baseSpeed 1. Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/mobs/Mob.java:1353-1356`, `core/…/actors/Char.java:175`, `core/…/actors/Char.java:770-783`, `core/…/levels/Level.java:1174`, `core/…/levels/Level.java:1257-1258`, `core/…/levels/Level.java:1214-1215`, `core/…/features/Door.java:35-43`, `core/…/mobs/Mob.java:1686-1705`</small>
- **Attack.** `melee`, reach 1. Adjacent only. Attack time: 1 turn (default).. <small>`core/…/mobs/Mob.java:558-568`, `core/…/mobs/Mob.java:753-757`</small>
- **Enrage on death.** The first time HP reaches 0 (unless it died in a chasm), it gains a BruteRage shield of HT/2+4 and stays alive while the shield holds. Damage becomes 15-40. The shield loses 4 per turn (scaled by the Ascension modifier), and the brute dies when it reaches 0. Healing it above 0 HP removes the rage. *Telegraph:* Shield number pops over it (it displays HT/2 = 20, while the real shield is HT/2+4 = 24) and a BERSERK spell icon shows if it is in view. <small>`core/…/mobs/Brute.java:74-112`, `core/…/mobs/Brute.java:128-157`, `core/…/mobs/Brute.java:58-62`, `core/…/mobs/Brute.java:105-109`</small>
- **AI.** Starts `sleeping`; flees: never (only Terror/Dread). Default Mob AI. <small>`core/…/mobs/Mob.java:124`, `core/…/levels/Level.java:774-776`</small>
- **Evasion.** defenseSkill 15; evasive: no. Average evasion; DR 0-8 blunts weak weapons. <small>`core/…/mobs/Brute.java:46`, `core/…/mobs/Brute.java:69-72`</small>
- **Surprise.** Can be surprised: yes. Standard rule. An Assassin's Preparation KO detaches BruteRage, which finishes a brute that is ALREADY enraged. On a brute that has not raged yet the KO sets HP to 0, and the isAlive() check that follows triggers the rage anyway, so the second phase still happens. <small>`core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/actors/Char.java:646-684`, `core/…/actors/Char.java:517-527`, `core/…/mobs/Brute.java:86-99`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `surprise`; ranged attacks first: no.
- **Plan.** Sneak-hit it if asleep, then melee it in a corridor. As it hits 0 HP and the rage icon appears, step back and keep moving away along a clear path for about 6 turns so the rage shield drains and it dies without swinging. If you are cornered, finish the shield with your biggest hits. Kill nearby shamans first or fight out of their line of fire. <small>`core/…/mobs/Brute.java:86-112`, `core/…/mobs/Brute.java:134-151`, `core/…/actors/Char.java:517-521`, `core/…/actors/Char.java:953-961`, `core/…/mobs/Mob.java:783-794`, `core/…/actors/Char.java:390`, `core/…/actors/Char.java:483-486`, `core/…/actors/Char.java:517-527`, `core/…/mobs/Brute.java:86-93`</small>
- **Kill or nullify: When it drops to 0 HP, walk away for about 6 turns instead of trading hits..** Rage gives a shield of HT/2+4 (24 at 40 HT) that drains 4 per turn, and the brute dies when the shield runs out. The enraged brute hits 15-40, and a same-speed pursuer cannot hit a hero who keeps stepping away along an open path. (derived) <small>`core/…/mobs/Brute.java:86-112`, `core/…/mobs/Brute.java:134-151`, `core/…/mobs/Brute.java:58-62`, `core/…/mobs/Mob.java:1353-1356`</small>
- **Kill or nullify: Or burst the rage shield in one or two big hits..** Damage to the enraged brute goes into the shield; once the shield is gone isAlive() is false. (derived) <small>`core/…/mobs/Brute.java:99`, `core/…/actors/Char.java:982-984`</small>
- **Kill or nullify: Kill it by knocking it into a chasm..** A chasm death sets hasRaged, so no rage. (derived) <small>`core/…/mobs/Brute.java:74-81`</small>
- **Kill or nullify: Open with a surprise attack..** Guaranteed hit on 40 HP; one strong sneak hit can take most of it. (derived) <small>`core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/actors/Char.java:646-684`</small>
- **Kill or nullify: Watch for shamans behind it, especially blue ones..** Vulnerable (blue shaman) raises post-armor damage by 1.33x, and the brute's 5-25 (15-40 enraged) is the cave's heaviest normal melee. (derived) <small>`core/…/mobs/Shaman.java:162-171`, `core/…/actors/Char.java:494-496`, `core/…/mobs/Brute.java:58-62`</small>
- **Escape.** Outrunnable: no; contact breaks at: door, out-of-sight, stairs. Same speed: it cannot close on you while you keep moving, but you cannot open distance either. Break sight through doors or corners. <small>`core/…/mobs/Mob.java:1353-1356`, `core/…/actors/Char.java:175`, `core/…/actors/Char.java:770-783`, `core/…/mobs/Mob.java:1342-1350`, `core/…/mobs/Mob.java:1400-1405`, `core/…/levels/Terrain.java:90`, `core/…/mobs/Mob.java:1686-1705`</small>

| Class | Note |
|---|---|
| Warrior | Trade blows at normal HP (armor vs 5-25), but still walk away from the enraged 15-40 phase. |
| Mage | Wands skip its 0-8 DR, and zapping the rage shield from range while backing off is safe. |
| Rogue | Assassin's Preparation KO finishes an already-enraged brute (it detaches BruteRage); used before the rage it only triggers it. Save the KO for the rage phase. |
| Huntress | Arrows while it approaches; during rage keep retreating and shooting (the bow does not need adjacency). |
| Duelist | Save a weapon ability to break the rage shield at once if you cannot retreat. |
| Cleric | Guiding Light's illumination zeroes its evasion for a reliable finishing hit. |

**Open questions.**

- BruteRage.act runs once per TICK; exact turns to self-death depend on Ascension statModifier (Brute.java:142). The base-game figure (6 turns) assumes the modifier is 1.

### Community notes

- **Tier 1.** “A Gnoll Brute becomes Enraged when brought to 0 HP, gaining a temporary shield and a big damage buff that can punch through most armor; the safe play is to run away and let the shield/enrage timer expire rather than trading hits with it.” [source](https://pixeldungeon.fandom.com/wiki/Gnoll_brute) (version: old wiki page (flagged oldVersion)). Confirmed: at 0 HP it gains a BruteRage shield of HT/2+4 (24), deals 15-40 instead of 5-25, and the shield loses 4 per turn, killing it after about 6 turns. A same-speed brute cannot hit a hero who keeps stepping away along an open path, so running it out works. <small>`core/…/mobs/Brute.java:86-112`, `core/…/mobs/Brute.java:58-62`, `core/…/mobs/Brute.java:134-151`, `core/…/mobs/Mob.java:1353-1356`</small>

??? note "Corrected during verification (4)"

    - Hand check: depths '11-15' corrected to '11-14'; the sewer, prison and caves boss floors create no rotation mobs and add no respawner (SewerBossLevel.java:132-138, PrisonBossLevel.java:597-604, CavesBossLevel.java:207-219).
    - Surprise note: an Assassin KO does NOT prevent the rage on a fresh brute; Char.java:517-523 sets HP 0, then Brute.isAlive (86-93) calls triggerEnrage because hasRaged is false. It only finishes an already-enraged brute.
    - Rogue class note corrected to match the KO/enrage order.
    - Enrage telegraph: the floating number shows HT/2 (20) while setShield uses HT/2+4 (24), Brute.java:105-106.

## Armored brute {#armoredbrute}

`actors.mobs.ArmoredBrute` · depths 11-14 (2% alternate of Brute) · **Tactic:** Treat it as a damage sponge. Soften it with wands, gas or fire first (bombs are reduced by its DR), then melee only with a high-damage weapon.

**Stats** (from the [Codex](../codex/mobs.md)): HT 40 · accuracy 20 · evasion 15 · damage `buff(ArmoredRage.class) != null ? Random.NormalIntRange( 15, 40 ) : Random.NormalIntRange( 5, 25 )` · armour `super.drRoll() + 4` · EXP 8 · max level 16 · properties none · loot ARMOR (100% base). <small>`core/…/mobs/ArmoredBrute.java:37`, `core/…/mobs/Brute.java:65`, `core/…/mobs/ArmoredBrute.java:48`, `core/…/mobs/ArmoredBrute.java:55`</small>

Depths corrected by hand: the boss floors 5, 10 and 15 create no rotation mobs and run no respawner, so this enemy never spawns there. <small>`core/…/mobs/MobSpawner.java:70-155`, `core/…/levels/SewerBossLevel.java:132-138`, `core/…/levels/PrisonBossLevel.java:597-604`, `core/…/levels/CavesBossLevel.java:207-219`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** baseSpeed 1 (inherits Brute). Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/mobs/Mob.java:1353-1356`, `core/…/actors/Char.java:175`, `core/…/actors/Char.java:770-783`, `core/…/levels/Level.java:1174`, `core/…/levels/Level.java:1257-1258`, `core/…/levels/Level.java:1214-1215`, `core/…/features/Door.java:35-43`, `core/…/mobs/Mob.java:1686-1705`</small>
- **Attack.** `melee`, reach 1. Adjacent only. Attack time: 1 turn (default).. <small>`core/…/mobs/Mob.java:558-568`, `core/…/mobs/Mob.java:753-757`</small>
- **Slow enrage on death.** Like Brute, but the ArmoredRage shield is HT/2+1 (21) and drains only 1 per 3 turns, about 63 turns in total (the source comment says 60), so waiting it out does not work. *Telegraph:* Shield number plus an 'enraged' warning status over it. <small>`core/…/mobs/ArmoredBrute.java:59-68`, `core/…/mobs/ArmoredBrute.java:78-100`</small>
- **AI.** Starts `sleeping`; flees: never (only Terror/Dread). Default Mob AI. <small>`core/…/mobs/Mob.java:124`, `core/…/levels/Level.java:774-776`</small>
- **Evasion.** defenseSkill 15; evasive: no. Evasion as Brute, but DR 4-12 makes chip damage nearly useless. <small>`core/…/mobs/Brute.java:46`, `core/…/mobs/ArmoredBrute.java:54-57`</small>
- **Surprise.** Can be surprised: yes. Standard rule; the surprise hit still goes through its DR. <small>`core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/actors/Char.java:646-684`, `core/…/actors/Char.java:390`, `core/…/actors/Char.java:483-486`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `keep-away`; ranged attacks first: yes.
- **Plan.** Treat it as a damage sponge. Soften it with wands, gas or fire first (bombs are reduced by its DR), then melee only with a high-damage weapon. When it enrages, keep dealing burst damage to strip the 21-point shield, because it will not expire in time. If you cannot out-damage 4-12 DR, avoid it: close doors and take the stairs. <small>`core/…/actors/Char.java:390`, `core/…/actors/Char.java:483-486`, `core/…/actors/Char.java:392-398`, `core/…/actors/Char.java:517-521`, `core/…/mobs/ArmoredBrute.java:78-100`, `core/…/actors/Char.java:517-527`, `core/…/mobs/Brute.java:86-99`, `core/…/bombs/Bomb.java:197`</small>
- **Kill or nullify: Use DR-bypassing damage: wands, gas, fire (not bombs)..** DR is subtracted only in Char.attack, so damage() sources skip its 4-12 DR. Bombs are the exception: Bomb.explode subtracts drRoll itself. (derived) <small>`core/…/mobs/ArmoredBrute.java:54-57`, `core/…/actors/Char.java:390`, `core/…/actors/Char.java:483-486`, `core/…/bombs/Bomb.java:197`</small>
- **Kill or nullify: Do not try to outwait the rage; kill the 21 shield directly or leave..** ArmoredRage loses 1 shield per 3 turns (~60 turns). (derived) <small>`core/…/mobs/ArmoredBrute.java:78-100`</small>
- **Kill or nullify: Use a chasm if one is available..** A chasm death prevents enrage (inherited from Brute). (derived) <small>`core/…/mobs/Brute.java:74-81`</small>
- **Kill or nullify: Often worth the fight: loot is guaranteed armor (25% plate, else scale) while your level is &lt;= 18..** lootChance 1 with ScaleArmor/PlateArmor createLoot, but rollToDropLoot returns early once hero level &gt; maxLvl + 2 (16 + 2). (derived) <small>`core/…/mobs/ArmoredBrute.java:42-44`, `core/…/mobs/ArmoredBrute.java:70-76`, `core/…/mobs/Mob.java:1060`, `core/…/mobs/Brute.java:49`</small>
- **Escape.** Outrunnable: no; contact breaks at: door, out-of-sight, stairs. Same speed as the hero. It cannot catch you while you keep moving but will follow; break sight with doors. <small>`core/…/mobs/Mob.java:1353-1356`, `core/…/actors/Char.java:175`, `core/…/actors/Char.java:770-783`, `core/…/mobs/Mob.java:1342-1350`, `core/…/mobs/Mob.java:1400-1405`, `core/…/levels/Terrain.java:90`, `core/…/mobs/Mob.java:1686-1705`</small>

| Class | Note |
|---|---|
| Warrior | Needs a high-tier weapon; low-damage weapons bounce off 4-12 DR. |
| Mage | Wands bypass DR entirely; kite and zap. |
| Rogue | Assassin's Preparation KO finishes it only once it is already enraged (the KO detaches ArmoredRage, a BruteRage subclass); before that the KO just triggers the rage. A sneak hit is still reduced by DR. |
| Huntress | Sniper subclass ignores DR with bow shots at range, which makes this an ideal target. |
| Duelist | Use high-damage abilities; skip chip attacks. |
| Cleric | Spell damage delivered through damage() skips its DR. |

**Open questions.**

- Which specific Cleric spells deal damage through damage() rather than Char.attack was not audited for this card.

### Community notes

- **Tier F.** “<del>A Gnoll Brute becomes Enraged when brought to 0 HP, gaining a temporary shield and a big damage buff that can punch through most armor; the safe play is to run away and let the shield/enrage timer expire rather than trading hits with it.</del>” [source](https://pixeldungeon.fandom.com/wiki/Gnoll_brute) (version: old wiki page (flagged oldVersion)). Applied to the armored variant, the 'run away and let it expire' advice fails: ArmoredRage starts at 21 and loses only 1 shield per 3 turns (~63 turns). Kill the shield or leave the floor instead. <small>`core/…/mobs/ArmoredBrute.java:59-68`, `core/…/mobs/ArmoredBrute.java:78-100`</small>

??? note "Corrected during verification (5)"

    - Hand check: depths '11-15 (2% alternate of Brute)' corrected to '11-14 (2% alternate of Brute)'; the sewer, prison and caves boss floors create no rotation mobs and add no respawner (SewerBossLevel.java:132-138, PrisonBossLevel.java:597-604, CavesBossLevel.java:207-219).
    - Counter 'wands, bombs, gas, fire': bombs removed; Bomb.java:197 subtracts the target's drRoll.
    - Loot counter: guaranteed armor only while hero level &lt;= 18 (Mob.java:1060, maxLvl 16).
    - Tactics: removed bombs from the DR-bypass list; Rogue note corrected (KO before the rage only triggers it).
    - Rage duration: 21 shield at 1 per 3 turns is ~63 turns; stated as such.

## Gnoll shaman (red) {#shaman-redshaman}

`actors.mobs.Shaman.RedShaman` · depths 11-14 (40% of Shaman family) · **Tactic:** Close the distance under cover: use corners and doors so it never has a long clear line, or step in while another monster blocks its line.

**Stats** (from the [Codex](../codex/mobs.md)): HT 35 · accuracy 18 · evasion 15 · damage 5-10 · armour 0-6 · EXP 8 · max level 16 · properties none · loot WAND (3% base). <small>`core/…/mobs/Shaman.java:151`, `core/…/mobs/Shaman.java:63`, `core/…/mobs/Shaman.java:58`, `core/…/mobs/Shaman.java:68`</small>

Depths corrected by hand: the boss floors 5, 10 and 15 create no rotation mobs and run no respawner, so this enemy never spawns there. <small>`core/…/mobs/MobSpawner.java:70-155`, `core/…/levels/SewerBossLevel.java:132-138`, `core/…/levels/PrisonBossLevel.java:597-604`, `core/…/levels/CavesBossLevel.java:207-219`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** baseSpeed 1. Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/mobs/Mob.java:1353-1356`, `core/…/actors/Char.java:175`, `core/…/actors/Char.java:770-783`, `core/…/levels/Level.java:1174`, `core/…/levels/Level.java:1257-1258`, `core/…/levels/Level.java:1214-1215`, `core/…/features/Door.java:35-43`, `core/…/mobs/Mob.java:1686-1705`</small>
- **Attack.** `bolt`, reach 8. Can attack when adjacent (melee) or when a MAGIC_BOLT path from it reaches the target unobstructed: the path stops on the first character and on solid cells, including closed doors and webs. Reach is bounded by its FOV (default 8). Attack time: 1 turn for both melee and bolt.. <small>`core/…/mobs/Shaman.java:72-76`, `core/…/mobs/Shaman.java:91-108`, `core/…/mobs/Shaman.java:113-115`, `core/…/mechanics/Ballistica.java:49`, `core/…/mechanics/Ballistica.java:130-139`, `core/…/actors/Char.java:191`</small>
- **Earthen bolt.** When not adjacent it zaps: magic accuracy (x2), 6-15 damage (scaled by the Ascension modifier) through damage() (hero armor does not reduce it), and 50% on hit to apply Weakness. When adjacent it only melees for 5-10, which armor reduces. *Telegraph:* Visible zap animation with a red bolt (sprite zap, then onZapComplete); no warning a turn ahead. <small>`core/…/mobs/Shaman.java:113-137`, `core/…/actors/Char.java:615-617`, `core/…/mobs/Shaman.java:141-144`, `core/…/sprites/ShamanSprite.java:63-70`, `core/…/sprites/ShamanSprite.java:90`</small>
- **Inflicts Weakness.** 20 turns (Weakness.DURATION); hero's outgoing attack damage x0.67. <small>`core/…/mobs/Shaman.java:156-159`, `core/…/buffs/Weakness.java:28`, `core/…/actors/Char.java:479-481`</small>
- **AI.** Starts `sleeping`; flees: never (only Terror/Dread). Default Hunting: it attacks whenever canAttack is true, so it stands still and zaps from wherever it has a clear bolt line. It only walks toward you when the line is blocked. It does not try to keep its distance. <small>`core/…/mobs/Mob.java:124`, `core/…/levels/Level.java:774-776`, `core/…/mobs/Mob.java:1326-1362`</small>
- **Evasion.** defenseSkill 15; evasive: no. Average evasion, 35 HP: the frailest cave gnoll. <small>`core/…/mobs/Shaman.java:46-48`</small>
- **Surprise.** Can be surprised: yes. Standard rule. <small>`core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/actors/Char.java:646-684`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `close`; ranged attacks first: no.
- **Plan.** Close the distance under cover: use corners and doors so it never has a long clear line, or step in while another monster blocks its line. Once adjacent, melee it down (35 HP). If it is asleep, sneak-hit it. With several gnolls around, kill the shaman before the brute when the shaman's debuff multiplies the brute's damage. <small>`core/…/mobs/Shaman.java:91-108`, `core/…/mobs/Shaman.java:113-137`, `core/…/actors/Char.java:627-630`, `core/…/mobs/Mob.java:783-794`, `core/…/actors/Char.java:390`, `core/…/actors/Char.java:483-486`</small>
- **Kill or nullify: Get adjacent. Do not stand at range trading shots..** Adjacent, it switches to plain 5-10 melee that armor reduces, with no debuff. At range it fires double-accuracy 6-15 bolts that ignore armor, with a 50% debuff chance. (derived) <small>`core/…/mobs/Shaman.java:91-108`, `core/…/mobs/Shaman.java:113-137`, `core/…/actors/Char.java:615-617`, `core/…/actors/Char.java:390`, `core/…/actors/Char.java:483-486`</small>
- **Kill or nullify: Approach around corners or through doors so it has to come to you..** The bolt needs a MAGIC_BOLT path that stops on solid cells; out of line it must walk closer (getCloser), which often ends adjacent at the corner. (derived) <small>`core/…/mobs/Shaman.java:72-76`, `core/…/mechanics/Ballistica.java:130-136`, `core/…/mobs/Mob.java:1342-1356`, `core/…/levels/Terrain.java:90`</small>
- **Kill or nullify: Put another body between you and it..** MAGIC_BOLT stops on the first character, so an ally or another mob in the line absorbs or blocks the bolt. (derived) <small>`core/…/mechanics/Ballistica.java:49`, `core/…/mechanics/Ballistica.java:137-139`</small>
- **Kill or nullify: Red: its Weakness cuts your damage by a third for 20 turns, so kill it before a long fight with a tanky enemy..** Debuff priority follows the effect sizes cited under inflicts. (derived) <small>`core/…/mobs/Shaman.java:156-159`, `core/…/buffs/Weakness.java:28`, `core/…/actors/Char.java:479-481`</small>
- **Kill or nullify: Sneak-kill it asleep..** 35 HP and 0 evasion when surprised. (derived) <small>`core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/actors/Char.java:646-684`</small>
- **Escape.** Outrunnable: no; contact breaks at: out-of-sight, door, stairs. Same speed. Breaking the bolt line matters more than distance: any wall, closed door or body in the line stops the zap. <small>`core/…/mobs/Mob.java:1342-1350`, `core/…/mobs/Mob.java:1400-1405`, `core/…/levels/Terrain.java:90`, `core/…/mechanics/Ballistica.java:130-139`, `core/…/mobs/Mob.java:1686-1705`</small>

| Class | Note |
|---|---|
| Warrior | Rush it: its melee is weak against armor, and only the bolt ignores armor. |
| Mage | A wand duel works (wands skip its DR), but you take armor-free bolts back; prefer to break line and ambush. |
| Rogue | Ideal sneak-attack target; invisibility also makes your first strike always hit. |
| Huntress | One of the few gnolls where trading ranged shots is acceptable (it has 35 HP), but each bolt it lands ignores armor and may debuff you; closing is still safer. |
| Duelist | Close with movement or abilities; adjacency removes its bolt. |
| Cleric | Close in; illumination guarantees the finishing hit. |

**Open questions.**

- Its bolt is also used by the Mob FOV rules; at distances beyond its viewDistance the enemy is not in FOV, so the reach of 8 is the default viewDistance, not a hard-coded range.

### Community notes

- **Tier 1.** “If a Gnoll Shaman spots you at range, break line of sight behind cover; retreating behind a door makes it chase into melee range, which is the safer place to finish it off.” [source](https://pixeldungeon.fandom.com/wiki/Gnoll_shaman) (version: old wiki page (flagged oldVersion)). Tactic confirmed: its ranged attack needs a MAGIC_BOLT path that stops on solid cells (walls, closed doors), so breaking line of sight stops it, and a hunting mob that cannot see you walks to your last seen cell. Adjacent it only uses 5-10 melee, which armor reduces. The 'lightning bolt' wording is outdated (see the next entry). <small>`core/…/mobs/Shaman.java:72-76`, `core/…/mechanics/Ballistica.java:130-136`, `core/…/mobs/Mob.java:1342-1356`, `core/…/mobs/Shaman.java:91-108`</small>
- **Tier F.** “<del>Its ranged attack is a lightning bolt.</del>” [source](https://pixeldungeon.fandom.com/wiki/Gnoll_shaman) (version: old wiki page (flagged oldVersion)). In v4.0.0 the ranged attack is an EarthenBolt: 6-15 damage through damage() plus a 50% chance of Weakness, Vulnerable or Hex depending on the shaman's colour. It is not lightning, and ELECTRIC-type resistances do not apply to it. <small>`core/…/mobs/Shaman.java:113-137`, `core/…/mobs/Shaman.java:111`</small>

??? note "Corrected during verification (2)"

    - Hand check: depths '11-15 (40% of Shaman family)' corrected to '11-14 (40% of Shaman family)'; the sewer, prison and caves boss floors create no rotation mobs and add no respawner (SewerBossLevel.java:132-138, PrisonBossLevel.java:597-604, CavesBossLevel.java:207-219).
    - All cited lines re-checked; no factual errors. Added the Ascension scaling note on bolt damage (Shaman.java:126).

## Gnoll shaman (blue) {#shaman-blueshaman}

`actors.mobs.Shaman.BlueShaman` · depths 11-14 (40% of Shaman family) · **Tactic:** Close the distance under cover: use corners and doors so it never has a long clear line, or step in while another monster blocks its line.

**Stats** (from the [Codex](../codex/mobs.md)): HT 35 · accuracy 18 · evasion 15 · damage 5-10 · armour 0-6 · EXP 8 · max level 16 · properties none · loot WAND (3% base). <small>`core/…/mobs/Shaman.java:162`, `core/…/mobs/Shaman.java:63`, `core/…/mobs/Shaman.java:58`, `core/…/mobs/Shaman.java:68`</small>

Depths corrected by hand: the boss floors 5, 10 and 15 create no rotation mobs and run no respawner, so this enemy never spawns there. <small>`core/…/mobs/MobSpawner.java:70-155`, `core/…/levels/SewerBossLevel.java:132-138`, `core/…/levels/PrisonBossLevel.java:597-604`, `core/…/levels/CavesBossLevel.java:207-219`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** baseSpeed 1. Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/mobs/Mob.java:1353-1356`, `core/…/actors/Char.java:175`, `core/…/actors/Char.java:770-783`, `core/…/levels/Level.java:1174`, `core/…/levels/Level.java:1257-1258`, `core/…/levels/Level.java:1214-1215`, `core/…/features/Door.java:35-43`, `core/…/mobs/Mob.java:1686-1705`</small>
- **Attack.** `bolt`, reach 8. Can attack when adjacent (melee) or when a MAGIC_BOLT path from it reaches the target unobstructed: the path stops on the first character and on solid cells, including closed doors and webs. Reach is bounded by its FOV (default 8). Attack time: 1 turn for both melee and bolt.. <small>`core/…/mobs/Shaman.java:72-76`, `core/…/mobs/Shaman.java:91-108`, `core/…/mobs/Shaman.java:113-115`, `core/…/mechanics/Ballistica.java:49`, `core/…/mechanics/Ballistica.java:130-139`, `core/…/actors/Char.java:191`</small>
- **Earthen bolt.** When not adjacent it zaps: magic accuracy (x2), 6-15 damage (scaled by the Ascension modifier) through damage() (hero armor does not reduce it), and 50% on hit to apply Vulnerable. When adjacent it only melees for 5-10, which armor reduces. *Telegraph:* Visible zap animation with a blue bolt (sprite zap, then onZapComplete); no warning a turn ahead. <small>`core/…/mobs/Shaman.java:113-137`, `core/…/actors/Char.java:615-617`, `core/…/mobs/Shaman.java:141-144`, `core/…/sprites/ShamanSprite.java:63-70`, `core/…/sprites/ShamanSprite.java:101`</small>
- **Inflicts Vulnerable.** 20 turns (Vulnerable.DURATION); damage taken after armor x1.33. <small>`core/…/mobs/Shaman.java:167-170`, `core/…/buffs/Vulnerable.java:28`, `core/…/actors/Char.java:494-496`</small>
- **AI.** Starts `sleeping`; flees: never (only Terror/Dread). Default Hunting: it attacks whenever canAttack is true, so it stands still and zaps from wherever it has a clear bolt line. It only walks toward you when the line is blocked. It does not try to keep its distance. <small>`core/…/mobs/Mob.java:124`, `core/…/levels/Level.java:774-776`, `core/…/mobs/Mob.java:1326-1362`</small>
- **Evasion.** defenseSkill 15; evasive: no. Average evasion, 35 HP: the frailest cave gnoll. <small>`core/…/mobs/Shaman.java:46-48`</small>
- **Surprise.** Can be surprised: yes. Standard rule. <small>`core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/actors/Char.java:646-684`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `close`; ranged attacks first: no.
- **Plan.** Close the distance under cover: use corners and doors so it never has a long clear line, or step in while another monster blocks its line. Once adjacent, melee it down (35 HP). If it is asleep, sneak-hit it. With several gnolls around, kill the shaman before the brute when the shaman's debuff multiplies the brute's damage. <small>`core/…/mobs/Shaman.java:91-108`, `core/…/mobs/Shaman.java:113-137`, `core/…/actors/Char.java:627-630`, `core/…/mobs/Mob.java:783-794`, `core/…/actors/Char.java:390`, `core/…/actors/Char.java:483-486`</small>
- **Kill or nullify: Get adjacent. Do not stand at range trading shots..** Adjacent, it switches to plain 5-10 melee that armor reduces, with no debuff. At range it fires double-accuracy 6-15 bolts that ignore armor, with a 50% debuff chance. (derived) <small>`core/…/mobs/Shaman.java:91-108`, `core/…/mobs/Shaman.java:113-137`, `core/…/actors/Char.java:615-617`, `core/…/actors/Char.java:390`, `core/…/actors/Char.java:483-486`</small>
- **Kill or nullify: Approach around corners or through doors so it has to come to you..** The bolt needs a MAGIC_BOLT path that stops on solid cells; out of line it must walk closer (getCloser), which often ends adjacent at the corner. (derived) <small>`core/…/mobs/Shaman.java:72-76`, `core/…/mechanics/Ballistica.java:130-136`, `core/…/mobs/Mob.java:1342-1356`, `core/…/levels/Terrain.java:90`</small>
- **Kill or nullify: Put another body between you and it..** MAGIC_BOLT stops on the first character, so an ally or another mob in the line absorbs or blocks the bolt. (derived) <small>`core/…/mechanics/Ballistica.java:49`, `core/…/mechanics/Ballistica.java:137-139`</small>
- **Kill or nullify: Blue is top priority when a brute, spinner or DM-200 is also on you: Vulnerable adds a third to their post-armor hits for 20 turns..** Debuff priority follows the effect sizes cited under inflicts. (derived) <small>`core/…/mobs/Shaman.java:167-170`, `core/…/buffs/Vulnerable.java:28`, `core/…/actors/Char.java:494-496`</small>
- **Kill or nullify: Sneak-kill it asleep..** 35 HP and 0 evasion when surprised. (derived) <small>`core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/actors/Char.java:646-684`</small>
- **Escape.** Outrunnable: no; contact breaks at: out-of-sight, door, stairs. Same speed. Breaking the bolt line matters more than distance: any wall, closed door or body in the line stops the zap. <small>`core/…/mobs/Mob.java:1342-1350`, `core/…/mobs/Mob.java:1400-1405`, `core/…/levels/Terrain.java:90`, `core/…/mechanics/Ballistica.java:130-139`, `core/…/mobs/Mob.java:1686-1705`</small>

| Class | Note |
|---|---|
| Warrior | Rush it: its melee is weak against armor, and only the bolt ignores armor. |
| Mage | A wand duel works (wands skip its DR), but you take armor-free bolts back; prefer to break line and ambush. |
| Rogue | Ideal sneak-attack target; invisibility also makes your first strike always hit. |
| Huntress | One of the few gnolls where trading ranged shots is acceptable (it has 35 HP), but each bolt it lands ignores armor and may debuff you; closing is still safer. |
| Duelist | Close with movement or abilities; adjacency removes its bolt. |
| Cleric | Close in; illumination guarantees the finishing hit. |

**Open questions.**

- Its bolt is also used by the Mob FOV rules; at distances beyond its viewDistance the enemy is not in FOV, so the reach of 8 is the default viewDistance, not a hard-coded range.

### Community notes

- **Tier 1.** “If a Gnoll Shaman spots you at range, break line of sight behind cover; retreating behind a door makes it chase into melee range, which is the safer place to finish it off.” [source](https://pixeldungeon.fandom.com/wiki/Gnoll_shaman) (version: old wiki page (flagged oldVersion)). Tactic confirmed: its ranged attack needs a MAGIC_BOLT path that stops on solid cells (walls, closed doors), so breaking line of sight stops it, and a hunting mob that cannot see you walks to your last seen cell. Adjacent it only uses 5-10 melee, which armor reduces. The 'lightning bolt' wording is outdated (see the next entry). <small>`core/…/mobs/Shaman.java:72-76`, `core/…/mechanics/Ballistica.java:130-136`, `core/…/mobs/Mob.java:1342-1356`, `core/…/mobs/Shaman.java:91-108`</small>
- **Tier F.** “<del>Its ranged attack is a lightning bolt.</del>” [source](https://pixeldungeon.fandom.com/wiki/Gnoll_shaman) (version: old wiki page (flagged oldVersion)). In v4.0.0 the ranged attack is an EarthenBolt: 6-15 damage through damage() plus a 50% chance of Weakness, Vulnerable or Hex depending on the shaman's colour. It is not lightning, and ELECTRIC-type resistances do not apply to it. <small>`core/…/mobs/Shaman.java:113-137`, `core/…/mobs/Shaman.java:111`</small>

??? note "Corrected during verification (2)"

    - Hand check: depths '11-15 (40% of Shaman family)' corrected to '11-14 (40% of Shaman family)'; the sewer, prison and caves boss floors create no rotation mobs and add no respawner (SewerBossLevel.java:132-138, PrisonBossLevel.java:597-604, CavesBossLevel.java:207-219).
    - All cited lines re-checked; no factual errors. Added the Ascension scaling note on bolt damage (Shaman.java:126).

## Gnoll shaman (purple) {#shaman-purpleshaman}

`actors.mobs.Shaman.PurpleShaman` · depths 11-14 (20% of Shaman family) · **Tactic:** Close the distance under cover: use corners and doors so it never has a long clear line, or step in while another monster blocks its line.

**Stats** (from the [Codex](../codex/mobs.md)): HT 35 · accuracy 18 · evasion 15 · damage 5-10 · armour 0-6 · EXP 8 · max level 16 · properties none · loot WAND (3% base). <small>`core/…/mobs/Shaman.java:173`, `core/…/mobs/Shaman.java:63`, `core/…/mobs/Shaman.java:58`, `core/…/mobs/Shaman.java:68`</small>

Depths corrected by hand: the boss floors 5, 10 and 15 create no rotation mobs and run no respawner, so this enemy never spawns there. <small>`core/…/mobs/MobSpawner.java:70-155`, `core/…/levels/SewerBossLevel.java:132-138`, `core/…/levels/PrisonBossLevel.java:597-604`, `core/…/levels/CavesBossLevel.java:207-219`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** baseSpeed 1. Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/mobs/Mob.java:1353-1356`, `core/…/actors/Char.java:175`, `core/…/actors/Char.java:770-783`, `core/…/levels/Level.java:1174`, `core/…/levels/Level.java:1257-1258`, `core/…/levels/Level.java:1214-1215`, `core/…/features/Door.java:35-43`, `core/…/mobs/Mob.java:1686-1705`</small>
- **Attack.** `bolt`, reach 8. Can attack when adjacent (melee) or when a MAGIC_BOLT path from it reaches the target unobstructed: the path stops on the first character and on solid cells, including closed doors and webs. Reach is bounded by its FOV (default 8). Attack time: 1 turn for both melee and bolt.. <small>`core/…/mobs/Shaman.java:72-76`, `core/…/mobs/Shaman.java:91-108`, `core/…/mobs/Shaman.java:113-115`, `core/…/mechanics/Ballistica.java:49`, `core/…/mechanics/Ballistica.java:130-139`, `core/…/actors/Char.java:191`</small>
- **Earthen bolt.** When not adjacent it zaps: magic accuracy (x2), 6-15 damage (scaled by the Ascension modifier) through damage() (hero armor does not reduce it), and 50% on hit to apply Hex. When adjacent it only melees for 5-10, which armor reduces. *Telegraph:* Visible zap animation with a purple bolt (sprite zap, then onZapComplete); no warning a turn ahead. <small>`core/…/mobs/Shaman.java:113-137`, `core/…/actors/Char.java:615-617`, `core/…/mobs/Shaman.java:141-144`, `core/…/sprites/ShamanSprite.java:63-70`, `core/…/sprites/ShamanSprite.java:112`</small>
- **Inflicts Hex.** 30 turns (Hex.DURATION); your accuracy and evasion rolls x0.8. <small>`core/…/mobs/Shaman.java:178-181`, `core/…/buffs/Hex.java:28`, `core/…/actors/Char.java:648`, `core/…/actors/Char.java:664`</small>
- **AI.** Starts `sleeping`; flees: never (only Terror/Dread). Default Hunting: it attacks whenever canAttack is true, so it stands still and zaps from wherever it has a clear bolt line. It only walks toward you when the line is blocked. It does not try to keep its distance. <small>`core/…/mobs/Mob.java:124`, `core/…/levels/Level.java:774-776`, `core/…/mobs/Mob.java:1326-1362`</small>
- **Evasion.** defenseSkill 15; evasive: no. Average evasion, 35 HP: the frailest cave gnoll. <small>`core/…/mobs/Shaman.java:46-48`</small>
- **Surprise.** Can be surprised: yes. Standard rule. <small>`core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/actors/Char.java:646-684`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `close`; ranged attacks first: no.
- **Plan.** Close the distance under cover: use corners and doors so it never has a long clear line, or step in while another monster blocks its line. Once adjacent, melee it down (35 HP). If it is asleep, sneak-hit it. With several gnolls around, kill the shaman before the brute when the shaman's debuff multiplies the brute's damage. <small>`core/…/mobs/Shaman.java:91-108`, `core/…/mobs/Shaman.java:113-137`, `core/…/actors/Char.java:627-630`, `core/…/mobs/Mob.java:783-794`, `core/…/actors/Char.java:390`, `core/…/actors/Char.java:483-486`</small>
- **Kill or nullify: Get adjacent. Do not stand at range trading shots..** Adjacent, it switches to plain 5-10 melee that armor reduces, with no debuff. At range it fires double-accuracy 6-15 bolts that ignore armor, with a 50% debuff chance. (derived) <small>`core/…/mobs/Shaman.java:91-108`, `core/…/mobs/Shaman.java:113-137`, `core/…/actors/Char.java:615-617`, `core/…/actors/Char.java:390`, `core/…/actors/Char.java:483-486`</small>
- **Kill or nullify: Approach around corners or through doors so it has to come to you..** The bolt needs a MAGIC_BOLT path that stops on solid cells; out of line it must walk closer (getCloser), which often ends adjacent at the corner. (derived) <small>`core/…/mobs/Shaman.java:72-76`, `core/…/mechanics/Ballistica.java:130-136`, `core/…/mobs/Mob.java:1342-1356`, `core/…/levels/Terrain.java:90`</small>
- **Kill or nullify: Put another body between you and it..** MAGIC_BOLT stops on the first character, so an ally or another mob in the line absorbs or blocks the bolt. (derived) <small>`core/…/mechanics/Ballistica.java:49`, `core/…/mechanics/Ballistica.java:137-139`</small>
- **Kill or nullify: Purple: Hex lowers both your accuracy and evasion rolls for 30 turns (the longest debuff), which hurts most against evasive spinners..** Debuff priority follows the effect sizes cited under inflicts. (derived) <small>`core/…/mobs/Shaman.java:178-181`, `core/…/buffs/Hex.java:28`, `core/…/actors/Char.java:648`, `core/…/actors/Char.java:664`</small>
- **Kill or nullify: Sneak-kill it asleep..** 35 HP and 0 evasion when surprised. (derived) <small>`core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/actors/Char.java:646-684`</small>
- **Escape.** Outrunnable: no; contact breaks at: out-of-sight, door, stairs. Same speed. Breaking the bolt line matters more than distance: any wall, closed door or body in the line stops the zap. <small>`core/…/mobs/Mob.java:1342-1350`, `core/…/mobs/Mob.java:1400-1405`, `core/…/levels/Terrain.java:90`, `core/…/mechanics/Ballistica.java:130-139`, `core/…/mobs/Mob.java:1686-1705`</small>

| Class | Note |
|---|---|
| Warrior | Rush it: its melee is weak against armor, and only the bolt ignores armor. |
| Mage | A wand duel works (wands skip its DR), but you take armor-free bolts back; prefer to break line and ambush. |
| Rogue | Ideal sneak-attack target; invisibility also makes your first strike always hit. |
| Huntress | One of the few gnolls where trading ranged shots is acceptable (it has 35 HP), but each bolt it lands ignores armor and may debuff you; closing is still safer. |
| Duelist | Close with movement or abilities; adjacency removes its bolt. |
| Cleric | Close in; illumination guarantees the finishing hit. |

**Open questions.**

- Its bolt is also used by the Mob FOV rules; at distances beyond its viewDistance the enemy is not in FOV, so the reach of 8 is the default viewDistance, not a hard-coded range.

### Community notes

- **Tier 1.** “If a Gnoll Shaman spots you at range, break line of sight behind cover; retreating behind a door makes it chase into melee range, which is the safer place to finish it off.” [source](https://pixeldungeon.fandom.com/wiki/Gnoll_shaman) (version: old wiki page (flagged oldVersion)). Tactic confirmed: its ranged attack needs a MAGIC_BOLT path that stops on solid cells (walls, closed doors), so breaking line of sight stops it, and a hunting mob that cannot see you walks to your last seen cell. Adjacent it only uses 5-10 melee, which armor reduces. The 'lightning bolt' wording is outdated (see the next entry). <small>`core/…/mobs/Shaman.java:72-76`, `core/…/mechanics/Ballistica.java:130-136`, `core/…/mobs/Mob.java:1342-1356`, `core/…/mobs/Shaman.java:91-108`</small>
- **Tier F.** “<del>Its ranged attack is a lightning bolt.</del>” [source](https://pixeldungeon.fandom.com/wiki/Gnoll_shaman) (version: old wiki page (flagged oldVersion)). In v4.0.0 the ranged attack is an EarthenBolt: 6-15 damage through damage() plus a 50% chance of Weakness, Vulnerable or Hex depending on the shaman's colour. It is not lightning, and ELECTRIC-type resistances do not apply to it. <small>`core/…/mobs/Shaman.java:113-137`, `core/…/mobs/Shaman.java:111`</small>

??? note "Corrected during verification (2)"

    - Hand check: depths '11-15 (20% of Shaman family)' corrected to '11-14 (20% of Shaman family)'; the sewer, prison and caves boss floors create no rotation mobs and add no respawner (SewerBossLevel.java:132-138, PrisonBossLevel.java:597-604, CavesBossLevel.java:207-219).
    - All cited lines re-checked; no factual errors. Added the Ascension scaling note on bolt damage (Shaman.java:126).

## Cave spinner {#spinner}

`actors.mobs.Spinner` · depths 12-14 · **Tactic:** Sneak-hit it asleep if possible. Otherwise wait for it at a corridor mouth and fight standing still.

**Stats** (from the [Codex](../codex/mobs.md)): HT 50 · accuracy 22 · evasion 17 · damage 10-20 · armour 0-6 · EXP 9 · max level 17 · properties none · loot MysteryMeat (12.5% base). <small>`core/…/mobs/Spinner.java:41`, `core/…/mobs/Spinner.java:65`, `core/…/mobs/Spinner.java:60`, `core/…/mobs/Spinner.java:70`</small>

Depths corrected by hand: the boss floors 5, 10 and 15 create no rotation mobs and run no respawner, so this enemy never spawns there. <small>`core/…/mobs/MobSpawner.java:70-155`, `core/…/levels/SewerBossLevel.java:132-138`, `core/…/levels/PrisonBossLevel.java:597-604`, `core/…/levels/CavesBossLevel.java:207-219`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** baseSpeed 1. Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/mobs/Mob.java:1353-1356`, `core/…/actors/Char.java:175`, `core/…/actors/Char.java:770-783`, `core/…/levels/Level.java:1174`, `core/…/levels/Level.java:1257-1258`, `core/…/levels/Level.java:1214-1215`, `core/…/features/Door.java:35-43`, `core/…/mobs/Mob.java:1686-1705`</small>
- **Attack.** `melee`, reach 1. Melee adjacent only. Web shots need a STOP_SOLID projectile line to the web cell. Attack time: 1 turn (default).. <small>`core/…/mobs/Mob.java:558-568`, `core/…/mobs/Mob.java:753-757`, `core/…/mobs/Spinner.java:169-176`</small>
- **Poison bite, then retreat.** Each landed bite has a 50% chance to apply Poison for 7-8 turns (scaled by half the Ascension modifier), reset its web cooldown to 0, and switch it to FLEEING. While fleeing it returns to HUNTING as soon as it sees you and you are not poisoned (unless it is terrified). *Telegraph:* Poison icon on you; the spider backs off. <small>`core/…/mobs/Spinner.java:119-132`, `core/…/mobs/Spinner.java:248-256`</small>
- **Web shot.** While hunting or fleeing with the web cooldown &lt;= 0 and you in view, it shoots a web at the cell you are moving into, or at the cell between you and it if you stood still. The web covers that cell and the two cells beside it (each Web blob seeded at 20). The cooldown is 10 turns after a web. It will not web a hero who stands still and whom it is about to attack. *Telegraph:* Zap animation (magic-missile style) toward the web cell; your movement is interrupted when the web lands in view. <small>`core/…/mobs/Spinner.java:136-178`, `core/…/mobs/Spinner.java:180-209`, `core/…/mobs/Spinner.java:227-246`, `core/…/mobs/Spinner.java:258-268`, `core/…/sprites/SpinnerSprite.java:73-78`</small>
- **Inflicts Poison.** Deals (turns left / 3) + 1 each turn: 17 total for 8 turns, 14 for 7 turns. <small>`core/…/mobs/Spinner.java:122-127`, `core/…/buffs/Poison.java:109-124`</small>
- **Inflicts Roots (via Web).** Stepping into a web cell clears it and roots you for 5 turns (Roots.DURATION). Webs make their cells solid, so they also stop thrown weapons, bolts and LARGE mobs, and they burn. <small>`core/…/levels/Level.java:1161-1165`, `core/…/blobs/Web.java:67-70`, `core/…/buffs/Roots.java:29`, `core/…/blobs/Web.java:101-108`, `core/…/mechanics/Ballistica.java:130-136`</small>
- **Immune.** Web <small>`core/…/mobs/Spinner.java:223-225`</small>
- **Resists.** Poison (half damage) <small>`core/…/mobs/Spinner.java:219-221`, `core/…/actors/Char.java:1356-1372`</small>
- **AI.** Starts `sleeping`; flees: After a poisoning bite (50% of landed hits). It returns to HUNTING once it sees you unpoisoned, or when it has nowhere left to run and can see you.. Custom Hunting/Fleeing states that interleave web shots. The web cooldown only ticks down while hunting or fleeing. <small>`core/…/mobs/Mob.java:124`, `core/…/levels/Level.java:774-776`, `core/…/mobs/Spinner.java:94-117`, `core/…/mobs/Spinner.java:227-273`, `core/…/mobs/Mob.java:1457-1486`</small>
- **Evasion.** defenseSkill 17; evasive: no. Highest evasion in the cave's regular pool, with 22 accuracy. At hero level ~12 (attack 21), plain melee lands ~60%; thrown weapons at range (x1.5) land ~73% (derived). <small>`core/…/mobs/Spinner.java:47`, `core/…/actors/Char.java:646-684`, `core/…/missiles/MissileWeapon.java:223-233`</small>
- **Surprise.** Can be surprised: yes. Standard rule; a sleeping spinner is a free guaranteed hit. <small>`core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/actors/Char.java:646-684`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `surprise`; ranged attacks first: yes.
- **Plan.** Sneak-hit it asleep if possible. Otherwise wait for it at a corridor mouth and fight standing still. When it bites, poisons you and backs off, hold position (or step to a cell not in a web line) and pelt it with missiles or wands while it flees. Let it come back to you instead of chasing. Burn any webs between you before advancing. <small>`core/…/missiles/MissileWeapon.java:223-233`, `core/…/mobs/Spinner.java:141-151`, `core/…/mobs/Spinner.java:248-256`, `core/…/hero/Hero.java:533-542`, `core/…/mobs/Mob.java:783-794`, `core/…/mobs/Mob.java:873-877`, `core/…/actors/Char.java:627-630`, `core/…/hero/Hero.java:517-542`</small>
- **Kill or nullify: Do not chase a fleeing spinner..** While fleeing it keeps shooting webs along your movement line (5-turn roots), and it comes back on its own once your poison ends. (derived) <small>`core/…/mobs/Spinner.java:136-178`, `core/…/mobs/Spinner.java:248-256`</small>
- **Kill or nullify: Throw or shoot at it while it flees..** It stays in view while fleeing and missiles get x1.5 accuracy at range against its 17 evasion. The first web comes at once (the bite resets the web cooldown to 0): standing still makes it land on the cell next to you toward the spinner, which keeps your own cell free but then blocks your line, because webs are solid. Sidestep to a fresh line or burn the web, then shoot during its 10-turn web cooldown. (derived) <small>`core/…/mobs/Spinner.java:127`, `core/…/mobs/Spinner.java:146-152`, `core/…/mobs/Spinner.java:198`, `core/…/mechanics/Ballistica.java:130-136`, `core/…/blobs/Web.java:101-108`, `core/…/missiles/MissileWeapon.java:223-233`</small>
- **Kill or nullify: Stand still in melee range instead of stepping in and out..** It will not web a stationary target it can attack, and a moving target gets webbed ahead of its step. (derived) <small>`core/…/mobs/Spinner.java:141-151`</small>
- **Kill or nullify: Burn webs (fire), or walk around them..** Web cells are flammable and fire clears them. Entering one roots you for 5 turns. (derived) <small>`core/…/blobs/Web.java:101-108`, `core/…/blobs/Fire.java:62-65`, `core/…/levels/Level.java:928-938`, `core/…/levels/Level.java:1161-1165`</small>
- **Kill or nullify: Poison and caustic effects are weak against it..** It resists Poison. (derived) <small>`core/…/mobs/Spinner.java:219-221`</small>
- **Kill or nullify: Cure poison early if you can, and be ready for it to come straight back..** Poison deals most of its damage in the first turns (left/3 + 1). The flee state only lasts while you are poisoned: as soon as it sees you unpoisoned it switches back to HUNTING, so curing poison ends its retreat. (derived) <small>`core/…/mobs/Spinner.java:252-255`, `core/…/buffs/Poison.java:112`</small>
- **Kill or nullify: A drunk Potion of Purity lets you walk through its webs for 20 turns..** BlobImmunity includes Web, and Level.occupyCell only roots characters that are not immune to Web. (derived) <small>`core/…/buffs/BlobImmunity.java:77`, `core/…/levels/Level.java:1161-1165`, `core/…/potions/PotionOfPurity.java:95`, `core/…/buffs/BlobImmunity.java:49`, `core/…/actors/Char.java:1381-1383`</small>
- **Kill or nullify: Levitation breaks and prevents web roots..** Levitation detaches Roots, and Roots cannot attach to a flying character. (derived) <small>`core/…/buffs/Levitation.java:46`, `core/…/buffs/Roots.java:37-44`</small>
- **Escape.** Outrunnable: no; contact breaks at: out-of-sight, door, stairs. Same speed, and webs land on the cell you move into, so fleeing in a straight line gets you rooted. Break line of sight quickly around corners or through doors (webs need a clear line), or take the stairs. <small>`core/…/mobs/Spinner.java:148-176`, `core/…/mobs/Mob.java:1342-1350`, `core/…/mobs/Mob.java:1400-1405`, `core/…/levels/Terrain.java:90`, `core/…/mobs/Mob.java:1686-1705`</small>

| Class | Note |
|---|---|
| Warrior | Hold ground and let it come; armor blunts 10-20 bites but not the poison. |
| Mage | Wands of fire both damage it and clear webs; zap during its flee phase. |
| Rogue | Surprise hit while it sleeps; while invisible your hits are surprise attacks (surprisedBy) and always land. |
| Huntress | Best matchup: bow shots at range get x1.5 accuracy against its 17 evasion, and it spends its flee phase in your line of fire. |
| Duelist | The Precise Assault talent gives the regular attack right after a weapon ability x2/x5/infinite accuracy, which beats its 17 evasion. |
| Cleric | Guiding Light zeroes its evasion for your next hit. |

**Open questions.**

- Resolved: the fleeing check reads enemy.buff(Poison.class) == null every turn (Spinner.java:252-253), so any poison cure ends the retreat on its next turn in view.

### Community notes

- **Tier 1.** “A Cave Spinner attacks in melee until it lands a poison hit, then flees; kill it fast before it can poison and flee.” [source](https://pixeldungeon.fandom.com/wiki/Cave_spinner) (version: unspecified). Confirmed with a correction: each landed bite has a 50% chance (not a certainty) to poison, and a poisoning bite switches it to FLEEING. Killing it before that happens avoids the flee-and-web phase. <small>`core/…/mobs/Spinner.java:119-132`, `core/…/mobs/Spinner.java:248-256`</small>
- **Tier F.** “<del>While fleeing it lays a web trail; stepping on its webs roots you for 5-7 turns.</del>” [source](https://pixeldungeon.fandom.com/wiki/Cave_spinner) (version: unspecified). Webs are not a trail behind the spinner: it shoots a 3-cell web at the cell you are moving into (or between you and it if you stood still), with a 10-turn cooldown. Stepping into a web applies Roots for exactly 5 turns (Roots.DURATION), not 5-7. <small>`core/…/mobs/Spinner.java:136-209`, `core/…/buffs/Roots.java:29`, `core/…/blobs/Web.java:67-70`</small>
- **Tier 1.** “Carry a Potion of Levitation to break the Root.” [source](https://pixeldungeon.fandom.com/wiki/Cave_spinner) (version: unspecified). Levitation detaches Roots and Roots cannot attach to a flying character. <small>`core/…/buffs/Levitation.java:46`, `core/…/buffs/Roots.java:37-44`</small>
- **Tier 3 (unverified).** “Carry a Potion of Healing before engaging one at low HP.” [source](https://pixeldungeon.fandom.com/wiki/Cave_spinner) (version: unspecified). Resource advice; nothing in the code to check beyond the damage figures on this card (10-20 bites, 14-17 poison).

??? note "Corrected during verification (8)"

    - Hand check: depths '12-15' corrected to '12-14'; the sewer, prison and caves boss floors create no rotation mobs and add no respawner (SewerBossLevel.java:132-138, PrisonBossLevel.java:597-604, CavesBossLevel.java:207-219).
    - Bestiary build: 'evasive' set to false. Its evasion is the highest of its cohort, but a hero of the level expected at its depth still lands most plain melee hits, which is what the tag means.
    - Poison total corrected from 'about 17' to 14-17 (7 turns: 3+3+2+2+2+1+1; 8 turns: 17), Poison.java:112-116.
    - Counter "throw at it while it flees": rationale rewritten; a stationary hero gets webbed on the adjacent cell toward the spinner, which blocks the shot line (Spinner.java:148-149, Web.java:101-108).
    - Counter 'cure poison early': the old rationale ('it will not re-engage while you are poisoned') argued against curing; rewritten to state the real trade-off.
    - Added counters: Purity (web immunity) and Levitation (removes/prevents Roots).
    - AI: added the default nowhereToRun exit from FLEEING (Mob.java:1477-1486).
    - Class notes: Duelist 'Precise Assault' is a talent, not an ability; Rogue note rephrased (invisibility = surprise attack, not 'loses track').

## DM-200 {#dm200}

`actors.mobs.DM200` · depths 13-14 · **Tactic:** Meet it in a room and stay adjacent: every turn you are adjacent is a turn it cannot vent.

**Stats** (from the [Codex](../codex/mobs.md)): HT 80 · accuracy 20 · evasion 12 · damage 10-25 · armour 0-8 · EXP 9 · max level 17 · properties INORGANIC, LARGE · loot WEAPON,ARMOR (20% base). <small>`core/…/mobs/DM200.java:38`, `core/…/mobs/DM200.java:64`, `core/…/mobs/DM200.java:59`, `core/…/mobs/DM200.java:69`</small>

Depths corrected by hand: the boss floors 5, 10 and 15 create no rotation mobs and run no respawner, so this enemy never spawns there. <small>`core/…/mobs/MobSpawner.java:70-155`, `core/…/levels/SewerBossLevel.java:132-138`, `core/…/levels/PrisonBossLevel.java:597-604`, `core/…/levels/CavesBossLevel.java:207-219`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** baseSpeed 1, but LARGE: it can only enter openSpace cells, so it cannot pass doors or 1-wide corridors. Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: no; follows you on the stairs: no. <small>`core/…/mobs/Mob.java:1353-1356`, `core/…/actors/Char.java:175`, `core/…/actors/Char.java:770-783`, `core/…/mobs/Mob.java:580-582`, `core/…/levels/Level.java:889-905`, `core/…/levels/Terrain.java:90`, `core/…/mobs/DM200.java:52-53`, `core/…/mobs/Mob.java:1686-1705`</small>
- **Attack.** `melee`, reach 1. Melee adjacent. Toxic vent follows a STOP_TARGET path from it to you; canVent requires a non-solid route of length &lt;= distance+1 (goes around obstacles, not through them). Attack time: 1 turn (default); vent costs 1 turn.. <small>`core/…/mobs/Mob.java:558-568`, `core/…/mobs/Mob.java:753-757`, `core/…/mobs/DM200.java:117-138`</small>
- **Toxic vent.** Only when you are in view and NOT attackable (not adjacent). Each hunting turn it has a Random.Int(100/distance)==0 chance to vent (about 5% at distance 5, 10% at distance 10). When it cannot step closer (for example you are in a corridor it cannot fit into) it vents whenever canVent allows: cooldown &lt;= 0 and a non-solid route of length &lt;= distance+1 exists. The vent seeds 20 toxic gas on each cell of the straight path (passing through other characters) and 100 on your cell. 30-turn cooldown, which ticks every turn in any state. *Telegraph:* Zap animation of a toxic-gas speck bolt toward you; no warning a turn ahead. <small>`core/…/mobs/DM200.java:106-110`, `core/…/mobs/DM200.java:112-128`, `core/…/mobs/DM200.java:140-186`, `core/…/sprites/DM200Sprite.java:67-72`, `core/…/mobs/DM200.java:130-138`</small>
- **Inflicts ToxicGas.** Each turn in the gas deals 1 + scalingDepth/5 damage (3-4 in the caves). The cloud spreads and lingers. <small>`core/…/mobs/DM200.java:123-126`, `core/…/blobs/ToxicGas.java:37-56`</small>
- **Immune.** ToxicGas, Poison, Bleeding (INORGANIC), so its own gas never hurts it. <small>`core/…/mobs/DM200.java:52`, `core/…/actors/Char.java:1421-1422`</small>
- **AI.** Starts `sleeping`; flees: never. Custom Hunting: attack if adjacent, else random vent chance, else step closer, else vent if it can, else handleUnreachableTarget (stays hunting and waits while it can see you). <small>`core/…/mobs/Mob.java:124`, `core/…/levels/Level.java:774-776`, `core/…/mobs/DM200.java:140-186`, `core/…/mobs/Mob.java:1387-1407`</small>
- **Evasion.** defenseSkill 12; evasive: no. Low evasion; easy to hit, but 80 HP and 0-8 DR. <small>`core/…/mobs/DM200.java:44`, `core/…/mobs/DM200.java:68-71`</small>
- **Surprise.** Can be surprised: yes. Standard rule. <small>`core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/actors/Char.java:646-684`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `close`; ranged attacks first: no.
- **Plan.** Meet it in a room and stay adjacent: every turn you are adjacent is a turn it cannot vent. It is easy to hit (12 evasion) but has 80 HP, so commit. If the fight goes badly, leave through a door: it cannot follow. Alternatively bait one vent from a doorway, step clear of the cloud and snipe it for up to 30 turns while it cannot vent or reach you. <small>`core/…/mobs/DM200.java:140-186`, `core/…/mobs/DM200.java:119`, `core/…/mobs/Mob.java:580-582`, `core/…/levels/Level.java:889-905`, `core/…/levels/Terrain.java:90`, `core/…/actors/Char.java:390`, `core/…/actors/Char.java:483-486`</small>
- **Kill or nullify: Fight it adjacent in the open..** It never vents while it can melee you; the vent branch runs only when canAttack is false. (derived) <small>`core/…/mobs/DM200.java:144-146`</small>
- **Kill or nullify: Do not retreat into a corridor while it can see you, unless you want to bait the vent..** When it cannot get closer (LARGE vs narrow cells) it vents whenever off cooldown. (derived) <small>`core/…/mobs/DM200.java:166-177`, `core/…/mobs/Mob.java:580-582`, `core/…/levels/Level.java:889-905`, `core/…/levels/Terrain.java:90`</small>
- **Kill or nullify: Bait-and-snipe: from behind a door or corridor, take one vent, step out of the cloud, then shoot it while it is stuck for 30 turns..** Vent cooldown is 30 turns. It cannot enter non-openSpace cells, and with no vent available and you in view it only waits (handleUnreachableTarget). (derived) <small>`core/…/mobs/DM200.java:119`, `core/…/mobs/DM200.java:130-131`, `core/…/mobs/DM200.java:179-182`, `core/…/mobs/Mob.java:1387-1407`, `core/…/mobs/Mob.java:580-582`, `core/…/levels/Level.java:889-905`, `core/…/levels/Terrain.java:90`</small>
- **Kill or nullify: Escape through any door..** Doors are solid, so they are never openSpace; it cannot follow. (derived) <small>`core/…/mobs/Mob.java:580-582`, `core/…/levels/Level.java:889-905`, `core/…/levels/Terrain.java:90`</small>
- **Kill or nullify: Gas, poison and bleed do nothing to it; use physical damage, fire or wands..** INORGANIC immunities. (derived) <small>`core/…/actors/Char.java:1421-1422`</small>
- **Kill or nullify: A drunk Potion of Purity makes its vent harmless for 20 turns..** BlobImmunity includes ToxicGas. (derived) <small>`core/…/buffs/BlobImmunity.java:76`, `core/…/potions/PotionOfPurity.java:95`, `core/…/buffs/BlobImmunity.java:49`, `core/…/actors/Char.java:1381-1383`</small>
- **Escape.** Outrunnable: yes; contact breaks at: door, corridor, out-of-sight, stairs. Same speed on open ground, but any door or 1-wide corridor stops it. Expect one parting vent if it still sees you when it gets stuck. <small>`core/…/mobs/Mob.java:580-582`, `core/…/levels/Level.java:889-905`, `core/…/levels/Terrain.java:90`, `core/…/mobs/DM200.java:166-177`, `core/…/mobs/Mob.java:1686-1705`</small>

| Class | Note |
|---|---|
| Warrior | Straight melee in the open; do not back into corridors. |
| Mage | Same bait-and-zap plan. Its INORGANIC immunities do not cover direct wand damage. |
| Rogue | Sneak hit to open, then stay adjacent. |
| Huntress | Bait the vent from a corridor, step out of the gas, then shoot freely; it cannot enter. |
| Duelist | Stay adjacent and use abilities; do not use abilities that push you out of melee range. |
| Cleric | Stay adjacent; heal outside its gas. |

**Open questions.**

- Whether the toxic cloud from a vent reliably drifts into a corridor the hero retreats down was not simulated; Blob spreading is generic and not audited here.

### Community notes

No community claim about this enemy was found.

??? note "Corrected during verification (3)"

    - Hand check: depths '13-15' corrected to '13-14'; the sewer, prison and caves boss floors create no rotation mobs and add no respawner (SewerBossLevel.java:132-138, PrisonBossLevel.java:597-604, CavesBossLevel.java:207-219).
    - Vent: 'always vents when it cannot step closer' qualified with canVent's cooldown and route conditions; cooldown ticks in every state (DM200.java:106-110).
    - Added counter: Purity (ToxicGas immunity).

## DM-201 {#dm201}

`actors.mobs.DM201` · depths 13-14 (2% alternate of DM200) · **Tactic:** Either avoid it entirely (it cannot move), or walk adjacent while it is awake and targeting you and melee it down with no ranged damage at all.

**Stats** (from the [Codex](../codex/mobs.md)): HT 120 · accuracy 20 · evasion 12 · damage 15-25 · armour 0-8 · EXP 9 · max level 17 · properties IMMOVABLE, INORGANIC, LARGE · loot WEAPON,ARMOR (20% base). <small>`core/…/mobs/DM201.java:35`, `core/…/mobs/DM200.java:64`, `core/…/mobs/DM201.java:48`, `core/…/mobs/DM200.java:69`</small>

Depths corrected by hand: the boss floors 5, 10 and 15 create no rotation mobs and run no respawner, so this enemy never spawns there. <small>`core/…/mobs/MobSpawner.java:70-155`, `core/…/levels/SewerBossLevel.java:132-138`, `core/…/levels/PrisonBossLevel.java:597-604`, `core/…/levels/CavesBossLevel.java:207-219`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** Immovable: getCloser and getFurther always return false, so it never moves. Speed `immobile`; flies: no; amphibious: no; water-bound: no; opens doors: no; follows you on the stairs: no. <small>`core/…/mobs/DM201.java:42`, `core/…/mobs/DM201.java:88-96`, `core/…/mobs/Mob.java:1686-1705`</small>
- **Attack.** `melee`, reach 1. Melee adjacent. The corrosive lob targets your cell whenever you are in its FOV after it has been 'threatened'. Attack time: 1 turn (default); lob costs 1 turn.. <small>`core/…/mobs/Mob.java:558-568`, `core/…/mobs/Mob.java:753-757`, `core/…/mobs/DM201.java:111-128`</small>
- **Corrosive lob when threatened.** It becomes threatened when it takes damage from a non-adjacent Char, or any damage while it has no enemy or its enemy is not adjacent (Corruption excepted). On its next hunting turn with you in view it lobs corrosive gas at strength 8: 15 on your cell and 5 on each non-solid neighbour. This resets threatened. It never vents toxic gas like DM-200 (canVent false). *Telegraph:* Zap animation with a corrosion bolt; no warning a turn ahead. <small>`core/…/mobs/DM201.java:52-63`, `core/…/mobs/DM201.java:70-86`, `core/…/mobs/DM201.java:111-128`, `core/…/sprites/DM201Sprite.java:74-79`</small>
- **Inflicts Corrosion (via CorrosiveGas).** Each turn in the gas sets Corrosion to at least 2 turns at damage &gt;= 8. Corrosion damage rises by 1 per turn up to scalingDepth/2+2 and by 0.5 after that; at depths 13-15 that threshold is 8-9, so it climbs about 0.5 per turn from 8. Leave the cloud immediately. <small>`core/…/mobs/DM201.java:74-79`, `core/…/blobs/CorrosiveGas.java:58`, `core/…/buffs/Corrosion.java:71-74`, `core/…/buffs/Corrosion.java:104-123`</small>
- **Immune.** ToxicGas, Poison, Bleeding (INORGANIC); Vertigo (IMMOVABLE) <small>`core/…/actors/Char.java:1421-1422`, `core/…/actors/Char.java:1433-1434`</small>
- **AI.** Starts `sleeping`; flees: never (cannot move). Hunting: lob if threatened, else default (melee if adjacent, otherwise it cannot close and waits). <small>`core/…/mobs/Mob.java:124`, `core/…/levels/Level.java:774-776`, `core/…/mobs/DM201.java:111-128`</small>
- **Evasion.** defenseSkill 12; evasive: no. Inherits DM-200's low evasion; 120 HP. <small>`core/…/mobs/DM200.java:44`, `core/…/mobs/DM201.java:40`</small>
- **Surprise.** Can be surprised: yes. Standard rule, but a sneak hit on a sleeper (enemy == null) sets threatened, so it lobs gas once it wakes and sees you. <small>`core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/actors/Char.java:646-684`, `core/…/mobs/DM201.java:55-60`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `close`; ranged attacks first: no.
- **Plan.** Either avoid it entirely (it cannot move), or walk adjacent while it is awake and targeting you and melee it down with no ranged damage at all. If you are gassed, step two cells out of the cloud, wait for it to thin, and come back in. <small>`core/…/mobs/DM201.java:52-63`, `core/…/mobs/DM201.java:111-128`</small>
- **Kill or nullify: Never damage it from range..** Any non-adjacent damage source sets threatened, which earns a corrosive cloud on your cell. (derived) <small>`core/…/mobs/DM201.java:55-60`, `core/…/mobs/DM201.java:116-123`</small>
- **Kill or nullify: Walk up to it while it is awake and hunting you, then melee it adjacent..** Damage while its enemy is adjacent does not set threatened, and it cannot move to dodge or chase. (derived) <small>`core/…/mobs/DM201.java:55-60`, `core/…/mobs/DM201.java:88-96`</small>
- **Kill or nullify: If a cloud lands, step out and let it clear before re-engaging..** Corrosion ramps each turn, but Corrosion in the gas lasts only 2 turns once you leave. (derived) <small>`core/…/blobs/CorrosiveGas.java:58`, `core/…/buffs/Corrosion.java:104-116`</small>
- **Kill or nullify: Or just skip it..** It is immovable; walk around its reach and out of its sight. Payoff: DM-200's decaying 20% weapon/armor drop plus a metal shard, but both only while your level is &lt;= 19 (rollToDropLoot returns early above maxLvl + 2). (derived) <small>`core/…/mobs/DM201.java:88-96`, `core/…/mobs/DM201.java:98-109`, `core/…/mobs/DM200.java:47-50`, `core/…/mobs/DM200.java:73-78`</small>
- **Kill or nullify: A drunk Potion of Purity makes its corrosive lob harmless for 20 turns..** BlobImmunity includes CorrosiveGas, and the gas only applies Corrosion to characters not immune to it. (derived) <small>`core/…/buffs/BlobImmunity.java:65`, `core/…/blobs/CorrosiveGas.java:57-58`, `core/…/potions/PotionOfPurity.java:95`, `core/…/buffs/BlobImmunity.java:49`, `core/…/actors/Char.java:1381-1383`</small>
- **Escape.** Outrunnable: yes; contact breaks at: out-of-sight, any-distance. It never moves. Staying out of its FOV also stops the lob (requires enemyInFOV). <small>`core/…/mobs/DM201.java:88-96`, `core/…/mobs/DM201.java:116`</small>

| Class | Note |
|---|---|
| Warrior | Straight melee from adjacency is the clean kill. |
| Mage | Zapping it from range triggers the lob; melee with the staff or skip it. |
| Rogue | A sneak hit on a sleeper still triggers a lob; walk up awake instead, or skip it. |
| Huntress | Do not shoot it; if you must kill it, melee, or skip it. |
| Duelist | Melee abilities from an adjacent cell only. |
| Cleric | Melee from adjacency; do not use ranged damage spells on it. |

**Open questions.**

- Whether hero spells that hit adjacent targets count as 'src instanceof Char' (the hero) or as a spell object depends on each spell; a non-Char source with the enemy adjacent does not set threatened (DM201.java:57-58).

### Community notes

No community claim about this enemy was found.

??? note "Corrected during verification (5)"

    - Hand check: depths '13-15 (2% alternate of DM200)' corrected to '13-14 (2% alternate of DM200)'; the sewer, prison and caves boss floors create no rotation mobs and add no respawner (SewerBossLevel.java:132-138, PrisonBossLevel.java:597-604, CavesBossLevel.java:207-219).
    - Bestiary build: speed 'slow' recorded as 'immobile'; the card's own movement text says it never walks.
    - Loot claim: the metal shard is not guaranteed; DM201.rollToDropLoot returns before the shard when hero level &gt; 19 (DM201.java:100).
    - Corrosion growth: at cave depths the +1 phase is essentially skipped (threshold 8-9 vs starting damage 8), buffs/Corrosion.java:104-111.
    - Added counter: Purity (CorrosiveGas immunity).

## Gnoll guard {#gnollguard}

`actors.mobs.GnollGuard` · depths quest (Blacksmith gnoll mine, branch of 12-14) · **Tactic:** Take out the linked sapper first (the guard takes quarter damage until then), keeping the guard adjacent or out of line so it never gets its 16-22 reach hit.

**Stats** (from the [Codex](../codex/mobs.md)): HT 35 · accuracy 20 · evasion 15 · damage `Random.NormalIntRange( 16, 22 ); \| Random.NormalIntRange( 6, 12 )` · armour 0-6 · EXP 7 · max level -2 · properties none · loot Spear (10% base). <small>`core/…/mobs/GnollGuard.java:35`, `core/…/mobs/GnollGuard.java:101`, `core/…/mobs/GnollGuard.java:83`, `core/…/mobs/GnollGuard.java:106`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** baseSpeed 1. While linked to a sapper it wanders toward the sapper's cell. Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/mobs/Mob.java:1353-1356`, `core/…/actors/Char.java:175`, `core/…/actors/Char.java:770-783`, `core/…/levels/Level.java:1174`, `core/…/levels/Level.java:1257-1258`, `core/…/levels/Level.java:1214-1215`, `core/…/features/Door.java:35-43`, `core/…/mobs/GnollGuard.java:141-150`, `core/…/mobs/Mob.java:1686-1705`</small>
- **Attack.** `melee`, reach 2. Attacks at distance &lt;= 2 only if PROJECTILE lines are clear both ways (no char or solid cell in between). It cannot curve around corners. Attack time: 1 turn (default).. <small>`core/…/mobs/GnollGuard.java:110-116`, `core/…/mechanics/Ballistica.java:47`, `core/…/mobs/Mob.java:753-757`</small>
- **Spear reach.** Hits for 16-22 from 2 cells away but only 6-12 when adjacent. A log warning appears when a reach hit on the hero deals more than 12. *Telegraph:* none before the hit; 'spear_warn' log line after a heavy reach hit. <small>`core/…/mobs/GnollGuard.java:82-98`</small>
- **Sapper armor.** While its linked sapper is alive, all damage to it is divided by 4. *Telegraph:* Earth-armor particle emitter on its sprite and an extra description line. <small>`core/…/mobs/GnollGuard.java:52-80`, `core/…/mobs/GnollGuard.java:118-125`, `core/…/sprites/GnollGuardSprite.java:66-68`</small>
- **AI.** Starts `sleeping`; flees: never. Default AI; linked guards patrol to their sapper. Also the mine's generic respawn mob (unlinked). <small>`core/…/mobs/Mob.java:124`, `core/…/levels/Level.java:774-776`, `core/…/mobs/GnollGuard.java:141-150`, `core/…/levels/MiningLevel.java:158-170`, `core/…/quest/MineLargeRoom.java:128-141`</small>
- **Evasion.** defenseSkill 15; evasive: no. Average evasion. <small>`core/…/mobs/GnollGuard.java:41`</small>
- **Surprise.** Can be surprised: yes. Standard rule (damage still quartered while its sapper lives). <small>`core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/actors/Char.java:646-684`, `core/…/mobs/GnollGuard.java:77-80`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `close`; ranged attacks first: no.
- **Plan.** Take out the linked sapper first (the guard takes quarter damage until then), keeping the guard adjacent or out of line so it never gets its 16-22 reach hit. Use corners so it has to step adjacent. Stand so the guard is between you and the sapper's boulders. When it is paralysed by a rock, kill the sapper or free-hit the guard. <small>`core/…/mobs/GnollGuard.java:77-89`, `core/…/mobs/GnollGuard.java:110-116`, `core/…/missiles/MissileWeapon.java:223-233`</small>
- **Kill or nullify: Get adjacent; never linger at distance 2 in a clear line..** Adjacent it deals 6-12; at reach 2 with clear lines it deals 16-22. (derived) <small>`core/…/mobs/GnollGuard.java:82-89`, `core/…/mobs/GnollGuard.java:110-116`</small>
- **Kill or nullify: Kill its sapper first..** Damage to the guard is quartered while the linked sapper lives. (derived) <small>`core/…/mobs/GnollGuard.java:61-80`</small>
- **Kill or nullify: Line it up with the sapper's boulders or rockfall..** Boulders hit the first character on their path and rockfalls hit anything under them. Guards are paralysed 10 turns (3 for others), and sappers act after guards, which makes this easier to set up. (derived) <small>`core/…/mobs/GnollGeomancer.java:712-747`, `core/…/mobs/GnollGeomancer.java:836-850`, `core/…/mobs/GnollSapper.java:40-41`</small>
- **Kill or nullify: Break its reach with a body or corner..** Its reach needs clear PROJECTILE lines both ways; any character or solid cell in between blocks it. (derived) <small>`core/…/mobs/GnollGuard.java:113-115`, `core/…/mechanics/Ballistica.java:130-139`</small>
- **Escape.** Outrunnable: no; contact breaks at: door, out-of-sight, stairs. Same speed. Retreating at distance 2 in the open is the worst position: it gets reach hits. <small>`core/…/mobs/Mob.java:1353-1356`, `core/…/actors/Char.java:175`, `core/…/actors/Char.java:770-783`, `core/…/mobs/Mob.java:1342-1350`, `core/…/mobs/Mob.java:1400-1405`, `core/…/levels/Terrain.java:90`, `core/…/mobs/GnollGuard.java:110-116`, `core/…/mobs/Mob.java:1686-1705`</small>

| Class | Note |
|---|---|
| Warrior | Stay adjacent; your armor handles 6-12. |
| Mage | Wand the sapper; quartered damage makes the guard a poor wand target while linked. |
| Rogue | Sneak-kill the sapper first to strip the guard's armor. |
| Huntress | Shoot the sapper, not the armored guard; step adjacent to the guard rather than kiting it at distance 2. |
| Duelist | Close in; abilities that reposition you adjacent help. |
| Cleric | Close in; target the sapper first. |

### Community notes

No community claim about this enemy was found.

??? note "Corrected during verification (2)"

    - Stats: EXP and loot are never granted (maxLvl -2; Mob.java:951, 1060).
    - Escape: added the stairs citation for the 'stairs' breakContact entry.

## Gnoll sapper {#gnollsapper}

`actors.mobs.GnollSapper` · depths quest (Blacksmith gnoll mine, branch of 12-14) · **Tactic:** Sneak up while it sleeps and kill it before it acts; its guard then loses its armor.

**Stats** (from the [Codex](../codex/mobs.md)): HT 45 · accuracy 18 · evasion 15 · damage 1-6 · armour 0-6 · EXP 10 · max level -2 · properties MINIBOSS. <small>`core/…/mobs/GnollSapper.java:37`, `core/…/mobs/GnollSapper.java:104`, `core/…/mobs/GnollSapper.java:99`, `core/…/mobs/GnollSapper.java:115`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** baseSpeed 1, but it does not approach an enemy it can see. When it cannot see you it will not chase more than 3 cells from its spawn. It wanders back to its spawn. Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/mobs/GnollSapper.java:150-158`, `core/…/mobs/GnollSapper.java:219-225`, `core/…/mobs/GnollSapper.java:230-235`, `core/…/levels/Level.java:1174`, `core/…/levels/Level.java:1257-1258`, `core/…/levels/Level.java:1214-1215`, `core/…/features/Door.java:35-43`, `core/…/mobs/Mob.java:1686-1705`</small>
- **Attack.** `ranged`, reach 8. Boulder throw: picks the MINE_BOULDER cell in its FOV that has a clear PROJECTILE line to you and is closest to you. The rock then flies as a MAGIC_BOLT and hits the first character on the path. Rockfall: centred on you, radius 2. Melee (1-6) only if you are adjacent. Attack time: Ability wind-up = your action time clamped 1-3 turns; ability cooldown 4-6 turns (reduced by damage taken / 10).. <small>`core/…/mobs/GnollSapper.java:180-225`, `core/…/mobs/GnollGeomancer.java:657-695`, `core/…/mobs/GnollGeomancer.java:700-762`, `core/…/mobs/GnollGeomancer.java:771-834`, `core/…/mobs/GnollSapper.java:108-112`</small>
- **Boulder throw.** The marked line from a boulder to you lands after the delay: 6-12 damage (no armor), 3 turns paralysis on the hero (10 on a gnoll guard), knockback 1. When it has a boulder to throw, it throws (never rockfalls) if you stand next to a barricade or the entrance or if its last ability was a rockfall; otherwise 50/50. With no throwable boulder it always rockfalls. The throw is cancelled if the source boulder is gone when it resolves. *Telegraph:* Targeted-cell markers along the whole flight path; your action is interrupted. <small>`core/…/mobs/GnollSapper.java:190-209`, `core/…/mobs/GnollSapper.java:129-148`, `core/…/mobs/GnollGeomancer.java:700-762`, `core/…/mobs/GnollSapper.java:131-143`</small>
- **Rockfall.** Marks cells within 2 of you (sparser farther out, one guaranteed safe neighbour cell) and drops rocks after the delay: 6-12 damage and 3 turns paralysis. Cells near barricades or the entrance are skipped. Empty cells may turn into new boulders. *Telegraph:* Targeted-cell markers plus falling-dust particles. <small>`core/…/mobs/GnollSapper.java:210-216`, `core/…/mobs/GnollGeomancer.java:771-834`, `core/…/mobs/GnollGeomancer.java:836-862`, `core/…/mobs/DelayedRockFall.java:83-98`</small>
- **Partner link.** Linked guard (or the geomancer) takes quarter damage / is invulnerable while the sapper lives. While it sees you within 3 cells it wakes and aggroes its partner onto you. *Telegraph:* Partner shows the earth-armor emitter. <small>`core/…/mobs/GnollSapper.java:67-96`, `core/…/mobs/GnollSapper.java:162-178`, `core/…/mobs/GnollGuard.java:77-80`, `core/…/mobs/GnollGeomancer.java:133-138`</small>
- **Inflicts Paralysis.** 3 turns on the hero, 10 on gnoll guards, from boulders and rockfall. Damage can break paralysis early (probabilistic). <small>`core/…/mobs/GnollGeomancer.java:736-738`, `core/…/mobs/GnollGeomancer.java:844-846`, `core/…/buffs/Paralysis.java:51-63`</small>
- **Immune.** AllyBuff, Dread (MINIBOSS) <small>`core/…/mobs/GnollSapper.java:51`, `core/…/actors/Char.java:1416-1417`</small>
- **AI.** Starts `sleeping`; flees: never. Custom Hunting: in view it counts down its ability cooldown and does not approach. Out of view it hunts only near its spawn. Acts after guards (actPriority MOB_PRIO-1). <small>`core/…/mobs/GnollSapper.java:40-56`, `core/…/mobs/GnollSapper.java:150-228`</small>
- **Evasion.** defenseSkill 15; evasive: no. Average evasion; weak melee (1-6). <small>`core/…/mobs/GnollSapper.java:46`, `core/…/mobs/GnollSapper.java:98-101`</small>
- **Surprise.** Can be surprised: yes. Standard rule. It starts asleep, so a sneak kill is the cleanest way to strip its partner's armor. <small>`core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/actors/Char.java:646-684`, `core/…/mobs/GnollSapper.java:55`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `surprise`; ranged attacks first: no.
- **Plan.** Sneak up while it sleeps and kill it before it acts; its guard then loses its armor. If it is awake, close to melee immediately, using corners so it has no clear throw. Every time cells are marked, use your action to get off them, ideally so the guard ends up in the rock path. Hug a barricade to rule out rockfall. <small>`core/…/mobs/GnollSapper.java:55`, `core/…/mobs/GnollSapper.java:219-225`, `core/…/mobs/GnollGeomancer.java:657-695`, `core/…/mobs/GnollGeomancer.java:792-804`</small>
- **Kill or nullify: Kill it first, ideally asleep..** It powers its guard's damage reduction, its own melee is weak, and it starts SLEEPING. (derived) <small>`core/…/mobs/GnollSapper.java:55`, `core/…/mobs/GnollSapper.java:98-101`, `core/…/mobs/GnollGuard.java:77-80`</small>
- **Kill or nullify: Rush into melee range..** It will not step toward a visible enemy, and adjacent it has only 1-6 melee between ability cooldowns. (derived) <small>`core/…/mobs/GnollSapper.java:219-225`</small>
- **Kill or nullify: On a marker, spend your action leaving the marked cells..** The delay equals your own action time (1-3), so you always get exactly that action. (derived) <small>`core/…/mobs/GnollSapper.java:200-209`, `core/…/mobs/GnollGeomancer.java:817-823`</small>
- **Kill or nullify: Stand next to a barricade to disable its rockfall..** Rockfall skips cells within 1 of a barricade or the entrance, and when a throw is available it throws instead. (derived) <small>`core/…/mobs/GnollSapper.java:181-194`, `core/…/mobs/GnollGeomancer.java:792-804`</small>
- **Kill or nullify: Remove its ammo: mine or break line to nearby MINE_BOULDER cells; mining the marked source boulder during the wind-up cancels that throw..** Throws need a boulder in its FOV with a clear projectile line to you (it picks the one closest to you). Boulders are SOLID but not LOS_BLOCKING; an adjacent boulder is mined in one action with the pickaxe, and the throw only happens if the source cell is still MINE_BOULDER when it resolves. (derived) <small>`core/…/mobs/GnollGeomancer.java:657-695`, `core/…/levels/Terrain.java:127`, `core/…/hero/Hero.java:1312-1319`, `core/…/hero/Hero.java:1371-1374`, `core/…/mobs/GnollSapper.java:131-143`</small>
- **Kill or nullify: Make its rocks hit the guard..** Boulders strike the first character on the path; guards get 10 turns of paralysis. (derived) <small>`core/…/mobs/GnollGeomancer.java:712-737`</small>
- **Kill or nullify: Kill it with few big hits rather than many small ones..** abilityCooldown is an int reduced by dmg/10f, which Java truncates: while the cooldown is positive each damaging hit removes ceil(dmg/10) turns, so even a 1-damage hit costs you a full turn of its cooldown. (derived) <small>`core/…/mobs/GnollSapper.java:61`, `core/…/mobs/GnollSapper.java:108-112`</small>
- **Kill or nullify: Stand adjacent to it: its rockfall then always drops a rock on its own cell..** Rockfall is centred on you; every non-solid cell within distance 1 of the centre is marked except the one safe cell, and the safe cell is never the source's cell. Only geomancer cells (and, for a geomancer source, sapper cells) are excluded, so the sapper's own cell is always marked, and it stands still (it does not approach or retreat). Step off the marks and it takes 6-12 plus 3 turns of paralysis. (derived) <small>`core/…/mobs/GnollGeomancer.java:774-779`, `core/…/mobs/GnollGeomancer.java:806-811`, `core/…/mobs/GnollGeomancer.java:836-850`, `core/…/mobs/GnollSapper.java:210-225`</small>
- **Escape.** Outrunnable: yes; contact breaks at: out-of-sight, distance&gt;3-from-spawn, stairs. It will not chase more than ~3 cells from its spawn when it cannot see you, and never approaches a seen enemy. Leaving its FOV ends its abilities (they need enemyInFOV). <small>`core/…/mobs/GnollSapper.java:150-158`, `core/…/mobs/GnollSapper.java:219-225`</small>

| Class | Note |
|---|---|
| Warrior | Charge it; its melee is trivial. |
| Mage | Wand it from range; its 45 HP falls fast. |
| Rogue | Priority sneak-attack target: it starts asleep. |
| Huntress | Bow it down from outside its boulder lines (check which boulders see you). |
| Duelist | Close and burst it; abilities shorten the fight and so the number of rock volleys. |
| Cleric | Close and illuminate for guaranteed hits. |

**Open questions.**

- The Blacksmith quest's pickaxe availability was not traced here; mining needs a pickaxe per Hero.actMine (Hero.java:1312-1375).

### Community notes

No community claim about this enemy was found.

??? note "Corrected during verification (6)"

    - Stats: EXP 10 is never granted (maxLvl -2).
    - Boulder throw: 'always throws next to a barricade' and 'never rockfalls twice in a row' hold only while a throw is available (GnollSapper.java:193-216); added throw cancellation when the boulder is gone (131-143).
    - Barricade counter: added the throw-available condition.
    - Ammo counter: added the cancel-by-mining option (GnollSapper.java:133-143, Hero.java:1371-1374).
    - Cooldown counter: 'each 10 damage shortens the cooldown by 1' was wrong; int truncation makes every hit remove ceil(dmg/10) (GnollSapper.java:111).
    - Added counter: adjacent to the sapper, its own rockfall always includes its own cell (GnollGeomancer.java:776-779, 806-811).

## Gnoll geomancer {#gnollgeomancer}

`actors.mobs.GnollGeomancer` · depths quest boss (Blacksmith gnoll mine, branch of 12-14) · **Tactic:** Clear the mine's sapper/guard pairs first where you can, since each living sapper is a future invulnerability shield.

**Stats** (from the [Codex](../codex/mobs.md)): HT 150 · accuracy 20 · evasion 0 · damage 3-6 · armour 0-6 · EXP 20 · max level 29 · properties BOSS, IMMOVABLE. <small>`core/…/mobs/GnollGeomancer.java:66`, `core/…/mobs/GnollGeomancer.java:156`, `core/…/mobs/GnollGeomancer.java:151`, `core/…/mobs/GnollGeomancer.java:161`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** Immovable (getCloser/getFurther false). It relocates only by its carve-and-dash (up to 12 cells toward a sapper spawn) at wake-up and at each HP bracket. Speed `immobile`; flies: no; amphibious: no; water-bound: no; opens doors: no; follows you on the stairs: no. <small>`core/…/mobs/GnollGeomancer.java:86-87`, `core/…/mobs/GnollGeomancer.java:180-188`, `core/…/mobs/GnollGeomancer.java:327-485`</small>
- **Attack.** `ranged`, reach 12. ViewDistance 12; it sees through high grass and smoke screen but not walls. Boulder throws need a MINE_BOULDER in its FOV with a clear PROJECTILE line to you. Rockfall is centred on you. Attack time: Abilities every 3-5 turns (cooldown also drops by damage/10, and by an extra 1 per turn when you are &gt; 2 cells away or its sapper lives, while it has RockArmor and you are not paralysed). Wind-up = your action time clamped 1-3.. <small>`core/…/mobs/GnollGeomancer.java:81-84`, `core/…/levels/Level.java:1327-1350`, `core/…/mobs/GnollGeomancer.java:562-650`, `core/…/mobs/GnollGeomancer.java:279`</small>
- **Rock armor and wake-up.** Spawns asleep with RockArmor 50 and immune to all buffs while asleep. While it has RockArmor it is invulnerable to everything except the Pickaxe, which is used by interacting with it and needs a pickaxe in your inventory; the damage is capped at the remaining armor. While asleep the pickaxe hit is capped at 15. The 1st hit logs a warning; the 3rd wakes it: it carves and dashes, becomes the boss, and aggroes every guard and sapper. Each dash (wake-up and each bracket) sets its ability cooldown to 1, so an ability follows almost at once. *Telegraph:* Log warning on hit 1; 'alert' log line on hit 3; screen shake and rock carving. <small>`core/…/quest/MineGiantRoom.java:125-128`, `core/…/mobs/GnollGeomancer.java:133-148`, `core/…/mobs/GnollGeomancer.java:175-178`, `core/…/mobs/GnollGeomancer.java:192-266`, `core/…/mobs/GnollGeomancer.java:445`</small>
- **HP brackets.** 150 HP in three brackets of 50. Damage cannot skip a bracket. Crossing into a new bracket triggers carve-and-dash plus a fresh RockArmor 25, which must be broken with the pickaxe again. It cannot die until it is in the final bracket. *Telegraph:* Boss health bar bleeds; rock carving and dash. <small>`core/…/mobs/GnollGeomancer.java:268-303`</small>
- **Sapper shield.** Each dash heads for the closest sapper spawn point not yet used (preferring spawns whose sapper is alive within 16 cells), at most 12 cells per dash, so it can dash at most 3 times. If that spawn's sapper is alive it links to it (teleporting the sapper and its guard next to it if they are farther than 3 cells), and it is invulnerable while that sapper lives. *Telegraph:* Earth-armor emitter on its sprite; description notes the sapper. <small>`core/…/mobs/GnollGeomancer.java:133-138`, `core/…/mobs/GnollGeomancer.java:305-325`, `core/…/mobs/GnollGeomancer.java:327-393`, `core/…/mobs/GnollGeomancer.java:448-484`, `core/…/sprites/GnollGeomancerSprite.java:84-86`</small>
- **Boulder volley.** Throws 1/2/3 boulders at once in HP brackets 2/1/0. Each hits the first character on its path for 6-12 (no armor) plus 3 turns paralysis and knockback 1. *Telegraph:* Targeted-cell markers along each flight path. <small>`core/…/mobs/GnollGeomancer.java:606-637`, `core/…/mobs/GnollGeomancer.java:700-762`</small>
- **Rockfall.** Radius 6/4/2 in brackets 0/1/2, around you; 6-12 damage and 3 turns paralysis (10 on guards). Its own cell and sapper cells are never marked. When a throw is available it never rockfalls twice in a row, and throws instead when you are next to a barricade or the entrance; with no throwable boulder it always rockfalls. *Telegraph:* Targeted-cell markers and falling-dust particles. <small>`core/…/mobs/GnollGeomancer.java:594-644`, `core/…/mobs/GnollGeomancer.java:771-862`</small>
- **No melee.** Hunting ends with spend(TICK); it never performs regular attacks. If it cannot see you it waits. *Telegraph:* none <small>`core/…/mobs/GnollGeomancer.java:562-577`, `core/…/mobs/GnollGeomancer.java:646-650`</small>
- **Inflicts Paralysis.** 3 turns on the hero (10 on guards) from each boulder or rock. <small>`core/…/mobs/GnollGeomancer.java:736-738`, `core/…/mobs/GnollGeomancer.java:844-846`</small>
- **Immune.** All buffs/debuffs while SLEEPING (except its own RockArmor/DelayedRockFall). <small>`core/…/mobs/GnollGeomancer.java:140-148`</small>
- **Immune.** All damage except Pickaxe while RockArmor is up; all damage while its sapper lives. <small>`core/…/mobs/GnollGeomancer.java:133-138`</small>
- **Immune.** AllyBuff, Dread (BOSS); Vertigo (IMMOVABLE); its own boulders. <small>`core/…/actors/Char.java:1414-1415`, `core/…/actors/Char.java:1433-1434`, `core/…/mobs/GnollGeomancer.java:729`</small>
- **Resists.** Grim, Grim trap, Scroll of Retribution, Scroll of Psionic Blast (BOSS) <small>`core/…/actors/Char.java:1414`</small>
- **AI.** Starts `sleeping`; flees: never. Custom Sleeping never self-wakes (awaken does nothing, beckon ignored while asleep). Only three pickaxe hits wake it. Custom Hunting: ability or wait. <small>`core/…/mobs/GnollGeomancer.java:545-560`, `core/…/mobs/GnollGeomancer.java:562-653`</small>
- **Evasion.** defenseSkill 0; evasive: no. No defenseSkill set: every attack that is not blocked by invulnerability hits. <small>`core/…/mobs/GnollGeomancer.java:68-88`</small>
- **Surprise.** Can be surprised: yes. The generic Mob.surprisedBy rule applies, but its evasion is already 0, so surprise only adds the Dagger/Dirk/Assassin's Blade/Throwing Knife/Kunai damage bonus, and only after RockArmor and the sapper link are gone. While asleep and armored only the pickaxe interaction hurts it, and that uses its own damage roll. <small>`core/…/mobs/Mob.java:873-877`, `core/…/mobs/GnollGeomancer.java:133-138`, `core/…/mobs/GnollGeomancer.java:192-217`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `chokepoint`; ranged attacks first: no.
- **Plan.** Clear the mine's sapper/guard pairs first where you can, since each living sapper is a future invulnerability shield. Then wake the geomancer with three pickaxe hits. After each dash: kill the linked sapper, pickaxe the new RockArmor, then damage it through the bracket, staying adjacent and dodging marked cells. When a volley is marked, step off the marked path (a guard standing in the path takes the rock instead), or retreat behind solid walls so it loses sight and waits. <small>`core/…/mobs/GnollGeomancer.java:133-138`, `core/…/mobs/GnollGeomancer.java:268-303`, `core/…/mobs/GnollGeomancer.java:580-586`, `core/…/mobs/GnollGeomancer.java:606-644`</small>
- **Kill or nullify: Before waking it, clear sappers you can reach (sneak-kill them)..** Each dash links it to the nearest living sapper and makes it invulnerable while that sapper lives. Dead sappers leave nothing to link. (derived) <small>`core/…/mobs/GnollGeomancer.java:327-363`, `core/…/mobs/GnollGeomancer.java:448-484`, `core/…/mobs/GnollGeomancer.java:133-138`</small>
- **Kill or nullify: Fight it adjacent, and keep the pickaxe for each new RockArmor..** Its ability cooldown drops faster while you are more than 2 cells away (with armor up); RockArmor only yields to the pickaxe. (derived) <small>`core/…/mobs/GnollGeomancer.java:580-586`, `core/…/mobs/GnollGeomancer.java:133-138`, `core/…/mobs/GnollGeomancer.java:192-266`</small>
- **Kill or nullify: Break line of sight behind real walls to reset..** It cannot move and waits when it cannot see you; high grass and smoke do not block its sight. (derived) <small>`core/…/mobs/GnollGeomancer.java:566-576`, `core/…/levels/Level.java:1327-1350`</small>
- **Kill or nullify: Dodge markers with your action; stand beside barricades against rockfall..** The delay equals your action time, and rockfall skips barricade-adjacent cells. (derived) <small>`core/…/mobs/GnollGeomancer.java:817-823`, `core/…/mobs/GnollGeomancer.java:792-804`</small>
- **Kill or nullify: Mine or avoid lines to boulders it can see, especially in the last bracket (3 simultaneous throws)..** Throws need MINE_BOULDER cells in its FOV with clear projectile lines to you. (derived) <small>`core/…/mobs/GnollGeomancer.java:606-637`, `core/…/mobs/GnollGeomancer.java:657-666`, `core/…/hero/Hero.java:1312-1375`</small>
- **Kill or nullify: Do not overkill a bracket..** HP is clamped at the bracket edge; extra damage is wasted. (derived) <small>`core/…/mobs/GnollGeomancer.java:284-288`</small>
- **Kill or nullify: Prefer few big hits over many small ones..** Every damaging hit (pickaxe hits included) cuts its int ability cooldown by ceil(dmg/10) while it is positive. (derived) <small>`core/…/mobs/GnollGeomancer.java:279`, `core/…/mobs/GnollGeomancer.java:592`</small>
- **Kill or nullify: Put a gnoll guard in the path of its boulders or rockfalls..** Boulders hit the first character on the path and its rockfall skips only geomancer and sapper cells; a guard hit is paralysed 10 turns. (derived) <small>`core/…/mobs/GnollGeomancer.java:712-737`, `core/…/mobs/GnollGeomancer.java:806-811`, `core/…/mobs/GnollGeomancer.java:844-846`</small>
- **Escape.** Outrunnable: yes; contact breaks at: out-of-sight, walls. It never walks; step behind walls (not grass) and it stops attacking. Its dashes carve open space, so line of sight changes after each bracket. <small>`core/…/mobs/GnollGeomancer.java:566-576`, `core/…/mobs/GnollGeomancer.java:327-440`</small>

| Class | Note |
|---|---|
| Warrior | Straightforward once armor is off; its 0 evasion means every swing lands. |
| Mage | Wands only after the armor is gone and the sapper is dead; RockArmor blocks all non-pickaxe damage. |
| Rogue | Sneak-kill sappers before waking it; surprise does nothing on the geomancer itself. |
| Huntress | The bow works once RockArmor is gone, but range speeds up its abilities (with armor up) and exposes you to boulder lines; kill linked sappers with arrows. |
| Duelist | Abilities for burst inside each bracket; do not waste damage past a bracket edge. |
| Cleric | Heal between brackets while it waits behind walls. |

**Open questions.**

- Pickaxe damage path (Pickaxe.damageRoll with the geomancer as owner, GnollGeomancer.java:206-208) was not audited in Pickaxe.java.
- Blacksmith quest flow (who supplies the pickaxe) not traced.

### Community notes

No community claim about this enemy was found.

??? note "Corrected during verification (7)"

    - Bestiary build: speed 'slow' recorded as 'immobile'; the card's own movement text says it never walks.
    - Sapper shield: it dashes to the nearest unused sapper SPAWN (preferring living sappers within 16) and links only if that sapper is alive; spawns are consumed, so 3 dashes max (GnollGeomancer.java:329-393).
    - Rockfall: the no-repeat and barricade rules hold only while a throw is available; its rocks skip its own and sapper cells but hit guards.
    - Wake-up: added abilityCooldown = 1 after every dash (GnollGeomancer.java:445).
    - Surprise: canBeSurprised corrected to true (Mob.surprisedBy is final-generic); only surprise-damage weapon bonuses matter since evasion is 0.
    - Added counters: cooldown truncation; guards as rock shields.
    - Tactics: 'move so a boulder or wall is in the line' replaced; the rock path is fixed at throw time from boulder to your cell, so the counter is leaving the marked cells.
