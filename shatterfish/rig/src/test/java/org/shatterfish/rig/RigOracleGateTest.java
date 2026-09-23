package org.shatterfish.rig;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.harness.observer.OracleObserver;
import org.shatterfish.harness.observer.OracleView;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.core.domain.JavaAccess.Predicates.target;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Rig plays no oracle Run (story 3.3, FR-11, non-negotiable 1).
 *
 * <p>{@code OracleObserver} says in its own javadoc that "a module built on the harness carries its
 * own rule when it arrives". The Rig is that module: it is the only thing that plays ranked Runs,
 * it depends on the harness and on the brain, and both {@code OracleObserver} and the six-argument
 * {@code RunLoop.Logging} that carries the oracle flag are public and constructible from here. The
 * fairness review of this story pointed out that until this file existed, the Rig's oracle story
 * rested on "there is no flag yet" — which is an intention, and non-negotiable 1 says the rule is
 * enforced by architecture instead.
 *
 * <p>The runner also reads every finished log's header back and refuses an invocation whose Run
 * claims the oracle. That is the check on the artifact; this is the check on the code. Neither is
 * redundant: the first would survive someone adding a flag, and the second would survive someone
 * writing a log by hand.
 */
class RigOracleGateTest {

    /** Nothing in the Rig builds an oracle observer, or holds what one returns. */
    static final ArchRule THE_RIG_MAKES_NO_ORACLE = noClasses()
            .that().resideInAPackage("org.shatterfish.rig..")
            .should().callConstructor(OracleObserver.class)
            .orShould().dependOnClassesThat().haveFullyQualifiedName(OracleObserver.class.getName())
            .orShould().dependOnClassesThat().haveFullyQualifiedName(OracleView.class.getName())
            .orShould().dependOnClassesThat().haveFullyQualifiedName(OracleObserver.Read.class.getName())
            .because("the Rig plays the Runs a number is published from, and an oracle Run is not one"
                    + " of them (FR-11); an oracle mode is for debugging and cannot be enabled in a"
                    + " ranked Run");

    /**
     * Nothing in the Rig reaches a class by its name. {@code Class.forName} and the reflective
     * lookups the game ships are the one way to build an oracle that the dependency rule above
     * cannot see.
     */
    static final ArchRule NO_CLASS_BY_NAME = noClasses()
            .that().resideInAPackage("org.shatterfish.rig..")
            .should().accessTargetWhere(target(name("forName")))
            .because("a class reached by its name is a constructor call the dependency rules cannot see");

    /**
     * Nothing in the Rig reads the environment, and only the runner reads a system property.
     *
     * <p>An environment variable or a {@code -D} is a second command line, and the flag this
     * command must never grow is an oracle — a list of flags nobody can add to is worth little if
     * the same request can arrive another way. The runner is the one exception and its reads are
     * visible in one place: where the child's JVM is ({@code java.home}), what to put on its
     * classpath ({@code java.class.path}), and what machine this is, which the summary records.
     * None of those can ask for anything.
     */
    static final ArchRule NO_SECOND_COMMAND_LINE = noClasses()
            .that().resideInAPackage("org.shatterfish.rig..")
            .and().doNotHaveFullyQualifiedName(Runner.class.getName())
            .should().accessTargetWhere(target(name("getenv")))
            .orShould().accessTargetWhere(target(name("getProperty"))
                    .and(describe("on the system, not on a properties file",
                            access -> access.getTargetOwner().getName().equals("java.lang.System"))))
            .because("a Run's arguments are the Rig's command line and nothing else; an environment"
                    + " variable would be a way to ask for an oracle that no flag list mentions");

    /** And the runner reads no environment variable either, whatever properties it needs. */
    static final ArchRule THE_RIG_READS_NO_ENVIRONMENT = noClasses()
            .that().resideInAPackage("org.shatterfish.rig..")
            .should().accessTargetWhere(target(name("getenv")))
            .because("nothing the Rig does depends on what was exported into its shell");

    @Test
    @DisplayName("no class of the Rig can build an oracle, name one, or be told to want one")
    void the_rig_cannot_reach_an_oracle() {
        JavaClasses rig = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("org.shatterfish.rig");

        THE_RIG_MAKES_NO_ORACLE.check(rig);
        NO_CLASS_BY_NAME.check(rig);
        NO_SECOND_COMMAND_LINE.check(rig);
        THE_RIG_READS_NO_ENVIRONMENT.check(rig);
    }

    @Test
    @DisplayName("the Rig's command line holds no oracle, and the child's holds none either")
    void neither_command_line_has_one() {
        // Both lists, by name. The parent's is the one an operator types; the child's is the one
        // the parent builds, and a flag added there would be reachable by anyone who runs the child
        // by hand -- which the child's own javadoc invites, since it is where a single Run is
        // debugged.
        for (String flag : Runner.KNOWN) {
            assertFalse(flag.toLowerCase(java.util.Locale.ROOT).contains("oracle"),
                    "the Rig's command line grew " + flag);
        }
        for (String flag : RunOne.KNOWN) {
            assertFalse(flag.toLowerCase(java.util.Locale.ROOT).contains("oracle"),
                    "a Run's command line grew " + flag);
        }
        // The counts as well as the names. A flag whose name does not say "oracle" can still be
        // one, so the size is what makes a new flag a decision somebody made here rather than a
        // line that slipped past a substring check. Story 3.4 added `--verify` and `--replay`.
        assertTrue(Runner.KNOWN.size() == 13 && RunOne.KNOWN.size() == 12,
                "the two lists are " + Runner.KNOWN + " and " + RunOne.KNOWN);
    }
}
