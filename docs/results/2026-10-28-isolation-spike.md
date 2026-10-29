# Isolation spike: what a rollout costs in a second copy of the game

Story 10.23 (#369), epic E10, workstream W4. Measured by `./gradlew :harness:isolationSpeed` (the tool is
`IsolationSpeed` in the harness tests). The decision this feeds is [ADR-0025](../adr/0025-classloader-isolated-rollout-world.md).

Two runs of the same command at the same commit, one after the other. The **first (below) is unthrottled**: 24 logical
processors, normal priority, though other builds were running (mean system CPU 78%). The second, at the end, was made while
the product owner was using the machine, with every java process pinned to 8 cores at idle priority, and is kept because it
shows the ratio does not move when the absolute times are five times worse. In each run the swap host and the isolated host
are timed in the same process on the same scrubbed copies and the same Action lines, interleaved, so **the ratio between them
is the headline**; absolute milliseconds depend on the machine and are not comparable with story 6.4's except as ratios.

## Environment

- Machine: amd64, 24 logical processors, Windows 11 10.0
- JDK: Azul Systems, Inc. 21.0.11 (OpenJDK 64-Bit Server VM)
- Commit: 11ae2627707112e8da80dc082f109b07d742a771
- Command: `./gradlew :harness:isolationSpeed -Pisolation.args="--set standard --seeds 8 --per-seed 3 --samples 3 --gap 40 --wait-limit 3000 --warmup 3 --fresh 6"`
- Configuration: set `standard`, 8 seeds, up to 3 mid-fight snapshots each, 3 samples per snapshot, horizons 2, 3 and 4 Actions, 3 warm-up samples discarded, 6 fresh-world rollouts
- Measured: 22 snapshots, 162 warm swap rollouts, 162 warm pooled isolated rollouts, 6 fresh-world rollouts
- System CPU load at the snapshots: min 0%, mean 78%, max 100%
- Note: seed 5367700046830 ended (HERO_DEAD) after 1 snapshots

## Correctness: the same scrubbed copy and Actions through both hosts

207 pairs of results compared (swap host against pooled isolated, and against fresh isolated); **11 differ**.

## The cost of one rollout (median / 95th percentile, ms)

| Horizon | swap: snapshot | load | rollout | restore | **swap total** | isolated pooled: hold | load | play | restore | **pooled total** | speed-up |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 2 | 12.129 | 25.985 | 3.500 | 29.734 | **72.340 / 85.261** | 1.379 | 29.031 | 5.113 | 2.544 | **38.573 / 59.169** | 1.9x |
| 3 | 12.584 | 25.511 | 4.717 | 29.569 | **74.445 / 89.700** | 1.409 | 28.613 | 7.170 | 2.572 | **40.528 / 51.064** | 1.8x |
| 4 | 12.129 | 25.937 | 6.847 | 30.166 | **76.867 / 97.652** | 1.346 | 28.558 | 10.442 | 2.526 | **43.731 / 68.632** | 1.8x |

## Where the isolated load goes (pooled, median / 95th percentile, ms)

| End the old scene | write the files | load the game and floor | build the new scene | step to the first wait | the whole load |
|---|---|---|---|---|---|
| 0.43 / 2.26 | 4.17 / 5.07 | 14.82 / 18.01 | 6.47 / 8.83 | 1.55 / 4.39 | 28.82 / 36.42 |

The scrub (the same for both hosts, in the live world): median 5.299 ms, 95th percentile 8.679 ms.

## Setup: opening a world

| What | n | median (ms) | 95th percentile (ms) |
|---|---|---|---|
| open a world (loader, boot, Run start, first floor), first of a pooled host, JVM already warm for the live game | 1 | 2150.1 | 2150.1 |
| the first rollout in a freshly opened world (classes still loading, code cold) | 1 | 266.4 | 266.4 |
| the first 5 rollouts of a world after its first (cold code), per rollout | 30 | 53.8 | 214.6 |
| a fresh world per rollout: open | 6 | 1692.2 | 1898.1 |
| a fresh world per rollout: open plus the 3-Action rollout | 6 | 1901.5 | 2246.0 |

Classes defined by one pooled world after its rollouts: 1482.

## The cost of a simulated turn (pooled world, lines of waits, play phase only)

| Line | rollouts | played in full | median play (ms) | per turn, full lines (ms) |
|---|---|---|---|---|
| 1 waits | 54 | 54 | 2.178 | 2.178 |
| 2 waits | 54 | 54 | 4.039 | 2.019 |
| 4 waits | 54 | 54 | 6.894 | 1.723 |
| 8 waits | 54 | 54 | 12.226 | 1.528 |
| 16 waits | 54 | 41 | 24.524 | 1.571 |

The play phase of a line includes the observation of each wait reached (the Observer), which the swap host's rollout phase includes too.

## Memory: worlds held open at once (after three collections)

| Worlds | heap used (MiB) | metaspace used (MiB) | loaded classes | committed virtual memory (MiB) | classes defined by the newest world |
|---|---|---|---|---|---|
| 0 | 11.6 | 34.7 | 6945 | 290 | -  |
| 1 | 13.5 | 42.7 | 8324 | 266 | 1370 |
| 2 | 15.4 | 50.7 | 9702 | 286 | 1370 |
| 3 | 17.4 | 58.7 | 11080 | 326 | 1370 |

## Rollouts per second per process (median costs, one scrub per rollout, horizon 3)

| Host | ms per rollout | rollouts per second |
|---|---|---|
| swap in place | 79.7 | 12.5 |
| isolated, pooled | 45.8 | 21.8 |
| isolated, pooled, one scrub shared by 8 rollouts | 41.2 | 24.3 |
| isolated, a fresh world per rollout | 1906.8 | 0.52 |

## What the least searches of story 6.6 cost with each host (median costs)

The shapes are story 6.6's: option 3 is 3 candidate Actions, one rollout each, in 2 worlds (6 rollouts, 2 scrubs); option 5 is 9 leaves in 2 worlds (18 rollouts, 2 scrubs); option 4 is 100 iterations (100 scrubs and 100 rollouts).

| Option | rollouts | swap host (ms) | isolated pooled (ms) |
|---|---|---|---|
| 3 | 6 | 457 | 254 |
| 5 | 18 | 1351 | 740 |
| 4 | 100 | 7974 | 4583 |

## Reading the tables

- **Speed-up, pooled isolated against swap in place: 1.8 to 1.9 times unthrottled (1.7 to 2.2 throttled).** The gain is the
  snapshot and the restore of the live world (about 42 ms of a 74 ms swap rollout). Isolation does not remove the load of the
  scrubbed copy (write the files, load the game and the floor, build the scene, step to the first wait), 29 ms in the pooled
  host, which is 70% of its 41 ms rollout. Story 6.4 measured the load at 15 ms; here it is twice that, so ADR-0010's estimate
  that without the swap the least option 3 search costs about 22 ms was too low: it costs 254 ms pooled against 457 ms swap.
- **A world costs 2.2 s to open and 0.27 s for its first rollout** (1.7 s to open a fresh one when warm). One world per hero class,
  opened once, is the usable shape; a fresh world per rollout is 0.5 rollouts per second.
- **Memory is cheap:** each world adds about 2 MiB of heap, 8 MiB of metaspace and 1370 loaded classes.
- **A simulated turn costs 1.5 to 2 ms** in a pooled world (7 to 10 ms throttled), flat in the length of the line, so a long
  line pays for its turns and not for the load.
- **Correctness:** 196 of 207 compared results are identical in every Observation section, in both runs. The 11 that differ are
  all in the `log` section only: same end, same length, every other section equal. They come from two snapshots (a DUELIST at
  turn 252 and a CLERIC at turn 140) and the same ones both times, so it is deterministic. The cause is not found; the likely
  suspects are the static log buffer and the journal's guidebook state, which a pooled world does not reset the way a fresh
  boot does. The fully observed equivalence test (`IsolatedWorldTest`, 53 Actions, every Observation equal including the log)
  passes, so the divergence needs a position with the log already scrolled. It is recorded as an open defect.

## The throttled run

### Environment

- Machine: amd64, 8 logical processors, Windows 11 10.0
- JDK: Azul Systems, Inc. 21.0.11 (OpenJDK 64-Bit Server VM)
- Commit: 11ae2627707112e8da80dc082f109b07d742a771
- Command: `./gradlew :harness:isolationSpeed -Pisolation.args="--set standard --seeds 8 --per-seed 3 --samples 3 --gap 40 --wait-limit 3000 --warmup 3 --fresh 6"`
- Configuration: set `standard`, 8 seeds, up to 3 mid-fight snapshots each, 3 samples per snapshot, horizons 2, 3 and 4 Actions, 3 warm-up samples discarded, 6 fresh-world rollouts
- Measured: 22 snapshots, 162 warm swap rollouts, 162 warm pooled isolated rollouts, 6 fresh-world rollouts
- System CPU load at the snapshots: min 0%, mean 48%, max 64%
- Note: seed 5367700046830 ended (HERO_DEAD) after 1 snapshots

### The cost of one rollout (median / 95th percentile, ms)

| Horizon | swap: snapshot | load | rollout | restore | **swap total** | isolated pooled: hold | load | play | restore | **pooled total** | speed-up |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 2 | 46.926 | 124.469 | 24.221 | 129.989 | **342.442 / 846.576** | 1.639 | 149.602 | 34.311 | 5.036 | **203.046 / 640.144** | 1.7x |
| 3 | 57.688 | 123.120 | 29.154 | 136.566 | **433.957 / 1202.485** | 1.372 | 128.191 | 47.164 | 4.099 | **193.657 / 775.607** | 2.2x |
| 4 | 53.382 | 127.935 | 43.692 | 147.922 | **445.110 / 1353.965** | 1.486 | 118.460 | 60.593 | 4.535 | **212.959 / 557.434** | 2.1x |

### Rollouts per second per process (median costs, one scrub per rollout, horizon 3)

| Host | ms per rollout | rollouts per second |
|---|---|---|
| swap in place | 443.1 | 2.3 |
| isolated, pooled | 202.8 | 4.9 |
| isolated, pooled, one scrub shared by 8 rollouts | 194.8 | 5.1 |
| isolated, a fresh world per rollout | 8290.9 | 0.12 |

### What the least searches cost (throttled, median costs)

| Option | rollouts | swap host (ms) | isolated pooled (ms) |
|---|---|---|---|
| 3 | 6 | 2622 | 1180 |
| 5 | 18 | 7829 | 3504 |
| 4 | 100 | 44306 | 20276 |
