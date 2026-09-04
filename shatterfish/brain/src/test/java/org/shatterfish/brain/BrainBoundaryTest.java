package org.shatterfish.brain;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.type;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Non-negotiable #1, information parity, as a build failure.
 *
 * <p>The rule of play is that the bot may use only what a human at the same screen could have. That
 * is enforced by architecture rather than intention, and this is where the architecture is stated:
 * the Observation is the brain's only channel, so anything that could carry a second one is closed.
 *
 * <p>Game code is the obvious channel and the least interesting, because a reviewer would see the
 * import. The channels worth a rule are the quiet ones. The brain runs in the same JVM as the game
 * (non-negotiable #4), so at Overlay and rig runtime the game's classes are on the same classpath
 * whether or not {@code brain} compiles against them: a single {@code Class.forName} string reaches
 * {@code Dungeon.level} with no dependency for a package rule to see. A file read reaches the save
 * bundle. A system property is a one-line channel from the harness in the same process. A service
 * loader lets the harness hand the brain an oracle-backed implementation of an {@code api}
 * interface without the brain ever naming a game type. A wall clock and an unseeded generator break
 * reproducibility (non-negotiable #5) rather than parity, which is the same build failure for a
 * different reason.
 *
 * <p>Every rule here is checked against a class that breaks it, in {@code BrainBoundaryRulesBiteTest}.
 * A boundary rule that has never rejected anything is a comment.
 */
@AnalyzeClasses(packages = "org.shatterfish.brain", importOptions = ImportOption.DoNotIncludeTests.class)
class BrainBoundaryTest {

	@ArchTest
	static final ArchRule brain_never_depends_on_game_code = noClasses()
			.that().resideInAPackage("org.shatterfish.brain..")
			.should().dependOnClassesThat()
			.resideInAnyPackage("com.shatteredpixel..", "com.watabou..")
			.because("the brain may only see the game through org.shatterfish.api (information parity);"
					+ " com.watabou is the Noosa layer, including com.watabou.utils.Random");

	@ArchTest
	static final ArchRule brain_depends_on_no_other_shatterfish_module = noClasses()
			.that().resideInAPackage("org.shatterfish.brain..")
			.should().dependOnClassesThat()
			.resideInAnyPackage("org.shatterfish.harness..", "org.shatterfish.overlay..",
					"org.shatterfish.codex..", "org.shatterfish.rig..")
			.because("brain depends on api only (AD-1); harness holds the game state the brain must not see");

	@ArchTest
	static final ArchRule brain_reads_no_files_and_opens_no_sockets = noClasses()
			.that().resideInAPackage("org.shatterfish.brain..")
			.should().dependOnClassesThat()
			.resideInAnyPackage("java.io..", "java.nio.file..", "java.net..")
			.because("a side channel is still a channel: the save bundle, the level file and a socket to"
					+ " an oracle process all carry information the Observation withholds. Anything the"
					+ " brain needs to persist goes out through api and is written by its caller");

	/**
	 * {@code java.lang.invoke} is banned as a whole package, not as {@code MethodHandles} alone:
	 * {@code MethodHandles.privateLookupIn} plus a {@code MethodHandle} reaches any private static in
	 * the JVM, and a rule naming only the entry class leaves the rest of the package open.
	 */
	@ArchTest
	static final ArchRule brain_uses_no_reflection = noClasses()
			.that().resideInAPackage("org.shatterfish.brain..")
			.should().dependOnClassesThat()
			.resideInAnyPackage("java.lang.reflect..", "java.lang.invoke..", "sun..", "jdk..")
			.because("reflection reaches what the rules above forbid by name, and it does so invisibly to"
					+ " a reviewer reading the imports");

	/**
	 * The classes that resolve a name to a class at runtime, run a process, or read the process
	 * environment. These live in {@code java.lang} and {@code java.util}, which the brain otherwise
	 * needs, so they are named one by one rather than by package.
	 */
	@ArchTest
	static final ArchRule brain_reaches_nothing_by_name_at_runtime = noClasses()
			.that().resideInAPackage("org.shatterfish.brain..")
			.should().dependOnClassesThat()
			.belongToAnyOf(ClassLoader.class, Module.class, ModuleLayer.class, System.class,
					Runtime.class, ProcessBuilder.class, Process.class, java.util.ServiceLoader.class,
					java.util.Scanner.class)
			.orShould().callMethodWhere(target(name("forName")).and(target(owner(type(Class.class)))))
			.because("Class.forName and a service loader both reach game code without naming it, and the"
					+ " brain shares a JVM with the game (non-negotiable #4), so the game's classes are"
					+ " on the classpath at run time whatever brain compiles against. System carries the"
					+ " environment, the properties and the streams, each a channel from the harness in"
					+ " the same process");

	/**
	 * Reproducibility rather than parity: a Run is determined by the tuple (tag, seed, action list)
	 * and nothing else (non-negotiable #5). A brain that reads a clock or seeds itself from the
	 * environment is not replayable, and the rig cannot measure what it cannot repeat. Randomness the
	 * brain legitimately needs arrives through {@code api}, seeded from the Run.
	 */
	@ArchTest
	static final ArchRule brain_has_no_clock_and_no_generator_of_its_own = noClasses()
			.that().resideInAPackage("org.shatterfish.brain..")
			.should().dependOnClassesThat()
			.resideInAnyPackage("java.time..")
			.orShould().dependOnClassesThat()
			.belongToAnyOf(java.util.Random.class, java.util.SplittableRandom.class,
					java.util.concurrent.ThreadLocalRandom.class)
			.orShould().callMethodWhere(target(name("random")).and(target(owner(type(Math.class)))))
			.because("a Run is (tag, seed, action list); a wall clock or a self-seeded generator makes"
					+ " the same tuple produce two different games");

	/**
	 * The positive form of the rules above. They are kept separately because a named ban says what
	 * went wrong; this one only says that something did. It is deliberately the weaker rule: it
	 * allows all of {@code java..}, which is why the bans exist.
	 */
	@ArchTest
	static final ArchRule brain_sees_only_the_api_and_the_jdk = classes()
			.that().resideInAPackage("org.shatterfish.brain..")
			.should().onlyDependOnClassesThat()
			.resideInAnyPackage("org.shatterfish.brain..", "org.shatterfish.api..", "java..")
			.allowEmptyShould(true)
			.because("the Observation is the brain's only channel (non-negotiable #1)");
}
