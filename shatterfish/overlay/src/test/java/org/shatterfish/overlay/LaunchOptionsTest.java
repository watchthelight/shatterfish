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
}
