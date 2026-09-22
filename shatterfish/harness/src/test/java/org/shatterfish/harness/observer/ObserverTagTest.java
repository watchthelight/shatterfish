package org.shatterfish.harness.observer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tag a Run attributes itself to (story 3.2).
 *
 * <p>`Observer.upstreamTag()` reads the version the boot set, and before the boot that version is
 * null -- so it answered `"vnull"`, a string that passes for a tag everywhere one is written: an
 * Observation's header, and from this story a Run log's header and the file name the log is
 * published under. A published Run attributing itself to no release at all.
 *
 * <p>The refusal is here rather than inside the reader because a JVM that has booted cannot be
 * un-booted: the guard was unreachable from any test while it lived there, and the mutation battery
 * deleted it with every test still green. This is the seam that makes the rule askable.
 */
class ObserverTagTest {

    @Test
    @DisplayName("a version the game has not set is refused, naming the boot that sets it")
    void a_game_with_no_version_has_no_tag() {
        for (String nothing : new String[] {null, ""}) {
            IllegalStateException refused = assertThrows(IllegalStateException.class,
                    () -> Observer.tag(nothing));
            assertTrue(refused.getMessage().contains("HeadlessBoot.ensure()"), refused.getMessage());
            assertTrue(refused.getMessage().contains("no version"), refused.getMessage());
        }
    }

    @Test
    @DisplayName("a version the game did set is the tag, with the v the release carries")
    void a_version_is_the_tag() {
        assertEquals("v4.0.0", Observer.tag("4.0.0"));
        assertEquals("v3.3.8", Observer.tag("3.3.8"));
    }
}
