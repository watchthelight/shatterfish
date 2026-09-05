package org.shatterfish.harness;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Every class the harness ships lives under {@code org.shatterfish.harness}.
 *
 * <p>Java packages are split across jars, so a class compiled into this module and declared in
 * {@code com.shatteredpixel.shatteredpixeldungeon.scenes} would read {@code GameScene.scene},
 * package-private and the static everything in the scene is gated on, with no reflection and no
 * edit to any upstream file. The hook ledger keys on upstream's directories and sees nothing;
 * {@code HarnessReflectionTest} scans {@code org.shatterfish.harness..} and sees nothing either.
 * The second fairness review of story 1.3 named the route. This test closes it the way
 * {@code BrainPackageAnchorTest} closed the same route for the brain: by walking the module's own
 * compiled output rather than trusting a package name.
 */
class HarnessPackageAnchorTest {

    private static final String REQUIRED = "org/shatterfish/harness/";

    @Test
    @DisplayName("every class compiled into harness lives under org.shatterfish.harness")
    void nothing_ships_outside_the_module_package() {
        Path output = mainOutput();
        try (Stream<Path> tree = Files.walk(output)) {
            List<String> strays = tree.filter(Files::isRegularFile)
                    .map(output::relativize)
                    .map(p -> p.toString().replace('\\', '/'))
                    .filter(p -> p.endsWith(".class"))
                    .filter(p -> !p.startsWith(REQUIRED))
                    .sorted()
                    .toList();

            assertTrue(strays.isEmpty(),
                    "these classes ship in the harness module but sit outside org.shatterfish.harness, where"
                            + " they could share a package with upstream and reach its package-private members"
                            + " with no reflection and no hook: " + strays);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The module's own compiled output, found from a class that is certainly in it. */
    private static Path mainOutput() {
        try {
            Path location = Path.of(HeadlessDriver.class.getProtectionDomain().getCodeSource()
                    .getLocation().toURI());
            if (!Files.isDirectory(location)) {
                fail("expected harness's compiled classes in a directory, found " + location
                        + "; this check walks the module's own output");
            }
            return location;
        } catch (URISyntaxException e) {
            throw new IllegalStateException("cannot locate the harness module's compiled output", e);
        }
    }
}
