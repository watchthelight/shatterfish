# Simulator speed: what a redetermined rollout costs

**ADR-0010's first measurement** ("Simulator speed"), made for story 6.4 ([#251](https://github.com/watchthelight/shatterfish/issues/251)).
It asks how many redetermined rollouts a second the swap-in-place host of [ADR-0009](../adr/0009-snapshot-restore-and-redetermination.md)
can run in one process, so that [ADR-0010](../adr/0010-tactical-search-deferral-criteria.md) can say whether sampled search fits
any budget at all. It is a rate on one machine, not an acceptance, and no number here was compared with a target set in advance
(NFR-3).

Shatterfish `main` at `a8aa3589d` with this story's change in the working tree (the `RolloutHost` phase listener and the
measurement); upstream `v4.0.2`; the `smoke` set; the real Brain (`weights/shatterfish.json`) finds the snapshots.

## Command

From the repository root, three times in a row, one process and one thread each:

```sh
./gradlew :harness:simulatorSpeed -Psimulator.args="--set smoke --seeds 25 --per-seed 3 --samples 4"
```

The defaults of the task are the same configuration with `--per-seed 2 --samples 3`; the three runs used the figures above. The
reports are attached unchanged: [run 1](logs/2026-10-26-simulator-speed-run1.txt), [run 2](logs/2026-10-26-simulator-speed-run2.txt),
[run 3](logs/2026-10-26-simulator-speed-run3.txt). Each plays the 25 `smoke` Runs with the Brain until it has taken up to three
mid-fight snapshots (a wait with an enemy in view, at least 40 waits from the last one, at a wait the scrub accepts). At each
snapshot it scrubs four samples (after four discarded warm-up samples in all) and rolls each out at 2, 3 and 4 Actions. The
line is the Brain's own: the Action it would choose at the snapshot's wait, then at each wait the scrubbed copy reaches (planned
untimed on the same copy, so the timed rollout plays the same line). Every run measured **73 snapshots, 289 scrubbed samples and
867 rollouts**; two Runs ended by the hero's death before their third snapshot. In all 867 rollouts every Action of the line was
applied: none ended in a death, a refusal or an early horizon.

`SimulatorSpeedSmokeTest` runs the same code in a tiny configuration and pins its structure (the five cost rows, the counts, the
percentile definitions), not its timings.

**Machine.** Intel Core Ultra 9 275HX (24 logical processors), Windows 11 Pro 10.0.26200, Azul Zulu OpenJDK 21.0.11, Gradle 9.5.0.
**The machine was shared and not idle in any run.** "Gradle" counts are of `gradle-wrapper.jar` processes machine-wide, and include
this measurement's own one.

| Run | Time | Gradle processes | Java processes | Processor load, sampled every 16 s | System load at the 73 snapshots (min / mean / max) |
|---|---|---|---|---|---|
| 1 | 08:42 to 08:44 | 1 to 2 | 4 to 6 | 14% to 40% | 0% / 24% / 60% |
| 2 | 08:44 to 08:46 | 1 to 2 | 4 to 8 | 5% to 42% | 0% / 25% / 88% |
| 3 | 08:46 to 08:49 | 1 to 2 | 8 to 32 | 27% to 100% (mostly above 60%) | 0% / 56% / 100% |

Runs 1 and 2 saw a lightly loaded machine; run 3 overlapped other agents' builds and Rigs (up to 32 Java processes at once) and
is the slower run for it. The headline figures are run 1's; run 3 says what the same costs look like when the machine is busy.
An isolated machine should be at least as fast as run 1.

## The five costs

Milliseconds, one process, run 1 (n is the number of timings behind the row; a scrub is made once per sample and shared by
the three horizons, and a rollout is timed once per sample per horizon).

| Cost | Horizon | n | median | 95th percentile |
|---|---|---|---|---|
| Taking a snapshot (the live Run's save, the statics and the profile's files held) | | 867 | 7.516 | 9.584 |
| Scrubbing it | | 289 | 3.232 | 4.242 |
| Loading the scrubbed copy (restore and step to its wait) | | 867 | 15.236 | 18.149 |
| Rolling out | 2 Actions | 289 | 1.905 | 3.400 |
| Rolling out | 3 Actions | 289 | 2.633 | 4.323 |
| Rolling out | 4 Actions | 289 | 3.287 | 5.598 |
| Restoring the live Run (restore, step to its wait, statics and profile put back) | | 867 | 18.101 | 22.489 |

"Actions" are the Brain's Actions, not game turns: an Action may take several turns (a walk, a rest, a search), and the fight
lines here were short. They are the unit the search budgets in, which is why the horizon is stated in them.

The same table across the three runs (median / 95th percentile, ms):

| Cost | Run 1 | Run 2 | Run 3 (busy) |
|---|---|---|---|
| snapshot | 7.52 / 9.58 | 7.81 / 11.58 | 10.58 / 17.79 |
| scrub | 3.23 / 4.24 | 3.29 / 4.29 | 4.19 / 8.15 |
| load | 15.24 / 18.15 | 15.50 / 21.65 | 21.45 / 36.18 |
| rollout, 2 Actions | 1.91 / 3.40 | 1.86 / 3.58 | 2.52 / 4.98 |
| rollout, 3 Actions | 2.63 / 4.32 | 2.58 / 4.86 | 3.47 / 6.68 |
| rollout, 4 Actions | 3.29 / 5.60 | 3.17 / 5.86 | 4.35 / 9.30 |
| restore | 18.10 / 22.49 | 18.51 / 25.48 | 25.25 / 44.68 |

## Redetermined rollouts per second per process

One rollout is a snapshot, a load, the rollout and a restore, plus a scrub. A search may scrub once per determinization and roll
out several times from the copy, so the scrub is shown paid once per rollout (the worst case) and once per eight. "Measured end
to end" is the number of rollouts over the summed time of their own pipelines with one scrub each, not a ratio of medians.

| Horizon | Run | scrub per rollout, medians | scrub per 8 rollouts, medians | scrub per rollout, 95th percentiles | measured end to end |
|---|---|---|---|---|---|
| 2 Actions | 1 | 21.74 | 23.17 | 17.28 | 21.35 |
| 3 Actions | 1 | 21.41 | 22.78 | 17.01 | 20.88 |
| 4 Actions | 1 | 21.11 | 22.45 | 16.65 | 20.44 |
| 2 Actions | 2 | 21.29 | 22.68 | 15.02 | 20.58 |
| 3 Actions | 2 | 20.97 | 22.32 | 14.73 | 20.35 |
| 4 Actions | 2 | 20.72 | 22.03 | 14.52 | 19.87 |
| 2 Actions | 3 (busy) | 15.63 | 16.58 | 8.95 | 13.64 |
| 3 Actions | 3 (busy) | 15.40 | 16.32 | 8.81 | 13.57 |
| 4 Actions | 3 (busy) | 15.19 | 16.09 | 8.61 | 13.05 |

**About 21 redetermined rollouts per second per process** on a lightly loaded machine (20 to 22 across the horizons and the two
quiet runs), about 15 on a busy one. For scale only: the Brain plays about 95 Input waits a second per process
([the Brain's throughput page](2026-10-26-throughput-7b1212eea.md)), so one rollout costs about as much as four or five waits
of ordinary play.

## Which cost dominates

Not the rules. Rolling the engine forward for two to four Actions is 1.9 to 3.3 ms of a 46 to 47 ms rollout, 4% to 7%. The
three costs of **swapping the Run in and out are 40.9 ms, 87% of the cycle at three Actions**, and the single largest cost is
the **restore of the live Run (18.1 ms, 39%)**, then the load of the copy (15.2 ms, 32%), then the snapshot (7.5 ms, 16%).
Both loads and the restore go through the game's own save and load (`Dungeon.saveGame`, `Dungeon.loadGame`) and the
step to the wait, which is what ADR-0009 chose to keep (no second implementation of the game's rules).

| Horizon | dominant cost | its share | rate as measured | rate if it were zero | rate if all three swap costs were zero |
|---|---|---|---|---|---|
| 2 Actions | restore, 18.1 ms | 39% | 21.74/s | 35.86/s | 194.7/s (5.14 ms) |
| 3 Actions | restore, 18.1 ms | 39% | 21.41/s | 34.94/s | 170.5/s (5.87 ms) |
| 4 Actions | restore, 18.1 ms | 38% | 21.11/s | 34.16/s | 153.4/s (6.52 ms) |

This is ADR-0010's pre-mortem ("snapshot cost may hide a viable design") answered in the direction it feared: the rules run
in a few milliseconds and the process spends nearly all of its time moving the Run's state. Removing the dominant cost alone
buys a factor of about 1.6; a host that did not pay the swap at all (the isolated, second-instance design of ADR-0009's
option 12, or a snapshot that is cheaper than a save) would be about 7 to 9 times faster. Neither is claimed here: the second
figure is arithmetic on medians, not a design.

## What fits a per-Decision budget

No Registration sets one: the six committed (`H-0001` to `H-0007`) all carry `budget_ms` 0, "the comparison does not constrain
thinking time", and the Overlay has no per-Decision budget written down. So the table below gives the answer for budgets from
10 ms to 5 s and does not pick one. It is the median costs of run 1, one search thread in the Run's own process (the host
must be called from the thread that plays the Run), the scrub paid once per determinization. "R" is rollouts per
determinization; each cell is determinizations times R, with the total.

| budgetMs | rollouts that fit, 2 Actions | 3 Actions | 4 Actions | R=1 | R=4 | R=16 |
|---|---|---|---|---|---|---|
| 10 | none | none | none | none | none | none |
| 25 | none | none | none | none | none | none |
| 50 | 1 | 1 | 1 | 1 x 1 = 1 | none | none |
| 100 | 2 | 2 | 2 | 2 x 1 = 2 | none | none |
| 250 | 5 | 5 | 5 | 5 x 1 = 5 | 1 x 4 = 4 | none |
| 500 | 10 | 10 | 10 | 10 x 1 = 10 | 2 x 4 = 8 | none |
| 1000 | 21 | 21 | 21 | 21 x 1 = 21 | 5 x 4 = 20 | 1 x 16 = 16 |
| 2000 | 43 | 42 | 42 | 42 x 1 = 42 | 11 x 4 = 44 | 2 x 16 = 32 |
| 5000 | 108 | 107 | 105 | 107 x 1 = 107 | 28 x 4 = 112 | 7 x 16 = 112 |

**The answer is none for any budget under about 47 ms** (one rollout's whole cycle at three Actions), and for 100 ms it is
two rollouts, which is not a search. A budget of a second buys about 21 rollouts, which is five determinizations of four. Whether
any of these is enough to choose better Actions is not this page's question: that is the properties measurement (story 6.5)
and the candidate comparison (story 6.6), and the budget they are run at is a Registration field set there, with the machine
named. On a busy machine (run 3) every count above is about a quarter lower.

## Classloader isolation

The row for isolated engine instances is not reported, because the E1 classloader-isolation spike was not completed and cannot
be inside this story: ADR-0009's JNI note records that the JNI specification forbids loading libGDX's native library into more
than one class loader, so an isolated instance needs the shared-parent-loader split that the spike would first have to build.

## Not measured here

- **Hardware that is not this machine.** Nothing here transfers to another CPU or a slower disk; the save and load are file
  operations on the Run's profile folder.
- **Long lines.** The horizon is 2 to 4 Actions (FR-34), and in these fights every line played all its Actions; deeper
  lines, or fights where the hero dies inside the horizon, were not met.
- **Fights on boss floors and with a quest NPC in view,** which the scrubber refuses (story 6.2), are not among the snapshots.
- **Contention between processes.** One process was timed at a time; the Rig's pool shares a disk and a memory bus across
  processes, which run 3 shows only in part.
- **The Brain's own cost of choosing** the Actions of the line, which is not part of the host.
