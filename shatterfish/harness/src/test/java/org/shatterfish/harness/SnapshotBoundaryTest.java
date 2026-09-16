package org.shatterfish.harness;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.BeliefSample;
import org.shatterfish.api.Redeterminer;
import org.shatterfish.api.RolloutEnd;
import org.shatterfish.api.RolloutResult;
import org.shatterfish.api.Simulator;
import org.shatterfish.api.SnapshotHandle;
import org.shatterfish.harness.driver.SnapshotStore;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The snapshot's bytes never leave the driver package, and what {@code api} carries of a snapshot
 * cannot be inflated into hidden state (ADR-0009): the snapshot type is not public and nothing
 * outside its package depends on it, held by ArchUnit over every Shatterfish class on the
 * classpath; and the handle, the result, the sample and the two interfaces hold nothing but an
 * id, a wait, a flag, Observations already made, and bytes a Brain wrote itself.
 */
@AnalyzeClasses(packages = "org.shatterfish", importOptions = ImportOption.DoNotIncludeTests.class)
class SnapshotBoundaryTest {

    private static final String SNAPSHOT = "org.shatterfish.harness.driver.Snapshot";

    @ArchTest
    static final ArchRule the_snapshot_stays_in_the_driver_package = noClasses()
            .that().resideOutsideOfPackage("org.shatterfish.harness.driver")
            .should().dependOnClassesThat().haveFullyQualifiedName(SNAPSHOT)
            .because("a snapshot's bytes are the Run's hidden state and stay module-private to harness (ADR-0009);"
                    + " api and every other package see a SnapshotHandle, which is an id");

    @Test
    @DisplayName("the snapshot type is not public, and the store hands out only handles")
    void the_snapshot_is_not_public() throws Exception {
        Class<?> snapshot = Class.forName(SNAPSHOT);
        assertFalse(Modifier.isPublic(snapshot.getModifiers()), "Snapshot is package-private");
        assertTrue(Modifier.isFinal(snapshot.getModifiers()), "and final");
        for (var method : SnapshotStore.class.getMethods()) {
            if (method.getDeclaringClass() == SnapshotStore.class) {
                assertFalse(method.getReturnType() == snapshot, method + " hands a Snapshot out");
                for (Class<?> parameter : method.getParameterTypes()) {
                    assertFalse(parameter == snapshot, method + " takes a Snapshot");
                }
            }
        }
    }

    @Test
    @DisplayName("what api carries of a snapshot holds nothing to inflate: an id, a wait, a flag, and Observations")
    void the_api_half_holds_no_bytes() {
        List<Class<?>> handleTypes = new java.util.ArrayList<>();
        for (RecordComponent component : SnapshotHandle.class.getRecordComponents()) {
            handleTypes.add(component.getType());
        }
        assertEquals(List.of(String.class, long.class, boolean.class), handleTypes);
        for (RecordComponent component : RolloutResult.class.getRecordComponents()) {
            assertFalse(component.getType().isArray(), component + " is an array");
            assertTrue(Set.of(List.class, int.class, RolloutEnd.class).contains(component.getType()), component.toString());
        }
        for (Class<?> type : List.of(Simulator.class, Redeterminer.class, SnapshotHandle.class, RolloutResult.class)) {
            for (Field field : type.getDeclaredFields()) {
                assertFalse(field.getType().isArray(), field + " holds bytes");
            }
        }
        // The one array in the reserved half is the sample's, which a Brain writes and only the
        // scrubber reads, copied in and out like a Belief's.
        assertEquals(2, BeliefSample.class.getDeclaredFields().length);
        JavaClasses api = new ClassFileImporter().importPackages("org.shatterfish.api");
        assertTrue(api.contain(SnapshotHandle.class), "api is on the classpath for the rule above");
    }
}
