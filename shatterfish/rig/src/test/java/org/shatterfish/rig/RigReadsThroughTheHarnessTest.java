package org.shatterfish.rig;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.harness.log.Json;
import org.shatterfish.harness.log.RunLogReader;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The Rig reads a log through the harness's reader, and never through the writer (story 3.4).
 *
 * <p>Story 3.3 left {@link LogHeader} walking the characters of a log line itself, because at the
 * time the only other reader was in the harness's <em>test</em> sources and a production class may
 * not depend on those. Story 3.4 put a reader in the harness's production sources, and at that
 * moment the Rig's copy stopped being the second implementation of the grammar and became the
 * third — one more place for a drift to hide, with no more honesty bought.
 *
 * <p>So the Rig now reads through the harness, and this is the rule that keeps it there. The
 * arrangement worth having is exactly two implementations: one in production and one deliberately
 * independent one in the harness's tests, with a test that they agree on a real log. Adding a
 * third does not make that agreement stronger; it makes it a majority vote.
 */
class RigReadsThroughTheHarnessTest {

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("org.shatterfish.rig");

    @Test
    @DisplayName("nothing in the Rig reads a log through the thing that wrote it")
    void the_rig_does_not_read_through_the_writer() {
        // The Rig's job on a log is to check a claim made by a process it started: the oracle flag
        // FR-11 keys on, and the chain a Results page publishes. A check performed with the
        // writer's own rendering would pass for any writer consistent with itself, which is the
        // shape of defect that survived four reviews in each of three stories.
        noClasses().that().resideInAPackage("org.shatterfish.rig")
                .should().dependOnClassesThat()
                .haveNameMatching("org\\.shatterfish\\.api\\.RunLogJson.*")
                .because("a reader built out of the writer agrees with any writer that agrees with"
                        + " itself, and the Rig is where a Run's own claims are checked")
                .check(PRODUCTION);
    }

    @Test
    @DisplayName("the Rig's view of a log is built on the harness's reader, not on a third one")
    void the_rig_reads_through_the_harness() {
        classes().that().haveFullyQualifiedName(LogHeader.class.getName())
                .should().dependOnClassesThat()
                .haveFullyQualifiedName(RunLogReader.class.getName())
                .andShould().dependOnClassesThat().haveFullyQualifiedName(Json.class.getName())
                .because("two implementations of one grammar keep each other honest; three are two"
                        + " places for a drift to hide")
                .check(PRODUCTION);
    }
}
