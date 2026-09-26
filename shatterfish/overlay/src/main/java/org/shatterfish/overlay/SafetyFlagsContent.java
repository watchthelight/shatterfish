package org.shatterfish.overlay;

import java.util.ArrayList;
import java.util.List;

/**
 * The Safety flags row's content (story 5.4, FR-38): the Decision's own {@code flags}, each a chip
 * whose text states the flag and whose colour follows {@link SafetyFlagVerdict}, at most
 * {@link #CHIPS} (FR-38: "Safety flags (at most four chips)"), absent when there are none.
 *
 * <p>A pure function of the flags list, in the style of {@link ModeStripContent} and
 * {@link DecisionCardContent}: a test holds it against a constructed list without booting the
 * game.
 */
final class SafetyFlagsContent {

    /** FR-38: "Safety flags (at most four chips)". */
    static final int CHIPS = 4;

    /** One chip: {@code text} states the flag; {@code verdict} is the colour, never the only signal. */
    record Chip(String text, SafetyFlagVerdict verdict) {
    }

    private SafetyFlagsContent() {
    }

    /** {@code flags}, one chip each, in their own order, truncated to {@link #CHIPS}; empty when {@code flags} is empty or null. */
    static List<Chip> of(List<String> flags) {
        if (flags == null || flags.isEmpty()) {
            return List.of();
        }
        List<Chip> chips = new ArrayList<>();
        for (String flag : flags) {
            if (chips.size() >= CHIPS) {
                break;
            }
            chips.add(new Chip(flag, SafetyFlagVerdict.of(flag)));
        }
        return List.copyOf(chips);
    }
}
