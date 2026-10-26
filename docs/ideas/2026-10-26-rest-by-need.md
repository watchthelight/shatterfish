# Rest by need: spend the rest turns where the hit points are low, and pay for them in food

Issue #219, the Goo gate (story 4.14). On main `efbccd998` the tuning slice (standard, WARRIOR, salts 1000 to 1039)
reaches depth 5 in 28 of 40 Runs and kills Goo in 25, so reach is the gap. Two single rules have been measured and
reverted (`2026-10-26-rest-when-hurt-and-fed.md`, `2026-10-26-stairs-ahead-of-a-crab.md`); this page starts from the
hit point and food economy of all 40 Runs rather than from the 12 deaths alone.

## What was read first (so it is not repeated)

- Rest when under half, fed and not frugal: 66 to 67 Goo kills over 120 Runs, median turns up 70 to 130 (page above).
- Stairs ahead of a crab: no Decision changed in 40 Runs (page above).
- Not on a page, in watchthelight: dew rest, room pickup and a heal from the waterskin, a search cap, no search-ahead
  (all neutral to negative, and fewer turns meant fewer wandering spawns and less experience); skipping the post-test
  rest when food is tight (cut turns, hurt the 80 slice); rest on depth 5 and eat before the boss (Goo kills
  unchanged); kiting a lone chaser (works as a mechanism, Goo kills not raised); walking away from fast enemies
  (merged as the outpaced rule, crab deaths 18 to 10 over 120 Runs).

## Mechanics (pinned v4.0.2)

- Regeneration is one hit point every 10 turns (`Regeneration.java:44`) and stops while starving (`:56`).
- Hunger rises one a turn; hungry at 300, starving at 450 (`Hunger.java:40-41`); at 450 the hero takes one point of
  damage every turn (`Hunger.java:96-99`). A ration is 300 turns (`Food.java:51`), one food is placed per ordinary
  floor (`Level.java:224-226`).
- So a Run holds `450 + 300 x food found` turns, and a hero that runs out has about its hit point count in turns left.
  Hunger is the one clock a rest cannot beat: a turn spent resting is a turn of food.

## Data: all 40 tuning Runs, depths 1 to 4 (debug records, `build/direction/main40`)

28 Runs reach depth 5, 12 do not. Per floor, mean over the Runs that reached the floor:

| floor | group | n | turns | fights | fights starting under half HP | mean HP at fight start | food found | food eaten | exit hunger (none/hungry/starving) |
|---|---|---|---|---|---|---|---|---|---|
| 1 | reach | 28 | 344 | 4.3 | 1 of 121 | 0.97 | 1.0 | 0.9 | 27/1/0 |
| 2 | reach | 28 | 386 | 6.7 | 1 of 188 | 0.92 | 0.8 | 1.3 | 22/5/1 |
| 2 | died | 12 | 703 | 9.6 | 9 of 115 | 0.86 | 1.3 | 1.5 | 7/0/5 |
| 3 | reach | 28 | 621 | 8.3 | 22 of 231 | 0.80 | 1.4 | 1.5 | 15/7/6 |
| 3 | died | 11 | 545 | 7.6 | 25 of 84 | 0.68 | 1.0 | 1.0 | 6/1/4 |
| 4 | reach | 28 | 652 | 9.2 | 46 of 258 | 0.71 | 2.1 | 1.9 | 5/10/13 |
| 4 | died | 4 | 362 | 5.0 | 8 of 20 | 0.59 | 1.3 | 1.0 | 2/2/0 |

What the table says:

1. The hero is hurt at fight start far more often in the Runs that die (depth 3: 30% of fights under half HP against
   9.5%), and more and more often with depth even in the Runs that live (depth 4: 18%).
2. Floors cost 340 to 650 turns against one food (300) a floor. By depth 4 exit 13 of 28 surviving Runs are starving.
   Surviving is a race with the hunger clock, so every added rest has to be paid for.
3. Only 15 of the 35 under-half fights on depths 2 to 4 in the dying Runs began with a clear hunger icon; 11 began
   starving (no regeneration, no food) and 9 hungry. A rest rule that needs a clear icon reaches under half of them,
   which is why the earlier rule moved so little.
4. Rest turns are spent on the wrong hit points. Over depths 1 to 4 the 40 Runs rested about 9,400 turns: 4,300 of them
   `rest: after-test` (rest owed within a few waits of a potion or scroll test), of which 3,739 turns began at 60 to
   90% of full health and regained 368 points, against 974 turns at under 60%. A test is not an injury: that rest
   is paid for out of the same food as a rest before a crab and bought less.
5. Exploring under half HP with a clear icon was 1,620 turns (about 40 per Run), at 50 to 75% a further 5,584. The
   hero is passively healing while it walks (a Run takes about 2,000 turns, 200 points of regeneration, against about
   125 points of damage per Run), so resting is not new healing, only healing taken before the next fight and not
   during it. What is lost to damage is lost in bursts: multi-enemy fights 1,031 points over the 40 Runs, gnoll
   scouts 958, flies 781, crabs 673, slimes 456, snakes 428.
6. Time sinks that are not fights: exploring frontier 482 turns a Run, walking to heaps 228, walking to the exit 204,
   searching 122 plus 54 for search spots. The long floors (850 to 1,640 turns) are an exit walk of 350 to 370 turns,
   a random-fallback stretch of 697 turns once, and rests of 200 to 340 turns that ran into hunger.

## The design: rest by need, funded by food

Two changes, one idea: the rest budget follows hit points and food, not events.

1. **Rest before exploring on, under half hit points, when the food pays for it** (`Explore.hurtRestOwed`): the hunger
   icon is clear, the floor is not locked, and `Larder.budget` (food held plus the clock left) less ten turns for each
   missing hit point still leaves 300 turns, a ration. A hero with a spare ration and a full stomach at 8 of 40 hit
   points rests (750 less 320 is 430); one with no spare food does not (450 less 320 is 130) and walks on, healing as
   it goes, as now. At most 8 such rests a floor (a rest is one Action that runs until healed or disturbed), counted in
   `Memory.rests`. Only while the explore Policy has somewhere to go: a spent floor is the descend and plunge
   Policies' rest.
2. **The post-test rest is for the hurt** (`TestItem.restOwed`): under three quarters of full health rather than under
   full. This takes back the 60 to 90% rests (about 100 turns a Run, a third of a ration) that the first change
   spends on the hurt.

Information parity: the rule reads hit points, the hunger icon, the buffs and the pack, all on the screen, and the
Brain's own clock (`Larder`). It never reads the true hunger value.

## What this is not

It does not touch the fight Policy, retreat or escape (another line of work), the descent rest, or the food pickup.
The earlier rule rested at under half with budget 600; this one prices the rest in the food it takes, and gives back
the turns from rests that the data shows buy little.

## Numbers

Filled in below after the runs.
