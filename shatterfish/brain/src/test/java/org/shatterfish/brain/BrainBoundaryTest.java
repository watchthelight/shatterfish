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
 * the Observation is the brain's only channel, so everything that could carry a second one is shut.
 *
 * <h2>Why this is an allowlist</h2>
 *
 * <p>Two rounds of adversarial review killed two denylists. The first banned game packages and
 * reflection, and was walked through with {@code Class.forName} plus {@code MethodHandles}. The
 * second added {@code java.lang.invoke}, {@code System}, {@code ServiceLoader} and nine other names,
 * and was walked through with {@code java.beans.Expression}, which performs arbitrary reflective
 * dispatch by string and returns {@code Object}, so that no banned type appears in the class file at
 * all. It read {@code Dungeon.seed}. The next denylist would have been walked through with
 * {@code java.lang.management}, {@code ProcessHandle}, {@code SecureRandom} or something nobody in
 * this repository has thought of, because the JDK is large and an attacker only needs one door.
 *
 * <p>So the rule is inverted. The brain may depend on a short list of packages that hold data and
 * arithmetic and nothing else; everything else in the JDK, the game and every other module is denied
 * because it is not on the list. A new capability the brain genuinely needs is one line here and a
 * decision someone has to make on purpose, which is the point.
 *
 * <p>Two packages have to be allowed whole and are not innocent: {@code java.lang} holds
 * {@code Class}, {@code System} and {@code ProcessBuilder}, and {@code java.util} holds
 * {@code Random} and {@code ServiceLoader}. Those are the only places a denylist survives here, and
 * both are closed sets fixed by the JDK rather than open-ended surface.
 *
 * <p>Two things this file cannot do on its own, and which are done next to it. It selects classes by
 * package, so a class compiled into this module under another package would be selected by no rule
 * at all: {@code BrainPackageAnchorTest} closes that. And ArchUnit's dependency rules are not
 * transitive, so everything denied here is reopened by one class in {@code api}, whose allowlist is
 * therefore kept at least as strict — see {@code ApiBoundaryTest}. Both holes were found by walking
 * an earlier version of these rules, not by reasoning about them.
 *
 * <p>Every rule is checked against a class that breaks it in {@code BrainBoundaryRulesBiteTest},
 * along with ordinary Java, which must pass. A boundary rule that has never rejected anything is a
 * comment, and a rule that rejects ordinary code is one that gets deleted.
 */
@AnalyzeClasses(packages = "org.shatterfish.brain", importOptions = ImportOption.DoNotIncludeTests.class)
class BrainBoundaryTest {

	/**
	 * Everything the brain may reach. Data structures, arithmetic, and the Observation.
	 *
	 * <p>Deliberately absent, each because it is a channel or a source of irreproducibility rather
	 * than because anyone expects the brain to want it: {@code java.io}, {@code java.nio},
	 * {@code java.net} (the save bundle, the level file, a socket to an oracle process);
	 * {@code java.lang.reflect}, {@code java.lang.invoke}, {@code java.beans}, {@code sun},
	 * {@code jdk} (reach anything by name, invisibly to a reviewer reading imports);
	 * {@code java.lang.management}, {@code java.util.prefs}, {@code javax} (the property table, a
	 * persistent store outside the process, and a very large surface); {@code java.time},
	 * {@code java.security}, {@code java.util.random}, {@code java.util.concurrent} (a clock, a
	 * generator the brain seeds itself, and scheduling — a Run is (tag, seed, action list) and
	 * nothing else).
	 */
	private static final String[] ALLOWED = {
			"org.shatterfish.brain..",
			"org.shatterfish.api..",
			"java.lang",
			"java.lang.runtime",
			"java.util",
			"java.util.function",
			"java.util.stream",
			"java.math",
	};

	/**
	 * The classes inside the two packages that have to be allowed whole but must not be reachable.
	 * {@code Class} is here because a reference to it is the first half of every reflective escape,
	 * and a brain has no legitimate use for class-based dispatch.
	 */
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

	/**
	 * Methods on otherwise harmless classes that reach outside the process. The wrapper types carry
	 * system-property readers that name neither {@code System} nor a property type, and
	 * {@code Collections.shuffle} without a generator makes one of its own. Each was found by an
	 * adversarial review walking the previous version of these rules.
	 */
	private static final ArchRule NO_SIDE_DOORS = noClasses()
			.that().resideInAPackage("org.shatterfish.brain..")
			.should().callMethodWhere(target(name("forName")).and(target(owner(type(Class.class)))))
			.orShould().callMethodWhere(target(name("random")).and(target(owner(type(Math.class)))))
			.orShould().callMethodWhere(target(name("random")).and(target(owner(type(StrictMath.class)))))
			.orShould().callMethodWhere(target(name("parallelStream")))
			.orShould().callMethodWhere(target(name("parallel")))
			.orShould().callMethodWhere(target(name("getStackTrace")))
			.orShould().callMethodWhere(target(name("getAllStackTraces")))
			.orShould().callMethodWhere(target(name("format")).and(target(owner(type(String.class)))))
			.orShould().callMethodWhere(target(name("toUpperCase")).and(target(owner(type(String.class)))))
			.orShould().callMethodWhere(target(name("toLowerCase")).and(target(owner(type(String.class)))))
			.orShould().callMethodWhere(target(name("getBoolean")).and(target(owner(type(Boolean.class)))))
			.orShould().callMethodWhere(target(name("getInteger")).and(target(owner(type(Integer.class)))))
			.orShould().callMethodWhere(target(name("getLong")).and(target(owner(type(Long.class)))))
			.orShould().callMethodWhere(target(name("shuffle"))
					.and(target(owner(type(java.util.Collections.class)))))
			.because("Boolean.getBoolean, Integer.getInteger and Long.getLong read system properties"
					+ " without naming System; Collections.shuffle seeds a generator of its own;"
					+ " parallelStream and parallel reopen the common pool that excluding"
					+ " java.util.concurrent was meant to shut; and getStackTrace hands back the caller"
					+ " chain, which is what StackWalker is denied for. These are matched by name rather"
					+ " than by owner because the receiver can be any subclass. String.format and case"
					+ " conversion follow the host's default locale, so the same Run formats differently on"
					+ " a Turkish machine and a German one; denying java.util.Locale as a type does not"
					+ " reach them, because the overloads that take one name it and the ones that do not"
					+ " name nothing. Every one of these lives on a class the brain legitimately needs");

	/**
	 * The rule everything else rests on: default deny. It is stated first because the named rules
	 * below are refinements of it, not additions to it.
	 */
	@ArchTest
	static final ArchRule brain_reaches_only_data_structures_and_the_api = classes()
			.that().resideInAPackage("org.shatterfish.brain..")
			.should().onlyDependOnClassesThat()
			.resideInAnyPackage(ALLOWED)
			.because("the Observation is the brain's only channel (non-negotiable #1). Anything not on"
					+ " the allowlist is denied because it is not on the allowlist, so a capability the"
					+ " brain needs is a decision someone makes on purpose rather than a door nobody"
					+ " noticed");

	@ArchTest
	static final ArchRule brain_reaches_nothing_dangerous_inside_the_allowed_packages = noClasses()
			.that().resideInAPackage("org.shatterfish.brain..")
			.should().dependOnClassesThat()
			.belongToAnyOf(DENIED_INSIDE_ALLOWED_PACKAGES)
			.because("java.lang and java.util have to be allowed whole for the brain to be writable at"
					+ " all, and they are where the JDK keeps class loading, the process environment,"
					+ " service loading, locales and unseeded generators");

	/** @see #NO_SIDE_DOORS */
	@ArchTest
	static final ArchRule brain_calls_no_method_that_reaches_outside_the_process = NO_SIDE_DOORS;

	/**
	 * Kept as a named rule even though the allowlist already implies it, because this is the one a
	 * reader comes looking for and a failure should say so in as many words.
	 */
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
}
