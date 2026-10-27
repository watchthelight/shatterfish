# Item identification: the Warrior knows healing from the start, so the lever is not where it looked

Story 7.3 (issue #280). The premise of `2026-10-26-desperate-potion.md` was that 35 of 40 deaths held an unidentified
potion, so a better way to try potions should raise reach. This page records what the held packs and the Beliefs say, and
the measurements of the three Playbook levers that could be built without a new Action.

## What the held packs and the Beliefs say

- A Warrior starts with the potion of healing and the scroll of rage identified (`HeroClass.java:185-187`), and every class with
  the scroll of identify (`HeroClass.java:117`). A probe of 142 held-potion screens on the first 12 tuning Runs found the
  healing odds of every unidentified potion at 0.0 and the odds of strength or experience between 0.04 and 0.19. So the
  healing path of the test-item Policy never fires for a Warrior, whatever its thresholds say, and a desperation drink of an
  unknown potion has no healing to find. The earlier page's result (66 Goo kills of 120 both with and without the rule) follows.
- 34 of 40 tuning Runs died with an unidentified potion in the pack. They are held because the chance of strength or experience
  is under the 0.2 floor, or because a harmful candidate is lethal at the hero's hit points (liquid flame scores 30 at depth 1,
  against 20 hit points).
- 18 units of a **known** potion of strength were in the packs at death (and 9 of healing), and no Policy drinks a known potion
  of strength or experience.
- The reference values play 189 tests in 40 Runs (78 potion drinks, all of them through `gain_odds_per_mille`, 111 scroll reads)
  and none of the 40 deaths follows a test within 100 waits.

## Measured, all against `main` `f51e9dae1` on `standard` WARRIOR

Tuning slice (salts 1000 to 1039), reach depth 5 / Goo kills / deaths within 100 waits of a test:

| variant | reach | Goo | near-test deaths |
|---|---|---|---|
| `main` and the reference values | 30/40 | 27/40 | 0 |
| healing odds floor 100 or 1, potion health ceiling 1000 | 30/40 | 27/40 (no Decision changed) | 0 |
| drink a known strength or experience potion | 30/40 | 26/40 | 0 |
| ... and gain floor 40 (drink nearly every unknown potion) | 25/40 | 23/40 | 4 |
| ... and gain floor 120 | 31/40 | 27/40 | 0 |
| ... and gain floor 300 (no unknown potion drunk) | 29/40 | 25/40 | 2 |

Second slice (salts 3000 to 3079) for the gain-floor-120 variant: reach 44/80 against 46/80, Goo kills 37/80 against 41/80.

Nothing beat `main` on both slices; the Playbook ships at the reference values.

## If this is reopened

- A **thrown** unknown potion is the one use of a potion the Warrior has left: liquid flame, toxic gas, frost and paralytic gas
  thrown at a distant group are weapons, and a throw identifies them. It needs a throw Action with a target choice and a way to
  tell the harmful from the benign without the true identity (the Beliefs' candidate shares, as `SafeTest` already reads them).
- A **known scroll of identify** (every class starts with one) read onto the unknown potion with the most worth buys under a
  fifth of a strength point per scroll for a Warrior; for the other classes, which start with healing unknown, it may be worth
  more and is worth measuring on them.
- The other classes: `potion_hp_per_mille` and `healing_odds_per_mille` are live for a Mage, Rogue or Huntress and have not
  been measured on any of them. Start with a `--class MAGE` slice of the same sets.
