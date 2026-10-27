# Choose water or dry ground for the Goo fight

Story 7.5 (per-boss playbooks). Goo heals while it stands in water and is hurt (`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Goo.java:109-110`, the water test in its
`act`), and the caustic ooze it puts on the hero is washed off in water (the `goo-wash` Rule). The two pull apart: the
hero wants water to wash and Goo wants it to heal. The Policy today goes to water only to wash, within
`bosses.goo.wash_range`, and meets Goo wherever it is (`meet_range`).

A stance field ("water", "dry", "either") would let the fight Policy pick the ground the fight is taken on, where the
ground is on the screen. The story's four numeric fields moved nothing on the tuning slice (the reach and the Goo kills
were the same in every variant), so a stance is not expected to either until the Goo's conversion falls below the 32 of
33 it has on main; it is worth trying when 4.14's gate work changes that.
