# From story 5.2 (the Panel frame)

- **The inventory pane in the executor.** With the full interface (2) the game hands an item selector
  to the inventory pane (`GameScene.java:1673-1674`), which no Action names, so the Overlay plays on
  the mixed interface (1). Teaching the executor and the Observer the pane's selector would let the
  Overlay play on the layout `DESIGN.md` draws, with the Panel left of the pane.
- **`DESIGN.md` and the full layout.** Its Layout & Spacing section places the Panel "below the status
  pane's row"; with interface size 1 or 2 the status pane is at the bottom (`GameScene.java:487`), and
  the Panel's top is below the menu pane and the boss bar instead. The design text could say so.
- **Screenshots for every story.** `--screenshot` writes one frame; a flag for a frame at a given wait,
  or one per floor, would let a later story's review see the Panel's content without a person at the
  window.
- **A Panel narrower than the strip's minimum.** In the mobile layout on a very narrow window the Mode
  strip is squeezed below 160; it keeps its place but its content (story 5.3) will need to elide.
