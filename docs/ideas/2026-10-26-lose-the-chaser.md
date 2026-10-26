# Break line of sight so a hunting mob loses the hero

Issue #219 (the Goo rate). The idea: when a fight is unfavourable and the stairs are out of reach, walk to a
cell the chaser cannot see (round a corner, through a door) and wait, so the mob drops back to wandering. It
came from the crab deaths on depths 2 to 4, where the Policy logs `hold: no-way` for the two or three waits a
crab needs and then `cornered`. This page records why it was not built. Nothing was implemented or run through
the rig; the decision rests on the upstream code and on the 40 baseline Runs.

## How a hunting mob loses the hero

The earlier note pointed at `Mob.java:1381-1387`. That branch only fires when the mob's `enemy` is null
(dead, charmed away), not when the hero merely leaves its sight. `chooseEnemy` keeps the hero as the enemy
while the state is HUNTING (`Mob.java:393`), so the real path is different:

- Each act the mob recomputes `enemyInFOV` from its own field of view (`Mob.java:290`). While the hero is in
  view the mob's `target` is the hero's cell (`Mob.java:1379-1380`).
- Once the hero is out of view the mob keeps `target` at the last cell it saw the hero on and walks there
  (`Mob.java:1389-1392`). It does not give up on a timer.
- It drops to WANDERING only when `getCloser(target)` fails, which is when it stands on that cell
  (`Mob.java:593`) or cannot path to it, and the hero is still not in view (`Mob.java:1397`, `1433-1445`).

So the chaser always walks to the cell where it last saw the hero, and the hero has to be unseen from that
cell on the turn it arrives. A single corner does not do that: the last-seen cell is the corner cell or the
one before it, and a hero one or two steps down the new corridor is in a straight line from it. Hiding needs
a second bend, or a closed door between the last-seen cell and the hero. A door helps little: the hero stands
in the doorway when last seen, so the door cell is the target, and the mob opens it and looks in.

A crab covers two cells a turn (`Crab.java:35`, base speed 2), so a crab 5 to 8 cells off arrives at the
last-seen cell in 3 or 4 turns while the hero has made the same number of steps. Snakes, gnolls and rats move
at the hero's speed, which leaves the same geometry with a longer gap.

Once the mob is WANDERING it re-notices the hero when `Random.Float() < 1 / (distance / 2 + stealth)`
(`Mob.java:1309`, `1316-1318`). The hero's `stealth()` is 0 without an Obfuscation glyph (`Char.java:1284-1289`),
so the chance is 2 / distance per turn: certain inside two cells, 50% at four, a third at six. A hero that
escapes cleanly still has a wandering crab within a few cells of a hero on 2 to 7 health.

## What the baseline logs say

Reading the 40 debug logs of main (efbccd998, standard WARRIOR, salt 1000):

- `hold: no-way` appears in 35 of 40 Runs (154 episodes, counting a run of consecutive waits and the fight that
  follows as one), `cornered` in 37. Most are not deaths: skeletons, thieves and flies on depth 6 and beyond at
  good health, which the Policy simply fights.
- Restricted to depths 1 to 4, hero under half health, nearest chaser at least 3 cells away when the first
  `no-way` is logged: 41 episodes (17 flies, 11 crabs, 7 gnoll scouts, 3 rats, 2 snakes, 1 slime). Only three
  end in the Run's death: two crabs and one gnoll scout. That is the whole population this lever could touch,
  out of 12 Runs that died before depth 5.
- Flies fly erratically and a hero cannot use the last-seen-cell rule against them, so the 17 fly episodes are
  not candidates.

The logs hold no terrain, so whether a second bend or a door lay within reach in those three deaths cannot be
read from them. In the crab death in Run SP-PPL-WOK the crab came down one column toward the hero over four
turns (cells 519, 567, 665, 713 against the hero on 761); nothing in the cell numbers suggests a bend.

## Verdict

The opportunity is rare (three Runs in 40 at most, before counting geometry) and the mechanism is weak: a
fast chaser walks to the last-seen cell, a single corner leaves the hero in view, and a wandering mob notices a
hero at close range with high probability. Cell-visibility search in the Policy for that is not justified at
this sample size. Not built, no rig table. Kiting was already tried and reverted for snakes (`Kite.java`, see
`docs/bestiary/sewers.md`), and the stairs variant for crabs changed nothing in 40 Runs
(`2026-10-26-stairs-ahead-of-a-crab.md`).

## Side finding for #219

Two of the 12 early deaths are piranha deaths on depths 2 and 4 (Runs VX-XDX-IKI and IU-BNU-NUS). The Policy
alternates `away` and `frontier` between two cells (260 and 307, 785 and 810) next to water while three
piranhas close in, losing 4 to 14 health a turn. That is the same two-cell oscillation shape as #174, and it is
cheaper to fix than anything on this page. Worth its own issue.
