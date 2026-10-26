# Issue #219, step 6: no walking away from a faster enemy

**A direction check, unregistered.** Nothing here ran under a Registration and no sequential test was run. It is the
measurement behind the change to the fight Policy's retreat (`Fight.outpaced`, `Bestiary.fast`): the Brain no longer
walks away on foot from an awake enemy the bestiary tags `speed: fast` within six cells, past a walk of two steps to
the stairs. Upstream `v4.0.2`, Profile 6, the `shatterfish` Brain on `weights/shatterfish.json`, Oracle off. Seeds
from the `standard` set only.

Commands (the baseline is `origin/main` at `e2ee5be5a`, built in a worktree by the tool):

```
./gradlew :rig:direction --args="--set standard --class WARRIOR --first 40 --salt-base 1000 --baseline origin/main --jobs 2 --debug-record"
./gradlew :rig:direction --args="--set standard --class WARRIOR --first 80 --salt-base 3000 --baseline origin/main --jobs 2 --debug-record"
```

## What the baseline's deaths on depths 2 to 4 were

The debug record's end line names the killer. On the 120 baseline Runs, 61 died on depths 2 to 4:

| killer | deaths |
|---|---|
| Crab | 18 |
| Hunger | 9 |
| SentryRoom$Sentry | 6 |
| Swarm | 6 |
| Piranha | 5 |
| Gnoll, Snake | 4 each |
| Slime, Burning | 2 each |
| Bomb, Rat, GnollExile, ToxicGas, Poison | 1 each |

The crab is the top killer by count. In the six tuning-set Runs it killed, the last waits hold
the same thing: the hero at half its health or less (a fifth or less in four of them), an unfavourable fight, and the retreat Policy walking
toward the stairs, up to thirty cells away, with the crab adjacent the whole way, losing health every second or
third step, until the walk ended in a death or in a fight at one or two hit points. A crab has `baseSpeed` 2: it
closes two cells for each the hero walks and attacks once a turn, so a walk away hands it about half an attack a step
and never breaks contact ([the rule](../rules/combat.md), [the claim](../brain-rules.md#faster-enemy-no-outrunning)).
A careful player at that screen fights the crab where it stands, or takes the stairs only if they are a step away.
Hostile mobs do not follow across the stairs ([`hostiles-stay-on-their-floor`](../brain-rules.md#hostiles-stay-on-their-floor)).
Eleven of the 19 tuning-set deaths on these depths were also Starving or Hungry at death; hunger is the larger cause
behind the crab's, and it is not touched here.

## The tables

Tuning set, 40 Runs, salts 1000 to 1039:

| | origin/main | this change | delta |
|---|---|---|---|
| reach depth 5 | 21/40 | 24/40 | +3 |
| Goo kills | 15/40 | 17/40 | +2 |
| median turns | 1935.0 | 1940.5 | +5.5 |
| deepest mean / max | 4.825 / 8 | 5.025 / 8 | +0.20 |
| deaths by depth 1..8 | 0/2/7/10/7/7/5/2 | 0/2/8/6/7/8/6/3 | |
| oscillation Steps | 556 | 650 | +94 |

Second slice, 80 Runs, salts 3000 to 3079:

| | origin/main | this change | delta |
|---|---|---|---|
| reach depth 5 | 33/80 | 35/80 | +2 |
| Goo kills | 25/80 | 27/80 | +2 |
| median turns | 1733.0 | 1807.0 | +74 |
| deepest mean / max | 4.175 / 8 | 4.250 / 8 | +0.08 |
| deaths by depth 1..8 | 5/10/19/13/9/15/7/2 | 5/10/14/16/11/15/8/1 | |
| oscillation Steps | 1070 | 1049 | -21 |

Combined, 120 Runs:

| | origin/main | this change | delta |
|---|---|---|---|
| reach depth 5 | 54/120 | 59/120 | +5 |
| Goo kills | 40/120 | 44/120 | +4 |
| deaths by depth 1..8 | 5/12/26/23/16/22/12/4 | 5/12/22/22/18/23/14/4 | |
| crab deaths on depths 2 to 4 | 18 | 10 (and 1 hermit crab) | -8 |

The crab deaths fall by eight, and the gain in Runs that reach depth 5 and that kill Goo is five and four. Neither is
significant by itself: the Goo baseline page puts the standard error of a 120-Run difference near four and a half
points, and these are 4.2 and 3.3 points. Both slices point the same way, and the mechanism is visible in the logs.
The Runs the crab no longer kills go on to die of other things (Bomb 1 to 4, Hunger 9 to 10, Piranha 5 to 6 on
depths 2 to 4), which is the next cause, not a regression of this one.

`:rig:direction` plays the baseline on the current host code; this change touches the Brain only, so the host-code
caveat of the Goo baseline page does not apply to these columns.
