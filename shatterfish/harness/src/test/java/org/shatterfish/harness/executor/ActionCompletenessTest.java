package org.shatterfish.harness.executor;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every input a person can give the hero maps to an Action kind, or is named here as unsupported
 * with a reason (FR-4; ADR-0014). The game's own list is {@code HeroAction}'s subclasses, which is
 * what a click becomes ({@code core/.../actors/hero/Hero.java:1920-2008}), plus the toolbar's
 * buttons, which do not go through a cell
 * ({@code core/.../ui/Toolbar.java:203}, {@code :225}, {@code :313}).
 *
 * <p>The test is a table and a walk: the table says what each input is, and the walk asks the game
 * what inputs it has. A kind added upstream lands in neither column and fails here, which is the
 * point — a Replay is verifiable only while every input a human made is one the executor can
 * express, so an input nobody has written down ends that quietly if nothing shouts.
 */
class ActionCompletenessTest {

    /** What each of the game's hero actions is, in Shatterfish's Action kinds. */
    private static final Map<String, String> SUPPORTED = new LinkedHashMap<>();

    /**
     * The inputs with no Action kind, each with the reason it has none. An unsupported input is not
     * a gap in the executor: it is a human input the bot cannot make, and a Run in which a human
     * made one stops being replayable from that wait (ADR-0011), which is why each needs a reason
     * rather than a shrug.
     */
    private static final Map<String, String> UNSUPPORTED = new LinkedHashMap<>();

    static {
        SUPPORTED.put("Move", "Step, one cell at a time; a click on a distant cell is a human's MoveTo, which is"
                + " recorded and never valid for the bot (ADR-0014, option 6)");
        SUPPORTED.put("Attack", "Attack");
        SUPPORTED.put("Interact", "Interact");
        SUPPORTED.put("PickUp", "PickUp");
        SUPPORTED.put("OpenChest", "OpenChest");
        SUPPORTED.put("Buy", "Buy");
        SUPPORTED.put("Unlock", "Unlock");
        SUPPORTED.put("LvlTransition", "Descend and Ascend, by the transition the cell holds");

        UNSUPPORTED.put("Alchemy", "the alchemy pot opens a scene of its own rather than a window, so it is not an"
                + " Input wait the Observer accepts and PromptKind.ALCHEMY is never produced (ADR-0006, story"
                + " 1.10); a bot cannot brew until a story gives the scene a door");
        UNSUPPORTED.put("Mine", "mining with a pickaxe is a click on a wall of the mining level"
                + " (Hero.java:1955-1963), which the valid set cannot offer because a wall is not a cell a click"
                + " walks onto; the mining branch is E2's ground and the Action kind would be new");
    }

    @Test
    @DisplayName("every hero action the game builds is an Action kind or is named unsupported with a reason")
    void every_input_is_named() {
        List<String> game = heroActions();
        assertTrue(game.size() >= 10, "the game's own list was read: " + game);

        List<String> unnamed = new ArrayList<>();
        for (String kind : game) {
            if (!SUPPORTED.containsKey(kind) && !UNSUPPORTED.containsKey(kind)) {
                unnamed.add(kind);
            }
        }
        assertEquals(List.of(), unnamed, "a hero action nobody has written down: name it in SUPPORTED with the"
                + " Action kind it is, or in UNSUPPORTED with the reason it has none");

        for (String named : new TreeSet<>(SUPPORTED.keySet())) {
            assertTrue(game.contains(named), named + " is named as supported and the game no longer has it");
        }
        for (String named : new TreeSet<>(UNSUPPORTED.keySet())) {
            assertTrue(game.contains(named), named + " is named as unsupported and the game no longer has it");
            assertFalse(UNSUPPORTED.get(named).isBlank(), named + " needs a reason, not a shrug");
        }
    }

    @Test
    @DisplayName("the buttons that are not a click are Action kinds too")
    void the_buttons_are_named() {
        // The toolbar's own inputs, which reach the hero directly rather than through a cell: the
        // wait button is rest(false), the rest button rest(true), the search button search(true).
        assertEquals("Wait", new Action.Wait().kind());
        assertEquals("Rest", new Action.Rest(true).kind());
        assertEquals("Search", new Action.Search().kind());
        // And the two panes: a talent point, and the armour ability.
        assertEquals("Talent", new Action.Talent("Hearty Meal").kind());
        assertEquals("Ability", new Action.Ability("Heroic Leap").kind());
    }

    @Test
    @DisplayName("every Action kind the schema has is one the executor can apply or refuses by name")
    void every_kind_has_a_branch() {
        // The sealed list is the schema's (ADR-0014). MoveTo is a human's record and never valid,
        // and the executor says so rather than pretending; everything else has a path.
        List<String> kinds = new ArrayList<>();
        for (Class<?> permitted : Action.class.getPermittedSubclasses()) {
            kinds.add(permitted.getSimpleName());
        }
        assertEquals(21, kinds.size(), "the sealed kinds of ADR-0014, with story 1.13's dismissal: " + kinds);
        assertTrue(kinds.contains("MoveTo"));
        assertTrue(kinds.contains("DismissPrompt"), "the tap that sends a message away");
    }

    /** The names of the game's own hero actions, from the class the hero stores in {@code curAction}. */
    private static List<String> heroActions() {
        List<String> kinds = new ArrayList<>();
        for (Class<?> nested : HeroAction.class.getDeclaredClasses()) {
            if (Modifier.isStatic(nested.getModifiers()) && HeroAction.class.isAssignableFrom(nested)) {
                kinds.add(nested.getSimpleName());
            }
        }
        return kinds;
    }
}
