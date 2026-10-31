package org.shatterfish.brain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.Belief;
import org.shatterfish.api.Observation;

/**
 * Story 10.13: a swarm of flies splits when a hit leaves it two or more health (Swarm.java:88-116), the clone standing
 * on a free cell beside it, so a hero in the open ends up ringed by them and a hero in a corridor is reached by two.
 * One swarm is a crowd to the Fight Policy before any hit lands.
 */
class SwarmCorridorTest {

    private static int cell(Observation screen, int x, int y) {
        return x + y * screen.map().width();
    }

    private static Brain.Decided after(Observation... screens) {
        Brain brain = new Brain(FightPolicyTest.KNOWLEDGE, Screens.WEIGHTS, 9L);
        Belief belief = Screens.drive(brain, screens);
        Brain.Decided decided = brain.decide(screens[screens.length - 1], belief);
        assertTrue(screens[screens.length - 1].actions().actions().contains(decided.action()),
                "only an Action the screen offers: " + decided.action());
        return decided;
    }

    @Test
    @DisplayName("one swarm coming across a room: the hero steps back into the corridor, where two cells can reach it, not four")
    void lone_swarm_is_met_in_the_corridor() {
        Observation doorway = FightPolicyTest.screen(3, 20, false, "worn shortsword",
                "############",
                "#####......#",
                "....@.....W#",
                "#####......#",
                "############");
        assertEquals(4, Fight.engage(doorway.map(), doorway.hero().cell()));
        Brain.Decided decided = after(doorway);
        assertEquals(Fight.NAME, decided.decision().policy());
        assertEquals(new Action.Step(cell(doorway, 3, 2)), decided.action(), decided.decision().toString());
        assertEquals("chokepoint 2", decided.decision().chosen().why());
    }

    @Test
    @DisplayName("in the corridor already, the hero holds for the swarm, a few waits at most")
    void holds_the_corridor() {
        Observation corridor = FightPolicyTest.screen(3, 20, false, "worn shortsword",
                "############",
                "#####......#",
                "...@......W#",
                "#####......#",
                "############");
        Brain.Decided decided = after(corridor);
        assertEquals(new Action.Wait(), decided.action(), decided.decision().toString());
        assertEquals("hold: chokepoint", decided.decision().chosen().why());
    }

    @Test
    @DisplayName("no corridor to step into, in a closed room: the hero goes to the swarm as before")
    void no_corridor_reachable() {
        Observation room = FightPolicyTest.screen(3, 20, false, "worn shortsword",
                "############",
                "#..........#",
                "#...@.....W#",
                "#..........#",
                "############");
        Brain.Decided decided = after(room);
        assertTrue(decided.decision().chosen().why().startsWith("approach "), decided.decision().toString());
    }

    @Test
    @DisplayName("a lone rat is still no crowd: the hero in the doorway goes to meet it")
    void a_rat_is_no_crowd() {
        Observation doorway = FightPolicyTest.screen(3, 20, false, "worn shortsword",
                "############",
                "#####......#",
                "....@.....r#",
                "#####......#",
                "############");
        assertTrue(after(doorway).decision().chosen().why().startsWith("approach "));
    }
}
