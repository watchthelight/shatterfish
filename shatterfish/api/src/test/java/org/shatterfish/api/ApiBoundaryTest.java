package org.shatterfish.api;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.type;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The api module is the wire between the harness and the brain, and it is data only.
 *
 * <p><b>These rules must be at least as strict as {@code BrainBoundaryTest}'s, and for a reason
 * worth stating.</b> The brain's allowlist permits {@code org.shatterfish.api..} wholesale, and
 * ArchUnit's dependency rules are not transitive. So every door shut in the brain is reopened by one
 * class here: an adversarial review put a fifteen-line reflective reader in this module, called it
 * from {@code org.shatterfish.brain}, and printed {@code Dungeon.seed} — the one thing
 * non-negotiable #1 names explicitly — with every rule in both modules green. `api` is not a
 * quieter module than `brain`; it is the same boundary seen from the other side.
 *
 * <p>The allowlist here is deliberately narrower than the brain's, because DTOs need less. If the
 * two ever diverge in the other direction, this module becomes a laundering layer again.
 *
 * <p>This is also where a parity break would be hardest to see: a type here that wraps game state
 * hands the brain hidden information no matter how careful the Observer is. That is why
 * {@code Snapshot} is not in this module — bundle bytes are inflatable by anything holding them, so
 * {@code api} carries only an opaque handle (AD-1, ADR-0009).
 */
@AnalyzeClasses(packages = "org.shatterfish.api", importOptions = ImportOption.DoNotIncludeTests.class)
class ApiBoundaryTest {

	/** As {@code BrainBoundaryTest.ALLOWED}, minus the brain's own package. Data and arithmetic. */
	private static final String[] ALLOWED = {
			"org.shatterfish.api..",
			"java.lang",
			"java.lang.runtime",
			"java.util",
			"java.util.function",
			"java.util.stream",
			"java.math",
	};

	/** As {@code BrainBoundaryTest.DENIED_INSIDE_ALLOWED_PACKAGES}. Kept in step deliberately. */
	private static final Class<?>[] DENIED_INSIDE_ALLOWED_PACKAGES = {
			Class.class, ClassLoader.class, Module.class, ModuleLayer.class, Package.class,
			System.class, Runtime.class, Process.class, ProcessBuilder.class, ProcessHandle.class,
			Thread.class, ThreadGroup.class, ThreadLocal.class, StackWalker.class,
			StackTraceElement.class,
			java.util.Random.class, java.util.SplittableRandom.class, java.util.Scanner.class,
			java.util.ServiceLoader.class, java.util.Date.class, java.util.Calendar.class,
			java.util.Timer.class, java.util.UUID.class, java.util.ResourceBundle.class,
			java.util.Locale.class, java.util.TimeZone.class, java.util.Currency.class,
			java.util.IdentityHashMap.class, java.util.WeakHashMap.class,
	};

	@ArchTest
	static final ArchRule api_reaches_only_data_structures = classes()
			.that().resideInAPackage("org.shatterfish.api..")
			.should().onlyDependOnClassesThat()
			.resideInAnyPackage(ALLOWED)
			.because("api is DTOs only, and the brain's allowlist permits all of it: anything api can"
					+ " reach, the brain can reach through api");

	@ArchTest
	static final ArchRule api_reaches_nothing_dangerous_inside_the_allowed_packages = noClasses()
			.that().resideInAPackage("org.shatterfish.api..")
			.should().dependOnClassesThat()
			.belongToAnyOf(DENIED_INSIDE_ALLOWED_PACKAGES)
			.orShould().callMethodWhere(target(name("forName")).and(target(owner(type(Class.class)))))
			.orShould().callMethodWhere(target(name("getStackTrace")))
			.because("the same doors the brain is denied, denied here, because api is inside the brain's"
					+ " allowlist and ArchUnit dependency rules are not transitive");

	@ArchTest
	static final ArchRule api_never_depends_on_game_code = noClasses()
			.that().resideInAPackage("org.shatterfish.api..")
			.should().dependOnClassesThat()
			.resideInAnyPackage("com.shatteredpixel..", "com.watabou..")
			.because("a game type crossing into api would put game state one field access from the brain");

	@Test
	@DisplayName("every class compiled into api lives under org.shatterfish.api")
	void nothing_ships_outside_the_scanned_package() {
		Path output = mainOutput();
		try (Stream<Path> tree = Files.walk(output)) {
			List<String> strays = tree.filter(Files::isRegularFile)
					.map(output::relativize)
					.map(p -> p.toString().replace('\\', '/'))
					.filter(p -> p.endsWith(".class"))
					.filter(p -> !p.startsWith("org/shatterfish/api/"))
					.sorted()
					.toList();

			assertTrue(strays.isEmpty(),
					"these classes ship in the api module but sit outside org.shatterfish.api, so the rules"
							+ " above pass over them without looking: " + strays);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static Path mainOutput() {
		try {
			Path location = Path.of(ShatterfishApi.class.getProtectionDomain().getCodeSource()
					.getLocation().toURI());
			if (!Files.isDirectory(location)) {
				fail("expected api's compiled classes in a directory, found " + location);
			}
			return location;
		} catch (URISyntaxException e) {
			throw new IllegalStateException("cannot locate the api module's compiled output", e);
		}
	}
}
