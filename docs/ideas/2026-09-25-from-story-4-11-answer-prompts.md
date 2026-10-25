# From story 4.11 (answer prompts)

- **Read a known scroll of upgrade onto the worn gear.** The upgrade window's rule only confirms a target
  another Action chose; no Policy yet reads a known scroll of upgrade (or magical infusion) onto the worn
  weapon or armour, which is the obvious target for a Brain that fights in melee.
- **The item selector as a Prompt.** An unknown scroll that identifies itself on reading (upgrade, remove
  curse, identify, transmutation, enchantment) opens the bag, which the executor sends away because no Action
  answers it, and the scroll is lost. Surfacing a selector the game opened after a plain `UseItem` as a Prompt
  whose options are the selectable items would let the Brain choose the target instead.
- **Cast the Cleric's spells.** The spell list is left unanswered; its spells are icons with hover text, which
  the section could carry by name the way the guess window's icons are, once a Policy wants to cast.
- **Buy in shops.** A shop is always left. A Brain with a model of prices and needs (food, a healing potion)
  could buy; the sell flow opens the bag and needs the selector above first.
- **Learn from a wrong guess.** A stone of intuition's wrong guess is logged ("Your guess was incorrect.",
  `items.properties:1442`) and rules one identity out for that appearance. The Beliefs do not read it yet, so
  a second stone could guess the same identity again; a fact "not X" per appearance would narrow the odds.
- **The item selector as a Prompt, again.** The upgrade window the game chains after an upgrade, while more
  upgrade items are held, is answered by a Brain error, because its "Back" reopens the selector no Action
  answers. With the selector as a Prompt, the Brain could back out and keep the scroll.
- **A use for a known scroll of identify (story 4.13).** Unknown scrolls are read onto the worn armour, so a
  scroll of identify among them is spent identifying itself. No Policy reads a known identify today, so
  nothing is lost yet; once one does (an unknown ring or wand worth knowing), the read onto the armour should
  hold back while identify is likely.
- **Unknown scrolls and the Mage's staff (story 4.13).** Unknown scrolls follow the upgrade's target, the weapon
  or the armour, except the Mage's staff, whose wand an unknown transmutation would take
  (ScrollOfTransmutation.java:158-159). For the Mage every unknown upgrade therefore goes onto the armour; once
  the Beliefs can rule transmutation out for an appearance, the staff could take its share.
- **The chained upgrade window shares the stack (story 4.13).** It spends every scroll held on the item the
  first went onto. With the item selector as a Prompt, the Brain could back out after one and share the rest.
