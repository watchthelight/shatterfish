# A Playbook reach for the Spirit Bow, and shots from beside the enemy

Issue #309. The shoot Policy (`Shoot.java`) fires the bow at the nearest awake, mobile enemy at least two cells away
in a clear line and states no reach, because the arrow flies as far as the hero sees. Two choices were left as they
were, and each is a measurement for a later story.

A reach in the class entry of the Playbook, `kit.shoot_reach` beside `kit.zap_reach`. A shot at the far end of the room
gives the enemy more turns to wake and close than a throw would, and a Huntress may do better saving the turn for a
position. It was not added here: the field changes the Playbook's canonical form and every hash that embeds it, which
is a larger diff than the offering fix, and the first table has nothing to tune it against yet.

A shot at an adjacent enemy. The missile accuracy factor is 0.5 beside the target against 1.5 away from it
(`MissileWeapon.java:215-233`), so the Policy leaves the adjacent enemy to the fight Policy and the gloves. Whether the
bow's damage (1 to 6 at level 0, `SpiritBow.java:189-203`) beats the gloves at half the accuracy is a number the Huntress
table can show once the class has a Playbook entry.
