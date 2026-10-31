---
title: 'Story 10.17: The power curve by depth'
type: 'feature'
created: '2026-10-31'
status: 'review'
baseline_commit: 'fdebf751b'
review_loop_iteration: 0
context: []
---

## Intent

**Problem:** The Prison deaths on depths 5 to 9 are about fighting strength (story 10.7, issue #363). A known
potion of strength sat unused in the pack on 49 percent of heroes on depth 5 and 71 percent on depth 7.

**Approach:** Measure the power curve by depth, then try the two approved levers and keep what is not worse on
both slices.

## Decisions

- **Lever 1 (kept).** The Playbook's `known_gains` goes from 0 to 1, so `TestItem.gain` drinks a known potion of
  strength or experience when the screen is calm. Story 7.3 measured it on Warrior only and shipped 0.
- **Lever 2 (dropped).** An `equip.armour_short_cost_per_mille` of 2000 (the game's two armour a missing point,
  `Hero.java:652-656`) gave a table identical to lever 1 alone on ALL-40 at salt 1000. It was built, measured
  and reverted; the tunable is not in the registry.
- **Keep rule.** Pooled over the six classes on both slices, a class dropping by more than 8 the exception.

## Acceptance

- `StrengthPotionTest` fails on `fdebf751b` (the shipped Playbook states `known_gains` 0) and passes.
- `PowerCurvePageTest` reads the logs the page cites.
- Results page: `docs/results/2026-10-31-10-17-the-power-curve.md`. Pooled reach depth 5: 123 to 134 (slice 1000),
  106 to 120 (slice 3000); Goo kills 98 to 105 and 80 to 90; no class below its baseline by more than 4.
- Warrior-80 at salt 3000: reach depth 5 54 to 56, Goo kills 47 to 48.

## Not done

The per-class table on the page is depth 6 only; the files hold depths 1 to 8. Heavier armour is not worn in any
Run and is the next lever.
