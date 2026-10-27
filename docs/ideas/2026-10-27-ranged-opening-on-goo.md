# Throw missiles at an approaching Goo before the melee

Story 7.5 (per-boss playbooks). The ranged Policy returns nothing while Goo is among the enemies in view
(`Ranged.java`, `choose`, the `Goo.NAME` test), so a hero with darts or javelins in the pack walks into the fight with
them unused, and Goo spends its approach turns (it walks at the hero's pace, `docs/rules/combat.md`) at no cost to
itself. A throw opening would spend the turns Goo takes to come on.

What stops it today: the Policy has to decide how far to throw and when to stop and dodge a pump, and both are Goo
facts. They would be fields of the Playbook's `bosses.goo` entry (a throw reach in cells), not code constants, and the
measurement is the same one the story uses: Goo kills over fights on `standard`, with the Wilson interval, plus a
second slice. It was not tried in 7.5 because the entry's four existing fields found no lever on main, and a fifth
field would have been tuned on the same 33 of 40 fights that already convert at 32 of 33.
