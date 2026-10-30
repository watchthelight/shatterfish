# Story 10.7: the Prison diagnosis

Issue #353. 960 Runs of the current Brain (the head of `main` at `332473aa4`, 10.8 and 10.9 recorded without a rule, 10.15's rest rule in) were
played on the `standard` seed set, 160 per class: 80 at salt base 1000 and 80 at salt base 3000, the first 80 entries of each class in file order,
entry `i` under salt base plus `i`. Unranked, one side, no baseline, nothing from `goo`, `holdout` or `tengu`:

```sh
./gradlew :rig:direction --args="--set standard --class ALL --first 80 --salt-base 1000 --debug-record --jobs 3 --out <folder>"
./gradlew :rig:direction --args="--set standard --class ALL --first 80 --salt-base 3000 --debug-record --jobs 3 --out <folder>"
```

**The logs.** Each Run leaves a Run log and a debug record of about 1.8 MB, 960 of them is about 1.7 GB, so the full logs are not committed.
`python tools/prison-diagnosis.py <out> <folder> <folder>` reads them and writes three small files, committed in
`docs/results/logs/2026-10-30-10-7-prison-diagnosis/`: `runs.tsv` (one row per Run: class, salt, seed code, ending,
depth, killer, hero HP and hunger state on the last Decision, reach), `arrivals.tsv` (the hero's level and strength on the first wait of each
depth, as the Rig's arrival record reads them) and `moments.tsv` (the 20 most expensive Decisions per class). `PrisonDiagnosisPageTest`
recomputes every table below from those three files, in Java and apart from the Python, and fails if the page differs. The tables are printed by
`python tools/prison-diagnosis.py tables <folder>`.

Every Run ended in a death (960 of 960); none reached depth 10, and no Run arrived on depth 9.

## Reach

|  | WARRIOR | MAGE | ROGUE | HUNTRESS | DUELIST | CLERIC | pooled |
|---|---|---|---|---|---|---|---|
| Runs | 160 | 160 | 160 | 160 | 160 | 160 | 960 |
| reach depth 5 | 107/160 | 72/160 | 59/160 | 98/160 | 72/160 | 43/160 | 451/960 |
| reach depth 10 | 0/160 | 0/160 | 0/160 | 0/160 | 0/160 | 0/160 | 0/960 |
| Goo kills | 90/160 | 36/160 | 38/160 | 66/160 | 38/160 | 32/160 | 300/960 |
| deaths on depth 5 | 18 | 37 | 22 | 33 | 34 | 13 | 157 |
| of them to Goo | 15 | 30 | 17 | 27 | 28 | 9 | 126 |
| deaths on depths 1 to 4 | 53 | 88 | 101 | 62 | 88 | 117 | 509 |
| deaths on depths 6 to 9 | 89 | 35 | 37 | 65 | 38 | 30 | 294 |
| deaths on depth 10 or deeper | 0 | 0 | 0 | 0 | 0 | 0 | 0 |

Reach of depth 5 is 451 of 960 (47.0%); the Warrior reaches it in 107 of 160, the Cleric in 43. Of the 451 that arrive on depth 5, 157 die there and
294 go on to depth 6, so the Goo is the narrowest door on the way (below). Nothing reaches depth 10, so the Tengu stories of this epic have no
real arrivals to practice on yet (10.6 draws on arrivals at depth 9, and there are none).

## Hunger

The product owner's first question. A hero is HUNGRY at 300 and STARVING at 450 (`Hunger.java:40-41`); once STARVING it takes 1 damage on each
tick (`Hunger.java:96-100`) and regenerates nothing (`Regeneration.java:56`). The table reads two things: how often Hunger is the killer the game
names, and how often the hero was STARVING (or HUNGRY) on its last Decision whatever name the killer has.

|  | WARRIOR | MAGE | ROGUE | HUNTRESS | DUELIST | CLERIC | pooled |
|---|---|---|---|---|---|---|---|
| deaths | 160 | 160 | 160 | 160 | 160 | 160 | 960 |
| killer is Hunger | 24 (15.0%) | 21 (13.1%) | 16 (10.0%) | 20 (12.5%) | 12 (7.5%) | 15 (9.4%) | 108 (11.3%) |
| STARVING on the last Decision | 101 (63.1%) | 61 (38.1%) | 71 (44.4%) | 74 (46.3%) | 71 (44.4%) | 62 (38.8%) | 440 (45.8%) |
| HUNGRY on the last Decision | 15 (9.4%) | 34 (21.3%) | 28 (17.5%) | 33 (20.6%) | 27 (16.9%) | 32 (20.0%) | 169 (17.6%) |
| deaths on depths 6 to 9 | 89 | 35 | 37 | 65 | 38 | 30 | 294 |
| of them killer Hunger | 13 (14.6%) | 5 (14.3%) | 5 (13.5%) | 7 (10.8%) | 7 (18.4%) | 6 (20.0%) | 43 (14.6%) |
| of them STARVING on the last Decision | 68 (76.4%) | 20 (57.1%) | 29 (78.4%) | 43 (66.2%) | 33 (86.8%) | 21 (70.0%) | 214 (72.8%) |
| of them HUNGRY on the last Decision | 7 (7.9%) | 6 (17.1%) | 6 (16.2%) | 13 (20.0%) | 2 (5.3%) | 7 (23.3%) | 41 (13.9%) |

Hunger is the named killer in 108 of 960 deaths (11.3%), but 440 of 960 heroes (45.8%) were STARVING on their last Decision, and on depths 6 to
9 it is 214 of 294 (72.8%), with another 41 HUNGRY (13.9%). The killer table understates hunger: a starving hero that a Skeleton finishes is a
Skeleton death. Of the 141 Skeleton deaths 102 (72.3%) were STARVING and 18 HUNGRY; the 10.8 page found the same on its smaller set. Story 10.15
moved the numbers it could (the rest after a test) and said what was left: food seen and left behind, and diving when the larder is empty.
That is the new story 10.39 below.

## The Goo

Depth 5 is the Goo. Every Run that reaches it must kill it to go on (`Goo kills` counts the Runs whose ending record names it), and 126 of the
157 deaths on depth 5 are the Goo's (80.3%), 27.9% of everything that arrives there.

|  | WARRIOR | MAGE | ROGUE | HUNTRESS | DUELIST | CLERIC | pooled |
|---|---|---|---|---|---|---|---|
| deaths to Goo | 15 | 30 | 17 | 27 | 28 | 9 | 126 |
| of them hunger NONE on the last Decision | 3 (20.0%) | 6 (20.0%) | 2 (11.8%) | 15 (55.6%) | 5 (17.9%) | 4 (44.4%) | 35 (27.8%) |
| of them hunger HUNGRY on the last Decision | 4 (26.7%) | 13 (43.3%) | 7 (41.2%) | 8 (29.6%) | 10 (35.7%) | 4 (44.4%) | 46 (36.5%) |
| of them hunger STARVING on the last Decision | 8 (53.3%) | 11 (36.7%) | 8 (47.1%) | 4 (14.8%) | 13 (46.4%) | 1 (11.1%) | 45 (35.7%) |

The Goo kills hungry heroes: 91 of its 126 victims (72.2%) were HUNGRY or STARVING when they died. A pumped-up Goo hits for three times its
roll, 3 to 36 (`Goo.java:66-79`), after a pump turn the hero can read (`Goo.java:176-182`). That shape is in the most expensive moments below: of
the 120 moments listed, 105 are on depth 5, 92 are the `fight` Policy with the reason "goo: attack" and 9 are `fight` with "dodge: pump".
The Goo fight is outside 10.8 to 10.16 and gets its own story (10.38 below).

## Where the Runs end

Deaths by depth and the killers of each, the game's own cause of death, per class and pooled. The pooled table is the one to read first.

#### WARRIOR

| depth | deaths | killers |
|---|---|---|
| 1 | 9 | Snake 5, Hunger 2, ToxicGas 2 |
| 2 | 10 | Hunger 4, Snake 2, ToxicGas 2, Gnoll 1, Piranha 1 |
| 3 | 17 | Swarm 5, Gnoll 4, Crab 3, Hunger 3, Burning 1, Poison 1 |
| 4 | 17 | Slime 5, Swarm 5, Crab 4, Bomb 1, Hunger 1, Piranha 1 |
| 5 | 18 | Goo 15, Ooze 2, Hunger 1 |
| 6 | 49 | Skeleton 25, Hunger 13, Thief 4, ToxicGas 2, Bleeding 1, Bomb 1, Burning 1, Ooze 1, Swarm 1 |
| 7 | 34 | DM100 11, Guard 9, Skeleton 9, Bleeding 3, Bee 1, Burning 1 |
| 8 | 6 | DM100 3, Guard 3 |

#### MAGE

| depth | deaths | killers |
|---|---|---|
| 1 | 3 | Snake 2, Bleeding 1 |
| 2 | 14 | Hunger 5, Snake 5, Gnoll 2, ToxicGas 2 |
| 3 | 37 | Crab 17, Swarm 8, Gnoll 4, Hunger 4, HermitCrab 1, Piranha 1, SentryRoom$Sentry 1, ToxicGas 1 |
| 4 | 34 | Crab 8, Hunger 7, Swarm 7, Poison 3, Bomb 2, ToxicGas 2, Bee 1, CausticSlime 1, Gnoll 1, Slime 1, Snake 1 |
| 5 | 37 | Goo 30, Ooze 6, Poison 1 |
| 6 | 18 | Skeleton 10, Hunger 4, Bomb 1, Burning 1, Ooze 1, ToxicGas 1 |
| 7 | 14 | Skeleton 6, DM100 4, Guard 3, Hunger 1 |
| 8 | 3 | DM100 2, Burning 1 |

#### ROGUE

| depth | deaths | killers |
|---|---|---|
| 1 | 19 | Snake 14, Rat 4, ToxicGas 1 |
| 2 | 11 | Snake 4, Gnoll 3, Bee 1, Hunger 1, Piranha 1, ToxicGas 1 |
| 3 | 33 | Crab 10, Swarm 10, HermitCrab 3, Gnoll 2, Hunger 2, Rat 2, Mimic 1, Piranha 1, Poison 1, Snake 1 |
| 4 | 38 | Crab 14, Slime 7, Hunger 5, Swarm 3, Gnoll 2, Poison 2, SentryRoom$Sentry 2, Mimic 1, Piranha 1, ToxicGas 1 |
| 5 | 22 | Goo 17, Hunger 3, Ooze 2 |
| 6 | 32 | Skeleton 20, Hunger 5, Thief 4, Mimic 1, Ooze 1, Swarm 1 |
| 7 | 5 | Skeleton 3, DM100 1, Guard 1 |

#### HUNTRESS

| depth | deaths | killers |
|---|---|---|
| 1 | 4 | Snake 3, Hunger 1 |
| 2 | 13 | Hunger 4, Gnoll 3, Snake 2, Burning 1, Piranha 1, Rat 1, ToxicGas 1 |
| 3 | 27 | Crab 9, Gnoll 5, Swarm 5, Hunger 2, Bleeding 1, Bomb 1, HermitCrab 1, Mimic 1, Snake 1, ToxicGas 1 |
| 4 | 18 | Crab 7, Hunger 4, Slime 3, HermitCrab 2, Swarm 1, Thief 1 |
| 5 | 33 | Goo 27, Ooze 4, Hunger 2 |
| 6 | 50 | Skeleton 35, Thief 6, Hunger 4, Burning 2, GoldenMimic 1, Mimic 1, Poison 1 |
| 7 | 12 | DM100 5, Skeleton 4, Hunger 2, Guard 1 |
| 8 | 3 | DM100 1, Guard 1, Hunger 1 |

#### DUELIST

| depth | deaths | killers |
|---|---|---|
| 1 | 9 | Snake 6, Burning 1, Rat 1, ToxicGas 1 |
| 2 | 11 | Gnoll 4, Hunger 2, SentryRoom$Sentry 2, GoldenMimic 1, Rat 1, Snake 1 |
| 3 | 34 | Crab 13, Gnoll 9, Swarm 6, Snake 3, Bleeding 1, Burning 1, Hunger 1 |
| 4 | 34 | Crab 14, Gnoll 5, Swarm 4, Poison 3, Hunger 2, Slime 2, ToxicGas 2, HermitCrab 1, Piranha 1 |
| 5 | 34 | Goo 28, Ooze 6 |
| 6 | 27 | Skeleton 16, Hunger 4, Burning 2, Poison 2, Ooze 1, Thief 1, ToxicGas 1 |
| 7 | 10 | Guard 3, Hunger 3, Skeleton 3, DM100 1 |
| 8 | 1 | Necromancer$NecroSkeleton 1 |

#### CLERIC

| depth | deaths | killers |
|---|---|---|
| 1 | 12 | Snake 9, Rat 2, ToxicGas 1 |
| 2 | 23 | Gnoll 9, Snake 5, Hunger 3, Rat 2, SentryRoom$Sentry 2, Bomb 1, Piranha 1 |
| 3 | 46 | Crab 17, Gnoll 12, Swarm 7, Hunger 2, Piranha 2, Burning 1, GnollExile 1, GoldenMimic 1, Poison 1, Snake 1, ToxicGas 1 |
| 4 | 36 | Crab 11, Swarm 8, Slime 6, Hunger 3, Bleeding 2, Poison 2, Bomb 1, Gnoll 1, HermitCrab 1, ToxicGas 1 |
| 5 | 13 | Goo 9, Ooze 3, Hunger 1 |
| 6 | 18 | Skeleton 9, Hunger 5, Thief 2, Burning 1, Poison 1 |
| 7 | 9 | DM100 4, Guard 2, Hunger 1, Skeleton 1, Thief 1 |
| 8 | 3 | DM100 1, Guard 1, Necromancer$NecroSkeleton 1 |

#### pooled

| depth | deaths | killers |
|---|---|---|
| 1 | 56 | Snake 39, Rat 7, ToxicGas 5, Hunger 3, Bleeding 1, Burning 1 |
| 2 | 82 | Gnoll 22, Hunger 19, Snake 19, ToxicGas 6, Piranha 4, Rat 4, SentryRoom$Sentry 4, Bee 1, Bomb 1, Burning 1, GoldenMimic 1 |
| 3 | 194 | Crab 69, Swarm 41, Gnoll 36, Hunger 14, Snake 6, HermitCrab 5, Piranha 4, Burning 3, Poison 3, ToxicGas 3, Bleeding 2, Mimic 2, Rat 2, Bomb 1, GnollExile 1, GoldenMimic 1, SentryRoom$Sentry 1 |
| 4 | 177 | Crab 58, Swarm 28, Slime 24, Hunger 22, Poison 10, Gnoll 9, ToxicGas 6, Bomb 4, HermitCrab 4, Piranha 3, Bleeding 2, SentryRoom$Sentry 2, Bee 1, CausticSlime 1, Mimic 1, Snake 1, Thief 1 |
| 5 | 157 | Goo 126, Ooze 23, Hunger 7, Poison 1 |
| 6 | 194 | Skeleton 115, Hunger 35, Thief 17, Burning 7, Ooze 4, Poison 4, ToxicGas 4, Bomb 2, Mimic 2, Swarm 2, Bleeding 1, GoldenMimic 1 |
| 7 | 84 | DM100 26, Skeleton 26, Guard 19, Hunger 7, Bleeding 3, Bee 1, Burning 1, Thief 1 |
| 8 | 16 | DM100 7, Guard 5, Necromancer$NecroSkeleton 2, Burning 1, Hunger 1 |

All killers over the 960 Runs, with the story that owns each:

| killer | depths 1 to 4 | depth 5 | depths 6 to 9 | deaths | share of all deaths | story |
|---|---|---|---|---|---|---|
| Skeleton | 0 | 0 | 141 | 141 | 14.7% | 10.8 |
| Crab | 127 | 0 | 0 | 127 | 13.2% | - |
| Goo | 0 | 126 | 0 | 126 | 13.1% | - |
| Hunger | 58 | 7 | 43 | 108 | 11.3% | 10.15 |
| Swarm | 69 | 0 | 2 | 71 | 7.4% | 10.13 |
| Gnoll | 67 | 0 | 0 | 67 | 7.0% | - |
| Snake | 65 | 0 | 0 | 65 | 6.8% | - |
| DM100 | 0 | 0 | 33 | 33 | 3.4% | 10.9 |
| Ooze | 0 | 23 | 4 | 27 | 2.8% | - |
| Guard | 0 | 0 | 24 | 24 | 2.5% | 10.10 |
| Slime | 24 | 0 | 0 | 24 | 2.5% | - |
| ToxicGas | 20 | 0 | 4 | 24 | 2.5% | - |
| Thief | 1 | 0 | 18 | 19 | 2.0% | 10.11 |
| Poison | 13 | 1 | 4 | 18 | 1.9% | - |
| Burning | 5 | 0 | 9 | 14 | 1.5% | - |
| Rat | 13 | 0 | 0 | 13 | 1.4% | - |
| Piranha | 11 | 0 | 0 | 11 | 1.1% | - |
| Bleeding | 5 | 0 | 4 | 9 | 0.9% | - |
| HermitCrab | 9 | 0 | 0 | 9 | 0.9% | - |
| Bomb | 6 | 0 | 2 | 8 | 0.8% | - |
| SentryRoom$Sentry | 7 | 0 | 0 | 7 | 0.7% | - |
| Mimic | 3 | 0 | 2 | 5 | 0.5% | - |
| Bee | 2 | 0 | 1 | 3 | 0.3% | - |
| GoldenMimic | 2 | 0 | 1 | 3 | 0.3% | - |
| Necromancer$NecroSkeleton | 0 | 0 | 2 | 2 | 0.2% | 10.12 |
| CausticSlime | 1 | 0 | 0 | 1 | 0.1% | - |
| GnollExile | 1 | 0 | 0 | 1 | 0.1% | - |

On depths 6 to 9 the killers outside every story are Burning 9, Bleeding 4, Ooze 4, Poison 4, ToxicGas 4, Bomb 2, Mimic 2, Bee 1 and
GoldenMimic 1: no one of them is 5% of the deaths of depths 6 to 9. The families that the table shows outside the stories 10.8 to 10.16 and that
are at least 5% of all 960 deaths are Goo (13.1%), Crab (13.2%), Gnoll (7.0%) and Snake (6.8%); Hunger (11.3%) has a story that is done and left
levers. Each gets a story from this PR (below).

## Level and strength on arrival

Mean hero level / mean strength on the first wait of each depth, with the number of Runs that arrived in brackets. The Codex lists 4 strength
potions and 6 upgrade scrolls by depth 9 (story 10.17's source); strength 10.8 on depth 6 is about one potion drunk. The Duelist is at 10.0 on
every depth, which no other class is: it never raises strength in these Runs. This page does not say why; story 10.17 (the power curve)
reads it first.

| class | d1 | d2 | d3 | d4 | d5 | d6 | d7 | d8 | d9 |
|---|---|---|---|---|---|---|---|---|---|
| WARRIOR | 1.0 / 10.0 (160) | 2.0 / 10.6 (151) | 2.8 / 10.9 (141) | 4.0 / 10.9 (124) | 6.1 / 10.9 (107) | 6.3 / 10.9 (89) | 7.4 / 11.0 (40) | 8.5 / 11.0 (6) | - |
| MAGE | 1.0 / 10.0 (160) | 1.9 / 10.5 (157) | 2.6 / 10.9 (143) | 3.9 / 10.9 (106) | 5.9 / 11.0 (72) | 6.2 / 11.0 (35) | 7.1 / 10.9 (17) | 7.7 / 11.0 (3) | - |
| ROGUE | 1.0 / 10.0 (160) | 1.9 / 10.3 (141) | 2.8 / 10.8 (130) | 4.1 / 10.9 (97) | 6.1 / 10.9 (59) | 6.6 / 10.9 (37) | 7.4 / 11.0 (5) | - | - |
| HUNTRESS | 1.0 / 10.0 (160) | 1.8 / 10.4 (156) | 2.6 / 10.8 (143) | 3.9 / 10.9 (116) | 5.7 / 11.0 (98) | 6.1 / 11.0 (65) | 7.1 / 11.0 (15) | 7.3 / 11.0 (3) | - |
| DUELIST | 1.0 / 10.0 (160) | 1.9 / 10.0 (151) | 2.6 / 10.0 (140) | 3.9 / 10.0 (106) | 6.1 / 10.0 (72) | 6.4 / 10.0 (38) | 7.2 / 10.0 (11) | 7.0 / 10.0 (1) | - |
| CLERIC | 1.0 / 10.0 (160) | 1.7 / 10.3 (148) | 2.5 / 10.8 (125) | 3.9 / 10.9 (79) | 6.0 / 11.0 (43) | 6.3 / 11.0 (30) | 7.3 / 11.0 (12) | 7.7 / 11.0 (3) | - |
| pooled | 1.0 / 10.0 (960) | 1.9 / 10.4 (904) | 2.7 / 10.7 (822) | 4.0 / 10.8 (628) | 6.0 / 10.8 (451) | 6.3 / 10.8 (294) | 7.2 / 10.9 (100) | 7.9 / 10.9 (16) | - |

## The 20 most expensive Decisions per class

HP lost on the Decision at wait `k`: the hero's HP at that wait minus its HP at wait `k+1`, for the waits the debug record keeps (an enemy in
view, or the log gained a line), and for the last wait of a hero that died, the HP it had. The Policy is the Run log's own `decision.policy`
for that wait. Ties are broken by salt, then wait. "seed/salt" names the Run (seed code and the salt it was played under).

#### WARRIOR

| rank | seed/salt | depth | hp | lost | policy | why | enemies in view |
|---|---|---|---|---|---|---|---|
| 1 | LXO-FXV-JQT/3039 | 5 | 29/45 | 26 | fight | goo: attack | Goo |
| 2 | NKH-RZF-ALI/3046 | 5 | 32/40 | 25 | fight | goo: attack | Goo |
| 3 | GIT-LPU-LWW/3027 | 5 | 41/45 | 24 | fight | goo: attack | Goo |
| 4 | SRE-IOA-ZJR/3069 | 5 | 24/45 | 24 | fight | goo: attack | Goo |
| 5 | KKO-SIP-SSO/1033 | 5 | 45/45 | 22 | fight | goo: attack | Goo |
| 6 | NKH-RZF-ALI/3046 | 5 | 40/40 | 22 | fight | goo: attack | Goo |
| 7 | HJO-WWK-PJG/1002 | 5 | 37/45 | 21 | fight | goo: attack | Goo |
| 8 | XKN-VIM-HTK/1059 | 5 | 45/45 | 21 | fight | goo: attack | Goo |
| 9 | EPS-SUK-IGG/3018 | 5 | 37/50 | 21 | fight | goo: attack | Goo |
| 10 | LXO-FXV-JQT/3039 | 5 | 34/45 | 21 | fight | dodge: pump | Goo |
| 11 | XVX-XDX-IKI/1015 | 5 | 20/50 | 20 | fight | dodge: pump | Goo |
| 12 | CIT-ECF-WRK/1029 | 5 | 20/45 | 20 | fight | goo: attack | Goo |
| 13 | KKO-SIP-SSO/1033 | 5 | 26/45 | 20 | fight | goo: attack | Goo |
| 14 | NCZ-GJC-BLF/3047 | 5 | 23/45 | 20 | fight | goo: attack | Goo |
| 15 | LXO-FXV-JQT/3039 | 5 | 42/45 | 19 | fight | dodge: pump | Goo |
| 16 | LVU-RXJ-BQU/3048 | 5 | 19/40 | 19 | fight | dodge: pump | Goo |
| 17 | QUY-SZB-KCN/3066 | 5 | 20/45 | 18 | fight | goo: attack | Goo |
| 18 | DLR-YWZ-ABC/1007 | 5 | 37/50 | 17 | fight | dodge: pump | Goo |
| 19 | WCA-KFY-YTG/1054 | 5 | 35/40 | 17 | fight | goo: attack | Goo |
| 20 | PMA-UHB-YQN/1019 | 4 | 20/40 | 16 | fight | approach 3 | swarm of flies |

#### MAGE

| rank | seed/salt | depth | hp | lost | policy | why | enemies in view |
|---|---|---|---|---|---|---|---|
| 1 | DLG-RPU-AXM/1002 | 5 | 41/45 | 28 | fight | goo: attack | Goo |
| 2 | DHX-OSY-AQY/1059 | 5 | 28/40 | 28 | fight | goo: attack | Goo |
| 3 | IMM-IFY-LVO/1019 | 5 | 30/45 | 27 | fight | goo: attack | Goo |
| 4 | GHQ-AMX-CXS/3047 | 5 | 45/45 | 26 | fight | goo: attack | Goo |
| 5 | TTH-ZIW-EYP/1005 | 5 | 25/50 | 25 | fight | goo: attack | Goo |
| 6 | IMM-IFY-LVO/1019 | 5 | 34/45 | 25 | fight | goo: attack | Goo |
| 7 | DMI-XZZ-OAY/3026 | 5 | 25/45 | 25 | fight | goo: attack | Goo |
| 8 | LGW-GOS-QSG/1051 | 5 | 28/45 | 23 | fight | goo: attack | Goo |
| 9 | OJY-RSM-XQU/1060 | 5 | 36/40 | 22 | fight | goo: attack | Goo |
| 10 | DLG-RPU-AXM/1002 | 5 | 22/45 | 21 | fight | goo: attack | Goo |
| 11 | IMM-IFY-LVO/1019 | 5 | 43/45 | 20 | fight | goo: attack | Goo |
| 12 | BAM-QZW-WDL/1066 | 5 | 20/45 | 20 | fight | goo: attack | Goo |
| 13 | RGP-OOJ-NSS/3035 | 5 | 20/40 | 20 | fight | goo: attack | Goo |
| 14 | DLG-RPU-AXM/1002 | 5 | 45/45 | 18 | fight | goo: attack | Goo |
| 15 | DMI-XZZ-OAY/1026 | 5 | 44/50 | 18 | fight | goo: attack | Goo |
| 16 | WRR-YQT-EVQ/1036 | 5 | 35/40 | 18 | fight | goo: attack | Goo |
| 17 | IMM-IFY-LVO/1019 | 5 | 34/45 | 17 | fight | goo: attack | Goo |
| 18 | CSX-MOL-BIN/1008 | 5 | 37/50 | 16 | fight | goo: attack | Goo |
| 19 | TTH-ZIW-EYP/1005 | 5 | 40/50 | 15 | fight | goo: attack | Goo |
| 20 | KLF-BYB-RCZ/1023 | 5 | 35/45 | 15 | fight | goo: attack | Goo |

#### ROGUE

| rank | seed/salt | depth | hp | lost | policy | why | enemies in view |
|---|---|---|---|---|---|---|---|
| 1 | TFB-WHU-CJY/1024 | 5 | 34/35 | 30 | fallback | uniform 1/9 | - |
| 2 | TFB-WHU-CJY/3024 | 5 | 34/35 | 30 | fallback | uniform 1/9 | - |
| 3 | SVN-TMV-HUF/1020 | 5 | 45/45 | 24 | fight | goo: attack | Goo |
| 4 | OBX-KJK-OHI/1022 | 5 | 32/45 | 24 | fight | goo: attack | Goo |
| 5 | AOW-WTU-NTE/3065 | 5 | 40/50 | 24 | fight | goo: attack | Goo |
| 6 | EHD-JKF-AIN/3011 | 5 | 31/45 | 23 | fight | goo: attack | Goo |
| 7 | RPP-BRV-TEO/3078 | 5 | 41/45 | 23 | fight | goo: attack | Goo |
| 8 | WWR-YJK-ISA/3039 | 5 | 36/50 | 22 | fight | goo: attack | Goo |
| 9 | WWR-YJK-ISA/3039 | 5 | 27/50 | 22 | fight | goo: attack | Goo |
| 10 | RPP-BRV-TEO/3078 | 5 | 33/45 | 22 | fight | goo: attack | Goo |
| 11 | WTT-QQM-EGF/1060 | 5 | 17/40 | 17 | fight | goo: attack | Goo |
| 12 | XLC-WCY-QNR/3051 | 5 | 37/45 | 17 | fight | goo: attack | Goo |
| 13 | PMQ-ALX-FBE/1071 | 5 | 41/50 | 15 | fight | goo: attack | Goo |
| 14 | WWR-YJK-ISA/3039 | 5 | 36/50 | 15 | fight | goo: attack | Goo |
| 15 | WWR-YJK-ISA/3039 | 5 | 29/50 | 15 | fight | goo: attack | Goo |
| 16 | ZYR-ZSJ-GVX/3014 | 6 | 25/55 | 14 | fight | retreat 2 | giant piranha;skeleton |
| 17 | ZTQ-SPQ-KXD/3046 | 6 | 55/55 | 14 | fight | attack: crazy thief | crazy thief;skeleton |
| 18 | XYN-STX-ECM/1057 | 7 | 18/55 | 13 | fight | cornered: prison guard | DM-100;prison guard |
| 19 | CSE-XBJ-BCM/3000 | 3 | 13/35 | 13 | explore | frontier 1 | giant piranha |
| 20 | PKZ-XPH-KHE/3033 | 6 | 17/45 | 13 | fight | cornered: crazy thief | crazy thief;skeleton |

#### HUNTRESS

| rank | seed/salt | depth | hp | lost | policy | why | enemies in view |
|---|---|---|---|---|---|---|---|
| 1 | WXC-BLV-ZJW/3059 | 5 | 44/45 | 39 | fallback | uniform 1/9 | - |
| 2 | AGT-TUB-RBZ/1049 | 5 | 35/45 | 27 | fight | goo: attack | Goo |
| 3 | AGT-TUB-RBZ/3049 | 5 | 27/40 | 27 | fight | goo: attack | Goo |
| 4 | CDC-GBY-WZL/1036 | 5 | 50/50 | 26 | fight | dodge: pump | Goo |
| 5 | JQL-UUV-EXG/1065 | 5 | 24/45 | 23 | fight | dodge: pump | Goo |
| 6 | GFH-YYC-DBH/3033 | 5 | 39/45 | 23 | fight | goo: attack | Goo |
| 7 | ARM-YWD-PBC/1051 | 5 | 40/40 | 22 | fight | goo: attack | Goo |
| 8 | CDC-GBY-WZL/3036 | 5 | 27/45 | 22 | fight | goo: attack | Goo |
| 9 | AGT-TUB-RBZ/1049 | 5 | 32/45 | 20 | fight | goo: attack | Goo |
| 10 | GHF-BBY-OMQ/3011 | 5 | 33/45 | 20 | fight | goo: attack | Goo |
| 11 | ZFI-BCP-YRL/1066 | 5 | 36/45 | 19 | fight | goo: attack | Goo |
| 12 | AGT-TUB-RBZ/1049 | 5 | 41/45 | 18 | fight | goo: attack | Goo |
| 13 | GFH-YYC-DBH/1033 | 5 | 27/45 | 17 | fight | goo: attack | Goo |
| 14 | LLF-NNJ-TCN/1055 | 5 | 17/45 | 17 | fight | dodge: pump | Goo |
| 15 | ZFI-BCP-YRL/1066 | 5 | 17/45 | 17 | fight | goo: attack | Goo |
| 16 | KVY-ZKV-PUZ/1076 | 5 | 23/40 | 17 | fight | goo: attack | Goo |
| 17 | XCX-ZHV-NKM/1078 | 5 | 32/45 | 17 | fight | goo: attack | Goo |
| 18 | GHF-BBY-OMQ/3011 | 5 | 38/45 | 17 | fight | goo: attack | Goo |
| 19 | CDC-GBY-WZL/1036 | 5 | 41/50 | 16 | fight | dodge: pump | Goo |
| 20 | ARM-YWD-PBC/1051 | 5 | 18/40 | 16 | fight | goo: attack | Goo |

#### DUELIST

| rank | seed/salt | depth | hp | lost | policy | why | enemies in view |
|---|---|---|---|---|---|---|---|
| 1 | CRD-NOU-BDG/3035 | 5 | 23/45 | 23 | fight | goo: attack | Goo |
| 2 | NFI-NJO-AEQ/3015 | 5 | 31/45 | 22 | fight | goo: attack | Goo |
| 3 | WYI-MWD-KVE/1010 | 5 | 25/45 | 20 | fight | goo: attack | Goo |
| 4 | XCY-CFO-PHY/1045 | 5 | 24/45 | 19 | fight | goo: attack | Goo |
| 5 | VXD-QBD-JSU/3057 | 5 | 25/45 | 19 | fight | goo: attack | Goo |
| 6 | WYI-MWD-KVE/1010 | 5 | 41/45 | 18 | fight | goo: attack | Goo |
| 7 | TPM-GRC-FYP/1062 | 5 | 26/45 | 18 | fight | goo: attack | Goo |
| 8 | BAV-TNP-CCE/3029 | 5 | 18/45 | 18 | fight | goo: attack | Goo |
| 9 | NTL-EZA-VJZ/3071 | 5 | 29/45 | 18 | fight | goo: attack | Goo |
| 10 | MUU-MIE-OUQ/3001 | 5 | 38/50 | 17 | fight | goo: attack | Goo |
| 11 | NTL-EZA-VJZ/3071 | 5 | 34/45 | 17 | fight | goo: attack | Goo |
| 12 | NTL-EZA-VJZ/3071 | 5 | 37/45 | 17 | fight | goo: attack | Goo |
| 13 | KAI-JXO-EAA/3073 | 6 | 35/55 | 17 | fight | approach 2 | crazy thief |
| 14 | XCY-CFO-PHY/3045 | 5 | 25/45 | 16 | fight | goo: attack | Goo |
| 15 | TPM-GRC-FYP/1062 | 5 | 40/45 | 15 | fight | goo: attack | Goo |
| 16 | XCY-CFO-PHY/1045 | 5 | 45/45 | 14 | fight | goo: attack | Goo |
| 17 | BDO-MZP-HPI/1070 | 5 | 33/50 | 14 | fight | goo: attack | Goo |
| 18 | BDO-MZP-HPI/1070 | 5 | 49/50 | 14 | fight | goo: attack | Goo |
| 19 | BAV-TNP-CCE/3029 | 5 | 43/45 | 14 | fight | goo: attack | Goo |
| 20 | CWY-XIN-WIS/3044 | 5 | 14/40 | 14 | fight | goo: attack | Goo |

#### CLERIC

| rank | seed/salt | depth | hp | lost | policy | why | enemies in view |
|---|---|---|---|---|---|---|---|
| 1 | GUJ-KMS-KIS/1027 | 5 | 37/45 | 31 | fight | goo: attack | Goo |
| 2 | SQZ-FNO-KUX/1010 | 5 | 28/45 | 28 | fight | goo: attack | Goo |
| 3 | DGW-BYX-VPX/1002 | 5 | 37/45 | 24 | fight | goo: attack | Goo |
| 4 | ZIR-RZA-XCM/3008 | 5 | 36/45 | 24 | fight | goo: attack | Goo |
| 5 | HIK-XQN-ERV/3056 | 4 | 40/40 | 24 | fight | attack: swarm of flies | slime;swarm of flies |
| 6 | RIE-CZA-UHJ/3023 | 5 | 26/50 | 23 | fight | goo: attack | Goo |
| 7 | YBD-TVS-LLI/3035 | 5 | 23/40 | 23 | fight | goo: attack | Goo |
| 8 | DGW-BYX-VPX/1002 | 5 | 27/50 | 22 | fallback | uniform 1/8 | - |
| 9 | CJQ-RCY-MQH/3054 | 5 | 22/45 | 22 | fight | goo: attack | Goo |
| 10 | CJQ-RCY-MQH/3054 | 5 | 23/45 | 21 | fight | goo: attack | Goo |
| 11 | HIK-XQN-ERV/1056 | 3 | 30/30 | 20 | fight | attack: gnoll scout | gnoll scout |
| 12 | DQC-LYZ-RLN/1077 | 5 | 19/45 | 19 | fight | goo: attack | Goo |
| 13 | ICD-WHC-GXS/1021 | 3 | 34/35 | 17 | answer-prompt | jump: Yes, I know what I'm doing | gnoll exile |
| 14 | RIE-CZA-UHJ/3023 | 5 | 42/50 | 16 | fight | goo: attack | Goo |
| 15 | RKO-ZSO-YFQ/3079 | 5 | 35/45 | 16 | fight | goo: attack | Goo |
| 16 | ILI-TZO-BXS/3062 | 7 | 29/50 | 15 | fight | cornered: prison guard | DM-100;prison guard;skeleton |
| 17 | JCS-SNL-SHA/1041 | 4 | 15/30 | 14 | fight | retreat 5 | slime |
| 18 | HVE-BNQ-VPM/3011 | 8 | 27/60 | 14 | fight | attack: prison guard | DM-100;prison guard |
| 19 | ARG-XQV-KNS/3012 | 2 | 22/25 | 14 | fight | approach 6 | gnoll scout |
| 20 | EEC-RMW-HVL/3024 | 2 | 28/30 | 14 | answer-prompt | jump: Yes, I know what I'm doing | gnoll exile |

The cost is the Goo. The dearest Decision of every class is on depth 5, 23 to 39 HP, and the 20th dearest still costs 13 to 16. For the
Huntress (39) and the Rogue (30) the dearest is a `fallback` Decision, the uniform choice among the legal Actions, taken because no Policy
claimed the wait; that is a hole in the Goo fight, not a cost of it. Outside depth 5 the dearest Decisions are single blows at low HP, a chasm
jump through the `answer-prompt` Policy ("jump: Yes, I know what I'm doing", the fall) and fights where the hero is `cornered`.

## Cornered

How often the last Decision's reason starts with "cornered", the Fight Policy saying it has no cell to step to.

|  | WARRIOR | MAGE | ROGUE | HUNTRESS | DUELIST | CLERIC | pooled |
|---|---|---|---|---|---|---|---|
| deaths on depths 6 to 9, last Decision says cornered | 54 of 89 (60.7%) | 18 of 35 (51.4%) | 29 of 37 (78.4%) | 49 of 65 (75.4%) | 25 of 38 (65.8%) | 17 of 30 (56.7%) | 192 of 294 (65.3%) |
| deaths on depths 1 to 9, last Decision says cornered | 87 of 160 (54.4%) | 73 of 160 (45.6%) | 112 of 160 (70.0%) | 92 of 160 (57.5%) | 98 of 160 (61.3%) | 107 of 160 (66.9%) | 569 of 960 (59.3%) |

Two of every three hero deaths on depths 6 to 9 (192 of 294, 65.3%) end in a cornered fight. That is the evidence for story 10.14 (doors,
chokepoints and the retreat upstairs), which has no killer to remove and so no row in the order below.

## What this page cannot show

- The killer table credits one cause per death. Hunger shows up here twice: as a killer, and as the state of heroes that something else
  finishes. The second is the larger.
- `+d8` below is a model, not a measurement. It takes the Runs that arrive on depths 5, 6, 7 and 8 and asks what reaching depth 8 would have been
  if the family never killed on depths 5 to 7 and the Runs it spared then met the other deaths of each depth at the rate the Runs that did arrive
  met them. It assumes the spared hero is as healthy as the average arrival, which a starving or low-HP hero is not, so it is a ceiling for a
  rule that works, and no rule is promised by a row. There is no figure for depth 10: no Run arrives on depth 9.
- The 10.8 and 10.9 pages read the deaths one by one and found no rule that fires differently from `main` and wins. The ceilings below are
  therefore the ceiling for the whole family's removal by any means (food, retreat, position), not for a burst rule or a lightning rule.
- Two slices of 80 per class is one measurement of the current Brain; a class row of 160 is wide. A difference of two Runs is noise.

## The order of the mob stories

The recommendation of this PR, which the product owner may overrule on it. The table is the model above, ordered by the pooled `+d8` (ties keep
the story order); "deaths" counts deaths on depths 5 to 9 to the family. After it, the order I recommend building in and why.

| story | family | killers counted | deaths WARRIOR | deaths MAGE | deaths ROGUE | deaths HUNTRESS | deaths DUELIST | deaths CLERIC | deaths pooled | +d8 WARRIOR | +d8 MAGE | +d8 ROGUE | +d8 HUNTRESS | +d8 DUELIST | +d8 CLERIC | +d8 pooled |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 10.8 | Skeleton bone burst | Skeleton | 34 | 16 | 23 | 39 | 19 | 10 | 141 | +18.4 | +11.3 | +15.0 | +20.3 | +8.8 | +4.0 | +77.8 |
| 10.9 | DM-100 lightning | DM100 | 14 | 6 | 1 | 6 | 1 | 5 | 33 | +11.0 | +4.0 | +1.0 | +5.0 | +1.0 | +4.0 | +26.0 |
| 10.10 | Guard chain pull | Guard | 12 | 3 | 1 | 2 | 3 | 3 | 24 | +9.0 | +3.0 | +1.0 | +1.0 | +3.0 | +2.0 | +19.0 |
| 10.15 | Prison food budgeting | Hunger | 14 | 5 | 8 | 9 | 7 | 7 | 50 | +2.0 | +1.9 | +0.0 | +3.5 | +4.5 | +2.9 | +14.8 |
| (none open) | Goo on depth 5 | Goo | 15 | 30 | 17 | 27 | 28 | 9 | 126 | +1.0 | +2.6 | +0.0 | +1.2 | +0.7 | +0.9 | +6.5 |
| 10.11 | Thief and Bandit steal | Thief, Bandit | 4 | 0 | 4 | 6 | 1 | 3 | 18 | +0.6 | +0.0 | +0.0 | +1.2 | +0.1 | +1.7 | +3.6 |
| 10.13 | Swarm split | Swarm | 1 | 0 | 1 | 0 | 0 | 0 | 2 | +0.2 | +0.0 | +0.0 | +0.0 | +0.0 | +0.0 | +0.2 |
| 10.12 | Necromancer priority | Necromancer, Necromancer$NecroSkeleton | 0 | 0 | 0 | 0 | 1 | 1 | 2 | +0.0 | +0.0 | +0.0 | +0.0 | +0.0 | +0.0 | +0.0 |

The recommended order of work, with the reach each would buy if it removed its killer entirely (the `+d8` of the row):

1. Story 10.38 (new, #422), the Goo fight on depth 5. It is not in the table's top rows because the Prison kills what the Goo lets through
   (`+6.5` Runs to depth 8), but it gates all of them: 126 Runs die there, 42.9% of the 294 that reach depth 6, and the Goo kills hungry
   heroes (91 of 126). Without it every Prison number is measured on the survivors.
2. Story 10.39 (new, #423), the second pass on food. 72.8% of the heroes that die on depths 6 to 9 were STARVING and a further 13.9% HUNGRY. The row
   of 10.15 (`+14.8`) understates it because the Skeleton and the Guard take the credit for starving heroes; the 10.8 row (`+77.8`) is largely this.
3. Story 10.10, the Guard chain pull: `+19.0` Runs to depth 8 (Warrior +9.0). The largest row that is still open and that names a mob.
4. Story 10.14, doors, chokepoints and the retreat upstairs: no killer to remove; 65.3% of deaths on depths 6 to 9 end cornered (Rogue 78.4%,
   Huntress 75.4%). The general lever behind the Skeleton and DM-100 rows, which no mob rule could take.
5. Story 10.40 (new, #424), the Sewers killers. Crab, Gnoll and Snake are 259 deaths, 27.0% of all 960, all on depths 1 to 4; the reach of depth 5
   (47.0%) is the base of every number above.
6. Story 10.11, the Thief and Bandit steal: `+3.6` (Huntress +1.2, Cleric +1.7), 18 deaths (Thief only; no Bandit death).
7. Story 10.13, the Swarm split: `+0.2` on depths 6 to 9 (2 deaths), 69 of its 71 deaths are on depths 1 to 4, so its reach is the Sewers' and
   the story belongs with 10.40.
8. Story 10.16, the Wandmaker quest: no killer; a ranged option, which matters for the classes whose deaths are in melee. Nothing here ranks it.
9. Story 10.12, the Necromancer priority: `+0.0`, 2 deaths in 960 Runs (the Necromancer's skeleton, depth 8). Last, and a candidate to drop.

Story 10.8 (Skeleton bone burst, `+77.8`) and Story 10.9 (DM-100 lightning, `+26.0`) are done and built no rule (#354, #355; pages
`2026-10-28-10-8-skeleton-deaths.md` and `2026-10-29-10-9-dm100-lightning.md`); their rows are the biggest in the table and are a reason to read
10.14, 10.38 and 10.39 as the way to those Runs, not a promise of a rule. Story 10.15 (Prison food budgeting, `+14.8`) is done and kept one rest
rule; 10.39 is its next step.
