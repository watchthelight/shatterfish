package org.shatterfish.rig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.HeroClass;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The baseline cache of {@code :rig:direction}: what its key holds, and when it refuses to cache. */
class DirectionCacheTest {

    @Test
    void the_key_follows_the_host_code_and_not_the_brain(@TempDir Path root) throws IOException {
        repository(root);
        String sha = head(root);
        Path first = key(root, sha);
        assertNotNull(first, "a clean tree has a cache key");
        assertTrue(first.startsWith(root.resolve(".git").toRealPath()) || first.toString().contains(".git"),
                "the cache lives under the common git folder, shared by every worktree: " + first);
        assertEquals(first, key(root, sha), "the same selection on the same trees has the same key");

        write(root, "shatterfish/brain/Brain.java", "class Brain { int changed; }");
        commit(root, "brain only");
        assertEquals(first, key(root, sha), "a Brain change in the current tree does not touch the baseline's Runs");

        write(root, "shatterfish/harness/Driver.java", "class Driver { int changed; }");
        commit(root, "harness");
        assertNotEquals(first, key(root, sha), "the baseline plays on the current harness, so a harness change is a new key");
    }

    @Test
    void the_selection_is_part_of_the_key(@TempDir Path root) throws IOException {
        repository(root);
        String sha = head(root);
        Path forty = Direction.cacheFolder(root, sha, "v4.0.2", "standard", HeroClass.WARRIOR, 40, 1000, 40);
        Path eighty = Direction.cacheFolder(root, sha, "v4.0.2", "standard", HeroClass.WARRIOR, 80, 3000, 80);
        assertNotEquals(forty, eighty);
    }

    @Test
    void uncommitted_host_changes_are_not_cached(@TempDir Path root) throws IOException {
        repository(root);
        String sha = head(root);
        write(root, "core/Game.java", "class Game { int dirty; }");
        assertNull(key(root, sha), "a dirty game tree would make the key a lie");
    }

    @Test
    void a_stored_baseline_appears_whole(@TempDir Path root) throws IOException {
        Path logs = Files.createDirectories(root.resolve("logs"));
        Files.writeString(logs.resolve("a.jsonl"), "{}\n");
        Files.writeString(logs.resolve("b.jsonl"), "{}\n");
        Path cached = root.resolve("cache").resolve("abc");
        Files.createDirectories(cached.getParent());
        Direction.store(logs, cached);
        assertTrue(Files.isRegularFile(cached.resolve("a.jsonl")));
        assertTrue(Files.isRegularFile(cached.resolve("b.jsonl")));
        try (var left = Files.list(cached.getParent())) {
            assertFalse(left.anyMatch(p -> p.getFileName().toString().contains(".partial-")), "no partial folder is left");
        }
    }

    private static Path key(Path root, String sha) {
        return Direction.cacheFolder(root, sha, "v4.0.2", "standard", HeroClass.WARRIOR, 40, 1000, 40);
    }

    private static void repository(Path root) throws IOException {
        git(root, "init", "-q");
        git(root, "config", "user.email", "test@example.org");
        git(root, "config", "user.name", "test");
        git(root, "config", "commit.gpgsign", "false");
        for (String path : Direction.HOST_PATHS) {
            write(root, path + "/Keep.java", "class Keep {}");
        }
        write(root, "shatterfish/brain/Brain.java", "class Brain {}");
        commit(root, "start");
    }

    private static void write(Path root, String path, String text) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private static void commit(Path root, String message) {
        git(root, "add", "-A");
        git(root, "commit", "-q", "-m", message);
    }

    private static String head(Path root) {
        return git(root, "rev-parse", "HEAD").strip();
    }

    private static String git(Path root, String... args) {
        List<String> command = new ArrayList<>(List.of("git", "-C", root.toString()));
        command.addAll(List.of(args));
        try {
            Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
            String said = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (p.waitFor() != 0) {
                throw new IllegalStateException(String.join(" ", command) + ": " + said);
            }
            return said;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
