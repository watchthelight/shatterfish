---
title: 'Story 4.6: The explore Policy'
type: 'feature'
created: '2026-09-23'
status: 'review'
baseline_commit: 'd17fda7ec (story 4.5, which contains 4.2 and 4.4)'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Every Brain so far plays like the random agent outside a Prompt. It never uncovers a
floor on purpose, so it never finds the stairs or the items, and a Run ends by starving or by
whatever wanders into it (FR-31).

**Approach:** A Policy, `explore`, between the prompt Policy and the fallback.
- It moves one Step per Input wait toward the nearest unexplored frontier: a known walkable cell
  beside a cell the screen has never shown. The path is found by breadth-first search over known
  walkable cells, starting from the Steps the screen offers.
- When no frontier is reachable, it searches for secret doors from cells beside a wall, once per
  spot, for a bounded number of spots per floor, and then gives the floor up.
- It yields when an enemy is in view, when a Prompt is open, and when the hero has stood on one cell
  for three waits in a row outside a search.

## Boundaries & Constraints

**Always:**
- The walkable cells come from the Observation's tiles, fog, heaps, traps, transitions and actors,
  never from game code (`brain` imports only `api`).
- The first move is always an offered Step.
- The Memory records only what was seen: where the hero stood, never that it meant to search.
- Every mechanics claim is a row in `docs/brain-rules.md`.

**Ask First:** Anything that needs an Observation field the Observer does not carry.

**Never:**
- Walking onto a stairs cell, a chest or other non-plain heap, an armed trap, the chasm or the well.
- Taking the stairs: that is story 4.12.
- Reading a secret door the screen has not revealed.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Frontier reachable | known floor leading to unseen cells | a Step toward the nearest, reason "frontier n" | N/A |
| Two frontiers | one nearer | the nearer; equal ones break the same way every time | N/A |
| Stairs, chest, trap, chasm on the only way | nothing else leads on | not walked through; the floor counts as exhausted | N/A |
| Floor exhausted | no frontier, walls around | walk to the nearest wall-side cell not yet stood on, search there once | at most 12 spots per floor, then yield |
| Enemy in view | a mob of enemy alignment | the Policy does not enter | N/A |
| Step refused | hero on one cell three waits running | the Policy yields that wait | N/A |

</frozen-after-approval>

## Code Map

- `shatterfish/brain/.../Explore.java` (new): the Policy; `walkable`, `frontier` and `spot` are
  package-visible for the test.
- `shatterfish/brain/.../Memory.java`: version 3. It adds `at` (where the hero stood at the last
  wait), `streak` (waits in a row on it) and `dwelt` (cells stood on at two waits in a row, per floor).
- `shatterfish/brain/.../Beliefs.java`: `fold` records them.
- `shatterfish/brain/.../Brain.java`: the Policies are answer-prompt, explore, fallback. Each
  Policy's stream is keyed on its name.
- `docs/brain-rules.md`: rows 7 to 12.
- `docs/rules/game-loop.md` (`Hero.handle(cell)`, intentional search cost) and
  `docs/rules/visibility.md` (`Hero.search`): re-read at v4.0.0 and re-cited, Tier 1.

## Tasks & Acceptance

**Execution:**
- [x] `Explore` Policy, Memory v3, and wiring into the Brain.
- [x] `ExplorePolicyTest`; `ShatterfishRunTest` checks that explore's waits are Steps or Searches
  and that it takes some.
- [x] Rules index rows and the three rules re-read at v4.0.0.
- [ ] Smoke direction check (parent).

**Acceptance Criteria:**
- It moves toward the nearest unexplored frontier reachable through known-passable cells
  (`ExplorePolicyTest.nearest_frontier`, `around_what_a_click_would_do`).
- It issues one Step per Input wait (`ShatterfishRunTest`: an explore wait is one Step or one Search).
- It searches when the floor is otherwise exhausted, with a bounded number of attempts
  (`ExplorePolicyTest.search`).
- It yields when an enemy becomes visible (`ExplorePolicyTest.yields_to_an_enemy`).
- The PR carries a `smoke` direction check.

## Design Notes

Constraints restated: non-negotiable #1 (only the screen), FR-27 and FR-28 (one Step per wait,
re-planned from each screen, no remembered intention), non-negotiable #8 (every mechanic cited).

**How to move.**
1. `MoveTo(frontier)`, one click that walks many cells. Rejected: the game walks the hero several
   turns on one input, and a human could not interrupt at each cell (the AC).
2. **One `Step` per wait along a breadth-first path, recomputed every wait.** Chosen: it is the AC,
   it re-plans from each screen, and the cost is one BFS over at most a few thousand cells.
3. A* toward one remembered target. Rejected: the remembered target is an intention, which the
   Memory may not hold. BFS to the nearest is as good on these maps.

**What to walk on.** The game turns a click on a stairs cell into a floor change, and a click on a
chest, tomb or remains into opening it (`Hero.java:1929-2017`, re-read here). So an innocent-looking
"Step" there is not a step, and the walkable set excludes them. It also excludes the chasm, which
jumps, the well, which drinks, armed traps, and occupied cells.

**How to search.**
1. Search in place N times. Rejected: an intentional search finds every searchable secret within
   its radius at once (`Hero.java:2546-2593`), so a second search from the same cell finds nothing.
2. **Search once from each cell beside a wall, nearest first, at most 12 per floor.** Chosen: every
   search can find something, and the bound caps the turns spent at 24 plus the walking.
3. Search from the dead ends only. Rejected: that is a heuristic without a cited basis, and a
   secret door can sit in any wall.

**Remembering the searches without remembering an intention.** The Memory may hold what was seen,
not what was done. A cell is "dwelt on" when the hero is seen on it at two waits in a row. That is
what a search looks like on the screen, and also what a rest, a wait or a refused step looks like.
The Policy treats a dwelt spot as searched, so a spot where the hero only rested is not searched
again. That is the price of not recording intentions, and the bound hides it.

**Stuck.** A Step the game refuses (a forge drawn as floor, `docs/rules/levels.md`) leaves the
hero in place. The same screen then gives the same Step forever. After three waits on one cell
outside a search, the Policy yields and the fallback breaks the loop.

**Streams keyed on the Policy's name.** Until now each Policy drew from a stream keyed on its
position in the list. Inserting `explore` at position 1 would have moved the fallback's stream.
Keying on the name makes adding a Policy leave every other Policy's draws alone. This change is why
the fallback's draws differ from 4.5's even on screens where explore does not act.

**Pre-mortem.**
- Explore never descends. The AC's "depth reached moving the right way" cannot move until story
  4.12 adds the descend Policy, so the direction check here measures exploration, not depth.
- Exploring moves the hero into mobs faster, and fighting is story 4.7. Deaths may come sooner in
  turns.
- A walkable-looking custom decoration can refuse a Step. The stuck rule bounds that to three
  waits.
- Walking onto a plain heap picks it up (`Hero.java:1974-1990`). That is harmless and wanted
  (story 4.8), but it takes a turn the Policy did not plan.

## Dev Notes

**Rules re-read at v4.0.0.** Three Rules the Policy rests on had been at needs-review since the
upgrade. Each was re-read at the pin and re-cited, Tier 1:
- `game-loop`, `Hero.handle(cell)`: `Hero.java:1929-2017`. v4.0.0 added two conditions to the
  transition clause, no plant on the cell, and from depth 26 only the regular entrance; the row now
  states them.
- `game-loop`, the intentional search cost: `Hero.java:211`, `:2505-2507`, `:2618-2629`, `:1917`.
- `visibility`, `Hero.search`: `Hero.java:2499-2519`, `:2546-2593`, `Foresight.java:32`.

`:codex:citations` reports no findings.

**Tests:** `:brain:test` passes, including `ExplorePolicyTest` (6). `ShatterfishRunTest`,
`BrainRulesIndexTest` and `StrategyLogTest` pass. In the `smoke` Runs of `ShatterfishRunTest`, the
explore Policy takes waits, and every one of them is a Step or a Search.

**Mutation battery: 10 of 10 killed.** The mutants:
- a transition, a chest or an armed trap made walkable;
- the chasm made walkable;
- an enemy in view ignored;
- depth-first instead of breadth-first;
- no search bound;
- searching the same spot twice;
- never yielding when stuck;
- a streak that never grows.

**For the direction check** (the parent runs it), measure on `smoke` against the 4.5 Brain, since
depth cannot move:
- the share of waits the explore Policy takes;
- cells ever seen per Run: the fog cells not UNKNOWN at the last wait, from the logs'
  Observation hashes. That is not logged directly; turns survived and waits per Run are.
- deepest floor, which is expected to be unchanged;
- turns survived.
