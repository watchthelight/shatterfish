# From story 5.4 (Safety flags, Belief summary and Decision log)

- **A goal-then-no-decision-then-goal sequence is not pinned by a test.** `DecisionLogContent.of`
  leaves the remembered goal alone on a `Wait` with no `Decision` (a plain, non-`Deliberator` agent),
  rather than clearing it to null and re-emitting a spurious "goal changed" line once a real Decision
  returns. The reasoning is in the story file's design note 4 pre-mortem; no test drives a history with
  exactly that shape (Decision, no-Decision, Decision-with-the-same-goal) to hold it directly.
- **`PanelLayout.MIN_PANEL_HEIGHT`'s worst case is still not every section's worst case.** The revised
  constant (design note 7) adds only the Decision card's own unavoidable single line to story 5.2's
  floor; the Goal line, the Safety flags row and the Belief summary can each still collapse toward
  zero, and the constant does not promise a Panel that clears it looks uncrowded once a Goal line and
  several Safety-flag chips are genuinely showing. A story that wants a tighter guarantee has one
  number to change, with a comment already saying what it does and does not promise.
- **The Belief summary's rows are not real pixel columns.** Unlike the Decision card's action/score/
  reason triads (UX-DR5), each Belief summary line is one `RenderedTextBlock`; this story's own
  acceptance criteria did not ask for a column a human is meant to compare row to row, and inventing
  one seemed the wrong place to spend the story's budget. A future story that wants the probabilities
  to line up visually has `DecisionCard`'s own column-measuring technique to copy.
- **`SafetyFlagVerdict.OK` has no real case yet.** `Safety.java`'s four flags today (`hp-low`,
  `enemy-in-view`, `hungry`, `starving`) are all conditions to watch, never a flag saying a situation
  is fine (`EXPERIENCE.md`'s own aspirational example, `"ok: fighting in corridor"`, is not something
  any Policy raises today). `SafetyFlagVerdict.OK` and its green chip exist and are tested
  (`SafetyFlagsContentTest.verdict_colours_are_designmd`) but nothing in a real Run shows one yet.
- **A second, general instance of the bug the fairness review found in Explain, now doubly confirmed.**
  Story 5.3's `docs/ideas/` entry asked for a shared base that gates a Panel control's own `active`
  from `InputLock` once per frame, rather than each control re-deriving `pane.active = !inputLocked` or
  `button.active = ... && !inputLocked` for itself. `DecisionLog`'s `ScrollPane` is the second real
  instance of exactly the pattern that entry predicted (the first being Explain); the controls row
  (5.5 to 5.7) will be the third, fourth and fifth, and is a good place to finally build the shared
  base rather than writing the same one-line gate a third time.
- **`EmbeddedRun.history()` never carries a `RunLog.Prompt`.** The `fairness-reviewer` subagent's
  second should-fix: `RunLoop.record` also writes a `Prompt` record (when there is a log and the wait
  answers one), but only the `Wait` reaches `BoundedLog`, so the in-memory Decision log and the
  on-disk Run log can diverge in content once logging is on and a Prompt is answered. Not a fairness
  leak -- both carry the same information, just differently scoped -- and `DecisionLogContent`'s own
  Javadoc already documents the choice ("a Prompt rides beside the wait that answered it"). Widening
  `history()` to a small ordered structure carrying both kinds (or teaching `BoundedLog` to accept a
  `Prompt` beside its `Wait`) is a small, separable follow-up, not folded into story 5.4.
