package org.shatterfish.overlay;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Number formatting for the Mode strip and the Decision card (story 5.3, UX-DR5, {@code DESIGN.md}
 * Typography): exact decimals, never a float, and no character ever stands for a digit that is not
 * there.
 *
 * <p>UX-DR5's "right-aligned in fixed-width columns... by column position, never by padding with
 * characters" is two different things depending on whether there is more than one row to compare: the
 * Mode strip is a single line, so {@link #rightAlign} padding it to a fixed character width is the
 * column (there is nothing else to misalign against); the Decision card stacks several rows, so its
 * score column is real pixel positioning ({@code DecisionCard}, from each row's measured
 * {@code RenderedTextBlock} width), and {@link #score} here returns the exact decimal with no padding
 * of its own, since a pixel column does not need one and adding one would only leave a stray blank
 * glyph inside the block that column is measured from.
 */
final class Columns {

    private Columns() {
    }

    /**
     * A {@link org.shatterfish.api.RunLog.Choice}'s score, a ten-thousandths {@code long} (the
     * {@code RunLog} javadoc), rendered as the exact decimal it is -- {@code BigDecimal.valueOf(v, 4)},
     * the same no-float technique {@code StrategyLog.choice} uses. Not padded: {@code DecisionCard}
     * right-aligns the column by position, not by character.
     */
    static String score(long tenThousandths) {
        return BigDecimal.valueOf(tenThousandths, 4).toPlainString();
    }

    /** {@code n}, right-aligned to {@code width}: the Mode strip's turn and floor, one line, no rows to compare. */
    static String number(long n, int width) {
        return rightAlign(Long.toString(n), width);
    }

    /** {@code text}, right-aligned to {@code width} with leading spaces; unchanged when already that wide or wider. */
    static String rightAlign(String text, int width) {
        return text.length() >= width ? text : " ".repeat(width - text.length()) + text;
    }

    /** An interval in seconds, to one decimal place (UX-DR6's Human play speed range is 0.5 to 5). */
    static String seconds(double seconds) {
        return String.format(Locale.ROOT, "%.1fs", seconds);
    }

    /**
     * A probability in {@code [0, 1]}, to two decimal places (story 5.4, the Belief summary):
     * {@code EXPERIENCE.md}'s own convention for it ("healing 0.35 / strength 0.20", Flow 4).
     * Two places, not four like {@link #score}: a Belief's odds are the Brain's own marginal, not
     * an exact decimal the way a {@code Choice}'s score is (ten-thousandths, {@code RunLog}'s own
     * javadoc), so a third digit would claim more precision than the number carries.
     */
    static String probability(double probability) {
        return String.format(Locale.ROOT, "%.2f", probability);
    }
}
