package org.shatterfish.brain;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The boundary rules of {@link BrainBoundaryTest}, checked against classes that break them.
 *
 * <p>{@code brain} contains one trivial class today, so every rule over there passes over almost
 * nothing. A rule in that position is indistinguishable from a comment until something has been
 * rejected by it, and the way a parity rule fails is by looking green while the hole is open: the
 * first draft of these rules banned {@code java.lang.invoke.MethodHandles..}, which matches no
 * package at all and left {@code MethodHandles.privateLookupIn} — a handle to any private static in
 * the JVM — entirely legal.
 *
 * <p>Each fixture below is a class in {@code org.shatterfish.brain} that a rule must reject, plus
 * one that every rule must accept. The last is as important as the others: a ban wide enough to
 * catch ordinary Java would be removed the first time it was inconvenient.
 *
 * <p>Two rules cannot be exercised here. {@code brain_never_depends_on_game_code} and
 * {@code brain_depends_on_no_other_shatterfish_module} need a fixture that names game code or
 * another Shatterfish module, and neither is on this module's classpath — which is the point of
 * AD-1, and is a stronger guarantee than a test. Putting either there to test the test would be the
 * hole itself. The Gradle resolution check in {@code brain/build.gradle} is the second line.
 */
class BrainBoundaryRulesBiteTest {

	@Test
	@DisplayName("the file and socket ban rejects a class that opens a file")
	void file_ban_bites() {
		assertRejects(BrainBoundaryTest.brain_reads_no_files_and_opens_no_sockets, ReadsAFile.class);
	}

	@Test
	@DisplayName("the reflection ban rejects both reflection and method handles")
	void reflection_ban_bites() {
		assertRejects(BrainBoundaryTest.brain_uses_no_reflection, UsesReflection.class);
		assertRejects(BrainBoundaryTest.brain_uses_no_reflection, UsesMethodHandles.class);
	}

	@Test
	@DisplayName("the runtime-lookup ban rejects Class.forName, system properties and service loaders")
	void runtime_lookup_ban_bites() {
		assertRejects(BrainBoundaryTest.brain_reaches_nothing_by_name_at_runtime, ResolvesAClassByName.class);
		assertRejects(BrainBoundaryTest.brain_reaches_nothing_by_name_at_runtime, ReadsASystemProperty.class);
		assertRejects(BrainBoundaryTest.brain_reaches_nothing_by_name_at_runtime, LoadsAService.class);
	}

	@Test
	@DisplayName("the determinism ban rejects a clock, an unseeded generator and Math.random")
	void determinism_ban_bites() {
		assertRejects(BrainBoundaryTest.brain_has_no_clock_and_no_generator_of_its_own, ReadsTheClock.class);
		assertRejects(BrainBoundaryTest.brain_has_no_clock_and_no_generator_of_its_own, SeedsItself.class);
		assertRejects(BrainBoundaryTest.brain_has_no_clock_and_no_generator_of_its_own, CallsMathRandom.class);
	}

	@Test
	@DisplayName("the positive rule rejects a dependency that is neither api nor the JDK")
	void positive_rule_bites() {
		assertRejects(BrainBoundaryTest.brain_sees_only_the_api_and_the_jdk, DependsOnAThirdParty.class);
	}

	@Test
	@DisplayName("ordinary Java passes every rule")
	void the_bans_do_not_catch_ordinary_code() {
		JavaClasses clean = new ClassFileImporter().importClasses(OrdinaryJava.class, OrdinaryJava.Suit.class,
				OrdinaryJava.Move.class);
		for (ArchRule rule : List.of(
				BrainBoundaryTest.brain_never_depends_on_game_code,
				BrainBoundaryTest.brain_depends_on_no_other_shatterfish_module,
				BrainBoundaryTest.brain_reads_no_files_and_opens_no_sockets,
				BrainBoundaryTest.brain_uses_no_reflection,
				BrainBoundaryTest.brain_reaches_nothing_by_name_at_runtime,
				BrainBoundaryTest.brain_has_no_clock_and_no_generator_of_its_own,
				BrainBoundaryTest.brain_sees_only_the_api_and_the_jdk)) {
			assertDoesNotThrow(() -> rule.check(clean),
					"a boundary rule rejects ordinary Java (lambdas, string concatenation, streams,"
							+ " records, enums, getClass): " + rule.getDescription());
		}
	}

	private static void assertRejects(ArchRule rule, Class<?> fixture) {
		JavaClasses fixtureClasses = new ClassFileImporter().importClasses(fixture);
		assertThrows(AssertionError.class, () -> rule.check(fixtureClasses),
				fixture.getSimpleName() + " breaks this rule and the rule did not notice: "
						+ rule.getDescription());
	}

	// --- fixtures. Each names one channel the brain must not have. ---

	@SuppressWarnings("unused")
	static final class ReadsAFile {
		static boolean saveExists(String name) {
			return new File(name).exists();
		}
	}

	@SuppressWarnings("unused")
	static final class UsesReflection {
		static Object read(Object target, String field) throws Exception {
			Field f = target.getClass().getDeclaredField(field);
			f.setAccessible(true);
			return f.get(target);
		}
	}

	@SuppressWarnings("unused")
	static final class UsesMethodHandles {
		static MethodHandles.Lookup lookup() {
			return MethodHandles.lookup();
		}
	}

	@SuppressWarnings("unused")
	static final class ResolvesAClassByName {
		static Class<?> find(String name) throws ClassNotFoundException {
			return Class.forName(name);
		}
	}

	@SuppressWarnings("unused")
	static final class ReadsASystemProperty {
		static String channel() {
			return System.getProperty("shatterfish.oracle");
		}
	}

	@SuppressWarnings("unused")
	static final class LoadsAService {
		static ServiceLoader<Runnable> providers() {
			return ServiceLoader.load(Runnable.class);
		}
	}

	@SuppressWarnings("unused")
	static final class ReadsTheClock {
		static Instant now() {
			return Instant.now();
		}
	}

	@SuppressWarnings("unused")
	static final class SeedsItself {
		static int roll() {
			return new Random().nextInt(6);
		}
	}

	@SuppressWarnings("unused")
	static final class CallsMathRandom {
		static double roll() {
			return Math.random();
		}
	}

	@SuppressWarnings("unused")
	static final class DependsOnAThirdParty {
		static ClassFileImporter importer() {
			return new ClassFileImporter();
		}
	}

	/**
	 * Everything a real brain is expected to be written with. If a ban above ever rejects this, the
	 * ban is wrong, not the code.
	 */
	@SuppressWarnings("unused")
	static final class OrdinaryJava {

		enum Suit { HEARTS, SPADES }

		record Move(String name, int score) {
		}

		static String describe(List<Move> moves, Suit suit) {
			String best = moves.stream()
					.filter(m -> m.score() > 0)
					.sorted((a, b) -> Integer.compare(b.score(), a.score()))
					.map(Move::name)
					.collect(Collectors.joining(", "));
			return "best of " + moves.size() + " for " + suit + ": " + best
					+ " (" + suit.getClass().getSimpleName() + ")";
		}
	}
}
