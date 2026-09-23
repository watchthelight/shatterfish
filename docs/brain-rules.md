# The Brain's Rules index

Every claim about a game mechanic that the Brain relies on, one row each, each pointing at the Rule
that settles it (FR-17). A Rule carries its `path:line` citations at the pinned tag, its Tier and
its test. This page carries only the claim and where it is used, so that "every heuristic is cited"
can be counted rather than asserted.

`BrainRulesIndexTest` holds every row to a Rule that exists: the quoted opening words must begin a
row of the linked page. When a Policy starts relying on something new, it gets a row here in the
same pull request. When a Rule flips to needs-review at an upgrade, the rows pointing at it here are
the Brain behaviour to re-check.

| # | Claim the Brain relies on | Used by | Rule |
|---|---|---|---|
| 1 | An options window draws one button per option, in order, so an `AnswerPrompt` index names a button whose label the Observation carries. | `answer-prompt` | [ui: "Every options window the game opens at the tag is an anonymous subclass"](rules/ui.md) |
| 2 | Pressing an option's button closes the window before the option acts, so an answered Prompt is closed. | `answer-prompt` | [game-loop: "Quest and shop dialogs are shown from the actor thread"](rules/game-loop.md) |
| 3 | The chasm prompt jumps only on "yes", so declining leaves the hero where it stood. | `answer-prompt` (declining) | [game-loop: "Walking onto a chasm asks first, within one act"](rules/game-loop.md) |
| 4 | The status pane shows the hero's exact health and maximum, which the `hp-low` flag reads. | Safety flags | [ui: "The status pane prints the hero's health over its bar"](rules/ui.md) |
| 5 | The hunger icon has three states, which the `hungry` and `starving` flags read. | Safety flags | [ui: "The hunger icon has three states"](rules/ui.md) |
| 6 | An enemy in view is a mob with the enemy alignment in the hero's field of view, which the `enemy-in-view` flag reads. | Safety flags | [visibility: "`hero.visibleEnemies` (the number on the DangerIndicator"](rules/visibility.md) |

The `fallback` Policy relies on no mechanic. It chooses uniformly among the Actions the Observation
offers, and the offered set is `ValidActions`' to get right (story 1.12), not the Brain's.

Row 3's Rule is at needs-review since the upgrade to `v4.0.0`. Until it is re-read, the Brain's
choice to decline a chasm prompt rests on a Rule nobody has confirmed at the pin.
