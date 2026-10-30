# Issue #410: scroll and potion identities from how often each appearance turns up

**A direction check and a calibration, unregistered.** Nothing here ran under a Registration and no sequential test was
run. Upstream `v4.0.2`, Profile 6, the `shatterfish` Brain on `weights/shatterfish.json`, Oracle off in every Run.
Seeds from the `standard` set only; never `goo`, `holdout` or `tengu`.

## The change

`Beliefs.identities` no longer weights every unknown scroll or potion by the Codex deck weight alone, with the strength
potion treated as the heaviest. Memory counts, per floor, each time an appearance is picked up (`seen:<label>` rows in
`Memory.found`). `IdentityOdds` weighs every one-to-one assignment of appearances to identities by the likelihood of those
counts:

- the scroll of upgrade and the potion of strength follow the guarantee schedule of `Dungeon.java:529-554` (three and two a
  five-floor set, at most one a floor, boss floors skipped);
- every other identity follows its deck weight at about 0.8 random scrolls and 0.8 random potions a floor
  (`Generator.java:221-252`, `RegularLevel.java:380`);
- a zero count on the floor the hero stands on is no data, and an appearance already on the screen counts as found there.

`TestItem` reads a scroll whose upgrade odds reach `test_item.upgrade_first_per_mille` first, and reads a held scroll of
identify onto an unknown potion before drinking it. The rules are in `docs/rules/identification.md` (nine new rows) and
`docs/brain-rules.md#identity-from-counts`; the community claims are `identify-1` and `identify-2` in `docs/lore/index.md`.
The new numbers are in the tunables registry: `beliefs.notice_per_mille` 700, `beliefs.draws_per_mille` 800,
`beliefs.leak_per_mille` 30, `test_item.upgrade_first_per_mille` 500.

The first test fails on `main`: one appearance found on floors 1, 2 and 4 gives upgrade odds above 0.8
(`IdentityFromCountsTest.three_finds_in_a_set_are_upgrade`).

## The calibration

`./gradlew :harness:identityCalibration -Pcalibration.args="--set standard --seeds 24"` plays the Brain on the first 24
`standard` seeds and scores the odds in its belief view against the true identities. The truth is read through
`Launcher.identityTruth`, a second oracle door beside `hiddenFacts`: it refuses without `--oracle`, only the calibration
calls it (`IdentityOracleGateTest`), and the Brain is handed the same Observation as in a ranked Run. A sample is an
appearance on a floor, scored on the last view held while the hero stood there. Brier is the sum over candidates of
(probability less 1 for the true one) squared, plus 1 when the truth is not a candidate; lower is better.

| | before | after |
|---|---:|---:|
| samples | 322 | 299 |
| Brier, all | 0.864 | 0.842 |
| Brier, scrolls | 0.838 | 0.789 |
| Brier, potions and rings | 0.872 | 0.860 |
| Brier, scroll of upgrade (true identity) | 0.690 | 0.288 |
| Brier, potion of strength (true identity) | 0.718 | 0.519 |
| mean probability on the truth, upgrade | 0.218 | 0.507 |
| mean probability on the truth, strength | 0.208 | 0.333 |
| top call right | 24.5% | 28.4% |

The two runs play different games after the first change of choice, so the samples are not the same ones; the rows by true
identity carry the effect. Rings share the potion row in the "after" tool; the "before" run labelled them potions too.

## The direction check

Commands (baseline `5fe283e37`, the merge base; the tool's git-ref baseline did not build in this environment, so the
current side was played at the baseline's Brain files and read back as a baseline folder):

```
./gradlew :rig:direction --args="--set standard --first 40 --salt-base 1000 --jobs 3"
./gradlew :rig:direction --args="--set standard --class WARRIOR --first 80 --salt-base 3000 --jobs 3"
./gradlew :rig:direction --args="--set standard --class WARRIOR --first 80 --salt-base 5000 --jobs 3"
```

| | all classes, 40 | Warrior 80, salts 3000 | Warrior 80, salts 5000 |
|---|---|---|---|
| reach depth 5 | 20 to 22 (+2) | 55 to 52 (-3) | 50 to 52 (+2) |
| Goo kills | 13 to 17 (+4) | 48 to 45 (-3) | 43 to 44 (+1) |
| deepest mean | 4.500 to 4.700 | 5.188 to 5.075 | 5.113 to 5.225 |
| median turns | 1892.8 to 1849.5 | 2171.5 to 2050.5 | 2040.0 to 2111.0 |
| heaps taken / seen | 2207/2510 to 2318/2653 | 5869/6496 to 5825/6406 | 5997/6596 to 6027/6655 |
| upgrades read by depth 5 | 74 to 75 | 150 to 147 | 151 to 149 |

What the tables cannot show: the first Warrior slice is 3 of 80 worse on reaching depth 5. An ablation with the belief model
only (the upgrade-first read order and the identify-held read switched off) gave the same 52 of 80 and a deepest mean of
5.050, so that dip comes from the odds and not from the two Policy changes. The same code on a second Warrior slice at a
different salt base is 2 of 80 better, and the two baselines themselves differ by 5 of 80 on reach (55 and 50) with the
same Brain, so a difference of 3 is inside the spread of the measure. Pooled over both Warrior slices (160 Runs) reach is
105 to 104 and the deepest mean 5.150 to 5.149. The all-class slice is better by 2 and by 0.2 floors. This page does not
claim a win rate gain; it claims calibrated odds, with play that is not measurably different.
