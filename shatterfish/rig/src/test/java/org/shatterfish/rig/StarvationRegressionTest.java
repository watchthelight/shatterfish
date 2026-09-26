package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.Action;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.Belief;
import org.shatterfish.api.Codex;
import org.shatterfish.api.Deliberator;
import org.shatterfish.api.Hunger;
import org.shatterfish.api.ItemKind;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.Observation;
import org.shatterfish.api.PromptKind;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.SeedSet;
import org.shatterfish.api.Weights;
import org.shatterfish.brain.Brain;
import org.shatterfish.harness.agent.RunLoop;
import org.shatterfish.harness.agent.RunOutcome;
import org.shatterfish.harness.boot.HeadlessBoot;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Run with food available never ends in starvation (story 4.9, the acceptance criterion by name).
 *
 * <p>The `smoke` set's Runs are played in this JVM by the shatterfish Brain, each under the salt the
 * direction check uses, and every screen the Brain is shown is watched. The game's ending says only
 * that the hero died (the Run log's cause is {@code DEATH}), not what killed it, so the test holds
 * the Brain to what makes starving with food impossible, on the screens themselves:
 *
 * <ul>
 *   <li>On every calm screen (no Prompt open, no enemy in view) where the hunger icon shows starving
 *       and a food the eat Policy knows is held and offered, the Brain eats; and where it shows hungry
 *       and a food the hunger takes whole is held, likewise. It eats without waste: at hungry a food of
 *       at most 300 energy, at starving one that wastes no more than any other held.</li>
 *   <li>Starving with a known food held, the Brain eats within {@link #STARVING_WAITS} waits whatever
 *       is in view: an enemy that never comes (an immovable one, one across a chasm) keeps the screen
 *       from ever being calm, and a Brain that ate only on calm screens would starve before it.</li>
 *   <li>No Run ends with its last screen calm, starving and a known food in the pack, unless the last
 *       Action was eating: a hero may die mid-meal, and the screen before the Action is the one the
 *       test sees. With an enemy in view the hero may die to it, to a shot or to a trap while retreating,
 *       which is not starving; the streak bound above covers those screens.</li>
 * </ul>
 *
 * <p>Every hero starts with a ration (HeroClass.java:108), so every Run has food available at the
 * start. The test also says how many of those screens it saw, so a set whose Runs never grow hungry
 * shows up as a test with nothing to hold.
 */
class StarvationRegressionTest {

    /** The turns a Run may take: long enough to starve to death from a full stomach several times over. */
    private static final int CAP = 5_000;

    private static final String COMMIT = "0".repeat(40);

    /** The most waits in a row a starving hero holding a known food may go without eating. */
    static final int STARVING_WAITS = 200;

    /** A food the eat Policy knows, which the screen offers to eat. */
    private static boolean edible(Observation observation, ItemView item, int most) {
        Integer energy = Brain.foods().get(item.name());
        return item.kind() == ItemKind.FOOD && energy != null && energy <= most && item.actions().contains("EAT");
    }

    /** Whether a starving hero holds a food the eat Policy knows. */
    private static boolean starvingWithFood(Observation observation) {
        return observation.hero().hunger() == Hunger.STARVING
                && observation.inventory().items().stream().anyMatch(item -> edible(observation, item, Integer.MAX_VALUE));
    }

    /**
     * Why eating {@code eaten} on {@code observation} wastes food, or null when it does not: at hungry
     * a food above 300 or mystery meat; at starving a food that wastes more than another held.
     */
    static String waste(Observation observation, String eaten) {
        Integer energy = Brain.foods().get(eaten);
        if (energy == null) {
            return "ate " + eaten + ", which the table does not know";
        }
        if (observation.hero().hunger() == Hunger.HUNGRY) {
            return energy <= 300 && !eaten.equals("mystery meat") ? null : "ate " + eaten + " while only hungry";
        }
        int least = Integer.MAX_VALUE;
        for (ItemView item : observation.inventory().items()) {
            if (edible(observation, item, Integer.MAX_VALUE) && !item.name().equals("mystery meat")) {
                least = Math.min(least, Math.max(0, Brain.foods().get(item.name()) - 450));
            }
        }
        return least == Integer.MAX_VALUE || Math.max(0, energy - 450) <= least ? null
                : "ate " + eaten + ", which wastes more than another food held";
    }

    /** Whether the screen is calm: no Prompt, no enemy drawn. */
    private static boolean calm(Observation observation) {
        return observation.header().prompt() == PromptKind.NONE
                && observation.actors().actors().stream().map(ActorView::alignment).noneMatch(Alignment.ENEMY::equals);
    }

    /**
     * Whether the Brain must eat on this screen: calm, and starving with a known food held, or hungry
     * with a known food of at most 300 energy that is not mystery meat.
     */
    static boolean mustEat(Observation observation) {
        if (!calm(observation)) {
            return false;
        }
        Hunger hunger = observation.hero().hunger();
        for (ItemView item : observation.inventory().items()) {
            if (hunger == Hunger.STARVING && edible(observation, item, Integer.MAX_VALUE)) {
                return true;
            }
            if (hunger == Hunger.HUNGRY && edible(observation, item, 300) && !item.name().equals("mystery meat")) {
                return true;
            }
        }
        return false;
    }

    /** The Brain, watched: every screen and the Action it chose there. */
    private static final class Watched implements Deliberator {

        private final Deliberator brain;
        final List<String> broken = new ArrayList<>();
        int mustEat;
        int starvingStreak;
        int starvingSeen;
        Observation last;
        Action lastChosen;

        Watched(Deliberator brain) {
            this.brain = brain;
        }

        @Override
        public Action decide(Observation observation) {
            Action chosen = brain.decide(observation);
            last = observation;
            lastChosen = chosen;
            boolean eats = chosen instanceof Action.UseItem use && use.action().equals("EAT");
            if (mustEat(observation)) {
                mustEat++;
                if (!eats) {
                    broken.add(observation.hero().hunger() + " with food, calm, and the Brain chose " + chosen);
                }
            }
            if (eats) {
                String wasted = waste(observation, ((Action.UseItem) chosen).item().name());
                if (wasted != null) {
                    broken.add(observation.hero().hunger() + ": " + wasted);
                }
            }
            if (starvingWithFood(observation)) {
                starvingSeen++;
                starvingStreak = eats ? 0 : starvingStreak + 1;
                if (starvingStreak == STARVING_WAITS) {
                    broken.add("starving with food held for " + STARVING_WAITS + " waits without eating, the last with "
                            + observation.actors().actors().size() + " actors in view");
                }
            } else {
                starvingStreak = 0;
            }
            return chosen;
        }

        @Override
        public RunLog.Decision lastDecision() {
            return brain.lastDecision();
        }

        @Override
        public Belief belief() {
            return brain.belief();
        }

        @Override
        public List<Integer> lastHighlights() {
            return brain.lastHighlights();
        }
    }

    @Test
    @DisplayName("a Run with food available never ends in starvation: the Brain eats on every calm hungry screen with food")
    @Timeout(value = 20, unit = TimeUnit.MINUTES)
    void food_held_never_starves(@TempDir Path out) {
        Path root = SeedSetsTest.ROOT;
        String tag = HeadlessBoot.pinnedTag();
        Codex.Knowledge codex = CodexKnowledge.read(root.resolve(CodexManifest.FOLDER).resolve(tag), tag);
        Weights weights = WeightsFile.read(WeightsFile.of(root, Brains.SHATTERFISH), Brains.SHATTERFISH);
        List<SeedSet.Entry> triples = SeedSets.load(root, SeedSets.SMOKE).set().entries();
        List<String> failures = new ArrayList<>();
        int mustEat = 0;
        int starving = 0;
        for (int i = 0; i < triples.size(); i++) {
            SeedSet.Entry triple = triples.get(i);
            Watched watched = new Watched((Deliberator) Brains.of(Brains.SHATTERFISH, triple, codex, weights));
            RunOutcome outcome = new RunLoop().playTriple(triple, 1000 + i, watched, CAP,
                    new RunLoop.Logging(out, COMMIT,
                            new RunLog.Brain(Brains.SHATTERFISH, COMMIT, Brains.configHash(Brains.SHATTERFISH, weights)),
                            "", "test"));
            String run = triple.seedCode() + " " + triple.heroClass() + " salt " + (1000 + i);
            for (String broken : watched.broken) {
                failures.add(run + ": " + broken);
            }
            mustEat += watched.mustEat;
            starving += watched.starvingSeen;
            Observation last = watched.last;
            boolean lastAte = watched.lastChosen instanceof Action.UseItem use && use.action().equals("EAT");
            if (outcome.cause() == RunOutcome.Cause.DEATH && last != null && !lastAte && calm(last)
                    && starvingWithFood(last)) {
                failures.add(run + ": died starving on a calm screen with food held");
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
        assertTrue(mustEat > 0, "no Run showed a calm hungry screen with food, so nothing was held to account");
        System.out.println("StarvationRegressionTest: " + mustEat + " calm hungry screens with food, " + starving
                + " starving screens with food");
    }
}
