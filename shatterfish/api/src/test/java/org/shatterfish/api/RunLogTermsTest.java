package org.shatterfish.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What story 8.1 added to the Decision record: a Choice's Evaluation terms and a Decision's Search
 * outcomes, both absent when empty so that every log written before keeps its bytes and its chain.
 */
class RunLogTermsTest {

    private static final String LEGACY_CHOICE =
            "{\"action\":{\"cell\":3,\"kind\":\"Step\"},\"score\":10000,\"why\":\"frontier\"}";

    private static RunLog.Decision decision(RunLog.Choice chosen, List<RunLog.SearchOutcome> search) {
        return new RunLog.Decision("explore", chosen, List.of(), List.of(), "explore", search);
    }

    private static RunLog.Wait wait(RunLog.Decision decision) {
        return new RunLog.Wait(1, 0, 0, 0, "a".repeat(64), java.util.Map.of("hero", "b".repeat(64)),
                new Action.Step(3), true, RunLog.BOT, decision, "", List.of(), 5);
    }

    @Test
    @DisplayName("a Choice with no terms is written exactly as it was before the field existed")
    void empty_terms_are_not_written() {
        RunLog.Decision before = decision(new RunLog.Choice(new Action.Step(3), 10_000, "frontier"), List.of());
        String text = RunLogJson.canonical(wait(before));
        assertTrue(text.contains(LEGACY_CHOICE), text);
        assertFalse(text.contains("terms"));
        assertFalse(text.contains("search"));
        // The three-argument constructors are the same record as the explicit empty lists.
        assertEquals(before, decision(new RunLog.Choice(new Action.Step(3), 10_000, "frontier", List.of()), List.of()));
    }

    @Test
    @DisplayName("terms are written in the order given, and the chain covers them")
    void terms_are_written_and_chained() {
        RunLog.Choice with = new RunLog.Choice(new Action.Step(3), 10_000, "frontier",
                List.of(new RunLog.Term("depth", 30_000), new RunLog.Term("hunger", -20_000)));
        String text = RunLogJson.canonical(wait(decision(with, List.of())));
        assertTrue(text.contains("\"terms\":[{\"feature\":\"depth\",\"value\":30000},"
                + "{\"feature\":\"hunger\",\"value\":-20000}]"), text);
        RunLog.Wait plain = wait(decision(new RunLog.Choice(new Action.Step(3), 10_000, "frontier"), List.of()));
        assertNotEquals(RunLogJson.chain("", plain), RunLogJson.chain("", wait(decision(with, List.of()))));
    }

    @Test
    @DisplayName("a Choice whose terms do not sum to its score is refused at construction")
    void terms_must_sum_to_the_score() {
        assertThrows(IllegalArgumentException.class, () -> new RunLog.Choice(new Action.Wait(), 100, "lie",
                List.of(new RunLog.Term("hp", 99))));
        assertThrows(IllegalArgumentException.class, () -> new RunLog.Choice(new Action.Wait(), 0, "lie",
                List.of(new RunLog.Term("hp", 5), new RunLog.Term("depth", 4))));
        new RunLog.Choice(new Action.Wait(), 9, "truth", List.of(new RunLog.Term("hp", 5), new RunLog.Term("depth", 4)));
        assertThrows(IllegalArgumentException.class, () -> new RunLog.Term("Not A Feature", 1));
    }

    @Test
    @DisplayName("a Term holds a feature name and a number and nothing else, since the Brain may name it")
    void a_term_cannot_widen() {
        var components = RunLog.Term.class.getRecordComponents();
        assertEquals(2, components.length);
        assertEquals(String.class, components[0].getType());
        assertEquals(long.class, components[1].getType());
    }

    @Test
    @DisplayName("Search outcomes are written only when a Search ran, and an outcome rests on a sample")
    void search_outcomes() {
        RunLog.Choice chosen = new RunLog.Choice(new Action.Step(3), 10_000, "frontier");
        assertFalse(RunLogJson.canonical(wait(decision(chosen, List.of()))).contains("search"));
        String text = RunLogJson.canonical(wait(decision(chosen,
                List.of(new RunLog.SearchOutcome(new Action.Step(3), -1_250, 40)))));
        assertTrue(text.contains("\"search\":[{\"action\":{\"cell\":3,\"kind\":\"Step\"},\"outcome\":-1250,\"samples\":40}]"),
                text);
        assertThrows(IllegalArgumentException.class, () -> new RunLog.SearchOutcome(new Action.Wait(), 0, 0));
    }
}
