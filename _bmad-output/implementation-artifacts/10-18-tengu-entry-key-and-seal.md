---
title: 'Story 10.18: Tengu entry, key and seal'
type: 'feature'
created: '2026-10-29'
status: 'review'
baseline_commit: 'e26cbe562'
review_loop_iteration: 0
context: []
---

## Intent

**Problem:** On the prison boss floor (depth 10) the Brain took the stairs up as "descend", or rested in the hallway
until it starved, so no practice Run ever reached Tengu. On the practice branch's own table 0 of 240 Runs reached the
seal: 189 left the floor by its stairs and 51 starved.

**Approach:** A `tengu` Policy (`Tengu.java`) between `equip` and `descend`. With the iron key in the journal and the
locked door drawn, it rests to the Playbook's `enter_per_mille` (bounded by `rest_limit`, never while starving), walks
to the door and hands over the Unlock. The pick-up and explore Policies already fetch the key from the side cells and
walk into Tengu's cell once the door is open, which seals the floor (`PrisonBossLevel.java:580-583`). The stairs up are
withheld from the offered actions on the unsealed boss floor. Everything it reads is on the screen: the locked-door
tile, the key note, hit points, hunger, depth, branch and the sealed flag. No `Observer` change.

## Acceptance criteria and where they are held

1. The Brain never takes the stairs up on the unsealed boss floor: `TenguEntryTest.the_stairs_up_are_no_way_down`,
   `TenguEntryRunTest`.
2. It walks to the cell door with the key held, rests first when hurt, and unlocks: `TenguEntryTest`
   (`walks_to_the_door_and_unlocks`, `rests_before_the_seal`, `the_rest_is_counted`, `without_the_key`,
   `only_on_the_unsealed_boss_floor`).
3. A practice Tengu Run of every class gets the key and reaches the seal within 600 waits: `TenguEntryRunTest`
   (fails on the practice branch before this story, ADR-0019).
4. The Playbook's `bosses.tengu` entry is data (`enter_per_mille`, `rest_limit`): `PlaybookTest`, `PlaybookFileTest`.
5. Rule `tengu-entry` cites `path:line` in `docs/rules/levels.md`; CoachLine templates `tengu/rest|unlock|door`:
   `BrainRulesIndexTest`, `HeuristicLinkTest`, `CoachLineTest`.
6. The practice table reports key held and seal reached, read from the debug record: `PracticeReachColumnsTest`.

## Design notes

- **The seal is left to the explore Policy.** Once the door is a plain door the frontier is Tengu's cell; no memory of
  the door was needed in the 240 Runs.
- **The up stairs are filtered, not ranked.** Descend searches for an exit that does not exist on this floor and the
  fallback picked at random, including Ascend; withholding the action removes the failure rather than outbidding it.
- **Rests are counted** for this Policy too (`Memory.rests()`), so the limit holds per floor.

## Verification

See pull request #397: the per-class table (240 of 240 Runs hold the key and reach the seal; 0 kills, the fight is
stories 10.19 and 10.20) and the Goo direction check (identical columns against the practice branch).
