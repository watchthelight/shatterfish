# From issue #163's fairness review (a locked-exit refusal starving explore)

- **`Memory.triedExits` and `Memory.chase` are two branches' own answer to the same shape of
  question -- where the fight Policy should not walk back to, where the descend Policy should not
  try again -- landing in the same VERSION bump (#163 and #174 both called it 12) and unioned here
  into 13.** Both are small, per-floor `Spot`-keyed facts a Policy reads before acting rather than
  after being refused; a future field of the same shape (a heap tried and refused, say, beyond the
  `Refused` record's own one-shot use) could look at either for the pattern rather than inventing a
  third.
- **The review's fix reads `NoteKind.KEY` off the journal instead of adding a `Memory.tried`-style
  cell for the *reason* a key is missing.** This works because a worn key is the only kind Notes ever
  shows for the boss floor and there is only one locked exit to ask about; a door-fetching story with
  several locked cells and several key kinds in play at once (the iron/crystal idea above) would need
  to know which key answers which door, which the journal alone does not say -- back to a
  `Memory`-carried fact, not the screen.
