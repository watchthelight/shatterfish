package org.shatterfish.harness.log;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The ending record (killer, floor, health, bosses by name) is diagnosis for the Rig. The Brain and
 * everything that builds its Observation or executes its Actions must not be able to name it, or the
 * game's state would have a way to the Brain that the Observer does not guard (E10 W1, the ADR-0011
 * amendment of 2026-10-28).
 */
class EndingRecordIsolationTest {

    private static final JavaClasses ALL = new ClassFileImporter().importPackages("org.shatterfish");

    private static final String RECORD = "org\\.shatterfish\\.harness\\.log\\.EndingRecord(\\$.*)?";

    @Test
    @DisplayName("neither the Brain, the api, the Observer nor the executor can name the ending record")
    void nothing_that_feeds_the_brain_names_the_record() {
        noClasses().that().resideInAnyPackage("org.shatterfish.brain..", "org.shatterfish.api..",
                        "org.shatterfish.harness.observer..", "org.shatterfish.harness.executor..")
                .should().dependOnClassesThat().haveNameMatching(RECORD)
                .because("the record is written after play and nothing read by a decision may see it")
                .check(ALL);
    }

    @Test
    @DisplayName("only the Run loop writes it and only the Rig reads it")
    void only_the_loop_and_the_rig_use_it() {
        noClasses().that().haveNameNotMatching("org\\.shatterfish\\.harness\\.agent\\.RunLoop(\\$.*)?")
                .and().resideOutsideOfPackage("org.shatterfish.rig..")
                .and().haveNameNotMatching(RECORD)
                .and().haveNameNotMatching(".*Test(\\$.*)?")
                .should().dependOnClassesThat().haveNameMatching(RECORD)
                .because("one writer and one reader keep the record's use easy to audit")
                .check(ALL);
    }
}
