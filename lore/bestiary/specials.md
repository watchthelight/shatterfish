---
topic: "Bestiary lore: Anywhere: special rooms, traps and summons"
tag: v4.0.0
sources:
  - https://pixeldungeon.fandom.com/wiki/Swarm_of_flies
  - https://pixeldungeon.fandom.com/wiki/Animated_statue
  - https://pixeldungeon.fandom.com/wiki/Wraith
  - https://pixeldungeon.fandom.com/wiki/User_blog:108.184.82.95/How_to_kill_Wraiths
  - https://pixeldungeon.fandom.com/wiki/Giant_piranha
  - https://spd-huntress-guide.pages.dev/guide
  - https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Special_Rooms
  - https://pixeldungeon.fandom.com/wiki/Honeypot
  - https://steamcommunity.com/sharedfiles/filedetails/?id=2786737188
  - https://steamcommunity.com/sharedfiles/filedetails/?id=3225596609
retrieved: 2026-09-26
tiers:
  tier1: 16
  contradicted: 8
  unverified: 0
claims: 24
---

# Lore: Anywhere: special rooms, traps and summons

Community claims about these enemies, each graded against the pinned code. Tier 1: the code
confirms it. Tier F: the code contradicts it. Tier 3: not settled by the code. The graded card
is in `docs/bestiary/specials.md`; a claim that reached this file is never a mechanic by itself, only its
verdict and the citations are.

## Swarm of flies (`actors.mobs.Swarm`)

### Claim specials-1

- **Claim:** “Lure a Swarm into a hallway before fighting so it can't surround you, and rely on armor rather than your weapon — melee/physical damage causes it to split in two (each half HP) if there is empty cardinal space, so alternate hitting and retreating, and mop up flies that can no longer split.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Swarm_of_flies>
- **Variant:** unknown
- **Version:** old wiki page (flagged oldVersion)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Split needs HP &gt;= damage+2 and a free non-solid cardinal cell; the clone gets (HP-damage)/2 and the original loses the same, so each half is about half the remaining HP. Accuracy 10 and damage 1-4 make armor the key stat. A fly with HP &lt; damage+2 cannot split.
- **Cites:** `core/…/mobs/Swarm.java:88-120`, `core/…/mobs/Swarm.java:83-85`, `core/…/mobs/Swarm.java:122-125`
- **Bestiary card:** `docs/bestiary/specials.md#swarm`

### Claim specials-2

- **Claim:** “Avoid non-physical damage sources (debuffs, magic, wands) on a Swarm if you want it to keep splitting for extra Potion of Healing drops — those damage types don't trigger the split.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Swarm_of_flies>
- **Variant:** unknown
- **Version:** old wiki page (flagged oldVersion)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Confirmed that wand/debuff damage bypasses defenseProc and so never splits. Farming caveat: each clone's drop chance is 1/(6\*(generation+1)) times (5 - SWARM_HP drops)/5, so returns shrink fast and stop after 5 drops.
- **Cites:** `core/…/mobs/Swarm.java:88`, `core/…/wands/WandOfMagicMissile.java:62`, `core/…/mobs/Swarm.java:145-155`
- **Bestiary card:** `docs/bestiary/specials.md#swarm`

## Animated statue (`actors.mobs.Statue`)

### Claim specials-3

- **Claim:** “A statue stays inert and won't attack until you first deal damage to it or apply a debuff to it (or trip the Guardian Trap that spawns it); this generally can't happen until you actually try to take the loot behind the locked door, unless you proactively snipe it with a Wand of Disintegration or a Projecting-enchanted thrown weapon from outside its detection.”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Animated_statue>
- **Variant:** unknown
- **Version:** old wiki page (flagged oldVersion)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Inert-until-damaged-or-debuffed is correct. But in v4.0.0 the statue vault holds no loot besides the statue itself (and nothing wakes it when you walk in), and Guardian-trap guardians are a different subclass that spawn already WANDERING toward you, not inert.
- **Cites:** `core/…/mobs/Statue.java:117-136`, `core/…/special/StatueRoom.java:43-73`, `core/…/traps/GuardianTrap.java:59-67`
- **Bestiary card:** `docs/bestiary/specials.md#statue`

### Claim specials-4

- **Claim:** “Animated Statues in statue-vault rooms carry enchanted (and sometimes armored) weapons and are more dangerous than the plain, unenchanted-weapon Summoned Guardians spawned by Guardian Traps elsewhere — size up gear accordingly before opening a statue vault.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Animated_statue>
- **Variant:** unknown
- **Version:** old wiki page (flagged oldVersion)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Vault statues get a random enchantment (never a curse) and 10% are Armored Statues with glyphed armor; Guardians get a +0 unenchanted weapon. HP formula (15+5\*depth) is the same for both.
- **Cites:** `core/…/mobs/Statue.java:63-72`, `core/…/mobs/Statue.java:206-217`, `core/…/traps/GuardianTrap.java:83-89`
- **Bestiary card:** `docs/bestiary/specials.md#statue`

## Armored statue (`actors.mobs.ArmoredStatue`)

### Claim specials-5

- **Claim:** “Animated Statues in statue-vault rooms carry enchanted (and sometimes armored) weapons and are more dangerous than the plain, unenchanted-weapon Summoned Guardians spawned by Guardian Traps elsewhere — size up gear accordingly before opening a statue vault.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Animated_statue>
- **Variant:** unknown
- **Version:** old wiki page (flagged oldVersion)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** The 'sometimes armored' part: 1 in 10 vault statues (more with Rat Skull) is an Armored Statue with double HP and random glyphed armor.
- **Cites:** `core/…/mobs/Statue.java:206-217`, `core/…/mobs/ArmoredStatue.java:46-57`
- **Bestiary card:** `docs/bestiary/specials.md#armoredstatue`

## Summoned guardian (`levels.traps.GuardianTrap.Guardian`)

### Claim specials-6

- **Claim:** “A statue stays inert and won't attack until you first deal damage to it or apply a debuff to it (or trip the Guardian Trap that spawns it); this generally can't happen until you actually try to take the loot behind the locked door, unless you proactively snipe it with a Wand of Disintegration or a Projecting-enchanted thrown weapon from outside its detection.”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Animated_statue>
- **Variant:** unknown
- **Version:** old wiki page (flagged oldVersion)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Guardians are never inert: they spawn WANDERING and are beckoned to the hero's position when the trap fires.
- **Cites:** `core/…/traps/GuardianTrap.java:59-67`, `core/…/traps/GuardianTrap.java:74-79`
- **Bestiary card:** `docs/bestiary/specials.md#guardian`

### Claim specials-7

- **Claim:** “Animated Statues in statue-vault rooms carry enchanted (and sometimes armored) weapons and are more dangerous than the plain, unenchanted-weapon Summoned Guardians spawned by Guardian Traps elsewhere — size up gear accordingly before opening a statue vault.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Animated_statue>
- **Variant:** unknown
- **Version:** old wiki page (flagged oldVersion)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Guardian weapons are +0 and unenchanted (enchant(null), level(0)).
- **Cites:** `core/…/traps/GuardianTrap.java:83-89`
- **Bestiary card:** `docs/bestiary/specials.md#guardian`

## Wraith (`actors.mobs.Wraith`)

### Claim specials-8

- **Claim:** “Before disturbing a Wraith-spawning tomb, stand with your back to a wall, statue, or room corner — wraiths spawn only in the four cardinal tiles around you, so blocking some of those tiles with terrain caps the number that can appear at once to 3 or fewer.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Wraith>
- **Variant:** unknown
- **Version:** old wiki page (flagged oldVersion)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Tomb wraiths spawn only on the hero's 4 cardinal neighbours that are not solid and unoccupied (allowAdjacent=false), so walls cap the count. Applies to tombs only; haunted skeletons spawn one wraith at the heap.
- **Cites:** `core/…/items/Heap.java:86-88`, `core/…/mobs/Wraith.java:106-110`, `core/…/mobs/Wraith.java:120-138`
- **Bestiary card:** `docs/bestiary/specials.md#wraith`

### Claim specials-9

- **Claim:** “Wraiths have very high evasion, so melee attacks miss often; they're described as weak to magic-type damage (including fire and poison), and this source claims a Wraith only has 1 HP so any connecting hit — including a guaranteed surprise attack performed by retreating behind a door — kills it outright. Treat the '1 HP' figure with caution since it's stated without a version and may not reflect the current build's stats.”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/User_blog:108.184.82.95/How_to_kill_Wraiths>
- **Variant:** unknown
- **Version:** old wiki page (flagged oldVersion)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** 1 HP, very high evasion (5x accuracy), and a guaranteed surprise hit all confirmed in v4.0.0; fire works (no Burning immunity). Poison is wrong: wraiths are INORGANIC and immune to Poison and ToxicGas.
- **Cites:** `core/…/mobs/Wraith.java:49`, `core/…/mobs/Wraith.java:85-88`, `core/…/mobs/Wraith.java:57`, `core/…/actors/Char.java:1421-1422`
- **Bestiary card:** `docs/bestiary/specials.md#wraith`

## Tormented spirit (`actors.mobs.TormentedSpirit`)

### Claim specials-10

- **Claim:** “Before disturbing a Wraith-spawning tomb, stand with your back to a wall, statue, or room corner — wraiths spawn only in the four cardinal tiles around you, so blocking some of those tiles with terrain caps the number that can appear at once to 3 or fewer.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Wraith>
- **Variant:** unknown
- **Version:** old wiki page (flagged oldVersion)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Applies to tormented spirits too: tomb spawns use the same spawnAround and 1% of them are Tormented Spirits.
- **Cites:** `core/…/mobs/Wraith.java:143-150`
- **Bestiary card:** `docs/bestiary/specials.md#tormentedspirit`

## Giant piranha (`actors.mobs.Piranha`)

### Claim specials-11

- **Claim:** “Avoid melee entirely — Giant Piranhas hit hard in the water. Ranged weapons work but their high HP/evasion make it slow; Fishing Spears specifically get greatly increased accuracy and damage against piranhas. The best plan is often to just avoid the fight: every flooded-vault floor spawns a Potion of Invisibility elsewhere on the level specifically so you can slip past them to the treasure.”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Giant_piranha>
- **Variant:** unknown
- **Version:** old wiki page (flagged oldVersion)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Invisibility potion per pool-room floor and high HP/evasion confirmed. Fishing Spear gets only a damage bonus (at least half the piranha's current HP); there is no accuracy bonus in v4.0.0.
- **Cites:** `core/…/missiles/FishingSpear.java:39-45`, `core/…/special/PoolRoom.java:91`, `core/…/mobs/Piranha.java:65-66`
- **Bestiary card:** `docs/bestiary/specials.md#piranha`

### Claim specials-12

- **Claim:** “As Huntress, prop the piranha-room door open and kill them with a thrown boomerang from outside the water — this avoids spending an Invisibility potion; lightning-type damage hits multiple grouped piranhas at once if you can lure them together.”
- **Tier:** 1 (TIER1)
- **Source:** <https://spd-huntress-guide.pages.dev/guide>
- **Variant:** unknown
- **Version:** unspecified (current-era source)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Attacking from a cell with no water path to the piranha leaves it unable to see or reach you. Lightning arcs 2 tiles from targets in water and deals full damage to all when the main target is in water. The Huntress's current kit is the Spirit Bow (the boomerang is a generic thrown weapon, not her starter); propping the door is irrelevant because piranhas never leave water.
- **Cites:** `core/…/mobs/Piranha.java:193-203`, `core/…/wands/WandOfLightning.java:81-84`, `core/…/wands/WandOfLightning.java:143-160`
- **Bestiary card:** `docs/bestiary/specials.md#piranha`

### Claim specials-13

- **Claim:** “The Flooded Vault special room is guarded by 3 Giant Piranhas. Piranhas die instantly if kept out of water for a single turn, so luring them onto dry land (or draining the pool) kills them outright; every floor containing a flooded vault also generates a Potion of Invisibility elsewhere on that floor, letting you sneak past the piranhas entirely to reach the vault's chest instead of fighting them in the water where they have the advantage.”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Special_Rooms>
- **Variant:** spd
- **Version:** unspecified (current-era source)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Three piranhas and the Invisibility potion are confirmed, and a piranha on land dies at once. But you cannot lure one onto land: its pathing only uses water cells. Only knockback, teleport or terrain change puts it on land, and 1 in 50 is a Phantom Piranha that teleports back to water instead of dying.
- **Cites:** `core/…/special/PoolRoom.java:39`, `core/…/special/PoolRoom.java:91`, `core/…/mobs/Piranha.java:132-146`, `core/…/levels/Level.java:1219-1221`
- **Bestiary card:** `docs/bestiary/specials.md#piranha`

## Phantom piranha (`actors.mobs.PhantomPiranha`)

### Claim specials-14

- **Claim:** “The Flooded Vault special room is guarded by 3 Giant Piranhas. Piranhas die instantly if kept out of water for a single turn, so luring them onto dry land (or draining the pool) kills them outright; every floor containing a flooded vault also generates a Potion of Invisibility elsewhere on that floor, letting you sneak past the piranhas entirely to reach the vault's chest instead of fighting them in the water where they have the advantage.”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Special_Rooms>
- **Variant:** spd
- **Version:** unspecified (current-era source)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Any flooded-vault piranha can be a Phantom Piranha (1/50); it does not die on land but teleports to water.
- **Cites:** `core/…/mobs/PhantomPiranha.java:87-92`, `core/…/mobs/Piranha.java:206-213`
- **Bestiary card:** `docs/bestiary/specials.md#phantompiranha`

## Golden bee (`actors.mobs.Bee`)

### Claim specials-15

- **Claim:** “Shattering/throwing a Honeypot spawns a Golden Bee that attacks the first thing in its field of view — including you, if no other enemy is nearby — so only pop a honeypot when an enemy (ideally a boss) is already in the bee's sight, not preemptively.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Honeypot>
- **Variant:** unknown
- **Version:** unspecified (current-era source)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Gist confirmed: with no other non-neutral mob within 3 tiles of the pot, it targets the hero if the hero is within 3 of the pot. Correction: the choice is by distance to the pot (3 tiles), not 'first thing in its field of view'.
- **Cites:** `core/…/mobs/Bee.java:174-202`
- **Bestiary card:** `docs/bestiary/specials.md#bee`

### Claim specials-16

- **Claim:** “If a Golden Bee turns hostile toward you by mistake, throwing a Potion of Honeyed Healing at it placates it into a friendly, following ally instead of an enemy.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Honeypot>
- **Variant:** unknown
- **Version:** unspecified (current-era source)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Confirmed, but the item is the Elixir of Honeyed Healing, not a potion; the bee becomes an ALLY and forgets its pot.
- **Cites:** `core/…/elixirs/ElixirOfHoneyedHealing.java:59-66`
- **Bestiary card:** `docs/bestiary/specials.md#bee`

### Claim specials-17

- **Claim:** “Crazy Bandits can steal and shatter a Honeypot from your inventory, which releases a hostile Golden Bee, so don't assume a Bandit encounter is contained to just the Bandit if you're carrying honeypots.”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://steamcommunity.com/sharedfiles/filedetails/?id=2786737188>
- **Variant:** unknown
- **Version:** unspecified (current-era source)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Bandits (Thief subclass) do shatter a stolen honeypot, but they shatter it with themselves as the pot holder, so the bee targets the bandit, not the hero. It only threatens the hero later, around the dropped pot, if the bandit dies. Claim filed under Crazy Bandit; graded here for the bee behaviour.
- **Cites:** `core/…/mobs/Thief.java:146-168`, `core/…/mobs/Bee.java:150-152`
- **Bestiary card:** `docs/bestiary/specials.md#bee`

## Mimic (`actors.mobs.Mimic`)

### Claim specials-18

- **Claim:** “Examining a suspicious chest will explicitly tell you if something is off — the safest ID method. If you'd rather not examine, hit the chest first with a ranged weapon, wand or thrown item from a distance; a Mimic will reveal itself and take damage, while a real chest is unaffected.”
- **Tier:** 1 (TIER1)
- **Source:** <https://steamcommunity.com/sharedfiles/filedetails/?id=3225596609>
- **Variant:** unknown
- **Version:** unspecified (current-era source)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Examine shows the 'feels off' hint for every chest-shaped mimic unless you carry a Mimic Tooth (then no hint). A ranged or wand hit reveals it (damage calls stopHiding); on a real chest a thrown item just lands.
- **Cites:** `core/…/mobs/Mimic.java:120-131`, `core/…/trinkets/MimicTooth.java:64-66`, `core/…/mobs/Mimic.java:194-201`
- **Bestiary card:** `docs/bestiary/specials.md#mimic`

### Claim specials-19

- **Claim:** “A Mimic's opening/first hit reportedly deals unusually high damage, so keep distance and fight it with ranged weapons once identified rather than walking up to melee it blind; luring it over your own traps also works. Mimics (like Animated Statues) are optional encounters — if unprepared, you can just leave the chest and move on.”
- **Tier:** 1 (TIER1)
- **Source:** <https://steamcommunity.com/sharedfiles/filedetails/?id=3225596609>
- **Variant:** unknown
- **Version:** unspecified (current-era source)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Hidden bite: infinite accuracy and fixed maximum damage 2+2L (crystal mimics steal an item instead of dealing bonus damage). Mimics stay PASSIVE and never move until disturbed, so they are optional. The 'lure it over traps' part was not checked.
- **Cites:** `core/…/mobs/Mimic.java:229-236`, `core/…/mobs/Mimic.java:251-257`, `core/…/mobs/Mimic.java:63-64`
- **Bestiary card:** `docs/bestiary/specials.md#mimic`

## Golden mimic (`actors.mobs.GoldenMimic`)

### Claim specials-20

- **Claim:** “Examining a suspicious chest will explicitly tell you if something is off — the safest ID method. If you'd rather not examine, hit the chest first with a ranged weapon, wand or thrown item from a distance; a Mimic will reveal itself and take damage, while a real chest is unaffected.”
- **Tier:** 1 (TIER1)
- **Source:** <https://steamcommunity.com/sharedfiles/filedetails/?id=3225596609>
- **Variant:** unknown
- **Version:** unspecified (current-era source)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Examine shows the 'feels off' hint for every chest-shaped mimic unless you carry a Mimic Tooth (then no hint). A ranged or wand hit reveals it (damage calls stopHiding); on a real chest a thrown item just lands.
- **Cites:** `core/…/mobs/Mimic.java:120-131`, `core/…/trinkets/MimicTooth.java:64-66`, `core/…/mobs/Mimic.java:194-201`
- **Bestiary card:** `docs/bestiary/specials.md#goldenmimic`

### Claim specials-21

- **Claim:** “A Mimic's opening/first hit reportedly deals unusually high damage, so keep distance and fight it with ranged weapons once identified rather than walking up to melee it blind; luring it over your own traps also works. Mimics (like Animated Statues) are optional encounters — if unprepared, you can just leave the chest and move on.”
- **Tier:** 1 (TIER1)
- **Source:** <https://steamcommunity.com/sharedfiles/filedetails/?id=3225596609>
- **Variant:** unknown
- **Version:** unspecified (current-era source)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Hidden bite: infinite accuracy and fixed maximum damage 2+2L (crystal mimics steal an item instead of dealing bonus damage). Mimics stay PASSIVE and never move until disturbed, so they are optional. The 'lure it over traps' part was not checked.
- **Cites:** `core/…/mobs/Mimic.java:229-236`, `core/…/mobs/Mimic.java:251-257`, `core/…/mobs/Mimic.java:63-64`
- **Bestiary card:** `docs/bestiary/specials.md#goldenmimic`

## Crystal mimic (`actors.mobs.CrystalMimic`)

### Claim specials-22

- **Claim:** “Examining a suspicious chest will explicitly tell you if something is off — the safest ID method. If you'd rather not examine, hit the chest first with a ranged weapon, wand or thrown item from a distance; a Mimic will reveal itself and take damage, while a real chest is unaffected.”
- **Tier:** 1 (TIER1)
- **Source:** <https://steamcommunity.com/sharedfiles/filedetails/?id=3225596609>
- **Variant:** unknown
- **Version:** unspecified (current-era source)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Examine shows the 'feels off' hint for every chest-shaped mimic unless you carry a Mimic Tooth (then no hint). A ranged or wand hit reveals it (damage calls stopHiding); on a real chest a thrown item just lands.
- **Cites:** `core/…/mobs/Mimic.java:120-131`, `core/…/trinkets/MimicTooth.java:64-66`, `core/…/mobs/Mimic.java:194-201`
- **Bestiary card:** `docs/bestiary/specials.md#crystalmimic`

### Claim specials-23

- **Claim:** “A Mimic's opening/first hit reportedly deals unusually high damage, so keep distance and fight it with ranged weapons once identified rather than walking up to melee it blind; luring it over your own traps also works. Mimics (like Animated Statues) are optional encounters — if unprepared, you can just leave the chest and move on.”
- **Tier:** 1 (TIER1)
- **Source:** <https://steamcommunity.com/sharedfiles/filedetails/?id=3225596609>
- **Variant:** unknown
- **Version:** unspecified (current-era source)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Hidden bite: infinite accuracy and fixed maximum damage 2+2L (crystal mimics steal an item instead of dealing bonus damage). Mimics stay PASSIVE and never move until disturbed, so they are optional. The 'lure it over traps' part was not checked.
- **Cites:** `core/…/mobs/Mimic.java:229-236`, `core/…/mobs/Mimic.java:251-257`, `core/…/mobs/Mimic.java:63-64`
- **Bestiary card:** `docs/bestiary/specials.md#crystalmimic`

## Ebony mimic (`actors.mobs.EbonyMimic`)

### Claim specials-24

- **Claim:** “Examining a suspicious chest will explicitly tell you if something is off — the safest ID method. If you'd rather not examine, hit the chest first with a ranged weapon, wand or thrown item from a distance; a Mimic will reveal itself and take damage, while a real chest is unaffected.”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://steamcommunity.com/sharedfiles/filedetails/?id=3225596609>
- **Variant:** unknown
- **Version:** unspecified (current-era source)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Does not apply to ebony mimics: they look like a faint outline rather than a chest, have no 'feels off' hint, and exist only when you carry a Mimic Tooth. A ranged hit still reveals them.
- **Cites:** `core/…/mobs/EbonyMimic.java:59-66`, `core/…/levels/RegularLevel.java:650-675`
- **Bestiary card:** `docs/bestiary/specials.md#ebonymimic`
