# From story 5.9 (the human half: recording, the shadow, notes)

- **A tap that interrupts a walk or a rest.** The unmodified game stops a long walk or a rest when the
  player taps (`CellSelector.select`'s `GameScene.cancel()` branch). A HUMAN Run's input lock holds
  presses between waits, because that tap is not an input any Action records, so a person cannot
  interrupt. Recording it needs a notification in `GameScene.cancel` (row 11) and an Action kind the
  executor can issue at the next wait; takeover (story 5.8) is the natural home.
- **`MoveTo` in the executor.** A click on a distant cell is the commonest human input and is marked
  unsupported, since the executor offers only single Steps and the walk spans turns with no waits in
  them. An executor that clicks the far cell as the person did would make most human Runs replayable;
  it needs a rule for what a Brain may be offered (a valid set of every known cell is too large).
- **Targets the valid set leaves out.** A throw at an empty cell, an armour or weapon ability, a
  quickslot assignment and resuming an interrupted walk are all marked unsupported. Each is a small
  extension of `ValidActions` and the executor.
- **A window's button the Observer does not list.** The first real HUMAN launch met, after the
  tutorial's journal hint, a window whose button a tap pressed and no Prompt option named; the rule
  that only the wait's own Prompt window can be answered now keeps a window the person opened out, but
  a Prompt whose buttons the Observer reads short would still be marked unsupported rather than read.
- **The notes key is fixed at N.** Story 5.11's hotkeys would make it bindable, with the game's own
  key-binding window.
- **A human's log replays only where the frames are the headless driver's.** On the desktop the
  exceptions of stories 5.1 and 5.2 stand; story 5.13 and issue #169 close them for a human's Run as
  for the Brain's.
