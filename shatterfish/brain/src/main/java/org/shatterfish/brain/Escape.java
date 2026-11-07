package org.shatterfish.brain;

import org.shatterfish.api.Action;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.BlobCell;
import org.shatterfish.api.Codex;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.Playbook;
import org.shatterfish.api.PromptKind;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.Tile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.IntPredicate;

/**
 * Leave a harmful cloud (issue #408).
 *
 * <p>The Policy enters when the hero's own cell shows fire or a harmful gas ({@link Explore#HARMFUL}),
 * or a flame of the magical fire room's reach. It ranks above the fight and heal Policies: the cloud
 * hurts (or, for the paralytic gas, disables) every turn the hero stays in it, whoever is beside the
 * hero. A step away from an adjacent enemy can cost a blow when the enemy is still beside the hero
 * afterwards or moves faster; the finishing rule below weighs that, and otherwise the cloud is the worse of the two.
 *
 * <p>The route is a search that may cross clouded cells, which the other Policies' plans never do
 * ({@link Explore#walkable}): every Step costs one turn, every clouded cell entered costs what a turn
 * in it is expected to cost ({@link #cost}), the dearest first, so the way out crosses the fewest and
 * mildest clouded cells and, among equals, the fewest Steps. Its target is the nearest cell clear of
 * every shown cloud and every remembered one ({@link Memory#clouds}) with no cloud beside it, since a
 * cloud spreads to its neighbours (Blob.java:158-186); failing that, any clear cell. A cloud the screen
 * shows is a cloud the hero sees: the Policy reads nothing off the screen it does not draw.
 *
 * <p>The one exception is an adjacent enemy that a single landed hit kills ({@link Fight#dies}): the
 * hero kills it first, since it would otherwise follow the hero out and the turn spent in the cloud is
 * one turn either way. Never in the paralytic gas, where the turn in the cloud is the one that decides
 * whether the hero acts again.
 *
 * <p>The test-item Policy calls the same route for its own escape after a test ({@link #step}).
 */
final class Escape implements Policy {

    /** The Policy's name, as the Decision records it and {@link Brain#policyNames()} lists it. */
    static final String NAME = "escape";

    /** The paralytic gas, by the class name the Observation carries. */
    static final String PARALYTIC = "ParalyticGas";

    private final Codex.Knowledge knowledge;
    private final Playbook playbook;

    Escape(Codex.Knowledge knowledge, Playbook playbook) {
        this.knowledge = knowledge;
        this.playbook = playbook;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String goal() {
        return "escape: harm";
    }

    @Override
    public boolean enters(Observation observation, Memory memory) {
        return observation.header().prompt() == PromptKind.NONE && !cloud(observation).isEmpty();
    }

    @Override
    public RunLog.Choice choose(Observation observation, Memory memory, List<Action> offered, Stream stream) {
        List<RunLog.Choice> ranked = ranked(observation, memory, offered, stream);
        return ranked.isEmpty() ? null : ranked.get(0);
    }

    @Override
    public List<RunLog.Choice> ranked(Observation observation, Memory memory, List<Action> offered, Stream stream) {
        String kind = cloud(observation);
        List<RunLog.Choice> ranked = new ArrayList<>();
        ActorView finish = PARALYTIC.equals(kind) ? null : finish(observation, offered);
        if (finish != null) {
            ranked.add(new RunLog.Choice(new Action.Attack(finish.cell()), Policies.CERTAIN, "finish: " + finish.name()));
        }
        Playbook.Identify identify = playbook.identify(observation.header().heroClass());
        Action step = step(observation, memory, offered, identify);
        if (step != null) {
            ranked.add(new RunLog.Choice(step, ranked.isEmpty() ? Policies.CERTAIN : 0, "escape: " + kind));
        }
        return ranked;
    }

    /** The adjacent, attackable enemy that one landed hit kills, the soonest-killed first, or null. */
    private ActorView finish(Observation observation, List<Action> offered) {
        ActorView best = null;
        for (ActorView enemy : Fight.enemies(observation, knowledge)) {
            if (offered.contains(new Action.Attack(enemy.cell())) && Fight.dies(observation, knowledge, enemy)
                    && (best == null || enemy.cell() < best.cell())) {
                best = enemy;
            }
        }
        return best;
    }

    /**
     * The kind of cloud the hero must leave, as the Observation names it, or empty: a harmful blob drawn on
     * its own cell (the worst of them), a flame of the magical fire room beside it, or a paralytic gas drawn
     * on a cell beside it.
     *
     * <p>The paralytic gas cannot be walked out of once it holds the hero. It lands at the end of the turn
     * the hero stands on a gas cell and is renewed to ten turns every turn after, so the hero acts again only
     * ten turns after the gas has left its cell (ParalyticGas.java:52, Paralysis.java:34, Hero.java:873-878),
     * and the Brain is never asked in between. The gas spreads one cell a turn, to the four orthogonal
     * neighbours only (Blob.java:166-186), so the only turn that can be used is the one before: the Policy
     * enters when the gas is drawn on a cell orthogonally beside the hero, and steps away from it.
     */
    static String cloud(Observation observation) {
        String here = TestItem.harmfulOn(observation.map(), observation.hero().cell());
        return here.isEmpty() && besideParalytic(observation.map(), observation.hero().cell()) ? PARALYTIC : here;
    }

    /** The cells with a paralytic gas drawn on them or on one of their four orthogonal neighbours. */
    static boolean[] paralyticZone(MapSection map) {
        int width = map.width();
        int size = map.tiles().size();
        boolean[] zone = new boolean[size];
        for (BlobCell blob : map.blobs()) {
            if (!blob.kinds().contains(PARALYTIC) || blob.cell() >= size) {
                continue;
            }
            int cell = blob.cell();
            zone[cell] = true;
            if (cell % width > 0) {
                zone[cell - 1] = true;
            }
            if (cell % width < width - 1) {
                zone[cell + 1] = true;
            }
            if (cell >= width) {
                zone[cell - width] = true;
            }
            if (cell + width < size) {
                zone[cell + width] = true;
            }
        }
        return zone;
    }

    /** Whether a paralytic gas is drawn on {@code cell} or on one of its four orthogonal neighbours. */
    static boolean besideParalytic(MapSection map, int cell) {
        int width = map.width();
        for (BlobCell blob : map.blobs()) {
            if (!blob.kinds().contains(PARALYTIC)) {
                continue;
            }
            int other = blob.cell();
            if (other == cell || other == cell - width || other == cell + width
                    || (other == cell - 1 && other / width == cell / width)
                    || (other == cell + 1 && other / width == cell / width)) {
                return true;
            }
        }
        return false;
    }

    /**
     * What a turn on a cell showing {@code kind} is expected to cost the hero, in hit points, on
     * {@code depth}; a cloud of no known kind (a remembered one) costs as a toxic gas.
     */
    static int cost(String kind, int depth) {
        return switch (kind) {
            // A turn on a paralytic gas cell costs ten turns of helplessness, and the cell beside one is
            // that cell a turn later: worth more than any health the hero has, so the route crosses as few
            // of them as there are.
            case PARALYTIC -> 1000;
            // Corrosion starts at the gas's strength, rises each tick, and is renewed to two ticks every turn
            // in the gas (CorrosiveGas.java:58, Corrosion.java:73, :106-115). The figure is an estimate.
            case "CorrosiveGas" -> 2 + depth / 4;
            // A flame sets the hero alight: up to eight ticks of 1 to 3 + depth/4 (Burning.java:54, :105-108,
            // Fire.java:100-102), which is 16 and more for the hero that does not reach water.
            case "Fire", Explore.ETERNAL_FIRE -> 8 * (2 + depth / 8);
            // Toxic gas: 1 + depth/5 a turn to everyone inside (ToxicGas.java:40, :48-51).
            default -> 1 + depth / 5;
        };
    }

    /** The cost of a turn on each cell, by the worst cloud drawn there or remembered there; 0 where none. */
    private static int[] costs(Observation observation, Memory memory) {
        MapSection map = observation.map();
        int[] costs = new int[map.tiles().size()];
        int depth = observation.header().depth();
        int branch = observation.header().branch();
        for (Memory.Cloud cloud : memory.clouds()) {
            if (cloud.depth() == depth && cloud.branch() == branch && cloud.cell() < costs.length
                    && cloud.until() >= memory.waits()) {
                costs[cloud.cell()] = Math.max(costs[cloud.cell()], cost("ToxicGas", depth));
            }
        }
        for (int cell : Explore.ignited(map)) {
            costs[cell] = Math.max(costs[cell], cost(Explore.ETERNAL_FIRE, depth));
        }
        for (BlobCell blob : map.blobs()) {
            for (String kind : blob.kinds()) {
                if (Explore.HARMFUL.contains(kind) && blob.cell() < costs.length) {
                    costs[blob.cell()] = Math.max(costs[blob.cell()], cost(kind, depth));
                }
            }
        }
        boolean[] zone = paralyticZone(map);
        for (int cell = 0; cell < costs.length; cell++) {
            if (zone[cell]) {
                costs[cell] = Math.max(costs[cell], cost(PARALYTIC, depth));
            }
        }
        return costs;
    }

    /**
     * The offered Step out of harm: along the route of least expected damage, then fewest Steps, to the
     * nearest cell clear of every cloud and of the edge of one; or, while burning, to the nearest water
     * within the Playbook's {@code reach}, which puts the burning out (Burning.java:99); or, after a
     * test, to the door the test was credited with. Null when no Step leads out.
     */
    static Action step(Observation observation, Memory memory, List<Action> offered, Playbook.Identify identify) {
        // The weight of a Step against hit points: a turn counts as less than one point of damage.
        final long turn = 1;
        final long point = 1000;
        MapSection map = observation.map();
        int hero = observation.hero().cell();
        // Through the cloud: a hero in the middle of one has no clean neighbour to start from.
        boolean[] walk = Explore.walkable(observation, memory, false, true);
        int[] costs = costs(observation, memory);
        int size = walk.length;
        long[] best = new long[size];
        int[] first = new int[size];
        Arrays.fill(best, Long.MAX_VALUE);
        Arrays.fill(first, -1);
        int[] steps = new int[size];
        PriorityQueue<long[]> queue = new PriorityQueue<>((a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0]) : Long.compare(a[1], b[1]));
        for (Action action : offered) {
            if (action instanceof Action.Step step && step.cell() < size && walk[step.cell()]) {
                long cost = costs[step.cell()] * point + turn;
                if (cost < best[step.cell()]) {
                    best[step.cell()] = cost;
                    first[step.cell()] = step.cell();
                    steps[step.cell()] = 1;
                    queue.add(new long[] {cost, step.cell()});
                }
            }
        }
        while (!queue.isEmpty()) {
            long[] head = queue.poll();
            int cell = (int) head[1];
            if (head[0] > best[cell]) {
                continue;
            }
            for (int next : Pickup.neighbours(map, cell)) {
                if (!walk[next] || next == hero) {
                    continue;
                }
                long cost = head[0] + costs[next] * point + turn;
                if (cost < best[next]) {
                    best[next] = cost;
                    first[next] = first[cell];
                    steps[next] = steps[cell] + 1;
                    queue.add(new long[] {cost, next});
                }
            }
        }
        boolean burning = TestItem.has(observation, TestItem.BURNING);
        IntPredicate clean = TestItem.clean(observation, memory);
        int target = -1;
        // Through the door the test was credited with, when there was one and it can be reached.
        int refuge = memory.refuge();
        if (!burning && refuge >= 0 && refuge < size && first[refuge] >= 0 && clean.test(refuge)) {
            target = refuge;
        }
        if (target < 0 && burning) {
            target = cheapest(best, steps, cell -> map.tiles().get(cell) == Tile.WATER && clean.test(cell), identify.reach());
        }
        if (target < 0) {
            // Clear of the cloud's edge, which spreads: a clean cell with no cloud beside it, which
            // through a door is the far side of it.
            target = cheapest(best, steps, cell -> clean.test(cell) && TestItem.clear(map, cell, clean), Integer.MAX_VALUE);
        }
        if (target < 0) {
            target = cheapest(best, steps, clean, Integer.MAX_VALUE);
        }
        if (target < 0 || target == hero) {
            return null;
        }
        Action step = new Action.Step(first[target]);
        return offered.contains(step) ? step : null;
    }

    /** The reached cell that fits and is cheapest to reach within {@code reach} Steps, ties to the lower cell; -1 if none. */
    private static int cheapest(long[] best, int[] steps, IntPredicate fits, int reach) {
        int pick = -1;
        for (int cell = 0; cell < best.length; cell++) {
            if (best[cell] != Long.MAX_VALUE && steps[cell] <= reach && fits.test(cell)
                    && (pick < 0 || best[cell] < best[pick])) {
                pick = cell;
            }
        }
        return pick;
    }
}
