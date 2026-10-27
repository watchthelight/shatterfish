---
title: 'Story 7.4: Upgrade allocation'
type: 'feature'
created: '2026-10-26'
status: 'review'
baseline_commit: 'f51e9dae1'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent, do not modify unless human renegotiates">

## Intent

**Problem:** the Brain reads every scroll of upgrade it finds onto the worn weapon or the worn armour by a rule written in
story 4.13 (the weapon on a tie), and nobody has measured whether that is where the scroll does most. Two scrolls are read
by the Goo's floor, so the lever is small, and a wrong allocation could cost as much as a right one gains.

**Approach:** the Playbook's `upgrade` section decides the target by fields, a measured allocation is kept only on recorded
numbers, and the mechanics it rests on are Rules with citations. No Action kind is added.

## Acceptance Criteria (from epics.md Story 7.4, with what holds them)

1. The story file states the allocation as it stands, from the code, and the count of upgrade scrolls found and read by
   depth 5 from the `standard` Run logs. Held by "The allocation as it stands" and "The lever's size" below, and by the
   `upgrade scrolls picked up` and `upgrades read` rows `:rig:direction` now prints (`DirectionMetricsTest`-style table in
   `DirectionTest`).
2. The mechanics (what an upgrade adds to armour, a weapon and a ring at `v4.0.2`, the cursed-item and strength-penalty
   interactions, the cost of reading onto an item whose level is not shown) are Rules with `path:line` citations and a row in
   the Brain's Rules index. Held by `docs/rules/upgrades.md` and `upgrade-allocation` in `docs/brain-rules.md`.
3. The `upgrade` section chooses among the targets the Observation shows by fields, and `UpgradePlaybookTest` asserts each
   choice on a constructed pack, including that no piece the hero does not wear is ever the target, and that a weapon whose
   level is not shown is not a target unless a field allows it. The worst case of an unknown scroll passes `SafeTest`
   exactly as before (the read goes through the same `plan`).
4. The story uses the upgrade window through the existing `Answers` path and adds no Action kind. "The heaviest armour the
   hero can wear after the strength it would take" is reachable by the Actions that exist (any pack item is a target of
   `UseItemOn`) but not by the Brain: see Decisions, and #305.
5. Rig discipline: tuning and confirmation tables, the added column "upgrades read by depth 5", revert-if-not-kept. Held by
   `docs/results/2026-10-26-7-4-upgrade-allocation.md`.
6. PR labelled `fairness` with the reviewer's verdict.

</frozen-after-approval>

## Code Map

- `shatterfish/api/.../Playbook.java`: `Upgrade` gains `hold_until_depth` (0 to 26) and `unknown_weapon` (`NO` or `YES`);
  the fields are in `SPECS`, so the validator reads them with the constructor.
- `shatterfish/brain/.../TestItem.java`: `upgrade` holds a known scroll above the depth, `target` passes over a weapon whose
  level is not shown unless the field allows it.
- `shatterfish/rig/.../DirectionMetrics.java`: the two upgrade rows of the PR table.
- `playbooks/shatterfish.json` (version 2, `tie` ARMOR), `playbooks/oneply.json` (written canonically).
- `docs/rules/upgrades.md`, `docs/brain-rules.md` (`upgrade-allocation`), `docs/adr/0023-playbooks-as-data.md`,
  `docs/results/2026-10-26-7-4-upgrade-allocation.md`.
- `UpgradePlaybookTest` (brain), `PlaybookTest`, `PlaybookValidatorTest`, `PlaybookFileTest`, `PlaybookEquivalenceTest`.

## The allocation as it stands (from the code)

`TestItem.target(observation, upgrader)` picks the worn weapon while its shown level is below the worn armour's plus
`weapon_lead`, the worn armour above, and on the line the `tie` slot; a weapon whose level is not shown is never picked, and
with no armour worn there is no target (`TestItem.java`, `target`). A known scroll of upgrade is read onto it at any health
unless the hero is blinded or immune to magic (`TestItem.upgrade`); an unknown scroll at full health is read onto the same
target, except the Mage's staff. `Answers.upgrade` confirms the window the read opened, and the chained window the game opens
while more scrolls are held, as long as the first read went onto a worn piece, so a stack held together goes onto one item.
At story 4.13 the values were `weapon_lead` 0 and `tie` WEAPON, which 7.2 moved into the Playbook without changing play.

## The lever's size

From `:rig:direction --set standard --class WARRIOR --first 40 --salt-base 1000` on `main` (`f51e9dae1`): 50 scrolls of
upgrade were picked up as heaps and 88 upgrades were read by depth 5, 2.2 a Run, 37 of 40 Runs read at least one, the most in a
Run is 3, all on depths 1 to 4, 56 onto the worn weapon and 32 onto the armour. On the 80-Run slice: 77 picked up, 143 read
(1.8 a Run). The lever is two scrolls, read on the first four floors. The heap count undercounts: chests, mimics and rewards
give scrolls with no heap.

## Decisions

- **Micro-brainstorm.** Five ways to allocate: (1) keep the weapon-first rule; (2) armour first; (3) weapon first; (4) a
  balanced line, `tie` armour with the weapon only when it is behind; (5) hold the scrolls until a better piece is worn;
  (6) the heaviest armour in the pack. Scored against: information parity (all use only the Observation: pass), reachable
  by existing Actions (all but 6 in effect, see below), measurable (all). 1 to 5 were played; 6 was not built.
- **Pre-mortem.** The ways it fails: two scrolls cannot be told from noise (the tables say how big the change is and a second
  slice confirms it); an allocation that wins reach and loses Goo kills (armour first did on the second slice, and was not kept
  for it); a field that reads a hidden fact (the two new fields read a depth and whether a level is shown, both on the screen);
  a Playbook change that silently changes the Equivalence test (it now compares the committed file with the upgrade section set
  back to the reference).
- **Fields over a rewrite.** `weapon_lead` and `tie` already express armour-first (lead -5), weapon-first (lead 5) and the
  balanced line; the story adds only `hold_until_depth` and `unknown_weapon`. The heaviest-armour option needs a field and a
  Policy change that is not this story's (next item).
- **The heaviest armour is not built.** An upgrade read onto a spare armour in the pack is a legal read, but `Equip` would
  never put it on: it replaces a worn piece only with a piece whose name is plain (`Equip.gear`) and never replaces a worn
  piece that shows an upgrade (`Equip.java`), so the scroll would be spent on gear the Brain does not wear. The AC says to stop
  and file when a target cannot be reached by the Actions that exist; this one can, but not usefully. Filed as #305 with what
  to change first (Equip rating an upgraded unworn armour), and no field added for it.
- **A scroll is never read onto a piece the hero does not wear**, and the Rules say why none of the others pays (no rule
  makes a spare weapon or armour better than the worn one at the same level).
- **Unknown scrolls follow the target.** An unknown scroll is read onto the same target and goes through the same `SafeTest`
  verdict; the hold applies only to a known scroll, since an unknown one cannot be told from another scroll and is tested as an
  unknown scroll is (story 4.10).
- **Kept: `tie` ARMOR, `weapon_lead` 0 (Playbook version 2).** The only allocation that beat `main` on both slices and both
  factors (docs/results/2026-10-26-7-4-upgrade-allocation.md). Not kept: armour first (conversion 68% on the second slice),
  weapon first (-8 on the first), and the hold (no change). `hold_until_depth` and `unknown_weapon` stay at the reference
  values and are fields for 7.5 and 7.6.
- **No Sequential test.** The epic's AC asks for one on a step that wins on both slices. The instruction for this story set
  the measurement as the tuning and confirmation slices, and a registered comparison needs a second registered Brain that plays
  the old Playbook, which the Rig has not got. Said in the PR and on the Results page; filed as a follow-up.

## Verification

- `./gradlew :api:test :brain:test :harness:test :overlay:test :codex:test :rig:test -Pshatterfish.testForks=1`.
- `mkdocs build --strict`.
- `:rig:direction --set standard --class WARRIOR --first 40 --salt-base 1000` and `--first 80 --salt-base 3000`: see the
  Results page and the PR.

## Results

See the Results page, `docs/results/2026-10-26-7-4-upgrade-allocation.md`: the kept allocation is 87 of 120 Runs to depth 5
and 79 of 120 Goo kills over two slices, against 76 and 68 for `main`.
