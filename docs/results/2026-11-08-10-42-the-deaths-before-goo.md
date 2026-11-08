# Story 10.42: the deaths before Goo

Issue #445. Goo is killed in most of the Runs that reach depth 5 (113 of 135, 84%, on the current-main slice the story
started from), but only 135 of 240 reach it. In the 520 Runs read here, 256 (49.2%) die on depths 1 to 4. This page reads
those 256 deaths from Run logs that already existed (no new Runs for the diagnosis), names the causes the hero could have
avoided, and measures the fix for the one that is new. The unknown-potion gamble is story #435's and the thieves are
another agent's; both are counted below and left alone.

## The Runs read

Three sets of existing logs, all with `--debug-record`:

- slice 1000 and slice 3000: `:rig:direction --set standard --class ALL --first 40`, 240 Runs each, Brain at `1e5ddb015`
  (the current values side of the story 10.37 confirmation);
- slice edc5: the 40 Runs (salts 1000 to 1039, all classes) of the issue #412 baseline, Brain at `edc544ade`.

The worktree named in the brief for the thief slice (`7ea59b6fa`) held no Run logs of its own, so the #412 baseline folder
stands in for it.

|  | slice 1000 | slice 3000 | slice edc5 | pooled |
|---|---|---|---|---|
| Runs | 240 | 240 | 40 | 520 |
| reach depth 5 | 129/240 | 118/240 | 17/40 | 264/520 |
| Goo kills | 109/240 | 82/240 | 13/40 | 204/520 |
| Goo kills among the Runs that reach depth 5 | 109/129 | 82/118 | 13/17 | 204/264 |
| deaths on depths 1 to 4 | 111 | 122 | 23 | 256 |

The hero reaches depth 5 in about half the Runs and kills Goo in four of five of those that do. The summaries the tables
below are counted from are committed: `docs/results/logs/2026-11-08-10-42-deaths-before-goo/runs.tsv` (every Run) and
`docs/results/logs/2026-11-08-10-42-deaths-before-goo/deaths.tsv` (the 256 deaths on depths 1 to 4: killer, depth, health
and level on arrival at the floor, what the pack held then, and how the last Decisions ran). `DeathsBeforeGooPageTest`
recomputes every table here from them.

## Who kills the hero, and where

| killer | depth 1 | depth 2 | depth 3 | depth 4 | deaths | share |
|---|---|---|---|---|---|---|
| Crab | 0 | 0 | 43 | 38 | 81 | 31.6% |
| Gnoll | 0 | 24 | 13 | 1 | 38 | 14.8% |
| Swarm | 0 | 0 | 26 | 8 | 34 | 13.3% |
| Snake | 23 | 8 | 2 | 0 | 33 | 12.9% |
| Hunger | 0 | 4 | 3 | 8 | 15 | 5.9% |
| Slime | 0 | 0 | 0 | 12 | 12 | 4.7% |
| Rat | 4 | 2 | 3 | 0 | 9 | 3.5% |
| Poison | 0 | 1 | 2 | 4 | 7 | 2.7% |
| Piranha | 0 | 1 | 3 | 2 | 6 | 2.3% |
| HermitCrab | 0 | 0 | 3 | 2 | 5 | 2.0% |
| other (10 kinds) | 1 | 8 | 1 | 6 | 16 | 6.3% |
| all | 28 | 48 | 99 | 81 | 256 | 100.0% |

The Crab alone is a third of the deaths, on depths 3 and 4 only. The Gnoll is depth 2 (24 of 38), the Snake depth 1 (23
of 33), the Swarm depth 3. Hunger, Slime, Rat, Poison and Piranha are the next rank and each below 6%.

|  | WARRIOR | MAGE | ROGUE | HUNTRESS | DUELIST | CLERIC | pooled |
|---|---|---|---|---|---|---|---|
| Runs | 87 | 87 | 87 | 87 | 86 | 86 | 520 |
| deaths on depths 1 to 4 | 22 | 44 | 51 | 32 | 48 | 59 | 256 |
| share of the Runs | 25.3% | 50.6% | 58.6% | 36.8% | 55.8% | 68.6% | 49.2% |
| by depth 1/2/3/4 | 2/5/6/9 | 2/6/21/15 | 11/9/16/15 | 1/8/13/10 | 6/6/19/17 | 6/14/24/15 | 28/48/99/81 |
| to the Crab | 5 | 14 | 15 | 16 | 13 | 18 | 81 |

The Warrior loses a quarter of its Runs before depth 5 and the Cleric more than two thirds; the other four are between
a third and three fifths. The Crab is the first or second killer of every class.

## What the hero carried

| in the pack on arrival at the floor the hero died on | deaths | share of 256 |
|---|---|---|
| an unknown potion | 127 | 49.6% |
| an unknown scroll | 26 | 10.2% |
| a known potion of healing | 13 | 5.1% |
| a throwing weapon | 103 | 40.2% |
| none of the four | 72 | 28.1% |

Half of the dead hold an unknown potion (story #435's gamble, left to it), a tenth an unknown scroll, one in twenty a
known potion of healing, and two in five a throwing weapon. 72 of 256 hold none of the four.

## The causes, and what each costs

| cause, counted on the 256 deaths (they overlap) | deaths | share | killers (top three) |
|---|---|---|---|
| the first Decision of the fatal fight was at under a third of health | 100 | 39.1% | Crab 36, Hunger 11, Swarm 11 |
| 10 or more of the 40 Decisions before it were under a third of health with no enemy in view | 87 | 34.0% | Crab 37, Swarm 13, Hunger 11 |
| starving on the last Decision | 68 | 26.6% | Crab 22, Hunger 15, Swarm 13 |
| hungry or starving on the last Decision | 116 | 45.3% | Crab 40, Swarm 21, Hunger 15 |
| waited (`hold: no-way`) for an enemy it could not walk away from | 101 | 39.5% | Crab 53, Gnoll 12, Swarm 12 |
| of them, with a throwing weapon in the pack and none thrown | 28 | 10.9% | Crab 17, Gnoll 3, Snake 2 |
| of them, with a throwing weapon in the pack and at least one thrown | 7 | 2.7% | Snake 2, Swarm 2, Crab 1 |
| of them, with nothing to throw | 66 | 25.8% | Crab 35, Gnoll 9, Swarm 8 |
| with a throwing weapon in the pack and none thrown in the fatal fight, held or not | 74 | 28.9% | Crab 25, Gnoll 13, Snake 8 |
| went back and forth between a retreat and another Policy 4 times or more | 46 | 18.0% | Crab 15, Gnoll 8, Swarm 8 |
| died holding an unknown potion | 127 | 49.6% | Crab 51, Swarm 16, Gnoll 14 |

How the last Decisions run is read from the Decision record: the Policy and its reason, and the Safety flags the screen
raised (`hp-low` is under a third of the hit points, the status pane's own warning, `Safety.java` line 29). The record holds
no hit point count, so "how hurt" is that flag and the health on arrival at the floor. Each death has the 20 last
Decisions in `deaths.tsv`.

### 1. A hurt hero meets the fight (100 of 256, 39%)

Two fatal fights in five begin under a third of health, and 87 heroes spent 10 or more of their last 40 quiet Decisions
under a third of health, walking on. These are the rest levers that were measured three times and not kept
([rest when hurt and fed](../ideas/2026-10-26-rest-when-hurt-and-fed.md), [rest by
need](../ideas/2026-10-26-rest-by-need.md), and the 10.40 reading). They fail on the food clock: 68 of the 256 are
starving on the last Decision, and a starving hero does not regenerate (`Regeneration.java:56`). Nothing is built here;
the arithmetic of those pages has not changed, and story 10.39 owns the food.

### 2. The hero waits for an enemy it cannot walk away from (101 of 256, 39%)

This is the new one. When an enemy is in view, the fight is not one the hero would take, and there is no way to walk
away, the fight Policy answers `hold: no-way` (`Fight.java` lines 347 to 357) for up to four waits and then fights where it
stands as `cornered` (`Fight.java` lines 291 to 296). There is no walk away from a Crab, because it moves two cells to the hero's
one (`Fight.outpaced`, `Fight.java` lines 680 to 682), unless the stairs are two steps off (`Fight.java` line 674, `OUTPACED_STAIRS`).
So it is mostly the Crab that does this: 53 of the 101.

In the same Decisions the ranged Policy, which ranks above the fight Policy, throws only while the fight is one the hero
would take: `Ranged.java` line 158 returns nothing when `Fight.favourable` is false, on the reasoning that "the fight Policy
retreats, which a throw would only delay" (`Ranged.java` line 40). That is true when the fight Policy can retreat and false
when it cannot: the throw is then not a delay but the only thing the free turns can buy. A thrown weapon takes one turn
and hits one and a half times as often from a distance (`Ranged.java` lines 25 to 31), and the enemy spends a turn on each cell it
crosses, so a Crab that closes two cells a turn from six cells away leaves two or three free throws.

In 28 deaths the hero waited with a throwing weapon in the pack and threw none (17 of them to the Crab), and in 7 more it
threw at least once. The other 66 waited with nothing to throw, which nothing here can change. Counted more widely, 74
heroes died with a throwing weapon in the pack and none thrown in the fatal fight, but for the others the enemy was
asleep, first seen beside the hero, or the fight was one the hero took, which is the existing rule's.

### 3. Hunger (68 starving on the last Decision, 116 hungry or starving)

26.6% of the dead are starving at the last Decision, and 15 have Hunger itself as the killer. Story 10.39 owns the food
budget (the Prison second pass), and the table above already shows the same Runs under cause 1. Not built here.

Also counted, and not built here: 46 heroes went back and forth between a retreat and another Policy four times or more
(the stairs bounce, `docs/ideas/2026-10-30-starving-stairs-bounce.md`), and 127 died holding an unknown potion (#435).

## Which cause to build

The largest by count is cause 1, and it is the one with the longest record of failing in the rig: three measurements
inside the noise, each stopped by the food clock this page also shows. Building it a fourth time without a new lever would
repeat a rejected experiment. Cause 2 is the largest that is new, it is a gap between two Policies that the code states in
its own comments, and the change is small and local: the ranged Policy also throws when the fight Policy has nowhere to
go. That is the fix measured below.

## The fix, measured: KEPT

The change (commit `ee12dea53`) let the ranged Policy throw when the floor is not sealed and
`Fight.retreat` finds no way away, as well as when the fight is favourable. Its first test,
`RangedPolicyTest` "a crab the hero cannot outrun and would not take on is thrown at while it closes, not waited for",
failed on `main` (`396dc470a`): the Decision was `Wait`, `hold: no-way`. The brain tests passed with the change.

It was played against `396dc470a` (pinned by SHA) on the two ALL-40 slices of the standard set, salt bases 1000 and 3000,
`:rig:direction --set standard --class ALL --first 40 --salt-base N --baseline 396dc470a --jobs 1 --debug-record`, and
paired by `:rig:confirm` (the E3 Sequential test, H0 0.50, H1 0.55, alpha = beta = 0.05, per-class veto at 8). The keep
rule of this epic is: pooled over both slices not worse, and no class 8 or more worse. It does not require E3 to accept H1
(PRs #449 and #438 were kept on it). Reach depth 5 and Goo kills, pooled per slice:

| | baseline 1000 | fix 1000 | baseline 3000 | fix 3000 |
|---|---|---|---|---|
| reach depth 5 | 122/240 | 127/240 | 123/240 | 122/240 |
| Goo kills | 101/240 | 106/240 | 88/240 | 89/240 |

Both slices pooled, per class (baseline `396dc470a` against the fix, summed over the 40 Runs of each class on each slice):

| class | reach depth 5 base | fix | Goo kills base | fix |
|---|---|---|---|---|
| WARRIOR | 63 | 62 | 52 | 52 |
| MAGE | 42 | 42 | 34 | 34 |
| ROGUE | 32 | 33 | 18 | 19 |
| HUNTRESS | 43 | 43 | 34 | 35 |
| DUELIST | 43 | 47 | 34 | 38 |
| CLERIC | 22 | 22 | 17 | 17 |
| POOLED | 245 | 249 | 189 | 195 |

E3 on each slice (`:rig:confirm`, columns are the pairs of fix against baseline):

**Slice 1000**

| group | pairs | missing | worse | equal | better | verdict | consumed | Goo kills fix | Goo kills base | veto |
|---|---|---|---|---|---|---|---|---|---|---|
| POOLED | 240 | 0 | 19 | 195 | 26 | REJECT | 194 | 106 | 101 | no |
| WARRIOR | 40 | 0 | 5 | 33 | 2 | UNDECIDED | 40 | 27 | 27 | no |
| MAGE | 40 | 0 | 3 | 37 | 0 | REJECT | 38 | 17 | 17 | no |
| ROGUE | 40 | 0 | 2 | 30 | 8 | UNDECIDED | 40 | 12 | 12 | no |
| HUNTRESS | 40 | 0 | 5 | 30 | 5 | UNDECIDED | 40 | 20 | 19 | no |
| DUELIST | 40 | 0 | 4 | 26 | 10 | UNDECIDED | 40 | 23 | 19 | no |
| CLERIC | 40 | 0 | 0 | 39 | 1 | UNDECIDED | 40 | 7 | 7 | no |

**Slice 3000**

| group | pairs | missing | worse | equal | better | verdict | consumed | Goo kills fix | Goo kills base | veto |
|---|---|---|---|---|---|---|---|---|---|---|
| POOLED | 240 | 0 | 20 | 194 | 26 | REJECT | 219 | 89 | 88 | no |
| WARRIOR | 40 | 0 | 2 | 34 | 4 | UNDECIDED | 40 | 25 | 25 | no |
| MAGE | 40 | 0 | 1 | 32 | 7 | UNDECIDED | 40 | 17 | 17 | no |
| ROGUE | 40 | 0 | 3 | 31 | 6 | UNDECIDED | 40 | 7 | 6 | no |
| HUNTRESS | 40 | 0 | 5 | 31 | 4 | UNDECIDED | 40 | 15 | 15 | no |
| DUELIST | 40 | 0 | 7 | 29 | 4 | REJECT | 27 | 15 | 15 | no |
| CLERIC | 40 | 0 | 2 | 37 | 1 | UNDECIDED | 40 | 10 | 10 | no |

Over the 480 pairs the fix is better in 52, worse in 39 and equal in 389. Pooled over both slices it is not worse: depth 5
245 to 249 and Goo kills 189 to 195. In the pair counts the largest worse-minus-better in any class on either slice is 3
(the veto is 8), and no class kills fewer Goos. **Verdict: KEPT** on the keep rule.

E3 did not accept H1: the pooled Sequential test on each slice ends REJECT of the 0.55 hypothesis and five of six classes
are UNDECIDED on both slices (26 better against 19 worse and 26 against 20 are too few decided pairs to show a win rate
near 0.55). That is "undecided" for the gain, not evidence of harm, and the keep rule does not ask for acceptance.

What the tables cannot show: the change acts only in the deaths that wait with a throwing weapon in the pack (28 of 256
in the diagnosis), so a true effect of a few pairs in a hundred is below what 240 pairs a slice can see. The size of the
gain is an estimate with a wide interval; the claim kept is "not worse", with a likely small gain.

## Caveats

- The diagnosis reads Run logs of earlier Brains (`1e5ddb015` for slices 1000 and 3000, `edc544ade` for the #412 set),
  not `396dc470a`. Cause 2 was in the code at all three; the counts will have moved a little since.
- The log has no hit point series; "under a third of health" is the Safety `hp-low` flag.
- The thief slice named in the brief was not found; the #412 baseline stands in for it.

Reproduce: `:rig:direction` as above for each slice, then `:rig:confirm --candidate <fix folder> --current <baseline
folder>`; the committed `fix-1000` and `fix-3000` folders hold each slice's table and test output.
