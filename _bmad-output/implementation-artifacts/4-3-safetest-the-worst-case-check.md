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
| Water beside | the same, water on a neighbouring cell | safe: three hits of burning, 12 | N/A |
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
- `shatterfish/brain/src/test/.../SafeTestWorstCaseTest.java`: thirteen cases, each on its own 3x3
  screen built from `api` records.
- `docs/rules/identification.md`: one row, the table's citations at `v4.0.0`.
- `docs/architecture.md` (the brain row), `docs/fairness.md` (an enforcement row).

## Tasks & Acceptance

**Execution:**
- [x] `SafeTest` with the cited worst-case table.
- [x] `SafeTestWorstCaseTest`.
- [x] The rules row, and the architecture and fairness rows.
- [x] Smoke direction check against the previous Brain.

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
   the odds, through `Beliefs`. The table is small (seven identities and the cursed wand) and honest
   about what it is: the rules row carries its citations, which the citation checker resolves at
   `v4.0.0`, and an upgrade's re-verification step re-reads it like every other row.

**The acceptance criterion's `safeTest(item, cell)` became `SafeTest.of(candidates, observation)`,
judged at the hero's cell.** Trying an unknown item means drinking, reading or zapping it where the
hero stands, so the cell is the hero's own, and the Observation supplies its terrain, the enemies in
view, the hit points and the buffs. The item enters as its candidate identities, built only through
`SafeTest.candidates` (a potion or scroll's `Beliefs.Guess`) or `SafeTest.wand`. Throwing an unknown
potion at another cell is a different test, left to a later story.

**Wand scope.** A wand's kind is never hidden: wands are not shuffled, so the name is the kind.
What is hidden is its level and its curse. A cursed wand's zap runs a random cursed effect instead
of its own (`Wand.java:753-765`). The common and uncommon effects (90% of cursed zaps,
`CursedWand.java:163`) that can reach a hero standing by the bolt's end are all scored, and the
worst of them is the harm:
- burning on the user (`CursedWand.java:220-230`), or a burning trap's fire at the bolt's end
  (`CursedWand.java:302-320`, `BurningTrap.java:47-49`), scored as liquid flame;
- toxic or paralytic gas at the bolt's end (`CursedWand.java:280-299`);
- the lightning bolt through the user's neighbourhood, for up to 10 + depth/4 and a stun
  (`CursedWand.java:550-580`);
- a conjured bomb of radius 1, for up to 12 + 3 x depth before armour (`CursedWand.java:518-525`,
  `Bomb.java:93-95`, `:160`, `:196-197`), the largest from depth 2 when the fire is put out;
- a health transfer that costs the user 2 x depth half the time (`CursedWand.java:477-492`).

The rare and very rare effects are not modelled. A wand whose curse is hidden is two candidates,
cursed at the generator's 30% (`Wand.java:562-563`), which only moves the mean. Only the hidden curse
is scored. What a known wand does by its own effect is the caller's to weigh, since its kind is
shown: a wand of lightning's arc strikes its user for half (`WandOfLightning.java:92-93`). The
level is irrelevant to the hero's safety.

**Pre-mortem:**
- *The toxic-gas exposure is a guess.* A potion seeds 1000 units that spread over the room and
  outlast any walk out of it. Ten turns is an assumption, named as `GAS_TURNS`, and a later story
  should measure it.
- *"Disabled beside an enemy" is treated as lethal*, without modelling the enemy's damage. That is
  conservative by design: the worst case of being paralysed ten turns beside a monster is death,
  and the Codex has the mob rolls to refine this when a Policy needs finer odds.
- *Burning is 8 + 2 turns in the dry:* eight from Burning's duration, plus two while the seeded
  fire (amount 2) relights it.
  - On water it is two hits. The fire burns twice (`Fire.java:52-62`), and each burn relights
    Burning and clears its acted mark (`Burning.java:214-218`), which skips the water check.
  - Beside water it is three hits. The step lands in the neighbouring fire too, which relights once
    more. That assumes the hero steps the next turn, which the Policy that calls this must then do.
  - A levitating hero gets no water credit, and neither does a water cell another creature stands on.
- *Only the hero's own cell is considered*, because trying an item is drinking, reading or zapping
  from where the hero stands. Throwing an unknown potion elsewhere is a different test and a later
  story.

## Dev Notes

- `SafeTest` is additive: no existing class changed. `SafeTestWorstCaseTest` builds its own screens
  rather than extending `Screens`, which story 4.4 also edits, so the two merge cleanly.
- `docs/brain-rules.md` (the Brain's Rules index) arrives with story 4.4. Once both are merged, it
  gains a row for `SafeTest` pointing at the new `identification.md` row.
- Tests: `:brain:test` passes (`SafeTestWorstCaseTest`, 13 cases); the rig's `SafeTestCodexTest` passes.
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

**Rig numbers (direction check).** Set `smoke`, 25 triples: the 4.1 Brain on main `4e95c0ec0`
against this branch at `fcb7bd95a`, each triple under the same fixed salt through `RunOne`.
**25 of 25 Action sequences are identical (1,688 waits).** No Policy calls `SafeTest`.

### Review

Fairness reviewer: PASS. Its three should-fixes are done:
- **Drift guard.** `SafeTest.CLASSES` lists the table's item classes, and the rig's
  `SafeTestCodexTest` holds each to the pinned Codex's `items.json`. `docs/UPSTREAM.md`'s list of
  what moves with the pin names the table, and the rules row says to re-read it at every upgrade.
- **Scaling depth.** Damage uses the game's scaling depth, 26 while the hero shows the Ascension
  challenge's "amulet's curse" (`Dungeon.java:447-452`, `actors.properties:85`).
- **Candidate construction.** `SafeTest.of`'s javadoc says candidates are built only through
  `candidates` and `wand`, and `of` refuses odds that are not a distribution.

The lens review's claims were each checked at the pinned code before changing anything:
1. **The cursed wand's worst case was understated.** The explosion, the health transfer, gas and
   a burning trap at the bolt's end are now in the maximum. At depth 13 beside water the blast (51)
   is the worst, where the old model said the burn (18) was (`cursed_blast_deep`).
2. **Liquid flame on water is two hits, not one** (`Fire.java:52-62`, `Burning.java:218`). Beside
   water it is three, a correction the review did not name: the step lands in the neighbouring fire
   the potion also seeded, which relights once more.
3. **"An uncursed wand never fires at the hero" was false for lightning.** The claim is narrowed:
   only the hidden curse is scored, and a known wand's own effect is the caller's.
4. **Lullaby was missing.** It disables. The review cited it as sleeping the reader directly; the
   code makes the reader drowsy (`ScrollOfLullaby.java:55`) and then asleep (`Drowsy.java:54-55`).
5. **Edge cases:**
   - levitation takes away the water credit, and an occupied water cell is no refuge;
   - the one decoration drawn as water (`DungeonTileSheet.java:436`, not `:442`) is a javadoc note;
   - Rage with nobody in view is safe by decision, as documented;
   - the odds must be non-negative and sum to one;
   - the unused `enemies` helper is removed.
6. **Tests:** the blast's depth scaling (`cursed_blast_deep`), worst-first ordering with an enemy in
   view (`ordering_with_enemies`), lullaby, flight and scaling, occupied water, and the odds check.
   `SafeTestWorstCaseTest` has 13 cases.
