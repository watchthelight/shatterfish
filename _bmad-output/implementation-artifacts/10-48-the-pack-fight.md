---
title: 'Story 10.48: The fight uses the pack'
type: 'feature'
created: '2026-11-09'
status: 'review'
baseline_commit: '28f4fe38d'
review_loop_iteration: 0
context: []
---

## Intent

**Problem:** heroes die holding known liquid flame, frost, attack wands and stones of fear (story 10.43 read 24 Guard
deaths and found them in most packs). The Brain throws only missile weapons (`Ranged`) and zaps only the Mage's staff
(`Zap`); nothing uses a known harmful potion or a loose wand against an enemy the hero will not outfight.

**Approach:** a new Policy, `pack`, that throws known liquid flame or frost and zaps a known attack wand at an awake,
mobile enemy 2 to 7 cells off on a clear line when the fight is not favourable and the hero needs four turns or more to
kill it. The burn and freeze numbers are cited at `v4.0.2` by `path:line` in `docs/rules/combat.md`; fire is thrown only
when no flammable ground joined to the landing cells reaches the hero, read from the drawn tiles. Measured on the two
ALL-40 standard slices (salt bases 1000 and 3000, `--jobs 4`) against the merge-base SHA `28f4fe38d`, with the epic's
keep rule: pooled over both slices not worse in reach depth 5 and Goo kills, no class 8 or more worse.

## Scope fences

- The `goo`, `holdout` and `tengu` sets are not played. The Policy is off on a sealed floor, so the Goo and Tengu
  fights are untouched in the first measurement; Goo is added only if the numbers support it.
- Stones of fear are not used: a terrified mob flees for twenty turns and comes back (`Terror.java:33`), which spends the
  stone and solves nothing.

## Alternatives weighed (before building)

1. Extend `Ranged` to throw potions. Rejected: `Ranged` only enters on a favourable fight (or when cornered), the opposite
   of when the pack is worth spending, and it is built around a missile's tier and strength.
2. Put the use in the fight Policy as one more ranked Choice. Rejected: the fight Policy is the largest file and already
   carries nine special cases; a Policy of its own has its own test, rule row and reason.
3. A Policy of its own, between `zap` and `shoot`. Chosen.
4. Use a stone of fear, a warding wand or a corruption wand as well. Rejected: no bolt damage, and corruption needs the
   enemy hurt (`WandOfCorruption`: resistance is five times as high at full health).

## Pre-mortem

- Frost is thrown at an enemy and the hero walks into its own cloud and freezes beside the enemy. Mitigation: the walk
  treats a `Freezing` blob as closed ground (`Explore.walkable`), and frost is thrown from three cells or more.
- Fire comes back over grass. Mitigation: `Pack.fireSafe`, tested on the tiles as drawn.
- The pack is spent on crabs at depth 3 and is gone at depth 7. Accepted: the Policy enters only on a fight the Brain
  already judges unfavourable; the measure is the judge.
- A wand with a hidden curse (30%) does a random bad thing. Accepted: it enters only on a fight the hero would lose.

## Outcome

(filled in at the end)
