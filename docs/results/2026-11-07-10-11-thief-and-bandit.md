# Story 10.11: how often a Thief takes something, and what it costs (no Brain change)

Issue #357. The story asks for a rule that decides between killing a Thief, chasing it and letting it go. Before a rule, this
page measures how many Runs lose an item to a thief and whether the loss ties to a death. Thieves are common and what they take
is often kept out of the hero's hands for good, but the deaths do not show it, and nothing in the rig's table can show a rule's
effect on it. So no rule is built and `brain/` is unchanged; the column a later story must add is named at the end.

## What the game does

- A crazy thief (`actors.mobs.thief.name`, `core/src/main/assets/messages/actors/actors.properties:1839`) has 20 health, defence 12
  and 5 experience (`actors/mobs/Thief.java:46-50`). The Bandit is its subclass (`actors/mobs/Bandit.java`).
- On a hit it steals `hero.belongings.randomUnequipped()`, refused only for a unique item or one with level 1 or more
  (`Thief.java:146-150`), then flees (`Thief.java:130-131`). Equipped gear is never taken, and a hero's unknown potions, scrolls
  and spare weapons are the usual stock.
- When it dies the carried item drops where it stands (`Thief.java:98-106`, `rollToDropLoot`).
- A fleeing thief that is out of the hero's view and 6 or more cells away teleports to a random cell out of view and the item is
  gone: the log says "gets away with your ..." (`Thief.java:197-223`, `actors.properties:1842`). Spawn: a rotation entry on the
  prison depths and 2.5 percent on others (`actors/mobs/MobSpawner.java:103-121`, `:223`).

## The Runs read

`--debug-record` files carry the game log, which the Run log and the table columns do not. The files kept from the 10.40 page's
two slices were read: slice 1 kept 54 of 240 Runs with records (the rest were lost with an old output folder) and slice 2 (Warrior,
80 Runs) is whole, 134 Runs in all, Brain `eb38f1c4d`. A theft is a "stole ... from you" log line counted once, and the same
for "gets away with" and "You defeated crazy thief". All but 7 of the 194 thefts fall on depth 6 or deeper, so the exposed Runs are the 81
that reach depth 5 or more.

| | Runs |
|---|---|
| Runs read | 134 |
| reach depth 5 or more | 81 |
| reach depth 6 or more | 70 |
| reach depth 6 or more and lose at least one item | 59 |
| thefts, all Runs | 194 |
| of them the thief got away with the item | 118 (61%) |
| thieves defeated | 93 |
| thefts per Run that loses something | 3.2 |
| stolen: gear and other / unknown or known potions / scrolls | 89 / 81 / 24 |
| a potion of healing stolen | 9 thefts in 9 Runs, all 9 got away |
| Runs lost on the depth of their last theft | 35 of 60 |
| deaths from the thief itself (`Thief` killer, the 240 Runs of the E10 instruments page) | 4 of 240 pooled |

## What it ties to, and what it does not

- Thieves cost items often: three in four of the Runs that reach the prison depths lose one, and three in five stolen items are
  never seen again. Of the 194 thefts, 89 were gear or other, 81 potions, 24 scrolls.
- The thief itself rarely kills: 4 of 240 Runs pooled.
- The Runs that lose an item and the Runs that do not cannot be compared fairly. The Runs without a theft are mostly the ones that
  died at the Goo on depth 5 before any thief; the Runs with a theft reach depth 6.57 on average against 5.57 for the
  others, which is the depth, not the thief.
- Nine Runs had a potion of healing taken, and all nine lost it for good. Five of the nine died within 55 records of it and four
  lived longer. A Run that dies soon after losing a healing potion is the one a thief could have cost; the log cannot say
  whether the potion would have been drunk, and the 10.40 page found no Run drinking an unknown one.

## Why no rule is built

A rule on killing, chasing or ignoring a thief changes how many items are lost, and the table has no column for items lost. The
columns it has (reach depth 5 and 10, Goo kills, deaths by depth) are set at the Goo or by the prison's Skeletons, and no Run
reaches depth 10 in these tables. A rule written now could not show a win or a loss, and "write a rule only where it wins" cannot
be met. The chase half has a cost the data does show: a fleeing thief walks at the hero's speed, a chase costs the wait
the hero spends not exploring, and 118 of the 194 chases already end with the item gone.

## What a later story must add

An `items stolen / recovered` pair of columns to the direction table, read from the game log the way `deaths to` is read from the
ending record (a "stole ... from you" line, a "gets away with" line, and a pack check after a thief's death), so that a thief rule
can show whether it keeps items. With them a rule that attacks a thief ahead of other enemies, or keeps a healing potion out of reach
by drinking it first, can be measured against the 118 lost items per 134 Runs read here. The number of Runs that die within 55
records of losing a potion of healing (5 of 9 here) is the effect to look for.

## What this page cannot show

- 134 Runs, 54 of them from slice 1, which lost its other records; a different set of Runs would change every rate.
- A fair no-theft control: no Run reaches the prison without meeting a thief often enough to make one.
- Whether a stolen item would have been used. Unknown potions are rarely drunk (10.40), so most thefts cost nothing the bot
  would have spent.
