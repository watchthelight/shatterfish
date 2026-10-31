---
title: 'Story 10.38: The Goo fight on depth 5'
type: 'feature'
created: '2026-10-30'
status: 'review'
baseline_commit: 'b51a2cd31'
review_loop_iteration: 0
context: []
---

## Intent

**Problem:** Goo killed 126 of the 157 depth-5 deaths in the Prison diagnosis (story 10.7, issue #353).

**Approach:** Read the fight against the pinned code, find the turns the hero wastes, change one thing, and keep it only if it is not worse on
both direction slices (salt bases 1000 and 3000) and on the practice floor.

## Decisions

- **Alternatives weighed.** (1) A ranged volley while Goo walks up. (2) Dry-ground positioning so Goo arrives on a cell that does not heal it.
  (3) Retreat upstairs to rest. (3) is closed by the code: the seal starts the turn Goo wakes and refuses every transition (Goo.java:134-136),
  and the HP at the seal is already 0.92 to 0.94. (2) is the lever for melee classes and is not built here. (1) was built: it needs no new number
  and the staff, bow and missiles were kept out of the Goo fight by their own policies.
- **Shared helpers.** `Zap.zapAt`, `Shoot.shotAt` and `Ranged.throwAt` are the choices the policies already made, now static and shared. Goo's
  throw accepts every plain missile; the ordinary ranged policy keeps the damage-worth filter.
- **No Playbook or tunables change**, because the volley has no threshold.

## Result

Practice floor 213 to 228 Goo kills of 240; direction slice 1000: reach d6 87 to 94, slice 3000: 71 to 78. See
`docs/results/2026-10-30-10-38-the-goo-fight.md`. Kept.

## Left over

Dry-ground positioning (water heal) for the melee classes; food (story 10.39).
