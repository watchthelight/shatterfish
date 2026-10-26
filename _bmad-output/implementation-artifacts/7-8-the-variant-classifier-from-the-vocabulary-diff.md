---
title: 'Story 7.8: The variant classifier from the vocabulary diff'
type: 'feature'
created: '2026-10-26'
status: 'in-review'
baseline_commit: 'e70381b9e'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent, do not modify unless human renegotiates">

## Intent

**Problem:** A forum claim about vanilla Pixel Dungeon can be applied to this game by mistake. The Codex already holds the
evidence to tell the two games apart (`codex/v4.0.2/vocabulary.json`: 449 names, 114 shared, 288 only here, 47 only in
vanilla), but nothing reads it, and the `variant` of the 144 claims was set by hand (66 `spd`, 78 `unknown`).

**Approach:** `VariantClassifier` takes a claim's text, its named subjects and its source type and returns `spd`, `pd`,
`mod:<name>` or `unknown`, with the evidence that decided it. It reads Codex files only. It says `unknown` rather than
guess. A labelled fixture of at least 40 claims holds it to precision and recall thresholds written down below before the
first run, and the 144 existing claims are classified and compared with the variant they carry, without changing any file.

## Acceptance Criteria (from epics.md Story 7.8, with the tests that hold them)

1. `VariantClassifier` returns a variant and its evidence (the names matched, the side of the diff each sits on, whether
   it is shared), never a bare label. `VariantClassifierTest.evidence_is_never_empty`.
2. A name only on the SPD side gives `spd`, only on the vanilla side `pd`; a shared name whose `differences` hold a
   comparable figure that the claim's figure matches gives the side it matches; a shared name with no matching figure
   gives `unknown`. `spd_only_name`, `pd_only_name`, `shared_name_with_a_matching_figure`,
   `shared_name_without_a_figure_is_unknown`, `a_figure_matching_neither_side_is_unknown`,
   `conflicting_evidence_is_unknown`.
3. The Results page counts how many of the 144 claims the classifier decides and how many it leaves `unknown`, and is
   generated and checked for drift. `VariantReportTest.the_committed_page_has_not_drifted`.
4. The mechanics-kind question is decided in an ADR. ADR-0022 chooses the amendment: no Codex change.
5. `VariantClassifierTest` runs a labelled fixture of at least 40 claims and fails below the thresholds. The fixture
   is `shatterfish/codex/src/test/resources/variant-fixture.txt`.
6. The 144 claims are classified and compared with the variant each carries; a disagreement is listed on the Results page
   and in the PR and nothing is applied. `VariantReportTest.every_claim_is_classified_and_none_is_changed`: the report reads and
   never writes.
7. The classifier reads Codex files and the claim text only, makes no network call and names no game class.
   `VariantClassifierTest.no_network_and_no_game_code`.

</frozen-after-approval>

## Thresholds (written before the first run)

The fixture is hand-labelled by construction (a developer post about this game is `spd`; a wiki page for a vanilla-only
item is `pd`; a text that names a mod is that mod; a shared name with no figure cannot be told). The classifier fails
the build when, on the fixture:

- precision is below **0.95** for any of `spd`, `pd`, `mod` and `unknown`; and
- recall is below **0.90** for any of them.

Precision carries more weight in use: a wrong `spd` or `pd` is the harm this story exists to prevent, and a decided claim
that should have been `unknown` is the failure to avoid. The fixture is made of cases that carry a signal or are
designed to carry none, so these thresholds guard against regression; they do not estimate accuracy on forum text in the
wild. The comparison with the 144 carried variants is the second check, and it is a count, not a gate.

## Decisions (made by the engineer, the product owner having delegated them)

- **Mechanics: the ADR amendment, not a Codex change.** The Codex generator would have to emit mechanics names from the
  cited properties, and for a mechanic there is no second side: the vanilla tree has no mechanics table to diff against, so
  a name list would say "this game has it" and nothing about vanilla. It would also change `vocabulary.json` and the pages
  under `docs/codex/`, under E2's drift rule, for no signal. Mechanics claims classify by source type and by what the
  text declares, and are otherwise `unknown`. ADR-0022 has the options.
- **Where it lives.** The Codex module's test sources, beside `Lore`: the same reason as ADR-0021 (the leak gate denies the
  generator a process and a print stream). It reads the committed JSON with the harness's `Json` reader, which the module
  already has on the test classpath, so no dependency is added.
- **Evidence kinds.** A mod is named by "X Pixel Dungeon" or "X PD" where X is not Shattered, original or vanilla; it
  overrides the vocabulary, since a mod carries SPD's names. A text that says "Shattered Pixel Dungeon", "SPD",
  "the original Pixel Dungeon" or "vanilla" declares its variant. A `developer` source is `spd` by construction. Subject
  names vote. Any `spd` against `pd` disagreement is `unknown` with all the evidence shown.
- **Not wired into intake.** A variant a person set stays unless the file says otherwise; the classifier reports and
  never writes.
- **`vocabulary.json`'s own `consumer` line still says "nothing reads it yet".** Changing it is a Codex regeneration with
  a drift check and a `VocabularyTest` edit for a sentence; it is left and named in the PR.

## What this does not claim

- The fixture thresholds say the rules do what they say, not that the rules are right for text in the wild.
- A name shared by both games with no differing figure is `unknown` by design, and most mob claims in the 144 are about
  such mobs.
- Date gating is story 7.9.

## Verification

- `./gradlew :codex:test --tests 'org.shatterfish.codex.Variant*'`, then `./gradlew :codex:test`.
- `./gradlew :codex:lore --args=variants` rewrites the Results page.
- `mkdocs build --strict` (as CI runs it).
- Fairness: no `brain` file, nothing the Brain reads.

## Results (first run, after the thresholds above were committed)

- Fixture: 48 claims (spd 18, pd 15, mod 5, unknown 10). Precision and recall 1.00 for every class on the first run,
  which says the rules do what they say on cases built to exercise them, not that they are accurate on forum text.
- The 144 claims: the classifier decides 38 (all `spd`) and leaves 106 `unknown`. Against the carried variants (66 `spd`,
  78 `unknown`): 21 agree on `spd`, 17 carry `unknown` where the table names a SPD-only subject, 45 carry `spd` that the
  classifier cannot confirm (a shared mob with no differing figure, or a variant taken from the page the source URL
  names), and no claim is a contradiction (`spd` against `pd`). Nothing was applied; the page lists all of them.

## Review

Self-review against the acceptance criteria: each criterion has a named test. Risks found and kept, not fixed: the text
scan (no subjects given) can match a common word that is also a mob name, which the evidence shows as "found in the
text"; the mod pattern reads a capitalised word before "Pixel Dungeon" as a mod name; `vocabulary.json`'s `consumer` line
is half stale (ADR-0022, Consequences).
