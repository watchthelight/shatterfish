package org.shatterfish.overlay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Safety flags row's content (story 5.4, FR-38): each flag becomes a chip whose text states
 * the flag and whose colour follows {@link SafetyFlagVerdict}, at most {@link SafetyFlagsContent#CHIPS},
 * absent when there are none.
 */
class SafetyFlagsContentTest {

    @Test
    @DisplayName("no flags: no chips")
    void no_flags_is_no_chips() {
        assertEquals(List.of(), SafetyFlagsContent.of(List.of()));
        assertEquals(List.of(), SafetyFlagsContent.of(null));
    }

    @Test
    @DisplayName("each chip's text states the flag it names, in the given order")
    void chip_text_states_the_flag() {
        List<SafetyFlagsContent.Chip> chips = SafetyFlagsContent.of(List.of("hp-low", "enemy-in-view"));
        assertEquals(List.of("hp-low", "enemy-in-view"), chips.stream().map(SafetyFlagsContent.Chip::text).toList());
    }

    @Test
    @DisplayName("at most four chips (FR-38), the rest truncated")
    void capped_at_four_chips() {
        List<SafetyFlagsContent.Chip> chips = SafetyFlagsContent.of(
                List.of("hp-low", "enemy-in-view", "hungry", "starving", "a-fifth-flag"));
        assertEquals(SafetyFlagsContent.CHIPS, chips.size());
        assertTrue(chips.stream().noneMatch(chip -> chip.text().equals("a-fifth-flag")));
    }

    @Test
    @DisplayName("hp-low and starving are danger, hungry and enemy-in-view are warn (Safety.java, Hunger.java)")
    void every_known_flag_has_its_own_verdict() {
        assertEquals(SafetyFlagVerdict.DANGER, SafetyFlagVerdict.of("hp-low"), "the worst danger there is (Safety.java:22-28)");
        assertEquals(SafetyFlagVerdict.DANGER, SafetyFlagVerdict.of("starving"), "starving deals damage every hunger tick (Hunger.java:161-164)");
        assertEquals(SafetyFlagVerdict.WARN, SafetyFlagVerdict.of("hungry"), "a warning only, no damage yet (Hunger.java:159-160)");
        assertEquals(SafetyFlagVerdict.WARN, SafetyFlagVerdict.of("enemy-in-view"), "conditional, DESIGN.md's own word for amber");

        // Every flag org.shatterfish.brain.Brain actually raises today (its own public mirror of
        // Safety.ALL) takes a real verdict, not the fallback -- a completeness check against the
        // real vocabulary, not this test's own guess at what it is.
        for (String flag : org.shatterfish.brain.Brain.safetyFlags()) {
            SafetyFlagVerdict verdict = SafetyFlagVerdict.of(flag);
            assertTrue(verdict == SafetyFlagVerdict.OK || verdict == SafetyFlagVerdict.WARN
                    || verdict == SafetyFlagVerdict.DANGER, flag);
        }
        assertEquals(4, org.shatterfish.brain.Brain.safetyFlags().size(),
                "if this grows, the four assertions above should grow with it, not silently fall back");
    }

    @Test
    @DisplayName("an unrecognised flag falls back to warn, not ok and not danger")
    void unrecognised_flag_falls_back_to_warn() {
        assertEquals(SafetyFlagVerdict.WARN, SafetyFlagVerdict.of("a-flag-nobody-wrote-here-yet"));
    }

    @Test
    @DisplayName("the verdict colours are DESIGN.md's own: ok green, warn amber, danger red")
    void verdict_colours_are_designmd() {
        assertEquals(0x66DD66, SafetyFlagVerdict.OK.color);
        assertEquals(0xFFD34D, SafetyFlagVerdict.WARN.color);
        assertEquals(0xFF5555, SafetyFlagVerdict.DANGER.color);
    }
}
