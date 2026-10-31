package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.Belief;
import org.shatterfish.api.Codex;
import org.shatterfish.api.ItemKind;
import org.shatterfish.api.ItemRef;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.KnownAppearance;
import org.shatterfish.api.Observation;
import org.shatterfish.api.Tile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What an appearance is, from how often it has turned up and on which floors (issue #410). The guarantee
 * schedule is Dungeon.java:529-554 and the decks Generator.java:329-377; the cases here are the owner's:
 * "it should know which are scrolls of upgrade after it gets one per level or every other level".
 */
class IdentityFromCountsTest {

    private static final List<Tile> FLOOR = List.of(Tile.EMPTY, Tile.EMPTY, Tile.EMPTY);

    /** The scroll family as the real tables give it, shortened: identify is known from the start. */
    static final Codex.Knowledge SCROLLS = new Codex.Knowledge(Screens.MANIFEST,
            List.of(new Codex.Identities(ItemKind.SCROLL,
                            List.of("scroll of AAA", "scroll of BBB", "scroll of CCC", "scroll of DDD", "scroll of EEE"),
                            List.of(new Codex.Candidate("items.scrolls.ScrollOfUpgrade", "scroll of upgrade", 0),
                                    new Codex.Candidate("items.scrolls.ScrollOfIdentify", "scroll of identify", 6),
                                    new Codex.Candidate("items.scrolls.ScrollOfRemoveCurse", "scroll of remove curse", 4),
                                    new Codex.Candidate("items.scrolls.ScrollOfRecharging", "scroll of recharging", 3),
                                    new Codex.Candidate("items.scrolls.ScrollOfTeleportation", "scroll of teleportation", 3)))),
            List.of(), List.of(new Codex.Guarantee("UPGRADE_SCROLLS", "items.scrolls.ScrollOfUpgrade", "scroll of upgrade", 3, 5)));

    private static final List<KnownAppearance> IDENTIFY_KNOWN = List.of(new KnownAppearance(ItemKind.SCROLL, "scroll of identify"));

    /** The screen at {@code depth} holding {@code quantity} of the AAA scroll, identify known. */
    private static Observation holding(int depth, String label, int quantity) {
        List<ItemView> items = quantity == 0 ? List.of() : List.of(Screens.item(ItemKind.SCROLL, label, quantity));
        return Screens.world(depth, FLOOR, List.of(), List.of(), items, IDENTIFY_KNOWN);
    }

    private static Beliefs after(Codex.Knowledge knowledge, Observation... screens) {
        Brain brain = new Brain(knowledge, Screens.WEIGHTS, 3L);
        Belief belief = null;
        for (Observation screen : screens) {
            belief = brain.update(screen, belief);
        }
        return brain.beliefs(screens[screens.length - 1], belief);
    }

    private static double odds(Beliefs beliefs, String label, String name) {
        return beliefs.identities().stream().filter(guess -> guess.label().equals(label)).findFirst().orElseThrow()
                .odds().stream().filter(one -> one.name().equals(name)).mapToDouble(Beliefs.Odds::probability).sum();
    }

    @Test
    @DisplayName("one appearance found on floors 1, 2 and 4 is the scroll of upgrade, with odds above 0.8 (fails on main)")
    void three_finds_in_a_set_are_upgrade() {
        // Held 1 on floor 1, picked up another on floor 2, held through 3, a third on floor 4: three of the
        // four floors of the first set, the guarantee's three (Dungeon.java:548-553).
        Beliefs beliefs = after(SCROLLS, holding(1, "scroll of AAA", 1), holding(2, "scroll of AAA", 2),
                holding(3, "scroll of AAA", 2), holding(4, "scroll of AAA", 3));
        assertTrue(odds(beliefs, "scroll of AAA", "scroll of upgrade") > 0.8,
                "upgrade odds " + odds(beliefs, "scroll of AAA", "scroll of upgrade"));
    }

    @Test
    @DisplayName("a control: the same appearance found once is less likely upgrade than found three times")
    void one_find_is_less() {
        Beliefs once = after(SCROLLS, holding(1, "scroll of AAA", 1), holding(2, "scroll of AAA", 1),
                holding(3, "scroll of AAA", 1), holding(4, "scroll of AAA", 1));
        Beliefs thrice = after(SCROLLS, holding(1, "scroll of AAA", 1), holding(2, "scroll of AAA", 2),
                holding(3, "scroll of AAA", 2), holding(4, "scroll of AAA", 3));
        assertTrue(odds(once, "scroll of AAA", "scroll of upgrade") < odds(thrice, "scroll of AAA", "scroll of upgrade") - 0.05,
                "once " + odds(once, "scroll of AAA", "scroll of upgrade") + ", thrice " + odds(thrice, "scroll of AAA", "scroll of upgrade"));
    }

    @Test
    @DisplayName("every other floor is enough too: finds on floors 2, 4 and 7 give upgrade over one half")
    void every_other_floor() {
        List<Observation> screens = new ArrayList<>();
        int held = 0;
        for (int depth = 1; depth <= 7; depth++) {
            if (depth == 2 || depth == 4 || depth == 7) {
                held++;
            }
            screens.add(holding(depth, "scroll of AAA", held));
        }
        Beliefs beliefs = after(SCROLLS, screens.toArray(Observation[]::new));
        assertTrue(odds(beliefs, "scroll of AAA", "scroll of upgrade") > 0.5,
                "upgrade odds " + odds(beliefs, "scroll of AAA", "scroll of upgrade"));
    }

    @Test
    @DisplayName("the odds sum to one, and an appearance seen once is a deck identity by weight")
    void a_distribution() {
        Beliefs beliefs = after(SCROLLS, holding(1, "scroll of BBB", 1));
        Beliefs.Guess guess = beliefs.identities().get(0);
        assertEquals(1.0, guess.odds().stream().mapToDouble(Beliefs.Odds::probability).sum(), 1e-9);
        assertTrue(odds(beliefs, "scroll of BBB", "scroll of identify") == 0, "identify is identified: not a candidate");
        assertTrue(odds(beliefs, "scroll of BBB", "scroll of remove curse") > odds(beliefs, "scroll of BBB", "scroll of recharging"),
                "remove curse (weight 4) over recharging (3)");
    }

    @Test
    @DisplayName("Memory counts the finds of an appearance by floor, and holding one is not finding it again")
    void memory_counts_by_floor() {
        Brain brain = new Brain(SCROLLS, Screens.WEIGHTS, 3L);
        Belief belief = null;
        for (Observation screen : List.of(holding(1, "scroll of AAA", 1), holding(1, "scroll of AAA", 1),
                holding(2, "scroll of AAA", 3), holding(2, "scroll of AAA", 2), holding(3, "scroll of AAA", 2))) {
            belief = brain.update(screen, belief);
        }
        Memory memory = Memory.of(belief);
        assertEquals(1, Memory.count(memory.found(), Beliefs.SIGHTING + "scroll of AAA", 1));
        assertEquals(2, Memory.count(memory.found(), Beliefs.SIGHTING + "scroll of AAA", 2), "two picked up, one read: still two found");
        assertEquals(0, Memory.count(memory.found(), Beliefs.SIGHTING + "scroll of AAA", 3));
    }

    // ---- what the Policy does with a known identify scroll

    private static final Codex.Knowledge POTIONS = new Codex.Knowledge(Screens.MANIFEST,
            List.of(new Codex.Identities(ItemKind.POTION, List.of("crimson potion", "amber potion"),
                    List.of(new Codex.Candidate("items.potions.PotionOfHealing", "potion of healing", 1),
                            new Codex.Candidate("items.potions.PotionOfFrost", "potion of frost", 1)))),
            List.of(), List.of());

    private static Observation pack(List<ItemView> items, Action... use) {
        List<Tile> tiles = new ArrayList<>(Collections.nCopies(3, Tile.EMPTY));
        List<Action> offered = new ArrayList<>(List.of(new Action.Step(0), new Action.Step(2)));
        offered.addAll(List.of(use));
        return Screens.lab(1, 3, tiles, List.of(), Screens.heroAt(1, 20, 20, List.of()), items, List.of(), List.of(),
                offered.toArray(Action[]::new));
    }

    @Test
    @DisplayName("a held scroll of identify is read onto the unknown potion before the potion is drunk")
    void identify_goes_onto_the_potion() {
        ItemView identify = Screens.using("scroll of identify", "READ");
        ItemView potion = Screens.unknown(ItemKind.POTION, "crimson potion", 1);
        ItemRef scroll = new ItemRef(0, "scroll of identify", 1);
        ItemRef target = new ItemRef(1, "crimson potion", 1);
        Observation screen = pack(List.of(identify, potion), new Action.UseItemOn(scroll, "READ", target),
                new Action.UseItem(target, "DRINK"));
        TestItem policy = new TestItem(POTIONS);
        TestItem.Plan plan = policy.plan(screen, Memory.START, screen.actions().actions());
        assertNotNull(plan);
        assertEquals(new Action.UseItemOn(scroll, "READ", target), plan.choice().action());
        assertEquals("identify: crimson potion (identify-held)", plan.choice().why());
        assertTrue(policy.enters(screen, Memory.START));
    }

    @Test
    @DisplayName("with no identify scroll held the potion is not read onto, and a blinded hero does not try")
    void no_scroll_no_read() {
        ItemView potion = Screens.unknown(ItemKind.POTION, "crimson potion", 1);
        ItemRef target = new ItemRef(0, "crimson potion", 1);
        Observation screen = pack(List.of(potion), new Action.UseItem(target, "DRINK"));
        TestItem policy = new TestItem(POTIONS);
        assertNull(policy.identifying(screen, Memory.START, screen.actions().actions()));
    }
}
