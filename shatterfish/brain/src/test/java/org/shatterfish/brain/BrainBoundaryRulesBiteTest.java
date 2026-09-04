package org.shatterfish.brain;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The boundary rules of {@link BrainBoundaryTest}, checked against classes that break them.
 *
 * <p>{@code brain} contains one trivial class, so every rule over there passes over almost nothing.
 * A rule in that position is indistinguishable from a comment until something has been rejected by
 * it, and the way a parity rule fails is by looking green while the hole is open. Two adversarial
 * reviews proved that twice over: the first walked a denylist with {@code Class.forName} plus
 * {@code MethodHandles}, the second walked the next denylist with {@code java.beans.Expression} and
 * read {@code Dungeon.seed}. Both exploits are fixtures below.
 *
 * <p>The fixtures are grouped by what they reach rather than by which rule catches them, because
 * under an allowlist the answer to "which rule" is usually "the allowlist", and the point of the
 * test is that the door is shut rather than which bolt shut it.
 *
 * <p>{@link OrdinaryJava} is as important as the rest. A ban wide enough to catch lambdas, records
 * or streams would be removed the first time it was inconvenient, so it is checked against every
 * rule and must pass.
 *
 * <p>Two rules cannot be exercised here. {@code brain_never_depends_on_game_code} and
 * {@code brain_depends_on_no_other_shatterfish_module} need a fixture that names game code or
 * another Shatterfish module, and neither is on this module's classpath — which is the point of
 * AD-1, and a stronger guarantee than a test. Putting either there to test the test would be the
 * hole itself.
 */
class BrainBoundaryRulesBiteTest {

	/** Every rule, so that each fixture is checked against all of them rather than a chosen one. */
	private static final List<ArchRule> ALL_RULES = List.of(
			BrainBoundaryTest.brain_reaches_only_data_structures_and_the_api,
			BrainBoundaryTest.brain_reaches_nothing_dangerous_inside_the_allowed_packages,
			BrainBoundaryTest.brain_calls_no_method_that_reaches_outside_the_process,
			BrainBoundaryTest.brain_never_depends_on_game_code,
			BrainBoundaryTest.brain_depends_on_no_other_shatterfish_module);

	@Test
	@DisplayName("the exploit from the first review is rejected: Class.forName plus method handles")
	void the_method_handles_exploit_is_rejected() {
		assertRejected(ReachesByMethodHandle.class);
	}

	@Test
	@DisplayName("the exploit from the second review is rejected: java.beans reflective dispatch")
	void the_java_beans_exploit_is_rejected() {
		assertRejected(ReachesByBeansExpression.class);
	}

	@Test
	@DisplayName("reflection is rejected however it is spelled")
	void reflection_is_rejected() {
		assertRejected(UsesReflection.class);
		assertRejected(ResolvesAClassByName.class);
		assertRejected(ReferencesForName.class);
		assertRejected(ReachesTheContextClassLoader.class);
	}

	@Test
	@DisplayName("every route to the process and its environment is rejected")
	void the_process_environment_is_rejected() {
		assertRejected(ReadsASystemProperty.class);
		assertRejected(ReadsPropertiesThroughManagement.class);
		assertRejected(ReadsTheCommandLine.class);
		assertRejected(LoadsAService.class);
		assertRejected(ReadsUserPreferences.class);
		assertRejected(ReadsAPropertyThroughAWrapper.class);
		assertRejected(ReadsTheClasspath.class);
	}

	@Test
	@DisplayName("files and sockets are rejected")
	void storage_and_network_are_rejected() {
		assertRejected(ReadsAFile.class);
	}

	@Test
	@DisplayName("every clock and every generator the brain could seed itself is rejected")
	void nondeterminism_is_rejected() {
		assertRejected(ReadsTheClock.class);
		assertRejected(ReadsTheClockTheOldWay.class);
		assertRejected(SeedsItself.class);
		assertRejected(SeedsItselfSecurely.class);
		assertRejected(SeedsItselfThroughTheNewApi.class);
		assertRejected(MakesARandomIdentifier.class);
		assertRejected(CallsMathRandom.class);
		assertRejected(SchedulesWork.class);
		assertRejected(ShufflesWithoutAGenerator.class);
		assertRejected(CallsStrictMathRandom.class);
		assertRejected(IteratesByIdentity.class);
		assertRejected(IteratesWhatTheCollectorLeft.class);
		assertRejected(GoesParallel.class);
		assertRejected(FormatsForTheHost.class);
		assertRejected(ChangesCaseForTheHost.class);
	}

	@Test
	@DisplayName("the caller chain is rejected")
	void the_caller_chain_is_rejected() {
		assertRejected(WalksTheStack.class);
	}

	@Test
	@DisplayName("a dependency that is neither the api nor an allowed data package is rejected")
	void anything_off_the_allowlist_is_rejected() {
		assertRejected(DependsOnAThirdParty.class);
	}

	@Test
	@DisplayName("ordinary Java passes every rule")
	void the_rules_do_not_catch_ordinary_code() {
		JavaClasses clean = new ClassFileImporter().importClasses(
				OrdinaryJava.class, OrdinaryJava.Suit.class, OrdinaryJava.Move.class);
		for (ArchRule rule : ALL_RULES) {
			assertDoesNotThrow(() -> rule.check(clean),
					"a boundary rule rejects ordinary Java (lambdas, string concatenation, streams,"
							+ " records, enums, BigDecimal): " + rule.getDescription());
		}
	}

	/**
	 * Asserts that some rule rejects the fixture, and that it does so <em>because of this class</em>.
	 * Checking only that an {@code AssertionError} was thrown would also pass when ArchUnit failed
	 * for an unrelated reason, such as having found no classes to check at all.
	 */
	private static void assertRejected(Class<?> fixture) {
		JavaClasses fixtureClasses = new ClassFileImporter().importClasses(fixture);
		List<String> failures = new java.util.ArrayList<>();
		for (ArchRule rule : ALL_RULES) {
			try {
				rule.check(fixtureClasses);
			} catch (AssertionError rejected) {
				String message = String.valueOf(rejected.getMessage());
				assertTrue(message.contains(fixture.getName()),
						"a rule failed on " + fixture.getSimpleName() + " without naming it, so the failure"
								+ " is not evidence that the fixture was rejected: " + message);
				failures.add(rule.getDescription());
			}
		}
		assertTrue(!failures.isEmpty(),
				fixture.getSimpleName() + " reaches outside the brain and no rule noticed. What it does: "
						+ fixture.getSimpleName() + " is a fixture in this test; read it.");
	}

	// --- fixtures. Each names one channel the brain must not have. ---

	@SuppressWarnings("unused")
	static final class ReachesByMethodHandle {
		static Object peek(String type, String field) throws Throwable {
			Class<?> target = Class.forName(type);
			MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(target, MethodHandles.lookup());
			return lookup.findStaticGetter(target, field, Object.class).invoke();
		}
	}

	@SuppressWarnings("unused")
	static final class ReachesByBeansExpression {
		static Object peek(String type, String field) throws Exception {
			Object target = new java.beans.Expression(Class.class, "forName", new Object[]{type}).getValue();
			Object f = new java.beans.Expression(target, "getDeclaredField", new Object[]{field}).getValue();
			new java.beans.Statement(f, "setAccessible", new Object[]{true}).execute();
			return new java.beans.Expression(f, "get", new Object[]{null}).getValue();
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
	static final class ResolvesAClassByName {
		static Class<?> find(String name) throws ClassNotFoundException {
			return Class.forName(name);
		}
	}

	/** A method reference, which is not a method call and slipped past the previous rule. */
	@SuppressWarnings("unused")
	static final class ReferencesForName {
		interface Resolver {
			Class<?> resolve(String name) throws ClassNotFoundException;
		}

		static Resolver resolver() {
			return Class::forName;
		}
	}

	@SuppressWarnings("unused")
	static final class ReachesTheContextClassLoader {
		static Object loader() {
			return Thread.currentThread().getContextClassLoader();
		}
	}

	@SuppressWarnings("unused")
	static final class ReadsASystemProperty {
		static String channel() {
			return System.getProperty("shatterfish.oracle");
		}
	}

	/** The property table without touching {@code System}. */
	@SuppressWarnings("unused")
	static final class ReadsPropertiesThroughManagement {
		static Object properties() {
			return ManagementFactory.getRuntimeMXBean().getSystemProperties();
		}
	}

	@SuppressWarnings("unused")
	static final class ReadsTheCommandLine {
		static Object commandLine() {
			return ProcessHandle.current().info().commandLine();
		}
	}

	@SuppressWarnings("unused")
	static final class LoadsAService {
		static ServiceLoader<Runnable> providers() {
			return ServiceLoader.load(Runnable.class);
		}
	}

	@SuppressWarnings("unused")
	static final class ReadsUserPreferences {
		static String stored(String key) {
			return Preferences.userRoot().node("shatterfish").get(key, null);
		}
	}

	/** System properties without naming System: the wrapper types read them too. */
	@SuppressWarnings("unused")
	static final class ReadsAPropertyThroughAWrapper {
		static boolean oracleOn() {
			return Boolean.getBoolean("shatterfish.oracle");
		}

		static Long seedHint() {
			return Long.getLong("shatterfish.peek.seed");
		}
	}

	/** Class loading and classpath file reading, without a ClassLoader or java.io. */
	@SuppressWarnings("unused")
	static final class ReadsTheClasspath {
		static String value(String bundle, String key) {
			return java.util.ResourceBundle.getBundle(bundle).getString(key);
		}
	}

	@SuppressWarnings("unused")
	static final class ShufflesWithoutAGenerator {
		static void mix(List<String> moves) {
			java.util.Collections.shuffle(moves);
		}
	}

	@SuppressWarnings("unused")
	static final class ReadsAFile {
		static boolean saveExists(String name) {
			return new File(name).exists();
		}
	}

	@SuppressWarnings("unused")
	static final class ReadsTheClock {
		static Instant now() {
			return Instant.now();
		}
	}

	@SuppressWarnings("unused")
	static final class ReadsTheClockTheOldWay {
		static long now() {
			return new Date().getTime();
		}
	}

	@SuppressWarnings("unused")
	static final class SeedsItself {
		static int roll() {
			return new Random().nextInt(6);
		}
	}

	@SuppressWarnings("unused")
	static final class SeedsItselfSecurely {
		static int roll() {
			return new java.security.SecureRandom().nextInt(6);
		}
	}

	@SuppressWarnings("unused")
	static final class SeedsItselfThroughTheNewApi {
		static long roll() {
			return java.util.random.RandomGenerator.getDefault().nextLong();
		}
	}

	@SuppressWarnings("unused")
	static final class MakesARandomIdentifier {
		static UUID id() {
			return UUID.randomUUID();
		}
	}

	@SuppressWarnings("unused")
	static final class CallsMathRandom {
		static double roll() {
			return Math.random();
		}
	}

	@SuppressWarnings("unused")
	static final class SchedulesWork {
		static Object pool() {
			return Executors.newFixedThreadPool(2);
		}
	}

	@SuppressWarnings("unused")
	static final class CallsStrictMathRandom {
		static double roll() {
			return StrictMath.random();
		}
	}

	/** Iteration order decided by identity hashing, which ADR-0016 row 6 removes from the game. */
	@SuppressWarnings("unused")
	static final class IteratesByIdentity {
		static java.util.Map<String, String> map() {
			return new java.util.IdentityHashMap<>();
		}
	}

	/** Iteration order decided by the garbage collector. */
	@SuppressWarnings("unused")
	static final class IteratesWhatTheCollectorLeft {
		static java.util.Map<String, String> map() {
			return new java.util.WeakHashMap<>();
		}
	}

	/** The common pool, which excluding java.util.concurrent was meant to shut. */
	@SuppressWarnings("unused")
	static final class GoesParallel {
		static long count(List<String> moves) {
			return moves.parallelStream().count();
		}
	}

	/** The host's default locale decides the digit grouping, so the same Run formats two ways. */
	@SuppressWarnings("unused")
	static final class FormatsForTheHost {
		static String gold(int amount) {
			return String.format("%,d", amount);
		}
	}

	/** Turkish maps i to a dotted capital, so a name used as a key stops matching. */
	@SuppressWarnings("unused")
	static final class ChangesCaseForTheHost {
		static String key(String name) {
			return name.toUpperCase();
		}
	}

	/** The caller chain: which harness, rig or overlay class is driving the brain. */
	@SuppressWarnings("unused")
	static final class WalksTheStack {
		static String caller() {
			return new Throwable().getStackTrace()[1].getClassName();
		}
	}

	@SuppressWarnings("unused")
	static final class DependsOnAThirdParty {
		static ClassFileImporter importer() {
			return new ClassFileImporter();
		}
	}

	/**
	 * Everything a real brain is expected to be written with. If a rule ever rejects this, the rule
	 * is wrong, not the code.
	 *
	 * <p>One thing ordinary Java may not do here: use a {@code Class} object. {@code java.lang.Class}
	 * is denied, and while a bare {@code getClass()} whose result is discarded is not recorded as a
	 * dependency, calling anything on the result is. So a hand-written {@code equals} compares with
	 * {@code instanceof} rather than {@code getClass()}, which is the better idiom anyway. That is a
	 * real constraint on how the brain gets written, and it is stated here rather than discovered.
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
			java.math.BigDecimal mean = moves.isEmpty()
					? java.math.BigDecimal.ZERO
					: java.math.BigDecimal.valueOf(moves.stream().mapToInt(Move::score).sum())
							.divide(java.math.BigDecimal.valueOf(moves.size()), java.math.RoundingMode.HALF_UP);
			return "best of " + moves.size() + " for " + suit + ": " + best + ", mean " + mean;
		}
	}
}
