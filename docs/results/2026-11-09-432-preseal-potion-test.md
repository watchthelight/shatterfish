# Issue 432: a pre-seal potion test, tried and dropped

Issue #432 asked that the Goo fight use the potions in the pack. Part (b), the gamble on the likeliest-healing unknown potion when dying, was
merged as #449 ([the gamble page](2026-11-08-435-unknown-potion-gamble.md)). This page is part (a): learn which unknown potion is healing
before the Goo seal, so the Heal policy has a known potion to drink in the fight. It was built, measured on two slices and dropped.

## The lever

On depth 4 and on the Goo floor before its seal, a calm hero at full health drinks the unknown potion with the highest odds of being healing
(at least the Playbook's `healing_odds_per_mille`), standing where any other test fits. The depth was the tunable `test_item.preseal_depth`
(4, range 0 to 5, 0 for never). The reason: of the 50 sealed Goo fights lost in story 10.40, 42 never drank, and only 11 held a potion known
as healing. The code lived in `TestItem` (`presealed`, a sort key that puts the likeliest-healing potion first) and was commit `c865b3c24` on
the local branch `story/432-preseal-potion-test`. It is not merged.

## Numbers

`:rig:direction --set standard --class ALL --first 40 --jobs 4 --debug-record`, 240 Runs per slice, 6 classes of 40, baseline pinned to the
merge-base `28f4fe38d`, so every difference is this change. The denominators are 240 on all four columns.

| pooled, 240 Runs | salt 1000 baseline | salt 1000 lever | salt 3000 baseline | salt 3000 lever |
|---|---|---|---|---|
| reach depth 5 | 137 | 136 | 127 | 125 |
| Goo kills | 117 | 118 | 107 | 105 |
| deepest mean | 4.842 | 4.808 | 4.688 | 4.671 |
| reach depth 4 | 169 | 169 | 155 | 155 |

Pooled over both slices: reach depth 5 falls from 264 to 261 and Goo kills from 224 to 223. Per class, reach depth 5 and Goo kills, baseline to
lever:

| class | s1000 reach 5 | s1000 Goo | s3000 reach 5 | s3000 Goo |
|---|---|---|---|---|
| WARRIOR | 28 to 28 | 25 to 25 | 32 to 32 | 25 to 25 |
| MAGE | 19 to 19 | 17 to 16 | 22 to 21 | 21 to 19 |
| ROGUE | 23 to 24 | 14 to 15 | 14 to 14 | 8 to 9 |
| HUNTRESS | 25 to 25 | 22 to 24 | 22 to 21 | 18 to 18 |
| DUELIST | 29 to 27 | 26 to 26 | 22 to 22 | 21 to 21 |
| CLERIC | 13 to 13 | 13 to 12 | 15 to 15 | 14 to 13 |

No class is 8 or more worse on either measure, but the keep rule asks that the pooled reach depth 5 and Goo kills are not worse, and both are
(by 3 and by 1). The lever is flat: the moves are one to two Runs a cell and change sign across classes. It is dropped and the code is not merged.

## Why it does nothing

Not measured directly (the Run logs of the slices were not kept), so these are readings, not findings:

- The depth 4 and 5 tests only add information when a Run holds an unknown potion with healing odds above the threshold, at full health, on a
  calm screen. The Runs that die before the seal mostly die at depth 3 or 4 hurt (story 10.46), where the hold on an unknown potion at less
  than full health already applies and #449's gamble now covers the dying case. The Runs that reach the seal and lose it are the 11 of 50 that held known healing
  plus Runs whose pack holds a potion the Beliefs rate below the threshold.
- A test that names healing also spends it: the pre-seal drink at full health wastes the heal itself. The Heal policy then has nothing to
  drink in the fight, where the unknown-potion gamble would have drunk it at the right moment. The lever swaps a late drink for an early one
  and the gamble (#449) already makes the late drink.

## The Warrior never gambled: not a gap

Issue #449 saw the Warrior untouched in its 80 Runs. The cause is by design in the game, not a hole in the Brain. `HeroClass.initWarrior`
calls `new PotionOfHealing().identify()` (`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/hero/HeroClass.java:186`), and
`Potion.identify` marks the class known in the potion handler (`items/potions/Potion.java:368-371`, `isKnown` at `:349`). So for a Warrior every
healing potion is identified on sight, no unknown appearance can be healing, and the Beliefs give every unknown potion a healing probability of zero.
The gamble needs a positive chance of healing to have a positive value, and the pre-seal test needs the odds to reach `healing_odds_per_mille`; both
correctly never fire. The Warrior's known-healing potions are drunk by the Heal policy as before. The other five classes learn a different potion
at start (`HeroClass.java:201`, `:218`, `:229`, `:244`, `:259`) and do gamble. Nothing to fix.

## What the Goo kill rate still lacks

The rate on the standard set is 117 of 240 and 107 of 240 (49 and 45 percent), and a potion in the pack is not what separates the lost fights.
Next candidates stay with the Goo fight itself and with reaching depth 5 healthy (reach depth 5 is 57 and 53 percent, so the Goo kill rate given
reach is 85 and 84 percent): the rate of kills per Run is mostly set by who reaches the seal. Raising reach is the larger lever.

## What this cannot show

Two slices of 240 Runs are two measurements, and the baseline here is `28f4fe38d`, before #457 (food worth), which lifts reach. A lever that
needs more calm Runs holding unknown potions may show on that base; the sign here is flat to negative on both slices, so it was not re-run.
