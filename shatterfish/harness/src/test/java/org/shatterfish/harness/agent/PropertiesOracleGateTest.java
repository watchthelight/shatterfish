package org.shatterfish.harness.agent;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.harness.Launcher;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The properties measurement reaches the oracle by one door and by nobody else (story 6.5, FR-11, non-negotiable 1): the
 * launcher's {@code hiddenFacts}, which refuses without {@code --oracle}. The measurement is a test-source tool; no ranked
 * Rig Run, no Overlay, no search and no Brain can call it. The Rig's and the Overlay's halves of the rule are in
 * {@code RigOracleGateTest} and {@code OverlayOracleGateTest}.
 */
class PropertiesOracleGateTest {

    private static final String THE_MEASUREMENT = "org[.]shatterfish[.]harness[.]agent[.]Properties.*";

    /**
     * Nothing in the harness, its main classes or its test classes (which hold every search the program has: the one-ply
     * leak test, the probe, the redetermination differential), calls the launcher's door except the measurement and the
     * tests that hold it.
     */
    static ArchRule onlyAsks(String allowed) {
        return noClasses()
                .that().resideInAPackage("org.shatterfish.harness..")
                .and().haveNameNotMatching("org[.]shatterfish[.]harness[.]Launcher.*")
                .and().haveNameNotMatching(allowed)
                .should().callMethod(Launcher.class, "hiddenFacts", Launcher.Launch.class)
                .because("the hidden facts are read for a measurement under --oracle and by nothing a Run, a Brain or a search can reach");
    }

    static final ArchRule ONLY_THE_MEASUREMENT_ASKS = onlyAsks(THE_MEASUREMENT);

    /** And no Brain, which is api-only, can name the harness at all. */
    static final ArchRule NO_BRAIN_NAMES_THE_LAUNCHER = noClasses()
            .that().resideInAPackage("org.shatterfish.brain..")
            .should().dependOnClassesThat().haveFullyQualifiedName(Launcher.class.getName())
            .because("a Decision is made from an Observation and a Belief alone");

    @Test
    @DisplayName("the door refuses unless the launch carries --oracle")
    void the_door_needs_the_flag() {
        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> Launcher.hiddenFacts(new Launcher.Launch(1L, false)));
        assertTrue(refused.getMessage().contains("--oracle"), refused.getMessage());
        assertTrue(Launcher.Launch.parse(new String[] {"--oracle"}).oracle());
        assertEquals(false, Launcher.Launch.parse(new String[0]).oracle(), "off by default");
    }

    @Test
    @DisplayName("the measurement runs only with --oracle, and on no set but smoke and standard")
    void the_measurement_needs_the_flag_and_the_right_set() {
        assertThrows(IllegalArgumentException.class, () -> PropertiesMeasurement.main(new String[] {"--seeds", "1"}));
        assertThrows(IllegalArgumentException.class,
                () -> PropertiesMeasurement.run(PropertiesMeasurement.Config.defaults(), new Launcher.Launch(1L, false), System.err));
        for (String set : List.of("goo", "holdout", "GOO", "Holdout", "../seeds/holdout", "bosses", "")) {
            assertThrows(IllegalArgumentException.class, () -> PropertiesMeasurement.main(new String[] {"--oracle", "--set", set}), set);
            assertThrows(IllegalArgumentException.class, () -> new PropertiesMeasurement.Config(set, 1, 1, 2, 0, 10, 1), set);
        }
    }

    @Test
    @DisplayName("only the measurement calls the door, and no Brain names the launcher; the rule bites on a fixture")
    void nothing_else_asks() {
        JavaClasses harness = new ClassFileImporter().importPackages("org.shatterfish.harness");
        ONLY_THE_MEASUREMENT_ASKS.check(harness);
        NO_BRAIN_NAMES_THE_LAUNCHER.check(new ClassFileImporter().importPackages("org.shatterfish.brain"));
        // The rule sees the call: with the measurement not exempt, the measurement itself is a violation.
        assertTrue(onlyAsks("a[.]name[.]that[.]matches[.]nothing").evaluate(new ClassFileImporter().importClasses(PropertiesMeasurement.class)).hasViolation(),
                "a class asking for the hidden facts that is not exempt passes the rule");
    }
}
