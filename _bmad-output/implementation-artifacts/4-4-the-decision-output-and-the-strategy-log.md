---
title: 'Story 4.4: The Decision output and the strategy log'
type: 'feature'
created: '2026-09-23'
status: 'review'
baseline_commit: '4e95c0ec0'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The Brain's Decision is thin. Every score is 0, `flags` is always empty, and an
ordinary wait records no alternatives, because only the fallback enters and it offers one
Choice. The reasons are sentences in prose. The Wait record's `highlights` field is always
empty, so the Overlay and the Replay scrubber would have to re-derive the cells the Brain
meant. A person watching cannot tell thinking from flailing (FR-32, FR-36, UX-DR13).

**Approach:**
- A Policy ranks the Actions it would take rather than naming one. Arbitration takes the first
  entering Policy's best as the chosen Action, and fills up to three distinct alternatives from
  that Policy's remaining ranks and then from later Policies.
- Every Choice carries a score in ten-thousandths and a one-line reason as labels and numbers.
- The Decision carries the Safety flags read from the screen.
- The Brain's Decision output carries its highlight cells, and the Run loop writes them into
  the Wait record's existing `highlights`.
- A plain-text strategy log is rendered from any Run log.
- The Brain's Rules index enumerates the mechanics claims the Brain relies on.

## Boundaries & Constraints

**Always:**
- `brain` imports only `api` and the allowed JDK packages. It uses no `Locale` and no
  `toLowerCase`.
- The log schema changes only additively within a version (PRD, "Decision and strategy log
  formats").
- A Brain log still replays to its own chain.

**Ask First:** Any change to the chained text of a record a committed log already contains.

**Never:**
- Highlights inside `RunLog.Decision`. ADR-0011 places them on the wait record, and duplicating
  them would chain the same cells twice.
- A strategy log that is a second source of truth: it is rendered from the Run log.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Ordinary wait, several Actions | fallback only | chosen plus up to three distinct alternatives, each scored `1/n` in ten-thousandths | N/A |
| One Action offered | fallback only | chosen, no alternatives (none exists) | N/A |
| Prompt with a declining answer | "Yes"/"No" | chosen "No" (10000); "Yes" recorded as an alternative | N/A |
| Cell Action chosen | `Step(5)` | highlights `[5]` on the wait record | N/A |
| Hero below a third of health, enemy in view | screen | flags `hp-low`, `enemy-in-view` | N/A |
| A log with no Decision (random agent) | Run log | strategy log prints the action alone | N/A |

</frozen-after-approval>

## Design notes

**Where the highlights live.**
1. A `highlights` field on `RunLog.Decision`. Rejected: a schema change that duplicates the Wait
   record's own `highlights` (ADR-0011's table) and chains the same cells twice.
2. **`Deliberator.lastHighlights()`**, a default method returning none. Chosen. The Brain's
   `Decided` carries the cells, `RunLoop` writes them into `Wait.highlights`, and Replay's follower
   restates the logged ones. There is no schema change, and the Overlay reads the same field from a
   live Deliberator or from the log.
3. A new `api` record wrapping the Decision and its cells, returned by the Deliberator. Rejected:
   it changes an interface two stories already implement, to carry one list.

**How alternatives exist on an ordinary wait.**
1. Every Policy enters and chooses once, so alternatives come only from other Policies. Rejected:
   today only the fallback enters, so an ordinary wait would never have an alternative.
2. **A Policy ranks.** `Policy.ranked(...)` is a default method built on `choose`, and a Policy
   that can say more overrides it. Chosen. It keeps the one-method contract for simple Policies
   and needs no change to them.
3. The arbitration draws alternatives itself from the offered set. Rejected: that records Actions
   no Policy weighed, with reasons nobody gave.

**The strategy log.**
1. Written by the Run loop beside the JSONL. Rejected: a second, unchained file that can
   disagree with the log.
2. **Rendered on demand from a Run log** (`rig/StrategyLog`, `./gradlew :rig:strategy
   --args="<log.jsonl>"`). Chosen. One line per wait, in plain text, so nothing needs to be kept
   in step.
3. Markdown tables. Rejected: long lines wrap badly in a terminal, and NFR-9 asks for text a
   person reads without tooling.

**Pre-mortem.**
- Changing the chosen draw moves Runs. The fallback's first draw is unchanged
  (`stream.below(n)`), and the alternatives come from later draws, so the chosen Action and the
  Runs are identical to 4.1's. The direction check should show a tie.
- Replay forgets the highlights. The follower restates `wait.highlights()`, and
  `ShatterfishRunTest` replays a Brain log.

## Code Map

- `api/.../Deliberator.java`: gains `lastHighlights()`.
- `api/.../RunLog.java:225-255`: `Choice`, `Decision` (unchanged); `Wait.highlights`.
- `brain/.../Policy.java`, `Policies.java`, `Brain.java`, `BrainDecider.java`, `Safety.java` (new),
  `Highlights.java` (new).
- `harness/.../agent/RunLoop.java:424`: writes the highlights. `harness/.../log/Replay.java`: the
  follower restates them.
- `rig/.../StrategyLog.java` (new), `rig/build.gradle` (the `strategy` task).
- `docs/brain-rules.md` (new): the Brain's Rules index.

## Tasks & Acceptance

**Execution:**
- [x] `Policy.ranked`; `ANSWER_PROMPT` and `FALLBACK` rank, with scores and label reasons.
- [x] Arbitration over ranks; `Safety` flags; `Highlights` of the chosen Action.
- [x] `Deliberator.lastHighlights`, written by `RunLoop` and restated by Replay.
- [x] `StrategyLog` and `:rig:strategy`.
- [x] `docs/brain-rules.md` and `BrainRulesIndexTest`.
- [x] `DecisionShapeTest`.

**Acceptance Criteria:**
- Given any screen, the Decision has a Goal. Given more than one offered Action, it has at least
  one alternative (`DecisionShapeTest`).
- Reasons are labels and numbers, never sentences (`DecisionShapeTest`).
- The highlight cells reach the wait record and survive a replay (`ShatterfishRunTest`).
- The strategy log renders any Run log as plain text, one line per wait (`StrategyLogTest`).
- Every row of the Brain's Rules index names a rule that exists on its page
  (`BrainRulesIndexTest`).
- The PR carries a `smoke` direction check against the 4.1 Brain.

## Dev Notes

**Schema:** no version bump. `RunLog.Decision` is unchanged, and the Brain's highlights go into the
wait record's existing `highlights` field (ADR-0011). Committed logs are untouched: the random
agent's waits carry neither a Decision nor highlights, as before.

**Behaviour:** the chosen Action is the one 4.1 chose on every screen. The fallback's pick is still
the first draw of the wait's stream, and the alternatives come from later draws. The prompt Policy's
pick is unchanged. The Runs, and so the direction check against the 4.1 Brain, should be identical;
only the log's Decisions and highlights differ.

**Tests:** `:brain:test` and `:api:test` pass, including `DecisionShapeTest` (5). The harness
Replay, RunLog and JsonRendering tests pass. `StrategyLogTest` (2), `BrainRulesIndexTest` (1) and
`ShatterfishRunTest` pass; in the worktree its version check is skipped because `.git` is a file
there. `mkdocs build --strict` is clean.

**Mutation battery: 9 of 9 killed.**
- An alternative repeating the chosen Action.
- Two alternatives instead of three.
- `hp-low` made strict.
- A Step left unhighlighted.
- The decline label lost.
- The refused marker dropped from the strategy log.
- An index row pointing at no Rule.
- The Run loop dropping highlights.
- The replay dropping highlights.

**Open:** row 3 of the Brain's Rules index (declining the chasm prompt) points at a game-loop Rule
at needs-review since `v4.0.0`. Re-reading it is upstream-rule work, not this story's.

**Rig numbers (direction check).** Set `smoke`, 25 triples. The `shatterfish` Brain at main
`4e95c0ec0` (story 4.1) against this branch before the 4.2 merge, with the salts fixed through
`RunOne`. All 25 Action sequences are identical, over 1,688 waits. The change moves no Run: it adds
what the log says, not what the Brain does.

### Review

Fairness review: PASS, with one should-fix, which is fixed. The index claimed row 3 was the only
claim resting on a needs-review Rule, but row 4 (`hp-low`) rested on `ui.md`'s status-pane row,
still cited at v3.3.8.

Lens review findings, all fixed:
1. **`hp-low` had no cited threshold.** It now uses the status pane's own low-health warning, `HP/HT
   < 0.334`, from `StatusPane.java:300-310`, compared in integers as `1000·HP < 334·HT`. A new
   `ui.md` row cites it at v4.0.0 (Tier 1), and index row 4 points at that row. Zero health counts as
   low: the pane darkens a dead hero rather than tinting it, but a Brain asked to decide at zero is in
   the worst danger there is. `DecisionShapeTest` pins 333/1000 (low), 334/1000 (not low) and 0.
2. **The index test only checked that rows were valid.** `BrainRulesIndexTest` now also requires
   every Policy except the fallback (`Brain.policyNames()`) and every Safety flag
   (`Brain.safetyFlags()`) to be named in some row's "Used by". The needs-review rows are reported,
   not refused: the page states "Rows resting on a needs-review Rule: 3." and the test holds that
   statement to the Tier each Rule page shows. A Rule that flips at an upgrade therefore shows up
   as a named row, without blocking a Brain story on upstream-rule work.
3. **`Highlights.of` had a default branch.** It now names all 21 Action kinds with no default, so the
   compiler checks exhaustiveness over the sealed type.
4. **`ShatterfishRunTest` did not check the Decision's shape in real waits.** At every real wait it
   now checks:
   - the goal is not blank;
   - the alternatives are distinct from each other and from the choice;
   - a fallback wait records `min(n-1, 3)` alternatives, with `n` read from its "uniform 1/n" reason.
5. **Highlights were checked only for Steps.** Every wait's highlights now equal the cells for its
   action kind, computed by the test's own switch. A new random-agent Run checks that its waits carry
   no Decision and no highlights.
6. **Scores were truncated.** The fallback's score is `1/n` rounded to the nearest ten-thousandth,
   so one in six is 1667, and the javadoc, fixture and test agree.
7. **A reason could break the strategy log.** Reasons, goals, flags, Policy names and Actions print
   with control characters as spaces and `|` as `/`.
8. **Labels were checked loosely.** Goals and reasons are held to `^[a-z-]+(: .+| [0-9/]+)?$`. The
   prompt Policy's goal became `prompt: close`, and its non-declining answers `answer: <label>`.
9. **The strategy log stopped at the first bad log.** `StrategyLog.main` now reports an unreadable
   log and continues, and a wait that answered a Prompt names the Prompt's kind from the log's
   Prompt record.

Tests after the fixes:
- `:brain:test` passes.
- `StrategyLogTest` (3), `BrainRulesIndexTest` (2) and `ShatterfishRunTest` (4, one skipped in the
  worktree) pass.
