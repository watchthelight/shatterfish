# Issue #219, step 6: fight Goo on ground where a pump can be dodged, and wash off the ooze

**A direction check, unregistered.** Nothing here ran under a Registration and no sequential test was run. It is the
measurement behind two additions to `Goo.engage` (the fight Policy's branch for an awake Goo that is the only enemy in
view): `goo: position` and `goo: wash` ([`goo-position`](../brain-rules.md#goo-position),
[`goo-wash`](../brain-rules.md#goo-wash)). Upstream `v4.0.2`, Profile 6, the `shatterfish` Brain on
`weights/shatterfish.json`, Oracle off. Seeds from the `standard` set only; the `goo` and `holdout` sets were not
touched.

Commands (the baseline is `origin/main` at `a8aa3589d`, which has #264, built in a worktree by the tool):

```
./gradlew :rig:direction --args="--set standard --class WARRIOR --first 40 --salt-base 1000 --baseline origin/main --jobs 6 --debug-record"
./gradlew :rig:direction --args="--set standard --class WARRIOR --first 80 --salt-base 3000 --baseline origin/main --jobs 6 --debug-record"
```

## Why the Brain lost the Goo fights it lost

On `a8aa3589d` 68 of the 120 Runs reach depth 5 (28 of the tuning 40, 40 of the 80 on salts 3000) and 49 kill Goo,
so 19 do not. The classification below is from the debug record of those 19 (a scratch script outside the repository;
it reads only the files the two commands wrote). A loss is put in the first class that fits: killed by the ooze
itself; the Run ends within three waits of a pump-up announcement that the Brain did not answer with a dodge; arrived
at the seal under 60% of its hit points; else plain blows.

| primary class of the loss | losses on `a8aa3589d` | with the change |
|---|---|---|
| a pumped blow the Brain did not dodge | 11 | 3 |
| the caustic ooze finished the hero | 3 | 3 |
| under 60% of hit points at the seal | 2 | 2 |
| plain blows (attrition) | 2 | 1 |
| died before Goo woke (no seal) | 1 | 1 |
| total | 19 | 9 |

Tags that overlap those classes, over the same losses: 6 of 19 arrived at the seal under 60% of their hit points and
10 were Starving at the seal (7 of 9 with the change; that cause is the food story of the Goo plan, not touched here);
7 had caustic ooze on them for 10 or more waits (1 with the change).

What the pumped blows were. The Brain already answers a pump-up announcement with a dodge: two steps straight away
from Goo, because Goo only attacks a pumped blow from up to two cells (Goo.java:141-152) and a Goo that has to step
drops the pump (Goo.java:243-259). Of 454 announcements in the 68 fights, 104 got no dodge. In the debug record the
Brain's `Goo.dodge` found no plan in those: the hero stood with a wall at its back or in a pocket, where there is no
cell two away, and none three away. A map dump of the Brain's own walkable cells at those waits showed rooms of the
arena's width with the hero against the wall Goo was coming from the other side of, and alcoves. The hero stands
wherever it was when Goo woke, and wherever the last dodge left it, and then waits for Goo to come. A player in that
spot would take a few steps into the room first, since Goo comes one cell a turn and spends those turns walking
(Mob.java:1363-1399), so the walk costs no blow.

The ooze: Goo puts caustic ooze on the hero on one hit in three (Goo.java:157-160), 20 turns of a point a turn on
depth 5, and the buff is gone the turn after it first acts if the hero stands in water (Ooze.java:91-118). The Brain
had no rule for it. The icon shows the ooze but, for this buff, not its turns (the debug record has turns -1), so the
rule cannot read how long is left.

## The change

1. `goo: position`: with Goo the only enemy in view and no pump pending, work out where Goo will stand beside the
   hero (it steps toward the hero; the guess is the walk of nearest-to-the-hero neighbours) and whether a dodge would
   exist there (`Goo.dodgeable`, the same rule as the dodge itself). If not, walk to the nearest ground within six
   steps where one would, by cells that are not beside Goo, the roomiest of those.
2. `goo: wash`: with the ooze buff on the hero, the hero not in water and no pump pending, walk to the nearest water
   within four steps (by cells not beside Goo, to a cell where a dodge would still work) before striking.

Both read the map as drawn, Goo's drawn cell, and the hero's buff icons; neither adds a field to the Observation or the
Memory. A dodge still comes first. Tests in `GooTest`: `positions_before_contact` failed on `main` (it chose
`goo: wait`), as did `washes_the_ooze_off`; `holds_in_the_open`, `strikes_when_cornered` and `does_not_wash_for_nothing`
pin the cases where nothing changes. One existing test, `holds_for_goo`, had the hero two cells from a wall in a
two-row room, which is a place the new rule moves from; its hero stands one column further in.

## Result

Both slices, `main` (`a8aa3589d`) against the change, from the tool's table:

| 40 Warriors, salts 1000 to 1039 | `main` | change | delta |
|---|---|---|---|
| reach depth 5 | 28/40 | 28/40 | 0 |
| Goo kills | 20/40 | 23/40 | +3 |
| median turns | 2066.5 | 2212.0 | +145.5 |
| deepest mean / max | 5.300 / 8 | 5.275 / 8 | -0.02 |
| deaths by depth 1..8 | 0/1/7/4/8/10/7/3 | 0/1/7/4/5/16/5/2 | |
| oscillation Steps | 642 | 539 | -103 |

| 80 Warriors, salts 3000 to 3079 | `main` | change | delta |
|---|---|---|---|
| reach depth 5 | 40/80 | 40/80 | 0 |
| Goo kills | 29/80 | 35/80 | +6 |
| median turns | 1873.5 | 1982.0 | +108.5 |
| deepest mean / max | 4.425 / 8 | 4.513 / 8 | +0.09 |
| deaths by depth 1..8 | 4/9/14/13/14/15/10/1 | 4/9/14/13/10/16/13/1 | |
| oscillation Steps | 1120 | 1169 | +49 |

The two slices are 120 distinct Runs of which 68 reach depth 5 on both sides (the change touches only the Goo fight,
so reach is identical), and kills go from 49 to 58: conversion 72.1% to 85.3%. The mechanism the change aims at, from
the debug records of those 68 fights:

| | `main` | change |
|---|---|---|
| pump-up announcements seen | 454 | 599 |
| of those, answered with no dodge | 104 (22.9%) | 30 (5.0%) |
| mean waits with ooze on the hero, per sealed fight | 7.8 | 4.9 |

More announcements with the change because the fights are longer and end less often in a death. Median turns are
higher for the same reason (a fight won takes longer than one lost early), and the deaths shifted a floor on, to
depths 6 and 7, where the Runs that now live die.

## Staging

The `goo: position` rule alone (before `goo: wash`) played 23/40 and 34/80 on the same slices, so it carries almost
all of the kills; `goo: wash` added one kill on the 80 and none on the 40, and halved the ooze waits per fight (9.2 to
3.7 on the 40, 9.7 to 5.6 on the 80 among sealed fights won). It is kept on the mechanism, not on kills.

## What the tables cannot show

- It is 68 fights. The 40 and 80 slices are the two slices used throughout #219, and the change was built by reading
  the losses of both, so the 80 is a second look at the same kind of Runs, not an independent test set, and no later
  slice was played. The +9 kills over 68 fights is a rate difference of 13 points with a standard error of about 6
  (the Runs are paired by seed but diverge from the first changed decision), so about two sigma on its own. The
  mechanism table above is the stronger evidence: unanswered pump announcements fall from 23% to 5% and the
  pumped-blow losses from 11 to 3.
- It does not touch the six losses that arrive at the seal under 60% of hit points and starving; those are the food
  and rest story of the plan, and the part of the Goo plan that already measured no gain.
- Goo's walk is guessed as a greedy walk toward the hero; on a map where its path-finding goes round a wall the guess is
  wrong and the hero walks to ground it did not need, or does not walk where it should. The cost of the first is a few
  turns before the blow. A sealed Goo fight lasts 57.8 waits on average with the change against 42.6 on `main` (the
  fights that are won last longer than the ones lost early), the longest 146 against 126, and all 120 Runs still end
  in a death before the turn cap, so no loop ran away; the strategy log was not read for a repeated cell.
- `:rig:direction` plays the baseline on the current host code, and this change does not touch the host, so the
  baseline column is `main` as it plays.
