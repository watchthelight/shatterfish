package org.shatterfish.brain;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Brain carries nothing from one wait to the next except what {@link BrainDecider} holds (story
 * 5.1's fairness review).
 *
 * <p>The Overlay's Run drops an answer that went stale and puts the decider back to where it stood
 * before it was asked ({@code Rewindable}). {@link BrainDecider}'s mark is its four fields, the Belief,
 * the last Decision, why and the highlights, and that is complete only if nothing else in the Brain
 * changes when it decides: every other field in the module, instance or static, is final. A field
 * added anywhere else that a decision writes would be state the rewind forgets, so it fails here and
 * has to be carried in the Belief or in the mark on purpose. (A final field can still point at
 * something mutable; the Brain's are immutable data, weights and the Codex, and {@code
 * BrainDeterminismTest} holds that deciding twice gives the same answer.)
 */
class BrainHoldsNoStateTest {

    /**
     * The scratch objects a single call builds and drops: the wait's random stream ({@link Stream},
     * made per wait from the Brain's seed and the wait's index) and the Belief's byte reader and
     * writer ({@link Bytes}). Their fields change as they are used; {@link #SCRATCH_IS_NEVER_KEPT}
     * holds that no field anywhere keeps one past the call.
     */
    private static final String[] SCRATCH = {Stream.class.getName(), Bytes.Reader.class.getName(),
            Bytes.Writer.class.getName()};

    static final ArchRule ONLY_THE_DECIDER_CARRIES_STATE = fields()
            .that().areDeclaredInClassesThat().resideInAPackage("org.shatterfish.brain..")
            .and().areDeclaredInClassesThat().doNotHaveFullyQualifiedName(BrainDecider.class.getName())
            .and().areDeclaredInClassesThat().doNotHaveFullyQualifiedName(SCRATCH[0])
            .and().areDeclaredInClassesThat().doNotHaveFullyQualifiedName(SCRATCH[1])
            .and().areDeclaredInClassesThat().doNotHaveFullyQualifiedName(SCRATCH[2])
            .should().beFinal()
            .because("the Brain's state between waits is BrainDecider's, which Rewindable puts back whole");

    static final ArchRule SCRATCH_IS_NEVER_KEPT = fields()
            .that().areDeclaredInClassesThat().resideInAPackage("org.shatterfish.brain..")
            .should().notHaveRawType(SCRATCH[0])
            .andShould().notHaveRawType(SCRATCH[1])
            .andShould().notHaveRawType(SCRATCH[2])
            .because("a stream or a byte buffer kept in a field would be state between waits the rewind forgets");

    @Test
    @DisplayName("every field in the Brain but BrainDecider's and a call's scratch is final, and no field keeps the scratch")
    void only_the_decider_carries_state() {
        JavaClasses brain = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("org.shatterfish.brain");
        assertFalse(brain.isEmpty());
        ONLY_THE_DECIDER_CARRIES_STATE.check(brain);
        SCRATCH_IS_NEVER_KEPT.check(brain);
    }

    /** A Policy that keeps the wait's stream for the next one. */
    static final class KeepsTheStream {
        private final Stream kept = null;
    }

    /** A Policy that remembers something between decisions outside the Belief. */
    static final class Remembers {
        int lastCell;
    }

    /** And one that keeps it in a static. */
    static final class RemembersStatically {
        static int lastCell;
    }

    @Test
    @DisplayName("the rule bites: a mutable field, instance or static, is refused; a final one is not")
    void the_rule_bites() {
        for (Class<?> offender : new Class<?>[] {Remembers.class, RemembersStatically.class}) {
            assertTrue(ONLY_THE_DECIDER_CARRIES_STATE.evaluate(new ClassFileImporter().importClasses(offender))
                    .hasViolation(), offender.getSimpleName());
        }
        assertFalse(ONLY_THE_DECIDER_CARRIES_STATE.evaluate(new ClassFileImporter().importClasses(Weighed.class))
                .hasViolation());
        assertTrue(SCRATCH_IS_NEVER_KEPT.evaluate(new ClassFileImporter().importClasses(KeepsTheStream.class))
                .hasViolation(), "a final field that keeps the stream is refused too");
    }

    private record Weighed(int weight) {
    }
}
