---
topic: "Bestiary lore: Quest branches and quest foes"
tag: v4.0.0
sources:
  - https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Old_Mine
  - https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies
  - https://pixeldungeon.fandom.com/wiki/Fire_elemental
  - https://pixeldungeon.fandom.com/wiki/Wraith
  - https://pixeldungeon.fandom.com/wiki/User_blog:108.184.82.95/How_to_kill_Wraiths
  - https://docs.oldmartijntje.nl/Archive/Shattered-Pixel-Dungeon-Strategy-Guide
  - https://pixeldungeon.fandom.com/wiki/Marsupial_rat
  - https://pixeldungeon.fandom.com/wiki/Gnoll_shaman
  - https://pixeldungeon.fandom.com/wiki/Skeleton
  - https://www.youtube.com/watch?v=TE1zJ1Eh7fg
retrieved: 2026-09-26
tiers:
  tier1: 25
  contradicted: 11
  unverified: 3
claims: 39
---

# Lore: Quest branches and quest foes

Community claims about these enemies, each graded against the pinned code. Tier 1: the code
confirms it. Tier F: the code contradicts it. Tier 3: not settled by the code. The graded card
is in `docs/bestiary/quest-branch.md`; a claim that reached this file is never a mechanic by itself, only its
verdict and the citations are.

## Crystal guardian (`actors.mobs.CrystalGuardian`)

### Claim quest-branch-1

- **Claim:** “Guardians in the Crystal Caves spawn only at floor generation (one per large room, up to 3 per floor), do not respawn, and start asleep surrounded by crystals — they won't notice the Hero unless there's a clear unobstructed path of sight. If they do notice and pursue, they can break crystals blocking their path at the cost of an extra turn. Community note: Guardians reportedly cannot be killed by any means, so the practical approach is avoidance (don't open a sightline to a sleeping Guardian) rather than combat.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Old_Mine>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Confirmed: 3 large rooms per mine, one guardian each, spawningWeight 0 and the mine respawner only makes wisps, so none respawn; it starts SLEEPING sealed in crystal; it stomps crystals at +1 move while hunting; HP is reset to 1 instead of dying. Nuance: the wake check is a walkable-path check (PathFinder over passable cells), not a line-of-sight check, and damage or the spire's 3rd-hit alarm (CrystalSpire.java:423-460) wakes/aggroes it regardless.
- **Cites:** `core/…/levels/MiningLevel.java:95`, `core/…/levels/MiningLevel.java:101`, `core/…/levels/MiningLevel.java:103`, `core/…/quest/MineLargeRoom.java:77`, `core/…/quest/MineLargeRoom.java:84`, `core/…/quest/MineLargeRoom.java:88`, `core/…/quest/MineLargeRoom.java:101`, `core/…/mobs/CrystalGuardian.java:57`, `core/…/mobs/CrystalGuardian.java:58`, `core/…/mobs/CrystalGuardian.java:155`, `core/…/mobs/CrystalGuardian.java:156`, `core/…/mobs/CrystalGuardian.java:201`, `core/…/mobs/CrystalGuardian.java:216`, `core/…/mobs/CrystalGuardian.java:224`, `core/…/mobs/CrystalGuardian.java:231`, `core/…/mobs/CrystalGuardian.java:234`, `core/…/mobs/CrystalGuardian.java:236`, `core/…/mobs/CrystalGuardian.java:255`, `core/…/mobs/CrystalGuardian.java:258`, `core/…/mobs/CrystalGuardian.java:259`, `core/…/mobs/CrystalGuardian.java:260`, `core/…/levels/MiningLevel.java:163`, `core/…/levels/MiningLevel.java:164`
- **Bestiary card:** `docs/bestiary/quest-branch.md#crystalguardian`

## Crystal wisp (`actors.mobs.CrystalWisp`)

### Claim quest-branch-2

- **Claim:** “Wisps are the common respawning enemy of the Crystal Caves branch; they function like other spellcasters (shamans, warlocks), can move directly through the crystal terrain while chasing the Hero, but cannot shoot their ranged attack through crystals — making the crystals themselves useful cover to break their line of sight even though they can walk around/through them to reach you eventually.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Old_Mine>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Confirmed: the crystal mine's createMob returns CrystalWisp (3x respawn cooldown); modifyPassable lets it fly through MINE_CRYSTAL; the beam is a MAGIC_BOLT (STOP_SOLID) and crystal is SOLID, so crystal blocks the beam. Crystal is not LOS_BLOCKING, so it still sees you through crystal and will drift through to melee.
- **Cites:** `core/…/levels/MiningLevel.java:159`, `core/…/levels/MiningLevel.java:163`, `core/…/levels/MiningLevel.java:164`, `core/…/levels/MiningLevel.java:173`, `core/…/levels/MiningLevel.java:175`, `core/…/mobs/CrystalWisp.java:48`, `core/…/mobs/CrystalWisp.java:69`, `core/…/mobs/CrystalWisp.java:70`, `core/…/mobs/CrystalWisp.java:71`, `core/…/mobs/CrystalWisp.java:92`, `core/…/mobs/CrystalWisp.java:93`, `core/…/mobs/CrystalWisp.java:94`, `core/…/mechanics/Ballistica.java:49`, `core/…/levels/Terrain.java:126`
- **Bestiary card:** `docs/bestiary/quest-branch.md#crystalwisp`

### Claim quest-branch-3

- **Claim:** “General counter to any enemy with a long-range attack: as soon as it spots you, retreat behind a door or around a corner so it loses line of sight and can't use its ranged attack; this applies to Warlocks, Scorpios, Wisps and similar spellcasters/archers throughout these floors.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Applies to crystal wisps: the beam needs a MAGIC_BOLT line, which doors (SOLID+LOS_BLOCKING), walls, crystal and bodies block. Caveat for wisps: they fly through crystal, so crystal cover only denies the beam, not the approach.
- **Cites:** `core/…/mobs/CrystalWisp.java:92`, `core/…/mobs/CrystalWisp.java:93`, `core/…/mobs/CrystalWisp.java:94`, `core/…/mechanics/Ballistica.java:49`, `core/…/levels/Terrain.java:90`
- **Bestiary card:** `docs/bestiary/quest-branch.md#crystalwisp`

## Newborn fire elemental (`actors.mobs.Elemental.NewbornFireElemental`)

### Claim quest-branch-4

- **Claim:** “Fire Elementals burn the Hero out of water, so standing in water snuffs the fire immediately; fighting a Fire Elemental while on water tiles is the recommended defense.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Water part confirmed for the newborn: its fireball seeds only 2 fire on water cells (8 elsewhere) and Burning detaches from a non-flying target in water. Note the newborn's melee never ignites (Elemental.java:406-410); only the fireball burns.
- **Cites:** `core/…/mobs/Elemental.java:365`, `core/…/mobs/Elemental.java:366`, `core/…/mobs/Elemental.java:368`, `core/…/mobs/Elemental.java:373`, `core/…/buffs/Burning.java:99`, `core/…/buffs/Burning.java:100`, `core/…/buffs/Burning.java:178`, `core/…/buffs/Burning.java:179`
- **Bestiary card:** `docs/bestiary/quest-branch.md#elemental-newbornfireelemental`

### Claim quest-branch-5

- **Claim:** “(though exposed scrolls in the backpack may still burn on the first hit)”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Item burning needs 4+ consecutive burning turns ((turns-3)/3 chance), so the first hit cannot burn scrolls; water ends Burning after at most one tick.
- **Cites:** `core/…/buffs/Burning.java:120`, `core/…/buffs/Burning.java:121`, `core/…/buffs/Burning.java:122`
- **Bestiary card:** `docs/bestiary/quest-branch.md#elemental-newbornfireelemental`

### Claim quest-branch-6

- **Claim:** “Fire Elementals take heavy bonus damage from the Chill effect”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Fire_elemental>
- **Variant:** unknown
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Each Frost/Chill application is replaced by HT/2-3HT/5 damage (30-36 at 60 HT).
- **Cites:** `core/…/mobs/Elemental.java:191`, `core/…/mobs/Elemental.java:192`, `core/…/mobs/Elemental.java:193`, `core/…/mobs/Elemental.java:194`, `core/…/mobs/Elemental.java:237`, `core/…/mobs/Elemental.java:238`
- **Bestiary card:** `docs/bestiary/quest-branch.md#elemental-newbornfireelemental`

### Claim quest-branch-7

- **Claim:** “to the point that a chill/freeze source can one-hit-kill one”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Fire_elemental>
- **Variant:** unknown
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** One application deals 30-36 of 60 HP: it cannot one-shot a full-HP elemental; two applications (or one on a hurt elemental) kill.
- **Cites:** `core/…/mobs/Elemental.java:71`, `core/…/mobs/Elemental.java:193`
- **Bestiary card:** `docs/bestiary/quest-branch.md#elemental-newbornfireelemental`

### Claim quest-branch-8

- **Claim:** “do not use a Wand of Firebolt or a Potion of Liquid Flame on a Fire Elemental, as fire damage does nothing to it and may slightly heal it instead”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Fire_elemental>
- **Variant:** unknown
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** FIERY makes it immune to Burning/Blazing (so Liquid Flame fire does nothing) but only resists Wand of Fireblast (half damage, not zero); no code heals it from fire. "Wand of Firebolt" is the pre-rework name. The advice not to use fire is still sound.
- **Cites:** `core/…/actors/Char.java:1423`, `core/…/actors/Char.java:1424`, `core/…/mobs/Elemental.java:235`
- **Bestiary card:** `docs/bestiary/quest-branch.md#elemental-newbornfireelemental`

### Claim quest-branch-9

- **Claim:** “General terrain rule for the three City Elementals: stand on water against Fire Elementals to extinguish burning, but stay off water against Frost and Shock Elementals, since water amplifies their chill duration and their bonus electric damage respectively.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Fire half applies to the newborn (water cuts its fire and ends Burning). The frost/shock halves concern other elementals.
- **Cites:** `core/…/mobs/Elemental.java:365`, `core/…/mobs/Elemental.java:366`, `core/…/buffs/Burning.java:99`, `core/…/buffs/Burning.java:100`
- **Bestiary card:** `docs/bestiary/quest-branch.md#elemental-newbornfireelemental`

## Wraith (corpse dust) (`items.quest.CorpseDust.DustWraith`)

### Claim quest-branch-10

- **Claim:** “Before disturbing a Wraith-spawning tomb, stand with your back to a wall, statue, or room corner — wraiths spawn only in the four cardinal tiles around you, so blocking some of those tiles with terrain caps the number that can appear at once to 3 or fewer.”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Wraith>
- **Variant:** unknown
- **Version:** older version (source flagged oldVersion)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** The four-cardinal-tile rule is Wraith.spawnAround (tomb/heap wraiths). Corpse-dust wraiths instead appear on a random empty visible cell more than round(viewDistance/3) tiles away, so standing against a wall does not cap them; standing where no visible cell is 4+ tiles away does.
- **Cites:** `core/…/quest/CorpseDust.java:125`, `core/…/quest/CorpseDust.java:126`, `core/…/quest/CorpseDust.java:127`, `core/…/quest/CorpseDust.java:128`, `core/…/quest/CorpseDust.java:129`, `core/…/quest/CorpseDust.java:130`, `core/…/quest/CorpseDust.java:135`, `core/…/mobs/Wraith.java:106`, `core/…/mobs/Wraith.java:107`, `core/…/mobs/Wraith.java:108`
- **Bestiary card:** `docs/bestiary/quest-branch.md#dustwraith`

### Claim quest-branch-11

- **Claim:** “Wraiths have very high evasion, so melee attacks miss often; ... a Wraith only has 1 HP so any connecting hit - including a guaranteed surprise attack performed by retreating behind a door - kills it outright.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/User_blog:108.184.82.95/How_to_kill_Wraiths>
- **Variant:** unknown
- **Version:** older version (source flagged oldVersion)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** HT 1 and defense 5x accuracy (85-95 at depth 7-9): misses are common, any hit kills; a hit while it cannot see you is a surprise with defense 0.
- **Cites:** `core/…/mobs/Wraith.java:49`, `core/…/mobs/Wraith.java:82`, `core/…/mobs/Wraith.java:87`, `core/…/mobs/Mob.java:796`, `core/…/mobs/Mob.java:797`, `core/…/mobs/Mob.java:798`, `core/…/mobs/Mob.java:799`, `core/…/mobs/Mob.java:801`, `core/…/mobs/Mob.java:873`, `core/…/mobs/Mob.java:874`, `core/…/mobs/Mob.java:875`
- **Bestiary card:** `docs/bestiary/quest-branch.md#dustwraith`

### Claim quest-branch-12

- **Claim:** “they're described as weak to magic-type damage (including fire and poison)”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/User_blog:108.184.82.95/How_to_kill_Wraiths>
- **Variant:** unknown
- **Version:** older version (source flagged oldVersion)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** INORGANIC makes wraiths immune to Poison (and ToxicGas/Bleeding). There is no magic or fire weakness in code; magic simply skips the accuracy roll and 1 HP means any damage kills.
- **Cites:** `core/…/mobs/Wraith.java:57`, `core/…/actors/Char.java:1421`, `core/…/actors/Char.java:1422`, `core/…/wands/WandOfMagicMissile.java:62`
- **Bestiary card:** `docs/bestiary/quest-branch.md#dustwraith`

## Modified DM-100 (`actors.mobs.quest.vault.VaultDM100`)

### Claim quest-branch-13

- **Claim:** “General counter to any enemy with a long-range attack: as soon as it spots you, retreat behind a door or around a corner so it loses line of sight and can't use its ranged attack; this applies to Warlocks, Scorpios, Wisps and similar spellcasters/archers throughout these floors.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** The DM-100 bolt needs a clear MAGIC_BOLT line; a door or corner forces it to walk. In the vault, losing sight sends it to INVESTIGATING your last cell, not HUNTING.
- **Cites:** `core/…/mobs/DM100.java:76`, `core/…/mobs/DM100.java:77`, `core/…/mobs/DM100.java:78`, `core/…/mobs/DM100.java:87`, `core/…/mobs/DM100.java:88`, `core/…/mechanics/Ballistica.java:49`, `core/…/levels/Terrain.java:90`
- **Bestiary card:** `docs/bestiary/quest-branch.md#vaultdm100`

## Fire elemental (vault) (`actors.mobs.quest.vault.VaultElemental.Fire`)

### Claim quest-branch-14

- **Claim:** “Fire Elementals burn the Hero out of water, so standing in water snuffs the fire immediately; fighting a Fire Elemental while on water tiles is the recommended defense.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Neither its melee proc nor its zap can ignite a target standing in water, and Burning detaches in water.
- **Cites:** `core/…/mobs/Elemental.java:243`, `core/…/mobs/Elemental.java:251`, `core/…/mobs/Elemental.java:252`, `core/…/buffs/Burning.java:99`, `core/…/buffs/Burning.java:100`, `core/…/buffs/Burning.java:178`, `core/…/buffs/Burning.java:179`
- **Bestiary card:** `docs/bestiary/quest-branch.md#vaultelemental-fire`

### Claim quest-branch-15

- **Claim:** “(though exposed scrolls in the backpack may still burn on the first hit)”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Scrolls only burn after 4+ burning turns, and never in the vault level.
- **Cites:** `core/…/buffs/Burning.java:120`, `core/…/buffs/Burning.java:121`, `core/…/buffs/Burning.java:122`
- **Bestiary card:** `docs/bestiary/quest-branch.md#vaultelemental-fire`

### Claim quest-branch-16

- **Claim:** “Fire Elementals take heavy bonus damage from the Chill effect”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Fire_elemental>
- **Variant:** unknown
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Each Frost/Chill application is replaced by HT/2-3HT/5 damage (30-36 at 60 HT).
- **Cites:** `core/…/mobs/Elemental.java:191`, `core/…/mobs/Elemental.java:192`, `core/…/mobs/Elemental.java:193`, `core/…/mobs/Elemental.java:194`, `core/…/mobs/Elemental.java:237`, `core/…/mobs/Elemental.java:238`
- **Bestiary card:** `docs/bestiary/quest-branch.md#vaultelemental-fire`

### Claim quest-branch-17

- **Claim:** “to the point that a chill/freeze source can one-hit-kill one”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Fire_elemental>
- **Variant:** unknown
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** One application deals 30-36 of 60 HP; it cannot one-shot a full-HP elemental.
- **Cites:** `core/…/mobs/Elemental.java:71`, `core/…/mobs/Elemental.java:193`
- **Bestiary card:** `docs/bestiary/quest-branch.md#vaultelemental-fire`

### Claim quest-branch-18

- **Claim:** “do not use a Wand of Firebolt or a Potion of Liquid Flame on a Fire Elemental, as fire damage does nothing to it and may slightly heal it instead”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Fire_elemental>
- **Variant:** unknown
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** FIERY: immune to Burning/Blazing, only resists (halves) Wand of Fireblast; no healing from fire in code. "Firebolt" is the old wand name.
- **Cites:** `core/…/actors/Char.java:1423`, `core/…/actors/Char.java:1424`, `core/…/mobs/Elemental.java:235`
- **Bestiary card:** `docs/bestiary/quest-branch.md#vaultelemental-fire`

### Claim quest-branch-19

- **Claim:** “General terrain rule for the three City Elementals: stand on water against Fire Elementals to extinguish burning, but stay off water against Frost and Shock Elementals, since water amplifies their chill duration and their bonus electric damage respectively.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Fire half confirmed for this card.
- **Cites:** `core/…/mobs/Elemental.java:243`, `core/…/mobs/Elemental.java:251`, `core/…/buffs/Burning.java:99`, `core/…/buffs/Burning.java:100`
- **Bestiary card:** `docs/bestiary/quest-branch.md#vaultelemental-fire`

### Claim quest-branch-20

- **Claim:** “General counter to any enemy with a long-range attack: as soon as it spots you, retreat behind a door or around a corner so it loses line of sight and can't use its ranged attack; this applies to Warlocks, Scorpios, Wisps and similar spellcasters/archers throughout these floors.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Its zap needs a MAGIC_BOLT line and a spent cooldown; behind a door or corner it must melee.
- **Cites:** `core/…/mobs/Elemental.java:133`, `core/…/mobs/Elemental.java:137`, `core/…/mobs/Elemental.java:143`, `core/…/mobs/Elemental.java:145`, `core/…/mechanics/Ballistica.java:49`
- **Bestiary card:** `docs/bestiary/quest-branch.md#vaultelemental-fire`

## Frost elemental (vault) (`actors.mobs.quest.vault.VaultElemental.Frost`)

### Claim quest-branch-21

- **Claim:** “Frost Elementals chill the target for 3 turns on a ranged hit (5 turns if the target is standing in water) and have a 33% chance to chill on their melee hit as well; because water extends the chill duration, the community strategy is the opposite of the Fire Elemental fight — avoid fighting a Frost Elemental while standing in water.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Ranged proc always freezes; melee proc on 1/3 of hits, or every hit if you stand in water; chill adds 3 turns, 5 on water. Chill at its cap becomes Frost.
- **Cites:** `core/…/mobs/Elemental.java:494`, `core/…/mobs/Elemental.java:495`, `core/…/mobs/Elemental.java:496`, `core/…/mobs/Elemental.java:502`, `core/…/mobs/Elemental.java:503`, `core/…/blobs/Freezing.java:75`
- **Bestiary card:** `docs/bestiary/quest-branch.md#vaultelemental-frost`

### Claim quest-branch-22

- **Claim:** “Frost Elementals take heavy bonus damage from fire-based effects, so a Wand of Firebolt, Potion of Liquid Flame, or similar fire damage is the recommended counter.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Burning is in its harmfulBuffs: each application deals 30-36 (HT/2-3HT/5) instead of sticking. "Firebolt" is the old name of Wand of Fireblast.
- **Cites:** `core/…/mobs/Elemental.java:490`, `core/…/mobs/Elemental.java:191`, `core/…/mobs/Elemental.java:192`, `core/…/mobs/Elemental.java:193`, `core/…/mobs/Elemental.java:194`
- **Bestiary card:** `docs/bestiary/quest-branch.md#vaultelemental-frost`

### Claim quest-branch-23

- **Claim:** “General terrain rule for the three City Elementals: stand on water against Fire Elementals to extinguish burning, but stay off water against Frost and Shock Elementals, since water amplifies their chill duration and their bonus electric damage respectively.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Frost half confirmed: water makes every melee hit freeze and lengthens chill.
- **Cites:** `core/…/mobs/Elemental.java:495`, `core/…/blobs/Freezing.java:75`
- **Bestiary card:** `docs/bestiary/quest-branch.md#vaultelemental-frost`

### Claim quest-branch-24

- **Claim:** “General counter to any enemy with a long-range attack: as soon as it spots you, retreat behind a door or around a corner so it loses line of sight and can't use its ranged attack; this applies to Warlocks, Scorpios, Wisps and similar spellcasters/archers throughout these floors.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Its zap needs a MAGIC_BOLT line and a spent cooldown; behind a door or corner it must melee.
- **Cites:** `core/…/mobs/Elemental.java:133`, `core/…/mobs/Elemental.java:137`, `core/…/mobs/Elemental.java:143`, `core/…/mobs/Elemental.java:145`, `core/…/mechanics/Ballistica.java:49`
- **Bestiary card:** `docs/bestiary/quest-branch.md#vaultelemental-frost`

## Shock elemental (vault) (`actors.mobs.quest.vault.VaultElemental.Shock`)

### Claim quest-branch-25

- **Claim:** “Shock Elementals are an uncommon elemental variant whose ranged attack blinds the target for 5 turns, and whose melee attack also damages nearby creatures for 40% of the hit and deals 40% extra damage if the target being hit is standing in water; avoid fighting them while standing in water for the same reason as Frost Elementals.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Zap blinds for DURATION/2 = 5 turns; melee arcs 40% to nearby characters, and to the target itself only when it stands in water (the "40% extra"). Rarity (20%) matches Elemental.random / VaultElemental.random (VaultElemental.java:31-37).
- **Cites:** `core/…/mobs/Elemental.java:523`, `core/…/mobs/Elemental.java:525`, `core/…/mobs/Elemental.java:526`, `core/…/mobs/Elemental.java:530`, `core/…/mobs/Elemental.java:551`, `core/…/buffs/Blindness.java:29`
- **Bestiary card:** `docs/bestiary/quest-branch.md#vaultelemental-shock`

### Claim quest-branch-26

- **Claim:** “General terrain rule for the three City Elementals: stand on water against Fire Elementals to extinguish burning, but stay off water against Frost and Shock Elementals, since water amplifies their chill duration and their bonus electric damage respectively.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Shock half confirmed: water adds the 40% arc to you.
- **Cites:** `core/…/mobs/Elemental.java:525`, `core/…/mobs/Elemental.java:526`, `core/…/mobs/Elemental.java:530`
- **Bestiary card:** `docs/bestiary/quest-branch.md#vaultelemental-shock`

### Claim quest-branch-27

- **Claim:** “General counter to any enemy with a long-range attack: as soon as it spots you, retreat behind a door or around a corner so it loses line of sight and can't use its ranged attack; this applies to Warlocks, Scorpios, Wisps and similar spellcasters/archers throughout these floors.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Its zap needs a MAGIC_BOLT line and a spent cooldown; behind a door or corner it must melee.
- **Cites:** `core/…/mobs/Elemental.java:133`, `core/…/mobs/Elemental.java:137`, `core/…/mobs/Elemental.java:143`, `core/…/mobs/Elemental.java:145`, `core/…/mechanics/Ballistica.java:49`
- **Bestiary card:** `docs/bestiary/quest-branch.md#vaultelemental-shock`

## Golem (vault) (`actors.mobs.quest.vault.VaultGolem`)

### Claim quest-branch-28

- **Claim:** “Golems have the highest attack, HP and armor of the normal Dwarven City enemies”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Golem 120 HP, 25-30 damage, DR 0-12 top the City roster (e.g. Monk 70 HP, Elemental 60 HP / 20-25).
- **Cites:** `core/…/mobs/Golem.java:45`, `core/…/mobs/Golem.java:63`, `core/…/mobs/Golem.java:73`, `core/…/mobs/Monk.java:42`, `core/…/mobs/Elemental.java:71`, `core/…/mobs/Elemental.java:85`
- **Bestiary card:** `docs/bestiary/quest-branch.md#vaultgolem`

### Claim quest-branch-29

- **Claim:** “they can't walk through corridors”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** LARGE: cannot path into non-open cells (doorways, corridors).
- **Cites:** `core/…/mobs/Golem.java:55`, `core/…/mobs/Mob.java:580`, `core/…/mobs/Mob.java:581`
- **Bestiary card:** `docs/bestiary/quest-branch.md#vaultgolem`

### Claim quest-branch-30

- **Claim:** “but may teleport to another room while searching for the Hero”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** True for the City golem (Golem.java:195-198), but the vault golem swaps its teleporting Wandering state for the stealth patrol state, so it never self-teleports.
- **Cites:** `core/…/vault/VaultGolem.java:28`, `core/…/vault/VaultGolem.java:32`, `core/…/mobs/Mob.java:1516`, `core/…/mobs/Mob.java:1517`
- **Bestiary card:** `docs/bestiary/quest-branch.md#vaultgolem`

### Claim quest-branch-31

- **Claim:** “and can also teleport the Hero to a nearby tile if the Hero hides in a doorway, so doorway-camping does not fully neutralize them”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** When it cannot walk closer it pulls you to the free cell beside it farthest from where you stood (20-turn cooldown); kept by the vault golem (only WANDERING/INVESTIGATING/SLEEPING are replaced).
- **Cites:** `core/…/mobs/Golem.java:147`, `core/…/mobs/Golem.java:150`, `core/…/mobs/Golem.java:151`, `core/…/mobs/Golem.java:152`, `core/…/mobs/Golem.java:153`, `core/…/mobs/Golem.java:154`, `core/…/mobs/Golem.java:164`, `core/…/mobs/Golem.java:172`, `core/…/mobs/Golem.java:235`, `core/…/mobs/Golem.java:239`, `core/…/mobs/Golem.java:244`
- **Bestiary card:** `docs/bestiary/quest-branch.md#vaultgolem`

### Claim quest-branch-32

- **Claim:** “A community-suggested approach is to get a polearm (pole hammer), find a small single-door room, aggro the Golem and retreat into that room, then kill it with overhead reach-attack swings; keep an escape item ready (invisibility potion, scroll of terror, Lloyd's Beacon, or a teleportation seed) in case you get cornered, since Golem hits are heavy.”
- **Tier:** 3 (UNVERIFIED)
- **Source:** <https://docs.oldmartijntje.nl/Archive/Shattered-Pixel-Dungeon-Strategy-Guide>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Strategy advice, not checkable as a whole. Code caveat: a golem that cannot reach you pulls you next to it every 20 turns while it sees you, so the room plan must include stepping back after each pull; a closed door (SOLID, LOS-blocking) stops both sight and the pull.
- **Cites:** `core/…/mobs/Golem.java:239`, `core/…/mobs/Golem.java:244`, `core/…/mobs/Golem.java:172`
- **Bestiary card:** `docs/bestiary/quest-branch.md#vaultgolem`

## Marsupial rat (vault) (`actors.mobs.quest.vault.VaultRat`)

### Claim quest-branch-33

- **Claim:** “Don't waste resources on a lone Marsupial Rat unless threatened; save consumables for deeper floors. They're only dangerous in numbers, so if a group is chasing you, retreat to a doorway/corridor and kill them one at a time.”
- **Tier:** 3 (UNVERIFIED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Marsupial_rat>
- **Variant:** unknown
- **Version:** older version (source flagged oldVersion)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Resource/positioning advice is not checkable from code; code confirms a rat is weak (8 HP, 1-4 damage). The vault rat is never spawned in v4.0.0.
- **Cites:** `core/…/mobs/Rat.java:36`, `core/…/mobs/Rat.java:56`
- **Bestiary card:** `docs/bestiary/quest-branch.md#vaultrat`

## Captured shaman (`actors.mobs.quest.vault.VaultShaman`)

### Claim quest-branch-34

- **Claim:** “If a Gnoll Shaman spots you at range, break line of sight behind cover immediately to dodge its lightning bolt; retreating behind a door will make it chase into melee range”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Gnoll_shaman>
- **Variant:** unknown
- **Version:** older version (source flagged oldVersion)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** The bolt needs a MAGIC_BOLT line; behind cover it must approach. Current code calls it an earthen bolt (Shaman.java:111), not lightning.
- **Cites:** `core/…/mobs/Shaman.java:73`, `core/…/mobs/Shaman.java:74`, `core/…/mobs/Shaman.java:75`, `core/…/mobs/Shaman.java:93`, `core/…/mobs/Shaman.java:94`, `core/…/mobs/Shaman.java:96`, `core/…/mechanics/Ballistica.java:49`
- **Bestiary card:** `docs/bestiary/quest-branch.md#vaultshaman`

### Claim quest-branch-35

- **Claim:** “which is the safer place to finish it off”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Gnoll_shaman>
- **Variant:** unknown
- **Version:** older version (source flagged oldVersion)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** For the vault variant melee is not safer: its melee is buffed to 5-25 (brute level), versus a 6-15 bolt.
- **Cites:** `core/…/vault/VaultShaman.java:83`, `core/…/vault/VaultShaman.java:84`, `core/…/vault/VaultShaman.java:85`, `core/…/mobs/Shaman.java:125`
- **Bestiary card:** `docs/bestiary/quest-branch.md#vaultshaman`

### Claim quest-branch-36

- **Claim:** “General counter to any enemy with a long-range attack: as soon as it spots you, retreat behind a door or around a corner so it loses line of sight and can't use its ranged attack; this applies to Warlocks, Scorpios, Wisps and similar spellcasters/archers throughout these floors.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Breaking line works; but in the vault its melee (5-25) is as dangerous as the bolt.
- **Cites:** `core/…/mobs/Shaman.java:73`, `core/…/mobs/Shaman.java:74`, `core/…/mobs/Shaman.java:75`, `core/…/mechanics/Ballistica.java:49`, `core/…/levels/Terrain.java:90`
- **Bestiary card:** `docs/bestiary/quest-branch.md#vaultshaman`

## Dwarven skeleton (`actors.mobs.quest.vault.VaultSkeleton`)

### Claim quest-branch-37

- **Claim:** “Skeletons explode on death for damage equal to their normal melee hit, but armor absorption against that explosion is halved compared to normal hits”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Skeleton>
- **Variant:** unknown
- **Version:** older version (source flagged oldVersion)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** The burst is its own 6-12 roll (melee is 2-10), and your DR is applied twice against it (armour is doubled, not halved).
- **Cites:** `core/…/mobs/Skeleton.java:67`, `core/…/mobs/Skeleton.java:81`, `core/…/mobs/Skeleton.java:124`, `core/…/mobs/Skeleton.java:125`
- **Bestiary card:** `docs/bestiary/quest-branch.md#vaultskeleton`

### Claim quest-branch-38

- **Claim:** “finish Skeletons off with a ranged attack from a distance once they're at very low HP so the explosion can't reach you”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Skeleton>
- **Variant:** unknown
- **Version:** older version (source flagged oldVersion)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Only adjacent characters (NEIGHBOURS8) are hit; a kill from 2+ tiles avoids it. Death by falling (Chasm) skips the burst.
- **Cites:** `core/…/mobs/Skeleton.java:75`, `core/…/mobs/Skeleton.java:78`, `core/…/mobs/Skeleton.java:79`, `core/…/mobs/Skeleton.java:80`
- **Bestiary card:** `docs/bestiary/quest-branch.md#vaultskeleton`

### Claim quest-branch-39

- **Claim:** “Aim to have armor that blocks roughly 4+ damage on average before descending into Prison, specifically to survive the death-explosion reliably if you can't kill a Skeleton before it reaches melee range.”
- **Tier:** 3 (UNVERIFIED)
- **Source:** <https://www.youtube.com/watch?v=TE1zJ1Eh7fg>
- **Variant:** unknown
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Gear-threshold advice for Prison; not checkable. Code does show armour counts double against the burst; at vault depth the unscaled 6-12 burst is minor.
- **Cites:** `core/…/mobs/Skeleton.java:125`
- **Bestiary card:** `docs/bestiary/quest-branch.md#vaultskeleton`
