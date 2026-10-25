# From issue #170 (the chasm confirmation)

- **A jump as a way down.** A chasm is a faster way to the next floor than the stairs: the hero lands
  crippled and bleeding and loses half its health or more (`Chasm.java:131-152`), and the floor it left
  is unfinished. The Brain never steps onto a chasm today. A descend Policy that meant to jump would
  hand over its Step onto the chasm past `Brain.decide`'s filter, and the prompt Policy already presses
  "yes" after such a Step (`Answers.jumpMeant`). When the trade is worth it (a floor spent, the stairs
  far, the hit points full) is the rig's question.
- **Other windows that ignore early taps.** The chasm's confirmation is the only window at `v4.0.0`
  whose buttons count `Game.elapsed` before they act. `EmbeddedRun` holds every Prompt's answer until
  its window has been in front more than 0.2 s, so another such window would be covered if its guard
  is no longer than that; a longer guard would need the constant raised.
