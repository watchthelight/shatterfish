---
title: 'Story 4.13: Reach the Goo gate on the smoke set'
type: 'feature'
created: '2026-09-25'
status: 'in-progress'
baseline_commit: 'c56636756'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** SM-3 asks the Warrior to kill Goo on at least 75% of the registered `goo` set (story
4.14). The Brain after story 4.12 reaches depth 5 on none of 25 `smoke` Runs (mean deepest floor
2.52, max 4) and on none of 40 Warrior Runs. Nothing says why: a Run log's ending has no killer, and
the death gallery (story 3.12) groups by ending and depth only (FR-26's E3 half).

**Approach:** Build the death gallery's per-Brain comparison view (FR-26's E4 half) and use it, with
an in-process probe of the last screens, to find the dominant failure causes; fix them in small
measured steps, each kept only when it improves Warrior depth or Goo reach and does not regress
`smoke`; and give the Brain what it needs for the Goo fight. Every number is a direction check,
never an acceptance.

## Boundaries & Constraints

**Always:**
- Every tuning step has its own direction check, recorded in the tuning log below: kept or reverted.
- The tuning set is **not** part of `goo`. `goo` is the registered acceptance set of story 4.14:
  tuning on any of its triples would fit the Brain to the seeds it is judged on, and nothing could
  un-run them afterwards (the same reason `holdout` is refused during development,
  docs/methodology.md). The tuning set is the first 40 Warrior triples of `standard` -- indices 0,
  6, 12, ... of `seeds/standard.json` -- each under salt `1000 + j`, `j` its position among the 40.
  `smoke` is measured separately, as the story names.
- Screen data and Beliefs only (non-negotiable 1); every mechanics claim cited (non-negotiable 8).
- No acceptance claim from `smoke` or the tuning set.

**Ask First:** anything that reads what the screen does not draw, e.g. Goo's pump-up animation
without an Observer change and its leak tests.

**Never:**
- Running `goo` (or `holdout`) during development.
- Reading game code from `brain`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Two Rig folders of the same triples | two Brains' Run logs | comparison page: endings, deaths by situation, every shared triple worst first | a triple in one folder only is counted, not compared |
| A folder of RunOne logs, no index | `*.jsonl` only | read every log | an unreadable log is skipped |
| The same triple and salt twice in a folder | two logs | refused, naming the key | N/A |
| Dizzy on a calm screen | the hero shows vertigo, no enemy in view | no Step and no stairs; a search a wait until it passes | N/A |
| Dizzy in a fight, a Step at a held cell | still after the Step, vertigo | refusals count; `VERTIGO_REFUSALS` in a row block the cell | the block lapses (a fight block) |
| At the low-health warning, a known healing potion, a retreat open | hp under a third, danger | the drink first, then the retreat | cooldown as story 4.9 |

</frozen-after-approval>

## Code Map

- `shatterfish/rig/.../GalleryComparison.java`: the per-Brain comparison view; `Gallery.main`
  writes it beside a comparison's two sides and takes `--compare <baseline> <candidate> <page>`.
- `shatterfish/rig/src/test/.../GalleryComparisonTest.java`, `GalleryTest.java`.
- `shatterfish/brain/.../Explore.java`: `dizzy`; `Brain.java`: no Steps or stairs while dizzy on a
  calm screen; `Beliefs.java`: vertigo's refusals count, `VERTIGO_REFUSALS` to block.
- `shatterfish/brain/.../Heal.java`: at the low-health warning the drink comes before a retreat.
- `shatterfish/brain/.../TestItem.java`: potions likely to be strength or experience are drunk at any
  health (`GAINS`, `GAIN_ODDS`).
- `shatterfish/brain/.../Memory.java`: version 10 -- story 4.11's version 9 (`windows`) plus `prior`,
  `bounces`, `hunger`, `hp` and `food`; `Spot` checks its own fields (an initialisation cycle).
- `shatterfish/brain/.../Larder.java`: the hunger clock and the food budget; `Explore` searches only
  promising spots while food is tight.
- `docs/methodology.md`: the comparison view.

## Tasks & Acceptance

**Execution:**
- [x] The comparison view, its tests, the methodology section.
- [x] The failure analysis: comparison view plus an in-process probe of the last screens.
- [ ] Tuning steps, each measured (the tuning log).
- [ ] The Goo fight.
- [ ] Rules rows, Brain Rules index rows, docs.
- [ ] Mutation battery.

**Acceptance Criteria:**
- The death gallery's per-Brain comparison view exists and is tested
  (`GalleryComparisonTest.keyed`, `the_page`, `deterministic`, `command_and_refusal`;
  `GalleryTest.main_on_a_comparison`), and was used to find the dominant failure cause (below).
- The smoke-set Goo kill rate and the failure causes are published as a direction check in the PR,
  never as an acceptance.
- Each tuning step's rule is tested: `DescendPolicyTest.vertigo`, `vertigo_refusals`;
  `HealPolicyTest.drinks_then_retreats`, `retreat_before_drinking`;
  `TestItemPolicyTest.potion_for_gains`, `potion_needs_a_use`.

## Design Notes

**The tuning set:**
1. A slice of `goo`, e.g. its first 40 triples. Rejected: `goo` is story 4.14's registered
   acceptance set; tuning on part of it fits the Brain to seeds it will be judged on, the way a
   development run of `holdout` would (docs/methodology.md), and the harm cannot be undone.
2. `smoke` alone. Rejected as the tuning set: 5 Warriors among 25 triples is too few to see a change
   in Goo reach.
3. **The first 40 Warrior triples of `standard`.** Chosen: `standard` is the development set,
   derived from another constant than `goo`, and all Warriors like `goo`.

**What the comparison view groups by:**
1. By ending and depth only, like the E3 gallery. Rejected: every Run dies, mostly on 2 and 3;
   the table says nothing about why.
2. By the killer. Not possible: a log's Outcome has no killer (docs/ideas.md).
3. **By the situation: the last wait's Policy and Safety flags.** Chosen: it is in every Brain log,
   it says what the Brain was doing and what was wrong on the screen that ended the Run, and the
   flags (`hp-low`, `starving`, `hungry`, `enemy-in-view`) separate starvation from combat.

**Pre-mortem:**
- *Tuning fits the tuning set.* Each step is a rule with a stated mechanism and a test, not a
  number fitted to seeds; `smoke` is checked separately; `goo` is never run.
- *A step helps depth and hurts survival.* Deaths by depth and survival are both logged per step.
- *The Goo fight needs what the screen does not show.* Goo's pump-up is announced in the game log
  (`Goo.java:221-224`, `actors.properties:1699`), which the Observation carries (ADR-0006); its
  animation is not read.

## Mechanics

| Claim | Citation |
|---|---|
| Regeneration: one hit point every 10 turns, none while starving | `actors/buffs/Regeneration.java:44`, `:56` |
| A ration is 300 turns of food | `items/food/Food.java:51`, `actors/buffs/Hunger.java:40` |
| A Step at a cell a character holds is refused with no time spent | `actors/hero/Hero.java:1831-1834`, `:1035-1037` |
| Under vertigo a move goes to a random neighbour, and stays put if that one is blocked | `actors/Char.java:1298-1305` |
| Goo: 100 hit points, pump-up doubles accuracy and triples damage, announced in the log | `actors/mobs/Goo.java:54`, `:67-89`, `:208-227`; `messages/actors/actors.properties:1699` |
| Goo heals on water and seals the floor once awake | `actors/mobs/Goo.java:109-136` |
| A pumped Goo reaches two cells in a clear line | `actors/mobs/Goo.java:141-152` |

## Tuning log

Tuning set: 40 Warriors of `standard`, salts 1000+j. Each row is a full play of the set.

| Step | Change | Median turns | Mean deepest (max) | Deaths by depth 1/2/3/4 | Reached 5 | Goo | Kept |
|---|---|---|---|---|---|---|---|
| g0 | main (story 4.12) | 976 | 2.49 (4) | 6/10/21/2 | 0 | 0 | baseline |
| S1 | dizzy hero stands still; vertigo refusals count; drink at the low-health warning | 1,038 | 2.60 (4) | 6/10/18/6 | 0 | 0 | kept |
| S2 | rest only as far as the food held pays for (lean rests) | 824 | 2.50 (4) | 6/11/20/3 | 0 | 0 | reverted |
| S3 | S1 + drink unknown potions likely to be strength or experience at any health | 1,148 | 2.53 (4) | 8/10/15/7 | 0 | 0 | kept: 22 of 40 died at strength 11, none before |
| S4 | S3 + the bounce breaker; testing cells never a doorway | 1,063 | 2.60 (4) | 7/10/15/8 | 0 | 0 | kept: removes the loops below |
| smoke | S4 on `smoke` against story 4.12 | 982 (was 1,044; mean 1,020, was 967) | 2.56 (4) (was 2.52) | 2/10/10/3 | 0 | 0 | no regression |
| F2 | S4 + the hunger clock; with under 600 turns of food, search only promising spots (at most 4 a floor, not above a boss floor) and no walks to testing cells | 1,054 | 2.70 (5) | 6/8/19/6, 1 on 5 | 1 | 0 | kept: the first Run to reach depth 5; survival flat |
| M | F2 merged with main (story 4.11) | 1,054 | 2.70 (5) | 6/8/19/6, 1 on 5 | 1 | 0 | the new baseline; story 4.11 changes none of these Runs |
| smoke | M on `smoke` against story 4.12 | 1,061 (was 1,044; mean 960, was 967) | 2.56 (4) (was 2.52) | 2/9/12/2 | 0 | 0 | no regression: F2 kept |
| S5 | M + a lone swarm of flies met from a chokepoint, as a group (it splits when hit) | 1,054 | 2.68 (5) | 6/8/20/5, 1 on 5 | 1 | 0 | reverted: no change |
| S6 | M + unknown scrolls read at full health onto the worn armour (a scroll of upgrade upgrades it; any other is identified, as a plain read is); a known scroll of upgrade read onto it; the upgrade window the game chains is confirmed for the worn armour | 1,174 | 3.00 (6) | 8/8/10/5, 8 on 5, 1 on 6 | 9 | 1 | kept: the first Goo kill; one Run ended on the chained window, fixed in the executor after |
| smoke | S6 on `smoke` | 1,182 (M: 1,061; mean 1,161, M: 960) | 2.84 (5) (M: 2.56) | 2/9/8/3, 3 on 5 | 3 | 0 | no regression |
| S6c | S6 re-measured with the executor's frame fix (every Run with two scrolls of upgrade changes) | 1,193 | 3.05 (6) | 8/8/9/5, 9 on 5, 1 on 6 | 10 | 2 | the new baseline; one Run ends on a talent the harness offers at its cap (issue filed) |
| S7 | S6c + hold while a lone enemy closes and the fight is favourable ("first strike") | 1,163 | 3.00 (5) | 7/8/10/8, 7 on 5 | 7 | 3 | reverted: `smoke` fell (median 1,182 to 965, depth 5 reached 3 to 1) |
| G1 | S6c + step out of reach when Goo's pump-up is announced | 1,193 | 3.05 (6) | as S6c | 10 | 2 | identical to S6c: the dodge never fired (see the review notes) |
| R1 | G1 + the review fold-in of `6aa5a3711` (below) | 1,270 | 3.10 (5) | 8/8/7/6, 11 on 5 | 11 | 3 | kept; 4 Runs end `UNKNOWN_WINDOW`, 3 of them on depth 5, on a talent at its cap (#162) |
| T1 | R1 + the Brain offers itself no talent at its cap | 1,270 | 3.13 (5) | 8/8/7/5, 12 on 5 | 12 | 4 | kept: every Run ends in a death |
| G2 | T1 + the dodge takes any Step that gains distance (two Steps clear the pump) | 1,270 | 3.13 (5) | as T1 | 12 | 4 | identical to T1: still never fires; the probe shows the hero cornered in a corridor beside Goo, having retreated |
| G3 | G2 + no retreat from Goo (its floor seals, so a retreat only backs the hero into a corridor) | 1,270 | 3.13 (5) | as T1 | 12 | 3 | reverted: the dodge now fires (140 times in 12 Runs) but Goo kills fall 4 to 3; stepping away also gives up attacks, and Goo heals on water |
| smoke | T1 (`6a62a262f`) on `smoke` | 1,071 (S6: 1,182; mean 1,123, S6: 1,161) | 2.96 (5) (S6: 2.84) | 2/9/7/2, 5 on 5 | 5 | 0 | deeper (depth 5 reached 3 to 5) but shorter: the Runs that reach Goo now die to it sooner |
| G4 | T1 + no retreat from Goo; the dodge only while Goo stands dry; a Step to the drier cell while Goo stands on water ("pull") | 1,270 | 3.13 (5) | as T1 | 12 | 4 | reverted: no change (pull 51 times, dodge 75). The probe: in 4 of 6 depth-5 Runs Goo stood on water on every screen -- its room is mostly water and the hero is pinned in a corridor mouth with no drier cell offered; the hero arrives at strength 11 in a +0 shortsword, holding a hand axe, a shortsword or mail armour it lacks the strength for |
| F1 | S4 + the hunger clock; leave a floor whose food is found when under 450 turns of food ("lean"); frugal rests to 70% and no walks to testing cells under 600 | 946 | 2.68 (4) | 6/9/17/8 | 0 | 0 | reverted: 16 starving at death as before, median survival down |

**g0 failure analysis** (comparison view and the probe of each Run's last screen):
- One Run of 40 looped forever with no time passing: a dizzy hero stepping at a cell an undrawn
  character held, which story 4.12's rule stopped counting as a refusal (647,556 waits).
- 19 of 40 died starving; they held no food. Floors took 235, 393 and 550 turns (median, depths 1-3)
  against about a ration (300 turns) of food placed per floor, and resting to full costs 10 turns a
  hit point.
- Every hero died at its starting strength, 10, in its starting shortsword and cloth armour,
  holding up to five unknown potions and several unread scrolls: the owed strength potions and
  upgrade scrolls were carried, never used.
- Two died at 1 to 4 hit points holding a known potion of healing, having stepped away instead of
  drinking.
- The enemies adjacent at death: swarms of flies (11), gnoll scouts (10), sewer crabs (9), sewer
  snakes (6), marsupial rats (3), giant piranhas (2), slimes (2).

**S3's probe:** 11 of 40 heroes died still at level 1, after 1,300 to 2,200 turns, starving on
depths 1 and 2. Their logs show two Policies undoing each other's Step for hundreds of turns: the
test-item Policy walking to a testing cell in a doorway, where `settled` then refused the test, and
the pick-up Policy walking back toward a ration (1,016 waits in one Run); the fight Policy
approaching an enemy seen only from one cell and the pick-up Policy walking back to a scroll from the
other, where the enemy in view stopped the pick-up (571 waits). S4 withholds the Step back after six
bounces between two cells, whichever Policies are involved, and no longer walks to a testing cell a
test is refused on.

**F1, reverted -- where the starving Runs' turns go.** The 16 Runs that died starving took 1,556
turns on average and changed floor 6 to 10 times, reaching depth 3: fight retreats cost 219 turns a
Run and the flights up the stairs bring rests with them (explore's rest before going back down, 94;
descend's rest, 107), against a food supply of about 300 turns a floor plus the starting ration
(Level.java:224-226, HeroClass.java:108). Leaving earlier did not change that: a floor left early
is a harder floor sooner. The food runs out because fights are lost and fled, so the fight is the
lever behind the food.

**S2, reverted:** starving deaths fell (fights while starving 10 to 5) but heroes went into the next
floor weaker and died there sooner; the comparison view's situations showed the move. Hit points
matter more than food at this depth, so the lever is strength, not rest.

## Review

### The checkpoint `6aa5a3711`, reviewed

Folded in before the next tuning step, each with a test:

- **Fairness.** The executor gives a window one frame's update before asking its button again only
  when the window is the upgrade window, and the class contract says so; the comment now says what
  happened (the scene stepper's frames ran while the hero was busy, and the wait is found before
  another frame). Tests: an upgrade window whose frame does not turn its button on is refused with the
  screen unchanged; the chained path gives the same screens twice from the same seed.
- **A known scroll of upgrade** makes the test-item Policy enter on its own (before, it entered only
  with a testable item or a rest owed, so a known upgrade waited for an unknown item). Tested through
  `Brain.decide`.
- **The chained upgrade window** follows the worn slot, recorded when the read is handed over
  (`Memory.Windows.worn`), not the armour's name, which an upgrade can change (a lifted curse glyph,
  Armor.java:465-475). Tested with a renamed armour and with a read onto an item that is not worn.
- **The hunger clock**: an answer or a dismissal costs no turn; a meal costs three, not four; hunger
  stands still on a locked floor (the "floor is locked" buff; Hunger.java:66); a rest while a potion's
  heal lands is billed at most that heal's turns.
- **Vertigo and roots** are waited out with a Wait, not a Search (a search costs six hunger and a
  search spot); a dizzy hero in fire or gas keeps its Steps.
- **The floor above a boss floor**: the comment now says why (it still places food; the boss floor
  places none and seals), the exemption applies on the main branch only, and the test-item Policy's
  walk rule uses the same exemption.
- **Tests that asserted nothing**: the Larder test checks the promising-only restriction; the gains
  test covers gas and flame at low health; the windows test round-trips the worn flag.
- **The comparison view** counts a missing or headerless log under `NO_LOG` or `UNREADABLE` instead
  of dropping it; tests for the index path and the comparison folder.
- **A gate when identify is likely: not added.** An unknown scroll read onto the worn armour that
  turns out to be identify is spent identifying itself, since the worn armour is already known. That
  loses nothing the Brain uses today -- no Policy reads a known scroll of identify -- while every
  upgrade found this way goes onto the armour. It becomes a cost when a Policy has a use for
  identify, and is noted in `docs/ideas.md`.

Two more found on the way:

- **Issue #162 in the Brain.** Four of 40 Runs of R1 ended `UNKNOWN_WINDOW`, three of them on depth 5,
  on a talent offered at its cap. The Brain now offers itself no talent that holds its most points
  (two in tiers 1 and 2, the tier in 3 and 4; Talent.java:98-110, :438-445). The harness fix (the
  valid set) stays with the issue.
- **Why G1's dodge never fired.** The probe's pump screens showed the hero beside Goo when the
  announcement came: one Step from beside Goo reaches two cells, still within the pump's reach, and
  G1 accepted only a Step beyond it. Goo takes two turns to land the pump (a charge, then the attack;
  Goo.java:180-186), so two Steps are enough: the dodge now takes a Step that gains distance, and the
  next wait's finishes it.
