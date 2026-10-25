# From the bestiary (lore/bestiary): Brain levers

The [bestiary](../bestiary/index.md) turns every enemy's mechanics into tactics and tags
(`tactics/bestiary.json`). These are the levers it opens for the Brain, ranked by the impact expected
on the current goal, a Warrior reaching depth 5 and killing Goo. The ranking is a hypothesis for the
rig, not a measurement: on story 4.13's tuning set 30 of 40 heroes die before depth 5, 16 of them on
depths 3 and 4, those not starving dying in fights at strength 11 in tier-1 gear
([direction check](../results/2026-09-26-4-13-direction-check.md)). Each lever cites the cards it rests on.

0. **Prerequisite: load the bestiary as an `api` record.** The rig reads `tactics/bestiary.json` the
   way `CodexKnowledge` reads the Codex tables and hands the Brain a record per enemy, keyed by the
   name the Observation shows, beside `Codex.Threat`. Fourteen names belong to more than one class
   (the three gnoll shamans, the boss and vault copies), so the key is the name plus the depth. The
   tags are general game knowledge, which non-negotiable 1 allows. No impact by itself; every lever
   below reads it.
   **Done** (branch `brain/bestiary-speed`): `Codex.Tactics`, read by `CodexKnowledge` beside the
   Codex with the mob table's alignment and the rotation's depths (boss floors left out) joined in;
   `Codex.Knowledge.tactics(name, depth, branch)` takes the class the rotation places on that depth
   (main branch only), then
   one it places nowhere (a summon, a quest or vault foe), then the first by class name. The hand
   lists went: `Fight.PASSIVE` is `ai: passive` for a walking class created as an enemy (the same
   three names), the IMMOVABLE use is `speed: immobile` (the same names), and `Heal.AT_RANGE` is
   `attack` ranged or bolt, or `flying`, a superset. Measured against main (`e867848f6`): the 25
   smoke triples play byte-identical Runs but for the think times; of the 40-Warrior tuning set 39
   are identical, and one (`HJO-WWK-PJG`, salt 1002) drinks a potion of healing at wait 575 that
   main did not, because a swarm of flies in view now counts as an enemy that flies, and dies on
   depth 3 at turn 727 instead of depth 4 at turn 1,289; depth-5 reach (11/40) and Goo kills (8/40)
   are unchanged.
1. **Speed-aware retreat.** Never retreat on foot from an enemy tagged `fast` or not `outrunnable`
   (the sewer crab on depths 3 and 4, later the bat and the piranha) unless the stairs are the next
   cell: a speed-2 enemy closes two cells per hero step and attacks once per turn, so every step
   away is a free attack. Fight it from the best cell in reach instead, and against a crab two cells
   away step back once so it spends its whole turn closing ([crab](../bestiary/sewers.md#crab), [speed
   rule](../bestiary/index.md#cross-cutting-rules)). Against a same-speed enemy, retreat only along a
   path to the stairs that does not pass it. Highest expected impact: the crab is the sewers' hardest
   hitter (1-7 at accuracy 12) and the fight Policy's retreat today walks away from it.
   **Measured, not kept** (the code is on branch `brain/bestiary-speed-lever1`). A: as written, with
   a stand-off (one Step back from an awake crab two cells off, a wait farther off) and, against a
   same-speed enemy not tagged outrunnable, only a stairs path whose j-th cell is more than j cells
   from it. B: the fast rule alone. Against lever 0 (40-Warrior tuning set; smoke):

   | | lever 0 | A | B |
   |---|---|---|---|
   | reached depth 5 | 11/40; 1/25 | 8/40; 3/25 | 12/40; 0/25 |
   | killed Goo | 8/40; 1/25 | 6/40; 3/25 | 6/40; 0/25 |
   | median turns | 1,222; 848 | 985; 627 | 1,306; 848 |
   | deepest mean (max) | 3.35 (5); 2.64 (5) | 3.05 (5); 2.80 (5) | 3.38 (5); 2.52 (4) |
   | deaths by depth 1-5 | 5 6 10 8 11; 3 10 6 5 1 | 7 9 7 9 8; 3 10 4 5 3 | 5 6 10 7 11 + 1 turn cap; 3 10 8 4 0 |
   | crab deaths (last enemy named) | 6; 3 | 1; 3 (1 a hermit crab) | 2; 2 |
   | oscillation Steps | 294; 183 | 168; 839 | 481; 161 |

   The crab rule works on the crab: deaths it closes drop from 6 to 1 or 2. What it costs is
   elsewhere. A's same-speed rule turns the step away from a rat, a snake or a gnoll into a fight
   (deaths on depths 1 and 2 rose from 11 to 16): walking away from an enemy at the hero's pace
   gains no ground, but it regenerates and it breaks contact at a door or a corner, which the tags'
   `breakContact` says and the stairs-only rule threw away. B keeps that and loses nothing clear, but
   gains nothing clear either: two Goo kills fewer, one more floor 5, worse on smoke, and one tuning
   Run (`NPM-FBY-JLE`) that walked away from Goo on its sealed floor for 18,227 waits to the turn cap,
   a loop the fight Policy's step-away already had (Goo at the hero's pace never catches it) that the
   divergence happened onto. Neither variant is an improvement without a survival regression.
2. **Surprise attacks.** Never walk up to a sleeping enemy to open in melee: a sleeper always wakes
   on its first turn with the hero adjacent, and the hit that follows is not a surprise. Throw from 2
   or more cells instead, which always hits a sleeper, then close. Against a same-speed hunter,
   break its sight through a door and hit it as it steps into view (the door ambush), which turns the
   sewer snake's 25 evasion (a 20% hit at level 1) into a certain hit ([snake](../bestiary/sewers.md#snake),
   [waking and the door ambush](../bestiary/index.md#cross-cutting-rules)). Tags: `ai`, `approach`
   (`surprise`), `evasive`. Most regular enemies are generated asleep, so this is a free first hit in
   most fights.
3. **Swarm, slime, crab and Goo special cases.** The swarm (`splits`): never throw at it in the
   open, since every physical hit makes a clone; fight it from a 1-wide corridor cell
   ([swarm](../bestiary/specials.md#swarm)). The slime: a hit of 5 or more is compressed (7 lands as
   6, 10 as 7), so a slow heavy weapon is wasted on it ([slime](../bestiary/sewers.md#slime)). The crab: `chokepoint`, and lever 1.
   Goo: open with a thrown stone on the sleeping Goo, fight on dry cells, never let it stand in
   water, where it heals every turn, even asleep, and step into water yourself to wash off Ooze; the
   pump itself the `Goo` class already answers ([Goo](../bestiary/bosses.md#goo)).
4. **A throwing Policy.** Pick up throwing weapons and throw them at an enemy closing from 2 or more
   cells (x1.5 accuracy), never at an adjacent one (x0.5), and pick them up after the fight. Tag:
   `rangedFirst`. It adds a hit or two per fight with the throwing stones the Warrior starts with;
   it is also what levers 2 and 3 throw with.
5. **Line of fire against bolt casters.** Close to adjacency around corners and through doors
   rather than trading shots with an enemy tagged `bolt` or `ranged` (the gnoll shaman, the DM-100,
   the dwarf warlock): adjacent, most of them only melee, and a wall, a closed door or a body in the
   line stops the bolt ([shaman](../bestiary/caves.md#shaman-redshaman), [DM-100](../bestiary/prison.md#dm100)).
   Lowest for the depth-5 goal, since the only such enemy before Goo is the Sad Ghost's gnoll
   trickster on depth 3; first for the prison, where the DM-100 arrives on depth 7.
