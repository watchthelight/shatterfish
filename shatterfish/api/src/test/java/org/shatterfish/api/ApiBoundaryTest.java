package org.shatterfish.api;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The api module is the wire between the harness and the brain, and it is data only.
 *
 * <p>It depends on nothing but the JDK, which is what lets the brain depend on it without
 * inheriting a path to the game. It is also where a parity break would be hardest to see: a type in
 * here that wraps game state hands the brain hidden information no matter how careful the Observer
 * is. That is why {@code Snapshot} is not in this module — bundle bytes are inflatable by anything
 * holding them, so {@code api} carries only an opaque handle (AD-1, ADR-0009).
 */
@AnalyzeClasses(packages = "org.shatterfish.api", importOptions = ImportOption.DoNotIncludeTests.class)
class ApiBoundaryTest {

	@ArchTest
	static final ArchRule api_uses_only_jdk_and_itself = classes()
			.that().resideInAPackage("org.shatterfish.api..")
			.should().onlyDependOnClassesThat()
			.resideInAnyPackage("org.shatterfish.api..", "java..")
			.because("api is DTOs only; anything else it could reach, the brain could reach through it");

	@ArchTest
	static final ArchRule api_never_depends_on_game_code = noClasses()
			.that().resideInAPackage("org.shatterfish.api..")
			.should().dependOnClassesThat()
			.resideInAnyPackage("com.shatteredpixel..", "com.watabou..")
			.because("a game type crossing into api would put game state one field access from the brain");
}
