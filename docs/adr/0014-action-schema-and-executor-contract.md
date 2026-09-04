---
status: proposed
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
