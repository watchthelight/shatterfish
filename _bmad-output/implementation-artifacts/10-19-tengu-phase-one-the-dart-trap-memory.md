---
title: 'Story 10.19: Tengu phase one, the dart-trap memory'
type: 'feature'
created: '2026-10-29'
status: 'review'
baseline_commit: '3f446413a'
review_loop_iteration: 0
context: []
---

## Intent

**Problem:** With story 10.18 every practice hero reaches the seal, but none gets through the first half of the fight:
the generic fight Policy dithers between an approach and a retreat at two and three cells from Tengu, and the dart
traps he lays are drawn for two turns and then hidden, so a hero that walked on them took 17 poison damage in all.

**Approach:** A `tengu` fight Policy (a hook in `Fight`, after Goo's) strikes when beside him and else steps to the
nearest cell beside him; it never retreats and never waits. The Observer shows a hidden `TenguDartTrap` only while the
game's `FadingTraps` tilemap stands over it. `Memory` keeps the cells of the last drawing while Tengu is in view on the
sealed floor, and the walk goes round them; a trap never drawn is in no Memory.

## Acceptance criteria and where they are held

1. A Tengu fight Policy for phase one, mechanics re-verified with `path:line`: Rule `tengu-fight` in
   `docs/brain-rules.md`, the dart-trap row in `docs/rules/levels.md`; `TenguFightTest`.
2. Melee, no stall, a retreat only when the heal Policy says so: `TenguFightTest.goes_straight_in_and_strikes`,
   `does_not_retreat_or_stall`.
3. Memory keeps dart cells only while they were drawn: `TenguTrapMemoryLeakTest` (the Memory equals the last drawing
   over 200 random histories against a model written apart from `Darts`; an unseen patch is not avoided; the
   permutation control gives equal Decisions before the draw and different ones after it); `FadingTrapsTest` for the
   Observer side (shown under the standing tilemap only, not after, not on a cell the fog hides, not another kind).
4. The first test fails without the Policy (ADR-0019): see the pull request.
5. A CoachLine sentence for the Policy: `tengu/attack`, `tengu/approach`, `tengu/across` in `CoachLine`,
   `coach-words.txt`, `CoachLineTest`, `docs/coach-line.md`.
6. `fairness-reviewer` run with the Memory as the question: no leak found; its kind-filter finding is applied.
7. Practice table, 6 classes x 40, before and after, and the off-floor direction check: `docs/results/e10-practice-kit.md`.

## Design notes

- **Phase cannot be read from the bar.** Eleven pips cannot tell 101 hit points from 100, so the Policy plays the same
  in the second half. Phase 2 (bombs, fire, the shocker) is story 10.20; here only whether heroes reach it is
  measured, from Tengu's yell in the log.
- **Memory is the least that is useful.** Replaced by each new drawing (the jump clears the old patch first,
  `PrisonBossLevel.java:655-667`), dropped when Tengu is out of view, the floor is unsealed or the hero leaves. It
  holds cells only and nothing reads the game.
- **No way round is crossed, not waited out.** The lock stops regeneration and Tengu shoots every turn the hero waits
  (`LockedFloor.java:36-58`), so the walk falls back to the nearest cell across the patch.
- **Off the boss floor nothing changes:** `:rig:direction` against the base commit is identical in all six classes.

## Verification

See the pull request: the practice table, the direction columns and the test that fails without the Policy. Result:
phase 2 reached 0 of 240 before and 240 of 240 after; Tengu killed 0 in both, which is story 10.20's.
