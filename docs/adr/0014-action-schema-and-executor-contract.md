---
status: accepted
date: 2026-09-04
deciders: watchthelight (product owner), Claude (engineer)
---

# ADR-0014: The Action type and the ActionExecutor contract

## Context and problem statement

`Action` is one of the two ports of the architecture (AD-4) and is consumed by `api`, `harness`,
`brain`, `rig` and `overlay`, yet nothing defined its kinds, its parameters, its canonical form in
the Run log, or what "one Action" means when the game would carry out several steps from one
click. The session 12 reviewer gate found this as a critical gap, and found a consequence that
breaks a promise in the experience spine: a multi-cell move keeps `curAction` set and never
returns the hero to the ready state between cells (`…/actors/hero/Hero.java:889-890`, `:977-995`),
so a Decision per cell, and therefore an interruption per cell, is impossible if the executor
issues a move target.

Non-negotiables touched: #1 (an Action must be something a human could input), #4 (through the
UI's own code paths), #5 (the Action list is half the Run tuple).

Paths abbreviate `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/` as `…/`, at tag
`v3.3.8`.

## Decision drivers

- One Action per Input wait (AD-5), so the Action list and the Run log align by `k`.
- Every Action must be expressible as a single human input, and every human input must map to an
  Action or be recorded as unsupported (FR-4).
- The Brain must be able to enumerate the valid Actions from the Observation alone (FR-3), so
  Action parameters may never carry anything the Observation does not.
- Targeting is a two-step interaction in the game (an item's `execute` opens a cell selector or a
  bag window, then the second click completes it); the Action type must close over that without a
  second Input wait.

## Considered options

**Shape**

1. A string command line (`"zap wand-of-magic-missile 14,7"`). Rejected: parsing in five places;
   no compile-time check that a parameter exists.
2. **A sealed interface `Action` in `api` with one record per kind.** Chosen: exhaustive
   `switch` in the executor, and a new kind is a compile error everywhere it matters.
3. One record with a kind enum and a bag of optional fields. Rejected: every consumer would
   re-validate which fields the kind uses.
4. A tree mirroring `HeroAction` (the game's own ten kinds). Rejected: `HeroAction` is the
   *result* of `Hero.handle(cell)`, not the input; the input is a cell or a button, and item use
   never becomes a `HeroAction` at all.

**Granularity of movement**

5. A move target cell, letting the game path to it. Rejected by the gate: the hero does not
   become ready between cells, so no Decision, no interruption, and no Run-log record per cell,
   which contradicts the experience spine's stepping model and FR-27's re-plan every Input wait.
6. **One step to an adjacent cell per Action.** Chosen. The executor calls `Hero.handle(cell)`
   with an adjacent cell, so the hero acts once and becomes ready again; a Brain that wants to
   cross a room emits one step per Input wait and re-plans at each. A human who clicks a distant
   cell in HUMAN mode still produces the game's multi-cell move; that is recorded as one
   `MoveTo` Action with its path, and Replay reproduces it by replaying the same click.
7. Both, with a flag. Rejected: two code paths for the same intent, and the flag would change
   what an Input wait means.

**Targeting**

8. **The Action carries the target it needs, and the executor drives the game's selector
   programmatically**: `UseItem(itemRef, action, target)` calls `Item.execute(hero, action)` and
   then feeds `target` to whatever `CellSelector.Listener` or item-selector window the game
   opened, within the same Input wait. Chosen; it is exactly what a human's two clicks do.
9. Two Input waits, one to open the selector and one to answer it. Rejected: the game does not
   return the hero to ready in between, so the second wait would never arrive.

**Item references**

10. Index into the inventory. Rejected: the index shifts when a stack merges or an item is
    consumed, so a Replay would target a different item.
11. **A stable `ItemRef` assigned by the Observer for the life of a Run**: the position of the
    item in the `Belongings` iteration order at the Input wait, plus the display name and
    quantity, all of which are in the Observation. The executor resolves it by re-walking the
    same order and asserts the display name matches, failing the Action otherwise. Chosen: it
    uses only observable data and detects a desync instead of acting on the wrong item.

## Decision outcome

`Action` is a sealed interface in `org.shatterfish.api`. The kinds, each with the human input it
reproduces:

| Kind | Parameters | Human input it reproduces |
|---|---|---|
| `Step` | adjacent cell | one click or one direction key |
| `Attack` | target cell (adjacent, or in range with a reaching weapon) | a click on a visible enemy |
| `Interact` | target cell | a click on an NPC or an ally to swap |
| `PickUp` | own cell | a click on the hero's own cell over a heap |
| `OpenChest` | adjacent or own cell | a click on a container heap |
| `Buy` | cell of a for-sale heap | a click on shop stock, then the trade window's buy button |
| `Unlock` | adjacent door or exit cell | a click on a locked door with the key held |
| `Descend` / `Ascend` | none | a click on the transition cell the hero stands on |
| `UseItem` | `ItemRef`, action string from the Observation's `actions` list, optional target (cell, `ItemRef`, or an option index) | an item button, then its action, then the selector |
| `Rest` | `full` flag | the wait button, or the rest button |
| `Search` | none | the search button |
| `Talent` / `Ability` | id from the Observation, optional target | the talents pane or the action indicator |
| `AnswerPrompt` | option index from the Observation's `prompt` section | a button in the open window |
| `Wait` | none | the wait button (one turn passes) |

Rules:

- **One Action per Input wait**, and every Action is a single human input plus, where the game
  opens one, the selector answer that input requires.
- **Every parameter is a value the Observation carries.** A cell is a cell the Observation
  includes; an `ItemRef` names an item the Observation lists; an option index indexes the
  Observation's own `prompt` or `actions` list. This is what makes the valid-Action set
  computable from the Observation alone (FR-3) and keeps the leak surface at zero.
- **`ActionExecutor.validActions(Observation)`** returns the Action set from the Observation,
  with no access to game state; `execute(Action)` re-validates against that set, asserts the
  UI-role thread (AD-8), and rejects with a `Reason` value rather than an exception.
- **`Decision.wait` is not valid while a Prompt is open**: the only valid Actions then are
  `AnswerPrompt`, so a Brain that returns `Wait` at a Prompt is a Brain error (the Panel's
  `brain error` state), never a silent stall.
- **Canonical form** for the Run log and hashing is the record's kind name plus its parameters in
  declaration order, integers only, produced by the same `api` writer as everything else
  (ADR-0011); the Action's schema version rides in `header.obsv` because the valid-Action set is
  part of the Observation.
- **Unsupported human input** (an input the executor cannot express, FR-4) is recorded as the
  `unsupported` record of ADR-0011 and ends Replay-verifiability from that `k`; the completeness
  test enumerates the game's hero-affecting inputs against this table.

### Consequences

- Good: the experience spine's promise holds; a human can take over at any cell of a crossing.
- Good: the executor is one exhaustive `switch`, so a new kind cannot be forgotten in `rig` or
  `overlay`.
- Bad: a Brain that crosses a long room spends one Input wait per cell, so the Rig's cost per Run
  rises against a design that batched moves; the E1 benchmark measures it, and the batching a
  human enjoys is a rendering convenience the Rig does not need.
- Bad: `ItemRef` by iteration order plus name is a compromise; a Run with two identically named
  stacks in different bags resolves by order, and the assertion catches only a name mismatch.

## Pre-mortem

*If this is wrong in six months, why?*

- A game input exists that no kind expresses and that a human uses often (a radial menu gesture,
  a drag). Mitigation: FR-4's completeness test is written before the Brain, and the `unsupported`
  record makes the gap visible rather than silent.
- Driving the game's selector programmatically diverges from what a click does (a listener that
  reads pointer state). Mitigation: the executor uses the listener's own `onSelect(cell)`, which
  is what `CellSelector.select` calls; the E1 story asserts it for every targeting item.
- One step per Input wait makes the Overlay's "Fast as it can" feel slower than the game's own
  autoexplore. Mitigation: that mode's ceiling is the sprite-wait bypass hook, not the Action
  granularity.
- `ItemRef` desyncs during a Replay after an unsupported input. Mitigation: verifiability already
  ends there.

## Amendment: story 1.7 (2026-09-05)

The `Action` records exist from story 1.7, since the `actions` section of the Observation
(ADR-0005) is a list of them; story 1.12 adds `validActions`. They differ from the table above in
five places. Item use is three kinds by the shape of its target, `UseItem(item, action)`,
`UseItemAt(item, action, cell)` and `UseItemOn(item, action, target)`, so that every component is
a value and every switch over actions is exhaustive without an optional field. The option index
the table allowed as a target is not a kind: a window of options an item opens, an enchantment to
choose from the scroll's three, lists labels that are not known at the Input wait the item is
used from, so an index chosen there would be blind; and a recognised window in front is an Input
wait of its own (ADR-0015 as story 1.5 amended it), so that window is a Prompt and its answer an
`AnswerPrompt` at the next wait, with the kind story 1.10 adds. Option 8's same-wait answer
stays for the cell selector and the bag, which show the Observation's own cells and items.
`Talent(talent)` names the talent as the hero section lists it, and the ability is
`Ability(ability)` or `AbilityAt(ability, cell)`. `MoveTo(cell)` is a record of the schema, so
that a human's click on a distant cell in the Overlay can be logged as ADR-0011 needs; it is never
in a valid set, and `ActionsSection` refuses it. `ItemRef(index, name, quantity)` is the position in
the inventory section plus the display name and the quantity, and the Observation refuses an
Action whose reference does not match its inventory, which puts option 11's desync check at
construction as well as at execution. `Rest(full)` carries its flag as a boolean. Each record
names its kind, which the codec writes first and the JSON writes as the `kind` key; the canonical
form of an Action in the Run log is that JSON.

## Amendment: story 1.12 (2026-09-11)

The valid set exists, and it lives in `api` as `ValidActions.of(Observation)` rather than on the
executor. The table above named it `ActionExecutor.validActions`, which is where the *authority*
sits and not where the *function* can sit: a Brain sees only `api` (ADR-0003), and a Brain that
cannot compute the set cannot reason about what it is choosing between. The executor of story 1.13
re-validates against it and is still the only thing that may say no. Paths abbreviate
`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/` as `…/`, at `v4.0.0`.

**The Observation carries its own set.** `Observer.observe()` builds the sections, computes the set
from them and returns `withActions` of it, so ADR-0006's Valid Actions row is filled and the leak
tests cover it: an Action whose parameter the Observation does not carry cannot be built, the
record refusing it at construction. `ActionsSection.NONE` stays for the section methods, which the
leak tests use one at a time.

**The rules, and what each reads.** Under a Prompt, the options and nothing else, so `Wait` is
absent as the table says. Otherwise: a `Step` for each of the eight cells around the hero drawn as
a tile a click walks onto; an `Attack` or an `Interact` instead where a character stands there, by
the alignment the actors section carries; `PickUp`, `Buy`, `Unlock` or `OpenChest` for a heap on
the hero's own cell, by its kind; `Descend` or `Ascend` for a transition there, unless the header's
`sealed` flag says a boss fight has locked the floor; every action every item of the inventory
offers, in the shape its target needs; a `Talent` for each talent whose tier still has a point, as
the hero section counts them; the armour ability once the hero has one; and `Rest`, `Search` and
`Wait`, which need nothing.

**Two narrowings, both deliberate.** The set is what the *screen* shows to be available, not what
the game will allow: a cell drawn as floor can be a decoration the hero cannot enter
(`…/levels/Terrain.java:119-120`), and a locked chest is offered whether or not the key is in the
pack. Those are the executor's to refuse, with a reason, which is the division of labour the table
already had. And a targeted item action is offered at each character in view and at the hero's own
cell rather than at every cell of the floor: the cross product of items and cells is thousands of
entries per wait, the set is a menu rather than a proof, and a Brain may still construct another
target, which the Observation checks and the executor judges. A later story may widen it when a
Brain wants to throw at a spot rather than at somebody.

**What is game knowledge, and where.** Three small tables in `ValidActions` say what the screen
means: which tiles a click walks onto, which item actions open the cell selector or the bag, and
which heap kinds open rather than pick up. The bag table is the awkward one, and it is honest about
it: the same identifier opens the bag for one item and not another — `READ` does for the scrolls of
identify, remove curse, transmutation and upgrade (`…/items/scrolls/InventoryScroll.java:39-49`)
and for the scroll of enchantment (`…/items/scrolls/exotic/ScrollOfEnchantment.java:45`), and for
no other scroll — and the identifier is all the Observation carries, which is also all a
player has before reading an unknown scroll. So both shapes are offered for such an action, the
plain one and the one on each other item, and the executor takes the shape the game asks for. The
entries were read off the classes that reach `GameScene.selectItem` rather than guessed, which the
first draft of this story did, and got wrong; the review found five more of them and the two
missing halves of the cell table, and the tables now name the class behind every entry.

**A third narrowing, and it runs the other way.** The set drops `Descend` and `Ascend` on a sealed
floor, which is "what the game will allow" and therefore an exception to the division of labour two
paragraphs above. It is here because the story's acceptance asks for it and because the flag is
already in the header for the screen's own reasons, and it is worth naming as an exception rather
than leaving as an inconsistency: everything else in the set is what the screen shows, and this one
thing is what the game does. The review of the upgrade to `v4.0.0` also found that the flag is not
always drawn — the vault floor seals without the `LockedFloor` buff the HUD shows
(`…/levels/VaultLevel.java:630-637`) — so the bot loses its stairs there with less on screen to
explain it than on a boss floor, where the boss, the bar and the buff are all in view. ADR-0006's
Boss lock row carries that now. They are the wiki-level facts non-negotiable 1 allows a
bot to know, they are cited to the code that decides them, and E2's Codex is where they move when
it exists. `ValidActionsTest` holds the rules over the schema's own corpus, in a module the build
forbids from seeing the game, which is what "computed with no game running" means here.

## Amendment: story 1.13 (2026-09-11)

The executor exists: `org.shatterfish.harness.executor.ActionExecutor`, one method, `execute(
Observation, Action)`, returning an `Outcome` that is either applied or rejected with a `Reason`.
It asserts the Input wait before anything else, re-validates against the Observation's own set, and
then makes the call a person's click, key or button makes. Paths abbreviate
`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/` as `…/`, at `v4.0.0`.

**No hook was needed, which was not obvious.** ADR-0016 spends action registration in E5, so this
story had to reach every input through something upstream already makes public, and every input is:

| Action | The call | Why it is the human's |
|---|---|---|
| `Step`, `Attack`, `Interact`, `PickUp`, `OpenChest`, `Buy`, `Unlock`, `Descend`, `Ascend` | `GameScene.handleCell(cell)` | it is the click: `Hero.handle` decides by cell which of those a click means (`…/actors/hero/Hero.java:1929-2015`) |
| `Rest`, `Wait` | `Hero.rest(flag)` | the rest and wait buttons (`…/ui/Toolbar.java:203`, `:225`) |
| `Search` | `Hero.search(true)` | the search button (`Toolbar.java:313`) |
| `Talent` | `Hero.upgradeTalent(talent)` | what the pane's own button calls (`Hero.java:377`) |
| `UseItem` | `Item.execute(hero, action)` | the item window's button (`…/items/Item.java:157`) |
| `UseItemAt` | that, then `GameScene.handleCell(cell)` | the second click, which the game's own cell selector is waiting for |
| `UseItemOn` | that, then the bag window's `ItemSelector.onSelect`, hiding first as its button does | the second tap (`…/windows/WndBag.java:145`, `:288-300`) |
| `AnswerPrompt` | a `PointerEvent` posted at the button's place | the tap, delivered where the input system delivers one (`SPD-classes/…/input/PointerEvent.java:57-61`, `:132`) |
| `DismissPrompt` | `Window.onBackPressed()` | the back key, and a tap outside the window (`…/ui/Window.java:223-225`) |

**`DismissPrompt` is new**, appended to the sealed list, and ADR-0006's amendment says why: a
message is a Prompt with no buttons, and this is the one thing a person can do with it. It carries
nothing, so the codec writes its kind and stops.

**`Rest(false)` is gone from the valid set.** The wait button and the rest button are the same call
with the flag down, so `Wait` and `Rest(false)` were two entries for one human input; the set offers
`Rest(true)` and `Wait`.

**The driver is told when an Action is handed over.** ADR-0015 as story 1.5 amended it says a new
Input wait follows the hero's own notification, a change of the window in front, *or* an Action
handed to the game. The driver used to infer the third from the hero holding an action or resting,
which detaching the broken seal does neither of (`…/items/armor/Armor.java:190-197`): it plays the
operate animation and returns, and a Run stalled waiting for a wait that had already been served.
`HeadlessDriver.actionHandedOver()` is called by the executor itself, so no caller has to notice.

**What is still unsupported, with reasons**, which `ActionCompletenessTest` holds against the game's
own list of hero actions: the alchemy pot, which opens a scene rather than a window and so is not an
Input wait the Observer accepts; and mining with a pickaxe, a click on a wall, which the valid set
cannot offer because a wall is not a cell a click walks onto. Both are E2's ground, and both would
need a kind of their own.

