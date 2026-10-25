package org.shatterfish.overlay;

import com.watabou.noosa.ColorBlock;
import com.watabou.noosa.Group;

import java.util.ArrayList;
import java.util.List;

/**
 * The frame around the whole game view that says this Run is an oracle Run (story 5.12, FR-43, UX-DR11):
 * four of the game's own {@code ColorBlock}s, 2 UI px thick, in the oracle colour, on the UI camera so it
 * follows the window whatever the Panel does to the world camera.
 *
 * <p>It exists only in an oracle Run (the dock makes it only then) and is kept the last member of the
 * play scene, in front of the Panel, the game's windows and its fade from black ({@code DESIGN.md}: "the
 * oracle border is drawn above everything"). It has no input of its own and no setter: a Run is an oracle
 * Run for as long as it lasts.
 */
final class OracleBorder extends Group {

    /** The frame's thickness in UI pixels ({@code DESIGN.md} Oracle border). */
    static final float EDGE = 2;

    private final List<ColorBlock> edges = new ArrayList<>();
    private float width = -1;
    private float height = -1;

    OracleBorder() {
        int color = 0xFF000000 | Panel.ORACLE_COLOR;
        for (int i = 0; i < 4; i++) {
            ColorBlock edge = new ColorBlock(1, 1, color);
            edges.add(edge);
            add(edge);
        }
    }

    /** Fits the frame to a view {@code width} by {@code height} UI pixels; the same size again changes nothing. */
    void place(float width, float height) {
        if (width == this.width && height == this.height) {
            return;
        }
        this.width = width;
        this.height = height;
        edges.get(0).size(width, EDGE);
        edges.get(1).size(width, EDGE);
        edges.get(2).size(EDGE, Math.max(0, height - 2 * EDGE));
        edges.get(3).size(EDGE, Math.max(0, height - 2 * EDGE));
        edges.get(0).x = 0;
        edges.get(0).y = 0;
        edges.get(1).x = 0;
        edges.get(1).y = height - EDGE;
        edges.get(2).x = 0;
        edges.get(2).y = EDGE;
        edges.get(3).x = width - EDGE;
        edges.get(3).y = EDGE;
    }

    /** The four edges: top, bottom, left, right. */
    List<ColorBlock> edges() {
        return edges;
    }

    /** The colour the edges are built in, as 24-bit RGB. */
    int rgb() {
        return Panel.ORACLE_COLOR;
    }
}
