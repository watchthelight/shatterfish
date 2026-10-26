package org.shatterfish.harness.redetermine;

import org.shatterfish.api.Fog;
import org.shatterfish.harness.observer.FogModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

/**
 * Finds terrain for the cells the screen does not show such that every cell paints the fog the screen painted.
 *
 * <p>The fog of a cell is a function of the terrain of the nine cells around it, and of three flags of those cells
 * that the player can see (in view, visited, mapped) {@link FogModel}. A cell the screen does not show can be solid
 * rock, and it must be one of three solids to keep the cells around it painted as they were: the plain wall (stitched
 * to its neighbours, not a way through), a secret door (stitched, and a way through for the "can this cell be
 * discovered" test), or a barricade (neither stitched nor a wall). Every variable starts as the plain wall, which is
 * what almost all of them need to be, and the search changes as few as it must: it takes the first cell that paints
 * wrongly and tries each change of one variable in its nine, deepening the number of changes allowed one at a time.
 * The order of cells, of variables and of values is fixed by the Observation alone, so the true terrain of an unseen
 * cell never reaches the answer except through the fog that the screen shows of its neighbours.
 */
final class CellSolver {

    static final class Unsolvable extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Unsolvable(String message) {
            super(message);
        }
    }

    private static final int MAX_CHANGES = 24;
    private static final int NODE_CAP = 400_000;

    private final int width;
    private final int length;
    private final int[] terrain;
    private final boolean[] fov;
    private final boolean[] visited;
    private final boolean[] mapped;
    private final Fog[] expected;
    private final int[] varCell;
    private final int[][] domain;
    private final int[] current;
    private final int[][] scope;
    private final int[][] touching;
    private final int[] constraintCell;
    private final boolean[] disc;
    private final boolean[] bad;
    private final TreeSet<Integer> badSet = new TreeSet<>();
    private int nodes;

    CellSolver(int width, int[] terrain, boolean[] fov, boolean[] visited, boolean[] mapped, Fog[] expected,
               int[] varCell, int[][] domain) {
        this.width = width;
        this.length = terrain.length;
        this.terrain = terrain;
        this.fov = fov;
        this.visited = visited;
        this.mapped = mapped;
        this.expected = expected;
        this.varCell = varCell;
        this.domain = domain;
        this.current = new int[varCell.length];
        for (int v = 0; v < varCell.length; v++) {
            current[v] = domain[v][0];
            terrain[varCell[v]] = current[v];
        }
        this.disc = new boolean[length];
        int[] offsets = offsets();
        TreeSet<Integer> constraints = new TreeSet<>();
        for (int cell : varCell) {
            for (int offset : offsets) {
                int c = cell - offset;
                if (c >= 0 && c < length) {
                    constraints.add(c);
                }
            }
        }
        this.constraintCell = constraints.stream().mapToInt(Integer::intValue).toArray();
        int[] indexOfVar = new int[length];
        Arrays.fill(indexOfVar, -1);
        for (int v = 0; v < varCell.length; v++) {
            indexOfVar[varCell[v]] = v;
        }
        this.scope = new int[length][];
        List<List<Integer>> touch = new ArrayList<>();
        for (int v = 0; v < varCell.length; v++) {
            touch.add(new ArrayList<>());
        }
        for (int c : constraintCell) {
            List<Integer> vars = new ArrayList<>();
            for (int offset : offsets) {
                int n = c + offset;
                if (n >= 0 && n < length && indexOfVar[n] >= 0 && !vars.contains(indexOfVar[n])) {
                    vars.add(indexOfVar[n]);
                }
            }
            vars.sort(Integer::compare);
            scope[c] = vars.stream().mapToInt(Integer::intValue).toArray();
            for (int v : vars) {
                touch.get(v).add(c);
            }
        }
        this.touching = new int[varCell.length][];
        for (int v = 0; v < varCell.length; v++) {
            touching[v] = touch.get(v).stream().mapToInt(Integer::intValue).toArray();
        }
        this.bad = new boolean[length];
        for (int c : constraintCell) {
            if (!ok(c)) {
                bad[c] = true;
                badSet.add(c);
            }
        }
    }

    private int[] offsets() {
        return new int[] {-width - 1, -width, -width + 1, -1, 0, 1, width - 1, width, width + 1};
    }

    private boolean ok(int cell) {
        disc[cell] = FogModel.discoverableAt(width, terrain, cell);
        return FogModel.fog(width, length, terrain, disc, fov, visited, mapped, cell) == expected[cell];
    }

    private void set(int v, int value) {
        current[v] = value;
        terrain[varCell[v]] = value;
        for (int c : touching[v]) {
            boolean nowBad = !ok(c);
            if (nowBad != bad[c]) {
                bad[c] = nowBad;
                if (nowBad) {
                    badSet.add(c);
                } else {
                    badSet.remove(c);
                }
            }
        }
    }

    /** Leaves the terrain array holding an assignment under which no constrained cell paints wrongly. */
    void solve() {
        for (int budget = 0; budget <= MAX_CHANGES; budget++) {
            if (search(budget)) {
                return;
            }
        }
        throw new Unsolvable("no terrain for the unseen cells keeps the fog the screen showed within " + MAX_CHANGES
                + " changes of the plain wall (cell " + (badSet.isEmpty() ? -1 : badSet.first()) + " paints wrongly)");
    }

    private boolean search(int budget) {
        if (badSet.isEmpty()) {
            return true;
        }
        if (budget == 0) {
            return false;
        }
        if (++nodes > NODE_CAP) {
            throw new Unsolvable("the search for terrain for the unseen cells passed " + NODE_CAP + " nodes");
        }
        int c = badSet.first();
        for (int v : scope[c]) {
            int old = current[v];
            for (int value : domain[v]) {
                if (value == old) {
                    continue;
                }
                set(v, value);
                if (search(budget - 1)) {
                    return true;
                }
                set(v, old);
            }
        }
        return false;
    }

    /** The cells whose fog differs from the Observation's under the terrain now held, for a test and a message. */
    int badCount() {
        return badSet.size();
    }
}
