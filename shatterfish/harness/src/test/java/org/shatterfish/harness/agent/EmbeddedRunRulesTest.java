package org.shatterfish.harness.agent;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The render thread never waits on the Brain, as a rule on the code rather than a measurement
 * (story 5.1, NFR-4, ADR-0013: "the budget is a label, never a wait").
 *
 * <p>{@code EmbeddedThreadingTest} measures frames while a Brain is held; this holds that the class
 * whose methods the render thread calls contains no call that could wait at all: no
 * {@code Future.get}, no sleep, no join, no monitor wait, no latch or termination await. It takes
 * the Brain's answer with {@code Future.resultNow()}, which throws rather than waits, and only after
 * {@code isDone()} says the answer is in.
 */
class EmbeddedRunRulesTest {

    private static final Set<String> WAITING = Set.of(
            "java.util.concurrent.Future.get",
            "java.util.concurrent.FutureTask.get",
            "java.util.concurrent.CompletableFuture.get",
            "java.util.concurrent.CompletableFuture.join",
            "java.lang.Thread.sleep",
            "java.lang.Thread.join",
            "java.lang.Object.wait",
            "java.util.concurrent.CountDownLatch.await",
            "java.util.concurrent.ExecutorService.awaitTermination",
            "java.util.concurrent.locks.Lock.lock",
            "java.util.concurrent.locks.LockSupport.park");

    static final ArchRule THE_RUN_NEVER_WAITS = noClasses()
            .that().haveFullyQualifiedName(EmbeddedRun.class.getName())
            .or().haveNameMatching(EmbeddedRun.class.getName().replace(".", "[.]") + "[$].*")
            .should().accessTargetWhere(DescribedPredicate.describe("a method that waits",
                    (JavaAccess<?> access) -> WAITING.contains(access.getTarget().getOwner().getName() + "."
                            + access.getTarget().getName())))
            .because("the render thread calls this class once per frame, and a frame that waits on the Brain"
                    + " is a frame the game does not draw (NFR-4)");

    @Test
    @DisplayName("the embedded Run calls nothing that waits")
    void the_run_never_waits() {
        JavaClasses harness = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("org.shatterfish.harness");
        THE_RUN_NEVER_WAITS.check(harness);
    }

    @Test
    @DisplayName("the rule bites: a class that calls Future.get is caught")
    void the_rule_bites() {
        JavaClasses fixture = new ClassFileImporter().importClasses(Waits.class);
        ArchRule same = noClasses().that().haveFullyQualifiedName(Waits.class.getName())
                .should().accessTargetWhere(DescribedPredicate.describe("a method that waits",
                        (JavaAccess<?> access) -> WAITING.contains(access.getTarget().getOwner().getName() + "."
                                + access.getTarget().getName())));
        assertTrue(same.evaluate(fixture).hasViolation(), "a Future.get is a violation");
    }

    /** A class that waits on a future, which the rule must catch. */
    static final class Waits {
        static Object take(java.util.concurrent.Future<?> future) throws Exception {
            return future.get();
        }
    }
}
