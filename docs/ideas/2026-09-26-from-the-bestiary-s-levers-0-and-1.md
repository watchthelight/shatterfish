# From the bestiary's levers 0 and 1

- **The step-away from Goo on its sealed floor never ends.** Goo walks at the hero's pace
  ([Goo](../bestiary/bosses.md#goo), tagged `speed: normal`), so the
  fight Policy's retreat away from it keeps it adjacent and harmless for ever, and the seal refuses
  the stairs; one tuning Run under lever 1's variant B did it for 18,227 waits. A rule for sealed
  floors alone (no stairs, so no retreat that is not a fight) would end it without A's early deaths.
  Done in issue #180: on a sealed floor the fight Policy retreats only to water (oozed) or a corridor
  (two or more enemies), at most `Fight.SEALED_RETREATS` times, and otherwise fights.
- **Break contact instead of the stairs-only rule.** The tags say what ends each enemy's pursuit
  (`breakContact`: a door, out of sight, the stairs). A same-speed retreat that heads for the nearest
  door or corner that breaks sight, rather than only for the stairs, is the variant of lever 1 that
  keeps what walking away was buying.
- **A crab fight far from the stairs.** Under the fast rule an unfavourable crab fight with the
  stairs far off is fought where it stands and sometimes lost sooner than the walk would have been;
  a thrown stone as it closes (lever 4) or the door ambush (lever 2) is what the tags suggest there.
- **The bestiary on a branch floor.** The rotation's depths are the main branch's, so on
  the Imp's vault (branch 1 of depths 16-19, Dungeon.java:356-368) the rotation no longer ranks the city's
  classes first (the lookup takes the branch), but among the classes placed nowhere the vault's ghoul
  and golem still lose to the King's by class name. Nothing the Brain reads differs between them today
  (`BestiaryKnowledgeTest`); a tag that splits them, such as `outrunnable` for the golems, needs a
  branch-to-class fact before a Brain question reads it.
- **A Rule for immobile enemies.** The fight Policy has always left enemies that never walk out of the
  chokepoint count, with no row in the Rules index; the bestiary's `immobile` tag and its card
  citations could back a Rule, read at the pin from the IMMOVABLE property's uses in `Char`.
