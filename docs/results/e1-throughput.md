# E1 throughput and tactics: the numbers the roadmap rests on

Story 1.21, [#34](https://github.com/watchthelight/shatterfish/issues/34). Upstream tag `v4.0.0`
(`2bb34a4e91d29c8785a9363cad6ddfe5122b1d4f`). Measured at Shatterfish commit `c976188c6`; this
page is a later commit of the same branch, and carries the report of that invocation unchanged
below the reading of it.

**Machine.** Intel Core Ultra 9 275HX (24 logical processors), 31 GB, Windows 11 Pro 10.0.26200,
Zulu OpenJDK 21.0.11, Gradle 9.4, libGDX 1.14.0 headless backend; the report's own Environment
line names what the process saw. One process, one Run at a time.

**Command.** From the repository root, at the commit above:

```
./gradlew :harness:benchmark -Pshatterfish.mobile=off
```

which is `Launcher --benchmark --oracle` with its defaults, 200 Runs, 24 samples, 4 siblings, a
horizon of 20 waits, seed base 31415926; every tuple it plays is (seed base + index, `WARRIOR`,
salt = seed times 31 plus 7), so the same command on the same commit plays the same Runs, and the
smoke test pins a tiny configuration's outcomes so that a change on the harness side that moves
them fails there. The rates below are this machine's and nobody else's; NFR-3 promises no rate
before it is measured, and this page is the measurement. The command was run three times; the
report below is the first, and the three rates are given beside it.

## What the numbers say

- The harness plays about **399.5 Input waits per second per process** with the random agent,
  **362.3 Runs per minute** to the death of a floor-one Warrior, **8609 turns per second**;
  three invocations gave 399.5, 404.7, 402.6 waits per second. Each Run's start and close is inside the
  wall time, and the random agent's mix of Actions makes a wait worth about
  22 game turns (a rest or a search is many turns), so the wait rate is the
  harness's cost per decision with a floor-one Run's setup amortised over its 64.0
  median waits. The `smoke` and `standard` seed sets of E3 are sized from the wait rate;
  process-per-game parallelism multiplies it by the cores.
- The Observer's read costs 99 microseconds per Observation, the codec's canonical bytes
  and hash 420, the JSON writer 922, each timed alone from the Observation; the
  writer hashes on its own, so its figure contains an encode and a hash and the columns are not
  additive. The rate above pays only the Observer's: the random agent's loop neither hashes nor
  renders. A Run that hashes every Observation for its log (ADR-0011) pays about 17%
  more per wait; one that renders the JSON, which hashes as it goes, about 37% more.
  The writer, not the game, is the first thing to make faster when the Rig's logs are on.
- **Leaf correlation 0.877, against a chance agreement of 0.876 (kappa 0.010.).** Over
  23 samples with a pair of playouts, siblings agreed on alive or dead at the horizon
  0.877 of the time, and two playouts that each survive with probability 0.933 agree
  by chance 0.876 of the time. The kappa is what is left, and it is about nothing: at a
  random floor-one wait, twenty waits on, the hero is nearly always alive whichever sibling was
  chosen, and the number is the survival rate's, not the tactics'. Long et al. (research §4) put
  Skat and Hearts at leaf correlation 0.8 to 1.0, where plain redetermination loses little to a
  Nash player; this reading is not on that scale and is not placed on it. It is a validation of
  the method, which E6 reruns from mid-fight snapshots, where survival is uncertain and the
  agreement of siblings means something.
- **Disambiguation factor 0.000**, over 23 samples with a surviving playout.
  A sample carries, by kind, identity 33.0, door 0.0, trap 0.0, mimic 0.0 hidden facts; the unknown appearances are the Run's, not
  the floor's, and dominate the count, while the samples, all at floor-one waits, carried almost
  no secret door, hidden trap or mimic. The revealed share by kind was identity 0.000, door n/a, trap n/a, mimic 0.000. A factor of zero says no
  surviving playout revealed any fact: in twenty random waits the agent identified nothing and
  found no secret. That is a floor set by the agent and the horizon, not the game's own rate,
  and it is counted over surviving playouts only, though on floor one the events that reveal
  (a trap sprung, an unknown potion drunk) are also the ones that kill. Long et al. put Kuhn
  poker at disambiguation 0, where redetermination fails; the reading here is not that game's,
  it is the random agent's. E6's rerun makes two definitional changes recorded in ADR-0010's
  amendment: the denominator is the facts reachable on the floor, and the count is taken at
  death too. ADR-0010's choice rule reads both numbers beside the Rig's acceptance, and never
  instead of it.
- **Bias is not measured.** It is defined as the probability that a game favours one player, a
  two-player quantity with no meaning for a single hero against the dungeon.
- **The first run of this command measured a bug, not the harness.** Its report carried thirty
  thousand FreeType stack traces: under libGDX's mock graphics the back buffer is zero wide, the
  game scales every text block's font by it, and FreeType refused size zero on every render of
  the game log. `HeadlessGraphics` fixes it (ADR-0015's amendment); the rate with the traces was
  371.2 waits per second, and the game's outcomes, every wait and every payoff, were the same
  before and after, which is what text rendering not being in the game looks like.

## Environment

- Shattered Pixel Dungeon 4.0.0; Java 21.0.11 (Azul Systems, Inc.), Windows 11 10.0 amd64, 24 processors, max heap 8048 MB.

## Throughput

- Runs: 200, in 33.1 s of wall time, one process, each Run's start and close included.
- Input waits: 13230 applied Actions (399.5 per second per process); 107 refusals re-served at the same wait.
- Turns: 285118 (8609 per second).
- Runs per minute: 362.3.
- Median Run length: 1387.0 turns, 64.0 Input waits (the mean of the two middles for an even count).
- Causes: {DEATH=200}.
- Deepest floor reached, by Runs: {1=197, 2=3}.

## Costs per Observation

Over 60 waits of one Run, seed 31416126, each read timed alone from the Observation; the writer hashes on its own, so its time is not additive with the codec's:

| What | Microseconds | Bytes |
|---|---|---|
| `Observer.observe()` | 98.6 | |
| codec: `ObservationCodec.encode` and `hash()` | 419.7 | 30226 |
| JSON writer: `Observation.json()` | 921.9 | 29322 |

## Tactics

- ORACLE: the hidden facts below were read through the oracle sidecar (story 1.18) from the launcher; nothing a playout chose saw them.
- Samples: 24 asked, 23 taken, skipped {a window in front=1}; 4 siblings asked, horizon 20 waits; 90 playouts reached the horizon or a death, 2 cut short and dropped.
- Leaf correlation: 0.877 (mean over the 23 samples with a pair of the share of sibling pairs agreeing on alive or dead at the horizon); chance agreement at this survival 0.876, kappa 0.010.
- Survival at the horizon: 0.933 of 90 playouts.
- Disambiguation factor: 0.000 (mean over the 23 samples with a surviving playout of the mean share of the sample's hidden facts a surviving playout revealed).
- Hidden facts per sample, by kind: identity 33.0, door 0.0, trap 0.0, mimic 0.0; revealed share by kind: identity 0.000, door n/a, trap n/a, mimic 0.000.
- Bias: not measured; a two-player quantity.

## Samples

- seed 31416127 wait 6: 4 playouts, payoffs [1, 1, 1, 1], hidden facts 33 {identity=33}, revealed 0.00
- seed 31416128 wait 13: skipped (a window in front)
- seed 31416129 wait 20: 4 playouts, payoffs [1, 1, 1, 1], hidden facts 33 {identity=33}, revealed 0.00
- seed 31416130 wait 27: 4 playouts, payoffs [1, 1, 1, 1], hidden facts 33 {identity=33}, revealed 0.00
- seed 31416131 wait 34: 4 playouts, payoffs [1, 1, 1, 1], hidden facts 33 {identity=33}, revealed 0.00
- seed 31416132 wait 11: 4 playouts, payoffs [1, 1, 1, 0], hidden facts 33 {identity=33}, revealed 0.00
- seed 31416133 wait 18: 4 playouts, payoffs [1, 1, 1, 1], hidden facts 33 {identity=33}, revealed 0.00
- seed 31416134 wait 25: 4 playouts, payoffs [1, 1, 1, 1], hidden facts 33 {identity=33}, revealed 0.00
- seed 31416135 wait 32: 3 playouts, payoffs [0, 1, 0], 1 cut short, hidden facts 33 {identity=33}, revealed 0.00
- seed 31416136 wait 9: 4 playouts, payoffs [1, 1, 1, 1], hidden facts 33 {identity=33}, revealed 0.00
- seed 31416137 wait 16: 3 playouts, payoffs [1, 0, 1], 1 cut short, hidden facts 33 {identity=33}, revealed 0.00
- seed 31416138 wait 23: 4 playouts, payoffs [1, 1, 1, 1], hidden facts 33 {identity=33}, revealed 0.00
- seed 31416139 wait 30: 4 playouts, payoffs [1, 1, 1, 1], hidden facts 33 {identity=33}, revealed 0.00
- seed 31416140 wait 7: 4 playouts, payoffs [1, 1, 1, 1], hidden facts 33 {identity=33}, revealed 0.00
- seed 31416141 wait 14: 4 playouts, payoffs [1, 1, 1, 1], hidden facts 34 {identity=33, mimic=1}, revealed 0.00
- seed 31416142 wait 21: 4 playouts, payoffs [1, 1, 1, 1], hidden facts 33 {identity=33}, revealed 0.00
- seed 31416143 wait 28: 4 playouts, payoffs [1, 1, 1, 1], hidden facts 33 {identity=33}, revealed 0.00
- seed 31416144 wait 35: 4 playouts, payoffs [1, 1, 1, 1], hidden facts 33 {identity=33}, revealed 0.00
- seed 31416145 wait 12: 4 playouts, payoffs [1, 1, 0, 1], hidden facts 33 {identity=33}, revealed 0.00
- seed 31416146 wait 19: 4 playouts, payoffs [1, 1, 1, 1], hidden facts 33 {identity=33}, revealed 0.00
- seed 31416147 wait 26: 4 playouts, payoffs [1, 1, 1, 1], hidden facts 33 {identity=33}, revealed 0.00
- seed 31416148 wait 33: 4 playouts, payoffs [1, 1, 1, 0], hidden facts 33 {identity=33}, revealed 0.00
- seed 31416149 wait 10: 4 playouts, payoffs [1, 1, 1, 1], hidden facts 33 {identity=33}, revealed 0.00
- seed 31416150 wait 17: 4 playouts, payoffs [1, 1, 1, 1], hidden facts 33 {identity=33}, revealed 0.00

## Definitions

- **Input waits per second.** Applied Actions over the wall time of the batch, in one process;
  a wait is one Observation, one Decision and one Action (ADR-0013). A refusal is re-served at
  the same wait and counted apart.
- **Runs per minute.** Runs to their end, death or the turn cap, over the same wall time.
- **Median Run length.** The median over the batch of the turns the game counted and of the
  Actions applied; the mean of the two middles for an even count.
- **Costs.** One Run, seed 31416126, its waits up to 400 of them (60 here,
  the Run ending first), each read timed three ways with `System.nanoTime`: the Observer's
  `observe()`; the codec's `ObservationCodec.encode` and `hash()`, the canonical bytes and their
  SHA-256 (ADR-0005); the JSON writer's `Observation.json()`, the rendering a Run log carries
  (ADR-0011), which computes the hash and the section hashes itself. Bytes are the canonical
  form's length plus the hash's, and the JSON's.
- **Leaf correlation.** From each sample, a snapshot at a windowless Input wait along a random
  Run (story 1.20), up to four distinct sibling Actions of the valid set are each played out with
  the random agent for the horizon; the payoff is 1 if the hero is alive at the horizon and 0 if
  dead, the terminal a tactical search scores at. A playout that does not reach the horizon or a
  death (a scene switch, no choice without a descent, a refusal that stays) is dropped and
  counted. Leaf correlation is the mean over the samples with a pair of the share of sibling
  pairs whose payoffs agree, Long et al.'s "probability that sibling terminal nodes share a
  payoff" at this horizon; chance agreement is s² + (1 − s)² at the pooled survival s, and kappa
  is (agreement − chance) / (1 − chance).
- **Disambiguation factor.** The hidden facts of a sample are the static ones the oracle sidecar
  names (story 1.18): every unknown potion, scroll and ring appearance of the Run, every secret
  door, every hidden trap and every hidden mimic, each as one key. A fact is revealed when the
  sidecar no longer names it at the horizon. The factor is the mean over the samples with a
  surviving playout of the mean over those playouts of the share revealed, Long et al.'s rate at
  which the information set shrinks, taken at this horizon rather than per move; the share is
  also given by kind. The sidecar is read from the launcher under `--oracle`, and nothing a
  playout chose saw it: the playouts are perfect-information rollouts on live snapshots by
  design, not a Simulator (ADR-0010).
- **Playouts stay on the floor of the sample.** A descent is never chosen in a playout, since the
  floor generated below would read the process's journal (story 1.20's limit) and since the
  tactical horizon of ADR-0010 is two to four hero turns.
- **The restore is checked.** Before each sibling plays, the restored wait's Observation hash is
  compared with the sample's; a mismatch skips the sample with its reason.

## Not measured here

- **The benchmark with a Brain attached** is E4's: no Brain exists yet, and the random agent's
  rate is an upper bound on nothing but the harness.
- **Simulator speed and the search leak test** are E6's (ADR-0010): no rollout host exists yet.
- **The variance paired seeds buy** needs two Brains and is E3's (SM-5).
- **Cross-platform reproducibility** is story 3.4's nightly; these numbers were produced on the
  machine above.
- **A process's journal.** The game loads its journal once per process (story 1.20's limit), so
  the 225 Runs of one invocation share it; the outcomes are the tuple's within a process, which
  the smoke test's pins and the two-process determinism test hold, and a per-process reading
  from a fresh journal is what story 3.4's nightly gives.

## SM-4

The rate and the two properties are published here, and the determinism test,
`DeterminismTwoJvmTest` (story 1.16), is green on this commit: the same tuple gives the same
Observation hash at every wait, twice in one process and in two other processes. Every Run this
page rests on is such a tuple.
