package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.Belief;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Evaluation terms a Decision records (story 8.1, FR-44): they come from the same call that
 * scored the Choice, they sum to the score recorded, and a Policy that scores nothing records none.
 */
class DecisionTermsTest {

    private static final Evaluation COMMITTED = new Evaluation(Screens.WEIGHTS);

    private static final Action[] OFFERED = {new Action.Wait(), new Action.Search(), new Action.Step(0),
            new Action.Step(2)};

    private static RunLog.Decision fallback(Brain brain) {
        Observation screen = Screens.offering(1, OFFERED);
        Belief belief = brain.update(screen, null);
        return brain.decide(screen, belief).decision();
    }

    private static List<RunLog.Choice> everyChoice(RunLog.Decision decision) {
        List<RunLog.Choice> all = new ArrayList<>();
        all.add(decision.chosen());
        all.addAll(decision.alternatives());
        return all;
    }

    @Test
    @DisplayName("the terms and the score come from one computation: the sum of the terms is the Action's score")
    void terms_are_the_score() {
        Observation screen = Screens.offering(3, OFFERED);
        for (Action action : OFFERED) {
            Evaluation.Scored scored = COMMITTED.scored(screen, action);
            assertEquals(COMMITTED.of(screen, action), scored.score(), action.toString());
            assertEquals(scored.score(), scored.terms().stream().mapToLong(RunLog.Term::contribution).sum());
            assertTrue(scored.terms().stream().noneMatch(term -> term.contribution() == 0), "no zeros are listed");
        }
        // The Action's own feature enters the terms once it is weighted, and only for that Action.
        Evaluation searching = new Evaluation(Screens.weights(Map.of("act_search", 5L)));
        assertTrue(searching.scored(screen, new Action.Search()).terms().stream()
                .anyMatch(term -> term.feature().equals(Evaluation.SEARCH) && term.contribution() == 5));
        assertTrue(searching.scored(screen, new Action.Wait()).terms().stream()
                .noneMatch(term -> term.feature().equals(Evaluation.SEARCH)));
    }

    @Test
    @DisplayName("terms are listed in the order the Evaluation lists its features")
    void in_feature_order() {
        Evaluation weighted = new Evaluation(Screens.weights(Map.of("act_wait", 7L, "act_attack", 3L)));
        Observation screen = Screens.offering(3, OFFERED);
        List<String> names = weighted.scored(screen, new Action.Wait()).terms().stream()
                .map(RunLog.Term::feature).toList();
        assertEquals(names.stream().sorted().toList(), names);
        assertEquals(Evaluation.FEATURES.stream().filter(names::contains).toList(), names);
    }

    @Test
    @DisplayName("every fallback Choice that carries terms has terms summing to its recorded score")
    void the_fallback_records_terms_that_add_up() {
        RunLog.Decision decision = fallback(new Brain(Screens.CODEX, Screens.WEIGHTS, 11L));
        assertEquals(Policies.FALLBACK, decision.policy());
        for (RunLog.Choice choice : everyChoice(decision)) {
            assertFalse(choice.terms().isEmpty(), "the fallback scores through the Evaluation");
            assertEquals(choice.score(), choice.terms().stream().mapToLong(RunLog.Term::contribution).sum());
            assertEquals(Policies.TIER, choice.terms().get(choice.terms().size() - 1).feature(),
                    "the chance of the draw is the last term, named for what it is");
            assertEquals(1, choice.terms().stream().filter(term -> term.feature().equals(Policies.TIER)).count());
        }
    }

    @Test
    @DisplayName("recording terms changes nothing the Brain does: the Actions are those of the weights alone")
    void recording_only() {
        Brain brain = new Brain(Screens.CODEX, Screens.WEIGHTS, 11L);
        Belief belief = null;
        for (int i = 0; i < 50; i++) {
            Observation screen = Screens.offering(1, OFFERED);
            belief = brain.update(screen, belief);
            Action taken = brain.decide(screen, belief).action();
            Stream stream = Stream.at(Stream.mix(11L ^ Stream.mix(Policies.FALLBACK.hashCode())), Memory.of(belief).waits());
            List<Action> sorted = screen.actions().actions();
            assertEquals(sorted.get(stream.below(sorted.size())), taken, "wait " + i);
        }
    }

    @Test
    @DisplayName("a Policy that scores nothing through the Evaluation records no terms, not zeros")
    void a_certain_policy_records_none() {
        Brain brain = new Brain(Screens.CODEX, Screens.WEIGHTS, 11L);
        Observation screen = Screens.offering(1, new Action.Descend());
        Belief belief = brain.update(screen, null);
        RunLog.Decision decision = brain.decide(screen, belief).decision();
        assertNotNull(decision);
        if (!Policies.FALLBACK.equals(decision.policy())) {
            for (RunLog.Choice choice : everyChoice(decision)) {
                assertTrue(choice.terms().isEmpty(), decision.policy() + " scores nothing: " + choice.terms());
            }
        }
        // And the no-Search rule: the Brain has no Search, so no Decision of it carries an outcome.
        assertTrue(decision.search().isEmpty());
        assertTrue(fallback(new Brain(Screens.CODEX, Screens.WEIGHTS, 11L)).search().isEmpty());
    }
}
