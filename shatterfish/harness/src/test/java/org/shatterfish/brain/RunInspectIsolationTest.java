package org.shatterfish.brain;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The inspection tool keeps its oracle reads apart (issues #181-#184): everything that reads the game's own
 * state sits in {@code RunInspectTest.Oracle}, and the class that holds the Brain and the Belief reads none
 * of it. That is a rule rather than a comment, because a later edit that added one {@code Dungeon.level}
 * read to the Inspector would hand the Pickup and Explore Policies a level they could not have seen, and the
 * dump would no longer show why the Brain chose what it chose.
 */
class RunInspectIsolationTest {

    private static final JavaClasses TESTS = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
            .importPackages("org.shatterfish.brain");

    @Test
    @DisplayName("the Inspector, which holds the Brain, reads no class of the game")
    void the_inspector_reads_no_game_state() {
        noClasses().that().haveFullyQualifiedName("org.shatterfish.brain.RunInspectTest$Inspector")
                .or().haveFullyQualifiedName("org.shatterfish.brain.RunInspectTest")
                .should().dependOnClassesThat().resideInAnyPackage("com.shatteredpixel..", "com.watabou..")
                .because("oracle reads belong in RunInspectTest.Oracle, which is never given the Brain")
                .check(TESTS);
    }

    @Test
    @DisplayName("the Oracle is given no Brain, Belief or Observation")
    void the_oracle_holds_nothing_of_the_brain() {
        noClasses().that().haveFullyQualifiedName("org.shatterfish.brain.RunInspectTest$Oracle")
                .should().dependOnClassesThat().haveSimpleNameStartingWith("Brain")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("org.shatterfish.api.Belief")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("org.shatterfish.api.Observation")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("org.shatterfish.brain.Memory")
                .because("true state may reach a person reading the dump and never the Brain")
                .check(TESTS);
    }
}
