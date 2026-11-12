# What loses on depths 6 to 8

Diagnosis only. Nothing here was built or measured against a baseline. It reads one finished slice and says where the Runs that beat Goo go on to die, so that the next stories can aim at depths 6 to 10. The goal is Tengu on depth 10, and no Run in this slice or any other has reached depth 10.

## Method

The slice is the `current` side of the talents branch measurement: Brain commit `85401aa55` (story 10.50), baseline `54fc50eaf`, `standard` set, salts 1000 to 1039, 40 Runs per class, 240 Runs, profile 6, cap 20000. Every Run ended in death.

No Run was replayed and nothing was built. Python read four kinds of file per Run: the Run log (every Decision with its Policy, reason and flags), the sparse debug records (hit points, hunger, enemies in view, pack, game log), the arrivals table (level, strength and pack on first arrival at each depth) and the heaps table (every item heap seen, and whether it was taken).

The denominator is 145 unless a line says otherwise: the Runs that killed Goo and died on depth 6 or deeper. Brain line numbers are those of `main` at `0b6c9c8d4`; the files cited (`Fight`, `Descend`, `Eat`, `Equip`, `TestItem`, `Answers`, `Pickup`, `Explore`) do not differ between that commit and the slice's. Game mechanics are cited from the pinned code under `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/`, shortened here to `core/.../`.

A "final fight" is the last stretch of Decisions with an enemy in view, allowing gaps of up to 10 Decisions; 129 of the 145 deaths have one. Hit point loss is attributed to the Decision that preceded it in the debug records, which are sparse, so those shares are approximate.

Differences under about 6 Runs of 240 are noise at this size. The estimates at the end are reasoning from pools, not measurements.

## Verified counts

| | Runs |
|---|---:|
| Runs in the slice, all deaths | 240 |
| Died on depths 1 to 5 | 6, 16, 31, 24, 18 (95) |
| Killed Goo | 146 |
| Killed Goo and died on depth 5 | 1 |
| Died on depth 6 | 55 |
| Died on depth 7 | 58 |
| Died on depth 8 | 28 |
| Died on depth 9 | 4 |
| Reached depth 10 | 0 |

The depth counts come from the Run log's `end` record. The `ending.json` files disagree on depth in 18 Runs (they give 57, 62, 23 and 3); the Run log is the record of play, so it is used here.

Two figures in the brief did not reproduce. The last screen shows the hero starving in 90 deaths, not 48 (hungry in 28 more, fed in 27). A skeleton is in view on the last screen in 54 deaths, not 41, and a necromancer's skeleton in 15 more. The killer is a skeleton in 40 and hunger damage in 16; the brief's numbers may have been those two with a different cut.

## 1. Cause of death

### Killer by depth

| Killer | 6 | 7 | 8 | 9 | All |
|---|---:|---:|---:|---:|---:|
| Skeleton | 27 | 13 | 0 | 0 | 40 |
| DM-100 | 0 | 29 | 8 | 0 | 37 |
| Hunger damage | 12 | 3 | 1 | 0 | 16 |
| Prison guard | 0 | 7 | 6 | 1 | 14 |
| Necromancer's skeleton | 0 | 0 | 9 | 3 | 12 |
| Poison | 4 | 0 | 0 | 0 | 4 |
| Bomb | 3 | 1 | 0 | 0 | 4 |
| Piranha | 2 | 1 | 0 | 0 | 3 |
| Caustic ooze | 3 | 0 | 0 | 0 | 3 |
| Thief, bleeding, burning, bee | 3 | 3 | 2 | 0 | 8 |
| Toxic gas, electricity, wraith, sentry | 1 | 1 | 2 | 0 | 4 |
| All | 55 | 58 | 28 | 4 | 145 |

The killers follow the spawn table closely. Depth 6 is three skeletons in five draws, depth 7 adds the DM-100 and the guard, depth 8 doubles both and adds the necromancer (`core/.../actors/mobs/MobSpawner.java:100-122`). The DM-100 kills 29 Runs on the first floor it appears on.

### Killer by class

| Killer | Warrior | Mage | Rogue | Huntress | Duelist | Cleric |
|---|---:|---:|---:|---:|---:|---:|
| Deaths past Goo | 29 | 27 | 12 | 25 | 23 | 29 |
| on depth 6 / 7 / 8 / 9 | 14/10/4/1 | 9/12/6/0 | 8/3/1/0 | 12/8/4/1 | 9/10/4/0 | 3/15/9/2 |
| Skeleton | 9 | 8 | 6 | 7 | 7 | 3 |
| DM-100 | 7 | 9 | 2 | 6 | 4 | 9 |
| Hunger damage | 5 | 1 | 2 | 4 | 1 | 3 |
| Prison guard | 1 | 2 | 2 | 3 | 2 | 4 |
| Necromancer's skeleton | 3 | 2 | 0 | 2 | 0 | 5 |

The Cleric is the only class that mostly clears depth 6 (3 of 29 die there). The Rogue reaches the prison least often and latest (median arrival turn 1982 against 1624 overall) and lasts a median of 286 turns once there.

### The state the last fight began in

The killer is the last blow. The table below gives each death one label, taken in this order: hunger damage was the last blow; a status or trap was the last blow; the final fight began with the hero starving; it began under half health; otherwise by how many enemies came within four cells.

| Label | 6 | 7 | 8 | 9 | All |
|---|---:|---:|---:|---:|---:|
| Final fight began starving | 18 | 27 | 12 | 2 | 59 |
| Final fight began under half health, not starving | 7 | 13 | 6 | 0 | 26 |
| Hunger damage was the last blow | 12 | 3 | 1 | 0 | 16 |
| Status or trap (poison 4, bomb 4, ooze 3, bleeding 2, burning 2, gas 1, electricity 1) | 11 | 3 | 3 | 0 | 17 |
| Healthy start, three or more enemies within 4 cells | 2 | 3 | 5 | 1 | 11 |
| Healthy start, one enemy at a time | 3 | 5 | 1 | 0 | 9 |
| Healthy start, two enemies | 1 | 2 | 0 | 1 | 4 |
| Killed by a mob with no fight record | 1 | 2 | 0 | 0 | 3 |

Only 24 of the 145 deaths begin their last fight fed and above half health. Across the 129 final fights, 83 began under half health, 58 under a third, and 15 at nine tenths or more. 73 began starving, 28 hungry, 28 fed.

A starving hero does not regenerate (`core/.../actors/buffs/Regeneration.java:56`) and loses HT/1000 a turn (`core/.../actors/buffs/Hunger.java:78-85`), so a hero that starves between fights only goes down. Of the 90 deaths that end starving, 77 had been starving for 50 turns or more without a break and 61 for 100 or more; the median unbroken stretch is 164 turns. Starving covers 28% of all turns played on depth 6 and below.

Examples, final fight begun starving and hurt:

- Duelist, salt 1029 (BAV-TNP-CCE), depth 7, turn 2754: meets one skeleton at 6 of 55 hit points, starving. Six Decisions later it is dead.
- Cleric, salt 1006 (OTE-CJR-RSK), depth 7, turn 3081: 17 of 60, starving, a guard and a skeleton.
- Duelist, salt 1028 (VBW-IGJ-OUJ), depth 8, turn 3089: 4 of 60, starving, one guard.
- Huntress, salt 1025 (ASB-SDG-JYZ), depth 9, turn 3230: 12 of 70, starving, a necromancer and its skeleton. This is the deepest and highest-level (11) death in the slice.

## 2. State on arrival at depth 6

All 145, on the first Decision of depth 6:

| | Value |
|---|---|
| Turn | median 1624 |
| Hero level | 6 in 95, 7 in 31, 5 in 14, 8 in 4, 4 in 1 |
| Hit points | median 100%; under half in 9; under three quarters in 21 |
| Hunger | fed 60, hungry 52, starving 33 |
| Food carried | none 56, one 31, two 29, three or more 29 (mean 1.39) |
| Healing potions, identified | none 58, one 47, two or more 40 |
| Strength | 10 in 5, 11 in 74, 12 in 66 |
| Worn armour | cloth 138, leather 7; level +1 in 57, +2 in 88 |
| Weapon | the class's starting weapon at +1 in 138 |
| A heavier armour unworn in the pack | 71 |
| Hungry or starving with no food | 47 |

So the hero walks into the prison at full health, level 6, in cloth armour and with its first weapon, and one time in three it is already out of food and hungry.

The hero's level is not the problem it might look like. Level 6 gives 45 hit points (`core/.../actors/hero/Hero.java:264`) and the prison mobs still give experience at that level (skeleton to level 10, `core/.../actors/mobs/Skeleton.java:52-56`). Levels at later arrivals are 7 or 8 on depth 7 (80 of 90) and 8 or 9 on depth 8 (25 of 32).

Health on arrival falls floor by floor: median 100% on depth 6, 82% on depth 7 (23 of 90 under half), 65% on depth 8 (10 of 32 under half), and all 4 arrivals on depth 9 are under half (median 24%).

### Those who die on 6 against those who reach 8

| On arrival at depth 6 | Died on 6 (55) | Died on 7 (58) | Reached 8 or 9 (32) |
|---|---:|---:|---:|
| Starving / hungry / fed | 20 / 18 / 17 | 10 / 24 / 24 | 3 / 10 / 19 |
| No food | 32 | 18 | 6 |
| Mean food carried | 0.78 | 1.55 | 2.12 |
| Under half health | 7 | 1 | 1 |
| Armour at +2 | 25 | 38 | 25 |
| Median arrival turn | 1670 | 1624 | 1591 |
| Median turns lived after arrival | 339 | 796 | 985 |

Read the other way round:

| Arrival state | Runs | Died on 6 | Died on 7 | Reached 8+ | Median turns lived |
|---|---:|---:|---:|---:|---:|
| Hungry or starving, no food | 47 | 29 | 15 | 3 | 368 |
| Fed, with food | 51 | 14 | 21 | 16 | 826 |
| Starving | 33 | 20 | 10 | 3 | 339 |
| Armour +2 | 88 | 25 | 38 | 25 | |
| Armour below +2 | 57 | 30 | 20 | 7 | |
| No identified healing potion | 58 | 27 | 21 | 10 | |

Food on arrival is the strongest single separator in the data. Armour level is second. Healing potions barely separate the groups.

Examples, arrived starving with no food and died on depth 6:

- Cleric, salt 1009 (EHQ-DHC-BJP): arrives turn 1769, dies of hunger turn 1830. It had been starving since depth 4.
- Huntress, salt 1004 (CPW-YFI-CHQ): arrives turn 1298, killed by a skeleton turn 1386.
- Duelist, salt 1038 (GEM-DPO-MHG): arrives turn 1786, killed by a skeleton turn 1870, with 863 gold picked up on the way.
- Huntress, salt 1017 (PKW-JXF-EHA): arrives turn 2081, dies of hunger turn 2114, with 935 gold.

By class: the Warrior arrives starving in 13 of 29 and without food in 17; the Rogue starving in 6 of 12; the Huntress fed in 19 of 25; the Cleric fed or hungry in 27 of 29.

## 3. Hunger

### Where the turns go

100,009 turns were played on depth 6 and below by the 145 Runs, a median of 652 per Run.

| Activity | Share of turns |
|---|---:|
| Exploring to a frontier | 28.4% |
| Resting | 15.9% |
| Walking to pick things up | 12.5% |
| Fight: retreating | 8.8% |
| Fight: attacking | 8.6% |
| Searching while exploring | 4.8% |
| Fight: approaching | 3.6% |
| Ranged, wands, thrown items | 2.6% |
| Fight: cornered | 2.3% |
| Walking to the exit, hungry | 2.3% |
| Searching for a missing exit | 1.9% |
| Walking to the exit, floor spent | 1.9% |
| Fight: holding | 1.7% |
| Fallback (uniform draw) | 1.4% |
| Eating | 1.2% |

The Runs that end starving and the Runs that do not spend their turns in nearly the same proportions (frontier 28.1% against 28.8%, rest 15.7% against 16.2%). The starving Runs are not wasting more time on explored-out floors; they had less food to begin with. That matches story 10.39, which found the rest and search levers neutral, and it is a reason not to try those again.

The food supply is the level's one food item per regular floor (`core/.../levels/Level.java:226`), a ration four times in five and a pasty otherwise (`core/.../items/Generator.java:537-541`), worth 300 or 450 turns (`core/.../items/food/Food.java:51`, `Pasty.java:50`). At a median of 441 turns on depth 6 and 217 on depth 7 that budget roughly balances only if nothing is in deficit on arrival, and a third of the Runs arrive in deficit.

Food picked up on depth 6 and below: 216 heaps over 271 floors visited. 115 Runs ate at least once down there. At death 127 of 145 carry no food, and 3 are starving with food in the pack.

### Food left on the floor

43 rows of the heaps tables are food seen on depth 5 or deeper and never taken, in 37 Runs (ration 31, pasty 6, mystery meat 3, berry 3; two rows may be repeats). 29 of them were seen 30 or more Decisions before the hero left the floor or died. The heaps table does not say why a heap was not taken: some of these are behind a locked door, across a chasm or guarded.

- Warrior, salt 1000: a ration seen on depth 6 at Decision 1615; the hero is starving for 357 Decisions on that floor afterwards, leaves, and dies to a DM-100 on depth 7, starving.
- Mage, salt 1039: a ration seen on depth 6, 115 starving Decisions afterwards, killed by a skeleton on the same floor.
- Rogue, salt 1034: a ration seen on depth 6, 298 Decisions afterwards of which 162 starving, killed by a skeleton on the same floor.
- Warrior, salt 1019 (PMA-UHB-YQN): a ration seen on depth 6, 127 starving Decisions afterwards, dies of hunger on that floor at turn 1905.

Fifteen of the 37 Runs died starving on the same floor as food they had seen. This is worth a look in the strategy log once the Rig may run again; the cause cannot be read from these files.

### The shop

Depth 6 always has a shop (`core/.../Dungeon.java:433-435`, `core/.../levels/RegularLevel.java:143-144`). It stocks two small rations (`core/.../levels/rooms/special/ShopRoom.java:291-292`), one potion of healing (`ShopRoom.java:277`) and an identified leather armour (`ShopRoom.java:228-232`). Prices are value times 5 times (depth/5 + 1), so times 10 on depth 6 (`core/.../actors/mobs/npcs/Shopkeeper.java:201-202`): 100 gold for a small ration (`core/.../items/food/SmallRation.java:31`, 150 turns of food; `:35-36`), 300 for the potion (`core/.../items/potions/PotionOfHealing.java:89-90`), 400 for the armour (`core/.../items/armor/Armor.java:706-718`).

The Brain never buys. The prompt Policy's rule for a shop window is to leave (`shatterfish/brain/.../Answers.java:32-33`, `:108`), the pick-up Policy and the explorer skip heaps that are for sale (`Pickup.java:179`, `Explore.java:791`), and no Policy emits `Action.Buy`. In this slice a shop window opened in 2 of the 145 Runs, both through the fallback's uniform draw, and the 21 Buy Actions in those two Runs are that same draw.

The hero has the gold. Gold is not in the pack listing, but the heaps table names the amount of most gold heaps, and nothing is ever spent. Summing the heaps taken before first arrival at depth 6 gives a lower bound on the purse: median 702, at least 200 in 143 Runs, at least 400 in 131, at least 500 in 114. Of the 47 Runs that arrive hungry or starving with no food, 46 hold at least 200 gold, the price of both rations. Of the 16 Runs killed by hunger, the smallest purse is 280.

- Huntress, salt 1017 (PKW-JXF-EHA): starves to death on depth 6 at turn 2114 with at least 935 gold.
- Warrior, salt 1034 (IIU-BNU-NUS): starves on depth 6 at turn 2707 after 571 starving turns, at least 1085 gold.
- Cleric, salt 1009 (EHQ-DHC-BJP): starves on depth 6 at turn 1830, at least 280 gold.

What the files cannot show is whether the shop room was found in each Run. For-sale heaps are not in the heaps table (no small ration appears in any of the 145), so the share of Runs that walked past the shopkeeper is unknown.

### The 16 hunger kills

Twelve are on depth 6. Their starving stretches run from 18 to 1170 turns, median 243. Five of them are not really food stories but stuck states:

- Warrior, salt 1037 (RVS-QHY-GIN), depth 6, turn 2846: 720 turns in the fallback Policy, 172 shop windows dismissed, 667 turns starving.
- Warrior, salt 1003 (ELH-RAA-OYI), depth 6, turn 3306: 595 turns on "hold: no-way" with a skeleton in view, 233 searching for an exit, 1170 turns starving.
- Huntress, salt 1028 (SAM-IFK-TWL), depth 6, turn 2809: 133 turns searching for an exit, 94 Decisions of fallback, 30 shop windows dismissed.
- Huntress, salt 1038 (YLI-CIA-CQU), depth 7, turn 2642: the last 30 Decisions are all fallback; 114 turns searching for an exit.
- Mage, salt 1023 (KLF-BYB-RCZ), depth 7, turn 2109: 21 of the last 30 Decisions search for an exit, 134 turns in all.

Nine Runs in the slice spent 100 turns or more searching for an exit they had not found.

### Mystery meat

Three of the 4 poison deaths and one of the 2 burning deaths follow a meal of mystery meat in the last few lines of the game log ("That food tasted... strange"): Cleric 1014 (AOU-UKX-ZQY, depth 6, turn 2124), Warrior 1005 (NSP-PPL-WOK, depth 6, turn 2204), Warrior 1036 (TDR-WKF-GUZ, depth 6, turn 2385) and Cleric 1010 (SQZ-FNO-KUX, depth 7, turn 2219, burning). The meat poisons one meal in five for HT/5 and burns one in five (`core/.../items/food/MysteryMeat.java:55-66`). The eat Policy walks to water first to answer the burn (`Eat.java:172-203`) but has no answer to the poison, and it eats the meat when starving whatever the hero's health (`Eat.java:233`). All four heroes were at 10 hit points or fewer when they died.

## 4. The fights

### What the last 30 Decisions are

Over all 145 deaths the last 30 Decisions are 50.4% fight, 25.4% explore, 9.2% pick-up, 6.8% descend, 2.5% fallback, 2.0% zap, 0.7% shoot, 0.6% heal, 0.5% eat, 0.5% pack.

The last Decision of the Run is "cornered" in 67 deaths, a retreat in 25, exploring in 16 and a hold in 12. It is a plain favourable attack in 1.

"Cornered" is the fight Policy's name for attacking because the fight is not favourable and there is no retreat left (`Fight.java:294-299`). A fight is favourable when the damage expected while killing the target is under half the hero's hit points, and never when health is low (`Fight.java:66-68`, `:80-81`). When it is not favourable the hero retreats, by the stairs first, at most twice per floor (`Fight.java:88`, `:621-648`).

On depth 6 and below the fight Policy made 8915 retreat Decisions (5234 of them "retreat: stairs"), 9135 attacks, 3015 cornered attacks, 1733 holds, 276 trips up the stairs in 108 Runs, and 19 chokepoint moves.

| Share of hit points lost in final fights, by the Decision before the loss | |
|---|---:|
| Cornered | 34.9% |
| Attack | 24.4% |
| Retreat | 20.1% |
| Ranged | 6.1% |
| Approach | 5.7% |
| Hold | 4.3% |

In the final fights the median number of retreat Decisions is 7; 55 fights have 10 or more and 26 have 20 or more. 64 of the 129 have five or more retreats and then end cornered. The pattern is: the fight is judged unfavourable, the hero walks away taking hits, the enemy follows, the retreat runs out, and the hero fights the same fight with fewer hit points.

### By mob

| | Skeleton (40) | DM-100 (37) | Guard (14) | Necromancer's skeleton (12) |
|---|---|---|---|---|
| Last 30, largest shares | retreat 29%, cornered 27%, frontier 18% | frontier 31%, retreat 21% | retreat 32%, cornered 22% | cornered 30%, attack 17% |
| Hit point loss, largest shares | cornered 73% | retreat 32%, attack 23%, approach 12% | cornered 53% | attack 46%, cornered 33% |
| Final fight began under half health | 28 | 21 of 35 | 7 | 6 |
| Final fight began starving | 26 | 14 | 11 | 5 |
| Began at half health or more, and retreated 5+ times | 12 | 8 | 6 | 5 |
| Starving at death | 29 | 15 | 11 | 7 |

**Skeleton.** 25 hit points, hits for 2 to 10, and its death explosion deals 6 to 12 to every neighbour less two armour rolls (`core/.../actors/mobs/Skeleton.java:52-67`, `:78-81`, `:125`). 20 of the 40 deaths have only skeletons in the fight and 18 never have more than one enemy within four cells. 28 of 40 have five or more retreats in the last 30 Decisions. No chokepoint move appears in any of them, which is as written: the chokepoint rule asks for two or more mobile enemies closing (`Fight.java:322`). Hero level at death is 7 in 17 and 8 in 9. Traces of the last 30 Decisions (R retreat, C cornered, f frontier, p pick-up, number is hit points):

- Duelist, salt 1030 (FYL-APY-UME), depth 7, turn 2400, level 8: `f8 f8 R7 f7 f7 R7 R7 R7 R7 R7 R6 R6 R6 R6 R6 R6 R6 R6 R6 R6 C6 C6 C4 C4 C4 C4 C4 C3 C2`. One skeleton, hero at 7 of 55 and starving when it came into view.
- Cleric, salt 1025 (IMF-JWA-ZVD), depth 7, turn 1786, level 6: 18 retreats at 16 or 17 hit points with a skeleton, a guard, a thief and a DM-100 about, one heal, then eleven cornered Decisions.
- Duelist, salt 1038 (GEM-DPO-MHG), depth 6, turn 1870: starving at 8 of 45, fourteen retreats with four stairs trips, then three cornered Decisions.
- Cleric, salt 1006 (OTE-CJR-RSK), depth 7, turn 3081: exploring and picking up at 19 of 60, two retreats, eight cornered Decisions. A potion of haste and a potion of invisibility were in the pack at death.

**DM-100.** 20 hit points, melee 2 to 8, and a lightning bolt of 3 to 10 at range (`core/.../actors/mobs/DM100.java:47-51`, `:62`, `:76-78`, `:98-100`). The bolt is dealt through `damage` directly, without the armour roll a melee hit subtracts (`core/.../actors/Char.java:390`). It is the weakest prison mob in hand to hand and the one that punishes distance. The Brain keeps its distance: in 12 of the 37 deaths the last 30 Decisions hold no attack at all, only 12 of 35 final fights contain any attack, and 18 of 35 approach and then retreat. Retreating is where most of the hit points go (32%). The final fights are short (median 17 turns; 16 last 12 turns or fewer).

- Cleric, salt 1032 (DQP-INR-NBI), depth 7, turn 1938: 52 of 55 hit points, fed, one DM-100. Six approach Decisions (52, 44, 38, 38, 28, 21), then four retreats (16, 17, 9, 9), dead in 12 turns without one attack. This hero wore leather.
- Cleric, salt 1007 (ECT-VFN-OFE), depth 7, turn 1994: 28 of 50, fed. Two approaches, two retreats, dead in 4 turns.
- Cleric, salt 1033 (AKW-BPE-DFI), depth 7, turn 2412: walking to the exit hungry at 23 of 55, one shot, five retreats (17, 17, 17, 8, 4). A potion of frost was in the pack.
- Cleric, salt 1004 (FMP-BBR-HNU), depth 7, turn 2590: 40 of 55, starving. Three approaches, two attacks (31), two retreats (19, 11), a hold at 6.

**Prison guard.** 40 hit points, hits for 4 to 12, and pulls the hero in once from under five cells, crippling it (`core/.../actors/mobs/Guard.java:51-67`, `:70-127`, `:179-184`). 11 of the 14 deaths are starving. Four end crippled.

- Cleric, salt 1001 (UQQ-NYM-YLJ), depth 8, turn 2418: 25 retreats at 17 or 18 of 55 with three stairs trips, then five cornered Decisions. A potion of liquid flame was in the pack.
- Cleric, salt 1018 (OPE-OEW-IJE), depth 7, turn 2257: 45 of 55, starving; 29 of the last 30 Decisions are retreats, falling from 17 to 10, and one cornered. Potions of invisibility and frost in the pack.
- Duelist, salt 1023 (ECF-HSU-SIP), depth 7, turn 2833: fed, 37 of 60; five attacks, 17 retreats at 13 to 16, seven cornered Decisions.

**Necromancer's skeleton.** The necromancer (40 hit points, `core/.../actors/mobs/Necromancer.java:55-59`) raises and heals a skeleton from range (`:172-193`, `:199-259`). Here the hero does fight: attack is 46% of the loss. The Brain attacks the skeleton 552 times on depth 6 and below and the necromancer itself 19 times.

- Cleric, salt 1019 (TUD-BAW-ZWC), depth 8, turn 2979: fed, 43 of 60, five kinds of enemy. 25 attack Decisions falling from 57 to 24, one heal, three cornered.
- Cleric, salt 1024 (EEC-RMW-HVL), depth 8, turn 2304: 53 of 55 at the start, starving. Attacks from 41 down to 22, three retreats, ten cornered.
- Cleric, salt 1005 (OPP-ZBH-ZHP), depth 9, turn 2590: full health at the start, hungry; ten attacks from 37 to 24, fifteen retreats at 10, five cornered.

**Thief.** Two kills only, but the game log shows it stealing a potion of healing 25 times and mystery meat 10 times in these Runs.

### What was in the pack and not used

Of the 105 deaths to a skeleton, DM-100, guard, necromancer's skeleton or thief:

| In the pack at death | Runs |
|---|---:|
| An identified potion of healing | 2 |
| An unidentified potion | 88 |
| An identified potion of liquid flame, frost, toxic gas, paralytic gas, invisibility or haste | 31 |
| A wand | 39 |
| Thrown weapons | 46 |
| A scroll of rage, lullaby or mirror image | 6 |

Healing is not the gap: the heal Policy fired 231 times in 106 Runs on these depths and the potions are gone by the end. The gap is the 31 Runs that die holding a potion that ends or escapes a fight (invisibility 10, liquid flame 8, frost 7, haste 7, toxic gas 5, paralytic gas 4). The pack Policy does throw: 30 liquid flame and 16 frost on these depths. It does not drink haste or invisibility to get away.

Whether a doorway was within reach cannot be read from these files. The Run log holds a hash of the map, not the map.

## 5. Rules shaped by depths 1 to 5

None of these has a literal depth test. Each is a rule whose premise holds in the sewers and fails in the prison.

1. **The shop is left.** `Answers.java:32-33` says the Brain has no model of prices, so it leaves. The first shop is on depth 6, so the rule never cost anything before. `HeapView.price` is already in the Observation (`shatterfish/api/.../HeapView.java:17`), as is the hero's gold (`HeroSection.java:32`).
2. **Retreat when the fight is not favourable.** `Fight.java:80-81`, `:621-648`. In the sewers a retreat breaks contact, the hero regenerates and comes back. In the prison the hero is in cloth armour against mobs that hit for up to 10 or 12, so most fights are unfavourable; it is starving 28% of the time, so it does not regenerate; and one of the mobs shoots. The retreat spends hit points (20% of the loss) and ends cornered (35%).
3. **The chokepoint needs two enemies.** `Fight.java:322`. 19 Decisions on these depths.
4. **No rest before the stairs while starving.** `Descend.java:408`. Correct in itself, since rest would not heal. Its effect is that a starving hero goes down at whatever health it has: 23 of 90 arrive on depth 7 under half health.
5. **Every scroll of upgrade goes onto the worn armour** (`Answers.java:34-36`), and a heavier piece with a hidden level is never chosen (`TestItem.java:585`; `Equip.java:124-133` and `:153` weigh it only before any scroll is read). Cloth is tier 1 and its ceiling is tier times (2 + level) (`core/.../items/armor/Armor.java:379-384`): 4 at +2. A leather armour at +0 has the same ceiling of 4, so swapping on depth 6 gains nothing. The scrolls are what matter, and by depth 6 they are in the cloth: 138 of 145 arrive in cloth, 97 die in cloth +2, and 79 die with a heavier piece unworn (58 leather, 37 mail, 12 scale, 2 plate between them). Leather at +2 would have a ceiling of 8 and asks for 11 strength (`Armor.java:698-702`), which 140 of 145 have on arrival. 19 Runs die holding an identified scroll of remove curse, 13 of them next to a heavier armour they did not wear; found armour is cursed three times in ten (`Armor.java:655-679`).
6. **Mystery meat is eaten when starving at any health.** `Eat.java:233`. On depths 1 to 5 the hero is rarely starving at 10 hit points.

## Ranked levers

The estimate is the number of the 145 Runs that would be expected to get past the floor they died on. It is not a count of Runs that reach depth 10; nothing in this slice supports a figure for that. The levers overlap heavily (most of the hungry Runs are also the cloth-armour Runs), so the estimates do not add.

Needs: B means the Brain alone, with fields the Observation already carries. F means a new Observer field or Action.

| Rank | Lever | Pool | Reasoning | Estimate | Touches | Needs |
|---:|---|---|---|---:|---|---|
| 1 | Buy at the depth 6 shop: both small rations when hungry or short of food, then the leather armour or the potion with what is left | 47 arrive hungry or starving with no food (29 die on depth 6); 71 die starving on depth 6 or 7; 131 hold 400 gold or more on arrival | Fed arrivals with food die on depth 6 at 27% (14 of 51); hungry arrivals without food at 62% (29 of 47). Two small rations are 300 turns, about the median stay on depth 6. If that closes half the gap, 47 x 0.35 x 0.5 is 8. The identified leather armour adds a piece whose level is known, which rule 5 can then use. | 8 to 12 | `Answers.java` (shop rule), a new buy Policy, `Pickup.java:179`, `Explore.java:791` | B. Price, gold and `Action.Buy` exist. Check first that for-sale heaps and their prices reach the Brain as the screen shows them. |
| 2 | Close with the DM-100 or break its line of sight; never walk away from it in the open | 37 deaths, 29 on depth 7; 14 final fights begin at half health or more; attack appears in 12 of 35 | It has 20 hit points and the lowest evasion of the prison mobs (8), and its bolt ignores armour, so every turn at range is a loss. Half of the 14 healthy starts. | 5 to 7 | `Fight.java` (retreat and favourable for a ranged enemy), `Bestiary` | B. The mob's name is on screen; its ranged attack is a Codex fact. |
| 3 | Put the scrolls of upgrade into armour worth keeping: wear the first leather or mail the hero can carry before the next scroll is read, using remove curse where held | 138 arrive in cloth; 79 die with a heavier piece unworn; armour +2 against below +2 on arrival: 28% against 53% die on depth 6 | Each tier doubles what a scroll buys. Story 10.47 worked this ground and kept one of three levers; the open part is the hidden-level skip in `TestItem.java:585`. Cautious because that story's gains were small. | 4 to 8 | `TestItem.java`, `Equip.java`, `Answers.java:34-36` | B. Strength, the piece's name and whether its level shows are on screen. |
| 4 | Do not retreat when the retreat cannot break contact: starving (no regeneration), flights used up, or the enemy as fast as the hero. Fight at once at full strength, or use the pack | 64 of 129 final fights retreat 5+ times and end cornered; 31 of them begin at half health or more | Retreat costs 20% of the hit points lost. A third of the 31. A related change, no stairs flight while starving, was built, measured and reverted (`2026-10-27-no-stairs-flight-while-starving.md`), so this needs a different cut than that one and may not pay. | 4 to 7 | `Fight.java:621-648`, `:294-299` | B |
| 5 | Fix the stuck states: the fallback loop at the shopkeeper, "hold: no-way" without end, the exit search that does not finish | 5 of the 16 hunger kills; 9 Runs with 100+ turns of exit search; 5 Runs with 20+ fallback Decisions | These are defects more than strategy. Each named Run above is one. | 4 to 5 | `Policies.java` (fallback), `Fight.java:356`, `Descend.java` (exit search) | B |
| 6 | Spend the pack before the last cornered blow: drink haste or invisibility to leave, throw what is held; and do not eat mystery meat under a fifth of health unless nothing else will stop the hunger damage | 31 of 105 mob deaths hold such a potion; 4 deaths follow a mystery meat meal | One in eight of the 31, plus two of the four. | 3 to 5 | `Pack.java`, `Escape.java`, `Eat.java:233` | B |

What I would do first is lever 1. It is the only one that adds food to a Run, the gold is already there and unspent in nine Runs out of ten, and it does not depend on winning fights the hero is not equipped to win. Lever 5 is cheap and should go alongside it because two of its five Runs are loops at the shopkeeper that a buying Policy will meet at once.

None of the six needs information the Observer does not expose. If lever 1 finds that for-sale heaps or prices are missing from what the Brain receives, that becomes an Observer change with leak tests, and the price is on screen for a human, so it is within information parity.

## What the data cannot show

- One slice, one branch, 240 Runs, and all of the analysis is on the 145 that passed Goo. A pool of 12 Runs is two or three seeds' worth of luck.
- Every estimate is a pool times a guessed conversion. The earlier prison pages found several plausible levers neutral when measured (10.39) and one harmful (the stairs flight). Treat the ranking as an order to try things in.
- "Got past the floor it died on" is not "reached depth 10". All four Runs that reached depth 9 arrived under half health. Getting to Tengu needs several of these at once, and the slice has no Run that shows what a healthy depth 9 looks like.
- The cause of an untaken food heap, whether the shop room was found, and whether a doorway was near are not in the logs.
- Gold is a lower bound from heap names, not the purse.
- Hit point loss shares come from sparse debug records.
- The labels in section 1 are ordered, so a death counted as "began starving" may also have been outnumbered.
- The class differences rest on 12 to 29 Runs each.

## Appendix: every death past Goo

Hunger, food and healing potions are on first arrival at depth 6. "Last fight began" gives hit points over maximum and how many turns before death. The last column counts retreat, cornered and attack Decisions in the last 30.

| Class | Salt | Seed | Depth | Turn | Killer | Level | On arrival at 6: hunger, food, healing | Hunger at death | Last fight began (HP, turns before death) | Last 30: retreat / cornered / attack |
|---|---:|---|---:|---:|---|---:|---|---|---|---|
| Warrior | 1000 | DCS-SHA-XYL | 7 | 2477 | DM-100 | 7 | hungry, 1, 2 | starving | 30/50, 18 | 6 / 2 / 3 |
| Warrior | 1001 | CWJ-VZE-FLJ | 7 | 2539 | DM-100 | 8 | starving, 0, 3 | starving | 25/55, 102 | 9 / 0 / 0 |
| Warrior | 1002 | HJO-WWK-PJG | 6 | 2998 | hunger | 8 | starving, 0, 3 | starving | 27/55, 63 | 5 / 5 / 17 |
| Warrior | 1003 | ELH-RAA-OYI | 6 | 3306 | hunger | 7 | none, 2, 3 | starving | 14/50, 245 | 1 / 0 / 0 |
| Warrior | 1005 | NSP-PPL-WOK | 6 | 2204 | poison | 7 | none, 3, 3 | hungry | 43/50, 34 | 0 / 0 / 22 |
| Warrior | 1006 | TAZ-YXM-MIA | 6 | 1961 | skeleton | 7 | starving, 0, 0 | starving | 20/50, 20 | 10 / 5 / 0 |
| Warrior | 1007 | DLR-YWZ-ABC | 7 | 3330 | skeleton | 8 | hungry, 4, 0 | hungry | 29/55, 34 | 22 / 2 / 4 |
| Warrior | 1009 | JJY-MSI-MBN | 9 | 3113 | necromancer's skeleton | 9 | starving, 0, 1 | none | 60/60, 55 | 3 / 17 / 1 |
| Warrior | 1011 | EIW-QWY-ISZ | 8 | 3105 | necromancer's skeleton | 10 | starving, 0, 5 | starving | 19/65, 22 | 4 / 3 / 10 |
| Warrior | 1013 | SLD-KZY-FFR | 6 | 2768 | skeleton | 8 | starving, 0, 1 | starving | 10/55, 16 | 6 / 8 / 0 |
| Warrior | 1014 | MZX-IGB-NNM | 7 | 2976 | piranha | 8 | hungry, 2, 2 | starving | 15/55, 23 | 20 / 0 / 0 |
| Warrior | 1016 | KMQ-KCV-KGY | 6 | 1749 | skeleton | 5 | none, 0, 0 | starving | 40/40, 146 | 16 / 7 / 0 |
| Warrior | 1017 | HBS-HHV-SJW | 7 | 2867 | skeleton | 9 | starving, 0, 1 | starving | 4/60, 14 | 5 / 4 / 0 |
| Warrior | 1018 | EPS-SUK-IGG | 6 | 1841 | skeleton | 7 | starving, 0, 0 | none | 14/50, 13 | 3 / 6 / 0 |
| Warrior | 1019 | PMA-UHB-YQN | 6 | 1905 | hunger | 6 | hungry, 0, 0 | starving | 2/45, 27 | 8 / 8 / 0 |
| Warrior | 1022 | PCM-GCM-KSF | 7 | 2250 | DM-100 | 8 | none, 3, 1 | none | 9/55, 6 | 4 / 0 / 0 |
| Warrior | 1025 | IMD-WTI-GQM | 7 | 2646 | skeleton | 9 | starving, 0, 3 | starving | 30/60, 19 | 11 / 5 / 3 |
| Warrior | 1027 | GIT-LPU-LWW | 6 | 1964 | thief | 7 | hungry, 0, 2 | none | 15/50, 24 | 4 / 8 / 0 |
| Warrior | 1028 | AGH-VZC-PTA | 7 | 2344 | DM-100 | 8 | hungry, 2, 0 | none | 23/55, 52 | 8 / 0 / 7 |
| Warrior | 1030 | YLV-DUF-MFY | 7 | 2025 | DM-100 | 7 | starving, 0, 2 | none | 21/50, 57 | 22 / 1 / 0 |
| Warrior | 1031 | ODX-OHE-HTO | 8 | 2246 | necromancer's skeleton | 7 | hungry, 2, 1 | none | 23/50, 70 | 0 / 23 / 0 |
| Warrior | 1032 | ILM-OET-BYZ | 7 | 2232 | DM-100 | 8 | none, 3, 1 | none | 26/55, 11 | 0 / 10 / 0 |
| Warrior | 1033 | KKO-SIP-SSO | 8 | 3030 | guard | 9 | none, 5, 1 | starving | 16/60, 61 | 6 / 2 / 0 |
| Warrior | 1034 | IIU-BNU-NUS | 6 | 2707 | hunger | 7 | starving, 0, 2 | starving | 14/50, 260 | 8 / 0 / 0 |
| Warrior | 1035 | LSV-WXG-BAU | 6 | 2099 | skeleton | 7 | hungry, 0, 1 | starving | 14/50, 7 | 4 / 3 / 0 |
| Warrior | 1036 | TDR-WKF-GUZ | 6 | 2385 | poison | 8 | hungry, 2, 2 | hungry | no fight | 6 / 3 / 0 |
| Warrior | 1037 | RVS-QHY-GIN | 6 | 2846 | hunger | 7 | hungry, 0, 1 | starving | no fight | 0 / 0 / 0 |
| Warrior | 1038 | ITW-BLO-QQL | 8 | 2805 | DM-100 | 9 | starving, 2, 3 | starving | 40/60, 80 | 20 / 1 / 0 |
| Warrior | 1039 | LXO-FXV-JQT | 6 | 2676 | skeleton | 7 | starving, 0, 0 | none | 17/50, 14 | 2 / 7 / 0 |
| Mage | 1001 | ZSH-YHE-YRE | 8 | 3578 | bee | 11 | none, 3, 2 | starving | 3/70, 31 | 8 / 0 / 0 |
| Mage | 1002 | DLG-RPU-AXM | 8 | 2435 | necromancer's skeleton | 8 | none, 1, 0 | starving | 29/55, 312 | 6 / 16 / 4 |
| Mage | 1003 | URJ-DFM-XXX | 6 | 1731 | piranha | 6 | none, 1, 0 | hungry | 40/45, 77 | 18 / 3 / 6 |
| Mage | 1004 | ZEG-SKZ-QUY | 7 | 2019 | DM-100 | 8 | none, 0, 0 | starving | 13/55, 2 | 3 / 0 / 0 |
| Mage | 1006 | YRG-QGQ-RZT | 6 | 1714 | bomb | 6 | hungry, 3, 1 | none | 17/45, 10 | 0 / 0 / 4 |
| Mage | 1007 | HSP-QJD-FIK | 6 | 2451 | skeleton | 7 | hungry, 0, 0 | hungry | 50/50, 34 | 4 / 17 / 0 |
| Mage | 1008 | CSX-MOL-BIN | 7 | 2460 | DM-100 | 8 | none, 5, 0 | starving | no fight | 0 / 0 / 11 |
| Mage | 1009 | USU-KZC-OAP | 8 | 2480 | guard | 9 | hungry, 7, 2 | none | 12/60, 2 | 1 / 0 / 0 |
| Mage | 1012 | VIM-CPE-UTZ | 8 | 1927 | DM-100 | 8 | none, 0, 1 | hungry | 22/55, 8 | 6 / 0 / 1 |
| Mage | 1013 | MBE-BDC-TSB | 6 | 1478 | skeleton | 5 | none, 1, 0 | hungry | 40/40, 26 | 14 / 7 / 0 |
| Mage | 1015 | HDW-MVW-IBB | 7 | 2713 | DM-100 | 8 | starving, 0, 0 | starving | 15/55, 46 | 15 / 1 / 0 |
| Mage | 1016 | UBB-REM-UUS | 7 | 2369 | guard | 7 | hungry, 2, 0 | starving | 6/50, 2 | 3 / 0 / 0 |
| Mage | 1018 | YNK-PZI-SLA | 6 | 2264 | skeleton | 7 | hungry, 0, 1 | starving | 13/50, 34 | 18 / 8 / 0 |
| Mage | 1019 | IMM-IFY-LVO | 7 | 1835 | DM-100 | 7 | none, 0, 1 | starving | 46/50, 26 | 1 / 0 / 1 |
| Mage | 1020 | MRM-GBB-NCG | 8 | 2750 | DM-100 | 9 | none, 4, 1 | hungry | 21/60, 5 | 2 / 0 / 1 |
| Mage | 1021 | QTB-QCJ-FGJ | 6 | 1947 | bomb | 7 | hungry, 1, 0 | starving | 20/50, 7 | 1 / 0 / 1 |
| Mage | 1022 | QEL-TZL-DMB | 7 | 2366 | skeleton | 8 | starving, 0, 1 | hungry | 18/55, 46 | 10 / 16 / 0 |
| Mage | 1023 | KLF-BYB-RCZ | 7 | 2109 | hunger | 7 | none, 3, 0 | starving | no fight | 0 / 0 / 0 |
| Mage | 1024 | RIC-OIC-HFF | 7 | 1955 | skeleton | 7 | none, 2, 1 | hungry | 46/50, 30 | 5 / 14 / 2 |
| Mage | 1026 | DMI-XZZ-OAY | 6 | 2706 | ooze | 8 | starving, 0, 2 | starving | 13/55, 5 | 1 / 0 / 0 |
| Mage | 1029 | KYR-KLQ-XZO | 7 | 2384 | DM-100 | 9 | hungry, 2, 2 | hungry | 31/60, 21 | 13 / 0 / 0 |
| Mage | 1030 | YGV-GVJ-PDR | 7 | 2217 | DM-100 | 7 | hungry, 0, 2 | starving | 24/50, 17 | 5 / 0 / 0 |
| Mage | 1031 | IJQ-TUD-SVE | 6 | 2106 | skeleton | 7 | none, 2, 1 | hungry | 10/50, 30 | 15 / 5 / 0 |
| Mage | 1034 | FXX-GYR-KHE | 7 | 1705 | DM-100 | 6 | hungry, 2, 0 | hungry | 45/45, 24 | 5 / 8 / 0 |
| Mage | 1036 | WRR-YQT-EVQ | 8 | 3120 | necromancer's skeleton | 10 | none, 2, 0 | none | 14/65, 36 | 6 / 10 / 0 |
| Mage | 1037 | MFV-ZTE-YDR | 7 | 2582 | skeleton | 9 | starving, 3, 1 | starving | 5/60, 9 | 8 / 3 / 0 |
| Mage | 1039 | ZSE-PZZ-KMH | 6 | 1938 | skeleton | 6 | starving, 0, 0 | starving | 22/45, 54 | 8 / 19 / 0 |
| Rogue | 1005 | KVT-HPD-XRD | 6 | 2154 | skeleton | 7 | starving, 0, 0 | starving | 5/50, 12 | 12 / 1 / 0 |
| Rogue | 1007 | XQP-BCF-RFH | 6 | 2454 | skeleton | 7 | starving, 0, 0 | starving | 10/50, 35 | 23 / 5 / 0 |
| Rogue | 1010 | OBH-JFX-VLX | 6 | 2147 | skeleton | 7 | starving, 0, 0 | starving | 3/50, 12 | 5 / 1 / 0 |
| Rogue | 1011 | EHD-JKF-AIN | 6 | 1765 | hunger | 6 | hungry, 1, 1 | starving | no fight | 0 / 0 / 0 |
| Rogue | 1015 | ORF-FEU-RTB | 7 | 2743 | DM-100 | 8 | none, 2, 0 | starving | 16/55, 11 | 8 / 0 / 0 |
| Rogue | 1018 | IRK-RGJ-VYN | 7 | 3364 | guard | 9 | starving, 4, 3 | starving | 51/60, 24 | 15 / 9 / 0 |
| Rogue | 1028 | BGJ-NMI-ROF | 6 | 2075 | skeleton | 6 | starving, 0, 0 | starving | 11/45, 11 | 5 / 6 / 0 |
| Rogue | 1029 | KOJ-QMD-ZMY | 6 | 2009 | hunger | 6 | hungry, 0, 0 | starving | 3/45, 45 | 23 / 3 / 0 |
| Rogue | 1030 | KOT-XQN-SDI | 7 | 1860 | guard | 6 | hungry, 2, 0 | starving | 17/45, 1 | 0 / 2 / 0 |
| Rogue | 1031 | OCI-WDR-RGG | 8 | 2807 | DM-100 | 9 | hungry, 1, 1 | starving | 55/60, 14 | 1 / 0 / 6 |
| Rogue | 1034 | HHX-TGT-ESH | 6 | 2491 | skeleton | 7 | none, 0, 0 | starving | 50/50, 80 | 11 / 12 / 7 |
| Rogue | 1035 | QNX-MOY-SNO | 6 | 2808 | skeleton | 8 | starving, 0, 3 | starving | 11/55, 13 | 2 / 5 / 0 |
| Huntress | 1000 | EWT-BZS-OQP | 7 | 2546 | DM-100 | 8 | none, 1, 2 | none | 22/55, 7 | 4 / 0 / 4 |
| Huntress | 1001 | JIK-OJQ-DXI | 6 | 2302 | skeleton | 7 | hungry, 3, 0 | hungry | 12/50, 9 | 2 / 9 / 0 |
| Huntress | 1004 | CPW-YFI-CHQ | 6 | 1386 | skeleton | 5 | starving, 0, 0 | starving | 23/40, 26 | 12 / 15 / 0 |
| Huntress | 1005 | GPR-WAN-SFD | 6 | 1897 | skeleton | 7 | none, 3, 1 | none | 21/50, 15 | 4 / 17 / 0 |
| Huntress | 1006 | QJN-BDR-JFA | 7 | 2102 | DM-100 | 7 | none, 4, 1 | none | 12/50, 4 | 5 / 0 / 0 |
| Huntress | 1007 | NRE-VXJ-RCF | 8 | 2760 | DM-100 | 9 | none, 5, 2 | hungry | 35/60, 19 | 10 / 0 / 0 |
| Huntress | 1010 | DLX-FFB-NPK | 6 | 1729 | skeleton | 6 | none, 2, 1 | none | 36/45, 74 | 3 / 11 / 0 |
| Huntress | 1011 | GHF-BBY-OMQ | 8 | 2990 | hunger | 10 | none, 0, 0 | starving | 8/65, 99 | 7 / 0 / 0 |
| Huntress | 1015 | WOV-THC-BBL | 8 | 2093 | guard | 8 | none, 1, 1 | starving | 29/55, 19 | 6 / 19 / 0 |
| Huntress | 1017 | PKW-JXF-EHA | 6 | 2114 | hunger | 7 | starving, 0, 0 | starving | no fight | 0 / 15 / 0 |
| Huntress | 1018 | FLO-FNT-MGB | 6 | 1617 | ooze | 6 | none, 3, 2 | none | 45/45, 49 | 2 / 0 / 3 |
| Huntress | 1021 | HJX-QWQ-JBY | 6 | 2104 | skeleton | 7 | none, 1, 0 | starving | 15/50, 66 | 13 / 11 / 0 |
| Huntress | 1023 | PKQ-IGE-XAR | 8 | 2484 | necromancer's skeleton | 7 | none, 4, 0 | starving | 2/50, 13 | 9 / 0 / 0 |
| Huntress | 1024 | QUZ-ZVQ-VDZ | 7 | 2584 | DM-100 | 8 | none, 1, 1 | hungry | 47/55, 18 | 1 / 1 / 3 |
| Huntress | 1025 | ASB-SDG-JYZ | 9 | 3230 | necromancer's skeleton | 11 | none, 0, 1 | starving | 12/70, 10 | 2 / 5 / 0 |
| Huntress | 1026 | KKS-LWS-GAI | 7 | 2806 | guard | 9 | none, 1, 0 | starving | 31/55, 56 | 6 / 24 / 0 |
| Huntress | 1027 | YTZ-QYC-NWI | 6 | 1661 | bomb | 6 | none, 1, 0 | none | no fight | 0 / 0 / 0 |
| Huntress | 1028 | SAM-IFK-TWL | 6 | 2809 | hunger | 8 | hungry, 1, 0 | starving | no fight | 0 / 0 / 0 |
| Huntress | 1030 | JAW-PKU-ZSM | 7 | 2632 | DM-100 | 9 | none, 1, 3 | starving | 34/55, 78 | 3 / 3 / 13 |
| Huntress | 1031 | PKJ-MRT-GGL | 6 | 2029 | skeleton | 7 | none, 0, 1 | starving | 20/50, 47 | 9 / 10 / 0 |
| Huntress | 1032 | JYX-YCF-BPG | 6 | 2074 | ooze | 7 | none, 1, 1 | starving | 10/50, 4 | 6 / 0 / 0 |
| Huntress | 1035 | FMT-JYF-YMK | 7 | 2543 | guard | 8 | none, 0, 0 | none | 31/55, 47 | 8 / 10 / 0 |
| Huntress | 1036 | CDC-GBY-WZL | 7 | 2550 | DM-100 | 8 | hungry, 2, 1 | hungry | 12/55, 6 | 4 / 0 / 0 |
| Huntress | 1038 | YLI-CIA-CQU | 7 | 2642 | hunger | 7 | none, 2, 3 | starving | no fight | 0 / 0 / 0 |
| Huntress | 1039 | SOA-VTQ-UFT | 6 | 2244 | skeleton | 7 | hungry, 3, 0 | starving | 38/50, 21 | 15 / 13 / 0 |
| Duelist | 1001 | MUU-MIE-OUQ | 7 | 2619 | skeleton | 9 | none, 1, 1 | starving | 8/60, 7 | 1 / 6 / 0 |
| Duelist | 1002 | LDG-ZAJ-KRO | 7 | 2511 | skeleton | 8 | hungry, 1, 2 | starving | 10/55, 14 | 5 / 10 / 0 |
| Duelist | 1007 | BVZ-KMR-BNL | 7 | 3116 | DM-100 | 9 | starving, 0, 2 | hungry | 22/60, 12 | 3 / 0 / 3 |
| Duelist | 1012 | KKU-NKF-FMS | 6 | 2138 | sentry | 7 | hungry, 1, 2 | none | no fight | 0 / 0 / 0 |
| Duelist | 1013 | OOY-UUN-PRH | 6 | 1816 | hunger | 6 | hungry, 0, 1 | starving | 7/45, 22 | 3 / 2 / 0 |
| Duelist | 1015 | NFI-NJO-AEQ | 8 | 2086 | DM-100 | 8 | none, 4, 0 | hungry | 34/55, 28 | 20 / 0 / 0 |
| Duelist | 1018 | BTO-DMJ-CID | 6 | 2274 | bee | 7 | starving, 0, 1 | starving | 13/50, 167 | 14 / 4 / 0 |
| Duelist | 1019 | IGC-UEN-HRK | 6 | 1702 | skeleton | 6 | starving, 0, 1 | starving | 6/45, 19 | 13 / 2 / 0 |
| Duelist | 1020 | LFJ-SOY-NHH | 7 | 3017 | DM-100 | 9 | hungry, 5, 0 | none | 9/60, 3 | 4 / 0 / 0 |
| Duelist | 1023 | ECF-HSU-SIP | 7 | 2833 | guard | 9 | none, 1, 1 | none | 37/60, 93 | 17 / 7 / 3 |
| Duelist | 1024 | OHC-XBQ-MUS | 6 | 1903 | burning | 7 | hungry, 0, 0 | starving | no fight | 4 / 11 / 0 |
| Duelist | 1027 | HRO-QIF-RVK | 8 | 2409 | wraith | 9 | none, 2, 2 | starving | 52/55, 146 | 0 / 30 / 0 |
| Duelist | 1028 | VBW-IGJ-OUJ | 8 | 3089 | guard | 9 | hungry, 0, 4 | starving | 4/60, 5 | 8 / 5 / 0 |
| Duelist | 1029 | BAV-TNP-CCE | 7 | 2754 | skeleton | 8 | none, 2, 1 | starving | 6/55, 8 | 3 / 5 / 0 |
| Duelist | 1030 | FYL-APY-UME | 7 | 2400 | skeleton | 8 | hungry, 0, 2 | starving | 7/55, 27 | 16 / 9 / 0 |
| Duelist | 1032 | XZE-GBM-IUU | 7 | 2866 | thief | 9 | hungry, 1, 0 | hungry | 10/60, 23 | 8 / 3 / 0 |
| Duelist | 1033 | JPY-EFV-PKF | 7 | 2293 | DM-100 | 7 | hungry, 0, 0 | starving | 11/50, 32 | 7 / 21 / 0 |
| Duelist | 1034 | HYZ-YEW-GGQ | 6 | 1950 | piranha | 7 | starving, 0, 1 | none | 15/50, 13 | 10 / 1 / 0 |
| Duelist | 1035 | CRD-NOU-BDG | 6 | 2176 | poison | 7 | none, 2, 2 | starving | 42/45, 339 | 0 / 4 / 0 |
| Duelist | 1036 | BPA-MAM-LBM | 8 | 1853 | electricity | 7 | none, 1, 0 | hungry | 30/45, 62 | 10 / 0 / 17 |
| Duelist | 1037 | FEN-MTX-JMH | 7 | 2808 | bleeding | 8 | none, 1, 1 | starving | no fight | 0 / 0 / 0 |
| Duelist | 1038 | GEM-DPO-MHG | 6 | 1870 | skeleton | 6 | starving, 0, 0 | starving | 8/45, 12 | 10 / 3 / 0 |
| Duelist | 1039 | MMR-RZY-IMG | 6 | 2426 | skeleton | 8 | hungry, 1, 1 | starving | 6/55, 19 | 5 / 9 / 0 |
| Cleric | 1001 | UQQ-NYM-YLJ | 8 | 2418 | guard | 8 | none, 2, 0 | starving | 27/55, 35 | 24 / 5 / 0 |
| Cleric | 1002 | DGW-BYX-VPX | 7 | 2277 | hunger | 8 | hungry, 1, 1 | starving | no fight | 0 / 0 / 0 |
| Cleric | 1003 | CRG-LPG-PSR | 7 | 3286 | toxicgas | 10 | none, 2, 0 | hungry | no fight | 0 / 0 / 0 |
| Cleric | 1004 | FMP-BBR-HNU | 7 | 2590 | DM-100 | 8 | none, 2, 2 | starving | 40/55, 7 | 2 / 0 / 2 |
| Cleric | 1005 | OPP-ZBH-ZHP | 9 | 2590 | necromancer's skeleton | 9 | hungry, 1, 0 | starving | 60/60, 91 | 15 / 5 / 6 |
| Cleric | 1006 | OTE-CJR-RSK | 7 | 3081 | skeleton | 9 | hungry, 0, 1 | starving | 17/60, 12 | 2 / 8 / 0 |
| Cleric | 1007 | ECT-VFN-OFE | 7 | 1994 | DM-100 | 7 | hungry, 0, 0 | none | 28/50, 4 | 2 / 0 / 6 |
| Cleric | 1008 | ZIR-RZA-XCM | 7 | 2907 | DM-100 | 10 | none, 3, 2 | hungry | 26/65, 17 | 4 / 0 / 1 |
| Cleric | 1009 | EHQ-DHC-BJP | 6 | 1830 | hunger | 6 | starving, 0, 0 | starving | 2/45, 26 | 6 / 0 / 0 |
| Cleric | 1010 | SQZ-FNO-KUX | 7 | 2219 | burning | 7 | hungry, 2, 0 | hungry | no fight | 2 / 0 / 0 |
| Cleric | 1011 | HVE-BNQ-VPM | 9 | 2766 | guard | 9 | none, 5, 1 | starving | 4/60, 5 | 8 / 0 / 3 |
| Cleric | 1014 | AOU-UKX-ZQY | 6 | 2124 | poison | 7 | none, 4, 0 | hungry | 33/50, 68 | 6 / 2 / 0 |
| Cleric | 1016 | UDI-ZZB-QBC | 8 | 2884 | guard | 9 | none, 2, 2 | starving | 31/60, 47 | 4 / 9 / 5 |
| Cleric | 1017 | GOE-HGP-KND | 7 | 2140 | DM-100 | 7 | hungry, 0, 0 | starving | no fight | 6 / 2 / 0 |
| Cleric | 1018 | OPE-OEW-IJE | 7 | 2257 | guard | 8 | starving, 0, 1 | starving | 45/55, 33 | 28 / 1 / 0 |
| Cleric | 1019 | TUD-BAW-ZWC | 8 | 2979 | necromancer's skeleton | 9 | none, 2, 0 | none | 43/60, 75 | 0 / 3 / 25 |
| Cleric | 1020 | ALE-OHF-UEP | 7 | 2897 | DM-100 | 9 | hungry, 2, 0 | starving | 24/60, 6 | 7 / 4 / 3 |
| Cleric | 1021 | ICD-WHC-GXS | 8 | 2357 | necromancer's skeleton | 9 | hungry, 4, 0 | starving | 41/60, 44 | 9 / 9 / 5 |
| Cleric | 1023 | RIE-CZA-UHJ | 7 | 2451 | bomb | 7 | hungry, 5, 1 | starving | 20/50, 19 | 6 / 0 / 0 |
| Cleric | 1024 | EEC-RMW-HVL | 8 | 2304 | necromancer's skeleton | 8 | hungry, 1, 3 | starving | 53/55, 75 | 3 / 10 / 11 |
| Cleric | 1025 | IMF-JWA-ZVD | 7 | 1786 | skeleton | 6 | hungry, 0, 1 | starving | 28/45, 81 | 13 / 10 / 0 |
| Cleric | 1026 | LAG-ODV-LEW | 6 | 2137 | hunger | 7 | none, 1, 3 | starving | 1/50, 1 | 1 / 1 / 0 |
| Cleric | 1027 | GUJ-KMS-KIS | 8 | 2820 | bleeding | 8 | hungry, 1, 1 | starving | 15/55, 6 | 2 / 0 / 0 |
| Cleric | 1029 | NJC-PRD-JPD | 7 | 2399 | skeleton | 8 | none, 2, 2 | starving | 41/55, 268 | 4 / 7 / 0 |
| Cleric | 1030 | ODW-MVX-KOW | 8 | 3136 | DM-100 | 10 | none, 4, 3 | hungry | 47/65, 331 | 4 / 9 / 16 |
| Cleric | 1032 | DQP-INR-NBI | 7 | 1938 | DM-100 | 8 | hungry, 2, 0 | none | 52/55, 12 | 4 / 0 / 1 |
| Cleric | 1033 | AKW-BPE-DFI | 7 | 2412 | DM-100 | 8 | none, 3, 3 | hungry | 23/55, 5 | 5 / 0 / 0 |
| Cleric | 1036 | BKU-OXW-ENJ | 8 | 2211 | necromancer's skeleton | 8 | hungry, 1, 1 | hungry | 21/55, 20 | 4 / 6 / 0 |
| Cleric | 1038 | QVW-EFP-FXG | 8 | 2612 | DM-100 | 9 | hungry, 1, 1 | none | 16/60, 31 | 13 / 2 / 0 |
