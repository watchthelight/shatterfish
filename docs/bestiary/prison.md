# Bestiary: Prison (depths 6-9)

One card per enemy. Mechanics are Tier 1: read from the pinned code at `v4.0.0` and cited.
Tactics are derived from the mechanics and name the lines they rest on. Community notes are
forum and wiki claims graded against the code: Tier 1 confirmed, Tier F contradicted (struck),
Tier 3 not settled. The [bestiary index](index.md) has the cross-cutting rules, the one-line
tactic for every enemy and the tag vocabulary of `tactics/bestiary.json`.

This region's boss is on the [bosses page](bosses.md): [Tengu](bosses.md#tengu).

## Enemies

- [Skeleton](#skeleton) (6-9): Open with a surprise hit if it is asleep. Soften it with ranged attacks as it walks in, since its low evasion (9) makes throws land.
- [Crazy thief](#thief) (6-9; rare 2.5% on 4): Treat it as an item threat rather than an HP threat. Hit it asleep or at range before it reaches you (HP 20).
- [Crazy bandit](#bandit) (6-9 (2% alternate of Thief)): Play it like a thief but stricter: open at range or with a surprise hit and burst it down (HP 20) before it touches you.
- [DM-100](#dm100) (7-9): Like a gnoll shaman: get next to it. Approach from outside its line of fire (around corners and through doors) or wait behind a corner until it walks up to...
- [Prison guard](#guard) (7-9): Either meet it in melee deliberately (walk into adjacency so the chain never fires) or snipe it from 5+ tiles and retreat around a corner as it closes.
- [Necromancer](#necromancer) (8-9): Shoot it from 5+ tiles as it walks in. Once it summons, ignore or body-block the skeleton and push through to the necromancer (it has no attack).
- [Spectral necromancer](#spectralnecromancer) (8-9 (2% alternate of Necromancer)): Top priority target. Burst it from range before it reaches 4 tiles if you can; otherwise charge straight at it, ignoring wraiths except those blocking the...
- [Necromancer's skeleton](#necromancer-necroskeleton) (8-9 (summoned by Necromancer)): Treat it as a distraction: body-block or walk past it to the necromancer.
- [Tengu](bosses.md#tengu) (boss 10): Phase 1: after each jump, note the flashed traps, walk the trap-free path to Tengu and fight him adjacent, where his accuracy halves, until he hits 100 HP.

## Rules these cards rely on

Tier 1, cited. Written for this region by the reader who verified its cards; the
[index](index.md#cross-cutting-rules) states the ones every region shares.

- **Surprise attacks.** A mob is surprised by the hero when the hero is invisible, or the mob has not seen its enemy (enemySeen false), or the hero is outside the mob's field of view; and the hero can surprise-attack only with a weapon whose STR requirement is met and that is not a Flail. A surprised or paralysed mob has defenseSkill 0, so the hero's accuracy roll always wins. Sleeping mobs set enemySeen=false every turn; wandering mobs set it false while wandering. An invisible attacker gets infinite accuracy. Some weapons add damage on surprise (Dagger, Throwing Knife: 75%-of-range to max), and the Sucker Punch talent adds damage. <small>`core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/hero/Hero.java:744-752`, `core/…/mobs/Mob.java:1237`, `core/…/mobs/Mob.java:1297-1298`, `core/…/actors/Char.java:627-630`, `core/…/actors/Char.java:662`, `core/…/actors/Char.java:678`, `core/…/melee/Dagger.java:66-71`, `core/…/missiles/ThrowingKnife.java:54-59`, `core/…/hero/Talent.java:882-887`, `core/…/mobs/Mob.java:814-830`</small>
- **Waking and noticing.** Level-generated mobs start SLEEPING; mobs spawned later start WANDERING. A sleeping mob wakes each turn with chance 1/(distance + stealth) against the least stealthy visible hostile; any negative buff wakes it; taking damage sets it to WANDERING and alerted. A wandering mob notices a visible enemy with chance 1/(distance/2 + stealth). Base stealth is 0 (plus the Obfuscation glyph). Flying characters at distance &gt;= 2 cannot wake sleepers. A wandering mob that notices the hero switches to HUNTING without spending time and acts again at once; at distance 1 the notice chance 1/(0.5+stealth) is certain for stealth &lt;= 0.5. <small>`core/…/mobs/Mob.java:124`, `core/…/levels/Level.java:774-775`, `core/…/mobs/Mob.java:1189-1197`, `core/…/mobs/Mob.java:1200-1246`, `core/…/mobs/Mob.java:902-910`, `core/…/mobs/Mob.java:1268-1284`, `core/…/actors/Char.java:1280-1286`, `core/…/mobs/Mob.java:1286-1295`</small>
- **Accuracy vs evasion.** hit() rolls Random.Float(attackSkill) against Random.Float(defenseSkill); ties hit. Magic attacks (hit(..., magic=true)) double the attacker's accuracy. Bless x1.25, Hex x0.8, Daze x0.5 on the respective roll. An invisible attacker that can surprise-attack gets infinite accuracy. Infinite evasion beats infinite accuracy. <small>`core/…/actors/Char.java:615-685`</small>
- **Speed and attack delay.** Base speed is 1 for every Char; Cripple halves speed, Stamina x1.5, Adrenaline x2, Haste x3, Dread x2 (plus Swiftness/Flow/Bulk glyphs). The hero's speed also takes Ring of Haste, armor speedFactor, Momentum and Nature's Power. Mobs spend 1/speed per step and attackDelay() per attack; Adrenaline divides a mob's attack delay by 1.5. A same-speed mob cannot be outrun by walking. <small>`core/…/actors/Char.java:175`, `core/…/actors/Char.java:770-783`, `core/…/mobs/Mob.java:753-757`, `core/…/mobs/Mob.java:759-779`, `core/…/mobs/Mob.java:1300-1303`, `core/…/mobs/Mob.java:1352-1356`, `core/…/mobs/Mob.java:864-867`, `core/…/hero/Hero.java:713-741`</small>
- **Reach.** By default a mob can attack only adjacent targets (champion buffs may extend reach). <small>`core/…/mobs/Mob.java:558-568`</small>
- **Line of fire (Ballistica).** PROJECTILE lines stop at the target cell, the first character, or solid terrain; MAGIC_BOLT lines stop at the first character or solid terrain. Any character standing in the line (ally or enemy) intercepts it. Closed doors are solid, so they block both. A mob whose canAttack needs such a line (DM-100, Tengu) simply does not fire when the line is blocked; the blocker is not hit. <small>`core/…/mechanics/Ballistica.java:42-51`, `core/…/mechanics/Ballistica.java:115-151`, `core/…/levels/Terrain.java:90`, `core/…/mobs/DM100.java:75-79`</small>
- **Doors.** A closed door is PASSABLE \| LOS_BLOCKING \| SOLID; an open door (someone standing in it) is only PASSABLE. Every walking mob can path through doors, but a closed door breaks line of sight and line of fire. The door closes again when the occupant leaves. <small>`core/…/levels/Terrain.java:90-91`, `core/…/actors/Char.java:1311-1313`</small>
- **Breaking pursuit.** A hunting mob that loses sight of its enemy keeps walking to the last known position; when it cannot get closer and still cannot see the enemy it shows the lost icon and returns to WANDERING. A mob that cannot currently see the hero is surprised by the hero's next attack. <small>`core/…/mobs/Mob.java:1326-1363`, `core/…/mobs/Mob.java:1387-1407`, `core/…/mobs/Mob.java:875`</small>
- **Stairs.** On a level change only allies are carried along (directable allies anywhere, intelligent allies within 5 tiles); hostile mobs stay on their level, so stairs always break contact. <small>`core/…/scenes/InterlevelScene.java:654`, `core/…/scenes/InterlevelScene.java:678`, `core/…/mobs/Mob.java:1686-1705`</small>
- **Fleeing.** A fleeing mob runs from the enemy's position; if it cannot see its enemy it escapes when 1+Random.Int(distance to target) &gt;= 6. If it has nowhere to run and is not terrified it turns and fights. <small>`core/…/mobs/Mob.java:1441-1486`</small>
- **Damage reduction.** Physical attacks via Char.attack subtract the defender's drRoll(); the hero's DR comes from armor (minus 2 per missing STR) and weapon defense factor. Wand bolts such as Magic Missile call damage() directly and skip DR and the hit roll. <small>`core/…/actors/Char.java:388-390`, `core/…/hero/Hero.java:643-655`, `core/…/wands/WandOfMagicMissile.java:56-62`</small>
- **Properties.** INORGANIC: immune to Bleeding, ToxicGas, Poison. ELECTRIC: resists Wand of Lightning, Shocking, Potential, Electricity, Shocking Dart, Shock Elemental. BOSS: resists Grim, Grim trap, Scroll of Retribution, Scroll of Psionic Blast; immune to AllyBuff (charm-to-ally/corruption) and Dread. Each resistance halves the effect (they multiply). <small>`core/…/actors/Char.java:1356-1371`, `core/…/actors/Char.java:1413-1441`</small>
- **Burning and water.** Burning detaches when the burning character stands in water and is not flying. <small>`core/…/buffs/Burning.java:99-100`, `core/…/buffs/Burning.java:178-181`</small>
- **Cleric Guiding Light.** An Illuminated mob has defenseSkill 0 against a Cleric using a weapon whose STR requirement is met (or against non-hero attackers); the next hit consumes it and deals bonus damage with Searing Light. <small>`core/…/mobs/Mob.java:783-794`, `core/…/actors/Char.java:423-431`</small>
- **Actor scheduling.** The actor with the lowest time acts next; on a tie the higher actPriority goes first (hero 0, mobs -20), so at equal times the hero acts before mobs. A mob action that never calls spend() leaves its time unchanged, so it acts again immediately. Mobs added by GameScene.add wait until the next whole turn plus any delay. <small>`core/…/actors/Actor.java:50-52`, `core/…/actors/Actor.java:266-268`, `core/…/scenes/GameScene.java:1186-1195`</small>

??? note "Rules corrected during verification (4)"

    - Speed rule: added Dread x2 and glyphs; resolved the open question on hero speed (Hero.java:713-741).
    - Added InterlevelScene.java:654 (descend) to the Stairs rule; 678 is the fall path.
    - Infinite accuracy for invisible attackers requires canSurpriseAttack (Char.java:627-630).
    - Added the Actor scheduling rule used by the Guard and NecroSkeleton corrections.

## Skeleton {#skeleton}

`actors.mobs.Skeleton` · depths 6-9 · **Tactic:** Open with a surprise hit if it is asleep. Soften it with ranged attacks as it walks in, since its low evasion (9) makes throws land.

**Stats** (from the [Codex](../codex/mobs.md)): HT 25 · accuracy 12 · evasion 9 · damage 2-10 · armour 0-5 · EXP 5 · max level 10 · properties INORGANIC, UNDEAD · loot WEAPON (16.7% base). <small>`core/…/mobs/Skeleton.java:47`, `core/…/mobs/Skeleton.java:157`, `core/…/mobs/Skeleton.java:66`, `core/…/mobs/Skeleton.java:162`</small>

Depths corrected by hand: the boss floors 5, 10 and 15 create no rotation mobs and run no respawner, so this enemy never spawns there. <small>`core/…/mobs/MobSpawner.java:70-155`, `core/…/levels/SewerBossLevel.java:132-138`, `core/…/levels/PrisonBossLevel.java:597-604`, `core/…/levels/CavesBossLevel.java:207-219`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** No speed override: base speed 1. Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/actors/Char.java:175`, `core/…/levels/Terrain.java:90`, `core/…/mobs/Mob.java:1686-1705`</small>
- **Attack.** `melee`, reach 1. Adjacent only (default canAttack). Attack time: 1 turn (default).. Damage: 2-10 melee, accuracy 12. <small>`core/…/mobs/Mob.java:558-568`, `core/…/mobs/Mob.java:753-757`, `core/…/mobs/Skeleton.java:65-68`, `core/…/mobs/Skeleton.java:156-159`</small>
- **Bone explosion on death.** When it dies (any cause except falling into a chasm) every living character in the 8 surrounding cells, hero, allies and other mobs alike, takes 6-12 damage (scaled by Ascension). Armor counts twice: DR is rolled twice and both rolls are subtracted. Earthroot armor's flat reduction is applied twice; Rock Armor's reduction is applied once and the blast is then zeroed if Rock Armor absorbed more than half of it (the code multiplies by round(after/before)). Shield of Light and Holy Ward reduce it by double their usual amount. Can kill the hero ("You were killed by the explosion of bones..."). *Telegraph:* none; the only warning is the skeleton's own HP bar approaching 0 <small>`core/…/mobs/Skeleton.java:70-141`</small>
- **Immune.** Bleeding, ToxicGas, Poison (INORGANIC) <small>`core/…/mobs/Skeleton.java:61-62`, `core/…/actors/Char.java:1421-1422`</small>
- **Resists.** No elemental resistances; extra DR 0-5 on every physical hit. Properties UNDEAD and INORGANIC. <small>`core/…/mobs/Skeleton.java:61-62`, `core/…/mobs/Skeleton.java:161-164`</small>
- **AI.** Starts `sleeping`; flees: never. Generated asleep (respawns wandering); standard hunting AI, never switches to FLEEING on its own. <small>`core/…/mobs/Mob.java:124`, `core/…/levels/Level.java:774-775`, `core/…/mobs/Mob.java:1321-1363`</small>
- **Evasion.** defenseSkill 9; evasive: no. Low evasion; accuracy 12. <small>`core/…/mobs/Skeleton.java:53`, `core/…/mobs/Skeleton.java:157-159`</small>
- **Surprise.** Can be surprised: yes. No override of surprisedBy; sleeping/wandering skeletons take guaranteed hits. <small>`core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/hero/Hero.java:744-752`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `surprise`; ranged attacks first: yes.
- **Plan.** Open with a surprise hit if it is asleep. Soften it with ranged attacks as it walks in, since its low evasion (9) makes throws land. Melee it down, then if your HP or armor is low, time the last hit so it lands from range, or step onto a door or corner first so it has to approach. Never finish a skeleton while you stand next to it at under ~12 HP with weak armor. Use its death blast against packs. <small>`core/…/mobs/Skeleton.java:53`, `core/…/mobs/Skeleton.java:78-80`, `core/…/mobs/Skeleton.java:124-126`, `core/…/melee/Dagger.java:66-71`, `core/…/wands/WandOfMagicMissile.java:56-62`, `core/…/mobs/Mob.java:783-794`, `core/…/hero/HeroClass.java:174-176`, `core/…/hero/HeroClass.java:190-193`, `core/…/hero/HeroClass.java:204-211`, `core/…/hero/HeroClass.java:221-224`, `core/…/hero/HeroClass.java:233-238`, `core/…/hero/HeroClass.java:248-253`</small>
- **Kill or nullify: Land the killing blow from 2+ tiles away (thrown weapon, bow, wand).** The explosion only reaches the 8 neighbouring cells of the skeleton. (derived) <small>`core/…/mobs/Skeleton.java:78-80`</small>
- **Kill or nullify: If you must finish it in melee, do it with solid armor and HP above ~12.** Blast is 6-12 before DR and your DR is subtracted twice, so good armor makes it near zero; with weak armor it can take up to 12. (derived) <small>`core/…/mobs/Skeleton.java:81`, `core/…/mobs/Skeleton.java:124-126`</small>
- **Kill or nullify: Kill it while other enemies stand next to it.** The blast hits any living Char adjacent to the skeleton, not just the hero. (derived) <small>`core/…/mobs/Skeleton.java:78-80`</small>
- **Kill or nullify: Knock it into a chasm to deny the explosion.** Death by Chasm returns before the blast. (derived) <small>`core/…/mobs/Skeleton.java:75`</small>
- **Kill or nullify: Do not use poison or toxic gas; prefer wands for chip damage.** Immune to Poison/ToxicGas (INORGANIC); wand bolts skip its 0-5 DR. (derived) <small>`core/…/actors/Char.java:1421-1422`, `core/…/mobs/Skeleton.java:161-164`, `core/…/wands/WandOfMagicMissile.java:56-62`</small>
- **Escape.** Outrunnable: no; contact breaks at: door, stairs, out-of-sight. Same speed as the hero; break line of sight with a door or corner, or take the stairs. <small>`core/…/actors/Char.java:175`, `core/…/mobs/Mob.java:1387-1407`, `core/…/levels/Terrain.java:90`, `core/…/mobs/Mob.java:1686-1705`</small>

| Class | Note |
|---|---|
| Warrior | Armor DR counts twice against the blast, so a Warrior in upgraded armor can simply melee it. Throwing stones can deliver the last hit from range. |
| Mage | Magic missile bolts skip its extra 0-5 DR and can finish it at range. |
| Rogue | Surprise-stab it asleep with the dagger (75%-to-max damage). Finish it with throwing knives from 2+ tiles. |
| Huntress | The Spirit Bow can deal the killing hit from 2+ tiles, so the blast never reaches you. |
| Duelist | Melee it and finish with throwing spikes from range if HP or armor is low. |
| Cleric | Guiding Light gives a guaranteed hit; keep armor up for the double-DR blast. |

### Community notes

- **Tier F.** “<del>Skeletons explode on death for damage equal to their normal melee hit, but armor absorption against that explosion is halved compared to normal hits; wear your heaviest available armor in Prison and, if possible, finish Skeletons off with a ranged attack from a distance once they're at very low HP so the explosion can't reach you.</del>” [source](https://pixeldungeon.fandom.com/wiki/Skeleton) (version: pre-v4 (source flagged as old version)). Old-version claim. In v4.0.0 the blast is its own roll of 6-12 (melee is 2-10), and armor DR is applied twice, not halved (two drRoll() calls subtracted). The advice to finish it from 2+ tiles is still correct: only the 8 adjacent cells are hit. <small>`core/…/mobs/Skeleton.java:78-81`, `core/…/mobs/Skeleton.java:124-125`, `core/…/mobs/Skeleton.java:65-68`</small>
- **Tier 1.** “Aim to have armor that blocks roughly 4+ damage on average before descending into Prison, specifically to survive the death-explosion reliably if you can't kill a Skeleton before it reaches melee range.” [source](https://www.youtube.com/watch?v=TE1zJ1Eh7fg) (version: not stated). The arithmetic holds in v4.0.0: the blast averages 9 (NormalIntRange 6-12) and DR is subtracted twice, so an average DR roll of 4 absorbs about 8 of it. The exact threshold is advice; the double-DR mechanism is code. <small>`core/…/mobs/Skeleton.java:81`, `core/…/mobs/Skeleton.java:124-125`</small>

??? note "Corrected during verification (3)"

    - Rock Armor is not simply 'applied twice': Skeleton.java:87-92 multiplies the absorbed damage by round(after/before), which zeroes the blast when Rock Armor absorbs more than half, else leaves the single reduction.
    - Added the UNDEAD property and the melee damage (2-10, Skeleton.java:65-68).
    - Hand check: depths '6-10' corrected to '6-9'; the sewer, prison and caves boss floors create no rotation mobs and add no respawner (SewerBossLevel.java:132-138, PrisonBossLevel.java:597-604, CavesBossLevel.java:207-219).

## Crazy thief {#thief}

`actors.mobs.Thief` · depths 6-9; rare 2.5% on 4 · **Tactic:** Treat it as an item threat rather than an HP threat. Hit it asleep or at range before it reaches you (HP 20).

**Stats** (from the [Codex](../codex/mobs.md)): HT 20 · accuracy 12 · evasion 12 · damage 1-10 · armour 0-3 · EXP 5 · max level 11 · properties UNDEAD · loot RING,ARTIFACT (3% base). <small>`core/…/mobs/Thief.java:39`, `core/…/mobs/Thief.java:116`, `core/…/mobs/Thief.java:82`, `core/…/mobs/Thief.java:121`</small>

Depths corrected by hand: the boss floors 5, 10 and 15 create no rotation mobs and run no respawner, so this enemy never spawns there. <small>`core/…/mobs/MobSpawner.java:70-155`, `core/…/levels/SewerBossLevel.java:132-138`, `core/…/levels/PrisonBossLevel.java:597-604`, `core/…/levels/CavesBossLevel.java:207-219`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** Speed 1; 5/6 while carrying a stolen item. Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/mobs/Thief.java:75-79`, `core/…/levels/Terrain.java:90`, `core/…/mobs/Mob.java:1686-1705`</small>
- **Attack.** `melee`, reach 1. Adjacent only. Attack time: 0.5 turn: two attacks per turn. Damage: 1-10 melee, accuracy 12. <small>`core/…/mobs/Thief.java:81-89`, `core/…/mobs/Thief.java:115-118`, `core/…/mobs/Mob.java:558-568`</small>
- **Steal.** On a successful melee hit against the hero, while it carries nothing, it picks ONE random top-level backpack slot (bags count as slots; items inside bags are never candidates). If that item is non-unique and its level is below +1 it takes one unit of it (one from a stack) and switches to FLEEING; otherwise that hit steals nothing and it does not pick again. It carries at most one stolen item. A stolen Honeypot shatters at once and the released bee hunts the thief, since the thief holds the shattered pot. *Telegraph:* none before; log "The thief stole X from you!" afterwards, and its description then names the carried item <small>`core/…/mobs/Thief.java:125-135`, `core/…/mobs/Thief.java:146-169`, `core/…/mobs/Thief.java:171-180`, `core/…/hero/Belongings.java:399-403`, `core/…/bags/Bag.java:47`, `core/…/items/Item.java:319-343`, `core/…/items/Honeypot.java:97-136`, `core/…/mobs/Bee.java:96-102`, `core/…/mobs/Bee.java:145-152`</small>
- **Escape with loot.** Each fleeing turn in which the thief cannot see the hero, it rolls 1+Random.Int(distance to the hero's last known position) &gt;= 6. On success, if it carries an item, the hero cannot see it and it is 6+ tiles from the hero, it teleports to a random respawn cell out of the hero's view, the item is lost for good ("The thief gets away with your X!") and it goes back to wandering. If those checks fail it just switches to WANDERING, still holding the item, and flees again when it next notices the hero. *Telegraph:* none; wool puff if the new cell is visible <small>`core/…/mobs/Thief.java:197-228`, `core/…/mobs/Mob.java:1441-1451`, `core/…/mobs/Thief.java:182-195`</small>
- **Runs when carrying loot.** A wandering thief that already holds an item flees instead of fighting when it notices the hero. *Telegraph:* none <small>`core/…/mobs/Thief.java:182-195`</small>
- **Drops gold when hit while fleeing.** Each physical hit (anything that goes through Char.attack and calls defenseProc) while it is FLEEING drops 1 gold on its cell. Wand bolts that call damage() directly, such as Magic Missile, do not trigger it. *Telegraph:* none <small>`core/…/mobs/Thief.java:137-144`, `core/…/items/Gold.java:46-52`, `core/…/actors/Char.java:483`, `core/…/wands/WandOfMagicMissile.java:56-62`</small>
- **Burning destroys a stolen scroll.** While a thief burns, a carried non-unique scroll is destroyed and carried mystery meat becomes chargrilled meat. *Telegraph:* ember burst on the thief <small>`core/…/buffs/Burning.java:152-164`</small>
- **Inflicts none (item theft).** Removes an item from the backpack <small>`core/…/mobs/Thief.java:146-169`</small>
- **Resists.** None; extra DR 0-3. Property UNDEAD (no built-in immunities). <small>`core/…/mobs/Thief.java:58`, `core/…/mobs/Thief.java:120-123`</small>
- **AI.** Starts `sleeping`; flees: after a successful steal, or on noticing the hero while it carries loot. Hunts normally until it steals. Custom Wandering and Fleeing states. <small>`core/…/mobs/Thief.java:55-56`, `core/…/mobs/Thief.java:125-135`, `core/…/mobs/Thief.java:182-228`, `core/…/mobs/Mob.java:124`</small>
- **Evasion.** defenseSkill 12; evasive: no. Moderate; accuracy 12, HP 20. <small>`core/…/mobs/Thief.java:47`, `core/…/mobs/Thief.java:115-118`</small>
- **Surprise.** Can be surprised: yes. Standard rules. <small>`core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/hero/Hero.java:744-752`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `surprise`; ranged attacks first: yes.
- **Plan.** Treat it as an item threat rather than an HP threat. Hit it asleep or at range before it reaches you (HP 20). If it steals, do not let it leave your view: chase it (it is slower while loaded) and throw or zap at it until it drops the item. Drive it into dead ends. <small>`core/…/mobs/Thief.java:46`, `core/…/mobs/Thief.java:75-79`, `core/…/mobs/Thief.java:197-228`, `core/…/wands/WandOfMagicMissile.java:56-62`, `core/…/melee/Dagger.java:66-71`, `core/…/mobs/Mob.java:783-794`, `core/…/hero/HeroClass.java:174-176`, `core/…/hero/HeroClass.java:190-193`, `core/…/hero/HeroClass.java:204-211`, `core/…/hero/HeroClass.java:221-224`, `core/…/hero/HeroClass.java:233-238`, `core/…/hero/HeroClass.java:248-253`, `core/…/buffs/Burning.java:152-158`</small>
- **Kill or nullify: Kill it before it lands a hit: ranged openers and surprise attacks.** Every successful melee hit can steal, and it attacks twice per turn (attack delay x0.5). (derived) <small>`core/…/mobs/Thief.java:86-89`, `core/…/mobs/Thief.java:125-135`</small>
- **Kill or nullify: Keep it in view after a theft and close the gap.** It escapes with the item only when it is out of the hero's FOV and at least 6 tiles away; while carrying loot it moves at 5/6 speed, so a hero at speed 1 gains ground. (derived) <small>`core/…/mobs/Thief.java:75-79`, `core/…/mobs/Thief.java:197-228`, `core/…/mobs/Mob.java:1445`</small>
- **Kill or nullify: Shoot or throw at it while it flees; kill it to get the item back.** Killing it drops the stolen item. Each physical hit while it flees also drops 1 gold; wand bolts do damage but drop no gold. (derived) <small>`core/…/mobs/Thief.java:98-107`, `core/…/mobs/Thief.java:137-144`, `core/…/items/Gold.java:46-52`</small>
- **Kill or nullify: Corner it in a dead end.** A fleeing mob with nowhere to run turns and fights instead of escaping. (derived) <small>`core/…/mobs/Mob.java:1477-1486`</small>
- **Kill or nullify: Keep valuables safe: equip them, upgrade them to +1, or keep them inside a bag.** steal() only draws from top-level backpack slots (never equipped items or bag contents) and refuses unique items and anything at +1 or higher; bags themselves are unique, so a draw that lands on a bag steals nothing. Enchantment does not protect a +0 item. (derived) <small>`core/…/mobs/Thief.java:146-150`, `core/…/hero/Belongings.java:399-403`, `core/…/bags/Bag.java:47`</small>
- **Kill or nullify: Do not burn a thief that stole a scroll you want back.** Burning destroys a carried non-unique scroll. (derived) <small>`core/…/buffs/Burning.java:152-158`</small>
- **Escape.** Outrunnable: no; contact breaks at: door, stairs, out-of-sight. You rarely need to escape it; the danger is losing an item. Without loot it moves at speed 1. <small>`core/…/mobs/Thief.java:75-79`, `core/…/mobs/Mob.java:1686-1705`</small>

| Class | Note |
|---|---|
| Warrior | Throwing stones soften it on approach and hit it while it flees. |
| Mage | Magic missile hits a fleeing thief without a hit roll and can kill it to recover the item, but bolts do not make it drop gold. Do not use the starting Liquid Flame if it holds a scroll. |
| Rogue | A dagger surprise hit on a sleeping thief deals near-max damage. Throwing knives on a fleeing one. |
| Huntress | The Spirit Bow kills it at range before contact and punishes a fleeing thief; keep line of sight. |
| Duelist | Throwing spikes on approach and while it flees. |
| Cleric | Guiding Light makes the opening hit certain; chase with the cudgel. |

### Community notes

- **Tier F.** “<del>Crazy Thieves attack twice per turn and try to steal one unequipped item from your backpack per hit; they can't steal unique, quest, or upgraded/enchanted items, so keeping valuables equipped or upgraded protects them from being stolen.</del>” [source](https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies) (version: not stated). Partly confirmed: attack delay is halved (two attacks per turn), equipped, unique and +1-or-higher items are safe. Contradicted: enchantment gives no protection (the check is level() &lt; 1, so a +0 enchanted item can be stolen), and a thief steals only once; after a successful theft it flees and no longer steals. A hit whose random draw is protected steals nothing. <small>`core/…/mobs/Thief.java:86-89`, `core/…/mobs/Thief.java:129-131`, `core/…/mobs/Thief.java:146-150`</small>

??? note "Corrected during verification (6)"

    - Steal: a random top-level backpack slot is drawn once per successful hit; if it is unique or +1 or higher nothing is stolen that hit (no re-pick). Bag contents are never candidates. Only one unit of a stack is taken.
    - Escape: added the Fleeing roll (thief must not see the hero, 1+Random.Int(dist)&gt;=6) and the fall-back to WANDERING with the item kept.
    - Gold drop is 1 gold per physical hit (Gold() = 1); wand bolts that call damage() do not trigger defenseProc. The MAGE note claiming bolts drop gold was wrong.
    - Stolen Honeypot: the bee hunts the thief (the pot holder), not the hero.
    - Added Burning destroying a stolen scroll, UNDEAD property, 1-10 damage; resolved the gold-quantity open question.
    - Hand check: depths '6-10; rare 2.5% on 4' corrected to '6-9; rare 2.5% on 4'; the sewer, prison and caves boss floors create no rotation mobs and add no respawner (SewerBossLevel.java:132-138, PrisonBossLevel.java:597-604, CavesBossLevel.java:207-219).

## Crazy bandit {#bandit}

`actors.mobs.Bandit` · depths 6-9 (2% alternate of Thief) · **Tactic:** Play it like a thief but stricter: open at range or with a surprise hit and burst it down (HP 20) before it touches you.

**Stats** (from the [Codex](../codex/mobs.md)): HT 20 · accuracy 12 · evasion 12 · damage 1-10 · armour 0-3 · EXP 5 · max level 11 · properties UNDEAD · loot RING,ARTIFACT (100% base). <small>`core/…/mobs/Bandit.java:34`, `core/…/mobs/Thief.java:116`, `core/…/mobs/Thief.java:82`, `core/…/mobs/Thief.java:121`</small>

Depths corrected by hand: the boss floors 5, 10 and 15 create no rotation mobs and run no respawner, so this enemy never spawns there. <small>`core/…/mobs/MobSpawner.java:70-155`, `core/…/levels/SewerBossLevel.java:132-138`, `core/…/levels/PrisonBossLevel.java:597-604`, `core/…/levels/CavesBossLevel.java:207-219`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** Inherits Thief: speed 1, 5/6 while carrying loot. Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/mobs/Bandit.java:34`, `core/…/mobs/Thief.java:75-79`, `core/…/mobs/MobSpawner.java:264`</small>
- **Attack.** `melee`, reach 1. Adjacent only. Attack time: 0.5 turn (inherited). <small>`core/…/mobs/Thief.java:86-89`</small>
- **Steal + disable.** Steals exactly like a thief (same draw rules). Only on a successful steal it also applies Blindness for 5 turns, Poison 5-6 and Cripple for 5 turns, then flees. Blindness reduces the hero's field of view to the adjacent cells, and Cripple halves hero speed (the loaded bandit moves at 5/6), so it is hard to keep in view or chase. *Telegraph:* none before; steal log line afterwards <small>`core/…/mobs/Bandit.java:45-58`, `core/…/buffs/Blindness.java:29`, `core/…/buffs/Cripple.java:28`, `core/…/mobs/Thief.java:197-228`, `core/…/levels/Level.java:1318-1319`, `core/…/levels/Level.java:1370-1386`</small>
- **Escape with loot.** Same as Thief, including the fall-back to WANDERING when the escape checks fail. *Telegraph:* none <small>`core/…/mobs/Thief.java:197-228`, `core/…/mobs/Mob.java:1441-1451`</small>
- **Inflicts Blindness.** 5 turns (DURATION/2) on successful steal <small>`core/…/mobs/Bandit.java:49`</small>
- **Inflicts Poison.** 5-6 on successful steal <small>`core/…/mobs/Bandit.java:50`</small>
- **Inflicts Cripple.** 5 turns (DURATION/2) on successful steal; halves hero speed <small>`core/…/mobs/Bandit.java:51`, `core/…/actors/Char.java:772`</small>
- **Resists.** None; extra DR 0-3 (inherited). Property UNDEAD (inherited). <small>`core/…/mobs/Thief.java:58`, `core/…/mobs/Thief.java:120-123`</small>
- **AI.** Starts `sleeping`; flees: after stealing. Thief AI. lootChance starts at 1, but Thief.lootChance() multiplies it by (1/3)^n, where n counts ring or artifact drops already made by any Thief or Bandit this run, so the drop is guaranteed only if no thief loot has dropped yet. <small>`core/…/mobs/Bandit.java:41-42`, `core/…/mobs/Thief.java:91-96`, `core/…/mobs/Thief.java:110-113`, `core/…/mobs/Thief.java:182-228`</small>
- **Evasion.** defenseSkill 12; evasive: no. Inherited from Thief. <small>`core/…/mobs/Thief.java:47`</small>
- **Surprise.** Can be surprised: yes. Standard rules. <small>`core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/hero/Hero.java:744-752`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `keep-away`; ranged attacks first: yes.
- **Plan.** Play it like a thief but stricter: open at range or with a surprise hit and burst it down (HP 20) before it touches you. If it closes, melee hard, since its 0.5 attack delay means waiting only gives it more steal attempts. <small>`core/…/mobs/Bandit.java:45-58`, `core/…/mobs/Thief.java:86-89`, `core/…/wands/WandOfMagicMissile.java:56-62`, `core/…/actors/Char.java:627-630`, `core/…/mobs/Mob.java:783-794`, `core/…/hero/HeroClass.java:174-176`, `core/…/hero/HeroClass.java:190-193`, `core/…/hero/HeroClass.java:204-211`, `core/…/hero/HeroClass.java:221-224`, `core/…/hero/HeroClass.java:233-238`, `core/…/hero/HeroClass.java:248-253`</small>
- **Kill or nullify: Never let it reach melee with stealable items in the pack: kill at range or from surprise.** One hit that steals also blinds and cripples you, which removes the chase counter that works on a thief. (derived) <small>`core/…/mobs/Bandit.java:45-58`, `core/…/mobs/Thief.java:86-89`</small>
- **Kill or nullify: If it steals, cure or outlast the blindness before chasing, and track its last seen direction.** Escape needs it out of the hero's FOV and 6+ tiles away; Blindness cuts the hero's FOV to adjacent cells and Cripple halves hero speed for 5 turns. (derived) <small>`core/…/mobs/Bandit.java:49-51`, `core/…/mobs/Thief.java:199-202`, `core/…/actors/Char.java:772`, `core/…/levels/Level.java:1318-1319`, `core/…/levels/Level.java:1370-1386`</small>
- **Kill or nullify: Worth killing, especially early.** Its ring-or-artifact drop chance is 1 x (1/3)^n, n = thief-family drops so far this run: guaranteed if no thief has dropped one yet, otherwise 1/3, 1/9, ... (derived) <small>`core/…/mobs/Bandit.java:41-42`, `core/…/mobs/Thief.java:52`, `core/…/mobs/Thief.java:91-96`</small>
- **Escape.** Outrunnable: no; contact breaks at: door, stairs, out-of-sight. Speed 1 without loot. <small>`core/…/mobs/Thief.java:75-79`</small>

| Class | Note |
|---|---|
| Warrior | Throwing stones on approach, then melee; a steal costs you the chase. |
| Mage | Magic missile at range; bolts cannot miss. |
| Rogue | Surprise-stab it asleep, or use the Cloak of Shadows for guaranteed-hit openers. |
| Huntress | Shoot on sight; the bow kills it before contact. |
| Duelist | Throwing spikes on approach. |
| Cleric | Guiding Light on the opening hit. |

### Community notes

- **Tier F.** “<del>Crazy Bandits can steal and shatter a Honeypot from your inventory, which releases a hostile Golden Bee, so don't assume a Bandit encounter is contained to just the Bandit if you're carrying honeypots.</del>” [source](https://steamcommunity.com/sharedfiles/filedetails/?id=2786737188) (version: not stated). The theft and shatter are real, but the released bee targets the pot holder, which is the thief/bandit that stole it. It only turns to the hero after the pot is dropped (the bandit dies), and then only if no other mob is within 3 tiles of the pot and the hero is within 3. <small>`core/…/mobs/Thief.java:159-163`, `core/…/items/Honeypot.java:97-136`, `core/…/mobs/Bee.java:96-102`, `core/…/mobs/Bee.java:145-205`, `core/…/mobs/Thief.java:99-105`</small>

??? note "Corrected during verification (4)"

    - Loot is not unconditionally guaranteed: THEIF_MISC count is shared with Thief (Thief.java:91-96).
    - Resolved the Blindness FOV question: a blind hero sees only adjacent cells (Level.java:1318-1319, 1382-1398).
    - Noted that Bandit.java:36 declares a shadowing 'item' field that is never used; behaviour is Thief's.
    - Hand check: depths '6-10 (2% alternate of Thief)' corrected to '6-9 (2% alternate of Thief)'; the sewer, prison and caves boss floors create no rotation mobs and add no respawner (SewerBossLevel.java:132-138, PrisonBossLevel.java:597-604, CavesBossLevel.java:207-219).

## DM-100 {#dm100}

`actors.mobs.DM100` · depths 7-9 · **Tactic:** Like a gnoll shaman: get next to it. Approach from outside its line of fire (around corners and through doors) or wait behind a corner until it walks up to you, then melee it; its melee is weaker and your armor applies.

**Stats** (from the [Codex](../codex/mobs.md)): HT 20 · accuracy 11 · evasion 8 · damage 2-8 · armour 0-4 · EXP 6 · max level 13 · properties ELECTRIC, INORGANIC · loot SCROLL (25% base). <small>`core/…/mobs/DM100.java:40`, `core/…/mobs/DM100.java:66`, `core/…/mobs/DM100.java:61`, `core/…/mobs/DM100.java:71`</small>

Depths corrected by hand: the boss floors 5, 10 and 15 create no rotation mobs and run no respawner, so this enemy never spawns there. <small>`core/…/mobs/MobSpawner.java:70-155`, `core/…/levels/SewerBossLevel.java:132-138`, `core/…/levels/PrisonBossLevel.java:597-604`, `core/…/levels/CavesBossLevel.java:207-219`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** No speed override. Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/actors/Char.java:175`, `core/…/levels/Terrain.java:90`</small>
- **Attack.** `bolt`, reach 99. Melee when adjacent. Otherwise it zaps whenever a MAGIC_BOLT line from it reaches the hero and the hero is in its field of view (99 = no fixed range cap): blocked by solid terrain, closed doors and any character in between. With the line blocked it does not fire at the blocker; it walks closer. Attack time: melee 1 turn; zap 1 turn (TIME_TO_ZAP). Damage: melee 2-8 (armor applies), accuracy 11; bolt 3-10 with accuracy doubled, no DR. <small>`core/…/mobs/DM100.java:42`, `core/…/mobs/DM100.java:75-79`, `core/…/mobs/DM100.java:85-128`, `core/…/mechanics/Ballistica.java:49`, `core/…/mobs/Mob.java:1326-1333`</small>
- **Lightning bolt.** A ranged zap rolled as a magic hit, so its accuracy counts double (11 becomes effectively 22). It deals 3-10 (Ascension scaled) through damage() directly, so hero armor DR does not reduce it; the AntiMagic glyph resists it. Can kill ("The lightning bolt killed you..."). *Telegraph:* none; the zap animation and lightning line play as it fires <small>`core/…/mobs/DM100.java:94-116`, `core/…/actors/Char.java:615-617`, `core/…/sprites/DM100Sprite.java:67-95`, `core/…/glyphs/AntiMagic.java:129`</small>
- **Immune.** Bleeding, ToxicGas, Poison (INORGANIC) <small>`core/…/mobs/DM100.java:56-57`, `core/…/actors/Char.java:1421-1422`</small>
- **Resists.** Half effect from Wand of Lightning, Shocking enchant, Potential, Electricity, Shocking Dart, Shock Elemental (ELECTRIC). ELECTRIC grants no immunities. <small>`core/…/mobs/DM100.java:56`, `core/…/actors/Char.java:1429-1431`, `core/…/actors/Char.java:1356-1371`</small>
- **Resists.** Extra DR 0-4 on physical hits <small>`core/…/mobs/DM100.java:70-73`</small>
- **AI.** Starts `sleeping`; flees: never. Standard hunting; attacks from range as soon as canAttack is true, so it stops approaching once it has a bolt line. <small>`core/…/mobs/Mob.java:1326-1333`, `core/…/mobs/DM100.java:75-79`</small>
- **Evasion.** defenseSkill 8; evasive: no. Evasion 8, HP 20: fragile and easy to hit (only the depth-6 Swarm, evasion 5, is lower among prison-depth spawns). <small>`core/…/mobs/DM100.java:47-48`, `core/…/mobs/Swarm.java:46`</small>
- **Surprise.** Can be surprised: yes. Standard rules. <small>`core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/hero/Hero.java:744-752`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `chokepoint`; ranged attacks first: no.
- **Plan.** Like a gnoll shaman: get next to it. Approach from outside its line of fire (around corners and through doors) or wait behind a corner until it walks up to you, then melee it; its melee is weaker and your armor applies. If caught in the open, charge rather than retreat, or trade with high-accuracy ranged only when you can finish it quickly (HP 20, evasion 8). <small>`core/…/mobs/DM100.java:87-100`, `core/…/mobs/DM100.java:47-48`, `core/…/actors/Char.java:1429-1431`, `core/…/melee/Dagger.java:66-71`, `core/…/mobs/Mob.java:783-794`, `core/…/hero/HeroClass.java:174-176`, `core/…/hero/HeroClass.java:190-193`, `core/…/hero/HeroClass.java:204-211`, `core/…/hero/HeroClass.java:221-224`, `core/…/hero/HeroClass.java:233-238`, `core/…/hero/HeroClass.java:248-253`</small>
- **Kill or nullify: Close to melee range using cover; do not trade at range.** When adjacent it uses its normal melee (2-8, reduced by your armor); at range its bolt has doubled accuracy and ignores armor. (derived) <small>`core/…/mobs/DM100.java:87-90`, `core/…/mobs/DM100.java:97-100`, `core/…/actors/Char.java:615-617`</small>
- **Kill or nullify: Break line of fire with corners and doors and make it come to you.** It can only zap along an unobstructed MAGIC_BOLT line; without one it has to walk up, so fight it at a corner where the first line it gets is adjacent. (derived) <small>`core/…/mobs/DM100.java:75-79`, `core/…/mechanics/Ballistica.java:115-151`, `core/…/levels/Terrain.java:90`</small>
- **Kill or nullify: Put another character in the line.** MAGIC_BOLT stops at the first character; if that is not the hero, canAttack is false and it does not zap at all (it walks instead). (derived) <small>`core/…/mechanics/Ballistica.java:43`, `core/…/mechanics/Ballistica.java:49`, `core/…/mechanics/Ballistica.java:137-139`, `core/…/mobs/DM100.java:75-79`</small>
- **Kill or nullify: If you must shoot it, commit: it is easy to hit and fragile.** defenseSkill 8, HP 20, DR 0-4. (derived) <small>`core/…/mobs/DM100.java:47-48`, `core/…/mobs/DM100.java:70-73`</small>
- **Kill or nullify: Avoid poison, gas and lightning against it.** INORGANIC immunities and ELECTRIC resistance. (derived) <small>`core/…/actors/Char.java:1421-1422`, `core/…/actors/Char.java:1429-1431`</small>
- **Kill or nullify: Wand bolts skip its DR.** Magic Missile calls damage() directly, bypassing its 0-4 DR and the hit roll. (derived) <small>`core/…/mobs/DM100.java:70-73`, `core/…/wands/WandOfMagicMissile.java:56-62`</small>
- **Escape.** Outrunnable: no; contact breaks at: door, out-of-sight, stairs. Running in a straight line keeps you in its bolt line; turn a corner or shut a door behind you. <small>`core/…/mobs/DM100.java:75-79`, `core/…/levels/Terrain.java:90`, `core/…/mobs/Mob.java:1686-1705`</small>

| Class | Note |
|---|---|
| Warrior | Close in; armor counts against its melee but not the bolt. |
| Mage | Magic missile works, but it is not immune; avoid a Wand of Lightning (halved). |
| Rogue | Ambush it at a corner. It is fragile enough for surprise dagger hits to matter. |
| Huntress | The bow is the exception: with evasion 8 and 20 HP it dies to a few arrows, so trading shots is acceptable. Still prefer corner ambushes. |
| Duelist | Close in at corners; melee it. |
| Cleric | Guiding Light, then melee. |

### Community notes

No community claim about this enemy was found.

??? note "Corrected during verification (5)"

    - Added the missing extra DR 0-4 (DM100.java:70-73).
    - 'Lowest evasion in the prison' was wrong: the depth-6 Swarm has 5 (Swarm.java:46).
    - A character in the bolt line is not hit; the DM-100 just cannot zap (canAttack false).
    - Reach set to 99 (FOV-limited) for consistency with the bolt; noted AntiMagic resists the bolt; resolved the ELECTRIC open question (no immunities).
    - Hand check: depths '7-10' corrected to '7-9'; the sewer, prison and caves boss floors create no rotation mobs and add no respawner (SewerBossLevel.java:132-138, PrisonBossLevel.java:597-604, CavesBossLevel.java:207-219).

## Prison guard {#guard}

`actors.mobs.Guard` · depths 7-9 · **Tactic:** Either meet it in melee deliberately (walk into adjacency so the chain never fires) or snipe it from 5+ tiles and retreat around a corner as it closes.

**Stats** (from the [Codex](../codex/mobs.md)): HT 40 · accuracy 12 · evasion 10 · damage 4-12 · armour 0-7 · EXP 7 · max level 14 · properties UNDEAD · loot ARMOR (20% base). <small>`core/…/mobs/Guard.java:43`, `core/…/mobs/Guard.java:138`, `core/…/mobs/Guard.java:66`, `core/…/mobs/Guard.java:143`</small>

Depths corrected by hand: the boss floors 5, 10 and 15 create no rotation mobs and run no respawner, so this enemy never spawns there. <small>`core/…/mobs/MobSpawner.java:70-155`, `core/…/levels/SewerBossLevel.java:132-138`, `core/…/levels/PrisonBossLevel.java:597-604`, `core/…/levels/CavesBossLevel.java:207-219`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** No speed override. Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/actors/Char.java:175`, `core/…/levels/Terrain.java:90`</small>
- **Attack.** `melee`, reach 1. Adjacent melee; the chain pull uses a PROJECTILE line (see abilities). Attack time: 1 turn. Damage: 4-12 melee, accuracy 12. <small>`core/…/mobs/Guard.java:65-68`, `core/…/mobs/Guard.java:137-140`, `core/…/mobs/Mob.java:558-568`</small>
- **Chain pull (once per guard).** When it is hunting, sees the hero, cannot attack (not adjacent) and is 2-4 tiles away (distance &lt; 5) with a PROJECTILE line that reaches the hero, and the first cell of that line (next to the guard) is not a pit, it drags the hero to the free non-solid cell on the line closest to the guard, applies Cripple for 4 turns and interrupts the hero. The pull spends no time (neither chain() nor its Hunting.act calls spend), so the guard acts again immediately, normally with a melee attack on the pulled, crippled hero. Once per guard; IMMOVABLE targets cannot be pulled; a failed attempt (blocked line or no free cell) does not use it up. *Telegraph:* none before; the yell "Get over here!", a chain visual and a chain sound play as it happens <small>`core/…/mobs/Guard.java:45-46`, `core/…/mobs/Guard.java:70-135`, `core/…/mobs/Guard.java:174-191`, `core/…/mobs/Mob.java:262-311`, `core/…/actors/Actor.java:266-268`</small>
- **Inflicts Cripple.** 4 turns after a chain pull (halves speed) <small>`core/…/mobs/Guard.java:127`, `core/…/actors/Char.java:772`</small>
- **Resists.** No elemental resistances; high extra DR 0-7 on physical hits. Property UNDEAD. <small>`core/…/mobs/Guard.java:60`, `core/…/mobs/Guard.java:142-145`</small>
- **AI.** Starts `sleeping`; flees: never. Custom Hunting tries the chain before normal hunting. <small>`core/…/mobs/Guard.java:62`, `core/…/mobs/Guard.java:174-191`</small>
- **Evasion.** defenseSkill 10; evasive: no. HP 40 with DR 0-7 makes it the tankiest regular prison mob. <small>`core/…/mobs/Guard.java:51-52`, `core/…/mobs/Guard.java:142-145`</small>
- **Surprise.** Can be surprised: yes. Standard rules. <small>`core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/hero/Hero.java:744-752`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `close`; ranged attacks first: no.
- **Plan.** Either meet it in melee deliberately (walk into adjacency so the chain never fires) or snipe it from 5+ tiles and retreat around a corner as it closes. Expect a long fight (40 HP, DR 0-7): open with a surprise hit if it sleeps, and use wand charges here rather than on low-DR enemies. <small>`core/…/mobs/Guard.java:179-184`, `core/…/mobs/Guard.java:142-145`, `core/…/wands/WandOfMagicMissile.java:56-62`, `core/…/melee/Dagger.java:66-71`, `core/…/mobs/Mob.java:783-794`, `core/…/actors/Char.java:423-431`, `core/…/hero/HeroClass.java:174-176`, `core/…/hero/HeroClass.java:190-193`, `core/…/hero/HeroClass.java:204-211`, `core/…/hero/HeroClass.java:221-224`, `core/…/hero/HeroClass.java:233-238`, `core/…/hero/HeroClass.java:248-253`</small>
- **Kill or nullify: Step next to it yourself, or stay 5+ tiles away.** It chains only while it cannot attack you and the distance is under 5; entering melee on your own terms avoids the free Cripple and the free follow-up attack. (derived) <small>`core/…/mobs/Guard.java:179-184`</small>
- **Kill or nullify: Keep a character, wall or closed door in the line.** The chain needs the PROJECTILE collision to land on the hero. (derived) <small>`core/…/mobs/Guard.java:74-79`, `core/…/mechanics/Ballistica.java:47`</small>
- **Kill or nullify: Stand so that the cell next to the guard on the line is a pit.** The chain fails if path[1] is a pit. (derived) <small>`core/…/mobs/Guard.java:76-79`</small>
- **Kill or nullify: Use wands or other non-physical damage.** Its 0-7 DR eats weapon damage; wand bolts skip DR. (derived) <small>`core/…/mobs/Guard.java:142-145`, `core/…/wands/WandOfMagicMissile.java:56-62`</small>
- **Kill or nullify: Remember whether you have seen this guard pull.** chainsUsed is per guard and permanent; after the pull it is a plain melee mob. (derived) <small>`core/…/mobs/Guard.java:45-46`, `core/…/mobs/Guard.java:119`</small>
- **Escape.** Outrunnable: no; contact breaks at: door, out-of-sight, stairs. Retreating in a straight line within 4 tiles invites a pull, a 4-turn Cripple and an immediate attack; break the line first. <small>`core/…/mobs/Guard.java:179-184`, `core/…/mobs/Guard.java:127`, `core/…/levels/Terrain.java:90`</small>

| Class | Note |
|---|---|
| Warrior | Walk into melee; your armor handles its 4-12. |
| Mage | Magic missile ignores its high DR, so this is the best wand target on the floor. |
| Rogue | Surprise dagger hits bypass its evasion and deal near-max damage. |
| Huntress | Shoot from 5+ tiles; once it is inside 5 expect the pull unless the line is blocked, so fight it adjacent instead. |
| Duelist | Melee it; do not try to kite inside 5 tiles. |
| Cleric | Guiding Light adds bonus damage and a sure hit against its high HP. |

**Open questions.**

- The no-spend follow-up after a pull is read from code (no spend() on the chain path, Actor picks the lowest time); not yet confirmed by a harness run.

### Community notes

No community claim about this enemy was found.

??? note "Corrected during verification (4)"

    - Chain pull spends no time: the guard acts again at once (Guard.java:70-121, 174-191; Mob.java:262-311).
    - Clarified the range as 2-4 tiles, the pull destination as the free cell on the line closest to the guard, IMMOVABLE exemption, and failed attempts not consuming the chain.
    - Added UNDEAD property and 4-12 damage.
    - Hand check: depths '7-10' corrected to '7-9'; the sewer, prison and caves boss floors create no rotation mobs and add no respawner (SewerBossLevel.java:132-138, PrisonBossLevel.java:597-604, CavesBossLevel.java:207-219).

## Necromancer {#necromancer}

`actors.mobs.Necromancer` · depths 8-9 · **Tactic:** Shoot it from 5+ tiles as it walks in. Once it summons, ignore or body-block the skeleton and push through to the necromancer (it has no attack).

**Stats** (from the [Codex](../codex/mobs.md)): HT 40 · accuracy 0 · evasion 14 · damage 1 · armour 0-5 · EXP 7 · max level 14 · properties UNDEAD · loot PotionOfHealing (20% base). <small>`core/…/mobs/Necromancer.java:50`, `core/…/actors/Char.java:689`, `core/…/actors/Char.java:709`, `core/…/mobs/Necromancer.java:97`</small>

Depths corrected by hand: the boss floors 5, 10 and 15 create no rotation mobs and run no respawner, so this enemy never spawns there. <small>`core/…/mobs/MobSpawner.java:70-155`, `core/…/levels/SewerBossLevel.java:132-138`, `core/…/levels/PrisonBossLevel.java:597-604`, `core/…/levels/CavesBossLevel.java:207-219`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** No speed override. Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/actors/Char.java:175`, `core/…/levels/Terrain.java:90`</small>
- **Attack.** `none`, reach 0. Never attacks directly (canAttack always false). It only needs to see the hero to summon. Attack time: n/a. <small>`core/…/mobs/Necromancer.java:129-132`</small>
- **Summon skeleton.** While hunting, if it sees the hero within 4 tiles and has no living skeleton, it picks a free, passable cell next to the hero that is in its field of view and reachable around (not through) walls, choosing the one closest to itself, and starts summoning (1 turn for its first summon, 2 turns later). On its next action the skeleton appears there with 20/25 HP, and the necromancer acts again at once (the summon itself spends no time). If a character occupies the cell it is pushed to the free neighbouring cell farthest from the necromancer; if there is nowhere to push, a hostile blocker takes 2-10 damage and the necromancer waits a turn and retries. Leaving HUNTING cancels the summon. *Telegraph:* zap then charging animation, rattle particles on the target cell, a CHARGEUP sound, and the hero is interrupted <small>`core/…/mobs/Necromancer.java:296-332`, `core/…/mobs/Necromancer.java:199-262`, `core/…/mobs/Necromancer.java:77-84`, `core/…/mobs/Necromancer.java:419-420`, `core/…/sprites/NecromancerSprite.java:123-134`</small>
- **Empower skeleton.** Each turn it sees the hero and can see its skeleton (and the skeleton does not need teleporting), it heals the skeleton by HT/5 (5) if it is hurt, or otherwise gives it Adrenaline for 3 turns if it has none (double speed, attack delay divided by 1.5). *Telegraph:* visible health-ray beam and RAY sound <small>`core/…/mobs/Necromancer.java:166-194`, `core/…/mobs/Necromancer.java:386-396`, `core/…/actors/Char.java:774`, `core/…/mobs/Mob.java:755`</small>
- **Teleport skeleton.** If it cannot see its skeleton, or the skeleton cannot attack and has no path or a long one (over twice the necromancer's distance to the hero and 4+ steps), it re-summons the skeleton into a visible free cell next to the hero, taking 2 turns. It does this only while its own sprite is visible to the hero, and never if the skeleton is already adjacent to the hero. *Telegraph:* same summoning telegraph as the summon <small>`core/…/mobs/Necromancer.java:338-384`</small>
- **Death link.** When the necromancer dies, its skeleton dies too, and that death triggers the skeleton's bone explosion on everything adjacent to the skeleton. *Telegraph:* none <small>`core/…/mobs/Necromancer.java:112-127`, `core/…/mobs/Skeleton.java:70-131`</small>
- **Inflicts none directly.** Summon-block damage 2-10 if the hero blocks the summoning cell with nowhere to be pushed <small>`core/…/mobs/Necromancer.java:227-241`</small>
- **Resists.** None; extra DR 0-5. Property UNDEAD. <small>`core/…/mobs/Necromancer.java:64`, `core/…/mobs/Necromancer.java:96-99`</small>
- **AI.** Starts `sleeping`; flees: never. Custom Hunting: summons or supports a skeleton when the hero is seen; otherwise walks toward the hero until within 4 tiles. Leaving HUNTING cancels an in-progress summon. <small>`core/…/mobs/Necromancer.java:66`, `core/…/mobs/Necromancer.java:77-84`, `core/…/mobs/Necromancer.java:266-406`</small>
- **Evasion.** defenseSkill 14; evasive: no. Highest evasion in the standard prison rotation (the rare depth-9 bat has 15); HP 40. <small>`core/…/mobs/Necromancer.java:55-56`, `core/…/mobs/MobSpawner.java:118-124`, `core/…/mobs/Bat.java:39`</small>
- **Surprise.** Can be surprised: yes. Standard rules. <small>`core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/hero/Hero.java:744-752`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `close`; ranged attacks first: yes.
- **Plan.** Shoot it from 5+ tiles as it walks in. Once it summons, ignore or body-block the skeleton and push through to the necromancer (it has no attack). Kill it with sure-hit damage, making sure its skeleton is not adjacent to you when it drops. If a pack forms, retreat behind a door: without sight of you it cannot summon or heal. <small>`core/…/mobs/Necromancer.java:297`, `core/…/mobs/Necromancer.java:112-127`, `core/…/mobs/Necromancer.java:129-132`, `core/…/wands/WandOfMagicMissile.java:56-62`, `core/…/actors/Char.java:627-630`, `core/…/mobs/Mob.java:796-802`, `core/…/mobs/Mob.java:783-794`, `core/…/hero/HeroClass.java:174-176`, `core/…/hero/HeroClass.java:190-193`, `core/…/hero/HeroClass.java:204-211`, `core/…/hero/HeroClass.java:221-224`, `core/…/hero/HeroClass.java:233-238`, `core/…/hero/HeroClass.java:248-253`</small>
- **Kill or nullify: Focus the necromancer, not the skeleton.** A dead skeleton is re-summoned after 2 turns, while killing the necromancer kills the skeleton too. (derived) <small>`core/…/mobs/Necromancer.java:289-332`, `core/…/mobs/Necromancer.java:112-127`</small>
- **Kill or nullify: Do not stand next to its skeleton when the necromancer dies.** The linked skeleton dies with cause null, not Chasm, so it bone-explodes on its neighbours. (derived) <small>`core/…/mobs/Necromancer.java:122-124`, `core/…/mobs/Skeleton.java:75-80`</small>
- **Kill or nullify: Open with ranged attacks from 5+ tiles.** It summons only when the hero is within 4; farther out it just walks closer and does nothing. (derived) <small>`core/…/mobs/Necromancer.java:297`, `core/…/mobs/Necromancer.java:402-405`</small>
- **Kill or nullify: Break line of sight to stop summons and heals.** Summon, heal and teleport all need enemySeen, which is false while the hero is out of its view or invisible; the summon cell must also be in its FOV. (derived) <small>`core/…/mobs/Necromancer.java:270`, `core/…/mobs/Necromancer.java:297`, `core/…/mobs/Necromancer.java:311`, `core/…/mobs/Necromancer.java:334`, `core/…/mobs/Mob.java:290`</small>
- **Kill or nullify: Do not step onto the rattling summon cell.** If you occupy it you are pushed away, or take 2-10 if you cannot be pushed; it does not cancel the summon. (derived) <small>`core/…/mobs/Necromancer.java:199-243`</small>
- **Kill or nullify: Use sure-hit tools on it.** Evasion 14 makes weapon swings miss; surprise hits, Guiding Light and wand bolts ignore evasion. (derived) <small>`core/…/mobs/Necromancer.java:56`, `core/…/mobs/Mob.java:796-802`, `core/…/mobs/Mob.java:783-794`, `core/…/wands/WandOfMagicMissile.java:56-62`</small>
- **Kill or nullify: Expect a Potion of Healing drop from the first few.** Loot is a Potion of Healing at 20% x (6 - drops so far)/6. (derived) <small>`core/…/mobs/Necromancer.java:61-62`, `core/…/mobs/Necromancer.java:101-110`</small>
- **Escape.** Outrunnable: no; contact breaks at: door, out-of-sight, stairs. The necromancer is harmless on its own; escaping means escaping its skeleton (see NecroSkeleton). Out of its sight it can still teleport the skeleton only if it sees you. <small>`core/…/mobs/Necromancer.java:129-132`, `core/…/mobs/Necromancer.java:334-384`</small>

| Class | Note |
|---|---|
| Warrior | Walk through the skeleton to the necromancer; armor handles the Adrenaline-boosted skeleton and the death blast. |
| Mage | Magic missile never misses its evasion-14 body; zap the necromancer. |
| Rogue | Cloak into invisibility and stab it: invisible attacks always hit, and surprised mobs have no evasion. |
| Huntress | Shoot it from 5+ tiles before it can summon; at 4 or less, keep shooting the necromancer rather than the skeleton. |
| Duelist | Rush it; it cannot fight back. |
| Cleric | Guiding Light answers its high evasion. |

### Community notes

No community claim about this enemy was found.

??? note "Corrected during verification (7)"

    - Summon: added reachability constraint, push direction (farthest from necromancer), retry after block damage, and that the summon completes without spending time.
    - Empower: Adrenaline is only applied when the skeleton has none.
    - Teleport: not done when the skeleton is already adjacent to the hero.
    - Resolved the invisible-hero question: enemyInFOV is false for an invisible hero (Mob.java:290), so no summon, heal or teleport.
    - 'Highest regular evasion in the prison' qualified (rare depth-9 bat has 15); added UNDEAD and healing-potion loot.
    - Hand check: depths '8-10' corrected to '8-9'; the sewer, prison and caves boss floors create no rotation mobs and add no respawner (SewerBossLevel.java:132-138, PrisonBossLevel.java:597-604, CavesBossLevel.java:207-219).
    - Bestiary build: 'evasive' set to false. Its evasion is the highest of its cohort, but a hero of the level expected at its depth still lands most plain melee hits, which is what the tag means.

## Spectral necromancer {#spectralnecromancer}

`actors.mobs.SpectralNecromancer` · depths 8-9 (2% alternate of Necromancer) · **Tactic:** Top priority target. Burst it from range before it reaches 4 tiles if you can; otherwise charge straight at it, ignoring wraiths except those blocking the path, since killing it clears them all.

**Stats** (from the [Codex](../codex/mobs.md)): HT 40 · accuracy 0 · evasion 14 · damage 1 · armour 0-5 · EXP 7 · max level 14 · properties UNDEAD · loot PotionOfHealing (20% base). <small>`core/…/mobs/SpectralNecromancer.java:40`, `core/…/actors/Char.java:689`, `core/…/actors/Char.java:709`, `core/…/mobs/Necromancer.java:97`</small>

Depths corrected by hand: the boss floors 5, 10 and 15 create no rotation mobs and run no respawner, so this enemy never spawns there. <small>`core/…/mobs/MobSpawner.java:70-155`, `core/…/levels/SewerBossLevel.java:132-138`, `core/…/levels/PrisonBossLevel.java:597-604`, `core/…/levels/CavesBossLevel.java:207-219`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** Inherits Necromancer. Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/mobs/SpectralNecromancer.java:40`, `core/…/mobs/MobSpawner.java:265`</small>
- **Attack.** `none`, reach 0. Never attacks directly (inherited canAttack false). Attack time: n/a. <small>`core/…/mobs/Necromancer.java:129-132`</small>
- **Summon wraith (unlimited).** Uses the necromancer's summon logic but spawns a Wraith instead, set to level 4: 1 HP, accuracy 14, evasion 70, damage 3-6, flying, spawned HUNTING with enemySeen already true, no EXP. It never records a "mySkeleton", so the "no minion, summon" branch fires again every cycle: while it sees the hero within 4 tiles it adds a new wraith about every 2 turns, limited in practice only by free visible cells next to the hero. A wraith acts 2 turns after it appears. *Telegraph:* zap then charging animation, shadow particles on the target cell, CHARGEUP sound, hero interrupted; CURSED sound when the wraith appears <small>`core/…/mobs/SpectralNecromancer.java:103-164`, `core/…/mobs/Necromancer.java:289-332`, `core/…/mobs/Wraith.java:42`, `core/…/mobs/Wraith.java:49-58`, `core/…/mobs/Wraith.java:76-89`, `core/…/mobs/Wraith.java:116-173`, `core/…/sprites/SpectralNecromancerSprite.java:108-135`</small>
- **Death link.** When it dies, all of its wraiths die. *Telegraph:* none <small>`core/…/mobs/SpectralNecromancer.java:72-82`</small>
- **Summon block.** If the summon cell is occupied and the occupant cannot be pushed, a hostile blocker takes 2-10 damage and it waits. *Telegraph:* same as summon <small>`core/…/mobs/SpectralNecromancer.java:104-145`</small>
- **Loot.** On death it drops a Scroll of Remove Curse next to itself, but only if the hero's level is at most 16 (maxLvl 14 + 2). *Telegraph:* none <small>`core/…/mobs/SpectralNecromancer.java:59-70`, `core/…/mobs/Necromancer.java:59`</small>
- **Resists.** None; extra DR 0-5 (inherited). Property UNDEAD (inherited). <small>`core/…/mobs/Necromancer.java:64`, `core/…/mobs/Necromancer.java:96-99`</small>
- **AI.** Starts `sleeping`; flees: never. Necromancer hunting AI; the skeleton heal/teleport branch never runs because it has no skeleton. <small>`core/…/mobs/Necromancer.java:266-406`, `core/…/mobs/SpectralNecromancer.java:103-164`</small>
- **Evasion.** defenseSkill 14; evasive: no. Inherited; its wraiths have evasion 70. <small>`core/…/mobs/Necromancer.java:56`, `core/…/mobs/Wraith.java:85-89`</small>
- **Surprise.** Can be surprised: yes. Standard rules. Its wraiths are created with enemySeen=true, so they are not surprisable on arrival. <small>`core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/hero/Hero.java:744-752`, `core/…/mobs/Wraith.java:88`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `close`; ranged attacks first: yes.
- **Plan.** Top priority target. Burst it from range before it reaches 4 tiles if you can; otherwise charge straight at it, ignoring wraiths except those blocking the path, since killing it clears them all. If you cannot reach it, fall back out of sight behind a door and clean up wraiths with sure-hit tools. Below hero level 17 it drops a Scroll of Remove Curse. <small>`core/…/mobs/SpectralNecromancer.java:72-82`, `core/…/mobs/SpectralNecromancer.java:59-70`, `core/…/mobs/Wraith.java:85-89`, `core/…/wands/WandOfMagicMissile.java:56-62`, `core/…/actors/Char.java:627-630`, `core/…/mobs/Mob.java:783-794`, `core/…/hero/HeroClass.java:174-176`, `core/…/hero/HeroClass.java:190-193`, `core/…/hero/HeroClass.java:204-211`, `core/…/hero/HeroClass.java:221-224`, `core/…/hero/HeroClass.java:233-238`, `core/…/hero/HeroClass.java:248-253`</small>
- **Kill or nullify: Kill it as fast as possible; every 2 turns in range is another wraith.** Unlimited summons while you are visible within 4 tiles, and all wraiths die with it. (derived) <small>`core/…/mobs/Necromancer.java:297`, `core/…/mobs/SpectralNecromancer.java:72-82`, `core/…/mobs/SpectralNecromancer.java:147-163`</small>
- **Kill or nullify: Start from 5+ tiles or out of sight.** It only summons with the hero seen within distance 4. (derived) <small>`core/…/mobs/Necromancer.java:297`</small>
- **Kill or nullify: Against wraiths use attacks with no hit roll, or sure hits.** Wraith evasion is 70 but it has 1 HP: wand bolts, area damage, Guiding Light or a surprise attack kill it outright. It is INORGANIC, so poison and toxic gas do nothing to it. (derived) <small>`core/…/mobs/Wraith.java:49`, `core/…/mobs/Wraith.java:85-89`, `core/…/wands/WandOfMagicMissile.java:56-62`, `core/…/mobs/Mob.java:783-794`, `core/…/mobs/Wraith.java:57`, `core/…/actors/Char.java:1421-1422`</small>
- **Kill or nullify: Break line of sight to stop the wave.** Summons need enemySeen and a summon cell in its FOV. (derived) <small>`core/…/mobs/Necromancer.java:270`, `core/…/mobs/Necromancer.java:311`</small>
- **Escape.** Outrunnable: no; contact breaks at: door, out-of-sight, stairs. Its wraiths fly, so chasms and traps do not slow them; a closed door only breaks line of sight. Stairs leave all of them behind. <small>`core/…/mobs/Wraith.java:54`, `core/…/levels/Terrain.java:90`, `core/…/mobs/Mob.java:1686-1705`</small>

| Class | Note |
|---|---|
| Warrior | Charge through the wraiths (3-6 damage each) to the necromancer. |
| Mage | Magic missile kills 1-HP wraiths with no hit roll, but spend charges on the necromancer first. |
| Rogue | Cloak in and stab the necromancer: invisibility makes the hit certain. |
| Huntress | Shoot the necromancer first; arrows into evasion-70 wraiths are wasted. |
| Duelist | Rush the necromancer. |
| Cleric | Guiding Light on the necromancer; wraiths need sure hits too. |

**Open questions.**

- Wraith movement/AI beyond stats, flying and spawn state belongs to the Wraith card (other cohort).

### Community notes

- **Tier F.** “<del>Wraiths have very high evasion, so melee attacks miss often; they're described as weak to magic-type damage (including fire and poison), and this source claims a Wraith only has 1 HP so any connecting hit — including a guaranteed surprise attack performed by retreating behind a door — kills it outright.</del>” [source](https://pixeldungeon.fandom.com/wiki/User_blog:108.184.82.95/How_to_kill_Wraiths) (version: pre-v4 (source flagged as old version)). Claim is about Wraith generally; recorded here because the spectral necromancer summons them. Confirmed: HP 1 and evasion = 5 x accuracy (70 for its level-4 wraiths). Contradicted: wraiths are INORGANIC, so poison and toxic gas do nothing. Note summoned wraiths start with enemySeen = true, so the door-retreat surprise only works after they lose sight of you. <small>`core/…/mobs/Wraith.java:49`, `core/…/mobs/Wraith.java:57`, `core/…/mobs/Wraith.java:85-89`, `core/…/actors/Char.java:1421-1422`</small>

??? note "Corrected during verification (5)"

    - Escape note 'wraiths must path around a closed door' was wrong: doors are passable to everyone; flying only helps them over chasms and traps.
    - Remove Curse drop is conditional on hero level &lt;= 16 (SpectralNecromancer.java:61).
    - Noted wraiths are INORGANIC (poison/gas immune) and that the wave is bounded only by free cells next to the hero.
    - Hand check: depths '8-10 (2% alternate of Necromancer)' corrected to '8-9 (2% alternate of Necromancer)'; the sewer, prison and caves boss floors create no rotation mobs and add no respawner (SewerBossLevel.java:132-138, PrisonBossLevel.java:597-604, CavesBossLevel.java:207-219).
    - Bestiary build: 'evasive' set to false. Its evasion is the highest of its cohort, but a hero of the level expected at its depth still lands most plain melee hits, which is what the tag means.

## Necromancer's skeleton {#necromancer-necroskeleton}

`actors.mobs.Necromancer.NecroSkeleton` · depths 8-9 (summoned by Necromancer) · **Tactic:** Treat it as a distraction: body-block or walk past it to the necromancer.

**Stats** (from the [Codex](../codex/mobs.md)): HT 25 · accuracy 12 · evasion 9 · damage 2-10 · armour 0-5 · EXP 5 · max level -5 · properties INORGANIC, UNDEAD · loot WEAPON (16.7% base). <small>`core/…/mobs/Necromancer.java:409`, `core/…/mobs/Skeleton.java:157`, `core/…/mobs/Skeleton.java:66`, `core/…/mobs/Skeleton.java:162`</small>

Depths corrected by hand: the boss floors 5, 10 and 15 create no rotation mobs and run no respawner, so this enemy never spawns there. <small>`core/…/mobs/MobSpawner.java:70-155`, `core/…/levels/SewerBossLevel.java:132-138`, `core/…/levels/PrisonBossLevel.java:597-604`, `core/…/levels/CavesBossLevel.java:207-219`</small>

### Mechanics

Tier 1: read from the code at the pinned tag.

- **Movement.** Speed 1; doubled while it has Adrenaline (3 turns, re-applied only when it has none and is at full HP). Speed `normal`; flies: no; amphibious: no; water-bound: no; opens doors: yes; follows you on the stairs: no. <small>`core/…/mobs/Necromancer.java:409`, `core/…/mobs/Necromancer.java:186-194`, `core/…/actors/Char.java:774`</small>
- **Attack.** `melee`, reach 1. Adjacent only. Attack time: 1 turn; /1.5 under Adrenaline. <small>`core/…/mobs/Skeleton.java:65-68`, `core/…/mobs/Mob.java:753-757`</small>
- **Bone explosion on death.** Same as Skeleton: 6-12 to every adjacent character, armor DR counted twice. It also fires when the necromancer's death kills it. *Telegraph:* none <small>`core/…/mobs/Skeleton.java:70-141`, `core/…/mobs/Necromancer.java:122-124`</small>
- **Necromancer support.** Healed by 5 per zap or given Adrenaline; teleported next to the hero when it is stuck or out of the necromancer's view. *Telegraph:* health-ray beam; summoning particles for a teleport <small>`core/…/mobs/Necromancer.java:166-194`, `core/…/mobs/Necromancer.java:338-384`</small>
- **Immune.** Bleeding, ToxicGas, Poison (INORGANIC, inherited) <small>`core/…/mobs/Skeleton.java:61-62`, `core/…/actors/Char.java:1421-1422`</small>
- **Resists.** None; extra DR 0-5. Properties UNDEAD and INORGANIC (inherited). <small>`core/…/mobs/Skeleton.java:61-62`, `core/…/mobs/Skeleton.java:161-164`</small>
- **AI.** Starts `wandering`; flees: never. Created WANDERING next to the hero. At distance 1 a wandering mob notices a hero with stealth &lt;= 0.5 on its first action and starts hunting without spending time; it is also aggroed whenever the necromancer is aggroed. It gives no EXP or loot. <small>`core/…/mobs/Necromancer.java:412`, `core/…/mobs/Necromancer.java:416-420`, `core/…/mobs/Necromancer.java:86-94`, `core/…/mobs/Mob.java:1268-1295`</small>
- **Evasion.** defenseSkill 9; evasive: no. Summoned at 20/25 HP. <small>`core/…/mobs/Skeleton.java:53`, `core/…/mobs/Necromancer.java:419-420`</small>
- **Surprise.** Can be surprised: yes. Standard rules only. enemySeen starts false, but the skeleton is scheduled at the next whole turn and mobs act after the hero only on equal times, so with integer turn times it takes its first action (and notices an adjacent hero) before the hero can swing. Treat it as unsurprisable on arrival unless the hero is invisible or stealthy. <small>`core/…/mobs/Mob.java:873-877`, `core/…/mobs/Mob.java:796-802`, `core/…/hero/Hero.java:744-752`, `core/…/mobs/Mob.java:137`, `core/…/scenes/GameScene.java:1186-1195`, `core/…/actors/Actor.java:50-52`, `core/…/actors/Actor.java:266-268`, `core/…/mobs/Mob.java:1286-1295`</small>

### Tactics

Derived from the mechanics above; each line names the code it rests on.

- **Approach:** `avoid`; ranged attacks first: no.
- **Plan.** Treat it as a distraction: body-block or walk past it to the necromancer. If it blocks a corridor you must pass, kill it and use the 2-turn re-summon window to reach the necromancer. Be non-adjacent to it when the necromancer falls. <small>`core/…/mobs/Necromancer.java:112-127`, `core/…/mobs/Necromancer.java:326`, `core/…/mobs/Skeleton.java:124-126`, `core/…/hero/HeroClass.java:174-176`, `core/…/hero/HeroClass.java:190-193`, `core/…/hero/HeroClass.java:204-211`, `core/…/hero/HeroClass.java:221-224`, `core/…/hero/HeroClass.java:233-238`, `core/…/hero/HeroClass.java:248-253`</small>
- **Kill or nullify: Ignore it and kill the necromancer.** It dies with its master and gives no EXP; killed on its own, it is replaced 2 turns later. (derived) <small>`core/…/mobs/Necromancer.java:112-127`, `core/…/mobs/Necromancer.java:416-417`, `core/…/mobs/Necromancer.java:326`</small>
- **Kill or nullify: Step away from it before the necromancer dies, or kill it from range.** Its bone explosion hits all adjacent characters, including when it dies with the necromancer. (derived) <small>`core/…/mobs/Skeleton.java:78-80`, `core/…/mobs/Necromancer.java:122-124`</small>
- **Kill or nullify: Break the necromancer's line of sight.** No heal or Adrenaline without the necromancer seeing the hero and the skeleton. (derived) <small>`core/…/mobs/Necromancer.java:334-396`</small>
- **Kill or nullify: Tell it apart by its darker sprite.** NecroSkeletonSprite renders at 0.75 brightness, while a normal skeleton gives EXP and loot and has no master. (derived) <small>`core/…/mobs/Necromancer.java:428-440`</small>
- **Escape.** Outrunnable: no; contact breaks at: door, out-of-sight, stairs. Under Adrenaline it moves at double speed; a necromancer that sees you can teleport it back next to you. <small>`core/…/actors/Char.java:774`, `core/…/mobs/Necromancer.java:338-384`, `core/…/mobs/Mob.java:1686-1705`</small>

| Class | Note |
|---|---|
| Warrior | Armor makes its blast trivial; walk past to the necromancer. |
| Mage | Zap the necromancer; it is not worth charges. |
| Rogue | Not worth a surprise attempt on arrival; spend the Cloak on the necromancer. |
| Huntress | Spend arrows on the necromancer, not on it. |
| Duelist | Push past it. |
| Cleric | Target the necromancer. |

**Open questions.**

- Arrival ordering depends on fractional actor times; if the necromancer acts at a fractional time the hero could act first. Not harness-verified.

### Community notes

No community claim about this enemy was found.

??? note "Corrected during verification (4)"

    - Surprise-on-arrival claim dropped: it acts at the next whole turn, before the hero on integer times, and notices an adjacent hero for certain at stealth &lt;= 0.5 (Wandering.noticeEnemy spends no time).
    - 'Aggroed along with the necromancer' was wrong at spawn: it starts WANDERING; aggro() only propagates when the necromancer is aggroed.
    - Adrenaline is re-applied only when the skeleton is at full HP and has none.
    - Hand check: depths '8-10 (summoned by Necromancer)' corrected to '8-9 (summoned by Necromancer)'; the sewer, prison and caves boss floors create no rotation mobs and add no respawner (SewerBossLevel.java:132-138, PrisonBossLevel.java:597-604, CavesBossLevel.java:207-219).
