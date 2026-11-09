package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.ActorsSection;
import org.shatterfish.api.Hunger;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fallback never draws a rest beside an enemy (issue #448, story 10.44). The only rest the screen offers is
 * the rest button, {@code Rest(true)}, which runs until the hit points are full or something interrupts it
 * (ValidActions: the wait button is {@code Wait}). In the baseline logs of the issue #412 re-measure the fallback drew
 * such a rest with an enemy in view for 134, 59 and 333 turns; one DUELIST spent 862 turns on one floor that way
 * and starved. An enemy in view that is not coming (asleep, out of reach) does not interrupt the rest, and one that
 * is coming is no moment to sit down.
 */
class FallbackRestTest {

    private static final Action[] OFFERED = {new Action.Wait(), new Action.Search(), new Action.Rest(true),
            new Action.Step(0), new Action.Step(2)};

    private static Observation screen(boolean enemy) {
        Observation plain = Screens.offering(3, OFFERED);
        ActorsSection actors = new ActorsSection(enemy ? List.of(Screens.enemy("sewer crab", 2)) : List.of());
        return new Observation(plain.header(), plain.map(), actors, Screens.hero(5, 20, 1, 10, Hunger.NONE),
                plain.inventory(), plain.journal(), plain.log(), plain.actions(), plain.prompt());
    }

    private static Set<Action> drawn(Observation observation, List<Action> offered) {
        Policy fallback = Policies.fallback(new Evaluation(Screens.WEIGHTS));
        Memory memory = DescendPolicyTest.at(List.of(), 5, 1, 0);
        Set<Action> taken = new HashSet<>();
        for (int k = 0; k < 300; k++) {
            RunLog.Choice choice = fallback.choose(observation, memory, offered, Stream.at(7L, k));
            taken.add(choice.action());
        }
        return taken;
    }

    @Test
    @DisplayName("with an enemy in view the fallback draws no rest, and every other Action stays in the draw")
    void no_rest_beside_an_enemy() {
        Set<Action> taken = drawn(screen(true), List.of(OFFERED));
        assertTrue(!taken.contains(new Action.Rest(true)), "rest drawn beside an enemy: " + taken);
        assertEquals(Set.of(new Action.Wait(), new Action.Search(), new Action.Step(0), new Action.Step(2)), taken);
    }

    @Test
    @DisplayName("control: with no enemy in view a fed hero's fallback still rests")
    void rest_stays_when_calm() {
        assertTrue(drawn(screen(false), List.of(OFFERED)).contains(new Action.Rest(true)));
    }

    @Test
    @DisplayName("control: when the rest is all that is offered beside an enemy it is drawn, a Run is not ended for want of one")
    void rest_when_nothing_else() {
        assertEquals(Set.of(new Action.Rest(true)), drawn(screen(true), List.of(new Action.Rest(true))));
    }
}
