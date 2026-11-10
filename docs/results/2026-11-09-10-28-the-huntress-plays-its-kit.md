# Story 10.28: the Huntress plays its kit

Issue #374. The class kit stories (10.25 to 10.30) teach the Brain each class's starting kit. [Story 10.30](2026-11-09-10-30-the-cleric-plays-its-kit.md)
showed that a kit the Brain never used pays when it is used. This is the Huntress.

## The gap

The Huntress's kit is the Spirit Bow and gloves (`HeroClass.java:221-231`). The bow is already used: the shoot Policy (issue #309) fires it at the nearest
awake, mobile enemy at least two cells away, and the Goo fight volleys with it (story 10.38). What the kit offers beyond that was read at the pinned tag:

- **Shooting beside the enemy** is not a gap. A missile weapon's accuracy is multiplied by 0.5 against an adjacent target and by 1.5 against one that is not
  (`MissileWeapon.java:215-233`, rule row in [combat](../rules/combat.md)), the bow rolls 1 to 6 at the first level (`SpiritBow.java:189-202`), and the gloves
  roll 1 to 5 at twice the speed (`Gloves.java:34-43`). Beside the enemy the gloves are better.
- **The arrow never runs out** (`SpiritBow.java:289-291`), so there is no ammunition habit to teach.
- **Talents** (Nature's Bounty, Barkskin, Followup Strike and the rest, `HighGrass.java:62-110`) are a point-spending question for every class, not a bow
  question; [the talent idea page](../ideas/2026-10-26-spend-the-talent-points.md) measured spending them on the Warrior and found nothing outside noise.
  Furrowed grass is left untrampled by the Huntress (`HighGrass.java:57-65`, `Hero.java:1020-1023`), which the walking Policies do not need to know.
- **The shot is withheld from a fight the hero would not take.** `Shoot` ran only while `Fight.favourable` held, on the reasoning that otherwise the fight
  Policy retreats and a shot would only delay it. Story 10.42 found that this reasoning fails when there is no retreat: an awake crab, which moves two cells to
  the hero's one, cannot be walked away from, and the fight Policy then waits for it (`hold: no-way`) and fights where it stands. It gave `Ranged` (thrown
  missiles) a `cornered` gate for that case and left `Shoot` alone. So a hurt Huntress with the bow in her pack waited for a crab while it walked up, with
  free shots in every turn it spent closing. Crabs are the largest killer before depth 5 (16 of the Huntress's 62 deaths on depths 1 to 4 in
  [story 10.7](2026-10-30-10-7-the-prison-diagnosis.md), where she died 33 times on depth 5, 27 of them to Goo).

## What was built

`Shoot` also fires when the fight is not favourable but `Ranged.cornered` holds: the floor is not sealed and `Fight.retreat` finds no way away. `cornered`
became a package-level static of `Ranged` that both Policies call. Nothing else changed: the target rules (awake, mobile, two or more cells off, clear line,
no Goo in view) and the Choice (`UseItemAt(bow, SHOOT, cell)`, reason `shoot: spirit bow`, so the existing CoachLine template `shoot/shoot` covers it) are the
same. Rule row `bow-shoot` in [brain rules](../brain-rules.md) states the new case.

Tests: `ShootPolicyTest` (a hurt Huntress with a crab four cells off shoots, the first of two new cases, which failed on `main` with
`expected: <shoot> but was: <fight>`; the stairs two steps away stay the way out, a sealed floor keeps the old rule, a sleeping crab is left asleep).

## Numbers

`:rig:direction --set standard --class ALL --first 40 --jobs 4 --debug-record`, 240 Runs per slice, 40 per class, salts 1000 and 3000, baseline pinned to the
merge-base `c6b3b9dc5`, so every difference is this change. The denominators are 240 on every pooled column, 40 on every class column per slice, and 80 per
class over both slices.

| pooled, 240 Runs | salt 1000 baseline | salt 1000 change | salt 3000 baseline | salt 3000 change |
|---|---|---|---|---|
| reach depth 5 | 152 | 154 | 141 | 144 |
| Goo kills | 128 | 126 | 113 | 116 |
| deepest mean | 5.117 | 5.150 | 4.921 | 5.000 |

Pooled over both slices (480 Runs): reach depth 5 rises from 293 to 298 (61.0 to 62.1 percent) and Goo kills from 241 to 242 (50.2 to 50.4 percent).

| class | s1000 reach 5 | s1000 Goo | s3000 reach 5 | s3000 Goo |
|---|---|---|---|---|
| WARRIOR | 32 to 32 | 29 to 29 | 29 to 29 | 22 to 22 |
| MAGE | 26 to 26 | 24 to 24 | 24 to 24 | 21 to 21 |
| ROGUE | 21 to 21 | 14 to 14 | 18 to 18 | 10 to 10 |
| HUNTRESS | 26 to 28 | 21 to 19 | 22 to 25 | 18 to 21 |
| DUELIST | 26 to 26 | 22 to 22 | 25 to 25 | 24 to 24 |
| CLERIC | 21 to 21 | 18 to 18 | 23 to 23 | 18 to 18 |

The keep rule: pooled over both slices not worse in reach depth 5 and Goo kills (both up), and no class 8 or more worse (none is worse in reach; the
Huntress's Goo kills are 39 to 40 of 80). Five classes are identical to the Run, as expected, since they hold no bow. The Huntress alone: reach depth 5
48 to 53 of 80 (60.0 to 66.3 percent), Goo kills 39 to 40 of 80, deepest mean 5.00 to 5.34 on the mean of the two slices. Kept.

## What this cannot show

- Two slices of 240 Runs are two measurements. The Huntress rows are 40 Runs a slice and move up in reach on both (+2 and +3); her Goo kills move -2 and +3,
  so that column is noise. A gain of five in 80 is inside what one 80-Run draw does.
- The lever reaches Runs only through a crab (or another enemy the Brain cannot walk away from) met on an unsealed floor while hurt. It does not touch the
  Goo fight, which is why Goo kills do not move. Reach given Goo conversion is 40 of 53 against 39 of 48 before.
- Median turns for the Huntress went 1967.0 to 1906.5 and 1479.5 to 1954.0, as Runs that used to die on depth 3 or 4 live to take a longer floor.
- The Tengu practice conversion the story's last clause asks for is not measured here: these Runs end at Goo, and the practice floor was not rerun.
- The remaining Huntress gaps are the talents and the subclass ability (Warden, `Answers.java` picks it, nothing uses its Natural Dominance or the armour
  ability), each an Action or Prompt question for a later story.
