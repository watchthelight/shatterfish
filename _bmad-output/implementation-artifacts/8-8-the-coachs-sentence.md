---
title: 'Story 8.8: The coach''s sentence'
type: 'feature'
created: '2026-10-27'
status: 'review'
baseline_commit: 'ef7b427bb'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent, do not modify unless human renegotiates">

## Intent

**Problem:** A learner sees a Decision they would not have made and has to decode a score and a one-line label to
understand it (FR-47, UJ-4).

**Approach:** A pure `CoachLine` function of a `RunLog.Decision` and the Observation it was made on returns one plain
line: the Action in `ActionText`'s words, a colon, and one reason clause chosen from the Policy's name and the lead of
its `why`, with a Safety flag appended when the clause does not already say it. The Decision card shows it over its
table.

## Acceptance Criteria (from epics.md Story 8.8, with the tests that hold them)

1. `CoachLine.of(Decision, Observation)` returns one line of at most 70 characters (two wraps of the Panel's narrowest
   text), naming the Action as `ActionText` does and one clause, with no digit in the clause.
   `CoachLineTest.the_line`, `flags`, `fits_and_decodes_nothing`.
2. Every word of every clause is on a closed list committed with the test (`coach-words.txt`), and every listed word is
   used; the Action words are `ActionText`'s own, so an unidentified item is called by the name the screen draws.
   `CoachLineTest.closed_vocabulary`.
3. A Decision with no usable reason (empty, unrecognised, or from a Policy with no template) yields the Action alone.
   `CoachLineTest.no_usable_reason`.
4. The inputs are the Decision and the Observation and nothing else; `brain` gains no import (the Overlay already
   depends on it); two games that differ in hidden state only (the Observer leak tests' permutation of unidentified
   potion, scroll and ring classes, played by the real Brain over two `standard` Warrior seeds) give equal sentences at
   every wait at which the Observation is equal, and every reason the real Brain wrote in those Runs has a template.
   `CoachLineLeakTest.equal_screens_equal_sentences`, `inputs_are_the_decision_and_the_observation`.
5. With the Oracle flag on the sentence is unchanged: it has no Oracle input. No `oracle` line is shown for the sentence
   (an oracle line is optional in the story and none is built).
6. One case per Policy name the Brain arbitrates (both models); a new Policy with no template fails the build.
   `CoachLineTest.one_case_per_policy`, `every_template_is_reached`.
7. The templates are listed in `docs/coach-line.md`, each with the `docs/brain-rules.md` row it rests on (the fallback's
   says it rests on none). `CoachLineTest.the_docs_page_and_the_rules`; `HeuristicLinkTest` stays green (no threshold is
   added).
8. Fairness: the PR is labelled `fairness` and carries the review verdict.

</frozen-after-approval>

## Design notes

Alternatives for the reason: (1) print the Policy's `why` as is; (2) a table keyed by Policy name only; (3) Policy name
plus the lead of the `why` and its detail words. (3) chosen: (1) shows distances and counts the person has to decode,
and (2) cannot tell "retreat: stairs" from "retreat 6". Alternatives for where the composer lives: `brain` (cannot name
`ActionText`, and the Brain would be writing for the screen), `api`, or the Overlay beside `ActionText`. The Overlay
chosen. Pre-mortem: a new `why` the table does not know (it falls to the Action alone and `CoachLineLeakTest` lists it
as unphrased); a Policy added without a sentence (`one_case_per_policy`); a clause that names an identity (the clause
text is constant, the only names in a line are `ActionText`'s); a line that overflows (the flag, then the reason, give
way to the Action).

Goo is not a Policy: its choices are the fight Policy's, with `why` leads `goo` and `dodge`, so its clauses are fight
templates. The story asked for the Rig numbers where a story changes play; this one cannot (no `brain`, `harness` or
`rig` main code changed, the Overlay only reads), and the leak test plays two `standard` Runs to the turn cap.

The hidden-identity permutation of `PermutedIdentitiesDifferentialTest` moved to a public test helper
(`HiddenPermutation`) and `RealBrain` became public, so the Overlay's tests can use them; the old test is otherwise
unchanged and passes.

## Results

`CoachLineLeakTest` over two `standard` Warrior seeds: 49 distinct sentences, no reason the real Brain wrote left
without a template, equal sentences wherever the Observation was equal.

What that differential test shows is Brain determinism plus `CoachLine` purity over the Runs it plays; it cannot see a
game read inside `CoachLine` that happens not to differ between the two worlds. The guard against that is structural:
`CoachLineLeakTest.the_composer_cannot_reach_the_game` is an ArchUnit rule (modelled on the `OracleView` rule of
`OracleMarkingTest`) that limits `CoachLine` and its nested classes to `org.shatterfish.api..`, `ActionText`,
`ActionContext` and `java..`. It was shown to bite by temporarily adding a `com.shatteredpixel` class reference to
`CoachLine` (the rule failed on it), then removing it. This answers the fairness-reviewer's should-fix on PR #338.

## Live-session checklist (the product owner's acceptance; no agent has opened a window)

- [ ] The Decision card shows one line over its table in the Brain's Run, for example "descend: the floor is done".
- [ ] The line wraps to at most two lines at the Panel's narrowest width and does not push the Controls row off.
- [ ] In a HUMAN Run (the Brain's shadow) the line is greyed with the rest of the card.
- [ ] An unidentified potion or scroll is named by its appearance in the line, never its kind.
- [ ] With the Oracle flag on, the line is the same.
