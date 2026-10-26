package org.shatterfish.brain;

import org.shatterfish.api.Action;
import org.shatterfish.api.Codex;
import org.shatterfish.api.Hunger;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.Tile;

import java.util.List;

/**
 * Jump into a chasm when the floor has no way down the hero can walk (issue #219, step 6).
 *
 * <p><b>Why.</b> Of 40 Warriors on the tuning set, four spent the rest of their Run on one floor with
 * no Policy left to choose (the fallback drew Actions at random for 33 to 269 waits), and three of them
 * starved there: the exit was never found, and each floor held a chasm the explore Policy walks around.
 * Hunger rises a point a turn (Hunger.java:88-95) and a starving hero regenerates nothing
 * (Regeneration.java:56), so every turn spent on a floor with no way down is a turn of food. A jump is
 * a way down: a Step onto a chasm cell asks whether to jump (Hero.java:1881-1893), and the fall lands
 * the hero on the floor below (Chasm.java:99-121).
 *
 * <p><b>The cost.</b> The landing cripples the hero, makes it bleed, and takes at least half of its
 * current hit points (Chasm.java:131-152), so the Policy jumps only when the hit points can bear it
 * ({@link #fit}), and a hero that is not starving rests first, since a rest then buys the hit points
 * the jump costs.
 *
 * <p><b>When.</b> Below the Policies that find a way down by walking, above the fallback: it acts only
 * on a screen where the explore Policy has nothing left to uncover ({@link Explore#spent}) and no open
 * exit is reachable. It walks to the nearest cell beside a chasm cell the screen shows, then steps onto
 * the chasm, which the Brain's answer to the jump question presses "yes" to because this Step was meant
 * ({@link Answers#jumpMeant}). It reads only the Observation, the Memory and the Codex.
 */
final class Plunge implements Policy {

    /** The Policy's name, as the Decision records it and {@link Brain#policyNames()} lists it. */
    static final String NAME = "plunge";

    /** The hit points below which the fall's bleeding may kill (Chasm.java:131-152). */
    static final int SAFE_HP = 15;

    /** The share of its hit points, in percent, a hero that can still regenerate has before it jumps. */
    static final int FIT_PERCENT = 80;

    /** The share of its hit points, in percent, a starving hero needs to jump: it will not regenerate. */
    static final int STARVING_PERCENT = 50;

    private final Codex.Knowledge knowledge;

    Plunge(Codex.Knowledge knowledge) {
        this.knowledge = knowledge;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String goal() {
        return "descend: chasm";
    }

    @Override
    public boolean enters(Observation observation, Memory memory) {
        return Calm.quiet(observation, knowledge) && !observation.header().sealed()
                && observation.header().branch() == 0 && chasm(observation.map())
                && Explore.spent(observation, memory);
    }

    @Override
    public RunLog.Choice choose(Observation observation, Memory memory, List<Action> offered, Stream stream) {
        if (memory.streak() == Explore.STUCK - 1) {
            return null;
        }
        MapSection map = observation.map();
        int hero = observation.hero().cell();
        // A way down on foot comes first; the descend Policy has already had its turn, so this is the
        // screen where it found none (an exit it cannot reach counts as none).
        if (!fit(observation)) {
            return restFirst(observation, offered);
        }
        for (Action action : offered) {
            if (action instanceof Action.Step step && isChasm(map, step.cell())) {
                return new RunLog.Choice(step, Policies.CERTAIN, "jump " + step.cell());
            }
        }
        Explore.Path path = Explore.nearest(map, Explore.walkable(observation, memory, true), hero, offered,
                cell -> besideChasm(map, cell));
        if (path == null) {
            path = Explore.nearest(map, Explore.walkable(observation, memory, false), hero, offered,
                    cell -> besideChasm(map, cell));
        }
        return path == null ? null : new RunLog.Choice(path.step(), Policies.CERTAIN, "to-chasm " + path.distance());
    }

    /** Whether the hero's hit points can bear the fall: see the class comment. */
    static boolean fit(Observation observation) {
        int hp = observation.hero().hp();
        int ht = observation.hero().ht();
        if (hp < Math.min(SAFE_HP, ht)) {
            return false;
        }
        int percent = observation.hero().hunger() == Hunger.STARVING ? STARVING_PERCENT : FIT_PERCENT;
        return hp * 100 >= ht * percent;
    }

    /** A rest toward the hit points the jump needs, or null when the hero cannot regenerate or none is offered. */
    private static RunLog.Choice restFirst(Observation observation, List<Action> offered) {
        if (observation.hero().hunger() == Hunger.STARVING) {
            return null;
        }
        for (Action rest : List.of(new Action.Rest(true), new Action.Rest(false), new Action.Search())) {
            if (offered.contains(rest)) {
                return new RunLog.Choice(rest, Policies.CERTAIN, "rest: chasm");
            }
        }
        return null;
    }

    /** Whether the screen shows a chasm cell at all. */
    static boolean chasm(MapSection map) {
        return map.tiles().contains(Tile.CHASM);
    }

    private static boolean isChasm(MapSection map, int cell) {
        return cell >= 0 && cell < map.tiles().size() && map.tiles().get(cell) == Tile.CHASM;
    }

    /** Whether one of the cell's eight neighbours is a chasm cell. */
    private static boolean besideChasm(MapSection map, int cell) {
        int width = map.width();
        int x = cell % width;
        int y = cell / width;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx;
                int ny = y + dy;
                if ((dx != 0 || dy != 0) && nx >= 0 && ny >= 0 && nx < width && ny < map.height()
                        && isChasm(map, nx + ny * width)) {
                    return true;
                }
            }
        }
        return false;
    }
}
