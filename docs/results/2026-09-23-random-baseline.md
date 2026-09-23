# The random Baseline on `standard`

Story 3.9, [#98](https://github.com/watchthelight/shatterfish/issues/98). The Baseline every later
Brain is compared with: what no Brain at all achieves.

| | |
|---|---|
| Registration | `H-0002-random-standard@56c84cdfaf3520b2` ([file](https://github.com/watchthelight/shatterfish/blob/9ce25f93b75d13a290f9274a66b58b4200ae1d08/registrations/H-0002-random-standard.json)), a baseline: it fixes one Brain and tests nothing |
| Upstream tag | `v4.0.0` (`2bb34a4e91d29c8785a9363cad6ddfe5122b1d4f`) |
| Invoked at | `9ce25f93b75d13a290f9274a66b58b4200ae1d08` |
| Brain | `random`, last changed at `cda228aa5`, configuration all zeros |
| Seed set | `standard` version 1, 500 triples, all six hero classes |
| Turn cap | 20,000 |
| Machine | Intel Core Ultra 9 275HX (24 logical processors), Windows 11 Pro, Zulu OpenJDK 21 |
| Oracle | off in every Run's header |
| Ledger | `FINISHED`, the first line for H-0002 in [`registrations/ledger.jsonl`](https://github.com/watchthelight/shatterfish/blob/main/registrations/ledger.jsonl) |

**Command**, from the repository root at the commit above:

```sh
./gradlew :rig:run --args="--brain random --seeds standard --parallel 24 --out <dir> --registration H-0002-random-standard"
```

The same command plays the same triples with fresh salts, drawn when each Run executes (ADR-0007),
so a re-run reproduces the distribution, not the Runs. The Runs themselves are reproduced from
their logs by `--replay`; the run index with every Run's salt and chain is committed in
[`results/2026-09-23-H-0002/`](https://github.com/watchthelight/shatterfish/tree/main/results/2026-09-23-H-0002).
The 500 logs (37 MB) are not.

## What it did

**500 Runs finished, 0 incomplete, on 24 processes in 177,193 ms** — 2.82 Runs per second, 191
Input waits per second across the pool.

| Ending | Runs |
|---|---|
| `DEATH` | 453 |
| `UNKNOWN_WINDOW` (a window the Harness does not know; counted missing in a comparison) | 47 |

| Depth reached | Runs |
|---|---|
| 1 | 493 |
| 2 | 7 |

No Run killed a boss or won. Seven reached the second floor, and not by `Descend`: the random agent
with `Descend` withheld ended every Run that reached an ending exactly as this one did (see the
[worse-Brain page](2026-09-23-worse-brain.md)), so none of them chose it.

**Turns survived**, over the 453 Runs the game ended: median **1,384**, quartiles 1,368 and 1,401,
mean 1,439.5. The spread is narrow: most random Runs end within a few dozen turns of one another.

## Beside E1's numbers

E1 measured the Harness in one process, one Run at a time, with the random agent to the death of a
floor-one Warrior: **399.5 Input waits per second per process** and **362.3 Runs per minute**
([E1 throughput](e1-throughput.md)). The Rig plays each Run in its own JVM, to its natural end or the
cap, across all six classes, and here reached **191 waits per second on 24 processes** and **169
Runs per minute**. The gap is the Rig's cost per Run: a JVM started, the game booted, a Profile
written, a log hash-chained and flushed, for Runs of about 68 waits each. Start-up dominates a Run
this short, which is why parallelism, not the engine's rate, is what the Rig's budget is spent on.
