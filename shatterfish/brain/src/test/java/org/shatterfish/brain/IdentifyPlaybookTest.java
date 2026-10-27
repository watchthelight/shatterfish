package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.Codex;
import org.shatterfish.api.ItemKind;
import org.shatterfish.api.ItemRef;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.Observation;
import org.shatterfish.api.Playbook;
import org.shatterfish.api.Tile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code identify} section of the Playbook decides which unknown potions and scrolls are tried and at
 * what health (story 7.3): each field, moved off its reference value, changes the Decision on a constructed
 * screen it is meant to; a Playbook equal to the reference reproduces the old Decision; and the strategy log's
 * reason names the field that let the test happen.
 */
class IdentifyPlaybookTest {

    /** Crimson potions that are healing one time in two and mind vision the rest: never strength or experience. */
    private static final Codex.Knowledge HALF_HEALING = new Codex.Knowledge(Screens.MANIFEST,
            List.of(new Codex.Identities(ItemKind.POTION, List.of("crimson potion"),
                    List.of(new Codex.Candidate("items.potions.PotionOfHealing", "potion of healing", 1),
                            new Codex.Candidate("items.potions.PotionOfMindVision", "potion of mind vision", 1)))),
            List.of(), List.of());

    /** Healing one time in ten: under the reference 200 per mille. */
    private static final Codex.Knowledge TENTH_HEALING = new Codex.Knowledge(Screens.MANIFEST,
            List.of(new Codex.Identities(ItemKind.POTION, List.of("crimson potion"),
                    List.of(new Codex.Candidate("items.potions.PotionOfHealing", "potion of healing", 1),
                            new Codex.Candidate("items.potions.PotionOfMindVision", "potion of mind vision", 9)))),
            List.of(), List.of());

    /** Strength one time in four: over the reference 200 per mille gain odds. */
    private static final Codex.Knowledge QUARTER_STRENGTH = new Codex.Knowledge(Screens.MANIFEST,
            List.of(new Codex.Identities(ItemKind.POTION, List.of("crimson potion"),
                    List.of(new Codex.Candidate("items.potions.PotionOfStrength", "potion of strength", 1),
                            new Codex.Candidate("items.potions.PotionOfMindVision", "potion of mind vision", 3)))),
            List.of(), List.of());

    /** Identify one time in seven (under the reference 250 per mille), magic mapping and teleportation the rest. */
    private static final Codex.Knowledge SCROLLS = new Codex.Knowledge(Screens.MANIFEST,
            List.of(new Codex.Identities(ItemKind.SCROLL, List.of("scroll of KAUNAN"),
                    List.of(new Codex.Candidate("items.scrolls.ScrollOfIdentify", "scroll of identify", 1),
                            new Codex.Candidate("items.scrolls.ScrollOfMagicMapping", "scroll of magic mapping", 3),
                            new Codex.Candidate("items.scrolls.ScrollOfTeleportation", "scroll of teleportation", 3)))),
            List.of(), List.of());

    private static final ItemView CRIMSON = Screens.unknown(ItemKind.POTION, "crimson potion", 1);
    private static final ItemView KAUNAN = Screens.unknown(ItemKind.SCROLL, "scroll of KAUNAN", 1);

    private static Playbook.Identify with(Object... pairs) {
        Map<String, Object> given = new java.util.LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            given.put((String) pairs[i], pairs[i + 1]);
        }
        return Playbook.Identify.REFERENCE.with(given);
    }

    private static TestItem policy(Codex.Knowledge knowledge, Playbook.Identify identify) {
        return new TestItem(knowledge, new Playbook("shatterfish", 1, identify, Playbook.Upgrade.REFERENCE, List.of()));
    }

    /** A three-cell floor, the hero in the middle at {@code hp} of 20, holding {@code item} and offered {@code use}. */
    private static Observation screen(ItemView item, int hp, Action use) {
        List<Tile> tiles = new ArrayList<>(Collections.nCopies(3, Tile.EMPTY));
        List<Action> offered = new ArrayList<>(List.of(new Action.Step(0), new Action.Step(2), use));
        return Screens.lab(1, 3, tiles, List.of(), Screens.heroAt(1, hp, 20, List.of()), List.of(item), List.of(),
                List.of(), offered.toArray(Action[]::new));
    }

    private static Action drink() {
        return new Action.UseItem(new ItemRef(0, "crimson potion", 1), TestItem.DRINK);
    }

    private static Action read() {
        return new Action.UseItem(new ItemRef(0, "scroll of KAUNAN", 1), TestItem.READ);
    }

    private static String why(TestItem policy, Observation screen) {
        TestItem.Plan plan = policy.plan(screen, Memory.START, screen.actions().actions());
        return plan == null ? null : plan.choice().why();
    }

    @Test
    @DisplayName("the reference values reproduce the old Decisions: a potion at half health, a scroll at full, and the reason names the field")
    void the_reference_is_the_old_play() {
        TestItem reference = policy(HALF_HEALING, Playbook.Identify.REFERENCE);
        assertEquals("test: crimson potion (healing_odds_per_mille)", why(reference, screen(CRIMSON, 10, drink())));
        assertNull(why(reference, screen(CRIMSON, 11, drink())), "11 of 20 is over half");
        TestItem scrolls = policy(SCROLLS, Playbook.Identify.REFERENCE);
        assertEquals("test: scroll of KAUNAN (inventory_odds_per_mille)", why(scrolls, screen(KAUNAN, 20, read())));
        assertNull(why(scrolls, screen(KAUNAN, 19, read())), "19 of 20 is under full");
        // The default constructor is the reference Playbook.
        assertEquals(why(reference, screen(CRIMSON, 10, drink())),
                why(new TestItem(HALF_HEALING), screen(CRIMSON, 10, drink())));
    }

    @Test
    @DisplayName("potion_hp_per_mille moves the health an unknown potion is drunk at for its healing odds")
    void potion_hp() {
        Observation fourteen = screen(CRIMSON, 14, drink());
        Observation six = screen(CRIMSON, 6, drink());
        assertNull(why(policy(HALF_HEALING, Playbook.Identify.REFERENCE), fourteen));
        assertEquals("test: crimson potion (healing_odds_per_mille)", why(policy(HALF_HEALING, with("potion_hp_per_mille", 700L)), fourteen));
        assertNull(why(policy(HALF_HEALING, with("potion_hp_per_mille", 700L)), screen(CRIMSON, 15, drink())), "15 of 20 is over 700 per mille");
        // Lower, the potion waits for worse health: 6 of 20 is 300 per mille, so a ceiling of 250 refuses it.
        assertNotNull(why(policy(HALF_HEALING, Playbook.Identify.REFERENCE), six));
        assertNull(why(policy(HALF_HEALING, with("potion_hp_per_mille", 250L)), six));
    }

    @Test
    @DisplayName("healing_odds_per_mille moves how likely healing must be for the hurt hero to drink")
    void healing_odds() {
        Observation hurt = screen(CRIMSON, 10, drink());
        assertNull(why(policy(TENTH_HEALING, Playbook.Identify.REFERENCE), hurt), "one in ten is under 200 per mille");
        assertEquals("test: crimson potion (healing_odds_per_mille)", why(policy(TENTH_HEALING, with("healing_odds_per_mille", 100L)), hurt));
        assertNull(why(policy(HALF_HEALING, with("healing_odds_per_mille", 600L)), hurt), "one in two is under 600 per mille");
    }

    @Test
    @DisplayName("gain_odds_per_mille moves how likely strength or experience must be for a drink at any health")
    void gain_odds() {
        Observation whole = screen(CRIMSON, 20, drink());
        assertEquals("test: crimson potion (gain_odds_per_mille)", why(policy(QUARTER_STRENGTH, Playbook.Identify.REFERENCE), whole));
        assertNull(why(policy(QUARTER_STRENGTH, with("gain_odds_per_mille", 300L)), whole), "one in four is under 300 per mille");
    }

    @Test
    @DisplayName("scroll_hp_per_mille moves the health an unknown scroll is read at, and inventory_odds_per_mille which scrolls")
    void scrolls() {
        Observation ten = screen(KAUNAN, 10, read());
        assertNull(why(policy(SCROLLS, Playbook.Identify.REFERENCE), ten));
        assertEquals("test: scroll of KAUNAN (inventory_odds_per_mille)", why(policy(SCROLLS, with("scroll_hp_per_mille", 500L)), ten));
        assertNull(why(policy(SCROLLS, with("scroll_hp_per_mille", 500L)), screen(KAUNAN, 9, read())), "9 of 20 is under 500 per mille");
        assertNull(why(policy(SCROLLS, with("inventory_odds_per_mille", 100L)), screen(KAUNAN, 20, read())),
                "identify one time in seven is over 100 per mille");
    }

    @Test
    @DisplayName("a potion_hp_per_mille of 1000 lets the hero drink an unknown potion for its healing odds at full health")
    void potion_hp_at_full_health() {
        Playbook.Identify eager = with("potion_hp_per_mille", 1000L);
        Observation whole = screen(CRIMSON, 20, drink());
        assertFalse(policy(HALF_HEALING, Playbook.Identify.REFERENCE).enters(whole, Memory.START));
        assertTrue(policy(HALF_HEALING, eager).enters(whole, Memory.START));
    }
}
