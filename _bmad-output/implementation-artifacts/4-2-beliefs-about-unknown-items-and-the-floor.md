---
title: 'Story 4.2: Beliefs about unknown items and the floor'
type: 'feature'
created: '2026-09-23'
status: 'in-progress'
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
- [ ] Smoke direction check against the previous Brain.

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
- An unidentified strength potion that is identified later is counted in the set where it was
  identified, not the one where it was found.
- The pool-room recogniser could match another room. Checked against the pinned code: the rooms
  that set a chest on a pedestal are the pool room, the suspicious chest room, the sentry room, the
  traps room and the vault treasure rooms. None of the others paints water. The level's own water
  (`RegularPainter.java:366-377`) can still surround the suspicious chest room's pedestal, which sits
  at its room's centre (`SuspiciousChestRoom.java:61-63`). The recogniser therefore requires water
  on exactly three sides and a wall on the fourth, which is the pool room's shape (`PoolRoom.java:52`,
  `:59-89`) and never the centre of a room.
