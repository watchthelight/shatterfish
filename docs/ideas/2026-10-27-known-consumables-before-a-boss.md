# Use a known consumable before a boss fight

Story 7.5 (per-boss playbooks). The story's list of boss claims not yet used by a Policy (in the story file) includes
the potions and scrolls whose effect the Codex knows once identified, for instance a known potion of healing held
back for the fight (the heal Policy already drinks it when health falls to the share named by `drink_per_mille`), a
known scroll or potion that changes the fight before the first blow, and unknown items, which are not known and so
are not a boss field at all.

The Playbook format names a "known consumable" field for a boss. 7.5 did not add one: no field there changes play
without a Policy that reads it, and the only consumable Policy a boss fight reaches today is the heal. The idea is to
choose the consumables worth saving by their Lore claims and give the boss entry a list of kinds to use at the
fight's start, measured on `standard` as the story does. It needs the `Answers` path for any item that opens a window.
