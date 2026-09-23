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
        return new Brain(Screens.CODEX, 3L);
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
    @DisplayName("each unidentified appearance in view carries a distribution over its family's identities")
    void a_distribution() {
        Beliefs beliefs = after(Screens.world(1, FLOOR, List.of(new HeapView(2, HeapKind.HEAP, false, "amber potion", 0, "")),
                List.of(), List.of(Screens.item(ItemKind.POTION, "crimson potion", 2)), List.of()));

        assertEquals(List.of("crimson potion", "amber potion"),
                beliefs.identities().stream().map(Beliefs.Guess::label).toList(), "the backpack's and the floor's");
        Beliefs.Guess crimson = guess(beliefs, "crimson potion");
        assertEquals(1.0, sum(crimson), 1e-12);
        assertEquals(4, crimson.odds().size(), "strength is a candidate though the decks weight it zero");
        // Healing 6, strength weighted as the commonest (6), mind vision 4, frost 3: of 19.
        assertEquals("potion of healing", crimson.odds().get(0).name());
        assertEquals(6.0 / 19, crimson.odds().get(0).probability(), 1e-12);
        assertEquals(6.0 / 19, crimson.odds().get(1).probability(), 1e-12);
        assertEquals("potion of frost", crimson.odds().get(3).name());
        assertEquals(3.0 / 19, crimson.odds().get(3).probability(), 1e-12);
        assertTrue(beliefs.identities().stream().noneMatch(guess -> guess.label().equals("golden potion")),
                "an appearance not in view is not guessed at");
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
        assertEquals(6.0 / 13, guess(beliefs, "amber potion").odds().get(0).probability(), 1e-12);
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
    @DisplayName("the Belief is a function of the screens: the same screens, the same bytes")
    void deterministic() {
        Observation seen = Screens.world(1, FLOOR, List.of(), List.of(Screens.enemy("rat", 2)),
                List.of(Screens.item(ItemKind.POTION, "potion of strength", 1)), List.of());
        Brain one = brain();
        Brain other = new Brain(Screens.CODEX, 99L);
        assertEquals(one.update(seen, one.update(seen, null)), other.update(seen, other.update(seen, null)),
                "the seed shapes choices, never beliefs");
    }
}
