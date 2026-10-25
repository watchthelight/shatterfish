# From issue #174 (two-cell oscillation)

- **The oscillation count as a rig tool.** Issue #174 counted Steps in runs of six or more alternating
  between two cells with a scratch script; `direction.py metrics`, the gallery or the strategy log could
  report it for every Run, so a Policy change that starts a new loop shows up in the numbers it already
  prints.
- **A region the plan already crossed, set again at the same spot, could be held.** Issue #174 lifts a
  region the explore or descend Policy walks into when there is no way around it. When the enemy there
  is still unfavourable, the fight Policy retreats again, sets a new region, and the plan crosses it
  again: only the bounce block (`Memory.BOUNCE_WAITS`) ends that loop, after six bounces. A region set
  inside one lifted a few waits earlier could be kept out of for good instead, which would make the
  floor spent past it and let the descend Policy leave.
- **Enemies bound to water.** Most of the long loops on `smoke` were giant piranhas: asleep or awake,
  they cannot leave the water, yet the fight Policy retreats from them as from any enemy. The Codex
  does not say which mobs are bound to water; a row for it would let the fight Policy ignore one the
  hero is not next to the water of.
- **Without the loops the hero meets fights it used to walk away from by accident.** Issue #174's
  chase took two `smoke` heroes to sleeping sewer snakes they then lost to, after retreating from the
  snake with it beside them, which gives it a free hit each Step. The threat estimate for evasive
  enemies, and whether to retreat from an adjacent enemy at all, deserve their own look.
