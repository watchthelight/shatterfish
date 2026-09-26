package org.shatterfish.overlay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Two Overlay Runs never share a Profile (story 5.1, FR-37). */
class FreshProfileTest {

    @Test
    @DisplayName("with no directory asked for, every Run gets a new one")
    void a_new_one_each_time() {
        Path first = ShatterfishLauncher.freshProfile(null);
        Path second = ShatterfishLauncher.freshProfile(null);
        assertNotEquals(first, second);
        assertTrue(Files.isDirectory(first) && Files.isDirectory(second));
    }

    @Test
    @DisplayName("an empty or missing directory is taken; one that holds anything is refused")
    void a_used_one_is_refused(@TempDir Path folder) throws IOException {
        Path missing = folder.resolve("new");
        assertEquals(missing, ShatterfishLauncher.freshProfile(missing));
        assertEquals(missing, ShatterfishLauncher.freshProfile(missing), "still empty");
        Files.writeString(missing.resolve("shatterfish-profile.txt"), "shatterfish-profile-version=3\n");
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> ShatterfishLauncher.freshProfile(missing));
        assertTrue(refused.getMessage().contains("FR-37"), refused.getMessage());
    }
}
