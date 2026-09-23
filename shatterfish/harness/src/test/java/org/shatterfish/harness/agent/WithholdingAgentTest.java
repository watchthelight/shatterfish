package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.ActionsSection;
import org.shatterfish.api.Observation;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.observer.Observer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The deliberately worse Brains (story 3.9): the random agent less one kind of Action, here
 * {@code Descend}, the kind SM-5 names.
 *
 * <p>A real Observation from a real wait, with its offered set replaced, so that the one thing
 * varied is whether {@code Descend} is in it.
 */
class WithholdingAgentTest {

    private static final long RUN_SALT = 0x5A17_5A17L;

    private HeadlessDriver driver;

    @AfterEach
    void close() {
        if (driver != null) {
            driver.close();
        }
    }

    private Observation observed() {
        driver = HeadlessDriver.start(0xC0FFEEL, HeroClass.WARRIOR, RUN_SALT);
        driver.stepToInputWait();
        return new Observer().observe();
    }

    private static Observation offering(Observation o, List<Action> actions) {
        return new Observation(o.header(), o.map(), o.actors(), o.hero(), o.inventory(), o.journal(),
                o.log(), new ActionsSection(actions), o.prompt());
    }

    @Test
    @DisplayName("with Descend offered, it takes every other Action and never Descend")
    void never_descends() {
        Observation real = observed();
        List<Action> offered = new ArrayList<>(real.actions().actions());
        offered.removeIf(Action.Descend.class::isInstance);
        List<Action> withStairs = new ArrayList<>(offered);
        withStairs.add(new Action.Descend());
        Observation observation = offering(real, withStairs);

        WithholdingAgent agent = new WithholdingAgent(1L, Action.Descend.class);
        Set<Action> drawn = new HashSet<>();
        for (int draw = 0; draw < 40 * withStairs.size(); draw++) {
            drawn.add(agent.decide(observation));
        }

        assertFalse(drawn.contains(new Action.Descend()));
        assertEquals(new HashSet<>(offered), drawn, "and every other Action, without preference");
    }

    @Test
    @DisplayName("with Descend not offered, it is the random agent: same seed, same choices")
    void otherwise_the_random_agent() {
        Observation real = observed();
        List<Action> offered = new ArrayList<>(real.actions().actions());
        offered.removeIf(Action.Descend.class::isInstance);
        Observation observation = offering(real, offered);

        WithholdingAgent worse = new WithholdingAgent(7L, Action.Descend.class);
        RandomAgent random = new RandomAgent(7L);
        for (int draw = 0; draw < 50; draw++) {
            assertEquals(random.decide(observation), worse.decide(observation), "draw " + draw);
        }
    }

    @Test
    @DisplayName("it withholds whichever kind it is given: Rest, for the worse Brain that is worse")
    void withholds_rest() {
        Observation real = observed();
        List<Action> offered = new ArrayList<>(real.actions().actions());
        offered.removeIf(Action.Rest.class::isInstance);
        List<Action> withRest = new ArrayList<>(offered);
        withRest.add(new Action.Rest(false));
        withRest.add(new Action.Rest(true));
        Observation observation = offering(real, withRest);

        WithholdingAgent agent = new WithholdingAgent(5L, Action.Rest.class);
        Set<Action> drawn = new HashSet<>();
        for (int draw = 0; draw < 40 * withRest.size(); draw++) {
            drawn.add(agent.decide(observation));
        }

        assertTrue(drawn.stream().noneMatch(Action.Rest.class::isInstance), drawn.toString());
        assertEquals(new HashSet<>(offered), drawn);
    }

    @Test
    @DisplayName("with Descend all there is, it has nothing to choose and says so")
    void nothing_but_stairs() {
        Observation stairs = offering(observed(), List.of(new Action.Descend()));

        assertNull(new WithholdingAgent(3L, Action.Descend.class).decide(stairs));
        assertTrue(new RandomAgent(3L).decide(stairs) instanceof Action.Descend,
                "where the random agent would have gone down");
    }
}
