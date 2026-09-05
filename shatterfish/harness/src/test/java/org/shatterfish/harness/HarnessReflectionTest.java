package org.shatterfish.harness;

import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.Test;
import org.shatterfish.harness.scene.NoOpGL;
import org.shatterfish.harness.scene.SceneStepper;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

import static com.tngtech.archunit.core.domain.JavaAccess.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.type;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
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
 * ledger is told less than the truth. Story 1.3 set the precedent with one field; this is what
 * stops a second arriving unannounced. Tests are not scanned: they reach privates freely, and the
 * ledger's own tests do.
 *
 * <p>The rule is by dependency rather than by call, because a method reference or a reflective
 * call to {@code getDeclaredField} itself is not a call to it. Anything outside the stepper that
 * so much as names {@code java.lang.reflect} or {@code java.lang.invoke} is rejected; the one
 * exception is {@code NoOpGL}, a dynamic proxy, which may name {@code Proxy},
 * {@code InvocationHandler} and {@code Method} and may not open anything or invoke through them.
 */
@AnalyzeClasses(packages = "org.shatterfish.harness", importOptions = ImportOption.DoNotIncludeTests.class)
class HarnessReflectionTest {

    /** The fields {@code docs/UPSTREAM.md} names, as {@code Owner.field}. */
    private static final Set<String> DECLARED = Set.of("GameScene.actorThread");

    @ArchTest
    static final ArchRule reflection_into_upstream_is_confined_to_the_stepper = noClasses()
            .that().resideInAPackage("org.shatterfish.harness..")
            .and().doNotBelongToAnyOf(SceneStepper.class, NoOpGL.class)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "java.lang.reflect..", "java.lang.invoke..", "sun..", "jdk.internal..")
            .orShould().accessTargetWhere(target(name("setAccessible")))
            .orShould().accessTargetWhere(target(name("trySetAccessible")))
            .orShould().accessTargetWhere(target(name("getDeclaredField")))
            .orShould().accessTargetWhere(target(name("getDeclaredFields")))
            .orShould().accessTargetWhere(target(name("getDeclaredMethod")))
            .orShould().accessTargetWhere(target(name("getDeclaredMethods")))
            .orShould().accessTargetWhere(target(name("getDeclaredConstructor")))
            .orShould().accessTargetWhere(target(name("getDeclaredConstructors")))
            .orShould().accessTargetWhere(target(name("privateLookupIn")))
            .because("the hook ledger cannot see reflection, so the harness keeps it in one named class that"
                    + " docs/UPSTREAM.md describes");

    @ArchTest
    static final ArchRule the_proxy_only_proxies = noClasses()
            .that().belongToAnyOf(NoOpGL.class)
            .should().dependOnClassesThat(type(Field.class).or(type(AccessibleObject.class)))
            .orShould().accessTargetWhere(target(name("invoke")).and(target(owner(type(Method.class)))))
            .orShould().accessTargetWhere(target(name("setAccessible")))
            .orShould().accessTargetWhere(target(name("trySetAccessible")))
            .orShould().dependOnClassesThat().resideInAnyPackage("java.lang.invoke..", "sun..", "jdk.internal..")
            .because("NoOpGL builds a dynamic proxy and nothing else");

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
        assertTrue(reached.contains("GameScene.actorThread") && GameScene.class != null);
    }

    /**
     * The field constants are what the previous test reads; this one bounds the code that could
     * reach a field without storing it in one. Every reflective access in the stepper and its nested
     * classes, by call or by reference, goes through one helper, so there is exactly one lookup and
     * one opening.
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
        long lookupAccesses = 0;
        long openingAccesses = 0;
        for (JavaClass owner : stepper) {
            for (JavaAccess<?> access : owner.getAccessesFromSelf()) {
                if (lookups.contains(access.getTarget().getName())) {
                    lookupAccesses++;
                }
                if (openings.contains(access.getTarget().getName())) {
                    openingAccesses++;
                }
            }
        }
        assertEquals(1, lookupAccesses, "one place looks a field up by name");
        assertEquals(1, openingAccesses, "one place opens it");
    }

    @Test
    void the_rule_bites() {
        for (Class<?> fixture : new Class<?>[]{ReachesAPrivateField.class, ReachesByReference.class, ReachesByMetaReflection.class}) {
            JavaClasses classes = new ClassFileImporter().importClasses(fixture);
            EvaluationResult result = reflection_into_upstream_is_confined_to_the_stepper.evaluate(classes);
            assertTrue(result.hasViolation(), fixture.getSimpleName() + " must be rejected");
            assertTrue(result.getFailureReport().toString().contains(fixture.getSimpleName()));
        }
    }

    /** What a second reflective read would look like, anywhere in harness but the stepper. */
    static final class ReachesAPrivateField {
        Object peek() throws Exception {
            Field field = GameScene.class.getDeclaredField("scene");
            field.setAccessible(true);
            return field.get(null);
        }
    }

    /** The same, without a call to the named methods: method references. */
    static final class ReachesByReference {
        interface Lookup {
            Field find(String name) throws NoSuchFieldException;
        }

        Object peek() throws Exception {
            Lookup lookup = GameScene.class::getDeclaredField;
            Field field = lookup.find("scene");
            java.util.function.Consumer<Boolean> open = field::setAccessible;
            open.accept(true);
            return field.get(null);
        }
    }

    /** The same, reaching getDeclaredField itself by reflection. */
    static final class ReachesByMetaReflection {
        Object peek() throws Exception {
            Method lookup = Class.class.getMethod("getDeclaredField", String.class);
            Object field = lookup.invoke(GameScene.class, "scene");
            Method open = AccessibleObject.class.getMethod("setAccessible", boolean.class);
            open.invoke(field, true);
            return Field.class.getMethod("get", Object.class).invoke(field, (Object) null);
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
