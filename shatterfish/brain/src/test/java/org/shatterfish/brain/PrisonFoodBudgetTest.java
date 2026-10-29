package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.ItemKind;
import org.shatterfish.api.Observation;
import org.shatterfish.api.Playbook;
import org.shatterfish.api.Tile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prison food budgeting (story 10.15, issue #361).
 *
 * <p>Food is one ration a regular floor, none on a boss floor (Level.java:224-226), and a hit point
 * rested costs ten turns of it (docs/rules/buffs.md); the Rig's debug records show the heroes that starve
 * by depth 6 spent their first floors resting from a scratch, not from a wound. The rest after a test
 * therefore begins only under the Playbook's {@code rest_hp_per_mille}. The numbers the arithmetic
 * stands on are read from the Codex rules page, not typed here.
 */
class PrisonFoodBudgetTest {

    /** The Playbook the shipped Brain plays: the reference with the rest budget the story measured and kept. */
    private static final Playbook.Identify BUDGET = Playbook.Identify.REFERENCE.with(Map.of("rest_hp_per_mille", 300L));

    private static TestItem policy(Playbook.Identify identify) {
        return new TestItem(Screens.CODEX, new Playbook("shatterfish", 1, identify, Playbook.Upgrade.REFERENCE, List.of()));
    }

    private static Observation calm(int hp, int ht) {
        List<Tile> tiles = new ArrayList<>(Collections.nCopies(3, Tile.EMPTY));
        List<Action> offered = new ArrayList<>(List.of(new Action.Step(0), new Action.Step(2)));
        offered.addAll(List.of(new Action.Rest(true), new Action.Search()));
        return Screens.lab(1, 3, tiles, List.of(), Screens.heroAt(1, hp, ht, List.of()),
                List.of(Screens.item(ItemKind.FOOD, "ration of food", 1)), List.of(), List.of(), offered.toArray(Action[]::new));
    }

    private static Memory justTested() {
        return new Memory(3, 1, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                Memory.Spot.NOWHERE, 0, true, List.of(), List.of(), "", 0, -1, -1, List.of(), List.of(), "", List.of(),
                Memory.Pack.NONE, Memory.Aim.NONE, -1, Memory.Trial.NONE, List.of(), 0, 0, List.of(), -1);
    }

    @Test
    @DisplayName("a scratch after a test is not rested off on the ration; a deep wound is")
    void scratch_is_not_rested_off() {
        Observation scratched = calm(12, 20);
        Observation wounded = calm(5, 20);
        assertTrue(policy(Playbook.Identify.REFERENCE).enters(scratched, justTested()),
                "control: the old Policy rests at 60 percent health, a hundred turns of food for eight hit points");
        assertFalse(policy(BUDGET).enters(scratched, justTested()), "the budget keeps exploring at 60 percent");
        assertTrue(policy(BUDGET).enters(wounded, justTested()), "and rests at 25 percent");
        TestItem.Plan plan = policy(BUDGET).plan(wounded, justTested(), wounded.actions().actions());
        assertEquals(new Action.Rest(true), plan.choice().action());
        assertEquals("rest: after-test (rest_waits)", plan.choice().why(), "the same CoachLine sentence as before");
    }

    @Test
    @DisplayName("the budget is exactly the line: at and under it rests, over it does not")
    void the_line() {
        assertTrue(BUDGET.restHp(30, 100));
        assertFalse(BUDGET.restHp(31, 100));
        assertTrue(Playbook.Identify.REFERENCE.restHp(99, 100), "the reference rests whenever hurt, as before");
    }

    @Test
    @DisplayName("the shipped Playbook states the budget, and the Codex rules page states the clock the budget is spent against")
    void numbers_come_from_the_codex() throws IOException {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("docs/rules/buffs.md"))) {
            root = root.getParent();
        }
        assertTrue(root != null, "the repository root holds docs/rules/buffs.md");
        String buffs = Files.readString(root.resolve("docs/rules/buffs.md"), StandardCharsets.UTF_8);
        assertTrue(buffs.contains("at 300 the hero is \"hungry\""), "hungry at 300");
        assertTrue(buffs.contains("at 450 \"starving\""), "starving at 450");
        assertTrue(buffs.contains("Regeneration is suspended"), "no regeneration while starving");
        assertTrue(buffs.contains("Searching costs 6 hunger per 2-turn search"), "a search");
        // A ration is 300 turns (Food.java), a floor yields one, so a hit point at ten turns costs 3.3 percent of a floor's food.
        assertEquals(10, Larder.clock(0, "Rest", false, 1, 0, Integer.MAX_VALUE, false, org.shatterfish.api.Hunger.NONE),
                "a hit point rested: ten turns (the Larder's clock, LarderTest.clock)");
        String playbook = Files.readString(root.resolve("playbooks/shatterfish.json"), StandardCharsets.UTF_8);
        assertTrue(playbook.contains("\"rest_hp_per_mille\":300"), "the shipped Playbook states the measured budget");
        assertTrue(Files.readString(root.resolve("playbooks/oneply.json"), StandardCharsets.UTF_8)
                .contains("\"rest_hp_per_mille\":1000"), "the reference Playbook keeps the old rest");
    }
}
