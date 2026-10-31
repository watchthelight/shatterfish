# Issue 429: Goo on dry ground

Story 10.38 ([the Goo fight](2026-10-30-10-38-the-goo-fight.md)) left the melee classes where they were. Goo regains hit points on every act it
ends in water (`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Goo.java:109-111`) and stood in water on 51% to 58% of
the fight Decisions, so the question was whether holding dry ground against it is what loses the fight. This page says what was built, that it
does what it claims, and that it is not what loses.

## The change

`Goo.dry`: with Goo the only enemy in view, no pump pending, Goo not beside the hero, and no volley to fire, the cell where Goo will fight the
hero (the guessed arrival cell of `goo-position`) is read from the shown terrain. When it is water, the hero walks to the nearest ground within
`meet_range` Steps whose arrival cell is dry and where a pump could be dodged, by cells that are not beside Goo. It adds no new number. Rule
row `goo-dry`, the combat rule for the heal, CoachLine sentence `goo/dry` ("to keep Goo out of water").

Test (ADR-0019): `GooTest.keeps_dry_ground_before_contact` fails on the head of `main` after story 10.38 (`goo: wait`, not `goo: dry`) and passes.
`strikes_a_goo_in_water` and `dry_yields_to_the_dodge` pin the two cases where the hero must not walk.

A first variant also stepped back from a hurt Goo that stood beside the hero in water, so that Goo would follow onto dry ground. It was worse on
the practice floor (223 Goo kills of 240 against 228; Huntress 38 to 33) because the step costs a blow for one of Goo's, and it was dropped
before the slices.

## Does the practice floor mean anything

Yes. The practice arena has plenty of water: Goo stood in water on 55.1% of the fight Decisions of the baseline (51.1% when beside the hero).

| practice, 40 per class, salts 1000 to 1039 | Goo killed of 240 | Goo in water, all Decisions | Goo in water, beside the hero |
|---|---|---|---|
| baseline (story 10.38 head) | 228 | 55.1% | 51.1% |
| first variant (also stepped back) | 223 | 31.0% | 20.8% |
| dry ground before contact (kept) | 229 | 45.5% | 38.5% |

## The direction check (40 per class, six classes, `standard`)

`:rig:direction --set standard --class ALL --first 40 --salt-base <1000|3000> --jobs 3 --debug-record`, baseline pinned to `eb38f1c4d` (the head of
`main` after story 10.38).

| slice | Goo kills | reach d6 | deaths on depth 5 | of them to Goo |
|---|---|---|---|---|
| 1000 baseline | 95 | 94 | 29 | 23 |
| 1000 dry ground | 96 | 95 | 28 | 22 |
| 3000 baseline | 88 | 85 | 27 | 21 |
| 3000 dry ground | 89 | 85 | 27 | 17 |

Goo's share of fight Decisions spent in water, pooled over both slices: 55.0% on the baseline and 44.5% with the change (beside the hero: 49.3%
and 35.4% in won fights, 57.4% and 38.3% in lost ones). The lever does what it claims. It moves the outcome by one Run a slice, which is
noise; it is kept because it is not worse on either slice and does what the rule says.

Per class, melee first, Goo kills and reach d6 on slice 1000 then 3000, baseline then change:

| class | 1000 Goo kills | 1000 reach d6 | 3000 Goo kills | 3000 reach d6 |
|---|---|---|---|---|
| WARRIOR | 29, 30 | 29, 30 | 24, 25 | 24, 24 |
| ROGUE | 8, 10 | 8, 9 | 6, 7 | 6, 7 |
| DUELIST | 8, 7 | 7, 7 | 15, 13 | 14, 12 |
| CLERIC | 11, 10 | 11, 10 | 9, 10 | 9, 10 |
| MAGE | 16, 16 | 16, 16 | 14, 14 | 13, 14 |
| HUNTRESS | 23, 23 | 23, 23 | 20, 20 | 19, 18 |

The melee classes move by a Run or two in both directions; Duelist is the one that loses (1 to 2 Runs). What the tables cannot show: 40 Runs per
class per slice cannot tell a one-Run change from none.

## So what loses the fight

The heal is not it. Goo spends half the fight in water and the hero cannot change that without losing the same number of blows. The next table
compares the 228 sealed depth-5 Goo fights of the 10.38 slices (volley side, both slices), 178 won and 50 lost, by the hero's state at the seal
(`dry-arrive.py`, `dry-lost.py`):

| at the seal | won | lost |
|---|---|---|
| HP over max HP | 0.941 | 0.888 |
| level | 6.04 | 5.80 |
| strength | 10.82 | 10.70 |
| armor | cloth +1.65 | cloth +1.64 |
| weapon enchant | +0.99 | +0.92 |
| starving | 20% | 30% |
| potions on hand | 5.6 | 5.3 |
| drank in the fight | 48% | 16% |

1. **The pack is not used.** 42 of the 50 lost fights never drank, with about five potions in the pack. Only 11 of the 50 held a potion already
   known as healing; the rest were unknown colours never tried. Lost heroes died from 20 to 30 hit points in a few Decisions (a pumped blow is
   3 to 24, 36 below half). Issue #432 (test the potions before the seal) and #435 (gamble on the likeliest-healing unknown when dying, for every
   fight: 45 of 76 Sewers deaths held unknown potions and none of the 76 drank in its last 20 Decisions).
2. **HP and hunger at the seal.** Starving at the seal is 30% of lost fights and 20% of won ones (Mage and Huntress: 38% and 13%). That is the
   food story, 10.39.

Level, strength, armor and weapon do not separate won from lost: all 228 fights wore cloth armor and strength was about 10.8. This data neither
supports nor refutes "drink strength and wear better armor" (10.17) as a Goo lever, since the whole population fights Goo in cloth at starting
strength; it only shows that the lever is not yet pulled for either group.
