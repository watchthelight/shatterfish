---
topic: "Bestiary lore: Caves (depths 11-14)"
tag: v4.0.0
sources:
  - https://pixeldungeon.fandom.com/wiki/Gnoll_brute
  - https://pixeldungeon.fandom.com/wiki/Gnoll_shaman
  - https://pixeldungeon.fandom.com/wiki/Cave_spinner
retrieved: 2026-09-26
tiers:
  tier1: 6
  contradicted: 5
  unverified: 1
claims: 12
---

# Lore: Caves (depths 11-14)

Community claims about these enemies, each graded against the pinned code. Tier 1: the code
confirms it. Tier F: the code contradicts it. Tier 3: not settled by the code. The graded card
is in `docs/bestiary/caves.md`; a claim that reached this file is never a mechanic by itself, only its
verdict and the citations are.

## Gnoll brute (`actors.mobs.Brute`)

### Claim caves-1

- **Claim:** “A Gnoll Brute becomes Enraged when brought to 0 HP, gaining a temporary shield and a big damage buff that can punch through most armor; the safe play is to run away and let the shield/enrage timer expire rather than trading hits with it.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Gnoll_brute>
- **Variant:** unknown
- **Version:** old wiki page (flagged oldVersion)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Confirmed: at 0 HP it gains a BruteRage shield of HT/2+4 (24), deals 15-40 instead of 5-25, and the shield loses 4 per turn, killing it after about 6 turns. A same-speed brute cannot hit a hero who keeps stepping away along an open path, so running it out works.
- **Cites:** `core/…/mobs/Brute.java:86-112`, `core/…/mobs/Brute.java:58-62`, `core/…/mobs/Brute.java:134-151`, `core/…/mobs/Mob.java:1353-1356`
- **Bestiary card:** `docs/bestiary/caves.md#brute`

## Armored brute (`actors.mobs.ArmoredBrute`)

### Claim caves-2

- **Claim:** “A Gnoll Brute becomes Enraged when brought to 0 HP, gaining a temporary shield and a big damage buff that can punch through most armor; the safe play is to run away and let the shield/enrage timer expire rather than trading hits with it.”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Gnoll_brute>
- **Variant:** unknown
- **Version:** old wiki page (flagged oldVersion)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Applied to the armored variant, the 'run away and let it expire' advice fails: ArmoredRage starts at 21 and loses only 1 shield per 3 turns (~63 turns). Kill the shield or leave the floor instead.
- **Cites:** `core/…/mobs/ArmoredBrute.java:59-68`, `core/…/mobs/ArmoredBrute.java:78-100`
- **Bestiary card:** `docs/bestiary/caves.md#armoredbrute`

## Gnoll shaman (red) (`actors.mobs.Shaman.RedShaman`)

### Claim caves-3

- **Claim:** “If a Gnoll Shaman spots you at range, break line of sight behind cover; retreating behind a door makes it chase into melee range, which is the safer place to finish it off.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Gnoll_shaman>
- **Variant:** unknown
- **Version:** old wiki page (flagged oldVersion)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Tactic confirmed: its ranged attack needs a MAGIC_BOLT path that stops on solid cells (walls, closed doors), so breaking line of sight stops it, and a hunting mob that cannot see you walks to your last seen cell. Adjacent it only uses 5-10 melee, which armor reduces. The 'lightning bolt' wording is outdated (see the next entry).
- **Cites:** `core/…/mobs/Shaman.java:72-76`, `core/…/mechanics/Ballistica.java:130-136`, `core/…/mobs/Mob.java:1342-1356`, `core/…/mobs/Shaman.java:91-108`
- **Bestiary card:** `docs/bestiary/caves.md#shaman-redshaman`

### Claim caves-4

- **Claim:** “Its ranged attack is a lightning bolt.”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Gnoll_shaman>
- **Variant:** unknown
- **Version:** old wiki page (flagged oldVersion)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** In v4.0.0 the ranged attack is an EarthenBolt: 6-15 damage through damage() plus a 50% chance of Weakness, Vulnerable or Hex depending on the shaman's colour. It is not lightning, and ELECTRIC-type resistances do not apply to it.
- **Cites:** `core/…/mobs/Shaman.java:113-137`, `core/…/mobs/Shaman.java:111`
- **Bestiary card:** `docs/bestiary/caves.md#shaman-redshaman`

## Gnoll shaman (blue) (`actors.mobs.Shaman.BlueShaman`)

### Claim caves-5

- **Claim:** “If a Gnoll Shaman spots you at range, break line of sight behind cover; retreating behind a door makes it chase into melee range, which is the safer place to finish it off.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Gnoll_shaman>
- **Variant:** unknown
- **Version:** old wiki page (flagged oldVersion)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Tactic confirmed: its ranged attack needs a MAGIC_BOLT path that stops on solid cells (walls, closed doors), so breaking line of sight stops it, and a hunting mob that cannot see you walks to your last seen cell. Adjacent it only uses 5-10 melee, which armor reduces. The 'lightning bolt' wording is outdated (see the next entry).
- **Cites:** `core/…/mobs/Shaman.java:72-76`, `core/…/mechanics/Ballistica.java:130-136`, `core/…/mobs/Mob.java:1342-1356`, `core/…/mobs/Shaman.java:91-108`
- **Bestiary card:** `docs/bestiary/caves.md#shaman-blueshaman`

### Claim caves-6

- **Claim:** “Its ranged attack is a lightning bolt.”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Gnoll_shaman>
- **Variant:** unknown
- **Version:** old wiki page (flagged oldVersion)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** In v4.0.0 the ranged attack is an EarthenBolt: 6-15 damage through damage() plus a 50% chance of Weakness, Vulnerable or Hex depending on the shaman's colour. It is not lightning, and ELECTRIC-type resistances do not apply to it.
- **Cites:** `core/…/mobs/Shaman.java:113-137`, `core/…/mobs/Shaman.java:111`
- **Bestiary card:** `docs/bestiary/caves.md#shaman-blueshaman`

## Gnoll shaman (purple) (`actors.mobs.Shaman.PurpleShaman`)

### Claim caves-7

- **Claim:** “If a Gnoll Shaman spots you at range, break line of sight behind cover; retreating behind a door makes it chase into melee range, which is the safer place to finish it off.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Gnoll_shaman>
- **Variant:** unknown
- **Version:** old wiki page (flagged oldVersion)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Tactic confirmed: its ranged attack needs a MAGIC_BOLT path that stops on solid cells (walls, closed doors), so breaking line of sight stops it, and a hunting mob that cannot see you walks to your last seen cell. Adjacent it only uses 5-10 melee, which armor reduces. The 'lightning bolt' wording is outdated (see the next entry).
- **Cites:** `core/…/mobs/Shaman.java:72-76`, `core/…/mechanics/Ballistica.java:130-136`, `core/…/mobs/Mob.java:1342-1356`, `core/…/mobs/Shaman.java:91-108`
- **Bestiary card:** `docs/bestiary/caves.md#shaman-purpleshaman`

### Claim caves-8

- **Claim:** “Its ranged attack is a lightning bolt.”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Gnoll_shaman>
- **Variant:** unknown
- **Version:** old wiki page (flagged oldVersion)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** In v4.0.0 the ranged attack is an EarthenBolt: 6-15 damage through damage() plus a 50% chance of Weakness, Vulnerable or Hex depending on the shaman's colour. It is not lightning, and ELECTRIC-type resistances do not apply to it.
- **Cites:** `core/…/mobs/Shaman.java:113-137`, `core/…/mobs/Shaman.java:111`
- **Bestiary card:** `docs/bestiary/caves.md#shaman-purpleshaman`

## Cave spinner (`actors.mobs.Spinner`)

### Claim caves-9

- **Claim:** “A Cave Spinner attacks in melee until it lands a poison hit, then flees; kill it fast before it can poison and flee.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Cave_spinner>
- **Variant:** unknown
- **Version:** unspecified
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Confirmed with a correction: each landed bite has a 50% chance (not a certainty) to poison, and a poisoning bite switches it to FLEEING. Killing it before that happens avoids the flee-and-web phase.
- **Cites:** `core/…/mobs/Spinner.java:119-132`, `core/…/mobs/Spinner.java:248-256`
- **Bestiary card:** `docs/bestiary/caves.md#spinner`

### Claim caves-10

- **Claim:** “While fleeing it lays a web trail; stepping on its webs roots you for 5-7 turns.”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Cave_spinner>
- **Variant:** unknown
- **Version:** unspecified
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Webs are not a trail behind the spinner: it shoots a 3-cell web at the cell you are moving into (or between you and it if you stood still), with a 10-turn cooldown. Stepping into a web applies Roots for exactly 5 turns (Roots.DURATION), not 5-7.
- **Cites:** `core/…/mobs/Spinner.java:136-209`, `core/…/buffs/Roots.java:29`, `core/…/blobs/Web.java:67-70`
- **Bestiary card:** `docs/bestiary/caves.md#spinner`

### Claim caves-11

- **Claim:** “Carry a Potion of Levitation to break the Root.”
- **Tier:** 1 (TIER1)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Cave_spinner>
- **Variant:** unknown
- **Version:** unspecified
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Levitation detaches Roots and Roots cannot attach to a flying character.
- **Cites:** `core/…/buffs/Levitation.java:46`, `core/…/buffs/Roots.java:37-44`
- **Bestiary card:** `docs/bestiary/caves.md#spinner`

### Claim caves-12

- **Claim:** “Carry a Potion of Healing before engaging one at low HP.”
- **Tier:** 3 (UNVERIFIED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Cave_spinner>
- **Variant:** unknown
- **Version:** unspecified
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Resource advice; nothing in the code to check beyond the damage figures on this card (10-20 bites, 14-17 poison).
- **Bestiary card:** `docs/bestiary/caves.md#spinner`
