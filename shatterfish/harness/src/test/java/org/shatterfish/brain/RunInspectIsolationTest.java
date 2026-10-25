package org.shatterfish.brain;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * The inspection tool keeps its oracle reads apart (issues #181-#184): everything that reads the game's own
 * state sits in {@code RunInspectTest.Oracle}, and every other class of the tool, the Inspector that holds
 * the Brain and the Belief, any class nested or anonymous inside it, and the test itself, reads none of it.
 * That is a rule rather than a comment, because a later edit that added one {@code Dungeon.level} read to
 * the Inspector would hand the Pickup and Explore Policies a level they could not have seen, and the dump
 * would no longer show why the Brain chose what it chose.
 *
 * <p>The Oracle side is a whitelist: it may depend on the game, the JDK and itself, and nothing else, so
 * it holds no Brain, Belief, Observation, Memory or Inspector (nested types such as {@code Brain.Decided}
 * included); its fields are private and every method it shows the Inspector returns void, so nothing it
 * read can flow out of it.
 */
class RunInspectIsolationTest {

    private static final JavaClasses TESTS = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
            .importPackages("org.shatterfish.brain");

    private static final String ORACLE = "org\\.shatterfish\\.brain\\.RunInspectTest\\$Oracle(\\$.*)?";

    @Test
    @DisplayName("no class of the tool but the Oracle reads the game or an oracle Observer")
    void nothing_but_the_oracle_reads_game_state() {
        noClasses().that().haveNameMatching("org\\.shatterfish\\.brain\\.RunInspectTest(\\$.*)?")
                .and().haveNameNotMatching(ORACLE)
                .should().dependOnClassesThat().resideInAnyPackage("com.shatteredpixel..", "com.watabou..")
                .orShould().dependOnClassesThat().haveNameMatching("org\\.shatterfish\\.harness\\.observer\\.Oracle.*")
                .because("oracle reads belong in RunInspectTest.Oracle, which is never given the Brain")
                .check(TESTS);
    }

    @Test
    @DisplayName("the Oracle depends on the game, the JDK and itself only")
    void the_oracle_depends_on_nothing_of_the_brain() {
        classes().that().haveNameMatching(ORACLE)
                .should().onlyDependOnClassesThat(
                        JavaClass.Predicates.resideInAnyPackage("com.shatteredpixel..", "com.watabou..", "java..")
                                .or(JavaClass.Predicates.belongToAnyOf(RunInspectTest.Oracle.class)))
                .because("true state may reach a person reading the dump and never the Brain")
                .check(TESTS);
    }

    @Test
    @DisplayName("what the Oracle shows the Inspector returns nothing and holds nothing")
    void the_oracle_gives_nothing_back() {
        methods().that().areDeclaredInClassesThat().haveNameMatching(ORACLE)
                .and().doNotHaveModifier(JavaModifier.PRIVATE)
                .and().doNotHaveModifier(JavaModifier.SYNTHETIC)
                .should().haveRawReturnType(void.class)
                .because("a value returned from the Oracle is a way for the level to reach the Brain")
                .check(TESTS);
        fields().that().areDeclaredInClassesThat().haveNameMatching(ORACLE)
                .should().bePrivate()
                .because("a readable field is a way for the level to reach the Brain")
                .check(TESTS);
    }
}
