# Story 10.27: the Rogue plays its kit

Issue #373. The class kit stories (10.25 to 10.30) teach the Brain each class's starting kit; [10.30](2026-11-09-10-30-the-cleric-plays-its-kit.md) showed
that a kit pays. This is the one built in this turn: the Rogue, chosen because on the two ALL-40 slices of the previous measurement it was the lowest
class in reach of depth 5 once the Cleric was fixed (39 of 80 Runs, against 44 to 61 for the others) and killed Goo in 24 of 80, the fewest of any class.
The story is kept on the keep rule.

## The gap

The Rogue starts wearing a cloak of shadows with three charges, a dagger, throwing knives, and a known scroll of magic mapping and potion of invisibility
(`HeroClass.java:204-219`). The Brain throws the knives (the ranged Policy), but no Policy ever put the cloak on, so the Rogue played as a fighter with a
dagger. The cloak's STEALTH action costs one turn and one charge (`CloakOfShadows.java:89-103`, `:319-325`) and makes the hero invisible. A mob cannot
see an invisible hero: its `enemyInFOV` needs `enemy.invisible <= 0` (`Mob.java:289-291`) and a hunting mob attacks only when `enemyInFOV` is true
(`Mob.java:1365-1367`), so it walks toward where it last saw the hero instead of hitting it. The hero's next blow hits whatever its accuracy
(`Char.java:627-629`) and, because the mob is surprised by an invisible hero (`Mob.java:912-916`), the dagger rolls from three quarters of the way between
its minimum and maximum up to the maximum (`Dagger.java:62-79`), against a plain roll over the whole range. The blow then dispels the cloak (`Hero.java:2389`,
`Invisibility.java:90-98`), at no further charge while the cloak's next four-turn bill is not due (`CloakOfShadows.java:360-365`). A charge returns in about
forty turns while the cloak is off (`CloakOfShadows.java:230-244`). Rule rows `cloak-opener` and, in the combat Rules page, the Rogue row.

The other pieces of the Rogue's kit were looked at and not built: the Assassin subclass (the Brain already picks it at Tengu's mask) adds a Preparation
buff to the cloak, which matters only after depth 5; the dagger's surprise bonus on a sleeper already comes with the Brain's walk to a sleeping enemy and
the attack, since a mob that has not seen the hero is surprised by it (`Mob.java:912-916`).

## What was built

- **Brain** (`Zap.java`): the zap Policy, which shoots the Mage's staff or the Cleric's tome at an awake, mobile enemy in a clear line within `kit.zap_reach`
  cells, also puts the worn cloak of shadows on when the class has a reach, no staff or tome is held, an awake and mobile enemy that one blow would not
  kill (`Fight.dies`) stands within the reach, the cloak shows a charge on its slot, the hero does not already show the `cloaked` buff, and no Goo is in view.
  The action is `UseItem(cloak, STEALTH)`, which the screen already offers and the executor already plays, so there is no `Observer` or executor change.
  The fight Policy then strikes from the shadow as it always does. It does not put the cloak on twice in a row, so a refused cloak cannot loop.
- **Playbook** (`playbooks/shatterfish.json`): a `ROGUE` entry with `kit.zap_reach` 2. Beside the enemy, or one cell away, the cloak is worth its charge: the
  mob has no time to walk off and the blow follows at once. The reach is a first value, not tuned; no other value was tried.

Tests: `ZapPolicyTest` (the Rogue puts the cloak on at the reach and beside the enemy and not past it; no charge, reach 0, a sleeper, a cloak not worn and a
cloak already on leave it to the dagger; the first of them failed on `main`).

## Numbers

`:rig:direction --set standard --class ALL --first 40 --jobs 4 --debug-record`, 240 Runs per slice, 40 per class, salts 1000 and 3000, baseline pinned to the
merge-base `c6b3b9dc5`, so every difference is this change. The denominators are 240 on every pooled column, 40 on every class column, and 80 per class over
both slices.

| pooled, 240 Runs | salt 1000 baseline | salt 1000 change | salt 3000 baseline | salt 3000 change |
|---|---|---|---|---|
| reach depth 5 | 152 | 162 | 141 | 146 |
| Goo kills | 128 | 141 | 113 | 123 |
| deepest mean | 5.117 | 5.292 | 4.921 | 5.108 |

Pooled over both slices (480 Runs): reach depth 5 rises from 293 to 308 (61.0 to 64.2 percent) and Goo kills from 241 to 264 (50.2 to 55.0 percent).

| class | s1000 reach 5 | s1000 Goo | s3000 reach 5 | s3000 Goo |
|---|---|---|---|---|
| WARRIOR | 32 to 32 | 29 to 29 | 29 to 29 | 22 to 22 |
| MAGE | 26 to 26 | 24 to 24 | 24 to 24 | 21 to 21 |
| ROGUE | 21 to 31 | 14 to 27 | 18 to 23 | 10 to 20 |
| HUNTRESS | 26 to 26 | 21 to 21 | 22 to 22 | 18 to 18 |
| DUELIST | 26 to 26 | 22 to 22 | 25 to 25 | 24 to 24 |
| CLERIC | 21 to 21 | 18 to 18 | 23 to 23 | 18 to 18 |

The keep rule: pooled over both slices not worse in reach depth 5 and Goo kills (both up), and no class 8 or more worse (none is worse; five classes are
identical to the Run, which is the expected result since they hold no cloak and the Brain's decisions for them did not change). The Rogue alone: reach depth 5
39 to 54 of 80 (48.8 to 67.5 percent), Goo kills 24 to 47 of 80 (30.0 to 58.8 percent), deepest mean 4.24 to 5.33 on the mean of the two slices. Kept.

## What this cannot show

- Two slices of 240 Runs are two measurements. The Rogue's rows are 40 Runs a slice and move in the same direction on both (+10 and +5 reach, +13 and +10 Goo
  kills), but each is one draw, and the second slice's gain is half the first's. Median turns for the Rogue grew by 511 and 570, as more Runs live to take a
  longer floor.
- The numbers do not say which part pays: the avoided attack, the guaranteed hit or the dagger's higher roll. The three come together with the cloak, and no
  variant that takes one away was run.
- The reach of 2 is the only value tried. A larger reach puts the cloak on earlier and may spend charges on enemies that wander off; a reach of 1 uses it
  only on an enemy already beside the hero. Neither was measured.
- The `dies` check stands in for "one blow would not kill" with the dagger's plain range; the stealth blow rolls higher, so the Policy sometimes puts the cloak
  on for an enemy the blow would have killed anyway. The fairness review noted this as an accuracy gap, not a leak.
- The cloak is matched by the buff's English name `cloaked`; a build in another language would not match it, and the guard against putting the cloak on twice
  in a row would then be the only thing between the Policy and a toggle.
- Tengu practice conversion for the Rogue is not measured here: the Rogue's Runs on this change end at Goo, and the practice floor was not rerun.
- The Rogue still reaches depth 5 in two thirds of its Runs; the cloak is not used to leave a fight that is going badly, and the potion of invisibility it
  starts with is a known item the Brain treats as any other. Either is a candidate for the class.
