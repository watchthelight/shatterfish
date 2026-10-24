# From issue #183 (avoided zones over doors and heaps)

- **Zones are a guess, and the guess is cheap to get wrong in both directions.** The fix shortens a
  retreat's zone from 100 waits and a radius of up to 8 to 40 waits and at most 5 cells, and lifts it
  early when the screen shows where the enemy stood with nobody inside. The numbers are tuned on 65
  Runs. A zone could instead be sized from what the Brain knows of the enemy it fled (its speed, whether
  it wanders or sleeps), which the bestiary levers make possible: a rat that is slower than the hero
  needs no zone at all.
- **Pickup now waits for a fresh zone to age instead of walking into it.** With the crossing allowed at
  once, the smoke set showed new loops of "retreat: stairs", then "item", then "retreat: stairs": the
  heap is next to the enemy the zone was set for, and the hero walks back in. Waiting half the zone's
  life (20 waits) ends those. A better rule would weigh the heap against the enemy that is likely still
  beside it, which is the same question as whether the fight was worth retreating from.
- **The explore Policy still crosses a fresh zone at once.** A frontier through a zone beats a search,
  and a frontier is where the unexplored part of the floor is. It can walk into the enemy the zone
  guards. If the numbers on a longer set show that, explore could take the other frontiers first and
  cross a fresh zone last.
- **Hidden doors are searched for in the right order now, but the search spots are not ranked.**
  `Explore.search` still takes the nearest uncovered wall. A wall next to a dead end, or in a long blank
  stretch of the map, is far likelier to hide a door than one in a room's side. The Codex has the room
  generators; a ranking from them would cut the 10 to 12 search waits a floor that remain.
- **Heaps seen and never taken.** About one heap in ten is still left on the floor when the hero
  descends (the `untaken` column of the measurement). The floor never counts as spent while a reachable
  remembered heap is left; that is the next step in #182.
