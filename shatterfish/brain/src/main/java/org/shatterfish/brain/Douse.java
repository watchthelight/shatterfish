package org.shatterfish.brain;

import org.shatterfish.api.Action;
import org.shatterfish.api.BlobCell;
import org.shatterfish.api.BuffView;
import org.shatterfish.api.Codex;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.PromptKind;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.Tile;

import java.util.List;
import java.util.Set;

/**
 * Put the fire out, or stop feeding it (issue #219, step 6).
 *
 * <p>Burning lasts eight turns and is renewed to eight by every new ignition (Burning.java:54). It
 * ends early only on an act on water by a character that is not flying (Burning.java:99-100, :178-181),
 * and a burning character on flammable ground seeds a Fire there (Burning.java:171-173), which sets it
 * alight again while it stands in it (Fire.java:54-103). Each turn costs {@code NormalIntRange(1, 3 +
 * depth/4)} (Burning.java:105-108). Hence, while the screen shows the burning buff:
 *
 * <ul>
 *   <li>Water within {@link #REACH} steps: walk there. Fewer than eight turns of burning are left
 *       after the walk, and the first act on water ends them.</li>
 *   <li>Else, standing on flammable ground (grass, high grass, a door: Terrain.java:86-103): walk to
 *       the nearest cell that is not flammable and has no fire on it, within {@link #FLEE}. The fire
 *       the hero seeds can no longer renew it there.</li>
 *   <li>Else nothing: the Policy stands aside, and burning runs its eight turns wherever the hero
 *       stands.</li>
 * </ul>
 *
 * <p>A levitating hero ("levitating", SafeTest) is not put out by water ({@code !target.flying},
 * Burning.java:99, :179), so for it the Policy skips the walk to water and only leaves flammable ground. The
 * sewer's decoration floor is flammable too (Level.java:1003-1006), but it is solid (Terrain.java:121-125,
 * the statue's flags) and never walkable, so no hero stands on it.
 *
 * <p>With an enemy beside the hero the fight Policy goes first: a step away is a free hit for it.
 * Every Step brings the hero strictly nearer its target, so the Policy cannot stand still or circle.
 */
final class Douse implements Policy {

    /** The Policy's name, as the Decision records it and {@link Brain#policyNames()} lists it. */
    static final String NAME = "douse";

    /** The buff's name on the screen (actors.properties:137). */
    static final String BURNING = TestItem.BURNING;

    /** The steps to water worth taking: a walk of w leaves 8 - w turns of burning to save (Burning.java:54). */
    static final int REACH = 5;

    /** The steps to bare ground worth taking, from flammable ground. */
    static final int FLEE = 6;

    /** The ground that burns, by the tile the screen draws (Terrain.java:86-103). */
    static final Set<Tile> FLAMMABLE = Set.of(Tile.GRASS, Tile.HIGH_GRASS, Tile.FURROWED_GRASS, Tile.DOOR,
            Tile.OPEN_DOOR);

    private final Codex.Knowledge knowledge;

    Douse(Codex.Knowledge knowledge) {
        this.knowledge = knowledge;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String goal() {
        return "douse: burning";
    }

    /** Whether the screen shows the hero burning. */
    static boolean burning(Observation observation) {
        for (BuffView view : observation.hero().buffs()) {
            if (view.name().equals(BURNING)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the screen shows the hero levitating, which water does not put out. */
    static boolean levitating(Observation observation) {
        for (BuffView view : observation.hero().buffs()) {
            if (view.name().equals(SafeTest.LEVITATING)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean enters(Observation observation, Memory memory) {
        return observation.header().prompt() == PromptKind.NONE && burning(observation)
                && observation.map().tiles().get(observation.hero().cell()) != Tile.WATER
                && !beside(observation);
    }

    /** Whether an enemy stands beside the hero. */
    private boolean beside(Observation observation) {
        int nearest = Fight.nearest(observation.map(), observation.hero().cell(), Fight.enemies(observation, knowledge));
        return nearest >= 0 && nearest <= 1;
    }

    @Override
    public RunLog.Choice choose(Observation observation, Memory memory, List<Action> offered, Stream stream) {
        MapSection map = observation.map();
        int hero = observation.hero().cell();
        boolean[] walk = Explore.walkable(observation, memory, true, true);
        int[] distance = Pickup.distances(map, walk, hero);
        int target = levitating(observation) ? -1
                : nearest(distance, cell -> map.tiles().get(cell) == Tile.WATER, REACH);
        String why = "douse: water";
        if (target < 0 && FLAMMABLE.contains(map.tiles().get(hero))) {
            Set<Integer> burning = fires(map);
            Set<Integer> ignited = Explore.ignited(map);
            target = nearest(distance, cell -> !FLAMMABLE.contains(map.tiles().get(cell)) && !burning.contains(cell)
                    && !ignited.contains(cell), FLEE);
            why = "douse: bare ground";
        }
        if (target < 0) {
            return null;
        }
        Action step = Pickup.firstStep(map, walk, hero, target, offered);
        return step == null ? null : new RunLog.Choice(step, Policies.CERTAIN, why);
    }

    /** The cells the screen shows a harmful blob on. */
    private static Set<Integer> fires(MapSection map) {
        Set<Integer> cells = new java.util.HashSet<>();
        for (BlobCell blob : map.blobs()) {
            if (blob.kinds().stream().anyMatch(Explore.HARMFUL::contains)) {
                cells.add(blob.cell());
            }
        }
        return cells;
    }

    private static int nearest(int[] distance, java.util.function.IntPredicate fits, int reach) {
        int best = -1;
        for (int cell = 0; cell < distance.length; cell++) {
            if (distance[cell] >= 1 && distance[cell] <= reach && fits.test(cell)
                    && (best < 0 || distance[cell] < distance[best])) {
                best = cell;
            }
        }
        return best;
    }
}
