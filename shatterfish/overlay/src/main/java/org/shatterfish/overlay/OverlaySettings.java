package org.shatterfish.overlay;

import java.util.EnumSet;
import java.util.Set;

/**
 * What the person has set for the Overlay (story 8.3, FR-45): the Pause-on conditions that are enabled and
 * the HP threshold. A value with defaults -- every condition off, the threshold at 30% -- so a Run that
 * nobody configured plays as it always has. Story 8.4's settings screen is only an editor of this value; the
 * evaluator ({@link PauseOn}) and {@link ControlState} read nothing else.
 *
 * @param pauseOn the enabled conditions
 * @param hpThresholdPercent the share of maximum hit points under which {@link PauseOn.Condition#HP_BELOW}
 *                           holds, {@value #HP_MIN} to {@value #HP_MAX}
 */
record OverlaySettings(Set<PauseOn.Condition> pauseOn, int hpThresholdPercent) {

    /** The threshold's range, in percent of maximum hit points. */
    static final int HP_MIN = 1;
    static final int HP_MAX = 99;
    /** The threshold's default. */
    static final int HP_DEFAULT = 30;

    /** Every condition off, the threshold at its default. */
    static final OverlaySettings DEFAULTS = new OverlaySettings(Set.of(), HP_DEFAULT);

    OverlaySettings {
        pauseOn = pauseOn.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(pauseOn));
        if (hpThresholdPercent < HP_MIN || hpThresholdPercent > HP_MAX) {
            throw new IllegalArgumentException("the HP threshold is " + HP_MIN + " to " + HP_MAX + " percent, not "
                    + hpThresholdPercent);
        }
    }

    /** These settings with {@code conditions} enabled and no others. */
    OverlaySettings withPauseOn(Set<PauseOn.Condition> conditions) {
        return new OverlaySettings(conditions, hpThresholdPercent);
    }

    /** These settings with the HP threshold at {@code percent}. */
    OverlaySettings withHpThreshold(int percent) {
        return new OverlaySettings(pauseOn, percent);
    }

    /** Every condition on, for a test and for the Overlay's own checks. */
    static OverlaySettings all() {
        return new OverlaySettings(EnumSet.allOf(PauseOn.Condition.class), HP_DEFAULT);
    }

    /** Whether any condition is enabled. */
    boolean anyPauseOn() {
        return !pauseOn.isEmpty();
    }
}
