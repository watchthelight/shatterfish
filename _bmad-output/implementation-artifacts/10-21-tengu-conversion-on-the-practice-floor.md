---
title: 'Story 10.21: Tengu conversion on the practice floor'
type: 'feature'
created: '2026-10-30'
status: 'review'
baseline_commit: '0fc078241'
review_loop_iteration: 0
context: []
---

## Intent

**Problem:** After stories 10.18 to 10.20 every practice hero reaches phase two (240 of 240) and none kills Tengu (0
of 240); the median death is at 128 to 138 turns, with him at about 4 of the bar's steps. The Run log did not say where
the hero's hit points went.

**Approach:** Diagnose first: a damage-by-source table in the practice output, built only from what the screen and the
game log show. Then one change at a time, each measured at 40 Runs per class on two slices (salts 1000 and 3000), kept
only when it is not worse.

## Acceptance criteria and where they are held

1. Damage by source per Run that died (melee, thrown, bomb, fire, shock, poison, dart trap, unknown, healed back, and
   the share of the loss in turns that cannot be split): `PracticeDamage`, `PracticeDamageTest`, the table in
   `docs/results/e10-practice-kit.md`. The ground effects the split needs are written to the debug record of a
   sealed depth-10 wait (`DebugRecord`).
2. Tengu's health at the hero's death is the existing "boss pips at death" column (median 4 of the bar).
3. Loss causes written on the results page: thrown attacks about 48 percent, melee 28, bombs 20, fire and shocker 4,
   plus wasted healing.
4. One kept change: the drink against Tengu alone, `Playbook.Tengu.drinkPerMille` (Rule `tengu-heal`, tunables
   registry, `playbooks/shatterfish.json` and `oneply.json`). The first test, `TenguConversionHealTest`, fails on `main`
   (there the danger against Tengu is the generic estimate, 40 of 60, and the hero drinks at 36).
5. `--first 10` off-floor direction check: identical columns (Warrior only, see below).
6. `fairness-reviewer` run on the diff.

## Design notes

- **Micro-brainstorm.** Levers the diagnosis pointed at: (E1) drink later against Tengu alone, because about 40
  percent of each potion went to the cap; (E2) a higher `adjacent_cost`, to stand and strike through more thrown
  attacks; (E3) a higher `bomb_cost`, to dodge more of the bombs; (C1) phase-one jump geometry; (D1) a walk that
  shortens the distance he jumps to. C1 was dropped as a poor return (phase one is already cleared in 240 of 240).
  D1 needs the jump landing rule cited from the pinned code and a new Policy branch; it is left to the idea file.
- **Why 400.** The same shape as Goo's drink (`Playbook.Goo.drinkPerMille`): a share of the hit points. The
  value was not swept; one value was tried and kept because it was not worse on two slices. Lower shares were not
  tried.
- **Damage by source is the screen's split.** Melee and thrown are told apart by Tengu standing beside the hero; a
  turn with a bomb and a blow in it is not split and is reported as such (20 to 32 percent of the loss). Nothing in the
  split is read by the Brain: it reads the Run's debug record after the Run.
- **A snapshot trap.** The first E1 run was invalid: the frozen classpath held a stale `brain` jar (`:rig:classes` does
  not rebuild the module jars), so the table matched the baseline to the decimal. Its config hash is the same as the
  baseline's except for the Playbook; the second run is the one in the results page.

## Result

See `docs/results/e10-practice-kit.md`, "Tengu conversion after story 10.21".

| | Tengu killed, salt 1000 | salt 3000 |
|---|---|---|
| before | 0 of 240 | 0 of 240 |
| after E1 | 3 of 240 (Mage 2, Duelist 1) | 0 of 240 |

Not a conversion. The story merges the instrument, the diagnosis and E1. E2 and E3 were worse (0 kills on salt 1000)
and are dropped.

## Not done

- The full 200-per-class-per-kit table: not run. The fixed kits other than `standard` do not exist in the harness
  (`KitProfiles` has `standard` and `arrived`), and with 0 to 1.3 percent rates a larger table would only repeat the
  zero.
- The `--first 10` direction check ran for the Warrior only.
- The Mage ranged trade is still untested; the 20-picture gallery reading was replaced by the debug-record reading of
  all 240 losses.
- Next: `docs/ideas/2026-10-30-tengu-thrown-attack-tax.md`.
