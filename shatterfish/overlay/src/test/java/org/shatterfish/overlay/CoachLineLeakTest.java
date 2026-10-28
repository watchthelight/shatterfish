package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.properties.HasName;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.shatterfish.api.Action;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;
import org.shatterfish.brain.BrainDecider;
import org.shatterfish.harness.agent.HiddenPermutation;
import org.shatterfish.harness.agent.RealBrain;
import org.shatterfish.harness.agent.RunLoop;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The coach says only what the bot knows (story 8.8, FR-47, non-negotiable 1), in the style of the Observer's
 * leak tests: two games that differ in hidden state only -- two unidentified potion, scroll and ring classes trade
 * the labels they draw under, and every floor item of those classes trades with its twin, as
 * {@code PermutedIdentitiesDifferentialTest} does (story 4.14) -- are played by the real Brain, and at every wait at
 * which the Observation is the same bytes in both, the sentence is the same. The permutation is made here, in the
 * game, and never handed to the Brain or to {@link CoachLine}. Seeds are Warriors of the {@code standard} set,
 * never {@code goo} or {@code holdout}.
 *
 * <p>The control: the sentences are not one constant line (some Policies and reasons are met), and the worlds do
 * part on at least one seed, so the permutation came to show and the claim was tried and not vacuous.
 */
@Timeout(value = 15, unit = TimeUnit.MINUTES)
class CoachLineLeakTest {

    private static final long SALT = 0x5A17_5A17L;
    private static final int TURN_CAP = 1_200;
    private static final int SEEDS = 2;
    private static final int MIN_PREFIX = 25;

    private record Step(String observation, String sentence) {
    }

    /** Decisions of the real Brain, over all Runs here, that no template phrased: "policy | why". */
    private static final Set<String> UNPHRASED = new java.util.TreeSet<>();

    @Test
    @DisplayName("two games that differ in hidden state only give equal sentences wherever the screens are equal")
    void equal_screens_equal_sentences() throws IOException {
        boolean someRunDiverged = false;
        Set<String> distinct = new HashSet<>();
        int withReason = 0;
        for (long seed : warriorSeeds()) {
            List<Step> a = play(seed, false);
            List<Step> b = play(seed, true);
            int limit = Math.min(a.size(), b.size());
            int prefix = 0;
            while (prefix < limit && a.get(prefix).observation().equals(b.get(prefix).observation())) {
                assertEquals(a.get(prefix).sentence(), b.get(prefix).sentence(),
                        "seed " + seed + ": the same screen, the same sentence, at wait " + (prefix + 1));
                distinct.add(a.get(prefix).sentence());
                if (a.get(prefix).sentence().contains(": ")) {
                    withReason++;
                }
                prefix++;
            }
            assertTrue(prefix >= MIN_PREFIX, "seed " + seed + ": the worlds must look alike for a while, but parted at wait " + (prefix + 1));
            someRunDiverged |= prefix < limit;
        }
        System.out.println("COACH distinct sentences: " + distinct.size() + "; unphrased: " + UNPHRASED);
        assertTrue(UNPHRASED.isEmpty(), "the real Brain wrote a reason no template phrases: " + UNPHRASED);
        assertTrue(distinct.size() >= 5, "the sentences are not one line: " + distinct);
        assertTrue(withReason > 0, "some sentence gave a reason");
        assertTrue(HiddenPermutation.exchanged() > 0, "some floor item changed class");
        assertTrue(someRunDiverged, "the permuted identities must come to show on at least one seed, or they were never in play");
    }

    @Test
    @DisplayName("the composer's inputs are the Decision and the Observation, and no Oracle flag: the sentence is the same with it on")
    void inputs_are_the_decision_and_the_observation() {
        List<Method> composers = new ArrayList<>();
        for (Method method : CoachLine.class.getDeclaredMethods()) {
            if (method.getName().equals("of")) {
                composers.add(method);
            }
        }
        assertEquals(1, composers.size(), "one way in");
        assertEquals(List.of(RunLog.Decision.class, Observation.class), Arrays.asList(composers.get(0).getParameterTypes()));
        assertTrue(Arrays.stream(CoachLine.class.getDeclaredFields()).noneMatch(f -> !java.lang.reflect.Modifier.isStatic(f.getModifiers())),
                "no state beside the arguments");
    }

    @Test
    @DisplayName("CoachLine and its nested classes depend only on the API, ActionText and java.*: no game class can be read inside it")
    void the_composer_cannot_reach_the_game() {
        String own = "org\\.shatterfish\\.overlay\\.CoachLine(\\$.*)?";
        String allowed = own + "|org\\.shatterfish\\.overlay\\.(ActionText|ActionContext)(\\$.*)?";
        ArchRule rule = classes().that().haveNameMatching(own)
                .should().onlyDependOnClassesThat(
                        JavaClass.Predicates.resideInAnyPackage("org.shatterfish.api..", "java..")
                                .or(HasName.Predicates.nameMatching(allowed)))
                .because("the sentence is a function of the Decision and the Observation, and the game is not read in it (non-negotiable 1)");
        JavaClasses main = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("org.shatterfish.overlay");
        assertTrue(main.contain(CoachLine.class), "the rule has a subject");
        rule.check(main);
    }

    private static List<Long> warriorSeeds() throws IOException {
        String text = Files.readString(RealBrain.root().resolve("seeds").resolve("standard.json"));
        Matcher entry = Pattern.compile("\"heroClass\":\"WARRIOR\",\"seed\":(\\d+)").matcher(text);
        List<Long> seeds = new ArrayList<>();
        while (seeds.size() < SEEDS && entry.find()) {
            seeds.add(Long.parseLong(entry.group(1)));
        }
        assertEquals(SEEDS, seeds.size(), "enough Warriors in the standard set");
        return seeds;
    }

    private static List<Step> play(long seed, boolean permuted) {
        BrainDecider brain = RealBrain.decider();
        List<Step> steps = new ArrayList<>();
        new RunLoop().play(seed, HeroClass.WARRIOR, SALT, (Observation observation) -> {
            if (permuted && steps.isEmpty()) {
                HiddenPermutation.permute();
            }
            Action action = brain.decide(observation);
            RunLog.Decision decided = brain.lastDecision();
            if (decided != null && CoachLine.template(decided) == null) {
                UNPHRASED.add(decided.policy() + " | " + decided.chosen().why());
            }
            steps.add(new Step(observation.hash(), String.valueOf(CoachLine.of(brain.lastDecision(), observation))));
            return action;
        }, TURN_CAP);
        return steps;
    }
}
