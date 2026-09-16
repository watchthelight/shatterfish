# E1 throughput and tactics: the numbers the roadmap rests on

Story 1.21, [#34](https://github.com/watchthelight/shatterfish/issues/34). Upstream tag `v4.0.0`
(`2bb34a4e91d29c8785a9363cad6ddfe5122b1d4f`), Shatterfish commit `8d28fab5b`.

**Machine.** Intel Core Ultra 9 275HX (24 logical processors), 31 GB, Windows 11 Pro 10.0.26200,
Zulu OpenJDK 21.0.11, Gradle 9.4, libGDX 1.14.0 headless backend. One process, one Run at a time.

**Command.** From the repository root, at the commit above:

```
./gradlew :harness:benchmark -Pshatterfish.mobile=off
```

which is `Launcher --benchmark` with its defaults, 200 Runs, 24 samples, 4 siblings, a horizon of
20 waits, seed base 31415926; every tuple it plays is (seed base + index, `WARRIOR`, salt = seed
times 31 plus 7), so the same command on the same commit plays the same Runs. The rates below are
this machine's and nobody else's; NFR-3 promises no rate before it is measured, and this page is
the measurement.

## What the numbers say

- The harness plays about **417.9 Input waits per second per process** with the random agent,
  about **376 Runs per minute** to the death of a floor-one Warrior. The `smoke` and `standard`
  seed sets of E3 are sized from this; process-per-game parallelism multiplies it by the cores.
- The Observer's read costs 81 microseconds per Observation, the codec's canonical bytes
  and hash 394, the JSON writer 833, each measured separately below. The rate above
  pays only the Observer's: the random agent's loop neither hashes nor renders. A Run that hashes
  every Observation for its log (ADR-0011) pays about 16% more per wait, and one that
  renders the JSON as well about 51% more; the writer, not the game, is the first thing
  to make faster when the Rig's logs are on.
- **Leaf correlation 0.884**: siblings of a random Run agree on whether the hero is alive
  twenty waits on, most of the time, because the hero mostly is (survival 0.934 at the
  horizon). Long et al. (research §4) put Skat and Hearts at leaf correlation 0.8 to 1.0, where
  plain redetermination loses little to a Nash player. The number says the game's short horizon
  is forgiving for a floor-one hero, and says nothing yet about a fight, which is what E6 will
  sample from.
- **Disambiguation factor 0.000**: a sample carries about 33.0 static hidden facts, and a factor
  of zero says the random agent revealed none of them: in twenty random waits it drank no unknown
  potion, read no unknown scroll, found no secret door and sprang no hidden trap. That is a floor
  set by the agent and the horizon, not the game's own rate; a Brain that searches, drinks and
  reads will move it, and E6 reads the property again with one from mid-fight snapshots. Long et
  al. put Kuhn poker at disambiguation 0, where redetermination fails; the reading here is not
  that game's, it is the random agent's. ADR-0010's choice rule reads both numbers beside the
  Rig's acceptance, and never instead of it.
- **Bias is not measured.** It is defined as the probability that a game favours one player, a
  two-player quantity with no meaning for a single hero against the dungeon.
- **The first run of this command measured a bug, not the harness.** Its report carried thirty
  thousand FreeType stack traces: under libGDX's mock graphics the back buffer is zero wide, the
  game scales every text block's font by it, and FreeType refused size zero on every render of
  the game log. The commit above fixes it (`HeadlessGraphics`, ADR-0015's amendment); the rate
  with the traces was 371.2 waits per second, and the number above is the harness's.

## Throughput

- Runs: 200, in 31.9 s of wall time, one process.
- Input waits: 13337 (417.9 per second per process).
- Runs per minute: 376.0.
- Median Run length: 1387 turns, 65 Input waits; 285118 turns in all.
- Causes: {DEATH=200}.
- Deepest floor reached, by Runs: {1=197, 2=3}.

## Costs per Observation

Over 60 waits of one Run:

| What | Microseconds | Bytes |
|---|---|---|
| `Observer.observe()` | 81.1 | |
| codec: `ObservationCodec.encode` and `hash()` | 394.4 | 30226 |
| JSON writer: `Observation.json()` | 833.4 | 29322 |

## Tactics

- Samples: 24 asked, 23 taken, 1 skipped under a window; 4 siblings, horizon 20 waits.
- Leaf correlation: 0.884 (mean pairwise agreement of siblings' payoffs).
- Survival at the horizon: 0.934 of playouts.
- Disambiguation factor: 0.000 (mean share of a sample's hidden facts revealed by the horizon; 33.0 hidden facts per sample).
- Mobs first drawn per wait during the playouts: 0.007.
- Bias: not measured; a two-player quantity.

## Definitions

- **Input waits per second.** Waits confirmed by the driver over the wall time of the batch, in
  one process; a wait is one Observation, one Decision and one Action (ADR-0013).
- **Runs per minute.** Runs to their end, death or the turn cap, over the same wall time.
- **Median Run length.** The median over the batch of the turns the game counted and of the
  waits the driver confirmed.
- **Costs.** The first Run's waits, up to 400 of them (60 here, the Run ending first),
  each read timed three ways with `System.nanoTime`: the Observer's `observe()`; the codec's
  `ObservationCodec.encode` and `hash()`, the canonical bytes and their SHA-256 (ADR-0005); the
  JSON writer's `Observation.json()`, the rendering a Run log carries (ADR-0011). Bytes are the
  canonical form's length plus the hash's, and the JSON's.
- **Leaf correlation.** From each sample, a snapshot at a windowless Input wait along a random
  Run (story 1.20), up to four distinct sibling Actions of the valid set are each played out with
  the random agent for the horizon; the payoff is 1 if the hero is alive at the horizon and 0 if
  dead, the terminal a tactical search scores at. Leaf correlation is the mean over samples of
  the share of sibling pairs whose payoffs agree, Long et al.'s "probability that sibling terminal
  nodes share a payoff" at this horizon.
- **Disambiguation factor.** The hidden facts of a sample are the static ones the oracle sidecar
  names (story 1.18): every unknown potion, scroll and ring appearance of the Run, every secret
  door, every hidden trap and every hidden mimic, each as one key. A fact is revealed when the
  sidecar no longer names it at the horizon. The factor is the mean over samples and their
  surviving playouts of the share revealed, Long et al.'s rate at which the information set
  shrinks, taken at this horizon rather than per move. A mob's position is hidden and shown again
  as it moves and is not among the facts; how many mobs were first drawn per wait during the
  playouts is reported beside the factor.
- **Playouts stay on the floor of the sample.** A descent is never chosen in a playout, since the
  floor generated below would read the process's journal (story 1.20's limit) and since the
  tactical horizon of ADR-0010 is two to four hero turns.

## Not measured here

- **The benchmark with a Brain attached** is E4's: no Brain exists yet, and the random agent's
  rate is an upper bound on nothing but the harness.
- **Simulator speed and the search leak test** are E6's (ADR-0010): no rollout host exists yet.
- **The variance paired seeds buy** needs two Brains and is E3's (SM-5).
- **Cross-platform reproducibility** is story 3.4's nightly; these numbers were produced on the
  machine above.

## SM-4

The rate and the two properties are published here, and the determinism test,
`DeterminismTwoJvmTest` (story 1.16), is green on this commit: the same tuple gives the same
Observation hash at every wait, twice in one process and in two other processes. Every Run this
page rests on is such a tuple.
