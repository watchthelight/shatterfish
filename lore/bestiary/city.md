---
topic: "Bestiary lore: Dwarven City (depths 16-19)"
tag: v4.0.0
sources:
  - https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies
  - https://pixeldungeon.fandom.com/wiki/Fire_elemental
  - https://tvtropes.org/pmwiki/pmwiki.php/YMMV/ShatteredPixelDungeon
  - https://pixeldungeon.fandom.com/wiki/Game_mechanics/Attacking
  - https://docs.oldmartijntje.nl/Archive/Shattered-Pixel-Dungeon-Strategy-Guide
retrieved: 2026-09-26
tiers:
  tier1: 13
  contradicted: 2
  unverified: 0
claims: 15
---

# Lore: Dwarven City (depths 16-19)

Community claims about these enemies, each graded against the pinned code. Tier 1: the code
confirms it. Tier F: the code contradicts it. Tier 3: not settled by the code. The graded card
is in `docs/bestiary/city.md`; a claim that reached this file is never a mechanic by itself, only its
verdict and the citations are.

## Fire elemental (`actors.mobs.Elemental.FireElemental`)

### Claim city-1

- **Claim:** “Fire Elementals burn the Hero out of water, so standing in water snuffs the fire immediately (though exposed scrolls in the backpack may still burn on the first hit); fighting a Fire Elemental while on water tiles is the recommended defense.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Confirmed that a target in water is never ignited by either its melee or bolt, and existing Burning ends in water. The caveat about scrolls burning on the first hit is wrong: in water no Burning is applied at all, and item burning needs 4+ burn ticks.
- **Cites:** `core/…/mobs/Elemental.java:243`, `core/…/mobs/Elemental.java:251`, `core/…/buffs/Burning.java:99`, `core/…/buffs/Burning.java:122`, `core/…/buffs/Burning.java:179`
- **Bestiary card:** `docs/bestiary/city.md#elemental-fireelemental`

### Claim city-2

- **Claim:** “Fire Elementals take heavy bonus damage from the Chill effect, to the point that a chill/freeze source can one-hit-kill one; conversely, do not use a Wand of Firebolt or a Potion of Liquid Flame on a Fire Elemental, as fire damage does nothing to it and may slightly heal it instead.”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Fire_elemental>
- **Variant:** unknown
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Frost or Chill deals 30-36 of its 60 HP, a big hit but not a one-shot from full. Fire does not heal it: it is immune to Burning and halves Wand of Fireblast; nothing in the code heals a FIERY mob from fire.
- **Cites:** `core/…/mobs/Elemental.java:71`, `core/…/mobs/Elemental.java:191`, `core/…/mobs/Elemental.java:193`, `core/…/mobs/Elemental.java:237`, `core/…/mobs/Elemental.java:238`, `core/…/actors/Char.java:1423`, `core/…/actors/Char.java:1424`
- **Bestiary card:** `docs/bestiary/city.md#elemental-fireelemental`

### Claim city-3

- **Claim:** “General terrain rule for the three City Elementals: stand on water against Fire Elementals to extinguish burning, but stay off water against Frost and Shock Elementals, since water amplifies their chill duration and their bonus electric damage respectively.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** For the fire elemental the water rule is confirmed.
- **Cites:** `core/…/mobs/Elemental.java:243`, `core/…/mobs/Elemental.java:251`
- **Bestiary card:** `docs/bestiary/city.md#elemental-fireelemental`

## Frost elemental (`actors.mobs.Elemental.FrostElemental`)

### Claim city-4

- **Claim:** “Frost Elementals chill the target for 3 turns on a ranged hit (5 turns if the target is standing in water) and have a 33% chance to chill on their melee hit as well; because water extends the chill duration, the community strategy is the opposite of the Fire Elemental fight — avoid fighting a Frost Elemental while standing in water.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Bolt: +3 turns of Chill, +5 in water. Melee: 1/3 chance, and always when the target stands in water, which the claim omits. Avoiding water follows.
- **Cites:** `core/…/mobs/Elemental.java:495`, `core/…/mobs/Elemental.java:503`, `core/…/blobs/Freezing.java:75`
- **Bestiary card:** `docs/bestiary/city.md#elemental-frostelemental`

### Claim city-5

- **Claim:** “Frost Elementals take heavy bonus damage from fire-based effects, so a Wand of Firebolt, Potion of Liquid Flame, or similar fire damage is the recommended counter.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Burning is in its harmfulBuffs: any Burning application is refused and deals 30-36 damage. Wand of Fireblast and fire blobs apply Burning.
- **Cites:** `core/…/mobs/Elemental.java:191`, `core/…/mobs/Elemental.java:193`, `core/…/mobs/Elemental.java:490`, `core/…/wands/WandOfFireblast.java:141`, `core/…/blobs/Fire.java:102`
- **Bestiary card:** `docs/bestiary/city.md#elemental-frostelemental`

### Claim city-6

- **Claim:** “General terrain rule for the three City Elementals: stand on water against Fire Elementals to extinguish burning, but stay off water against Frost and Shock Elementals, since water amplifies their chill duration and their bonus electric damage respectively.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** For the frost elemental: in water every melee hit freezes and each freeze adds 5 turns of Chill instead of 3.
- **Cites:** `core/…/mobs/Elemental.java:495`, `core/…/blobs/Freezing.java:75`
- **Bestiary card:** `docs/bestiary/city.md#elemental-frostelemental`

## Shock elemental (`actors.mobs.Elemental.ShockElemental`)

### Claim city-7

- **Claim:** “Shock Elementals are an uncommon elemental variant whose ranged attack blinds the target for 5 turns, and whose melee attack also damages nearby creatures for 40% of the hit and deals 40% extra damage if the target being hit is standing in water; avoid fighting them while standing in water for the same reason as Frost Elementals.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Bolt: Blindness for DURATION/2 = 5 turns. Melee: arcs deal 40% of the hit to other characters within 2 tiles of the victim, and the victim is also hit only when standing in water (an extra 40%).
- **Cites:** `core/…/mobs/Elemental.java:523`, `core/…/mobs/Elemental.java:525`, `core/…/mobs/Elemental.java:526`, `core/…/mobs/Elemental.java:530`, `core/…/mobs/Elemental.java:551`, `core/…/buffs/Blindness.java:29`
- **Bestiary card:** `docs/bestiary/city.md#elemental-shockelemental`

### Claim city-8

- **Claim:** “General terrain rule for the three City Elementals: stand on water against Fire Elementals to extinguish burning, but stay off water against Frost and Shock Elementals, since water amplifies their chill duration and their bonus electric damage respectively.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** For the shock elemental: water makes the melee arc hit you as well, and arcs from characters in water chain 2 tiles instead of 1.
- **Cites:** `core/…/mobs/Elemental.java:525`, `core/…/mobs/Elemental.java:526`, `core/…/enchantments/Shocking.java:102`
- **Bestiary card:** `docs/bestiary/city.md#elemental-shockelemental`

## Dwarf warlock (`actors.mobs.Warlock`)

### Claim city-9

- **Claim:** “Caves Warlocks can temporarily degrade the enchantment/upgrade level of your equipment as an attack effect, on top of the class-specific soul-marking mechanic — treat them as a threat to your gear, not just your HP.”
- **Tier:** 1 (TIER1)
- **Source:** <https://tvtropes.org/pmwiki/pmwiki.php/YMMV/ShatteredPixelDungeon>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Degrade on the bolt is confirmed (50% on a hit against the hero, 30 turns). Two details are wrong: warlocks spawn in the Dwarven City (depths 16-19), not the caves, and "soul-marking" is the Mage's Warlock subclass, not an enemy mechanic.
- **Cites:** `core/…/mobs/Warlock.java:112`, `core/…/mobs/Warlock.java:113`, `core/…/buffs/Degrade.java:32`, `core/…/mobs/MobSpawner.java:158`, `core/…/mobs/MobSpawner.java:163`
- **Bestiary card:** `docs/bestiary/city.md#warlock`

### Claim city-10

- **Claim:** “Dwarf Warlocks are dangerous ranged fighters (compared to the DM-100 miniboss in threat) whose spell has a chance to inflict Degrade, temporarily reducing the effective upgrade level of the Hero's equipment; this makes them a priority ranged threat in the City/Metropolis region rather than something to trade hits with at range.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Code confirms a ranged magic bolt with a 50% chance to Degrade the hero on hit. The DM-100 threat comparison is opinion.
- **Cites:** `core/…/mobs/Warlock.java:80`, `core/…/mobs/Warlock.java:110`, `core/…/mobs/Warlock.java:112`, `core/…/mobs/Warlock.java:113`, `core/…/mobs/Warlock.java:117`, `core/…/mobs/Warlock.java:120`
- **Bestiary card:** `docs/bestiary/city.md#warlock`

### Claim city-11

- **Claim:** “Physical attacks that would normally require an accuracy check auto-hit as a surprise attack when the target is unaware (e.g. it just walked into view through a door); using a door-luring loop (stand off the door, let the door close, let the enemy open it and thereby come into your view for the first time) lets you land a guaranteed surprise attack on a Warlock or similar enemy before it can use its ranged spell.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Game_mechanics/Attacking>
- **Variant:** unknown
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Mob FOV and enemySeen are computed at the start of the mob's own act, so right after it steps through a door it has not yet seen you, and the hero's next attack is a surprise (defenseSkill 0). Requires a weapon that allows surprise attacks.
- **Cites:** `core/…/actors/Char.java:202`, `core/…/mobs/Mob.java:290`, `core/…/mobs/Mob.java:1327`, `core/…/mobs/Mob.java:875`, `core/…/mobs/Mob.java:876`, `core/…/mobs/Mob.java:796`, `core/…/mobs/Mob.java:801`
- **Bestiary card:** `docs/bestiary/city.md#warlock`

### Claim city-12

- **Claim:** “General counter to any enemy with a long-range attack: as soon as it spots you, retreat behind a door or around a corner so it loses line of sight and can't use its ranged attack; this applies to Warlocks, Scorpios, Wisps and similar spellcasters/archers throughout these floors.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** For Warlocks: the bolt needs a MAGIC_BOLT line ending on your cell; closed doors are solid and LOS-blocking, and a hunter that loses sight walks to your last known cell. It will still walk after you.
- **Cites:** `core/…/mobs/Warlock.java:80`, `core/…/mobs/Warlock.java:86`, `core/…/mechanics/Ballistica.java:49`, `core/…/mechanics/Ballistica.java:130`, `core/…/levels/Terrain.java:90`, `core/…/mobs/Mob.java:1342`, `core/…/mobs/Mob.java:1401`, `core/…/mobs/Mob.java:1403`
- **Bestiary card:** `docs/bestiary/city.md#warlock`

## Senior monk (`actors.mobs.Senior`)

### Claim city-13

- **Claim:** “The Golden Monk is a rare Dwarf Monk variant that hits slightly harder and builds its Focus (special-attack charge) twice as fast as a normal Monk, and always drops a pasty (food item) on death; treat it as a higher-priority, faster-charging version of the regular Monk fight.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Damage 16-25 vs the Monk's 12-25; loot = Pasty with lootChance 1. "Focus twice as fast" is true only for movement: each step cuts the cooldown by 3.33 instead of 1.67; the time-based part and the 6-7 turn reset are the same as a Monk's.
- **Cites:** `core/…/mobs/Senior.java:33`, `core/…/mobs/Senior.java:34`, `core/…/mobs/Senior.java:41`, `core/…/mobs/Senior.java:47`, `core/…/mobs/Monk.java:56`, `core/…/mobs/Monk.java:94`, `core/…/mobs/Monk.java:102`, `core/…/mobs/Monk.java:124`
- **Bestiary card:** `docs/bestiary/city.md#senior`

## Golem (`actors.mobs.Golem`)

### Claim city-14

- **Claim:** “Golems have the highest attack, HP and armor of the normal Dwarven City enemies; they can't walk through corridors but may teleport to another room while searching for the Hero, and can also teleport the Hero to a nearby tile if the Hero hides in a doorway, so doorway-camping does not fully neutralize them.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Confirmed: HP 120, DR 0-12 and damage 25-30 are the highest of the regular city mobs (Monk accuracy 30 beats its 28). LARGE mobs cannot enter non-openSpace cells; wandering golems self-teleport when they cannot reach their target; hunting golems pull the hero to a cell next to themselves when they see but cannot reach them.
- **Cites:** `core/…/mobs/Golem.java:45`, `core/…/mobs/Golem.java:63`, `core/…/mobs/Golem.java:68`, `core/…/mobs/Golem.java:73`, `core/…/mobs/Golem.java:55`, `core/…/mobs/Golem.java:195`, `core/…/mobs/Golem.java:197`, `core/…/mobs/Golem.java:239`, `core/…/mobs/Golem.java:164`, `core/…/mobs/Mob.java:580`
- **Bestiary card:** `docs/bestiary/city.md#golem`

### Claim city-15

- **Claim:** “A community-suggested approach is to get a polearm (pole hammer), find a small single-door room, aggro the Golem and retreat into that room, then kill it with overhead reach-attack swings; keep an escape item ready (invisibility potion, scroll of terror, Lloyd's Beacon, or a teleportation seed) in case you get cornered, since Golem hits are heavy.”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://docs.oldmartijntje.nl/Archive/Shattered-Pixel-Dungeon-Strategy-Guide>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Standing in a doorway with a reach weapon does not keep you safe: when the golem sees you and cannot step closer it pulls you next to itself at once (enemyTeleCooldown 20 between pulls). Keeping escape items is reasonable advice.
- **Cites:** `core/…/mobs/Golem.java:212`, `core/…/mobs/Golem.java:235`, `core/…/mobs/Golem.java:239`, `core/…/mobs/Golem.java:244`, `core/…/mobs/Golem.java:164`, `core/…/mobs/Golem.java:172`
- **Bestiary card:** `docs/bestiary/city.md#golem`
