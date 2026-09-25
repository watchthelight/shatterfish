package org.shatterfish.brain;

import org.shatterfish.api.Action;
import org.shatterfish.api.Codex;
import org.shatterfish.api.HeapKind;
import org.shatterfish.api.HeapView;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The pick-up Policy (story 4.8, FR-31): go and take an item when what it is worth carried exceeds
 * what the turns to reach and take it cost.
 *
 * <p>What an item is worth is the Evaluation's: every stack carried is worth the {@code item}
 * weight, gold its {@code gold} weight a piece, and an identity whose effect is one of the
 * position's features adds that ({@link Evaluation#identity}). An unidentified item is worth the
 * expectation over what the Beliefs say it may be (story 4.2), never what it is. The cost is the
 * {@code turn} weight times the Steps to the heap plus the turn picking it up takes
 * ({@code Item.TIME_TO_PICK_UP}, Item.java:67, spent at Item.java:130).
 *
 * <p>It enters only on a calm screen, no Prompt and no enemy in view but a passive one (the fight
 * Policy takes the rest). It takes one Step per wait toward the best heap, always one the screen
 * offers, and {@link Action.PickUp} when standing on it. A Step onto a plain heap's cell is itself a
 * pick-up once the hero arrives, unless an enemy is visible (Hero.java:1974-1977); a passive statue
 * or gnoll exile is scenery to {@link Explore#calm} but the game still counts it among the visible
 * enemies, so with one in view the arriving Step does not pick up, a PickUp wait follows, and the
 * cost counts that one more turn.
 *
 * <p>Only plain heaps: a chest, a tomb or a for-sale heap is not an item on the floor. Not a heap a
 * character stands on: the screen offers an Interact or an Attack onto that cell, not a Step
 * (ValidActions.java:171-176). Not a heap the game would not let the hero take: a pick-up the game
 * refuses spends no turn (Hero.java:1162-1188), so the Memory records a heap as refused when the
 * hero stood on it at two waits in a row, the first calm, under the same title (story 4.8's review);
 * nor a dewdrop while the hero is at full health, which is refused unless a waterskin has room
 * (Dewdrop.java:63-69, :120-122), and whether it has is not on the screen. Every Step it takes
 * brings the hero nearer the heap; when the hero has stood still long enough for the explore Policy
 * to yield ({@link Explore#STUCK} - 1 waits), this one yields too.
 */
final class Pickup implements Policy {

    /** The Policy's name, as the Decision records it and {@link Brain#policyNames()} lists it. */
    static final String NAME = "pick-up";

    private final Evaluation evaluation;
    private final Codex.Knowledge knowledge;

    Pickup(Evaluation evaluation, Codex.Knowledge knowledge) {
        this.evaluation = evaluation;
        this.knowledge = knowledge;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String goal() {
        return "items: take";
    }

    /** A dewdrop's title (items.properties): refused at full health unless a waterskin has room. */
    static final String DEWDROP = "dewdrop";

    @Override
    public boolean enters(Observation observation, Memory memory) {
        return Explore.calm(observation)
                && observation.map().heaps().stream().anyMatch(heap -> takeable(heap, observation, memory));
    }

    /**
     * Whether the hero has stood still long enough for this Policy to yield: from
     * {@link Explore#STUCK} - 1 waits on, until it moves, unless it stands on the heap it is going
     * for, where the only Action is the pick-up and a refusal is the Memory's to record.
     */
    static boolean stuck(Memory memory) {
        return memory.streak() >= Explore.STUCK - 1;
    }

    /**
     * Where this Policy's plan goes on {@code observation} under {@code memory}: the heap it targets
     * and the Step it takes toward it, or {@link Memory.Aim#NONE}. The Brain records it in the Memory
     * after each fold, so the next screen can be read against it.
     */
    Memory.Aim aim(Observation observation, Memory memory) {
        if (!enters(observation, memory)) {
            return Memory.Aim.NONE;
        }
        RunLog.Choice choice = choose(observation, memory, observation.actions().actions(), null);
        if (choice == null) {
            return Memory.Aim.NONE;
        }
        if (choice.action() instanceof Action.Step step) {
            return new Memory.Aim(target(observation, memory), step.cell());
        }
        return new Memory.Aim(observation.hero().cell(), -1);
    }

    /** Whether a heap is one this Policy may go for: a plain heap it has not seen refused, nobody on it. */
    static boolean takeable(HeapView heap, Observation observation, Memory memory) {
        if (heap.kind() != HeapKind.HEAP || heap.item().isEmpty()) {
            return false;
        }
        var header = observation.header();
        if (memory.refuses(header.depth(), header.branch(), heap.cell(), heap.item())) {
            return false;
        }
        var hero = observation.hero();
        if (Beliefs.untitled(heap.item()).equals(DEWDROP) && hero.hp() >= hero.ht()) {
            return false;
        }
        return observation.actors().actors().stream().noneMatch(actor -> actor.cell() == heap.cell());
    }

    /**
     * Whether an enemy the game keeps passive is in view: scenery to {@link Explore#calm}, but one of
     * the hero's visible enemies, so arriving on a heap does not pick it up (Hero.java:1974-1977).
     */
    static boolean passiveInView(Observation observation) {
        return observation.actors().actors().stream()
                .anyMatch(actor -> actor.alignment() == org.shatterfish.api.Alignment.ENEMY && Fight.passive(actor));
    }

    /** The cell of the heap this Policy goes for, or -1. */
    int target(Observation observation, Memory memory) {
        HeapView best = best(observation, memory);
        return best == null ? -1 : best.cell();
    }

    private HeapView best(Observation observation, Memory memory) {
        MapSection map = observation.map();
        boolean[] walk = Explore.walkable(observation, memory);
        int[] distance = distances(map, walk, observation.hero().cell());
        HeapView best = null;
        long bestNet = 0;
        for (HeapView heap : map.heaps()) {
            if (!takeable(heap, observation, memory) || heap.cell() >= distance.length
                    || distance[heap.cell()] < 0) {
                continue;
            }
            int turns = distance[heap.cell()] + 1 + (distance[heap.cell()] > 0 && passiveInView(observation) ? 1 : 0);
            long net = Math.addExact(worth(heap.item(), observation), evaluation.turns(turns));
            if (net > bestNet) {
                bestNet = net;
                best = heap;
            }
        }
        return best;
    }

    @Override
    public RunLog.Choice choose(Observation observation, Memory memory, List<Action> offered, Stream stream) {
        MapSection map = observation.map();
        int hero = observation.hero().cell();
        boolean[] walk = Explore.walkable(observation, memory);
        int[] distance = distances(map, walk, hero);
        HeapView best = best(observation, memory);
        if (best == null || (stuck(memory) && best.cell() != hero)) {
            return null;
        }
        String label = Beliefs.untitled(best.item());
        if (best.cell() == hero) {
            Action take = new Action.PickUp();
            return offered.contains(take) ? new RunLog.Choice(take, 10000, "take: " + label) : null;
        }
        Action step = firstStep(map, walk, hero, best.cell(), offered);
        return step == null ? null
                : new RunLog.Choice(step, 10000, "item: " + label + " " + distance[best.cell()]);
    }

    /**
     * What the item a heap shows is worth carried, by its title: gold by the piece, a stack by the
     * {@code item} weight plus what its identity, known or believed, adds to the position.
     */
    long worth(String title, Observation observation) {
        String name = Beliefs.untitled(title);
        int quantity = quantity(title);
        if (name.equals("gold")) {
            return evaluation.gold(quantity);
        }
        long worth = evaluation.carried(1);
        for (Codex.Identities family : knowledge.families()) {
            for (Codex.Candidate candidate : family.candidates()) {
                if (candidate.name().equals(name)) {
                    return Math.addExact(worth, Math.multiplyExact(quantity, evaluation.identity(candidate.className())));
                }
            }
        }
        for (Beliefs.Guess guess : Beliefs.identities(observation, knowledge)) {
            if (guess.label().equals(name)) {
                double expected = 0;
                for (Beliefs.Odds odds : guess.odds()) {
                    expected += odds.probability() * evaluation.identity(classOf(odds.name()));
                }
                return Math.addExact(worth, Math.round(quantity * expected));
            }
        }
        return worth;
    }

    private String classOf(String name) {
        for (Codex.Identities family : knowledge.families()) {
            for (Codex.Candidate candidate : family.candidates()) {
                if (candidate.name().equals(name)) {
                    return candidate.className();
                }
            }
        }
        return "";
    }

    /** The quantity a heap's title shows: " x" and a count for a stack (Item.java:64, :492-493), else one. */
    static int quantity(String title) {
        int space = title.lastIndexOf(' ');
        if (space > 0 && title.length() > space + 2 && title.charAt(space + 1) == 'x'
                && title.substring(space + 2).chars().allMatch(c -> c >= '0' && c <= '9')) {
            return Integer.parseInt(title.substring(space + 2));
        }
        return 1;
    }

    /** Steps from the hero to every cell over walkable cells, -1 where none reaches; the hero's cell is 0. */
    static int[] distances(MapSection map, boolean[] walk, int hero) {
        int[] distance = new int[walk.length];
        Arrays.fill(distance, -1);
        distance[hero] = 0;
        ArrayDeque<Integer> queue = new ArrayDeque<>(List.of(hero));
        int width = map.width();
        while (!queue.isEmpty()) {
            int cell = queue.poll();
            for (int next : neighbours(map, cell)) {
                if (walk[next] && distance[next] < 0) {
                    distance[next] = distance[cell] + 1;
                    queue.add(next);
                }
            }
        }
        return distance;
    }

    /**
     * The offered Step that starts a shortest way from the hero to {@code target}, or null: a Step that
     * brings the hero nearer, never one sideways, so a cell that is walkable but offered no Step (an
     * ally's, which is offered as an Interact) cannot make it wander.
     */
    static Action firstStep(MapSection map, boolean[] walk, int hero, int target, List<Action> offered) {
        int[] fromTarget = distances(map, walk, target);
        Action best = null;
        int bestDistance = fromTarget[hero] < 0 ? Integer.MAX_VALUE : fromTarget[hero];
        for (Action action : offered) {
            if (action instanceof Action.Step step && step.cell() < walk.length && walk[step.cell()]
                    && fromTarget[step.cell()] >= 0 && fromTarget[step.cell()] < bestDistance) {
                bestDistance = fromTarget[step.cell()];
                best = step;
            }
        }
        return best;
    }

    private static List<Integer> neighbours(MapSection map, int cell) {
        int width = map.width();
        int x = cell % width;
        int y = cell / width;
        List<Integer> cells = new ArrayList<>(8);
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx;
                int ny = y + dy;
                if ((dx != 0 || dy != 0) && nx >= 0 && ny >= 0 && nx < width && ny < map.height()) {
                    cells.add(nx + ny * width);
                }
            }
        }
        return cells;
    }
}
