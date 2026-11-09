# Story 10.47, wear and wield better gear

Issue #459, epic E10. Story 10.41 (#443) found that at depth 5 the worn armour is upgraded cloth in every Run, a heavier
armour sits unworn in 48% and the spare weapon goes unused in 88%, and that holding scrolls of upgrade until depth 4 makes
things much worse. This page measures the three levers it left open, each on its own, against main at `91c24281c`
(known_gains 1 in, food worth in). One is kept.

Each lever: `:rig:direction --set standard --class ALL --first 40 --salt-base <1000|3000> --baseline 91c24281c --jobs 4`,
240 Runs per slice, the standard set only. Denominators are 240 per slice and 480 pooled for every row below, the same on
both sides. The keep rule is the epic's: pooled over both slices not worse in reach depth 5 and in Goo kills, and no class
8 or more worse. E3 acceptance is not required.

## What the pinned code says

| Fact | Where |
|---|---|
| A found armour is cursed with probability 0.30 and then always carries a curse glyph; its level is +0 (75%), +1 (20%) or +2 (5%), never negative. A name that shows no glyph is cursed with 0.30/0.85. | `Armor.java:655-679` |
| A cursed piece cannot be taken off, but a scroll of upgrade clears the flag (`cursed = false`) and a scroll of remove curse clears the flag and the glyph. | `EquipableItem.java:126-131`, `Armor.java:471-473,490`, `ScrollOfRemoveCurse.java:117-145` |
| Armour absorbs `NormalIntRange(DRMin, DRMax)` with `DRMax = tier * (2 + level)`, `DRMin = level`; each point of strength short takes 2 off the roll, and evasion is divided by 1.5 for it. | `Armor.java:379-407`, `Hero.java:652-657`, `Armor.java:420-422` |
| Strength asked is `8 + 2 * tier - floor((sqrt(8 * level + 1) - 1) / 2)`; the hero starts at 10: cloth 10, leather 12, mail 14. | `Armor.java:698-703`, `Weapon.java:358-363` |
| A weapon short of strength loses accuracy by 1.5 and swings 1.2 times slower for each point: 5/9 of its damage a turn. | `Weapon.java:303`, `Weapon.java:314-317` |
| A found weapon is cursed with probability 0.30, level +0/+1/+2 as armour. | `Weapon.java:426-447` |

The equip Policy rated armour short of strength at two thirds a point, so a leather armour two points short looked like 0.9
of a cloth armour's absorbing when the game gives it none (a roll of 0 to 4 less 4). That model was only ever wrong in the
direction of wearing too much, so it never mattered while the curse price (0.353 of the cursed weight, 3529 per mille of
armour weight) kept every short-strength armour off.

## Lever 1: `armour_pick` HEAVIEST

Set in every `upgrade` section of `playbooks/shatterfish.json` (the default, Warrior and Mage). Salt 1000 only: it changed
nothing, and the second slice was stopped.

| Slice | reach depth 5 | Goo kills |
|---|---|---|
| 1000 | 143 to 143 | 120 to 120 |

The 240 Runs have the same Decision sequence as the baseline, step for step, so this is not a flat result of a lever that
works: it never fires. `TestItem.heaviest` considers a spare armour only when its level is shown (`TestItem.java:585`), and a
found armour has a hidden level; the Playbook value is read (three entries changed, the file reaches the child through
`Direction.play`), the candidate list is empty. This is what the #305 page said for 40 Runs; it holds for 240. Dropped.

## Lever 3: the spare weapon

Starting weapons are identified, so a scroll of upgrade read onto one shows (`cudgel +1`), and the equip Policy refused to
replace any worn weapon whose shown level was above 0, because the Codex's means are level 0's. The lever rates the worn weapon
at the Codex row for its shown level (`Equip.rated`, levels 0 to 3, 5 and 10; a level without a row keeps the old refusal) and
cuts a weapon short of strength by 5/9 a point instead of 2/3. From the Runs of 10.41 (112 reaching depth 5): a spare melee
weapon beat the worn one on the game's own numbers in 33 of the 84 Runs holding one (a spear or sickle at 11 against a
cudgel +1 at 6).

| Slice | reach depth 5 | Goo kills |
|---|---|---|
| 1000 | 143 to 144 | 120 to 120 |
| 3000 | 133 to 133 | 106 to 105 |
| pooled | 276 to 277 | 226 to 225 |

The lever fired: 58 Runs wore a weapon from the pack against 17 on the baseline, and 37 of the 240 slice-1000 Runs differ
from it. Goo kills fall by one pooled, which is not "not worse" on the rule as written. Worst class: Rogue loses 3 Goo kills,
Warrior gains 4. A heavier weapon at +0 that replaces one with two scrolls in it is a loss of the scrolls, and the gain in
damage does not show in the Goo kill count. Dropped, on the rule. Branch `story/10-47-l3` holds the code.

## Lever 2: wear the heavier armour before any scroll is read

Equip weighs an armour by what the game rolls (`Equip.armourAbsorbed`, the triangular roll less 2 a point short, cut by 1.5
a point for evasion) and, while the worn armour shows no upgrade, weighs both pieces at the level the coming scrolls give them
(`PROJECTED_UPGRADES`, 2, after 2.2 scrolls read by depth 5 in the #305 page: 89 by 40 Warriors). At strength 11 a leather
armour at +2 asks 11 and absorbs 2 to 8 against the cloth's 2 to 4, so it goes on and the scrolls follow it; at strength 10
it is a point short and absorbs no more than the cloth, so it stays in the pack. The curse is priced as before, 0.353 of the
cursed weight: a scroll of upgrade clears the flag, but the price is charged in full, which only makes the lever stricter.

The first version was measured at 276 to 268 reach depth 5 and 226 to 219 Goo kills pooled, and every class lost. Its Run
logs showed the cause: 15 Runs put the cloth and the leather on in turn (79 and 95 "wear" Decisions) because a worn piece of
hidden level was weighed at level 0 while the spare was weighed at +2. The fix weighs a worn piece of hidden level like a
fresh one (`EquipPolicyTest.no_ping_pong`) and the lever was measured again from scratch:

| Slice | reach depth 5 | Goo kills |
|---|---|---|
| 1000 | 143 to 144 | 120 to 121 |
| 3000 | 133 to 136 | 106 to 108 |
| pooled | 276 to 280 | 226 to 229 |

| Class | reach depth 5 (pooled, 80 Runs) | Goo kills |
|---|---|---|
| Warrior | 62 to 61 | 51 to 51 |
| Mage | 49 to 50 | 44 to 45 |
| Rogue | 37 to 39 | 23 to 24 |
| Huntress | 48 to 48 | 39 to 39 |
| Duelist | 51 to 51 | 46 to 46 |
| Cleric | 29 to 31 | 23 to 24 |

Kept on the keep rule: not worse pooled in either column, the largest loss in a class is 1. The gain is 4 and 3 in 480, well
inside the noise of a chain of unequal Runs, so the page claims no more than "not worse"; the lever is justified by the mechanism
(it prices a short-strength armour correctly and puts the scrolls where they pay) more than by the table.

## What the tables cannot show

- Every baseline is `91c24281c`. Main has moved since; a later merge changes the baseline, not these differences.
- Four of the five tables are one-slice-pair measurements. Nothing here was confirmed on a third salt.
- The upper bound for lever 2 is small: of 112 Runs reaching depth 5, only 10 held a leather armour with no strength
  shortfall and 24 were at strength 11, and the hero's strength was 10 at the first scroll in most.
- Heavier armour only pays after about three scrolls at strength 10 (leather +3 absorbs 3 to 10 against cloth +3's 3 to 5),
  and the hero has two by depth 5. The lever that would change that is strength, not the armour choice.
