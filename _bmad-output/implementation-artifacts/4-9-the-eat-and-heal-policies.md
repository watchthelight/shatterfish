---
title: 'Story 4.9: The eat and heal Policies'
type: 'feature'
created: '2026-09-25'
status: 'done'
baseline_commit: '3e3fea87d'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** With stories 4.6 and 4.7 the Brain explores and fights, but it never eats and never
drinks. A hero that lives long enough grows hungry, then starves, and starving turns off
regeneration and costs hit points every turn. A hero holding a potion of healing still dies with it
in the pack. Both are losses to bookkeeping, not to the dungeon (FR-31).

**Approach:** Two Policies.
- `eat`: when the hunger icon shows hungry or starving and food is held, eat the food that wastes
  none of its energy at that state. Never eat at no icon.
- `heal`: when an identified potion of healing is held and the hero's hit points are at or below
  what the enemies in view are expected to take before the fight is over, drink it. The threshold
  comes from story 4.7's threat estimate, not from a constant.

`heal` stands above `fight`, so it can preempt a fight the hero is expected to lose. `eat` stands
below `fight` and above `explore`, and acts only on calm screens.

## Boundaries & Constraints

**Always:**
- One Action per Input wait, always one the screen offers.
- Hunger is read only from the hunger icon's three states (`HeroSection.hunger`). Food and potions
  are read only by the names and kinds the inventory shows.
- A potion counts as a potion of healing only when the screen shows it identified by that name.
- Every mechanics claim has a row in `docs/brain-rules.md` resting on a cited Rule.

**Ask First:** anything that needs the exact hunger value, which the game never shows.

**Never:**
- Eating while the hunger icon shows nothing.
- Drinking an unidentified potion to heal (testing unknown items is story 4.10's).
- Reading game code from `brain`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Fed, food held | no hunger icon, a ration held | `eat` stands aside | N/A |
| Hungry, ration held | hungry icon, calm screen | eat the ration | N/A |
| Hungry, only a pasty | hungry icon, pasty held | wait for starving; explore goes on | N/A |
| Starving, pasty and ration | starving icon | eat the pasty (no waste at 450) | N/A |
| Starving, only mystery meat | starving icon | eat the mystery meat | N/A |
| Hungry, enemy in view | hungry icon, rat in view | `fight` takes the wait; `eat` stands aside | N/A |
| Raw blandfruit held | hungry icon | never eaten (the game refuses it) | N/A |
| Low HP, rat beside, healing held | hp at or under the threat | drink the potion of healing | N/A |
| Full HP, brute beside, healing held | hp above the threat | `fight` takes the wait | N/A |
| Just drank | the potion was handed over within the last waits | no second potion | N/A |
| Unknown potion held | an appearance, not identified | never drunk to heal | N/A |

</frozen-after-approval>

## Code Map

- `shatterfish/brain/.../Eat.java`: the Policy and its food table.
- `shatterfish/brain/.../Heal.java`: the Policy and its threshold.
- `shatterfish/brain/.../Brain.java`: the Policy order
  `answer-prompt, heal, fight, eat, pick-up, equip, explore, fallback` (after merging story 4.8); `handed` records a drink; `foods()` and
  `uneaten()` for the Codex drift test.
- `shatterfish/brain/.../Memory.java`: `drank`, the wait at which the heal Policy last handed over
  a drink (VERSION 6 after merging story 4.8), with story 4.8's and story 4.7's constructors kept.
- `shatterfish/brain/.../Beliefs.java`: the fold carries `drank` over.
- `shatterfish/brain/.../Policies.java`: the fallback never draws an eat Action.
- `shatterfish/rig/.../StarvationRegressionTest.java`: real `smoke` Runs.
- `shatterfish/rig/.../FoodCodexTest.java`: the food table against the Codex's item and string tables.
- `docs/rules/buffs.md`: the hunger row re-read at v4.0.0; new rows for food and for the potion of healing.
- `docs/brain-rules.md`: rows 35 to 41 (after story 4.8's 30 to 34), and the fallback's note.
- `docs/architecture.md`, `docs/fairness.md`, `docs/rules/index.md`, `docs/ideas.md`.

## Tasks & Acceptance

**Execution:**
- [x] `Eat` with its food table, and `EatPolicyTest`.
- [x] `Heal` with its threshold, `Memory.drank`, and `HealPolicyTest`.
- [x] The Policy order, `PolicyArbitrationTest`, `ShatterfishRunTest`.
- [x] `StarvationRegressionTest` and `FoodCodexTest`.
- [x] Rules rows and the Brain's Rules index.
- [ ] Smoke direction check (the parent's).

**Acceptance Criteria:**
- The bot eats when the hunger icon reaches hungry and food is held (`EatPolicyTest.hungry_eats`).
- It never wastes food at full satiety (`EatPolicyTest.fed_never_eats`), nor eats a food that would
  overflow the hunger it has (`EatPolicyTest.no_waste_when_hungry`, `starving_prefers_the_largest_that_fits`).
- It drinks a known potion of healing when its hit points fall below a threshold derived from the
  visible threat (`HealPolicyTest.drinks_under_threat`, `holds_when_threat_is_small`,
  `threshold_follows_the_threat`), and not a second one while the first is still healing
  (`HealPolicyTest.one_potion_at_a_time`).
- `StarvationRegressionTest` asserts that a Run with food available never ends with cause starvation.
- The PR carries a smoke-set direction check.

## Design Notes

**Constraints restated:**
- Non-negotiable #1. The hunger value is never shown (docs/rules/buffs.md, "The exact hunger value
  is never shown"), so the Policy reasons from the icon's three states. An unidentified potion's
  identity is not read. Food is known by the name the inventory shows.
- Non-negotiable #8. Every food energy, the eat time and the potion's effect are cited Rules.

**When to eat:**
1. Eat as soon as any food is held. Rejected: at no icon the hunger may be anywhere from 0 to 299,
   and every point of energy beyond the hunger is lost (Hunger.java:146-148 clamps at 0).
2. Eat only when starving. Rejected: 150 turns of hungry pass first, a starving hero does not
   regenerate (Regeneration.java:56), and story 4.7's rest before descending is skipped while hungry.
3. **Eat at the hungry icon a food whose energy is at most 300, and at the starving icon the food
   that wastes least, the largest first.** Chosen. The hungry icon means the hunger is at least 300
   (Hunger.java:178-186), so a food of 300 or less wastes nothing. The starving icon means exactly
   450 (Hunger.java:96-102 caps it), so a food of 450 or less wastes nothing. A pasty held while
   hungry waits for starving rather than lose up to 150.

**Which food is known:**
1. A new Codex table of food energies. Rejected for this story: the generator would need a new
   extractor for one field of fourteen classes, and the Codex's `items.json` already names every food.
2. Eat anything whose kind is food. Rejected: a raw blandfruit refuses to be eaten and spends no
   time (Blandfruit.java:106-110), which would loop, and a blandfruit infused with a potion applies
   that potion (Blandfruit.java:115-118), which can be a harmful one.
3. **A hand-written table in `Eat`, each energy cited, held against the Codex by `FoodCodexTest`.**
   Chosen. Every Codex food with an eat action is either in the table or named as not eaten, and
   every name in the table is a Codex food or a pasty's holiday name. A renamed or new food fails the
   test at an upgrade.

**The heal threshold:**
1. A constant share of maximum health. Rejected by the acceptance criterion.
2. A search over the next turns. Rejected: that is E6's tactical search.
3. **The fight Policy's estimate: the damage the engaging enemies are expected to deal until the
   fight is over, and never less than their worst single turn.** Chosen. With `H` the turns the hero
   expects to need to kill the enemy it kills soonest, between 1 and 10, the enemies that threaten
   are the awake ones (no sleep icon) that can reach the hero within `1 + H` steps over the cells it
   may walk on; of those, `k` can engage the hero's cell at once, `perTurn` is the sum of the `k`
   largest expected damages per turn (`Fight.enemyDamage`) and `worst` the sum of the `k` largest
   damage maxima less the hero's armour minimum: `danger = max(worst, ceil(perTurn × H))`. The
   Policy drinks when `hp ≤ danger`, the hero is missing at least a quarter of the potion's heal
   (the first turn's heal is a quarter of what is left, Healing.java:76-79), the fight is favourable
   or the fight Policy has no retreat, and the last drink's heal would be spent. With no enemy
   threatening the danger is 0 and it never drinks.

**Where the Policies stand:**
- `heal` above `fight`: when the hero is expected to die before the fight is over, drinking is
  better than the fight's own best move, and the fight Policy has no drink among its options.
- `eat` below `fight`: it enters only on calm screens, which are the screens `fight` stands aside
  on, so the order between them does not matter; eating takes three turns (Food.java:47, :87), which
  are free hits for an enemy in view. Above `explore`, so a hungry hero eats before it walks on.

**Pre-mortem:**
- *Starving with enemies always in view.* `eat` never acts then. Starving costs `HT/1000` a turn
  (Hunger.java:80-85), so a hero loses one hit point every fifty turns at 20 HT: the fight ends
  long before that matters.
- *A potion of healing drunk too early, or too late.* The margin is the expected damage over the
  fight, which is an estimate; the worst single turn is the floor under it. Heavy enemies the Codex
  has no figures for take story 4.7's pessimistic figures, which only makes the Brain drink sooner.
- *Two potions in a row.* The heal takes several turns to land and the buff has no icon
  (Buff.java:94-96: the base `icon()` is none, and `Healing` does not override it), so the screen
  does not say a heal is running. The Memory records the wait of the last drink the Brain handed over.
- *Eating refused.* A raw blandfruit is never in the table. Any other refusal is caught by the
  harness's refusal cap.
- *Hunger never reached on `smoke`.* The Runs die around turn 400 to 500, so many never grow
  hungry. `StarvationRegressionTest` also checks every wait that shows a hungry or starving hero on a
  calm screen with food held, so the check has teeth on the Runs that do.

## Review

**Implementation notes:**
- **Policy order:** `answer-prompt, heal, fight, eat, explore, fallback`.
  - `heal` sits above `fight`, so a drink can preempt a fight the hero is expected to lose.
  - `eat` enters only on calm screens, the screens `fight` stands aside on. It sits above `explore`,
    so a hungry hero eats before it walks on.
- **Threshold formula:** `danger = max(worst, ceil(perTurn * H))`, as the Design Notes define it.
  - The Policy drinks when all three hold:
    - `hp <= danger`;
    - the hero is missing at least `firstTurn(HT) = max(1, round((int)(0.8 * HT + 14) / 4))`;
    - no drink was handed over in the last `Heal.HEALING = 5` waits (since the review:
      `Heal.healingTurns(HT)` waits, 11 at 20 HT).
  - With no enemy in view the danger is 0.
- **Memory:**
  - `VERSION` 5, with one new component after `avoid`: `long drank`, -1 until a drink.
  - `Bytes`: `number(drank)` after the avoid regions.
  - `Memory.drinking()` sets `drank` to this memory's `waits`. `Brain.handed` calls it when the Decision's
    Policy is `heal`. `Beliefs.fold` carries it over.
  - A twenty-argument constructor, story 4.7's shape, defaults it to -1.
- **Weights:** unchanged. No Evaluation feature was added.
- **Deviation found while testing, the fallback:**
  - On a fed screen with nothing to explore, the uniform fallback drew "eat" among its Actions, which
    wastes food at full satiety: `EatPolicyTest.fed_never_eats` caught it as an alternative.
  - The fallback now leaves eating to the eat Policy, and draws an eat Action only when nothing else is
    offered, so a Run is never ended for want of an Action.
  - This moves the fallback's draws on any screen that offers food.
- **`vanilla-src`:** a junction to the main checkout's copy, so the codex module's `DocsCitationTest`
  can run in the worktree. It is git-ignored and never committed.

**Tests run** (one gradle job at a time):
- `:brain:test`: all pass, with the new `EatPolicyTest` (10 cases) and `HealPolicyTest` (8 cases).
- Rig:
  - `FoodCodexTest`, `BrainRulesIndexTest` and `StarvationRegressionTest` pass. The last plays the 25
    `smoke` triples in-process under salts 1000 + i, in 22 s.
  - `ShatterfishRunTest` passes, with its git-history check skipped in the worktree as before.
  - `StrategyLogTest`, `CodexKnowledgeTest`, `SafeTestCodexTest` and `WeightsFileTest` pass.
- `:codex:citations`: no findings. `DocsCitationTest` passes.
- The full `./gradlew build` is CI's.

**Mutation battery:** 17 bugs planted, 17 killed, each against `:brain:test` unless noted.

| Mutant | Killed by |
|---|---|
| `Eat.enters` ignores the icon | `EatPolicyTest.the_icon_gates_both` |
| `Eat.ranked` ignores the icon | `EatPolicyTest.the_icon_gates_both` |
| Hungry takes foods up to 450 | `no_waste_when_hungry` |
| Mystery meat not ranked last | `mystery_meat_last` |
| Waste not counted at starving | `starving_prefers_the_largest_that_fits` |
| Any item kind counts as food | `never_unknown_food` |
| `eat` above `fight` | `never_under_attack` |
| The fallback eats | `fed_never_eats` |
| Heal margin `danger + 10` | `HealPolicyTest.holds_when_threat_is_small` |
| No first-turn check | `not_while_nearly_full` |
| No wait after a drink | `one_potion_at_a_time` |
| One enemy always engages | `threshold_follows_the_threat` |
| Any potion heals | `unknown_potion` |
| `Brain.handed` does not record the drink | `one_potion_at_a_time`, `the_memory` |
| The fold forgets the drink | `the_memory` |
| The codec does not read the drink | the Memory round-trip tests |
| `Eat.enters` always false (rig) | `StarvationRegressionTest`, which failed on QAI-OCF-LGF, the Warrior at salt 1000: hungry, calm, food held, and the Brain stepped on |

The first run left the two icon mutants alive: each check hid the other. `the_icon_gates_both` now
tests each directly.

**Deferred** (in `docs/ideas.md`):
- a Codex food table;
- healing by deduction from `Beliefs`;
- healing from poison or bleeding with nothing in view;
- cooking.

**Heads-up for merging:** story 4.8 also changes `Memory`, and it will reach main first. Union both
stories' fields into one version then, keeping `drank` and its codec position after story 4.8's fields.
The Policy order becomes `answer-prompt, heal, fight, eat, pick-up, equip, explore, fallback`, or with
pick-up above eat. Neither is settled by this story.

**Review round** (the parent's direction check against main at story 4.7: median turns 422 to 575,
mean score 405 to 488, mean deepest 2.16 to 2.20; eat took 0.2% of waits, and heal never fired,
because no `smoke` Run held an identified potion of healing):
- **Fairness reviewer:** no violation. Two wording fixes under #8:
  - (a) The heal is not invisible. It has no buff icon, but the game draws a floating heal number
    each turn and the sprite's healing state (Healing.java:61, :107-111); neither is in the
    Observation. `Heal`, `Brain.handed` and `Memory.drank` now say so, and `docs/ideas.md` has a
    line on exposing it.
  - (b) The per-turn quarter is `Healing.java:76-79` (with the tick at :62), re-checked.
- **Lens review, all fixed with tests:**
  1. The danger counted every enemy in view, including sleeping, distant and unreachable ones.
     - Now only awake enemies count, and only if they can reach the hero within `1 + H` steps over
       the walkable cells (`Heal.steps`).
     - Cited by a new Tier-1 combat Rule re-read at v4.0.0: a sleeping mob never attacks, and shows
       the sleep icon (Mob.java:1182-1240, :1248-1261; MobSprite.java:36-40; CharSprite.java:636-641).
     - Test: `HealPolicyTest.only_what_threatens`.
  2. Against an enemy the hero cannot kill, the danger was large and heal pre-empted the fight
     Policy's retreat every few waits until the potions were gone.
     - Now, when the fight is not favourable and `Fight.retreat` has a move, the retreat takes the
       wait. The hero drinks when cornered, or when the fight is favourable and the hit points are at
       the danger.
     - Tests: `retreat_before_drinking`, and `retreats_rather_than_drinks` over several waits.
  3. A starving hero with an enemy in view that never comes (immovable, unreachable) never ate: the
     eat Policy needed a calm screen, and the fallback never eats.
     - Now eat enters while starving with no Prompt open and no enemy beside the hero or offered as
       an Attack (`Eat.pressed`), on the waits the fight Policy stands aside.
     - Tests: `EatPolicyTest.starving_with_an_enemy_far`, and `starving_before_an_immovable_enemy`
       over several waits: the fight Policy holds four waits, then the hero eats.
     - `StarvationRegressionTest` adds a starving-with-food streak bound of 200 waits, whatever is
       in view.
  4. `StarvationRegressionTest`'s ending check read the screen before the last Action.
     - It now skips a Run whose last Action was eating (a hero may die mid-meal), and covers screens
       with enemies in view that are not beside the hero.
     - Every meal is also checked for waste: at hungry a food of at most 300 energy and not mystery
       meat; at starving no more waste than another food held.
  - Minor: the cooldown was 5 waits, but a second drink replaces what is left of the first
    (Healing.java:98), and a heal of 30 lands over 11 turns. The cooldown is now
    `Heal.healingTurns(HT)`, derived from the formula (`HealPolicyTest.healing_turns`,
    `one_potion_at_a_time`).
- **Tests:**
  - `:brain:test` passes: `EatPolicyTest` 12 cases, `HealPolicyTest` 12.
  - `StarvationRegressionTest` passes. Across the 25 `smoke` Runs it saw 22 calm hungry screens
    with food and 5 starving screens with food.
- **Mutation battery on the fixes:** 8 of 8 killed.
  - The mutants:
    - sleeping enemies threaten;
    - reach ignored;
    - unreachable enemies counted;
    - drinking over an open retreat;
    - a five-wait cooldown;
    - half healed per turn;
    - starving eats only when calm;
    - an adjacent enemy does not press.
  - The last one first survived, because an adjacent enemy is always offered as an Attack.
    `Eat.pressed` is now tested with the Attack removed from the offered set.

**Merged with main at story 4.8** (`232afa008`, PR #138):
- **Memory VERSION 6**, one record of 25 components:
  - `waits, deepest, facts, found, held, known, labels, pending, monsters, at, streak, calm, dwelt, blocked,
    last, holds, near, before, flights, avoid, underfoot, refused, pack, aim, drank`.
  - `drank` is appended after story 4.8's `aim`.
  - `Bytes`: story 4.8's layout, then `number(drank)`. The reader matches field for field.
  - Every helper passes every field: `handed`, `avoiding`, `aiming`, `drinking`.
  - Story 4.8's 24-argument shape defaults `drank` to -1, and chains to story 4.7's and 4.6's shapes.
  - `Beliefs.fold` carries `memory.drank()` through both of its constructions.
- **Policy order:** `answer-prompt, heal, fight, eat, pick-up, equip, explore, fallback`. `eat` stands
  above `pick-up`:
  - A hungry hero eats the food it already holds before walking to a heap. The meal is certain; the
    heap is a walk, and its item may not be food.
  - Story 4.8's refused-heap and stuck rules key on the Action handed over (a pick-up, or a Step on a
    calm screen), so a meal in between counts as neither.
  - `PolicyArbitrationTest` and `ShatterfishRunTest` list the order, and a post-merge mutant putting
    `eat` below `pick-up` is killed.
- **Docs:**
  - `docs/brain-rules.md`: story 4.8's rows 30 to 34 stand, and this story's follow as 35 to 41. Row 37
    now says eating waits for a calm screen only while hungry.
  - `docs/fairness.md` keeps both stories' rows.
  - `docs/architecture.md` appends the 4.9 sentence after the 4.8 one.
- **Tests after the merge** (one gradle job at a time):
  - `:api:test` passes, and `:brain:test` passes (154 tests).
  - Rig tests pass: `ShatterfishRunTest` (its git-history check skipped in the worktree),
    `StarvationRegressionTest`, `FoodCodexTest`, `BrainRulesIndexTest`, `StrategyLogTest`,
    `CodexKnowledgeTest`, `SafeTestCodexTest` and `WeightsFileTest`.
  - `StarvationRegressionTest` saw 38 calm hungry screens with food and 5 starving screens with food.
  - `:codex:citations`: no findings. `DocsCitationTest` passes.
- **Post-merge mutants:** 5 of 5 killed, run against `:brain:test`:
  - the reader drops the drink;
  - the writer drops the drink;
  - the fold forgets the drink;
  - `aiming` drops the drink;
  - eat below pick-up.

**Verification round** (the parent's direction check of `0f7c5e7c6` against main at story 4.8, on
`smoke`: median turns 557 to 752, mean deepest 2.12 to 2.44 with a first depth 4, mean score 637 to
847, 22 deaths and 3 unknown windows as before). The pass confirmed the retreat gate, the redrink
floor, the Memory v6 codec and helpers, story 4.8's rules with eat between, parity and determinism.
Six findings, all fixed:
1. **The reach filter dropped enemies that shoot or fly.**
   - The gnoll shaman, DM-100 and dwarf warlock cast bolts, the evil eye beams, the scorpio (and the
     acidic scorpio) shoots, and the vampire bat flies.
   - `Heal.AT_RANGE` names them. Each is cited, and a new Tier-1 combat Rule covers them. They count
     as threatening whatever the steps on foot.
   - Test: `HealPolicyTest.shooters_threaten_from_afar`. A cornered hero shot by an eye behind a wall
     drinks, and an eye asleep threatens nothing.
2. **`StarvationRegressionTest`'s death check could fail on correct play**, for example a starving hero
   killed while retreating, or by a shot or a trap.
   - It now fires only when the last screen was calm.
   - The 200-wait streak bound covers the other screens.
3. **A starving hero started a meal with an awake enemy two steps off.**
   - `Eat.pressed` now also counts an awake enemy within `Eat.MEAL_REACH = 3` steps (`Heal.steps`), and
     an awake one that shoots.
   - Starving costs only `HT/1000` a turn, so waiting is nearly free.
   - Test: `EatPolicyTest.what_presses`. It covers three steps off; asleep; four off; an eye four off;
     no way round a wall.
4. **Attacks on passive enemies counted as pressure.**
   - Now only an Attack on one of `Fight.enemies` counts. Test: `what_presses`, with a passive statue
     beside the hero.
5. **The cooldown blocked a needed second drink.**
   - A redrink takes the larger of what is left and a whole potion (Healing.java:97-98).
   - The Policy may drink again when `Heal.remaining` is under `REDRINK_SHARE_PER_MILLE` = 250
     (a quarter of a potion). That is an assumption, stated. It is 5 waits at 20 HT, where 7 of 30 is
     left, and the hit points must still be at the danger.
   - Waits count as turns, which overstates what is left and never understates it.
   - Tests: `healing_turns` and `one_potion_at_a_time`.
6. **Vial of Blood is not modelled** (Healing.java:80-82, :91-93). The `Heal` javadoc and
   `docs/ideas.md` say so. So does a line on tracing shooters' lines of fire.

**Docs:** `docs/rules/combat.md` gains the "Some mobs attack from where they stand" row (Tier 1).
`docs/brain-rules.md` row 39 describes the redrink, row 41 names eat, and row 42 is new.

**Tests** (one gradle job at a time):
- `:brain:test` passes (156 tests).
- `StarvationRegressionTest` (38 calm hungry and 5 starving screens with food), `ShatterfishRunTest`,
  `BrainRulesIndexTest` and `StrategyLogTest` pass.
- `:codex:citations`: no findings. `DocsCitationTest` passes.

**Mutation battery on the new lines:** 11 of 11 killed.
- Shooters need a path.
- The eye is missing from the set.
- Redrink: never, always, or at a half share.
- `remaining` off by a turn.
- The meal's reach is one step.
- Sleepers press, shooters do not press, and unreachable enemies press.
- Any Attack presses.

