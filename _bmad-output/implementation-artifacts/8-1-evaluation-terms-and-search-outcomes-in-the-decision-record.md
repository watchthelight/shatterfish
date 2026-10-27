---
title: 'Story 8.1: Evaluation terms and Search outcomes in the Decision record'
type: 'feature'
created: '2026-10-27'
status: 'review'
baseline_commit: '1cbfd468d'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent, do not modify unless human renegotiates">

## Intent

**Problem:** The Explain expansion (story 8.2, FR-44) has to show the Evaluation terms behind each score and, when a
Search ran, each alternative's expected outcome. A Decision carries neither: a `Choice` is an Action, a score and a `why`.

**Approach:** Two members inside the existing Decision, written only when non-empty: `terms` on a `Choice` and
`search` on a `Decision`. The Brain's fallback Policy, the only one that scores through `Evaluation`, fills `terms` from
the same call that scored. Search never shipped (ADR-0010), so `search` is an optional field no Brain writes.

## Acceptance Criteria (from epics.md Story 8.1, with the tests that hold them)

1. A `Choice` may carry `terms` (feature, weighted contribution in ten-thousandths, in the Evaluation's feature order);
   a `Decision` may carry `search` (Action, expected outcome, sample count). Empty fields are not written, so the
   canonical JSON and the chain of every existing log are what they were.
   `RunLogTermsTest.empty_terms_are_not_written`, `terms_are_written_and_chained`, `search_outcomes`;
   `DecisionRecordCompatibilityTest.the_committed_logs_keep_their_bytes` (every committed Run log verifies its chain and
   reads and writes back byte for byte), `a_decision_with_both_round_trips`.
2. A `Choice` whose terms do not sum to its score is refused at construction. `RunLogTermsTest.terms_must_sum_to_the_score`.
3. The first deliverable is the note below stating how the fields enter the format; it is absent-when-empty, and no new
   record kind is used. ADR-0011 carries the amendment.
4. The Brain fills `terms` from the same `Evaluation` call that scored the Choice, never a second one.
   `DecisionTermsTest.terms_are_the_score`, `in_feature_order`, `the_fallback_records_terms_that_add_up`.
5. The Brain's Actions over the `smoke` set are equal before and after, shown by comparing the Action list of each Run
   (not by `:rig:direction`). `DecisionTermsTest.recording_only` at unit level and the smoke pass in Results below.
6. A Policy that scores nothing records no terms, not zeros. `DecisionTermsTest.a_certain_policy_records_none`.
7. Fairness: terms are features read from the Observation with weights from data. The Observer leak tests and the
   brain import ban stay green; the PR is labelled `fairness` and carries the `fairness-reviewer` verdict.

</frozen-after-approval>

## Design note: how the fields enter the format

Two ways were weighed. (1) Absent-when-empty members inside the existing `Decision`, schema version 2 unchanged.
(2) A version bump of the header's `v` to 3. The bump makes every existing log unreadable to a build that refuses a
different `v` and buys nothing, because a reader that does not know the members still understands the Decision. A
third way, a new record kind, needs an ADR the planning of Epic 8 did not call for. Chosen: (1). The reading did not
find a reason it cannot hold: `RunLogJson` writes a member only when asked to, `RunLogVerifier` strips the unchained
keys by kind and treats everything else as chained text, and `RunLogReader` reads the members only when present.

**The fallback's score is not the Evaluation's worth.** It records the chance of drawing the Action from its tier
(1667 for a tier of six), so the Evaluation's terms do not sum to it. The rule that terms sum to the score is kept by a
closing `tier` term carrying the difference. Changing the recorded score instead would have changed every existing
fallback Decision's bytes and the Panel's reading of it.

## Results

See the PR for the smoke-pass comparison; the method is `tools`-free: both passes are `:rig:run --brain shatterfish
--seeds smoke --parallel 2`, compared line by line with `chain`, `prev` and `terms` removed from the "after" log.
