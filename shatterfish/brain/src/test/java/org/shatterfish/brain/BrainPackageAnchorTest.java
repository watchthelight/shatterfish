package org.shatterfish.brain;

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
 * Every class the brain ships must be somewhere the boundary rules look.
 *
 * <p>{@code BrainBoundaryTest} selects classes by package: {@code @AnalyzeClasses(packages =
 * "org.shatterfish.brain")}, and every rule then asks for classes that reside in
 * {@code org.shatterfish.brain..}. A class compiled into this module under any other package is
 * therefore selected by nothing at all — not scanned, not rejected, just absent. An adversarial
 * review demonstrated it with a twelve-line class in {@code org.shatterfish.peek} that did
 * {@code Class.forName}, {@code setAccessible} and a static field read, shipped in
 * {@code brain.jar}, and left the build green.
 *
 * <p>The boundary was opt-in by package name. This test makes it apply to the module, which is what
 * AD-1 actually says: the whole brain could otherwise be written one package to the left.
 */
class BrainPackageAnchorTest {

	private static final String REQUIRED = "org/shatterfish/brain/";

	@Test
	@DisplayName("every class compiled into brain lives under org.shatterfish.brain")
	void nothing_ships_outside_the_scanned_package() {
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
					"these classes ship in the brain module but sit outside org.shatterfish.brain, so"
							+ " every rule in BrainBoundaryTest passes over them without looking: " + strays
							+ ". Move them under org.shatterfish.brain, or the boundary is opt-in.");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** The module's own compiled output, found from a class that is certainly in it. */
	private static Path mainOutput() {
		try {
			Path location = Path.of(BrainModule.class.getProtectionDomain().getCodeSource()
					.getLocation().toURI());
			if (!Files.isDirectory(location)) {
				fail("expected brain's compiled classes in a directory, found " + location
						+ "; this check walks the module's own output");
			}
			return location;
		} catch (URISyntaxException e) {
			throw new IllegalStateException("cannot locate the brain module's compiled output", e);
		}
	}
}
