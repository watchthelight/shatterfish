# Goo baseline on Profile 6: 120 Warrior Runs at 7b1212eea

**A published baseline measurement, not tuning, not an acceptance.** Nothing here ran under a Registration and no
sequential test was run. It is the Profile 6 replacement for the [Goo baseline at 5b48107f9](2026-10-25-goo-baseline-5b48107f9.md)
(issue #234, step 2 of #219), whose numbers were measured before the Run's generator changed and are void (issue
#240, then #258). It was run exactly as #234 ran: the same two commands, the same set and salts, `--debug-record` on.
No Brain code was changed because of it, and no individual Goo Run was read for ideas; the page reports aggregates only
and has no per-fight table.

Shatterfish `main` at `7b1212eea`, upstream `v4.0.2`, Profile 6, the `shatterfish` Brain on `weights/shatterfish.json`,
Oracle off, turn cap 20,000. Both batches were played on 2026-10-26 on one machine, one checkout, no baseline side.

## Commands

```sh
./gradlew :rig:direction --args="--set standard --class WARRIOR --first 40 --salt-base 1000 --debug-record --jobs 6 --out build/direction/p6-ga"
./gradlew :rig:direction --args="--set standard --class WARRIOR --first 80 --salt-base 3000 --debug-record --jobs 6 --out build/direction/p6-gb"
```

The first batch is the tuning set (40 Warriors of `standard`, salts 1000 to 1039), the second the first 80 Warriors under
salts 3000 to 3079; the 120 Runs are 120 distinct Runs (see the [tuning tables](2026-10-26-profile6-tuning-7b1212eea.md)).
The numbers below were computed from the Run logs and their `*.debug.ndjson` records by a scratch script outside the
repository, which reads only the files the two commands wrote. All 120 Run logs carry profile 6. The `goo` and `holdout`
sets were not touched.

## Headline

| | 120 Runs | 95% interval (Wilson) |
|---|---|---|
| reach depth 5 | 59/120 = 49.2% | 40.4% to 58.0% |
| Goo kills | 44/120 = 36.7% | 28.6% to 45.6% |
| Goo fights | 59 | |
| conversion, kills / fights (= kills / reach) | 44/59 = 74.6% | 62.2% to 83.9% |
| median turns | 1821.5 | quartiles 1221.0 and 2397.0 |
| deaths | 120 of 120 (no Run hit the turn cap) | |

Batches on their own: salts 1000 to 1039 reach depth 5 in 24/40, kill Goo 17/40, median turns 1940.5; salts 3000 to
3079 reach depth 5 in 35/80, kill Goo 27/80, median turns 1807.0.

A **Goo fight** is a Run in which Goo was in view on depth 5 in at least two waits, or was killed. Every one of the 59
Runs that reached depth 5 was a fight under that rule (none arrived and died at once), so conversion equals kills over
reach here. Of the 59 fights, 44 ended with Goo dead, 12 with the hero dead to Goo and 3 with the hero dead to the
caustic ooze Goo left on it. (Four deaths are credited to Ooze on depth 5 in total; the fourth is a Run in which Goo was
already dead, which counts as a kill.)

Deaths by depth, 1 to 8: 5 / 12 / 22 / 22 / 18 / 23 / 14 / 4.

For orientation only: the voided page measured 27.5% reach and 14.2% Goo kills on Profile 5. These Profile 6 numbers
are their own baseline, not that page's numbers moved, and the two are not compared.

## What the fights arrived with

Over the 59 fights: mean HP on arrival 35.3 (76% of maximum), 6.9 pumps seen (waits whose log holds "Goo is pumping
itself up!"), 1.8 potions of healing held on arrival, and the hero stood on water in 43% of the waits with Goo in view
and Goo in 58%. Hunger on arrival: 29 starving, 23 hungry, 7 not hungry.

Fights split by what the hero arrived with (killed / fights):

| split | yes | no |
|---|---|---|
| starving on arrival | 20/29 | 24/30 |
| HP at least 80% on arrival | 26/32 | 18/27 |
| held a potion of healing | 43/50 | 1/9 |
| Goo on water in at least 25% of waits | 39/50 | 5/9 |
| any caustic ooze on the hero while Goo in view | 31/42 | 13/17 |

These are descriptive. The cells are small (several have a spread of well over 20 percentage points), the splits
overlap, and none was chosen in advance; they say what the baseline looks like, not what to change.

## Deaths by killer

120 deaths. Killer is the cause class of the ranking record the death added.

| killer | deaths | by depth |
|---|---|---|
| Hunger | 19 | d1 1, d2 3, d3 4, d4 3, d5 2, d6 4, d7 2 |
| Skeleton | 15 | d6 12, d7 3 |
| Goo | 12 | d5 12 |
| DM100 | 10 | d7 7, d8 3 |
| Crab | 10 | d3 7, d4 3 |
| Piranha | 7 | d3 3, d4 3, d6 1 |
| SentryRoom$Sentry | 7 | d1 1, d2 2, d4 4 |
| Swarm | 6 | d3 1, d4 5 |
| Snake | 6 | d1 3, d2 2, d3 1 |
| Bomb | 5 | d2 1, d3 2, d4 1, d6 1 |
| Gnoll | 4 | d2 1, d3 3 |
| Ooze | 4 | d5 4 |
| Thief, Poison | 2 each | |
| Mimic, Rat, Bleeding, Necromancer$NecroSkeleton, Slime, HermitCrab, Burning, GnollExile, Guard, ToxicGas, Bandit | 1 each | |

## What this variance allows

The Goo kill rate is 36.7% with a standard error of 4.4 percentage points at 120 Runs. A comparison of two independent
arms of 120 has a standard error of 6.2 points on the difference, so a change worth +5 points is a 0.8 sigma effect: not
visible.

For +5 points of Goo kills over a 36.7% baseline (36.7% to 41.7%), a two-sided test at 5% with 80% power needs about
**1,490 Runs per arm** if the arms are independent (90% power: about 2,000). The rate is nearer one half than the
voided page's 14%, so the binomial spread is larger and the sample a +5 point claim needs is larger, not smaller. Paired
arms (the same seed and salt, which share a dungeon until the change makes them diverge) should correlate and cut that;
how much has not been measured on this Profile. A change that moves a mechanism the debug record can see should be
judged on that mechanism first, and on Goo kills only at that sample size.

## Reproducing

The Run logs and debug records are in `build/direction/p6-ga/current` and `p6-gb/current` of the checkout that played
them (about 1.8 MB a record, not committed). Re-running the two commands at `7b1212eea` on the same upstream tag
reproduces the Runs, since a Run is determined by tag, seed and action list; both batches also reproduce the "change"
columns of the [no-outrunning page](2026-10-26-219-no-outrunning-fast-enemies.md) exactly.
