package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.ActionsSection;
import org.shatterfish.api.Belief;
import org.shatterfish.api.HeaderSection;
import org.shatterfish.api.HeroClass;
import org.shatterfish.api.Observation;
import org.shatterfish.api.PromptKind;
import org.shatterfish.api.PromptSection;
import org.shatterfish.api.Tile;
import org.shatterfish.api.ValidActions;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #170: the Brain never steps onto a chasm it can see, and a chasm's question it did not ask for
 * is answered "No, I changed my mind". A jump takes the hero down a floor, crippled, bleeding and hurt
 * ({@code core/.../levels/features/Chasm.java:99-121}, {@code :131-152}), and the question's "yes" was
 * the button the general rule pressed, being the lowest.
 */
class ChasmTest {

    /** The chasm's buttons as the game labels them ({@code core/src/main/assets/messages/levels/levels.properties:3-4}). */
    private static final List<String> BUTTONS = List.of("Yes, I know what I'm doing", "No, I changed my mind");

    private static Brain brain() {
        return new Brain(FightPolicyTest.KNOWLEDGE, Screens.WEIGHTS, 9L);
    }

    /** Every Action the Brain hands over across {@code waits} waits of {@code screen}, driven as its driver drives it. */
    private static List<Brain.Decided> played(Observation screen, int waits) {
        Brain brain = brain();
        Belief belief = null;
        List<Brain.Decided> all = new ArrayList<>();
        for (int wait = 0; wait < waits; wait++) {
            belief = brain.update(screen, belief);
            Brain.Decided decided = brain.decide(screen, belief);
            all.add(decided);
            belief = brain.handed(screen, belief, decided);
        }
        return all;
    }

    private static boolean ontoAChasm(Observation screen, Action action) {
        return action instanceof Action.Step step && screen.map().tiles().get(step.cell()) == Tile.CHASM;
    }

    @Test
    @DisplayName("no Policy steps onto a chasm in view: not the fallback, not a fight's chokepoint, not a retreat")
    void never_steps_onto_a_chasm() {
        List<Observation> screens = List.of(
                // Nothing to explore and nowhere to go but the two chasms: the fallback's menu.
                FightPolicyTest.screen(20, false,
                        "#####",
                        "#c@c#",
                        "#####"),
                // Two rats coming across a room, and a chasm in a nook that only two cells can reach.
                FightPolicyTest.screen(20, false,
                        "#######",
                        "#c#####",
                        "#.@...#",
                        "#.....#",
                        "#...r.#",
                        "#..r..#",
                        "#######"),
                // Hurt, a brute beside, and chasms on the far side.
                FightPolicyTest.screen(4, false,
                        "#######",
                        "#cc...#",
                        "#c@B..#",
                        "#cc...#",
                        "#######"));
        for (Observation screen : screens) {
            assertTrue(screen.actions().actions().stream().anyMatch(action -> ontoAChasm(screen, action)),
                    "the screen offers a Step onto a chasm, as ValidActions offers one");
            for (Brain.Decided decided : played(screen, 12)) {
                assertFalse(ontoAChasm(screen, decided.action()), "stepped onto a chasm: "
                        + (decided.decision() == null ? decided.action() : decided.decision()));
            }
        }
    }

    /** The chasm's question open over {@code screen}, offering its two answers. */
    private static Observation asked(Observation screen) {
        PromptSection prompt = new PromptSection(PromptKind.CHASM_JUMP, "Chasm",
                "Do you really want to jump into the chasm? A fall that far will be painful.", BUTTONS);
        HeaderSection h = screen.header();
        HeaderSection header = new HeaderSection(h.version(), h.upstreamTag(), h.codexVersion(), h.heroClass(),
                h.challenges(), h.depth(), h.branch(), h.sealed(), h.oracle(), PromptKind.CHASM_JUMP);
        Observation open = new Observation(header, screen.map(),
                screen.actors(), screen.hero(), screen.inventory(), screen.journal(), screen.log(), ActionsSection.NONE,
                prompt);
        return open.withActions(ValidActions.of(open));
    }

    @Test
    @DisplayName("a chasm's question the Brain did not ask for is answered no, by the game's own words")
    void an_unasked_jump_is_declined() {
        Observation floor = FightPolicyTest.screen(20, false,
                "#####",
                "#.@c#",
                "#####");
        Observation question = asked(floor);
        Brain brain = brain();
        // Asked cold, and asked after a Step onto a floor cell: both are questions the Brain did not ask.
        Brain.Decided cold = brain.decide(question, brain.update(question, null));
        assertEquals(new Action.AnswerPrompt(1), cold.action(), cold.decision().toString());
        assertEquals("decline: No, I changed my mind", cold.decision().chosen().why());

        Belief belief = brain.update(floor, null);
        belief = brain.handed(floor, belief, new Brain.Decided(new Action.Step(floor.hero().cell() - 1), null, List.of(), ""));
        belief = brain.update(question, belief);
        assertEquals(new Action.AnswerPrompt(1), brain.decide(question, belief).action());
    }

    @Test
    @DisplayName("a jump the Brain meant, a Step onto a chasm it handed over, is answered yes")
    void a_meant_jump_is_affirmed() {
        Observation floor = FightPolicyTest.screen(20, false,
                "#####",
                "#.@c#",
                "#####");
        int chasm = floor.hero().cell() + 1;
        Observation question = asked(floor);
        Brain brain = brain();
        Belief belief = brain.update(floor, null);
        belief = brain.handed(floor, belief, new Brain.Decided(new Action.Step(chasm), null, List.of(), ""));
        belief = brain.update(question, belief);
        Brain.Decided decided = brain.decide(question, belief);
        assertEquals(new Action.AnswerPrompt(0), decided.action(), decided.decision().toString());
        assertEquals("jump: Yes, I know what I'm doing", decided.decision().chosen().why());
        assertTrue(decided.decision().alternatives().stream().anyMatch(alt -> alt.action().equals(new Action.AnswerPrompt(1))),
                "no is the alternative");
    }

    @Test
    @DisplayName("the answers the Brain means elsewhere still stand: the seal's yes, the scroll cancel's yes, the subclass")
    void meant_answers_still_stand() {
        Brain brain = brain();
        Observation seal = Screens.titled("Broken Seal", "The Warrior's broken seal must be affixed", List.of("yes", "no"),
                new Action.AnswerPrompt(0), new Action.AnswerPrompt(1));
        assertEquals(new Action.AnswerPrompt(0), brain.decide(seal, brain.update(seal, null)).action());
        Observation cancel = Screens.asked(HeroClass.WARRIOR, PromptKind.ITEM, "Scroll of Upgrade", Answers.SCROLL_CANCEL,
                List.of("Yes, I'm positive", "No, I changed my mind"), List.of());
        assertEquals(new Action.AnswerPrompt(0), brain.decide(cancel, brain.update(cancel, null)).action());
        Observation subclass = Screens.asked(HeroClass.WARRIOR, PromptKind.SUBCLASS, "Berserker",
                List.of("Yes, I've made my choice.", "No, I'll decide later."), List.of());
        assertEquals(new Action.AnswerPrompt(0), brain.decide(subclass, brain.update(subclass, null)).action());
    }
}
