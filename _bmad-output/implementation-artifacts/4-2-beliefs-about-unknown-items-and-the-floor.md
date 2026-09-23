---
title: 'Story 4.2: Beliefs about unknown items and the floor'
type: 'feature'
created: '2026-09-23'
status: 'review'
baseline_commit: 'main after story 4.1'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The Brain of story 4.1 remembers only how many waits it has served and the deepest
floor. It cannot say what an unidentified potion may be, that a floor holds an item it has not yet
seen, how many guaranteed strength potions and upgrade scrolls the current set of floors still owes,
or where an enemy that walked out of view was last (FR-29). Every Policy after this one reasons from
those facts.

**Approach:** The Brain is built on `Codex.Knowledge`, a new `api` value the rig reads from the
committed Codex tables (`CodexKnowledge`): the identifiable families (potions, scrolls, rings), each
with its appearances and its identities by deck weight; the items special rooms place; and the drops
guaranteed per set of floors. The Belief (`Memory` version 2) carries what the screen stops showing:
floor facts, guaranteed drops found per set, and enemy sightings. Candidate identities are recomputed
at every wait from the Codex, the appearances in view and the journal's identified list.
`Brain.beliefs(Observation, Belief)` exposes all four. No Policy consults them yet.

## Boundaries & Constraints

**Always:**
- Every input is general game knowledge (the Codex) or what the screen shows: the inventory, the
  heaps drawn, the journal, and the actors in view.
- An identity is inferred, never read.
- `brain` still opens no file. It is handed `Codex.Knowledge` at construction.
- The Belief is a function of the screens alone. The Brain's seed shapes choices, never beliefs.

**Ask First:** Any Belief that needs a field the Observation does not carry.

**Never:**
- Reading the item's class, the level's rooms, or a monster the screen does not show.
- Using a Belief in play in this story. It changes no Action, which is what the direction check shows.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Unidentified appearance in view | "crimson potion" in the backpack or on a heap | a distribution over the potion identities not yet identified, summing to one | N/A |
| An identity identified | the journal lists "potion of healing" | no appearance's candidates include it | N/A |
| A guaranteed identity | strength (deck weight 0) | a candidate, weighted as the family's commonest identity (assumption) | N/A |
| Pool room seen | a chest on a pedestal beside water | the floor holds a potion of invisibility, remembered after it leaves view, on that floor only | N/A |
| Guaranteed drop found | identified potion of strength appears in the backpack | found +1 in this set of floors; owed = 2 - found | holding it again is not finding it again |
| Enemy lost from view | a rat seen, then not | remembered at its last cell, stale | seen again: replaced |
| Foreign Belief | version 1 bytes, truncated or over-long bytes | refused | IllegalArgumentException |

</frozen-after-approval>

## Code Map

- `shatterfish/api/.../Codex.java`: `Candidate`, `Identities`, `RoomSpawn`, `Guarantee`, `Knowledge`.
- `shatterfish/rig/.../CodexKnowledge.java`: reads `items.json`, `decks.json`, `rooms.json` and
  `guarantees.json` after the manifest check; `compact` removes the tables' indentation for the
  canonical-JSON reader.
- `shatterfish/brain/.../Beliefs.java`: `fold` (Memory after a screen), `view` (the four beliefs),
  `identities`, `implied` (the pool room), `sightings`.
- `shatterfish/brain/.../Memory.java`: version 2. Facts, found, held and sightings, written through
  `Bytes`, the brain's own big-endian codec (no `java.io` or `java.nio` in `brain`).
- `shatterfish/brain/.../Brain.java`: built on `Codex.Knowledge`; `update` folds; `beliefs` views.
- `shatterfish/rig/.../Brains.java`, `RunOne.java`: `--codex` now yields `Codex.Knowledge`.

## Tasks & Acceptance

**Execution:**
- [x] `api` Knowledge records and `JsonRenderingTest` helpers.
- [x] `rig/CodexKnowledge` and `CodexKnowledgeTest` against the committed Codex.
- [x] `brain/Beliefs`, `Memory` v2, `Bytes`; `Brain` built on Knowledge.
- [x] `BeliefConsistencyTest`.
- [x] Smoke direction check against the previous Brain.

**Acceptance Criteria:**
- Given an unidentified item in view, then it carries a candidate set with probabilities weighted
  from the Codex deck weights and narrowed by the journal's identified list; they sum to one, and
  an identified identity leaves every other candidate set (`BeliefConsistencyTest`).
- Given a pool room seen, then the floor holds a potion of invisibility (`BeliefConsistencyTest.a_pool_room`).
- The chapter counters track the guaranteed strength potions and upgrade scrolls (`chapter_counters`).
- An enemy seen and then lost is remembered with its last cell and marked stale (`remembered_monsters`).
- The PR carries a smoke-set direction check against the previous Brain.

## Design Notes

Constraints restated: non-negotiable #1 (the identity is inferred from the appearance and the
journal, never read), `brain` opens no file, and the Belief is reproducible from the screens.

Alternatives weighed for how the Codex reaches the Brain:
1. **Hand the Brain the full typed Codex tables** (`Codex.Decks`, `Codex.Rooms`, `Codex.Guarantees`).
   Rejected: it needs a reader for about thirty record types with their citations, and the Brain
   would carry far more than it reasons from.
2. **Let the Brain parse the JSON text its caller hands it.** Rejected: the parsing would live in
   `brain`, and it would duplicate the rig's reader.
3. **A digest `api` value, `Codex.Knowledge`, read by the rig from the committed tables.** Chosen.
   Its records hold only what a Brain reasons from, it grows by adding a record per story, and the
   rig's reader is tested against the real Codex.

Where candidate identities live:
1. Stored in the Belief and updated incrementally.
2. **Recomputed at each wait from the Codex, the appearances in view and the journal.** Chosen:
   the journal is the game's own record of identification history, so storing it again would only
   create a second copy that could disagree.
3. A hybrid of the two, with a cache. Rejected as premature.

Pre-mortem:
- The strength and upgrade weight is an assumption. The decks weight them zero because a guarantee
  places them (`Level.java:228-233`). They are weighted as the family's commonest identity, stated
  in the code, and left to a later story to measure.
- A chapter counter counts a rise in the identified quantity held, so dropping an item and picking
  it up again would count it twice. The Brain does not drop items yet.
- The pool-room recogniser could match another room. Checked against the pinned code: the rooms
  that set a chest on a pedestal are the pool room, the suspicious chest room, the sentry room, the
  traps room and the vault treasure rooms. None of the others paints water. The level's own water
  (`RegularPainter.java:366-377`) can still surround the suspicious chest room's pedestal, which sits
  at its room's centre (`SuspiciousChestRoom.java:61-63`). The recogniser therefore requires water
  on exactly three sides and a wall on the fourth, which is the pool room's shape (`PoolRoom.java:52`,
  `:59-89`) and never the centre of a room.

## Dev Notes

**Reviews.**
- Fairness reviewer: PASS. Two test gaps it named (a non-enemy actor, a chest's contents) are covered
  by the existing `alignment == ENEMY` filter and `Observer.java:303`; no leak.
- Lens review (edge case, verification gap, correctness against the game) found real defects, all fixed:
  - **Found unidentified, then identified.** Most heroes meet the first strength potion unidentified,
    and drinking identifies it as it is consumed (`PotionOfStrength.java:44`), so the first draft never
    counted it. The memory now sets aside every rise in an unidentified appearance of a family a
    guarantee places into, per set. When the guarantee's identity first appears in the journal, and
    exactly one appearance held a wait ago is held no more, the finds set aside under that appearance
    are the guarantee's, in the sets they were picked up in. With two or more such appearances, which
    one it was cannot be told, and nothing is counted (`found_unidentified`).
  - **Heap titles.** A heap shows its top item's `title()` (`Observer.java:302`), which adds " x2" for
    a stack and a signed level (`Item.java:485-497`). Stacked heaps matched no appearance.
    `Beliefs.untitled` strips both.
  - **Distinct appearances are distinct identities.** The first draft gave every appearance in view
    the same weight-over-total odds, which is no joint distribution at all. The appearances are
    shuffled uniformly per Run, and each appearance in view is taken as one draw by deck weight, so
    the joint weight of an assignment is the product of the weights it assigns. Each appearance's odds
    are its exact marginal, computed by a dynamic programme over subsets of at most 12 identities.
    With every remaining appearance in view it is uniform (`distinct_identities`, `all_in_view`).
  - The test fixture's scroll appearances now read as the game draws them ("scroll of KAUNAN",
    `items.properties:1137`).
  - Memory records refuse negative values; `CodexKnowledge` refuses a Codex with no boss depth or
    without the two guaranteed drops the Brain counts.
  - Sightings are matched to the nearest remembered one of the same name; the cap and two same-named
    enemies are tested (`many_monsters`); a non-empty memory round-trips (`round_trip`).
- **Not changed:** a killed enemy stays remembered as a stale sighting. Dropping a sighting whose
  last cell is in view would also forget an enemy that walked out of view, which is what the
  acceptance criterion asks to remember. Reading kills from the log is recorded in `docs/ideas.md`
  for the fight Policy.

**Direction check (rig numbers).** `smoke`, 25 triples, each played by the 4.1 Brain (main at `4e95c0ec0`, built in a separate worktree) and by this branch under the same fixed salt
(1000 + the triple's index), through `RunOne` directly: **25 of 25 Runs played the identical Action
sequence, 1,688 waits.** The Beliefs change no Action, as intended. The script is
`direction.py` in the session scratchpad; the Runner's own `--against` cannot compare two builds.
