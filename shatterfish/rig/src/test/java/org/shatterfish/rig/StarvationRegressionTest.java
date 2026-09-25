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
 *       and a food the hunger takes whole is held, likewise. Starving costs {@code HT/1000} a turn
 *       (docs/rules/buffs.md), so a hero cannot starve to death without passing through many of
 *       these screens.</li>
 *   <li>No Run ends with its last screen calm, starving, and a known food in the pack: the only way
 *       such a hero dies with nothing in view is starving (or a trap or burn it could not see).</li>
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

    /** A food the eat Policy knows, which the screen offers to eat. */
    private static boolean edible(Observation observation, ItemView item, int most) {
        Integer energy = Brain.foods().get(item.name());
        return item.kind() == ItemKind.FOOD && energy != null && energy <= most && item.actions().contains("EAT");
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
        Observation last;

        Watched(Deliberator brain) {
            this.brain = brain;
        }

        @Override
        public Action decide(Observation observation) {
            Action chosen = brain.decide(observation);
            last = observation;
            if (mustEat(observation)) {
                mustEat++;
                if (!(chosen instanceof Action.UseItem use && use.action().equals("EAT"))) {
                    broken.add(observation.hero().hunger() + " with food, calm, and the Brain chose " + chosen);
                }
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
            Observation last = watched.last;
            if (outcome.cause() == RunOutcome.Cause.DEATH && last != null && calm(last)
                    && last.hero().hunger() == Hunger.STARVING
                    && last.inventory().items().stream().anyMatch(item -> edible(last, item, Integer.MAX_VALUE))) {
                failures.add(run + ": died starving on a calm screen with food held");
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
        assertTrue(mustEat > 0, "no Run showed a calm hungry screen with food, so nothing was held to account");
    }
}
