# Wearing an upgraded armour

Issue #305. The equip Policy now rates an armour at the upgrade level the screen shows, and the Playbook's `upgrade` section has a new field, `armour_pick` (`WORN` or `HEAVIEST`), that lets a scroll of upgrade go to the armour in the pack worth most worn one level higher. These are direction checks (ADR-0012), not a published rate; `goo` and `holdout` were not run.

Tuning slice `--set standard --class <class> --first 40 --salt-base 1000 --baseline origin/main --jobs 2`, played on the current tree against `origin/main`.

| Class | Playbook | reach depth 5 | Goo kills | median turns | deaths by depth 1..7 | upgrades read by depth 5 |
|---|---|---:|---:|---:|---|---:|
| WARRIOR | `armour_pick` WORN, `HEAVIEST` (both) | 33/40, 33/40 | 32/40, 32/40 | 2240.5 | 0/1/3/3/2/15/16 | 89 |
| MAGE | `armour_pick` WORN, `HEAVIEST` (both) | 18/40, 18/40 | 6/40, 6/40 | 1476.5 | 0/1/9/12/13/2/3 | 78 |

Every column of the direction table is identical to `origin/main`'s, for both classes and with both values of `armour_pick` (each pair above is main then current; the two Playbook variants gave the same table). The change is a real fix that these Runs never reach: an upgraded armour spare whose level is shown is rare in 40 Runs. A found armour has a hidden level, and a scroll of upgrade never shows the level of what it is read onto (`Item.java:408-415`), so the equip Policy cannot tell that a spare took a scroll. `HEAVIEST` therefore passes over a spare of hidden level (the rule is on [Upgrades](../rules/upgrades.md)), which is nearly every spare.

The shipped Playbook keeps `armour_pick` `WORN`: no measured gain justifies a change of behaviour. The idea that would open the field to the common case is [Count the upgrades read onto a spare armour](../ideas/2026-10-27-count-upgrades-read-onto-a-spare.md).

What the table cannot show: the tests (`EquipPolicyTest`, `UpgradePlaybookTest`) assert the new behaviour on constructed packs; no Run in the slice put it into play, so the rig numbers say only that nothing else moved.
