---
title: 'Story 4.3: safeTest, the worst-case check'
type: 'feature'
created: '2026-09-23'
status: 'review'
baseline_commit: '39d69bcec'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Story 4.2 gave the Brain odds over what each unidentified potion or scroll may be, but
no way to ask the question those odds exist for: if I try this item here, now, can it kill me? The
test-unknown-items Policy (story 4.10) must not drink a potion that is healing nine times in ten
and, the tenth time, liquid flame that burns the hero to death (FR-30).

**Approach:** `SafeTest`, a pure function in `brain`. It takes the candidate identities with their
probabilities and the Observation, and returns a verdict with the worst case, the mean and each
candidate's harm. The worst case of each identity comes from a hand-written table, read from the
pinned code and cited both in the code and in a `docs/rules/identification.md` row. The verdict
looks only at the worst case: refused when it is lethal, or when it disables or draws enemies with
one in view. Potions and scrolls enter as a `Beliefs.Guess` mapped to the Codex's class names;
wands enter as uncursed or cursed, because a wand's kind is never hidden but its curse is. No Policy
calls it yet.

## Boundaries & Constraints

**Always:**
- The inputs are what the screen shows: the hero's cell and its neighbours, the enemies in view,
  the hit points and the depth. Add to that the candidate identities from `Beliefs`, and the Codex's
  class names.
- Every harm is cited `path:line` at `v4.0.0`.
- Pure: no state, no randomness, no file.

**Ask First:** A worst case that needs a fact the Observation does not carry.

**Never:**
- Reading the item's class.
- Deciding on the mean.
- Changing an Action in this story; the direction check shows none changes.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Lethal worst, good mean | 90% healing, 10% liquid flame, 20 hp, depth 4, dry | refused: flame 40 >= 20; mean 4 | N/A |
| Water beside | the same, water on a neighbouring cell | safe: two hits of burning, 8 | N/A |
| Disabling worst beside an enemy | paralytic gas, frost, retribution or a cursed wand, a rat in view | refused | N/A |
| Same, alone | no enemy in view | safe | N/A |
| Wand with a hidden curse | curse not shown | two candidates, cursed at 30% | N/A |
| No candidates | empty list | refused as an error | IllegalArgumentException |
| A name the Codex lacks | a guess naming an unknown identity | error | IllegalArgumentException |

</frozen-after-approval>

## Code Map

- `shatterfish/brain/.../SafeTest.java`: `Candidate`, `Harm` and `Verdict`; `candidates(Guess,
  Knowledge)`, `wand(name, curseKnown, visiblyCursed)`, `of(candidates, observation)`, and `harm`
  (the cited table).
- `shatterfish/brain/src/test/.../SafeTestWorstCaseTest.java`: seven cases, each on its own 3x3
  screen built from `api` records.
- `docs/rules/identification.md`: one row, the table's citations at `v4.0.0`.
- `docs/architecture.md` (the brain row), `docs/fairness.md` (an enforcement row).

## Tasks & Acceptance

**Execution:**
- [x] `SafeTest` with the cited worst-case table.
- [x] `SafeTestWorstCaseTest`.
- [x] The rules row, and the architecture and fairness rows.
- [ ] Smoke direction check against the previous Brain (the parent runs it).

**Acceptance Criteria:**
- `safeTest` scores the worst case over the candidate identities, using Codex class names, the
  terrain and the visible enemies.
- It covers unknown potions, scrolls and wands with the same code (`one_code_path`).
- A lethal worst case is refused regardless of the mean (`lethal_worst_refused`,
  `hit_points_decide`).
- Standing next to water changes the verdict for a fire candidate (`water_beside`).
- The function is pure and testable without a running game.
- The PR carries a `smoke` direction check against the previous Brain.

## Design Notes

**Constraints restated:**
- Non-negotiable #1: the identity is a candidate, never read.
- #8: every harm is read from the pinned code and cited.
- `brain` opens no file and imports only `api` and `java.util`.

**Alternatives weighed for where the worst cases live:**
1. **Extend the Codex generator** to extract each identity's effect (damage expressions, buff
   durations, water rules) into a new table. Rejected for this story. The facts are code paths,
   not declarations: a `NormalIntRange` inside `Burning.act`, a `water[...]` test, `Buff.prolong`
   calls. A generator extracting them would be a second implementation of the game's rules, which
   non-negotiable #4 rules out, or a set of anchors as fragile as a hand table and much more
   expensive, with drift checks, citations and a Codex version bump.
2. **Put the facts in `Codex.Knowledge` as hand-written data read by the rig.** Rejected. The data
   would pass through the rig and the `api` for no gain, and it would look generated when it is not.
3. **A hand-written table in `SafeTest`, keyed by the Codex's class names, with every entry cited
   in the code and in a `docs/rules` row.** Chosen. The Codex still supplies the class names and
   the odds, through `Beliefs`. The table is small (six identities and the cursed wand) and honest
   about what it is: the rules row carries its citations, which the citation checker resolves at
   `v4.0.0`, and an upgrade's re-verification step re-reads it like every other row.

**Wand scope.** A wand's kind is never hidden: wands are not shuffled, so the name is the kind.
What is hidden is its level and its curse. A cursed wand's zap runs a random cursed effect instead
of its own (`Wand.java:753-765`). Two of the common and uncommon effects land on the user:
- Burn-and-Freeze can set the user burning (`CursedWand.java:220-230`).
- The lightning bolt strikes the user's own neighbourhood for up to 10 + depth/4 and stuns
  (`CursedWand.java:550-580`).

The rare and very rare effects (10% of cursed zaps, `CursedWand.java:163`) are not modelled. A wand
whose curse is hidden is two candidates, cursed at the generator's 30% (`Wand.java:562-563`),
which only moves the mean. The level is irrelevant to the hero's safety.

**Pre-mortem:**
- *The toxic-gas exposure is a guess.* A potion seeds 1000 units that spread over the room and
  outlast any walk out of it. Ten turns is an assumption, named as `GAS_TURNS`, and a later story
  should measure it.
- *"Disabled beside an enemy" is treated as lethal*, without modelling the enemy's damage. That is
  conservative by design: the worst case of being paralysed ten turns beside a monster is death,
  and the Codex has the mob rolls to refine this when a Policy needs finer odds.
- *Burning is 8 + 2 turns in the dry:* eight from Burning's duration, plus two while the seeded
  fire (amount 2) relights it. Beside water it is two hits: the hit, then a step into the water.
  That assumes the hero steps the next turn, which the Policy that calls this must then do.
- *Only the hero's own cell is considered*, because trying an item is drinking, reading or zapping
  from where the hero stands. Throwing an unknown potion elsewhere is a different test and a later
  story.

## Dev Notes

- `SafeTest` is additive: no existing class changed. `SafeTestWorstCaseTest` builds its own screens
  rather than extending `Screens`, which story 4.4 also edits, so the two merge cleanly.
- `docs/brain-rules.md` (the Brain's Rules index) arrives with story 4.4. Once both are merged, it
  gains a row for `SafeTest` pointing at the new `identification.md` row.
- Tests: `:api:test` and `:brain:test` pass (`SafeTestWorstCaseTest`, 7 cases).
- `:codex:citations`: no findings; the new rules row resolves at `v4.0.0`. `mkdocs build --strict`
  is clean.
- Mutation battery, 8 of 8 killed by `SafeTestWorstCaseTest`:
  - the mean decides;
  - water ignored;
  - a diagonal neighbour not counted as beside;
  - enemies ignored;
  - Rage harmless;
  - a cursed wand harmless;
  - lethal made strict (`>`);
  - toxic gas made flat in depth.
