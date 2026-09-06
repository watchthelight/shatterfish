---
status: accepted
date: 2026-09-03
deciders: watchthelight (product owner), Claude (engineer)
---

# ADR-0006: How the Observer handles each visibility rule

## Context and problem statement

`Observer` is the single door from game state to the Brain (non-negotiable #1). It must produce,
for every kind of hidden information, exactly what the game draws and nothing more, and it must
do so from the game's own predicates so that a change in what the game shows changes the
Observation the same way. Session 10 read every drawing predicate (`docs/rules/visibility.md`,
`identification.md`, `buffs.md`, `combat.md`) and found several places where the bootstrap's
assumptions were wrong. Decide, per rule, what the Observer reads and what it must never read.

Non-negotiables touched: #1 (parity), #8 (Codex over folklore: every rule below cites the line).

Paths abbreviate `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/` as `…/`; all at
`v3.3.8`.

## Decision drivers

- The Observation must equal the screen, including the game's own quirks (invisible chars drawn
  faintly; heaps remembered in fog, mobs not).
- Every input the Observer uses must be a field or method the renderer or HUD also uses, so a
  reviewer can check parity by reading two files side by side.
- The construction must be cheap enough to run every Input wait and must run on the thread the
  game allows (FR-12).
- Every rule must have a leak test that would fail if the rule were dropped.

## Considered options

1. **Read model fields through the exact predicates the renderer and HUD use** (`heroFOV`,
   `visited`, `mapped`, `discoverable`, `Trap.visible`, `Heap.seen`, `sprite.visible`,
   `Item.name()`, `BuffIndicator`'s icon rule). Chosen.
2. Scrape the Noosa scene graph (walk `GameScene`'s groups, read sprite positions and visibility).
   Rejected: ties the Observer to the render thread and to sprite lifetime; a headless scene may
   not create every visual; and heaps, fog and the inventory are drawn by tilemaps and windows,
   not sprites, so scraping would still need the model.
3. Render to a pixel buffer and parse it. Rejected: parity-perfect but slow, brittle, and it
   would turn the Codex's semantic knowledge into an OCR problem.
4. Hook the game's draw calls to emit events (a hook per tilemap, sprite and window). Rejected:
   many hooks against a budget of eight (PRD §10) for information option 1 already has.
5. Read raw model fields and blacklist the hidden ones. Rejected: a later tag adds a field and it
   leaks by default; option 1 is a whitelist.
6. Compute visibility independently in the Observer (own shadowcasting). Rejected: a second
   implementation of a game rule (non-negotiable #4's spirit) that could disagree with the game.

## Decision outcome

The Observer is a class in `org.shatterfish.harness` with one public method,
`observe(): Observation`, called only at an Input wait: the hero is ready and either no `Window` is
open or the open window is one of the recognised Prompt kinds (`docs/rules/game-loop.md`; quest and
shop windows are shown after `ready()`, `…/actors/hero/Hero.java:1019-1035`); any other window is an
assertion failure. It runs on the thread that owns the scene (ADR-0013). It reads the
following and nothing else.

| Rule | What the Observer reads | Cites | What it must never read |
|---|---|---|---|
| Cell visibility | `Level.heroFOV[c]` → `Fog.VISIBLE`; else `visited[c]` → `VISITED`; else `mapped[c]` → `MAPPED`; else `UNKNOWN`. Non-discoverable cells are `UNKNOWN`. | `…/tiles/FogOfWar.java:288-299`, `:200-208` | the raw `map[]` of an `UNKNOWN` cell |
| Terrain | For VISIBLE, VISITED and MAPPED cells, `Terrain.discover`-inverse is *not* applied: the Observer maps `map[c]` through the same table the tile sheet uses, so `SECRET_DOOR` becomes the `WALL` tile and `SECRET_TRAP` the `EMPTY` tile. | `…/tiles/DungeonTileSheet.java:427`, `:464`; `…/levels/Terrain.java:47-48` | `Level.secret[]`; the `SECRET` flag |
| Traps | A trap is present iff `trap.visible` *and* its cell's `Fog` is not `UNKNOWN`: the painter reveals a share of traps at generation and `trigger()` reveals traps mobs set off anywhere, but the fog paints unvisited cells opaque and examine refuses them. Kind is the display name; `active` as the inactive tile shows it. | `…/levels/traps/Trap.java:62-67`, `:92-102`; `…/levels/painters/RegularPainter.java:483-493`; `…/tiles/FogOfWar.java:200-208`; `…/scenes/GameScene.java:1661-1667`; `…/actors/hero/Hero.java:1888-1895` | traps on `UNKNOWN` cells; `visible == false` traps |
| Heaps | A heap is present iff `heap.seen` (sticky: remembered in fog) with its *current* top item, since the sprite updates on `drop` even out of FOV; `hidden` heaps carry the flag (drawn at alpha 0.15). Container types expose only the container, except a CRYSTAL_CHEST, which names the category (artifact, wand, ring); a plain HEAP or FOR_SALE exposes the top item's `title()`, and a single for-sale item its price (a stack of several shows none). A neutral, passive mimic has no heap and is emitted here as a CHEST at its cell, never as an actor; a differential test compares a real chest and a stealthy mimic at the same cell byte for byte. | `…/sprites/ItemSprite.java:212-238`, `:236`, `:323-326`; `…/items/Heap.java:80`, `:368-416`, `:400-406`; `…/actors/mobs/Mimic.java:62-64`, `:112-118`, `:148-152`, `:325-327` | a container's contents; `Heap.peek()` on a container; heaps with `seen == false`; the examine quirk that opens `WndInfoMob` on a hidden mimic (`…/scenes/GameScene.java:1729-1735`) |
| Mobs | A char is present iff `heroFOV[ch.pos]` (the sprite's `visible`), except a neutral passive mimic (a heap, above); its `invisible > 0` state is exposed as a flag because the sprite is drawn at alpha 0.4. Position, display name, alignment, and health quantised to the bar's pixel width (ADR-0005). Actors are ordered by cell, never by `Level.mobs` iteration. | `…/scenes/GameScene.java:1441-1448`; `…/actors/Char.java:1272-1274`; `…/sprites/CharSprite.java:401-408`; `…/ui/HealthBar.java:65-88` | `Level.mobs` outside `heroFOV`; exact `HP` |
| Mob state | The emote the sprite currently shows, read from `CharSprite.emo` and its `visible` flag through an accessor (hook row 4 of ADR-0008): `SLEEP`, `ALERT` (`!`), `LOST` (`?`), `INVESTIGATE`, or `NONE`. `Mob.alerted` is not the emote: it is set during other actors' turns and the icon appears only at the mob's next act. | `…/sprites/MobSprite.java:39`; `…/sprites/CharSprite.java:116`, `:635-639`, `:698-708`, `:719-729`; `…/effects/EmoIcon.java:126-148`; `…/actors/mobs/Mob.java:229-238`, `:812`, `:1190` | `Mob.state`; `Mob.alerted`; `Mob.enemySeen`; `Mob.target`; `Mob.enemy` |
| Mob buffs | Every buff with `icon() != NONE`, as `WndInfoMob`'s row shows (up to 50), each with the turns `WndInfoBuff`'s description would print, plus the sprite states set through `fx(true)`. | `…/windows/WndInfoMob.java:63-64`, `:80`; `…/actors/buffs/Buff.java:94-96`; `…/actors/buffs/FlavourBuff.java:37-42` | buffs without an icon or sprite state |
| Hero buffs | Every buff with an icon (the hero window's buffs tab lists them uncapped) with the turns its description shows (the large UI prints them on the icon too); hunger as the three HUD states. | `…/windows/WndHero.java:301-314`; `…/ui/BuffIndicator.java:192-196`, `:347-364`; `…/actors/buffs/Hunger.java:179-187` | icon-less buffs (`Regeneration`, `Awareness`, `Speed`, `Sleep`, `TimeStasis`); the exact hunger value |
| Items | `name()`, `title()`, `image`, `quantity`, `levelKnown`, `cursedKnown`, `visiblyUpgraded()`, `visiblyCursed()`, `status()`, `actions(hero)`, `defaultAction()`; for wands `curChargeKnown`; for rings `isKnown()`; equipped slot. | `…/items/Item.java:433-451`, `:483-499`; `…/items/wands/Wand.java:332-334`; `…/items/rings/Ring.java:238-241`; `…/windows/WndUseItem.java:54-76` | `getClass()` of an unknown potion, scroll or ring; `level()` or `cursed` when unknown; ID progress counters; `ItemStatusHandler.unknown()`; `Wand.curCharges` when `!curChargeKnown` |
| Known appearances | `Potion.getKnown()`, `Scroll.getKnown()`, `Ring.getKnown()` (this Run). | `…/items/potions/Potion.java:402-404` | `Catalog` (cross-Run); `ItemStatusHandler.itemLabels` beyond seen items |
| Vision buffs | Nothing special: mind vision, magical sight, blindness (a 3x3 FOV), darkness, Light and Foresight all act through `heroFOV`, `visited` and `mapped` before the Observer reads them. | `…/levels/Level.java:1290-1378`, `:1403-1411`; `…/Dungeon.java:914-938` | any recomputation of FOV |
| Blobs | For cells with `heroFOV[c]` (or an `alwaysVisible` blob), the set of blob kinds with `cur[c] > 0`; the emitter draws one particle per such cell regardless of volume and the cell info names the blob only. | `…/effects/BlobEmitter.java:59-70`; `…/windows/WndInfoCell.java:144-153` | `Blob.cur` outside `heroFOV`; any volume |
| Danger count | `hero.visibleEnemies()` as the indicator shows it (includes invisible enemies in FOV). | `…/ui/DangerIndicator.java:87-104` | anything else derived from `Level.mobs` |
| Log | The raw `GLog` messages (text and color prefix) captured from the `GLog.update` signal on the thread that emits them, kept in order and capped at N; never `GameLog.entries`, which are rendered on the render thread, merged when colors match and wrapped by UI size. Existence leaks the game itself makes ("You hear something die") are kept because the player sees them. | `…/ui/GameLog.java:52-129`; `…/utils/GLog.java` | `GameLog.entries` |
| Journal | `Notes` landmarks and keys recorded this Run. | `…/journal/Notes.java:115-142` | `Document` page state; `Bestiary` |
| Prompt | The open `Window`'s kind, text and option labels when it is one of the Prompt kinds (subclass, talent, quest, shop, alchemy, chasm jump, harmful-potion confirmation); any other window at an Input wait is an assertion failure. | `…/ui/Window.java:65-80`; `…/windows/WndOptions.java:90-92` | nothing more |
| Valid Actions | Computed by `ActionExecutor.validActions(observation)` from the Observation alone, never from game state, and then included in the Observation (FR-3). | | game state |
| Seed and turn | Nothing: the seed is drawn by `WndHero` but excluded so a Brain cannot fingerprint published seeds (FR-9), and the game draws no turn counter; both go to the Run log outside the hash. The Brain counts Input waits itself. | `…/windows/WndHero.java:188-211`; `…/actors/Actor.java:154-158` | `Dungeon.seed`; `Actor.now()`; wall-clock time |

Oracle mode (FR-11) is a separate class, `OracleObserver`, that returns the same `Observation`
(with `header.oracle = true`, so its hashes differ from a fair Run's) plus an `OracleView` sidecar
(true identities, unseen mob positions) that only the Overlay's oracle marking and the E9
labelling tool consume; the Brain's interface never receives the sidecar. It lives in `harness`,
is constructed only by the launcher flag, and the Rig's runner refuses it.

### Consequences

- Good: every row is checkable against two files, and the leak tests are one per row (PRD FR-8
  gains: an invisible char is present with the flag; a mob outside FOV is absent; a container's
  contents are absent; a trap on an unvisited cell is absent; a stealthy mimic equals a chest;
  the hunger value, the seed and blob volumes are absent).
- Good: the Observer has no visibility logic of its own; upstream's `updateFieldOfView` is the
  only FOV.
- Bad: reading `CharSprite.emo` needs a read-only accessor (an additive one-line hook under the
  accessor row of ADR-0008); the alternative, mirroring the emote logic, would be a second
  implementation.
- Bad: reproducing the mimic exception and the alpha-0.4 rule means the Observation carries two
  quirks the Brain must understand; the Codex documents both.

## Pre-mortem

*If this is wrong in six months, why?*

- A later tag changes a drawing predicate and the Observer silently diverges from the screen.
  Mitigation: each row's citation is a Rule; the upgrade procedure flips changed Rules to
  needs-review, and the toggle tests compare the Observation against the fog and sprite state of
  a headless scene.
- The Observer reads state while the actor thread is mutating it. Mitigation: it runs only at an
  Input wait, when the actor thread is parked in `Actor.process` (ADR-0013), and asserts
  `Dungeon.hero.ready` and no `Window` on entry.
- "What the HUD shows" is broader than what this table lists (the hero info window, the
  talents pane, item descriptions). Mitigation: those are static text the Codex already carries
  or per-item `info()`, which the Observer may add under the same whitelist rule; the schema
  version bumps.
- The valid-Action set computed from the Observation disagrees with what the ActionExecutor
  actually accepts. Mitigation: FR-4's completeness test plus a property test that every valid
  Action is accepted and every invalid one rejected before touching state.

## Amendment: story 1.8 (2026-09-06)

The Observer exists: `org.shatterfish.harness.observer.Observer`, with `header()` and `map()`.
The actors and the hero (story 1.9), the inventory, journal, log and Prompt (1.10) and the rows
left (1.11) follow, and `observe()` arrives when every section does, so that the Observer never
emits a section it cannot build. Every method asserts the Input wait on entry through the
driver's own predicate, `HeadlessDriver.heroWaits`, and `GameScene.interfaceBlockingHero()`
(`…/scenes/GameScene.java:1386-1396`), which covers a window and the inventory pane selecting, so
the driver and the Observer have one definition of a wait; a Prompt window is 1.10's to read, and
any window fails now. Paths abbreviate `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/`
as `…/`, at the tag.

**Terrain goes through the sheet's own tables.** The tilemap's order is the sheet's direct table,
then water and chasm, which are stitched from their neighbours, then the flat table
(`…/tiles/DungeonTerrainTilemap.java:42-56`; `…/tiles/DungeonTileSheet.java:414-465`), and the
Observer keeps a table from the sheet's visual constants to `Tile`, keyed by visual so that a
terrain reaches it only through the game's tables. A secret door reaches the wall's visual and a
secret trap the floor's that way; no rule of Shatterfish's own decides what a secret looks like.
The mine's crystal and boulder share every sprite index (`DungeonTileSheet.java:211-216`,
`:311-316`) and are told apart by the cell's name (`…/levels/MiningLevel.java:227-235`), so they
are the one pair mapped by terrain. A terrain in no table fails loudly, and `TerrainTableTest`
walks every constant of `Terrain` and holds that every tile but `NONE` is reached.

**Fog is the level the fog of war paints, cell for cell, raised to the examine level for a wall
painted opaque.** A cell that cannot be discovered, or that is neither in view, visited nor
mapped, is opaque (`…/tiles/FogOfWar.java:200-205`); a cell that is not a wall is painted its own
level, in view, visited or mapped (`:288-299`); a wall cell is painted by the cells its face
belongs to (`:210-267`): opaque on the bottom row, in two halves for a wall over a wall, each the
darkest of the cell, its side neighbour and, when that neighbour is a wall, the cell below it, and
the darkest of the cell and the cell below for a wall over floor; the wall tilemap hides the
visual by the same rule (`…/tiles/WallBlockingTilemap.java:194-202`). So a room's far wall is
opaque until the corridor beyond is seen, even while in view; the first draft emitted it as in
view, and the review found it. The Observer mirrors the painting, taking the lighter of a wall's
two halves as the part the player sees, and then one step past the paint: a discoverable wall
painted opaque that is visited or mapped is emitted at that level, since the examine window opens
on any visited or mapped cell and draws its tile (`…/scenes/GameScene.java:1661-1667`;
`…/windows/WndInfoCell.java:42-74`), which is what the player can learn of it; the fog's own gate
stays in front of that step, a cell that cannot be discovered being never visited or mapped in
play. `FogParityTest`
reads the painted texture back and holds every cell to it at the first wait, blinded, with mind
vision, after a scroll of magic mapping read the game's way, and with rock marked visited by
hand; and holds two readings of one wait byte-equal.

**Traps appear when visible on a cell that is not unknown.** The feature layer draws a trap's
tile only while it is visible, in its colour while active and black once disarmed
(`…/tiles/TerrainFeaturesTilemap.java:56-62`), and the fog paints an unknown cell opaque over it;
the kind is `Trap.name()`. `MapLeakTest` holds a revealed trap on an unknown cell absent, present
once the cell is mapped, and inactive once disarmed.

**Heaps appear when seen on a cell that is not unknown, and not when empty**, since the sprite is
blank then (`…/sprites/ItemSprite.java:213-215`) and visible only once seen (`:323-326`;
`…/levels/Level.java:991`). The kind is the type; `hidden` is the faint sprite (`:236`); the item
is the top item's title for a plain or for-sale heap, as the sprite shows it (`:216-217`); the
price is `Shopkeeper.sellPrice` for a for-sale heap of one entry, exactly as the heap's own title
prints it (`…/items/Heap.java:368-376`; `…/actors/mobs/npcs/Shopkeeper.java:201-203`;
`core/src/main/assets/messages/items/items.properties:2354`), and 0 for a heap of several items.
The row above says "a stack of several shows none"; the heap counts entries, not quantity, so a
stacked item is one entry whose title prints the price of the whole stack, and it is a heap of
several distinct items that prints none. `MapLeakTest` holds both. The category is
the words the description prints for a crystal chest (`Heap.java:400-406`;
`items.properties:2361-2363`), "an artifact", "a wand" or "a ring". A passive mimic drawn as a
chest is story 1.9's, with its differential test.

**The header is built now.** The release as `"v" + Game.version`, the class, the challenges by
their name ids in name order (`…/Challenges.java:43-64`), the depth and branch, the boss lock as
`sealed` (`…/levels/Level.java:180`, set by `seal()` together with the `LockedFloor` buff whose
icon the HUD shows, `:617-630`; `…/actors/buffs/LockedFloor.java:76-78`; ADR-0005's table says
the locked stairs and the boss bar, which are each boss level's own `seal()` and an assignment of
its own), pulled forward from story 1.11 since it is one drawn flag, no oracle, and no Prompt,
since no window is open at a wait the Observer accepts.

**Left empty by design.** Blobs, the floor feeling and the transitions are empty until story 1.11;
less is never a leak. `map()` is not for play until story 1.9 (#22): a neutral, passive mimic has
no heap and is drawn as a chest, so until 1.9 emits it as a chest the absence of a heap under a
chest sprite would say what the screen does not; the method's Javadoc says so, and no brain reads
the section yet. The leak tests of this story are `MapLeakTest`, `FogParityTest`,
`TerrainTableTest` and `ObserverGateTest`, named in `docs/rules/visibility.md`.

## Amendment: story 1.9 (2026-09-06)

The actors and the hero exist: `Observer.actors()` and `Observer.hero()`, and a hidden mimic is a
heap of `map()`. Paths abbreviate `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/`
as `…/`, at the tag.

**A mob is present exactly when its sprite is drawn**: in the hero's field of view
(`…/scenes/GameScene.java:1447`; `…/actors/Char.java:1272-1274`), every mob of `Level.mobs` but a
hidden mimic. It carries its display name, its alignment, its health as the bar over it draws it,
the invisible flag for a sprite drawn at alpha 0.4 (`…/sprites/CharSprite.java:401-407`), the
emote its sprite shows, and every buff with an icon as the examine window's row lists them
(`…/windows/WndInfoMob.java:63-64`, `:80`).

**Health is the bar's pips.** `HealthBar.level(Char)` fills the bar with health over the greater
of health plus shielding and the maximum (`…/ui/HealthBar.java:82-88`), and the bar is hidden at
full health with no shield (`…/ui/CharHealthIndicator.java:55`), which reads as every pip lit;
the codec's `healthPips` quantises the share (ADR-0005). `ActorLeakTest` holds a mob at five
health behind a shield of five to the formula, and the actors' JSON to carrying no health value.

**The emote comes through hook row 4, and the sleep icon through the sprite's own rule.**
`CharSprite.emo` is protected with no getter (`CharSprite.java:116`), written only by the show
and hide methods under a lock (`:655-737`); `shatterfishEmote()` returns it under the same lock
and writes nothing (`docs/UPSTREAM.md`, row 4). The alert, investigate and lost icons are set and
cleared by the acts themselves (`…/actors/mobs/Mob.java:229-238`; `…/effects/EmoIcon.java:102`,
`:126`, `:150`) and are read from the sprite. The sleep icon is different: every frame the mob
sprite shows it while the mob is alive and sleeping and hides it otherwise, replacing any other
icon (`…/sprites/MobSprite.java:39`; `CharSprite.java:635-639`, `:655-675`), and the driver's
frame updates the sprites before the acts of a turn, so at an Input wait the sprite still carries
the icon of the frame before those acts; a mob put to sleep or woken during the turn would read
one frame stale, and the Overlay, whose frames are not fenced, would read the fresh icon for the
same Run. So the Observer applies the sprite's rule as the next frame applies it, reading
`state == SLEEPING` for that one drawn bit and dropping a sleep icon that frame would hide; that is
the one read of `Mob.state` the mob-state row's last column allows, since it is the predicate the
renderer applies every frame, and nothing else of the state reaches the Observation.
`ActorLeakTest` holds hunting, wandering and fleeing, a target and the seen flag to one
Observation; holds a mob put to sleep with no frame drawn to `SLEEP`, the next frame agreeing;
holds a mob woken with no frame drawn to `NONE` while the stale icon is still on the sprite;
and holds the alert icon read through the accessor.

**Buffs are the ones with an icon** (`…/ui/BuffIndicator.java:192-196`; `…/windows/WndHero.java:301-314`;
`WndInfoMob.java:63-64`), with a flavour buff's turns as its description prints them, the visual
cooldown to two decimals (`…/actors/buffs/FlavourBuff.java:35-42`; `…/actors/buffs/Buff.java:136-138`,
`:141-143`), carried only when the description the window shows contains them: one flavour buff,
the shadows of foliage, prints none (`…/actors/buffs/Shadows.java:125-127`) and its cooldown is
its own scheduling, which no window shows, so it reads as untimed. A buff of another kind prints
its own numbers in its own words, which the schema does not carry: a loss, recorded, for a later
schema to close. Two identical icons draw twice; the schema lists a buff once.

**The hero section is the HUD's numbers**, with the cites on `HeroSection`: the talents of the
tiers the pane shows, counted from the level with the subclass and the ability as gates
(`…/ui/TalentsPane.java:75-86`); the unspent points clamped at zero, as the stars are drawn; hunger
as the icon's state (`…/actors/buffs/Hunger.java:179-187`) with the value behind it absent; a
quickslot's placeholder as an item of no quantity (`…/QuickSlot.java:72-74`), and the quickslot's
item named as the inventory names it. `HeroSectionTest` holds each against the game.

**A hidden mimic is the chest it is drawn as.** Neutral and passive (`…/actors/mobs/Mimic.java:62-64`),
it names itself as the chest (`:112-118`; `…/actors/mobs/GoldenMimic.java:51-53`;
`…/actors/mobs/CrystalMimic.java:59-61`), and a crystal mimic describes its category as a crystal
chest would (`CrystalMimic.java:68-84`); it is drawn like any mob in view and, when stealthy, once
its cell is visited (`GameScene.java:1441-1447`). So `map()` emits it as a `CHEST`, a
`LOCKED_CHEST`, a `CRYSTAL_CHEST` with the category of its item, or the `EBONY_CHEST` only an ebony
mimic wears (`…/actors/mobs/EbonyMimic.java:47-71`; `…/sprites/ItemSpriteSheet.java:124`), a member
added to `HeapKind` for it since the screen draws it, flagged hidden since it hides at alpha 0.2
(`…/sprites/MimicSprite.java:121-125`) as a faint heap does; `actors()` never lists a hidden mimic.
`MimicDifferentialTest` pairs each of the three real chests with the mimic that imitates it at one
cell and holds the Observations byte-identical, then stops the hiding and holds the mimic an actor
and the heap gone. The hint a non-stealthy mimic's description carries (`Mimic.java:121-130`) and
the window the game opens on it (`GameScene.java:1729-1735`) are not read, as the row says; a human
who taps can learn more than the bot here, which is a loss and not a leak. The alignment flips on
the mimic's act, not on its reveal (`Mimic.java:212-222`, `:134-145`), which comes before the next
Input wait in play.

Left: `observe()`, and the sections of stories 1.10 and 1.11.

## Amendment: story 1.10 (2026-09-06)

The inventory, the journal, the log and the Prompt exist: `Observer.inventory()`, `journal()`,
`log()` and `prompt()`, and the header carries the Prompt's kind. Paths abbreviate
`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/` as `…/`, at the tag.

**An item is what the slot, the item window and the log print.** The belongings in the order the
bag iterates them, the six slots then the backpack with a bag before its contents
(`…/actors/hero/Belongings.java:422-453`; `…/items/bags/Bag.java:216-250`), which
`InventorySection` enforces (ADR-0005). The name is `name()`: an unknown potion's colour, scroll's
rune or ring's gem (`…/items/potions/Potion.java:377-379`; `…/items/scrolls/Scroll.java:240-242`;
`…/items/rings/Ring.java:172-174`), and a weapon's or armor's curse enchantment only once the
curse is known (`…/items/weapon/Weapon.java:408-416`; `…/items/armor/Armor.java:573-581`). The
level and curse flags carry the values the slot draws, `visiblyUpgraded()` and `visiblyCursed()`
(`…/items/Item.java:433-443`; `…/ui/ItemSlot.java:279-283`); the status is the slot's text
(`ItemSlot.java:234`; `Item.java:570-572`), so a wand shows nothing until its level is known and
`?/max` until its charges are (`…/items/wands/Wand.java:336-343`), and an artifact nothing until
identified and uncursed (`…/items/artifacts/Artifact.java:189-206`); the actions are the item
window's buttons, `actions(hero)`, and the default the one it colours (`Item.java:110-115`,
`:179-181`; `…/windows/WndUseItem.java:54-76`), carried as the identifiers the window executes,
the default kept even while it is not offered, as an empty waterskin keeps drink
(`…/items/Waterskin.java:52`, `:74-78`). The family is the item's package, as `ItemKind` lays out.
The level and curse flags are the item's own fields where the slot and the item window draw their
effect, and the item's own identification predicate raises both where that predicate is what the
screen draws instead: a potion or scroll is identified exactly when its type is known
(`…/items/potions/Potion.java`, `isIdentified()`; `…/items/scrolls/Scroll.java`), and food, keys,
stones, bags, spells, bombs and the rest are identified always (`…/items/food/Food.java`;
`…/items/keys/Key.java`; `…/items/stones/Runestone.java`), the slot drawing their type icon on that
predicate (`…/ui/ItemSlot.java:244-249`); the review found that a potion identified by a scroll
and one of the same known type picked up later differ in the raw fields (`Item.java:468-469`)
and not on the screen, and `ItemLeakTest` now holds the two to one Observation.
`ItemLeakTest` holds an unknown potion, scroll and ring to their appearance with the class and the
true name absent from the JSON and the bytes; holds a weapon at +2, cursed with a curse
enchantment, a wand at zero charges and the identification counters of a wand and a ring, all
unknown, byte-identical to the same items at +0, uncursed, charged and counted otherwise; and
holds the order with every hero's velvet pouch before the stone put into it.

The losses: the slot's small type icon, the strength text and its colours and the level's colours
(`ItemSlot.java:244-300`) are not carried, since the name, the flags and the status say what they
draw; and a lost inventory (`…/actors/hero/Belongings.java:110-116`;
`…/ui/InventoryPane.java:358-364`) greys every slot the pane draws, which the section does not
carry: the items are drawn and listed, and a use the game refuses is the executor's to report
(story 1.13).

**The journal is every note the tab draws, and the identified potions, scrolls and rings.** The
notes tab draws the written notes, then every floor's landmarks and keys down from the deepest
(`…/windows/WndJournal.java:497-541`), and the Observer reads through the tab's own two calls
(`…/journal/Notes.java:685-705`), so a record on a floor the tab does not list, which no caller
writes today, is not a note, as the review asked: a landmark's title at its floor (`:206-217`), a key's title and
count (`:324-331`, `:344-346`), a written note's title, body and the floor it names, if it names
one (`:430-437`, `:487-495`). The known appearances are the three `getKnown()` sets
(`…/items/potions/Potion.java:402-404`; `…/items/scrolls/Scroll.java:265-267`;
`…/items/rings/Ring.java:280-282`), each by the name it draws once known
(`…/items/Item.java:501-503`); the Catalog, the guide and the bestiary are not read.
`JournalSectionTest` holds a landmark, a counted key and two written notes; `ItemLeakTest` holds
the three every hero and the Warrior start knowing (`…/actors/hero/HeroClass.java:117`,
`:183-184`) and an identified item's arrival.

**The log is the signal, captured at the pane's own seam.** `GLog` dispatches every message on
`GLog.update` (`…/utils/GLog.java:32-60`); the pane's constructor replaces every listener with
itself (`…/ui/GameLog.java:47`; `SPD-classes/…/utils/Signal.java:58-61`) on every scene creation,
and `create()` then emits the floor's own lines (`…/scenes/GameScene.java:596-599`, `:663-689`),
so the Observer's listener, `GameLogListener`, is re-added through hook row 3, a site right after
the pane is constructed (`docs/UPSTREAM.md`, row 3), before those lines. The pane's handler
returns false (`GameLog.java:149-154`), so the listener after it hears every message; the tone is
the pane's rule on the prefix (`:72-87`), the new-line marker is dropped (`:66-69`), and the
newest sixty-four are kept (ADR-0005). The listener also joins the signal at the start of every
Run, before the first floor is built, since a message dispatched then reaches the next pane
through the signal's static buffer (`GameLog.java:52`, `:57-60`). What the signal does not carry
is the pane's wipe: `GameLog.wipe()` empties the pane with no message (`…/items/journal/Guidebook.java:57`;
`…/windows/WndSettings.java:1093`), and the section keeps lines the human saw before it, memory
and not hidden information, recorded as a loss. `LogListenerTest` changes floor twice the way the game
does, the scene destroyed and recreated, and holds the section to the descent line each creation
emits and to a message after it, holds the signal to two listeners, and holds a message dispatched
while the first floor is built.

**A Prompt is the window in front, read through hook row 4's second site.** The gate admits a
window exactly when it is one the game opens on its own and waits on, `Prompts.kind(window)`
not `NONE`, with the hero waiting under it or the resurrection window up
(`…/windows/WndResurrect.java:98-114`), and fails every read on any other window, naming it; that
is the driver's own definition of a wait's state, `HeadlessDriver.waitState`, which the driver
confirms only with two timing conditions besides, a window's second frame in front and an empty
render queue, which a reader of the state cannot see. The
kinds: the subclass choice, the ghost's, wandmaker's, imp's and blacksmith's quest windows, the
trade window and the resurrection window by their classes; an options window by the nearest
named class enclosing the anonymous subclass its opener declares, every opener at the tag
declaring one: the chasm's jump (`…/levels/features/Chasm.java:59-62`), a potion's harmful-drink
warning by its title (`…/items/potions/Potion.java:238-252`), any other item's confirmation or
choice (`ITEM`), the talents pane's random-talent confirmation (`…/ui/TalentsPane.java:189-192`),
the quest givers', the levels' and the shopkeeper's follow-ups (`QUEST`, `SHOP`), and `OTHER` for
the rest, with what they draw. The title, text and options are what the window draws, read
through `Group.shatterfishMembers()` (`docs/UPSTREAM.md`, row 4): the recognised windows draw a
title first, an icon title's label or a title block, then a message block, then their buttons
(`…/windows/WndOptions.java:40-66`; `…/windows/WndTitledMessage.java:42-54`;
`…/windows/WndResurrect.java:65-74`; `…/windows/WndChooseSubclass.java:49-93`), and an untitled
options window the message alone. The title is the icon title's label where the window has one,
read by type, since a titled message brings its title bar to the front after laying it out
(`…/windows/WndTitledMessage.java:67`) and the drawing order then has the title last while the
screen shows it first, which the review's quest-window test found; otherwise the title is the
first text block when there are two, a title block coming before its message
(`WndOptions.java:53-59`). The text is the rest, and the options the styled buttons' labels in
drawing order (`…/ui/StyledButton.java:124`; `…/ui/RenderedTextBlock.java:96`), icon buttons and
item slots being no option. A member the group would not draw, one that does not exist or is not visible
(`SPD-classes/…/noosa/Group.java:72-79`; `…/noosa/Gizmo.java:26-29`), is not read, and an item
slot's texts, the status, strength and level of the item it shows (`…/ui/ItemSlot.java:220-300`),
are the slot's decorations and not the window's words, which the review found the resurrection
window's two slots would otherwise have added to its text. The same accessor on `Game.scene()`
finds the window in front, as the scene does for itself (`…/scenes/GameScene.java:1376-1384`).
`PromptGateTest` holds the chasm prompt, a known harmful potion's warning, a known beneficial
potion's throw and the chalice's warning to their kinds, titles, texts and labels; a quest window
with no buttons, the trade window, the subclass choice and the resurrection window to their kinds
and labels; an options window of unlisted origin to `OTHER`, an untitled one to an empty title, a
message window over an options window to a failure of every read and the options once it closes,
and a plain message window to a failure of every read.

`PromptKind` gains `ITEM` and `OTHER` at the end of the list, as `HeapKind` gained a member in
story 1.9. `ALCHEMY` is never produced at the tag, alchemy being a scene; `TALENT` is the pane's
one confirmation. The blacksmith's later windows (`WndBlacksmith.WndSmith`, `WndReforge`),
protected inside their class, and the crown's ability choice (`WndChooseAbility`) stay
unrecognised until the story that answers them, a loss recorded here: a Run that reaches one
stops at the driver rather than reading it. A quest window with no buttons, the ghost's or the
wandmaker's first words, carries no options; how a Brain dismisses it is ADR-0014's to say when
the executor lands.

Left: `observe()` and the rows of story 1.11.
