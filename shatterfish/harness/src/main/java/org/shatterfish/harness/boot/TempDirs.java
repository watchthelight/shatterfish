package org.shatterfish.harness.boot;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Temporary directories that go when the JVM does.
 *
 * <p>The boot makes a profile directory per JVM and one JVM is one Run, so a rig that plays
 * thousands of Runs left thousands of {@code shatterfish-profile*} directories in the temp folder.
 * Deletion is best effort and recursive: a file that will not go (a handle still open on Windows)
 * is skipped, and a failure to clean up never changes how the JVM exits.
 */
public final class TempDirs {

    private TempDirs() {
    }

    /** Creates a directory under the temp folder that is deleted, with everything in it, at JVM exit. */
    public static Path createDeletedOnExit(String prefix) throws IOException {
        Path directory = Files.createTempDirectory(prefix);
        deleteOnExit(directory);
        return directory;
    }

    /** Registers {@code directory} to be deleted recursively when the JVM exits. */
    public static void deleteOnExit(Path directory) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteTree(directory), "shatterfish-temp-cleanup"));
    }

    /** Deletes {@code directory} and everything under it, skipping what cannot be deleted. */
    public static void deleteTree(Path directory) {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    quietly(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException failed) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException failed) {
                    quietly(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException | RuntimeException bestEffort) {
            // Nothing to do: the directory is in the temp folder, where the OS will reach it.
        }
    }

    private static void quietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException | RuntimeException bestEffort) {
            // see deleteTree
        }
    }
}
