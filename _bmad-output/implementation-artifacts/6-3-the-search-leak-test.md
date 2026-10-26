---
title: 'Story 6.3: The search leak test'
type: 'feature'
created: '2026-10-26'
status: 'review'
baseline_commit: 'd48e2d628'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent, do not modify unless human renegotiates">

## Intent

**Problem:** ADR-0010's choice rule 1 says that if the search leak test fails, no engine-rollout design ships. No such
test exists. The scrubber and the rollout host (story 6.2) are held row by row, and the one-ply model (story 6.1) has a
first fairness test over three hand-made world pairs, but nothing yet asks the question FR-13 asks of a whole search:
does the Decision depend on anything the player cannot know?

**Approach:** `SearchLeakTest`, in the harness test source set. For each mid-fight snapshot of the `smoke` set it builds
alternate worlds by scrubbing the live snapshot with N different samples, checks that every alternate shows the player
the same Observation, runs each search in the original and in every alternate with the same search seed and wait index,
and requires the Decisions to be identical as canonical bytes. It covers the one-ply model and a probe search that
spends its budget on rollouts through the `Simulator`, names each hidden element as its own case, and carries negative
controls that plant a leak and require the same comparison to catch it.

## Acceptance Criteria (from epics.md Story 6.3, with the tests that hold them)

1. For each mid-fight snapshot of the `smoke` set: N at least 8 alternate worlds from N different samples; every
   alternate's Observation hash equals the original's; each search run on the original and on each alternate, the same
   Run salt and wait index; the Decisions identical as canonical bytes (chosen Action, alternatives in order, scores,
   one-line reasons). `SearchLeakTest.no_search_depends_on_the_hidden_state`.
2. Each of the true seed, unseen mob positions, unidentified item classes, hidden traps and secret doors is its own case,
   which fails with its own message when that element leaks. `SearchLeakTest.no_hidden_element_moves_a_decision` (the
   cases) and `SearchLeakTest.every_element_case_can_fail`, `reading_the_seed_is_caught`,
   `reading_an_unseen_mob_is_caught` (the failures).
3. It covers every search the program has: the one-ply model, and a probe search that rolls out through the
   `Simulator` (`ProbeSearch`, a test fixture).
4. Negative controls, a planted leak in each of four forms caught: a search handed the live snapshot's handle with the
   flag forged (`a_forged_handle_is_caught`), a search that reads the true seed (`reading_the_seed_is_caught`), a probe
   whose scrubber leaves one row out (`a_scrubber_missing_a_row_is_caught`), a search that reads an unseen mob's
   position (`reading_an_unseen_mob_is_caught`).
5. The rollout host's refusal of an unscrubbed or foreign handle, asserted separately: `RolloutHostRefusalTest`.
6. The story file and the test's Javadoc state what the test does not claim (below). The test runs in CI on every pull
   request, is in the guarantees table of `docs/fairness.md` with its test name, and its result on the `smoke` set is
   published as a Results page (`docs/results/2026-10-26-6-3-the-search-leak-test.md`).

</frozen-after-approval>

## Constraints that bind this design

- **Information parity is the point.** Nothing the test builds may give a search a way to see the truth; the controls
  that do (the leaky host, the peeks) live in the test source set only, and the main source set gains nothing.
- **A test that cannot fail proves nothing.** Every comparison has a control that plants the leak it is meant to catch,
  and the control fails the test when it is not caught.
- **No game imports in `brain`** (ArchUnit, `BrainImportsNoGameCodeTest`). The probe is not in `brain`: it needs a
  `Simulator` and a `SnapshotStore`, both of which are reached through the harness.
- **Mechanics claims cite pinned code.** The invisible and disguised enemy cases cite
  `Observer.actors()` and `Observer.hiddenMimic` (`Mimic.java:62-64`, `:112-118`; `CharSprite.java:401-407`), as the
  Observer's own Javadoc does.

## Design

### What the test builds

- **Fixtures.** Seven Runs of the `smoke` set (never `goo` or `holdout`) are tried: the first two Warriors, whose
  fights the differential test also uses, and the first of each other class. The Cleric's Brain dies at wait 273 before
  a fight the scrub accepts, so six give a snapshot, and the test fails below six. Each is played by the real Brain to
  its first wait with an enemy in view (and at least 30 waits in) whose snapshot the scrub accepts. At that wait the fixture plants a
  hidden trap (`FrostTrap`) on free floor in view and a secret door in a wall in view, which read as floor and wall, so
  that the secret-trap row has something to replace in every snapshot; the Observation is asserted equal before and
  after planting.
- **Alternates.** `SnapshotScrubber.scrub(live, sample n)` for n = 1.., eight accepted. A sample is the scrubber's
  `ScrubSample` (a dungeon seed, a rollout salt, a stream seed) with up to two remembered mobs placed on cells the
  Observation's map shows as visited and not in view, so the mob row has something to put back.
- **Worlds.** Each alternate is restored into the driver through `SnapshotStore.restoreScrubbed`, stepped to the
  snapshot's wait and observed. The test asserts `Observation.hash()` equal to the live Run's, and as a precondition
  it throws a different error than a leak does (`PRECONDITION`), so a broken alternate is never mistaken for a pass.
- **Held elements.** The test reads each of the five hidden elements in the original and in every alternate
  (`HiddenPeek`) and asserts that every element the snapshot holds differs, and that across the six snapshots all five
  were held. "Differ in every row" is thereby shown here for the elements the test names, and row by row by
  `RedeterminationDifferentialTest`.

### The searches

- **The one-ply model.** `Brain` with `Model.ONE_PLY` on the real Codex, `decide(observation, update(observation, null))`.
  It reads the Observation and the Codex, so equality is nearly a type fact; the test holds it to bytes anyway, and
  checks it is not vacuous (at least one wait is the one-ply policy's).
- **The probe search** (`ProbeSearch`, the fixture the AC calls for). From the Observation alone it picks up to three
  candidate Actions (attacks, then a rest, a search, steps). In the world it stands in, it takes a snapshot, scrubs it with
  two samples drawn from (search seed, wait index, sample number, and the visited cells of the map), rolls each candidate
  and two rests out of every copy through `RolloutHost`, scores the screen each rollout ends on (the hero's hit points, the
  enemies' bars, a death), ranks the candidates by the sum, and writes a `RunLog.Decision` whose reasons name the number
  of rollouts, deaths, refused Actions and refused scrubs. A refused scrub is skipped, never replaced by another draw.
- **Same Run salt and wait index.** All worlds are played in one driver, so the Run's declared salt is one value; the
  wait index is the snapshot's; the search seed is a constant (`9`). Nothing the probe draws comes from the world.
- **Canonical bytes.** The Action's string plus `RunLogJson.canonical(new RunLog.Shadow(wait, decision))`, UTF-8,
  compared with `Arrays.equals`; the failure prints both texts.

### The cases

- *All rows* (`N = 8`): alternates from the complete scrubber.
- *Each element alone* (3 alternates each): a `SnapshotScrubber` with only the element's rows (`HiddenPeek.rows()`), so
  the worlds differ from the live Run in that element and nowhere else, and a failure names it:
  `SEARCH LEAK [the true seed]`, `[unseen mob positions]`, `[unidentified item classes]`, `[hidden traps]`,
  `[secret doors]`. Hidden traps and secret doors share one row (`SECRET_TRAPS_AND_DOORS`), so they are two cases over
  the same worlds, told apart by their controls.
- *Invisible and disguised enemies* (`OnePlyLeakTest`, the E6.1 gap): an invisible enemy in view (drawn faint and
  flagged, so the flag is the player's and nothing else is) under every AI state and a hit point away; an invisible enemy
  out of view, hunting; a hidden mimic against the real chest it wears (chest and locked chest), with the mimic then
  revealed as the control that the bytes move. The scrubber refuses a snapshot with a chest in view (audit B2), so the
  scrubber's alternates cannot hold a disguised enemy, and these cases are in-place pairs as the first version of the
  file is.

### The negative controls

| # | Planted leak | How | Caught by |
|---|---|---|---|
| 1 | A search handed the live snapshot's handle with the scrubbed flag forged | `LeakyHost` (test source set) believes the flag and restores the live snapshot under it; the probe rolls out from `new SnapshotHandle(live.id(), live.k(), true)` | the world's own rolls differ between worlds |
| 2 | A search that reads the true seed | the probe adds `Dungeon.seed` to its scores (`HiddenPeek.TRUE_SEED`) | the element case `[the true seed]` |
| 3 | A probe whose scrubber leaves one row out | each of the 13 rows in turn, through `LeakyHost` (the real host refuses an incomplete copy) | `MOBS_OUT_OF_VIEW` and `MOB_AI_AND_HP` always (required); `UNKNOWN_CELLS` and `SECRET_TRAPS_AND_DOORS` on some layouts; the rest are listed on the Results page with why |
| 4 | A search that reads an unseen mob's position | the probe adds the sum of unseen mobs' cells | `[unseen mob positions]` |
| 5 | The other three elements read | one peek each | `[unidentified item classes]`, `[hidden traps]`, `[secret doors]` |
| 6 | A scrub refusal that tracks the hidden state | the probe's scrub refuses when `Dungeon.seed` is even | `[refusal as evidence]`: the Decision records the refused count |
| 7 | The comparison itself | the hero's hit points, which the screen shows, cut to a quarter | the one-ply Decision moves, or the equality above could not fail |

`RolloutHostRefusalTest` holds the real host's refusals: an unscrubbed handle (refused by `Simulator.simulate` before
any host runs), the live snapshot's id with the flag forged, an id nobody made, another store's scrubbed copy, a copy
made with a row missing, a copy at the wrong wait, null arguments, and that no refusal message carries the salt; and
that the genuine copy is accepted and the live Run reads as it did.

### Alternatives considered

1. **Perturb the live game in place only** (as `OnePlyLeakTest` does). Rejected as the main method: it covers what its
   author thought of, and the scrub table is the definition of "hidden" (ADR-0009). Kept for the invisible and disguised
   enemies, where the scrubber refuses and in-place is the only way to build the pair.
2. **Alternates from an independent mutator** (a test-side "randomise all hidden state" that does not use the scrubber).
   Rejected: a second implementation of the table, which would drift from the scrubber and prove nothing about it. The
   cost is a circularity this story states rather than hides: the alternates are the scrubber's, so a hidden element the
   scrubber leaves alone is not varied, and is outside the proof (below).
3. **Compare Decisions by hash.** Rejected by the AC: a hash says that two Decisions differ and not how; the failure
   message prints both texts.
4. **Put the probe in `brain` as a second search Policy.** Rejected: it would need `Simulator` and a store in the
   Brain's module, and the AC calls it a test fixture. It lives beside the test.
5. **One long rollout per candidate (a real horizon).** Rejected for cost and for the point: the probe exists to
   exercise the rollout path under the comparison. Three turns reach the rows the mobs and cells use; the table of
   controls says which rows a longer line would be needed for.
6. **Treat a refused nested scrub as "retry with the next sample".** Rejected: the next draw would then be a function of
   the refusal, which is hidden-state-dependent by a fraction of a bit (ADR-0009's amendment). The probe skips and says so
   in its reasons, which is how control 6 sees a refusal that tracks the truth.

### Pre-mortem

It is a year on, a search has shipped, and it was reading the hidden state. How did this test miss it?

| Cause | Guard |
|---|---|
| The leak was in a hidden element outside the table | Stated as the test's limit; the fairness reviewer reads every search diff; the table grows with the audit, and the test follows because alternates come from the scrubber |
| The leak needs a long line (a descent, a trap triggered, a gas cloud) | The probe's rollouts are three turns, and the Results page lists the rows control 3 did not reach. A search with a real horizon adds its own control for each row it reads on a long line; this test does not claim them |
| The alternates were all alike (the scrubber changes little) | The test reads the five elements in every alternate and fails when a held one did not move; the differential test holds the other rows |
| The Decision compared was trivial (all ties, one candidate) | The Decision includes scores and reasons; the main test counts rollouts and fails when none ran, and when the one-ply model never took a wait; control 7 shows the comparison moves when the screen does |
| A fixture was refused and the others looked fine | Six snapshots from five classes (the Cleric gives none, and the test fails below six); the test prints each fixture and each that did not arrive |
| The rollout host stopped refusing | `RolloutHostRefusalTest`; the forged-handle control uses the leaky host on purpose, so the real host's refusal is asserted only there |
| The shared salt hides a leak (all worlds share the Run's declared salt) | Named below; `RedeterminationDifferentialTest` holds the `ROLLOUT_SALT` row by reading the seed the copy rolls by |
| The test is slow and gets disabled | A few minutes on the described machine for the class (the all-rows case about one, the element cases two, control 3 about two), in the harness test task that already runs on every pull request; the controls stop at the first catch |

## What a search must not trust (the residuals of story 6.2, restated for the first search that rolls out)

These are not tested here, because each is a property of the scrub that a search has to respect and no comparison of
worlds can vary:

- **A refusal is never evidence.** Some refusals depend on the true state (an unsolvable fog, an item no roll fits). A
  search treats a refused scrub as "no sample": it does not retry with another draw, does not read the count as a signal,
  and does not branch its Decision on it. Control 6 shows a refusal that tracks the truth is visible in the Decision; it
  cannot show a refusal that depends on the truth and is never seen, which is why the rule is also a rule.
- **A rollout that descends** generates its floor from the sample's seed, with `limited_drops` zeroed, so drops on
  that floor are biased; a search must not weigh what it finds there as the game's.
- **A copy is the screen plus a sample, not the world.** Re-created buff icons, true actor ids, mob cooldowns reset to
  ready, gas volume in view and the respawn clock are the true ones or defaults. A rollout is a draw from a belief and
  not a forecast.
- **The scrubbed copy sits in the real save slot for the length of a rollout.** A crash mid-rollout leaves it there.

## What the test does not claim

It proves independence from the hidden state it can vary, which is the rows of the ADR-0009 table and nothing beyond. A
hidden element outside the table is outside the proof until the table grows. A snapshot the scrub refuses (a chest in
view, a boss floor, a quest NPC in view, a held trinket) is not searched from, so it is not in the proof either. The Run's
declared salt is one value across every world of one driver, so a search that reads it is not caught by this comparison
(the differential test holds the row). The proof is of the Decisions of the two searches the program has, over six
snapshots, three-turn rollouts and the samples drawn; it is a necessary condition for fairness and not a sufficient one,
which is why the fairness reviewer still reads every search diff.

## Deferred

- The belief sampler that writes a `BeliefSample` from Codex priors (the probe draws its samples from its seed and the
  map). The leak test drives whichever sampler arrives through the same `Search` seam.
- A control for each remaining row on a longer line (a descent for `DUNGEON_SEED`, a triggered trap for the secret
  trap row, a long fight for `HERO_HIDDEN_BUFFS`): the first search with a real horizon adds it.
- A scrubbed disguised enemy: the scrub refuses chests in view (audit B2); the in-place pairs of `OnePlyLeakTest` stand
  in until a mimic bundle can be built.

## As built

- `ProbeSearch`, `LeakyHost`, `HiddenPeek`: test fixtures in `harness/src/test/java/org/shatterfish/harness/agent`.
- `SearchLeakTest` (9 tests), `RolloutHostRefusalTest` (8), three new tests in `OnePlyLeakTest`.
- `docs/fairness.md`: the Search section and the guarantees table. ADR-0010: an amendment. Results page.
- No main-source change; no upstream file touched; no hook row.
