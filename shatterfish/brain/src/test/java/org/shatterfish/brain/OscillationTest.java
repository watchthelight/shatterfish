package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.ActionsSection;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.ActorsSection;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.Belief;
import org.shatterfish.api.Emote;
import org.shatterfish.api.EquipSlot;
import org.shatterfish.api.Feeling;
import org.shatterfish.api.Fog;
import org.shatterfish.api.HeaderSection;
import org.shatterfish.api.HeapKind;
import org.shatterfish.api.HeapView;
import org.shatterfish.api.HeroClass;
import org.shatterfish.api.HeroSection;
import org.shatterfish.api.HeroSubclass;
import org.shatterfish.api.Hunger;
import org.shatterfish.api.InventorySection;
import org.shatterfish.api.ItemKind;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.JournalSection;
import org.shatterfish.api.LogSection;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.ObservationCodec;
import org.shatterfish.api.PromptKind;
import org.shatterfish.api.PromptSection;
import org.shatterfish.api.QuickslotView;
import org.shatterfish.api.Tile;
import org.shatterfish.api.TransitionKind;
import org.shatterfish.api.TransitionView;
import org.shatterfish.api.ValidActions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Back and forth between two cells (issue #174): the loops the smoke set showed, each played out wait by
 * wait through the Brain against a small world that moves the hero on its Steps and draws an enemy only
 * from where the hero could see it, and shown to end.
 *
 * <p>Worlds are drawn as text: {@code #} wall, {@code .} floor in view, {@code ~} floor seen before and
 * out of view now, a space a cell never seen. The hero, the enemies and the heaps are placed by each
 * test, since what the screen shows of them depends on where the hero stands.
 */
class OscillationTest {

    private static Brain brain() {
        return new Brain(FightPolicyTest.KNOWLEDGE, Screens.WEIGHTS, 11L);
    }

    /** A screen of {@code rows} with the hero on {@code hero} at {@code hp} of 20, these actors and heaps in view. */
    static Observation screen(String[] rows, int hero, int hp, List<ActorView> actors, List<HeapView> heaps) {
        int width = rows[0].length();
        List<Tile> tiles = new ArrayList<>();
        List<Fog> fog = new ArrayList<>();
        List<TransitionView> transitions = new ArrayList<>();
        for (String row : rows) {
            for (int x = 0; x < width; x++) {
                char c = row.charAt(x);
                if (c == '<') {
                    transitions.add(new TransitionView(tiles.size(), TransitionKind.REGULAR_ENTRANCE));
                }
                tiles.add(switch (c) {
                    case ' ' -> Tile.NONE;
                    case '#' -> Tile.WALL;
                    case '<' -> Tile.ENTRANCE;
                    default -> Tile.EMPTY;
                });
                fog.add(c == ' ' ? Fog.UNKNOWN : c == '~' ? Fog.VISITED : Fog.VISIBLE);
            }
        }
        HeaderSection header = new HeaderSection(ObservationCodec.SCHEMA_VERSION, "v4.0.0", "", HeroClass.WARRIOR,
                List.of(), 1, 0, false, false, PromptKind.NONE);
        MapSection map = new MapSection(width, rows.length, tiles, fog, List.of(), heaps, List.of(), Feeling.NONE,
                transitions);
        HeroSection section = new HeroSection(hero, "", HeroSubclass.NONE, "", 1, 0, 1, hp, 20, 0, 10, 0, 0, 0,
                Hunger.NONE, List.of(), List.of(), List.of(0, 0, 0, 0),
                Collections.nCopies(HeroSection.QUICKSLOTS, new QuickslotView("", false)));
        List<ItemView> pack = new ArrayList<>(List.of(
                new ItemView(ItemKind.WEAPON, "worn shortsword", 1, true, 0, true, false, "", EquipSlot.WEAPON, List.of(), ""),
                new ItemView(ItemKind.ARMOR, "cloth armor", 1, true, 0, true, false, "", EquipSlot.ARMOR, List.of(), "")));
        pack.addAll(ExplorePolicyTest.PACK);
        Observation bare = new Observation(header, map, new ActorsSection(actors), section, new InventorySection(pack),
                new JournalSection(List.of(), List.of()), new LogSection(List.of()), ActionsSection.NONE,
                PromptSection.NONE);
        return bare.withActions(ValidActions.of(bare));
    }

    static ActorView rat(int cell) {
        return new ActorView(cell, "marsupial rat", Alignment.ENEMY, ObservationCodec.MAX_HEALTH_PIPS, false, Emote.NONE,
                List.of());
    }

    static HeapView ration(int cell) {
        return new HeapView(cell, HeapKind.HEAP, false, "ration of food", 0, "");
    }

    /** What a played-out stretch of waits did: the hero's cell after each, and each Action. */
    record Played(List<Integer> cells, List<Action> actions, List<Said> decisions, Belief belief) {
    }

    /** One wait's Decision, for the messages. */
    record Said(String policy, String why) {
    }

    /**
     * {@code waits} waits of {@code brain} in the world {@code world}, from the hero on {@code start} and the
     * Belief {@code belief}: each wait the screen the world draws for where the hero stands, the Brain's
     * update, Decision and handover, as its driver does them, and the hero moved by a Step.
     */
    static Played play(Brain brain, IntFunction<Observation> world, int start, Belief belief, int waits) {
        int hero = start;
        List<Integer> cells = new ArrayList<>();
        List<Action> actions = new ArrayList<>();
        List<Said> decisions = new ArrayList<>();
        for (int wait = 0; wait < waits; wait++) {
            Observation screen = world.apply(hero);
            belief = brain.update(screen, belief);
            Brain.Decided decided = brain.decide(screen, belief);
            belief = brain.handed(screen, belief, decided);
            actions.add(decided.action());
            decisions.add(decided.decision() == null ? new Said("-", decided.why())
                    : new Said(decided.decision().policy(), decided.decision().chosen().why()));
            if (decided.action() instanceof Action.Step step) {
                hero = step.cell();
            }
            cells.add(hero);
        }
        return new Played(cells, actions, decisions, belief);
    }

    /** The longest run of Steps in {@code played} that alternate between the same two cells. */
    static int longestBounce(Played played) {
        int longest = 0;
        int run = 0;
        for (int i = 0; i < played.actions().size(); i++) {
            boolean step = played.actions().get(i) instanceof Action.Step;
            boolean back = step && i >= 2 && played.cells().get(i).equals(played.cells().get(i - 2))
                    && !played.cells().get(i).equals(played.cells().get(i - 1));
            run = back ? Math.max(run + 1, 3) : step ? 1 : 0;
            longest = Math.max(longest, back ? run : 0);
        }
        return longest;
    }

    /**
     * A floor where a side pocket shows an enemy the corridor does not: from the pocket the way to the
     * enemy runs back out through the corridor. {@code pocket} is the pocket's cell.
     */
    private static final String[] POCKET = {
        "##########",
        "#........#",
        "####.##.##",
        "#######.##",
        "#######.##",
        "####....##",
        "##########",
    };

    private static final int POCKET_CELL = 4 + 2 * 10;
    private static final int RAT_CELL = 4 + 5 * 10;

    /** The pocket floor with the rat drawn only from the pocket or the lower corridor, and {@code heaps}. */
    private static IntFunction<Observation> pocket(String[] rows, List<HeapView> heaps) {
        return hero -> screen(rows, hero, 20,
                hero == POCKET_CELL || hero / 10 >= 3 ? List.of(rat(RAT_CELL)) : List.of(),
                heaps);
    }

    @Test
    @DisplayName("fight and pick-up: the enemy seen only from the heap's pocket is chased once it drops out of view, "
            + "not left to the pick-up Policy, which walked the hero back into the pocket")
    void chase_past_the_pick_up() {
        Played played = play(brain(), pocket(POCKET, List.of(ration(POCKET_CELL))), 1 + 10, null, 24);
        assertTrue(longestBounce(played) < Memory.BOUNCES, "no loop: " + played.cells() + " " + played.decisions());
        assertTrue(played.actions().contains(new Action.Attack(RAT_CELL)),
                "the hero reaches the rat and attacks it: " + played.cells() + " " + played.decisions());
        assertTrue(played.decisions().stream().anyMatch(d -> d.why().startsWith("chase ")),
                "the fight Policy chased: " + played.decisions());
    }

    @Test
    @DisplayName("fight and explore: the enemy seen only from a frontier pocket is chased, not left to the explore "
            + "Policy, which walked the hero back to the frontier")
    void chase_past_the_frontier() {
        String[] rows = POCKET.clone();
        rows[3] = "#### ##.##";
        Played played = play(brain(), pocket(rows, List.of()), 1 + 10, null, 24);
        assertTrue(longestBounce(played) < Memory.BOUNCES, "no loop: " + played.cells() + " " + played.decisions());
        assertTrue(played.actions().contains(new Action.Attack(RAT_CELL)),
                "the hero reaches the rat and attacks it: " + played.cells() + " " + played.decisions());
    }

    @Test
    @DisplayName("a chase is where the screen showed the enemy, it lapses after CHASE_WAITS waits, and an attack ends it")
    void chase_bounds() {
        Brain brain = brain();
        Observation seen = pocket(POCKET, List.of()).apply(POCKET_CELL);
        Belief belief = brain.update(seen, null);
        Brain.Decided approach = brain.decide(seen, belief);
        assertTrue(approach.decision().chosen().why().startsWith("approach "), approach.decision().toString());
        Memory chasing = Memory.of(brain.handed(seen, belief, approach));
        assertEquals(new Memory.Spot(1, 0, RAT_CELL), chasing.chase(), "the rat's cell, as the screen drew it");
        assertEquals(chasing, Memory.of(chasing.belief()), "the chase survives the Belief's bytes");

        Observation corridor = pocket(POCKET, List.of()).apply(5 + 10);
        Memory fresh = Beliefs.fold(chasing, corridor, FightPolicyTest.KNOWLEDGE);
        assertTrue(Fight.chasing(corridor, fresh, FightPolicyTest.KNOWLEDGE), "out of view one wait later: chased");
        Memory stale = chasing;
        for (int i = 0; i <= Fight.CHASE_WAITS; i++) {
            stale = Beliefs.fold(stale, corridor, FightPolicyTest.KNOWLEDGE);
        }
        assertFalse(Fight.chasing(corridor, stale, FightPolicyTest.KNOWLEDGE), "past CHASE_WAITS the chase has lapsed");
        assertEquals(Memory.Spot.NOWHERE, stale.chase(), "and the fold forgets it");

        Observation beside = pocket(POCKET, List.of()).apply(5 + 5 * 10);
        Memory reached = Beliefs.fold(chasing, beside, FightPolicyTest.KNOWLEDGE);
        assertEquals(Memory.Spot.NOWHERE, reached.chase(), "beside the cell, the chase is over");

        Brain.Decided attack = brain.decide(beside, reached.belief());
        assertEquals(new Action.Attack(RAT_CELL), attack.action());
        assertEquals(Memory.Spot.NOWHERE, Memory.of(brain.handed(beside, chasing.belief(), attack)).chase(),
                "an attack ends the chase");
    }

    /** A corridor with a frontier at its far end, x 1 to 9 walkable, the rest never seen. */
    private static final String[] CORRIDOR = {
        "#############",
        "#.........   ",
        "#############",
    };

    @Test
    @DisplayName("a region over the only way on: the plan through it crosses and lifts it, and the way out of it no "
            + "longer steps the hero back out at every other wait")
    void region_crossed() {
        Memory.Avoid region = new Memory.Avoid(1, 0, 5 + 13, 2, 500);
        Belief start = Memory.START.avoiding(region).belief();
        Played played = play(brain(), hero -> screen(CORRIDOR, hero, 20, List.of(), List.of()), 1 + 13, start, 12);
        assertTrue(longestBounce(played) < Memory.BOUNCES, "no loop: " + played.cells() + " " + played.decisions());
        assertTrue(played.cells().contains(9 + 13), "the hero reaches the frontier: " + played.cells());
        assertTrue(Memory.of(played.belief()).avoid().isEmpty(), "the region was lifted");
    }

    @Test
    @DisplayName("the explore Policy's way out of a region is the short walk away from its centre, and lifts nothing")
    void away_lifts_nothing() {
        Memory.Avoid region = new Memory.Avoid(1, 0, 5 + 13, 2, 500);
        Observation inside = screen(CORRIDOR, 4 + 13, 20, List.of(), List.of());
        Brain brain = brain();
        Belief belief = brain.update(inside, Memory.START.avoiding(region).belief());
        Brain.Decided away = brain.decide(inside, belief);
        assertTrue(away.decision().chosen().why().startsWith("away "), away.decision().toString());
        assertEquals(new Action.Step(3 + 13), away.action(), "out of the region the short way, away from its centre");
        assertEquals(List.of(region), Memory.of(brain.handed(inside, belief, away)).avoid());
    }

    /** A room three rows high, all of it in view. */
    private static final String[] ROOM = {
        "############",
        "#..........#",
        "#..........#",
        "#..........#",
        "############",
    };

    @Test
    @DisplayName("two overlapping regions: the way out counts both, where one at a time led out of each into the other")
    void overlapping_regions() {
        int width = 12;
        Memory memory = Memory.START.avoiding(new Memory.Avoid(1, 0, 4 + 2 * width, 2, 500))
                .avoiding(new Memory.Avoid(1, 0, 7 + 2 * width, 2, 500));
        Played played = play(brain(), hero -> screen(ROOM, hero, 20, List.of(), List.of()), 5 + 2 * width,
                memory.belief(), 8);
        assertTrue(longestBounce(played) < Memory.BOUNCES, "no loop: " + played.cells() + " " + played.decisions());
        assertTrue(played.cells().subList(0, 6).stream().anyMatch(cell -> cell % width <= 1 || cell % width >= 10),
                "the hero leaves both regions: " + played.cells() + " " + played.decisions());
    }

    @Test
    @DisplayName("a retreat by the stairs sets a region round the enemy too, so the explore Policy does not walk "
            + "straight back into view once the enemy drops out of it")
    void stairs_retreat_sets_a_region() {
        String[] rows = {
            "##########",
            "#<.......#",
            "##########",
        };
        Observation hurt = screen(rows, 4 + 10, 5, List.of(rat(8 + 10)), List.of());
        Brain brain = brain();
        Belief belief = brain.update(hurt, null);
        Brain.Decided retreat = brain.decide(hurt, belief);
        assertEquals("retreat: stairs", retreat.decision().chosen().why(), retreat.decision().toString());
        assertEquals(List.of(new Memory.Avoid(1, 0, 8 + 10, 5, Memory.of(belief).waits() + Brain.AVOID_WAITS)),
                Memory.of(brain.handed(hurt, belief, retreat)).avoid(), "one past where the rat was seen from");
    }

    @Test
    @DisplayName("bounced BOUNCES times:the cell of the Step back is blocked for BOUNCE_WAITS waits, so the Step back is "
            + "no choice at the next wait either, where story 4.13 withheld it for one")
    void bounce_blocks_the_cell() {
        // The frontier is to the left: from 6, the explore Policy's plan is the Step back to 5.
        String[] rows = {
            "##########",
            "   ......#",
            "##########",
        };
        IntFunction<Observation> world = hero -> screen(rows, hero, 20, List.of(), List.of());
        int a = 5 + 10;
        int b = 6 + 10;
        Memory memory = Memory.START;
        for (int i = 0; i < Memory.BOUNCES + 2; i++) {
            memory = Beliefs.fold(memory, world.apply(i % 2 == 0 ? a : b), FightPolicyTest.KNOWLEDGE);
        }
        assertTrue(memory.bounces() >= Memory.BOUNCES);
        assertEquals(a, memory.prior());
        Memory blocked = memory;
        assertTrue(blocked.fleeting().contains(new Memory.Cloud(1, 0, a, blocked.waits() + Memory.BOUNCE_WAITS)),
                "blocked: " + blocked.fleeting());
        Brain brain = brain();
        Brain.Decided first = brain.decide(world.apply(b), blocked.belief());
        assertNotEquals(new Action.Step(a), first.action(), first.decision().toString());
        Memory later = Beliefs.fold(blocked, world.apply(b), FightPolicyTest.KNOWLEDGE);
        later = Beliefs.fold(later, world.apply(b), FightPolicyTest.KNOWLEDGE);
        assertTrue(later.bounces() < Memory.BOUNCES, "the bounces start again");
        Brain.Decided second = brain.decide(world.apply(b), later.belief());
        assertNotEquals(new Action.Step(a), second.action(), "still blocked two waits on: " + second.decision());
    }

    @Test
    @DisplayName("a fight is weighed against the enemies seen in the last RECALL_WAITS waits and out of view now, "
            + "not those whose cell the screen shows empty")
    void recalled_threats() {
        String[] rows = {
            "##########",
            "#........#",
            "#~~~~~~~~#",
            "##########",
        };
        Observation screen = screen(rows, 1 + 10, 20, List.of(rat(4 + 10)), List.of());
        Memory memory = Memory.START;
        for (int i = 0; i < Fight.RECALL_WAITS + 5; i++) {
            memory = Beliefs.fold(memory, screen, FightPolicyTest.KNOWLEDGE);
        }
        Memory.Seen hidden = new Memory.Seen("brute", 1, 5 + 20, memory.waits() - 1);
        Memory.Seen shown = new Memory.Seen("brute", 1, 6 + 10, memory.waits() - 1);
        Memory.Seen old = new Memory.Seen("brute", 1, 7 + 20, memory.waits() - Fight.RECALL_WAITS - 1);
        List<Memory.Seen> monsters = new ArrayList<>(memory.monsters());
        monsters.addAll(List.of(hidden, shown, old));
        Memory recalled = new Memory(memory.waits(), memory.deepest(), memory.facts(), memory.found(), memory.held(),
                memory.known(), memory.labels(), memory.pending(), monsters, memory.at(), memory.streak(), memory.calm(),
                memory.dwelt(), memory.blocked());
        List<ActorView> threats = Fight.threats(screen, recalled, Fight.enemies(screen, FightPolicyTest.KNOWLEDGE), FightPolicyTest.KNOWLEDGE);
        assertEquals(List.of(4 + 10, 5 + 20), threats.stream().map(ActorView::cell).toList(),
                "the rat in view and the brute out of view; not the brute whose cell shows empty, nor the old sighting");
        assertTrue(Fight.favourable(screen, FightPolicyTest.KNOWLEDGE, Fight.enemies(screen, FightPolicyTest.KNOWLEDGE)), "the rat alone");
        assertFalse(Fight.favourable(screen, FightPolicyTest.KNOWLEDGE, threats), "with the brute nearby");
        Brain.Decided decided = brain().decide(screen, recalled.belief());
        assertFalse(decided.decision().chosen().why().startsWith("approach "),
                "no approach toward a fight the brute makes unfavourable: " + decided.decision());
    }
}
