---
title: 'Story 4.10: The test-unknown-items Policy'
type: 'feature'
created: '2026-09-25'
status: 'review'
baseline_commit: '232afa008 (main with stories 4.1-4.8)'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The Brain carries unidentified potions and scrolls and never learns what they are,
except when the random fallback happens to drink or read one. That draw is blind to the danger
(`SafeTest`, story 4.3, has no caller) and to what the game does next. In the direction checks
since story 4.6, every Run that ended on an `UNKNOWN_WINDOW` did so after the fallback used an item:
- casting the Cleric's holy tome;
- reading an unknown scroll onto another item;
- using a stone of intuition.

Each opens a window the harness does not answer (FR-31, FR-30).

**Approach:** A `test-item` Policy that tries one unidentified potion (drink) or scroll (read) at a
time. It acts only on a calm screen, and only where `SafeTest` says the worst case is survivable.
When a cell a few steps away makes the worst case smaller (water against liquid flame, a door
against toxic gas), it walks there first and tests there. It then carries out the escape
`SafeTest`'s model assumes: out of the gas, into the water.

Identification goes through the game, and the journal's identified list narrows every other
appearance's candidates, as story 4.2's Beliefs already compute. The one gap is a test the game
refuses without saying so on the item. That gap is closed with a per-floor record, so the Policy
never retries it.

The random fallback stops using items while any other Action is offered.

## Boundaries & Constraints

**Always:**
- The item's identity comes only from its shown name, the Beliefs' odds and the journal; never from
  what it is.
- A test only where `SafeTest.of` at the testing cell says safe.
- One Action per wait, always one the screen offers; the brain imports only `api`.
- An unknown scroll is read plainly (`UseItem` READ), never onto another item (see Design, D3).

**Ask First:** a harness change that makes a new window answerable (the upgrade window, the guess
window, the Cleric's spell window). That is story 4.11's, or a harness story's.

**Never:**
- Test during a fight: only on a calm screen, with no Prompt and no enemy in view.
- Read while the screen shows the hero blinded or immune to magic.
- Test an appearance with a single candidate left.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Safe potion | unknown potion over harmless and frost candidates, calm, no enemy | DRINK it | N/A |
| Enemy in view | same, an enemy on screen | the Policy does not enter | N/A |
| Lethal here, water near | liquid flame among the candidates, 20 HP, depth 1, water 2 steps away | Step toward the water; DRINK on it | N/A |
| Lethal everywhere near | liquid flame, 5 HP, no water in reach | nothing | N/A |
| Unknown scroll | scroll of NAUDIZ over upgrade/identify/lullaby | READ, plainly | N/A |
| Blind | same, hero shows "blinded" | nothing | N/A |
| Refused test | READ handed, next screen still holds the scroll unchanged | the appearance is not tested again on this floor | N/A |
| One candidate | appearance whose odds are one identity | not tested | N/A |
| In its own gas | the hero's cell shows ToxicGas, calm | Step toward the nearest cell with no harmful blob | N/A |
| Fallback | calm screen offering Steps and item uses, nothing else acting | a Step, never an item use | only item uses offered: an item use |

</frozen-after-approval>

## Code Map

- `shatterfish/brain/src/main/java/org/shatterfish/brain/TestItem.java`: the new Policy (plan,
  cell choice, escape, trial record).
- `shatterfish/brain/src/main/java/org/shatterfish/brain/SafeTest.java`:
  - `of(candidates, observation, cell)`: the verdict at a cell other than the hero's.
  - A closed door beside the testing cell shortens the toxic-gas worst case.
- `shatterfish/brain/src/main/java/org/shatterfish/brain/Memory.java`: version 6, with five new fields:
  - `trial`: the test handed over at the last wait;
  - `balked`: the appearances a floor refused to test;
  - `walking`: the Steps in a row toward testing cells;
  - `tested`: the wait of the last drink or read;
  - `clouds`: the cells seen showing fire or a harmful gas.
- `shatterfish/brain/src/main/java/org/shatterfish/brain/Beliefs.java`: the fold does three things:
  - it turns a trial the next screen shows unapplied, or a walk that goes on too long, into a balk;
  - it keeps the clouds;
  - it blocks the test-item Policy's own refused Step first.
- `shatterfish/brain/src/main/java/org/shatterfish/brain/Explore.java`: `walkable` leaves out the
  cells showing fire or a harmful gas and the remembered clouds. The escape walks through them.
- `shatterfish/brain/src/main/java/org/shatterfish/brain/Brain.java`:
  - Policy order: answer-prompt, fight, test-item, pick-up, equip, explore, fallback.
  - `handed` records the trial.
- `shatterfish/brain/src/main/java/org/shatterfish/brain/Policies.java`: the fallback draws item uses
  only when nothing else is offered.
- Docs:
  - `docs/rules/identification.md`: a row on what the windows after a use do, and the door and gas rule.
  - `docs/brain-rules.md`: rows 7 (updated) and 35-37.
  - `docs/architecture.md` and `docs/fairness.md`.

## Tasks & Acceptance

Each acceptance criterion names its test.

- **AC1: Tested only where `SafeTest` says survivable, and at the cell that makes the worst case
  smaller.** `TestItemPolicyTest`:
  - `safe_potion_drunk`: a safe potion is drunk.
  - `lethal_here_walks_to_water`: a potion lethal here but survivable on water is walked to water
    and drunk there, several screens through the Brain.
  - `lethal_everywhere_untested`: lethal everywhere in reach, so untested.
  - `door_shortens_gas`: `SafeTest.of` at a cell beside a closed door scores toxic gas lower.
  - Existing `SafeTestWorstCaseTest`.
- **AC2: Worth most early and never in a fight.** `TestItemPolicyTest`:
  - `not_in_a_fight`: an enemy in view keeps it out.
  - `before_explore`: it takes a calm wait before explore does.
  - `most_worth_first`: the appearance with more copies and more candidates goes first.
- **AC3: The outcome narrows the candidates.**
  - `TestItemPolicyTest.candidates_narrow`: after the journal lists the tested identity, the other
    appearances of the family lose it.
  - `TestItemPolicyTest.refused_test_balks`: a test the game refused is recorded, and not repeated
    on the floor, over several screens.
  - `TestItemPolicyTest.single_candidate_skipped`.
- **AC4: No window it cannot close.**
  - `TestItemPolicyTest.scroll_read_plainly`: READ is `UseItem`, never `UseItemOn`.
  - `TestItemPolicyTest.cancel_confirmation_answered_yes`: answer-prompt presses "Yes, I'm positive"
    on the unknown inventory scroll's cancel confirmation. "No, I changed my mind" would reopen the
    bag, which no Action answers.
- **AC5: The fallback leaves items alone.** `PolicyArbitrationTest.fallback_leaves_items` and
  `.fallback_items_when_nothing_else`.
- **AC6: Escape.** `TestItemPolicyTest.escapes_its_gas`, over several screens.
- **AC7: Policy lists and the Rules index.** `ShatterfishRunTest`, `PolicyArbitrationTest`,
  `BrainRulesIndexTest`, `:codex:citations`.
- **AC8: Direction check.** The pull request carries a `smoke` direction check (run by the parent).

## Design Notes

**Goal and constraints.** Learn what the unknown potions and scrolls are without dying to them,
using only what the screen shows. Arbitration is unchanged (the first Policy that enters and ranks
takes the wait). The Memory holds what was seen, not intentions: a trial is read only to interpret
the next screen, as story 4.7's `last` is.

**D1: when to test.** Alternatives:
- (a) Test as soon as the screen is calm.
- (b) Test only in the first N waits after arriving on a floor.
- (c) Score the information against the floor's remaining turns.

Chosen: (a), ranked above explore. The Policy enters only on calm screens, so it never tests during
a fight. Because it outranks explore, it takes the first calm moment after the item is held, which
is as early on the floor as the item allows. (b) would leave items picked up late untested until the
next floor. (c) needs a model of what knowing an identity buys, which the Evaluation does not have
yet (idea filed).

Added after the first direction check, for potions: a potion is drunk only while the hero is down to
four fifths of its hit points or below. The first build drank whenever calm, and it measured worse
than the same build with the Policy switched off (below). The likeliest unknown potion is healing,
and a healing potion drunk at full health is spent for nothing, while every other identity costs the
same hurt or whole. Scrolls are read at any health. A health gate on scrolls (read only above four
fifths, against rage's draw) measured no different within the noise, so it was left out.

**D2: which item first.** Alternatives:
- (a) Inventory order.
- (b) Most copies held times candidates left minus one.
- (c) Entropy of the odds.

Chosen: (b). A test identifies every copy held, and an appearance with more candidates is more
uncertain. It is integer arithmetic, deterministic, with ties broken by inventory order. (c) is
nearly the same order for the Codex's families, at the price of floating-point ties.

**D3: how to read a scroll.** An unknown `InventoryScroll` (remove curse, upgrade, transmutation)
identifies itself and leaves the pack before its item picker opens (`InventoryScroll.java:39-49`).
Alternatives:
- (a) Read onto a target (`UseItemOn`). The executor answers the picker with the target. If the
  scroll proves to be upgrade, the game then opens `WndUpgrade` (`ScrollOfUpgrade.java:60-66`),
  which is not a Prompt the harness recognises (`harness/.../driver/Prompts.java`). The Run ends
  `UNKNOWN_WINDOW`, which is exactly what happened in 4.8's direction check.
- (b) Read plainly (`UseItem` READ). The executor sends the picker away with the back press
  (`ActionExecutor.useItem`). The scroll, identified by use, then asks to confirm the cancel
  (`InventoryScroll.java:137-139`, `:52-80`), an options window enclosed in an `Item`, so a Prompt
  of kind `ITEM`. answer-prompt declines only a label that is exactly "no" and presses the lowest
  other option, "Yes, I'm positive" (`items.properties:1156`). The scroll is consumed, and
  identified.
- (c) Never read an unknown scroll while upgrade is a candidate.

Chosen: (b). It never stalls. It costs the first scroll of upgrade, remove curse or transmutation
read unknown, which is identified and so not wasted as information. (c) would leave most scrolls
untested all game. (a) is the better play once the harness answers `WndUpgrade`, which is filed for
story 4.11. The answer to the confirmation is pinned by a test, because "No, I changed my mind"
reopens the picker and stalls the Run.

**D4: the testing cell.** A potion's harm lands on the drinker's cell. Of the modelled worst cases,
two depend on the cell:
- Liquid flame, which water shortens: 2 hits on water and 3 beside it (`SafeTest`, story 4.3).
- Toxic gas, which a closed door beside the cell shortens. A closed door is solid
  (`Terrain.java:90`) and a blob does not spread into solid cells (`Blob.java:158-164`). A door
  opens when entered and closes behind the last one out (`Level.java:1270`, `Door.java:45-58`,
  `Char.java:1312`).

  The hero steps into the door and on through it. The turns in gas are then the drink, the doorway
  and the first cell beyond. That is three turns, an assumption stated as one, against
  `SafeTest.GAS_TURNS`' ten.

Alternatives:
- (a) Test only where the hero stands.
- (b) Walk to the reachable cell within 8 Steps with the smallest worst case, when it is strictly
  smaller than here.
- (c) Search the whole floor.

Chosen: (b). (c) walks across floors for a potion. (a) never tests liquid flame at depth 1 on 20 HP,
since dry it scores 30.

**D5: escape.** `SafeTest`'s smaller figures assume the hero leaves the fire or the gas. No other
Policy does that: explore would wander, and fight is not entered on a calm screen.

So for `ESCAPE_WAITS` (12) after a drink or read it handed over, `test-item` also enters when:
- the hero's own cell shows a harmful blob the Observation names (Fire, ToxicGas, CorrosiveGas,
  ParalyticGas); or
- the hero shows "burning" off water.

It Steps toward the nearest cell that shows no blob and has none beside it. Through a door, that is
the far side; the cloud's edge is where the gas spreads next. When burning, it steps toward water
first. The hero moves on every such wait, so this cannot stand still.

It escapes only its own clouds, within that window. The first build escaped every cloud, and the
direction check found a Warrior on depth 1 stepping out of a doorway 695 times. The gas filled the
room behind a door; seen from outside, the shut door is solid and hides the gas, so explore stepped
straight back in.

**D8: remembered clouds.** The same run showed pick-up walking back into the hero's own cloud for a
key, and explore walking through it. Alternatives:
- (a) Leave clouds to each Policy.
- (b) Exclude only the cells that show a blob now.
- (c) Remember every cell seen showing one.

(b) alone ping-pongs at a shut door: in the doorway the gas shows, outside it does not. Chosen: (c).
The Memory keeps the clouded cells per floor, and `Explore.walkable`, which every walking Policy
uses, leaves them out. A cell is cleared when it is seen with no blob. A shut door's cell is never
cleared by sight, since it hides the gas behind it.

The first lapse was 20 waits. That made explore walk back and forth every 40 waits toward a room the
level keeps full of gas: the memory lapsed, the room drew explore toward it, and a sight of it
clouded it again. The lapse is now 500 waits, a safety net rather than a rule.

**D6: a test the game refuses.** Reading is refused, with no turn spent, while blind, magic immune,
or under a cursed Unstable Spellbook charge (`Scroll.java:172-192`). Only the first two show as
buffs. Alternatives:
- (a) Check buffs only.
- (b) Record every trial and compare it with the next screen.
- (c) Parse the log line.

Chosen: (b), plus (a) as a guard. When the trial's appearance is still held in the same quantity on
the next screen, the test did not happen, and the appearance is not tried again on that floor. A
refused Step toward a testing cell is balked the same way. That covers the unseen causes without
reading log text, which is locale-bound.

**D7: the fallback.** It drew uniformly among all offered Actions, item uses included. The three
unknown-window endings in 4.8's direction check were all fallback item uses:
- the holy tome's `WndClericSpells` (`HolyTome.java:93`);
- upgrade's `WndUpgrade` (`ScrollOfUpgrade.java:64`);
- intuition's `WndGuess` (`StoneOfIntuition.java:73`).

Alternatives:
- (a) Leave it.
- (b) Draw item uses only when nothing else is offered.
- (c) Exclude only the item uses known to open windows.

Chosen: (b). Item uses are now the Policies' to make deliberately (4.8 equips, 4.10 tests), and a
random use also wastes the item. (c) is a list the next upstream tag breaks. Story 4.9's fallback
change (eat only when nothing else is offered) is a special case of this, and the two unify when
4.9 merges.

**Placement.** answer-prompt, heal, fight, eat, test-item, pick-up, equip, explore, fallback (after the
merge with story 4.9).
- Heal and fight own the screens with an enemy in view, which test-item never enters.
- Eat before test-item: a hungry hero eats first, a starving one cannot rest off a test's cost
  (Regeneration.java:56), and eating takes one calm wait.
- Test-item before pick-up and equip. Pick-up and equip first was the first draft (pick-up gathers
  more copies to identify at once), but it fails the escape: with the hero in its own toxic cloud, a
  pick-up Step toward a heap, or an equip taking a turn, would come before the Step out. A copy
  picked up after a test is simply known.

**Pre-mortem: what would make this fail.**
- The fallback's lost randomness changes play, so the direction check may move. It is recorded,
  not hidden.
- The water walk ping-pongs with explore. Mitigations: the Policy outranks explore, so explore
  never interleaves; the destination is fixed by the screen; and a refused Step is balked.
- The escape oscillates between two gassy cells. The BFS targets the nearest blob-free cell, so
  every Step strictly nears it.
- The confirm-cancel label changes at an upgrade. `cancel_confirmation_answered_yes` pins it, and
  the rules row is re-read at every upgrade.
- A potion of healing is drunk at full health, which wastes it. It happened, so potions are now
  drunk only when hurt (D1).
- The escape ping-pongs with explore at a door. It happened, and D5 and D8 answer it.

## Mechanics (pinned code, v4.0.0)

| Claim | Citation |
|---|---|
| Drinking an unknown potion asks nothing; only a known harmful potion warns | `items/potions/Potion.java:237-259` |
| A drink detaches one potion, costs a turn and applies at the hero's cell | `items/potions/Potion.java:288-306` |
| Reading is refused, no turn spent, while magic immune, blind, or under a cursed spellbook charge | `items/scrolls/Scroll.java:172-192` |
| An unknown inventory scroll identifies itself and detaches, then opens the picker | `items/scrolls/InventoryScroll.java:39-49` |
| Cancelling the picker of a scroll identified by use asks to confirm, yes consumes it and no reopens the picker | `items/scrolls/InventoryScroll.java:52-80`, `:137-139`; `assets/messages/items/items.properties:1155-1157` |
| Upgrade's selection opens `WndUpgrade`; intuition's opens `WndGuess`; the holy tome's cast opens `WndClericSpells` | `items/scrolls/ScrollOfUpgrade.java:60-66`, `items/stones/StoneOfIntuition.java:71-73`, `items/artifacts/HolyTome.java:87-95` |
| A closed door is solid, an open one not; blobs spread only through non-solid cells | `levels/Terrain.java:90-91`, `actors/blobs/Blob.java:158-164` |
| A door opens on entry and shuts when its last occupant leaves | `levels/Level.java:1270`, `levels/features/Door.java:45-58`, `actors/Char.java:1312` |
| Identification shows in the name and the journal's identified list | `items/potions/Potion.java:368-379`, `items/scrolls/Scroll.java:211-241` |
| The blind and magic-immune buffs show as "blinded" and "immune to magic" | `assets/messages/actors/actors.properties:131`, `:305` |
| The burning buff shows as "burning" | `assets/messages/actors/actors.properties:137` |

The liquid-flame, toxic-gas, paralytic-gas and frost worst cases are story 4.3's, cited in
`docs/rules/identification.md`.

## Dev Agent Record

### Tests

- `:api:test` and `:brain:test` pass.
  - `TestItemPolicyTest` has 23 cases, several of them run over several screens through the Brain:
    the walk to water, the balk, the escape, the cloud memory.
  - `PolicyArbitrationTest` has two new fallback cases.
- Rig tests pass, run as one gradle job: `BrainRulesIndexTest`, `ShatterfishRunTest` (its git-history
  check is skipped in a worktree, as before), `StrategyLogTest`, `SafeTestCodexTest`,
  `CodexKnowledgeTest` and `WeightsFileTest`.
- `:codex:citations` reports no findings. `vanilla-src` is a junction to the main checkout's copy,
  not committed.

### Mutation battery

`scratchpad/mutations410.py` plants 17 bugs, each run against `TestItemPolicyTest`,
`PolicyArbitrationTest` and `SafeTestWorstCaseTest`. All 17 are killed:

| # | Where | Mutant |
|---|---|---|
| 1 | `TestItem` | enters without a calm screen |
| 2 | `TestItem` | tests a single candidate |
| 3 | `TestItem` | ignores balks |
| 4 | `TestItem` | reads while blind |
| 5 | `TestItem` | the hurt boundary (`<=` to `<`) |
| 6 | `TestItem` | worth order reversed |
| 7 | `TestItem` | reach unbounded |
| 8 | `TestItem` | escape window widened |
| 9 | `TestItem` | escape to the cloud's edge |
| 10 | `SafeTest` | door ignored |
| 11 | `SafeTest` | the hero's own doorway not a door |
| 12 | `Beliefs` | a refused test missed |
| 13 | `Beliefs` | walk bound widened |
| 14 | `Beliefs` | a shut door seen clear |
| 15 | `Memory` | a Step counts as a test |
| 16 | `Policies` | the fallback draws item uses |
| 17 | `Explore` | clouds walked through |

Mutants 1 and 2 survived the first run: their tests used a hero at full health, which the hurt gate
already kept out. The tests now use 16 of 20.

### Direction check (development)

This is the `smoke` set: 25 triples under fixed salts through `RunOne`, the same method the parent's
direction checks use. The baseline is main with 4.8, taken from the parent's `fx-48c` logs. The
parent runs the check the PR carries.

| | main (4.8) | 4.10, test-item off (ablation) | 4.10 |
|---|---|---|---|
| turns survived, median (mean) | 557 (595) | 533 (632) | 536 (580) |
| deepest floor, mean (max) | 2.12 (3) | 2.08 (3) | **2.16** (3) |
| score, mean | 636.8 | 627.4 | 538.7 |
| endings | 22 deaths, 3 unknown windows | 25 deaths | **25 deaths** |
| test-item share of waits | none | none | 1.6% |

- **Unknown windows.** All three are gone. They were the fallback's item uses, which the fallback no
  longer makes.
- **The ablation.** With test-item off, the fallback change and the cloud memory alone play like
  main.
- **Testing costs score on this branch.** Nothing uses what it learns yet: no Policy drinks a known
  healing potion until story 4.9 merges. Meanwhile each test spends the potion or scroll, and a
  harmful one costs hit points. The first read of a scroll of upgrade is spent on identifying itself
  (D3).
- **What the development runs found and fixed.**
  - A walk that flipped at a door (the doorway's own tile).
  - The escape ping-pong at a gassed room's shut door (D5).
  - Pick-up and explore walking back into the hero's own cloud (D8).
  - Explore swinging toward a permanently gassed room every 40 waits (D8's lapse).
  - Drinking whole (D1).
- **Order of tuning.** The development runs came in that order: calm-only drinking 435; loops fixed
  456; clouds 498; clouds kept 511; potions only when hurt 539. Two variants measured and dropped:
  scrolls gated by health (528), and scrolls not tested at all (566). All are inside the spread one
  Run's divergence makes on 25 triples.

### Deviations

- `Explore.walkable` changed for every walking Policy, to leave out remembered clouds (D8). It is a
  change to stories 4.6, 4.7 and 4.8's walking, justified by the direction check.
- Potions are drunk only when hurt (D1), a rule added after the first measurement, and then only at
  half health with a reserve (review, below).

## Review and the merge with story 4.9

The fairness review found no violation. It noted one citation, `Blob.java:156-158`, which sat two
lines early and is now `:158-164`. The lens review explained the score loss:
- An unknown potion or scroll held counts 30 to the score (Rankings.java:203-211, Potion.java:441-442,
  Scroll.java:278-279), so three tests cost about 90 points of score.
- Nothing in main used what a test identified.
- An inventory scroll read plainly is burned with no effect.
- Scrolls were read at any health.
- Nothing rested after a test.

The target is survival and depth (E4's Goo gate), and a test must pay for itself in play.

**Merge.** Main at `58a541254` (story 4.9) was merged in:
- `Memory` is now version 7 with one field order: 4.8's 24 fields, then `drank` (4.9), then `trial`,
  `balked`, `walking`, `tested`, `clouds` and `refuge` (4.10). There is one symmetric `Bytes` layout,
  and every helper passes every field: `handed`, `avoiding`, `aiming`, `drinking` and `trying`.
- The fallback's rule covers story 4.9's: no item use, eating included, while anything else is offered.
- The Policy order is given under Placement.

**Rule changes (chosen after the review):**
- **Potions.** Drunk only when the knowledge has a use now:
  - the hero is at half its hit points or below on a calm screen;
  - healing holds at least 0.2 of the odds;
  - the worst case leaves at least a quarter of the hero's hit points: a reserve margin, not just
    damage below the hit points.

  Drunk then, a healing potion heals at once, and once known the heal Policy of story 4.9 drinks the
  rest in a fight. An expected-payoff model was considered. It needs a value for knowing healing that
  nothing measures yet, so the thresholds stand in for it (idea filed).
- **Scrolls.** Read only at full health (Lullaby is then harmless, MagicalSleep.java:37-50, and Rage
  is met whole), and only while identify, remove curse, transmutation and upgrade (InventoryScroll's
  subclasses) hold under 0.25 of the scroll's odds. Read plainly, one of those is identified and
  consumed with no effect (InventoryScroll.java:39-50, :137-139). The alternative, never reading
  until story 4.11 answers the upgrade window, would leave scrolls such as magic mapping unread all
  game. The chosen rule reads a scroll exactly when that loss is unlikely.
- **Rest after a test.** Within 50 waits of a drink or read, short of full health and neither hungry
  nor starving, the Policy rests (`Rest(true)`, "rest: after-test").
  - Explore's rest (story 4.7) covers only floors fled by the stairs.
  - A rest inside this Policy keeps the rule minimal and bounded.

**Bugs fixed, each with a test:**
- A cloud on a shut door's cell never cleared, blocking the room behind the door for the memory's
  life. No cloud is now kept on a shut door, which is solid and holds no gas. `CLOUD_WAITS` is 100: a
  potion seeds 1000 units (PotionOfToxicGas.java:49) and every clouded cell loses at least one a turn
  (Blob.java:186). (`clouds_are_remembered`, including a door out of view.)
- The door's three-turn credit assumed an escape through the door. It is now credited only when the
  cell beyond the door is a known walking cell (`SafeTest.refuge`). The drink records that cell as
  `Memory.refuge`, and the escape makes for it first. (`door_without_a_way_through`,
  `escape_to_the_refuge`, `door_shortens_gas`)
- A test could happen while standing in harm. Now no test while in fire or gas, escaping or not.
  (`no_test_in_harm`)
- Answer-prompt pressed option 0 on every item Prompt, which drinks a known harmful potion at its
  confirmation. It now affirms only the scroll's cancel, recognised by its exact text, and declines
  every other ITEM or HARMFUL_POTION Prompt by "No, I changed my mind". Other Prompt kinds are
  unchanged, since story 4.11 generalises them. (`cancel_confirmation_answered_yes`,
  `item_confirmations_declined`)
- A walk balk stopped the appearance for the whole floor. A `Balk` now carries `walk`: a refused or
  overlong walk stops only the walking, and the test where the hero stands is still allowed.
  (`long_walk_balks`, `memory_round_trip`)

**Tests.**
- `:api:test` and `:brain:test` pass. `TestItemPolicyTest` has 30 cases.
- Rig tests pass, each run as its own gradle job: `ShatterfishRunTest` (its git-history check skipped
  in a worktree), `StarvationRegressionTest`, `FoodCodexTest`, `BrainRulesIndexTest`,
  `StrategyLogTest`, `SafeTestCodexTest`, `CodexKnowledgeTest` and `WeightsFileTest`.
- `:codex:citations`: no findings.

**Mutation battery.** `scratchpad/mutations410b.py` plants 30 bugs across the new rules (the reserve,
the healing odds, the half-health boundary, the full-health read, the item-picker odds, the rest and
its bounds, the refuge, the door's way through, the shut-door cloud, the walk balk, the item
confirmations) and the old ones. All 30 are killed.
- Two survived the first run: the single-candidate test, whose last candidate was not healing, and
  the shut-door cloud test, where every cell was in view.
- Both tests were strengthened: the last candidate is now healing, and one door is out of view.

**Direction check** (`smoke`, 25 triples, salts 1000+i through `RunOne`). The baseline is story 4.9
(`fx-49c`, 4.9 at `59c0434a3`, the Brain of main `58a541254`).

| | main (4.9) | 4.10, test-item off | 4.10 | 4.10, potions at two thirds |
|---|---|---|---|---|
| turns survived, median (mean) | 752 (842) | 863 (907) | **863 (935)** | 863 (941) |
| deepest floor, mean (max) | 2.44 (4) | 2.36 (4) | 2.32 (4) | 2.32 (4) |
| score, mean | 847.8 | 928.0 | 924.9 | 925.4 |
| endings | 22 deaths, 3 unknown windows | 25 deaths | 25 deaths | 25 deaths |
| test-item waits (tests) | none | none | 12 (5) | 25 (7) |

- **Survival.** Median turns rise from 752 to 863. The fallback leaving items alone accounts for
  almost all of it: the ablation with test-item off gets the same median.
- **Unknown windows.** All three are gone.
- **Test-item itself.** With the new rules it is roughly neutral: mean turns 935 against 907 off,
  score 925 against 928, depth 2.32 against 2.36, all within one Run's divergence. It fires rarely,
  because a calm screen at half health or below with a likely-healing potion is rare on the smoke
  floors.
- **Depth.** Mean deepest floor dips from 2.44 to 2.32. The runs live longer on the same floors;
  descending is story 4.12's.
- **The variant.** Drinking at two thirds of the hit points measured the same as half, so the
  recommended half stands.

## Verification pass on 5d1cea0d2

The pass confirmed these as correct:
- the Memory v7 codec and every 31-argument call;
- 4.9's `drank`;
- the Policy order;
- the scroll-cancel match (the confirmation is classified ITEM, and its text is `items.properties:1155`);
- the fallback;
- shut-door clouds;
- determinism and parity.

It found four issues, each now fixed with a test.

1. **The escape stopped at the first cell with no gas drawn** (medium-high). The hero then rested or
   tested at the edge of its own spreading cloud (Blob.java:158-186). The gas interrupts a rest
   (Hero.java:1644-1646), and the hero could drink the next unknown potion there. Resting or testing
   in the doorway holds the door open (Door.java:45-58) and voids the door's credit.
   - **Fix:** within `ESCAPE_WAITS` of a test, the escape goes on while the hero's cell or a neighbour
     shows or remembers a cloud. While a cloud is anywhere on the floor, it also goes on until the
     hero stands on the refuge.
   - A test and the after-test rest now need the hero `settled`: cell and neighbours clean, and not in
     a doorway.
   - Tests: `no_rest_or_test_at_the_edge` (several screens), `escape_carries_on_to_the_refuge`,
     `not_in_a_doorway`, and the updated `escapes_its_gas`.
2. **A rest on a locked boss floor never ends** (medium). Regeneration and hunger stop there
   (LockedFloor.java:62-64, Regeneration.java:112-117), and the Run stalls.
   - **Fix:** no after-test rest while the hero shows "floor is locked" (actors.properties:290).
   - Test: `rest_where_it_ends`.
3. **The rest happened inside a region the fight Policy had retreated from** (low).
   - **Fix:** no rest while the hero's cell is in an avoid region.
   - Test: `rest_where_it_ends`.
4. **An ally could hold the door open** (low).
   - **Fix:** no door credit (`SafeTest.refuge` is -1) while an ally is in view.
   - Test: `no_credit_with_an_ally`.

**Verification.**
- `:brain:test` passes, with `TestItemPolicyTest` at 35 cases.
- The eight rig tests pass, each as its own gradle job.
- `:codex:citations` reports no findings.
- The mutation battery on the new lines (`scratchpad/mutations410c.py`) killed 8 of 8.

**Direction check** (`smoke`, salts 1000+i, against `fx-49c`):

| | main (4.9) | 4.10 at 5d1cea0d2 | 4.10 now |
|---|---|---|---|
| turns survived, median (mean) | 752 (842) | 863 (935) | 863 (915) |
| deepest floor, mean (max) | 2.44 (4) | 2.32 (4) | 2.32 (4) |
| score, mean | 847.8 | 924.9 | 917.5 |
| endings | 22 deaths, 3 unknown windows | 25 deaths | 25 deaths |
| test-item waits (tests) | none | 12 (5) | 7 (4) |

The changes touch only the few Runs that test, and both 4.10 columns fall within one Run's divergence.
