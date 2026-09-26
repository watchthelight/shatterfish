# Bestiary: Bosses and their parts

The five bosses and every part of their fights: pylons, summons, fists. Each boss is also
listed on its region page's index line. Mechanics are Tier 1 (read from the pinned code, cited),
tactics are derived from them, and community claims carry their tier. The rules these cards
lean on are on each region page and summarised on the [bestiary index](index.md).

## Goo (depth 5)

Region page: [Sewers (depths 1-4)](sewers.md).

### Goo {#goo}

`actors.mobs.Goo` · depths boss 5 · **Tactic:** Arrive healthy with healing. Open with a guaranteed ranged hit on the sleeping Goo.

**Stats** (from the [Codex](../codex/mobs.md)): HT 100 · accuracy `attack` · evasion 8 · damage `Random.NormalIntRange( min\*3, max\*3 ); \| Random.NormalIntRange( min, max )` · armour 0-2 · EXP 10 · max level 29 · properties ACIDIC, BOSS, DEMONIC. <small>`core/…/mobs/Goo.java:51`, `core/…/mobs/Goo.java:84`, `core/…/mobs/Goo.java:68`, `core/…/mobs/Goo.java:97`</small>

#### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** baseSpeed 1. Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/mobs/Mob.java:1355`, `core/…/mobs/Mob.java:1302`, `core/…/actors/Char.java:175`, `core/…/actors/Char.java:770-783`</small>
- **Attack.** `melee`, reach 2. Normal attack: adjacent. Pumped attack: distance &lt;=2 AND a STOP_TARGET\|STOP_SOLID\|IGNORE_SOFT_SOLID Ballistica clear both from Goo to you and from you to Goo (walls/pillars block; characters do not). Single target only. Attack time: 1 turn; pump-up turns also cost 1. <small>`core/…/mobs/Goo.java:141-152`, `core/…/mobs/Goo.java:178-228`, `core/…/mechanics/Ballistica.java:42-45`, `core/…/mobs/Mob.java:753-757`, `core/…/mobs/Mob.java:774-779`</small>
- **Pump-up (charged attack).** When it could attack you (adjacent), it pumps up instead with chance 1/5 (1/2 at or below half HP). From then on its reach is 2 with a clear line both ways. On its next turn in reach it charges to level 2 (no damage); on the turn after, it strikes at x2 accuracy for 3x damage (3-24, 3-36 enraged). The strike can still miss. If it has to move instead, or leaves HUNTING, the charge is lost. Stronger Bosses: +2 at once, so only one warning turn. *Telegraph:* '!!!' over Goo, log 'Goo is pumping itself up!', charge-up sound, pump animation, and black goo particles on the cells it could reach at the current pump level: radius 1 at level 1 (although the level-2 charge step already works at distance 2), radius 2 at level 2. <small>`core/…/mobs/Goo.java:67-89`, `core/…/mobs/Goo.java:178-228`, `core/…/mobs/Goo.java:243-259`, `core/…/sprites/GooSprite.java:93-121`, `core/…/actors/actors.properties:1698-1699`, `core/…/mobs/Goo.java:104-107`</small>
- **Enrage at half HP.** At HP &lt;= 50%: damage max 8-&gt;12, accuracy 10-&gt;15, defense x1.5, pump chance 1/5-&gt;1/2. *Telegraph:* 'enraged' status, yell 'GLUUUURP!', health bar bleeds, goo spray. <small>`core/…/mobs/Goo.java:67-94`, `core/…/mobs/Goo.java:187`, `core/…/mobs/Goo.java:267-274`, `core/…/actors/actors.properties:1700-1701`</small>
- **Heals in water.** Each act on a water tile while hurt heals +1 HP (up to +3 ramping with Stronger Bosses) and shortens your regen lock timer. *Telegraph:* green '+1' heal numbers on Goo. <small>`core/…/mobs/Goo.java:109-132`</small>
- **Seals the arena.** Once awake (or damaged) the level locks: the entrance becomes water and stairs cannot be used until Goo dies. LockedFloor stops hero regeneration once its timer runs out (starts at 50, or 20 with Stronger Bosses, capped at 50); damaging Goo adds time (x1.5), and Goo healing in water removes time. *Telegraph:* Boss music, boss health bar, yell 'GLURP-GLURP!'. <small>`core/…/mobs/Goo.java:134-136`, `core/…/mobs/Goo.java:261-280`, `core/…/mobs/Goo.java:311-324`, `core/…/levels/SewerBossLevel.java:177-195`, `core/…/hero/Hero.java:1438`, `core/…/buffs/LockedFloor.java:31-59`, `core/…/buffs/Regeneration.java:113-118`</small>
- **Inflicts Ooze.** 1/3 chance per landed hit, 20 turns; at depth 5 deals 1 per turn; washes off in water. <small>`core/…/mobs/Goo.java:154-160`, `core/…/buffs/Ooze.java:30-37`, `core/…/buffs/Ooze.java:90-120`</small>
- **Immune.** Ooze (ACIDIC); AllyBuff, Dread (BOSS) <small>`core/…/mobs/Goo.java:59-61`, `core/…/actors/Char.java:1414-1415`, `core/…/actors/Char.java:1427-1428`</small>
- **Resists.** Corrosion (ACIDIC); Grim, Grim trap, Scroll of Retribution, Psionic Blast (BOSS) <small>`core/…/actors/Char.java:1414-1415`, `core/…/actors/Char.java:1427-1428`</small>
- **AI.** Starts `sleeping`; flees: never. Placed at the arena centre with default SLEEPING state. HT 100 (120 Stronger Bosses), def 8, DR 0-2, dmg 1-8, acc 10 (codex). DEMONIC. <small>`core/…/sewerboss/ThickPillarsGooRoom.java:54-56`, `core/…/mobs/Mob.java:124`, `core/…/mobs/Goo.java:53-62`</small>
- **Evasion.** defenseSkill 8; evasive: no. Hit chance for an accuracy roll A vs evasion D (both uniform) is A/(2D) when A&lt;=D, else 1-D/(2A) (derived from core/…/actors/Char.java:646-684). Hero lvl8 (acc 17) vs 8: ~76%; vs enraged 12: ~65%. <small>`core/…/mobs/Goo.java:56`, `core/…/mobs/Goo.java:91-94`, `core/…/actors/Char.java:619-685`</small>
- **Surprise.** Can be surprised: yes. Asleep at the start: the opening ranged hit is guaranteed (and the fight begins). <small>`core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/mobs/Mob.java:1237`, `core/…/mobs/Mob.java:1298`, `core/…/mobs/Mob.java:1327`, `core/…/mobs/Goo.java:261-266`</small>

#### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `close`; ranged attacks first: yes.
- **Plan.** Arrive healthy with healing. Open with a guaranteed ranged hit on the sleeping Goo. Fight it on dry ground in melee; watch for '!!!' / 'pumping itself up': immediately walk 3 tiles away or behind a pillar, then re-engage once the charge is wasted. Below half HP it pumps half the time and hits harder: be ready to disengage every other turn. Wash Ooze off in water, but never let Goo sit in water. <small>`core/…/mobs/Goo.java:141-228`, `core/…/missiles/MissileWeapon.java:215-233`, `core/…/items/Item.java:633-635`, `core/…/mechanics/Ballistica.java:42-51`, `core/…/wands/WandOfMagicMissile.java:56-62`, `core/…/actors/Char.java:390`, `core/…/actors/Char.java:486`, `core/…/melee/Dagger.java:62-78`, `core/…/missiles/ThrowingKnife.java:50-66`, `core/…/artifacts/CloakOfShadows.java:306`, `core/…/actors/Char.java:627-630`, `core/…/mobs/Mob.java:875`, `core/…/melee/Rapier.java:92-151`, `core/…/wands/WandOfPrismaticLight.java:96-100`, `core/…/spells/Smite.java:120-127`, `core/…/spells/Sunray.java:100-105`, `core/…/darts/HolyDart.java:64-67`, `core/…/mobs/Mob.java:783-793`, `core/…/spells/GuidingLight.java:86-91`, `core/…/hero/Hero.java:481`, `core/…/hero/Hero.java:2368`, `core/…/buffs/Invisibility.java:90-98`, `core/…/artifacts/CloakOfShadows.java:360-366`, `core/…/spells/ClericSpell.java:139-146`, `core/…/items/TengusMask.java:73`</small>
- **Kill or nullify: Step out of the pump.** After the warning you get two actions before the strike: move so you end &gt;2 tiles away or behind a pillar/wall (the particles show exactly the danger cells). Goo then has to move and loses the charge. (derived) <small>`core/…/mobs/Goo.java:141-152`, `core/…/mobs/Goo.java:178-186`, `core/…/mobs/Goo.java:243-250`, `core/…/sprites/GooSprite.java:102-121`</small>
- **Kill or nullify: Keep Goo out of water.** It heals every turn it stands in water, and it starts on water at the room centre in every layout (a central pool in the diamond and walled rooms, water-filled floor in the pillar rooms). Pull it onto dry tiles: the dry perimeter paths in pillar rooms, away from the central pool otherwise. Even a sleeping Goo heals on water. (derived) <small>`core/…/mobs/Goo.java:109-132`, `core/…/sewerboss/ThickPillarsGooRoom.java:35-46`, `core/…/sewerboss/DiamondGooRoom.java:59-60`, `core/…/sewerboss/ThinPillarsGooRoom.java:36`, `core/…/sewerboss/ThinPillarsGooRoom.java:57`, `core/…/sewerboss/WalledGooRoom.java:57-58`, `core/…/sewerboss/DiamondGooRoom.java:64-65`, `core/…/sewerboss/ThickPillarsGooRoom.java:54-55`</small>
- **Kill or nullify: You in water is fine.** Water removes your Ooze; only Goo standing in water is bad. (derived) <small>`core/…/buffs/Ooze.java:93-94`</small>
- **Kill or nullify: Holy damage.** DEMONIC: Prismatic Light x1.333 and Holy Darts deal bonus damage. Smite (Paladin subclass, depth 10) and Sunray (tier-2 talent, level 7+) are normally not available yet. (derived) <small>`core/…/mobs/Goo.java:60`, `core/…/wands/WandOfPrismaticLight.java:96-100`, `core/…/spells/Smite.java:120-127`, `core/…/spells/Sunray.java:100-105`, `core/…/darts/HolyDart.java:64-67`, `core/…/spells/ClericSpell.java:121-146`, `core/…/hero/Talent.java:435-436`</small>
- **Kill or nullify: Don't stall.** Your regen stops when the lock timer runs out; dealing damage keeps it topped up, Goo healing drains it. (derived) <small>`core/…/mobs/Goo.java:113-117`, `core/…/mobs/Goo.java:275-279`, `core/…/buffs/LockedFloor.java:48-58`</small>
- **Kill or nullify: Bring healing; no escape.** Stairs are locked for the whole fight. (derived) <small>`core/…/hero/Hero.java:1438`, `core/…/levels/SewerBossLevel.java:184`</small>
- **Kill or nullify: Scroll of Lullaby.** Goo is not Sleep-immune (BOSS only blocks AllyBuff and Dread). About 5 turns after reading, Drowsy becomes magical sleep: Goo is set to SLEEPING and paralysed, any pump is cancelled, and it stays asleep until damaged, so the next hit is a guaranteed surprise. The reader also gets Drowsy and falls asleep unless at full HP, and a sleeping Goo on water still heals. (derived) <small>`core/…/scrolls/ScrollOfLullaby.java:47-55`, `core/…/buffs/Drowsy.java:46-58`, `core/…/buffs/MagicalSleep.java:37-67`, `core/…/actors/Char.java:1414-1415`, `core/…/mobs/Mob.java:277-282`, `core/…/mobs/Goo.java:104-110`, `core/…/mobs/Mob.java:902-907`</small>
- **Kill or nullify: Toxic gas works.** Goo's properties (BOSS, DEMONIC, ACIDIC) give no ToxicGas immunity or resistance; only INORGANIC is immune. (derived) <small>`core/…/mobs/Goo.java:59-61`, `core/…/actors/Char.java:1414-1428`</small>
- **Kill or nullify: Invisibility cancels a pump.** An invisible hero is never 'in FOV' for Goo, so it cannot attack and has to move, and moving resets the pump. Attacking ends the invisibility. (derived) <small>`core/…/mobs/Mob.java:290`, `core/…/mobs/Goo.java:243-250`, `core/…/hero/Hero.java:481`, `core/…/hero/Hero.java:2368`, `core/…/buffs/Invisibility.java:90-98`</small>
- **Escape.** Outrunnable: no; contact breaks at: none. No escape once the fight starts: level sealed, entrance flooded, stairs refuse. Breaking line with pillars only cancels a pump. <small>`core/…/mobs/Goo.java:134-136`, `core/…/hero/Hero.java:1438`, `core/…/levels/SewerBossLevel.java:177-195`</small>

| Class | Note |
|---|---|
| Warrior | Melee on dry tiles from where you can step 2 tiles clear; always walk out of the pump. |
| Mage | Magic Missile ignores its DR; zap while it approaches, step out on pump. |
| Rogue | Surprise knife/dagger opener on the sleeping Goo. Activating the cloak during a pump cancels it (Goo can't see you and has to move); the first hit after that is a surprise, and attacking drops the cloak. |
| Huntress | Kite: shoot from 3+ tiles (x1.5 acc); Goo only pumps when it can attack, so staying out of reach avoids pumps entirely until it closes. |
| Duelist | Lunge re-engages instantly after you step out of a pump. |
| Cleric | Guiding Light from range, then cudgel on the Illuminated Goo. Smite is a Paladin spell and subclasses come after Tengu, so it is not an option here. |

**Open questions.**

- Stronger Bosses challenge variant only noted, not fully tactically analysed.

#### Community notes

- **Tier 1.** “When Goo starts its pump-up '!!!' animation, immediately move at least 2 tiles away from it — staying in that radius risks a hit that can one-shot an underleveled hero; the attack can also simply miss if you can't escape in time.” [source](https://pixeldungeon.fandom.com/wiki/Goo) (version: unspecified (SPD wiki, no version stated)). Confirmed with a precision fix: the pumped strike reaches distance 2 with a clear line, so you must END 3+ tiles away (two steps from adjacent) or behind a wall. Ending exactly 2 tiles away is still in range. It deals 3x damage (3-24, 3-36 enraged) at x2 accuracy and uses a normal hit roll, so it can miss. <small>`core/…/mobs/Goo.java:141-152`, `core/…/mobs/Goo.java:67-89`, `core/…/actors/Char.java:619-685`</small>
- **Tier 1.** “Goo's regular melee hit has roughly a 1-in-3 chance to inflict ~20 turns of Caustic Ooze; step into water to wash the debuff off, so fighting Goo near or in the flooded room helps a lot.” [source](https://pixeldungeon.fandom.com/wiki/Goo) (version: unspecified (SPD wiki, no version stated)). Confirmed: Random.Int(3)==0 per landed hit (the pumped strike too), Ooze for 20 turns, 1 damage per turn at depth 5, and standing in water washes it off. Caveat: only YOU in water is good; Goo standing in water heals every turn. <small>`core/…/mobs/Goo.java:154-160`, `core/…/buffs/Ooze.java:32`, `core/…/buffs/Ooze.java:90-120`, `core/…/mobs/Goo.java:109-132`</small>
- **Tier F.** “<del>Wand of Slowness is a strong tool against Goo.</del>” [source](https://pixeldungeon.fandom.com/wiki/Goo) (version: unspecified (SPD wiki, no version stated)). There is no Wand of Slowness in SPD v4.0.0 (it is an original Pixel Dungeon item). (Part of a compound claim.) The wands package holds CursedWand, BlastWave, Corrosion, Corruption, Disintegration, Fireblast, Frost, Lightning, LivingEarth, MagicMissile, PrismaticLight, Regrowth, Transfusion and Warding only.
- **Tier 1.** “Scroll of Lullaby is a strong tool against Goo.” [source](https://pixeldungeon.fandom.com/wiki/Goo) (version: unspecified (SPD wiki, no version stated)). Goo is not Sleep-immune; Lullaby's Drowsy turns into magical sleep that cancels a pump and lasts until Goo is damaged. Caveat: the reader also gets Drowsy. (Part of a compound claim.) <small>`core/…/scrolls/ScrollOfLullaby.java:47-55`, `core/…/buffs/MagicalSleep.java:37-67`, `core/…/actors/Char.java:1414-1415`</small>
- **Tier 1.** “Potion of Toxic Gas (paired with Invisibility so you avoid the gas yourself) is a strong tool against Goo.” [source](https://pixeldungeon.fandom.com/wiki/Goo) (version: unspecified (SPD wiki, no version stated)). Goo has no ToxicGas immunity or resistance (BOSS/DEMONIC/ACIDIC). (Part of a compound claim.) <small>`core/…/mobs/Goo.java:59-61`, `core/…/actors/Char.java:1414-1428`</small>
- **Tier 3 (unverified).** “Scroll of Mirror Image is a strong tool against Goo.” [source](https://pixeldungeon.fandom.com/wiki/Goo) (version: unspecified (SPD wiki, no version stated)). Plausible (images are allies that draw and deal hits), but how effective they are is not a code fact. (Part of a compound claim.)
- **Tier 1.** “As Rogue, using Cloak of Shadows while Goo is pumping up cancels its attack outright.” [source](https://pixeldungeon.fandom.com/wiki/Goo) (version: unspecified (SPD wiki, no version stated)). An invisible hero is never in Goo's FOV, so it cannot attack and must move, and getCloser resets pumpedUp to 0. (Part of a compound claim.) <small>`core/…/mobs/Mob.java:290`, `core/…/mobs/Goo.java:243-250`</small>

??? note "Corrected during verification (7)"

    - Bestiary build: empty break-contact list recorded as 'none' (the floor is sealed for the fight).
    - Pump description: reach 2 applies from pump level 1 (the level-2 charge step), although the level-1 telegraph only shows radius 1; the strike can still miss.
    - LockedFloor timer is 20 (not 50) with Stronger Bosses, capped at 50.
    - Keep-out-of-water counter: Goo starts on water at the centre in all four room layouts; re-cited the room code.
    - Cleric note was wrong: Smite needs the Paladin subclass (Tengu's Mask, depth 10). Holy damage counter caveated the same way.
    - Warrior note: removed the unsupported seal-shield claim. Rogue note: one surprise per cloak activation.
    - Added counters confirmed from community lore: Lullaby (not Sleep-immune), toxic gas (no immunity), invisibility cancels a pump.

## Tengu (depth 10)

Region page: [Prison (depths 6-9)](prison.md).

### Tengu {#tengu}

`actors.mobs.Tengu` · depths boss 10 · **Tactic:** Phase 1: after each jump, note the flashed traps, walk the trap-free path to Tengu and fight him adjacent, where his accuracy halves, until he hits 100 HP.

**Stats** (from the [Codex](../codex/mobs.md)): HT 200 · accuracy `10; \| 20` · evasion 15 · damage 6-12 · armour 0-5 · EXP 20 · max level 29 · properties BOSS. <small>`core/…/mobs/Tengu.java:85`, `core/…/mobs/Tengu.java:107`, `core/…/mobs/Tengu.java:102`, `core/…/mobs/Tengu.java:116`</small>

#### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** Never walks: its Hunting state has no movement step. It changes position only by jumping (teleporting) when it loses an HP bracket. Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: no; follows you on the stairs: no. <small>`core/…/mobs/Tengu.java:384-429`, `core/…/mobs/Tengu.java:240-328`</small>
- **Attack.** `ranged`, reach 99. Attacks at any distance within its view (viewDistance 12) along a PROJECTILE line (stopped by walls, doors and characters), throwing shurikens when not adjacent. Accuracy is 10 when adjacent and 20 at range. An invisible hero is not attackable (enemyInFOV false). Attack time: 1 turn. Damage: 6-12, accuracy 10 adjacent / 20 at range. <small>`core/…/mobs/Tengu.java:98`, `core/…/mobs/Tengu.java:101-113`, `core/…/mobs/Tengu.java:235-238`, `core/…/sprites/TenguSprite.java:93-112`, `core/…/mobs/Mob.java:290`</small>
- **HP-bracket jump.** HP is split into 8 brackets of HT/8 (25; 31 with Stronger Bosses). A hit cannot push HP below 1 above the top of the next lower bracket, so one hit removes at most 24-48 HP depending on where in the bracket it lands. Every bracket change triggers a jump after the attack resolves; the first damage at full HP already causes one. *Telegraph:* none; wool puff at the old and new positions <small>`core/…/mobs/Tengu.java:139-151`, `core/…/mobs/Tengu.java:186-203`</small>
- **Phase 1: cell jumps and hidden dart traps.** In the prison cell (FIGHT_START) each jump moves it to a random cell of the room more than 3.5 tiles from the hero and re-rolls hidden Tengu dart traps over 40-90% of the room's 7x7 interior (fill grows as his HP falls). Occupied cells get no trap, and the layout always leaves a trap-free path from Tengu to the hero, deliberately winding: its length is between ceil(7\*fill) and ceil(4+4\*fill) steps. The traps flash visible and fade after about 2 seconds. A triggered dart hits the character standing on the trap (else the nearest targetable one in line), never Tengu, for 4-8 minus DR plus Poison 8 (about 17 total). *Telegraph:* traps are drawn briefly after each jump (FadingTraps, fadeDelay 2) and light specks burst on each trap cell <small>`core/…/mobs/Tengu.java:255-279`, `core/…/levels/PrisonBossLevel.java:650-653`, `core/…/levels/PrisonBossLevel.java:669-729`, `core/…/traps/TenguDartTrap.java:29-48`, `core/…/traps/PoisonDartTrap.java:62-140`, `core/…/buffs/Poison.java:109-117`</small>
- **Phase change at half HP.** At HP &lt;= HT/2 in phase 1, HP is set to exactly HT/2 and it yells "Let's make this interesting...". It then vanishes (FIGHT_PAUSE), and the arena phase starts when the hero walks back up to the start hallway. *Telegraph:* yell line; flash and blast sound <small>`core/…/mobs/Tengu.java:179-184`, `core/…/levels/PrisonBossLevel.java:468-509`, `core/…/levels/PrisonBossLevel.java:585-590`</small>
- **Phase 2: arena jumps.** Each bracket loss jumps it to a cell 5-7 tiles from the hero and 5+ from its old spot, and raises its ability quota: 1 + 2 per jump, +1 extra for jumps 3 and 4. *Telegraph:* wool puff <small>`core/…/mobs/Tengu.java:281-309`, `core/…/mobs/Tengu.java:482-490`</small>
- **Bomb.** Only at or below half HP, and always the first ability (fire is used instead if no bomb cell exists). It is thrown to the non-solid cell next to the hero, without an existing bomb, that is closest to Tengu (the cell may be occupied). It shows 3..., 2..., 1... on successive turns, then blasts every character except Tengu within 2 non-solid path steps for (5+depth)-(10+2\*depth) damage, 15-30 on depth 10, minus DR once. The bomb cannot be picked up. *Telegraph:* a floating "3...", "2...", "1..." over the bomb and smoke over the blast area <small>`core/…/mobs/Tengu.java:449-452`, `core/…/mobs/Tengu.java:496-520`, `core/…/mobs/Tengu.java:566-671`, `core/…/mobs/Tengu.java:717-721`</small>
- **Shocker.** Always the second ability (fire is used instead if no cell qualifies). It is thrown to a non-solid cell next to the hero, at least 2 from Tengu and at least 2 from any other shocker, closest to Tengu. Every turn it seeds its own cell and alternately its 4 diagonal or 4 orthogonal neighbours with electricity that discharges as the seed decays, for 2+depth damage (12 on depth 10) that ignores armor DR. It never expires during the fight. *Telegraph:* static spark particles on the pending cells (alwaysVisible) and lightning lines <small>`core/…/mobs/Tengu.java:500-501`, `core/…/mobs/Tengu.java:524-531`, `core/…/mobs/Tengu.java:932-1100`</small>
- **Fire wave.** From the third ability on it picks bomb, fire or shocker at random, rerolling a repeat 9 times in 10 (Stronger Bosses: bomb or shocker, always plus fire). The fire starts from Tengu's cell in the direction of the first step toward the hero and each turn advances from every still-burning cell to the forward cell and both forward diagonals, so it forms a widening wedge that runs until walls stop it. A fire cell ignites (Burning) whoever stands in it when it burns out, and destroys flammable terrain. *Telegraph:* Tengu zap animation plus a steam puff on it; steam over fire cells <small>`core/…/mobs/Tengu.java:502-512`, `core/…/mobs/Tengu.java:749-925`</small>
- **Ability cadence (phase 2).** Abilities only start at or below half HP. Tengu's quota is 1 + 2 per arena jump + 1 extra for jumps 3 and 4 (max 11). Between uses there is a 1-4 turn cooldown (1 turn if 3+ behind, none if 4+ behind). Each ability costs him 2 turns (1 if 4+ behind), during which he throws no shurikens. Abilities always target the hero and ignore line of sight, so they continue even when the hero is invisible or out of view. *Telegraph:* Tengu's zap animation and the thrown item <small>`core/…/mobs/Tengu.java:450-490`, `core/…/mobs/Tengu.java:492-559`, `core/…/mobs/Tengu.java:407-428`</small>
- **Inflicts Burning.** From fire-wave cells <small>`core/…/mobs/Tengu.java:871-875`</small>
- **Inflicts Poison.** From phase-1 dart traps (8, or 15 with Stronger Bosses) <small>`core/…/traps/TenguDartTrap.java:36-43`</small>
- **Immune.** Roots, Blindness, Dread, Terror <small>`core/…/mobs/Tengu.java:349-354`</small>
- **Immune.** AllyBuff (charm-to-ally, corruption) and Dread (BOSS) <small>`core/…/actors/Char.java:1414-1415`</small>
- **Immune.** Cannot gain buffs (except Doom) or take damage while removed from the level between phases <small>`core/…/mobs/Tengu.java:122-135`</small>
- **Resists.** Grim, Grim trap, Scroll of Retribution, Scroll of Psionic Blast (BOSS); extra DR 0-5. Not immune to Paralysis, Slow, Frost or Chill. <small>`core/…/actors/Char.java:1414`, `core/…/mobs/Tengu.java:115-118`, `core/…/mobs/Tengu.java:349-354`</small>
- **AI.** Starts `hunting`; flees: never. Placed directly in HUNTING at each phase start. Without a line of fire it just waits, or uses an ability if one is due. Abilities always target the hero and do not need line of sight. <small>`core/…/levels/PrisonBossLevel.java:450`, `core/…/levels/PrisonBossLevel.java:498`, `core/…/mobs/Tengu.java:384-429`, `core/…/mobs/Tengu.java:508`, `core/…/mobs/Tengu.java:566-583`, `core/…/mobs/Tengu.java:932-951`</small>
- **Evasion.** defenseSkill 15; evasive: no. Highest evasion in the cohort; HT 200 (250 with Stronger Bosses). <small>`core/…/mobs/Tengu.java:90-92`</small>
- **Surprise.** Can be surprised: yes. No surprisedBy override. It is always hunting with its enemy seen, so surprise needs the hero to be invisible or outside its FOV. <small>`core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/hero/Hero.java:744-752`, `core/…/actors/Char.java:627-630`</small>

#### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `close`; ranged attacks first: no.
- **Plan.** Phase 1: after each jump, note the flashed traps, walk the trap-free path to Tengu and fight him adjacent, where his accuracy halves, until he hits 100 HP. Phase 2 (open ellipse, no cover, no water): close to adjacent again after each jump (5-7 tiles away) using the 2-turn windows after his abilities; dodge the bomb (3-turn countdown, radius 2), keep clear of shockers permanently, and sidestep fire wedges. Heal between phases during the pause. <small>`core/…/mobs/Tengu.java:106-113`, `core/…/mobs/Tengu.java:146-149`, `core/…/mobs/Tengu.java:284-296`, `core/…/mobs/Tengu.java:635-636`, `core/…/mobs/Tengu.java:1069`, `core/…/actors/Char.java:627-630`, `core/…/mobs/Mob.java:783-794`, `core/…/hero/HeroClass.java:174-176`, `core/…/hero/HeroClass.java:190-193`, `core/…/hero/HeroClass.java:204-211`, `core/…/hero/HeroClass.java:221-224`, `core/…/hero/HeroClass.java:233-238`, `core/…/hero/HeroClass.java:248-253`, `core/…/levels/PrisonBossLevel.java:242-252`</small>
- **Kill or nullify: Fight adjacent to it.** Its accuracy halves (10 vs 20) when adjacent, and it never walks away; only a bracket jump moves it. (derived) <small>`core/…/mobs/Tengu.java:106-113`, `core/…/mobs/Tengu.java:384-405`</small>
- **Kill or nullify: Do not spend one huge burst per bracket.** A single hit can take at most 24-48 HP (down to 1 above the next bracket line); the excess is wasted, and each bracket crossing makes him jump away. (derived) <small>`core/…/mobs/Tengu.java:139-151`, `core/…/mobs/Tengu.java:186-203`</small>
- **Kill or nullify: Phase 1: memorize the flashed traps and walk the trap-free route to Tengu.** A trap-free path from Tengu to the hero is guaranteed each jump, and the traps are rendered briefly before fading. (derived) <small>`core/…/levels/PrisonBossLevel.java:695-701`, `core/…/levels/PrisonBossLevel.java:717`, `core/…/levels/PrisonBossLevel.java:724-728`</small>
- **Kill or nullify: Bomb: be 3+ path steps from the bomb before "1..." ends.** The blast covers non-solid cells within distance 2 after a 3-turn countdown. (derived) <small>`core/…/mobs/Tengu.java:622-632`</small>
- **Kill or nullify: Shocker: never end a turn within 1 tile of a shocker.** Its 3x3 area alternates between diagonal and orthogonal shocks every turn, deals 12 with no DR on depth 10, and never goes away. (derived) <small>`core/…/mobs/Tengu.java:983-1022`, `core/…/mobs/Tengu.java:1069`</small>
- **Kill or nullify: Fire: step sideways out of the wedge; there is no water in the arena.** The fire only advances forward and forward-diagonal from Tengu along the throw direction; the phase-2 arena is an open floor ellipse with no water, so Burning cannot be doused by terrain. (derived) <small>`core/…/mobs/Tengu.java:751-760`, `core/…/mobs/Tengu.java:809-819`, `core/…/levels/PrisonBossLevel.java:242-252`</small>
- **Kill or nullify: Block shurikens with allies or invisibility, not terrain.** His normal attack needs a PROJECTILE line and a visible hero; an ally in the line or hero invisibility stops it. The arena has no interior cover, and abilities ignore both. (derived) <small>`core/…/mobs/Tengu.java:235-238`, `core/…/mobs/Tengu.java:385-428`, `core/…/mobs/Mob.java:290`, `core/…/levels/PrisonBossLevel.java:242-252`, `core/…/mobs/Tengu.java:508`</small>
- **Kill or nullify: Do not bring terror, roots, blindness or charm/corruption.** Tengu is immune to them. (derived) <small>`core/…/mobs/Tengu.java:349-354`, `core/…/actors/Char.java:1414-1415`</small>
- **Kill or nullify: Paralysis, slow and frost do work on him.** His immunities are Roots, Blindness, Dread, Terror plus BOSS immunity to AllyBuff/Dread; nothing else. (derived) <small>`core/…/mobs/Tengu.java:349-354`, `core/…/actors/Char.java:1413-1415`</small>
- **Kill or nullify: Use the ability recovery windows to close in.** Each ability spends 2 of his turns with no shuriken, and he never moves between jumps. (derived) <small>`core/…/mobs/Tengu.java:541-554`, `core/…/mobs/Tengu.java:384-405`</small>
- **Escape.** Outrunnable: yes; contact breaks at: none: floor sealed during the fight. It never chases (no movement), but the level is sealed once the fight starts, so you cannot leave. Phase 1 has no ability pressure; its only damage there is its attack plus traps. <small>`core/…/levels/PrisonBossLevel.java:431-433`, `core/…/mobs/Tengu.java:384-405`, `core/…/mobs/Tengu.java:452`</small>

| Class | Note |
|---|---|
| Warrior | Tank in melee; your armor reduces the bomb (DR once) but not shocker damage. |
| Mage | Wands are fine, but cast from adjacent. Big single bursts are capped by the bracket clamp. |
| Rogue | Cloak of Shadows gives guaranteed hits with no evasion roll; save charges for phase 2. |
| Huntress | You can shoot from adjacent, which keeps his accuracy at 10; shooting from range invites acc-20 shurikens. |
| Duelist | Stay adjacent; many quick hits suit the bracket clamp. |
| Cleric | Guiding Light for sure hits against evasion 15. |

**Open questions.**

- Information parity: phase-1 traps become SECRET_TRAP after a ~2s render. Whether the bot may remember rendered-then-hidden traps needs an Observer ruling (a human can).
- Exact per-turn timing of shocker cells: centre seeded with 1, ring cells with 2, each discharges when its seed decays to 0 (TE:1015-1022, 1057-1063); treat the whole 3x3 as unsafe.
- Tengu's mask drops only if the hero has no subclass (TE:214-216) - informational.

#### Community notes

- **Tier F.** “<del>Phase 1 requires only bringing Tengu to half HP. He plants fading poison-dart traps when he jumps (8 turns of poison, ~17 total damage, plus 1-4 physical) — track where he lands and avoid those tiles. Ranged/burst classes (Rogue's sneak attack, Huntress's boomerang, Mage's staff, or any damaging wand) make phase 1 much easier.</del>” [source](https://pixeldungeon.fandom.com/wiki/Tengu) (version: not stated). Confirmed: phase 1 ends at HP &lt;= HT/2, the traps fade, poison 8 totals 17. Contradicted: the dart's physical part is 4-8 minus DR, not 1-4; traps are re-rolled across 40-90% of the whole cell each jump, not placed where he lands, and a trap-free path to him is always left. The Huntress no longer starts with a boomerang (Spirit Bow). <small>`core/…/mobs/Tengu.java:179-184`, `core/…/levels/PrisonBossLevel.java:669-729`, `core/…/traps/TenguDartTrap.java:36-43`, `core/…/traps/PoisonDartTrap.java:114-128`, `core/…/hero/HeroClass.java:221-225`</small>
- **Tier F.** “<del>Phase 2 is a maze fight where Tengu teleports after taking a set amount of damage; a Potion of Mind Vision helps locate him fast in the maze's small circular rooms, and there's always a poison-trap-free path to him if you track his placed traps carefully.</del>” [source](https://pixeldungeon.fandom.com/wiki/Tengu) (version: not stated). Old layout. In v4.0.0 phase 2 is a single open elliptical arena of empty floor with no traps; the jump-on-damage part is right (bracket jumps to 5-7 tiles away). The trap-free-path guarantee belongs to phase 1. <small>`core/…/levels/PrisonBossLevel.java:242-252`, `core/…/levels/PrisonBossLevel.java:489-509`, `core/…/mobs/Tengu.java:281-309`</small>
- **Tier F.** “<del>Paralysis-inflicting tools (Stunning weapon enchant, Wand of Avalanche, Curare-tipped darts, Potion of Paralytic Gas, bombs) are the best counters to Tengu; Potion of Invisibility lets you close distance in the maze without eating shuriken hits.</del>” [source](https://pixeldungeon.fandom.com/wiki/Tengu) (version: not stated). Stunning enchant, Wand of Avalanche and curare darts do not exist in v4.0.0 (paralytic darts and Potion of Paralytic Gas do). Tengu is not immune to paralysis, and invisibility does stop his shurikens (enemyInFOV needs a visible hero), but his abilities still target the hero. There is no maze. <small>`core/…/mobs/Tengu.java:349-354`, `core/…/mobs/Mob.java:290`, `core/…/mobs/Tengu.java:407-428`, `core/…/mobs/Tengu.java:508`</small>

??? note "Corrected during verification (7)"

    - Bracket clamp: max damage per hit is 24-48 HP (to 1 above the next bracket line), not 'one bracket (25)'. First hit at full HP already triggers a jump.
    - Reach 99 is really FOV-limited (viewDistance 12, Tengu.java:98); an invisible hero cannot be attacked.
    - Phase-1 dart: 4-8 minus DR plus Poison 8; traps are re-rolled over the room each jump, not at his landing spot; trap-free path is deliberately winding (PrisonBossLevel.java:695-701).
    - Resolved the arena open question: open EMPTY ellipse with no water or cover (PrisonBossLevel.java:242-252). Removed the 'step into water' and 'use corners' advice.
    - Bomb cell may be occupied; bomb/shocker fall back to fire when no cell qualifies; shockers keep 2 apart; added the ability cadence (quota, 1-4 turn cooldown, 2-turn cost).
    - Added: not immune to Paralysis/Slow/Frost (only Roots, Blindness, Dread, Terror + BOSS).
    - Bestiary build: 'evasive' set to false. Its evasion is the highest of its cohort, but a hero of the level expected at its depth still lands most plain melee hits, which is what the tag means.

## DM-300 (depth 15)

Region page: [Caves (depths 11-14)](caves.md).

### DM-300 {#dm300}

`actors.mobs.DM300` · depths boss 15 · **Tactic:** Engage on plain floor and stay adjacent, reacting to each ability: leave the gas cloud, or step to an unmarked cell before rocks land.

**Stats** (from the [Codex](../codex/mobs.md)): HT 300 · accuracy 20 · evasion 15 · damage 15-25 · armour 0-10 · EXP 30 · max level 29 · properties BOSS, INORGANIC, LARGE. <small>`core/…/mobs/DM300.java:79`, `core/…/mobs/DM300.java:99`, `core/…/mobs/DM300.java:94`, `core/…/mobs/DM300.java:104`</small>

#### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** baseSpeed 1; x2 while supercharged. LARGE: it needs openSpace (no doors or 1-wide gaps), but while supercharged and hunting it digs through arena walls (not the gate) when blocked, spending 3 turns per dig. Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: no; follows you on the stairs: no. <small>`core/…/mobs/DM300.java:348-351`, `core/…/mobs/DM300.java:604-663`, `core/…/mobs/Mob.java:580-582`, `core/…/levels/Level.java:889-905`, `core/…/levels/Terrain.java:90`</small>
- **Attack.** `melee`, reach 1. Melee adjacent. Gas vent follows a STOP_TARGET path; when it cannot reach you it only vents if you are inside a 30-degree cone with STOP_SOLID from it. Attack time: 1 turn (default).. <small>`core/…/mobs/Mob.java:558-568`, `core/…/mobs/Mob.java:753-757`, `core/…/mobs/DM300.java:203-236`, `core/…/mobs/DM300.java:373-402`</small>
- **Toxic gas vent.** Seeds 100 gas at the path's end (your cell) and 20 on each path cell, topping up to 250 total around itself. The gas is added just before your next action. INORGANIC targets are never gassed. *Telegraph:* Zap animation of a toxic speck bolt; your action is interrupted. <small>`core/…/mobs/DM300.java:368-402`, `core/…/sprites/DM300Sprite.java:86-91`</small>
- **Rockfall slam.** If you are adjacent you are knocked back 2 cells first (1 at distance 2). Then cells in a 7x7 around the landing point are marked (denser near the centre, one random safe neighbour) and hit after your action time (1-3 turns) for 6-12 (10-20 challenged) plus 3 (5) turns of paralysis. Rocks never hurt DM-300 or pylons. The safe cell is re-rolled only half the time when it lands on a wall or an energized tile, so it can be unusable. *Telegraph:* Slam animation, targeted-cell markers and falling dust; your action is interrupted. <small>`core/…/mobs/DM300.java:404-471`, `core/…/mobs/DM300.java:687-706`, `core/…/sprites/DM300Sprite.java:107`, `core/…/mobs/DM300.java:439-444`</small>
- **Ability rhythm.** Out of the supercharge: while you are reachable and in view it uses an ability once turnsSinceLastAbility exceeds a 5-9 turn cooldown (5-7 challenged). After gas it favours rocks 3:1, after rocks it favours gas 3:1. An ability used while adjacent costs it a turn; at range it is free. If it cannot reach you (openSpace path) it uses an ability every 5 turns: gas if you are in the cone, else rocks unless you are already paralysed. *Telegraph:* none beyond each ability's own <small>`core/…/mobs/DM300.java:159-302`, `core/…/mobs/DM300.java:112-116`</small>
- **Conductive-floor barrier.** When it walks onto a non-energized INACTIVE_TRAP tile while hunting (not flying), it gains a Barrier of 30 + (missing HP)/10. The Barrier decays by about 1 per turn while it is 20 or more (slower below). *Telegraph:* Log warning 'shield', sparks, and a shield number. <small>`core/…/mobs/DM300.java:321-346`, `core/…/buffs/Barrier.java:52-61`</small>
- **Supercharge.** When HP falls to HT/3\*(2 - pylons activated), i.e. 200 then 100 (HT/4 steps with 3 pylons when challenged), HP is clamped there and it supercharges: invulnerable, speed x2, no abilities, it activates a pylon (a random one that is not the closest to you), and the arena's water, inactive-trap and decor tiles become electrified. It ends when that pylon dies. It cannot die until all pylons have been activated. Entering the supercharge costs it 3 turns (2 challenged). When the charge ends, its ability timer is capped at 2, so no ability comes immediately. *Telegraph:* Yell 'charging', 'invulnerable' status, charge sprite state; then yell 'supercharged'; 'charging_hint' log if you hit it. <small>`core/…/mobs/DM300.java:475-535`, `core/…/mobs/DM300.java:286-299`, `core/…/mobs/DM300.java:514-521`, `core/…/mobs/DM300.java:541-570`, `core/…/levels/CavesBossLevel.java:382-409`, `core/…/sprites/DM300Sprite.java:52`, `core/…/sprites/DM300Sprite.java:103`, `core/…/mobs/DM300.java:528`, `core/…/mobs/DM300.java:545-546`</small>
- **Inflicts ToxicGas.** 1 + scalingDepth/5 per turn in the cloud (4 at depth 15). <small>`core/…/blobs/ToxicGas.java:37-56`</small>
- **Inflicts Paralysis.** 3 turns (5 challenged) from each rock that hits you. <small>`core/…/mobs/DM300.java:697-699`</small>
- **Inflicts Electricity (PylonEnergy floor).** 6-12 per turn to any non-flying non-DM300 character standing on an energized tile. <small>`core/…/levels/CavesBossLevel.java:888-932`</small>
- **Immune.** Sleep; ToxicGas, Poison, Bleeding (INORGANIC); AllyBuff, Dread (BOSS); all damage while supercharged; its own rocks and the energized floor. <small>`core/…/mobs/DM300.java:674-676`, `core/…/actors/Char.java:1421-1422`, `core/…/actors/Char.java:1414-1415`, `core/…/mobs/DM300.java:514-521`, `core/…/mobs/DM300.java:691`, `core/…/levels/CavesBossLevel.java:907`</small>
- **Resists.** Terror, Charm, Vertigo, Cripple, Chill, Frost, Roots, Slow (halved); plus BOSS resistances (Grim, Retribution, Psionic Blast). <small>`core/…/mobs/DM300.java:677-684`, `core/…/actors/Char.java:1414`</small>
- **AI.** Starts `wandering`; flees: never. Starts WANDERING. Before hunting it beckons toward the hero whenever it can reach them; each beckon calls notice(), which on the first call assigns the boss bar and starts the ability clock (the first damage does the same). Abilities are only used in HUNTING. While supercharged it always hunts the hero (unless the hero is invisible). <small>`core/…/levels/CavesBossLevel.java:332-333`, `core/…/mobs/DM300.java:176-199`, `core/…/mobs/DM300.java:286-299`, `core/…/mobs/DM300.java:353-366`, `core/…/mobs/DM300.java:475-480`, `core/…/mobs/Mob.java:1139-1147`</small>
- **Evasion.** defenseSkill 15; evasive: no. Average evasion, DR 0-10. <small>`core/…/mobs/DM300.java:86`, `core/…/mobs/DM300.java:103-106`</small>
- **Surprise.** Can be surprised: yes. Standard rule. Before it hunts, beckon() puts it in WANDERING toward you with enemySeen false until its wandering detection roll succeeds, so a first hit landed before it notices you is a surprise hit; do not count on it. <small>`core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/actors/Char.java:646-684`, `core/…/mobs/DM300.java:192-195`, `core/…/mobs/Mob.java:1139-1147`, `core/…/mobs/Mob.java:1269-1284`</small>

#### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `close`; ranged attacks first: no.
- **Plan.** Engage on plain floor and stay adjacent, reacting to each ability: leave the gas cloud, or step to an unmarked cell before rocks land. Keep it off inactive-trap tiles. At 200 and 100 HP it supercharges: run to the sparking pylon and kill it with many small hits while stepping around its rotating shocks and off electrified tiles (DM-300 is chasing at double speed). Then return to the adjacent fight. The final phase keeps the electrified floor, so fight on safe tiles. <small>`core/…/mobs/DM300.java:159-302`, `core/…/mobs/Pylon.java:198-211`, `core/…/actors/Char.java:1429-1431`, `core/…/levels/CavesBossLevel.java:888-932`, `core/…/actors/Char.java:953-961`</small>
- **Kill or nullify: Fight it adjacent, in open floor away from inactive-trap tiles..** Adjacent ability use costs it a turn, and a narrow or unreachable position makes it spam an ability every 5 turns. It gains a Barrier by stepping on those tiles. (derived) <small>`core/…/mobs/DM300.java:256-259`, `core/…/mobs/DM300.java:202-236`, `core/…/mobs/DM300.java:327-343`</small>
- **Kill or nullify: On a gas vent, leave the cloud next action; on a slam, leave the marked cells (look for the unmarked neighbour)..** Gas lands centred on you. Rocks fall after exactly your action time and skip one safe cell. (derived) <small>`core/…/mobs/DM300.java:373-402`, `core/…/mobs/DM300.java:439-469`</small>
- **Kill or nullify: When it supercharges, go straight for the activated pylon; ignore DM-300..** It is invulnerable until the pylon dies, and it moves at double speed. (derived) <small>`core/…/mobs/DM300.java:514-535`, `core/…/levels/CavesBossLevel.java:411-426`</small>
- **Kill or nullify: Stay off water, inactive-trap and decor tiles while the energy is up; Levitation makes you immune..** Energized tiles deal 6-12 per turn to non-flying characters. (derived) <small>`core/…/levels/CavesBossLevel.java:403-407`, `core/…/levels/CavesBossLevel.java:904-928`</small>
- **Kill or nullify: Assume the floor stays electrified in the final phase..** eliminatePylon clears the energy only while more than 2 pylons (1 challenged) remain. With 4 pylons, the second kill leaves 2, so the energy persists, sourced from DM-300. (derived) <small>`core/…/levels/CavesBossLevel.java:112`, `core/…/levels/CavesBossLevel.java:411-426`</small>
- **Kill or nullify: Do not waste burst past a threshold..** HP is clamped at 200/100 when it supercharges. (derived) <small>`core/…/mobs/DM300.java:496-506`</small>
- **Kill or nullify: Gas and poison items do nothing to it; bring physical damage or wands..** INORGANIC. (derived) <small>`core/…/actors/Char.java:1421-1422`</small>
- **Kill or nullify: Drink a Potion of Purity for a 20-turn window of immunity to its gas, pylon shocks and the energized floor..** BlobImmunity includes ToxicGas and the Electricity damage source used by both the pylons and PylonEnergy. (derived) <small>`core/…/buffs/BlobImmunity.java:66`, `core/…/buffs/BlobImmunity.java:76`, `core/…/mobs/Pylon.java:138`, `core/…/levels/CavesBossLevel.java:914`, `core/…/potions/PotionOfPurity.java:95`, `core/…/buffs/BlobImmunity.java:49`, `core/…/actors/Char.java:1381-1383`</small>
- **Escape.** Outrunnable: no; contact breaks at: none. The level seals when you come within 3 cells of a pylon (entrance walled). There is no stairs escape until it dies. Narrow spots block it only before a supercharge. <small>`core/…/levels/CavesBossLevel.java:282-310`, `core/…/mobs/DM300.java:604-663`</small>

| Class | Note |
|---|---|
| Warrior | Melee it adjacent; the seal shield helps absorb a rock plus a hit while you are paralysed. |
| Mage | Wands are fine on DM-300; on the ELECTRIC pylons Wand of Lightning does half damage, so use another wand or melee there. |
| Rogue | The cloak can help you slip to the pylon; surprise hits on DM-300 are rare since it beckons toward you. |
| Huntress | Ranged play invites the at-range free abilities; still, arrows are the best pylon killers (many small hits, no shock exposure). |
| Duelist | Fast multi-hit abilities suit the pylon's damage compression. |
| Cleric | Heal between abilities; keep off conductive tiles. |

**Open questions.**

- Final-phase persistence of PylonEnergy is derived from the eliminatePylon count logic, not observed in play.
- Stronger Bosses variant values were noted but not treated separately.

#### Community notes

- **Tier F.** “<del>DM-300 has no ranged attack, so pure kiting works: it releases toxic gas around itself and, every time it moves, drops an avalanche on a random adjacent tile that can damage and stun you. Since it matches your movement speed, step one tile away whenever gas reaches you — it will close back to melee range without getting a bonus attack out of you moving.</del>” [source](https://pixeldungeon.fandom.com/wiki/DM-300) (version: unspecified). Outdated (original Pixel Dungeon behaviour). In v4.0.0 the gas vent is a ranged zap along a path to your cell (100 gas on you), rockfalls are a cooldown ability centred on you (not a rock on every move), abilities used at range cost it no turn, and it moves at double speed while supercharged. Kiting at range invites free abilities. <small>`core/…/mobs/DM300.java:373-402`, `core/…/mobs/DM300.java:238-283`, `core/…/mobs/DM300.java:409-471`, `core/…/mobs/DM300.java:348-351`, `core/…/mobs/DM300.java:256-259`</small>
- **Tier 1.** “Come in with armor that can absorb roughly its top hit (~24 damage).” [source](https://pixeldungeon.fandom.com/wiki/DM-300) (version: unspecified). Its melee is NormalIntRange(15, 25), so the top hit is 25 before armor. <small>`core/…/mobs/DM300.java:93-96`</small>
- **Tier F.** “<del>It heals quickly if it is standing on a triggered trap, so don't let it linger over sprung traps.</del>” [source](https://pixeldungeon.fandom.com/wiki/DM-300) (version: unspecified). It does not heal. Stepping onto a non-energized INACTIVE_TRAP tile while hunting gives it a Barrier shield of 30 + (missing HP)/10. The practical advice (keep it off sprung-trap tiles) still holds. <small>`core/…/mobs/DM300.java:321-346`</small>
- **Tier 1.** “Slow, Paralyze, or Freeze effects reduce how often it can hit you.” [source](https://pixeldungeon.fandom.com/wiki/DM-300) (version: unspecified). Paralysis stops its turn (act() returns early while paralysed) and it has no Paralysis resistance. It resists Slow, Chill and Frost, so those last half as long. <small>`core/…/mobs/DM300.java:162-163`, `core/…/mobs/DM300.java:677-684`</small>
- **Tier 1.** “Potion of Purification counters the Toxic Gas.” [source](https://pixeldungeon.fandom.com/wiki/DM-300) (version: unspecified). A drunk Potion of Purity gives 20 turns of BlobImmunity, which includes ToxicGas (and also the Electricity source of pylon shocks and the energized floor). <small>`core/…/buffs/BlobImmunity.java:76`, `core/…/potions/PotionOfPurity.java:95`</small>
- **Tier F.** “<del>Potion of Purification counters the avalanche's Paralysis.</del>” [source](https://pixeldungeon.fandom.com/wiki/DM-300) (version: unspecified). The rockfall's paralysis is a Paralysis buff applied directly by the falling rock, not a gas, and BlobImmunity does not list Paralysis; Purity does not prevent it. <small>`core/…/mobs/DM300.java:697-699`, `core/…/buffs/BlobImmunity.java:63-77`</small>
- **Tier 3 (unverified).** “Bring a weapon averaging around 20 damage per hit and 5-8 Potions of Healing.” [source](https://pixeldungeon.fandom.com/wiki/DM-300) (version: unspecified). Preparation advice (weapon averaging ~20 per hit, 5-8 Potions of Healing); not checkable from code. For scale: HT is 300 (400 challenged), DR 0-10.

??? note "Corrected during verification (6)"

    - Bestiary build: empty break-contact list recorded as 'none' (the floor is sealed for the fight).
    - Surprise: beckon sets WANDERING (Mob.java:1139-1147), not HUNTING, so enemySeen stays false until it notices you; 'surprises unlikely' reworded.
    - AI: start state cited (CavesBossLevel.java:332-333); the ability clock starts at the first beckon via notice().
    - Supercharge: added the 3-turn (2 challenged) charge time and post-charge ability delay; rockfall safe cell may be a wall or energized tile; barrier decay noted.
    - Added counter: Purity covers gas and all Electricity damage in the arena.
    - Mage note: lightning on pylons is halved (ELECTRIC resistance), not useless; Warrior seal-shield note now cited (Char.java:953-961).

### Power pylon {#pylon}

`actors.mobs.Pylon` · depths boss 15 (DM-300 arena part; NEUTRAL until activated) · **Tactic:** When DM-300 supercharges, find the pylon that turned active (not the one nearest you).

**Stats** (from the [Codex](../codex/mobs.md)): HT 50 · accuracy 0 · evasion 0 · damage 1 · armour 0 (no override: only Barkskin adds to it) · EXP 1 · max level -2 · properties BOSS_MINION, ELECTRIC, IMMOVABLE, INORGANIC, MINIBOSS, STATIC. <small>`core/…/mobs/Pylon.java:49`, `core/…/actors/Char.java:689`, `core/…/actors/Char.java:709`, `core/…/actors/Char.java:701`</small>

#### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** Immovable, static arena fixture (4 of them). Speed `immobile`; flies: no; amphibious: no; water-bound: no; opens doors: no; follows you on the stairs: no. <small>`core/…/mobs/Pylon.java:58-66`, `core/…/levels/CavesBossLevel.java:112`, `core/…/levels/CavesBossLevel.java:209-210`</small>
- **Attack.** `none`, reach 1. No targeting. Each turn while active it shocks two opposite adjacent cells (three cells when challenged), rotating one step each turn. Attack time: 1 turn per shock cycle.. <small>`core/…/mobs/Pylon.java:71-133`</small>
- **Rotating shock.** Hits cells CIRCLE8[n] and CIRCLE8[n+4] for 10-20 Electricity, where n advances by 1 (one step clockwise) every turn; with Stronger Bosses it hits n, n+3 and n+5. Derived: without the challenge any given adjacent cell is shocked every 4 turns; with it the pattern is irregular. It shocks any character there (allies too) except DM-300. *Telegraph:* Lightning arcs and sparks on the shocked cells, visible as they happen. The next cells are the current ones rotated one step. <small>`core/…/mobs/Pylon.java:95-131`, `core/…/mobs/Pylon.java:135-149`, `SPD-classes/…/utils/PathFinder.java:74`</small>
- **Dormant until activated.** While NEUTRAL (inactive) it is invulnerable and rejects all buffs. Activation by DM-300's supercharge makes it an ENEMY and HUNTING. *Telegraph:* Sprite switches to the active look. <small>`core/…/mobs/Pylon.java:151-162`, `core/…/mobs/Pylon.java:183-196`, `core/…/sprites/PylonSprite.java:76`</small>
- **Damage compression.** Incoming hits of 15 or more are compressed: 14 + floor((sqrt(8\*(d-14)+1)-1)/2), so a 36 hit deals 20. *Telegraph:* none <small>`core/…/mobs/Pylon.java:198-211`</small>
- **Death ends the supercharge.** On death it calls eliminatePylon, which ends DM-300's supercharge (and clears the energy floor if more than 2 pylons remain). *Telegraph:* DM-300 yells charge_lost or pylons_destroyed. <small>`core/…/mobs/Pylon.java:213-217`, `core/…/levels/CavesBossLevel.java:411-426`, `core/…/mobs/DM300.java:541-565`</small>
- **Inflicts Electricity.** 10-20 damage per shock, bypassing armor (direct damage()). <small>`core/…/mobs/Pylon.java:135-139`, `core/…/actors/Char.java:390`, `core/…/actors/Char.java:483-486`</small>
- **Immune.** Everything while inactive (invulnerable, no buffs). <small>`core/…/mobs/Pylon.java:183-196`</small>
- **Immune.** STATIC: AllyBuff, Dread, Terror, Amok, Charm, Sleep, Paralysis, Frost, Chill, Slow, Speed. IMMOVABLE: Vertigo. INORGANIC: ToxicGas, Poison, Bleeding. MINIBOSS: AllyBuff, Dread. <small>`core/…/actors/Char.java:1416-1417`, `core/…/actors/Char.java:1421-1422`, `core/…/actors/Char.java:1433-1434`, `core/…/actors/Char.java:1439-1441`</small>
- **Resists.** ELECTRIC: Wand of Lightning, Shocking enchant, Potential glyph, Electricity, shocking dart, shock elementals (halved). <small>`core/…/actors/Char.java:1429-1431`</small>
- **AI.** Starts `passive`; flees: never. Custom act(): updates FOV, then does nothing while NEUTRAL, or shocks while active. beckon is ignored. <small>`core/…/mobs/Pylon.java:65-66`, `core/…/mobs/Pylon.java:71-93`, `core/…/mobs/Pylon.java:164-167`</small>
- **Evasion.** defenseSkill 0; evasive: no. No defenseSkill: every attack hits once it is active. <small>`core/…/mobs/Pylon.java:51-67`</small>
- **Surprise.** Can be surprised: yes. The generic Mob.surprisedBy rule applies, but evasion is already 0, so surprise only adds surprise-damage weapon bonuses. <small>`core/…/mobs/Mob.java:873-877`, `core/…/mobs/Pylon.java:51-67`</small>

#### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `keep-away`; ranged attacks first: yes.
- **Plan.** When DM-300 supercharges, find the pylon that turned active (not the one nearest you). Stand on a non-conductive tile and hit it from range if you can. If in melee, watch the arcs: without Stronger Bosses a cell just shocked is safe for 3 more turns. Use rapid small hits rather than one big one. <small>`core/…/mobs/Pylon.java:95-131`, `core/…/mobs/Pylon.java:198-203`, `core/…/actors/Char.java:1429-1431`, `core/…/levels/CavesBossLevel.java:393-400`, `core/…/mobs/Pylon.java:99-104`</small>
- **Kill or nullify: Kill it with many small hits (fast weapons, thrown volleys, multi-hit abilities)..** Hits of 15+ are compressed, so damage per hit below 15 is fully efficient. (derived) <small>`core/…/mobs/Pylon.java:198-203`</small>
- **Kill or nullify: Attack from range, or step around it following the rotation..** Only adjacent cells are shocked, and a given cell is hit every 4 turns. (derived) <small>`core/…/mobs/Pylon.java:95-131`</small>
- **Kill or nullify: Do not use lightning-type damage on it..** ELECTRIC halves Wand of Lightning, Shocking and the other electric sources. (derived) <small>`core/…/actors/Char.java:1429-1431`</small>
- **Kill or nullify: Keep an eye on DM-300 while you work..** The supercharged boss moves at double speed, is invulnerable, and still melees. (derived) <small>`core/…/mobs/DM300.java:348-351`, `core/…/mobs/DM300.java:514-521`</small>
- **Kill or nullify: A drunk Potion of Purity makes its shocks deal 0 for 20 turns..** Shocks use an Electricity damage source, which BlobImmunity lists. (derived) <small>`core/…/mobs/Pylon.java:138`, `core/…/buffs/BlobImmunity.java:66`, `core/…/potions/PotionOfPurity.java:95`, `core/…/buffs/BlobImmunity.java:49`, `core/…/actors/Char.java:1381-1383`</small>
- **Escape.** Outrunnable: yes; contact breaks at: step-away. It never moves; only adjacent cells are dangerous. <small>`core/…/mobs/Pylon.java:95-131`</small>

| Class | Note |
|---|---|
| Warrior | Melee from a cell that was just shocked; fast weapons beat heavy ones here. |
| Mage | Any non-electric wand from range; do not use lightning. |
| Rogue | Thrown weapons from range; many small hits. |
| Huntress | Ideal: arrows from range, each well under the compression threshold. |
| Duelist | Multi-hit abilities make full use of damage below 15 per hit. |
| Cleric | Ranged spells if non-electric; otherwise time melee with the rotation. |

**Open questions.**

- Resolved: CIRCLE8 order is TL, T, TR, R, BR, B, BL, L (PathFinder.java:74), so the shock pair rotates clockwise.

#### Community notes

No community claim about this enemy was found.

??? note "Corrected during verification (4)"

    - Bestiary build: speed 'slow' recorded as 'immobile'; the card's own movement text says it never walks.
    - Rotating shock: rotation is clockwise (PathFinder.java:74); the every-4-turns cadence holds only without Stronger Bosses; shocks hit allies too.
    - Surprise: canBeSurprised corrected to true (generic rule); only damage bonuses matter.
    - Added counter: Purity; tactics timing qualified for Stronger Bosses.

## King of Dwarves (depth 20)

Region page: [Dwarven City (depths 16-19)](city.md).

### King of Dwarves {#dwarfking}

`actors.mobs.DwarfKing` · depths boss 20 · **Tactic:** Enter at full HP and charges; once sealed there is no leaving. Phase 1: hit the King hard (250 HP to the threshold); kill life-linked minions when damage is being split; watch pedestal particles and yells for teleports.

**Stats** (from the [Codex](../codex/mobs.md)): HT 300 · accuracy 26 · evasion 22 · damage 15-25 · armour 0-10 · EXP 40 · max level 29 · properties BOSS, UNDEAD. <small>`core/…/mobs/DwarfKing.java:80`, `core/…/mobs/DwarfKing.java:99`, `core/…/mobs/DwarfKing.java:94`, `core/…/mobs/DwarfKing.java:104`</small>

#### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** No baseSpeed override. IMMOVABLE in phase 2 (sits on the throne). Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/actors/Char.java:175`, `core/…/mobs/Mob.java:1355`, `core/…/mobs/DwarfKing.java:498`, `core/…/mobs/DwarfKing.java:499`, `core/…/mobs/DwarfKing.java:519`</small>
- **Attack.** `melee`, reach 1. Adjacent melee only; no ranged attack. Attack time: 1 turn (default).. <small>`core/…/mobs/Mob.java:558`, `core/…/mobs/Mob.java:753`, `core/…/mobs/DwarfKing.java:94`, `core/…/mobs/DwarfKing.java:95`, `core/…/mobs/DwarfKing.java:99`, `core/…/mobs/DwarfKing.java:100`</small>
- **Arena seal.** Stepping into the throne room seals it (LockedFloor) and spawns the King WANDERING, beckoned to you. *Telegraph:* The King fades in and yells on notice. <small>`core/…/levels/CityBossLevel.java:316`, `core/…/levels/CityBossLevel.java:317`, `core/…/levels/CityBossLevel.java:319`, `core/…/levels/CityBossLevel.java:326`, `core/…/levels/CityBossLevel.java:335`, `core/…/levels/CityBossLevel.java:336`, `core/…/levels/CityBossLevel.java:339`, `core/…/levels/Level.java:648`, `core/…/mobs/DwarfKing.java:433`, `core/…/mobs/DwarfKing.java:437`</small>
- **Phase 1 summons.** When summonCooldown &lt;= 0 it queues a summon on a random free pedestal (4 pedestals at +/-3,+/-3 from the throne): every 4th is a monk or warlock, otherwise a ghoul. Delay 3 turns (2 under Stronger Bosses). Cooldown 10-14 (8-10). Damage dealt to it lowers both cooldowns by damage/8. *Telegraph:* Particles pour on the chosen pedestal for the delay: bone rattle = ghoul, green flame = monk, rising shadow = warlock, static sparks = golem. <small>`core/…/mobs/DwarfKing.java:161`, `core/…/mobs/DwarfKing.java:162`, `core/…/mobs/DwarfKing.java:163`, `core/…/mobs/DwarfKing.java:318`, `core/…/mobs/DwarfKing.java:319`, `core/…/mobs/DwarfKing.java:320`, `core/…/mobs/DwarfKing.java:322`, `core/…/mobs/DwarfKing.java:329`, `core/…/mobs/DwarfKing.java:333`, `core/…/mobs/DwarfKing.java:492`, `core/…/mobs/DwarfKing.java:493`, `core/…/mobs/DwarfKing.java:494`, `core/…/mobs/DwarfKing.java:730`, `core/…/mobs/DwarfKing.java:734`, `core/…/mobs/DwarfKing.java:736`, `core/…/mobs/DwarfKing.java:738`, `core/…/mobs/DwarfKing.java:741`, `core/…/levels/CityBossLevel.java:82`, `core/…/levels/CityBossLevel.java:85`, `core/…/levels/CityBossLevel.java:233`</small>
- **Life link.** Links itself to its furthest unlinked minion for 100 turns; damage to either is split evenly with the linked partner (ceil(dmg/(links+1))). *Telegraph:* Yell 'I have need of your essence, slave!' / 'Bleed for me, slave!' and a health ray to the minion; minion gets a life link buff. <small>`core/…/mobs/DwarfKing.java:173`, `core/…/mobs/DwarfKing.java:186`, `core/…/mobs/DwarfKing.java:347`, `core/…/mobs/DwarfKing.java:356`, `core/…/mobs/DwarfKing.java:363`, `core/…/mobs/DwarfKing.java:364`, `core/…/mobs/DwarfKing.java:365`, `core/…/mobs/DwarfKing.java:366`, `core/…/actors/Char.java:837`, `core/…/actors/Char.java:845`, `core/…/actors/Char.java:849`</small>
- **Teleport swap.** Hops one cell directly away from you (or to the farthest open neighbour) and teleports its furthest minion onto a cell adjacent to you. *Telegraph:* Yell 'Deal with them, slave!' / 'Keep them busy, slave!'. <small>`core/…/mobs/DwarfKing.java:190`, `core/…/mobs/DwarfKing.java:374`, `core/…/mobs/DwarfKing.java:390`, `core/…/mobs/DwarfKing.java:391`, `core/…/mobs/DwarfKing.java:393`, `core/…/mobs/DwarfKing.java:410`, `core/…/mobs/DwarfKing.java:416`, `core/…/mobs/DwarfKing.java:425`, `core/…/mobs/DwarfKing.java:426`</small>
- **Phase 2: shielded summoner.** At HP &lt;= 50 (100 challenged) it teleports to the throne, becomes IMMOVABLE and invulnerable to everything but KingDamager, gains a Barrier equal to HT (300), kills all current minions, and stops attacking: it only summons fixed waves (non-challenge: 4 ghouls; at shield &lt;= 200 three ghouls then a monk or warlock; at shield &lt;= 100 a warlock, a monk and 2 ghouls). Each phase-2 summon carries KingDamager: when it dies (or stops being an enemy) the King takes HT/12 = 25 (HT/18 challenged). The Barrier regains 1 per turn while below 20 and holds steady above. *Telegraph:* Status 'invulnerable', scream particles, yells 'Enough! Arise my slaves!', 'More! Bleed for your king!', 'Useless! KILL THEM NOW!'. <small>`core/…/mobs/DwarfKing.java:495`, `core/…/mobs/DwarfKing.java:496`, `core/…/mobs/DwarfKing.java:497`, `core/…/mobs/DwarfKing.java:498`, `core/…/mobs/DwarfKing.java:499`, `core/…/mobs/DwarfKing.java:503`, `core/…/mobs/DwarfKing.java:508`, `core/…/mobs/DwarfKing.java:509`, `core/…/mobs/DwarfKing.java:447`, `core/…/mobs/DwarfKing.java:448`, `core/…/mobs/DwarfKing.java:451`, `core/…/mobs/DwarfKing.java:256`, `core/…/mobs/DwarfKing.java:262`, `core/…/mobs/DwarfKing.java:266`, `core/…/mobs/DwarfKing.java:272`, `core/…/mobs/DwarfKing.java:273`, `core/…/mobs/DwarfKing.java:280`, `core/…/mobs/DwarfKing.java:284`, `core/…/mobs/DwarfKing.java:287`, `core/…/mobs/DwarfKing.java:703`, `core/…/mobs/DwarfKing.java:704`, `core/…/mobs/DwarfKing.java:777`, `core/…/mobs/DwarfKing.java:778`, `core/…/mobs/DwarfKing.java:786`, `core/…/mobs/DwarfKing.java:790`, `core/…/mobs/DwarfKing.java:800`, `core/…/mobs/DwarfKing.java:801`, `core/…/buffs/Barrier.java:54`, `core/…/buffs/Barrier.java:57`, `core/…/buffs/ShieldBuff.java:70`</small>
- **Summon crush.** If the pedestal and all 8 neighbours are blocked when a summon completes, the occupant of the pedestal takes 20-40 damage; in phase 2 this also deals HT/12 to the King. *Telegraph:* Same pedestal particles. <small>`core/…/mobs/DwarfKing.java:679`, `core/…/mobs/DwarfKing.java:686`, `core/…/mobs/DwarfKing.java:707`, `core/…/mobs/DwarfKing.java:708`, `core/…/mobs/DwarfKing.java:709`, `core/…/mobs/DwarfKing.java:713`</small>
- **Phase 3: deferred damage.** When the shield hits 0 it becomes mobile again and fights in melee; all damage is converted to deferred damage that ticks 10% of the pool per turn (min 1). It keeps summoning whenever fewer than 4 summons are pending. It only counts as dead in phase 3. *Telegraph:* Yell 'You cannot kill me ... I. AM. IMMORTAL!', boss bar bleeds, finale music; each hit shows a deferred-damage number. <small>`core/…/mobs/DwarfKing.java:518`, `core/…/mobs/DwarfKing.java:519`, `core/…/mobs/DwarfKing.java:520`, `core/…/mobs/DwarfKing.java:524`, `core/…/mobs/DwarfKing.java:525`, `core/…/mobs/DwarfKing.java:473`, `core/…/mobs/DwarfKing.java:475`, `core/…/mobs/DwarfKing.java:476`, `core/…/mobs/DwarfKing.java:478`, `core/…/mobs/DwarfKing.java:296`, `core/…/mobs/DwarfKing.java:297`, `core/…/mobs/DwarfKing.java:543`, `core/…/mobs/DwarfKing.java:544`, `core/…/glyphs/Viscosity.java:149`, `core/…/glyphs/Viscosity.java:150`</small>
- **Death cleanup.** On death all remaining subjects die, the floor unseals, Degrade on the hero is removed, and the King's Crown drops. *Telegraph:* Yell 'You've... Doomed us all...'. <small>`core/…/mobs/DwarfKing.java:563`, `core/…/mobs/DwarfKing.java:565`, `core/…/mobs/DwarfKing.java:574`, `core/…/mobs/DwarfKing.java:577`, `core/…/mobs/DwarfKing.java:578`, `core/…/mobs/DwarfKing.java:588`, `core/…/mobs/DwarfKing.java:589`, `core/…/mobs/DwarfKing.java:592`</small>
- **Immune.** Everything except KingDamager during phase 2 (isInvulnerable) <small>`core/…/mobs/DwarfKing.java:447`, `core/…/mobs/DwarfKing.java:451`</small>
- **Immune.** AllyBuff and Dread (BOSS) <small>`core/…/mobs/DwarfKing.java:89`, `core/…/actors/Char.java:1414`, `core/…/actors/Char.java:1415`</small>
- **Immune.** Doom's damage amplification after phase 1 if already doomed <small>`core/…/mobs/DwarfKing.java:598`</small>
- **Resists.** Grim, Grim trap, Scroll of Retribution, Scroll of Psionic Blast (BOSS) <small>`core/…/actors/Char.java:1414`</small>
- **AI.** Starts `wandering`; flees: never. Spawned WANDERING and beckoned to the hero when the arena seals; phase-specific act() logic. <small>`core/…/levels/CityBossLevel.java:336`, `core/…/levels/CityBossLevel.java:339`, `core/…/mobs/DwarfKing.java:154`</small>
- **Evasion.** defenseSkill 22; evasive: no. defenseSkill 22, DR 0-10, HT 300 (450 challenged), accuracy 26, damage 15-25 (codex). <small>`core/…/mobs/DwarfKing.java:85`, `core/…/mobs/DwarfKing.java:87`, `core/…/mobs/DwarfKing.java:95`, `core/…/mobs/DwarfKing.java:100`, `core/…/mobs/DwarfKing.java:105`</small>
- **Surprise.** Can be surprised: yes. Standard rules; it spawns WANDERING, so the first hit before it notices you can be a surprise. <small>`core/…/levels/CityBossLevel.java:336`, `core/…/mobs/Mob.java:873`, `core/…/mobs/Mob.java:875`</small>

#### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `close`; ranged attacks first: no.
- **Plan.** Approach: enter at full HP and charges; once sealed there is no leaving. Phase 1: hit the King hard (250 HP to the threshold); kill life-linked minions when damage is being split; watch pedestal particles and yells for teleports. Phase 2: King sits idle on the throne; kill each summon as it lands (25 shield each, ghouls must die for real), warlocks first. Phase 3: pile damage into the deferred pool and kite or fight the continuing summons until the pool drains the last 50 HP. <small>`core/…/mobs/DwarfKing.java:495`, `core/…/mobs/DwarfKing.java:451`, `core/…/mobs/DwarfKing.java:473`, `core/…/levels/CityBossLevel.java:319`, `core/…/mobs/Mob.java:558`</small>
- **Kill or nullify: Phase 1: put damage on the King itself; spend nothing on phase-1 minions beyond survival..** Every 8 damage it takes advances both its summon and ability cooldowns by 1, and at 50 HP (100 challenged) phase 1 ends and every current minion dies. While a minion is life-linked, damage to the King is split with that minion, so killing the linked minion restores full damage. (derived) <small>`core/…/mobs/DwarfKing.java:493`, `core/…/mobs/DwarfKing.java:494`, `core/…/mobs/DwarfKing.java:495`, `core/…/mobs/DwarfKing.java:508`, `core/…/mobs/DwarfKing.java:509`, `core/…/actors/Char.java:837`, `core/…/actors/Char.java:840`, `core/…/actors/Char.java:841`, `core/…/actors/Char.java:845`</small>
- **Kill or nullify: Watch pedestal particles to see what spawns and where; be in position to kill it as it lands..** Each summon telegraphs its type and cell for 3 turns before appearing. (derived) <small>`core/…/mobs/DwarfKing.java:662`, `core/…/mobs/DwarfKing.java:730`, `core/…/mobs/DwarfKing.java:734`, `core/…/mobs/DwarfKing.java:741`</small>
- **Kill or nullify: Phase 2: ignore the King, kill every summon; each permanent kill removes 25 shield..** The King is invulnerable except to KingDamager and never attacks in phase 2 (its phase-2 branch always returns before Mob AI runs). The 12 non-challenge summons x 25 = 300 = its shield. (derived) <small>`core/…/mobs/DwarfKing.java:201`, `core/…/mobs/DwarfKing.java:256`, `core/…/mobs/DwarfKing.java:292`, `core/…/mobs/DwarfKing.java:451`, `core/…/mobs/DwarfKing.java:790`</small>
- **Kill or nullify: Phase 2 ghouls: finish crumpled ghouls properly..** KingDamager persists through a ghoul's crumple (revivePersists), so the shield only drops when the ghoul truly dies. (derived) <small>`core/…/mobs/DwarfKing.java:773`, `core/…/mobs/Ghoul.java:187`, `core/…/mobs/Ghoul.java:188`</small>
- **Kill or nullify: Phase 3: dump as much damage as possible fast, then survive..** Damage is pooled and paid out at 10% per turn (min 1); a bigger pool drains faster in absolute terms. Summons keep coming (up to 4 pending) while you wait. (derived) <small>`core/…/mobs/DwarfKing.java:473`, `core/…/mobs/DwarfKing.java:476`, `core/…/glyphs/Viscosity.java:149`, `core/…/mobs/DwarfKing.java:296`</small>
- **Kill or nullify: Warlocks first among the adds..** Warlock bolts ignore armor and can Degrade (cleansed only on the King's death). (derived) <small>`core/…/mobs/Warlock.java:117`, `core/…/mobs/Warlock.java:113`, `core/…/mobs/DwarfKing.java:588`</small>
- **Kill or nullify: Use holy damage in phases 1 and 3..** The King is UNDEAD. Holy damage: Holy Bomb (+50% damage), Wand of Prismatic Light (x1.333 damage), Wand of Transfusion (damages instead of charming), Holy Dart bonus damage, and the Cleric's Smite (max roll), Holy Lance (max roll) and Sunray (fixed bonus damage) all key off the UNDEAD property. It is invulnerable to everything except KingDamager in phase 2. (derived) <small>`core/…/mobs/DwarfKing.java:90`, `core/…/mobs/DwarfKing.java:451`, `core/…/bombs/HolyBomb.java:72`, `core/…/bombs/HolyBomb.java:76`, `core/…/wands/WandOfPrismaticLight.java:96`, `core/…/wands/WandOfPrismaticLight.java:100`, `core/…/wands/WandOfTransfusion.java:129`, `core/…/wands/WandOfTransfusion.java:137`, `core/…/darts/HolyDart.java:64`, `core/…/darts/HolyDart.java:67`, `core/…/spells/Smite.java:123`, `core/…/spells/Smite.java:124`, `core/…/spells/HolyLance.java:120`, `core/…/spells/HolyLance.java:121`, `core/…/spells/Sunray.java:100`</small>
- **Kill or nullify: Use the four statues beside the throne to block warlock bolts..** The arena has solid statues two and three cells either side of the throne; MAGIC_BOLT stops on solid terrain. (derived) <small>`core/…/levels/CityBossLevel.java:169`, `core/…/levels/CityBossLevel.java:170`, `core/…/levels/CityBossLevel.java:171`, `core/…/levels/CityBossLevel.java:172`, `core/…/levels/Terrain.java:121`, `core/…/mechanics/Ballistica.java:130`</small>
- **Escape.** Outrunnable: no; contact breaks at: none. The arena seals on entry (LockedFloor); there is no escape until it dies. <small>`core/…/levels/CityBossLevel.java:319`, `core/…/levels/Level.java:648`, `core/…/mobs/DwarfKing.java:574`</small>

| Class | Note |
|---|---|
| Warrior | Melee all phases; save shielding and healing for phase 3's add flood. |
| Mage | Wands for burst in phase 1 and on adds; any damage into phase 3's pool counts. |
| Rogue | A surprise hit is possible before it notices you; later, use invisibility to escape the adds in phase 3. |
| Huntress | Ranged damage on pedestal spawns in phase 2; it has no ranged attack, so kite it in phases 1 and 3. |
| Duelist | Burst abilities on the King in phase 1 and on monks and warlocks in phase 2. |
| Cleric | Guiding Light never misses; Illuminated adds make easy targets. |

**Open questions.**

- Stronger Bosses challenge waves (6/12/18 summons, DK golems) only summarised.

#### Community notes

- **Tier 1.** “The Dwarf King fight takes place in a circular throne room with pedestals; he periodically summons a subject (roughly every 10-14 turns) and uses a special ability on a similar cadence. Minions are mostly Ghouls, with roughly every fourth summon instead being a Warlock or a Monk; all summoned minions spawn already aware of the Hero and drop no loot or EXP.” [source](https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Bosses) (version: not stated). Summon and ability cooldowns are 10-14 (8-10 on Stronger Bosses); every 4th summon is a monk or warlock, the rest ghouls; summons appear HUNTING with maxLvl -2 (no EXP, no loot once the hero is above level 0). The arena is a diamond with 4 pedestals. <small>`core/…/mobs/DwarfKing.java:113`, `core/…/mobs/DwarfKing.java:114`, `core/…/mobs/DwarfKing.java:163`, `core/…/mobs/DwarfKing.java:187`, `core/…/mobs/DwarfKing.java:319`, `core/…/mobs/DwarfKing.java:320`, `core/…/mobs/DwarfKing.java:322`, `core/…/mobs/DwarfKing.java:699`, `core/…/mobs/DwarfKing.java:702`, `core/…/mobs/Mob.java:951`, `core/…/mobs/Mob.java:1060`, `core/…/levels/CityBossLevel.java:82`, `core/…/levels/CityBossLevel.java:83`, `core/…/levels/CityBossLevel.java:84`, `core/…/levels/CityBossLevel.java:85`, `core/…/levels/CityBossLevel.java:163`</small>
- **Tier F.** “<del>Priority-kill any summoned dwarf skeletons/undead minions first, since they can chain-stun the Hero into a long stun-lock while taking heavy damage; avoid letting yourself be surrounded by them.</del>” [source](https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Bosses) (version: not stated). Shattered's King summons ghouls, monks, warlocks (and golems on Stronger Bosses), not skeletons, and none of them stuns or paralyses. This describes the original Pixel Dungeon undead dwarves. <small>`core/…/mobs/DwarfKing.java:604`, `core/…/mobs/DwarfKing.java:617`, `core/…/mobs/DwarfKing.java:624`, `core/…/mobs/DwarfKing.java:639`, `core/…/mobs/Ghoul.java:65`, `core/…/mobs/Ghoul.java:66`</small>
- **Tier F.** “<del>Throw a Potion of Toxic Gas into the middle of the throne room at the start of the fight, because the King consistently moves to that central spot, and the gas deals roughly 10 HP per turn to him. Do NOT use a Potion of Paralytic Gas on him — he is immune to many potions and you risk paralyzing yourself instead.</del>” [source](https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Bosses) (version: not stated). Toxic gas deals 1 + depth/5 = 5 per turn on depth 20, not about 10; the King is invulnerable to it in phase 2 (when he sits on the central throne), and in phase 3 the damage is deferred. He is not immune to Paralytic Gas: his BOSS immunities are only AllyBuff and Dread. The warning about gassing yourself is fair. <small>`core/…/blobs/ToxicGas.java:40`, `core/…/mobs/DwarfKing.java:451`, `core/…/mobs/DwarfKing.java:473`, `core/…/mobs/DwarfKing.java:498`, `core/…/actors/Char.java:1414`, `core/…/actors/Char.java:1415`, `core/…/blobs/ParalyticGas.java:51`, `core/…/blobs/ParalyticGas.java:52`</small>
- **Tier 3 (unverified).** “Use a Scroll of Mirror Image early in the fight (rather than saving it) so the summoned mirror images attack the King himself rather than getting wasted on summoned skeletons; a Scroll of Psionic Blast is also considered strong once he has summoned several skeletons, though its damage is inconsistent.” [source](https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Bosses) (version: not stated). Mirror-image targeting is AI behaviour not checked here. The King has no skeleton summons, and BOSS resists Scroll of Psionic Blast (half effect on the King). <small>`core/…/actors/Char.java:1414`</small>
- **Tier F.** “<del>SOURCE IS THIN / MISMATCHED VERSION: this specific advice thread is titled about the boss in the \*original\* Pixel Dungeon (Watabou's game), not Shattered Pixel Dungeon — it describes the King of Dwarves summoning 1-5 undead dwarves from two pedestals and warns the undead can paralyze the Hero if surrounded. The mechanics described (pedestal count, paralysis-on-surround) may not match Shattered's rebalanced Dwarf King fight and should be treated as folklore from the base game rather than confirmed Shattered-specific tactics.</del>” [source](https://pixeldungeon.fandom.com/f/p/1997203027525688865) (version: not stated). Original Pixel Dungeon folklore. Shattered's throne room has 4 summoning pedestals, and its summons have no paralysis effect. <small>`core/…/levels/CityBossLevel.java:82`, `core/…/levels/CityBossLevel.java:83`, `core/…/levels/CityBossLevel.java:84`, `core/…/levels/CityBossLevel.java:85`, `core/…/mobs/DwarfKing.java:329`, `core/…/mobs/DwarfKing.java:604`, `core/…/mobs/DwarfKing.java:617`, `core/…/mobs/DwarfKing.java:624`</small>

??? note "Corrected during verification (1)"

    - Bestiary build: empty break-contact list recorded as 'none' (the floor is sealed for the fight).

### Dwarven ghoul (DK summon) {#dwarfking-dkghoul}

`actors.mobs.DwarfKing.DKGhoul` · depths boss 20 (summoned) · **Tactic:** DK ghouls arrive hunting from pedestals and still revive if another ghoul is within view or 3 tiles.

**Stats** (from the [Codex](../codex/mobs.md)): HT 45 · accuracy 24 · evasion 20 · damage 16-22 · armour 0-4 · EXP 5 · max level 20 · properties BOSS_MINION, UNDEAD · loot Gold (20% base). <small>`core/…/mobs/DwarfKing.java:604`, `core/…/mobs/Ghoul.java:70`, `core/…/mobs/Ghoul.java:65`, `core/…/mobs/Ghoul.java:75`</small>

#### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** No baseSpeed override (1 tile/turn). Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/actors/Char.java:175`, `core/…/mobs/Mob.java:1355`, `core/…/levels/Level.java:1214`, `core/…/levels/Level.java:1258`, `core/…/mobs/Mob.java:1699`, `core/…/mobs/Mob.java:1700`</small>
- **Attack.** `melee`, reach 1. Adjacent only (default Mob.canAttack). Attack time: 1 turn (default).. <small>`core/…/mobs/Mob.java:558`, `core/…/mobs/Mob.java:753`, `core/…/mobs/Ghoul.java:65`, `core/…/mobs/Ghoul.java:66`, `core/…/mobs/Ghoul.java:70`, `core/…/mobs/Ghoul.java:71`</small>
- **Spawns in pairs.** On its first act a ghoul with no partner creates a partner ghoul on an orthogonally adjacent free cell; if the original is awake the child starts WANDERING. DK ghouls set partnerID -2 every act, so they never create partners. *Telegraph:* none (a second ghoul pushes out beside the first) <small>`core/…/mobs/Ghoul.java:105`, `core/…/mobs/Ghoul.java:107`, `core/…/mobs/Ghoul.java:121`, `core/…/mobs/Ghoul.java:124`, `core/…/mobs/Ghoul.java:125`, `core/…/mobs/DwarfKing.java:611`, `core/…/mobs/DwarfKing.java:612`</small>
- **Life link revival.** DK ghouls have no partner, but any other ghoul of the same alignment that sees it or is within 3 tiles (distance &lt; 4) can host it: it crumples instead of dying and revives after timesDowned\*5 turns at round(HT/10) = 5 HP on or next to its cell. The link breaks and the downed ghoul dies for real if the host loses sight of it while 4+ tiles away and no other ghoul can host, if its cell is a pit, or if the host is removed. *Telegraph:* Crumple animation (the body stays on the floor); on revival a green healing number "5" floats over it. <small>`core/…/mobs/Ghoul.java:153`, `core/…/mobs/Ghoul.java:154`, `core/…/mobs/Ghoul.java:155`, `core/…/mobs/Ghoul.java:158`, `core/…/mobs/Ghoul.java:161`, `core/…/mobs/Ghoul.java:162`, `core/…/mobs/Ghoul.java:252`, `core/…/mobs/Ghoul.java:253`, `core/…/mobs/Ghoul.java:257`, `core/…/mobs/Ghoul.java:260`, `core/…/mobs/Ghoul.java:266`, `core/…/mobs/Ghoul.java:268`, `core/…/mobs/Ghoul.java:289`, `core/…/mobs/Ghoul.java:296`, `core/…/mobs/Ghoul.java:328`, `core/…/mobs/Ghoul.java:331`, `core/…/mobs/Ghoul.java:335`, `core/…/mobs/Ghoul.java:336`, `core/…/mobs/Ghoul.java:358`, `core/…/mobs/Ghoul.java:369`, `core/…/actors/Char.java:1268`, `core/…/actors/Char.java:1270`, `core/…/sprites/GhoulSprite.java:56`</small>
- **Boss minion.** Spawned by the Dwarf King already HUNTING, with maxLvl -2 (no EXP/loot). In DK phase 2 it carries KingDamager (persists through crumpling), which deals the King HT/12 when it permanently dies. *Telegraph:* Bone-rattle particles pour on a summoning pedestal for the summon delay, then burst with a bones sound. <small>`core/…/mobs/DwarfKing.java:604`, `core/…/mobs/DwarfKing.java:607`, `core/…/mobs/DwarfKing.java:699`, `core/…/mobs/DwarfKing.java:702`, `core/…/mobs/DwarfKing.java:703`, `core/…/mobs/DwarfKing.java:704`, `core/…/mobs/DwarfKing.java:740`, `core/…/mobs/DwarfKing.java:741`, `core/…/mobs/DwarfKing.java:773`, `core/…/mobs/DwarfKing.java:786`, `core/…/mobs/DwarfKing.java:790`, `core/…/mobs/Ghoul.java:187`</small>
- **AI.** Starts `hunting`; flees: never. Summoned HUNTING by the King; no partner (partnerID forced to -2), so no pair regrouping. <small>`core/…/mobs/DwarfKing.java:607`, `core/…/mobs/DwarfKing.java:612`, `core/…/mobs/DwarfKing.java:702`</small>
- **Evasion.** defenseSkill 20; evasive: no. defenseSkill 20, DR 0-4 (codex). Accuracy 24, damage 16-22. <small>`core/…/mobs/Ghoul.java:49`, `core/…/mobs/Ghoul.java:66`, `core/…/mobs/Ghoul.java:71`, `core/…/mobs/Ghoul.java:76`</small>
- **Surprise.** Can be surprised: yes. Summoned already hunting, so surprise needs invisibility or attacking from outside its view. <small>`core/…/mobs/Mob.java:873`, `core/…/mobs/Mob.java:875`, `core/…/mobs/DwarfKing.java:702`</small>

#### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `close`; ranged attacks first: no.
- **Plan.** DK ghouls arrive hunting from pedestals and still revive if another ghoul is within view or 3 tiles. Kill them in pairs, or kill one far from others. In phase 2 each one's permanent death removes HT/12 (25) of the King's shield, but a crumpled ghoul does not count until it truly dies. <small>`core/…/mobs/Ghoul.java:161`, `core/…/mobs/Ghoul.java:252`, `core/…/mobs/Ghoul.java:328`, `core/…/mobs/Mob.java:873`, `core/…/mobs/Mob.java:796`, `core/…/missiles/MissileWeapon.java:231`, `core/…/wands/WandOfMagicMissile.java:62`, `core/…/spells/GuidingLight.java:86`, `core/…/actors/Char.java:388`</small>
- **Kill or nullify: Kill ghouls that stand near each other back to back: after one crumples, kill the ghoul hosting it within the revive window (5 turns the first time)..** Any same-alignment ghoul in view or within 3 tiles hosts the downed one. When the host dies its GhoulLifeLink detaches and kills the downed ghoul unless yet another ghoul can host. A lone ghoul with no other ghoul nearby dies for real. (derived) <small>`core/…/mobs/Ghoul.java:159`, `core/…/mobs/Ghoul.java:161`, `core/…/mobs/Ghoul.java:328`, `core/…/mobs/Ghoul.java:335`, `core/…/mobs/Ghoul.java:336`, `core/…/mobs/Ghoul.java:360`, `core/…/actors/Char.java:1268`, `core/…/actors/Char.java:1270`, `core/…/mobs/Ghoul.java:369`</small>
- **Kill or nullify: Separate host from corpse: after one crumples, retreat around a corner or through a door so the surviving ghoul follows you 4+ tiles away and loses sight of the body..** The link detaches when the host no longer sees the downed ghoul and is at distance &gt;= 4, which kills the downed ghoul unless another ghoul can host. (derived) <small>`core/…/mobs/Ghoul.java:252`, `core/…/mobs/Ghoul.java:253`, `core/…/mobs/Ghoul.java:254`, `core/…/mobs/Ghoul.java:330`, `core/…/mobs/Ghoul.java:336`</small>
- **Kill or nullify: Pits are permanent kills (the throne room has none; the King's own phase change also kills minions)..** Death by Chasm, or dying/being downed on a pit cell, bypasses the revival. (derived) <small>`core/…/mobs/Ghoul.java:154`, `core/…/mobs/Ghoul.java:257`, `core/…/mobs/Ghoul.java:260`, `core/…/mobs/DwarfKing.java:508`, `core/…/mobs/DwarfKing.java:509`</small>
- **Kill or nullify: Fight at a corridor mouth so only one ghoul can reach you, but still finish the second quickly..** DK ghouls are plain melee (adjacent reach) with 16-22 damage; fewer adjacent attackers means fewer hits. (derived) <small>`core/…/mobs/Mob.java:558`, `core/…/mobs/Ghoul.java:66`</small>
- **Kill or nullify: Use holy damage..** DK ghouls are Ghouls, hence UNDEAD. Holy damage: Holy Bomb (+50% damage), Wand of Prismatic Light (x1.333 damage), Wand of Transfusion (damages instead of charming), Holy Dart bonus damage, and the Cleric's Smite (max roll), Holy Lance (max roll) and Sunray (fixed bonus damage) all key off the UNDEAD property. (derived) <small>`core/…/mobs/Ghoul.java:61`, `core/…/bombs/HolyBomb.java:72`, `core/…/bombs/HolyBomb.java:76`, `core/…/wands/WandOfPrismaticLight.java:96`, `core/…/wands/WandOfPrismaticLight.java:100`, `core/…/wands/WandOfTransfusion.java:129`, `core/…/wands/WandOfTransfusion.java:137`, `core/…/darts/HolyDart.java:64`, `core/…/darts/HolyDart.java:67`, `core/…/spells/Smite.java:123`, `core/…/spells/Smite.java:124`, `core/…/spells/HolyLance.java:120`, `core/…/spells/HolyLance.java:121`, `core/…/spells/Sunray.java:100`</small>
- **Escape.** Outrunnable: no; contact breaks at: out-of-sight. Summoned inside the sealed throne room: the entrance becomes a locked door and LockedFloor is applied, so stairs are not an option until the King dies. Normal speed. When the King dies all remaining subjects die. <small>`core/…/levels/CityBossLevel.java:319`, `core/…/levels/CityBossLevel.java:347`, `core/…/levels/Level.java:648`, `core/…/mobs/DwarfKing.java:577`, `core/…/mobs/DwarfKing.java:578`</small>

| Class | Note |
|---|---|
| Warrior | Armor DR applies to its melee (16-22); tank in a doorway and chain kills. |
| Mage | Wand bolts skip accuracy; use an AoE or chaining wand to down both ghouls together. |
| Rogue | DK ghouls arrive already hunting; surprise needs invisibility or attacking from outside their view. |
| Huntress | Spirit arrows get x1.5 accuracy at range; once one ghoul crumples, finish the ghoul hosting it fast. |
| Duelist | Burst weapon abilities help kill the host ghoul inside the 5-turn window. |
| Cleric | Guiding Light deals direct damage without an accuracy roll; use it to finish the host ghoul. |

**Open questions.**

- Exact turn accounting of GhoulLifeLink countdown under time-freeze effects not traced.

#### Community notes

No community claim about this enemy was found.

### Dwarf monk (DK summon) {#dwarfking-dkmonk}

`actors.mobs.DwarfKing.DKMonk` · depths boss 20 (summoned) · **Tactic:** DK monk: arrives hunting and gains Focus on its first action (cooldown starts at 0).

**Stats** (from the [Codex](../codex/mobs.md)): HT 70 · accuracy 30 · evasion 30 · damage 12-25 · armour 0-2 · EXP 11 · max level 21 · properties BOSS_MINION, UNDEAD · loot Food (8.3% base). <small>`core/…/mobs/DwarfKing.java:617`, `core/…/mobs/Monk.java:60`, `core/…/mobs/Monk.java:55`, `core/…/mobs/Monk.java:70`</small>

#### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** No baseSpeed override; each step also cuts its focus cooldown by 0.67. Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/actors/Char.java:175`, `core/…/mobs/Mob.java:1355`, `core/…/mobs/Monk.java:99`, `core/…/mobs/Monk.java:102`, `core/…/levels/Level.java:1214`, `core/…/levels/Level.java:1258`, `core/…/mobs/Mob.java:1699`, `core/…/mobs/Mob.java:1700`</small>
- **Attack.** `melee`, reach 1. Adjacent only. Attack time: 0.5 turns (attacks twice per normal turn).. <small>`core/…/mobs/Monk.java:65`, `core/…/mobs/Monk.java:66`, `core/…/mobs/Mob.java:558`</small>
- **Focus (parry).** While HUNTING with focusCooldown &lt;= 0 it gains Focus. Focused and not paralysed or sleeping, its defenseSkill is INFINITE_EVASION, so the next attack that rolls accuracy misses (even an invisible surprise attack). The miss shows 'parried', removes Focus, and sets focusCooldown to 6-7. Cooldown drops by all time it spends, plus 0.67 per step. *Telegraph:* Buff icon 'focused' (mind-vision icon tinted teal) on the monk; 'parried' floats when it triggers. <small>`core/…/mobs/Monk.java:84`, `core/…/mobs/Monk.java:86`, `core/…/mobs/Monk.java:87`, `core/…/mobs/Monk.java:93`, `core/…/mobs/Monk.java:94`, `core/…/mobs/Monk.java:102`, `core/…/mobs/Monk.java:107`, `core/…/mobs/Monk.java:108`, `core/…/mobs/Monk.java:109`, `core/…/mobs/Monk.java:115`, `core/…/mobs/Monk.java:120`, `core/…/mobs/Monk.java:124`, `core/…/mobs/Monk.java:125`, `core/…/mobs/Monk.java:143`, `core/…/mobs/Monk.java:147`, `core/…/mobs/Monk.java:152`, `core/…/mobs/Monk.java:157`, `core/…/actors/Char.java:638`, `core/…/actors/Char.java:640`</small>
- **Boss minion.** Summoned HUNTING with maxLvl -2 (no EXP/loot). Only if summoned during phase 2 does it carry KingDamager, whose removal deals HT/12 to the King's shield. *Telegraph:* Green flame particles (Elmo) pour on a summoning pedestal, then burst with a burning sound. <small>`core/…/mobs/DwarfKing.java:617`, `core/…/mobs/DwarfKing.java:620`, `core/…/mobs/DwarfKing.java:670`, `core/…/mobs/DwarfKing.java:672`, `core/…/mobs/DwarfKing.java:738`, `core/…/mobs/DwarfKing.java:739`, `core/…/mobs/DwarfKing.java:704`, `core/…/mobs/DwarfKing.java:699`, `core/…/mobs/DwarfKing.java:703`</small>
- **AI.** Starts `hunting`; flees: never. Default Mob AI; gains Focus the first turn it hunts (focusCooldown starts at 0). <small>`core/…/mobs/DwarfKing.java:620`, `core/…/mobs/Monk.java:81`, `core/…/mobs/Monk.java:86`</small>
- **Evasion.** defenseSkill 30; evasive: yes. defenseSkill 30 (highest in the city), DR 0-2, HT 70, accuracy 30, damage 12-25. Focus makes it evade one attack outright. <small>`core/…/mobs/Monk.java:42`, `core/…/mobs/Monk.java:43`, `core/…/mobs/Monk.java:61`, `core/…/mobs/Monk.java:71`, `core/…/mobs/Monk.java:56`</small>
- **Surprise.** Can be surprised: yes. Summoned already hunting and focused after its first act; a focused monk parries even surprise attacks. <small>`core/…/mobs/Monk.java:86`, `core/…/mobs/Monk.java:108`, `core/…/mobs/Mob.java:796`, `core/…/actors/Char.java:638`</small>

#### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `close`; ranged attacks first: no.
- **Plan.** DK monk: arrives hunting and gains Focus on its first action (cooldown starts at 0). Waste its parry with a cheap throw, or kill it with wands. It hits twice per turn, so prioritise it when it reaches you. <small>`core/…/mobs/Monk.java:107`, `core/…/mobs/Monk.java:108`, `core/…/mobs/Monk.java:120`, `core/…/actors/Char.java:638`, `core/…/wands/WandOfMagicMissile.java:62`, `core/…/spells/GuidingLight.java:86`, `core/…/mobs/Mob.java:783`, `core/…/missiles/MissileWeapon.java:231`, `core/…/hero/Hero.java:533`</small>
- **Kill or nullify: Strip Focus with a cheap attack first (a thrown stone or dart, a weak hit), then land the real blow..** Any missed attack roll calls defenseVerb, which consumes Focus and starts a 6-7 turn cooldown. (derived) <small>`core/…/mobs/Monk.java:115`, `core/…/mobs/Monk.java:120`, `core/…/mobs/Monk.java:124`, `core/…/actors/Char.java:592`, `core/…/actors/Char.java:596`</small>
- **Kill or nullify: Use wands and other no-roll damage..** Wand bolts and Guiding Light call damage() without Char.hit, so Focus and its defense of 30 do not apply. (derived) <small>`core/…/wands/WandOfMagicMissile.java:62`, `core/…/spells/GuidingLight.java:86`</small>
- **Kill or nullify: Paralyse or freeze it..** Focus only works while paralysed == 0, and Mob.defenseSkill is 0 when paralysed. (derived) <small>`core/…/mobs/Monk.java:108`, `core/…/mobs/Mob.java:797`, `core/…/buffs/Frost.java:57`</small>
- **Kill or nullify: Do not kite it; stand and fight..** Every step it takes cuts focus cooldown by an extra 0.67, so it refocuses faster when chasing. (derived) <small>`core/…/mobs/Monk.java:102`</small>
- **Kill or nullify: High armor and HP..** It attacks at 0.5 delay (two 12-25 hits per turn) with low DR 0-2; trade blows only if armor soaks it. (derived) <small>`core/…/mobs/Monk.java:66`, `core/…/mobs/Monk.java:71`</small>
- **Kill or nullify: Use holy damage..** DK monks are Monks, hence UNDEAD. Holy damage: Holy Bomb (+50% damage), Wand of Prismatic Light (x1.333 damage), Wand of Transfusion (damages instead of charming), Holy Dart bonus damage, and the Cleric's Smite (max roll), Holy Lance (max roll) and Sunray (fixed bonus damage) all key off the UNDEAD property. (derived) <small>`core/…/mobs/Monk.java:51`, `core/…/bombs/HolyBomb.java:72`, `core/…/bombs/HolyBomb.java:76`, `core/…/wands/WandOfPrismaticLight.java:96`, `core/…/wands/WandOfPrismaticLight.java:100`, `core/…/wands/WandOfTransfusion.java:129`, `core/…/wands/WandOfTransfusion.java:137`, `core/…/darts/HolyDart.java:64`, `core/…/darts/HolyDart.java:67`, `core/…/spells/Smite.java:123`, `core/…/spells/Smite.java:124`, `core/…/spells/HolyLance.java:120`, `core/…/spells/HolyLance.java:121`, `core/…/spells/Sunray.java:100`</small>
- **Escape.** Outrunnable: no; contact breaks at: out-of-sight. Summoned inside the sealed throne room: the entrance becomes a locked door and LockedFloor is applied, so stairs are not an option until the King dies. Normal speed. When the King dies all remaining subjects die. <small>`core/…/levels/CityBossLevel.java:319`, `core/…/levels/CityBossLevel.java:347`, `core/…/levels/Level.java:648`, `core/…/mobs/DwarfKing.java:577`, `core/…/mobs/DwarfKing.java:578`</small>

| Class | Note |
|---|---|
| Warrior | Spend the parry on a cheap attack before a big hit; its 0.5 attack delay makes armor very valuable. |
| Mage | Wands ignore Focus and defense; the best monk killer. |
| Rogue | It is never asleep; invisibility does not beat Focus, so bait the parry first. |
| Huntress | The first arrow is parried; a cheap thrown weapon can take that parry instead. Arrows at range get x1.5 accuracy against its 30 defense. |
| Duelist | Do not waste a charged weapon ability into Focus; bait the parry first. Precise Assault's accuracy does not beat infinite evasion. |
| Cleric | Guiding Light has no accuracy roll, so it bypasses Focus; its Illuminated defense-0 effect does not override Focus. |

**Open questions.**

- Resolved: Imp.Quest.oldProcess never drops a token on depth 20.

#### Community notes

No community claim about this enemy was found.

### Dwarf warlock (DK summon) {#dwarfking-dkwarlock}

`actors.mobs.DwarfKing.DKWarlock` · depths boss 20 (summoned) · **Tactic:** Kill DK warlocks early in each wave, since their armor-ignoring bolts stack with the other summons.

**Stats** (from the [Codex](../codex/mobs.md)): HT 70 · accuracy 25 · evasion 18 · damage 12-18 · armour 0-8 · EXP 11 · max level 21 · properties BOSS_MINION, UNDEAD · loot POTION (50% base). <small>`core/…/mobs/DwarfKing.java:624`, `core/…/mobs/Warlock.java:68`, `core/…/mobs/Warlock.java:63`, `core/…/mobs/Warlock.java:73`</small>

#### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** No baseSpeed override. Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/actors/Char.java:175`, `core/…/mobs/Mob.java:1355`, `core/…/levels/Level.java:1214`, `core/…/levels/Level.java:1258`, `core/…/mobs/Mob.java:1699`, `core/…/mobs/Mob.java:1700`</small>
- **Attack.** `bolt`, reach 8. Shadow bolt whenever the MAGIC_BOLT line reaches the hero (stops on first char or solid tile); melee only when adjacent or the line is blocked. No cooldown on the bolt. Attack time: bolt 1 turn (TIME_TO_ZAP); melee 1 turn. <small>`core/…/mobs/Warlock.java:45`, `core/…/mobs/Warlock.java:78`, `core/…/mobs/Warlock.java:80`, `core/…/mobs/Warlock.java:83`, `core/…/mobs/Warlock.java:85`, `core/…/mobs/Warlock.java:86`, `core/…/mobs/Warlock.java:92`, `core/…/mobs/Warlock.java:93`, `core/…/mobs/Warlock.java:106`, `core/…/mechanics/Ballistica.java:49`, `core/…/actors/Char.java:191`</small>
- **Shadow bolt.** Magic hit roll (x2 accuracy vs 25 attackSkill); on hit deals 12-18 via enemy.damage (no armor DR roll) and, against the hero, 50% chance of Degrade for 30 turns. *Telegraph:* Warlock plays its cast animation and a shadow bolt (MagicMissile.SHADOW) travels to you; no warning before the cast. <small>`core/…/mobs/Warlock.java:105`, `core/…/mobs/Warlock.java:110`, `core/…/mobs/Warlock.java:112`, `core/…/mobs/Warlock.java:113`, `core/…/mobs/Warlock.java:117`, `core/…/mobs/Warlock.java:120`, `core/…/actors/Char.java:616`, `core/…/sprites/WarlockSprite.java:57`, `core/…/sprites/WarlockSprite.java:61`, `core/…/sprites/WarlockSprite.java:62`</small>
- **Boss minion.** Summoned HUNTING with maxLvl -2 (no EXP/loot); each zap at the hero costs 400 boss score. Only if summoned during phase 2 does it carry KingDamager, whose removal deals HT/12 to the King's shield. *Telegraph:* Shadow particles rise from a summoning pedestal for the summon delay, then a curse burst. <small>`core/…/mobs/DwarfKing.java:624`, `core/…/mobs/DwarfKing.java:627`, `core/…/mobs/DwarfKing.java:631`, `core/…/mobs/DwarfKing.java:633`, `core/…/mobs/DwarfKing.java:667`, `core/…/mobs/DwarfKing.java:668`, `core/…/mobs/DwarfKing.java:736`, `core/…/mobs/DwarfKing.java:737`, `core/…/mobs/DwarfKing.java:704`, `core/…/mobs/DwarfKing.java:699`, `core/…/mobs/DwarfKing.java:703`</small>
- **Inflicts Degrade.** 30 turns: upgraded items act as a lower level (levels above 2 shrink to round(sqrt(2\*(lvl-1))+1)). Cleansed when the Dwarf King dies. <small>`core/…/buffs/Degrade.java:32`, `core/…/buffs/Degrade.java:57`, `core/…/buffs/Degrade.java:66`, `core/…/mobs/Warlock.java:113`, `core/…/mobs/DwarfKing.java:588`, `core/…/mobs/DwarfKing.java:589`</small>
- **AI.** Starts `hunting`; flees: never. Default Mob AI; prefers the bolt at any range with a clear line. <small>`core/…/mobs/DwarfKing.java:627`, `core/…/mobs/Warlock.java:85`</small>
- **Evasion.** defenseSkill 18; evasive: no. defenseSkill 18, DR 0-8, HT 70, accuracy 25, damage 12-18 (codex). <small>`core/…/mobs/Warlock.java:50`, `core/…/mobs/Warlock.java:51`, `core/…/mobs/Warlock.java:64`, `core/…/mobs/Warlock.java:69`, `core/…/mobs/Warlock.java:74`</small>
- **Surprise.** Can be surprised: yes. Standard surprise rules. Arrives hunting, so surprise needs invisibility or attacking from outside its view. <small>`core/…/mobs/Mob.java:873`, `core/…/mobs/Mob.java:796`</small>

#### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `close`; ranged attacks first: no.
- **Plan.** Kill DK warlocks early in each wave, since their armor-ignoring bolts stack with the other summons. Close to melee or put the throne-side statues or other minions in the bolt line. A phase-2 warlock's death removes 25 of the King's shield. <small>`core/…/mobs/Warlock.java:85`, `core/…/mobs/Warlock.java:120`, `core/…/actors/Char.java:390`, `core/…/missiles/MissileWeapon.java:231`, `core/…/wands/WandOfMagicMissile.java:62`, `core/…/mobs/Mob.java:875`</small>
- **Kill or nullify: Get adjacent. Next to you it must melee, and your armor applies..** doAttack uses melee when adjacent; the bolt bypasses DR because it calls enemy.damage directly, while melee goes through Char.attack's DR roll. Melee also has no Degrade chance. (derived) <small>`core/…/mobs/Warlock.java:85`, `core/…/mobs/Warlock.java:88`, `core/…/mobs/Warlock.java:120`, `core/…/actors/Char.java:388`, `core/…/actors/Char.java:390`</small>
- **Kill or nullify: Approach using cover: the four statues beside the throne, or another monster between you..** The bolt requires the MAGIC_BOLT collision to land on your cell; characters and solid tiles (the arena statues) stop it. (derived) <small>`core/…/mobs/Warlock.java:80`, `core/…/mechanics/Ballistica.java:49`, `core/…/mechanics/Ballistica.java:130`, `core/…/mechanics/Ballistica.java:137`, `core/…/levels/CityBossLevel.java:169`, `core/…/levels/CityBossLevel.java:170`, `core/…/levels/CityBossLevel.java:171`, `core/…/levels/CityBossLevel.java:172`, `core/…/levels/Terrain.java:121`</small>
- **Kill or nullify: Do not trade ranged fire in the open..** Its bolt has no cooldown, x2 accuracy and ignores armor; Degrade weakens upgraded gear for 30 turns. (derived) <small>`core/…/mobs/Warlock.java:110`, `core/…/actors/Char.java:616`, `core/…/mobs/Warlock.java:113`, `core/…/buffs/Degrade.java:32`</small>
- **Kill or nullify: Anti-Magic armor cuts DarkBolt damage; Degrade is cleansed when the King dies..** Anti-Magic subtracts an extra armor-like roll from damage whose source is Warlock.DarkBolt; it does not shorten Degrade (Ring of Elements does). The King's death detaches Degrade from the hero. (derived) <small>`core/…/actors/Char.java:941`, `core/…/actors/Char.java:942`, `core/…/glyphs/AntiMagic.java:132`, `core/…/rings/RingOfElements.java:85`, `core/…/mobs/DwarfKing.java:588`, `core/…/mobs/DwarfKing.java:589`</small>
- **Kill or nullify: Use holy damage..** DK warlocks are Warlocks, hence UNDEAD. Holy damage: Holy Bomb (+50% damage), Wand of Prismatic Light (x1.333 damage), Wand of Transfusion (damages instead of charming), Holy Dart bonus damage, and the Cleric's Smite (max roll), Holy Lance (max roll) and Sunray (fixed bonus damage) all key off the UNDEAD property. (derived) <small>`core/…/mobs/Warlock.java:59`, `core/…/bombs/HolyBomb.java:72`, `core/…/bombs/HolyBomb.java:76`, `core/…/wands/WandOfPrismaticLight.java:96`, `core/…/wands/WandOfPrismaticLight.java:100`, `core/…/wands/WandOfTransfusion.java:129`, `core/…/wands/WandOfTransfusion.java:137`, `core/…/darts/HolyDart.java:64`, `core/…/darts/HolyDart.java:67`, `core/…/spells/Smite.java:123`, `core/…/spells/Smite.java:124`, `core/…/spells/HolyLance.java:120`, `core/…/spells/HolyLance.java:121`, `core/…/spells/Sunray.java:100`</small>
- **Escape.** Outrunnable: no; contact breaks at: out-of-sight. Summoned inside the sealed throne room: the entrance becomes a locked door and LockedFloor is applied, so stairs are not an option until the King dies. Normal speed. When the King dies all remaining subjects die. <small>`core/…/levels/CityBossLevel.java:319`, `core/…/levels/CityBossLevel.java:347`, `core/…/levels/Level.java:648`, `core/…/mobs/DwarfKing.java:577`, `core/…/mobs/DwarfKing.java:578`</small>

| Class | Note |
|---|---|
| Warrior | Rush it; your armor only matters once adjacent. |
| Mage | Wands skip accuracy; zap from behind a statue or another minion, then step out of its line. |
| Rogue | It arrives hunting, never asleep; use invisibility to close without taking bolts. |
| Huntress | Arrow duels are losing trades (its bolt ignores armor and never cools down); close in or break its line with statues. |
| Duelist | Close with movement abilities, then melee. |
| Cleric | Guiding Light from cover; close to melee. |

**Open questions.**

- Degrade applies only to the hero (TODO note in code); allies take bolt damage without it.

#### Community notes

- **Tier 1.** “General counter to any enemy with a long-range attack: as soon as it spots you, retreat behind a door or around a corner so it loses line of sight and can't use its ranged attack; this applies to Warlocks, Scorpios, Wisps and similar spellcasters/archers throughout these floors.” [source](https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies) (version: not stated). Applies to DK warlocks too, but inside the sealed arena the only line blockers are the four throne-side statues and other characters. <small>`core/…/mobs/Warlock.java:80`, `core/…/levels/CityBossLevel.java:169`, `core/…/levels/CityBossLevel.java:170`, `core/…/levels/CityBossLevel.java:171`, `core/…/levels/CityBossLevel.java:172`</small>

??? note "Corrected during verification (1)"

    - Bestiary build: reach 1 corrected to 8. The bolt fires along any clear MAGIC_BOLT line to a target in its field of view, and the mob's view distance is the default 8 (Char.java:191).

### Golem (DK summon) {#dwarfking-dkgolem}

`actors.mobs.DwarfKing.DKGolem` · depths boss 20 (summoned, Stronger Bosses only) · **Tactic:** DK golems (Stronger Bosses only) fight in the open arena; kill them with heavy hits or wands, and expect to be pulled next to them whenever you stand out of reach.

**Stats** (from the [Codex](../codex/mobs.md)): HT 120 · accuracy 28 · evasion 15 · damage 25-30 · armour 0-12 · EXP 12 · max level 22 · properties BOSS_MINION, INORGANIC, LARGE · loot WEAPON,ARMOR (20% base). <small>`core/…/mobs/DwarfKing.java:639`, `core/…/mobs/Golem.java:67`, `core/…/mobs/Golem.java:62`, `core/…/mobs/Golem.java:72`</small>

#### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** No baseSpeed override; LARGE, so it can only path through openSpace cells. The throne room is an open diamond, so this rarely limits it there. No self-teleport on the boss floor. Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: no; follows you on the stairs: no. <small>`core/…/actors/Char.java:175`, `core/…/mobs/Mob.java:1355`, `core/…/mobs/Golem.java:55`, `core/…/mobs/Mob.java:580`, `core/…/levels/Level.java:889`, `core/…/levels/Level.java:893`, `core/…/levels/Level.java:896`, `core/…/mobs/Golem.java:195`, `core/…/mobs/Golem.java:196`, `core/…/mobs/Golem.java:197`, `core/…/mobs/Mob.java:1699`, `core/…/mobs/Mob.java:1700`</small>
- **Attack.** `melee`, reach 1. Adjacent melee only; its teleport zap needs a non-solid path of length &lt;= distance+1 (goes around obstacles, not through walls or closed doors). Attack time: 1 turn (default).. <small>`core/…/mobs/Mob.java:558`, `core/…/mobs/Mob.java:753`, `core/…/mobs/Golem.java:175`, `core/…/mobs/Golem.java:177`, `core/…/mobs/Golem.java:179`</small>
- **Teleport enemy to itself.** When hunting and it can see but not attack you: each turn a 1-in-(100/distance) chance, or always if it cannot step closer, it zaps and teleports you to the free cell next to it that is farthest from your current position. Needs enemyTeleCooldown &lt;= 0 (20 turns after use) and a non-solid path. Blocked by MagicImmune and IMMOVABLE targets. Interrupts the hero. *Telegraph:* Golem attack animation plus a green ember bolt (MagicMissile.ELMO) travelling to you, then you appear next to it. <small>`core/…/mobs/Golem.java:212`, `core/…/mobs/Golem.java:225`, `core/…/mobs/Golem.java:226`, `core/…/mobs/Golem.java:228`, `core/…/mobs/Golem.java:235`, `core/…/mobs/Golem.java:239`, `core/…/mobs/Golem.java:240`, `core/…/mobs/Golem.java:150`, `core/…/mobs/Golem.java:151`, `core/…/mobs/Golem.java:154`, `core/…/mobs/Golem.java:159`, `core/…/mobs/Golem.java:163`, `core/…/mobs/Golem.java:164`, `core/…/mobs/Golem.java:166`, `core/…/mobs/Golem.java:172`, `core/…/mobs/Golem.java:176`, `core/…/sprites/GolemSprite.java:106`, `core/…/sprites/GolemSprite.java:111`</small>
- **Self-teleport while wandering.** Inherited from Golem but disabled here: the self-teleport branch requires !Dungeon.bossLevel(), and it only spawns on the boss floor. *Telegraph:* n/a (never happens on the boss floor) <small>`core/…/mobs/Golem.java:195`</small>
- **Boss minion.** Only summoned under the Stronger Bosses challenge (every 9th phase-1/3 summon, and two in the last phase-2 wave); arrives HUNTING with maxLvl -2. In phase 2 its removal deals HT/18 (= 25 of 450) to the King's shield. *Telegraph:* Static sparks on a summoning pedestal, then a spark burst with a charge-up sound. <small>`core/…/mobs/DwarfKing.java:244`, `core/…/mobs/DwarfKing.java:308`, `core/…/mobs/DwarfKing.java:309`, `core/…/mobs/DwarfKing.java:639`, `core/…/mobs/DwarfKing.java:642`, `core/…/mobs/DwarfKing.java:664`, `core/…/mobs/DwarfKing.java:666`, `core/…/mobs/DwarfKing.java:734`, `core/…/mobs/DwarfKing.java:735`, `core/…/mobs/DwarfKing.java:790`, `core/…/mobs/DwarfKing.java:699`, `core/…/mobs/DwarfKing.java:85`</small>
- **Inflicts forced teleport.** Pulls the hero adjacent to the golem. <small>`core/…/mobs/Golem.java:164`</small>
- **Immune.** Bleeding, Toxic Gas, Poison (INORGANIC) <small>`core/…/mobs/Golem.java:54`, `core/…/actors/Char.java:1421`, `core/…/actors/Char.java:1422`</small>
- **AI.** Starts `hunting`; flees: never. Custom Wandering (self-teleport) and Hunting (enemy teleport) states. <small>`core/…/mobs/DwarfKing.java:642`, `core/…/mobs/Golem.java:57`, `core/…/mobs/Golem.java:58`</small>
- **Evasion.** defenseSkill 15; evasive: no. defenseSkill 15 (easy to hit) but DR 0-12 and HT 120; accuracy 28, damage 25-30. <small>`core/…/mobs/Golem.java:45`, `core/…/mobs/Golem.java:46`, `core/…/mobs/Golem.java:63`, `core/…/mobs/Golem.java:68`, `core/…/mobs/Golem.java:73`</small>
- **Surprise.** Can be surprised: yes. Standard surprise rules, but it arrives hunting. <small>`core/…/mobs/Mob.java:873`, `core/…/mobs/Mob.java:796`</small>

#### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `close`; ranged attacks first: yes.
- **Plan.** DK golems (Stronger Bosses only) fight in the open arena; kill them with heavy hits or wands, and expect to be pulled next to them whenever you stand out of reach. <small>`core/…/mobs/Golem.java:172`, `core/…/mobs/Golem.java:239`, `core/…/missiles/MissileWeapon.java:231`, `core/…/wands/WandOfMagicMissile.java:62`, `core/…/actors/Char.java:390`</small>
- **Kill or nullify: Expect to be pulled next to it; there is no corridor to hide in..** The sealed arena is an open diamond; when it sees you and cannot step closer it pulls you adjacent (20-turn cooldown), and teleports cannot be avoided by doorway tricks here. (derived) <small>`core/…/levels/CityBossLevel.java:163`, `core/…/levels/CityBossLevel.java:181`, `core/…/mobs/Golem.java:235`, `core/…/mobs/Golem.java:239`, `core/…/mobs/Golem.java:172`</small>
- **Kill or nullify: Magic Immunity blocks the pull and burns its cooldown..** With MagicImmune on the target, teleportEnemy keeps the target in place but still sets enemyTeleCooldown = 20. (derived) <small>`core/…/mobs/Golem.java:159`, `core/…/mobs/Golem.java:160`, `core/…/mobs/Golem.java:163`, `core/…/mobs/Golem.java:172`</small>
- **Kill or nullify: Use high-damage hits rather than many weak ones..** DR 0-12 eats small hits; its low defense 15 means accuracy is rarely the problem. (derived) <small>`core/…/mobs/Golem.java:46`, `core/…/mobs/Golem.java:73`</small>
- **Kill or nullify: Do not use poison, toxic gas or bleed on it..** INORGANIC immunity. (derived) <small>`core/…/mobs/Golem.java:54`, `core/…/actors/Char.java:1421`, `core/…/actors/Char.java:1422`</small>
- **Escape.** Outrunnable: no; contact breaks at: out-of-sight. Summoned inside the sealed throne room: the entrance becomes a locked door and LockedFloor is applied, so stairs are not an option until the King dies. Normal speed. When the King dies all remaining subjects die. <small>`core/…/levels/CityBossLevel.java:319`, `core/…/levels/CityBossLevel.java:347`, `core/…/levels/Level.java:648`, `core/…/mobs/DwarfKing.java:577`, `core/…/mobs/DwarfKing.java:578`</small>

| Class | Note |
|---|---|
| Warrior | Its 25-30 hits are huge; armor DR applies, trade only with high armor. |
| Mage | Wand damage goes through damage() directly, so it skips both the accuracy roll and the 0-12 DR roll made in Char.attack. |
| Rogue | It arrives hunting, never asleep; do not melee trade. |
| Huntress | Shoot with x1.5 range accuracy against defense 15, but expect a pull every 20 of its turns. |
| Duelist | Burst it with weapon abilities; it cannot be kited out of pull range. |
| Cleric | Guiding Light never misses; not UNDEAD, so no holy bonus. |

#### Community notes

No community claim about this enemy was found.

## Yog-Dzewa (depth 25)

Region page: [Demon Halls (depths 21-24)](halls.md).

### Yog-Dzewa {#yogdzewa}

`actors.mobs.YogDzewa` · depths boss 25 · **Tactic:** Before 25: kill the Demon Spawners and carry Torches, healing, and root/escape tools.

**Stats** (from the [Codex](../codex/mobs.md)): HT 1000 · accuracy `INFINITE_ACCURACY` · evasion 0 · damage 1 · armour 0 (no override: only Barkskin adds to it) · EXP 50 · max level 29 · properties BOSS, DEMONIC, IMMOVABLE, STATIC. <small>`core/…/mobs/YogDzewa.java:67`, `core/…/mobs/YogDzewa.java:156`, `core/…/actors/Char.java:709`, `core/…/actors/Char.java:701`</small>

#### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** Immobile (IMMOVABLE, STATIC). It sits at exit + 3 rows. Speed `immobile`; flies: no; amphibious: no; water-bound: no; opens doors: no; follows you on the stairs: no. <small>`core/…/mobs/YogDzewa.java:82`, `core/…/mobs/YogDzewa.java:84`, `core/…/mobs/YogFist.java:115`</small>
- **Attack.** `bolt`, reach 99. Beams are Ballistica WONT_STOP from Yog through the aimed cell to the map edge, ignoring walls and characters. The primary beam aims at the hero's position (not gated by Yog's FOV). Attack time: Beams resolve on Yog's next act after a delay of gate(1, ceil(hero cooldown), 3) turns. Ability cooldown is 10-15 turns minus (phase-1).. <small>`core/…/mobs/YogDzewa.java:198`, `core/…/mobs/YogDzewa.java:252`, `core/…/mobs/YogDzewa.java:260`, `core/…/mobs/YogDzewa.java:277`, `core/…/mobs/YogDzewa.java:287`, `core/…/mobs/YogDzewa.java:290`, `core/…/mobs/YogDzewa.java:291`</small>
- **Awakening (phase 0 -&gt; 1).** Inert and invulnerable until the hero sees it (heroFOV). Then notice() assigns the boss bar and rolls the first summon and ability cooldowns (10-15). *Telegraph:* Yog yells its 'notice' line and the boss music starts <small>`core/…/mobs/YogDzewa.java:179`, `core/…/mobs/YogDzewa.java:183`, `core/…/mobs/YogDzewa.java:184`, `core/…/mobs/YogDzewa.java:188`, `core/…/mobs/YogDzewa.java:568`, `core/…/mobs/YogDzewa.java:569`, `core/…/mobs/YogDzewa.java:570`, `core/…/mobs/YogDzewa.java:571`, `core/…/mobs/YogDzewa.java:388`</small>
- **Death beams.** 1 + (HT-HP)/400 beams: 1 above 600 HP, 2 at 600-201, 3 at 200 and below. Beam 1 aims at the hero. Extra beams aim at hero neighbours no closer to Yog. If all passable cells around the hero would be covered, one beam is dropped. Each affected non-Yog-aligned character (and Bees) takes a magic hit at INFINITE_ACCURACY for 20-30 (30-50 on Stronger Bosses) as Eye.DeathGaze damage. Flammable terrain on the path is destroyed. The beam is held while the hero is rooted. *Telegraph:* every cell of every beam path is shown as a targeted cell for the delay, and the hero is interrupted <small>`core/…/mobs/YogDzewa.java:194`, `core/…/mobs/YogDzewa.java:203`, `core/…/mobs/YogDzewa.java:224`, `core/…/mobs/YogDzewa.java:226`, `core/…/mobs/YogDzewa.java:228`, `core/…/mobs/YogDzewa.java:248`, `core/…/mobs/YogDzewa.java:253`, `core/…/mobs/YogDzewa.java:256`, `core/…/mobs/YogDzewa.java:265`, `core/…/mobs/YogDzewa.java:273`, `core/…/mobs/YogDzewa.java:282`, `core/…/mobs/YogDzewa.java:288`, `core/…/mobs/YogDzewa.java:157`</small>
- **Summons.** When summonCooldown &lt;= 0 it spawns the next of its rotating regular summons on the free neighbour cell closest to the hero and beckons it to the hero. Normal: 4 slots, Ripper for each spawner still alive and Larva otherwise. Stronger Bosses: 6 slots with Eyes/Scorpios/Larvae and 2 Rippers. Cooldown is 10-15 minus (phase-1), plus another 10-(phase-1) while a fist lives. Damage dealt to Yog reduces both cooldowns by dmg/10. *Telegraph:* the minion is pushed out of Yog (Pushing effect) <small>`core/…/mobs/YogDzewa.java:131`, `core/…/mobs/YogDzewa.java:142`, `core/…/mobs/YogDzewa.java:143`, `core/…/mobs/YogDzewa.java:146`, `core/…/mobs/YogDzewa.java:297`, `core/…/mobs/YogDzewa.java:306`, `core/…/mobs/YogDzewa.java:330`, `core/…/mobs/YogDzewa.java:333`, `core/…/mobs/YogDzewa.java:334`, `core/…/mobs/YogDzewa.java:335`, `core/…/mobs/YogDzewa.java:336`, `core/…/mobs/YogDzewa.java:407`, `core/…/mobs/YogDzewa.java:408`</small>
- **Fist phases.** HP can't drop below HT-300\*phase in phases 1-3. Crossing 700/400/100 advances the phase and adds the next fist below the exit: one of each pair Burning/Soiled, Rotting/Rusted, Bright/Dark in seeded random order, plus its pair on Stronger Bosses. Yog is invulnerable while any fist lives. In phase 4 HP is floored at 100. When the last fist dies in phase 4 it enters phase 5: summonCooldown = -15 (burst of minions), and from then on the ability cooldown is capped at 2 and summons at 3. *Telegraph:* log 'darkness' message, an 'invulnerable' status, and shadow particles at the exit <small>`core/…/mobs/YogDzewa.java:112`, `core/…/mobs/YogDzewa.java:113`, `core/…/mobs/YogDzewa.java:114`, `core/…/mobs/YogDzewa.java:397`, `core/…/mobs/YogDzewa.java:400`, `core/…/mobs/YogDzewa.java:402`, `core/…/mobs/YogDzewa.java:411`, `core/…/mobs/YogDzewa.java:413`, `core/…/mobs/YogDzewa.java:416`, `core/…/mobs/YogDzewa.java:419`, `core/…/mobs/YogDzewa.java:422`, `core/…/mobs/YogDzewa.java:362`, `core/…/mobs/YogDzewa.java:364`, `core/…/mobs/YogDzewa.java:365`, `core/…/mobs/YogDzewa.java:349`, `core/…/mobs/YogDzewa.java:352`, `core/…/mobs/YogDzewa.java:452`, `core/…/mobs/YogDzewa.java:456`</small>
- **Darkness.** Level viewDistance becomes max(4-(phase-1), 1): 4/3/2/1/1 for phases 1-5. The hero keeps viewDistance 6 only with the Light buff. *Telegraph:* vision shrinks <small>`core/…/mobs/YogDzewa.java:475`, `core/…/mobs/YogDzewa.java:477`, `core/…/mobs/YogDzewa.java:484`, `core/…/mobs/YogDzewa.java:485`, `core/…/buffs/Light.java:42`</small>
- **Minion aggro.** When Yog is aggroed onto a character, its minions (Larva, YogRipper, YogEye, YogScorpio) within 4 tiles of Yog are aggroed onto that character. The hero's own attacks do not trigger this, because Yog is always in HUNTING state and Mob.defenseProc and Mob.damage only call aggro for non-hunting mobs. Ally attacks (Mirror/Prismatic Image, Bee, Living Earth guardian), Duelist Challenge and Stone of Aggression do trigger it. *Telegraph:* none <small>`core/…/mobs/YogDzewa.java:511`, `core/…/mobs/YogDzewa.java:513`, `core/…/mobs/YogDzewa.java:515`, `core/…/mobs/YogDzewa.java:77`, `core/…/mobs/Mob.java:834`, `core/…/mobs/Mob.java:835`, `core/…/mobs/Mob.java:836`, `core/…/mobs/Mob.java:909`, `core/…/npcs/MirrorImage.java:175`, `core/…/mobs/Bee.java:126`, `core/…/duelist/Challenge.java:177`</small>
- **Death.** Killing Yog kills all Larva/YogRipper/YogEye/YogScorpio and unseals the level. *Telegraph:* yell 'defeated' <small>`core/…/mobs/YogDzewa.java:526`, `core/…/mobs/YogDzewa.java:527`, `core/…/mobs/YogDzewa.java:528`, `core/…/mobs/YogDzewa.java:546`</small>
- **Immune.** Invulnerable in phase 0 and whenever any YogFist exists. BOSS: AllyBuff, Dread. STATIC: Terror, Amok, Charm, Sleep, Paralysis, Frost, Chill, Slow, Speed. IMMOVABLE: Vertigo. isAlive stays true until phase 5. <small>`core/…/mobs/YogDzewa.java:387`, `core/…/mobs/YogDzewa.java:388`, `core/…/mobs/YogDzewa.java:382`, `core/…/mobs/YogDzewa.java:383`, `core/…/actors/Char.java:1415`, `core/…/actors/Char.java:1440`, `core/…/actors/Char.java:1441`, `core/…/actors/Char.java:1434`</small>
- **Resists.** BOSS: Grim, GrimTrap, Scroll of Retribution, Scroll of Psionic Blast (x0.5) <small>`core/…/actors/Char.java:1414`</small>
- **AI.** Starts `passive`; flees: never. Custom act; state is nominally HUNTING (so allies can target it) but the AI states are never used. It never moves or melees. Phase 0 waits for the hero to see it. After that the beam and summon clocks run regardless of vision. <small>`core/…/mobs/YogDzewa.java:161`, `core/…/mobs/YogDzewa.java:179`, `core/…/mobs/YogDzewa.java:246`, `core/…/mobs/YogDzewa.java:297`, `core/…/mobs/YogDzewa.java:76`, `core/…/mobs/YogDzewa.java:77`</small>
- **Evasion.** defenseSkill 0; evasive: no. No defenseSkill set, so it defaults to 0 (always hit). DR 0. HT 1000 in five HP bands (300 each, then the last 100). <small>`core/…/mobs/YogDzewa.java:72`, `core/…/mobs/Mob.java:130`</small>
- **Surprise.** Can be surprised: yes. Irrelevant: defense is already 0. Damage is gated by invulnerability and HP floors, not accuracy. <small>`core/…/mobs/Mob.java:130`, `core/…/mobs/YogDzewa.java:388`</small>

#### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `keep-away`; ranged attacks first: yes.
- **Plan.** Before 25: kill the Demon Spawners and carry Torches, healing, and root/escape tools. Wake Yog by seeing it, then deal ~300 damage (any means: it is always hit). Each beam volley shows its full path, so keep a free step off the marked lines and move before acting. At each 300-HP threshold a fist spawns next to Yog, invulnerable within 4 tiles of Yog. Drag it at least 5 tiles away and kill it per its own card while dodging beams and killing summons. Yog itself can't be hurt while a fist lives. After the third fist dies, Yog has 100 HP left with fast beams and summons: burst it immediately. Allies (Mirror Images and the like) attacking Yog pull its nearby minions onto themselves. <small>`core/…/mobs/YogDzewa.java:198`, `core/…/mobs/YogDzewa.java:388`, `core/…/mobs/YogFist.java:125`, `core/…/mobs/YogDzewa.java:402`</small>
- **Kill or nullify: Step off every marked beam cell before the delay ends.** Beams hit only characters on the marked paths. Accuracy is infinite, so evasion doesn't help (except infinite-evasion states). Walls don't block WONT_STOP beams, so only moving works. (derived) <small>`core/…/mobs/YogDzewa.java:198`, `core/…/mobs/YogDzewa.java:224`, `core/…/mobs/YogDzewa.java:282`, `core/…/actors/Char.java:638`</small>
- **Kill or nullify: Kill all four Demon Spawners on 21-24.** Each dead spawner turns a Ripper summon into a 20-HP Larva. (derived) <small>`core/…/mobs/YogDzewa.java:143`, `core/…/mobs/YogDzewa.java:144`</small>
- **Kill or nullify: Pull each fist 5+ tiles away from Yog before fighting it.** Fists are invulnerable within 4 tiles (Chebyshev distance) of Yog's cell (exit + 3 rows). (derived) <small>`core/…/mobs/YogFist.java:115`, `core/…/mobs/YogFist.java:116`, `core/…/mobs/YogFist.java:125`</small>
- **Kill or nullify: Bring Light (Torch).** Arena vision falls to 1 by phase 4. Light keeps hero vision at 6. The Dark fist strips Light, so carry a spare. (derived) <small>`core/…/mobs/YogDzewa.java:477`, `core/…/mobs/YogDzewa.java:484`, `core/…/items/Torch.java:72`, `core/…/mobs/YogFist.java:619`</small>
- **Kill or nullify: Save burst for phase 5 (last 100 HP).** After the last fist dies Yog has 100 HP, a -15 summon burst and 2/3-turn caps on its ability and summon clocks, so end it fast. (derived) <small>`core/…/mobs/YogDzewa.java:364`, `core/…/mobs/YogDzewa.java:349`, `core/…/mobs/YogDzewa.java:352`, `core/…/mobs/YogDzewa.java:402`</small>
- **Kill or nullify: Being rooted delays beams.** Queued beams don't fire while the hero is rooted (YogDzewa.java:194). The volley stays queued at the same cells and fires on Yog's first act after the roots end, so use that act to step off the marked lines. (derived) <small>`core/…/mobs/YogDzewa.java:194`</small>
- **Kill or nullify: AntiMagic armor.** The beams use Eye.DeathGaze as their damage source, which AntiMagic resists. (derived) <small>`core/…/mobs/YogDzewa.java:228`, `core/…/glyphs/AntiMagic.java:133`</small>
- **Kill or nullify: Ring of Elements.** Beams deal Eye.DeathGaze damage, which Ring of Elements resists. (derived) <small>`core/…/mobs/YogDzewa.java:228`, `core/…/rings/RingOfElements.java:85`, `core/…/rings/RingOfElements.java:93`, `core/…/glyphs/AntiMagic.java:133`</small>
- **Escape.** Outrunnable: no; contact breaks at: none. The fight seals the level (unsealed on death), and beams target the hero's position regardless of vision. A fist that is WANDERING re-targets the hero on its next act unless the hero is invisible. <small>`core/…/mobs/YogDzewa.java:252`, `core/…/mobs/YogDzewa.java:546`, `core/…/mobs/YogFist.java:94`, `core/…/levels/HallsBossLevel.java:246`</small>

| Class | Note |
|---|---|
| Warrior | Beams ignore armor, so dodging is everything. Combo parry's infinite evasion does beat the infinite-accuracy beam (core/…/actors/Char.java:638). |
| Mage | Wand of Prismatic Light deals x1.333 to this DEMONIC target (core/…/items/wands/WandOfPrismaticLight.java:96-100) and its hits are direct damage with no evasion roll. Wand damage counts toward the HP bands. |
| Rogue | Invisibility (Cloak) stops fists from re-acquiring you (core/…/actors/mobs/YogFist.java:94), but Yog's beam still aims at hero.pos. |
| Huntress | Yog has 0 evasion, so arrows from outside beam lines are free damage. |
| Duelist | Round Shield guard gives infinite evasion, which beats the beam's infinite accuracy. |
| Cleric | Smite and Holy Lance roll their maximum and Sunray deals flat 8/12 against DEMONIC targets (core/…/actors/hero/spells/Smite.java:123, core/…/actors/hero/spells/HolyLance.java:120, core/…/actors/hero/spells/Sunray.java:100). |

**Open questions.**

- HallsBossLevel arena geometry (pillars, corridors usable against the LARGE Rusted Fist) was not read.
- Whether the WONT_STOP beam path is truncated at the map edge only (core/…/mechanics/Ballistica.java:115 insideMap loop) was accepted as read and not rig-tested.

#### Community notes

- **Tier F.** “<del>Every physical hit the Hero lands on Yog-Dzewa's central eye spawns larvae that attack the Hero; damage the Hero deals to Yog-Dzewa is divided by 4 while both fists are alive, and by 2 once only one fist remains — implying the fists should be killed first before committing to attacking the eye directly.</del>” [source](https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Bosses) (version: not stated). Old-version mechanics. Summons run on a 10-15 turn timer, not per hit. Damage to Yog only shortens that timer by dmg/10. There is no /4 or /2 damage scaling: Yog is fully invulnerable (isInvulnerable returns true) while ANY fist exists, and normally only one fist exists at a time. The practical conclusion (kill the fist before hitting Yog) is forced by the code anyway. <small>`core/…/mobs/YogDzewa.java:297`, `core/…/mobs/YogDzewa.java:333`, `core/…/mobs/YogDzewa.java:388`, `core/…/mobs/YogDzewa.java:397`, `core/…/mobs/YogDzewa.java:407`, `core/…/mobs/YogDzewa.java:408`</small>

??? note "Corrected during verification (4)"

    - Citation YogDzewa.java:160 pointed at @Override; moved to 161 (act()).
    - 'Minion aggro: when something attacks Yog' was overstated. The hero's attacks never call Yog.aggro (Yog is HUNTING). Only allies and aggro effects do.
    - Rooted-beam counter: clarified that the volley stays queued on the same cells and fires once roots end, rather than being 'unable to combine'.
    - Bestiary build: speed 'slow' recorded as 'immobile'; the card's own movement text says it never walks.

### God's larva {#yogdzewa-larva}

`actors.mobs.YogDzewa.Larva` · depths boss 25 (summoned) · **Tactic:** Low-HP chaff: shoot or zap it as it comes out of Yog, or melee it off a beam line.

**Stats** (from the [Codex](../codex/mobs.md)): HT 20 · accuracy 30 · evasion 12 · damage 15-25 · armour 0-4 · EXP 5 · max level -2 · properties BOSS_MINION, DEMONIC. <small>`core/…/mobs/YogDzewa.java:641`, `core/…/mobs/YogDzewa.java:658`, `core/…/mobs/YogDzewa.java:663`, `core/…/mobs/YogDzewa.java:668`</small>

#### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** baseSpeed 1 (no override) Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/mobs/YogDzewa.java:641`, `core/…/actors/Char.java:771`</small>
- **Attack.** `melee`, reach 1. Adjacent (default canAttack). Attack time: 1 turn. <small>`core/…/mobs/Mob.java:558`, `core/…/mobs/Mob.java:753`</small>
- **Summoned toward hero.** Placed next to Yog on the free cell closest to the hero and beckoned to the hero's position. All larvae die when Yog dies. *Telegraph:* pushed out of Yog <small>`core/…/mobs/YogDzewa.java:306`, `core/…/mobs/YogDzewa.java:330`, `core/…/mobs/YogDzewa.java:527`</small>
- **AI.** Starts `wandering`; flees: never. beckon() puts it into WANDERING toward the hero's cell and it notices normally. Yog.aggro() pushes minions within 4 tiles of Yog onto whoever Yog is aggroed to. The hero hitting Yog does not trigger this (Yog's state is HUNTING, so Mob.defenseProc and Mob.damage don't call aggro). Allies like Mirror/Prismatic Images, bees and Living Earth guardians do, and so do Challenge and Stone of Aggression. <small>`core/…/mobs/YogDzewa.java:330`, `core/…/mobs/Mob.java:1143`, `core/…/mobs/Mob.java:1144`, `core/…/mobs/Mob.java:1146`, `core/…/mobs/YogDzewa.java:513`, `core/…/mobs/YogDzewa.java:77`, `core/…/mobs/YogDzewa.java:509`, `core/…/mobs/YogDzewa.java:510`, `core/…/mobs/YogDzewa.java:511`, `core/…/mobs/YogDzewa.java:515`, `core/…/mobs/Mob.java:834`, `core/…/mobs/Mob.java:835`, `core/…/mobs/Mob.java:836`, `core/…/mobs/Mob.java:909`, `core/…/npcs/MirrorImage.java:175`, `core/…/npcs/PrismaticImage.java:218`, `core/…/mobs/Bee.java:126`</small>
- **Evasion.** defenseSkill 12; evasive: no. defenseSkill 12, HT 20, DR 0-4, attack 30, damage 15-25 (hits hard for its HP). <small>`core/…/mobs/YogDzewa.java:646`, `core/…/mobs/YogDzewa.java:647`, `core/…/mobs/YogDzewa.java:659`, `core/…/mobs/YogDzewa.java:664`, `core/…/mobs/YogDzewa.java:669`</small>
- **Surprise.** Can be surprised: yes. standard rules <small>`core/…/mobs/Mob.java:796`, `core/…/mobs/Mob.java:873`, `core/…/mobs/Mob.java:875`, `core/…/mobs/Mob.java:1237`</small>

#### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `close`; ranged attacks first: yes.
- **Plan.** Low-HP chaff: shoot or zap it as it comes out of Yog, or melee it off a beam line. Killing Demon Spawners earlier means more of these and fewer Rippers. <small>`core/…/mobs/YogDzewa.java:646`, `core/…/mobs/YogDzewa.java:143`</small>
- **Kill or nullify: One-shot or two-shot it on arrival.** Only 20 HP and 12 defense. Kill it before its 15-25 hits add up while dodging beams. (derived) <small>`core/…/mobs/YogDzewa.java:646`, `core/…/mobs/YogDzewa.java:664`</small>
- **Kill or nullify: Don't count on Yog's beams to clear minions.** Yog's beams hit only characters of a different alignment (plus Bees), so larvae standing in the line are NOT hurt. (derived) <small>`core/…/mobs/YogDzewa.java:203`</small>
- **Kill or nullify: Use Mirror Images or other allies on Yog to pull minions off you.** When an ally attacks Yog, Yog.aggro turns every Larva/YogRipper/YogEye/YogScorpio within 4 tiles of Yog onto that ally. (derived) <small>`core/…/mobs/YogDzewa.java:511`, `core/…/mobs/YogDzewa.java:513`, `core/…/mobs/YogDzewa.java:515`, `core/…/npcs/MirrorImage.java:175`</small>
- **Escape.** Outrunnable: no; contact breaks at: out-of-sight. Same speed as the hero. The boss level is sealed during the fight (no stairs), and larvae die when Yog dies. <small>`core/…/actors/Char.java:771`, `core/…/mobs/YogDzewa.java:546`</small>

| Class | Note |
|---|---|
| Warrior | Melee it. |
| Mage | Any damage wand. |
| Rogue | Melee. |
| Huntress | One or two arrows. |
| Duelist | Melee. |
| Cleric | Smite and Holy Lance roll their maximum and Sunray deals flat 8/12 against DEMONIC targets (core/…/actors/hero/spells/Smite.java:123, core/…/actors/hero/spells/HolyLance.java:120, core/…/actors/hero/spells/Sunray.java:100). |

#### Community notes

No community claim about this enemy was found.

??? note "Corrected during verification (1)"

    - ai.detail said 'attacks on Yog aggro larvae'. The hero's attacks never call Yog.aggro (Yog is HUNTING, Mob.java:835 / 909). Only ally attackers and aggro effects do.

### Ripper demon (Yog summon) {#yogdzewa-yogripper}

`actors.mobs.YogDzewa.YogRipper` · depths boss 25 (summoned) · **Tactic:** Don't run in open ground. Take it at a corner or doorway, or within 2 tiles, so it can't leap, and kill it fast (60 HP, DR 0-4).

**Stats** (from the [Codex](../codex/mobs.md)): HT 60 · accuracy 30 · evasion 22 · damage 15-25 · armour 0-4 · EXP 9 · max level -2 · properties BOSS_MINION, DEMONIC, UNDEAD. <small>`core/…/mobs/YogDzewa.java:675`, `core/…/mobs/RipperDemon.java:77`, `core/…/mobs/RipperDemon.java:72`, `core/…/mobs/RipperDemon.java:87`</small>

#### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** baseSpeed 1 (normal walking), but it leaps: when the enemy is visible, 3+ tiles away, it is not rooted and the leap cooldown is &lt;= 0, it jumps across any STOP_SOLID-clear line. Leap cooldown is 2-4 turns. Speed `fast`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/mobs/RipperDemon.java:60`, `core/…/mobs/RipperDemon.java:236`, `core/…/mobs/RipperDemon.java:237`, `core/…/mobs/RipperDemon.java:145`, `core/…/mobs/RipperDemon.java:251`, `core/…/mobs/Mob.java:1686`, `core/…/mobs/Mob.java:1699`</small>
- **Attack.** `melee`, reach 1. Adjacent melee. The leap line is Ballistica STOP_TARGET\|STOP_SOLID: it jumps over characters and is stopped by walls and closed doors. Attack time: 0.5 turn (two attacks per hero turn). <small>`core/…/mobs/Mob.java:558`, `core/…/mobs/RipperDemon.java:82`, `core/…/mobs/RipperDemon.java:83`, `core/…/mobs/RipperDemon.java:152`, `core/…/mobs/RipperDemon.java:251`</small>
- **Leap (prep).** It picks a landing cell. If the target has not moved since last turn, that cell is the target's own. If the target moved, it is the neighbour of the target opposite the target's previous position, i.e. one step further along the direction of travel. If that line is blocked it retries aiming straight at the target. It then waits gate(0.5, ceil(target cooldown), 1.5) turns. *Telegraph:* log warning (RipperDemon 'leap' message), a targeted-cell marker on the landing cell, RipperSprite prep animation, and the hero is interrupted <small>`core/…/mobs/RipperDemon.java:239`, `core/…/mobs/RipperDemon.java:240`, `core/…/mobs/RipperDemon.java:243`, `core/…/mobs/RipperDemon.java:248`, `SPD-classes/…/utils/PathFinder.java:74`, `core/…/mobs/RipperDemon.java:253`, `core/…/mobs/RipperDemon.java:259`, `core/…/mobs/RipperDemon.java:261`, `core/…/mobs/RipperDemon.java:262`, `core/…/mobs/RipperDemon.java:264`, `core/…/mobs/RipperDemon.java:265`, `core/…/mobs/RipperDemon.java:266`, `core/…/mobs/RipperDemon.java:267`, `core/…/sprites/RipperSprite.java:68`</small>
- **Leap (resolve).** On its next act it re-traces a STOP_TARGET\|STOP_SOLID line to the chosen cell (landing short if something solid now blocks). Whoever of opposite alignment is standing on the landing cell at that moment is hit with INFINITE_ACCURACY. The leap deals no direct damage: it only applies Bleeding at 0.75 x damageRoll (15-25). If the cell is occupied it bounces to the nearest free neighbour. If the cell is empty it just lands there. If rooted at resolve time, the leap is cancelled. The leap cooldown is then 2-4 acts. *Telegraph:* the jump animation <small>`core/…/mobs/RipperDemon.java:147`, `core/…/mobs/RipperDemon.java:148`, `core/…/mobs/RipperDemon.java:152`, `core/…/mobs/RipperDemon.java:155`, `core/…/mobs/RipperDemon.java:194`, `core/…/mobs/RipperDemon.java:195`, `core/…/mobs/RipperDemon.java:196`, `core/…/mobs/RipperDemon.java:145`, `core/…/mobs/RipperDemon.java:153`, `core/…/mobs/RipperDemon.java:159`, `core/…/mobs/RipperDemon.java:185`</small>
- **Boss minion.** Same RipperDemon behaviour. BOSS_MINION, maxLvl -2. Killed when Yog dies. Normal mode: one of the 4 regular summon slots per Demon Spawner still alive. Stronger Bosses: 2 fixed Ripper slots out of 6. *Telegraph:* pushed out of Yog <small>`core/…/mobs/YogDzewa.java:675`, `core/…/mobs/YogDzewa.java:678`, `core/…/mobs/YogDzewa.java:527`, `core/…/mobs/YogDzewa.java:146`, `core/…/mobs/YogDzewa.java:133`, `core/…/mobs/YogDzewa.java:134`, `core/…/mobs/YogDzewa.java:143`</small>
- **Inflicts Bleeding.** Leap hit only: level 0.75 x (15-25). Ticks down by random halving. <small>`core/…/mobs/RipperDemon.java:196`, `core/…/buffs/Bleeding.java:80`, `core/…/buffs/Bleeding.java:106`, `core/…/buffs/Bleeding.java:132`</small>
- **AI.** Starts `wandering`; flees: never. Summoned next to Yog and beckoned to the hero's cell. Custom Hunting: with no enemy it goes WANDERING. When stuck with the enemy out of FOV it shows lost and wanders. YogRipper adds BOSS_MINION and dies with Yog. <small>`core/…/mobs/DemonSpawner.java:113`, `core/…/mobs/RipperDemon.java:67`, `core/…/mobs/RipperDemon.java:68`, `core/…/mobs/RipperDemon.java:230`, `core/…/mobs/RipperDemon.java:231`, `core/…/mobs/RipperDemon.java:281`, `core/…/mobs/RipperDemon.java:283`, `core/…/mobs/YogDzewa.java:330`, `core/…/mobs/Mob.java:1143`</small>
- **Evasion.** defenseSkill 22; evasive: no. defenseSkill 22. DR 0-4 (low). HT 60. <small>`core/…/mobs/RipperDemon.java:52`, `core/…/mobs/RipperDemon.java:88`, `core/…/mobs/RipperDemon.java:51`</small>
- **Surprise.** Can be surprised: yes. Standard rules. Summoned rippers start WANDERING (beckoned), so they can be surprised until they notice you. <small>`core/…/mobs/Mob.java:796`, `core/…/mobs/Mob.java:873`, `core/…/mobs/Mob.java:875`, `core/…/mobs/Mob.java:1237`, `core/…/mobs/YogDzewa.java:330`, `core/…/mobs/Mob.java:1143`, `core/…/mobs/Mob.java:1144`</small>

#### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `chokepoint`; ranged attacks first: no.
- **Plan.** Don't run in open ground. Take it at a corner or doorway, or within 2 tiles, so it can't leap, and kill it fast (60 HP, DR 0-4). If you see the leap warning and marker, step so you are not on the marked cell: sidestep if you were standing still, stop if you were walking. Rooting or a closed door also stops the leap. Its two attacks per turn make trading hits costly, so use your best burst. <small>`core/…/mobs/RipperDemon.java:83`, `core/…/mobs/RipperDemon.java:237`, `core/…/mobs/RipperDemon.java:147`, `core/…/mobs/RipperDemon.java:265`</small>
- **Kill or nullify: When the landing marker appears, don't be on the marked cell.** The leap only damages whoever stands on the landing cell when it resolves. The marker shows exactly that cell. If you stood still, it's your cell, so step off it. If you were walking, it's the cell ahead of you, so stop or turn. (derived) <small>`core/…/mobs/RipperDemon.java:155`, `core/…/mobs/RipperDemon.java:194`, `core/…/mobs/RipperDemon.java:240`, `core/…/mobs/RipperDemon.java:248`, `core/…/mobs/RipperDemon.java:265`</small>
- **Kill or nullify: Fight it within 2 tiles, or put a wall or door in the leap line.** Leaps need distance &gt;= 3 and a STOP_SOLID-clear line. Up close it just melees. (derived) <small>`core/…/mobs/RipperDemon.java:237`, `core/…/mobs/RipperDemon.java:251`, `core/…/mobs/RipperDemon.java:257`</small>
- **Kill or nullify: Root it.** Rooted rippers cannot start a leap, and an already-prepped leap is cancelled. (derived) <small>`core/…/mobs/RipperDemon.java:147`, `core/…/mobs/RipperDemon.java:236`</small>
- **Kill or nullify: Burst it down: low DR 0-4 and 60 HP.** Its damage output is high (two 15-25 attacks per turn), but it is fragile. (derived) <small>`core/…/mobs/RipperDemon.java:51`, `core/…/mobs/RipperDemon.java:83`, `core/…/mobs/RipperDemon.java:88`</small>
- **Escape.** Outrunnable: no; contact breaks at: out-of-sight. The leap covers 3+ tiles every 2-4 acts, so it cannot be outrun in the open. The Yog arena is sealed until Yog dies, so there are no stairs to take. <small>`core/…/mobs/RipperDemon.java:236`, `core/…/mobs/RipperDemon.java:145`, `core/…/mobs/RipperDemon.java:152`, `core/…/levels/Terrain.java:90`, `core/…/mobs/Mob.java:1400`, `core/…/mobs/Mob.java:1401`, `core/…/mobs/Mob.java:1686`, `core/…/mobs/Mob.java:1699`, `core/…/levels/HallsBossLevel.java:246`, `core/…/mobs/YogDzewa.java:546`</small>

| Class | Note |
|---|---|
| Warrior | Armor matters a lot against 2 attacks per turn. The Combo parry's infinite evasion beats the INFINITE_ACCURACY leap (core/…/actors/Char.java:638). |
| Mage | Wand of Prismatic Light deals x1.333 to this DEMONIC target (core/…/items/wands/WandOfPrismaticLight.java:96-100) and its hits are direct damage with no evasion roll. Roots (Wand of Regrowth, Potion of Snap Freeze) stop leaps (rooted check; core/…/items/wands/WandOfRegrowth.java:121, core/…/items/potions/exotic/PotionOfSnapFreeze.java:58). |
| Rogue | Yog rippers arrive beckoned (WANDERING toward your cell), not hunting, so a brief surprise window can exist before they notice you. Otherwise burst it. |
| Huntress | Shooting at range invites the leap. Watch for the marker and sidestep, then finish point-blank. |
| Duelist | Round Shield guard (infinite evasion) negates the guaranteed leap hit. |
| Cleric | Smite and Holy Lance roll their maximum and Sunray deals flat 8/12 against DEMONIC targets (core/…/actors/hero/spells/Smite.java:123, core/…/actors/hero/spells/HolyLance.java:120, core/…/actors/hero/spells/Sunray.java:100). |

#### Community notes

No community claim about this enemy was found.

??? note "Corrected during verification (3)"

    - MAGE class note named a nonexistent 'Wand of Entanglement'; replaced with real root sources (Wand of Regrowth, Potion of Snap Freeze).
    - Leap (resolve): made explicit that the leap deals no direct damage (only Bleeding), and that the landing line is re-traced against solid terrain.
    - Removed 'stairs'/'door' from breakContact (sealed arena). ROGUE note and surprise said it is usually hunting, but Yog summons are beckoned into WANDERING (Mob.java:1143-1144).

### Evil eye (Yog summon) {#yogdzewa-yogeye}

`actors.mobs.YogDzewa.YogEye` · depths boss 25 (summoned; Stronger Bosses challenge only) · **Tactic:** Treat the charge animation as a 2-turn warning, but don't just step sideways: while you stay in its view it re-aims at your new cell.

**Stats** (from the [Codex](../codex/mobs.md)): HT 100 · accuracy 30 · evasion 20 · damage 20-30 · armour 0-10 · EXP 13 · max level -2 · properties BOSS_MINION, DEMONIC · loot Dewdrop (100% base). <small>`core/…/mobs/YogDzewa.java:681`, `core/…/mobs/Eye.java:76`, `core/…/mobs/Eye.java:71`, `core/…/mobs/Eye.java:81`</small>

#### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** baseSpeed 1 (no override) Speed `normal`; flies: yes; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/mobs/Eye.java:60`, `core/…/mobs/Mob.java:570`, `core/…/mobs/Mob.java:572`, `core/…/mobs/Mob.java:573`, `core/…/mobs/Mob.java:1686`, `core/…/mobs/Mob.java:1699`</small>
- **Attack.** `bolt`, reach 6. The beam is a Ballistica STOP_SOLID from the eye toward the aimed cell. It passes through characters and stops at walls and closed doors. Reach is limited by the eye's FOV (viewDistance 6). While the beam is on cooldown it melees adjacent targets only. Attack time: charge costs 2 turns, fire costs 1 turn, melee costs 1 turn. <small>`core/…/mobs/Eye.java:94`, `core/…/mobs/Eye.java:97`, `core/…/mobs/Eye.java:105`, `core/…/mobs/Eye.java:106`, `core/…/mobs/Eye.java:133`, `core/…/mobs/Eye.java:138`, `core/…/mobs/Eye.java:55`, `core/…/mechanics/Ballistica.java:130`</small>
- **Death gaze (charge).** When the beam cooldown is 0 and the target is visible, not invisible, and adjacent or on a clear STOP_SOLID line, it spends attackDelay\*2 = 2 turns charging. While charged it takes only 1/4 damage. Adjacent with the beam ready, it charges instead of biting. *Telegraph:* EyeSprite.charge: charging animation, attracting magic particles, CHARGEUP sound. The eye turns toward the target cell. <small>`core/…/mobs/Eye.java:93`, `core/…/mobs/Eye.java:94`, `core/…/mobs/Eye.java:96`, `core/…/mobs/Eye.java:97`, `core/…/mobs/Eye.java:131`, `core/…/mobs/Eye.java:132`, `core/…/mobs/Eye.java:133`, `core/…/mobs/Eye.java:134`, `core/…/mobs/Eye.java:154`, `core/…/sprites/EyeSprite.java:105`, `core/…/sprites/EyeSprite.java:107`, `core/…/sprites/EyeSprite.java:108`, `core/…/sprites/EyeSprite.java:74`</small>
- **Death gaze (fire).** On its next act, canAttack is re-evaluated. If the target is still in its FOV (not invisible, with a clear STOP_SOLID line), beamTarget is RE-AIMED at the target's CURRENT cell. Only if the target is out of its FOV or behind solid terrain does it fire at the old stored cell. The beam runs from the eye through the aimed cell until solid terrain. Every character on the line (allies and other mobs included) rolls a magic hit (x2 accuracy) for 30-50 damage, applied directly with no armor reduction. It also destroys flammable terrain on the line, including a closed door it stops at. Beam cooldown afterwards is 4-6 of its acts. If the eye leaves HUNTING while charged, the charge is lost. *Telegraph:* the charge above. Then a purple DeathRay beam. <small>`core/…/mobs/Eye.java:112`, `core/…/mobs/Eye.java:113`, `core/…/mobs/Eye.java:128`, `core/…/mobs/Eye.java:138`, `core/…/mobs/Eye.java:172`, `core/…/mobs/Eye.java:177`, `core/…/mobs/Eye.java:179`, `core/…/mobs/Eye.java:187`, `core/…/mobs/Eye.java:192`, `core/…/mobs/Eye.java:193`, `core/…/mobs/Eye.java:196`, `core/…/mobs/Eye.java:279`, `core/…/mobs/Eye.java:281`, `core/…/mobs/Eye.java:93`, `core/…/mobs/Eye.java:94`, `core/…/mobs/Eye.java:96`, `core/…/mobs/Eye.java:97`, `core/…/mobs/Eye.java:98`, `core/…/mobs/Eye.java:99`, `core/…/mobs/Eye.java:103`, `core/…/levels/Terrain.java:90`, `core/…/mobs/Mob.java:290`</small>
- **Boss minion.** Same Eye behaviour. BOSS_MINION, maxLvl -2. Only in the Stronger Bosses summon table, in slots gated by living Demon Spawners (i &lt; spawnersAlive, alternating YogEye/YogScorpio). Killed when Yog dies. *Telegraph:* pushed out of Yog <small>`core/…/mobs/YogDzewa.java:681`, `core/…/mobs/YogDzewa.java:684`, `core/…/mobs/YogDzewa.java:131`, `core/…/mobs/YogDzewa.java:138`, `core/…/mobs/YogDzewa.java:527`, `core/…/mobs/YogDzewa.java:135`, `core/…/mobs/YogDzewa.java:683`</small>
- **Resists.** Wand of Disintegration, Eye.DeathGaze (so its own beam and Yog's), Disintegration trap: x0.5 <small>`core/…/mobs/Eye.java:270`, `core/…/mobs/Eye.java:271`, `core/…/mobs/Eye.java:272`, `core/…/actors/Char.java:1368`</small>
- **AI.** Starts `wandering`; flees: never. Custom Hunting: if the beam is charged and the enemy is set, it fires even when the enemy is not in FOV. YogEye appears only under the Stronger Bosses challenge and dies with Yog. <small>`core/…/mobs/Eye.java:62`, `core/…/mobs/Eye.java:275`, `core/…/mobs/Eye.java:279`, `core/…/mobs/Mob.java:124`</small>
- **Evasion.** defenseSkill 20; evasive: no. defenseSkill 20. DR 0-10. Incoming damage x0.25 while the beam is charged. <small>`core/…/mobs/Eye.java:54`, `core/…/mobs/Eye.java:82`, `core/…/mobs/Eye.java:154`</small>
- **Surprise.** Can be surprised: yes. No override. Standard surprise rules. <small>`core/…/mobs/Mob.java:796`, `core/…/mobs/Mob.java:873`, `core/…/mobs/Mob.java:875`, `core/…/mobs/Mob.java:1237`</small>

#### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `close`; ranged attacks first: no.
- **Plan.** Treat the charge animation as a 2-turn warning, but don't just step sideways: while you stay in its view it re-aims at your new cell. Break line of sight (corner, closed door, out of its 6 tiles) or go invisible before it fires, or accept the hit. Right after it fires, close to melee and hit hard during the 4-6 act cooldown, when it can only bite for 20-30. When it starts charging again, stop attacking (1/4 damage) and break sight. Use doors and corners to approach. In the Yog arena, Light matters even more because Yog shrinks vision. <small>`core/…/mobs/Eye.java:133`, `core/…/mobs/Eye.java:154`, `core/…/mobs/Eye.java:172`, `core/…/levels/HallsLevel.java:66`</small>
- **Kill or nullify: Break its line of sight (or go invisible) during the 2-turn charge. Sidestepping in view does NOT dodge..** When the charged eye acts, canAttack re-aims beamTarget at your current cell if you are in its FOV with a clear STOP_SOLID line. Only when you are out of its FOV, invisible, or behind solid terrain does it fire at the stale cell. So get around a corner, behind a closed door or out of its 6-tile view, off the old line. (derived) <small>`core/…/mobs/Eye.java:94`, `core/…/mobs/Eye.java:96`, `core/…/mobs/Eye.java:97`, `core/…/mobs/Eye.java:99`, `core/…/mobs/Eye.java:103`, `core/…/mobs/Eye.java:128`, `core/…/mobs/Eye.java:279`, `core/…/mobs/Mob.java:290`</small>
- **Kill or nullify: Do not attack a charging eye.** It takes 1/4 damage while beamCharged. Use the 2 charge turns to reposition. (derived) <small>`core/…/mobs/Eye.java:154`</small>
- **Kill or nullify: Rush it in the 4-6 turn cooldown after it fires.** While beamCooldown &gt; 0 it can only melee adjacent (20-30), so that is the safe window to close in and do damage. (derived) <small>`core/…/mobs/Eye.java:105`, `core/…/mobs/Eye.java:106`, `core/…/mobs/Eye.java:172`, `core/…/mobs/Eye.java:120`</small>
- **Kill or nullify: Break the line with a closed door or wall, not with other monsters.** STOP_SOLID stops at solid cells (doors included) but passes through characters. The beam also burns a flammable closed door it reaches (Level.destroy -&gt; EMBERS), so a door blocks one beam and then it is gone. (derived) <small>`core/…/mobs/Eye.java:94`, `core/…/mechanics/Ballistica.java:130`, `core/…/mechanics/Ballistica.java:137`, `core/…/mobs/Eye.java:177`, `core/…/mobs/Eye.java:179`, `core/…/mobs/Eye.java:181`, `core/…/levels/Level.java:928`, `core/…/levels/Level.java:929`, `core/…/levels/Level.java:930`, `core/…/levels/Level.java:931`, `core/…/levels/Level.java:932`, `core/…/levels/Level.java:933`, `core/…/levels/Level.java:934`</small>
- **Kill or nullify: Line other enemies up in the beam.** The gaze damages every character on its path with no alignment check. (derived) <small>`core/…/mobs/Eye.java:187`, `core/…/mobs/Eye.java:196`</small>
- **Kill or nullify: AntiMagic armor.** DeathGaze is in AntiMagic.RESISTS, and armor DR doesn't apply to it. (derived) <small>`core/…/glyphs/AntiMagic.java:133`, `core/…/actors/Char.java:941`</small>
- **Kill or nullify: Ring of Elements.** Eye.DeathGaze is in AntiMagic.RESISTS, which Ring of Elements resists, and Char.damage multiplies by resist(src). (derived) <small>`core/…/rings/RingOfElements.java:85`, `core/…/rings/RingOfElements.java:93`, `core/…/glyphs/AntiMagic.java:133`, `core/…/actors/Char.java:929`, `core/…/actors/Char.java:1371`</small>
- **Escape.** Outrunnable: no; contact breaks at: out-of-sight. Same speed as the hero, flies, and sees 6 tiles. The arena is sealed until Yog dies. A charged beam fires at your last cell even without sight. <small>`core/…/mobs/Eye.java:60`, `core/…/mobs/Eye.java:55`, `core/…/levels/HallsLevel.java:66`, `core/…/mobs/Eye.java:279`, `core/…/levels/Terrain.java:90`, `core/…/mobs/Mob.java:1400`, `core/…/mobs/Mob.java:1401`, `core/…/mobs/Mob.java:1686`, `core/…/mobs/Mob.java:1699`, `core/…/levels/HallsBossLevel.java:246`</small>

| Class | Note |
|---|---|
| Warrior | Armor does not reduce the gaze. Break line of sight during charges and rely on the Broken Seal shield. |
| Mage | Wand of Prismatic Light deals x1.333 to this DEMONIC target (core/…/items/wands/WandOfPrismaticLight.java:96-100) and its hits are direct damage with no evasion roll. Disintegration is resisted (x0.5). |
| Rogue | An asleep eye has 0 defense. Open with a surprise hit, then dodge the first charge. |
| Huntress | Shoot during its cooldown. Shooting during the charge wastes arrows (x0.25). |
| Duelist | Round Shield guard gives INFINITE_EVASION against the magic hit roll (core/…/actors/hero/Hero.java:577, core/…/actors/Char.java:638). |
| Cleric | Smite and Holy Lance roll their maximum and Sunray deals flat 8/12 against DEMONIC targets (core/…/actors/hero/spells/Smite.java:123, core/…/actors/hero/spells/HolyLance.java:120, core/…/actors/hero/spells/Sunray.java:100). |

**Open questions.**

- Resolved (corrects the earlier reading): Eye.java:103 returns the stale beamCharged only when the fresh aim fails (target not visible, invisible, charming it, or line blocked by solid). A visible hero with a clear line is re-aimed at Eye.java:94-99 on the firing act (called from Eye.java:279). Not rig-tested.

#### Community notes

No community claim about this enemy was found.

??? note "Corrected during verification (1)"

    - Same MAJOR beam re-aim correction as Eye (a visible hero is re-targeted on the firing act). Removed stairs/door escape (sealed arena). Noted that its slots only exist when spawners are alive.

### Scorpio (Yog summon) {#yogdzewa-yogscorpio}

`actors.mobs.YogDzewa.YogScorpio` · depths boss 25 (summoned; Stronger Bosses challenge only) · **Tactic:** Close the distance using cover (corners, doors, other monsters between you) and fight it adjacent, where it can't attack.

**Stats** (from the [Codex](../codex/mobs.md)): HT 110 · accuracy 36 · evasion 24 · damage 30-40 · armour 0-16 · EXP 14 · max level -2 · properties BOSS_MINION, DEMONIC · loot POTION (50% base). <small>`core/…/mobs/YogDzewa.java:687`, `core/…/mobs/Scorpio.java:63`, `core/…/mobs/Scorpio.java:58`, `core/…/mobs/Scorpio.java:68`</small>

#### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** baseSpeed 1. While HUNTING with the enemy seen, getCloser is replaced by getFurther, so it backs away from its target. Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/mobs/Scorpio.java:89`, `core/…/mobs/Scorpio.java:90`, `core/…/mobs/Scorpio.java:91`, `core/…/mobs/Mob.java:714`, `core/…/mobs/Mob.java:1686`, `core/…/mobs/Mob.java:1699`</small>
- **Attack.** `ranged`, reach 6. It can attack only when NOT adjacent, along a PROJECTILE Ballistica whose collision is the target. The shot stops at the first character or wall, so another monster or a closed door blocks it. Reach is limited by its FOV (viewDistance 6). Attack time: 1 turn (default). <small>`core/…/mobs/Scorpio.java:73`, `core/…/mobs/Scorpio.java:74`, `core/…/mobs/Scorpio.java:75`, `core/…/mobs/Scorpio.java:46`, `core/…/mechanics/Ballistica.java:47`</small>
- **Kiting.** It retreats from a seen target instead of approaching it, and cannot attack when adjacent. *Telegraph:* none <small>`core/…/mobs/Scorpio.java:74`, `core/…/mobs/Scorpio.java:91`</small>
- **Sight-gated aggro.** aggro() is ignored unless the aggressor is in its FOV. Being hit from outside its view does not make it hunt the attacker. *Telegraph:* none <small>`core/…/mobs/Scorpio.java:98`, `core/…/mobs/Scorpio.java:101`, `core/…/mobs/Scorpio.java:102`, `core/…/mobs/Mob.java:836`</small>
- **Boss minion.** Same Scorpio behaviour. BOSS_MINION, maxLvl -2. Only in the Stronger Bosses summon table, in the slots that would be Rippers-per-spawner in normal mode (i &lt; spawnersAlive, alternating YogEye/YogScorpio). Killed when Yog dies. *Telegraph:* pushed out of Yog <small>`core/…/mobs/YogDzewa.java:687`, `core/…/mobs/YogDzewa.java:690`, `core/…/mobs/YogDzewa.java:131`, `core/…/mobs/YogDzewa.java:138`, `core/…/mobs/YogDzewa.java:527`, `core/…/mobs/YogDzewa.java:135`, `core/…/mobs/YogDzewa.java:689`</small>
- **Inflicts Cripple.** 50% chance on hit, prolonged to 10 turns. Halves speed. <small>`core/…/mobs/Scorpio.java:81`, `core/…/mobs/Scorpio.java:82`, `core/…/buffs/Cripple.java:28`, `core/…/actors/Char.java:772`</small>
- **AI.** Starts `wandering`; flees: kites: always moves away from a seen target while hunting. Hunting with a ranged canAttack. While hunting, getCloser becomes getFurther, and only when it currently sees the enemy. So if the target leaves its FOV, getCloser returns false and handleUnreachableTarget sends it straight to WANDERING (it shows 'lost' and does not walk to your last position). If it sees you but has no clear PROJECTILE line (a body in the way), it backs off instead of repositioning. If it can neither shoot nor retreat, it spends a turn. YogScorpio is beckoned to the hero on summon, and while WANDERING it uses normal getCloser and walks toward the hero. Stronger Bosses only, one of the first four slots per Demon Spawner still alive (alternating Eye/Scorpio). <small>`core/…/mobs/Scorpio.java:91`, `core/…/mobs/Mob.java:1360`, `core/…/mobs/Mob.java:1400`, `core/…/mobs/Mob.java:124`, `core/…/mobs/Scorpio.java:90`, `core/…/mobs/Mob.java:1327`, `core/…/mobs/Mob.java:1353`, `core/…/mobs/Mob.java:1401`, `core/…/mobs/Mob.java:1402`, `core/…/mobs/Mob.java:1403`, `core/…/mobs/YogDzewa.java:132`, `core/…/mobs/YogDzewa.java:133`, `core/…/mobs/YogDzewa.java:135`, `core/…/mobs/YogDzewa.java:138`, `core/…/mobs/YogDzewa.java:330`, `core/…/mobs/Mob.java:1143`, `core/…/mobs/Mob.java:1144`, `core/…/mobs/Mob.java:1146`, `core/…/mobs/Scorpio.java:93`</small>
- **Evasion.** defenseSkill 24; evasive: no. defenseSkill 24. DR 0-16 (highest regular-mob DR in the cohort), so weak multi-hit weapons suffer. <small>`core/…/mobs/Scorpio.java:45`, `core/…/mobs/Scorpio.java:69`</small>
- **Surprise.** Can be surprised: yes. Standard rules. aggro() needs sight, but sniping from beyond its 6-tile view is not generally possible: with Light the hero's vision is also 6 in the Halls. Surprise hits come from a sleeping scorpio (enemySeen false), from invisibility, or after it has lost you and gone WANDERING. <small>`core/…/mobs/Mob.java:796`, `core/…/mobs/Mob.java:873`, `core/…/mobs/Mob.java:875`, `core/…/mobs/Mob.java:1237`, `core/…/mobs/Scorpio.java:102`, `core/…/buffs/Light.java:36`, `core/…/buffs/Light.java:42`, `core/…/shatteredpixeldungeon/Dungeon.java:506`</small>

#### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `close`; ranged attacks first: no.
- **Plan.** Close the distance using cover (corners, doors, other monsters between you) and fight it adjacent, where it can't attack. Herd it toward a dead end so its retreat fails. Don't trade shots at range against its 30-40 damage and 50% cripple. Open with surprise hits on sleeping scorpios. Once it loses sight of you it wanders, and an unaware scorpio has 0 defense. In the Yog arena, standing adjacent also keeps you out of its shots while you dodge beams. <small>`core/…/mobs/Scorpio.java:74`, `core/…/mobs/Scorpio.java:91`, `core/…/mobs/Scorpio.java:102`</small>
- **Kill or nullify: Get adjacent and stay adjacent.** canAttack requires !adjacent, so an adjacent scorpio cannot hit you at all. (derived) <small>`core/…/mobs/Scorpio.java:74`</small>
- **Kill or nullify: Pin it in a dead end or corner.** Its only response to adjacency is getFurther. When no retreat step exists it just spends a turn. (derived) <small>`core/…/mobs/Scorpio.java:91`, `core/…/mobs/Mob.java:719`, `core/…/mobs/Mob.java:1400`</small>
- **Kill or nullify: Block its line with a body or a door.** PROJECTILE shots stop at the first character or solid cell. (derived) <small>`core/…/mobs/Scorpio.java:75`, `core/…/mechanics/Ballistica.java:47`</small>
- **Kill or nullify: Open on it while it is asleep or has lost you.** Sleeping or WANDERING-after-losing-you scorpios have enemySeen false, so hits on them are surprise hits (defense 0). Out-ranging its 6-tile FOV isn't practical: Light only raises hero vision to 6. (derived) <small>`core/…/mobs/Mob.java:796`, `core/…/mobs/Mob.java:875`, `core/…/mobs/Mob.java:1237`, `core/…/buffs/Light.java:36`, `core/…/buffs/Light.java:42`</small>
- **Kill or nullify: Cure or avoid Cripple before trying to run.** It never chases, but Cripple halves your speed, so every tile you walk while in its view costs two turns and gives it two shots. Break sight in as few steps as possible. (derived) <small>`core/…/mobs/Scorpio.java:82`, `core/…/actors/Char.java:772`</small>
- **Kill or nullify: Break line of sight once and it gives up.** Out of its FOV, its hunting getCloser returns false (enemySeen is false) and it drops to WANDERING at once. It does not pursue your last position, and it has to re-notice you by the wandering detection roll. (derived) <small>`core/…/mobs/Scorpio.java:90`, `core/…/mobs/Scorpio.java:91`, `core/…/mobs/Mob.java:1327`, `core/…/mobs/Mob.java:1360`, `core/…/mobs/Mob.java:1401`, `core/…/mobs/Mob.java:1403`, `core/…/mobs/Mob.java:1270`, `core/…/mobs/Mob.java:1283`</small>
- **Escape.** Outrunnable: yes; contact breaks at: out-of-sight. It never approaches while hunting (it only backs away) and gives up the moment you leave its FOV. It shoots anything in its 6-tile FOV along a clear PROJECTILE line. Break line of sight around a corner or through a door. A crippled hero spends 2 turns per tile in view. The boss level is sealed during the fight, so stairs are not an option. <small>`core/…/mobs/Scorpio.java:73`, `core/…/mobs/Scorpio.java:91`, `core/…/levels/Terrain.java:90`, `core/…/mobs/Mob.java:1400`, `core/…/mobs/Mob.java:1401`, `core/…/mobs/Mob.java:1686`, `core/…/mobs/Mob.java:1699`, `core/…/mobs/Scorpio.java:90`, `core/…/mobs/Mob.java:1403`, `core/…/levels/HallsBossLevel.java:246`, `core/…/mobs/YogDzewa.java:546`</small>

| Class | Note |
|---|---|
| Warrior | Walk in adjacent. Armor reduces its hits (physical). Don't knock it away from you: at 2+ tiles it can shoot again. |
| Mage | Wand of Prismatic Light deals x1.333 to this DEMONIC target (core/…/items/wands/WandOfPrismaticLight.java:96-100) and its hits are direct damage with no evasion roll. Wand damage skips its 0-16 DR. |
| Rogue | Sleeping or unaware scorpios have defense 0. Invisibility lets you reach adjacency unshot. |
| Huntress | Trading at range is its game (36 accuracy, 30-40 damage, 50% cripple). Close to melee using cover, or shoot sleeping or unaware ones. |
| Duelist | Stay adjacent. High DR favours heavy single hits over rapid light ones. |
| Cleric | Smite and Holy Lance roll their maximum and Sunray deals flat 8/12 against DEMONIC targets (core/…/actors/hero/spells/Smite.java:123, core/…/actors/hero/spells/HolyLance.java:120, core/…/actors/hero/spells/Sunray.java:100). |

**Open questions.**

- Whether a scorpio in a 1-tile corridor with the hero adjacent always fails getFurther depends on Dungeon.flee pathing (core/…/actors/mobs/Mob.java:719). Not traced further.

#### Community notes

- **Tier 1.** “Scorpios shoot a ranged spike attack and behave similarly to the Gnoll Trickster: they have no melee attack and will try to flee if the Hero gets adjacent. Their ranged hits deal physical damage (reducible by armor) and have a chance to inflict Cripple. They are described as very manageable if you fight from a doorway, though repeated door use can eventually break the door.” [source](https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies) (version: not stated). Core mechanics confirmed: no attack when adjacent, retreats (getFurther) while hunting, the shot is a normal attack so armor DR applies, and 50% Cripple. It backs away whenever it sees you, not only when you are adjacent. The door caveat is wrong: doors have no durability and only toggle DOOR/OPEN_DOOR (core/…/levels/features/Door.java:36,54). They are removed only by fire or destroy. See the split entry below. Acidic inherits all of this from Scorpio. YogScorpio inherits all of this from Scorpio. <small>`core/…/mobs/Scorpio.java:74`, `core/…/mobs/Scorpio.java:75`, `core/…/mobs/Scorpio.java:81`, `core/…/mobs/Scorpio.java:82`, `core/…/mobs/Scorpio.java:90`, `core/…/mobs/Scorpio.java:91`, `core/…/actors/Char.java:388`, `core/…/actors/Char.java:390`</small>
- **Tier F.** “<del>Split from the claim above: 'repeated door use can eventually break the door.'</del>” [source](https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies) (version: not stated). Doors only switch between DOOR and OPEN_DOOR. There is no use counter. A door is removed only when flammable terrain is destroyed (fire, the Eye/Yog beams), which leaves EMBERS. <small>`core/…/features/Door.java:36`, `core/…/features/Door.java:54`, `core/…/levels/Level.java:928`, `core/…/levels/Level.java:929`, `core/…/levels/Level.java:930`, `core/…/levels/Level.java:931`, `core/…/levels/Level.java:932`, `core/…/levels/Level.java:933`, `core/…/levels/Level.java:934`</small>

??? note "Corrected during verification (3)"

    - Removed 'snipe from beyond 6 tiles with Light': Light only raises hero vision to max(level, 6) = 6 in the Halls (Light.java:42, Dungeon.java:506), equal to its viewDistance.
    - AI/escape: added that a hunting scorpio drops to WANDERING the moment it loses sight (Scorpio.java:91 + Mob.java:1360/1401-1403) and never pursues. escape.outrunnable set to true. The cripple counter was reworded: it never chases, and cripple doubles your time spent in its view.
    - Removed 'stairs'/'door' from breakContact: the Yog arena is sealed until Yog dies. Clarified that it only appears in slots gated by living spawners (YogDzewa.java:135-138).

### Burning fist {#yogfist-burningfist}

`actors.mobs.YogFist.BurningFist` · depths boss 25 (Yog fist) · **Tactic:** Pull it away from Yog. Stay out of its 3x3 fire aura and break its MAGIC_BOLT line (wall, closed door or a body) to deny zaps.

**Stats** (from the [Codex](../codex/mobs.md)): HT 300 · accuracy 36 · evasion 20 · damage 18-36 · armour 0-15 · EXP 25 · max level -2 · properties BOSS, DEMONIC, FIERY. <small>`core/…/mobs/YogFist.java:216`, `core/…/mobs/YogFist.java:179`, `core/…/mobs/YogFist.java:184`, `core/…/mobs/YogFist.java:189`</small>

#### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** baseSpeed 1 (no override) Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/mobs/YogFist.java:65`, `core/…/actors/Char.java:771`</small>
- **Attack.** `bolt`, reach 6. The zap needs a Ballistica MAGIC_BOLT (stops at the first character or wall) whose collision is the target. A body or a closed door in between blocks it. Otherwise it melees adjacent. Reach is limited by FOV (viewDistance 6). Attack time: zap: 1 turn. Melee: 1 turn. Ranged zap whenever rangedCooldown &lt;= 0, even when adjacent (canRangedInMelee). Each zap adds 8-12 turns of cooldown, and melee is used in between.. <small>`core/…/mobs/YogFist.java:105`, `core/…/mobs/YogFist.java:106`, `core/…/mobs/YogFist.java:108`, `core/…/mobs/YogFist.java:131`, `core/…/mobs/YogFist.java:137`, `core/…/mobs/YogFist.java:87`, `core/…/mobs/YogFist.java:71`, `core/…/mobs/YogFist.java:258`, `core/…/mobs/YogFist.java:261`, `core/…/mobs/YogFist.java:262`, `core/…/mobs/YogFist.java:266`, `core/…/mobs/YogFist.java:269`, `core/…/mobs/YogFist.java:273`</small>
- **Near-Yog invulnerability.** Invulnerable while within 4 tiles of Yog's cell (exit + 3 rows). It spawns right below the exit, inside that zone. It logs a warning the first time. *Telegraph:* log 'invuln_warn' message <small>`core/…/mobs/YogFist.java:114`, `core/…/mobs/YogFist.java:115`, `core/…/mobs/YogFist.java:116`, `core/…/mobs/YogFist.java:121`, `core/…/mobs/YogFist.java:123`, `core/…/mobs/YogFist.java:125`, `core/…/mobs/YogDzewa.java:452`</small>
- **Relentless hunting.** Whenever it is wandering and the hero is not invisible, it beckons to the hero and hunts them. It starts HUNTING. *Telegraph:* none <small>`core/…/mobs/YogFist.java:77`, `core/…/mobs/YogFist.java:94`, `core/…/mobs/YogFist.java:95`, `core/…/mobs/YogFist.java:96`</small>
- **Zap.** Every 8-12 of its acts (no hit roll): if the hero stands on WATER, that tile is evaporated to EMPTY. Otherwise Burning is applied to the hero. Then it seeds Fire on every non-water, non-solid cell of the hero's 3x3. The evaporated tile is no longer water at that point, so fire is seeded on the hero's own cell too. *Telegraph:* a MagicMissile bolt from the fist (FistSprite zap animation) when visible <small>`core/…/mobs/YogFist.java:258`, `core/…/mobs/YogFist.java:261`, `core/…/mobs/YogFist.java:262`, `core/…/mobs/YogFist.java:266`, `core/…/mobs/YogFist.java:269`, `core/…/mobs/YogFist.java:273`, `core/…/sprites/FistSprite.java:122`, `core/…/sprites/FistSprite.java:126`, `core/…/mobs/YogFist.java:259`, `core/…/mobs/YogFist.java:270`, `core/…/levels/Level.java:973`, `core/…/levels/Level.java:987`</small>
- **Fire aura.** Each act it evaporates water on its own cell and on 0-2 random neighbours (1.67 on average), and seeds Fire on every non-water, non-solid cell of its 3x3. *Telegraph:* steam puffs and flames around it <small>`core/…/mobs/YogFist.java:229`, `core/…/mobs/YogFist.java:230`, `core/…/mobs/YogFist.java:236`, `core/…/mobs/YogFist.java:240`, `core/…/mobs/YogFist.java:247`, `core/…/mobs/YogFist.java:249`, `core/…/mobs/YogFist.java:250`</small>
- **Inflicts Burning.** Reignite (8 turns). Water ends it after its first tick. After 4+ ticks of burning, each tick has a (ticks-3)/3 chance to burn a non-unique scroll or raw/frozen meat in the pack. <small>`core/…/mobs/YogFist.java:266`, `core/…/buffs/Burning.java:54`, `core/…/buffs/Burning.java:99`, `core/…/buffs/Burning.java:129`, `core/…/buffs/Burning.java:122`</small>
- **Immune.** Sleep (all fists). BOSS: AllyBuff, Dread. FIERY: Burning, Blazing. Also Frost. <small>`core/…/mobs/YogFist.java:194`, `core/…/mobs/YogFist.java:281`, `core/…/actors/Char.java:1415`, `core/…/actors/Char.java:1424`</small>
- **Resists.** BOSS: Grim, GrimTrap, Retribution, Psionic Blast (x0.5) <small>`core/…/actors/Char.java:1414`</small>
- **Resists.** Wand of Fireblast, Fire Elemental (FIERY). Storm Cloud, Geyser trap. <small>`core/…/actors/Char.java:1423`, `core/…/mobs/YogFist.java:283`, `core/…/mobs/YogFist.java:284`</small>
- **AI.** Starts `hunting`; flees: never. Starts HUNTING and re-acquires the hero whenever wandering unless the hero is invisible. Its death calls Yog.processFistDeath; the last fist in phase 4 triggers Yog's final phase. <small>`core/…/mobs/YogFist.java:77`, `core/…/mobs/YogFist.java:94`, `core/…/mobs/YogFist.java:163`, `core/…/mobs/YogFist.java:166`, `core/…/mobs/YogDzewa.java:362`</small>
- **Evasion.** defenseSkill 20; evasive: no. defenseSkill 20, HT 300, DR 0-15, attack 36, melee 18-36 (Rusted 22-44). <small>`core/…/mobs/YogFist.java:68`, `core/…/mobs/YogFist.java:69`, `core/…/mobs/YogFist.java:180`, `core/…/mobs/YogFist.java:185`, `core/…/mobs/YogFist.java:190`</small>
- **Surprise.** Can be surprised: yes. No override, but it always knows the hero (auto-beckon), so surprise only happens when the hero is invisible or outside its FOV. <small>`core/…/mobs/Mob.java:796`, `core/…/mobs/Mob.java:873`, `core/…/mobs/Mob.java:875`, `core/…/mobs/Mob.java:1237`, `core/…/mobs/YogFist.java:94`</small>

#### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `close`; ranged attacks first: no.
- **Plan.** Pull it away from Yog. Stay out of its 3x3 fire aura and break its MAGIC_BOLT line (wall, closed door or a body) to deny zaps. Keep a water tile nearby to step into and put out Burning; standing on water does not stop the zap's fire. Hit it with ranged weapons, non-fire wands, or reach weapons from outside the burning cells. Note it still zaps when adjacent if its cooldown is ready. Keep scrolls safe. <small>`core/…/mobs/YogFist.java:116`, `core/…/mobs/YogFist.java:131`</small>
- **Kill or nullify: Drag it 5+ tiles from Yog before attacking.** It is invulnerable within 4 tiles of Yog. (derived) <small>`core/…/mobs/YogFist.java:116`, `core/…/mobs/YogFist.java:125`</small>
- **Kill or nullify: Put a minion or other body between you and it to block the zap.** The zap requires a MAGIC_BOLT line whose collision is the target, and MAGIC_BOLT stops at characters. (derived) <small>`core/…/mobs/YogFist.java:106`, `core/…/mechanics/Ballistica.java:49`</small>
- **Kill or nullify: Keep water nearby to put out Burning, but don't expect water to block the zap.** A zap on a hero standing in water evaporates that tile (Level.set -&gt; EMPTY, which clears the water flag) and then seeds fire on the now-dry tile and its neighbours, so water only saves you the direct Burning, not the fire. Burning detaches when you stand in water after its first tick, so a separate water tile to step into is the real value. It also evaporates water around itself each act. (derived) <small>`core/…/mobs/YogFist.java:229`, `core/…/mobs/YogFist.java:236`, `core/…/mobs/YogFist.java:240`, `core/…/mobs/YogFist.java:261`, `core/…/mobs/YogFist.java:262`, `core/…/mobs/YogFist.java:269`, `core/…/mobs/YogFist.java:270`, `core/…/mobs/YogFist.java:273`, `core/…/levels/Level.java:973`, `core/…/levels/Level.java:987`, `core/…/buffs/Burning.java:99`</small>
- **Kill or nullify: Don't stand next to it.** Its 3x3 is re-seeded with fire every act, so melee range means standing in flames. (derived) <small>`core/…/mobs/YogFist.java:247`, `core/…/mobs/YogFist.java:250`</small>
- **Kill or nullify: Don't use fire or frost against it.** Immune to Burning and Frost, and resists Fireblast. (derived) <small>`core/…/actors/Char.java:1424`, `core/…/mobs/YogFist.java:281`, `core/…/actors/Char.java:1423`</small>
- **Kill or nullify: Protect scrolls.** Burning on the hero destroys non-unique scrolls (and meat) in the pack. (derived) <small>`core/…/buffs/Burning.java:129`</small>
- **Kill or nullify: Break its line of sight or put a body in the line to force it to walk.** Its ranged attack needs a MAGIC_BOLT line whose collision is you, and Hunting only attacks with the enemy in FOV. Out of line, it must walk toward you, and while walking it can't zap. (derived) <small>`core/…/mobs/YogFist.java:105`, `core/…/mobs/YogFist.java:106`, `core/…/mobs/Mob.java:1328`, `core/…/mobs/Mob.java:1353`, `core/…/mechanics/Ballistica.java:49`</small>
- **Escape.** Outrunnable: no; contact breaks at: invisibility. Same speed as the hero, auto-re-targets the hero unless the hero is invisible, and the level is sealed during the fight. <small>`core/…/mobs/YogFist.java:94`, `core/…/mobs/YogDzewa.java:546`</small>

| Class | Note |
|---|---|
| Warrior | Melee means standing in fire and still eating zaps (canRangedInMelee). Hit and step back, and put out Burning in water. |
| Mage | Wand of Prismatic Light deals x1.333 to this DEMONIC target (core/…/items/wands/WandOfPrismaticLight.java:96-100) and its hits are direct damage with no evasion roll. Avoid Fireblast/Frost (immune or resisted). |
| Rogue | Keep distance and use thrown weapons. Its zap doesn't roll to hit, so evasion doesn't help. |
| Huntress | Arrows from range, from cover that breaks its line between shots. Keep a water tile nearby for Burning. |
| Duelist | Reach weapons (Spear/Glaive) can hit it from 2 tiles, outside the 3x3 aura. |
| Cleric | Smite and Holy Lance roll their maximum and Sunray deals flat 8/12 against DEMONIC targets (core/…/actors/hero/spells/Smite.java:123, core/…/actors/hero/spells/HolyLance.java:120, core/…/actors/hero/spells/Sunray.java:100). |

#### Community notes

- **Tier F.** “<del>A Potion of Paralytic Gas only affects the Burning Fist in this encounter; the Rotting Fist is immune to paralysis, so don't waste a paralytic gas potion trying to lock it down.</del>” [source](https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Bosses) (version: not stated). Half right for this fist: Paralysis does affect the Burning fist (its immunities are Sleep, Frost, Burning/Blazing, AllyBuff, Dread). But the claim that it is the ONLY fist affected is wrong: no fist is paralysis-immune. <small>`core/…/mobs/YogFist.java:194`, `core/…/mobs/YogFist.java:281`, `core/…/actors/Char.java:1415`, `core/…/actors/Char.java:1424`</small>
- **Tier 1.** “After the Rotting Fist is dead, deal with the Burning Fist by hiding behind a wall or otherwise out of its spell range to force it to close distance before you engage it in melee.” [source](https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Bosses) (version: not stated). The mechanism holds: its ranged zap needs a clear MAGIC_BOLT line and the hero in FOV, so hiding behind a wall forces it to walk to you. Caveats: it still zaps when adjacent if its 8-12 act cooldown is up (canRangedInMelee), and melee means standing in its 3x3 fire. The 'after the Rotting Fist is dead' framing comes from an older two-fist Yog. Now the three fists come one at a time in seeded random order. <small>`core/…/mobs/YogFist.java:105`, `core/…/mobs/YogFist.java:106`, `core/…/mobs/YogFist.java:131`, `core/…/mobs/Mob.java:1328`, `core/…/mobs/Mob.java:1353`</small>
- **Tier F.** “<del>The Burning Fist is especially weak against Bleeding, Poison, and Vertigo debuffs; inflict these before or during the fight for extra damage-over-time and crowd control.</del>” [source](https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Bosses) (version: not stated). The code has no special weakness. BurningFist has no damage multiplier or vulnerability for Bleeding, Poison or Vertigo. They apply normally because it is not immune to them (FIERY only grants Burning/Blazing immunity), but they do no extra damage. <small>`core/…/mobs/YogFist.java:216`, `core/…/mobs/YogFist.java:218`, `core/…/mobs/YogFist.java:219`, `core/…/mobs/YogFist.java:221`, `core/…/mobs/YogFist.java:222`, `core/…/mobs/YogFist.java:224`, `core/…/mobs/YogFist.java:225`, `core/…/mobs/YogFist.java:227`, `core/…/mobs/YogFist.java:229`, `core/…/mobs/YogFist.java:230`, `core/…/mobs/YogFist.java:231`, `core/…/mobs/YogFist.java:232`, `core/…/mobs/YogFist.java:233`, `core/…/mobs/YogFist.java:235`, `core/…/mobs/YogFist.java:236`, `core/…/mobs/YogFist.java:238`, `core/…/mobs/YogFist.java:239`, `core/…/mobs/YogFist.java:240`, `core/…/mobs/YogFist.java:241`, `core/…/mobs/YogFist.java:242`, `core/…/mobs/YogFist.java:243`, `core/…/mobs/YogFist.java:244`, `core/…/mobs/YogFist.java:245`, `core/…/mobs/YogFist.java:247`, `core/…/mobs/YogFist.java:248`, `core/…/mobs/YogFist.java:249`, `core/…/mobs/YogFist.java:250`, `core/…/mobs/YogFist.java:251`, `core/…/mobs/YogFist.java:252`, `core/…/mobs/YogFist.java:254`, `core/…/mobs/YogFist.java:255`, `core/…/mobs/YogFist.java:257`, `core/…/mobs/YogFist.java:258`, `core/…/mobs/YogFist.java:259`, `core/…/mobs/YogFist.java:261`, `core/…/mobs/YogFist.java:262`, `core/…/mobs/YogFist.java:263`, `core/…/mobs/YogFist.java:264`, `core/…/mobs/YogFist.java:265`, `core/…/mobs/YogFist.java:266`, `core/…/mobs/YogFist.java:267`, `core/…/mobs/YogFist.java:269`, `core/…/mobs/YogFist.java:270`, `core/…/mobs/YogFist.java:271`, `core/…/mobs/YogFist.java:272`, `core/…/mobs/YogFist.java:273`, `core/…/mobs/YogFist.java:274`, `core/…/mobs/YogFist.java:275`, `core/…/mobs/YogFist.java:276`, `core/…/mobs/YogFist.java:278`, `core/…/mobs/YogFist.java:280`, `core/…/mobs/YogFist.java:281`, `core/…/mobs/YogFist.java:283`, `core/…/mobs/YogFist.java:284`, `core/…/mobs/YogFist.java:285`, `core/…/mobs/YogFist.java:287`, `core/…/actors/Char.java:1423`, `core/…/actors/Char.java:1424`</small>

??? note "Corrected during verification (2)"

    - Citation YogFist.java:257 pointed at @Override; moved to 258 (zap()).
    - Counter 'fight it from water: a zap only evaporates the tile' was wrong. After Level.set(EMPTY) clears water[] (Level.java:987), the NEIGHBOURS9 fire loop (YogFist.java:269-273) seeds fire on the hero's own cell as well. Rewritten.

### Soiled fist {#yogfist-soiledfist}

`actors.mobs.YogFist.SoiledFist` · depths boss 25 (Yog fist) · **Tactic:** Pull it off Yog and check its 3x3: the more furrowed or high grass, the less damage you do (zero at 6 or more).

**Stats** (from the [Codex](../codex/mobs.md)): HT 300 · accuracy 36 · evasion 20 · damage 18-36 · armour 0-15 · EXP 25 · max level -2 · properties BOSS, DEMONIC. <small>`core/…/mobs/YogFist.java:289`, `core/…/mobs/YogFist.java:179`, `core/…/mobs/YogFist.java:184`, `core/…/mobs/YogFist.java:189`</small>

#### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** baseSpeed 1 (no override) Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/mobs/YogFist.java:65`, `core/…/actors/Char.java:771`</small>
- **Attack.** `bolt`, reach 6. The zap needs a Ballistica MAGIC_BOLT (stops at the first character or wall) whose collision is the target. A body or a closed door in between blocks it. Otherwise it melees adjacent. Reach is limited by FOV (viewDistance 6). Attack time: zap: 1 turn. Melee: 1 turn. Ranged zap whenever rangedCooldown &lt;= 0, even when adjacent (canRangedInMelee). Each zap adds 8-12 turns of cooldown, and melee is used in between.. <small>`core/…/mobs/YogFist.java:105`, `core/…/mobs/YogFist.java:106`, `core/…/mobs/YogFist.java:108`, `core/…/mobs/YogFist.java:131`, `core/…/mobs/YogFist.java:137`, `core/…/mobs/YogFist.java:87`, `core/…/mobs/YogFist.java:71`, `core/…/mobs/YogFist.java:360`, `core/…/mobs/YogFist.java:365`, `core/…/mobs/YogFist.java:367`, `core/…/mobs/YogFist.java:374`, `core/…/mobs/YogFist.java:377`, `core/…/mobs/YogFist.java:381`</small>
- **Near-Yog invulnerability.** Invulnerable while within 4 tiles of Yog's cell (exit + 3 rows). It spawns right below the exit, inside that zone. It logs a warning the first time. *Telegraph:* log 'invuln_warn' message <small>`core/…/mobs/YogFist.java:114`, `core/…/mobs/YogFist.java:115`, `core/…/mobs/YogFist.java:116`, `core/…/mobs/YogFist.java:121`, `core/…/mobs/YogFist.java:123`, `core/…/mobs/YogFist.java:125`, `core/…/mobs/YogDzewa.java:452`</small>
- **Relentless hunting.** Whenever it is wandering and the hero is not invisible, it beckons to the hero and hunts them. It starts HUNTING. *Telegraph:* none <small>`core/…/mobs/YogFist.java:77`, `core/…/mobs/YogFist.java:94`, `core/…/mobs/YogFist.java:95`, `core/…/mobs/YogFist.java:96`</small>
- **Zap.** Every 8-12 turns: a magic hit (x2 accuracy) roots the hero for 3 turns. Grass (1 in 5 furrowed) is then spread on free cells around the hero outside Yog's 4-tile zone. *Telegraph:* a MagicMissile bolt from the fist (FistSprite zap animation) when visible <small>`core/…/mobs/YogFist.java:360`, `core/…/mobs/YogFist.java:365`, `core/…/mobs/YogFist.java:367`, `core/…/mobs/YogFist.java:374`, `core/…/mobs/YogFist.java:377`, `core/…/mobs/YogFist.java:381`, `core/…/sprites/FistSprite.java:122`, `core/…/sprites/FistSprite.java:126`</small>
- **Grass armor.** Each act: 1 (2/3 chance) or 2 (1/3) random cells of its 3x3 that are GRASS become FURROWED_GRASS. Then every cell of its 3x3 that is &gt;4 tiles from Yog, not solid and not already furrowed or high grass becomes GRASS. Incoming damage is multiplied by (6-n)/6, where n = FURROWED or HIGH grass cells in its 3x3, so 6 or more means 0 damage. Burning deals no damage to it, although it can be ignited. Furrowed and high grass also block line of sight. *Telegraph:* leaf particles and visible grass tiles around it <small>`core/…/mobs/YogFist.java:301`, `core/…/mobs/YogFist.java:305`, `core/…/mobs/YogFist.java:306`, `core/…/mobs/YogFist.java:316`, `core/…/mobs/YogFist.java:317`, `core/…/mobs/YogFist.java:328`, `core/…/mobs/YogFist.java:329`, `core/…/mobs/YogFist.java:330`, `core/…/mobs/YogFist.java:334`, `core/…/mobs/YogFist.java:337`, `core/…/mobs/YogFist.java:338`, `core/…/mobs/YogFist.java:391`, `core/…/mobs/YogFist.java:392`, `core/…/mobs/YogFist.java:393`, `core/…/mobs/YogFist.java:394`, `core/…/levels/Terrain.java:103`, `core/…/levels/Terrain.java:104`, `core/…/actors/Char.java:828`</small>
- **Inflicts Roots.** 3 turns. The hero can't move. Yog's queued beams also wait while the hero is rooted. <small>`core/…/mobs/YogFist.java:367`, `core/…/buffs/Roots.java:39`, `core/…/mobs/YogDzewa.java:194`</small>
- **Immune.** Sleep (all fists). BOSS: AllyBuff, Dread. <small>`core/…/mobs/YogFist.java:194`, `core/…/actors/Char.java:1415`</small>
- **Resists.** BOSS: Grim, GrimTrap, Retribution, Psionic Blast (x0.5) <small>`core/…/actors/Char.java:1414`</small>
- **AI.** Starts `hunting`; flees: never. Starts HUNTING and re-acquires the hero whenever wandering unless the hero is invisible. Its death calls Yog.processFistDeath; the last fist in phase 4 triggers Yog's final phase. <small>`core/…/mobs/YogFist.java:77`, `core/…/mobs/YogFist.java:94`, `core/…/mobs/YogFist.java:163`, `core/…/mobs/YogFist.java:166`, `core/…/mobs/YogDzewa.java:362`</small>
- **Evasion.** defenseSkill 20; evasive: no. defenseSkill 20, HT 300, DR 0-15, attack 36, melee 18-36 (Rusted 22-44). <small>`core/…/mobs/YogFist.java:68`, `core/…/mobs/YogFist.java:69`, `core/…/mobs/YogFist.java:180`, `core/…/mobs/YogFist.java:185`, `core/…/mobs/YogFist.java:190`</small>
- **Surprise.** Can be surprised: yes. No override, but it always knows the hero (auto-beckon), so surprise only happens when the hero is invisible or outside its FOV. <small>`core/…/mobs/Mob.java:796`, `core/…/mobs/Mob.java:873`, `core/…/mobs/Mob.java:875`, `core/…/mobs/Mob.java:1237`, `core/…/mobs/YogFist.java:94`</small>

#### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `close`; ranged attacks first: no.
- **Plan.** Pull it off Yog and check its 3x3: the more furrowed or high grass, the less damage you do (zero at 6 or more). Burn the grass (fire does it no harm, but the grass is what matters) or drag it onto fresh stone, then burst it. Its root zap is magic-accuracy, so expect to be rooted for 3 turns every 8-12. Yog's beams wait while you're rooted. <small>`core/…/mobs/YogFist.java:116`, `core/…/mobs/YogFist.java:131`</small>
- **Kill or nullify: Drag it 5+ tiles from Yog before attacking.** It is invulnerable within 4 tiles of Yog. (derived) <small>`core/…/mobs/YogFist.java:116`, `core/…/mobs/YogFist.java:125`</small>
- **Kill or nullify: Put a minion or other body between you and it to block the zap.** The zap requires a MAGIC_BOLT line whose collision is the target, and MAGIC_BOLT stops at characters. (derived) <small>`core/…/mobs/YogFist.java:106`, `core/…/mechanics/Ballistica.java:49`</small>
- **Kill or nullify: Burn or clear the grass around it before hitting.** Damage is scaled by the count of furrowed/high grass in its 3x3. Fire removes grass, and the fire can't hurt the fist but doesn't need to. (derived) <small>`core/…/mobs/YogFist.java:334`, `core/…/mobs/YogFist.java:337`</small>
- **Kill or nullify: Keep it moving onto fresh floor.** It re-grasses its 3x3 each act, but furrowing (what counts) happens only 1.33 tiles per act on average, so a freshly moved fist has few furrowed cells. (derived) <small>`core/…/mobs/YogFist.java:301`, `core/…/mobs/YogFist.java:317`, `core/…/mobs/YogFist.java:329`</small>
- **Kill or nullify: Being rooted pauses Yog's beams.** Yog holds its beams while the hero is rooted. (derived) <small>`core/…/mobs/YogDzewa.java:194`</small>
- **Escape.** Outrunnable: no; contact breaks at: invisibility. Same speed as the hero, auto-re-targets the hero unless the hero is invisible, and the level is sealed during the fight. <small>`core/…/mobs/YogFist.java:94`, `core/…/mobs/YogDzewa.java:546`</small>

| Class | Note |
|---|---|
| Warrior | Watch the grass count before committing attacks. |
| Mage | Wand of Prismatic Light deals x1.333 to this DEMONIC target (core/…/items/wands/WandOfPrismaticLight.java:96-100) and its hits are direct damage with no evasion roll. Fire wands clear its grass armor. |
| Rogue | Surprise doesn't bypass the grass multiplier. Clear grass first. |
| Huntress | Shoot when it is on fresh floor. Arrows into a grass-armored fist are wasted. |
| Duelist | Heavy hits right after dragging it onto clean floor. |
| Cleric | Smite and Holy Lance roll their maximum and Sunray deals flat 8/12 against DEMONIC targets (core/…/actors/hero/spells/Smite.java:123, core/…/actors/hero/spells/HolyLance.java:120, core/…/actors/hero/spells/Sunray.java:100). |

**Open questions.**

- Whether the hero trampling HIGH_GRASS or FURROWED_GRASS near it lowers n was not traced. Only FURROWED and HIGH count (YogFist.java:329-330).

#### Community notes

No community claim about this enemy was found.

### Rotting fist {#yogfist-rottingfist}

`actors.mobs.YogFist.RottingFist` · depths boss 25 (Yog fist) · **Tactic:** Drag it away from Yog and off water. Hit it with large single hits spaced out so each new bleed is bigger than the remaining one.

**Stats** (from the [Codex](../codex/mobs.md)): HT 300 · accuracy 36 · evasion 20 · damage 18-36 · armour 0-15 · EXP 25 · max level -2 · properties ACIDIC, BOSS, DEMONIC. <small>`core/…/mobs/YogFist.java:399`, `core/…/mobs/YogFist.java:179`, `core/…/mobs/YogFist.java:184`, `core/…/mobs/YogFist.java:189`</small>

#### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** baseSpeed 1 (no override) Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/mobs/YogFist.java:65`, `core/…/actors/Char.java:771`</small>
- **Attack.** `bolt`, reach 6. The zap needs a Ballistica MAGIC_BOLT (stops at the first character or wall) whose collision is the target. A body or a closed door in between blocks it. Otherwise it melees adjacent. Reach is limited by FOV (viewDistance 6). Attack time: zap: 1 turn. Melee: 1 turn. Ranged zap whenever rangedCooldown &lt;= 0, even when adjacent (canRangedInMelee). Each zap adds 8-12 turns of cooldown, and melee is used in between.. <small>`core/…/mobs/YogFist.java:105`, `core/…/mobs/YogFist.java:106`, `core/…/mobs/YogFist.java:108`, `core/…/mobs/YogFist.java:131`, `core/…/mobs/YogFist.java:137`, `core/…/mobs/YogFist.java:87`, `core/…/mobs/YogFist.java:71`, `core/…/mobs/YogFist.java:443`, `core/…/mobs/YogFist.java:445`</small>
- **Near-Yog invulnerability.** Invulnerable while within 4 tiles of Yog's cell (exit + 3 rows). It spawns right below the exit, inside that zone. It logs a warning the first time. *Telegraph:* log 'invuln_warn' message <small>`core/…/mobs/YogFist.java:114`, `core/…/mobs/YogFist.java:115`, `core/…/mobs/YogFist.java:116`, `core/…/mobs/YogFist.java:121`, `core/…/mobs/YogFist.java:123`, `core/…/mobs/YogFist.java:125`, `core/…/mobs/YogDzewa.java:452`</small>
- **Relentless hunting.** Whenever it is wandering and the hero is not invisible, it beckons to the hero and hunts them. It starts HUNTING. *Telegraph:* none <small>`core/…/mobs/YogFist.java:77`, `core/…/mobs/YogFist.java:94`, `core/…/mobs/YogFist.java:95`, `core/…/mobs/YogFist.java:96`</small>
- **Zap.** Every 8-12 turns: seeds 100 volume of Toxic Gas on the hero's cell (no hit roll). *Telegraph:* a MagicMissile bolt from the fist (FistSprite zap animation) when visible <small>`core/…/mobs/YogFist.java:443`, `core/…/mobs/YogFist.java:445`, `core/…/sprites/FistSprite.java:122`, `core/…/sprites/FistSprite.java:126`</small>
- **Rot (damage to bleed).** Damage from anything except Bleeding (and the Sickle harvest) is converted into Bleeding at 0.6 x dmg after resistances. Bleeding.set only replaces the current bleed if the new rolled level is higher, so smaller hits during a bleed are wasted. *Telegraph:* a 'Bleeding N' status on the fist <small>`core/…/mobs/YogFist.java:422`, `core/…/mobs/YogFist.java:423`, `core/…/mobs/YogFist.java:424`, `core/…/mobs/YogFist.java:425`, `core/…/mobs/YogFist.java:435`, `core/…/mobs/YogFist.java:436`, `core/…/buffs/Bleeding.java:80`, `core/…/buffs/Bleeding.java:81`</small>
- **Water regeneration.** Only while standing ON a water tile (not next to one), it heals HT/50 = 6 per act. *Telegraph:* green heal number <small>`core/…/mobs/YogFist.java:412`, `core/…/mobs/YogFist.java:413`, `core/…/mobs/YogFist.java:414`</small>
- **Gas registration (no gas).** Each act it seeds a ToxicGas blob with volume 0 on itself. This only makes sure gas it later creates acts at the right time; it leaves no trail. *Telegraph:* none <small>`core/…/mobs/YogFist.java:410`</small>
- **Inflicts Ooze.** 50% chance on melee hit, 20 turns, 1+depth/5 per turn. Water washes it off. <small>`core/…/mobs/YogFist.java:452`, `core/…/mobs/YogFist.java:453`, `core/…/buffs/Ooze.java:32`, `core/…/buffs/Ooze.java:93`</small>
- **Inflicts Toxic Gas.** Zap seeds 100 gas at the hero's cell. <small>`core/…/mobs/YogFist.java:445`</small>
- **Immune.** Sleep (all fists). BOSS: AllyBuff, Dread. ACIDIC: Ooze. Also ToxicGas. <small>`core/…/mobs/YogFist.java:194`, `core/…/actors/Char.java:1415`</small>
- **Immune.** ToxicGas (own) and Ooze (ACIDIC) <small>`core/…/mobs/YogFist.java:461`, `core/…/actors/Char.java:1428`</small>
- **Resists.** BOSS: Grim, GrimTrap, Retribution, Psionic Blast (x0.5) <small>`core/…/actors/Char.java:1414`</small>
- **Resists.** Corrosion (ACIDIC, x0.5) <small>`core/…/actors/Char.java:1427`</small>
- **AI.** Starts `hunting`; flees: never. Starts HUNTING and re-acquires the hero whenever wandering unless the hero is invisible. Its death calls Yog.processFistDeath; the last fist in phase 4 triggers Yog's final phase. <small>`core/…/mobs/YogFist.java:77`, `core/…/mobs/YogFist.java:94`, `core/…/mobs/YogFist.java:163`, `core/…/mobs/YogFist.java:166`, `core/…/mobs/YogDzewa.java:362`</small>
- **Evasion.** defenseSkill 20; evasive: no. defenseSkill 20, HT 300, DR 0-15, attack 36, melee 18-36 (Rusted 22-44). <small>`core/…/mobs/YogFist.java:68`, `core/…/mobs/YogFist.java:69`, `core/…/mobs/YogFist.java:180`, `core/…/mobs/YogFist.java:185`, `core/…/mobs/YogFist.java:190`</small>
- **Surprise.** Can be surprised: yes. No override, but it always knows the hero (auto-beckon), so surprise only happens when the hero is invisible or outside its FOV. <small>`core/…/mobs/Mob.java:796`, `core/…/mobs/Mob.java:873`, `core/…/mobs/Mob.java:875`, `core/…/mobs/Mob.java:1237`, `core/…/mobs/YogFist.java:94`</small>

#### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `close`; ranged attacks first: no.
- **Plan.** Drag it away from Yog and off water. Hit it with large single hits spaced out so each new bleed is bigger than the remaining one. Step out of the toxic cloud it drops on you every 8-12 turns. Its melee can ooze you, so wash off in water it isn't standing in. <small>`core/…/mobs/YogFist.java:116`, `core/…/mobs/YogFist.java:131`</small>
- **Kill or nullify: Drag it 5+ tiles from Yog before attacking.** It is invulnerable within 4 tiles of Yog. (derived) <small>`core/…/mobs/YogFist.java:116`, `core/…/mobs/YogFist.java:125`</small>
- **Kill or nullify: Put a minion or other body between you and it to block the zap.** The zap requires a MAGIC_BOLT line whose collision is the target, and MAGIC_BOLT stops at characters. (derived) <small>`core/…/mobs/YogFist.java:106`, `core/…/mechanics/Ballistica.java:49`</small>
- **Kill or nullify: Space out big hits rather than many small ones.** Each hit becomes bleed 0.6 x dmg, but Bleeding.set keeps only the larger rolled level, so a flurry of small hits during a strong bleed adds nothing. (derived) <small>`core/…/mobs/YogFist.java:435`, `core/…/buffs/Bleeding.java:81`</small>
- **Kill or nullify: Keep it out of water.** It heals 6 per act on water. (derived) <small>`core/…/mobs/YogFist.java:412`</small>
- **Kill or nullify: Step out of the gas immediately. Fight in open space so gas spreads thin..** The zap drops 100 gas on your tile, and it is immune to that gas. (derived) <small>`core/…/mobs/YogFist.java:445`, `core/…/mobs/YogFist.java:461`</small>
- **Kill or nullify: Stand in water yourself to shed Ooze (but don't let it follow you in).** Water removes Ooze from non-flyers, and the fist heals on water. (derived) <small>`core/…/buffs/Ooze.java:93`, `core/…/mobs/YogFist.java:412`</small>
- **Escape.** Outrunnable: no; contact breaks at: invisibility. Same speed as the hero, auto-re-targets the hero unless the hero is invisible, and the level is sealed during the fight. <small>`core/…/mobs/YogFist.java:94`, `core/…/mobs/YogDzewa.java:546`</small>

| Class | Note |
|---|---|
| Warrior | Heavy weapon hits suit the bleed conversion. |
| Mage | Wand of Prismatic Light deals x1.333 to this DEMONIC target (core/…/items/wands/WandOfPrismaticLight.java:96-100) and its hits are direct damage with no evasion roll. Big single zaps beat rapid small ones. |
| Rogue | Surprise hits are big single hits, which is good here. |
| Huntress | Snipe from outside the gas. Several small arrows overlap bleeds, so this is less efficient. |
| Duelist | Prefer heavy hits or abilities over fast light weapons. |
| Cleric | Smite and Holy Lance roll their maximum and Sunray deals flat 8/12 against DEMONIC targets (core/…/actors/hero/spells/Smite.java:123, core/…/actors/hero/spells/HolyLance.java:120, core/…/actors/hero/spells/Sunray.java:100). |

#### Community notes

- **Tier F.** “<del>Isolate and fight the two fists of Yog-Dzewa separately rather than together. Fight the Rotting Fist while standing on water: it inflicts Caustic Ooze, and fighting on water lets tools like Seed of Earthroot / Greaves of Nature tank the damage more easily. Note the Rotting Fist itself also heals 4 HP per turn while it is on/near water, so this is a damage-mitigation choice for the Hero, not a way to deny the fist's own healing.</del>” [source](https://pixeldungeon.fandom.com/wiki/Rotting_fist) (version: not stated). Partly right, contradicted on the numbers and setup. Right: its melee oozes (50%) and water washes Ooze off. Wrong: it heals HT/50 = 6 HP per act, not 4, and only while standing ON water, not near it. And normally only one fist exists at a time: a fist spawns at each 300-HP phase and Yog is invulnerable while any fist lives, so there is no pair to separate. Only Stronger Bosses spawns two at once, and then the Rotting fist's partner is the Rusted fist, not the Burning fist. 'Greaves of Nature' is not an item in this codebase. <small>`core/…/mobs/YogFist.java:412`, `core/…/mobs/YogFist.java:413`, `core/…/mobs/YogFist.java:414`, `core/…/mobs/YogFist.java:452`, `core/…/mobs/YogFist.java:453`, `core/…/buffs/Ooze.java:93`, `core/…/mobs/YogDzewa.java:388`, `core/…/mobs/YogDzewa.java:411`, `core/…/mobs/YogDzewa.java:419`, `core/…/mobs/YogDzewa.java:421`, `core/…/mobs/YogDzewa.java:422`</small>
- **Tier F.** “<del>A Potion of Paralytic Gas only affects the Burning Fist in this encounter; the Rotting Fist is immune to paralysis, so don't waste a paralytic gas potion trying to lock it down.</del>” [source](https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Bosses) (version: not stated). No Paralysis immunity. Its immunities are Sleep (all fists), ToxicGas (its own), Ooze (ACIDIC) and AllyBuff/Dread (BOSS). Paralytic gas works on it. This may be confused with its immunity to Toxic Gas. <small>`core/…/mobs/YogFist.java:194`, `core/…/mobs/YogFist.java:404`, `core/…/mobs/YogFist.java:461`, `core/…/actors/Char.java:1415`, `core/…/actors/Char.java:1428`</small>

??? note "Corrected during verification (1)"

    - Renamed the 'Toxic trail' ability: the volume-0 seed (YogFist.java:410) creates no gas.

### Rusted fist {#yogfist-rustedfist}

`actors.mobs.YogFist.RustedFist` · depths boss 25 (Yog fist) · **Tactic:** Pull it away from Yog and pile on damage. It drains at 10% of stored damage per act, so keep adding and don't expect instant results.

**Stats** (from the [Codex](../codex/mobs.md)): HT 300 · accuracy 36 · evasion 20 · damage 22-44 · armour 0-15 · EXP 25 · max level -2 · properties BOSS, DEMONIC, INORGANIC, LARGE. <small>`core/…/mobs/YogFist.java:466`, `core/…/mobs/YogFist.java:179`, `core/…/mobs/YogFist.java:476`, `core/…/mobs/YogFist.java:189`</small>

#### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** baseSpeed 1. LARGE: cannot enter cells that aren't openSpace (doors, 1-wide corridors). Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: no; follows you on the stairs: no. <small>`core/…/mobs/YogFist.java:65`, `core/…/actors/Char.java:771`, `core/…/mobs/YogFist.java:471`, `core/…/mobs/Mob.java:580`, `core/…/levels/Level.java:892`</small>
- **Attack.** `bolt`, reach 6. The zap needs a Ballistica MAGIC_BOLT (stops at the first character or wall) whose collision is the target. A body or a closed door in between blocks it. Otherwise it melees adjacent. Reach is limited by FOV (viewDistance 6). Attack time: zap: 1 turn. Melee: 1 turn. Ranged zap whenever rangedCooldown &lt;= 0, even when adjacent (canRangedInMelee). Each zap adds 8-12 turns of cooldown, and melee is used in between.. <small>`core/…/mobs/YogFist.java:105`, `core/…/mobs/YogFist.java:106`, `core/…/mobs/YogFist.java:108`, `core/…/mobs/YogFist.java:131`, `core/…/mobs/YogFist.java:137`, `core/…/mobs/YogFist.java:87`, `core/…/mobs/YogFist.java:71`, `core/…/mobs/YogFist.java:494`, `core/…/mobs/YogFist.java:496`</small>
- **Near-Yog invulnerability.** Invulnerable while within 4 tiles of Yog's cell (exit + 3 rows). It spawns right below the exit, inside that zone. It logs a warning the first time. *Telegraph:* log 'invuln_warn' message <small>`core/…/mobs/YogFist.java:114`, `core/…/mobs/YogFist.java:115`, `core/…/mobs/YogFist.java:116`, `core/…/mobs/YogFist.java:121`, `core/…/mobs/YogFist.java:123`, `core/…/mobs/YogFist.java:125`, `core/…/mobs/YogDzewa.java:452`</small>
- **Relentless hunting.** Whenever it is wandering and the hero is not invisible, it beckons to the hero and hunts them. It starts HUNTING. *Telegraph:* none <small>`core/…/mobs/YogFist.java:77`, `core/…/mobs/YogFist.java:94`, `core/…/mobs/YogFist.java:95`, `core/…/mobs/YogFist.java:96`</small>
- **Zap.** Every 8-12 turns: Cripple for 4 turns on the hero (no hit roll). *Telegraph:* a MagicMissile bolt from the fist (FistSprite zap animation) when visible <small>`core/…/mobs/YogFist.java:494`, `core/…/mobs/YogFist.java:496`, `core/…/sprites/FistSprite.java:122`, `core/…/sprites/FistSprite.java:126`</small>
- **Deferred damage.** All damage (after resistances) is stored as Viscosity.DeferedDamage instead of being applied. Each act, 10% of the stored amount (min 1) is dealt. *Telegraph:* a 'deferred N' status on the fist and its buff <small>`core/…/mobs/YogFist.java:482`, `core/…/mobs/YogFist.java:483`, `core/…/mobs/YogFist.java:485`, `core/…/mobs/YogFist.java:486`, `core/…/glyphs/Viscosity.java:149`, `core/…/glyphs/Viscosity.java:150`</small>
- **Inflicts Cripple.** 4 turns. Halves hero speed. <small>`core/…/mobs/YogFist.java:496`, `core/…/actors/Char.java:772`</small>
- **Immune.** Sleep (all fists). BOSS: AllyBuff, Dread. INORGANIC: Bleeding, ToxicGas, Poison. <small>`core/…/mobs/YogFist.java:194`, `core/…/actors/Char.java:1415`</small>
- **Immune.** Bleeding, ToxicGas, Poison (INORGANIC) <small>`core/…/mobs/YogFist.java:472`, `core/…/actors/Char.java:1422`</small>
- **Resists.** BOSS: Grim, GrimTrap, Retribution, Psionic Blast (x0.5) <small>`core/…/actors/Char.java:1414`</small>
- **AI.** Starts `hunting`; flees: never. Starts HUNTING and re-acquires the hero whenever wandering unless the hero is invisible. Its death calls Yog.processFistDeath; the last fist in phase 4 triggers Yog's final phase. <small>`core/…/mobs/YogFist.java:77`, `core/…/mobs/YogFist.java:94`, `core/…/mobs/YogFist.java:163`, `core/…/mobs/YogFist.java:166`, `core/…/mobs/YogDzewa.java:362`</small>
- **Evasion.** defenseSkill 20; evasive: no. defenseSkill 20, HT 300, DR 0-15, attack 36, melee 18-36 (Rusted 22-44). <small>`core/…/mobs/YogFist.java:68`, `core/…/mobs/YogFist.java:69`, `core/…/mobs/YogFist.java:180`, `core/…/mobs/YogFist.java:185`, `core/…/mobs/YogFist.java:190`</small>
- **Surprise.** Can be surprised: yes. No override, but it always knows the hero (auto-beckon), so surprise only happens when the hero is invisible or outside its FOV. <small>`core/…/mobs/Mob.java:796`, `core/…/mobs/Mob.java:873`, `core/…/mobs/Mob.java:875`, `core/…/mobs/Mob.java:1237`, `core/…/mobs/YogFist.java:94`</small>

#### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `close`; ranged attacks first: no.
- **Plan.** Pull it away from Yog and pile on damage. It drains at 10% of stored damage per act, so keep adding and don't expect instant results. Its melee is the strongest of the fists (22-44), and its zap cripples you (no dodge), so fight from a spot a LARGE body can't reach (corridor, doorway) if the arena has one, and hit it from there. <small>`core/…/mobs/YogFist.java:116`, `core/…/mobs/YogFist.java:131`</small>
- **Kill or nullify: Drag it 5+ tiles from Yog before attacking.** It is invulnerable within 4 tiles of Yog. (derived) <small>`core/…/mobs/YogFist.java:116`, `core/…/mobs/YogFist.java:125`</small>
- **Kill or nullify: Put a minion or other body between you and it to block the zap.** The zap requires a MAGIC_BOLT line whose collision is the target, and MAGIC_BOLT stops at characters. (derived) <small>`core/…/mobs/YogFist.java:106`, `core/…/mechanics/Ballistica.java:49`</small>
- **Kill or nullify: Front-load damage, then survive while the deferral ticks.** Damage isn't lost, only delayed at 10% per act, so a big early pile keeps draining it. (derived) <small>`core/…/mobs/YogFist.java:485`, `core/…/glyphs/Viscosity.java:149`</small>
- **Kill or nullify: Use narrow corridors or doorways.** As a LARGE mob it cannot enter non-openSpace cells, so it can't follow through 1-wide gaps. (derived) <small>`core/…/mobs/YogFist.java:471`, `core/…/mobs/Mob.java:580`</small>
- **Kill or nullify: Don't rely on bleed or gas.** INORGANIC immunities. (derived) <small>`core/…/actors/Char.java:1422`</small>
- **Escape.** Outrunnable: no; contact breaks at: invisibility. Same speed as the hero, auto-re-targets the hero unless the hero is invisible, and the level is sealed during the fight. <small>`core/…/mobs/YogFist.java:94`, `core/…/mobs/YogDzewa.java:546`</small>

| Class | Note |
|---|---|
| Warrior | Armor matters against 22-44 melee. |
| Mage | Wand of Prismatic Light deals x1.333 to this DEMONIC target (core/…/items/wands/WandOfPrismaticLight.java:96-100) and its hits are direct damage with no evasion roll. No bleed/gas/poison wands. |
| Rogue | Keep damage flowing. Sneak bonuses still add to the stored pool. |
| Huntress | Shoot from a narrow spot it can't enter. |
| Duelist | Reach weapons from a corridor mouth. |
| Cleric | Smite and Holy Lance roll their maximum and Sunray deals flat 8/12 against DEMONIC targets (core/…/actors/hero/spells/Smite.java:123, core/…/actors/hero/spells/HolyLance.java:120, core/…/actors/hero/spells/Sunray.java:100). |

**Open questions.**

- Whether the Yog arena has non-openSpace cells to exploit LARGE was not verified (HallsBossLevel not read).

#### Community notes

No community claim about this enemy was found.

### Bright fist {#yogfist-brightfist}

`actors.mobs.YogFist.BrightFist` · depths boss 25 (Yog fist) · **Tactic:** Drag it from Yog and fight it adjacent so it can only melee. Expect a 15-turn blind at half HP when it teleports away: stop and stay off Yog's marked lines until it walks back to you.

**Stats** (from the [Codex](../codex/mobs.md)): HT 300 · accuracy 36 · evasion 20 · damage 18-36 · armour 0-15 · EXP 25 · max level -2 · properties BOSS, DEMONIC, ELECTRIC. <small>`core/…/mobs/YogFist.java:501`, `core/…/mobs/YogFist.java:179`, `core/…/mobs/YogFist.java:184`, `core/…/mobs/YogFist.java:189`</small>

#### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** baseSpeed 1 (no override) Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/mobs/YogFist.java:65`, `core/…/actors/Char.java:771`</small>
- **Attack.** `bolt`, reach 6. The zap needs a Ballistica MAGIC_BOLT (stops at the first character or wall) whose collision is the target. A body or a closed door in between blocks it. Otherwise it melees adjacent. Reach is limited by FOV (viewDistance 6). Attack time: zap: 1 turn. Melee: 1 turn. No ranged cooldown: it zaps whenever NOT adjacent and has a MAGIC_BOLT line. When adjacent it always melees (canRangedInMelee false).. <small>`core/…/mobs/YogFist.java:105`, `core/…/mobs/YogFist.java:106`, `core/…/mobs/YogFist.java:108`, `core/…/mobs/YogFist.java:131`, `core/…/mobs/YogFist.java:137`, `core/…/mobs/YogFist.java:87`, `core/…/mobs/YogFist.java:71`, `core/…/mobs/YogFist.java:508`, `core/…/mobs/YogFist.java:512`, `core/…/mobs/YogFist.java:520`, `core/…/mobs/YogFist.java:525`, `core/…/mobs/YogFist.java:527`, `core/…/mobs/YogFist.java:528`</small>
- **Near-Yog invulnerability.** Invulnerable while within 4 tiles of Yog's cell (exit + 3 rows). It spawns right below the exit, inside that zone. It logs a warning the first time. *Telegraph:* log 'invuln_warn' message <small>`core/…/mobs/YogFist.java:114`, `core/…/mobs/YogFist.java:115`, `core/…/mobs/YogFist.java:116`, `core/…/mobs/YogFist.java:121`, `core/…/mobs/YogFist.java:123`, `core/…/mobs/YogFist.java:125`, `core/…/mobs/YogDzewa.java:452`</small>
- **Relentless hunting.** Whenever it is wandering and the hero is not invisible, it beckons to the hero and hunts them. It starts HUNTING. *Telegraph:* none <small>`core/…/mobs/YogFist.java:77`, `core/…/mobs/YogFist.java:94`, `core/…/mobs/YogFist.java:95`, `core/…/mobs/YogFist.java:96`</small>
- **Zap.** When not adjacent (no cooldown): a magic hit (x2 accuracy) for 10-20 LightBeam damage (no armor) plus Blindness for 5 turns. *Telegraph:* a MagicMissile bolt from the fist (FistSprite zap animation) when visible <small>`core/…/mobs/YogFist.java:508`, `core/…/mobs/YogFist.java:512`, `core/…/mobs/YogFist.java:520`, `core/…/mobs/YogFist.java:525`, `core/…/mobs/YogFist.java:527`, `core/…/mobs/YogFist.java:528`, `core/…/sprites/FistSprite.java:122`, `core/…/sprites/FistSprite.java:126`</small>
- **Flash and flee at half HP.** The first time it drops to HT/2 (150), HP is clamped to 150, the hero is blinded for 15 turns, and it teleports to a random cell outside the hero's FOV that can path to the exit, then wanders (and re-hunts next act). On death it blinds the hero for 30 turns. The teleport destination can land back inside the 4-tile invulnerability zone around Yog, since only hero FOV, solidity, occupancy and a path to the exit are checked. *Telegraph:* white screen flash and a log 'teleport' warning <small>`core/…/mobs/YogFist.java:547`, `core/…/mobs/YogFist.java:548`, `core/…/mobs/YogFist.java:549`, `core/…/mobs/YogFist.java:557`, `core/…/mobs/YogFist.java:558`, `core/…/mobs/YogFist.java:559`, `core/…/mobs/YogFist.java:560`, `core/…/mobs/YogFist.java:562`, `core/…/mobs/YogFist.java:551`, `core/…/mobs/YogFist.java:552`, `core/…/mobs/YogFist.java:553`, `core/…/mobs/YogFist.java:554`, `core/…/mobs/YogFist.java:555`, `core/…/mobs/YogFist.java:556`, `core/…/mobs/YogFist.java:114`, `core/…/mobs/YogFist.java:115`, `core/…/mobs/YogFist.java:116`</small>
- **Inflicts Blindness.** 5 turns per zap, 15 at the half-HP flash, 30 on death. <small>`core/…/mobs/YogFist.java:528`, `core/…/mobs/YogFist.java:549`, `core/…/mobs/YogFist.java:562`, `core/…/buffs/Blindness.java:29`</small>
- **Immune.** Sleep (all fists). BOSS: AllyBuff, Dread. <small>`core/…/mobs/YogFist.java:194`, `core/…/actors/Char.java:1415`</small>
- **Resists.** BOSS: Grim, GrimTrap, Retribution, Psionic Blast (x0.5) <small>`core/…/actors/Char.java:1414`</small>
- **Resists.** ELECTRIC: Wand of Lightning, Shocking, Potential, Electricity, Shocking Dart, Shock Elemental (x0.5) <small>`core/…/mobs/YogFist.java:506`, `core/…/actors/Char.java:1429`, `core/…/actors/Char.java:1430`</small>
- **AI.** Starts `hunting`; flees: never. Starts HUNTING and re-acquires the hero whenever wandering unless the hero is invisible. Its death calls Yog.processFistDeath; the last fist in phase 4 triggers Yog's final phase. <small>`core/…/mobs/YogFist.java:77`, `core/…/mobs/YogFist.java:94`, `core/…/mobs/YogFist.java:163`, `core/…/mobs/YogFist.java:166`, `core/…/mobs/YogDzewa.java:362`</small>
- **Evasion.** defenseSkill 20; evasive: no. defenseSkill 20, HT 300, DR 0-15, attack 36, melee 18-36 (Rusted 22-44). <small>`core/…/mobs/YogFist.java:68`, `core/…/mobs/YogFist.java:69`, `core/…/mobs/YogFist.java:180`, `core/…/mobs/YogFist.java:185`, `core/…/mobs/YogFist.java:190`</small>
- **Surprise.** Can be surprised: yes. No override, but it always knows the hero (auto-beckon), so surprise only happens when the hero is invisible or outside its FOV. <small>`core/…/mobs/Mob.java:796`, `core/…/mobs/Mob.java:873`, `core/…/mobs/Mob.java:875`, `core/…/mobs/Mob.java:1237`, `core/…/mobs/YogFist.java:94`</small>

#### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `close`; ranged attacks first: no.
- **Plan.** Drag it from Yog and fight it adjacent so it can only melee. Expect a 15-turn blind at half HP when it teleports away: stop and stay off Yog's marked lines until it walks back to you. Before the killing blow, position safely, because death blinds you for 30 turns. <small>`core/…/mobs/YogFist.java:116`, `core/…/mobs/YogFist.java:131`</small>
- **Kill or nullify: Drag it 5+ tiles from Yog before attacking.** It is invulnerable within 4 tiles of Yog. (derived) <small>`core/…/mobs/YogFist.java:116`, `core/…/mobs/YogFist.java:125`</small>
- **Kill or nullify: Put a minion or other body between you and it to block the zap.** The zap requires a MAGIC_BOLT line whose collision is the target, and MAGIC_BOLT stops at characters. (derived) <small>`core/…/mobs/YogFist.java:106`, `core/…/mechanics/Ballistica.java:49`</small>
- **Kill or nullify: Stay adjacent.** canRangedInMelee is false, so adjacent it only melees and never blinds you with a zap. (derived) <small>`core/…/mobs/YogFist.java:508`, `core/…/mobs/YogFist.java:131`</small>
- **Kill or nullify: Plan for blindness at half HP and on the kill.** 15 turns of blindness at 150 HP and 30 on death, both while Yog's beams and summons are active. Pre-position off beam lines before the last hit. (derived) <small>`core/…/mobs/YogFist.java:549`, `core/…/mobs/YogFist.java:562`</small>
- **Kill or nullify: AntiMagic armor.** LightBeam is in AntiMagic.RESISTS. Ring of Elements also resists it. (derived) <small>`core/…/glyphs/AntiMagic.java:134`, `core/…/rings/RingOfElements.java:85`, `core/…/rings/RingOfElements.java:93`</small>
- **Escape.** Outrunnable: no; contact breaks at: invisibility. Same speed as the hero, auto-re-targets the hero unless the hero is invisible, and the level is sealed during the fight. <small>`core/…/mobs/YogFist.java:94`, `core/…/mobs/YogDzewa.java:546`</small>

| Class | Note |
|---|---|
| Warrior | Adjacent melee is the plan. |
| Mage | Wand of Prismatic Light deals x1.333 to this DEMONIC target (core/…/items/wands/WandOfPrismaticLight.java:96-100) and its hits are direct damage with no evasion roll. Lightning is resisted. |
| Rogue | Stay adjacent. |
| Huntress | Ranged invites 5-turn blinds. Close in or kill quickly from range. |
| Duelist | Stay adjacent. Round Shield guard blocks its magic hit (infinite evasion). |
| Cleric | Smite and Holy Lance roll their maximum and Sunray deals flat 8/12 against DEMONIC targets (core/…/actors/hero/spells/Smite.java:123, core/…/actors/hero/spells/HolyLance.java:120, core/…/actors/hero/spells/Sunray.java:100). |

#### Community notes

No community claim about this enemy was found.

??? note "Corrected during verification (1)"

    - tactics.approach said 'keep-away' while the plan is to stay adjacent (canRangedInMelee=false). Set to 'close'.

### Dark fist {#yogfist-darkfist}

`actors.mobs.YogFist.DarkFist` · depths boss 25 (Yog fist) · **Tactic:** Drag it from Yog and fight it adjacent so it can only melee. At half HP it removes your Light and teleports away: relight a Torch and hold position off beam lines until it returns.

**Stats** (from the [Codex](../codex/mobs.md)): HT 300 · accuracy 36 · evasion 20 · damage 18-36 · armour 0-15 · EXP 25 · max level -2 · properties BOSS, DEMONIC. <small>`core/…/mobs/YogFist.java:569`, `core/…/mobs/YogFist.java:179`, `core/…/mobs/YogFist.java:184`, `core/…/mobs/YogFist.java:189`</small>

#### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** baseSpeed 1 (no override) Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/mobs/YogFist.java:65`, `core/…/actors/Char.java:771`</small>
- **Attack.** `bolt`, reach 6. The zap needs a Ballistica MAGIC_BOLT (stops at the first character or wall) whose collision is the target. A body or a closed door in between blocks it. Otherwise it melees adjacent. Reach is limited by FOV (viewDistance 6). Attack time: zap: 1 turn. Melee: 1 turn. No ranged cooldown: it zaps whenever NOT adjacent and has a MAGIC_BOLT line. When adjacent it always melees (canRangedInMelee false).. <small>`core/…/mobs/YogFist.java:105`, `core/…/mobs/YogFist.java:106`, `core/…/mobs/YogFist.java:108`, `core/…/mobs/YogFist.java:131`, `core/…/mobs/YogFist.java:137`, `core/…/mobs/YogFist.java:87`, `core/…/mobs/YogFist.java:71`, `core/…/mobs/YogFist.java:574`, `core/…/mobs/YogFist.java:578`, `core/…/mobs/YogFist.java:586`, `core/…/mobs/YogFist.java:591`, `core/…/mobs/YogFist.java:593`, `core/…/mobs/YogFist.java:595`, `core/…/mobs/YogFist.java:597`</small>
- **Near-Yog invulnerability.** Invulnerable while within 4 tiles of Yog's cell (exit + 3 rows). It spawns right below the exit, inside that zone. It logs a warning the first time. *Telegraph:* log 'invuln_warn' message <small>`core/…/mobs/YogFist.java:114`, `core/…/mobs/YogFist.java:115`, `core/…/mobs/YogFist.java:116`, `core/…/mobs/YogFist.java:121`, `core/…/mobs/YogFist.java:123`, `core/…/mobs/YogFist.java:125`, `core/…/mobs/YogDzewa.java:452`</small>
- **Relentless hunting.** Whenever it is wandering and the hero is not invisible, it beckons to the hero and hunts them. It starts HUNTING. *Telegraph:* none <small>`core/…/mobs/YogFist.java:77`, `core/…/mobs/YogFist.java:94`, `core/…/mobs/YogFist.java:95`, `core/…/mobs/YogFist.java:96`</small>
- **Zap.** When not adjacent (no cooldown): a magic hit (x2 accuracy) for 10-20 DarkBolt damage (no armor). On a hit it also shortens the hero's Light buff by 50 turns (Light.weaken spends -50). *Telegraph:* a MagicMissile bolt from the fist (FistSprite zap animation) when visible <small>`core/…/mobs/YogFist.java:574`, `core/…/mobs/YogFist.java:578`, `core/…/mobs/YogFist.java:586`, `core/…/mobs/YogFist.java:591`, `core/…/mobs/YogFist.java:593`, `core/…/mobs/YogFist.java:595`, `core/…/mobs/YogFist.java:597`, `core/…/sprites/FistSprite.java:122`, `core/…/sprites/FistSprite.java:126`, `core/…/buffs/Light.java:58`, `core/…/buffs/Light.java:59`</small>
- **Darkness and flee at half HP.** The first time it drops to HT/2 (150), HP is clamped to 150, the hero's Light buff is removed, and it teleports to a random cell outside the hero's FOV that can path to the exit, then wanders (and re-hunts). On death it removes the hero's Light again. The teleport destination can land back inside the 4-tile invulnerability zone around Yog, since only hero FOV, solidity, occupancy and a path to the exit are checked. *Telegraph:* black screen flash and a log 'teleport' warning <small>`core/…/mobs/YogFist.java:617`, `core/…/mobs/YogFist.java:618`, `core/…/mobs/YogFist.java:619`, `core/…/mobs/YogFist.java:621`, `core/…/mobs/YogFist.java:630`, `core/…/mobs/YogFist.java:631`, `core/…/mobs/YogFist.java:632`, `core/…/mobs/YogFist.java:633`, `core/…/mobs/YogFist.java:635`, `core/…/mobs/YogFist.java:637`, `core/…/mobs/YogFist.java:624`, `core/…/mobs/YogFist.java:625`, `core/…/mobs/YogFist.java:626`, `core/…/mobs/YogFist.java:627`, `core/…/mobs/YogFist.java:628`, `core/…/mobs/YogFist.java:629`, `core/…/mobs/YogFist.java:114`, `core/…/mobs/YogFist.java:115`, `core/…/mobs/YogFist.java:116`</small>
- **Inflicts Light (removed/weakened).** -50 Light duration per zap. Removed entirely at half HP and on death, which drops the hero to arena vision (Yog phase-dependent, down to 1). <small>`core/…/mobs/YogFist.java:597`, `core/…/buffs/Light.java:58`, `core/…/mobs/YogFist.java:621`, `core/…/mobs/YogFist.java:637`, `core/…/buffs/Light.java:53`</small>
- **Immune.** Sleep (all fists). BOSS: AllyBuff, Dread. <small>`core/…/mobs/YogFist.java:194`, `core/…/actors/Char.java:1415`</small>
- **Resists.** BOSS: Grim, GrimTrap, Retribution, Psionic Blast (x0.5) <small>`core/…/actors/Char.java:1414`</small>
- **AI.** Starts `hunting`; flees: never. Starts HUNTING and re-acquires the hero whenever wandering unless the hero is invisible. Its death calls Yog.processFistDeath; the last fist in phase 4 triggers Yog's final phase. <small>`core/…/mobs/YogFist.java:77`, `core/…/mobs/YogFist.java:94`, `core/…/mobs/YogFist.java:163`, `core/…/mobs/YogFist.java:166`, `core/…/mobs/YogDzewa.java:362`</small>
- **Evasion.** defenseSkill 20; evasive: no. defenseSkill 20, HT 300, DR 0-15, attack 36, melee 18-36 (Rusted 22-44). <small>`core/…/mobs/YogFist.java:68`, `core/…/mobs/YogFist.java:69`, `core/…/mobs/YogFist.java:180`, `core/…/mobs/YogFist.java:185`, `core/…/mobs/YogFist.java:190`</small>
- **Surprise.** Can be surprised: yes. No override, but it always knows the hero (auto-beckon), so surprise only happens when the hero is invisible or outside its FOV. <small>`core/…/mobs/Mob.java:796`, `core/…/mobs/Mob.java:873`, `core/…/mobs/Mob.java:875`, `core/…/mobs/Mob.java:1237`, `core/…/mobs/YogFist.java:94`</small>

#### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `close`; ranged attacks first: no.
- **Plan.** Drag it from Yog and fight it adjacent so it can only melee. At half HP it removes your Light and teleports away: relight a Torch and hold position off beam lines until it returns. Relight after it dies as well. <small>`core/…/mobs/YogFist.java:116`, `core/…/mobs/YogFist.java:131`</small>
- **Kill or nullify: Drag it 5+ tiles from Yog before attacking.** It is invulnerable within 4 tiles of Yog. (derived) <small>`core/…/mobs/YogFist.java:116`, `core/…/mobs/YogFist.java:125`</small>
- **Kill or nullify: Put a minion or other body between you and it to block the zap.** The zap requires a MAGIC_BOLT line whose collision is the target, and MAGIC_BOLT stops at characters. (derived) <small>`core/…/mobs/YogFist.java:106`, `core/…/mechanics/Ballistica.java:49`</small>
- **Kill or nullify: Stay adjacent.** canRangedInMelee is false, so adjacent it only melees and never drains Light. (derived) <small>`core/…/mobs/YogFist.java:574`, `core/…/mobs/YogFist.java:131`</small>
- **Kill or nullify: Carry a spare Torch.** It strips Light at half HP and on death, and Yog's darkness shrinks vision to 1-2 by then. (derived) <small>`core/…/mobs/YogFist.java:621`, `core/…/mobs/YogFist.java:637`, `core/…/mobs/YogDzewa.java:477`, `core/…/items/Torch.java:72`</small>
- **Kill or nullify: AntiMagic armor.** DarkBolt is in AntiMagic.RESISTS. Ring of Elements also resists it. (derived) <small>`core/…/glyphs/AntiMagic.java:135`, `core/…/rings/RingOfElements.java:85`, `core/…/rings/RingOfElements.java:93`</small>
- **Escape.** Outrunnable: no; contact breaks at: invisibility. Same speed as the hero, auto-re-targets the hero unless the hero is invisible, and the level is sealed during the fight. <small>`core/…/mobs/YogFist.java:94`, `core/…/mobs/YogDzewa.java:546`</small>

| Class | Note |
|---|---|
| Warrior | Adjacent melee. |
| Mage | Wand of Prismatic Light deals x1.333 to this DEMONIC target (core/…/items/wands/WandOfPrismaticLight.java:96-100) and its hits are direct damage with no evasion roll. |
| Rogue | Stay adjacent. |
| Huntress | Ranged fire invites Light drain. Close in. |
| Duelist | Stay adjacent. |
| Cleric | Smite and Holy Lance roll their maximum and Sunray deals flat 8/12 against DEMONIC targets (core/…/actors/hero/spells/Smite.java:123, core/…/actors/hero/spells/HolyLance.java:120, core/…/actors/hero/spells/Sunray.java:100). |

#### Community notes

No community claim about this enemy was found.

??? note "Corrected during verification (1)"

    - tactics.approach said 'keep-away' while the plan is to stay adjacent (canRangedInMelee=false). Set to 'close'.
