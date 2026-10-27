# Upgrade allocation

Story 7.4. The Playbook's `upgrade` section decides which worn piece takes a scroll of upgrade and when
(`weapon_lead`, `tie`, and the two fields this story added, `hold_until_depth` and `unknown_weapon`). This page
holds the count that sizes the lever, the allocations that were played, and the one that was kept. These are
direction checks (ADR-0012), not a published rate: nothing here is ranked, and `goo` and `holdout` were not run.

Mechanics the allocation rests on are Rules on [Upgrades](../rules/upgrades.md) and the row
[`upgrade-allocation`](../brain-rules.md#upgrade-allocation).

## How big the lever is

Counted from the Run logs of `:rig:direction --set standard --class WARRIOR` on `main` (`f51e9dae1`), depths 1 to 5.
"Read" is an answered upgrade window, one per upgrade spent; "picked up" is a plain heap of a scroll of upgrade taken,
which undercounts (a chest, a mimic and a reward give scrolls with no heap).

| | tuning (40 Runs, salts 1000..) | confirmation (80 Runs, salts 3000..) |
|---|---:|---:|
| scrolls of upgrade picked up | 50 | 77 |
| upgrades read | 88 (2.2 a Run) | 143 (1.8 a Run) |
| Runs that read at least one | 37 of 40 | not counted |
| read onto the worn weapon | 56 | 90 |
| read onto the worn armour | 32 | 52 |
| read onto a found weapon | 0 | 1 |
| most in one Run | 3 | not counted |
| depth of the reads | 25, 22, 21, 20 on depths 1 to 4; none on 5 | not counted |

A Run reads two upgrades by the Goo's floor, and every one of them is read on depths 1 to 4. That is a small lever,
and the allocation of two scrolls can only matter through what it does to the gear that is worn afterwards.

## What was played

Tuning slice `--set standard --class WARRIOR --first 40 --salt-base 1000`, each against `main` read from its folder.
Reach is Runs that reached depth 5; conversion is Goo kills over reach.

| Allocation | `weapon_lead` / `tie` / `hold_until_depth` | reach depth 5 | Goo kills | conversion | deaths by depth 1..8 |
|---|---|---:|---:|---:|---|
| main (weapon on a tie) | 0 / WEAPON / 0 | 30/40 | 27/40 | 90% | 0/0/7/3/3/18/6/3 |
| armour first | -5 / WEAPON / 0 | 34/40 (+4) | 28/40 (+1) | 82% | 0/1/2/3/8/12/9/5 |
| weapon first | 5 / WEAPON / 0 | 22/40 (-8) | 19/40 (-8) | 86% | 0/1/7/10/3/17/2/0 |
| armour first, held to depth 3 | -5 / WEAPON / 3 | 34/40 (+4) | 28/40 (+1) | 82% | 0/1/3/2/7/15/8/4 |
| armour on a tie | 0 / ARMOR / 0 | 33/40 (+3) | 32/40 (+5) | 97% | 0/1/3/3/2/15/16/0 |

`unknown_weapon` was not played: a Warrior's weapon is identified at the start, so the field changes no Decision for it
(`UpgradePlaybookTest.unknown_weapon` asserts it on a constructed pack).

Confirmation slice `--first 80 --salt-base 3000`, the two allocations that beat `main` on the tuning slice:

| | main | armour first | armour on a tie |
|---|---:|---:|---:|
| reach depth 5 | 46/80 | 53/80 (+7) | 54/80 (+8) |
| Goo kills | 41/80 | 36/80 (-5) | 47/80 (+6) |
| conversion | 89% | 68% | 87% |
| median turns | 1987.0 | 1981.5 | 2171.5 |
| deepest mean / max | 4.800 / 8 | 4.900 / 8 | 5.200 / 8 |
| deaths by depth 1..8 | 5/9/9/11/9/18/15/4 | 5/8/8/6/17/19/12/5 | 5/8/8/5/8/16/25/5 |
| oscillation Steps | 1174 | 1127 | 1302 |
| heaps taken / seen | 5552/6150 | 5603/6202 | 5889/6519 |
| upgrade scrolls picked up by depth 5 | 77 | 83 | 85 |
| upgrades read by depth 5 | 143 | 148 | 150 |

## What was kept, and why

**Armour on a tie** (`tie` ARMOR, `weapon_lead` 0, Playbook version 2). It is the only allocation that beat `main` on
both slices and on both factors of the gate: reach (+3 and +8) and Goo kills (+5 and +6), 87 of 120 against 76 of 120 for
reach and 79 of 120 against 68 of 120 for Goo kills over the two slices. The noise on Goo kills is about 5 in 120 Runs,
so +11 is more than twice that. Armour first reached depth 5 more often on both slices and lost conversion on the second
(68%), which is why it is not kept; weapon first lost on the tuning slice by eight and was not taken to a second.

The hold (`hold_until_depth` 3) changed nothing on the slice it was played on (34 and 28, the same as without it) and is
left at 0. The field stays in the Playbook as a lever for 7.5 and 7.6, where a boss entry may want scrolls kept for the
fight.

### A reading of why, which the tables do not show

The 4.x Brain's Equip Policy does not replace a worn piece whose level shows an upgrade (`Equip.java`, the
`visiblyUpgraded() > 0` skip), so a weapon that took the first scroll is never swapped for a better one found
afterwards. The logs show it: weapons worn by depth 5 (the `wear:` Decisions) are 0 in 40 Runs and 2 in 80 on `main`, 9 and
18 with armour first, 3 and 7 with armour on a tie. The allocation that keeps the starting weapon plain longest swaps
weapons most; armour first swaps most of all and loses conversion, which this page cannot explain and does not try to.

## The strategy log

`:rig:strategy` over the 80 Runs of the confirmation slice, for `main` and for the kept allocation (all depths): 89 and 107
reads of a known or unknown scroll onto a worn piece, 154 and 172 upgrade windows answered, and no read handed over twice on
consecutive waits in either, so no refused read is repeated and no window is left open. The answers outnumber the
known reads because an unknown scroll that turns out to be an upgrade, and the window the game chains while more scrolls are
held, open the window without a known read.

## What the tables cannot show

- Two slices of one class on one seed set, 120 Runs, no Sequential test. The epic's acceptance criteria for this story
  ask for a registered Sequential test on `standard` against `main` (ADR-0012) for a step that wins on both slices; it
  was not run, because a registered comparison needs two registered Brains and the Rig has no second Brain that plays
  the old Playbook. The result is a direction, kept on the E4 rule of two slices (CLAUDE.md), and the Registration is
  filed as a follow-up.
- Runs differ a little between invocations until #303 lands (the hash-order choice of an enemy), so a few Runs per 40 may
  differ for that alone.
- Oscillation Steps rose with the kept allocation (+128 over 80 Runs, 1174 to 1302) and so did heaps taking over 100 waits
  (212 to 224 of a larger number seen). Both come with the Runs going deeper (deepest mean 4.8 to 5.2), which adds floors
  and heaps; the table cannot separate that from a cost.
- Warrior only. The other classes have a class entry to hold their own allocation when 7.6 measures them.
- The heaviest armour in the pack is not a target. The Actions reach it, but nothing would put it on after the scroll
  (#305).
