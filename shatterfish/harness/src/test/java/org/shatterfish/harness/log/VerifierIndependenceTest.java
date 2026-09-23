package org.shatterfish.harness.log;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The chain checker does not depend on the thing that writes chains (story 3.4).
 *
 * <p>A hash chain exists so a file can be checked without the tool that produced it. A checker that
 * re-rendered each record through {@code RunLogJson} and compared would agree with any writer that
 * agreed with itself — including a wrong one — which is not a check, it is a mirror. So
 * {@link RunLogVerifier} takes each line's bytes as they are, strips the unchained keys by name,
 * and hashes with the JDK's own digest.
 *
 * <p>That is a property of the code, not of anyone's intentions, so it is a rule rather than a
 * comment: a comment saying "do not import the writer here" does not survive a refactor, and the
 * import that broke it would look like a simplification in review. The published rules on the
 * methodology page are what a stranger's third implementation is written from, and this is what
 * keeps the second one from quietly becoming the first one again.
 */
class VerifierIndependenceTest {

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("org.shatterfish");

    @Test
    @DisplayName("the chain checker does not reach the writer, so agreeing with it is evidence")
    void the_verifier_does_not_depend_on_the_writer() {
        noClasses().that().haveFullyQualifiedName(RunLogVerifier.class.getName())
                .should().dependOnClassesThat().haveNameMatching("org\\.shatterfish\\.api\\.RunLogJson.*")
                .because("a chain recomputed through the writer would agree with any writer that"
                        + " agreed with itself, which is the one thing a chain exists not to be")
                .check(PRODUCTION);
    }

    @Test
    @DisplayName("nor does it reach the reader, so a record it could not parse is still a chain it can check")
    void the_verifier_does_not_depend_on_the_reader() {
        // A log holding a kind this build has never heard of still chains, and a checker that had
        // to parse a record before it could hash the line would refuse the file instead of saying
        // the bytes are intact. That is the difference between "this is not a Run I understand"
        // and "this file was edited", and the Rig reports them differently.
        noClasses().that().haveFullyQualifiedName(RunLogVerifier.class.getName())
                .should().dependOnClassesThat().haveFullyQualifiedName(RunLogReader.class.getName())
                .because("whether a file's bytes are intact is a separate question from whether"
                        + " this build understands the records in it")
                .check(PRODUCTION);
    }
}
