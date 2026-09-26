package org.shatterfish.overlay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Two Overlay Runs never share a Profile (story 5.1, FR-37). */
class FreshProfileTest {

    @Test
    @DisplayName("with no directory asked for, every Run gets a new one, claimed")
    void a_new_one_each_time() {
        Path first = ShatterfishLauncher.freshProfile(null);
        Path second = ShatterfishLauncher.freshProfile(null);
        assertNotEquals(first, second);
        assertTrue(Files.isRegularFile(first.resolve(ShatterfishLauncher.OWNER_FILE)));
        assertTrue(Files.isRegularFile(second.resolve(ShatterfishLauncher.OWNER_FILE)));
        OverlayGame.deleteQuietly(first);
        OverlayGame.deleteQuietly(second);
        assertFalse(Files.exists(first), "a Profile the launcher made is deleted at the end of its Run");
    }

    @Test
    @DisplayName("a directory the player named survives the Run's end, claim and all; one the launcher made does not")
    void a_named_one_survives(@TempDir Path folder) throws IOException {
        Path asked = folder.resolve("mine");
        LaunchOptions named = LaunchOptions.parse(new String[] {"--seed", "1", "--class", "warrior",
                "--profile", asked.toString()});
        LaunchOptions unnamed = LaunchOptions.parse(new String[] {"--seed", "1", "--class", "warrior"});
        assertFalse(ShatterfishLauncher.deletesProfile(named));
        assertTrue(ShatterfishLauncher.deletesProfile(unnamed));

        Path mine = ShatterfishLauncher.freshProfile(named.profile());
        Files.writeString(mine.resolve("game.dat"), "a save the Run wrote");
        OverlayGame.releaseProfile(mine, ShatterfishLauncher.deletesProfile(named));
        assertTrue(Files.isRegularFile(mine.resolve("game.dat")), "the player's directory keeps what the Run wrote");
        assertTrue(Files.isRegularFile(mine.resolve(ShatterfishLauncher.OWNER_FILE)),
                "and its claim, so a second Run is refused it");
        assertThrows(IllegalArgumentException.class, () -> ShatterfishLauncher.freshProfile(named.profile()));

        Path made = ShatterfishLauncher.freshProfile(unnamed.profile());
        OverlayGame.releaseProfile(made, ShatterfishLauncher.deletesProfile(unnamed));
        assertFalse(Files.exists(made));
        OverlayGame.deleteQuietly(made);   // the shutdown hook's second call finds it gone, and says nothing
    }

    @Test
    @DisplayName("a directory is claimed once: a second launcher given it is refused")
    void claimed_once(@TempDir Path folder) {
        Path asked = folder.resolve("run");
        assertEquals(asked, ShatterfishLauncher.freshProfile(asked));
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> ShatterfishLauncher.freshProfile(asked));
        assertTrue(refused.getMessage().contains("claimed"), refused.getMessage());
    }

    @Test
    @DisplayName("a directory that holds anything else is refused, and left unclaimed")
    void a_used_one_is_refused(@TempDir Path folder) throws IOException {
        Path used = Files.createDirectories(folder.resolve("used"));
        Files.writeString(used.resolve("shatterfish-profile.txt"), "shatterfish-profile-version=3\n");
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> ShatterfishLauncher.freshProfile(used));
        assertTrue(refused.getMessage().contains("FR-37"), refused.getMessage());
        assertFalse(Files.exists(used.resolve(ShatterfishLauncher.OWNER_FILE)), "the claim is taken back");
    }
}
