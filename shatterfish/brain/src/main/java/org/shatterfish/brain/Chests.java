package org.shatterfish.brain;

import org.shatterfish.api.Action;
import org.shatterfish.api.Codex;
import org.shatterfish.api.HeapKind;
import org.shatterfish.api.HeapView;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.NoteKind;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.Tile;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The chest Policy (issue #181): open what is worth opening, from a calm screen, when the hero can
 * afford what may come out of it.
 *
 * <p>Opening spills the contents onto the heap's cell as a plain heap (Heap.java:108-115), which the
 * pick-up Policy then takes, so this Policy only opens. A click on a chest, skeleton, remains or tomb
 * opens it from any cell beside it (Hero.java:1982-1999, :2478), so it walks to a cell beside the
 * heap and hands over the Step onto it; standing on the heap's cell the screen offers
 * {@link Action.OpenChest}.
 */
final class Chests implements Policy {

    /** The Policy's name, as the Decision records it and {@link Brain#policyNames()} lists it. */
    static final String NAME = "open-chest";

    /** The most wraiths a tomb is opened for, that is free cardinal sides: most of them solid or taken. */
    static final int MAX_WRAITHS = 1;

    /** Tiles a wraith cannot be placed on (Wraith.java:118, {@code Level.solid}); doors count as free, the safe way. */
    private static final Set<Tile> SOLID = EnumSet.of(
            Tile.WALL, Tile.WALL_DECO, Tile.LOCKED_DOOR,
            Tile.CRYSTAL_DOOR, Tile.LOCKED_EXIT, Tile.BARRICADE,
            Tile.BOOKSHELF, Tile.STATUE, Tile.STATUE_SP,
            Tile.REGION_DECO, Tile.REGION_DECO_ALT);

    /** A golden key's title (items.properties:709): it opens a locked chest on its own floor (Hero.java:1218, :2484). */
    static final String GOLDEN_KEY = "golden key";

    /** A crystal key's title (items.properties:712, shown with a capital): it opens a crystal chest (Hero.java:1220, :2490). */
    static final String CRYSTAL_KEY = "crystal key";

    private final Evaluation evaluation;
    private final Codex.Knowledge knowledge;

    Chests(Evaluation evaluation, Codex.Knowledge knowledge) {
        this.evaluation = evaluation;
        this.knowledge = knowledge;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String goal() {
        return "chests: open";
    }

    @Override
    public boolean enters(Observation observation, Memory memory) {
        return Explore.calm(observation, knowledge)
                && observation.map().heaps().stream().anyMatch(heap -> openable(heap, observation, memory));
    }

    /**
     * Whether the game refused the click on this heap twice running on this floor: the Memory then holds its cell
     * blocked (Beliefs.fold), and a click the game keeps refusing spends no time, so it would be handed over for ever.
     */
    private static boolean refused(HeapView heap, Observation observation, Memory memory) {
        return memory.blocked().stream().anyMatch(spot ->
                spot.on(observation.header().depth(), observation.header().branch()) && spot.cell() == heap.cell());
    }

    /** Whether a heap is a kind this Policy opens, and not one the game has refused. */
    static boolean openable(HeapView heap, Observation observation, Memory memory) {
        if (refused(heap, observation, memory)) {
            return false;
        }
        return switch (heap.kind()) {
            case CHEST, SKELETON, REMAINS, TOMB -> !heap.hidden();
            case LOCKED_CHEST -> !heap.hidden() && keyHeld(observation, GOLDEN_KEY);
            case CRYSTAL_CHEST -> !heap.hidden() && keyHeld(observation, CRYSTAL_KEY);
            // An ebony chest is a mimic whatever it holds (EbonyMimic.java:47-71), only seen with a Mimic Tooth.
            case EBONY_CHEST, HEAP, FOR_SALE -> false;
        };
    }

    /**
     * How many wraiths opening a tomb from {@code stand} brings: one on each cardinal neighbour of the hero
     * that is not solid and holds nobody (Heap.java:86, Wraith.java:102-125). A neighbour off the map or
     * out of sight counts as free, since nothing says it is not.
     */
    static int freeSides(Observation observation, int stand) {
        MapSection map = observation.map();
        int width = map.width();
        int x = stand % width;
        int y = stand / width;
        int free = 0;
        int[][] sides = {{0, -1}, {-1, 0}, {1, 0}, {0, 1}};
        for (int[] side : sides) {
            int nx = x + side[0];
            int ny = y + side[1];
            if (nx < 0 || ny < 0 || nx >= width || ny >= map.height()) {
                continue;
            }
            int cell = nx + ny * width;
            boolean taken = observation.actors().actors().stream().anyMatch(actor -> actor.cell() == cell);
            if (!taken && !SOLID.contains(map.tiles().get(cell))) {
                free++;
            }
        }
        return free;
    }

    /**
     * The most damage one hit of a mimic does, by the depth it is on, from general knowledge of the game: a
     * chest that is a mimic bites for {@code 2 + 2 * level} with infinite accuracy when touched
     * (Mimic.java:229-236, :251-257), level the floor's depth. Depth 1 has none (RegularLevel.java:415-421).
     */
    static int mimicBite(int depth, HeapKind kind) {
        if (depth < 2) {
            return 0;
        }
        // A held golden key means the floor made a real locked chest: a golden mimic takes the place of the
        // chest and queues no key (RegularLevel.java:430-437).
        return kind == HeapKind.LOCKED_CHEST ? 0 : 2 + 2 * depth;
    }

    /** The most damage a wraith's hit does on a floor of this depth (Wraith.java:75-83: up to 2 + level). */
    static int wraithHit(int depth) {
        return 2 + depth;
    }

    /**
     * Whether the hero has the health to take the worst opening this heap may be, standing on {@code stand}: a
     * chest's hidden mimic bite and one more hit, a skeleton's or remains' haunting (one wraith, or half the
     * health, Heap.java:96-100) and a tomb's wraiths, two hits each. Whether a chest is a mimic or a heap
     * haunted is not on the screen, so the worst is assumed, and the hero rests or goes on until it can.
     */
    static boolean affordable(HeapView heap, Observation observation, int stand) {
        int hp = observation.hero().hp();
        int depth = observation.header().depth();
        return switch (heap.kind()) {
            case CHEST, LOCKED_CHEST, CRYSTAL_CHEST -> hp > 2 * mimicBite(depth, heap.kind());
            case SKELETON, REMAINS -> hp > 2 * wraithHit(depth);
            case TOMB -> {
                int wraiths = freeSides(observation, stand);
                yield wraiths <= MAX_WRAITHS && hp > wraiths * 2 * wraithHit(depth);
            }
            case EBONY_CHEST, HEAP, FOR_SALE -> false;
        };
    }

    /**
     * Whether a key of this title for this floor is held: collected into the journal's Notes on pickup,
     * never carried in the pack (Key.java:53-69), and good on the main branch only (Hero.java:2484). A
     * chest the game finds no key for spends no turn (Hero.java:1223-1228), so none is tried without.
     */
    static boolean keyHeld(Observation observation, String title) {
        return observation.header().branch() == 0 && observation.journal().notes().stream()
                .anyMatch(note -> note.kind() == NoteKind.KEY
                        && note.depth() == observation.header().depth() && note.title().equalsIgnoreCase(title));
    }

    @Override
    public RunLog.Choice choose(Observation observation, Memory memory, List<Action> offered, Stream stream) {
        MapSection map = observation.map();
        int hero = observation.hero().cell();
        boolean[] walk = Explore.walkable(observation, memory, 0, false);
        int[] distance = Pickup.distances(map, walk, hero);
        HeapView best = null;
        int bestStand = -1;
        long bestNet = 0;
        for (HeapView heap : map.heaps()) {
            if (!openable(heap, observation, memory)) {
                continue;
            }
            int stand = stand(observation, walk, distance, heap);
            if (stand < 0) {
                continue;
            }
            long net = Math.addExact(evaluation.carried(1), evaluation.turns(distance[stand] + 1));
            if (net > bestNet) {
                bestNet = net;
                best = heap;
                bestStand = stand;
            }
        }
        if (best == null) {
            return null;
        }
        String why = "chest " + distance[bestStand];
        if (hero == best.cell()) {
            Action open = new Action.OpenChest(hero);
            return offered.contains(open) ? new RunLog.Choice(open, Policies.CERTAIN, "open: " + why) : null;
        }
        if (bestStand == hero) {
            Action click = new Action.Step(best.cell());
            return offered.contains(click) ? new RunLog.Choice(click, Policies.CERTAIN, "open: " + why) : null;
        }
        Action step = Pickup.firstStep(map, walk, hero, bestStand, offered);
        return step == null ? null : new RunLog.Choice(step, Policies.CERTAIN, "chest: " + distance[bestStand]);
    }

    /**
     * The nearest cell the hero can open {@code heap} from and may: walkable and beside it, or the hero's own
     * cell; for a tomb the nearest where {@link #affordable} holds, since the cell decides the wraiths. -1 for none.
     */
    private static int stand(Observation observation, boolean[] walk, int[] distance, HeapView heap) {
        MapSection map = observation.map();
        int width = map.width();
        int target = heap.cell();
        int best = -1;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int x = target % width + dx;
                int y = target / width + dy;
                if (x < 0 || y < 0 || x >= width || y >= map.height()) {
                    continue;
                }
                int cell = x + y * width;
                if (distance[cell] >= 0 && (walk[cell] || distance[cell] == 0)
                        && (best < 0 || distance[cell] < distance[best])
                        && affordable(heap, observation, cell)
                        && observation.actors().actors().stream().noneMatch(actor -> actor.cell() == target)) {
                    best = cell;
                }
            }
        }
        return best;
    }
}
