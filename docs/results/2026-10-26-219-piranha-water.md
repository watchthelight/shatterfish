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

The baseline is `origin/main` after #264 (the sentry floor): `b1bed0e8d` for the tuning slice, `a8aa3589d` for the
second. An earlier measurement against `d48e2d628` (before #264) read reach +7 and Goo +10 on 120 Runs; the sentry fix
took part of that gain, so the numbers below are the ones that count.

Tuning set, 40 Runs, salts 1000 to 1039:

| | origin/main | this change | delta |
|---|---|---|---|
| reach depth 5 | 28/40 | 28/40 | 0 |
| Goo kills | 20/40 | 21/40 | +1 |
| median turns | 2066.5 | 1971.0 | -95.5 |
| deepest mean / max | 5.300 / 8 | 5.225 / 7 | -0.08 |
| deaths by depth 1..8 | 0/1/7/4/8/10/7/3 | 0/1/7/4/7/12/9/0 | |

Second slice, 80 Runs, salts 3000 to 3079:

| | origin/main | this change | delta |
|---|---|---|---|
| reach depth 5 | 40/80 | 44/80 | +4 |
| Goo kills | 29/80 | 35/80 | +6 |
| median turns | 1873.5 | 1818.0 | -55.5 |
| deepest mean / max | 4.425 / 8 | 4.688 / 8 | +0.26 |
| deaths by depth 1..8 | 4/9/14/13/14/15/10/1 | 5/9/10/12/10/16/16/2 | |

Combined, 120 Runs:

| | origin/main | this change | delta (paired SE) |
|---|---|---|---|
| reach depth 5 | 68/120 | 72/120 | +4 (+3.3 points, SE 3.5) |
| Goo kills | 49/120 | 56/120 | +7 (+5.8 points, SE 3.4) |
| Piranha deaths on depths 2 to 4 | not recorded | 6 (3 and 3 by slice) | |
| Swarm deaths on depths 2 to 4 | 6 (earlier baseline) | 7 | |

The Runs are paired by seed, and the standard error is that of the paired per-Run difference. Neither gain is
significant: Goo is 1.7 standard errors and reach 0.9. Both slices move the same way for Goo kills, and the tuning slice
shows no reach gain. The change does not cut piranha deaths to zero; the heroes it saves go on to die of other things
(swarm, hunger), which is the next cause. An earlier version that only refused to fight raised piranha deaths from 2
to 5 on the tuning set, because explore and pickup walked back to the shore; the avoided shore regions are what fixed that.

`:rig:direction` plays the baseline on the current host code; this change touches the Brain only, so the host-code
caveat of the Goo baseline page does not apply to these columns.
