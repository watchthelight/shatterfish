# The properties measurement: leaf correlation and disambiguation with a Brain

**ADR-0010's second measurement**, made for story 6.5 ([#252](https://github.com/watchthelight/shatterfish/issues/252)). It
reruns the method of story 1.21 ([E1 throughput](e1-throughput.md)) the way that story's amendment in
[ADR-0010](../adr/0010-tactical-search-deferral-criteria.md) said it must be rerun: from mid-fight snapshots, with a Brain
driving, with the two changes the amendment named. It reports the two transferable properties of Long et al. with 95%
intervals for each driving Brain. It compares nothing to a threshold and chooses nothing. The prediction at the end is
explanatory, not a verdict.

Shatterfish at the commit printed in each log (branch `story/6-5-the-properties-measurement`); upstream `v4.0.2`; the real Brain
(`weights/shatterfish.json`) finds the snapshots, and the snapshots are drawn with the code of story 6.4 (`SimulatorSpeed`).
The speed of the rollouts this method would need in a search is on the [simulator speed page](2026-10-26-simulator-speed.md);
this page does not use a Simulator, only the perfect-information playouts of story 1.21.

## Method

At a wait with an enemy drawn (and no window in front), at least 40 waits after the last snapshot, the live Run is snapshotted
(`SnapshotStore`) and up to four distinct valid Actions (never a descent) are chosen as siblings. For each of three drivers every
sibling is played from the snapshot, then the driver plays on, to 20 waits or to the hero's death. A leaf is read at 4 waits (the
tactical horizon of ADR-0010) and at 20 waits (story 1.21's), from the same playout. The drivers see an Observation and nothing else:

- the **random agent** (a fresh derived seed per playout);
- the **baseline Brain**, rewound to the Belief the live Brain held at the snapshot;
- the **one-ply model** (`Brain.Model.ONE_PLY`), rewound to the same Belief.

Definitions, pinned on hand-built cases by `PropertiesDefinitionTest`:

- **Leaf correlation**: the probability that two sibling leaves of one snapshot share the payoff (alive or dead). Because
  survival is near 1 for a Brain, the page also gives the **chance agreement** at that survival and Cohen's **kappa**
  (agreement minus chance, over one minus chance), which says what the agreement adds. Where everyone lives, kappa is 0/0 and
  printed "n/a".
- **Disambiguation factor**: the share of the floor's hidden facts that the leaves reveal. The hidden facts are the identities of
  the unknown potions, scrolls and rings an item of this floor shows (in the pack or on the floor), and the floor's secret doors,
  hidden traps and hidden mimics. The two changes of the amendment are in: the denominator is **the facts reachable on the floor
  of the sample** (not the Run's whole set of unknown appearances), and the count is **taken at a death as well as at the
  horizon**. The table "one at a time" below shows each change on its own beside story 1.21's count.

Intervals are 95% percentile intervals of a cluster bootstrap over seeds (the snapshots of one Run are not independent), 2000
resamples, a fixed seed. Bias is not measured: it is a two-player quantity, and ADR-0010 decided not to measure it.

**The oracle.** The hidden facts are counted through the oracle sidecar from the launcher's `--oracle` door
(`Launcher.hiddenFacts`), between the steps of a playout, into the leaf record only. The task passes `--oracle` and every log prints it. No driver
was handed a fact. `PropertiesOracleGateTest`, `RigOracleGateTest`, `OverlayOracleGateTest` and `OracleGateTest` hold that no
Rig run, Overlay or search can ask.

## Command

```sh
./gradlew :harness:propertiesMeasurement                                   # the smoke set, 25 Runs
./gradlew :harness:propertiesMeasurement -Pproperties.args="--set standard"   # the second slice: the first 25 standard Runs
```

The `smoke` run was made twice and the two reports are byte for byte the same ([run 1](logs/2026-10-26-properties-smoke-run1.txt),
[run 2](logs/2026-10-26-properties-smoke-run2.txt)); the second slice is [here](logs/2026-10-26-properties-standard-run1.txt).
Each log names its configuration: up to 6 snapshots per Run, 4 siblings, 2000 resamples. The `goo` and `holdout` sets were not
touched (the measurement refuses them).

## The result on `smoke`

143 snapshots (3 Runs ended by the hero's death before their sixth), 526 leaves per driver.

**Horizon 20 waits**, the payoff alive or dead:

| Driver | Survival | Leaf correlation | Chance agreement | Kappa | Disambiguation factor |
|---|---|---|---|---|---|
| random agent | 0.327 [0.269, 0.384] | 0.670 [0.606, 0.723] | 0.560 | 0.251 [0.146, 0.352] | 0.250 [0.190, 0.312] |
| baseline Brain | 0.973 [0.950, 0.993] | 0.976 [0.953, 0.993] | 0.948 | 0.528 [-0.150, 0.838] | 0.065 [0.043, 0.087] |
| one-ply model | 0.949 [0.912, 0.979] | 0.951 [0.925, 0.973] | 0.903 | 0.497 [0.114, 0.684] | 0.069 [0.043, 0.095] |

**Horizon 4 waits**, the same payoff:

| Driver | Survival | Leaf correlation | Chance agreement | Kappa | Disambiguation factor |
|---|---|---|---|---|---|
| random agent | 0.960 [0.926, 0.987] | 0.959 [0.930, 0.985] | 0.923 | 0.468 [0.167, 0.610] | 0.060 [0.034, 0.088] |
| baseline Brain | 0.985 [0.965, 1.000] | 0.988 [0.975, 1.000] | 0.970 | 0.611 [0.382, 0.697] | 0.020 [0.010, 0.032] |
| one-ply model | 0.985 [0.965, 1.000] | 0.988 [0.975, 1.000] | 0.970 | 0.611 [0.382, 0.697] | 0.020 [0.010, 0.032] |

The baseline Brain and the one-ply model read the same at four waits: over four waits they chose the same Actions in every playout, so the identical
cells are a result and not a copy error.

**A second payoff, added here.** A Brain that survives 97% of the time leaves the first payoff little to disagree about, and its
kappa has an interval that crosses zero. So the same leaves are also read as "alive with at least half the hero's health left".
This is an addition to the amendment, which named one payoff; the first payoff above is the amendment's.

| Horizon | Driver | Sound | Leaf correlation | Chance agreement | Kappa |
|---|---|---|---|---|---|
| 20 | random agent | 0.177 [0.130, 0.223] | 0.744 [0.679, 0.804] | 0.709 | 0.119 [0.005, 0.221] |
| 20 | baseline Brain | 0.791 [0.703, 0.870] | 0.872 [0.831, 0.911] | 0.669 | 0.612 [0.448, 0.729] |
| 20 | one-ply model | 0.789 [0.720, 0.852] | 0.888 [0.853, 0.924] | 0.667 | 0.664 [0.511, 0.791] |
| 4 | random agent | 0.835 [0.760, 0.897] | 0.924 [0.877, 0.963] | 0.724 | 0.726 [0.507, 0.873] |
| 4 | baseline Brain | 0.863 [0.793, 0.922] | 0.960 [0.933, 0.983] | 0.764 | 0.832 [0.690, 0.929] |
| 4 | one-ply model | 0.867 [0.797, 0.925] | 0.964 [0.944, 0.983] | 0.769 | 0.843 [0.698, 0.930] |

### Story 1.21's reading and the two changes, one at a time

The disambiguation factor at 20 waits, counted as story 1.21 counted it, then with the floor's facts as the denominator, then
with deaths counted too:

| Driver | Run-wide denominator, survivors (as 1.21) | Floor denominator, survivors | Floor denominator, every leaf |
|---|---|---|---|
| random agent | 0.015 [0.009, 0.022] | 0.125 [0.062, 0.201] | 0.250 [0.190, 0.312] |
| baseline Brain | 0.008 [0.005, 0.011] | 0.055 [0.036, 0.077] | 0.065 [0.043, 0.087] |
| one-ply model | 0.007 [0.005, 0.010] | 0.051 [0.034, 0.070] | 0.069 [0.043, 0.095] |

The floor denominator is the change that moves the number (about eight times); counting deaths moves the random agent a great
deal (it dies in 354 of its 526 leaves) and a Brain little (14 and 27 deaths).

### What the leaves did

| Driver | Leaves | Ended at the horizon | Death | Left the floor | Dropped |
|---|---|---|---|---|---|
| random agent | 526 | 142 | 354 | 30 | 46 (sibling refused 45, stalled 1) |
| baseline Brain | 526 | 477 | 14 | 35 | 46 (sibling refused 45, driver cannot decide 1) |
| one-ply model | 526 | 497 | 27 | 2 | 46 (sibling refused 45, driver cannot decide 1) |

A playout that cannot be read to an end is dropped and counted, never replaced. "Sibling refused" means the executor would not
apply the Action at the restored wait; it is the same snapshots and siblings for the three drivers, so the 45 are the same
playouts. A playout that ends in a descent or a scene change is counted alive and the hidden set is read just before. The floor
holds 5.7 hidden facts per snapshot on average, 34 in the Run's whole set; by kind, 3.4 identities, 0.8 doors, 1.4 traps and
0.06 mimics.

## The second slice: the first 25 `standard` Runs

131 snapshots, 474 to 478 leaves per driver ([log](logs/2026-10-26-properties-standard-run1.txt)). Horizon 20, alive or dead:

| Driver | Survival | Leaf correlation | Kappa | Disambiguation factor |
|---|---|---|---|---|
| random agent | 0.377 [0.316, 0.433] | 0.707 [0.643, 0.770] | 0.377 [0.253, 0.489] | 0.177 [0.119, 0.247] |
| baseline Brain | 0.987 [0.978, 0.996] | 0.972 [0.946, 0.992] | -0.128 [-0.445, 0.085] | 0.031 [0.018, 0.045] |
| one-ply model | 0.977 [0.959, 0.992] | 0.971 [0.944, 0.991] | 0.350 [-0.339, 0.730] | 0.038 [0.023, 0.055] |

On the sound payoff at 20 waits the kappa is 0.403 [0.229, 0.567] for the baseline Brain and 0.570 [0.421, 0.698] for the
one-ply model, and at 4 waits 0.555 and 0.562. The shape is the `smoke` shape: leaf correlation near survival, a kappa the first
payoff cannot place for a Brain, a sound-payoff kappa of about 0.4 to 0.8, a disambiguation factor of a few hundredths for a
Brain. The slice is an independent check of direction, not a pooled estimate: its disambiguation factor for the Brain is about
half the `smoke` one, outside that interval, which says the factor depends on which Runs are drawn. One `standard` seed
(648322377831) came back from its first snapshot's playouts to a different map Observation than it left; its Brain playouts
at that snapshot (8) were dropped and the Run ended there with its recorded snapshots kept (the log's note and the "restore
differs=4" in its table). The cause is not known; it did not happen on `smoke`, and the measurement checks the restore against the snapshot's Observation hash so that it can never pass unseen.

## What it says for sampled search (explanatory)

This section is an explanation of the readings in the terms of Long et al., not a test and not a choice. The properties
predict how well determinized (sampled) search does in a game: it does well where leaf correlation is high, bias low and
disambiguation fast. Our scales are not theirs (one player against a dungeon, horizons in waits, a payoff of alive or dead), so
a comparison of magnitudes would claim more than the numbers hold.

- **Leaf correlation, high for a Brain (0.95 to 0.99 on alive or dead, 0.87 to 0.96 on the sound payoff).** Sibling Actions
  mostly end the same way, for a Brain that rarely dies, so a search that ranks them on survival has little to separate;
  where it does not, the sound payoff, a kappa of 0.6 to 0.8 means the agreement is more than chance. Read the way Long et al.
  read it, this favours sampling: if siblings agree, a sampled world rarely changes the ranking, so few samples would do.
  It also means the payoff must be finer than survival for a search to have something to find.
- **Disambiguation, low (0.02 at four waits, 0.03 to 0.07 at twenty, a Brain).** In a tactical horizon the hero learns about
  one hidden fact in fifty. Long et al.'s result is that search over a sampled world does best when play resolves the hidden
  information quickly, so a low factor predicts that the hidden facts stay hidden for the whole line and that sampling them
  buys little inside a short horizon. The random agent's 0.25 is its deaths and its wandering, not a property a search could use.
- **The random agent is not the Brain.** Its leaf correlation of 0.67 and survival of 0.33 are the numbers story 1.21 could not
  place on the scale; the Brain readings are the ones the scale can use.

What these cannot show: that either property is a reason to build or not build a search (ADR-0010's rule 4 puts them beside a
candidate, not in front of it), the bias, or how much a search would gain on the Brain's real Decisions, which is story 6.6's.

## Limits

- The scales differ from Long et al.'s (see above). The properties are given in the Run's own units and are not compared with a
  published table.
- A playout that leaves the floor is counted alive, since the floor's events are over and the hero lives. Dropping them would drop
  exactly the Brain's flight.
- The second payoff and the "one at a time" table were added after a first full run showed a Brain's first-payoff kappa had
  almost nothing to measure (14 deaths in 526 baseline leaves). They are additions to the amendment, and labelled so.
- Chests and the loot of mobs are not counted in the floor's identities (the contents of a heap on the floor are).
- Kappa for a Brain with 97% survival is unstable; its interval includes zero in several cells. Read the sound payoff beside it.
- The slice and the repeat were run on a shared machine; the report does not depend on timing.
