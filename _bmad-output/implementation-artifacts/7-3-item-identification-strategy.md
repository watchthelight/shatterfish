---
title: 'Story 7.3: Item-identification strategy'
type: 'feature'
created: '2026-10-26'
status: 'review'
baseline_commit: 'f51e9dae1'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent, do not modify unless human renegotiates">

## Intent

**Problem:** the Brain tries an unknown potion or scroll by fixed rules (story 4.10, 4.13) that Story 7.2 moved into the
Playbook's `identify` section. Whether those values are the right ones, and whether the Playbook can say all that the Policy
decides by, was never measured. 35 of 40 tuning Runs of `main` died holding an unidentified potion
(`docs/ideas/2026-10-26-desperate-potion.md`).

**Approach:** read the held packs and the Beliefs before touching a value, make the `identify` section carry every number the
test-item Policy decides by, measure candidates on the tuning slice and a second slice, and keep a change only on recorded
numbers (CLAUDE.md, E4 lessons). Where nothing beats `main`, land the structure at the reference values and say so.

</frozen-after-approval>

## Acceptance Criteria (from epics.md Story 7.3, with the tests that hold them)

1. The story file holds a micro-brainstorm of at least three alternatives, the pre-mortem, and the baseline table of `main`
   at the start from the `standard` set. Held by the sections below.
2. The claims about identification the story relies on are Rules with `path:line` at `v4.0.2`, each with a row in
   `docs/brain-rules.md`: the new row in `docs/rules/identification.md` (what a class starts with identified, and the deck
   weights); the three new Playbook fields linked to existing slugs in the heuristics table.
3. The change is a Playbook change plus only the Policy code needed to read a new field; the Observer is not touched; the Brain
   uses the Beliefs' candidate identities; `PermutedIdentitiesDifferentialTest` stays green. Held by the diff (no file of
   `harness`, `api` Observer paths or `Observer` changed) and by the test.
4. `IdentifyPlaybookTest` asserts on constructed screens that each field changes the Decision it is meant to and that the
   reference values reproduce the old Decision. Held by `IdentifyPlaybookTest` (and `TestItemPolicyTest`, unchanged in what it
   asserts about the Decisions).
5. The strategy log names the Playbook field that fired. Every Decision of the Policy now ends in the field's name:
   `test: crimson potion (healing_odds_per_mille)`, `cell: jade potion (reach)`, `rest: after-test (rest_waits)`,
   `escape: ToxicGas (escape_waits)`, `drink: potion of strength (known_gains)`. Held by `IdentifyPlaybookTest.the_reference_is_the_old_play`
   and `ShatterfishRunTest`.
6. The PR carries the tuning-slice and second-slice direction tables against `main`, and the deaths within 100 waits of a test
   before and after. Below.

## Code Map

- `shatterfish/api/.../Playbook.java`: `Identify` gains `potion_hp_per_mille`, `scroll_hp_per_mille` and `known_gains`
  (`SPECS`, the constructor checks, `values()`, `with()`, `REFERENCE`), and the two health tests `potionHp` and `scrollHp`.
- `shatterfish/brain/.../TestItem.java`: the half-health and full-health tests read the fields; `gain` drinks a known potion of
  strength or experience when `known_gains` is 1; every Decision's reason names the field.
- `playbooks/shatterfish.json`, `playbooks/oneply.json`: the three fields at their reference values.
- `shatterfish/brain/src/test/.../IdentifyPlaybookTest.java` (new), `TestItemPolicyTest.java`, `shatterfish/rig/src/test/...`
  (`PlaybookEquivalenceTest`, `PlaybookValidatorTest`, `ShatterfishRunTest`), `PlaybookTest` (api): the new fields and reasons.
- `docs/rules/identification.md`, `docs/brain-rules.md`, `docs/ideas/2026-10-26-item-identification.md`.

## Baseline: `main` at the start (standard, WARRIOR, salts 1000 to 1039; and 3000 to 3079)

`main` is `f51e9dae1`. The reference-value branch plays the tuning slice to 39 of 40 equal Runs, the same one 7.2 recorded.

| | tuning 40 (salt 1000) | second slice 80 (salt 3000) |
|---|---|---|
| reach depth 5 | 30/40 | 46/80 |
| Goo kills (kills over reach) | 27/40 (27/30) | 41/80 (41/46) |
| median turns | 2187.5 | 1987.0 |
| deaths by depth 1..8 | 0/0/7/3/3/18/6/3 | 5/9/9/11/9/18/15/4 |
| tests handed over (Decisions `test:`) | 189 (78 potions, 111 scrolls) | 349 (151 potions, 198 scrolls) |
| deaths within 100 waits of a test | 0 | 3 |

The held packs at the last wait of each tuning Run (a `--debug-record` pass of the reference values; `lastpack.py` of the
session, not committed): 34 of 40 died holding at least one unidentified potion; 18 units of a **known** potion of strength
were held at death, 9 of healing, and no Policy drinks a known strength or experience potion (the heal Policy drinks only
healing).

## Findings that shaped the story

- **The Warrior's potion of healing is known from the start** (`HeroClass.java:185-187`), as the scroll of rage; every class
  starts with the scroll of identify known (`:117`). A probe of 142 held-potion screens on the first 12 tuning Runs found the
  healing odds of every unidentified potion at 0.0 and the strength-or-experience odds at 0.04 to 0.19. So for a Warrior the
  whole "drink an unknown potion for its chance of healing" path never fires: `healing_odds_per_mille` set to 100 and to 1 and
  `potion_hp_per_mille` set to 1000 changed no Decision in 40 Runs (the three tables equal the reference, Run for Run). It is the
  reason the desperation rule of `2026-10-26-desperate-potion.md` could not move a Goo kill. The unknown potions the pack holds
  at death are mostly ones whose chance of being strength or experience is under the 0.2 floor, or which carry a harmful
  candidate the hero cannot yet afford (`SafeTest` calls liquid flame lethal at 20 hit points).
- **A known potion of strength is never drunk** by any Policy. `known_gains` adds that Decision.

## Micro-brainstorm

Goal: find healing, strength and the useful scrolls early without dying to a bad drink, and reach depth 5 more often.
Binding constraints: information parity (the Beliefs' candidate identities only), the Observer untouched, a Playbook change
plus the Policy code to read a field, a value kept only on recorded numbers. Alternatives, scored on the evidence above:

1. **Test sooner and at full health** (`potion_hp_per_mille` 1000, `scroll_hp_per_mille` lower). For scrolls the Policy already
   reads at the first calm full-health moment (an unread scroll was in the pack at death in 9 of 40 Runs, mostly one found
   shortly before). For potions, healing is never a candidate
   for a Warrior, so the health gate decides nothing. Built as a field, measured at 1000: no Decision changed.
2. **Stop testing an appearance the deck weights narrow to one candidate.** Already so: `odds().size() < 2` is not tried.
3. **Throw rather than drink a potion the Beliefs say is mostly harmful.** Needs a new Action shape and a target choice; the
   thrown potion identifies only the harmful ones, and the Warrior's gain is in the benign ones. Not built; the idea page
   records it.
4. **Hold unknowns for an emergency drink when healing is likeliest.** Measured by the earlier desperate-potion page: no change;
   and with healing known, the odds of healing in an unknown potion are zero.
5. **Lower the strength-or-experience floor** (`gain_odds_per_mille` 200 down to 120 and 40, up to 300): drink more of the
   unknown potions, or fewer. Cheapest to build (a value), measured below.
6. **Drink a known strength or experience potion** (`known_gains`): free points the Brain currently leaves in the pack.
7. **Read a known scroll of identify onto the unknown potion with the most worth.** The Warrior starts with one, and 14 of 40
   Runs died holding it. A potion's odds of being strength are 0.04 to 0.19, so one read buys under a fifth of a strength point;
   not built.

Chosen to build and measure: 1 (as a field), 5 and 6. Chosen not to build: 3, 7 (a new Action shape, or a gain too small to
show on 40 Runs), and 2 and 4 are already so or already measured.

## Pre-mortem

Imagine the PR merged and the Goo gate worse. Likely causes, and what was done about each:

- Drinking more unknown potions at depth 1 and 2, where the hero has 20 to 30 hit points, kills it on a bad draw. Checked by
  counting deaths within 100 waits of a test, before and after, on every candidate; `gain_odds_per_mille` 40 shows the cost
  (4 such deaths in 40, reach 25/40).
- A field changes a Decision for a class not measured (Mage, Rogue, Huntress): the reference values reproduce the old
  Decisions bit for bit (`PlaybookEquivalenceTest`, `IdentifyPlaybookTest.the_reference_is_the_old_play`), so nothing moves
  until a value moves.
- The reason string changes every Run log's text: tables are read from outcomes, not text, and the test of the reason now
  accepts the field's name. No tool parses the old text (grep of `shatterfish/`).
- The noise: tuning slice runs differ by hash-order noise (#303). The decision rule below uses both slices, and a result
  inside the noise is not taken as a win.

## Measurements (all `--baseline origin/main --jobs 2`, standard WARRIOR)

Tuning slice, 40 Runs, salts 1000 to 1039. Every row is one `:rig:direction` against `main` `f51e9dae1`.

| variant (Playbook values changed from the reference) | reach depth 5 | Goo kills | median turns | deaths by depth 1..8 | tests (potions) | deaths within 100 waits of a test |
|---|---|---|---|---|---|---|
| `main` | 30/40 | 27/40 | 2187.5 | 0/0/7/3/3/18/6/3 | 189 (78) | 0 |
| reference values (this branch) | 30/40 | 27/40 | 2187.5 | 0/0/7/3/3/18/6/3 | 189 (78) | 0 |
| `healing_odds_per_mille` 100, and 1 | 30/40 | 27/40 | 2187.5 | 0/0/7/3/3/18/6/3 | 189 (78) | 0 |
| `known_gains` 1 | 30/40 | 26/40 | 2179.5 | 0/0/6/4/5/15/9/1 | 185 (78) | 0 |
| `known_gains` 1, `potion_hp_per_mille` 1000 | 30/40 | 26/40 | 2179.5 | 0/0/6/4/5/15/9/1 | 185 (78) | 0 |
| `known_gains` 1, `gain_odds_per_mille` 40 | 25/40 | 23/40 | 2062.0 | 1/1/5/8/3/12/9/1 | 267 (172) | 4 |
| `known_gains` 1, `gain_odds_per_mille` 120 | 31/40 | 27/40 | 2202.5 | 0/0/5/4/6/14/11/0 | 187 (82) | 0 |
| `known_gains` 1, `gain_odds_per_mille` 300 | 29/40 | 25/40 | 2196.0 | 1/0/6/4/4/16/8/1 | 113 (0) | 2 |

Second slice, 80 Runs, salts 3000 to 3079, for the one candidate that was not behind on the tuning slice
(`known_gains` 1, `gain_odds_per_mille` 120):

| | `main` | candidate | delta |
|---|---|---|---|
| reach depth 5 | 46/80 | 44/80 | -2 |
| Goo kills | 41/80 | 37/80 | -4 |
| median turns | 1987.0 | 1866.5 | -120.5 |
| deaths by depth 1..8 | 5/9/9/11/9/18/15/4 | 5/9/10/12/10/19/13/2 | |
| tests (potions) | 349 (151) | 350 (155) | |
| deaths within 100 waits of a test | 3 | 3 | |

Reach and conversion: the candidate's kills over reach are 27/31 on the tuning slice (main 27/30) and 37/44 on the second
(main 41/46); the difference is in reach and not in the Goo fight.

## Decision

No candidate beats `main` on both slices. The best on the tuning slice (+1 reach, equal Goo) is behind on the second
(-2 reach, -4 Goo kills, the range of the noise of about 5 Goo kills per 120 Runs, but on the wrong side), `known_gains` alone
is 1 Goo kill behind on the tuning slice, and a lower floor costs early deaths. So the Playbook ships at the reference values
(`known_gains` 0, `potion_hp_per_mille` 500, `scroll_hp_per_mille` 1000, `gain_odds_per_mille` 200): the structure lands, the
behaviour does not change, and the three new fields are there for the next class or the next idea. No Sequential test was
registered, since nothing won both slices (ADR-0012).

What the tables cannot show: a Warrior with strength 10 holds 18 known potions of strength in 40 Runs at death, but 19 more
strength points over 40 Runs did not move reach, so the Goo gate is not limited by strength in this window; a Mage, Rogue or
Huntress starts with healing unknown, and for those `potion_hp_per_mille` and `healing_odds_per_mille` are live, unmeasured
here (the slices are Warrior).

## Strategy log check (E4)

`:rig:strategy` over a folder of reference-value Runs: see the PR. The Decisions of the Policy end in the field's name, so
`grep '(gain_odds_per_mille)'` finds every potion test of a Run without a debugger.

## Decisions (design)

- **The new fields are numbers, not flags.** `known_gains` is 0 or 1 because the Playbook holds typed numbers and enum names and
  a boolean adds a third kind to every reader; the range check refuses 2.
- **`potion_hp_per_mille` and `scroll_hp_per_mille` are health fractions in thousandths**, like the odds, compared in `long`
  arithmetic against the hit points (`1000 * hp <= per_mille * ht`), so the references are bit-equal to the old `2 * hp <= ht`
  and `hp >= ht`.
- **The reason names the field that let the item be tried**, the healing odds when the hero was hurt enough and otherwise the gain
  odds for a potion; for a scroll the inventory odds, and `scroll_hp_per_mille` for a read onto the worn armour (the field that
  gates every read).
- **`known_gains` reads only names the inventory shows** (`potion of strength`, `potion of experience`), which an identified
  potion shows to a human at the same screen.

## Verification

- `./gradlew :api:test :brain:test :rig:test -Pshatterfish.testForks=1`, `mkdocs build --strict`.
- `:rig:direction` tables above; `tests73.py`-style counts of `test-item` Decisions per Run log (not committed).
- Fairness review: see the PR.
