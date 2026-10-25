# From story 5.3 (the Mode strip, Goal line and Decision card)

- **Per-column text for story 5.4's rows too.** The review found that a single padded
  `RenderedTextBlock` per row does not actually align in the pixel font (it is proportional, not
  monospace), and fixed the Decision card's rows with a `RenderedTextBlock` triad per row (action,
  score, reason) whose column edges come from each block's own measured width
  (`DecisionCard.refresh`/`.layout`). Story 5.4's Belief rows and Decision log will want the same
  treatment; nothing here shares the column-measuring code between row kinds yet, so each is its own
  small implementation rather than one shared row component.
- **The Mode strip's turn and floor stay a padded string on purpose.** `Columns.number`, unchanged:
  the strip is one line, so there is no second row to misalign against, which is the property UX-DR5
  is protecting; a future story that puts a second line of numbers beside it (unlikely, since the
  strip is meant to stay one line) would need to revisit this.
- **Plain fallback labels for some Action kinds.** `ActionText` gives `Interact`, `PickUp`,
  `OpenChest`, `Buy`, `Unlock` and `DismissPrompt` short, generic words (`"interact"`, `"open"`, ...)
  since the epic and its review named only Step, Attack, the item-use kinds, Descend/Ascend, Rest,
  Search, Wait and AnswerPrompt explicitly. A later story wanting a chest's or a shop's own detail
  (a cell, a stock item) extends the one exhaustive switch rather than searching for where labels live.
- **Real Mode, speed mode and THINKING.** `ModeState.of` reads a placeholder Mode (always RUNNING) and
  speed mode (`normal`, with a placeholder interval); only the turn, the floor and the live THINKING
  flag are real, from `EmbeddedRun.snapshot()`. Stories 5.5 to 5.7 give PAUSED, HUMAN, Next Step, Run N,
  Human play speed and Fast their own controls; `ModeState` and `ModeStripContent` already handle every
  value, so those stories add a caller rather than a format.
- **Explain while a Run plays.** The Decision card's Explain `RedButton` is inactive
  (`explainButton.active`, not only `.visible`) while `InputLock` is holding (every Run but one that
  has ended), so neither a human's click nor a stray tap can reach it. It works once the Run is over,
  to read the last Decision. Story 5.5's input-gate hook is for hero-directed input, not Panel
  buttons; whichever story gives PAUSED a real click (5.6's controls row) should route one to the
  Panel too, or give the Panel its own listener that the gate does not close.
- **A second, general instance of the bug the fairness review found in Explain.** Any Panel control
  with a `PointerArea` (every future button in 5.6's controls row, the speed selector, the steppers)
  needs the same `active`-while-locked discipline Explain now has, since `ActionExecutor.press`
  bypasses `InputLock` for every window button, not only the ones that happen to overlap the Panel
  today. A shared base (a "Panel button" that gates its own `active` from one flag `PanelDock` sets
  once per frame) would make this a property of the class rather than something each new control has
  to remember.
- **The Decision card's own height can overflow the Panel's.** Nothing below it (Safety flags, the
  Belief summary, the Decision log) exists yet, so there is blank room today; story 5.4 will need the
  Panel to give the sections above it only what they ask for and the Decision log the rest, per
  UX-DR2's "never fewer than three lines," which today's fixed layout does not yet arbitrate.
- **The Goal line's two-line wrap.** `DESIGN.md` says "wrapping to two lines at most"; `GoalLine` wraps
  through the game's own `RenderedTextBlock` but does not cap it at two lines or ellipsize a third, since
  a Brain's goal today is always the short label `DecisionShapeTest` holds it to (at most 40 characters).
  A longer goal from a future Policy would need the cap this story left unenforced.
