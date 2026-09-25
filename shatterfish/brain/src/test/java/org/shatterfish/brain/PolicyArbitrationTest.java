package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.Belief;
import org.shatterfish.api.RunLog;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Arbitration, the first Policies, the memory and the stream (story 4.1). */
class PolicyArbitrationTest {

    private static Brain brain() {
        return new Brain(Screens.CODEX, Screens.WEIGHTS, 11L);
    }

    @Test
    @DisplayName("an open Prompt goes to the prompt Policy, which declines when it can")
    void a_prompt_comes_first() {
        Brain.Decided decided = brain().decide(
                Screens.prompting(new Action.AnswerPrompt(1), new Action.AnswerPrompt(0)), null);

        assertEquals(new Action.AnswerPrompt(1), decided.action(), "\"No\" to the chasm, whatever the order offered");
        RunLog.Decision decision = decided.decision();
        assertEquals("answer-prompt", decision.policy());
        assertTrue(decision.goal().contains("prompt"), decision.goal());
        assertEquals(List.of("answer-prompt", "heal", "fight", "eat", "explore", "fallback"), brain().policies());
    }

    @Test
    @DisplayName("a Prompt with no declining answer gets its lowest answer")
    void no_way_to_decline() {
        Brain.Decided decided = brain().decide(Screens.asking(List.of("Take the gold", "Take the key"),
                new Action.AnswerPrompt(1), new Action.AnswerPrompt(0)), null);
        assertEquals(new Action.AnswerPrompt(0), decided.action());
        assertEquals(new Action.AnswerPrompt(1), brain().decide(Screens.asking(List.of("Jump", " cancel "),
                new Action.AnswerPrompt(0), new Action.AnswerPrompt(1)), null).action(), "labels are case- and space-blind");
    }

    @Test
    @DisplayName("an alternative is another Action, never the one chosen")
    void alternatives_differ() {
        Brain brain = brain();
        Belief belief = null;
        int seen = 0;
        for (int i = 0; i < 60; i++) {
            var screen = Screens.prompting(new Action.AnswerPrompt(0), new Action.AnswerPrompt(1));
            belief = brain.update(screen, belief);
            RunLog.Decision decision = brain.decide(screen, belief).decision();
            for (RunLog.Choice alternative : decision.alternatives()) {
                assertTrue(!alternative.action().equals(decision.chosen().action()), "wait " + i + ": " + decision);
                seen++;
            }
            assertTrue(decision.alternatives().size() <= 1, decision.toString());
        }
        assertTrue(seen > 0, "the fallback sometimes chose otherwise, and was recorded");
    }

    @Test
    @DisplayName("the configuration names the Policies and the memory's version")
    void the_configuration() {
        assertEquals("policies=answer-prompt,heal,fight,eat,explore,fallback;memory=" + Memory.VERSION + ";weights=" + Screens.WEIGHTS.canonical(),
                Brain.configuration(Screens.WEIGHTS));
    }

    @Test
    @DisplayName("a Prompt that offers no answer is dismissed")
    void dismissed() {
        Brain.Decided decided = brain().decide(Screens.prompting(new Action.DismissPrompt()), null);
        assertEquals(new Action.DismissPrompt(), decided.action());
        assertEquals("answer-prompt", decided.decision().policy());
    }

    @Test
    @DisplayName("an ordinary screen goes to the fallback, which reaches every Action offered")
    void the_fallback() {
        Action[] offered = {new Action.Wait(), new Action.Search(), new Action.Step(0), new Action.Step(2)};
        Set<Action> taken = new HashSet<>();
        Belief belief = null;
        Brain brain = brain();
        for (int i = 0; i < 200; i++) {
            var screen = Screens.offering(1, offered);
            belief = brain.update(screen, belief);
            Brain.Decided decided = brain.decide(screen, belief);
            assertEquals("fallback", decided.decision().policy());
            taken.add(decided.action());
        }
        assertEquals(Set.of(offered), taken, "no preference: every Action offered is taken");
    }

    @Test
    @DisplayName("nothing offered is no Action and no Decision, with the reason said")
    void nothing_offered() {
        Brain.Decided decided = brain().decide(Screens.offering(1), null);
        assertNull(decided.action());
        assertNull(decided.decision());
        assertTrue(decided.why().contains("no Action"), decided.why());
    }

    @Test
    @DisplayName("the memory counts waits and the deepest floor, and refuses a Belief it did not write")
    void the_memory() {
        Brain brain = brain();
        Belief belief = brain.update(Screens.offering(3, new Action.Wait()), null);
        belief = brain.update(Screens.offering(2, new Action.Wait()), belief);
        Memory memory = Memory.of(belief);
        assertEquals(2, memory.waits());
        assertEquals(3, memory.deepest());
        assertEquals(memory, Memory.of(memory.belief()), "the bytes round-trip");
        assertEquals(Memory.START, Memory.of(null));
        assertThrows(IllegalArgumentException.class, () -> Memory.of(new Belief(1, new byte[12])),
                "a Belief of the first version is not this one");
        assertThrows(IllegalArgumentException.class, () -> Memory.of(new Belief(Memory.VERSION, new byte[3])));
        byte[] longer = java.util.Arrays.copyOf(memory.belief().bytes(), memory.belief().bytes().length + 1);
        assertThrows(IllegalArgumentException.class, () -> Memory.of(new Belief(Memory.VERSION, longer)),
                "bytes left over are refused");
        assertThrows(IllegalArgumentException.class, () -> new Brain(null, Screens.WEIGHTS, 1L));
        assertThrows(IllegalArgumentException.class, () -> new Brain(Screens.CODEX, null, 1L));
    }

    @Test
    @DisplayName("the stream draws inside its bound, the same draws for the same seed and wait")
    void the_stream() {
        for (int bound = 1; bound < 40; bound++) {
            Stream stream = Stream.at(5L, bound);
            for (int i = 0; i < 100; i++) {
                int draw = stream.below(bound);
                assertTrue(draw >= 0 && draw < bound, bound + ": " + draw);
            }
        }
        assertEquals(Stream.at(9L, 3L).next(), Stream.at(9L, 3L).next());
        assertTrue(Stream.at(9L, 3L).next() != Stream.at(9L, 4L).next());
        assertThrows(IllegalArgumentException.class, () -> Stream.at(1L, 1L).below(0));
        // SplitMix64's published first output for seed 0 (the harness's Mix test vector, mix(0, 1)).
        assertEquals(0xe220a8397b1dcdafL, Stream.mix(0x9E3779B97F4A7C15L));
    }
}
