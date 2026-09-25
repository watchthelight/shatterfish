package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.Belief;
import org.shatterfish.api.Emote;
import org.shatterfish.api.Hunger;
import org.shatterfish.api.Observation;
import org.shatterfish.api.Weights;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Evaluation (story 4.5, FR-33): the obvious orderings hold under the committed weights, the
 * weights are the Evaluation's whole configuration, and changing one changes what the Brain does.
 */
class EvaluationMonotonicityTest {

    private static final Evaluation COMMITTED = new Evaluation(Screens.WEIGHTS);

    @Test
    @DisplayName("the weights these orderings are checked under are the committed file's")
    void the_committed_weights() throws java.io.IOException {
        // A test may read a file where the Brain may not: the boundary rules are about brain's
        // main classes. Without this, Screens.WEIGHTS is a hand copy nothing holds to the file, and
        // every ordering below could hold for weights nobody plays with.
        java.nio.file.Path here = java.nio.file.Path.of("").toAbsolutePath();
        java.nio.file.Path file = null;
        for (java.nio.file.Path p = here; p != null && file == null; p = p.getParent()) {
            if (java.nio.file.Files.isRegularFile(p.resolve("weights/shatterfish.json"))) {
                file = p.resolve("weights/shatterfish.json");
            }
        }
        assertTrue(file != null, "the committed weights are found above " + here);
        assertEquals(java.nio.file.Files.readString(file, java.nio.charset.StandardCharsets.UTF_8).strip(),
                Screens.WEIGHTS.canonical());
    }

    @Test
    @DisplayName("each Action feature, weighted, makes its Action the one taken over a neutral one")
    void every_action_feature() {
        Observation hurt = Screens.showing(1, Screens.hero(5, 20, 1, 10, Hunger.NONE), List.of(),
                new Action.Step(0), new Action.Rest(false));
        assertEquals(new Action.Rest(false), taken(Map.of("act_rest_hurt", 1L), hurt));
        // With an enemy in view the fight Policy takes the wait (story 4.7), so the attack feature is
        // held on the Evaluation itself: weighted, it scores the Attack above the Step.
        Observation rat = Screens.showing(1, Screens.hero(), List.of(new ActorView(0, "rat", Alignment.ENEMY, 3, false,
                Emote.NONE, List.of())), new Action.Step(2), new Action.Attack(0));
        Evaluation attacking = new Evaluation(Screens.weights(Map.of("act_attack", 1L)));
        assertTrue(attacking.of(rat, new Action.Attack(0)) > attacking.of(rat, new Action.Step(2)));
        assertEquals(new Action.Descend(), taken(Map.of("act_descend", 1L),
                Screens.offering(1, new Action.Step(0), new Action.Descend())));
        assertEquals(new Action.Search(), taken(Map.of("act_search", 1L),
                Screens.offering(1, new Action.Step(0), new Action.Search())));
        assertEquals(new Action.Wait(), taken(Map.of("act_wait", 1L),
                Screens.offering(1, new Action.Step(0), new Action.Wait())));
    }

    /** What a Brain on these weights takes at the first wait of {@code screen}, drawing uniformly. */
    private static Action taken(Map<String, Long> weights, Observation screen) {
        Action first = null;
        for (long seed = 0; seed < 20; seed++) {
            Brain brain = new Brain(Screens.CODEX, Screens.weights(weights), seed);
            Action action = brain.decide(screen, brain.update(screen, null)).action();
            if (first != null) {
                assertEquals(first, action, "every seed takes the weighted Action");
            }
            first = action;
        }
        return first;
    }

    private static long score(int depth, int hp, int ht, int level, int strength, Hunger hunger, int enemies) {
        List<ActorView> actors = new ArrayList<>();
        for (int i = 0; i < enemies; i++) {
            actors.add(new ActorView(i == 0 ? 0 : 2, "rat", Alignment.ENEMY, 3, false, Emote.NONE, List.of()));
        }
        return COMMITTED.position(Screens.showing(depth, Screens.hero(hp, ht, level, strength, hunger), actors,
                new Action.Wait()));
    }

    @Test
    @DisplayName("more hit points at equal depth score higher")
    void more_health() {
        assertTrue(score(2, 20, 20, 1, 10, Hunger.NONE, 0) > score(2, 19, 20, 1, 10, Hunger.NONE, 0));
        assertTrue(score(2, 5, 20, 1, 10, Hunger.NONE, 0) > score(2, 0, 20, 1, 10, Hunger.NONE, 0));
    }

    @Test
    @DisplayName("deeper, a higher level and more strength score higher, all else equal")
    void progress() {
        assertTrue(score(3, 10, 20, 1, 10, Hunger.NONE, 0) > score(2, 10, 20, 1, 10, Hunger.NONE, 0));
        assertTrue(score(2, 10, 20, 2, 10, Hunger.NONE, 0) > score(2, 10, 20, 1, 10, Hunger.NONE, 0));
        assertTrue(score(2, 10, 20, 1, 11, Hunger.NONE, 0) > score(2, 10, 20, 1, 10, Hunger.NONE, 0));
    }

    @Test
    @DisplayName("hunger and enemies in view score lower")
    void danger() {
        long fed = score(2, 10, 20, 1, 10, Hunger.NONE, 0);
        long hungry = score(2, 10, 20, 1, 10, Hunger.HUNGRY, 0);
        long starving = score(2, 10, 20, 1, 10, Hunger.STARVING, 0);
        assertTrue(fed > hungry && hungry > starving, fed + " " + hungry + " " + starving);
        assertTrue(score(2, 10, 20, 1, 10, Hunger.NONE, 1) < fed);
        assertTrue(score(2, 10, 20, 1, 10, Hunger.NONE, 2) < score(2, 10, 20, 1, 10, Hunger.NONE, 1));
    }

    @Test
    @DisplayName("resting scores by the health missing, when resting is weighted")
    void resting_when_hurt() {
        Evaluation rests = new Evaluation(Screens.weights(Map.of("act_rest_hurt", 1L)));
        Observation hurt = Screens.showing(1, Screens.hero(5, 20, 1, 10, Hunger.NONE), List.of(), new Action.Rest(false));
        Observation whole = Screens.showing(1, Screens.hero(20, 20, 1, 10, Hunger.NONE), List.of(), new Action.Rest(false));
        long hurtGain = rests.of(hurt, new Action.Rest(false)) - rests.position(hurt);
        long wholeGain = rests.of(whole, new Action.Rest(false)) - rests.position(whole);
        assertEquals(750, hurtGain, "a quarter of the health left is three quarters missing");
        assertEquals(0, wholeGain);
        assertEquals(COMMITTED.position(hurt), COMMITTED.of(hurt, new Action.Rest(false)),
                "the committed weights give an Action no score of its own");
    }

    @Test
    @DisplayName("a weight set that forgets a feature, or weights one there is not, is refused")
    void the_whole_set() {
        List<Weights.Term> fewer = new ArrayList<>(Screens.WEIGHTS.terms());
        fewer.remove(0);
        assertThrows(IllegalArgumentException.class, () -> new Evaluation(new Weights("shatterfish", 1, fewer)));
        List<Weights.Term> more = new ArrayList<>(Screens.WEIGHTS.terms());
        more.add(new Weights.Term("luck", 1));
        assertThrows(IllegalArgumentException.class, () -> new Evaluation(new Weights("shatterfish", 1, more)));
        assertThrows(IllegalArgumentException.class, () -> new Evaluation(null));
    }

    @Test
    @DisplayName("changing a weight changes what the Brain does, with nothing recompiled")
    void weights_are_behaviour() {
        Action[] offered = {new Action.Wait(), new Action.Search(), new Action.Step(0), new Action.Step(2)};
        Set<Action> committed = play(Screens.WEIGHTS, offered);
        Set<Action> searching = play(Screens.weights(Map.of("act_search", 5L)), offered);
        assertEquals(Set.of(offered), committed, "the committed weights leave the fallback uniform");
        assertEquals(Set.of(new Action.Search()), searching, "a weight on searching makes the Brain search");
        assertNotEquals(Brain.configuration(Screens.WEIGHTS),
                Brain.configuration(Screens.weights(Map.of("act_search", 5L))),
                "and the two weight sets are two configurations");
    }

    @Test
    @DisplayName("a Choice records its chance within its tier, and the reason names the tier")
    void tiers() {
        Observation screen = Screens.offering(1, new Action.Wait(), new Action.Search(), new Action.Step(0),
                new Action.Step(2));
        Brain searching = new Brain(Screens.CODEX, Screens.weights(Map.of("act_search", 5L)), 11L);
        var decision = searching.decide(screen, searching.update(screen, null)).decision();
        assertEquals(new Action.Search(), decision.chosen().action());
        assertEquals(10_000, decision.chosen().score(), "the only Action of the top tier is certain within it");
        assertEquals("top 1/1", decision.chosen().why());
        assertEquals(3, decision.alternatives().size());
        for (var alternative : decision.alternatives()) {
            assertEquals("lower 1/3", alternative.why());
            assertEquals(3333, alternative.score());
        }
        Brain alike = new Brain(Screens.CODEX, Screens.WEIGHTS, 11L);
        var uniform = alike.decide(screen, alike.update(screen, null)).decision();
        assertEquals("uniform 1/4", uniform.chosen().why());
        assertEquals(2500, uniform.chosen().score());
    }

    @Test
    @DisplayName("under the committed weights the fallback draws what story 4.1's did")
    void the_same_draws() {
        Action[] offered = {new Action.Wait(), new Action.Search(), new Action.Step(0), new Action.Step(2)};
        Brain brain = new Brain(Screens.CODEX, Screens.WEIGHTS, 11L);
        Belief belief = null;
        for (int i = 0; i < 50; i++) {
            Observation screen = Screens.offering(1, offered);
            belief = brain.update(screen, belief);
            Action taken = brain.decide(screen, belief).action();
            // The fallback draws from its own stream at this wait, keyed on its name (story 4.6).
            Stream stream = Stream.at(Stream.mix(11L ^ Stream.mix(Policies.FALLBACK.hashCode())), Memory.of(belief).waits());
            List<Action> sorted = screen.actions().actions();
            assertEquals(sorted.get(stream.below(sorted.size())), taken, "wait " + i);
        }
    }

    private static Set<Action> play(Weights weights, Action... offered) {
        Brain brain = new Brain(Screens.CODEX, weights, 11L);
        Set<Action> taken = new HashSet<>();
        Belief belief = null;
        for (int i = 0; i < 100; i++) {
            Observation screen = Screens.offering(1, offered);
            belief = brain.update(screen, belief);
            taken.add(brain.decide(screen, belief).action());
        }
        return taken;
    }
}
