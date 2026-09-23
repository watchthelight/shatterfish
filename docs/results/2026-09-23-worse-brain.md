# The deliberately worse Brain, rejected

Story 3.9, [#98](https://github.com/watchthelight/shatterfish/issues/98): SM-5, the proof that the
Rig can tell a worse Brain from a better one, and E3's done-when. Every attempt is on this page,
including the two that did not show what they were meant to.

**Common to all four.** Upstream tag `v4.0.0`; Seed set `standard` version 1 (500 triples); turn
cap 20,000; 24 processes on an Intel Core Ultra 9 275HX under Windows 11; the GSPRT at the
calibrated bounds — p0 0.50, p1 0.60, α = β = 0.05, burn-in 20, maximum 500, missing cap 0.25
([calibration](calibration-v4.0.0.md)). A pair is two Runs of one triple under one salt, scored
from the candidate's side; a verdict is over the pairs up to the stop, in the Seed set's order. The
Registrations are in [`registrations/`](https://github.com/watchthelight/shatterfish/tree/main/registrations),
the ledger lines in [`registrations/ledger.jsonl`](https://github.com/watchthelight/shatterfish/blob/main/registrations/ledger.jsonl),
and each `comparison.json` with both sides' run indexes in
[`results/`](https://github.com/watchthelight/shatterfish/tree/main/results). The command, for each:

```sh
./gradlew :rig:run --args="--brain <candidate> --against <baseline> --seeds standard --parallel 24 --out <dir> --registration <id>"
```

## The four attempts

| Registration | Candidate | Baseline | Verdict | Stopped at | Consumed (worse / equal / better) | All 500 pairs (worse / equal / better) | Missing | Identical pairs | Runs, time |
|---|---|---|---|---|---|---|---|---|---|
| `H-0004-nodescend-worse@c2dd0f753fc53dbb` | `random_nodescend` | `random` | REJECT | 20 | 0 / 20 / 0 | 0 / 500 / 0 | 42 | 458 | 1000 in 376,198 ms |
| `H-0005-norest-worse@ecc7ab486cc071a4`, first | `random_norest` | `random` | **VOID** | 27 | 14 / 7 / 6 | 339 / 83 / 78 | 82 | 1 | 1000 in 371,513 ms |
| `H-0005-norest-worse@ecc7ab486cc071a4`, second | `random_norest` | `random` | **REJECT** | 22 | 13 / 4 / 5 | 340 / 80 / 80 | 80 | 0 | 1000 in 397,396 ms |
| `H-0006-random-over-norest@e3d09312f0907838` | `random` | `random_norest` | **ACCEPT** | 49 | 11 / 12 / 26 | 72 / 86 / 342 | 86 | 0 | 1000 in 392,894 ms |

**H-0004: the worse Brain SM-5 names is the random agent.** `random_nodescend` withholds `Descend`,
and played the same Run as `random` on every one of the 458 pairs that reached an ending: the random
agent never takes the stairs — the seven Baseline Runs that reached the second floor fell there — so
withholding `Descend` withholds nothing. The test rejected at its burn-in, and that rejection means
nothing about "worse": a one-sided test rejects two identical Brains exactly as readily, since a run
of ties is evidence against H1. It is published because it happened.

**H-0005 and H-0006: a worse Brain that is worse.** `random_norest` withholds `Rest`, chosen on the
development set `smoke` before anything was registered on `standard` (withholding `Attack` changed
one pair of 25; withholding `Rest` lost 21). Its first invocation was **void**: 7 of the first 27
pairs had a Run the game did not end, over the registered cap of one in four, although over all 500
pairs it scored 0.24. The story file fixed, before the second invocation ran, that there would be
exactly one more and that it would be published whatever it concluded. It **rejected** at pair 22,
with the LLR at the lower bound (trace: −44.1, −176.3, −396.4, −0.64, … −2.61, −2.97). And the same
difference from the other side — `random` as the candidate against `random_norest` — **accepted** at
pair 49 (trace ending 2.47, 2.75, 2.77, 3.05 against a bound of 2.94). So the rejection is about
direction, not only about two Brains being alike: the Rig tells the better Brain from the worse one
either way round.

## What pairing buys

ADR-0012 left open how much pairing on (triple, salt) is worth; the research warned it might be
little. `comparison.json` now reports the within-pair correlation of turns survived over the pairs
both Runs of which reached an ending:

| Registration | Pairs both ended | Turns correlation r | Identical pairs |
|---|---|---|---|
| H-0004 (the same Brain twice, in effect) | 458 | 1.000 | 458 |
| H-0005, first | 418 | 0.192 | 1 |
| H-0005, second | 420 | 0.152 | 0 |
| H-0006 | 414 | 0.125 | 0 |

When the two Brains are the same, pairing makes the two Runs the same Run, and every pair ties. The
moment they differ in one kind of Action, they part at the first draw that kind would have taken,
and what is left of the pairing is a correlation of 0.12 to 0.19 in turns survived: the shared
dungeon still counts for something, but not much, as the research warned it might not (chess sees
about 15% from pairing). The test does not depend on it being large; the unpaired cross-check ADR-0012 names is story 3.10's.

## Beside E1's numbers

A comparison on `standard` is 1,000 Runs and took **371 to 397 seconds** here, 2.5 to 2.7 Runs
per second on 24 processes; the Baseline alone, 500 Runs, took 177 seconds. E1's Harness plays
**399.5 Input waits per second** in one process and **362.3 Runs per minute** of the floor-one
Warrior ([E1 throughput](e1-throughput.md)); the Rig spends most of a Run this short starting its
JVM and booting the game, which is the cost the Rig buys isolation and reproducibility with. The
early stops above came at 20 to 49 pairs, but the Rig still plays all 500 before it evaluates;
stopping dispatch at the verdict is in [ideas](../ideas.md).
