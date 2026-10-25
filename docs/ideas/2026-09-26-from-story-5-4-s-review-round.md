# From story 5.4's review round

- **The word "turn" repeats on every Decision log row.** Considered (the coordinator's own review
  offered a header row or dropping the word, "only if it's cheap"): dropping it is a one-line change
  to `DecisionLogContent.waitLine`, but every already-passing `DecisionLogContentTest`/`DecisionLogTest`
  assertion and the story file's own worked examples spell out the current format, so the real cost is
  in what it touches, not the line itself. Deferred rather than folded into the review round.
- **`PanelLayout.MIN_PANEL_HEIGHT` still assumes the Decision log's own line is `SIZE` (6) UI pixels,
  not the ~6.5 the small font actually measures at.** The review round's third pass found (by
  running the real game, not by reading the constant) that a `RenderedTextBlock`'s real height at
  the small size is not exactly `SIZE`; `DecisionLog` itself no longer assumes otherwise anywhere
  (`rebuild`, `minHeight` and `viewportHeightFor` all work from the real, measured, already-`PixelScene.align`-ed
  row positions, never a nominal pitch), but `PanelLayout.MIN_PANEL_HEIGHT` (story 5.2, extended by
  this story's own first pass) still budgets the log's three-line floor as `3 * (6 + 2)`, which
  slightly under-states the real room three lines need. A Panel that just clears the constant's own
  threshold could still be a few pixels short in practice; a story that wants the constant itself to
  stop guessing has one number to change, once a real measurement is available without booting the
  game to get it (the constant is evaluated at class-load time, before any font exists to measure).
