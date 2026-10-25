# Gate the hero and item entry points against a game controller while PAUSED

Story 5.5 put the input gate (hook row 9) at `CellSelector.select` and `CellSelector.processKeyHold`.
That shuts out every click and held direction key, and with the Overlay's `InputLock` in front of the
game's input multiplexer it leaves a mouse or a keyboard no way to the toolbar, quickslots or inventory
pane. A game controller's button is the remaining gap: it writes the key queue past the lock
(`SPD-classes/.../input/ControllerHandler.java:122-134`) and can press a quickslot, which calls
`Item.execute` directly.

The obvious fix is to ask the gate at the five entry points hook row 11 already marks (`Hero.handle`,
`Hero.rest`, `Hero.search`, `Hero.upgradeTalent`, `Item.execute`). It was not taken in 5.5 because a
Prompt's button is answered by a pointer event the executor posts, which the game handles on a later
frame, outside the executor's call. If that button reached one of those methods (a window that runs an item
action, say), a shut gate would drop the Brain's own answer. The executor's open window would have to be
held until the answer lands before this is safe.

Alternative with no upstream edit: filter `ControllerHandler`'s listener in the Overlay the way
`InputLock` filters the multiplexer, if libGDX lets a listener be put first.
