# The Brain's Rules index

Every claim about a game mechanic that the Brain relies on, one row each, each pointing at the Rule
that settles it (FR-17). A Rule carries its `path:line` citations at the pinned tag, its Tier and
its test. This page carries only the claim and where it is used, so that "every heuristic is cited"
can be counted rather than asserted.

`BrainRulesIndexTest` holds the index in both directions:

- every row points at a Rule that exists: the quoted opening words must begin a row of the linked page;
- every Policy the Brain arbitrates, except `fallback`, and every Safety flag a Decision can carry,
  is named in some row's "Used by" column;
- the list of rows resting on a needs-review Rule, below, is exactly the rows whose linked Rule
  says needs-review on its page.

When a Policy starts relying on something new, it gets a row here in the same pull request. When a
Rule flips to needs-review at an upgrade, the test names the rows here that rest on it: the Brain
behaviour to re-check.

| # | Claim the Brain relies on | Used by | Rule |
|---|---|---|---|
| 1 | An options window draws one button per option, in order, so an `AnswerPrompt` index names a button whose label the Observation carries. | `answer-prompt` | [ui: "Every options window the game opens at the tag is an anonymous subclass"](rules/ui.md) |
| 2 | Pressing an option's button closes the window before the option acts, so an answered Prompt is closed. | `answer-prompt` | [game-loop: "Quest and shop dialogs are shown from the actor thread"](rules/game-loop.md) |
| 3 | The chasm prompt jumps only on "yes", so declining leaves the hero where it stood. | `answer-prompt` (declining) | [game-loop: "Walking onto a chasm asks first, within one act"](rules/game-loop.md) |
| 4 | The status pane warns of low health below a third, `HP/HT < 0.334`, which is where the `hp-low` flag starts. | `hp-low` | [ui: "The status pane tints the hero's portrait as a low-health warning"](rules/ui.md) |
| 5 | The hunger icon has three states, which the `hungry` and `starving` flags read. | `hungry`, `starving` | [ui: "The hunger icon has three states"](rules/ui.md) |
| 6 | An enemy in view is a mob with the enemy alignment in the hero's field of view, which the `enemy-in-view` flag reads. | `enemy-in-view` | [visibility: "`hero.visibleEnemies` (the number on the DangerIndicator"](rules/visibility.md) |
| 7 | Trying an unidentified item where the hero stands does, at worst, what `SafeTest` scores: the fire, gas, sleep and cursed-wand effects of each candidate identity, scaled by the game's scaling depth, with water shortening a burn. | `SafeTest` (no Policy yet; story 4.10) | [identification: "The worst cases `SafeTest` scores an unidentified item by"](rules/identification.md) |

The `fallback` Policy relies on no mechanic. It chooses uniformly among the Actions the Observation
offers, and the offered set is `ValidActions`' to get right (story 1.12), not the Brain's.

Rows resting on a needs-review Rule: 3.

Row 3's Rule has been at needs-review since the upgrade to `v4.0.0`. Until it is re-read, the
Brain's choice to decline a chasm prompt rests on a Rule nobody has confirmed at the pin.
