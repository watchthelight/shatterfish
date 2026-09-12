package org.shatterfish.harness.executor;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Action;
import org.shatterfish.api.ItemRef;
import org.shatterfish.api.Observation;
import org.shatterfish.api.PromptKind;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.driver.Prompts;
import org.shatterfish.harness.driver.Windows;
import org.shatterfish.harness.observer.Observer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Over states the driver walks into, the two halves of ADR-0014's contract hold: an Action the
 * Observation offers is applied, and one it does not offer is refused with the game untouched. The
 * second half is the one that matters for parity — a rejection must cost nothing, or a Brain could
 * learn from what a refusal changed.
 *
 * <p>The states are reached by playing: the test takes an Action from the set at each wait, which
 * is story 1.14's agent in miniature, and asks the two questions at every one of them.
 */
@Timeout(value = 10, unit = TimeUnit.MINUTES)
class ActionValidityPropertyTest {

    private static final long SEED = 99_001_122L;

    /** Waits to walk through. Enough to leave the first room and meet the floor's own furniture. */
    private static final int WAITS = 40;

    private HeadlessDriver driver;
    private final ActionExecutor executor = new ActionExecutor();

    @AfterEach
    void endTheRun() {
        if (driver != null) {
            driver.close();
            driver = null;
        }
    }

    @Test
    @DisplayName("over forty waits, an offered Action is applied and an unoffered one changes nothing")
    void the_two_halves_hold() {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR);
        driver.stepToInputWait();
        Random choices = new Random(SEED);

        int applied = 0;
        int refused = 0;
        int messages = 0;
        for (int wait = 0; wait < WAITS && Dungeon.hero.isAlive(); wait++) {
            Observation observation = new Observer().observe();
            List<Action> offered = observation.actions().actions();
            assertTrue(!offered.isEmpty(), "a wait with nothing to do at wait " + wait);

            // An Action the screen does not offer changes nothing at all.
            String before = observation.hash();
            for (Action stranger : strangers(observation)) {
                Outcome outcome = executor.execute(observation, stranger);
                Outcome.Rejected rejected = assertInstanceOf(Outcome.Rejected.class, outcome,
                        stranger + " at wait " + wait);
                assertTrue(rejected.reason() == Reason.NOT_OFFERED || rejected.reason() == Reason.ITEM_MOVED,
                        "a stranger is refused for being one: " + rejected);
                assertEquals(before, new Observer().observe().hash(),
                        "a refusal changed the screen at wait " + wait + ": " + rejected);
                refused++;
            }

            // And one the screen does offer is applied, whatever it is. A message in front offers
            // exactly one thing, the dismissal, which is how a Run gets past a sign (story 1.13).
            if (observation.prompt().kind() == PromptKind.MESSAGE) {
                assertEquals(List.of(new Action.DismissPrompt()), offered, "a message offers one Action");
                messages++;
            }
            Action chosen = offered.get(choices.nextInt(offered.size()));
            Outcome outcome = executor.execute(observation, chosen);
            assertInstanceOf(Outcome.Applied.class, outcome, chosen + " at wait " + wait + ": " + outcome);
            applied++;
            try {
                driver.stepToInputWait();
            } catch (HeadlessDriver.Stalled stalled) {
                // A stall is a failure now. Story 1.13 found the one wall a Run used to hit here —
                // an ordinary step onto a sign leaves a message the driver would not call a wait —
                // and the answer was to make a message a Prompt with one Action, the dismissal. If
                // this fires again, a window of some other kind is in front and nobody can move it.
                Window front = Windows.front();
                throw new AssertionError("the Run stalled at wait " + wait + " under "
                        + (front == null ? "no window" : front.getClass().getSimpleName() + ", a "
                                + Prompts.kind(front) + " prompt") + ": " + stalled.getMessage(), stalled);
            }
        }

        assertTrue(applied >= WAITS - 1, "the Run went the whole way: " + applied + " Actions applied");
        assertTrue(refused >= applied, "and refusals were asked for at every wait: " + refused);
        assertTrue(messages > 0 || applied == WAITS,
                "the Run either met a message and sent it away, or never met one: " + messages);
    }

    /**
     * Actions this Observation does not offer, built from its own values so that the record will
     * hold them: a step onto the hero's own cell, an answer to a Prompt that is not open, and an
     * item reference naming something the pack does not hold there.
     */
    private static List<Action> strangers(Observation observation) {
        List<Action> strangers = new ArrayList<>();
        strangers.add(new Action.Step(observation.hero().cell()));
        if (observation.prompt().options().isEmpty()) {
            strangers.add(new Action.AnswerPrompt(0));
        }
        if (!observation.inventory().items().isEmpty()
                && !observation.inventory().items().get(0).actions().isEmpty()) {
            // The right position and a name the pack never held: the desync a drifted Replay makes.
            strangers.add(new Action.UseItem(new ItemRef(0, "a thing the pack never held", 1),
                    observation.inventory().items().get(0).actions().get(0)));
        }
        return strangers;
    }
}
