# Story 10.43, the Guard chain when fleeing

Story 10.43 (#447), epic E10. Story 10.10 (#356) added a dodge out of the Prison Guard's chain line that needs another
awake enemy in view; it fired in 2 of 480 Runs while Guards killed 24 heroes. This page says why, what the 24 deaths
share, and what a second rule did.

## How the chain works

The Guard pulls once, when it sees the hero within 4 cells (`Dungeon.level.distance( pos, enemy.pos ) < 5`,
`actors/mobs/Guard.java:183`) and cannot attack it, along a clear line to the closest open cell to the Guard
(`Guard.java:71-93`); the hero is set down there, crippled for 4 turns (`Guard.java:127`). The Guard has 40 hit points
and 4 to 12 damage (`Guard.java:46-60`). The pull does no damage.

## The 24 deaths (standard set, salts 1000 and 3000, all classes, main at the 10.10 merge)

- 24 of 480 Runs ended in a Guard's hands; all at depth 7 (22) or 8 (2). Huntress 6, Rogue 5, Warrior 5, Mage 4,
  Cleric 2, Duelist 2.
- 19 of the 24 were crippled by the pull; in 15 the Guard first showed at 4 or more cells, in 9 at 3 or fewer.
- 14 had no other awake enemy in view, so the 10.10 dodge could not enter (the trigger is as written; the line check is
  not the cause). In the other 10 the hero was usually already fighting, out of the line or nearly dead.
- The hero was weak: 16 were STARVING and 3 HUNGRY at the end, the hit points at the start of the encounter were about
  28 for the median hero (of 45 to 60), and every one wore cloth armour +2 with a starting weapon at +1 or +2. At least
  5 of the Guard's 11 health pips remained in 18 of the 24.
- Most were either waiting ("hold: no-way") in the Guard's line as it approached or walking a "retreat" step through
  it when the chain landed.
- They carried unused tools, by a count of the last debug record: a known potion of strength in most, liquid flame in
  about 8, frost in about 5, an attack or warding wand in about 8, and a stone of fear, flock or aggression in 5. The
  Brain has no Policy that throws a known harmful potion or zaps a wand at a Guard.

So the situation does occur. The pull is not what kills the hero: it is set down beside a 40-hit-point melee mob it
cannot hurt, at low health, with its escape route ended.

## The experiment

Rule: when the fight is not favourable (the hero holds or retreats), the dodge needs no company and takes the cell out of
the line that is farthest from the Guard. The first test (`a_fleeing_hero_steps_out_of_the_line_alone`) failed with
the 10.10 rule (the hero stepped along "retreat 4") and passes with it. Code on branch `story/10-44-guard-flee-chain`,
not merged.

Direction check, standard set, all classes, 40 per class, `--jobs 1`, baseline 65576db61 (the 10.10 head):

| | reach d5 | Goo kills | Guard deaths | Runs with a dodge |
|---|---|---|---|---|
| salt 1000, before / after | 122 / 122 | 101 / 101 | 10 / 12 | 1 / 15 |
| salt 3000, before / after | 123 / 123 | 88 / 88 | 11 / 9 | 2 / 11 |
| pooled | 245 / 245 | 189 / 189 | 21 / 21 | 3 / 26 |

Reach depth 10 is 0 of 480 in both. The rule fires in 26 Runs and changes nothing in the columns: the Guard deaths are
the same number. No class dropped.

## Decision

Not merged. A rule with no measured effect adds a gate to the Fight Policy for nothing. The lever the 24 deaths point
at is item use against a Guard that cannot be outfought (the known strength potion, the wands, thrown liquid flame
or frost from the 4 to 8 cells the Guard walks), a new Policy with its own fairness question, left to an idea file:
`docs/ideas/2026-11-08-guard-fight-uses-the-pack.md`. A separate observation: 16 of 24 were starving when they met the
Guard, which is the food economy of earlier stories, not the Guard.
