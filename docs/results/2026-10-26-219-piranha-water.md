# Issue #219, step 6: a piranha is a thing of the water, not a fight

**A direction check, unregistered.** Nothing here ran under a Registration and no sequential test was run. It is the
measurement behind the change that makes the Brain treat a water-bound enemy (the bestiary tag `leave-water`, today the
piranha) as scenery while it cannot reach the hero, walk once to the nearest cell out of its reach when it can, and
plan around the shore afterwards. Upstream `v4.0.2`, Profile 6, the `shatterfish` Brain on `weights/shatterfish.json`,
Oracle off. Seeds from the `standard` set only.

Commands (the baseline is `origin/main` at `d48e2d628`, built in a worktree by the tool):

```
./gradlew :rig:direction --args="--set standard --class WARRIOR --first 40 --salt-base 1000 --baseline origin/main --jobs 6 --debug-record"
./gradlew :rig:direction --args="--set standard --class WARRIOR --first 80 --salt-base 3000 --baseline origin/main --jobs 6 --debug-record"
```

## What the deaths were

Piranha killed 5 of the 120 baseline Runs on depths 2 to 4 (the earlier baseline page, `e2ee5be5a`). In the debug
records four of them were heroes at healthy hitpoints that walked to the water's edge or into the water, were bitten,
and chose Fight, the retreat Policy, or a pickup beside the pool, each turn, until dead. A piranha lives only in
water, bites only a hero beside it, and loses a hero with no water path ([the rule](../rules/combat.md), tier 1,
`Piranha.java:72-77`, `:139`, `:150`, `:171-200`). A careful player steps away from the pool and does not come back
to its edge; the Brain fought it and kept returning to the same shore.

Swarm of flies killed 6 on the same depths. Those deaths are multi-cause: most were heroes already hurt, hungry or
starving, which split a 50 HP swarm into more flies by hitting it (`Swarm.java:90-116`). That is the hunger and
low-hitpoint cause, not touched here.

## The change

- An enemy whose bestiary tactics include `leave-water` is not an enemy while no water it can swim to touches the hero.
- A hero beside such water steps to the nearest cell out of its reach (once per floor, `retreat: leave water`).
- The shore is remembered as soft avoided regions, so explore, descend and pickup route around it and cross only when
  there is no way around. After the one walk, a forced crossing hides the piranha from the Policies so the hero keeps moving.
- Water the piranha cannot swim to is ignored. Every other enemy is handled as before.

## The tables

Tuning set, 40 Runs, salts 1000 to 1039:

| | origin/main | this change | delta |
|---|---|---|---|
| reach depth 5 | 24/40 | 26/40 | +2 |
| Goo kills | 17/40 | 19/40 | +2 |
| median turns | 1940.5 | 2000.0 | +59.5 |
| deepest mean / max | 5.025 / 8 | 5.075 / 7 | +0.05 |
| deaths by depth 1..8 | 0/2/8/6/7/8/6/3 | 0/2/6/6/7/11/8/0 | |
| oscillation Steps | 650 | 566 | -84 |

Second slice, 80 Runs, salts 3000 to 3079:

| | origin/main | this change | delta |
|---|---|---|---|
| reach depth 5 | 35/80 | 40/80 | +5 |
| Goo kills | 27/80 | 35/80 | +8 |
| median turns | 1807.0 | 1776.5 | -30.5 |
| deepest mean / max | 4.250 / 8 | 4.575 / 8 | +0.33 |
| deaths by depth 1..8 | 5/10/14/16/11/15/8/1 | 6/10/10/14/6/16/16/2 | |
| oscillation Steps | 1049 | 1020 | -29 |

Combined, 120 Runs:

| | origin/main | this change | delta (paired SE) |
|---|---|---|---|
| reach depth 5 | 59/120 | 66/120 | +7 (+5.8 points, SE 3.6) |
| Goo kills | 44/120 | 54/120 | +10 (+8.3 points, SE 3.5) |

The Runs are paired by seed, and the standard error is that of the paired per-Run difference. The Goo gain is 2.4
standard errors, reach 1.6. The second slice was the confirmation: the first lever set was tuned on the 40-Run slice
and the 80-Run slice, drawn from other salts, moved the same way and further. Piranha killed 5 of the 120 Runs on depths
2 to 4 with this change as well (2 and 3 by slice), so the gain is not a count of fewer piranha deaths alone: the
heroes that stopped fighting at the shore spent their turns elsewhere and reached Goo. Swarm deaths on those depths
were 6, unchanged. An earlier version that only refused to fight raised piranha deaths from 2 to 5 on the tuning set,
because explore and pickup walked back to the shore; the avoided shore regions are what fixed that.

`:rig:direction` plays the baseline on the current host code; this change touches the Brain only, so the host-code
caveat of the Goo baseline page does not apply to these columns.
