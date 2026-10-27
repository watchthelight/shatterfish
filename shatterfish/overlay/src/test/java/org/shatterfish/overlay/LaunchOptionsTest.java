package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The launcher's command line (story 5.1): the Run's tuple, and the one place the oracle may be asked
 * for, off unless asked.
 */
class LaunchOptionsTest {

    @Test
    @DisplayName("a Run states its seed and class; the rest has defaults, and the oracle is off")
    void the_defaults() {
        LaunchOptions options = LaunchOptions.parse(new String[] {"--seed", "12345", "--class", "warrior"});
        assertEquals(12345L, options.seed());
        assertEquals(HeroClass.WARRIOR, options.heroClass());
        assertNull(options.salt(), "no salt stated: one is drawn and written down");
        assertEquals(Path.of("overlay-runs"), options.out());
        assertEquals(20_000, options.turnCap());
        assertFalse(options.oracle(), "the oracle is off by default");
        assertFalse(options.exitWhenOver());
    }

    @Test
    @DisplayName("a seed may be the code a player types")
    void a_seed_code() {
        LaunchOptions options = LaunchOptions.parse(new String[] {"--seed", "abc-def-ghi", "--class", "mage"});
        assertEquals(com.shatteredpixel.shatteredpixeldungeon.utils.DungeonSeed.convertFromCode("ABC-DEF-GHI"),
                options.seed());
        assertEquals(HeroClass.MAGE, options.heroClass());
    }

    @Test
    @DisplayName("the oracle is a flag of this launcher's, and the salt is hex")
    void the_oracle_and_the_salt() {
        LaunchOptions options = LaunchOptions.parse(new String[] {"--seed", "1", "--class", "rogue", "--oracle",
                "--salt", "0x5a175a17", "--exit-when-over", "--turn-cap", "50"});
        assertTrue(options.oracle());
        assertTrue(options.exitWhenOver());
        assertEquals(0x5A17_5A17L, options.salt());
        assertEquals(50, options.turnCap());
    }

    @Test
    @DisplayName("an unknown flag, a flag given twice and a missing tuple are refused by name")
    void refusals() {
        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class,
                () -> LaunchOptions.parse(new String[] {"--seed", "1", "--class", "warrior", "--Oracle"}));
        assertTrue(unknown.getMessage().contains("--Oracle"), unknown.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> LaunchOptions.parse(new String[] {"--seed", "1", "--seed", "2", "--class", "warrior"}));
        assertThrows(IllegalArgumentException.class, () -> LaunchOptions.parse(new String[] {"--class", "warrior"}));
        assertThrows(IllegalArgumentException.class,
                () -> LaunchOptions.parse(new String[] {"--seed", "1", "--class", "warrior", "--out"}));
        assertThrows(IllegalArgumentException.class,
                () -> LaunchOptions.parse(new String[] {"--seed", "-1", "--class", "warrior"}));
    }

    @Test
    @DisplayName("--window sets the window's size, and without it the game's own setting is used")
    void the_window() {
        LaunchOptions sized = LaunchOptions.parse(new String[] {"--seed", "1", "--class", "warrior", "--window", "1600x900"});
        assertEquals(1600, sized.windowWidth());
        assertEquals(900, sized.windowHeight());
        LaunchOptions unsized = LaunchOptions.parse(new String[] {"--seed", "1", "--class", "warrior"});
        assertEquals(0, unsized.windowWidth());
        assertEquals(0, unsized.windowHeight());
        assertThrows(IllegalArgumentException.class,
                () -> LaunchOptions.parse(new String[] {"--seed", "1", "--class", "warrior", "--window", "1600"}));
        assertThrows(IllegalArgumentException.class,
                () -> LaunchOptions.parse(new String[] {"--seed", "1", "--class", "warrior", "--window", "0x900"}));
    }

    @Test
    @DisplayName("an oracle Run opens windowed, so fullscreen cannot hide the title bar's marker")
    void the_oracle_is_windowed() {
        assertTrue(OverlayGame.windowed(LaunchOptions.parse(new String[] {"--seed", "1", "--class", "warrior", "--oracle"})));
        assertTrue(OverlayGame.windowed(LaunchOptions.parse(new String[] {"--seed", "1", "--class", "warrior", "--window", "800x600"})));
        assertFalse(OverlayGame.windowed(LaunchOptions.parse(new String[] {"--seed", "1", "--class", "warrior"})),
                "a fair Run with no size asked for keeps the game's own fullscreen default");
    }

    @Test
    @DisplayName("replay_ a log names the whole Run: seed, class and cap come from its header, nobody else plays")
    void replay_takes_the_tuple_from_the_log() {
        Path log = ReplayLoadTest.smallLog();
        LaunchOptions options = LaunchOptions.parse(new String[] {"--replay", log.toString()});
        assertTrue(options.replaying());
        assertEquals(log, options.replay());
        assertNull(options.refused(), "this build can play it");
        assertEquals(ReplaySessionTest.SEED, options.seed());
        assertEquals(HeroClass.WARRIOR, options.heroClass());
        assertEquals(60, options.turnCap());
        assertEquals("replay", options.agent());
        assertFalse(options.oracle());
    }

    @Test
    @DisplayName("replay_ a log that is missing or does not verify is not an error of the command line: it is refused for the Panel to say")
    void replay_refusal_is_kept_for_the_panel() {
        LaunchOptions options = LaunchOptions.parse(new String[] {"--replay", "no-such-log.jsonl"});
        assertTrue(options.replaying());
        assertTrue(options.refused() != null && !options.refused().isEmpty(), "the reason: " + options.refused());
    }

    @Test
    @DisplayName("replay_ flags that would change the Run, or start from another log, are refused with --replay")
    void replay_refuses_what_the_log_decides() {
        String log = ReplayLoadTest.smallLog().toString();
        for (String[] extra : new String[][] {{"--seed", "1"}, {"--class", "mage"}, {"--salt", "0x5"},
                {"--turn-cap", "10"}, {"--out", "x"}, {"--agent", "brain"}}) {
            String[] args = new String[extra.length + 2];
            args[0] = "--replay";
            args[1] = log;
            System.arraycopy(extra, 0, args, 2, extra.length);
            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                    () -> LaunchOptions.parse(args), extra[0]);
            assertTrue(refused.getMessage().contains(extra[0]), refused.getMessage());
        }
        assertThrows(IllegalArgumentException.class,
                () -> LaunchOptions.parse(new String[] {"--seed", "1", "--class", "warrior", "--agent", "replay"}),
                "replay is not an agent a launch can name without a log");
        assertThrows(IllegalArgumentException.class,
                () -> LaunchOptions.parse(new String[] {"--replay", log, "--oracle"}));
        assertThrows(IllegalArgumentException.class,
                () -> LaunchOptions.parse(new String[] {"--replay", log, "--resume", "x"}));
    }
}
