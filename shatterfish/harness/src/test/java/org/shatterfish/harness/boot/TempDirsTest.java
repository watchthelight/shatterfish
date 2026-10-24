package org.shatterfish.harness.boot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The profile directory a boot makes does not outlive its JVM. */
class TempDirsTest {

    @Test
    void deleteTreeRemovesNestedFilesAndDirectories(@TempDir Path root) throws IOException {
        Path dir = Files.createDirectories(root.resolve("a/b/c"));
        Files.writeString(dir.resolve("x.txt"), "x");
        Files.writeString(root.resolve("a/y.txt"), "y");
        TempDirs.deleteTree(root.resolve("a"));
        assertFalse(Files.exists(root.resolve("a")));
    }

    @Test
    void deleteTreeOnAMissingDirectoryIsQuiet(@TempDir Path root) {
        TempDirs.deleteTree(root.resolve("nothing"));
    }

    @Test
    void aJvmThatCreatesOneLeavesNothingBehind() throws Exception {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        Path exe = Files.exists(Path.of(java + ".exe")) ? Path.of(java + ".exe") : java;
        Process child = new ProcessBuilder(exe.toString(), "-cp", System.getProperty("java.class.path"),
                Maker.class.getName()).redirectErrorStream(true).start();
        String said = new String(child.getInputStream().readAllBytes());
        assertEquals(0, child.waitFor(), said);
        Path made = Path.of(said.strip().split("\n")[0].strip());
        assertTrue(made.isAbsolute(), said);
        assertFalse(Files.exists(made), "the directory should be gone: " + made);
    }

    /** Runs in the child: makes a directory with content, says where, and exits. */
    public static final class Maker {
        public static void main(String[] args) throws IOException {
            Path dir = TempDirs.createDeletedOnExit("shatterfish-profile-test");
            Files.createDirectories(dir.resolve("saves/slot1"));
            Files.writeString(dir.resolve("saves/slot1/game.dat"), "x");
            System.out.println(dir);
        }
    }
}
