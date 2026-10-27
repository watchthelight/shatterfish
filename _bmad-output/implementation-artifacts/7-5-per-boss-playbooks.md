---
title: 'Story 7.5: Per-boss playbooks'
type: 'feature'
created: '2026-10-27'
status: 'review'
baseline_commit: '3d0a4f663'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent, do not modify unless human renegotiates">

## Intent

**Problem:** the Goo fight has four numbers in `Goo.java` and `Descend.java` (how far the fight Policy walks to better
ground, how far to water, the share of health at which the heal Policy drinks, the share of health the descent rest reaches),
and the bosses below it have none. A boss fight is where a Run ends, and none of these numbers has a place where a person
can read or change it.

**Approach:** the Playbook's `bosses` section holds one typed entry per boss, with no layout or coordinate, and the Goo
Policies read their numbers from it. A value is changed only on recorded numbers.

## Acceptance Criteria (from epics.md Story 7.5, with what holds them)

1. The story file lists the Tier 1 and Tier 2 boss claims that no Policy uses yet, and the Policy decisions that rest on
   boss claims. Held by "The boss claims" below.
2. The `bosses` section holds typed per-boss fields (stand-off distance, water or not, rest fraction before the stairs
   down, known consumable) and no layout or coordinate. Held by `PlaybookValidatorTest` (ranges, unknown boss, unknown key,
   a key from another section, and the refusal of seeds and place words that already ran on every section),
   `PlaybookFileTest.boss_entries`, `PlaybookTest.bosses`. See Decisions for which of the four fields exist and why.
3. Goo is tuned on `standard`, not `goo`, and the `bosses` set is run beside it and reported only. Held by
   `docs/results/2026-10-27-7-5-per-boss-playbooks.md`.
4. The story does not claim the Goo gate (4.14). Kills over fights before and after, with the Wilson interval, are on the
   Results page.
5. Tengu and DM-300 have entries only as far as the `bosses` set reaches them; if fewer than 10 of its 100 Runs reach a
   boss the entry is untested, with no tuning. Held by `Playbook.Later` (status `UNTESTED`) and the Results page: none of 100
   Runs reached depth 10.
6. Every field that changes play links from `docs/brain-rules.md` or a Lore claim, and `HeuristicLinkTest` stays green.
   Held by `HeuristicLinkTest`.
7. The PR is labelled `fairness` and carries the reviewer's verdict.
8. A Registration under the Sequential test against main on `standard` is needed only if a Goo change ships. No Goo change
   ships, so none is made.

</frozen-after-approval>

## Code Map

- `shatterfish/api/.../Playbook.java`: `Bosses(goo, tengu, dm300)`; `Goo(descentRestPerMille, drinkPerMille, meetRange,
  washRange)`; `Later(descentRestPerMille, status)` for the two bosses below; `Playbook.BOSSES`; the specs the validator
  and the file reader share.
- `shatterfish/harness/.../knowledge/PlaybookFile.java`, `PlaybookValidator.java`: read and check the section.
- `shatterfish/brain/.../Goo.java`, `Fight.java`, `Heal.java`, `OnePly.java`, `Descend.java`, `Brain.java`: the constants
  `POSITION_RANGE`, `WASH_RANGE`, `DRINK_PER_MILLE` become reads of the Goo entry; the descent rest reads the entry of the
  boss floor below.
- `playbooks/shatterfish.json`, `playbooks/oneply.json`: the reference entries.
- `docs/brain-rules.md`, `docs/adr/0023-playbooks-as-data.md`, `docs/results/2026-10-27-7-5-per-boss-playbooks.md`, three
  ideas under `docs/ideas/2026-10-27-*.md`.
- Tests: `BossPlaybookTest` (brain), `PlaybookTest`, `PlaybookValidatorTest`, `PlaybookFileTest`, `PlaybookEquivalenceTest`,
  `JsonRenderingTest`, `HeuristicLinkTest`.

## The boss claims

Tier 1 and Tier 2 claims for the bosses (`docs/lore/index.md`; there are no Tier 2 boss claims, 12 Tier 1, 3 Tier 3, 16
Tier F). Used by a Policy today:

- `bosses-1` (move two cells from Goo's pump-up): the Goo dodge in `Goo.dodge`, Rule `goo-pump`.
- `bosses-2` (Goo's caustic ooze is washed off in water): `Goo.wash`, Rule `goo-wash`.

Not used by any Policy:

- Goo: `bosses-4` (Scroll of Lullaby), `bosses-5` (Potion of Toxic Gas with Invisibility), `bosses-7` (Rogue's Cloak of
  Shadows cancels a pump). All three are a known consumable or class ability used at the fight; there is no Policy that
  spends an item on a boss.
- DM-300: `bosses-12` (armour that absorbs its top hit), `bosses-14` (slow, paralyse or freeze effects), `bosses-15` (Potion
  of Purification against the gas).
- Dwarf King and below, outside the depths the Brain reaches: `bosses-18`, `bosses-23`, `bosses-25`, `bosses-28`.

Tengu has no Tier 1 or 2 claim (`bosses-8` to `bosses-10` are Tier F or Tier 3 at the pin).

Policy decisions that rest on boss claims: the Goo dodge (`goo-pump`, `bosses-1`); the wash (`goo-wash`, `bosses-2`); the
Goo position and engagement (`goo-position`, `goo-engage`, the Rules from #219 that cite `docs/rules/combat.md`); the heal
share against Goo (`goo-engage`, with the measured `goo-position-and-wash` page); the descent rest (`rest-before-descend`,
`docs/rules/buffs.md`), which is not a boss claim but is the rest that keeps the boss floor survivable.

## Decisions

- **Micro-brainstorm.** Four ways to hold the boss numbers: (1) leave the constants in code and only list the claims; (2)
  one `bosses` map of free-form keys per boss; (3) a typed entry per boss with ranges, as the other sections have; (4) a
  generic list of "boss rules". Against: information parity (all four use only constants, pass), the validator's refusal of
  place words (3 keeps it, 2 and 4 weaken it), `HeuristicLinkTest` (3 links each field to the Rule it came from), and the
  epic's wording (typed fields, no layout). Chosen: 3.
- **Pre-mortem.** The ways it fails: moving the constants changes play (the Equivalence test and 80 equal Run logs say it
  does not); a field that reads a hidden fact (all four are numbers the Policy compares with what the screen shows); a
  tuning that wins by noise (every variant is held to both slices, and the Wilson interval is printed); a field no Policy
  reads, which would read as tuning that never ran (Tengu and DM-300 entries are flagged `UNTESTED`, and the story says why).
- **Which of the four fields exist.** "Stand-off distance" is `meet_range` (how far from the fight the Policy walks to ground
  where a dodge would work); "water or not" is `wash_range` (how far it walks to water to wash off the ooze). Both were
  constants and now are fields. "Rest fraction before the stairs down" is `descent_rest_per_mille`, one per boss floor, read
  by `Descend`. "Known consumable" is not built as a field: no Policy spends an item on a boss, so a field would be read by
  nothing. It is idea `2026-10-27-known-consumables-before-a-boss`.
- **`drink_per_mille` is also in the Goo entry.** The heal share against Goo was a constant in `Goo.java`; it is the same
  kind of number as the other two, so it moved with them.
- **Tengu and DM-300** have `descent_rest_per_mille` and `status`; `status` is `UNTESTED` and the validator accepts only
  `TESTED` or `UNTESTED`. None of 100 `bosses` Runs reached depth 10, so there is nothing to tune.
- **Not kept: any value change.** The reference values are `descent_rest_per_mille` 1000, `drink_per_mille` 400,
  `meet_range` 6, `wash_range` 4 and they stay. Variants played: drink share 600 and 250, meet 9 with wash 6, each on the
  tuning slice (40 Runs), and the two that moved anything on the second slice (80 Runs). None beat the reference on both
  slices; drink 600 lost three Goo kills on the second slice. See the Results page.
- **No Sequential test.** No Goo change ships.
- **The Goo gate is not claimed.** That is 4.14.

## Verification

- `./gradlew :api:test :brain:test :harness:test :rig:test -Pshatterfish.testForks=1`.
- `mkdocs build --strict`.
- `:rig:direction --set standard --class WARRIOR --first 40 --salt-base 1000 --baseline origin/main` and `--first 80
  --salt-base 3000`, and `--set bosses --first 100 --salt-base 5000`: see the Results page and the PR.
- The refactor is checked by the direction tables of the reference Playbook against `origin/main`: every column equal on both
  slices, and the Run logs equal ignoring `think_ms`.

## Results

See `docs/results/2026-10-27-7-5-per-boss-playbooks.md`. The reference Playbook plays exactly as `main` (40 of 40, and 80 of
80 equal columns). No variant of the Goo entry won on both slices, so no value changed. Goo kills over fights on
`standard`: 79 of 87 (90.8%, 82.9 to 95.3) on both slices before and after.

## Fairness review

`fairness-reviewer` on `origin/main...HEAD`: pass with findings, none blocking, no information-parity violation. The diff
touches neither `Observer` nor `ActionExecutor`; the Goo fields are whole numbers, and the descent rest reads the depth from
the header. Findings and what was done:

1. The one-argument constructors that default to the reference Goo entry (`Fight`, `Heal`, `OnePly`, `Descend`) are used by
   tests and nothing else; `Brain.policies` is the only construction site. Kept for the existing tests; noted here.
2. The boss floors in `Playbook.Bosses.descentRest` now cite `Dungeon.java:441-443` in the Javadoc. Done.
3. `Descend.rests` reads `depth + 1` from the header only on the main branch. No change.
4. Two more refusals pinned in `PlaybookValidatorTest.boss_ranges`: a seed word as a key under `bosses.goo`, and a seed
   string as a `status` value. Done.
