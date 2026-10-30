# Determinized fight search, measured (story 10.24, #370)

**Question.** Does rolling a fight's candidate Actions out through the isolated engine, under sampled hidden state, and taking
the best mean beat the rules Brain's own fight Policy?

**Verdict.** No win. The search is kept as a Rig-only Brain, `fightsearch`, which is off by default: no Brain that ships uses
it and the Overlay never lends it a look-ahead. It stays registered so that a better value function, a larger K, or Tengu
practice (#387) can be tried on a working, leak-tested path.

## The measurement

`:rig:direction --set standard --class <C> --first 40 --salt-base 1000 --baseline 7a753e616e93998bc5c209a4038f16472fcff54e
--brain fightsearch --jobs 3`, once per class, one lane at a time. The baseline is main at `7a753e616` (the 4.x Brain), the
current side is this branch merged with main (`42c811723`), so the engine, harness, Codex and Playbook are identical and the
only difference is the Policy. Plan: K=4 samples, H=8 Actions, up to 3 candidates, radius 3, margin 100, gate 1 (dangerous
fights only). Forty Runs per side per class, none unfinished.

| Class | reach depth 5 (rules / search) | Goo kills | deepest mean | median turns |
|---|---|---|---|---|
| WARRIOR | 31 / 30 | 27 / 27 | 5.65 / 5.70 | 2063.5 / 2305.5 |
| MAGE | 20 / 25 | 11 / 9 | 4.55 / 4.65 | 1564.0 / 1673.0 |
| ROGUE | 15 / 12 | 10 / 11 | 4.13 / 3.95 | 1768.0 / 1392.3 |
| HUNTRESS | 25 / 21 | 18 / 15 | 4.85 / 4.55 | 1806.3 / 1680.0 |
| DUELIST | 19 / 15 | 12 / 7 | 4.45 / 3.95 | 1579.5 / 1494.0 |
| CLERIC | 16 / 12 | 12 / 8 | 4.15 / 3.93 | 1386.8 / 1470.8 |

Reaching depth 5, summed: 130 of 240 for the rules, 115 of 240 for the search. One class is up (MAGE), five are down or
level, none past noise at n = 40, and the sum is lower for the search. One table per class is one measurement. A 3000 slice
(80 Runs for MAGE and DUELIST) was planned and stopped: it could not change the verdict.

**An earlier table is superseded.** A first table (rules 101 of 235, search 100 of 240) was measured before three changes
and must not be quoted: it predates the fix for a crash in the search's rollout world, the item-use stop, the uniform
unidentified-item permutation, and the merge of main (tunables, Tengu). It also counted 35 baseline DUELIST Runs, because
five baseline child JVMs died with "paging file is too small" (errno 1455) while two lanes ran at once, and the direction
tool drops unfinished Runs from the denominator. Later tables lost one to three search Runs per class to that crash.

**The crash.** Remembered mobs are put back into a scrubbed copy from the Brain's hypothesis. A bare `Mimic`, `Bee`,
`GoldenMimic`, `EbonyMimic` or `CrystalMimic` is built with HP 0 and HT 0 (their spawner sets them up), and the Observer
refuses a health bar with nothing to be full at. `MobRows.freshMob` now skips such mobs. The test
`MobRowsTest.a_mob_without_health_is_not_remembered` fails on the code without that guard (the Mimic comes back with HP 0
and HT 0) and passes with it.

## Why it does not help

A diagnostic over 20 Decisions where the search disagreed with the rules pick, and the playouts behind them:

- The gap by which the search beats the rules pick is near the noise of K=4 (median 226 milli against a 100-milli margin),
  and the choice flips with the seed among near-equal candidates.
- H=8 is too short to see the risk at low health: no deaths at H=8, 27 to 33 percent at H=16, with different choices.
- About 39 percent of the disagreements are retreat or hold moves.
- Many waits are never searched at all, because fewer than two candidates qualify.
- The playout is the rules Brain, so the search can only find a first Action that the rules, continued, do better after. The
  playout dies in ways the real Brain, which can react differently at each step, might not.
- Defeat counting from a sliding log window is a noise source in the value.

## Fairness changes made on the way

The fairness review found that a rollout could depend on an unidentified item's class through the scrubber's permuted
assignment. Playouts now stop at the first item use, and the permutation is drawn uniformly over the assignments the hero
could hold (#402; the old draw was biased and a chi-square test fails on it). The search plan is passed explicitly, not
through the environment (#404), and the Overlay never builds a searching decider (#403).

## What the table cannot show

Think time per Decision (the Run logs carry `think_ms`); the effect on Tengu, where the fights that matter are; any plan
other than the reference one (`--search k=8,h=12` changes it and is part of the configuration hash).
