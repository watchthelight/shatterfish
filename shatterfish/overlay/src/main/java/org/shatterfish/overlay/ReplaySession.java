package org.shatterfish.overlay;

import org.shatterfish.api.RunLog;
import org.shatterfish.harness.log.LogFollower;
import org.shatterfish.harness.log.Replay;

import java.nio.file.Path;

/**
 * A Run log loaded to be played back in the game (story 8.5, FR-46).
 *
 * <p>Loading is the harness's {@link Replay#open}: the hash chain is verified first and the file is read once, the
 * header must be one this build can play (schema, upstream tag, Observation schema, Profile, Codex, no oracle, no
 * challenges, a bounded cap), and a log that fails any of these is refused with the reason and is not played. What
 * plays it is a {@link LogFollower} in its stopping mode as the Run's agent, so the executor applies the log's Actions
 * at the wait the log applied them, the Brain decides nothing, and a wait whose Observation hash differs ends the
 * replay at that wait instead of being played past. The session keeps no copy of the header's salt or seed beyond
 * what starting the game needs, and gives the follower neither.
 *
 * @param header   the log's header: the tuple the game starts from, and the cap
 * @param follower the Run's agent
 * @param waits    the waits the log holds
 */
record ReplaySession(RunLog.Header header, LogFollower follower, int waits) {

    /**
     * The interface size the log was played on, which the replay plays on too: the Observation carries log text that
     * changes with it, so a Rig log (headless driver, states nothing, size 0) replayed on the Overlay's size 1 would
     * differ at its first wait that mentions it. An Overlay log states its own.
     */
    int interfaceSize() {
        return header.interfaceSize() >= 0 ? header.interfaceSize() : 0;
    }

    /**
     * Opens {@code file} to play it.
     *
     * <p>A log played with a game controller connected is refused: the Observation's log text changes with the
     * controller, and a replay has no controller to give it, so the first wait that mentions one would be a false
     * mismatch.
     *
     * @throws IllegalArgumentException with the reason in words, when the log cannot be played
     */
    static ReplaySession load(Path file) {
        Replay.Opened opened = Replay.open(file);
        if (opened.header().controller() > 0) {
            throw new IllegalArgumentException("this log was played with a game controller connected, which a replay"
                    + " cannot reproduce: the game words its messages differently");
        }
        LogFollower follower = LogFollower.stopping(opened.log());
        return new ReplaySession(opened.header(), follower, follower.total());
    }
}
