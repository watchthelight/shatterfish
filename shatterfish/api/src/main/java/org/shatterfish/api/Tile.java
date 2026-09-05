package org.shatterfish.api;

/**
 * What a cell looks like: one member per terrain the tile sheet can draw, named as the game
 * names the terrain ({@code core/.../levels/Terrain.java:26-70}), plus {@link #NONE} for a cell
 * the player has never seen ({@link Fog#UNKNOWN}).
 *
 * <p>The two secret terrains have no member. A secret door is drawn as a {@link #WALL} and a
 * secret trap as {@link #EMPTY} until found ({@code core/.../tiles/DungeonTileSheet.java:427},
 * {@code :464}), so that is what the Observer emits (ADR-0006), and the schema cannot say
 * otherwise (ADR-0005, option 12).
 */
public enum Tile {
    NONE,
    CHASM, EMPTY, GRASS, EMPTY_WELL, WALL, DOOR, OPEN_DOOR, ENTRANCE, ENTRANCE_SP, EXIT, EMBERS,
    LOCKED_DOOR, HERO_LKD_DR, CRYSTAL_DOOR, PEDESTAL, WALL_DECO, BARRICADE, EMPTY_SP, HIGH_GRASS,
    FURROWED_GRASS, TRAP, INACTIVE_TRAP, EMPTY_DECO, LOCKED_EXIT, UNLOCKED_EXIT, WELL, BOOKSHELF,
    ALCHEMY, CUSTOM_DECO_EMPTY, CUSTOM_DECO, STATUE, STATUE_SP, REGION_DECO, REGION_DECO_ALT,
    MINE_CRYSTAL, MINE_BOULDER, WATER
}
