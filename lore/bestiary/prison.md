---
topic: "Bestiary lore: Prison (depths 6-9)"
tag: v4.0.0
sources:
  - https://pixeldungeon.fandom.com/wiki/Skeleton
  - https://www.youtube.com/watch?v=TE1zJ1Eh7fg
  - https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies
  - https://steamcommunity.com/sharedfiles/filedetails/?id=2786737188
  - https://pixeldungeon.fandom.com/wiki/User_blog:108.184.82.95/How_to_kill_Wraiths
retrieved: 2026-09-26
tiers:
  tier1: 1
  contradicted: 4
  unverified: 0
claims: 5
---

# Lore: Prison (depths 6-9)

Community claims about these enemies, each graded against the pinned code. Tier 1: the code
confirms it. Tier F: the code contradicts it. Tier 3: not settled by the code. The graded card
is in `docs/bestiary/prison.md`; a claim that reached this file is never a mechanic by itself, only its
verdict and the citations are.

## Skeleton (`actors.mobs.Skeleton`)

### Claim prison-1

- **Claim:** “Skeletons explode on death for damage equal to their normal melee hit, but armor absorption against that explosion is halved compared to normal hits; wear your heaviest available armor in Prison and, if possible, finish Skeletons off with a ranged attack from a distance once they're at very low HP so the explosion can't reach you.”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Skeleton>
- **Variant:** unknown
- **Version:** pre-v4 (source flagged as old version)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Old-version claim. In v4.0.0 the blast is its own roll of 6-12 (melee is 2-10), and armor DR is applied twice, not halved (two drRoll() calls subtracted). The advice to finish it from 2+ tiles is still correct: only the 8 adjacent cells are hit.
- **Cites:** `core/…/mobs/Skeleton.java:78-81`, `core/…/mobs/Skeleton.java:124-125`, `core/…/mobs/Skeleton.java:65-68`
- **Bestiary card:** `docs/bestiary/prison.md#skeleton`

### Claim prison-2

- **Claim:** “Aim to have armor that blocks roughly 4+ damage on average before descending into Prison, specifically to survive the death-explosion reliably if you can't kill a Skeleton before it reaches melee range.”
- **Tier:** 1 (TIER1)
- **Source:** <https://www.youtube.com/watch?v=TE1zJ1Eh7fg>
- **Variant:** unknown
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** The arithmetic holds in v4.0.0: the blast averages 9 (NormalIntRange 6-12) and DR is subtracted twice, so an average DR roll of 4 absorbs about 8 of it. The exact threshold is advice; the double-DR mechanism is code.
- **Cites:** `core/…/mobs/Skeleton.java:81`, `core/…/mobs/Skeleton.java:124-125`
- **Bestiary card:** `docs/bestiary/prison.md#skeleton`

## Crazy thief (`actors.mobs.Thief`)

### Claim prison-3

- **Claim:** “Crazy Thieves attack twice per turn and try to steal one unequipped item from your backpack per hit; they can't steal unique, quest, or upgraded/enchanted items, so keeping valuables equipped or upgraded protects them from being stolen.”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies>
- **Variant:** spd
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Partly confirmed: attack delay is halved (two attacks per turn), equipped, unique and +1-or-higher items are safe. Contradicted: enchantment gives no protection (the check is level() &lt; 1, so a +0 enchanted item can be stolen), and a thief steals only once; after a successful theft it flees and no longer steals. A hit whose random draw is protected steals nothing.
- **Cites:** `core/…/mobs/Thief.java:86-89`, `core/…/mobs/Thief.java:129-131`, `core/…/mobs/Thief.java:146-150`
- **Bestiary card:** `docs/bestiary/prison.md#thief`

## Crazy bandit (`actors.mobs.Bandit`)

### Claim prison-4

- **Claim:** “Crazy Bandits can steal and shatter a Honeypot from your inventory, which releases a hostile Golden Bee, so don't assume a Bandit encounter is contained to just the Bandit if you're carrying honeypots.”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://steamcommunity.com/sharedfiles/filedetails/?id=2786737188>
- **Variant:** unknown
- **Version:** not stated
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** The theft and shatter are real, but the released bee targets the pot holder, which is the thief/bandit that stole it. It only turns to the hero after the pot is dropped (the bandit dies), and then only if no other mob is within 3 tiles of the pot and the hero is within 3.
- **Cites:** `core/…/mobs/Thief.java:159-163`, `core/…/items/Honeypot.java:97-136`, `core/…/mobs/Bee.java:96-102`, `core/…/mobs/Bee.java:145-205`, `core/…/mobs/Thief.java:99-105`
- **Bestiary card:** `docs/bestiary/prison.md#bandit`

## Spectral necromancer (`actors.mobs.SpectralNecromancer`)

### Claim prison-5

- **Claim:** “Wraiths have very high evasion, so melee attacks miss often; they're described as weak to magic-type damage (including fire and poison), and this source claims a Wraith only has 1 HP so any connecting hit — including a guaranteed surprise attack performed by retreating behind a door — kills it outright.”
- **Tier:** F (CONTRADICTED)
- **Source:** <https://pixeldungeon.fandom.com/wiki/User_blog:108.184.82.95/How_to_kill_Wraiths>
- **Variant:** unknown
- **Version:** pre-v4 (source flagged as old version)
- **Date:** not stated by the source
- **Retrieved:** 2026-09-26
- **Verdict against `v4.0.0`:** Claim is about Wraith generally; recorded here because the spectral necromancer summons them. Confirmed: HP 1 and evasion = 5 x accuracy (70 for its level-4 wraiths). Contradicted: wraiths are INORGANIC, so poison and toxic gas do nothing. Note summoned wraiths start with enemySeen = true, so the door-retreat surprise only works after they lose sight of you.
- **Cites:** `core/…/mobs/Wraith.java:49`, `core/…/mobs/Wraith.java:57`, `core/…/mobs/Wraith.java:85-89`, `core/…/actors/Char.java:1421-1422`
- **Bestiary card:** `docs/bestiary/prison.md#spectralnecromancer`
