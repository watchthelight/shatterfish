---
topic: "Bestiary lore: Bosses and their parts"
tag: v4.0.0
sources:
  - https://pixeldungeon.fandom.com/wiki/Goo
  - https://pixeldungeon.fandom.com/wiki/Tengu
  - https://pixeldungeon.fandom.com/wiki/DM-300
  - https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Bosses
  - https://pixeldungeon.fandom.com/f/p/1997203027525688865
  - https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies
  - https://pixeldungeon.fandom.com/wiki/Rotting_fist
retrieved: 2026-09-26
tiers:
  tier1: 12
  contradicted: 16
  unverified: 3
claims: 31
---

# Lore: Bosses and their parts

Community claims about these enemies, each graded against the pinned code. Tier 1: the code
confirms it. Tier F: the code contradicts it. Tier 3: not settled by the code. The graded card
is in `docs/bestiary/bosses.md`; a claim that reached this file is never a mechanic by itself, only its
verdict and the citations are.

## Goo (`actors.mobs.Goo`)

### Claim bosses-1

- **Claim:** “When Goo starts its pump-up '!!!' animation, immediately move at least 2 tiles away from it — staying in that radius risks a hit that can one-shot an underleveled hero; the attack can also simply miss if you can't escape in time.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Goo>
- **Variant:** unknown
- **Version:** unspecified (SPD wiki, no version stated)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Confirmed with a precision fix: the pumped strike reaches distance 2 with a clear line, so you must END 3+ tiles away (two steps from adjacent) or behind a wall. Ending exactly 2 tiles away is still in range. It deals 3x damage (3-24, 3-36 enraged) at x2 accuracy and uses a normal hit roll, so it can miss.
- **Cites:** `core/…/mobs/Goo.java:141-152`, `core/…/mobs/Goo.java:67-89`, `core/…/actors/Char.java:619-685`
- **Bestiary card:** `docs/bestiary/bosses.md#goo`

### Claim bosses-2

- **Claim:** “Goo's regular melee hit has roughly a 1-in-3 chance to inflict ~20 turns of Caustic Ooze; step into water to wash the debuff off, so fighting Goo near or in the flooded room helps a lot.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Goo>
- **Variant:** unknown
- **Version:** unspecified (SPD wiki, no version stated)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Confirmed: Random.Int(3)==0 per landed hit (the pumped strike too), Ooze for 20 turns, 1 damage per turn at depth 5, and standing in water washes it off. Caveat: only YOU in water is good; Goo standing in water heals every turn.
- **Cites:** `core/…/mobs/Goo.java:154-160`, `core/…/buffs/Ooze.java:32`, `core/…/buffs/Ooze.java:90-120`, `core/…/mobs/Goo.java:109-132`
- **Bestiary card:** `docs/bestiary/bosses.md#goo`

### Claim bosses-3

- **Claim:** “Wand of Slowness is a strong tool against Goo.”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Goo>
- **Variant:** unknown
- **Version:** unspecified (SPD wiki, no version stated)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** There is no Wand of Slowness in SPD v4.0.0 (it is an original Pixel Dungeon item). (Part of a compound claim.) The wands package holds CursedWand, BlastWave, Corrosion, Corruption, Disintegration, Fireblast, Frost, Lightning, LivingEarth, MagicMissile, PrismaticLight, Regrowth, Transfusion and Warding only.
- **Bestiary card:** `docs/bestiary/bosses.md#goo`

### Claim bosses-4

- **Claim:** “Scroll of Lullaby is a strong tool against Goo.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Goo>
- **Variant:** unknown
- **Version:** unspecified (SPD wiki, no version stated)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Goo is not Sleep-immune; Lullaby's Drowsy turns into magical sleep that cancels a pump and lasts until Goo is damaged. Caveat: the reader also gets Drowsy. (Part of a compound claim.)
- **Cites:** `core/…/scrolls/ScrollOfLullaby.java:47-55`, `core/…/buffs/MagicalSleep.java:37-67`, `core/…/actors/Char.java:1414-1415`
- **Bestiary card:** `docs/bestiary/bosses.md#goo`

### Claim bosses-5

- **Claim:** “Potion of Toxic Gas (paired with Invisibility so you avoid the gas yourself) is a strong tool against Goo.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Goo>
- **Variant:** unknown
- **Version:** unspecified (SPD wiki, no version stated)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Goo has no ToxicGas immunity or resistance (BOSS/DEMONIC/ACIDIC). (Part of a compound claim.)
- **Cites:** `core/…/mobs/Goo.java:59-61`, `core/…/actors/Char.java:1414-1428`
- **Bestiary card:** `docs/bestiary/bosses.md#goo`

### Claim bosses-6

- **Claim:** “Scroll of Mirror Image is a strong tool against Goo.”
- **Tier:** 3 (UNVERIFIED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Goo>
- **Variant:** unknown
- **Version:** unspecified (SPD wiki, no version stated)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Plausible (images are allies that draw and deal hits), but how effective they are is not a code fact. (Part of a compound claim.)
- **Bestiary card:** `docs/bestiary/bosses.md#goo`

### Claim bosses-7

- **Claim:** “As Rogue, using Cloak of Shadows while Goo is pumping up cancels its attack outright.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Goo>
- **Variant:** unknown
- **Version:** unspecified (SPD wiki, no version stated)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** An invisible hero is never in Goo's FOV, so it cannot attack and must move, and getCloser resets pumpedUp to 0. (Part of a compound claim.)
- **Cites:** `core/…/mobs/Mob.java:290`, `core/…/mobs/Goo.java:243-250`
- **Bestiary card:** `docs/bestiary/bosses.md#goo`

## Tengu (`actors.mobs.Tengu`)

### Claim bosses-8

- **Claim:** “Phase 1 requires only bringing Tengu to half HP. He plants fading poison-dart traps when he jumps (8 turns of poison, ~17 total damage, plus 1-4 physical) — track where he lands and avoid those tiles. Ranged/burst classes (Rogue's sneak attack, Huntress's boomerang, Mage's staff, or any damaging wand) make phase 1 much easier.”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Tengu>
- **Variant:** unknown
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Confirmed: phase 1 ends at HP &lt;= HT/2, the traps fade, poison 8 totals 17. Contradicted: the dart's physical part is 4-8 minus DR, not 1-4; traps are re-rolled across 40-90% of the whole cell each jump, not placed where he lands, and a trap-free path to him is always left. The Huntress no longer starts with a boomerang (Spirit Bow).
- **Cites:** `core/…/mobs/Tengu.java:179-184`, `core/…/levels/PrisonBossLevel.java:669-729`, `core/…/traps/TenguDartTrap.java:36-43`, `core/…/traps/PoisonDartTrap.java:114-128`, `core/…/hero/HeroClass.java:221-225`
- **Bestiary card:** `docs/bestiary/bosses.md#tengu`

### Claim bosses-9

- **Claim:** “Phase 2 is a maze fight where Tengu teleports after taking a set amount of damage; a Potion of Mind Vision helps locate him fast in the maze's small circular rooms, and there's always a poison-trap-free path to him if you track his placed traps carefully.”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Tengu>
- **Variant:** unknown
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Old layout. In v4.0.0 phase 2 is a single open elliptical arena of empty floor with no traps; the jump-on-damage part is right (bracket jumps to 5-7 tiles away). The trap-free-path guarantee belongs to phase 1.
- **Cites:** `core/…/levels/PrisonBossLevel.java:242-252`, `core/…/levels/PrisonBossLevel.java:489-509`, `core/…/mobs/Tengu.java:281-309`
- **Bestiary card:** `docs/bestiary/bosses.md#tengu`

### Claim bosses-10

- **Claim:** “Paralysis-inflicting tools (Stunning weapon enchant, Wand of Avalanche, Curare-tipped darts, Potion of Paralytic Gas, bombs) are the best counters to Tengu; Potion of Invisibility lets you close distance in the maze without eating shuriken hits.”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Tengu>
- **Variant:** unknown
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Stunning enchant, Wand of Avalanche and curare darts do not exist in v4.0.0 (paralytic darts and Potion of Paralytic Gas do). Tengu is not immune to paralysis, and invisibility does stop his shurikens (enemyInFOV needs a visible hero), but his abilities still target the hero. There is no maze.
- **Cites:** `core/…/mobs/Tengu.java:349-354`, `core/…/mobs/Mob.java:290`, `core/…/mobs/Tengu.java:407-428`, `core/…/mobs/Tengu.java:508`
- **Bestiary card:** `docs/bestiary/bosses.md#tengu`

## DM-300 (`actors.mobs.DM300`)

### Claim bosses-11

- **Claim:** “DM-300 has no ranged attack, so pure kiting works: it releases toxic gas around itself and, every time it moves, drops an avalanche on a random adjacent tile that can damage and stun you. Since it matches your movement speed, step one tile away whenever gas reaches you — it will close back to melee range without getting a bonus attack out of you moving.”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/DM-300>
- **Variant:** unknown
- **Version:** unspecified
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Outdated (original Pixel Dungeon behaviour). In v4.0.0 the gas vent is a ranged zap along a path to your cell (100 gas on you), rockfalls are a cooldown ability centred on you (not a rock on every move), abilities used at range cost it no turn, and it moves at double speed while supercharged. Kiting at range invites free abilities.
- **Cites:** `core/…/mobs/DM300.java:373-402`, `core/…/mobs/DM300.java:238-283`, `core/…/mobs/DM300.java:409-471`, `core/…/mobs/DM300.java:348-351`, `core/…/mobs/DM300.java:256-259`
- **Bestiary card:** `docs/bestiary/bosses.md#dm300`

### Claim bosses-12

- **Claim:** “Come in with armor that can absorb roughly its top hit (~24 damage).”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/DM-300>
- **Variant:** unknown
- **Version:** unspecified
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Its melee is NormalIntRange(15, 25), so the top hit is 25 before armor.
- **Cites:** `core/…/mobs/DM300.java:93-96`
- **Bestiary card:** `docs/bestiary/bosses.md#dm300`

### Claim bosses-13

- **Claim:** “It heals quickly if it is standing on a triggered trap, so don't let it linger over sprung traps.”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/DM-300>
- **Variant:** unknown
- **Version:** unspecified
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** It does not heal. Stepping onto a non-energized INACTIVE_TRAP tile while hunting gives it a Barrier shield of 30 + (missing HP)/10. The practical advice (keep it off sprung-trap tiles) still holds.
- **Cites:** `core/…/mobs/DM300.java:321-346`
- **Bestiary card:** `docs/bestiary/bosses.md#dm300`

### Claim bosses-14

- **Claim:** “Slow, Paralyze, or Freeze effects reduce how often it can hit you.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/DM-300>
- **Variant:** unknown
- **Version:** unspecified
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Paralysis stops its turn (act() returns early while paralysed) and it has no Paralysis resistance. It resists Slow, Chill and Frost, so those last half as long.
- **Cites:** `core/…/mobs/DM300.java:162-163`, `core/…/mobs/DM300.java:677-684`
- **Bestiary card:** `docs/bestiary/bosses.md#dm300`

### Claim bosses-15

- **Claim:** “Potion of Purification counters the Toxic Gas.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/DM-300>
- **Variant:** unknown
- **Version:** unspecified
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** A drunk Potion of Purity gives 20 turns of BlobImmunity, which includes ToxicGas (and also the Electricity source of pylon shocks and the energized floor).
- **Cites:** `core/…/buffs/BlobImmunity.java:76`, `core/…/potions/PotionOfPurity.java:95`
- **Bestiary card:** `docs/bestiary/bosses.md#dm300`

### Claim bosses-16

- **Claim:** “Potion of Purification counters the avalanche's Paralysis.”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/DM-300>
- **Variant:** unknown
- **Version:** unspecified
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** The rockfall's paralysis is a Paralysis buff applied directly by the falling rock, not a gas, and BlobImmunity does not list Paralysis; Purity does not prevent it.
- **Cites:** `core/…/mobs/DM300.java:697-699`, `core/…/buffs/BlobImmunity.java:63-77`
- **Bestiary card:** `docs/bestiary/bosses.md#dm300`

### Claim bosses-17

- **Claim:** “Bring a weapon averaging around 20 damage per hit and 5-8 Potions of Healing.”
- **Tier:** 3 (UNVERIFIED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/DM-300>
- **Variant:** unknown
- **Version:** unspecified
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Preparation advice (weapon averaging ~20 per hit, 5-8 Potions of Healing); not checkable from code. For scale: HT is 300 (400 challenged), DR 0-10.
- **Bestiary card:** `docs/bestiary/bosses.md#dm300`

## King of Dwarves (`actors.mobs.DwarfKing`)

### Claim bosses-18

- **Claim:** “The Dwarf King fight takes place in a circular throne room with pedestals; he periodically summons a subject (roughly every 10-14 turns) and uses a special ability on a similar cadence. Minions are mostly Ghouls, with roughly every fourth summon instead being a Warlock or a Monk; all summoned minions spawn already aware of the Hero and drop no loot or EXP.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Bosses>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Summon and ability cooldowns are 10-14 (8-10 on Stronger Bosses); every 4th summon is a monk or warlock, the rest ghouls; summons appear HUNTING with maxLvl -2 (no EXP, no loot once the hero is above level 0). The arena is a diamond with 4 pedestals.
- **Cites:** `core/…/mobs/DwarfKing.java:113`, `core/…/mobs/DwarfKing.java:114`, `core/…/mobs/DwarfKing.java:163`, `core/…/mobs/DwarfKing.java:187`, `core/…/mobs/DwarfKing.java:319`, `core/…/mobs/DwarfKing.java:320`, `core/…/mobs/DwarfKing.java:322`, `core/…/mobs/DwarfKing.java:699`, `core/…/mobs/DwarfKing.java:702`, `core/…/mobs/Mob.java:951`, `core/…/mobs/Mob.java:1060`, `core/…/levels/CityBossLevel.java:82`, `core/…/levels/CityBossLevel.java:83`, `core/…/levels/CityBossLevel.java:84`, `core/…/levels/CityBossLevel.java:85`, `core/…/levels/CityBossLevel.java:163`
- **Bestiary card:** `docs/bestiary/bosses.md#dwarfking`

### Claim bosses-19

- **Claim:** “Priority-kill any summoned dwarf skeletons/undead minions first, since they can chain-stun the Hero into a long stun-lock while taking heavy damage; avoid letting yourself be surrounded by them.”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Bosses>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Shattered's King summons ghouls, monks, warlocks (and golems on Stronger Bosses), not skeletons, and none of them stuns or paralyses. This describes the original Pixel Dungeon undead dwarves.
- **Cites:** `core/…/mobs/DwarfKing.java:604`, `core/…/mobs/DwarfKing.java:617`, `core/…/mobs/DwarfKing.java:624`, `core/…/mobs/DwarfKing.java:639`, `core/…/mobs/Ghoul.java:65`, `core/…/mobs/Ghoul.java:66`
- **Bestiary card:** `docs/bestiary/bosses.md#dwarfking`

### Claim bosses-20

- **Claim:** “Throw a Potion of Toxic Gas into the middle of the throne room at the start of the fight, because the King consistently moves to that central spot, and the gas deals roughly 10 HP per turn to him. Do NOT use a Potion of Paralytic Gas on him — he is immune to many potions and you risk paralyzing yourself instead.”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Bosses>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Toxic gas deals 1 + depth/5 = 5 per turn on depth 20, not about 10; the King is invulnerable to it in phase 2 (when he sits on the central throne), and in phase 3 the damage is deferred. He is not immune to Paralytic Gas: his BOSS immunities are only AllyBuff and Dread. The warning about gassing yourself is fair.
- **Cites:** `core/…/blobs/ToxicGas.java:40`, `core/…/mobs/DwarfKing.java:451`, `core/…/mobs/DwarfKing.java:473`, `core/…/mobs/DwarfKing.java:498`, `core/…/actors/Char.java:1414`, `core/…/actors/Char.java:1415`, `core/…/blobs/ParalyticGas.java:51`, `core/…/blobs/ParalyticGas.java:52`
- **Bestiary card:** `docs/bestiary/bosses.md#dwarfking`

### Claim bosses-21

- **Claim:** “Use a Scroll of Mirror Image early in the fight (rather than saving it) so the summoned mirror images attack the King himself rather than getting wasted on summoned skeletons; a Scroll of Psionic Blast is also considered strong once he has summoned several skeletons, though its damage is inconsistent.”
- **Tier:** 3 (UNVERIFIED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Bosses>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Mirror-image targeting is AI behaviour not checked here. The King has no skeleton summons, and BOSS resists Scroll of Psionic Blast (half effect on the King).
- **Cites:** `core/…/actors/Char.java:1414`
- **Bestiary card:** `docs/bestiary/bosses.md#dwarfking`

### Claim bosses-22

- **Claim:** “SOURCE IS THIN / MISMATCHED VERSION: this specific advice thread is titled about the boss in the \*original\* Pixel Dungeon (Watabou's game), not Shattered Pixel Dungeon — it describes the King of Dwarves summoning 1-5 undead dwarves from two pedestals and warns the undead can paralyze the Hero if surrounded. The mechanics described (pedestal count, paralysis-on-surround) may not match Shattered's rebalanced Dwarf King fight and should be treated as folklore from the base game rather than confirmed Shattered-specific tactics.”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/f/p/1997203027525688865>
- **Variant:** unknown
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Original Pixel Dungeon folklore. Shattered's throne room has 4 summoning pedestals, and its summons have no paralysis effect.
- **Cites:** `core/…/levels/CityBossLevel.java:82`, `core/…/levels/CityBossLevel.java:83`, `core/…/levels/CityBossLevel.java:84`, `core/…/levels/CityBossLevel.java:85`, `core/…/mobs/DwarfKing.java:329`, `core/…/mobs/DwarfKing.java:604`, `core/…/mobs/DwarfKing.java:617`, `core/…/mobs/DwarfKing.java:624`
- **Bestiary card:** `docs/bestiary/bosses.md#dwarfking`

## Dwarf warlock (DK summon) (`actors.mobs.DwarfKing.DKWarlock`)

### Claim bosses-23

- **Claim:** “General counter to any enemy with a long-range attack: as soon as it spots you, retreat behind a door or around a corner so it loses line of sight and can't use its ranged attack; this applies to Warlocks, Scorpios, Wisps and similar spellcasters/archers throughout these floors.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Applies to DK warlocks too, but inside the sealed arena the only line blockers are the four throne-side statues and other characters.
- **Cites:** `core/…/mobs/Warlock.java:80`, `core/…/levels/CityBossLevel.java:169`, `core/…/levels/CityBossLevel.java:170`, `core/…/levels/CityBossLevel.java:171`, `core/…/levels/CityBossLevel.java:172`
- **Bestiary card:** `docs/bestiary/bosses.md#dwarfking-dkwarlock`

## Yog-Dzewa (`actors.mobs.YogDzewa`)

### Claim bosses-24

- **Claim:** “Every physical hit the Hero lands on Yog-Dzewa's central eye spawns larvae that attack the Hero; damage the Hero deals to Yog-Dzewa is divided by 4 while both fists are alive, and by 2 once only one fist remains — implying the fists should be killed first before committing to attacking the eye directly.”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Bosses>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Old-version mechanics. Summons run on a 10-15 turn timer, not per hit. Damage to Yog only shortens that timer by dmg/10. There is no /4 or /2 damage scaling: Yog is fully invulnerable (isInvulnerable returns true) while ANY fist exists, and normally only one fist exists at a time. The practical conclusion (kill the fist before hitting Yog) is forced by the code anyway.
- **Cites:** `core/…/mobs/YogDzewa.java:297`, `core/…/mobs/YogDzewa.java:333`, `core/…/mobs/YogDzewa.java:388`, `core/…/mobs/YogDzewa.java:397`, `core/…/mobs/YogDzewa.java:407`, `core/…/mobs/YogDzewa.java:408`
- **Bestiary card:** `docs/bestiary/bosses.md#yogdzewa`

## Scorpio (Yog summon) (`actors.mobs.YogDzewa.YogScorpio`)

### Claim bosses-25

- **Claim:** “Scorpios shoot a ranged spike attack and behave similarly to the Gnoll Trickster: they have no melee attack and will try to flee if the Hero gets adjacent. Their ranged hits deal physical damage (reducible by armor) and have a chance to inflict Cripple. They are described as very manageable if you fight from a doorway, though repeated door use can eventually break the door.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Core mechanics confirmed: no attack when adjacent, retreats (getFurther) while hunting, the shot is a normal attack so armor DR applies, and 50% Cripple. It backs away whenever it sees you, not only when you are adjacent. The door caveat is wrong: doors have no durability and only toggle DOOR/OPEN_DOOR (core/…/levels/features/Door.java:36,54). They are removed only by fire or destroy. See the split entry below. Acidic inherits all of this from Scorpio. YogScorpio inherits all of this from Scorpio.
- **Cites:** `core/…/mobs/Scorpio.java:74`, `core/…/mobs/Scorpio.java:75`, `core/…/mobs/Scorpio.java:81`, `core/…/mobs/Scorpio.java:82`, `core/…/mobs/Scorpio.java:90`, `core/…/mobs/Scorpio.java:91`, `core/…/actors/Char.java:388`, `core/…/actors/Char.java:390`
- **Bestiary card:** `docs/bestiary/bosses.md#yogdzewa-yogscorpio`

### Claim bosses-26

- **Claim:** “Split from the claim above: 'repeated door use can eventually break the door.'”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Doors only switch between DOOR and OPEN_DOOR. There is no use counter. A door is removed only when flammable terrain is destroyed (fire, the Eye/Yog beams), which leaves EMBERS.
- **Cites:** `core/…/features/Door.java:36`, `core/…/features/Door.java:54`, `core/…/levels/Level.java:928`, `core/…/levels/Level.java:929`, `core/…/levels/Level.java:930`, `core/…/levels/Level.java:931`, `core/…/levels/Level.java:932`, `core/…/levels/Level.java:933`, `core/…/levels/Level.java:934`
- **Bestiary card:** `docs/bestiary/bosses.md#yogdzewa-yogscorpio`

## Burning fist (`actors.mobs.YogFist.BurningFist`)

### Claim bosses-27

- **Claim:** “A Potion of Paralytic Gas only affects the Burning Fist in this encounter; the Rotting Fist is immune to paralysis, so don't waste a paralytic gas potion trying to lock it down.”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Bosses>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Half right for this fist: Paralysis does affect the Burning fist (its immunities are Sleep, Frost, Burning/Blazing, AllyBuff, Dread). But the claim that it is the ONLY fist affected is wrong: no fist is paralysis-immune.
- **Cites:** `core/…/mobs/YogFist.java:194`, `core/…/mobs/YogFist.java:281`, `core/…/actors/Char.java:1415`, `core/…/actors/Char.java:1424`
- **Bestiary card:** `docs/bestiary/bosses.md#yogfist-burningfist`

### Claim bosses-28

- **Claim:** “After the Rotting Fist is dead, deal with the Burning Fist by hiding behind a wall or otherwise out of its spell range to force it to close distance before you engage it in melee.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Bosses>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** The mechanism holds: its ranged zap needs a clear MAGIC_BOLT line and the hero in FOV, so hiding behind a wall forces it to walk to you. Caveats: it still zaps when adjacent if its 8-12 act cooldown is up (canRangedInMelee), and melee means standing in its 3x3 fire. The 'after the Rotting Fist is dead' framing comes from an older two-fist Yog. Now the three fists come one at a time in seeded random order.
- **Cites:** `core/…/mobs/YogFist.java:105`, `core/…/mobs/YogFist.java:106`, `core/…/mobs/YogFist.java:131`, `core/…/mobs/Mob.java:1328`, `core/…/mobs/Mob.java:1353`
- **Bestiary card:** `docs/bestiary/bosses.md#yogfist-burningfist`

### Claim bosses-29

- **Claim:** “The Burning Fist is especially weak against Bleeding, Poison, and Vertigo debuffs; inflict these before or during the fight for extra damage-over-time and crowd control.”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Bosses>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** The code has no special weakness. BurningFist has no damage multiplier or vulnerability for Bleeding, Poison or Vertigo. They apply normally because it is not immune to them (FIERY only grants Burning/Blazing immunity), but they do no extra damage.
- **Cites:** `core/…/mobs/YogFist.java:216`, `core/…/mobs/YogFist.java:218`, `core/…/mobs/YogFist.java:219`, `core/…/mobs/YogFist.java:221`, `core/…/mobs/YogFist.java:222`, `core/…/mobs/YogFist.java:224`, `core/…/mobs/YogFist.java:225`, `core/…/mobs/YogFist.java:227`, `core/…/mobs/YogFist.java:229`, `core/…/mobs/YogFist.java:230`, `core/…/mobs/YogFist.java:231`, `core/…/mobs/YogFist.java:232`, `core/…/mobs/YogFist.java:233`, `core/…/mobs/YogFist.java:235`, `core/…/mobs/YogFist.java:236`, `core/…/mobs/YogFist.java:238`, `core/…/mobs/YogFist.java:239`, `core/…/mobs/YogFist.java:240`, `core/…/mobs/YogFist.java:241`, `core/…/mobs/YogFist.java:242`, `core/…/mobs/YogFist.java:243`, `core/…/mobs/YogFist.java:244`, `core/…/mobs/YogFist.java:245`, `core/…/mobs/YogFist.java:247`, `core/…/mobs/YogFist.java:248`, `core/…/mobs/YogFist.java:249`, `core/…/mobs/YogFist.java:250`, `core/…/mobs/YogFist.java:251`, `core/…/mobs/YogFist.java:252`, `core/…/mobs/YogFist.java:254`, `core/…/mobs/YogFist.java:255`, `core/…/mobs/YogFist.java:257`, `core/…/mobs/YogFist.java:258`, `core/…/mobs/YogFist.java:259`, `core/…/mobs/YogFist.java:261`, `core/…/mobs/YogFist.java:262`, `core/…/mobs/YogFist.java:263`, `core/…/mobs/YogFist.java:264`, `core/…/mobs/YogFist.java:265`, `core/…/mobs/YogFist.java:266`, `core/…/mobs/YogFist.java:267`, `core/…/mobs/YogFist.java:269`, `core/…/mobs/YogFist.java:270`, `core/…/mobs/YogFist.java:271`, `core/…/mobs/YogFist.java:272`, `core/…/mobs/YogFist.java:273`, `core/…/mobs/YogFist.java:274`, `core/…/mobs/YogFist.java:275`, `core/…/mobs/YogFist.java:276`, `core/…/mobs/YogFist.java:278`, `core/…/mobs/YogFist.java:280`, `core/…/mobs/YogFist.java:281`, `core/…/mobs/YogFist.java:283`, `core/…/mobs/YogFist.java:284`, `core/…/mobs/YogFist.java:285`, `core/…/mobs/YogFist.java:287`, `core/…/actors/Char.java:1423`, `core/…/actors/Char.java:1424`
- **Bestiary card:** `docs/bestiary/bosses.md#yogfist-burningfist`

## Rotting fist (`actors.mobs.YogFist.RottingFist`)

### Claim bosses-30

- **Claim:** “Isolate and fight the two fists of Yog-Dzewa separately rather than together. Fight the Rotting Fist while standing on water: it inflicts Caustic Ooze, and fighting on water lets tools like Seed of Earthroot / Greaves of Nature tank the damage more easily. Note the Rotting Fist itself also heals 4 HP per turn while it is on/near water, so this is a damage-mitigation choice for the Hero, not a way to deny the fist's own healing.”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Rotting_fist>
- **Variant:** unknown
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Partly right, contradicted on the numbers and setup. Right: its melee oozes (50%) and water washes Ooze off. Wrong: it heals HT/50 = 6 HP per act, not 4, and only while standing ON water, not near it. And normally only one fist exists at a time: a fist spawns at each 300-HP phase and Yog is invulnerable while any fist lives, so there is no pair to separate. Only Stronger Bosses spawns two at once, and then the Rotting fist's partner is the Rusted fist, not the Burning fist. 'Greaves of Nature' is not an item in this codebase.
- **Cites:** `core/…/mobs/YogFist.java:412`, `core/…/mobs/YogFist.java:413`, `core/…/mobs/YogFist.java:414`, `core/…/mobs/YogFist.java:452`, `core/…/mobs/YogFist.java:453`, `core/…/buffs/Ooze.java:93`, `core/…/mobs/YogDzewa.java:388`, `core/…/mobs/YogDzewa.java:411`, `core/…/mobs/YogDzewa.java:419`, `core/…/mobs/YogDzewa.java:421`, `core/…/mobs/YogDzewa.java:422`
- **Bestiary card:** `docs/bestiary/bosses.md#yogfist-rottingfist`

### Claim bosses-31

- **Claim:** “A Potion of Paralytic Gas only affects the Burning Fist in this encounter; the Rotting Fist is immune to paralysis, so don't waste a paralytic gas potion trying to lock it down.”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Bosses>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** No Paralysis immunity. Its immunities are Sleep (all fists), ToxicGas (its own), Ooze (ACIDIC) and AllyBuff/Dread (BOSS). Paralytic gas works on it. This may be confused with its immunity to Toxic Gas.
- **Cites:** `core/…/mobs/YogFist.java:194`, `core/…/mobs/YogFist.java:404`, `core/…/mobs/YogFist.java:461`, `core/…/actors/Char.java:1415`, `core/…/actors/Char.java:1428`
- **Bestiary card:** `docs/bestiary/bosses.md#yogfist-rottingfist`
