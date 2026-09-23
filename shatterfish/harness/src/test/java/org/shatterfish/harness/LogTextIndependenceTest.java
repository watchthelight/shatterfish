package org.shatterfish.harness;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The skeptic's reader stays a second implementation (story 3.4).
 *
 * <p>{@link LogText} reads a Run log from its own text, with the JDK's own digest and no code
 * shared with the writer. {@code LogReaderAgreementTest} holds it against the production reader
 * over every line of a real log and over the lines a hand editor produces, and that agreement is
 * the evidence behind the published chain rules: two implementations reaching one answer.
 *
 * <p><b>Deleting it would be loud. Converging it would be silent.</b> Six test files stop compiling
 * without it, so nobody removes it by accident — but rewriting its body to call {@code Json} and
 * {@code RunLogVerifier} would leave every test green while turning the agreement into one
 * implementation agreeing with itself. That is the defect this project has found in six consecutive
 * stories, one level up: not a test that asserts with the code under test, but a *second
 * implementation* that quietly becomes the first one.
 *
 * <p>So the independence is a rule. The production reader has one
 * ({@code VerifierIndependenceTest}); it scans production classes only and cannot see this one,
 * which is why this rule exists separately and imports the test classes too.
 */
class LogTextIndependenceTest {

    private static final JavaClasses EVERYTHING = new ClassFileImporter()
            .importPackages("org.shatterfish");

    @Test
    @DisplayName("the skeptic's reader shares no code with the production reader it is checked against")
    void the_test_reader_is_its_own_implementation() {
        noClasses().that().haveFullyQualifiedName(LogText.class.getName())
                .should().dependOnClassesThat().resideInAPackage("org.shatterfish.harness.log")
                .because("an agreement between two implementations is evidence; an agreement"
                        + " between one implementation and a wrapper around it is not, and this"
                        + " reader is the one that may not be changed to suit the other")
                .check(EVERYTHING);
    }

    @Test
    @DisplayName("nor with the writer, which is the dependency the chain exists not to have")
    void the_test_reader_does_not_read_through_the_writer() {
        noClasses().that().haveFullyQualifiedName(LogText.class.getName())
                .should().dependOnClassesThat()
                .haveNameMatching("org\\.shatterfish\\.api\\.(RunLogJson|JsonWriter).*")
                .because("a chain recomputed through the writer would agree with any writer that"
                        + " agreed with itself")
                .check(EVERYTHING);
    }
}
