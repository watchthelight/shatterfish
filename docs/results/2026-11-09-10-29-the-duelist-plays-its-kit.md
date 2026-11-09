# Story 10.29: the Duelist plays its kit

Issue #375. The class kit stories teach the Brain each class's starting kit; [story 10.30](2026-11-09-10-30-the-cleric-plays-its-kit.md) showed that
one unused kit item can move a class. This is the Duelist. On the two ALL-40 slices of that story the Duelist reached depth 5 in 27 and 24 of 40 Runs
(51 of 80) and killed Goo in 23 and 23 (46 of 80). The story is kept on the keep rule.

## The gap

The Duelist starts wearing a rapier (`HeroClass.java:233-247`). A Duelist's weapon ability is its default action: it costs one charge, which the weapon
shows as `cur/max` (`MeleeWeapon.java:76-82`, `:423-433`, `:482-488`; two at the start, one regained about every 60 turns while not full). For the
rapier the ability is the lunge: it jumps the hero to a free cell beside an enemy exactly two cells away and strikes it in the same turn, without a miss
(`Char.INFINITE_ACCURACY`) and for 5 more than a blow at level 0, so 6 to 13 against the rapier's own 1 to 8 (`Rapier.java:55-58`, `:71-74`, `:92-177`).
Every other distance, a rooted hero, or no free cell beside the target is refused with a message and passes no turn (`Rapier.java:106-127`). No Policy used
the ability, so the Duelist played as a fighter with a rapier that waited for each enemy to close. Rule rows `rapier-lunge` (Brain) and the combat page row.

The `ABILITY` item action was already among the targeted actions the Observation offers (`ValidActions.AT_A_CELL`), and the executor's generic item path
(item action, then the cell) plays it as a person does: `LungeTest` drives it on a Duelist and passes without an executor change, so there is no new Action
kind and no hook.

## What was built

- **Brain** (`Lunge.java`, a new Policy, named `lunge`, placed after `zap`, before `shoot`, `ranged` and `fight`): `UseItemAt(rapier, ABILITY, enemy cell)`
  when the worn rapier shows a charge, the nearest awake, mobile enemy is exactly two cells off, and a Step the screen offers ends beside it. Goo is included,
  except while its pump is pending (the fight Policy dodges that). A lunge the game refused leaves the screen as it was; the Policy then skips one wait, as the
  zap Policy does. No Playbook field: the Policy reads the weapon, not the class. CoachLine template `lunge/lunge`.
- No Observer, executor, or upstream change.

Tests: `LungePolicyTest` (the Duelist lunges at two cells; not at one or three; not with no or unknown charge, another weapon, an unworn rapier, a sleeper, or
no cell beside the enemy; the first of them failed on `main`, where no Policy chose the ability) and `LungeTest` (the executor lunges: a charge goes, the hero
stands beside the target, the blow lands for at least 6).

## Numbers

`:rig:direction --set standard --class ALL --first 40 --jobs 4 --debug-record`, 240 Runs per slice, 40 per class, salts 1000 and 3000, baseline pinned to the
merge-base `c6b3b9dc5`, so every difference is this change. The denominators are 240 on every pooled column per slice, 480 pooled, 40 per class per slice and
80 per class over both slices.

| pooled, 240 Runs a slice | salt 1000 baseline | salt 1000 change | salt 3000 baseline | salt 3000 change |
|---|---|---|---|---|
| reach depth 5 | 152 | 154 | 141 | 142 |
| Goo kills | 128 | 130 | 113 | 111 |
| deepest mean | 5.117 | 5.192 | 4.921 | 4.933 |

Pooled over both slices (480 Runs): reach depth 5 rises from 293 to 296 (61.0 to 61.7 percent) and Goo kills stay at 241 (50.2 percent).

| class (40 Runs a slice) | s1000 reach 5 | s1000 Goo | s3000 reach 5 | s3000 Goo |
|---|---|---|---|---|
| WARRIOR | 32 to 32 | 29 to 29 | 29 to 29 | 22 to 22 |
| MAGE | 26 to 26 | 24 to 24 | 24 to 24 | 21 to 21 |
| ROGUE | 21 to 21 | 14 to 14 | 18 to 18 | 10 to 10 |
| HUNTRESS | 26 to 26 | 21 to 21 | 22 to 22 | 18 to 18 |
| DUELIST | 26 to 28 | 22 to 24 | 25 to 26 | 24 to 22 |
| CLERIC | 21 to 21 | 18 to 18 | 23 to 23 | 18 to 18 |

The keep rule: pooled over both slices not worse in reach depth 5 (up 3) and Goo kills (equal), and no class 8 or more worse (none is worse by more than 2; five classes
are identical to the Run, as expected since they hold no rapier). The Duelist alone: reach depth 5 from 51 to 54 of 80, Goo kills 46 to 46 of 80, deepest mean
5.09 to 5.35 on the mean of the two slices. Kept.

## What this cannot show

- The effect is small and inside the noise of 80 Runs: +3 reach on the Duelist, and its Goo kills are +2 on one slice and -2 on the other. The change is kept
  because it does no harm on the rule, not because these numbers prove a gain.
- The lunge fires once per approach: it needs distance exactly two, and afterwards the hero is beside the enemy, where the ability has no use. A rapier lunge
  is about 9.5 against a Goo of 100 hit points, so it does not move the Goo fight much. The charges (two to three at the depths Goo is met) are spent freely;
  a variant that saves a charge for Goo was not tried, because the lever is flat and the pool is not worse.
- The abilities of found weapons (other melee weapons offer a different one, with other ranges and targets) and the Duelist's talents are not used. The lunge is
  gated to the rapier by name.
- Tengu practice conversion is not measured here.
