package org.shatterfish.overlay;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Right-aligned, fixed-width number columns (story 5.3, UX-DR5, {@code DESIGN.md} Typography): "scores,
 * probabilities, and turn and Floor counters are right-aligned in fixed-width columns so that the eye
 * can compare them without reading; the pixel font is not monospace, so alignment is by column
 * position, never by padding with characters." Padding with spaces is what this class does anyway,
 * because the Mode strip and the Decision card render each row as one line of text today (a
 * simplification recorded in {@code docs/ideas.md}); a fixed character width still gives every number
 * the same column start, which is the property the tests hold.
 */
final class Columns {

    /**
     * A {@link org.shatterfish.api.RunLog.Choice}'s score, a ten-thousandths {@code long} (the
     * {@code RunLog} javadoc), rendered as the exact decimal it is -- {@code BigDecimal.valueOf(v, 4)},
     * the same no-float technique {@code StrategyLog.choice} uses -- and right-aligned to
     * {@link #SCORE_WIDTH}.
     */
    static final int SCORE_WIDTH = 8;

    private Columns() {
    }

    /** {@code tenThousandths} as an exact decimal fraction of one, right-aligned to {@link #SCORE_WIDTH}. */
    static String score(long tenThousandths) {
        return rightAlign(BigDecimal.valueOf(tenThousandths, 4).toPlainString(), SCORE_WIDTH);
    }

    /** {@code n}, right-aligned to {@code width}. */
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
}
