package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Belief;
import org.shatterfish.api.HeapKind;
import org.shatterfish.api.HeapView;
import org.shatterfish.api.ItemKind;
import org.shatterfish.api.KnownAppearance;
import org.shatterfish.api.Observation;
import org.shatterfish.api.Tile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Brain's Beliefs (story 4.2, FR-29): candidate identities that are a distribution and narrow
 * as identities are learned, the facts a floor implies, the guaranteed drops a set of floors owes,
 * and enemies remembered once out of sight.
 */
class BeliefConsistencyTest {

    private static final List<Tile> FLOOR = List.of(Tile.EMPTY, Tile.EMPTY, Tile.EMPTY);

    private static Brain brain() {
        return new Brain(Screens.CODEX, Screens.WEIGHTS, 3L);
    }

    /** The Beliefs after showing {@code screens} in order, at the last of them. */
    private static Beliefs after(Observation... screens) {
        Brain brain = brain();
        Belief belief = null;
        for (Observation screen : screens) {
            belief = brain.update(screen, belief);
        }
        return brain.beliefs(screens[screens.length - 1], belief);
    }

    private static Beliefs.Guess guess(Beliefs beliefs, String label) {
        return beliefs.identities().stream().filter(guess -> guess.label().equals(label)).findFirst().orElseThrow();
    }

    private static double sum(Beliefs.Guess guess) {
        return guess.odds().stream().mapToDouble(Beliefs.Odds::probability).sum();
    }

    @Test
    @DisplayName("an unidentified appearance in view carries its family's identities, by deck weight")
    void a_distribution() {
        Beliefs beliefs = after(Screens.world(1, FLOOR, List.of(), List.of(),
                List.of(Screens.item(ItemKind.POTION, "crimson potion", 2)), List.of()));

        Beliefs.Guess crimson = guess(beliefs, "crimson potion");
        assertEquals(1.0, sum(crimson), 1e-12);
        assertEquals(4, crimson.odds().size(), "strength is a candidate though the decks weight it zero");
        // Healing 6, strength weighted as the heaviest (6), mind vision 4, frost 3: of 19.
        assertEquals("potion of healing", crimson.odds().get(0).name());
        assertEquals(6.0 / 19, crimson.odds().get(0).probability(), 1e-12);
        assertEquals(6.0 / 19, crimson.odds().get(1).probability(), 1e-12);
        assertEquals("potion of frost", crimson.odds().get(3).name());
        assertEquals(3.0 / 19, crimson.odds().get(3).probability(), 1e-12);
        assertEquals(1, beliefs.identities().size(), "an appearance not in view is not guessed at");
    }

    @Test
    @DisplayName("two appearances in view are two identities: each one's odds are its marginal over the pairs")
    void distinct_identities() {
        Beliefs beliefs = after(Screens.world(1, FLOOR, List.of(new HeapView(2, HeapKind.HEAP, false, "amber potion x2", 0, "")),
                List.of(), List.of(Screens.item(ItemKind.POTION, "crimson potion", 1)), List.of()));

        assertEquals(List.of("crimson potion", "amber potion"),
                beliefs.identities().stream().map(Beliefs.Guess::label).toList(), "a heap's stack count is not its name");
        // P(x) is w_x times the other's total, 19 - w_x: healing and strength 6*13, mind vision
        // 4*15, frost 3*16, of 264.
        for (Beliefs.Guess guess : beliefs.identities()) {
            assertEquals(1.0, sum(guess), 1e-12);
            assertEquals(78.0 / 264, guess.odds().get(0).probability(), 1e-12, guess.toString());
            assertEquals(48.0 / 264, guess.odds().get(3).probability(), 1e-12, guess.toString());
        }
    }

    @Test
    @DisplayName("with every remaining appearance in view, each is equally likely to be any remaining identity")
    void all_in_view() {
        List<KnownAppearance> known = List.of(new KnownAppearance(ItemKind.POTION, "potion of healing"));
        Beliefs beliefs = after(Screens.world(1, FLOOR, List.of(), List.of(),
                List.of(Screens.item(ItemKind.POTION, "crimson potion", 1), Screens.item(ItemKind.POTION, "amber potion", 1),
                        Screens.item(ItemKind.POTION, "golden potion", 1)), known));
        assertEquals(3, beliefs.identities().size());
        for (Beliefs.Guess guess : beliefs.identities()) {
            for (Beliefs.Odds odds : guess.odds()) {
                assertEquals(1.0 / 3, odds.probability(), 1e-12, guess.toString());
            }
        }
    }

    @Test
    @DisplayName("identifying one identity removes it from every other appearance's candidates, and the rest still sum to one")
    void identifying_narrows() {
        List<KnownAppearance> known = List.of(new KnownAppearance(ItemKind.POTION, "potion of healing"));
        Beliefs beliefs = after(Screens.world(1, FLOOR, List.of(), List.of(),
                List.of(Screens.item(ItemKind.POTION, "crimson potion", 1), Screens.item(ItemKind.POTION, "amber potion", 1),
                        Screens.item(ItemKind.POTION, "potion of healing", 1)), known));

        for (Beliefs.Guess guess : beliefs.identities()) {
            assertTrue(guess.odds().stream().noneMatch(odds -> odds.name().equals("potion of healing")), guess.toString());
            assertEquals(1.0, sum(guess), 1e-12, guess.toString());
            assertEquals(3, guess.odds().size(), guess.toString());
        }
        assertEquals(2, beliefs.identities().size(), "an identified item wears its own name and is not guessed at");
        // Strength 6*7, mind vision 4*9, frost 3*10, of 108.
        assertEquals(42.0 / 108, guess(beliefs, "amber potion").odds().get(0).probability(), 1e-12);
    }

    @Test
    @DisplayName("a heap's title is its item's name without the stack count or the level")
    void heap_titles() {
        assertEquals("crimson potion", Beliefs.untitled("crimson potion x12"));
        assertEquals("ring of accuracy", Beliefs.untitled("ring of accuracy +2"));
        assertEquals("dagger", Beliefs.untitled("dagger -1 x2"));
        assertEquals("scroll of KAUNAN", Beliefs.untitled("scroll of KAUNAN"));
        assertEquals("box x", Beliefs.untitled("box x"));
    }

    @Test
    @DisplayName("an identified name of another family narrows nothing here")
    void by_family() {
        List<KnownAppearance> known = List.of(new KnownAppearance(ItemKind.SCROLL, "potion of healing"));
        Beliefs beliefs = after(Screens.world(1, FLOOR, List.of(), List.of(),
                List.of(Screens.item(ItemKind.POTION, "crimson potion", 1)), known));
        assertEquals(4, guess(beliefs, "crimson potion").odds().size());
    }

    @Test
    @DisplayName("a chest on a pedestal with water on three sides and a wall on the fourth is a pool room, remembered out of view")
    void a_pool_room() {
        // The pedestal on cell 4 of a 3x3 floor; its sides are cells 1, 3, 5 and 7.
        HeapView chest = new HeapView(4, HeapKind.CHEST, false, "", 0, "");
        Observation pool = Screens.grid(3, 3, List.of(Tile.EMPTY, Tile.WATER, Tile.EMPTY, Tile.WATER, Tile.PEDESTAL,
                Tile.WATER, Tile.EMPTY, Tile.WALL, Tile.EMPTY), List.of(chest));
        Observation later = Screens.world(3, FLOOR, List.of(), List.of(), List.of(), List.of());
        Observation below = Screens.world(4, FLOOR, List.of(), List.of(), List.of(), List.of());

        assertEquals(List.of(new Beliefs.FloorItem("potion of invisibility", "pool room")), after(pool, later).floor());
        assertEquals(List.of(), after(pool, later, below).floor(), "a fact belongs to its floor");
        assertEquals(List.of(new Beliefs.FloorItem("potion of invisibility", "pool room")),
                after(pool, later, pool).floor(), "and is recorded once");

        // The suspicious chest room's pedestal sits at its room's centre, never beside a wall, and
        // the level's water can surround it.
        Observation suspicious = Screens.grid(3, 3, List.of(Tile.EMPTY, Tile.WATER, Tile.EMPTY, Tile.WATER, Tile.PEDESTAL,
                Tile.WATER, Tile.EMPTY, Tile.WATER, Tile.EMPTY), List.of(chest));
        assertEquals(List.of(), after(suspicious).floor(), "water on four sides is not the pool");
        Observation shore = Screens.grid(3, 3, List.of(Tile.EMPTY, Tile.WATER, Tile.EMPTY, Tile.EMPTY, Tile.PEDESTAL,
                Tile.WATER, Tile.EMPTY, Tile.WALL_DECO, Tile.EMPTY), List.of(chest));
        assertEquals(List.of(), after(shore).floor(), "water on two sides is not the pool");
        Observation heap = Screens.grid(3, 3, List.of(Tile.EMPTY, Tile.WATER, Tile.EMPTY, Tile.WATER, Tile.PEDESTAL,
                Tile.WATER, Tile.EMPTY, Tile.WALL, Tile.EMPTY),
                List.of(new HeapView(4, HeapKind.HEAP, false, "crimson potion", 0, "")));
        assertEquals(List.of(), after(heap).floor(), "only the prize's chest");
    }

    @Test
    @DisplayName("the guaranteed strength potions and upgrade scrolls are counted per set of five floors")
    void chapter_counters() {
        Observation none = Screens.world(2, FLOOR, List.of(), List.of(), List.of(), List.of());
        Observation one = Screens.world(2, FLOOR, List.of(), List.of(),
                List.of(Screens.item(ItemKind.POTION, "potion of strength", 1)), List.of());
        Observation drunk = Screens.world(3, FLOOR, List.of(), List.of(), List.of(), List.of());
        Observation another = Screens.world(4, FLOOR, List.of(), List.of(),
                List.of(Screens.item(ItemKind.POTION, "potion of strength", 1)), List.of());
        Observation nextSet = Screens.world(5, FLOOR, List.of(), List.of(), List.of(), List.of());

        assertEquals(new Beliefs.Chapter("STRENGTH_POTIONS", "potion of strength", 0, 0, 2), after(none).chapters().get(0));
        assertEquals(new Beliefs.Chapter("STRENGTH_POTIONS", "potion of strength", 0, 1, 1), after(none, one).chapters().get(0));
        assertEquals(new Beliefs.Chapter("STRENGTH_POTIONS", "potion of strength", 0, 1, 1),
                after(none, one, one).chapters().get(0), "holding it is not finding it again");
        assertEquals(new Beliefs.Chapter("STRENGTH_POTIONS", "potion of strength", 0, 2, 0),
                after(none, one, drunk, another).chapters().get(0), "drunk, then another found");
        assertEquals(new Beliefs.Chapter("STRENGTH_POTIONS", "potion of strength", 1, 0, 2),
                after(none, one, drunk, another, nextSet).chapters().get(0), "a new set owes its own");
        assertEquals(new Beliefs.Chapter("UPGRADE_SCROLLS", "scroll of upgrade", 0, 0, 3), after(none, one).chapters().get(1));
    }

    @Test
    @DisplayName("an appearance found unidentified and then identified as strength counts in the set it was found in")
    void found_unidentified() {
        List<KnownAppearance> strength = List.of(new KnownAppearance(ItemKind.POTION, "potion of strength"));
        Observation found = Screens.world(2, FLOOR, List.of(), List.of(),
                List.of(Screens.item(ItemKind.POTION, "crimson potion", 1)), List.of());
        Observation drunk = Screens.world(6, FLOOR, List.of(), List.of(), List.of(), strength);
        Observation back = Screens.world(4, FLOOR, List.of(), List.of(), List.of(), strength);

        // Drunk on floor 6: the one potion that was crimson is gone and strength is known, so the
        // crimson potion found on floor 2 was strength, and the first set found it.
        assertEquals(new Beliefs.Chapter("STRENGTH_POTIONS", "potion of strength", 0, 1, 1),
                after(found, drunk, back).chapters().get(0));
        assertEquals(new Beliefs.Chapter("STRENGTH_POTIONS", "potion of strength", 1, 0, 2),
                after(found, drunk).chapters().get(0), "not the set it was drunk in");

        // Identified in the pack: two crimson potions become two potions of strength on floor 6,
        // found on floor 2 -- not a find on floor 6.
        Observation two = Screens.world(2, FLOOR, List.of(), List.of(),
                List.of(Screens.item(ItemKind.POTION, "crimson potion", 2)), List.of());
        Observation named = Screens.world(6, FLOOR, List.of(), List.of(),
                List.of(Screens.item(ItemKind.POTION, "potion of strength", 2)), strength);
        assertEquals(new Beliefs.Chapter("STRENGTH_POTIONS", "potion of strength", 1, 0, 2),
                after(two, named).chapters().get(0));
        assertEquals(new Beliefs.Chapter("STRENGTH_POTIONS", "potion of strength", 0, 2, 0),
                after(two, named, back).chapters().get(0));

        // Two appearances gone at the wait strength became known: which was strength cannot be
        // told, so neither is counted.
        Observation both = Screens.world(2, FLOOR, List.of(), List.of(),
                List.of(Screens.item(ItemKind.POTION, "crimson potion", 1), Screens.item(ItemKind.POTION, "amber potion", 1)),
                List.of());
        assertEquals(new Beliefs.Chapter("STRENGTH_POTIONS", "potion of strength", 0, 0, 2),
                after(both, drunk, back).chapters().get(0));
    }

    @Test
    @DisplayName("a memory with something in every list survives its bytes, and refuses a negative count")
    void round_trip() {
        Brain brain = brain();
        Belief belief = null;
        for (Observation screen : List.of(
                Screens.grid(3, 3, List.of(Tile.EMPTY, Tile.WATER, Tile.EMPTY, Tile.WATER, Tile.PEDESTAL,
                        Tile.WATER, Tile.EMPTY, Tile.WALL, Tile.EMPTY), List.of(new HeapView(4, HeapKind.CHEST, false, "", 0, ""))),
                Screens.world(3, FLOOR, List.of(), List.of(Screens.enemy("rat", 2)),
                        List.of(Screens.item(ItemKind.POTION, "potion of strength", 1), Screens.item(ItemKind.POTION, "amber potion", 1)),
                        List.of(new KnownAppearance(ItemKind.POTION, "potion of strength"))))) {
            belief = brain.update(screen, belief);
        }
        Memory memory = Memory.of(belief);
        for (List<?> list : List.of(memory.facts(), memory.found(), memory.held(), memory.known(), memory.labels(),
                memory.pending(), memory.monsters())) {
            assertTrue(!list.isEmpty(), memory.toString());
        }
        assertEquals(memory, Memory.of(memory.belief()));
        byte[] bytes = memory.belief().bytes();
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> Memory.of(new Belief(Memory.VERSION, java.util.Arrays.copyOf(bytes, bytes.length - 3))),
                "cut short inside the last record");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> new Memory.Found("x", 0, -1));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> new Memory.Seen("rat", 1, -2, 0));
    }

    @Test
    @DisplayName("an enemy seen and then lost is remembered where it was last, and marked stale")
    void remembered_monsters() {
        Observation seen = Screens.world(1, FLOOR, List.of(), List.of(Screens.enemy("rat", 2)), List.of(), List.of());
        Observation lost = Screens.world(1, FLOOR, List.of(), List.of(), List.of(), List.of());
        Observation back = Screens.world(1, FLOOR, List.of(), List.of(Screens.enemy("rat", 0)), List.of(), List.of());
        Observation below = Screens.world(2, FLOOR, List.of(), List.of(), List.of(), List.of());

        assertEquals(List.of(new Beliefs.Monster("rat", 2, 1, false)), after(seen).monsters());
        assertEquals(List.of(new Beliefs.Monster("rat", 2, 1, true)), after(seen, lost).monsters(),
                "the last known cell, and that it is stale");
        assertEquals(List.of(new Beliefs.Monster("rat", 0, 3, false)), after(seen, lost, back).monsters(),
                "seen again, the old sighting is replaced");
        assertEquals(List.of(), after(seen, lost, below).monsters(), "another floor's enemies are not this one's");
        assertEquals(List.of(new Beliefs.Monster("rat", 2, 1, true)), after(seen, lost, below, lost).monsters(),
                "and are still remembered on the way back");
    }

    @Test
    @DisplayName("two enemies of one name are two sightings, and the oldest are forgotten past the cap")
    void many_monsters() {
        Observation two = Screens.world(1, FLOOR, List.of(), List.of(Screens.enemy("rat", 0), Screens.enemy("rat", 2)),
                List.of(), List.of());
        Observation one = Screens.world(1, FLOOR, List.of(), List.of(Screens.enemy("rat", 2)), List.of(), List.of());
        assertEquals(List.of(new Beliefs.Monster("rat", 2, 2, false), new Beliefs.Monster("rat", 0, 1, true)),
                after(two, one).monsters(), "one rat in view accounts for one of the two remembered");

        List<Memory.Seen> many = new java.util.ArrayList<>();
        for (int i = 0; i < Memory.MONSTERS; i++) {
            many.add(new Memory.Seen("bat " + i, 1, 0, i));
        }
        List<Memory.Seen> after = Beliefs.sightings(many, one, 100);
        assertEquals(Memory.MONSTERS, after.size());
        assertEquals(new Memory.Seen("rat", 1, 2, 100), after.get(0));
        assertTrue(after.stream().noneMatch(seen -> seen.name().equals("bat " + (Memory.MONSTERS - 1))),
                "the last remembered is the first forgotten");
    }

    @Test
    @DisplayName("the Belief is a function of the screens: the same screens, the same bytes")
    void deterministic() {
        Observation seen = Screens.world(1, FLOOR, List.of(), List.of(Screens.enemy("rat", 2)),
                List.of(Screens.item(ItemKind.POTION, "potion of strength", 1)), List.of());
        Brain one = brain();
        Brain other = new Brain(Screens.CODEX, Screens.WEIGHTS, 99L);
        assertEquals(one.update(seen, one.update(seen, null)), other.update(seen, other.update(seen, null)),
                "the seed shapes choices, never beliefs");
    }
}
