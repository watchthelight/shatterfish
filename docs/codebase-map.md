# Codebase map

File to mechanic, for the pinned upstream tag (`v3.3.8`, see [Upstream](UPSTREAM.md)).

!!! note "Placeholder"
    Bootstrap session 10 runs BMAD's document-project workflow over upstream, scoped to what
    Shatterfish touches, and folds the result in here. Every statement will cite `path:line`
    in the pinned tag. Until then this page lists the entry points the bootstrap prompt
    suggests, each marked *unverified*; nothing here may be relied on before it is checked
    against the code and moved into [Rules](rules/index.md).

## Areas to document

| Area | Entry points (unverified, from the bootstrap prompt, section 10) |
|---|---|
| Game loop and hero input | `Actor.process`, `Hero.handle`, `Hero.curAction`, `Hero.ready`, `Hero.act`, `Item.execute` |
| Visibility | `Dungeon.observe`, `Level.heroFOV`, `Level.visited`, `Level.mapped`, `Level.updateFieldOfView`, `Char.invisible`, `MindVision`, `Blindness` |
| Items and identification | `Item.name`, `Item.isIdentified`, `Item.levelKnown`, `Item.cursedKnown`, `ItemStatusHandler`, `Notes`, `Catalog` |
| Mobs and AI states | `Mob` states, `Mob.loot`, `Mob.lootChance`, `Char.hit`, `Bestiary.getMobRotation` |
| Level generation and special rooms | `Level.addItemToSpawn`, `Level.feeling`, `Terrain.SECRET_DOOR`, `Trap.visible`, `Heap.seen` |
| Generator and guarantees | `Generator`, `Dungeon.posNeeded`, `Dungeon.souNeeded`, `Dungeon.LimitedDrops` |
| RNG | `com.watabou.utils.Random`, `Dungeon.seed`, `resetGenerators` |
| Buffs | `Char` buffs, `MindVision`, `Blindness` |
| UI toolkit | `GameScene`, `PixelScene`, `Window`, `Chrome`, `RedButton`, `IconButton`, `Icons`, `StatusPane`, `Toolbar`, `KeyBindings`, `SPDAction` |
| Save and load | `Bundle` |
| Text and assets | `core/src/main/assets/messages/**/*.properties`, `Assets`, `Document`, `journal.properties` |
| Changelog | the `changes` package |
| Render thread | `com.watabou.noosa.Game.runOnRenderThread` |

## Discrepancies

Where the pinned tag contradicts the bootstrap prompt's section 3, the tag wins and the
discrepancy is recorded here.

_None recorded yet._
