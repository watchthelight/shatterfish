---
title: 'Story 4.9: The eat and heal Policies'
type: 'feature'
created: '2026-09-25'
status: 'review'
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
  `answer-prompt, heal, fight, eat, explore, fallback`; `handed` records a drink; `foods()` and
  `uneaten()` for the Codex drift test.
- `shatterfish/brain/.../Memory.java`: `drank`, the wait at which the heal Policy last handed over
  a drink (VERSION 5), with story 4.7's twenty-argument constructor kept.
- `shatterfish/brain/.../Beliefs.java`: the fold carries `drank` over.
- `shatterfish/brain/.../Policies.java`: the fallback never draws an eat Action.
- `shatterfish/rig/.../StarvationRegressionTest.java`: real `smoke` Runs.
- `shatterfish/rig/.../FoodCodexTest.java`: the food table against the Codex's item and string tables.
- `docs/rules/buffs.md`: the hunger row re-read at v4.0.0; new rows for food and for the potion of healing.
- `docs/brain-rules.md`: rows 30 to 35, and the fallback's note.
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
   fight is over, and never less than their worst single turn.** Chosen. With `E` the enemies in
   view, `k` the enemies that can engage the hero's cell at once, `perTurn` the sum of the `k`
   largest expected damages per turn (`Fight.enemyDamage`), `worst` the sum of the `k` largest
   damage maxima less the hero's armour minimum, and `H` the turns the hero expects to need to kill
   the enemy it kills soonest, between 1 and 10: `danger = max(worst, ceil(perTurn × H))`. The
   Policy drinks when `hp ≤ danger`, the hero is missing at least a quarter of the potion's heal
   (the first turn's heal is a quarter of what is left, Healing.java:76-79), and no drink was handed
   over in the last 5 waits. With no enemy in view the danger is 0 and it never drinks.

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
    - no drink was handed over in the last `Heal.HEALING = 5` waits.
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
