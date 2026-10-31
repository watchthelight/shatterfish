# The measured win rate and the E7 close

Story 7.10, [#287](https://github.com/watchthelight/shatterfish/issues/287). **The final Brain won no Run.** It played all 500
triples of `standard` once and won 0 of them; the Profile 6 baseline, played again on the same triples, also won 0. This page
reports the rate with its interval, what moved, what the number cannot show, and the link count that closes the epic.

No win rate was set to beat (settled with the product owner on pull request #289), so nothing here passes or fails. The
measure was not changed to close the epic.

## What was run

| | Final Brain | Profile 6 baseline |
|---|---|---|
| Commit | `bb0ac93a6`: `origin/main` at `583ebac89` (stories 7.1 to 7.9 merged) plus this branch's spec and Registration commits; no `brain` source differs from `origin/main` | `7b1212eea`, in a separate worktree |
| Brain | `shatterfish` on `weights/shatterfish.json` and `playbooks/shatterfish.json` | `shatterfish` on `weights/shatterfish.json`, no Playbook (E7 had not begun) |
| Registration | `H-0008-shatterfish-standard-e7-close` (a baseline: one Brain, no test), [generated page](2026-10-27-H-0008.md) | none: an unranked re-run, so it is a reference and not a registered measurement ([generated page](2026-10-27-p6-baseline-standard.md)) |
| Runs | 500, every triple of `standard` version 1, all six classes, played once | the same 500 triples |
| Turn cap | 20,000 (the Rig's) | 20,000 |
| Command | `./gradlew :rig:run --args="--brain shatterfish --seeds standard --parallel 2 --out <dir> --registration H-0008-shatterfish-standard-e7-close"` | the same without `--registration` |
| Wall time | 2,011 s on 2 processes | 1,764 s on 2 processes |

The Profile 6 baseline of [the tuning page](2026-10-26-profile6-tuning-7b1212eea.md) covers only 120 Warrior Runs under tuning
salts, so it cannot stand beside a 500-Run, six-class set; this page plays `7b1212eea` again over the whole set and says so.
Salts are drawn per Run (ADR-0007), so the two sides are two distributions on the same triples, not paired Runs, and a second
pass of either would differ in a few Runs. The Registration was committed before the first Run and amended once, before any Run
was played, to drop a commit hash from its claim text (the Registration reader refuses eight hex digits in prose); no Run had
begun.

A Goo kill here is a Run that killed at least one boss (the first boss of the main path is Goo, on depth 5), counted over the Runs
that reached depth 5. "Reach depth 5" is a deepest floor of 5 or more.

## The win rate

Warrior first, then all classes, then each other class. Each cell is computed from the committed `outcomes.jsonl` of the two
data folders ([final](https://github.com/watchthelight/shatterfish/tree/main/results/2026-10-27-H-0008),
[baseline](https://github.com/watchthelight/shatterfish/tree/main/results/2026-10-27-p6-baseline-standard)), and
`WinRateTest.the_page_has_not_drifted` fails when this block and those files disagree.

<!-- winrate:begin -->

### Warrior slice

| | Profile 6 baseline | E7 final Brain |
|---|---|---|
| Runs | 84 | 84 |
| wins | 0/84 (0.0%) | 0/84 (0.0%) |
| win rate, Wilson 95% | 0.00% to 4.37% | 0.00% to 4.37% |
| Score of the wins | no wins | no wins |
| reach depth 5 | 40/84 (47.6%) | 57/84 (67.9%) |
| Goo kills over reach | 29/40 (72.5%) | 49/57 (86.0%) |
| deaths by depth 1..deepest | 5/5/20/14/12/19/7/2 | 3/8/5/11/10/25/19/3 |
| endings | DEATH 84 | DEATH 84 |
| median turns | 1668 | 2031 |

### All classes

| | Profile 6 baseline | E7 final Brain |
|---|---|---|
| Runs | 500 | 500 |
| wins | 0/500 (0.0%) | 0/500 (0.0%) |
| win rate, Wilson 95% | 0.00% to 0.76% | 0.00% to 0.76% |
| Score of the wins | no wins | no wins |
| reach depth 5 | 95/500 (19.0%) | 206/500 (41.2%) |
| Goo kills over reach | 54/95 (56.8%) | 133/206 (64.6%) |
| deaths by depth 1..deepest | 69/79/144/113/46/32/15/2 | 32/50/120/91/78/83/41/4 |
| endings | DEATH 500 | DEATH 499, UNSERVED_SCENE 1 |
| median turns | 1276 | 1461 |

### Cleric slice

| | Profile 6 baseline | E7 final Brain |
|---|---|---|
| Runs | 83 | 83 |
| wins | 0/83 (0.0%) | 0/83 (0.0%) |
| win rate, Wilson 95% | 0.00% to 4.42% | 0.00% to 4.42% |
| Score of the wins | no wins | no wins |
| reach depth 5 | 10/83 (12.0%) | 19/83 (22.9%) |
| Goo kills over reach | 6/10 (60.0%) | 14/19 (73.7%) |
| deaths by depth 1..deepest | 12/14/28/19/4/3/3 | 8/11/28/17/6/8/5 |
| endings | DEATH 83 | DEATH 83 |
| median turns | 1126 | 1148 |

### Duelist slice

| | Profile 6 baseline | E7 final Brain |
|---|---|---|
| Runs | 83 | 83 |
| wins | 0/83 (0.0%) | 0/83 (0.0%) |
| win rate, Wilson 95% | 0.00% to 4.42% | 0.00% to 4.42% |
| Score of the wins | no wins | no wins |
| reach depth 5 | 15/83 (18.1%) | 29/83 (34.9%) |
| Goo kills over reach | 1/15 (6.7%) | 16/29 (55.2%) |
| deaths by depth 1..deepest | 5/11/24/28/14/1 | 4/8/22/20/14/12/3 |
| endings | DEATH 83 | DEATH 83 |
| median turns | 1417 | 1312 |

### Huntress slice

| | Profile 6 baseline | E7 final Brain |
|---|---|---|
| Runs | 83 | 83 |
| wins | 0/83 (0.0%) | 0/83 (0.0%) |
| win rate, Wilson 95% | 0.00% to 4.42% | 0.00% to 4.42% |
| Score of the wins | no wins | no wins |
| reach depth 5 | 17/83 (20.5%) | 35/83 (42.2%) |
| Goo kills over reach | 14/17 (82.4%) | 26/35 (74.3%) |
| deaths by depth 1..deepest | 9/13/29/15/6/6/5 | 5/10/18/15/10/18/7 |
| endings | DEATH 83 | DEATH 83 |
| median turns | 1384 | 1471 |

### Mage slice

| | Profile 6 baseline | E7 final Brain |
|---|---|---|
| Runs | 84 | 84 |
| wins | 0/84 (0.0%) | 0/84 (0.0%) |
| win rate, Wilson 95% | 0.00% to 4.37% | 0.00% to 4.37% |
| Score of the wins | no wins | no wins |
| reach depth 5 | 4/84 (4.8%) | 43/84 (51.2%) |
| Goo kills over reach | 1/4 (25.0%) | 16/43 (37.2%) |
| deaths by depth 1..deepest | 23/23/21/13/3/1 | 1/4/23/12/27/10/5/1 |
| endings | DEATH 84 | DEATH 83, UNSERVED_SCENE 1 |
| median turns | 782 | 1496 |

### Rogue slice

| | Profile 6 baseline | E7 final Brain |
|---|---|---|
| Runs | 83 | 83 |
| wins | 0/83 (0.0%) | 0/83 (0.0%) |
| win rate, Wilson 95% | 0.00% to 4.42% | 0.00% to 4.42% |
| Score of the wins | no wins | no wins |
| reach depth 5 | 9/83 (10.8%) | 23/83 (27.7%) |
| Goo kills over reach | 3/9 (33.3%) | 12/23 (52.2%) |
| deaths by depth 1..deepest | 15/13/22/24/7/2 | 11/9/24/16/11/10/2 |
| endings | DEATH 83 | DEATH 83 |
| median turns | 1322 | 1296 |

<!-- winrate:end -->

## What the number can and cannot show

- **A win is a Run that ascends with the Amulet.** The deepest any Run got is on the "deaths by depth" row; none is
  within many floors of the end of the main path. A win rate of zero over 500 Runs has a Wilson 95% upper bound of 0.76%: the true
  rate of this Brain on this set is very probably under 1%, and nothing here distinguishes 0% from 0.5%.
- **Zero does not mean no movement.** The movement of the epic is in the reach and Goo-kill rows, where the final Brain's
  counts are above the baseline's in every class table; that is a difference of distributions on one machine, not a paired
  test, and no Sequential test was run.
- **It cannot say why.** The deaths-by-depth row says where Runs end, not what ended them, and the log records the ending and
  depth and not the killer. The Warrior slice is the Brain's best class by construction (the Playbook began as the Warrior's
  numbers); the other classes carry per-class entries only for the Mage.
- **The set is `standard`, the development set.** The Brain was tuned on its first Runs (`--first 40`, `--first 80` slices of the
  Warrior triples). The Warrior slice here therefore includes triples the Brain was tuned on, and the other classes' slices
  include the triples story 7.6 tuned the Mage on. It is not a held-out number, and it is the reason the held-out run exists.
- **Endings.** One final-Brain Run (a Mage, seed code `XMK-SUI-KRX`, depth 2) ended `UNSERVED_SCENE` and not by death, after a
  ranged throw. It is counted in the Runs and as no death, and is filed as
  [#328](https://github.com/watchthelight/shatterfish/issues/328). Cause, found afterwards: the loading scene was in mode `RETURN`, a teleport to a named floor
  (a cursed wand's inter-floor effect, `core/.../items/wands/CursedWand.java:757-767`), which the harness did not serve.
  The harness now serves it like the game does (`core/.../scenes/InterlevelScene.java:717-731`); the figures above are as measured. No Run of either side hit the turn cap.

## The `holdout` set

This story did not run `holdout`. The acceptance criteria allow one confirmation run after tuning, and only with the product
owner's explicit go-ahead in the pull request thread; none had been given when this page was first written, so no release-level
number was taken here. `holdout` was not opened by any invocation of this story (the Registration says `release_level: false`, and
the ledger had no `holdout` line for it). Nothing was run on `goo` either; E7 does not wait for the Goo gate (4.14).

The product owner then gave the go-ahead on [pull request #329](https://github.com/watchthelight/shatterfish/pull/329#issuecomment-6440993127)
("You can go-ahead on all.", 2026-10-27), and the Brain's one allowance was spent: the `holdout` set was run once, afterwards, on
the same Brain, under Registration H-0009 with `release_level: true`. Its numbers, beside the `standard` numbers of this page, are on
[the holdout page](2026-10-27-e7-holdout.md).

## The heuristic link count

The count the epic closes against, beside story 7.1's baseline (71 found, 35 linked to a Rule, 5 to a measured row, 31 unlinked).
It is rendered from the code, and the same test compares it with this block.

<!-- links:begin -->

| | story 7.1 (the baseline) | at the close |
|---|---|---|
| heuristics found | 71 | 102 |
| linked to a Rule | 35 | 66 |
| linked to a Lore claim | 0 | 0 |
| linked to a measured row | 5 | 5 |
| unlinked | 31 | 31 |

Linked at each Tier: Rule Tier 1: 65, Rule Tier none yet: 1, measured Tier 3: 5.

Tier 3 links on heuristics that shipped, each with the Results page that is its evidence:

- [`goo-position-and-wash`](2026-10-26-219-goo-fight-position-and-wash.md): `Playbook.Goo.meetRange`, `Playbook.Goo.washRange`
- [`outpaced-by-a-fast-enemy`](2026-10-26-219-no-outrunning-fast-enemies.md): `Fight.OUTPACED_RANGE`, `Fight.OUTPACED_STAIRS`
- [`piranha-leave-budget`](2026-10-26-219-piranha-water.md): `Fight.LEAVES`

<!-- links:end -->

The one Rule link at Tier "none yet" is `Descend.SEARCHES` to `hidden-exit-search`, whose Rule has no test yet.

**The check did not close to an empty allowlist.** Each of the 31 unlinked heuristics is a tuned value or a stated assumption:
no Rule states its value, and no Results page played it. A Rule row that states the mechanic a value protects (the cost of a
search, the fall of a chasm, the hunger clock) was not taken as a link for the value, because that would label a tuned number
with a Tier 1 it does not have. Each entry now carries its reason and the issue that owns it in `HeuristicLinkTest.UNLINKED`
(`every_unlinked_entry_names_a_reason_and_an_owning_issue`), and the same text is on the
[Brain's Rules index](../brain-rules.md):
[#312](https://github.com/watchthelight/shatterfish/issues/312) (the fight Policy's budgets),
[#313](https://github.com/watchthelight/shatterfish/issues/313) (explore, descend and larder allowances),
[#314](https://github.com/watchthelight/shatterfish/issues/314) (loop backstops and the avoided region) and
[#315](https://github.com/watchthelight/shatterfish/issues/315) (reach constants).

No heuristic links to a Lore claim: no committed claim lists an `implemented_in`, and the 144 migrated claims are undated and
mostly unclassified (see the [variant classifier](2026-10-26-7-8-the-variant-classifier.md) and
[date gating](2026-10-26-7-9-date-gating-from-the-changelog.md) pages). The Tier 3 links above are allowed because each is
backed by the Results page named beside it.

## Reproducing

`./gradlew :rig:results` regenerates the two generated pages from `results/`. The Run logs (about 37 MB a side) are not committed;
each Run's chain and salt are in the data folders' `runs.jsonl`. The block above is rewritten with
`SHATTERFISH_WRITE_WINRATE=1 ./gradlew :rig:test --tests org.shatterfish.rig.WinRateTest`.
