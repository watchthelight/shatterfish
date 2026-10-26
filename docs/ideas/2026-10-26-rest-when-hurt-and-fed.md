# Rest before exploring on when hurt, fed and alone (measured, not kept)

Issue #219, the Goo gate. On main `fa5e15cd4` the tuning slice (standard, WARRIOR, salts 1000 to 1039) reaches depth 5 in
28 of 40 Runs and kills Goo in 25; Goo converts 25 of 28. So reach, not the fight, is the gap: 12 Runs died on depths 2
to 4 (deaths by depth 0/1/7/4/3/...), and only 3 died to Goo.

From the debug records of those 12 deaths: crabs 5 (hold or cornered), starving with a gnoll scout or crab 3, giant
piranha shores 2, a swarm of flies 1, a slime 1. Most of them met the killer at under half health, because the explore
Policy only rests before a descent (`rest: descent`, `rest: before-descent`) and never after a fight on the way: one
hero explored at 6 of 30 and walked into a crab at 6.

## The lever

In `Explore.plan`, before the frontier, rest (`Rest(true)`) when the hero has under 50% of its hit points, the hunger
icon is off, and `Larder.frugal` is false (food budget at least 600 turns). Regeneration is a hit point every ten turns
(`Regeneration.java:44`, `:69`) and stops while starving (`:56`), which is why the food guard is there. Information
parity holds: it reads only hit points, the hunger icon and the pack.

## Numbers (against origin/main `fa5e15cd4`, `--jobs 2`)

| slice | reach depth 5 | Goo kills | median turns |
|---|---|---|---|
| tuning 40, salt 1000 | 28 to 30 | 25 to 27 | 2130.5 to 2262.0 |
| 80, salt 3000 | 44 to 45 | 41 to 40 | 1974.0 to 2045.0 |

Combined 120 Runs: Goo kills 66 to 67. That is inside the noise (about 3 on 40 Runs, 4 on 80), and the second slice
went the wrong way, so the lever is not kept. No PR. Median turns rose by 70 to 130, the cost of the rests.

## What the table cannot show

Hunger already kills about one Run in six on depths 2 to 4, so each rest competes with food; the gain from fewer fights
at low health is roughly cancelled by the extra hunger. A smaller threshold (under 33%) or resting only when food
held is at least a ration might separate the two, but they are a second lever and were not tried.
