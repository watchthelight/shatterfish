# Story 6.3: the search leak test

`SearchLeakTest` is the instrument of [ADR-0010](../adr/0010-tactical-search-deferral-criteria.md)'s choice rule 1: if it fails,
no engine-rollout design ships. This page is its first reading, on the `smoke` set, with the program's two searches. It is a
findings page and not a Rig run: it has no seed set to compare brains over and no sequential test. Nothing about a Brain's
strength is claimed here.

## What was run

- Upstream tag `v4.0.2`; Shatterfish at the story 6.3 branch, on the harness; `./gradlew :harness:test --tests
  org.shatterfish.harness.agent.SearchLeakTest` with one test fork, on the development machine.
- Seed set: `smoke` (never `goo` or `holdout`). The first two Warriors and the first of every other class were played by the
  real Brain to the first wait with an enemy in view, at least 30 waits in, that the scrub accepts. A hidden trap and a secret
  door were planted in view first (the Observation is asserted unchanged by them).

| Snapshot | Wait | Planted |
|---|---|---|
| Warrior `3343871708117` | 53 | trap and door |
| Mage `2204215268527` | 30 | door |
| Rogue `2127745194024` | 73 | trap and door |
| Huntress `2246569184187` | 30 | trap and door |
| Duelist `956134529682` | 30 | trap and door |
| Warrior `4458542768457` | 270 | trap and door |
| Cleric `1456654449712` | none | the Brain's Run ends at wait 273 before a fight the scrub accepts, so the entry gives no snapshot |

- Searches: the one-ply model (story 6.1, the real Codex) and the probe search (`ProbeSearch`, a test fixture): up to three
  candidate Actions, each rolled out for three turns from each of two scrubbed copies through `RolloutHost`.
- Seed of the search's own stream: `9`; Run salt `0x5A175A17`; the wait index of each snapshot. Every world is played in one
  driver, so the Run's salt is one value across them.

## Result

| Case | Worlds | Decisions compared | Rollouts | Verdict |
|---|---|---|---|---|
| Every row varied (8 alternates per snapshot) | 48 alternates beside 6 originals | 108 | 324 | identical bytes in every world |
| Each element alone (3 alternates per snapshot, 5 elements) | 90 alternates | 240 | 720 | identical bytes; no element leaks |
| Scrub refusals among the alternates | | | | 0 |

The Decision of the policies that took the wait, over the all-rows case: the one-ply model's own policy took 27 of the 54
one-ply Decisions (the fight Policy beneath it the other 27); the probe made 54. Every alternate showed the Observation hash
of its original, section by section. For each hidden element a snapshot holds (all five were held, across the six
snapshots), every alternate world read it differently from the live Run.

## The controls

| Control | Planted leak | Caught |
|---|---|---|
| 1 | a search handed the live snapshot's handle with the scrubbed flag forged (a host that believes the flag) | yes |
| 2 | a search that reads `Dungeon.seed` | yes, as `[the true seed]` |
| 3 | a probe whose scrubber leaves one row out, each of 13 rows in turn, first three snapshots | `MOBS_OUT_OF_VIEW`, `MOB_AI_AND_HP` always; `UNKNOWN_CELLS` and `SECRET_TRAPS_AND_DOORS` on some layouts (the first full run caught both over six snapshots) |
| 4 | a search that reads where the unseen mobs stand | yes, as `[unseen mob positions]` |
| 5 | a search that reads unidentified item classes, hidden traps, secret doors | yes, each under its own name |
| 6 | a scrub refusal that tracks the hidden state (refuses when the seed is even) | yes, as `[refusal as evidence]` |
| 7 | the comparison on a pair whose screens differ (the hero's hit points cut) | the one-ply Decision moves |

The rows control 3 did not reach with a three-turn rollout from a fight are `POTION_SCROLL_RING_CLASSES`,
`ITEM_LEVEL_AND_CURSE`, `STEALTHY_MIMIC_KEPT` (a gate: the scrub refuses the snapshot it would keep), `CONTAINER_CONTENTS`,
`UNSEEN_ITEMS`, `HERO_HIDDEN_BUFFS`, `DUNGEON_SEED` (read only by a descent), `GENERATOR_AND_QUEUES` and `ROLLOUT_SALT` (the
Run's salt is one value across the worlds, so no comparison between them can see it left alone; `RedeterminationDifferentialTest`
holds that row). A search with a longer line adds its own control for each row it reads.

## What this does not claim

It proves independence from the hidden state the test can vary, the rows of the ADR-0009 table and nothing beyond. A hidden
element outside the table is outside the proof until the table grows. A snapshot the scrub refuses (a chest in view, a boss
floor, a quest NPC in view, a held trinket) is not searched from, so it is not in the proof. The probe's rollouts are three
turns. It is a necessary condition for fairness and not a sufficient one, which is why the fairness reviewer still reads
every search diff. A refusal of the scrub is never evidence: the probe skips a refused sample and records the count, and
control 6 shows a refusal that tracks the truth is caught.

Also in this story, outside the table above: `RolloutHostRefusalTest` (the real host's refusals, eight tests) and three new
cases in `OnePlyLeakTest` for the invisible and disguised enemies the first version of that test did not cover (an invisible
enemy in view under every AI state, one out of view, and a mimic hiding as a chest against the chest it wears).

The test runs in the harness test task, so in CI on every pull request.
