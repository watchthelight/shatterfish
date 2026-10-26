---
title: 'Story 7.1: The heuristic-to-Rule link check'
type: 'feature'
created: '2026-10-26'
status: 'review'
baseline_commit: 'a303dcb7973b4c2e4589064245009861e07aab14'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent, do not modify unless human renegotiates">

## Intent

**Problem:** the Brain decides by dozens of named thresholds, odds and budgets, and nothing says which ones a Rule backs. "Every
heuristic is cited" (FR-18) cannot be counted, so it cannot go down.

**Approach:** declare each such constant with a `@Heuristic("slug")` annotation that carries a slug and nothing else, find them
by reflection in a test, require each slug to be a Rule row, a measured-heuristic row or the visible `unlinked` allowlist, and
render the result as a drift-checked table on `docs/brain-rules.md`. No play changes.

## Acceptance Criteria (from epics.md Story 7.1, with the tests that hold them)

1. A heuristic is declared with `@Heuristic` in `brain`; its slug must be a Rule row, a measured-heuristic row, or `unlinked`
   (Lore claim ids join in story 7.7). Held by `HeuristicLinkTest`: `every_heuristic_is_linked_or_listed`,
   `a_dangling_slug_is_refused` (the check bites and names the constant), `the_annotation_carries_only_a_slug`. The constants
   are found by reflection over the `brain` classes (`HeuristicIndex.annotated`), and
   `every_numeric_constant_is_declared_or_exempt` makes a bare new threshold fail.
2. The index page carries a table of each heuristic, its slug, linked Rule, Tier and Policy, rendered by `HeuristicIndex` and
   checked for drift by `the_table_has_not_drifted`.
3. Tuned thresholds link to the Results page or idea record that measured them (Tier 3; Tier 2 only with a named test, held
   by `measured_rows_are_sound`); the rest are on the `unlinked` allowlist, whose count is at the head of the table and which
   may not rise (`unlinked_count_does_not_rise`). The PR states the count before and after.
4. `BrainDeterminismTest` and the differential tests stay green, and the play is unchanged: `:rig:direction` on the
   `standard` tuning slice against `origin/main` shows identical columns. **Rig numbers:** identical by construction, given in
   the PR. No Sequential test applies, since no play changes.

</frozen-after-approval>

## Code Map

- `shatterfish/brain/src/main/java/org/shatterfish/brain/Heuristic.java`: the annotation (runtime retention, fields, one `value`).
- 71 constants in `Beliefs`, `Brain`, `Chests`, `Descend`, `Douse`, `Eat`, `Explore`, `Fight`, `Goo`, `Heal`, `Larder`, `Memory`,
  `OnePly`, `Pickup`, `Plunge`, `Ranged`, `SafeTest`, `TestItem`, `Traps`: one annotation line each, nothing else changed.
- `shatterfish/rig/src/test/java/org/shatterfish/rig/HeuristicIndex.java` and `HeuristicLinkTest.java`.
- `docs/brain-rules.md`: the "Heuristics" section, the measured-heuristic rows and the generated table.
- `shatterfish/brain/src/test/java/org/shatterfish/brain/BrainBoundaryTest.java`: `java.lang.annotation` joins the allowlist.

## Decisions

- **Micro-brainstorm of where the link lives.** (1) The annotation carries Rule, Tier and note: more than "a slug and nothing
  else", and Tier would be stored twice. (2) A separate YAML registry keyed by constant: renaming a constant slips past it.
  (3) The annotation carries a slug; the page holds the meaning (chosen): the Tier is read from the Rule page, the measured
  rows live beside the Rule rows. (4) Annotate the Policy class, not the constant: loses the per-constant count. (5) An
  annotation processor: build machinery for a one-line check. Pre-mortem: a threshold left bare would go uncounted, so a second
  test requires every numeric `static final` of a `brain` class to be annotated or exempt with a reason.
- **When a Rule counts as a link.** A constant is linked to a Rule only when a Codex figure sets its value or the Rule row states
  the mechanic it rests on. A constant whose javadoc says "an assumption" with no Rule or measurement behind the value goes
  on the `unlinked` allowlist, even where a nearby Rule explains the mechanic, because the Rule does not account for the number.
  Judgement calls are the list in `HeuristicLinkTest.UNLINKED`; link one by annotating it and deleting it there.
- **Measured rows.** Three Results pages played a value on the Rig (the Goo ground and wash ranges, the outpaced range and
  stairs, the piranha leave budget). They are Tier 3: no test in the repository reproduces a figure.
- **Policies column.** The declaring class's Policy name when it is a Policy the Brain arbitrates, else the class name. A
  cross-class usage scan would make every PR that adds a call site fail the drift check.
- **Boundary.** `java.lang.annotation` is added to the Brain's ArchUnit allowlist. The package holds only meta-annotations and
  their enums; it reads nothing and opens no channel. Fairness review covers this.
- **Out of reach.** Inline numbers with no name (the one-third hit point line in `Fight.java:993` and `Heal.java:118`, the
  `0.25` in `Heal.java:150`, `Equip.java:132`) are not found by reflection. Naming them would edit policy logic that other work is
  changing, so they are recorded here for story 7.10.

## Measurement

Before the story nothing was declared: 0 heuristics found. After: 71 found, 35 linked to a Rule, 5 linked to a measured row at
Tier 3, 31 unlinked (the baseline story 7.10 closes against). Twelve numeric constants are exempt as capacities, schema versions
and indexes.

## Verification

- `./gradlew build -Pshatterfish.testForks=1`: green except the one boundary rule the annotation tripped, fixed above.
- `./gradlew :rig:direction --args="--set standard --class WARRIOR --first 40 --salt-base 1000 --baseline origin/main --jobs 2"`:
  see the PR for the table.
- `mkdocs build --strict`.

## Results

- **Count.** Before: nothing declared. After: 71 heuristics found, 35 linked to a Rule, 5 linked to a measured row at Tier 3,
  31 unlinked.
- **No play changed.** `:rig:direction --set standard --class WARRIOR --first 40 --salt-base 1000 --baseline origin/main
  --jobs 2` (baseline `a303dcb79`, current `06d91fcb0`): every column identical (reach depth 5 28/40, Goo kills 25/40, median
  turns 2130.5, deaths by depth 0/1/7/4/3/17/6/2). All 40 Run logs are equal record for record once the three fields that
  cannot match across two commits are set aside: `chain` and `prev` (the hash chain starts from a header that carries the
  commit) and `think_ms` (wall time). The SHA-256 of the 40 normalized logs is `b74b950d39cd665aece1c753112509a35743da5af50fd6fba29e6489839c537f`
  on both sides. The table cannot show that the commit-bound fields would match, which is why the comparison is on the
  normalized logs.
- **Fairness review.** Verdict: no violation found. Two should-fix notes, both taken: a bite test for the allowlist widening
  (`BrainBoundaryRulesBiteTest.the_heuristic_annotation_opens_no_channel`: a plain `@Heuristic` constant passes every rule,
  reading the annotation back through `Class` and `Field` is rejected), and the note that `ApiBoundaryTest` stays stricter
  than the Brain's allowlist on purpose, so the two lists are not to be synced upward.
- **Lore claims.** Story 7.7 (PR #293) landed on `main` while this PR was open, so it was merged in and a slug may now be a
  Lore claim id (the file stem under `lore/<topic>/`, read by `HeuristicIndex.loreClaims`). The reverse check is in
  `HeuristicLinkTest.problems`: a heuristic linked to a claim fails unless the claim's `implemented_in` names `policy:<the
  heuristic's Policy>` (any target when the heuristic's class is not a Policy), and `a_dangling_slug_is_refused` covers both
  directions. No committed claim lists an `implemented_in` yet (all 144 are empty), so no heuristic links to a claim today and
  the table's Lore count is 0.
