package org.shatterfish.overlay;

import com.watabou.noosa.ColorBlock;
import com.watabou.noosa.Gizmo;
import com.watabou.noosa.Group;
import com.watabou.noosa.Scene;

import java.util.ArrayList;
import java.util.List;

/**
 * The enemies the screen does not show, outlined on the dungeon in the oracle colour (story 5.12, FR-43,
 * UX-DR11). Oracle Runs only: the dock makes this layer only for one, and hands it cells only from
 * {@link OracleMarkContent}.
 *
 * <p>Native like {@link MapHighlights}: the game's own {@code ColorBlock}s on the world camera, edges and
 * never a filled quad. The shape differs from the Decision's outlines as well as the colour (UX-DR14): a
 * double outline, a 1 px frame and another one inside it. Unlike them it sits in front of the fog of war,
 * since an unseen enemy stands in the fog, which a layer behind it would never show; it is still behind
 * everything the game draws after the fog (spells, effects over fog, health indicators, the HUD, the Panel).
 */
final class OracleOutlines extends Group {

    static final float EDGE = 1;
    /** The gap between the outer frame and the inner one. */
    static final float INSET = 2;
    /** The class name of the game's fog of war, found by name as the dock finds the fader. */
    static final String FOG = "com.shatteredpixel.shatteredpixeldungeon.tiles.FogOfWar";

    /** One outlined cell and the blocks that draw it. */
    static final class Outline extends Group {
        private final int cell;

        Outline(int cell, int mapWidth) {
            this.cell = cell;
            float x = (cell % mapWidth) * MapHighlights.CELL;
            float y = (cell / mapWidth) * MapHighlights.CELL;
            frame(x, y, 0);
            frame(x, y, INSET);
        }

        private void frame(float x, float y, float inset) {
            float side = MapHighlights.CELL - 2 * inset;
            block(x + inset, y + inset, side, EDGE);
            block(x + inset, y + inset + side - EDGE, side, EDGE);
            block(x + inset, y + inset + EDGE, EDGE, side - 2 * EDGE);
            block(x + inset + side - EDGE, y + inset + EDGE, EDGE, side - 2 * EDGE);
        }

        private void block(float x, float y, float width, float height) {
            ColorBlock block = new ColorBlock(width, height, 0xFF000000 | Panel.ORACLE_COLOR);
            block.x = x;
            block.y = y;
            add(block);
        }

        int cell() {
            return cell;
        }
    }

    private List<Integer> shown = List.of();
    private int shownWidth;

    /** Outlines {@code cells} on a map {@code mapWidth} wide, replacing what was outlined; the same cells change nothing. */
    void show(List<Integer> cells, int mapWidth) {
        if (cells.equals(shown) && (cells.isEmpty() || mapWidth == shownWidth)) {
            return;
        }
        clear();
        shown = List.copyOf(cells);
        shownWidth = mapWidth;
        if (mapWidth <= 0) {
            return;
        }
        for (int cell : cells) {
            add(new Outline(cell, mapWidth));
        }
    }

    /** The cells drawn now, read off the outlines themselves. */
    List<Integer> drawn() {
        List<Integer> cells = new ArrayList<>();
        for (Gizmo member : shatterfishMembers()) {
            if (member instanceof Outline outline) {
                cells.add(outline.cell());
            }
        }
        return cells;
    }

    /** Every block this layer draws. */
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

    /** The colour the outlines are built in, as 24-bit RGB. */
    int rgb() {
        return Panel.ORACLE_COLOR;
    }

    /**
     * Puts the layer in {@code scene} directly in front of the fog of war: added, then every member that was
     * after the fog moved to the front in its own order, which leaves this layer right behind them. A scene
     * with no fog gets it on top.
     */
    void placeIn(Scene scene) {
        scene.add(this);
        List<Gizmo> members = scene.shatterfishMembers();
        int fog = -1;
        for (int i = 0; i < members.size() && fog < 0; i++) {
            if (members.get(i) != null && members.get(i).getClass().getName().equals(FOG)) {
                fog = i;
            }
        }
        if (fog < 0) {
            return;
        }
        for (int i = fog + 1; i < members.size(); i++) {
            Gizmo member = members.get(i);
            if (member != this) {
                scene.bringToFront(member);
            }
        }
    }
}
