package org.shatterfish.harness;

import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.Test;
import org.shatterfish.harness.scene.SceneStepper;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reflection into upstream from {@code harness} is confined to {@code SceneStepper}, and what it
 * reaches is exactly what {@code docs/UPSTREAM.md} says it reaches.
 *
 * <p>This is not the brain's boundary. The harness is trusted code: it holds the game, and the
 * Observer that later stories build is the door through which the brain sees it. What this rule
 * protects is reviewability. The hook ledger sees every edit to upstream and nothing that reaches
 * a private member by reflection, so the places that do must be few and named, or a reader of the
 * ledger is told less than the truth. Story 1.3 set the precedent with two fields; this is what
 * stops a third arriving unannounced. Tests are not scanned: they reach privates freely, and the
 * ledger's own tests do.
 */
@AnalyzeClasses(packages = "org.shatterfish.harness", importOptions = ImportOption.DoNotIncludeTests.class)
class HarnessReflectionTest {

    /** The fields {@code docs/UPSTREAM.md} names, as {@code Owner.field}. */
    private static final Set<String> DECLARED = Set.of("GameScene.actorThread", "Actor.current");

    @ArchTest
    static final ArchRule reflection_into_upstream_is_confined_to_the_stepper = noClasses()
            .that().resideInAPackage("org.shatterfish.harness..")
            .and().doNotBelongToAnyOf(SceneStepper.class)
            .should().callMethodWhere(target(name("setAccessible")))
            .orShould().callMethodWhere(target(name("trySetAccessible")))
            .orShould().callMethodWhere(target(name("getDeclaredField")))
            .orShould().callMethodWhere(target(name("getDeclaredFields")))
            .orShould().callMethodWhere(target(name("getDeclaredMethod")))
            .orShould().callMethodWhere(target(name("getDeclaredMethods")))
            .orShould().callMethodWhere(target(name("getDeclaredConstructor")))
            .orShould().callMethodWhere(target(name("getDeclaredConstructors")))
            .orShould().callMethodWhere(target(name("privateLookupIn")))
            .orShould().dependOnClassesThat().resideInAnyPackage("sun..", "jdk.internal..")
            .because("the hook ledger cannot see reflection, so the harness keeps it in one named class that"
                    + " docs/UPSTREAM.md describes");

    @Test
    void the_stepper_reaches_exactly_the_fields_the_ledger_names() throws Exception {
        Set<String> reached = new TreeSet<>();
        for (Field field : SceneStepper.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == Field.class) {
                field.setAccessible(true);
                Field reflected = (Field) field.get(null);
                reached.add(reflected.getDeclaringClass().getSimpleName() + "." + reflected.getName());
            }
        }
        assertEquals(new TreeSet<>(DECLARED), reached,
                "SceneStepper's reflective reach and the set this test declares must agree; change both, and"
                        + " docs/UPSTREAM.md, together");

        Path doc = repoRoot().resolve("docs/UPSTREAM.md");
        String text = Files.readString(doc);
        for (String field : DECLARED) {
            assertTrue(text.contains("`" + field + "`"), doc + " must name `" + field + "`");
        }
        assertTrue(reached.contains("GameScene.actorThread") && GameScene.class != null
                && reached.contains("Actor.current") && Actor.class != null);
    }

    /**
     * The field constants are what the previous test reads; this one bounds the code that could
     * reach a field without storing it in one. Every reflective call in the stepper and its nested
     * classes goes through one helper, so there is exactly one lookup and one opening.
     */
    @Test
    void the_stepper_reflects_in_one_place() {
        Class<?>[] nested = SceneStepper.class.getDeclaredClasses();
        Class<?>[] all = new Class<?>[nested.length + 1];
        all[0] = SceneStepper.class;
        System.arraycopy(nested, 0, all, 1, nested.length);
        JavaClasses stepper = new ClassFileImporter().importClasses(all);

        Set<String> lookups = Set.of("getDeclaredField", "getDeclaredFields", "getDeclaredMethod", "getDeclaredMethods",
                "getDeclaredConstructor", "getDeclaredConstructors", "privateLookupIn");
        Set<String> openings = Set.of("setAccessible", "trySetAccessible");
        long lookupCalls = 0;
        long openingCalls = 0;
        for (JavaClass owner : stepper) {
            for (JavaMethodCall call : owner.getMethodCallsFromSelf()) {
                if (lookups.contains(call.getTarget().getName())) {
                    lookupCalls++;
                }
                if (openings.contains(call.getTarget().getName())) {
                    openingCalls++;
                }
            }
        }
        assertEquals(1, lookupCalls, "one place looks a field up by name");
        assertEquals(1, openingCalls, "one place opens it");
    }

    @Test
    void the_rule_bites() {
        JavaClasses fixture = new ClassFileImporter().importClasses(ReachesAPrivateField.class);
        EvaluationResult result = reflection_into_upstream_is_confined_to_the_stepper.evaluate(fixture);
        assertTrue(result.hasViolation(), "a harness class outside the stepper that reflects must be rejected");
        assertTrue(result.getFailureReport().toString().contains(ReachesAPrivateField.class.getSimpleName()));
    }

    /** What a third reflective read would look like, anywhere in harness but the stepper. */
    static final class ReachesAPrivateField {
        Object peek() throws Exception {
            Field field = GameScene.class.getDeclaredField("scene");
            field.setAccessible(true);
            return field.get(null);
        }
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !(Files.exists(dir.resolve("settings.gradle"))
                && Files.exists(dir.resolve("docs/UPSTREAM.md")))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new IllegalStateException("not inside the repository");
        }
        return dir;
    }
}
