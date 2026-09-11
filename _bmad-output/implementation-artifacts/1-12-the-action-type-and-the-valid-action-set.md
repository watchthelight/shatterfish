---
story: 1.12
key: 1-12-the-action-type-and-the-valid-action-set
title: "The Action type and the valid-Action set"
epic: 1
issue: 25
status: review
created: '2026-09-11'
updated: '2026-09-11'
review_loop_iteration: 1
baseline_commit: 'b71b6d8bbc3a3540b11b3678436e5991f58ea59f'
---

# Story 1.12: The Action type and the valid-Action set

As the bot,
I want a closed set of Actions and to know which are legal right now,
So that anything I do could have been done by a person at the same screen.

Paths abbreviate `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/` as `…/`, and every
line number is at the pinned tag `v4.0.0`, which the upgrade of this same day moved to.

## Acceptance criteria and how each was met

| Criterion | Outcome |
|---|---|
| Given ADR-0014's sealed kind list, when `Action` and `validActions(Observation)` are implemented in `api` | **Met.** `Action`'s twenty records landed with the schema in story 1.7; this story adds `ValidActions.of(Observation)` beside them in `api`, and ADR-0014's amendment records why the function lives there rather than on the executor |
| Then a move is one step to an adjacent cell and never a multi-cell target | **Met.** `ValidActions` offers a `Step` only for the eight cells around the hero, and `ActionsSection` refuses `MoveTo` outright; `ValidActionsTest` holds every step to one cell of distance |
| And every parameter is a value the Observation carries: a cell it includes, an item reference it lists, or an option index into its own prompt or action list | **Met.** The record does it: `Observation`'s constructor checks every Action against the sections, so building `withActions(ValidActions.of(observation))` is the assertion, and `ValidActionsTest` builds it |
| And the valid set is computed from the Observation alone, with no game access, asserted by a test that computes it from a deserialized Observation with no game running | **Met.** `ValidActionsTest` runs in `api`, which the build forbids from seeing a line of the game (`ApiBoundaryTest`), so no game is a structural fact rather than an arrangement; the set is a function of the value, which the suite holds by computing it twice from equal records and comparing the hashes |
| And `Wait` is absent from the valid set while a Prompt is open | **Met.** Under a Prompt the options are the only Actions; `ValidActionsTest` holds that and that nothing else appears |

## What was built

- `shatterfish/api/.../ValidActions.java`: the set, and the three small tables of game knowledge it
  needs — the tiles a click walks onto, the item actions that open the cell selector or the bag,
  and the doors an unlock is for.
- `shatterfish/harness/.../observer/Observer.java`: `observe()` returns the Observation with its own
  set attached, which fills the last row of ADR-0006's whitelist.
- Tests: `ValidActionsTest` (eleven), `WalkableTableTest` (three, in the harness, which can see both
  `Terrain` and `Tile`), and `ObserveTest`'s two new ones; `VisibilityChecklistTest` has no pending
  row left; `JsonRenderingTest` names the new helper, as it is built to require.
- Docs: ADR-0014's story 1.12 amendment; ADR-0006's Valid Actions and Boss lock rows; a row in
  `docs/rules/levels.md` for the hero's own rule about the cell a click names.

## What the story found

**The hero's rule for a click is `passable || avoid`, not `passable`**
(`…/actors/hero/Hero.java:1832-1835`). Two terrains are avoid rather than passable and are still
places a person clicks: the chasm, which asks before the jump, and the well, which is how a person
drinks one (`…/levels/Level.java:1253-1254`). The first draft read the flag table alone, kept the
chasm as a special case and left the well out, so the bot could never have taken a well; the review
found it, and `WalkableTableTest` now holds the table to the game's flags rather than to a reading
of them.

**An unlock is for a door, and a chest is opened.** `Hero.handle` sends every chest heap, the locked
one included, to `OpenChest`, and answers the key question inside the action
(`Hero.java:1974-1991`, `:2454-2472`); `Unlock` is for a locked door, a hero-locked door, a crystal
door or the boss floor's exit (`:1993-1998`). The first draft had it exactly backwards, offering an
unlock for a locked chest and never offering one for a door, so a bot could open no locked chest
and no door at all.

**A shop heap is bought only when it is one item with a price** (`Hero.java:1984-1988`), and the
section already says which: the price is carried exactly for a single for-sale item (ADR-0006, the
Heaps row), so the rule reads off the Observation with nothing added.

**The same identifier means different things to different items.** `READ` opens the bag for the
scrolls of identify, remove curse, transmutation and upgrade
(`…/items/scrolls/InventoryScroll.java:39-49`) and for the scroll of enchantment
(`…/items/scrolls/exotic/ScrollOfEnchantment.java:45`), and for no other scroll. The Observation
carries the identifier and not the class, which is also all a person has before reading an unknown
scroll, so both shapes are offered and the executor takes the one the game asks for. The identifiers
that *always* open the cell selector are nine and not two: a first draft guessed at four, three of
which do not exist, and the review found the rest by reading the classes that reach
`GameScene.selectCell` and `GameScene.selectItem`.

**The sealed flag is not always drawn.** Story 1.11 carried `Level.locked` because the boss floors
set it with the `LockedFloor` buff whose icon the HUD shows; the vault floor of `v4.0.0` seals
without the buff and says so in a comment (`…/levels/VaultLevel.java:630-637`). This story is the
first code to act on the bit — the set drops the stairs on a sealed floor — so ADR-0006's row no
longer claims the buff always comes with it.

## Decisions taken inside the story

**The function lives in `api`.** Alternatives: (a) `ActionExecutor.validActions` in the harness, as
ADR-0014's table said; (b) `ValidActions.of` in `api`; (c) both, the harness delegating. Chosen (b):
a Brain sees only `api` (ADR-0003), and a Brain that cannot compute the set cannot reason about what
it is choosing between; the executor stays the authority and re-validates. Pre-mortem: the api
boundary test refuses a class that reads text or bytes into a record, and this one writes records
from a record, which is why it is a named helper rather than a silent arrival.

**The set is a menu, not a proof.** Alternatives: (a) every legal input, including a targeted item
action at every cell of the floor; (b) the inputs the screen shows, with targeted actions at the
characters in view and the hero's own cell; (c) untargeted shapes only, the executor choosing the
target. Chosen (b): (a) is thousands of entries per wait and hashes into the Observation; (c) breaks
ADR-0014's rule that one Action is one input with its selector answer. A Brain may still construct
another target, which the record checks and the executor judges.

**The screen decides, except for the lock.** Everything in the set is what the screen shows to be
available, and the executor refuses what the game will not allow — except the sealed floor, where
the set drops the stairs. That is the story's acceptance and it is an exception, recorded as one in
ADR-0014 rather than left as an inconsistency.

## Evidence

`./gradlew build -Pshatterfish.mobile=off`: green, 467 tests across 39 suites.
`mkdocs build --strict`: clean.

**Mutation battery**, twenty-one mutations of `ValidActions.java` and `Observer.java`, each applied
to a committed clean tree, run against `ValidActionsTest`, `ObserveTest`, `WalkableTableTest` and
`VisibilityChecklistTest`, restored with `git checkout`, the tree verified clean after each:

| # | Mutation | Caught by |
|---|---|---|
| M1 | a Prompt does not take the whole wait | `ValidActionsTest` (waiting is not an answer) |
| M2 | the options of a Prompt are not offered | `ValidActionsTest` (one Action per button) |
| M3 | a step is offered onto a wall | `ValidActionsTest` (fifteen solid tiles beside the hero; it survived the first run, and the test now walls the cell) |
| M4 | a step is offered onto a character | `ValidActionsTest` (a neighbour is an attack or an interaction) |
| M5 | an enemy beside the hero is an interaction | `ValidActionsTest` (the alignment the actors section carries) |
| M6 | a step reaches two cells away | `ValidActionsTest` (a move is one step) |
| M7 | the boss lock does not take the stairs away | `ValidActionsTest` (the sealed floor) |
| M8 | a descent is an ascent | `ValidActionsTest` (the way down under the hero) |
| M9 | a heap under the hero is not picked up | `ValidActionsTest` (nine heap kinds underfoot; it survived the first run, and the test now puts each there) |
| M10 | a targeted item action is offered plainly | `ValidActionsTest` (the shape a target needs), `ObserveTest` (the record refuses an action the item does not offer) |
| M11 | a targeted item action is offered at every cell | `ValidActionsTest` (offered at a character in view or at the hero's cell) |
| M12 | a talent with no point to spend is offered | `ValidActionsTest` (the points the pane draws) |
| M13 | an item action the window does not offer is invented | `ValidActionsTest`, `ObserveTest` (the Observation refuses it at construction) |
| M14 | the whole read carries no Actions | `ObserveTest` (the read carries the set it implies) |
| M15 | the Actions the read carries are somebody else's | `ObserveTest` (the set equals the one the Observation implies) |
| M16 | the well is not a cell a click walks onto | `ValidActionsTest` (the two avoid tiles), `WalkableTableTest` (every terrain the hero can enter) |
| M17 | the chasm is not a cell a click walks onto | `ValidActionsTest`, `WalkableTableTest` |
| M18 | a locked door is not unlocked | `ValidActionsTest` (the doors beside the hero) |
| M19 | a shop heap is bought whatever its price | `ValidActionsTest` (a stacked shop heap is picked up) |
| M20 | a bomb is thrown without a cell | `ValidActionsTest` (every identifier that opens the cell selector) |
| M21 | a stylus inscribes nothing | `ValidActionsTest` (every identifier that can open the bag) |

Thirteen of the first fifteen were caught at once; the two survivors were gaps in the fixtures, no
test having a wall beside the hero or a heap beneath it. Six more came with the review's fixes, two
of which survived their first run because no fixture offered a bomb or a stylus — the table test
that holds every identifier closed them. All twenty-one are caught at the head of the branch.

## The fairness review

Run as an isolated `fairness-reviewer` on `50eaaac40`. Verdict: FINDINGS, none blocking. It could
construct no path by which hidden state reaches an Observation, a Decision or the brain: every
branch reads accessors of the record it is handed, and the module boundary makes anything else a
build failure. What it found instead was six wrong entries in the three tables, and it was right
about all six.

1. **The well.** `WALKABLE` was the passable flag plus the chasm, and the hero's own rule is
   passable or avoid, so the well belonged. Fixed, with `WalkableTableTest` to hold it.
2. **The locked chest and the doors.** `Unlock` was offered for a chest and never for a door, both
   backwards. Fixed.
3. **The shop heap.** `Buy` was offered for every for-sale heap; the game buys only a single priced
   item. Fixed from the price the section carries.
4. **Seven identifiers missing from the cell table** (a bomb lit and thrown, a spell or the chains
   cast, a weapon's ability, the talisman's scry, the armband's steal, the key inserted, the
   sandals' root) and **five from the bag table** (the resin and the liquid metal applied, a glyph
   inscribed, a dart tipped, a glyph transferred, the rose outfitted). Read off the classes that
   reach the selectors and added.
5. **The claim about `READ`** was too strong: the scroll of enchantment opens the bag as well.
   Corrected in the code's comment and in the ADR.
6. **Five cites** were stale at the new pin or named the wrong file. Corrected.

It also asked for two things this story had left thin, and both are in: a harness-side test tying
the walkable table to `Terrain.flags`, which is the test that would have caught the well; and a
test in `ObserveTest` that hidden state cannot change the set, since the row's other claim was
tautological — a mob moved between two cells the hero cannot see changes neither the menu nor the
bytes, and the same mob brought into view turns the step onto its cell into an attack.

Two notes it left are not this story's to fix and are recorded: the alchemy pot and the pickaxe are
human inputs with no Action kind at all, which is ADR-0014's completeness gap for story 1.13's test
to enumerate; and `HeapKind.EBONY_CHEST` in the container list is unreachable, an ebony mimic being
a character the hero cannot stand on.

## Deviations

- No upstream file is touched and no hook is spent.
- `Action` itself was built in story 1.7, so this story is the valid set and the wiring; the
  acceptance criterion that names `Action` is met by what already exists.

## Known limitations, handed forward

- **The set is what the screen shows**, so a step onto a decoration drawn as floor is offered and
  refused by the executor (story 1.13), as it is for a person who clicks the blacksmith's forge.
- **A targeted item action is offered at the characters in view and the hero's own cell**, not at
  every cell; a Brain that wants to throw at a spot constructs the Action itself.
- **An ability is offered in both shapes**, since whether it asks for a cell is the ability's own
  business and the screen does not say.
- **The alchemy pot and mining have no Action kind**, so the set cannot offer them (story 1.13).
- **A diagonal step into a doorway** is offered and the game routes around it; the executor's
  re-validation is where that is settled.

## Follow-ups for later stories

- Story 1.13 (#26): the executor, its rejection reasons, and the completeness test that enumerates
  the game's hero-affecting inputs against the Action kinds — the alchemy pot and the pickaxe are
  waiting there.
- Story 1.14 (#27): the random agent draws from this set, which is its first real use.
