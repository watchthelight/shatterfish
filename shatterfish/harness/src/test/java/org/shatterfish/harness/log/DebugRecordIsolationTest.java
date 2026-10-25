package org.shatterfish.harness.log;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The debug record is written by the Rig side after play and never read by the Brain or by anything
 * that builds an Observation (issue #219, the ADR-0011 update of 2026-10-25). That is a rule and not
 * a comment, since one read of it from the Observer or the Brain would hand the Brain the water
 * under Goo and the pumps it was not shown.
 */
class DebugRecordIsolationTest {

    private static final JavaClasses ALL = new ClassFileImporter().importPackages("org.shatterfish");

    private static final String RECORD = "org\\.shatterfish\\.harness\\.log\\.DebugRecord(\\$.*)?";

    @Test
    @DisplayName("neither the Brain, the api, nor the Observer can name the debug record")
    void nothing_that_feeds_the_brain_names_the_record() {
        noClasses().that().resideInAnyPackage("org.shatterfish.brain..", "org.shatterfish.api..",
                        "org.shatterfish.harness.observer..", "org.shatterfish.harness.executor..",
                        "org.shatterfish.codex..")
                .should().dependOnClassesThat().haveNameMatching(RECORD)
                .because("the record is written after play and nothing read by a decision may see it")
                .check(ALL);
    }

    @Test
    @DisplayName("only the Run loop uses the debug record, and its writing calls return nothing")
    void only_the_loop_writes_it_and_nothing_comes_back() {
        noClasses().that().haveNameNotMatching("org\\.shatterfish\\.harness\\.agent\\.RunLoop(\\$.*)?")
                .and().haveNameNotMatching(RECORD)
                .and().haveNameNotMatching(".*Test(\\$.*)?")
                .should().dependOnClassesThat().haveNameMatching(RECORD)
                .because("one caller keeps the record's use easy to audit")
                .check(ALL);
        methods().that().arePublic().and().areDeclaredInClassesThat().haveNameMatching(RECORD)
                .and().haveNameMatching("wait|end")
                .should().haveRawReturnType(void.class)
                .because("a value returned from the record is a way for the game's state to reach the loop")
                .check(ALL);
    }
}
