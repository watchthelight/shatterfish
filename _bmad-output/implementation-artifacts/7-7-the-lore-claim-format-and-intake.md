---
title: 'Story 7.7: The lore claim format and intake'
type: 'feature'
created: '2026-10-26'
status: 'review'
baseline_commit: 'a303dcb79'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent, do not modify unless human renegotiates">

## Intent

**Problem:** Community knowledge has no entry point. The 144 claims graded in E4 sit in eight grouped files with no
validator, a claim has no id a heuristic can name, a forum claim from the research skill has nowhere to go, and the
verdicts were graded at `v4.0.0` while the pin is `v4.0.2`.

**Approach:** One Markdown file per claim under `lore/<topic>/<id>.md` with the PRD addendum's frontmatter, a checker
(`LoreCheck`) that runs in `./gradlew build`, an intake command that enters a draft or refuses it with a note, a
conversion of the 144 claims by a committed script, and a generated docs page by topic and Tier. The checker and the
intake live in the Codex tooling (no new module, ADR-0003 stands) with no game code and no network call.

## Acceptance Criteria (from epics.md Story 7.7, with the tests that hold them)

1. An ADR with five options, including "leave the grouped files", records the file per claim, the fields and their
   allowed values, the body rule, the tag the verdict was graded against, and that the checker lives in the Codex
   tooling. `docs/adr/0021-lore-claim-format.md`.
2. `LoreCheck` runs in `./gradlew build` and validates every file: required fields, enums, `source_url` a URL,
   `retrieved` a date, a Tier 1 or 2 claim carrying at least one `path:line` citation that resolves at the tag it was
   graded against through the FR-17 resolver, every `implemented_in` name existing.
   `LoreCheckTest.the_committed_lore_reports_nothing`, `a_missing_field_is_named`, `the_values_are_held`,
   `the_body_rule_is_held`, `identity_is_held`, `strays_and_unreadable_files`, `a_tier_needs_its_citation`,
   `a_citation_must_resolve`, `implemented_in_names_exist`, `the_real_registry_knows_what_exists`,
   `the_committed_claims_round_trip`.
3. Intake refuses a claim that references a seed, a seed string, a specific dungeon layout, a map coordinate or a level
   feeling tied to one Run, with a message saying which. `LoreIntakeTest.a_seed_string_is_refused`,
   `a_seed_is_refused`, `a_layout_is_refused`, `a_coordinate_is_refused`,
   `a_level_feeling_tied_to_a_run_is_refused` (one rejecting case per kind, and a general feeling statement accepted),
   `a_good_claim_is_entered`.
4. The intake command reads a draft from a folder, validates it, assigns the next id, and writes it under `lore/` or
   writes a rejection note beside it, and never reaches the network. `LoreIntakeTest.ids_notes_and_reruns`,
   `incomplete_drafts_are_refused`, `no_network` (the sources name no network class and no game class).
5. Intake never promotes: a draft enters at Tier 3 unless it carries the citation that makes it Tier 1, which the
   checker verifies. `LoreIntakeTest.tier_one_needs_a_citation`, `a_resolving_citation_carries_the_tier`,
   `assigned_fields_are_refused`.
6. The 144 claims are split into one file each by a committed script run once; the count per group before and after is
   equal (31, 12, 15, 8, 5, 39, 10, 24); every claim's tier and verdict carry over verbatim; the grouped files are
   removed; the `docs/bestiary/` links are updated. `LoreMigrationTest.the_counts_are_equal`,
   `no_field_was_dropped` (a SHA-256 manifest of the ten fields of every claim, written by `tools/lore-split.py`).
7. The old `v4.0.0` verdicts are kept as written and marked `needs-review` against `v4.0.2` where a cited line moved,
   using the citation checker, so no verdict silently claims a tag it was not graded against.
   `LoreCheckTest.status_follows_the_cited_lines`, `review_rewrites_only_the_status`,
   `LoreMigrationTest.moved_verdicts_are_marked`.
8. A claim's `implemented_in` and a heuristic's link agree in both directions (`LoreLinkTest`) once the `@Heuristic`
   check of story 7.1 accepts a Lore claim id. **Half done**, see Decisions: the claim's half is
   `LoreLinkTest.implemented_in_names_exist` and `claim_ids_are_unique`; the heuristic's half waits for `@Heuristic`.
9. The docs site lists the claims by topic and Tier, built from the files and checked for drift, and
   `mkdocs build --strict` passes. `LoreDocsTest.the_committed_page_has_not_drifted`, `the_page_lists_every_claim`,
   `cells_are_escaped`, `the_nav_names_the_page`.

</frozen-after-approval>

## Decisions (made by the engineer, the product owner having delegated them)

- **Topic is `bestiary`; the eight grouped files are the `group` field and the id prefix.** The epic says "per topic"
  and counts the eight files; one directory with eight id prefixes keeps `lore/<topic>/<id>.md` and the counts.
- **`implemented_in` entries are prefixed** (`policy:`, `rule:`, `playbook:`). The epic lists three kinds of name, and
  an unprefixed name would be ambiguous. Nothing uses one yet: all 144 are empty.
- **Playbook fields are the weights terms until story 7.2.** There is no Playbook as data yet. The registry reads
  `weights/*.json` terms, the nearest existing data the Brain reads at construction; 7.2 replaces it.
- **The status is computed from the cited lines, not from the tag alone.** Marking all 144 `needs-review` because the
  tag differs would make the flag carry no information. A claim is `needs-review` exactly when a line it cites reads
  differently at the pin than at the graded tag (same line numbers, text compared); 41 of 144 do. The checker holds
  both directions and `:codex:lore --args=review` sets the status after a re-pin.
- **The rejection scan reads `claim` and `quote`, never the verdict.** The first sweep refused `bosses-9`, whose
  verdict says "Old layout" of a boss arena, and `bosses-12`, whose verdict cites `NormalIntRange(15, 25)`. The
  verdict is the project's text. A coordinate pattern also ignores a parenthesis that follows an identifier.
- **Intake refuses, not lowers, a tier asked for with no citation.** The epic says a claim enters at Tier 3 unless the
  file supplies the citation. A draft that asks for Tier 1 and has none is refused with that message, so the writer
  learns it was not graded.
- **A test-only dependency of `codex` on `brain`** to read `Brain.policyNames()`. `brain` depends on `api` only, so no
  cycle, and the generator's classpath is unchanged.
- **The heuristic direction is deferred.** Story 7.1 (issue #278, another agent, in parallel) introduces `@Heuristic`;
  it is not on `main`. Writing a reverse check against a class that does not exist would be a guess at its shape. The
  hand-off for 7.1: a Lore claim id is the file stem of a file under `lore/<topic>/` (`LoreLinkTest.claim_ids()`
  lists them, ids are unique across topics), and `HeuristicLinkTest` accepts a slug that is a Rule row or such an id.
  Follow-up: after both merge, add the reverse check (every `implemented_in` of kind `rule:` or a claim id is named by
  a heuristic that links back) to `HeuristicLinkTest` or `LoreLinkTest`.
- **Group headers are not carried.** The title with a depth range, the source list and the tier counts of each grouped
  file are dropped: sources are per claim, counts are generated, depth ranges are on the bestiary pages.

## What this does not claim

- The refusal patterns are patterns. They catch the five kinds as the tests spell them and miss others (a seed spelled
  with spaces, a layout described without the words).
- `needs-review` says a cited line reads differently, not that the verdict is wrong. A comment added above a range
  flags a claim that is still true.
- The 41 flagged claims are not re-read here. That is a person's work and the count heads the docs page.
- The `variant` of the 144 is as graded (66 `spd`, 78 `unknown`); classifying them is story 7.8.

## Verification

- `./gradlew :codex:test --tests 'org.shatterfish.codex.Lore*'`, then `./gradlew build`.
- `./gradlew :codex:lore --args=check` prints `144 claims under lore/ checked` and `no findings`.
- `mkdocs build --strict` (as CI runs it).
- Fairness: the diff touches no `brain` file and nothing the Brain reads (the `brain` module gains no dependency; the
  Codex gains a test-only edge to it). No `fairness-reviewer` run is needed; the PR says why.
