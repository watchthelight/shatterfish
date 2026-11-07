package org.shatterfish.harness.agent;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.harness.Launcher;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The identity calibration reads the true identities by one door and nobody else does (issue #410, non-negotiable 1):
 * the launcher's {@code identityTruth}, which refuses without {@code --oracle}. The calibration is a test-source tool
 * that scores the Brain's odds; no Rig Run, no Overlay, no search and no Brain can call the door.
 */
class IdentityOracleGateTest {

    private static final String THE_CALIBRATION = "org[.]shatterfish[.]harness[.]agent[.](IdentityCalibration|IdentityOracleGateTest).*";

    static ArchRule onlyAsks(String allowed) {
        return noClasses()
                .that().resideInAPackage("org.shatterfish.harness..")
                .and().haveNameNotMatching("org[.]shatterfish[.]harness[.]Launcher.*")
                .and().haveNameNotMatching(allowed)
                .should().callMethod(Launcher.class, "identityTruth", Launcher.Launch.class)
                .because("the true identities are read to score the Brain under --oracle and by nothing a Run, a Brain or a search can reach");
    }

    @Test
    @DisplayName("the door refuses unless the launch carries --oracle")
    void the_door_needs_the_flag() {
        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> Launcher.identityTruth(new Launcher.Launch(1L, false)));
        assertTrue(refused.getMessage().contains("--oracle"), refused.getMessage());
    }

    @Test
    @DisplayName("the calibration runs only with --oracle, and on no set but smoke and standard")
    void the_calibration_needs_the_flag_and_the_right_set() {
        assertThrows(IllegalArgumentException.class, () -> IdentityCalibration.main(new String[] {"--seeds", "1"}));
        for (String set : List.of("goo", "holdout", "GOO", "Holdout", "../seeds/holdout", "tengu", "")) {
            assertThrows(IllegalArgumentException.class, () -> new IdentityCalibration(set, 1, 10, System.err), set);
        }
    }

    @Test
    @DisplayName("only the calibration calls the door; the rule bites on a fixture")
    void nothing_else_asks() {
        onlyAsks(THE_CALIBRATION).check(new ClassFileImporter().importPackages("org.shatterfish.harness"));
        assertTrue(onlyAsks("a[.]name[.]that[.]matches[.]nothing").evaluate(new ClassFileImporter().importPackages("org.shatterfish.harness.agent").that(com.tngtech.archunit.base.DescribedPredicate.describe("the calibration", c -> c.getName().startsWith(IdentityCalibration.class.getName())))).hasViolation(),
                "a class asking for the true identities that is not exempt passes the rule");
    }
}
