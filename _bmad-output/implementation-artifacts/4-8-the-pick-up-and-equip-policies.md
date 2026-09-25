---
title: 'Story 4.8: The pick-up and equip Policies'
type: 'feature'
created: '2026-09-25'
status: 'review'
baseline_commit: '265e5e9f2 (story 4.6, with main at story 4.3)'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The Brain walks past items and never changes its gear. Explore's Steps pick up the
items they happen to cross (a click on a plain heap picks it up on arrival), but nothing goes out of
its way for a strength potion, and a better sword in the pack stays in the pack (FR-31).

**Approach:** Two Policies over the Evaluation of story 4.5, with new weights for what items and gear
are worth:
- `pick-up` goes to the heap whose worth carried most exceeds the turns to reach and take it.
- `equip` puts on a melee weapon or armour the Evaluation prefers to what is worn, after the
  strength it asks and the chance a hidden curse holds, and never one whose worst case under
  `SafeTest` is unsurvivable.

The Codex's knowledge gains the melee weapons and armour with their tier, strength and level-0 mean
roll from the combat table.

## Boundaries & Constraints

**Always:**
- An item's worth comes from its shown name, the Beliefs' odds for an unidentified appearance, and
  the Codex; never from what it is.
- A piece's curse comes only from what the screen shows (`cursedKnown`, `visiblyCursed`), with the
  generator's three in ten for a hidden curse.
- One Action per wait, always one the screen offers.
- `brain` opens no file; the Codex and weights arrive from the rig.

**Ask First:** a feature the Observation does not carry (an item's level before it is known, a heap
under another).

**Never:** equip a piece shown cursed, replace a worn piece shown cursed, or put on a piece whose
worst case `SafeTest` refuses.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Strength potion near | heap "potion of strength" two cells away, calm | a Step toward it | N/A |
| Gold far | heap "gold" across an explored room | not taken | N/A |
| Standing on a heap | the screen offers PickUp | PickUp | N/A |
| Enemy in view | any heap | the Policy does not enter | N/A |
| Better weapon, hidden curse | shortsword in pack, worn shortsword on, strength 12 | EQUIP | N/A |
| Two points short | the same at strength 10 | nothing | N/A |
| Unsurvivable worst case | hidden curse, cursed blast ≥ hp | nothing | N/A |
| Cursed worn | worn piece shown cursed | nothing replaces it | N/A |

</frozen-after-approval>

## Code Map

- `shatterfish/api/.../Codex.java`: `Codex.Gear` (class, name, weapon/armour, tier, strength, mean in
  thousandths); `Knowledge.gear()`, with a four-argument constructor kept for callers without gear.
- `shatterfish/rig/.../CodexKnowledge.java`: `gear(folder)` joins `items.json`'s strength with
  `combat.json`'s level-0 means (a separate method, so story 4.7's additions merge beside it).
- `shatterfish/brain/.../Pickup.java` and `Equip.java`: the Policies.
- `shatterfish/brain/.../Evaluation.java`: features `item`, `gold`, `turn`, `weapon`, `armor`,
  `cursed`, with `identity(className)` for what strength and experience add to the position.
- `shatterfish/brain/.../SafeTest.java`: `gear(...)` candidates and the cursed weapon's and armour's
  worst proc.
- `shatterfish/brain/.../Brain.java`: Policy order answer-prompt, pick-up, equip, explore, fallback.
- `weights/shatterfish.json`: version 2.

## Tasks & Acceptance

**Execution:**
- [x] `Codex.Gear`, `CodexKnowledge.gear`, `CodexKnowledgeTest.the_gear`.
- [x] Evaluation features and the committed weights (version 2).
- [x] `Pickup`, `PickupThresholdTest`.
- [x] `Equip`, `SafeTest.gear`, `EquipPolicyTest`.
- [x] Docs: `docs/rules/identification.md` (one row), `docs/brain-rules.md` (rows 16–19),
  `docs/architecture.md`, `docs/fairness.md`.
- [ ] Smoke direction check (the parent runs it).

**Acceptance Criteria:**
- A potion of strength two cells away is taken; a single gold piece across an explored room is not
  (`PickupThresholdTest.the_ordering`).
- It equips a weapon or armour when the Evaluation prefers it, accounting for the strength
  requirement and the curse risk (`EquipPolicyTest.strength`, `curse_risk`, `armour`).
- It never equips an item whose worst case under `SafeTest` is unsurvivable
  (`EquipPolicyTest.safe_test_refuses`).
- The PR carries a smoke-set direction check.

## Design Notes

Constraints restated: non-negotiable #1 (worth and curse from the screen, the Beliefs and the Codex,
never the item's truth), #8 (every mechanic cited at `v4.0.0`), and the Evaluation as the one scoring
function whose weights are data (story 4.5).

**How to value an item. Alternatives:**
1. A hand table of item values in the Brain. Rejected: it would be a second set of weights outside
   the weights file, invisible to the configuration hash.
2. The Codex's `value` (shop gold). Rejected: it is a shop price, `-1` for every potion and scroll in
   the table (an expression), and says nothing of what an item does for play.
3. **The Evaluation**: a flat `item` weight per stack carried, `gold` per piece, and what an identity
   adds to the position's own features (strength +1, a level). Chosen: it is the one scoring
   function, tunable by the rig, and an unidentified item's worth is the Beliefs' expectation of the
   same.

**How to value gear. Alternatives:**
1. The tier alone. Rejected: tiers overlap (a worn shortsword and a dagger are both tier 1 with
   different rolls).
2. Recompute damage from the tier formula in the Brain. Rejected: a second implementation of a game
   rule (#4).
3. **The combat table's measured level-0 mean** (`combat.json`, `MeleeWeapon.damageRoll` and
   `Hero.drRoll`), cut by 1.5 per point of strength short. Chosen: measured, cited, already in the
   Codex.

**Policy order:** answer-prompt, pick-up, equip, explore, fallback. Both new Policies enter only on a
calm screen, so the fight Policy (story 4.7), which takes the enemy-in-view case, can sit above them
without their competing.

**Weights (version 2, ten-thousandths, untuned, a starting point for the rig):**
- `item` 2000: a stack is worth about 13 turns of detour.
- `gold` 10 a piece.
- `turn` -150.
- `weapon` 2 and `armor` 4 per thousandth of mean roll: armour absorbs every hit it is struck with,
  a weapon rolls once per swing, and absorbed damage is half the typical weapon roll.
- `cursed` -10000: at three in ten, a hidden curse costs 3000, so a shortsword over a worn
  shortsword (+6000) is worth the risk, and a sideways swap is not.

**Pre-mortem:**
- The flat `item` weight values a ration and a scroll of identify alike. Food belongs to the eat
  Policy (story 4.9); for now any stack is worth the same.
- A level the screen shows (`+2`) is ignored: gear is valued at level 0. An upgraded piece is
  undervalued, never overvalued.
- Two pieces of the same name (an identified sword with an enchantment prefix) do not match the
  Codex name and are ignored, which is conservative.
- A heap's title shows only its top item; a heap of several items is valued by that one.
- `Pickup` computes its own breadth-first distances rather than reusing `Explore`'s private search,
  so the two Policies do not couple; both use `Explore.walkable`.

## Review

Fairness reviewer: PASS, no leak. Its two should-fixes are done:
- The curse chance is the conditional one, given that no enchantment or glyph shows: 0.3/0.9 for a
  weapon (Weapon.java:439-447) and 0.3/0.85 for armour (Armor.java:671-679).
- The Parchment Scrap trinket scales the 30% (Weapon.java:441, Armor.java:673). The Brain does not
  scale it yet; the rules row says so and issue #136 tracks it.

Lens review, seven findings; each is fixed and has a test.
1. **A refused pick-up livelocked.** A pick-up the game refuses spends no turn (a dewdrop at full
   health with no waterskin room, a full pack; Hero.java:1162-1188, Dewdrop.java:63-69, :120-122).
   The screen did not change, so PickUp was chosen again forever.
   - The Memory records a heap as refused when the hero stood on it at two waits in a row, the
     first calm, under the same title. This is inferred from the screen alone.
   - Pickup skips refused heaps, and a dewdrop while the hero is at full health.
   - Memory is version 4. Its new fields are `underfoot` (the title of the plain heap under the
     hero at the last wait) and `refused` (depth, branch, cell, title). A 14-argument constructor
     keeps story 4.6's shape (`PickupThresholdTest.refused_heap`, `dewdrop`).
2. **Stationary Pickup and Equip waits counted as searches and as a stuck streak.** Per the
   coordinator, story 4.7 owns the generic fix: record the last Action kind in the Memory, and
   count the streak only after a Step and a searched spot only after a Search. This story keeps its
   own part: Pickup yields when the streak reaches `Explore.STUCK - 1`, and every Step it takes
   brings the hero nearer the heap (`no_wandering`).
3. **An unrecognised worn piece counted as worth zero.** The Mage's staff, a piece whose name shows
   an enchantment or glyph, and a renamed holy weapon all made any recognised piece look better.
   Nothing now replaces a worn piece the Codex does not name (`unknown_or_upgraded_worn`).
4. **The Warrior's seal was lost on an armour swap.** The seal-transfer Prompt (Armor.java:261-283)
   was declined, which left the seal on the armour coming off. Answer-prompt now affirms a Prompt
   titled "Broken Seal" (`PolicyArbitrationTest.the_seal_moves`).
5. **SafeTest understated cursed gear.**
   - A cursed weapon is now scored as a cursed wand's zap, because of Wondrous (Wondrous.java:38-47).
   - A cursed armour is scored as the worse of fire and gas, and it disables. Overgrowth's Firebloom
     and Blindweed are the reason (Overgrowth.java:40-52, Firebloom.java:57).
6. **Pickup could oscillate near an ally.** The ally's cell is walkable, but the screen offers an
   Interact there, not a Step. Now a Step must bring the hero nearer the heap, and a heap with a
   character on it is skipped (`no_wandering`).
7. **A swap costs two turns.** The worn piece comes off first (KindOfWeapon.java:127,
   EquipableItem.java:132-136, Armor.java:246). Swaps are now charged two turns (`swap_costs_two`).

Also fixed:
- A worn piece whose level shows an upgrade is never swapped off: the Codex's means are level 0's
  (`unknown_or_upgraded_worn`).
- Citation corrections: Item.java:130, and Hero.java:1974-1977.

After the review:
- Tests pass: `:api:test`, `:brain:test`, and in the rig `BrainRulesIndexTest`, `SafeTestCodexTest`,
  `WeightsFileTest`, `CodexKnowledgeTest`, `ShatterfishRunTest` and `StrategyLogTest`.
- The battery on the new code killed 14 of 14 mutants:
  - refusal never recorded;
  - refusal recorded without a calm screen;
  - a refused heap taken;
  - a dewdrop at full health;
  - an occupied heap;
  - a sideways Step;
  - never yielding;
  - an unknown worn piece replaced;
  - an upgraded worn piece replaced;
  - a swap for one turn;
  - armour priced at the weapon's chance;
  - a cursed weapon not disabling;
  - a cursed weapon as the blast alone;
  - the seal declined.

Verification pass on `147489e86`: two more findings, each fixed and tested.
1. **Pickup's own refused Step looped.** When the game refuses a Step (a forge drawn as floor:
   `getCloser` fails and the hero is made ready without spending time, Hero.java:1025-1037), Pickup
   sat out one wait and then took the same Step again. Only Explore's step cell was ever blocked.
   - Pickup now yields whenever the streak is at least `Explore.STUCK - 1`, until the hero moves.
     The one exception is standing on the heap it is going for, where the only Action is the
     pick-up and a refusal is caught by the refused-heap rule.
   - When the streak reaches `STUCK - 1` after a calm screen, the cell of Pickup's own Step from the
     last screen is added to `blocked` (`PickupThresholdTest.refused_step`).
   - The guard counts the streak however it is counted. Under story 4.7's rule (the streak counts
     only after a handed Step on a calm screen), a refused Pickup Step still counts.
2. **False refusals.** Two cases recorded a heap as refused when it was not:
   - A heap of several like items shows the next one's title after a successful pick-up (the
     Observer shows the top item only).
   - The hero can stand on a heap for two calm waits without Pickup trying it.
   - A refusal is now recorded only when the pack is unchanged between the two waits (items listed,
     total quantity, gold), and only when the heap was Pickup's target on the earlier screen
     (`refusal_false_positives`).

New Memory fields (version 4, after `refused`):
- `Pack pack`, where `Pack(int items, int quantity, int gold)`: the pack on the last screen, with
  `Pack.NONE` = (-1, -1, -1) before any.
- `Aim aim`, where `Aim(int target, int step)`: where Pickup's plan went on the last screen, the
  heap's cell and its Step's cell, -1 for none, `Aim.NONE` = (-1, -1).
- `Brain.update` sets `aim` after the fold with `Pickup.aim(...)`, a pure function of the screen and
  the memory: what the Policy computes, not what was handed over.
- Bytes: after the refused list, `pack.items`, `pack.quantity`, `pack.gold`, `aim.target`,
  `aim.step`, as five integers.

After the verification fixes:
- Tests pass: `:brain:test`, and in the rig `ShatterfishRunTest`, `StrategyLogTest`,
  `BrainRulesIndexTest` and `WeightsFileTest`.
- The battery on the new code killed 9 of 9 mutants: yielding one wait only, never yielding,
  yielding on the target heap too, Pickup's own Step never blocked, the pack ignored, the aim
  ignored, the aim never recorded, the aim's Step lost, and gold left out of the pack.

### Merge with stories 4.6 and 4.7 (main at 644393fb3)

- **Policy order:** answer-prompt, fight, pick-up, equip, explore, fallback. Pick-up and equip
  enter only on a calm screen, so they stay out while an enemy is in view, which is fight's to take.
- **Memory is version 5**, story 4.7's 20 fields followed by this story's four: `underfoot`,
  `refused`, `pack`, `aim`.
  - One Bytes layout, with the reader and writer symmetric. After 4.7's `avoid` list come
    `text(underfoot)`, then the refused count with each entry as depth, branch, cell, `text(title)`,
    then `pack.items`, `pack.quantity`, `pack.gold`, then `aim.target` and `aim.step`.
  - Constructors chain from 14 arguments (4.6's shape) to 20 (4.7's shape) to 24.
  - 4.7's `handed` and `avoiding` carry the new fields through.
- **What 4.7's rule made redundant, and what stays.** 4.7 counts the streak only after a handed Step
  on a calm screen, and a searched spot only after a Search. That replaces this story's earlier
  inference from standing still. This story keeps:
  - the refused-heap rule (a calm screen, standing still, the same title, an unchanged pack, and
    Pickup's aim on the heap);
  - Pickup yielding while `streak >= STUCK - 1`.
- **Blocking a refused Step.** When the streak reaches `STUCK - 1` on a calm screen, the blocked
  cell is Pickup's own Step from its aim on the screen before, if it planned one. Otherwise it is
  explore's Step on this screen, as in 4.7. The reason: on a calm screen Pickup stands above explore,
  so when its plan was a Step, that was the Step refused.
- **One `Codex.Gear` record:** 4.7's `(name, level, min, max, meanPerMille)`, extended with `tier`
  and `strength` (the level-0 strength from the item table; both 0 where the table states none).
  - 4.7's 5-argument constructor defaults them to 0.
  - The reader fills them for every measured level.
  - `Knowledge` is 4.7's, with `threats`, `weapons`, `armours` and `immovable`. The 4-argument
    constructor and `Knowledge.of` still work. This story's `gear` field and its separate Gear
    record are gone.
  - Equip reads the kind from the list a piece is measured in, and uses `Equip.Piece` for level 0.
- **One name matcher:** `Fight.measured(shown, gear)`, extracted from `Fight.worn`. Equip counts only
  a piece whose shown name is its measured name. An enchanted name, the mage's staff and a renamed
  holy weapon are neither put on nor replaced (`EquipPolicyTest.entering`).
- **Docs:** this story's Rules index rows are now 30–34 (they were 16–20), following 4.7's 16–29.
  The architecture row keeps every story's sentence, with 4.8's after 4.7's.
- **Tests:** `:api:test`, `:brain:test`, and in the rig `CodexKnowledgeTest`, `BrainRulesIndexTest`,
  `SafeTestCodexTest`, `WeightsFileTest`, `ShatterfishRunTest` and `StrategyLogTest` pass;
  `:codex:citations` has no findings.

### Merge review of 7d9367cda

The smoke direction check against main:
- score 405 → 637;
- median turns 422 → 557;
- deepest floor 2.16 → 2.12;
- pick-up takes 14% of waits, the fallback 0.6%.

Three low-severity findings, each fixed and tested:
1. **A heap was marked refused after a turn the Brain did not take.** The refused-heap rule now also
   requires that the last Action handed over was a pick-up (`memory.last()` is `PickUp`, the kind
   story 4.7 records). A human's Wait or Search, or a wait with nothing handed over, refuses nothing
   (`refused_heap`, `refusal_false_positives`).
2. **A passive enemy in view cost one more turn than counted.** A passive statue or gnoll exile is
   scenery to `calm`, but the game counts it among the hero's visible enemies, so the arriving Step
   does not pick up (Hero.java:1974-1977) and a PickUp wait follows. The cost now counts that turn
   (`Pickup.passiveInView`, `passive_in_view`), and the class comment says so.
3. **A piece with a shown negative level was valued at +0.** Equip now skips a candidate whose level
   is known and below zero (`EquipPolicyTest.negative_level`).

## Dev Notes

- Tests: `:api:test`, `:brain:test` (`PickupThresholdTest` 5, `EquipPolicyTest` 5), rig
  `CodexKnowledgeTest`, `WeightsFileTest`, `BrainRulesIndexTest`, `SafeTestCodexTest`,
  `ShatterfishRunTest` and `StrategyLogTest` pass.
- Mutation battery: 13 of 13 killed (turn cost ignored, gold as a plain item, gold quantity ignored,
  Beliefs ignored, entering with an enemy in view, a chest taken, the strength penalty off, the curse
  risk off, SafeTest ignored, a piece shown cursed worn, a worn cursed piece replaced, strength adding
  nothing, a cursed weapon harmless).
