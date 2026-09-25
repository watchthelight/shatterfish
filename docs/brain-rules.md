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
| 7 | Trying an unidentified item where the hero stands does, at worst, what `SafeTest` scores: the fire, gas, sleep and cursed-wand effects of each candidate identity, scaled by the game's scaling depth, with water shortening a burn. | `test-item`, `equip` (through `SafeTest`) | [identification: "The worst cases `SafeTest` scores an unidentified item by"](rules/identification.md) |
| 8 | A click on a stairs cell travels, and a click on a chest, a tomb or remains opens it: neither is a step, so the explore Policy never paths through a transition or a heap that is not a plain one. | `explore` | [game-loop: "`Hero.handle(cell)` sets `curAction` by cell content"](rules/game-loop.md) |
| 9 | The cells a click steps onto are the walkable tiles the Observation offers Steps onto; the explore Policy walks only on those, less the chasm, which jumps, and the well, which drinks. | `explore` | [levels: "A cell a click steps onto"](rules/levels.md) |
| 10 | An intentional search finds every searchable secret within its radius at once, so the explore Policy searches from each spot once. | `explore` (searching) | [visibility: "`Hero.search(intentional)` scans"](rules/visibility.md) |
| 11 | A search reaches the square of cells within one of the hero (two for the Rogue), costs two turns and adds 4 to hunger, so the explore Policy counts a wall as covered once a search was made within that reach of it, and searches only where it reaches an uncovered wall. | `explore` (searching) | [game-loop: "Intentional search costs"](rules/game-loop.md) |
| 12 | Doors may be hidden from depth 2, which is why a floor with no frontier left is searched before it is given up. | `explore` (searching) | [levels: "Hidden doors: on depth > 1"](rules/levels.md) |
| 13 | A hidden door is drawn as a wall until it is found, so a search spot is a cell beside a wall. | `explore` (searching) | [visibility: "Before discovery a SECRET_DOOR is drawn with the WALL visual"](rules/visibility.md) |
| 14 | A click on a transition cell with no enemy in view travels, and on the hero's own transition cell it is the Descend input; a sealed floor refuses every transition, so the explore Policy walks onto the exit when a floor is done and never from a sealed floor. | `explore` (descending) | [game-loop: "`Hero.handle(cell)` sets `curAction` by cell content"](rules/game-loop.md) |
| 15 | Depth 2's entrance room has all its doors hidden until the guidebook's Searching page is found, which is why an exhausted floor is searched before the Policy gives it up. | `explore` (searching) | [levels: "On depth 2 every unlocked door of the entrance room is hidden"](rules/levels.md) |
| 16 | Adjacent means Chebyshev distance 1, so any of a cell's eight neighbours is next to it, walls or no walls; how many enemies can engage a cell is how many of its neighbours an enemy can stand on. | `fight` | [combat: "`distance` is Chebyshev"](rules/combat.md) |
| 17 | A hunting mob attacks when it can attack its enemy, which is when it is adjacent, so a hunting enemy beside the hero is fighting it; an enemy the game keeps passive is not hunting, which row 25 covers. | `fight` | [combat: "A hunting mob attacks when"](rules/combat.md) |
| 18 | An attack lands when a uniform roll under the attacker's accuracy is at least a uniform roll under the defender's evasion, which gives the fight Policy its hit chance. | `fight` (threat estimate) | [combat: "`Char.hit`:"](rules/combat.md) |
| 19 | The hero's accuracy is 10 and its evasion 5, each one more per level, before its weapon, armour, rings and talents. | `fight` (threat estimate) | [combat: "Hero base `attackSkill = 10`"](rules/combat.md) |
| 20 | A hit does the damage roll less the defender's damage-reduction roll, never below 0. | `fight` (threat estimate) | [combat: "On a hit the defender takes the attacker's damage roll"](rules/combat.md) |
| 21 | A mob's hit points, accuracy, evasion, damage and damage reduction are fixed figures in its class, which the Codex reads into the table the fight Policy estimates by. | `fight` (threat estimate) | [combat: "Mob combat numbers live in per-class overrides"](rules/combat.md) |
| 22 | The hero's damage reduction is its armour's roll, which the Codex measured per armour and level. | `fight` (threat estimate) | [combat: "Hero `drRoll`"](rules/combat.md) |
| 23 | Bare-handed, the hero rolls `NormalIntRange(1, max(STR - 8, 1))`. | `fight` (threat estimate) | [combat: "A hero fighting bare-handed"](rules/combat.md) |
| 24 | While a boss fight has sealed the floor, no transition activates, so the fight Policy never plans a retreat by the stairs on a sealed floor. | `fight` (retreat) | [levels: "`Level.locked` is set by `seal()`"](rules/levels.md) |
| 25 | The animated and armored statues and the gnoll exile stay passive until provoked -- a statue by damage, an exile by a debuff -- so while one shows a full health bar, no buff and no alert, the fight Policy neither fights nor flees it and the explore Policy walks past it. | `fight`, `explore` | [combat: "The animated statue is created `PASSIVE`"](rules/combat.md) |
| 26 | Damage and damage-reduction rolls are `NormalIntRange`, a triangular distribution, which the threat estimate takes the exact expectation over. | `fight` (threat estimate) | [combat: "`Random.NormalIntRange(min, max)`"](rules/combat.md) |
| 27 | A weapon or armour shows its enchantment or glyph around its own name, and a mage's staff the name of its wand, which is how the threat estimate finds what the hero wears. | `fight` (threat estimate) | [combat: "A weapon's shown name wraps"](rules/combat.md) |
| 28 | Depth 1's up stairs are the surface, which a hero without the Amulet cannot leave by, so the fight Policy retreats only by the regular stairs. | `fight` (retreat) | [levels: "Depth 1's entrance is a `SURFACE` transition"](rules/levels.md) |
| 29 | Regeneration heals only while the hero is not starving, so the rest before going back down a fled floor is skipped while hungry or starving, and capped. | `explore` (rest) | [buffs: "Regeneration heals 1 HP every 10 turns"](rules/buffs.md) |
| 30 | A click on a plain heap with no enemy in view walks there and picks it up on arrival, and a pick-up takes a turn, so the pick-up Policy prices an item against the Steps to it plus one, and enters only on a calm screen. | `pick-up` | [game-loop: "`Hero.handle(cell)` sets `curAction`"](rules/game-loop.md) |
| 31 | What the pick-up and equip Policies weigh: a pick-up's turn and a refused pick-up's none, gear's curse chance given no enchantment shows, the strength penalty, the equip turn and a swap's two, the curse that keeps a piece on, and a cursed piece's worst proc. | `pick-up`, `equip` | [identification: "What the pick-up and equip Policies weigh"](rules/identification.md) |
| 32 | Equipping reveals a curse and a cursed piece cannot be taken off, so the equip Policy never puts on a piece shown cursed and never replaces one. | `equip` | [identification: "Equipping a weapon, armor, ring or artifact sets `cursedKnown = true`"](rules/identification.md) |
| 33 | A weapon's and an armour's strength requirement is set by its tier, which the equip Policy reads from the Codex at level 0. | `equip` | [identification: "Strength requirement is"](rules/identification.md) |
| 34 | The Warrior putting on new armour is asked, under the broken seal's name, whether to move the seal, and "no" leaves it on the armour coming off, so answer-prompt affirms that one Prompt. | `answer-prompt` (affirming) | [identification: "What the pick-up and equip Policies weigh"](rules/identification.md) |
| 35 | An unknown inventory scroll read without a target is identified and consumed, and the cancel confirmation that follows is answered by answer-prompt's lowest answer, "Yes, I'm positive"; "No, I changed my mind" would reopen a picker no Action answers, and a scroll of upgrade read onto an item opens a window no Action answers. So the test-item Policy reads plainly, and the fallback leaves the items alone while anything else is offered. | `test-item`, `answer-prompt`, `fallback` | [identification: "An unknown inventory scroll read without a target"](rules/identification.md) |
| 36 | A closed door beside the drinker's cell keeps toxic gas in the room once the hero is through the door, and water on or beside the cell shortens a burn, so the test-item Policy walks to such a cell to drink, and steps out of its own cloud or fire afterwards. | `test-item` (testing cell, escape) | [identification: "A closed door is solid and an open one is not"](rules/identification.md) |
| 37 | Reading is refused, with no turn spent, while blind, immune to magic or under a cursed spellbook's charge, so the test-item Policy does not read under the first two and records a test the game did not carry out. | `test-item` (balk) | [identification: "Reading a scroll is refused (turn not spent)"](rules/identification.md) |
| 38 | Drinking an unknown potion asks no confirmation, so the test-item Policy's drink is one Action. | `test-item` | [identification: "Drinking an unknown potion asks no confirmation"](rules/identification.md) |
| 39 | A shut door is solid and a gas does not spread into it, so a shut door shows no gas while the room behind it is full of it; a cell seen showing fire or a harmful gas is therefore kept out of until it is seen clear, and a shut door's cell is never seen clear. | `explore`, `pick-up`, `fight`, `test-item` (walking) | [identification: "A closed door is solid and an open one is not"](rules/identification.md) |

The `fallback` Policy chooses uniformly among the Actions the Observation offers, and the offered set
is `ValidActions`' to get right (story 1.12), not the Brain's. Since story 4.10 it leaves the item
uses out while anything else is offered, which rests on row 35.

Rows resting on a needs-review Rule: 3.

Row 3's Rule has been at needs-review since the upgrade to `v4.0.0`. Until it is re-read, the
Brain's choice to decline a chasm prompt rests on a Rule nobody has confirmed at the pin.
