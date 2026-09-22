package org.shatterfish.rig;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The Seed set task (story 3.1): {@code ./gradlew :rig:seeds} writes {@code seeds/} from the
 * derivation in {@link SeedSets}, in one process with no Run, no game boot and nothing drawn.
 * Regenerating and committing is the only way a set changes, and {@code SeedSetsTest} compares the
 * committed bytes with a fresh generation, so a hand-edited file fails the build naming the file
 * and this command.
 *
 * <p>It is the pattern {@code :codex:generate} set, for the same reason: one task writes every
 * file and deletes any file in the folder it no longer writes, so the one command returns the tree
 * to what the tests expect rather than leaving a set nobody generates standing beside the ones
 * everybody reads.
 */
public final class Seeds {

    private Seeds() {
    }

    /**
     * Writes the sets: the first argument is the repository root and the files go to
     * {@code <root>/seeds/}, unless a folder is named, which only a test does. Silent; the folder
     * is the output.
     */
    public static void main(String[] args) {
        if (args.length != 1 && args.length != 2) {
            throw new IllegalArgumentException("usage: Seeds <repository root> [<folder>]");
        }
        Path root = checkout(args[0]);
        Path folder = args.length == 2 ? Path.of(args[1]).toAbsolutePath().normalize()
                : root.resolve(SeedSets.FOLDER);
        // Everything is derived before anything is written, so that a set the records refuse
        // cannot leave the folder half regenerated.
        write(SeedSets.files(), folder);
    }

    /** The repository root the argument names, real and checked to be a Shatterfish checkout. */
    public static Path checkout(String argument) {
        Path root = Path.of(argument).toAbsolutePath().normalize();
        try {
            root = root.toRealPath();
        } catch (IOException e) {
            throw new IllegalArgumentException("not a directory: " + argument, e);
        }
        if (!Files.isDirectory(root.resolve("core/src/main/java")) || !Files.isRegularFile(root.resolve("docs/UPSTREAM.md"))) {
            throw new IllegalArgumentException("not a Shatterfish checkout (no core/src/main/java or docs/UPSTREAM.md): " + root);
        }
        return root;
    }

    /**
     * Writes every file of {@code files} under {@code folder} as UTF-8 bytes, and deletes any
     * regular file there that is not one of them. A directory is refused rather than removed: the
     * task owns the files it writes, and deleting a tree it never wrote is not something one
     * command should do quietly.
     */
    public static void write(Map<String, String> files, Path folder) {
        try {
            Files.createDirectories(folder);
            try (Stream<Path> present = Files.list(folder)) {
                for (Path stale : present.filter(p -> !files.containsKey(p.getFileName().toString())).toList()) {
                    if (Files.isDirectory(stale)) {
                        throw new IllegalStateException(stale + " is not the task's; " + folder
                                + " holds only the seed sets " + SeedSets.COMMAND + " writes");
                    }
                    Files.delete(stale);
                }
            }
            for (Map.Entry<String, String> file : files.entrySet()) {
                Files.write(folder.resolve(file.getKey()), file.getValue().getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("the seed sets could not be written under " + folder, e);
        }
    }
}
