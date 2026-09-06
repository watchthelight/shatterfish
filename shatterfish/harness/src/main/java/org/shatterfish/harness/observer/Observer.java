package org.shatterfish.harness.observer;

import com.shatteredpixel.shatteredpixeldungeon.Challenges;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Shopkeeper;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.Artifact;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.Trap;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.tiles.DungeonTileSheet;
import com.watabou.noosa.Game;
import org.shatterfish.api.Challenge;
import org.shatterfish.api.Feeling;
import org.shatterfish.api.Fog;
import org.shatterfish.api.HeaderSection;
import org.shatterfish.api.HeapKind;
import org.shatterfish.api.HeapView;
import org.shatterfish.api.HeroClass;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.ObservationCodec;
import org.shatterfish.api.PromptKind;
import org.shatterfish.api.Tile;
import org.shatterfish.api.TrapView;
import org.shatterfish.harness.driver.HeadlessDriver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The one door from game state to the bot (non-negotiable 1; ADR-0006): reads, at an Input wait,
 * exactly what the screen draws, through the predicates the renderer and the HUD use, and builds
 * the sections of the Observation from them. Story 1.8 builds the header and the map; the actors
 * and the hero (story 1.9), the inventory, journal, log and Prompt (1.10) and the rows left
 * (1.11) follow, and {@code observe()} arrives when every section does. Nothing here reads a
 * field the screen does not draw, and every rule cites the drawing code at the pinned tag; paths
 * abbreviate {@code core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/} as {@code …/}.
 *
 * <p>Every method runs only at an Input wait: the hero is ready with no action and not resting,
 * and no window is open, which is what the driver confirms (ADR-0015) and what this class
 * asserts on entry. A Prompt window is story 1.10's; any window is a failure here.
 */
public final class Observer {

    /**
     * What each visual of the tile sheet's two tables looks like, as a {@link Tile}. The sheet
     * draws several terrains with one visual ({@code …/tiles/DungeonTileSheet.java:427-431},
     * {@code :446-447}, {@code :464}), so the table is keyed by visual and a terrain reaches it
     * through the sheet's own tables, never through a table of Shatterfish's own.
     */
    private static final Map<Integer, Tile> BY_VISUAL = new HashMap<>();

    static {
        visual(DungeonTileSheet.FLOOR, Tile.EMPTY);
        visual(DungeonTileSheet.GRASS, Tile.GRASS);
        visual(DungeonTileSheet.EMPTY_WELL, Tile.EMPTY_WELL);
        visual(DungeonTileSheet.ENTRANCE, Tile.ENTRANCE);
        visual(DungeonTileSheet.EXIT, Tile.EXIT);
        visual(DungeonTileSheet.EMBERS, Tile.EMBERS);
        visual(DungeonTileSheet.PEDESTAL, Tile.PEDESTAL);
        visual(DungeonTileSheet.FLOOR_SP, Tile.EMPTY_SP);
        visual(DungeonTileSheet.ENTRANCE_SP, Tile.ENTRANCE_SP);
        visual(DungeonTileSheet.FLOOR_DECO, Tile.EMPTY_DECO);
        visual(DungeonTileSheet.LOCKED_EXIT, Tile.LOCKED_EXIT);
        visual(DungeonTileSheet.UNLOCKED_EXIT, Tile.UNLOCKED_EXIT);
        visual(DungeonTileSheet.WELL, Tile.WELL);
        visual(DungeonTileSheet.FLAT_WALL, Tile.WALL);
        visual(DungeonTileSheet.FLAT_DOOR, Tile.DOOR);
        visual(DungeonTileSheet.FLAT_DOOR_OPEN, Tile.OPEN_DOOR);
        visual(DungeonTileSheet.FLAT_DOOR_LOCKED, Tile.LOCKED_DOOR);
        visual(DungeonTileSheet.FLAT_DOOR_CRYSTAL, Tile.CRYSTAL_DOOR);
        visual(DungeonTileSheet.FLAT_WALL_DECO, Tile.WALL_DECO);
        visual(DungeonTileSheet.FLAT_BOOKSHELF, Tile.BOOKSHELF);
        visual(DungeonTileSheet.FLAT_ALCHEMY_POT, Tile.ALCHEMY);
        visual(DungeonTileSheet.FLAT_BARRICADE, Tile.BARRICADE);
        visual(DungeonTileSheet.FLAT_HIGH_GRASS, Tile.HIGH_GRASS);
        visual(DungeonTileSheet.FLAT_FURROWED_GRASS, Tile.FURROWED_GRASS);
        visual(DungeonTileSheet.FLAT_STATUE, Tile.STATUE);
        visual(DungeonTileSheet.FLAT_STATUE_SP, Tile.STATUE_SP);
        visual(DungeonTileSheet.FLAT_REGION_DECO, Tile.REGION_DECO);
        visual(DungeonTileSheet.FLAT_REGION_DECO_ALT, Tile.REGION_DECO_ALT);
    }

    private static void visual(int visual, Tile tile) {
        Tile before = BY_VISUAL.put(visual, tile);
        if (before != null) {
            throw new IllegalStateException("the tile sheet draws " + before + " and " + tile + " with one visual, " + visual
                    + "; the map from visuals to tiles is not a function any more");
        }
    }

    public Observer() {
    }

    /** The upstream release the game is: the version the launcher set, as the tag names it. */
    public static String upstreamTag() {
        return "v" + Game.version;
    }

    /**
     * The header (ADR-0005): the schema version, the release, the hero's class, the challenges
     * the Run was started with ({@code …/Challenges.java:43-64}; the challenges window and the
     * hero window both show them), the depth and branch the interlevel screen and the status pane
     * name, whether the floor is locked by a boss fight ({@code …/levels/Level.java:180}, set by
     * {@code seal()} with the {@code LockedFloor} buff whose icon the HUD shows, {@code :617-630};
     * {@code …/actors/buffs/LockedFloor.java:76-78}), no oracle, and no Prompt, since no window is
     * open at a wait this class accepts.
     */
    public HeaderSection header() {
        atInputWait();
        Hero hero = Dungeon.hero;
        List<Challenge> challenges = new ArrayList<>();
        for (int i = 0; i < Challenges.MASKS.length; i++) {
            if ((Dungeon.challenges & Challenges.MASKS[i]) != 0) {
                challenges.add(Challenge.valueOf(Challenges.NAME_IDS[i].toUpperCase(Locale.ROOT)));
            }
        }
        return new HeaderSection(ObservationCodec.SCHEMA_VERSION, upstreamTag(), "", HeroClass.valueOf(hero.heroClass.name()),
                challenges, Dungeon.depth, Dungeon.branch, Dungeon.level.locked, false, PromptKind.NONE);
    }

    /**
     * The map (ADR-0005; ADR-0006, Cell visibility, Terrain, Traps, Heaps): per cell the fog
     * level the fog of war paints and, where the fog is not opaque, the tile the terrain tilemap
     * draws; the traps whose feature tile is drawn on a cell the fog does not hide; the heaps whose
     * sprite is visible on such a cell, showing what the sprite and the heap's own title show.
     * Blobs, the floor feeling and the transitions are story 1.11's and are empty here.
     *
     * <p>Not for play until story 1.9 (issue #22): a neutral, passive mimic has no heap and is
     * drawn as a chest, so until that story emits it as a {@link HeapKind#CHEST} the absence of a
     * heap under a chest sprite would tell a brain what the screen does not.
     */
    public MapSection map() {
        atInputWait();
        Level level = Dungeon.level;
        int cells = level.length();
        List<Fog> fog = new ArrayList<>(cells);
        List<Tile> tiles = new ArrayList<>(cells);
        for (int cell = 0; cell < cells; cell++) {
            Fog f = fog(level, cell);
            fog.add(f);
            tiles.add(f == Fog.UNKNOWN ? Tile.NONE : tile(level.map[cell]));
        }
        List<TrapView> traps = new ArrayList<>();
        for (Trap trap : level.traps.valueList()) {
            // The feature layer draws a trap only while it is visible, in its colour while active
            // and black once disarmed (…/tiles/TerrainFeaturesTilemap.java:56-62); the fog of war
            // then paints an unknown cell opaque over it (…/tiles/FogOfWar.java:200-205).
            if (trap.visible && fog.get(trap.pos) != Fog.UNKNOWN) {
                traps.add(new TrapView(trap.pos, trap.name(), trap.active));
            }
        }
        List<HeapView> heaps = new ArrayList<>();
        for (Heap heap : level.heaps.valueList()) {
            // The sprite is visible once the heap has been seen and stays so (…/sprites/ItemSprite.java:323-326;
            // …/levels/Level.java:991), blank for an empty heap (:213-215), faint for a hidden one
            // (:236), and it shows the top item or the container (:216-231); the heap's title
            // prints a single for-sale item's price (…/items/Heap.java:368-376) and its
            // description names a crystal chest's category (:394-406).
            if (!heap.seen || fog.get(heap.pos) == Fog.UNKNOWN || heap.items == null || heap.size() <= 0) {
                continue;
            }
            HeapKind kind = HeapKind.valueOf(heap.type.name());
            Item top = heap.peek();
            String item = kind == HeapKind.HEAP || kind == HeapKind.FOR_SALE ? top.title() : "";
            int price = kind == HeapKind.FOR_SALE && heap.size() == 1 ? Shopkeeper.sellPrice(top) : 0;
            String category = kind == HeapKind.CRYSTAL_CHEST ? category(top) : "";
            heaps.add(new HeapView(heap.pos, kind, heap.hidden, item, price, category));
        }
        return new MapSection(level.width(), level.height(), tiles, fog, traps, heaps, List.of(), Feeling.NONE, List.of());
    }

    /** The category word a crystal chest's description prints for what is inside ({@code …/items/Heap.java:400-406}). */
    private static String category(Item inside) {
        if (inside instanceof Artifact) {
            return Messages.get(Heap.class, "artifact");
        } else if (inside instanceof Wand) {
            return Messages.get(Heap.class, "wand");
        } else {
            return Messages.get(Heap.class, "ring");
        }
    }

    /**
     * The fog level the fog of war paints on a cell ({@code …/tiles/FogOfWar.java:200-267}),
     * raised to what the examine window gives for a wall the fog paints opaque.
     *
     * <p>A cell that cannot be discovered, or that is neither in view, visited nor mapped, is
     * opaque ({@code :200-205}). A cell that is not a wall is painted its own level: in view,
     * visited, mapped ({@code :288-299}). A wall cell is painted by the cells its face belongs to
     * ({@code :210-267}): opaque on the bottom row; a wall with a wall below it is painted in two
     * halves, each the darkest of the cell, its side neighbour and, when that neighbour is a
     * wall, the cell below that neighbour, and opaque when the neighbour and the cell below it are
     * both walls or the cell is at the map's edge; a wall with a floor below it is the darkest of
     * the cell and the cell below. The cell's level is the lighter of its two halves, the part the
     * player sees. So a room's far wall is opaque until the corridor beyond is seen, even while
     * in view.
     *
     * <p>Such a wall is still visited or mapped, so the examine window opens on it and draws its
     * tile ({@code …/scenes/GameScene.java:1661-1667}; {@code …/windows/WndInfoCell.java:42-74});
     * it is emitted as visited or mapped rather than unknown, which is what the player can learn
     * of it. The fog's own gate stays in front of that step: a cell that cannot be discovered is
     * never visited or mapped in play and reads unknown whatever the arrays say.
     * {@code FogParityTest} holds every cell to the painted texture.
     */
    static Fog fog(Level level, int cell) {
        Fog painted = painted(level, cell);
        if (painted == Fog.UNKNOWN && level.discoverable[cell] && wall(level, cell)
                && (level.visited[cell] || level.mapped[cell])) {
            return level.visited[cell] ? Fog.VISITED : Fog.MAPPED;
        }
        return painted;
    }

    /** The level the fog of war paints, as {@code FogOfWar.updateTexture} decides it ({@code FogOfWar.java:200-267}). */
    private static Fog painted(Level level, int cell) {
        if (!level.discoverable[cell] || (!level.heroFOV[cell] && !level.visited[cell] && !level.mapped[cell])) {
            return Fog.UNKNOWN;
        }
        if (!wall(level, cell)) {
            return own(level, cell);
        }
        int width = level.width();
        if (cell + width >= level.length()) {
            return Fog.UNKNOWN;
        }
        if (!wall(level, cell + width)) {
            return darker(own(level, cell), own(level, cell + width));
        }
        Fog left = Fog.UNKNOWN;
        if (cell % width != 0) {
            if (wall(level, cell - 1)) {
                left = wall(level, cell + width - 1) ? Fog.UNKNOWN
                        : darker(own(level, cell), darker(own(level, cell + width - 1), own(level, cell - 1)));
            } else {
                left = darker(own(level, cell), own(level, cell - 1));
            }
        }
        Fog right = Fog.UNKNOWN;
        if ((cell + 1) % width != 0) {
            if (wall(level, cell + 1)) {
                right = wall(level, cell + width + 1) ? Fog.UNKNOWN
                        : darker(own(level, cell), darker(own(level, cell + width + 1), own(level, cell + 1)));
            } else {
                right = darker(own(level, cell), own(level, cell + 1));
            }
        }
        return lighter(left, right);
    }

    /** A cell's own level: in view, visited, mapped, or nothing ({@code FogOfWar.java:288-299}). */
    private static Fog own(Level level, int cell) {
        if (level.heroFOV[cell]) {
            return Fog.VISIBLE;
        } else if (level.visited[cell]) {
            return Fog.VISITED;
        } else if (level.mapped[cell]) {
            return Fog.MAPPED;
        } else {
            return Fog.UNKNOWN;
        }
    }

    /** Whether the fog treats the cell as a wall: the sheet's stitching set ({@code FogOfWar.java:284-286}). */
    private static boolean wall(Level level, int cell) {
        return DungeonTileSheet.wallStitcheable(level.map[cell]);
    }

    private static Fog darker(Fog a, Fog b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    private static Fog lighter(Fog a, Fog b) {
        return a.ordinal() <= b.ordinal() ? a : b;
    }

    /**
     * What the terrain tilemap draws for a terrain, as a {@link Tile}: the sheet's direct table
     * first, then water and chasm, which are stitched from their neighbours, then the flat table
     * ({@code …/tiles/DungeonTerrainTilemap.java:42-56}; {@code …/tiles/DungeonTileSheet.java:414-465}).
     * A secret door reaches the wall's visual and a secret trap the floor's through those tables,
     * so no rule of Shatterfish's own decides what a secret looks like. The mine's crystal and
     * boulder share every sprite ({@code DungeonTileSheet.java:211-216}) and are told apart by the
     * cell's name ({@code …/levels/MiningLevel.java:227-235}), so they are the one pair mapped by
     * terrain. A terrain in none of the tables is a change of the tag and fails here.
     */
    static Tile tile(int terrain) {
        if (terrain == Terrain.MINE_CRYSTAL) {
            return Tile.MINE_CRYSTAL;
        } else if (terrain == Terrain.MINE_BOULDER) {
            return Tile.MINE_BOULDER;
        }
        Integer visual = DungeonTileSheet.directVisuals.get(terrain, null);
        if (visual == null && terrain == Terrain.WATER) {
            return Tile.WATER;
        } else if (visual == null && terrain == Terrain.CHASM) {
            return Tile.CHASM;
        }
        if (visual == null) {
            visual = DungeonTileSheet.directFlatVisuals.get(terrain, null);
        }
        if (visual == null) {
            throw new IllegalStateException("terrain " + terrain + " is in neither table of the tile sheet"
                    + " (DungeonTileSheet.java:414-465) and is neither water nor chasm; the tag has changed");
        }
        Tile tile = BY_VISUAL.get(visual);
        if (tile == null) {
            throw new IllegalStateException("the tile sheet draws terrain " + terrain + " with visual " + visual
                    + ", which no Tile names; the tag has changed");
        }
        return tile;
    }

    private static void atInputWait() {
        Level level = Dungeon.level;
        Hero hero = Dungeon.hero;
        require(level != null && hero != null, "no Run is in progress");
        // The driver's own condition for an Input wait (AD-5; ADR-0015), so there is one definition.
        require(HeadlessDriver.heroWaits(hero),
                "the hero is not waiting for input: ready=" + hero.ready + ", action=" + hero.curAction + ", resting=" + hero.resting);
        require(!GameScene.interfaceBlockingHero(),
                "a window is open or the inventory is selecting; a Prompt window is story 1.10's to read, and any other"
                        + " window at an Input wait is a failure (ADR-0006)");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("the Observer runs only at an Input wait: " + message);
        }
    }
}
