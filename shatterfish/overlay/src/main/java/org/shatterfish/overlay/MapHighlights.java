package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.tiles.DungeonTilemap;
import com.watabou.noosa.ColorBlock;
import com.watabou.noosa.Gizmo;
import com.watabou.noosa.Group;
import com.watabou.noosa.Scene;

import java.util.ArrayList;
import java.util.List;

/**
 * The planned path, the target and the considered cells, outlined on the dungeon itself (story 5.10,
 * FR-41, UX-DR10, non-negotiable 6).
 *
 * <p>Native: the game's own {@code ColorBlock}s on the world camera, four thin edges to a cell and never a
 * filled quad, so a cell is outlined and its floor still shows. Never over a sprite: the layer sits in the
 * scene directly behind the terrain's group, so the items, the characters, the walls' tops, the fog and the
 * HUD are all drawn over it ({@code GameScene.java:254-399} is the draw order it is slotted into). The
 * colours are DESIGN.md's; the shapes differ as well so colour never carries the meaning alone (UX-DR14):
 * the path is a thin full outline, the target a thick one and a considered cell four corner brackets.
 *
 * <p>The layer draws what {@link MapHighlightContent} hands it and nothing it works out itself.
 */
final class MapHighlights extends Group {

    /** The game's world cell size in pixels ({@code DungeonTilemap.SIZE}). */
    static final int CELL = DungeonTilemap.SIZE;
    static final float PATH_EDGE = 1;
    static final float TARGET_EDGE = 2;
    static final float CONSIDERED_EDGE = 1;
    /** How far a considered cell's corner bracket reaches along each edge. */
    static final float BRACKET = 4;

    /** One outlined cell and the blocks that draw it. */
    static final class Outline extends Group {
        private final MapHighlightContent.Role role;
        private final int cell;

        Outline(MapHighlightContent.Role role, int cell, int mapWidth) {
            this.role = role;
            this.cell = cell;
            float x = (cell % mapWidth) * CELL;
            float y = (cell / mapWidth) * CELL;
            int color = 0xFF000000 | role.rgb();
            switch (role) {
                case PATH -> frame(x, y, PATH_EDGE, color);
                case TARGET -> frame(x, y, TARGET_EDGE, color);
                case CONSIDERED -> brackets(x, y, CONSIDERED_EDGE, color);
            }
        }

        private void block(float x, float y, float width, float height, int color) {
            ColorBlock block = new ColorBlock(width, height, color);
            block.x = x;
            block.y = y;
            add(block);
        }

        private void frame(float x, float y, float edge, int color) {
            block(x, y, CELL, edge, color);
            block(x, y + CELL - edge, CELL, edge, color);
            block(x, y + edge, edge, CELL - 2 * edge, color);
            block(x + CELL - edge, y + edge, edge, CELL - 2 * edge, color);
        }

        private void brackets(float x, float y, float edge, int color) {
            for (int right = 0; right < 2; right++) {
                for (int below = 0; below < 2; below++) {
                    float bx = right == 0 ? x : x + CELL - BRACKET;
                    float by = below == 0 ? y : y + CELL - edge;
                    block(bx, by, BRACKET, edge, color);
                    float vx = right == 0 ? x : x + CELL - edge;
                    float vy = below == 0 ? y + edge : y + CELL - BRACKET;
                    block(vx, vy, edge, BRACKET - edge, color);
                }
            }
        }

        MapHighlightContent.Role role() {
            return role;
        }

        int cell() {
            return cell;
        }

        /** The 24-bit colour this outline is drawn in. */
        int rgb() {
            return role.rgb();
        }
    }

    private MapHighlightContent.Cells shown = MapHighlightContent.Cells.NONE;
    private int shownWidth;

    /**
     * Outlines {@code cells} on a map {@code mapWidth} cells wide, replacing what was outlined before; the
     * same cells again change nothing. {@link MapHighlightContent.Cells#NONE} clears the layer.
     */
    void show(MapHighlightContent.Cells cells, int mapWidth) {
        if (cells.equals(shown) && (cells.isEmpty() || mapWidth == shownWidth)) {
            return;
        }
        clear();
        shown = cells;
        shownWidth = mapWidth;
        if (mapWidth <= 0) {
            return;
        }
        for (MapHighlightContent.Role role : MapHighlightContent.Role.values()) {
            for (int cell : cells.of(role)) {
                add(new Outline(role, cell, mapWidth));
            }
        }
    }

    /** The cells drawn now in {@code role}, read off the outlines themselves rather than off what was asked. */
    List<Integer> drawn(MapHighlightContent.Role role) {
        List<Integer> cells = new ArrayList<>();
        for (Gizmo member : shatterfishMembers()) {
            if (member instanceof Outline outline && outline.role() == role) {
                cells.add(outline.cell());
            }
        }
        return cells;
    }

    /** Every block this layer draws, for tests of the shapes (never a filled quad). */
    List<ColorBlock> blocks() {
        List<ColorBlock> blocks = new ArrayList<>();
        for (Gizmo member : shatterfishMembers()) {
            if (member instanceof Outline outline) {
                for (Gizmo block : outline.shatterfishMembers()) {
                    if (block instanceof ColorBlock colorBlock) {
                        blocks.add(colorBlock);
                    }
                }
            }
        }
        return blocks;
    }

    /**
     * Puts the layer in {@code scene} directly behind the terrain. The terrain's group is the first of the
     * scene's members that holds a {@link DungeonTilemap}; the members after it are moved to the front in
     * their own order, which leaves them as they were with this layer slotted in after the terrain. A scene
     * with no terrain (none the game builds) gets the layer on top, as any other member.
     */
    void placeIn(Scene scene) {
        scene.add(this);
        List<Gizmo> members = scene.shatterfishMembers();
        int terrain = -1;
        for (int i = 0; i < members.size() && terrain < 0; i++) {
            if (members.get(i) instanceof Group group && holdsTerrain(group)) {
                terrain = i;
            }
        }
        if (terrain < 0) {
            return;
        }
        for (int i = terrain + 1; i < members.size(); i++) {
            Gizmo member = members.get(i);
            if (member != this) {
                scene.bringToFront(member);
            }
        }
    }

    private static boolean holdsTerrain(Group group) {
        for (Gizmo member : group.shatterfishMembers()) {
            if (member instanceof DungeonTilemap) {
                return true;
            }
        }
        return false;
    }
}
