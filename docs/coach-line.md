# The coach's sentence

Story 8.8 (FR-47, UJ-4). The Decision card shows the Brain's choice as one plain line in the game's own
words: the Action as the Decision log words it, a colon, and one reason clause. `CoachLine` composes it from
the `RunLog.Decision` and the Observation the Decision was made on, and from nothing else, so it says only what
the bot knows and is the same line with the Oracle flag on or off. An unidentified item is called by the name
the screen draws; no identity is ever in it.

The clause is chosen from the Policy's name and the lead of the choice's one-line reason, with no number the
person would have to decode. A Safety flag the clause does not already say follows it after a comma (`hurt`,
`hungry`, `starving`). A Decision whose reason no template recognises, or that has none, shows the Action alone
and not an invented reason. A line is at most 70 characters, two wraps of the Panel's narrowest text.

`CoachLineTest` holds this page in both directions with the code: one row per template, each template's Rule
a row of [the Brain's Rules index](brain-rules.md), every word of every clause on the closed list committed
beside the test, and a template for every Policy the Brain arbitrates, so a new Policy without a sentence fails
the build. A clause adds no threshold, so there is nothing for `HeuristicLinkTest` to link.

| Template | Clause | Rule it rests on |
|---|---|---|
| `answer-prompt/jump` | the chasm is the way down | [`chasm-plunge`](brain-rules.md#chasm-plunge) |
| `answer-prompt/affirm` | to confirm what it planned | [`option-press-closes-window`](brain-rules.md#option-press-closes-window) |
| `answer-prompt/decline` | to say no to the question | [`item-confirmation-decline`](brain-rules.md#item-confirmation-decline) |
| `answer-prompt/answer` | to answer the question | [`option-press-closes-window`](brain-rules.md#option-press-closes-window) |
| `answer-prompt/dismiss` | to close the window | [`option-press-closes-window`](brain-rules.md#option-press-closes-window) |
| `answer-prompt/subclass` | to pick a subclass | [`subclass-window`](brain-rules.md#subclass-window) |
| `answer-prompt/upgrade` | to upgrade the best gear | [`upgrade-window`](brain-rules.md#upgrade-window) |
| `answer-prompt/guess` | the likeliest kind | [`guess-window`](brain-rules.md#guess-window) |
| `answer-prompt/leave` | to leave the window | [`shun-no-time-windows`](brain-rules.md#shun-no-time-windows) |
| `heal/heal` | hurt, with enemies near | [`heal-threshold`](brain-rules.md#heal-threshold) |
| `douse/douse/water` | burning, water is near | [`burning-douse`](brain-rules.md#burning-douse) |
| `douse/douse/ground` | burning, bare ground is near | [`burning-douse`](brain-rules.md#burning-douse) |
| `zap/zap` | an enemy is in staff range | [`staff-zap`](brain-rules.md#staff-zap) |
| `shoot/shoot` | an enemy is in bow range | [`bow-shoot`](brain-rules.md#bow-shoot) |
| `ranged/throw` | to hit from a distance | [`missile-throw`](brain-rules.md#missile-throw) |
| `one-ply/heal` | a drink is worth it | [`heal-threshold`](brain-rules.md#heal-threshold) |
| `one-ply/retreat` | to back away from the fight | [`heal-threshold`](brain-rules.md#heal-threshold) |
| `one-ply/reposition` | to fight from a better cell | [`adjacent-chebyshev`](brain-rules.md#adjacent-chebyshev) |
| `one-ply/attack` | the fight is worth it | [`hit-chance`](brain-rules.md#hit-chance) |
| `fight/chase` | to follow the enemy | [`sleeping-mob-out-of-view`](brain-rules.md#sleeping-mob-out-of-view) |
| `fight/hold` | to hold a good spot | [`adjacent-chebyshev`](brain-rules.md#adjacent-chebyshev) |
| `fight/retreat/stairs` | to leave by the stairs | [`depth-1-up-stairs`](brain-rules.md#depth-1-up-stairs) |
| `fight/retreat/water` | to get out of the water | [`water-bound-enemies`](brain-rules.md#water-bound-enemies) |
| `fight/retreat` | to back away from the fight | [`heal-threshold`](brain-rules.md#heal-threshold) |
| `fight/attack` | an enemy is next to it | [`hunting-adjacent-fights`](brain-rules.md#hunting-adjacent-fights) |
| `fight/chokepoint` | to fight where few reach it | [`adjacent-chebyshev`](brain-rules.md#adjacent-chebyshev) |
| `fight/approach` | to close in on the enemy | [`hunting-adjacent-fights`](brain-rules.md#hunting-adjacent-fights) |
| `fight/cornered` | cornered, so it fights | [`faster-enemy-no-outrunning`](brain-rules.md#faster-enemy-no-outrunning) |
| `eat/eat` | the hunger icon says eat | [`hunger-icon-states`](brain-rules.md#hunger-icon-states) |
| `eat/eat/water` | water first, then the meat | [`blandfruit-mystery-meat`](brain-rules.md#blandfruit-mystery-meat) |
| `test-item/escape` | to get clear of the effect | [`test-spot-door-water`](brain-rules.md#test-spot-door-water) |
| `test-item/upgrade` | to upgrade the best gear | [`upgrade-allocation`](brain-rules.md#upgrade-allocation) |
| `test-item/drink` | a known gain, drunk at once | [`strength-experience-potions`](brain-rules.md#strength-experience-potions) |
| `test-item/cell` | to reach a safe place to test | [`test-spot-door-water`](brain-rules.md#test-spot-door-water) |
| `test-item/rest` | to rest after a test | [`regen-rest-test`](brain-rules.md#regen-rest-test) |
| `test-item/test/healing` | it may heal, and it is hurt | [`unknown-potion-heal-half`](brain-rules.md#unknown-potion-heal-half) |
| `test-item/test/gain` | it may be a gain | [`strength-experience-potions`](brain-rules.md#strength-experience-potions) |
| `test-item/test/inventory` | gear to pick, so worth a read | [`scroll-picker`](brain-rules.md#scroll-picker) |
| `test-item/test/scroll` | full health, so a read is safe | [`unknown-scroll-full-health`](brain-rules.md#unknown-scroll-full-health) |
| `test-item/test` | worst case survivable | [`safe-test-worst-cases`](brain-rules.md#safe-test-worst-cases) |
| `pick-up/item` | an item worth taking | [`pickup-equip-weights`](brain-rules.md#pickup-equip-weights) |
| `pick-up/take` | an item worth taking | [`pickup-equip-weights`](brain-rules.md#pickup-equip-weights) |
| `open-chest/open` | a chest to open | [`chest-open-key`](brain-rules.md#chest-open-key) |
| `open-chest/chest` | a chest to open | [`chest-open-key`](brain-rules.md#chest-open-key) |
| `equip/wear` | better gear to wear | [`pickup-equip-weights`](brain-rules.md#pickup-equip-weights) |
| `descend/spent` | the floor is done | [`time-and-hunger-costs`](brain-rules.md#time-and-hunger-costs) |
| `descend/hungry` | hungry, food lies below | [`floor-food-guarantees`](brain-rules.md#floor-food-guarantees) |
| `descend/overstayed` | it has stayed long enough | [`time-and-hunger-costs`](brain-rules.md#time-and-hunger-costs) |
| `descend/unlock` | to open the locked exit | [`goo-worn-key`](brain-rules.md#goo-worn-key) |
| `descend/no exit` | to search for a hidden exit | [`hidden-exit-search`](brain-rules.md#hidden-exit-search) |
| `descend/rest` | to rest before going down | [`rest-before-descend`](brain-rules.md#rest-before-descend) |
| `explore/frontier` | to uncover more of the floor | [`step-cells`](brain-rules.md#step-cells) |
| `explore/search` | to look for a hidden way | [`search-radius-cost`](brain-rules.md#search-radius-cost) |
| `explore/away` | to keep clear of fire or gas | [`gas-fire-avoid`](brain-rules.md#gas-fire-avoid) |
| `explore/stuck` | it cannot move as asked | [`root-vertigo-refusal`](brain-rules.md#root-vertigo-refusal) |
| `explore/rest` | to rest before going down | [`rest-before-descend`](brain-rules.md#rest-before-descend) |
| `plunge/jump` | no way down but the chasm | [`chasm-plunge`](brain-rules.md#chasm-plunge) |
| `plunge/rest` | to rest before the jump | [`chasm-plunge`](brain-rules.md#chasm-plunge) |
| `fight/dodge` | to dodge the pump | [`goo-pump`](brain-rules.md#goo-pump) |
| `fight/goo/attack` | to fight Goo | [`goo-engage`](brain-rules.md#goo-engage) |
| `fight/goo/wait` | to wait for Goo's move | [`goo-pump`](brain-rules.md#goo-pump) |
| `fight/goo/approach` | to close in on Goo | [`goo-engage`](brain-rules.md#goo-engage) |
| `fallback/fallback` | nothing better to do | none: it says only that nothing above fired |

## Coach mode

Story 8.9 (FR-47, UJ-4). Coach mode puts this sentence in front of a person who is playing. It is a setting of the
HUMAN Mode and not a fourth Mode: the Controls row has a Coach button (a hotkey with no default key, bound in the game's
key-binding screen), off by default, dim in RUNNING, PAUSED and once the Run is over, with the reason in the Mode
strip when it is pressed anyway ("Coach is off: the bot is playing; take over to play with the coach").

Nothing new is decided for it. A HUMAN Run already asks the Brain, at every wait, what it would do (the shadow
Decision of story 5.9), updates its Beliefs and Memory from the Observation, and never executes the answer. With the
coach on, the Decision card shows the sentence of the shadow Decision, only when that shadow is for the wait the
person is looking at; the Brain's worker computes it while the person thinks, a move made first is taken at once, and
a shadow that lands after it is dropped from the card (the log keeps it as skipped, as for any shadow). With the coach
off the card shows the shadow's table and no sentence.

Switching the coach on or off writes one `mode` record of the existing kind (`HUMAN`, speed `player, coach` or
`player`), at the wait in front. No log format changes, nothing reads the speed word back, and a log with coach advice
verifies and replays as an ordinary log. `CoachRunTest` holds that a coached Run and an uncoached one, played with
the same moves, have the same Observation hash at every wait and the same shadow Decisions.
