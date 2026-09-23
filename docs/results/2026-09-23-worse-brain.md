# The deliberately worse Brain, rejected

Story 3.9, [#98](https://github.com/watchthelight/shatterfish/issues/98): SM-5, the proof that the
Rig can tell a worse Brain from a better one, and E3's done-when. Every Registration this story
committed is on this page, including the ones that did not show what they were meant to.

**Common to every attempt.** Upstream tag `v4.0.0`; Seed set `standard` version 1 (500 triples, all
six hero classes); turn cap 20,000; 24 processes on an Intel Core Ultra 9 275HX under Windows 11;
Oracle off in every Run's header; the GSPRT at the calibrated bounds — p0 0.50, p1 0.60, α = β =
0.05, burn-in 20, maximum 500, missing cap 0.25 ([calibration](calibration-v4.0.0.md)). A pair is
two Runs of one triple under one salt, scored from the candidate's side; a verdict is over the pairs
up to the stop, in the Seed set's order. A pair with a Run the game did not end is *missing*: it
scores ½, so it sits in the "equal" counts below, and the result is void if more than a quarter of
the consumed pairs are missing. Every Brain here is the random agent, or the random agent with one
kind of Action withheld, with a configuration of zeros. The Registrations are in
[`registrations/`](https://github.com/watchthelight/shatterfish/tree/main/registrations), the ledger
in [`registrations/ledger.jsonl`](https://github.com/watchthelight/shatterfish/blob/main/registrations/ledger.jsonl),
and each `comparison.json` with both sides' run indexes and summaries in
[`results/`](https://github.com/watchthelight/shatterfish/tree/main/results); the Run logs (about 37
MB a side) are not committed. The command, from the repository root at the invoking commit:

```sh
./gradlew :rig:run --args="--brain <candidate> --against <baseline> --seeds standard --parallel 24 --out <dir> --registration <id>"
```

## The attempts

| Registration | Invoked at | Candidate | Baseline | Verdict | Stopped at | Consumed (worse / equal / better) | All 500 pairs (worse / equal / better) | Missing | Identical pairs | Time for 1,000 Runs |
|---|---|---|---|---|---|---|---|---|---|---|
| `H-0003-nodescend-worse` | `9ce25f93b` | `random-nodescend` | `random` | refused on reading; no Run | — | — | — | — | — | — |
| `H-0004-nodescend-worse@c2dd0f753fc53dbb` | `da9f76b7b` | `random_nodescend` | `random` | REJECT | 20 | 0 / 20 / 0 | 0 / 500 / 0 | 42 | 458 | 376,198 ms |
| `H-0005-norest-worse@ecc7ab486cc071a4`, first | `370cf405b` | `random_norest` | `random` | **VOID** | 27 | 14 / 7 / 6 | 339 / 83 / 78 | 82 | 1 | 371,513 ms |
| `H-0006-random-over-norest@e3d09312f0907838` | `370cf405b` | `random` | `random_norest` | **ACCEPT** | 49 | 11 / 12 / 26 | 72 / 86 / 342 | 86 | 0 | 392,894 ms |
| `H-0005-norest-worse@ecc7ab486cc071a4`, second | `6d65a19cb` | `random_norest` | `random` | **REJECT** | 22 | 13 / 4 / 5 | 340 / 80 / 80 | 80 | 0 | 397,396 ms |

**H-0003: a name the log refuses.** The first Registration named the worse Brain
`random-nodescend`. A Brain's name goes into a hyphen-separated run id, so the record refuses a
hyphen, and it refused this one the moment the first invocation read the file — before any Run, and
before the ledger is opened, so the ledger has no line for it. The file was withdrawn in
`ffb2446a4` rather than left as one no reader accepts, the name became `random_nodescend`, and two
tests now hold every Brain name to the log's pattern and every committed Registration to reading.

**H-0004: the worse Brain SM-5 names is the random agent.** `random_nodescend` withholds `Descend`,
and ended every one of the 458 pairs that reached an ending exactly as `random` did. Two agents that
draw from one stream part at the first draw one of them withholds, so outcomes this identical mean
`Descend` was never chosen in those Runs: withholding it withholds nothing. (The seven Baseline Runs
that reached the second floor got there without it; how is a question for the Codex, not this page.)
The test rejected at its burn-in, and that rejection says nothing about "worse": a one-sided test
rejects two identical Brains exactly as readily, since a run of ties is evidence against H1. It is
published because it happened.

**H-0005 and H-0006: a worse Brain that is worse.** `random_norest` withholds `Rest`, chosen on the
development set `smoke` before anything was registered on `standard`: withholding `Attack`
(`random_noattack`, kept in the Rig so this is reproducible) changed one pair of 25; withholding
`Rest` lost 21. Without `Rest` a random Run takes many more, shorter Actions and dies far sooner in
turns — a median of 288 against the Baseline's 1,384 in H-0005's second invocation.

Its first invocation was **void**: 7 of the first 27 pairs had a Run the game did not end, above the
registered cap of a quarter. (A pair is missing if either side's Run is; each side loses about 9% of
its Runs to windows the Harness does not know, so about 16% of pairs are missing on average, and a
27-pair prefix can easily reach 26%.) H-0006, the same difference from the other side, ran beside it
and **accepted** at pair 49.

The one further invocation of H-0005 was decided after both results were seen — H-0005's void and
its full 500 pairs, and H-0006's accept — and was written into the story file and committed
(`6d65a19cb`) before it ran, with the rule that it would be published whatever it concluded and that
there would be no third. That is a second chance at the same hypothesis on fresh salts, so the
chance that *some* invocation of H-0005 rejects a Brain that is not worse is higher than the
calibrated 5%; the ledger shows both invocations so that nobody has to take the count on trust. It
**rejected** at pair 22.

The traces, in natural log units against bounds of ±2.944: H-0005's second, −44.1, −176.3, −396.4,
−0.64, −0.31, −0.40, −0.70, −1.03, −1.42, −1.56, −1.99, −2.15, −1.57, −1.19, −0.89, −1.18, −1.48,
−1.59, −1.92, −2.25, −2.61, −2.97; H-0006's ending 2.47, 2.75, 2.77, 3.05. The first three values
are that large because three pairs with one outcome have almost no variance, which the
regularization divides by; the burn-in exists so that nothing stops there. Both final values are past
their bound, so `comparison.json` records the LLR at the bound with `clamped` true.

So the rejection is about direction, not only about two Brains being alike: the Rig tells the better
Brain from the worse one either way round, at 22 and 49 pairs.

## What pairing buys

ADR-0012 left open how much pairing on (triple, salt) is worth; the research it cites warned that it
might be little (chess sees about 15%, ADR-0012's context). `comparison.json` now reports the
within-pair correlation of turns survived over the pairs both Runs of which reached an ending, and
the pairs that ended alike in every part of the Composite outcome:

| Registration | Pairs both ended | Turns correlation r | Pairs ended alike |
|---|---|---|---|
| H-0004 (the same Brain twice, in effect) | 458 | 1.000 | 458 |
| H-0005, first | 418 | 0.192 | 1 |
| H-0006 | 414 | 0.125 | 0 |
| H-0005, second | 420 | 0.152 | 0 |

When the two Brains do the same thing, pairing makes the two Runs end the same way, and every pair
ties. The moment they differ in one kind of Action they part, and what is left of the pairing is a
correlation of 0.12 to 0.19 in turns survived: the shared dungeon still counts for something, but
not much. Turns survived is a proxy — the test consumes the win / tie / loss score, whose variance
under pairing against an unpaired shuffle is story 3.10's cross-check — and the test does not depend
on the benefit being large.

## Beside E1's numbers

A comparison on `standard` is 1,000 Runs and took **371 to 397 seconds** here, 2.5 to 2.7 Runs per
second on 24 processes; the Baseline alone, 500 Runs, took 177 seconds. E1's Harness plays **399.5
Input waits per second** in one process and **362.3 Runs per minute** of the floor-one Warrior
([E1 throughput](e1-throughput.md)). A Baseline Run is about 68 waits, so its cost is mostly the
Rig's per-Run start-up (a JVM, the game booted, a Profile, a hash-chained log); a `random_norest`
Run is about 350 waits, and a comparison with it runs at about 540 waits per second across the pool
against the Baseline's 190. The early stops came at 20 to 49 pairs, but the Rig plays all 500 before
it evaluates; stopping dispatch at the verdict is in [ideas](../ideas.md).
