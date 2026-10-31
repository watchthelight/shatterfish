# Dying with unknown potions untried is not a Goo problem: 45 of 76 Sewers deaths

The Goo agent found that 42 of 50 lost Goo fights never drank, with about five mostly unknown potions held. The 10.40 reading
(`docs/results/2026-10-31-10-40-sewers-killers.md`, slice 1, 240 Runs, `--debug-record`) looks at the 76 Crab, Gnoll, Snake
and Swarm deaths on depths 1 to 4 and finds the same thing.

## Counts

- 53 of 76 held a potion of some kind on the last Decision; 45 held at least one unknown potion (130 in all, 2.9 each).
- None held a known potion of healing. None drank anything in the last 20 Decisions.
- By family: Crab 23 of 30, Swarm 13 of 21, Gnoll 9 of 20, Snake 0 of 5 (the Snake deaths are depth 1, before most potions).
- The fatal fight began at 50% of maximum health on average (1116 of 2375 hit points summed). 33 of 76 were hungry or
  starving on the last Decision (21 starving).

## What the lever is

When the fight Policy ranks `cornered` or the hero is under the low-health warning with no known heal, drink the unknown
potion most likely to be healing, whatever the enemy. The existing rule
[`unknown-potion-heal-half`](../brain-rules.md#unknown-potion-heal-half) lives in `TestItem` and fires at half health or below
when healing is likely enough; in these 45 deaths it did not. Why is not read here. The candidates to read first are the odds
term (`Beliefs`), the quarter-of-health reserve after the worst case (`TestItem.reserved`), and that `TestItem` stands below
`Fight` in the Policy order, so a fight that returns a move never reaches it.

## What to measure

A potion is a gamble: harmful identities (liquid flame, toxic gas, paralytic gas) at low
health can kill the hero outright, which is why the safe-test price exists. Measure the lever on the Runs that die holding an
unknown potion: both sides play identically until the first Decision where the hero is cornered, so a paired comparison
on slice 1 changes only those Runs. Read the true identities from the debug record's potion table (the colour names did
not match the table in every file in this reading, so count identities in the lever's own run).

## Also seen in the same files

107 of 240 Runs die holding a known potion of strength. `known_gains` is 0 in the committed Playbook because story 7.3
measured it as no gain on an older Brain (`_bmad-output/implementation-artifacts/7-3-item-identification-strategy.md`).
A free strength point left in the pack is worth one more measure once the fight lever is in.
