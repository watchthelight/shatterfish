# Issue 435: gamble on the likeliest-healing unknown potion when dying

Story 10.40 ([the Sewers killers](2026-10-31-10-40-sewers-killers.md)) found that 45 of the 76 Runs lost to Crab, Gnoll, Snake or Swarm on
depths 1 to 4 died holding unknown potions, and none drank; 42 of 50 lost Goo fights never drank either. The Brain already drink-tests when hurt
(`TestItem`), but only in calm, and a dying hero beside an enemy has no calm. This page says what was built and what it measured.

## The change

`Heal.gamble`, the last step of the `Heal` policy: HP below the drink threshold, no known healing on hand, an awake enemy within two cells. The
Beliefs (`Beliefs.identities`, #418) give each unknown potion appearance its odds of being each potion; `SafeTest.candidates` and `SafeTest.harm`
price the harmful identities from the Codex. In hit points:

- healing gains `min(heals(ht), ht)`;
- an identity whose worst case takes at least the hero's HP, or disables it beside an enemy, costs `ht`;
- another harmful identity costs its damage;
- a harmless one costs the turn, the close enemies' damage for one turn.

The appearance with the highest P(healing) among those with a positive expected value is drunk. When none is positive (burning or toxic gas
would kill at the current HP, say) nothing is drunk and the Fight policy goes on. It adds no number to the Playbook beyond the existing
`healing_odds_per_mille` and `potion_hp_per_mille`. Rule row `unknown-potion-gamble`, CoachLine sentence for the `gamble` reason.

Test (ADR-0019): `HealPolicyTest.gambles_on_an_unknown_potion` fails on the head of `main` (no drink, the hero fights on) and passes.
`gamble_is_priced` pins the expected value, including the skip when a worst case would kill, and `no_gamble_without_an_enemy_close` pins the
trigger.

## Numbers

`:rig:direction --set standard --class ALL --first 40 --jobs 1 --debug-record`, baseline pinned to the merge-base `9b46e1172`, so every
difference is this change.

| pooled, 240 Runs | salt 1000 baseline | salt 1000 gamble | salt 3000 baseline | salt 3000 gamble |
|---|---|---|---|---|
| reach depth 5 | 129 | 142 | 118 | 122 |
| Goo kills | 109 | 119 | 82 | 96 |
| deepest mean | 4.846 | 4.963 | 4.429 | 4.575 |
| median turns | 1800.5 | 1876.0 | 1540.7 | 1611.0 |
| Runs that gambled | 0 | 55 | 0 | 51 |

Per class, reach depth 5 and Goo kills (baseline to gamble), melee classes first:

| class | s1000 reach 5 | s1000 Goo | s3000 reach 5 | s3000 Goo |
|---|---|---|---|---|
| WARRIOR | 32 to 32 | 32 to 32 | 27 to 27 | 21 to 21 |
| ROGUE | 18 to 20 | 12 to 12 | 17 to 16 | 8 to 10 |
| DUELIST | 18 to 22 | 13 to 17 | 17 to 20 | 12 to 17 |
| HUNTRESS | 27 to 27 | 25 to 24 | 24 to 24 | 20 to 21 |
| MAGE | 22 to 25 | 19 to 20 | 19 to 19 | 14 to 16 |
| CLERIC | 12 to 16 | 8 to 14 | 14 to 16 | 7 to 11 |

The Warrior is untouched on both slices (identical Runs): it never gambled in its 80 Runs. Duelist gambled in 31 Runs and Cleric in 13 (the
two slices together). The gain sits with the
classes that die early, Duelist and Cleric above all, and no class loses more than one Goo kill on either slice. Deaths on depth 4 fall from 48 to
36 (salt 1000) and from 44 to 40 (salt 3000); deaths on depths 5 to 7 rise a little because those Runs now live to reach them.

## What it cannot show

The practice floor's kit holds known healing, so the gamble does not fire there and is measured on the full-Run slices only. Two slices of 240
Runs are two measurements; the sign agrees on both and on every pooled column, but a single class cell moves by one or two Runs. The run time
columns rise by about 70 turns because the Runs that live longer play more.

The slices were played before main gained the emergency scrolls (#411). After that merge the gamble comes after them: a known teleport, terror,
mirror image or lullaby scroll is tried first, since it costs nothing to identify. The unit tests pass on the merged head; the slices were not
replayed, so the numbers above are for the gamble on its own.

## Fairness

The `fairness-reviewer` found no violation: the gamble reads only the `Observation`, `Memory` and the Beliefs, and `ItemView` carries no true
identity, so a differential test on hidden identity would hold for any Brain and was not added.
