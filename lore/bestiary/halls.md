---
topic: "Bestiary lore: Demon Halls (depths 21-24)"
tag: v4.0.0
sources:
  - https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies
retrieved: 2026-09-26
tiers:
  tier1: 6
  contradicted: 2
  unverified: 0
claims: 8
---

# Lore: Demon Halls (depths 21-24)

Community claims about these enemies, each graded against the pinned code. Tier 1: the code
confirms it. Tier F: the code contradicts it. Tier 3: not settled by the code. The graded card
is in `docs/bestiary/halls.md`; a claim that reached this file is never a mechanic by itself, only its
verdict and the citations are.

## Succubus (`actors.mobs.Succubus`)

### Claim halls-1

- **Claim:** “Succubi will teleport directly to the Hero the moment they notice them if they are at least 3 tiles away, then fight in melee and attempt to Charm the Hero; because they close distance instantly, you cannot simply keep range against them the way you can against other City ranged casters.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Code confirms: it blinks when its target cell is visible and more than 2 tiles away (i.e. 3+), then melees and charms on 1/3 of hits. Caveats: it is not literally 'the moment they notice'. The blink happens on its hunting move, has a 4-6 walking-step cooldown, is blocked by roots, and lands beside you along a projectile line (cut short by walls or bodies).
- **Cites:** `core/…/mobs/Succubus.java:112`, `core/…/mobs/Succubus.java:114`, `core/…/mobs/Succubus.java:115`, `core/…/mobs/Succubus.java:158`, `core/…/mobs/Succubus.java:97`, `core/…/mobs/Succubus.java:98`
- **Bestiary card:** `docs/bestiary/halls.md#succubus`

## Evil eye (`actors.mobs.Eye`)

### Claim halls-2

- **Claim:** “General counter to any enemy with a long-range attack: as soon as it spots you, retreat behind a door or around a corner so it loses line of sight and can't use its ranged attack; this applies to Warlocks, Scorpios, Wisps and similar spellcasters/archers throughout these floors.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Applied to the Evil Eye, which is a long-ranged Halls caster though the claim doesn't name it. Breaking sight before it charges prevents the charge (canAttack needs the target in FOV). Breaking sight during a charge makes it fire at your old cell instead of re-aiming, which is the only reliable dodge.
- **Cites:** `core/…/mobs/Eye.java:96`, `core/…/mobs/Eye.java:97`, `core/…/mobs/Eye.java:103`, `core/…/mobs/Eye.java:279`
- **Bestiary card:** `docs/bestiary/halls.md#eye`

## Scorpio (`actors.mobs.Scorpio`)

### Claim halls-3

- **Claim:** “Scorpios shoot a ranged spike attack and behave similarly to the Gnoll Trickster: they have no melee attack and will try to flee if the Hero gets adjacent. Their ranged hits deal physical damage (reducible by armor) and have a chance to inflict Cripple. They are described as very manageable if you fight from a doorway, though repeated door use can eventually break the door.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Core mechanics confirmed: no attack when adjacent, retreats (getFurther) while hunting, the shot is a normal attack so armor DR applies, and 50% Cripple. It backs away whenever it sees you, not only when you are adjacent. The door caveat is wrong: doors have no durability and only toggle DOOR/OPEN_DOOR (core/…/levels/features/Door.java:36,54). They are removed only by fire or destroy. See the split entry below.
- **Cites:** `core/…/mobs/Scorpio.java:74`, `core/…/mobs/Scorpio.java:75`, `core/…/mobs/Scorpio.java:81`, `core/…/mobs/Scorpio.java:82`, `core/…/mobs/Scorpio.java:90`, `core/…/mobs/Scorpio.java:91`, `core/…/actors/Char.java:388`, `core/…/actors/Char.java:390`
- **Bestiary card:** `docs/bestiary/halls.md#scorpio`

### Claim halls-4

- **Claim:** “Split from the claim above: 'repeated door use can eventually break the door.'”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Doors only switch between DOOR and OPEN_DOOR. There is no use counter. A door is removed only when flammable terrain is destroyed (fire, the Eye/Yog beams), which leaves EMBERS.
- **Cites:** `core/…/features/Door.java:36`, `core/…/features/Door.java:54`, `core/…/levels/Level.java:928`, `core/…/levels/Level.java:929`, `core/…/levels/Level.java:930`, `core/…/levels/Level.java:931`, `core/…/levels/Level.java:932`, `core/…/levels/Level.java:933`, `core/…/levels/Level.java:934`
- **Bestiary card:** `docs/bestiary/halls.md#scorpio`

### Claim halls-5

- **Claim:** “General counter to any enemy with a long-range attack: as soon as it spots you, retreat behind a door or around a corner so it loses line of sight and can't use its ranged attack; this applies to Warlocks, Scorpios, Wisps and similar spellcasters/archers throughout these floors.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Confirmed and stronger than stated for scorpios: attacking needs the target in FOV plus a clear PROJECTILE line, and once you leave its FOV a hunting scorpio gives up immediately (WANDERING) instead of chasing.
- **Cites:** `core/…/mobs/Mob.java:1328`, `core/…/mobs/Mob.java:1360`, `core/…/mobs/Mob.java:1401`, `core/…/mobs/Mob.java:1403`, `core/…/mobs/Scorpio.java:75`, `core/…/mobs/Scorpio.java:91`
- **Bestiary card:** `docs/bestiary/halls.md#scorpio`

## Acidic scorpio (`actors.mobs.Acidic`)

### Claim halls-6

- **Claim:** “Scorpios shoot a ranged spike attack and behave similarly to the Gnoll Trickster: they have no melee attack and will try to flee if the Hero gets adjacent. Their ranged hits deal physical damage (reducible by armor) and have a chance to inflict Cripple. They are described as very manageable if you fight from a doorway, though repeated door use can eventually break the door.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Core mechanics confirmed: no attack when adjacent, retreats (getFurther) while hunting, the shot is a normal attack so armor DR applies, and 50% Cripple. It backs away whenever it sees you, not only when you are adjacent. The door caveat is wrong: doors have no durability and only toggle DOOR/OPEN_DOOR (core/…/levels/features/Door.java:36,54). They are removed only by fire or destroy. See the split entry below. Acidic inherits all of this from Scorpio.
- **Cites:** `core/…/mobs/Scorpio.java:74`, `core/…/mobs/Scorpio.java:75`, `core/…/mobs/Scorpio.java:81`, `core/…/mobs/Scorpio.java:82`, `core/…/mobs/Scorpio.java:90`, `core/…/mobs/Scorpio.java:91`, `core/…/actors/Char.java:388`, `core/…/actors/Char.java:390`
- **Bestiary card:** `docs/bestiary/halls.md#acidic`

### Claim halls-7

- **Claim:** “Split from the claim above: 'repeated door use can eventually break the door.'”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Doors only switch between DOOR and OPEN_DOOR. There is no use counter. A door is removed only when flammable terrain is destroyed (fire, the Eye/Yog beams), which leaves EMBERS.
- **Cites:** `core/…/features/Door.java:36`, `core/…/features/Door.java:54`, `core/…/levels/Level.java:928`, `core/…/levels/Level.java:929`, `core/…/levels/Level.java:930`, `core/…/levels/Level.java:931`, `core/…/levels/Level.java:932`, `core/…/levels/Level.java:933`, `core/…/levels/Level.java:934`
- **Bestiary card:** `docs/bestiary/halls.md#acidic`

### Claim halls-8

- **Claim:** “General counter to any enemy with a long-range attack: as soon as it spots you, retreat behind a door or around a corner so it loses line of sight and can't use its ranged attack; this applies to Warlocks, Scorpios, Wisps and similar spellcasters/archers throughout these floors.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Confirmed and stronger than stated for scorpios: attacking needs the target in FOV plus a clear PROJECTILE line, and once you leave its FOV a hunting scorpio gives up immediately (WANDERING) instead of chasing.
- **Cites:** `core/…/mobs/Mob.java:1328`, `core/…/mobs/Mob.java:1360`, `core/…/mobs/Mob.java:1401`, `core/…/mobs/Mob.java:1403`, `core/…/mobs/Scorpio.java:75`, `core/…/mobs/Scorpio.java:91`
- **Bestiary card:** `docs/bestiary/halls.md#acidic`
