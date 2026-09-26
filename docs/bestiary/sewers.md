# Bestiary: Sewers (depths 1-4)

One card per enemy. Mechanics are Tier 1: read from the pinned code at `v4.0.0` and cited.
Tactics are derived from the mechanics and name the lines they rest on. Community notes are
forum and wiki claims graded against the code: Tier 1 confirmed, Tier F contradicted (struck),
Tier 3 not settled. The [bestiary index](index.md) has the cross-cutting rules, the one-line
tactic for every enemy and the tag vocabulary of `tactics/bestiary.json`.

This region's boss is on the [bosses page](bosses.md): [Goo](bosses.md#goo).

## Enemies

- [Marsupial rat](#rat) (1-3): Low threat. Walk in and melee; if several, back into a 1-wide corridor (one step past the door, not the door cell) so they come one at a time.
- [Albino rat](#albino) (1-3 (1/50 alternate of Rat)): Easy to hit (def 2) but 12 HP and a bleeding bite. Soften it with a throw as it approaches, then melee it down; worth killing for the guaranteed meat.
- [Sewer snake](#snake) (1-3): Never slug it out in the open (20% hit rate). If it's asleep, throw/shoot from 2+ tiles for a guaranteed hit.
- [Gnoll scout](#gnoll) (2-4): Throw or shoot once as it closes, then melee. No reason to keep distance: the gnoll scout has no ranged or magic attack.
- [Gnoll exile](#gnollexile) (2-4 (1/50 alternate of Gnoll)): Decide first: it's the toughest sewer regular (24 HP, 1-10 dmg, acc 15, reach 2) but drops 2-3 items.
- [Sewer crab](#crab) (3-4): The sewer's real damage dealer (1-7, acc 12, fast). Take one ranged shot as it appears, then stand and melee in a corridor/doorway.
- [Hermit crab](#hermitcrab) (3-4 (1/50 alternate of Crab)): Worth killing for the guaranteed armor. Don't plink it with weak hits (DR 2-6).
- [Slime](#slime) (4): A 20 HP sponge that punishes heavy weapons. Use your fastest weapon, DOTs, or thrown weapons; don't spend surprise-damage openers or upgraded heavy hits here.
- [Caustic slime](#causticslime) (4 (1/50 alternate of Slime)): Slime plan plus ooze management: soften with ranged hits, finish in melee standing near water, and step into water after the fight to clear Ooze.
- [Fetid rat](#fetidrat) (quest (Sad Ghost, depth 2)): Engage from range, ideally with wands (no gas). If you must melee, hit once and step back out of the cloud rather than standing in it; stand near water to...
- [Gnoll trickster](#gnolltrickster) (quest (Sad Ghost, depth 3)): Opposite of instinct: don't shoot it from afar, run it down. Approach using cover (doors, corners, other mobs) to cut its line; once adjacent it cannot hurt...
- [Great crab](#greatcrab) (quest (Sad Ghost, depth 4)): Never trade in the open: every hit and zap is blocked while it watches you.
- [Goo](bosses.md#goo) (boss 5): Arrive healthy with healing. Open with a guaranteed ranged hit on the sleeping Goo.

## Rules these cards rely on

Tier 1, cited. Written for this region by the reader who verified its cards; the
[index](index.md#cross-cutting-rules) states the ones every region shares.

- **Accuracy vs evasion.** Char.hit rolls acuRoll=Random.Float(attackSkill) and defRoll=Random.Float(defenseSkill), each scaled by Bless x1.25, Hex x0.8, Daze x0.5 and champion factors; hit if acuRoll&gt;=defRoll. Evasion &gt;= INFINITE_EVASION always misses and beats infinite accuracy; accuracy &gt;= INFINITE_ACCURACY always hits. An invisible attacker that can surprise attack gets infinite accuracy. Derived: P(hit)=A/(2D) if A&lt;=D else 1-D/(2A). <small>`core/…/actors/Char.java:612-613`, `core/…/actors/Char.java:619-685`</small>
- **Hero base accuracy and evasion.** Hero starts with attackSkill 10 and defenseSkill 5, each +1 per level; weapon accuracyFactor multiplies accuracy; armor evasionFactor multiplies evasion; paralysis halves hero evasion. <small>`core/…/hero/Hero.java:219-220`, `core/…/hero/Hero.java:2075-2076`, `core/…/hero/Hero.java:511-565`, `core/…/hero/Hero.java:567-611`</small>
- **Thrown/bow accuracy by range.** Missile weapons (including the Huntress Spirit Bow's arrows, a MissileWeapon subclass) get x1.5 accuracy when the target is not adjacent and x0.5 (+0.25 per Point Blank talent point) when adjacent. <small>`core/…/missiles/MissileWeapon.java:215-233`, `core/…/weapon/SpiritBow.java:293`</small>
- **Surprise attacks.** A mob is surprised by the hero if the hero is invisible, or the mob has not 'seen' the hero (enemySeen false: set false every Sleeping and Wandering tick, and in Hunting set to 'hero in FOV' at the START of the mob's act), or the hero is outside the mob's FOV array — and the hero canSurpriseAttack (false with a Flail or a weapon whose STR requirement the hero does not meet). A surprised mob's defenseSkill is 0, so the attack always hits. Paralysed mobs also have 0 evasion. Dagger, Dirk, Assassin's Blade, Throwing Knife and Kunai roll 75%-to-max damage on surprise; Sucker Punch adds damage. Surprise hits play HIT_STRONG and show the Surprise effect. Every hero melee or thrown attack calls Invisibility.dispel(), which also ends the Cloak of Shadows' stealth, so invisibility buys ONE surprise attack per activation, not a string of them. <small>`core/…/mobs/Mob.java:869-877`, `core/…/mobs/Mob.java:796-802`, `core/…/mobs/Mob.java:814-829`, `core/…/hero/Hero.java:744-752`, `core/…/mobs/Mob.java:1237`, `core/…/mobs/Mob.java:1298`, `core/…/mobs/Mob.java:1327`, `core/…/melee/Dagger.java:62-78`, `core/…/missiles/ThrowingKnife.java:50-66`, `core/…/hero/Talent.java:882-887`, `core/…/hero/Hero.java:481`, `core/…/hero/Hero.java:2368`, `core/…/buffs/Invisibility.java:90-98`, `core/…/artifacts/CloakOfShadows.java:360-366`</small>
- **Waking and noticing.** Sleeping mob with a hostile in view wakes with chance 1/(distance+stealth) per turn; wandering mob notices with chance 1/(distance/2+stealth). Hero stealth is 0 by default (only Obfuscation glyph raises it), so a sleeping mob always wakes the first time it acts with the hero adjacent and in view, and a wandering mob always notices at distance &lt;=2. Hero acts before mobs on the same tick (HERO_PRIO 0 &gt; MOB_PRIO -20). Consequence (derived): a sleeping mob cannot be walked up to and meleed by surprise without stealth/invisibility; the reliable surprise on a sleeper is a ranged/reach attack made from distance &gt;=2 on the hero's turn. Exceptions that the stealth() formula does not show: with the Rogue's Silent Steps talent (tier 2) the hero cannot wake a sleeper from distance &gt;= 4 - talent points (&gt;= 2 at 2 points), and a flying character at distance &gt;= 2 cannot wake one. A mob that does wake spends TIME_TO_WAKE_UP (1 turn) before acting again, so the hero gets one free action, but that action is not a surprise (enemySeen is set true on waking in view). <small>`core/…/mobs/Mob.java:1244-1246`, `core/…/mobs/Mob.java:1227-1232`, `core/…/mobs/Mob.java:1282-1284`, `core/…/actors/Actor.java:50-52`, `core/…/actors/Char.java:1280-1286`, `core/…/mobs/Mob.java:1210-1219`, `core/…/mobs/Mob.java:140`, `core/…/mobs/Mob.java:1248-1261`</small>
- **Taking damage wakes/aggroes.** Any non-invulnerable damage wakes a sleeper (to WANDERING) and sets alerted; damage from Wand, ClericSpell or ArmorAbility aggroes the mob onto the hero directly. A landed hit (defenseProc) aggroes onto the attacker. A missed attack does not aggro. <small>`core/…/mobs/Mob.java:902-925`, `core/…/mobs/Mob.java:834-841`, `core/…/actors/Char.java:583-604`</small>
- **Wands and spells ignore evasion and armor.** Offensive wands such as Magic Missile call ch.damage() directly: no hit roll and no drRoll; armor DR is only applied inside Char.attack. <small>`core/…/wands/WandOfMagicMissile.java:56-62`, `core/…/actors/Char.java:390`, `core/…/actors/Char.java:486`</small>
- **Speed and attack delay.** A mob's move costs 1/speed() time; speed = baseSpeed (1 default) modified by Cripple /2, Haste x3 etc. Mob attacks cost attackDelay()=1 (Adrenaline /1.5). The hero's base speed is also 1. <small>`core/…/mobs/Mob.java:1355`, `core/…/mobs/Mob.java:1302`, `core/…/actors/Char.java:175`, `core/…/actors/Char.java:770-783`, `core/…/mobs/Mob.java:753-757`, `core/…/mobs/Mob.java:774-779`, `core/…/hero/Hero.java:714-739`</small>
- **Melee reach.** Default mob canAttack is adjacency only (Chebyshev distance 1, diagonals included); champion buffs can add reach. <small>`core/…/mobs/Mob.java:558-568`</small>
- **Chokepoints (adjacency geometry).** Adjacency is Chebyshev distance 1 over all 8 neighbours, and pathing and attacks have no corner-cutting restriction. A hero standing IN a door cell can therefore be attacked from the three room-side cells (straight and both diagonals). The real one-attacker-at-a-time spot is a 1-wide corridor tile, for example one step back from the door, where only the cell in front (and the one behind) touch you. <small>`core/…/levels/Level.java:1533-1543`, `SPD-classes/…/utils/PathFinder.java:67-70`, `core/…/mobs/Mob.java:558-568`</small>
- **Ballistica line of fire.** PROJECTILE = STOP_TARGET\|STOP_CHARS\|STOP_SOLID: thrown items and projectile mobs stop at the first character or solid cell. MAGIC_BOLT = STOP_CHARS\|STOP_SOLID. Closed doors are SOLID and LOS_BLOCKING; open doors are not. <small>`core/…/mechanics/Ballistica.java:42-51`, `core/…/items/Item.java:633-635`, `core/…/levels/Terrain.java:90-91`</small>
- **Doors.** Any character stepping on a closed door opens it (pressCell/occupyCell); the door closes again when the last character leaves and no item lies on it. A closed door blocks line of sight, so standing beside a closed door hides you from a mob on the other side. <small>`core/…/levels/Terrain.java:90-91`, `core/…/levels/Level.java:1212`, `core/…/levels/Level.java:1257-1258`, `core/…/features/Door.java:35-59`, `core/…/actors/Char.java:1311-1313`</small>
- **Breaking pursuit.** A hunting mob that loses sight keeps walking to the hero's last seen cell; when it cannot get closer and still cannot see the hero it shows the 'lost' mark and goes WANDERING. Mobs only flee under Terror/Dread. <small>`core/…/mobs/Mob.java:1327`, `core/…/mobs/Mob.java:1342-1360`, `core/…/mobs/Mob.java:1400-1405`, `core/…/mobs/Mob.java:284-286`, `core/…/mobs/Mob.java:1437-1486`</small>
- **Door ambush (derived).** Because enemySeen is fixed at the start of a hunting mob's act, a mob that steps through a door into view of a hero it could not see is still 'unseeing' until its next act; the hero, acting next, gets a guaranteed surprise hit. This is exactly the in-game Snake hint. It fails if the mob gets another act before the hero: a speed-2 mob (Crab) acts again 0.5 turns after stepping in, so the ambush only works when that step landed on a half-turn (the hero acts first on ties), roughly a coin flip the player cannot see. <small>`core/…/mobs/Mob.java:1327`, `core/…/mobs/Mob.java:873-877`, `core/…/actors/Char.java:198-203`, `core/…/actors/actors.properties:1799`, `core/…/actors/Actor.java:50-52`</small>
- **Stairs.** Only allies are carried between floors; hostile mobs never follow the hero through stairs. Stairs cannot be used while the level is locked (boss fight). <small>`core/…/mobs/Mob.java:1686-1705`, `core/…/hero/Hero.java:1438`</small>
- **Spawn state.** Mobs placed at level creation keep the default SLEEPING state unless their class sets another; mobs respawned later start WANDERING (PASSIVE mobs stay passive). Rare alternates (Albino, Gnoll Exile, Hermit Crab, Caustic Slime) replace their base mob with chance 1/50 (Rat Skull multiplies). <small>`core/…/mobs/Mob.java:124`, `core/…/levels/Level.java:774-776`, `core/…/mobs/MobSpawner.java:245-262`</small>
- **Water.** Standing in water (not flying) removes Ooze and Burning from a character after the debuff has acted once. <small>`core/…/buffs/Ooze.java:93-94`, `core/…/buffs/Ooze.java:118-120`, `core/…/buffs/Burning.java:99-100`, `core/…/buffs/Burning.java:178-181`</small>
- **Properties.** ACIDIC: resists Corrosion, immune to Ooze. BOSS: resists Grim/GrimTrap/Retribution/Psionic Blast, immune to AllyBuff and Dread. MINIBOSS: immune to AllyBuff and Dread. DEMONIC: takes bonus damage from Prismatic Light (x1.333), Smite (max roll), Sunray, Holy Dart. UNDEAD takes the same holy bonuses. Availability in the sewers: Smite is the Paladin's spell and subclasses come from Tengu's Mask (depth 10); Sunray is a tier-2 Cleric talent (hero level 7+). Realistic holy sources before Goo are the Wand of Prismatic Light and Holy Darts. <small>`core/…/actors/Char.java:1413-1453`, `core/…/wands/WandOfPrismaticLight.java:96-100`, `core/…/spells/Smite.java:120-127`, `core/…/spells/Sunray.java:100-105`, `core/…/darts/HolyDart.java:64-67`, `core/…/spells/ClericSpell.java:121-146`, `core/…/items/TengusMask.java:73`, `core/…/items/TengusMask.java:102`, `core/…/hero/Talent.java:435-436`, `core/…/hero/Talent.java:1022-1024`</small>

??? note "Rules corrected during verification (4)"

    - Waking rule: added the Silent Steps and flying exceptions (the card cited Mob.java:1210-1219 without describing it) and the 1-turn wake-up delay.
    - Surprise rule: added that attacking dispels invisibility and the cloak (one surprise per activation).
    - Properties rule: added UNDEAD and noted Smite/Sunray are not available in the sewers.
    - New rule: door cells expose 3 room-side neighbours (8-way Chebyshev adjacency); a corridor tile is the true chokepoint.

## Marsupial rat {#rat}

`actors.mobs.Rat` · depths 1-3 · **Tactic:** Low threat. Walk in and melee; if several, back into a 1-wide corridor (one step past the door, not the door cell) so they come one at a time.

**Stats** (from the [Codex](../codex/mobs.md)): HT 8 · accuracy 8 · evasion 2 · damage 1-4 · armour 0-1 · EXP 1 · max level 5 · properties none. <small>`core/…/mobs/Rat.java:31`, `core/…/mobs/Rat.java:60`, `core/…/mobs/Rat.java:55`, `core/…/mobs/Rat.java:65`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** baseSpeed 1 (Char default); 1 tile per turn, same as the hero. Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/mobs/Mob.java:1355`, `core/…/mobs/Mob.java:1302`, `core/…/actors/Char.java:175`, `core/…/actors/Char.java:770-783`, `core/…/levels/Terrain.java:90-91`, `core/…/levels/Level.java:1212`, `core/…/levels/Level.java:1257-1258`, `core/…/features/Door.java:35-59`, `core/…/actors/Char.java:1311-1313`, `core/…/mobs/Mob.java:1686-1705`, `core/…/hero/Hero.java:1438`</small>
- **Attack.** `melee`, reach 1. Adjacent only (default Mob.canAttack). Attack time: 1 turn. <small>`core/…/mobs/Mob.java:558-568`, `core/…/mobs/Mob.java:753-757`, `core/…/mobs/Mob.java:774-779`</small>
- **Ratmogrify neutrality.** If the hero's armor ability is Ratmogrify and the rat is in hero view, it turns NEUTRAL and drops the hero as target. *Telegraph:* none <small>`core/…/mobs/Rat.java:43-51`</small>
- **AI.** Starts `sleeping`; flees: never (only Terror/Dread). Standard Mob AI; stats per codex (HT 8, acc 8, dmg 1-4, def 2, DR 0-1). <small>`core/…/mobs/Mob.java:124`, `core/…/levels/Level.java:774-776`, `core/…/mobs/Rat.java:33-67`</small>
- **Evasion.** defenseSkill 2; evasive: no. Hit chance for an accuracy roll A vs evasion D (both uniform) is A/(2D) when A&lt;=D, else 1-D/(2A) (derived from core/…/actors/Char.java:646-684). Hero lvl1 (acc 10) vs def 2: ~90% to hit. <small>`core/…/mobs/Rat.java:37`, `core/…/actors/Char.java:619-685`</small>
- **Surprise.** Can be surprised: yes. No override of surprisedBy/defenseSkill. <small>`core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/mobs/Mob.java:1237`, `core/…/mobs/Mob.java:1298`, `core/…/mobs/Mob.java:1327`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `close`; ranged attacks first: no.
- **Plan.** Low threat. Walk in and melee; if several, back into a 1-wide corridor (one step past the door, not the door cell) so they come one at a time. Don't waste thrown ammo or wand charges. <small>`core/…/mobs/Rat.java:36-67`, `core/…/mobs/Mob.java:558-568`, `core/…/melee/Gloves.java:37`, `core/…/levels/Level.java:1533-1543`, `SPD-classes/…/utils/PathFinder.java:67-70`</small>
- **Kill or nullify: Just melee it.** Evasion 2 and 8 HP; any starting weapon hits ~90% and kills in 2-3 hits. (derived) <small>`core/…/mobs/Rat.java:36-37`, `core/…/actors/Char.java:619-685`</small>
- **Kill or nullify: Fight groups from a 1-wide corridor tile.** Rats only attack adjacent, so in a 1-wide corridor only one can reach you from the front. Do not stand IN the door cell: diagonals count, so three room-side rats can bite you there. (derived) <small>`core/…/levels/Level.java:1533-1543`, `SPD-classes/…/utils/PathFinder.java:67-70`, `core/…/mobs/Mob.java:558-568`</small>
- **Escape.** Outrunnable: no; contact breaks at: door, out-of-sight, stairs. Same speed as the hero: you cannot gain distance, but closing a door behind you breaks sight and it gives up at your last seen cell; it never follows via stairs. <small>`core/…/mobs/Mob.java:1355`, `core/…/mobs/Mob.java:1302`, `core/…/actors/Char.java:175`, `core/…/actors/Char.java:770-783`, `core/…/mobs/Mob.java:1327`, `core/…/mobs/Mob.java:1342-1360`, `core/…/mobs/Mob.java:1400-1405`, `core/…/levels/Terrain.java:90-91`, `core/…/levels/Level.java:1212`, `core/…/levels/Level.java:1257-1258`, `core/…/features/Door.java:35-59`, `core/…/actors/Char.java:1311-1313`, `core/…/mobs/Mob.java:1686-1705`, `core/…/hero/Hero.java:1438`</small>

| Class | Note |
|---|---|
| Warrior | Shortsword melee is enough. |
| Mage | Staff melee; save charges. |
| Rogue | Dagger melee. |
| Huntress | Gloves (0.5 attack delay) shred rats; save arrows. |
| Duelist | Rapier melee. |
| Cleric | Cudgel melee; no spell needed. |

### Community notes

- **Tier 1.** “Don't waste resources on a lone Marsupial Rat unless threatened; save consumables for deeper floors. They're only dangerous in numbers, so if a group is chasing you, retreat to a doorway/corridor and kill them one at a time.” [source](https://pixeldungeon.fandom.com/wiki/Marsupial_rat) (version: old wiki page (flagged oldVersion)). Mechanics confirmed: 8 HP, 1-4 damage, adjacent-only attack, so a corridor limits attackers. Correction: the door cell itself is not a one-at-a-time spot (3 room-side neighbours); use a corridor tile. 'Save consumables' is judgment, not code. <small>`core/…/mobs/Rat.java:36-62`, `core/…/mobs/Mob.java:558-568`, `core/…/levels/Level.java:1533-1543`</small>

??? note "Corrected during verification (1)"

    - Chokepoint counter: 'corridor or doorway' corrected; the door cell has 3 room-side neighbours.

## Albino rat {#albino}

`actors.mobs.Albino` · depths 1-3 (1/50 alternate of Rat) · **Tactic:** Easy to hit (def 2) but 12 HP and a bleeding bite. Soften it with a throw as it approaches, then melee it down; worth killing for the guaranteed meat.

**Stats** (from the [Codex](../codex/mobs.md)): HT 12 · accuracy 8 · evasion 2 · damage 1-4 · armour 0-1 · EXP 2 · max level 5 · properties none · loot MysteryMeat (100% base). <small>`core/…/mobs/Albino.java:31`, `core/…/mobs/Rat.java:60`, `core/…/mobs/Rat.java:55`, `core/…/mobs/Rat.java:65`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** Inherits Rat, baseSpeed 1. Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/mobs/Mob.java:1355`, `core/…/mobs/Mob.java:1302`, `core/…/actors/Char.java:175`, `core/…/actors/Char.java:770-783`, `core/…/levels/Terrain.java:90-91`, `core/…/levels/Level.java:1212`, `core/…/levels/Level.java:1257-1258`, `core/…/features/Door.java:35-59`, `core/…/actors/Char.java:1311-1313`, `core/…/mobs/Mob.java:1686-1705`, `core/…/hero/Hero.java:1438`</small>
- **Attack.** `melee`, reach 1. Adjacent only. Attack time: 1 turn. <small>`core/…/mobs/Mob.java:558-568`, `core/…/mobs/Mob.java:753-757`, `core/…/mobs/Mob.java:774-779`</small>
- **Ratmogrify neutrality.** Inherited from Rat. *Telegraph:* none <small>`core/…/mobs/Rat.java:43-51`</small>
- **Inflicts Bleeding.** On a damaging hit, 50% chance to apply Bleeding.set(NormalFloat(2,3)); set() itself re-rolls the level to NormalFloat(x/2, x), so a fresh bleed is about 1-3. It does not stack (only a higher roll replaces it). Each tick it deals round(level), then the level is re-rolled to NormalFloat(level/2, level) and the bleed ends when that rounds to 0. Water does not cure it; a Potion of Healing does. <small>`core/…/mobs/Albino.java:44-50`, `core/…/buffs/Bleeding.java:71-86`, `core/…/buffs/Bleeding.java:103-135`, `core/…/potions/PotionOfHealing.java:76-81`</small>
- **AI.** Starts `sleeping`; flees: never. Rat AI; HT 12, EXP 2, always drops Mystery Meat. <small>`core/…/mobs/Mob.java:124`, `core/…/levels/Level.java:774-776`, `core/…/mobs/Albino.java:33-40`</small>
- **Evasion.** defenseSkill 2; evasive: no. Inherits Rat defense 2. <small>`core/…/mobs/Rat.java:37`</small>
- **Surprise.** Can be surprised: yes. No override. <small>`core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/mobs/Mob.java:1237`, `core/…/mobs/Mob.java:1298`, `core/…/mobs/Mob.java:1327`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `close`; ranged attacks first: yes.
- **Plan.** Easy to hit (def 2) but 12 HP and a bleeding bite. Soften it with a throw as it approaches, then melee it down; worth killing for the guaranteed meat. <small>`core/…/mobs/Albino.java:36-50`, `core/…/missiles/MissileWeapon.java:215-233`, `core/…/items/Item.java:633-635`, `core/…/mechanics/Ballistica.java:42-51`, `core/…/hero/HeroClass.java:175-182`</small>
- **Kill or nullify: Kill it fast, ranged first if possible.** Every hit it lands may add a bleed that keeps ticking; fewer hits taken = fewer bleeds. (derived) <small>`core/…/mobs/Albino.java:44-50`</small>
- **Kill or nullify: Heal after the fight, not during.** Bleed re-rolls downward every tick and ends on its own within a few turns; water does not remove it (a Potion of Healing does, but a rat bleed is not worth one). (derived) <small>`core/…/buffs/Bleeding.java:132-135`, `core/…/potions/PotionOfHealing.java:76-81`</small>
- **Escape.** Outrunnable: no; contact breaks at: door, out-of-sight, stairs. As Rat. <small>`core/…/mobs/Mob.java:1355`, `core/…/mobs/Mob.java:1302`, `core/…/actors/Char.java:175`, `core/…/actors/Char.java:770-783`, `core/…/mobs/Mob.java:1327`, `core/…/mobs/Mob.java:1342-1360`, `core/…/mobs/Mob.java:1400-1405`, `core/…/mobs/Mob.java:1686-1705`, `core/…/hero/Hero.java:1438`</small>

| Class | Note |
|---|---|
| Warrior | Throwing stones on approach, then shortsword. |
| Mage | One magic missile then staff. |
| Rogue | Throwing knife then dagger. |
| Huntress | One or two arrows then gloves. |
| Duelist | Throwing spike then rapier. |
| Cleric | Cudgel; Guiding Light optional. |

### Community notes

- **Tier 1.** “Albino Rats (a rare Marsupial Rat variant) have considerably more HP than normal rats and inflict Bleeding, so treat them as tougher than a normal rat encounter.” [source](https://pixeldungeon.fandom.com/wiki/Marsupial_rat) (version: old wiki page (flagged oldVersion)). Confirmed: HT 12 vs the rat's 8, and a 50% bleed on damaging hits. Same speed, evasion and damage as a rat otherwise. <small>`core/…/mobs/Albino.java:36`, `core/…/mobs/Rat.java:36`, `core/…/mobs/Albino.java:44-50`</small>

??? note "Corrected during verification (1)"

    - Bleeding: set() re-rolls the applied level (NormalFloat(x/2,x)), so the start is ~1-3, not 2-3; it does not stack; the per-tick decay is a re-roll to NormalFloat(level/2, level), not a subtraction; added the Healing cure.

## Sewer snake {#snake}

`actors.mobs.Snake` · depths 1-3 · **Tactic:** Never slug it out in the open (20% hit rate). If it's asleep, throw/shoot from 2+ tiles for a guaranteed hit.

**Stats** (from the [Codex](../codex/mobs.md)): HT 4 · accuracy 10 · evasion 25 · damage 1-4 · armour 0 (no override: only Barkskin adds to it) · EXP 2 · max level 7 · properties none · loot SEED (25% base). <small>`core/…/mobs/Snake.java:33`, `core/…/mobs/Snake.java:54`, `core/…/mobs/Snake.java:49`, `core/…/actors/Char.java:701`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** baseSpeed 1. Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/mobs/Mob.java:1355`, `core/…/mobs/Mob.java:1302`, `core/…/actors/Char.java:175`, `core/…/actors/Char.java:770-783`, `core/…/levels/Terrain.java:90-91`, `core/…/levels/Level.java:1212`, `core/…/levels/Level.java:1257-1258`, `core/…/features/Door.java:35-59`, `core/…/actors/Char.java:1311-1313`, `core/…/mobs/Mob.java:1686-1705`, `core/…/hero/Hero.java:1438`</small>
- **Attack.** `melee`, reach 1. Adjacent only. Attack time: 1 turn. <small>`core/…/mobs/Mob.java:558-568`, `core/…/mobs/Mob.java:753-757`, `core/…/mobs/Mob.java:774-779`</small>
- **Extreme evasion.** defenseSkill 25 with only 4 HP. Each dodge seen by the hero counts toward flashing the Adventurer's Guide page on surprise attacks. *Telegraph:* Dodge text. The guide button flashes the surprise-attack page after 2 dodges seen by the hero if that page is unread, or after 4 if the first boss has not been slain yet. <small>`core/…/mobs/Snake.java:38-39`, `core/…/mobs/Snake.java:58-71`</small>
- **AI.** Starts `sleeping`; flees: never. Standard AI. HT 4, acc 10, dmg 1-4, EXP 2, 25% seed drop. <small>`core/…/mobs/Mob.java:124`, `core/…/levels/Level.java:774-776`, `core/…/mobs/Snake.java:35-56`</small>
- **Evasion.** defenseSkill 25; evasive: yes. Hit chance for an accuracy roll A vs evasion D (both uniform) is A/(2D) when A&lt;=D, else 1-D/(2A) (derived from core/…/actors/Char.java:646-684). Hero lvl1 melee (acc 10) vs 25: 20% per swing; thrown at range (acc 15): 30%; surprised: 100% (defense 0); wand: always. <small>`core/…/mobs/Snake.java:39`, `core/…/actors/Char.java:619-685`, `core/…/hero/Hero.java:219-220`, `core/…/hero/Hero.java:2075-2076`, `core/…/hero/Hero.java:561`, `core/…/missiles/MissileWeapon.java:215-233`, `core/…/items/Item.java:633-635`, `core/…/mechanics/Ballistica.java:42-51`</small>
- **Surprise.** Can be surprised: yes. Surprise sets its evasion to 0: the whole counter. The game's own description tells you to let it chase you through a doorway and strike just after it steps into the door. <small>`core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/mobs/Mob.java:1237`, `core/…/mobs/Mob.java:1298`, `core/…/mobs/Mob.java:1327`, `core/…/actors/actors.properties:1799`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `surprise`; ranged attacks first: yes.
- **Plan.** Never slug it out in the open (20% hit rate). If it's asleep, throw/shoot from 2+ tiles for a guaranteed hit. If it's hunting you, retreat through a door and hit it the moment it steps into the doorway. Otherwise use a wand/spell. Its hits are weak (1-4), so if cornered in the open, trading is survivable, just slow. <small>`core/…/hero/HeroClass.java:175-182`, `core/…/hero/HeroClass.java:193`, `core/…/hero/HeroClass.java:205-211`, `core/…/hero/HeroClass.java:223-224`, `core/…/hero/HeroClass.java:235-239`, `core/…/hero/HeroClass.java:250-253`, `core/…/spells/ClericSpell.java:109`, `core/…/melee/Dagger.java:62-78`, `core/…/missiles/ThrowingKnife.java:50-66`, `core/…/artifacts/CloakOfShadows.java:306`, `core/…/actors/Char.java:627-630`, `core/…/mobs/Mob.java:875`, `core/…/melee/Rapier.java:92-151`, `core/…/mobs/Mob.java:783-793`, `core/…/spells/GuidingLight.java:86-91`, `core/…/wands/WandOfMagicMissile.java:56-62`, `core/…/actors/Char.java:390`, `core/…/actors/Char.java:486`, `core/…/hero/Hero.java:481`, `core/…/hero/Hero.java:2368`, `core/…/buffs/Invisibility.java:90-98`, `core/…/artifacts/CloakOfShadows.java:360-366`</small>
- **Kill or nullify: Door ambush.** Stand beside a closed door out of its sight; when it steps through, it has not 'seen' you until its next act, so your hit lands at 100% and 4 HP dies to almost anything. (derived) <small>`core/…/mobs/Mob.java:1327`, `core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/actors/actors.properties:1799`</small>
- **Kill or nullify: Snipe sleeping snakes from range.** A sleeper has enemySeen false: a thrown weapon/arrow from distance &gt;=2 always hits. Walking adjacent first wakes it for sure. (derived) <small>`core/…/mobs/Mob.java:1237`, `core/…/mobs/Mob.java:1244-1246`, `core/…/mobs/Mob.java:1227-1232`, `core/…/mobs/Mob.java:1282-1284`, `core/…/actors/Actor.java:50-52`, `core/…/actors/Char.java:1280-1286`, `core/…/missiles/MissileWeapon.java:215-233`, `core/…/items/Item.java:633-635`, `core/…/mechanics/Ballistica.java:42-51`</small>
- **Kill or nullify: Wands / Guiding Light.** Direct wand damage has no hit roll; Guiding Light deals damage without a roll and Illuminated drops its evasion to 0 against the Cleric's next weapon hit. (derived) <small>`core/…/wands/WandOfMagicMissile.java:56-62`, `core/…/actors/Char.java:390`, `core/…/actors/Char.java:486`, `core/…/mobs/Mob.java:783-793`, `core/…/spells/GuidingLight.java:86-91`</small>
- **Kill or nullify: Thrown weapons beat melee if you must trade.** Missile accuracy x1.5 when not adjacent (30% vs 20% at hero lvl1); never throw adjacent (x0.5). (derived) <small>`core/…/missiles/MissileWeapon.java:215-233`, `core/…/items/Item.java:633-635`, `core/…/mechanics/Ballistica.java:42-51`</small>
- **Kill or nullify: Accuracy buffs.** Bless x1.25 acc; Hex/Daze on the snake cut its defense roll. (derived) <small>`core/…/actors/Char.java:646-669`</small>
- **Escape.** Outrunnable: no; contact breaks at: door, out-of-sight, stairs. Same speed; breaking sight through a door also sets up the ambush. <small>`core/…/mobs/Mob.java:1355`, `core/…/mobs/Mob.java:1302`, `core/…/actors/Char.java:175`, `core/…/actors/Char.java:770-783`, `core/…/mobs/Mob.java:1327`, `core/…/mobs/Mob.java:1342-1360`, `core/…/mobs/Mob.java:1400-1405`, `core/…/mobs/Mob.java:1686-1705`, `core/…/hero/Hero.java:1438`</small>

| Class | Note |
|---|---|
| Warrior | Door ambush with the shortsword; throwing stones on sleeping snakes from range. |
| Mage | Magic Missile: auto-hit, 4 HP snake usually dies in 1-2 zaps. |
| Rogue | Door ambush with the dagger (surprise damage 75%-max). The Cloak of Shadows gives one guaranteed surprise hit per activation (attacking dispels it). Thrown knives also get the surprise bonus. |
| Huntress | Spirit bow from range on sleepers (guaranteed) or at 30%+ on hunters; door ambush with gloves. |
| Duelist | Rapier lunge (from distance 2) has infinite accuracy; door ambush otherwise. |
| Cleric | Guiding Light (no hit roll) usually kills; if not, Illuminated makes the next cudgel hit certain. |

### Community notes

No community claim about this enemy was found.

??? note "Corrected during verification (2)"

    - Guide-flash telegraph also triggers at 4 dodges before the first boss kill.
    - Rogue note: the cloak does not make every hit a surprise; any attack dispels it.

## Gnoll scout {#gnoll}

`actors.mobs.Gnoll` · depths 2-4 · **Tactic:** Throw or shoot once as it closes, then melee. No reason to keep distance: the gnoll scout has no ranged or magic attack.

**Stats** (from the [Codex](../codex/mobs.md)): HT 12 · accuracy 10 · evasion 4 · damage 1-6 · armour 0-2 · EXP 2 · max level 8 · properties none · loot Gold (50% base). <small>`core/…/mobs/Gnoll.java:29`, `core/…/mobs/Gnoll.java:50`, `core/…/mobs/Gnoll.java:45`, `core/…/mobs/Gnoll.java:55`</small>

Depths corrected by hand: the boss floors 5, 10 and 15 create no rotation mobs and run no respawner, so this enemy never spawns there. <small>`core/…/mobs/MobSpawner.java:70-155`, `core/…/levels/SewerBossLevel.java:132-138`, `core/…/levels/PrisonBossLevel.java:597-604`, `core/…/levels/CavesBossLevel.java:207-219`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** baseSpeed 1. Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/mobs/Mob.java:1355`, `core/…/mobs/Mob.java:1302`, `core/…/actors/Char.java:175`, `core/…/actors/Char.java:770-783`, `core/…/levels/Terrain.java:90-91`, `core/…/levels/Level.java:1212`, `core/…/levels/Level.java:1257-1258`, `core/…/features/Door.java:35-59`, `core/…/actors/Char.java:1311-1313`, `core/…/mobs/Mob.java:1686-1705`, `core/…/hero/Hero.java:1438`</small>
- **Attack.** `melee`, reach 1. Adjacent only; the scout has no ranged or magic attack (no canAttack override). Attack time: 1 turn. <small>`core/…/mobs/Gnoll.java:29-58`, `core/…/mobs/Mob.java:558-568`, `core/…/mobs/Mob.java:753-757`, `core/…/mobs/Mob.java:774-779`</small>
- **AI.** Starts `sleeping`; flees: never. Plain melee mob: HT 12, acc 10, dmg 1-6, def 4, DR 0-2, 50% gold. <small>`core/…/mobs/Mob.java:124`, `core/…/levels/Level.java:774-776`, `core/…/mobs/Gnoll.java:31-57`</small>
- **Evasion.** defenseSkill 4; evasive: no. Hit chance for an accuracy roll A vs evasion D (both uniform) is A/(2D) when A&lt;=D, else 1-D/(2A) (derived from core/…/actors/Char.java:646-684). Hero lvl1 vs def 4: ~80%. <small>`core/…/mobs/Gnoll.java:35`, `core/…/actors/Char.java:619-685`</small>
- **Surprise.** Can be surprised: yes. No override. <small>`core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/mobs/Mob.java:1237`, `core/…/mobs/Mob.java:1298`, `core/…/mobs/Mob.java:1327`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `close`; ranged attacks first: yes.
- **Plan.** Throw or shoot once as it closes, then melee. No reason to keep distance: the gnoll scout has no ranged or magic attack. The lightning-casting gnolls are Gnoll Shamans, which spawn in the Caves (depths 11-15), not here. <small>`core/…/hero/HeroClass.java:175-182`, `core/…/missiles/MissileWeapon.java:215-233`, `core/…/items/Item.java:633-635`, `core/…/mechanics/Ballistica.java:42-51`, `core/…/melee/Rapier.java:92-151`, `core/…/mobs/Mob.java:783-793`, `core/…/spells/GuidingLight.java:86-91`, `core/…/mobs/MobSpawner.java:127-157`</small>
- **Kill or nullify: Open with a ranged hit, then melee.** Ordinary melee mob with a 1-6 bite; a free throw on approach shortens the trade. (derived) <small>`core/…/mobs/Gnoll.java:44-57`, `core/…/missiles/MissileWeapon.java:215-233`, `core/…/items/Item.java:633-635`, `core/…/mechanics/Ballistica.java:42-51`</small>
- **Kill or nullify: Chokepoint for packs (a corridor tile, not the door cell).** Adjacency-only attacks; a 1-wide corridor lets one gnoll reach you, the door cell lets three. (derived) <small>`core/…/levels/Level.java:1533-1543`, `SPD-classes/…/utils/PathFinder.java:67-70`, `core/…/mobs/Mob.java:558-568`</small>
- **Escape.** Outrunnable: no; contact breaks at: door, out-of-sight, stairs. Same speed as hero. <small>`core/…/mobs/Mob.java:1355`, `core/…/mobs/Mob.java:1302`, `core/…/actors/Char.java:175`, `core/…/actors/Char.java:770-783`, `core/…/mobs/Mob.java:1327`, `core/…/mobs/Mob.java:1342-1360`, `core/…/mobs/Mob.java:1400-1405`, `core/…/mobs/Mob.java:1686-1705`, `core/…/hero/Hero.java:1438`</small>

| Class | Note |
|---|---|
| Warrior | Stone then shortsword. |
| Mage | 1 missile then staff, or 2-3 missiles for a no-damage kill. |
| Rogue | Knife then dagger. |
| Huntress | Arrows while it approaches, gloves when adjacent. |
| Duelist | Lunge in from 2 tiles (auto-hit) then rapier. |
| Cleric | Guiding Light then cudgel on the Illuminated gnoll. |

### Community notes

- **Tier 1.** “Whittle Gnoll Scouts down with darts or wand charges as they approach, before they reach melee range, and avoid fighting two or more at once — even killing them one by one you won't out-heal the combined damage and can die.” [source](https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Gnoll_Scout) (version: unspecified (SPD wiki, no version stated)). Confirmed: scouts are melee-only (no canAttack override), so every tile they walk toward you is a free ranged shot, and each extra adjacent gnoll adds its own 1-6 attack. 'You won't out-heal two' is a judgment, not a code fact. <small>`core/…/mobs/Gnoll.java:29-58`, `core/…/mobs/Mob.java:558-568`, `core/…/missiles/MissileWeapon.java:223-233`</small>

??? note "Corrected during verification (2)"

    - Hand check: depths '2-5' corrected to '2-4'; the sewer, prison and caves boss floors create no rotation mobs and add no respawner (SewerBossLevel.java:132-138, PrisonBossLevel.java:597-604, CavesBossLevel.java:207-219).
    - Chokepoint counter corrected (door cell is not a chokepoint); shaman depth now cited from MobSpawner.

## Gnoll exile {#gnollexile}

`actors.mobs.GnollExile` · depths 2-4 (1/50 alternate of Gnoll) · **Tactic:** Decide first: it's the toughest sewer regular (24 HP, 1-10 dmg, acc 15, reach 2) but drops 2-3 items.

**Stats** (from the [Codex](../codex/mobs.md)): HT 24 · accuracy 15 · evasion 6 · damage 1-10 · armour `super.drRoll() + Random.NormalIntRange(0, 1)` · EXP 2 · max level 8 · properties none · loot Gold (0% base). <small>`core/…/mobs/GnollExile.java:41`, `core/…/mobs/GnollExile.java:65`, `core/…/mobs/GnollExile.java:60`, `core/…/mobs/GnollExile.java:70`</small>

Depths corrected by hand: the boss floors 5, 10 and 15 create no rotation mobs and run no respawner, so this enemy never spawns there. <small>`core/…/mobs/MobSpawner.java:70-155`, `core/…/levels/SewerBossLevel.java:132-138`, `core/…/levels/PrisonBossLevel.java:597-604`, `core/…/levels/CavesBossLevel.java:207-219`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** baseSpeed 1. Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/mobs/Mob.java:1355`, `core/…/mobs/Mob.java:1302`, `core/…/actors/Char.java:175`, `core/…/actors/Char.java:770-783`, `core/…/levels/Terrain.java:90-91`, `core/…/levels/Level.java:1212`, `core/…/levels/Level.java:1257-1258`, `core/…/features/Door.java:35-59`, `core/…/actors/Char.java:1311-1313`, `core/…/mobs/Mob.java:1686-1705`, `core/…/hero/Hero.java:1438`</small>
- **Attack.** `melee`, reach 2. Adjacent, or within distance 2 when a path of length &lt;=2 exists through non-solid cells not occupied by other characters (spear reach; allies/mobs in between block it). Attack time: 1 turn. <small>`core/…/mobs/GnollExile.java:74-96`, `core/…/mobs/Mob.java:753-757`, `core/…/mobs/Mob.java:774-779`</small>
- **Passive until provoked.** Starts PASSIVE: wanders and ignores the hero. Turns hostile only if it takes damage (alerted) while seeing the hero, or gets any negative buff. Noise (beckon) moves it but does not end passivity. A miss triggers neither defenseProc nor damage, so it does not provoke it. A miss is only possible if the hero cannot surprise attack (Flail, or a weapon above the hero's STR), because a passive exile is otherwise always surprised. *Telegraph:* Log: 'The spear-wielding gnoll looks at you warily, but doesn't move to attack.' (passive) / 'The spear-wielding gnoll moves to attack!' (aggro); description text changes. <small>`core/…/mobs/GnollExile.java:49-51`, `core/…/mobs/GnollExile.java:120-130`, `core/…/mobs/GnollExile.java:146-181`, `core/…/mobs/GnollExile.java:198-204`, `core/…/actors/actors.properties:1655-1658`, `core/…/actors/Char.java:583-604`, `core/…/hero/Hero.java:744-752`, `core/…/mobs/Mob.java:902-925`</small>
- **Rich loot.** Drops 2-3 random items if hero level &lt;= maxLvl+2 (10). *Telegraph:* Description mentions its backpack. <small>`core/…/mobs/GnollExile.java:98-118`, `core/…/actors/actors.properties:1655`</small>
- **AI.** Starts `passive`; flees: never. HT 24, acc 15, dmg 1-10, def 6, DR 0-3 (per codex). Passive state extends Wandering so it walks around with enemySeen false. <small>`core/…/mobs/GnollExile.java:46-72`, `core/…/mobs/GnollExile.java:146-181`, `core/…/levels/Level.java:774-776`</small>
- **Evasion.** defenseSkill 6; evasive: no. Hit chance for an accuracy roll A vs evasion D (both uniform) is A/(2D) when A&lt;=D, else 1-D/(2A) (derived from core/…/actors/Char.java:646-684). Hero lvl1 vs 6: ~70%. <small>`core/…/mobs/GnollExile.java:53`, `core/…/actors/Char.java:619-685`</small>
- **Surprise.** Can be surprised: yes. While passive it runs Wandering.continueWandering, which sets enemySeen false every tick, so the opening hit is always a surprise (guaranteed hit, dagger bonus) even if it is looking at you. <small>`core/…/mobs/GnollExile.java:146-181`, `core/…/mobs/Mob.java:1297-1299`, `core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/mobs/Mob.java:1237`, `core/…/mobs/Mob.java:1298`, `core/…/mobs/Mob.java:1327`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `surprise`; ranged attacks first: no.
- **Plan.** Decide first: it's the toughest sewer regular (24 HP, 1-10 dmg, acc 15, reach 2) but drops 2-3 items. If you are healthy and want the loot, open with a guaranteed surprise hit (it's passive), then fight it adjacent or in a doorway; if weak, walk past it — it won't engage. <small>`core/…/mobs/GnollExile.java:59-118`, `core/…/melee/Dagger.java:62-78`, `core/…/missiles/ThrowingKnife.java:50-66`, `core/…/artifacts/CloakOfShadows.java:306`, `core/…/actors/Char.java:627-630`, `core/…/mobs/Mob.java:875`, `core/…/melee/Rapier.java:92-151`, `core/…/mobs/Mob.java:783-793`, `core/…/spells/GuidingLight.java:86-91`, `core/…/mobs/Mob.java:902-925`, `core/…/mobs/Mob.java:834-841`, `core/…/wands/WandOfMagicMissile.java:56-62`, `core/…/actors/Char.java:390`, `core/…/actors/Char.java:486`, `core/…/hero/Hero.java:481`, `core/…/hero/Hero.java:2368`, `core/…/buffs/Invisibility.java:90-98`, `core/…/artifacts/CloakOfShadows.java:360-366`, `core/…/spells/GuidingLight.java:149-153`</small>
- **Kill or nullify: Choose the fight: you may simply leave it alone.** It never attacks unless damaged or debuffed. (derived) <small>`core/…/mobs/GnollExile.java:146-181`</small>
- **Kill or nullify: Open with your biggest guaranteed hit.** Passive = unseeing = surprised; the first blow cannot miss. (derived) <small>`core/…/mobs/Mob.java:1298`, `core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/mobs/Mob.java:1237`, `core/…/mobs/Mob.java:1327`, `core/…/melee/Dagger.java:62-78`, `core/…/missiles/ThrowingKnife.java:50-66`</small>
- **Kill or nullify: Don't stand 2 tiles away in the open.** Its reach is 2; to deny reach, put another character or a wall in the 2-step path, or be fully out of range. (derived) <small>`core/…/mobs/GnollExile.java:74-96`</small>
- **Kill or nullify: Kite with ranged only beyond 2 tiles.** Same speed as you; ranged attacks from 3+ tiles are safe until it closes. (derived) <small>`core/…/mobs/Mob.java:1355`, `core/…/mobs/Mob.java:1302`, `core/…/actors/Char.java:175`, `core/…/actors/Char.java:770-783`, `core/…/mobs/GnollExile.java:80-93`</small>
- **Escape.** Outrunnable: no; contact breaks at: door, out-of-sight, stairs. Once aggro it is a normal-speed hunter; break sight via doors. <small>`core/…/mobs/Mob.java:1355`, `core/…/mobs/Mob.java:1302`, `core/…/actors/Char.java:175`, `core/…/actors/Char.java:770-783`, `core/…/mobs/Mob.java:1327`, `core/…/mobs/Mob.java:1342-1360`, `core/…/mobs/Mob.java:1400-1405`, `core/…/mobs/Mob.java:1686-1705`, `core/…/hero/Hero.java:1438`</small>

| Class | Note |
|---|---|
| Warrior | Surprise shortsword hit, then melee from a corridor tile; avoid standing 2 tiles away in the open. |
| Mage | Zap only if you can finish it; missiles bypass its DR. |
| Rogue | Best candidate: dagger surprise (75%-max) as the opener. The cloak gives one more surprise per activation (attacking dispels it), not repeated ones. |
| Huntress | Surprise arrow, then keep 3+ tiles and shoot; it matches your speed so expect it to close. |
| Duelist | Lunge opener (infinite accuracy). |
| Cleric | Either opener is guaranteed: a cudgel surprise hit (passive = unseeing), or Guiding Light (no hit roll) followed by a certain cudgel hit on the Illuminated exile. Both provoke it, since Illuminated is a negative buff and any damage alerts it. |

### Community notes

No community claim about this enemy was found.

??? note "Corrected during verification (5)"

    - Hand check: depths '2-5 (1/50 alternate of Gnoll)' corrected to '2-4 (1/50 alternate of Gnoll)'; the sewer, prison and caves boss floors create no rotation mobs and add no respawner (SewerBossLevel.java:132-138, PrisonBossLevel.java:597-604, CavesBossLevel.java:207-219).
    - Resolved the open question: a missed attack does not provoke (Char.java:583-604); a miss only happens without surprise ability.
    - Warrior note: removed the unsupported 'Broken Seal shield up' claim.
    - Rogue note: the cloak does not give repeated surprises.
    - Cleric note: rewritten; the old reason (Guiding Light aggroes it) applies equally to a cudgel hit, and Illuminated is NEGATIVE, so it also triggers the debuff aggro.

## Sewer crab {#crab}

`actors.mobs.Crab` · depths 3-4 · **Tactic:** The sewer's real damage dealer (1-7, acc 12, fast). Take one ranged shot as it appears, then stand and melee in a corridor/doorway.

**Stats** (from the [Codex](../codex/mobs.md)): HT 15 · accuracy 12 · evasion 5 · damage 1-7 · armour 0-4 · EXP 4 · max level 9 · properties none · loot MysteryMeat (16.7% base). <small>`core/…/mobs/Crab.java:29`, `core/…/mobs/Crab.java:51`, `core/…/mobs/Crab.java:46`, `core/…/mobs/Crab.java:56`</small>

Depths corrected by hand: the boss floors 5, 10 and 15 create no rotation mobs and run no respawner, so this enemy never spawns there. <small>`core/…/mobs/MobSpawner.java:70-155`, `core/…/levels/SewerBossLevel.java:132-138`, `core/…/levels/PrisonBossLevel.java:597-604`, `core/…/levels/CavesBossLevel.java:207-219`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** baseSpeed 2: each move costs 0.5 turn, so it covers 2 tiles per hero turn. Speed `fast`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/mobs/Crab.java:36`, `core/…/mobs/Mob.java:1355`, `core/…/mobs/Mob.java:1302`, `core/…/actors/Char.java:175`, `core/…/actors/Char.java:770-783`, `core/…/levels/Terrain.java:90-91`, `core/…/levels/Level.java:1212`, `core/…/levels/Level.java:1257-1258`, `core/…/features/Door.java:35-59`, `core/…/actors/Char.java:1311-1313`, `core/…/mobs/Mob.java:1686-1705`, `core/…/hero/Hero.java:1438`</small>
- **Attack.** `melee`, reach 1. Adjacent only. Attack time: 1 turn (speed does not speed up attacks). <small>`core/…/mobs/Mob.java:558-568`, `core/…/mobs/Mob.java:753-757`, `core/…/mobs/Mob.java:774-779`</small>
- **Thick carapace.** DR 0-4 applied to each weapon hit. *Telegraph:* none <small>`core/…/mobs/Crab.java:56-58`, `core/…/actors/Char.java:390`, `core/…/actors/Char.java:483-486`</small>
- **AI.** Starts `sleeping`; flees: never. HT 15, acc 12, dmg 1-7, def 5, EXP 4. <small>`core/…/mobs/Mob.java:124`, `core/…/levels/Level.java:774-776`, `core/…/mobs/Crab.java:31-58`</small>
- **Evasion.** defenseSkill 5; evasive: no. Hit chance for an accuracy roll A vs evasion D (both uniform) is A/(2D) when A&lt;=D, else 1-D/(2A) (derived from core/…/actors/Char.java:646-684). Hero lvl1 vs 5: 75%. <small>`core/…/mobs/Crab.java:35`, `core/…/actors/Char.java:619-685`</small>
- **Surprise.** Can be surprised: yes. Door ambush is unreliable: at speed 2 it acts again 0.5 turns after stepping into the door and re-checks sight. It works only when that step landed on a half-turn (the hero acts first on ties), roughly half the time. Sleeping or not-yet-noticing crabs are reliable surprise targets. <small>`core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/mobs/Mob.java:1237`, `core/…/mobs/Mob.java:1298`, `core/…/mobs/Mob.java:1327`, `core/…/mobs/Crab.java:36`, `core/…/mobs/Mob.java:1355`, `core/…/actors/Char.java:198-203`, `core/…/actors/Actor.java:50-52`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `chokepoint`; ranged attacks first: yes.
- **Plan.** The sewer's real damage dealer (1-7, acc 12, fast). Take one ranged shot as it appears, then stand and melee in a corridor/doorway. Never try to flee it across a room. Wands and big hits beat its armor. <small>`core/…/mobs/Crab.java:36-58`, `core/…/wands/WandOfMagicMissile.java:56-62`, `core/…/actors/Char.java:390`, `core/…/actors/Char.java:486`, `core/…/actors/Char.java:483-486`, `core/…/melee/Gloves.java:37`, `core/…/missiles/MissileWeapon.java:215-233`, `core/…/items/Item.java:633-635`, `core/…/mechanics/Ballistica.java:42-51`, `core/…/melee/Rapier.java:92-151`, `core/…/mobs/Mob.java:783-793`, `core/…/spells/GuidingLight.java:86-91`</small>
- **Kill or nullify: Don't run; fight where you stand or at a chokepoint.** At 2x speed it gains a tile every turn you flee, and gets free hits. (derived) <small>`core/…/mobs/Crab.java:36`, `core/…/mobs/Mob.java:1355`, `core/…/mobs/Mob.java:1302`, `core/…/actors/Char.java:175`, `core/…/actors/Char.java:770-783`</small>
- **Kill or nullify: Only one free ranged shot.** It closes 2 tiles per turn; spot it early and use one throw/arrow, then melee. (derived) <small>`core/…/mobs/Crab.java:36`, `core/…/missiles/MissileWeapon.java:215-233`, `core/…/items/Item.java:633-635`, `core/…/mechanics/Ballistica.java:42-51`</small>
- **Kill or nullify: Hit hard, not often; or use wands.** DR 0-4 per hit eats small hits; wand damage bypasses DR entirely. (derived) <small>`core/…/actors/Char.java:390`, `core/…/actors/Char.java:483-486`, `core/…/wands/WandOfMagicMissile.java:56-62`, `core/…/actors/Char.java:486`</small>
- **Kill or nullify: Snipe it asleep.** Sleeping = guaranteed hit from range; strongest opening before it wakes. (derived) <small>`core/…/mobs/Mob.java:1237`, `core/…/mobs/Mob.java:1244-1246`, `core/…/mobs/Mob.java:1227-1232`, `core/…/mobs/Mob.java:1282-1284`, `core/…/actors/Actor.java:50-52`, `core/…/actors/Char.java:1280-1286`, `core/…/missiles/MissileWeapon.java:215-233`, `core/…/items/Item.java:633-635`, `core/…/mechanics/Ballistica.java:42-51`</small>
- **Kill or nullify: Slow it.** Cripple halves speed, putting it at hero speed. (derived) <small>`core/…/actors/Char.java:772`</small>
- **Escape.** Outrunnable: no; contact breaks at: stairs, door. Cannot be outrun. Only leaving the floor by stairs you are already next to reliably ends contact; doors break sight but it reaches them first. <small>`core/…/mobs/Crab.java:36`, `core/…/mobs/Mob.java:1355`, `core/…/mobs/Mob.java:1302`, `core/…/actors/Char.java:175`, `core/…/actors/Char.java:770-783`, `core/…/mobs/Mob.java:1686-1705`, `core/…/hero/Hero.java:1438`, `core/…/mobs/Mob.java:1327`, `core/…/mobs/Mob.java:1342-1360`, `core/…/mobs/Mob.java:1400-1405`</small>

| Class | Note |
|---|---|
| Warrior | Stand and fight; shortsword + seal shield; the extra HP is the answer. |
| Mage | Magic Missile ignores its DR; zap as it approaches. |
| Rogue | Snipe a sleeping crab with knives for surprise damage; otherwise dagger. |
| Huntress | One arrow as it appears, then gloves. Gloves strike twice per turn at full accuracy, so despite DR 0-4 eating part of each hit they out-damage adjacent arrows at x0.5 accuracy. |
| Duelist | Lunge when it's 2 tiles away (infinite accuracy) to trade first. |
| Cleric | Guiding Light (no DR) then cudgel on Illuminated target. |

### Community notes

- **Tier 1.** “Crabs move twice as fast as the Hero, so ranged kiting is hard.” [source](https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Sewer_Crab) (version: unspecified (SPD wiki, no version stated)). baseSpeed 2 and move cost 1/speed confirm it: a crab closes 2 tiles per hero turn. (Part of a compound claim.) <small>`core/…/mobs/Crab.java:36`, `core/…/mobs/Mob.java:1355`</small>
- **Tier F.** “<del>Use surprise attacks from around corners/doors (guaranteed hits) instead.</del>” [source](https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Sewer_Crab) (version: unspecified (SPD wiki, no version stated)). A surprise hit does always land, but the door ambush against a crab is not reliable: at speed 2 it acts again 0.5 turns after stepping into the door, sees you, and is no longer surprised, unless that step fell on a half-turn. Snipe it asleep or before it notices you instead. (Part of a compound claim.) <small>`core/…/mobs/Crab.java:36`, `core/…/mobs/Mob.java:1327`, `core/…/mobs/Mob.java:1355`, `core/…/actors/Actor.java:50-52`</small>
- **Tier 1.** “Debuff them with stun, paralysis, burning or poison via seeds/traps.” [source](https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Sewer_Crab) (version: unspecified (SPD wiki, no version stated)). The crab has no properties, immunities or resistances, so all of these apply; paralysis also zeroes its evasion. (Part of a compound claim.) <small>`core/…/mobs/Crab.java:29-59`, `core/…/mobs/Mob.java:277-282`, `core/…/mobs/Mob.java:796-802`</small>
- **Tier 1.** “Gear up to a Tier-2 weapon/armor before depth 3-4 where crabs start appearing, since they hit hardest of any Sewer enemy.” [source](https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Sewer_Crab) (version: unspecified (SPD wiki, no version stated)). Crabs first appear on depth 3, and 1-7 damage at accuracy 12 is the highest of the regular sewer mobs (gnoll 1-6/10, slime 2-5/12, rat and snake 1-4). The rare Gnoll Exile (1-10) and Goo hit harder. The gear advice is judgment. (Part of a compound claim.) <small>`core/…/mobs/MobSpawner.java:85-97`, `core/…/mobs/Crab.java:45-53`, `core/…/mobs/Gnoll.java:44-52`, `core/…/mobs/Slime.java:47-55`, `core/…/mobs/GnollExile.java:59-67`</small>
- **Tier 1.** “Because a Crab moves 2 tiles for every 1 of yours, plan positioning: if you're one tile away, back up one more tile so the crab is forced to spend its whole turn closing distance and can't attack you that turn.” [source](https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Sewer_Crab) (version: unspecified (SPD wiki, no version stated)). Follows from move cost 0.5 and attack cost 1: if you end your turn 3+ tiles away, the crab spends its whole turn closing and you strike first. It costs you your action too, so it trades tempo for the first hit. Keep doing it until it is adjacent at the start of your turn. <small>`core/…/mobs/Crab.java:36`, `core/…/mobs/Mob.java:1355`, `core/…/mobs/Mob.java:753-757`</small>

??? note "Corrected during verification (3)"

    - Hand check: depths '3-5' corrected to '3-4'; the sewer, prison and caves boss floors create no rotation mobs and add no respawner (SewerBossLevel.java:132-138, PrisonBossLevel.java:597-604, CavesBossLevel.java:207-219).
    - Surprise note: quantified the door-ambush failure (timing phase), and noted sleepers remain reliable.
    - Huntress note rewritten; the old text was self-contradictory.

## Hermit crab {#hermitcrab}

`actors.mobs.HermitCrab` · depths 3-4 (1/50 alternate of Crab) · **Tactic:** Worth killing for the guaranteed armor. Don't plink it with weak hits (DR 2-6).

**Stats** (from the [Codex](../codex/mobs.md)): HT 25 · accuracy 12 · evasion 5 · damage 1-7 · armour `super.drRoll() + 2` · EXP 4 · max level 9 · properties none · loot MysteryMeat (50% base). <small>`core/…/mobs/HermitCrab.java:28`, `core/…/mobs/Crab.java:51`, `core/…/mobs/Crab.java:46`, `core/…/mobs/HermitCrab.java:50`</small>

Depths corrected by hand: the boss floors 5, 10 and 15 create no rotation mobs and run no respawner, so this enemy never spawns there. <small>`core/…/mobs/MobSpawner.java:70-155`, `core/…/levels/SewerBossLevel.java:132-138`, `core/…/levels/PrisonBossLevel.java:597-604`, `core/…/levels/CavesBossLevel.java:207-219`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** baseSpeed 1 (half a sewer crab's). Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/mobs/HermitCrab.java:34`, `core/…/mobs/Mob.java:1355`, `core/…/mobs/Mob.java:1302`, `core/…/actors/Char.java:175`, `core/…/actors/Char.java:770-783`, `core/…/levels/Terrain.java:90-91`, `core/…/levels/Level.java:1212`, `core/…/levels/Level.java:1257-1258`, `core/…/features/Door.java:35-59`, `core/…/actors/Char.java:1311-1313`, `core/…/mobs/Mob.java:1686-1705`, `core/…/hero/Hero.java:1438`</small>
- **Attack.** `melee`, reach 1. Adjacent only. Attack time: 1 turn. <small>`core/…/mobs/Mob.java:558-568`, `core/…/mobs/Mob.java:753-757`, `core/…/mobs/Mob.java:774-779`</small>
- **Barrel armor.** DR 2-6 (crab 0-4 +2) on every weapon hit; HT 25. *Telegraph:* none <small>`core/…/mobs/HermitCrab.java:33`, `core/…/mobs/HermitCrab.java:49-52`, `core/…/actors/Char.java:390`, `core/…/actors/Char.java:483-486`</small>
- **Guaranteed armor drop.** Drops a random armor if hero level &lt;= 11, plus 50% meat. *Telegraph:* description mentions something rattling in the barrel <small>`core/…/mobs/HermitCrab.java:36-47`, `core/…/actors/actors.properties:1719`</small>
- **AI.** Starts `sleeping`; flees: never. Crab AI; acc 12, dmg 1-7, def 5. <small>`core/…/mobs/Mob.java:124`, `core/…/levels/Level.java:774-776`, `core/…/mobs/Crab.java:45-53`</small>
- **Evasion.** defenseSkill 5; evasive: no. Same evasion as Crab; the defence is DR, not dodge. <small>`core/…/mobs/Crab.java:35`</small>
- **Surprise.** Can be surprised: yes. Normal speed, so the door ambush works. <small>`core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/mobs/Mob.java:1237`, `core/…/mobs/Mob.java:1298`, `core/…/mobs/Mob.java:1327`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `surprise`; ranged attacks first: yes.
- **Plan.** Worth killing for the guaranteed armor. Don't plink it with weak hits (DR 2-6). Open with a surprise, use wand charges, fight at a door. <small>`core/…/mobs/HermitCrab.java:49-52`, `core/…/wands/WandOfMagicMissile.java:56-62`, `core/…/actors/Char.java:390`, `core/…/actors/Char.java:486`, `core/…/melee/Dagger.java:62-78`, `core/…/missiles/ThrowingKnife.java:50-66`, `core/…/melee/Gloves.java:37`, `core/…/missiles/MissileWeapon.java:215-233`, `core/…/items/Item.java:633-635`, `core/…/mechanics/Ballistica.java:42-51`, `core/…/melee/Rapier.java:92-151`, `core/…/mobs/Mob.java:783-793`, `core/…/spells/GuidingLight.java:86-91`</small>
- **Kill or nullify: Wands and big hits.** DR 2-6 floors small hits to 0; wand damage ignores DR. (derived) <small>`core/…/mobs/HermitCrab.java:49-52`, `core/…/wands/WandOfMagicMissile.java:56-62`, `core/…/actors/Char.java:390`, `core/…/actors/Char.java:486`</small>
- **Kill or nullify: Door ambush / surprise.** Normal speed; guaranteed hits help against a 25 HP target. (derived) <small>`core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/mobs/Mob.java:1237`, `core/…/mobs/Mob.java:1298`, `core/…/mobs/Mob.java:1327`, `core/…/actors/actors.properties:1799`</small>
- **Kill or nullify: Ranged shots while it approaches, then melee or wands.** Equal speed: you get free shots only while it walks in from a distance. Each throw lets it close a tile, and backing off only holds the distance, never gains it, so a 'kite loop' does not work. (derived) <small>`core/…/mobs/Mob.java:1355`, `core/…/mobs/Mob.java:1302`, `core/…/actors/Char.java:175`, `core/…/actors/Char.java:770-783`, `core/…/missiles/MissileWeapon.java:215-233`, `core/…/items/Item.java:633-635`, `core/…/mechanics/Ballistica.java:42-51`</small>
- **Escape.** Outrunnable: no; contact breaks at: door, out-of-sight, stairs. Equal speed; break sight to lose it. <small>`core/…/mobs/Mob.java:1355`, `core/…/mobs/Mob.java:1302`, `core/…/actors/Char.java:175`, `core/…/actors/Char.java:770-783`, `core/…/mobs/Mob.java:1327`, `core/…/mobs/Mob.java:1342-1360`, `core/…/mobs/Mob.java:1400-1405`, `core/…/mobs/Mob.java:1686-1705`, `core/…/hero/Hero.java:1438`</small>

| Class | Note |
|---|---|
| Warrior | Shortsword dmg is modest vs DR 2-6; stack surprise hits via door ambush. |
| Mage | Magic Missile ignores DR; best class vs it. |
| Rogue | Surprise dagger/knife damage (75%-max) gets through armor best. |
| Huntress | Gloves are near-useless vs DR 2-6; use the bow from range (bow at range x1.5 acc). |
| Duelist | Lunge + rapier; prefer surprises. |
| Cleric | Guiding Light ignores DR. |

### Community notes

No community claim about this enemy was found.

??? note "Corrected during verification (2)"

    - Hand check: depths '3-5 (1/50 alternate of Crab)' corrected to '3-4 (1/50 alternate of Crab)'; the sewer, prison and caves boss floors create no rotation mobs and add no respawner (SewerBossLevel.java:132-138, PrisonBossLevel.java:597-604, CavesBossLevel.java:207-219).
    - 'Kite with ranged' counter rewritten: at equal speed you cannot both back away and throw.

## Slime {#slime}

`actors.mobs.Slime` · depths 4 · **Tactic:** A 20 HP sponge that punishes heavy weapons. Use your fastest weapon, DOTs, or thrown weapons; don't spend surprise-damage openers or upgraded heavy hits here.

**Stats** (from the [Codex](../codex/mobs.md)): HT 20 · accuracy 12 · evasion 5 · damage 2-5 · armour 0 (no override: only Barkskin adds to it) · EXP 4 · max level 9 · properties none. <small>`core/…/mobs/Slime.java:33`, `core/…/mobs/Slime.java:53`, `core/…/mobs/Slime.java:48`, `core/…/actors/Char.java:701`</small>

Depths corrected by hand: the boss floors 5, 10 and 15 create no rotation mobs and run no respawner, so this enemy never spawns there. <small>`core/…/mobs/MobSpawner.java:70-155`, `core/…/levels/SewerBossLevel.java:132-138`, `core/…/levels/PrisonBossLevel.java:597-604`, `core/…/levels/CavesBossLevel.java:207-219`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** baseSpeed 1. Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/mobs/Mob.java:1355`, `core/…/mobs/Mob.java:1302`, `core/…/actors/Char.java:175`, `core/…/actors/Char.java:770-783`, `core/…/levels/Terrain.java:90-91`, `core/…/levels/Level.java:1212`, `core/…/levels/Level.java:1257-1258`, `core/…/features/Door.java:35-59`, `core/…/actors/Char.java:1311-1313`, `core/…/mobs/Mob.java:1686-1705`, `core/…/hero/Hero.java:1438`</small>
- **Attack.** `melee`, reach 1. Adjacent only. Attack time: 1 turn. <small>`core/…/mobs/Mob.java:558-568`, `core/…/mobs/Mob.java:753-757`, `core/…/mobs/Mob.java:774-779`</small>
- **Elastic membrane (damage compression).** Any single instance of damage &gt;= 5 (from any source) is reduced: 5/7/10/14/19/25 incoming become 5/6/7/8/9/10. *Telegraph:* Description says it's hard to deal more than 6 damage per attack; def_verb 'blocked'. <small>`core/…/mobs/Slime.java:57-67`, `core/…/actors/actors.properties:1794-1795`</small>
- **Resists.** Big single hits (compression above 4 damage). <small>`core/…/mobs/Slime.java:57-67`</small>
- **AI.** Starts `sleeping`; flees: never. HT 20, acc 12, dmg 2-5, def 5. Drops a +0 tier-2 weapon with chance 1/5, then 1/20, 1/80... <small>`core/…/mobs/Mob.java:124`, `core/…/levels/Level.java:774-776`, `core/…/mobs/Slime.java:35-83`</small>
- **Evasion.** defenseSkill 5; evasive: no. Hit chance for an accuracy roll A vs evasion D (both uniform) is A/(2D) when A&lt;=D, else 1-D/(2A) (derived from core/…/actors/Char.java:646-684). Hero lvl1 vs 5: 75%. <small>`core/…/mobs/Slime.java:39`, `core/…/actors/Char.java:619-685`</small>
- **Surprise.** Can be surprised: yes. No override; but surprise-damage bonuses are partly wasted by the compression. <small>`core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/mobs/Mob.java:1237`, `core/…/mobs/Mob.java:1298`, `core/…/mobs/Mob.java:1327`, `core/…/mobs/Slime.java:57-67`, `core/…/melee/Dagger.java:62-78`, `core/…/missiles/ThrowingKnife.java:50-66`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `close`; ranged attacks first: no.
- **Plan.** A 20 HP sponge that punishes heavy weapons. Use your fastest weapon, DOTs, or thrown weapons; don't spend surprise-damage openers or upgraded heavy hits here. <small>`core/…/mobs/Slime.java:57-67`, `core/…/melee/Gloves.java:37`, `core/…/melee/Dagger.java:62-78`, `core/…/missiles/ThrowingKnife.java:50-66`, `core/…/wands/WandOfMagicMissile.java:56-62`, `core/…/actors/Char.java:390`, `core/…/actors/Char.java:486`, `core/…/melee/MeleeWeapon.java:250-259`, `core/…/melee/Cudgel.java:36-45`, `core/…/wands/WandOfMagicMissile.java:47-53`, `SPD-classes/…/utils/Random.java:138-140`</small>
- **Kill or nullify: Many small hits beat few big ones.** Hits of 4 or less pass through untouched; a 10-damage hit only does 7. (derived) <small>`core/…/mobs/Slime.java:57-67`</small>
- **Kill or nullify: Damage over time.** DOT ticks are separate small instances: Burning is 1-4 per tick at depths 4-5 and small bleeds stay under 5, so neither is compressed. Poison ticks (left/3)+1 and only reach 5 when 12+ turns remain. (derived) <small>`core/…/mobs/Slime.java:57-67`, `core/…/buffs/Poison.java:112`, `core/…/buffs/Burning.java:105-108`</small>
- **Kill or nullify: Chokepoint.** Ordinary adjacency-only melee mob with a steady 2-5 bite. (derived) <small>`core/…/mobs/Mob.java:558-568`</small>
- **Escape.** Outrunnable: no; contact breaks at: door, out-of-sight, stairs. Same speed. <small>`core/…/mobs/Mob.java:1355`, `core/…/mobs/Mob.java:1302`, `core/…/actors/Char.java:175`, `core/…/actors/Char.java:770-783`, `core/…/mobs/Mob.java:1327`, `core/…/mobs/Mob.java:1342-1360`, `core/…/mobs/Mob.java:1400-1405`, `core/…/mobs/Mob.java:1686-1705`, `core/…/hero/Hero.java:1438`</small>

| Class | Note |
|---|---|
| Warrior | The worn shortsword rolls 1-10, so about two thirds of its hits are 5+ and get compressed (10 becomes 7). Still fine, just slower; no reason to spend resources. |
| Mage | Magic Missile rolls 2-8; about 37% of zaps are 6+ and lose 1-2 damage to compression. It still never misses, so it stays efficient. |
| Rogue | Dagger normal hits are fine; don't waste a surprise (75%-max roll gets capped). |
| Huntress | Gloves (fast, small hits) are ideal. |
| Duelist | Rapier; lunge bonus damage is partly capped. |
| Cleric | Cudgel (1-8, half of hits are 5+ and get trimmed by 1-2). Guiding Light 2-8 is trimmed the same way on about a third of casts. |

### Community notes

No community claim about this enemy was found.

??? note "Corrected during verification (5)"

    - Hand check: depths '4-5' corrected to '4'; the sewer, prison and caves boss floors create no rotation mobs and add no respawner (SewerBossLevel.java:132-138, PrisonBossLevel.java:597-604, CavesBossLevel.java:207-219).
    - Warrior note was wrong: shortsword 1-10 lands 5+ on about 68% of hits, so most hits ARE compressed.
    - Mage note was wrong: Magic Missile 2-8 exceeds 5 on about 37% of zaps, not 'rarely'.
    - Cleric note was wrong: Guiding Light 2-8 is not 'mostly under the cap'; quantified.
    - DOT counter softened: poison ticks reach 5 at 12+ turns remaining.

## Caustic slime {#causticslime}

`actors.mobs.CausticSlime` · depths 4 (1/50 alternate of Slime) · **Tactic:** Slime plan plus ooze management: soften with ranged hits, finish in melee standing near water, and step into water after the fight to clear Ooze.

**Stats** (from the [Codex](../codex/mobs.md)): HT 20 · accuracy 12 · evasion 5 · damage 2-5 · armour 0 (no override: only Barkskin adds to it) · EXP 4 · max level 9 · properties ACIDIC. <small>`core/…/mobs/CausticSlime.java:33`, `core/…/mobs/Slime.java:53`, `core/…/mobs/Slime.java:48`, `core/…/actors/Char.java:701`</small>

Depths corrected by hand: the boss floors 5, 10 and 15 create no rotation mobs and run no respawner, so this enemy never spawns there. <small>`core/…/mobs/MobSpawner.java:70-155`, `core/…/levels/SewerBossLevel.java:132-138`, `core/…/levels/PrisonBossLevel.java:597-604`, `core/…/levels/CavesBossLevel.java:207-219`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** baseSpeed 1. Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/mobs/Mob.java:1355`, `core/…/mobs/Mob.java:1302`, `core/…/actors/Char.java:175`, `core/…/actors/Char.java:770-783`, `core/…/levels/Terrain.java:90-91`, `core/…/levels/Level.java:1212`, `core/…/levels/Level.java:1257-1258`, `core/…/features/Door.java:35-59`, `core/…/actors/Char.java:1311-1313`, `core/…/mobs/Mob.java:1686-1705`, `core/…/hero/Hero.java:1438`</small>
- **Attack.** `melee`, reach 1. Adjacent only. Attack time: 1 turn. <small>`core/…/mobs/Mob.java:558-568`, `core/…/mobs/Mob.java:753-757`, `core/…/mobs/Mob.java:774-779`</small>
- **Damage compression.** Inherits Slime.damage. *Telegraph:* as Slime <small>`core/…/mobs/Slime.java:57-67`</small>
- **Goo blob drop.** Drops a Goo Blob (and Slime's loot roll) if hero level &lt;= 11. *Telegraph:* none <small>`core/…/mobs/CausticSlime.java:51-62`</small>
- **Inflicts Ooze.** On each landed hit, 50% chance of Ooze for 20 turns; at depths &lt;5 it deals 1 damage every other turn, 1/turn at depth 5; removed by standing in water after it has ticked once. <small>`core/…/mobs/CausticSlime.java:41-49`, `core/…/buffs/Ooze.java:30-37`, `core/…/buffs/Ooze.java:90-120`</small>
- **Immune.** Ooze (ACIDIC) <small>`core/…/mobs/CausticSlime.java:38`, `core/…/actors/Char.java:1427-1428`</small>
- **Resists.** Corrosion (ACIDIC); big single hits (Slime compression) <small>`core/…/actors/Char.java:1427-1428`, `core/…/mobs/Slime.java:57-67`</small>
- **AI.** Starts `sleeping`; flees: never. Slime stats (HT 20, acc 12, dmg 2-5, def 5) plus ACIDIC. <small>`core/…/mobs/Mob.java:124`, `core/…/levels/Level.java:774-776`, `core/…/mobs/CausticSlime.java:35-39`</small>
- **Evasion.** defenseSkill 5; evasive: no. As Slime. <small>`core/…/mobs/Slime.java:39`</small>
- **Surprise.** Can be surprised: yes. No override. <small>`core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/mobs/Mob.java:1237`, `core/…/mobs/Mob.java:1298`, `core/…/mobs/Mob.java:1327`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `close`; ranged attacks first: yes.
- **Plan.** Slime plan plus ooze management: soften with ranged hits, finish in melee standing near water, and step into water after the fight to clear Ooze. <small>`core/…/mobs/CausticSlime.java:41-49`, `core/…/buffs/Ooze.java:93-94`, `core/…/missiles/MissileWeapon.java:215-233`, `core/…/items/Item.java:633-635`, `core/…/mechanics/Ballistica.java:42-51`, `core/…/wands/WandOfMagicMissile.java:56-62`, `core/…/actors/Char.java:390`, `core/…/actors/Char.java:486`</small>
- **Kill or nullify: Fight next to (or in) water.** Stepping into water strips Ooze after its first tick. (derived) <small>`core/…/buffs/Ooze.java:93-94`, `core/…/buffs/Ooze.java:118-120`</small>
- **Kill or nullify: Use ranged/fast small hits as for Slime.** Compression plus every hit it lands is a 50% ooze roll: shorter melee exchanges = fewer oozes. (derived) <small>`core/…/mobs/Slime.java:57-67`, `core/…/mobs/CausticSlime.java:41-49`</small>
- **Escape.** Outrunnable: no; contact breaks at: door, out-of-sight, stairs. Same speed. <small>`core/…/mobs/Mob.java:1355`, `core/…/mobs/Mob.java:1302`, `core/…/actors/Char.java:175`, `core/…/actors/Char.java:770-783`, `core/…/mobs/Mob.java:1327`, `core/…/mobs/Mob.java:1342-1360`, `core/…/mobs/Mob.java:1400-1405`, `core/…/mobs/Mob.java:1686-1705`, `core/…/hero/Hero.java:1438`</small>

| Class | Note |
|---|---|
| Warrior | Melee near water. |
| Mage | Missiles from range; avoid melee exchanges. |
| Rogue | Knives from range then dagger. |
| Huntress | Bow first (keeps it from biting), then gloves. |
| Duelist | Spikes then rapier. |
| Cleric | Guiding Light then cudgel. |

### Community notes

No community claim about this enemy was found.

??? note "Corrected during verification (1)"

    - Hand check: depths '4-5 (1/50 alternate of Slime)' corrected to '4 (1/50 alternate of Slime)'; the sewer, prison and caves boss floors create no rotation mobs and add no respawner (SewerBossLevel.java:132-138, PrisonBossLevel.java:597-604, CavesBossLevel.java:207-219).

## Fetid rat {#fetidrat}

`actors.mobs.FetidRat` · depths quest (Sad Ghost, depth 2) · **Tactic:** Engage from range, ideally with wands (no gas). If you must melee, hit once and step back out of the cloud rather than standing in it; stand near water to clear Ooze.

**Stats** (from the [Codex](../codex/mobs.md)): HT 20 · accuracy 12 · evasion 5 · damage 1-4 · armour `super.drRoll() + Random.NormalIntRange(0, 2)` · EXP 4 · max level 5 · properties DEMONIC, MINIBOSS. <small>`core/…/mobs/FetidRat.java:37`, `core/…/mobs/FetidRat.java:55`, `core/…/mobs/Rat.java:55`, `core/…/mobs/FetidRat.java:60`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** baseSpeed 1 (Rat). Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/mobs/Mob.java:1355`, `core/…/mobs/Mob.java:1302`, `core/…/actors/Char.java:175`, `core/…/actors/Char.java:770-783`, `core/…/levels/Terrain.java:90-91`, `core/…/levels/Level.java:1212`, `core/…/levels/Level.java:1257-1258`, `core/…/features/Door.java:35-59`, `core/…/actors/Char.java:1311-1313`, `core/…/mobs/Mob.java:1686-1705`, `core/…/hero/Hero.java:1438`</small>
- **Attack.** `melee`, reach 1. Adjacent only. Attack time: 1 turn. <small>`core/…/mobs/Mob.java:558-568`, `core/…/mobs/Mob.java:753-757`, `core/…/mobs/Mob.java:774-779`</small>
- **Stench cloud on being hit.** Every time a weapon/attack hit lands on it (defenseProc), it seeds 20 volume of Stench Gas on its own tile. Anyone non-immune in the gas is Paralysed (prolonged to 2 turns) each gas tick. Direct damage that doesn't go through Char.attack (wands, DOTs) does not trigger defenseProc. *Telegraph:* Visible gas cloud after the first hit; description warns of stench. <small>`core/…/mobs/FetidRat.java:78-84`, `core/…/blobs/StenchGas.java:53-65`, `core/…/buffs/Paralysis.java:34`, `core/…/actors/Char.java:483`, `core/…/wands/WandOfMagicMissile.java:56-62`, `core/…/actors/Char.java:390`, `core/…/actors/Char.java:486`</small>
- **Seeks the hero.** Starts WANDERING; picks the closer-to-hero of two wander destinations. *Telegraph:* none <small>`core/…/mobs/FetidRat.java:47-48`, `core/…/mobs/FetidRat.java:93-106`</small>
- **Inflicts Ooze.** 1/3 chance per landed hit, 20 turns; water washes it off. <small>`core/…/mobs/FetidRat.java:64-76`, `core/…/buffs/Ooze.java:30-37`, `core/…/buffs/Ooze.java:90-120`</small>
- **Inflicts Paralysis (via Stench Gas).** 2 turns per gas tick while in the cloud; paralysis also halves hero evasion. Taking damage while paralysed can break it early (the chance grows with damage taken relative to current HP). <small>`core/…/blobs/StenchGas.java:61`, `core/…/hero/Hero.java:597-599`, `core/…/actors/Char.java:949-951`, `core/…/buffs/Paralysis.java:51-63`</small>
- **Immune.** Stench Gas; AllyBuff and Dread (MINIBOSS) <small>`core/…/mobs/FetidRat.java:108-110`, `core/…/actors/Char.java:1416-1417`</small>
- **AI.** Starts `wandering`; flees: never. Spawned when you accept the Sad Ghost's quest on depth 2 (quest type = depth-1). HT 20, acc 12, dmg 1-4 (Rat), def 5, DR 0-3, MINIBOSS, DEMONIC. <small>`core/…/npcs/Ghost.java:170-189`, `core/…/npcs/Ghost.java:304-316`, `core/…/mobs/FetidRat.java:39-62`</small>
- **Evasion.** defenseSkill 5; evasive: no. Hit chance for an accuracy roll A vs evasion D (both uniform) is A/(2D) when A&lt;=D, else 1-D/(2A) (derived from core/…/actors/Char.java:646-684). Hero lvl2-3 (acc 11-12) vs 5: ~77-79%. <small>`core/…/mobs/FetidRat.java:43`, `core/…/actors/Char.java:619-685`</small>
- **Surprise.** Can be surprised: yes. Wandering until it notices you; a hit before it notices is guaranteed. <small>`core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/mobs/Mob.java:1237`, `core/…/mobs/Mob.java:1298`, `core/…/mobs/Mob.java:1327`, `core/…/mobs/FetidRat.java:47-48`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `keep-away`; ranged attacks first: yes.
- **Plan.** Engage from range, ideally with wands (no gas). If you must melee, hit once and step back out of the cloud rather than standing in it; stand near water to clear Ooze. Weak bite (1-4), so the danger is only the paralysis lock. <small>`core/…/mobs/FetidRat.java:78-84`, `core/…/wands/WandOfMagicMissile.java:56-62`, `core/…/actors/Char.java:390`, `core/…/actors/Char.java:486`, `core/…/missiles/MissileWeapon.java:215-233`, `core/…/items/Item.java:633-635`, `core/…/mechanics/Ballistica.java:42-51`, `core/…/wands/WandOfPrismaticLight.java:96-100`, `core/…/spells/Smite.java:120-127`, `core/…/spells/Sunray.java:100-105`, `core/…/darts/HolyDart.java:64-67`, `core/…/mobs/Mob.java:783-793`, `core/…/spells/GuidingLight.java:86-91`, `core/…/spells/ClericSpell.java:139-146`</small>
- **Kill or nullify: Kill it at range.** The gas is seeded on the rat's tile when it's hit; being adjacent means standing in the densest part of the cloud and getting paralysed while it bites. (derived) <small>`core/…/mobs/FetidRat.java:78-84`, `core/…/blobs/StenchGas.java:53-65`</small>
- **Kill or nullify: Wands avoid the gas entirely.** Wand damage goes straight to damage(), not defenseProc, so no stench is released. (derived) <small>`core/…/wands/WandOfMagicMissile.java:56-62`, `core/…/actors/Char.java:390`, `core/…/actors/Char.java:486`, `core/…/mobs/FetidRat.java:78-84`</small>
- **Kill or nullify: Holy damage.** DEMONIC: Prismatic Light x1.333 and Holy Darts deal bonus damage. Smite (Paladin, subclass from depth 10) and Sunray (tier-2 talent, level 7+) are not available at this quest depth. (derived) <small>`core/…/mobs/FetidRat.java:51`, `core/…/wands/WandOfPrismaticLight.java:96-100`, `core/…/spells/Smite.java:120-127`, `core/…/spells/Sunray.java:100-105`, `core/…/darts/HolyDart.java:64-67`, `core/…/spells/ClericSpell.java:121-146`, `core/…/hero/Talent.java:435-436`</small>
- **Kill or nullify: Fight near water.** Ooze is washed off in water. (derived) <small>`core/…/buffs/Ooze.java:93-94`</small>
- **Kill or nullify: Back out of the cloud.** Paralysis only applies while you stand in gas; retreat from the cloud after each hit and let it come to you. (derived) <small>`core/…/blobs/StenchGas.java:53-65`</small>
- **Escape.** Outrunnable: no; contact breaks at: door, out-of-sight, stairs. Same speed; it keeps choosing wander targets near you, so it will find you again. <small>`core/…/mobs/Mob.java:1355`, `core/…/mobs/Mob.java:1302`, `core/…/actors/Char.java:175`, `core/…/actors/Char.java:770-783`, `core/…/mobs/Mob.java:1327`, `core/…/mobs/Mob.java:1342-1360`, `core/…/mobs/Mob.java:1400-1405`, `core/…/mobs/Mob.java:1686-1705`, `core/…/hero/Hero.java:1438`, `core/…/mobs/FetidRat.java:93-106`</small>

| Class | Note |
|---|---|
| Warrior | Throwing stones from range; if melee, hit-and-step-back out of the gas. |
| Mage | Magic Missile: no gas at all. |
| Rogue | Surprise throwing knife opener, then knives from range. |
| Huntress | Spirit bow from max range; ideal matchup. |
| Duelist | Throwing spikes; avoid lunging into the cloud. |
| Cleric | Guiding Light: direct damage, so no gas is released. Smite is not available this early (Paladin subclass). |

**Open questions.**

- Exact spread radius/decay of a 20-volume Stench Gas seed (Blob.evolve) not traced; 'range 3+' is a safe margin, not a computed bound.

### Community notes

- **Tier F.** “<del>Fetid Rat surrounds itself with paralytic gas; standing next to it for more than one turn paralyzes you and it will kill you while paralyzed.</del>” [source](https://pixeldungeon.fandom.com/wiki/Fetid_rat) (version: old wiki page (flagged oldVersion)). There is no passive aura. The rat seeds 20 Stench Gas on its own tile only when a hit lands on it (defenseProc); an unhit rat emits nothing, and wand or DOT damage releases none. Once gas exists, anyone standing in it gets 2 turns of paralysis per tick, and its bite is only 1-4, so the danger is being locked while it chews. (Part of a compound claim.) <small>`core/…/mobs/FetidRat.java:78-84`, `core/…/blobs/StenchGas.java:53-65`</small>
- **Tier 1.** “In melee, let it move to you, hit once, then back away immediately so its gas cloud doesn't engulf you; don't chase it into the cloud.” [source](https://pixeldungeon.fandom.com/wiki/Fetid_rat) (version: old wiki page (flagged oldVersion)). Consistent with code: each landed hit seeds gas on the rat's tile, and paralysis only applies to characters standing in gas cells. Better still, fight it with wands or from range. (Part of a compound claim.) <small>`core/…/mobs/FetidRat.java:78-84`, `core/…/blobs/StenchGas.java:53-65`</small>

??? note "Corrected during verification (2)"

    - Cleric note and Holy damage counter: Smite needs the Paladin subclass (Tengu's Mask, depth 10) and Sunray is tier 2, so neither exists at a depth 2 quest.
    - Added that damage can break the stench paralysis early (Paralysis.processDamage).

## Gnoll trickster {#gnolltrickster}

`actors.mobs.GnollTrickster` · depths quest (Sad Ghost, depth 3) · **Tactic:** Opposite of instinct: don't shoot it from afar, run it down. Approach using cover (doors, corners, other mobs) to cut its line; once adjacent it cannot hurt you and will back away, so herd it into a corner or dead end...

**Stats** (from the [Codex](../codex/mobs.md)): HT 20 · accuracy 16 · evasion 5 · damage 1-6 · armour 0-2 · EXP 5 · max level 8 · properties MINIBOSS · loot MISSILE (100% base). <small>`core/…/mobs/GnollTrickster.java:43`, `core/…/mobs/GnollTrickster.java:66`, `core/…/mobs/Gnoll.java:45`, `core/…/mobs/Gnoll.java:55`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** baseSpeed 1. While hunting, its 'approach' step is always a step AWAY from you (getFurther), and only if it currently sees you. Out of sight it cannot close in, gives up ('lost') and goes back to wandering. Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/mobs/GnollTrickster.java:106-114`, `core/…/mobs/Mob.java:1355`, `core/…/mobs/Mob.java:1302`, `core/…/actors/Char.java:175`, `core/…/actors/Char.java:770-783`, `core/…/levels/Terrain.java:90-91`, `core/…/levels/Level.java:1212`, `core/…/levels/Level.java:1257-1258`, `core/…/features/Door.java:35-59`, `core/…/actors/Char.java:1311-1313`, `core/…/mobs/Mob.java:1686-1705`, `core/…/hero/Hero.java:1438`, `core/…/mobs/Mob.java:1387-1406`</small>
- **Attack.** `ranged`, reach 99. Only when NOT adjacent, and a PROJECTILE Ballistica from it reaches you (no character or solid cell/closed door in between); you must also be in its view. Attack time: 1 turn. <small>`core/…/mobs/GnollTrickster.java:70-74`, `core/…/mechanics/Ballistica.java:47`, `core/…/mobs/Mob.java:1328`, `core/…/mobs/Mob.java:753-757`, `core/…/mobs/Mob.java:774-779`</small>
- **Escalating darts (combo).** Each landed dart raises combo by 1; effect = Random.Int(4) + combo. Effect 3+: Poison(effect-2), except that at effect 6+ with no Burning already on you it sets you on fire instead (and seeds fire on a flammable tile). So at effect 6+ while already burning you get Poison again. Burning needs combo 3+. Combo resets to 0 whenever it tries to move. *Telegraph:* Dart projectile animation; no pre-attack warning. <small>`core/…/mobs/GnollTrickster.java:76-104`, `core/…/mobs/GnollTrickster.java:107-108`, `core/…/sprites/GnollTricksterSprite.java:61-81`</small>
- **Keeps distance.** While hunting and adjacent it cannot attack and steps away instead; if it cannot step away it just waits (no attack). *Telegraph:* It backs off. <small>`core/…/mobs/GnollTrickster.java:70-74`, `core/…/mobs/GnollTrickster.java:106-114`, `core/…/mobs/Mob.java:1387-1406`</small>
- **Seeks the hero.** Starts WANDERING toward hero-near destinations; can only be aggroed by characters it can see. *Telegraph:* none <small>`core/…/mobs/GnollTrickster.java:53-54`, `core/…/mobs/GnollTrickster.java:116-124`, `core/…/mobs/GnollTrickster.java:147-160`</small>
- **Retreats when its line is blocked; loses you out of sight.** If it sees you but its dart line is blocked (another character in the way), it still cannot attack and steps away rather than repositioning. If it cannot see you at all, it cannot approach and drops to wandering (toward destinations near you). *Telegraph:* It backs off; the 'lost' mark when it gives up. <small>`core/…/mobs/GnollTrickster.java:70-74`, `core/…/mobs/GnollTrickster.java:106-114`, `core/…/mobs/Mob.java:1342-1360`, `core/…/mobs/Mob.java:1387-1406`, `core/…/mobs/GnollTrickster.java:147-160`</small>
- **Inflicts Poison.** Poison set(effect-2): deals left/3+1 per turn. <small>`core/…/mobs/GnollTrickster.java:98-100`, `core/…/buffs/Poison.java:109-127`</small>
- **Inflicts Burning.** At effect &gt;=6 (combo &gt;=3); water extinguishes; burning can destroy scrolls/meat in pack. <small>`core/…/mobs/GnollTrickster.java:91-97`, `core/…/buffs/Burning.java:97-186`</small>
- **Immune.** AllyBuff and Dread (MINIBOSS) <small>`core/…/mobs/GnollTrickster.java:60`, `core/…/actors/Char.java:1416-1417`</small>
- **AI.** Starts `wandering`; flees: never (retreats only to keep range). Spawned by the Sad Ghost quest on depth 3. HT 20, acc 16, dmg 1-6, def 5, DR 0-2, MINIBOSS; drops a missile weapon. <small>`core/…/npcs/Ghost.java:170-189`, `core/…/npcs/Ghost.java:304-316`, `core/…/mobs/GnollTrickster.java:45-68`</small>
- **Evasion.** defenseSkill 5; evasive: no. Hit chance for an accuracy roll A vs evasion D (both uniform) is A/(2D) when A&lt;=D, else 1-D/(2A) (derived from core/…/actors/Char.java:646-684). Hero lvl3 (acc 12) vs 5: ~79%. <small>`core/…/mobs/GnollTrickster.java:49`, `core/…/actors/Char.java:619-685`</small>
- **Surprise.** Can be surprised: yes. Wandering until it notices you. <small>`core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/mobs/Mob.java:1237`, `core/…/mobs/Mob.java:1298`, `core/…/mobs/Mob.java:1327`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `close`; ranged attacks first: no.
- **Plan.** Opposite of instinct: don't shoot it from afar, run it down. Approach using cover (doors, corners, other mobs) to cut its line; once adjacent it cannot hurt you and will back away, so herd it into a corner or dead end and melee it. Stand near water in case of Burning. <small>`core/…/mobs/GnollTrickster.java:70-114`, `core/…/artifacts/CloakOfShadows.java:306`, `core/…/actors/Char.java:627-630`, `core/…/mobs/Mob.java:875`, `core/…/melee/Rapier.java:92-151`, `core/…/mobs/Mob.java:783-793`, `core/…/spells/GuidingLight.java:86-91`, `core/…/wands/WandOfMagicMissile.java:56-62`, `core/…/actors/Char.java:390`, `core/…/actors/Char.java:486`, `core/…/hero/Hero.java:481`, `core/…/hero/Hero.java:2368`, `core/…/buffs/Invisibility.java:90-98`, `core/…/artifacts/CloakOfShadows.java:360-366`, `core/…/mobs/Mob.java:290`</small>
- **Kill or nullify: Get adjacent and stay adjacent.** It literally cannot attack an adjacent target; each step it takes away resets its combo. (derived) <small>`core/…/mobs/GnollTrickster.java:70-74`, `core/…/mobs/GnollTrickster.java:106-114`</small>
- **Kill or nullify: Corner it.** When it can't step away while adjacent, it waits instead of attacking: a cornered trickster is harmless. (derived) <small>`core/…/mobs/GnollTrickster.java:110`, `core/…/mobs/Mob.java:1387-1406`</small>
- **Kill or nullify: Break line of fire.** Its dart needs a clear PROJECTILE line. A closed door or corner breaks sight entirely, and it then gives up the hunt and wanders; another character in the line makes it step back (combo reset) instead of shooting. (derived) <small>`core/…/mobs/GnollTrickster.java:72-73`, `core/…/mechanics/Ballistica.java:47`, `core/…/levels/Terrain.java:90`, `core/…/mobs/Mob.java:1387-1406`</small>
- **Kill or nullify: Stand in or near water.** Water ends Burning. (derived) <small>`core/…/buffs/Burning.java:99-100`</small>
- **Kill or nullify: Out-range it only if you win the trade fast.** Its accuracy 16 is high; a long ranged duel lets combo build. (derived) <small>`core/…/mobs/GnollTrickster.java:66-68`, `core/…/mobs/GnollTrickster.java:85-87`</small>
- **Escape.** Outrunnable: no; contact breaks at: door, out-of-sight, stairs. It wants distance anyway; break line of fire with a door or corner. <small>`core/…/mobs/Mob.java:1327`, `core/…/mobs/Mob.java:1342-1360`, `core/…/mobs/Mob.java:1400-1405`, `core/…/mobs/GnollTrickster.java:72-73`, `core/…/mobs/Mob.java:1686-1705`, `core/…/hero/Hero.java:1438`</small>

| Class | Note |
|---|---|
| Warrior | Close in behind cover (doors, corners, other mobs), then melee it once it is cornered. |
| Mage | Magic Missile never misses but it shoots back at accuracy 16 and 20 HP takes several zaps; closing to adjacency forces it to retreat instead of shooting, so herd it into a corner and use the staff. |
| Rogue | Cloak invisibility: it cannot see you, so it cannot shoot or keep its distance while you close in; the first hit is a surprise, then the cloak drops. |
| Huntress | Bow duel is risky vs acc 16 + combo; still close and use gloves, or shoot only when it just moved (combo 0). |
| Duelist | Rapier lunge needs the target exactly 2 tiles away: step 1 and strike with effectively infinite accuracy in one action. Perfect right after it steps back from you. |
| Cleric | Close to melee; Guiding Light as a finisher. |

### Community notes

No community claim about this enemy was found.

??? note "Corrected during verification (5)"

    - Combo effect: effect 6+ while already burning gives Poison(effect-2), not nothing; combo resets on any getCloser call.
    - Break-line-of-fire counter clarified: out of sight it abandons the hunt; blocked line makes it retreat.
    - Warrior note: removed unsupported 'seal shield absorbs the darts' claim.
    - Duelist note: the lunge moves 1 tile and needs the target at distance exactly 2, not 'closes 2 tiles'.
    - Rogue note: the cloak gives one surprise hit (attacking dispels it), not every hit.

## Great crab {#greatcrab}

`actors.mobs.GreatCrab` · depths quest (Sad Ghost, depth 4) · **Tactic:** Never trade in the open: every hit and zap is blocked while it watches you.

**Stats** (from the [Codex](../codex/mobs.md)): HT 25 · accuracy 12 · evasion 0 · damage 1-7 · armour 0-4 · EXP 6 · max level 9 · properties MINIBOSS · loot MysteryMeat (100% base). <small>`core/…/mobs/GreatCrab.java:40`, `core/…/mobs/Crab.java:51`, `core/…/mobs/Crab.java:46`, `core/…/mobs/Crab.java:56`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** baseSpeed 1, but every 3rd getCloser call spends the turn without moving: ~2 tiles per 3 turns. Speed `slow`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/mobs/GreatCrab.java:47`, `core/…/mobs/GreatCrab.java:60-73`, `core/…/mobs/Mob.java:1355`, `core/…/mobs/Mob.java:1302`, `core/…/actors/Char.java:175`, `core/…/actors/Char.java:770-783`, `core/…/levels/Terrain.java:90-91`, `core/…/levels/Level.java:1212`, `core/…/levels/Level.java:1257-1258`, `core/…/features/Door.java:35-59`, `core/…/actors/Char.java:1311-1313`, `core/…/mobs/Mob.java:1686-1705`, `core/…/hero/Hero.java:1438`</small>
- **Attack.** `melee`, reach 1. Adjacent only. Attack time: 1 turn. <small>`core/…/mobs/Mob.java:558-568`, `core/…/mobs/Mob.java:753-757`, `core/…/mobs/Mob.java:774-779`</small>
- **Claw block (attacks).** Infinite evasion against its CURRENT target whenever it has seen that target, isn't asleep or paralysed, and the target isn't invisible. Infinite evasion beats infinite accuracy (lunges, etc.). *Telegraph:* 'blocked' status + log 'The crab blocks with its massive claw.' <small>`core/…/mobs/GreatCrab.java:94-112`, `core/…/actors/Char.java:636-640`, `core/…/actors/actors.properties:1706-1707`</small>
- **Claw block (wands/cleric spells).** Negates direct damage from Wands and ClericSpells while its current target is the hero, under the same seeing conditions (enemySeen, not asleep, not paralysed, hero not invisible). Add-on effects and environmental damage go through. A blocked zap does not reach Mob.damage, so it does not wake or alert the crab either. *Telegraph:* same message <small>`core/…/mobs/GreatCrab.java:75-92`</small>
- **Immune.** AllyBuff and Dread (MINIBOSS) <small>`core/…/mobs/GreatCrab.java:57`, `core/…/actors/Char.java:1416-1417`</small>
- **AI.** Starts `wandering`; flees: never. Spawned by the Sad Ghost quest on depth 4. HT 25, acc 12, dmg 1-7, def 0 (when not blocking), DR 0-4. The description itself says it cannot block unseen attacks or attacks from multiple enemies. <small>`core/…/npcs/Ghost.java:170-189`, `core/…/npcs/Ghost.java:304-316`, `core/…/mobs/GreatCrab.java:42-58`, `core/…/actors/actors.properties:1709`</small>
- **Evasion.** defenseSkill 0; evasive: yes. 0 evasion when its block conditions fail, infinite when they hold. <small>`core/…/mobs/GreatCrab.java:46`, `core/…/mobs/GreatCrab.java:94-112`</small>
- **Surprise.** Can be surprised: yes. Surprise = the block's own conditions failing (enemySeen false / hero invisible): every surprise hit lands. <small>`core/…/mobs/GreatCrab.java:97-101`, `core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/mobs/Mob.java:1237`, `core/…/mobs/Mob.java:1298`, `core/…/mobs/Mob.java:1327`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `surprise`; ranged attacks first: no.
- **Plan.** Never trade in the open: every hit and zap is blocked while it watches you. Use its slowness: break sight behind a door or corner, wait beside the door, hit it as it steps through (guaranteed), then retreat out of sight and repeat. Otherwise hit it from invisibility, with an ally tanking, or with bombs/fire/poison. <small>`core/…/mobs/GreatCrab.java:60-112`, `core/…/actors/Char.java:636-640`, `core/…/artifacts/CloakOfShadows.java:306`, `core/…/actors/Char.java:627-630`, `core/…/mobs/Mob.java:875`, `core/…/melee/Dagger.java:62-78`, `core/…/missiles/ThrowingKnife.java:50-66`, `core/…/melee/Rapier.java:92-151`, `core/…/hero/Hero.java:481`, `core/…/hero/Hero.java:2368`, `core/…/buffs/Invisibility.java:90-98`, `core/…/artifacts/CloakOfShadows.java:360-366`</small>
- **Kill or nullify: Door ambush / hit it before it sees you.** Block needs enemySeen; a crab that steps through a door into view hasn't 'seen' you until its next act. (derived) <small>`core/…/mobs/GreatCrab.java:97`, `core/…/mobs/Mob.java:1327`</small>
- **Kill or nullify: Hit-and-hide.** It's slower than you: step out of its sight (door/corner), let it come, strike as it appears, repeat. (derived) <small>`core/…/mobs/GreatCrab.java:60-73`, `core/…/mobs/Mob.java:1327`, `core/…/mobs/Mob.java:1342-1360`, `core/…/mobs/Mob.java:1400-1405`</small>
- **Kill or nullify: Second attacker.** Block only applies against its current target; an ally (e.g. Mirror Image) or you, while it targets the ally, hits normally. (derived) <small>`core/…/mobs/GreatCrab.java:100`</small>
- **Kill or nullify: Invisibility or paralysis.** Both disable the block. (derived) <small>`core/…/mobs/GreatCrab.java:81-84`, `core/…/mobs/GreatCrab.java:99-101`</small>
- **Kill or nullify: Non-wand damage sources.** Bombs, fire, poison, gas and traps are neither attacks (no evasion roll) nor Wand/ClericSpell damage. (derived) <small>`core/…/mobs/GreatCrab.java:75-92`</small>
- **Escape.** Outrunnable: yes; contact breaks at: door, out-of-sight, stairs. About 2/3 hero speed; you can always walk away. <small>`core/…/mobs/GreatCrab.java:60-73`, `core/…/mobs/Mob.java:1355`, `core/…/mobs/Mob.java:1302`, `core/…/actors/Char.java:175`, `core/…/actors/Char.java:770-783`, `core/…/mobs/Mob.java:1686-1705`, `core/…/hero/Hero.java:1438`</small>

| Class | Note |
|---|---|
| Warrior | Door ambush loop with the shortsword. |
| Mage | Wand zaps are negated while it sees you; ambush with the staff, or zap only from surprise. |
| Rogue | Cloak of Shadows: a hit from invisibility bypasses the block and is a surprise hit (dagger 75%-max), but attacking drops the cloak, so it is one hit per activation. |
| Huntress | Arrows are blocked while it sees you; shoot only when it can't see you (sleeping is impossible, it starts wandering) or from ambush. |
| Duelist | Lunge's infinite accuracy still loses to infinite evasion; ambush only. |
| Cleric | Guiding Light is negated while it sees you; ambush with the cudgel. |

### Community notes

No community claim about this enemy was found.

??? note "Corrected during verification (2)"

    - Wand/spell block applies only while its current target is the hero (GreatCrab.java:83).
    - Rogue note: invisible hits are one per cloak activation.
