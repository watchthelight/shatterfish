package org.shatterfish.overlay;

/**
 * A Safety flag's verdict (story 5.4, FR-38, {@code DESIGN.md} Colors): the chip colour a flag's
 * text is tinted with, which is never the only thing the chip says (its text always states the
 * flag itself, {@code DESIGN.md} Components: "the color never carries meaning alone").
 *
 * <p>{@code org.shatterfish.brain.Safety} (package-private to {@code brain}) is the one place a
 * flag's own text is decided, and today's four ({@code Safety.ALL}, mirrored by the public
 * {@code Brain.safetyFlags()}) are {@code hp-low}, {@code enemy-in-view}, {@code hungry} and
 * {@code starving}: none is "ok" today (the story's own "As the human ... spot a bad belief" line
 * is about beliefs, not flags; {@code EXPERIENCE.md}'s "ok: fighting in corridor" line is a
 * later Policy's flag, not one {@code Safety} raises yet -- {@code docs/ideas.md}). This class
 * only decides the colour; it never invents a flag's text.
 *
 * <p>The mapping is judged from what the game itself does about each condition, cited by
 * {@code path:line} (non-negotiable 8):
 * <ul>
 *   <li>{@code hp-low} -- danger. {@code Safety.java:22-28}: the status pane's own low-health
 *   tint, at zero health "the worst danger there is."</li>
 *   <li>{@code starving} -- danger. {@code Hunger.java:161-164}: starving deals the hero damage
 *   every hunger tick, and more the longer it is not fed, unlike hungry alone.</li>
 *   <li>{@code hungry} -- warn. {@code Hunger.java:159-160}: only a warning message; no damage
 *   yet.</li>
 *   <li>{@code enemy-in-view} -- warn. Conditional on the fight, exactly {@code DESIGN.md}'s own
 *   words for amber ("amber means conditional"); a corridor fight might be fine and an open-room
 *   one might not, and this flag alone does not say which.</li>
 * </ul>
 * A flag {@code brain} raises that this class does not recognise falls back to {@link #WARN}
 * (unclassified is worth a look, not dismissible as {@link #OK} and not alarmed over as
 * {@link #DANGER}) -- {@code SafetyFlagsContentTest.every_known_flag_has_its_own_verdict} pins
 * that the four above never take this fallback, so it stays a real fallback and not the answer
 * for a flag someone forgot to add here.
 */
enum SafetyFlagVerdict {

    /** {@code DESIGN.md} colors.ok: green means the flag says safe. */
    OK(0x66DD66),
    /** {@code DESIGN.md} colors.warn: amber means conditional. */
    WARN(0xFFD34D),
    /** {@code DESIGN.md} colors.danger: red means unsafe. */
    DANGER(0xFF5555);

    final int color;

    SafetyFlagVerdict(int color) {
        this.color = color;
    }

    /** {@code flag}'s verdict, by the mapping this class's own Javadoc cites; {@link #WARN} for an unrecognised one. */
    static SafetyFlagVerdict of(String flag) {
        return switch (flag) {
            case "hp-low", "starving" -> DANGER;
            case "hungry", "enemy-in-view" -> WARN;
            default -> WARN;
        };
    }
}
