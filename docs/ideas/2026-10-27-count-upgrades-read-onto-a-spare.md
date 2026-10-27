# Count the upgrades read onto a spare armour whose level is hidden

Issue #305 lets the upgrade allocation send a scroll to the heaviest armour in the pack, but only to a spare whose level the screen shows. A scroll of upgrade never shows the level of the piece it is read onto (`Item.java:408-415`), so a spare of hidden level that has taken a scroll still reads as level 0 to the equip Policy, which would never put it on for the upgrade.

A person at the same screen would simply remember "I read two scrolls onto this": the Brain can keep that count in its Memory, by item slot and name, and let the equip Policy add it to the hidden level. That is information a human has, so it keeps parity. It would open the `HEAVIEST` pick to the common case of a found armour that has not been worn long enough to be identified.

Open questions: the count has to survive the item moving in the pack and being renamed when a curse lifts (`Armor.java:471-472`), and a hidden negative level, which a person cannot know, stays unmodelled. Measure with `:rig:direction` on the standard set before adopting, as `armour_pick` was.
