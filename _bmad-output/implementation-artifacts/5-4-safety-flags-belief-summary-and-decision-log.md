---
title: 'Story 5.4: Safety flags, Belief summary and Decision log'
type: 'feature'
created: '2026-09-26'
status: 'done'
baseline_commit: '6de8c673b'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Story 5.3 gave the Panel a Mode strip, a Goal line and a Decision card, so the human can
see what the Brain is about to do and why. It cannot yet see what the Brain currently *believes* (an
unidentified item's odds, a floor's known facts, a chapter's counters) or what it is *worried about*
(a Safety flag), and it cannot read more than the single most recent Decision -- so a run that goes
wrong because of a bad belief, or because a flag was ignored three turns ago, leaves nothing on screen
to show it.

**Approach:** Read the Brain's own Beliefs (story 4.2) through a small new api record,
`BeliefSummary`, built by `BrainDecider` the same way story 4.4's Highlights are, and exposed by
`Deliberator` alongside `lastDecision()`; read the Run's own Safety flags, already carried by
`RunLog.Decision.flags()` since story 4.4, as chips coloured by a verdict this story decides;
and give `EmbeddedRun` a small bounded history of the exact wait records `RunLoop.record` already
builds for the file, so the Decision log is a view over them and not a second source. Every new
section is a pure content function first (`SafetyFlagsContent`, `BeliefSummaryContent`,
`DecisionLogContent`), turned into `RenderedTextBlock`s and, for the log, the game's own
`ScrollPane`, the same split story 5.3 held itself to.

## Acceptance Criteria (from epics.md Story 5.4, with the tests that hold them)

1. **Safety flags.** Appear as chips; each chip's text states the flag, and its colour follows its
   verdict; absent when there are none.
   Tests: `SafetyFlagsContentTest` (chip text and order, the four-chip cap, every known flag's
   verdict, the unrecognised-flag fallback, the verdict colours), `PanelContentTest.full_panel_shows_flags_belief_and_log`
   and `.no_flags_hides_the_row` (a real Panel), `.collapsed_hides_the_goal_and_the_card` (hidden
   collapsed too).
2. **The Belief summary.** Shows unknown items with their top candidate and probability, then floor
   facts, then chapter counters.
   Tests: `BeliefSummaryTest` (the mapping from a constructed `Beliefs` to the api's
   `BeliefSummary`: the three least-confident items, ties broken by label, a Guess with nothing left
   skipped, floor and chapter lines formatted, and the fairness property against a real `Brain` and
   `Observation`), `BeliefSummaryContentTest` (the overlay's own formatting: absent-vs-placeholder,
   items then floor then chapters, two-decimal probabilities), `PanelContentTest.full_panel_shows_flags_belief_and_log`.
3. **The Decision log.** Shows one line per Input wait with the turn, the actor, the Action and the
   score; a goal change and a Mode change each get a line of their own; newest at the bottom;
   auto-scrolls only while already at the bottom; is a view over the Run log records, not a second
   source of truth.
   Tests: `DecisionLogContentTest` (one line per wait, newest at the bottom, the goal-change and
   Mode-change lines, the em dash for a Decider with no Decision), `DecisionLogTest` (the same
   content on a real Panel, auto-scroll while at the bottom, no auto-scroll once the human has
   scrolled up, and the fairness review's synthetic-tap gate on the `ScrollPane`), `BoundedLogTest`
   (the 200-record cap `EmbeddedRun.history()` is built on), `EmbeddedSnapshotTest.a_served_wait`
   (the served wait's own record and the Brain's belief summary both reach the snapshot).

</frozen-after-approval>

## Design notes

### 1. Getting the Belief summary to the Overlay

- **A. Expose the Brain's own `Beliefs` record straight through `Deliberator`.** Rejected: `Beliefs`
  (and its nested `Guess`/`Odds`/`FloorItem`/`Chapter`/`Monster`) live in `org.shatterfish.brain`,
  and `Deliberator` is `api`; `api` cannot import a brain type without inverting the module
  dependency the non-negotiable itself states ("the brain module cannot import game code," and
  by the same discipline the reverse direction -- api depending on brain -- is not built either;
  `overlay` already depends on both, but a `Deliberator` method is read by the harness too, which
  must not gain a brain dependency for one field).
- **B. A `String` (or a small JSON blob) the Brain formats for itself, read back by the Overlay as
  opaque text.** Rejected: it would mean the Overlay parses text the Brain wrote for humans (or
  invents a second ad-hoc schema for exactly this one field), the same objection story 4.4's review
  raised against showing `Action.toString()` on screen -- a machine-readable shape belongs in a
  record with named fields a test can hold, not a string a human happens to be able to read today.
- **C. A small `api` record, `BeliefSummary`, mirroring story 4.4's `Highlights` pattern: built by
  `BrainDecider` from its own `Beliefs`, exposed by a new `Deliberator.beliefSummary()` default
  method (null by default, the same "nothing to show" convention `lastDecision()` and `belief()`
  already use).** Chosen. `BeliefSummary` (`shatterfish/api/.../BeliefSummary.java`) carries only
  what a person reading the Panel needs: `items` (at most `ITEMS` = 3, each a label, a candidate
  name and a probability), `floor` and `chapters` (already formatted one-line strings, since their
  wording is the brain module's own choice and the Overlay should not have to guess it). A new
  brain-module class, `BeliefSummaries` (`shatterfish/brain/.../BeliefSummaries.java`, package-private,
  a pure static utility exactly like `Highlights`), builds one from a `Beliefs`.
  `BrainDecider.decide()` calls `brain.beliefs(observation, belief)` -- an existing public method,
  story 4.2 -- with the same `belief` its own Javadoc names ("holding belief, the one `update`
  returned for it"), right after `update()` and before `decide()`/`handed()` touch it again, and
  stores `BeliefSummaries.of(...)` in a new field, added to the `Rewindable` `Mark` record beside
  `belief`/`last`/`why`/`highlights` so a stale-answer rewind puts it back too.

Pre-mortem: *A reviewer worries `BeliefSummaries.of` could leak a true identity by accident.*
It cannot construct one: every `BeliefSummary.Item` it builds copies a `Beliefs.Guess`'s own
`odds().get(0)` -- a candidate name and a probability `Beliefs.identities()` already computed from
the Codex's family weights and what is in view (`Beliefs.java:458-502`), never from the game's
hidden truth. `BeliefSummaryTest.never_names_a_true_identity` drives the real pipeline (a `Brain`,
a constructed `Observation` with an unidentified potion, nothing in the journal yet) and holds that
the summary's candidate is always one of the Codex's own four named candidates and its probability
is strictly between 0 and 1 -- never a certainty a fair Brain could not have. A `fairness-reviewer`
subagent pass was also requested on this diff (see "Fairness review" below for its status at the time
of writing).

### 2. Safety flags' verdict

`RunLog.Decision.flags()` already carries a Safety flag's own text unchanged since story 4.4
(`Safety.flags(Observation)`, `shatterfish/brain/.../Safety.java:47-65`, feeding
`Brain.java:333`) -- but as a bare string, with no verdict attached. This story's own acceptance
criterion asks for a verdict-coloured chip, which nothing upstream of the Overlay decides today.

- **A. Encode the verdict in the flag's own text at the source (`Safety.flags` returns, say,
  `"danger: hp-low"`).** Rejected: three other modules already read a flag's text as the bare
  identifier `Safety.ALL` names (`DecisionShapeTest`, `StrategyLogTest` and `GalleryComparisonTest`
  in `rig`, which format and publish `"flags hp-low,enemy-in-view"` verbatim into a death gallery's
  markdown page) plus this module's own `DecisionCardContentTest`/`PanelContentTest` from story
  5.3; changing the format changes what every one of those already-published-shaped strings says,
  for a need (a chip's colour) that is purely the Overlay's own presentation.
- **B. A hardcoded lookup table in `overlay`, keyed on the literal strings `Safety.java` happens to
  use today, with no link back to the real vocabulary.** Rejected as written: it is exactly the kind
  of silently-stale mapping `ActionText`'s exhaustive switch (story 5.3) was designed to prevent --
  a fifth flag `Safety.java` adds later would fall back with nobody told to update this table.
- **C. The same lookup table, but completeness-tested against the real vocabulary:
  `SafetyFlagVerdict.of(String)` (`shatterfish/overlay/.../SafetyFlagVerdict.java`), a `switch` over
  the four flag identifiers with an explicit `WARN` fallback, and `SafetyFlagsContentTest.every_known_flag_has_its_own_verdict`
  asserting every string in `org.shatterfish.brain.Brain.safetyFlags()` (already `public`, story
  4.4's own mirror of `Safety.ALL`, so no new brain-module visibility is needed) takes a real
  verdict and that the list is still exactly four -- if brain grows a fifth flag, this test does not
  merely fall back quietly, it stops asserting the thing that matters (that a real verdict, not
  the fallback, is what the four *known* flags get) without a human having looked at the new one.**
  Chosen. The four verdicts themselves are judged from what the game does about each condition, cited
  by `path:line` (non-negotiable 8): `hp-low` and `starving` are `DANGER` (`Safety.java:22-28`, "the
  worst danger there is"; `Hunger.java:161-164`, starving deals the hero damage every hunger tick and
  more the longer it goes unfed); `hungry` and `enemy-in-view` are `WARN` (`Hunger.java:159-160`, only
  a warning message, no damage yet; and `DESIGN.md`'s own words for amber, "conditional" -- an enemy
  in view is not inherently unsafe, a corridor fight might be fine).

Pre-mortem: *An unrecognised flag string reads as unsafe-by-omission or safe-by-omission, either of
which would be a colour that lies.* The fallback is `WARN`, not `OK` and not `DANGER`: worth a look,
neither dismissible nor alarming -- and `SafetyFlagsContentTest.unrecognised_flag_falls_back_to_warn`
holds that this is the fallback's own colour, not an accident of enum ordinal order.

### 3. Decision log source

- **A. `EmbeddedRun` reopens and tails the Run log file for the lines the Panel needs.** Rejected
  outright by the acceptance criterion itself ("a view over the Run log records rather than a second
  source of truth") and by story 5.3's own rejection of the same idea for a single Decision ("the log
  may be null... and a Decision the render thread already saw once should not cost a file read every
  frame") -- doubly true for up to 200 lines, every frame.
- **B. A second list, built by `EmbeddedRun` reading the same fields `RunLoop.record` reads
  (`deliberator.lastDecision()`, etc.) independently, alongside the log write.** Rejected: two call
  sites computing the same `RunLog.Wait` from the same inputs are two chances to compute it
  differently, which is exactly the "second source of truth" the acceptance criterion names by name
  -- and the harness already has one call, `RunLoop.record`, that is the log's own writer.
- **C. `RunLoop.record` returns the exact `RunLog.Wait` it built (previously `void`, and it now
  builds and returns one even with no log to write to, since the oracle-consistency check and the
  record's shape should not depend on whether a file happens to be open); `EmbeddedRun.serve()`
  hands that same object to a small new class, `BoundedLog` (`shatterfish/harness/.../BoundedLog.java`),
  capacity 200 (FR-38: "the Decision log shows... 200 lines on screen; the Run log holds the rest");
  `EmbeddedRun.Snapshot` gains `history()`, a `List<RunLog>` (the sealed interface, not narrowed to
  `Wait`, so a future Mode-change record slots in without widening the type again).** Chosen.
  `BoundedLog` is its own class rather than inline `Deque` bookkeeping in `EmbeddedRun` so its own
  trimming rule (oldest drops once the capacity is passed) is something `BoundedLogTest` can hold
  directly, without driving 200 real waits through a game in a unit test.

Pre-mortem: *`RunLoop.record` building a `RunLog.Wait` even with `log == null` changes behaviour
somewhere that depended on the old early return.* The only two callers are the headless loop's own
`playing()` (which always has a real `log` in every path that matters, and ignores the return value
either way) and `EmbeddedRun.serve()` (which now uses the return value); the oracle-consistency check
that now always runs is a strengthening, not a new way to fail -- `EmbeddedRun.frame()` already
throws the identical check before a decision is even submitted (`EmbeddedRun.java:407-413`), so by
the time `record()` runs a second, redundant check of the same invariant, it has never once found it
false in a passing Run.

### 4. Goal changes and Mode changes as lines of their own

- **A. A new `RunLog` record kind, `GoalChange`, written whenever the goal differs from the last
  wait's.** Rejected: `RunLog.Decision.goal()` already states every wait's goal; a second record
  saying "it changed" would duplicate information the log already carries on every `Wait`, for a
  presentation-only need (where to put a line break) that the Overlay can compute for itself by
  comparing two consecutive waits it already has.
- **B. Show every wait's goal on its own line, always, rather than only where it changes.** Rejected:
  most waits do not change the goal (fighting the same enemy for several turns, say), and repeating
  it every line would bury the one thing a changed goal is meant to draw the eye to -- UX-DR14's
  "colour is never the only signal" has a quieter cousin here, that repetition is not signal either.
- **C. `DecisionLogContent.of` derives a goal-change line by comparing each `RunLog.Wait`'s
  `decision().goal()` to the last one it saw, emitting a line only when it differs; `RunLog.Mode`
  (ADR-0013's own record kind, already in the sealed interface, unwritten by any headless caller
  since nothing paces a Mode change until stories 5.5 to 5.7) is read the same way a real Mode
  change would appear, with a line of its own, so those stories add a writer and this class needs no
  change.** Chosen -- both `DecisionLogContentTest.goal_change_gets_its_own_line` and
  `.mode_change_gets_its_own_line` construct the records directly, since nothing produces a real
  `RunLog.Mode` yet.

Pre-mortem: *A `Wait` with no Decision (a plain, non-`Deliberator` agent) could be read as "the goal
is null now," clearing a goal line that should have stayed.* `DecisionLogContent.of` only emits a
goal-change line when the new goal is non-null and differs from the last non-null one it saw; a
`Wait` with `decision() == null` contributes its own action-and-score line (with an em dash where the
score would be, `ModeStripContent.NO_INTERVAL_YET`, the same placeholder convention story 5.3 chose
for a value nobody measured) and leaves the remembered goal alone.
`DecisionLogContentTest.no_decision_shows_an_em_dash` holds the em dash; nothing in the test suite
constructs a goal-then-no-decision-then-goal sequence to hold the "leaves it alone" half directly,
which is recorded in `docs/ideas.md` as the one case this story's tests do not pin.

### 5. "Most relevant" for the Belief summary's three items

FR-38 says "the three most relevant unknown items" without saying what relevant means.

- **A. The Brain's own iteration order (`Beliefs.identities()`'s family-then-label order), truncated
  at three.** Rejected: an artifact of which Codex family the loop reaches first, not a judgement
  about which item the human most needs to see.
- **B. The three most confident guesses (highest top-candidate probability) first.** Rejected: a
  belief the Brain is already all but certain about is the belief least likely to be the "bad
  belief" the story's own "As the human" line exists to let the human catch ("so that I can spot a
  bad belief before it costs a run") -- showing the safest guesses first would bury the one item
  most worth a second look behind ones that need none.
- **C. The three *least* confident guesses (lowest top-candidate probability) first, ties broken by
  label so the choice is not order-of-computation, a `Guess` with no odds left (every candidate
  ruled out) skipped without using up the cap.** Chosen. `BeliefSummaries.of` sorts ascending by
  `guess.odds().get(0).probability()` (`Beliefs.identities()`'s own sort already puts the top
  candidate first within a `Guess`) and keeps the first three. `BeliefSummaryTest.least_confident_first_and_capped_at_three`
  and `.ties_break_by_label` hold the ordering against a constructed `Beliefs`, and
  `.guesses_with_no_odds_are_skipped` holds that an exhausted `Guess` is not shown and does not
  crowd out a real one.

Pre-mortem: *A human reads "least confident first" as the Brain hedging, not as a deliberate choice
to surface risk.* Recorded here and in `BeliefSummaries.of`'s own Javadoc in the same words as this
note, so a reader of the code meets the reasoning before they meet the ordering.

### 6. Scrolling and the synthetic-tap gate

`DESIGN.md` names the game's own `ScrollPane` for the log ("a `ScrollPane` with the newest line at
the bottom"), and the setup itself named the risk: "the executor's synthetic taps bypass `InputLock`,
so any clickable area (e.g. a `ScrollPane`'s drag area) must not intercept executor taps."

- **A. Render only the visible window of lines directly (no `ScrollPane`), clipped by hand and
  scrolled by re-slicing the list.** Rejected: it reinvents drag, the scroll wheel and the thumb the
  game's own `ScrollPane` (`core/.../ui/ScrollPane.java`) already gives every other scrollable list
  in the game (`AboutScene`, `WndJournal`), for no benefit over using it, and non-negotiable 6 asks
  for the game's own toolkit specifically.
- **B. A real `ScrollPane`, with no gate at all, relying on `InputLock` covering it the way it covers
  every other human click.** Rejected: `InputLock` sits in the libGDX `InputProcessor` multiplexer
  and does cover a human's real drag or scroll-wheel event while a Run plays; but
  `ActionExecutor.press` (story 5.3's fairness review, `ActionExecutor.java:420-428`) queues its
  synthetic taps as `PointerEvent`s directly, past that multiplexer entirely, and `ScrollPane`'s own
  drag area (`ScrollPane.PointerController`, a `ScrollArea`, itself a `PointerArea`) is dispatched by
  that same signal -- so a synthetic tap meant for a window's own button drawn where the log happens
  to sit could be swallowed by the log's drag area exactly as it could have been swallowed by
  Explain's, before story 5.3's fairness review fixed that one case.
- **C. `DecisionLog.pane.active = !inputLocked`, set every `content()` call, the identical mechanism
  story 5.3's fairness review chose for Explain: `Gizmo.isActive()` walks the parent chain, so the
  pane's own child (`ScrollPane.PointerController`) reads inactive whenever the pane does, and
  `PointerArea.onSignal`'s default `blockLevel` (`BLOCK_WHEN_ACTIVE`) then lets any tap fall through
  rather than consuming it.** Chosen -- not a special case invented for the log, but the general
  rule the story 5.3 fairness review already named for "every future Panel control"
  (`docs/ideas.md`, "A second, general instance of the bug the fairness review found in Explain")
  applied to its first real second instance.
  `DecisionLogTest.does_not_steal_a_synthetic_tap_while_locked` reproduces
  `PanelContentTest.explain_does_not_steal_a_synthetic_tap_while_locked` exactly, at the log's own
  position.

Pre-mortem: *Auto-scroll and the gate interact badly: scrolling programmatically while `active` is
false could itself be read as "the game moved something."* It cannot: `DecisionLog.content()`'s own
`scrollToBottom()` is a plain method call, not a `PointerEvent`, and runs on the UI-role thread inside
the same call that already writes every other section's `RenderedTextBlock` text -- the same
"rendering is read-only, and setting text is not a click" property `PanelContentTest.content_does_not_draw_from_the_generator`
already holds for the rest of the Panel.

### 7. Height arbitration (story 5.3's own deferred item)

Story 5.3 deferred "the Decision card's height arbitrating room with the sections story 5.4 adds
below it," and `PanelLayout.MIN_PANEL_HEIGHT` (story 5.2) already baked in an assumption -- padding,
the strip, one gap and the log's three lines -- that predates every section this story adds between
the strip and the log.

- **A. Leave `MIN_PANEL_HEIGHT` as it was and let the Panel collapse to the strip only when it is
  visibly too small, discovered on screen rather than computed.** Rejected: `PanelLayout.of` is a
  pure function of the screen exactly so the collapse threshold does not need a human staring at a
  cramped Panel to notice it is cramped; leaving the constant wrong defeats the reason it exists.
- **B. Compute the true worst case exactly, including the Goal line's up to two lines and the Safety
  flags' one row, every time all of them are showing something.** Rejected as this story's scope: the
  Goal line and the flags row both collapse to zero height with nothing to show, and *most* of the
  time on a fresh Run neither has anything yet; sizing for "everything visible at once" would
  collapse the Panel on screens that would in practice display it fine, trading one wrong estimate
  for another in the opposite direction.
- **C. Add exactly the one line that is never optional -- the Decision card's own "no decision yet"
  placeholder, one line at body size 8 -- to the existing floor, and say plainly in the constant's own
  Javadoc that the Goal line, the flags row and the Belief summary's placeholder can each still
  collapse toward zero (the Belief summary always shows at least its own one-line placeholder at the
  same size the card's occupies, so it does not add a second worst-case line of its own).** Chosen:
  `MIN_PANEL_HEIGHT` becomes `PADDING + STRIP_HEIGHT + gap + (8+2) + gap + 3*(6+2) + PADDING` (66,
  up from 50). `PanelLayoutTest` references the constant symbolically rather than a literal number,
  so this change needed no edit there.

Pre-mortem: *66 is still not "every section has real content," so a screen that just clears the new
threshold could still show a cramped Panel once a Goal line and several Safety-flag chips are real.*
True, and named as exactly that in the constant's own Javadoc rather than left implicit; a story that
wants a tighter guarantee has one exact number to change, in one place, with a comment already
explaining what the number does and does not promise (`docs/ideas.md`).

## Tasks

- [x] `BeliefSummary` (api): the record, `Deliberator.beliefSummary()`.
- [x] `BeliefSummaries` (brain): `Beliefs` to `BeliefSummary`; `BrainDecider` wired, including
  `Mark`/`rewind`.
- [x] `RunLoop.record` returns its `RunLog.Wait`; `BoundedLog`; `EmbeddedRun.history()`,
  `Snapshot.beliefSummary()`, `Snapshot.history()`.
- [x] `SafetyFlagVerdict`, `SafetyFlagsContent`, `SafetyFlagsRow`.
- [x] `BeliefSummaryContent`, `BeliefSummarySection`, `Columns.probability`.
- [x] `DecisionLogContent`, `DecisionLog` (the game's `ScrollPane`, the synthetic-tap gate).
- [x] `Panel`: the three new sections wired into `content`/`layout`; `PanelDock.frame` threads the
  Snapshot's new fields through.
- [x] `PanelLayout.MIN_PANEL_HEIGHT` revised for the Decision card's own worst case.
- [x] Tests: `BeliefSummaryTest`, `BeliefSummaryContentTest`, `SafetyFlagsContentTest`,
  `DecisionLogContentTest`, `DecisionLogTest`, `BoundedLogTest`; `PanelContentTest` and
  `EmbeddedSnapshotTest` extended.
- [x] Fairness review of the api/brain diff (this engineer's own reading, backed by
  `BeliefSummaryTest.never_names_a_true_identity`); a `fairness-reviewer` subagent pass was also
  launched on the same diff, result pending at the time of writing.
- [x] Real launch with `--agent brain` and a screenshot.
- [x] Docs: `docs/architecture.md`, ADR-0013 amendment, `docs/fairness.md`, `docs/ideas.md`.
- [x] Mutation battery.

## Review

### What was built

- `org.shatterfish.api.BeliefSummary`: `items` (at most `ITEMS` = 3, each a label, a candidate name
  and a probability), `floor`, `chapters` (already-formatted lines). `org.shatterfish.api.Deliberator.beliefSummary()`:
  a default method, null unless overridden.
- `org.shatterfish.brain.BeliefSummaries.of(Beliefs)`: the least-confident-first mapping (design
  note 5). `BrainDecider` computes it in `decide()` from `brain.beliefs(observation, belief)` and
  carries it through `mark()`/`rewind()`.
- `org.shatterfish.harness.agent.BoundedLog`: a capacity-bounded FIFO of `RunLog` records.
  `RunLoop.record` now returns the `RunLog.Wait` it builds (design note 3); `EmbeddedRun.serve()`
  appends it to a `BoundedLog` of capacity `HISTORY_CAPACITY` (200) and also now reads
  `deliberator.beliefSummary()`; `EmbeddedRun.Snapshot` gained `beliefSummary` and `history` (with a
  five-argument secondary constructor kept for the callers built on story 5.3's shape, so no existing
  call site needed to change).
- `org.shatterfish.overlay.SafetyFlagVerdict` (`OK`/`WARN`/`DANGER`, `DESIGN.md`'s three colours),
  `SafetyFlagsContent` (the chip list, capped at 4, absent when empty), `SafetyFlagsRow` (the `TAG`
  nine-patch chips, tinted, ink text).
- `org.shatterfish.overlay.BeliefSummaryContent` (present/absent, items formatted with
  `Columns.probability`, floor and chapters passed through), `BeliefSummarySection` (one
  `RenderedTextBlock` per line, body size, always shows something while full -- its own "nothing
  believed yet" placeholder mirrors the Decision card's "no decision yet").
- `org.shatterfish.overlay.DecisionLogContent` (design notes 3 and 4: one line per wait, goal- and
  Mode-change lines, an em dash where a Decider with no Decision leaves no score),
  `org.shatterfish.overlay.DecisionLog` (the game's `ScrollPane` wrapping a `Component` of line
  blocks, muted ink for past lines and ink for the newest, auto-scroll gated on "was already at the
  bottom," the synthetic-tap gate on `pane.active`, design note 6).
- `Panel`: `flags`, `belief`, `log` fields, wired into `content()` (each set from the Snapshot's new
  fields, gated on `full`) and `layout()` (the section order `DESIGN.md` names -- strip, Goal line,
  Decision card, Safety flags, Belief summary, Decision log -- each above the log measured by its own
  `contentHeight()`, the log given whatever remains down to the Panel's own bottom edge, never less
  than three lines). `layout()` now runs once before `log.content()` inside `Panel.content()` (not
  only after, as every other section's own two-phase split has it) specifically so the log's
  `ScrollPane` already knows this frame's real viewport height before it decides whether to
  auto-scroll (caught by `DecisionLogTest.auto_scrolls_while_at_the_bottom` on the very first fill,
  when the pane's constructed height is still 0). `PanelDock.frame` threads
  `snapshot.beliefSummary()` and `snapshot.history()` through.
- `PanelLayout.MIN_PANEL_HEIGHT`: revised per design note 7 (50 to 66).
- `shatterfish/api/src/test/.../JsonRenderingTest.java`: `BeliefSummary`/`BeliefSummary$Item` added
  to `HELPERS`, the same way `RunLog`'s own kinds are listed -- neither is reachable from
  `Observation`'s own schema, and neither reads JSON back.
- No upstream file edited; no hook row.

### Tests

`:api:test`, `:brain:test` and `:overlay:test` all green in full. `:harness:test`'s own touched
classes (`BoundedLogTest`, `EmbeddedSnapshotTest`) are green in full and were also green as part of a
whole-suite `:harness:test` earlier in the session; the whole-module run attempted again afterward
(twice) was killed by the machine itself mid-run (a Gradle daemon stopped "by user or operating
system," an `in-progress-*.bin` left where a finished run's binary result store belongs) rather than
failing any test -- an environment resource issue, not a code one, and the machine rules say not to
chase a job killed for memory. The targeted classes stayed the source of truth for harness, per this
story's own "Targeted tests only" instruction.

| Test | Holds |
|---|---|
| `BeliefSummaryTest` (brain) | the three least-confident-first items (design note 5), ties broken by label, an exhausted `Guess` skipped without using the cap, floor and chapter lines formatted from a constructed `Beliefs`, an empty `Beliefs` summarises to nothing, and the fairness property against a real `Brain`/`Observation` (design note 1) |
| `BeliefSummaryContentTest` (overlay) | null and empty summaries are both "not present" (the placeholder case); items, then floor, then chapters, in order; a probability to two decimal places |
| `SafetyFlagsContentTest` | no flags is no chips; chip text states the flag; capped at four; every one of `Brain.safetyFlags()`'s four flags takes a real verdict, with `path:line` citations for each (design note 2); an unrecognised flag falls back to warn; the three verdict colours are `DESIGN.md`'s own |
| `DecisionLogContentTest` | one line per wait (turn, actor, Action, score); newest at the bottom; a goal-change line where the goal differs, and only there; a Mode-change line (constructed, since nothing writes a real one yet); an em dash where a Decider with no Decision leaves no score; an empty history is an empty log |
| `DecisionLogTest` | the same content on a real Panel; auto-scroll to the newest line while the view was already at the bottom, including the very first fill; no auto-scroll once the human has scrolled up; the `ScrollPane`'s drag area does not steal a synthetic tap meant for a window button underneath while input is locked (design note 6) |
| `BoundedLogTest` | within capacity, every record in order; past capacity, the oldest drops and the log is never larger; a capacity under one is refused; `asList()` is a copy |
| `PanelContentTest` (extended) | a full Panel shows the flags' chips (text and verdict), the Belief summary's lines, and the Decision log's lines together, from one Snapshot; no flags hides the row; collapsed hides the flags, the Belief summary and the log the same way it already hid the Goal line and the card |
| `EmbeddedSnapshotTest.a_served_wait` (extended) | the served wait's own record reaches `snapshot.history()`, and the Brain's belief summary reaches `snapshot.beliefSummary()`, at the same point the Decision already did; a second wait grows the history, oldest first |
| `RunLoopRecordTest` (the `fairness-reviewer` subagent's own addition) | `RunLoop.record(null, ...)` still throws on a real Observation's oracle/header mismatch, and still returns the `Wait` it built when there is none -- the one combination (no log open) design note 3's change actually added, which nothing else exercised |

### Mutation battery

A control run of `:api:test`, `:brain:test`, `:harness:test` and `:overlay:test` passed first; then
15 mutants, each planted by hand, run against the tests above, killed, and reverted.

| Mutant | Killed by |
|---|---|
| Q1: `BeliefSummaries.of`'s cap uses `>` instead of `>=` (a fourth item slips through) | `BeliefSummaryTest.least_confident_first_and_capped_at_three` |
| Q2: `BeliefSummaries.topProbability`'s comparator is reversed (most confident first) | `BeliefSummaryTest.least_confident_first_and_capped_at_three`, `.ties_break_by_label` |
| Q3: `BeliefSummaries`' chapter line uses `owed` alone as the denominator, not `found + owed` | `BeliefSummaryTest.floor_and_chapters_are_formatted_lines` |
| Q4: `BeliefSummaries`' floor line drops the `(because)` parenthetical | `BeliefSummaryTest.floor_and_chapters_are_formatted_lines` |
| Q5: `SafetyFlagVerdict.of("hp-low")` returns `WARN` instead of `DANGER` | `SafetyFlagsContentTest.every_known_flag_has_its_own_verdict` |
| Q6: `SafetyFlagsContent.of`'s cap uses `>` instead of `>=` (a fifth chip slips through) | `SafetyFlagsContentTest.capped_at_four_chips` |
| Q7: `DecisionLogContent.of` never emits a goal-change line (the `if` is deleted) | `DecisionLogContentTest.goal_change_gets_its_own_line` |
| Q8: `DecisionLogContent.of`'s `RunLog.Mode` case is dropped (mode changes produce no line) | `DecisionLogContentTest.mode_change_gets_its_own_line` |
| Q9: `DecisionLogContent.waitLine` shows the raw thousandths turn, not divided by 1000 | `DecisionLogContentTest.one_line_per_wait` |
| Q10: `BoundedLog.add` drops its `while` trim entirely (unbounded growth) | `BoundedLogTest.past_capacity_drops_the_oldest` |
| Q11: `BoundedLog.add` trims from the wrong end (`removeLast` instead of `removeFirst`) | `BoundedLogTest.past_capacity_drops_the_oldest` |
| Q12: `DecisionLog.atBottom` always returns `true` (auto-scroll never respects a scrolled-up view) | `DecisionLogTest.does_not_yank_a_scrolled_up_view_to_the_bottom` |
| Q13: `DecisionLog.content`'s `pane.active = !inputLocked` line is dropped (always active) | `DecisionLogTest.does_not_steal_a_synthetic_tap_while_locked` |
| Q14: `Panel.layout`'s log height uses `card.bottom()` instead of `belief.bottom()` as the log's top (skips the flags/belief sections' own room) | `PanelContentTest.full_panel_shows_flags_belief_and_log`'s own section-order position assertions (added alongside this mutant, since the original content-only assertions did not depend on any section's position and did not catch it on the first pass) |
| Q15: `RunLoop.record`'s oracle-consistency check reverts to `log != null && ...` (the pre-story-5.4 early-return behaviour) | `RunLoopRecordTest.oracle_mismatch_throws_with_no_log` (the `fairness-reviewer` subagent's own test, added for exactly this) |

### Fairness review

A `fairness-reviewer` subagent pass was run against the api/brain/harness diff (design notes 1 and 3),
asked specifically whether `BeliefSummary.Item.candidate()` could ever carry a true identity, whether
`beliefSummary()` opens any door beyond what `Beliefs` (story 4.2) already computed, whether the
oracle path is still sound, and what the now-unconditional check in `RunLoop.record` changes.
**Verdict: no violation found (high confidence).**

- `BeliefSummary.Item.candidate()`: fine. `Beliefs.identities()` (`Beliefs.java:458-501`) builds every
  `Guess.odds()` entry purely from `Codex.Knowledge` family/candidate names, filtered by what is
  actually identified and actually in view; no true class or hidden identity is read anywhere in it,
  and `BeliefSummaries.of` takes `odds().get(0)` verbatim. `BeliefSummaryTest.never_names_a_true_identity`
  confirms this against a real `Brain`/`Observation`.
- Whether `BeliefSummary` opens a new door: fine. `BrainDecider.decide` calls
  `brain.beliefs(observation, belief)`, itself `Beliefs.view(Memory.of(belief), observation, knowledge)`
  -- a pure function of the same `Observation`/`Belief` `decide()` already uses. No new game read on
  the render thread; `lastBeliefSummary` and `lastDecision` are set together in `EmbeddedRun.serve()`
  from the same wait.
- Oracle-mode interaction: fine. The summary is derived from the same Observation the Decision was
  made on; if that Observation is oracle-flagged, that is an existing, unrelated invariant, untouched
  by this diff.
- `RunLoop.record` always building/checking the `Wait` even with `log == null`: a fairness-positive
  strengthening (an oracle/header mismatch now throws even in an unlogged embedded Run, where it
  previously did not), not a leak -- but flagged as under-tested: no test exercised
  `record(null, ...)` with a mismatched oracle flag specifically. **Should-fix, addressed:** the
  subagent left `RunLoopRecordTest.java` in the tree closing exactly that gap -- a real `Observation`
  (via `HeadlessDriver`/`Observer`) with its header's `oracle` flipped, holding that
  `RunLoop.record(null, ...)` still throws on the mismatch and still returns the built `Wait` when
  there is none. Verified here: it compiles, passes, and (planted as mutant Q15 above) is genuinely
  the thing that catches a regression of the fix.
- A second should-fix, not yet acted on: `EmbeddedRun.history()` only receives the `Wait` record; a
  `RunLog.Prompt` `RunLoop.record` also writes (when `log != null` and the wait answers a Prompt)
  never reaches `history()`, so the in-memory Decision log and the on-disk Run log can diverge in
  content when logging is on. Not a fairness leak (nothing hidden, both are the same information
  differently scoped) -- recorded in `docs/ideas.md` rather than fixed in this story, since
  `DecisionLogContent`'s own Javadoc already documents that a Prompt "rides beside the wait that
  answered it," and widening `history()` to carry Prompts too is a small, separable follow-up.

No other class in the diff imports outside `org.shatterfish.api`; no `brain`- or `api`-module
`build.gradle` changed; `Deliberator.beliefSummary()` defaults to null, so a non-`Deliberator` Decider
shows nothing rather than a half-filled summary.

### Real launch

`:overlay:launch --agent brain --seed 2000 --class WARRIOR --turn-cap 300 --exit-when-over --window
1600x900 --screenshot s54-1600x900.png`. The Run played to its turn cap (300 turns, 290 waits, deepest
floor 1). The screenshot (`Screenshot.FRAME`, a fixed early frame -- not adjustable per launch, so it
always lands a few waits in) shows the Panel FULL at 1600x900, interface size 1: the Mode strip
(`RUNNING normal — turn 5 floor 1 THINKING`), the Goal line (`explore: floor`), the Decision card
(`step NW 1.0000 frontier 1`, three alternatives at `0.1111` each), the Explain button, the Belief
summary showing its placeholder (`nothing believed yet` -- nothing unidentified was in view at wait
5), and the Decision log scrolled to the bottom, showing turns 2 through 5's own lines
(`turn N bot step NNN 1.0000`). No Safety flags chip is showing at this exact wait, but the Run's own
log (`overlay-runs/v4.0.0-WARRIOR-0-AAA-AAA-CYY-6587783f7f748278-shatterfish.jsonl`) confirms both
`enemy-in-view` and `hungry` were raised later in the same Run (`grep '"flags":'`), so the chip path
was exercised by this Run even though the fixed screenshot frame happened to land before either did.

## Review round (real labels for the Decision log's rows, and no row half clipped)

The reviewer looked at `s54-1600x900.png` and found two things to fix before this story is done:

1. **The Decision log showed raw Actions** (`"step 740"`, `"step 659"`), not the compass direction
   `ActionText` already gives the Decision card (`"step NW"`). The log's own `waitLine` had always
   called `ActionText.of(wait.action(), null)` -- design note 3 gives `EmbeddedRun.history()` the
   exact `RunLog.Wait` records the file gets, and a `Wait` carries only its Observation's *hash*, not
   the Observation itself, so there was never anything to build a real label from.
2. **The top visible row was shown half clipped**, most visibly while auto-scrolled to the bottom
   (`s54-1600x900.png`'s own topmost line): the Panel's own height is a pure function of the screen
   and is essentially never already a whole multiple of one row's pitch, so whatever scroll position
   the `ScrollPane` landed on could clip a row mid-glyph, top or bottom.

### What was built

**Item 1.** A new small public record, `org.shatterfish.harness.agent.ActionContext` (`heroCell`,
`mapWidth`, `actors`, `promptOptions` -- exactly the four things `ActionText` ever reads out of an
Observation), with `ActionContext.of(Observation)`. `BoundedLog.Entry` (`BoundedLog`'s own nested
record, both now `public` so the Overlay, a different module, can read them) pairs a `RunLog` record
with the `ActionContext` its Action needs; `EmbeddedRun.serve()` now calls
`history.add(wait, ActionContext.of(observation))` instead of `history.add(wait)`, capturing the
context once, from the same Observation already in hand, rather than keeping the whole Observation
per history entry (200 of them) for the sake of four fields. `ActionText.of(Action, Observation)`
(the Decision card's own route, unchanged in every caller) now builds an `ActionContext` and
delegates to a new `ActionText.of(Action, ActionContext)` (the Decision log's route); both share one
implementation of `direction`/`target`/`option`, so the two routes cannot silently drift apart.
`DecisionLogContent.waitLine` threads `entry.context()` through instead of a hardcoded null.

Alternatives considered for where `ActionContext` lives: an api record (rejected -- `api` is the
Observation schema, `Action` and named helpers, per `JsonRenderingTest`'s own `HELPERS` discipline,
not a home for an Overlay-facing presentation type); nested inside `overlay.ActionText` itself
(rejected outright -- `harness.EmbeddedRun` is the one building it, at `serve()`, and `harness` may
not depend on `overlay`, only the reverse); keeping the whole `Observation` per history entry
(rejected -- `ActionText` reads four fields out of it, and 200 kept Observations (map tiles, the
whole inventory, the whole journal) for the sake of four ints and two lists is exactly the kind of
carrying-more-than-a-label-needs the coordinator's own phrasing ("carry what ActionText needs")
argued against).

**Item 2.** `DecisionLog.snappedViewportHeight(float height)`: floors the viewport handed to the
`ScrollPane` (`pane.setRect(x, y, width, snappedViewportHeight(height))` in `layout()`) to a whole
number of row pitches (`LINE_PITCH = SIZE + ROW_GAP`), never below one row. A structural fix over
snapping the scroll position instead: the Panel's own height rarely lands on a row boundary either,
so a viewport that is itself a whole number of rows cannot show a partial one at *either* edge,
regardless of scroll position -- auto-scrolled to the bottom (the bug's own screenshot), scrolled to
the top, or dragged to any point between. The unused remainder shows as a few pixels of the Panel's
own translucent background below the last whole row, which reads as room rather than as something
cut off. The alternative the setup itself offered (snap the *scroll position* to line boundaries
instead) was rejected: it fixes the auto-scroll-to-bottom case directly but would need to run again
on every manual drag too (this class's own `content()` already skips its body when nothing changed,
specifically so a human's drag is not fought every frame), so it protects fewer of the cases a
partial row could appear in for the same amount of code.

**Optional (turn repeated on every row): deferred.** Dropping the word or adding a header row would
touch every already-passing `DecisionLogContentTest`/`DecisionLogTest` assertion and the story's own
worked examples above for a cosmetic saving the setup itself called optional; recorded in
`docs/ideas.md` rather than done here.

### Tests

`ActionTextTest.action_context_matches_observation` (new): `ActionText.of(Action, Observation)` and
`ActionText.of(Action, ActionContext)` read the same label from the same screen, concretely (a real
compass direction, a real named target), not merely "the same as each other" by both falling back.
`DecisionLogContentTest.wait_with_context_reads_a_compass_direction` (new): the content function
itself threads a constructed `ActionContext` through to a compass direction.
`DecisionLogTest.row_reads_the_card_style_label_when_context_is_captured` (new): the same, on a real
`DecisionLog` component, and that the raw cell is *not* in the row's text.
`EmbeddedSnapshotTest.a_served_wait` (extended): a real served wait's history entry carries a real,
non-null `ActionContext` whose `heroCell`/`mapWidth` match the Brain's own Observation.
`BoundedLogTest.context_reaches_as_list_unchanged` (new): a context added beside a record reaches
`asList()` as the same object. `DecisionLogTest.viewport_height_is_a_whole_number_of_rows` (new): on
a real Panel, `log.pane().height()` is a whole multiple of the row pitch.
`DecisionLogTest.snapped_viewport_height_floors_and_has_a_floor_of_one_row` (new): the pure function
directly -- unchanged when already exact, discards a partial row's worth of extra height, and never
goes below one row even when given less room than that.

`:overlay:test` (full module) and `:harness:test`'s touched classes
(`BoundedLogTest`, `EmbeddedSnapshotTest`, `RunLoopRecordTest`) both green.

### Mutation battery (review round)

A control run of `:overlay:test` and the three touched `:harness:test` classes passed first; then 8
more mutants (23 in total with the original battery), each planted, run, killed, and reverted.

| Mutant | Killed by |
|---|---|
| R1: `ActionContext.of` drops its null check (NPEs instead of returning null for a null Observation) | `ActionTextTest.every_kind_without_an_observation` |
| R2: `DecisionLogContent.waitLine` ignores its `context` parameter (hardcodes null) | `DecisionLogContentTest.wait_with_context_reads_a_compass_direction`, `DecisionLogTest.row_reads_the_card_style_label_when_context_is_captured` |
| R3: `EmbeddedRun.serve()` calls `history.add(wait, null)` instead of capturing the real context | `EmbeddedSnapshotTest.a_served_wait` |
| R4: `BoundedLog.add` always stores a null context, ignoring the one passed in | `BoundedLogTest.context_reaches_as_list_unchanged` |
| R5: `DecisionLog.snappedViewportHeight` drops its `Math.max(1, ...)` floor | `DecisionLogTest.snapped_viewport_height_floors_and_has_a_floor_of_one_row` |
| R6: `DecisionLog.snappedViewportHeight` rounds up (`Math.ceil`) instead of down | `DecisionLogTest.snapped_viewport_height_floors_and_has_a_floor_of_one_row` |
| R7: `ActionText.direction`'s null-context fallback is dropped (NPEs instead of returning the raw cell) | `ActionTextTest.every_kind_without_an_observation` |
| R8: `ActionContext.of` swaps `heroCell` and `mapWidth` | `ActionTextTest.action_context_matches_observation` |

### Real launch (review round)

`:overlay:launch --agent brain --seed 2000 --class WARRIOR --turn-cap 300 --exit-when-over --window
1600x900 --screenshot s54-review-1600x900.png`. Same tuple as the first launch's, a fresh Run (log
`v4.0.0-WARRIOR-0-AAA-AAA-CYY-4328d6a4bd269ed5-shatterfish.jsonl`); ended at TURN_CAP, 289 waits,
deepest floor 1. The Decision log now reads `turn N bot step N 1.0000` / `step NW 1.0000` -- real
compass directions, not `step 700` / `step 740` -- and the topmost visible row is a whole row, not
one shown cut through the middle of its glyphs the way the first screenshot's was.

### Deferred

To `docs/ideas.md`: a goal-then-no-decision-then-goal sequence's "leaves the remembered goal alone"
half (design note 4) is not pinned by a test of its own; `MIN_PANEL_HEIGHT`'s own worst case still
does not promise every section fits with real content in all of them at once (design note 7); the
Belief summary's lines are single `RenderedTextBlock`s, not real pixel columns the way the Decision
card's rows are (this story's own acceptance criteria do not ask for it, and inventing one for a
value nobody is meant to compare row to row seemed the wrong place to spend the story's own budget);
Safety flags beyond the four `Safety.java` names today (`ok: fighting in corridor`,
`EXPERIENCE.md`'s own aspirational example) still need a Policy to raise them before
`SafetyFlagVerdict` has an `OK` case exercised by anything real; the controls row (5.5 to 5.7) will
need the same `pane.active = !inputLocked` discipline this story's `DecisionLog` and story 5.3's
Explain both needed, which is exactly what `docs/ideas.md` already asked a shared base class to give
every future control rather than each re-deriving it; the review round's own "turn" repeated on
every Decision log row (considered, deferred as not cheap enough to fold in here).
