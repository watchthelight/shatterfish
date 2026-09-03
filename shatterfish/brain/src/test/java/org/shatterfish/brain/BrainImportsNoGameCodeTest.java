package org.shatterfish.brain;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Non-negotiable #1, information parity, as a build failure: nothing in the brain may touch
 * upstream game code. {@code com.shatteredpixel..} is Shattered Pixel Dungeon, {@code com.watabou..}
 * is the Noosa/utility layer it sits on (including {@code com.watabou.utils.Random}).
 */
@AnalyzeClasses(packages = "org.shatterfish.brain", importOptions = ImportOption.DoNotIncludeTests.class)
class BrainImportsNoGameCodeTest {

    @ArchTest
    static final ArchRule brain_never_depends_on_game_code = noClasses()
            .that().resideInAPackage("org.shatterfish.brain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.shatteredpixel..", "com.watabou..")
            .because("the brain may only see the game through org.shatterfish.api (information parity)");

    @ArchTest
    static final ArchRule brain_depends_on_api_only = noClasses()
            .that().resideInAPackage("org.shatterfish.brain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.shatterfish.harness..", "org.shatterfish.overlay..",
                    "org.shatterfish.codex..", "org.shatterfish.rig..")
            .because("brain depends on api only");
}
