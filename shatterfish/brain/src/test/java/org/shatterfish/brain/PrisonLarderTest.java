package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.Hunger;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.Tunables;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Prison's second food pass (story 10.39, issue #423). The 960-Run diagnosis found 45.8% of heroes
 * STARVING on their last Decision. On a floor whose exit was never found, the fallback drew uniformly
 * from every Action offered, and a Rest(full) is a draw: it runs until the hit points are full, hundreds
 * of turns, on a larder that is empty, while a starving hero regenerates nothing (Regeneration.java:56)
 * and loses hit points each turn.
 */
class PrisonLarderTest {

    private static final Action[] OFFERED = {new Action.Wait(), new Action.Search(), new Action.Rest(true),
            new Action.Step(0), new Action.Step(2)};

    private static Observation screen(Hunger hunger, int hp) {
        Observation plain = Screens.offering(3, OFFERED);
        return new Observation(plain.header(), plain.map(), plain.actors(), Screens.hero(hp, 20, 1, 10, hunger),
                plain.inventory(), plain.journal(), plain.log(), plain.actions(), plain.prompt());
    }

    /** Every Action the fallback ranks over 300 draws, at {@code vector}. */
    private static Set<Action> drawn(Observation observation, Tunables.Vector vector) {
        Policy fallback = Policies.fallback(new Evaluation(Screens.WEIGHTS), vector);
        Memory memory = DescendPolicyTest.at(List.of(), 5, 1, 0);
        Set<Action> taken = new HashSet<>();
        for (int k = 0; k < 300; k++) {
            RunLog.Choice choice = fallback.choose(observation, memory, List.of(OFFERED), Stream.at(7L, k));
            taken.add(choice.action());
        }
        return taken;
    }

    private static final Tunables.Vector NO_RESTS = Tunables.Vector.REFERENCE;

    private static final Tunables.Vector OLD = Tunables.Vector.REFERENCE.with(Map.of("larder.fallback_rests", 1L));

    @Test
    @DisplayName("a hungry hero with no food draws no rest from the fallback, the other Actions stay in the draw")
    void hungry_and_empty_does_not_rest() {
        Set<Action> taken = drawn(screen(Hunger.HUNGRY, 5), NO_RESTS);
        assertTrue(!taken.contains(new Action.Rest(true)), "rest drawn: " + taken);
        assertEquals(Set.of(new Action.Wait(), new Action.Search(), new Action.Step(0), new Action.Step(2)), taken);
    }

    @Test
    @DisplayName("a starving hero with no food draws no rest either")
    void starving_and_empty_does_not_rest() {
        assertTrue(!drawn(screen(Hunger.STARVING, 5), NO_RESTS).contains(new Action.Rest(true)));
    }

    @Test
    @DisplayName("the old behaviour holds: a fed hero rests in the fallback, and so does a hungry one at the old setting")
    void old_behaviour_holds() {
        assertTrue(drawn(screen(Hunger.NONE, 5), NO_RESTS).contains(new Action.Rest(true)), "fed, rest stays in the draw");
        assertTrue(drawn(screen(Hunger.HUNGRY, 5), OLD).contains(new Action.Rest(true)),
                "the old setting: rest stays in the draw");
    }
}
