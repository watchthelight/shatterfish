package org.shatterfish.api;

/**
 * What a cell looks like: one member per visual the tile sheet distinguishes
 * ({@code core/.../tiles/DungeonTileSheet.java:414-465}, and the chasm and water tilemaps,
 * {@code :73-84}), named as the game names the terrain drawn that way
 * ({@code core/.../levels/Terrain.java:26-70}), plus {@link #NONE} for a cell the player has never
 * seen ({@link Fog#UNKNOWN}).
 *
 * <p>Terrain that is drawn like another terrain has no member of its own, because the screen does
 * not tell them apart: a trap's floor, active or not, is {@link #EMPTY} and the trap is a
 * {@link TrapView} drawn on its own layer only while it is revealed
 * ({@code core/.../tiles/TerrainFeaturesTilemap.java:57-62}); custom decoration floor is
 * {@link #EMPTY}; a door the hero locked is a {@link #LOCKED_DOOR} by visual and by name
 * ({@code core/.../levels/Level.java:1584-1586}). The two secret terrains are drawn as
 * {@link #WALL} and {@link #EMPTY} until found ({@code DungeonTileSheet.java:427}, {@code :464}),
 * so that is what the Observer emits (ADR-0006), and the schema cannot say otherwise (ADR-0005,
 * option 12).
 */
public enum Tile {
    NONE,
    CHASM, EMPTY, GRASS, EMPTY_WELL, WALL, DOOR, OPEN_DOOR, ENTRANCE, ENTRANCE_SP, EXIT, EMBERS,
    LOCKED_DOOR, CRYSTAL_DOOR, PEDESTAL, WALL_DECO, BARRICADE, EMPTY_SP, HIGH_GRASS, FURROWED_GRASS,
    EMPTY_DECO, LOCKED_EXIT, UNLOCKED_EXIT, WELL, BOOKSHELF, ALCHEMY, STATUE, STATUE_SP, REGION_DECO,
    REGION_DECO_ALT, MINE_CRYSTAL, MINE_BOULDER, WATER
}
