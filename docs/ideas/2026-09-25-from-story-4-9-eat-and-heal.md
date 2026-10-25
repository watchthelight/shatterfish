# From story 4.9 (eat and heal)

- **A Codex food table.** The eat Policy's food energies are a hand-written table in `Eat`, cited line
  by line and held to the Codex's item names by `FoodCodexTest`. The Codex generator could extract
  each food class's `energy` initialiser the way it extracts combat figures, and hand it to the Brain
  through `Codex.Knowledge`, so an upgrade that changes an energy fails the Codex drift check rather
  than a re-read.
- **Healing by deduction.** A potion counts as a potion of healing only when the screen shows it
  identified. When `Beliefs` puts an appearance's odds of healing at one (every other identity
  accounted for), a player would drink it with the same confidence; story 4.10, which tests unknown
  items, is where that belongs.
- **Healing out of combat.** The heal Policy drinks only against enemies in view. A hero poisoned or
  bleeding for more than its hit points, with nothing in view, dies with the potion in the pack; the
  potion cures both (PotionOfHealing.java:76-86). Measuring the poison left from the buff's shown turns
  would give a threshold for that case.
- **The heal in progress.** The game shows a floating heal number over the hero each turn a heal lands,
  and the sprite's healing state (Healing.java:61, :107-111), neither of which is in the Observation.
  The heal Policy instead counts waits since the drink it handed over. Carrying the sprite's healing
  state in the hero section (an Observer change, with its leak tests) would let the Brain see a heal
  running, whoever started it.
- **The Vial of Blood.** The trinket spreads a potion's heal over more turns and caps each turn's heal
  (Healing.java:80-82, :91-93). The heal Policy's first-turn and remaining-heal figures assume it is
  absent; the trinket shows in the inventory, so its level could be read and the figures scaled.
- **Shooters by line of fire.** The heal and eat Policies treat a shooting enemy in view as able to hit
  the hero wherever it stands. The game fires only along a clear bolt or projectile line (Ballistica);
  the Brain could trace the line over the tiles the map draws.
- **Cooking.** Mystery meat is eaten last because of its side effects; frozen carpaccio and chargrilled
  meat have none. Cooking it (fire, frost, the alchemy pot) and brewing blandfruit are left to a later
  story, and the table lists blandfruit as never eaten until then.
