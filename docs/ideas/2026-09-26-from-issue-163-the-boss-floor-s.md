# From issue #163 (the boss floor's worn key)

- **Iron keys and crystal keys are not fetched the same way.** The fix values a worn key's heap
  (`Pickup.WORN_KEY`) enough to cross the whole floor for and teaches the descend Policy to
  walk to a locked exit and unlock it, but a `LOCKED_DOOR` (iron key) or `CRYSTAL_DOOR` (crystal key)
  behind which the Brain wants to go is left exactly as it was: `ValidActions` still offers `Unlock`
  at one, and nothing picks the matching key up for it or walks it there on purpose. This was in
  scope for #163 only as far as not breaking it, which needed no change (the fix touches nothing an
  iron or crystal door's path already ran through). Extending it is more than "give it the same
  worth": an iron or crystal key is not always worth fetching (a `CryptRoom` or `LibraryRoom`'s key
  opens a door to that room's own prize, not a floor exit, so a Brain that never means to open that
  door has no use for its key), and doors, unlike the boss exit, are not always on the way anywhere
  the Brain would otherwise walk. A future story would need to weigh a door's key by what is judged
  worth reaching behind it, not by a flat bonus.
