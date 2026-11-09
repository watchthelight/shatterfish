# Story 10.30: the Cleric plays its kit

Issue #376. The class kit stories (10.25 to 10.30) teach the Brain each class's starting kit. This is the one built in this turn: the Cleric,
chosen because it reaches depth 5 least often. On the two ALL-40 slices of the previous measurement ([the pre-seal potion page](2026-11-09-432-preseal-potion-test.md))
the Cleric reached depth 5 in 13 and 15 of 40 Runs, against 14 to 32 for every other class, and in the 520 Runs read in
[story 10.42](2026-11-08-10-42-the-deaths-before-goo.md) it lost 59 of 86 Runs before depth 5, more than any class. The story is kept on the keep rule.

## The gap

The Cleric starts with a holy tome worn in the artifact slot, three charges in it, and a cudgel (`HeroClass.java:248-261`, `HolyTome.java:57-59`).
No Policy ever opened the tome: its one action is CAST, which opens a spell window (`HolyTome.java:81-96`), and the Brain's fallback leaves item uses
alone while anything else is offered, so the Cleric played as a fighter with a cudgel. Its first spell, Guiding Light, is a bolt at a chosen cell for 2 to 8
that costs one charge and one turn (`GuidingLight.java:84-96`, `:111-118`), and it leaves the target illuminated: an illuminated mob has no defence against
the Cleric, so the Cleric's next blow on it cannot miss (`Mob.java:822-832`, `Char.java:423-424`). The tome gains a charge about every forty turns while it
is not full (`HolyTome.java:289-296`), so a charge spent at the cap costs nothing. Rule row `tome-guiding-light`.

## What was built

- **Brain** (`Zap.java`): the zap Policy, which shoots the Mage's staff at an awake, mobile enemy in a clear line within `kit.zap_reach` cells, also shoots with
  the worn holy tome when no staff is worn: `UseItemAt(tome, CAST, enemy cell)`, while the tome shows a charge on its slot. The Playbook gives the Cleric
  `zap_reach` 6, as the Mage has. No new Policy, Action kind, Playbook field or CoachLine template: it is the `zap` Policy's reason, `zap: holy tome`.
  The Policy leaves the tome alone once the Cleric holds a second spell that takes a cell (a point in Shield of Light or in any talent of the second tier or
  above), because the Action does not name its spell.
- **Executor** (`ActionExecutor.java`): `CAST` at a cell on the tome had opened the spell window and left it open. It now plays what a person does: the tome's
  window, the Guiding Light button (closing the window, as the button's click does), and the cell (`WndClericSpells.java:186-206`,
  `TargetedClericSpell.java:34-43`). It checks the same `HolyTome.canCast` the button checks, and refuses when a second cell-targeted spell is in the list.
- **Goo fight**: `Goo.volley` calls `Zap.zapAt`, so a Cleric with a charge now casts at Goo from two cells off or more, as the Mage zaps. That is part of the
  numbers below.

Tests: `ZapPolicyTest` (the Cleric casts, past the reach it does not, no charge or reach 0 it does not, a second spell leaves the tome alone; the first of them
failed on `main`) and `TomeCastTest` (the executor casts Guiding Light: a charge goes, the rat is hit, the window is shut; a spent tome is refused).

## Numbers

`:rig:direction --set standard --class ALL --first 40 --jobs 4 --debug-record`, 240 Runs per slice, 40 per class, salts 1000 and 3000, baseline pinned to the
merge-base `082891b59`, so every difference is this change. The denominators are 240 on every pooled column, 40 on every class column, and 80 per class over
both slices.

| pooled, 240 Runs | salt 1000 baseline | salt 1000 change | salt 3000 baseline | salt 3000 change |
|---|---|---|---|---|
| reach depth 5 | 143 | 150 | 133 | 138 |
| Goo kills | 120 | 126 | 106 | 110 |
| deepest mean | 4.958 | 5.088 | 4.725 | 4.883 |

Pooled over both slices (480 Runs): reach depth 5 rises from 276 to 288 (57.5 to 60.0 percent) and Goo kills from 226 to 236 (47.1 to 49.2 percent).

| class | s1000 reach 5 | s1000 Goo | s3000 reach 5 | s3000 Goo |
|---|---|---|---|---|
| WARRIOR | 32 to 32 | 29 to 29 | 30 to 30 | 22 to 22 |
| MAGE | 25 to 25 | 23 to 23 | 24 to 24 | 21 to 21 |
| ROGUE | 21 to 21 | 14 to 14 | 16 to 16 | 9 to 9 |
| HUNTRESS | 26 to 26 | 21 to 21 | 22 to 22 | 18 to 18 |
| DUELIST | 27 to 27 | 23 to 23 | 24 to 24 | 23 to 23 |
| CLERIC | 12 to 19 | 10 to 16 | 17 to 22 | 13 to 17 |

The keep rule: pooled over both slices not worse in reach depth 5 and Goo kills (both up), and no class 8 or more worse (none is worse; five classes are
identical to the Run, which is the expected result since they hold no tome and the Brain's decisions for them did not change). The Cleric alone: reach depth 5
29 to 41 of 80 (36 to 51 percent), Goo kills 23 to 33 of 80, deepest mean 4.01 to 4.88 on the mean of the two slices. Kept.

## What this cannot show

- Two slices of 240 Runs are two measurements. The Cleric's rows are 40 Runs a slice and move in the same direction on both (+7 and +5 reach, +6 and +4
  Goo kills), but each is one draw. Median turns for the Cleric grew by 64 and 18, as more Runs live to take a longer floor.
- The Cleric still reaches depth 5 in about half its Runs and Goo conversion given reach is 33 of 41, so the class remains the lowest reach of the six. The
  other tome spells (Holy Weapon, for 2 more damage a hit, and Holy Ward, for 1 less damage a hit, both for 50 turns) are not cast: they take the spell window
  as a Prompt with no options today, so casting them needs a Prompt change and, by the story's own rule, an ADR if it needs a new Action. They are the next
  candidate for this class, with the armour ability after the first subclass choice.
- Tengu practice conversion for the Cleric (the story's last clause) is not measured here: the Cleric's Runs on this change end at Goo, and the practice
  floor was not rerun.
- A human takeover that casts another targeted spell is recorded by `HumanTurns` as a `CAST` at a cell, which the Replay would play as Guiding Light. The
  executor refuses the Action when a second such spell is held, so it cannot silently cast the wrong one, but the log still cannot name the spell. A
  Prompt-carrying spell choice would fix that and is the same ADR.
