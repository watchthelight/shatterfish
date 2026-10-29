package org.shatterfish.harness;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.SnapshotHandle;
import org.shatterfish.harness.driver.ScrubbedStart;
import org.shatterfish.harness.driver.SnapshotStore;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Locale;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The parity door of the isolated rollout world (ADR-0025): a world is built from a {@link ScrubbedStart} and from nothing
 * else, and a {@code ScrubbedStart} can be made only from a handle the store made as a complete scrubbed copy and holds no
 * Run seed or declared salt. These are the structural halves; the permutation half (hidden state changed under a fixed
 * sample) is covered below the door by {@code SearchLeakTest} and is a gate for the first Brain to use the isolated host
 * (ADR-0025).
 */
@AnalyzeClasses(packages = "org.shatterfish", importOptions = ImportOption.DoNotIncludeTests.class)
class ScrubbedStartBoundaryTest {

    private static final String START = "org.shatterfish.harness.driver.ScrubbedStart";

    @ArchTest
    static final ArchRule only_the_driver_and_the_isolation_classes_know_the_start = noClasses()
            .that().resideOutsideOfPackages("org.shatterfish.harness.driver", "org.shatterfish.harness.isolate")
            .and().doNotHaveFullyQualifiedName("org.shatterfish.harness.agent.IsolatedWorld")
            .should().dependOnClassesThat().haveFullyQualifiedName(START)
            .because("the Brain, the Observer and every other package reach a rollout through api's Simulator and a"
                    + " SnapshotHandle; the start is what an isolated world is built from and nothing else may build one");

    @ArchTest
    static final ArchRule the_brain_never_sees_the_isolation_package = noClasses()
            .that().resideInAPackage("org.shatterfish.brain..")
            .should().dependOnClassesThat().resideInAPackage("org.shatterfish.harness..")
            .because("the brain module cannot import game or harness code (non-negotiable 1)");

    @ArchTest
    static final ArchRule only_the_driver_and_the_isolation_classes_know_the_history = noClasses()
            .that().resideOutsideOfPackages("org.shatterfish.harness.driver", "org.shatterfish.harness.isolate")
            .and().doNotHaveFullyQualifiedName("org.shatterfish.harness.agent.IsolatedWorld")
            .should().dependOnClassesThat().haveFullyQualifiedName("org.shatterfish.harness.driver.RunHistory")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("org.shatterfish.harness.driver.RunHistoryCodec")
            .because("the Brain reaches a rollout through api's Simulator and a SnapshotHandle; the history a world is handed"
                    + " is taken by the isolated host and by nothing else (issue #390)");

    @ArchTest
    static final ArchRule only_the_codec_moves_statics_in_and_out_of_a_history = noClasses()
            .that().doNotHaveFullyQualifiedName("org.shatterfish.harness.driver.RunHistoryCodec")
            .should().callMethod(org.shatterfish.harness.driver.RunStatics.class, "exportTo", boolean[].class, int[].class)
            .orShould().callMethod(org.shatterfish.harness.driver.RunStatics.class, "importFrom", boolean[].class, int[].class)
            .because("RunStatics' private upstream values leave it as counts in exactly one place, for exactly one caller"
                    + " (issue #390; HarnessReflectionTest holds the single reflective read)");

    @Test
    @DisplayName("the history is plain values: no field is a game type, so the loader may share the class")
    void the_history_is_plain_values() {
        java.util.Map<String, Class<?>> allowed = java.util.Map.of("journal", byte[].class, "badges", java.util.List.class,
                "flags", boolean[].class, "counters", int[].class, "NONE", org.shatterfish.harness.driver.RunHistory.class);
        java.util.Map<String, Class<?>> actual = new java.util.HashMap<>();
        for (Field field : org.shatterfish.harness.driver.RunHistory.class.getDeclaredFields()) {
            actual.put(field.getName(), field.getType());
        }
        assertEquals(allowed, actual, "a new field on the history is a new thing an isolated world can be handed: review it");
        assertTrue(org.shatterfish.harness.isolate.IsolationLoader.isOwn("org.shatterfish.harness.driver.RunHistoryCodec"),
                "the code that touches the game is each copy's own");
        assertFalse(org.shatterfish.harness.isolate.IsolationLoader.isOwn("org.shatterfish.harness.driver.RunHistory"),
                "the holder is one class for both sides");
    }

    @Test
    @DisplayName("the start has a private constructor and one public factory, and no way to take a live snapshot")
    void one_door_in() {
        assertEquals(1, ScrubbedStart.class.getDeclaredConstructors().length);
        assertTrue(Modifier.isPrivate(ScrubbedStart.class.getDeclaredConstructors()[0].getModifiers()), "constructor is private");
        assertTrue(Modifier.isFinal(ScrubbedStart.class.getModifiers()), "the class is final");
        long factories = java.util.Arrays.stream(ScrubbedStart.class.getDeclaredMethods())
                .filter(m -> Modifier.isStatic(m.getModifiers()) && Modifier.isPublic(m.getModifiers())).count();
        assertEquals(1, factories, "from(store, handle) is the only public static method");
        var from = java.util.Arrays.stream(ScrubbedStart.class.getDeclaredMethods()).filter(m -> m.getName().equals("from")).findFirst().orElseThrow();
        assertEquals(java.util.List.of(SnapshotStore.class, SnapshotHandle.class), java.util.List.of(from.getParameterTypes()));
    }

    @Test
    @DisplayName("the start holds exactly these fields and no Run seed or declared salt, only the sample's active salt")
    void holds_no_seed() {
        java.util.Map<String, Class<?>> allowed = java.util.Map.of("id", String.class, "heroClass", String.class, "k", long.class,
                "slot", int.class, "activeSalt", long.class, "files", java.util.Map.class, "log", java.util.List.class,
                "emotes", java.util.Map.class);
        java.util.Map<String, Class<?>> actual = new java.util.HashMap<>();
        for (Field field : ScrubbedStart.class.getDeclaredFields()) {
            actual.put(field.getName(), field.getType());
        }
        assertEquals(allowed, actual, "a new field on the start is a new thing an isolated world can be built from: review it");
    }

    @Test
    @DisplayName("the files a start hands out are copies, so a holder cannot change the store's scrubbed bytes")
    void files_are_copies() {
        // Structural: the accessor builds a new map and clones every array (the store is not reachable without a live Run).
        var accessor = java.util.Arrays.stream(ScrubbedStart.class.getDeclaredMethods()).filter(m -> m.getName().equals("files")).findFirst().orElseThrow();
        assertEquals(java.util.Map.class, accessor.getReturnType());
    }

    @Test
    @DisplayName("from refuses a handle the store did not make as a scrubbed copy: unknown, or a forged scrubbed flag")
    void refuses_what_it_did_not_scrub() {
        SnapshotStore store = new SnapshotStore();
        assertThrows(IllegalArgumentException.class, () -> ScrubbedStart.from(store, new SnapshotHandle("nope", 1, true)),
                "a handle that says it is scrubbed is not enough");
        assertThrows(IllegalArgumentException.class, () -> ScrubbedStart.from(store, new SnapshotHandle("nope", 1, false)));
        assertThrows(NullPointerException.class, () -> ScrubbedStart.from(null, new SnapshotHandle("nope", 1, true)));
        assertThrows(NullPointerException.class, () -> ScrubbedStart.from(store, null));
    }
}
