package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.watabou.noosa.Game;
import org.shatterfish.api.RunLog;

/**
 * Everything a replay decides before the game starts (story 8.5): the log is opened again and must be the one the
 * launcher read, the interface size is set to the one the log was played on, and a window the game would play the
 * compact interface in is refused. It is a class of its own so the production start-up can be tested without a
 * window; {@code OverlayGame.createReplay} does what follows (the game, the Run, the controls).
 *
 * @param session the loaded log, or null when it is refused
 * @param refusal why the log is not played, in words for the Panel, or null
 */
record ReplayStart(ReplaySession session, String refusal) {

    /**
     * Prepares the replay {@code options} names. The launcher took the seed, class, salt and cap from its first read of
     * the log; the log is read again here so what plays is what verifies now, and a log that changed between the two
     * reads is refused. Sets {@code SPDSettings.interfaceSize} to the log's size.
     */
    static ReplayStart prepare(LaunchOptions options) {
        ReplaySession session;
        try {
            if (options.refused() != null) {
                return refused(options.refused());
            }
            session = ReplaySession.load(options.replay());
        } catch (IllegalArgumentException | java.io.UncheckedIOException cannot) {
            return refused(cannot.getMessage());
        }
        RunLog.Header header = session.header();
        if (header.seed() != options.seed() || !header.heroClass().name().equals(options.heroClass().name())
                || !java.util.Objects.equals(header.salt(), options.salt()) || header.cap() != options.turnCap()) {
            return refused("the log changed after the game was launched: its tuple is no longer the one the game"
                    + " started from");
        }
        int size = session.interfaceSize();
        SPDSettings.interfaceSize(size);
        if (SPDSettings.interfaceSize() != size && Game.width > 0 && Game.height > 0) {
            // The game plays the compact interface on a window below its full-UI minimum
            // (SPDSettings.java:140-146), which words the log differently from the Run it recorded.
            return refused("this window is too small for interface size " + size + ", which the log was played on");
        }
        return new ReplayStart(session, null);
    }

    private static ReplayStart refused(String reason) {
        return new ReplayStart(null, reason);
    }
}
