---
id: identify-1
topic: identification
group: identify
subject: "Scroll of Identify"
source_url: https://www.cyberly.org/game-cheats/pc/shattered-pixel-dungeon.html
source_type: other
date: unknown
retrieved: 2026-10-29
variant: unknown
version_claimed: "unspecified"
tier: 3
graded_against: v4.0.2
status: current
claim: "Shops, drops and spawns give enough scrolls of identify to have almost every potion, scroll and ring identified before the third region."
quote: "You usually get enough identification scrolls from shops/drops/spawns"
cites: []
implemented_in: []
---

## Verdict against `v4.0.2`

Not settled by the code. What the code does say: every class starts with the scroll of identify known (`HeroClass.java:117`); it is the heaviest random scroll, 3 of 15 cards in each deck (`Generator.java:362-377`), which with about 0.8 random scrolls a floor (`RegularLevel.java:380`, `Generator.java:221-252`) is about one every six floors; the shop on floors 6, 11 and 16 always stocks one (`ShopRoom.java:281`); and a library room's first prize is identify or remove curse (`LibraryRoom.java:49-79`). Whether that is "enough" depends on how many the hero reaches and buys, which only the Rig can measure. The Brain therefore reads a held identify scroll onto an unknown potion at once but does not hold potion tests for one it has not found (`TestItem.identifying`). The page also advises a scroll of divination, which Shattered Pixel Dungeon does not have, so the page may describe a variant.
