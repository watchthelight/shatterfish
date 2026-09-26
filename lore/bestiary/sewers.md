---
topic: "Bestiary lore: Sewers (depths 1-4)"
tag: v4.0.0
sources:
  - https://pixeldungeon.fandom.com/wiki/Marsupial_rat
  - https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Gnoll_Scout
  - https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Sewer_Crab
  - https://pixeldungeon.fandom.com/wiki/Fetid_rat
retrieved: 2026-09-26
tiers:
  tier1: 8
  contradicted: 2
  unverified: 0
claims: 10
---

# Lore: Sewers (depths 1-4)

Community claims about these enemies, each graded against the pinned code. Tier 1: the code
confirms it. Tier F: the code contradicts it. Tier 3: not settled by the code. The graded card
is in `docs/bestiary/sewers.md`; a claim that reached this file is never a mechanic by itself, only its
verdict and the citations are.

## Marsupial rat (`actors.mobs.Rat`)

### Claim sewers-1

- **Claim:** “Don't waste resources on a lone Marsupial Rat unless threatened; save consumables for deeper floors. They're only dangerous in numbers, so if a group is chasing you, retreat to a doorway/corridor and kill them one at a time.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Marsupial_rat>
- **Variant:** unknown
- **Version:** old wiki page (flagged oldVersion)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Mechanics confirmed: 8 HP, 1-4 damage, adjacent-only attack, so a corridor limits attackers. Correction: the door cell itself is not a one-at-a-time spot (3 room-side neighbours); use a corridor tile. 'Save consumables' is judgment, not code.
- **Cites:** `core/…/mobs/Rat.java:36-62`, `core/…/mobs/Mob.java:558-568`, `core/…/levels/Level.java:1533-1543`
- **Bestiary card:** `docs/bestiary/sewers.md#rat`

## Albino rat (`actors.mobs.Albino`)

### Claim sewers-2

- **Claim:** “Albino Rats (a rare Marsupial Rat variant) have considerably more HP than normal rats and inflict Bleeding, so treat them as tougher than a normal rat encounter.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Marsupial_rat>
- **Variant:** unknown
- **Version:** old wiki page (flagged oldVersion)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Confirmed: HT 12 vs the rat's 8, and a 50% bleed on damaging hits. Same speed, evasion and damage as a rat otherwise.
- **Cites:** `core/…/mobs/Albino.java:36`, `core/…/mobs/Rat.java:36`, `core/…/mobs/Albino.java:44-50`
- **Bestiary card:** `docs/bestiary/sewers.md#albino`

## Gnoll scout (`actors.mobs.Gnoll`)

### Claim sewers-3

- **Claim:** “Whittle Gnoll Scouts down with darts or wand charges as they approach, before they reach melee range, and avoid fighting two or more at once — even killing them one by one you won't out-heal the combined damage and can die.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Gnoll_Scout>
- **Variant:** spd
- **Version:** unspecified (SPD wiki, no version stated)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Confirmed: scouts are melee-only (no canAttack override), so every tile they walk toward you is a free ranged shot, and each extra adjacent gnoll adds its own 1-6 attack. 'You won't out-heal two' is a judgment, not a code fact.
- **Cites:** `core/…/mobs/Gnoll.java:29-58`, `core/…/mobs/Mob.java:558-568`, `core/…/missiles/MissileWeapon.java:223-233`
- **Bestiary card:** `docs/bestiary/sewers.md#gnoll`

## Sewer crab (`actors.mobs.Crab`)

### Claim sewers-4

- **Claim:** “Crabs move twice as fast as the Hero, so ranged kiting is hard.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Sewer_Crab>
- **Variant:** spd
- **Version:** unspecified (SPD wiki, no version stated)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** baseSpeed 2 and move cost 1/speed confirm it: a crab closes 2 tiles per hero turn. (Part of a compound claim.)
- **Cites:** `core/…/mobs/Crab.java:36`, `core/…/mobs/Mob.java:1355`
- **Bestiary card:** `docs/bestiary/sewers.md#crab`

### Claim sewers-5

- **Claim:** “Use surprise attacks from around corners/doors (guaranteed hits) instead.”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Sewer_Crab>
- **Variant:** spd
- **Version:** unspecified (SPD wiki, no version stated)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** A surprise hit does always land, but the door ambush against a crab is not reliable: at speed 2 it acts again 0.5 turns after stepping into the door, sees you, and is no longer surprised, unless that step fell on a half-turn. Snipe it asleep or before it notices you instead. (Part of a compound claim.)
- **Cites:** `core/…/mobs/Crab.java:36`, `core/…/mobs/Mob.java:1327`, `core/…/mobs/Mob.java:1355`, `core/…/actors/Actor.java:50-52`
- **Bestiary card:** `docs/bestiary/sewers.md#crab`

### Claim sewers-6

- **Claim:** “Debuff them with stun, paralysis, burning or poison via seeds/traps.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Sewer_Crab>
- **Variant:** spd
- **Version:** unspecified (SPD wiki, no version stated)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** The crab has no properties, immunities or resistances, so all of these apply; paralysis also zeroes its evasion. (Part of a compound claim.)
- **Cites:** `core/…/mobs/Crab.java:29-59`, `core/…/mobs/Mob.java:277-282`, `core/…/mobs/Mob.java:796-802`
- **Bestiary card:** `docs/bestiary/sewers.md#crab`

### Claim sewers-7

- **Claim:** “Gear up to a Tier-2 weapon/armor before depth 3-4 where crabs start appearing, since they hit hardest of any Sewer enemy.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Sewer_Crab>
- **Variant:** spd
- **Version:** unspecified (SPD wiki, no version stated)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Crabs first appear on depth 3, and 1-7 damage at accuracy 12 is the highest of the regular sewer mobs (gnoll 1-6/10, slime 2-5/12, rat and snake 1-4). The rare Gnoll Exile (1-10) and Goo hit harder. The gear advice is judgment. (Part of a compound claim.)
- **Cites:** `core/…/mobs/MobSpawner.java:85-97`, `core/…/mobs/Crab.java:45-53`, `core/…/mobs/Gnoll.java:44-52`, `core/…/mobs/Slime.java:47-55`, `core/…/mobs/GnollExile.java:59-67`
- **Bestiary card:** `docs/bestiary/sewers.md#crab`

### Claim sewers-8

- **Claim:** “Because a Crab moves 2 tiles for every 1 of yours, plan positioning: if you're one tile away, back up one more tile so the crab is forced to spend its whole turn closing distance and can't attack you that turn.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Sewer_Crab>
- **Variant:** spd
- **Version:** unspecified (SPD wiki, no version stated)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Follows from move cost 0.5 and attack cost 1: if you end your turn 3+ tiles away, the crab spends its whole turn closing and you strike first. It costs you your action too, so it trades tempo for the first hit. Keep doing it until it is adjacent at the start of your turn.
- **Cites:** `core/…/mobs/Crab.java:36`, `core/…/mobs/Mob.java:1355`, `core/…/mobs/Mob.java:753-757`
- **Bestiary card:** `docs/bestiary/sewers.md#crab`

## Fetid rat (`actors.mobs.FetidRat`)

### Claim sewers-9

- **Claim:** “Fetid Rat surrounds itself with paralytic gas; standing next to it for more than one turn paralyzes you and it will kill you while paralyzed.”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Fetid_rat>
- **Variant:** unknown
- **Version:** old wiki page (flagged oldVersion)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** There is no passive aura. The rat seeds 20 Stench Gas on its own tile only when a hit lands on it (defenseProc); an unhit rat emits nothing, and wand or DOT damage releases none. Once gas exists, anyone standing in it gets 2 turns of paralysis per tick, and its bite is only 1-4, so the danger is being locked while it chews. (Part of a compound claim.)
- **Cites:** `core/…/mobs/FetidRat.java:78-84`, `core/…/blobs/StenchGas.java:53-65`
- **Bestiary card:** `docs/bestiary/sewers.md#fetidrat`

### Claim sewers-10

- **Claim:** “In melee, let it move to you, hit once, then back away immediately so its gas cloud doesn't engulf you; don't chase it into the cloud.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Fetid_rat>
- **Variant:** unknown
- **Version:** old wiki page (flagged oldVersion)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Consistent with code: each landed hit seeds gas on the rat's tile, and paralysis only applies to characters standing in gas cells. Better still, fight it with wands or from range. (Part of a compound claim.)
- **Cites:** `core/…/mobs/FetidRat.java:78-84`, `core/…/blobs/StenchGas.java:53-65`
- **Bestiary card:** `docs/bestiary/sewers.md#fetidrat`
