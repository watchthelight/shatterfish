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
- `shatterfish/brain/.../Memory.java`: version 3. It adds:
  - `at`: where the hero stood at the last wait, by depth, branch and cell;
  - `streak`: waits in a row on that cell;
  - `calm`: whether the last screen had no Prompt and no enemy in view;
  - `dwelt`: cells stood on at two waits in a row, the first calm, per floor;
  - `blocked`: cells a refused Step pointed at, per floor.
- `shatterfish/brain/.../Beliefs.java`: `fold` records them.
- `shatterfish/brain/.../Brain.java`: the Policies are answer-prompt, explore, fallback. Each
  Policy's stream is keyed on its name.
- `docs/brain-rules.md`: rows 8 to 15 (after story 4.3's row 7).
- `docs/rules/game-loop.md` (`Hero.handle(cell)`, intentional search cost) and
  `docs/rules/visibility.md` (`Hero.search`): re-read at v4.0.0 and re-cited, Tier 1.

## Tasks & Acceptance

**Execution:**
- [x] `Explore` Policy, Memory v3, and wiring into the Brain.
- [x] `ExplorePolicyTest`; `ShatterfishRunTest` checks that explore's waits are Steps or Searches
  and that it takes some.
- [x] Rules index rows and the three rules re-read at v4.0.0.
- [x] Smoke direction check (parent), and again after the review fixes.

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
2. **Search from a cell only where it reaches a wall no search has covered, nearest first, at most
   12 per floor.** Chosen: every search can find something, and the bound caps the turns spent at 24
   plus the walking. This is the review's version; see the Dev Notes.
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
- Explore never descended in the first version, so the AC's "depth reached moving the right way"
  could not move. The review pulled a minimal descent forward from story 4.12; see the Dev Notes.
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

## Review

The fairness review passed. The lens review found the following; each claim was checked at the
pinned code before it was fixed.

1. **Overlapping searches (high).**
   - Depth 2's entrance room hides every unlocked door while the guidebook's Searching page has not
     been found (`RegularPainter.java:268-274`, `EntranceRoom.java:133-145`), which is every
     headless Run.
   - A non-Rogue search reaches only the square around the hero (`Hero.java:2506`), and "nearest
     spot not yet stood on" picked spots whose reach overlapped, so twelve searches covered little.
   - Fix: a wall is covered once a search was made within the searcher's reach of it (one, two for
     the Rogue). A spot is worth a search only when it reaches an uncovered wall, and spots whose
     uncovered walls have never-seen cells within two of them come first.
   - `docs/rules/levels.md` gains the entrance-room row (Tier 1) and `docs/brain-rules.md` row 15.
2. **Any pause counted as a search, and the stuck rule fired after any pause (high).**
   - A cell is now recorded as searched only when the screen before was calm: no Prompt, no enemy
     in view, a screen explore acts on.
   - The Policy yields on exactly the wait where the streak reaches `STUCK - 1`, so the fallback
     gets one wait and explore then acts again.
3. **A refused Step livelocked (medium).** On the wait where the Policy yields, `fold` records the
   cell of the Step its plan takes on that screen as blocked on that floor. That cell is a function
   of the screen and the memory, not an intention. The walkable cells leave blocked cells out, so
   the next plan goes round.
4. **The acceptance criterion's depth (the parent's decision).**
   - Stated deviation: the minimal part of story 4.12 is pulled forward. When no frontier is
     reachable and the searches are spent or reach no uncovered wall, the Policy walks to the
     regular exit and onto it; standing on it, it takes the offered `Descend`.
   - A click on a transition cell with no enemy in view travels (`Hero.java:2000-2007`), and the
     Policy acts only with none in view. It never descends from a sealed floor.
   - Story 4.12 refines when to leave a floor. The frozen intent is unchanged. Brain-rules row 14
     covers it.
5. **Search hunger.** An intentional search on an unlocked floor adds 4 hunger (10 with a cursed
   talisman), taken from Well Fed first (`Hero.java:212`, `:2621-2627`, `Hunger.java:136-146`). The
   game-loop row and brain-rules row 11 now say so.
6. **Floors keyed by branch.** Searched and blocked spots carry the depth and the branch, so
   Mining (`branch` 1) and depth 11's main floor are different floors.
7. **Allies.** An ally's or a neutral's cell is walkable past the first Step, because a click on one
   swaps places (`Char.java:246-270`, `Mob.java:880-882`). Only an enemy's cell is left out.
8. **Tests.**
   - `ShatterfishRunTest` checks that an explore wait's Action matches its reason: a Step names a
     frontier, a search spot or the exit and a distance; a Search is counted against the bound;
     otherwise it is `Descend`, reason "descend". A mismatch fails.
   - The tie-break is pinned: `deterministic` asserts the Step.
   - `detour` asserts the one Step.
   - New cases: the well excluded, a plain heap and a disarmed trap walkable, an ally walkable and
     an enemy not (`what_it_walks_on`); covered walls and the Rogue's reach (`covered_walls`); a
     pause beside an enemy is no search; 256-spot eviction; the descent, on and off the exit, and
     sealed; other floors and branches; a refused Step goes round.

**Battery after the review: 17 of 17 killed.** The mutants: transitions, chests, armed traps or the chasm made walkable; allies blocked; enemy ignored; depth-first; no search bound; coverage ignored; the Rogue at radius 1; the promising preference ignored; the stuck rule as `>=`; blocked cells never excluded; a pause counted as a search; descending from a sealed floor; never taking Descend; the branch ignored.

**Rig numbers.**
- Before the review: `smoke`, 25 triples, fixed salts, the 4.5 Brain against `35a20f05e`.
  - The median turns survived fell from 1,374 to 27.
  - The mean score rose from 78 to 99; the mean deepest floor stayed at 1.04.
  - Explore took 49% of waits.
- The fall in survival is expected until story 4.7 adds fighting. After the review fixes, the
  parent reruns the check against the pushed head.
