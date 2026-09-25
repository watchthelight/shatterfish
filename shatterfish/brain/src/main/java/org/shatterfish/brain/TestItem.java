package org.shatterfish.brain;

import org.shatterfish.api.Action;
import org.shatterfish.api.BlobCell;
import org.shatterfish.api.BuffView;
import org.shatterfish.api.Codex;
import org.shatterfish.api.ItemKind;
import org.shatterfish.api.ItemRef;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.Tile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The test-item Policy (story 4.10, FR-31, FR-30): learn what an unidentified potion or scroll is by
 * drinking or reading it, where the worst it can do is survivable.
 *
 * <p>It enters only on a calm screen, no Prompt and no enemy in view but a passive one, so it never
 * tests during a fight; and it ranks first among the calm-screen Policies -- above pick-up, equip and
 * explore -- so it tests at the first calm moment after the item is held, which is as early on the
 * floor as the item allows, and its escape from its own fire or gas comes before anything else. Of the appearances
 * held, it tries first the one worth most to know: the most copies held (a test identifies them
 * all) times the candidates left beyond one. An appearance with a single candidate left is known by
 * elimination and not tried. A potion is drunk only while the hero is down to four fifths of its hit
 * points or below ({@link #hurt}): the likeliest unknown potion is healing, whose drink at full
 * health is wasted, and every other identity costs the same hurt or whole.
 *
 * <p>Every test is one {@link SafeTest} calls safe at the cell it happens on. A potion's worst case
 * depends on the cell -- water shortens liquid flame's burn, a closed door beside it toxic gas's
 * cloud -- so when a cell within {@link #REACH} Steps scores a strictly smaller worst case than the
 * hero's own, it walks there first, one offered Step a wait, and drinks there. A scroll's worst case
 * does not depend on the cell and is read where the hero stands.
 *
 * <p>A scroll is read plainly, never onto another item (story 4.10, design D3): an unknown
 * inventory scroll identifies itself and leaves the pack before its item picker opens
 * (InventoryScroll.java:39-49), the executor sends the picker away, and the scroll asks to confirm
 * the cancel (InventoryScroll.java:137-139, :52-80), which answer-prompt affirms. Read onto an item,
 * a scroll of upgrade would open the upgrade window (ScrollOfUpgrade.java:60-66), which no Action
 * answers. It does not read while the screen shows the hero blinded or immune to magic, which the
 * game refuses without spending a turn (Scroll.java:172-192); a refusal the screen does not show
 * beforehand is the Memory's to record ({@link Memory#balked}), and the appearance is not tried
 * again on that floor.
 *
 * <p>After a test, it carries out the escape {@code SafeTest}'s smaller figures assume: for
 * {@link #ESCAPE_WAITS} waits, while the hero's own cell shows a harmful blob, or the hero shows
 * "burning", it Steps toward the nearest cell with none -- the nearest water while burning. No
 * calm-screen Policy ranks above it, so nothing walks the hero deeper into the cloud first.
 */
final class TestItem implements Policy {

    /** The Policy's name, as the Decision records it and {@link Brain#policyNames()} lists it. */
    static final String NAME = "test-item";

    /** The item actions that test: a potion's drink (Potion.java:84), a scroll's read (Scroll.java). */
    static final String DRINK = "DRINK";
    static final String READ = "READ";

    /** The most Steps it walks to a better testing cell. */
    static final int REACH = 8;

    /**
     * The most Steps in a row it walks toward testing cells before it balks at the appearance: twice
     * {@link #REACH}, which a walk to a cell that stays put never needs.
     */
    static final int WALKS = 2 * REACH;

    /** The buffs a read is refused under, as the screen names them (actors.properties:131, :305). */
    static final String BLINDED = "blinded";
    static final String MAGIC_IMMUNE = "immune to magic";

    /** The burning buff, as the screen names it (actors.properties:137). */
    static final String BURNING = "burning";

    /**
     * The blobs whose cells the escape leaves, by the class names the Observation carries: the fire
     * and the gases a tested potion can seed (Potion.java:332-334 and the harmful potions' shatter).
     */
    static final Set<String> HARMFUL = Explore.HARMFUL;

    /**
     * How many waits after a test it steps out of fire or gas: the toxic cloud's modelled
     * {@link SafeTest#GAS_TURNS} and two. A cloud it did not make is not its to leave: seen from
     * outside a shut door a gassed room shows no gas at all (a closed door is solid and draws what
     * is behind it unseen), so a Policy that stepped out of every cloud would step out of the doorway,
     * see none, and let explore walk back in, for as long as the gas lasts.
     */
    static final int ESCAPE_WAITS = SafeTest.GAS_TURNS + 2;

    private final Codex.Knowledge knowledge;

    TestItem(Codex.Knowledge knowledge) {
        this.knowledge = knowledge;
    }

    /**
     * What the Policy would do on a screen: the appearance it acts for (empty for an escape), how many
     * of it the pack holds, and the Choice.
     */
    record Plan(String label, int quantity, RunLog.Choice choice) {
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String goal() {
        return "items: identify";
    }

    @Override
    public boolean enters(Observation observation, Memory memory) {
        return Explore.calm(observation) && (escaping(observation, memory) || !testable(observation, memory).isEmpty());
    }

    @Override
    public RunLog.Choice choose(Observation observation, Memory memory, List<Action> offered, Stream stream) {
        Plan plan = plan(observation, memory, offered);
        return plan == null ? null : plan.choice();
    }

    /** The plan on this screen, or null: the same one {@link #choose} takes, which the Brain records. */
    Plan plan(Observation observation, Memory memory, List<Action> offered) {
        if (!Explore.calm(observation)) {
            return null;
        }
        MapSection map = observation.map();
        int hero = observation.hero().cell();
        if (escaping(observation, memory)) {
            Action step = escape(observation, memory, offered);
            if (step != null) {
                return new Plan("", 0, new RunLog.Choice(step, Policies.CERTAIN, "escape: " + harm(observation)));
            }
        }
        for (Testable item : testable(observation, memory)) {
            Action use = new Action.UseItem(item.ref(), item.verb());
            if (!offered.contains(use)) {
                continue;
            }
            List<SafeTest.Candidate> candidates = SafeTest.candidates(item.guess(), knowledge);
            SafeTest.Verdict here = SafeTest.of(candidates, observation);
            if (item.guess().kind() == ItemKind.POTION && !Pickup.stuck(memory)) {
                boolean[] walk = Explore.walkable(observation, memory);
                int[] distance = Pickup.distances(map, walk, hero);
                int best = -1;
                int bestDamage = here.safe() ? here.worst().damage() : Integer.MAX_VALUE;
                int bestDistance = Integer.MAX_VALUE;
                for (int cell = 0; cell < distance.length; cell++) {
                    if (distance[cell] < 1 || distance[cell] > REACH || !harmfulOn(map, cell).isEmpty()) {
                        continue;
                    }
                    SafeTest.Verdict there = SafeTest.of(candidates, observation, cell);
                    int damage = there.worst().damage();
                    if (there.safe() && (damage < bestDamage || (best >= 0 && damage == bestDamage
                            && distance[cell] < bestDistance))) {
                        best = cell;
                        bestDamage = damage;
                        bestDistance = distance[cell];
                    }
                }
                if (best >= 0) {
                    Action step = Pickup.firstStep(map, walk, hero, best, offered);
                    if (step != null) {
                        return new Plan(item.guess().label(), item.quantity(),
                                new RunLog.Choice(step, Policies.CERTAIN, "cell: " + item.guess().label()));
                    }
                }
            }
            if (here.safe()) {
                return new Plan(item.guess().label(), item.quantity(),
                        new RunLog.Choice(use, Policies.CERTAIN, "test: " + item.guess().label()));
            }
        }
        return null;
    }

    /**
     * The trial the Memory records when this Policy's {@code action} is handed over on this screen
     * (story 4.10), or {@link Memory.Trial#NONE} when it is not one this Policy plans here.
     */
    Memory.Trial trial(Observation observation, Memory memory, Action action) {
        Plan plan = plan(observation, memory, observation.actions().actions());
        if (plan == null || plan.label().isEmpty() || !plan.choice().action().equals(action)) {
            return Memory.Trial.NONE;
        }
        return new Memory.Trial(plan.label(), plan.quantity(),
                action instanceof Action.Step step ? step.cell() : -1);
    }

    /** An appearance held that this Policy may test: the pack's reference, its guess and its verb. */
    record Testable(ItemRef ref, int quantity, Beliefs.Guess guess, String verb) {
    }

    /**
     * The appearances held that may be tested here, worth most first: a potion that can be drunk or a
     * scroll that can be read, with two candidates or more, not balked at on this floor, and not a
     * scroll while the hero is blinded or immune to magic.
     */
    List<Testable> testable(Observation observation, Memory memory) {
        Map<String, Beliefs.Guess> guesses = new LinkedHashMap<>();
        for (Beliefs.Guess guess : Beliefs.identities(observation, knowledge)) {
            guesses.put(guess.label(), guess);
        }
        boolean unreadable = has(observation, BLINDED) || has(observation, MAGIC_IMMUNE);
        int depth = observation.header().depth();
        int branch = observation.header().branch();
        List<ItemView> items = observation.inventory().items();
        List<Testable> testable = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            ItemView item = items.get(index);
            Beliefs.Guess guess = guesses.get(item.name());
            if (guess == null || guess.odds().size() < 2 || memory.balks(depth, branch, item.name())) {
                continue;
            }
            String verb = item.kind() == ItemKind.POTION && guess.kind() == ItemKind.POTION && hurt(observation) ? DRINK
                    : item.kind() == ItemKind.SCROLL && guess.kind() == ItemKind.SCROLL && !unreadable ? READ : null;
            if (verb == null || !item.actions().contains(verb)) {
                continue;
            }
            testable.add(new Testable(new ItemRef(index, item.name(), item.quantity()), item.quantity(), guess, verb));
        }
        // Worth most first; the sort is stable, so equal worths keep the pack's order.
        testable.sort(Comparator.comparingLong(TestItem::worth).reversed());
        return testable;
    }

    /**
     * Whether the hero is down to four fifths of its hit points or below, when a potion is drunk to
     * test it: a healing potion then heals rather than being wasted (the direction check of story
     * 4.10 measured drinking whole as a loss: potions of healing spent at full health).
     */
    static boolean hurt(Observation observation) {
        return 5L * observation.hero().hp() <= 4L * observation.hero().ht();
    }

    /** What knowing an appearance is worth: every copy held, times the candidates beyond one. */
    static long worth(Testable item) {
        return (long) item.quantity() * (item.guess().odds().size() - 1);
    }

    /** Whether the hero's own cell shows a harmful blob, or the hero is burning. */
    static boolean inHarm(Observation observation) {
        return !harm(observation).isEmpty();
    }

    /** Whether the hero is in harm within {@link #ESCAPE_WAITS} waits of a test this Policy handed over. */
    static boolean escaping(Observation observation, Memory memory) {
        return memory.tested() >= 0 && memory.waits() - memory.tested() <= ESCAPE_WAITS && inHarm(observation);
    }

    /**
     * What harms the hero where it stands: "burning" off water (burning ends at an act on water,
     * Burning.java:98-99), or a harmful blob on its cell, or empty.
     */
    static String harm(Observation observation) {
        if (has(observation, BURNING) && observation.map().tiles().get(observation.hero().cell()) != Tile.WATER) {
            return BURNING;
        }
        return harmfulOn(observation.map(), observation.hero().cell());
    }

    private static String harmfulOn(MapSection map, int cell) {
        for (BlobCell blob : map.blobs()) {
            if (blob.cell() == cell) {
                for (String kind : blob.kinds()) {
                    if (HARMFUL.contains(kind)) {
                        return kind;
                    }
                }
            }
        }
        return "";
    }

    /**
     * The offered Step out of harm: toward the nearest walkable cell with no harmful blob, or, while
     * burning, toward the nearest water within {@link #REACH}, which puts the burning out
     * (Burning.java:98-99). Every Step brings the hero strictly nearer, so it cannot stand still.
     */
    static Action escape(Observation observation, Memory memory, List<Action> offered) {
        MapSection map = observation.map();
        int hero = observation.hero().cell();
        // Through the cloud: a hero in the middle of one has no clean neighbour to start from.
        boolean[] walk = Explore.walkable(observation, memory, true, true);
        int[] distance = Pickup.distances(map, walk, hero);
        boolean burning = has(observation, BURNING);
        int depth = observation.header().depth();
        int branch = observation.header().branch();
        java.util.function.IntPredicate clean = cell -> harmfulOn(map, cell).isEmpty()
                && !memory.clouded(depth, branch, cell, memory.waits());
        int target = -1;
        if (burning) {
            target = nearest(map, distance, cell -> map.tiles().get(cell) == Tile.WATER && clean.test(cell), REACH);
        }
        if (target < 0) {
            // Clear of the cloud's edge, which spreads: a clean cell with no cloud beside it, which
            // through a door is the far side of it.
            target = nearest(map, distance, cell -> clean.test(cell) && clear(map, cell, clean), Integer.MAX_VALUE);
        }
        if (target < 0) {
            target = nearest(map, distance, clean, Integer.MAX_VALUE);
        }
        if (target < 0 || target == hero) {
            return null;
        }
        return Pickup.firstStep(map, walk, hero, target, offered);
    }

    /** Whether every neighbour of {@code cell} on the map is {@code clean}. */
    private static boolean clear(MapSection map, int cell, java.util.function.IntPredicate clean) {
        int width = map.width();
        int x = cell % width;
        int y = cell / width;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx;
                int ny = y + dy;
                if ((dx != 0 || dy != 0) && nx >= 0 && ny >= 0 && nx < width && ny < map.height()
                        && !clean.test(nx + ny * width)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int nearest(MapSection map, int[] distance, java.util.function.IntPredicate fits, int reach) {
        int best = -1;
        for (int cell = 0; cell < distance.length; cell++) {
            if (distance[cell] >= 1 && distance[cell] <= reach && fits.test(cell)
                    && (best < 0 || distance[cell] < distance[best])) {
                best = cell;
            }
        }
        return best;
    }

    private static boolean has(Observation observation, String buff) {
        for (BuffView view : observation.hero().buffs()) {
            if (view.name().equals(buff)) {
                return true;
            }
        }
        return false;
    }
}
