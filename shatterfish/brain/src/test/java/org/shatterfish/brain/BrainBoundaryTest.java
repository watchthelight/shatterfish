package org.shatterfish.brain;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Non-negotiable #1, information parity, as a build failure.
 *
 * <p>The rule of play is that the bot may use only what a human at the same screen could have. That
 * is enforced by architecture rather than intention, and this is where the architecture is stated:
 * the Observation is the brain's only channel, so anything that could carry a second one is closed.
 *
 * <p>Game code is the obvious channel. The others are quieter and matter just as much: a file read
 * reaches the save bundle and the level it describes, a socket reaches an oracle in another
 * process, and reflection reaches whatever the first two rules forbid by name. A brain that can
 * open a file can read the dungeon it is playing.
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

	@ArchTest
	static final ArchRule brain_uses_no_reflection = noClasses()
			.that().resideInAPackage("org.shatterfish.brain..")
			.should().dependOnClassesThat()
			.resideInAnyPackage("java.lang.reflect..", "java.lang.invoke.MethodHandles..")
			.because("reflection reaches what the rules above forbid by name, and it does so invisibly to"
					+ " a reviewer reading the imports");

	/**
	 * The positive form of the rules above. They are kept separately because a named ban says what
	 * went wrong; this one only says that something did.
	 */
	@ArchTest
	static final ArchRule brain_sees_only_the_api_and_the_jdk = classes()
			.that().resideInAPackage("org.shatterfish.brain..")
			.should().onlyDependOnClassesThat()
			.resideInAnyPackage("org.shatterfish.brain..", "org.shatterfish.api..", "java..")
			.allowEmptyShould(true)
			.because("the Observation is the brain's only channel (non-negotiable #1)");
}
