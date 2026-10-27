package org.shatterfish.overlay;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.harness.observer.OracleObserver;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.core.domain.JavaAccess.Predicates.target;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The Overlay's rule for the oracle, which {@code OracleObserver} asks every module built on the
 * harness to carry (story 5.1, FR-11, non-negotiable 1).
 *
 * <p>The launcher is the one place an oracle Run may be asked for; this holds that it is the one
 * place one can be made, and that nothing in the Overlay reaches the Rig, whose Runs are ranked and
 * which refuses the oracle flag by name.
 */
class OverlayOracleGateTest {

    private static JavaClasses overlay() {
        return new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("org.shatterfish.overlay");
    }

    static final ArchRule ONLY_THE_LAUNCHER_MAKES_AN_ORACLE = noClasses()
            .that().resideInAPackage("org.shatterfish.overlay..")
            .and().doNotHaveFullyQualifiedName(ShatterfishLauncher.class.getName())
            .should().callConstructor(OracleObserver.class)
            .orShould().dependOnClassesThat().haveFullyQualifiedName(OracleObserver.class.getName())
            .because("the launcher's --oracle is the one door to an oracle Run in the Overlay (FR-11)");

    /** The harness launcher's {@code hiddenFacts} is the properties measurement's door (story 6.5); the Overlay has its own launcher. */
    static final ArchRule THE_OVERLAY_DOES_NOT_ASK_THE_HARNESS_LAUNCHER = noClasses()
            .that().resideInAPackage("org.shatterfish.overlay..")
            .should().dependOnClassesThat().haveFullyQualifiedName(org.shatterfish.harness.Launcher.class.getName())
            .because("the oracle door of a measurement is not the Overlay's, whose --oracle is its own launcher's");

    static final ArchRule THE_OVERLAY_IS_NOT_THE_RIG = noClasses()
            .that().resideInAPackage("org.shatterfish.overlay..")
            .should().dependOnClassesThat().resideInAPackage("org.shatterfish.rig..")
            .because("a ranked Run is the Rig's, and the Overlay's oracle must have no path to one");

    static final ArchRule NO_CLASS_BY_NAME = noClasses()
            .that().resideInAPackage("org.shatterfish.overlay..")
            .should().accessTargetWhere(target(name("forName")))
            .because("a class reached by its name is a constructor call the dependency rules cannot see");

    @Test
    @DisplayName("only the launcher makes an oracle observer")
    void only_the_launcher() {
        ONLY_THE_LAUNCHER_MAKES_AN_ORACLE.check(overlay());
    }

    @Test
    @DisplayName("nothing in the Overlay depends on the Rig")
    void not_the_rig() {
        THE_OVERLAY_IS_NOT_THE_RIG.check(overlay());
    }

    @Test
    @DisplayName("nothing in the Overlay asks the harness launcher for the hidden facts")
    void not_the_harness_launcher() {
        THE_OVERLAY_DOES_NOT_ASK_THE_HARNESS_LAUNCHER.check(overlay());
    }

    @Test
    @DisplayName("nothing in the Overlay reaches a class by its name")
    void no_class_by_name() {
        NO_CLASS_BY_NAME.check(overlay());
    }

    @Test
    @DisplayName("the rules bite: the launcher itself does make one")
    void the_rule_bites() {
        ArchRule theLauncher = noClasses().that().haveFullyQualifiedName(ShatterfishLauncher.class.getName())
                .should().dependOnClassesThat(describe("the oracle",
                        c -> c.getFullName().equals(OracleObserver.class.getName())));
        org.junit.jupiter.api.Assertions.assertTrue(theLauncher.evaluate(overlay()).hasViolation(),
                "the launcher's --oracle branch is seen by the rule");
    }

    @Test
    @DisplayName("the Beliefs view (story 8.6): without the flag the same view carries no oracle line, and no sidecar or view of the oracle makes one")
    void the_beliefs_view_has_no_oracle_line_without_the_flag() {
        org.shatterfish.api.BeliefView view = new org.shatterfish.api.BeliefView("h",
                java.util.List.of(new org.shatterfish.api.BeliefView.Appearance("crimson potion",
                        java.util.List.of(new org.shatterfish.api.BeliefView.Candidate("potion of frost", 1.0)))),
                java.util.List.of(), java.util.List.of(), java.util.List.of());

        // A fair Run's Panel is given no rows, so its content is the Brain's lines alone.
        org.junit.jupiter.api.Assertions.assertTrue(BeliefViewContent.of(view).texts().stream()
                .noneMatch(line -> line.startsWith(OracleMarkContent.PREFIX)));
        // Nothing read by the oracle, no rows; and a sidecar that holds no read answers none.
        org.junit.jupiter.api.Assertions.assertTrue(OracleMarkContent.beliefRows(null, view).isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(new OracleSidecar().beliefRows(null, view).isEmpty());
        // And a fair Run cannot be given a sidecar to make any.
        PanelDock fair = new PanelDock();
        fair.oracle(false);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> fair.oracleSidecar(new OracleSidecar()));
    }
}
